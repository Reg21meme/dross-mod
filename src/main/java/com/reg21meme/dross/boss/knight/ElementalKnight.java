package com.reg21meme.dross.boss.knight;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.SteadyController;
import com.reg21meme.dross.boss.golem.ColossusRock;
import com.reg21meme.dross.boss.golem.LavaPools;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.ratking.PlagueBall;
import com.reg21meme.dross.boss.ratking.PoisonCloud;
import com.reg21meme.dross.boss.rimefall.RimefallAnims;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModSounds;
import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.registries.RegistryObject;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
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
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One of the ten elemental knights ({@link KnightKind}; CLAUDE.md step 18): two of each element will guard a broken
 * portal in that element's Overworld biomes, "the boss mob before you interact with the mod". Hostile, they never
 * despawn, and each fights with two moves of its own (the user's, November 2026: "you don't have to do too much, 2
 * attacks"), every number in {@link KnightTuning}:
 * <ul>
 *   <li><b>9C Thawed axeman</b>: a wide swing of his axe (chills you); the smash, his axe into the ground and the ice
 *       bursting up in a line to you, a big spike under you at its end (the Alpha Moose's clear ice: thrown up, slowed,
 *       frosted).</li>
 *   <li><b>9D Thawed spearman</b>: a long thrust; the shield wall, his shield up (he takes a third of the damage) while
 *       he pokes at you five times.</li>
 *   <li><b>P1 Blight Warden</b>: a ball of poison from his staff (the Rat King's); the blight rain, poison raining round
 *       him a while.</li>
 *   <li><b>P3 Sporebound</b>: his scythe swept round him (poisons); the spore burst, the Blightcap on his head puffing out
 *       a cloud that sickens, blinds and poisons, and leaves clouds of poison.</li>
 *   <li><b>F1B Cinder Knight</b>: his hammer swung (sets you burning); the slam, a shockwave of fire, a ring of fire left
 *       round him.</li>
 *   <li><b>F5 Flamehand</b>: fireballs from his fists; the great fireball, called up over his head and hurled, bursting
 *       where it lands.</li>
 *   <li><b>T3A Wreck Knight</b>: his anchor thrown on its chain ({@link KnightAnchor}: it hooks you and hauls you to
 *       him); the anchor slam, swung down in front of him with a crack of lightning (stuns).</li>
 *   <li><b>T6 Stormblade</b>: two quick cuts; the lightning dash ("like the Flash"), straight through you and past,
 *       lightning left behind him that stings.</li>
 *   <li><b>E3 Geode Knight</b>: his pick; the crystal burst, rings of amethyst bursting up round him and one under you.</li>
 *   <li><b>E4 Rockfall Knight</b>: his boulder thrown (the Colossus's rock); the rockfall, his boulder brought down on
 *       the ground and rocks raining down round you.</li>
 * </ul>
 * Each element shrugs off its own: fire never hurts the fire knights, the ice knights don't freeze or slow, the poison
 * knights aren't poisoned, lightning never hurts the thunder knights and the earth knights take no fall damage. No
 * boss bar (the user, November 18, 2026: "no boss bars please for the knights"); hurt, it calls the other knights
 * near to help. On show ({@link #setShowcase}) they stand frozen and play their moves without effect.
 */
public class ElementalKnight extends Monster implements GeoEntity
{
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(ElementalKnight.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_COUNT = SynchedEntityData.defineId(ElementalKnight.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHOWCASE = SynchedEntityData.defineId(ElementalKnight.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_ANCHOR_OUT = SynchedEntityData.defineId(ElementalKnight.class, EntityDataSerializers.BOOLEAN);
    private static final String TAG_SHOWCASE = "Showcase";
    private static final String TAG_RUIN = "Ruin";
    private static final String TAG_RUIN_HOME = "RuinHome";
    private static final String TAG_RUIN_SLOT = "RuinSlot";
    /** (client) Its walk shows once it moves faster than {@link RimefallAnims#MOVING} (blocks a second), and goes back to
     *  its idle only once it's been slower than this for {@link #WALK_HOLD_TICKS} ticks: one speed for both flickered it
     *  between the two (restarting each) as it slowed down or was nudged (the user, November 18, 2026: "jittery"). */
    private static final double WALK_STILL = 0.1D;
    private static final int WALK_HOLD_TICKS = 5;
    /** (client) How quickly the speed its walk is played at follows how fast it really moves (a share a tick): its
     *  position comes from the server every few ticks, so the speed worked out tick by tick wobbles. */
    private static final double WALK_SPEED_EASE = 0.3D;
    /** The anchor's flag is cleared this long after a throw at the latest (in case its anchor was lost). */
    private static final int ANCHOR_SAFETY = 140;

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private final KnightKind kind;
    private final List<RimefallAnims.Anim> anims;
    private int seenActionCount = -1;
    // ---- (client) its gait: whether its walk shows and how fast it really moves, worked out once a tick
    private int gaitTick = -1;
    private boolean walkShown;
    private int stillTicks;
    private double shownSpeed;

    // ---- the move going on (server)
    /** The move going on: 0 its first, 1 its second, -1 none (or one on show, which does nothing). */
    private int move = -1;
    private int moveStart;
    private int moveEnd;
    private final int[] readyAt = new int[2];
    private int nextMoveAt;
    private int anchorThrownAt = -10_000;
    /** Things it's to do a few ticks from now (the spikes along the smash's line, the crystals, the rocks). */
    private final List<Later> later = new ArrayList<>();

    // ---- the Blight Warden's rain
    private int rainUntil = -1;
    private Vec3 rainAt = Vec3.ZERO;

    // ---- the Stormblade's dash and its trail
    private Vec3 dashWay = Vec3.ZERO;
    private double dashLeft;
    private boolean dashing;
    private final Set<Integer> dashHit = new HashSet<>();
    private final List<TrailPoint> trail = new ArrayList<>();
    private final Map<Integer, Integer> trailStungAt = new HashMap<>();

    private record Later(int at, Runnable run)
    {
    }

    private record TrailPoint(Vec3 at, int until)
    {
    }

    public ElementalKnight(EntityType<? extends ElementalKnight> type, Level level, KnightKind kind)
    {
        super(type, level);
        this.kind = kind;
        this.anims = KnightAnims.ANIMS.getOrDefault(kind.model, List.of());
        this.setMaxUpStep(1.0F);
        this.xpReward = KnightTuning.XP;
    }

    public static AttributeSupplier.Builder createAttributes(KnightKind kind)
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, kind.health)
                .add(Attributes.ARMOR, kind.armor)
                .add(Attributes.MOVEMENT_SPEED, kind.speed)
                .add(Attributes.KNOCKBACK_RESISTANCE, kind.knockbackResistance)
                .add(Attributes.ATTACK_DAMAGE, KnightTuning.PLAIN_HIT)
                .add(Attributes.FOLLOW_RANGE, KnightTuning.FOLLOW_RANGE);
    }

    public KnightKind kind()
    {
        return this.kind;
    }

    /** A damage source of {@code type} (Minecraft's own kinds: fire, a mob's blow...). */
    static DamageSource typed(Level level, ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity causing)
    {
        return new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type),
                direct, causing);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_ACTION, -1);
        this.entityData.define(DATA_ACTION_COUNT, 0);
        this.entityData.define(DATA_SHOWCASE, false);
        this.entityData.define(DATA_ANCHOR_OUT, false);
    }

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(2, new FightGoal());
        this.goalSelector.addGoal(6, new WaterAvoidingRandomStrollGoal(this, 0.8D));
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 10.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, ElementalKnight.class).setAlertOthers(ElementalKnight.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
        this.targetSelector.addGoal(3, new NearestAttackableTargetGoal<>(this, IronGolem.class, true));
    }

    // ---------------------------------------------------------------- on show

    /** On show: frozen (no AI), never pushed, can't be hurt except by {@code /kill}; it plays its moves without effect. */
    public void setShowcase(boolean showcase)
    {
        this.entityData.set(DATA_SHOWCASE, showcase);
        this.setNoAi(showcase);
        this.setPersistenceRequired();
    }

    public boolean isShowcase()
    {
        return this.entityData.get(DATA_SHOWCASE);
    }

    // ---------------------------------------------------------------- a ruin's guard (boss/knight/ruin/KnightRuinKeeper)

    /** The ruin it guards (its id), its spot there and which of its two knights it is; null if it guards none. */
    @Nullable
    private String ruin;
    @Nullable
    private BlockPos ruinHome;
    private int ruinSlot;

    /** Makes it the guard of a ruin: it keeps near its spot there (walking back when it has nothing to fight) and gives
     * up a foe {@link com.reg21meme.dross.boss.knight.ruin.KnightRuinTuning#LEASH} blocks from it. */
    public void guard(String ruin, BlockPos home, int slot)
    {
        this.ruin = ruin;
        this.ruinHome = home.immutable();
        this.ruinSlot = slot;
        this.restrictTo(this.ruinHome, (int) com.reg21meme.dross.boss.knight.ruin.KnightRuinTuning.LEASH);
        this.setPersistenceRequired();
    }

    @Nullable
    public String ruin()
    {
        return this.ruin;
    }

    @Nullable
    public BlockPos ruinHome()
    {
        return this.ruinHome;
    }

    public int ruinSlot()
    {
        return this.ruinSlot;
    }

    /** Every half second: a foe too far from its ruin is let go, and with none it walks back to its spot. */
    private void keepToRuin()
    {
        if (this.ruinHome == null || this.tickCount % 10 != 0)
        {
            return;
        }
        double leash = com.reg21meme.dross.boss.knight.ruin.KnightRuinTuning.LEASH;
        LivingEntity target = this.getTarget();
        if (target != null && target.distanceToSqr(Vec3.atBottomCenterOf(this.ruinHome)) > leash * leash)
        {
            this.setTarget(null);
            target = null;
        }
        double back = com.reg21meme.dross.boss.knight.ruin.KnightRuinTuning.HOME_RETURN;
        if (target == null && !this.isActing() && this.distanceToSqr(Vec3.atBottomCenterOf(this.ruinHome)) > back * back)
        {
            this.getNavigation().moveTo(this.ruinHome.getX() + 0.5D, this.ruinHome.getY(), this.ruinHome.getZ() + 0.5D, 1.0D);
        }
    }

    /** On show: plays one of its animations by name (idle, walk, or a move's), nothing happening. False if it hasn't it. */
    public boolean showcasePlay(String name)
    {
        RimefallAnims.Anim anim = RimefallAnims.named(this.anims, name);
        if (anim == null)
        {
            return false;
        }
        this.move = -1;
        this.setAction(anim);
        this.moveStart = this.tickCount;
        this.moveEnd = this.tickCount + (anim.ticks() > 0 ? anim.ticks() : 40);
        return true;
    }

    @Override
    public boolean isPushable()
    {
        return !this.isShowcase() && super.isPushable();
    }

    // ---------------------------------------------------------------- the anchor (T3A)

    /** True while the Wreck Knight's anchor is out of his hand (it isn't drawn on him then). */
    public boolean isAnchorOut()
    {
        return this.entityData.get(DATA_ANCHOR_OUT);
    }

    void setAnchorOut(boolean out)
    {
        this.entityData.set(DATA_ANCHOR_OUT, out);
    }

    // ---------------------------------------------------------------- moves

    private void setAction(@Nullable RimefallAnims.Anim anim)
    {
        this.entityData.set(DATA_ACTION, anim == null ? -1 : this.anims.indexOf(anim));
        this.entityData.set(DATA_ACTION_COUNT, this.entityData.get(DATA_ACTION_COUNT) + 1);
    }

    @Nullable
    private RimefallAnims.Anim action()
    {
        int index = this.entityData.get(DATA_ACTION);
        return index >= 0 && index < this.anims.size() ? this.anims.get(index) : null;
    }

    /** True while one of its moves (or an animation on show) is going on. */
    public boolean isActing()
    {
        return this.tickCount < this.moveEnd;
    }

    /** True if move {@code index} is ready (cooled down). */
    public boolean ready(int index)
    {
        return this.tickCount >= this.readyAt[index];
    }

    /** Starts move {@code index} (0 its first, 1 its second) at its target. (Public for the tests.) */
    public void startMove(int index)
    {
        KnightKind.Move m = this.kind.move(index);
        RimefallAnims.Anim anim = RimefallAnims.named(this.anims, m.anim());
        if (anim == null)
        {
            return;
        }
        this.move = index;
        this.moveStart = this.tickCount;
        int length = anim.ticks() > 0 ? anim.ticks() : KnightTuning.DASH_MAX_TICKS + KnightTuning.DASH_RECOVER;
        this.moveEnd = this.tickCount + length;
        this.readyAt[index] = this.tickCount + m.cooldown();
        this.nextMoveAt = this.moveEnd + KnightTuning.PAUSE;
        this.setAction(anim);
        this.getNavigation().stop();
        this.onStart();
    }

    private String moveAnim()
    {
        return this.kind.move(this.move).anim();
    }

    /** The server's side of the move going on, every tick: its blows land at their animation's moments. */
    private void tickMove()
    {
        if (this.move < 0)
        {
            return;
        }
        int t = this.tickCount - this.moveStart;
        String anim = this.moveAnim();
        LivingEntity target = this.getTarget();
        boolean lockedFace = this.dashing;
        int firstHit = KnightAnims.moment(this.kind.model, anim, "hits.0");
        int release = KnightAnims.moment(this.kind.model, anim, "release");
        int lockAt = Math.max(firstHit, release) - 3;
        if (target != null && !lockedFace && (lockAt < 0 || t < lockAt || this.guarding()))
        {
            this.turnTo(target.position(), 18.0F);
        }
        for (int k = 0; k < 8; k++)
        {
            if (KnightAnims.moment(this.kind.model, anim, "hits." + k) == t)
            {
                this.onHit(k);
            }
        }
        if (release == t)
        {
            this.onRelease();
        }
        this.onMoveTick(t);
        if (!this.isActing())
        {
            this.move = -1;
            this.dashing = false;
        }
    }

    /** True while the Thawed spearman's shield is up. */
    public boolean guarding()
    {
        if (this.kind != KnightKind.THAWED_SPEAR || this.move != 1)
        {
            return false;
        }
        int t = this.tickCount - this.moveStart;
        return t >= KnightAnims.moment(this.kind.model, "shield_wall", "guard.0")
                && t <= KnightAnims.moment(this.kind.model, "shield_wall", "guard.1");
    }

    private void onStart()
    {
        LivingEntity target = this.getTarget();
        // his grunt (for his quick first move only now and then, it comes so often) and the move's wind-up
        if (this.move == 1 || this.random.nextBoolean())
        {
            this.play("effort", 1.0F);
        }
        String windup = this.windup();
        if (windup != null)
        {
            this.play(windup, 1.4F);
        }
        switch (this.kind)
        {
            case STORMBLADE ->
            {
                if (this.move == 1 && target != null)
                {
                    Vec3 to = target.position().subtract(this.position());
                    Vec3 flat = new Vec3(to.x, 0.0D, to.z);
                    this.dashWay = flat.lengthSqr() < 1.0E-4D ? this.forward() : flat.normalize();
                    this.dashLeft = flat.length() + KnightTuning.DASH_PAST;
                    this.dashing = true;
                    this.dashHit.clear();
                    this.turnTo(target.position(), 180.0F);
                    this.play("dash", 1.4F);
                }
            }
            case THAWED_SPEAR ->
            {
                if (this.move == 1)
                {
                    this.play("guard", 1.2F);
                }
            }
            case BLIGHT_WARDEN, FLAMEHAND ->
            {
                if (this.move == 1)
                {
                    this.play(this.kind == KnightKind.FLAMEHAND ? "charge" : "rain_call", 1.5F);
                }
            }
            default ->
            {
            }
        }
    }

    /** A blow landing (the {@code k}th of its animation's hits). */
    private void onHit(int k)
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        LivingEntity target = this.getTarget();
        switch (this.kind)
        {
            case THAWED_AXE ->
            {
                if (this.move == 0)
                {
                    this.play("swing", 1.2F);
                    this.sweepParticles(ParticleTypes.SNOWFLAKE, KnightTuning.AXE_SWING_REACH);
                    KnightEffects.arc(level, this, KnightTuning.AXE_SWING_REACH * 0.75D, 1.7D, 75.0D, -75.0D, 14,
                            ParticleTypes.SNOWFLAKE, 0.03D);
                    for (LivingEntity hit : this.strike(KnightTuning.AXE_SWING_ARC, KnightTuning.AXE_SWING_REACH,
                            KnightTuning.AXE_SWING_DAMAGE, KnightTuning.AXE_SWING_KNOCK))
                    {
                        hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KnightTuning.AXE_SWING_CHILL, 0), this);
                    }
                }
                else
                {
                    this.iceSmash(level, target);
                }
            }
            case THAWED_SPEAR ->
            {
                if (this.move == 0)
                {
                    this.play("thrust", 1.2F);
                    KnightEffects.thrustStreak(level, this, KnightTuning.THRUST_REACH);
                    for (LivingEntity hit : this.strike(KnightTuning.THRUST_ARC, KnightTuning.THRUST_REACH,
                            KnightTuning.THRUST_DAMAGE, KnightTuning.THRUST_KNOCK))
                    {
                        hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KnightTuning.THRUST_SLOW_TICKS,
                                KnightTuning.THRUST_SLOW_LEVEL), this);
                    }
                }
                else
                {
                    this.sound(knightSound(this.kind, "poke"), 1.0F, 0.95F + 0.05F * k);
                    Vec3 tip = this.at(KnightTuning.POKE_REACH, 0.4D, 2.5D);
                    level.sendParticles(ParticleTypes.SNOWFLAKE, tip.x, tip.y, tip.z, 5, 0.2D, 0.2D, 0.2D, 0.05D);
                    for (LivingEntity hit : this.strike(KnightTuning.POKE_ARC, KnightTuning.POKE_REACH,
                            KnightTuning.POKE_DAMAGE, KnightTuning.POKE_KNOCK))
                    {
                        hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KnightTuning.POKE_SLOW_TICKS, 0),
                                this);
                    }
                }
            }
            case BLIGHT_WARDEN ->
            {
                // the rain (his first move is a cast: its release)
                this.rainUntil = this.tickCount + KnightTuning.RAIN_TICKS;
                this.rainAt = this.position();
                this.play("rain", 2.0F);
                // his staff driven into the ground: a ring of poison rolling out from it
                KnightEffects.shockwave(level, this, this.at(1.2D, 0.4D, 0.0D), 3.5D,
                        new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_POISON_RAIN), 1.5F));
            }
            case SPOREBOUND ->
            {
                if (this.move == 0)
                {
                    this.play("reap", 1.3F);
                    KnightEffects.arc(level, this, KnightTuning.REAP_REACH * 0.8D, 1.6D, 80.0D, -80.0D, 16,
                            new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_SPORES), 1.4F), 0.0D);
                    this.sweepParticles(new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_SPORES), 1.4F),
                            KnightTuning.REAP_REACH);
                    for (LivingEntity hit : this.strike(KnightTuning.REAP_ARC, KnightTuning.REAP_REACH,
                            KnightTuning.REAP_DAMAGE, KnightTuning.REAP_KNOCK))
                    {
                        hit.addEffect(new MobEffectInstance(MobEffects.POISON, KnightTuning.REAP_POISON, 0), this);
                    }
                }
                else
                {
                    this.sporeBurst(level);
                }
            }
            case CINDER ->
            {
                if (this.move == 0)
                {
                    this.play("swing", 1.4F);
                    this.sweepParticles(ParticleTypes.FLAME, KnightTuning.HAMMER_REACH);
                    KnightEffects.arc(level, this, KnightTuning.HAMMER_REACH * 0.75D, 1.6D, 75.0D, -75.0D, 16,
                            ParticleTypes.FLAME, 0.02D);
                    for (LivingEntity hit : this.strike(KnightTuning.HAMMER_ARC, KnightTuning.HAMMER_REACH,
                            KnightTuning.HAMMER_DAMAGE, KnightTuning.HAMMER_KNOCK))
                    {
                        hit.setSecondsOnFire(KnightTuning.HAMMER_FIRE_SECONDS);
                    }
                    this.hammerFire(level);
                }
                else
                {
                    this.fireSlam(level);
                }
            }
            case WRECK ->
            {
                // the anchor slam (his first move is the throw: its release)
                // (where the anchor's crown comes down: ahead of him and a little to his right)
                Vec3 at = this.at(2.2D, 0.8D, 0.0D);
                this.play("anchor_slam", 2.4F);
                LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                if (bolt != null)
                {
                    bolt.moveTo(at.x, this.groundY(at.x, at.z, this.getY()), at.z);
                    bolt.setVisualOnly(true);
                    level.addFreshEntity(bolt);
                }
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, this.getY() + 0.5D, at.z, 40, 1.2D, 0.4D, 1.2D, 0.4D);
                KnightEffects.shockwave(level, this, at, KnightTuning.ANCHOR_SLAM_REACH + 1.0D, ParticleTypes.ELECTRIC_SPARK);
                level.sendParticles(new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_SPARK), 2.0F), at.x,
                        this.getY() + 0.3D, at.z, 30, 1.5D, 0.2D, 1.5D, 0.0D);
                for (LivingEntity hit : this.strike(KnightTuning.ANCHOR_SLAM_ARC, KnightTuning.ANCHOR_SLAM_REACH,
                        KnightTuning.ANCHOR_SLAM_DAMAGE, KnightTuning.ANCHOR_SLAM_KNOCK))
                {
                    hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KnightTuning.ANCHOR_SLAM_STUN, 1), this);
                }
            }
            case STORMBLADE ->
            {
                this.sound(knightSound(this.kind, "slash"), 1.0F, 1.0F + 0.08F * k);
                // arcs of lightning along each cut: the X (each blade from out at its side across in front), then the
                // wide cut (both blades flung out from the middle)
                DustParticleOptions bright = new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_SPARK_BRIGHT), 1.3F);
                double[][] cuts = k == 0 ? new double[][] {{-75.0D, 25.0D, 2.3D}, {75.0D, -25.0D, 1.5D}}
                        : new double[][] {{-5.0D, -85.0D, 1.9D}, {5.0D, 85.0D, 1.9D}};
                for (double[] c : cuts)
                {
                    KnightEffects.arc(level, this, KnightTuning.SLASH_REACH * 0.7D, c[2], c[0], c[1], 12,
                            ParticleTypes.ELECTRIC_SPARK, 0.05D);
                    KnightEffects.arc(level, this, KnightTuning.SLASH_REACH * 0.7D, c[2], c[0], c[1], 6, bright, 0.0D);
                }
                if (k == 1)
                {
                    Vec3 mid = this.at(1.6D, 0.0D, 1.9D);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, mid.x, mid.y, mid.z, 30, 0.6D, 0.4D, 0.6D, 0.35D);
                }
                this.sweepParticles(ParticleTypes.ELECTRIC_SPARK, KnightTuning.SLASH_REACH);
                for (LivingEntity hit : this.strike(KnightTuning.SLASH_ARC, KnightTuning.SLASH_REACH,
                        KnightTuning.SLASH_DAMAGE, KnightTuning.SLASH_KNOCK))
                {
                    hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KnightTuning.SLASH_SLOW_TICKS, 0), this);
                }
            }
            case GEODE ->
            {
                if (this.move == 0)
                {
                    this.play("pick", 1.3F);
                    // (where its spike bites the ground: rock and amethyst thrown up, a small ring of dust)
                    Vec3 bite = this.at(KnightTuning.PICK_BITE_FORWARD, KnightTuning.PICK_BITE_RIGHT, 0.0D);
                    double gy = this.groundY(bite.x, bite.z, this.getY());
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
                            bite.x, gy + 0.2D, bite.z, 18, 0.3D, 0.25D, 0.3D, 0.2D);
                    level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                            level.getBlockState(BlockPos.containing(bite.x, gy - 0.5D, bite.z))), bite.x, gy + 0.2D, bite.z,
                            22, 0.4D, 0.2D, 0.4D, 0.25D);
                    KnightEffects.shockwave(level, this, new Vec3(bite.x, gy, bite.z), 2.2D,
                            new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_AMETHYST_DUST), 1.3F));
                    this.strike(KnightTuning.PICK_ARC, KnightTuning.PICK_REACH, KnightTuning.PICK_DAMAGE,
                            KnightTuning.PICK_KNOCK);
                }
                else
                {
                    this.crystalBurst(level, target);
                }
            }
            case ROCKFALL ->
            {
                // the rockfall (his first move is the throw: its release)
                this.rockfall(level, target);
            }
            case FLAMEHAND ->
            {
            }
        }
    }

    /** Something leaving its hand (its animation's release). */
    private void onRelease()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        LivingEntity target = this.getTarget();
        Vec3 aimAt = target != null ? target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D)
                : this.position().add(this.forward().scale(12.0D)).add(0.0D, 1.0D, 0.0D);
        switch (this.kind)
        {
            case BLIGHT_WARDEN ->
            {
                // (from the globe at the end of his staff as he whips it forward)
                Vec3 from = this.at(3.0D, 0.8D, 4.7D);
                double d = from.distanceTo(aimAt);
                int ticks = Mth.clamp((int) Math.round(d * KnightTuning.CAST_TICKS_PER_BLOCK), KnightTuning.CAST_TICKS_MIN,
                        KnightTuning.CAST_TICKS_MAX);
                PlagueBall ball = PlagueBall.cast(level, this, from);
                ball.aim(target != null ? this.lead(target, ticks).add(0.0D, 0.3D, 0.0D) : aimAt, ticks);
                level.addFreshEntity(ball);
                this.play("cast", 1.3F);
            }
            case FLAMEHAND ->
            {
                boolean great = this.move == 1;
                // (from where it shows as it leaves him: the orb over his head, his right fist at the end of the punch)
                Vec3 from = great ? this.at(1.5D, 0.0D, 5.3D) : this.at(2.2D, 0.1D, 3.4D);
                Vec3 to = aimAt;
                if (target != null)
                {
                    int ticks = (int) Math.round(from.distanceTo(aimAt)
                            / (great ? KnightTuning.GREAT_SPEED : KnightTuning.FIREBALL_SPEED));
                    to = this.lead(target, ticks).add(0.0D, target.getBbHeight() * (great ? 0.3D : 0.5D), 0.0D);
                }
                level.addFreshEntity(KnightFireball.shoot(level, this, from, to, great));
                this.play(great ? "great_fireball" : "fireball", great ? 2.0F : 1.2F);
            }
            case WRECK ->
            {
                // (from where the anchor leaves his hands, over his right shoulder)
                Vec3 from = this.at(0.2D, 1.1D, 4.8D);
                level.addFreshEntity(KnightAnchor.thrown(level, this, from, aimAt));
                this.setAnchorOut(true);
                this.anchorThrownAt = this.tickCount;
                this.play("anchor_throw", 1.8F);
            }
            case ROCKFALL ->
            {
                // (where the boulder is in his hands as he lets it go: 2.3 blocks ahead of him and 4.6 up)
                Vec3 from = this.at(2.3D, 0.0D, 4.6D);
                double d = from.distanceTo(aimAt);
                int ticks = Mth.clamp((int) Math.round(d * KnightTuning.BOULDER_TICKS_PER_BLOCK),
                        KnightTuning.BOULDER_TICKS_MIN, KnightTuning.BOULDER_TICKS_MAX);
                ColossusRock rock = ColossusRock.boulder(level, this, from, KnightTuning.BOULDER_DAMAGE,
                        KnightTuning.BOULDER_SIZE);
                rock.aim(target != null ? this.lead(target, ticks).add(0.0D, 0.3D, 0.0D) : aimAt, ticks);
                level.addFreshEntity(rock);
                this.play("throw", 1.6F);
            }
            default ->
            {
            }
        }
    }

    /** Every tick of a move (the dash moves him; the shield wall's frost). */
    private void onMoveTick(int t)
    {
        if (this.level() instanceof ServerLevel lv)
        {
            String anim = this.moveAnim();
            int hit = KnightAnims.moment(this.kind.model, anim, "hits.0");
            int rel = KnightAnims.moment(this.kind.model, anim, "release");
            int until = hit >= 0 ? hit : rel;
            if (until > 0 && t < until)
            {
                KnightEffects.windup(lv, this, this.move, t, until);
            }
        }
        if (this.kind == KnightKind.STORMBLADE && this.move == 1 && this.dashing)
        {
            this.dashStep(t);
        }
        if (this.guarding() && this.level() instanceof ServerLevel level && t % 4 == 0)
        {
            Vec3 at = this.at(1.0D, 0.6D, 2.0D);
            level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 2, 0.4D, 0.8D, 0.4D, 0.01D);
        }
    }

    // ---------------------------------------------------------------- the moves' effects

    private void iceSmash(ServerLevel level, @Nullable LivingEntity target)
    {
        this.play("smash", 1.6F);
        Vec3 start = this.at(KnightTuning.SMASH_FORWARD, KnightTuning.SMASH_RIGHT, 0.0D);
        KnightEffects.shockwave(level, this, start, 3.0D, ParticleTypes.SNOWFLAKE);
        Vec3 end = target != null && target.distanceTo(this) <= KnightTuning.SMASH_LINE_MAX
                ? target.position() : this.position().add(this.forward().scale(8.0D));
        Vec3 line = new Vec3(end.x - start.x, 0.0D, end.z - start.z);
        int steps = Math.max(1, (int) Math.floor(line.length() / KnightTuning.SMASH_STEP));
        BlockState ice = ModBlocks.HUNT_ICE_SPIKE.get().defaultBlockState();
        for (int i = 0; i <= steps; i++)
        {
            Vec3 p = start.add(line.scale(i / (double) steps));
            this.later(i, () ->
            {
                Vec3 at = new Vec3(p.x, this.groundY(p.x, p.z, this.getY()), p.z);
                float h = KnightTuning.SMASH_SMALL_MIN + this.random.nextFloat()
                        * (KnightTuning.SMASH_SMALL_MAX - KnightTuning.SMASH_SMALL_MIN);
                KnightSpikes.spike(level, ice, at, h * 0.9F, h, KnightSpikes.lean(this.random, 22.0D),
                        this.random.nextFloat() * 360.0F, KnightTuning.SPIKE_LIFE);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                        at.x, at.y + 0.3D, at.z, 8, 0.3D, 0.2D, 0.3D, 0.1D);
                this.sound(knightSound(this.kind, "spike"), 0.9F, 0.9F + this.random.nextFloat() * 0.3F);
                for (LivingEntity hit : this.near(at, KnightTuning.SMASH_SMALL_HIT))
                {
                    this.hurtFoe(hit, KnightTuning.SMASH_SMALL_DAMAGE);
                    hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KnightTuning.AXE_SWING_CHILL, 0), this);
                }
            });
        }
        Vec3 big = end;
        this.later(steps + 3, () ->
        {
            Vec3 at = new Vec3(big.x, this.groundY(big.x, big.z, this.getY()), big.z);
            float h = KnightTuning.SMASH_BIG_HEIGHT;
            KnightSpikes.spike(level, ice, at, h * 0.55F, h, KnightSpikes.lean(this.random, 5.0D),
                    this.random.nextFloat() * 360.0F, KnightTuning.SPIKE_LIFE);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                    at.x, at.y + 0.8D, at.z, 40, 0.5D, 0.8D, 0.5D, 0.25D);
            level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y + 1.0D, at.z, 25, 0.6D, 0.8D, 0.6D, 0.05D);
            this.play("big_spike", 1.8F);
            for (LivingEntity hit : this.near(at, KnightTuning.SMASH_BIG_HIT))
            {
                this.hurtFoe(hit, KnightTuning.SMASH_BIG_DAMAGE);
                this.launch(hit, KnightTuning.SMASH_BIG_LAUNCH);
                hit.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, KnightTuning.SMASH_BIG_SLOW, 1), this);
                if (hit.canFreeze())
                {
                    hit.setTicksFrozen(Math.max(hit.getTicksFrozen(), KnightTuning.SMASH_FROST));
                }
            }
        });
    }

    private void sporeBurst(ServerLevel level)
    {
        Vec3 head = this.position().add(0.0D, 4.6D, 0.0D);
        double r = KnightTuning.SPORE_RADIUS;
        this.play("spore_burst", 2.0F);
        level.sendParticles(new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_SPORES), 2.5F), head.x, head.y,
                head.z, 160, r * 0.45D, 1.5D, r * 0.45D, 0.0D);
        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, head.x, head.y - 2.0D, head.z, 80, r * 0.5D, 1.5D, r * 0.5D, 0.0D);
        level.sendParticles(ParticleTypes.ITEM_SLIME, head.x, head.y, head.z, 40, 1.0D, 0.6D, 1.0D, 0.2D);
        for (LivingEntity hit : this.near(this.position().add(0.0D, 1.0D, 0.0D), r))
        {
            this.hurtFoe(hit, KnightTuning.SPORE_DAMAGE);
            hit.addEffect(new MobEffectInstance(MobEffects.CONFUSION, KnightTuning.SPORE_NAUSEA, 0), this);
            hit.addEffect(new MobEffectInstance(MobEffects.POISON, KnightTuning.SPORE_POISON, 1), this);
            hit.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, KnightTuning.SPORE_BLIND, 0), this);
            hit.knockback(KnightTuning.SPORE_PUSH, this.getX() - hit.getX(), this.getZ() - hit.getZ());
        }
        for (int i = 0; i < KnightTuning.SPORE_CLOUDS; i++)
        {
            double a = 2.0D * Math.PI * (i + this.random.nextDouble() * 0.5D) / KnightTuning.SPORE_CLOUDS;
            double x = this.getX() + Math.cos(a) * KnightTuning.SPORE_CLOUD_OUT;
            double z = this.getZ() + Math.sin(a) * KnightTuning.SPORE_CLOUD_OUT;
            level.addFreshEntity(PoisonCloud.at(level, new Vec3(x, this.groundY(x, z, this.getY()), z), this));
        }
    }

    /** The hammer swing's fire: a burst of flame along its arc and a few patches of fire left on the ground there. */
    private void hammerFire(ServerLevel level)
    {
        Vec3 f = this.forward();
        Vec3 side = new Vec3(-f.z, 0.0D, f.x);
        for (int i = 0; i < KnightTuning.HAMMER_FIRES; i++)
        {
            double a = (i / (double) (KnightTuning.HAMMER_FIRES - 1) - 0.5D) * Math.toRadians(140.0D);
            double d = 2.0D + this.random.nextDouble() * 1.8D;
            Vec3 spot = this.position().add(f.scale(Math.cos(a) * d)).add(side.scale(Math.sin(a) * d));
            level.sendParticles(ParticleTypes.FLAME, spot.x, spot.y + 0.6D, spot.z, 8, 0.3D, 0.3D, 0.3D, 0.04D);
            LavaPools.placeFire(level, BlockPos.containing(spot.x, spot.y + 0.5D, spot.z), false,
                    KnightTuning.HAMMER_FIRE_MIN_TICKS, KnightTuning.HAMMER_FIRE_MAX_TICKS);
        }
    }

    private void fireSlam(ServerLevel level)
    {
        Vec3 at = this.at(KnightTuning.SLAM_FORWARD, KnightTuning.SLAM_RIGHT, 0.0D);
        double r = KnightTuning.SLAM_RADIUS;
        this.play("slam", 2.4F);
        level.sendParticles(ParticleTypes.EXPLOSION, at.x, at.y + 0.5D, at.z, 3, 1.0D, 0.2D, 1.0D, 0.0D);
        KnightEffects.shockwave(level, this, at, r + 1.0D, ParticleTypes.FLAME);
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 0.3D, at.z, 180, r * 0.45D, 0.3D, r * 0.45D, 0.16D);
        level.sendParticles(ParticleTypes.LAVA, at.x, at.y + 0.3D, at.z, 40, r * 0.35D, 0.2D, r * 0.35D, 0.0D);
        level.sendParticles(ParticleTypes.LARGE_SMOKE, at.x, at.y + 0.6D, at.z, 30, r * 0.35D, 0.4D, r * 0.35D, 0.04D);
        // a pillar of fire where the hammer lands
        level.sendParticles(ParticleTypes.FLAME, at.x, at.y + 1.5D, at.z, 60, 0.4D, 1.2D, 0.4D, 0.08D);
        for (LivingEntity hit : this.near(at.add(0.0D, 1.0D, 0.0D), r))
        {
            double d = Math.sqrt(hit.distanceToSqr(at.x, hit.getY(), at.z));
            float share = 1.0F - 0.5F * (float) Mth.clamp(d / r, 0.0D, 1.0D);
            this.hurtFoe(hit, KnightTuning.SLAM_DAMAGE * share);
            hit.setSecondsOnFire(KnightTuning.SLAM_FIRE_SECONDS);
            hit.knockback(KnightTuning.SLAM_KNOCK * share, at.x - hit.getX(), at.z - hit.getZ());
            this.launch(hit, KnightTuning.SLAM_LAUNCH * share);
        }
        for (int i = 0; i < KnightTuning.SLAM_FIRES; i++)
        {
            double a = 2.0D * Math.PI * (i + this.random.nextDouble() * 0.5D) / KnightTuning.SLAM_FIRES;
            double d = KnightTuning.SLAM_FIRE_NEAR + this.random.nextDouble()
                    * (KnightTuning.SLAM_FIRE_FAR - KnightTuning.SLAM_FIRE_NEAR);
            BlockPos spot = BlockPos.containing(at.x + Math.cos(a) * d, this.getY() + 0.5D, at.z + Math.sin(a) * d);
            LavaPools.placeFire(level, spot, false, KnightTuning.SLAM_FIRE_MIN_TICKS, KnightTuning.SLAM_FIRE_MAX_TICKS);
        }
    }

    private void crystalBurst(ServerLevel level, @Nullable LivingEntity target)
    {
        this.play("crystal_burst", 2.0F);
        // the stomp: his right foot coming down hard, a heavy thud and the ground's dust thrown up round it
        Vec3 foot = this.at(0.1D, KnightTuning.STOMP_RIGHT, 0.0D);
        this.sound(ModSounds.COLOSSUS_SLAM.get(), 2.6F, 0.9F);
        this.sound(knightSound(this.kind, "step"), 3.0F, 0.55F);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK,
                level.getBlockState(BlockPos.containing(foot.x, this.getY() - 0.5D, foot.z))), foot.x, this.getY() + 0.2D,
                foot.z, 60, 0.7D, 0.25D, 0.7D, 0.3D);
        level.sendParticles(ParticleTypes.EXPLOSION, foot.x, this.getY() + 0.4D, foot.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        Vec3 middle = this.position();
        KnightEffects.shockwave(level, this, middle, KnightTuning.BURST_OUTER + 1.0D,
                new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_AMETHYST_DUST), 1.5F));
        this.crystalRing(level, middle, KnightTuning.BURST_INNER, KnightTuning.BURST_INNER_COUNT);
        this.later(KnightTuning.BURST_OUTER_DELAY,
                () -> this.crystalRing(level, middle, KnightTuning.BURST_OUTER, KnightTuning.BURST_OUTER_COUNT));
        if (target != null && target.distanceTo(this) <= this.kind.second.maxRange() + 2.0D)
        {
            Vec3 at = target.position();
            level.sendParticles(new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_AMETHYST_DUST), 1.5F),
                    at.x, at.y + 0.1D, at.z, 30, 0.6D, 0.05D, 0.6D, 0.0D);
            this.later(KnightTuning.BURST_FOE_DELAY, () ->
            {
                this.sound(knightSound(this.kind, "crystals"), 1.4F, 1.25F);
                this.crystal(level, middle, at, KnightTuning.CRYSTAL_FOE, 4.0D);
            });
        }
    }

    private void crystalRing(ServerLevel level, Vec3 middle, double radius, int count)
    {
        for (int i = 0; i < count; i++)
        {
            double a = 2.0D * Math.PI * (i + this.random.nextDouble() * 0.4D) / count;
            Vec3 at = middle.add(Math.cos(a) * radius, 0.0D, Math.sin(a) * radius);
            float h = KnightTuning.CRYSTAL_MIN + this.random.nextFloat() * (KnightTuning.CRYSTAL_MAX - KnightTuning.CRYSTAL_MIN);
            this.crystal(level, middle, at, h, 25.0D);
        }
        this.sound(knightSound(this.kind, "crystals"), 1.6F, 0.95F + this.random.nextFloat() * 0.1F);
    }

    /** One amethyst crystal bursting up at {@code at}, {@code h} tall, leaning away from {@code middle}. */
    private void crystal(ServerLevel level, Vec3 middle, Vec3 spot, float h, double lean)
    {
        Vec3 at = new Vec3(spot.x, this.groundY(spot.x, spot.z, this.getY()), spot.z);
        KnightSpikes.spike(level, Blocks.AMETHYST_CLUSTER.defaultBlockState(), at, h, h,
                KnightSpikes.leanAway(middle, at, lean), this.random.nextFloat() * 360.0F, KnightTuning.SPIKE_LIFE);
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.AMETHYST_BLOCK.defaultBlockState()),
                at.x, at.y + 0.3D, at.z, 10, 0.3D, 0.2D, 0.3D, 0.1D);
        level.sendParticles(new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_AMETHYST_DUST), 1.2F),
                at.x, at.y + h * 0.5D, at.z, 6, 0.3D, h * 0.3D, 0.3D, 0.0D);
        for (LivingEntity hit : this.near(at, KnightTuning.BURST_HIT))
        {
            this.hurtFoe(hit, KnightTuning.BURST_DAMAGE);
            this.launch(hit, KnightTuning.BURST_LAUNCH);
        }
    }

    private void rockfall(ServerLevel level, @Nullable LivingEntity target)
    {
        Vec3 at = this.position().add(this.forward().scale(2.0D));
        this.play("rockfall", 2.4F);
        KnightEffects.shockwave(level, this, at, KnightTuning.ROCKFALL_SLAM_RADIUS + 1.5D,
                new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_STONE_DUST), 1.8F));
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), at.x,
                at.y + 0.3D, at.z, 60, KnightTuning.ROCKFALL_SLAM_RADIUS * 0.4D, 0.3D, KnightTuning.ROCKFALL_SLAM_RADIUS * 0.4D, 0.2D);
        for (LivingEntity hit : this.near(at.add(0.0D, 1.0D, 0.0D), KnightTuning.ROCKFALL_SLAM_RADIUS))
        {
            this.hurtFoe(hit, KnightTuning.ROCKFALL_SLAM_DAMAGE);
            hit.knockback(KnightTuning.ROCKFALL_SLAM_KNOCK, at.x - hit.getX(), at.z - hit.getZ());
        }
        Vec3 middle = target != null ? target.position() : this.position().add(this.forward().scale(8.0D));
        // the circle: everything in it hurt every pulse while the rocks come down, its edge marked with dust
        int last = KnightTuning.ROCKFALL_SPREAD_TICKS + KnightTuning.ROCKFALL_FALL_TICKS;
        for (int when = KnightTuning.ROCKFALL_FALL_TICKS; when <= last; when += KnightTuning.ROCKFALL_PULSE_TICKS)
        {
            this.later(when, () -> this.rockfallPulse(level, middle));
        }
        for (int when = 0; when < last; when += 5)
        {
            this.later(when, () -> this.rockfallRing(level, middle));
        }
        for (int i = 0; i < KnightTuning.ROCKFALL_ROCKS; i++)
        {
            int when = i == 0 ? 0 : this.random.nextInt(KnightTuning.ROCKFALL_SPREAD_TICKS);
            this.later(when, () ->
            {
                double a = this.random.nextDouble() * Math.PI * 2.0D;
                double d = Math.sqrt(this.random.nextDouble()) * KnightTuning.ROCKFALL_RADIUS;
                double x = middle.x + Math.cos(a) * d;
                double z = middle.z + Math.sin(a) * d;
                Vec3 spot = new Vec3(x, this.groundY(x, z, middle.y), z);
                ColossusRock rock = ColossusRock.boulder(level, this, spot.add(0.0D, KnightTuning.ROCKFALL_HEIGHT, 0.0D),
                        KnightTuning.ROCKFALL_DAMAGE, KnightTuning.ROCKFALL_ROCK_SIZE);
                rock.aim(spot, KnightTuning.ROCKFALL_FALL_TICKS);
                level.addFreshEntity(rock);
            });
        }
    }

    /** One pulse of the rockfall: everything in its circle hurt, a puff of dust and grit over it. */
    private void rockfallPulse(ServerLevel level, Vec3 middle)
    {
        double r = KnightTuning.ROCKFALL_RADIUS + KnightTuning.ROCKFALL_ZONE_MARGIN;
        double ground = this.groundY(middle.x, middle.z, middle.y);
        AABB box = new AABB(middle.x - r, ground - 1.0D, middle.z - r, middle.x + r,
                ground + KnightTuning.ROCKFALL_ZONE_HEIGHT, middle.z + r);
        for (LivingEntity hit : this.level().getEntitiesOfClass(LivingEntity.class, box, this::canHit))
        {
            double dx = hit.getX() - middle.x;
            double dz = hit.getZ() - middle.z;
            double reach = r + hit.getBbWidth() * 0.5D;
            if (dx * dx + dz * dz <= reach * reach)
            {
                // (it ignores the half second a hit leaves: every pulse counts)
                hit.invulnerableTime = 0;
                this.hurtFoe(hit, KnightTuning.ROCKFALL_PULSE_DAMAGE);
            }
        }
        level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()), middle.x,
                ground + 0.4D, middle.z, 24, r * 0.4D, 0.2D, r * 0.4D, 0.1D);
    }

    /** The rockfall's circle, marked on the ground while it lasts. */
    private void rockfallRing(ServerLevel level, Vec3 middle)
    {
        double r = KnightTuning.ROCKFALL_RADIUS + KnightTuning.ROCKFALL_ZONE_MARGIN;
        double ground = this.groundY(middle.x, middle.z, middle.y);
        DustParticleOptions dust = new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_STONE_DUST), 1.6F);
        int n = 28;
        for (int i = 0; i < n; i++)
        {
            double a = 2.0D * Math.PI * (i + this.random.nextDouble() * 0.5D) / n;
            double x = middle.x + Math.cos(a) * r;
            double z = middle.z + Math.sin(a) * r;
            level.sendParticles(dust, x, this.groundY(x, z, ground) + 0.15D, z, 1, 0.05D, 0.02D, 0.05D, 0.0D);
        }
    }

    /** The Blight Warden's rain, while it falls (every tick). */
    private void tickRain(ServerLevel level)
    {
        if (this.tickCount >= this.rainUntil)
        {
            return;
        }
        double r = KnightTuning.RAIN_RADIUS;
        for (int i = 0; i < KnightTuning.RAIN_DROPS; i++)
        {
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double d = Math.sqrt(this.random.nextDouble()) * r;
            int color = this.random.nextInt(3) == 0 ? DrossColors.KNIGHT_POISON_RAIN_DARK : DrossColors.KNIGHT_POISON_RAIN;
            // (the cloud's drops at every height from the cloud down to the ground: the user, November 18, 2026, saw the
            // rain stay up in the air over him and never reach the ground)
            double h = this.random.nextDouble() * KnightTuning.RAIN_HEIGHT;
            level.sendParticles(new DustParticleOptions(DrossColors.vector(color), 1.3F), this.rainAt.x + Math.cos(a) * d,
                    this.rainAt.y + 0.2D + h, this.rainAt.z + Math.sin(a) * d, 0, 0.0D, -1.0D, 0.0D, 3.0D);
        }
        for (int i = 0; i < KnightTuning.RAIN_FALLING; i++)
        {
            // drops of slime falling all the way from the cloud to the ground
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double d = Math.sqrt(this.random.nextDouble()) * r;
            level.sendParticles(ParticleTypes.ITEM_SLIME, this.rainAt.x + Math.cos(a) * d,
                    this.rainAt.y + KnightTuning.RAIN_HEIGHT * (0.4D + 0.6D * this.random.nextDouble()),
                    this.rainAt.z + Math.sin(a) * d, 0, 0.0D, -1.0D, 0.0D, 0.6D);
        }
        if (this.tickCount % 4 == 0)
        {
            // the cloud it falls from
            int dark = DrossColors.KNIGHT_POISON_RAIN_DARK;
            level.sendParticles(new DustParticleOptions(DrossColors.vector(dark), 3.0F), this.rainAt.x,
                    this.rainAt.y + KnightTuning.RAIN_HEIGHT + 0.5D, this.rainAt.z, 12, r * 0.5D, 0.3D, r * 0.5D, 0.0D);
        }
        for (int i = 0; i < 2; i++)
        {
            // splashes where it lands
            double a = this.random.nextDouble() * Math.PI * 2.0D;
            double d = Math.sqrt(this.random.nextDouble()) * r;
            double x = this.rainAt.x + Math.cos(a) * d;
            double z = this.rainAt.z + Math.sin(a) * d;
            level.sendParticles(ParticleTypes.ITEM_SLIME, x, this.groundY(x, z, this.rainAt.y) + 0.15D, z, 3, 0.1D, 0.0D,
                    0.1D, 0.05D);
        }
        if ((this.rainUntil - this.tickCount) % KnightTuning.RAIN_EVERY == 0)
        {
            for (LivingEntity hit : this.near(this.rainAt.add(0.0D, 1.0D, 0.0D), r))
            {
                hit.hurt(typed(level, net.minecraft.world.damagesource.DamageTypes.MAGIC, this, this), KnightTuning.RAIN_DAMAGE);
                hit.addEffect(new MobEffectInstance(MobEffects.POISON, KnightTuning.RAIN_POISON, 1), this);
            }
        }
    }

    /** One tick of the Stormblade's dash. */
    private void dashStep(int t)
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        double step = Math.min(KnightTuning.DASH_SPEED, this.dashLeft);
        Vec3 before = this.position();
        this.move(MoverType.SELF, this.dashWay.scale(step).add(0.0D, this.onGround() ? 0.0D : -0.3D, 0.0D));
        this.setDeltaMovement(0.0D, this.getDeltaMovement().y, 0.0D);
        double moved = this.position().subtract(before).horizontalDistance();
        this.dashLeft -= Math.max(moved, 0.01D);
        float yaw = (float) (Mth.atan2(-this.dashWay.x, this.dashWay.z) * Mth.RAD_TO_DEG);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
        // lightning left behind him
        this.trail.add(new TrailPoint(before, this.tickCount + KnightTuning.TRAIL_TICKS));
        this.trail.add(new TrailPoint(before.add(this.position()).scale(0.5D), this.tickCount + KnightTuning.TRAIL_TICKS));
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, this.getX(), this.getY() + 1.5D, this.getZ(), 12, 0.4D, 0.9D, 0.4D, 0.2D);
        level.sendParticles(new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_SPARK_BRIGHT), 2.0F), this.getX(),
                this.getY() + 1.8D, this.getZ(), 10, 0.3D, 1.0D, 0.3D, 0.0D);
        if (t % 3 == 0)
        {
            this.sound(knightSound(this.kind, "dash_crackle"), 0.8F, 0.9F + this.random.nextFloat() * 0.2F);
        }
        for (LivingEntity hit : this.near(this.position().add(0.0D, 1.0D, 0.0D), KnightTuning.DASH_HIT))
        {
            if (this.dashHit.add(hit.getId()))
            {
                this.hurtFoe(hit, KnightTuning.DASH_DAMAGE);
                Vec3 side = new Vec3(-this.dashWay.z, 0.0D, this.dashWay.x);
                if (side.dot(hit.position().subtract(this.position())) < 0.0D)
                {
                    side = side.scale(-1.0D);
                }
                hit.knockback(KnightTuning.DASH_KNOCK, -side.x, -side.z);
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, hit.getX(), hit.getY() + hit.getBbHeight() * 0.5D,
                        hit.getZ(), 20, 0.3D, 0.4D, 0.3D, 0.4D);
            }
        }
        if (this.dashLeft <= 0.05D || this.horizontalCollision || t >= KnightTuning.DASH_MAX_TICKS)
        {
            this.play("trail", 1.0F);
            this.dashing = false;
            this.setAction(null);
            this.moveEnd = this.tickCount + KnightTuning.DASH_RECOVER;
            this.nextMoveAt = this.moveEnd + KnightTuning.PAUSE / 2;
        }
    }

    /** The Stormblade's lightning trail (every tick). */
    private void tickTrail(ServerLevel level)
    {
        if (this.trail.isEmpty())
        {
            return;
        }
        boolean sting = this.tickCount % KnightTuning.TRAIL_EVERY == 0;
        Iterator<TrailPoint> it = this.trail.iterator();
        while (it.hasNext())
        {
            TrailPoint p = it.next();
            if (this.tickCount >= p.until())
            {
                it.remove();
                continue;
            }
            if (this.random.nextInt(2) == 0)
            {
                level.sendParticles(new DustParticleOptions(DrossColors.vector(DrossColors.KNIGHT_SPARK), 1.6F), p.at().x,
                        p.at().y + 0.15D + this.random.nextDouble() * 0.6D, p.at().z, 1, 0.25D, 0.05D, 0.25D, 0.0D);
            }
            if (this.random.nextInt(4) == 0)
            {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, p.at().x, p.at().y + 0.2D, p.at().z, 1, 0.3D, 0.2D, 0.3D, 0.1D);
            }
            if (sting)
            {
                for (LivingEntity hit : this.near(p.at().add(0.0D, 0.5D, 0.0D), KnightTuning.TRAIL_HIT))
                {
                    Integer last = this.trailStungAt.get(hit.getId());
                    if (last == null || this.tickCount - last >= KnightTuning.TRAIL_EVERY)
                    {
                        this.trailStungAt.put(hit.getId(), this.tickCount);
                        hit.hurt(typed(level, net.minecraft.world.damagesource.DamageTypes.LIGHTNING_BOLT, this, this),
                                KnightTuning.TRAIL_DAMAGE);
                    }
                }
            }
        }
        if (this.trail.isEmpty())
        {
            this.trailStungAt.clear();
        }
    }

    // ---------------------------------------------------------------- helpers

    /** Its front (flat), from where its body faces. */
    Vec3 forward()
    {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    /** A spot this far ahead of it, out to its right and up. */
    Vec3 at(double ahead, double right, double up)
    {
        Vec3 f = this.forward();
        return this.position().add(f.x * ahead - f.z * right, up, f.z * ahead + f.x * right);
    }

    /** Turns it towards {@code to} by at most {@code max} degrees. */
    private void turnTo(Vec3 to, float max)
    {
        double dx = to.x - this.getX();
        double dz = to.z - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4D)
        {
            return;
        }
        float want = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(this.getYRot(), want, max);
        this.setYRot(yaw);
        this.yBodyRot = yaw;
        this.yHeadRot = yaw;
    }

    /** Where {@code target} will be in {@code ticks} ticks if it keeps going. */
    private Vec3 lead(LivingEntity target, int ticks)
    {
        Vec3 v = target.getId() == this.seenId ? this.seenMotion : target.getDeltaMovement();
        double speed = Math.sqrt(v.x * v.x + v.z * v.z);
        if (speed > LEAD_MAX_SPEED)
        {
            v = v.scale(LEAD_MAX_SPEED / speed);
        }
        return target.position().add(v.x * ticks, 0.0D, v.z * ticks);
    }

    /** Most it leads a foe by (blocks a tick: a sprinting player is about 0.28). */
    private static final double LEAD_MAX_SPEED = 0.4D;
    /** Its foe: who, where last tick, and how it has been moving (smoothed over a few ticks). */
    private int seenId = -1;
    @Nullable
    private Vec3 seenAt;
    private Vec3 seenMotion = Vec3.ZERO;

    /** Every tick: how its foe is really moving (a player's own motion never reaches the server). */
    private void watchTarget()
    {
        LivingEntity t = this.getTarget();
        if (t == null)
        {
            this.seenId = -1;
            this.seenAt = null;
            this.seenMotion = Vec3.ZERO;
            return;
        }
        Vec3 p = t.position();
        if (t.getId() == this.seenId && this.seenAt != null)
        {
            Vec3 step = p.subtract(this.seenAt);
            this.seenMotion = step.lengthSqr() > 4.0D ? Vec3.ZERO : this.seenMotion.scale(0.6D).add(step.scale(0.4D));
        }
        else
        {
            this.seenMotion = Vec3.ZERO;
        }
        this.seenId = t.getId();
        this.seenAt = p;
    }

    /** The top of the ground at {@code x, z}, looking near {@code nearY}. */
    double groundY(double x, double z, double nearY)
    {
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        int top = Mth.floor(nearY) + 3;
        for (int y = top; y >= top - 9; y--)
        {
            p.set(Mth.floor(x), y, Mth.floor(z));
            BlockState state = this.level().getBlockState(p);
            if (!state.getCollisionShape(this.level(), p).isEmpty()
                    && this.level().getBlockState(p.above()).getCollisionShape(this.level(), p.above()).isEmpty())
            {
                return y + state.getCollisionShape(this.level(), p).max(net.minecraft.core.Direction.Axis.Y);
            }
        }
        return nearY;
    }

    /** True if its blows may hurt {@code e}: anything living but itself, its fellow knights (unless the Rage Wand has
     * set them on each other), players in creative or spectator mode, armour stands and the gallery's statues. */
    public boolean canHit(LivingEntity e)
    {
        if (e == this || !e.isAlive() || e instanceof ArmorStand || e instanceof KnightCandidate)
        {
            return false;
        }
        if (e instanceof Player player && (player.isCreative() || player.isSpectator()))
        {
            return false;
        }
        return !(e instanceof ElementalKnight) || Rage.areEnemies(this, e);
    }

    /** Everything it may hurt whose body is within {@code r} of {@code at}. */
    private List<LivingEntity> near(Vec3 at, double r)
    {
        List<LivingEntity> out = new ArrayList<>();
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, new AABB(at, at).inflate(r + 2.0D), this::canHit))
        {
            if (e.getBoundingBox().inflate(0.1D).distanceToSqr(at) <= r * r)
            {
                out.add(e);
            }
        }
        return out;
    }

    /**
     * A blow in front of it: everything it may hurt within {@code reach} blocks (from its middle to their edge) and
     * {@code arc} degrees round where it faces takes {@code damage} and is knocked away. Returns what it hit.
     */
    private List<LivingEntity> strike(double arc, double reach, float damage, double knock)
    {
        // (a little past its reach and its arc: a foe that stepped back during the wind-up, when the blow looks as if
        // it lands, is hit: KnightTuning.REACH_SLACK)
        reach += KnightTuning.REACH_SLACK;
        arc = Math.min(360.0D, arc + KnightTuning.ARC_SLACK);
        Vec3 f = this.forward();
        List<LivingEntity> hits = new ArrayList<>();
        AABB box = this.getBoundingBox().inflate(reach + 1.0D, 2.0D, reach + 1.0D);
        for (LivingEntity e : this.level().getEntitiesOfClass(LivingEntity.class, box, this::canHit))
        {
            Vec3 d = new Vec3(e.getX() - this.getX(), 0.0D, e.getZ() - this.getZ());
            double dist = d.length() - e.getBbWidth() / 2.0D;
            if (dist > reach || e.getY() > this.getY() + this.getBbHeight() || e.getY() + e.getBbHeight() < this.getY() - 1.0D)
            {
                continue;
            }
            if (d.length() > 1.0D)
            {
                double angle = Math.toDegrees(Math.acos(Mth.clamp(f.dot(d.normalize()), -1.0D, 1.0D)));
                if (angle > arc / 2.0D)
                {
                    continue;
                }
            }
            if (this.hurtFoe(e, damage))
            {
                e.knockback(knock, this.getX() - e.getX(), this.getZ() - e.getZ());
            }
            hits.add(e);
        }
        return hits;
    }

    private boolean hurtFoe(LivingEntity e, float damage)
    {
        boolean hurt = e.hurt(this.damageSources().mobAttack(this), damage);
        if (hurt && this.level() instanceof ServerLevel level)
        {
            KnightEffects.impact(level, e, this.kind.element);
        }
        return hurt;
    }

    private void launch(LivingEntity e, double up)
    {
        e.setDeltaMovement(e.getDeltaMovement().add(0.0D, up * (1.0D - e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) * 0.5D), 0.0D));
        e.hurtMarked = true;
    }

    private void sweepParticles(ParticleOptions particle, double reach)
    {
        if (this.level() instanceof ServerLevel level)
        {
            Vec3 at = this.at(reach * 0.6D, 0.0D, 1.6D);
            level.sendParticles(particle, at.x, at.y, at.z, 20, reach * 0.3D, 0.4D, reach * 0.3D, 0.05D);
            level.sendParticles(ParticleTypes.SWEEP_ATTACK, at.x, at.y, at.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    private void sound(SoundEvent sound, float volume, float pitch)
    {
        this.level().playSound(null, this.getX(), this.getY() + 2.0D, this.getZ(), sound, SoundSource.HOSTILE, volume, pitch);
    }

    /** Plays one of this knight's own sounds ({@link KnightSounds}) at its own pitch. */
    private void play(String name, float volume)
    {
        this.sound(knightSound(this.kind, name), volume, 1.0F);
    }

    /**
     * One of a knight's own sounds ({@code entity.knight_<kind>.<name>}: all original, made by
     * {@code tools/knights/sounds.py} and registered in {@code ModSounds.KNIGHT_SOUNDS}).
     */
    public static SoundEvent knightSound(KnightKind kind, String name)
    {
        String id = KnightSounds.event(kind, name);
        RegistryObject<SoundEvent> sound = ModSounds.KNIGHT_SOUNDS.get(id);
        return sound != null && sound.isPresent() ? sound.get()
                : SoundEvent.createVariableRangeEvent(new ResourceLocation(Dross.MODID, id));
    }

    /** The wind-up sound at the start of the move going on (a heavy blow's build-up), or null if it has none. */
    @Nullable
    private String windup()
    {
        return switch (this.kind)
        {
            case THAWED_AXE -> this.move == 1 ? "smash_windup" : null;
            case SPOREBOUND -> this.move == 1 ? "spore_windup" : null;
            case CINDER -> this.move == 1 ? "slam_windup" : null;
            case WRECK -> this.move == 0 ? "throw_windup" : "slam_windup";
            case GEODE -> this.move == 1 ? "burst_windup" : null;
            case ROCKFALL -> this.move == 0 ? "heave" : "rockfall_windup";
            default -> null;
        };
    }

    void later(int ticks, Runnable run)
    {
        this.later.add(new Later(this.tickCount + ticks, run));
    }

    // ---------------------------------------------------------------- every tick

    @Override
    public void tick()
    {
        super.tick();
        if (!(this.level() instanceof ServerLevel level))
        {
            if (this.isAlive() && !this.isInvisible())
            {
                KnightEffects.ambient(this.level(), this);
            }
            return;
        }
        if (this.isShowcase())
        {
            if (!this.isActing() && this.action() != null)
            {
                this.setAction(null);
            }
            return;
        }
        this.watchTarget();
        this.keepToRuin();
        this.tickMove();
        if (!this.later.isEmpty() && this.isAlive())
        {
            List<Later> due = new ArrayList<>();
            this.later.removeIf(l ->
            {
                if (this.tickCount >= l.at())
                {
                    due.add(l);
                    return true;
                }
                return false;
            });
            due.forEach(l -> l.run().run());
        }
        this.tickRain(level);
        this.tickTrail(level);
        if (this.move < 0 && this.action() != null)
        {
            this.setAction(null);
        }
        if (this.isAnchorOut() && this.tickCount - this.anchorThrownAt > ANCHOR_SAFETY)
        {
            this.setAnchorOut(false);
        }
    }

    @Override
    public void die(DamageSource source)
    {
        this.later.clear();
        if (this.level() instanceof ServerLevel level && !this.isShowcase() && !this.dead)
        {
            KnightEffects.death(level, this);
        }
        super.die(source);
    }

    // ---------------------------------------------------------------- what hurts it

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        if (this.isShowcase() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return false;
        }
        if (this.immuneTo(source))
        {
            return false;
        }
        if (this.guarding() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            amount *= KnightTuning.GUARD_DAMAGE_TAKEN;
            this.sound(knightSound(this.kind, "block"), 1.0F, 0.9F + this.random.nextFloat() * 0.2F);
        }
        return super.hurt(source, amount);
    }

    @Override
    public void knockback(double strength, double x, double z)
    {
        if (!this.guarding() && !this.dashing)
        {
            super.knockback(strength, x, z);
        }
    }

    /**
     * Each element shrugs off its own (the user, November 19, 2026: "make the thunder, fire and rock knights immune to
     * fire, the poison ones immune to poison and wither, and the same clear immunities for the rest"): the fire, thunder
     * and earth knights never burn (their entity types are fire-immune: fire, lava, magma); the thunder knights take no
     * lightning; the earth knights no fall, no suffocating in a wall and nothing falling on them (an anvil, a
     * stalactite); the ice knights no freezing. Effects: {@link #canBeAffected}.
     */
    private boolean immuneTo(DamageSource source)
    {
        if (source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return false;
        }
        return switch (this.kind.element)
        {
            case THUNDER -> source.is(DamageTypeTags.IS_LIGHTNING);
            case EARTH -> source.is(DamageTypeTags.IS_FALL)
                    || source.is(net.minecraft.world.damagesource.DamageTypes.IN_WALL)
                    || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_BLOCK)
                    || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_ANVIL)
                    || source.is(net.minecraft.world.damagesource.DamageTypes.FALLING_STALACTITE)
                    || source.is(net.minecraft.world.damagesource.DamageTypes.STALAGMITE);
            case ICE -> source.is(DamageTypeTags.IS_FREEZING);
            case POISON -> source.is(net.minecraft.world.damagesource.DamageTypes.WITHER);
            default -> false;
        };
    }

    /** The effects each element shrugs off: poison and wither (poison), slowness, mining fatigue and weakness from the
     * cold (ice), nothing more for the rest (their fire, lightning and falls are damage: {@link #immuneTo}). */
    @Override
    public boolean canBeAffected(MobEffectInstance effect)
    {
        MobEffect e = effect.getEffect();
        boolean immune = switch (this.kind.element)
        {
            case POISON -> e == MobEffects.POISON || e == MobEffects.WITHER;
            case ICE -> e == MobEffects.MOVEMENT_SLOWDOWN || e == MobEffects.DIG_SLOWDOWN;
            default -> false;
        };
        return !immune && super.canBeAffected(effect);
    }

    @Override
    public boolean canFreeze()
    {
        return this.kind.element != KnightKind.Element.ICE && super.canFreeze();
    }

    @Override
    public void thunderHit(ServerLevel level, LightningBolt bolt)
    {
        if (this.kind.element != KnightKind.Element.THUNDER)
        {
            super.thunderHit(level, bolt);
        }
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source)
    {
        return this.kind.element != KnightKind.Element.EARTH && super.causeFallDamage(distance, multiplier, source);
    }

    @Override
    public boolean removeWhenFarAway(double distance)
    {
        return false;
    }

    /** One on show stays on Peaceful (like the other showcases' frozen mobs); a real one goes, like any monster. */
    @Override
    protected boolean shouldDespawnInPeaceful()
    {
        return !this.isShowcase();
    }

    // ---------------------------------------------------------------- sounds

    // All its own (tools/knights/sounds.py): each element its own character, each knight his own weight and voice.

    @Nullable
    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.isShowcase() ? null : knightSound(this.kind, "ambient");
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return KnightTuning.AMBIENT_INTERVAL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return knightSound(this.kind, "hurt");
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return knightSound(this.kind, "death");
    }

    /** Its sounds are made at its own pitch: only a little variation. */
    @Override
    public float getVoicePitch()
    {
        return 1.0F + (this.random.nextFloat() - 0.5F) * 0.08F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        float volume = switch (this.kind)
        {
            case WRECK, ROCKFALL, CINDER -> KnightTuning.STEP_VOLUME_HEAVY;
            case STORMBLADE, FLAMEHAND, BLIGHT_WARDEN -> KnightTuning.STEP_VOLUME_LIGHT;
            default -> KnightTuning.STEP_VOLUME;
        };
        this.playSound(knightSound(this.kind, "step"), volume, 0.95F + this.random.nextFloat() * 0.1F);
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putBoolean(TAG_SHOWCASE, this.isShowcase());
        if (this.ruin != null && this.ruinHome != null)
        {
            tag.putString(TAG_RUIN, this.ruin);
            tag.put(TAG_RUIN_HOME, NbtUtils.writeBlockPos(this.ruinHome));
            tag.putInt(TAG_RUIN_SLOT, this.ruinSlot);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        if (tag.getBoolean(TAG_SHOWCASE))
        {
            this.setShowcase(true);
        }
        if (tag.contains(TAG_RUIN))
        {
            this.guard(tag.getString(TAG_RUIN), NbtUtils.readBlockPos(tag.getCompound(TAG_RUIN_HOME)), tag.getInt(TAG_RUIN_SLOT));
        }
    }

    // ---------------------------------------------------------------- animations

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new SteadyController<>(this, "main", KnightAnims.GAIT_BLEND, this::animate));
    }

    private PlayState animate(AnimationState<ElementalKnight> state)
    {
        AnimationController<ElementalKnight> controller = state.getController();
        this.trackGait();
        int count = this.entityData.get(DATA_ACTION_COUNT);
        if (count != this.seenActionCount)
        {
            this.seenActionCount = count;
            controller.forceAnimationReset();
        }
        RimefallAnims.Anim action = this.action();
        if (action != null)
        {
            // a short blend into a move (its moments count it: KnightAnims.MOVE_BLEND), shorter still into the dash
            controller.transitionLength(action.loop() ? KnightAnims.DASH_BLEND : KnightAnims.MOVE_BLEND);
            controller.setAnimationSpeed(1.0D);
            return state.setAndContinue(action.raw());
        }
        controller.transitionLength(KnightAnims.GAIT_BLEND);
        RimefallAnims.Anim walk = RimefallAnims.named(this.anims, "walk");
        if (walk != null && !this.isShowcase() && this.walkShown)
        {
            RimefallAnims.matchSpeed(controller, walk, Math.max(this.shownSpeed, RimefallAnims.MOVING));
            return state.setAndContinue(walk.raw());
        }
        controller.setAnimationSpeed(1.0D);
        RimefallAnims.Anim idle = RimefallAnims.named(this.anims, "idle");
        return idle != null ? state.setAndContinue(idle.raw()) : PlayState.STOP;
    }

    /** (client, once a tick) How fast it really moves, eased, and whether its walk shows (with a margin and a hold). */
    private void trackGait()
    {
        if (this.tickCount == this.gaitTick)
        {
            return;
        }
        this.gaitTick = this.tickCount;
        double speed = RimefallAnims.groundSpeed(this);
        this.shownSpeed += (speed - this.shownSpeed) * WALK_SPEED_EASE;
        if (speed > RimefallAnims.MOVING)
        {
            this.walkShown = true;
            this.stillTicks = 0;
        }
        else if (speed < WALK_STILL && ++this.stillTicks >= WALK_HOLD_TICKS)
        {
            this.walkShown = false;
        }
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.cache;
    }

    // ---------------------------------------------------------------- how it fights

    /**
     * Its fight: when a move is ready and its foe is in that move's range (and in sight, for a throw), it makes it (its
     * second move first, the rarer); meanwhile it closes in, or, a knight that fights from afar (Flamehand, the Blight
     * Warden, the Rockfall Knight), keeps about {@link KnightKind#keepAway} blocks off.
     */
    private class FightGoal extends Goal
    {
        private int repathAt;

        FightGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity target = ElementalKnight.this.getTarget();
            return target != null && target.isAlive();
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.canUse() || ElementalKnight.this.isActing();
        }

        @Override
        public void stop()
        {
            ElementalKnight.this.getNavigation().stop();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            ElementalKnight me = ElementalKnight.this;
            LivingEntity target = me.getTarget();
            if (me.isActing())
            {
                me.getNavigation().stop();
                return;
            }
            if (target == null)
            {
                return;
            }
            me.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double dist = Math.sqrt(me.distanceToSqr(target.getX(), me.getY(), target.getZ()));
            if (me.tickCount >= me.nextMoveAt)
            {
                for (int index = 1; index >= 0; index--)
                {
                    KnightKind.Move m = me.kind.move(index);
                    if (me.ready(index) && dist >= m.minRange() && dist <= m.maxRange()
                            && (dist < 5.0D || me.hasLineOfSight(target))
                            && !(me.kind == KnightKind.WRECK && index == 0 && me.isAnchorOut()))
                    {
                        me.startMove(index);
                        return;
                    }
                }
            }
            double keep = me.kind.keepAway;
            if (keep > 0.0D)
            {
                if (dist > keep + 4.0D)
                {
                    this.chase(target);
                }
                else if (dist < keep - 5.0D)
                {
                    Vec3 away = me.position().subtract(target.position()).multiply(1.0D, 0.0D, 1.0D).normalize().scale(6.0D);
                    if (me.tickCount >= this.repathAt)
                    {
                        me.getNavigation().moveTo(me.getX() + away.x, me.getY(), me.getZ() + away.z, 1.1D);
                        this.repathAt = me.tickCount + 10;
                    }
                }
                else
                {
                    me.getNavigation().stop();
                }
            }
            else if (dist > 2.2D)
            {
                this.chase(target);
            }
            else
            {
                me.getNavigation().stop();
            }
        }

        private void chase(LivingEntity target)
        {
            ElementalKnight me = ElementalKnight.this;
            if (me.tickCount >= this.repathAt || me.getNavigation().isDone())
            {
                me.getNavigation().moveTo(target, 1.0D);
                this.repathAt = me.tickCount + 10;
            }
        }
    }
}
