package com.reg21meme.dross.boss.rimefall;

import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.Locale;

/**
 * One Rimefall candidate on show: a frozen model of one of the Ice Necromancer's possible servants or one of Rimefall's
 * possible regular mobs, in one of its variants ({@link RimefallDesigns}, made by {@code tools/rimefall/build.py}), for
 * {@code /dross showcase boss_designs rimefall}. The user asked for the looks only: it never moves (no AI, no
 * animations), makes no sound and never fights.
 * <p>
 * Clients get the design number; the hitbox follows the design (its measured size, from a little taller than a player
 * to about 27 blocks), and the area it's drawn for reaches as far as the model does, so the giants never vanish when their
 * feet leave the screen. It can't be hurt except by {@code /kill} (damage that bypasses invulnerability), can't be
 * pushed or leashed, never despawns (not even on Peaceful) and doesn't burn. Right-clicking it shows its number, name
 * and mood on the action bar.
 */
public class RimefallCandidate extends Mob implements GeoEntity
{
    private static final EntityDataAccessor<Integer> DATA_DESIGN = SynchedEntityData.defineId(RimefallCandidate.class,
            EntityDataSerializers.INT);
    private static final String TAG_DESIGN = "Design";
    /** Blocks added round the model's reach for the area it's drawn for. */
    private static final double CULL_MARGIN = 1.0D;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    public RimefallCandidate(EntityType<? extends RimefallCandidate> type, Level level)
    {
        super(type, level);
        this.setNoAi(true);
        this.setSilent(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, 20.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_DESIGN, 1);
    }

    /** The design it shows. Works on both sides. */
    public RimefallDesigns.Design getDesign()
    {
        return RimefallDesigns.byNumber(this.entityData.get(DATA_DESIGN));
    }

    public void setDesign(int number)
    {
        this.entityData.set(DATA_DESIGN, RimefallDesigns.byNumber(number).number());
        this.refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);
        if (DATA_DESIGN.equals(key))
        {
            this.refreshDimensions();
        }
    }

    /** The hitbox: the design's measured size (the very wide ones get their body's width: a hitbox is square). */
    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        RimefallDesigns.Design design = this.getDesign();
        return EntityDimensions.scalable(design.width(), design.height());
    }

    /** Drawn whenever any part of the model could be on screen: its whole reach round its feet, up to its top. */
    @Override
    public AABB getBoundingBoxForCulling()
    {
        RimefallDesigns.Design design = this.getDesign();
        double reach = design.reach() + CULL_MARGIN;
        return new AABB(this.getX() - reach, this.getY() - CULL_MARGIN, this.getZ() - reach,
                this.getX() + reach, this.getY() + design.top() + CULL_MARGIN, this.getZ() + reach);
    }

    // ---------------------------------------------------------------- a frozen exhibit

    /** Nothing hurts it except damage that bypasses invulnerability ({@code /kill}, the void). */
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

    /** Right-click: its number, name and mood (and how tall it is) on the action bar. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide)
        {
            return InteractionResult.SUCCESS;
        }
        RimefallDesigns.Design design = this.getDesign();
        player.displayClientMessage(Component.translatable("dross.rimefall.showcase.info", design.label(), design.name(),
                design.mood(), String.format(Locale.ROOT, "%.1f", design.top())), true);
        return InteractionResult.CONSUME;
    }

    // ---------------------------------------------------------------- GeckoLib (no animations: the model's own pose)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }

    // ---------------------------------------------------------------- saving and spawning

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_DESIGN, this.getDesign().number());
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_DESIGN))
        {
            this.setDesign(tag.getInt(TAG_DESIGN));
        }
    }

    /**
     * Spawns a candidate on show.
     *
     * @param feet where its feet go (the bottom middle of the air block above the ground)
     * @param yaw  the way it faces, in degrees (0 = south, 90 = west, 180 = north, -90 = east)
     * @return the candidate, already in the world, or null if it couldn't be created
     */
    @Nullable
    public static RimefallCandidate spawnShowcase(ServerLevel level, Vec3 feet, float yaw, RimefallDesigns.Design design)
    {
        RimefallCandidate candidate = ModEntities.RIMEFALL_CANDIDATE.get().create(level);
        if (candidate == null)
        {
            return null;
        }
        candidate.setDesign(design.number());
        candidate.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        candidate.setYHeadRot(yaw);
        candidate.setYBodyRot(yaw);
        candidate.yHeadRotO = yaw;
        candidate.yBodyRotO = yaw;
        level.addFreshEntity(candidate);
        return candidate;
    }
}
