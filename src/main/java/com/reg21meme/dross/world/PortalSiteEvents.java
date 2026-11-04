package com.reg21meme.dross.world;

import com.reg21meme.dross.Dross;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * When the server's Overworld is made (before its spawn area is generated), its portal site's spot starts being worked out
 * from the seed on a background thread ({@link SiteFinder#register}), so it's ready long before anything asks for it.
 * Nothing else of the site happens at world start: no chunk is loaded or held for it, and nothing is built (the cathedral
 * comes with its land, {@link PortalSiteStructure}; the tidy-up waits for that land, {@link SiteTidy}).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class PortalSiteEvents
{
    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD)
        {
            SiteFinder.register(level);
        }
    }

    private PortalSiteEvents() {}
}
