package com.reg21meme.dross.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * How a boss chases the player it fights. Minecraft's pathfinder works out a route every few ticks and the mob walks
 * from one waypoint to the next, so while it walks its body faces the next waypoint, not its target (which has moved on
 * since): the user saw the bosses walking with their side to them. So when the way to the target is clear, a boss runs
 * straight at it instead, turning towards it every tick ({@link #chase}); only when it isn't (a wall, a drop, water, a
 * steep climb, out of sight) does it follow the pathfinder. Every number is a labelled constant here.
 */
public final class BossChase
{
    /** The way ahead is checked every this many blocks... */
    private static final double STEP = 1.0D;
    /** ...up to this far (it's checked again every tick, so further on is seen as it comes closer). */
    private static final double LOOKAHEAD = 8.0D;
    /** A target more than this many blocks above or below it: the pathfinder finds the way up or down. */
    private static final double MAX_RISE = 1.5D;

    private BossChase() {}

    /**
     * Moves a boss one tick towards its target: straight at it when {@link #straightWay} says the way is clear (its
     * move control turns it towards the target every tick), otherwise along a path from the pathfinder, worked out
     * again every {@code repathTicks} ticks.
     *
     * @param repath the boss's countdown to its next path (its fighting AI keeps it between ticks)
     * @return the countdown for the next tick
     */
    public static int chase(Mob boss, LivingEntity target, double speed, int repath, int repathTicks)
    {
        if (straightWay(boss, target))
        {
            boss.getNavigation().stop();
            boss.getMoveControl().setWantedPosition(target.getX(), target.getY(), target.getZ(), speed);
            return 0; // the moment the way isn't clear any more, it works out a path
        }
        if (repath - 1 <= 0)
        {
            boss.getNavigation().moveTo(target, speed);
            return repathTicks;
        }
        return repath - 1;
    }

    /**
     * True if a boss can run straight at its target: it can see it, it's about level with it ({@link #MAX_RISE}), and
     * along the line to it (every {@link #STEP} blocks, up to {@link #LOOKAHEAD}) its body fits (a step it can walk up
     * is fine), there's ground to stand on (a step up or down is fine, a drop isn't), no liquid, and nothing the boss's
     * own pathfinding would avoid or go round (fire, cactus, fences... unless it doesn't mind them, like the Cinder
     * Colossus and fire).
     */
    public static boolean straightWay(Mob boss, LivingEntity target)
    {
        if (!boss.getSensing().hasLineOfSight(target) || Math.abs(target.getY() - boss.getY()) > MAX_RISE)
        {
            return false;
        }
        double dx = target.getX() - boss.getX();
        double dz = target.getZ() - boss.getZ();
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < STEP)
        {
            return true;
        }
        Vec3 dir = new Vec3(dx / distance, 0.0D, dz / distance);
        Level level = boss.level();
        AABB body = boss.getBoundingBox().move(0.0D, boss.maxUpStep(), 0.0D);
        double reach = Math.min(distance, LOOKAHEAD);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (double d = STEP; d <= reach + 1.0E-6D; d += STEP)
        {
            AABB at = body.move(dir.x * d, 0.0D, dir.z * d);
            if (!level.noCollision(boss, at) || level.containsAnyLiquid(at.expandTowards(0.0D, -boss.maxUpStep(), 0.0D)))
            {
                return false;
            }
            if (!standable(boss, pos, boss.getX() + dir.x * d, boss.getY(), boss.getZ() + dir.z * d))
            {
                return false;
            }
        }
        return true;
    }

    /** True if the boss can stand at (x, z) at its height, a step above or a step below it. */
    private static boolean standable(Mob boss, BlockPos.MutableBlockPos pos, double x, double y, double z)
    {
        int bx = Mth.floor(x);
        int by = Mth.floor(y + 1.0E-3D);
        int bz = Mth.floor(z);
        for (int dy : new int[] {0, 1, -1})
        {
            pos.set(bx, by + dy, bz);
            BlockPathTypes type = WalkNodeEvaluator.getBlockPathTypeStatic(boss.level(), pos);
            if (type != BlockPathTypes.OPEN && type != BlockPathTypes.BLOCKED && boss.getPathfindingMalus(type) == 0.0F)
            {
                return true;
            }
        }
        return false;
    }
}
