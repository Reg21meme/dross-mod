package com.reg21meme.dross.dimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Ash Layer: thin drifts of volcanic ash in the Cinder Wastes, 1 to 8 layers deep, exactly like snow layers (it's a
 * real {@link SnowLayerBlock}): it stacks when you place more on it, you sink into deep drifts, it needs solid ground
 * under it, and a single layer is replaced by whatever you place there.
 *
 * <p>Unlike snow it <b>never melts</b>: snow melts in its random tick when the block light is above 11, so this block
 * takes no random ticks (its block properties don't ask for them) and {@link #randomTick} does nothing in case anything
 * ticks it anyway. It never burns. It drops one Ash Layer per layer (loot table).
 */
public class AshLayerBlock extends SnowLayerBlock
{
    public AshLayerBlock(Properties properties)
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
        // Never melts (vanilla snow layers melt here next to bright light).
    }
}
