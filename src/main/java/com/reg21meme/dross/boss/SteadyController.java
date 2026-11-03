package com.reg21meme.dross.boss;

import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animation.AnimationController;

/**
 * A GeckoLib controller whose animation goes on from the moment it's at when its speed changes (the user, November 3,
 * 2026: the Pallbearer "stutters while walking").
 * <p>
 * GeckoLib works an animation's moment out as its speed times the time since it started, so a walk sped up or slowed
 * every frame to match how fast its mob really moves (the bosses', their minions', Rimefall's animals', the Frostfolk's,
 * the Cinder Wastes' mobs') jumped back and forth along its loop as that speed wobbled: at half speed, 60 ticks into a
 * loop, a speed 0.04 higher put it 2.4 ticks further on at once. Here a change of speed moves the animation's start
 * instead, so the moment it's at stays and only how fast it goes on changes. At a steady speed it's GeckoLib's own.
 */
public class SteadyController<T extends GeoAnimatable> extends AnimationController<T>
{
    /** The speed the last moment was worked out at (NaN: none yet). */
    private double lastSpeed = Double.NaN;

    public SteadyController(T animatable, String name, int transitionTicks, AnimationStateHandler<T> handler)
    {
        super(animatable, name, transitionTicks, handler);
    }

    @Override
    protected double adjustTick(double tick)
    {
        double speed = this.animationSpeedModifier.apply(this.animatable);
        if (this.shouldResetTick)
        {
            this.lastSpeed = speed;
            return super.adjustTick(tick);
        }
        if (speed > 0.0D && this.lastSpeed > 0.0D && speed != this.lastSpeed)
        {
            // the moment it's at, at the old speed: start it earlier or later so the new speed puts it there too
            double moment = this.lastSpeed * Math.max(tick - this.tickOffset, 0.0D);
            this.tickOffset = tick - moment / speed;
        }
        this.lastSpeed = speed;
        return speed * Math.max(tick - this.tickOffset, 0.0D);
    }
}
