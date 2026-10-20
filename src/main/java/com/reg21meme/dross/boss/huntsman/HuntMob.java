package com.reg21meme.dross.boss.huntsman;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

/**
 * What the Huntsman and the Alpha Moose share (CLAUDE.md step 14, his fight): their GeckoLib animations, picked by
 * name on the server and synced (with a counter, so the same one asked for again starts over). For now they're on
 * show only ({@code /dross showcase boss_designs huntsman}): frozen (no AI), they can't be hurt except by
 * {@code /kill}, can't be pushed or leashed and never despawn; a move plays once and then they go back to their first
 * animation (standing) after {@link #HOLD_AFTER} ticks, except a death, which stays down. His shots loose real arrows
 * from his bow ({@link HuntCues}), harmless while he's on show, and a few moments make a placeholder sound
 * ({@link HuntShowSounds}) until the real sounds are picked. Their fight comes later.
 */
public abstract class HuntMob extends Mob implements GeoEntity
{
    /** A move's last frame is held this many ticks before it goes back to standing. */
    public static final int HOLD_AFTER = 20;
    /** GeckoLib's blend between two animations (ticks). */
    protected static final int BLEND_TICKS = 4;
    /** His arrows' speed (a fully drawn bow shoots at 3), and the arrow rain's (into the sky, so they come down near). */
    public static final float ARROW_SPEED = 3.0F;
    public static final float VOLLEY_SPEED = 1.8F;
    /** Bigger moves than this (blocks) are shown at once ({@link #lerpTo}). */
    public static final double SNAP = 1.0D;
    /** A shot this steep (its direction's up part) is a volley into the sky. */
    private static final double VOLLEY_UP = 0.6D;
    private static final String TAG_ANIM = "Anim";

    private static final EntityDataAccessor<String> DATA_ANIM = SynchedEntityData.defineId(HuntMob.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_COUNT = SynchedEntityData.defineId(HuntMob.class,
            EntityDataSerializers.INT);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int seenCount = -1;
    private int playing;
    private String later;
    private int laterTicks;

    protected HuntMob(EntityType<? extends HuntMob> type, Level level)
    {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_ANIM, "");
        this.entityData.define(DATA_COUNT, 0);
    }

    /** The animations it has right now (the Huntsman's change when he's in the saddle). */
    public abstract List<HuntAnims.Anim> animations();

    /** The animation playing (its first, standing, if none was asked for or the one asked for isn't one of its own). */
    public HuntAnims.Anim current()
    {
        HuntAnims.Anim a = HuntAnims.named(this.animations(), this.entityData.get(DATA_ANIM));
        return a != null ? a : this.animations().get(0);
    }

    /** Goes up by one every time an animation starts (both sides): the Huntsman's saddle follows the moose's. */
    public int animCount()
    {
        return this.entityData.get(DATA_COUNT);
    }

    /** Ticks since the current animation started (server side). */
    public int playingTicks()
    {
        return this.playing;
    }

    /** Plays one of its animations (server side). Returns false if it has none of that name. */
    public boolean play(String name)
    {
        if (HuntAnims.named(this.animations(), name) == null)
        {
            return false;
        }
        this.later = null;
        this.entityData.set(DATA_ANIM, name);
        this.entityData.set(DATA_COUNT, this.entityData.get(DATA_COUNT) + 1);
        this.playing = 0;
        return true;
    }

