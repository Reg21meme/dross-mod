package com.reg21meme.dross.boss.lair;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.sites.BossSites;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Plugs the Colossus lair's own fit test ({@link LairFinder}) into the boss-site planner, during mod setup (before any
 * world loads: the plan depends on it). Mod event bus.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class LairSetup
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private LairSetup() {}

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event)
    {
        BossSites.setFinder(BossSites.COLOSSUS_LAIR, LairFinder.INSTANCE);
        LOGGER.info("Colossus lairs: the boss-site planner places them with the volcano's own fit test.");
    }
}
