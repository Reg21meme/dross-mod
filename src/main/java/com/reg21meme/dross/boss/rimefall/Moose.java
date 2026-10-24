package com.reg21meme.dross.boss.rimefall;

import com.reg21meme.dross.boss.huntsman.skull.SkullDrops;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import com.reg21meme.dross.boss.BossChase;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.entity.PartEntity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.PanicGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import javax.annotation.Nullable;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

/**
 * The wild moose of Rimefall (CLAUDE.md, "The plan", step 14): about 3.5-4 blocks tall with its antlers (the Huntsman's
 * Alpha Moose is bigger), alone. Bulls carry antlers; cows and calves don't (like real moose).
 * <ul>
 *   <li><b>Neutral</b>: hit one and it lowers its head and charges, then rams you, throwing you back more than up
 *       (the user's note).</li>
 *   <li><b>A mother</b> (a cow with her calf within {@link RimefallMobTuning#MOTHER_CALF_RANGE} blocks) attacks any
 *       player who comes within {@link RimefallMobTuning#MOTHER_NOTICE_RANGE} blocks of her or her calf, like a polar
 *       bear (the user's request), but never chases further than {@link RimefallMobTuning#MOTHER_LEASH} blocks from
 *       her calf; a moose without a calf never does. Hurting a calf brings its mother.</li>
 *   <li>A bull's ram hits harder than a cow's ({@link RimefallMobTuning#RAM_DAMAGE_BULL}, {@link RimefallMobTuning#RAM_DAMAGE_COW}).</li>
 *   <li>Some spawn as a mother with her calf ({@link RimefallMobTuning#MOTHER_CHANCE}); calves grow up in 20 minutes.
 *       Moose can't be bred. No drops for now.</li>
 * </ul>
 * Its look and animations are made by {@code tools/rimefall/mobs.py} ({@code rimemobs/moose.py}); now and then it
 * kneels on its front knees to graze.
 */
