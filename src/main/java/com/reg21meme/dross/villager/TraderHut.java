package com.reg21meme.dross.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.PoolElementStructurePiece;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The Dross trader's hut: picking an open spot at the edge of a village, building the hut,
 * and laying a dirt path from its door to the village's own paths.
 * <p>
 * The hut is 5x5x5 on the outside (3x3x3 inside): the floor replaces the top ground block,
 * walls are 3 high, then a flat roof.
 */
public final class TraderHut
{
    /** Walls, floor and roof. Temporary: see "Parked for later" in CLAUDE.md. */
    private static final Block WALL = Blocks.NETHERITE_BLOCK;
    /** The 3x3 inside floor. (Not carpet: carpet in the doorway stopped him from walking out.) */
    private static final Block FLOOR = Blocks.GOLD_BLOCK;
    private static final Block DOOR = Blocks.OAK_DOOR;
    private static final Block WINDOW = Blocks.GLASS_PANE;

    /** The hut covers its center +-2 blocks (5 wide). */
    static final int HALF = 2;
    /** How far outside the village's bounding box we look for a spot. */
    private static final int SEARCH_MARGIN = 24;
    /** The ground under the hut may vary by at most this many blocks. */
    private static final int MAX_GROUND_VARIATION = 2;
    /** How many of the best-scoring spots we try to connect with a path. */
    private static final int SPOTS_TO_TRY = 12;
    /** The path search never goes further than this from the door (in blocks). */
    private static final int PATH_SEARCH_RADIUS = 64;

    /**
     * A chosen hut spot.
     * @param floor    the center block of the hut's floor
     * @param doorSide the wall the door is in (the door faces this way, out of the hut)
     * @param path     the ground blocks to turn into a dirt path, starting just outside the door
     */
    public record Site(BlockPos floor, Direction doorSide, List<BlockPos> path)
    {
        /** Where the trader stands: on the floor, in the middle of the hut. */
        public BlockPos center()
        {
            return floor.above();
        }
    }

    private record Candidate(BlockPos floor, Direction doorSide, double distSqr) {}

    private TraderHut() {}

    // ---------------------------------------------------------------- choosing a spot

    /**
     * Finds an open spot just outside the village's bounding box (so it can't overlap houses or paths),
     * as close as possible to one of the village's paths, and a ground-following path to it.
     * Returns null if there is no such spot. The village's chunks must already be generated.
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

        // What the new path should connect to: the village's dirt path blocks. Desert villages
        // may not use dirt paths, so fall back to "anywhere on a street piece".
        List<BlockPos> targets = findDirtPaths(level, box);
        boolean useStreets = targets.isEmpty();
        if (useStreets)
        {
            targets = streetSurface(level, streets);
        }
        if (targets.isEmpty())
        {
            return null;
        }
        Set<Long> targetColumns = new HashSet<>();
        for (BlockPos t : targets)
        {
            targetColumns.add(column(t.getX(), t.getZ()));
        }

        List<Candidate> candidates = new ArrayList<>();
        int edge = HALF + 1; // the hut plus one block of space around it
        for (int x = box.minX() - SEARCH_MARGIN; x <= box.maxX() + SEARCH_MARGIN; x += 2)
        {
            for (int z = box.minZ() - SEARCH_MARGIN; z <= box.maxZ() + SEARCH_MARGIN; z += 2)
            {
                // Fully outside the village's box: "at the edge", and never on top of a house or path.
                if (box.intersects(x - edge, z - edge, x + edge, z + edge))
                {
                    continue;
                }
                Integer floorY = floorHeight(level, x, z);
                if (floorY == null)
                {
                    continue;
                }
                // Put the door on the side closest to a village path.
                Direction bestSide = null;
                double bestDist = Double.MAX_VALUE;
                for (Direction side : Direction.Plane.HORIZONTAL)
                {
                    int sx = x + side.getStepX() * edge;
                    int sz = z + side.getStepZ() * edge;
                    for (BlockPos t : targets)
                    {
                        double dx = t.getX() - sx;
                        double dz = t.getZ() - sz;
                        double d = dx * dx + dz * dz;
                        if (d < bestDist)
                        {
                            bestDist = d;
                            bestSide = side;
                        }
                    }
                }
                candidates.add(new Candidate(new BlockPos(x, floorY, z), bestSide, bestDist));
            }
        }

        candidates.sort(Comparator.comparingDouble(Candidate::distSqr));
        for (int i = 0; i < Math.min(SPOTS_TO_TRY, candidates.size()); i++)
        {
            Candidate c = candidates.get(i);
            List<BlockPos> path = findPath(level, c, houses, streets, targetColumns, useStreets);
            if (path != null)
            {
                return new Site(c.floor(), c.doorSide(), path);
            }
        }
        return null;
    }

    /**
     * A hut spot when there is no village: centered on {@code stand} (where the trader would stand),
     * with the door facing {@code faceToward}. The "path" is just the block in front of the door.
     */
    public static Site fallbackSite(BlockPos stand, BlockPos faceToward)
    {
        BlockPos floor = stand.below();
        Direction side = Direction.getNearest(faceToward.getX() - floor.getX(), 0, faceToward.getZ() - floor.getZ());
        if (!side.getAxis().isHorizontal())
        {
            side = Direction.SOUTH;
        }
        return new Site(floor, side, List.of(floor.relative(side, HALF + 1)));
    }

    /** Street pieces have "streets" in their template name, e.g. village/plains/streets/... */
    private static boolean isStreet(StructurePiece piece)
    {
        return piece instanceof PoolElementStructurePiece pool && pool.getElement().toString().contains("streets");
    }

