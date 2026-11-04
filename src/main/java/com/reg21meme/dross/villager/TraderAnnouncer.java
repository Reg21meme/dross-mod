package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Tells each player who joins where the nearest Dross trader is: "Dross trader's hut is at X, Y, Z" (plain text), his
 * spot in the nearest Rift Chapel ({@link RiftChapels#nearest} within {@link RiftChapels#SEARCH_RADIUS} blocks: of them
 * in the Overworld, of the Overworld's place for them in the Nether, of the world spawn elsewhere), the same place the
 * Weathered Letter names for someone there. The search runs off the server thread, and the line comes a moment after
 * joining. Nothing is said if there's no chapel that near (or the world makes none). Spawn-egg (and {@code /summon})
 * traders never say anything.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class TraderAnnouncer
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The chat line, {@code "Dross trader's hut is at %s, %s, %s"} (X, Y, Z) in {@code en_us.json}. */
    static final String LOCATION_KEY = "dross.trader.location";

    /** The chat line with the coordinates of the trader's spot in this chapel. */
    static Component locationMessage(RiftChapels.Chapel chapel)
    {
        BlockPos spot = chapel.traderSpot();
        return Component.translatable(LOCATION_KEY, spot.getX(), spot.getY(), spot.getZ());
    }

    /** A player joined: look for the nearest chapel to them (off the server thread), then tell them where it is. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        try
        {
            MinecraftServer server = player.server;
            ServerLevel overworld = server.overworld();
            if (overworld == null || !RiftChapels.available(overworld))
            {
                return;
            }
            BlockPos from = RiftChapels.searchOrigin(overworld, player.level(), player.blockPosition());
            RiftChapels.nearestAsync(overworld, from, RiftChapels.SEARCH_RADIUS).thenAccept(found -> found.ifPresent(chapel -> server.execute(() -> {
                // (back on the server thread; they may have left meanwhile)
                if (server.getPlayerList().getPlayer(player.getUUID()) == player)
                {
                    player.sendSystemMessage(locationMessage(chapel));
                }
            })));
        }
        catch (RuntimeException e)
        {
            // A problem with the message must never stop a player from joining.
            LOGGER.warn("[Dross] Couldn't tell {} where the nearest Dross trader is ({}).", player.getGameProfile().getName(), e.toString());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        RiftChapels.forgetRequests();
    }

    private TraderAnnouncer() {}
}
