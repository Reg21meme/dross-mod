package com.reg21meme.dross.dimension.sites;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReferenceArray;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;

/**
 * The boss-site planner's biome map ({@link BossSites}): for each map cell (16 x 16 blocks, sampled at its middle), which
 * boss biome it is (or none), and which patch it belongs to once the planner has joined it into one. Kept in tiles of
 * {@link #TILE} x {@link #TILE} cells.
 * <ul>
 *   <li>A cell's biome is sampled once and remembered ({@link #kind}). Sampling is a pure function of the cell, so any
 *       thread may do it at any time: two threads sampling the same cell write the same value, and a thread that doesn't
 *       see another's value yet just samples it again.</li>
 *   <li>Who owns a cell ({@link #owner}) is written only by the thread holding the planner's scan lock; any thread may
 *       read it. A patch, once written, never changes ({@link Patch} is immutable).</li>
 *   <li>{@link #sampleRings} samples a band of rings round the hub, on this thread alone or, when a caller is waiting
 *       for it, shared with the planner's helper threads ({@link PlannerThreads}).</li>
 * </ul>
 */
final class BiomeCells
{
    /** A tile is this many cells each way (64: 1,024 blocks). */
    static final int TILE_SHIFT = 6;
    static final int TILE = 1 << TILE_SHIFT;
    private static final int TILE_MASK = TILE - 1;
    /** Tiles this far from the hub's tile each way are kept in a plain array (past the planner's reach plus a margin); others in a map. */
    private static final int GRID_RADIUS = (BossSitesTuning.MAX_DISTANCE + 8192) / (TILE * BossSitesTuning.CELL) + 1;
    private static final int GRID_SIDE = 2 * GRID_RADIUS + 1;

    /** A cell's kind: not sampled yet, not a boss biome, or boss biome i (index in {@link BossSites#BOSS_BIOMES}) as {@code 2 + i}. */
    static final byte UNKNOWN = 0;
    static final byte OTHER = 1;
    private static final int FIRST_BOSS = 2;

    /** A band smaller than this (cells) is sampled by the caller alone. */
    private static final int PARALLEL_MIN_CELLS = 4096;
    /** A share of the sampling handed out at a time (cells, about: about a millisecond's work). */
    private static final int WORK_UNIT_CELLS = 512;

    private final SiteTerrain terrain;
    private final List<ResourceKey<Biome>> bossBiomes;
    private final int hubTileX;
    private final int hubTileZ;
    private final AtomicReferenceArray<Tile> grid = new AtomicReferenceArray<>(GRID_SIDE * GRID_SIDE);
    private final ConcurrentHashMap<Long, Tile> far = new ConcurrentHashMap<>();

    private static final class Tile
    {
        final byte[] kinds = new byte[TILE * TILE];
        /** Who owns each cell (a {@code BossSites.Shape} while it waits to be numbered, then its {@link Patch}); made when first needed. */
        volatile Object[] owners;
    }

    BiomeCells(SiteTerrain terrain, List<ResourceKey<Biome>> bossBiomes, int hubCellX, int hubCellZ)
    {
        this.terrain = terrain;
        this.bossBiomes = List.copyOf(bossBiomes);
        this.hubTileX = hubCellX >> TILE_SHIFT;
        this.hubTileZ = hubCellZ >> TILE_SHIFT;
    }

    /** The kind that means boss biome {@code index} ({@link BossSites#BOSS_BIOMES}). */
    static byte bossKind(int index)
    {
        return (byte) (FIRST_BOSS + index);
    }

    /** The boss biome index of a kind, or -1 if it isn't one. */
    static int bossIndex(byte kind)
    {
        return kind >= FIRST_BOSS ? kind - FIRST_BOSS : -1;
    }

    // ---------------------------------------------------------------------------------------------
    // Tiles
    // ---------------------------------------------------------------------------------------------

    private static int local(int cx, int cz)
    {
        return (cz & TILE_MASK) << TILE_SHIFT | cx & TILE_MASK;
    }

    private int gridIndex(int tx, int tz)
    {
        int gx = tx - this.hubTileX + GRID_RADIUS;
        int gz = tz - this.hubTileZ + GRID_RADIUS;
        return gx < 0 || gz < 0 || gx >= GRID_SIDE || gz >= GRID_SIDE ? -1 : gz * GRID_SIDE + gx;
    }

    private Tile tileOrNull(int cx, int cz)
    {
        int tx = cx >> TILE_SHIFT;
        int tz = cz >> TILE_SHIFT;
        int i = gridIndex(tx, tz);
        return i >= 0 ? this.grid.get(i) : this.far.get(ChunkPos.asLong(tx, tz));
    }

    private Tile tile(int cx, int cz)
    {
        int tx = cx >> TILE_SHIFT;
        int tz = cz >> TILE_SHIFT;
        int i = gridIndex(tx, tz);
        if (i < 0)
        {
            return this.far.computeIfAbsent(ChunkPos.asLong(tx, tz), k -> new Tile());
        }
        Tile t = this.grid.get(i);
        if (t == null)
        {
            Tile made = new Tile();
            t = this.grid.compareAndSet(i, null, made) ? made : this.grid.get(i);
        }
        return t;
    }

