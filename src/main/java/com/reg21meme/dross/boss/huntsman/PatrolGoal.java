package com.reg21meme.dross.boss.huntsman;

import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.EnumSet;
import java.util.List;

/**
 * Calm, the Alpha Moose of a Huntsman's village rides its patrol, him in the saddle: a loop just outside the village's
 * lights ({@link com.reg21meme.dross.boss.huntsman.village.HuntsmanVillages#patrolLoop}), point after point, round and
 * round (the user, October 23, 2026: "the Huntsman should be patrolling the village ... when he comes into view he
 * should already be moving around"). A point it can't get to (no way there, or no headway for a while) is skipped.
 * The fight starts when he spots a foe, as anywhere; afterwards it takes the loop up again from its nearest point.
 */
final class PatrolGoal extends Goal
{
    /** A point counts as reached this near (blocks, over the ground). */
    private static final double REACHED = 6.0D;
    /** Its way to the point is asked for again this often (ticks), and a point it has made no headway towards for this
     * long is skipped. */
    private static final int REPATH_TICKS = 40;
    private static final int STUCK_TICKS = 160;
    /** Headway: closer by this much in {@link #STUCK_TICKS}. */
    private static final double HEADWAY = 3.0D;
    /** The ground's top this far above or below it (a roof, a cliff's foot) isn't where it rides: it keeps its height. */
    private static final int HEIGHT_SLACK = 12;

    private final AlphaMoose moose;
    private int repath;
    private int since;
    private double bestDistance;

    PatrolGoal(AlphaMoose moose)
    {
        this.moose = moose;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE));
    }

    @Override
    public boolean canUse()
    {
        return !this.moose.isShowcase() && this.moose.stage() == HuntStage.CALM && !this.moose.patrol().isEmpty()
                && this.moose.isAlive();
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.canUse();
    }

    @Override
    public void start()
    {
        List<BlockPos> loop = this.moose.patrol();
        int next = this.moose.patrolNext();
        if (next < 0 || next >= loop.size() || this.flat(loop.get(next)) > HuntsmanTuning.PATROL_REJOIN)
        {
            this.moose.setPatrolNext(this.nearest(loop));       // (after a fight: the loop from its nearest point)
        }
        this.aim();
    }

    @Override
    public void stop()
    {
        this.moose.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        List<BlockPos> loop = this.moose.patrol();
        BlockPos point = loop.get(this.moose.patrolNext());
        double d = this.flat(point);
        if (d < REACHED)
        {
            this.moose.setPatrolNext((this.moose.patrolNext() + 1) % loop.size());
            this.aim();
            return;
        }
        if (d < this.bestDistance - HEADWAY)
        {
            this.bestDistance = d;
            this.since = 0;
        }
        if (++this.since > STUCK_TICKS)
        {
            this.moose.setPatrolNext((this.moose.patrolNext() + 1) % loop.size());     // (no headway: the next one)
            this.aim();
            return;
        }
        if (--this.repath <= 0 || this.moose.getNavigation().isDone())
        {
            this.walkTo(point);
        }
    }

    /** Sets off for the point it's on. */
    private void aim()
    {
        BlockPos point = this.moose.patrol().get(this.moose.patrolNext());
        this.since = 0;
        this.bestDistance = this.flat(point);
        this.walkTo(point);
    }

    private void walkTo(BlockPos point)
    {
        this.repath = REPATH_TICKS;
        int y = this.moose.level().getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, point.getX(), point.getZ());
        if (Math.abs(y - this.moose.getBlockY()) > HEIGHT_SLACK)
        {
            y = this.moose.getBlockY();
        }
        this.moose.getNavigation().moveTo(point.getX() + 0.5D, y, point.getZ() + 0.5D, HuntsmanTuning.PATROL_SPEED);
    }

    private double flat(BlockPos point)
    {
        Vec3 p = this.moose.position();
        double dx = point.getX() + 0.5D - p.x;
        double dz = point.getZ() + 0.5D - p.z;
        return Math.sqrt(dx * dx + dz * dz);
    }

    private int nearest(List<BlockPos> loop)
    {
        int best = 0;
        for (int i = 1; i < loop.size(); i++)
        {
            if (this.flat(loop.get(i)) < this.flat(loop.get(best)))
            {
                best = i;
            }
        }
        return best;
    }
}
