package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.slf4j.Logger;

/**
 * Builds the unlit Dross portal frame at the portal site, once per world.
 *
 * <p>What gets built (seen from above, Z grows downwards, F = frame, . = cleared space on a solid floor):
 * <pre>
 *   . . . . . .    2 rows behind the frame
 *   . . . . . .
 *   . F F F F .    the frame row (z = PortalSite.Z), opening in the middle two columns
 *   . . . . . .    3 rows in front of the frame (+Z), where /dross site puts the player
 *   . . . . . .
 *   . . . . . .
 * </pre>
 * The floor is only filled in where it isn't already solid ground (water, lava, air over a cliff...).
 * Everything above the floor is cleared up to one block above the frame.
 */
public final class PortalSiteBuilder
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Extra floor/clear space on each side of the frame along X. */
    private static final int PAD_X = 1;
    /** Floor/clear rows behind (-Z) and in front (+Z) of the frame. */
    private static final int ROWS_BEHIND = 2;
    static final int ROWS_IN_FRONT = 3;
    /** How many air blocks are cleared above the floor (frame height + 1). */
    private static final int CLEAR_HEIGHT = PortalSite.FRAME_HEIGHT + 1;
    /** Below the floor, fill up to this many extra blocks of water/lava/air so the platform has some support. */
    private static final int SUPPORT_DEPTH = 3;

    /**
     * Makes sure the frame exists in this world, building it the first time.
     * Must be called on the server thread with the Overworld.
     *
     * @return the frame position (bottom corner frame block with the lowest X)
     */
    public static BlockPos ensurePlaced(ServerLevel overworld)
    {
        PortalSiteData data = PortalSiteData.get(overworld);
        if (data.isPlaced())
        {
            return data.getFramePos();
        }

        BlockPos framePos = plannedFramePos(overworld);
        build(overworld, framePos);
        data.markPlaced(framePos);
        LOGGER.info("Dross: built the unlit portal site frame at {}", framePos.toShortString());
        return framePos;
    }

    /** Where the frame goes: on the ground at the site, with the site column inside the opening. */
    static BlockPos plannedFramePos(ServerLevel level)
    {
        int standY = findStandY(level, PortalSite.X, PortalSite.Z);
        standY = Mth.clamp(standY, level.getMinBuildHeight() + 1, level.getMaxBuildHeight() - CLEAR_HEIGHT - 1);
        return new BlockPos(PortalSite.frameMinX(), standY, PortalSite.Z);
    }

    /**
     * The Y a player would stand at in this column: one above the top solid block or the top of water/lava.
     * Trees (logs and leaves) and things you can walk through (grass, flowers, snow layers) are ignored.
     */
    private static int findStandY(ServerLevel level, int x, int z)
    {
        int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, top, z);
        for (int y = top - 1; y >= level.getMinBuildHeight(); y--)
        {
            pos.setY(y);
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS))
            {
                continue;
            }
            if (!state.getFluidState().isEmpty())
            {
                return y + 1; // surface of water or lava
            }
            if (!state.getCollisionShape(level, pos).isEmpty())
            {
                return y + 1; // solid ground
            }
        }
        return level.getSeaLevel();
    }

    private static void build(ServerLevel level, BlockPos frame)
    {
        int frameX = frame.getX();
        int floorY = frame.getY() - 1;
        int bottomY = frame.getY();
        int topY = bottomY + CLEAR_HEIGHT - 1;
        int frameZ = frame.getZ();

        int minX = frameX - PAD_X;
        int maxX = frameX + PortalSite.FRAME_WIDTH - 1 + PAD_X;
        int minZ = frameZ - ROWS_BEHIND;
        int maxZ = frameZ + ROWS_IN_FRONT;

        BlockState platform = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // 1. Solid floor, with a little support underneath where there's water, lava or a drop.
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                pos.set(x, floorY, z);
                if (!isSolidGround(level, pos))
                {
                    level.setBlock(pos, platform, Block.UPDATE_ALL);
                    for (int d = 1; d <= SUPPORT_DEPTH; d++)
                    {
                        pos.set(x, floorY - d, z);
                        if (isSolidGround(level, pos))
                        {
                            break;
                        }
                        level.setBlock(pos, platform, Block.UPDATE_ALL);
                    }
                }
            }
        }

        // 2. Seal water/lava (and sand/gravel above) around the cleared box so nothing pours or falls into it.
        for (int x = minX - 1; x <= maxX + 1; x++)
        {
            for (int z = minZ - 1; z <= maxZ + 1; z++)
            {
                boolean inside = x >= minX && x <= maxX && z >= minZ && z <= maxZ;
                if (!inside)
                {
                    for (int y = bottomY; y <= topY; y++)
                    {
                        pos.set(x, y, z);
                        if (!level.getBlockState(pos).getFluidState().isEmpty())
                        {
                            level.setBlock(pos, platform, Block.UPDATE_ALL);
                        }
                    }
                }
                else
                {
                    pos.set(x, topY + 1, z);
                    BlockState above = level.getBlockState(pos);
                    if (!above.getFluidState().isEmpty() || above.getBlock() instanceof FallingBlock)
                    {
                        level.setBlock(pos, platform, Block.UPDATE_ALL);
                    }
                }
            }
        }

        // 3. Clear the space above the floor (trees, terrain, water...).
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                for (int y = bottomY; y <= topY; y++)
                {
                    pos.set(x, y, z);
                    if (!level.getBlockState(pos).isAir())
                    {
                        level.setBlock(pos, air, Block.UPDATE_ALL);
                    }
                }
            }
        }

        // 4. The frame itself: 4 wide x 5 tall along X, corners included, opening left empty (unlit).
        BlockState frameBlock = ModBlocks.DROSS_PORTAL_FRAME.get().defaultBlockState();
        for (int i = 0; i < PortalSite.FRAME_WIDTH; i++)
        {
            for (int j = 0; j < PortalSite.FRAME_HEIGHT; j++)
            {
                boolean edge = i == 0 || i == PortalSite.FRAME_WIDTH - 1 || j == 0 || j == PortalSite.FRAME_HEIGHT - 1;
                if (edge)
                {
                    level.setBlock(pos.set(frameX + i, bottomY + j, frameZ), frameBlock, Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Something you can stand on: not air, not a liquid, not leaves, and has a collision box. */
    private static boolean isSolidGround(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.is(BlockTags.LEAVES)
                && !state.getCollisionShape(level, pos).isEmpty();
    }

    private PortalSiteBuilder() {}
}
