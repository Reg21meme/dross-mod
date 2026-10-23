package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.boss.BossChase;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * How the Huntsman and the Alpha Moose fight ({@link HuntFight}): between moves a pause (facing their target), then a
 * move from their table for the distance ({@link HuntsmanTuning}: a move on cooldown or that can't be made drops out and
 * the others share its weight), played to its end. A move is its animation and what happens at its moments
 * ({@link HuntCues}: a blow lands, an arrow flies, a bola leaves the hand). The moves both of them use are here: running
 * in, the Antler Charge and the Back Kick (made as soon as a foe is behind the moose, without a roll).
 */
abstract class HuntGoal extends Goal
{
    /** One move: started once, then ticked (from 1) until it says it's done. */
    interface Move
    {
        String name();

        void start();

        boolean tick(int t);

        default void stop()
        {
        }
    }

    /** How long a path holds before it's worked out again, while it runs in. */
    private static final int REPATH = 10;

    protected final HuntMob mob;
    @Nullable
    protected LivingEntity target;
    @Nullable
    private Move move;
    private int t;
    private int pause;
    private final Map<String, Long> ready = new HashMap<>();

    protected HuntGoal(HuntMob mob)
    {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
    }

    /** The stage this goal fights in. */
    protected abstract boolean stageOk();

    protected abstract Object[][] table();

    /** The upper ends of the table's rows but the last. */
    protected abstract double[] bands();

    protected abstract int basePause();

    /** Can it make {@code name} now (the distance, in sight, its spear in hand...)? */
    protected abstract boolean can(String name, LivingEntity target, double distance);

    @Nullable
    protected abstract Move make(String name, LivingEntity target);

    protected abstract int cooldown(String name);

    /** When nothing in the row for {@code distance} can be made: the moose closes in, he keeps his range. */
    protected abstract String fallback(double distance);

    /** A move made at once, without a roll (the Back Kick), or null. */
    @Nullable
    protected Move react(LivingEntity target, double distance)
    {
        return null;
    }

    /** Free to start a move (not in the middle of one of its own animations). */
    protected boolean free()
    {
        return !this.mob.busy();
    }

    @Override
    public boolean canUse()
    {
        return !this.mob.isShowcase() && this.mob.isAlive() && this.stageOk() && this.mob.getTarget() != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return !this.mob.isShowcase() && this.mob.isAlive() && this.stageOk()
                && (this.mob.getTarget() != null || this.move != null);
    }

    @Override
    public void start()
    {
        this.pause = this.basePause();
        this.move = null;
    }

    @Override
    public void stop()
    {
        if (this.move != null)
        {
            this.move.stop();
            this.move = null;
        }
        this.mob.forcedLoop = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        this.target = this.mob.getTarget();
        if (this.move != null)
        {
            boolean done = this.move.tick(++this.t) || !this.stageOk() || !this.mob.isAlive();
            if (done)
            {
                this.move.stop();
                this.cool(this.move.name());
                this.move = null;
                this.pause = this.mob.isAngry() ? HuntsmanTuning.PAUSE_ANGRY : this.basePause();
            }
            return;
        }
        LivingEntity target = this.target;
        if (target == null)
        {
            return;
        }
        double d = HuntFight.flat(this.mob, target);
        Move reaction = this.react(target, d);
        if (reaction != null)
        {
            this.begin(reaction);
            return;
        }
        if (this.pause > 0 || !this.free())
        {
            if (this.pause > 0)
            {
                this.pause--;
            }
            HuntFight.face(this.mob, target);
            return;
        }
        Object[] row = HuntFight.row(this.table(), this.bands(), d);
        String name = HuntFight.pick(row, n -> this.ready(n) && this.can(n, target, d), this.mob.getRandom());
        Move m = this.make(name != null ? name : this.fallback(d), target);
        if (m != null)
        {
            this.begin(m);
        }
    }

    private void begin(Move m)
    {
        this.move = m;
        this.t = 0;
        m.start();
    }

    protected boolean ready(String name)
    {
        Long at = this.ready.get(name);
        return at == null || this.mob.level().getGameTime() >= at;
    }

    private void cool(String name)
    {
        int c = this.cooldown(name);
        if (c > 0)
        {
            this.ready.put(name, this.mob.level().getGameTime() + (long) (c * HuntFight.cooldownScale(this.mob)));
        }
    }

