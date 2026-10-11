package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
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
import java.util.List;
import java.util.Map;

/**
 * The Cinder Colossus: a gorilla-like golem of cobblestone and obsidian with a volcano on its back and a lava core,
 * the mini-boss that serves the Fire Necromancer. For now it only exists as a showcase (looks and animations, no
 * fight): see CLAUDE.md, "The plan", step 8, and {@link GolemDesigns} for the candidate designs.
 * <p>
 * It has three looks ({@link GolemPhase}): dormant (its rock shell, the volcano only smoking), erupted (the volcano
 * has blown and lava has poured down over the shell) and core (the shell broken off: a lava body with a bigger,
 * always-erupting volcano). Clients get the design number, the phase and the current {@link GolemAction}, with a
 * counter that goes up every time an action starts, so a repeated action (another eruption, another death) restarts
 * its animation.
 * <p>
 * Two animation controllers: {@code main} moves the body ({@code idle}, {@code walk}, {@code erupt},
 * {@code break_shell}, {@code death}, {@code statue}); {@code volcano} moves the volcano's own bones (the blast of an
 * eruption, {@code volcano_burst}, and the core form's live eruption, {@code volcano_erupting}). The renderer
 * ({@code client.CinderColossusRenderer}) shows or hides bones by their name prefix ({@link #showsBone}), pours the
 * lava over the shell ({@link #flowProgress}) and cools it into obsidian when it dies. The particles come out of the
 * crater bones, and the eruption's lava and flames shoot out the way each volcano points (the renderer tells it where
 * the craters are and which way they face, {@link #setCraters}).
 * <p>
 * <b>Showcase golems</b> ({@link #spawnShowcase}, saved with {@code Showcase}) stand frozen (no AI), make no sound,
 * can't be pushed, never despawn (not even on Peaceful) and can't be hurt except by {@code /kill}. Right-clicking one
 * moves it on to its next stage (dormant: erupts; erupted: breaks out of its shell; lava or statue: dormant again);
 * sneak + right-click makes it die.
 */
