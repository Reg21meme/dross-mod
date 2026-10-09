package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/** Builds the portal site (castle) when a world has finished loading (only the first time; it's remembered after that). */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class PortalSiteEvents
{
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        ServerLevel overworld = event.getServer().overworld();
        if (overworld == null)
        {
            return;
        }
        PortalSiteData data = PortalSiteData.get(overworld);
        if (data.isPlaced())
        {
            LOGGER.info("Dross portal site: already placed in this world ({}); frame corner {} axis {}",
                    data.getKind(), data.getFramePos().toShortString(), data.getAxis());
            return;
        }
        PortalSiteBuilder.ensurePlaced(overworld);
    }

    private PortalSiteEvents() {}
}
