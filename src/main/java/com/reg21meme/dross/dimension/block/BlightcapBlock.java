package com.reg21meme.dross.dimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.PipeBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * A piece of a giant decaying blightcap mushroom (Blightwood): the Blightcap Block and the Rotting Blightcap make its
 * cap, the Blightcap Stem its stem.
 *
 * <p>They're real {@link HugeMushroomBlock}s, like vanilla's mushroom blocks: each of the six faces shows either the
 * outside of the mushroom or its pale inside (the face's block state property; the vanilla {@code huge_red_mushroom}
 * and {@code huge_brown_mushroom} features set them, so they can build giant blightcaps). The one change: both cap
 * blocks count as the same cap, so where a Blightcap Block touches a Rotting Blightcap the face between them is the
 * inside too, as it would be between two blocks of one kind (vanilla only joins a block to its own kind). The stem
 * joins only other stems.
 *
 * <p>What they drop is in their loot tables (cap: 0-2 Gloomcaps, rotting cap and stem: nothing; silk touch: the block).
 */
public class BlightcapBlock extends HugeMushroomBlock
{
    /** True for the cap blocks (they join each other), false for the stem. */
    private final boolean cap;

    public BlightcapBlock(Properties properties, boolean cap)
    {
        super(properties);
        this.cap = cap;
    }

    /** Whether a neighbour belongs to the same part of the mushroom (cap with cap, stem with stem). */
    public boolean joinsWith(BlockState neighbour)
    {
        return neighbour.getBlock() instanceof BlightcapBlock other && other.cap == cap;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        BlockGetter level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = defaultBlockState();
        for (Direction direction : Direction.values())
        {
            state = state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), !joinsWith(level.getBlockState(pos.relative(direction))));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbour, LevelAccessor level, BlockPos pos,
            BlockPos neighbourPos)
    {
        if (joinsWith(neighbour))
        {
            return state.setValue(PipeBlock.PROPERTY_BY_DIRECTION.get(direction), false);
        }
        return super.updateShape(state, direction, neighbour, level, pos, neighbourPos);
    }
}
