package com.reg21meme.dross.villager;

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
 * Tells players in chat where the Dross trader's hut is: "Dross trader's hut is at X, Y, Z" (plain text). The
 * coordinates are his spot in the hut ({@link TraderSpawnData#getPos()}), the same position the Weathered Letter uses.
 * <ul>
 *   <li><b>When he spawns:</b> everyone online is told once, right after the real once-per-world trader is spawned and
 *       saved (in a village's Rift Chapel or the emergency chapel near spawn). {@link TraderSpawner} calls
 *       {@link #announceSpawned}. Spawn-egg traders and showcase traders never do.</li>
 *   <li><b>On joining:</b> a player who joins a world where he has already spawned is told. Old worlds with his old
 *       netherite hut count too. Before he has spawned nothing is said on joining: the message above covers it once
 *       he does.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class TraderAnnouncer
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The chat line, {@code "Dross trader's hut is at %s, %s, %s"} (X, Y, Z) in {@code en_us.json}. */
    static final String LOCATION_KEY = "dross.trader.location";

    /** The chat line with the coordinates of his spot in the hut. Server thread. */
    static Component locationMessage(ServerLevel overworld)
    {
        BlockPos hut = TraderSpawnData.get(overworld).getPos();
        return Component.translatable(LOCATION_KEY, hut.getX(), hut.getY(), hut.getZ());
    }

    /**
     * Tells everyone online where his hut is. Called once, right after the trader is spawned and saved. A problem here
     * is only logged: it must never undo or repeat the spawn.
     */
    static void announceSpawned(ServerLevel overworld)
    {
        try
        {
            // Also writes the line to the server log.
            overworld.getServer().getPlayerList().broadcastSystemMessage(locationMessage(overworld), false);
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("[Dross] Couldn't tell the players where the Dross trader's hut is ({}).", e.toString());
        }
    }

    /** A player joined: if the trader has already spawned, tell them where his hut is. */
    @SubscribeEvent
    public static void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        try
        {
            ServerLevel overworld = player.server.overworld();
            if (overworld == null || !TraderSpawnData.get(overworld).hasSpawned())
            {
                return; // not spawned yet: everyone is told when he is
            }
            player.sendSystemMessage(locationMessage(overworld));
        }
        catch (RuntimeException e)
        {
            // A problem with the message must never stop a player from joining.
            LOGGER.warn("[Dross] Couldn't tell {} where the Dross trader's hut is ({}).",
                    player.getGameProfile().getName(), e.toString());
        }
    }

    private TraderAnnouncer() {}
}
