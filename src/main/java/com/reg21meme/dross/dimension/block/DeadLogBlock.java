package com.reg21meme.dross.dimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The trunk of a dead tree: the Charred Log of the Cinder Wastes and the Frostbitten Log of Rimefall. Placed along an
 * axis like any log, in {@code #minecraft:logs} (so leaves count it as a log), but there's nothing to strip and no
 * planks to make from it.
 *
 * <p>How easily it burns is given when it's made ({@link Flammability}): 0 and 0 never burn (the Charred Log, already
 * burnt out, lies next to lava pools); the Frostbitten Log burns slowly.
 */
public class DeadLogBlock extends RotatedPillarBlock
{
    private final int spread;
    private final int burn;

    /** {@code spread} and {@code burn} as in {@link Flammability}; both 0 for a log that never burns. */
    public DeadLogBlock(Properties properties, int spread, int burn)
    {
        super(properties);
        this.spread = spread;
        this.burn = burn;
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return burn > 0;
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
