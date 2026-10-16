package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.boss.BossChase;
import com.reg21meme.dross.boss.rage.Rage;
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
import net.minecraft.world.entity.Mob;
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
 *   <li><b>throw</b> (Shell and Cracking): tears a rock out of the ground and throws it ({@link ColossusRock}; it never
 *       places a block);</li>
 *   <li><b>slam</b>: both fists raised high for about a second (the warning), then a shockwave that does little damage
 *       but throws players far;</li>
 *   <li><b>charge</b>: head down, it paws the ground (its eyes on its target), then gallops head first to the spot
 *       where its target stood half a second before the gallop (up to 32 blocks), hitting everything in its way; in
 *       the Cracking and Lava Core stages it leaves a trail of fire;</li>
 *   <li><b>lob</b> (Cracking and Lava Core): its volcanoes spit lava blobs ({@link LavaBlob}) that land a few blocks
 *       from the player and leave pools of temporary lava;</li>
 *   <li><b>summon</b> (Cracking and Lava Core, and any stage while a player's risen undead are near): it pounds the
 *       ground three times and minions rise at the pounds (to its left, its right, in front of it): stone golems, or
 *       lava golems in the Lava Core stage.</li>
 * </ul>
 * Which move it picks depends on its stage and how far its target is: the chance tables in {@link ColossusTuning}
 * ({@link #weights}). A move always plays to its end (even if its target dies), except when the Colossus changes stage
 * ({@link #interrupt}); the stage changes have their own attacks ({@link #stageChangeSummon}, {@link #stageChangeLob}).
 * Every hit it lands in the Lava Core stage sets you on fire. It never breaks a block.
 * <p>
 * It faces its target nearly all the time (the user's wish: about 90% of the fight): it walks straight at it when the
 * way is clear ({@link BossChase}), turns to it standing and through its moves ({@link #tracks}), except the moment a
 * move commits to a direction and the charge's gallop, and after a charge it wheels round to face it again.
 * <p>
 * Against Necromancy's risen undead (when they're within {@link ColossusTuning#RISEN_RANGE} blocks): a summon raises
 * one golem for every two of them, the golems go for them first, and while it has fewer golems than half of them it may
 * summon in every stage. When three or more attack it up close, its close-range moves lean from the swipe to the slam.
 */
public class ColossusAttackGoal extends Goal
{
    /** Its moves, each with its action (animation) and its cooldown in ticks (before the stage's scale). */
    enum Move
    {
        SWIPE(GolemAction.SWIPE, ColossusTuning.SWIPE_COOLDOWN),
        THROW(GolemAction.THROW, ColossusTuning.THROW_COOLDOWN),
        SLAM(GolemAction.SLAM, ColossusTuning.SLAM_COOLDOWN),
        CHARGE(GolemAction.CHARGE, ColossusTuning.CHARGE_COOLDOWN_SHELL),
        LOB(GolemAction.LOB, ColossusTuning.LOB_COOLDOWN),
        SUMMON(GolemAction.SUMMON, ColossusTuning.SUMMON_COOLDOWN);

        final GolemAction action;
        final int cooldown;

        Move(GolemAction action, int cooldown)
        {
            this.action = action;
            this.cooldown = cooldown;
        }

        /** Ticks before it can make this move again in this stage (the charge has its own cooldown per stage). */
        int cooldown(ColossusStage stage)
        {
            if (this == CHARGE)
            {
                return switch (stage)
                {
                    case SHELL -> ColossusTuning.CHARGE_COOLDOWN_SHELL;
                    case CRACKING -> ColossusTuning.CHARGE_COOLDOWN_CRACKING;
                    case LAVA_CORE -> ColossusTuning.CHARGE_COOLDOWN_CORE;
                };
            }
            return Math.round(this.cooldown * stage.cooldownScale);
        }
    }

    /** How far its target is (from its middle): which line of its stage's chance table counts. */
    enum Band
    {
        CLOSE, MID, FAR, VERY_FAR;

        /** The band for a distance, or null beyond the charge's reach (it walks closer). */
        @Nullable
        static Band of(double dist)
        {
            if (dist <= ColossusTuning.CLOSE_BAND)
            {
                return CLOSE;
            }
            if (dist <= ColossusTuning.MID_BAND)
            {
                return MID;
            }
            if (dist <= ColossusTuning.FAR_BAND)
            {
                return FAR;
            }
            return dist <= ColossusTuning.CHARGE_MAX_RANGE ? VERY_FAR : null;
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
    /** More than one minion at a pound: each next one rises this many degrees further round (alternating sides). */
    private static final double EXTRA_MINION_DEGREES = 35.0D;
    /** The charge checks for a clear run every this many blocks. */
    private static final double RUN_CHECK_STEP = 2.0D;
    /** A charge closer than this to its spot is already there. */
    private static final double ARRIVED = 0.05D;
    /** How far below/above a target's feet a blob may land (it settles on the ground there). */
    private static final int LAND_SEARCH_UP = 4;
    private static final int LAND_SEARCH_DOWN = 8;
    /** Tries to find a landing spot for a blob that isn't too close to any player. */
    private static final int LANDING_TRIES = 8;
    /** Particles for its impacts. */
    private static final int DUST = 30;

    private final CinderColossus golem;
    private final EnumMap<Move, Integer> cooldowns = new EnumMap<>(Move.class);
    @Nullable
    private Move current;
    /** Ticks before it picks its next move. */
    private int pause;
    private int repath;
    /** Ticks left of stepping in for a swipe it picked from a little out of reach (0: not stepping in). */
    private int stepIn;
    /** Ticks left of wheeling round to face its target after a charge (0: not wheeling). */
    private int wheel;
    /** Where its target was last seen: a move keeps aiming there if the target dies or vanishes mid-move. */
    @Nullable
    private Vec3 lastSeen;
    /** The charge: the spot it runs to (where its target stood when the wind-up locked it), where it set off, the way it gallops, how far it runs, and what it has already hit (each thing once). */
    @Nullable
    private Vec3 chargeSpot;
    private Vec3 chargeFrom = Vec3.ZERO;
    private Vec3 chargeDir = Vec3.ZERO;
    private double chargeRun;
    private int chargeTicks;
    private final Set<Integer> chargeHits = new HashSet<>();
    /** The summon in progress: how many minions it raises in all. */
    private int summonCount = ColossusTuning.SUMMON_COUNT;
    /** Where the blobs of its last lob were aimed (for the tests). */
    private List<Vec3> lastLandings = List.of();

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
        this.stepIn = 0;
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
        this.stepIn = 0;
        this.wheel = 0;
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
            this.stepIn = 0;
            return;
        }
        double dist = flatDistance(this.golem.position(), target.position());
        if (this.stepIn > 0)
        {
            // Stepping in for the swipe it picked.
            this.stepIn--;
            if (this.ready(Move.SWIPE) && this.inSwipeReach(target, dist))
            {
                this.begin(Move.SWIPE, target);
                return;
            }
            if (this.stepIn == 0 || dist > ColossusTuning.CLOSE_BAND)
            {
                this.stepIn = 0;
                this.pause = ColossusTuning.RETHINK_TICKS;
            }
            this.approach(target, dist);
            return;
        }
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
        if (move == Move.SWIPE && !this.inSwipeReach(target, dist))
        {
            this.stepIn = ColossusTuning.SWIPE_STEP_IN_TICKS;
            this.approach(target, dist);
            return;
        }
        this.begin(move, target);
    }

    /**
     * Starts a move at a target. The charge aims at the target now (its wind-up keeps following it until the spot
     * locks, see {@link #runCharge}); the summon works out how many minions it raises. Package-private for
     * {@link ColossusGameTest}, which starts moves itself.
     */
    void begin(Move move, LivingEntity target)
    {
        this.current = move;
        this.stepIn = 0;
        this.wheel = 0;
        this.lastSeen = target.position();
        this.golem.getNavigation().stop();
        if (move == Move.CHARGE)
        {
            this.chargeSpot = target.position();
        }
        if (move == Move.SUMMON)
        {
            this.summonCount = this.summonCount();
        }
        this.golem.startAction(move.action);
    }

    // ---------------------------------------------------------------- choosing a move

    /** A move it can make right now (at random, by its stage's chance table), or null if none fits. */
    @Nullable
    private Move choose(LivingEntity target, double dist)
    {
        Band band = Band.of(dist);
        if (band == null)
        {
            return null; // beyond the charge's reach: it walks closer
        }
        ColossusStage stage = this.golem.stage();
        int risen = this.golem.risenNear(ColossusTuning.RISEN_RANGE).size();
        int golems = this.golem.minions().size();
        boolean risenSummon = risenSummonOpen(risen, golems);
        boolean swarmed = band == Band.CLOSE && this.swarmCount() >= ColossusTuning.SWARM_ATTACKERS;
        EnumMap<Move, Integer> weights = weights(stage, band, swarmed, risenSummon);
        Set<Move> available = EnumSet.noneOf(Move.class);
        for (Move move : weights.keySet())
        {
            if (weights.get(move) > 0 && this.fits(move, target, dist, stage, golems, risenSummon))
            {
                available.add(move);
            }
        }
        return pick(weights, available, this.golem.getRandom());
    }

    /**
     * The chance (percent) of each move in a stage and distance band: its line of the stage's table in
     * {@link ColossusTuning}. {@code swarmed} (three or more risen undead attacking it up close) moves
     * {@link ColossusTuning#SWARM_SLAM_SHIFT} points of the close band from the swipe to the slam; {@code risenSummon}
     * (risen undead near, and fewer golems than half of them) gives the summon a chance where the table has none.
     */
    static EnumMap<Move, Integer> weights(ColossusStage stage, Band band, boolean swarmed, boolean risenSummon)
    {
        List<ColossusTuning.Chances> table = switch (stage)
        {
            case SHELL -> ColossusTuning.SHELL_CHANCES;
            case CRACKING -> ColossusTuning.CRACKING_CHANCES;
            case LAVA_CORE -> ColossusTuning.CORE_CHANCES;
        };
        ColossusTuning.Chances line = table.get(band.ordinal());
        int swipe = line.swipe();
        int slam = line.slam();
        if (swarmed && band == Band.CLOSE)
        {
            int shift = Math.min(ColossusTuning.SWARM_SLAM_SHIFT, swipe);
            swipe -= shift;
            slam += shift;
        }
        int summon = line.summon();
        if (risenSummon && summon == 0)
        {
            summon = ColossusTuning.RISEN_SUMMON_CHANCE;
        }
        EnumMap<Move, Integer> weights = new EnumMap<>(Move.class);
        weights.put(Move.SWIPE, swipe);
        weights.put(Move.THROW, line.throwRock());
        weights.put(Move.SLAM, slam);
        weights.put(Move.CHARGE, line.charge());
        weights.put(Move.LOB, line.lob());
        weights.put(Move.SUMMON, summon);
        return weights;
    }

    /** One of the available moves at random, by their weights (the others' share is spread over them), or null. */
    @Nullable
    static Move pick(EnumMap<Move, Integer> weights, Set<Move> available, RandomSource random)
    {
        int total = 0;
        for (Move move : available)
        {
            total += Math.max(0, weights.getOrDefault(move, 0));
        }
        if (total <= 0)
        {
            return null;
        }
        int roll = random.nextInt(total);
        for (Move move : available)
        {
            roll -= Math.max(0, weights.getOrDefault(move, 0));
            if (roll < 0)
            {
                return move;
            }
        }
        return null;
    }

    /** True while risen undead are near and it has fewer golems than half of them (it may summon in any stage). */
    static boolean risenSummonOpen(int risen, int golems)
    {
        return risen > 0 && golems * ColossusTuning.RISEN_PER_GOLEM < risen && golems < ColossusTuning.MAX_MINIONS;
    }

    /** True if it can make this move at its target right now (ready, in range, in sight...). */
    private boolean fits(Move move, LivingEntity target, double dist, ColossusStage stage, int golems, boolean risenSummon)
    {
        if (!this.ready(move))
        {
            return false;
        }
        double dy = target.getY() - this.golem.getY();
        return switch (move)
        {
            // (Out of reach but still close, it steps in first.)
            case SWIPE -> dist <= ColossusTuning.CLOSE_BAND && dy > -2.0D && dy < 4.0D;
            case SLAM -> dist <= ColossusTuning.SLAM_TRIGGER_RANGE && dy > -2.0D && dy < 3.0D;
            case SUMMON -> golems < ColossusTuning.MAX_MINIONS && (risenSummon || (stage != ColossusStage.SHELL
                    && golems < ColossusTuning.SUMMON_BELOW && dist <= ColossusTuning.SUMMON_RANGE));
            case LOB -> stage != ColossusStage.SHELL && dist >= ColossusTuning.LOB_MIN_RANGE
                    && dist <= ColossusTuning.LOB_MAX_RANGE && this.golem.getSensing().hasLineOfSight(target);
            case THROW -> stage != ColossusStage.LAVA_CORE && dist >= ColossusTuning.THROW_MIN_RANGE
                    && dist <= ColossusTuning.THROW_MAX_RANGE && this.golem.getSensing().hasLineOfSight(target);
            case CHARGE -> dist >= ColossusTuning.CHARGE_MIN_RANGE && dist <= ColossusTuning.CHARGE_MAX_RANGE
                    && Math.abs(dy) < ColossusTuning.CHARGE_MAX_RISE && this.golem.getSensing().hasLineOfSight(target)
                    && this.clearRun(target);
        };
    }

    /** True if it could make this move at this target right now (for {@link ColossusGameTest}'s fight tests). */
    boolean available(Move move, LivingEntity target)
    {
        double dist = flatDistance(this.golem.position(), target.position());
        int golems = this.golem.minions().size();
        boolean risenSummon = risenSummonOpen(this.golem.risenNear(ColossusTuning.RISEN_RANGE).size(), golems);
        return Band.of(dist) != null && this.fits(move, target, dist, this.golem.stage(), golems, risenSummon);
    }

    private boolean ready(Move move)
    {
        return this.cooldowns.getOrDefault(move, 0) <= 0;
    }

    /** True if its swipe reaches the target from where it stands. */
    private boolean inSwipeReach(LivingEntity target, double dist)
    {
        double edge = dist - this.golem.getBbWidth() / 2.0D - target.getBbWidth() / 2.0D;
        double dy = target.getY() - this.golem.getY();
        return edge <= ColossusTuning.SWIPE_REACH && dy > -2.0D && dy < 4.0D;
    }

    /** How many risen undead are attacking it within its slam's range. */
    int swarmCount()
    {
        int count = 0;
        for (Mob risen : this.golem.risenNear(ColossusTuning.SLAM_TRIGGER_RANGE))
        {
            if (risen.getTarget() == this.golem)
            {
                count++;
            }
        }
        return count;
    }

    /**
     * How many minions a summon raises: one for every two risen undead near it (half, rounded up) while there are any,
     * otherwise {@link ColossusTuning#SUMMON_COUNT}. (Never past {@link ColossusTuning#MAX_MINIONS} alive: each pound
     * checks.)
     */
    int summonCount()
    {
        int risen = this.golem.risenNear(ColossusTuning.RISEN_RANGE).size();
        if (risen > 0)
        {
            return (risen + ColossusTuning.RISEN_PER_GOLEM - 1) / ColossusTuning.RISEN_PER_GOLEM;
        }
        return ColossusTuning.SUMMON_COUNT;
    }

    /** Players (not in creative or spectator mode) within this many blocks of it. */
    private List<Player> playersNear(double range)
    {
        List<Player> near = new ArrayList<>();
        for (Player player : this.golem.level().players())
        {
            if (!player.isCreative() && !player.isSpectator() && player.isAlive() && player.distanceToSqr(this.golem) <= range * range)
            {
                near.add(player);
            }
        }
        return near;
    }

    /**
     * True if the run at the target is clear-ish: nothing solid stands in the first {@link ColossusTuning#CHARGE_CLEAR_SHARE}
     * of the way (low steps are fine: it climbs them). A wall further on stops it then.
     */
    private boolean clearRun(LivingEntity target)
    {
        Vec3 dir = flat(target.position().subtract(this.golem.position()));
        if (dir.lengthSqr() < 1.0E-4D)
        {
            return false;
        }
        dir = dir.normalize();
        AABB box = this.golem.getBoundingBox().move(0.0D, this.golem.maxUpStep(), 0.0D);
        double run = Math.min(ColossusTuning.CHARGE_MAX_DISTANCE, flatDistance(this.golem.position(), target.position()));
        double check = run * ColossusTuning.CHARGE_CLEAR_SHARE;
        for (double d = RUN_CHECK_STEP; d <= check; d += RUN_CHECK_STEP)
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
        if (this.wheel > 0)
        {
            // Just after a charge (it has run to where its target was): it digs in and wheels round to face it before
            // it walks on.
            this.wheel--;
            this.golem.getNavigation().stop();
            this.golem.turnTowards(target.position(), this.golem.stage().turnSpeed * ColossusTuning.CHARGE_WHEEL_TURN);
            if (Math.abs(Mth.degreesDifference(this.golem.getYRot(), yawTo(this.golem.position(), target.position())))
                    <= ColossusTuning.CHARGE_WHEEL_DONE)
            {
                this.wheel = 0;
            }
            return;
        }
        double edge = dist - this.golem.getBbWidth() / 2.0D - target.getBbWidth() / 2.0D;
        if (edge <= ColossusTuning.SWIPE_REACH * STOP_SHARE)
        {
            this.golem.getNavigation().stop();
            this.golem.turnTowards(target.position(), this.golem.stage().turnSpeed);
            return;
        }
        this.golem.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // Straight at it when the way is clear (facing it as it walks), else along a path.
        this.repath = BossChase.chase(this.golem, target, 1.0D, this.repath, REPATH_TICKS);
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
        if (aim != null && this.tracks(t, m))
        {
            // It keeps its body turned to its target through its moves (the user's wish: facing it about 90% of the fight).
            this.golem.turnTowards(aim, stage.turnSpeed);
        }
        switch (this.current)
        {
            case SWIPE -> {
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
            case CHARGE -> this.runCharge(t, m, stage, target);
            case LOB -> {
                if (t == at(0))
                {
                    this.golem.sound(ModSounds.COLOSSUS_RUMBLE.get(), 3.0F, 1.0F);
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
                        this.pound(k, pounds.size(), stage);
                    }
                }
                if (t >= at(m.summonTicks()))
                {
                    this.finish();
                }
            }
        }
    }

    /**
     * True if the move in progress lets it turn to face its target this tick: every moment except when the move has
     * committed to a direction: the last {@link ColossusTuning#SWIPE_COMMIT_TICKS} before the swipe's hit and the last
     * {@link ColossusTuning#SLAM_COMMIT_TICKS} before the slam's (so a dodge counts), the throw's and the lob's release,
     * and the whole charge ({@link #runCharge} turns it itself: towards its target in the wind-up, then the gallop runs
     * straight).
     */
    private boolean tracks(int t, GolemDesigns.Moves m)
    {
        return switch (this.current)
        {
            case SWIPE -> t < at(m.swipeHit()) - ColossusTuning.SWIPE_COMMIT_TICKS || t > at(m.swipeHit());
            case SLAM -> t < at(m.slamHit()) - ColossusTuning.SLAM_COMMIT_TICKS || t > at(m.slamHit());
            case THROW -> t != at(m.throwRelease());
            case LOB -> t != at(m.lobRelease());
            case SUMMON -> true;
            case CHARGE -> false;
        };
    }

    /** True while its charge gallops in its straight line (from the end of the wind-up until it stops). */
    boolean isGalloping()
    {
        return this.current == Move.CHARGE
                && this.golem.ticksInAction() >= at(this.golem.getDesign().moves().chargeStartTicks());
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
        this.cooldowns.put(move, move.cooldown(stage));
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
        dustRing(level, this.golem.modelPoint(m.slamAt()), ColossusTuning.SLAM_RADIUS * ring / 3.0D, 10 + 8 * ring);
    }

    /** A ring of dust thrown up from the ground round {@code centre} (cosmetic: the slam's and the roar's shockwaves). */
    static void dustRing(ServerLevel level, Vec3 centre, double r, int points)
    {
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
     * The charge: during the wind-up it keeps turning to face its target, and {@link ColossusTuning#CHARGE_AIM_LOCK_TICKS}
     * before the gallop it fixes the spot it will run to (where its target stands then) and lines up on it; then it
     * gallops in a straight line all the way to that spot (at most {@link ColossusTuning#CHARGE_MAX_DISTANCE} blocks),
     * unless it runs into something solid (then it's dazed for a moment) or runs out of time.
     */
    private void runCharge(int t, GolemDesigns.Moves m, ColossusStage stage, @Nullable LivingEntity target)
    {
        int windup = at(m.chargeStartTicks());
        if (t < windup - ColossusTuning.CHARGE_AIM_LOCK_TICKS && target != null && target.isAlive())
        {
            this.chargeSpot = target.position(); // not locked yet: it follows its target with its eyes
        }
        Vec3 spot = this.chargeSpot != null ? this.chargeSpot : this.lastSeen;
        if (t < windup - 2)
        {
            if (spot != null)
            {
                this.golem.turnTowards(spot, stage.turnSpeed * ColossusTuning.CHARGE_WINDUP_TURN);
            }
            return;
        }
        if (t == windup - 2)
        {
            this.golem.sound(ModSounds.COLOSSUS_CHARGE.get(), 3.0F, 1.0F);
        }
        if (t == windup)
        {
            Vec3 dir = spot != null ? flat(spot.subtract(this.golem.position())) : Vec3.ZERO;
            this.chargeDir = dir.lengthSqr() > 1.0E-4D ? dir.normalize() : this.golem.forward();
            this.chargeRun = Math.min(ColossusTuning.CHARGE_MAX_DISTANCE, dir.length());
            this.chargeFrom = this.golem.position();
            this.chargeTicks = (int) Math.ceil(this.chargeRun / ColossusTuning.CHARGE_SPEED) + ColossusTuning.CHARGE_SLACK_TICKS;
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
            this.stopCharge();
            this.pause = Math.max(this.pause, ColossusTuning.CHARGE_DAZED_TICKS);
            return;
        }
        double gone = flatDistance(this.chargeFrom, this.golem.position());
        if (t > windup && (gone >= this.chargeRun - ARRIVED || t > windup + this.chargeTicks))
        {
            this.stopCharge(); // there (or it gave up)
            this.wheel = ColossusTuning.CHARGE_WHEEL_TICKS; // then it wheels round to face its target
            return;
        }
        // The last stride is just long enough to stop on the spot.
        double step = Math.min(ColossusTuning.CHARGE_SPEED, this.chargeRun - gone);
        Vec3 v = this.golem.getDeltaMovement();
        this.golem.setDeltaMovement(this.chargeDir.x * step, v.y, this.chargeDir.z * step);
        float yaw = (float) (Mth.atan2(this.chargeDir.z, this.chargeDir.x) * Mth.RAD_TO_DEG) - 90.0F;
        this.golem.setYRot(yaw);
        this.golem.setYBodyRot(yaw);
        this.golem.setYHeadRot(yaw);
        this.chargeHit(stage);
        if (stage != ColossusStage.SHELL)
        {
            this.fireTrail(stage);
        }
        if ((t - windup) % 6 == 0)
        {
            this.golem.sound(ModSounds.COLOSSUS_GALLOP.get(), 2.0F, 1.0F);
        }
    }

    /** The charge is over: it digs in and stops (no sliding on), and the move ends. */
    private void stopCharge()
    {
        Vec3 v = this.golem.getDeltaMovement();
        this.golem.setDeltaMovement(0.0D, v.y, 0.0D);
        this.chargeSpot = null;
        this.finish();
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

    /**
     * Cracking and Lava Core stages: fire on the ground behind it as it gallops (it never spreads and goes out by
     * itself: after 4 to 5 seconds in the Lava Core, half that in the Cracking stage).
     */
    private void fireTrail(ColossusStage stage)
    {
        if (!(this.golem.level() instanceof ServerLevel level))
        {
            return;
        }
        boolean core = stage == ColossusStage.LAVA_CORE;
        int minTicks = core ? ColossusTuning.FIRE_TRAIL_MIN_TICKS : ColossusTuning.CRACKING_FIRE_MIN_TICKS;
        int maxTicks = core ? ColossusTuning.FIRE_TRAIL_MAX_TICKS : ColossusTuning.CRACKING_FIRE_MAX_TICKS;
        boolean plants = ForgeEventFactory.getMobGriefingEvent(level, this.golem);
        Vec3 behind = this.golem.position().subtract(this.chargeDir.scale(this.golem.getBbWidth() / 2.0D - 0.5D));
        Vec3 side = new Vec3(-this.chargeDir.z, 0.0D, this.chargeDir.x);
        for (int k = -1; k <= 1; k++)
        {
            Vec3 p = behind.add(side.scale(k * 1.1D));
            BlockPos pos = BlockPos.containing(p.x, this.golem.getY() + 0.2D, p.z);
            if (!level.getBlockState(pos).is(com.reg21meme.dross.registry.ModBlocks.CINDER_FIRE.get()))
            {
                LavaPools.placeFire(level, pos, plants, minTicks, maxTicks);
            }
        }
    }

    /**
     * The lob: lava blobs fly from its craters to spots a few blocks from the target (never on it), on opposite-ish
     * sides of it, each leaving a pool of temporary lava where it lands.
     */
    private void lob(@Nullable LivingEntity target, @Nullable Vec3 aim, ColossusStage stage, GolemDesigns.Moves m)
    {
        Vec3 centre = target != null && target.isAlive() ? target.position() : aim;
        if (centre == null)
        {
            return;
        }
        this.spew(List.of(centre), ColossusTuning.LOB_BLOBS, stage, m);
    }

    /**
     * Entering the Lava Core stage (the shell has burst off, after its roar): its volcanoes spew
     * {@link ColossusTuning#STAGE_CHANGE_LOB_BLOBS} blobs at once, landing round the players near it (shared out between
     * them; round its target if no player is near), never on anyone. Called by {@link CinderColossus} when the shell
     * bursting has finished.
     */
    void stageChangeLob()
    {
        List<Vec3> centres = new ArrayList<>();
        LivingEntity target = this.golem.getTarget();
        // (Raged by the Rage Wand and fighting a raged mob, it ignores the players: the blobs land round its enemy.)
        boolean ragedFight = Rage.areEnemies(this.golem, target);
        for (Player player : ragedFight ? List.<Player>of() : this.playersNear(ColossusTuning.BOSS_BAR_RANGE))
        {
            centres.add(player.position());
        }
        if (centres.isEmpty() && target != null && target.isAlive())
        {
            centres.add(target.position());
        }
        if (!centres.isEmpty())
        {
            this.spew(centres, ColossusTuning.STAGE_CHANGE_LOB_BLOBS, this.golem.stage(), this.golem.getDesign().moves());
        }
    }

    /**
     * Its volcanoes spit {@code blobs} lava blobs: blob i lands round centre i (taking turns when there are more blobs
     * than centres), each centre's blobs spread round it on opposite-ish sides, {@link ColossusTuning#LOB_LAND_MIN} to
     * {@link ColossusTuning#LOB_LAND_MAX} blocks out, and never that close to any player near it.
     */
    private void spew(List<Vec3> centres, int blobs, ColossusStage stage, GolemDesigns.Moves m)
    {
        if (!(this.golem.level() instanceof ServerLevel level) || centres.isEmpty())
        {
            return;
        }
        RandomSource random = this.golem.getRandom();
        List<Player> players = this.playersNear(ColossusTuning.BOSS_BAR_RANGE + ColossusTuning.LOB_LAND_MAX);
        List<GolemDesigns.Point> craters = m.craters();
        List<Vec3> landings = new ArrayList<>();
        int n = centres.size();
        double[] start = new double[n];
        for (int c = 0; c < n; c++)
        {
            start[c] = random.nextDouble() * 2.0D * Math.PI;
        }
        for (int i = 0; i < blobs; i++)
        {
            int c = i % n;
            int share = blobs / n + (c < blobs % n ? 1 : 0); // how many blobs land round this centre
            int j = i / n; // this one's turn round it
            Vec3 centre = centres.get(c);
            Vec3 spot = null;
            for (int tries = 0; tries < LANDING_TRIES && spot == null; tries++)
            {
                double angle = start[c] + 2.0D * Math.PI * j / share
                        + (random.nextDouble() - 0.5D) * 0.8D * Math.PI / Math.max(1, share);
                double dist = ColossusTuning.LOB_LAND_MIN
                        + random.nextDouble() * (ColossusTuning.LOB_LAND_MAX - ColossusTuning.LOB_LAND_MIN);
                Vec3 at = centre.add(Math.cos(angle) * dist, 0.0D, Math.sin(angle) * dist);
                if (clearOf(at, players) || tries == LANDING_TRIES - 1)
                {
                    spot = at;
                }
            }
            Vec3 landing = groundAt(level, spot);
            landings.add(landing);
            Vec3 from = craters.isEmpty() ? this.golem.position().add(0.0D, this.golem.getBbHeight(), 0.0D)
                    : this.golem.modelPoint(craters.get(i % craters.size()));
            LavaBlob blob = LavaBlob.spat(level, this.golem, from, ColossusTuning.LOB_DIRECT_DAMAGE * stage.damageScale);
            blob.aim(landing, ColossusTuning.LOB_FLIGHT_TICKS);
            level.addFreshEntity(blob);
        }
        this.lastLandings = List.copyOf(landings);
        this.golem.sound(ModSounds.COLOSSUS_LOB.get(), 3.0F, 1.0F);
    }

    /** True if a blob landing here keeps its distance from every one of these players. */
    private static boolean clearOf(Vec3 at, List<Player> players)
    {
        double keep = ColossusTuning.LOB_LAND_MIN - 1.0E-3D;
        for (Player player : players)
        {
            if (flatDistance(at, player.position()) < keep)
            {
                return false;
            }
        }
        return true;
    }

    /** Where the blobs of its last lob (or stage-change spew) were aimed. For {@link ColossusGameTest}. */
    List<Vec3> lastLandings()
    {
        return this.lastLandings;
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
     * One of the summoning's pounds (0: its left fist, 1: its right, 2: both): dust where it lands, and its share of
     * the summon's minions climbs out of the ground on that side of it (to its left, its right, in front of it), never
     * past {@link ColossusTuning#MAX_MINIONS} alive.
     */
    private void pound(int k, int pounds, ColossusStage stage)
    {
        if (!(this.golem.level() instanceof ServerLevel level))
        {
            return;
        }
        this.golem.sound(ModSounds.COLOSSUS_POUND.get(), 3.0F, k == 2 ? 0.8F : 1.0F);
        this.dust(this.golem.modelPoint(POUND_AT[Math.min(k, POUND_AT.length - 1)]), k == 2 ? 0.9D : 0.5D);
        int count = this.summonCount / pounds + (k < this.summonCount % pounds ? 1 : 0);
        Vec3 dir = switch (k)
        {
            case 0 -> this.golem.left();
            case 1 -> this.golem.left().scale(-1.0D);
            default -> this.golem.forward();
        };
        EntityType<? extends GolemMinion> type = stage == ColossusStage.LAVA_CORE ? ModEntities.LAVA_GOLEM.get()
                : ModEntities.STONE_GOLEM.get();
        int alive = this.golem.minions().size();
        for (int i = 0; i < count && alive < ColossusTuning.MAX_MINIONS; i++)
        {
            // More than one here: each next one a little further round, alternating sides (0, +35, -35, +70...).
            double degrees = EXTRA_MINION_DEGREES * ((i + 1) / 2) * (i % 2 == 0 ? -1.0D : 1.0D);
            if (this.raise(level, type, rotate(dir, degrees)))
            {
                alive++;
            }
        }
    }

    /**
     * Entering the Cracking stage (its eruption has finished, after its roar): {@link ColossusTuning#STAGE_CHANGE_MINIONS}
     * stone golems climb out of the ground at once, all round it (in front, to its right, behind, to its left), never
     * past {@link ColossusTuning#MAX_MINIONS} alive. Called by {@link CinderColossus} when the eruption has finished.
     */
    void stageChangeSummon()
    {
        if (!(this.golem.level() instanceof ServerLevel level))
        {
            return;
        }
        this.golem.sound(ModSounds.COLOSSUS_POUND.get(), 3.0F, 0.8F);
        Vec3 fwd = this.golem.forward();
        Vec3 left = this.golem.left();
        Vec3[] round = {fwd, left.scale(-1.0D), fwd.scale(-1.0D), left};
        int alive = this.golem.minions().size();
        for (int i = 0; i < ColossusTuning.STAGE_CHANGE_MINIONS && alive < ColossusTuning.MAX_MINIONS; i++)
        {
            Vec3 dir = round[i % round.length];
            if (i >= round.length)
            {
                dir = rotate(dir, 45.0D); // (more than four: in between)
            }
            if (this.raise(level, ModEntities.STONE_GOLEM.get(), dir))
            {
                alive++;
            }
        }
    }

    /** A minion of this type climbs out of the ground on this side of it (if there's room): true if it did. */
    private boolean raise(ServerLevel level, EntityType<? extends GolemMinion> type, Vec3 dir)
    {
        Vec3 feet = this.minionSpot(level, type, dir);
        if (feet == null)
        {
            return false;
        }
        this.dust(feet, 0.5D);
        return GolemMinion.summon(type, level, this.golem, feet, this.golem.getYRot()) != null;
    }

    /** A flat direction turned this many degrees (round the vertical). */
    private static Vec3 rotate(Vec3 dir, double degrees)
    {
        double a = Math.toRadians(degrees);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        return new Vec3(dir.x * cos - dir.z * sin, 0.0D, dir.x * sin + dir.z * cos);
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

    /**
     * Throws something: sideways (a horizontal velocity) and up, softened by half its knockback resistance. Also the
     * roar's throw ({@link CinderColossus#roar}).
     */
    static void knock(Entity victim, Vec3 sideways, double up)
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

    /** The yaw (Minecraft's: 0 south, 90 west) that faces from one spot towards another. */
    private static float yawTo(Vec3 from, Vec3 to)
    {
        return (float) (Mth.atan2(to.z - from.z, to.x - from.x) * Mth.RAD_TO_DEG) - 90.0F;
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
