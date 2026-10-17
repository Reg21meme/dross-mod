package com.reg21meme.dross.dimension.sewer;

import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModStructures;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * The rat cave: the final chamber of a sewer network, where all its pipes' channels lead, and the Rat King's battlefield.
 * A round domed cave about {@value SewerTuning#RAT_CAVE_RADIUS} x 2 blocks across and {@value SewerTuning#RAT_CAVE_HEIGHT}
 * tall in the middle, of rough tuff, deepslate and mud, its floor muddy with patches of Sewer Sludge all over the fight
 * area. Copper pipes run along its wall and across its ceiling and empty into it: water pours from their mouths into drains
 * in the floor (each mouth is boxed in with copper on every side, so the water only ever falls straight down into its
 * drain and never floods the floor), and one pipe oozes sludge onto a heap. In the middle the Rat King sleeps on a nest of
 * piled carpets, surrounded by heaps of gold, his hoard: about 70% Duskstone Gold Ore, 30% gold blocks. Lit green candles
 * stand round the nest and on top of his gold heaps, and glow lichen grows on the walls (the user's picks, October 16).
 * <p>
 * The boss area finds it through {@link RatCaves} (its nest, its bounds and an arrival spot).
 */
public final class RatCavePiece extends SewerPiece
{
    private final int x;
    private final int z;
    /**
     * The tunnels that lead in: each is a side (0 north, 1 east, 2 south, 3 west, as {@link SewerNodePiece#SIDES}) and how
     * far the tunnel's middle line is to the side of the cave's middle (blocks), packed as side * 1000 + offset + 500.
     */
    private final int[] doors;
    /** Where the boss area's teleport lands (the block your feet are in), on the first door's line inside the cave. */
    private final int arrivalX;
    private final int arrivalZ;
    /** Which of {@link #doors} the royal road comes in by, or -1. */
    private final int royalDoor;

    private volatile Layout layout;

    RatCavePiece(int x, int z, int[] doors, int royalDoor, int floorY, long seed)
    {
        super(ModStructures.RAT_CAVE.get(), box(x, z, floorY), floorY, seed);
        this.x = x;
        this.z = z;
        this.doors = doors.clone();
        this.royalDoor = royalDoor;
        int[] arrival = findArrival();
        this.arrivalX = arrival[0];
        this.arrivalZ = arrival[1];
    }

    public RatCavePiece(CompoundTag tag)
    {
        super(ModStructures.RAT_CAVE.get(), tag);
        this.x = tag.getInt("X");
        this.z = tag.getInt("Z");
        this.doors = tag.getIntArray("Doors");
        this.arrivalX = tag.getInt("ArrivalX");
        this.arrivalZ = tag.getInt("ArrivalZ");
        this.royalDoor = tag.contains("RoyalDoor") ? tag.getInt("RoyalDoor") : -1;
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        super.addAdditionalSaveData(context, tag);
        tag.putInt("X", this.x);
        tag.putInt("Z", this.z);
        tag.putIntArray("Doors", this.doors);
        tag.putInt("ArrivalX", this.arrivalX);
        tag.putInt("ArrivalZ", this.arrivalZ);
        tag.putInt("RoyalDoor", this.royalDoor);
    }

    /** How far the cave (with its wall) reaches from its middle. */
    static int reach()
    {
        return (int) Math.ceil(SewerTuning.RAT_CAVE_RADIUS + SewerTuning.RAT_CAVE_WOBBLE) + 3;
    }

    private static BoundingBox box(int x, int z, int floorY)
    {
        int r = reach();
        return new BoundingBox(x - r, floorY - 2, z - r, x + r, floorY + SewerTuning.RAT_CAVE_HEIGHT + 4, z + r);
    }

    static int side(int door)
    {
        return door / 1000;
    }

    static int offset(int door)
    {
        return door % 1000 - 500;
    }

    static int door(int side, int offset)
    {
        return side * 1000 + offset + 500;
    }

    // ---------------------------------------------------------------------------------------------
    // The API's view of it (RatCaves)
    // ---------------------------------------------------------------------------------------------

    /** The middle of its floor, where you'd stand (y = floor + 1). */
    public BlockPos centre()
    {
        return new BlockPos(this.x, this.floorY + 1, this.z);
    }

    /** The block the Rat King's feet are in when he lies on his nest: the carpet on top of the middle of the heap. */
    public BlockPos nest()
    {
        return new BlockPos(this.x, this.floorY + 1 + nestWool(0.0D), this.z);
    }

    public BlockPos arrival()
    {
        return new BlockPos(this.arrivalX, this.floorY + 1, this.arrivalZ);
    }

    public int floorY()
    {
        return this.floorY;
    }

    /** True if the royal road comes into this cave. */
    public boolean hasRoyalDoor()
    {
        return this.royalDoor >= 0 && this.royalDoor < this.doors.length;
    }

    /** True if pos is inside the cave's air (or its floor), not in the rock round it or the tunnels leading in. */
    public boolean contains(BlockPos pos)
    {
        int dx = pos.getX() - this.x;
        int dz = pos.getZ() - this.z;
        if (Math.abs(dx) > reach() || Math.abs(dz) > reach())
        {
            return false;
        }
        double d = Math.sqrt(dx * dx + dz * dz);
        double r = radiusAt(Math.atan2(dz, dx));
        return d <= r + 0.5D && pos.getY() >= this.floorY && pos.getY() <= airTop(dx, dz, d) + 1;
    }

    // ---------------------------------------------------------------------------------------------
    // The cave's shape
    // ---------------------------------------------------------------------------------------------

    /** The floor's radius in the direction {@code angle}: a fixed wobble round the circle. */
    double radiusAt(double angle)
    {
        double wobble = SewerNoise.value2(this.seed ^ 0x2A7CA5EL, 3.0D + 1.6D * Math.cos(angle), 3.0D + 1.6D * Math.sin(angle));
        return SewerTuning.RAT_CAVE_RADIUS + SewerTuning.RAT_CAVE_WOBBLE * wobble;
    }

    /** The highest air block over (dx, dz) (a dome: tallest in the middle, the wall height at the edge, a little rough). */
    int airTop(int dx, int dz, double d)
    {
        double t = Math.max(-0.3D, 1.0D - (d / SewerTuning.RAT_CAVE_RADIUS) * (d / SewerTuning.RAT_CAVE_RADIUS));
        double rough = 0.9D * SewerNoise.value2(this.seed ^ 0xD0BEL, (this.x + dx) / 4.0D, (this.z + dz) / 4.0D);
        int height = (int) Math.round(SewerTuning.RAT_CAVE_WALL_HEIGHT + (SewerTuning.RAT_CAVE_HEIGHT - SewerTuning.RAT_CAVE_WALL_HEIGHT) * t + rough);
        return this.floorY + Math.max(4, height);
    }

    boolean isAir(int dx, int y, int dz)
    {
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > radiusAt(Math.atan2(dz, dx)))
        {
            return false;
        }
        return y >= this.floorY + 1 && y <= airTop(dx, dz, d);
    }

    /** Blocks of wool under the nest's carpet at this distance from the middle (the heap): 2 in the middle, 1, then none. */
    private static int nestWool(double d)
    {
        return d <= 1.5D ? 2 : d <= 2.6D ? 1 : 0;
    }

    /** The angle at which a door's line meets the wall. */
    private double doorAngle(int door)
    {
        Direction side = SewerNodePiece.SIDES[side(door)];
        double along = SewerTuning.RAT_CAVE_RADIUS;
        int off = offset(door);
        double ax = side.getStepX() * along + side.getStepZ() * off;
        double az = side.getStepZ() * along + side.getStepX() * off;
        return Math.atan2(az, ax);
    }

    /** True if (dx, dz) is near the way in from a door (within 2 of its line, in the outer part of the cave). */
    private boolean nearDoor(int dx, int dz)
    {
        for (int door : this.doors)
        {
            Direction side = SewerNodePiece.SIDES[side(door)];
            int along = dx * side.getStepX() + dz * side.getStepZ();
            int across = dx * side.getStepZ() + dz * side.getStepX();
            if (along > 6 && Math.abs(across - offset(door)) <= 2)
            {
                return true;
            }
        }
        return false;
    }

    /** The arrival spot: on the first door's line, at least ARRIVAL_MIN_DISTANCE from the middle, inside the floor. */
    private int[] findArrival()
    {
        for (int door : this.doors)
        {
            Direction side = SewerNodePiece.SIDES[side(door)];
            int off = offset(door);
            for (int along = 0; along <= SewerTuning.RAT_CAVE_RADIUS + 2; along++)
            {
                int dx = side.getStepX() * along + side.getStepZ() * off;
                int dz = side.getStepZ() * along + side.getStepX() * off;
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d >= SewerTuning.ARRIVAL_MIN_DISTANCE && d <= radiusAt(Math.atan2(dz, dx)) - 2.0D)
                {
                    return new int[] {this.x + dx, this.z + dz};
                }
            }
        }
        // (never needed: the planner only makes caves with a door whose line has room for it)
        return new int[] {this.x + SewerTuning.ARRIVAL_MIN_DISTANCE, this.z};
    }

    /** True if the cave has room for an arrival spot on some door's line (the planner checks this before keeping the cave). */
    boolean hasArrival()
    {
        int dx = this.arrivalX - this.x;
        int dz = this.arrivalZ - this.z;
        double d = Math.sqrt(dx * dx + dz * dz);
        return d >= SewerTuning.ARRIVAL_MIN_DISTANCE && d <= radiusAt(Math.atan2(dz, dx)) - 2.0D;
    }

    // ---------------------------------------------------------------------------------------------
    // What's in it (worked out once from the seed, the same in every chunk)
    // ---------------------------------------------------------------------------------------------

    /** Every special block of the cave, by its place relative to (x, floorY, z): the nest, gold, pipes, water, plants. */
    private static final class Layout
    {
        final Map<Long, BlockState> blocks = new HashMap<>();
        /** Floor spots that are never sludge (the nest's base, the drains' rims, the arrival pad...). */
        final Set<Long> plainFloor = new HashSet<>();
    }

    private static long key(int dx, int dy, int dz)
    {
        return BlockPos.asLong(dx, dy, dz);
    }

    private static long column(int dx, int dz)
    {
        return BlockPos.asLong(dx, 0, dz);
    }

    private Layout layout()
    {
        Layout l = this.layout;
        if (l == null)
        {
            synchronized (this)
            {
                l = this.layout;
                if (l == null)
                {
                    l = makeLayout();
                    this.layout = l;
                }
            }
        }
        return l;
    }

    private Layout makeLayout()
    {
        Layout l = new Layout();
        Random random = new Random(this.seed ^ 0x8A7C0DEL);
        int r = reach();
        int ax = this.arrivalX - this.x;
        int az = this.arrivalZ - this.z;

        // The arrival pad: plain floor, nothing standing on it.
        for (int dx = -2; dx <= 2; dx++)
        {
            for (int dz = -2; dz <= 2; dz++)
            {
                l.plainFloor.add(column(ax + dx, az + dz));
            }
        }

        // The nest: piled carpets on a low heap of wool in the middle.
        int nr = (int) Math.ceil(SewerTuning.NEST_RADIUS);
        for (int dx = -nr - 1; dx <= nr + 1; dx++)
        {
            for (int dz = -nr - 1; dz <= nr + 1; dz++)
            {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= SewerTuning.NEST_RADIUS + 1.0D)
                {
                    l.plainFloor.add(column(dx, dz));
                }
                if (d > SewerTuning.NEST_RADIUS)
                {
                    continue;
                }
                int wool = nestWool(d);
                for (int dy = 1; dy <= wool; dy++)
                {
                    l.blocks.put(key(dx, dy, dz), pick(SewerTuning.NEST_WOOL, this.x + dx, this.floorY + dy, this.z + dz));
                }
                l.blocks.put(key(dx, wool + 1, dz), pick(SewerTuning.NEST_CARPETS, this.x + dx, this.floorY + wool + 1, this.z + dz));
            }
        }

        // The hoard: heaps of gold round the nest, and a few single blocks scattered between them.
        List<int[]> heapTops = new ArrayList<>();
        double turn = random.nextDouble() * Math.PI * 2.0D;
        for (int i = 0; i < SewerTuning.GOLD_HEAPS; i++)
        {
            double angle = turn + i * Math.PI * 2.0D / SewerTuning.GOLD_HEAPS + (random.nextDouble() - 0.5D) * 0.5D;
            double dist = Mth.lerp(random.nextDouble(), SewerTuning.GOLD_HEAP_DISTANCE_MIN, SewerTuning.GOLD_HEAP_DISTANCE_MAX);
            double radius = Mth.lerp(random.nextDouble(), SewerTuning.GOLD_HEAP_RADIUS_MIN, SewerTuning.GOLD_HEAP_RADIUS_MAX);
            double hx = Math.cos(angle) * dist;
            double hz = Math.sin(angle) * dist;
            int span = (int) Math.ceil(radius);
            int[] top = null;
            for (int dx = (int) Math.floor(hx) - span; dx <= (int) Math.ceil(hx) + span; dx++)
            {
                for (int dz = (int) Math.floor(hz) - span; dz <= (int) Math.ceil(hz) + span; dz++)
                {
                    double dh = Math.sqrt((dx - hx) * (dx - hx) + (dz - hz) * (dz - hz));
                    if (dh > radius || l.blocks.containsKey(key(dx, 1, dz)))
                    {
                        continue;
                    }
                    int height = dh <= radius * 0.55D ? 2 : 1;
                    for (int dy = 1; dy <= height; dy++)
                    {
                        l.blocks.put(key(dx, dy, dz), gold(dx, dy, dz));
                    }
                    l.plainFloor.add(column(dx, dz));
                    if (top == null || height > top[2] || height == top[2] && dh < Math.hypot(top[0] - hx, top[1] - hz))
                    {
                        top = new int[] {dx, dz, height};
                    }
                }
            }
            if (top != null)
            {
                heapTops.add(top);
            }
        }
        for (int i = 0; i < SewerTuning.GOLD_SCATTERED; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = Mth.lerp(random.nextDouble(), SewerTuning.NEST_RADIUS + 1.0D, SewerTuning.GOLD_HEAP_DISTANCE_MAX + 2.0D);
            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);
            if (!l.blocks.containsKey(key(dx, 1, dz)))
            {
                l.blocks.put(key(dx, 1, dz), gold(dx, 1, dz));
            }
        }

        // Copper pipes along the wall, each ending in a mouth: water pouring into a drain, or (the last ones) sludge oozing onto a heap.
        List<double[]> mouths = new ArrayList<>();
        int yp = SewerTuning.WALL_PIPE_HEIGHT;
        for (int i = 0; i < SewerTuning.WALL_PIPES; i++)
        {
            boolean sludge = i >= SewerTuning.WALL_PIPES - SewerTuning.SLUDGE_WALL_PIPES;
            for (int attempt = 0; attempt < 24; attempt++)
            {
                double a0 = random.nextDouble() * Math.PI * 2.0D;
                double span = Math.toRadians(40.0D + random.nextDouble() * 30.0D);
                double a1 = a0 + span;
                if (crossesDoor(a0, a1))
                {
                    continue;
                }
                int[] mouth = wallMouth(a1, yp);
                if (mouth == null || tooClose(mouth[0], mouth[1], ax, az, 5) || tooCloseToAny(mouth[0], mouth[1], mouths, 6))
                {
                    continue;
                }
                wallBand(l, a0, a1, yp);
                if (sludge)
                {
                    sludgeMouth(l, mouth[0], yp, mouth[1]);
                }
                else
                {
                    waterMouth(l, mouth[0], yp, mouth[1]);
                }
                mouths.add(new double[] {mouth[0], mouth[1]});
                break;
            }
        }

        // Copper pipes across the ceiling, each pouring water down into a drain CEILING_PIPE_END blocks from the middle.
        for (int i = 0; i < SewerTuning.CEILING_PIPES; i++)
        {
            for (int attempt = 0; attempt < 24; attempt++)
            {
                int sideIndex = random.nextInt(4);
                int off = (random.nextBoolean() ? 1 : -1) * (3 + random.nextInt(5));
                if (doorOnLine(sideIndex, off))
                {
                    continue;
                }
                Direction side = SewerNodePiece.SIDES[sideIndex];
                int mx = side.getStepX() * SewerTuning.CEILING_PIPE_END + side.getStepZ() * off;
                int mz = side.getStepZ() * SewerTuning.CEILING_PIPE_END + side.getStepX() * off;
                if (tooClose(mx, mz, ax, az, 5) || tooCloseToAny(mx, mz, mouths, 6))
                {
                    continue;
                }
                for (int along = SewerTuning.CEILING_PIPE_END; along <= r; along++)
                {
                    int dx = side.getStepX() * along + side.getStepZ() * off;
                    int dz = side.getStepZ() * along + side.getStepX() * off;
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d > radiusAt(Math.atan2(dz, dx)))
                    {
                        break;
                    }
                    int top = airTop(dx, dz, d) - this.floorY;
                    boolean band = along % SewerTuning.PIPE_BAND_EVERY == 0;
                    l.blocks.put(key(dx, top, dz), pick(band ? SewerTuning.PIPE_BAND : SewerTuning.PIPE_BODY, this.x + dx, this.floorY + top, this.z + dz));
                }
                int top = airTop(mx, mz, Math.sqrt(mx * mx + mz * mz)) - this.floorY;
                waterMouth(l, mx, top, mz);
                mouths.add(new double[] {mx, mz});
                break;
            }
        }

        // Pointed dripstone hanging from the roof, never over the middle (where the Rat King's poison balls fly) or a pipe.
        for (int i = 0; i < SewerTuning.RAT_CAVE_STALACTITES; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = Mth.lerp(random.nextDouble(), 8.0D, SewerTuning.RAT_CAVE_RADIUS - 2.0D);
            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);
            int length = 1 + random.nextInt(3);
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d > radiusAt(Math.atan2(dz, dx)) - 1.0D || tooCloseToAny(dx, dz, mouths, 3) || nearDoor(dx, dz))
            {
                continue;
            }
            int top = airTop(dx, dz, d) - this.floorY;
            if (top - length < 7 || l.blocks.containsKey(key(dx, top, dz)))
            {
                continue;
            }
            for (int k = 0; k < length; k++)
            {
                DripstoneThickness thickness = k == length - 1 ? DripstoneThickness.TIP : k == length - 2 ? DripstoneThickness.FRUSTUM : DripstoneThickness.BASE;
                l.blocks.put(key(dx, top - k, dz), Blocks.POINTED_DRIPSTONE.defaultBlockState()
                        .setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN).setValue(PointedDripstoneBlock.THICKNESS, thickness));
            }
        }

        // A few dim Gloomcaps at the wall's foot, on coarse dirt.
        for (int i = 0; i < SewerTuning.RAT_CAVE_GLOOMCAPS; i++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double dist = radiusAt(angle) - 1.3D;
            int dx = (int) Math.round(Math.cos(angle) * dist);
            int dz = (int) Math.round(Math.sin(angle) * dist);
            if (!isAir(dx, this.floorY + 1, dz) || nearDoor(dx, dz) || tooCloseToAny(dx, dz, mouths, 3) || tooClose(dx, dz, ax, az, 4)
                    || l.blocks.containsKey(key(dx, 1, dz)))
            {
                continue;
            }
            l.blocks.put(key(dx, 1, dz), ModBlocks.GLOOMCAP.get().defaultBlockState());
            l.blocks.put(key(dx, 0, dz), Blocks.COARSE_DIRT.defaultBlockState());
            l.plainFloor.add(column(dx, dz));
        }

        // Lit green candles: round the nest on the floor just outside its carpets, and on top of his gold heaps.
        double candleTurn = random.nextDouble() * Math.PI * 2.0D;
        double ring = SewerTuning.NEST_RADIUS + 1.2D;
        for (int i = 0; i < SewerTuning.NEST_CANDLE_SPOTS; i++)
        {
            double angle = candleTurn + i * Math.PI * 2.0D / SewerTuning.NEST_CANDLE_SPOTS;
            int dx = (int) Math.round(Math.cos(angle) * ring);
            int dz = (int) Math.round(Math.sin(angle) * ring);
            if (l.blocks.containsKey(key(dx, 1, dz)))
            {
                continue;
            }
            l.blocks.put(key(dx, 1, dz), candles(2 + random.nextInt(SewerTuning.NEST_CANDLES_MAX - 1)));
            l.plainFloor.add(column(dx, dz));
        }
        for (int i = 0; i < Math.min(SewerTuning.HEAP_CANDLE_HEAPS, heapTops.size()); i++)
        {
            int[] top = heapTops.get(i);
            if (!l.blocks.containsKey(key(top[0], top[2] + 1, top[1])))
            {
                l.blocks.put(key(top[0], top[2] + 1, top[1]), candles(1 + random.nextInt(SewerTuning.NEST_CANDLES_MAX)));
            }
        }

        // Glow lichen on the walls: air blocks against the rock at the cave's edge (not by the doors, where tunnels open it).
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                if (nearDoorWide(dx, dz))
                {
                    continue;
                }
                for (int dy = 2; dy <= SewerTuning.RAT_CAVE_HEIGHT; dy++)
                {
                    int wy = this.floorY + dy;
                    if (!isAir(dx, wy, dz) || l.blocks.containsKey(key(dx, dy, dz)) || unit(this.x + dx, wy, this.z + dz, 61) >= SewerTuning.LICHEN_RAT_CAVE)
                    {
                        continue;
                    }
                    for (Direction d : SewerNodePiece.SIDES)
                    {
                        int nx = dx + d.getStepX();
                        int nz = dz + d.getStepZ();
                        double nd = Math.sqrt(nx * nx + nz * nz);
                        // the wall: rock the cave's shell lines (within its wall ring), not more cave air
                        if (!isAir(nx, wy, nz) && nd > radiusAt(Math.atan2(nz, nx)) && nd <= radiusAt(Math.atan2(nz, nx)) + 2.0D
                                && wy <= this.floorY + SewerTuning.RAT_CAVE_WALL_HEIGHT + 3)
                        {
                            l.blocks.put(key(dx, dy, dz), Blocks.GLOW_LICHEN.defaultBlockState().setValue(MultifaceBlock.getFaceProperty(d), true));
                            break;
                        }
                    }
                }
            }
        }
        return l;
    }

    /** True if (dx, dz) is within 3 of a door's line in the outer part of the cave (where its tunnel opens the wall). */
    private boolean nearDoorWide(int dx, int dz)
    {
        for (int door : this.doors)
        {
            Direction side = SewerNodePiece.SIDES[side(door)];
            int along = dx * side.getStepX() + dz * side.getStepZ();
            int across = dx * side.getStepZ() + dz * side.getStepX();
            if (along > 6 && Math.abs(across - offset(door)) <= 3)
            {
                return true;
            }
        }
        return false;
    }

    private BlockState gold(int dx, int dy, int dz)
    {
        return unit(this.x + dx, this.floorY + dy, this.z + dz, 41) < SewerTuning.GOLD_ORE_SHARE
                ? ModBlocks.DUSKSTONE_GOLD_ORE.get().defaultBlockState() : Blocks.GOLD_BLOCK.defaultBlockState();
    }

    private boolean crossesDoor(double a0, double a1)
    {
        double margin = Math.toRadians(16.0D);
        double span = a1 - a0;
        for (int door : this.doors)
        {
            // how far round from the arc's start the door is (0 to 2 pi)
            double rel = turnsFrom(a0, doorAngle(door));
            if (rel <= span + margin || rel >= Math.PI * 2.0D - margin)
            {
                return true;
            }
        }
        return false;
    }

    /** How far round (0 to 2 pi, anticlockwise in x/z) angle b is from angle a. */
    private static double turnsFrom(double a, double b)
    {
        double rel = (b - a) % (Math.PI * 2.0D);
        return rel < 0.0D ? rel + Math.PI * 2.0D : rel;
    }

    private boolean doorOnLine(int sideIndex, int off)
    {
        for (int door : this.doors)
        {
            if (side(door) == sideIndex && Math.abs(offset(door) - off) <= 4)
            {
                return true;
            }
        }
        return false;
    }

    private static boolean tooClose(int x1, int z1, int x2, int z2, double distance)
    {
        return (x1 - x2) * (x1 - x2) + (z1 - z2) * (z1 - z2) < distance * distance;
    }

    private static boolean tooCloseToAny(int x, int z, List<double[]> spots, double distance)
    {
        for (double[] s : spots)
        {
            if ((x - s[0]) * (x - s[0]) + (z - s[1]) * (z - s[1]) < distance * distance)
            {
                return true;
            }
        }
        return false;
    }

    /** The mouth of a wall pipe ending at this angle: the air block against the wall, at the pipe's height. */
    private int[] wallMouth(double angle, int dy)
    {
        for (double d = radiusAt(angle) + 1.0D; d > SewerTuning.RAT_CAVE_RADIUS * 0.6D; d -= 0.5D)
        {
            int dx = (int) Math.round(Math.cos(angle) * d);
            int dz = (int) Math.round(Math.sin(angle) * d);
            if (isAir(dx, this.floorY + dy, dz))
            {
                return isAir(dx, this.floorY + 1, dz) && isAir(dx, this.floorY + dy + 1, dz) ? new int[] {dx, dz} : null;
            }
        }
        return null;
    }

    /** The wall pipe: a band of copper 2 blocks tall hugging the wall from angle a0 to a1. */
    private void wallBand(Layout l, double a0, double a1, int dy)
    {
        int r = reach();
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                if (turnsFrom(a0, Math.atan2(dz, dx)) > a1 - a0)
                {
                    continue;
                }
                int sx = Integer.signum(dx);
                int sz = Integer.signum(dz);
                for (int y = dy; y <= dy + 1; y++)
                {
                    int wy = this.floorY + y;
                    if (!isAir(dx, wy, dz))
                    {
                        continue;
                    }
                    boolean atWall = (sx != 0 && !isAir(dx + sx, wy, dz)) || (sz != 0 && !isAir(dx, wy, dz + sz));
                    if (!atWall)
                    {
                        continue;
                    }
                    boolean band = Math.floorMod(dx + dz, SewerTuning.PIPE_BAND_EVERY) == 0;
                    l.blocks.put(key(dx, y, dz), pick(band ? SewerTuning.PIPE_BAND : SewerTuning.PIPE_BODY, this.x + dx, wy, this.z + dz));
                }
            }
        }
    }

    /**
     * A pipe's mouth pouring water: the source in the mouth, boxed in with copper on all four sides (so it only ever pours
     * straight down), the falling water under it, and the drain it falls into (a water source in the floor, with a rim of
     * stone round it and a bed below: the water never spreads over the floor).
     */
    private void waterMouth(Layout l, int dx, int dy, int dz)
    {
        l.blocks.put(key(dx, dy, dz), water());
        l.blocks.put(key(dx, dy + 1, dz), pick(SewerTuning.PIPE_BAND, this.x + dx, this.floorY + dy + 1, this.z + dz));
        for (Direction d : SewerNodePiece.SIDES)
        {
            int nx = dx + d.getStepX();
            int nz = dz + d.getStepZ();
            if (isAir(nx, this.floorY + dy, nz))
            {
                l.blocks.put(key(nx, dy, nz), pick(SewerTuning.PIPE_BAND, this.x + nx, this.floorY + dy, this.z + nz));
            }
        }
        BlockState falling = Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, 8);
        for (int y = dy - 1; y >= 1; y--)
        {
            l.blocks.put(key(dx, y, dz), falling);
        }
        l.blocks.put(key(dx, 0, dz), water());
        l.blocks.put(key(dx, -1, dz), pick(SewerTuning.BASINS, this.x + dx, this.floorY - 1, this.z + dz));
        l.plainFloor.add(column(dx, dz));
        for (Direction d : SewerNodePiece.SIDES)
        {
            int nx = dx + d.getStepX();
            int nz = dz + d.getStepZ();
            l.blocks.put(key(nx, 0, nz), pick(SewerTuning.DRAIN_RIM, this.x + nx, this.floorY, this.z + nz));
            l.plainFloor.add(column(nx, nz));
        }
    }

    /** A pipe's mouth oozing sludge: a lump of sludge hanging in the mouth and a heap of it on the floor below. */
    private void sludgeMouth(Layout l, int dx, int dy, int dz)
    {
        BlockState sludge = ModBlocks.SEWER_SLUDGE.get().defaultBlockState();
        l.blocks.put(key(dx, dy, dz), sludge);
        double len = Math.sqrt(dx * dx + dz * dz);
        double hx = dx - dx / len * 1.2D;
        double hz = dz - dz / len * 1.2D;
        for (int x = (int) Math.floor(hx) - 2; x <= (int) Math.ceil(hx) + 2; x++)
        {
            for (int z = (int) Math.floor(hz) - 2; z <= (int) Math.ceil(hz) + 2; z++)
            {
                double d = Math.sqrt((x - hx) * (x - hx) + (z - hz) * (z - hz));
                if (d > 1.9D || !isAir(x, this.floorY + 1, z))
                {
                    continue;
                }
                l.blocks.put(key(x, 0, z), sludge);
                l.blocks.put(key(x, 1, z), sludge);
                if (d <= 0.9D)
                {
                    l.blocks.put(key(x, 2, z), sludge);
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Building it
    // ---------------------------------------------------------------------------------------------

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            ChunkPos chunkPos, BlockPos pivot)
    {
        Layout l = layout();
        int f = this.floorY;
        int r = reach();
        BlockState sludge = ModBlocks.SEWER_SLUDGE.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -r; dx <= r; dx++)
        {
            for (int dz = -r; dz <= r; dz++)
            {
                int bx = this.x + dx;
                int bz = this.z + dz;
                if (!chunkBox.isInside(bx, f, bz))
                {
                    continue;
                }
                double d = Math.sqrt(dx * dx + dz * dz);
                double radius = radiusAt(Math.atan2(dz, dx));
                if (d > radius)
                {
                    if (d <= radius + 2.0D)
                    {
                        // the rough wall
                        for (int y = f - 1; y <= f + SewerTuning.RAT_CAVE_WALL_HEIGHT + 3; y++)
                        {
                            shell(level, chunkBox, pos, bx, y, bz, pick(SewerTuning.CAVE_ROCK, bx, y, bz));
                        }
                    }
                    continue;
                }
                int top = airTop(dx, dz, d);
                for (int y = f - 1; y <= top; y++)
                {
                    BlockState special = l.blocks.get(key(dx, y - f, dz));
                    if (special != null)
                    {
                        place(level, chunkBox, pos, bx, y, bz, special);
                    }
                    else if (y == f)
                    {
                        boolean plain = l.plainFloor.contains(column(dx, dz)) || nearDoor(dx, dz) && d > radius - 5.0D;
                        BlockState floor = plain ? (nearDoor(dx, dz) ? pick(SewerTuning.FLOORS, bx, y, bz) : pick(SewerTuning.CAVE_FLOOR, bx, y, bz))
                                : sludgeAt(bx, bz, SewerTuning.SLUDGE_RAT_CAVE, SewerTuning.RAT_CAVE_SLUDGE_PATCH_SIZE) ? sludge
                                : pick(SewerTuning.CAVE_FLOOR, bx, y, bz);
                        place(level, chunkBox, pos, bx, y, bz, floor);
                    }
                    else if (y > f)
                    {
                        place(level, chunkBox, pos, bx, y, bz, air());
                    }
                }
                shell(level, chunkBox, pos, bx, top + 1, bz, pick(SewerTuning.CAVE_ROCK, bx, top + 1, bz));
                shell(level, chunkBox, pos, bx, top + 2, bz, pick(SewerTuning.CAVE_ROCK, bx, top + 2, bz));
            }
        }
    }
}
