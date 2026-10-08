package com.reg21meme.dross.portal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The Dross portal frame: can't be crafted, mined, blown up or pushed.
 * Minimal starting version; owned by portal-builder.
 */
public class DrossPortalFrameBlock extends Block
{
    public DrossPortalFrameBlock()
    {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .pushReaction(PushReaction.BLOCK));
    }
}
