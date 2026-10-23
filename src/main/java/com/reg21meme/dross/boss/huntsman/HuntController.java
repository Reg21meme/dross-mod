package com.reg21meme.dross.boss.huntsman;

import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animatable.model.CoreGeoModel;
import software.bernie.geckolib.core.animation.Animation;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.state.BoneSnapshot;

import java.util.Map;
import java.util.function.ToDoubleBiFunction;

/**
 * A GeckoLib controller for the Huntsman and the Alpha Moose that keeps time with the world rather than with whichever
 * frames happen to draw it (the user, October 22, 2026: his cloak still went into the moose's rump at a gallop).
 * <p>
 * GeckoLib starts an animation's clock on the frame it's first drawn and starts a loop over on the first frame after it
 * ends, so two entities' animations drift apart: the moose's gallop and his ride along with it (his {@code carry_run}),
 * or his own part of the gallop, by a tick or more, more after one of them has been off screen. Here a playing
 * animation's moment is worked out from the world's game time every frame: it starts on its {@code anchor} (the tick the
 * server started it plus the blend: {@link HuntMob#anchor}) and a loop goes round on whole lengths from it, so the moose
 * and his saddle (both anchored on the moose's animation) are always at the same moment of it. Its {@code speed} plays
 * it faster or slower (a gallop at the pace they really run, so hooves and boots don't slide: {@link HuntMob#pace}); when
 * the server changes a loop's speed it moves the anchor too, so the loop goes on from the moment it was at. Blends
 * between two animations work as GeckoLib's own do.
 */
final class HuntController<T extends HuntMob> extends AnimationController<T>
{
    /** (mob, animation name) to the game time its first beat falls on (NaN if unknown) and to how fast it plays. */
    private final ToDoubleBiFunction<T, String> anchor;
    private final ToDoubleBiFunction<T, String> speed;
    private double now = Double.NaN;

    HuntController(T mob, String name, int blendTicks, AnimationStateHandler<T> handler,
                   ToDoubleBiFunction<T, String> anchor, ToDoubleBiFunction<T, String> speed)
    {
        super(mob, name, blendTicks, handler);
        this.anchor = anchor;
        this.speed = speed;
    }

    @Override
    public void process(CoreGeoModel<T> model, AnimationState<T> state, Map<String, CoreGeoBone> bones,
                        Map<String, BoneSnapshot> snapshots, double seekTime, boolean crashWhenCantFindBone)
    {
        this.now = this.animatable.level().getGameTime() + state.getPartialTick();
        super.process(model, state, bones, snapshots, seekTime, crashWhenCantFindBone);
    }

    @Override
    protected double adjustTick(double tick)
    {
        double now = this.now;
        if (Double.isNaN(now))
        {
            return super.adjustTick(tick);
        }
        boolean reset = this.shouldResetTick;
        this.shouldResetTick = false;
        String name = this.currentAnimation != null ? HuntAnims.shortName(this.currentAnimation.animation().name()) : null;
        if (this.animationState == State.TRANSITIONING || name == null)
        {
            // a blend into the next animation: it starts now and takes the blend's own time, as GeckoLib's does
            if (reset)
            {
                if (this.animationState != State.STOPPED)
                {
                    this.tickOffset = now;
                }
                return 0.0D;
            }
            return Math.max(now - this.tickOffset, 0.0D);
        }
        double speed = Math.max(1.0E-3D, this.speed.applyAsDouble(this.animatable, name));
        double anchor = this.anchor.applyAsDouble(this.animatable, name);
        double length = this.currentAnimation.animation().length();
        if (Double.isNaN(anchor) || length <= 0.0D)
        {
            if (reset)
            {
                this.tickOffset = now;
                return 0.0D;
            }
            return speed * Math.max(now - this.tickOffset, 0.0D);
        }
        // the animation itself: its moment by the world's clock
        double time = Math.max((now - anchor) * speed, 0.0D);
        if (this.currentAnimation.loopType() == Animation.LoopType.LOOP)
        {
            time %= length;
        }
        this.tickOffset = now - time / speed;
        return time;
    }
}
