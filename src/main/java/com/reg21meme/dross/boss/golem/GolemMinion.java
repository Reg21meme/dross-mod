package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
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
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.UUID;

/**
 * A small golem (about a block tall) that a Cinder Colossus summons in its fight: it climbs out of the ground
 * ({@link #EMERGE_ANIM}) and goes for the players. Two kinds: {@link StoneGolem} (the Cracking stage) and
 * {@link LavaGolem} (the Lava Core stage, which blows up). Their models and animations are made by tools/golems
 * (designs/minions.py); their numbers are in {@link ColossusTuning}.
 * <p>
 * A minion belongs to the Colossus that summoned it (its UUID is saved as {@code Master}). It crumbles away when that
 * Colossus dies or is gone, and never despawns on its own.
 * <p>
 * A <b>wild</b> golem has no Colossus ({@link #isWild()}): one that spawned by itself in the Cinder Wastes (the spawn
 * rules are in {@link WildGolemSpawns}), or came from a spawn egg (Dross creative tab) or {@code /summon}. It never
 * crumbles, wanders about when it has nothing to fight, and despawns like any monster when no player is near. A wild
 * lava golem's fuse waits for a player (see {@link LavaGolem}).
 * <p>
 * Both kinds ignore light (they belong in the lava-lit wastes), walk over magma and fire without fear (they're
 * fire-proof), never walk into lava (they'd only wade, sink and get stuck), and never drown (they're stone, like an iron
 * golem). They drop nothing and give a little experience.
 */
public abstract class GolemMinion extends Monster implements GeoEntity
{
    private static final EntityDataAccessor<Boolean> DATA_EMERGING = SynchedEntityData.defineId(GolemMinion.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ATTACK_SERIAL = SynchedEntityData.defineId(GolemMinion.class, EntityDataSerializers.INT);
    private static final String TAG_MASTER = "Master";
    private static final String TAG_EMERGE = "Emerging";
    /** Ticks a GeckoLib animation takes to blend into the next. */
    private static final int BLEND_TICKS = 3;
    /** Experience it drops. */
    private static final int XP = 2;
    /** Dust particles when it climbs out or crumbles. */
    private static final int DUST = 24;

    /**
     * The walk animation never runs slower or faster than these (times its own speed). The lava golem's stride needs
     * about 2.5 times at its speed, so the top leaves room for it (its knuckles would slide if it were clamped).
     */
    private static final double MIN_WALK_SPEEDUP = 0.6D;
    private static final double MAX_WALK_SPEEDUP = 3.0D;
    private static final RawAnimation IDLE_ANIM = RawAnimation.begin().thenLoop("animation.golem_minion.idle");
    private static final RawAnimation WALK_ANIM = RawAnimation.begin().thenLoop("animation.golem_minion.walk");
    private static final RawAnimation ATTACK_ANIM = RawAnimation.begin().thenPlayAndHold("animation.golem_minion.attack");
    private static final RawAnimation EMERGE_ANIM = RawAnimation.begin().thenPlayAndHold("animation.golem_minion.spawn");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    @Nullable
    private UUID master;
    /** Server: ticks left of climbing out of the ground. */
    private int emergeLeft;
    /** Client: the attack counter the animation last started for, and when this client saw it. */
    private int attackSeen = -1;
    private int attackSeenAt;

    protected GolemMinion(EntityType<? extends GolemMinion> type, Level level)
    {
        super(type, level);
        this.xpReward = XP;
        // Fire-proof: magma floors and flames don't scare them (the Cinder Wastes are full of magma)...
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 0.0F);
        // ...but they never walk into lava (Minecraft's, or a Colossus's pools): they'd only wade, sink and get stuck.
        this.setPathfindingMalus(BlockPathTypes.LAVA, -1.0F);
    }

    /** Its model, animations and timings (GolemDesigns.STONE_GOLEM or LAVA_GOLEM). */
    public abstract GolemDesigns.Minion design();

    /** The block its dust looks like when it climbs out or crumbles. */
    protected abstract BlockState dust();

