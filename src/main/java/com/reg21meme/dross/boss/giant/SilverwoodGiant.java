package com.reg21meme.dross.boss.giant;

import com.reg21meme.dross.boss.BossChase;
import com.reg21meme.dross.boss.SteadyController;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rimefall.RimefallAnims;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModSounds;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.TimeUtil;
import net.minecraft.util.valueproviders.UniformInt;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.MoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.goal.target.ResetUniversalAngerTargetGoal;
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
import software.bernie.geckolib.core.animation.RawAnimation;
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
import java.util.UUID;

/**
 * The Silverwood Giant ({@code dross:silverwood_giant}; QUEST_PLAN.md, "The Silverwood Giant"): a giant warrior of the
 * Silverwood Forest, the Odyssey's Laestrygonians in the Dross's dress, about 4.5 blocks tall, with no armour. Three
 * looks (G1 the Boulder-Hurler with his rock, G2 the Brawler with his bare hands, G3 the Tree-Bearer with a whole
 * uprooted tree; {@link GiantMoments}), each giant one of them at random, each as likely, unless told
 * ({@code {Look:"brawler"}}). Every number is in {@link GiantTuning}.
 * <ul>
 *   <li><b>Neutral, with patience</b>: walking or running past it does nothing. A survival or adventure player who
 *       stays within {@link GiantTuning#PATIENCE_RANGE} blocks for about 4 seconds is warned (it stares at them, its
 *       eyes flare, it huffs and stamps: the {@code warn} animation); about 8 seconds and it attacks them. Moving off
 *       calms it again. Hit by anything, it fights back. It never starts a fight with another mob.</li>
 *   <li><b>Angry</b>: it runs at its target and smashes (an overhead blow where it lands), sweeps (a backhand knocking
 *       you away to its left) and stomps (throwing whoever's round it into the air), and hurls a boulder
 *       ({@link GiantBoulder}) at the one it's angry with when they're out of reach. Its anger runs out after about 30
 *       seconds, and it gives up a chase {@link GiantTuning#CHASE_LEASH} blocks from where it began and walks back (the
 *       moose's way).</li>
 *   <li>Its blows land at their animations' moments and places ({@link GiantMoments}, made with the animations by
 *       {@code tools/forest_giant/mobs.py}); its walk and run play as fast as it really goes ({@link SteadyController}).
 *       It dies the way Minecraft's mobs do (it tips over, flashing red, and goes in a puff of smoke: the user's
 *       choice, November 18, 2026, "regular Minecraft mobs ... normal Minecraft attributes and death"). It despawns like any monster,
 *       never drops anything.</li>
 * </ul>
 * The showcase ({@code /dross showcase silverwood_giants}) uses frozen ones ({@link #spawnShowcase}).
 */
public class SilverwoodGiant extends Monster implements GeoEntity, NeutralMob
{
    /** Every animation (animation.forest_giant.<name>), in a fixed order: an action is synced as its index. */
    public static final List<String> ANIMATIONS = List.of("idle", "walk", "run", "warn", "smash", "sweep", "stomp", "throw",
            "hurt");
    /** The animations that loop (the others hold their last pose until the next). */
    private static final Set<String> LOOPS = Set.of("idle", "walk", "run");
    private static final Map<String, RawAnimation> RAW = new HashMap<>();
    static
    {
        for (String name : ANIMATIONS)
        {
            String id = "animation.forest_giant." + name;
            RAW.put(name, LOOPS.contains(name) ? RawAnimation.begin().thenLoop(id) : RawAnimation.begin().thenPlayAndHold(id));
        }
    }

