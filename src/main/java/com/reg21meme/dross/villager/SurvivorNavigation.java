package com.reg21meme.dross.villager;

import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.BlockPathTypes;
import net.minecraft.world.level.pathfinder.PathFinder;
import net.minecraft.world.level.pathfinder.WalkNodeEvaluator;
import net.minecraft.world.entity.ai.navigation.GroundPathNavigation;

/**
 * A survivor's path finding: vanilla walking, except that the cells it keeps out of ({@link Survivor#keepsOutOf}: any
 * portal, the hub exit portal's steps and the cells round the arrival spot) count as walls, so its paths go round them.
 */
final class SurvivorNavigation extends GroundPathNavigation
{
    SurvivorNavigation(Survivor survivor, Level level)
    {
        super(survivor, level);
    }

    @Override
    protected PathFinder createPathFinder(int maxVisitedNodes)
    {
        // Called from the constructor of PathNavigation: only set the inherited field here.
        this.nodeEvaluator = new KeepClearNodeEvaluator();
        this.nodeEvaluator.setCanPassDoors(true);
        return new PathFinder(this.nodeEvaluator, maxVisitedNodes);
    }

    /** Vanilla's walking node evaluator, with the survivor's kept-clear cells blocked. */
    private static final class KeepClearNodeEvaluator extends WalkNodeEvaluator
    {
        @Override
        public BlockPathTypes getBlockPathType(BlockGetter level, int x, int y, int z)
        {
            if (this.mob instanceof Survivor survivor && survivor.keepsOutOf(level, x, y, z))
            {
                return BlockPathTypes.BLOCKED;
            }
            return super.getBlockPathType(level, x, y, z);
        }
    }
}
