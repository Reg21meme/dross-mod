package com.reg21meme.dross;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModCreativeTabs;
import com.reg21meme.dross.registry.ModEnchantments;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.registry.ModLootModifiers;
import com.reg21meme.dross.registry.ModParticles;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

// The value here must match the mod_id in gradle.properties (which fills in META-INF/mods.toml)
@Mod(Dross.MODID)
public class Dross
{
    public static final String MODID = "dross";
    private static final Logger LOGGER = LogUtils.getLogger();

    public Dross(FMLJavaModLoadingContext context)
    {
        IEventBus modEventBus = context.getModEventBus();

        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModEntities.ENTITY_TYPES.register(modEventBus);
        ModParticles.PARTICLE_TYPES.register(modEventBus);
        ModCreativeTabs.CREATIVE_TABS.register(modEventBus);
        ModEnchantments.ENCHANTMENTS.register(modEventBus);
        ModLootModifiers.LOOT_MODIFIERS.register(modEventBus);

        LOGGER.info("Dross is loading");
    }
}
