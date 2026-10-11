package com.reg21meme.dross.dimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Stormvine: a glowing electric blue vine that hangs on silverwood trees (decoration only).
 *
 * <p>It's a real {@link VineBlock}, so it attaches and hangs exactly like vanilla vines, you can climb it
 * ({@code #minecraft:climbable}), and code that clears vines with their tree (the world site's tree felling) takes it
 * too. Unlike vanilla vines it <b>never spreads or grows</b>: its block properties have no random ticks, and
 * {@link #randomTick} does nothing in case anything ticks it anyway. It gives off light (set in its block
 * properties) and its model is drawn full-bright, so it glows in the dark. Only shears pick it up (loot table).
 */
public class StormvineBlock extends VineBlock
{
    public StormvineBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public boolean isRandomlyTicking(BlockState state)
    {
        return false;
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        // Never spreads or grows (vanilla vines grow here).
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.VINE_BURN;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.VINE_SPREAD;
    }
}
