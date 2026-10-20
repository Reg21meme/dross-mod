package com.reg21meme.dross.boss.huntsman.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Supplier;

/**
 * A Rimefall crop ({@link HunterCrops}: Frostroot, Snow Lichen): a normal crop on farmland, ages 0-7, that needs light to
 * grow (block light 9, like wheat) but, being hardy, never pops off in the dark. A vanilla crop breaks when it's placed
 * or updated where the light is under 8 and it can't see the sky, which would empty the hunters' farm igloos when a
 * template puts them down before the game has lit their lanterns.
 */
public class HardyCropBlock extends CropBlock
{
    private final Supplier<? extends ItemLike> seed;

    /** {@code seed}: what plants it (and what pick-block gives). */
    public HardyCropBlock(Properties properties, Supplier<? extends ItemLike> seed)
    {
        super(properties);
        this.seed = seed;
    }

    @Override
    protected ItemLike getBaseSeedId()
    {
        return this.seed.get();
    }

    /** Only its soil counts, never the light (see the class comment). */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        BlockPos below = pos.below();
        BlockState soil = level.getBlockState(below);
        if (state.getBlock() == this)
        {
            return soil.canSustainPlant(level, below, Direction.UP, this);
        }
        return this.mayPlaceOn(soil, level, below);
    }
}