    /** Plays one of its animations {@code delay} ticks from now (server side). Returns false if it has none of that
     * name. */
    public boolean playLater(String name, int delay)
    {
        if (HuntAnims.named(this.animations(), name) == null)
        {
            return false;
        }
        if (delay <= 0)
        {
            return this.play(name);
        }
        this.later = name;
        this.laterTicks = delay;
        return true;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!this.level().isClientSide)
        {
            if (this.later != null && --this.laterTicks <= 0)
            {
                this.play(this.later);
            }
            HuntAnims.Anim a = this.current();
            if (!a.loop())
            {
                this.playing++;
                for (HuntCues.Shot shot : HuntCues.shots(a.model(), a.name()))
                {
                    if (shot.tick() == this.playing)
                    {
                        this.loose(shot);
                    }
                }
                HuntShowSounds.play(this, a, this.playing);
                if (!a.stays() && this.playing > a.ticks() + HOLD_AFTER)
                {
                    this.moveBy(HuntCues.shift(a.model(), a.name()));
                    this.entityData.set(DATA_ANIM, "");
                    this.entityData.set(DATA_COUNT, this.entityData.get(DATA_COUNT) + 1);
                    this.playing = 0;
                }
            }
        }
    }

    /** Moves it where an animation that carries it (the backstep shot's leap) left it: blocks to its right, up and
     * ahead (its body's facing), in the same tick as it goes back to standing, so it's never seen jump back. */
    private void moveBy(double[] shift)
    {
        if (shift == null)
        {
            return;
        }
        double yaw = Math.toRadians(this.yBodyRot);
        double x = this.getX() - Math.cos(yaw) * shift[0] - Math.sin(yaw) * shift[2];
        double z = this.getZ() - Math.sin(yaw) * shift[0] + Math.cos(yaw) * shift[2];
        this.moveTo(x, this.getY() + shift[1], z, this.getYRot(), this.getXRot());
    }

    /**
     * A move of more than {@link #SNAP} blocks in one go (the leap off the moose, the backstep's landing) is shown at
     * once, not slid over the next ticks the way the game smooths other moves (the user saw him slide sideways as he
     * landed).
     */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport)
    {
        if (this.position().distanceToSqr(x, y, z) > SNAP * SNAP)
        {
            this.moveTo(x, y, z, yRot, xRot);
            super.lerpTo(x, y, z, yRot, xRot, 1, teleport);
            return;
        }
        super.lerpTo(x, y, z, yRot, xRot, steps, teleport);
    }

    /** A real arrow off his bow, where the animation's arrow leaves it, the way it flies. On show it can't hurt:
     * it has no damage, so it glances off a player. */
    private void loose(HuntCues.Shot shot)
    {
        double yaw = Math.toRadians(this.yBodyRot);
        Vec3 ahead = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
        Vec3 right = new Vec3(-Math.cos(yaw), 0.0D, -Math.sin(yaw));
        Vec3 from = this.position().add(right.scale(shot.right())).add(0.0D, shot.up(), 0.0D)
                .add(ahead.scale(shot.ahead()));
        Vec3 dir = right.scale(shot.dirRight()).add(0.0D, shot.dirUp(), 0.0D).add(ahead.scale(shot.dirAhead()))
                .normalize();
        Arrow arrow = new Arrow(this.level(), from.x, from.y, from.z);
        arrow.setOwner(this);
        arrow.setBaseDamage(0.0D);
        arrow.pickup = AbstractArrow.Pickup.DISALLOWED;
        arrow.shoot(dir.x, dir.y, dir.z, shot.dirUp() > VOLLEY_UP ? VOLLEY_SPEED : ARROW_SPEED, 0.0F);
        this.level().addFreshEntity(arrow);
        this.playSound(SoundEvents.ARROW_SHOOT, 1.0F, 1.0F / (this.getRandom().nextFloat() * 0.4F + 1.2F) + 0.5F);
    }

    // ---------------------------------------------------------------- on show

    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        return !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public void push(double x, double y, double z)
    {
    }

    @Override
    public boolean isPushedByFluid()
    {
        return false;
    }

    @Override
    public boolean canBeLeashed(Player player)
    {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    @Override
    public boolean canFreeze()
    {
        return false;
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_ANIM, this.entityData.get(DATA_ANIM));
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_ANIM, tag.getString(TAG_ANIM));
    }

    // ---------------------------------------------------------------- GeckoLib

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<HuntMob> state)
    {
        int count = this.entityData.get(DATA_COUNT);
        if (count != this.seenCount)
        {
            this.seenCount = count;
            state.getController().forceAnimationReset();
        }
        return state.setAndContinue(this.current().raw());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }
}
