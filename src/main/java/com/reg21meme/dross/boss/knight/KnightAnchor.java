package com.reg21meme.dross.boss.knight;

import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;

/**
 * The Wreck Knight's anchor, thrown on its chain ({@code dross:knight_anchor}; the user: "him throwing the anchor and
 * stuff", and before, "he throws his anchor at you and pulls it back"). It flies straight at his foe; the first thing it
 * hits takes {@link KnightTuning#ANCHOR_DAMAGE} and is hooked: hauled back towards him until it's near him (or a while
 * has gone); a wall, or flying out to its full length, and it comes straight back. Then it flies back to his hand and is
 * gone (he carries it on his shoulder again: {@link ElementalKnight#setAnchorOut}). Drawn with its own model
 * ({@code knightmob/wreck_anchor}, made by tools/knights/props.py) and its chain back to his hand.
 */
public class KnightAnchor extends Entity implements GeoEntity
{
    private static final EntityDataAccessor<Integer> DATA_OWNER = SynchedEntityData.defineId(KnightAnchor.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HOOKED = SynchedEntityData.defineId(KnightAnchor.class, EntityDataSerializers.INT);

    private enum State { OUT, HOOKED, BACK }

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private State state = State.OUT;
    private int stateTicks;

    public KnightAnchor(EntityType<? extends KnightAnchor> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;
    }

    /** An anchor thrown by {@code owner} from {@code from} at {@code to}. */
    public static KnightAnchor thrown(Level level, ElementalKnight owner, Vec3 from, Vec3 to)
    {
        KnightAnchor anchor = new KnightAnchor(ModEntities.KNIGHT_ANCHOR.get(), level);
        anchor.entityData.set(DATA_OWNER, owner.getId());
        anchor.setPos(from.x, from.y, from.z);
        Vec3 v = to.subtract(from).normalize().scale(KnightTuning.ANCHOR_SPEED);
        anchor.setDeltaMovement(v);
        anchor.face(v);
        return anchor;
    }

    @Override
    protected void defineSynchedData()
    {
        this.entityData.define(DATA_OWNER, -1);
        this.entityData.define(DATA_HOOKED, -1);
    }

    /** The knight who threw it (both sides), or null. */
    @Nullable
    public ElementalKnight owner()
    {
        return this.level().getEntity(this.entityData.get(DATA_OWNER)) instanceof ElementalKnight knight ? knight : null;
    }

    /** Where its chain starts: the knight's right hand (roughly), or null. */
    @Nullable
    public Vec3 chainStart(float partialTick)
    {
        ElementalKnight owner = this.owner();
        if (owner == null)
        {
            return null;
        }
        float yaw = Mth.lerp(partialTick, owner.yBodyRotO, owner.yBodyRot) * Mth.DEG_TO_RAD;
        Vec3 at = owner.getPosition(partialTick);
        // his right hand: a little ahead of him and out to his right, about chest high
        double fx = -Mth.sin(yaw), fz = Mth.cos(yaw);
        return at.add(fx * 0.9D - fz * 0.9D, 3.1D, fz * 0.9D + fx * 0.9D);
    }

    /** Turns it to point along {@code v}. */
    private void face(Vec3 v)
    {
        if (v.lengthSqr() < 1.0E-6D)
        {
            return;
        }
        this.setYRot((float) (Mth.atan2(-v.x, v.z) * Mth.RAD_TO_DEG));
        this.setXRot((float) (Mth.atan2(-v.y, v.horizontalDistance()) * Mth.RAD_TO_DEG));
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
    }

    @Override
    public void tick()
    {
        this.xo = this.getX();
        this.yo = this.getY();
        this.zo = this.getZ();
        this.yRotO = this.getYRot();
        this.xRotO = this.getXRot();
        super.tick();
        if (this.level().isClientSide)
        {
            Vec3 v = this.position().subtract(this.xo, this.yo, this.zo);
            if (this.state == State.OUT && v.lengthSqr() > 1.0E-4D)
            {
                this.face(v);
            }
            this.level().addParticle(new DustParticleOptions(com.reg21meme.dross.DrossColors.vector(
                    com.reg21meme.dross.DrossColors.KNIGHT_SPARK), 1.0F), this.getX(), this.getY(), this.getZ(), 0, 0, 0);
            return;
        }
        ElementalKnight owner = this.owner();
        if (owner == null || !owner.isAlive() || this.tickCount > 200)
        {
            this.discard();
            return;
        }
        this.stateTicks++;
        Vec3 hand = this.chainStart(1.0F);
        switch (this.state)
        {
            case OUT -> this.flyOut(owner);
            case HOOKED -> this.haul(owner, hand);
            case BACK -> this.comeBack(owner, hand);
        }
    }

    private void flyOut(ElementalKnight owner)
    {
        HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, e -> e instanceof LivingEntity living
                && living != owner && owner.canHit(living));
        if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living)
        {
            living.hurt(this.damageSources().mobProjectile(this, owner), KnightTuning.ANCHOR_DAMAGE);
            this.entityData.set(DATA_HOOKED, living.getId());
            this.state = State.HOOKED;
            this.stateTicks = 0;
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ElementalKnight.knightSound(KnightKind.WRECK, "anchor_hook"),
                    SoundSource.HOSTILE, 1.8F, 1.0F);
            if (this.level() instanceof ServerLevel level)
            {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, living.getX(), living.getY() + living.getBbHeight() / 2.0D,
                        living.getZ(), 20, 0.3D, 0.4D, 0.3D, 0.3D);
            }
            return;
        }
        if (hit.getType() == HitResult.Type.BLOCK)
        {
            this.setPos(hit.getLocation());
            this.goBack();
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ElementalKnight.knightSound(KnightKind.WRECK, "anchor_clang"),
                    SoundSource.HOSTILE, 1.4F, 1.0F);
            return;
        }
        Vec3 v = this.getDeltaMovement();
        this.setPos(this.position().add(v));
        if (this.distanceTo(owner) > KnightTuning.ANCHOR_MAX_OUT)
        {
            this.goBack();
        }
    }

    private void haul(ElementalKnight owner, Vec3 hand)
    {
        Entity hooked = this.level().getEntity(this.entityData.get(DATA_HOOKED));
        if (!(hooked instanceof LivingEntity living) || !living.isAlive() || this.stateTicks > KnightTuning.ANCHOR_HAUL_TICKS
                || living.distanceTo(owner) < KnightTuning.ANCHOR_HAUL_STOP)
        {
            this.goBack();
            return;
        }
        Vec3 pull = owner.position().subtract(living.position());
        pull = new Vec3(pull.x, 0.0D, pull.z).normalize().scale(KnightTuning.ANCHOR_HAUL_SPEED);
        living.setDeltaMovement(pull.x, Math.max(living.getDeltaMovement().y, living.onGround() ? 0.12D : -0.1D), pull.z);
        living.hurtMarked = true;
        living.fallDistance = 0.0F;
        this.setPos(living.getX(), living.getY() + living.getBbHeight() * 0.5D, living.getZ());
        if (this.stateTicks % 6 == 0)
        {
            this.level().playSound(null, this.getX(), this.getY(), this.getZ(), ElementalKnight.knightSound(KnightKind.WRECK, "anchor_haul"),
                    SoundSource.HOSTILE, 1.3F, 0.95F + this.random.nextFloat() * 0.1F);
        }
    }

    private void comeBack(ElementalKnight owner, Vec3 hand)
    {
        Vec3 to = hand.subtract(this.position());
        if (to.length() < KnightTuning.ANCHOR_RETURN_SPEED * 1.2D || this.stateTicks > 60)
        {
            owner.setAnchorOut(false);
            this.level().playSound(null, owner.getX(), owner.getY() + 3.0D, owner.getZ(), ElementalKnight.knightSound(KnightKind.WRECK, "anchor_catch"),
                    SoundSource.HOSTILE, 1.2F, 1.0F);
            this.discard();
            return;
        }
        Vec3 v = to.normalize().scale(KnightTuning.ANCHOR_RETURN_SPEED);
        this.setDeltaMovement(v);
        this.setPos(this.position().add(v));
        this.face(v.scale(-1.0D));     // (trailing back on its chain, ring first)
    }

    private void goBack()
    {
        this.state = State.BACK;
        this.stateTicks = 0;
        this.entityData.set(DATA_HOOKED, -1);
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance)
    {
        return distance < 128.0D * 128.0D;
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
    }

    /** Never saved: one left when its land unloads would have no knight to go back to. */
    @Override
    public boolean shouldBeSaved()
    {
        return false;
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.cache;
    }
}
