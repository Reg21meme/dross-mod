package com.reg21meme.dross.portal;

import com.reg21meme.dross.registry.ModBlocks;
import java.util.Optional;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The Dross version of vanilla's {@code PortalShape}: the same shape rules as a nether portal
 * (opening from 2x3 up to 21x21, standing upright along X or Z, corners optional),
 * but every frame block must be {@code dross:dross_portal_frame}.
 * Obsidian does not count, so a Rift Key does nothing in a normal obsidian frame.
 */
public class DrossPortalShape
{
    public static final int MIN_WIDTH = 2;
    public static final int MAX_WIDTH = 21;
    public static final int MIN_HEIGHT = 3;
    public static final int MAX_HEIGHT = 21;

    private final LevelAccessor level;
    private final Direction.Axis axis;
    private final Direction rightDir;
    private int numPortalBlocks;
    @Nullable
    private BlockPos bottomLeft;
    private int height;
    private final int width;

    /** Finds a complete, empty Dross frame around {@code pos}, trying {@code preferredAxis} first. */
    public static Optional<DrossPortalShape> findEmptyPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis preferredAxis)
    {
        return findPortalShape(level, pos, shape -> shape.isValid() && shape.numPortalBlocks == 0, preferredAxis);
    }

    /** Finds a complete, already fully lit Dross frame around {@code pos}, trying {@code preferredAxis} first. */
    public static Optional<DrossPortalShape> findLitPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis preferredAxis)
    {
        return findPortalShape(level, pos, DrossPortalShape::isComplete, preferredAxis);
    }

    /** Finds a complete Dross frame around {@code pos} whether its opening is empty, partly lit or fully lit. */
    public static Optional<DrossPortalShape> findAnyPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis preferredAxis)
    {
        return findPortalShape(level, pos, DrossPortalShape::isValid, preferredAxis);
    }

    public static Optional<DrossPortalShape> findPortalShape(LevelAccessor level, BlockPos pos, Predicate<DrossPortalShape> filter, Direction.Axis preferredAxis)
    {
        Optional<DrossPortalShape> first = Optional.of(new DrossPortalShape(level, pos, preferredAxis)).filter(filter);
        if (first.isPresent())
        {
            return first;
        }
        Direction.Axis other = preferredAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        return Optional.of(new DrossPortalShape(level, pos, other)).filter(filter);
    }

    public DrossPortalShape(LevelAccessor level, BlockPos pos, Direction.Axis axis)
    {
        this.level = level;
        this.axis = axis;
        this.rightDir = axis == Direction.Axis.X ? Direction.WEST : Direction.SOUTH;
        this.bottomLeft = this.calculateBottomLeft(pos);
        if (this.bottomLeft == null)
        {
            this.bottomLeft = pos;
            this.width = 1;
            this.height = 1;
        }
        else
        {
            this.width = this.calculateWidth();
            if (this.width > 0)
            {
                this.height = this.calculateHeight();
            }
        }
    }

    static boolean isFrame(BlockState state)
    {
        return state.is(ModBlocks.DROSS_PORTAL_FRAME.get());
    }

    static boolean isEmpty(BlockState state)
    {
        return state.isAir() || state.is(BlockTags.FIRE) || state.is(ModBlocks.DROSS_PORTAL.get());
    }

    @Nullable
    private BlockPos calculateBottomLeft(BlockPos pos)
    {
        int minY = Math.max(this.level.getMinBuildHeight(), pos.getY() - MAX_HEIGHT);
        while (pos.getY() > minY && isEmpty(this.level.getBlockState(pos.below())))
        {
            pos = pos.below();
        }
        Direction leftDir = this.rightDir.getOpposite();
        int distance = this.getDistanceUntilEdgeAboveFrame(pos, leftDir) - 1;
        return distance < 0 ? null : pos.relative(leftDir, distance);
    }

    private int calculateWidth()
    {
        int w = this.getDistanceUntilEdgeAboveFrame(this.bottomLeft, this.rightDir);
        return w >= MIN_WIDTH && w <= MAX_WIDTH ? w : 0;
    }

    private int getDistanceUntilEdgeAboveFrame(BlockPos start, Direction dir)
    {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i <= MAX_WIDTH; ++i)
        {
            cursor.set(start).move(dir, i);
            BlockState state = this.level.getBlockState(cursor);
            if (!isEmpty(state))
            {
                if (isFrame(state))
                {
                    return i;
                }
                break;
            }
            BlockState below = this.level.getBlockState(cursor.move(Direction.DOWN));
            if (!isFrame(below))
            {
                break;
            }
        }
        return 0;
    }

    private int calculateHeight()
    {
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        int h = this.getDistanceUntilTop(cursor);
        return h >= MIN_HEIGHT && h <= MAX_HEIGHT && this.hasTopFrame(cursor, h) ? h : 0;
    }

    private boolean hasTopFrame(BlockPos.MutableBlockPos cursor, int h)
    {
        for (int i = 0; i < this.width; ++i)
        {
            cursor.set(this.bottomLeft).move(Direction.UP, h).move(this.rightDir, i);
            if (!isFrame(this.level.getBlockState(cursor)))
            {
                return false;
            }
        }
        return true;
    }

    private int getDistanceUntilTop(BlockPos.MutableBlockPos cursor)
    {
        for (int i = 0; i < MAX_HEIGHT; ++i)
        {
            cursor.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, -1);
            if (!isFrame(this.level.getBlockState(cursor)))
            {
                return i;
            }
            cursor.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, this.width);
            if (!isFrame(this.level.getBlockState(cursor)))
            {
                return i;
            }
            for (int j = 0; j < this.width; ++j)
            {
                cursor.set(this.bottomLeft).move(Direction.UP, i).move(this.rightDir, j);
                BlockState state = this.level.getBlockState(cursor);
                if (!isEmpty(state))
                {
                    return i;
                }
                if (state.is(ModBlocks.DROSS_PORTAL.get()))
                {
                    ++this.numPortalBlocks;
                }
            }
        }
        return MAX_HEIGHT;
    }

    public boolean isValid()
    {
        return this.bottomLeft != null && this.width >= MIN_WIDTH && this.width <= MAX_WIDTH
                && this.height >= MIN_HEIGHT && this.height <= MAX_HEIGHT;
    }

    /** True when the frame is valid and its whole opening is already filled with Dross portal blocks. */
    public boolean isComplete()
    {
        return this.isValid() && this.numPortalBlocks == this.width * this.height;
    }

    /** Fills the opening with {@code dross:dross_portal}, lined up with the frame like vanilla. */
    public void createPortalBlocks()
    {
        BlockState portal = ModBlocks.DROSS_PORTAL.get().defaultBlockState().setValue(DrossPortalBlock.AXIS, this.axis);
        BlockPos.betweenClosed(this.bottomLeft, this.bottomLeft.relative(Direction.UP, this.height - 1).relative(this.rightDir, this.width - 1))
                .forEach(p -> this.level.setBlock(p, portal, 18));
    }

    public Direction.Axis getAxis()
    {
        return this.axis;
    }

    /** Lowest opening block on the "left" side (as seen by vanilla's rules). */
    public BlockPos getBottomLeft()
    {
        return this.bottomLeft;
    }

    /** The opening block with the lowest X, Y and Z (the corner {@code BlockPos.betweenClosed} would start at). */
    public BlockPos getMinCorner()
    {
        BlockPos far = this.bottomLeft.relative(this.rightDir, this.width - 1);
        return new BlockPos(Math.min(this.bottomLeft.getX(), far.getX()), this.bottomLeft.getY(), Math.min(this.bottomLeft.getZ(), far.getZ()));
    }

    public int getWidth()
    {
        return this.width;
    }

    public int getHeight()
    {
        return this.height;
    }
}
