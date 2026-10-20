package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.client.HuntsmanBarStyles;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
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
 * The Huntsman's boss bar showcase ({@code /dross showcase bossbars huntsman [<1-20>] [angry]}; world-builder wires the
 * command): shows bar designs ({@link HuntsmanBarStyles}, drawn by the client's {@code DrossBossBars}) to the player who
 * ran it, for {@link #SHOWCASE_SECONDS} seconds, the bars draining away and filling up again so the cut-off at every
 * health shows.
 * <ul>
 *   <li>Round 2 (designs 11 to 20, the default; the user's picks of round 1: design {@link #WILD_PICK} for the Wild
 *       Hunt, design 1 or 3 for the Alpha Moose, {@link #MOOSE_PICKS}): the Wild Hunt of design 9, the Huntsman of the
 *       design shown, and both Alpha Moose candidates under it (design 1, then design 3); with {@code angry} the
 *       Huntsman's angry look and both moose's.</li>
 *   <li>Round 1 (designs 1 to 10): one design's three bars (the Wild Hunt, the Huntsman, the Alpha Moose), or with
 *       {@code angry} its two angry looks.</li>
 * </ul>
 * Showing another replaces the last; the chat line has a button for each design and one to switch to the other set.
 * <p>
 * Each bar's name is the translation key {@link #KEY_PREFIX} + the look ("wild_hunt", "huntsman", "huntsman_angry",
 * "alpha_moose", "alpha_moose_angry") with the design's number as its one argument: that's how the client knows what to
 * draw.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HuntsmanBarShowcase
{
    /** The command that shows a design (its number, and maybe "angry", added at the end); the chat buttons run it. */
    public static final String COMMAND = "/dross showcase bossbars huntsman";
    /** The bars' names: this and the look (the client reads it to draw the right one). */
    public static final String KEY_PREFIX = "boss.dross.huntsman.showcase.";
    /** The bars stay up this long (seconds). */
    public static final int SHOWCASE_SECONDS = 60;
    /** Their fill drains away and fills up again once every this many ticks (each bar a little behind the one above). */
    private static final int PERIOD_TICKS = 160;
    private static final int STAGGER_TICKS = 20;
    private static final List<String> NORMAL = List.of("wild_hunt", "huntsman", "alpha_moose");
    private static final List<String> ANGRY = List.of("huntsman_angry", "alpha_moose_angry");
    /** Round 2: the Huntsman bars are designs {@link #FIRST_ROUND2} and up; the user's picks from round 1. */
    public static final int FIRST_ROUND2 = 11;
    public static final int WILD_PICK = 9;
    public static final List<Integer> MOOSE_PICKS = List.of(1, 3);

    /** One bar to show: a look of a design. */
    public record Shown1(int design, String look)
    {
    }

    /** The bars a design shows (in order): round 2's set with the picks, or round 1's design alone. */
    public static List<Shown1> barsFor(int option, boolean angry)
    {
        List<Shown1> out = new ArrayList<>();
        if (option >= FIRST_ROUND2)
        {
            if (!angry)
            {
                out.add(new Shown1(WILD_PICK, "wild_hunt"));
            }
            out.add(new Shown1(option, angry ? "huntsman_angry" : "huntsman"));
            MOOSE_PICKS.forEach(m -> out.add(new Shown1(m, angry ? "alpha_moose_angry" : "alpha_moose")));
        }
        else
        {
            (angry ? ANGRY : NORMAL).forEach(look -> out.add(new Shown1(option, look)));
        }
        return out;
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
     * Shows design {@code option}'s bars ({@link #barsFor}: round 2's set with the picks, or round 1's design alone) to
     * the player who ran the command, replacing any shown before, and a chat line with a button for each design.
     *
     * @return 1 if it was shown, 0 if not (not a player, or no such design)
     */
    public static int show(CommandSourceStack source, int option, boolean angry)
    {
        ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.translatable("commands.dross.showcase.bossbars_huntsman.not_player"));
            return 0;
        }
        int count = HuntsmanBarStyles.ALL.size();
        if (option < 1 || option > count)
        {
            source.sendFailure(Component.translatable("commands.dross.showcase.bossbars_huntsman.no_such", option, count));
            return 0;
        }
        HuntsmanBarStyles.Style style = HuntsmanBarStyles.byNumber(option);
        Shown old = SHOWN.remove(player.getUUID());
        if (old != null)
        {
            old.hide();
        }
        List<ServerBossEvent> bars = new ArrayList<>();
        for (Shown1 each : barsFor(option, angry))
        {
            ServerBossEvent bar = bar(each.design(), each.look());
            bar.addPlayer(player);
            bars.add(bar);
        }
        SHOWN.put(player.getUUID(), new Shown(bars, player.server.getTickCount()));

        boolean round2 = option >= FIRST_ROUND2;
        MutableComponent message = round2
                ? Component.translatable("commands.dross.showcase.bossbars_huntsman.shown_round2", option, style.name(),
                        Component.translatable(angry ? "commands.dross.showcase.bossbars_huntsman.set_angry_round2"
                                : "commands.dross.showcase.bossbars_huntsman.set_normal_round2"), SHOWCASE_SECONDS)
                : Component.translatable("commands.dross.showcase.bossbars_huntsman.shown", option,
                        style.name(), Component.translatable(angry ? "commands.dross.showcase.bossbars_huntsman.set_angry"
                                : "commands.dross.showcase.bossbars_huntsman.set_normal"), SHOWCASE_SECONDS);
        // Round 2's buttons first (the Huntsman bars to pick from), then round 1's.
        for (boolean second : new boolean[] {true, false})
        {
            message.append(" ").append(Component.translatable(second ? "commands.dross.showcase.bossbars_huntsman.round2"
                    : "commands.dross.showcase.bossbars_huntsman.round1"));
            for (HuntsmanBarStyles.Style each : HuntsmanBarStyles.ALL)
            {
                int n = each.number();
                if ((n >= FIRST_ROUND2) != second)
                {
                    continue;
                }
                String command = COMMAND + " " + n + (angry ? " angry" : "");
                message.append(" ").append(Component.literal("[" + n + "]").withStyle(s -> s
                        .withColor(n == option ? DrossColors.HUNTSMAN_BAR_BUTTON_SHOWN : DrossColors.HUNTSMAN_BAR_BUTTON)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                                Component.translatable("commands.dross.showcase.bossbars_huntsman.button", n, each.name())))));
            }
        }
        String other = COMMAND + " " + option + (angry ? "" : " angry");
        message.append(" ").append(Component.translatable(angry ? "commands.dross.showcase.bossbars_huntsman.toggle_normal"
                : "commands.dross.showcase.bossbars_huntsman.toggle_angry").withStyle(s -> s
                .withColor(DrossColors.HUNTSMAN_BAR_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, other))));
        source.sendSuccess(() -> message, false);
        return 1;
    }

    /** One showcase bar: design {@code option}'s look, named by its key (the design's number its argument). */
    private static ServerBossEvent bar(int option, String look)
    {
        ServerBossEvent bar = new ServerBossEvent(Component.translatable(KEY_PREFIX + look, option),
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
