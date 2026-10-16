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
 *   <li><b>cast</b> (from a few blocks to {@link RatKingTuning#CAST_MAX}, when he can see his target): he flicks his
 *       staff and a {@link PlagueBall} flies from its orb on an arc to where his target will be (a fan of three in the
 *       Rage), bursting into a poison cloud where it lands.</li>
 * </ul>
 * Which move fits depends on how far his target is; when one fits and is ready he makes it (by weight, if more than one
 * fits), otherwise he keeps running at it (his run plays as fast as he moves). A move always plays to its end, except
 * when he burrows or flies into his rage ({@link #interrupt}); while he's busy (asleep, waking, raging, burrowing) the
 * AI waits.
 * <p>
 * He faces his target nearly all the time (the user's wish: about 90% of the fight): running straight at it when the
 * way is clear ({@link BossChase}), standing close, and through his moves, except the few ticks a sweep is committed
 * before its hit. (While he's busy, {@link RatKing}
 * turns him: through his rage, before he digs in, and as he pops up.)
 */
public class RatKingAttackGoal extends Goal
{
    /** His moves, each with its action (animation). */
    enum Move
    {
        SWEEP(RatKingAction.SWEEP),
        CAST(RatKingAction.CAST);

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
            };
        }

        /** Its share when more than one move fits (they're only ever made where they fit, so these rarely compete). */
        int weight()
        {
            return switch (this)
            {
                case SWEEP -> SWEEP_WEIGHT;
                case CAST -> CAST_WEIGHT;
            };
        }
    }

    /** The moves' shares when more than one fits at once. */
    private static final int SWEEP_WEIGHT = 100;
    private static final int CAST_WEIGHT = 100;
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
    /** How loud the moves' sounds are. */
    private static final float SWEEP_VOLUME = 1.2F;
    private static final float CAST_VOLUME = 1.2F;

    private final RatKing king;
    private final EnumMap<Move, Integer> cooldowns = new EnumMap<>(Move.class);
    @Nullable
    private Move current;
    /** Ticks before he picks his next move (he keeps running at his target meanwhile). */
    private int pause;
    private int repath;
    /** Where his target was last seen: a move keeps aiming there if the target dies or vanishes mid-move. */
    @Nullable
    private Vec3 lastSeen;
    /** How many poison balls his last cast threw (for the tests). */
    private int lastBalls;

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
        this.king.getNavigation().stop();
        this.king.setAggressive(false);
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    /** He burrows or flies into a rage (its own animation plays): the move in progress is dropped. */
    void interrupt()
    {
        this.current = null;
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
        Move move = this.choose(target);
        if (move == null)
        {
            this.approach(target);
            return;
        }
        this.begin(move, target);
    }

    /** Starts a move at a target. Package-private for {@link RatKingFightGameTest}, which starts moves itself. */
    void begin(Move move, LivingEntity target)
    {
        this.current = move;
        this.lastSeen = target.position();
        this.king.getNavigation().stop();
        this.king.startAction(move.action);
    }

    // ---------------------------------------------------------------- choosing a move

    /** A move he can make at his target right now (by weight, if more than one fits), or null if none fits. */
    @Nullable
    private Move choose(LivingEntity target)
    {
        Set<Move> available = EnumSet.noneOf(Move.class);
        int total = 0;
        for (Move move : Move.values())
        {
            if (this.fits(move, target))
            {
                available.add(move);
                total += move.weight();
            }
        }
        if (total <= 0)
        {
            return null;
        }
        RandomSource random = this.king.getRandom();
        int roll = random.nextInt(total);
        for (Move move : available)
        {
            roll -= move.weight();
            if (roll < 0)
            {
                return move;
            }
        }
        return null;
    }

    /** True if he can make this move at the target right now (ready, in range, in sight). */
    boolean fits(Move move, LivingEntity target)
    {
        if (this.cooldowns.getOrDefault(move, 0) > 0)
        {
            return false;
        }
        double dist = flatDistance(this.king.position(), target.position());
        double dy = target.getY() - this.king.getY();
        return switch (move)
        {
            case SWEEP -> dist - target.getBbWidth() / 2.0D <= RatKingTuning.SWEEP_RANGE - RatKingTuning.SWEEP_START_MARGIN
                    && dy > -RatKingTuning.SWEEP_BELOW && dy < RatKingTuning.SWEEP_ABOVE;
            case CAST -> dist >= RatKingTuning.CAST_MIN && dist <= RatKingTuning.CAST_MAX
                    && this.king.getSensing().hasLineOfSight(target);
        };
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
        if (this.king.getAction() == move.action)
        {
            this.king.startAction(RatKingAction.IDLE);
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

    // ---------------------------------------------------------------- helpers

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
