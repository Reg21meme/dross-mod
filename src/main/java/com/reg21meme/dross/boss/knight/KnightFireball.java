package com.reg21meme.dross.boss.knight;

import com.reg21meme.dross.boss.golem.BossProjectile;
import com.reg21meme.dross.boss.golem.LavaPools;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * One of Flamehand's fireballs ({@code dross:knight_fireball}): straight, no fall. A small one hits what it meets and
 * sets it burning; a great one ({@link #isGreat}) is slow and big, and bursts where it lands: everything round it hurt
 * (less at the edge), burning, thrown out, a ring of the Colossus's fire left (it goes out by itself). Neither breaks or
 * lights a block of the world (the Colossus's fire only goes into air). Drawn as Minecraft's fire charge, the great one
 * {@link KnightTuning#GREAT_SCALE} times as big, both full-bright.
 */
public class KnightFireball extends BossProjectile implements ItemSupplier
{
    private static final EntityDataAccessor<Boolean> DATA_GREAT = SynchedEntityData.defineId(KnightFireball.class, EntityDataSerializers.BOOLEAN);

    public KnightFireball(EntityType<? extends KnightFireball> type, Level level)
    {
        super(type, level);
    }

    /** A fireball from {@code owner} at {@code from}, flying straight at {@code to}. */
    public static KnightFireball shoot(Level level, LivingEntity owner, Vec3 from, Vec3 to, boolean great)
    {
        KnightFireball ball = new KnightFireball(ModEntities.KNIGHT_FIREBALL.get(), level);
        ball.setOwner(owner);
        ball.setPos(from.x, from.y, from.z);
        ball.entityData.set(DATA_GREAT, great);
        double speed = great ? KnightTuning.GREAT_SPEED : KnightTuning.FIREBALL_SPEED;
        ball.setDeltaMovement(to.subtract(from).normalize().scale(speed));
        return ball;
    }

    public boolean isGreat()
    {
        return this.entityData.get(DATA_GREAT);
    }

    /** A small one hits anything within this much of its path (a great one only what it meets: it bursts anyway). */
    @Override
    protected double hitMargin()
    {
        return this.isGreat() ? 0.0D : KnightTuning.FIREBALL_HIT_MARGIN;
    }

    @Override
    protected void defineSynchedData()
    {
        this.entityData.define(DATA_GREAT, false);
    }

    @Override
    protected double gravity()
    {
        return 0.0D;
    }

    @Override
    public ItemStack getItem()
    {
        return new ItemStack(Items.FIRE_CHARGE);
    }

    @Override
    protected void flyingEffects()
    {
        if (!this.level().isClientSide)
        {
            return;
        }
        int n = this.isGreat() ? 6 : 1;
        double spread = this.isGreat() ? 0.6D : 0.1D;
        for (int i = 0; i < n; i++)
        {
            this.level().addParticle(ParticleTypes.FLAME, this.getX() + (this.random.nextDouble() - 0.5D) * spread,
                    this.getY() + 0.15D + (this.random.nextDouble() - 0.5D) * spread,
                    this.getZ() + (this.random.nextDouble() - 0.5D) * spread, 0.0D, 0.0D, 0.0D);
        }
        this.level().addParticle(ParticleTypes.SMOKE, this.getX(), this.getY() + 0.15D, this.getZ(), 0.0D, 0.0D, 0.0D);
    }

    /** It flies through its knight's friends (its hits aren't fire any more: they'd hurt a fire knight). */
    @Override
    protected boolean canHitEntity(Entity e)
    {
        if (this.getOwner() instanceof ElementalKnight knight && e instanceof LivingEntity living && !knight.canHit(living))
        {
            return false;
        }
        return super.canHitEntity(e);
    }

    // Its hits are a projectile's and its great blast an explosion, not fire (the user, November 19, 2026: Flamehand
    // "doesn't do damage ... I have fire resistance": a fireball's damage is fire, which Fire Resistance takes away
    // whole); the burning it sets still is fire.
    @Override
    protected void onHit(HitResult hit)
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        Entity owner = this.getOwner();
        if (this.isGreat())
        {
            burst(level, hit.getLocation(), owner);
        }
        else if (hit instanceof EntityHitResult entityHit && entityHit.getEntity() instanceof LivingEntity living)
        {
            living.hurt(ElementalKnight.typed(level, DamageTypes.MOB_PROJECTILE, this, owner), KnightTuning.FIREBALL_DAMAGE);
            living.setSecondsOnFire(KnightTuning.FIREBALL_FIRE_SECONDS);
            level.sendParticles(ParticleTypes.FLAME, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 12,
                    0.25D, 0.25D, 0.25D, 0.05D);
        }
        else
        {
            splash(level, hit.getLocation(), owner);
            level.sendParticles(ParticleTypes.FLAME, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z, 10,
                    0.2D, 0.2D, 0.2D, 0.04D);
            level.playSound(null, hit.getLocation().x, hit.getLocation().y, hit.getLocation().z,
                    ElementalKnight.knightSound(KnightKind.FLAMEHAND, "fizzle"), SoundSource.HOSTILE, 0.8F, 1.0F);
        }
        this.discard();
    }

    /** A small fireball that lands on the ground: fire splashes round where it lands. */
    private void splash(ServerLevel level, Vec3 at, Entity owner)
    {
        double r = KnightTuning.FIREBALL_SPLASH_RADIUS;
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.2D, at.z, 18, r * 0.35D, 0.15D, r * 0.35D, 0.03D);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.2D, at.z, 3, 0.3D, 0.1D, 0.3D, 0.0D);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r + 1.0D)))
        {
            if (owner instanceof ElementalKnight knight ? !knight.canHit(living) : !living.isAlive())
            {
                continue;
            }
            if (living.getBoundingBox().distanceToSqr(at) > r * r)
            {
                continue;
            }
            living.hurt(ElementalKnight.typed(level, DamageTypes.MOB_PROJECTILE, this, owner), KnightTuning.FIREBALL_SPLASH_DAMAGE);
            living.setSecondsOnFire(KnightTuning.FIREBALL_FIRE_SECONDS);
        }
    }

    /** The great fireball's burst. */
    private void burst(ServerLevel level, Vec3 at, Entity owner)
    {
        double r = KnightTuning.GREAT_RADIUS;
        level.playSound(null, at.x, at.y, at.z, ElementalKnight.knightSound(KnightKind.FLAMEHAND, "blast"), SoundSource.HOSTILE, 2.6F, 1.0F);
        level.sendParticles(ParticleTypes.EXPLOSION_EMITTER, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, 80, r * 0.4D, 0.6D, r * 0.4D, 0.15D);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 20, r * 0.3D, 0.4D, r * 0.3D, 0.0D);
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r)))
        {
            if (owner instanceof ElementalKnight knight ? !knight.canHit(living) : !living.isAlive())
            {
                continue;
            }
            double d = living.position().add(0.0D, living.getBbHeight() / 2.0D, 0.0D).distanceTo(at);
            if (d > r)
            {
                continue;
            }
            float share = 1.0F - 0.5F * (float) (d / r);
            living.hurt(ElementalKnight.typed(level, DamageTypes.EXPLOSION, this, owner), KnightTuning.GREAT_DAMAGE * share);
            living.setSecondsOnFire(KnightTuning.GREAT_FIRE_SECONDS);
            Vec3 away = living.position().subtract(at).multiply(1.0D, 0.0D, 1.0D);
            away = away.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
            living.knockback(KnightTuning.GREAT_KNOCK * share, -away.x, -away.z);
            living.setDeltaMovement(living.getDeltaMovement().add(0.0D, 0.35D * share, 0.0D));
            living.hurtMarked = true;
        }
        for (int i = 0; i < KnightTuning.GREAT_FIRES; i++)
        {
            double a = 2.0D * Math.PI * (i + this.random.nextDouble() * 0.5D) / KnightTuning.GREAT_FIRES;
            double d = r * (0.4D + this.random.nextDouble() * 0.5D);
            BlockPos spot = LavaPools.surfaceNear(level, BlockPos.containing(at.x + Math.cos(a) * d, at.y + 1.0D,
                    at.z + Math.sin(a) * d), 4, false);
            if (spot != null)
            {
                LavaPools.placeFire(level, spot, false, KnightTuning.SLAM_FIRE_MIN_TICKS, KnightTuning.SLAM_FIRE_MAX_TICKS);
            }
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        this.entityData.set(DATA_GREAT, tag.getBoolean("Great"));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        tag.putBoolean("Great", this.isGreat());
    }
}
