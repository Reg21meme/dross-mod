package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.boss.BossChase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * The Alpha Moose's moves in both stages. Stage 2, on its own (the user's table, {@link HuntsmanTuning#MOOSE_CHANCES}):
 * the ram, the antler toss and the stomp up close, the bellow, the Cracking Stamp and its charge further out, and the
 * back kick whenever a foe is behind it. Stage 1, the Wild Hunt ({@link HuntsmanTuning#WILD_MOOSE_CHANCES}): the same
 * attacks with the Huntsman on its back (he rides along with each: his {@code carry_} animations), and the circling hunt
 * and riding off and in to set up his shots; he makes his own moves meanwhile ({@link SaddleGoal}).
 */
final class MooseGoal extends HuntGoal
{
    private static final double[] BANDS = {HuntsmanTuning.MOOSE_CLOSE, HuntsmanTuning.MOOSE_MID, HuntsmanTuning.MOOSE_FAR};
    private static final double[] WILD_BANDS = {HuntsmanTuning.WILD_CLOSE, HuntsmanTuning.WILD_MID, HuntsmanTuning.WILD_FAR};
    /** Its blows land this far round from its front (degrees). */
    private static final double RAM_HALF_ANGLE = 45.0D;
    private static final double STOMP_HALF_ANGLE = 50.0D;
    private static final double RAM_KNOCK = 1.4D;
    private static final double RAM_KNOCK_UP = 0.3D;
    private static final double TOSS_REACH = 2.5D;
    private static final double TOSS_KNOCK = 0.3D;
    private static final double STOMP_KNOCK = 0.6D;
    private static final double STOMP_KNOCK_UP = 0.2D;
    private static final double BELLOW_PUSH_UP = 0.3D;
    private static final int BELLOW_SLOW_LEVEL = 1;

    private final AlphaMoose moose;

    MooseGoal(AlphaMoose moose)
    {
        super(moose);
        this.moose = moose;
    }

    /** In the Wild Hunt (with him on its back) rather than on its own. */
    private boolean wild()
    {
        return this.moose.stage() == HuntStage.WILD_HUNT && this.moose.rider() != null;
    }

    @Override
    protected boolean stageOk()
    {
        return this.moose.stage() == HuntStage.DUEL || this.wild();
    }

    @Override
    protected Object[][] table()
    {
        return this.wild() ? HuntsmanTuning.WILD_MOOSE_CHANCES : HuntsmanTuning.MOOSE_CHANCES;
    }

    @Override
    protected double[] bands()
    {
        return this.wild() ? WILD_BANDS : BANDS;
    }

    @Override
    protected int basePause()
    {
        return this.wild() ? HuntsmanTuning.PAUSE_WILD_HUNT : HuntsmanTuning.PAUSE_MOOSE;
    }

    @Override
    protected String fallback(double distance)
    {
        return this.wild() ? "ride_in" : "run_in";
    }

    @Override
    protected boolean can(String name, LivingEntity target, double d)
    {
        double front = HuntsmanTuning.MOOSE_FRONT;
        return switch (name)
        {
            case "ram" -> d <= front + HuntsmanTuning.RAM_REACH;
            case "toss" -> d <= front + TOSS_REACH;
            case "stomp" -> d <= front + HuntsmanTuning.STOMP_REACH;
            case "bellow" -> d <= HuntsmanTuning.BELLOW_LENGTH;
            case "charge" -> d >= HuntsmanTuning.CHARGE_MIN && d <= HuntsmanTuning.CHARGE_MAX && this.sees(target)
                    && BossChase.straightWay(this.moose, target);
            case "stamp" -> d >= HuntsmanTuning.STAMP_MIN && d <= HuntsmanTuning.STAMP_MAX && this.sees(target);
            case "circle" -> d >= HuntsmanTuning.WILD_CLOSE && d <= HuntsmanTuning.WILD_FAR && this.sees(target);
            case "run_in", "ride_in", "ride_off" -> true;
            default -> false;
        };
    }

    @Override
    protected int cooldown(String name)
    {
        return switch (name)
        {
            case "ram" -> HuntsmanTuning.RAM_COOLDOWN;
            case "toss" -> HuntsmanTuning.TOSS_COOLDOWN;
            case "stomp" -> HuntsmanTuning.STOMP_COOLDOWN;
            case "bellow" -> HuntsmanTuning.BELLOW_COOLDOWN;
            case "charge" -> this.wild() ? HuntsmanTuning.CHARGE_COOLDOWN : HuntsmanTuning.CHARGE_COOLDOWN_STAGE2;
            case "circle" -> HuntsmanTuning.CIRCLE_COOLDOWN;
            case "stamp" -> HuntsmanTuning.STAMP_COOLDOWN;
            case "back_kick" -> HuntsmanTuning.BACK_KICK_COOLDOWN;
            default -> 0;
        };
    }

    @Nullable
    @Override
    protected Move react(LivingEntity target, double distance)
    {
        return this.backKick(this.moose, target);
    }

    private static int moment(String anim, String moment)
    {
        return HuntCues.moment(HuntAnims.MOOSE, anim, moment);
    }

    private static int length(String anim)
    {
        HuntAnims.Anim a = HuntAnims.named(HuntAnims.ALPHA_MOOSE, anim);
        return a != null ? a.ticks() : 20;
    }

    /** A blow from its antlers or hooves, in front of it. */
    private Runnable front(double reach, double halfAngle, float damage, double knock, double up)
    {
        return () -> HuntFight.strike(this.moose, this.moose.position(), HuntFight.facing(this.moose),
                HuntsmanTuning.MOOSE_FRONT + reach, halfAngle, damage, knock, up);
    }

    @Nullable
    @Override
    protected Move make(String name, LivingEntity target)
    {
        return switch (name)
        {
            case "ram" -> new Timed("ram", this.moose, "ram", length("ram"), moment("ram", "hit"))
                    .at(moment("ram", "hit"), this.front(HuntsmanTuning.RAM_REACH, RAM_HALF_ANGLE,
                            HuntsmanTuning.RAM_DAMAGE, RAM_KNOCK, RAM_KNOCK_UP));
            case "toss" -> new Timed("toss", this.moose, "antler_toss", length("antler_toss"), moment("antler_toss", "hit"))
                    .at(moment("antler_toss", "hit"), this.front(TOSS_REACH, RAM_HALF_ANGLE, HuntsmanTuning.TOSS_DAMAGE,
                            TOSS_KNOCK, HuntsmanTuning.TOSS_UP));
            case "stomp" -> new Timed("stomp", this.moose, "stomp", length("stomp"), moment("stomp", "hit"))
                    .at(moment("stomp", "hit"), this.front(HuntsmanTuning.STOMP_REACH, STOMP_HALF_ANGLE,
                            HuntsmanTuning.STOMP_DAMAGE, STOMP_KNOCK, STOMP_KNOCK_UP));
            case "bellow" -> new Timed("bellow", this.moose, "bellow", length("bellow"), moment("bellow", "start"))
                    .at(moment("bellow", "start"), () -> HuntFight.blast(this.moose, HuntFight.facing(this.moose),
                            HuntsmanTuning.BELLOW_LENGTH, HuntsmanTuning.BELLOW_HALF_ANGLE, HuntsmanTuning.BELLOW_DAMAGE,
                            HuntsmanTuning.BELLOW_PUSH, BELLOW_PUSH_UP, HuntsmanTuning.HORN_SLOW_TICKS, BELLOW_SLOW_LEVEL));
            case "stamp" -> new Timed("stamp", this.moose, "crack_stamp", length("crack_stamp"), moment("crack_stamp", "hit"))
                    .at(moment("crack_stamp", "hit"), () ->
                    {
                        LivingEntity t = this.target;
                        if (t != null)
                        {
                            double yaw = Math.toRadians(HuntFight.facing(this.moose));
                            Vec3 from = this.moose.position().add(-Math.sin(yaw) * HuntsmanTuning.MOOSE_FRONT, 0.0D,
                                    Math.cos(yaw) * HuntsmanTuning.MOOSE_FRONT);
                            HuntFight.crack(this.moose, from, t.position());
                        }
                    });
            case "charge" -> new Charge(this.moose);
            case "run_in" -> new Chase("run_in", HuntsmanTuning.MOOSE_RUN, HuntsmanTuning.MOOSE_CLOSE);
            case "ride_in" -> new Chase("ride_in", HuntsmanTuning.MOOSE_RUN, HuntsmanTuning.WILD_CLOSE);
            case "circle" -> new Circle();
            case "ride_off" -> new RideOff();
            default -> null;
        };
    }

    /** The circling hunt: the moose gallops a ring round its target for a while, keeping it on his left, so he can
     * shoot from its back (his own moves: {@link SaddleGoal}). */
    private final class Circle implements Move
    {
        @Override
        public String name()
        {
            return "circle";
        }

        @Override
        public void start()
        {
        }

        @Override
        public boolean tick(int t)
        {
            LivingEntity target = MooseGoal.this.target;
            if (target == null || !target.isAlive())
            {
                return true;
            }
            AlphaMoose moose = MooseGoal.this.moose;
            double angle = Math.atan2(moose.getZ() - target.getZ(), moose.getX() - target.getX()) - HuntsmanTuning.CIRCLE_LEAD;
            double r = HuntsmanTuning.CIRCLE_RADIUS;
            moose.getMoveControl().setWantedPosition(target.getX() + Math.cos(angle) * r, target.getY(),
                    target.getZ() + Math.sin(angle) * r, HuntsmanTuning.CIRCLE_SPEED);
            return t >= HuntsmanTuning.CIRCLE_TICKS;
        }

        @Override
        public void stop()
        {
            MooseGoal.this.moose.getNavigation().stop();
        }
    }

    /** Riding off: the moose gallops out away from its target, to set up a charge or his shots. */
    private final class RideOff implements Move
    {
        private Vec3 to = Vec3.ZERO;

        @Override
        public String name()
        {
            return "ride_off";
        }

        @Override
        public void start()
        {
            LivingEntity target = MooseGoal.this.target;
            AlphaMoose moose = MooseGoal.this.moose;
            if (target == null)
            {
                this.to = moose.position();
                return;
            }
            Vec3 away = moose.position().subtract(target.position());
            double d = Math.sqrt(away.x * away.x + away.z * away.z);
            if (d < 1.0D)
            {
                double a = moose.getRandom().nextDouble() * Math.PI * 2.0D;
                away = new Vec3(Math.cos(a), 0.0D, Math.sin(a));
                d = 1.0D;
            }
            double out = HuntsmanTuning.RIDE_OFF_MIN
                    + moose.getRandom().nextDouble() * (HuntsmanTuning.RIDE_OFF_MAX - HuntsmanTuning.RIDE_OFF_MIN);
            this.to = target.position().add(away.x / d * out, 0.0D, away.z / d * out);
            moose.getNavigation().moveTo(this.to.x, this.to.y, this.to.z, HuntsmanTuning.MOOSE_RUN);
        }

        @Override
        public boolean tick(int t)
        {
            AlphaMoose moose = MooseGoal.this.moose;
            if (t % 20 == 0)
            {
                moose.getNavigation().moveTo(this.to.x, this.to.y, this.to.z, HuntsmanTuning.MOOSE_RUN);
            }
            double dx = this.to.x - moose.getX();
            double dz = this.to.z - moose.getZ();
            return dx * dx + dz * dz < 6.25D || t >= HuntsmanTuning.RIDE_OFF_TICKS;
        }

        @Override
        public void stop()
        {
            MooseGoal.this.moose.getNavigation().stop();
        }
    }
}
