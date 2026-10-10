package com.reg21meme.dross.world;

import com.reg21meme.dross.world.shrine.GroundFit;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Looks for good spots for the portal site with the <b>world generator's own noise</b> (the terrain height and biome
 * the generator <i>would</i> make there), so no chunk is generated and it can run on background threads.
 * <p>
 * Flat ground as big as the cathedral is rare (a ring 3,000 to 10,000 blocks from spawn may only have a handful of
 * such places), so the search covers the <b>whole ring</b>: it's cut into overlapping square tiles
 * ({@link #REGION_SIZE} blocks), scanned in a random order (seeded by the world seed, so each world has its own spot).
 * In a tile the biome and the smooth terrain height are read every {@link #GRID} blocks, and every footprint-sized
 * window of that grid is tested, cheapest test first:
 * <ol>
 *   <li>every biome under the window and its blending ring is allowed;</li>
 *   <li>the <b>smooth</b> terrain height (the generator's large-scale "depth", almost free to read) varies little under
 *       the window: hilly windows are skipped before any real height is read;</li>
 *   <li>the real terrain heights on the grid vary by no more than the rules allow (corners first);</li>
 *   <li>no place a village could be ({@link SiteVillages}) is anywhere near;</li>
 *   <li>the height every {@link #COARSE_STEP} blocks over the footprint, then over the ring (the staircase back to the
 *       natural terrain must fit inside the ring); a window that just misses is tried again moved by half a grid step;</li>
 *   <li>a closer look: the height every {@link #FINE_STEP} blocks, water, and every biome cell.</li>
 * </ol>
 * Good spots are ranked by how little earth would move. Real heights are read in a band around sea level (much
 * cheaper than the whole world's height), each at most once per tile, and several tiles are scanned at once on
 * low-priority threads. Features the noise can't see (lakes, ravines, trees, boulders) and the real heights are checked
 * later in the real world ({@link SiteCheck}).
 */
final class SiteSearch
{
    /** A height reading outside the band (or of nothing at all). Never passes a flatness test. */
    static final int OUT_OF_BAND = 1_000_000;
    /** Mixed into the world seed so the site's random spots are its own. */
    private static final long SEED_SALT = 0x5D20_55C4_7E0FL;
    /** The first full look at a spot's ground samples every this many blocks... */
    private static final int COARSE_STEP = 8;
    /** ...and the best spots get a closer look, every this many blocks. */
    private static final int FINE_STEP = 4;
    /** The best spots get every biome cell checked (biomes come in 4-block cells). */
    private static final int BIOME_STEP_FINE = 4;
    /** A scanned tile is this many blocks square... */
    private static final int REGION_SIZE = 512;
    /** ...sampled every this many blocks. */
    private static final int GRID = 16;
    /**
     * A window whose smooth terrain height varies by more than the allowed variation plus this many blocks is skipped
     * without reading real heights (the real terrain's small bumps can't make up that much).
     */
    private static final int SMOOTH_TOLERANCE = 5;
    /** A window that's flat on the grid but just fails the closer look is tried again moved by this many blocks each way. */
    private static final int NUDGE = GRID / 2;
    /** With the strict rules (lowland biomes only), heights are read from this many blocks below sea level... */
    private static final int BAND_BELOW = 24;
    /** ...to this many above it. A spot whose ground is outside the band is skipped. */
    private static final int BAND_ABOVE = 72;
    /** At most this many search threads (fewer on small machines: half the processors, at least one). */
    private static final int MAX_THREADS = 4;

    /**
     * A spot that passed.
     *
     * @param groundY   the planned ground level (the median terrain height under the footprint, per the noise)
     * @param variation highest minus lowest terrain height under the footprint, per the noise
     * @param score     how much earth would move (lower is better)
     * @param refined   true if it got the closer look
     */
    record Candidate(int x, int z, int groundY, int variation, double score, String biome, boolean refined)
    {
        String describe(int spawnX, int spawnZ)
        {
            return String.format(Locale.ROOT, "%d %d (%s, %d blocks from spawn, ground %d, variation %d, score %.0f%s)",
                    x, z, biome, Math.round(Math.sqrt((double) (x - spawnX) * (x - spawnX) + (double) (z - spawnZ) * (z - spawnZ))),
                    groundY, variation, score, refined ? "" : ", coarse look only");
        }
    }

    /**
     * What a search found, best first, and how many spots fell at each step.
     *
     * @param exhaustive true if every tile of the ring was scanned (searching again with the same rules finds nothing new)
     */
    record Result(List<Candidate> candidates, int regions, int tiles, int tried, int biomeOk, int smoothOk, int flatOk,
                  int villagesOk, int ringOk, int heights, int threads, long millis, boolean exhaustive, boolean cancelled)
    {
        String summary()
        {
            return "scanned " + regions + " of " + tiles + " tiles (" + tried + " spots) in " + millis + " ms on " + threads
                    + " thread(s): " + biomeOk + " all in allowed biomes, " + smoothOk + " gentle enough at a glance, " + flatOk
                    + " flat enough on the " + GRID + "-block grid, " + villagesOk + " clear of villages, " + ringOk
                    + " good all round (" + candidates.size() + " kept); " + heights + " terrain heights read"
                    + (exhaustive ? "; the whole ring was scanned" : "") + (cancelled ? " (stopped early)" : "");
        }

        static Result empty()
        {
            return new Result(List.of(), 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, false, false);
        }
    }

    /** Everything the search needs, read on the server thread; it only reads the generator, so it's safe on any thread. */
    static final class Context
    {
        final ChunkGenerator generator;
        final RandomState random;
        final ChunkGeneratorStructureState structures;
        final List<SiteVillages.VillageSet> villages;
        final long seed;
        final int spawnX;
        final int spawnZ;
        final int seaLevel;
        final SiteLayout layout;
        /** Spots already tried in the real world (rejected), which new spots keep away from. */
        final long[] avoid;
        /** The full world height, and the band around sea level that heights are read in with the strict rules. */
        final LevelHeightAccessor fullHeights;
        final LevelHeightAccessor band;
        final int bandMin;
        final int bandMax;

        private Context(ServerLevel level, SiteLayout layout, long[] avoid)
        {
            this.generator = level.getChunkSource().getGenerator();
            this.random = level.getChunkSource().randomState();
            this.structures = level.getChunkSource().getGeneratorState();
            this.villages = SiteVillages.villageSets(structures);
            this.seed = level.getSeed();
            BlockPos spawn = level.getSharedSpawnPos();
            this.spawnX = spawn.getX();
            this.spawnZ = spawn.getZ();
            this.seaLevel = level.getSeaLevel();
            this.layout = layout;
            this.avoid = avoid.clone();
            this.fullHeights = LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight());
            this.bandMin = Math.max(level.getMinBuildHeight(), Math.floorDiv(level.getSeaLevel() - BAND_BELOW, 8) * 8);
            this.bandMax = Math.min(level.getMaxBuildHeight(), Math.floorDiv(level.getSeaLevel() + BAND_ABOVE + 7, 8) * 8);
            this.band = LevelHeightAccessor.create(bandMin, bandMax - bandMin);
        }
    }

    /** Reads what the search needs. Server thread. */
    static Context capture(ServerLevel level, SiteLayout layout, long[] avoid)
    {
        return new Context(level, layout, avoid);
    }

    /** Reads heights and biomes for one thread, remembering heights so each column is read once. Not thread-safe. */
    private static final class Sampler
    {
        final Context ctx;
        final boolean fullBand;
        final Long2IntOpenHashMap grounds = new Long2IntOpenHashMap();
        final Long2IntOpenHashMap wets = new Long2IntOpenHashMap();
        final SiteVillages.Finder villages;
        int reads;

        Sampler(Context ctx, boolean fullBand)
        {
            this.ctx = ctx;
            this.fullBand = fullBand;
            this.villages = new SiteVillages.Finder(ctx.generator, ctx.random, ctx.structures, ctx.villages, ctx.fullHeights);
        }

        /** The terrain's top solid block at this column (water ignored), per the noise; {@link #OUT_OF_BAND} or more if outside the band. */
        int ground(int x, int z)
        {
            long key = BlockPos.asLong(x, 0, z);
            if (grounds.containsKey(key))
            {
                return grounds.get(key);
            }
            reads++;
            int y;
            if (fullBand)
            {
                y = ctx.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.fullHeights, ctx.random) - 1;
            }
            else
            {
                y = ctx.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.band, ctx.random) - 1;
                if (y >= ctx.bandMax - 2 || y <= ctx.bandMin)
                {
                    y = OUT_OF_BAND + (int) (key & 0xFFFF); // different values, so a window of them never looks flat
                }
            }
            grounds.put(key, y);
            return y;
        }

        /** True if the noise puts water (or lava) on top of the ground at this column. */
        boolean wet(int x, int z)
        {
            int ground = ground(x, z);
            long key = BlockPos.asLong(x, 0, z);
            if (wets.containsKey(key))
            {
                return wets.get(key) != 0;
            }
            reads++;
            int top = ctx.generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, fullBand ? ctx.fullHeights : ctx.band,
                    ctx.random) - 1;
            boolean wet = top > ground && ground < OUT_OF_BAND;
            wets.put(key, wet ? 1 : 0);
            return wet;
        }

        Holder<Biome> biome(int x, int y, int z)
        {
            return ctx.generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                    ctx.random.sampler());
        }

        /**
         * The smooth terrain height at this column: where the generator's large-scale "depth" (which falls steadily with
         * height) crosses zero, found from two readings. The real ground is this plus small bumps. If the generator has no
         * depth that changes with height (a flat world), it's just sea level, so no window is ever skipped on it.
         */
        float smooth(int x, int z)
        {
            DensityFunction depth = ctx.random.router().depth();
            int y0 = ctx.seaLevel;
            int y1 = ctx.seaLevel + 64;
            double d0 = depth.compute(new DensityFunction.SinglePointContext(x, y0, z));
            double d1 = depth.compute(new DensityFunction.SinglePointContext(x, y1, z));
            if (!(Math.abs(d0 - d1) > 1.0E-6D))
            {
                return y0;
            }
            return (float) (y0 + d0 * (y1 - y0) / (d0 - d1));
        }
    }

    /** What the threads share while a search runs. */
    private static final class Shared
    {
        final List<Candidate> found = new ArrayList<>();
        final AtomicInteger nextRegion = new AtomicInteger();
        final AtomicInteger regions = new AtomicInteger();
        final AtomicInteger tried = new AtomicInteger();
        final AtomicInteger biomeOk = new AtomicInteger();
        final AtomicInteger smoothOk = new AtomicInteger();
        final AtomicInteger flatOk = new AtomicInteger();
        final AtomicInteger villagesOk = new AtomicInteger();
        final AtomicInteger ringOk = new AtomicInteger();
        final AtomicInteger reads = new AtomicInteger();
        final AtomicBoolean stop = new AtomicBoolean();
        long deadline;
        /** The tiles' lowest corners, in scanning order. */
        List<long[]> tiles = List.of();

        synchronized boolean tooClose(int x, int z, long[] avoid)
        {
            return SiteSearch.tooClose(x, z, avoid, found);
        }

        synchronized int add(Candidate c)
        {
            found.add(c);
            return found.size();
        }

        synchronized int count()
        {
            return found.size();
        }
    }

    /**
     * Runs a search: tiles of the ring until {@link SiteRules#GOOD_SPOTS_WANTED} spots pass, every tile (at most
     * {@link SiteRules#MAX_SEARCH_REGIONS}) has been scanned or {@link SiteRules#MAX_SEARCH_MILLIS} have passed. Any thread; it starts a few helper threads of
     * its own and waits for them.
     *
     * @param searchNumber makes each search of a world try different spots
     */
    static Result run(Context ctx, SiteRules.Level rules, int searchNumber, BooleanSupplier cancelled)
    {
        long start = System.currentTimeMillis();
        boolean fullBand = rules.biomes() == null; // any dry land: heights over the whole world height
        Shared shared = new Shared();
        shared.deadline = start + SiteRules.MAX_SEARCH_MILLIS;
        shared.tiles = tiles(ctx, rules, searchNumber);
        int threads = Math.max(1, Math.min(MAX_THREADS, Runtime.getRuntime().availableProcessors() / 2));
        List<Thread> helpers = new ArrayList<>();
        for (int t = 1; t < threads; t++)
        {
            Thread thread = new Thread(() -> scanRegions(ctx, rules, searchNumber, cancelled, shared, fullBand),
                    "Dross portal site search " + (t + 1));
            thread.setDaemon(true);
            thread.setPriority(Thread.NORM_PRIORITY - 2);
            thread.start();
            helpers.add(thread);
        }
        scanRegions(ctx, rules, searchNumber, cancelled, shared, fullBand);
        for (Thread thread : helpers)
        {
            try
            {
                thread.join();
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
                shared.stop.set(true);
            }
        }
        boolean stopped = cancelled.getAsBoolean();

        List<Candidate> found = new ArrayList<>(shared.found);
        found.sort(Comparator.comparingDouble(Candidate::score));
        List<Candidate> ranked = found;
        boolean exhaustive = shared.regions.get() >= shared.tiles.size() && found.size() < SiteRules.GOOD_SPOTS_WANTED;
        return new Result(ranked, shared.regions.get(), shared.tiles.size(), shared.tried.get(), shared.biomeOk.get(),
                shared.smoothOk.get(), shared.flatOk.get(), shared.villagesOk.get(), shared.ringOk.get(),
                shared.reads.get(), threads, System.currentTimeMillis() - start, exhaustive, stopped);
    }

    /**
     * The tiles covering the ring around spawn, in a random order (from the world seed). They overlap by a window and its
     * ring, so every window position is tested in some tile.
     */
    private static List<long[]> tiles(Context ctx, SiteRules.Level rules, int searchNumber)
    {
        int ringCells = (rules.blendMargin() + GRID - 1) / GRID;
        int windowCells = ringCells + (Math.max(ctx.layout.width(), ctx.layout.depth()) - 1 + rules.blendMargin()) / GRID;
        int step = REGION_SIZE - (windowCells + 1) * GRID;
        Random rng = new Random(ctx.seed ^ SEED_SALT ^ (31L * searchNumber + rules.number()) * 0x9E3779B97F4A7C15L);
        int phaseX = rng.nextInt(step / GRID) * GRID;
        int phaseZ = rng.nextInt(step / GRID) * GRID;
        int reach = rules.maxDistance() + REGION_SIZE;
        List<long[]> tiles = new ArrayList<>();
        for (int ox = Math.floorDiv(ctx.spawnX - reach - phaseX, step) * step + phaseX; ox <= ctx.spawnX + reach; ox += step)
        {
            for (int oz = Math.floorDiv(ctx.spawnZ - reach - phaseZ, step) * step + phaseZ; oz <= ctx.spawnZ + reach; oz += step)
            {
                // Keep the tile if part of it is in the ring: its nearest point within the outer edge, its farthest beyond the inner.
                long nearX = Math.max(0, Math.max(ox - ctx.spawnX, ctx.spawnX - (ox + REGION_SIZE)));
                long nearZ = Math.max(0, Math.max(oz - ctx.spawnZ, ctx.spawnZ - (oz + REGION_SIZE)));
                long farX = Math.max(Math.abs(ox - ctx.spawnX), Math.abs(ox + REGION_SIZE - ctx.spawnX));
                long farZ = Math.max(Math.abs(oz - ctx.spawnZ), Math.abs(oz + REGION_SIZE - ctx.spawnZ));
                if (nearX * nearX + nearZ * nearZ <= (long) rules.maxDistance() * rules.maxDistance()
                        && farX * farX + farZ * farZ >= (long) rules.minDistance() * rules.minDistance())
                {
                    tiles.add(new long[] {ox, oz});
                }
            }
        }
        java.util.Collections.shuffle(tiles, rng);
        return tiles;
    }

    /** One thread's share: takes tiles one at a time until the search has enough, runs out of tiles or time, or is cancelled. */
    private static void scanRegions(Context ctx, SiteRules.Level rules, int searchNumber, BooleanSupplier cancelled, Shared shared,
                                    boolean fullBand)
    {
        try
        {
            while (!shared.stop.get())
            {
                if (cancelled.getAsBoolean() || System.currentTimeMillis() > shared.deadline
                        || shared.count() >= SiteRules.GOOD_SPOTS_WANTED)
                {
                    shared.stop.set(true);
                    return;
                }
                int tile = shared.nextRegion.getAndIncrement();
                if (tile >= shared.tiles.size() || tile >= SiteRules.MAX_SEARCH_REGIONS)
                {
                    return;
                }
                Sampler sampler = new Sampler(ctx, fullBand);
                long[] origin = shared.tiles.get(tile);
                scanRegion(ctx, rules, sampler, shared, cancelled, (int) origin[0], (int) origin[1]);
                shared.reads.addAndGet(sampler.reads);
                if (!shared.stop.get())
                {
                    shared.regions.incrementAndGet();
                }
            }
        }
        catch (RuntimeException e)
        {
            shared.stop.set(true);
            throw e;
        }
    }

    /** Scans one tile: every footprint-sized window on its grid. */
    private static void scanRegion(Context ctx, SiteRules.Level rules, Sampler sampler, Shared shared, BooleanSupplier cancelled,
                                   int originX, int originZ)
    {
        SiteLayout layout = ctx.layout;
        int n = REGION_SIZE / GRID + 1;
        // Grid points under the footprint and under its blending ring, relative to the footprint's lowest corner.
        int footW = (layout.width() - 1) / GRID;
        int footD = (layout.depth() - 1) / GRID;
        int ring = (rules.blendMargin() + GRID - 1) / GRID;
        int ringW = (layout.width() - 1 + rules.blendMargin()) / GRID;
        int ringD = (layout.depth() - 1 + rules.blendMargin()) / GRID;
        // Corners and the middle first: most windows fail on those.
        List<int[]> order = new ArrayList<>();
        int[][] first = {{0, 0}, {footW, footD}, {footW, 0}, {0, footD}, {footW / 2, footD / 2}};
        for (int[] p : first)
        {
            order.add(p);
        }
        for (int i = 0; i <= footW; i++)
        {
            for (int k = 0; k <= footD; k++)
            {
                boolean seen = false;
                for (int[] p : first)
                {
                    seen |= p[0] == i && p[1] == k;
                }
                if (!seen)
                {
                    order.add(new int[] {i, k});
                }
            }
        }

        byte[] biomeOkAt = new byte[n * n]; // 0 = not read yet, 1 = allowed, 2 = not
        float[] smoothAt = new float[n * n];
        Arrays.fill(smoothAt, Float.NaN);
        String[] biomeName = new String[n * n];
        long minD2 = (long) rules.minDistance() * rules.minDistance();
        long maxD2 = (long) rules.maxDistance() * rules.maxDistance();

        for (int i0 = ring; i0 + ringW < n; i0++)
        {
            for (int k0 = ring; k0 + ringD < n; k0++)
            {
                if (shared.stop.get() || cancelled.getAsBoolean())
                {
                    return;
                }
                int x = originX + i0 * GRID - layout.minDX();
                int z = originZ + k0 * GRID - layout.minDZ();
                long dx = x - ctx.spawnX;
                long dz = z - ctx.spawnZ;
                long d2 = dx * dx + dz * dz;
                if (d2 < minD2 || d2 > maxD2 || shared.tooClose(x, z, ctx.avoid))
                {
                    continue;
                }
                shared.tried.incrementAndGet();
                // 1. Every biome under the window and its ring.
                boolean allowed = true;
                for (int i = i0 - ring; allowed && i <= i0 + ringW; i++)
                {
                    for (int k = k0 - ring; allowed && k <= k0 + ringD; k++)
                    {
                        int index = i * n + k;
                        if (biomeOkAt[index] == 0)
                        {
                            Holder<Biome> biome = sampler.biome(originX + i * GRID, ctx.seaLevel + 1, originZ + k * GRID);
                            biomeOkAt[index] = (byte) (rules.allows(biome) ? 1 : 2);
                            biomeName[index] = name(biome);
                        }
                        allowed = biomeOkAt[index] == 1;
                    }
                }
                if (!allowed)
                {
                    continue;
                }
                shared.biomeOk.incrementAndGet();
                // 2. The smooth terrain height under the window, corners first: hilly windows stop here.
                float smoothLow = Float.MAX_VALUE;
                float smoothHigh = -Float.MAX_VALUE;
                for (int[] p : order)
                {
                    int index = (i0 + p[0]) * n + (k0 + p[1]);
                    if (Float.isNaN(smoothAt[index]))
                    {
                        smoothAt[index] = sampler.smooth(originX + (i0 + p[0]) * GRID, originZ + (k0 + p[1]) * GRID);
                    }
                    smoothLow = Math.min(smoothLow, smoothAt[index]);
                    smoothHigh = Math.max(smoothHigh, smoothAt[index]);
                    if (smoothHigh - smoothLow > rules.maxVariation() + SMOOTH_TOLERANCE)
                    {
                        break;
                    }
                }
                if (smoothHigh - smoothLow > rules.maxVariation() + SMOOTH_TOLERANCE)
                {
                    continue;
                }
                shared.smoothOk.incrementAndGet();
                // 3. The real heights on the grid under the window, corners first.
                int low = Integer.MAX_VALUE;
                int high = Integer.MIN_VALUE;
                for (int[] p : order)
                {
                    int h = sampler.ground(originX + (i0 + p[0]) * GRID, originZ + (k0 + p[1]) * GRID);
                    low = Math.min(low, h);
                    high = Math.max(high, h);
                    if (high - low > rules.maxVariation())
                    {
                        break;
                    }
                }
                if (high - low > rules.maxVariation())
                {
                    continue;
                }
                shared.flatOk.incrementAndGet();
                // 4. Villages, wherever they could be.
                BoundingBox footprint = layout.footprintAt(x, z, 0, 0);
                if (!sampler.villages.villagesNear(footprint.inflatedBy(rules.blendMargin()),
                        SiteRules.VILLAGE_REACH + SiteRules.VILLAGE_CLEARANCE).isEmpty())
                {
                    continue;
                }
                shared.villagesOk.incrementAndGet();
                // 5. The ground every COARSE_STEP blocks, then the ring; if it just misses, nudged half a grid step each way.
                Look look = look(sampler, rules, footprint, COARSE_STEP);
                if (look == null)
                {
                    nudges:
                    for (int nx = -NUDGE; nx <= NUDGE; nx += NUDGE)
                    {
                        for (int nz = -NUDGE; nz <= NUDGE; nz += NUDGE)
                        {
                            if (nx == 0 && nz == 0)
                            {
                                continue;
                            }
                            long ndx = x + nx - ctx.spawnX;
                            long ndz = z + nz - ctx.spawnZ;
                            long nd2 = ndx * ndx + ndz * ndz;
                            BoundingBox moved = layout.footprintAt(x + nx, z + nz, 0, 0);
                            if (nd2 < minD2 || nd2 > maxD2 || !sampler.villages.villagesNear(moved.inflatedBy(rules.blendMargin()),
                                    SiteRules.VILLAGE_REACH + SiteRules.VILLAGE_CLEARANCE).isEmpty())
                            {
                                continue;
                            }
                            look = look(sampler, rules, moved, COARSE_STEP);
                            if (look != null)
                            {
                                x += nx;
                                z += nz;
                                break nudges;
                            }
                        }
                    }
                }
                if (look == null)
                {
                    continue;
                }
                // 6. The closer look: the height every FINE_STEP blocks, water, and every biome cell.
                BoundingBox spot = layout.footprintAt(x, z, 0, 0);
                Look fine = look(sampler, rules, spot, FINE_STEP);
                if (fine == null || hasWater(sampler, spot.inflatedBy(SiteRules.WATER_CLEARANCE))
                        || !biomesAllowed(sampler, rules, spot.inflatedBy(rules.blendMargin()), fine.groundY + 1, BIOME_STEP_FINE))
                {
                    continue;
                }
                shared.ringOk.incrementAndGet();
                if (shared.tooClose(x, z, ctx.avoid))
                {
                    continue; // another thread found one right here meanwhile
                }
                if (shared.add(new Candidate(x, z, fine.groundY, fine.variation, fine.score, biomeName[i0 * n + k0], true))
                        >= SiteRules.GOOD_SPOTS_WANTED)
                {
                    shared.stop.set(true);
                    return;
                }
            }
        }
    }

    /** The ground under a footprint and its ring, sampled every {@code step} blocks: null if it fails the rules. */
    private record Look(int groundY, int variation, double score) {}

    @Nullable
    private static Look look(Sampler sampler, SiteRules.Level rules, BoundingBox footprint, int step)
    {
        int[] heights = sampleGrid(sampler, footprint, step, step);
        int variation = spread(heights);
        if (variation > rules.maxVariation())
        {
            return null;
        }
        int ground = median(heights);
        double footprintWork = 0;
        for (int h : heights)
        {
            footprintWork += Math.abs(h - ground);
        }
        footprintWork *= (double) footprint.getXSpan() * footprint.getZSpan() / heights.length;

        // The staircase: a column d blocks out goes to its natural height, but at most d from the ground level.
        int margin = rules.blendMargin();
        BoundingBox outer = footprint.inflatedBy(margin + step);
        double ringWork = 0;
        for (int x : axis(outer.minX(), outer.maxX(), step))
        {
            for (int z : axis(outer.minZ(), outer.maxZ(), step))
            {
                int d = GroundFit.distanceOutside(footprint, x, z);
                if (d == 0 || d > margin + step)
                {
                    continue;
                }
                int need = Math.abs(sampler.ground(x, z) - ground) - d;
                if (need > 0 && d >= margin && rules.staircaseRequired())
                {
                    return null; // the staircase wouldn't end inside the ring
                }
                if (need > rules.maxRingChange() && rules.staircaseRequired())
                {
                    return null; // too much earth to move next to the footprint
                }
                ringWork += Math.max(0, need);
            }
        }
        ringWork *= (double) step * step;
        return new Look(ground, variation, footprintWork + 2.0D * ringWork + 50.0D * variation);
    }

    /** Terrain heights over a box, every {@code stepX}/{@code stepZ} blocks, edges included. */
    private static int[] sampleGrid(Sampler sampler, BoundingBox box, int stepX, int stepZ)
    {
        int[] xs = axis(box.minX(), box.maxX(), stepX);
        int[] zs = axis(box.minZ(), box.maxZ(), stepZ);
        int[] heights = new int[xs.length * zs.length];
        int i = 0;
        for (int x : xs)
        {
            for (int z : zs)
            {
                heights[i++] = sampler.ground(x, z);
            }
        }
        return heights;
    }

    /** True if every biome over the box (sampled every {@code step} blocks, edges included) is allowed. */
    private static boolean biomesAllowed(Sampler sampler, SiteRules.Level rules, BoundingBox box, int y, int step)
    {
        for (int x : axis(box.minX(), box.maxX(), step))
        {
            for (int z : axis(box.minZ(), box.maxZ(), step))
            {
                if (!rules.allows(sampler.biome(x, y, z)))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** True if the noise puts water on the ground anywhere over the box (every {@link #COARSE_STEP} blocks). */
    private static boolean hasWater(Sampler sampler, BoundingBox box)
    {
        for (int x : axis(box.minX(), box.maxX(), COARSE_STEP))
        {
            for (int z : axis(box.minZ(), box.maxZ(), COARSE_STEP))
            {
                if (sampler.wet(x, z))
                {
                    return true;
                }
            }
        }
        return false;
    }

    /** {@code min}, {@code min + step}, ... and always {@code max}. */
    private static int[] axis(int min, int max, int step)
    {
        int count = (max - min) / step + 1;
        boolean extra = (max - min) % step != 0;
        int[] values = new int[count + (extra ? 1 : 0)];
        for (int i = 0; i < count; i++)
        {
            values[i] = min + i * step;
        }
        if (extra)
        {
            values[count] = max;
        }
        return values;
    }

    private static boolean tooClose(int x, int z, long[] avoid, List<Candidate> found)
    {
        long spacing = (long) SiteRules.SPOT_SPACING * SiteRules.SPOT_SPACING;
        for (long packed : avoid)
        {
            long dx = BlockPos.getX(packed) - x;
            long dz = BlockPos.getZ(packed) - z;
            if (dx * dx + dz * dz < spacing)
            {
                return true;
            }
        }
        for (Candidate c : found)
        {
            long dx = c.x() - x;
            long dz = c.z() - z;
            if (dx * dx + dz * dz < spacing)
            {
                return true;
            }
        }
        return false;
    }

    private static int spread(int[] values)
    {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int v : values)
        {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }
        return max - min;
    }

    private static int median(int[] values)
    {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted[sorted.length / 2];
    }

    static String name(Holder<Biome> biome)
    {
        return biome.unwrapKey().map(key -> key.location().toString()).orElse("unknown biome");
    }

    private SiteSearch() {}
}
