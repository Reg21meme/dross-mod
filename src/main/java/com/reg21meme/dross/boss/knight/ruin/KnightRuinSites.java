package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossWorldSettings;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.boss.knight.KnightRuinDesigns;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;
import java.util.function.Supplier;

/**
 * Where the knight ruins go in the Overworld (CLAUDE.md step 18; the user's rules, November 19, 2026), from the world's
 * seed alone: the generator's own noise (biomes, heights), never a chunk made. Deterministic and thread-safe: world
 * generation ({@link KnightRuinPlacement}, {@link KnightRuinStructure}), the keeper, the commands and the tests all get
 * the same answers, in any order.
 * <ul>
 *   <li><b>Biomes</b>: a ruin stands only in its element's biomes ({@link RuinAreas}: the user's lists), with that biome
 *       area's own layouts. The biome map is read a cell (16 blocks) at a time, at the land's own height, in tiles kept
 *       once read; joined cells of one area are a <i>patch</i>.</li>
 *   <li><b>Near the spawn</b>: {@code nearSpawn} (5) ruins between {@code nearSpawnMinDistance} (200) and
 *       {@code nearSpawnRadius} (1,000) blocks of it, each in its own big patch ({@link KnightRuinTuning#BIG_PATCH}),
 *       {@link KnightRuinTuning#EDGE} in from its edge: the spawn's own patch first if it's big, then the nearest;
 *       elements mixed where the land allows (a new element first); never two in touching patches; no thunder (no
 *       shores). Their ways in face the spawn.</li>
 *   <li><b>Further out</b>: the world in regions {@code spacing} (800) blocks a side, each with at most one candidate:
 *       a big land patch at least 43 blocks deep, or a shore (a beach, stony shore or snowy beach by the sea, a river's
 *       bank) for thunder and ice's frozen shore. A rare area's patch (badlands, dark forest, mushroom fields, jungle)
 *       is picked first, else the seed picks an element among those there, then an area of it, its biggest patch (each
 *       river segment and strip of beach is a patch of its own: picked outright, they took half the ruins). A candidate
 *       gives way to a higher-ranked one (rare first, then the seed's roll) of a region round it in the same patch (2
 *       regions out) or a touching one (1 out), or within
 *       {@link #MIN_APART} blocks, and to the near-spawn ruins. On the highest peaks 3 in 10 are the storm's.</li>
 *   <li><b>A spot</b>: a land ruin's ground is the median of the land's heights over its footprint (it may vary by
 *       {@link KnightRuinTuning#MAX_RELIEF}, its footprint at most a fifth under water); a shore ruin stands at the sea's
 *       level with its back (the template's sea side) over the water and its front on the land.</li>
 * </ul>
 */
