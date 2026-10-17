package com.reg21meme.dross.enchant;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.rage.Rage;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The player's allies leave the risen undead alone (the user's request: "iron golems don't attack Necromancy-spawned
 * mobs, because they are allies to the player"). The allies ({@link #isAlly}) are iron golems (village or player-built),
 * snow golems and tamed wolves (anyone's). None of them ever targets, chases, gets angry at or hurts a risen undead (or a
 * risen jockey's mount), whoever its owner is:
 * <ul>
 *   <li>their own searches for something to attack skip risen undead ({@link #skipRisenInSearches}), so an iron golem
 *       still finds a real zombie standing behind your skeletons (it always goes for the nearest one it may attack);</li>
 *   <li>anything else that would make one of them target a risen undead is refused ({@link #onChangeTarget}): hitting
 *       back after a risen undead hit it, a wolf helping its owner or its pack...;</li>
 *   <li>their blows, and a snow golem's stray snowball, never land on one ({@link #onAttack});</li>
 *   <li>every {@link #CHECK_INTERVAL_TICKS} ticks each ally lets go of anything it still holds against one
 *       ({@link #forgetRisen}): its target (with the target goal holding it, and its walk towards it), its anger, and who
 *       last hurt it. That ends a fight already under way.</li>
 * </ul>
 * The risen undead keep their own rules ({@link RisenTargetGoal}): they never pick a golem or a wolf on their own, but
 * they still defend their owner from one that attacks the owner, and join in when the owner hits one. It just won't hit
 * back: allies don't fight allies. Only their pack AI may send them after an ally: a zombie calling nearby zombies for
 * help against a golem doesn't call risen zombies ({@link #onChangeTarget}).
 *
 * <p><b>The Rage Wand</b> (the boss area's {@code boss/rage}): if an ally and a risen undead are both raged on different
 * teams, the Rage wins and they may fight ({@link Rage#areEnemies}). Raged on the same team, the Rage keeps them apart
 * itself. Everything here is server side.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RisenAllies
{
    // ---------------------------------------------------------------------------------------------
    // Tuning numbers (20 ticks = 1 second).
    // ---------------------------------------------------------------------------------------------

    /** How often (ticks) each ally checks that it holds nothing against a risen undead: every half second. */
    public static final int CHECK_INTERVAL_TICKS = 10;

    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The search conditions inside Minecraft's "attack the nearest X" target goal, and the filter inside those conditions.
     * Neither can be reached from outside, so they're found by their type (their names differ between the development game
     * and the released one). Null if they couldn't be found: then the allies' searches aren't filtered, and only the other
     * rules keep them off the risen undead (they still never target one, but may not spot a mob behind one).
     */
    @Nullable
    private static final Field SEARCH_CONDITIONS = findField(NearestAttackableTargetGoal.class, TargetingConditions.class);
    @Nullable
    private static final Field SEARCH_FILTER = findField(TargetingConditions.class, Predicate.class);
    /** True once the "couldn't filter the searches" warning has been logged (it's only logged once). */
    private static boolean warned;

    private RisenAllies() {}

    // ---------------------------------------------------------------------------------------------
    // Who
    // ---------------------------------------------------------------------------------------------

    /** The player's allies: iron golems, snow golems and tamed wolves. Server side (a wolf's tameness is checked now). */
    public static boolean isAlly(@Nullable Entity entity)
    {
        return entity instanceof IronGolem || entity instanceof SnowGolem || (entity instanceof Wolf wolf && wolf.isTame());
    }

    /**
     * True if {@code ally} is one of the player's allies and must leave {@code other} alone: {@code other} is a risen
     * undead (or a risen mount), and the Rage hasn't made the two enemies. Safe to call with null.
     */
    public static boolean leavesAlone(@Nullable Entity ally, @Nullable Entity other)
    {
        return isAlly(ally) && RisenUndead.isRisen(other) && !Rage.areEnemies(ally, other);
    }

    /** A mob that is an ally now or may become one (a wild wolf can be tamed later). */
    private static boolean mayBecomeAlly(Entity entity)
    {
        return entity instanceof IronGolem || entity instanceof SnowGolem || entity instanceof Wolf;
    }

    // ---------------------------------------------------------------------------------------------
    // Events
    // ---------------------------------------------------------------------------------------------

    /**
     * A golem or a wolf joins the world (spawned, built, or loaded with its chunk): its searches skip risen undead. Last
     * priority, so a target goal another mod adds when it joins is covered too.
     */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onJoinLevel(EntityJoinLevelEvent event)
    {
        if (!event.getLevel().isClientSide() && mayBecomeAlly(event.getEntity()) && event.getEntity() instanceof Mob mob)
        {
            skipRisenInSearches(mob);
        }
    }

    /**
     * Both ways round:
     * <ul>
     *   <li>an ally never takes a risen undead as its target, whatever tries to set it;</li>
     *   <li>a risen undead takes an ally as its target only when its own pack AI picks it ({@link RisenTargetGoal}:
     *       defending its owner, its owner's target, helping its pack), never through anything else. A zombie hurt by an
     *       iron golem calls every zombie near it with no target onto the golem, risen zombies included; that call is
     *       refused, or the player's undead would gang up on the golem on their own.</li>
     * </ul>
     * Unless the Rage Wand made the two enemies.
     */
    @SubscribeEvent
    public static void onChangeTarget(LivingChangeTargetEvent event)
    {
        if (!(event.getEntity() instanceof Mob mob) || mob.level().isClientSide())
        {
            return;
        }
        LivingEntity newTarget = event.getNewTarget();
        if (leavesAlone(mob, newTarget))
        {
            event.setCanceled(true);
        }
        else if (newTarget != null && newTarget != mob.getTarget() && RisenUndead.isRisen(mob) && isAlly(newTarget)
                && !RisenTargetGoal.isPicking(mob) && !Rage.areEnemies(mob, newTarget))
        {
            event.setCanceled(true);
        }
    }

    /** An ally's blows, bites and snowballs never land on a risen undead (so it never "fights back" at an ally either). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event)
    {
        LivingEntity victim = event.getEntity();
        if (!victim.level().isClientSide() && leavesAlone(event.getSource().getEntity(), victim))
        {
            event.setCanceled(true);
        }
    }

    /** Every {@link #CHECK_INTERVAL_TICKS} ticks, each ally lets go of anything it holds against a risen undead. */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        if (event.getEntity() instanceof Mob ally && !ally.level().isClientSide() && isAlly(ally)
                && (ally.tickCount + ally.getId()) % CHECK_INTERVAL_TICKS == 0)
        {
            forgetRisen(ally);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Letting go
    // ---------------------------------------------------------------------------------------------

    /**
     * The ally lets go of every risen undead it must leave alone: its target (and the target goal holding it, which would
     * keep setting it again, and its walk towards it: an iron golem's walk carries on by itself), whoever last hurt it (its
     * "hit back" goal would turn on it), and its anger (an iron golem's or a tamed wolf's). Package-private for the tests.
     */
    static void forgetRisen(Mob ally)
    {
        LivingEntity target = ally.getTarget();
        if (leavesAlone(ally, target))
        {
            for (WrappedGoal running : ally.targetSelector.getRunningGoals().toList())
            {
                if (running.getFlags().contains(Goal.Flag.TARGET))
                {
                    running.stop(); // a target goal clears the target when it stops
                }
            }
            if (ally.getTarget() == target)
            {
                ally.setTarget(null);
            }
            ally.getNavigation().stop();
        }
        if (leavesAlone(ally, ally.getLastHurtByMob()))
        {
            ally.setLastHurtByMob(null);
        }
        if (ally instanceof NeutralMob neutral && ally.level() instanceof ServerLevel level)
        {
            UUID angryAt = neutral.getPersistentAngerTarget();
            if (angryAt != null && leavesAlone(ally, level.getEntity(angryAt)))
            {
                neutral.setPersistentAngerTarget(null);
                neutral.setRemainingPersistentAngerTime(0);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Their searches skip risen undead
    // ---------------------------------------------------------------------------------------------

    /**
     * Each of the mob's "attack the nearest X" target goals (an iron golem's "nearest monster", a snow golem's, a wolf's
     * "nearest skeleton"...) gets a filter that also skips the risen undead it must leave alone, on top of its own filter.
     * Without it, a golem whose nearest monster is a risen undead would pick that one, be refused, and never look past it.
     * Done once per goal (a goal that already has it is left as it is).
     */
    @SuppressWarnings("unchecked")
    static void skipRisenInSearches(Mob mob)
    {
        if (SEARCH_CONDITIONS == null || SEARCH_FILTER == null)
        {
            warnOnce(null);
            return;
        }
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals())
        {
            if (!(wrapped.getGoal() instanceof NearestAttackableTargetGoal<?> search))
            {
                continue;
            }
            try
            {
                TargetingConditions conditions = (TargetingConditions) SEARCH_CONDITIONS.get(search);
                if (conditions == null)
                {
                    continue;
                }
                Object filter = SEARCH_FILTER.get(conditions);
                if (filter instanceof SkipRisen)
                {
                    continue;
                }
                // A copy, so conditions another goal might share are never changed.
                SEARCH_CONDITIONS.set(search, conditions.copy().selector(new SkipRisen(mob, (Predicate<LivingEntity>) filter)));
            }
            catch (IllegalAccessException | RuntimeException e)
            {
                warnOnce(e);
                return;
            }
        }
    }

    /**
     * True if every "attack the nearest X" target goal of this mob skips risen undead (and it has at least one). For the
     * tests.
     */
    static boolean searchesSkipRisen(Mob mob)
    {
        if (SEARCH_CONDITIONS == null || SEARCH_FILTER == null)
        {
            return false;
        }
        boolean any = false;
        for (WrappedGoal wrapped : mob.targetSelector.getAvailableGoals())
        {
            if (wrapped.getGoal() instanceof NearestAttackableTargetGoal<?> search)
            {
                any = true;
                try
                {
                    TargetingConditions conditions = searchConditions(search);
                    if (conditions == null || !(SEARCH_FILTER.get(conditions) instanceof SkipRisen))
                    {
                        return false;
                    }
                }
                catch (IllegalAccessException | RuntimeException e)
                {
                    return false;
                }
            }
        }
        return any;
    }

    /** The conditions a "nearest X" target goal searches with, or null if they can't be read. Also used by the tests. */
    @Nullable
    static TargetingConditions searchConditions(NearestAttackableTargetGoal<?> search)
    {
        if (SEARCH_CONDITIONS == null)
        {
            return null;
        }
        try
        {
            return (TargetingConditions) SEARCH_CONDITIONS.get(search);
        }
        catch (IllegalAccessException | RuntimeException e)
        {
            return null;
        }
    }

    /** A search filter: the goal's own filter (if any), and never a risen undead this ally must leave alone. */
    private record SkipRisen(Mob ally, @Nullable Predicate<LivingEntity> original) implements Predicate<LivingEntity>
    {
        @Override
        public boolean test(LivingEntity candidate)
        {
            return (this.original == null || this.original.test(candidate)) && !leavesAlone(this.ally, candidate);
        }
    }

    /** The one non-static field of this type declared in {@code owner}, made accessible; null if there isn't exactly one. */
    @Nullable
    private static Field findField(Class<?> owner, Class<?> type)
    {
        Field found = null;
        for (Field field : owner.getDeclaredFields())
        {
            if (field.getType() == type && !Modifier.isStatic(field.getModifiers()))
            {
                if (found != null)
                {
                    return null; // more than one: can't tell which
                }
                found = field;
            }
        }
        if (found == null)
        {
            return null;
        }
        try
        {
            found.setAccessible(true);
            return found;
        }
        catch (RuntimeException e)
        {
            return null;
        }
    }

    private static void warnOnce(@Nullable Exception e)
    {
        if (!warned)
        {
            warned = true;
            LOGGER.warn("Couldn't make the target searches of iron golems, snow golems and tamed wolves skip Necromancy's "
                    + "risen undead. They still never target them, but may not spot a monster standing behind one.", e);
        }
    }
}
