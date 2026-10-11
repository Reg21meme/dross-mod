package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * A blob of lava a Cinder Colossus's volcanoes spit out ({@code dross:lava_blob}): it flies on a high arc and, where
 * it lands, leaves a pool of temporary lava ({@link LavaPools#makePool}). One that lands right on someone burns
 * them. The Colossus aims its blobs a few blocks from a player, never right at them.
 */
public class LavaBlob extends BossProjectile
{
    private static final String TAG_DAMAGE = "Damage";
    private static final String TAG_POOL = "PoolSteps";

    private float damage = ColossusTuning.LOB_DIRECT_DAMAGE;
    /** How long its pool lasts, in cooling steps of {@link CinderLavaBlock#STEP_TICKS} ticks. */
    private int poolSteps = CinderLavaBlock.MAX_LIFE;

    public LavaBlob(EntityType<? extends LavaBlob> type, Level level)
    {
        super(type, level);
    }

    /** A blob spat by {@code owner} from {@code from}. Its pool lasts between the tuning's min and max time. */
    public static LavaBlob spat(Level level, LivingEntity owner, Vec3 from, float damage)
    {
        LavaBlob blob = new LavaBlob(ModEntities.LAVA_BLOB.get(), level);
        blob.setOwner(owner);
        blob.setPos(from.x, from.y, from.z);
        blob.damage = damage;
        int ticks = ColossusTuning.POOL_MIN_TICKS
                + level.random.nextInt(ColossusTuning.POOL_MAX_TICKS - ColossusTuning.POOL_MIN_TICKS + 1);
        blob.poolSteps = ticks / CinderLavaBlock.STEP_TICKS - 1;
        return blob;
    }

    @Override
    protected double gravity()
    {
        return ColossusTuning.LOB_GRAVITY;
    }

    @Override
    protected void flyingEffects()
    {
        if (!this.level().isClientSide)
        {
            return;
        }
        this.level().addParticle(ParticleTypes.FLAME, this.getX() + (this.random.nextDouble() - 0.5D) * 0.4D,
                this.getY() + 0.3D, this.getZ() + (this.random.nextDouble() - 0.5D) * 0.4D, 0.0D, 0.0D, 0.0D);
        if (this.random.nextInt(3) == 0)
        {
            this.level().addParticle(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.4D, this.getZ(), 0.0D, 0.02D, 0.0D);
        }
        if (this.random.nextInt(4) == 0)
        {
            this.level().addParticle(ParticleTypes.LAVA, this.getX(), this.getY() + 0.3D, this.getZ(), 0.0D, 0.0D, 0.0D);
        }
    }

    @Override
    protected void onHitEntity(EntityHitResult hit)
    {
        if (this.level().isClientSide)
        {
            return;
        }
        Entity target = hit.getEntity();
        Entity owner = this.getOwner();
        DamageSource source = this.damageSources().mobProjectile(this, owner instanceof LivingEntity living ? living : null);
        if (target.hurt(source, this.damage))
        {
            target.setSecondsOnFire(ColossusTuning.CORE_FIRE_SECONDS);
        }
        this.splash(target.blockPosition());
    }

    @Override
    protected void onHitBlock(BlockHitResult hit)
    {
        if (!this.level().isClientSide)
        {
            this.splash(hit.getBlockPos().relative(hit.getDirection()));
        }
    }

    /** Splashes down: a pool of temporary lava on the ground around {@code where}, a burst of lava and smoke. */
    private void splash(BlockPos where)
    {
        if (this.level() instanceof ServerLevel level)
        {
            Entity owner = this.getOwner();
            boolean plants = ForgeEventFactory.getMobGriefingEvent(level, owner != null ? owner : this);
            LavaPools.makePool(level, where, this.poolSteps, this.random, plants);
            Vec3 at = Vec3.atBottomCenterOf(where);
            level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3D, at.z, 12, 1.0D, 0.3D, 1.0D, 0.0D);
            level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3D, at.z, 16, 1.2D, 0.2D, 1.2D, 0.03D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6D, at.z, 8, 1.0D, 0.4D, 1.0D, 0.02D);
            level.playSound(null, at.x, at.y, at.z, ModSounds.LAVA_BLOB_SPLASH.get(), SoundSource.HOSTILE, 2.0F,
                    0.9F + this.random.nextFloat() * 0.2F);
        }
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putFloat(TAG_DAMAGE, this.damage);
        tag.putInt(TAG_POOL, this.poolSteps);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_DAMAGE))
        {
            this.damage = tag.getFloat(TAG_DAMAGE);
        }
        if (tag.contains(TAG_POOL))
        {
            this.poolSteps = tag.getInt(TAG_POOL);
        }
    }
}