public final class KnightRuinSites
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The structure set that places the ruins ({@code data/dross/worldgen/structure_set/knight_ruin.json}). */
    public static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(Dross.MODID, "knight_ruin"));

    /** The ruins' own salt (the seed's choices of patch, spot, way and layout). */
    private static final long SALT = 0x4B6E52756E733131L;
    /** A ruin gives way to a higher-ranked one nearer than this, whatever their biomes. */
    static final int MIN_APART = 300;
    /** Regions' candidates keep this much more than the near-spawn radius from the spawn. */
    static final int NEAR_GAP = 150;
    /** A candidate is checked against the near-spawn ruins within this many blocks of it (same or touching patch). */
    static final int NEAR_CHECK_REACH = 2400;
    /** A window labelled to tell whether two cells share a patch reaches this many cells past them. */
    static final int CONFLICT_MARGIN = 16;
    /** A shore patch needs at least this many cells (a speck of beach is no shore). */
    static final int SHORE_MIN_CELLS = 6;
    /** A shore cell needs the sea (or for a river, the land) within this many cells. */
    static final int SHORE_NEAR = 2;
    /** Deep enough for a land ruin: its footprint's edge {@link KnightRuinTuning#EDGE} in from its patch's. */
    static final int LAND_DEPTH = KnightRuinTuning.EDGE + KnightRuinTuning.HALF_RUIN;
    /** A near-spawn spot this deep is deep enough: past it the nearest to the spawn is taken. */
    static final int NEAR_GOOD_DEPTH = 80;
    /** On a mountain a ruin's ground may vary this much (it builds its own crags). */
    static final int MAX_RELIEF_MOUNTAIN = 22;
    /** A swamp's footprint may be this much under water (its pools). */
    static final double MAX_WET_SWAMP = 0.4D;
    /** A shore's waterline is looked for this far from its start, every {@link #SHORE_STEP} blocks. */
    static final int SHORE_MARCH = 60;
    static final int SHORE_STEP = 3;
    /** A region tries at most this many spots in all, and this many in one patch (each spot's heights cost a few ms). */
    static final int TRIES_PER_REGION = 10;
    static final int TRIES_PER_PATCH = 3;
    /** A rare area's patch is tried this many times (past the region's limit: a big one always gets its ruin if it can). */
    static final int TRIES_PER_RARE_PATCH = 8;
    /** A land patch's tries keep this many cells apart where they can. */
    static final int SPREAD_CELLS = 3;
    /** Near the spawn, if the big patches don't hold them all, a last pass takes patches this big and spots this deep. */
    static final int NEAR_RELAXED_PATCH = 20_000;
    static final int NEAR_RELAXED_DEPTH = 29;

    // ---------------------------------------------------------------------------------------------
    // The world's context
    // ---------------------------------------------------------------------------------------------

    /** What the work reads: the Overworld's generator and noise, its heights, the spawn and the world's settings. */
    record Context(ChunkGenerator generator, RandomState random, LevelHeightAccessor heights, int seaLevel, long seed,
                   int spawnX, int spawnZ, boolean available, int spacing, int nearCount, int nearRadius, int nearMin)
    {
        static Context of(ServerLevel overworld)
        {
            ChunkGenerator generator = overworld.getChunkSource().getGenerator();
            RandomState random = overworld.getChunkSource().randomState();
            boolean available = overworld.getChunkSource().getGeneratorState().possibleStructureSets().stream().anyMatch(s -> s.is(SET))
                    && overworld.getServer().getWorldData().worldGenOptions().generateStructures();
            ChunkPos spawn = new ChunkPos(random.sampler().findSpawnPosition());
            DrossWorldSettings.Values settings = DrossWorldSettings.of(overworld);
            int radius = settings.ruinsNearSpawnRadius();
            return new Context(generator, random, LevelHeightAccessor.create(overworld.getMinBuildHeight(), overworld.getHeight()),
                    overworld.getSeaLevel(), overworld.getSeed(), spawn.getMinBlockX() + 8, spawn.getMinBlockZ() + 8, available,
                    Math.max(300, settings.ruinSpacing()), Math.max(0, settings.ruinsNearSpawn()), radius,
                    Mth.clamp(settings.ruinsNearSpawnMin(), 0, radius - 100));
        }

        /** For the probe: another seed's world with this Overworld's generator and the default settings. */
        static Context forSeed(ServerLevel overworld, ChunkGenerator generator, RandomState random, long seed)
        {
            ChunkPos spawn = new ChunkPos(random.sampler().findSpawnPosition());
            int radius = com.reg21meme.dross.DrossConfig.DEFAULT_RUINS_NEAR_SPAWN_RADIUS;
            return new Context(generator, random, LevelHeightAccessor.create(overworld.getMinBuildHeight(), overworld.getHeight()),
                    overworld.getSeaLevel(), seed, spawn.getMinBlockX() + 8, spawn.getMinBlockZ() + 8, true,
                    com.reg21meme.dross.DrossConfig.DEFAULT_RUIN_SPACING, com.reg21meme.dross.DrossConfig.DEFAULT_RUINS_NEAR_SPAWN, radius,
                    com.reg21meme.dross.DrossConfig.DEFAULT_RUINS_NEAR_SPAWN_MIN);
        }

        double fromSpawn(int x, int z)
        {
            return Math.sqrt(Mth.square((double) (x - this.spawnX)) + Mth.square((double) (z - this.spawnZ)));
        }
    }

    /** A candidate: its site, the cell of its patch it stands for (global cell coordinates), its rank. */
    record Candidate(RuinSite site, int cellX, int cellZ, long rank, int regionX, int regionZ)
    {
        /** Higher first; ties (practically never) by region. */
        boolean outranks(Candidate o)
        {
            if (this.rank != o.rank)
            {
                return this.rank > o.rank;
            }
            return this.regionX != o.regionX ? this.regionX > o.regionX : this.regionZ > o.regionZ;
        }
    }

    /** One world's work, kept. */
    static final class World
    {
        final Context ctx;
        final ConcurrentHashMap<Long, FutureTask<byte[]>> tiles = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Long, FutureTask<Optional<Candidate>>> candidates = new ConcurrentHashMap<>();
        final ConcurrentHashMap<Long, FutureTask<Optional<RuinSite>>> decisions = new ConcurrentHashMap<>();
        final FutureTask<List<Candidate>> near;
        final Set<Long> queued = ConcurrentHashMap.newKeySet();
        final ExecutorService warmer;

        World(Context ctx)
        {
            this.ctx = ctx;
            this.near = new FutureTask<>(() -> safely(() -> planNear(this), List.of(), "the ruins near the spawn"));
            // (one background thread, gone when it's had nothing to do for half a minute: a closed world's goes)
            java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(1, 1, 30L,
                    java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "Dross knight ruins");
                t.setDaemon(true);
                return t;
            });
            pool.allowCoreThreadTimeOut(true);
            this.warmer = pool;
        }
    }

    /** Per world, by its random state (one per server level; weak, so a closed world's goes). Guarded by itself. */
    private static final Map<RandomState, World> WORLDS = new WeakHashMap<>();

    /** Remembers the Overworld and starts planning the ruins near its spawn (and the regions round it) in the background. */
    public static void register(ServerLevel overworld)
    {
        World world = world(overworld);
        if (world == null || !world.ctx.available())
        {
            return;
        }
        // (the ruins near the spawn on a thread of their own at normal priority: the spawn area's chunks wait for them when
        // a new world is made, while the far regions' low-priority thread may be starved by world generation)
        Thread near = new Thread(world.near, "Dross knight ruins near the spawn");
        near.setDaemon(true);
        near.start();
        int reach = world.ctx.nearRadius() + NEAR_GAP + world.ctx.spacing() * 2;
        queueAround(world, world.ctx.spawnX(), world.ctx.spawnZ(), reach);
    }

    @Nullable
    static World world(ServerLevel overworld)
    {
        RandomState random = overworld.getChunkSource().randomState();
        synchronized (WORLDS)
        {
            World known = WORLDS.get(random);
            if (known != null)
            {
                return known;
            }
            Context ctx;
            try
            {
                ctx = Context.of(overworld);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Knight ruins: couldn't read the Overworld's generator, so the world has none", e);
                return null;
            }
            World made = new World(ctx);
            WORLDS.put(random, made);
            if (!ctx.available())
            {
                LOGGER.info("Knight ruins: this world doesn't generate their structure set {} (Generate Structures off, or a "
                        + "superflat world's own list of structures), so it has none", SET.location());
            }
            else
            {
                LOGGER.info("Knight ruins: about {} blocks apart, {} within {} blocks of the spawn {} {} (none nearer than {})",
                        ctx.spacing(), ctx.nearCount(), ctx.nearRadius(), ctx.spawnX(), ctx.spawnZ(), ctx.nearMin());
            }
            return made;
        }
    }

    @Nullable
    private static World known(RandomState random)
    {
        synchronized (WORLDS)
        {
            return WORLDS.get(random);
        }
    }

    public static boolean available(ServerLevel overworld)
    {
        World w = world(overworld);
        return w != null && w.ctx.available();
    }

    // ---------------------------------------------------------------------------------------------
    // Questions
    // ---------------------------------------------------------------------------------------------

    /** True if a ruin's structure starts in this chunk (world generation's way in). */
    static boolean isStartChunk(RandomState random, int chunkX, int chunkZ)
    {
        return siteStartingIn(random, chunkX, chunkZ) != null;
    }

    /** The ruin whose structure starts in this chunk, if any. */
    @Nullable
    static RuinSite siteStartingIn(RandomState random, int chunkX, int chunkZ)
    {
        World w = known(random);
        if (w == null || !w.ctx.available())
        {
            return null;
        }
        int bx = (chunkX << 4) + 8;
        int bz = (chunkZ << 4) + 8;
        if (w.ctx.nearCount() > 0 && w.ctx.fromSpawn(bx, bz) <= w.ctx.nearRadius() + 32)
        {
            for (Candidate c : await(w.near, List.of()))
            {
                if (c.site().startChunk().x == chunkX && c.site().startChunk().z == chunkZ)
                {
                    return c.site();
                }
            }
        }
        int rx = Math.floorDiv(bx, w.ctx.spacing());
        int rz = Math.floorDiv(bz, w.ctx.spacing());
        Optional<Candidate> c = candidate(w, rx, rz);
        if (c.isEmpty() || c.get().site().startChunk().x != chunkX || c.get().site().startChunk().z != chunkZ)
        {
            return null;
        }
        return decision(w, rx, rz).orElse(null);
    }

    /** The ruins near the spawn (waits for them; normally long done). */
    public static List<RuinSite> nearSpawn(ServerLevel overworld)
    {
        World w = world(overworld);
        if (w == null || !w.ctx.available())
        {
            return List.of();
        }
        return await(w.near, List.<Candidate>of()).stream().map(Candidate::site).toList();
    }

    /** Every ruin within {@code radius} blocks of a spot (works the regions out if need be: off the server thread for big radii). */
    public static List<RuinSite> within(ServerLevel overworld, int x, int z, int radius)
    {
        World w = world(overworld);
        if (w == null || !w.ctx.available())
        {
            return List.of();
        }
        List<RuinSite> found = new ArrayList<>();
        for (RuinSite s : nearSpawn(overworld))
        {
            if (dist(s.x(), s.z(), x, z) <= radius)
            {
                found.add(s);
            }
        }
        int r = w.ctx.spacing();
        for (int rx = Math.floorDiv(x - radius, r); rx <= Math.floorDiv(x + radius, r); rx++)
        {
            for (int rz = Math.floorDiv(z - radius, r); rz <= Math.floorDiv(z + radius, r); rz++)
            {
                decision(w, rx, rz).filter(s -> dist(s.x(), s.z(), x, z) <= radius).ifPresent(found::add);
            }
        }
        found.sort(Comparator.comparingDouble(s -> dist(s.x(), s.z(), x, z)));
        return found;
    }

    /** The ruins within a few regions of a spot that are already worked out (never waits: for checks every few ticks);
     * the rest are queued for working out. */
    public static List<RuinSite> readyNear(ServerLevel overworld, int x, int z, int radius)
    {
        World w = world(overworld);
        if (w == null || !w.ctx.available())
        {
            return List.of();
        }
        List<RuinSite> found = new ArrayList<>();
        if (w.near.isDone())
        {
            for (Candidate c : await(w.near, List.of()))
            {
                if (dist(c.site().x(), c.site().z(), x, z) <= radius)
                {
                    found.add(c.site());
                }
            }
        }
        int r = w.ctx.spacing();
        for (int rx = Math.floorDiv(x - radius, r); rx <= Math.floorDiv(x + radius, r); rx++)
        {
            for (int rz = Math.floorDiv(z - radius, r); rz <= Math.floorDiv(z + radius, r); rz++)
            {
                FutureTask<Optional<RuinSite>> task = w.decisions.get(key(rx, rz));
                if (task != null && task.isDone())
                {
                    await(task, Optional.<RuinSite>empty()).filter(s -> dist(s.x(), s.z(), x, z) <= radius).ifPresent(found::add);
                }
                else
                {
                    queue(w, rx, rz);
                }
            }
        }
        return found;
    }

    /** The nearest ruin (of an element, or any) within {@code maxRadius} blocks, searching outwards a ring of regions at a time. */
    public static Optional<RuinSite> nearest(ServerLevel overworld, int x, int z, @Nullable KnightKind.Element element, int maxRadius)
    {
        World w = world(overworld);
        if (w == null || !w.ctx.available())
        {
            return Optional.empty();
        }
        RuinSite best = null;
        double bestDist = Double.MAX_VALUE;
        for (RuinSite s : nearSpawn(overworld))
        {
            double d = dist(s.x(), s.z(), x, z);
            if ((element == null || s.element() == element) && d < bestDist && d <= maxRadius)
            {
                best = s;
                bestDist = d;
            }
        }
        int r = w.ctx.spacing();
        int crx = Math.floorDiv(x, r);
        int crz = Math.floorDiv(z, r);
        int rings = maxRadius / r + 1;
        for (int ring = 0; ring <= rings; ring++)
        {
            // (a ring's nearest edge is (ring - 1) regions off: past the best found, stop)
            if (best != null && (ring - 1) * (double) r > bestDist)
            {
                break;
            }
            for (int dx = -ring; dx <= ring; dx++)
            {
                for (int dz = -ring; dz <= ring; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != ring)
                    {
                        continue;
                    }
                    Optional<RuinSite> s = decision(w, crx + dx, crz + dz);
                    if (s.isPresent() && (element == null || s.get().element() == element))
                    {
                        double d = dist(s.get().x(), s.get().z(), x, z);
                        if (d < bestDist && d <= maxRadius)
                        {
                            best = s.get();
                            bestDist = d;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** The ruin with this id (its middle column), or null if none is planned there. */
    @Nullable
    public static RuinSite byId(ServerLevel overworld, String id)
    {
        String[] parts = id.split(",");
        if (parts.length != 2)
        {
            return null;
        }
        try
        {
            int x = Integer.parseInt(parts[0].trim());
            int z = Integer.parseInt(parts[1].trim());
            for (RuinSite s : within(overworld, x, z, 2))
            {
                if (s.x() == x && s.z() == z)
                {
                    return s;
                }
            }
        }
        catch (NumberFormatException ignored)
        {
        }
        return null;
    }

    /** Queues the regions within {@code reach} blocks of a spot for working out in the background. */
    public static void warmAround(ServerLevel overworld, int x, int z, int reach)
    {
        World w = world(overworld);
        if (w != null && w.ctx.available())
        {
            queueAround(w, x, z, reach);
        }
    }

    private static void queueAround(World w, int x, int z, int reach)
    {
        int r = w.ctx.spacing();
        int crx = Math.floorDiv(x, r);
        int crz = Math.floorDiv(z, r);
        int n = reach / r + 1;
        // nearest first
        List<long[]> order = new ArrayList<>();
        for (int dx = -n; dx <= n; dx++)
        {
            for (int dz = -n; dz <= n; dz++)
            {
                order.add(new long[] {crx + dx, crz + dz, (long) dx * dx + (long) dz * dz});
            }
        }
        order.sort(Comparator.comparingLong(a -> a[2]));
        for (long[] o : order)
        {
            queue(w, (int) o[0], (int) o[1]);
        }
    }

    private static void queue(World w, int rx, int rz)
    {
        if (w.queued.add(key(rx, rz)))
        {
            w.warmer.execute(() -> decision(w, rx, rz));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The biome map: cells and tiles
    // ---------------------------------------------------------------------------------------------

    static long key(int a, int b)
    {
        return ((long) a << 32) ^ (b & 0xFFFFFFFFL);
    }

    /** A cell's code ({@link RuinAreas}); cells are 16 blocks, sampled at their middle. */
    static byte cell(World w, int cx, int cz)
    {
        int t = KnightRuinTuning.TILE;
        int tx = Math.floorDiv(cx, t);
        int tz = Math.floorDiv(cz, t);
        byte[] tile = tile(w, tx, tz);
        return tile[(cx - tx * t) * t + (cz - tz * t)];
    }

    private static byte[] tile(World w, int tx, int tz)
    {
        FutureTask<byte[]> task = w.tiles.computeIfAbsent(key(tx, tz), k -> new FutureTask<>(() -> readTile(w.ctx, tx, tz)));
        return await(task, new byte[KnightRuinTuning.TILE * KnightRuinTuning.TILE]);
    }

    /** Nanoseconds spent reading the biome map's tiles (for the probe). */
    static final java.util.concurrent.atomic.AtomicLong TILE_NANOS = new java.util.concurrent.atomic.AtomicLong();

    private static byte[] readTile(Context ctx, int tx, int tz)
    {
        long started = System.nanoTime();
        try
        {
            return readTileNow(ctx, tx, tz);
        }
        finally
        {
            TILE_NANOS.addAndGet(System.nanoTime() - started);
        }
    }

    private static byte[] readTileNow(Context ctx, int tx, int tz)
    {
        int t = KnightRuinTuning.TILE;
        int c = KnightRuinTuning.CELL;
        byte[] codes = new byte[t * t];
        DensityFunction depth = ctx.random().router().depth();
        for (int i = 0; i < t; i++)
        {
            for (int j = 0; j < t; j++)
            {
                int x = (tx * t + i) * c + c / 2;
                int z = (tz * t + j) * c + c / 2;
                int y = smooth(ctx, depth, x, z);
                codes[i * t + j] = RuinAreas.code(ctx.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x),
                        QuartPos.fromBlock(y), QuartPos.fromBlock(z), ctx.random().sampler()));
            }
        }
        return codes;
    }

    /** The land's smooth height (where the generator's "depth" crosses zero: {@code world/SiteFinder}'s way). */
    static int smooth(Context ctx, DensityFunction depth, int x, int z)
    {
        int y0 = ctx.seaLevel();
        int y1 = ctx.seaLevel() + 64;
        double d0 = depth.compute(new DensityFunction.SinglePointContext(x, y0, z));
        double d1 = depth.compute(new DensityFunction.SinglePointContext(x, y1, z));
        if (!(Math.abs(d0 - d1) > 1.0E-6D))
        {
            return y0;
        }
        int y = (int) Math.round(y0 + d0 * (y1 - y0) / (d0 - d1));
        return Mth.clamp(y, ctx.heights().getMinBuildHeight() + 8, ctx.heights().getMaxBuildHeight() - 8);
    }

    /** A rectangle of cells, its patches labelled, each cell's depth in its patch (blocks to its edge). */
    static final class Window
    {
        final int x0;
        final int z0;
        final int w;
        final int h;
        final byte[] code;
        final int[] label;
        final int[] depth;
        final int[] size;

        Window(World world, int x0, int z0, int w, int h)
        {
            this.x0 = x0;
            this.z0 = z0;
            this.w = w;
            this.h = h;
            this.code = new byte[w * h];
            for (int i = 0; i < w; i++)
            {
                for (int j = 0; j < h; j++)
                {
                    this.code[i * h + j] = cell(world, x0 + i, z0 + j);
                }
            }
            this.label = new int[w * h];
            int labels = 0;
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            List<Integer> sizes = new ArrayList<>();
            sizes.add(0);
            for (int s = 0; s < w * h; s++)
            {
                if (this.label[s] != 0 || this.code[s] == RuinAreas.NONE)
                {
                    continue;
                }
                labels++;
                byte base = RuinAreas.base(this.code[s]);
                int count = 0;
                this.label[s] = labels;
                queue.add(s);
                while (!queue.isEmpty())
                {
                    int q = queue.poll();
                    count++;
                    int qi = q / h;
                    int qj = q % h;
                    for (int d = 0; d < 4; d++)
                    {
                        int ni = qi + (d == 0 ? 1 : d == 1 ? -1 : 0);
                        int nj = qj + (d == 2 ? 1 : d == 3 ? -1 : 0);
                        if (ni < 0 || nj < 0 || ni >= w || nj >= h)
                        {
                            continue;
                        }
                        int n = ni * h + nj;
                        if (this.label[n] == 0 && this.code[n] != RuinAreas.NONE && RuinAreas.base(this.code[n]) == base)
                        {
                            this.label[n] = labels;
                            queue.add(n);
                        }
                    }
                }
                sizes.add(count);
            }
            this.size = sizes.stream().mapToInt(Integer::intValue).toArray();
            this.depth = depths();
        }

        /** Each cell's distance to the nearest cell of another patch (a chamfer pass each way, kept within its patch). */
        private int[] depths()
        {
            final int big = Integer.MAX_VALUE / 4;
            int[] d = new int[this.w * this.h];
            for (int i = 0; i < this.w; i++)
            {
                for (int j = 0; j < this.h; j++)
                {
                    int s = i * this.h + j;
                    d[s] = big;
                    for (int k = 0; k < 4; k++)
                    {
                        int ni = i + (k == 0 ? 1 : k == 1 ? -1 : 0);
                        int nj = j + (k == 2 ? 1 : k == 3 ? -1 : 0);
                        if (ni >= 0 && nj >= 0 && ni < this.w && nj < this.h && this.label[ni * this.h + nj] != this.label[s])
                        {
                            d[s] = 0;
                        }
                    }
                }
            }
            int[][] forward = {{-1, -1, 4}, {-1, 0, 3}, {-1, 1, 4}, {0, -1, 3}};
            int[][] backward = {{1, 1, 4}, {1, 0, 3}, {1, -1, 4}, {0, 1, 3}};
            for (int pass = 0; pass < 2; pass++)
            {
                int[][] kernel = pass == 0 ? forward : backward;
                for (int a = 0; a < this.w; a++)
                {
                    int i = pass == 0 ? a : this.w - 1 - a;
                    for (int b = 0; b < this.h; b++)
                    {
                        int j = pass == 0 ? b : this.h - 1 - b;
                        int s = i * this.h + j;
                        for (int[] k : kernel)
                        {
                            int ni = i + k[0];
                            int nj = j + k[1];
                            if (ni >= 0 && nj >= 0 && ni < this.w && nj < this.h && this.label[ni * this.h + nj] == this.label[s])
                            {
                                d[s] = Math.min(d[s], d[ni * this.h + nj] + k[2]);
                            }
                        }
                    }
                }
            }
            int cell = KnightRuinTuning.CELL;
            for (int s = 0; s < d.length; s++)
            {
                // (an edge cell's middle is half a cell from its edge; a chamfer step of 3 is a cell)
                d[s] = d[s] >= big ? 10_000 : cell / 2 + d[s] * cell / 3;
            }
            return d;
        }

        int index(int cx, int cz)
        {
            int i = cx - this.x0;
            int j = cz - this.z0;
            return i < 0 || j < 0 || i >= this.w || j >= this.h ? -1 : i * this.h + j;
        }

        int cellX(int s)
        {
            return this.x0 + s / this.h;
        }

        int cellZ(int s)
        {
            return this.z0 + s % this.h;
        }

        /** The block in a cell's middle. */
        int blockX(int s)
        {
            return this.cellX(s) * KnightRuinTuning.CELL + KnightRuinTuning.CELL / 2;
        }

        int blockZ(int s)
        {
            return this.cellZ(s) * KnightRuinTuning.CELL + KnightRuinTuning.CELL / 2;
        }

        int area(int s)
        {
            return this.size[this.label[s]] * KnightRuinTuning.CELL * KnightRuinTuning.CELL;
        }

        /** True if a cell of patch {@code a} is beside a cell of patch {@code b}. */
        boolean touching(int a, int b)
        {
            for (int i = 0; i < this.w; i++)
            {
                for (int j = 0; j < this.h; j++)
                {
                    if (this.label[i * this.h + j] != a)
                    {
                        continue;
                    }
                    if ((i + 1 < this.w && this.label[(i + 1) * this.h + j] == b) || (i > 0 && this.label[(i - 1) * this.h + j] == b)
                            || (j + 1 < this.h && this.label[i * this.h + j + 1] == b) || (j > 0 && this.label[i * this.h + j - 1] == b))
                    {
                        return true;
                    }
                }
            }
            return false;
        }

        /** True if a cell has a cell matching {@code test} within {@code r} cells. */
        boolean near(int s, int r, java.util.function.IntPredicate test)
        {
            int i0 = s / this.h;
            int j0 = s % this.h;
            for (int i = Math.max(0, i0 - r); i <= Math.min(this.w - 1, i0 + r); i++)
            {
                for (int j = Math.max(0, j0 - r); j <= Math.min(this.h - 1, j0 + r); j++)
                {
                    if (test.test(i * this.h + j))
                    {
                        return true;
                    }
                }
            }
            return false;
        }
    }

    /** A window of cells covering the blocks x0..x1, z0..z1 (and {@code margin} cells more). */
    static Window window(World w, int x0, int z0, int x1, int z1, int margin)
    {
        int c = KnightRuinTuning.CELL;
        int cx0 = Math.floorDiv(x0, c) - margin;
        int cz0 = Math.floorDiv(z0, c) - margin;
        int cx1 = Math.floorDiv(x1, c) + margin;
        int cz1 = Math.floorDiv(z1, c) + margin;
        return new Window(w, cx0, cz0, cx1 - cx0 + 1, cz1 - cz0 + 1);
    }

    // ---------------------------------------------------------------------------------------------
    // Near the spawn
    // ---------------------------------------------------------------------------------------------

    static List<Candidate> planNear(World w)
    {
        Context ctx = w.ctx;
        if (ctx.nearCount() <= 0)
        {
            return List.of();
        }
        long start = System.nanoTime();
        int reach = ctx.nearRadius() + KnightRuinTuning.NEAR_WINDOW_PAD;
        Window win = window(w, ctx.spawnX() - reach, ctx.spawnZ() - reach, ctx.spawnX() + reach, ctx.spawnZ() + reach, 0);
        int spawnCell = win.index(Math.floorDiv(ctx.spawnX(), KnightRuinTuning.CELL), Math.floorDiv(ctx.spawnZ(), KnightRuinTuning.CELL));
        int spawnLabel = spawnCell < 0 ? 0 : win.label[spawnCell];
        // each big land patch's spots, deep enough and in the ring round the spawn (and, for the last pass if they haven't
        // all been found, smaller patches' and shallower spots: NEAR_RELAXED_*)
        Map<Integer, List<Integer>> spots = new HashMap<>();
        Map<Integer, List<Integer>> relaxed = new HashMap<>();
        for (int s = 0; s < win.code.length; s++)
        {
            if (!RuinAreas.isLand(win.code[s]) || win.depth[s] < NEAR_RELAXED_DEPTH || win.area(s) < NEAR_RELAXED_PATCH)
            {
                continue;
            }
            boolean strict = win.depth[s] >= LAND_DEPTH && win.area(s) >= KnightRuinTuning.BIG_PATCH;
            double d = ctx.fromSpawn(win.blockX(s), win.blockZ(s));
            if (d < ctx.nearMin() || d > ctx.nearRadius())
            {
                continue;
            }
            relaxed.computeIfAbsent(win.label[s], k -> new ArrayList<>()).add(s);
            if (strict)
            {
                spots.computeIfAbsent(win.label[s], k -> new ArrayList<>()).add(s);
            }
        }
        Comparator<Integer> best = Comparator.<Integer>comparingInt(s -> -Math.min(win.depth[s], NEAR_GOOD_DEPTH))
                .thenComparingDouble(s -> ctx.fromSpawn(win.blockX(s), win.blockZ(s)));
        spots.values().forEach(list -> list.sort(best));
        relaxed.values().forEach(list -> list.sort(best));
        List<Integer> patches = order(spots, spawnLabel, win, ctx);
        List<Integer> relaxedPatches = order(relaxed, spawnLabel, win, ctx);
        List<Candidate> picked = new ArrayList<>();
        List<Integer> pickedLabels = new ArrayList<>();
        Set<KnightKind.Element> elements = new java.util.HashSet<>();
        RandomSource rng = random(ctx, 0x6E656172L, 0, 0);
        for (int pass = 0; pass < 3 && picked.size() < ctx.nearCount(); pass++)
        {
            Map<Integer, List<Integer>> pool = pass < 2 ? spots : relaxed;
            for (int label : pass < 2 ? patches : relaxedPatches)
            {
                if (picked.size() >= ctx.nearCount())
                {
                    break;
                }
                if (pickedLabels.contains(label) || pickedLabels.stream().anyMatch(p -> win.touching(p, label)))
                {
                    continue;
                }
                KnightRuinDesigns.Area area = RuinAreas.area(win.code[pool.get(label).get(0)]);
                if (area == null || (pass == 0 && elements.contains(area.element())))
                {
                    continue;
                }
                List<Integer> list = pool.get(label);
                for (int t = 0; t < Math.min(KnightRuinTuning.SPOT_TRIES, list.size()); t++)
                {
                    int s = list.get(t);
                    int x = win.blockX(s);
                    int z = win.blockZ(s);
                    if (picked.stream().anyMatch(c -> dist(c.site().x(), c.site().z(), x, z) < MIN_APART))
                    {
                        continue;
                    }
                    Direction front = towards(x, z, ctx.spawnX(), ctx.spawnZ());
                    RuinSite site = resolveLand(ctx, x, z, area, front, true, rng);
                    if (site != null)
                    {
                        picked.add(new Candidate(site, win.cellX(s), win.cellZ(s), Long.MAX_VALUE, Integer.MIN_VALUE, picked.size()));
                        pickedLabels.add(label);
                        elements.add(area.element());
                        break;
                    }
                }
            }
        }
        LOGGER.info("Knight ruins near the spawn ({} ms): {}", (System.nanoTime() - start) / 1_000_000L,
                picked.isEmpty() ? "none (no big patch of their biomes there)" : String.join("; ", picked.stream().map(c -> c.site().describe()
                        + " " + (int) ctx.fromSpawn(c.site().x(), c.site().z()) + " blocks out").toList()));
        return picked;
    }

    /** Patches in the near-spawn order: the spawn's own first, then by their best spot's distance from the spawn. */
    private static List<Integer> order(Map<Integer, List<Integer>> spots, int spawnLabel, Window win, Context ctx)
    {
        List<Integer> patches = new ArrayList<>(spots.keySet());
        patches.sort(Comparator.<Integer>comparingInt(l -> l == spawnLabel ? 0 : 1)
                .thenComparingDouble(l -> ctx.fromSpawn(win.blockX(spots.get(l).get(0)), win.blockZ(spots.get(l).get(0))))
                .thenComparingInt(l -> l));
        return patches;
    }

    // ---------------------------------------------------------------------------------------------
    // Regions
    // ---------------------------------------------------------------------------------------------

    static Optional<Candidate> candidate(World w, int rx, int rz)
    {
        FutureTask<Optional<Candidate>> task = w.candidates.computeIfAbsent(key(rx, rz),
                k -> new FutureTask<>(() -> safely(() -> findCandidate(w, rx, rz), Optional.empty(), "region " + rx + ", " + rz)));
        return await(task, Optional.empty());
    }

    static Optional<RuinSite> decision(World w, int rx, int rz)
    {
        FutureTask<Optional<RuinSite>> task = w.decisions.computeIfAbsent(key(rx, rz),
                k -> new FutureTask<>(() -> safely(() -> decide(w, rx, rz), Optional.empty(), "region " + rx + ", " + rz)));
        return await(task, Optional.empty());
    }

    /** A region's one candidate (or none): the patch the seed picks (a rare one first) and a spot in it that passes. */
    private static Optional<Candidate> findCandidate(World w, int rx, int rz)
    {
        Context ctx = w.ctx;
        int r = ctx.spacing();
        int bx0 = rx * r;
        int bz0 = rz * r;
        // (a region wholly in the near-spawn ring's reach has nothing)
        double farthest = 0;
        for (int corner = 0; corner < 4; corner++)
        {
            farthest = Math.max(farthest, ctx.fromSpawn(bx0 + (corner & 1) * (r - 1), bz0 + (corner >> 1) * (r - 1)));
        }
        int exclusion = (ctx.nearCount() > 0 ? ctx.nearRadius() + NEAR_GAP : ctx.nearMin());
        if (farthest < exclusion)
        {
            return Optional.empty();
        }
        int marginCells = KnightRuinTuning.REGION_MARGIN / KnightRuinTuning.CELL;
        Window win = window(w, bx0, bz0, bx0 + r - 1, bz0 + r - 1, marginCells);
        Map<Integer, List<Integer>> land = new HashMap<>();
        Map<Integer, List<Integer>> shore = new HashMap<>();
        for (int s = 0; s < win.code.length; s++)
        {
            int x = win.blockX(s);
            int z = win.blockZ(s);
            if (x < bx0 || x >= bx0 + r || z < bz0 || z >= bz0 + r || ctx.fromSpawn(x, z) < exclusion)
            {
                continue;
            }
            byte code = win.code[s];
            if (RuinAreas.isLand(code))
            {
                if (win.depth[s] >= LAND_DEPTH && win.area(s) >= KnightRuinTuning.BIG_PATCH)
                {
                    land.computeIfAbsent(win.label[s], k -> new ArrayList<>()).add(s);
                }
            }
            else if (RuinAreas.isShore(code) && win.size[win.label[s]] >= SHORE_MIN_CELLS && shoreCell(win, s))
            {
                shore.computeIfAbsent(win.label[s], k -> new ArrayList<>()).add(s);
            }
        }
        if (land.isEmpty() && shore.isEmpty())
        {
            return Optional.empty();
        }
        RandomSource rng = random(ctx, 0x72656769L, rx, rz);
        long rankRoll = random(ctx, 0x72616E6BL, rx, rz).nextLong() >>> 2;
        // What's tried, in order: a rare area's patches first; then an element the seed picks among those here, an area
        // of it, its patches biggest first; then the next element... (Picking a patch outright, a region of plains, a
        // forest and a river was mostly the river's: each river segment and strip of beach is a patch of its own.)
        List<Option> options = new ArrayList<>();
        land.keySet().stream().sorted().forEach(l -> options.add(new Option(l, false, win.code[land.get(l).get(0)], win.size[l])));
        shore.keySet().stream().sorted().forEach(l -> options.add(new Option(l, true, win.code[shore.get(l).get(0)], win.size[l])));
        List<Option> order = new ArrayList<>();
        List<Option> rare = new ArrayList<>(options.stream().filter(Option::rare).toList());
        shuffle(rare, rng);
        order.addAll(rare);
        Map<KnightKind.Element, Map<String, List<Option>>> byElement = new java.util.TreeMap<>();
        for (Option o : options)
        {
            if (!o.rare())
            {
                byElement.computeIfAbsent(o.element(), e -> new java.util.TreeMap<>()).computeIfAbsent(o.areaKey(), a -> new ArrayList<>()).add(o);
            }
        }
        List<KnightKind.Element> elements = new ArrayList<>(byElement.keySet());
        shuffle(elements, rng);
        for (KnightKind.Element e : elements)
        {
            List<String> areas = new ArrayList<>(byElement.get(e).keySet());
            shuffle(areas, rng);
            for (String a : areas)
            {
                List<Option> patches = byElement.get(e).get(a);
                patches.sort(Comparator.<Option>comparingInt(o -> -o.size()).thenComparingInt(Option::label));
                order.addAll(patches);
            }
        }
        int tries = 0;
        for (Option o : order)
        {
            List<Integer> cells = new ArrayList<>(o.shore() ? shore.get(o.label()) : land.get(o.label()));
            if (o.shore())
            {
                shuffle(cells, rng);
            }
            else
            {
                cells.sort(Comparator.<Integer>comparingInt(s -> -win.depth[s]).thenComparingInt(s -> s));
                cells = spread(win, cells);
            }
            int most = o.rare() ? TRIES_PER_RARE_PATCH : TRIES_PER_PATCH;
            for (int t = 0; t < Math.min(most, cells.size()) && (tries < TRIES_PER_REGION || o.rare()); t++, tries++)
            {
                int s = cells.get(t);
                RuinSite site = o.shore() ? resolveShore(ctx, win, s, rng) : resolveLandCell(ctx, win, s, rng);
                if (site != null && site.x() >= bx0 && site.x() < bx0 + r && site.z() >= bz0 && site.z() < bz0 + r
                        && ctx.fromSpawn(site.x(), site.z()) >= exclusion)
                {
                    long rank = (o.rare() ? 1L << 62 : 0L) | rankRoll;
                    return Optional.of(new Candidate(site, win.cellX(s), win.cellZ(s), rank, rx, rz));
                }
            }
            if (tries >= TRIES_PER_REGION && !o.rare())
            {
                break;
            }
        }
        return Optional.empty();
    }

    /** A patch's cells (best first) with the ones within {@link #SPREAD_CELLS} of a better one moved to the end, so its
     * tries aren't all on the same hill. */
    private static List<Integer> spread(Window win, List<Integer> cells)
    {
        List<Integer> first = new ArrayList<>();
        List<Integer> rest = new ArrayList<>();
        for (int s : cells)
        {
            boolean near = false;
            for (int f : first)
            {
                if (Math.abs(win.cellX(f) - win.cellX(s)) <= SPREAD_CELLS && Math.abs(win.cellZ(f) - win.cellZ(s)) <= SPREAD_CELLS)
                {
                    near = true;
                    break;
                }
            }
            (near ? rest : first).add(s);
        }
        first.addAll(rest);
        return first;
    }

    /** One patch a region may try: its label, whether it's a shore, its code, its size in cells. */
    private record Option(int label, boolean shore, byte code, int size)
    {
        @Nullable
        KnightRuinDesigns.Area area()
        {
            return RuinAreas.area(this.code);
        }

        boolean rare()
        {
            return !this.shore && KnightRuinTuning.RARE.contains(this.area());
        }

        /** A beach's element is thunder (a warm or cold shore by its sea). */
        KnightKind.Element element()
        {
            KnightRuinDesigns.Area a = this.area();
            return a == null ? KnightKind.Element.THUNDER : a.element();
        }

        String areaKey()
        {
            KnightRuinDesigns.Area a = this.area();
            return a == null ? "beach" : a.id() + (RuinAreas.isStorm(this.code) ? "_storm" : "");
        }
    }

    /** A shore cell by its water: a beach-like one with the sea within {@link #SHORE_NEAR} cells, a river's with land. */
    private static boolean shoreCell(Window win, int s)
    {
        byte code = win.code[s];
        if (RuinAreas.isRiver(code))
        {
            return win.near(s, SHORE_NEAR, n -> !RuinAreas.isRiver(win.code[n]) && !RuinAreas.isOpenWater(win.code[n]));
        }
        return win.near(s, SHORE_NEAR, n -> RuinAreas.isOpenWater(win.code[n]));
    }

    /** Whether a region's candidate stands: it gives way to the near-spawn ruins and to higher-ranked neighbours'. */
    private static Optional<RuinSite> decide(World w, int rx, int rz)
    {
        Optional<Candidate> mine = candidate(w, rx, rz);
        if (mine.isEmpty())
        {
            return Optional.empty();
        }
        Candidate c = mine.get();
        if (w.ctx.nearCount() > 0 && w.ctx.fromSpawn(c.site().x(), c.site().z()) < w.ctx.nearRadius() + NEAR_CHECK_REACH)
        {
            for (Candidate n : await(w.near, List.<Candidate>of()))
            {
                if (clash(w, n, c, true))
                {
                    return Optional.empty();
                }
            }
        }
        for (int dx = -2; dx <= 2; dx++)
        {
            for (int dz = -2; dz <= 2; dz++)
            {
                if (dx == 0 && dz == 0)
                {
                    continue;
                }
                Optional<Candidate> other = candidate(w, rx + dx, rz + dz);
                if (other.isPresent() && other.get().outranks(c) && clash(w, other.get(), c, Math.abs(dx) <= 1 && Math.abs(dz) <= 1))
                {
                    return Optional.empty();
                }
            }
        }
        return Optional.of(c.site());
    }

    /** True if two candidates may not both stand: too near, in one patch, or (if {@code touch}) in touching patches. */
    private static boolean clash(World w, Candidate a, Candidate b, boolean touch)
    {
        double d = dist(a.site().x(), a.site().z(), b.site().x(), b.site().z());
        if (d < MIN_APART)
        {
            return true;
        }
        int c = KnightRuinTuning.CELL;
        Window win = window(w, Math.min(a.cellX(), b.cellX()) * c, Math.min(a.cellZ(), b.cellZ()) * c,
                Math.max(a.cellX(), b.cellX()) * c, Math.max(a.cellZ(), b.cellZ()) * c, CONFLICT_MARGIN);
        int la = win.label[win.index(a.cellX(), a.cellZ())];
        int lb = win.label[win.index(b.cellX(), b.cellZ())];
        return la == lb || (touch && win.touching(la, lb));
    }

    // ---------------------------------------------------------------------------------------------
    // Spots: the land's heights
    // ---------------------------------------------------------------------------------------------

    /** A land cell's spot: its area (3 in 10 of the highest peaks the storm's), a seeded way in. */
    private static RuinSite resolveLandCell(Context ctx, Window win, int s, RandomSource rng)
    {
        byte code = win.code[s];
        KnightRuinDesigns.Area area = RuinAreas.area(code);
        if (area == null)
        {
            return null;
        }
        if (area == KnightRuinDesigns.EARTH_PEAKS && RuinAreas.isStorm(code) && rng.nextDouble() < KnightRuinTuning.STORM_PEAKS_SHARE)
        {
            area = KnightRuinDesigns.THUNDER_MOUNTAINS;
        }
        Direction front = Direction.Plane.HORIZONTAL.getRandomDirection(rng);
        return resolveLand(ctx, win.blockX(s), win.blockZ(s), area, front, false, rng);
    }

    /** A land ruin at a column: its ground the median of the land's heights over its footprint, if the land is even and dry enough. */
    @Nullable
    static RuinSite resolveLand(Context ctx, int x, int z, KnightRuinDesigns.Area area, Direction front, boolean near, RandomSource rng)
    {
        KnightRuinDesigns.Design design = pick(area, rng);
        if (design == null)
        {
            return null;
        }
        int half = Math.max(design.width(), design.depth()) / 2;
        int step = KnightRuinTuning.GROUND_STEP;
        LevelHeightAccessor band = band(ctx, area.setting());
        List<Integer> heights = new ArrayList<>();
        int wet = 0;
        int samples = 0;
        for (int dx = -half; dx <= half; dx += step)
        {
            for (int dz = -half; dz <= half; dz += step)
            {
                int[] col = column(ctx, x + dx, z + dz, band);
                samples++;
                if (col[2] == 1)
                {
                    return null; // (higher than its band: a cliff or a peak)
                }
                if (col[1] == 1)
                {
                    wet++;
                }
                else
                {
                    heights.add(col[0]);
                }
            }
        }
        double maxWet = area == KnightRuinDesigns.POISON_SWAMP ? MAX_WET_SWAMP : KnightRuinTuning.MAX_WET;
        if (heights.isEmpty() || wet > samples * maxWet)
        {
            return null;
        }
        heights.sort(Integer::compare);
        int relief = heights.get(heights.size() - 1) - heights.get(0);
        int maxRelief = area.setting() == KnightRuinDesigns.Setting.MOUNTAIN ? MAX_RELIEF_MOUNTAIN : KnightRuinTuning.MAX_RELIEF;
        if (relief > maxRelief)
        {
            return null;
        }
        int ground = Math.max(heights.get(heights.size() / 2), ctx.seaLevel() - 1);
        if (!fits(ctx, design, ground))
        {
            return null;
        }
        return new RuinSite(x, z, ground, front, design, near);
    }

    /**
     * A shore ruin from a shore cell: the water's side worked out from the cells round it (a beach's sea, a river's own
     * water), the waterline found along that way from the land, and the ruin stood with its template's shore line on it,
     * at the sea's level, its back over the water. Its land side must be mostly dry and its sea side mostly wet.
     */
    @Nullable
    private static RuinSite resolveShore(Context ctx, Window win, int s, RandomSource rng)
    {
        byte code = win.code[s];
        boolean river = RuinAreas.isRiver(code);
        // the way to the water, from the cells round it
        double vx = 0;
        double vz = 0;
        int warm = 0;
        int sea = 0;
        int i0 = s / win.h;
        int j0 = s % win.h;
        for (int i = Math.max(0, i0 - 3); i <= Math.min(win.w - 1, i0 + 3); i++)
        {
            for (int j = Math.max(0, j0 - 3); j <= Math.min(win.h - 1, j0 + 3); j++)
            {
                byte c = win.code[i * win.h + j];
                if (river)
                {
                    // (land round a river: the water is away from it)
                    if (!RuinAreas.isRiver(c) && !RuinAreas.isOpenWater(c))
                    {
                        vx -= i - i0;
                        vz -= j - j0;
                    }
                }
                else if (RuinAreas.isOpenWater(c))
                {
                    vx += i - i0;
                    vz += j - j0;
                    sea++;
                    if (c == RuinAreas.WARM)
                    {
                        warm++;
                    }
                }
            }
        }
        if (Math.abs(vx) < 1.0E-6D && Math.abs(vz) < 1.0E-6D)
        {
            return null;
        }
        Direction toWater = Math.abs(vx) >= Math.abs(vz) ? (vx > 0 ? Direction.EAST : Direction.WEST) : (vz > 0 ? Direction.SOUTH : Direction.NORTH);
        KnightRuinDesigns.Area area;
        if (code == RuinAreas.BEACH)
        {
            area = warm * 2 > sea ? KnightRuinDesigns.THUNDER_WARM_BEACH : KnightRuinDesigns.THUNDER_COLD_BEACH;
        }
        else
        {
            area = RuinAreas.area(code);
        }
        if (area == null)
        {
            return null;
        }
        KnightRuinDesigns.Design design = pick(area, rng);
        if (design == null)
        {
            return null;
        }
        int sx = toWater.getStepX();
        int sz = toWater.getStepZ();
        // from the land towards the water: the first wet column after a dry one is the waterline
        int x = win.blockX(s) - sx * KnightRuinTuning.SHORE_LOOK;
        int z = win.blockZ(s) - sz * KnightRuinTuning.SHORE_LOOK;
        int[] wx = null;
        boolean dry = false;
        LevelHeightAccessor band = band(ctx, KnightRuinDesigns.Setting.SHORE);
        for (int t = 0; t <= SHORE_MARCH; t += SHORE_STEP)
        {
            int[] col = column(ctx, x + sx * t, z + sz * t, band);
            if (col[1] == 0)
            {
                dry = true;
            }
            else if (dry)
            {
                wx = new int[] {x + sx * t, z + sz * t};
                break;
            }
        }
        if (wx == null)
        {
            return null;
        }
        int ground = ctx.seaLevel() - 1;
        int depth = design.depth();
        int back = depth / 2 - KnightRuinDesigns.shoreLine(depth); // the middle is this far from the waterline, inland
        int cx = wx[0] - sx * back;
        int cz = wx[1] - sz * back;
        Direction front = toWater.getOpposite();
        // its land side mostly dry and not much over the sea, its sea side mostly wet
        int half = Math.max(design.width(), depth) / 2;
        int landDry = 0;
        int landN = 0;
        int seaWet = 0;
        int seaN = 0;
        int highest = Integer.MIN_VALUE;
        for (int across = -half; across <= half; across += 6)
        {
            for (int along = -depth / 2; along <= depth / 2; along += 4)
            {
                // ("along" from the back (the sea's side) to the front)
                int px = cx + front.getStepX() * along + toWater.getClockWise().getStepX() * across;
                int pz = cz + front.getStepZ() * along + toWater.getClockWise().getStepZ() * across;
                int[] col = column(ctx, px, pz, band);
                if (along + depth / 2 < KnightRuinDesigns.shoreLine(depth))
                {
                    seaN++;
                    seaWet += col[1];
                }
                else
                {
                    landN++;
                    if (col[1] == 0)
                    {
                        landDry++;
                        highest = Math.max(highest, col[0]);
                    }
                }
            }
        }
        if (landDry < landN * KnightRuinTuning.SHORE_SIDE_SHARE || seaWet < seaN * KnightRuinTuning.SHORE_SIDE_SHARE
                || highest - ground > KnightRuinTuning.MAX_RELIEF || !fits(ctx, design, ground))
        {
            return null;
        }
        return new RuinSite(cx, cz, ground, front, design, false);
    }

    /**
     * {ground's top block, 1 if the sea (or a river) stands over it, 1 if it's at the band's top (too high to tell)}: the
     * generator's own heights (no chunk made), worked out only over a band of heights (the cost goes with its height).
     * Water is the sea's: ground under its level (a lake over it isn't seen; the piece fills one).
     */
    static int[] column(Context ctx, int x, int z, LevelHeightAccessor band)
    {
        HEIGHT_READS.incrementAndGet();
        int floor = ctx.generator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, band, ctx.random()) - 1;
        return new int[] {floor, floor < ctx.seaLevel() - 1 ? 1 : 0, floor >= band.getMaxBuildHeight() - 2 ? 1 : 0};
    }

    /** The heights read so far (for the probe). */
    static final java.util.concurrent.atomic.AtomicLong HEIGHT_READS = new java.util.concurrent.atomic.AtomicLong();

    /** The band of heights a ruin's ground is looked for in: a shore's near the sea, the land's up to the hills, a mountain's up high. */
    static LevelHeightAccessor band(Context ctx, KnightRuinDesigns.Setting setting)
    {
        int min = Math.max(ctx.heights().getMinBuildHeight(), Math.floorDiv(ctx.seaLevel() - 32, 8) * 8);
        int top = switch (setting)
        {
            case SHORE -> ctx.seaLevel() + 64;
            case MOUNTAIN, CAVERN -> ctx.heights().getMaxBuildHeight();
            default -> ctx.seaLevel() + 192;
        };
        top = Math.min(ctx.heights().getMaxBuildHeight(), Math.floorDiv(top + 7, 8) * 8);
        return LevelHeightAccessor.create(min, top - min);
    }

    private static boolean fits(Context ctx, KnightRuinDesigns.Design design, int ground)
    {
        return ground - design.ground() - 8 > ctx.heights().getMinBuildHeight()
                && ground + design.layers() - design.ground() + 4 < ctx.heights().getMaxBuildHeight();
    }

    /** One of an area's layouts, the seed's pick. */
    @Nullable
    private static KnightRuinDesigns.Design pick(KnightRuinDesigns.Area area, RandomSource rng)
    {
        List<KnightRuinDesigns.Design> designs = KnightRuinDesigns.ALL.stream().filter(d -> d.area() == area).toList();
        return designs.isEmpty() ? null : designs.get(rng.nextInt(designs.size()));
    }

    private static Direction towards(int x, int z, int tx, int tz)
    {
        int dx = tx - x;
        int dz = tz - z;
        return Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    static RandomSource random(Context ctx, long purpose, int a, int b)
    {
        return new XoroshiroRandomSource(ctx.seed() ^ SALT ^ purpose * 0x9E3779B97F4A7C15L ^ a * 0xC2B2AE3D27D4EB4FL ^ b * 0x165667B19E3779F9L);
    }

    private static <T> void shuffle(List<T> list, RandomSource rng)
    {
        for (int i = list.size() - 1; i > 0; i--)
        {
            int j = rng.nextInt(i + 1);
            T t = list.get(i);
            list.set(i, list.get(j));
            list.set(j, t);
        }
    }

    static double dist(int x0, int z0, int x1, int z1)
    {
        return Math.sqrt(Mth.square((double) (x0 - x1)) + Mth.square((double) (z0 - z1)));
    }

    private static <T> T safely(Supplier<T> work, T fallback, String what)
    {
        try
        {
            return work.get();
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Knight ruins: working out {} failed", what, e);
            return fallback;
        }
    }

    private static <T> T await(FutureTask<T> task, T fallback)
    {
        task.run();
        try
        {
            return task.get();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return fallback;
        }
        catch (ExecutionException e)
        {
            LOGGER.error("Knight ruins: working them out failed", e.getCause());
            return fallback;
        }
    }

    /** For the probe and the tests: a fresh world's work from a context (nothing kept from before). */
    static World fresh(Context ctx)
    {
        return new World(ctx);
    }

    /** For the probe and the tests: the ruins that stand within a radius of a spot in a world's work, nearest first. */
    static List<Candidate> standing(World w, int x, int z, int radius)
    {
        List<Candidate> found = new ArrayList<>();
        for (Candidate c : await(w.near, List.<Candidate>of()))
        {
            if (dist(c.site().x(), c.site().z(), x, z) <= radius)
            {
                found.add(c);
            }
        }
        int r = w.ctx.spacing();
        for (int rx = Math.floorDiv(x - radius, r); rx <= Math.floorDiv(x + radius, r); rx++)
        {
            for (int rz = Math.floorDiv(z - radius, r); rz <= Math.floorDiv(z + radius, r); rz++)
            {
                if (decision(w, rx, rz).isPresent())
                {
                    Candidate c = candidate(w, rx, rz).orElseThrow();
                    if (dist(c.site().x(), c.site().z(), x, z) <= radius)
                    {
                        found.add(c);
                    }
                }
            }
        }
        found.sort(Comparator.comparingDouble(c -> dist(c.site().x(), c.site().z(), x, z)));
        return found;
    }

    /** For the probe and the tests: the world's work for a level. */
    @Nullable
    static World work(ServerLevel overworld)
    {
        return world(overworld);
    }

    /** For the probe and the tests: whether two standing ruins break the rules (in one patch within 2 regions, touching
     * within 1, or nearer than {@link #MIN_APART}). */
    @Nullable
    static String breach(World w, Candidate a, Candidate b)
    {
        double d = dist(a.site().x(), a.site().z(), b.site().x(), b.site().z());
        if (d < MIN_APART)
        {
            return "only " + (int) d + " blocks apart";
        }
        boolean nearA = a.regionX() == Integer.MIN_VALUE;
        boolean nearB = b.regionX() == Integer.MIN_VALUE;
        int span;
        if (nearA && nearB)
        {
            span = 1;
        }
        else if (nearA || nearB)
        {
            Candidate other = nearA ? b : a;
            span = w.ctx.fromSpawn(other.site().x(), other.site().z()) < w.ctx.nearRadius() + NEAR_CHECK_REACH ? 1 : 3;
        }
        else
        {
            span = Math.max(Math.abs(a.regionX() - b.regionX()), Math.abs(a.regionZ() - b.regionZ()));
        }
        if (span > 2)
        {
            return null;
        }
        int c = KnightRuinTuning.CELL;
        Window win = window(w, Math.min(a.cellX(), b.cellX()) * c, Math.min(a.cellZ(), b.cellZ()) * c,
                Math.max(a.cellX(), b.cellX()) * c, Math.max(a.cellZ(), b.cellZ()) * c, CONFLICT_MARGIN);
        int la = win.label[win.index(a.cellX(), a.cellZ())];
        int lb = win.label[win.index(b.cellX(), b.cellZ())];
        if (la == lb)
        {
            return "in one patch";
        }
        if (span <= 1 && win.touching(la, lb))
        {
            return "in touching patches";
        }
        return null;
    }

    /** For the tests and the probe: the context of a world. */
    @Nullable
    static Context context(ServerLevel overworld)
    {
        World w = world(overworld);
        return w == null ? null : w.ctx;
    }

    /** For the tests: every region's candidate within a radius (before the neighbours' rule). */
    static List<Candidate> candidatesWithin(ServerLevel overworld, int x, int z, int radius)
    {
        World w = world(overworld);
        if (w == null || !w.ctx.available())
        {
            return List.of();
        }
        List<Candidate> found = new ArrayList<>();
        int r = w.ctx.spacing();
        for (int rx = Math.floorDiv(x - radius, r); rx <= Math.floorDiv(x + radius, r); rx++)
        {
            for (int rz = Math.floorDiv(z - radius, r); rz <= Math.floorDiv(z + radius, r); rz++)
            {
                candidate(w, rx, rz).ifPresent(found::add);
            }
        }
        return found;
    }

    /** For the tests: whether two sites' cells are in one patch (or touching ones). */
    static boolean samePatch(ServerLevel overworld, RuinSite a, RuinSite b, boolean touch)
    {
        World w = world(overworld);
        if (w == null)
        {
            return false;
        }
        int c = KnightRuinTuning.CELL;
        Candidate ca = new Candidate(a, Math.floorDiv(a.x(), c), Math.floorDiv(a.z(), c), 0, 0, 0);
        Candidate cb = new Candidate(b, Math.floorDiv(b.x(), c), Math.floorDiv(b.z(), c), 0, 0, 1);
        int cell = KnightRuinTuning.CELL;
        Window win = window(w, Math.min(ca.cellX(), cb.cellX()) * cell, Math.min(ca.cellZ(), cb.cellZ()) * cell,
                Math.max(ca.cellX(), cb.cellX()) * cell, Math.max(ca.cellZ(), cb.cellZ()) * cell, CONFLICT_MARGIN);
        int la = win.label[win.index(ca.cellX(), ca.cellZ())];
        int lb = win.label[win.index(cb.cellX(), cb.cellZ())];
        return la != 0 && (la == lb || (touch && win.touching(la, lb)));
    }

    /** For the tests: the code of the cell holding a block column. */
    static byte codeAt(ServerLevel overworld, int x, int z)
    {
        World w = world(overworld);
        return w == null ? RuinAreas.NONE : cell(w, Math.floorDiv(x, KnightRuinTuning.CELL), Math.floorDiv(z, KnightRuinTuning.CELL));
    }

    private KnightRuinSites() {}
}
