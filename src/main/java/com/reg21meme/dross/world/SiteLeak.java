package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import org.slf4j.Logger;

/**
 * "The Dross is leaking out": done <b>once</b>, right after the castle (or placeholder shrine) is placed.
 * Around the frame:
 * <ul>
 *   <li>flowers, grass and ferns die: they become dead bushes or disappear;</li>
 *   <li>some grass blocks become coarse dirt;</li>
 *   <li>some stone bricks become cracked stone bricks.</li>
 * </ul>
 * Grass and bricks are hit harder near the frame than at the edge of the radius. The frame itself is never touched.
 * (The drifting blue particles and the ambient sound come from the frame blocks: that's the portal area.)
 */
final class SiteLeak
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How far (in blocks, measured flat from the middle of the opening) the leak reaches. */
    static final int LEAK_RADIUS = 16;
    /** How far below and above the bottom of the opening the leak reaches. */
    private static final int LEAK_BELOW = 6;
    private static final int LEAK_ABOVE = 24;
    /** A dying flower or grass plant becomes a dead bush with this chance (if a dead bush can stand there); otherwise it's removed. */
    private static final float DEAD_BUSH_CHANCE = 0.35F;
    /** A grass block becomes coarse dirt with this chance, right next to the frame. */
    private static final float COARSE_DIRT_CHANCE = 0.45F;
    /** A stone brick block becomes cracked with this chance, right next to the frame. */
    private static final float CRACK_CHANCE = 0.7F;
    /**
     * How much weaker the coarse dirt and crack chances are at the edge of the radius than at the frame
     * (0 = the same everywhere, 1 = nothing at the edge).
     */
    private static final float EDGE_FALLOFF = 0.5F;

    /** Applies the leak around the frame, once, and logs what changed. */
    static void apply(ServerLevel level, SiteFrame frame)
    {
        RandomSource random = level.getRandom();
        BlockPos center = frame.openingCenter();
        BlockState frameBlock = ModBlocks.DROSS_PORTAL_FRAME.get().defaultBlockState();
        int minY = Math.max(level.getMinBuildHeight(), center.getY() - LEAK_BELOW);
        int maxY = Math.min(level.getMaxBuildHeight() - 1, center.getY() + LEAK_ABOVE);

        int deadBushes = 0;
        int plantsRemoved = 0;
        int coarseDirt = 0;
        int cracked = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -LEAK_RADIUS; dx <= LEAK_RADIUS; dx++)
        {
            for (int dz = -LEAK_RADIUS; dz <= LEAK_RADIUS; dz++)
            {
                double distance = Math.sqrt(dx * dx + dz * dz);
                if (distance > LEAK_RADIUS)
                {
                    continue;
                }
                float strength = 1.0F - EDGE_FALLOFF * (float) (distance / LEAK_RADIUS);
                for (int y = minY; y <= maxY; y++)
                {
                    pos.set(center.getX() + dx, y, center.getZ() + dz);
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || state.is(frameBlock.getBlock()))
                    {
                        continue; // never touch the frame
                    }
                    if (isDyingPlant(state))
                    {
                        if (killPlant(level, random, pos, state))
                        {
                            deadBushes++;
                        }
                        else
                        {
                            plantsRemoved++;
                        }
                    }
                    else if (state.is(Blocks.GRASS_BLOCK) && random.nextFloat() < COARSE_DIRT_CHANCE * strength)
                    {
                        level.setBlock(pos, Blocks.COARSE_DIRT.defaultBlockState(), Block.UPDATE_ALL);
                        coarseDirt++;
                    }
                    else if (state.is(Blocks.STONE_BRICKS) && random.nextFloat() < CRACK_CHANCE * strength)
                    {
                        level.setBlock(pos, Blocks.CRACKED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                        cracked++;
                    }
                }
            }
        }
        LOGGER.info("Dross portal site: leak effects applied within {} blocks of {}: {} plants turned into dead bushes, "
                + "{} plants removed, {} grass blocks turned into coarse dirt, {} stone bricks cracked",
                LEAK_RADIUS, center.toShortString(), deadBushes, plantsRemoved, coarseDirt, cracked);
    }

    /** Flowers (one and two blocks tall), grass, tall grass, ferns and large ferns. */
    private static boolean isDyingPlant(BlockState state)
    {
        return state.is(BlockTags.SMALL_FLOWERS)
                || state.is(BlockTags.TALL_FLOWERS)
                || state.is(Blocks.GRASS)
                || state.is(Blocks.TALL_GRASS)
                || state.is(Blocks.FERN)
                || state.is(Blocks.LARGE_FERN);
    }

    /**
     * Replaces the plant (both halves of a tall one) with a dead bush or air. Nothing drops.
     *
     * @return true if it became a dead bush
     */
    private static boolean killPlant(ServerLevel level, RandomSource random, BlockPos pos, BlockState state)
    {
        BlockPos bottom = pos.immutable();
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF))
        {
            boolean upper = state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.UPPER;
            bottom = upper ? pos.below() : pos.immutable();
            BlockPos top = bottom.above();
            if (isDyingPlant(level.getBlockState(top)))
            {
                // Remove the top half quietly first, so the bottom half doesn't pop off as an item.
                level.setBlock(top, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
            }
            if (!isDyingPlant(level.getBlockState(bottom)))
            {
                return false; // only the top half was here
            }
        }

        BlockState deadBush = Blocks.DEAD_BUSH.defaultBlockState();
        if (random.nextFloat() < DEAD_BUSH_CHANCE && deadBush.canSurvive(level, bottom))
        {
            level.setBlock(bottom, deadBush, Block.UPDATE_ALL);
            return true;
        }
        level.setBlock(bottom, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        return false;
    }

    private SiteLeak() {}
}
