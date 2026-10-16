package com.reg21meme.dross.boss.ratking;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A cloud of poison where one of the Rat King's {@link PlagueBall}s burst ({@code dross:poison_cloud}): our own small
 * entity, not Minecraft's area effect cloud. It's {@link RatKingTuning#CLOUD_RADIUS} blocks wide, lasts
 * {@link RatKingTuning#CLOUD_TICKS} ticks and shrinks as it fades ({@link RatKingTuning#CLOUD_END_SHARE} of its size at
 * the end), shown as drifting brownish-green dust. Every {@link RatKingTuning#CLOUD_APPLY_EVERY} ticks (the first at
 * once) it poisons every living thing inside it, except the Rat King and rats, with Poison I for
 * {@link RatKingTuning#POISON_TICKS} ticks: the poison is always short. It only tops the poison up when it has (almost)
 * run out ({@link RatKingTuning#CLOUD_TOPUP_BELOW}), so standing in it is a steady Poison I. (Undead can't be
 * poisoned, as usual.)
 */
public class PoisonCloud extends Entity
{
    private static final EntityDataAccessor<Float> DATA_RADIUS = SynchedEntityData.defineId(PoisonCloud.class, EntityDataSerializers.FLOAT);
    private static final String TAG_AGE = "Age";
    private static final String TAG_OWNER = "Owner";
    /** Dust particles each tick for each square block of the cloud, at least one. */
    private static final float PARTICLES_PER_AREA = 0.35F;
    private static final float PARTICLE_SIZE = 1.6F;
    /** How high the dust drifts (blocks above its middle), and how fast. */
    private static final double PARTICLE_HEIGHT = 1.2D;
    private static final double PARTICLE_RISE = 0.01D;

    private int age;
    /** The Rat King whose ball made it (for whose poison it is). */
    @Nullable
    private UUID owner;

    public PoisonCloud(EntityType<? extends PoisonCloud> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;
    }

    /** A new cloud, its middle on the ground at {@code at} (add it to the world). */
    public static PoisonCloud at(Level level, Vec3 at, @Nullable LivingEntity owner)
    {
        PoisonCloud cloud = new PoisonCloud(ModEntities.POISON_CLOUD.get(), level);
        cloud.setPos(at.x, at.y, at.z);
        cloud.owner = owner != null ? owner.getUUID() : null;
        cloud.setRadius(RatKingTuning.CLOUD_RADIUS);
        return cloud;
    }

    @Override
    protected void defineSynchedData()
    {
        this.entityData.define(DATA_RADIUS, RatKingTuning.CLOUD_RADIUS);
    }

    /** How wide it is now (blocks, from its middle). */
    public float getRadius()
    {
        return this.entityData.get(DATA_RADIUS);
    }

    private void setRadius(float radius)
    {
        this.entityData.set(DATA_RADIUS, radius);
    }

    /** Its radius this far through its life: full at first, shrinking (eased) to its end share. */
    static float radiusAt(int age)
    {
        float k = Mth.clamp(age / (float) RatKingTuning.CLOUD_TICKS, 0.0F, 1.0F);
        float eased = k * k * (3.0F - 2.0F * k);
        return RatKingTuning.CLOUD_RADIUS * Mth.lerp(eased, 1.0F, RatKingTuning.CLOUD_END_SHARE);
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.particles();
            return;
        }
        this.setRadius(radiusAt(this.age));
        if (this.age % RatKingTuning.CLOUD_APPLY_EVERY == 0)
        {
            this.poison();
        }
        this.age++;
        if (this.age >= RatKingTuning.CLOUD_TICKS)
        {
            this.discard();
        }
    }

    /** Poisons every living thing inside it (not the Rat King, not rats, not spectators). */
    private void poison()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        float r = this.getRadius();
        AABB area = new AABB(this.getX() - r, this.getY() - RatKingTuning.CLOUD_BELOW, this.getZ() - r,
                this.getX() + r, this.getY() + RatKingTuning.CLOUD_ABOVE, this.getZ() + r);
        Entity source = this.owner != null ? level.getEntity(this.owner) : null;
        // (Also a Rat King or rat that the Rage Wand has made an enemy of whoever cast it.)
        for (LivingEntity living : level.getEntitiesOfClass(LivingEntity.class, area,
                e -> canPoison(e) || e.isAlive() && !e.isSpectator() && Rage.areEnemies(source, e)))
        {
            double dx = living.getX() - this.getX();
            double dz = living.getZ() - this.getZ();
            if (dx * dx + dz * dz <= r * r && needsPoison(living))
            {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, RatKingTuning.POISON_TICKS, 0), source);
            }
        }
    }

    /**
     * True if the cloud should give (or top up) this one's poison: it has none, or less than
     * {@link RatKingTuning#CLOUD_TOPUP_BELOW} ticks of it left. Minecraft's poison hurts on the tick it's given (and
     * then every 25 ticks), so topping it up every time the cloud checks would hurt every half second; this way
     * standing in the cloud is a steady Poison I (one point of damage every 1.25 seconds).
     */
    static boolean needsPoison(LivingEntity living)
    {
        MobEffectInstance poison = living.getEffect(MobEffects.POISON);
        return poison == null || poison.getDuration() < RatKingTuning.CLOUD_TOPUP_BELOW;
    }

    /** Living things the cloud poisons: not the Rat King, not rats, not spectators. */
    static boolean canPoison(LivingEntity living)
    {
        return living.isAlive() && !(living instanceof RatKing) && !(living instanceof Rat) && !living.isSpectator();
    }

    /** Client: brownish-green dust drifting about inside it. */
    private void particles()
    {
        RandomSource random = this.random;
        float r = this.getRadius();
        int count = Math.max(1, Math.round(PARTICLES_PER_AREA * (float) Math.PI * r * r));
        for (int i = 0; i < count; i++)
        {
            double a = random.nextDouble() * 2.0D * Math.PI;
            double d = Math.sqrt(random.nextDouble()) * r;
            int color = random.nextInt(3) == 0 ? DrossColors.POISON_CLOUD_DARK : DrossColors.POISON_CLOUD;
            this.level().addParticle(new DustParticleOptions(DrossColors.vector(color), PARTICLE_SIZE),
                    this.getX() + Math.cos(a) * d, this.getY() + random.nextDouble() * PARTICLE_HEIGHT,
                    this.getZ() + Math.sin(a) * d, 0.0D, PARTICLE_RISE, 0.0D);
        }
    }

    @Override
    public boolean isPickable()
    {
        return false;
    }

    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public PushReaction getPistonPushReaction()
    {
        return PushReaction.IGNORE;
    }

    @Override
    public boolean isIgnoringBlockTriggers()
    {
        return true;
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
        this.age = tag.getInt(TAG_AGE);
        this.owner = tag.hasUUID(TAG_OWNER) ? tag.getUUID(TAG_OWNER) : null;
        this.setRadius(radiusAt(this.age));
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
        tag.putInt(TAG_AGE, this.age);
        if (this.owner != null)
        {
            tag.putUUID(TAG_OWNER, this.owner);
        }
    }
}
