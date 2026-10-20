package com.reg21meme.dross.boss.huntsman.village;

import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;

/**
 * The hunters' meat (the user's request, October 19, 2026: "a meat block where it's just the same crafting recipe for
 * any meat, chicken etc., just goes into a generic meat block, and they have it over a center fire"), registered in
 * {@code registry/ModBlocks}:
 * <ul>
 *   <li><b>Meat Block</b> ({@code dross:meat_block}): a joint of raw meat with a bone through it. Nine of any raw meat
 *       make one (the item tag {@code #dross:raw_meats}: beef, porkchop, chicken, mutton, rabbit); it hangs on the
 *       hunter villages' drying racks.</li>
 *   <li><b>Roasted Meat Block</b> ({@code dross:roasted_meat_block}): the same joint roasted. Nine of any cooked meat
 *       make one ({@code #dross:cooked_meats}), or a Meat Block smelted, smoked or cooked on a campfire; one turns on
 *       the spit over every courtyard's fire.</li>
 * </ul>
 * Both stand like a log, the bone along the way they were placed ({@link RotatedPillarBlock}), are soft and squishy (a
 * hoe mines them fastest: {@code #minecraft:mineable/hoe}) and drop themselves. There's no recipe back to meat: they're
 * generic (which meat went in isn't kept). Textures: {@code tools/huntsman/meat_blocks.py}.
 */
public final class MeatBlocks
{
    /** How long they take to break by hand, and how they stand up to explosions: about a hay bale's. */
    public static final float STRENGTH = 1.0F;

    private MeatBlocks() {}

    /** The Meat Block: raw red meat marbled with fat. */
    public static Block meat()
    {
        return new RotatedPillarBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_RED)
                .strength(STRENGTH)
                .sound(SoundType.WART_BLOCK));
    }

    /** The Roasted Meat Block: a browned, glistening crust. */
    public static Block roasted()
    {
        return new RotatedPillarBlock(BlockBehaviour.Properties.of()
                .mapColor(MapColor.COLOR_BROWN)
                .strength(STRENGTH)
                .sound(SoundType.WART_BLOCK));
    }
}
