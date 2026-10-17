package com.reg21meme.dross.dimension.sewer;

import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.PointedDripstoneBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DripstoneThickness;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;

/**
 * A junction of the sewer maze, where tunnels meet (or a dead end, where one stops):
 * <ul>
 *   <li>{@link Kind#CROSSING}: just the tunnels' crossing, as wide and tall as the biggest tunnel there;</li>
 *   <li>{@link Kind#ROOM}: a square brick room (pillars when three or four tunnels meet), its channels running into a
 *       drain in the middle;</li>
 *   <li>{@link Kind#POOL}: the room under a pipe entrance, with the deep pool of sewer water you land in;</li>
 *   <li>{@link Kind#GRAND_POOL}: the bigger room under the Rat King's grand entrance, its deeper pool lit green by Blight
 *       Vents in its corners;</li>
 *   <li>{@link Kind#HALL}: the big middle hall of a network without a rat cave (where its channels run);</li>
 *   <li>{@link Kind#CAVE}: a rough natural cave the sewer broke into: tuff, deepslate and mud, a muddy floor with lots of
 *       sludge, stalactites.</li>
 * </ul>
 * In the Rat King's sewer, a junction on the royal road has no water channels (the road is dry).
 * A little glow lichen grows on its walls. The tunnels joining it are their own pieces; they open its walls where they
 * arrive.
 */
public final class SewerNodePiece extends SewerPiece
{
    public enum Kind { CROSSING, ROOM, POOL, HALL, CAVE, GRAND_POOL }

    /** The four ways a tunnel can leave a junction, as bits of {@link #links}: north (-z), east (+x), south (+z), west (-x). */
    static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private final Kind kind;
    private final int x;
    private final int z;
    /** Half its inside width (square kinds) or its radius (a cave section). */
    private final int half;
    private final int height;
    /** Which sides have a tunnel (bits, see SIDES), and which of those tunnels have a water channel. */
    private final int links;
    private final int channels;
    /** On the Rat King's royal road: the "channels" mark the road's sides, and carry no water. */
    private final boolean royal;

    SewerNodePiece(Kind kind, int x, int z, int half, int height, int links, int channels, boolean royal, int floorY, long seed)
    {
        super(ModStructures.SEWER_NODE.get(), box(kind, x, z, half, height, floorY), floorY, seed);
        this.kind = kind;
        this.x = x;
        this.z = z;
        this.half = half;
        this.height = height;
        this.links = links;
        this.channels = channels;
        this.royal = royal;
    }

    public SewerNodePiece(CompoundTag tag)
    {
        super(ModStructures.SEWER_NODE.get(), tag);
        this.kind = Kind.values()[Math.max(0, Math.min(Kind.values().length - 1, tag.getInt("Kind")))];
        this.x = tag.getInt("X");
        this.z = tag.getInt("Z");
        this.half = tag.getInt("Half");
        this.height = tag.getInt("Height");
        this.links = tag.getInt("Links");
        this.channels = tag.getInt("Channels");
        this.royal = tag.getBoolean("Royal");
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        super.addAdditionalSaveData(context, tag);
        tag.putInt("Kind", this.kind.ordinal());
        tag.putInt("X", this.x);
        tag.putInt("Z", this.z);
        tag.putInt("Half", this.half);
        tag.putInt("Height", this.height);
        tag.putInt("Links", this.links);
        tag.putInt("Channels", this.channels);
        tag.putBoolean("Royal", this.royal);
    }

    private static BoundingBox box(Kind kind, int x, int z, int half, int height, int floorY)
    {
        int reach = kind == Kind.CAVE ? (int) Math.ceil(half * 1.2D) + 2 : half + 1;
        int bottom = kind == Kind.POOL ? floorY - SewerTuning.POOL_DEPTH : kind == Kind.GRAND_POOL ? floorY - SewerTuning.GRAND_POOL_DEPTH : floorY - 1;
        int top = floorY + height + (kind == Kind.CAVE ? 3 : 1);
        return new BoundingBox(x - reach, bottom, z - reach, x + reach, top, z + reach);
    }

