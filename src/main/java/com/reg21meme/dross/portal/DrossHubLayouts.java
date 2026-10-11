package com.reg21meme.dross.portal;

import com.reg21meme.dross.world.shrine.ShrineDesign;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * Works out the parts of the hub's layout ({@link DrossHub#layout}) that aren't simply saved:
 * <ul>
 *   <li>{@link #derive}: for a hub built before layouts were saved, which hub it is and what it covers, read from its
 *       real exit frame. Never changes any blocks.</li>
 *   <li>{@link #restSpots}: two free cells side by side near the exit portal, where the hub's two survivors (Villager
 *       area) rest.</li>
 * </ul>
 */
final class DrossHubLayouts
{
    /** How far around the preferred rest spots to look for two free cells side by side (blocks), and then further still. */
    private static final int REST_SEARCH_RADIUS = 6;
    private static final int REST_SEARCH_WIDE = 12;
    /** How far above or below the preferred rest spots to look (blocks). */
    private static final int REST_SEARCH_HEIGHT = 3;
    /** The survivors are villager-sized (0.6 wide, 1.95 tall). */
    private static final EntityDimensions REST_SIZE = EntityType.VILLAGER.getDimensions();

    private DrossHubLayouts() {}

    /** A layout worked out from an older hub's frame. */
    record Derived(DrossHub.Kind kind, BoundingBox footprint, int groundY) {}

    /**
     * What an older hub is, from its real exit frame:
     * <ul>
     *   <li>a 3x5 opening along X exactly where the Shattered Spire's frame is (its middle column on the hub centre): the
     *       spire, standing on ground 9 blocks under the opening, covering its design's footprint;</li>
     *   <li>a 2x3 opening: the placeholder, an 11x11 platform round it;</li>
     *   <li>anything else: unknown, the box round its frame and 5 blocks more each way.</li>
     * </ul>
     */
    static Derived derive(PortalOpening exit)
    {
        Optional<ShrineDesign> spire = ShrineDesign.byId(DrossHubBuilder.SPIRE_DESIGN_ID);
        if (spire.isPresent())
        {
            ShrineDesign design = spire.get();
            int ground = exit.minCorner().getY() - DrossHubBuilder.spireOpeningAboveGround(design);
            ShrineDesign.Built built = design.built(design.centerForFrameAt(DrossHub.CENTER.getX(), DrossHub.CENTER.getZ(), ground,
                    DrossHubBuilder.SPIRE_FACING), DrossHubBuilder.SPIRE_FACING);
            if (built.openingMin().equals(exit.minCorner()) && built.frameAxis() == exit.axis()
                    && built.openingWidth() == exit.width() && built.openingHeight() == exit.height())
            {
                return new Derived(DrossHub.Kind.SPIRE, built.footprint(), ground);
            }
        }
        int floorY = exit.minCorner().getY() - 1;
        if (exit.width() == 2 && exit.height() == 3)
        {
            return new Derived(DrossHub.Kind.PLACEHOLDER, placeholderBoxAround(exit), floorY);
        }
        return new Derived(DrossHub.Kind.UNKNOWN, boxAroundFrame(exit), floorY);
    }

    /**
     * The placeholder's 11x11 platform for its 2x3 opening (the opening is the middle two of its four frame columns;
     * the platform's centre column is the opening's second column).
     */
    static BoundingBox placeholderBoxAround(PortalOpening exit)
    {
        BlockPos min = exit.minCorner();
        boolean alongX = exit.axis() == Direction.Axis.X;
        int cx = alongX ? min.getX() + 1 : min.getX();
        int cz = alongX ? min.getZ() : min.getZ() + 1;
        return DrossHubBuilder.placeholderBox(cx, cz, min.getY() - 1);
    }

    /** The exit frame (the opening and its frame) and {@link DrossHubBuilder#PLATFORM_RADIUS} blocks more on every side. */
    static BoundingBox boxAroundFrame(PortalOpening exit)
    {
        BlockPos min = exit.minCorner();
        boolean alongX = exit.axis() == Direction.Axis.X;
        int r = DrossHubBuilder.PLATFORM_RADIUS;
        int alongMin = (alongX ? min.getX() : min.getZ()) - 1 - r;
        int alongMax = (alongX ? min.getX() : min.getZ()) + exit.width() + r;
        int perp = alongX ? min.getZ() : min.getX();
        int minY = min.getY() - 1 - DrossHubBuilder.SUPPORT_DEPTH;
        int maxY = min.getY() + exit.height() + 1;
        return alongX
                ? new BoundingBox(alongMin, minY, perp - r, alongMax, maxY, perp + r)
                : new BoundingBox(perp - r, minY, alongMin, perp + r, maxY, alongMax);
    }

    // ------------------------------------------------------------------ rest spots

    /**
     * Two free cells side by side, near the exit portal, where the survivors rest (the cells their feet are in).
     * <ul>
     *   <li>First choice: two blocks past the frame's end (on its +X side for a frame along X), one and two blocks in
     *       front of it, one level below the opening. In the Shattered Spire that's on its dais, beside the step:
     *       (3, ground+8, 1) and (3, ground+8, 2).</li>
     *   <li>Otherwise the nearest such pair within {@value #REST_SEARCH_RADIUS} blocks (then
     *       {@value #REST_SEARCH_WIDE}) of the first choice.</li>
     *   <li>Never in or next to the opening, on the steps right in front of or behind it, on the walkway straight out in
     *       front of it ({@value DrossHub#WALKWAY_DEPTH} blocks), or within a block of the arrival spot. Each cell has
     *       room for a villager-sized body and solid ground under it (not leaves), and nothing harmful.</li>
     * </ul>
     *
     * @return the two cells (mentor's first), or an empty list if there's no such pair (never for the spire or the placeholder)
     */
    static List<BlockPos> restSpots(ServerLevel dross, PortalOpening exit, Vec3 arrival)
    {
        Lane lane = new Lane(exit);
        BlockPos first = lane.cell(lane.maxAlong + 2, lane.minY - 1, lane.perp + 1);
        BlockPos second = lane.cell(lane.maxAlong + 2, lane.minY - 1, lane.perp + 2);
        if (isRestSpot(dross, lane, arrival, first) && isRestSpot(dross, lane, arrival, second))
        {
            return List.of(first, second);
        }
        for (int radius : new int[] {REST_SEARCH_RADIUS, REST_SEARCH_WIDE})
        {
            List<BlockPos> pair = searchPair(dross, lane, arrival, first, radius);
            if (!pair.isEmpty())
            {
                return pair;
            }
        }
        return List.of();
    }

    /** The nearest free pair of cells side by side within {@code radius} of {@code around}. */
    private static List<BlockPos> searchPair(ServerLevel dross, Lane lane, Vec3 arrival, BlockPos around, int radius)
    {
        List<BlockPos> cells = new ArrayList<>();
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                for (int dy = -REST_SEARCH_HEIGHT; dy <= REST_SEARCH_HEIGHT; dy++)
                {
                    cells.add(around.offset(dx, dy, dz));
                }
            }
        }
        cells.sort(Comparator.comparingDouble(cell -> cell.distSqr(around)));
        Direction[] sides = {lane.perpDirection, lane.perpDirection.getOpposite(), lane.alongDirection, lane.alongDirection.getOpposite()};
        for (BlockPos cell : cells)
        {
            if (!isRestSpot(dross, lane, arrival, cell))
            {
                continue;
            }
            for (Direction side : sides)
            {
                BlockPos next = cell.relative(side);
                if (isRestSpot(dross, lane, arrival, next))
                {
                    return List.of(cell, next);
                }
            }
        }
        return List.of();
    }

    /** A free cell for a survivor's feet, clear of the portal, its steps, the walkway and the arrival spot. */
    private static boolean isRestSpot(ServerLevel dross, Lane lane, Vec3 arrival, BlockPos cell)
    {
        if (lane.isKeptClear(cell) || isNearArrival(cell, arrival))
        {
            return false;
        }
        if (dross.getBlockState(cell.below()).is(BlockTags.LEAVES))
        {
            return false;
        }
        return DrossTeleporter.isStandable(dross, REST_SIZE, Vec3.atBottomCenterOf(cell));
    }

    /** True if the cell is within a block of the arrival spot (along X and Z), at about the same height. */
    static boolean isNearArrival(BlockPos cell, Vec3 arrival)
    {
        return Math.abs(cell.getX() + 0.5D - arrival.x) < 2.0D && Math.abs(cell.getZ() + 0.5D - arrival.z) < 2.0D
                && Math.abs(cell.getY() - Mth.floor(arrival.y)) <= 2;
    }

    /** The exit opening seen as a lane: "along" the frame, "perp" straight through it (front = +perp). */
    static final class Lane
    {
        final boolean alongX;
        final int minAlong;
        final int maxAlong;
        final int perp;
        final int minY;
        final int height;
        final Direction alongDirection;
        final Direction perpDirection;

        Lane(PortalOpening exit)
        {
            BlockPos min = exit.minCorner();
            this.alongX = exit.axis() == Direction.Axis.X;
            this.minAlong = alongX ? min.getX() : min.getZ();
            this.maxAlong = minAlong + exit.width() - 1;
            this.perp = alongX ? min.getZ() : min.getX();
            this.minY = min.getY();
            this.height = exit.height();
            this.alongDirection = alongX ? Direction.EAST : Direction.SOUTH;
            this.perpDirection = alongX ? Direction.SOUTH : Direction.EAST;
        }

        BlockPos cell(int along, int y, int perpendicular)
        {
            return alongX ? new BlockPos(along, y, perpendicular) : new BlockPos(perpendicular, y, along);
        }

        int alongOf(BlockPos cell)
        {
            return alongX ? cell.getX() : cell.getZ();
        }

        int perpOf(BlockPos cell)
        {
            return alongX ? cell.getZ() : cell.getX();
        }

        /**
         * True for cells kept clear for the people using the portal: in or beside the opening (the frame's plane), on the
         * steps right in front of and behind it, and the walkway straight out in front of it.
         */
        boolean isKeptClear(BlockPos cell)
        {
            int along = alongOf(cell);
            int p = perpOf(cell);
            int y = cell.getY();
            boolean inLane = along >= minAlong && along <= maxAlong;
            if (p == perp && along >= minAlong - 1 && along <= maxAlong + 1 && y >= minY - 2 && y <= minY + height)
            {
                return true; // the opening and its frame
            }
            if (inLane && Math.abs(p - perp) == 1 && y >= minY - 2 && y <= minY + 1)
            {
                return true; // either step
            }
            return inLane && p >= perp + 1 && p <= perp + DrossHub.WALKWAY_DEPTH; // the walkway out in front
        }
    }
}
