package com.reg21meme.dross.portal;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * The Dross portal frame ("obsidian with orange veins").
 * <ul>
 *   <li>Can't be crafted: there is no recipe.</li>
 *   <li>Can't be mined or blown up: unbreakable like bedrock, and it drops nothing.</li>
 *   <li>Can't be pushed by pistons.</li>
 *   <li>Withers and the Ender Dragon can't break it (block tags wither_immune / dragon_immune).</li>
 * </ul>
 * It deliberately does NOT override Forge's {@code isPortalFrame}, so flint and steel can't
 * make a purple nether portal inside it. Only a netherite ingot lights it (see {@link DrossPortalActivation}).
 */
public class DrossPortalFrameBlock extends Block
{
    public DrossPortalFrameBlock()
    {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BLACK)
                .instrument(NoteBlockInstrument.BASEDRUM)
                .sound(SoundType.STONE)
                .strength(-1.0F, 3600000.0F)
                .noLootTable()
                .isValidSpawn((state, level, pos, type) -> false)
                .pushReaction(PushReaction.BLOCK));
    }
}
