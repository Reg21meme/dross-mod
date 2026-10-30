package com.reg21meme.dross.boss.emberdactyl;

import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.registry.ModEntities;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.util.GeckoLibUtil;

import static com.reg21meme.dross.boss.emberdactyl.EmberTuning.*;

/**
 * A fire tornado the Emberdactyl's wings whip up (the servant candidate Firestorm's look, spinning; the user's plan). It
 * runs along the ground after its target for {@link EmberTuning#TORNADO_LIFE} ticks: anything within
 * {@link EmberTuning#TORNADO_PULL_RANGE} is pulled in, burnt ({@link EmberTuning#TORNADO_DAMAGE} a second and set on fire)
 * and, in its core, thrown up into the sky to fall back down (the user: "the tornado also makes you go into the sky and
 * fall down"). Its size is synced (the young's are smaller). It never hurts its maker or its kin.
 */
public class FireTornado extends Entity implements GeoEntity
{
    private static final EntityDataAccessor<Float> DATA_SIZE = SynchedEntityData.defineId(FireTornado.class, EntityDataSerializers.FLOAT);
    /** Its look at size 1 (the adult's): the Firestorm statue's model shrunk to this. */
    public static final float MODEL_SCALE = 0.6F;
    /** Its hitbox at size 1. */
    private static final float WIDTH = 3.0F;
    private static final float HEIGHT = 7.0F;
    /** How high over it it catches a raged Emberdactyl of its owner's enemies (blocks): they fight in the air. */
    private static final double SKY_REACH = 30.0D;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID ownerId;
    @Nullable
    private UUID targetId;
    private Vec3 heading = new Vec3(1.0D, 0.0D, 0.0D);
    private int life = TORNADO_LIFE;
    /** When each thing it holds was last hurt and last thrown up (ticks). */
    private final Map<UUID, Integer> lastHurt = new HashMap<>();
    private final Map<UUID, Integer> lastLift = new HashMap<>();

    public FireTornado(EntityType<? extends FireTornado> type, Level level)
    {
        super(type, level);
        this.noPhysics = true;
    }

    /** Sends one out from {@code from} along {@code heading}, after {@code target}. */
    public static void spawn(ServerLevel level, LivingEntity owner, Vec3 from, Vec3 heading, @Nullable LivingEntity target, float size)
    {
        FireTornado t = ModEntities.FIRE_TORNADO.get().create(level);
        if (t == null)
        {
            return;
        }
        t.setSize(size);
        t.moveTo(from.x, from.y, from.z, 0.0F, 0.0F);
        t.ownerId = owner.getUUID();
        t.targetId = target == null ? null : target.getUUID();
        t.heading = heading.normalize();
        level.addFreshEntity(t);
        level.playSound(null, from.x, from.y, from.z, SoundEvents.FIRECHARGE_USE, SoundSource.HOSTILE, 3.0F, 0.5F);
    }

    @Override
    protected void defineSynchedData()
    {
        this.entityData.define(DATA_SIZE, 1.0F);
    }

    public float getSize()
    {
        return this.entityData.get(DATA_SIZE);
    }

