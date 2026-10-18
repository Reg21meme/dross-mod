package com.reg21meme.dross.boss.ratking;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rage.RageLeader;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophies;
import com.reg21meme.dross.enchant.RisenUndead;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModParticles;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.OwnableEntity;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;
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
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Rat King, one of the Plague Necromancer's three servants: a lean, sly giant rat standing upright, with a staff,
 * a crown and a red cape (CLAUDE.md, "The plan", step 9; the design is the user's pick, Gnawrick 1C, from
 * {@link RatKingDesigns}, made by {@code tools/ratking/}).
 * <p>
 * Clients get the design number, his current {@link RatKingAction}, a counter that goes up every time an action starts
 * (so playing the same one again restarts its animation) and whether he's enraged (his eyes glow red: the renderer's
 * rage layer, faded in at {@link RatKingDesigns.Design#rageEyes()} during the rage). The client adds the particles the
 * animations are timed for: Zzz while he sleeps, steam out of his ears in the rage, dirt as he digs, dives and bursts
 * out (and a trail of it over the ground while he tunnels), dust as he stomps and lands flat on his back. Where they
 * come from (his head, his ears) is tracked by the renderer ({@code client.RatKingRenderer}) from marker bones. The
 * server plays his sounds at the same moments (all original, made by {@code tools/ratking/sounds.py}). They play for
 * showcase kings too (which are otherwise silent).
 * <p>
 * <b>The fight</b> (every Rat King that isn't a showcase king: his spawn egg, {@link #spawnBoss} for
 * {@code /dross boss summon ratking}, {@code /summon}; every number is in {@link RatKingTuning}):
 * <ul>
 *   <li>He starts <b>asleep</b>. A player (not a spectator) within {@link RatKingTuning#WAKE_RANGE} blocks
 *       ({@link RatKingTuning#WAKE_RANGE_SNEAKING} if they sneak), or anything that hurts him, wakes him (that hit does
 *       no damage). He can't be hurt while he wakes up; then his boss bar appears and he fights, and never sleeps
 *       again.</li>
 *   <li>His moves ({@link RatKingAttackGoal}): a staff <b>sweep</b> close up, the <b>spear charge</b> (a dash to
 *       where his target stood, the staff held out like a lance, into a sweep if it hits) and a <b>poison shot</b> (one
 *       {@link PlagueBall}, three in the Rage) from further away; between them he runs at his target.</li>
 *   <li>Every {@link RatKingTuning#BURROW_EVERY} health he loses ({@link RatKingTuning#RAGE_BURROW_EVERY} in the Rage)
 *       he <b>burrows</b> ({@link RatKingBurrow}): he digs and dives in, tunnels towards his target, calls his
 *       {@link Rat}s out of the ground round it, and pops up near it. He can't be hurt from the dive until he lands.
 *       A hit never skips a burrow: his health stops at it.</li>
 *   <li>At {@link RatKingTuning#RAGE_AT} of his health he flies into a <b>rage</b> (his health stops there and he can't
 *       be hurt while it plays): red eyes from then on, and the Rage numbers.</li>
 *   <li>His boss bar ({@code boss.dross.rat_king.bar[.rage]}, drawn by {@code boss.client.DrossBossBars}) shows
 *       to players within {@link RatKingTuning#BOSS_BAR_RANGE} blocks once he's awake.</li>
 *   <li>When he dies he plays his silly death and lies there for {@link RatKingTuning#CORPSE_TICKS} ticks, then
 *       vanishes in a puff of smoke; his rats crumble. The player credited with the kill and every player within the
 *       boss bar's range get "Down the Drain" and their own trophy ({@code boss.ratking.trophy.RatKingTrophies}; not
 *       when he's removed with {@code /kill}). No other drops.</li>
 * </ul>
 * <p>
 * <b>Showcase Rat Kings</b> ({@link #spawnShowcase}, saved with {@code Showcase}) stand frozen (no AI), make no sound
 * of their own (no chatter; only their animations' sounds), can't be pushed, never despawn (not even on Peaceful) and
 * can't be hurt except by {@code /kill}. Right-clicking one plays his next animation (sleep, wake up, walk, burrow, pop
 * up, rage, the spear charge on the spot, die, stand); sneak + right-click: he dies. They never fight.
 */
public class RatKing extends Monster implements GeoEntity, RageLeader
{
    private static final EntityDataAccessor<Integer> DATA_DESIGN = SynchedEntityData.defineId(RatKing.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(RatKing.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_SERIAL = SynchedEntityData.defineId(RatKing.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_ENRAGED = SynchedEntityData.defineId(RatKing.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Boolean> DATA_SHOWCASE = SynchedEntityData.defineId(RatKing.class, EntityDataSerializers.BOOLEAN);

    private static final String TAG_DESIGN = "Design";
    private static final String TAG_ACTION = "Action";
    private static final String TAG_ENRAGED = "Enraged";
    private static final String TAG_SHOWCASE = "Showcase";
    private static final String TAG_AWAKE = "Awake";
    private static final String TAG_BURROW_AT = "BurrowAt";

    /** Ticks GeckoLib blends one animation into the next; an action's animation starts this long after the action. */
    public static final int ANIMATION_BLEND_TICKS = 4;
    /** Ticks after a once-only animation (wake, pop up, rage) has ended before he's switched to standing. */
    private static final int SETTLE_TICKS = 2;
    /** A fight move (sweep, cast) the AI didn't end is ended this long after its animation (a safety net). */
    private static final int MOVE_SAFETY_TICKS = 20;
    /** While he sleeps, a Z floats up from his head every this many ticks. */
    private static final int ZZZ_PERIOD = 22;
    /** Steam particles per ear in each puff, and how fast they shoot out (blocks per tick). */
    private static final int STEAM_PER_EAR = 3;
    private static final double STEAM_SPEED = 0.16D;
    /** Dirt flung out of the hole as he digs (per burst, every other tick), as he dives, and as he bursts out. */
    private static final int DIG_DIRT = 3;
    private static final int DIVE_DIRT = 4;
    private static final int BURST_DIRT = 26;
    /** Dirt thrown up over the ground each tick while he tunnels (the trail), and how fast. */
    private static final int TRAIL_DIRT = 3;
    private static final double TRAIL_SPEED = 0.18D;
    /** Dust kicked up by each stomp of his tantrum, and when he lands flat on his back. */
    private static final int STOMP_DUST = 8;
    private static final int THUD_DUST = 18;
    /** How loud his snores are, and which tick of each sleep loop he snores on. */
    private static final float SNORE_VOLUME = 0.7F;
    private static final int SNORE_AT = 2;
    /** How loud his footsteps are while he walks on the spot (showcase) or runs (the fight), and the run's pitch. */
    private static final float STEP_VOLUME = 0.5F;
    private static final float RUN_STEP_PITCH = 1.15F;
    /** Dust and green sparks thrown up where his staff's foot strikes the ground in the rage. */
    private static final int SLAM_DUST = 14;
    private static final int SLAM_SPARKS = 12;
    private static final float SLAM_SPARK_SIZE = 1.1F;
    /** His run plays between these times its own speed to keep up with how fast he really moves. */
    private static final double MIN_RUN_SPEEDUP = 0.5D;
    private static final double MAX_RUN_SPEEDUP = 3.0D;
    /** The renderer draws him when this far (blocks) beyond his hitbox is on screen: he lies on his back 3.5 blocks long. */
    private static final double CULL_MARGIN = 4.0D;
    /** The puff he vanishes in after lying dead: smoke and dust, and how loud. */
    private static final int VANISH_POOF = 30;
    private static final int VANISH_SMOKE = 16;
    private static final float VANISH_VOLUME = 0.8F;
    /** Health checks compare with this much slack (health is a float). */
    private static final float EPSILON = 1.0E-3F;
    /** How far (degrees) his head may turn away from his body before his body follows (Minecraft's usual is 40). */
    private static final int HEAD_TURN = 10;
    /** A showcase king's spear charge dashes on the spot this long (ticks) before he skids. */
    private static final int SHOWCASE_DASH_TICKS = 30;
    /** The charge's dust: kicked up behind his feet every other tick as he dashes, and a spray as he skids. */
    private static final int DASH_DUST = 2;
    private static final int SKID_DUST = 16;
    /** How loud the showcase's charge cry is. */
    private static final float SHOWCASE_CHARGE_VOLUME = 1.0F;

    /** The showcase's right-click order: each click plays the next one. */
    private static final RatKingAction[] CYCLE = {RatKingAction.SLEEP, RatKingAction.WAKE, RatKingAction.WALK,
            RatKingAction.BURROW, RatKingAction.POPUP, RatKingAction.RAGE, RatKingAction.CHARGE_START, RatKingAction.DEATH,
            RatKingAction.IDLE};

    private static final Map<RatKingAction, RawAnimation> ANIMATIONS = new EnumMap<>(RatKingAction.class);

    static
    {
        for (RatKingAction a : RatKingAction.values())
        {
            ANIMATIONS.put(a, a.mode() == RatKingAction.Mode.LOOP
                    ? RawAnimation.begin().thenLoop(a.animationName())
                    : RawAnimation.begin().thenPlayAndHold(a.animationName()));
        }
    }

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    /** Server: tickCount when the current action started. */
    private int actionStartTick;
    /** Server: where the showcase's right-click cycle is (-1: not started). */
    private int cycleIndex = -1;
    /** Client: tickCount when this client saw the current action start. */
    private int clientActionStart;
    /** Client: the action counter the animation controller last started an animation for. */
    private int serialSeen = -1;
    /** Client: where the particles come from, as the renderer last drew him (null before his first frame). */
    @Nullable
    private Vec3 zzzSpot;
    private final Vec3[] earSpots = new Vec3[2];
    private final Vec3[] earAims = new Vec3[2];

    // The fight (server side). The goal is made in registerGoals, during the constructor: no initializer here.
    private RatKingAttackGoal attackGoal;
    /** His burrows: digging in, tunnelling, calling his rats, popping up. */
    private final RatKingBurrow burrow = new RatKingBurrow(this);
    /** True once he has woken up (he never sleeps again). */
    private boolean awake;
    /** The health his next burrow comes at (NaN: work it out from his health). */
    private float burrowAt = Float.NaN;
    @Nullable
    private ServerBossEvent bossBar;
    /** The last player credited with a hit on him (theirs, their arrow's, their pet's or risen undead's), and when. */
    @Nullable
    private UUID creditPlayer;
    private int creditTick;

    public RatKing(EntityType<? extends RatKing> type, Level level)
    {
        super(type, level);
        this.xpReward = RatKingTuning.XP;
        this.setMaxUpStep(RatKingTuning.STEP_HEIGHT);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, RatKingTuning.MAX_HEALTH)
                .add(Attributes.ARMOR, RatKingTuning.ARMOR)
                .add(Attributes.MOVEMENT_SPEED, RatKingTuning.SPEED)
                .add(Attributes.ATTACK_DAMAGE, RatKingTuning.SWEEP_DAMAGE)
                .add(Attributes.KNOCKBACK_RESISTANCE, RatKingTuning.KNOCKBACK_RESISTANCE)
                .add(Attributes.FOLLOW_RANGE, RatKingTuning.FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_DESIGN, 1);
        this.entityData.define(DATA_ACTION, RatKingAction.IDLE.ordinal());
        this.entityData.define(DATA_ACTION_SERIAL, 0);
        this.entityData.define(DATA_ENRAGED, false);
        this.entityData.define(DATA_SHOWCASE, false);
    }

    @Override
    protected void registerGoals()
    {
        this.attackGoal = new RatKingAttackGoal(this);
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, this.attackGoal);
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F)
        {
            @Override
            public boolean canUse()
            {
                return RatKing.this.canLookAround() && super.canUse();
            }

            @Override
            public boolean canContinueToUse()
            {
                return RatKing.this.canLookAround() && super.canContinueToUse();
            }
        });
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this)
        {
            @Override
            public boolean canUse()
            {
                return RatKing.this.canLookAround() && super.canUse();
            }

            @Override
            public boolean canContinueToUse()
            {
                return RatKing.this.canLookAround() && super.canContinueToUse();
            }
        });
        // He goes for whoever hurts him (never another Rat King or a rat), else the nearest player he can see.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, RatKing.class, Rat.class)
        {
            @Override
            public boolean canUse()
            {
                return RatKing.this.isFightActive() && super.canUse();
            }
        });
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true)
        {
            @Override
            public boolean canUse()
            {
                return RatKing.this.isFightActive() && super.canUse();
            }
        });
    }

    /** A fighting Rat King (spawn egg, summon command, /summon) never despawns and starts asleep. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag)
    {
        this.setPersistenceRequired();
        if (!this.isShowcase())
        {
            this.awake = false;
            this.startAction(RatKingAction.SLEEP);
            this.applyStats();
        }
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    // ---------------------------------------------------------------- state

    public RatKingDesigns.Design getDesign()
    {
        return RatKingDesigns.byNumber(this.entityData.get(DATA_DESIGN));
    }

    public void setDesign(int number)
    {
        this.entityData.set(DATA_DESIGN, RatKingDesigns.byNumber(number).number());
    }

    public RatKingAction getAction()
    {
        int i = this.entityData.get(DATA_ACTION);
        RatKingAction[] all = RatKingAction.values();
        return i >= 0 && i < all.length ? all[i] : RatKingAction.IDLE;
    }

    /** Starts an action (and its animation) from the beginning, even if it's the one already playing. */
    public void startAction(RatKingAction action)
    {
        this.entityData.set(DATA_ACTION, action.ordinal());
        this.entityData.set(DATA_ACTION_SERIAL, this.entityData.get(DATA_ACTION_SERIAL) + 1);
        this.actionStartTick = this.tickCount;
    }

    /** True once he has flown into a rage (his eyes glow red); a showcase king sleeps it off. Works on both sides. */
    public boolean isEnraged()
    {
        return this.entityData.get(DATA_ENRAGED);
    }

    /** True if he only shows off a design (spawned by {@link #spawnShowcase}). Works on both sides. */
    public boolean isShowcase()
    {
        return this.entityData.get(DATA_SHOWCASE);
    }

    /** Server: ticks since the current action's animation started. */
    int animationTicks()
    {
        return this.tickCount - this.actionStartTick - ANIMATION_BLEND_TICKS;
    }

    /** Server: ticks since the current action started (its animation starts {@link #ANIMATION_BLEND_TICKS} later). */
    int ticksInAction()
    {
        return this.tickCount - this.actionStartTick;
    }

    /** Ticks into the current action's animation, on either side. */
    private int animationTicksEitherSide()
    {
        return this.level().isClientSide ? Mth.floor(this.clientAnimationTicks(0.0F)) : this.animationTicks();
    }

    // ---------------------------------------------------------------- the showcase's controls

    /** Plays one of his animations (the showcase's controls and right-click). Server side. */
    public void play(RatKingAction action)
    {
        switch (action)
        {
            case SLEEP -> this.entityData.set(DATA_ENRAGED, false); // sleeping calms him down
            case RAGE -> this.entityData.set(DATA_ENRAGED, true);
            default -> {
            }
        }
        this.startAction(action);
    }

    /** The word the showcase uses for an action (its subcommand and its action-bar line). */
    public static String word(RatKingAction action)
    {
        return switch (action)
        {
            case SLEEP -> "sleep";
            case WAKE -> "wake";
            case WALK -> "walk";
            case BURROW, BURROWED -> "burrow";
            case POPUP -> "popup";
            case RAGE -> "rage";
            case CHARGE_START, CHARGE, CHARGE_SKID -> "charge";
            case DEATH, DEAD -> "die";
            default -> "idle";
        };
    }

    // ---------------------------------------------------------------- the fight: state

    /** True for a fighting Rat King asleep (he hasn't been woken yet). */
    public boolean isAsleep()
    {
        return !this.isShowcase() && this.getAction() == RatKingAction.SLEEP;
    }

    /** True once a fighting Rat King has woken up (server side; also while the wake-up plays). */
    public boolean isAwake()
    {
        return this.awake;
    }

    /**
     * True while a fighting Rat King is awake and alive, the wake-up over: his boss bar shows, and he picks targets and
     * fights (unless he's {@link #isBusy busy}). Server side.
     */
    public boolean isFightActive()
    {
        return !this.isShowcase() && this.awake && this.getAction() != RatKingAction.WAKE && this.isAlive();
    }

    /** True while he can't make a move: asleep, waking, raging, burrowing, under the ground, popping up, or dead. */
    public boolean isBusy()
    {
        return switch (this.getAction())
        {
            case SLEEP, WAKE, RAGE, BURROW, BURROWED, POPUP, DEATH, DEAD -> true;
            default -> false;
        };
    }

    /** True while he's busy burrowing (from the dig until he's back on his feet). */
    public boolean isBurrowing()
    {
        return this.burrow.isActive();
    }

    /** He looks round (at players, about) only while he's fighting and not busy. */
    private boolean canLookAround()
    {
        return this.isFightActive() && !this.isBusy();
    }

    /**
     * True while he's under the ground (or diving into it, or not yet out of it): nothing can hit or target him, and
     * nothing bumps into him. Works on both sides.
     */
    public boolean isUnderground()
    {
        RatKingDesigns.Design d = this.getDesign();
        return switch (this.getAction())
        {
            case BURROWED -> !this.isShowcase();
            case BURROW -> !this.isShowcase() && this.animationTicksEitherSide() >= d.dive();
            case POPUP -> !this.isShowcase() && this.animationTicksEitherSide() < d.popupBurst();
            default -> false;
        };
    }

    /** True while a fighting Rat King can't be hurt: waking up, raging, from his dive until he has landed again. */
    private boolean isTransitionInvulnerable()
    {
        if (this.isShowcase())
        {
            return false;
        }
        RatKingDesigns.Design d = this.getDesign();
        int t = this.animationTicks();
        return switch (this.getAction())
        {
            case WAKE, RAGE, BURROWED -> true;
            case BURROW -> t >= d.dive();
            case POPUP -> t < d.popupLand();
            default -> false;
        };
    }

    /** Wakes a sleeping fighting Rat King: he startles awake (he can't be hurt while he does), then the fight begins. */
    public void wakeUp()
    {
        if (!this.isAsleep() || !this.isAlive())
        {
            return;
        }
        this.awake = true;
        this.play(RatKingAction.WAKE);
    }

    /** His health at which he flies into a rage. */
    float rageHealth()
    {
        return RatKingTuning.RAGE_AT * this.getMaxHealth();
    }

    /** Sets his speed for his phase (the first, or the Rage). */
    void applyStats()
    {
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null)
        {
            speed.setBaseValue(this.isEnraged() ? RatKingTuning.RAGE_SPEED : RatKingTuning.SPEED);
        }
    }

    // ---------------------------------------------------------------- the fight: damage, burrows and the rage

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        // Asleep: anything that hurts him only wakes him (no damage, no knockback).
        if (!this.level().isClientSide && this.isAsleep() && this.isAlive() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                && !this.isInvulnerableTo(source))
        {
            if (source.getEntity() instanceof LivingEntity attacker && attacker != this)
            {
                this.setLastHurtByMob(attacker);
            }
            this.wakeUp();
            return false;
        }
        return super.hurt(source, amount);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        // A showcase Rat King can't be hurt by anything except damage that bypasses invulnerability (/kill, the void).
        if (this.isShowcase() && !bypass)
        {
            return true;
        }
        if (!this.isShowcase() && !bypass && (this.isTransitionInvulnerable() || source.is(DamageTypes.IN_WALL)
                || source.is(DamageTypes.CRAMMING) || source.is(DamageTypes.FALL)))
        {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    /**
     * A hit never takes him past his next burrow or his rage: his health stops there and it starts (damage that
     * bypasses invulnerability, {@code /kill}, isn't stopped).
     */
    @Override
    protected void actuallyHurt(DamageSource source, float amount)
    {
        if (this.isShowcase() || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            super.actuallyHurt(source, amount);
            return;
        }
        float floor = this.healthFloor();
        super.actuallyHurt(source, amount);
        this.noteCredit(source);
        if (floor > 0.0F && this.getHealth() <= floor)
        {
            this.setHealth(floor);
            if (this.burrow.isActive())
            {
                return; // one burrow at a time: he stays at its checkpoint until he's back up
            }
            if (!this.isEnraged() && floor <= this.rageHealth() + EPSILON)
            {
                this.beginRage();
            }
            else
            {
                this.beginBurrow(floor);
            }
        }
    }

    /** The health his next hit stops at: his burrow's checkpoint while he burrows, else the next burrow or the rage. */
    float healthFloor()
    {
        if (this.burrow.isActive())
        {
            return this.burrow.floor();
        }
        float checkpoint = this.nextBurrow();
        return this.isEnraged() ? checkpoint : Math.max(this.rageHealth(), checkpoint);
    }

    /** The health his next burrow comes at (0: none before the rage, or before the end). */
    float nextBurrow()
    {
        if (Float.isNaN(this.burrowAt) || this.burrowAt >= this.getHealth() - EPSILON)
        {
            this.burrowAt = this.checkpointBelow(this.getHealth());
        }
        return this.burrowAt;
    }

    /**
     * The first burrow checkpoint below this health: every {@link RatKingTuning#BURROW_EVERY} down from his full health
     * in the first phase, every {@link RatKingTuning#RAGE_BURROW_EVERY} down from the rage in the Rage. 0 if none is
     * left (the rage, or his death, comes first).
     */
    float checkpointBelow(float health)
    {
        boolean rage = this.isEnraged();
        float start = rage ? this.rageHealth() : this.getMaxHealth();
        float every = rage ? RatKingTuning.RAGE_BURROW_EVERY : RatKingTuning.BURROW_EVERY;
        int steps = Mth.floor((start - health) / every + EPSILON) + 1;
        float checkpoint = start - steps * every;
        float bottom = rage ? 0.0F : this.rageHealth();
        return checkpoint > bottom + EPSILON ? checkpoint : 0.0F;
    }

    /** He has reached a burrow checkpoint: he stops what he's doing, digs and dives in ({@link RatKingBurrow}). */
    private void beginBurrow(float checkpoint)
    {
        if (this.attackGoal != null)
        {
            this.attackGoal.interrupt();
        }
        this.getNavigation().stop();
        this.burrowAt = this.checkpointBelow(checkpoint);
        this.burrow.begin(checkpoint);
        this.startAction(RatKingAction.BURROW);
    }

    /** He has reached his rage: he stops what he's doing and flies into it (he can't be hurt while it plays). */
    private void beginRage()
    {
        if (this.attackGoal != null)
        {
            this.attackGoal.interrupt();
        }
        this.getNavigation().stop();
        this.play(RatKingAction.RAGE); // his eyes turn red
        this.burrowAt = Float.NaN; // the Rage counts its burrows from here
    }

    /** Remembers which player gets the credit for a hit: theirs, their arrow's, their pet's or their risen undead's. */
    private void noteCredit(DamageSource source)
    {
        UUID player = creditFor(source);
        if (player != null)
        {
            this.creditPlayer = player;
            this.creditTick = this.tickCount;
        }
    }

    @Nullable
    private static UUID creditFor(DamageSource source)
    {
        Entity attacker = source.getEntity();
        Entity direct = source.getDirectEntity();
        if (attacker instanceof Player player)
        {
            return player.getUUID();
        }
        UUID owner = RisenUndead.getOwnerId(attacker);
        if (owner == null)
        {
            owner = RisenUndead.getOwnerId(direct);
        }
        if (owner == null && direct != null && direct.getPersistentData().hasUUID(RisenUndead.PROJECTILE_OWNER_KEY))
        {
            owner = direct.getPersistentData().getUUID(RisenUndead.PROJECTILE_OWNER_KEY);
        }
        if (owner == null && attacker instanceof OwnableEntity pet)
        {
            owner = pet.getOwnerUUID();
        }
        return owner;
    }

    /**
     * Everyone who helped defeat him: the player credited with the kill (wherever they are) and every player within his
     * boss bar's range ({@link RatKingTuning#BOSS_BAR_RANGE}; not spectators), each once.
     */
    List<ServerPlayer> victors(DamageSource source)
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return List.of();
        }
        Map<UUID, ServerPlayer> victors = new LinkedHashMap<>();
        UUID killer = creditFor(source);
        if (killer == null && this.creditPlayer != null && this.tickCount - this.creditTick <= RatKingTuning.CREDIT_TICKS)
        {
            killer = this.creditPlayer;
        }
        if (killer == null && this.getLastHurtByMob() instanceof Player player)
        {
            killer = player.getUUID();
        }
        if (killer != null)
        {
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(killer);
            if (player != null)
            {
                victors.put(player.getUUID(), player);
            }
        }
        double range = RatKingTuning.BOSS_BAR_RANGE;
        for (ServerPlayer player : level.players())
        {
            if (!player.isSpectator() && player.distanceToSqr(this) <= range * range)
            {
                victors.putIfAbsent(player.getUUID(), player);
            }
        }
        return List.copyOf(victors.values());
    }

    /**
     * Everyone who helped defeat him ({@link #victors}) gets "Down the Drain" and their own trophy
     * ({@link RatKingTuning#TROPHY_DROP_DESIGN}), straight into their inventory; with a full inventory it's dropped a
     * block in front of them, and for a player who's dead (waiting to respawn) where they died. A dropped one is theirs
     * alone and nothing can destroy it ({@link RatKingTrophies#giveTo}, shared with the Cinder Colossus).
     */
    private void rewardVictors(DamageSource source)
    {
        for (ServerPlayer player : this.victors(source))
        {
            DrossAdvancements.grant(player, DrossAdvancements.DOWN_THE_DRAIN);
            RatKingTrophies.giveTo(player, RatKingTuning.TROPHY_DROP_DESIGN);
        }
    }

    // ---------------------------------------------------------------- the fight: his rats

    /** His summoned rats that are still alive near him. */
    List<Rat> summonedRats()
    {
        AABB area = this.getBoundingBox().inflate(RatKingTuning.RAT_RANGE);
        return this.level().getEntitiesOfClass(Rat.class, area, rat -> rat.isAlive() && rat.belongsTo(this));
    }

    /** The Rage Wand ({@code boss.rage}): when he's raged, his rats join his team. */
    @Override
    public List<? extends Mob> rageFollowers()
    {
        return this.summonedRats();
    }

    /** The Rage Wand: raging him while he sleeps wakes him (then he fights the other raged mobs with his own moves). */
    @Override
    public void onRaged()
    {
        this.wakeUp();
    }

    /** His rats crumble when he dies. */
    private void crumbleRats()
    {
        for (Rat rat : this.summonedRats())
        {
            rat.crumble();
        }
    }

    // ---------------------------------------------------------------- the fight: dying

    /**
     * A fighting Rat King plays his silly death and lies there (see {@link #tickDeath}); his rats crumble, his boss bar
     * goes, and the players who beat him get "Down the Drain" and a trophy each (not when he's removed with
     * {@code /kill}).
     */
    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (this.level().isClientSide || !this.dead || this.isShowcase())
        {
            return;
        }
        this.burrow.finish();
        this.startAction(RatKingAction.DEATH);
        if (this.bossBar != null)
        {
            this.bossBar.setProgress(0.0F);
            this.bossBar.removeAllPlayers();
        }
        this.crumbleRats();
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            this.rewardVictors(source);
        }
    }

    /**
     * Vanilla removes a dead mob after 20 ticks; a fighting Rat King lies there through his death and
     * {@link RatKingTuning#CORPSE_TICKS} more, then vanishes in a puff of smoke. (A showcase king killed with
     * {@code /kill} goes the vanilla way.)
     */
    @Override
    protected void tickDeath()
    {
        if (this.isShowcase())
        {
            super.tickDeath();
            return;
        }
        this.deathTime = Math.min(this.deathTime + 1, Short.MAX_VALUE);
        int end = this.getDesign().deathTicks() + ANIMATION_BLEND_TICKS + RatKingTuning.CORPSE_TICKS;
        if (!this.level().isClientSide && !this.isRemoved() && this.deathTime >= end)
        {
            this.vanish();
        }
    }

    /** His body vanishes in a puff of smoke and dust. */
    private void vanish()
    {
        if (this.level() instanceof ServerLevel level)
        {
            AABB box = this.getBoundingBox();
            Vec3 middle = box.getCenter();
            level.sendParticles(ParticleTypes.POOF, middle.x, box.minY + 0.4D, middle.z, VANISH_POOF, 1.0D, 0.3D, 1.0D, 0.03D);
            level.sendParticles(ParticleTypes.LARGE_SMOKE, middle.x, box.minY + 0.5D, middle.z, VANISH_SMOKE, 0.8D, 0.3D, 0.8D, 0.02D);
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, this.ground()), middle.x, box.minY + 0.2D, middle.z,
                    VANISH_POOF, 1.0D, 0.2D, 1.0D, 0.1D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.RAT_KING_POP.get(), this.getSoundSource(),
                    VANISH_VOLUME, 0.8F);
        }
        this.remove(RemovalReason.KILLED);
    }

    // ---------------------------------------------------------------- the fight: boss bar

    /** Shows his boss bar to the players near him (and hides it from the ones who left); keeps its name and fill up to date. */
    private void updateBossBar()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        boolean rage = this.isEnraged()
                && !(this.getAction() == RatKingAction.RAGE && this.animationTicks() < this.getDesign().rageEyes());
        Component name = Component.translatable(rage ? RatKingTuning.BAR_KEY_RAGE : RatKingTuning.BAR_KEY);
        BossEvent.BossBarColor color = rage ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN;
        if (this.bossBar == null)
        {
            this.bossBar = new ServerBossEvent(name, color, BossEvent.BossBarOverlay.PROGRESS);
            this.bossBar.setDarkenScreen(false);
            this.bossBar.setPlayBossMusic(false);
            this.bossBar.setCreateWorldFog(false);
        }
        if (!name.equals(this.bossBar.getName()))
        {
            this.bossBar.setName(name);
            this.bossBar.setColor(color);
        }
        this.bossBar.setProgress(Mth.clamp(this.getHealth() / this.getMaxHealth(), 0.0F, 1.0F));
        double range = RatKingTuning.BOSS_BAR_RANGE;
        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(this) <= range * range)
            {
                this.bossBar.addPlayer(player);
            }
            else
            {
                this.bossBar.removePlayer(player);
            }
        }
        for (ServerPlayer player : new ArrayList<>(this.bossBar.getPlayers()))
        {
            if (player.isRemoved() || player.level() != level)
            {
                this.bossBar.removePlayer(player);
            }
        }
    }

    /** His boss bar (null until he has woken up). For {@link RatKingFightGameTest}. */
    @Nullable
    ServerBossEvent bossBar()
    {
        return this.bossBar;
    }

    @Override
    public void stopSeenByPlayer(ServerPlayer player)
    {
        super.stopSeenByPlayer(player);
        if (this.bossBar != null)
        {
            this.bossBar.removePlayer(player);
        }
    }

    @Override
    public void remove(RemovalReason reason)
    {
        if (this.bossBar != null)
        {
            this.bossBar.removeAllPlayers();
        }
        super.remove(reason);
    }

    // ---------------------------------------------------------------- the fight: helpers for his moves

    /** His fighting AI (for {@link RatKingFightGameTest}, which starts moves itself). Null only before his goals exist. */
    @Nullable
    RatKingAttackGoal attackGoal()
    {
        return this.attackGoal;
    }

    /** Wakes him straight into the fight, standing (no wake-up animation). For {@link RatKingFightGameTest}. */
    void startFightNow()
    {
        this.awake = true;
        this.startAction(RatKingAction.IDLE);
    }

    /** Straight into the Rage (no rage animation), his health at the rage. For {@link RatKingFightGameTest}. */
    void skipToRage()
    {
        this.setHealth(Math.min(this.getHealth(), this.rageHealth()));
        this.entityData.set(DATA_ENRAGED, true);
        this.burrowAt = Float.NaN;
        this.applyStats();
        this.startAction(RatKingAction.IDLE);
    }

    /** The way he faces, flat (a unit vector). */
    Vec3 forward()
    {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    /** His right side, flat (a unit vector). */
    Vec3 right()
    {
        Vec3 f = this.forward();
        return new Vec3(-f.z, 0.0D, f.x);
    }

    /** Turns him (body, head and all) towards a spot, at most {@code maxDegrees} this tick. */
    void turnTowards(Vec3 spot, float maxDegrees)
    {
        double dx = spot.x - this.getX();
        double dz = spot.z - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4D)
        {
            return;
        }
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        this.face(Mth.approachDegrees(this.getYRot(), wanted, maxDegrees));
    }

    /** Faces him this way at once (body, head and all). */
    void face(float yaw)
    {
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.yRotO = yaw;
    }

    /** How fast he turns to face his target during a move (degrees a tick). */
    float turnSpeed()
    {
        return this.isEnraged() ? RatKingTuning.RAGE_TURN_SPEED : RatKingTuning.TURN_SPEED;
    }

    /**
     * Turns him towards his target (if he has a live one here) at his {@link #turnSpeed}: while he's busy (the rage,
     * the start of a burrow, popping up), when his fighting AI doesn't turn him itself.
     */
    private void trackTarget()
    {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && target.level() == this.level())
        {
            this.turnTowards(target.position(), this.turnSpeed());
        }
    }

    /**
     * How far (degrees) his head may turn away from his body: hardly at all, so where he looks his whole body turns
     * (his model has no separate head turn, and Minecraft's usual 40 degrees let his body lag behind his eyes).
     */
    @Override
    public int getMaxHeadYRot()
    {
        return HEAD_TURN;
    }

    /** Where a poison ball leaves his staff's orb in his cast (in the world, as he stands now). */
    Vec3 castPoint()
    {
        RatKingDesigns.Design d = this.getDesign();
        return this.position().add(this.forward().scale(d.castForward())).add(0.0D, d.castUp(), 0.0D)
                .add(this.right().scale(d.castRight()));
    }

    /**
     * Something his blows may hit: alive, living, not himself, not a spectator; not another Rat King or a rat, unless
     * the Rage Wand has made that one his enemy ({@link Rage#areEnemies}: raged, on another team).
     */
    boolean canHit(Entity entity)
    {
        if (entity == this || !entity.isAlive() || !(entity instanceof LivingEntity) || entity.isSpectator())
        {
            return false;
        }
        return !(entity instanceof RatKing) && !(entity instanceof Rat) || Rage.areEnemies(this, entity);
    }

    /** A damage source for his blows (scaled by the difficulty for players, like every mob's). */
    DamageSource blow()
    {
        return this.damageSources().mobAttack(this);
    }

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.spawnParticles();
            return;
        }
        RatKingDesigns.Design d = this.getDesign();
        int t = this.animationTicks();
        if (this.isShowcase())
        {
            this.showcaseTick(d, t);
        }
        else
        {
            this.fightTick(d, t);
        }
        this.playActionSounds(d, t);
    }

    /** A showcase king's once-only animations end in their resting state. */
    private void showcaseTick(RatKingDesigns.Design d, int t)
    {
        switch (this.getAction())
        {
            case WAKE -> {
                if (t >= d.wakeTicks() + SETTLE_TICKS) this.startAction(RatKingAction.IDLE);
            }
            case POPUP -> {
                if (t >= d.popupTicks() + SETTLE_TICKS) this.startAction(RatKingAction.IDLE);
            }
            case RAGE -> {
                if (t >= d.rageTicks() + SETTLE_TICKS) this.startAction(RatKingAction.IDLE);
            }
            case BURROW -> {
                if (t >= d.burrowTicks()) this.startAction(RatKingAction.BURROWED);
            }
            case DEATH -> {
                if (t >= d.deathTicks()) this.startAction(RatKingAction.DEAD);
            }
            // The spear charge on the spot: the wind-up, a dash, the skid.
            case CHARGE_START -> {
                if (t >= d.chargeStartTicks() + SETTLE_TICKS)
                {
                    this.startAction(RatKingAction.CHARGE);
                    this.sound(ModSounds.RAT_KING_CHARGE.get(), SHOWCASE_CHARGE_VOLUME);
                }
            }
            case CHARGE -> {
                if (t >= SHOWCASE_DASH_TICKS) this.startAction(RatKingAction.CHARGE_SKID);
            }
            case CHARGE_SKID -> {
                if (t >= d.chargeSkidTicks() + SETTLE_TICKS) this.startAction(RatKingAction.IDLE);
            }
            default -> {
            }
        }
    }

    /** A fighting king: sleeping until woken, the wake-up, the rage, his burrows, and his boss bar. */
    private void fightTick(RatKingDesigns.Design d, int t)
    {
        RatKingAction action = this.getAction();
        if (!this.awake && action != RatKingAction.SLEEP && action != RatKingAction.WAKE && !action.isDead() && this.isAlive())
        {
            // One that hasn't been woken yet sleeps (made without its spawn setup, or saved before the fight existed).
            this.startAction(RatKingAction.SLEEP);
            action = RatKingAction.SLEEP;
        }
        boolean under = action == RatKingAction.BURROWED;
        this.noPhysics = under;
        this.setNoGravity(under);
        switch (action)
        {
            case SLEEP -> this.checkWake();
            case WAKE -> {
                if (t >= d.wakeTicks() + SETTLE_TICKS) this.startAction(RatKingAction.IDLE);
            }
            case RAGE -> {
                this.trackTarget(); // his tantrum is aimed at his target: he keeps glaring at it
                if (t >= d.rageTicks() + SETTLE_TICKS)
                {
                    this.startAction(RatKingAction.IDLE);
                    this.applyStats(); // the Rage's speed from now on
                }
            }
            case BURROW -> {
                // First a quick turn to face his target, then he digs and dives in (forward, the way he faces).
                if (t < d.digFrom()) this.trackTarget();
                if (t >= d.burrowTicks()) this.burrow.goUnder();
            }
            case BURROWED -> this.burrow.tickUnder();
            case POPUP -> {
                // He comes straight up out of the hole under him, keeping his eyes on his target, and lands facing it.
                this.trackTarget();
                if (t >= d.popupTicks() + SETTLE_TICKS)
                {
                    this.startAction(RatKingAction.IDLE);
                    this.burrow.finish();
                }
            }
            case SWEEP -> {
                if (t >= d.sweepTicks() + MOVE_SAFETY_TICKS) this.startAction(RatKingAction.IDLE);
            }
            case CAST -> {
                if (t >= d.castTicks() + MOVE_SAFETY_TICKS) this.startAction(RatKingAction.IDLE);
            }
            case CHARGE_START -> {
                if (t >= RatKingTuning.CHARGE_WINDUP_TICKS + MOVE_SAFETY_TICKS) this.startAction(RatKingAction.IDLE);
            }
            case CHARGE -> {
                if (t >= maxDashTicks() + MOVE_SAFETY_TICKS) this.startAction(RatKingAction.IDLE);
            }
            case CHARGE_SKID -> {
                if (t >= RatKingTuning.CHARGE_RECOVER_TICKS + MOVE_SAFETY_TICKS) this.startAction(RatKingAction.IDLE);
            }
            case DEATH -> {
                if (t >= d.deathTicks()) this.startAction(RatKingAction.DEAD);
            }
            default -> {
            }
        }
        if (this.isFightActive())
        {
            this.updateBossBar();
        }
    }

    /** The longest a charge's dash can last (ticks): his whole run at his slower dash speed, and its slack. */
    private static int maxDashTicks()
    {
        double slowest = Math.min(RatKingTuning.CHARGE_SPEED, RatKingTuning.RAGE_CHARGE_SPEED);
        return (int) Math.ceil(RatKingTuning.CHARGE_MAX_DISTANCE / slowest) + RatKingTuning.CHARGE_SLACK_TICKS;
    }

    /** Asleep: a player (not a spectator) close enough wakes him; a sneaking one has to come closer. */
    private void checkWake()
    {
        if (!this.isAlive())
        {
            return;
        }
        for (Player player : this.level().players())
        {
            if (player.isSpectator() || !player.isAlive())
            {
                continue;
            }
            double range = player.isShiftKeyDown() ? RatKingTuning.WAKE_RANGE_SNEAKING : RatKingTuning.WAKE_RANGE;
            if (player.distanceToSqr(this) <= range * range)
            {
                this.wakeUp();
                return;
            }
        }
    }

    /** Server: the sounds his animations are timed for (for everyone near him, even when he's a silent showcase king). */
    private void playActionSounds(RatKingDesigns.Design d, int t)
    {
        switch (this.getAction())
        {
            case SLEEP -> {
                if (t >= 0 && t % d.sleepTicks() == SNORE_AT) this.sound(ModSounds.RAT_KING_SNORE.get(), SNORE_VOLUME);
            }
            case WAKE -> {
                if (t == d.wakeStartle()) this.sound(ModSounds.RAT_KING_STARTLE.get(), 1.0F);
                if (t == d.wakeYawn()) this.sound(ModSounds.RAT_KING_YAWN.get(), 1.0F);
                if (t == d.wakeGrab()) this.sound(ModSounds.RAT_KING_STAFF_GRAB.get(), 1.0F);
            }
            case WALK -> {
                int half = Math.max(1, d.walkTicks() / 2);
                if (t >= 0 && t % half == 0) this.sound(ModSounds.RAT_KING_STEP.get(), STEP_VOLUME);
            }
            case BURROW -> {
                if (t == d.digFrom()) this.sound(ModSounds.RAT_KING_DIG.get(), 1.0F);
                if (t == d.dive()) this.sound(ModSounds.RAT_KING_DIVE.get(), 1.0F);
            }
            case POPUP -> {
                if (t == 0) this.sound(ModSounds.RAT_KING_RUMBLE.get(), 1.0F);
                if (t == d.popupBurst()) this.sound(ModSounds.RAT_KING_POP.get(), 1.0F);
            }
            case RAGE -> {
                for (int s : d.rageStomps())
                {
                    if (t == s) this.sound(ModSounds.RAT_KING_STOMP.get(), 1.0F);
                }
                if (!d.rageSteam().isEmpty() && t == d.rageSteam().get(0)) this.sound(rageWhistle(), 1.0F);
                if (t == d.rageScreech()) this.sound(ModSounds.RAT_KING_SCREECH.get(), 1.0F);
                if (t == d.rageSlam()) this.sound(ModSounds.RAT_KING_STAFF_SLAM.get(), 1.0F);
            }
            case DEATH -> {
                if (t == 0) this.sound(ModSounds.RAT_KING_DEATH.get(), 1.0F);
                if (t == d.deathFall()) this.sound(ModSounds.RAT_KING_THUD.get(), 1.0F);
                if (t == d.deathClatter()) this.sound(ModSounds.RAT_KING_STAFF_CLATTER.get(), 1.0F);
                if (t == d.deathCrown()) this.sound(ModSounds.RAT_KING_CROWN_CLINK.get(), 1.0F);
            }
            default -> {
            }
        }
    }

    /** The train whistle his rage plays: the version picked in {@link RatKingSounds#RAGE_WHISTLE}. */
    private static SoundEvent rageWhistle()
    {
        return switch (RatKingSounds.RAGE_WHISTLE)
        {
            case 2 -> ModSounds.RAT_KING_RAGE_WHISTLE_2.get();
            case 3 -> ModSounds.RAT_KING_RAGE_WHISTLE_3.get();
            default -> ModSounds.RAT_KING_RAGE_WHISTLE_1.get();
        };
    }

    private void sound(SoundEvent event, float volume)
    {
        this.sound(event, volume, 1.0F);
    }

    /** Plays one of his sounds where he stands, for everyone near (even when he's a silent showcase king). */
    void sound(SoundEvent event, float volume, float pitch)
    {
        this.level().playSound(null, this.getX(), this.getY(), this.getZ(), event, this.getSoundSource(), volume, pitch);
    }

    @Override
    @Nullable
    protected SoundEvent getAmbientSound()
    {
        return this.isAsleep() ? null : ModSounds.RAT_KING_AMBIENT.get(); // asleep he only snores
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.RAT_KING_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.RAT_KING_DEATH.get();
    }

    /** His running footsteps (quick and light). */
    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        if (!this.isUnderground())
        {
            this.playSound(ModSounds.RAT_KING_STEP.get(), STEP_VOLUME, RUN_STEP_PITCH);
        }
    }

    @Override
    public void onSyncedDataUpdated(EntityDataAccessor<?> key)
    {
        super.onSyncedDataUpdated(key);
        if (DATA_ACTION_SERIAL.equals(key))
        {
            this.clientActionStart = this.tickCount;
        }
    }

    /** Client: ticks into the current action's animation (GeckoLib blends into it first, so it starts a little late). */
    public float clientAnimationTicks(float partialTick)
    {
        return this.tickCount - this.clientActionStart + partialTick - ANIMATION_BLEND_TICKS;
    }

    /** Client: how strongly his eyes glow red (the renderer's rage layer): fading in during the rage, off when dead. */
    public float rageStrength(float partialTick)
    {
        RatKingDesigns.Design d = this.getDesign();
        float t = this.clientAnimationTicks(partialTick);
        return switch (this.getAction())
        {
            case RAGE -> smoothStep(d.rageEyes(), d.rageEyes() + 5.0F, t);
            case DEATH -> this.isEnraged() ? 1.0F - smoothStep(d.deathXEyes() - 3.0F, d.deathXEyes(), t) : 0.0F;
            case DEAD, SLEEP -> 0.0F;
            default -> this.isEnraged() ? 1.0F : 0.0F;
        };
    }

    private static float smoothStep(float from, float to, float x)
    {
        float k = Mth.clamp((x - from) / (to - from), 0.0F, 1.0F);
        return k * k * (3.0F - 2.0F * k);
    }

    /** Client: the renderer tells him where his head's Zzz spot and his ears are each time it draws him. */
    public void setMarkers(@Nullable Vec3 zzz, @Nullable Vec3 earL, @Nullable Vec3 aimL, @Nullable Vec3 earR, @Nullable Vec3 aimR)
    {
        this.zzzSpot = zzz;
        this.earSpots[0] = earL;
        this.earAims[0] = aimL;
        this.earSpots[1] = earR;
        this.earAims[1] = aimR;
    }

    /** Client: the particles his animations are timed for (see the class comment). */
    private void spawnParticles()
    {
        RatKingDesigns.Design d = this.getDesign();
        int t = Mth.floor(this.clientAnimationTicks(0.0F));
        switch (this.getAction())
        {
            case SLEEP -> {
                if (this.tickCount % ZZZ_PERIOD == 0) this.zzz();
            }
            case WAKE -> {
                if (t < d.wakeStartle() && this.tickCount % ZZZ_PERIOD == 0) this.zzz();
            }
            case BURROW -> {
                // The hole he digs and dives into is in front of him.
                if (t >= d.digFrom() && t < d.digTo() && this.tickCount % 2 == 0) this.dirt(DIG_DIRT, 0.22D, RatKingDesigns.HOLE_FORWARD);
                if (t >= d.dive() && t < d.dive() + 16 && this.tickCount % 2 == 0) this.dirt(DIVE_DIRT, 0.28D, RatKingDesigns.HOLE_FORWARD);
            }
            case BURROWED -> {
                // Tunnelling (a fighting king): a trail of dirt thrown up over the ground where he goes.
                if (!this.isShowcase() && (Math.abs(this.getX() - this.xo) > 1.0E-3D || Math.abs(this.getZ() - this.zo) > 1.0E-3D))
                {
                    this.trail();
                }
            }
            case POPUP -> {
                // The hole he pops up out of is right under him.
                if (t >= 0 && t < d.popupPeek() && this.tickCount % 4 == 0) this.dirt(1, 0.08D, d.popupHoleForward());
                if (t == d.popupBurst()) this.dirt(BURST_DIRT, 0.42D, d.popupHoleForward());
            }
            case RAGE -> {
                for (int s : d.rageSteam())
                {
                    if (t >= s && t < s + 2) this.steam();
                }
                for (int s : d.rageStomps())
                {
                    if (t == s) this.dust(STOMP_DUST, 0.6D);
                }
                if (t == d.rageSlam()) this.slamBurst(d);
            }
            case DEATH -> {
                if (t == d.deathFall()) this.dust(THUD_DUST, 2.2D);
            }
            case CHARGE -> {
                if (this.tickCount % 2 == 0) this.dust(DASH_DUST, 0.5D);
            }
            case CHARGE_SKID -> {
                if (t == 0) this.dust(SKID_DUST, 1.2D);
            }
            default -> {
            }
        }
    }

    private void zzz()
    {
        Vec3 at = this.zzzSpot != null ? this.zzzSpot : this.position().add(this.forward().scale(0.5D)).add(0.0D, 3.6D, 0.0D);
        RandomSource r = this.random;
        this.level().addParticle(ModParticles.ZZZ.get(), at.x + (r.nextDouble() - 0.5D) * 0.2D, at.y,
                at.z + (r.nextDouble() - 0.5D) * 0.2D, 0.0D, 0.0D, 0.0D);
    }

    private void steam()
    {
        RandomSource r = this.random;
        for (int i = 0; i < 2; i++)
        {
            Vec3 at = this.earSpots[i];
            Vec3 aim = this.earAims[i];
            if (at == null || aim == null)
            {
                Vec3 side = new Vec3(this.forward().z, 0.0D, -this.forward().x).scale(i == 0 ? 0.35D : -0.35D);
                at = this.position().add(side).add(0.0D, 3.4D, 0.0D);
                aim = side.normalize().add(0.0D, 1.0D, 0.0D).normalize();
            }
            for (int k = 0; k < STEAM_PER_EAR; k++)
            {
                double speed = STEAM_SPEED * (0.7D + r.nextDouble() * 0.6D);
                this.level().addParticle(ParticleTypes.CLOUD, at.x, at.y, at.z,
                        aim.x * speed + (r.nextDouble() - 0.5D) * 0.03D, aim.y * speed + (r.nextDouble() - 0.5D) * 0.03D,
                        aim.z * speed + (r.nextDouble() - 0.5D) * 0.03D);
            }
        }
    }

    /** The block his hole is dug into (dirt if it's air). */
    BlockState ground()
    {
        BlockState state = this.level().getBlockState(BlockPos.containing(this.getX(), this.getY() - 0.2D, this.getZ()));
        return state.isAir() ? Blocks.DIRT.defaultBlockState() : state;
    }

    /** Dirt flung up out of his hole, {@code forward} blocks in front of his feet. */
    private void dirt(int count, double speed, double forward)
    {
        RandomSource r = this.random;
        Vec3 hole = this.position().add(this.forward().scale(forward)).add(0.0D, 0.15D, 0.0D);
        BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, this.ground());
        for (int k = 0; k < count; k++)
        {
            double a = r.nextDouble() * Math.PI * 2.0D;
            this.level().addParticle(block, hole.x, hole.y, hole.z, Math.cos(a) * speed * 0.6D,
                    speed * (0.8D + r.nextDouble()), Math.sin(a) * speed * 0.6D);
        }
    }

    /** Dirt thrown up over the ground above him as he tunnels (at his mound, and a little behind it). */
    private void trail()
    {
        RandomSource r = this.random;
        BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, this.ground());
        Vec3 mound = this.position().add(this.forward().scale(RatKingDesigns.HOLE_FORWARD));
        for (int k = 0; k < TRAIL_DIRT; k++)
        {
            Vec3 at = mound.add(this.forward().scale(-r.nextDouble() * 0.8D));
            this.level().addParticle(block, at.x + (r.nextDouble() - 0.5D) * 0.5D, at.y + 0.1D,
                    at.z + (r.nextDouble() - 0.5D) * 0.5D, (r.nextDouble() - 0.5D) * 0.08D,
                    TRAIL_SPEED * (0.6D + r.nextDouble()), (r.nextDouble() - 0.5D) * 0.08D);
        }
    }

    /** Dust and green sparks where his staff's foot strikes the ground in the rage. */
    private void slamBurst(RatKingDesigns.Design d)
    {
        Vec3 f = this.forward();
        Vec3 right = new Vec3(-f.z, 0.0D, f.x);
        Vec3 at = this.position().add(f.scale(d.slamForward())).add(right.scale(d.slamRight()));
        RandomSource r = this.random;
        BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, this.ground());
        for (int k = 0; k < SLAM_DUST; k++)
        {
            double a = r.nextDouble() * Math.PI * 2.0D;
            this.level().addParticle(block, at.x + Math.cos(a) * 0.3D, at.y + 0.1D, at.z + Math.sin(a) * 0.3D,
                    Math.cos(a) * 0.15D, 0.15D + r.nextDouble() * 0.1D, Math.sin(a) * 0.15D);
        }
        DustParticleOptions spark = new DustParticleOptions(Vec3.fromRGB24(DrossColors.RAT_KING_SLAM_SPARK).toVector3f(),
                SLAM_SPARK_SIZE);
        for (int k = 0; k < SLAM_SPARKS; k++)
        {
            this.level().addParticle(spark, at.x, at.y + 0.2D, at.z, (r.nextDouble() - 0.5D) * 0.4D,
                    r.nextDouble() * 0.35D, (r.nextDouble() - 0.5D) * 0.4D);
        }
    }

    private void dust(int count, double radius)
    {
        RandomSource r = this.random;
        BlockParticleOption block = new BlockParticleOption(ParticleTypes.BLOCK, this.ground());
        for (int k = 0; k < count; k++)
        {
            double a = r.nextDouble() * Math.PI * 2.0D;
            double d = r.nextDouble() * radius;
            this.level().addParticle(block, this.getX() + Math.cos(a) * d, this.getY() + 0.1D, this.getZ() + Math.sin(a) * d,
                    Math.cos(a) * 0.08D, 0.12D, Math.sin(a) * 0.08D);
        }
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(CULL_MARGIN, 1.0D, CULL_MARGIN);
    }

    // ---------------------------------------------------------------- animation (GeckoLib)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        AnimationController<RatKing> main = new AnimationController<>(this, "main", ANIMATION_BLEND_TICKS, this::animate);
        main.setAnimationSpeedHandler(king -> king.animationSpeed());
        controllers.add(main);
    }

    /** True while a fighting king runs about standing (the run plays then). */
    private boolean isRunningShown()
    {
        return !this.isShowcase() && this.getAction() == RatKingAction.IDLE && this.walkAnimation.speed() > 0.05F;
    }

    /**
     * 1, except while a fighting king runs or dashes: then his run (or the charge's dash) plays as fast as he's moving,
     * so his feet don't slide.
     */
    private double animationSpeed()
    {
        RatKingDesigns.Design d = this.getDesign();
        boolean dashing = !this.isShowcase() && this.getAction() == RatKingAction.CHARGE;
        if (!dashing && !this.isRunningShown())
        {
            return 1.0D;
        }
        double natural = dashing ? d.chargeBlocks() / Math.max(1, d.chargeTicks()) : d.runBlocks() / Math.max(1, d.runTicks());
        double moved = Math.sqrt(Mth.square(this.getX() - this.xo) + Mth.square(this.getZ() - this.zo));
        return Mth.clamp(moved / natural, MIN_RUN_SPEEDUP, MAX_RUN_SPEEDUP);
    }

    private PlayState animate(AnimationState<RatKing> state)
    {
        int serial = this.entityData.get(DATA_ACTION_SERIAL);
        if (serial != this.serialSeen)
        {
            // A new action (or the same one again): start its animation from the beginning.
            this.serialSeen = serial;
            state.getController().forceAnimationReset();
        }
        if (this.isRunningShown())
        {
            return state.setAndContinue(ANIMATIONS.get(RatKingAction.RUN));
        }
        return state.setAndContinue(ANIMATIONS.get(this.getAction()));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }

    // ---------------------------------------------------------------- spawning

    /**
     * Spawns a frozen showcase Rat King of a design, standing.
     *
     * @param feet where his feet go (the bottom middle of the air block above the ground)
     * @param yaw  the way he faces, in degrees (0 = south, 90 = west, 180 = north, -90 = east)
     * @return the Rat King, already in the world, or null if he couldn't be created
     */
    @Nullable
    public static RatKing spawnShowcase(ServerLevel level, Vec3 feet, float yaw, RatKingDesigns.Design design)
    {
        RatKing king = ModEntities.RAT_KING.get().create(level);
        if (king == null)
        {
            return null;
        }
        king.entityData.set(DATA_SHOWCASE, true);
        king.setDesign(design.number());
        king.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        king.setYHeadRot(yaw);
        king.setYBodyRot(yaw);
        king.yHeadRotO = yaw;
        king.yBodyRotO = yaw;
        king.setNoAi(true);
        king.setSilent(true);
        king.setPersistenceRequired();
        level.addFreshEntity(king);
        return king;
    }

    /**
     * Spawns a fighting Rat King (the user's pick, Gnawrick 1C), asleep, facing {@code yaw}. Used by
     * {@code /dross boss summon ratking}; his spawn egg makes one the same way.
     *
     * @return the Rat King, already in the world, or null if he couldn't be created
     */
    @Nullable
    public static RatKing spawnBoss(ServerLevel level, Vec3 feet, float yaw)
    {
        RatKing king = ModEntities.RAT_KING.get().create(level);
        if (king == null)
        {
            return null;
        }
        king.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        king.setYHeadRot(yaw);
        king.setYBodyRot(yaw);
        king.yHeadRotO = yaw;
        king.yBodyRotO = yaw;
        ForgeEventFactory.onFinalizeSpawn(king, level, level.getCurrentDifficultyAt(king.blockPosition()),
                MobSpawnType.COMMAND, null, null);
        level.addFreshEntity(king);
        return king;
    }

    /**
     * Right-clicking a showcase Rat King plays his next animation (sleep, wake up, walk, burrow, pop up, rage, die,
     * stand, and round again); with sneak he dies. His number, name and what he's doing go on the action bar.
     */
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
        RatKingAction next;
        if (player.isShiftKeyDown())
        {
            next = RatKingAction.DEATH;
        }
        else
        {
            this.cycleIndex = (this.cycleIndex + 1) % CYCLE.length;
            next = CYCLE[this.cycleIndex];
        }
        this.play(next);
        RatKingDesigns.Design design = this.getDesign();
        player.displayClientMessage(Component.translatable("dross.rat_king.showcase." + word(next), design.label(),
                design.name(), design.mood()), true);
        return InteractionResult.CONSUME;
    }

    /** Nothing can hit or target him under the ground (projectiles fly over his mound). */
    @Override
    public boolean isPickable()
    {
        return super.isPickable() && !this.isUnderground();
    }

    @Override
    public boolean canBeSeenAsEnemy()
    {
        return super.canBeSeenAsEnemy() && !this.isUnderground();
    }

    /** Nothing shoves him about: a showcase king, or a fighting one (not even a crowd of his own rats). */
    @Override
    public boolean isPushable()
    {
        return false;
    }

    @Override
    public void push(double x, double y, double z)
    {
        if (!this.isShowcase() && !this.isUnderground())
        {
            super.push(x, y, z);
        }
    }

    @Override
    public boolean isPushedByFluid()
    {
        return !this.isShowcase() && !this.isUnderground();
    }

    @Override
    public boolean canBeLeashed(Player player)
    {
        return false;
    }

    @Override
    protected boolean shouldDespawnInPeaceful()
    {
        return !this.isShowcase();
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return false;
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putInt(TAG_DESIGN, this.getDesign().number());
        tag.putString(TAG_ACTION, this.getAction().name());
        tag.putBoolean(TAG_ENRAGED, this.isEnraged());
        if (this.isShowcase())
        {
            tag.putBoolean(TAG_SHOWCASE, true);
            return;
        }
        tag.putBoolean(TAG_AWAKE, this.awake);
        if (!Float.isNaN(this.burrowAt))
        {
            tag.putFloat(TAG_BURROW_AT, this.burrowAt);
        }
        this.burrow.save(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_SHOWCASE, tag.getBoolean(TAG_SHOWCASE));
        this.entityData.set(DATA_ENRAGED, tag.getBoolean(TAG_ENRAGED));
        if (tag.contains(TAG_DESIGN))
        {
            this.setDesign(tag.getInt(TAG_DESIGN));
        }
        this.awake = tag.getBoolean(TAG_AWAKE);
        this.burrowAt = tag.contains(TAG_BURROW_AT) ? tag.getFloat(TAG_BURROW_AT) : Float.NaN;
        // An action that was halfway through when he was saved picks up where it would have ended (a fight move
        // starts again from standing; a fighting king halfway down his hole carries on under the ground).
        RatKingAction saved = RatKingAction.byName(tag.getString(TAG_ACTION));
        RatKingAction action = switch (saved)
        {
            case WAKE, POPUP, RAGE -> RatKingAction.IDLE;
            case BURROW -> RatKingAction.BURROWED;
            case DEATH -> RatKingAction.DEAD;
            default -> saved.isMove() || saved == RatKingAction.RUN ? RatKingAction.IDLE : saved;
        };
        this.startAction(action);
        if (!this.isShowcase())
        {
            this.burrow.load(tag, action == RatKingAction.BURROWED);
            if (!this.isDeadOrDying())
            {
                this.applyStats(); // a rejoin mid-fight: the phase's speed (and his boss bar, made on his next tick) come back
            }
        }
    }
}
