package com.reg21meme.dross.dimension.block;

import com.reg21meme.dross.registry.ModBlocks;

/**
 * Silvermoss: the Silverwood Forest's floor, silver-blue moss over the ground.
 *
 * <ul>
 *   <li>It's in {@code #minecraft:dirt}, so saplings, flowers and ferns grow on it.</li>
 *   <li>Like grass: a hoe tills it into farmland, a shovel flattens it into a dirt path.</li>
 *   <li>Bone meal on it sprouts Glimmer Ferns, and now and then a Wispbloom, on the silvermoss around it (like bone
 *       meal on grass sprouts grass and flowers).</li>
 *   <li>It drops itself (mine it with a shovel or a hoe).</li>
 * </ul>
 * The tilling, flattening and bone meal work the same for the other biomes' ground covers: see
 * {@link SproutingSoilBlock}.
 */
public class SilvermossBlock extends SproutingSoilBlock
{
    /** 1 in this many sprouted plants is a Wispbloom; the rest are Glimmer Ferns. */
    private static final int WISPBLOOM_ONE_IN = 8;

    public SilvermossBlock(Properties properties)
    {
        super(properties, true, ModBlocks.GLIMMER_FERN, ModBlocks.WISPBLOOM, WISPBLOOM_ONE_IN);
    }
}
