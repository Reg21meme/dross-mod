package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
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
 * The Huntsman's boss bar showcase ({@code /dross showcase bossbars huntsman [angry]}; world-builder wires the command):
 * shows his bars (the user's picks, {@code HuntsmanBarArt}, drawn by the client's {@code DrossBossBars}) to the player who
 * ran it, for {@link #SHOWCASE_SECONDS} seconds, the bars draining away and filling up again so the cut-off at every
 * health shows: the fight's three (the Wild Hunt of stage 1, then the Huntsman and the Alpha Moose of stage 2, stacked
 * to compare; in the fight stage 1 shows only the Wild Hunt), or with {@code angry} the two angry looks (the survivor's,
 * when the other has died). Showing them again replaces the last; the chat line has a button for the other set.
 * <p>
 * Each bar's name is the translation key {@link #KEY_PREFIX} + the look ({@link #LOOKS}): that's how the client knows
 * what to draw. His fight will name its bars the same way.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HuntsmanBarShowcase
{
    /** The command that shows the bars ("angry" added at the end for the angry looks); the chat button runs it. */
    public static final String COMMAND = "/dross showcase bossbars huntsman";
    /** The bars' names: this and the look (the client reads it to draw the right one). */
    public static final String KEY_PREFIX = "boss.dross.huntsman.bar.";
    /** Every look: the Wild Hunt (stage 1), the Huntsman and the Alpha Moose (stage 2), and those two angry. */
    public static final List<String> LOOKS = List.of("wild_hunt", "huntsman", "huntsman_angry", "alpha_moose", "alpha_moose_angry");
    /** The bars stay up this long (seconds). */
    public static final int SHOWCASE_SECONDS = 60;
    /** Their fill drains away and fills up again once every this many ticks (each bar a little behind the one above). */
    private static final int PERIOD_TICKS = 160;
    private static final int STAGGER_TICKS = 20;
    private static final List<String> NORMAL = List.of("wild_hunt", "huntsman", "alpha_moose");
    private static final List<String> ANGRY = List.of("huntsman_angry", "alpha_moose_angry");

    /** The looks the showcase shows, in order. */
    public static List<String> barsFor(boolean angry)
    {
        return angry ? ANGRY : NORMAL;
    }

    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private record Shown(List<ServerBossEvent> bars, int start)
    {
        void hide()
        {
            this.bars.forEach(ServerBossEvent::removeAllPlayers);
        }
    }

    private HuntsmanBarShowcase() {}

    /**
     * Shows the bars ({@link #barsFor}) to the player who ran the command, replacing any shown before, and a chat line
     * with a button for the other set.
     *
     * @return 1 if they were shown, 0 if not (not a player)
     */
    public static int show(CommandSourceStack source, boolean angry)
    {
        ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.translatable("commands.dross.showcase.bossbars_huntsman.not_player"));
            return 0;
        }
        Shown old = SHOWN.remove(player.getUUID());
        if (old != null)
        {
            old.hide();
        }
        List<ServerBossEvent> bars = new ArrayList<>();
        for (String look : barsFor(angry))
        {
            ServerBossEvent bar = bar(look);
            bar.addPlayer(player);
            bars.add(bar);
        }
        SHOWN.put(player.getUUID(), new Shown(bars, player.server.getTickCount()));

        MutableComponent message = Component.translatable("commands.dross.showcase.bossbars_huntsman.shown",
                Component.translatable(angry ? "commands.dross.showcase.bossbars_huntsman.set_angry"
                        : "commands.dross.showcase.bossbars_huntsman.set_normal"), SHOWCASE_SECONDS);
        String other = COMMAND + (angry ? "" : " angry");
        message.append(" ").append(Component.translatable(angry ? "commands.dross.showcase.bossbars_huntsman.toggle_normal"
                : "commands.dross.showcase.bossbars_huntsman.toggle_angry").withStyle(s -> s
                .withColor(DrossColors.HUNTSMAN_BAR_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, other))));
        source.sendSuccess(() -> message, false);
        return 1;
    }

    /** One showcase bar of the look {@code look}, named by its key. */
    private static ServerBossEvent bar(String look)
    {
        ServerBossEvent bar = new ServerBossEvent(Component.translatable(KEY_PREFIX + look),
                look.endsWith("angry") ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.WHITE, BossEvent.BossBarOverlay.PROGRESS);
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
