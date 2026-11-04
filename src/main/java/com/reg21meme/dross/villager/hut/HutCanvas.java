package com.reg21meme.dross.villager.hut;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.StairsShape;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Where a hut design draws its blocks. Designs use their own local grid, always drawn as if the
 * door faces south:
 * <ul>
 *   <li>x runs from 0 (west) to width - 1 (east),</li>
 *   <li>z runs from 0 (the back, north) to depth - 1 (the front, south: the door side),</li>
 *   <li>y = 0 is the ground layer (floors replace it), y = 1 is where you stand, and y = -1 is just underground.</li>
 * </ul>
 * The canvas turns that grid (and every stair, door, log...) to face the real direction.
 * <p>
 * Blocks are only recorded while drawing; {@link #apply} works out the shapes that depend on neighbours from the
 * recorded blocks themselves (glass panes join each other and the walls, stair corners round off: {@link #shaped}), then
 * places them bottom-up with a known shape and no neighbour updates, the way Minecraft's own structures are placed. So
 * a hut built a chunk at a time with the land (the Rift Chapels: {@code villager.RiftChapelPiece}) comes out exactly the
 * same whichever of its chunks is made first: each chunk places only the blocks inside its own part ({@code clip}), and
 * reads the world (where the ground is) only there.
 */
final class HutCanvas
{
    /** No neighbour or shape updates while placing; the clients still get every block. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** A foundation goes at most this many blocks down looking for solid ground. */
    private static final int MAX_FOUNDATION_DEPTH = 8;

    /** The world it's drawn into, or null when it's only drawn (to know what goes where: no reads, nothing placed). */
    @Nullable
    private final WorldGenLevel level;
    /** Only blocks inside this box are read or placed (a chunk being made); null for no limit. */
    @Nullable
    private final BoundingBox clip;
    private final BlockPos center;
    private final int centerX;
    private final int centerZ;
    private final Rotation rotation;
    private final RandomSource random;
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

    HutCanvas(@Nullable WorldGenLevel level, @Nullable BoundingBox clip, BlockPos center, Direction facing, int width, int depth, long seed)
    {
        this.level = level;
        this.clip = clip;
        this.center = center;
        this.centerX = width / 2;
        this.centerZ = depth / 2;
        this.rotation = rotationFor(facing);
        this.random = RandomSource.create(seed);
    }

    /** The turn that takes "door faces south" (how designs are drawn) to "door faces {@code facing}". */
    static Rotation rotationFor(Direction facing)
    {
        return switch (facing)
        {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /**
     * Local grid position to world position, for a design whose center column (width / 2, depth / 2)
     * sits on {@code center} with its door facing {@code facing}.
     */
    static BlockPos toWorld(BlockPos center, Direction facing, int width, int depth, int x, int y, int z)
    {
        int dx = x - width / 2;
        int dz = z - depth / 2;
        return switch (rotationFor(facing))
        {
            case CLOCKWISE_90 -> center.offset(-dz, y, dx);
            case CLOCKWISE_180 -> center.offset(-dx, y, -dz);
            case COUNTERCLOCKWISE_90 -> center.offset(dz, y, -dx);
            default -> center.offset(dx, y, dz);
        };
    }

    private BlockPos toWorld(int x, int y, int z)
    {
        int dx = x - centerX;
        int dz = z - centerZ;
        return switch (rotation)
        {
            case CLOCKWISE_90 -> center.offset(-dz, y, dx);
            case CLOCKWISE_180 -> center.offset(-dx, y, -dz);
            case COUNTERCLOCKWISE_90 -> center.offset(dz, y, -dx);
            default -> center.offset(dx, y, dz);
        };
    }

    /** The same random numbers every time for the same design, so it always looks the same. */
    RandomSource random()
    {
        return random;
    }

    // ---------------------------------------------------------------- drawing

    void set(int x, int y, int z, BlockState state)
    {
        blocks.put(toWorld(x, y, z), state.rotate(rotation));
    }

    void set(int x, int y, int z, Block block)
    {
        set(x, y, z, block.defaultBlockState());
    }

    /** A solid box between two corners (inclusive, in any order). */
    void fill(int x1, int y1, int z1, int x2, int y2, int z2, BlockState state)
    {
        for (int y = Math.min(y1, y2); y <= Math.max(y1, y2); y++)
        {
            for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
            {
                for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
                {
                    set(x, y, z, state);
                }
            }
        }
    }

    void fill(int x1, int y1, int z1, int x2, int y2, int z2, Block block)
    {
        fill(x1, y1, z1, x2, y2, z2, block.defaultBlockState());
    }

    void air(int x1, int y1, int z1, int x2, int y2, int z2)
    {
        fill(x1, y1, z1, x2, y2, z2, Blocks.AIR);
    }

    /** The four walls of a box (its outline seen from above), from {@code y1} up to {@code y2}. */
    void walls(int x1, int z1, int x2, int z2, int y1, int y2, BlockState state)
    {
        for (int y = y1; y <= y2; y++)
        {
            for (int x = x1; x <= x2; x++)
            {
                set(x, y, z1, state);
                set(x, y, z2, state);
            }
            for (int z = z1 + 1; z < z2; z++)
            {
                set(x1, y, z, state);
                set(x2, y, z, state);
            }
        }
    }

    void walls(int x1, int z1, int x2, int z2, int y1, int y2, Block block)
    {
        walls(x1, z1, x2, z2, y1, y2, block.defaultBlockState());
    }

    /** A door (two halves) at x/y/z, in a wall it opens out of towards {@code outward}. */
    void door(int x, int y, int z, Block door, Direction outward)
    {
        BlockState state = door.defaultBlockState().setValue(DoorBlock.FACING, outward.getOpposite());
        set(x, y, z, state.setValue(DoorBlock.HALF, DoubleBlockHalf.LOWER));
        set(x, y + 1, z, state.setValue(DoorBlock.HALF, DoubleBlockHalf.UPPER));
    }

    /**
     * Fills from y = -1 downwards with {@code block} until solid ground, under the given area,
     * so a hut built over a dip doesn't float. Does nothing on flat ground.
     */
    void foundation(int x1, int z1, int x2, int z2, Block block)
    {
        for (int x = Math.min(x1, x2); x <= Math.max(x1, x2); x++)
        {
            for (int z = Math.min(z1, z2); z <= Math.max(z1, z2); z++)
            {
                if (!this.readable(toWorld(x, 0, z)))
                {
                    continue; // (a column outside the chunk being made: its own chunk lays its foundation)
                }
                for (int y = -1; y >= -MAX_FOUNDATION_DEPTH; y--)
                {
                    BlockPos pos = toWorld(x, y, z);
                    BlockState state = level.getBlockState(pos);
                    if (state.blocksMotion() && level.getFluidState(pos).isEmpty())
                    {
                        break;
                    }
                    set(x, y, z, block);
                }
            }
        }
    }

    /**
     * True if the world's ground at this spot (y = 0) is solid and dry (before anything is placed). Where the world isn't
     * read (only drawing, or outside the chunk being made) it counts as solid: no foundation is laid there.
     */
    boolean isSolidGround(int x, int z)
    {
        return isSolid(x, 0, z);
    }

    /** True if the world at this spot (before anything is placed) is solid and dry (solid where it isn't read). */
    boolean isSolid(int x, int y, int z)
    {
        BlockPos pos = toWorld(x, y, z);
        if (!this.readable(pos))
        {
            return true;
        }
        return level.getBlockState(pos).blocksMotion() && level.getFluidState(pos).isEmpty();
    }

    /** True if the world at this spot (before anything is placed) is a plain dirt block (never where it isn't read). */
    boolean isDirt(int x, int y, int z)
    {
        BlockPos pos = toWorld(x, y, z);
        return this.readable(pos) && level.getBlockState(pos).is(Blocks.DIRT);
    }

    /** True if this spot of the world is read: there is a world, and the spot is in the part being made. */
    private boolean readable(BlockPos pos)
    {
        return level != null && (clip == null || clip.isInside(pos.getX(), clip.minY(), pos.getZ()));
    }

    // ---------------------------------------------------------------- placing

    /**
     * Places the recorded blocks that are inside the part being made ({@code clip}; all of them if there's none),
     * bottom-up, each in its final shape ({@link #shaped}), without neighbour updates.
     *
     * @return how many blocks were placed
     */
    int apply()
    {
        if (level == null)
        {
            return 0;
        }
        List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(this.shaped().entrySet());
        list.sort(Comparator.comparingInt(entry -> entry.getKey().getY()));
        int placed = 0;
        for (Map.Entry<BlockPos, BlockState> entry : list)
        {
            if (clip == null || clip.isInside(entry.getKey()))
            {
                level.setBlock(entry.getKey(), entry.getValue(), FLAGS);
                placed++;
            }
        }
        return placed;
    }

    /**
     * Every recorded block in its final shape: glass panes (and iron bars) joined to the panes and solid faces beside
     * them, stairs turned into corners where two meet at a right angle (Minecraft's own rules for both), worked out from
     * the recorded blocks alone (a spot with nothing recorded counts as air), so every chunk of a hut gets the same
     * answer. Other blocks are kept as drawn. In drawing order.
     */
    Map<BlockPos, BlockState> shaped()
    {
        BlockGetter view = new Recorded();
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet())
        {
            BlockPos pos = entry.getKey();
            BlockState state = entry.getValue();
            if (state.getBlock() instanceof IronBarsBlock bars)
            {
                for (Direction side : Direction.Plane.HORIZONTAL)
                {
                    BlockPos next = pos.relative(side);
                    BlockState there = view.getBlockState(next);
                    boolean joins = bars.attachsTo(there, there.isFaceSturdy(view, next, side.getOpposite()));
                    state = state.setValue(side(side), joins);
                }
            }
            else if (state.getBlock() instanceof StairBlock)
            {
                state = state.setValue(StairBlock.SHAPE, stairsShape(state, view, pos));
            }
            out.put(pos, state);
        }
        return Collections.unmodifiableMap(out);
    }

    /** A pane's (or iron bars') join towards {@code side}. */
    private static BooleanProperty side(Direction side)
    {
        return switch (side)
        {
            case NORTH -> BlockStateProperties.NORTH;
            case EAST -> BlockStateProperties.EAST;
            case SOUTH -> BlockStateProperties.SOUTH;
            default -> BlockStateProperties.WEST;
        };
    }

    /** A stair's shape from its neighbours, exactly Minecraft's rule ({@code StairBlock.getStairsShape}, which is private). */
    private static StairsShape stairsShape(BlockState state, BlockGetter view, BlockPos pos)
    {
        Direction facing = state.getValue(StairBlock.FACING);
        Half half = state.getValue(StairBlock.HALF);
        BlockState front = view.getBlockState(pos.relative(facing));
        if (StairBlock.isStairs(front) && front.getValue(StairBlock.HALF) == half)
        {
            Direction other = front.getValue(StairBlock.FACING);
            if (other.getAxis() != facing.getAxis() && canTakeShape(state, view, pos, other.getOpposite()))
            {
                return other == facing.getCounterClockWise() ? StairsShape.OUTER_LEFT : StairsShape.OUTER_RIGHT;
            }
        }
        BlockState back = view.getBlockState(pos.relative(facing.getOpposite()));
        if (StairBlock.isStairs(back) && back.getValue(StairBlock.HALF) == half)
        {
            Direction other = back.getValue(StairBlock.FACING);
            if (other.getAxis() != facing.getAxis() && canTakeShape(state, view, pos, other))
            {
                return other == facing.getCounterClockWise() ? StairsShape.INNER_LEFT : StairsShape.INNER_RIGHT;
            }
        }
        return StairsShape.STRAIGHT;
    }

    private static boolean canTakeShape(BlockState state, BlockGetter view, BlockPos pos, Direction side)
    {
        BlockState there = view.getBlockState(pos.relative(side));
        return !StairBlock.isStairs(there) || there.getValue(StairBlock.FACING) != state.getValue(StairBlock.FACING)
                || there.getValue(StairBlock.HALF) != state.getValue(StairBlock.HALF);
    }

    /** The recorded blocks as a little world of their own: what's recorded, air everywhere else (for {@link #shaped}). */
    private final class Recorded implements BlockGetter
    {
        @Override
        @Nullable
        public BlockEntity getBlockEntity(BlockPos pos)
        {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos)
        {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos)
        {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight()
        {
            return level != null ? level.getHeight() : 384;
        }

        @Override
        public int getMinBuildHeight()
        {
            return level != null ? level.getMinBuildHeight() : -64;
        }
    }

    // ---------------------------------------------------------------- block states (drawn as if the door faces south)

    /** Stairs you walk up while moving towards {@code facing} (the tall side is on that side). */
    static BlockState stairs(Block block, Direction facing)
    {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    /** Anything that points somewhere: wall torches, ladders, wall banners, end rods, lightning rods, glazed terracotta... */
    static BlockState facing(Block block, Direction direction)
    {
        BlockState state = block.defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING))
        {
            return state.setValue(BlockStateProperties.HORIZONTAL_FACING, direction);
        }
        if (state.hasProperty(BlockStateProperties.FACING))
        {
            return state.setValue(BlockStateProperties.FACING, direction);
        }
        return state;
    }

    /** A lantern hanging from the block above it. */
    static BlockState hanging(Block lantern)
    {
        return lantern.defaultBlockState().setValue(LanternBlock.HANGING, true);
    }

    /** 1 to 4 lit candles. */
    static BlockState candles(Block candle, int count)
    {
        return candle.defaultBlockState().setValue(CandleBlock.CANDLES, count).setValue(CandleBlock.LIT, true);
    }
}
