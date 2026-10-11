package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The Cinder Colossus's fighting AI: it walks up to its target and, between pauses, picks one of its moves (each one
 * an animation, see {@link GolemAction}; their numbers are in {@link ColossusTuning} and {@link ColossusStage}):
 * <ul>
 *   <li><b>swipe</b>: a backhand sweep of its fist at anyone close in front of it;</li>
 *   <li><b>throw</b>: tears a rock out of the ground and throws it ({@link ColossusRock}; it never places a block);</li>
 *   <li><b>slam</b>: both fists raised high for about a second (the warning), then a shockwave that does little damage
 *       but throws players far;</li>
 *   <li><b>charge</b>: head down, it paws the ground, then gallops about 10 blocks in a straight line, head first,
 *       hitting everything in its way (in the Lava Core stage leaving a trail of fire);</li>
 *   <li><b>lob</b> (Cracking and Lava Core): its volcanoes spit lava blobs ({@link LavaBlob}) that land a few blocks
 *       from the player and leave pools of temporary lava;</li>
 *   <li><b>summon</b> (Cracking and Lava Core): it pounds the ground three times and a minion rises at each pound (to
 *       its left, its right, in front of it): stone golems, or lava golems in the Lava Core stage.</li>
 * </ul>
 * A move always plays to its end (even if its target dies), except when the Colossus changes stage
 * ({@link #interrupt}). Every hit it lands in the Lava Core stage sets you on fire. It never breaks a block.
 */
public class ColossusAttackGoal extends Goal
{
    /** Its moves, each with its action (animation) and its cooldown in ticks (before the stage's scale). */
    enum Move
    {
        SWIPE(GolemAction.SWIPE, ColossusTuning.SWIPE_COOLDOWN),
        THROW(GolemAction.THROW, ColossusTuning.THROW_COOLDOWN),
        SLAM(GolemAction.SLAM, ColossusTuning.SLAM_COOLDOWN),
        CHARGE(GolemAction.CHARGE, ColossusTuning.CHARGE_COOLDOWN),
        LOB(GolemAction.LOB, ColossusTuning.LOB_COOLDOWN),
        SUMMON(GolemAction.SUMMON, ColossusTuning.SUMMON_COOLDOWN);

        final GolemAction action;
        final int cooldown;

        Move(GolemAction action, int cooldown)
        {
            this.action = action;
            this.cooldown = cooldown;
        }
    }

    /** How often (ticks) it works out a new path to its target while it walks up to it. */
    private static final int REPATH_TICKS = 10;
    /** It stops walking up when the target is this much closer than its swipe's reach (so it doesn't push into them). */
    private static final double STOP_SHARE = 0.7D;
    /** The fist points (model units) where its pounds land while summoning: left fist, right fist, both. */
    private static final GolemDesigns.Point[] POUND_AT = {
            new GolemDesigns.Point(28.0F, 0.0F, -27.0F), new GolemDesigns.Point(-28.0F, 0.0F, -27.0F),
            new GolemDesigns.Point(0.0F, 0.0F, -34.0F)};
    /** Where its right fist tears the rock out of the ground (model units). */
    private static final GolemDesigns.Point RIP_AT = new GolemDesigns.Point(-25.0F, 0.0F, -25.0F);
    /** The charge checks for a clear run every this many blocks. */
    private static final double RUN_CHECK_STEP = 2.0D;
    /** How far below/above a target's feet a blob may land (it settles on the ground there). */
    private static final int LAND_SEARCH_UP = 4;
    private static final int LAND_SEARCH_DOWN = 8;
    /** Particles for its impacts. */
    private static final int DUST = 30;

    private final CinderColossus golem;
    private final EnumMap<Move, Integer> cooldowns = new EnumMap<>(Move.class);
    @Nullable
    private Move current;
    /** Ticks before it picks its next move. */
    private int pause;
    private int repath;
    /** Where its target was last seen: a move keeps aiming there if the target dies or vanishes mid-move. */
    @Nullable
    private Vec3 lastSeen;
    /** The charge: the way it gallops, how far it has gone, and what it has already hit (each thing once). */
    private Vec3 chargeDir = Vec3.ZERO;
    private double charged;
    private final Set<Integer> chargeHits = new HashSet<>();

    public ColossusAttackGoal(CinderColossus golem)
    {
        this.golem = golem;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        if (this.golem.isShowcase() || this.golem.isTransforming() || !this.golem.isAlive())
        {
            return false;
        }
        LivingEntity target = this.golem.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse()
    {
        if (this.golem.isTransforming() || !this.golem.isAlive())
        {
            return false;
        }
        if (this.current != null)
        {
            return true; // a move plays to its end
        }
        LivingEntity target = this.golem.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start()
    {
        this.pause = Math.max(this.pause, this.golem.stage().attackPause);
        this.golem.setAggressive(true);
    }

    @Override
    public void stop()
    {
        if (this.current != null && !this.golem.isTransforming() && this.golem.isAlive())
        {
            this.finish();
        }
        this.current = null;
        this.golem.getNavigation().stop();
        this.golem.setAggressive(false);
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    /** A stage change takes over (its own animation plays): the move in progress is dropped. */
    void interrupt()
    {
        this.current = null;
        this.chargeHits.clear();
        this.pause = this.golem.stage().attackPause;
    }

    @Override
    public void tick()
    {
        this.cooldowns.replaceAll((move, left) -> Math.max(0, left - 1));
        LivingEntity target = this.golem.getTarget();
        if (target != null && target.isAlive())
        {
            this.lastSeen = target.position();
        }
        if (this.current != null)
        {
            this.run(target);
            return;
        }
        if (target == null || !target.isAlive())
        {
            return;
        }
        double dist = flatDistance(this.golem.position(), target.position());
        if (this.pause > 0)
        {
            this.pause--;
            this.approach(target, dist);
            return;
        }
        Move move = this.choose(target, dist);
        if (move == null)
        {
            this.approach(target, dist);
            this.pause = ColossusTuning.RETHINK_TICKS;
            return;
        }
        this.current = move;
        this.golem.getNavigation().stop();
        this.golem.startAction(move.action);
    }

    // ---------------------------------------------------------------- choosing a move

    /** A move it can make right now (at random, weighted), or null if none fits. */
    @Nullable
    private Move choose(LivingEntity target, double dist)
    {
        ColossusStage stage = this.golem.stage();
        double edge = dist - this.golem.getBbWidth() / 2.0D - target.getBbWidth() / 2.0D;
        double dy = target.getY() - this.golem.getY();
        boolean sees = this.golem.getSensing().hasLineOfSight(target);
        boolean later = stage != ColossusStage.SHELL;
        List<Move> moves = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        if (this.ready(Move.SWIPE) && edge <= ColossusTuning.SWIPE_REACH && dy > -2.0D && dy < 4.0D)
        {
            add(moves, weights, Move.SWIPE, 6);
        }
        if (this.ready(Move.SLAM) && dist <= ColossusTuning.SLAM_TRIGGER_RANGE && dy > -2.0D && dy < 3.0D)
        {
            add(moves, weights, Move.SLAM, this.playersNear(ColossusTuning.SLAM_RADIUS) >= 2 ? 8 : 3);
        }
        if (later && this.ready(Move.SUMMON) && dist <= ColossusTuning.SUMMON_RANGE
                && this.golem.minions().size() < ColossusTuning.SUMMON_BELOW)
        {
            add(moves, weights, Move.SUMMON, 4);
        }
        if (later && this.ready(Move.LOB) && sees && dist >= ColossusTuning.LOB_MIN_RANGE && dist <= ColossusTuning.LOB_MAX_RANGE)
        {
            add(moves, weights, Move.LOB, 4);
        }
        if (this.ready(Move.CHARGE) && sees && dist >= ColossusTuning.CHARGE_MIN_RANGE && dist <= ColossusTuning.CHARGE_MAX_RANGE
                && Math.abs(dy) < 2.5D && this.clearRun(target))
        {
            add(moves, weights, Move.CHARGE, 3);
        }
        if (this.ready(Move.THROW) && sees && dist >= ColossusTuning.THROW_MIN_RANGE && dist <= ColossusTuning.THROW_MAX_RANGE)
        {
            add(moves, weights, Move.THROW, 3);
        }
        int total = 0;
        for (int w : weights)
        {
            total += w;
        }
        if (total == 0)
        {
            return null;
        }
        int roll = this.golem.getRandom().nextInt(total);
        for (int i = 0; i < moves.size(); i++)
        {
            roll -= weights.get(i);
            if (roll < 0)
            {
                return moves.get(i);
            }
        }
        return moves.get(moves.size() - 1);
    }

    private static void add(List<Move> moves, List<Integer> weights, Move move, int weight)
    {
        moves.add(move);
        weights.add(weight);
    }

    private boolean ready(Move move)
    {
        return this.cooldowns.getOrDefault(move, 0) <= 0;
    }

    /** Players (not in creative or spectator mode) within this many blocks of it. */
    private int playersNear(double range)
    {
        int count = 0;
        for (Player player : this.golem.level().players())
        {
            if (!player.isCreative() && !player.isSpectator() && player.distanceToSqr(this.golem) <= range * range)
            {
                count++;
            }
        }
        return count;
    }

    /** True if nothing solid stands in the way of a charge at the target (low steps are fine: it climbs them). */
    private boolean clearRun(LivingEntity target)
    {
        Vec3 dir = flat(target.position().subtract(this.golem.position()));
        if (dir.lengthSqr() < 1.0E-4D)
        {
            return false;
        }
        AABB box = this.golem.getBoundingBox().move(0.0D, this.golem.maxUpStep(), 0.0D);
        double run = Math.min(ColossusTuning.CHARGE_DISTANCE, flatDistance(this.golem.position(), target.position()));
        for (double d = RUN_CHECK_STEP; d <= run; d += RUN_CHECK_STEP)
        {
            if (!this.golem.level().noCollision(this.golem, box.move(dir.scale(d))))
            {
                return false;
            }
        }
        return true;
    }

    /** Walks up to the target, or stands and faces it once it's close enough to hit. */
    private void approach(LivingEntity target, double dist)
    {
        double edge = dist - this.golem.getBbWidth() / 2.0D - target.getBbWidth() / 2.0D;
        if (edge <= ColossusTuning.SWIPE_REACH * STOP_SHARE)
        {
            this.golem.getNavigation().stop();
            this.golem.turnTowards(target.position(), this.golem.stage().turnSpeed);
            return;
        }
        this.golem.getLookControl().setLookAt(target, 30.0F, 30.0F);
        if (--this.repath <= 0)
        {
            this.repath = REPATH_TICKS;
            this.golem.getNavigation().moveTo(target, 1.0D);
        }
    }

    // ---------------------------------------------------------------- running a move

    /** Ticks into the move when something {@code animTicks} into its animation happens (GeckoLib blends in first). */
    private static int at(int animTicks)
    {
        return CinderColossus.ANIMATION_BLEND_TICKS + animTicks;
    }

    private void run(@Nullable LivingEntity target)
    {
        int t = this.golem.ticksInAction();
        GolemDesigns.Moves m = this.golem.getDesign().moves();
        ColossusStage stage = this.golem.stage();
        Vec3 aim = target != null && target.isAlive() ? target.position() : this.lastSeen;
        switch (this.current)
        {
            case SWIPE -> {
                if (t < at(m.swipeHit()) - 2 && aim != null)
                {
                    this.golem.turnTowards(aim, stage.turnSpeed);
                }
                if (t == at(m.swipeHit()) - 4)
                {
                    this.golem.sound(ModSounds.COLOSSUS_SWIPE.get(), 2.0F, 0.9F);
                }
                if (t == at(m.swipeHit()))
                {
                    this.swipeHit(stage);
                }
                if (t >= at(m.swipeTicks()))
                {
                    this.finish();
                }
            }
            case THROW -> {
                if (t < at(m.throwRelease()) && aim != null)
                {
                    this.golem.turnTowards(aim, stage.turnSpeed);
                }
                if (t == at(m.throwGrab()))
                {
                    this.ripRock();
                }
                if (t == at(m.throwRelease()))
                {
                    this.throwRock(target, aim, stage, m);
                }
                if (t >= at(m.throwTicks()))
                {
                    this.finish();
                }
            }
            case SLAM -> {
                if (t < at(m.slamRaised()) && aim != null)
                {
                    this.golem.turnTowards(aim, stage.turnSpeed);
                }
                if (t == at(m.slamRaised()))
                {
                    this.golem.sound(ModSounds.COLOSSUS_ROAR.get(), 3.0F, 1.0F);
                }
                if (t == at(m.slamHit()))
                {
                    this.slamHit(stage, m);
                }
                int ring = t - at(m.slamHit());
                if (ring >= 1 && ring <= 3)
                {
                    this.slamRing(m, ring);
                }
                if (t >= at(m.slamTicks()))
                {
                    this.finish();
                }
            }
            case CHARGE -> this.runCharge(t, m, stage, aim);
            case LOB -> {
                if (t == at(0))
                {
                    this.golem.sound(ModSounds.COLOSSUS_RUMBLE.get(), 3.0F, 1.0F);
                }
                if (t < at(m.lobRelease()) && aim != null)
                {
                    this.golem.turnTowards(aim, stage.turnSpeed);
                }
                if (t == at(m.lobRelease()))
                {
                    this.lob(target, aim, stage, m);
                }
                if (t >= at(m.lobTicks()))
                {
                    this.finish();
                }
            }
            case SUMMON -> {
                if (t == at(4))
                {
                    this.golem.sound(ModSounds.COLOSSUS_ROAR.get(), 3.0F, 1.0F);
                }
                List<Integer> pounds = m.summonPounds();
                for (int k = 0; k < pounds.size(); k++)
                {
                    if (t == at(pounds.get(k)))
                    {
                        this.pound(k, stage);
                    }
                }
                if (t >= at(m.summonTicks()))
                {
                    this.finish();
                }
            }
        }
    }

    /** The move is over: back to standing, its cooldown starts and it pauses before the next one. */
    private void finish()
    {
        Move move = this.current;
        this.current = null;
        this.chargeHits.clear();
        if (move == null)
        {
            return;
        }
        ColossusStage stage = this.golem.stage();
        if (this.golem.getAction() == move.action)
        {
            this.golem.startAction(GolemAction.IDLE);
        }
        this.cooldowns.put(move, Math.round(move.cooldown * stage.cooldownScale));
        this.pause = Math.max(this.pause, stage.attackPause);
    }

    // ---------------------------------------------------------------- the moves' hits

    /** The swipe: everything close in front of it is hit and knocked away to its left. */
    private void swipeHit(ColossusStage stage)
    {
        Vec3 fwd = this.golem.forward();
        Vec3 left = this.golem.left();
        double reach = this.golem.getBbWidth() / 2.0D + ColossusTuning.SWIPE_REACH;
        double minCos = Math.cos(Math.toRadians(ColossusTuning.SWIPE_HALF_ARC));
        AABB area = this.golem.getBoundingBox().inflate(ColossusTuning.SWIPE_REACH, 1.0D, ColossusTuning.SWIPE_REACH);
        for (Entity victim : this.golem.level().getEntities(this.golem, area, this.golem::canHit))
        {
            Vec3 to = flat(victim.position().subtract(this.golem.position()));
            double d = to.length();
            if (d - victim.getBbWidth() / 2.0D > reach || (d > 0.5D && to.scale(1.0D / d).dot(fwd) < minCos))
            {
                continue;
            }
            if (victim.hurt(this.golem.blow(), ColossusTuning.SWIPE_DAMAGE * stage.damageScale))
            {
                Vec3 away = d > 1.0E-3D ? to.scale(1.0D / d) : fwd;
                knock(victim, away.scale(0.5D).add(left.scale(0.85D)).normalize().scale(ColossusTuning.SWIPE_KNOCKBACK),
                        ColossusTuning.SWIPE_LIFT);
                this.golem.afterHit(victim);
            }
        }
    }

    /** The throw's first half: its fist tears the rock out of the ground (dust and a crack of stone; no block changes). */
    private void ripRock()
    {
        Vec3 at = this.golem.modelPoint(RIP_AT);
        this.dust(at, 0.4D);
        this.golem.sound(ModSounds.COLOSSUS_RIP.get(), 2.0F, 0.9F);
    }

    /** The throw: the rock leaves its hand on an arc towards where the target will be. */
    private void throwRock(@Nullable LivingEntity target, @Nullable Vec3 aim, ColossusStage stage, GolemDesigns.Moves m)
    {
        if (aim == null || !(this.golem.level() instanceof ServerLevel level))
        {
            return;
        }
        Vec3 from = this.golem.modelPoint(m.throwAt());
        Vec3 goal = target != null && target.isAlive() ? target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D) : aim;
        int ticks = Mth.clamp((int) Math.round(flatDistance(from, goal) / ColossusTuning.ROCK_SPEED), 6, 40);
        if (target != null && target.isAlive())
        {
            // Lead a moving target a little (half of where it would be by then).
            Vec3 moving = flat(target.position().subtract(target.xo, target.yo, target.zo));
            goal = goal.add(moving.scale(ticks * 0.5D));
        }
        ColossusRock rock = ColossusRock.thrown(level, this.golem, from, ColossusTuning.ROCK_DAMAGE * stage.damageScale,
                stage == ColossusStage.LAVA_CORE);
        rock.aim(goal, ticks);
        level.addFreshEntity(rock);
        this.golem.sound(ModSounds.COLOSSUS_THROW.get(), 2.0F, 0.9F);
    }

    /** The slam: a shockwave round where its fists land. Little damage, but it throws everything near it far away. */
    private void slamHit(ColossusStage stage, GolemDesigns.Moves m)
    {
        Vec3 centre = this.golem.modelPoint(m.slamAt());
        double radius = ColossusTuning.SLAM_RADIUS;
        this.golem.sound(ModSounds.COLOSSUS_SLAM.get(), 4.0F, 0.9F);
        if (this.golem.level() instanceof ServerLevel level)
        {
            level.sendParticles(ParticleTypes.EXPLOSION, centre.x, centre.y + 0.3D, centre.z, 3, 0.8D, 0.2D, 0.8D, 0.0D);
        }
        this.dust(centre, 1.2D);
        AABB area = new AABB(centre, centre).inflate(radius, 3.0D, radius);
        for (Entity victim : this.golem.level().getEntities(this.golem, area, this.golem::canHit))
        {
            if (victim instanceof Player player && (player.isCreative() || player.isSpectator()))
            {
                continue;
            }
            Vec3 to = flat(victim.position().subtract(centre));
            double d = to.length();
            double dy = victim.getY() - centre.y;
            if (d > radius || dy < -1.5D || dy > 3.0D)
            {
                continue;
            }
            victim.hurt(this.golem.blow(), ColossusTuning.SLAM_DAMAGE * stage.damageScale);
            Vec3 away = d > 1.0E-3D ? to.scale(1.0D / d) : this.golem.forward();
            knock(victim, away.scale(ColossusTuning.SLAM_KNOCKBACK * (1.0D - 0.45D * d / radius)), ColossusTuning.SLAM_LIFT);
            this.golem.afterHit(victim);
        }
    }

    /** The slam's shockwave rolling outwards: a ring of dust (cosmetic). */
    private void slamRing(GolemDesigns.Moves m, int ring)
    {
        if (!(this.golem.level() instanceof ServerLevel level))
        {
            return;
        }
        Vec3 centre = this.golem.modelPoint(m.slamAt());
        double r = ColossusTuning.SLAM_RADIUS * ring / 3.0D;
        int points = 10 + 8 * ring;
        for (int i = 0; i < points; i++)
        {
            double a = 2.0D * Math.PI * i / points;
            Vec3 p = centre.add(Math.cos(a) * r, 0.0D, Math.sin(a) * r);
            BlockState ground = level.getBlockState(BlockPos.containing(p.x, p.y - 0.5D, p.z));
            if (!ground.isAir())
            {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), p.x, p.y + 0.1D, p.z, 3, 0.2D, 0.05D,
                        0.2D, 0.15D);
            }
        }
    }

    /**
     * The charge: it turns towards the target during the wind-up, then gallops in a straight line until it has gone
     * {@link ColossusTuning#CHARGE_DISTANCE} blocks, runs into something solid (then it's dazed for a moment) or runs
     * out of time.
     */
    private void runCharge(int t, GolemDesigns.Moves m, ColossusStage stage, @Nullable Vec3 aim)
    {
        int windup = at(m.chargeStartTicks());
        if (t < windup - 2)
        {
            if (aim != null)
            {
                this.golem.turnTowards(aim, stage.turnSpeed * 1.5F);
            }
            return;
        }
        if (t == windup - 2)
        {
            this.golem.sound(ModSounds.COLOSSUS_CHARGE.get(), 3.0F, 1.0F);
        }
        if (t == windup)
        {
            Vec3 dir = aim != null ? flat(aim.subtract(this.golem.position())) : Vec3.ZERO;
            this.chargeDir = dir.lengthSqr() > 1.0E-4D ? dir.normalize() : this.golem.forward();
            this.charged = 0.0D;
            this.chargeHits.clear();
        }
        if (t < windup)
        {
            return;
        }
        if (t > windup + 2 && this.golem.horizontalCollision)
        {
            // Ran into a wall: a thud, and it's dazed for a moment.
            this.golem.sound(ModSounds.COLOSSUS_SLAM.get(), 2.0F, 1.2F);
            this.dust(this.golem.position().add(this.chargeDir.scale(this.golem.getBbWidth() / 2.0D)), 0.8D);
            this.finish();
            this.pause = Math.max(this.pause, ColossusTuning.CHARGE_DAZED_TICKS);
            return;
        }
        Vec3 v = this.golem.getDeltaMovement();
        this.golem.setDeltaMovement(this.chargeDir.x * ColossusTuning.CHARGE_SPEED, v.y, this.chargeDir.z * ColossusTuning.CHARGE_SPEED);
        float yaw = (float) (Mth.atan2(this.chargeDir.z, this.chargeDir.x) * Mth.RAD_TO_DEG) - 90.0F;
        this.golem.setYRot(yaw);
        this.golem.setYBodyRot(yaw);
        this.golem.setYHeadRot(yaw);
        this.charged += ColossusTuning.CHARGE_SPEED;
        this.chargeHit(stage);
        if (stage == ColossusStage.LAVA_CORE)
        {
            this.fireTrail();
        }
        if ((t - windup) % 6 == 0)
        {
            this.golem.sound(ModSounds.COLOSSUS_GALLOP.get(), 2.0F, 1.0F);
        }
        if (this.charged >= ColossusTuning.CHARGE_DISTANCE || t > windup + ColossusTuning.CHARGE_MAX_TICKS)
        {
            this.finish();
        }
    }

    /** Everything it runs into during its charge is hit (once) and thrown aside and up. */
    private void chargeHit(ColossusStage stage)
    {
        AABB front = this.golem.getBoundingBox().inflate(0.5D, 0.0D, 0.5D).expandTowards(this.chargeDir.scale(0.8D));
        Vec3 side = new Vec3(-this.chargeDir.z, 0.0D, this.chargeDir.x);
        for (Entity victim : this.golem.level().getEntities(this.golem, front, this.golem::canHit))
        {
            if (!this.chargeHits.add(victim.getId()))
            {
                continue;
            }
            if (victim.hurt(this.golem.blow(), ColossusTuning.CHARGE_DAMAGE * stage.damageScale))
            {
                Vec3 to = flat(victim.position().subtract(this.golem.position()));
                double sign = to.dot(side) >= 0.0D ? 1.0D : -1.0D;
                Vec3 push = side.scale(sign).add(this.chargeDir.scale(0.6D)).normalize().scale(ColossusTuning.CHARGE_KNOCKBACK);
                knock(victim, push, ColossusTuning.CHARGE_LIFT);
                this.golem.afterHit(victim);
            }
        }
    }

    /** Lava Core stage: fire on the ground behind it as it gallops (it never spreads and goes out by itself). */
    private void fireTrail()
    {
        if (!(this.golem.level() instanceof ServerLevel level))
        {
            return;
        }
        boolean plants = ForgeEventFactory.getMobGriefingEvent(level, this.golem);
        Vec3 behind = this.golem.position().subtract(this.chargeDir.scale(this.golem.getBbWidth() / 2.0D - 0.5D));
        Vec3 side = new Vec3(-this.chargeDir.z, 0.0D, this.chargeDir.x);
        for (int k = -1; k <= 1; k++)
        {
            Vec3 p = behind.add(side.scale(k * 1.1D));
            BlockPos pos = BlockPos.containing(p.x, this.golem.getY() + 0.2D, p.z);
            if (!level.getBlockState(pos).is(com.reg21meme.dross.registry.ModBlocks.CINDER_FIRE.get()))
            {
                LavaPools.placeFire(level, pos, plants);
            }
        }
    }

    /**
     * The lob: lava blobs fly from its craters to spots a few blocks from the target (never on it), on opposite-ish
     * sides of it, each leaving a pool of temporary lava where it lands.
     */
    private void lob(@Nullable LivingEntity target, @Nullable Vec3 aim, ColossusStage stage, GolemDesigns.Moves m)
    {
        if (!(this.golem.level() instanceof ServerLevel level))
        {
            return;
        }
        Vec3 centre = target != null && target.isAlive() ? target.position() : aim;
        if (centre == null)
        {
            return;
        }
        RandomSource random = this.golem.getRandom();
        double angle = random.nextDouble() * 2.0D * Math.PI;
        List<GolemDesigns.Point> craters = m.craters();
        for (int i = 0; i < ColossusTuning.LOB_BLOBS; i++)
        {
            if (i > 0)
            {
                angle += Math.PI * (0.6D + 0.8D * random.nextDouble());
            }
            double dist = ColossusTuning.LOB_LAND_MIN + random.nextDouble() * (ColossusTuning.LOB_LAND_MAX - ColossusTuning.LOB_LAND_MIN);
            Vec3 spot = centre.add(Math.cos(angle) * dist, 0.0D, Math.sin(angle) * dist);
            Vec3 landing = groundAt(level, spot);
            Vec3 from = craters.isEmpty() ? this.golem.position().add(0.0D, this.golem.getBbHeight(), 0.0D)
                    : this.golem.modelPoint(craters.get(i % craters.size()));
            LavaBlob blob = LavaBlob.spat(level, this.golem, from, ColossusTuning.LOB_DIRECT_DAMAGE * stage.damageScale);
            blob.aim(landing, ColossusTuning.LOB_FLIGHT_TICKS);
            level.addFreshEntity(blob);
        }
        this.golem.sound(ModSounds.COLOSSUS_LOB.get(), 3.0F, 1.0F);
    }

    /** The top of the ground at (or near) a spot: where a blob aimed there should land. */
    private static Vec3 groundAt(ServerLevel level, Vec3 spot)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int x = Mth.floor(spot.x);
        int z = Mth.floor(spot.z);
        int y0 = Mth.floor(spot.y);
        for (int y = y0 + LAND_SEARCH_UP; y >= y0 - LAND_SEARCH_DOWN; y--)
        {
            pos.set(x, y - 1, z);
            BlockState below = level.getBlockState(pos);
            if (below.isFaceSturdy(level, pos, Direction.UP) && level.getBlockState(pos.above()).getCollisionShape(level, pos.above()).isEmpty())
            {
                return new Vec3(spot.x, y, spot.z);
            }
        }
        return spot;
    }

    /**
     * One of the summoning's pounds (0: its left fist, 1: its right, 2: both): dust where it lands, and a minion climbs
     * out of the ground on that side of it (to its left, its right, in front of it).
     */
    private void pound(int k, ColossusStage stage)
    {
        if (!(this.golem.level() instanceof ServerLevel level))
        {
            return;
        }
        this.golem.sound(ModSounds.COLOSSUS_POUND.get(), 3.0F, k == 2 ? 0.8F : 1.0F);
        this.dust(this.golem.modelPoint(POUND_AT[Math.min(k, POUND_AT.length - 1)]), k == 2 ? 0.9D : 0.5D);
        if (this.golem.minions().size() >= ColossusTuning.MAX_MINIONS)
        {
            return;
        }
        Vec3 dir = switch (k)
        {
            case 0 -> this.golem.left();
            case 1 -> this.golem.left().scale(-1.0D);
            default -> this.golem.forward();
        };
        EntityType<? extends GolemMinion> type = stage == ColossusStage.LAVA_CORE ? ModEntities.LAVA_GOLEM.get()
                : ModEntities.STONE_GOLEM.get();
        Vec3 feet = this.minionSpot(level, type, dir);
        if (feet != null)
        {
            GolemMinion.summon(type, level, this.golem, feet, this.golem.getYRot());
        }
    }

    /** A spot on the ground on one side of it where a minion fits (tried closer in if the first spot is blocked). */
    @Nullable
    private Vec3 minionSpot(ServerLevel level, EntityType<?> type, Vec3 dir)
    {
        for (double d = ColossusTuning.SUMMON_DISTANCE; d >= this.golem.getBbWidth() / 2.0D + 0.5D; d -= 1.0D)
        {
            Vec3 spot = this.golem.position().add(dir.scale(d));
            for (int dy = 2; dy >= -3; dy--)
            {
                BlockPos feet = BlockPos.containing(spot.x, this.golem.getY() + dy, spot.z);
                BlockPos below = feet.below();
                if (!level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                        || !level.getFluidState(feet).isEmpty())
                {
                    continue;
                }
                Vec3 at = new Vec3(spot.x, feet.getY(), spot.z);
                if (level.noCollision(type.getAABB(at.x, at.y, at.z)))
                {
                    return at;
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------- helpers

    /** Throws something: sideways (a horizontal velocity) and up, softened by half its knockback resistance. */
    private static void knock(Entity victim, Vec3 sideways, double up)
    {
        double resist = victim instanceof LivingEntity living ? living.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) : 0.0D;
        double scale = 1.0D - 0.5D * Mth.clamp(resist, 0.0D, 1.0D);
        Vec3 v = victim.getDeltaMovement();
        victim.setDeltaMovement(v.x * 0.2D + sideways.x * scale, Math.max(v.y, 0.0D) + up * scale, v.z * 0.2D + sideways.z * scale);
        victim.hurtMarked = true;
        victim.hasImpulse = true;
    }

    /** Dust and grit thrown up from the ground at a spot. */
    private void dust(Vec3 at, double spread)
    {
        if (!(this.golem.level() instanceof ServerLevel level))
        {
            return;
        }
        BlockState ground = level.getBlockState(BlockPos.containing(at.x, at.y - 0.5D, at.z));
        if (ground.isAir())
        {
            ground = level.getBlockState(BlockPos.containing(at.x, at.y - 1.5D, at.z));
        }
        if (!ground.isAir())
        {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, at.y + 0.2D, at.z, DUST, spread,
                    0.1D, spread, 0.2D);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.3D, at.z, 4, spread * 0.6D, 0.1D, spread * 0.6D, 0.01D);
    }

    private static Vec3 flat(Vec3 v)
    {
        return new Vec3(v.x, 0.0D, v.z);
    }

    private static double flatDistance(Vec3 a, Vec3 b)
    {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