    /**
     * The floor height for a hut centered on x/z, or null if the spot is no good: water or lava,
     * a tree trunk, an existing path, or ground that is too uneven.
     */
    private static Integer floorHeight(ServerLevel level, int cx, int cz)
    {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int dx = -HALF; dx <= HALF; dx++)
        {
            for (int dz = -HALF; dz <= HALF; dz++)
            {
                BlockPos ground = groundAt(level, cx + dx, cz + dz);
                BlockState state = level.getBlockState(ground);
                if (!level.getFluidState(ground).isEmpty() || state.is(BlockTags.LOGS) || state.is(Blocks.DIRT_PATH))
                {
                    return null;
                }
                min = Math.min(min, ground.getY());
                max = Math.max(max, ground.getY());
            }
        }
        return max - min <= MAX_GROUND_VARIATION ? max : null;
    }

    /** The top solid (or liquid) block in this column, ignoring leaves. */
    private static BlockPos groundAt(ServerLevel level, int x, int z)
    {
        return new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1, z);
    }

    private static List<BlockPos> findDirtPaths(ServerLevel level, BoundingBox box)
    {
        List<BlockPos> found = new ArrayList<>();
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                BlockPos ground = groundAt(level, x, z);
                if (level.getBlockState(ground).is(Blocks.DIRT_PATH))
                {
                    found.add(ground);
                }
            }
        }
        return found;
    }

    private static List<BlockPos> streetSurface(ServerLevel level, List<BoundingBox> streets)
    {
        List<BlockPos> found = new ArrayList<>();
        for (BoundingBox street : streets)
        {
            for (int x = street.minX(); x <= street.maxX(); x++)
            {
                for (int z = street.minZ(); z <= street.maxZ(); z++)
                {
                    found.add(groundAt(level, x, z));
                }
            }
        }
        return found;
    }

    // ---------------------------------------------------------------- the path

    /**
     * Breadth-first search over ground columns from just outside the door to the nearest village path.
     * Each step may go up or down at most one block, never through water or lava, and never through a
     * house or the hut itself. Returns the ground blocks to pave (not including the village path block
     * it reaches), or null if there's no way through.
     */
    private static List<BlockPos> findPath(ServerLevel level, Candidate c, List<BoundingBox> houses,
                                           List<BoundingBox> streets, Set<Long> targetColumns, boolean useStreets)
    {
        BlockPos floor = c.floor();
        int startX = floor.getX() + c.doorSide().getStepX() * (HALF + 1);
        int startZ = floor.getZ() + c.doorSide().getStepZ() * (HALF + 1);
        long start = column(startX, startZ);

        Map<Long, Long> cameFrom = new HashMap<>();
        Map<Long, Integer> heights = new HashMap<>();
        ArrayDeque<Long> queue = new ArrayDeque<>();
        cameFrom.put(start, start);
        heights.put(start, floor.getY()); // the doorstep is levelled with the hut floor
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
                        || (Math.abs(nx - floor.getX()) <= HALF && Math.abs(nz - floor.getZ()) <= HALF)
                        || insideAny(houses, nx, nz))
                {
                    continue;
                }
                BlockPos ground = groundAt(level, nx, nz);
                if (!level.getFluidState(ground).isEmpty() || Math.abs(ground.getY() - y) > 1)
                {
                    continue;
                }
                cameFrom.put(next, current);
                heights.put(next, ground.getY());
                queue.add(next);
            }
        }
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

    /** Builds the hut at the site. */
    public static void build(ServerLevel level, Site site)
    {
        BlockPos floor = site.floor();
        int y0 = floor.getY();
        BlockState wall = WALL.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        for (int dx = -HALF; dx <= HALF; dx++)
        {
            for (int dz = -HALF; dz <= HALF; dz++)
            {
                int x = floor.getX() + dx;
                int z = floor.getZ() + dz;
                boolean edge = Math.abs(dx) == HALF || Math.abs(dz) == HALF;
                // Fill any gap under the floor down to the ground.
                fillDown(level, new BlockPos(x, y0 - 1, z), Blocks.DIRT, 8);
                level.setBlock(new BlockPos(x, y0, z), edge ? wall : FLOOR.defaultBlockState(), Block.UPDATE_ALL);
                for (int y = y0 + 1; y <= y0 + 3; y++)
                {
                    level.setBlock(new BlockPos(x, y, z), edge ? wall : air, Block.UPDATE_ALL);
                }
                level.setBlock(new BlockPos(x, y0 + 4, z), wall, Block.UPDATE_ALL);
                // Clear leftover tree parts or plants above the roof.
                for (int y = y0 + 5; y <= y0 + 12; y++)
                {
                    BlockPos above = new BlockPos(x, y, z);
                    BlockState state = level.getBlockState(above);
                    if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || (!state.isAir() && state.canBeReplaced()))
                    {
                        level.setBlock(above, air, Block.UPDATE_ALL);
                    }
                }
            }
        }

        // Door in the middle of the door wall. FACING is the way a player placing it from outside would face.
        Direction doorSide = site.doorSide();
        BlockPos doorLower = floor.relative(doorSide, HALF).above();
        BlockState door = DOOR.defaultBlockState().setValue(DoorBlock.FACING, doorSide.getOpposite());
        level.setBlock(doorLower, door.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER), Block.UPDATE_ALL);
        level.setBlock(doorLower.above(), door.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER), Block.UPDATE_ALL);

        // A single glass pane at eye height in a side wall, joined to the wall on both sides.
        Direction windowSide = doorSide.getClockWise();
        BlockState pane = WINDOW.defaultBlockState()
                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(doorSide), true)
                .setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(doorSide.getOpposite()), true);
        level.setBlock(floor.relative(windowSide, HALF).above(2), pane, Block.UPDATE_ALL);
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
