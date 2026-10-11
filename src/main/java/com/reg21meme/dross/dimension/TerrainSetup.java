package com.reg21meme.dross.dimension;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.world.level.block.ComposterBlock;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/**
 * One-time setup for the Dross's terrain blocks when the game starts: which of them go in a composter, and how likely
 * each one is to raise its level (vanilla's numbers for the matching vanilla plant).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class TerrainSetup
{
    /** Like oak leaves and saplings. */
    private static final float COMPOST_LEAVES = 0.3F;
    /** Like vines. */
    private static final float COMPOST_VINE = 0.5F;
    /** Like ferns and flowers, and Silvermoss like a moss block. */
    private static final float COMPOST_PLANT = 0.65F;
    /** Like grass and dead bushes' dry stalks (vanilla grass: 0.3). */
    private static final float COMPOST_GRASS = 0.3F;
    /** Like glow lichen. */
    private static final float COMPOST_LICHEN = 0.5F;
    /** Like vanilla mushroom blocks and stems. */
    private static final float COMPOST_MUSHROOM_BLOCK = 0.85F;

    private TerrainSetup() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event)
    {
        // The composter's list is a plain map shared by all mods, so it's filled in on the main thread.
        event.enqueueWork(() -> {
            ComposterBlock.COMPOSTABLES.put(ModItems.SILVERWOOD_LEAVES.get(), COMPOST_LEAVES);
            ComposterBlock.COMPOSTABLES.put(ModItems.SILVERWOOD_SAPLING.get(), COMPOST_LEAVES);
            ComposterBlock.COMPOSTABLES.put(ModItems.STORMVINE.get(), COMPOST_VINE);
            ComposterBlock.COMPOSTABLES.put(ModItems.GLIMMER_FERN.get(), COMPOST_PLANT);
            ComposterBlock.COMPOSTABLES.put(ModItems.WISPBLOOM.get(), COMPOST_PLANT);
            ComposterBlock.COMPOSTABLES.put(ModItems.SILVERMOSS.get(), COMPOST_PLANT);
            // The other biomes' plants and the giant blightcap's blocks
            ComposterBlock.COMPOSTABLES.put(ModItems.EMBERWEED.get(), COMPOST_GRASS);
            ComposterBlock.COMPOSTABLES.put(ModItems.GLOOMCAP.get(), COMPOST_PLANT);
            ComposterBlock.COMPOSTABLES.put(ModItems.SPOREPOD.get(), COMPOST_PLANT);
            ComposterBlock.COMPOSTABLES.put(ModItems.BLIGHTCAP_BLOCK.get(), COMPOST_MUSHROOM_BLOCK);
            ComposterBlock.COMPOSTABLES.put(ModItems.ROTTING_BLIGHTCAP.get(), COMPOST_MUSHROOM_BLOCK);
            ComposterBlock.COMPOSTABLES.put(ModItems.BLIGHTCAP_STEM.get(), COMPOST_MUSHROOM_BLOCK);
            ComposterBlock.COMPOSTABLES.put(ModItems.FROSTBLOOM.get(), COMPOST_PLANT);
            ComposterBlock.COMPOSTABLES.put(ModItems.SCAR_ROOTS.get(), COMPOST_PLANT);
            ComposterBlock.COMPOSTABLES.put(ModItems.SOULWICK.get(), COMPOST_PLANT);
            ComposterBlock.COMPOSTABLES.put(ModItems.STORM_LICHEN.get(), COMPOST_LICHEN);
            ComposterBlock.COMPOSTABLES.put(ModItems.CRAG_THISTLE.get(), COMPOST_PLANT);
        });
    }
}
