package com.reg21meme.dross.boss.huntsman;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
 * animation (standing) after {@link #HOLD_AFTER} ticks. Their fight comes later.
 */
public abstract class HuntMob extends Mob implements GeoEntity
{
    /** A move's last frame is held this many ticks before it goes back to standing. */
    public static final int HOLD_AFTER = 20;
    /** GeckoLib's blend between two animations (ticks). */
    private static final int BLEND_TICKS = 4;
    private static final String TAG_ANIM = "Anim";

    private static final EntityDataAccessor<String> DATA_ANIM = SynchedEntityData.defineId(HuntMob.class,
            EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Integer> DATA_COUNT = SynchedEntityData.defineId(HuntMob.class,
            EntityDataSerializers.INT);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int seenCount = -1;
    private int playing;

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

    /** Plays one of its animations (server side). Returns false if it has none of that name. */
    public boolean play(String name)
    {
        if (HuntAnims.named(this.animations(), name) == null)
        {
            return false;
        }
        this.entityData.set(DATA_ANIM, name);
        this.entityData.set(DATA_COUNT, this.entityData.get(DATA_COUNT) + 1);
        this.playing = 0;
        return true;
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!this.level().isClientSide)
        {
            HuntAnims.Anim a = this.current();
            if (!a.loop() && ++this.playing > a.ticks() + HOLD_AFTER)
            {
                this.entityData.set(DATA_ANIM, "");
                this.entityData.set(DATA_COUNT, this.entityData.get(DATA_COUNT) + 1);
            }
        }
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
