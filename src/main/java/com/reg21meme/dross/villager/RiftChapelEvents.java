package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * When the server's Overworld is made (before its spawn area is generated), the Dross trader's home starts being worked
 * out from the seed on a background thread ({@link RiftChapels#register}: which village, and where his chapel goes), so
 * it's ready before world generation reaches it. Nothing of him is built at world start: the chapel comes with its land.
 * <p>
 * And one warning when a world starts with Generate Structures off: world generation then makes no structures (like
 * Minecraft's strongholds and villages), so such a world has no Rift Chapel (and so no trader), no Fallen Cathedral and no
 * hub. Nothing else is done about it.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RiftChapelEvents
{
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD)
        {
            RiftChapels.register(level);
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!event.getServer().getWorldData().worldGenOptions().generateStructures())
        {
            LOGGER.warn("[Dross] The Dross's Rift Chapel, Fallen Cathedral and hub aren't generated: Generate Structures is off in this world, "
                    + "so it has no Dross trader, no portal site and no exit portal in the Dross.");
        }
    }

    private RiftChapelEvents() {}
}
