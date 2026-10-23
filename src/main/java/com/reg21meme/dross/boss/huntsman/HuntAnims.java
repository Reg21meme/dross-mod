package com.reg21meme.dross.boss.huntsman;

import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.List;

/**
 * The Huntsman's and the Alpha Moose's animations (made by {@code tools/rimefall/mobs.py}: the modules
 * {@code rimemobs/huntsman.py}, {@code huntsman_rider.py} and {@code alpha_moose.py}), as their files name them
 * ({@code animation.<model>.<name>}), with how long each lasts. The Huntsman has two models: on foot ({@code huntsman})
 * and in the saddle ({@code huntsman_rider}), whose animations all start with {@code ride_}. While he rides it the Alpha
 * Moose wears the Wild Hunt's model ({@link #WILD}: itself with him in its saddle, made by
 * {@code tools/rimefall/wildhunt_model.py}; the user, October 23, 2026: "make them one model, then separate the models
 * after the split"), which has every animation of both: the moose's own, his {@code ride_*} and a {@code carry_<name>}
 * for each of the moose's (the saddle's motion under him, {@link #carry}); the moose plays all three at once.
 */
public final class HuntAnims
{
    /**
     * One animation.
     *
     * @param model  the model whose file it's in ({@code huntsman}, {@code huntsman_rider}, {@code alpha_moose})
     * @param name   its name
     * @param ticks  how long it lasts once (0 for a loop)
     * @param stays  true if its last pose stays (a death) instead of going back to standing
     * @param length how long it lasts in its file (ticks; a loop's once round), to keep a loop on its beat when it's
     *               played faster or slower ({@link HuntMob#setPace})
     */
    public record Anim(String model, String name, int ticks, boolean stays, double length)
    {
        public boolean loop()
        {
            return ticks == 0;
        }

        public RawAnimation raw()
        {
            String id = "animation." + model + "." + name;
            return loop() ? RawAnimation.begin().thenLoop(id) : RawAnimation.begin().thenPlayAndHold(id);
        }
    }

    private static Anim loop(String model, String name, double seconds)
    {
        return new Anim(model, name, 0, false, seconds * 20.0D);
    }

    private static Anim once(String model, String name, double seconds)
    {
        return new Anim(model, name, (int) Math.round(seconds * 20.0D), false, seconds * 20.0D);
    }

    /** A move whose last pose stays: a death. */
    private static Anim last(String model, String name, double seconds)
    {
        return new Anim(model, name, (int) Math.round(seconds * 20.0D), true, seconds * 20.0D);
    }

    public static final String FOOT = "huntsman";
    public static final String RIDER = "huntsman_rider";
    public static final String MOOSE = "alpha_moose";
    /** The Alpha Moose with the Huntsman in its saddle, as one model. */
    public static final String WILD = "wild_hunt";

    /** The Huntsman on foot (stage 2 of his fight). The first is the one he plays when nothing else is. */
    public static final List<Anim> HUNTSMAN = List.of(
            loop(FOOT, "idle", 4.0), loop(FOOT, "walk", 1.1), loop(FOOT, "run", 0.62),
            once(FOOT, "spear_combo", 1.9), once(FOOT, "thrust_sweep", 1.4), once(FOOT, "leap_strike", 1.7),
            once(FOOT, "throw_spear", 1.3), once(FOOT, "catch_spear", 0.8), once(FOOT, "aimed_shot", 2.9),
            once(FOOT, "quick_shot", 1.9), once(FOOT, "backstep_shoot", 2.4), once(FOOT, "arrow_rain", 4.2), once(FOOT, "bola_throw", 1.5),
            once(FOOT, "horn_blow", 3.7), once(FOOT, "land", 0.7), last(FOOT, "death", 4.6));

    /** The Huntsman in the saddle (stage 1, the Wild Hunt). */
    public static final List<Anim> RIDING = List.of(
            loop(RIDER, "ride_idle", 4.0), loop(RIDER, "ride_gallop", 0.6),
            once(RIDER, "ride_thrust", 1.3), once(RIDER, "ride_shoot", 2.9), once(RIDER, "ride_rain", 4.2),
            once(RIDER, "ride_horn", 3.7), once(RIDER, "ride_bola", 1.5), once(RIDER, "ride_leap", 1.4));

    /** His leap off the moose (the split, played with the moose's rear: the saddle's motion is in it, he springs at the
     * top of the rear): at its end he gets off and lands on foot ({@code land}). */
    public static final String LEAP = "ride_leap";
    /** His own part of riding the moose's gallop (and its charge). */
    public static final String GALLOP = "ride_gallop";

    /** The Alpha Moose. */
    public static final List<Anim> ALPHA_MOOSE = List.of(
            loop(MOOSE, "idle", 6.0), loop(MOOSE, "walk", 1.7), loop(MOOSE, "run", 0.6), loop(MOOSE, "charge", 0.56),
            once(MOOSE, "ram", 1.1), once(MOOSE, "back_kick", 1.2), once(MOOSE, "antler_toss", 1.3),
            once(MOOSE, "stomp", 1.2), once(MOOSE, "bellow", 2.2), once(MOOSE, "crack_stamp", 2.2),
            once(MOOSE, "rear", 2.4), once(MOOSE, "eat", 6.0), last(MOOSE, "death", 4.6));

    /**
     * The saddle's motion while the moose plays {@code moose} ({@code carry_<name>} in the Wild Hunt's file), looped
     * or held like the moose's own.
     */
    public static RawAnimation carry(Anim moose)
    {
        String id = "animation." + WILD + ".carry_" + moose.name();
        return moose.loop() ? RawAnimation.begin().thenLoop(id) : RawAnimation.begin().thenPlayAndHold(id);
    }

    /** {@code anim} as it's named in {@code model}'s file (the moose's and his in the Wild Hunt's). */
    public static RawAnimation raw(String model, Anim anim)
    {
        String id = "animation." + model + "." + anim.name();
        return anim.loop() ? RawAnimation.begin().thenLoop(id) : RawAnimation.begin().thenPlayAndHold(id);
    }

    /** The one called {@code name} in {@code anims}, or null. */
    public static Anim named(List<Anim> anims, String name)
    {
        for (Anim a : anims)
        {
            if (a.name().equals(name))
            {
                return a;
            }
        }
        return null;
    }

    /** An animation's own name from GeckoLib's id for it ({@code animation.<model>.<name>}). */
    static String shortName(String id)
    {
        int dot = id.lastIndexOf('.');
        return dot < 0 ? id : id.substring(dot + 1);
    }

    private HuntAnims() {}
}
