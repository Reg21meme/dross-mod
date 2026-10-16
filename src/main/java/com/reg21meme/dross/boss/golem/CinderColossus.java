package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rage.RageLeader;
import com.reg21meme.dross.boss.trophy.ColossusTrophies;
import com.reg21meme.dross.enchant.RisenUndead;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
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
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
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
import net.minecraft.world.entity.ai.control.MoveControl;
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
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
import java.util.Optional;
import java.util.UUID;

/**
 * The Cinder Colossus: a gorilla-like golem of scorched stone and obsidian with two volcanoes on its back and a lava
 * core, the mini-boss that serves the Fire Necromancer (CLAUDE.md, "The plan", step 8; the design is
 * {@link GolemDesigns}, 3G Saddleback).
 * <p>
 * <b>Its looks</b> ({@link GolemPhase}): dormant (its rock shell, the volcanoes only smoking), erupted (the volcanoes
 * have blown and lava has poured down over the shell) and core (the shell broken off: a lava body with bigger,
 * always-erupting volcanoes). Clients get the design number, the look, the current {@link GolemAction} and a counter
 * that goes up every time an action starts, so a repeated action restarts its animation.
 * <p>
 * Two animation controllers: {@code main} moves the body (idle, walk, the fight's moves, erupt, break_shell, death,
 * statue); {@code volcano} moves the volcanoes' own bones (the eruption's blast, the lob's spurts, the core form's
 * live eruption). The renderer ({@code client.CinderColossusRenderer}) shows or hides bones by their name prefix
 * ({@link #showsBone}), pours the lava over the shell ({@link #flowProgress}), cracks the shell in the fight's middle
 * stage ({@link #crackAmount}) and cools it into obsidian when it dies.
 * <p>
 * <b>The fight</b> (a Colossus from the spawn egg or {@code /dross boss summon colossus}): three stages
 * ({@link ColossusStage}: Shell, Cracking, Lava Core) at {@link ColossusTuning#CRACKING_AT} and
 * {@link ColossusTuning#LAVA_CORE_AT} of its health. A hit never takes it past the next threshold: the health stops
 * there and it plays the change (the eruption, then the shell bursting), during which it can't be hurt. Its moves are
 * run by {@link ColossusAttackGoal}. A boss bar shows for players within {@link ColossusTuning#BOSS_BAR_RANGE} blocks
 * (drawn by {@code boss.client.DrossBossBars}). When it dies it cools into an obsidian statue that stands for
 * {@link ColossusTuning#STATUE_TICKS} ticks and then crumbles; its minions crumble at once. The player who killed it
 * (or whose arrow or risen undead did) and every player within the boss bar's range get the "Set in Stone"
 * advancement. It never despawns (except on Peaceful, like every monster) and never spawns by itself.
 * <p>
 * <b>Showcase golems</b> ({@link #spawnShowcase}, saved with {@code Showcase}) stand frozen (no AI), make no sound,
 * can't be pushed, never despawn (not even on Peaceful) and can't be hurt except by {@code /kill}. They never fight,
 * have no boss bar and never grant the advancement. Right-clicking one moves it on to its next stage (dormant: erupts;
 * erupted: breaks out of its shell; lava or statue: dormant again); sneak + right-click makes it die.
 */
