package com.reg21meme.dross.enchant;

import java.util.EnumSet;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.target.TargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.player.Player;

/**
 * Picks a target for a risen undead, like a tamed wolf does. One goal per {@link Source}:
 * <ul>
 *   <li>{@link Source#OWNER_ATTACKER}: mobs that hurt the owner (they defend the owner);</li>
 *   <li>{@link Source#OWNER_TARGET}: mobs the owner hits;</li>
 *   <li>{@link Source#SELF_ATTACKER}: mobs that hurt this risen undead (it fights back).</li>
 * </ul>
 * Players, the Dross trader and the owner's other risen undead are never picked
 * ({@link RisenUndead#isValidTarget}).
 */
public class RisenTargetGoal extends TargetGoal
{
    public enum Source { OWNER_ATTACKER, OWNER_TARGET, SELF_ATTACKER }

    /** Same as vanilla's "hurt by" targeting: doesn't need line of sight. */
    private static final TargetingConditions CONDITIONS = TargetingConditions.forCombat().ignoreLineOfSight().ignoreInvisibilityTesting();
    /** How long (ticks) they keep chasing a target they can't see. Vanilla's "hurt by" goal uses 300. */
    private static final int UNSEEN_MEMORY_TICKS = 300;

    private final Source source;
    /** Timestamp of the last hit we reacted to, so each hit is only picked up once. */
    private int timestamp;
    private LivingEntity candidate;

    public RisenTargetGoal(Mob mob, Source source)
    {
        super(mob, false);
        this.source = source;
        this.setFlags(EnumSet.of(Goal.Flag.TARGET));
    }

    @Override
    public boolean canUse()
    {
        LivingEntity found;
        int stamp;
        if (this.source == Source.SELF_ATTACKER)
        {
            found = this.mob.getLastHurtByMob();
            stamp = this.mob.getLastHurtByMobTimestamp();
        }
        else
        {
            Player owner = RisenUndead.getOwner(this.mob);
            if (owner == null)
            {
                return false;
            }
            if (this.source == Source.OWNER_ATTACKER)
            {
                found = owner.getLastHurtByMob();
                stamp = owner.getLastHurtByMobTimestamp();
            }
            else
            {
                found = owner.getLastHurtMob();
                stamp = owner.getLastHurtMobTimestamp();
            }
        }
        if (found == null || stamp == this.timestamp || !RisenUndead.isValidTarget(this.mob, found))
        {
            return false;
        }
        if (!this.canAttack(found, CONDITIONS))
        {
            return false;
        }
        this.candidate = found;
        this.timestamp = stamp;
        return true;
    }

    @Override
    public void start()
    {
        this.mob.setTarget(this.candidate);
        this.targetMob = this.candidate;
        this.unseenMemoryTicks = UNSEEN_MEMORY_TICKS;
        super.start();
    }
}
