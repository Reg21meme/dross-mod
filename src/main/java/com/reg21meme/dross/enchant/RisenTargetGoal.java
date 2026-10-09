package com.reg21meme.dross.enchant;

import java.util.EnumSet;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.Brain;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.MemoryStatus;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;

/**
 * The risen undead's "pack AI": the one goal that picks what a risen undead fights.
 *
 * <p><b>Commitment:</b> once it has a target it keeps it until that target dies or becomes invalid (gone, out of its
 * follow range, a forbidden target...). It never switches to a "better" target mid-fight.
 *
 * <p><b>When it's free</b> (no target), it picks one in this order, highest first:
 * <ol>
 *   <li><b>Defend the owner:</b> the mob that last hurt the owner, or (searched every
 *       {@link #SEARCH_INTERVAL_TICKS}) the nearest mob within {@link #DEFEND_RADIUS} of the owner that is
 *       targeting the owner;</li>
 *   <li><b>The owner's target:</b> the mob the owner last hit, if that hit was within
 *       {@link #OWNER_TARGET_MEMORY_TICKS};</li>
 *   <li><b>Fight back:</b> a mob that hurt this risen undead (an existing rule, kept);</li>
 *   <li><b>Help the pack:</b> (searched every {@link #SEARCH_INTERVAL_TICKS}, and right away when a fight ends) the
 *       target of the nearest of the same owner's risen undead within {@link #PACK_RADIUS} that is fighting.</li>
 * </ol>
 * If none of these finds anything, {@link RisenFollowOwnerGoal} walks it after the owner.
 *
 * <p>Players, villagers, wandering traders, the Dross trader and the owner's other risen undead are never picked
 * ({@link RisenUndead#isValidTarget}).
 */
public class RisenTargetGoal extends TargetGoal
{
    // ---------------------------------------------------------------------------------------------
    // Tuning numbers (20 ticks = 1 second).
    // ---------------------------------------------------------------------------------------------

    /** "Help the pack": allies (same owner's risen undead) this close (blocks) get help. */
    public static final double PACK_RADIUS = 16.0D;
    /** "Defend the owner": mobs this close (blocks, X/Y/Z) to the owner that are targeting the owner get attacked. */
    public static final double DEFEND_RADIUS = 16.0D;
    /** How often (ticks) a free risen undead runs the two searches above: every 0.5 seconds. */
    public static final int SEARCH_INTERVAL_TICKS = 10;
    /** The owner's last hit counts as "the owner's target" for this long (ticks): 5 seconds. */
    public static final int OWNER_TARGET_MEMORY_TICKS = 100;

    /** Same as vanilla's "hurt by" targeting: doesn't need line of sight. */
    private static final TargetingConditions CONDITIONS = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();

    /** Game time of the next search; 0 = search on the next check. Each mob starts at a random offset. */
    private long nextSearchTime;
    @Nullable
    private LivingEntity candidate;

    public RisenTargetGoal(Mob mob)
    {
        super(mob, false);
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
        // Spread the searches of a freshly raised group over different ticks.
        this.nextSearchTime = mob.level().getGameTime() + mob.getRandom().nextInt(SEARCH_INTERVAL_TICKS);
    }

    // ---------------------------------------------------------------------------------------------
    // Picking a target (only while free)
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean canUse()
    {
        this.candidate = this.pick();
        return this.candidate != null;
    }

    @Nullable
    private LivingEntity pick()
    {
        // Already has a target (for example the mob the owner hit when it rose, or a mob that just hit it):
        // commitment, keep it.
        LivingEntity current = this.mob.getTarget();
        if (this.usable(current))
        {
            return current;
        }

        Player owner = RisenUndead.getOwner(this.mob);
        long now = this.mob.level().getGameTime();
        boolean search = now >= this.nextSearchTime;
        if (search)
        {
            this.nextSearchTime = now + SEARCH_INTERVAL_TICKS;
        }

        if (owner != null)
        {
            // 1. Defend the owner.
            LivingEntity attacker = owner.getLastHurtByMob(); // vanilla forgets it after 5 seconds
            if (this.usable(attacker))
            {
                return attacker;
            }
            if (search)
            {
                LivingEntity threat = this.findMobTargeting(owner);
                if (threat != null)
                {
                    return threat;
                }
            }

            // 2. The owner's target.
            LivingEntity hit = owner.getLastHurtMob();
            if (owner.tickCount - owner.getLastHurtMobTimestamp() <= OWNER_TARGET_MEMORY_TICKS && this.usable(hit))
            {
                return hit;
            }
        }

        // 3. Fight back against whatever hurt it (vanilla forgets it after 5 seconds).
        LivingEntity selfAttacker = this.mob.getLastHurtByMob();
        if (this.usable(selfAttacker))
        {
            return selfAttacker;
        }

        // 4. Help the pack.
        if (search)
        {
            return this.findPackTarget();
        }
        return null;
    }