    // ---------------------------------------------------------------------------------------------
    // Kinds (any thread)
    // ---------------------------------------------------------------------------------------------

    /** The cell's kind (sampled now if it hasn't been yet). Any thread. */
    byte kind(int cx, int cz)
    {
        Tile t = tile(cx, cz);
        int i = local(cx, cz);
        byte k = t.kinds[i];
        if (k == UNKNOWN)
        {
            k = sample(cx, cz);
            t.kinds[i] = k;
        }
        return k;
    }

    /** The biome at the cell's middle, as a kind (the same point and height every time). */
    private byte sample(int cx, int cz)
    {
        Holder<Biome> biome = this.terrain.biome(cx * BossSitesTuning.CELL + BossSitesTuning.CELL / 2, cz * BossSitesTuning.CELL + BossSitesTuning.CELL / 2);
        for (int i = 0; i < this.bossBiomes.size(); i++)
        {
            if (biome.is(this.bossBiomes.get(i)))
            {
                return bossKind(i);
            }
        }
        return OTHER;
    }

    // ---------------------------------------------------------------------------------------------
    // Owners (written under the scan lock, read by anyone)
    // ---------------------------------------------------------------------------------------------

    /** Who owns the cell (a shape waiting to be numbered, a {@link Patch}, or null). Any thread. */
    Object owner(int cx, int cz)
    {
        Tile t = tileOrNull(cx, cz);
        if (t == null)
        {
            return null;
        }
        Object[] owners = t.owners;
        return owners == null ? null : owners[local(cx, cz)];
    }

    Object owner(long cell)
    {
        return owner(ChunkPos.getX(cell), ChunkPos.getZ(cell));
    }

    /** Sets who owns the cell. Only with the scan lock held. */
    void setOwner(long cell, Object owner)
    {
        int cx = ChunkPos.getX(cell);
        int cz = ChunkPos.getZ(cell);
        Tile t = tile(cx, cz);
        Object[] owners = t.owners;
        if (owners == null)
        {
            owners = new Object[TILE * TILE];
            t.owners = owners;
        }
        owners[local(cx, cz)] = owner;
    }

    // ---------------------------------------------------------------------------------------------
    // Sampling bands of rings
    // ---------------------------------------------------------------------------------------------

    /**
     * Samples every cell not sampled yet whose ring round the hub's cell (Chebyshev distance) is {@code from} to
     * {@code to}. With {@code helpers}, the planner's helper threads share the work ({@link PlannerThreads}; the caller
     * works too), in shares of about {@link #WORK_UNIT_CELLS} cells.
     */
    void sampleRings(int hubCellX, int hubCellZ, int from, int to, boolean helpers)
    {
        if (to < from)
        {
            return;
        }
        // the band's rows, as pieces (z, x from, x to) of at most WORK_UNIT_CELLS cells
        IntArrayList pieces = new IntArrayList();
        long cells = 0;
        for (int dz = -to; dz <= to; dz++)
        {
            int cz = hubCellZ + dz;
            if (Math.abs(dz) >= from)
            {
                cells += addPieces(pieces, cz, hubCellX - to, hubCellX + to);
            }
            else
            {
                cells += addPieces(pieces, cz, hubCellX - to, hubCellX - from);
                cells += addPieces(pieces, cz, hubCellX + from, hubCellX + to);
            }
        }
        int count = pieces.size() / 3;
        if (!helpers || cells < PARALLEL_MIN_CELLS)
        {
            samplePieces(pieces, 0, count);
            return;
        }
        // shares: runs of pieces of about WORK_UNIT_CELLS cells each
        IntArrayList starts = new IntArrayList();
        int inShare = WORK_UNIT_CELLS;
        for (int p = 0; p < count; p++)
        {
            if (inShare >= WORK_UNIT_CELLS)
            {
                starts.add(p);
                inShare = 0;
            }
            inShare += pieces.getInt(3 * p + 2) - pieces.getInt(3 * p + 1) + 1;
        }
        starts.add(count);
        PlannerThreads.runAll(starts.size() - 1, u -> samplePieces(pieces, starts.getInt(u), starts.getInt(u + 1)));
    }

    private static int addPieces(IntArrayList pieces, int cz, int cx0, int cx1)
    {
        for (int s = cx0; s <= cx1; s += WORK_UNIT_CELLS)
        {
            pieces.add(cz);
            pieces.add(s);
            pieces.add(Math.min(cx1, s + WORK_UNIT_CELLS - 1));
        }
        return Math.max(0, cx1 - cx0 + 1);
    }

    private void samplePieces(IntArrayList pieces, int first, int end)
    {
        for (int p = first; p < end; p++)
        {
            sampleRow(pieces.getInt(3 * p + 1), pieces.getInt(3 * p + 2), pieces.getInt(3 * p));
        }
    }

    private void sampleRow(int cx0, int cx1, int cz)
    {
        Tile t = null;
        int tileX = Integer.MIN_VALUE;
        for (int cx = cx0; cx <= cx1; cx++)
        {
            if (cx >> TILE_SHIFT != tileX)
            {
                tileX = cx >> TILE_SHIFT;
                t = tile(cx, cz);
            }
            int i = local(cx, cz);
            if (t.kinds[i] == UNKNOWN)
            {
                t.kinds[i] = sample(cx, cz);
            }
        }
    }
}
