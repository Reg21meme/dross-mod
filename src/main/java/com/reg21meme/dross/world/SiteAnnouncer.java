package com.reg21meme.dross.world;

import com.reg21meme.dross.Dross;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tells each player in chat where the portal site is whenever they join a world that has one: "Dross portal site is at
 * X, Y, Z" (plain text). The coordinates are the middle block of the frame opening's bottom row
 * ({@link PortalSite#getOpeningCenter}). The spot comes from the seed, so it's known from the start, before the
 * cathedral's land is made. A world with no site (a superflat world's own list of structures) says nothing.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class SiteAnnouncer
{
    /** The chat line, {@code "Dross portal site is at %s, %s, %s"} (X, Y, Z) in {@code en_us.json}. */
    static final String LOCATION_KEY = "dross.site.location";

    /** The chat line with the site's coordinates. Server thread. */
    static Component locationMessage(ServerLevel anyLevel)
    {
        BlockPos center = PortalSite.getOpeningCenter(anyLevel);
        return Component.translatable(LOCATION_KEY, center.getX(), center.getY(), center.getZ());
    }

    /** A player joined: if the world has a site, tell them where it is. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        ServerLevel overworld = player.server.overworld();
        if (overworld == null || !PortalSite.isPlaced(overworld))
        {
            return;
        }
        player.sendSystemMessage(locationMessage(overworld));
    }

    private SiteAnnouncer() {}
}
