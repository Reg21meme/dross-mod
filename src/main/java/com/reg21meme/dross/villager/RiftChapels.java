package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongArray;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import org.slf4j.Logger;

/**
 * The Dross trader's home: <b>one Rift Chapel per world, at the edge of a village</b> (the user's decision of November 4,
 * 2026: "only 1 trader, and I still want him to be in villages"), made by world generation with the village's land, its
 * trader in it ({@code dross:rift_chapel}: {@link RiftChapelStructure}, {@link RiftChapelPiece}, {@link RiftChapelPathPiece}).
 * <p>
 * <b>Which village</b> is worked out from the seed alone ({@link TraderVillages}): of the villages nearest the spawn point
 * that have room at their edge, the seed picks one at random. Nothing is generated or loaded for it: it's worked out once
 * on a background thread when the world loads ({@link #register}), and anything that asks before that's done waits for it.
 * World generation then starts the chapel only in that one chunk ({@link RiftChapelPlacement}), so a world gets exactly one
 * chapel and one trader, and {@code /dross goto trader}, the join message and the Weathered Letter know where it is from the
 * start. A village gives the trader only once: {@link TraderVillageData}.
 * <p>
 * No chapel (and so no trader) in a world made with Generate Structures off, or one whose Overworld doesn't generate the
 * chapel's structure set (a superflat world's own list of structures).
 */
