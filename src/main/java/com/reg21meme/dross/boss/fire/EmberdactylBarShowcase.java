package com.reg21meme.dross.boss.fire;

import com.reg21meme.dross.Dross;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Emberdactyl's boss bar showcase ({@code /dross showcase bossbars emberdactyl}; world-builder's {@code DrossCommand}
 * wires the command): shows its bar (the user's pick, the Winged Volcano: {@code EmberdactylBarArt}, drawn by
 * {@code tools/fire/bossbars.py}; the client's {@code DrossBossBars} draws it) to the player who ran it, the adult's look
 * ("The Sky That Burns") over its young reborn's ("The Ember That Remains"), for {@link #SHOWCASE_SECONDS} seconds, the
 * bars draining away and filling up again so the cut-off at every health shows. In the fight only one shows at a time:
 * the adult's, then the young's. Showing them again replaces the last.
 * <p>
 * Each bar's name is the translation key {@link #KEY_PREFIX} + the look ({@link #LOOKS}), the same as the fight's bars:
 * that's how the client knows what to draw.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class EmberdactylBarShowcase
{
    /** The command that shows the bars. */
    public static final String COMMAND = "/dross showcase bossbars emberdactyl";
    /** The Emberdactyl's bars' names: this and the look ("adult" or "young"). */
    public static final String KEY_PREFIX = "boss.dross.emberdactyl.bar.";
    /** The looks the showcase shows, top to bottom: the adult's, then its young reborn's. */
    public static final List<String> LOOKS = List.of("adult", "young");
    /** The bars stay up this long (seconds). */
    public static final int SHOWCASE_SECONDS = 60;
    /** Their fill drains away and fills up again once every this many ticks (each bar a little behind the one above). */
    private static final int PERIOD_TICKS = 160;
    private static final int STAGGER_TICKS = 20;

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private record Shown(List<ServerBossEvent> bars, int start)
    {
        void hide()
        {
            this.bars.forEach(ServerBossEvent::removeAllPlayers);
        }
    }

    private EmberdactylBarShowcase() {}

    /**
     * Shows the bars ({@link #LOOKS}) to the player who ran the command, replacing any shown before, with a chat line
     * saying what they are.
     *
     * @return 1 if they were shown, 0 if not (not a player)
     */
    public static int show(CommandSourceStack source)
    {
        ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.translatable("commands.dross.showcase.bossbars_emberdactyl.not_player"));
            return 0;
        }
        Shown old = SHOWN.remove(player.getUUID());
        if (old != null)
        {
            old.hide();
        }
        List<ServerBossEvent> bars = new ArrayList<>();
        for (String look : LOOKS)
        {
            ServerBossEvent bar = bar(look);
            bar.addPlayer(player);
            bars.add(bar);
        }
        SHOWN.put(player.getUUID(), new Shown(bars, player.server.getTickCount()));
        source.sendSuccess(() -> Component.translatable("commands.dross.showcase.bossbars_emberdactyl.shown",
                SHOWCASE_SECONDS), false);
        return 1;
    }

    /** One showcase bar of the look {@code look}, named by its key. */
    private static ServerBossEvent bar(String look)
    {
        ServerBossEvent bar = new ServerBossEvent(Component.translatable(KEY_PREFIX + look), BossEvent.BossBarColor.RED,
                BossEvent.BossBarOverlay.PROGRESS);
        bar.setDarkenScreen(false);
        bar.setPlayBossMusic(false);
        bar.setCreateWorldFog(false);
        bar.setProgress(1.0F);
        return bar;
    }

    /** How full a showcase bar is this many ticks after it went up: full, draining away, filling up again. */
    static float progressAt(int ticks)
    {
        return 0.5F + 0.5F * Mth.cos((float) (2.0D * Math.PI * Math.max(0, ticks) / PERIOD_TICKS));
    }

    /** The showcase bars shown to this player now (empty if none): for the checks. */
    public static List<ServerBossEvent> shownTo(ServerPlayer player)
    {
        Shown shown = SHOWN.get(player.getUUID());
        return shown == null ? List.of() : shown.bars();
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || SHOWN.isEmpty())
        {
            return;
        }
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();
        Iterator<Map.Entry<UUID, Shown>> it = SHOWN.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<UUID, Shown> entry = it.next();
            Shown shown = entry.getValue();
            int ticks = now - shown.start();
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player == null || ticks >= SHOWCASE_SECONDS * 20)
            {
                shown.hide();
                it.remove();
                continue;
            }
            for (int i = 0; i < shown.bars().size(); i++)
            {
                shown.bars().get(i).setProgress(progressAt(ticks - i * STAGGER_TICKS));
            }
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event)
    {
        Shown shown = SHOWN.remove(event.getEntity().getUUID());
        if (shown != null)
        {
            shown.hide();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        SHOWN.values().forEach(Shown::hide);
        SHOWN.clear();
    }
}
