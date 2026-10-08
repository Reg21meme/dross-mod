package com.reg21meme.dross.portal;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.advancements.Advancement;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

/**
 * What happens each time a player arrives in the Dross through the portal, in order:
 * <ol>
 *   <li>"The Dross" fades in as a big on-screen title (like the /title command), together with
 *       four falling piano notes ("dun, dun, DUN, dunnn") that only the arriving player hears.</li>
 *   <li>Once the last note has rung out, the "Entered the Dross" advancement is granted
 *       (only does anything the first time), so its fanfare doesn't clash with the notes.</li>
 * </ol>
 * The title text comes from the lang key {@code dimension.dross.dross}.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class DrossArrival
{
    // All times are in ticks after arriving (20 ticks = 1 second).
    /** Wait 1 second first, so the title isn't hidden behind the "Loading terrain" screen. */
    private static final int TITLE_TICK = 20;
    /**
     * When each note plays ("dun, dun, DUN, dunnn"). The first one starts with the title fading in.
     * The first three are 0.4 s apart; the last waits a little longer (0.5 s) for emphasis.
     */
    private static final int[] NOTE_TICKS = {TITLE_TICK, TITLE_TICK + 8, TITLE_TICK + 16, TITLE_TICK + 26};
    /**
     * Note pitches, each lower than the last: E, D, C#, then a drop to low F#.
     * Same pitch scale as a note block: 2^((clicks - 12) / 12), so 0.5 is the lowest note block note.
     */
    private static final float[] NOTE_PITCHES = {0.891F, 0.794F, 0.749F, 0.5F};
    /** Getting louder; the last note is at full volume (1.0 is the loudest Minecraft plays a sound). */
    private static final float[] NOTE_VOLUMES = {0.6F, 0.6F, 0.8F, 1.0F};
    /** Grant the advancement 2 seconds after the last note, once it has rung out. */
    private static final int ADVANCEMENT_TICK = NOTE_TICKS[NOTE_TICKS.length - 1] + 40;

    // Same timings as the /title command's defaults: 0.5 s fade in, 3.5 s on screen, 1 s fade out.
    private static final int FADE_IN = 10;
    private static final int STAY = 70;
    private static final int FADE_OUT = 20;

    private static final ResourceLocation ADVANCEMENT_ID = new ResourceLocation(Dross.MODID, "entered_the_dross");
    /** Criterion name in data/dross/advancements/entered_the_dross.json. */
    private static final String ADVANCEMENT_CRITERION = "entered";

    /** Players in the middle of their arrival sequence, and how many ticks since they arrived. */
    private static final Map<UUID, Integer> ARRIVING = new HashMap<>();

    private DrossArrival() {}

    /** Called right after a player has gone through the portal into the Dross. */
    static void start(ServerPlayer player)
    {
        ARRIVING.put(player.getUUID(), 0);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || ARRIVING.isEmpty())
        {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        Iterator<Map.Entry<UUID, Integer>> it = ARRIVING.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<UUID, Integer> entry = it.next();
            ServerPlayer player = server == null ? null : server.getPlayerList().getPlayer(entry.getKey());
            // Stop if they logged out or already left the Dross.
            if (player == null || player.level().dimension() != ModDimensions.DROSS_LEVEL)
            {
                it.remove();
                continue;
            }
            int tick = entry.getValue() + 1;
            entry.setValue(tick);

            if (tick == TITLE_TICK)
            {
                showTitle(player);
            }
            for (int i = 0; i < NOTE_TICKS.length; i++)
            {
                if (tick == NOTE_TICKS[i])
                {
                    // playNotifySound only sends the sound to this one player.
                    player.playNotifySound(SoundEvents.NOTE_BLOCK_HARP.value(), SoundSource.MASTER, NOTE_VOLUMES[i], NOTE_PITCHES[i]);
                }
            }
            if (tick >= ADVANCEMENT_TICK)
            {
                grantAdvancement(server, player);
                it.remove();
            }
        }
    }

    private static void showTitle(ServerPlayer player)
    {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(FADE_IN, STAY, FADE_OUT));
        // Clear any leftover subtitle from an earlier /title command.
        player.connection.send(new ClientboundSetSubtitleTextPacket(Component.empty()));
        player.connection.send(new ClientboundSetTitleTextPacket(
                Component.translatable("dimension.dross.dross").withStyle(ChatFormatting.GOLD)));
    }

    /** Does nothing if the player already has it. */
    private static void grantAdvancement(MinecraftServer server, ServerPlayer player)
    {
        Advancement advancement = server.getAdvancements().getAdvancement(ADVANCEMENT_ID);
        if (advancement != null)
        {
            player.getAdvancements().award(advancement, ADVANCEMENT_CRITERION);
        }
    }
}
