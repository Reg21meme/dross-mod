package com.reg21meme.dross.boss;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;

/**
 * The Rat King's placeholder arena ({@code /dross boss arena ratking}): a flat round floor of dirt and coarse dirt,
 * {@link #RADIUS} blocks out from the middle (31 blocks across). About a third of it ({@link #COARSE_SHARE}) is coarse
 * dirt, in irregular patches: a smooth noise made from the blocks' own coordinates (the same every time), cut so that
 * share of the circle is coarse. The air above it is cleared {@link #CLEAR_HEIGHT} blocks up (blocks with a block entity,
 * like chests, are left alone), and gaps under it are filled with dirt up to {@link #FILL_DEPTH} blocks down, so it
 * doesn't float on uneven ground. It's a stand-in for his real lair (beneath Blightwood, later).
 */
public final class RatKingArena
{
    /** Blocks from the middle to the edge of the floor (the floor is 2 x RADIUS + 1 = 31 blocks across). */
    public static final int RADIUS = 15;
    /** Air is cleared this many blocks above the floor (he's 3.4 blocks tall; his poison balls arc a little higher). */
    public static final int CLEAR_HEIGHT = 15;
    /** Gaps under the floor are filled at most this many blocks down. */
    public static final int FILL_DEPTH = 6;
    /** About this share of the floor is coarse dirt. */
    public static final double COARSE_SHARE = 1.0D / 3.0D;
    /** The patches' noise: two layers, their cell sizes (blocks) and weights. */
    private static final double BIG_CELL = 5.0D;
    private static final double SMALL_CELL = 2.5D;
    private static final double BIG_WEIGHT = 0.65D;
    private static final double SMALL_WEIGHT = 0.35D;
    /** Set without poking neighbours (no redstone going off), but players see every change. */
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    private RatKingArena() {}

    /** What building the arena did. */
    public record Result(int changed, int keptBlockEntities)
    {
    }

    /** True if a column (dx, dz from the middle) is part of the round floor. */
    public static boolean inFloor(int dx, int dz)
    {
        return dx * dx + dz * dz <= (RADIUS + 0.5D) * (RADIUS + 0.5D);
    }

    /**
     * Builds the arena with its floor at {@code floor} (the block under the middle of it).
     */
    public static Result build(ServerLevel level, BlockPos floor)
    {
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState coarse = Blocks.COARSE_DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        double threshold = coarseThreshold(floor);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int changed = 0;
        int kept = 0;
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
            {
                if (!inFloor(dx, dz))
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
                // The floor itself: dirt, or coarse dirt in the patches.
                pos.set(x, floor.getY(), z);
                if (!level.getBlockState(pos).hasBlockEntity())
                {
                    level.setBlock(pos, noise(x, z) > threshold ? coarse : dirt, FLAGS);
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
                    level.setBlock(pos, dirt, FLAGS);
                    changed++;
                }
            }
        }
        return new Result(changed, kept);
    }

    /**
     * The noise level above which a column of this arena is coarse dirt: the level that {@link #COARSE_SHARE} of its
     * columns are above (so every arena, wherever it is, is about a third coarse dirt).
     */
    static double coarseThreshold(BlockPos floor)
    {
        List<Double> values = new ArrayList<>();
        for (int dx = -RADIUS; dx <= RADIUS; dx++)
        {
            for (int dz = -RADIUS; dz <= RADIUS; dz++)
            {
                if (inFloor(dx, dz))
                {
                    values.add(noise(floor.getX() + dx, floor.getZ() + dz));
                }
            }
        }
        values.sort(null);
        int index = Mth.clamp((int) Math.round(values.size() * (1.0D - COARSE_SHARE)) - 1, 0, values.size() - 1);
        return values.get(index);
    }

    /** A smooth, patchy 0-1 noise for a column (the same every time): two layers of value noise. */
    static double noise(int x, int z)
    {
        return BIG_WEIGHT * valueNoise(x, z, BIG_CELL, 1) + SMALL_WEIGHT * valueNoise(x, z, SMALL_CELL, 2);
    }

    /** Value noise: a steady random number at each corner of a grid of {@code cell}-block squares, blended smoothly. */
    private static double valueNoise(int x, int z, double cell, int salt)
    {
        double fx = x / cell;
        double fz = z / cell;
        int ix = Mth.floor(fx);
        int iz = Mth.floor(fz);
        double tx = smooth(fx - ix);
        double tz = smooth(fz - iz);
        double a = hash(ix, iz, salt);
        double b = hash(ix + 1, iz, salt);
        double c = hash(ix, iz + 1, salt);
        double d = hash(ix + 1, iz + 1, salt);
        double top = a + (b - a) * tx;
        double bottom = c + (d - c) * tx;
        return top + (bottom - top) * tz;
    }

    private static double smooth(double t)
    {
        return t * t * (3.0D - 2.0D * t);
    }

    /** A steady 0-1 number for a grid corner (the same every time). */
    private static double hash(int x, int z, int salt)
    {
        long h = x * 73856093L ^ z * 19349663L ^ salt * 83492791L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0D;
    }
}
