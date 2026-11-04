package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * One warning when a world starts with Generate Structures off: world generation then makes no structures (like
 * Minecraft's strongholds and villages), so such a world has no Rift Chapels (and so no trader) and no Fallen Cathedral.
 * Nothing else is done about it.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RiftChapelEvents
{
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!event.getServer().getWorldData().worldGenOptions().generateStructures())
        {
            LOGGER.warn("[Dross] The Dross's Rift Chapels, Fallen Cathedral and hub aren't generated: Generate Structures is off in this world, "
                    + "so it has no Dross trader, no portal site and no exit portal in the Dross.");
        }
    }

    private RiftChapelEvents() {}
}
