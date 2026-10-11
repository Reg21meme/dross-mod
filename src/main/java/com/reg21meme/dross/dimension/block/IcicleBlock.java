package com.reg21meme.dross.dimension.block;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Icicle: a decorative spike of ice hanging in Rimefall. It only hangs under a block whose bottom face is solid all
 * over (a full face, as {@code isFaceSturdy} says), and breaks the moment that block goes. It's see-through
 * (translucent), you walk through it, it never hurts and never falls, and it never melts. It drops nothing unless
 * mined with Silk Touch (loot table), like ice and glass.
 */
public class IcicleBlock extends Block
{
    /** The outline: the spike hangs from the top of the block, 6 pixels wide at its root, 13 long. */
    private static final VoxelShape SHAPE = Block.box(5.0D, 3.0D, 5.0D, 11.0D, 16.0D, 11.0D);

    public IcicleBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        BlockPos above = pos.above();
        return level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN);
    }

    @Override
    @Nullable
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockState state = defaultBlockState();
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    /** The block above changed: if it can't hold the icicle any more, the icicle breaks (and drops nothing). */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos,
            BlockPos neighbourPos)
    {
        if (direction == Direction.UP && !state.canSurvive(level, pos))
        {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }
}
