package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.boss.BossChase;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;

/**
 * Stage 2, the Alpha Moose on its own (the user's table, {@link HuntsmanTuning#MOOSE_CHANCES}): the ram, the antler
 * toss and the stomp up close, the bellow, the Cracking Stamp and its charge (more often than in stage 1) further out,
 * and the back kick whenever a foe is behind it.
 */
final class MooseGoal extends HuntGoal
{
    private static final double[] BANDS = {HuntsmanTuning.MOOSE_CLOSE, HuntsmanTuning.MOOSE_MID, HuntsmanTuning.MOOSE_FAR};
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

    @Override
    protected boolean stageOk()
    {
        return this.moose.stage() == HuntStage.DUEL;
    }

    @Override
    protected Object[][] table()
    {
        return HuntsmanTuning.MOOSE_CHANCES;
    }

    @Override
    protected double[] bands()
    {
        return BANDS;
    }

    @Override
    protected int basePause()
    {
        return HuntsmanTuning.PAUSE_MOOSE;
    }

    @Override
    protected String fallback(double distance)
    {
        return "run_in";
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
            case "run_in" -> true;
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
            case "charge" -> HuntsmanTuning.CHARGE_COOLDOWN_STAGE2;
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
            default -> null;
        };
    }
}
