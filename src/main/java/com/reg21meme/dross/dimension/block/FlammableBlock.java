package com.reg21meme.dross.dimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/** A plain full block that can burn (Silverwood Planks: like oak planks, see {@link Flammability}). */
public class FlammableBlock extends Block
{
    private final int spread;
    private final int burn;

    public FlammableBlock(Properties properties, int spread, int burn)
    {
        super(properties);
        this.spread = spread;
        this.burn = burn;
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return burn;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return spread;
    }
}
