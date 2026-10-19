package com.reg21meme.dross.boss.rimefall;

import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
 * The Pond Skater (CLAUDE.md, "The plan", step 14): a water strider as big as a house, hostile, spawning on Rimefall's
 * ice. A spider's speed off the ice; on ice it skates, {@link RimefallMobTuning#SKATER_ICE_SPEED_BONUS} faster (the
 * user: "almost ocelot fast, they catch up to you instantly"). It rears up and stabs with its beak
 * ({@link RimefallMobTuning#SKATER_DAMAGE}). Its body is its hitbox, and each of its six legs has a small one of its
 * own ({@link PondSkaterPart}): hit a leg and you hit it.
 * <p>
 * Its look and animations are made by {@code tools/rimefall/mobs.py} ({@code rimemobs/pondskater.py}): idle, walk,
 * skate (its long middle legs rowing like oars) and attack.
 */
public class PondSkater extends Monster implements GeoEntity, RimefallAnimal
{
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(PondSkater.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_COUNT = SynchedEntityData.defineId(PondSkater.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHOWCASE = SynchedEntityData.defineId(PondSkater.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ON_ICE = SynchedEntityData.defineId(PondSkater.class, EntityDataSerializers.BOOLEAN);
    private static final String TAG_SHOWCASE = "Showcase";
    /** The skating speed bonus's id (never saved: it's put on and taken off as it steps on and off the ice). */
    private static final UUID ICE_SPEED_ID = UUID.fromString("3b6f0c2e-8d41-4f6a-9c2b-71e5a0d4c913");

    public static final String MOB = "pondskater";
    public static final RimefallAnims.Anim IDLE = RimefallAnims.loop(MOB, "idle", 0.0D);
    public static final RimefallAnims.Anim WALK = RimefallAnims.loop(MOB, "walk", 3.27D);
    /** One stroke of the oars covers its stroke length (1.88 blocks) in 0.9 s, gliding on after it. */
    public static final RimefallAnims.Anim SKATE = RimefallAnims.loop(MOB, "skate", 4.2D);
    public static final RimefallAnims.Anim ATTACK = RimefallAnims.once(MOB, "attack", RimefallMobTuning.STAB_TICKS);
    public static final List<RimefallAnims.Anim> ANIMS = List.of(IDLE, WALK, SKATE, ATTACK);
    private static final int BLEND_TICKS = 3;

    /**
     * Where each leg's little hitbox sits, in blocks from its feet (its own left is +x, its front -z): the middle and
     * hind legs' ankles and the raised front legs' hooks (from {@code rimedesigns/d08_pondskater.py}'s leg points).
     */
    private static final double[][] LEG_SPOTS = {
            {3.6D, 1.4D, 0.0D}, {-3.6D, 1.4D, 0.0D},          // the middle legs (the oars)
            {2.5D, 1.05D, 2.4D}, {-2.5D, 1.05D, 2.4D},        // the hind legs
            {0.6D, 1.25D, -2.4D}, {-0.6D, 1.25D, -2.4D}       // the front legs, raised like hooks
    };

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    private final PondSkaterPart[] legs;
    private int actionEnd = -1;
    private int seenActionCount;
    private int skateSoundCooldown;

    public PondSkater(EntityType<? extends PondSkater> type, Level level)
    {
        super(type, level);
        this.legs = new PondSkaterPart[LEG_SPOTS.length];
        for (int i = 0; i < this.legs.length; i++)
        {
            this.legs[i] = new PondSkaterPart(this, RimefallMobTuning.LEG_PART_WIDTH, RimefallMobTuning.LEG_PART_HEIGHT);
        }
        // Its legs' ids follow its own (like the Ender Dragon's parts).
        this.setId(ENTITY_COUNTER.getAndAdd(this.legs.length + 1) + 1);
        this.xpReward = 5;
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, RimefallMobTuning.SKATER_HEALTH)
                .add(Attributes.MOVEMENT_SPEED, RimefallMobTuning.SKATER_SPEED)
                .add(Attributes.ATTACK_DAMAGE, RimefallMobTuning.SKATER_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, RimefallMobTuning.SKATER_FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, -1);
        this.entityData.define(DATA_ACTION_COUNT, 0);
        this.entityData.define(DATA_SHOWCASE, false);
        this.entityData.define(DATA_ON_ICE, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new StabGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public boolean isShowcase()
    {
        return this.entityData.get(DATA_SHOWCASE);
    }

    /** True while it stands on ice (it skates there, much faster). */
    public boolean isOnIce()
    {
        return this.entityData.get(DATA_ON_ICE);
    }

    // ---------------------------------------------------------------- its legs' hitboxes

    @Override
    public void setId(int id)
    {
        super.setId(id);
        if (this.legs != null)
        {
            for (int i = 0; i < this.legs.length; i++)
            {
                this.legs[i].setId(id + i + 1);
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
        return this.legs;
    }

    public PondSkaterPart[] getLegs()
    {
        return this.legs;
    }

    /** Puts each leg's hitbox where that leg is (turned with its body). */
    private void placeLegs()
    {
        float yaw = this.yBodyRot * ((float) Math.PI / 180.0F);
        double cos = Mth.cos(yaw);
        double sin = Mth.sin(yaw);
        for (int i = 0; i < this.legs.length; i++)
        {
            double lx = LEG_SPOTS[i][0];
            double lz = LEG_SPOTS[i][2];
            // Its own left (+x) and front (-z) turned by its body's yaw into the world: facing (-sin, cos), its left
            // (cos, sin).
            double wx = lx * cos + lz * sin;
            double wz = lx * sin - lz * cos;
            PondSkaterPart leg = this.legs[i];
            double x = this.getX() + wx;
            double y = this.getY() + LEG_SPOTS[i][1] - RimefallMobTuning.LEG_PART_HEIGHT / 2.0D;
            double z = this.getZ() + wz;
            leg.xo = leg.getX();
            leg.yo = leg.getY();
            leg.zo = leg.getZ();
            leg.xOld = leg.getX();
            leg.yOld = leg.getY();
            leg.zOld = leg.getZ();
            leg.setPos(x, y, z);
        }
    }

    // ---------------------------------------------------------------- the ice

    @Override
    public void aiStep()
    {
        super.aiStep();
        if (!this.level().isClientSide)
        {
            // The block just under the middle of its feet (not "on the ground": that flickers off at every little hop,
            // and a mob that hasn't moved yet doesn't know it's down).
            BlockState below = this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.2D, this.getZ()));
            boolean ice = below.is(BlockTags.ICE);
            this.entityData.set(DATA_ON_ICE, ice);
            AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
            if (speed != null)
            {
                boolean has = speed.getModifier(ICE_SPEED_ID) != null;
                if (ice && !has)
                {
                    speed.addTransientModifier(new AttributeModifier(ICE_SPEED_ID, "Pond Skater skating on ice",
                            RimefallMobTuning.SKATER_ICE_SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
                else if (!ice && has)
                {
                    speed.removeModifier(ICE_SPEED_ID);
                }
            }
            if (ice && RimefallAnims.groundSpeed(this) > 1.0D && --this.skateSoundCooldown <= 0)
            {
                this.playSound(ModSounds.POND_SKATER_SKATE.get(), 0.6F, 0.9F + this.random.nextFloat() * 0.2F);
                this.skateSoundCooldown = 18;
            }
        }
    }

    @Override
    public void tick()
    {
        super.tick();
        this.placeLegs();
        if (!this.level().isClientSide && this.actionEnd >= 0 && this.tickCount >= this.actionEnd)
        {
            this.startAction(null);
        }
    }

    // ---------------------------------------------------------------- its stab

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

    /** Runs at its target and, close enough, rears up and stabs down with its beak. */
    private class StabGoal extends Goal
    {
        private int timer;
        private int cooldown;
        private boolean stabbing;
        private boolean hit;

        StabGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity target = PondSkater.this.getTarget();
            return !PondSkater.this.isShowcase() && target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.stabbing || this.canUse();
        }

        @Override
        public void stop()
        {
            PondSkater.this.getNavigation().stop();
            this.stabbing = false;
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            PondSkater self = PondSkater.this;
            LivingEntity target = self.getTarget();
            this.cooldown = Math.max(0, this.cooldown - 1);
            this.timer++;
            if (this.stabbing)
            {
                if (target != null && this.timer < RimefallMobTuning.STAB_HIT_TICK)
                {
                    Rimehorn.faceTowards(self, target.position());
                }
                if (!this.hit && this.timer >= RimefallMobTuning.STAB_HIT_TICK)
                {
                    this.hit = true;
                    if (target != null && target.isAlive() && Rimehorn.inReach(self, target, RimefallMobTuning.STAB_REACH + 0.5D))
                    {
                        self.doHurtTarget(target);
                    }
                }
                if (this.timer >= RimefallMobTuning.STAB_TICKS)
                {
                    this.stabbing = false;
                    this.cooldown = RimefallMobTuning.STAB_COOLDOWN;
                }
                return;
            }
            if (target == null)
            {
                return;
            }
            self.getLookControl().setLookAt(target, 30.0F, 30.0F);
            if (this.cooldown == 0 && Rimehorn.inReach(self, target, RimefallMobTuning.STAB_REACH))
            {
                this.stabbing = true;
                this.hit = false;
                this.timer = 0;
                self.getNavigation().stop();
                self.startAction(ATTACK);
                self.playSound(ModSounds.POND_SKATER_ATTACK.get(), 1.0F, self.getVoicePitch());
                return;
            }
            if (this.timer % 8 == 1 || self.getNavigation().isDone())
            {
                self.getNavigation().moveTo(target, 1.0D);
            }
        }
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.POND_SKATER_AMBIENT.get();
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.POND_SKATER_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.POND_SKATER_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        if (!this.isOnIce())
        {
            this.playSound(ModSounds.POND_SKATER_STEP.get(), 0.35F, 1.0F);
        }
    }

    // ---------------------------------------------------------------- animation (GeckoLib)

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
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new AnimationController<>(this, "main", BLEND_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<PondSkater> state)
    {
        AnimationController<PondSkater> controller = state.getController();
        int count = this.entityData.get(DATA_ACTION_COUNT);
        if (count != this.seenActionCount)
        {
            this.seenActionCount = count;
            controller.forceAnimationReset();
        }
        RimefallAnims.Anim action = this.action();
        if (action != null)
        {
            controller.setAnimationSpeed(1.0D);
            return state.setAndContinue(action.raw());
        }
        double speed = RimefallAnims.groundSpeed(this);
        if (!this.isShowcase() && speed > RimefallAnims.MOVING)
        {
            RimefallAnims.Anim gait = this.isOnIce() ? SKATE : WALK;
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

    /** Its legs reach about 6 blocks out each side: drawn whenever any of it could be on screen. */
    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(6.0D, 1.0D, 6.0D);
    }

    // ---------------------------------------------------------------- showcase Pond Skaters

    @Nullable
    public static PondSkater spawnShowcase(ServerLevel level, Vec3 feet, float yaw)
    {
        PondSkater skater = ModEntities.POND_SKATER.get().create(level);
        if (skater == null)
        {
            return null;
        }
        skater.entityData.set(DATA_SHOWCASE, true);
        RimefallAnimals.freeze(skater, level, feet, yaw);
        return skater;
    }

    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (!this.isShowcase() || hand != InteractionHand.MAIN_HAND)
        {
            return super.mobInteract(player, hand);
        }
        if (!this.level().isClientSide)
        {
            RimefallAnims.Anim now = this.action();
            int next = (ANIMS.indexOf(now == null ? IDLE : now) + 1) % ANIMS.size();
            this.playShowcase(ANIMS.get(next).name());
            player.displayClientMessage(Component.translatable("dross.rimefall_mob.showcase.playing",
                    Component.translatable("entity.dross.pond_skater"), ANIMS.get(next).name()), true);
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
    protected boolean shouldDespawnInPeaceful()
    {
        return !this.isShowcase();
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return !this.isShowcase() && super.removeWhenFarAway(distance);
    }

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        if (this.isShowcase())
        {
            tag.putBoolean(TAG_SHOWCASE, true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_SHOWCASE, tag.getBoolean(TAG_SHOWCASE));
    }
}
