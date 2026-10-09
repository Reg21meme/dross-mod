package com.reg21meme.dross.villager.hut;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LanternBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.slf4j.Logger;

import java.util.ArrayList;
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
 * Blocks are only recorded while drawing; {@link #apply} places them all at once, bottom-up, without
 * neighbour updates (so nothing pops off half-built), then fixes shapes in one pass at the end
 * (fences and panes connect, stair corners round off, grass under snow turns snowy).
 */
final class HutCanvas
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** No neighbour or shape updates while placing; the clients still get every block. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** A foundation goes at most this many blocks down looking for solid ground. */
    private static final int MAX_FOUNDATION_DEPTH = 8;

    private final ServerLevel level;
    private final BlockPos center;
    private final int centerX;
    private final int centerZ;
    private final Rotation rotation;
    private final RandomSource random;
    private final Map<BlockPos, BlockState> blocks = new LinkedHashMap<>();

    HutCanvas(ServerLevel level, BlockPos center, Direction facing, int width, int depth, long seed)
    {
        this.level = level;
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

    /** Snow on the ground: grass at y = 0 (so it turns snowy) with {@code layers} of snow on top. */
    void snowOnGround(int x, int z, int layers)
    {
        set(x, 0, z, Blocks.GRASS_BLOCK);
        set(x, 1, z, snow(layers));
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

    /** True if the world's ground at this spot (y = 0) is solid and dry (before anything is placed). */
    boolean isSolidGround(int x, int z)
    {
        BlockPos pos = toWorld(x, 0, z);
        return level.getBlockState(pos).blocksMotion() && level.getFluidState(pos).isEmpty();
    }

    /** True if the world at this spot (before anything is placed) is solid and dry. */
    boolean isSolid(int x, int y, int z)
    {
        BlockPos pos = toWorld(x, y, z);
        return level.getBlockState(pos).blocksMotion() && level.getFluidState(pos).isEmpty();
    }

    /** True if the world at this spot (before anything is placed) is a plain dirt block. */
    boolean isDirt(int x, int y, int z)
    {
        return level.getBlockState(toWorld(x, y, z)).is(Blocks.DIRT);
    }

    // ---------------------------------------------------------------- placing

    /**
     * Places every recorded block, bottom-up, then lets fences, panes, stairs, grass... fit their neighbours.
     * Anything that can't stay where the design put it (a flower on stone, a lantern with nothing to hang from)
     * disappears, and is logged as a warning so the design can be fixed.
     */
    void apply(String design)
    {
        List<Map.Entry<BlockPos, BlockState>> list = new ArrayList<>(blocks.entrySet());
        list.sort(Comparator.comparingInt(entry -> entry.getKey().getY()));
        for (Map.Entry<BlockPos, BlockState> entry : list)
        {
            level.setBlock(entry.getKey(), entry.getValue(), FLAGS);
        }
        List<String> fellOff = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : list)
        {
            BlockPos pos = entry.getKey();
            BlockState placed = level.getBlockState(pos);
            BlockState shaped = Block.updateFromNeighbourShapes(placed, level, pos);
            if (shaped != placed)
            {
                level.setBlock(pos, shaped, FLAGS);
                if (shaped.isAir() && !placed.isAir())
                {
                    fellOff.add(BuiltInRegistries.BLOCK.getKey(placed.getBlock()).getPath() + " at " + pos.toShortString());
                }
            }
        }
        if (!fellOff.isEmpty())
        {
            LOGGER.warn("Hut design {}: {} block(s) couldn't stay where they were drawn: {}", design, fellOff.size(), fellOff);
        }
    }

    // ---------------------------------------------------------------- block states (drawn as if the door faces south)

    /** Stairs you walk up while moving towards {@code facing} (the tall side is on that side). */
    static BlockState stairs(Block block, Direction facing)
    {
        return block.defaultBlockState().setValue(StairBlock.FACING, facing);
    }

    /** Upside-down stairs, for eaves, arches and counters. */
    static BlockState upsideDown(Block block, Direction facing)
    {
        return stairs(block, facing).setValue(StairBlock.HALF, Half.TOP);
    }

    static BlockState slab(Block block)
    {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM);
    }

    static BlockState topSlab(Block block)
    {
        return block.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
    }

    static BlockState log(Block block, Direction.Axis axis)
    {
        return block.defaultBlockState().setValue(RotatedPillarBlock.AXIS, axis);
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

    /** A trapdoor on the {@code facing} side of its block (open = standing up against that side). */
    static BlockState trapdoor(Block block, Direction facing, boolean top, boolean open)
    {
        return block.defaultBlockState()
                .setValue(TrapDoorBlock.FACING, facing)
                .setValue(TrapDoorBlock.HALF, top ? Half.TOP : Half.BOTTOM)
                .setValue(TrapDoorBlock.OPEN, open);
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

    /** Leaves that never decay. */
    static BlockState leaves(Block block)
    {
        return block.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
    }

    static BlockState snow(int layers)
    {
        return Blocks.SNOW.defaultBlockState().setValue(SnowLayerBlock.LAYERS, layers);
    }

    /** Sculk veins or glow lichen growing on the given sides of their block (DOWN = lying on the floor). */
    static BlockState growth(Block block, Direction... sides)
    {
        BlockState state = block.defaultBlockState();
        for (Direction side : sides)
        {
            state = state.setValue(MultifaceBlock.getFaceProperty(side), true);
        }
        return state;
    }

    /** Vines hanging on the given sides of their block. */
    static BlockState vines(Direction... sides)
    {
        BlockState state = Blocks.VINE.defaultBlockState();
        for (Direction side : sides)
        {
            state = state.setValue(VineBlock.getPropertyForFace(side), true);
        }
        return state;
    }
}
