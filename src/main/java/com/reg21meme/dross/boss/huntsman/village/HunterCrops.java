package com.reg21meme.dross.boss.huntsman.village;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * Rimefall's crops, grown by the hunter villages (the user, October 20, 2026: "make it a custom plant for the biome ...
 * a berry farm outside or then another crop with its own igloo"; Snow Lichen the user's pick over kale; "don't worry
 * about food and stuff, we will do that later": none of them can be eaten yet), registered in {@code registry/ModBlocks}
 * and {@code ModItems}:
 * <ul>
 *   <li><b>Frostroot</b> ({@code dross:frostroots}, the item {@code dross:frostroot} plants it): pale ice-blue roots
 *       under spiky frosted leaves, a crop on farmland ({@link HardyCropBlock}).</li>
 *   <li><b>Snow Lichen</b> ({@code dross:snow_lichen}, planted with its own clumps): a pale, branching crust that grows
 *       into tufts like coral, a crop on farmland; later the moose's feed.</li>
 *   <li><b>Rimeberry Bush</b> ({@code dross:rimeberry_bush}, planted with Rimeberries): two blocks tall, icy-blue
 *       berries on both halves ({@link RimeberryBushBlock}); the Rimehorns' food.</li>
 *   <li><b>Frostroot Crate</b> and <b>Rimeberry Basket</b>: nine of them packed into a block and back (the user: "a
 *       compacted root, like a root barrel ... which is just a block, and use that for decorations ... alongside the
 *       meat, as well as a berry [one]").</li>
 * </ul>
 * Textures: {@code tools/huntsman/crops.py}. Frostroot and Snow Lichen compost like a carrot, Rimeberries like sweet
 * berries.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HunterCrops
{
    /** How likely each raises a composter's level (vanilla's carrot is 0.65, sweet berries 0.3). */
    public static final float COMPOST_ROOT = 0.65F;
    public static final float COMPOST_LICHEN = 0.65F;
    public static final float COMPOST_BERRIES = 0.3F;
    /** How long the crate and the basket take to break by hand: about a barrel's and a hay bale's. */
    public static final float CRATE_STRENGTH = 1.5F;
    public static final float BASKET_STRENGTH = 0.8F;

    private HunterCrops() {}

    public static Block frostroots()
    {
        return new HardyCropBlock(BlockBehaviour.Properties.copy(Blocks.CARROTS).mapColor(MapColor.ICE),
                () -> ModItems.FROSTROOT.get());
    }

    public static Block snowLichen()
    {
        return new HardyCropBlock(BlockBehaviour.Properties.copy(Blocks.CARROTS).mapColor(MapColor.SNOW),
                () -> ModItems.SNOW_LICHEN.get());
    }

    public static Block rimeberryBush()
    {
        return new RimeberryBushBlock(BlockBehaviour.Properties.copy(Blocks.SWEET_BERRY_BUSH).mapColor(MapColor.ICE),
                () -> ModItems.RIMEBERRIES.get());
    }

    /** A spruce crate heaped with Frostroots. */
    public static Block frostrootCrate()
    {
        return new Block(BlockBehaviour.Properties.of().mapColor(MapColor.PODZOL).strength(CRATE_STRENGTH)
                .sound(SoundType.WOOD));
    }

    /** A woven basket heaped with Rimeberries. */
    public static Block rimeberryBasket()
    {
        return new Block(BlockBehaviour.Properties.of().mapColor(MapColor.COLOR_LIGHT_BLUE).strength(BASKET_STRENGTH)
                .sound(SoundType.BAMBOO_WOOD));
    }

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event)
    {
        event.enqueueWork(() ->
        {
            ComposterBlock.COMPOSTABLES.put(ModItems.FROSTROOT.get(), COMPOST_ROOT);
            ComposterBlock.COMPOSTABLES.put(ModItems.SNOW_LICHEN.get(), COMPOST_LICHEN);
            ComposterBlock.COMPOSTABLES.put(ModItems.RIMEBERRIES.get(), COMPOST_BERRIES);
        });
    }
}