public class CinderColossus extends Monster implements GeoEntity
{
    private static final EntityDataAccessor<Integer> DATA_DESIGN = SynchedEntityData.defineId(CinderColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_PHASE = SynchedEntityData.defineId(CinderColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION = SynchedEntityData.defineId(CinderColossus.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_ACTION_SERIAL = SynchedEntityData.defineId(CinderColossus.class, EntityDataSerializers.INT);

    private static final String TAG_DESIGN = "Design";
    private static final String TAG_PHASE = "Phase";
    private static final String TAG_ACTION = "Action";
    private static final String TAG_SHOWCASE = "Showcase";

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
    private static final int ANIMATION_BLEND_TICKS = 6;

    private static final Map<GolemAction, RawAnimation> ANIMATIONS = new EnumMap<>(GolemAction.class);
    private static final RawAnimation VOLCANO_BURST = RawAnimation.begin().thenPlayAndHold("animation.cinder_colossus.volcano_burst");
    private static final RawAnimation VOLCANO_ERUPTING = RawAnimation.begin().thenLoop("animation.cinder_colossus.volcano_erupting");

    static
    {
        for (GolemAction action : GolemAction.values())
        {
            boolean loops = action == GolemAction.IDLE || action == GolemAction.WALK;
            ANIMATIONS.put(action, loops ? RawAnimation.begin().thenLoop(action.animationName())
                    : RawAnimation.begin().thenPlayAndHold(action.animationName()));
        }
    }

    private final AnimatableInstanceCache animationCache = GeckoLibUtil.createInstanceCache(this);
    /** True for a showcase golem. Server side only. */
    private boolean showcase;
    /** Server: tickCount when the current action started. */
    private int actionStartTick;
    /** Client: tickCount when this client saw the current action start. */
    private int clientActionStart;
    /** Client: the action counters the two animation controllers last started an animation for. */
    private int mainSerialSeen = -1;
    private int volcanoSerialSeen = -1;
    /** Client: where the volcano craters were the last time it was drawn, and which way they point, for the particles. */
    private List<Crater> craters = List.of();

    public CinderColossus(EntityType<? extends Monster> type, Level level)
    {
        super(type, level);
        this.xpReward = 0;
    }

    /** Placeholder numbers for the fight (not built yet); the showcase golems never fight. */
    public static AttributeSupplier.Builder createAttributes()
    {
        return Monster.createMonsterAttributes()
                .add(Attributes.MAX_HEALTH, 300.0D)
                .add(Attributes.ARMOR, 10.0D)
                .add(Attributes.KNOCKBACK_RESISTANCE, 1.0D)
                .add(Attributes.MOVEMENT_SPEED, 0.22D)
                .add(Attributes.ATTACK_DAMAGE, 18.0D)
                .add(Attributes.FOLLOW_RANGE, 40.0D);
    }

    @Override
    protected void defineSynchedData()
    {
        super.defineSynchedData();
        this.entityData.define(DATA_DESIGN, 1);
        this.entityData.define(DATA_PHASE, GolemPhase.DORMANT.ordinal());
        this.entityData.define(DATA_ACTION, GolemAction.IDLE.ordinal());
        this.entityData.define(DATA_ACTION_SERIAL, 0);
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

    /** True if this golem only shows off a design (spawned by {@link #spawnShowcase}). Server side only. */
    public boolean isShowcase()
    {
        return this.showcase;
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
        int elapsed = this.tickCount - this.actionStartTick;
        GolemDesigns.Design design = this.getDesign();
        switch (this.getAction())
        {
            case ERUPT -> {
                if (elapsed >= design.eruptTicks() + SETTLE_TICKS)
                {
                    this.setPhase(GolemPhase.ERUPTED);
                    this.startAction(GolemAction.IDLE);
                }
            }
            case BREAK_SHELL -> {
                if (elapsed >= design.breakTicks() + SETTLE_TICKS)
                {
                    this.setPhase(GolemPhase.CORE);
                    this.startAction(GolemAction.IDLE);
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
     * ({@code vent_*}) in the core form while alive, the eruption's blast ({@code burst_*}) only while erupting.
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
            return this.getAction() == GolemAction.ERUPT;
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
     * eruption in the core form; a burst when the shell breaks; steam while it cools.
     */
    private void spawnParticles()
    {
        RandomSource random = this.getRandom();
        GolemAction action = this.getAction();
        GolemPhase phase = this.getPhase();
        float t = this.animationSeconds(0.0F);
        float blast = GolemDesigns.ERUPT_BLAST;
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
        controllers.add(new AnimationController<>(this, "main", ANIMATION_BLEND_TICKS, this::animateBody));
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
        return state.setAndContinue(ANIMATIONS.get(this.getAction()));
    }

    /** The volcano's own bones: the blast while it erupts, the live eruption in the core form, else still. */
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
        golem.showcase = true;
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
            return InteractionResult.SUCCESS;
        }
        if (!this.showcase)
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

    /** A showcase golem can't be hurt by anything except damage that bypasses invulnerability ({@code /kill}, the void). */
    @Override
    public boolean isInvulnerableTo(DamageSource source)
    {
        if (this.showcase && !source.is(DamageTypeTags.BYPASSES_INVULNERABILITY))
        {
            return true;
        }
        return super.isInvulnerableTo(source);
    }

    @Override
    public boolean isPushable()
    {
        return !this.showcase && super.isPushable();
    }

    @Override
    public void push(double x, double y, double z)
    {
        if (!this.showcase)
        {
            super.push(x, y, z);
        }
    }

    @Override
    public boolean isPushedByFluid()
    {
        return !this.showcase;
    }

    @Override
    protected boolean shouldDespawnInPeaceful()
    {
        return !this.showcase;
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
        if (this.showcase)
        {
            tag.putBoolean(TAG_SHOWCASE, true);
        }
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag)
    {
        super.readAdditionalSaveData(tag);
        this.showcase = tag.getBoolean(TAG_SHOWCASE);
        if (tag.contains(TAG_DESIGN))
        {
            this.setDesign(tag.getInt(TAG_DESIGN));
        }
        GolemPhase phase = GolemPhase.byName(tag.getString(TAG_PHASE));
        GolemAction action = GolemAction.byName(tag.getString(TAG_ACTION));
        // Actions that were halfway through when it was saved pick up where they would have ended.
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
            }
        }
        this.setPhase(phase);
        this.startAction(action);
    }
}
