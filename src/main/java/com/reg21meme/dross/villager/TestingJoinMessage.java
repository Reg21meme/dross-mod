package com.reg21meme.dross.villager;

import com.reg21meme.dross.Dross;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * TESTING ONLY. Delete this whole file when the mod is ready: nothing else depends on it.
 * Tells each joining player where the Dross trader's hut is, so we can /tp there while testing.
 * He wanders, but always stays within 50 blocks of his hut.
 * If the hut is still being built when they join, they're told as soon as it's ready.
 */
// TESTING ONLY
@Mod.EventBusSubscriber(modid = Dross.MODID)
public class TestingJoinMessage
{
    /** Players who joined while the hut was still being built. */
    private static final Set<UUID> WAITING = new HashSet<>();

    // TESTING ONLY
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        TraderSpawnData data = TraderSpawnData.get(player.server.overworld());
        if (data.hasSpawned())
        {
            tellHut(player, data.getPos());
        }
        else if (TraderSpawner.isWaiting())
        {
            player.sendSystemMessage(Component.literal("[Dross test] Trader's hut is being built..."));
            WAITING.add(player.getUUID());
        }
        else
        {
            player.sendSystemMessage(Component.literal("[Dross test] Trader has NOT spawned (see the log for why)"));
        }
    }

    // TESTING ONLY
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.phase != TickEvent.Phase.END || WAITING.isEmpty() || server == null || server.getTickCount() % 20 != 0)
        {
            return;
        }
        ServerLevel overworld = server.overworld();
        TraderSpawnData data = TraderSpawnData.get(overworld);
        if (data.hasSpawned() || !TraderSpawner.isWaiting())
        {
            for (UUID id : WAITING)
            {
                ServerPlayer player = server.getPlayerList().getPlayer(id);
                if (player != null)
                {
                    if (data.hasSpawned())
                    {
                        tellHut(player, data.getPos());
                    }
                    else
                    {
                        player.sendSystemMessage(Component.literal("[Dross test] Trader has NOT spawned (see the log for why)"));
                    }
                }
            }
            WAITING.clear();
        }
    }

    private static void tellHut(ServerPlayer player, BlockPos pos)
    {
        player.sendSystemMessage(Component.literal("[Dross test] Trader's hut is at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
    }
}
