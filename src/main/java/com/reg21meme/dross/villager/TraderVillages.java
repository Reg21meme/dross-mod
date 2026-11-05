package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.LegacyRandomSource;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.WorldgenRandom;
import net.minecraft.world.level.levelgen.XoroshiroRandomSource;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;
import org.slf4j.Logger;

/**
 * Works out, from the world's seed alone, the one village the Dross trader lives at and where his Rift Chapel goes beside
 * it (the user's decision of November 4, 2026: "only 1 trader, and I still want him to be in villages"). Nothing is
 * generated or loaded: the villages are worked out exactly the way world generation will make them, and the land is read
 * from the generator's noise. {@link RiftChapels} runs it once per world, on a background thread when the world loads.
 * <ol>
 *   <li><b>The possible villages near the spawn point</b> (the climate sampler's, where Minecraft starts looking for the
 *       world spawn), out to {@link #SEARCH_RADIUS} blocks, nearest first ({@link #possibleVillages}: each village
 *       structure set's grid has one possible village chunk per cell; a quick look at its placement rules and biomes).</li>
 *   <li><b>The seed's order</b> ({@link #trialOrder}): the {@link #NEAREST} nearest shuffled, then the rest. Each in turn
 *       is laid out the way world generation will make it (the set's weighted choice of village type, the type's biome rule
 *       and the whole jigsaw layout of streets and houses), so what comes out is the village the land will really get,
 *       piece for piece (modded villages too, if they're tagged {@code #minecraft:village}); the first that is a village
 *       and has room at its edge (below) is his. Laying one out takes about a second, so normally only one is.</li>
 *   <li><b>A spot at each village's edge</b> ({@link #spotIn}): along {@link #RAYS} lines out of its middle, the first
 *       place where the chapel and the ring of land it steps back to the ground keep {@link #PIECE_MARGIN} blocks clear of
 *       every piece of the village (houses, streets, lamps, farms...), its door facing the village; a 3-wide path from its
 *       door, straight out and then round one corner, to the nearest street, at most {@link #MAX_PATH} blocks, never
 *       through a house. The best few by a quick estimate of the land are read properly: the land under the chapel
 *       varying by at most {@link RiftChapels#MAX_RELIEF} blocks, dry (no sea, river or beach), the path's ground dry and
 *       climbing at most about a block a block. The best of those by unevenness, path length and gap to the houses.</li>
 *   <li>If no village in reach has a spot (a world of oceans, say), the chapel stands on its own on the nearest land that
 *       fits ({@link RiftChapels#fit}) to the spawn point, at least {@link #FALLBACK_MIN} blocks out, kept off any
 *       possible village (the old "emergency chapel").</li>
 * </ol>
 * Deterministic: the same answer on any thread, every time, for the same seed and mods.
 */
