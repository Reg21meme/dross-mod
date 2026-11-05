package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Tells each player who joins where the Dross trader is: "Dross trader's hut is at X, Y, Z" (plain text), his spot in the
 * world's one Rift Chapel ({@link RiftChapels#home}), the same place the Weathered Letter names. It's known from the seed
 * from the start (worked out in the background as the world loads; if a player joins before that's done, the line comes
 * when it is), before the chapel's land is made. Nothing is said in a world that has no chapel. Spawn-egg (and
 * {@code /summon}) traders never say anything.
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

    /** A player joined: tell them where the trader's chapel is (once it's worked out). */
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
            RiftChapels.homeAsync(overworld).thenAccept(found -> found.ifPresent(home -> server.execute(() -> {
                // (on the server thread; if it wasn't worked out yet, they may have left meanwhile)
                if (server.getPlayerList().getPlayer(player.getUUID()) == player)
                {
                    player.sendSystemMessage(locationMessage(home.chapel()));
                }
            })));
        }
        catch (RuntimeException e)
        {
            // A problem with the message must never stop a player from joining.
            LOGGER.warn("[Dross] Couldn't tell {} where the Dross trader is ({}).", player.getGameProfile().getName(), e.toString());
        }
    }

    private TraderAnnouncer() {}
}
