package com.reg21meme.dross.boss.ratking;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.BossChase;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * The Rat King's fighting AI (every number is in {@link RatKingTuning}): he runs at his target and, between pauses, makes
 * one of his moves (each one an animation, see {@link RatKingAction}):
 * <ul>
 *   <li><b>sweep</b> (close up): a flat sweep of his staff round his front, hitting everything in its arc (never his
 *       rats) and knocking it back;</li>
 *   <li><b>spear charge</b> (from {@link RatKingTuning#CHARGE_MIN} to {@link RatKingTuning#CHARGE_MAX} blocks, when he
 *       can see his target and the way is clear): he crouches with his staff levelled like a spear (turning to face his
 *       target), and at the end of the wind-up locks the spot where it stands and dashes straight there, faster than
 *       he runs, the staff held out like a lance. The first thing in his way takes a jab (small knockback) and he goes
 *       straight into a sweep if his target is in its reach; a miss (he gets to the spot) or a wall stops him and he
 *       needs a moment to recover;</li>
 *   <li><b>cast</b> (from a few blocks to {@link RatKingTuning#CAST_MAX}, when he can see his target): he flicks his
 *       staff and a {@link PlagueBall} flies from its orb on an arc to where his target will be (a fan of three in the
 *       Rage), bursting into a poison cloud where it lands.</li>
 * </ul>
 * Which one he makes is the user's table ({@link RatKingTuning#FIRST_CHANCES}, {@link RatKingTuning#RAGE_CHANCES}): by
 * how far his target is (close, mid, far, beyond: {@link Band}), rolled each time he's free to pick; a move on its
 * cooldown or that can't be made right now drops out and the others share its chance. "Run in" means he runs at his
 * target and doesn't roll again for {@link RatKingTuning#RUN_IN_TICKS} ticks (his close sweep may still come sooner). A
 * move always plays to its end, except when he burrows or flies into his rage ({@link #interrupt}); while he's busy
 * (asleep, waking, raging, burrowing) the AI waits.
 * <p>
 * He faces his target nearly all the time (the user's wish: about 90% of the fight): running straight at it when the
 * way is clear ({@link BossChase}), standing close, and through his moves, except the few ticks a sweep is committed
 * before its hit and the charge's dash (he runs where he aimed); he turns back to it while he recovers. (While he's busy,
 * {@link RatKing} turns him: through his rage, before he digs in, and as he pops up.)
 */
public class RatKingAttackGoal extends Goal
{
    /** His moves, each with the action (animation) it starts with. */
    enum Move
    {
        SWEEP(RatKingAction.SWEEP),
        CAST(RatKingAction.CAST),
        CHARGE(RatKingAction.CHARGE_START);

        final RatKingAction action;

        Move(RatKingAction action)
        {
            this.action = action;
        }

        /** Ticks before he can make this move again (first phase, or the Rage). */
        int cooldown(boolean rage)
        {
            return switch (this)
            {
                case SWEEP -> rage ? RatKingTuning.RAGE_SWEEP_COOLDOWN : RatKingTuning.SWEEP_COOLDOWN;
                case CAST -> rage ? RatKingTuning.RAGE_CAST_COOLDOWN : RatKingTuning.CAST_COOLDOWN;
                case CHARGE -> rage ? RatKingTuning.RAGE_CHARGE_COOLDOWN : RatKingTuning.CHARGE_COOLDOWN;
            };
        }
    }

    /** What he does when he's free to pick: one of his moves, or run in at his target. */
    enum Choice
    {
        SWEEP(Move.SWEEP),
        CHARGE(Move.CHARGE),
        POISON(Move.CAST),
        RUN_IN(null);

        @Nullable
        final Move move;

        Choice(@Nullable Move move)
        {
            this.move = move;
        }
    }

    /** How far his target is, for the move table (the lines of {@link RatKingTuning#FIRST_CHANCES}, in this order). */
    enum Band
    {
        /** In his sweep's reach. */
        CLOSE,
        /** {@link RatKingTuning#BAND_MID_FROM} to {@link RatKingTuning#BAND_FAR_FROM} blocks, in sight. */
        MID,
        /** To {@link RatKingTuning#BAND_FAR_TO} blocks, in sight. */
        FAR,
        /** Further, or out of sight. */
        BEYOND
    }

    /**
     * One spear charge's dash, for {@link RatKingFightGameTest}: where it set off and the spot it locked, where it
     * stopped and how many ticks it ran, at which tick of the wind-up the spot locked, what it hit (if anything), and
     * whether it ran into a wall.
     */
    record Dash(Vec3 from, Vec3 spot, Vec3 end, int ticks, int lockedAt, @Nullable Entity hit, boolean wall)
    {
    }

    /** How often (ticks) he works out a new path to his target while he runs at it. */
    private static final int REPATH_TICKS = 8;
    /** He runs at his target this much faster than his speed attribute (1: his own speed). */
    private static final double RUN_SPEED = 1.0D;
    /** He stops turning to face his target this many ticks before the sweep's hit (so a dodge counts). */
    private static final int SWEEP_TRACK_UNTIL = 3;
    /** The sweep's trail of green sparks: how many points along the arc, how big. */
    private static final int SWEEP_TRAIL_POINTS = 14;
    private static final float SWEEP_TRAIL_SIZE = 1.0F;
    /** The middle of a poison ball is this far above where it is (its position is the bottom of its box). */
    private static final double BALL_HALF = 0.25D;
    /** How loud the moves' sounds are, and the pitch of the lance's jab and the wall's crack. */
    private static final float SWEEP_VOLUME = 1.2F;
    private static final float CAST_VOLUME = 1.2F;
    private static final float CHARGE_VOLUME = 1.4F;
    private static final float JAB_VOLUME = 1.2F;
    private static final float JAB_PITCH = 1.3F;
    private static final float WALL_VOLUME = 1.2F;
    private static final float WALL_PITCH = 1.1F;
    private static final float SKID_VOLUME = 0.9F;
    private static final float SKID_PITCH = 1.4F;
    /** A thing in the dash's way counts if it's this far below his feet at most (and anywhere up his body). */
    private static final double CHARGE_HIT_BELOW = 0.5D;

    private final RatKing king;
    private final EnumMap<Move, Integer> cooldowns = new EnumMap<>(Move.class);
    @Nullable
    private Move current;
    /** Ticks before he picks his next move (he keeps running at his target meanwhile). */
    private int pause;
    /** Ticks of "run in" left: he runs at his target and doesn't roll for a move. */
    private int runIn;
    private int repath;
    /** Where his target was last seen: a move keeps aiming there if the target dies or vanishes mid-move. */
    @Nullable
    private Vec3 lastSeen;
    /** How many poison balls his last cast threw (for the tests). */
    private int lastBalls;

    // The spear charge's dash: the spot it runs to, where it set off, the way it runs, how far and how long at most,
    // how fast, how long it has run, and when the spot locked.
    @Nullable
    private Vec3 chargeSpot;
    private Vec3 chargeFrom = Vec3.ZERO;
    private Vec3 chargeDir = Vec3.ZERO;
    private double chargeRun;
    private int chargeMaxTicks;
    private double chargeSpeed;
    private int dashTicks;
    private int lockedAt;
    @Nullable
    private Dash lastDash;

    public RatKingAttackGoal(RatKing king)
    {
        this.king = king;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        if (!this.king.isFightActive() || this.king.isBusy())
        {
            return false;
        }
        LivingEntity target = this.king.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public boolean canContinueToUse()
    {
        if (!this.king.isFightActive() || this.king.isBusy())
        {
            return false;
        }
        if (this.current != null)
        {
            return true; // a move plays to its end
        }
        LivingEntity target = this.king.getTarget();
        return target != null && target.isAlive();
    }

    @Override
    public void start()
    {
        this.pause = Math.max(this.pause, this.attackPause());
        this.king.setAggressive(true);
    }

    @Override
    public void stop()
    {
        if (this.current != null && !this.king.isBusy() && this.king.isAlive())
        {
            this.finish();
        }
        this.current = null;
        this.runIn = 0;
        this.stopMoving();
        this.king.getNavigation().stop();
        this.king.setAggressive(false);
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    /** He burrows or flies into a rage (its own animation plays): the move in progress is dropped (a dash stops dead). */
    void interrupt()
    {
        if (this.current == Move.CHARGE)
        {
            this.stopMoving();
            this.cooldowns.put(Move.CHARGE, Move.CHARGE.cooldown(this.king.isEnraged()));
        }
        this.current = null;
        this.chargeSpot = null;
        this.runIn = 0;
        this.pause = this.attackPause();
    }

    private int attackPause()
    {
        return this.king.isEnraged() ? RatKingTuning.RAGE_ATTACK_PAUSE : RatKingTuning.ATTACK_PAUSE;
    }

    @Override
    public void tick()
    {
        this.cooldowns.replaceAll((move, left) -> Math.max(0, left - 1));
        LivingEntity target = this.king.getTarget();
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
        if (this.pause > 0)
        {
            this.pause--;
            this.approach(target);
            return;
        }
        if (this.runIn > 0)
        {
            // Running in: no roll, except the close band's sweep (it's certain, not a roll) once his target is in reach.
            this.runIn--;
            if (this.band(target) == Band.CLOSE && this.fits(Move.SWEEP, target))
            {
                this.runIn = 0;
                this.begin(Move.SWEEP, target);
                return;
            }
            this.approach(target);
            return;
        }
        Choice choice = this.choose(target);
        if (choice == null)
        {
            this.approach(target); // between his sweep's reach and the mid band, or nothing fits: he runs in
            return;
        }
        if (choice.move == null)
        {
            this.runIn = RatKingTuning.RUN_IN_TICKS;
            this.approach(target);
            return;
        }
        this.begin(choice.move, target);
    }

    /** Starts a move at a target. Package-private for {@link RatKingFightGameTest}, which starts moves itself. */
    void begin(Move move, LivingEntity target)
    {
        this.current = move;
        this.lastSeen = target.position();
        this.chargeSpot = null;
        if (move == Move.CHARGE)
        {
            this.lastDash = null;
        }
        this.king.getNavigation().stop();
        this.king.startAction(move.action);
    }

    // ---------------------------------------------------------------- choosing a move

    /** How far his target is (null: between his sweep's reach and the mid band, where he just runs in). */
    @Nullable
    Band band(LivingEntity target)
    {
        if (this.inSweepReach(target))
        {
            return Band.CLOSE;
        }
        double dist = flatDistance(this.king.position(), target.position());
        if (dist < RatKingTuning.BAND_MID_FROM)
        {
            return null;
        }
        if (dist > RatKingTuning.BAND_FAR_TO || !this.king.getSensing().hasLineOfSight(target))
        {
            return Band.BEYOND;
        }
        return dist < RatKingTuning.BAND_FAR_FROM ? Band.MID : Band.FAR;
    }

    /** His line of the move table for a band, in this phase. */
    static RatKingTuning.Chances chances(boolean rage, Band band)
    {
        return (rage ? RatKingTuning.RAGE_CHANCES : RatKingTuning.FIRST_CHANCES).get(band.ordinal());
    }

    /**
     * What he does now (at random, by his table: moves that don't fit right now drop out and the others share their
     * chance), or null if nothing does (between the bands, or his sweep is on its cooldown up close). It changes nothing,
     * so {@link RatKingFightGameTest} can roll it many times.
     */
    @Nullable
    Choice choose(LivingEntity target)
    {
        Band band = this.band(target);
        if (band == null)
        {
            return null;
        }
        RatKingTuning.Chances line = chances(this.king.isEnraged(), band);
        EnumMap<Choice, Integer> weights = new EnumMap<>(Choice.class);
        weights.put(Choice.SWEEP, line.sweep());
        weights.put(Choice.CHARGE, line.charge());
        weights.put(Choice.POISON, line.poison());
        weights.put(Choice.RUN_IN, line.runIn());
        Set<Choice> available = EnumSet.noneOf(Choice.class);
        for (Choice choice : Choice.values())
        {
            if (weights.get(choice) > 0 && (choice.move == null || this.fits(choice.move, target)))
            {
                available.add(choice);
            }
        }
        return pick(weights, available, this.king.getRandom());
    }

    /** One of the available choices at random, by their weights (the others' share is spread over them), or null. */
    @Nullable
    static Choice pick(EnumMap<Choice, Integer> weights, Set<Choice> available, RandomSource random)
    {
        int total = 0;
        for (Choice choice : available)
        {
            total += Math.max(0, weights.getOrDefault(choice, 0));
        }
        if (total <= 0)
        {
            return null;
        }
        int roll = random.nextInt(total);
        for (Choice choice : available)
        {
            roll -= Math.max(0, weights.getOrDefault(choice, 0));
            if (roll < 0)
            {
                return choice;
            }
        }
        return null;
    }

    /** True if his target is in his sweep's reach (a little inside it, so it's still in reach at the hit). */
    boolean inSweepReach(LivingEntity target)
    {
        double dist = flatDistance(this.king.position(), target.position());
        double dy = target.getY() - this.king.getY();
        return dist - target.getBbWidth() / 2.0D <= RatKingTuning.SWEEP_RANGE - RatKingTuning.SWEEP_START_MARGIN
                && dy > -RatKingTuning.SWEEP_BELOW && dy < RatKingTuning.SWEEP_ABOVE;
    }

    /** True if he can make this move at the target right now (ready, in range, in sight, a clear run). */
    boolean fits(Move move, LivingEntity target)
    {
        if (this.cooldowns.getOrDefault(move, 0) > 0)
        {
            return false;
        }
        double dist = flatDistance(this.king.position(), target.position());
        return switch (move)
        {
            case SWEEP -> this.inSweepReach(target);
            case CAST -> dist >= RatKingTuning.CAST_MIN && dist <= RatKingTuning.CAST_MAX
                    && this.king.getSensing().hasLineOfSight(target);
            case CHARGE -> dist >= RatKingTuning.CHARGE_MIN && dist <= RatKingTuning.CHARGE_MAX
                    && Math.abs(target.getY() - this.king.getY()) <= RatKingTuning.CHARGE_MAX_RISE
                    && this.king.getSensing().hasLineOfSight(target) && this.clearRun(target);
        };
    }

    /**
     * True if the dash at the target is clear-ish: nothing solid and no liquid in the first
     * {@link RatKingTuning#CHARGE_CLEAR_SHARE} of the way (low steps are fine: he runs up them). A wall further on stops
     * him then.
     */
    private boolean clearRun(LivingEntity target)
    {
        Vec3 dir = flat(target.position().subtract(this.king.position()));
        if (dir.lengthSqr() < 1.0E-4D)
        {
            return false;
        }
        double run = Math.min(RatKingTuning.CHARGE_MAX_DISTANCE, dir.length());
        dir = dir.normalize();
        AABB box = this.king.getBoundingBox().move(0.0D, this.king.maxUpStep(), 0.0D);
        double check = run * RatKingTuning.CHARGE_CLEAR_SHARE;
        for (double d = RatKingTuning.CHARGE_CHECK_STEP; d <= check + 1.0E-6D; d += RatKingTuning.CHARGE_CHECK_STEP)
        {
            AABB at = box.move(dir.scale(d));
            if (!this.king.level().noCollision(this.king, at)
                    || this.king.level().containsAnyLiquid(at.expandTowards(0.0D, -this.king.maxUpStep(), 0.0D)))
            {
                return false;
            }
        }
        return true;
    }

    /** Runs at the target, or stands and faces it once it's close. */
    private void approach(LivingEntity target)
    {
        double edge = flatDistance(this.king.position(), target.position()) - this.king.getBbWidth() / 2.0D
                - target.getBbWidth() / 2.0D;
        if (edge <= RatKingTuning.RUN_STOP)
        {
            this.king.getNavigation().stop();
            this.king.turnTowards(target.position(), this.king.turnSpeed());
            return;
        }
        this.king.getLookControl().setLookAt(target, 30.0F, 30.0F);
        // Straight at it when the way is clear (facing it as he runs), else along a path.
        this.repath = BossChase.chase(this.king, target, RUN_SPEED, this.repath, REPATH_TICKS);
    }

    // ---------------------------------------------------------------- running a move

    /** Ticks into the move when something {@code animTicks} into its animation happens (GeckoLib blends in first). */
    private static int at(int animTicks)
    {
        return RatKing.ANIMATION_BLEND_TICKS + animTicks;
    }

    private void run(@Nullable LivingEntity target)
    {
        int t = this.king.ticksInAction();
        RatKingDesigns.Design d = this.king.getDesign();
        Vec3 aim = target != null && target.isAlive() ? target.position() : this.lastSeen;
        switch (this.current)
        {
            case SWEEP -> {
                // He keeps turning to face his target, except for the moment the blow is committed (so a dodge counts);
                // after the hit he turns back to it during the follow-through.
                int hit = at(d.sweepHit());
                if ((t < hit - SWEEP_TRACK_UNTIL || t > hit) && aim != null)
                {
                    this.king.turnTowards(aim, this.king.turnSpeed());
                }
                if (t == hit)
                {
                    this.sweepHit(d);
                }
                if (t >= at(d.sweepTicks()))
                {
                    this.finish();
                }
            }
            case CAST -> {
                // He aims at his target until the ball leaves the orb, and keeps facing it as he eases back.
                if (t != at(d.castRelease()) && aim != null)
                {
                    this.king.turnTowards(aim, this.king.turnSpeed());
                }
                if (t == at(d.castRelease()))
                {
                    this.cast(target, aim);
                }
                if (t >= at(d.castTicks()))
                {
                    this.finish();
                }
            }
            case CHARGE -> this.runCharge(t, d, target, aim);
        }
    }

    /** The move is over: back to standing, its cooldown starts and he pauses before the next one. */
    private void finish()
    {
        Move move = this.current;
        this.current = null;
        if (move == null)
        {
            return;
        }
        if (this.king.getAction() == move.action || move == Move.CHARGE && this.king.getAction().isCharge())
        {
            this.king.startAction(RatKingAction.IDLE);
        }
        if (move == Move.CHARGE)
        {
            this.stopMoving();
            this.chargeSpot = null;
        }
        boolean rage = this.king.isEnraged();
        this.cooldowns.put(move, move.cooldown(rage));
        this.pause = Math.max(this.pause, this.attackPause());
    }

    // ---------------------------------------------------------------- the moves

    /**
     * The sweep's hit: everything within {@link RatKingTuning#SWEEP_RANGE} of his middle, in the
     * {@link RatKingTuning#SWEEP_ARC} in front of him, is hit and knocked back (never his rats or himself).
     */
    private void sweepHit(RatKingDesigns.Design d)
    {
        Vec3 fwd = this.king.forward();
        double range = RatKingTuning.SWEEP_RANGE;
        double minCos = Math.cos(Math.toRadians(RatKingTuning.SWEEP_ARC / 2.0D));
        float damage = this.king.isEnraged() ? RatKingTuning.RAGE_SWEEP_DAMAGE : RatKingTuning.SWEEP_DAMAGE;
        AABB area = this.king.getBoundingBox().inflate(range, RatKingTuning.SWEEP_ABOVE, range);
        for (Entity victim : this.king.level().getEntities(this.king, area, this.king::canHit))
        {
            Vec3 to = flat(victim.position().subtract(this.king.position()));
            double dist = to.length();
            double dy = victim.getY() - this.king.getY();
            if (dist - victim.getBbWidth() / 2.0D > range || dy <= -RatKingTuning.SWEEP_BELOW || dy >= RatKingTuning.SWEEP_ABOVE
                    || (dist > 0.5D && to.scale(1.0D / dist).dot(fwd) < minCos))
            {
                continue;
            }
            if (victim.hurt(this.king.blow(), damage) && victim instanceof LivingEntity living)
            {
                living.knockback(RatKingTuning.SWEEP_KNOCKBACK, this.king.getX() - victim.getX(), this.king.getZ() - victim.getZ());
            }
        }
        this.king.sound(ModSounds.RAT_KING_ATTACK_STAFF.get(), SWEEP_VOLUME, 1.0F);
        this.sweepTrail(d);
    }

    /** Green sparks along the arc his staff's orb sweeps through (cosmetic). */
    private void sweepTrail(RatKingDesigns.Design d)
    {
        if (!(this.king.level() instanceof ServerLevel level))
        {
            return;
        }
        Vec3 fwd = this.king.forward();
        Vec3 right = this.king.right();
        double half = Math.toRadians(RatKingTuning.SWEEP_ARC / 2.0D);
        double reach = d.sweepReach();
        double height = d.castUp() * 0.78D; // about chest height, where the orb passes
        DustParticleOptions spark = new DustParticleOptions(DrossColors.vector(DrossColors.RAT_KING_SWEEP_TRAIL), SWEEP_TRAIL_SIZE);
        for (int i = 0; i < SWEEP_TRAIL_POINTS; i++)
        {
            // From his right round to his left, the way the staff sweeps.
            double a = -half + 2.0D * half * i / (SWEEP_TRAIL_POINTS - 1);
            Vec3 dir = fwd.scale(Math.cos(a)).add(right.scale(-Math.sin(a)));
            Vec3 p = this.king.position().add(dir.scale(reach)).add(0.0D, height, 0.0D);
            level.sendParticles(spark, p.x, p.y, p.z, 1, 0.05D, 0.05D, 0.05D, 0.0D);
        }
    }

    /**
     * The cast: poison balls leave his staff's orb on an arc to where the target will be (leading a moving target): one
     * in the first phase, a fan of three in the Rage ({@link RatKingTuning#FAN_SPREAD} degrees apart).
     */
    private void cast(@Nullable LivingEntity target, @Nullable Vec3 aim)
    {
        if (aim == null || !(this.king.level() instanceof ServerLevel level))
        {
            return;
        }
        Vec3 from = this.king.castPoint().subtract(0.0D, BALL_HALF, 0.0D);
        Vec3 goal = target != null && target.isAlive()
                ? target.position().add(0.0D, target.getBbHeight() * 0.5D - BALL_HALF, 0.0D) : aim;
        int ticks = Mth.clamp((int) Math.round(flatDistance(from, goal) / RatKingTuning.BALL_SPEED),
                RatKingTuning.BALL_MIN_TICKS, RatKingTuning.BALL_MAX_TICKS);
        if (target != null && target.isAlive())
        {
            // Lead a moving target (a share of where it would be by the time the ball gets there).
            Vec3 moving = flat(target.position().subtract(target.xo, target.yo, target.zo));
            goal = goal.add(moving.scale(ticks * RatKingTuning.BALL_LEAD));
        }
        List<Double> angles = new ArrayList<>();
        int balls = this.king.isEnraged() ? RatKingTuning.RAGE_CAST_BALLS : RatKingTuning.CAST_BALLS;
        for (int i = 0; i < balls; i++)
        {
            // 0, then -spread, +spread, -2 spread...
            int side = (i + 1) / 2;
            angles.add(side * RatKingTuning.FAN_SPREAD * (i % 2 == 1 ? -1.0D : 1.0D));
        }
        Vec3 offset = goal.subtract(from);
        for (double degrees : angles)
        {
            Vec3 turned = rotate(offset, degrees);
            PlagueBall ball = PlagueBall.cast(level, this.king, from);
            ball.aim(from.add(turned.x, offset.y, turned.z), ticks);
            level.addFreshEntity(ball);
        }
        this.lastBalls = balls;
        this.king.sound(ModSounds.RAT_KING_ATTACK_ORB.get(), CAST_VOLUME, 1.0F);
    }

    /** How many poison balls his last cast threw. For {@link RatKingFightGameTest}. */
    int lastBalls()
    {
        return this.lastBalls;
    }

    // ---------------------------------------------------------------- the spear charge

    /**
     * The spear charge, tick by tick: the wind-up (he turns to face his target), then at
     * {@link RatKingTuning#CHARGE_WINDUP_TICKS} the spot locks and he dashes; the dash ends with a hit (straight into a
     * sweep, or a recovery), at the spot (a miss: a skid and a recovery) or at a wall (a recovery); the recovery lasts
     * {@link RatKingTuning#CHARGE_RECOVER_TICKS}, he turns back to his target meanwhile, and then the move is over.
     */
    private void runCharge(int t, RatKingDesigns.Design d, @Nullable LivingEntity target, @Nullable Vec3 aim)
    {
        switch (this.king.getAction())
        {
            case CHARGE_START -> {
                if (t < RatKingTuning.CHARGE_WINDUP_TICKS)
                {
                    this.stopMoving();
                    if (aim != null)
                    {
                        this.king.turnTowards(aim, this.king.turnSpeed());
                    }
                    return;
                }
                // The end of the wind-up: the spot where his target stands now is where he runs.
                this.lockedAt = t;
                this.startDash(aim != null ? aim : this.king.position().add(this.king.forward()));
                this.dashTick(d, target);
            }
            case CHARGE -> this.dashTick(d, target);
            case CHARGE_SKID -> {
                this.stopMoving();
                if (aim != null)
                {
                    this.king.turnTowards(aim, this.king.turnSpeed());
                }
                if (t >= RatKingTuning.CHARGE_RECOVER_TICKS)
                {
                    this.finish();
                }
            }
            default -> this.finish(); // (something else took over his body)
        }
    }

    /** The spot is locked: he faces it and sets off at his dash speed (at most {@link RatKingTuning#CHARGE_MAX_DISTANCE}). */
    private void startDash(Vec3 spot)
    {
        Vec3 dir = flat(spot.subtract(this.king.position()));
        this.chargeSpot = spot;
        this.chargeDir = dir.lengthSqr() > 1.0E-4D ? dir.normalize() : this.king.forward();
        this.chargeRun = Math.min(RatKingTuning.CHARGE_MAX_DISTANCE, dir.length());
        this.chargeFrom = this.king.position();
        this.chargeSpeed = this.king.isEnraged() ? RatKingTuning.RAGE_CHARGE_SPEED : RatKingTuning.CHARGE_SPEED;
        this.chargeMaxTicks = (int) Math.ceil(this.chargeRun / this.chargeSpeed) + RatKingTuning.CHARGE_SLACK_TICKS;
        this.dashTicks = 0;
        this.king.face(yawOf(this.chargeDir));
        this.king.startAction(RatKingAction.CHARGE);
        this.king.sound(ModSounds.RAT_KING_CHARGE.get(), CHARGE_VOLUME, 1.0F);
    }

    /** One tick of the dash: a wall or a hit stops it, and so does getting to the spot; otherwise on he runs. */
    private void dashTick(RatKingDesigns.Design d, @Nullable LivingEntity target)
    {
        if (this.dashTicks > 0 && this.king.horizontalCollision)
        {
            // Ran into a wall: the lance cracks against it, and he recovers.
            this.king.sound(ModSounds.RAT_KING_STAFF_SLAM.get(), WALL_VOLUME, WALL_PITCH);
            this.endDash(null, true);
            this.recover();
            return;
        }
        Entity hit = this.firstInPath(d);
        if (hit != null)
        {
            this.jab(hit, target);
            return;
        }
        double gone = flatDistance(this.chargeFrom, this.king.position());
        if (gone >= this.chargeRun - RatKingTuning.CHARGE_ARRIVED || this.dashTicks >= this.chargeMaxTicks)
        {
            // At the spot and nothing there (his target dodged): he skids to a stop.
            this.king.sound(ModSounds.RAT_KING_DIG.get(), SKID_VOLUME, SKID_PITCH);
            this.endDash(null, false);
            this.recover();
            return;
        }
        // Straight on; the last stride is just long enough to stop on the spot.
        double step = Math.min(this.chargeSpeed, this.chargeRun - gone);
        Vec3 v = this.king.getDeltaMovement();
        this.king.setDeltaMovement(this.chargeDir.x * step, v.y, this.chargeDir.z * step);
        this.king.face(yawOf(this.chargeDir));
        this.dashTicks++;
    }

    /**
     * The first thing in the dash's way: alive, one his blows may hit (never his own rats), not a player in creative or
     * spectator mode, within the lance's reach in front of his middle ({@link RatKingDesigns.Design#chargeReach}) and no
     * further to the side than his body (plus {@link RatKingTuning#CHARGE_HIT_SIDE}). The nearest one, or null.
     */
    @Nullable
    private Entity firstInPath(RatKingDesigns.Design d)
    {
        double reach = d.chargeReach();
        double side = this.king.getBbWidth() / 2.0D + RatKingTuning.CHARGE_HIT_SIDE;
        Vec3 dir = this.chargeDir;
        Vec3 here = this.king.position();
        AABB area = this.king.getBoundingBox().expandTowards(dir.scale(reach)).inflate(side, 0.0D, side);
        Entity best = null;
        double bestAlong = Double.MAX_VALUE;
        for (Entity e : this.king.level().getEntities(this.king, area, this.king::canHit))
        {
            if (e instanceof Player player && (player.isCreative() || player.isSpectator()))
            {
                continue;
            }
            Vec3 to = flat(e.position().subtract(here));
            double along = to.dot(dir);
            double across = Math.abs(to.x * dir.z - to.z * dir.x);
            double half = e.getBbWidth() / 2.0D;
            boolean overlaps = e.getY() < this.king.getY() + this.king.getBbHeight()
                    && e.getY() + e.getBbHeight() > this.king.getY() - CHARGE_HIT_BELOW;
            if (along + half < 0.0D || along - half > reach || across - half > side || !overlaps)
            {
                continue;
            }
            if (along < bestAlong)
            {
                bestAlong = along;
                best = e;
            }
        }
        return best;
    }

    /**
     * The lance strikes: {@link RatKingTuning#CHARGE_DAMAGE} ({@link RatKingTuning#RAGE_CHARGE_DAMAGE} in the Rage) and
     * a small knockback, and he stops. If his target is in his sweep's reach he goes straight into a sweep (whatever its
     * cooldown: it's part of the charge); otherwise he recovers.
     */
    private void jab(Entity victim, @Nullable LivingEntity target)
    {
        float damage = this.king.isEnraged() ? RatKingTuning.RAGE_CHARGE_DAMAGE : RatKingTuning.CHARGE_DAMAGE;
        if (victim.hurt(this.king.blow(), damage) && victim instanceof LivingEntity living)
        {
            living.knockback(RatKingTuning.CHARGE_KNOCKBACK, this.king.getX() - victim.getX(), this.king.getZ() - victim.getZ());
        }
        this.king.sound(ModSounds.RAT_KING_ATTACK_STAFF.get(), JAB_VOLUME, JAB_PITCH);
        this.endDash(victim, false);
        if (target != null && target.isAlive() && this.inSweepReach(target))
        {
            this.cooldowns.put(Move.CHARGE, Move.CHARGE.cooldown(this.king.isEnraged()));
            this.begin(Move.SWEEP, target);
        }
        else
        {
            this.recover();
        }
    }

    /** The dash is over: he stops dead (no sliding on) and it's recorded (for the tests). */
    private void endDash(@Nullable Entity hit, boolean wall)
    {
        this.stopMoving();
        this.lastDash = new Dash(this.chargeFrom, this.chargeSpot != null ? this.chargeSpot : this.king.position(),
                this.king.position(), this.dashTicks, this.lockedAt, hit, wall);
    }

    /** A miss or a wall: he skids and straightens up ({@link RatKingTuning#CHARGE_RECOVER_TICKS}; he can't act meanwhile). */
    private void recover()
    {
        this.king.startAction(RatKingAction.CHARGE_SKID);
    }

    /** He stops moving sideways (his fall, if any, goes on). */
    private void stopMoving()
    {
        Vec3 v = this.king.getDeltaMovement();
        this.king.setDeltaMovement(0.0D, v.y, 0.0D);
    }

    /** True while his spear charge dashes in its straight line (from the end of the wind-up until it stops). */
    boolean isDashing()
    {
        return this.current == Move.CHARGE && this.king.getAction() == RatKingAction.CHARGE;
    }

    // ---------------------------------------------------------------- for the tests

    /** The move he's making now (null: none). For {@link RatKingFightGameTest}. */
    @Nullable
    Move current()
    {
        return this.current;
    }

    /** The spot his charge runs to, once the wind-up has locked it (null before). For {@link RatKingFightGameTest}. */
    @Nullable
    Vec3 chargeSpot()
    {
        return this.chargeSpot;
    }

    /** His last charge's dash (null: none yet). For {@link RatKingFightGameTest}. */
    @Nullable
    Dash lastDash()
    {
        return this.lastDash;
    }

    /** Ticks before he can make a move again. For {@link RatKingFightGameTest}. */
    int cooldownLeft(Move move)
    {
        return this.cooldowns.getOrDefault(move, 0);
    }

    /** Puts a move on its cooldown for this many ticks. For {@link RatKingFightGameTest}. */
    void setCooldown(Move move, int ticks)
    {
        this.cooldowns.put(move, ticks);
    }

    // ---------------------------------------------------------------- helpers

    /** The yaw (degrees, Minecraft's) that faces along a flat direction. */
    private static float yawOf(Vec3 dir)
    {
        return (float) (Mth.atan2(dir.z, dir.x) * Mth.RAD_TO_DEG) - 90.0F;
    }

    /** A vector turned this many degrees round the vertical (its height kept). */
    private static Vec3 rotate(Vec3 v, double degrees)
    {
        double a = Math.toRadians(degrees);
        double cos = Math.cos(a);
        double sin = Math.sin(a);
        return new Vec3(v.x * cos - v.z * sin, v.y, v.x * sin + v.z * cos);
    }

    private static Vec3 flat(Vec3 v)
    {
        return new Vec3(v.x, 0.0D, v.z);
    }

    static double flatDistance(Vec3 a, Vec3 b)
    {
        double dx = a.x - b.x;
        double dz = a.z - b.z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
