package com.reg21meme.dross.dimension.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.FlowerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Wispbloom: a small flower with glowing pale-blue petals (its light level is set in its block properties, and its
 * petals are drawn full-bright by its model). A normal small flower otherwise: bees visit it, it burns, it drops
 * itself, it crafts into blue dye, and it goes into suspicious stew.
 */
public class WispbloomBlock extends FlowerBlock
{
    public WispbloomBlock(Supplier<MobEffect> stewEffect, int stewSeconds, Properties properties)
    {
        super(stewEffect, stewSeconds, properties);
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.PLANT_BURN;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.PLANT_SPREAD;
    }
}