    private void setSize(float size)
    {
        this.entityData.set(DATA_SIZE, size);
        this.refreshDimensions();
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);
        if (DATA_SIZE.equals(key))
        {
            this.refreshDimensions();
        }
    }

    @Override
    public EntityDimensions getDimensions(Pose pose)
    {
        return EntityDimensions.scalable(WIDTH * this.getSize(), HEIGHT * this.getSize());
    }

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            return;
        }
        ServerLevel level = (ServerLevel) this.level();
        if (--this.life <= 0)
        {
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 2.0D, this.getZ(), 20, 1.0D, 2.0D, 1.0D, 0.05D);
            this.discard();
            return;
        }
        // drifting after its target, along the ground
        Entity target = this.targetId == null ? null : level.getEntity(this.targetId);
        if (target != null && target.isAlive())
        {
            Vec3 to = new Vec3(target.getX() - this.getX(), 0.0D, target.getZ() - this.getZ());
            if (to.lengthSqr() > 0.01D)
            {
                this.heading = this.heading.scale(0.92D).add(to.normalize().scale(0.08D)).normalize();
            }
        }
        double speed = TORNADO_SPEED * (this.getSize() < 1.0F ? 1.5D : 1.0D);
        Vec3 step = this.heading.scale(speed);
        this.move(MoverType.SELF, step);
        double ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, Mth.floor(this.getX()), Mth.floor(this.getZ()));
        this.setPos(this.getX(), ground, this.getZ());
        // its fire
        float size = this.getSize();
        if (this.tickCount % 2 == 0)
        {
            level.sendParticles(ParticleTypes.FLAME, this.getX(), this.getY() + HEIGHT * size * 0.5D, this.getZ(), 8,
                    WIDTH * size * 0.4D, HEIGHT * size * 0.4D, WIDTH * size * 0.4D, 0.05D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + HEIGHT * size, this.getZ(), 2, 0.6D, 0.3D, 0.6D, 0.02D);
        }
        if (this.tickCount % 20 == 0)
        {
            level.playSound(null, this.getX(), this.getY(), this.getZ(), SoundEvents.BLAZE_BURN, SoundSource.HOSTILE, 2.0F, 0.5F);
        }
        // what it catches
        double pull = TORNADO_PULL_RANGE * size;
        Entity owner = this.ownerId == null ? null : level.getEntity(this.ownerId);
        // (a raged Emberdactyl of another team is caught however high it flies over it: the fire reaches up for it)
        AABB reach = this.getBoundingBox().inflate(pull, 2.0D, pull).expandTowards(0.0D, SKY_REACH, 0.0D);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, reach,
                e -> e.isAlive() && !Rage.areTeammates(owner, e)
                        && (Rage.areEnemies(owner, e) || !(e instanceof EmberBoss) && !(e instanceof EmberEgg)
                        && e.getY() <= this.getY() + HEIGHT * size + 2.0D
                        && !(e instanceof Player p && (p.isCreative() || p.isSpectator())))))
        {
            Vec3 in = new Vec3(this.getX() - e.getX(), 0.0D, this.getZ() - e.getZ());
            double d = in.length();
            if (d > pull)
            {
                continue;
            }
            if (d > 0.3D)
            {
                Vec3 swirl = new Vec3(-in.z, 0.0D, in.x).normalize().scale(TORNADO_PULL * 0.6D);
                e.setDeltaMovement(e.getDeltaMovement().add(in.normalize().scale(TORNADO_PULL)).add(swirl));
                e.hurtMarked = true;
            }
            int now = this.tickCount;
            if (now - this.lastHurt.getOrDefault(e.getUUID(), -1000) >= TORNADO_HURT_EVERY)
            {
                this.lastHurt.put(e.getUUID(), now);
                if (e.hurt(this.damageSources().mobAttack(owner instanceof LivingEntity living ? living : null), TORNADO_DAMAGE))
                {
                    e.setSecondsOnFire(TORNADO_FIRE_SECONDS);
                }
            }
            if (d <= TORNADO_CORE * Math.max(0.7D, size) && now - this.lastLift.getOrDefault(e.getUUID(), -1000) >= TORNADO_LIFT_EVERY)
            {
                this.lastLift.put(e.getUUID(), now);
                e.setDeltaMovement(e.getDeltaMovement().x * 0.3D, TORNADO_LIFT * Math.max(0.75D, size), e.getDeltaMovement().z * 0.3D);
                e.hurtMarked = true;
                e.hasImpulse = true;
            }
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
    public boolean shouldBeSaved()
    {
        return false;
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(4.0D * this.getSize(), 3.0D * this.getSize(), 4.0D * this.getSize());
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag)
    {
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag)
    {
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
