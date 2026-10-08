package com.reg21meme.dross.villager;

import com.reg21meme.dross.Dross;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Every second, gives each Dross Compass in a player's inventory its target (the portal site),
 * if it doesn't have it yet. This is how the creative-tab compass gets aimed.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public class DrossCompassTracker
{
    private static final int CHECK_INTERVAL = 20;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player))
        {
            return;
        }
        if (player.tickCount % CHECK_INTERVAL != 0 || !(player.level() instanceof ServerLevel level))
        {
            return;
        }
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
        {
            ItemStack stack = player.getInventory().getItem(i);
            if (DrossCompass.isDrossCompass(stack))
            {
                DrossCompass.applyTarget(stack, level);
            }
        }
    }
}