    private static final EntityDataAccessor<Integer> DATA_LOOK = SynchedEntityData.defineId(SilverwoodGiant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(SilverwoodGiant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_COUNT = SynchedEntityData.defineId(SilverwoodGiant.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Boolean> DATA_SHOWCASE = SynchedEntityData.defineId(SilverwoodGiant.class, EntityDataSerializers.BOOLEAN);

    /** Its look, saved by id: {@code /summon dross:silverwood_giant ~ ~ ~ {Look:"brawler"}}. */
    public static final String TAG_LOOK = "Look";
    private static final String TAG_SHOWCASE = "Showcase";
    private static final UniformInt ANGER = TimeUtil.rangeOfSeconds(GiantTuning.ANGER_MIN_SECONDS, GiantTuning.ANGER_MAX_SECONDS);

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);

    // ---------------------------------------------------------------- server state

    /** When the action it plays began (its tick count). */
    private int actionStart;
    private int remainingPersistentAngerTime;
    @Nullable
    private UUID persistentAngerTarget;
    /** Where it was when it started chasing its target (it gives up past its leash). */
    @Nullable
    private Vec3 chaseFrom;
    /** How long each survival player has tried its patience (ticks), and whom it has already warned. */
    private final Map<UUID, Integer> patience = new HashMap<>();
    private final Set<UUID> warned = new HashSet<>();
    /** The player it's staring at (warned, still near), else null. */
    @Nullable
    private Player staringAt;
    /** Whom its boulder is meant for (taken when the throw starts). */
    @Nullable
    private LivingEntity throwAt;
    /** Its moves' cooldowns (ticks) and the pause before its next move. */
    private int smashCooldown;
    private int sweepCooldown;
    private int stompCooldown;
    private int throwCooldown;
    private int movePause;
    /** It had a target last tick (it roars when it turns on someone). */
    private boolean hadTarget;

    // ---------------------------------------------------------------- client state

    private int seenActionCount = -1;
    /** How fast it moves, eased (its position comes from the server every few ticks), and whether its run shows. */
    private double shownSpeed;
    private boolean runShown;

    public SilverwoodGiant(EntityType<? extends SilverwoodGiant> type, Level level)
    {
        super(type, level);
        this.xpReward = GiantTuning.XP;
        this.setMaxUpStep(GiantTuning.STEP_HEIGHT);
        this.moveControl = new TurningMoveControl(this, GiantTuning.MAX_TURN);
    }

    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, GiantTuning.HEALTH)
                .add(Attributes.ARMOR, GiantTuning.ARMOR)
                .add(Attributes.ARMOR_TOUGHNESS, GiantTuning.ARMOR_TOUGHNESS)
                .add(Attributes.KNOCKBACK_RESISTANCE, GiantTuning.KNOCKBACK_RESISTANCE)
                .add(Attributes.MOVEMENT_SPEED, GiantTuning.SPEED)
                .add(Attributes.FOLLOW_RANGE, GiantTuning.FOLLOW_RANGE)
                .add(Attributes.ATTACK_DAMAGE, GiantTuning.ATTACK_DAMAGE);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_LOOK, GiantMoments.LOOKS.indexOf(GiantMoments.DEFAULT));
        this.entityData.define(DATA_ACTION, -1);
        this.entityData.define(DATA_ACTION_COUNT, 0);
        this.entityData.define(DATA_SHOWCASE, false);
    }

    // ---------------------------------------------------------------- its look

    /**
     * True if it hurls boulders: only the Boulder-Hurler, who carries one (the user, November 18, 2026: "not all the
     * giants should be able to throw rocks, only the one that spawns in with one ... they have their own attacks"). The
     * Brawler and the Tree-Bearer keep to their smash, sweep and stomp.
     */
    public boolean throwsBoulders()
    {
        return this.look() == GiantMoments.HURLER;
    }

    public GiantMoments.Look look()
    {
        int index = this.entityData.get(DATA_LOOK);
        return index >= 0 && index < GiantMoments.LOOKS.size() ? GiantMoments.LOOKS.get(index) : GiantMoments.DEFAULT;
    }

    public void setLook(GiantMoments.Look look)
    {
        this.entityData.set(DATA_LOOK, Math.max(0, GiantMoments.LOOKS.indexOf(look)));
    }

    public boolean isShowcase()
    {
        return this.entityData.get(DATA_SHOWCASE);
    }

    // ---------------------------------------------------------------- its actions (a move, the warning, its death)

    /** The animation the server started (null: its walk, run or idle, by how it moves). */
    @Nullable
    public String action()
    {
        int index = this.entityData.get(DATA_ACTION);
        return index >= 0 && index < ANIMATIONS.size() ? ANIMATIONS.get(index) : null;
    }

    /** Starts an animation (null: back to walking or standing). Starting the same one again restarts it. */
    public void startAction(@Nullable String name)
    {
        this.entityData.set(DATA_ACTION, name == null ? -1 : ANIMATIONS.indexOf(name));
        this.entityData.set(DATA_ACTION_COUNT, this.entityData.get(DATA_ACTION_COUNT) + 1);
        this.actionStart = this.tickCount;
    }

    /** Ticks of its action's own clock (GeckoLib starts it after its blend: negative until then). */
    public int animationTick()
    {
        return this.tickCount - this.actionStart - GiantTuning.BLEND_TICKS;
    }

    /** True while it makes a move, warns or flinches (not walking, running or standing). */
    public boolean inMove()
    {
        String action = this.action();
        return action != null && !LOOPS.contains(action);
    }

    /** The showcase's controls: plays one of its animations ("idle": back to standing). */
    public void playShowcase(String animation)
    {
        this.startAction("idle".equals(animation) ? null : animation);
    }

    // ---------------------------------------------------------------- goals

    @Override
    protected void registerGoals()
    {
        this.goalSelector.addGoal(0, new FloatGoal(this));
        this.goalSelector.addGoal(1, new FightGoal());
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, GiantTuning.WANDER_SPEED)
        {
            @Override
            public boolean canUse()
            {
                return SilverwoodGiant.this.staringAt == null && !SilverwoodGiant.this.inMove() && super.canUse();
            }
        });
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 12.0F));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));
        // Hurt by anything, it fights back; it never picks a fight with another mob itself.
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, 10, true, false, this::isAngryAt));
        this.targetSelector.addGoal(3, new ResetUniversalAngerTargetGoal<>(this, false));
    }

    // ---------------------------------------------------------------- every tick

    @Override
    public void tick()
    {
        super.tick();
        if (!this.level().isClientSide)
        {
            this.serverTick();
        }
    }

    @Override
    public void aiStep()
    {
        super.aiStep();
        if (this.level().isClientSide)
        {
            double speed = RimefallAnims.groundSpeed(this);
            this.shownSpeed += (speed - this.shownSpeed) * 0.35D;
            if (this.shownSpeed > GiantTuning.RUN_ABOVE + 0.3D)
            {
                this.runShown = true;
            }
            else if (this.shownSpeed < GiantTuning.RUN_ABOVE - 0.3D)
            {
                this.runShown = false;
            }
            return;
        }
        if (this.isAlive() && !this.isShowcase())
        {
            // Its anger runs out even while it chases you (false: vanilla's neutral mobs stay angry as long as they keep
            // a player as their target), and it never follows you far. When it runs out it gives up for good: the target
            // goal that picked its target remembers it and would set it again the very next tick.
            boolean wasAngry = this.isAngry();
            this.updatePersistentAnger((ServerLevel) this.level(), false);
            if (wasAngry && !this.isAngry())
            {
                this.giveUp();
            }
            this.keepToLeash();
        }
    }

    private void serverTick()
    {
        String action = this.action();
        if (action != null)
        {
            int t = this.animationTick();
            this.cues(action, t);
            GiantMoments.Anim anim = this.look().anim(action);
            if (anim != null && !anim.loop() && !"death".equals(action) && t >= anim.ticks())
            {
                this.startAction(null);
                this.movePause = GiantTuning.MOVE_PAUSE;
            }
        }
        if (this.isShowcase() || !this.isAlive())
        {
            return;
        }
        this.smashCooldown = Math.max(0, this.smashCooldown - 1);
        this.sweepCooldown = Math.max(0, this.sweepCooldown - 1);
        this.stompCooldown = Math.max(0, this.stompCooldown - 1);
        this.throwCooldown = Math.max(0, this.throwCooldown - 1);
        this.movePause = Math.max(0, this.movePause - 1);
        LivingEntity target = this.getTarget();
        if (target != null && !this.hadTarget)
        {
            // it turns on someone: a roar (the patience's end, or a blow)
            this.sound(ModSounds.SILVERWOOD_GIANT_ROAR.get(), GiantTuning.ROAR_VOLUME);
            this.patience.clear();
            this.warned.clear();
            this.staringAt = null;
        }
        this.hadTarget = target != null;
        if (target == null && this.tickCount % GiantTuning.PATIENCE_CHECK == 0)
        {
            this.tryPatience();
        }
        if (this.staringAt != null && target == null)
        {
            // it stares at the one it warned, turning to face them, and stays put
            this.getLookControl().setLookAt(this.staringAt, 30.0F, 30.0F);
            this.turnTowards(this.staringAt.position(), GiantTuning.MAX_TURN);
            this.getNavigation().stop();
        }
    }

    // ---------------------------------------------------------------- patience

    /** True if this player tries its patience by standing near: alive, in survival or adventure, within range. */
    private boolean triesPatience(Player player)
    {
        return player.isAlive() && !player.isSpectator() && !player.isCreative()
                && player.position().subtract(this.position()).horizontalDistance() <= GiantTuning.PATIENCE_RANGE
                && Math.abs(player.getY() - this.getY()) <= this.getBbHeight();
    }

    /**
     * Counts how long each survival player has stood near (each look counts {@link GiantTuning#PATIENCE_CHECK} ticks),
     * draining the count of those who moved off; warns the one who has tried it longest at
     * {@link GiantTuning#WARN_TICKS} and attacks them at {@link GiantTuning#ANGER_TICKS}.
     */
    private void tryPatience()
    {
        double r = GiantTuning.PATIENCE_RANGE + 1.0D;
        List<Player> near = this.level().getEntitiesOfClass(Player.class, this.getBoundingBox().inflate(r, 3.0D, r),
                this::triesPatience);
        Set<UUID> present = new HashSet<>();
        for (Player player : near)
        {
            present.add(player.getUUID());
            this.patience.merge(player.getUUID(), GiantTuning.PATIENCE_CHECK, Integer::sum);
        }
        for (Iterator<Map.Entry<UUID, Integer>> it = this.patience.entrySet().iterator(); it.hasNext(); )
        {
            Map.Entry<UUID, Integer> e = it.next();
            if (!present.contains(e.getKey()))
            {
                e.setValue(e.getValue() - GiantTuning.PATIENCE_DRAIN * GiantTuning.PATIENCE_CHECK);
            }
            if (e.getValue() < GiantTuning.WARN_AGAIN_BELOW)
            {
                this.warned.remove(e.getKey());
            }
            if (e.getValue() <= 0)
            {
                it.remove();
            }
        }
        Player worst = null;
        int most = 0;
        for (Player player : near)
        {
            int count = this.patience.getOrDefault(player.getUUID(), 0);
            if (count > most)
            {
                most = count;
                worst = player;
            }
        }
        this.staringAt = worst != null && most >= GiantTuning.WARN_TICKS ? worst : null;
        if (worst == null)
        {
            return;
        }
        if (most >= GiantTuning.ANGER_TICKS)
        {
            this.setTarget(worst);
            return;
        }
        if (most >= GiantTuning.WARN_TICKS && this.warned.add(worst.getUUID()) && !this.inMove())
        {
            this.getNavigation().stop();
            this.startAction("warn");
        }
    }

    /** How long a player has tried its patience (ticks), for the tests. */
    public int patienceOf(Player player)
    {
        return this.patience.getOrDefault(player.getUUID(), 0);
    }

    // ---------------------------------------------------------------- its leash (like the moose's)

    /** Gives up a chase {@link GiantTuning#CHASE_LEASH} blocks from where it began and walks back there (not while
     *  raged: the Rage Wand's fights are its own). */
    private void keepToLeash()
    {
        LivingEntity target = this.getTarget();
        if (target == null || Rage.isRaged(this))
        {
            this.chaseFrom = null;
            return;
        }
        if (this.chaseFrom == null)
        {
            this.chaseFrom = this.position();
            return;
        }
        if (this.position().subtract(this.chaseFrom).horizontalDistance() > GiantTuning.CHASE_LEASH)
        {
            Vec3 back = this.chaseFrom;
            this.giveUp();
            this.getNavigation().moveTo(back.x, back.y, back.z, GiantTuning.RETURN_SPEED);
        }
    }

    /** Stops being angry and drops its target, and the target goals that would pick it again the very next tick. */
    public void giveUp()
    {
        this.stopBeingAngry();
        this.targetSelector.getRunningGoals().toList().forEach(WrappedGoal::stop);
        this.setTarget(null);
        this.chaseFrom = null;
        this.throwAt = null;
        this.hadTarget = false;
    }

    /** For the tests: where its chase began (as if it had already chased its target from there). */
    public void setChaseFromForTest(Vec3 from)
    {
        this.chaseFrom = from;
    }

    // ---------------------------------------------------------------- the moments of its animations

    /** What happens at tick {@code t} of an action: its sounds always (the showcase's too), its blows only for real. */
    private void cues(String action, int t)
    {
        GiantMoments.Look look = this.look();
        boolean real = !this.isShowcase() && this.isAlive();
        switch (action)
        {
            case "warn" -> {
                if (t == look.warnFlare())
                {
                    this.sound(ModSounds.SILVERWOOD_GIANT_WARN.get(), 1.5F);
                }
                if (t == look.warnStamp())
                {
                    this.sound(ModSounds.SILVERWOOD_GIANT_STAMP.get(), GiantTuning.BLOW_VOLUME);
                    this.dust(this.toWorld(look.stompPoint()), 12, 0.6D);
                }
            }
            case "smash" -> {
                if (t == look.smashHit())
                {
                    Vec3 at = this.toWorld(look.smashPoint());
                    this.sound(ModSounds.SILVERWOOD_GIANT_SMASH.get(), GiantTuning.BLOW_VOLUME);
                    this.dust(new Vec3(at.x, this.getY(), at.z), 24, 1.0D);
                    if (real)
                    {
                        this.smashLands(at);
                    }
                }
            }
            case "sweep" -> {
                if (t == look.sweepHit() - GiantTuning.SWEEP_SOUND_LEAD)
                {
                    this.sound(ModSounds.SILVERWOOD_GIANT_SWEEP.get(), GiantTuning.BLOW_VOLUME);
                }
                if (t == look.sweepHit() && real)
                {
                    this.sweepLands();
                }
            }
            case "stomp" -> {
                if (t == look.stompHit())
                {
                    Vec3 at = this.toWorld(look.stompPoint());
                    this.sound(ModSounds.SILVERWOOD_GIANT_STOMP.get(), GiantTuning.ROAR_VOLUME);
                    this.dust(at, 40, GiantTuning.STOMP_RADIUS * 0.6D);
                    if (real)
                    {
                        this.stompLands(at);
                    }
                }
            }
            case "throw" -> {
                if (t == look.throwRelease() - GiantTuning.THROW_SOUND_LEAD)
                {
                    this.sound(ModSounds.SILVERWOOD_GIANT_THROW.get(), GiantTuning.BLOW_VOLUME);
                }
                if (t == look.throwRelease() && real)
                {
                    this.hurl();
                }
                if (t == look.throwTear())
                {
                    this.sound(ModSounds.SILVERWOOD_GIANT_TEAR.get(), 1.5F);
                }
            }
            case "death" -> {
                if (t == Math.max(0, look.deathRoar() - 3))
                {
                    this.sound(ModSounds.SILVERWOOD_GIANT_DEATH.get(), GiantTuning.ROAR_VOLUME);
                }
                if (t == look.deathKnees())
                {
                    this.sound(ModSounds.SILVERWOOD_GIANT_FALL.get(), 0.8F);
                }
                if (t == look.deathThud())
                {
                    this.sound(ModSounds.SILVERWOOD_GIANT_FALL.get(), GiantTuning.BLOW_VOLUME);
                    this.dust(this.position().add(this.facing().scale(2.0D)), 40, 1.5D);
                }
            }
            default -> {
            }
        }
    }

    /** A point of its own frame ({@link GiantMoments.Point}: its left, up, forward) where it stands now. */
    public Vec3 toWorld(GiantMoments.Point p)
    {
        Vec3 forward = this.facing();
        Vec3 left = new Vec3(forward.z, 0.0D, -forward.x);
        return this.position().add(left.scale(p.left())).add(0.0D, p.up(), 0.0D).add(forward.scale(p.forward()));
    }

    /** The way its body faces, along the ground. */
    public Vec3 facing()
    {
        float yaw = this.yBodyRot * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0D, Mth.cos(yaw));
    }

    /** Its left, along the ground. */
    public Vec3 leftSide()
    {
        Vec3 forward = this.facing();
        return new Vec3(forward.z, 0.0D, -forward.x);
    }

    // ---------------------------------------------------------------- its blows

    /** Whether one of its blows may land on {@code e}: anything alive but itself, another giant, a teammate (the Rage
     *  Wand's), a spectator or a player in creative. */
    public boolean mayHit(LivingEntity e)
    {
        if (e == this || !e.isAlive() || e.isSpectator() || e instanceof Player player && player.isCreative())
        {
            return false;
        }
        if (Rage.areTeammates(this, e))
        {
            return false;
        }
        return !(e instanceof SilverwoodGiant) || Rage.areEnemies(this, e);
    }

    private List<LivingEntity> hittable(AABB box)
    {
        return this.level().getEntitiesOfClass(LivingEntity.class, box, this::mayHit);
    }

    /** The smash lands at {@code at}: everything within {@link GiantTuning#SMASH_RADIUS} of it is hurt and knocked back. */
    private void smashLands(Vec3 at)
    {
        double r = GiantTuning.SMASH_RADIUS;
        for (LivingEntity e : this.hittable(new AABB(at.x - r, at.y - 1.5D, at.z - r, at.x + r, at.y + 2.5D, at.z + r)))
        {
            if (Math.hypot(e.getX() - at.x, e.getZ() - at.z) - e.getBbWidth() / 2.0D <= r
                    && e.hurt(this.damageSources().mobAttack(this), GiantTuning.SMASH_DAMAGE))
            {
                throwFrom(e, this.position(), GiantTuning.SMASH_KNOCKBACK, 0.3D);
            }
        }
    }

    /** The sweep: everything in an arc in front of it, within {@link GiantTuning#SWEEP_REACH}, is hurt and knocked away to
     *  its left. */
    private void sweepLands()
    {
        double r = GiantTuning.SWEEP_REACH;
        Vec3 forward = this.facing();
        Vec3 left = this.leftSide();
        for (LivingEntity e : this.hittable(this.getBoundingBox().inflate(r, 0.5D, r)))
        {
            double dx = e.getX() - this.getX();
            double dz = e.getZ() - this.getZ();
            double d = Math.sqrt(dx * dx + dz * dz);
            boolean inArc = d < this.getBbWidth() * 0.5D || (dx * forward.x + dz * forward.z) / d >= GiantTuning.SWEEP_ARC_COS;
            if (d - e.getBbWidth() / 2.0D <= r && inArc && e.hurt(this.damageSources().mobAttack(this), GiantTuning.SWEEP_DAMAGE))
            {
                push(e, left.scale(GiantTuning.SWEEP_THROW), GiantTuning.SWEEP_LIFT);
            }
        }
    }

    /** The stomp lands at {@code at} (its stamping foot): everything within {@link GiantTuning#STOMP_RADIUS} is hurt and
     *  thrown up (about 3.5 blocks) and a little out. */
    private void stompLands(Vec3 at)
    {
        double r = GiantTuning.STOMP_RADIUS;
        for (LivingEntity e : this.hittable(new AABB(at.x - r, at.y - 1.0D, at.z - r, at.x + r, at.y + 2.0D, at.z + r)))
        {
            if (Math.hypot(e.getX() - at.x, e.getZ() - at.z) - e.getBbWidth() / 2.0D <= r && e.onGround()
                    && e.hurt(this.damageSources().mobAttack(this), GiantTuning.STOMP_DAMAGE))
            {
                throwFrom(e, at, GiantTuning.STOMP_OUT, GiantTuning.STOMP_LIFT);
            }
        }
    }

    /** Hurls its boulder (from where its hand lets go, {@link GiantMoments.Look#releasePoint}) at the one it's angry
     *  with; never at anyone else. */
    private void hurl()
    {
        LivingEntity target = this.throwAt != null && this.throwAt.isAlive() ? this.throwAt : this.getTarget();
        this.throwAt = null;
        if (target == null || !this.isAngryAt(target))
        {
            return;
        }
        Vec3 from = this.toWorld(this.look().releasePoint());
        GiantBoulder boulder = GiantBoulder.thrown(this.level(), this, from);
        Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        int ticks = Mth.clamp((int) Math.round(from.distanceTo(aim) * GiantTuning.BOULDER_TICKS_PER_BLOCK),
                GiantTuning.BOULDER_MIN_TICKS, GiantTuning.BOULDER_MAX_TICKS);
        boulder.aim(aim, ticks);
        this.level().addFreshEntity(boulder);
    }

    /** Throws {@code e} away from {@code from} (along the ground) and up, as much as its knockback resistance lets. */
    static void throwFrom(LivingEntity e, Vec3 from, double out, double up)
    {
        Vec3 away = e.position().subtract(from).multiply(1.0D, 0.0D, 1.0D);
        away = away.lengthSqr() < 1.0E-4D ? new Vec3(1.0D, 0.0D, 0.0D) : away.normalize();
        push(e, away.scale(out), up);
    }

    /** Sets {@code e} moving along {@code along} (blocks a tick, along the ground) and up, its knockback resistance taken
     *  off. */
    static void push(LivingEntity e, Vec3 along, double up)
    {
        double keep = 1.0D - Mth.clamp(e.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE), 0.0D, 1.0D);
        if (keep <= 0.0D)
        {
            return;
        }
        Vec3 v = e.getDeltaMovement();
        e.setDeltaMovement(v.x * 0.5D + along.x * keep, Math.max(v.y, up * keep), v.z * 0.5D + along.z * keep);
        e.hurtMarked = true;
    }

    /** Turns it (body and head) towards a spot, at most {@code maxTurn} degrees this tick. */
    private void turnTowards(Vec3 spot, float maxTurn)
    {
        double dx = spot.x - this.getX();
        double dz = spot.z - this.getZ();
        if (dx * dx + dz * dz < 1.0E-4D)
        {
            return;
        }
        float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F;
        float turned = Mth.approachDegrees(this.getYRot(), yaw, maxTurn);
        this.setYRot(turned);
        this.setYBodyRot(turned);
        this.setYHeadRot(turned);
    }

    /** Dust and clods kicked up round a spot on the ground. */
    private void dust(Vec3 at, int count, double spread)
    {
        if (this.level() instanceof ServerLevel level)
        {
            BlockPos below = BlockPos.containing(at.x, this.getY() - 0.5D, at.z);
            BlockState ground = level.getBlockState(below);
            if (ground.isAir())
            {
                ground = Blocks.DIRT.defaultBlockState();
            }
            level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), at.x, this.getY() + 0.2D, at.z, count,
                    spread, 0.2D, spread, 0.15D);
        }
    }

    /** One of its sounds, from where it stands (heard even from a silent showcase giant). */
    private void sound(SoundEvent sound, float volume)
    {
        this.level().playSound(null, this.getX(), this.getY() + 2.0D, this.getZ(), sound, SoundSource.HOSTILE, volume,
                0.95F + this.random.nextFloat() * 0.1F);
    }

    // ---------------------------------------------------------------- fighting

    /** Its fight: it runs at its target and, between short pauses, smashes, sweeps or stomps when it's close, or hurls
     *  a boulder when it's far (only at the one it's angry with). */
    private class FightGoal extends Goal
    {
        private int repath;

        FightGoal()
        {
            this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        }

        @Override
        public boolean canUse()
        {
            LivingEntity target = SilverwoodGiant.this.getTarget();
            return !SilverwoodGiant.this.isShowcase() && target != null && target.isAlive()
                    && SilverwoodGiant.this.canAttack(target);
        }

        @Override
        public boolean canContinueToUse()
        {
            return this.canUse();
        }

        @Override
        public boolean requiresUpdateEveryTick()
        {
            return true;
        }

        @Override
        public void tick()
        {
            SilverwoodGiant self = SilverwoodGiant.this;
            LivingEntity target = self.getTarget();
            if (target == null)
            {
                return;
            }
            String action = self.action();
            if (self.inMove() && !"hurt".equals(action))
            {
                self.getNavigation().stop();
                if (self.animationTick() < self.blowTick(action) - GiantTuning.COMMIT_TICKS)
                {
                    self.getLookControl().setLookAt(target, 30.0F, 30.0F);
                    self.turnTowards(target.position(), GiantTuning.MAX_TURN);
                }
                return;
            }
            self.getLookControl().setLookAt(target, 30.0F, 30.0F);
            double distance = target.position().subtract(self.position()).horizontalDistance() - target.getBbWidth() / 2.0D;
            boolean seen = self.getSensing().hasLineOfSight(target);
            if (self.movePause == 0 && action == null && seen)
            {
                if (distance <= GiantTuning.MELEE_REACH && Math.abs(target.getY() - self.getY()) < self.getBbHeight())
                {
                    String move = self.pickBlow();
                    if (move != null)
                    {
                        self.getNavigation().stop();
                        self.startAction(move);
                        return;
                    }
                }
                else if (self.throwsBoulders() && distance >= GiantTuning.THROW_MIN && distance <= GiantTuning.THROW_MAX
                        && self.throwCooldown == 0 && self.isAngryAt(target))
                {
                    self.getNavigation().stop();
                    self.throwAt = target;
                    self.throwCooldown = GiantTuning.THROW_COOLDOWN;
                    self.startAction("throw");
                    return;
                }
            }
            if (distance > GiantTuning.MELEE_REACH * 0.6D)
            {
                this.repath = BossChase.chase(self, target, GiantTuning.CHASE_SPEED, this.repath, 10);
            }
            else
            {
                self.getNavigation().stop();
                self.turnTowards(target.position(), GiantTuning.MAX_TURN);
            }
        }
    }

    /** When a move's blow lands (or its boulder leaves its hand): it stops turning just before. */
    private int blowTick(@Nullable String action)
    {
        GiantMoments.Look look = this.look();
        if (action == null)
        {
            return 0;
        }
        return switch (action)
        {
            case "smash" -> look.smashHit();
            case "sweep" -> look.sweepHit();
            case "stomp" -> look.stompHit();
            case "throw" -> look.throwRelease();
            default -> 0;
        };
    }

    /** Smash, sweep or stomp (whichever are ready, at random by their weights; the stomp much likelier with a crowd
     *  round it), or null if none is ready. */
    @Nullable
    private String pickBlow()
    {
        double r = GiantTuning.STOMP_RADIUS;
        int crowd = this.hittable(this.getBoundingBox().inflate(r, 1.0D, r)).size();
        List<String> moves = new ArrayList<>();
        List<Integer> weights = new ArrayList<>();
        if (this.smashCooldown == 0)
        {
            moves.add("smash");
            weights.add(GiantTuning.SMASH_WEIGHT);
        }
        if (this.sweepCooldown == 0)
        {
            moves.add("sweep");
            weights.add(GiantTuning.SWEEP_WEIGHT);
        }
        if (this.stompCooldown == 0)
        {
            moves.add("stomp");
            weights.add(crowd >= GiantTuning.STOMP_CROWD ? GiantTuning.STOMP_CROWD_WEIGHT : GiantTuning.STOMP_WEIGHT);
        }
        int total = weights.stream().mapToInt(Integer::intValue).sum();
        if (total == 0)
        {
            return null;
        }
        int roll = this.random.nextInt(total);
        for (int i = 0; i < moves.size(); i++)
        {
            roll -= weights.get(i);
            if (roll < 0)
            {
                String move = moves.get(i);
                switch (move)
                {
                    case "smash" -> this.smashCooldown = GiantTuning.SMASH_COOLDOWN;
                    case "sweep" -> this.sweepCooldown = GiantTuning.SWEEP_COOLDOWN;
                    default -> this.stompCooldown = GiantTuning.STOMP_COOLDOWN;
                }
                return move;
            }
        }
        return null;
    }

    /** Never another giant (unless the Rage Wand has set them on each other). */
    @Override
    public boolean canAttack(LivingEntity target)
    {
        if (target instanceof SilverwoodGiant && !Rage.areEnemies(this, target))
        {
            return false;
        }
        return super.canAttack(target);
    }

    @Override
    public void setTarget(@Nullable LivingEntity target)
    {
        super.setTarget(this.isShowcase() ? null : target);
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

    // ---------------------------------------------------------------- hurt and death

    @Override
    public boolean hurt(DamageSource source, float amount)
    {
        boolean hurt = super.hurt(source, amount);
        if (hurt && !this.level().isClientSide && this.isAlive() && this.action() == null)
        {
            this.startAction("hurt");
        }
        return hurt;
    }

    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        return this.isShowcase() && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY) || super.isInvulnerableTo(source);
    }

    @Override
    public void die(DamageSource source)
    {
        super.die(source);
        if (!this.level().isClientSide && this.dead)
        {
            this.getNavigation().stop();
        }
    }

    /** Each new giant wears one of its three looks at random, each as likely (a spawn, a spawn egg, {@code /summon}
     *  without a {@code Look}; one saved keeps its own: a saved look is read after this). */
    @Override
    public SpawnGroupData finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty, MobSpawnType reason,
                                        @Nullable SpawnGroupData data, @Nullable CompoundTag tag)
    {
        if (!this.isShowcase())
        {
            this.setLook(GiantMoments.LOOKS.get(this.random.nextInt(GiantMoments.LOOKS.size())));
        }
        return super.finalizeSpawn(level, difficulty, reason, data, tag);
    }

    // ---------------------------------------------------------------- sounds

    @Override
    protected SoundEvent getAmbientSound()
    {
        return ModSounds.SILVERWOOD_GIANT_AMBIENT.get();
    }

    @Override
    public int getAmbientSoundInterval()
    {
        return GiantTuning.AMBIENT_INTERVAL;
    }

    @Override
    protected SoundEvent getHurtSound(DamageSource source)
    {
        return ModSounds.SILVERWOOD_GIANT_HURT.get();
    }

    @Override
    protected SoundEvent getDeathSound()
    {
        return ModSounds.SILVERWOOD_GIANT_DEATH.get();
    }

    @Override
    protected void playStepSound(BlockPos pos, BlockState state)
    {
        this.playSound(ModSounds.SILVERWOOD_GIANT_STEP.get(), GiantTuning.STEP_VOLUME, 0.95F + this.random.nextFloat() * 0.1F);
    }

    /** A footfall every step of its own (vanilla's is every 1.7 blocks): its walk's, or its run's when it runs. */
    @Override
    protected float nextStep()
    {
        boolean running = this.getDeltaMovement().horizontalDistance() * 20.0D > GiantTuning.RUN_ABOVE;
        return this.moveDist + (float) ((running ? this.look().runStep() : this.look().walkStep()) * 0.6D);
    }

    @Override
    protected float getSoundVolume()
    {
        return 1.5F;
    }

    // ---------------------------------------------------------------- its animation (GeckoLib)

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers)
    {
        controllers.add(new SteadyController<>(this, "main", GiantTuning.BLEND_TICKS, this::animate));
    }

    private PlayState animate(AnimationState<SilverwoodGiant> state)
    {
        AnimationController<SilverwoodGiant> controller = state.getController();
        int count = this.entityData.get(DATA_ACTION_COUNT);
        if (count != this.seenActionCount)
        {
            this.seenActionCount = count;
            controller.forceAnimationReset();
        }
        String action = this.action();
        if (action != null)
        {
            controller.setAnimationSpeed(1.0D);
            return state.setAndContinue(RAW.get(action));
        }
        if (!this.isShowcase() && this.shownSpeed > GiantTuning.MOVING)
        {
            double pace = this.runShown ? this.look().runPace() : this.look().walkPace();
            controller.setAnimationSpeed(Mth.clamp(this.shownSpeed / pace, GiantTuning.MIN_SPEEDUP, GiantTuning.MAX_SPEEDUP));
            return state.setAndContinue(RAW.get(this.runShown ? "run" : "walk"));
        }
        controller.setAnimationSpeed(1.0D);
        return state.setAndContinue(RAW.get("idle"));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache()
    {
        return this.animationCache;
    }

    /** Drawn whole: its spear (G2), its boulder thrown overhead, its body lying dead reach past its hitbox. */
    @Override
    public AABB getBoundingBoxForCulling()
    {
        return this.getBoundingBox().inflate(3.0D, 1.5D, 3.0D);
    }

    // ---------------------------------------------------------------- the showcase's giants

    /**
     * Spawns a frozen showcase giant of this look: no AI, silent, can't be pushed or hurt (but by {@code /kill}), never
     * despawns, facing {@code yaw}. It plays the showcase's animations ({@link #playShowcase}).
     *
     * @return it, already in the world, or null if it couldn't be made
     */
    @Nullable
    public static SilverwoodGiant spawnShowcase(ServerLevel level, Vec3 feet, float yaw, GiantMoments.Look look)
    {
        SilverwoodGiant giant = ModEntities.SILVERWOOD_GIANT.get().create(level);
        if (giant == null)
        {
            return null;
        }
        giant.entityData.set(DATA_SHOWCASE, true);
        giant.setLook(look);
        giant.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        giant.setYHeadRot(yaw);
        giant.setYBodyRot(yaw);
        giant.yHeadRotO = yaw;
        giant.yBodyRotO = yaw;
        giant.setNoAi(true);
        giant.setSilent(true);
        giant.setPersistenceRequired();
        level.addFreshEntity(giant);
        return giant;
    }

    /** A showcase giant: right-click plays its next animation. */
    @Override
    protected InteractionResult mobInteract(Player player, InteractionHand hand)
    {
        if (!this.isShowcase() || hand != InteractionHand.MAIN_HAND)
        {
            return InteractionResult.PASS;
        }
        if (!this.level().isClientSide)
        {
            String now = this.action();
            String next = ANIMATIONS.get((ANIMATIONS.indexOf(now == null ? "idle" : now) + 1) % ANIMATIONS.size());
            this.playShowcase(next);
            player.displayClientMessage(Component.translatable("dross.silverwood_giant.showcase.playing",
                    this.look().label(), next), true);
        }
        return InteractionResult.sidedSuccess(this.level().isClientSide);
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
    public boolean canBeLeashed(Player player)
    {
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distanceToClosestPlayer)
    {
        return !this.isShowcase();
    }

    @Override
    protected boolean shouldDespawnInPeaceful()
    {
        return !this.isShowcase();
    }

    // ---------------------------------------------------------------- saving

    @Override
    public void addAdditionalSaveData(CompoundTag tag)
    {
        super.addAdditionalSaveData(tag);
        tag.putString(TAG_LOOK, this.look().id());
        if (this.isShowcase())
        {
            tag.putBoolean(TAG_SHOWCASE, true);
        }
        this.addPersistentAngerSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        GiantMoments.Look look = GiantMoments.byId(tag.getString(TAG_LOOK));
        this.setLook(look == null ? GiantMoments.DEFAULT : look);
        this.entityData.set(DATA_SHOWCASE, tag.getBoolean(TAG_SHOWCASE));
        this.readPersistentAngerSaveData(this.level(), tag);
    }

    /** Minecraft's move control, but a giant turns at most {@code maxTurn} degrees a tick as it walks. */
    private static final class TurningMoveControl extends MoveControl
    {
        private final float maxTurn;

        TurningMoveControl(Mob mob, float maxTurn)
        {
            super(mob);
            this.maxTurn = maxTurn;
        }

        @Override
        protected float rotlerp(float from, float to, float max)
        {
            return super.rotlerp(from, to, Math.min(max, this.maxTurn));
        }
    }
}
