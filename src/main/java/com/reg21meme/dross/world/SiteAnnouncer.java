package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Tells players in chat where the portal site is: "Dross portal site is at X, Y, Z" (plain text). The coordinates
 * are the middle block of the frame opening's bottom row ({@link PortalSite#getOpeningCenter}).
 * <ul>
 *   <li><b>When the site is placed:</b> everyone online is told once, right after it's built, whichever way it was
 *       built (the Fallen Cathedral, the small fallback shrine or a castle template; in the background a little while
 *       after a new world opens, or on the spot by {@link PortalSiteBuilder#ensurePlaced} when someone leaves the
 *       Dross before that). {@link PortalSitePlacer} calls {@link #announcePlaced}.</li>
 *   <li><b>On joining:</b> a player who joins a world where the site is already placed is told. Old worlds count as
 *       placed (their bare frame or small shrine). Before the site is placed nothing is said on joining: the message
 *       above covers it once it's built.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class SiteAnnouncer
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The chat line, {@code "Dross portal site is at %s, %s, %s"} (X, Y, Z) in {@code en_us.json}. */
    static final String LOCATION_KEY = "dross.site.location";

    /** The chat line with the site's coordinates. Server thread. */
    static Component locationMessage(ServerLevel anyLevel)
    {
        BlockPos center = PortalSite.getOpeningCenter(anyLevel);
        return Component.translatable(LOCATION_KEY, center.getX(), center.getY(), center.getZ());
    }

    /**
     * Tells everyone online where the site is. Called once, right after the site is placed and saved. A problem here
     * is only logged: it must never undo or repeat the placing.
     */
    static void announcePlaced(ServerLevel overworld)
    {
        try
        {
            // Also writes the line to the server log.
            overworld.getServer().getPlayerList().broadcastSystemMessage(locationMessage(overworld), false);
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("Dross portal site: couldn't tell the players where the site is ({}).", e.toString());
        }
    }

    /** A player joined: if the site is already placed, tell them where it is. */
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
            return; // not built yet: everyone is told when it is
        }
        player.sendSystemMessage(locationMessage(overworld));
    }

    private SiteAnnouncer() {}
}
