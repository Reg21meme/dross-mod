package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossWorldSettings;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.boss.knight.KnightRuinDesigns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BuiltinStructureSets;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.FutureTask;

/**
 * Where the knight ruins go in the Nether (PRE_DROSS_PLAN.md, stage T3; the user, November 25, 2026: "make Dross knights,
 * the fire ones, spawn in the Nether ... still rare ... maybe make the Nether one every 1,000 blocks"), from the world's
 * seed alone: the Nether generator's own noise (its rock, lava and air, and its biomes), never a chunk made.
 * Deterministic and thread-safe, like the Overworld's ({@link KnightRuinSites}): world generation
 * ({@link NetherRuinPlacement}, {@link NetherRuinStructure}), the keeper, the commands and the tests all get the same
 * answers, in any order.
 * <ul>
 *   <li><b>Squares</b>: the Nether in squares {@code netherSpacing} (1,000) blocks a side (Nether blocks), at most one
 *       ruin in each, its middle at least {@link KnightRuinTuning#NETHER_EDGE_SHARE} of the side in from each edge (so
 *       two are never nearer than a third of the spacing). Each square tries up to {@link KnightRuinTuning#NETHER_TRIES}
 *       columns the seed picks; the first that fits is its ruin, and a square where none fits has none.</li>
 *   <li><b>A floor</b>: the Nether has a roof, and floors one over another. In a column, a floor is rock with air over it,
 *       between {@link KnightRuinTuning#NETHER_FLOOR_MIN} (8 over the lava sea) and
 *       {@link KnightRuinTuning#NETHER_FLOOR_MAX}, with at least {@link KnightRuinTuning#NETHER_MIN_OPEN} blocks of air
 *       over it. Its biome (there, at the floor's height) picks the area ({@link RuinAreas#netherArea}: the biome tags
 *       {@code dross:knight_ruin/<area>}), and the seed one of that area's layouts.</li>
 *   <li><b>The footprint</b>: the floor read every {@link KnightRuinTuning#NETHER_SAMPLE_STEP} blocks over it. Every
 *       sample needs a floor near the middle's or rock there (a drop, or the lava sea under it: no ruin, so none ever
 *       floats); at most {@link KnightRuinTuning#NETHER_MAX_WALL} of them rock, at most
 *       {@link KnightRuinTuning#NETHER_MAX_LOW} (rock included) with rock lower over them than the ruin's top (so it's
 *       never buried); the floors varying by at most {@link KnightRuinTuning#NETHER_MAX_RELIEF}. Its ground is their
 *       median, and its middle has open air {@link KnightRuinTuning#NETHER_HEADROOM} past its top.</li>
 *   <li><b>Its way in</b> faces the side with the most open floor round it (at least
 *       {@link KnightRuinTuning#NETHER_FRONT_NEEDED} of three samples walkable).</li>
 *   <li>Never within {@link KnightRuinTuning#NETHER_COMPLEX_CHUNKS} chunks of a possible fortress or bastion start.</li>
 * </ul>
 * Only the world's own Nether ({@link Level#NETHER}) has them: another dimension with the Nether's biomes never does.
 */
