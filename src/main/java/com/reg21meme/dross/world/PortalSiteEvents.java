package com.reg21meme.dross.world;

import com.reg21meme.dross.Dross;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Builds the portal site frame when a world has finished loading (only the first time; it's remembered after that). */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class PortalSiteEvents
{
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld != null)
        {
            PortalSiteBuilder.ensurePlaced(overworld);
        }
    }

    private PortalSiteEvents() {}
}