public final class RiftChapels
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The structure set that places it ({@code data/dross/worldgen/structure_set/rift_chapel.json}). */
    public static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(Dross.MODID, "rift_chapel"));
    /** The biomes a chapel on its own (no village had room) may stand in ({@code data/dross/tags/worldgen/biome/has_structure/rift_chapel.json}). */
    public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME, new ResourceLocation(Dross.MODID, "has_structure/rift_chapel"));

    // ---- Tuning ----
    /** A chapel on its own has its middle this many blocks into its chunk (X and Z): the chunk's middle. */
    static final int IN_CHUNK = 8;
    /** The land is read this far round the footprint too (blocks), so its edges are dry and flat enough as well. */
    static final int FIT_EDGE = 2;
    /**
     * The land's highest and lowest read columns may differ by at most this many blocks (the floor cuts or fills the rest;
     * Minecraft's terrain adaptation smooths the land round it).
     */
    static final int MAX_RELIEF = 6;
    /** Heights are read in a band from this far under sea level... */
    static final int BAND_BELOW = 16;
    /** ...to this far over it (much quicker than the whole column; land higher than that is no place for it anyway). */
    static final int BAND_ABOVE = 112;
    /** The salt of the seeded random that turns a chapel on its own (its door faces one of the four ways). */
    private static final int FACING_SALT = 1104202604;
    /** A ground read that found no ground in the band (land too high or too low) or water on it. */
    static final int NO_GROUND = Integer.MIN_VALUE;

    /**
     * A chapel: the chunk it starts in, its middle on its ground (the design's center: the ground block the floor
     * replaces), and the way its door faces.
     */
    public record Chapel(int chunkX, int chunkZ, BlockPos center, Direction facing)
    {
        public ChunkPos startChunk()
        {
            return new ChunkPos(this.chunkX, this.chunkZ);
        }

        /** Everything the chapel covers, from its ground layer up to its top (the trader's {@code HutBox}). */
        public BoundingBox footprint()
        {
            return TraderHut.DESIGN.footprint(this.center, this.facing);
        }

        /** His spot in the aisle (feet): where he's spawned and where he teleports home to. */
        public BlockPos traderSpot()
        {
            return TraderHut.DESIGN.traderSpot(this.center, this.facing);
        }

        /** Where to stand in front of the door (feet), on the chapel's own ground ({@code /dross goto trader}). */
        public BlockPos doorstep()
        {
            return TraderHut.DESIGN.doorstep(this.center, this.facing);
        }

        /** The yaw of someone at the doorstep looking at the door. */
        public float doorYaw()
        {
            return this.facing.getOpposite().toYRot();
        }

        /** The horizontal distance (squared) from {@code from} to his spot. */
        public long distanceSqr(BlockPos from)
        {
            long dx = this.traderSpot().getX() - from.getX();
            long dz = this.traderSpot().getZ() - from.getZ();
            return dx * dx + dz * dz;
        }

        public String describe()
        {
            BlockPos spot = this.traderSpot();
            return "the Rift Chapel at " + spot.getX() + ", " + spot.getY() + ", " + spot.getZ() + " (door facing " + this.facing.getName()
                    + ", chunk " + this.chunkX + ", " + this.chunkZ + ")";
        }
    }

    /**
     * The world's trader's home: his chapel; whether it's at a village (else on its own), that village's start chunk
     * ({@link #village}: the key of the "once a village" rule, the chapel's own chunk for one on its own), type and middle;
     * the middle line of the path from its door to the village's nearest street (its corners; empty on its own) and what
     * it's made of; and, for the log and the tests, its distance from the spawn point, its land's unevenness, which of how
     * many villages the seed picked, and how many possible village chunks and villages were looked at.
     */
    public record Home(Chapel chapel, boolean inVillage, long village, @Nullable ResourceLocation villageType, @Nullable BlockPos villageMiddle,
                       List<BlockPos> path, Block pathBlock, long distance, int relief, int pick, int choices, int looked, int villages)
    {
        Home picked(int pick, int choices, int looked, int villages)
        {
            return new Home(this.chapel, this.inVillage, this.village, this.villageType, this.villageMiddle, this.path, this.pathBlock,
                    this.distance, this.relief, pick, choices, looked, villages);
        }

        public String describe()
        {
            return this.chapel.describe() + (this.inVillage ? ", at the edge of the " + this.villageType + " at " + this.villageMiddle.getX() + ", "
                    + this.villageMiddle.getZ() + " (the No. " + this.pick + " nearest possible village; the seed tries the " + this.choices
                    + " nearest in an order of its own), its path " + TraderVillages.pathLength(this.path) + " blocks to the nearest street"
                    : ", on its own (no village had room)") + "; " + this.distance + " blocks from the spawn point, its land " + this.relief
                    + " blocks uneven; " + this.looked + " possible village(s) tried, " + this.villages + " laid out";
        }
    }

    // ---------------------------------------------------------------- the world's home (worked out once)

    /** One world's context, its possible villages (for the quick answers) and its home, worked out on a thread of its own. */
    private record Entry(TraderVillages.Context context, boolean available, List<TraderVillages.Cell> possible,
                         CompletableFuture<Optional<Home>> home) {}

    /** Per world, by its random state (one per server level; weak, so a closed world's goes). Guarded by itself. */
    private static final Map<RandomState, Entry> ENTRIES = new WeakHashMap<>();

    /**
     * Remembers the server's Overworld and starts working its trader's home out in the background. Called when the level
     * is made, before anything is generated in it ({@link RiftChapelEvents}).
     */
    static void register(ServerLevel overworld)
    {
        entry(overworld);
    }

    /** The Overworld's entry, made (and its work started) now if it hasn't been yet. Null only if its generator can't be read. */
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
            TraderVillages.Context context;
            boolean available;
            List<TraderVillages.Cell> possible;
            try
            {
                context = TraderVillages.Context.of(overworld);
                // (Generate Structures off: no structure is made at all, like vanilla's villages, so no chapel either)
                available = overworld.getServer().getWorldData().worldGenOptions().generateStructures()
                        && context.structures().possibleStructureSets().stream().anyMatch(set -> set.is(SET));
                long start = System.nanoTime();
                // (with atVillages off the chapel stands on its own, never at a village: no quick answers from them are needed)
                possible = available && context.atVillages() ? TraderVillages.possibleVillages(context) : List.of();
                LOGGER.info("[Dross] {} possible villages within {} blocks of the spawn point {}, {} (looked at in {} ms)", possible.size(),
                        context.searchRadius(), context.spawnX(), context.spawnZ(), (System.nanoTime() - start) / 1_000_000L);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("[Dross] Couldn't read the Overworld's generator, so the world has no Dross trader", e);
                return null;
            }
            CompletableFuture<Optional<Home>> home = new CompletableFuture<>();
            Entry made = new Entry(context, available, List.copyOf(possible), home);
            ENTRIES.put(random, made);
            if (!available)
            {
                LOGGER.info("[Dross] This world makes no Rift Chapel (Generate Structures is off, or its Overworld doesn't generate the "
                        + "structure set {}), so it has no Dross trader", SET.location());
                home.complete(Optional.empty());
                return made;
            }
            Thread thread = new Thread(() -> {
                try
                {
                    home.complete(TraderVillages.planLogged(context, made.possible()));
                }
                catch (Throwable e)
                {
                    LOGGER.error("[Dross] Working out the Dross trader's village failed, so the world has no trader", e);
                    home.complete(Optional.empty());
                }
            }, "Dross trader's home");
            thread.setDaemon(true);
            thread.start();
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

    /** True if world generation makes the trader's chapel in this world (see the class's description). */
    public static boolean available(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        return entry != null && entry.available();
    }

    /** The world's trader's home. Waits until it's worked out (normally long done: started with the world). */
    public static Optional<Home> home(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        return entry == null ? Optional.empty() : entry.home().join();
    }

    /** The home if it's already worked out, else null (never waits: for checks every few ticks). */
    @Nullable
    public static Optional<Home> homeIfReady(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        if (entry == null)
        {
            return Optional.empty();
        }
        return entry.home().isDone() ? entry.home().join() : null;
    }

    /** The home, when it's worked out (at once if it already is). */
    public static CompletableFuture<Optional<Home>> homeAsync(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        return entry == null ? CompletableFuture.completedFuture(Optional.empty()) : entry.home();
    }

    /** The home of the world whose random state this is (the world-generation threads' way in; waits), or null. */
    @Nullable
    static Home homeOf(RandomState random)
    {
        Entry entry = known(random);
        return entry == null || !entry.available() ? null : entry.home().join().orElse(null);
    }

    /**
     * True if the chapel's structure starts in this chunk. Quick for every chunk out of its reach, and, while the home is
     * still being worked out, for every chunk it can't be in ({@link TraderVillages#neverHolds}: no waiting); the others
     * wait for it.
     */
    static boolean isChapelChunk(RandomState random, int chunkX, int chunkZ)
    {
        Entry entry = known(random);
        if (entry == null || !entry.available() || !entry.context().inReach(chunkX, chunkZ))
        {
            return false;
        }
        if (!entry.home().isDone() && TraderVillages.neverHolds(entry.context(), entry.possible(), chunkX, chunkZ))
        {
            return false;
        }
        Home home = entry.home().join().orElse(null);
        return home != null && home.chapel().chunkX() == chunkX && home.chapel().chunkZ() == chunkZ;
    }

    /** The world's possible villages, nearest first (for the tests). */
    static List<TraderVillages.Cell> possibleVillages(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        return entry == null ? List.of() : entry.possible();
    }

    /** The context the world's home is worked out from (for the tests), or null. */
    @Nullable
    static TraderVillages.Context context(ServerLevel overworld)
    {
        Entry entry = entry(overworld);
        return entry == null ? null : entry.context();
    }

    // ---------------------------------------------------------------- a chapel on its own (from the noise alone)

    /** How many spots have been worked out, and how long that took in all (for the log and the tests). */
    private static final AtomicLong FITS_READ = new AtomicLong();
    private static final AtomicLong FIT_NANOS = new AtomicLong();
    /** Why spots were turned down, since the server started: {its biome, water or no ground in the band, too steep, a corner's biome}. */
    private static final AtomicLongArray TURNED_DOWN = new AtomicLongArray(4);

    static long[] turnedDown()
    {
        long[] out = new long[TURNED_DOWN.length()];
        for (int i = 0; i < out.length; i++)
        {
            out[i] = TURNED_DOWN.get(i);
        }
        return out;
    }

    /** {spots worked out, their nanoseconds in all} since the server started (for the tests). */
    static long[] fitTiming()
    {
        return new long[] {FITS_READ.get(), FIT_NANOS.get()};
    }

    /**
     * Whether a chapel on its own fits in the chunk, and where, from the generator's noise alone (no chunk is made or
     * loaded): the old "emergency chapel" when no village near the spawn has room ({@link TraderVillages}). Its door faces a
     * way the seed picks; its middle is the chunk's middle. The checks, cheapest first:
     * <ol>
     *   <li>the biome where the land roughly is (two readings of the noise's large-scale depth) is in {@link #BIOMES};</li>
     *   <li>nine columns of the land (the middle, then the corners and the middles of the sides of the footprint grown by
     *       {@link #FIT_EDGE}): each has dry ground in the height band (no water or lava on it), and they differ by at most
     *       {@link #MAX_RELIEF} blocks; the floor goes at their median;</li>
     *   <li>the biome at that height, at the middle and the four corners, is in {@link #BIOMES} (so no river, beach or sea
     *       comes right up to it).</li>
     * </ol>
     * The same answer on any thread, every time.
     */
    static Optional<Chapel> fit(ChunkGenerator generator, RandomState random, long seed, int chunkX, int chunkZ)
    {
        long start = System.nanoTime();
        try
        {
            return fitUntimed(generator, random, seed, chunkX, chunkZ);
        }
        finally
        {
            FITS_READ.incrementAndGet();
            FIT_NANOS.addAndGet(System.nanoTime() - start);
        }
    }

    private static Optional<Chapel> fitUntimed(ChunkGenerator generator, RandomState random, long seed, int chunkX, int chunkZ)
    {
        Direction facing = facing(seed, chunkX, chunkZ);
        int x = (chunkX << 4) + IN_CHUNK;
        int z = (chunkZ << 4) + IN_CHUNK;
        BoundingBox foot = TraderHut.DESIGN.footprint(new BlockPos(x, 0, z), facing);
        int x0 = foot.minX() - FIT_EDGE;
        int x1 = foot.maxX() + FIT_EDGE;
        int z0 = foot.minZ() - FIT_EDGE;
        int z1 = foot.maxZ() + FIT_EDGE;
        if (!inBiomes(generator, random, x, smoothHeight(random, generator.getSeaLevel(), x, z), z))
        {
            TURNED_DOWN.incrementAndGet(0);
            return Optional.empty();
        }
        LevelHeightAccessor band = band(generator);
        int[][] columns = {{x, z}, {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {x, z0}, {x, z1}, {x0, z}, {x1, z}};
        int[] grounds = new int[columns.length];
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int i = 0; i < columns.length; i++)
        {
            int ground = ground(generator, random, band, columns[i][0], columns[i][1]);
            if (ground == NO_GROUND)
            {
                TURNED_DOWN.incrementAndGet(1);
                return Optional.empty();
            }
            grounds[i] = ground;
            low = Math.min(low, ground);
            high = Math.max(high, ground);
            if (high - low > MAX_RELIEF)
            {
                TURNED_DOWN.incrementAndGet(2);
                return Optional.empty();
            }
        }
        Arrays.sort(grounds);
        int groundY = grounds[grounds.length / 2];
        for (int[] at : new int[][] {{x, z}, {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}})
        {
            if (!inBiomes(generator, random, at[0], groundY, at[1]))
            {
                TURNED_DOWN.incrementAndGet(3);
                return Optional.empty();
            }
        }
        return Optional.of(new Chapel(chunkX, chunkZ, new BlockPos(x, groundY, z), facing));
    }

    /** The way a chapel on its own's door faces, picked by the seed for its chunk. */
    static Direction facing(long seed, int chunkX, int chunkZ)
    {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, chunkX, chunkZ, FACING_SALT);
        return Direction.from2DDataValue(random.nextInt(4));
    }

    /**
     * The land's smooth height at a column: where the noise's large-scale "depth" (which falls steadily with height)
     * crosses zero, from two readings (microseconds; a column of real heights costs about a millisecond). Sea level if the
     * noise has no such depth. Only for a first guess (where to look at the biome, which spots to read properly).
     */
    static int smoothHeight(RandomState random, int seaLevel, int x, int z)
    {
        DensityFunction depth = random.router().depth();
        int y0 = seaLevel;
        int y1 = seaLevel + 64;
        double d0 = depth.compute(new DensityFunction.SinglePointContext(x, y0, z));
        double d1 = depth.compute(new DensityFunction.SinglePointContext(x, y1, z));
        if (!(Math.abs(d0 - d1) > 1.0E-6D))
        {
            return y0;
        }
        return Mth.clamp((int) Math.round(y0 + d0 * (y1 - y0) / (d0 - d1)), y0 - BAND_BELOW, y0 + BAND_ABOVE);
    }

    private static boolean inBiomes(ChunkGenerator generator, RandomState random, int x, int y, int z)
    {
        Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                random.sampler());
        return biome.is(BIOMES);
    }

    /** The height band the land is read in (whole noise cells, so the readings are the generator's own). */
    static LevelHeightAccessor band(ChunkGenerator generator)
    {
        int min = Math.max(generator.getMinY(), Math.floorDiv(generator.getSeaLevel() - BAND_BELOW, 8) * 8);
        int max = Math.min(generator.getMinY() + generator.getGenDepth(), Math.floorDiv(generator.getSeaLevel() + BAND_ABOVE + 7, 8) * 8);
        return LevelHeightAccessor.create(min, Math.max(8, max - min));
    }

    /**
     * A column of the land from the noise, in the band: its ground's top, or {@link #NO_GROUND} if water or lava lies on
     * it, or it has no ground in the band (land up to the band's top, or none in it).
     */
    static int ground(ChunkGenerator generator, RandomState random, LevelHeightAccessor band, int x, int z)
    {
        NoiseColumn column = generator.getBaseColumn(x, z, band, random);
        int top = band.getMaxBuildHeight() - 1;
        for (int y = top; y >= band.getMinBuildHeight(); y--)
        {
            BlockState state = column.getBlock(y);
            if (state.isAir())
            {
                continue;
            }
            if (!state.getFluidState().isEmpty() || y >= top - 1)
            {
                return NO_GROUND;
            }
            return y;
        }
        return NO_GROUND;
    }

    private RiftChapels() {}
}
