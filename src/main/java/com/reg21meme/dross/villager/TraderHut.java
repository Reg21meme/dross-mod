package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.villager.hut.HutDesign;
import com.reg21meme.dross.villager.hut.HutDesigns;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
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

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Dross trader's hut: picking an open spot at the edge of a village, building the hut, and laying a dirt
 * path from its door to the village's own paths. What gets built is {@link #DESIGN}, today the Rift Chapel
 * (see {@code villager.hut}): 9 x 13 blocks on the ground and 18 high, with the door in one short side.
 * <p>
 * How a spot is chosen: every couple of blocks around the village, outside its bounding box, the hut is
 * tried facing all four ways. A try is allowed if the ground under the whole footprint is dry and level enough
 * (the build fills dips with dirt but never digs, so the floor sits at the highest ground). It is scored by
 * how far its door is from a village path, plus a penalty for uneven ground. The best few get a path search,
 * and the first one that connects wins.
 */
public final class TraderHut
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The hut design the trader lives in. This is the one line to change if another design is ever used. */
    static final HutDesign DESIGN = HutDesigns.RIFT_CHAPEL;

    /** How far outside the village's bounding box we look for a spot (for the hut's center, in blocks). */
    private static final int SEARCH_MARGIN = 24;
    /** The ground under the hut may vary by at most this many blocks (the floor ends up on a plinth this tall). */
    private static final int MAX_GROUND_VARIATION = 3;
    /** A spot's score is its door's distance to a village path in blocks, plus this much per block of uneven ground. */
    private static final int FLATNESS_PENALTY = 4;
    /** Hut centers are tried every this many blocks. */
    private static final int CANDIDATE_STEP = 2;
    /** How many of the best-scoring spots we try to connect with a path. */
    private static final int SPOTS_TO_TRY = 12;
    /** The path search never goes further than this from the door (in blocks). */
    private static final int PATH_SEARCH_RADIUS = 64;

    /**
     * A chosen hut spot.
     * @param center the design's center: the ground block in the middle of the footprint (the floor replaces it)
     * @param facing the way the door faces, out of the hut
     * @param path   the ground blocks to turn into a dirt path, starting at the block in front of the door
     */
    public record Site(BlockPos center, Direction facing, List<BlockPos> path)
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

    /** The floor height for a footprint (its highest ground) and how uneven the ground under it is. */
    private record Floor(int y, int variation) {}

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
     * ({@link #SEARCH_MARGIN}), plus half the hut's size, plus one for the block in front of its door.
     * {@link TraderSpawner} generates at least this much around the village before it searches.
     */
    public static int searchReach()
    {
        return SEARCH_MARGIN + Math.max(DESIGN.width(), DESIGN.depth()) / 2 + 1;
    }

    // ---------------------------------------------------------------- choosing a spot

    /**
     * Finds an open spot just outside the village's bounding box (so it can't overlap houses or paths),
     * as close as possible to one of the village's paths, and a ground-following path to it.
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
     * The search itself, on ground that has already been read: every candidate hut center around the village
     * box, scored, then the best few tried with a path search.
     *
     * @param box        the village's bounding box
     * @param houses     the bounding boxes of everything in the village that isn't a street (paths never cross them)
     * @param streets    the bounding boxes of the village's street pieces
     * @param buildLimit the world's build limit: the hut must end below it
     */
    private static Site search(Ground ground, BoundingBox box, List<BoundingBox> houses, List<BoundingBox> streets,
                               int buildLimit)
    {
        long startedAt = System.nanoTime();

        // What the new path should connect to: the village's dirt path blocks. Desert villages
        // may not use dirt paths, so fall back to "anywhere on a street piece".
        List<BlockPos> targets = findDirtPaths(ground, box);
        boolean useStreets = targets.isEmpty();
        if (useStreets)
        {
            targets = streetSurface(ground, streets);
        }
        if (targets.isEmpty())
        {
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

        // Every hut center, facing every way, that fits on the ground.
        List<Candidate> candidates = new ArrayList<>();
        for (int x = box.minX() - SEARCH_MARGIN; x <= box.maxX() + SEARCH_MARGIN; x += CANDIDATE_STEP)
        {
            for (int z = box.minZ() - SEARCH_MARGIN; z <= box.maxZ() + SEARCH_MARGIN; z += CANDIDATE_STEP)
            {
                for (Orientation o : ORIENTATIONS)
                {
                    BoundingBox fp = o.footprint();
                    // Fully outside the village's box, with a block of space all round: "at the edge", and
                    // never on top of a house or path.
                    if (box.intersects(x + fp.minX() - 1, z + fp.minZ() - 1, x + fp.maxX() + 1, z + fp.maxZ() + 1))
                    {
                        continue;
                    }
                    Floor floor = ground.floor(x + fp.minX(), z + fp.minZ(), x + fp.maxX(), z + fp.maxZ());
                    if (floor == null || floor.y() + DESIGN.height() >= buildLimit)
                    {
                        continue;
                    }
                    // The block in front of the door is where the path starts, so it must be usable.
                    int entranceX = x + o.entrance().getX();
                    int entranceZ = z + o.entrance().getZ();
                    if (Ground.kind(ground.at(entranceX, entranceZ)) == Ground.BLOCKED)
                    {
                        continue;
                    }
                    double score = distanceToNearest(targetX, targetZ, entranceX, entranceZ)
                            + FLATNESS_PENALTY * floor.variation();
                    candidates.add(new Candidate(new BlockPos(x, floor.y(), z), o.facing(), score));
                }
            }
        }

        // Try the best spots until one can be connected to the village.
        candidates.sort(Comparator.comparingDouble(Candidate::score));
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
            List<BlockPos> path = findPath(ground, c, houses, streets, targetColumns, useStreets, deadColumns);
            if (path != null)
            {
                site = new Site(c.center(), c.facing(), path);
                break;
            }
        }
        LOGGER.info("[Dross] Trader hut spot search: {} candidate spots, {} path searches, {} ms ({}).",
                candidates.size(), tried, (System.nanoTime() - startedAt) / 1_000_000L,
                site != null ? "found one" : "none could be connected");
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
            Floor floor = ground.floor(x + fp.minX(), z + fp.minZ(), x + fp.maxX(), z + fp.maxZ());
            if (floor == null || floor.y() + DESIGN.height() >= level.getMaxBuildHeight())
            {
                continue;
            }
            // The block in front of the door must be dry and about level with the floor, so the doorstep
            // doesn't float or sit in a hole.
            int cell = ground.at(x + o.entrance().getX(), z + o.entrance().getZ());
            if (Ground.kind(cell) == Ground.BLOCKED || Math.abs(Ground.y(cell) - floor.y()) > 1)
            {
                continue;
            }
            BlockPos center = new BlockPos(x, floor.y(), z);
            return new Site(center, facing, List.of(DESIGN.entrance(center, facing)));
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

    /** Street pieces have "streets" in their template name, e.g. village/plains/streets/... */
    private static boolean isStreet(StructurePiece piece)
    {
        return piece instanceof PoolElementStructurePiece pool && pool.getElement().toString().contains("streets");
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

    private static List<BlockPos> findDirtPaths(Ground ground, BoundingBox box)
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

    // ---------------------------------------------------------------- the ground

    /**
     * The ground of a rectangle of columns, each read once. A column's "ground" is its top block that stops
     * movement or holds fluid, leaves not counted (the same as the MOTION_BLOCKING_NO_LEAVES heightmap).
     * Columns outside the rectangle are read when asked for, and remembered.
     * <p>
     * Every column has a kind:
     * <ul>
     *   <li>{@link #FREE}: ordinary dry ground, fine to build on and to walk over;</li>
     *   <li>{@link #ROAD}: a dirt path, a village road. Fine to walk to, but never built on;</li>
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
        private final Map<Long, Integer> outside = new HashMap<>();
        private final BlockPos.MutableBlockPos scratch = new BlockPos.MutableBlockPos();
        /** The chunk the last column was read from, so a run of columns in one chunk costs one lookup. */
        private LevelChunk chunk;
        private int chunkX = Integer.MAX_VALUE;
        private int chunkZ = Integer.MAX_VALUE;

        Ground(ServerLevel level, boolean loadChunks, int minX, int minZ, int maxX, int maxZ)
        {
            this.level = level;
            this.loadChunks = loadChunks;
            this.minX = minX;
            this.minZ = minZ;
            this.sizeX = maxX - minX + 1;
            this.sizeZ = maxZ - minZ + 1;
            this.cells = new int[sizeX * sizeZ];
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    cells[(x - minX) * sizeZ + (z - minZ)] = read(x, z);
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

        /**
         * The floor for a hut over this rectangle: the highest ground in it, and how much the ground varies.
         * Null if any column is not {@link #FREE} or the ground varies by more than {@link #MAX_GROUND_VARIATION}.
         */
        Floor floor(int fromX, int fromZ, int toX, int toZ)
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
                    if (highest - lowest > MAX_GROUND_VARIATION)
                    {
                        return null;
                    }
                }
            }
            return new Floor(highest, highest - lowest);
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
            LevelChunk c = chunkAt(x, z);
            if (c == null)
            {
                return pack(0, BLOCKED);
            }
            int y = c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15);
            BlockState state = c.getBlockState(scratch.set(x, y, z));
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
     * Breadth-first search over ground columns from the block in front of the door to the nearest village path.
     * Each step may go up or down at most one block, never through a wall (fluid, an unloaded chunk...),
     * and never through a house or the hut itself. Returns the ground blocks to pave (not including the village
     * path block it reaches), or null if there's no way through. When it fails, every column it visited goes into
     * {@code deadColumns}, so spots whose door opens onto one of those are skipped.
     */
    private static List<BlockPos> findPath(Ground ground, Candidate c, List<BoundingBox> houses,
                                           List<BoundingBox> streets, Set<Long> targetColumns, boolean useStreets,
                                           Set<Long> deadColumns)
    {
        BoundingBox footprint = DESIGN.footprint(c.center(), c.facing());
        BlockPos entrance = DESIGN.entrance(c.center(), c.facing());
        int startX = entrance.getX();
        int startZ = entrance.getZ();
        long start = column(startX, startZ);

        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Integer> heights = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        cameFrom.put(start, start);
        heights.put(start, c.center().getY()); // the doorstep is levelled with the hut floor
        queue.add(start);

        while (!queue.isEmpty())
        {
            long current = queue.poll();
            int x = columnX(current);
            int z = columnZ(current);
            if (current != start && isTarget(x, z, targetColumns, streets, useStreets))
            {
                List<BlockPos> path = new ArrayList<>();
                long step = cameFrom.get(current);
                while (true)
                {
                    path.add(0, new BlockPos(columnX(step), heights.get(step), columnZ(step)));
                    if (step == start)
                    {
                        return path;
                    }
                    step = cameFrom.get(step);
                }
            }
            int y = heights.get(current);
            for (Direction dir : Direction.Plane.HORIZONTAL)
            {
                int nx = x + dir.getStepX();
                int nz = z + dir.getStepZ();
                long next = column(nx, nz);
                if (cameFrom.containsKey(next)
                        || Math.abs(nx - startX) > PATH_SEARCH_RADIUS || Math.abs(nz - startZ) > PATH_SEARCH_RADIUS
                        || (nx >= footprint.minX() && nx <= footprint.maxX() && nz >= footprint.minZ() && nz <= footprint.maxZ())
                        || insideAny(houses, nx, nz))
                {
                    continue;
                }
                int cell = ground.at(nx, nz);
                if (Ground.kind(cell) == Ground.BLOCKED || Math.abs(Ground.y(cell) - y) > 1)
                {
                    continue;
                }
                cameFrom.put(next, current);
                heights.put(next, Ground.y(cell));
                queue.add(next);
            }
        }
        deadColumns.addAll(cameFrom.keySet());
        return null;
    }

    private static boolean isTarget(int x, int z, Set<Long> targetColumns, List<BoundingBox> streets, boolean useStreets)
    {
        return useStreets ? insideAny(streets, x, z) : targetColumns.contains(column(x, z));
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

    private static int columnX(long column)
    {
        return (int) (column >> 32);
    }

    private static int columnZ(long column)
    {
        return (int) column;
    }

    /** Turns each ground block of the path into a dirt path and clears plants on top of it. */
    public static void layPath(ServerLevel level, Site site)
    {
        for (BlockPos ground : site.path())
        {
            fillDown(level, ground.below(), Blocks.DIRT, 4);
            BlockState state = level.getBlockState(ground);
            boolean doorstep = ground.equals(site.path().get(0));
            if (doorstep || isPaveable(state))
            {
                level.setBlock(ground, Blocks.DIRT_PATH.defaultBlockState(), Block.UPDATE_ALL);
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
