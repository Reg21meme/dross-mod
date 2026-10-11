package com.reg21meme.dross.villager;

import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.portal.PortalOpening;
import com.reg21meme.dross.registry.ModBlocks;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Where the hub's two survivors live, worked out once from the hub's layout ({@link DrossHub#layout}) when they spawn,
 * and then saved on each of them (so nothing here runs while they walk about):
 * <ul>
 *   <li>{@link #restSpots}: their two spots side by side (mentor's first), and the way they face there: towards the
 *       arrival spot (in the Shattered Spire: on the dais beside the step, facing west).</li>
 *   <li>{@link #area}: where they may be. The hub's footprint grown by {@value #AREA_MARGIN} blocks on every side
 *       ("never more than 20 blocks outside its walls"); for the spire X -45..69, Z -54..58. Stepping outside it pops them
 *       back to their spots.</li>
 *   <li>{@link #waypoints}: places they like to walk to, each with a weight (how often it's picked). Measured from the
 *       arrival spot along the way it faces (out of the front gate): the other side of the hall, the gate, just outside
 *       it, the foot of the steps, along the road, the end of the road, a little past it, and the ground to either side
 *       in front of the walls. Each is the nearest spot they can stand on; ones that don't exist or fall outside the area
 *       are left out.</li>
 *   <li>{@link #clearance}: the cells by the exit portal they keep clear (see {@link Clearance}).</li>
 * </ul>
 */
public record SurvivorGrounds(List<BlockPos> restSpots, List<Float> restYaws, BoundingBox area, List<Waypoint> waypoints,
                              @Nullable Clearance clearance)
{
    /** The area reaches this many blocks past the hub's footprint, on every side (up and down too). */
    public static final int AREA_MARGIN = 20;

    // The places they walk to, in blocks from the arrival spot along the way it faces (out of the gate), and to the side.
    /** Just inside the gate (the spire's gate is 4 to 6 blocks in front of the arrival spot)... */
    private static final int GATE = 5;
    /** ...on either side of the walkway out of the portal (which is kept clear, 3 wide), this far to the side. */
    private static final int GATE_SIDE = 2;
    /** Just outside the gate. */
    private static final int OUTSIDE_GATE = 9;
    /** The foot of the steps down from the gate (the spire's ground starts 15 blocks out). */
    private static final int FOOT_OF_STEPS = 15;
    /** A little past the far edge of the hub's footprint (the end of the spire's road), outside the walls. */
    private static final int PAST_THE_END = 8;
    /** The ground to either side, in front of the walls: this far out... */
    private static final int SIDES_OUT = 18;
    /** ...and this far to each side. */
    private static final int SIDES_ACROSS = 16;

    // How often each kind of place is picked (higher = more often). Their own rest spot is picked with HOME_WEIGHT.
    /** Their rest spot: they come back to rest there often. */
    public static final int HOME_WEIGHT = 3;
    private static final int HALL_WEIGHT = 2;
    /** Each side of the gate (so the gate as a whole has 2). */
    private static final int GATE_WEIGHT = 1;
    private static final int STEPS_WEIGHT = 2;
    private static final int OUTSIDE_WEIGHT = 1;

    // Finding a spot to stand on near a place
    /** Looks this many blocks down from the expected height... */
    private static final int SCAN_DOWN = 16;
    /** ...then this many up. */
    private static final int SCAN_UP = 12;
    /** Two places closer than this (blocks) count as the same place: only the first is kept. */
    private static final double MIN_APART = 3.0D;

    /** A place they like to walk to, and how often it's picked (its weight). */
    public record Waypoint(BlockPos pos, int weight) {}

    /** The rest spot of the survivor with this index (0 = mentor). */
    public BlockPos restSpot(int index)
    {
        return this.restSpots.get(index);
    }

    public float restYaw(int index)
    {
        return this.restYaws.get(index);
    }

    // ------------------------------------------------------------------------------------------------------------
    // From the hub's layout
    // ------------------------------------------------------------------------------------------------------------

    /**
     * Their grounds at this hub. Server thread only (it reads blocks round the hub, loading their chunks).
     *
     * @return null if the layout has fewer than two rest spots
     */
    @Nullable
    public static SurvivorGrounds fromLayout(ServerLevel dross, DrossHub.Layout layout, EntityDimensions size)
    {
        if (layout.restSpots().size() < 2)
        {
            return null;
        }
        Vec3 arrival = layout.arrival();
        BoundingBox area = layout.footprint().inflatedBy(AREA_MARGIN);
        List<BlockPos> rest = List.of(layout.restSpots().get(0).immutable(), layout.restSpots().get(1).immutable());
        List<Float> yaws = new ArrayList<>();
        for (BlockPos spot : rest)
        {
            yaws.add(yawTowards(spot, arrival));
        }
        Clearance clearance = new Clearance(layout.exit(), arrival);
        List<Waypoint> waypoints = waypoints(dross, layout, rest.get(0), area, clearance, size);
        return new SurvivorGrounds(rest, yaws, area, waypoints, clearance);
    }

    /** The way to face from {@code from} to look at {@code to} (Minecraft's yaw: 0 = south, 90 = west). */
    static float yawTowards(BlockPos from, Vec3 to)
    {
        double dx = to.x - (from.getX() + 0.5D);
        double dz = to.z - (from.getZ() + 0.5D);
        if (dx * dx + dz * dz < 1.0E-6D)
        {
            return 0.0F;
        }
        return Mth.wrapDegrees((float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90.0F);
    }

    /** The places they walk to (see the class comment), each the nearest spot to stand on, in order out from the hall. */
    private static List<Waypoint> waypoints(ServerLevel dross, DrossHub.Layout layout, BlockPos mentorRest, BoundingBox area,
                                            Clearance clearance, EntityDimensions size)
    {
        Vec3 arrival = layout.arrival();
        Direction forward = Direction.fromYRot(layout.arrivalYaw());
        Direction right = forward.getClockWise();
        BoundingBox footprint = layout.footprint();
        double edge = switch (forward)
        {
            case SOUTH -> footprint.maxZ() + 1 - arrival.z;
            case NORTH -> arrival.z - footprint.minZ();
            case EAST -> footprint.maxX() + 1 - arrival.x;
            default -> arrival.x - footprint.minX();
        };
        int hallY = Mth.floor(arrival.y);
        List<Waypoint> found = new ArrayList<>();

        // The other side of the hall: the mentor's rest spot mirrored across the walkway out of the portal.
        Vec3 offset = Vec3.atBottomCenterOf(mentorRest).subtract(arrival);
        double along = offset.x * forward.getStepX() + offset.z * forward.getStepZ();
        double across = offset.x * right.getStepX() + offset.z * right.getStepZ();
        add(found, dross, at(arrival, forward, right, along, -across), mentorRest.getY(), area, clearance, size, HALL_WEIGHT);

        // Out through the gate (a spot on each side of the walkway), down the steps and along the road, each found near the
        // height of the one before.
        int y = add(found, dross, at(arrival, forward, right, GATE, GATE_SIDE), hallY, area, clearance, size, GATE_WEIGHT);
        y = add(found, dross, at(arrival, forward, right, GATE, -GATE_SIDE), y, area, clearance, size, GATE_WEIGHT);
        y = add(found, dross, at(arrival, forward, right, OUTSIDE_GATE, 0), y, area, clearance, size, OUTSIDE_WEIGHT);
        int stepsY = add(found, dross, at(arrival, forward, right, FOOT_OF_STEPS, 0), y, area, clearance, size, STEPS_WEIGHT);
        y = stepsY;
        if (edge > FOOT_OF_STEPS + 2 * MIN_APART)
        {
            y = add(found, dross, at(arrival, forward, right, (FOOT_OF_STEPS + edge) / 2.0D, 0), y, area, clearance, size, OUTSIDE_WEIGHT);
            y = add(found, dross, at(arrival, forward, right, edge - 1.0D, 0), y, area, clearance, size, OUTSIDE_WEIGHT);
        }
        add(found, dross, at(arrival, forward, right, Math.max(edge, FOOT_OF_STEPS) + PAST_THE_END, 0), y, area, clearance, size,
                OUTSIDE_WEIGHT);

        // The ground to either side in front of the walls.
        add(found, dross, at(arrival, forward, right, SIDES_OUT, SIDES_ACROSS), stepsY, area, clearance, size, OUTSIDE_WEIGHT);
        add(found, dross, at(arrival, forward, right, SIDES_OUT, -SIDES_ACROSS), stepsY, area, clearance, size, OUTSIDE_WEIGHT);
        return List.copyOf(found);
    }

    /** The column {@code along} blocks out from the arrival spot the way it faces, and {@code across} to its right. */
    private static int[] at(Vec3 arrival, Direction forward, Direction right, double along, double across)
    {
        double x = arrival.x + forward.getStepX() * along + right.getStepX() * across;
        double z = arrival.z + forward.getStepZ() * along + right.getStepZ() * across;
        return new int[] {Mth.floor(x), Mth.floor(z)};
    }

    /**
     * Adds the spot to stand on nearest {@code expectedY} in this column, if there is one, it's in the area, it isn't one
     * of the cells kept clear, and it isn't next to a place already found.
     *
     * @return the height of the spot found, or {@code expectedY} if none was added (the next place is looked for near it)
     */
    private static int add(List<Waypoint> found, ServerLevel level, int[] column, int expectedY, BoundingBox area,
                           Clearance clearance, EntityDimensions size, int weight)
    {
        BlockPos spot = standableNear(level, column[0], column[1], expectedY, size);
        if (spot == null || !area.isInside(spot) || clearance.keepsRestingOff(spot))
        {
            return expectedY;
        }
        for (Waypoint other : found)
        {
            if (other.pos().distSqr(spot) < MIN_APART * MIN_APART)
            {
                return spot.getY();
            }
        }
        found.add(new Waypoint(spot, weight));
        return spot.getY();
    }

    /**
     * The cell nearest {@code expectedY} in this column where something {@code size} big can stand: first looking down
     * (to {@value #SCAN_DOWN} blocks), then up (to {@value #SCAN_UP}). So inside the spire it finds the floor under the
     * expected height, not the roof, and outside the ground near it.
     */
    @Nullable
    static BlockPos standableNear(ServerLevel level, int x, int z, int expectedY, EntityDimensions size)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy <= SCAN_DOWN; dy++)
        {
            if (canStandAt(level, pos.set(x, expectedY - dy, z), size))
            {
                return pos.immutable();
            }
        }
        for (int dy = 1; dy <= SCAN_UP; dy++)
        {
            if (canStandAt(level, pos.set(x, expectedY + dy, z), size))
            {
                return pos.immutable();
            }
        }
        return null;
    }

    /**
     * True if something {@code size} big fits standing in this cell (no block or liquid in the way), with solid ground
     * under it that isn't leaves, and nothing harmful (fire, lava, magma, cactus, a portal...) in or under it.
     */
    static boolean canStandAt(ServerLevel level, BlockPos feet, EntityDimensions size)
    {
        if (feet.getY() <= level.getMinBuildHeight() || feet.getY() + 2 >= level.getMaxBuildHeight())
        {
            return false;
        }
        AABB body = size.makeBoundingBox(Vec3.atBottomCenterOf(feet));
        if (!level.noCollision(body) || level.containsAnyLiquid(body))
        {
            return false;
        }
        BlockPos below = feet.below();
        BlockState ground = level.getBlockState(below);
        VoxelShape shape = ground.getCollisionShape(level, below);
        if (shape.isEmpty() || shape.max(Direction.Axis.Y) < 0.5D || ground.is(BlockTags.LEAVES))
        {
            return false;
        }
        AABB withFloor = new AABB(body.minX, body.minY - 0.5D, body.minZ, body.maxX, body.maxY, body.maxZ).deflate(1.0E-7D);
        return level.getBlockStatesIfLoaded(withFloor).noneMatch(SurvivorGrounds::isHarmful);
    }

    private static boolean isHarmful(BlockState state)
    {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.CAMPFIRES) || state.is(Blocks.CACTUS)
                || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW) || state.is(Blocks.WITHER_ROSE)
                || state.is(Blocks.COBWEB) || state.is(BlockTags.PORTALS) || state.is(ModBlocks.DROSS_PORTAL.get())
                || state.is(ModBlocks.CINDER_LAVA.get()) || state.is(ModBlocks.CINDER_FIRE.get())
                || !state.getFluidState().isEmpty();
    }

    // ------------------------------------------------------------------------------------------------------------
    // The cells by the exit portal they keep clear
    // ------------------------------------------------------------------------------------------------------------

    /**
     * The cells by the hub's exit portal the survivors keep clear, so arriving players always land on a free step and
     * nobody walks into the portal.
     * <ul>
     *   <li>{@link #keepsWalkingOff}: they never walk through these: the opening and its frame, the steps right in front
     *       of and behind it, and every cell within a block of the arrival spot.</li>
     *   <li>{@link #keepsRestingOff}: they never pick these as a place to go or stand: all of the above, and the walkway
     *       straight out in front of the portal (as wide as the opening, {@value DrossHub#WALKWAY_DEPTH} blocks deep), which
     *       they may still cross.</li>
     * </ul>
     *
     * @param exit    the exit portal's opening
     * @param arrival where arriving players land (feet)
     */
    public record Clearance(PortalOpening exit, Vec3 arrival)
    {
        private boolean alongX()
        {
            return this.exit.axis() == Direction.Axis.X;
        }

        private int minAlong()
        {
            return alongX() ? this.exit.minCorner().getX() : this.exit.minCorner().getZ();
        }

        private int plane()
        {
            return alongX() ? this.exit.minCorner().getZ() : this.exit.minCorner().getX();
        }

        /** +1 if the arrival spot (the front) is on the + side of the portal's plane, -1 if on the - side. */
        private int front()
        {
            double arrivalPerp = alongX() ? this.arrival.z : this.arrival.x;
            return arrivalPerp >= plane() + 0.5D ? 1 : -1;
        }

        /** True for cells they never walk through: the opening and frame, either step, and near the arrival spot. */
        public boolean keepsWalkingOff(int x, int y, int z)
        {
            int along = alongX() ? x : z;
            int perp = alongX() ? z : x;
            int minAlong = minAlong();
            int maxAlong = minAlong + this.exit.width() - 1;
            int minY = this.exit.minCorner().getY();
            int plane = plane();
            if (perp == plane && along >= minAlong - 1 && along <= maxAlong + 1 && y >= minY - 2 && y <= minY + this.exit.height())
            {
                return true; // the opening and its frame
            }
            if (along >= minAlong && along <= maxAlong && Math.abs(perp - plane) == 1 && y >= minY - 2 && y <= minY + 1)
            {
                return true; // either step
            }
            return Math.abs(x + 0.5D - this.arrival.x) < 2.0D && Math.abs(z + 0.5D - this.arrival.z) < 2.0D
                    && Math.abs(y - Mth.floor(this.arrival.y)) <= 2; // within a block of the arrival spot
        }

        /** True for cells they never pick to go to or stand on: the above, and the walkway out in front of the portal. */
        public boolean keepsRestingOff(BlockPos cell)
        {
            if (keepsWalkingOff(cell.getX(), cell.getY(), cell.getZ()))
            {
                return true;
            }
            int along = alongX() ? cell.getX() : cell.getZ();
            int perp = alongX() ? cell.getZ() : cell.getX();
            int minAlong = minAlong();
            int out = (perp - plane()) * front();
            return along >= minAlong && along < minAlong + this.exit.width() && out >= 1 && out <= DrossHub.WALKWAY_DEPTH;
        }

        CompoundTag save()
        {
            CompoundTag tag = new CompoundTag();
            BlockPos min = this.exit.minCorner();
            tag.putIntArray("Exit", new int[] {min.getX(), min.getY(), min.getZ(), alongX() ? 0 : 1, this.exit.width(), this.exit.height()});
            ListTag arrivalTag = new ListTag();
            arrivalTag.add(DoubleTag.valueOf(this.arrival.x));
            arrivalTag.add(DoubleTag.valueOf(this.arrival.y));
            arrivalTag.add(DoubleTag.valueOf(this.arrival.z));
            tag.put("Arrival", arrivalTag);
            return tag;
        }

        @Nullable
        static Clearance load(CompoundTag tag)
        {
            int[] exit = tag.getIntArray("Exit");
            ListTag arrivalTag = tag.getList("Arrival", Tag.TAG_DOUBLE);
            if (exit.length != 6 || arrivalTag.size() != 3)
            {
                return null;
            }
            PortalOpening opening = new PortalOpening(new BlockPos(exit[0], exit[1], exit[2]),
                    exit[3] == 0 ? Direction.Axis.X : Direction.Axis.Z, exit[4], exit[5]);
            return new Clearance(opening, new Vec3(arrivalTag.getDouble(0), arrivalTag.getDouble(1), arrivalTag.getDouble(2)));
        }
    }
}
