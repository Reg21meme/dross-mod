package com.reg21meme.dross.dimension.sites;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

/**
 * One patch of a boss biome: a separate region of it on the surface, numbered by its distance from the hub (see
 * {@link BossSites} for the rules). It knows its cells (chunks: the biome map has one sample per chunk, at the chunk's
 * middle block), how far each lies from the patch's edge, and where it has the most room.
 * <p>
 * Definitions:
 * <ul>
 *   <li><b>distance</b>: from the hub ({@code DrossHub.CENTER}, 0, 0) to the patch's <b>nearest point</b> (the middle of
 *       its nearest chunk, so to within 8 blocks of its edge); patches are numbered by it, nearest first (ties: the
 *       smaller {@link #id}).</li>
 *   <li><b>depth</b> of a spot: how far it lies inside the patch, from the middle of its chunk to the middle of the
 *       nearest chunk that isn't in the patch, less half a chunk (8 blocks): about the radius of the biggest circle round
 *       it that stays in the patch.</li>
 *   <li><b>id</b>: its nearest chunk ({@link ChunkPos#asLong}), the same every time for the same seed.</li>
 * </ul>
 * Immutable and safe to share between threads.
 */
public final class Patch
{
    private final ResourceKey<Biome> biome;
    private final long id;
    private final int number;
    private final double distance;
    private final int nearestCell;
    /** The patch's cells (chunks, {@link ChunkPos#asLong}), sorted, and each one's depth (blocks), in the same order. */
    private final long[] cells;
    private final float[] depths;
    /** The cells' indices, deepest first (ties: nearer the hub, then the smaller chunk). */
    private final int[] byDepth;
    private final int minX;
    private final int minZ;
    private final int maxX;
    private final int maxZ;

    Patch(ResourceKey<Biome> biome, int number, double distance, long nearest, long[] cells, float[] depths, int[] byDepth)
    {
        this.biome = biome;
        this.id = nearest;
        this.number = number;
        this.distance = distance;
        this.cells = cells;
        this.depths = depths;
        this.byDepth = byDepth;
        this.nearestCell = Arrays.binarySearch(cells, nearest);
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (long c : cells)
        {
            x0 = Math.min(x0, ChunkPos.getX(c));
            z0 = Math.min(z0, ChunkPos.getZ(c));
            x1 = Math.max(x1, ChunkPos.getX(c));
            z1 = Math.max(z1, ChunkPos.getZ(c));
        }
        this.minX = x0 * BossSitesTuning.CELL;
        this.minZ = z0 * BossSitesTuning.CELL;
        this.maxX = x1 * BossSitesTuning.CELL + BossSitesTuning.CELL - 1;
        this.maxZ = z1 * BossSitesTuning.CELL + BossSitesTuning.CELL - 1;
    }

    /** The biome it's a patch of. */
    public ResourceKey<Biome> biome()
    {
        return this.biome;
    }

    /** A number that stays the same for this patch (for the same seed): its nearest chunk. */
    public long id()
    {
        return this.id;
    }

    /** Its place in its biome's list, nearest the hub first: 1, 2, 3... */
    public int number()
    {
        return this.number;
    }

    /** Its group (0 = the 3 nearest patches, 1 = the next 3, ...). */
    public int group()
    {
        return (this.number - 1) / BossSitesTuning.GROUP_SIZE;
    }

    /** Blocks from the hub to its nearest point. */
    public double distance()
    {
        return this.distance;
    }

    /** Its nearest point to the hub (the middle of its nearest chunk; y 0). */
    public BlockPos nearestPoint()
    {
        return middle(this.cells[this.nearestCell]);
    }

    /** Its deepest point (where it has the most room round it; y 0). */
    public BlockPos deepest()
    {
        return middle(this.cells[this.byDepth[0]]);
    }

    /** How deep its deepest point is (blocks): about the radius of the biggest circle that fits inside it. */
    public double depth()
    {
        return this.depths[this.byDepth[0]];
    }

    /** Its area (blocks², 256 per chunk). */
    public int area()
    {
        return this.cells.length * BossSitesTuning.CELL * BossSitesTuning.CELL;
    }

    /** How many chunks it covers. */
    public int chunkCount()
    {
        return this.cells.length;
    }

    /** True if the column is in the patch (its chunk is one of the patch's). */
    public boolean contains(int x, int z)
    {
        if (x < this.minX || x > this.maxX || z < this.minZ || z > this.maxZ)
        {
            return false;
        }
        return Arrays.binarySearch(this.cells, ChunkPos.asLong(Math.floorDiv(x, BossSitesTuning.CELL), Math.floorDiv(z, BossSitesTuning.CELL))) >= 0;
    }

    /** True if the chunk is one of the patch's. */
    public boolean containsChunk(ChunkPos chunk)
    {
        return Arrays.binarySearch(this.cells, chunk.toLong()) >= 0;
    }

    /** How deep the column lies in the patch (blocks; negative outside it). */
    public double depthAt(int x, int z)
    {
        int i = Arrays.binarySearch(this.cells, ChunkPos.asLong(Math.floorDiv(x, BossSitesTuning.CELL), Math.floorDiv(z, BossSitesTuning.CELL)));
        return i >= 0 ? this.depths[i] : -1.0D;
    }

    /** The smallest and largest block X and Z of its chunks (a box round it). */
    public int minX()
    {
        return this.minX;
    }

    public int minZ()
    {
        return this.minZ;
    }

    public int maxX()
    {
        return this.maxX;
    }

    public int maxZ()
    {
        return this.maxZ;
    }

    /**
     * Spots to try a use at: the middles of its deepest chunks, deepest first, each at least {@code spacing} blocks from
     * the ones before it, at most {@code count} (y 0). The same list every time.
     */
    public List<BlockPos> spots(int count, int spacing)
    {
        List<BlockPos> spots = new ArrayList<>();
        long spacing2 = (long) spacing * spacing;
        for (int index : this.byDepth)
        {
            if (spots.size() >= count)
            {
                break;
            }
            BlockPos p = middle(this.cells[index]);
            boolean apart = true;
            for (BlockPos s : spots)
            {
                long dx = s.getX() - p.getX();
                long dz = s.getZ() - p.getZ();
                if (dx * dx + dz * dz < spacing2)
                {
                    apart = false;
                    break;
                }
            }
            if (apart)
            {
                spots.add(p);
            }
        }
        return spots;
    }

    /** Blocks from (x, z) to the nearest corner or edge of the box round the patch (0 inside it): a lower bound for anything in it. */
    double boxDistance(int x, int z)
    {
        double dx = Math.max(0, Math.max(this.minX - x, x - this.maxX));
        double dz = Math.max(0, Math.max(this.minZ - z, z - this.maxZ));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** The chunks it covers ({@link ChunkPos#asLong}, sorted): for maps. */
    long[] chunks()
    {
        return this.cells.clone();
    }

    /** The middle block of a cell (chunk), y 0. */
    static BlockPos middle(long cell)
    {
        return new BlockPos(ChunkPos.getX(cell) * BossSitesTuning.CELL + BossSitesTuning.CELL / 2, 0,
                ChunkPos.getZ(cell) * BossSitesTuning.CELL + BossSitesTuning.CELL / 2);
    }

    @Override
    public String toString()
    {
        return String.format(java.util.Locale.ROOT, "%s patch %d (group %d): %d blocks from the hub at %s, %d chunks, deepest %s (%.0f deep)",
                this.biome.location().getPath(), this.number, this.group() + 1, Math.round(this.distance), nearestPoint().toShortString(),
                this.cells.length, deepest().toShortString(), depth());
    }
}
