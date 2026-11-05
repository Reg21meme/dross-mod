package com.reg21meme.dross.world;

import com.reg21meme.dross.DrossWorldSettings;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.FutureTask;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.slf4j.Logger;

/**
 * Works out where a world's portal site (the Fallen Cathedral) goes, from its seed alone (the rules: {@link SiteRules}):
 * the seed's direction and distance from the spawn point, then the nearest spot round there that passes, read from the
 * world generator's own noise (biomes, heights, possible villages: no chunk is ever made). Deterministic and thread-safe:
 * the structure's placement and piece on the world-generation threads ({@link PortalSitePlacement},
 * {@link PortalSiteStructure}), {@link PortalSite}'s getters on the server thread and the tests all get the same answer.
 * <ul>
 *   <li>{@link #register} (when the server's Overworld is made, before its spawn is generated) remembers the world's
 *       generator and starts working the spot out on a background thread; anything that asks before it's done waits for
 *       it (or, if it hasn't started, does it itself: the same work, done once).</li>
 *   <li>A world whose Overworld doesn't generate the site's structure set (a superflat world's own list of structures)
 *       has no site: {@link #available} is false. So has a world made with Generate Structures off.</li>
 * </ul>
 */
final class SiteFinder
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The structure set that places the site ({@code data/dross/worldgen/structure_set/portal_site.json}). */
    static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(Dross.MODID, "portal_site"));

    /**
     * What the work reads: the Overworld's generator, its noise and its structure placements, and the spawn point it
     * measures from (the climate sampler's: where Minecraft starts looking for the world spawn).
     */
    record Context(ChunkGenerator generator, RandomState random, ChunkGeneratorStructureState structures, LevelHeightAccessor fullHeights,
                   LevelHeightAccessor band, int seaLevel, List<SiteVillages.VillageSet> villages, boolean available, int spawnX, int spawnZ,
                   int minDistance, int maxDistance, List<SiteRules.Rung> ladder)
    {
        static Context of(ServerLevel overworld)
        {
            return of(overworld, overworld.getChunkSource().getGenerator(), overworld.getChunkSource().randomState(),
                    overworld.getChunkSource().getGeneratorState());
        }

        /** The context for this generator, noise and structure placements, with the Overworld's heights (a probe's other seeds). */
        static Context of(ServerLevel overworld, ChunkGenerator generator, RandomState random, ChunkGeneratorStructureState structures)
        {
            LevelHeightAccessor full = LevelHeightAccessor.create(overworld.getMinBuildHeight(), overworld.getHeight());
            int bandMin = Math.max(overworld.getMinBuildHeight(), Math.floorDiv(overworld.getSeaLevel() - SiteRules.BAND_BELOW, 8) * 8);
            int bandMax = Math.min(overworld.getMaxBuildHeight(), Math.floorDiv(overworld.getSeaLevel() + SiteRules.BAND_ABOVE + 7, 8) * 8);
            // (Generate Structures off: no structure is made at all, like vanilla's strongholds, so no site either)
            boolean available = structures.possibleStructureSets().stream().anyMatch(set -> set.is(SET))
                    && overworld.getServer().getWorldData().worldGenOptions().generateStructures();
            ChunkPos spawn = new ChunkPos(random.sampler().findSpawnPosition());
            // (the distances this world was made with: config/dross-common.toml when it was new)
            DrossWorldSettings.Values settings = DrossWorldSettings.of(overworld);
            int min = settings.siteMinDistance();
            int max = Math.max(min, settings.siteMaxDistance());
            return new Context(generator, random, structures, full, LevelHeightAccessor.create(bandMin, bandMax - bandMin),
                    overworld.getSeaLevel(), SiteVillages.villageSets(structures), available, spawn.getMinBlockX() + 8, spawn.getMinBlockZ() + 8,
                    min, max, SiteRules.ladder(min, max));
        }

        /** True if a spot in this chunk could ever be the site's (the widest rung's distances from the spawn point). */
        boolean inReach(int chunkX, int chunkZ)
        {
            double d = Math.sqrt(Mth.square((double) ((chunkX << 4) + 8 - spawnX)) + Mth.square((double) ((chunkZ << 4) + 8 - spawnZ)));
            SiteRules.Rung widest = ladder.get(ladder.size() - 1);
            return d >= widest.minDistance() - SiteRules.REACH_SLACK && d <= widest.maxDistance() + SiteRules.REACH_SLACK;
        }
    }

    /** One world's context and the work that finds its spot (run once). */
    private record Entry(Context context, FutureTask<SiteSpot> task)
    {
    }

    /** Per world, by its random state (one per server level; weak, so a closed world's goes). Guarded by itself. */
    private static final Map<RandomState, Entry> ENTRIES = new WeakHashMap<>();

    /**
     * Remembers the server's Overworld and starts working its spot out in the background (a daemon thread; a world that
     * has no site does nothing). Called when the level is made, before anything is generated in it.
     */
    static void register(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        if (entry != null && entry.context().available())
        {
            Thread thread = new Thread(entry.task(), "Dross portal site");
            thread.setDaemon(true);
            thread.start();
        }
    }

    /** The Overworld's entry, made now if it hasn't been yet. Null only if there's no context to be had. */
    @Nullable
    private static Entry entry(ServerLevel overworld)
    {
        RandomState random = overworld.getChunkSource().randomState();
        synchronized (ENTRIES)
        {
            Entry known = ENTRIES.get(random);
            if (known != null)
            {
                return known;
            }
            Context context;
            try
            {
                context = Context.of(overworld);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Dross portal site: couldn't read the Overworld's generator, so the world has no site", e);
                return null;
            }
            Entry made = new Entry(context, new FutureTask<>(() -> computeSafely(context)));
            ENTRIES.put(random, made);
            if (!context.available())
            {
                LOGGER.info("Dross portal site: this world's Overworld doesn't generate the site's structure set {} (a superflat "
                        + "world's own list of structures, for one), so it has no portal site", SET.location());
            }
            return made;
        }
    }

    @Nullable
    private static Entry known(RandomState random)
    {
        synchronized (ENTRIES)
        {
            return ENTRIES.get(random);
        }
    }

    /** True if the world has a portal site (its Overworld generates the site's structure set). */
    static boolean available(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        return entry != null && entry.context().available();
    }

    /** The world's spot. Waits until it's worked out (normally long done: about a second's work, started with the world). */
    @Nullable
    static SiteSpot spot(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        return entry == null ? null : await(entry);
    }

    /** The spot if it's already worked out, else null (never waits: for checks every few ticks). */
    @Nullable
    static SiteSpot spotIfReady(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        if (entry == null || !entry.task().isDone())
        {
            return null;
        }
        return await(entry);
    }

    /** The spot of the world whose random state this is (the world-generation threads' way in), or null if it's not known. */
    @Nullable
    static SiteSpot spot(RandomState random)
    {
        Entry entry = known(random);
        return entry == null || !entry.context().available() ? null : await(entry);
    }

    /** True if the site's structure starts in this chunk. Quick for every chunk out of the site's reach (no work done). */
    static boolean isSiteChunk(RandomState random, int chunkX, int chunkZ)
    {
        Entry entry = known(random);
        if (entry == null || !entry.context().available() || !entry.context().inReach(chunkX, chunkZ))
        {
            return false;
        }
        SiteSpot spot = await(entry);
        return spot != null && (spot.x() >> 4) == chunkX && (spot.z() >> 4) == chunkZ;
    }

    /** The context the world's spot is worked out from (for the tests), or null. */
    @Nullable
    static Context context(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        return entry == null ? null : entry.context();
    }

    @Nullable
    private static SiteSpot await(Entry entry)
    {
        // (if no thread has started it yet, this one does the work; if one is at it, this waits for it)
        entry.task().run();
        try
        {
            return entry.task().get();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
            return null;
        }
        catch (ExecutionException e)
        {
            LOGGER.error("Dross portal site: working out the site's spot failed", e.getCause());
            return null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Working it out
    // ---------------------------------------------------------------------------------------------

    /** {@link #compute}, or, if that fails, the seed's own spot at sea level (a world never goes without a site). */
    private static SiteSpot computeSafely(Context ctx)
    {
        try
        {
            return compute(ctx);
        }
        catch (RuntimeException e)
        {
            int[] seeded = seedSpot(ctx);
            int ground = PortalCathedral.fitting(ctx.fullHeights(), ctx.seaLevel());
            LOGGER.error("Dross portal site: working out its spot failed; it goes on the seed's own spot {} {} at y {}", seeded[0],
                    seeded[1], ground, e);
            return new SiteSpot(seeded[0], seeded[1], ground, SiteRules.LAST_RESORT, "the seed's own spot (the search failed)", -1,
                    distanceFromSpawn(ctx, seeded[0], seeded[1]), 0, 0, 0L);
        }
    }

    /** The seed's spot: its direction and distance from the spawn point. */
    static int[] seedSpot(Context ctx)
    {
        // (Xoroshiro mixes the seed first: the legacy generator's first number hardly changes between nearby seeds, so seeds
        // 1, 2, 3... would all point the same way)
        RandomSource rng = new XoroshiroRandomSource(ctx.structures().getLevelSeed() ^ SiteRules.SPOT_SALT);
        double angle = rng.nextDouble() * Math.PI * 2.0D;
        int distance = ctx.minDistance() + rng.nextInt(ctx.maxDistance() - ctx.minDistance() + 1);
        return new int[] {ctx.spawnX() + (int) Math.round(Math.cos(angle) * distance), ctx.spawnZ() + (int) Math.round(Math.sin(angle) * distance)};
    }

    static int distanceFromSpawn(Context ctx, int x, int z)
    {
        return (int) Math.round(Math.sqrt(Mth.square((double) (x - ctx.spawnX())) + Mth.square((double) (z - ctx.spawnZ()))));
    }

    /**
     * Works the spot out: the seed's spot, then the nearest that passes on each rung of the ladder in turn (a WARN for every
     * rung after the first), then the last resort. Deterministic; any thread; nothing is generated. Each candidate is
     * read in stages, cheapest first, and what's read is kept for the next rungs.
     */
    static SiteSpot compute(Context ctx)
    {
        long start = System.nanoTime();
        int[] seeded = seedSpot(ctx);
        Search search = new Search(ctx);
        for (SiteRules.Rung rung : ctx.ladder())
        {
            Candidate found = search.find(seeded[0], seeded[1], rung);
            if (found == null)
            {
                LOGGER.info("Dross portal site: no spot passed rule {} ({}): {} (the biomes: {})", rung.number(), rung.name(),
                        describeRejected(search.rejected), search.biomes.entrySet().stream()
                                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(8)
                                .map(e -> e.getKey() + " " + e.getValue()).collect(java.util.stream.Collectors.joining(", ")));
            }
            else
            {
                SiteSpot spot = search.spot(found, rung.number(), rung.name(), start);
                if (rung.number() == 0)
                {
                    LOGGER.info("Dross portal site: from the seed: {}", spot.describe());
                }
                else
                {
                    LOGGER.warn("Dross portal site: no spot near the seed's ({} {}) passed the stricter rules; relaxed to \"{}\": {}",
                            seeded[0], seeded[1], rung.name(), spot.describe());
                }
                return spot;
            }
        }
        SiteSpot spot;
        if (search.best != null)
        {
            spot = search.spot(search.best, SiteRules.LAST_RESORT, "last resort: the flattest dry land looked at", start);
        }
        else
        {
            int ground = ctx.generator().getBaseHeight(seeded[0], seeded[1], Heightmap.Types.OCEAN_FLOOR_WG, ctx.fullHeights(), ctx.random()) - 1;
            spot = new SiteSpot(seeded[0], seeded[1], PortalCathedral.fitting(ctx.fullHeights(), ground), SiteRules.LAST_RESORT,
                    "last resort: the seed's own spot", -1, distanceFromSpawn(ctx, seeded[0], seeded[1]), search.seen.size(),
                    search.reads + 1, (System.nanoTime() - start) / 1_000_000L);
        }
        LOGGER.error("Dross portal site: no spot near the seed's ({} {}) passed even the loosest rules; {}", seeded[0], seeded[1],
                spot.describe());
        return spot;
    }

    /** A column's ground in the noise, read once: {@link #OUT} when it's outside the height band (a mountain, say). */
    private static final int OUT = Integer.MIN_VALUE;
    /** Not read yet. */
    private static final int UNREAD = Integer.MIN_VALUE + 1;

    /** A spot being looked at (its site column), and what's been read about it. */
    private static final class Candidate
    {
        final int x;
        final int z;
        /** The smooth height of the land at the site column (its biomes are read there), or {@link #UNREAD}. */
        int smoothY = UNREAD;
        /** Its biomes' worst fit ({@link SiteRules#fit}), or {@link #UNREAD}. */
        int fit = UNREAD;
        /** The biome that gave it its fit (for the log). */
        String worstBiome = "";
        int quickVariation = UNREAD;
        boolean quickDry;
        @Nullable
        Boolean villageFree;
        int variation = UNREAD;
        boolean dry;

        Candidate(int x, int z)
        {
            this.x = x;
            this.z = z;
        }
    }

    // Why a spot was turned down (counted for the log)
    static final int PASSED = 0;
    static final int OUT_OF_BAND = 1;
    static final int BIOME = 2;
    static final int QUICK = 3;
    static final int VILLAGE = 4;
    static final int CHECK = 5;

    /** "n out of the height band, n biomes..." for the log. */
    static String describeRejected(int[] rejected)
    {
        return String.format("%d out of the height band, %d for their biomes, %d too rough or wet at a quick look, %d near a village, "
                + "%d too rough or wet at the full check", rejected[OUT_OF_BAND], rejected[BIOME], rejected[QUICK], rejected[VILLAGE], rejected[CHECK]);
    }

    /** One working-out: what's been read (not thread-safe: one per computation). */
    private static final class Search
    {
        final Context ctx;
        /** The footprint seen from above with the site column on 0, 0. */
        final BoundingBox columns = PortalCathedral.footprint(0, 0, 0);
        final Long2ObjectOpenHashMap<Candidate> seen = new Long2ObjectOpenHashMap<>();
        final SiteVillages.Finder villages;
        /** The flattest dry spot fully looked at so far (the last resort). */
        @Nullable
        Candidate best;
        int reads;

        Search(Context ctx)
        {
            this.ctx = ctx;
            this.villages = new SiteVillages.Finder(ctx.generator(), ctx.random(), ctx.structures(), ctx.villages(), ctx.fullHeights());
        }

        /** Why the last rung's spots were turned down: how many for each reason ({@link #PASSED}, {@link #OUT_OF_BAND}...). */
        final int[] rejected = new int[6];
        /** The biomes that turned the last rung's spots down, and how many each. */
        final Map<String, Integer> biomes = new java.util.HashMap<>();

        /** The nearest spot to {@code cx}/{@code cz} on the rung's grid that passes its rules, or null. */
        @Nullable
        Candidate find(int cx, int cz, SiteRules.Rung rung)
        {
            Arrays.fill(rejected, 0);
            biomes.clear();
            int n = rung.radius() / rung.step();
            List<int[]> offsets = new ArrayList<>();
            for (int i = -n; i <= n; i++)
            {
                for (int j = -n; j <= n; j++)
                {
                    if (i * i + j * j <= n * n)
                    {
                        offsets.add(new int[] {i, j});
                    }
                }
            }
            offsets.sort((a, b) -> a[0] * a[0] + a[1] * a[1] != b[0] * b[0] + b[1] * b[1]
                    ? Integer.compare(a[0] * a[0] + a[1] * a[1], b[0] * b[0] + b[1] * b[1])
                    : a[0] != b[0] ? Integer.compare(a[0], b[0]) : Integer.compare(a[1], b[1]));
            for (int[] o : offsets)
            {
                int x = cx + o[0] * rung.step();
                int z = cz + o[1] * rung.step();
                int d = distanceFromSpawn(ctx, x, z);
                if (d < rung.minDistance() || d > rung.maxDistance())
                {
                    continue;
                }
                Candidate c = seen.computeIfAbsent(BlockPos.asLong(x, 0, z), key -> new Candidate(x, z));
                int reason = passes(c, rung);
                rejected[reason]++;
                if (reason == BIOME)
                {
                    biomes.merge(c.worstBiome, 1, Integer::sum);
                }
                if (reason == PASSED)
                {
                    return c;
                }
            }
            return null;
        }

        /** The rung's rules, cheapest first: its height band, biomes, a quick look at its heights, villages, the full check. */
        private int passes(Candidate c, SiteRules.Rung rung)
        {
            if (c.smoothY == UNREAD)
            {
                c.smoothY = smooth(c.x, c.z);
            }
            if (c.smoothY <= ctx.band().getMinBuildHeight() || c.smoothY >= ctx.band().getMaxBuildHeight() - SiteRules.BAND_TOP_ROOM)
            {
                return OUT_OF_BAND;
            }
            if (c.fit == UNREAD)
            {
                c.fit = biomeFit(c);
            }
            if (c.fit < SiteRules.needs(rung.biomes()))
            {
                return BIOME;
            }
            if (c.quickVariation == UNREAD)
            {
                quick(c);
            }
            if (!c.quickDry || c.quickVariation > rung.maxVariation())
            {
                return QUICK;
            }
            if (c.villageFree == null)
            {
                c.villageFree = villages.villagesNear(footprint(c).inflatedBy(SiteRules.BLEND_MARGIN),
                        SiteRules.VILLAGE_REACH + SiteRules.VILLAGE_CLEARANCE).isEmpty();
            }
            if (!c.villageFree)
            {
                return VILLAGE;
            }
            if (c.variation == UNREAD)
            {
                check(c);
                if (c.dry && (best == null || c.variation < best.variation))
                {
                    best = c;
                }
            }
            return c.dry && c.variation <= rung.maxVariation() ? PASSED : CHECK;
        }

        private BoundingBox footprint(Candidate c)
        {
            return columns.moved(c.x, 0, c.z);
        }

        /**
         * The worst fit of the biomes at the site's height: at the site column, then over the footprint (its corners, along
         * its sides and through its middle, every {@link SiteRules#BIOME_STEP} blocks); and -1 (forbidden) if an ocean,
         * river, beach or swamp is just round it ({@link SiteRules#WATER_CLEARANCE} out: its corners and sides).
         */
        private int biomeFit(Candidate c)
        {
            int qy = QuartPos.fromBlock(c.smoothY);
            int worst = worse(c, 3, biome(c.x, qy, c.z));
            BoundingBox box = footprint(c);
            for (int x : samples(box.minX(), box.maxX(), SiteRules.BIOME_STEP))
            {
                for (int z : samples(box.minZ(), box.maxZ(), SiteRules.BIOME_STEP))
                {
                    if (worst < 0)
                    {
                        return worst;
                    }
                    worst = worse(c, worst, biome(x, qy, z));
                }
            }
            BoundingBox ring = box.inflatedBy(SiteRules.WATER_CLEARANCE);
            for (int x : samples(ring.minX(), ring.maxX(), ring.getXSpan() / 2))
            {
                for (int z : samples(ring.minZ(), ring.maxZ(), ring.getZSpan() / 2))
                {
                    if (worst < 0)
                    {
                        return worst;
                    }
                    Holder<Biome> biome = biome(x, qy, z);
                    if (SiteRules.isForbidden(biome))
                    {
                        worst = worse(c, worst, biome);
                    }
                }
            }
            return worst;
        }

        /** The worse of {@code worst} and this biome's fit, remembering the biome when it's the worse (for the log). */
        private static int worse(Candidate c, int worst, Holder<Biome> biome)
        {
            int fit = SiteRules.fit(biome);
            if (fit < worst)
            {
                c.worstBiome = biome.unwrapKey().map(key -> key.location().getPath()).orElse("?");
                return fit;
            }
            return worst;
        }

        /**
         * The land's smooth height at a column: where the generator's large-scale "depth" (which falls steadily with height)
         * crosses zero, from two readings (microseconds, where a column of real heights costs about a millisecond). The real
         * ground is this plus small bumps. With no such depth (a flat world) it's sea level.
         */
        private int smooth(int x, int z)
        {
            DensityFunction depth = ctx.random().router().depth();
            int y0 = ctx.seaLevel();
            int y1 = ctx.seaLevel() + 64;
            double d0 = depth.compute(new DensityFunction.SinglePointContext(x, y0, z));
            double d1 = depth.compute(new DensityFunction.SinglePointContext(x, y1, z));
            if (!(Math.abs(d0 - d1) > 1.0E-6D))
            {
                return y0;
            }
            return (int) Math.round(y0 + d0 * (y1 - y0) / (d0 - d1));
        }

        private Holder<Biome> biome(int x, int qy, int z)
        {
            return ctx.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), qy, QuartPos.fromBlock(z), ctx.random().sampler());
        }

        /** The footprint's corners, the middles of its sides and its middle: how much their ground varies, and any water. */
        private void quick(Candidate c)
        {
            BoundingBox box = footprint(c);
            int[] xs = {box.minX(), (box.minX() + box.maxX()) / 2, box.maxX()};
            int[] zs = {box.minZ(), (box.minZ() + box.maxZ()) / 2, box.maxZ()};
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            boolean dry = true;
            for (int x : xs)
            {
                for (int z : zs)
                {
                    int[] read = column(x, z);
                    if (read[0] == OUT)
                    {
                        c.quickVariation = Integer.MAX_VALUE;
                        c.quickDry = false;
                        return;
                    }
                    low = Math.min(low, read[0]);
                    high = Math.max(high, read[0]);
                    dry &= read[1] == 0;
                }
            }
            c.quickVariation = high - low;
            c.quickDry = dry;
        }

        /** Every {@link SiteRules#CHECK_STEP} blocks: the ground's variation in the footprint, and no water in it or near it. */
        private void check(Candidate c)
        {
            BoundingBox box = footprint(c);
            BoundingBox near = box.inflatedBy(SiteRules.WATER_CLEARANCE);
            int low = Integer.MAX_VALUE;
            int high = Integer.MIN_VALUE;
            boolean dry = true;
            for (int x : samples(near.minX(), near.maxX(), SiteRules.CHECK_STEP))
            {
                for (int z : samples(near.minZ(), near.maxZ(), SiteRules.CHECK_STEP))
                {
                    int[] read = column(x, z);
                    dry &= read[1] == 0;
                    boolean inside = x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ();
                    if (read[0] == OUT)
                    {
                        dry = false;
                    }
                    else if (inside)
                    {
                        low = Math.min(low, read[0]);
                        high = Math.max(high, read[0]);
                    }
                }
            }
            c.variation = low > high ? Integer.MAX_VALUE : high - low;
            c.dry = dry;
        }

        /**
         * A column from the noise, in the height band: {its ground's top (the solid block water lies on), 1 if water or lava
         * lies on it}; the ground is {@link #OUT} if it isn't in the band (land up to the band's top, or none in it).
         */
        private int[] column(int x, int z)
        {
            reads++;
            NoiseColumn column = ctx.generator().getBaseColumn(x, z, ctx.band(), ctx.random());
            int top = ctx.band().getMaxBuildHeight() - 1;
            boolean wet = false;
            for (int y = top; y >= ctx.band().getMinBuildHeight(); y--)
            {
                BlockState state = column.getBlock(y);
                if (state.isAir())
                {
                    continue;
                }
                if (!state.getFluidState().isEmpty())
                {
                    wet = true;
                    continue;
                }
                return new int[] {y >= top - 1 ? OUT : y, wet ? 1 : 0};
            }
            return new int[] {OUT, wet ? 1 : 0};
        }

        /**
         * The spot for a candidate: its ground level the median of the generator's heights every
         * {@link SiteRules#GROUND_STEP} blocks over the footprint (the hub's way), moved to the nearest level the cathedral
         * fits at in the world's height.
         */
        SiteSpot spot(Candidate c, int rung, String rungName, long start)
        {
            BoundingBox box = footprint(c);
            int[] xs = samples(box.minX(), box.maxX(), SiteRules.GROUND_STEP);
            int[] zs = samples(box.minZ(), box.maxZ(), SiteRules.GROUND_STEP);
            int[] found = new int[xs.length * zs.length];
            int n = 0;
            for (int x : xs)
            {
                for (int z : zs)
                {
                    reads++;
                    int y = ctx.generator().getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, ctx.band(), ctx.random()) - 1;
                    if (y > ctx.band().getMinBuildHeight() && y < ctx.band().getMaxBuildHeight() - 2)
                    {
                        found[n++] = y;
                    }
                }
            }
            int median = n == 0 ? (c.smoothY == UNREAD ? ctx.seaLevel() : c.smoothY) : sortedMedian(found, n);
            int ground = PortalCathedral.fitting(ctx.fullHeights(), median);
            return new SiteSpot(c.x, c.z, ground, rung, rungName, c.variation == UNREAD ? -1 : c.variation,
                    distanceFromSpawn(ctx, c.x, c.z), seen.size(), reads, (System.nanoTime() - start) / 1_000_000L);
        }

        private static int sortedMedian(int[] values, int n)
        {
            int[] sorted = Arrays.copyOf(values, n);
            Arrays.sort(sorted);
            return sorted[n / 2];
        }
    }

    /** The sample positions from {@code min} to {@code max}: every {@code step}, and {@code max} itself. */
    static int[] samples(int min, int max, int step)
    {
        int count = (max - min) / step + 1;
        boolean last = (max - min) % step != 0;
        int[] out = new int[count + (last ? 1 : 0)];
        for (int i = 0; i < count; i++)
        {
            out[i] = min + i * step;
        }
        if (last)
        {
            out[count] = max;
        }
        return out;
    }

    private SiteFinder() {}
}
