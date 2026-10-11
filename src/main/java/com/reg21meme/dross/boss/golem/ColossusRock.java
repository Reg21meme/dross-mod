package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * A chunk of rock the Cinder Colossus tears out of the ground and throws ({@code dross:colossus_rock}). It flies on
 * an arc, hurts and knocks back whoever it hits and shatters into dust when it lands. It never places a block and
 * never breaks one. In the Lava Core stage it's molten (it looks like magma and sets you on fire).
 */
public class ColossusRock extends BossProjectile
{
    private static final EntityDataAccessor<Boolean> DATA_MOLTEN = SynchedEntityData.defineId(ColossusRock.class, EntityDataSerializers.BOOLEAN);
    private static final String TAG_DAMAGE = "Damage";
    private static final String TAG_MOLTEN = "Molten";
    /** Dust particles when it shatters. */
    private static final int SHATTER_PARTICLES = 40;

    private float damage = ColossusTuning.ROCK_DAMAGE;

    public ColossusRock(EntityType<? extends ColossusRock> type, Level level)
    {
        super(type, level);
    }

    /** A rock thrown by {@code owner} from {@code from}, hitting this hard; molten in the Lava Core stage. */
    public static ColossusRock thrown(Level level, LivingEntity owner, Vec3 from, float damage, boolean molten)
    {
        ColossusRock rock = new ColossusRock(ModEntities.COLOSSUS_ROCK.get(), level);
        rock.setOwner(owner);
        rock.setPos(from.x, from.y, from.z);
        rock.damage = damage;
        rock.entityData.set(DATA_MOLTEN, molten);
        return rock;
    }

    @Override
    protected void defineSynchedData()
    {
        this.entityData.define(DATA_MOLTEN, false);
    }

    /** True in the Lava Core stage: drawn as a lump of magma, and it sets you on fire. */
    public boolean isMolten()
    {
        return this.entityData.get(DATA_MOLTEN);
    }

    /** The block it looks like (and shatters into). */
    public BlockState look()
    {
        return this.isMolten() ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState();
    }

    @Override
    protected double gravity()
    {
        return ColossusTuning.ROCK_GRAVITY;
    }

    @Override
    protected void flyingEffects()
    {
        if (this.level().isClientSide && this.isMolten() && this.random.nextInt(2) == 0)
        {
            this.level().addParticle(ParticleTypes.FLAME, this.getX(), this.getY() + 0.3D, this.getZ(), 0.0D, 0.02D, 0.0D);
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
            Vec3 push = this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize().scale(ColossusTuning.ROCK_KNOCKBACK);
            target.push(push.x, 0.25D, push.z);
            target.hurtMarked = true;
            if (this.isMolten())
            {
                target.setSecondsOnFire(ColossusTuning.CORE_FIRE_SECONDS);
            }
        }
        this.shatter();
    }

    @Override
    protected void onHitBlock(BlockHitResult hit)
    {
        if (!this.level().isClientSide)
        {
            this.shatter();
        }
    }

    /** Bursts into dust where it is, with a crack of stone, and is gone. Nothing is placed or broken. */
    private void shatter()
    {
        if (this.level() instanceof ServerLevel level)
        {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, this.look()), this.getX(), this.getY() + 0.3D,
                    this.getZ(), SHATTER_PARTICLES, 0.35D, 0.35D, 0.35D, 0.15D);
            if (this.isMolten())
            {
                level.sendParticles(ParticleTypes.LAVA, this.getX(), this.getY() + 0.3D, this.getZ(), 4, 0.3D, 0.2D, 0.3D, 0.0D);
            }
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.COLOSSUS_ROCK_SHATTER.get(),
                    SoundSource.HOSTILE, 1.5F, 0.9F + this.random.nextFloat() * 0.2F);
        }
        this.discard();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putFloat(TAG_DAMAGE, this.damage);
        tag.putBoolean(TAG_MOLTEN, this.isMolten());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.contains(TAG_DAMAGE))
        {
            this.damage = tag.getFloat(TAG_DAMAGE);
        }
        this.entityData.set(DATA_MOLTEN, tag.getBoolean(TAG_MOLTEN));
    }
}
