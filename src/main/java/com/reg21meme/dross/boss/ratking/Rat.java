package com.reg21meme.dross.boss.ratking;

import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
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
 * The Rat King's rats (CLAUDE.md, "The plan", step 9): two kinds, one class. {@code dross:rat} is a regular rat (the
 * Plague Rat); {@code dross:enraged_rat} is the enraged breed (the Plague Ravager), bigger and scarier, with glowing red
 * eyes. Their looks are {@link RatDesigns} (made by {@code tools/ratking/}): two animations each, idle and run
 * ({@code animation.rat.idle|run}). Their numbers are in {@link RatKingTuning}.
 * <ul>
 *   <li><b>Summoned</b> ({@link #summon}: a Rat King calls them out of the ground while he's burrowed): they belong to
 *       him (his UUID is saved as {@code Master}) and attack players (bites). They never despawn while he lives, and
 *       crumble into dust when he dies or is gone.</li>
 *   <li><b>Wild</b> (a spawn egg, {@code /summon}): a regular rat is harmless wildlife (it wanders about and flees when
 *       hurt, never attacks); an enraged rat is hostile and attacks players.</li>
 * </ul>
 * Every bite of an enraged rat poisons (briefly). No drops; a little experience.
 * <p>
 * <b>Showcase rats</b> ({@link #spawnShowcase}) stand frozen like the showcase Rat Kings (no AI, silent, can't be pushed
 * or hurt except by {@code /kill}, never despawn); they run on the spot when told to. Right-clicking one switches it
 * between idle and running. Their sounds are original, made by {@code tools/ratking/sounds.py}.
 */
public class Rat extends Monster implements GeoEntity
{
    private static final EntityDataAccessor<Integer> DATA_DESIGN = SynchedEntityData.defineId(Rat.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_RUNNING = SynchedEntityData.defineId(Rat.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SHOWCASE = SynchedEntityData.defineId(Rat.class, EntityDataSerializers.BOOLEAN);

    private static final String TAG_DESIGN = "Design";
    private static final String TAG_RUNNING = "Running";
    private static final String TAG_SHOWCASE = "Showcase";
    private static final String TAG_MASTER = "Master";

    /**
     * The design a rat wears (its spawn egg, {@code /summon}, a Rat King's call): each kind has one now, the user's picks
     * from round 1, R3 Plague Rat and E3 Plague Ravager (the other six were dropped).
     */
    public static final int DEFAULT_DESIGN = 1;
    /** How fast a rat wanders and chases (times its speed), and how loud its footsteps are. */
    private static final double WANDER_SPEED = 1.0D;
    private static final double CHASE_SPEED = 1.0D;
    private static final float STEP_VOLUME = 0.2F;
    /** How loud its bite and its squeak as it bursts out of the ground are. */
    private static final float BITE_VOLUME = 0.8F;
    private static final float EMERGE_VOLUME = 1.0F;
    /** Dirt thrown up as it bursts out of the ground, and dust as it crumbles. */
    private static final int EMERGE_DIRT = 14;
    private static final int CRUMBLE_DUST = 12;
    private static final float CRUMBLE_VOLUME = 0.6F;
    /** A summoned rat starts checking its king after this many ticks (it might be called before he's loaded). */
    private static final int MASTER_GRACE_TICKS = 40;

    /** Ticks GeckoLib blends idle into running and back. */
    private static final int BLEND_TICKS = 3;
    /** Room round its hitbox for the long tail when deciding whether it's on screen (blocks). */
    private static final double CULL_MARGIN = 1.5D;

    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.rat.idle");
    private static final RawAnimation RUN = RawAnimation.begin().thenLoop("animation.rat.run");

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    /** The Rat King that called it out of the ground (null: a wild rat). Server side. */
    @Nullable
    private UUID master;

    public Rat(EntityType<? extends Rat> type, Level level)
    {
        super(type, level);
        this.xpReward = RatKingTuning.RAT_XP;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, RatKingTuning.RAT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, RatKingTuning.RAT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, RatKingTuning.RAT_DAMAGE);
    }

    public static AttributeSupplier.Builder createEnragedAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, RatKingTuning.ENRAGED_RAT_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, RatKingTuning.ENRAGED_RAT_SPEED)
                .add(Attributes.ATTACK_DAMAGE, RatKingTuning.ENRAGED_RAT_DAMAGE);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_DESIGN, DEFAULT_DESIGN);
        this.entityData.define(DATA_RUNNING, false);
        this.entityData.define(DATA_SHOWCASE, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        // A wild regular rat runs off when it's hurt...
        this.goalSelector.addGoal(1, new PanicGoal(this, RatKingTuning.RAT_PANIC_SPEED)
        {
            @Override
            public boolean canUse()
            {
                return !Rat.this.bites() && super.canUse();
            }
        });
        // ...the others bite.
        this.goalSelector.addGoal(2, new MeleeAttackGoal(this, CHASE_SPEED, true)
        {
            @Override
            public boolean canUse()
            {
                return Rat.this.bites() && super.canUse();
            }
        });
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, WANDER_SPEED));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 8.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, RatKing.class, Rat.class)
        {
            @Override
            public boolean canUse()
            {
                return Rat.this.isHostile() && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true)
        {
            @Override
            public boolean canUse()
            {
                return Rat.this.isHostile() && super.canUse();
            }
        });
    }

    /** True for the enraged breed ({@code dross:enraged_rat}). */
    public boolean isEnraged()
    {
        return this.getType() == ModEntities.ENRAGED_RAT.get();
    }

    /** True if it attacks players: one a Rat King called, or an enraged rat. A wild regular rat never does. Server side. */
    public boolean isHostile()
    {
        return !this.isShowcase() && (this.isSummoned() || this.isEnraged());
    }

    /**
     * True if it bites whatever it targets: a hostile rat ({@link #isHostile}), or any rat raged by the Rage Wand (a
     * raged wild regular rat fights the other raged mobs too, but still never picks a player by itself).
     */
    private boolean bites()
    {
        return this.isHostile() || (!this.isShowcase() && Rage.isRaged(this));
    }

    /** True if a Rat King called it out of the ground (server side: the client doesn't know its king). */
    public boolean isSummoned()
    {
        return this.master != null;
    }

    /** True if this rat was called by {@code king}. */
    public boolean belongsTo(RatKing king)
    {
        return king.getUUID().equals(this.master);
    }

    public RatDesigns.Design getDesign()
    {
        return RatDesigns.byNumber(this.isEnraged(), this.entityData.get(DATA_DESIGN));
    }

    public void setDesign(int number)
    {
        this.entityData.set(DATA_DESIGN, RatDesigns.byNumber(this.isEnraged(), number).number());
    }

    public boolean isRunning()
    {
        return this.entityData.get(DATA_RUNNING);
    }

    /** Showcase control: run on the spot, or stand idle. */
    public void setRunning(boolean running)
    {
        this.entityData.set(DATA_RUNNING, running);
    }

    public boolean isShowcase()
    {
        return this.entityData.get(DATA_SHOWCASE);
    }

    // ---------------------------------------------------------------- summoned by a Rat King

    /**
     * A rat of this type bursts out of the ground for {@code king} at {@code feet} (on the ground), facing {@code yaw}:
     * dirt, a squeak and a hop, and it goes for the king's target.
     *
     * @return the rat, already in the world, or null if it couldn't be made
     */
    @Nullable
    public static Rat summon(EntityType<Rat> type, ServerLevel level, RatKing king, Vec3 feet, float yaw)
    {
        Rat rat = type.create(level);
        if (rat == null)
        {
            return null;
        }
        rat.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        rat.setYHeadRot(yaw);
        rat.setYBodyRot(yaw);
        ForgeEventFactory.onFinalizeSpawn(rat, level, level.getCurrentDifficultyAt(BlockPos.containing(feet)),
                MobSpawnType.MOB_SUMMONED, null, null);
        rat.master = king.getUUID();
        Rage.recruit(rat, king); // the Rage Wand: a raged Rat King's new rat joins his side
        rat.setDeltaMovement(0.0D, RatKingTuning.RAT_HOP, 0.0D);
        LivingEntity target = king.getTarget();
        if (target != null && target.isAlive())
        {
            rat.setTarget(target);
        }
        level.addFreshEntity(rat);
        BlockState ground = level.getBlockState(BlockPos.containing(feet.x, feet.y - 0.5D, feet.z));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground),
                feet.x, feet.y + 0.1D, feet.z, EMERGE_DIRT, 0.25D, 0.1D, 0.25D, 0.15D);
        level.playSound(null, feet.x, feet.y, feet.z, rat.getAmbientSound(), SoundSource.HOSTILE, EMERGE_VOLUME,
                0.9F + level.random.nextFloat() * 0.3F);
        return rat;
    }

    /** Falls apart into a puff of dust (its king died or is gone): no drops, no experience. */
    public void crumble()
    {
        if (this.level() instanceof ServerLevel level)
        {
            BlockState ground = level.getBlockState(this.blockPosition().below());
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground.isAir() ? Blocks.DIRT.defaultBlockState() : ground),
                    this.getX(), this.getY() + 0.2D, this.getZ(), CRUMBLE_DUST, 0.2D, 0.15D, 0.2D, 0.05D);
            level.sendParticles(ParticleTypes.POOF, this.getX(), this.getY() + 0.3D, this.getZ(), 4, 0.15D, 0.1D, 0.15D, 0.01D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), this.getDeathSound(), SoundSource.HOSTILE,
                    CRUMBLE_VOLUME, 1.2F);
        }
        this.discard();
    }

    @Override
    public void tick()
    {
        super.tick();
        if (!this.level().isClientSide && this.master != null && this.isAlive()
                && this.tickCount % RatKingTuning.RAT_MASTER_CHECK == 0 && this.tickCount > MASTER_GRACE_TICKS
                && !this.masterAlive())
        {
            this.crumble();
        }
    }

    /** True if the Rat King that called it is still alive (and loaded, and near). */
    private boolean masterAlive()
    {
        if (this.master == null || !(this.level() instanceof ServerLevel level))
        {
            return false;
        }
        Entity entity = level.getEntity(this.master);
        double range = RatKingTuning.RAT_RANGE;
        return entity instanceof RatKing king && king.isAlive() && king.distanceToSqr(this) < range * range;
    }

    /** Every bite of an enraged rat poisons (briefly). */
    @Override
    public boolean doHurtTarget(Entity target)
    {
        boolean hit = super.doHurtTarget(target);
        if (hit)
        {
            this.playSound(this.isEnraged() ? ModSounds.ENRAGED_RAT_ATTACK.get() : ModSounds.RAT_ATTACK.get(), BITE_VOLUME,
                    0.9F + this.random.nextFloat() * 0.2F);
            if (this.isEnraged() && target instanceof LivingEntity living)
            {
                living.addEffect(new MobEffectInstance(MobEffects.POISON, RatKingTuning.ENRAGED_BITE_POISON_TICKS, 0), this);
            }
        }
        return hit;
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.isEnraged() ? ModSounds.ENRAGED_RAT_AMBIENT.get() : ModSounds.RAT_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return this.isEnraged() ? ModSounds.ENRAGED_RAT_HURT.get() : ModSounds.RAT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return this.isEnraged() ? ModSounds.ENRAGED_RAT_DEATH.get() : ModSounds.RAT_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        this.playSound(this.isEnraged() ? ModSounds.ENRAGED_RAT_STEP.get() : ModSounds.RAT_STEP.get(), STEP_VOLUME, 1.0F);
    }

    // ---------------------------------------------------------------- animation (GeckoLib)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<Rat> state)
    {
        boolean moving = !this.isShowcase() && this.walkAnimation.speed() > 0.05F;
        return state.setAndContinue(this.isRunning() || moving ? RUN : IDLE);
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(CULL_MARGIN, 0.5D, CULL_MARGIN);
    }

    // ---------------------------------------------------------------- showcase rats

    /**
     * Spawns a frozen showcase rat of a design (regular or enraged, by the design), standing.
     *
     * @return the rat, already in the world, or null if it couldn't be created
     */
    @Nullable
    public static Rat spawnShowcase(ServerLevel level, Vec3 feet, float yaw, RatDesigns.Design design)
    {
        Rat rat = (design.enraged() ? ModEntities.ENRAGED_RAT : ModEntities.RAT).get().create(level);
        if (rat == null)
        {
            return null;
        }
        rat.entityData.set(DATA_SHOWCASE, true);
        rat.setDesign(design.number());
        rat.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        rat.setYHeadRot(yaw);
        rat.setYBodyRot(yaw);
        rat.yHeadRotO = yaw;
        rat.yBodyRotO = yaw;
        rat.setNoAi(true);
        rat.setSilent(true);
        rat.setPersistenceRequired();
        level.addFreshEntity(rat);
        return rat;
    }

    /** Right-clicking a showcase rat switches it between idle and running on the spot. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (this.level().isClientSide)
        {
            return this.isShowcase() ? InteractionResult.SUCCESS : InteractionResult.PASS;
        }
        if (!this.isShowcase())
        {
            return super.mobInteract(player, hand);
        }
        this.setRunning(!this.isRunning());
        RatDesigns.Design design = this.getDesign();
        player.displayClientMessage(Component.translatable("dross.rat.showcase." + (this.isRunning() ? "run" : "idle"),
                design.label(), design.name(), design.mood()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        if (this.isShowcase() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushable()
    {
        return !this.isShowcase() && super.isPushable();
    }

    @Override
    public void push(double x, double y, double z)
    {
        if (!this.isShowcase())
        {
            super.push(x, y, z);
        }
    }

    @Override
    public boolean isPushedByFluid()
    {
        return !this.isShowcase();
    }

    @Override
    protected boolean shouldDespawnInPeaceful()
    {
        return !this.isShowcase();
    }

    /** A wild rat despawns like any monster when no player is near; a summoned one never does (it crumbles instead). */
    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return !this.isShowcase() && !this.isSummoned() && super.removeWhenFarAway(distanceToClosestPlayer);
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_DESIGN, this.getDesign().number());
        tag.putBoolean(TAG_RUNNING, this.isRunning());
        if (this.isShowcase())
        {
            tag.putBoolean(TAG_SHOWCASE, true);
        }
        if (this.master != null)
        {
            tag.putUUID(TAG_MASTER, this.master);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_SHOWCASE, tag.getBoolean(TAG_SHOWCASE));
        this.entityData.set(DATA_RUNNING, tag.getBoolean(TAG_RUNNING));
        if (tag.contains(TAG_DESIGN))
        {
            this.setDesign(tag.getInt(TAG_DESIGN));
        }
        this.master = tag.hasUUID(TAG_MASTER) ? tag.getUUID(TAG_MASTER) : null;
    }
}
