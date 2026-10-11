package com.reg21meme.dross.dimension.worldgen;

import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.grower.AbstractMegaTreeGrower;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * What a Silverwood Sapling grows into.
 * <ul>
 *   <li>One sapling on its own: a common silverwood, or now and then a tall one ({@link #TALL_CHANCE}).</li>
 *   <li>Four saplings in a 2x2 square: a great silverwood with a 2x2 trunk (like dark oak or giant spruce).</li>
 * </ul>
 */
public class SilverwoodTreeGrower extends AbstractMegaTreeGrower
{
    /** Chance (0 to 1) that a single sapling grows a tall silverwood instead of a common one. */
    public static final float TALL_CHANCE = 0.3F;

    @Override
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredFeature(RandomSource random, boolean hasFlowers)
    {
        return random.nextFloat() < TALL_CHANCE ? DrossTreeFeatures.TALL_SILVERWOOD : DrossTreeFeatures.SILVERWOOD;
    }

    @Override
    protected ResourceKey<ConfiguredFeature<?, ?>> getConfiguredMegaFeature(RandomSource random)
    {
        return DrossTreeFeatures.GREAT_SILVERWOOD;
    }
}
