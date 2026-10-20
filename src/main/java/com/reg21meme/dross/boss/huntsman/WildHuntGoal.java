package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.boss.BossChase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Stage 1, the Wild Hunt (on the Alpha Moose; the user's table, {@link HuntsmanTuning#WILD_CHANCES}): the moose runs
 * the fight for both of them. His moves from the saddle (the spear thrust, the horn, the bola, the arrow rain, the
 * circling hunt's shots) wait until his hands are free; the moose's own (the charge, riding off and in, the back kick)
 * don't.
 */
final class WildHuntGoal extends HuntGoal
{
    private static final double[] BANDS = {HuntsmanTuning.WILD_CLOSE, HuntsmanTuning.WILD_MID, HuntsmanTuning.WILD_FAR};
    /** His spear's reach from the saddle covers this far round from the moose's front (it's on his right too). */
    private static final double THRUST_HALF_ANGLE = 100.0D;
    private static final double THRUST_KNOCK = 0.5D;
    private static final double THRUST_KNOCK_UP = 0.2D;

    private final AlphaMoose moose;

    WildHuntGoal(AlphaMoose moose)
    {
        super(moose);
        this.moose = moose;
    }

    @Override
    protected boolean stageOk()
    {
        return this.moose.stage() == HuntStage.WILD_HUNT && this.moose.rider() != null;
    }

    @Override
    protected Object[][] table()
    {
        return HuntsmanTuning.WILD_CHANCES;
    }

    @Override
    protected double[] bands()
    {
        return BANDS;
    }

    @Override
    protected int basePause()
    {
        return HuntsmanTuning.PAUSE_WILD_HUNT;
    }

    @Override
    protected String fallback()
    {
        return "ride_in";
    }

    private boolean riderFree()
    {
        Huntsman rider = this.moose.rider();
        return rider != null && !rider.busy();
    }

    @Override
    protected boolean can(String name, LivingEntity target, double d)
    {
        return switch (name)
        {
            case "thrust" -> this.riderFree() && d <= HuntsmanTuning.THRUST_REACH;
            case "horn" -> this.riderFree() && d <= HuntsmanTuning.HORN_LENGTH;
            case "bola" -> this.riderFree() && d >= HuntsmanTuning.BOLA_MIN && d <= HuntsmanTuning.BOLA_MAX && this.sees(target);
            case "circle" -> this.riderFree() && d >= HuntsmanTuning.WILD_CLOSE && d <= HuntsmanTuning.WILD_FAR
                    && this.sees(target);
            case "charge" -> d >= HuntsmanTuning.CHARGE_MIN && d <= HuntsmanTuning.CHARGE_MAX && this.sees(target)
                    && BossChase.straightWay(this.moose, target);
            case "rain" -> this.riderFree();
            case "ride_off", "ride_in" -> true;
            default -> false;
        };
    }

    @Override
    protected int cooldown(String name)
    {
        return switch (name)
        {
            case "thrust" -> HuntsmanTuning.THRUST_COOLDOWN;
            case "horn" -> HuntsmanTuning.HORN_COOLDOWN;
            case "bola" -> HuntsmanTuning.BOLA_COOLDOWN;
            case "circle" -> HuntsmanTuning.CIRCLE_COOLDOWN;
            case "charge" -> HuntsmanTuning.CHARGE_COOLDOWN;
            case "rain" -> HuntsmanTuning.RAIN_COOLDOWN;
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

    @Nullable
    @Override
    protected Move make(String name, LivingEntity target)
    {
        Huntsman rider = this.moose.rider();
        if (rider == null)
        {
            return null;
        }
        String m = HuntAnims.RIDER;
        return switch (name)
        {
            case "thrust" -> new Timed("thrust", rider, "ride_thrust", length("ride_thrust"),
                    HuntCues.moment(m, "ride_thrust", "hits.0"))
                    .at(HuntCues.moment(m, "ride_thrust", "hits.0"), () -> HuntFight.strike(rider, this.moose.position(),
                            HuntFight.facing(this.moose), HuntsmanTuning.THRUST_REACH, THRUST_HALF_ANGLE,
                            HuntsmanTuning.THRUST_DAMAGE, THRUST_KNOCK, THRUST_KNOCK_UP));
            case "horn" -> new Timed("horn", rider, "ride_horn", HuntCues.moment(m, "ride_horn", "blow") + 10,
                    HuntCues.moment(m, "ride_horn", "blow"))
                    .at(HuntCues.moment(m, "ride_horn", "blow"), () -> HuntFight.blast(rider, HuntFight.facing(this.moose),
                            HuntsmanTuning.HORN_LENGTH, HuntsmanTuning.HORN_HALF_ANGLE, HuntsmanTuning.HORN_DAMAGE,
                            HuntsmanTuning.HORN_PUSH, HuntsmanTuning.HORN_PUSH_UP, HuntsmanTuning.HORN_SLOW_TICKS,
                            HuntsmanTuning.HORN_SLOW_LEVEL));
            case "bola" -> new Timed("bola", rider, "ride_bola", length("ride_bola"), HuntCues.moment(m, "ride_bola", "release"))
                    .at(HuntCues.moment(m, "ride_bola", "release"), () -> Bola.throwAt(rider,
                            hand(rider, HuntsmanTuning.RIDER_HAND_UP), target));
            case "rain" -> new Timed("rain", rider, "ride_rain", HuntCues.moment(m, "ride_rain", "shots.3") + 4,
                    HuntCues.moment(m, "ride_rain", "shots.3"))
                    .first(() -> rider.shotTarget = null)
                    .at(HuntCues.moment(m, "ride_rain", "shots.3"), () -> HuntFight.rain(rider, target,
                            HuntsmanTuning.RAIN_ARROWS));
            case "circle" -> new Circle(rider);
            case "charge" -> new Charge(this.moose);
            case "ride_off" -> new RideOff();
            case "ride_in" -> new Chase("ride_in", HuntsmanTuning.MOOSE_RUN, HuntsmanTuning.WILD_CLOSE);
            default -> null;
        };
    }

    private static int length(String anim)
    {
        HuntAnims.Anim a = HuntAnims.named(HuntAnims.RIDING, anim);
        return a != null ? a.ticks() : 20;
    }

    /** The circling hunt: the moose gallops a ring round its target, keeping it on his left, while he shoots
     * ({@link HuntsmanTuning#CIRCLE_SHOTS_ANIMS} of his saddle shots, two arrows each). */
    private final class Circle implements Move
    {
        private final Huntsman rider;
        private int shots;

        Circle(Huntsman rider)
        {
            this.rider = rider;
        }

        @Override
        public String name()
        {
            return "circle";
        }

        @Override
        public void start()
        {
            this.rider.shotTarget = WildHuntGoal.this.target;
            this.rider.shotBase = HuntsmanTuning.SHOT_BASE_DAMAGE;
            this.rider.shotFrost = HuntFight.Frost.NONE;
            this.rider.play("ride_shoot");
            this.shots = 1;
        }

        @Override
        public boolean tick(int t)
        {
            LivingEntity target = WildHuntGoal.this.target;
            if (target == null || !target.isAlive())
            {
                return true;
            }
            this.rider.shotTarget = target;
            double angle = Math.atan2(WildHuntGoal.this.moose.getZ() - target.getZ(), WildHuntGoal.this.moose.getX() - target.getX())
                    - HuntsmanTuning.CIRCLE_LEAD;
            double r = HuntsmanTuning.CIRCLE_RADIUS;
            WildHuntGoal.this.moose.getMoveControl().setWantedPosition(target.getX() + Math.cos(angle) * r, target.getY(),
                    target.getZ() + Math.sin(angle) * r, HuntsmanTuning.CIRCLE_SPEED);
            if (!this.rider.busy())
            {
                if (this.shots >= HuntsmanTuning.CIRCLE_SHOTS_ANIMS)
                {
                    return true;
                }
                this.rider.play("ride_shoot");
                this.shots++;
            }
            return t > HuntsmanTuning.CIRCLE_SHOTS_ANIMS * length("ride_shoot") + 20;
        }

        @Override
        public void stop()
        {
            this.rider.shotTarget = null;
            WildHuntGoal.this.moose.getNavigation().stop();
        }
    }

    /** Riding off: the moose gallops out away from its target, to set up a charge or the shots. */
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
            LivingEntity target = WildHuntGoal.this.target;
            AlphaMoose moose = WildHuntGoal.this.moose;
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
            AlphaMoose moose = WildHuntGoal.this.moose;
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
            WildHuntGoal.this.moose.getNavigation().stop();
        }
    }
}
