package com.reg21meme.dross.boss.rimefall;

import com.reg21meme.dross.boss.BossChase;
import com.reg21meme.dross.boss.huntsman.skull.SkullDrops;
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
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.PartEntity;
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
 * The Rimehorn (CLAUDE.md, "The plan", step 14): Rimefall's woolly rhino, about 4.5 blocks tall and 8 long, in packs
 * of 6-8. The frosted ones (design 7A) are the males, the dark brown ones (7B, with snow on their backs) the females.
 * <ul>
 *   <li><b>Neutral</b>: it leaves you alone until you hurt one (a calf too); then its pack within
 *       {@link RimefallMobTuning#RIMEHORN_FOLLOW_RANGE} blocks turns on you, for 20-39 seconds.</li>
 *   <li><b>Its moves</b> (the user's): the <b>horn flick</b> when you're close (it drops its head and flicks its great
 *       horn up, launching you mostly upwards: {@link RimefallMobTuning#FLICK_DAMAGE}), and the <b>rush</b> when
 *       you're further off (a 7-8 block charge with its head low: {@link RimefallMobTuning#RUSH_DAMAGE}); hurt by an
 *       arrow from afar, it rushes at once.</li>
 *   <li><b>Calves</b>: feed Frostbloom to a male and a female (both are needed). The calf takes its sex's coat and
 *       grows up in about 35 minutes. <b>A Rimehorn you bred never attacks you</b>, even grown up and even when its
 *       pack is angry with you (it remembers who bred it: {@code Breeder}).</li>
 *   <li>No drops for now (its meat comes later); a little experience.</li>
 * </ul>
 * Its look and animations are made by {@code tools/rimefall/mobs.py} ({@code rimemobs/rimehorn.py}).
 */
public class Rimehorn extends Animal implements GeoEntity, NeutralMob, RimefallAnimal
{
    private static final EntityDataAccessor<Boolean> DATA_MALE = SynchedEntityData.defineId(Rimehorn.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(Rimehorn.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_COUNT = SynchedEntityData.defineId(Rimehorn.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHOWCASE = SynchedEntityData.defineId(Rimehorn.class, EntityDataSerializers.BOOLEAN);

    private static final String TAG_MALE = "Male";
    private static final String TAG_BREEDER = "Breeder";
    private static final String TAG_SHOWCASE = "Showcase";
    /** A hunter village's animal, in its pen (the template sets it): it never starts a fight, and it runs when hurt
     * instead of fighting back (the plan of October 20, 2026: "the village animals passive"); out of its pen it's a
     * wild animal for good ({@link Pen}). */
    public static final String TAG_PENNED = "Penned";
    private static final UniformInt ANGER = TimeUtil.rangeOfSeconds(RimefallMobTuning.ANGER_MIN_SECONDS,
            RimefallMobTuning.ANGER_MAX_SECONDS);

    public static final String MOB = "rimehorn";
    public static final RimefallAnims.Anim IDLE = RimefallAnims.loop(MOB, "idle", 0.0D);
    public static final RimefallAnims.Anim WALK = RimefallAnims.loop(MOB, "walk", 1.12D);
    public static final RimefallAnims.Anim RUN = RimefallAnims.loop(MOB, "run", 5.37D);
    public static final RimefallAnims.Anim RUSH = RimefallAnims.loop(MOB, "rush", 10.79D);
    public static final RimefallAnims.Anim EAT = RimefallAnims.loop(MOB, "eat", 0.0D);
    public static final RimefallAnims.Anim FLICK = RimefallAnims.once(MOB, "flick", RimefallMobTuning.FLICK_TICKS);
    public static final List<RimefallAnims.Anim> ANIMS = List.of(IDLE, WALK, RUN, RUSH, EAT, FLICK);
    /** Faster than this (blocks a second) it gallops instead of walking. */
    private static final double RUN_ABOVE = 2.6D;
    private static final int BLEND_TICKS = 4;
    /**
     * Its body's hitboxes past its main one (a square round its middle: it's 8 blocks long), in blocks from its feet
     * (its own left +x, up +y, front -z), measured from its model: its face and horn, its neck and chest, its rump.
     */
    private static final double[][] PART_SPOTS = {{0.0D, 1.3D, -4.15D}, {0.0D, 0.6D, -2.7D}, {0.0D, 0.4D, 2.3D}};
    private static final float[][] PART_SIZES = {{1.9F, 3.0F}, {2.6F, 3.8F}, {2.8F, 3.8F}};
    /** A calf is drawn at this share of a grown one (its parts too). */
    private static final float CALF_MODEL_SCALE = 0.42F;

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    /** Server: when the action playing ends (-1: it never does, a showcase's loop). */
    private int actionEnd = -1;
    /** Client: the action counter last seen (a change restarts the animation). */
    private int seenActionCount;
    @Nullable
    private UUID breeder;
    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;
    /** Server: set when an arrow (or anything thrown) hurts it from afar: it rushes at once. */
    private boolean rushNow;
    /** Server: where it was when it started chasing its target (it gives up past {@link RimefallMobTuning#RIMEHORN_CHASE_LEASH}). */
    @Nullable
    private Vec3 chaseFrom;
    private final RimefallBodyPart<Rimehorn>[] parts;

    @SuppressWarnings("unchecked")
    public Rimehorn(EntityType<? extends Rimehorn> type, Level level)
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

    /** Its face, chest and rump hitboxes (in {@link #PART_SPOTS}' order). */
    public RimefallBodyPart<Rimehorn>[] getBodyParts()
    {
        return this.parts;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Mob.createMobAttributes()
                .add(Attributes.MAX_HEALTH, RimefallMobTuning.RIMEHORN_HEALTH)
                .add(Attributes.ARMOR, RimefallMobTuning.RIMEHORN_ARMOR)
                .add(Attributes.MOVEMENT_SPEED, RimefallMobTuning.RIMEHORN_SPEED)
                .add(Attributes.KNOCKBACK_RESISTANCE, RimefallMobTuning.RIMEHORN_KNOCKBACK_RESISTANCE)
                .add(Attributes.FOLLOW_RANGE, RimefallMobTuning.RIMEHORN_FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, RimefallMobTuning.FLICK_DAMAGE);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_MALE, false);
        this.entityData.define(DATA_ACTION, -1);
        this.entityData.define(DATA_ACTION_COUNT, 0);
        this.entityData.define(DATA_SHOWCASE, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FightGoal());
        this.goalSelector.addGoal(1, new PanicGoal(this, 1.6D)
        {
            @Override
            public boolean canUse()
            {
                return (Rimehorn.this.isBaby() || Rimehorn.this.penned) && super.canUse();     // a calf runs off; its herd does the fighting
            }
        });
        this.goalSelector.addGoal(2, new MateGoal());
        this.goalSelector.addGoal(3, new TemptGoal(this, 1.1D, Ingredient.of(ModItems.FROSTBLOOM.get(),
                ModItems.RIMEBERRIES.get()), false));
        this.goalSelector.addGoal(4, new FollowParentGoal(this, 1.1D));
        this.goalSelector.addGoal(5, new EatGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 1.0D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this).setAlertOthers());
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    // ---------------------------------------------------------------- who it is

    public boolean isMale()
    {
        return this.entityData.get(DATA_MALE);
    }

    public void setMale(boolean male)
    {
        this.entityData.set(DATA_MALE, male);
    }

    /** The player who bred it (null: wild). It never attacks them. */
    @Nullable
    public UUID getBreeder()
    {
        return this.breeder;
    }

    public void setBreeder(@Nullable UUID breeder)
    {
        this.breeder = breeder;
    }

    private boolean isBreeder(@Nullable LivingEntity entity)
    {
        return entity != null && this.breeder != null && this.breeder.equals(entity.getUUID());
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

    /** Its skull, now and then, when a player killed a grown one ({@link SkullDrops}). */
    @Override
    protected void dropCustomDeathLoot(DamageSource source, int looting, boolean recentlyHit)
    {
        super.dropCustomDeathLoot(source, looting, recentlyHit);
        if (!this.isBaby() && !this.isShowcase())
        {
            SkullDrops.sometimes(this, ModItems.RIMEHORN_SKULL.get(), looting, recentlyHit);
        }
    }

    /**
     * A natural pack: its first {@link RimefallMobTuning#PACK_MALES} are males, the rest females (vanilla's group data
     * counts them). One from a spawn egg or {@code /summon} is a male or a female at random.
     */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag)
    {
        if (data == null)
        {
            data = new AgeableMob.AgeableMobGroupData(0.1F);
        }
        SpawnGroupData result = super.finalizeSpawn(level, difficulty, reason, data, tag);
        boolean pack = reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
        int index = result instanceof AgeableMob.AgeableMobGroupData group ? group.getGroupSize() - 1 : 0;
        this.setMale(pack ? index < RimefallMobTuning.PACK_MALES : this.random.nextBoolean());
        return result;
    }

    // ---------------------------------------------------------------- calves

    /** Frostbloom, Rimefall's flower, or Rimeberries bring two Rimehorns into love (the user, October 20, 2026: "you
     * can use rimeberry bush for rimehorn ... we have a flower ... we can use that for both"). */
    @Override
    public boolean isFood(ItemStack stack)
    {
        return stack.is(ModItems.FROSTBLOOM.get()) || stack.is(ModItems.RIMEBERRIES.get());
    }

    /** It takes a male and a female (the user: "it needs a male and a female"). */
    @Override
    public boolean canMate(Animal other)
    {
        return other != this && other instanceof Rimehorn mate && mate.isMale() != this.isMale() && super.canMate(other);
    }

    @Nullable
    @Override
    public AgeableMob getBreedOffspring(ServerLevel level, AgeableMob partner)
    {
        Rimehorn calf = ModEntities.RIMEHORN.get().create(level);
        if (calf != null)
        {
            calf.setMale(this.random.nextBoolean());
            ServerPlayer cause = this.getLoveCause();
            if (cause == null && partner instanceof Animal mate)
            {
                cause = mate.getLoveCause();
            }
            if (cause != null)
            {
                calf.setBreeder(cause.getUUID());
            }
            if (this.penned)
            {
                calf.setPenned(true);       // (born in the pen: its mother's pen)
                this.pen.copyTo(calf.pen);
            }
        }
        return calf;
    }

    /** How close (middle to middle) it must come to its mate: its own width and a bit (vanilla's 3 blocks is less than a Rimehorn is wide). */
    public double mateReach()
    {
        return this.getBbWidth() + RimefallMobTuning.MATE_REACH_PAST_WIDTH;
    }

    /**
     * Vanilla's breeding goal for an animal too big for it: it walks up to the nearest Rimehorn in love it can mate with
     * (the other sex) and they make a calf once they're within {@link #mateReach} of each other for 3 seconds (vanilla
     * asks for 3 blocks, which two Rimehorns pushing against each other never reach).
     */
    private class MateGoal extends Goal
    {
        private static final int LOVE_TICKS = 60;
        @Nullable
        private Rimehorn partner;
        private int loveTime;

        MateGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            if (!Rimehorn.this.isInLove())
            {
                return false;
            }
            this.partner = this.findPartner();
            return this.partner != null;
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.partner != null && this.partner.isAlive() && this.partner.isInLove() && this.loveTime < LOVE_TICKS * 3;
        }

        @Override
        public void stop()
        {
            this.partner = null;
            this.loveTime = 0;
        }

        @Override
        public void tick()
        {
            Rimehorn self = Rimehorn.this;
            if (this.partner == null)
            {
                return;
            }
            self.getLookControl().setLookAt(this.partner, 10.0F, (float) self.getMaxHeadXRot());
            double reach = Math.max(self.mateReach(), this.partner.mateReach());
            boolean close = self.distanceToSqr(this.partner) < reach * reach;
            if (close)
            {
                self.getNavigation().stop();
            }
            else
            {
                self.getNavigation().moveTo(this.partner, 1.0D);
            }
            this.loveTime++;
            if (this.loveTime >= this.adjustedTickDelay(LOVE_TICKS) && close && self.level() instanceof ServerLevel server)
            {
                self.spawnChildFromBreeding(server, this.partner);
                this.partner = null;
            }
        }

        @Nullable
        private Rimehorn findPartner()
        {
            Rimehorn self = Rimehorn.this;
            Rimehorn best = null;
            double bestDistance = Double.MAX_VALUE;
            for (Rimehorn other : self.level().getEntitiesOfClass(Rimehorn.class,
                    self.getBoundingBox().inflate(RimefallMobTuning.MATE_SEARCH_RANGE)))
            {
                if (self.canMate(other))
                {
                    double d = self.distanceToSqr(other);
                    if (d < bestDistance)
                    {
                        bestDistance = d;
                        best = other;
                    }
                }
            }
            return best;
        }
    }

    /** A calf grows up in {@link RimefallMobTuning#RIMEHORN_GROW_TICKS} ticks (vanilla's in 20 minutes). */
    @Override
    public void setBaby(boolean baby)
    {
        this.setAge(baby ? -RimefallMobTuning.RIMEHORN_GROW_TICKS : 0);
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

    /** Never at the player who bred it. */
    @Override
    public boolean isAngryAt(LivingEntity entity)
    {
        return !this.isBreeder(entity) && NeutralMob.super.isAngryAt(entity);
    }

    @Override
    public boolean canAttack(LivingEntity target)
    {
        return !this.isBreeder(target) && !(target instanceof Rimehorn) && super.canAttack(target);
    }

    /** A pack's call (or anything else) can't turn it on the player who bred it. */
    @Override
    public void setTarget(@Nullable LivingEntity target)
    {
        super.setTarget(this.isBreeder(target) || this.isShowcase() || this.penned ? null : target);
    }

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && source.getDirectEntity() instanceof Projectile && source.getEntity() instanceof LivingEntity shooter
                && shooter.distanceTo(this) > RimefallMobTuning.RUSH_MIN_DISTANCE)
        {
            this.rushNow = true;        // shot from afar: it rushes at the shooter
        }
        return hurt;
    }

    // ---------------------------------------------------------------- actions (what the animations show)

    private void startAction(@Nullable RimefallAnims.Anim anim, boolean forever)
    {
        int index = anim == null ? -1 : ANIMS.indexOf(anim);
        this.entityData.set(DATA_ACTION, index);
        this.entityData.set(DATA_ACTION_COUNT, this.entityData.get(DATA_ACTION_COUNT) + 1);
        this.actionEnd = anim == null || forever || anim.loop() ? -1 : this.tickCount + anim.ticks();
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
        this.startAction(anim == IDLE ? null : anim, true);
        if (anim != null && !anim.loop())
        {
            this.actionEnd = this.tickCount + anim.ticks();
        }
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
            this.startAction(null, false);
        }
        if (!this.level().isClientSide && this.isShowcase() && this.isBaby())
        {
            this.setAge(-RimefallMobTuning.RIMEHORN_GROW_TICKS);      // a showcase calf stays a calf
        }
    }

    @Override
    public void aiStep()
    {
        super.aiStep();
        if (!this.level().isClientSide)
        {
            // Its anger runs out even while it chases you (false: vanilla's polar bears and wolves stay angry for as
            // long as they keep you as their target, and a Rimehorn that keeps up with you never lets go: the user saw
            // one chase them across the biome).
            this.updatePersistentAnger((ServerLevel) this.level(), false);
            this.keepToHome();
        }
    }

    /**
     * Gives up a chase once it's {@link RimefallMobTuning#RIMEHORN_CHASE_LEASH} blocks from where it began (and goes back
     * there), so a herd never follows you far.
     */
    /** For the tests: where its chase began (as if it had already chased its target this far). */
    void setChaseFromForTest(Vec3 from)
    {
        this.chaseFrom = from;
    }

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
        if (this.position().subtract(this.chaseFrom).horizontalDistance() > RimefallMobTuning.RIMEHORN_CHASE_LEASH)
        {
            Vec3 home = this.chaseFrom;
            this.stopBeingAngry();
            this.setTarget(null);
            this.chaseFrom = null;
            this.getNavigation().moveTo(home.x, home.y, home.z, 1.0D);
        }
    }

    // ---------------------------------------------------------------- its fight: the flick and the rush

    /** Chases its target, rushes it from afar, flicks it with its horn up close. Adults only (calves just call). */
    private class FightGoal extends Goal
    {
        private enum Phase { CHASE, WINDUP, RUSH, FLICK }

        private Phase phase = Phase.CHASE;
        private int timer;
        private int flickCooldown;
        private int rushCooldown;
        private Vec3 rushDir = Vec3.ZERO;
        private Vec3 rushStart = Vec3.ZERO;
        private boolean flickHit;
        private int repath;

        FightGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK, Flag.JUMP));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity target = Rimehorn.this.getTarget();
            return !Rimehorn.this.isBaby() && !Rimehorn.this.isShowcase() && target != null && target.isAlive()
                    && Rimehorn.this.canAttack(target);
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.phase == Phase.RUSH || this.phase == Phase.FLICK || this.canUse();
        }

        @Override
        public void start()
        {
            this.phase = Phase.CHASE;
            this.timer = 0;
        }

        @Override
        public void stop()
        {
            Rimehorn.this.getNavigation().stop();
            if (Rimehorn.this.action() == RUSH || Rimehorn.this.action() == FLICK)
            {
                Rimehorn.this.startAction(null, false);
            }
            this.phase = Phase.CHASE;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            Rimehorn self = Rimehorn.this;
            LivingEntity target = self.getTarget();
            this.flickCooldown = Math.max(0, this.flickCooldown - 1);
            this.rushCooldown = Math.max(0, this.rushCooldown - 1);
            this.timer++;
            switch (this.phase)
            {
                case FLICK -> this.tickFlick(target);
                case WINDUP -> this.tickWindup(target);
                case RUSH -> this.tickRush();
                default -> this.tickChase(target);
            }
        }

        private void tickChase(@Nullable LivingEntity target)
        {
            Rimehorn self = Rimehorn.this;
            if (target == null)
            {
                return;
            }
            self.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = horizontalDistance(self, target);
            if (this.flickCooldown == 0 && inReach(self, target, RimefallMobTuning.FLICK_REACH))
            {
                this.phase = Phase.FLICK;
                this.timer = 0;
                this.flickHit = false;
                self.getNavigation().stop();
                self.startAction(FLICK, false);
                self.playSound(ModSounds.RIMEHORN_FLICK.get(), 1.0F, self.getVoicePitch());
                return;
            }
            boolean shot = self.rushNow;
            if ((this.rushCooldown == 0 || shot) && distance >= RimefallMobTuning.RUSH_MIN_DISTANCE
                    && distance <= RimefallMobTuning.RUSH_MAX_DISTANCE && self.hasLineOfSight(target))
            {
                self.rushNow = false;
                this.phase = Phase.WINDUP;
                this.timer = 0;
                self.getNavigation().stop();
                return;
            }
            // Straight at you, head on, when the way is clear (the user saw them run at you sideways along the
            // pathfinder's zigzag); round things by the pathfinder when it isn't.
            this.repath = BossChase.chase(self, target, RimefallMobTuning.RIMEHORN_CHASE_SPEED, this.repath, 10);
        }

        private void tickWindup(@Nullable LivingEntity target)
        {
            Rimehorn self = Rimehorn.this;
            if (target != null)
            {
                self.getLookControl().setLookAt(target, 30.0F, 30.0F);
                faceTowards(self, target.position());
            }
            if (this.timer >= RimefallMobTuning.RUSH_WINDUP)
            {
                Vec3 to = target == null ? self.getLookAngle() : target.position().subtract(self.position());
                Vec3 flat = new Vec3(to.x, 0.0D, to.z);
                this.rushDir = flat.lengthSqr() < 1.0E-4 ? self.getLookAngle().multiply(1, 0, 1).normalize() : flat.normalize();
                this.rushStart = self.position();
                this.phase = Phase.RUSH;
                this.timer = 0;
                self.startAction(RUSH, false);
                self.playSound(ModSounds.RIMEHORN_RUSH.get(), 1.4F, self.getVoicePitch());
            }
        }

        private void tickRush()
        {
            Rimehorn self = Rimehorn.this;
            Vec3 motion = self.getDeltaMovement();
            self.setDeltaMovement(this.rushDir.x * RimefallMobTuning.RUSH_SPEED, motion.y, this.rushDir.z * RimefallMobTuning.RUSH_SPEED);
            float yaw = (float) (Mth.atan2(this.rushDir.z, this.rushDir.x) * (180.0D / Math.PI)) - 90.0F;
            self.setYRot(yaw);
            self.setYBodyRot(yaw);
            self.setYHeadRot(yaw);
            // The first thing in its way (never another Rimehorn, never the player who bred it) is hit and thrown.
            AABB ahead = self.getBoundingBox().expandTowards(this.rushDir.scale(1.2D)).inflate(0.2D);
            for (LivingEntity hit : self.level().getEntitiesOfClass(LivingEntity.class, ahead,
                    e -> e != self && e.isAlive() && !(e instanceof Rimehorn) && !self.isBreeder(e) && !e.isSpectator()
                            && !(e instanceof Player player && player.isCreative())))
            {
                if (hit.hurt(self.damageSources().mobAttack(self), RimefallMobTuning.RUSH_DAMAGE))
                {
                    hit.knockback(RimefallMobTuning.RUSH_KNOCKBACK, -this.rushDir.x, -this.rushDir.z);
                    hit.setDeltaMovement(hit.getDeltaMovement().add(0.0D, RimefallMobTuning.RUSH_KNOCKBACK_UP, 0.0D));
                    hit.hurtMarked = true;
                }
                this.endRush();
                return;
            }
            double run = self.position().subtract(this.rushStart).horizontalDistance();
            if (run >= RimefallMobTuning.RUSH_LENGTH || this.timer >= RimefallMobTuning.RUSH_MAX_TICKS || self.horizontalCollision)
            {
                this.endRush();
            }
        }

        private void endRush()
        {
            Rimehorn self = Rimehorn.this;
            self.setDeltaMovement(self.getDeltaMovement().multiply(0.3D, 1.0D, 0.3D));
            self.startAction(null, false);
            this.rushCooldown = RimefallMobTuning.RUSH_COOLDOWN;
            this.phase = Phase.CHASE;
            this.timer = 0;
        }

        private void tickFlick(@Nullable LivingEntity target)
        {
            Rimehorn self = Rimehorn.this;
            if (target != null && this.timer < RimefallMobTuning.FLICK_HIT_TICK)
            {
                self.getLookControl().setLookAt(target, 30.0F, 30.0F);
                faceTowards(self, target.position());
            }
            if (!this.flickHit && this.timer >= RimefallMobTuning.FLICK_HIT_TICK)
            {
                this.flickHit = true;
                if (target != null && target.isAlive() && inReach(self, target, RimefallMobTuning.FLICK_REACH + 0.6D)
                        && target.hurt(self.damageSources().mobAttack(self), RimefallMobTuning.FLICK_DAMAGE))
                {
                    // Launched mostly upwards, a little away (the user: "more vertical than horizontal").
                    Vec3 away = target.position().subtract(self.position()).multiply(1, 0, 1);
                    away = away.lengthSqr() < 1.0E-4 ? self.getLookAngle().multiply(1, 0, 1) : away.normalize();
                    double resist = 1.0D - target.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.6D;
                    target.setDeltaMovement(away.x * RimefallMobTuning.FLICK_LAUNCH_BACK * resist,
                            RimefallMobTuning.FLICK_LAUNCH_UP * resist, away.z * RimefallMobTuning.FLICK_LAUNCH_BACK * resist);
                    target.hurtMarked = true;
                }
            }
            if (this.timer >= RimefallMobTuning.FLICK_TICKS)
            {
                this.flickCooldown = RimefallMobTuning.FLICK_COOLDOWN;
                this.phase = Phase.CHASE;
                this.timer = 0;
            }
        }
    }

    /** Distance between two entities' middles along the ground. */
    static double horizontalDistance(LivingEntity a, LivingEntity b)
    {
        return Math.sqrt(Mth.square(a.getX() - b.getX()) + Mth.square(a.getZ() - b.getZ()));
    }

    /**
     * True if {@code target} is within {@code reach} blocks of the front of {@code self}'s body (its middle plus half
     * its width) and roughly in front of it.
     */
    static boolean inReach(LivingEntity self, LivingEntity target, double reach)
    {
        double distance = horizontalDistance(self, target) - self.getBbWidth() / 2.0D - target.getBbWidth() / 2.0D;
        if (distance > reach || Math.abs(target.getY() - self.getY()) > self.getBbHeight())
        {
            return false;
        }
        Vec3 to = target.position().subtract(self.position()).multiply(1, 0, 1);
        if (to.lengthSqr() < 1.0E-4)
        {
            return true;
        }
        float yaw = self.getYRot() * ((float) Math.PI / 180.0F);
        Vec3 facing = new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
        return facing.dot(to.normalize()) > 0.2D;
    }

    static void faceTowards(LivingEntity self, Vec3 point)
    {
        double dx = point.x - self.getX();
        double dz = point.z - self.getZ();
        if (dx * dx + dz * dz < 1.0E-4)
        {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * (180.0D / Math.PI)) - 90.0F;
        self.setYRot(Mth.rotateIfNecessary(self.getYRot(), yaw, 12.0F));
        self.setYBodyRot(self.getYRot());
    }

    // ---------------------------------------------------------------- grazing

    /** Now and then, standing still with nothing else to do, it lowers its head and grazes. */
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
            Rimehorn self = Rimehorn.this;
            return !self.isShowcase() && self.getTarget() == null && self.onGround() && self.getNavigation().isDone()
                    && self.getRandom().nextInt(RimefallMobTuning.RIMEHORN_EAT_CHANCE) == 0;
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.left > 0 && Rimehorn.this.getTarget() == null && Rimehorn.this.hurtTime == 0;
        }

        @Override
        public void start()
        {
            this.left = RimefallMobTuning.RIMEHORN_EAT_TICKS;
            Rimehorn.this.getNavigation().stop();
            Rimehorn.this.startAction(EAT, false);
        }

        @Override
        public void tick()
        {
            this.left--;
            if (this.left % 30 == 0)
            {
                Rimehorn.this.playSound(ModSounds.RIMEHORN_EAT.get(), 0.8F, Rimehorn.this.getVoicePitch());
            }
        }

        @Override
        public void stop()
        {
            if (Rimehorn.this.action() == EAT)
            {
                Rimehorn.this.startAction(null, false);
            }
        }
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.RIMEHORN_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.RIMEHORN_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.RIMEHORN_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        this.playSound(ModSounds.RIMEHORN_STEP.get(), 0.6F, 1.0F);
    }

    @Override
    protected float getSoundVolume()
    {
        return 1.3F;
    }

    // ---------------------------------------------------------------- animation (GeckoLib)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<Rimehorn> state)
    {
        AnimationController<Rimehorn> controller = state.getController();
        int count = this.entityData.get(DATA_ACTION_COUNT);
        if (count != this.seenActionCount)
        {
            this.seenActionCount = count;
            controller.forceAnimationReset();
        }
        RimefallAnims.Anim action = this.action();
        double speed = RimefallAnims.groundSpeed(this);
        if (action != null)
        {
            if (action == RUSH && !this.isShowcase())
            {
                RimefallAnims.matchSpeed(controller, RUSH, speed);
            }
            else
            {
                controller.setAnimationSpeed(1.0D);
            }
            return state.setAndContinue(action.raw());
        }
        if (!this.isShowcase() && speed > RimefallAnims.MOVING)
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

    /** Its head and horn reach well past its hitbox: drawn whenever any of it could be on screen. */
    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(3.0D, 0.5D, 3.0D);
    }

    // ---------------------------------------------------------------- showcase Rimehorns

    /**
     * Spawns a frozen showcase Rimehorn: a male, a female or (calf) a female calf.
     *
     * @return it, already in the world, or null if it couldn't be made
     */
    @Nullable
    public static Rimehorn spawnShowcase(ServerLevel level, Vec3 feet, float yaw, boolean male, boolean calf)
    {
        Rimehorn rimehorn = ModEntities.RIMEHORN.get().create(level);
        if (rimehorn == null)
        {
            return null;
        }
        rimehorn.entityData.set(DATA_SHOWCASE, true);
        rimehorn.setMale(male);
        if (calf)
        {
            rimehorn.setAge(-RimefallMobTuning.RIMEHORN_GROW_TICKS);
        }
        RimefallAnimals.freeze(rimehorn, level, feet, yaw);
        return rimehorn;
    }

    /** Right-clicking a showcase Rimehorn plays its next animation; feeding a wild one works as usual. */
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
                    Component.translatable("dross.rimehorn." + (this.isBaby() ? "calf" : this.isMale() ? "male" : "female")),
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
        tag.putBoolean(TAG_MALE, this.isMale());
        if (this.breeder != null)
        {
            tag.putUUID(TAG_BREEDER, this.breeder);
        }
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
        this.setMale(tag.getBoolean(TAG_MALE));
        this.breeder = tag.hasUUID(TAG_BREEDER) ? tag.getUUID(TAG_BREEDER) : null;
        this.entityData.set(DATA_SHOWCASE, tag.getBoolean(TAG_SHOWCASE));
        this.penned = tag.getBoolean(TAG_PENNED);
        this.pen.load(tag);
        this.readPersistentAngerSaveData(this.level(), tag);
    }
}