    /** The nearest valid mob within {@link #DEFEND_RADIUS} of the owner that is targeting the owner. */
    @Nullable
    private LivingEntity findMobTargeting(Player owner)
    {
        AABB area = owner.getBoundingBox().inflate(DEFEND_RADIUS);
        LivingEntity best = null;
        double bestDist = Double.MAX_VALUE;
        for (Mob other : owner.level().getEntitiesOfClass(Mob.class, area, other -> isTargeting(other, owner)))
        {
            double dist = this.mob.distanceToSqr(other);
            if (dist < bestDist && this.usable(other))
            {
                best = other;
                bestDist = dist;
            }
        }
        return best;
    }

    /** True if {@code other} is after {@code owner}: its target, or (piglins, wardens...) its brain's attack target. */
    private static boolean isTargeting(Mob other, Player owner)
    {
        if (other.getTarget() == owner)
        {
            return true;
        }
        Brain<?> brain = other.getBrain();
        return brain.checkMemory(MemoryModuleType.ATTACK_TARGET, MemoryStatus.REGISTERED)
                && brain.getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == owner;
    }

    /** The target of the nearest same-owner risen undead within {@link #PACK_RADIUS} that is fighting. */
    @Nullable
    private LivingEntity findPackTarget()
    {
        UUID ownerId = RisenUndead.getOwnerId(this.mob);
        if (ownerId == null)
        {
            return null;
        }
        LivingEntity best = null;
        double bestDist = PACK_RADIUS * PACK_RADIUS;
        // The list of loaded risen undead (at most a few dozen), not a world search.
        for (Mob ally : RisenUndead.active())
        {
            if (ally == this.mob || !ally.isAlive() || ally.level() != this.mob.level() || !ownerId.equals(RisenUndead.getOwnerId(ally)))
            {
                continue;
            }
            double dist = this.mob.distanceToSqr(ally);
            if (dist > bestDist)
            {
                continue;
            }
            LivingEntity allyTarget = ally.getTarget();
            if (this.usable(allyTarget))
            {
                best = allyTarget;
                bestDist = dist;
            }
        }
        return best;
    }

    /**
     * Allowed ({@link RisenUndead#isValidTarget}), attackable, and inside this mob's follow range (otherwise vanilla
     * would drop it again right away).
     */
    private boolean usable(@Nullable LivingEntity target)
    {
        if (!RisenUndead.isValidTarget(this.mob, target) || !this.canAttack(target, CONDITIONS))
        {
            return false;
        }
        double range = this.getFollowDistance();
        return this.mob.distanceToSqr(target) <= range * range;
    }

    // ---------------------------------------------------------------------------------------------
    // Fighting
    // ---------------------------------------------------------------------------------------------

    @Override
    public void start()
    {
        this.mob.setTarget(this.candidate);
        this.targetMob = this.candidate;
        this.candidate = null;
        super.start();
    }

    /** Keep the target until it dies or becomes invalid (vanilla also drops it when out of follow range). */
    @Override
    public boolean canContinueToUse()
    {
        LivingEntity target = this.mob.getTarget() != null ? this.mob.getTarget() : this.targetMob;
        return RisenUndead.isValidTarget(this.mob, target) && super.canContinueToUse();
    }

    @Override
    public void stop()
    {
        super.stop();
        // The fight is over: look for allies that are still fighting right away, before following the owner.
        this.nextSearchTime = 0L;
    }
}
