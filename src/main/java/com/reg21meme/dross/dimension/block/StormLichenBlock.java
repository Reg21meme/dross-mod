package com.reg21meme.dross.dimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.GlowLichenBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Storm Lichen: an electric blue lichen that glows on the cliff faces of the Gravecrags. It's a real
 * {@link GlowLichenBlock} (a {@link MultifaceBlock}), so it grows on any side of a block (several at once), the vanilla
 * {@code multiface_growth} feature can scatter it over cliffs, bone meal spreads it like glow lichen, and only shears
 * pick it up (one per side it covers; loot table). It gives off light (its block properties) and is drawn full-bright
 * by its model, with no biome tint. It burns like glow lichen.
 */
public class StormLichenBlock extends GlowLichenBlock
{
    public StormLichenBlock(Properties properties)
    {
        super(properties);
    }

    /**
     * Glow lichen checks for the glow lichen item here (holding it, you add a side instead of replacing the block);
     * this checks for Storm Lichen. Any other block replaces it, like vanilla.
     */
    @Override
    public boolean canBeReplaced(BlockState state, BlockPlaceContext context)
    {
        if (!context.getItemInHand().is(asItem()))
        {
            return true;
        }
        for (Direction direction : Direction.values())
        {
            if (!MultifaceBlock.hasFace(state, direction))
            {
                return true; // room for another side
            }
        }
        return false;
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.LICHEN_BURN;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.LICHEN_SPREAD;
    }
}