public class CinderColossus extends Monster implements GeoEntity, RageLeader
{
    private static final EntityDataAccessor<Integer> DATA_DESIGN = SynchedEntityData.defineId(CinderColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(CinderColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(CinderColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_SERIAL = SynchedEntityData.defineId(CinderColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHOWCASE = SynchedEntityData.defineId(CinderColossus.class, EntityDataSerializers.BOOLEAN);

    private static final String TAG_DESIGN = "Design";
    private static final String TAG_PHASE = "Phase";
    private static final String TAG_ACTION = "Action";
    private static final String TAG_SHOWCASE = "Showcase";
    /** Saved: it has already roared at its first hit (it only does once). */
    private static final String TAG_ROARED = "Roared";

    /** Ticks it waits after the shell-breaking or erupting animation before the next phase counts (the bits settle). */
    private static final int SETTLE_TICKS = 10;
    /** Blocks added around the hitbox for render culling: the arms swing, the shell and the volcano's bombs fly wide. */
    private static final double CULL_MARGIN = 4.0D;
    /** Straight up, for smoke (it rises whichever way a volcano points). */
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    /** How far (blocks) the eruption's lava jet reaches out of a crater, and the core form's constant one. */
    private static final double ERUPT_JET_BLOCKS = 2.5D;
    private static final double CORE_JET_BLOCKS = 1.5D;
    /**
     * Ticks the body's and the volcano's animations take to blend from one into the next. GeckoLib starts the new
     * animation only after the blend, so both controllers use the same number and stay in step (the volcano's blast
     * is kept upright against the body's lean frame by frame), and {@link #animationSeconds} allows for it.
     */
    static final int ANIMATION_BLEND_TICKS = 6;
    /**
     * How far a mob walking on ordinary ground moves in a tick for each unit of (speed attribute) squared: Minecraft
     * adds speed x speed to a walking mob's motion every tick and ground friction keeps 0.546 of it (0.6 x 0.91), so
     * it settles at speed x speed / (1 - 0.546).
     */
    private static final double WALK_DISTANCE_FACTOR = 1.0D / (1.0D - 0.6D * 0.91D);
    /**
     * The walk animation never plays faster or slower than this many times its own speed (the Lava Core stage's walk
     * needs about 3.4 to keep its knuckles from sliding, the Shell stage's about 1.9).
     */
    private static final double MAX_WALK_SPEEDUP = 3.6D;
    private static final double MIN_WALK_SPEEDUP = 0.6D;
    /** The Cracking stage's crack layer starts this strong (it grows to full as its health falls to the next stage). */
    private static final float CRACK_START = 0.35F;
    /** How quickly (share per tick) the crack look catches up with its health on the client, so plates ease out. */
    private static final float CRACK_EASE = 0.2F;
    /** How long (ticks) a player stays credited for a kill after their last hit (the vanilla window). */
    private static final int CREDIT_TICKS = 100;
    /** Volume of its big moments (louder than 1 carries further: 16 blocks per 1). */
    private static final float LOUD = 3.0F;
    /** The roar's ring of dust: points round it (more the further it has rolled), and smoke at its feet. */
    private static final int ROAR_RING_POINTS = 12;
    private static final double ROAR_RING_POINTS_PER_BLOCK = 4.0D;
    private static final int ROAR_SMOKE = 16;
    /** Obsidian dust when its statue crumbles. */
    private static final int CRUMBLE_DUST = 160;
    // A projectile's weak hit (ColossusStage.rangedDamageTaken): a stone clink and sparks off the shell, a hiss and a
    // puff of smoke in the lava (the arrow burns up).
    /** The clink: a pitched-up stone sound. */
    private static final float GLANCE_VOLUME = 1.0F;
    private static final float GLANCE_PITCH = 1.6F;
    /** The hiss: the sound fire makes going out. */
    private static final float HISS_VOLUME = 0.6F;
    private static final float HISS_PITCH = 1.5F;
    /** Sparks off the stone (dust specks, size 1 is a normal dust speck) and how far (blocks) they spread. */
    private static final int GLANCE_SPARKS = 6;
    private static final float GLANCE_SPARK_SIZE = 0.8F;
    private static final double GLANCE_SPREAD = 0.15D;
    /** The puff of smoke, the embers in it, and how far (blocks) they spread. */
    private static final int HISS_SMOKE = 6;
    private static final int HISS_EMBERS = 3;
    private static final float HISS_EMBER_SIZE = 0.7F;
    private static final double HISS_SPREAD = 0.12D;
    /** How fast the smoke drifts (blocks a tick). */
    private static final double HISS_SMOKE_SPEED = 0.02D;

    private static final Map<GolemAction, RawAnimation> ANIMATIONS = new EnumMap<>(GolemAction.class);
    private static final RawAnimation VOLCANO_BURST = RawAnimation.begin().thenPlayAndHold("animation.cinder_colossus.volcano_burst");
    private static final RawAnimation VOLCANO_ERUPTING = RawAnimation.begin().thenLoop("animation.cinder_colossus.volcano_erupting");
    private static final RawAnimation VOLCANO_LOB = RawAnimation.begin().thenPlayAndHold("animation.cinder_colossus.volcano_lob");
    private static final RawAnimation WALK = RawAnimation.begin().thenLoop(GolemAction.WALK.animationName());

    static
    {
        for (GolemAction action : GolemAction.values())
        {
            boolean loops = action == GolemAction.IDLE || action == GolemAction.WALK;
            ANIMATIONS.put(action, loops ? RawAnimation.begin().thenLoop(action.animationName())
                    : RawAnimation.begin().thenPlayAndHold(action.animationName()));
        }
        // The charge: the wind-up (head down, pawing the ground), then the gallop until the charge ends.
        ANIMATIONS.put(GolemAction.CHARGE, RawAnimation.begin().thenPlay("animation.cinder_colossus.charge_start")
                .thenLoop("animation.cinder_colossus.charge"));
    }

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    /** Server: tickCount when the current action started. */
    private int actionStartTick;
    /** Client: tickCount when this client saw the current action start. */
    private int clientActionStart;
    /** Client: the action counters the two animation controllers last started an animation for. */
    private int mainSerialSeen = -1;
    private int volcanoSerialSeen = -1;
    /** Client: where the volcano craters were the last time it was drawn, and which way they point, for the particles. */
    private List<Crater> craters = List.of();
    /** Client: the crack look as shown (eased towards {@link #crackTarget}), this tick and the last. */
    private float crackShown;
    private float crackShownO;
    /** Client: walking (with a little hysteresis, so the walk animation's speed doesn't flicker). */
    private boolean walkingShown;
    /** Server: the fight's moves (null for a showcase golem, which never fights). */
    @Nullable
    private ColossusAttackGoal attackGoal;
    /** Server: its boss bar (made when it first fights). */
    @Nullable
    private ServerBossEvent bossBar;
    /** Server: the last player credited with a hit (directly, or through their arrows, pets or risen undead), and when. */
    @Nullable
    private UUID creditPlayer;
    private int creditTick;
    /** Server: true once it has had its first hit (and roared at it): it only roars at a first hit once. Saved. */
    private boolean firstHitRoared;
    /** Server: its roar's ring of dust rolling outwards: ticks since the roar (0: none). */
    private int roarRing;

    public CinderColossus(EntityType<? extends Monster> type, Level level)
    {
        super(type, level);
        this.xpReward = ColossusTuning.XP_REWARD;
        this.moveControl = new ColossusMoveControl(this);
        this.setMaxUpStep(ColossusTuning.STEP_HEIGHT);
        this.setPathfindingMalus(BlockPathTypes.LAVA, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DAMAGE_FIRE, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.DANGER_FIRE, 0.0F);
        this.setPathfindingMalus(BlockPathTypes.WATER, 8.0F);
    }

    /** Its attributes; armour and speed change with the stage ({@link ColossusStage}). */
    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, ColossusTuning.MAX_HEALTH)
                .add(Attributes.ARMOR, ColossusStage.SHELL.armor)
                .add(Attributes.KNOCKBACK_RESISTANCE, ColossusTuning.KNOCKBACK_RESISTANCE)
                .add(Attributes.MOVEMENT_SPEED, ColossusStage.SHELL.speed)
                .add(Attributes.ATTACK_DAMAGE, ColossusTuning.SWIPE_DAMAGE)
                .add(Attributes.FOLLOW_RANGE, ColossusTuning.FOLLOW_RANGE);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_DESIGN, 1);
        this.entityData.define(DATA_PHASE, GolemPhase.DORMANT.ordinal());
        this.entityData.define(DATA_ACTION, GolemAction.IDLE.ordinal());
        this.entityData.define(DATA_ACTION_SERIAL, 0);
        this.entityData.define(DATA_SHOWCASE, false);
    }

    @Override
    protected void registerGoals()
    {
        this.attackGoal = new ColossusAttackGoal(this);
        this.goalSelector.addGoal(1, this.attackGoal);
        this.goalSelector.addGoal(7, new LookAtPlayerGoal(this, Player.class, 16.0F));
        this.goalSelector.addGoal(8, new RandomLookAroundGoal(this));
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this, CinderColossus.class, GolemMinion.class));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    /** A fighting Colossus (spawn egg, summon command): it never despawns and starts in its Shell stage. */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag)
    {
        this.setPersistenceRequired();
        this.applyStage(ColossusStage.SHELL);
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    // ---------------------------------------------------------------- state

    public GolemDesigns.Design getDesign()
    {
        return GolemDesigns.byNumber(this.entityData.get(DATA_DESIGN));
    }

    public void setDesign(int number)
    {
        this.entityData.set(DATA_DESIGN, GolemDesigns.byNumber(number).number());
    }

    public GolemPhase getPhase()
    {
        int i = this.entityData.get(DATA_PHASE);
        GolemPhase[] all = GolemPhase.values();
        return i >= 0 && i < all.length ? all[i] : GolemPhase.DORMANT;
    }

    public void setPhase(GolemPhase phase)
    {
        this.entityData.set(DATA_PHASE, phase.ordinal());
    }

    /** The fight's stage (it follows the look: dormant is the Shell, erupted is Cracking, core is the Lava Core). */
    public ColossusStage stage()
    {
        return ColossusStage.of(this.getPhase());
    }

    public GolemAction getAction()
    {
        int i = this.entityData.get(DATA_ACTION);
        GolemAction[] all = GolemAction.values();
        return i >= 0 && i < all.length ? all[i] : GolemAction.IDLE;
    }

    /** Starts an action (and its animation) from the beginning, even if it's the one already playing. */
    public void startAction(GolemAction action)
    {
        this.entityData.set(DATA_ACTION, action.ordinal());
        this.entityData.set(DATA_ACTION_SERIAL, this.entityData.get(DATA_ACTION_SERIAL) + 1);
        this.actionStartTick = this.tickCount;
    }

    /** Server: ticks since the current action started (its animation starts {@link #ANIMATION_BLEND_TICKS} later). */
    int ticksInAction()
    {
        return this.tickCount - this.actionStartTick;
    }

    /** True if this golem only shows off a design (spawned by {@link #spawnShowcase}). Works on both sides. */
    public boolean isShowcase()
    {
        return this.entityData.get(DATA_SHOWCASE);
    }

    /** True while it plays a stage change (the eruption, the shell bursting): it can't be hurt then. */
    public boolean isTransforming()
    {
        GolemAction action = this.getAction();
        return action == GolemAction.ERUPT || action == GolemAction.BREAK_SHELL;
    }

    // ---------------------------------------------------------------- showcase controls

    /** Back to its dormant shell, standing (also brings a statue back). */
    public void showDormant()
    {
        this.setPhase(GolemPhase.DORMANT);
        this.startAction(GolemAction.IDLE);
    }

    /** Dormant shell first, then the volcano erupts and lava pours over it; it ends erupted. */
    public void playErupt()
    {
        this.setPhase(GolemPhase.DORMANT);
        this.startAction(GolemAction.ERUPT);
    }

    /** Straight to the erupted shell (lava already poured over it), standing. */
    public void showErupted()
    {
        this.setPhase(GolemPhase.ERUPTED);
        this.startAction(GolemAction.IDLE);
    }

    /** The erupted shell, then it breaks off; it ends in the lava core form. */
    public void playBreakShell()
    {
        this.setPhase(GolemPhase.ERUPTED);
        this.startAction(GolemAction.BREAK_SHELL);
    }

    /** Straight to the lava core form, standing, its volcano erupting. */
    public void showCore()
    {
        this.setPhase(GolemPhase.CORE);
        this.startAction(GolemAction.IDLE);
    }

    /** Plays the death in its current form: it sinks down and cools into an obsidian statue, and stays one. */
    public void playDeath()
    {
        this.startAction(GolemAction.DEATH);
    }

    /** Knuckle-walking on the spot (true) or standing (false). A statue comes back to life to do it. */
    public void setWalking(boolean walking)
    {
        this.startAction(walking ? GolemAction.WALK : GolemAction.IDLE);
    }

    // ---------------------------------------------------------------- the fight: stages and damage

    /** The health its current stage stops at (the next stage's threshold), or 0 in the Lava Core stage. */
    private float healthFloor()
    {
        return switch (this.getPhase())
        {
            case DORMANT -> ColossusTuning.CRACKING_AT * this.getMaxHealth();
            case ERUPTED -> ColossusTuning.LAVA_CORE_AT * this.getMaxHealth();
            case CORE -> 0.0F;
        };
    }

    /** Sets its armour and speed for a stage ({@link ColossusStage}). Also used by {@link ColossusGameTest}. */
    void applyStage(ColossusStage stage)
    {
        AttributeInstance armor = this.getAttribute(Attributes.ARMOR);
        if (armor != null)
        {
            armor.setBaseValue(stage.armor);
        }
        AttributeInstance speed = this.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed != null)
        {
            speed.setBaseValue(stage.speed);
        }
    }

    /**
     * Damage that would take it past its stage's threshold stops there, and it starts the next stage (the eruption,
     * or the shell bursting). Damage that bypasses invulnerability ({@code /kill}, the void) isn't stopped. Ranged
     * damage ({@link #isRanged}) is first cut to its stage's {@link ColossusStage#rangedDamageTaken}, and a pickaxe's
     * blow ({@link #isPickaxeBlow}) in the Shell and Cracking stages multiplied by
     * {@link ColossusTuning#PICKAXE_DAMAGE_TAKEN} (its armour then counts as usual).
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
        if (isRanged(source))
        {
            // Arrows, tridents, fireballs... barely hurt it, less with every stage (melee is never reduced).
            amount *= this.stage().rangedDamageTaken;
            this.rangedHitEffect(source);
        }
        else if (isPickaxeBlow(source) && this.stage() != ColossusStage.LAVA_CORE)
        {
            // A pickaxe cracks its rock shell (the Shell and Cracking stages): double damage, before its armour.
            amount *= ColossusTuning.PICKAXE_DAMAGE_TAKEN;
        }
        super.actuallyHurt(source, amount);
        this.noteCredit(source);
        boolean nextStage = floor > 0.0F && this.getHealth() <= floor;
        if (nextStage)
        {
            this.setHealth(floor);
            this.beginNextStage();
        }
        if (!this.firstHitRoared)
        {
            // Its first hit in the fight: it roars and throws the players near it back. (Not when that hit starts a
            // stage change, which roars a moment later anyway, or kills it.)
            this.firstHitRoared = true;
            if (!nextStage && this.isAlive() && !this.isDeadOrDying())
            {
                this.roar(true);
            }
        }
    }

    /**
     * True for ranged damage, which {@link ColossusStage#rangedDamageTaken} reduces: anything tagged
     * {@code minecraft:is_projectile} (arrows, risen skeletons' too, tridents, fireballs, snowballs...), unless it
     * bypasses invulnerability ({@code /kill}).
     */
    static boolean isRanged(DamageSource source)
    {
        return source.is(DamageTypeTags.IS_PROJECTILE) && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
    }

    /**
     * True for a melee blow (a player's or a mob's own hit, not a projectile, thorns or an explosion they caused) from
     * someone holding a pickaxe in their main hand: anything in the {@code minecraft:pickaxes} item tag, so other mods'
     * pickaxes count too. {@link ColossusTuning#PICKAXE_DAMAGE_TAKEN} multiplies it while the shell is on.
     */
    static boolean isPickaxeBlow(DamageSource source)
    {
        if (isRanged(source) || source.is(DamageTypeTags.BYPASSES_INVULNERABILITY)
                || !(source.is(DamageTypes.PLAYER_ATTACK) || source.is(DamageTypes.MOB_ATTACK)
                || source.is(DamageTypes.MOB_ATTACK_NO_AGGRO)))
        {
            return false;
        }
        return source.getEntity() instanceof LivingEntity attacker && source.getDirectEntity() == attacker
                && attacker.getMainHandItem().is(ItemTags.PICKAXES);
    }

    /**
     * Shows that a ranged hit was weak, where the projectile hit: a clink and sparks off the stone (Shell, Cracking),
     * or a hiss and a puff of smoke as it burns up in the lava (Lava Core).
     */
    private void rangedHitEffect(DamageSource source)
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        Vec3 at = this.hitPoint(source.getDirectEntity());
        if (this.stage() == ColossusStage.LAVA_CORE)
        {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, this.getSoundSource(),
                    HISS_VOLUME, HISS_PITCH);
            level.sendParticles(ParticleTypes.SMOKE, at.x, at.y, at.z, HISS_SMOKE,
                    HISS_SPREAD, HISS_SPREAD, HISS_SPREAD, HISS_SMOKE_SPEED);
            level.sendParticles(new DustParticleOptions(DrossColors.vector(DrossColors.COLOSSUS_RANGED_EMBER), HISS_EMBER_SIZE),
                    at.x, at.y, at.z, HISS_EMBERS, HISS_SPREAD, HISS_SPREAD, HISS_SPREAD, 0.0D);
        }
        else
        {
            level.playSound(null, at.x, at.y, at.z, SoundEvents.STONE_BREAK, this.getSoundSource(),
                    GLANCE_VOLUME, GLANCE_PITCH);
            level.sendParticles(new DustParticleOptions(DrossColors.vector(DrossColors.COLOSSUS_RANGED_SPARK), GLANCE_SPARK_SIZE),
                    at.x, at.y, at.z, GLANCE_SPARKS, GLANCE_SPREAD, GLANCE_SPREAD, GLANCE_SPREAD, 0.0D);
        }
    }

    /**
     * Where a projectile meets its body: along the projectile's flight this tick (it's still just outside when the hit
     * is found), else the point of its box nearest the projectile, else the middle of its box.
     */
    private Vec3 hitPoint(@Nullable Entity projectile)
    {
        AABB box = this.getBoundingBox();
        if (projectile == null)
        {
            return box.getCenter();
        }
        Vec3 from = projectile.position();
        Optional<Vec3> along = box.clip(from, from.add(projectile.getDeltaMovement()));
        return along.orElseGet(() -> new Vec3(Mth.clamp(from.x, box.minX, box.maxX),
                Mth.clamp(from.y, box.minY, box.maxY), Mth.clamp(from.z, box.minZ, box.maxZ)));
    }

    /** Its shell erupts (Shell to Cracking) or bursts off (Cracking to Lava Core). It can't be hurt while it plays. */
    private void beginNextStage()
    {
        if (this.attackGoal != null)
        {
            this.attackGoal.interrupt();
        }
        this.getNavigation().stop();
        if (this.getPhase() == GolemPhase.DORMANT)
        {
            this.startAction(GolemAction.ERUPT);
            this.applyStage(ColossusStage.CRACKING);
        }
        else if (this.getPhase() == GolemPhase.ERUPTED)
        {
            this.startAction(GolemAction.BREAK_SHELL);
            this.applyStage(ColossusStage.LAVA_CORE);
        }
        this.playSound(ModSounds.COLOSSUS_RUMBLE.get(), LOUD, 0.9F);
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        boolean bypass = source.is(DamageTypeTags.BYPASSES_INVULNERABILITY);
        // A showcase golem can't be hurt by anything except damage that bypasses invulnerability (/kill, the void).
        if (this.isShowcase() && !bypass)
        {
            return true;
        }
        if (!bypass && (this.isTransforming() || source.is(DamageTypes.IN_WALL) || source.is(DamageTypes.CRAMMING)
                || source.is(DamageTypes.DROWN) || source.is(DamageTypes.FALL) || source.is(DamageTypes.FLY_INTO_WALL)))
        {
            return true;
        }
        return super.isInvulnerableTo(source);
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
     * Everyone who helped defeat it: the player credited with the kill (wherever they are) and every player within its
     * boss bar's range ({@link ColossusTuning#BOSS_BAR_RANGE}; not spectators), each once.
     */
    List<ServerPlayer> victors(DamageSource source)
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return List.of();
        }
        Map<UUID, ServerPlayer> victors = new LinkedHashMap<>();
        UUID killer = creditFor(source);
        if (killer == null && this.creditPlayer != null && this.tickCount - this.creditTick <= CREDIT_TICKS)
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
        double range = ColossusTuning.BOSS_BAR_RANGE;
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
     * Everyone who helped defeat it ({@link #victors}) gets "Set in Stone" and their own trophy
     * ({@link ColossusTuning#DROP_DESIGN}), straight into their inventory; with a full inventory it's dropped a block
     * in front of them, and for a player who's dead (waiting to respawn) where they died. A dropped one is theirs alone
     * and nothing can destroy it ({@link ColossusTrophies#giveTo}).
     */
    private void rewardVictors(DamageSource source)
    {
        for (ServerPlayer player : this.victors(source))
        {
            DrossAdvancements.grant(player, DrossAdvancements.SET_IN_STONE);
            ColossusTrophies.giveTo(player, ColossusTuning.DROP_DESIGN);
        }
    }

    // ---------------------------------------------------------------- the fight: dying into a statue

    /**
     * A fighting Colossus plays its cooling death and stays as a statue (see {@link #tickDeath}); its minions crumble,
     * its boss bar goes, and the players who beat it get the advancement and a trophy each (not when it's removed with
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
        this.startAction(GolemAction.DEATH);
        if (this.bossBar != null)
        {
            this.bossBar.setProgress(0.0F);
            this.bossBar.removeAllPlayers();
        }
        this.crumbleMinions();
        if (!source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            this.rewardVictors(source);
        }
    }

    /**
     * Vanilla removes a dead mob after 20 ticks; a fighting Colossus stays through its cooling death and then as an
     * obsidian statue for {@link ColossusTuning#STATUE_TICKS}, then crumbles away. (A showcase golem killed with
     * {@code /kill} goes the vanilla way, as before.)
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
        int end = this.getDesign().deathTicks() + ANIMATION_BLEND_TICKS + ColossusTuning.STATUE_TICKS;
        if (!this.level().isClientSide && !this.isRemoved() && this.deathTime >= end)
        {
            this.crumbleStatue();
        }
    }

    /** Ticks it has been dead (for the statue), shown as a statue after the cooling. */
    private void crumbleStatue()
    {
        if (this.level() instanceof ServerLevel level)
        {
            AABB box = this.getBoundingBox();
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState()),
                    box.getCenter().x, box.minY + box.getYsize() * 0.4D, box.getCenter().z, CRUMBLE_DUST,
                    box.getXsize() * 0.35D, box.getYsize() * 0.3D, box.getZsize() * 0.35D, 0.1D);
            level.playSound(null, this.getX(), this.getY(), this.getZ(), ModSounds.COLOSSUS_CRUMBLE.get(),
                    this.getSoundSource(), LOUD, 0.8F);
        }
        this.remove(RemovalReason.KILLED);
    }

    /** Its minions crumble when it dies. */
    private void crumbleMinions()
    {
        for (GolemMinion minion : this.minions())
        {
            minion.crumble();
        }
    }

    /** Its minions that are still alive near it. */
    List<GolemMinion> minions()
    {
        AABB area = this.getBoundingBox().inflate(ColossusTuning.MINION_RANGE);
        return this.level().getEntitiesOfClass(GolemMinion.class, area, m -> m.isAlive() && m.belongsTo(this));
    }

    /**
     * The Rage Wand ({@code boss.rage}): when it's raged, its golems join its side. A raged Colossus fights the other
     * raged mobs with its own moves (its fighting AI takes any target), and an idle one starts fighting.
     */
    @Override
    public List<? extends Mob> rageFollowers()
    {
        return this.minions();
    }

    // ---------------------------------------------------------------- the fight: boss bar

    /** Shows its boss bar to the players near it (and hides it from the ones who left); keeps its name and fill up to date. */
    private void updateBossBar()
    {
        if (!(this.level() instanceof ServerLevel level))
        {
            return;
        }
        ColossusStage stage = this.stage();
        Component name = Component.translatable(stage.barName());
        BossEvent.BossBarColor color = stage == ColossusStage.LAVA_CORE ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.PURPLE;
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
        double range = ColossusTuning.BOSS_BAR_RANGE;
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

    // ---------------------------------------------------------------- ticking

    @Override
    public void tick()
    {
        super.tick();
        if (this.level().isClientSide)
        {
            this.crackShownO = this.crackShown;
            this.crackShown += (this.crackTarget() - this.crackShown) * CRACK_EASE;
            this.spawnParticles();
            return;
        }
        int elapsed = this.ticksInAction();
        GolemDesigns.Design design = this.getDesign();
        this.stageSounds(elapsed, design);
        if (this.isFighting() && this.isTransforming())
        {
            this.trackTarget(); // its fighting AI waits while a stage change plays: it keeps glaring at its target
        }
        switch (this.getAction())
        {
            case ERUPT -> {
                if (elapsed >= design.eruptTicks() + SETTLE_TICKS)
                {
                    this.setPhase(GolemPhase.ERUPTED);
                    this.startAction(GolemAction.IDLE);
                    if (this.isFighting())
                    {
                        this.attackGoal.stageChangeSummon(); // the Cracking stage starts with four stone golems
                    }
                }
            }
            case BREAK_SHELL -> {
                if (elapsed >= design.breakTicks() + SETTLE_TICKS)
                {
                    this.setPhase(GolemPhase.CORE);
                    this.startAction(GolemAction.IDLE);
                    if (this.isFighting())
                    {
                        this.attackGoal.stageChangeLob(); // the Lava Core starts with its volcanoes spewing lava
                    }
                }
            }
            case DEATH -> {
                if (elapsed >= design.deathTicks() + ANIMATION_BLEND_TICKS)
                {
                    // Same pose, held: also what it shows when it's loaded again later.
                    this.startAction(GolemAction.STATUE);
                }
            }
            default -> {
            }
        }
        if (this.roarRing > 0 && this.level() instanceof ServerLevel level)
        {
            double r = ColossusTuning.ROAR_RADIUS * this.roarRing / ColossusTuning.ROAR_RING_TICKS;
            ColossusAttackGoal.dustRing(level, this.position(), r, ROAR_RING_POINTS + (int) (r * ROAR_RING_POINTS_PER_BLOCK));
            this.roarRing = this.roarRing >= ColossusTuning.ROAR_RING_TICKS ? 0 : this.roarRing + 1;
        }
        if (!this.isShowcase() && this.isAlive())
        {
            this.updateBossBar();
        }
    }

    /** True for a fighting Colossus that's alive (not a showcase golem): its stage changes bring attacks. */
    private boolean isFighting()
    {
        return !this.isShowcase() && this.isAlive() && this.attackGoal != null;
    }

    /** Turns it towards its target (if it has a live one here) at its stage's turn speed. */
    private void trackTarget()
    {
        LivingEntity target = this.getTarget();
        if (target != null && target.isAlive() && target.level() == this.level())
        {
            this.turnTowards(target.position(), this.stage().turnSpeed);
        }
    }

    /**
     * Its roar's shockwave (the first time it's hurt, and at the roar of each stage change): a ring of dust rolls out
     * and every player within {@link ColossusTuning#ROAR_RADIUS} blocks is thrown back about 15 blocks. Knockback only:
     * no damage, and only players (not in creative or spectator mode): its minions and other mobs stay put. A showcase
     * golem never does it.
     *
     * @param withSound also roar (the stage changes play their own roar)
     */
    void roar(boolean withSound)
    {
        if (!(this.level() instanceof ServerLevel level) || this.isShowcase())
        {
            return;
        }
        if (withSound)
        {
            this.playSound(ModSounds.COLOSSUS_ROAR.get(), LOUD, 0.9F);
        }
        double radius = ColossusTuning.ROAR_RADIUS;
        for (ServerPlayer player : level.players())
        {
            if (player.isCreative() || player.isSpectator() || !player.isAlive() || player.distanceToSqr(this) > radius * radius)
            {
                continue;
            }
            Vec3 to = new Vec3(player.getX() - this.getX(), 0.0D, player.getZ() - this.getZ());
            double d = to.length();
            Vec3 away = d > 1.0E-3D ? to.scale(1.0D / d) : this.forward();
            ColossusAttackGoal.knock(player, away.scale(ColossusTuning.ROAR_KNOCKBACK), ColossusTuning.ROAR_LIFT);
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, this.getX(), this.getY() + 0.5D, this.getZ(), ROAR_SMOKE,
                this.getBbWidth() * 0.5D, 0.3D, this.getBbWidth() * 0.5D, 0.05D);
        this.roarRing = 1;
    }

    /**
     * True for a player's risen undead (raised by Necromancy; the enchant area's own check), not a jockey's mount: the
     * Colossus summons golems against them, and its golems go for them first.
     */
    static boolean isRisenUndead(Entity entity)
    {
        return entity instanceof Mob && entity.isAlive() && RisenUndead.isRisen(entity)
                && !entity.getPersistentData().getBoolean(RisenUndead.MOUNT_KEY);
    }

    /** The risen undead (see {@link #isRisenUndead}) within {@code range} blocks of it. */
    List<Mob> risenNear(double range)
    {
        return this.level().getEntitiesOfClass(Mob.class, this.getBoundingBox().inflate(range),
                mob -> isRisenUndead(mob) && mob.distanceToSqr(this) <= range * range);
    }

    /** Its fighting AI (for {@link ColossusGameTest}, which starts moves itself). Null only before its goals exist. */
    @Nullable
    ColossusAttackGoal attackGoal()
    {
        return this.attackGoal;
    }

    /** The big moments' sounds (a showcase golem is silent, so it makes none of them). */
    private void stageSounds(int elapsed, GolemDesigns.Design design)
    {
        int blast = ANIMATION_BLEND_TICKS + Math.round(GolemDesigns.ERUPT_BLAST * 20.0F);
        switch (this.getAction())
        {
            case ERUPT -> {
                if (elapsed == blast)
                {
                    this.playSound(ModSounds.COLOSSUS_ERUPT.get(), LOUD, 0.9F);
                }
                else if (elapsed == blast + 6)
                {
                    this.playSound(ModSounds.COLOSSUS_ROAR.get(), LOUD, 0.9F);
                    this.roar(false);
                }
            }
            case BREAK_SHELL -> {
                int burst = ANIMATION_BLEND_TICKS + design.burstTicks();
                // Its roar starts as it rears up and opens its jaw (BREAK_ROAR_LEAD before the burst), so it comes out
                // of its open mouth; the shell cracks off and the roar's shockwave rolls out at the burst.
                if (elapsed == Math.max(0, burst - Math.round(ColossusTuning.BREAK_ROAR_LEAD * 20.0F)))
                {
                    this.playSound(ModSounds.COLOSSUS_ROAR.get(), LOUD, 0.8F);
                }
                if (elapsed == burst)
                {
                    this.playSound(ModSounds.COLOSSUS_SHELL_BREAK.get(), LOUD, 0.9F);
                    this.roar(false);
                }
            }
            case DEATH -> {
                if (elapsed == ANIMATION_BLEND_TICKS + Math.round(GolemDesigns.GLOW_FADE_START * 20.0F))
                {
                    this.playSound(ModSounds.COLOSSUS_COOL.get(), 2.0F, 0.6F);
                }
            }
            default -> {
            }
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

    /** Client: seconds since the current action started on this client. */
    public float secondsInAction(float partialTick)
    {
        return (this.tickCount - this.clientActionStart + partialTick) / 20.0F;
    }

    /**
     * Client: seconds into the current action's animations. They start {@link #ANIMATION_BLEND_TICKS} ticks after the
     * action (GeckoLib blends into them first), so the effects timed to them (the blast's particles, the poured lava,
     * the shell bursting, the death's fades and steam) use this rather than {@link #secondsInAction}.
     */
    public float animationSeconds(float partialTick)
    {
        return this.secondsInAction(partialTick) - ANIMATION_BLEND_TICKS / 20.0F;
    }

    // ---------------------------------------------------------------- what the renderer shows

    /**
     * Whether the renderer draws this bone right now: the rock shell ({@code shell_*}) while it has one, the lava
     * extras ({@code molten_*}) in the core form (and as they grow out when the shell bursts), the live eruption
     * ({@code vent_*}) in the core form while alive, the eruption's blast ({@code burst_*}) only while erupting (and
     * the lob's lava spurts), the rock it throws ({@code prop_*}) only while it holds it.
     */
    public boolean showsBone(String bone, float partialTick)
    {
        if (bone.startsWith("shell_"))
        {
            return this.getPhase().hasShell();
        }
        if (bone.startsWith("molten_"))
        {
            return this.getPhase() == GolemPhase.CORE || (this.getAction() == GolemAction.BREAK_SHELL
                    && this.animationSeconds(partialTick) * 20.0F >= this.getDesign().burstTicks());
        }
        if (bone.startsWith("vent_"))
        {
            return this.getPhase() == GolemPhase.CORE && !this.getAction().isDead();
        }
        if (bone.startsWith("burst_"))
        {
            GolemAction action = this.getAction();
            return action == GolemAction.ERUPT
                    || (action == GolemAction.LOB && this.getPhase().hasShell() && bone.endsWith("_column"));
        }
        if (bone.startsWith("prop_"))
        {
            if (this.getAction() != GolemAction.THROW)
            {
                return false;
            }
            GolemDesigns.Moves moves = this.getDesign().moves();
            float t = this.animationSeconds(partialTick) * 20.0F;
            boolean held = t >= moves.throwGrab() && t < moves.throwRelease();
            boolean molten = this.getPhase() == GolemPhase.CORE;
            return held && (bone.equals("prop_magma") == molten);
        }
        return true;
    }

    /** How much of the poured lava shows on the shell: 0 dormant, rising while it erupts, 1 erupted. */
    public float flowProgress(float partialTick)
    {
        return switch (this.getPhase())
        {
            case DORMANT -> this.getAction() == GolemAction.ERUPT
                    ? clamp01((this.animationSeconds(partialTick) - GolemDesigns.FLOW_START) / (GolemDesigns.FLOW_END - GolemDesigns.FLOW_START))
                    : 0.0F;
            case ERUPTED -> 1.0F;
            case CORE -> 0.0F;
        };
    }

    /**
     * How strongly the lava glows: dim while dormant (its insides have cooled), brightening to full as the lava pours
     * over it in the eruption; full when erupted and as lava; fading to 0 while it dies, 0 as a statue.
     */
    public float glowStrength(float partialTick)
    {
        float awake = this.getPhase() != GolemPhase.DORMANT ? 1.0F
                : GolemDesigns.DORMANT_GLOW + (1.0F - GolemDesigns.DORMANT_GLOW) * this.flowProgress(partialTick);
        return switch (this.getAction())
        {
            case DEATH -> awake * (1.0F - smoothStep(GolemDesigns.GLOW_FADE_START, GolemDesigns.GLOW_FADE_END,
                    this.animationSeconds(partialTick)));
            case STATUE -> 0.0F;
            default -> awake;
        };
    }

    /** How much of the obsidian statue texture shows: 0 normally, rising to 1 while it dies, 1 as a statue. */
    public float statueStrength(float partialTick)
    {
        return switch (this.getAction())
        {
            case DEATH -> smoothStep(GolemDesigns.STATUE_FADE_START, GolemDesigns.STATUE_FADE_END, this.animationSeconds(partialTick));
            case STATUE -> 1.0F;
            default -> 0.0F;
        };
    }

    /**
     * Client: how cracked its shell looks (0 to 1) in the fight's Cracking stage: plates knocked loose and bright
     * cracks ({@code GolemCrackLayer}). It starts at {@link #CRACK_START} when the volcanoes erupt (growing in with the
     * poured lava) and grows to 1 as its health falls towards the Lava Core stage. Never for a showcase golem (its
     * erupted look stays as it always was).
     */
    public float crackAmount(float partialTick)
    {
        return Mth.lerp(partialTick, this.crackShownO, this.crackShown);
    }

    /** The crack look its health and stage call for right now (see {@link #crackAmount}). */
    private float crackTarget()
    {
        if (this.isShowcase())
        {
            return 0.0F;
        }
        GolemPhase phase = this.getPhase();
        if (phase == GolemPhase.DORMANT && this.getAction() == GolemAction.ERUPT)
        {
            return CRACK_START * this.flowProgress(0.0F);
        }
        if (phase != GolemPhase.ERUPTED)
        {
            return 0.0F;
        }
        float health = this.getHealth() / this.getMaxHealth();
        float worn = clamp01((ColossusTuning.CRACKING_AT - health) / (ColossusTuning.CRACKING_AT - ColossusTuning.LAVA_CORE_AT));
        return CRACK_START + (1.0F - CRACK_START) * worn;
    }

    private static float clamp01(float x)
    {
        return Mth.clamp(x, 0.0F, 1.0F);
    }

    private static float smoothStep(float from, float to, float x)
    {
        float t = clamp01((x - from) / (to - from));
        return t * t * (3.0F - 2.0F * t);
    }

    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(CULL_MARGIN, 2.0D, CULL_MARGIN);
    }

    // ---------------------------------------------------------------- the fight: movement

    /** How far (blocks) a mob with this speed attribute walks in a tick on ordinary ground. */
    public static double blocksPerTick(double speedAttribute)
    {
        return speedAttribute * speedAttribute * WALK_DISTANCE_FACTOR;
    }

    /**
     * A point on its model (in the model's units: x its left, y up, z its back) in the world, as it stands now. The
     * fight's moves use the points GolemDesigns gives (where a fist lands, where the rock leaves its hand...).
     */
    Vec3 modelPoint(GolemDesigns.Point p)
    {
        double gx = -p.x() / 16.0D;
        double gy = p.y() / 16.0D;
        double gz = p.z() / 16.0D;
        double a = Math.toRadians(180.0D - this.yBodyRot);
        double wx = gx * Math.cos(a) + gz * Math.sin(a);
        double wz = -gx * Math.sin(a) + gz * Math.cos(a);
        return this.position().add(wx, gy, wz);
    }

    /** Turns it (body, head and all) towards a spot, at most {@code maxDegrees} this tick. */
    void turnTowards(Vec3 spot, float maxDegrees)
    {
        double dx = spot.x - this.getX();
        double dz = spot.z - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4D)
        {
            return;
        }
        float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float yaw = Mth.approachDegrees(this.getYRot(), wanted, maxDegrees);
        this.setYRot(yaw);
        this.setYBodyRot(yaw);
        this.setYHeadRot(yaw);
        this.yRotO = this.getYRot();
    }

    /** The way it faces, flat (a unit vector). */
    Vec3 forward()
    {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    /** Its left side, flat (a unit vector). */
    Vec3 left()
    {
        float yaw = this.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(Mth.cos(yaw), 0.0D, Mth.sin(yaw));
    }

    /**
     * Something its attacks may hit: alive, not itself, not a spectator; not another Colossus or a golem, unless the
     * Rage Wand has made that one its enemy ({@link Rage#areEnemies}: raged, on another side).
     */
    boolean canHit(Entity entity)
    {
        if (entity == this || !entity.isAlive() || entity.isSpectator() || !(entity instanceof LivingEntity))
        {
            return false;
        }
        return !(entity instanceof CinderColossus) && !(entity instanceof GolemMinion) || Rage.areEnemies(this, entity);
    }

    /** In the Lava Core stage every hit sets you on fire. */
    void afterHit(Entity victim)
    {
        if (this.stage() == ColossusStage.LAVA_CORE)
        {
            victim.setSecondsOnFire(ColossusTuning.CORE_FIRE_SECONDS);
        }
    }

    /** A damage source for its blows (scaled by the difficulty for players, like every mob's). */
    DamageSource blow()
    {
        return this.damageSources().mobAttack(this);
    }

    /** Plays one of its sounds where it stands (louder carries further). */
    void sound(SoundEvent sound, float volume, float pitch)
    {
        this.playSound(sound, volume, pitch);
    }

    @Override
    public int getMaxHeadYRot()
    {
        return 10; // its head can't turn on its own: the whole body turns
    }

    @Override
    public int getHeadRotSpeed()
    {
        return Math.round(this.stage().turnSpeed);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, DamageSource source)
    {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater()
    {
        return true;
    }

    @Override
    public boolean canBeLeashed(Player player)
    {
        return false;
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound()
    {
        return this.getPhase() == GolemPhase.CORE ? ModSounds.COLOSSUS_AMBIENT_CORE.get() : ModSounds.COLOSSUS_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return 160;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.COLOSSUS_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.COLOSSUS_DEATH.get();
    }

    @Override
    protected float getSoundVolume()
    {
        return 2.0F;
    }

    @Override
    public float getVoicePitch()
    {
        return 0.9F + (this.random.nextFloat() - 0.5F) * 0.1F;
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        if (this.getAction() != GolemAction.CHARGE)
        {
            this.playSound(ModSounds.COLOSSUS_STEP.get(), 1.2F, 0.9F);
        }
    }

    // ---------------------------------------------------------------- particles (client)

    /**
     * Client: a volcano's crater as it was last drawn.
     *
     * @param at  the middle of the crater's opening (world space)
     * @param aim the way the volcano points out of it (a unit vector; it leans with the golem's back)
     */
    public record Crater(Vec3 at, Vec3 aim)
    {
    }

    /** Client: the renderer reports where the craters were drawn and which way they point. */
    public void setCraters(List<Crater> craters)
    {
        this.craters = craters;
    }

    /** The craters, or a guess above its back if it hasn't been drawn yet. */
    private List<Crater> craters()
    {
        if (!this.craters.isEmpty())
        {
            return this.craters;
        }
        List<Crater> guess = new ArrayList<>();
        guess.add(new Crater(this.position().add(0.0D, this.getBbHeight() * 1.3D, 0.0D), UP));
        return guess;
    }

    /**
     * Smoke from a dormant volcano; a blast of lava when it erupts; smoke and the odd spark once erupted; a constant
     * eruption in the core form; a burst when the shell breaks; steam while it cools. In the fight: lava spurting from
     * the craters when it lobs, and dust thrown up by its gallop.
     */
    private void spawnParticles()
    {
        RandomSource random = this.getRandom();
        GolemAction action = this.getAction();
        GolemPhase phase = this.getPhase();
        float t = this.animationSeconds(0.0F);
        float blast = GolemDesigns.ERUPT_BLAST;
        float lob = this.getDesign().moves().lobRelease() / 20.0F;
        for (Crater c : this.craters())
        {
            Vec3 crater = c.at();
            Vec3 aim = c.aim();
            // Smoke leaves along the jet and rises.
            Vec3 smoke = aim.add(UP).normalize();
            if (action == GolemAction.ERUPT)
            {
                if (t >= blast && t < blast + 0.05F)
                {
                    Vec3 bang = crater.add(aim.scale(0.6D));
                    this.level().addParticle(ParticleTypes.EXPLOSION, bang.x, bang.y, bang.z, 0.0D, 0.0D, 0.0D);
                }
                if (t >= blast && t < blast + 0.7F)
                {
                    this.jet(crater, aim, ParticleTypes.LAVA, 6, ERUPT_JET_BLOCKS);
                    this.burst(crater, smoke, ParticleTypes.LARGE_SMOKE, 2, 0.15D);
                    this.burst(crater, aim, ParticleTypes.FLAME, 3, 0.25D);
                }
                else if (t >= blast + 0.7F && this.tickCount % 3 == 0)
                {
                    this.burst(crater, smoke, ParticleTypes.LARGE_SMOKE, 1, 0.08D);
                }
                else if (t < blast && this.tickCount % 6 == 0)
                {
                    this.burst(crater, UP, ParticleTypes.SMOKE, 2, 0.05D);
                }
            }
            else if (action.isDead())
            {
                if (action == GolemAction.DEATH && t > GolemDesigns.GLOW_FADE_START && t < GolemDesigns.STATUE_FADE_END
                        && this.tickCount % 2 == 0)
                {
                    this.steam();
                }
            }
            else if (phase == GolemPhase.CORE)
            {
                this.jet(crater, aim, ParticleTypes.LAVA, random.nextInt(3) == 0 ? 2 : 1, CORE_JET_BLOCKS);
                this.burst(crater, aim, ParticleTypes.FLAME, 1, 0.12D);
                if (this.tickCount % 3 == 0)
                {
                    this.burst(crater, smoke, ParticleTypes.LARGE_SMOKE, 1, 0.1D);
                }
                if (action == GolemAction.LOB && t >= lob && t < lob + 0.3F)
                {
                    this.jet(crater, aim, ParticleTypes.LAVA, 4, ERUPT_JET_BLOCKS);
                }
            }
            else if (action == GolemAction.LOB)
            {
                if (t >= lob && t < lob + 0.35F)
                {
                    this.jet(crater, aim, ParticleTypes.LAVA, 4, ERUPT_JET_BLOCKS);
                    this.burst(crater, aim, ParticleTypes.FLAME, 3, 0.25D);
                    this.burst(crater, smoke, ParticleTypes.LARGE_SMOKE, 2, 0.12D);
                }
                else if (t < lob && this.tickCount % 3 == 0)
                {
                    this.burst(crater, UP, ParticleTypes.LARGE_SMOKE, 1, 0.06D);
                }
            }
            else if (phase == GolemPhase.ERUPTED)
            {
                if (this.tickCount % 5 == 0)
                {
                    this.burst(crater, smoke, ParticleTypes.LARGE_SMOKE, 1, 0.06D);
                }
                if (random.nextInt(8) == 0)
                {
                    this.jet(crater, aim, ParticleTypes.LAVA, 1, 0.5D);
                }
            }
            else if (this.tickCount % 10 == 0)
            {
                // Dormant: a lazy plume of smoke.
                this.burst(crater, UP, ParticleTypes.CAMPFIRE_COSY_SMOKE, 1, 0.02D);
            }
        }
        if (action == GolemAction.BREAK_SHELL)
        {
            float burst = this.getDesign().burstTicks() / 20.0F;
            if (t >= burst && t < burst + 0.05F)
            {
                Vec3 middle = this.position().add(0.0D, this.getBbHeight() * 0.6D, 0.0D);
                this.level().addParticle(ParticleTypes.EXPLOSION_EMITTER, middle.x, middle.y, middle.z, 0.0D, 0.0D, 0.0D);
                this.jet(middle, UP, ParticleTypes.LAVA, 24, 1.0D);
            }
        }
        if (action == GolemAction.CHARGE && t * 20.0F > this.getDesign().moves().chargeStartTicks() && this.onGround())
        {
            // Dust and grit thrown up by its gallop.
            BlockState ground = this.level().getBlockState(this.blockPosition().below());
            if (!ground.isAir())
            {
                AABB box = this.getBoundingBox();
                for (int i = 0; i < 3; i++)
                {
                    this.level().addParticle(new BlockParticleOption(ParticleTypes.BLOCK, ground),
                            Mth.lerp(random.nextDouble(), box.minX, box.maxX), box.minY + 0.1D,
                            Mth.lerp(random.nextDouble(), box.minZ, box.maxZ), 0.0D, 0.15D, 0.0D);
                }
            }
        }
    }

    /** A few particles at a spot, thrown out along a direction (a unit vector) with some spread. */
    private void burst(Vec3 at, Vec3 dir, ParticleOptions particle, int count, double speed)
    {
        RandomSource random = this.getRandom();
        for (int i = 0; i < count; i++)
        {
            double v = speed * (0.6D + random.nextDouble());
            this.level().addParticle(particle,
                    at.x + (random.nextDouble() - 0.5D) * 0.6D, at.y + random.nextDouble() * 0.3D, at.z + (random.nextDouble() - 0.5D) * 0.6D,
                    dir.x * v + (random.nextDouble() - 0.5D) * speed, dir.y * v + (random.nextDouble() - 0.5D) * speed * 0.5D,
                    dir.z * v + (random.nextDouble() - 0.5D) * speed);
        }
    }

    /**
     * Particles scattered along a line out of a spot (up to `length` blocks along a unit vector). For lava pops, which
     * ignore the speed they're given (they always pop upwards), this is what makes a jet of them follow the volcano.
     */
    private void jet(Vec3 at, Vec3 dir, ParticleOptions particle, int count, double length)
    {
        RandomSource random = this.getRandom();
        for (int i = 0; i < count; i++)
        {
            Vec3 p = at.add(dir.scale(random.nextDouble() * length));
            this.level().addParticle(particle, p.x + (random.nextDouble() - 0.5D) * 0.5D, p.y + (random.nextDouble() - 0.5D) * 0.5D,
                    p.z + (random.nextDouble() - 0.5D) * 0.5D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** Steam rising off its body as it cools. */
    private void steam()
    {
        RandomSource random = this.getRandom();
        AABB box = this.getBoundingBox();
        double x = Mth.lerp(random.nextDouble(), box.minX, box.maxX);
        double y = Mth.lerp(random.nextDouble(), box.minY + 0.5D, box.maxY);
        double z = Mth.lerp(random.nextDouble(), box.minZ, box.maxZ);
        this.level().addParticle(ParticleTypes.CLOUD, x, y, z, 0.0D, 0.06D, 0.0D);
    }

    // ---------------------------------------------------------------- animation (GeckoLib)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        AnimationController<CinderColossus> main = new AnimationController<>(this, "main", ANIMATION_BLEND_TICKS, this::animateBody);
        main.setAnimationSpeedHandler(golem -> golem.bodyAnimationSpeed());
        controllers.add(main);
        controllers.add(new AnimationController<>(this, "volcano", ANIMATION_BLEND_TICKS, this::animateVolcano));
    }

    private PlayState animateBody(AnimationState<CinderColossus> state)
    {
        int serial = this.entityData.get(DATA_ACTION_SERIAL);
        if (serial != this.mainSerialSeen)
        {
            // A new action (or the same one again): start its animation from the beginning.
            this.mainSerialSeen = serial;
            state.getController().forceAnimationReset();
        }
        GolemAction action = this.getAction();
        if (action == GolemAction.IDLE && this.isWalkingShown())
        {
            return state.setAndContinue(WALK);
        }
        return state.setAndContinue(ANIMATIONS.get(action));
    }

    /** Client: true while it walks about (a fighting golem moving between its moves), with a little hysteresis. */
    private boolean isWalkingShown()
    {
        float speed = this.walkAnimation.speed();
        if (this.walkingShown ? speed < 0.02F : speed > 0.06F)
        {
            this.walkingShown = !this.walkingShown;
        }
        return this.walkingShown && !this.isShowcase();
    }

    /**
     * How fast the body's animation plays: its walk is sped up to match how fast it really moves in its stage (so its
     * knuckles don't slide); everything else plays as made. The speed only changes with the stage (a steady speed,
     * because changing it mid-loop would make the walk jump).
     */
    private double bodyAnimationSpeed()
    {
        if (this.getAction() != GolemAction.IDLE || !this.walkingShown)
        {
            return 1.0D;
        }
        GolemDesigns.Design design = this.getDesign();
        double natural = design.walkBlocks() / design.walkTicks();
        return Mth.clamp(blocksPerTick(this.stage().speed) / natural, MIN_WALK_SPEEDUP, MAX_WALK_SPEEDUP);
    }

    /** The volcano's own bones: the blast while it erupts, the spurts of a lob, the live eruption in the core form. */
    private PlayState animateVolcano(AnimationState<CinderColossus> state)
    {
        int serial = this.entityData.get(DATA_ACTION_SERIAL);
        if (serial != this.volcanoSerialSeen)
        {
            this.volcanoSerialSeen = serial;
            state.getController().forceAnimationReset();
        }
        GolemAction action = this.getAction();
        if (action == GolemAction.ERUPT)
        {
            return state.setAndContinue(VOLCANO_BURST);
        }
        if (this.getPhase() == GolemPhase.CORE && !action.isDead())
        {
            return state.setAndContinue(VOLCANO_ERUPTING);
        }
        if (action == GolemAction.LOB)
        {
            return state.setAndContinue(VOLCANO_LOB);
        }
        return PlayState.STOP;
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }

    // ---------------------------------------------------------------- showcase golems

    /**
     * Spawns a frozen showcase golem of a design, standing in its dormant shell.
     *
     * @param feet where its feet go (the bottom middle of the air block above the ground)
     * @param yaw  the way it faces, in degrees (0 = south, 90 = west, 180 = north, -90 = east)
     * @return the golem, already in the world, or null if it couldn't be created
     */
    @Nullable
    public static CinderColossus spawnShowcase(ServerLevel level, Vec3 feet, float yaw, GolemDesigns.Design design)
    {
        CinderColossus golem = ModEntities.CINDER_COLOSSUS.get().create(level);
        if (golem == null)
        {
            return null;
        }
        golem.entityData.set(DATA_SHOWCASE, true);
        golem.setDesign(design.number());
        golem.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        golem.setYHeadRot(yaw);
        golem.setYBodyRot(yaw);
        golem.yHeadRotO = yaw;
        golem.yBodyRotO = yaw;
        golem.setNoAi(true);
        golem.setSilent(true);
        golem.setPersistenceRequired();
        level.addFreshEntity(golem);
        return golem;
    }

    /**
     * Spawns a fighting Cinder Colossus (the user's pick, 3G Saddleback), in its Shell stage, facing {@code yaw}. Used by
     * {@code /dross boss summon colossus}; the spawn egg makes one the same way.
     *
     * @return the Colossus, already in the world, or null if it couldn't be created
     */
    @Nullable
    public static CinderColossus spawnBoss(ServerLevel level, Vec3 feet, float yaw)
    {
        CinderColossus golem = ModEntities.CINDER_COLOSSUS.get().create(level);
        if (golem == null)
        {
            return null;
        }
        golem.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        golem.setYHeadRot(yaw);
        golem.setYBodyRot(yaw);
        golem.yHeadRotO = yaw;
        golem.yBodyRotO = yaw;
        net.minecraftforge.event.ForgeEventFactory.onFinalizeSpawn(golem, level, level.getCurrentDifficultyAt(golem.blockPosition()),
                MobSpawnType.COMMAND, null, null);
        level.addFreshEntity(golem);
        return golem;
    }

    /**
     * Right-clicking a showcase golem moves it on to its next stage: dormant, it erupts; erupted, it breaks out of
     * its shell; in its lava form (or as a statue) it goes back to dormant. With sneak it dies. Its number, name and
     * what it's doing go on the action bar.
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
        String what;
        if (player.isShiftKeyDown())
        {
            this.playDeath();
            what = "death";
        }
        else if (this.getAction().isDead() || this.getPhase() == GolemPhase.CORE)
        {
            this.showDormant();
            what = "dormant";
        }
        else if (this.getPhase() == GolemPhase.DORMANT)
        {
            this.playErupt();
            what = "erupt";
        }
        else
        {
            this.playBreakShell();
            what = "break";
        }
        GolemDesigns.Design design = this.getDesign();
        player.displayClientMessage(Component.translatable("dross.golem.showcase." + what, design.label(), design.name(),
                design.mood()), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public boolean isPushable()
    {
        return false; // nothing shoves a colossus around (a showcase golem, or a fighting one)
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
        tag.putString(TAG_PHASE, this.getPhase().name());
        tag.putString(TAG_ACTION, this.getAction().name());
        if (this.isShowcase())
        {
            tag.putBoolean(TAG_SHOWCASE, true);
        }
        if (this.firstHitRoared)
        {
            tag.putBoolean(TAG_ROARED, true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.entityData.set(DATA_SHOWCASE, tag.getBoolean(TAG_SHOWCASE));
        this.firstHitRoared = tag.getBoolean(TAG_ROARED);
        if (tag.contains(TAG_DESIGN))
        {
            this.setDesign(tag.getInt(TAG_DESIGN));
        }
        GolemPhase phase = GolemPhase.byName(tag.getString(TAG_PHASE));
        GolemAction action = GolemAction.byName(tag.getString(TAG_ACTION));
        // Actions that were halfway through when it was saved pick up where they would have ended. A fight move in
        // progress starts again from standing (the fight's AI picks its next move).
        switch (action)
        {
            case ERUPT -> {
                phase = GolemPhase.ERUPTED;
                action = GolemAction.IDLE;
            }
            case BREAK_SHELL -> {
                phase = GolemPhase.CORE;
                action = GolemAction.IDLE;
            }
            case DEATH -> action = GolemAction.STATUE;
            default -> {
                if (action.isAttack())
                {
                    action = GolemAction.IDLE;
                }
            }
        }
        this.setPhase(phase);
        this.startAction(action);
        if (!this.isShowcase() && !this.isDeadOrDying())
        {
            // A rejoin mid-fight: the stage's armour and speed (and its boss bar, made on its next tick) come back.
            this.applyStage(ColossusStage.of(phase));
        }
    }

    // ---------------------------------------------------------------- movement control

    /**
     * Walks like any mob, but turns at its stage's {@link ColossusStage#turnSpeed} (vanilla mobs swing round up to 90
     * degrees a tick, which looks wrong on something this big).
     */
    static class ColossusMoveControl extends MoveControl
    {
        private final CinderColossus golem;

        ColossusMoveControl(CinderColossus golem)
        {
            super(golem);
            this.golem = golem;
        }

        @Override
        public void tick()
        {
            if (this.operation != Operation.MOVE_TO)
            {
                super.tick();
                return;
            }
            this.operation = Operation.WAIT;
            double dx = this.wantedX - this.mob.getX();
            double dz = this.wantedZ - this.mob.getZ();
            double dy = this.wantedY - this.mob.getY();
            if (dx * dx + dy * dy + dz * dz < 2.5000003E-7D)
            {
                this.mob.setZza(0.0F);
                return;
            }
            float wanted = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
            this.mob.setYRot(this.rotlerp(this.mob.getYRot(), wanted, this.golem.stage().turnSpeed));
            this.mob.setSpeed((float) (this.speedModifier * this.mob.getAttributeValue(Attributes.MOVEMENT_SPEED)));
        }
    }
}
