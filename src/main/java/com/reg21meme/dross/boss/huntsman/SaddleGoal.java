package com.reg21meme.dross.boss.huntsman;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Stage 1, the Wild Hunt: the Huntsman's own moves from the saddle, on his own timer while the Alpha Moose makes its
 * own ({@link MooseGoal}; the user, October 23, 2026: "the hunter needs to be shooting or hitting me with a spear while
 * the moose attacks"; until then one table ran both of them and he sat still while it charged). He uses what fits where
 * his target is ({@link HuntsmanTuning#SADDLE_CHANCES}): close up mostly his spear (not behind him), his bow with a shot
 * for each side (ahead past the moose's head, left, right, behind over his shoulder: so the moose needn't turn side-on
 * to him), the bola and the horn ahead, the arrow rain anywhere; and the bola at once at a target running away from him
 * (the user: "if I am running away he should use his bola to slow me down").
 */
final class SaddleGoal extends HuntGoal
{
    private static final double[] BANDS = {HuntsmanTuning.WILD_CLOSE, HuntsmanTuning.WILD_MID, HuntsmanTuning.WILD_FAR};
    /** His spear's reach from the saddle covers this far round from the moose's front (it's on his right too). */
    private static final double THRUST_HALF_ANGLE = 100.0D;
    private static final double THRUST_KNOCK = 0.5D;
    private static final double THRUST_KNOCK_UP = 0.2D;

    private final Huntsman rider;

    SaddleGoal(Huntsman rider)
    {
        super(rider);
        this.rider = rider;
    }

    @Override
    protected boolean stageOk()
    {
        return this.rider.stage() == HuntStage.WILD_HUNT && this.moose() != null;
    }

    @Nullable
    private AlphaMoose moose()
    {
        return this.rider.getVehicle() instanceof AlphaMoose m ? m : null;
    }

    @Override
    protected Object[][] table()
    {
        return HuntsmanTuning.SADDLE_CHANCES;
    }

    @Override
    protected double[] bands()
    {
        return BANDS;
    }

    @Override
    protected int basePause()
    {
        return HuntsmanTuning.PAUSE_SADDLE;
    }

    /** With nothing that fits, he waits (and tries again next tick). */
    @Override
    protected String fallback(double distance)
    {
        return "";
    }

    /** How far round {@code target} is from the way the moose faces (degrees, 0 to 180), and whether it's on his left. */
    private double angleOff(LivingEntity target)
    {
        double yaw = Math.toRadians(HuntFight.facing(this.rider));
        Vec3 ahead = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 to = target.position().subtract(this.rider.position());
        double d = Math.sqrt(to.x * to.x + to.z * to.z);
        if (d < 1.0E-3D)
        {
            return 0.0D;
        }
        return Math.toDegrees(Math.acos(Math.max(-1.0D, Math.min(1.0D, (ahead.x * to.x + ahead.z * to.z) / d))));
    }

    private boolean onHisLeft(LivingEntity target)
    {
        double yaw = Math.toRadians(HuntFight.facing(this.rider));
        Vec3 left = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw));
        Vec3 to = target.position().subtract(this.rider.position());
        return left.x * to.x + left.z * to.z > 0.0D;
    }

    /** Which of his shots fits where {@code target} is: ahead, to his left (ride_shoot), right, or behind. */
    private String shotFor(LivingEntity target)
    {
        double off = this.angleOff(target);
        if (off <= HuntsmanTuning.SADDLE_SHOT_FRONT)
        {
            return "ride_shoot_front";
        }
        if (off >= HuntsmanTuning.SADDLE_SHOT_BACK)
        {
            return "ride_shoot_back";
        }
        return this.onHisLeft(target) ? "ride_shoot" : "ride_shoot_right";
    }

    /** True if {@code target} is running away from him (by the spots he's seen it at). */
    private boolean fleeing(LivingEntity target)
    {
        Vec3 away = target.position().subtract(this.rider.position());
        double d = Math.sqrt(away.x * away.x + away.z * away.z);
        if (d < 1.0E-3D)
        {
            return false;
        }
        Vec3 move = this.rider.motionOf(target);
        return (move.x * away.x + move.z * away.z) / d >= HuntsmanTuning.BOLA_FLEEING_SPEED;
    }

    /** The bola at once at a target running away (when it's ready and he's free). */
    @Nullable
    @Override
    protected Move react(LivingEntity target, double distance)
    {
        if (this.free() && this.ready("bola") && this.can("bola", target, distance) && this.fleeing(target))
        {
            return this.make("bola", target);
        }
        return null;
    }

    @Override
    protected boolean can(String name, LivingEntity target, double d)
    {
        double off = this.angleOff(target);
        return switch (name)
        {
            case "thrust" -> d <= HuntsmanTuning.THRUST_REACH && off <= THRUST_HALF_ANGLE;
            case "horn" -> d <= HuntsmanTuning.HORN_LENGTH && off <= HuntsmanTuning.HORN_HALF_ANGLE;
            case "bola" -> d >= HuntsmanTuning.BOLA_MIN && d <= HuntsmanTuning.BOLA_MAX && off <= HuntsmanTuning.BOLA_HALF_ANGLE
                    && this.sees(target);
            case "shoot" -> d >= HuntsmanTuning.SADDLE_SHOT_MIN && d <= HuntsmanTuning.SADDLE_SHOT_MAX && this.sees(target);
            case "rain" -> true;
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
            case "shoot" -> HuntsmanTuning.SADDLE_SHOT_COOLDOWN;
            case "rain" -> HuntsmanTuning.RAIN_COOLDOWN;
            default -> 0;
        };
    }

    private static int length(String anim)
    {
        HuntAnims.Anim a = HuntAnims.named(HuntAnims.RIDING, anim);
        return a != null ? a.ticks() : 20;
    }

    private static int moment(String anim, String moment)
    {
        return HuntCues.moment(HuntAnims.RIDER, anim, moment);
    }

    @Nullable
    @Override
    protected Move make(String name, LivingEntity target)
    {
        Huntsman rider = this.rider;
        AlphaMoose moose = this.moose();
        if (moose == null)
        {
            return null;
        }
        return switch (name)
        {
            case "thrust" -> new Timed("thrust", rider, "ride_thrust", length("ride_thrust"), moment("ride_thrust", "hits.0"))
                    .at(moment("ride_thrust", "hits.0"), () -> HuntFight.strike(rider, moose.position(),
                            HuntFight.facing(moose), HuntsmanTuning.THRUST_REACH, THRUST_HALF_ANGLE,
                            HuntsmanTuning.THRUST_DAMAGE, THRUST_KNOCK, THRUST_KNOCK_UP));
            case "horn" -> new Timed("horn", rider, "ride_horn", moment("ride_horn", "blow") + 10, moment("ride_horn", "blow"))
                    .at(moment("ride_horn", "blow"), () -> HuntFight.blast(rider, HuntFight.facing(moose),
                            HuntsmanTuning.HORN_LENGTH, HuntsmanTuning.HORN_HALF_ANGLE, HuntsmanTuning.HORN_DAMAGE,
                            HuntsmanTuning.HORN_PUSH, HuntsmanTuning.HORN_PUSH_UP, HuntsmanTuning.HORN_SLOW_TICKS,
                            HuntsmanTuning.HORN_SLOW_LEVEL));
            case "bola" -> new Timed("bola", rider, "ride_bola", length("ride_bola"), moment("ride_bola", "release"))
                    .at(moment("ride_bola", "release"), () -> Bola.throwAt(rider, hand(rider, HuntsmanTuning.RIDER_HAND_UP),
                            target));
            case "shoot" ->
            {
                String anim = this.shotFor(target);
                yield new Timed("shoot", rider, anim, length(anim), 0)
                        .first(() ->
                        {
                            rider.shotTarget = target;
                            rider.shotBase = HuntsmanTuning.SHOT_BASE_DAMAGE;
                            rider.shotFrost = HuntFight.Frost.FROST;     // every arrow of his frosts you
                        })
                        .at(length(anim), () -> rider.shotTarget = null);
            }
            case "rain" -> new Timed("rain", rider, "ride_rain", moment("ride_rain", "shots.3") + 4, moment("ride_rain", "shots.3"))
                    .first(() -> HuntFight.startRain(rider, target))
                    .at(moment("ride_rain", "shots.3") + 1, () -> rider.rainTarget = null);
            default -> null;
        };
    }
}
