package com.reg21meme.dross.portal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.PushReaction;

/**
 * The lit orange Dross portal. Minimal starting version; owned by portal-builder.
 */
public class DrossPortalBlock extends Block
{
    public DrossPortalBlock()
    {
        super(BlockBehaviour.Properties.of()
                .noCollission()
                .strength(-1.0F, 3600000.0F)
                .lightLevel(state -> 11)
                .noLootTable()
                .pushReaction(PushReaction.BLOCK));
    }
}
