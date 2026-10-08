package com.reg21meme.dross.villager;

import com.reg21meme.dross.Dross;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * TESTING ONLY. Delete this whole file when the mod is ready: nothing else depends on it.
 * Tells each joining player where the Dross trader is, so we can /tp to him while testing.
 */
// TESTING ONLY
@Mod.EventBusSubscriber(modid = Dross.MODID)
public class TestingJoinMessage
{
    // TESTING ONLY
    @SubscribeEvent
    public static void onPlayerJoin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        ServerLevel overworld = player.server.overworld();
        TraderSpawnData data = TraderSpawnData.get(overworld);
        if (data.hasSpawned())
        {
            BlockPos pos = data.getPos();
            player.sendSystemMessage(Component.literal("[Dross test] Trader is at " + pos.getX() + ", " + pos.getY() + ", " + pos.getZ()));
        }
        else
        {
            player.sendSystemMessage(Component.literal("[Dross test] Trader has NOT spawned (no plains/desert found near spawn?)"));
        }
    }
}
