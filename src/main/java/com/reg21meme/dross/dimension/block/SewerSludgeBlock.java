package com.reg21meme.dross.dimension.block;

import net.minecraft.world.level.block.MudBlock;

/**
 * Sewer Sludge: the murky brownish-green muck lying in patches on the sewers' floors (and on the Rat King's battlefield).
 * It works like mud: you sink a little way into it (its top is 2 pixels lower than a full block, so it still counts as a
 * full block for standing things on it, and mobs can spawn on it), and it slows anything walking through it, though less
 * than soul sand ({@code TerrainBlocks.SEWER_SLUDGE_SPEED}). A shovel digs it fastest; it drops itself.
 */
public class SewerSludgeBlock extends MudBlock
{
    public SewerSludgeBlock(Properties properties)
    {
        super(properties);
    }
}