    protected boolean sees(LivingEntity target)
    {
        return this.mob.getSensing().hasLineOfSight(target);
    }

    /** Where his hand is (to throw from): up and a little ahead of where his model stands (the moose's ground, in the
     * saddle). */
    static Vec3 hand(HuntMob who, double up)
    {
        double yaw = Math.toRadians(HuntFight.facing(who));
        return who.modelOrigin().add(-Math.sin(yaw) * HuntsmanTuning.HAND_AHEAD, up, Math.cos(yaw) * HuntsmanTuning.HAND_AHEAD);
    }

    // ---------------------------------------------------------------- moves both of them make

    /** An animation on {@code who}, things done at its moments ({@link #at}), the goal's mob facing its target until
     * {@code faceUntil}; done at {@code length}. */
    class Timed implements Move
    {
        private final String name;
        private final HuntMob who;
        private final String anim;
        private final int length;
        private final int faceUntil;
        private final Map<Integer, Runnable> moments = new HashMap<>();
        @Nullable
        private Runnable onStart;

        Timed(String name, HuntMob who, String anim, int length, int faceUntil)
        {
            this.name = name;
            this.who = who;
            this.anim = anim;
            this.length = length;
            this.faceUntil = faceUntil;
        }

        Timed at(int tick, Runnable action)
        {
            Runnable before = this.moments.get(tick);
            this.moments.put(tick, before == null ? action : () ->
            {
                before.run();
                action.run();
            });
            return this;
        }

        Timed first(Runnable action)
        {
            this.onStart = action;
            return this;
        }

        @Override
        public String name()
        {
            return this.name;
        }

        @Override
        public void start()
        {
            HuntGoal.this.mob.getNavigation().stop();
            if (this.onStart != null)
            {
                this.onStart.run();
            }
            this.who.play(this.anim);
        }

        @Override
        public boolean tick(int t)
        {
            LivingEntity target = HuntGoal.this.target;
            if (target != null && t <= this.faceUntil)
            {
                HuntFight.face(HuntGoal.this.mob, target);
            }
            Runnable action = this.moments.get(t);
            if (action != null)
            {
                action.run();
            }
            return t >= this.length;
        }
    }

    /** Running (or galloping) in at its target, for a while or until it's within {@code until}. */
    class Chase implements Move
    {
        private final String name;
        private final double speed;
        private final double until;
        private int repath;

        Chase(String name, double speed, double until)
        {
            this.name = name;
            this.speed = speed;
            this.until = until;
        }

        @Override
        public String name()
        {
            return this.name;
        }

        @Override
        public void start()
        {
            this.repath = 0;
        }

        @Override
        public boolean tick(int t)
        {
            LivingEntity target = HuntGoal.this.target;
            if (target == null)
            {
                return true;
            }
            this.repath = BossChase.chase(HuntGoal.this.mob, target, this.speed, this.repath, REPATH);
            return t >= HuntsmanTuning.RUN_IN_TICKS || HuntFight.flat(HuntGoal.this.mob, target) <= this.until;
        }

        @Override
        public void stop()
        {
            HuntGoal.this.mob.getNavigation().stop();
        }
    }

    /** The Antler Charge: the moose paws the snow, turning to its target; then it locks where its target stands and
     * gallops there in a straight line, head low; the first thing in its way takes the charge's damage and is thrown;
     * a wall stops it; after the dash it skids to a stop. (In stage 1 he rides along.) */
    class Charge implements Move
    {
        private final AlphaMoose moose;
        private Vec3 dir = Vec3.ZERO;
        private double left;
        private int phase;
        private int skid;

        Charge(AlphaMoose moose)
        {
            this.moose = moose;
        }

        @Override
        public String name()
        {
            return "charge";
        }

        @Override
        public void start()
        {
            this.moose.getNavigation().stop();
            this.moose.play("stomp");
            this.phase = 0;
        }

