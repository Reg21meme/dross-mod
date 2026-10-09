package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.slf4j.Logger;

/**
 * The placeholder castle: a small ruined shrine around an unlit 4x5 Dross frame along X (vanilla blocks only).
 * Used until the user saves a real castle template ({@link PortalCastle}).
 *
 * <p>Seen from above (Z grows downwards; P = broken pillar, w = broken wall, F = frame, s = step, . = floor):
 * <pre>
 *   P w w w w w w w w P    back wall (z = frame z - 4)
 *   w . . . . . . . . w
 *   w . . . . . . . . w
 *   w . . . . s s . . w
 *   w . . F F F F . . w    the frame row (z = frame z), opening in the middle two columns
 *   w . . . . s s . . w    steps up into the opening, from the front and from the back
 *   w . . . . . . . . w
 *   w . . . . . . . . w    /dross site puts the player here, facing the frame
 *   P w w . . . . w w P    front (z = frame z + 4): open in the middle
 * </pre>
 * Walls and pillars have random, broken heights. The layout depends only on the world seed.
 */
final class PortalShrine
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Floor reaches this many blocks past each end of the frame (along X). */
    private static final int SIDE_MARGIN = 3;
    /** Floor rows behind (-Z) and in front (+Z) of the frame. */
    private static final int BACK_MARGIN = 4;
    private static final int FRONT_MARGIN = 4;
    /** Corner pillar heights (back-left, back-right, front-left, front-right): broken off at different heights. */
    private static final int[] PILLAR_HEIGHTS = {5, 3, 2, 4};
    /** Highest a broken wall column gets (in blocks, before a possible slab on top). */
    private static final int WALL_MAX_HEIGHT = 2;
    /** Chance that a wall column's top gets a slab on it. */
    private static final float WALL_SLAB_CHANCE = 0.35F;
    /** Chance that a floor block is missing (the ground shows through); higher at the floor's edge. */
    private static final float FLOOR_GAP_CHANCE = 0.12F;
    private static final float FLOOR_EDGE_GAP_CHANCE = 0.45F;
    /** How many pieces of rubble get scattered around the floor (fewer if a spot is taken). */
    private static final int RUBBLE_COUNT = 8;
    /** Mixed into the world seed so the shrine's random look is its own. */
    private static final long SEED_SALT = 0x5D2055L;

    /**
     * Builds the shrine with its frame's bottom corner (lowest X) at {@code corner}.
     *
     * @return the frame (always 4x5 along X)
     */
    static SiteFrame build(ServerLevel level, BlockPos corner)
    {
        RandomSource random = RandomSource.create(level.getSeed() ^ SEED_SALT);
        int fx = corner.getX();
        int g = corner.getY(); // the floor's top: players stand at this Y, the frame's bottom row is at this Y
        int fz = corner.getZ();
        int frameMaxX = fx + PortalSite.FRAME_WIDTH - 1;

        int minX = fx - SIDE_MARGIN;
        int maxX = frameMaxX + SIDE_MARGIN;
        int minZ = fz - BACK_MARGIN;
        int maxZ = fz + FRONT_MARGIN;

        PortalSiteBuilder.prepareGround(level, minX, maxX, minZ, maxZ, g - 1, g + PortalSiteBuilder.CLEAR_HEIGHT - 1,
                Blocks.STONE_BRICKS.defaultBlockState());
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Floor: mixed stone bricks, with gaps where the ground shows through (more at the edges).
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                boolean edge = x == minX || x == maxX || z == minZ || z == maxZ;
                pos.set(x, g - 1, z);
                boolean gap = random.nextFloat() < (edge ? FLOOR_EDGE_GAP_CHANCE : FLOOR_GAP_CHANCE);
                if (!gap || !PortalSiteBuilder.isSolidGround(level, pos))
                {
                    level.setBlock(pos, brick(random), Block.UPDATE_ALL);
                }
            }
        }
        // Chiseled stone bricks under the frame.
        for (int x = fx; x <= frameMaxX; x++)
        {
            level.setBlock(pos.set(x, g - 1, fz), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
        }

        // Broken pillars in the four corners.
        int[][] pillarSpots = {{minX, minZ}, {maxX, minZ}, {minX, maxZ}, {maxX, maxZ}};
        for (int i = 0; i < pillarSpots.length; i++)
        {
            column(level, random, pos, pillarSpots[i][0], g, pillarSpots[i][1], PILLAR_HEIGHTS[i], i % 2 == 0);
        }

        // Partial walls: the back, both sides, and the front ends (the middle of the front stays open).
        for (int x = minX + 1; x <= maxX - 1; x++)
        {
            wall(level, random, pos, x, g, minZ);
            if (x < fx || x > frameMaxX)
            {
                wall(level, random, pos, x, g, maxZ);
            }
        }
        for (int z = minZ + 1; z <= maxZ - 1; z++)
        {
            wall(level, random, pos, minX, g, z);
            wall(level, random, pos, maxX, g, z);
        }

        // Rubble on the floor, away from the frame and the way in.
        for (int i = 0; i < RUBBLE_COUNT; i++)
        {
            int x = minX + 1 + random.nextInt(maxX - minX - 1);
            int z = minZ + 1 + random.nextInt(maxZ - minZ - 1);
            boolean inTheWay = x >= fx - 1 && x <= frameMaxX + 1 && z >= fz - 2 && z <= fz + 3;
            pos.set(x, g, z);
            if (!inTheWay && level.getBlockState(pos).isAir())
            {
                level.setBlock(pos, rubble(random), Block.UPDATE_ALL);
            }
        }

        // The frame: 4 wide x 5 tall along X, corners included, opening left empty (unlit).
        BlockState frameBlock = ModBlocks.DROSS_PORTAL_FRAME.get().defaultBlockState();
        for (int i = 0; i < PortalSite.FRAME_WIDTH; i++)
        {
            for (int j = 0; j < PortalSite.FRAME_HEIGHT; j++)
            {
                boolean edge = i == 0 || i == PortalSite.FRAME_WIDTH - 1 || j == 0 || j == PortalSite.FRAME_HEIGHT - 1;
                if (edge)
                {
                    level.setBlock(pos.set(fx + i, g + j, fz), frameBlock, Block.UPDATE_ALL);
                }
            }
        }

        // Steps up into the opening (its bottom is one block above the floor), from the front and from the back.
        for (int x = fx + 1; x <= frameMaxX - 1; x++)
        {
            level.setBlock(pos.set(x, g, fz + 1), stairs(Blocks.STONE_BRICK_STAIRS, Direction.NORTH), Block.UPDATE_ALL);
            level.setBlock(pos.set(x, g, fz - 1), stairs(Blocks.MOSSY_STONE_BRICK_STAIRS, Direction.SOUTH), Block.UPDATE_ALL);
        }

        SiteFrame frame = SiteFrame.standard(corner.immutable(), PortalSite.FRAME_AXIS);
        if (PortalCastle.isValidInWorld(level, frame))
        {
            LOGGER.info("Dross portal site: built the placeholder ruined shrine; frame corner {} axis {} (opening {}x{})",
                    frame.corner().toShortString(), frame.axis(), frame.openingWidth(), frame.openingHeight());
        }
        else
        {
            LOGGER.error("Dross portal site: built the placeholder ruined shrine, but its frame at {} doesn't pass the portal frame check. "
                    + "The Rift Key may not light it.", frame.corner().toShortString());
        }
        return frame;
    }

    /** A broken wall column: 0 to WALL_MAX_HEIGHT blocks, sometimes with a slab on top. */
    private static void wall(ServerLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int g, int z)
    {
        column(level, random, pos, x, g, z, random.nextInt(WALL_MAX_HEIGHT + 1), random.nextFloat() < WALL_SLAB_CHANCE);
    }

    private static void column(ServerLevel level, RandomSource random, BlockPos.MutableBlockPos pos, int x, int g, int z, int height, boolean slabOnTop)
    {
        for (int y = 0; y < height; y++)
        {
            level.setBlock(pos.set(x, g + y, z), brick(random), Block.UPDATE_ALL);
        }
        if (slabOnTop)
        {
            Block slab = random.nextBoolean() ? Blocks.STONE_BRICK_SLAB : Blocks.MOSSY_STONE_BRICK_SLAB;
            level.setBlock(pos.set(x, g + height, z), slab.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM), Block.UPDATE_ALL);
        }
    }

    /** Mostly plain stone bricks, with mossy and cracked ones mixed in. */
    private static BlockState brick(RandomSource random)
    {
        float roll = random.nextFloat();
        if (roll < 0.5F)
        {
            return Blocks.STONE_BRICKS.defaultBlockState();
        }
        if (roll < 0.8F)
        {
            return Blocks.MOSSY_STONE_BRICKS.defaultBlockState();
        }
        return Blocks.CRACKED_STONE_BRICKS.defaultBlockState();
    }

    private static BlockState rubble(RandomSource random)
    {
        return switch (random.nextInt(5))
        {
            case 0 -> Blocks.COBBLESTONE.defaultBlockState();
            case 1 -> Blocks.MOSSY_COBBLESTONE.defaultBlockState();
            case 2 -> Blocks.STONE_BRICK_SLAB.defaultBlockState();
            case 3 -> Blocks.MOSSY_STONE_BRICK_SLAB.defaultBlockState();
            default -> stairs(Blocks.STONE_BRICK_STAIRS, Direction.Plane.HORIZONTAL.getRandomDirection(random));
        };
    }

    /** Stairs you walk up while moving towards {@code facing}. */
    private static BlockState stairs(Block block, Direction facing)
    {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    private PortalShrine() {}
}
