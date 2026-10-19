package com.reg21meme.dross.boss.rimefall;

import net.minecraft.world.entity.Entity;
import net.minecraft.util.Mth;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;

import java.util.List;

/**
 * The animations of Rimefall's animals (made by {@code tools/rimefall/mobs.py}: {@code animation.<mob>.<name>}), and
 * the bits each animal's animation controller shares: which animation plays, at what speed.
 * <p>
 * An animal plays one of three things: an <b>action</b> its server side started (a horn flick, a ram, grazing; synced,
 * with a counter so starting the same action again restarts it), its <b>gait</b> (walk or run, sped up or slowed down to
 * match how fast it really moves, so its feet don't slide), or <b>idle</b>.
 */
public final class RimefallAnims
{
    /** Below this (blocks a second) an animal counts as standing still. */
    public static final double MOVING = 0.15D;
    /** A gait plays at most this much faster or slower than it was made. */
    public static final double MIN_SPEEDUP = 0.4D;
    public static final double MAX_SPEEDUP = 2.4D;

    /**
     * One animation.
     *
     * @param name  its name ({@code animation.<mob>.<name>})
     * @param ticks how long it lasts once (0 for a loop the server never ends)
     * @param loop  true if it loops
     * @param speed blocks a second its feet cover as made (a gait; 0 for anything else)
     */
    public record Anim(String name, int ticks, boolean loop, double speed, RawAnimation raw)
    {
    }

    public static Anim loop(String mob, String name, double speed)
    {
        return new Anim(name, 0, true, speed, RawAnimation.begin().thenLoop("animation." + mob + "." + name));
    }

    public static Anim once(String mob, String name, int ticks)
    {
        return new Anim(name, ticks, false, 0.0D, RawAnimation.begin().thenPlayAndHold("animation." + mob + "." + name));
    }

    /** The one called {@code name}, or null. */
    public static Anim named(List<Anim> anims, String name)
    {
        for (Anim anim : anims)
        {
            if (anim.name().equals(name))
            {
                return anim;
            }
        }
        return null;
    }

    /** How fast an entity moves over the ground, in blocks a second (works on both sides). */
    public static double groundSpeed(Entity entity)
    {
        return Math.sqrt(Mth.square(entity.getX() - entity.xo) + Mth.square(entity.getZ() - entity.zo)) * 20.0D;
    }

    /** Plays a gait at the speed that matches {@code speed} (blocks a second). */
    public static <T extends software.bernie.geckolib.core.animatable.GeoAnimatable> void matchSpeed(
            AnimationController<T> controller, Anim gait, double speed)
    {
        controller.setAnimationSpeed(gait.speed() > 0 ? Mth.clamp(speed / gait.speed(), MIN_SPEEDUP, MAX_SPEEDUP) : 1.0D);
    }

    private RimefallAnims() {}
}
