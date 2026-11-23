package com.reg21meme.dross.boss.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.GroundsShape;
import com.reg21meme.dross.dimension.ModDimensions;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * The protected grounds near this player, as the server last sent them ({@code boss.BossGroundsMessage}): their own game
 * never makes any headway on one of those blocks, like bedrock (no cracks, no block breaking and flickering back), and
 * nothing slows them. The server refuses the break anyway; this only keeps their screen honest.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, value = Dist.CLIENT)
public final class BossGroundsClient
{
    private static volatile List<GroundsShape> shapes = List.of();

    private BossGroundsClient() {}

    public static void set(List<GroundsShape> sent)
    {
        shapes = List.copyOf(sent);
    }

    /** True if this player's game should never start to break this block (the Dross only; never in creative or spectator mode). */
    public static boolean protects(Player player, BlockPos pos)
    {
        List<GroundsShape> now = shapes;
        if (now.isEmpty() || player.isCreative() || player.isSpectator()
                || !player.level().dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return false;
        }
        for (GroundsShape shape : now)
        {
            if (shape.contains(pos))
            {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreakSpeed(PlayerEvent.BreakSpeed event)
    {
        Player player = event.getEntity();
        if (player.level().isClientSide && event.getPosition().isPresent() && protects(player, event.getPosition().get()))
        {
            event.setNewSpeed(0.0F);
        }
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        shapes = List.of();
    }
}
