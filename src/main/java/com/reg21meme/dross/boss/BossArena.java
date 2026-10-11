package com.reg21meme.dross.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The placeholder boss arena ({@code /dross boss arena colossus}): a flat round floor of red nether bricks and similar
 * blocks, {@link #RADIUS} blocks out from the middle (31 blocks across), with:
 * <ul>
 *   <li>a medallion of chiseled nether bricks in the middle, a ring of nether bricks at {@link #INNER_RING} blocks and
 *       a border of nether bricks round the edge; a few cracked bricks scattered over the rest;</li>
 *   <li>shroomlights set into the floor (on the inner ring and in the border), so no monsters spawn on it at night;</li>
 *   <li>a knee-high wall of red nether brick round the edge, with a 3-block gap at north, east, south and west (the
 *       Colossus's charge stops at the wall, and you can walk in and out through the gaps);</li>
 *   <li>the air above it cleared {@link #CLEAR_HEIGHT} blocks up (blocks with a block entity, like chests, are left
 *       alone), and gaps under the floor filled with nether bricks up to {@link #FILL_DEPTH} blocks down, so it
 *       doesn't float on uneven ground.</li>
 * </ul>
 * It's a stand-in for the real arena (the golem floor of the Fire Necromancer's tower, later).
 */
public final class BossArena
{
    /** Blocks from the middle to the edge of the floor (the floor is 2 x RADIUS + 1 = 31 blocks across). */
    public static final int RADIUS = 15;
    /** The ring of nether bricks (with lights) this many blocks from the middle. */
    private static final double INNER_RING = 7.0D;
    /** The middle medallion's radius. */
    private static final double MEDALLION = 1.5D;
    /** How much of the rest of the floor is cracked bricks. */
    private static final float CRACKED_SHARE = 0.1F;
    /** Air is cleared this many blocks above the floor (the Colossus is 5.5 blocks tall; its blobs fly high). */
    private static final int CLEAR_HEIGHT = 20;
    /** Gaps under the floor are filled at most this many blocks down. */
    private static final int FILL_DEPTH = 6;
    /** Half the width of the gaps in the wall (1 = a 3-block gap). */
    private static final int GATE_HALF = 1;
    /** Shroomlights on the inner ring and in the border, evenly spaced. */
    private static final int RING_LIGHTS = 8;
    private static final int BORDER_LIGHTS = 12;
    /** Set without poking neighbours (no redstone going off), but players see every change. */
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private BossArena() {}

    /** What building the arena did. */
    public record Result(int changed, int keptBlockEntities)
    {
    }

    /**
     * Builds the arena with its floor at {@code floor} (the block under the middle of it).
     */
    public static Result build(ServerLevel level, BlockPos floor)
    {
        BlockState red = Blocks.RED_NETHER_BRICKS.defaultBlockState();
        BlockState dark = Blocks.NETHER_BRICKS.defaultBlockState();
        BlockState cracked = Blocks.CRACKED_NETHER_BRICKS.defaultBlockState();
        BlockState chiseled = Blocks.CHISELED_NETHER_BRICKS.defaultBlockState();
        BlockState light = Blocks.SHROOMLIGHT.defaultBlockState();
        BlockState wall = Blocks.RED_NETHER_BRICK_WALL.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        int kept = 0;
        for (int dx = -RADIUS - 1; dx <= RADIUS + 1; dx++)
        {
            for (int dz = -RADIUS - 1; dz <= RADIUS + 1; dz++)
            {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > RADIUS + 0.5D)
                {
                    continue;
                }
                int x = floor.getX() + dx;
                int z = floor.getZ() + dz;
                // Clear the air above (keeping block entities such as chests).
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++)
                {
                    pos.set(x, floor.getY() + dy, z);
                    BlockState here = level.getBlockState(pos);
                    if (here.isAir())
                    {
                        continue;
                    }
                    if (here.hasBlockEntity())
                    {
                        kept++;
                        continue;
                    }
                    level.setBlock(pos, air, FLAGS);
                    changed++;
                }
                // The floor itself.
                BlockState top;
                if (r <= MEDALLION)
                {
                    top = chiseled;
                }
                else if (Math.abs(r - INNER_RING) < 0.5D)
                {
                    top = isLightSpot(dx, dz, RING_LIGHTS) ? light : dark;
                }
                else if (r >= RADIUS - 1.0D)
                {
                    top = isLightSpot(dx, dz, BORDER_LIGHTS) && r < RADIUS ? light : dark;
                }
                else
                {
                    top = hash(x, z) < CRACKED_SHARE ? cracked : red;
                }
                pos.set(x, floor.getY(), z);
                if (!level.getBlockState(pos).hasBlockEntity())
                {
                    level.setBlock(pos, top, FLAGS);
                    changed++;
                }
                else
                {
                    kept++;
                }
                // Fill gaps under it, down to solid ground.
                for (int dy = 1; dy <= FILL_DEPTH; dy++)
                {
                    pos.set(x, floor.getY() - dy, z);
                    BlockState below = level.getBlockState(pos);
                    if (below.hasBlockEntity() || (!below.canBeReplaced() && below.getFluidState().isEmpty()))
                    {
                        break;
                    }
                    level.setBlock(pos, dark, FLAGS);
                    changed++;
                }
                // The low wall round the edge, with a gap at each compass point.
                boolean edge = r >= RADIUS - 0.5D;
                boolean gate = Math.abs(dx) <= GATE_HALF || Math.abs(dz) <= GATE_HALF;
                if (edge && !gate)
                {
                    pos.set(x, floor.getY() + 1, z);
                    if (!level.getBlockState(pos).hasBlockEntity())
                    {
                        level.setBlock(pos, wall, Block.UPDATE_ALL);
                        changed++;
                    }
                }
            }
        }
        return new Result(changed, kept);
    }

    /** True for the floor blocks that are lights on a ring: {@code count} of them, evenly spaced round it. */
    private static boolean isLightSpot(int dx, int dz, int count)
    {
        double angle = Math.atan2(dz, dx);
        double step = 2.0D * Math.PI / count;
        double off = Math.abs(Math.IEEEremainder(angle, step));
        double r = Math.sqrt(dx * dx + dz * dz);
        return off * r < 0.5D;
    }

    /** A steady 0-1 number for a column (the same every time), for scattering cracked bricks. */
    private static float hash(int x, int z)
    {
        long h = x * 73856093L ^ z * 19349663L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0F;
    }

    /** The block under the command's feet (or the first solid ground below it): where the arena's floor goes. */
    public static BlockPos floorUnder(ServerLevel level, BlockPos feet)
    {
        BlockPos.MutableBlockPos pos = feet.below().mutable();
        for (int i = 0; i < 16 && pos.getY() > level.getMinBuildHeight(); i++)
        {
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && state.getFluidState().isEmpty() && state.isFaceSturdy(level, pos, Direction.UP))
            {
                return pos.immutable();
            }
            pos.move(Direction.DOWN);
        }
        return feet.below();
    }
}