final class TraderVillages
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---- Tuning ----
    /** Villages are looked for this far from the spawn point (blocks, to the middle of a village's start chunk). */
    static final int SEARCH_RADIUS = 3000;
    /** The seed tries this many possible villages nearest the spawn point first, in an order of its own (see {@link #trialOrder}). */
    static final int NEAREST = 5;
    /** Mixed into the world seed for that pick (any number; changing it moves every world's trader). */
    static final long PICK_SALT = 1104202606L;
    /** The chapel's footprint, grown by its ring ({@link RiftChapelPiece#RING}) and this many blocks more, touches no village piece. */
    static final int PIECE_MARGIN = 2;
    /** How many lines out of a village's middle a spot is looked for along. */
    static final int RAYS = 16;
    /** Along each, spots are tried from this far out of the village's middle (blocks)... */
    static final int RAY_START = 8;
    /** ...every this many blocks... */
    static final int RAY_STEP = 3;
    /** ...out to this far. */
    static final int RAY_MAX = 168;
    /** On each line, the first clear spot and this many more further out ({@link #RAY_STEP} apart) are candidates. */
    static final int RAY_EXTRA = 2;
    /** The path leaves the door straight out for at least this many blocks before it turns. */
    static final int PATH_MIN_OUT = 2;
    /** The path is at most this long (blocks along its middle line). */
    static final int MAX_PATH = 28;
    /** How wide the path is (blocks, odd: a middle line and the same on either side). */
    static final int PATH_WIDTH = 3;
    /** Along the path its ground is read every this many blocks (and at its end). */
    static final int PATH_SAMPLE = 4;
    /** Between two of those reads d blocks apart the ground may rise or fall by at most d plus this much. */
    static final int PATH_SLACK = 1;
    /** Of a village's candidates, the best this many by the quick estimate of the land have it read properly. */
    static final int EXACT_TRIES = 6;
    /** A candidate's score: this much per block of unevenness under the chapel... */
    static final int RELIEF_WEIGHT = 4;
    /** ...plus its path's length, plus this much per block between its ring and the nearest village piece. */
    static final double GAP_WEIGHT = 0.5D;
    /** A possible village chunk's biome is looked at in a 3 x 3 spread of points this many blocks apart round its middle. */
    static final int START_SPREAD = 16;
    /** With no village that has room: chunks out to this many blocks from the spawn point are tried for a chapel of its own... */
    static final int FALLBACK_RADIUS = 1536;
    /** ...every this many chunks... */
    static final int FALLBACK_STEP = 2;
    /** ...never within this many chunks of a possible village's start... */
    static final int FALLBACK_VILLAGE_CLEARANCE = 6;
    /**
     * ...and never nearer the spawn point than this (blocks): out of the spawn area Minecraft makes when a world is created,
     * so that area's chunks never wait for the plan unless a possible village is near them.
     */
    static final int FALLBACK_MIN = 384;
    /** A village's chapel is never further than this from its village's start chunk's middle (blocks): its rays, and room. */
    static final int NEAR_VILLAGE = RAY_MAX + 64;

    /** A chapel is never further than this from the spawn point (blocks): for a quick "no" for every chunk out of reach. */
    static final int REACH = Math.max(SEARCH_RADIUS + RAY_MAX + MAX_PATH + 32, FALLBACK_RADIUS + 32);

    private TraderVillages() {}

    // ---------------------------------------------------------------- what's read

    /**
     * What the work reads: the Overworld's generator, noise and structure placements, the registries and structure
     * templates the jigsaw layouts are made from, its heights, and the spawn point measured from.
     */
    record Context(ChunkGenerator generator, RandomState random, ChunkGeneratorStructureState structures, RegistryAccess registries,
                   StructureTemplateManager templates, LevelHeightAccessor heights, long seed, int spawnX, int spawnZ)
    {
        static Context of(ServerLevel overworld)
        {
            RandomState random = overworld.getChunkSource().randomState();
            ChunkGeneratorStructureState structures = overworld.getChunkSource().getGeneratorState();
            ChunkPos spawn = new ChunkPos(random.sampler().findSpawnPosition());
            return new Context(overworld.getChunkSource().getGenerator(), random, structures, overworld.getServer().registryAccess(),
                    overworld.getServer().getStructureManager(), LevelHeightAccessor.create(overworld.getMinBuildHeight(), overworld.getHeight()),
                    structures.getLevelSeed(), spawn.getMiddleBlockX(), spawn.getMiddleBlockZ());
        }

        /** True if a chapel could start in this chunk (it's within {@link #REACH} of the spawn point). */
        boolean inReach(int chunkX, int chunkZ)
        {
            long dx = (chunkX << 4) + 8 - this.spawnX;
            long dz = (chunkZ << 4) + 8 - this.spawnZ;
            return dx * dx + dz * dz <= (long) REACH * REACH;
        }

        long distanceSqr(int x, int z)
        {
            long dx = x - this.spawnX;
            long dz = z - this.spawnZ;
            return dx * dx + dz * dz;
        }
    }

    /**
     * True if the chapel can't start in this chunk whatever the plan comes to: it's further than {@link #NEAR_VILLAGE} from
     * every possible village and nearer the spawn point than a chapel on its own ever is. For world generation's chunks
     * while the plan is still being worked out (the spawn area, made as the world is created).
     */
    static boolean neverHolds(Context ctx, List<Cell> possible, int chunkX, int chunkZ)
    {
        int x = (chunkX << 4) + 8;
        int z = (chunkZ << 4) + 8;
        if (ctx.distanceSqr(x, z) >= (long) (FALLBACK_MIN - 24) * (FALLBACK_MIN - 24))
        {
            return false;
        }
        long near = (long) NEAR_VILLAGE * NEAR_VILLAGE;
        for (Cell cell : possible)
        {
            long dx = x - ((cell.chunkX() << 4) + 8);
            long dz = z - ((cell.chunkZ() << 4) + 8);
            if (dx * dx + dz * dz <= near)
            {
                return false;
            }
        }
        return true;
    }

    /** One village structure set: its placement grid and its structures. */
    record VillageSet(RandomSpreadStructurePlacement placement, StructureSet set) {}

    /** The world's village structure sets: a grid placement with at least one structure tagged {@code #minecraft:village}. */
    static List<VillageSet> villageSets(ChunkGeneratorStructureState state)
    {
        List<VillageSet> found = new ArrayList<>();
        for (Holder<StructureSet> holder : state.possibleStructureSets())
        {
            StructureSet set = holder.value();
            if (set.placement() instanceof RandomSpreadStructurePlacement placement
                    && set.structures().stream().anyMatch(entry -> entry.structure().is(StructureTags.VILLAGE)))
            {
                found.add(new VillageSet(placement, set));
            }
        }
        return found;
    }

    /** A possible village chunk of one set's grid. */
    record Cell(int set, int chunkX, int chunkZ, long distanceSqr) {}

    /**
     * A village as world generation will make it: its type, its start chunk, the boxes of its pieces (streets apart from
     * the rest) and of the whole, and the middle of its first piece (the town centre).
     */
    record Village(ResourceLocation type, ChunkPos start, BlockPos middle, BoundingBox box, List<BoundingBox> houses, List<BoundingBox> streets,
                   long distanceSqr)
    {
        String describe()
        {
            return this.type + " at " + this.middle.getX() + ", " + this.middle.getZ() + " (" + (this.houses.size() + this.streets.size())
                    + " pieces, " + Math.round(Math.sqrt(this.distanceSqr)) + " blocks from the spawn point)";
        }
    }

    // ---------------------------------------------------------------- the plan

    /** {@link #plan}, timed and logged; empty only if there's no place at all for a chapel. */
    static Optional<RiftChapels.Home> planLogged(Context ctx, List<Cell> possible)
    {
        long start = System.nanoTime();
        Optional<RiftChapels.Home> home = plan(ctx, possible);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        if (home.isPresent())
        {
            LOGGER.info("[Dross] The Dross trader's Rift Chapel, from the seed: {} (worked out in {} ms)", home.get().describe(), millis);
        }
        else
        {
            LOGGER.error("[Dross] No place for the Dross trader's Rift Chapel was found within {} blocks of the spawn point {}, {} "
                    + "({} ms): this world has no trader", Math.max(SEARCH_RADIUS, FALLBACK_RADIUS), ctx.spawnX(), ctx.spawnZ(), millis);
        }
        return home;
    }

    /** {@link #plan} with the world's possible villages worked out first. */
    static Optional<RiftChapels.Home> plan(Context ctx)
    {
        return plan(ctx, possibleVillages(ctx));
    }

    /**
     * The trader's home: a village's chapel if any village in reach has room, else one on its own. Any thread. The possible
     * villages ({@link #possibleVillages}, nearest first) are tried in the seed's order ({@link #trialOrder}): each one's
     * layout is worked out (a second or so of the generator's heights: Minecraft's own work for it, which it does again
     * when its land is made) and the first that is a village with room at its edge is his. So normally only one village is
     * laid out.
     */
    static Optional<RiftChapels.Home> plan(Context ctx, List<Cell> possible)
    {
        List<VillageSet> sets = villageSets(ctx.structures());
        List<Cell> order = trialOrder(ctx, possible);
        int tried = 0;
        int villages = 0;
        for (Cell cell : order)
        {
            tried++;
            long start = System.nanoTime();
            Village village = villageAt(ctx, sets.get(cell.set()), cell);
            long laid = System.nanoTime();
            Optional<RiftChapels.Home> spot = village == null ? Optional.empty() : spotIn(ctx, village);
            LOGGER.info("[Dross] Trader's village, try {}: the possible village chunk {}, {} ({} blocks from the spawn point, No. {} by distance): {}; "
                            + "laid out in {} ms, its edge looked at in {} ms", tried, cell.chunkX(), cell.chunkZ(), Math.round(Math.sqrt(cell.distanceSqr())),
                    possible.indexOf(cell) + 1, village == null ? "no village there" : village.describe() + (spot.isPresent() ? ", room for the chapel"
                            : ", no room for the chapel"), (laid - start) / 1_000_000L, (System.nanoTime() - laid) / 1_000_000L);
            if (village == null)
            {
                continue;
            }
            villages++;
            if (spot.isPresent())
            {
                return Optional.of(spot.get().picked(possible.indexOf(cell) + 1, Math.min(NEAREST, possible.size()), tried, villages));
            }
        }
        LOGGER.warn("[Dross] No village within {} blocks of the spawn point ({} possible village chunks, {} villages) has room for the Dross "
                + "trader's chapel: it stands on its own", SEARCH_RADIUS, tried, villages);
        return fallback(ctx, sets, tried, villages);
    }

    /**
     * Every possible village chunk within {@link #SEARCH_RADIUS} of the spawn point, nearest first, by a quick look
     * (microseconds a chunk, from the noise's smooth heights): its set's placement rules let a structure start there, and
     * the biome at a 3 x 3 spread of points {@value #START_SPREAD} blocks apart round it suits at least one of the set's
     * village types (a village is assumed whenever any of them does, so hardly one is missed; a chunk that turns out to
     * have no village is simply passed over when it's tried). The chapel is only ever at one of these
     * ({@link #NEAR_VILLAGE}), which is how a chunk far from all of them is answered at once.
     */
    static List<Cell> possibleVillages(Context ctx)
    {
        List<VillageSet> sets = villageSets(ctx.structures());
        List<Cell> out = new ArrayList<>();
        int seaLevel = ctx.generator().getSeaLevel();
        for (Cell cell : cells(ctx, sets))
        {
            VillageSet set = sets.get(cell.set());
            if (!set.placement().isStructureChunk(ctx.structures(), cell.chunkX(), cell.chunkZ()))
            {
                continue;
            }
            int x0 = cell.chunkX() << 4;
            int z0 = cell.chunkZ() << 4;
            boolean could = false;
            for (int dx = -1; dx <= 1 && !could; dx++)
            {
                for (int dz = -1; dz <= 1 && !could; dz++)
                {
                    int x = x0 + 8 + dx * START_SPREAD;
                    int z = z0 + 8 + dz * START_SPREAD;
                    int y = RiftChapels.smoothHeight(ctx.random(), seaLevel, x, z);
                    Holder<Biome> biome = ctx.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y),
                            QuartPos.fromBlock(z), ctx.random().sampler());
                    for (StructureSet.StructureSelectionEntry entry : set.set().structures())
                    {
                        if (entry.structure().is(StructureTags.VILLAGE) && entry.structure().value().biomes().contains(biome))
                        {
                            could = true;
                            break;
                        }
                    }
                }
            }
            if (could)
            {
                out.add(cell);
            }
        }
        return out;
    }

    /**
     * The order the possible villages are tried in: the {@link #NEAREST} nearest shuffled by the seed (so his village is a
     * random one of them), then the rest, nearest first.
     */
    static List<Cell> trialOrder(Context ctx, List<Cell> possible)
    {
        int first = Math.min(NEAREST, possible.size());
        List<Cell> order = new ArrayList<>(possible.subList(0, first));
        RandomSource rng = new XoroshiroRandomSource(ctx.seed() ^ PICK_SALT);
        for (int i = order.size() - 1; i > 0; i--)
        {
            int k = rng.nextInt(i + 1);
            Cell swap = order.get(i);
            order.set(i, order.get(k));
            order.set(k, swap);
        }
        order.addAll(possible.subList(first, possible.size()));
        return order;
    }

    /** Every set's possible village chunks within {@link #SEARCH_RADIUS} of the spawn point, nearest first. */
    static List<Cell> cells(Context ctx, List<VillageSet> sets)
    {
        List<Cell> cells = new ArrayList<>();
        long radiusSqr = (long) SEARCH_RADIUS * SEARCH_RADIUS;
        for (int s = 0; s < sets.size(); s++)
        {
            RandomSpreadStructurePlacement placement = sets.get(s).placement();
            int spacing = placement.spacing();
            int minCellX = Math.floorDiv((ctx.spawnX() - SEARCH_RADIUS) >> 4, spacing) - 1;
            int maxCellX = Math.floorDiv((ctx.spawnX() + SEARCH_RADIUS) >> 4, spacing) + 1;
            int minCellZ = Math.floorDiv((ctx.spawnZ() - SEARCH_RADIUS) >> 4, spacing) - 1;
            int maxCellZ = Math.floorDiv((ctx.spawnZ() + SEARCH_RADIUS) >> 4, spacing) + 1;
            for (int cx = minCellX; cx <= maxCellX; cx++)
            {
                for (int cz = minCellZ; cz <= maxCellZ; cz++)
                {
                    ChunkPos chunk = placement.getPotentialStructureChunk(ctx.seed(), cx * spacing, cz * spacing);
                    long d = ctx.distanceSqr(chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
                    if (d <= radiusSqr)
                    {
                        cells.add(new Cell(s, chunk.x, chunk.z, d));
                    }
                }
            }
        }
        cells.sort(Comparator.comparingLong(Cell::distanceSqr).thenComparingInt(Cell::set).thenComparingLong(c -> ChunkPos.asLong(c.chunkX(), c.chunkZ())));
        return cells;
    }

    /**
     * The village world generation makes in this chunk, or null: the steps of {@code ChunkGenerator.createStructures} for
     * one set (its placement's rules, then its structures tried in its weighted, seeded order until one generates), with
     * the structure's own biome rule and its whole layout. Only villages count (a set may hold other structures too).
     */
    @Nullable
    static Village villageAt(Context ctx, VillageSet set, Cell cell)
    {
        if (!set.placement().isStructureChunk(ctx.structures(), cell.chunkX(), cell.chunkZ()))
        {
            return null;
        }
        ChunkPos chunk = new ChunkPos(cell.chunkX(), cell.chunkZ());
        List<StructureSet.StructureSelectionEntry> entries = set.set().structures();
        StructureSet.StructureSelectionEntry chosen = null;
        StructureStart start = null;
        if (entries.size() == 1)
        {
            chosen = entries.get(0);
            start = generate(ctx, chosen, chunk);
        }
        else
        {
            List<StructureSet.StructureSelectionEntry> left = new ArrayList<>(entries);
            WorldgenRandom random = new WorldgenRandom(new LegacyRandomSource(0L));
            random.setLargeFeatureSeed(ctx.seed(), chunk.x, chunk.z);
            int total = 0;
            for (StructureSet.StructureSelectionEntry entry : left)
            {
                total += entry.weight();
            }
            while (!left.isEmpty())
            {
                int roll = random.nextInt(total);
                int index = 0;
                for (StructureSet.StructureSelectionEntry entry : left)
                {
                    roll -= entry.weight();
                    if (roll < 0)
                    {
                        break;
                    }
                    index++;
                }
                StructureSet.StructureSelectionEntry entry = left.get(index);
                StructureStart made = generate(ctx, entry, chunk);
                if (made != null)
                {
                    chosen = entry;
                    start = made;
                    break;
                }
                left.remove(index);
                total -= entry.weight();
            }
        }
        if (start == null || !chosen.structure().is(StructureTags.VILLAGE) || start.getPieces().isEmpty())
        {
            return null;
        }
        List<BoundingBox> houses = new ArrayList<>();
        List<BoundingBox> streets = new ArrayList<>();
        BoundingBox all = null;
        for (StructurePiece piece : start.getPieces())
        {
            BoundingBox b = piece.getBoundingBox();
            (isStreet(piece) ? streets : houses).add(b);
            all = all == null ? b : encapsulate(all, b);
        }
        BoundingBox first = start.getPieces().get(0).getBoundingBox();
        BlockPos middle = new BlockPos((first.minX() + first.maxX()) / 2, first.minY(), (first.minZ() + first.maxZ()) / 2);
        ResourceLocation type = chosen.structure().unwrapKey().map(ResourceKey::location).orElse(new ResourceLocation("unknown"));
        return new Village(type, chunk, middle, all, List.copyOf(houses), List.copyOf(streets), cell.distanceSqr());
    }

    /** One structure's start in the chunk, as world generation makes it ({@code ChunkGenerator.tryGenerateStructure}), or null. */
    @Nullable
    private static StructureStart generate(Context ctx, StructureSet.StructureSelectionEntry entry, ChunkPos chunk)
    {
        Structure structure = entry.structure().value();
        StructureStart start = structure.generate(ctx.registries(), ctx.generator(), ctx.generator().getBiomeSource(), ctx.random(),
                ctx.templates(), ctx.seed(), chunk, 0, ctx.heights(), structure.biomes()::contains);
        return start.isValid() ? start : null;
    }

    /**
     * Street pieces have "streets" (the roads) or "terminators" (their dead ends) in their template name, as Minecraft's
     * do ({@code village/plains/streets/...}); everything else (houses, farms, wells, lamps, animals) is a house here.
     */
    private static boolean isStreet(StructurePiece piece)
    {
        if (!(piece instanceof PoolElementStructurePiece pool))
        {
            return false;
        }
        String name = pool.getElement().toString();
        return name.contains("streets") || name.contains("terminators");
    }

    private static BoundingBox encapsulate(BoundingBox a, BoundingBox b)
    {
        return new BoundingBox(Math.min(a.minX(), b.minX()), Math.min(a.minY(), b.minY()), Math.min(a.minZ(), b.minZ()),
                Math.max(a.maxX(), b.maxX()), Math.max(a.maxY(), b.maxY()), Math.max(a.maxZ(), b.maxZ()));
    }

    // ---------------------------------------------------------------- the spot at a village's edge

    /** A spot being considered: the chapel's middle (on the land's rough height), its door's way, its path, the estimate. */
    private record Candidate(int x, int z, Direction facing, List<BlockPos> path, int pathLength, double gap, double estimate, int order) {}

    /** A candidate whose land was read properly: its floor's level and its score (lower is better). */
    private record Checked(Candidate candidate, int floorY, int relief, double score) {}

    /**
     * The best spot for the chapel at this village's edge (see the class's description), or empty if none fits. Reads the
     * land from the noise (a few dozen columns at about a millisecond each); deterministic.
     */
    static Optional<RiftChapels.Home> spotIn(Context ctx, Village village)
    {
        List<BoundingBox> pieces = new ArrayList<>(village.houses());
        pieces.addAll(village.streets());
        List<BoundingBox> targets = village.streets().isEmpty() ? pieces : village.streets();
        int keep = RiftChapelPiece.RING + PIECE_MARGIN;
        ChunkGenerator generator = ctx.generator();
        int seaLevel = generator.getSeaLevel();
        List<Candidate> candidates = new ArrayList<>();
        for (int ray = 0; ray < RAYS; ray++)
        {
            double angle = 2.0D * Math.PI * ray / RAYS;
            double ux = Math.cos(angle);
            double uz = Math.sin(angle);
            int extra = -1;
            for (int r = RAY_START; r <= RAY_MAX && extra < RAY_EXTRA; r += RAY_STEP)
            {
                int x = village.middle().getX() + (int) Math.round(ux * r);
                int z = village.middle().getZ() + (int) Math.round(uz * r);
                Direction toward = Direction.getNearest(village.middle().getX() - x, 0, village.middle().getZ() - z);
                if (!toward.getAxis().isHorizontal())
                {
                    continue;
                }
                BoundingBox foot = TraderHut.DESIGN.footprint(new BlockPos(x, 0, z), toward);
                if (gap(pieces, foot, keep) < 0.0D)
                {
                    continue;
                }
                extra++;
                Candidate c = candidate(ctx, x, z, toward, foot, pieces, targets, keep, seaLevel, candidates.size());
                if (c != null)
                {
                    candidates.add(c);
                }
                // (the door turned to the nearest street too, if that's another way)
                Direction toStreet = towardNearest(targets, x, z);
                if (toStreet != null && toStreet != toward)
                {
                    BoundingBox turned = TraderHut.DESIGN.footprint(new BlockPos(x, 0, z), toStreet);
                    if (gap(pieces, turned, keep) >= 0.0D)
                    {
                        Candidate t = candidate(ctx, x, z, toStreet, turned, pieces, targets, keep, seaLevel, candidates.size());
                        if (t != null)
                        {
                            candidates.add(t);
                        }
                    }
                }
            }
        }
        if (candidates.isEmpty())
        {
            return Optional.empty();
        }
        candidates.sort(Comparator.comparingDouble(Candidate::estimate).thenComparingInt(Candidate::order));
        Long2IntOpenHashMap grounds = new Long2IntOpenHashMap();
        grounds.defaultReturnValue(Integer.MAX_VALUE);
        LevelHeightAccessor band = RiftChapels.band(generator);
        Checked best = null;
        for (Candidate c : candidates.subList(0, Math.min(EXACT_TRIES, candidates.size())))
        {
            Checked checked = check(ctx, band, grounds, c);
            if (checked != null && (best == null || checked.score() < best.score()))
            {
                best = checked;
            }
        }
        if (best == null)
        {
            return Optional.empty();
        }
        Candidate c = best.candidate();
        BlockPos center = new BlockPos(c.x(), best.floorY(), c.z());
        RiftChapels.Chapel chapel = new RiftChapels.Chapel(center.getX() >> 4, center.getZ() >> 4, center, c.facing());
        return Optional.of(new RiftChapels.Home(chapel, true, village.start().toLong(), village.type(), village.middle(), c.path(),
                pathBlock(village.type()), Math.round(Math.sqrt(village.distanceSqr())), best.relief(), 0, 0, 0, 0));
    }

    /**
     * The candidate with its door facing {@code facing}, or null: its path (straight out at least {@link #PATH_MIN_OUT}
     * blocks, then round one corner to the nearest street), at most {@link #MAX_PATH} long and through no house, and its
     * quick estimate from the noise's smooth heights.
     */
    @Nullable
    private static Candidate candidate(Context ctx, int x, int z, Direction facing, BoundingBox foot, List<BoundingBox> pieces,
                                       List<BoundingBox> targets, int keep, int seaLevel, int order)
    {
        BlockPos entrance = TraderHut.DESIGN.entrance(new BlockPos(x, 0, z), facing);
        int[] target = nearestPoint(targets, entrance.getX(), entrance.getZ());
        int fx = facing.getStepX();
        int fz = facing.getStepZ();
        int px = -fz;
        int pz = fx;
        int out = (target[0] - entrance.getX()) * fx + (target[1] - entrance.getZ()) * fz;
        int side = (target[0] - entrance.getX()) * px + (target[1] - entrance.getZ()) * pz;
        if (out < PATH_MIN_OUT || out + Math.abs(side) > MAX_PATH)
        {
            return null;
        }
        List<BlockPos> line = new ArrayList<>();
        line.add(new BlockPos(entrance.getX(), 0, entrance.getZ()));
        BlockPos corner = new BlockPos(entrance.getX() + fx * out, 0, entrance.getZ() + fz * out);
        line.add(corner);
        if (side != 0)
        {
            line.add(new BlockPos(corner.getX() + px * side, 0, corner.getZ() + pz * side));
        }
        line = cutAtTarget(line, targets);
        // Its cells: never in the chapel or a house (a street it reaches is where it ends).
        LongLinkedOpenHashSet cells = RiftChapelPathPiece.cells(line, PATH_WIDTH / 2);
        for (long cell : cells)
        {
            int cx = ChunkPos.getX(cell);
            int cz = ChunkPos.getZ(cell);
            if (inside(foot, cx, cz))
            {
                return null;
            }
            for (BoundingBox house : pieces)
            {
                if (!targets.contains(house) && inside(house, cx, cz))
                {
                    return null;
                }
            }
        }
        int length = pathLength(line);
        double gap = gap(pieces, foot, keep);
        // The quick estimate: the smooth heights' spread over the chapel's columns, its path's length, the gap.
        int[][] columns = columns(x, z, facing);
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int[] at : columns)
        {
            int h = RiftChapels.smoothHeight(ctx.random(), seaLevel, at[0], at[1]);
            low = Math.min(low, h);
            high = Math.max(high, h);
        }
        double estimate = RELIEF_WEIGHT * (high - low) + length + GAP_WEIGHT * gap;
        return new Candidate(x, z, facing, List.copyOf(line), length, gap, estimate, order);
    }

    /**
     * The candidate's land read properly from the noise, or null if it doesn't fit: the chapel's nine columns dry and
     * within {@link RiftChapels#MAX_RELIEF} of each other (its floor at their median), no sea, river or beach under its
     * middle or corners, and its path's ground dry and gentle.
     */
    @Nullable
    private static Checked check(Context ctx, LevelHeightAccessor band, Long2IntOpenHashMap grounds, Candidate c)
    {
        int[][] columns = columns(c.x(), c.z(), c.facing());
        int[] heights = new int[columns.length];
        int low = Integer.MAX_VALUE;
        int high = Integer.MIN_VALUE;
        for (int i = 0; i < columns.length; i++)
        {
            int ground = ground(ctx, band, grounds, columns[i][0], columns[i][1]);
            if (ground == RiftChapels.NO_GROUND)
            {
                return null;
            }
            heights[i] = ground;
            low = Math.min(low, ground);
            high = Math.max(high, ground);
        }
        if (high - low > RiftChapels.MAX_RELIEF)
        {
            return null;
        }
        Arrays.sort(heights);
        int floorY = heights[heights.length / 2];
        for (int i = 0; i < 5; i++)
        {
            if (!dry(ctx, columns[i][0], floorY, columns[i][1]))
            {
                return null;
            }
        }
        // The path: read every few blocks along its middle line (and at its end), each dry, the climb gentle.
        int last = floorY;
        int sinceLast = 0;
        List<BlockPos> line = c.path();
        int steps = pathLength(line);
        for (int s = 1; s <= steps; s++)
        {
            sinceLast++;
            if (s % PATH_SAMPLE != 0 && s != steps)
            {
                continue;
            }
            BlockPos at = pointAlong(line, s);
            int ground = ground(ctx, band, grounds, at.getX(), at.getZ());
            if (ground == RiftChapels.NO_GROUND || Math.abs(ground - last) > sinceLast + PATH_SLACK)
            {
                return null;
            }
            last = ground;
            sinceLast = 0;
        }
        double score = RELIEF_WEIGHT * (high - low) + c.pathLength() + GAP_WEIGHT * c.gap();
        return new Checked(c, floorY, high - low, score);
    }

    /** A column's ground from the noise (remembered for the village's other candidates). */
    private static int ground(Context ctx, LevelHeightAccessor band, Long2IntOpenHashMap grounds, int x, int z)
    {
        long key = ChunkPos.asLong(x, z);
        int known = grounds.get(key);
        if (known != Integer.MAX_VALUE)
        {
            return known;
        }
        int read = RiftChapels.ground(ctx.generator(), ctx.random(), band, x, z);
        grounds.put(key, read);
        return read;
    }

    /** No ocean, river or beach at that point. */
    private static boolean dry(Context ctx, int x, int y, int z)
    {
        Holder<Biome> biome = ctx.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(y), QuartPos.fromBlock(z),
                ctx.random().sampler());
        return !biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER) && !biome.is(BiomeTags.IS_BEACH);
    }

    /**
     * The nine columns the chapel's land is read at: its middle, then the corners and the middles of the sides of its
     * footprint grown by {@link RiftChapels#FIT_EDGE} (the middle and the corners first: the dry-land check reads those).
     */
    private static int[][] columns(int x, int z, Direction facing)
    {
        BoundingBox foot = TraderHut.DESIGN.footprint(new BlockPos(x, 0, z), facing);
        int x0 = foot.minX() - RiftChapels.FIT_EDGE;
        int x1 = foot.maxX() + RiftChapels.FIT_EDGE;
        int z0 = foot.minZ() - RiftChapels.FIT_EDGE;
        int z1 = foot.maxZ() + RiftChapels.FIT_EDGE;
        return new int[][] {{x, z}, {x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}, {x, z0}, {x, z1}, {x0, z}, {x1, z}};
    }

    /** The blocks between the footprint grown by {@code keep} and the nearest piece (box to box, flat), or -1 if it touches one. */
    private static double gap(List<BoundingBox> pieces, BoundingBox foot, int keep)
    {
        long nearest = Long.MAX_VALUE;
        for (BoundingBox b : pieces)
        {
            int dx = Math.max(0, Math.max(b.minX() - (foot.maxX() + keep), (foot.minX() - keep) - b.maxX()));
            int dz = Math.max(0, Math.max(b.minZ() - (foot.maxZ() + keep), (foot.minZ() - keep) - b.maxZ()));
            if (dx == 0 && dz == 0)
            {
                return -1.0D;
            }
            nearest = Math.min(nearest, (long) dx * dx + (long) dz * dz);
        }
        return nearest == Long.MAX_VALUE ? 0.0D : Math.sqrt(nearest);
    }

    /** The nearest point (x, z) of any of the boxes to (x, z), flat. */
    private static int[] nearestPoint(List<BoundingBox> boxes, int x, int z)
    {
        int[] best = null;
        long bestSqr = Long.MAX_VALUE;
        for (BoundingBox b : boxes)
        {
            int nx = Mth.clamp(x, b.minX(), b.maxX());
            int nz = Mth.clamp(z, b.minZ(), b.maxZ());
            long d = (long) (nx - x) * (nx - x) + (long) (nz - z) * (nz - z);
            if (d < bestSqr)
            {
                bestSqr = d;
                best = new int[] {nx, nz};
            }
        }
        return best;
    }

    /** The way (of the four) from (x, z) towards the nearest street, or null if there's none. */
    @Nullable
    private static Direction towardNearest(List<BoundingBox> targets, int x, int z)
    {
        int[] p = nearestPoint(targets, x, z);
        if (p == null || (p[0] == x && p[1] == z))
        {
            return null;
        }
        Direction d = Direction.getNearest(p[0] - x, 0, p[1] - z);
        return d.getAxis().isHorizontal() ? d : null;
    }

    /** The middle line cut where it first reaches a street (it ends on that street's edge). */
    private static List<BlockPos> cutAtTarget(List<BlockPos> line, List<BoundingBox> targets)
    {
        int steps = pathLength(line);
        for (int s = 0; s <= steps; s++)
        {
            BlockPos at = pointAlong(line, s);
            for (BoundingBox t : targets)
            {
                if (inside(t, at.getX(), at.getZ()))
                {
                    return lineTo(line, s);
                }
            }
        }
        return line;
    }

    /** The middle line's first {@code steps} blocks (its corners up to there and the point it stops at). */
    private static List<BlockPos> lineTo(List<BlockPos> line, int steps)
    {
        List<BlockPos> out = new ArrayList<>();
        out.add(line.get(0));
        int done = 0;
        for (int i = 0; i + 1 < line.size(); i++)
        {
            int leg = line.get(i).distManhattan(line.get(i + 1));
            if (done + leg >= steps)
            {
                BlockPos end = pointAlong(line, steps);
                if (!end.equals(out.get(out.size() - 1)))
                {
                    out.add(end);
                }
                return out;
            }
            done += leg;
            out.add(line.get(i + 1));
        }
        return out;
    }

    /** The middle line's point {@code s} blocks along it. */
    static BlockPos pointAlong(List<BlockPos> line, int s)
    {
        int left = s;
        for (int i = 0; i + 1 < line.size(); i++)
        {
            BlockPos a = line.get(i);
            BlockPos b = line.get(i + 1);
            int leg = a.distManhattan(b);
            if (left <= leg)
            {
                return new BlockPos(a.getX() + Integer.signum(b.getX() - a.getX()) * left, 0, a.getZ() + Integer.signum(b.getZ() - a.getZ()) * left);
            }
            left -= leg;
        }
        return line.get(line.size() - 1);
    }

    /** The middle line's length in blocks. */
    static int pathLength(List<BlockPos> line)
    {
        int length = 0;
        for (int i = 0; i + 1 < line.size(); i++)
        {
            length += line.get(i).distManhattan(line.get(i + 1));
        }
        return length;
    }

    private static boolean inside(BoundingBox b, int x, int z)
    {
        return x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ();
    }

    /** What a village's path is made of: its own streets' block (smooth sandstone in a desert village, else dirt paths). */
    static Block pathBlock(ResourceLocation villageType)
    {
        return villageType.getPath().contains("desert") ? Blocks.SMOOTH_SANDSTONE : Blocks.DIRT_PATH;
    }

    // ---------------------------------------------------------------- no village with room

    /**
     * A chapel on its own: the nearest chunk to the spawn point (every {@link #FALLBACK_STEP} chunks, from
     * {@link #FALLBACK_MIN} out to {@link #FALLBACK_RADIUS} blocks) where one fits ({@link RiftChapels#fit}) and no possible
     * village starts within {@link #FALLBACK_VILLAGE_CLEARANCE} chunks.
     */
    private static Optional<RiftChapels.Home> fallback(Context ctx, List<VillageSet> sets, int looked, int villages)
    {
        int spawnChunkX = ctx.spawnX() >> 4;
        int spawnChunkZ = ctx.spawnZ() >> 4;
        int rings = FALLBACK_RADIUS / 16 / FALLBACK_STEP;
        for (int ring = 0; ring <= rings; ring++)
        {
            List<int[]> spots = new ArrayList<>();
            for (int dx = -ring; dx <= ring; dx++)
            {
                for (int dz = -ring; dz <= ring; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring)
                    {
                        spots.add(new int[] {spawnChunkX + dx * FALLBACK_STEP, spawnChunkZ + dz * FALLBACK_STEP});
                    }
                }
            }
            spots.sort(Comparator.comparingLong((int[] s) -> ctx.distanceSqr((s[0] << 4) + 8, (s[1] << 4) + 8))
                    .thenComparingLong(s -> ChunkPos.asLong(s[0], s[1])));
            for (int[] spot : spots)
            {
                if (ctx.distanceSqr((spot[0] << 4) + 8, (spot[1] << 4) + 8) < (long) FALLBACK_MIN * FALLBACK_MIN
                        || nearPossibleVillage(ctx, sets, spot[0], spot[1]))
                {
                    continue;
                }
                Optional<RiftChapels.Chapel> chapel = RiftChapels.fit(ctx.generator(), ctx.random(), ctx.seed(), spot[0], spot[1]);
                if (chapel.isPresent())
                {
                    RiftChapels.Chapel c = chapel.get();
                    long d = Math.round(Math.sqrt(ctx.distanceSqr(c.center().getX(), c.center().getZ())));
                    return Optional.of(new RiftChapels.Home(c, false, c.startChunk().toLong(), null, null, List.of(), Blocks.DIRT_PATH, d, 0, 1, 1,
                            looked, villages));
                }
            }
        }
        return Optional.empty();
    }

    /** True if any village set could start a village within {@link #FALLBACK_VILLAGE_CLEARANCE} chunks of the chunk. */
    private static boolean nearPossibleVillage(Context ctx, List<VillageSet> sets, int chunkX, int chunkZ)
    {
        int r = FALLBACK_VILLAGE_CLEARANCE;
        for (VillageSet set : sets)
        {
            int spacing = set.placement().spacing();
            for (int cx = Math.floorDiv(chunkX - r, spacing); cx <= Math.floorDiv(chunkX + r, spacing); cx++)
            {
                for (int cz = Math.floorDiv(chunkZ - r, spacing); cz <= Math.floorDiv(chunkZ + r, spacing); cz++)
                {
                    ChunkPos p = set.placement().getPotentialStructureChunk(ctx.seed(), cx * spacing, cz * spacing);
                    if (Math.abs(p.x - chunkX) <= r && Math.abs(p.z - chunkZ) <= r && set.placement().isStructureChunk(ctx.structures(), p.x, p.z))
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
