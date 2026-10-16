package com.reg21meme.dross.boss.rage;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;

import javax.annotation.Nullable;
import java.util.EnumSet;

/**
 * A raged mob's targeting (added by {@link RageAi} while it's raged, at {@link RageTuning#GOAL_PRIORITY}, ahead of all
 * its own target goals): it goes for the nearest raged enemy within {@link RageTuning#RAGE_RANGE} blocks and keeps it
 * until it dies, calms down or gets out of range, then picks the nearest one left. While this goal runs the mob's own
 * targeting (players, whoever hurt it...) waits; with no enemy left it doesn't run, and the mob targets as it normally
 * would.
 * <p>
 * Brain-based fighters (piglins, hoglins, wardens...) are told through their brain's attack target too
 * ({@link RageAi}); a mob with no attack of its own goes for it with {@link RageHeadbuttGoal}.
 */
public class RageTargetGoal extends Goal
{
    private final Mob mob;
    @Nullable
    private LivingEntity target;
    /** The mob's tick count at which it may look for a target again. */
    private int nextSearch;
    /** A target the game refused (another rule cancelled it), skipped until {@link #refusedUntil}. */
    @Nullable
    private LivingEntity refused;
    private int refusedUntil;

    public RageTargetGoal(Mob mob)
    {
        this.mob = mob;
        this.setFlags(EnumSet.of(Flag.TARGET));
    }

    /** The raged enemy it's going for now (null: none). */
    @Nullable
    public LivingEntity target()
    {
        return this.target;
    }

    @Override
    public boolean canUse()
    {
        if (!Rage.isRaged(this.mob) || !this.mob.isAlive() || this.mob.tickCount < this.nextSearch)
        {
            return false;
        }
        this.nextSearch = this.mob.tickCount + RageTuning.TARGET_SEARCH_TICKS;
        this.target = Rage.nearestTarget(this.mob, this.isRefused(this.refused) ? this.refused : null);
        return this.target != null;
    }

    @Override
    public boolean canContinueToUse()
    {
        return Rage.isRaged(this.mob) && this.mob.isAlive() && Rage.isTargetable(this.mob, this.target)
                && !this.isRefused(this.target);
    }

    @Override
    public void start()
    {
        RageAi.boostFollowRange(this.mob);
        this.holdTarget();
    }

    @Override
    public void tick()
    {
        this.holdTarget();
    }

    @Override
    public boolean requiresUpdateEveryTick()
    {
        return true;
    }

    @Override
    public void stop()
    {
        RageAi.restoreFollowRange(this.mob);
        if (this.target != null && this.mob.getTarget() == this.target)
        {
            this.mob.setTarget(null);
        }
        if (this.target != null)
        {
            RageAi.forgetBrainTarget(this.mob, this.target);
        }
        this.target = null;
        this.nextSearch = 0; // its target died (or the like): it looks for the next one straight away
    }

    /** True if the game refused this target a moment ago (it's skipped for {@link RageTuning#REFUSED_TARGET_TICKS}). */
    private boolean isRefused(@Nullable LivingEntity entity)
    {
        return entity != null && entity == this.refused && this.mob.tickCount < this.refusedUntil;
    }

    /**
     * Keeps the mob on its target (something else may have cleared it: a spider in daylight, a swap by another goal).
     * If the game refuses it (another rule cancels the change), that target is skipped for a while.
     */
    private void holdTarget()
    {
        if (this.target == null)
        {
            return;
        }
        if (this.mob.getTarget() != this.target)
        {
            this.mob.setTarget(this.target);
            if (this.mob.getTarget() != this.target && !RageAi.fightsWithBrain(this.mob))
            {
                this.refused = this.target;
                this.refusedUntil = this.mob.tickCount + RageTuning.REFUSED_TARGET_TICKS;
            }
        }
    }
}
