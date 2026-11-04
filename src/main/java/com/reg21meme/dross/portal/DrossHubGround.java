package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.world.shrine.GroundFit;
import com.reg21meme.dross.world.shrine.ShrineDesign;
import java.lang.ref.WeakReference;
import java.util.Arrays;
import net.minecraft.util.Mth;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

/**
 * The ground the hub stands on, worked out from the world's seed alone, before any of its chunks exist: the same answer
 * on every thread and every time for a world, so the structure that builds it with the land ({@link DrossHubPiece}) and
 * {@link DrossHub#layout} always agree.
 * <ul>
 *   <li>{@link #groundY}: its ground level, the median of the Dross generator's own heights over the Shattered Spire's
 *       footprint (sampled every {@value #SAMPLE_STEP} blocks, its last row and column too), nudged to the nearest level
 *       the spire fits at in the world's height. The land round Dross 0,0 is kept gentle ({@code dross:hub_falloff}).
 *       Worked out once per world (about 0.1-0.2 s: each sample costs the generator about 0.8 ms) and remembered.</li>
 *   <li>{@link #target}: where one column's ground goes: the ground level inside the footprint; d blocks outside it, its
 *       own natural height but never more than d above or below the ground level, out to {@link DrossHub#HUB_BLEND_MARGIN}
 *       (a staircase back to the land, at most a block a step; the Overworld's cathedral's rule, world-builder's
 *       {@link GroundFit}); further out, its own height.</li>
 * </ul>
 */
public final class DrossHubGround
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The footprint's heights are sampled every this many blocks along X and Z (its last row and column too). */
    static final int SAMPLE_STEP = 6;

    private static final Object LOCK = new Object();
    /** The world (its random state) the remembered ground level belongs to. */
    private static WeakReference<RandomState> rememberedFor = new WeakReference<>(null);
    private static int remembered;
    /** How long working it out took the last time (ms), for the tests' log. */
    private static long lastMillis = -1L;

    private DrossHubGround() {}

    /**
     * The hub's ground level (the Y of the ground block it stands on) in the world whose generator and random state these
     * are: the Dross's. Thread-safe; the first call for a world samples the generator (about 0.1-0.2 s: each sample costs the generator about 0.8 ms), later
     * ones are free.
     */
    public static int groundY(ChunkGenerator generator, RandomState random, LevelHeightAccessor heights)
    {
        synchronized (LOCK)
        {
            if (rememberedFor.get() == random)
            {
                return remembered;
            }
            long start = System.nanoTime();
            BoundingBox columns = DrossHub.columns();
            int[] xs = samples(columns.minX(), columns.maxX());
            int[] zs = samples(columns.minZ(), columns.maxZ());
            int[] found = new int[xs.length * zs.length];
            int n = 0;
            for (int x : xs)
            {
                for (int z : zs)
                {
                    // (the height map counts the first free block above the ground; the ground block is one under it)
                    found[n++] = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, heights, random) - 1;
                }
            }
            Arrays.sort(found);
            int median = found[n / 2];
            int ground = fitting(DrossHub.spire(), heights, median);
            long millis = (System.nanoTime() - start) / 1_000_000L;
            lastMillis = millis;
            rememberedFor = new WeakReference<>(random);
            remembered = ground;
            LOGGER.info("Dross hub: its ground level is y {} (the median of {} samples of the generator's own heights under its footprint: "
                    + "lowest {}, median {}, highest {}{}), worked out in {} ms", ground, n, found[0], median, found[n - 1],
                    ground == median ? "" : "; the spire doesn't fit in the world's height there", millis);
            return ground;
        }
    }

    /** How long the last {@link #groundY} that sampled the generator took, in milliseconds (-1 if none has yet). */
    public static long lastMillis()
    {
        synchronized (LOCK)
        {
            return lastMillis;
        }
    }

    /**
     * Where a column's ground goes: {@code groundY} in the footprint; d blocks outside it (d below
     * {@link DrossHub#HUB_BLEND_MARGIN}), its {@code natural} height clamped to within d of {@code groundY}; else
     * {@code natural}.
     */
    public static int target(int x, int z, int natural, int groundY)
    {
        int d = GroundFit.distanceOutside(DrossHub.columns(), x, z);
        if (d == 0)
        {
            return groundY;
        }
        if (d >= DrossHub.HUB_BLEND_MARGIN)
        {
            return natural;
        }
        return Mth.clamp(natural, groundY - d, groundY + d);
    }

    /** The sample columns from {@code min} to {@code max}: every {@value #SAMPLE_STEP}, and {@code max} itself. */
    private static int[] samples(int min, int max)
    {
        int count = (max - min) / SAMPLE_STEP + 1;
        boolean last = (max - min) % SAMPLE_STEP != 0;
        int[] out = new int[count + (last ? 1 : 0)];
        for (int i = 0; i < count; i++)
        {
            out[i] = min + i * SAMPLE_STEP;
        }
        if (last)
        {
            out[count] = max;
        }
        return out;
    }

    /** {@code wanted} if the spire fits in the world's height with its ground there, else the nearest level that does. */
    private static int fitting(ShrineDesign spire, LevelHeightAccessor heights, int wanted)
    {
        if (spire.fitsHeightAt(heights, wanted))
        {
            return wanted;
        }
        for (int d = 1; d <= heights.getHeight(); d++)
        {
            if (spire.fitsHeightAt(heights, wanted - d))
            {
                return wanted - d;
            }
            if (spire.fitsHeightAt(heights, wanted + d))
            {
                return wanted + d;
            }
        }
        return wanted;
    }
}