public class Moose extends Animal implements GeoEntity, NeutralMob, RimefallAnimal
{
    private static final EntityDataAccessor<Boolean> DATA_BULL = SynchedEntityData.defineId(Moose.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(Moose.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_COUNT = SynchedEntityData.defineId(Moose.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHOWCASE = SynchedEntityData.defineId(Moose.class, EntityDataSerializers.BOOLEAN);
    /** Frost on its coat: only a moose that spawned in Rimefall has it (the user, October 19). */
    private static final EntityDataAccessor<Boolean> DATA_SNOWY = SynchedEntityData.defineId(Moose.class, EntityDataSerializers.BOOLEAN);

    private static final String TAG_BULL = "Bull";
    private static final String TAG_SHOWCASE = "Showcase";
    /** A hunter village's animal, in its pen (the template sets it): it never starts a fight, and it runs when hurt
     * instead of fighting back (the plan of October 20, 2026: "the village animals passive"); out of its pen it's a
     * wild animal for good ({@link Pen}). */
    public static final String TAG_PENNED = "Penned";
    private static final String TAG_SNOWY = "Snowy";
    private static final UniformInt ANGER = TimeUtil.rangeOfSeconds(RimefallMobTuning.ANGER_MIN_SECONDS,
            RimefallMobTuning.ANGER_MAX_SECONDS);

    public static final String MOB = "moose";
    public static final RimefallAnims.Anim IDLE = RimefallAnims.loop(MOB, "idle", 0.0D);
    public static final RimefallAnims.Anim WALK = RimefallAnims.loop(MOB, "walk", 1.10D);
    public static final RimefallAnims.Anim RUN = RimefallAnims.loop(MOB, "run", 4.48D);
    public static final RimefallAnims.Anim CHARGE = RimefallAnims.loop(MOB, "charge", 7.21D);
    public static final RimefallAnims.Anim ATTACK = RimefallAnims.once(MOB, "attack", RimefallMobTuning.RAM_TICKS);
    public static final RimefallAnims.Anim EAT = RimefallAnims.once(MOB, "eat", RimefallMobTuning.MOOSE_EAT_TICKS);
    public static final List<RimefallAnims.Anim> ANIMS = List.of(IDLE, WALK, RUN, CHARGE, ATTACK, EAT);
    /** Faster than this (blocks a second) it trots instead of walking. */
    private static final double RUN_ABOVE = 2.4D;
    private static final int BLEND_TICKS = 4;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private int actionEnd = -1;
    private int seenActionCount;
    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;
    /** Server: a mother is chasing a player who came near her calf (not one who hurt her): she keeps to her calf. */
    private boolean protecting;
    /** Server: ticks before a mother who gave up a chase may start another. */
    private int motherPause;
    /** Server: a mother who gave up a chase is walking back to her calf. */
    private boolean returningToCalf;
    /** Server: where it was when it started chasing its target. */
    @Nullable
    private Vec3 chaseFrom;
    /**
     * Its body's hitboxes past its main one (a square round its middle: it's nearly 5 blocks long), in blocks from its
     * feet (its own left +x, up +y, front -z), measured from its model: its head, its rump.
     */
    private static final double[][] PART_SPOTS = {{0.0D, 1.7D, -2.4D}, {0.0D, 1.3D, 1.4D}};
    private static final float[][] PART_SIZES = {{1.2F, 1.7F}, {1.4F, 2.3F}};
    /** A calf is drawn at this share of a grown one (its parts too). */
    private static final float CALF_MODEL_SCALE = 0.45F;
    private final RimefallBodyPart<Moose>[] parts;

    @SuppressWarnings("unchecked")
    public Moose(EntityType<? extends Moose> type, Level level)
    {
        super(type, level);
        this.parts = new RimefallBodyPart[PART_SPOTS.length];
        for (int i = 0; i < this.parts.length; i++)
        {
            this.parts[i] = new RimefallBodyPart<>(this, PART_SIZES[i][0], PART_SIZES[i][1]);
        }
        // Its parts' ids follow its own (like the Ender Dragon's parts).
        this.setId(ENTITY_COUNTER.getAndAdd(this.parts.length + 1) + 1);
    }

    @Override
    public void setId(int id)
    {
        super.setId(id);
        if (this.parts != null)
        {
            for (int i = 0; i < this.parts.length; i++)
            {
                this.parts[i].setId(id + i + 1);
            }
        }
    }

    @Override
    public boolean isMultipartEntity()
    {
        return true;
    }

    @Override
    public PartEntity<?>[] getParts()
    {
        return this.parts;
    }

    /** Its head and rump hitboxes (in {@link #PART_SPOTS}' order). */
    public RimefallBodyPart<Moose>[] getBodyParts()
    {
        return this.parts;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, RimefallMobTuning.MOOSE_HEALTH)
                .add(Attributes.ARMOR, RimefallMobTuning.MOOSE_ARMOR)
                .add(Attributes.MOVEMENT_SPEED, RimefallMobTuning.MOOSE_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, RimefallMobTuning.MOOSE_KNOCKBACK_RESISTANCE)
                .add(Attributes.FOLLOW_RANGE, RimefallMobTuning.MOOSE_FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, RimefallMobTuning.RAM_DAMAGE_BULL);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_BULL, false);
        this.entityData.define(DATA_ACTION, -1);
        this.entityData.define(DATA_ACTION_COUNT, 0);
        this.entityData.define(DATA_SHOWCASE, false);
        this.entityData.define(DATA_SNOWY, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FightGoal());
        this.goalSelector.addGoal(2, new PanicGoal(this, 1.6D)
        {
            @Override
            public boolean canUse()
            {
                return (Moose.this.isBaby() || Moose.this.penned) && super.canUse();     // a calf runs; a grown moose fights
            }
        });
        this.goalSelector.addGoal(3, new BreedGoal(this, 1.0D));
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.1D, Ingredient.of(ModItems.SNOW_LICHEN.get(),
                ModItems.FROSTBLOOM.get()), false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.15D));
        this.goalSelector.addGoal(5, new EatGoal());
        // (a mother on her way back to her calf doesn't wander off instead: a stroll picked out there once took her 13
        // blocks from it, past the leash she'd just given up at)
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D)
        {
            @Override
            public boolean canUse()
            {
                return !Moose.this.returningToCalf && super.canUse();
            }

            @Override
            public boolean canContinueToUse()
            {
                return !Moose.this.returningToCalf && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<Player>(this, Player.class, 10, true, false, this::threatensCalf)
        {
            @Override
            public boolean canUse()
            {
                return Moose.this.motherPause <= 0 && !Moose.this.penned && super.canUse();
            }

            @Override
            public void start()
            {
                super.start();
                Moose.this.protecting = true;
            }
        });
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(4, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    // ---------------------------------------------------------------- who it is

    public boolean isBull()
    {
        return this.entityData.get(DATA_BULL);
    }

    public void setBull(boolean bull)
    {
        this.entityData.set(DATA_BULL, bull);
    }

    /** True if it wears the frosty coat: it spawned in Rimefall (the user: "only moose that spawned in Rimefall should
     * have white"; one in the Silverwood Forest, or from a spawn egg in the Overworld, isn't covered in snow). */
    public boolean isSnowy()
    {
        return this.entityData.get(DATA_SNOWY);
    }

    public void setSnowy(boolean snowy)
    {
        this.entityData.set(DATA_SNOWY, snowy);
    }

    /** Whether a moose spawning at its feet now spawned in Rimefall. */
    private boolean inRimefall(LevelReader level)
    {
        return level.getBiome(this.blockPosition()).is(DrossBiomes.RIMEFALL);
    }

    /** A grown cow with a calf near her (she attacks players who come close). */
    public boolean isMother()
    {
        return !this.isBull() && !this.isBaby() && !this.isShowcase() && !this.level().getEntitiesOfClass(Moose.class,
                this.getBoundingBox().inflate(RimefallMobTuning.MOTHER_CALF_RANGE), Moose::isBaby).isEmpty();
    }

    /** Her nearest calf within {@link RimefallMobTuning#MOTHER_CALF_RANGE} blocks (null: none). */
    @Nullable
    public Moose nearestCalf()
    {
        Moose best = null;
        double bestDistance = Double.MAX_VALUE;
        for (Moose calf : this.level().getEntitiesOfClass(Moose.class, this.getBoundingBox().inflate(RimefallMobTuning.MOTHER_CALF_RANGE),
                Moose::isBaby))
        {
            double d = this.distanceToSqr(calf);
            if (d < bestDistance)
            {
                bestDistance = d;
                best = calf;
            }
        }
        return best;
    }

    /**
     * True if she's a mother near her calf and the player has come within {@link RimefallMobTuning#MOTHER_NOTICE_RANGE}
     * of her or of the calf (she goes for them; the user: she shouldn't leave her calf).
     */
    private boolean threatensCalf(LivingEntity player)
    {
        if (this.isBull() || this.isBaby() || this.isShowcase())
        {
            return false;
        }
        Moose calf = this.nearestCalf();
        return calf != null && this.distanceTo(calf) <= RimefallMobTuning.MOTHER_LEASH
                && (player.distanceTo(this) < RimefallMobTuning.MOTHER_NOTICE_RANGE
                || player.distanceTo(calf) < RimefallMobTuning.MOTHER_NOTICE_RANGE);
    }

    /** The ram's damage: a bull hits harder than a cow (the user's request). */
    public float ramDamage()
    {
        return this.isBull() ? RimefallMobTuning.RAM_DAMAGE_BULL : RimefallMobTuning.RAM_DAMAGE_COW;
    }

    private boolean penned;
    /** Its pen, while it's penned: out of it, it's no longer penned ({@link Pen}). */
    private final Pen pen = new Pen();

    /** A hunter village's penned animal, inside its pen: it never goes for anyone ({@link #TAG_PENNED}). */
    public boolean isPenned()
    {
        return this.penned;
    }

    public void setPenned(boolean penned)
    {
        this.penned = penned;
        if (penned)
        {
            this.setTarget(null);
            this.stopBeingAngry();
        }
    }

    @Override
    public boolean isShowcase()
    {
        return this.entityData.get(DATA_SHOWCASE);
    }

    /** Its skull, now and then, when a player killed a grown bull ({@link SkullDrops}). */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit)
    {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (this.isBull() && !this.isBaby() && !this.isShowcase())
        {
            SkullDrops.sometimes(this, ModItems.MOOSE_SKULL.get(), looting, recentlyHit);
        }
    }

    /**
     * A natural moose is a mother with her calf now and then ({@link RimefallMobTuning#MOTHER_CHANCE}: a cow, her calf
     * standing beside her), otherwise alone, a bull or a cow at random; one from a spawn egg or {@code /summon} is a
     * bull or a cow at random.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag)
    {
        if (data == null)
        {
            data = new AgeableMob.AgeableMobGroupData(false);
        }
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        this.setBull(this.random.nextBoolean());
        this.setSnowy(this.inRimefall(level));
        boolean natural = reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
        if (natural && !this.isBaby() && this.random.nextFloat() < RimefallMobTuning.MOTHER_CHANCE)
        {
            this.setBull(false);
            Moose calf = ModEntities.MOOSE.get().create(level.getLevel());
            if (calf != null)
            {
                calf.setBull(this.random.nextBoolean());
                calf.setSnowy(this.isSnowy());
                calf.setBaby(true);
                float yaw = this.getYRot() * ((float) Math.PI / 180.0F);
                calf.moveTo(this.getX() + Math.sin(yaw) * 1.6D, this.getY(), this.getZ() - Math.cos(yaw) * 1.6D,
                        this.getYRot(), 0.0F);
                level.addFreshEntity(calf);
            }
        }
        return result;
    }

    /** Snow Lichen, or Frostbloom, Rimefall's flower, brings a bull and a cow into love (the user, October 20, 2026:
     * "the snow lichen for the [moose] ... we have a flower ... we can use that for both"; until then moose weren't
     * bred). */
    @Override
    public boolean isFood(ItemStack stack)
    {
        return stack.is(ModItems.SNOW_LICHEN.get()) || stack.is(ModItems.FROSTBLOOM.get());
    }

    /** It takes a bull and a cow. */
    @Override
    public boolean canMate(Animal other)
    {
        return other != this && other instanceof Moose mate && mate.isBull() != this.isBull() && super.canMate(other);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner)
    {
        Moose calf = ModEntities.MOOSE.get().create(level);
        if (calf != null)
        {
            calf.setBull(this.random.nextBoolean());
            calf.setSnowy(this.isSnowy());
            if (this.penned)
            {
                calf.setPenned(true);       // (born in the pen: its mother's pen)
                this.pen.copyTo(calf.pen);
            }
        }
        return calf;
    }

    // ---------------------------------------------------------------- being angry (a neutral mob)

    @Override
    public void startPersistentAngerTimer()
    {
        this.setRemainingPersistentAngerTime(ANGER.sample(this.random));
    }

    @Override
    public void setRemainingPersistentAngerTime(int time)
    {
        this.remainingPersistentAngerTime = time;
    }

    @Override
    public int getRemainingPersistentAngerTime()
    {
        return this.remainingPersistentAngerTime;
    }

    @Override
    public void setPersistentAngerTarget(@Nullable UUID target)
    {
        this.persistentAngerTarget = target;
    }

    @Nullable
    @Override
    public UUID getPersistentAngerTarget()
    {
        return this.persistentAngerTarget;
    }

    @Override
    public boolean canAttack(LivingEntity target)
    {
        return !(target instanceof Moose) && super.canAttack(target);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target)
    {
        super.setTarget(this.isShowcase() || this.penned ? null : target);
    }

    // ---------------------------------------------------------------- actions

    private void startAction(@Nullable RimefallAnims.Anim anim)
    {
        this.entityData.set(DATA_ACTION, anim == null ? -1 : ANIMS.indexOf(anim));
        this.entityData.set(DATA_ACTION_COUNT, this.entityData.get(DATA_ACTION_COUNT) + 1);
        this.actionEnd = anim == null || anim.loop() ? -1 : this.tickCount + anim.ticks();
    }

    @Nullable
    private RimefallAnims.Anim action()
    {
        int index = this.entityData.get(DATA_ACTION);
        return index >= 0 && index < ANIMS.size() ? ANIMS.get(index) : null;
    }

    @Override
    public List<RimefallAnims.Anim> animations()
    {
        return ANIMS;
    }

    @Override
    public void playShowcase(String animation)
    {
        RimefallAnims.Anim anim = RimefallAnims.named(ANIMS, animation);
        this.startAction(anim == IDLE ? null : anim);
    }

    @Override
    public void tick()
    {
        if (this.penned && !this.level().isClientSide && !this.pen.stillInside(this))
        {
            this.penned = false;            // (out of its pen: a wild animal from now on)
            this.pen.forget();
        }
        super.tick();
        RimefallBodyPart.place(this, this.parts, PART_SPOTS, this.isBaby() ? CALF_MODEL_SCALE : 1.0F);
        if (!this.level().isClientSide && this.actionEnd >= 0 && this.tickCount >= this.actionEnd)
        {
            this.startAction(null);
        }
        if (!this.level().isClientSide && this.isShowcase() && this.isBaby())
        {
            this.setAge(AgeableMob.BABY_START_AGE);                   // a showcase calf stays a calf
        }
    }

    @Override
    public void aiStep()
    {
        super.aiStep();
        if (!this.level().isClientSide)
        {
            // Its anger runs out even while it chases you (vanilla's "true" keeps a neutral mob angry for as long as it
            // keeps its target), and it never follows you far from where the chase began.
            this.updatePersistentAnger((ServerLevel) this.level(), false);
            this.keepToCalf();
            this.keepToHome();
        }
    }

    /** Gives up a chase {@link RimefallMobTuning#MOOSE_CHASE_LEASH} blocks from where it began, and goes back there. */
    private void keepToHome()
    {
        LivingEntity target = this.getTarget();
        if (target == null)
        {
            this.chaseFrom = null;
            return;
        }
        if (this.chaseFrom == null)
        {
            this.chaseFrom = this.position();
            return;
        }
        if (this.position().subtract(this.chaseFrom).horizontalDistance() > RimefallMobTuning.MOOSE_CHASE_LEASH)
        {
            Vec3 home = this.chaseFrom;
            this.stopBeingAngry();
            this.setTarget(null);
            this.protecting = false;
            this.chaseFrom = null;
            this.getNavigation().moveTo(home.x, home.y, home.z, 1.0D);
        }
    }

    /**
     * A mother chasing a player who came near her calf gives up once she's {@link RimefallMobTuning#MOTHER_LEASH} blocks
     * from it (or the calf is gone) and walks back to it.
     */
    private void keepToCalf()
    {
        if (this.motherPause > 0)
        {
            this.motherPause--;
        }
        if (this.returningToCalf)
        {
            // (her way back is asked for again whenever it stops: the chase's own end, a tick after she gave up,
            // stopped her first one, and she could wander off further from her calf instead)
            Moose calf = this.nearestCalf();
            if (calf == null || this.getTarget() != null
                    || this.distanceTo(calf) <= RimefallMobTuning.MOTHER_BACK_WITHIN)
            {
                this.returningToCalf = false;
            }
            else if (this.getNavigation().isDone())
            {
                this.getNavigation().moveTo(calf, 1.2D);
            }
        }
        if (!this.protecting)
        {
            return;
        }
        LivingEntity target = this.getTarget();
        if (target == null)
        {
            this.protecting = false;
            return;
        }
        Moose calf = this.nearestCalf();
        if (calf == null || this.distanceTo(calf) > RimefallMobTuning.MOTHER_LEASH)
        {
            this.stopBeingAngry();
            this.protecting = false;
            this.motherPause = RimefallMobTuning.MOTHER_GIVE_UP_PAUSE;
            if (calf != null)
            {
                this.getNavigation().moveTo(calf, 1.2D);
                this.returningToCalf = true;
            }
        }
    }

    /** Hurt by someone, she's angry like any moose (she chases them as far as a moose does). */
    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getEntity() instanceof LivingEntity)
        {
            this.protecting = false;
        }
        return hurt;
    }

    // ---------------------------------------------------------------- its fight: the charge and the ram

    private class FightGoal extends Goal
    {
        private int timer;
        private int cooldown;
        private boolean ramming;
        private boolean hit;
        private int repath;

        FightGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity target = Moose.this.getTarget();
            return !Moose.this.isBaby() && !Moose.this.isShowcase() && target != null && target.isAlive()
                    && Moose.this.canAttack(target);
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.ramming || this.canUse();
        }

        @Override
        public void start()
        {
            this.timer = 0;
            this.ramming = false;
            Moose.this.startAction(CHARGE);
            Moose.this.playSound(ModSounds.MOOSE_CHARGE.get(), 1.4F, Moose.this.getVoicePitch());
        }

        @Override
        public void stop()
        {
            Moose.this.getNavigation().stop();
            Moose.this.startAction(null);
            this.ramming = false;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            Moose self = Moose.this;
            LivingEntity target = self.getTarget();
            this.cooldown = Math.max(0, this.cooldown - 1);
            this.timer++;
            if (this.ramming)
            {
                if (target != null && this.timer < RimefallMobTuning.RAM_HIT_TICK)
                {
                    Rimehorn.faceTowards(self, target.position());
                }
                if (!this.hit && this.timer >= RimefallMobTuning.RAM_HIT_TICK)
                {
                    this.hit = true;
                    if (target != null && target.isAlive() && Rimehorn.inReach(self, target, RimefallMobTuning.RAM_REACH + 0.6D)
                            && target.hurt(self.damageSources().mobAttack(self), self.ramDamage()))
                    {
                        // Thrown back more than up (the user's note for the moose).
                        Vec3 away = target.position().subtract(self.position()).multiply(1, 0, 1);
                        away = away.lengthSqr() < 1.0E-4 ? self.getLookAngle().multiply(1, 0, 1) : away.normalize();
                        target.knockback(RimefallMobTuning.RAM_KNOCKBACK, -away.x, -away.z);
                        target.setDeltaMovement(target.getDeltaMovement().add(0.0D, RimefallMobTuning.RAM_KNOCKBACK_UP, 0.0D));
                        target.hurtMarked = true;
                    }
                }
                if (this.timer >= RimefallMobTuning.RAM_TICKS)
                {
                    this.ramming = false;
                    this.cooldown = RimefallMobTuning.RAM_COOLDOWN;
                    self.startAction(CHARGE);
                }
                return;
            }
            if (target == null)
            {
                return;
            }
            self.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.cooldown == 0 && Rimehorn.inReach(self, target, RimefallMobTuning.RAM_REACH))
            {
                this.ramming = true;
                this.hit = false;
                this.timer = 0;
                self.getNavigation().stop();
                self.startAction(ATTACK);
                self.playSound(ModSounds.MOOSE_ATTACK.get(), 1.2F, self.getVoicePitch());
                return;
            }
            // Straight at you, head on, when the way is clear; round things by the pathfinder when it isn't.
            this.repath = BossChase.chase(self, target, RimefallMobTuning.MOOSE_CHARGE_SPEED, this.repath, 10);
        }
    }

    // ---------------------------------------------------------------- grazing

    /** Now and then it kneels on its front knees and grazes (the animation gets down and up again by itself). */
    private class EatGoal extends Goal
    {
        private int left;

        EatGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            Moose self = Moose.this;
            return !self.isShowcase() && !self.isBaby() && self.getTarget() == null && self.onGround()
                    && self.getNavigation().isDone() && self.getRandom().nextInt(RimefallMobTuning.MOOSE_EAT_CHANCE) == 0;
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.left > 0 && Moose.this.getTarget() == null;
        }

        @Override
        public void start()
        {
            this.left = RimefallMobTuning.MOOSE_EAT_TICKS;
            Moose.this.getNavigation().stop();
            Moose.this.startAction(EAT);
        }

        @Override
        public void tick()
        {
            this.left--;
            if (this.left % 30 == 0 && this.left > 20 && this.left < RimefallMobTuning.MOOSE_EAT_TICKS - 30)
            {
                Moose.this.playSound(ModSounds.MOOSE_EAT.get(), 0.7F, Moose.this.getVoicePitch());
            }
        }

        @Override
        public void stop()
        {
            if (Moose.this.action() == EAT)
            {
                Moose.this.startAction(null);
            }
        }
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.MOOSE_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.MOOSE_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.MOOSE_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        this.playSound(ModSounds.MOOSE_STEP.get(), 0.5F, 1.0F);
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 200;
    }

    // ---------------------------------------------------------------- animation (GeckoLib)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<Moose> state)
    {
        AnimationController<Moose> controller = state.getController();
        int count = this.entityData.get(DATA_ACTION_COUNT);
        if (count != this.seenActionCount)
        {
            this.seenActionCount = count;
            controller.forceAnimationReset();
        }
        RimefallAnims.Anim action = this.action();
        double speed = RimefallAnims.groundSpeed(this);
        boolean moving = !this.isShowcase() && speed > RimefallAnims.MOVING;
        if (action == CHARGE && !this.isShowcase())
        {
            // Chasing: the head-down gallop while it runs, standing (idle) while it waits.
            if (moving)
            {
                RimefallAnims.matchSpeed(controller, CHARGE, speed);
                return state.setAndContinue(CHARGE.raw());
            }
            controller.setAnimationSpeed(1.0D);
            return state.setAndContinue(IDLE.raw());
        }
        if (action != null)
        {
            controller.setAnimationSpeed(1.0D);
            return state.setAndContinue(action.raw());
        }
        if (moving)
        {
            RimefallAnims.Anim gait = speed > RUN_ABOVE ? RUN : WALK;
            RimefallAnims.matchSpeed(controller, gait, speed);
            return state.setAndContinue(gait.raw());
        }
        controller.setAnimationSpeed(1.0D);
        return state.setAndContinue(IDLE.raw());
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(2.0D, 1.0D, 2.0D);
    }

    // ---------------------------------------------------------------- showcase moose

    /**
     * Spawns a frozen showcase moose: a bull, a cow or a calf.
     *
     * @return it, already in the world, or null if it couldn't be made
     */
    @Nullable
    public static Moose spawnShowcase(ServerLevel level, Vec3 feet, float yaw, boolean bull, boolean calf)
    {
        Moose moose = ModEntities.MOOSE.get().create(level);
        if (moose == null)
        {
            return null;
        }
        moose.entityData.set(DATA_SHOWCASE, true);
        moose.setBull(bull);
        moose.setSnowy(level.getBiome(BlockPos.containing(feet)).is(DrossBiomes.RIMEFALL));
        if (calf)
        {
            moose.setBaby(true);
        }
        RimefallAnimals.freeze(moose, level, feet, yaw);
        return moose;
    }

    @Override
    public InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (!this.isShowcase())
        {
            return super.mobInteract(player, hand);
        }
        if (hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide)
        {
            RimefallAnims.Anim now = this.action();
            int next = (ANIMS.indexOf(now == null ? IDLE : now) + 1) % ANIMS.size();
            this.playShowcase(ANIMS.get(next).name());
            player.displayClientMessage(Component.translatable("dross.rimefall_mob.showcase.playing",
                    Component.translatable("dross.moose." + (this.isBaby() ? "calf" : this.isBull() ? "bull" : "cow")),
                    ANIMS.get(next).name()), true);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        return (this.isShowcase() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)) || super.isInvulnerableTo(source);
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
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_BULL, this.isBull());
        tag.putBoolean(TAG_SNOWY, this.isSnowy());
        if (this.isShowcase())
        {
            tag.putBoolean(TAG_SHOWCASE, true);
        }
        if (this.penned)
        {
            tag.putBoolean(TAG_PENNED, true);
            this.pen.save(tag);
        }
        this.addPersistentAngerSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.setBull(tag.getBoolean(TAG_BULL));
        this.setSnowy(tag.getBoolean(TAG_SNOWY));
        this.entityData.set(DATA_SHOWCASE, tag.getBoolean(TAG_SHOWCASE));
        this.penned = tag.getBoolean(TAG_PENNED);
        this.pen.load(tag);
        this.readPersistentAngerSaveData(this.level(), tag);
    }
}