        @Override
        public boolean tick(int t)
        {
            LivingEntity target = HuntGoal.this.target;
            if (this.phase == 0)
            {
                if (target != null)
                {
                    HuntFight.face(this.moose, target);
                }
                if (t >= HuntsmanTuning.CHARGE_WINDUP)
                {
                    if (target == null)
                    {
                        return true;
                    }
                    Vec3 to = target.position().subtract(this.moose.position());
                    double d = Math.sqrt(to.x * to.x + to.z * to.z);
                    this.dir = d < 1.0E-3D ? Vec3.ZERO : new Vec3(to.x / d, 0.0D, to.z / d);
                    this.left = Math.min(HuntsmanTuning.CHARGE_MAX, d);
                    this.phase = 1;
                    this.moose.forcedLoop = "charge";
                    this.moose.play("charge");
                    this.moose.level().playSound(null, this.moose.getX(), this.moose.getY(), this.moose.getZ(),
                            ModSounds.MOOSE_CHARGE.get(), SoundSource.HOSTILE, 3.0F, 0.6F);
                }
                return false;
            }
            if (this.phase == 1)
            {
                float yaw = (float) (Math.atan2(this.dir.z, this.dir.x) * 180.0D / Math.PI) - 90.0F;
                this.moose.setYRot(yaw);
                this.moose.yBodyRot = yaw;
                this.moose.setYHeadRot(yaw);
                double step = Math.min(HuntsmanTuning.CHARGE_SPEED, this.left);
                this.moose.move(MoverType.SELF, new Vec3(this.dir.x * step, -0.2D, this.dir.z * step));
                this.left -= step;
                if (this.moose.level() instanceof ServerLevel level && t % 2 == 0)
                {
                    level.sendParticles(ParticleTypes.SNOWFLAKE, this.moose.getX(), this.moose.getY() + 0.2D,
                            this.moose.getZ(), 6, 0.8D, 0.1D, 0.8D, 0.05D);
                }
                LivingEntity hit = this.firstInTheWay();
                if (hit != null)
                {
                    if (hit.hurt(this.moose.damageSources().mobAttack(this.moose),
                            HuntsmanTuning.CHARGE_DAMAGE * HuntFight.damageScale(this.moose)))
                    {
                        HuntFight.throwFrom(hit, this.moose.position(), HuntsmanTuning.CHARGE_KNOCK,
                                HuntsmanTuning.CHARGE_KNOCK_UP);
                    }
                    this.phase = 2;
                }
                else if (this.moose.horizontalCollision || this.left <= 1.0E-3D)
                {
                    this.phase = 2;
                }
                if (this.phase == 2)
                {
                    this.moose.forcedLoop = null;
                    this.moose.loop("idle");
                }
                return false;
            }
            return ++this.skid >= HuntsmanTuning.CHARGE_SKID;
        }

        @Nullable
        private LivingEntity firstInTheWay()
        {
            AABB box = this.moose.getBoundingBox().inflate(0.5D, 0.0D, 0.5D);
            List<LivingEntity> hits = this.moose.level().getEntitiesOfClass(LivingEntity.class, box, e -> e.isAlive()
                    && !e.isSpectator() && !HuntFight.isPartner(this.moose, e) && !e.isPassengerOfSameVehicle(this.moose));
            return hits.isEmpty() ? null : hits.get(0);
        }

        @Override
        public void stop()
        {
            this.moose.forcedLoop = null;
        }
    }

    /** The Back Kick: a foe close behind the moose gets both its hind hooves. */
    @Nullable
    Move backKick(AlphaMoose moose, LivingEntity target)
    {
        if (!this.ready("back_kick") || moose.busy())
        {
            return null;
        }
        float behind = HuntFight.facing(moose) + 180.0F;
        double reach = HuntsmanTuning.MOOSE_BACK + HuntsmanTuning.BACK_KICK_REACH;
        double halfAngle = 180.0D - HuntsmanTuning.BACK_KICK_ANGLE;
        boolean someone = false;
        for (LivingEntity e : HuntFight.within(moose, moose.position(), behind, reach, halfAngle))
        {
            if (e instanceof Player || e == target)
            {
                someone = true;
                break;
            }
        }
        if (!someone)
        {
            return null;
        }
        return new Timed("back_kick", moose, "back_kick", HuntAnims.named(HuntAnims.ALPHA_MOOSE, "back_kick").ticks(), 0)
                .at(HuntCues.moment(HuntAnims.MOOSE, "back_kick", "hit"), () -> HuntFight.strike(moose, moose.position(),
                        HuntFight.facing(moose) + 180.0F, reach, halfAngle, HuntsmanTuning.BACK_KICK_DAMAGE,
                        HuntsmanTuning.BACK_KICK_KNOCK, HuntsmanTuning.BACK_KICK_KNOCK_UP));
    }
}
