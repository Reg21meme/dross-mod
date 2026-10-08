package com.reg21meme.dross.enchant;

import java.util.EnumSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;

/**
 * When a risen undead has nothing to fight, it walks after its owner, and teleports next to the owner
 * if it falls too far behind (like a tamed wolf). Riders steer their mount, and the whole jockey teleports.
 * Distances and speed are the constants in {@link RisenUndead}.
 */
public class RisenFollowOwnerGoal extends Goal
{
    /** How often (ticks) the path to the owner is recalculated. */
    private static final int RECALC_PATH_TICKS = 10;
    private static final int TELEPORT_ATTEMPTS = 10;

    private final PathfinderMob mob;
    private Player owner;
    private int timeToRecalcPath;

    public RisenFollowOwnerGoal(PathfinderMob mob)
    {
        this.mob = mob;
        this.setFlags(EnumSet.of(Goal.Flag.MOVE, Goal.Flag.LOOK));
    }

    @Override
    public boolean canUse()
    {
        Player found = RisenUndead.getOwner(this.mob);
        if (found == null || found.isSpectator() || this.isBusy())
        {
            return false;
        }
        if (this.mob.distanceToSqr(found) < sq(RisenUndead.FOLLOW_START_DISTANCE))
        {
            return false;
        }
        this.owner = found;
        return true;
    }

    @Override
    public boolean canContinueToUse()
    {
        return this.owner != null
                && this.owner.isAlive()
                && !this.mob.getNavigation().isDone()
                && !this.isBusy()
                && this.mob.distanceToSqr(this.owner) > sq(RisenUndead.FOLLOW_STOP_DISTANCE);
    }

    /** Fighting, leashed, or a passenger that isn't steering. */
    private boolean isBusy()
    {
        LivingEntity target = this.mob.getTarget();
        if (target != null && target.isAlive())
        {
            return true;
        }
        if (this.mob.isLeashed())
        {
            return true;
        }
        Entity vehicle = this.mob.getVehicle();
        return vehicle != null && vehicle.getControllingPassenger() != this.mob;
    }

    @Override
    public void start()
    {
        this.timeToRecalcPath = 0;
    }

    @Override
    public void stop()
    {
        this.owner = null;
        this.mob.getNavigation().stop();
    }

    @Override
    public void tick()
    {
        this.mob.getLookControl().setLookAt(this.owner, 10.0F, (float) this.mob.getMaxHeadXRot());
        if (--this.timeToRecalcPath <= 0)
        {
            this.timeToRecalcPath = this.adjustedTickDelay(RECALC_PATH_TICKS);
            if (this.mob.distanceToSqr(this.owner) >= sq(RisenUndead.TELEPORT_DISTANCE))
            {
                this.teleportToOwner();
            }
            else
            {
                // For a rider this is the mount's navigation, so the rider steers the mount.
                this.mob.getNavigation().moveTo(this.owner, RisenUndead.FOLLOW_SPEED);
            }
        }
    }

    /** Same spot rules as vanilla's tamed-pet teleport: 2-3 blocks from the owner, on solid, non-leaf ground. */
    private void teleportToOwner()
    {
        Entity body = this.mob.getRootVehicle(); // the mount for a jockey, otherwise the mob itself
        BlockPos center = this.owner.blockPosition();
        for (int i = 0; i < TELEPORT_ATTEMPTS; i++)
        {
            int dx = this.randomBetween(-3, 3);
            int dy = this.randomBetween(-1, 1);
            int dz = this.randomBetween(-3, 3);
            if (Math.abs(dx) < 2 && Math.abs(dz) < 2)
            {
                continue;
            }
            BlockPos pos = center.offset(dx, dy, dz);
            if (canStandAt(body, pos))
            {
                body.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, body.getYRot(), body.getXRot());
                this.mob.getNavigation().stop();
                return;
            }
        }
    }

    /** True if {@code body} can stand at {@code pos}: walkable, not on leaves, and nothing in the way. */
    static boolean canStandAt(Entity body, BlockPos pos)
    {
        if (WalkNodeEvaluator.getBlockPathTypeStatic(body.level(), pos.mutable()) != BlockPathTypes.WALKABLE)
        {
            return false;
        }
        if (body.level().getBlockState(pos.below()).getBlock() instanceof LeavesBlock)
        {
            return false;
        }
        return body.level().noCollision(body, body.getType().getAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D));
    }

    private int randomBetween(int min, int max)
    {
        return this.mob.getRandom().nextInt(max - min + 1) + min;
    }

    private static double sq(double d)
    {
        return d * d;
    }
}
