package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.villager.hut.HutDesign;
import com.reg21meme.dross.villager.hut.HutDesigns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * The Dross trader's hut: picking a spot in the village, building the hut, and laying a wide path from its door
 * to the village's own streets. What gets built is {@link #DESIGN}, today the Rift Chapel (see
 * {@code villager.hut}): 9 x 13 blocks on the ground and 18 high, with the door in one short side.
 * <p>
 * <b>Which spot.</b> The chapel sits among or at the edge of the village's houses. Every couple of blocks in and
 * around the village, the hut is tried facing all four ways. A try is allowed if
 * <ul>
 *   <li>its footprint, grown by {@link #PIECE_MARGIN} blocks, touches no piece of the village (houses, streets,
 *       lamp posts, wells, hay... every piece has a bounding box), and covers no village path block;</li>
 *   <li>the ground under the whole footprint is dry and level enough: bumps may be cut away by up to
 *       {@link #MAX_CUT} blocks and dips filled in by up to {@link #MAX_FILL} (so the ground may vary by up to
 *       {@link #MAX_CUT} + {@link #MAX_FILL} blocks). The floor goes at the height that needs the least cutting
 *       plus filling (on a tie, the one that cuts less); the build clears everything above the floor and fills
 *       dips with dirt. The doorstep ground must be within {@link #DOORSTEP_STEP} of the floor, so the path
 *       can start.</li>
 * </ul>
 * It is scored by how close it is to the village's pieces ({@link #GAP_WEIGHT} per block), how far its door is
 * from the nearest village path block, and penalties for uneven ground and for earthworks. The best few get a
 * path search, and the first one that connects wins.
 * <p>
 * <b>The path</b> is {@link #PATH_WIDTH} blocks wide, from the doorstep to the nearest village path block
 * (the nearest street, not the village center). Its middle line is searched with a preference for straight runs;
 * every column the strip covers must be walkable ground (no fluid, logs, houses or the hut, and no steps over one
 * block). It never replaces village-made blocks: only natural ground is paved, and it stops at the first village
 * path block it touches.
 * <p>
 * <b>The path material</b> is whatever the village's streets are made of: the commonest top block on the street
 * pieces that isn't plain terrain. That is {@code dirt_path} for plains, savanna, taiga and snowy villages and
 * {@code smooth_sandstone} for desert villages (checked in the 1.20.1 village templates), and
 * {@code dirt_path} if it can't be told.
 */
public final class TraderHut
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The hut design the trader lives in. This is the one line to change if another design is ever used. */
    static final HutDesign DESIGN = HutDesigns.RIFT_CHAPEL;

    /** How far outside the village's bounding box we look for a spot (for the hut's center, in blocks). */
    private static final int SEARCH_MARGIN = 24;
    /**
     * Free space kept between the hut and every village piece's bounding box, in blocks. The hut's footprint grown
     * by this much must not touch any piece, so it never overlaps (or crowds) a house, street or decoration.
     */
    private static final int PIECE_MARGIN = 2;
    /** The floor may sit at most this many blocks below the highest ground under the hut (bumps are cut away). */
    static final int MAX_CUT = 3;
    /** The floor may sit at most this many blocks above the lowest ground under the hut (dips are filled in). */
    static final int MAX_FILL = 3;
    /**
     * The ground in front of the door (the doorstep row) must be within this many blocks of the floor. The row is
     * levelled to the floor, and the path beyond it steps one block at a time.
     */
    static final int DOORSTEP_STEP = 1;
    /** A spot's score is its door's distance to a village path in blocks, plus this much per block of uneven ground... */
    private static final int FLATNESS_PENALTY = 4;
    /** ...plus this much per block of earth cut or filled under the hut (summed over all its columns)... */
    private static final double EARTHWORK_WEIGHT = 0.1D;
    /** ...plus this much per block between the hut and the nearest village piece (lower is closer to the houses). */
    private static final double GAP_WEIGHT = 1.0D;
    /** Hut centers are tried every this many blocks. */
    private static final int CANDIDATE_STEP = 2;
    /** How many of the best-scoring spots we try to connect with a path. */
    private static final int SPOTS_TO_TRY = 12;
    /** The path search never goes further than this from the door (in blocks). */
    private static final int PATH_SEARCH_RADIUS = 64;
    /** How wide the path is, in blocks, across its direction of travel. Must be odd (the middle line is the center). */
    static final int PATH_WIDTH = 3;
    /** How far the path covers either side of its middle line. */
    private static final int PATH_RADIUS = PATH_WIDTH / 2;
    /** The path search charges this much (a step costs 1) for every turn, so paths prefer long straight runs. */
    private static final int TURN_COST = 3;
    /** A path material needs at least this many street columns to be believed; otherwise dirt paths are used. */
    private static final int MIN_MATERIAL_SAMPLES = 6;

    /**
     * A chosen hut spot.
     * @param center    the design's center: the ground block in the middle of the footprint (the floor replaces it)
     * @param facing    the way the door faces, out of the hut
     * @param path      the ground blocks to pave: the block in front of the door first, then the rest of the doorstep
     *                  row ({@link #PATH_WIDTH} wide, all levelled with the floor), then the strip to the village
     * @param pathBlock what the path is made of (the village's own path material)
     */
    public record Site(BlockPos center, Direction facing, List<BlockPos> path, Block pathBlock)
    {
        /** Where the trader stands inside, and teleports home to. */
        public BlockPos traderSpot()
        {
            return DESIGN.traderSpot(center, facing);
        }

        /** Everything the hut covers, from the ground layer up to its highest point. It is saved on the trader. */
        public BoundingBox footprint()
        {
            return DESIGN.footprint(center, facing);
        }

        /** The ground block in front of the door, where the path starts. */
        public BlockPos entrance()
        {
            return DESIGN.entrance(center, facing);
        }
    }

    /** One way the door can face, with the footprint and the door-front block measured from the hut's center. */
    private record Orientation(Direction facing, BoundingBox footprint, BlockPos entrance) {}

    /** A hut spot being considered. The center's Y is the floor height. Lower scores are better. */
    private record Candidate(BlockPos center, Direction facing, double score) {}

    /**
     * The floor height chosen for a footprint, how uneven the ground under it is (highest minus lowest), and the
     * earthwork it takes: the number of blocks cut away plus blocks filled in, over all columns.
     */
    record Floor(int y, int variation, int work) {}

    /** The four orientations. The footprint and entrance are worked out once, as offsets from the center. */
    private static final List<Orientation> ORIENTATIONS = orientations();

    private TraderHut() {}

    private static List<Orientation> orientations()
    {
        List<Orientation> list = new ArrayList<>();
        for (Direction facing : Direction.Plane.HORIZONTAL)
        {
            list.add(new Orientation(facing,
                    DESIGN.footprint(BlockPos.ZERO, facing),
                    DESIGN.entrance(BlockPos.ZERO, facing)));
        }
        return List.copyOf(list);
    }

    /**
     * How far from the village's bounding box the search reads blocks: the furthest hut center
     * ({@link #SEARCH_MARGIN}), plus half the hut's size, plus one for the block in front of its door, plus the
     * path's half width. {@link TraderSpawner} generates at least this much around the village before it searches.
     */
    public static int searchReach()
    {
        return SEARCH_MARGIN + Math.max(DESIGN.width(), DESIGN.depth()) / 2 + 1 + PATH_RADIUS;
    }

    // ---------------------------------------------------------------- choosing a spot

    /**
     * Finds a spot in or beside the village, close to its houses and clear of every piece of it, and a
     * {@link #PATH_WIDTH}-wide ground-following path from the door to the nearest village path.
     * Returns null if there is no such spot. The village's chunks must already be generated: columns in
     * chunks that aren't are treated as walls, and no chunk is ever generated here.
     */
    public static Site findVillageSite(ServerLevel level, StructureStart village)
    {
        BoundingBox box = village.getBoundingBox();
        List<BoundingBox> houses = new ArrayList<>();
        List<BoundingBox> streets = new ArrayList<>();
        for (StructurePiece piece : village.getPieces())
        {
            (isStreet(piece) ? streets : houses).add(piece.getBoundingBox());
        }

        // Every column the search can reach, read once.
        int reach = searchReach();
        Ground ground = new Ground(level, false,
                box.minX() - reach, box.minZ() - reach, box.maxX() + reach, box.maxZ() + reach);
        return search(ground, box, houses, streets, level.getMaxBuildHeight());
    }

    /**
     * The search itself, on ground that has already been read: the path material is detected, then every candidate
     * hut center in and around the village box is scored, then the best few are tried with a path search.
     *
     * @param box        the village's bounding box
     * @param houses     the bounding boxes of every piece that isn't a street: houses, farms, wells, lamp posts...
     *                   (paths never cross them)
     * @param streets    the bounding boxes of the village's street pieces
     * @param buildLimit the world's build limit: the hut must end below it
     */
    private static Site search(Ground ground, BoundingBox box, List<BoundingBox> houses, List<BoundingBox> streets,
                               int buildLimit)
    {
        long startedAt = System.nanoTime();

        // What the path is made of, and which columns are the village's own path blocks.
        Block material = detectPathMaterial(ground, streets, houses);
        ground.markRoads(material, streets, houses);

        // What the new path should connect to: the village's path blocks. If none can be told apart (a village
        // whose street top blocks are not what we detected), fall back to "anywhere on a street piece".
        List<BlockPos> targets = findRoads(ground, box);
        boolean useStreets = targets.isEmpty();
        if (useStreets)
        {
            targets = streetSurface(ground, streets);
        }
        if (targets.isEmpty())
        {
            LOGGER.info("[Dross] Trader hut spot search: the village has no street to connect to.");
            return null;
        }
        Set<Long> targetColumns = new HashSet<>();
        int[] targetX = new int[targets.size()];
        int[] targetZ = new int[targets.size()];
        for (int i = 0; i < targets.size(); i++)
        {
            BlockPos t = targets.get(i);
            targetColumns.add(column(t.getX(), t.getZ()));
            targetX[i] = t.getX();
            targetZ[i] = t.getZ();
        }

        // Every piece of the village, for the "keep clear" and "stay close" tests.
        List<BoundingBox> pieces = new ArrayList<>(houses);
        pieces.addAll(streets);

        // Every hut center, facing every way, that fits on the ground.
        List<Candidate> candidates = new ArrayList<>();
        for (int x = box.minX() - SEARCH_MARGIN; x <= box.maxX() + SEARCH_MARGIN; x += CANDIDATE_STEP)
        {
            for (int z = box.minZ() - SEARCH_MARGIN; z <= box.maxZ() + SEARCH_MARGIN; z += CANDIDATE_STEP)
            {
                for (Orientation o : ORIENTATIONS)
                {
                    BoundingBox fp = o.footprint();
                    int fromX = x + fp.minX();
                    int fromZ = z + fp.minZ();
                    int toX = x + fp.maxX();
                    int toZ = z + fp.maxZ();
                    // Clear of every village piece (with room to spare), and how close the nearest one is.
                    double gap = gapToPieces(pieces, fromX, fromZ, toX, toZ);
                    if (gap < 0)
                    {
                        continue;
                    }
                    // The doorstep row is where the path starts, so it must be usable ground...
                    int entranceX = x + o.entrance().getX();
                    int entranceZ = z + o.entrance().getZ();
                    int[] doorstep = doorstepHeights(ground, entranceX, entranceZ, o.facing());
                    if (doorstep == null)
                    {
                        continue;
                    }
                    // ...and the floor goes where the ground under the hut and the doorstep can be levelled to.
                    Floor floor = ground.floor(fromX, fromZ, toX, toZ, doorstep);
                    if (floor == null || floor.y() + DESIGN.height() >= buildLimit)
                    {
                        continue;
                    }
                    double score = distanceToNearest(targetX, targetZ, entranceX, entranceZ)
                            + GAP_WEIGHT * gap
                            + FLATNESS_PENALTY * floor.variation()
                            + EARTHWORK_WEIGHT * floor.work();
                    candidates.add(new Candidate(new BlockPos(x, floor.y(), z), o.facing(), score));
                }
            }
        }

        // Try the best spots until one can be connected to the village.
        candidates.sort(Comparator.comparingDouble(Candidate::score));
        PathFinder finder = new PathFinder(ground, houses, streets, targetColumns, useStreets);
        Set<Long> deadColumns = new HashSet<>();
        Site site = null;
        int tried = 0;
        for (Candidate c : candidates)
        {
            if (tried >= SPOTS_TO_TRY)
            {
                break;
            }
            BlockPos entrance = DESIGN.entrance(c.center(), c.facing());
            if (deadColumns.contains(column(entrance.getX(), entrance.getZ())))
            {
                continue; // a failed search already walked through here, so this one would fail the same way
            }
            tried++;
            List<BlockPos> path = finder.find(c, deadColumns);
            if (path != null)
            {
                site = new Site(c.center(), c.facing(), path, material);
                break;
            }
        }
        LOGGER.info("[Dross] Trader hut spot search: {} candidate spots, {} path searches, {} ms ({}); path {} blocks wide of {}.",
                candidates.size(), tried, (System.nanoTime() - startedAt) / 1_000_000L,
                site != null ? "found one" : "none could be connected",
                PATH_WIDTH, BuiltInRegistries.BLOCK.getKey(material));
        return site;
    }

    /**
     * A hut spot when there is no village: centered on {@code stand}'s column (a safe spot found by the spawner),
     * with the door facing {@code faceToward} if the ground allows it, otherwise turned to another side
     * (clockwise, then counter-clockwise, then away). The "path" is just the block in front of the door.
     * Generates the chunks the hut needs. Returns null if the ground around there won't do: fluid, trees,
     * too uneven, or too tall for the world.
     */
    public static Site fallbackSite(ServerLevel level, BlockPos stand, BlockPos faceToward)
    {
        int reach = Math.max(DESIGN.width(), DESIGN.depth()) / 2 + 1;
        Ground ground = new Ground(level, true,
                stand.getX() - reach, stand.getZ() - reach, stand.getX() + reach, stand.getZ() + reach);

        Direction toward = Direction.getNearest(faceToward.getX() - stand.getX(), 0, faceToward.getZ() - stand.getZ());
        if (!toward.getAxis().isHorizontal())
        {
            toward = Direction.SOUTH;
        }
        for (Direction facing : List.of(toward, toward.getClockWise(), toward.getCounterClockWise(), toward.getOpposite()))
        {
            Orientation o = orientation(facing);
            BoundingBox fp = o.footprint();
            int x = stand.getX();
            int z = stand.getZ();
            // The block in front of the door must be dry and within a step of the floor, so the doorstep
            // doesn't float or sit in a hole.
            int cell = ground.at(x + o.entrance().getX(), z + o.entrance().getZ());
            if (Ground.kind(cell) == Ground.BLOCKED)
            {
                continue;
            }
            Floor floor = ground.floor(x + fp.minX(), z + fp.minZ(), x + fp.maxX(), z + fp.maxZ(),
                    new int[] {Ground.y(cell)});
            if (floor == null || floor.y() + DESIGN.height() >= level.getMaxBuildHeight())
            {
                continue;
            }
            BlockPos center = new BlockPos(x, floor.y(), z);
            return new Site(center, facing, List.of(DESIGN.entrance(center, facing)), Blocks.DIRT_PATH);
        }
        return null;
    }

    private static Orientation orientation(Direction facing)
    {
        for (Orientation o : ORIENTATIONS)
        {
            if (o.facing() == facing)
            {
                return o;
            }
        }
        throw new IllegalArgumentException("Not a horizontal direction: " + facing);
    }

    /**
     * Street pieces have "streets" (the roads) or "terminators" (the dead-end stubs) in their template name,
     * e.g. village/plains/streets/... Everything else in a village (houses, farms, wells, lamp posts, animals...) is
     * treated as a building.
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

    /**
     * How far the footprint is from the nearest village piece, in blocks (rectangle to rectangle, in X and Z), or
     * -1 if the footprint grown by {@link #PIECE_MARGIN} touches any piece (or has one inside it).
     */
    private static double gapToPieces(List<BoundingBox> pieces, int fromX, int fromZ, int toX, int toZ)
    {
        long nearest = Long.MAX_VALUE;
        for (BoundingBox b : pieces)
        {
            int dx = Math.max(0, Math.max(b.minX() - toX, fromX - b.maxX()));
            int dz = Math.max(0, Math.max(b.minZ() - toZ, fromZ - b.maxZ()));
            if (dx <= PIECE_MARGIN && dz <= PIECE_MARGIN)
            {
                return -1;
            }
            nearest = Math.min(nearest, (long) dx * dx + (long) dz * dz);
        }
        return nearest == Long.MAX_VALUE ? 0 : Math.sqrt(nearest);
    }

    /** The distance from (x, z) to the nearest of the given columns. */
    private static double distanceToNearest(int[] targetX, int[] targetZ, int x, int z)
    {
        long best = Long.MAX_VALUE;
        for (int i = 0; i < targetX.length; i++)
        {
            long dx = targetX[i] - x;
            long dz = targetZ[i] - z;
            best = Math.min(best, dx * dx + dz * dz);
        }
        return Math.sqrt(best);
    }

    /** The village's path blocks (columns marked {@link Ground#ROAD}) inside its bounding box. */
    private static List<BlockPos> findRoads(Ground ground, BoundingBox box)
    {
        List<BlockPos> found = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                int cell = ground.at(x, z);
                if (Ground.kind(cell) == Ground.ROAD)
                {
                    found.add(new BlockPos(x, Ground.y(cell), z));
                }
            }
        }
        return found;
    }

    private static List<BlockPos> streetSurface(Ground ground, List<BoundingBox> streets)
    {
        List<BlockPos> found = new ArrayList<>();
        for (BoundingBox street : streets)
        {
            for (int x = street.minX(); x <= street.maxX(); x++)
            {
                for (int z = street.minZ(); z <= street.maxZ(); z++)
                {
                    int cell = ground.at(x, z);
                    if (Ground.kind(cell) != Ground.BLOCKED)
                    {
                        found.add(new BlockPos(x, Ground.y(cell), z));
                    }
                }
            }
        }
        return found;
    }

    /**
     * The doorstep row: the block in front of the door and its neighbors on either side, {@link #PATH_WIDTH} in all.
     * Each must be usable ground (not fluid, a wall, an unloaded chunk or an existing path). Returns the ground
     * height of each, or null if any isn't usable. The floor is then picked within {@link #DOORSTEP_STEP} of all of
     * them, because the whole row is levelled with the hut floor.
     */
    private static int[] doorstepHeights(Ground ground, int entranceX, int entranceZ, Direction facing)
    {
        Direction side = facing.getClockWise();
        int[] heights = new int[PATH_WIDTH];
        for (int k = -PATH_RADIUS; k <= PATH_RADIUS; k++)
        {
            int cell = ground.at(entranceX + k * side.getStepX(), entranceZ + k * side.getStepZ());
            if (Ground.kind(cell) != Ground.FREE)
            {
                return null;
            }
            heights[k + PATH_RADIUS] = Ground.y(cell);
        }
        return heights;
    }

    /**
     * Picks the floor height for a footprint, or null if the ground can't be levelled. {@code counts[i]} is how many
     * footprint columns have their ground at {@code lowest + i}. The floor {@code y} may cut at most {@link #MAX_CUT}
     * blocks off the highest ground and fill at most {@link #MAX_FILL} blocks on the lowest, and must be within
     * {@link #DOORSTEP_STEP} of every height in {@code doorstep}. Of those, the one needing the fewest blocks cut
     * plus filled wins; on a tie, the higher one (it cuts less).
     */
    static Floor chooseFloor(int lowest, int[] counts, int[] doorstep)
    {
        int variation = counts.length - 1;
        if (variation > MAX_CUT + MAX_FILL)
        {
            return null;
        }
        int highest = lowest + variation;
        int bottom = highest - MAX_CUT;
        int top = lowest + MAX_FILL;
        for (int door : doorstep)
        {
            bottom = Math.max(bottom, door - DOORSTEP_STEP);
            top = Math.min(top, door + DOORSTEP_STEP);
        }
        Floor best = null;
        for (int y = top; y >= bottom; y--)
        {
            int work = 0;
            for (int i = 0; i < counts.length; i++)
            {
                work += counts[i] * Math.abs(lowest + i - y);
            }
            if (best == null || work < best.work())
            {
                best = new Floor(y, variation, work);
            }
        }
        return best;
    }

    // ---------------------------------------------------------------- the path material

    /**
     * What the village's streets are made of: the commonest top block on the street pieces' columns (not counting
     * columns inside a building's box) that isn't
     * plain terrain (dirt, grass, sand, gravel, snow, stone...) and is either a dirt path or a full block. Plains,
     * savanna, taiga and snowy villages come out as {@code dirt_path} and desert villages as
     * {@code smooth_sandstone} (their street templates use it). If there are fewer than
     * {@link #MIN_MATERIAL_SAMPLES} such columns, it's {@code dirt_path}.
     */
    private static Block detectPathMaterial(Ground ground, List<BoundingBox> streets, List<BoundingBox> houses)
    {
        Map<Block, Integer> counts = new HashMap<>();
        Set<Long> seen = new HashSet<>();
        for (BoundingBox street : streets)
        {
            for (int x = street.minX(); x <= street.maxX(); x++)
            {
                for (int z = street.minZ(); z <= street.maxZ(); z++)
                {
                    Block top = ground.top(x, z);
                    if (top != null && isPathMaterialCandidate(top) && !insideAny(houses, x, z) && seen.add(column(x, z)))
                    {
                        counts.merge(top, 1, Integer::sum);
                    }
                }
            }
        }
        return chooseMaterial(counts);
    }

    /** The most common block of the counts, or {@code dirt_path} if there's too little to go on. */
    static Block chooseMaterial(Map<Block, Integer> counts)
    {
        Block best = null;
        int bestCount = 0;
        for (Map.Entry<Block, Integer> entry : counts.entrySet())
        {
            if (entry.getValue() > bestCount)
            {
                best = entry.getKey();
                bestCount = entry.getValue();
            }
        }
        return best != null && bestCount >= MIN_MATERIAL_SAMPLES ? best : Blocks.DIRT_PATH;
    }

    /** A block a village's street could be made of: a dirt path, or a full block that isn't plain terrain. */
    private static boolean isPathMaterialCandidate(Block block)
    {
        if (block == Blocks.DIRT_PATH)
        {
            return true;
        }
        BlockState state = block.defaultBlockState();
        return !state.isAir()
                && !state.is(BlockTags.DIRT) && !state.is(BlockTags.SAND) && !state.is(BlockTags.ICE)
                && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS)
                && !state.is(Blocks.GRAVEL) && !state.is(Blocks.SNOW_BLOCK) && !state.is(Blocks.CLAY)
                && !state.is(BlockTags.BASE_STONE_OVERWORLD) && !state.is(BlockTags.PLANKS)
                && state.isCollisionShapeFullBlock(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
    }

    // ---------------------------------------------------------------- the ground

    /**
     * The ground of a rectangle of columns, each read once. A column's "ground" is its top block that stops
     * movement or holds fluid, leaves not counted (the same as the MOTION_BLOCKING_NO_LEAVES heightmap).
     * Columns outside the rectangle are read when asked for, and remembered.
     * <p>
     * Every column has a kind:
     * <ul>
     *   <li>{@link #FREE}: ordinary dry ground, fine to build on and to walk over;</li>
     *   <li>{@link #ROAD}: a village path block (a dirt path, or the village's street material once
     *       {@link #markRoads} has run). Fine to walk to, but never built on or paved over;</li>
     *   <li>{@link #BLOCKED}: a chunk that isn't generated yet, fluid, ice, a log, cactus or bamboo.
     *       (The last two count as ground in the heightmap and would raise the floor.) Never built on
     *       and never walked over.</li>
     * </ul>
     */
    private static final class Ground
    {
        static final int FREE = 0;
        static final int ROAD = 1;
        static final int BLOCKED = 2;

        private final ServerLevel level;
        /** True: generate chunks that aren't there yet (the fallback). False: leave them, they're walls. */
        private final boolean loadChunks;
        private final int minX;
        private final int minZ;
        private final int sizeX;
        private final int sizeZ;
        /** One packed cell per column (see {@link #pack}), x-major. */
        private final int[] cells;
        /** The top block of each column of the rectangle (null where it couldn't be read), same layout as the cells. */
        private final Block[] tops;
        private final Map<Long, Integer> outside = new HashMap<>();
        private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        /** The chunk the last column was read from, so a run of columns in one chunk costs one lookup. */
        private LevelChunk chunk;
        private int chunkX = Integer.MAX_VALUE;
        private int chunkZ = Integer.MAX_VALUE;
        /** The block the last {@link #read} saw on top. */
        private Block lastTop;

        Ground(ServerLevel level, boolean loadChunks, int minX, int minZ, int maxX, int maxZ)
        {
            this.level = level;
            this.loadChunks = loadChunks;
            this.minX = minX;
            this.minZ = minZ;
            this.sizeX = maxX - minX + 1;
            this.sizeZ = maxZ - minZ + 1;
            this.cells = new int[sizeX * sizeZ];
            this.tops = new Block[sizeX * sizeZ];
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    int i = (x - minX) * sizeZ + (z - minZ);
                    cells[i] = read(x, z);
                    tops[i] = lastTop;
                }
            }
        }

        /** The packed cell for this column: use {@link #y} and {@link #kind} on it. */
        int at(int x, int z)
        {
            int ix = x - minX;
            int iz = z - minZ;
            if (ix >= 0 && iz >= 0 && ix < sizeX && iz < sizeZ)
            {
                return cells[ix * sizeZ + iz];
            }
            return outside.computeIfAbsent(column(x, z), key -> read(x, z));
        }

        /** The top block of this column, or null if it wasn't read (outside the rectangle, or not loaded). */
        Block top(int x, int z)
        {
            int ix = x - minX;
            int iz = z - minZ;
            if (ix >= 0 && iz >= 0 && ix < sizeX && iz < sizeZ)
            {
                return tops[ix * sizeZ + iz];
            }
            return null;
        }

        /**
         * Marks the village's own path blocks: every free column inside a street piece (and outside every building)
         * whose top block is the village's path {@code material}. (Dirt paths are marked as they are read, anywhere.)
         */
        void markRoads(Block material, List<BoundingBox> streets, List<BoundingBox> houses)
        {
            if (material == Blocks.DIRT_PATH)
            {
                return;
            }
            for (BoundingBox street : streets)
            {
                for (int x = Math.max(street.minX(), minX); x <= Math.min(street.maxX(), minX + sizeX - 1); x++)
                {
                    for (int z = Math.max(street.minZ(), minZ); z <= Math.min(street.maxZ(), minZ + sizeZ - 1); z++)
                    {
                        int i = (x - minX) * sizeZ + (z - minZ);
                        if (tops[i] == material && kind(cells[i]) == FREE && !insideAny(houses, x, z))
                        {
                            cells[i] = pack(y(cells[i]), ROAD);
                        }
                    }
                }
            }
        }

        /**
         * The floor for a hut over this rectangle (see {@link #chooseFloor}): bumps are cut down by up to
         * {@link #MAX_CUT} blocks and dips filled in by up to {@link #MAX_FILL}. Null if any column is not
         * {@link #FREE}, the ground varies by more than {@link #MAX_CUT} + {@link #MAX_FILL}, or no floor is within
         * {@link #DOORSTEP_STEP} of the {@code doorstep} ground heights.
         */
        Floor floor(int fromX, int fromZ, int toX, int toZ, int[] doorstep)
        {
            int lowest = Integer.MAX_VALUE;
            int highest = Integer.MIN_VALUE;
            for (int x = fromX; x <= toX; x++)
            {
                for (int z = fromZ; z <= toZ; z++)
                {
                    int cell = at(x, z);
                    if (kind(cell) != FREE)
                    {
                        return null;
                    }
                    lowest = Math.min(lowest, y(cell));
                    highest = Math.max(highest, y(cell));
                    if (highest - lowest > MAX_CUT + MAX_FILL)
                    {
                        return null;
                    }
                }
            }
            int[] counts = new int[highest - lowest + 1];
            for (int x = fromX; x <= toX; x++)
            {
                for (int z = fromZ; z <= toZ; z++)
                {
                    counts[y(at(x, z)) - lowest]++;
                }
            }
            return chooseFloor(lowest, counts, doorstep);
        }

        /** The Y of the ground block (meaningless for a {@link #BLOCKED} column). */
        static int y(int cell)
        {
            return cell >> 2;
        }

        static int kind(int cell)
        {
            return cell & 3;
        }

        private static int pack(int y, int kind)
        {
            return (y << 2) | kind;
        }

        private int read(int x, int z)
        {
            lastTop = null;
            LevelChunk c = chunkAt(x, z);
            if (c == null)
            {
                return pack(0, BLOCKED);
            }
            int y = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
            BlockState state = c.getBlockState(scratch.set(x, y, z));
            lastTop = state.getBlock();
            if (state.isAir() || !state.getFluidState().isEmpty() || state.is(BlockTags.LOGS) || state.is(BlockTags.ICE)
                    || state.is(Blocks.CACTUS) || state.is(Blocks.BAMBOO))
            {
                return pack(y, BLOCKED);
            }
            return pack(y, state.is(Blocks.DIRT_PATH) ? ROAD : FREE);
        }

        private LevelChunk chunkAt(int x, int z)
        {
            int cx = x >> 4;
            int cz = z >> 4;
            if (cx != chunkX || cz != chunkZ)
            {
                chunk = loadChunks ? level.getChunk(cx, cz) : level.getChunkSource().getChunkNow(cx, cz);
                chunkX = cx;
                chunkZ = cz;
            }
            return chunk;
        }
    }

    // ---------------------------------------------------------------- the path

    /**
     * Finds the wide path for one hut spot. The search runs over the path's <i>middle line</i>, one ground column
     * per step, from the block in front of the door to the nearest village path block (it stops next to it; the
     * village block itself is not paved). A middle-line column is usable only if the whole {@link #PATH_WIDTH}
     * x {@link #PATH_WIDTH} block around it is: every column in it (except existing village path blocks and other
     * village-made things on a street piece, which are simply left alone) must be dry, loaded, outside the hut and
     * outside every building, and neighboring columns may differ by at most one block in height. Each step also changes height by at most one. Turns cost extra,
     * so the middle line runs straight where it can.
     * <p>
     * The path itself is the strip across the direction of travel at every middle-line column, with the full block
     * at turns so corners are filled in.
     */
    private static final class PathFinder
    {
        private static final int R = PATH_SEARCH_RADIUS;
        private static final int WINDOW = 2 * R + 1;
        private static final Direction[] DIRS = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
        private static final int BLOCK = 2 * PATH_RADIUS + 1;
        private static final int NO_HEIGHT = Integer.MIN_VALUE;

        private final Ground ground;
        private final List<BoundingBox> houses;
        private final List<BoundingBox> streets;
        private final Set<Long> roadColumns;
        private final boolean useStreets;

        // The state of the search in progress (one candidate at a time).
        private BoundingBox footprint;
        private int startX;
        private int startZ;
        private int floorY;
        private Direction facing;
        private byte[] usable;

        PathFinder(Ground ground, List<BoundingBox> houses, List<BoundingBox> streets, Set<Long> roadColumns,
                   boolean useStreets)
        {
            this.ground = ground;
            this.houses = houses;
            this.streets = streets;
            this.roadColumns = roadColumns;
            this.useStreets = useStreets;
        }

        /**
         * The ground blocks to pave for this hut spot, doorstep row first, or null if there's no way through.
         * When it fails, every column it visited goes into {@code deadColumns}, so spots whose door opens onto
         * one of those are skipped.
         */
        List<BlockPos> find(Candidate c, Set<Long> deadColumns)
        {
            footprint = DESIGN.footprint(c.center(), c.facing());
            BlockPos entrance = DESIGN.entrance(c.center(), c.facing());
            startX = entrance.getX();
            startZ = entrance.getZ();
            floorY = c.center().getY();
            facing = c.facing();
            usable = new byte[WINDOW * WINDOW];

            int[] cost = new int[WINDOW * WINDOW * 4];
            int[] previous = new int[WINDOW * WINDOW * 4];
            Arrays.fill(cost, Integer.MAX_VALUE);
            Arrays.fill(previous, -1);
            PriorityQueue<Long> open = new PriorityQueue<>();

            int startCell = cellIndex(startX, startZ);
            int startState = startCell * 4 + directionIndex(facing);
            cost[startState] = 0;
            open.add(entry(0, startState));
            int goal = -1;

            while (!open.isEmpty())
            {
                long top = open.poll();
                int spent = (int) (top >>> 32);
                int state = (int) top;
                if (spent > cost[state])
                {
                    continue;
                }
                int cell = state >> 2;
                int dir = state & 3;
                int x = startX - R + cell / WINDOW;
                int z = startZ - R + cell % WINDOW;
                if (cell != startCell && isTarget(x, z))
                {
                    goal = state;
                    break;
                }
                int y = heightAt(x, z);
                for (int nd = 0; nd < 4; nd++)
                {
                    int nx = x + DIRS[nd].getStepX();
                    int nz = z + DIRS[nd].getStepZ();
                    if (Math.abs(nx - startX) > R || Math.abs(nz - startZ) > R)
                    {
                        continue;
                    }
                    int ncell = cellIndex(nx, nz);
                    int next = ncell * 4 + nd;
                    int nextCost = spent + 1 + (nd == dir ? 0 : TURN_COST);
                    if (nextCost >= cost[next])
                    {
                        continue;
                    }
                    if (isTarget(nx, nz))
                    {
                        // Reaching a village path block ends the search; it only has to be dry ground at a gentle step.
                        int targetCell = ground.at(nx, nz);
                        if (Ground.kind(targetCell) == Ground.BLOCKED || Math.abs(Ground.y(targetCell) - y) > 1)
                        {
                            continue;
                        }
                    }
                    else if (!stripUsable(nx, nz, ncell) || Math.abs(heightAt(nx, nz) - y) > 1)
                    {
                        continue;
                    }
                    cost[next] = nextCost;
                    previous[next] = state;
                    open.add(entry(nextCost, next));
                }
            }

            if (goal < 0)
            {
                for (int i = 0; i < cost.length; i++)
                {
                    if (cost[i] != Integer.MAX_VALUE)
                    {
                        int cell = i >> 2;
                        deadColumns.add(column(startX - R + cell / WINDOW, startZ - R + cell % WINDOW));
                    }
                }
                return null;
            }

            // The middle line, from the doorstep to the last column before the village path.
            List<int[]> line = new ArrayList<>();
            for (int s = previous[goal]; s >= 0; s = previous[s])
            {
                int cell = s >> 2;
                line.add(0, new int[] {startX - R + cell / WINDOW, startZ - R + cell % WINDOW});
            }
            return pave(line);
        }

        /** Turns the middle line into the blocks to pave: the doorstep row, then the strip at every step. */
        private List<BlockPos> pave(List<int[]> line)
        {
            Map<Long, BlockPos> out = new LinkedHashMap<>();
            Direction side = facing.getClockWise();
            // The doorstep row: the block in front of the door first, then the ones beside it.
            put(out, startX, startZ);
            for (int k = 1; k <= PATH_RADIUS; k++)
            {
                put(out, startX + k * side.getStepX(), startZ + k * side.getStepZ());
                put(out, startX - k * side.getStepX(), startZ - k * side.getStepZ());
            }
            for (int i = 1; i < line.size(); i++)
            {
                int[] at = line.get(i);
                int dx = at[0] - line.get(i - 1)[0];
                int dz = at[1] - line.get(i - 1)[1];
                boolean turns = i + 1 < line.size()
                        && (line.get(i + 1)[0] - at[0] != dx || line.get(i + 1)[1] - at[1] != dz);
                // Across the direction of travel (the perpendicular of (dx, dz) is (dz, dx) up to sign).
                for (int k = -PATH_RADIUS; k <= PATH_RADIUS; k++)
                {
                    if (turns)
                    {
                        for (int j = -PATH_RADIUS; j <= PATH_RADIUS; j++)
                        {
                            put(out, at[0] + k, at[1] + j);
                        }
                    }
                    else
                    {
                        put(out, at[0] + k * Math.abs(dz), at[1] + k * Math.abs(dx));
                    }
                }
            }
            return new ArrayList<>(out.values());
        }

        /** Adds a column to the path unless it's an existing village path block (left alone) or already in. */
        private void put(Map<Long, BlockPos> out, int x, int z)
        {
            if (isExempt(x, z) && !isDoorstepRow(x, z))
            {
                return;
            }
            out.putIfAbsent(column(x, z), new BlockPos(x, heightAt(x, z), z));
        }

        /** True if the column is on the doorstep row: the one in front of the door and its neighbors either side. */
        private boolean isDoorstepRow(int x, int z)
        {
            Direction side = facing.getClockWise();
            for (int k = -PATH_RADIUS; k <= PATH_RADIUS; k++)
            {
                if (x == startX + k * side.getStepX() && z == startZ + k * side.getStepZ())
                {
                    return true;
                }
            }
            return false;
        }

        /** The height the path has at this column: the doorstep row is levelled with the floor, the rest follows the ground. */
        private int heightAt(int x, int z)
        {
            return isDoorstepRow(x, z) ? floorY : Ground.y(ground.at(x, z));
        }

        /** True for a column where the new path ends: a village path block, or (if none were found) any street column. */
        private boolean isTarget(int x, int z)
        {
            return useStreets ? insideAny(streets, x, z) : roadColumns.contains(column(x, z));
        }

        /**
         * A column the path must leave alone (and that needn't be natural ground): a village path block, or
         * anything village-made on a street piece, such as decoration, farmland or terracotta. (Plain natural ground
         * on a street piece's bounding box, like grass beside the road, can be paved. If no village path blocks
         * could be told apart, every dry street column counts.)
         */
        private boolean isExempt(int x, int z)
        {
            int kind = Ground.kind(ground.at(x, z));
            if (kind == Ground.ROAD)
            {
                return true;
            }
            if (kind == Ground.BLOCKED || !insideAny(streets, x, z))
            {
                return false;
            }
            Block top = ground.top(x, z);
            return useStreets || (top != null && !isPaveable(top.defaultBlockState()));
        }

        /** Whether the whole block around this middle-line column can be part of the path (cached per search). */
        private boolean stripUsable(int x, int z, int cell)
        {
            byte known = usable[cell];
            if (known == 0)
            {
                known = (byte) (computeUsable(x, z) ? 1 : 2);
                usable[cell] = known;
            }
            return known == 1;
        }

        private boolean computeUsable(int cx, int cz)
        {
            int[] heights = new int[BLOCK * BLOCK];
            for (int i = 0; i < BLOCK; i++)
            {
                for (int j = 0; j < BLOCK; j++)
                {
                    int x = cx + i - PATH_RADIUS;
                    int z = cz + j - PATH_RADIUS;
                    if (isExempt(x, z))
                    {
                        heights[i * BLOCK + j] = NO_HEIGHT;
                        continue;
                    }
                    if ((x >= footprint.minX() && x <= footprint.maxX() && z >= footprint.minZ() && z <= footprint.maxZ())
                            || insideAny(houses, x, z)
                            || Ground.kind(ground.at(x, z)) == Ground.BLOCKED)
                    {
                        return false;
                    }
                    heights[i * BLOCK + j] = heightAt(x, z);
                }
            }
            // Neighboring columns of the block may differ by at most one, so every column can be walked onto.
            for (int i = 0; i < BLOCK; i++)
            {
                for (int j = 0; j < BLOCK; j++)
                {
                    int h = heights[i * BLOCK + j];
                    if (h == NO_HEIGHT)
                    {
                        continue;
                    }
                    if (i + 1 < BLOCK && heights[(i + 1) * BLOCK + j] != NO_HEIGHT
                            && Math.abs(heights[(i + 1) * BLOCK + j] - h) > 1)
                    {
                        return false;
                    }
                    if (j + 1 < BLOCK && heights[i * BLOCK + j + 1] != NO_HEIGHT
                            && Math.abs(heights[i * BLOCK + j + 1] - h) > 1)
                    {
                        return false;
                    }
                }
            }
            return true;
        }

        private int cellIndex(int x, int z)
        {
            return (x - (startX - R)) * WINDOW + (z - (startZ - R));
        }

        private static int directionIndex(Direction d)
        {
            for (int i = 0; i < DIRS.length; i++)
            {
                if (DIRS[i] == d)
                {
                    return i;
                }
            }
            throw new IllegalArgumentException("Not a horizontal direction: " + d);
        }

        private static long entry(int cost, int state)
        {
            return ((long) cost << 32) | (state & 0xFFFFFFFFL);
        }
    }

    private static boolean insideAny(List<BoundingBox> boxes, int x, int z)
    {
        for (BoundingBox b : boxes)
        {
            if (x >= b.minX() && x <= b.maxX() && z >= b.minZ() && z <= b.maxZ())
            {
                return true;
            }
        }
        return false;
    }

    private static long column(int x, int z)
    {
        return ((long) x << 32) | (z & 0xFFFFFFFFL);
    }

    /**
     * Paves the path with the site's path block and clears plants on top of it. Only natural ground (dirt, grass,
     * sand, gravel, snow, stone) is replaced, so village-made blocks are never overwritten; the doorstep row
     * in front of the hut is always paved and gets two blocks of headroom.
     */
    public static void layPath(ServerLevel level, Site site)
    {
        BlockPos entrance = site.entrance();
        Direction along = site.facing();
        for (BlockPos ground : site.path())
        {
            fillDown(level, ground.below(), Blocks.DIRT, 4);
            BlockState state = level.getBlockState(ground);
            // The doorstep row: the same distance from the hut as the entrance and at most half a path width beside it.
            int lateral = Math.abs((ground.getX() - entrance.getX()) * along.getStepZ()
                    + (ground.getZ() - entrance.getZ()) * along.getStepX());
            int forward = (ground.getX() - entrance.getX()) * along.getStepX()
                    + (ground.getZ() - entrance.getZ()) * along.getStepZ();
            boolean doorstep = forward == 0 && lateral <= PATH_RADIUS;
            if (doorstep || isPaveable(state))
            {
                level.setBlock(ground, site.pathBlock().defaultBlockState(), Block.UPDATE_ALL);
            }
            // Dirt paths turn back into dirt with a solid block on top; plants and snow just get cleared.
            for (int up = 1; up <= (doorstep ? 2 : 1); up++)
            {
                BlockPos above = ground.above(up);
                BlockState aboveState = level.getBlockState(above);
                if (!aboveState.isAir() && (doorstep || aboveState.canBeReplaced()))
                {
                    level.setBlock(above, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    private static boolean isPaveable(BlockState state)
    {
        return state.is(BlockTags.DIRT) || state.is(BlockTags.SAND) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SNOW_BLOCK) || state.is(BlockTags.BASE_STONE_OVERWORLD);
    }

    // ---------------------------------------------------------------- building

    /**
     * Builds the hut at the site: the design clears its footprint, fills dips under it and draws itself.
     * The path is laid separately, with {@link #layPath}.
     */
    public static void build(ServerLevel level, Site site)
    {
        DESIGN.build(level, site.center(), site.facing());
    }

    /** Places {@code block} from {@code top} downwards until it reaches solid ground (at most {@code maxDepth} blocks). */
    private static void fillDown(ServerLevel level, BlockPos top, Block block, int maxDepth)
    {
        BlockPos pos = top;
        for (int i = 0; i < maxDepth; i++)
        {
            BlockState state = level.getBlockState(pos);
            if (state.blocksMotion() && level.getFluidState(pos).isEmpty())
            {
                return;
            }
            level.setBlock(pos, block.defaultBlockState(), Block.UPDATE_ALL);
            pos = pos.below();
        }
    }
}
