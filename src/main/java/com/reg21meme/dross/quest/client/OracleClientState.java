package com.reg21meme.dross.quest.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.quest.oracle.OracleMarker;
import com.reg21meme.dross.quest.oracle.OracleQuests;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * CLIENT ONLY. What this player's game knows of their quests, as the server last sent it
 * ({@code quest.oracle.OracleSyncMessage}): the markers to draw over the Blind Oracle and the Dross Trader (the villager
 * area's {@code QuestMarkerRenderer}), which locked
 * lands are open and which of their barriers this player has seen fall (the dimension area's ward visuals). Forgotten on
 * leaving a world.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, value = Dist.CLIENT)
public final class OracleClientState
{
    private static volatile OracleMarker marker = OracleMarker.NONE;
    private static volatile OracleMarker traderMarker = OracleMarker.NONE;
    private static volatile int openLands;
    private static volatile int fallenLands;

    private OracleClientState() {}

    /** The marker this player sees over the Blind Oracle (NONE until the server says otherwise). */
    public static OracleMarker marker()
    {
        return marker;
    }

    /** The marker this player sees over the Dross Trader (NONE until the server says otherwise). */
    public static OracleMarker traderMarker()
    {
        return traderMarker;
    }

    /** True in creative or spectator mode: nothing is locked, and no barrier is shown. */
    private static boolean exempt()
    {
        LocalPlayer player = Minecraft.getInstance().player;
        return player != null && (player.isCreative() || player.isSpectator());
    }

    /**
     * True if this land is open for this player: a land no quest locks always is, and so is every land in creative or
     * spectator mode; a locked land once they've handed in the quest that opens it.
     */
    public static boolean isLandOpen(ResourceKey<Biome> biome)
    {
        int bit = OracleQuests.lockableLands().indexOf(biome);
        return bit < 0 || exempt() || (openLands & (1 << bit)) != 0;
    }

    /**
     * True if this land's edge still shows its barrier's curtain to this player: it's locked, or it's open but they
     * haven't come to it since (its barrier falls in front of them when they do: {@code dimension/ward/client/LandWardFall}).
     * Never in creative or spectator mode.
     */
    public static boolean showsCurtain(ResourceKey<Biome> biome)
    {
        int bit = OracleQuests.lockableLands().indexOf(biome);
        if (bit < 0 || exempt())
        {
            return false;
        }
        return (openLands & (1 << bit)) == 0 || (fallenLands & (1 << bit)) == 0;
    }

    /** True if any land still shows a curtain to this player (if not, there's no edge to look for). */
    public static boolean anyCurtain()
    {
        for (ResourceKey<Biome> land : OracleQuests.lockableLands())
        {
            if (showsCurtain(land))
            {
                return true;
            }
        }
        return false;
    }

    /** This land's barrier (by its bit) has just fallen in front of them (the server has saved it too). */
    public static void markFallen(int bit)
    {
        if (bit >= 0 && bit < 31)
        {
            fallenLands |= 1 << bit;
        }
    }

    /** From the server's message. */
    public static void set(OracleMarker newMarker, OracleMarker newTraderMarker, int newOpenLands, int newFallenLands)
    {
        marker = newMarker;
        traderMarker = newTraderMarker;
        openLands = newOpenLands;
        fallenLands = newFallenLands;
    }

    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event)
    {
        marker = OracleMarker.NONE;
        traderMarker = OracleMarker.NONE;
        openLands = 0;
        fallenLands = 0;
    }
}
