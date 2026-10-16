package com.reg21meme.dross.boss.rage;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import java.util.EnumSet;

/**
 * The headbutt: how a raged mob with no attack of its own (cows, pigs, sheep, chickens, villagers...: see
 * {@link RageAi#headbutts}) fights. The user: no new animations or models, all the old Minecraft rules apply. It walks
 * at its raged enemy with its own walk and, once it's close enough ({@link RageTuning#HEADBUTT_REACH}), bumps it for
 * {@link RageTuning#HEADBUTT_DAMAGE} every {@link RageTuning#HEADBUTT_COOLDOWN} ticks: an ordinary mob hit, so the
 * enemy flinches, is knocked back a little and makes its hurt sound.
 * <p>
 * It runs ahead of the mob's own goals ({@link RageTuning#GOAL_PRIORITY}), so a raged cow doesn't panic or follow
 * wheat. A brain-based mob (a villager, a camel...) is walked there by its brain ({@link RageAi#walkAt}).
 */
public class RageHeadbuttGoal extends Goal
{
    private final Mob mob;
    /** True for a brain-based mob: its brain walks it (through its walk target), not this goal. */
    private final boolean brainWalker;
    private int cooldown;
    private int repathIn;

    public RageHeadbuttGoal(Mob mob, boolean brainWalker)
    {
        this.mob = mob;
        this.brainWalker = brainWalker;
        this.setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        return this.enemy() != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.enemy() != null;
    }

    /** Its target, if that's a raged enemy it can go for; else null. */
    private LivingEntity enemy()
    {
        LivingEntity target = this.mob.getTarget();
        return this.mob.isAlive() && Rage.isTargetable(this.mob, target) ? target : null;
    }

    @Override
    public void start()
    {
        this.repathIn = 0;
        this.mob.setAggressive(true);
    }

    @Override
    public void stop()
    {
        this.mob.setAggressive(false);
        if (this.brainWalker)
        {
            RageAi.stopWalking(this.mob);
        }
        else
        {
            this.mob.getNavigation().stop();
        }
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void tick()
    {
        LivingEntity target = this.enemy();
        if (target == null)
        {
            return;
        }
        this.mob.getLookControl().setLookAt(target, 30.0F, 30.0F);
        boolean repath = --this.repathIn <= 0;
        if (repath)
        {
            this.repathIn = RageTuning.HEADBUTT_REPATH_TICKS;
        }
        if (this.brainWalker)
        {
            RageAi.walkAt(this.mob, target, repath);
        }
        else if (repath)
        {
            this.mob.getNavigation().moveTo(target, RageTuning.HEADBUTT_SPEED);
        }
        if (this.cooldown > 0)
        {
            this.cooldown--;
        }
        if (this.cooldown <= 0 && this.inReach(target) && this.mob.hasLineOfSight(target))
        {
            this.cooldown = RageTuning.HEADBUTT_COOLDOWN;
            this.mob.swing(InteractionHand.MAIN_HAND);
            if (target.hurt(this.mob.damageSources().mobAttack(this.mob), RageTuning.HEADBUTT_DAMAGE))
            {
                this.mob.setLastHurtMob(target);
            }
        }
    }

    /** True if its target is close enough to bump: their hitboxes within {@link RageTuning#HEADBUTT_REACH} sideways, overlapping in height. */
    private boolean inReach(LivingEntity target)
    {
        double reach = (this.mob.getBbWidth() + target.getBbWidth()) * 0.5D + RageTuning.HEADBUTT_REACH;
        double dx = target.getX() - this.mob.getX();
        double dz = target.getZ() - this.mob.getZ();
        boolean overlapsInHeight = target.getY() < this.mob.getY() + this.mob.getBbHeight() + RageTuning.HEADBUTT_REACH
                && target.getY() + target.getBbHeight() > this.mob.getY() - RageTuning.HEADBUTT_REACH;
        return dx * dx + dz * dz <= reach * reach && overlapsInHeight;
    }
}
