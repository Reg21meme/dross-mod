package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.WeakHashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import org.slf4j.Logger;

/**
 * The Rift Chapels, the Dross trader's homes, as world generation makes them (the user's decision of November 2, 2026): a
 * rare structure of its own in the countryside, {@code dross:rift_chapel}, on a seeded grid like Minecraft's igloos and
 * witch huts ({@link RiftChapelPlacement}: one possible spot in every {@code spacing} x {@code spacing} chunks), each with
 * its trader. Not part of the villages (a modpack that replaces Minecraft's villages would lose it).
 * <p>
 * <b>Whether a possible spot gets a chapel</b> ({@link #fit}) is worked out from the generator's noise alone, the same on
 * every thread and at any time: its biome in {@link #BIOMES}, its land dry and flat enough. So {@link #nearest} can tell,
 * without generating or loading anything, where the nearest chapel is ({@code /dross goto trader}, the join message, the
 * Weathered Letter), and it's exactly where world generation puts one.
 */
public final class RiftChapels
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The structure set that places them ({@code data/dross/worldgen/structure_set/rift_chapel.json}). */
    public static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(Dross.MODID, "rift_chapel"));
    /** The biomes a chapel may stand in: dry, open Overworld land ({@code data/dross/tags/worldgen/biome/has_structure/rift_chapel.json}). */
    public static final TagKey<Biome> BIOMES = TagKey.create(Registries.BIOME, new ResourceLocation(Dross.MODID, "has_structure/rift_chapel"));

    // ---- Tuning ----
    /** {@link #nearest} looks this far (blocks) by default: the join message's and the Weathered Letter's search. */
    public static final int SEARCH_RADIUS = 5000;
    /** The chapel's middle is this many blocks into its chunk (X and Z): the chunk's middle. */
    static final int IN_CHUNK = 8;
    /** The land is read this far round the footprint too (blocks), so its edges are dry and flat enough as well. */
    static final int FIT_EDGE = 2;
    /**
     * The land's highest and lowest read columns may differ by at most this many blocks (the floor cuts or fills the rest;
     * Minecraft's terrain adaptation smooths the land round it). 5 turned about a fifth of the possible spots down as too
     * steep.
     */
    static final int MAX_RELIEF = 6;
    /** Heights are read in a band from this far under sea level... */
    static final int BAND_BELOW = 16;
    /** ...to this far over it (much quicker than the whole column; land higher than that is no place for it anyway). */
    static final int BAND_ABOVE = 112;
    /** The salt of the seeded random that turns each chapel (its door faces one of the four ways). */
    private static final int FACING_SALT = 1104202604;
    /** A ground read that found no ground in the band (land too high or too low) or water on it. */
    private static final int NO_GROUND = Integer.MIN_VALUE;

    /**
     * One chapel: the chunk it starts in, its middle on its ground (the design's center: the ground block the floor
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

    // ---------------------------------------------------------------- the fit (from the noise alone)

    /** Each world's answers so far, by start chunk (deterministic, so kept). Per world by its random state; weak. */
    private static final Map<RandomState, Map<Long, Optional<Chapel>>> FITS = new WeakHashMap<>();
    /** How many spots have been worked out, and how long that took in all (for the log and the tests). */
    private static final AtomicLong FITS_READ = new AtomicLong();
    private static final AtomicLong FIT_NANOS = new AtomicLong();

    /** {@link #fit}, remembered: world generation and {@link #nearest} share the answers. Any thread. */
    static Optional<Chapel> fitCached(ChunkGenerator generator, RandomState random, long seed, int chunkX, int chunkZ)
    {
        Map<Long, Optional<Chapel>> fits;
        synchronized (FITS)
        {
            fits = FITS.computeIfAbsent(random, r -> new ConcurrentHashMap<>());
        }
        long key = ChunkPos.asLong(chunkX, chunkZ);
        Optional<Chapel> known = fits.get(key);
        if (known != null)
        {
            return known;
        }
        long start = System.nanoTime();
        Optional<Chapel> made = fit(generator, random, seed, chunkX, chunkZ);
        FIT_NANOS.addAndGet(System.nanoTime() - start);
        FITS_READ.incrementAndGet();
        Optional<Chapel> before = fits.putIfAbsent(key, made);
        return before != null ? before : made;
    }

    /**
     * Why spots were turned down, since the server started (for the tests' log): {its biome, water or no ground in the band,
     * too steep, a corner's biome, kept off a village or the portal site by the placement}.
     */
    private static final AtomicLongArray TURNED_DOWN = new AtomicLongArray(5);

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
     * Whether the chunk (a possible spot of the grid) gets a chapel, and where, from the generator's noise alone (no chunk
     * is made or loaded). Its door faces a way the seed picks; its middle is the chunk's middle. The checks, cheapest first:
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

    /** The way a chapel's door faces, picked by the seed for its chunk. */
    static Direction facing(long seed, int chunkX, int chunkZ)
    {
        WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
        random.setLargeFeatureWithSalt(seed, chunkX, chunkZ, FACING_SALT);
        return Direction.from2DDataValue(random.nextInt(4));
    }

    /**
     * The land's smooth height at a column: where the noise's large-scale "depth" (which falls steadily with height)
     * crosses zero, from two readings (microseconds; a column of real heights costs about a millisecond). Sea level if the
     * noise has no such depth. Only for where to look at the biome first.
     */
    private static int smoothHeight(RandomState random, int seaLevel, int x, int z)
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
    private static LevelHeightAccessor band(ChunkGenerator generator)
    {
        int min = Math.max(generator.getMinY(), Math.floorDiv(generator.getSeaLevel() - BAND_BELOW, 8) * 8);
        int max = Math.min(generator.getMinY() + generator.getGenDepth(), Math.floorDiv(generator.getSeaLevel() + BAND_ABOVE + 7, 8) * 8);
        return LevelHeightAccessor.create(min, Math.max(8, max - min));
    }

    /**
     * A column of the land from the noise, in the band: its ground's top, or {@link #NO_GROUND} if water or lava lies on
     * it, or it has no ground in the band (land up to the band's top, or none in it).
     */
    private static int ground(ChunkGenerator generator, RandomState random, LevelHeightAccessor band, int x, int z)
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

    // ---------------------------------------------------------------- where they are

    /** What the searches read from a world: its generator and noise, its structure placements and the chapels' grid. */
    private record Reader(ChunkGenerator generator, RandomState random, ChunkGeneratorStructureState structures, long seed,
                          RandomSpreadStructurePlacement placement)
    {
        /** The chapel the grid puts in this chunk, if world generation makes one there. */
        Optional<Chapel> chapelAt(int chunkX, int chunkZ)
        {
            if (!this.placement.isStructureChunk(this.structures, chunkX, chunkZ))
            {
                TURNED_DOWN.incrementAndGet(4);
                return Optional.empty();
            }
            return fitCached(this.generator, this.random, this.seed, chunkX, chunkZ);
        }

        /** The possible spot of the grid cell (in cells). */
        ChunkPos spotOfCell(int cellX, int cellZ)
        {
            int spacing = this.placement.spacing();
            return this.placement.getPotentialStructureChunk(this.seed, cellX * spacing, cellZ * spacing);
        }

        int cellBlocks()
        {
            return this.placement.spacing() * 16;
        }
    }

    /**
     * The world's reader, or null if it makes no chapels: not the Overworld's kind of world (its list of structures leaves
     * them out, as a superflat world's does), or Generate Structures is off. Any thread.
     */
    @Nullable
    private static Reader reader(ServerLevel overworld)
    {
        if (!overworld.getServer().getWorldData().worldGenOptions().generateStructures())
        {
            return null;
        }
        ChunkGeneratorStructureState structures = overworld.getChunkSource().getGeneratorState();
        for (Holder<StructureSet> set : structures.possibleStructureSets())
        {
            if (set.is(SET) && set.value().placement() instanceof RandomSpreadStructurePlacement placement)
            {
                return new Reader(overworld.getChunkSource().getGenerator(), overworld.getChunkSource().randomState(), structures,
                        structures.getLevelSeed(), placement);
            }
        }
        return null;
    }

    /** True if world generation makes Rift Chapels in this world (see {@link #reader}). */
    public static boolean available(ServerLevel overworld)
    {
        return reader(overworld) != null;
    }

    /** How many searches have run and how long they took in all (for the log and the tests). */
    private static final AtomicLong SEARCHES = new AtomicLong();
    private static final AtomicLong SEARCH_NANOS = new AtomicLong();

    /** {searches run, their nanoseconds in all} since the server started (for the tests). */
    static long[] searchTiming()
    {
        return new long[] {SEARCHES.get(), SEARCH_NANOS.get()};
    }

    /**
     * The nearest chapel to {@code from} (by his spot in it, across the land) within {@code radius} blocks, from the noise
     * alone: nothing is generated or loaded. The grid's cells are looked at ring by ring outwards until no nearer one can
     * be left. Empty if there's none that near, or the world makes none ({@link #available}). Any thread; the first look
     * at a cell costs about a millisecond a column read (nine at most), then it's remembered.
     */
    public static Optional<Chapel> nearest(ServerLevel overworld, BlockPos from, int radius)
    {
        long start = System.nanoTime();
        try
        {
            Reader reader = reader(overworld);
            if (reader == null)
            {
                return Optional.empty();
            }
            int cell = reader.cellBlocks();
            int cellX = Math.floorDiv(from.getX() >> 4, reader.placement().spacing());
            int cellZ = Math.floorDiv(from.getZ() >> 4, reader.placement().spacing());
            long radiusSqr = (long) radius * radius;
            Chapel best = null;
            long bestSqr = Long.MAX_VALUE;
            for (int ring = 0; ; ring++)
            {
                // (every spot of a cell `ring` cells out is at least ring - 1 cells away)
                long nearestPossible = (long) Math.max(0, ring - 1) * cell;
                if (nearestPossible * nearestPossible > Math.min(bestSqr, radiusSqr))
                {
                    break;
                }
                for (int[] at : ring(cellX, cellZ, ring))
                {
                    ChunkPos spot = reader.spotOfCell(at[0], at[1]);
                    Optional<Chapel> chapel = reader.chapelAt(spot.x, spot.z);
                    if (chapel.isPresent())
                    {
                        long d = chapel.get().distanceSqr(from);
                        if (d <= radiusSqr && d < bestSqr)
                        {
                            best = chapel.get();
                            bestSqr = d;
                        }
                    }
                }
            }
            return Optional.ofNullable(best);
        }
        finally
        {
            SEARCHES.incrementAndGet();
            SEARCH_NANOS.addAndGet(System.nanoTime() - start);
        }
    }

    /** The cells (in cells) of the square ring {@code ring} cells round {@code (cellX, cellZ)}; the cell itself for 0. */
    private static List<int[]> ring(int cellX, int cellZ, int ring)
    {
        List<int[]> out = new ArrayList<>();
        for (int dx = -ring; dx <= ring; dx++)
        {
            for (int dz = -ring; dz <= ring; dz++)
            {
                if (Math.max(Math.abs(dx), Math.abs(dz)) == ring)
                {
                    out.add(new int[] {cellX + dx, cellZ + dz});
                }
            }
        }
        return out;
    }

    /** Every chapel within {@code radius} blocks of {@code from} (by his spot), nearest first. For the tests and the log. */
    public static List<Chapel> within(ServerLevel overworld, BlockPos from, int radius)
    {
        Reader reader = reader(overworld);
        if (reader == null)
        {
            return List.of();
        }
        int cells = radius / reader.cellBlocks() + 2;
        int cellX = Math.floorDiv(from.getX() >> 4, reader.placement().spacing());
        int cellZ = Math.floorDiv(from.getZ() >> 4, reader.placement().spacing());
        long radiusSqr = (long) radius * radius;
        List<Chapel> out = new ArrayList<>();
        for (int dx = -cells; dx <= cells; dx++)
        {
            for (int dz = -cells; dz <= cells; dz++)
            {
                ChunkPos spot = reader.spotOfCell(cellX + dx, cellZ + dz);
                reader.chapelAt(spot.x, spot.z).filter(c -> c.distanceSqr(from) <= radiusSqr).ifPresent(out::add);
            }
        }
        out.sort(Comparator.comparingLong(c -> c.distanceSqr(from)));
        return out;
    }

    /**
     * Where to search from for someone at {@code pos} in {@code level}: there in the Overworld; in the Nether the
     * Overworld's place for it (its coordinates times the Nether's scale, 8); anywhere else the world spawn.
     */
    public static BlockPos searchOrigin(ServerLevel overworld, Level level, BlockPos pos)
    {
        if (level.dimension() == Level.OVERWORLD)
        {
            return pos;
        }
        if (level.dimension() == Level.NETHER)
        {
            double scale = DimensionType.getTeleportationScale(level.dimensionType(), overworld.dimensionType());
            return BlockPos.containing(pos.getX() * scale, pos.getY(), pos.getZ() * scale);
        }
        return overworld.getSharedSpawnPos();
    }

    // ---------------------------------------------------------------- off the server thread

    /** The searches the server thread asked for, run one at a time on a thread of their own. */
    private static final ExecutorService SEARCH_THREAD = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Dross Rift Chapel search");
        thread.setDaemon(true);
        thread.setPriority(Thread.MIN_PRIORITY + 1);
        return thread;
    });
    /** Searches asked for with {@link #nearestIfReady}, by the chunk they search from (only within {@link #SEARCH_RADIUS}). */
    private static final Map<Long, CompletableFuture<Optional<Chapel>>> REQUESTS = new ConcurrentHashMap<>();
    /** Done requests are forgotten past this many (they're quick to redo: the cells are remembered). */
    private static final int MAX_REQUESTS = 64;

    /** {@link #nearest} on the search thread. A problem completes it with nothing (and is logged). */
    public static CompletableFuture<Optional<Chapel>> nearestAsync(ServerLevel overworld, BlockPos from, int radius)
    {
        BlockPos at = from.immutable();
        return CompletableFuture.supplyAsync(() -> {
            try
            {
                return nearest(overworld, at, radius);
            }
            catch (RuntimeException e)
            {
                LOGGER.warn("[Dross] Couldn't work out the nearest Rift Chapel to {} ({})", at.toShortString(), e.toString());
                return Optional.<Chapel>empty();
            }
        }, SEARCH_THREAD);
    }

    /**
     * The answer of a search within {@link #SEARCH_RADIUS} of {@code from}'s chunk if it's done; otherwise null, and the
     * search is started (once) on the search thread. For a caller on the server thread that can ask again next tick.
     */
    @Nullable
    public static Optional<Chapel> nearestIfReady(ServerLevel overworld, BlockPos from)
    {
        long key = ChunkPos.asLong(from.getX() >> 4, from.getZ() >> 4);
        CompletableFuture<Optional<Chapel>> request = REQUESTS.get(key);
        if (request == null)
        {
            if (REQUESTS.size() >= MAX_REQUESTS)
            {
                REQUESTS.values().removeIf(CompletableFuture::isDone);
            }
            request = REQUESTS.computeIfAbsent(key, k -> nearestAsync(overworld, from, SEARCH_RADIUS));
        }
        return request.isDone() ? request.join() : null;
    }

    /** Forgets the requests (when the server stops: the next world is another). */
    static void forgetRequests()
    {
        REQUESTS.clear();
    }

    private RiftChapels() {}
}