    public Kind kind()
    {
        return this.kind;
    }

    /** True on the Rat King's royal road. */
    public boolean isRoyal()
    {
        return this.royal;
    }

    /** Half its inside width. */
    public int half()
    {
        return this.half;
    }

    /** How many tunnels meet here (1: a dead end). */
    public int linkCount()
    {
        return Integer.bitCount(this.links);
    }

    public int floorLevel()
    {
        return this.floorY;
    }

    /** The middle of its floor (the block you'd stand on is one above). */
    public BlockPos centre()
    {
        return new BlockPos(this.x, this.floorY, this.z);
    }

    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random, BoundingBox chunkBox,
                            ChunkPos chunkPos, BlockPos pivot)
    {
        if (this.kind == Kind.CAVE)
        {
            buildCave(level, chunkBox);
        }
        else
        {
            buildRoom(level, chunkBox);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Square junctions: crossings, rooms, pool rooms, the hall
    // ---------------------------------------------------------------------------------------------

    private void buildRoom(WorldGenLevel level, BoundingBox chunkBox)
    {
        int f = this.floorY;
        int h = this.height;
        boolean pillars = (this.kind == Kind.ROOM && Integer.bitCount(this.links) >= 3) || this.kind == Kind.HALL;
        double sludge = this.kind == Kind.CROSSING ? SewerTuning.SLUDGE_SIDE_TUNNELS * 0.6D : SewerTuning.SLUDGE_ROOMS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -this.half - 1; dx <= this.half + 1; dx++)
        {
            for (int dz = -this.half - 1; dz <= this.half + 1; dz++)
            {
                int bx = this.x + dx;
                int bz = this.z + dz;
                if (!chunkBox.isInside(bx, f, bz))
                {
                    continue;
                }
                boolean inside = Math.abs(dx) <= this.half && Math.abs(dz) <= this.half;
                if (!inside)
                {
                    shell(level, chunkBox, pos, bx, f, bz, pick(SewerTuning.FLOORS, bx, f, bz));
                    for (int y = f + 1; y <= f + h; y++)
                    {
                        shell(level, chunkBox, pos, bx, y, bz, y == f + 1 ? pick(SewerTuning.WALL_FOOT, bx, y, bz) : pick(SewerTuning.WALLS, bx, y, bz));
                    }
                    shell(level, chunkBox, pos, bx, f + h + 1, bz, pick(SewerTuning.ROOFS, bx, f + h + 1, bz));
                    continue;
                }
                floorAndWater(level, chunkBox, pos, dx, dz, bx, bz, sludge);
                boolean pillar = pillars && Math.abs(dx) == 2 && Math.abs(dz) == 2;
                for (int y = f + 1; y <= f + h; y++)
                {
                    place(level, chunkBox, pos, bx, y, bz, pillar ? pick(SewerTuning.PILLARS, bx, y, bz) : air());
                }
                shell(level, chunkBox, pos, bx, f + h + 1, bz, pick(SewerTuning.ROOFS, bx, f + h + 1, bz));
            }
        }
        roomLichen(level, chunkBox, pos);
    }

    /**
     * Glow lichen on the room's walls (now they're there): never where a tunnel arrives (another piece opens the wall
     * there), and not on a plain crossing (it's just the tunnels' crossing).
     */
    private void roomLichen(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos)
    {
        if (this.kind == Kind.CROSSING)
        {
            return;
        }
        int f = this.floorY;
        for (int i = 0; i < 4; i++)
        {
            Direction side = SIDES[i];
            boolean linked = (this.links & (1 << i)) != 0;
            for (int along = -this.half; along <= this.half; along++)
            {
                if (linked && Math.abs(along) <= SewerTuning.MAIN_WIDTH / 2 + 1)
                {
                    continue;
                }
                int bx = this.x + side.getStepX() * this.half + side.getStepZ() * along;
                int bz = this.z + side.getStepZ() * this.half + side.getStepX() * along;
                for (int y = f + 2; y <= f + this.height - 1; y++)
                {
                    if (unit(bx, y, bz, 17) < SewerTuning.LICHEN_TUNNELS)
                    {
                        lichen(level, chunkBox, pos, bx, y, bz, side);
                    }
                }
            }
        }
    }

    /** The floor at one spot inside: the pool, the drain, a channel, or plain floor (with sludge patches). */
    private void floorAndWater(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int dx, int dz, int bx, int bz, double sludge)
    {
        int f = this.floorY;
        int ax = Math.abs(dx);
        int az = Math.abs(dz);
        if (this.kind == Kind.POOL || this.kind == Kind.GRAND_POOL)
        {
            boolean grand = this.kind == Kind.GRAND_POOL;
            int p = grand ? SewerTuning.GRAND_POOL_HALF : SewerTuning.POOL_HALF;
            int depth = grand ? SewerTuning.GRAND_POOL_DEPTH : SewerTuning.POOL_DEPTH;
            if (ax <= p && az <= p)
            {
                for (int y = f - depth + 1; y <= f; y++)
                {
                    place(level, chunkBox, pos, bx, y, bz, water());
                }
                place(level, chunkBox, pos, bx, f - depth, bz, pick(SewerTuning.BASINS, bx, f - depth, bz));
                return;
            }
            if (ax <= p + 1 && az <= p + 1)
            {
                // the basin's sides, under the floor
                for (int y = f - depth; y < f; y++)
                {
                    place(level, chunkBox, pos, bx, y, bz, pick(SewerTuning.BASINS, bx, y, bz));
                }
            }
            if (grand && SewerTuning.POOL_ROOM_VENTS && ax == this.half && az == this.half)
            {
                // the green glow: a Blight Vent in each corner of the floor
                place(level, chunkBox, pos, bx, f, bz, ModBlocks.BLIGHT_VENT.get().defaultBlockState());
                return;
            }
            if (!this.royal && channelAt(dx, dz, p + 1, 0))
            {
                channel(level, chunkBox, pos, bx, bz);
                return;
            }
            place(level, chunkBox, pos, bx, f, bz, floor(bx, bz, sludge, SewerTuning.FLOORS));
            return;
        }
        if (this.royal)
        {
            // the royal road has no water: plain floor across the junction
            place(level, chunkBox, pos, bx, f, bz, floor(bx, bz, sludge, SewerTuning.FLOORS));
            return;
        }
        int drain = this.kind == Kind.CROSSING ? 0 : 1;
        if (this.channels != 0 && ax <= drain && az <= drain)
        {
            channel(level, chunkBox, pos, bx, bz);
            return;
        }
        if (channelAt(dx, dz, drain + 1, 0))
        {
            channel(level, chunkBox, pos, bx, bz);
            return;
        }
        place(level, chunkBox, pos, bx, f, bz, floor(bx, bz, sludge, SewerTuning.FLOORS));
    }

    /**
     * True if (dx, dz) is on one of this junction's channels (within {@code width} of its line), from {@code start} blocks
     * out from the middle to its wall.
     */
    private boolean channelAt(int dx, int dz, int start, int width)
    {
        for (int i = 0; i < 4; i++)
        {
            if ((this.channels & (1 << i)) == 0)
            {
                continue;
            }
            Direction side = SIDES[i];
            int along = dx * side.getStepX() + dz * side.getStepZ();
            int across = dx * side.getStepZ() + dz * side.getStepX();
            if (Math.abs(across) <= width && along >= start && along <= this.half)
            {
                return true;
            }
        }
        return false;
    }

    private void channel(WorldGenLevel level, BoundingBox chunkBox, BlockPos.MutableBlockPos pos, int bx, int bz)
    {
        place(level, chunkBox, pos, bx, this.floorY, bz, water());
        place(level, chunkBox, pos, bx, this.floorY - 1, bz, pick(SewerTuning.BASINS, bx, this.floorY - 1, bz));
    }

    // ---------------------------------------------------------------------------------------------
    // Rough cave sections
    // ---------------------------------------------------------------------------------------------

    /** The cave's radius in the direction of (dx, dz): its edge wobbles (a fixed wobble round the circle). */
    private double radiusAt(int dx, int dz)
    {
        double angle = Math.atan2(dz, dx);
        double wobble = SewerNoise.value2(this.seed ^ 0xCA7EL, 2.0D + 1.4D * Math.cos(angle), 2.0D + 1.4D * Math.sin(angle));
        return this.half * (1.0D + 0.18D * wobble);
    }

    /** How many blocks of air over the floor at (dx, dz) (0 outside the cave): highest in the middle, at least 3 at the edge. */
    private int airAt(int dx, int dz)
    {
        double r = radiusAt(dx, dz);
        double d = Math.sqrt(dx * dx + dz * dz);
        if (d > r)
        {
            return 0;
        }
        double t = 1.0D - (d / r) * (d / r);
        double rough = SewerNoise.value2(this.seed ^ 0xF00DL, (this.x + dx) / 3.0D, (this.z + dz) / 3.0D);
        return Math.max(3, (int) Math.round(3 + (this.height - 3) * t + rough));
    }

    private void buildCave(WorldGenLevel level, BoundingBox chunkBox)
    {
        int f = this.floorY;
        int reach = (int) Math.ceil(this.half * 1.2D) + 2;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -reach; dx <= reach; dx++)
        {
            for (int dz = -reach; dz <= reach; dz++)
            {
                int bx = this.x + dx;
                int bz = this.z + dz;
                if (!chunkBox.isInside(bx, f, bz))
                {
                    continue;
                }
                int air = airAt(dx, dz);
                if (air == 0)
                {
                    double d = Math.sqrt(dx * dx + dz * dz);
                    if (d <= radiusAt(dx, dz) + 1.6D)
                    {
                        // the rough wall round it
                        int wallTop = f + 4;
                        for (int y = f; y <= wallTop; y++)
                        {
                            shell(level, chunkBox, pos, bx, y, bz, pick(SewerTuning.CAVE_ROCK, bx, y, bz));
                        }
                    }
                    continue;
                }
                if (channelAt(dx, dz, 1, 0) || (this.channels != 0 && dx == 0 && dz == 0))
                {
                    channel(level, chunkBox, pos, bx, bz);
                }
                else
                {
                    place(level, chunkBox, pos, bx, f, bz, floor(bx, bz, SewerTuning.SLUDGE_CAVE_SECTIONS, SewerTuning.CAVE_FLOOR));
                }
                int top = f + air;
                boolean stalactite = air >= 4 && unit(bx, top, bz, 21) < SewerTuning.CAVE_SECTION_STALACTITE_CHANCE;
                for (int y = f + 1; y <= top; y++)
                {
                    BlockState state = stalactite && y == top
                            ? Blocks.POINTED_DRIPSTONE.defaultBlockState().setValue(PointedDripstoneBlock.TIP_DIRECTION, Direction.DOWN)
                                    .setValue(PointedDripstoneBlock.THICKNESS, DripstoneThickness.TIP)
                            : air();
                    place(level, chunkBox, pos, bx, y, bz, state);
                }
                shell(level, chunkBox, pos, bx, top + 1, bz, pick(SewerTuning.CAVE_ROCK, bx, top + 1, bz));
                shell(level, chunkBox, pos, bx, top + 2, bz, pick(SewerTuning.CAVE_ROCK, bx, top + 2, bz));
            }
        }
    }
}
