package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * {@code dross:lava_golem}: the small golem of lava a Cinder Colossus summons in the Lava Core stage. Its punches set
 * you on fire. If nobody kills it, it swells, hisses and blows up {@link ColossusTuning#LAVA_GOLEM_FUSE_TICKS} ticks
 * after it climbs out of the ground: everything near it is hurt and set on fire, but the blast never breaks a block.
 * Killed in time, it just fizzles out. See {@link GolemMinion}.
 * <p>
 * A <b>wild</b> one (spawned by itself near lava in the Cinder Wastes, or from a spawn egg or {@code /summon}) has no
 * timer, or it would blow up alone in an empty waste: its fuse is lit, creeper-like, when its target comes within
 * {@link ColossusTuning#WILD_LAVA_GOLEM_FUSE_RANGE} blocks and it can see it. Then it swells and hisses for
 * {@link ColossusTuning#LAVA_GOLEM_SWELL_TICKS} ticks and blows up the same way (no blocks broken). Once lit, it can't be
 * put out: run, or kill it.
 */
public class LavaGolem extends GolemMinion
{
    private static final EntityDataAccessor<Integer> DATA_FUSE = SynchedEntityData.defineId(LavaGolem.class, EntityDataSerializers.INT);
    private static final String TAG_FUSE = "Fuse";
    private static final String TAG_LIT = "FuseLit";
    /** Particles in its blast. */
    private static final int BLAST_FLAMES = 40;
    /** How hard its blast throws things (at the middle). */
    private static final double BLAST_KNOCKBACK = 0.8D;

    /** A wild lava golem's fuse has been lit (a summoned one's always burns). Server side. */
    private boolean lit;

    public LavaGolem(EntityType<? extends LavaGolem> type, Level level)
    {
        super(type, level);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, ColossusTuning.LAVA_GOLEM_HEALTH)
                .add(Attributes.ATTACK_DAMAGE, ColossusTuning.LAVA_GOLEM_DAMAGE)
                .add(Attributes.MOVEMENT_SPEED, ColossusTuning.LAVA_GOLEM_SPEED)
                .add(Attributes.FOLLOW_RANGE, 32.0D);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_FUSE, ColossusTuning.LAVA_GOLEM_FUSE_TICKS);
    }

    @Override
    public GolemDesigns.Minion design()
    {
        return GolemDesigns.LAVA_GOLEM;
    }

    @Override
    protected BlockState dust()
    {
        return Blocks.MAGMA_BLOCK.defaultBlockState();
    }

    @Override
    protected double walkSpeed()
    {
        return ColossusTuning.LAVA_GOLEM_SPEED;
    }

    /** Ticks left before it blows up (it only counts down while the fuse is lit). */
    public int fuse()
    {
        return this.entityData.get(DATA_FUSE);
    }

    /**
     * True if its fuse is burning: a summoned one's always is (it blows up on its timer); a wild one's once a player
     * came close. Server side.
     */
    public boolean isFuseLit()
    {
        return this.lit || !this.isWild();
    }

    /** How far it has swollen before blowing up: 0 normally, rising to 1 over its last few seconds. */
    public float swell(float partialTick)
    {
        float left = this.fuse() - partialTick;
        return Math.max(0.0F, Math.min(1.0F, 1.0F - left / ColossusTuning.LAVA_GOLEM_SWELL_TICKS));
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            if (this.random.nextInt(4) == 0)
            {
                this.level().addParticle(ParticleTypes.FLAME, this.getRandomX(0.5D), this.getRandomY(), this.getRandomZ(0.5D),
                        0.0D, 0.02D, 0.0D);
            }
            return;
        }
        if (!this.isAlive() || this.isEmerging())
        {
            return;
        }
        if (!this.isFuseLit())
        {
            if (!this.targetInReach())
            {
                return; // a wild one waits for a player
            }
            // Lit: from here on it's the end of a summoned one's fuse (the hiss and swell, then the blast).
            this.lit = true;
            this.entityData.set(DATA_FUSE, ColossusTuning.LAVA_GOLEM_SWELL_TICKS + 1);
        }
        int fuse = this.fuse() - 1;
        this.entityData.set(DATA_FUSE, fuse);
        if (fuse == ColossusTuning.LAVA_GOLEM_SWELL_TICKS)
        {
            this.playSound(ModSounds.LAVA_GOLEM_FUSE.get(), 1.0F, 0.9F);
        }
        if (fuse <= 0)
        {
            this.blowUp();
        }
    }

    /** True if its target is alive, close enough to light a wild one's fuse and in sight. */
    private boolean targetInReach()
    {
        LivingEntity target = this.getTarget();
        double reach = ColossusTuning.WILD_LAVA_GOLEM_FUSE_RANGE;
        return target != null && target.isAlive() && this.distanceToSqr(target) <= reach * reach
                && this.getSensing().hasLineOfSight(target);
    }

    /**
     * Its blast: everything within {@link ColossusTuning#LAVA_GOLEM_BLAST_RADIUS} blocks (most at the middle) is hurt,
     * knocked back and set on fire. No blocks are broken: it isn't a Minecraft explosion, just its damage and looks.
     * Its own Colossus and the other minions aren't hurt.
     */
    private void blowUp()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        Vec3 at = this.position().add(0.0D, 0.5D, 0.0D);
        double radius = ColossusTuning.LAVA_GOLEM_BLAST_RADIUS;
        DamageSource source = this.damageSources().explosion(this, this);
        for (LivingEntity victim : level.getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(radius),
                e -> e != this && e.isAlive() && !(e instanceof CinderColossus) && !(e instanceof GolemMinion)))
        {
            double dist = victim.position().add(0.0D, victim.getBbHeight() / 2.0D, 0.0D).distanceTo(at);
            if (dist > radius)
            {
                continue;
            }
            double share = 1.0D - dist / radius;
            if (victim.hurt(source, (float) (ColossusTuning.LAVA_GOLEM_BLAST_DAMAGE * (0.35D + 0.65D * share))))
            {
                victim.setSecondsOnFire(ColossusTuning.LAVA_GOLEM_BLAST_FIRE_SECONDS);
            }
            Vec3 away = victim.position().subtract(this.position()).multiply(1.0D, 0.0D, 1.0D);
            away = away.lengthSqr() < 1.0E-4D ? new Vec3(0.0D, 0.0D, 0.0D) : away.normalize();
            double kb = BLAST_KNOCKBACK * share * (1.0D - victim.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE));
            victim.push(away.x * kb, 0.3D * share, away.z * kb);
            victim.hurtMarked = true;
        }
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y, at.z, 2, 0.4D, 0.4D, 0.4D, 0.0D);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y, at.z, BLAST_FLAMES, 0.8D, 0.6D, 0.8D, 0.08D);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y, at.z, 10, 0.6D, 0.4D, 0.6D, 0.0D);
        level.playSound(null, at.x, at.y, at.z, ModSounds.LAVA_GOLEM_EXPLODE.get(), SoundSource.HOSTILE, 2.0F,
                0.9F + this.random.nextFloat() * 0.2F);
        this.discard();
    }

    /** Its punches set you on fire. */
    @Override
    public boolean doHurtTarget(Entity target)
    {
        boolean hit = super.doHurtTarget(target);
        if (hit)
        {
            target.setSecondsOnFire(ColossusTuning.LAVA_GOLEM_FIRE_SECONDS);
        }
        return hit;
    }

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.LAVA_GOLEM_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.LAVA_GOLEM_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.LAVA_GOLEM_DEATH.get();
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_FUSE, this.fuse());
        tag.putBoolean(TAG_LIT, this.lit);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.lit = tag.getBoolean(TAG_LIT);
        if (tag.contains(TAG_FUSE))
        {
            this.entityData.set(DATA_FUSE, tag.getInt(TAG_FUSE));
        }
        if (!this.isFuseLit())
        {
            // A wild one waiting for a player has its whole fuse (one from a spawn egg saved before wild lava golems
            // waited may have been part-way down it).
            this.entityData.set(DATA_FUSE, ColossusTuning.LAVA_GOLEM_FUSE_TICKS);
        }
    }
}