    /** Its walking speed attribute, to speed the walk animation up to match. */
    protected abstract double walkSpeed();

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_EMERGING, false);
        this.entityData.define(DATA_ATTACK_SERIAL, 0);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, 1.0D, true));
        // Only a wild golem wanders (a summoned one stays where it climbed out until it has someone to fight).
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, ColossusTuning.WILD_GOLEM_WANDER_SPEED)
        {
            @Override
            public boolean canUse()
            {
                return GolemMinion.this.isWild() && super.canUse();
            }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, CinderColossus.class, GolemMinion.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ---------------------------------------------------------------- summoning

    /**
     * Raises a minion of this type for {@code colossus} at {@code feet} (on the ground), facing {@code yaw}: it climbs
     * out of the ground and goes for the Colossus's target.
     *
     * @return the minion, already in the world, or null if it couldn't be made
     */
    @Nullable
    public static <T extends GolemMinion> T summon(EntityType<T> type, ServerLevel level, CinderColossus colossus, Vec3 feet,
                                                   float yaw)
    {
        T created = type.create(level);
        if (created == null)
        {
            return null;
        }
        GolemMinion minion = created;
        minion.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        minion.setYHeadRot(yaw);
        minion.setYBodyRot(yaw);
        ForgeEventFactory.onFinalizeSpawn(minion, level, level.getCurrentDifficultyAt(BlockPos.containing(feet)),
                MobSpawnType.MOB_SUMMONED, null, null);
        minion.master = colossus.getUUID();
        minion.setPersistenceRequired(); // (a summoned minion never despawns: it crumbles with its Colossus instead)
        minion.emergeLeft = minion.design().spawnTicks() + BLEND_TICKS;
        minion.entityData.set(DATA_EMERGING, true);
        LivingEntity target = colossus.getTarget();
        if (target != null && target.isAlive())
        {
            minion.setTarget(target);
        }
        level.addFreshEntity(minion);
        minion.puff(level, true);
        return created;
    }

    /** True if this minion was summoned by {@code colossus}. */
    public boolean belongsTo(CinderColossus colossus)
    {
        return colossus.getUUID().equals(this.master);
    }

    /**
     * True if no Colossus summoned it: it spawned by itself in the Cinder Wastes, or came from a spawn egg or
     * {@code /summon}. Server side only (the client doesn't know its Colossus).
     */
    public boolean isWild()
    {
        return this.master == null;
    }

    /** True while it climbs out of the ground (it doesn't move or attack yet). */
    public boolean isEmerging()
    {
        return this.entityData.get(DATA_EMERGING);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            return;
        }
        if (this.emergeLeft > 0 && --this.emergeLeft == 0)
        {
            this.entityData.set(DATA_EMERGING, false);
        }
        // (A wild golem has no Colossus: it stays.)
        if (this.master != null && this.tickCount % ColossusTuning.MINION_MASTER_CHECK == 0
                && this.tickCount > 2 * ColossusTuning.MINION_MASTER_CHECK && this.isAlive() && !this.masterAlive())
        {
            this.crumble();
        }
    }

    /** True if the Colossus that summoned it is still alive (and loaded, and near). */
    private boolean masterAlive()
    {
        if (this.master == null || !(this.level() instanceof ServerLevel level))
        {
            return false;
        }
        Entity entity = level.getEntity(this.master);
        return entity instanceof CinderColossus colossus && colossus.isAlive()
                && colossus.distanceToSqr(this) < ColossusTuning.MINION_RANGE * ColossusTuning.MINION_RANGE;
    }

    /** Falls apart into dust (its Colossus died or is gone): no drops, no experience. */
    public void crumble()
    {
        if (this.level() instanceof ServerLevel level)
        {
            this.puff(level, false);
        }
        this.discard();
    }

    /** Dust and a crunch of stone, as it climbs out of the ground (emerging) or crumbles. */
    private void puff(ServerLevel level, boolean emerging)
    {
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, emerging ? level.getBlockState(this.blockPosition().below())
                : this.dust()), this.getX(), this.getY() + (emerging ? 0.1D : 0.5D), this.getZ(), DUST, 0.35D,
                emerging ? 0.1D : 0.4D, 0.35D, 0.1D);
        level.playSound(null, this.getX(), this.getY(), this.getZ(), emerging ? ModSounds.GOLEM_EMERGE.get()
                : ModSounds.STONE_GOLEM_DEATH.get(), SoundSource.HOSTILE, 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
    }

    /** It can't move or fight while it climbs out of the ground. */
    @Override
    protected boolean isImmobile()
    {
        return super.isImmobile() || this.isEmerging();
    }

    @Override
    public boolean doHurtTarget(Entity target)
    {
        this.entityData.set(DATA_ATTACK_SERIAL, this.entityData.get(DATA_ATTACK_SERIAL) + 1);
        return super.doHurtTarget(target);
    }

    /** A wild golem despawns like any monster when no player is near; a summoned one never does. */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return this.isWild();
    }

    /**
     * Light doesn't matter to them: a monster normally only spawns (and likes to walk) where it's dark, but these fire
     * golems belong in the lava-lit Cinder Wastes. (Natural spawning asks this through {@code checkSpawnRules}.)
     */
    @Override
    public float getWalkTargetValue(BlockPos pos, LevelReader level)
    {
        return 0.0F;
    }

    /** They're stone and lava: they never run out of air (like an iron golem). */
    @Override
    protected int decreaseAirSupply(int air)
    {
        return air;
    }

    @Override
    public boolean canBeLeashed(Player player)
    {
        return false;
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        this.playSound(ModSounds.STONE_GOLEM_STEP.get(), 0.6F, 1.0F);
    }

    @Override
    protected abstract SoundEvent getAmbientSound();

    @Override
    protected abstract SoundEvent getHurtSound(DamageSource source);

    @Override
    protected abstract SoundEvent getDeathSound();

    // ---------------------------------------------------------------- animation (GeckoLib)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        AnimationController<GolemMinion> main = new AnimationController<>(this, "main", BLEND_TICKS, this::animate);
        main.setAnimationSpeedHandler(minion -> minion.animationSpeed());
        controllers.add(main);
    }

    private PlayState animate(AnimationState<GolemMinion> state)
    {
        if (this.isEmerging())
        {
            return state.setAndContinue(EMERGE_ANIM);
        }
        int serial = this.entityData.get(DATA_ATTACK_SERIAL);
        if (serial != this.attackSeen)
        {
            boolean first = this.attackSeen < 0;
            this.attackSeen = serial;
            if (!first)
            {
                this.attackSeenAt = this.tickCount;
                state.getController().forceAnimationReset();
            }
        }
        if (this.attackSeenAt > 0 && this.tickCount - this.attackSeenAt < this.design().attackTicks() + BLEND_TICKS)
        {
            return state.setAndContinue(ATTACK_ANIM);
        }
        return state.setAndContinue(this.walkAnimation.speed() > 0.05F ? WALK_ANIM : IDLE_ANIM);
    }

    /** The walk animation runs faster when the minion walks faster than the animation's own stride. */
    private double animationSpeed()
    {
        if (this.isEmerging() || this.walkAnimation.speed() <= 0.05F)
        {
            return 1.0D;
        }
        GolemDesigns.Minion d = this.design();
        double natural = d.walkBlocks() / d.walkTicks();
        return Math.max(MIN_WALK_SPEEDUP, Math.min(MAX_WALK_SPEEDUP, CinderColossus.blocksPerTick(this.walkSpeed()) / natural));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (this.master != null)
        {
            tag.putUUID(TAG_MASTER, this.master);
        }
        tag.putInt(TAG_EMERGE, this.emergeLeft);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.master = tag.hasUUID(TAG_MASTER) ? tag.getUUID(TAG_MASTER) : null;
        this.emergeLeft = tag.getInt(TAG_EMERGE);
        this.entityData.set(DATA_EMERGING, this.emergeLeft > 0);
    }
}
