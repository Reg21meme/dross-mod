package com.reg21meme.dross.boss.giant;

import com.reg21meme.dross.boss.golem.BossProjectile;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The boulder a Silverwood Giant hurls ({@code dross:giant_boulder}; QUEST_PLAN.md: "like the Odyssey's giants, it
 * hurls a boulder, but only at someone it's angry with"): a mossy lump of Cragstone on an arc, aimed by the giant
 * ({@link #aim}); it hurts and knocks back whoever it hits ({@link GiantTuning#BOULDER_DAMAGE}) and shatters into dust
 * where it lands. It never places a block and never breaks one, and it never hits a giant (unless the Rage Wand has set
 * one on its thrower).
 */
public class GiantBoulder extends BossProjectile
{
    /** A showcase giant's: it flies and shatters the same, but passes through everything living and hurts nobody. */
    private boolean harmless;

    public GiantBoulder(EntityType<? extends GiantBoulder> type, Level level)
    {
        super(type, level);
    }

    /** A boulder hurled by {@code giant} from {@code from} (then {@link #aim} it). */
    public static GiantBoulder thrown(Level level, LivingEntity giant, Vec3 from)
    {
        GiantBoulder boulder = new GiantBoulder(ModEntities.GIANT_BOULDER.get(), level);
        boulder.setOwner(giant);
        boulder.setPos(from.x, from.y, from.z);
        return boulder;
    }

    /** Makes it the showcase's harmless one. */
    public GiantBoulder harmless()
    {
        this.harmless = true;
        return this;
    }

    /** The block it looks like (and shatters into). */
    public static BlockState look()
    {
        return ModBlocks.CRAGSTONE.get().defaultBlockState();
    }

    @Override
    protected double gravity()
    {
        return GiantTuning.BOULDER_GRAVITY;
    }

    @Override
    protected boolean canHitEntity(Entity entity)
    {
        if (this.harmless || entity instanceof SilverwoodGiant && !Rage.areEnemies(this.getOwner(), entity))
        {
            return false;
        }
        return super.canHitEntity(entity);
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
        if (target.hurt(source, GiantTuning.BOULDER_DAMAGE))
        {
            Vec3 push = this.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize().scale(GiantTuning.BOULDER_KNOCKBACK);
            target.push(push.x, 0.3D, push.z);
            target.hurtMarked = true;
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

    /** Bursts into dust where it is, with a crash of rock, and is gone. Nothing is placed or broken. */
    private void shatter()
    {
        if (this.level() instanceof ServerLevel level)
        {
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, look()), this.getX(), this.getY() + 0.4D,
                    this.getZ(), GiantTuning.BOULDER_DUST, 0.45D, 0.45D, 0.45D, 0.15D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.SILVERWOOD_GIANT_BOULDER_HIT.get(),
                    SoundSource.HOSTILE, GiantTuning.BLOW_VOLUME, 0.9F + this.random.nextFloat() * 0.2F);
        }
        this.discard();
    }
}
