package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

/**
 * Places the Colossus's temporary lava pools ({@link CinderLavaBlock}) and fire trail ({@link CinderFireBlock}).
 * Both only ever go into empty air, or (if the mob griefing game rule is on) in place of soft plants: grass, ferns,
 * flowers, snow layers. They never replace anything solid, never touch a block with a block entity (chests,
 * furnaces, signs...), never go into water or any other liquid, and need solid ground under them.
 */
public final class LavaPools
{
    /** A pool spreads from its middle over ground that steps up or down at most this many blocks between neighbours. */
    private static final int MAX_STEP = 1;
    /** Its middle may sit this far above or below where the blob landed (it settles on the ground there). */
    private static final int SETTLE = 2;
    /** Blocks within this share of the radius are always lava; further out the edge is ragged. */
    private static final double SOLID_SHARE = 0.75D;
    /** Edge blocks (in the ragged band) are lava this often. */
    private static final float EDGE_CHANCE = 0.55F;

    private LavaPools() {}

    /**
     * Makes a roughly circular pool of temporary lava on the ground around {@code landing}, never more than
     * {@link ColossusTuning#POOL_RADIUS} blocks from its middle in any direction. It spreads outwards over the ground
     * (stepping up or down a block at a time), so it follows a slope but never ends up on the other side of a wall.
     * Its blocks last {@code lifeSteps} cooling steps ({@link CinderLavaBlock#STEP_TICKS} ticks each) plus one, the
     * ones at the edge one step less, so the pool shrinks in from its edge as it cools.
     *
     * @param plants   true if soft plants (grass, flowers, snow layers) may be replaced too (the mob griefing rule)
     * @return how many lava blocks were placed
     */
    public static int makePool(ServerLevel level, BlockPos landing, int lifeSteps, RandomSource random, boolean plants)
    {
        BlockPos centre = surfaceNear(level, landing, SETTLE, plants);
        if (centre == null)
        {
            return 0;
        }
        double radius = ColossusTuning.POOL_RADIUS;
        int r = (int) Math.floor(radius);
        int life = Math.max(0, Math.min(CinderLavaBlock.MAX_LIFE, lifeSteps));
        Map<Long, BlockPos> visited = new HashMap<>();
        Deque<BlockPos> open = new ArrayDeque<>();
        open.add(centre);
        visited.put(column(centre), centre);
        int placed = 0;
        while (!open.isEmpty())
        {
            BlockPos at = open.poll();
            double dist = Math.sqrt(sq(at.getX() - centre.getX()) + sq(at.getZ() - centre.getZ()));
            boolean edge = dist > radius * SOLID_SHARE;
            if (!edge || random.nextFloat() < EDGE_CHANCE)
            {
                int blockLife = edge ? Math.max(0, life - 1) : life;
                level.setBlock(at, ModBlocks.CINDER_LAVA.get().defaultBlockState().setValue(CinderLavaBlock.LIFE, blockLife),
                        Block.UPDATE_ALL);
                placed++;
            }
            for (Direction side : Direction.Plane.HORIZONTAL)
            {
                BlockPos next = at.relative(side);
                if (Math.abs(next.getX() - centre.getX()) > r || Math.abs(next.getZ() - centre.getZ()) > r
                        || sq(next.getX() - centre.getX()) + sq(next.getZ() - centre.getZ()) > radius * radius
                        || visited.containsKey(column(next)))
                {
                    continue;
                }
                BlockPos ground = surfaceNear(level, next, MAX_STEP, plants);
                if (ground != null)
                {
                    visited.put(column(ground), ground);
                    open.add(ground);
                }
                else
                {
                    visited.put(column(next), next);
                }
            }
        }
        return placed;
    }

    /**
     * Lights one block of the fire trail at {@code pos} (or a block above or below it, on the ground), if it's free.
     *
     * @return true if fire was placed
     */
    public static boolean placeFire(ServerLevel level, BlockPos pos, boolean plants)
    {
        BlockPos spot = surfaceNear(level, pos, 1, plants);
        if (spot == null)
        {
            return false;
        }
        level.setBlock(spot, ModBlocks.CINDER_FIRE.get().defaultBlockState(), Block.UPDATE_ALL);
        return true;
    }

    /**
     * The spot (searching from {@code reach} blocks above {@code pos} down to {@code reach} below it) where a lava or
     * fire block may go: free (see {@link #canTake}) with solid ground under it. Null if there's none.
     */
    public static BlockPos surfaceNear(ServerLevel level, BlockPos pos, int reach, boolean plants)
    {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = reach; dy >= -reach; dy--)
        {
            at.set(pos.getX(), pos.getY() + dy, pos.getZ());
            if (!level.isInWorldBounds(at) || !canTake(level, at, plants))
            {
                continue;
            }
            BlockPos below = at.below();
            if (level.getBlockState(below).isFaceSturdy(level, below, Direction.UP))
            {
                return at.immutable();
            }
        }
        return null;
    }

    /**
     * True if a lava or fire block may replace what's at {@code pos}: empty air, or (with {@code plants}) a soft
     * plant or a thin snow layer. Never a liquid, never a block with a block entity, never anything else.
     */
    public static boolean canTake(ServerLevel level, BlockPos pos, boolean plants)
    {
        BlockState state = level.getBlockState(pos);
        if (!state.getFluidState().isEmpty() || state.hasBlockEntity())
        {
            return false;
        }
        if (state.isAir())
        {
            return true;
        }
        if (!plants)
        {
            return false;
        }
        boolean thinSnow = state.getBlock() instanceof SnowLayerBlock && state.getValue(SnowLayerBlock.LAYERS) <= 2;
        boolean softPlant = (state.canBeReplaced() && !state.is(BlockTags.FIRE) && !state.is(ModBlocks.CINDER_LAVA.get()))
                || state.is(BlockTags.SMALL_FLOWERS);
        return thinSnow || softPlant;
    }

    private static long column(BlockPos pos)
    {
        return BlockPos.asLong(pos.getX(), 0, pos.getZ());
    }

    private static double sq(double v)
    {
        return v * v;
    }
}
