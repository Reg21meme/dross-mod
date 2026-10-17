package com.reg21meme.dross.boss.rage;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.behavior.BehaviorUtils;
import net.minecraft.world.entity.ai.behavior.EntityTracker;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.monster.Slime;
import net.minecraft.world.entity.monster.warden.Warden;
import net.minecraft.world.entity.schedule.Activity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Makes a raged mob fight (see {@link Rage}): while it has the Rage, it gets the rage's goals; when the Rage is gone
 * they're taken away again and it's its old self. Called every tick for every mob ({@link RageEvents}), so the goals
 * come back by themselves after a world is loaded (the Rage is saved with the mob; the goals aren't).
 * <p>
 * The mob fights with its own attacks and moves; the rage only gives it its target ({@link RageTargetGoal}):
 * <ul>
 *   <li><b>Goal-based mobs</b> (most of Minecraft's: zombies, skeletons, creepers, spiders, endermen, iron golems,
 *       wolves, the Cinder Colossus, the Rat King...): their own attack goals go for whatever their target is.</li>
 *   <li><b>Brain-based fighters</b> (piglins, piglin brutes, hoglins, zoglins, axolotls, the warden): their brain's
 *       attack target is set too, and piglins are made angry at it, the warden too (best effort: a brain may still
 *       drop a target its own rules don't like).</li>
 *   <li><b>Mobs with no attack of their own</b> ({@link #headbutts}: no attack strength, like cows, pigs, sheep,
 *       chickens, villagers, horses, llamas, snow golems): they headbutt ({@link RageHeadbuttGoal}).</li>
 *   <li><b>Slimes and magma cubes</b> only hurt players and iron golems by touching them in Minecraft; a raged one
 *       hurts its raged enemy the same way.</li>
 * </ul>
 * Every raged mob shows a few specks of its team's colour now and then; its Rage and team never wear off (with no
 * enemy near it behaves normally, keeping its team). Every {@link RageTuning#TEAMMATE_CHECK_TICKS} ticks it also makes
 * sure it holds nothing against a teammate (its target, anger, brain memories: {@link Rage#forgetTeammates}), and that
 * none of its own target goals is stuck on a target the game refused ({@link #stopStuckTargetGoals}). While it goes for a raged enemy, its follow range is at least
 * {@link RageTuning#MIN_FOLLOW_RANGE} so it can find its way to an enemy across {@link RageTuning#RAGE_RANGE}
 * ({@link #boostFollowRange}); with no enemy it's back to its own.
 */
public final class RageAi
{
    /** The follow range boost while raged (not saved: it's put back with the goals). */
    private static final UUID FOLLOW_RANGE_ID = UUID.fromString("6f0c9c1e-6a77-4d0f-9d38-2b5a1c7e4f20");
    private static final String FOLLOW_RANGE_NAME = "dross:rage_follow_range";

    /** The raged mobs whose goals are in, with their fight state (server side; a mob forgotten when it's gone). */
    private static final Map<Mob, State> STATES = new WeakHashMap<>();

    private RageAi() {}

    /** One raged mob's goals and fight state. */
    private static final class State
    {
        final RageTargetGoal targetGoal;
        @Nullable
        final RageHeadbuttGoal headbuttGoal;
        final boolean brainFighter;
        /** True if a raged enemy was within range at its last check (it ignores players then). */
        boolean enemyNear;
        /** Ticks until its next check for enemies near. */
        int scanIn;

        State(RageTargetGoal targetGoal, @Nullable RageHeadbuttGoal headbuttGoal, boolean brainFighter)
        {
            this.targetGoal = targetGoal;
            this.headbuttGoal = headbuttGoal;
            this.brainFighter = brainFighter;
        }
    }

    // ---------------------------------------------------------------- what kind of fighter

    /**
     * True if it has no attack of its own and headbutts instead: a mob without an attack strength (cows, pigs, sheep,
     * chickens, villagers, horses, llamas, snow golems...). Judgment calls, with an attack strength but no attack on
     * other mobs: a rabbit (only the killer bunny bites), a goat (it rams whatever it likes on its own timer) and a frog
     * (it only eats small slimes) headbutt too.
     */
    public static boolean headbutts(Mob mob)
    {
        if (mob.getAttribute(Attributes.ATTACK_DAMAGE) == null)
        {
            return true;
        }
        if (mob instanceof Rabbit rabbit)
        {
            return rabbit.getVariant() != Rabbit.Variant.EVIL;
        }
        return mob instanceof Goat || mob instanceof Frog;
    }

    /** True for a brain-based mob (villagers, piglins, camels...): its brain walks it about. */
    static boolean walksByBrain(Mob mob)
    {
        return mob.getBrain().checkMemory(MemoryModuleType.WALK_TARGET, MemoryStatus.REGISTERED);
    }

    /** True for a brain-based mob that fights through its brain's attack target (piglins, hoglins, the warden...). */
    static boolean fightsWithBrain(Mob mob)
    {
        return !headbutts(mob) && mob.getBrain().checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED);
    }

    // ---------------------------------------------------------------- goals in and out

    /** Gives a raged mob its rage goals (if it hasn't got them yet). Server side. */
    static void ensureInstalled(Mob mob)
    {
        if (!mob.level().isClientSide && !STATES.containsKey(mob))
        {
            install(mob);
        }
    }

    private static State install(Mob mob)
    {
        RageTargetGoal targetGoal = new RageTargetGoal(mob);
        mob.targetSelector.addGoal(RageTuning.GOAL_PRIORITY, targetGoal);
        RageHeadbuttGoal headbuttGoal = null;
        if (headbutts(mob))
        {
            headbuttGoal = new RageHeadbuttGoal(mob, walksByBrain(mob));
            mob.goalSelector.addGoal(RageTuning.GOAL_PRIORITY, headbuttGoal);
        }
        State state = new State(targetGoal, headbuttGoal, fightsWithBrain(mob));
        state.enemyNear = Rage.hasEnemyNear(mob);
        state.scanIn = RageTuning.ENEMY_SCAN_TICKS;
        STATES.put(mob, state);
        return state;
    }

    /** Takes a mob's rage goals away (its Rage was cleared, with {@code /effect clear}) and forgets its team. Server side. */
    static void uninstall(Mob mob)
    {
        mob.getPersistentData().remove(Rage.TAG_TEAM);
        State state = STATES.remove(mob);
        if (state == null)
        {
            return;
        }
        mob.targetSelector.removeGoal(state.targetGoal); // (stopping it lets go of its raged target)
        if (state.headbuttGoal != null)
        {
            mob.goalSelector.removeGoal(state.headbuttGoal);
        }
        restoreFollowRange(mob);
    }

    /**
     * While it goes for a raged enemy ({@link RageTargetGoal}), its follow range (how far it tracks its target and finds
     * paths) is at least {@link RageTuning#MIN_FOLLOW_RANGE}, so it can reach an enemy across
     * {@link RageTuning#RAGE_RANGE}. (Not saved: the goal puts it back after a world is loaded.)
     */
    static void boostFollowRange(Mob mob)
    {
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null && follow.getModifier(FOLLOW_RANGE_ID) == null)
        {
            double extra = RageTuning.MIN_FOLLOW_RANGE - follow.getValue();
            if (extra > 0.0D)
            {
                follow.addTransientModifier(new AttributeModifier(FOLLOW_RANGE_ID, FOLLOW_RANGE_NAME, extra,
                        AttributeModifier.Operation.ADDITION));
            }
        }
    }

    /** Its own follow range again (no raged enemy to go for: it behaves normally). */
    static void restoreFollowRange(Mob mob)
    {
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null)
        {
            follow.removeModifier(FOLLOW_RANGE_ID);
        }
    }

    /** True if this mob has its rage goals now (for the tests: they come back after a world is loaded). */
    public static boolean hasRageGoals(Mob mob)
    {
        State state = STATES.get(mob);
        return state != null && mob.targetSelector.getAvailableGoals().stream().anyMatch(goal -> goal.getGoal() == state.targetGoal);
    }

    // ---------------------------------------------------------------- every tick

    /** Every mob, every tick (server side): goals in or out as its Rage comes and goes, and a raged mob's fight. */
    static void tick(Mob mob)
    {
        boolean raged = Rage.isRaged(mob);
        if (!raged && STATES.isEmpty())
        {
            return;
        }
        State state = STATES.get(mob);
        if (!raged)
        {
            if (state != null)
            {
                uninstall(mob); // the Rage was cleared (/effect clear): its old self again
            }
            return;
        }
        if (!mob.isAlive() || !(mob.level() instanceof ServerLevel level))
        {
            STATES.remove(mob); // dead (a Colossus statue, a Rat King lying there): out of the fight
            return;
        }
        if (state == null)
        {
            state = install(mob); // a raged mob loaded from a save, or given the Rage by a command
            Rage.settleTeam(mob); // (its teammates and it let go of each other, as when it joined)
        }
        if (--state.scanIn <= 0)
        {
            state.scanIn = RageTuning.ENEMY_SCAN_TICKS;
            state.enemyNear = Rage.hasEnemyNear(mob);
        }
        RageTeam team = Rage.teamOf(mob);
        if (team != null && (mob.tickCount + mob.getId()) % RageTuning.TEAMMATE_CHECK_TICKS == 0)
        {
            Rage.forgetTeammates(mob); // the safety net: never a grudge against its own team, however it got one
            stopStuckTargetGoals(mob);
        }
        if (team != null && (mob.tickCount + mob.getId()) % RageTuning.RAGE_PARTICLE_EVERY == 0)
        {
            Rage.teamParticles(level, mob, team, RageTuning.RAGE_PARTICLES);
        }
        LivingEntity target = state.targetGoal.target();
        if (target != null && target.isAlive())
        {
            if (state.brainFighter)
            {
                brainAttack(mob, target);
            }
            if (mob instanceof Slime slime)
            {
                slimeTouch(slime, target);
            }
        }
    }

    // ---------------------------------------------------------------- letting go of teammates

    /**
     * Stops whatever in its target selector holds on to its teammate {@code mate} (see {@link Rage#forget}): its rage
     * goal if that's aimed at it, and, if {@code mate} is its target now, its own running target goal, which picked it
     * and would keep setting it again (a hurt-by goal remembers whoever hurt it). It picks again from scratch, and the
     * Rage never lets it pick a teammate ({@link RageEvents}).
     */
    static void stopTargetGoalsOn(Mob mob, LivingEntity mate, boolean mateIsTarget)
    {
        List<WrappedGoal> running = mob.targetSelector.getRunningGoals().toList();
        for (WrappedGoal wrapped : running)
        {
            Goal goal = wrapped.getGoal();
            boolean holdsMate = goal instanceof RageTargetGoal rage ? rage.target() == mate
                    : mateIsTarget && wrapped.getFlags().contains(Goal.Flag.TARGET);
            if (holdsMate)
            {
                wrapped.stop();
            }
        }
    }

    /**
     * The safety net's second half: a target goal of its own still running while it has no target at all is stuck on a
     * target the game refused (a teammate it went for before they were teammates: a hurt-by goal keeps trying to set
     * it again, and blocks its other target goals meanwhile). It's stopped, so it can pick again. Its rage goal isn't
     * touched (it lets go of a refused target by itself).
     */
    static void stopStuckTargetGoals(Mob mob)
    {
        if (mob.getTarget() != null)
        {
            return;
        }
        List<WrappedGoal> running = mob.targetSelector.getRunningGoals().toList();
        for (WrappedGoal wrapped : running)
        {
            if (!(wrapped.getGoal() instanceof RageTargetGoal) && wrapped.getFlags().contains(Goal.Flag.TARGET))
            {
                wrapped.stop();
            }
        }
    }

    /**
     * Who a brain-based mob's brain holds something against: its attack target, who it's angry at, who last hurt it,
     * and whom it's walking at (only the memories its brain has; none for a goal-based mob).
     */
    static List<Entity> brainGrudges(Mob mob, ServerLevel level)
    {
        List<Entity> held = new ArrayList<>(4);
        Brain<?> brain = mob.getBrain();
        LivingEntity attackTarget = memory(brain, MemoryModuleType.ATTACK_TARGET);
        if (attackTarget != null)
        {
            held.add(attackTarget);
        }
        UUID angryAt = memory(brain, MemoryModuleType.ANGRY_AT);
        Entity angryAtEntity = angryAt == null ? null : level.getEntity(angryAt);
        if (angryAtEntity != null)
        {
            held.add(angryAtEntity);
        }
        LivingEntity hurtBy = memory(brain, MemoryModuleType.HURT_BY_ENTITY);
        if (hurtBy != null)
        {
            held.add(hurtBy);
        }
        Entity walkingAt = walkingAt(brain);
        if (walkingAt != null)
        {
            held.add(walkingAt);
        }
        return held;
    }

    /**
     * Its brain lets go of its teammate {@code mate}: no attack target, anger, hurt-by or walk target pointing at it.
     *
     * @return true if it was walking at it (it stops)
     */
    static boolean forgetBrainMate(Mob mob, LivingEntity mate)
    {
        forgetBrainTarget(mob, mate);
        Brain<?> brain = mob.getBrain();
        if (memory(brain, MemoryModuleType.HURT_BY_ENTITY) == mate)
        {
            brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
            brain.eraseMemory(MemoryModuleType.HURT_BY);
        }
        if (walkingAt(brain) == mate)
        {
            brain.eraseMemory(MemoryModuleType.WALK_TARGET);
            return true;
        }
        return false;
    }

    /** The entity a brain is walking at (its walk target follows an entity), or null. */
    @Nullable
    private static Entity walkingAt(Brain<?> brain)
    {
        WalkTarget walk = memory(brain, MemoryModuleType.WALK_TARGET);
        return walk != null && walk.getTarget() instanceof EntityTracker tracker ? tracker.getEntity() : null;
    }

    // ---------------------------------------------------------------- brain-based mobs (best effort)

    /** A brain-based fighter: its brain's attack target is its raged enemy, and it's angry at it (piglins, the warden). */
    private static void brainAttack(Mob mob, LivingEntity target)
    {
        Brain<?> brain = mob.getBrain();
        if (memory(brain, MemoryModuleType.ATTACK_TARGET) != target)
        {
            brain.setMemory(MemoryModuleType.ATTACK_TARGET, target);
            if (mob instanceof Warden warden)
            {
                warden.increaseAngerAt(target, RageTuning.WARDEN_ANGER, false);
            }
        }
        if (brain.checkMemory(MemoryModuleType.ANGRY_AT, MemoryStatus.REGISTERED))
        {
            brain.setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, target.getUUID(), RageTuning.BRAIN_ANGER_TICKS);
        }
    }

    /** Its target goal let go of {@code target}: a brain-based mob's brain lets go of it too. */
    static void forgetBrainTarget(Mob mob, LivingEntity target)
    {
        Brain<?> brain = mob.getBrain();
        if (memory(brain, MemoryModuleType.ATTACK_TARGET) == target)
        {
            brain.eraseMemory(MemoryModuleType.ATTACK_TARGET);
        }
        if (target.getUUID().equals(memory(brain, MemoryModuleType.ANGRY_AT)))
        {
            brain.eraseMemory(MemoryModuleType.ANGRY_AT);
        }
    }

    /** A brain's memory, or null if it has none (or doesn't have that kind of memory at all: then asking would throw). */
    @Nullable
    private static <U> U memory(Brain<?> brain, MemoryModuleType<U> type)
    {
        return brain.checkMemory(type, MemoryStatus.VALUE_PRESENT) ? brain.getMemory(type).orElse(null) : null;
    }

    /**
     * A brain-based headbutter (a villager, a camel...) walks at its raged enemy: its brain's walk target. Its brain
     * would run from what hurt or scares it (a villager from a zombie, an animal that panics), so while it's raged
     * those memories are wiped and a panic is stopped.
     */
    static void walkAt(Mob mob, LivingEntity target, boolean repath)
    {
        Brain<?> brain = mob.getBrain();
        brain.eraseMemory(MemoryModuleType.HURT_BY);
        brain.eraseMemory(MemoryModuleType.HURT_BY_ENTITY);
        brain.eraseMemory(MemoryModuleType.NEAREST_HOSTILE);
        brain.eraseMemory(MemoryModuleType.AVOID_TARGET);
        if (brain.hasMemoryValue(MemoryModuleType.IS_PANICKING) || brain.isActive(Activity.PANIC))
        {
            brain.eraseMemory(MemoryModuleType.IS_PANICKING);
            stopBrain(mob);
            brain.useDefaultActivity();
            repath = true;
        }
        if (repath || !brain.hasMemoryValue(MemoryModuleType.WALK_TARGET))
        {
            BehaviorUtils.setWalkAndLookTargetMemories(mob, target, RageTuning.HEADBUTT_BRAIN_SPEED, 0);
        }
    }

    /** A brain-based headbutter stops walking at its enemy (its brain takes over again). */
    static void stopWalking(Mob mob)
    {
        mob.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        mob.getBrain().eraseMemory(MemoryModuleType.LOOK_TARGET);
    }

    /** Stops everything its brain is doing (a panic, mostly); it starts again next tick with its walk target. */
    @SuppressWarnings("unchecked")
    private static void stopBrain(Mob mob)
    {
        if (mob.level() instanceof ServerLevel level)
        {
            ((Brain<Mob>) mob.getBrain()).stopAll(level, mob);
        }
    }

    // ---------------------------------------------------------------- slimes

    /**
     * Minecraft's slimes (bigger than tiny) and magma cubes hurt players and iron golems by touching them; a raged one
     * hurts its raged enemy the same way (Minecraft's reach, damage and sound; the hurt mob's invulnerability after a
     * hit spaces the hits out).
     */
    private static void slimeTouch(Slime slime, LivingEntity target)
    {
        if (slime.isTiny() && !(slime instanceof net.minecraft.world.entity.monster.MagmaCube))
        {
            return;
        }
        double reach = 0.6D * slime.getSize();
        if (slime.distanceToSqr(target) < reach * reach && slime.hasLineOfSight(target)
                && target.hurt(slime.damageSources().mobAttack(slime), (float) slime.getAttributeValue(Attributes.ATTACK_DAMAGE)))
        {
            slime.playSound(SoundEvents.SLIME_ATTACK, 1.0F, (slime.getRandom().nextFloat() - slime.getRandom().nextFloat()) * 0.2F + 1.0F);
        }
    }
}
