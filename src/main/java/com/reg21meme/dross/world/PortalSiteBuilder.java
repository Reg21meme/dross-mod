package com.reg21meme.dross.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FallingBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Builds the portal site (the castle) once per world. Where it goes and how it's placed is {@link PortalSitePlacer}'s
 * job (a random spot 3,000 to 10,000 blocks from spawn, picked and built in the background when the world is new);
 * this class is the entry point other code uses, plus the small helpers the placeholder shrine needs.
 * <ol>
 *   <li>If the user's castle template {@code data/dross/structures/portal_castle.nbt} exists and contains exactly one
 *       complete, unlit Dross frame, it's placed centred on the site ({@link PortalCastle}).</li>
 *   <li>Otherwise the <b>Fallen Cathedral</b> (shrine design No. 1) is built with its frame opening on the site
 *       column, on fitted ground ({@link PortalCathedral}).</li>
 *   <li>Only if that fails (its template is missing, or its frame doesn't pass the check: an error is logged), the
 *       small placeholder ruined shrine is built instead ({@link PortalShrine}).</li>
 *   <li>Then the Dross "leaks" into the area around the frame, once ({@link SiteLeak}).</li>
 * </ol>
 * The frame's corner, axis and opening size are saved in {@link PortalSiteData}. The frame is never lit here
 * (lighting it with the Rift Key is the portal area's job). Worlds that already have a site keep it.
 */
public final class PortalSiteBuilder
{
    /** How many air blocks are cleared above the placeholder's floor (frame height + 1). */
    static final int CLEAR_HEIGHT = PortalSite.FRAME_HEIGHT + 1;
    /** Below the floor, fill up to this many extra blocks of water/lava/air so the platform has some support. */
    static final int SUPPORT_DEPTH = 3;

    /**
     * Makes sure the site exists in this world, building it right now if it isn't built yet (it normally is, a little
     * while after a new world opens: this then waits for the search and loads the spot's chunks on the spot, which can
     * take a few seconds). Must be called on the server thread with the Overworld.
     *
     * @return the frame position (bottom corner frame block with the lowest X and Z)
     */
    public static BlockPos ensurePlaced(ServerLevel overworld)
    {
        PortalSiteData data = PortalSiteData.get(overworld);
        if (data.isPlaced())
        {
            return data.getFramePos();
        }
        return PortalSitePlacer.placeNow(overworld).corner();
    }

    /**
     * Where the small placeholder frame goes for the site column {@code x}/{@code z}: on the ground there, with the
     * site column as the left block of the opening.
     */
    static BlockPos placeholderFramePos(ServerLevel level, int x, int z)
    {
        int standY = clampY(level, findStandY(level, x, z), CLEAR_HEIGHT);
        return new BlockPos(x - 1, standY, z);
    }

    /** Keeps a build of this height (plus its support underneath) inside the world's build limits. */
    static int clampY(ServerLevel level, int y, int height)
    {
        return Mth.clamp(y, level.getMinBuildHeight() + SUPPORT_DEPTH + 2, level.getMaxBuildHeight() - height - 1);
    }

    /**
     * The Y a player would stand at in this column: one above the top solid block or the top of water/lava.
     * Trees (logs and leaves) and things you can walk through (grass, flowers, snow layers) are ignored.
     * The column's chunk is loaded first ({@code Level.getHeight} would answer "the bottom of the world" for a chunk
     * that isn't loaded yet).
     */
    static int findStandY(ServerLevel level, int x, int z)
    {
        int top = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
                .getHeight(Heightmap.Types.WORLD_SURFACE, x, z) + 1;
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

    /**
     * Flattens a box for building on:
     * <ol>
     *   <li>a solid floor at {@code floorY} wherever the ground isn't solid (water, lava, a drop), with a little
     *       support underneath;</li>
     *   <li>water/lava around the box (and sand/gravel above it) sealed off so nothing pours or falls in;</li>
     *   <li>everything from {@code floorY + 1} up to {@code topY} cleared (trees, terrain, water...).</li>
     * </ol>
     */
    static void prepareGround(ServerLevel level, int minX, int maxX, int minZ, int maxZ, int floorY, int topY, BlockState platform)
    {
        int bottomY = floorY + 1;
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
    }

    /** Something you can stand on: not air, not a liquid, not leaves, and has a collision box. */
    static boolean isSolidGround(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.is(BlockTags.LEAVES)
                && !state.getCollisionShape(level, pos).isEmpty();
    }

    private PortalSiteBuilder() {}
}