public final class NetherRuinSites
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The structure set that places them ({@code data/dross/worldgen/structure_set/nether_knight_ruin.json}). */
    public static final ResourceKey<StructureSet> SET = ResourceKey.create(Registries.STRUCTURE_SET, new ResourceLocation(Dross.MODID, "nether_knight_ruin"));
    /** Their structure ({@code data/dross/worldgen/structure/nether_knight_ruin.json}). */
    public static final ResourceKey<Structure> STRUCTURE = ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(Dross.MODID, "nether_knight_ruin"));

    /** The Nether ruins' own salt (the seed's choices of spot, floor, layout and way in). */
    private static final long SALT = 0x4E65746865725275L;
    private static final long PURPOSE_SQUARE = 0x73717561L;
    /** For the probe: tries made, floors tried and columns read so far. */
    static final java.util.concurrent.atomic.AtomicLong TRIES_MADE = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong FLOORS_TRIED = new java.util.concurrent.atomic.AtomicLong();
    static final java.util.concurrent.atomic.AtomicLong COLUMNS_READ = new java.util.concurrent.atomic.AtomicLong();
    /** A sample with this little air over its floor has no room to stand. */
    private static final int STAND_ROOM = 3;
    /** The open air over a floor is counted at most this far up. */
    private static final int OPEN_CAP = 96;

    // ---------------------------------------------------------------------------------------------
    // The world's context
    // ---------------------------------------------------------------------------------------------

    /** What the work reads: the Nether's generator, noise and heights, its structure state (for the fortresses), the seed,
     * whether it has them at all, the spacing, and the Nether's side of the world spawn. */
    record Context(ChunkGenerator generator, RandomState random, LevelHeightAccessor heights, long seed, boolean available,
                   int spacing, @Nullable ChunkGeneratorStructureState structures, @Nullable Holder<StructureSet> complexes,
                   int spawnX, int spawnZ)
    {
        static Context of(ServerLevel nether)
        {
            ChunkGenerator generator = nether.getChunkSource().getGenerator();
            RandomState random = nether.getChunkSource().randomState();
            ChunkGeneratorStructureState structures = nether.getChunkSource().getGeneratorState();
            boolean available = nether.dimension() == Level.NETHER
                    && structures.possibleStructureSets().stream().anyMatch(s -> s.is(SET))
                    && nether.getServer().getWorldData().worldGenOptions().generateStructures();
            Holder<StructureSet> complexes = structures.possibleStructureSets().stream()
                    .filter(s -> s.is(BuiltinStructureSets.NETHER_COMPLEXES)).findFirst().orElse(null);
            DrossWorldSettings.Values settings = DrossWorldSettings.of(nether);
            // (where the Overworld's spawn will be, from its climate sampler: a new world's isn't set yet when the Nether loads)
            BlockPos spawn = nether.getServer().overworld().getChunkSource().randomState().sampler().findSpawnPosition();
            return new Context(generator, random, LevelHeightAccessor.create(nether.getMinBuildHeight(), nether.getHeight()),
                    nether.getSeed(), available, Math.max(300, settings.netherRuinSpacing()), structures, complexes,
                    Math.floorDiv(spawn.getX(), 8), Math.floorDiv(spawn.getZ(), 8));
        }

        /** For the probe: another seed's Nether with this Nether's generator and the default spacing. */
        static Context forSeed(ServerLevel nether, ChunkGenerator generator, RandomState random, long seed)
        {
            ChunkGeneratorStructureState structures = ChunkGeneratorStructureState.createForNormal(random, seed, generator.getBiomeSource(),
                    nether.registryAccess().lookupOrThrow(Registries.STRUCTURE_SET));
            Holder<StructureSet> complexes = structures.possibleStructureSets().stream()
                    .filter(s -> s.is(BuiltinStructureSets.NETHER_COMPLEXES)).findFirst().orElse(null);
            return new Context(generator, random, LevelHeightAccessor.create(nether.getMinBuildHeight(), nether.getHeight()), seed, true,
                    com.reg21meme.dross.DrossConfig.DEFAULT_NETHER_RUIN_SPACING, structures, complexes, 0, 0);
        }

        /** How far in from a square's edge a ruin's middle keeps. */
        int margin()
        {
            return (int) Math.round(this.spacing * KnightRuinTuning.NETHER_EDGE_SHARE);
        }
    }

    /** One world's work, kept. */
    static final class World
    {
        final Context ctx;
        final ConcurrentHashMap<Long, FutureTask<Optional<RuinSite>>> squares = new ConcurrentHashMap<>();
        final Set<Long> queued = ConcurrentHashMap.newKeySet();
        final ExecutorService warmer;

        World(Context ctx)
        {
            this.ctx = ctx;
            // (one background thread, gone when it's had nothing to do for half a minute: a closed world's goes)
            java.util.concurrent.ThreadPoolExecutor pool = new java.util.concurrent.ThreadPoolExecutor(1, 1, 30L,
                    java.util.concurrent.TimeUnit.SECONDS, new java.util.concurrent.LinkedBlockingQueue<>(), r -> {
                Thread t = new Thread(r, "Dross knight ruins (Nether)");
                t.setDaemon(true);
                t.setPriority(Thread.MIN_PRIORITY);
                return t;
            });
            pool.allowCoreThreadTimeOut(true);
            this.warmer = pool;
        }
    }

    /** Per world, by its Nether's random state (weak, so a closed world's goes). Guarded by itself. */
    private static final Map<RandomState, World> WORLDS = new WeakHashMap<>();

    /** Remembers the Nether and starts working out the squares round its side of the world spawn in the background. */
    public static void register(ServerLevel nether)
    {
        World w = world(nether);
        if (w != null && w.ctx.available())
        {
            queueAround(w, w.ctx.spawnX(), w.ctx.spawnZ(), KnightRuinTuning.NETHER_WARM_AT_LOAD);
        }
    }

    @Nullable
    static World world(ServerLevel nether)
    {
        if (nether.dimension() != Level.NETHER)
        {
            return null;
        }
        RandomState random = nether.getChunkSource().randomState();
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
                ctx = Context.of(nether);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Knight ruins in the Nether: couldn't read the Nether's generator, so it has none", e);
                return null;
            }
            World made = new World(ctx);
            WORLDS.put(random, made);
            if (!ctx.available())
            {
                LOGGER.info("Knight ruins in the Nether: this world's Nether doesn't generate their structure set {} (Generate "
                        + "Structures off, or a Nether that isn't the game's own), so it has none", SET.location());
            }
            else
            {
                LOGGER.info("Knight ruins in the Nether: at most one in each square {} blocks a side{}", ctx.spacing(),
                        ctx.complexes() == null ? "" : ", kept off the fortresses and bastions");
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

    /** True if this world's Nether has knight ruins (not with Generate Structures off). */
    public static boolean available(ServerLevel nether)
    {
        World w = world(nether);
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

    /** The ruin whose structure starts in this chunk, if any (nothing in another dimension's world: it isn't known). */
    @Nullable
    static RuinSite siteStartingIn(RandomState random, int chunkX, int chunkZ)
    {
        World w = known(random);
        if (w == null || !w.ctx.available())
        {
            return null;
        }
        int s = w.ctx.spacing();
        int rx = Math.floorDiv((chunkX << 4) + 8, s);
        int rz = Math.floorDiv((chunkZ << 4) + 8, s);
        // (a chunk wholly outside its square's middle part never holds a ruin's middle: answered without working it out)
        int lo = w.ctx.margin();
        int hi = s - w.ctx.margin();
        int x0 = (chunkX << 4) - rx * s;
        int z0 = (chunkZ << 4) - rz * s;
        if (x0 + 15 < lo || x0 >= hi || z0 + 15 < lo || z0 >= hi)
        {
            return null;
        }
        return square(w, rx, rz).filter(site -> site.startChunk().x == chunkX && site.startChunk().z == chunkZ).orElse(null);
    }

    /** Every ruin within {@code radius} blocks of a spot, nearest first (works the squares out if need be: off the
     * server thread for big radii). */
    public static List<RuinSite> within(ServerLevel nether, int x, int z, int radius)
    {
        World w = world(nether);
        if (w == null || !w.ctx.available())
        {
            return List.of();
        }
        return standing(w, x, z, radius);
    }

    /** The ruins within a radius whose squares are already worked out (never waits: for checks every few ticks); the
     * rest are queued for working out. */
    public static List<RuinSite> readyNear(ServerLevel nether, int x, int z, int radius)
    {
        World w = world(nether);
        if (w == null || !w.ctx.available())
        {
            return List.of();
        }
        List<RuinSite> found = new ArrayList<>();
        int s = w.ctx.spacing();
        for (int rx = Math.floorDiv(x - radius, s); rx <= Math.floorDiv(x + radius, s); rx++)
        {
            for (int rz = Math.floorDiv(z - radius, s); rz <= Math.floorDiv(z + radius, s); rz++)
            {
                FutureTask<Optional<RuinSite>> task = w.squares.get(KnightRuinSites.key(rx, rz));
                if (task != null && task.isDone())
                {
                    await(task, Optional.<RuinSite>empty()).filter(site -> KnightRuinSites.dist(site.x(), site.z(), x, z) <= radius)
                            .ifPresent(found::add);
                }
                else
                {
                    queue(w, rx, rz);
                }
            }
        }
        return found;
    }

    /** The nearest ruin (of an element, or any) within {@code maxRadius} blocks, searching outwards a ring of squares at a time. */
    public static Optional<RuinSite> nearest(ServerLevel nether, int x, int z, @Nullable KnightKind.Element element, int maxRadius)
    {
        World w = world(nether);
        if (w == null || !w.ctx.available())
        {
            return Optional.empty();
        }
        int s = w.ctx.spacing();
        int crx = Math.floorDiv(x, s);
        int crz = Math.floorDiv(z, s);
        RuinSite best = null;
        double bestDist = Double.MAX_VALUE;
        int rings = maxRadius / s + 1;
        for (int ring = 0; ring <= rings; ring++)
        {
            // (a ring's nearest edge is (ring - 1) squares off: past the best found, stop)
            if (best != null && (ring - 1) * (double) s > bestDist)
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
                    Optional<RuinSite> site = square(w, crx + dx, crz + dz);
                    if (site.isPresent() && (element == null || site.get().element() == element))
                    {
                        double d = KnightRuinSites.dist(site.get().x(), site.get().z(), x, z);
                        if (d < bestDist && d <= maxRadius)
                        {
                            best = site.get();
                            bestDist = d;
                        }
                    }
                }
            }
        }
        return Optional.ofNullable(best);
    }

    /** The ruin with this id ({@code nether:x,z}), or null if none is planned there. */
    @Nullable
    public static RuinSite byId(ServerLevel nether, String id)
    {
        if (!RuinSite.isNetherId(id))
        {
            return null;
        }
        String[] parts = id.substring(RuinSite.NETHER_PREFIX.length()).split(",");
        if (parts.length != 2)
        {
            return null;
        }
        try
        {
            int x = Integer.parseInt(parts[0].trim());
            int z = Integer.parseInt(parts[1].trim());
            World w = world(nether);
            if (w == null || !w.ctx.available())
            {
                return null;
            }
            return square(w, Math.floorDiv(x, w.ctx.spacing()), Math.floorDiv(z, w.ctx.spacing()))
                    .filter(site -> site.x() == x && site.z() == z).orElse(null);
        }
        catch (NumberFormatException e)
        {
            return null;
        }
    }

    /** Queues the squares within {@code reach} blocks of a spot for working out in the background. */
    public static void warmAround(ServerLevel nether, int x, int z, int reach)
    {
        World w = world(nether);
        if (w != null && w.ctx.available())
        {
            queueAround(w, x, z, reach);
        }
    }

    private static void queueAround(World w, int x, int z, int reach)
    {
        int s = w.ctx.spacing();
        int crx = Math.floorDiv(x, s);
        int crz = Math.floorDiv(z, s);
        int n = reach / s + 1;
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
        if (w.queued.add(KnightRuinSites.key(rx, rz)))
        {
            w.warmer.execute(() -> square(w, rx, rz));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Squares and their spots
    // ---------------------------------------------------------------------------------------------

    static Optional<RuinSite> square(World w, int rx, int rz)
    {
        FutureTask<Optional<RuinSite>> task = w.squares.computeIfAbsent(KnightRuinSites.key(rx, rz),
                k -> new FutureTask<>(() -> safely(() -> plan(w.ctx, rx, rz), "square " + rx + ", " + rz)));
        return await(task, Optional.empty());
    }

    /** A square's ruin: the first of its tries (columns the seed picks in its middle part) that fits, or none. */
    static Optional<RuinSite> plan(Context ctx, int rx, int rz)
    {
        int s = ctx.spacing();
        int margin = ctx.margin();
        int inner = Math.max(1, s - 2 * margin);
        RandomSource rng = random(ctx, PURPOSE_SQUARE, rx, rz);
        for (int t = 0; t < KnightRuinTuning.NETHER_TRIES; t++)
        {
            int x = rx * s + margin + rng.nextInt(inner);
            int z = rz * s + margin + rng.nextInt(inner);
            TRIES_MADE.incrementAndGet();
            // (each try its own randomness: what one try reads never moves the next try's spot)
            RandomSource own = new XoroshiroRandomSource(rng.nextLong());
            RuinSite site = tryAt(ctx, x, z, own);
            if (site != null)
            {
                return Optional.of(site);
            }
        }
        return Optional.empty();
    }

    /** One spot: its column's floors (at most {@link KnightRuinTuning#NETHER_FLOORS_PER_SPOT}, in a seeded order), each in
     * its biome's area with a layout the seed picks, until one fits. */
    @Nullable
    private static RuinSite tryAt(Context ctx, int x, int z, RandomSource rng)
    {
        if (nearComplex(ctx, x, z))
        {
            return null;
        }
        NoiseColumn middle = column(ctx, x, z);
        List<Integer> floors = new ArrayList<>();
        for (int y = KnightRuinTuning.NETHER_FLOOR_MIN; y <= KnightRuinTuning.NETHER_FLOOR_MAX; y++)
        {
            if (solid(middle, y) && open(middle, y) >= KnightRuinTuning.NETHER_MIN_OPEN)
            {
                floors.add(y);
            }
        }
        shuffle(floors, rng);
        for (int i = 0; i < Math.min(KnightRuinTuning.NETHER_FLOORS_PER_SPOT, floors.size()); i++)
        {
            int floor = floors.get(i);
            FLOORS_TRIED.incrementAndGet();
            KnightRuinDesigns.Area area = RuinAreas.netherArea(biome(ctx, x, floor + 1, z));
            if (area == null)
            {
                continue;
            }
            KnightRuinDesigns.Design design = pick(area, rng);
            if (design == null)
            {
                continue;
            }
            RuinSite site = fit(ctx, x, z, floor, design, rng);
            if (site != null)
            {
                return site;
            }
        }
        return null;
    }

    /** What a column holds near a level: a floor (and the open air over it), rock right through, or nothing under it. */
    private record Sample(int floor, int open, boolean wall)
    {
        static final Sample WALL = new Sample(Integer.MIN_VALUE, 0, true);
        static final Sample HOLE = new Sample(Integer.MIN_VALUE, 0, false);

        boolean isFloor()
        {
            return this.floor != Integer.MIN_VALUE;
        }
    }

    /** A design on a spot's floor: the footprint's samples, its ground (their floors' median), its way in; null if it doesn't fit. */
    @Nullable
    private static RuinSite fit(Context ctx, int x, int z, int floor, KnightRuinDesigns.Design design, RandomSource rng)
    {
        int top = design.layers() - 1 - design.ground();
        int half = Math.max(design.width(), design.depth()) / 2;
        int[] offsets = offsets(half);
        int n = offsets.length * offsets.length;
        int maxWalls = (int) Math.floor(n * KnightRuinTuning.NETHER_MAX_WALL);
        int walls = 0;
        List<Sample> found = new ArrayList<>();
        Sample middle = null;
        for (int dx : offsets)
        {
            for (int dz : offsets)
            {
                Sample s = sample(column(ctx, x + dx, z + dz), floor);
                if (!s.isFloor())
                {
                    if (!s.wall() || ++walls > maxWalls)
                    {
                        return null; // (nothing under it: it would float; or too much rock: it would be buried)
                    }
                    continue;
                }
                found.add(s);
                if (dx == 0 && dz == 0)
                {
                    middle = s;
                }
            }
        }
        if (middle == null || found.isEmpty())
        {
            return null;
        }
        int[] heights = found.stream().mapToInt(Sample::floor).sorted().toArray();
        if (heights[heights.length - 1] - heights[0] > KnightRuinTuning.NETHER_MAX_RELIEF)
        {
            return null;
        }
        int ground = heights[heights.length / 2];
        if (ground < KnightRuinTuning.NETHER_FLOOR_MIN || ground > KnightRuinTuning.NETHER_FLOOR_MAX
                || ground + top + NetherRuinPiece.ABOVE > KnightRuinTuning.NETHER_TOP_MAX)
        {
            return null;
        }
        // the rock over it: the middle open past its top, and not too much of it cut away anywhere
        if (middle.floor() + middle.open() < ground + top + KnightRuinTuning.NETHER_HEADROOM)
        {
            return null;
        }
        int low = walls;
        for (Sample s : found)
        {
            // (its first rock over the floor is at floor + open + 1: inside the ruin's height, it's cut away)
            if (s.floor() + s.open() + 1 <= ground + top)
            {
                low++;
            }
        }
        if (low > n * KnightRuinTuning.NETHER_MAX_LOW)
        {
            return null;
        }
        Direction front = front(ctx, x, z, half, ground, rng);
        return front == null ? null : new RuinSite(x, z, ground, front, design, false);
    }

    /** The side with the most open floor round it, three samples a side {@link KnightRuinTuning#NETHER_FRONT_LOOK} blocks
     * past the footprint (ties in a seeded order); null if even the best has too little. */
    @Nullable
    private static Direction front(Context ctx, int x, int z, int half, int ground, RandomSource rng)
    {
        List<Direction> order = new ArrayList<>(List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST));
        shuffle(order, rng);
        Direction best = null;
        int bestCount = -1;
        int out = half + KnightRuinTuning.NETHER_FRONT_LOOK;
        for (Direction d : order)
        {
            Direction across = d.getClockWise();
            int count = 0;
            for (int a : new int[] {-half / 2, 0, half / 2})
            {
                Sample s = sample(column(ctx, x + d.getStepX() * out + across.getStepX() * a, z + d.getStepZ() * out + across.getStepZ() * a), ground);
                if (s.isFloor() && Math.abs(s.floor() - ground) <= KnightRuinTuning.NETHER_FRONT_STEP && s.open() >= STAND_ROOM)
                {
                    count++;
                }
            }
            if (count > bestCount)
            {
                bestCount = count;
                best = d;
            }
        }
        return bestCount >= KnightRuinTuning.NETHER_FRONT_NEEDED ? best : null;
    }

    /** The floor nearest a level within {@link KnightRuinTuning#NETHER_FLOOR_WINDOW} of it (ties: the higher), and the air
     * over it; or rock there (a wall), or nothing under it (a drop, the lava sea). */
    private static Sample sample(NoiseColumn col, int level)
    {
        int window = KnightRuinTuning.NETHER_FLOOR_WINDOW;
        int best = Integer.MIN_VALUE;
        int bestDist = Integer.MAX_VALUE;
        for (int y = level + window; y >= level - window; y--)
        {
            if (solid(col, y) && col.getBlock(y + 1).isAir())
            {
                int d = Math.abs(y - level);
                if (d < bestDist)
                {
                    best = y;
                    bestDist = d;
                }
            }
        }
        if (best == Integer.MIN_VALUE)
        {
            return solid(col, level + 1) ? Sample.WALL : Sample.HOLE;
        }
        return new Sample(best, open(col, best), false);
    }

    /** Samples across a footprint {@code 2 * half + 1} wide: its middle, every {@link KnightRuinTuning#NETHER_SAMPLE_STEP}
     * out from it, and its edges. */
    private static int[] offsets(int half)
    {
        List<Integer> list = new ArrayList<>();
        list.add(0);
        for (int d = KnightRuinTuning.NETHER_SAMPLE_STEP; d < half; d += KnightRuinTuning.NETHER_SAMPLE_STEP)
        {
            list.add(d);
            list.add(-d);
        }
        if (half > 0)
        {
            list.add(half);
            list.add(-half);
        }
        return list.stream().mapToInt(Integer::intValue).toArray();
    }

    /** The Nether's own blocks in a column (rock, lava, air: before its surface, carvers or features), no chunk made. */
    private static NoiseColumn column(Context ctx, int x, int z)
    {
        COLUMNS_READ.incrementAndGet();
        return ctx.generator().getBaseColumn(x, z, ctx.heights(), ctx.random());
    }

    private static boolean solid(NoiseColumn col, int y)
    {
        BlockState state = col.getBlock(y);
        return !state.isAir() && state.getFluidState().isEmpty();
    }

    /** The air over a floor (blocks of it, up to the first rock or lava). */
    private static int open(NoiseColumn col, int floor)
    {
        int n = 0;
        while (n < OPEN_CAP && col.getBlock(floor + 1 + n).isAir())
        {
            n++;
        }
        return n;
    }

    private static Holder<Biome> biome(Context ctx, int x, int y, int z)
    {
        return ctx.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                ctx.random().sampler());
    }

    /** True if a fortress or bastion could start within {@link KnightRuinTuning#NETHER_COMPLEX_CHUNKS} chunks. */
    private static boolean nearComplex(Context ctx, int x, int z)
    {
        return ctx.complexes() != null && ctx.structures() != null
                && ctx.structures().hasStructureChunkInRange(ctx.complexes(), x >> 4, z >> 4, KnightRuinTuning.NETHER_COMPLEX_CHUNKS);
    }

    @Nullable
    private static KnightRuinDesigns.Design pick(KnightRuinDesigns.Area area, RandomSource rng)
    {
        List<KnightRuinDesigns.Design> designs = KnightRuinDesigns.ALL.stream().filter(d -> d.area() == area).toList();
        return designs.isEmpty() ? null : designs.get(rng.nextInt(designs.size()));
    }

    // ---------------------------------------------------------------------------------------------
    // Landing (/dross goto ruin): the real world, on the server thread
    // ---------------------------------------------------------------------------------------------

    /** A landing is looked for this far round a ruin's doorstep... */
    static final int LANDING_RADIUS = 8;
    /** ... from this far over it down to this far under it (the Nether's floors: never a heightmap, which finds the roof). */
    static final int LANDING_UP = 3;
    static final int LANDING_DOWN = 8;

    /**
     * Where a visitor stands at a Nether ruin (its chunks must be loaded): the nearest safe spot round its doorstep (solid
     * ground that isn't magma, room for a player, no fire or lava there or beside the feet), else its first knight's spot
     * (its design keeps it clear), else empty.
     */
    public static Optional<Vec3> landing(ServerLevel nether, RuinSite site)
    {
        BlockPos door = site.doorstep();
        for (int r = 0; r <= LANDING_RADIUS; r++)
        {
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                    {
                        continue;
                    }
                    for (int dy = LANDING_UP; dy >= -LANDING_DOWN; dy--)
                    {
                        BlockPos feet = door.offset(dx, dy, dz);
                        if (standable(nether, feet))
                        {
                            return Optional.of(Vec3.atBottomCenterOf(feet));
                        }
                    }
                }
            }
        }
        BlockPos guard = site.guardCell(0);
        return standable(nether, guard) ? Optional.of(Vec3.atBottomCenterOf(guard)) : Optional.empty();
    }

    private static boolean standable(ServerLevel level, BlockPos feet)
    {
        BlockPos under = feet.below();
        BlockState ground = level.getBlockState(under);
        if (!ground.isFaceSturdy(level, under, Direction.UP) || ground.is(net.minecraft.world.level.block.Blocks.MAGMA_BLOCK)
                || !ground.getFluidState().isEmpty())
        {
            return false;
        }
        for (BlockPos p : new BlockPos[] {feet, feet.above()})
        {
            BlockState s = level.getBlockState(p);
            if (!s.getCollisionShape(level, p).isEmpty() || !s.getFluidState().isEmpty() || s.is(net.minecraft.tags.BlockTags.FIRE))
            {
                return false;
            }
        }
        for (Direction d : Direction.Plane.HORIZONTAL)
        {
            if (!level.getFluidState(feet.relative(d)).isEmpty())
            {
                return false;
            }
        }
        return true;
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

    private static Optional<RuinSite> safely(java.util.function.Supplier<Optional<RuinSite>> work, String what)
    {
        try
        {
            return work.get();
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Knight ruins in the Nether: working out {} failed", what, e);
            return Optional.empty();
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
            LOGGER.error("Knight ruins in the Nether: working them out failed", e.getCause());
            return fallback;
        }
    }

    /** For the tests: a fresh world's work from a context (nothing kept from before). */
    static World fresh(Context ctx)
    {
        return new World(ctx);
    }

    /** For the tests: the world's work for the Nether. */
    @Nullable
    static World work(ServerLevel nether)
    {
        return world(nether);
    }

    /** The ruins within a radius of a spot in a world's work, nearest first. */
    static List<RuinSite> standing(World w, int x, int z, int radius)
    {
        List<RuinSite> found = new ArrayList<>();
        int s = w.ctx.spacing();
        for (int rx = Math.floorDiv(x - radius, s); rx <= Math.floorDiv(x + radius, s); rx++)
        {
            for (int rz = Math.floorDiv(z - radius, s); rz <= Math.floorDiv(z + radius, s); rz++)
            {
                square(w, rx, rz).filter(site -> KnightRuinSites.dist(site.x(), site.z(), x, z) <= radius).ifPresent(found::add);
            }
        }
        found.sort(Comparator.comparingDouble(site -> KnightRuinSites.dist(site.x(), site.z(), x, z)));
        return found;
    }

    /** For the tests: whether the biome at a ruin's middle (at its ground) is in its area's tag. */
    static boolean inItsBiome(World w, RuinSite site)
    {
        net.minecraft.tags.TagKey<Biome> tag = RuinAreas.netherTag(site.design().area());
        return tag != null && biome(w.ctx, site.x(), site.groundY() + 1, site.z()).is(tag);
    }

    private NetherRuinSites() {}
}
