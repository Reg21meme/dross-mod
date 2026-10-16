package com.reg21meme.dross.boss.ratking;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.client.RatKingBarStyles;
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

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * The Rat King's boss bar showcase ({@code /dross showcase bossbars ratking [<1-8>]}; world-builder wires the command
 * and calls {@link #list} and {@link #show}): shows one of the bar designs ({@link RatKingBarStyles}, drawn by the
 * client's {@code DrossBossBars}) to the player who ran it, its normal look and its Rage look one above the other, named
 * "Option N: name", for {@link #SHOWCASE_SECONDS} seconds, their fill draining away and filling up again smoothly so the
 * cut-off at every health shows. Showing another replaces the last one; a chat line has a button for each design.
 * The user picks one for {@link RatKingTuning#BOSS_BAR_STYLE}.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RatKingBarShowcase
{
    /** The command that shows a design (its number is added at the end); the chat buttons run it. */
    public static final String COMMAND = "/dross showcase bossbars ratking";
    /** The bars stay up this long (seconds). */
    public static final int SHOWCASE_SECONDS = 40;
    /** Their fill drains away and fills up again once every this many ticks. */
    private static final int PERIOD_TICKS = 160;

    /** Each player's showcase bars. */
    private static final Map<UUID, Shown> SHOWN = new HashMap<>();

    private record Shown(ServerBossEvent normal, ServerBossEvent rage, int start)
    {
        void hide()
        {
            this.normal.removeAllPlayers();
            this.rage.removeAllPlayers();
        }
    }

    private RatKingBarShowcase() {}

    /** {@code /dross showcase bossbars ratking}: shows design 1, with the buttons for all of them. */
    public static int list(CommandSourceStack source)
    {
        return show(source, 1);
    }

    /**
     * {@code /dross showcase bossbars ratking <n>}: shows design {@code n}'s normal and Rage bars to the player who ran it
     * (replacing the last ones) for {@link #SHOWCASE_SECONDS} seconds, and a chat line with a button for each design.
     *
     * @return 1 if it was shown, 0 if not (not a player, or no such design)
     */
    public static int show(CommandSourceStack source, int option)
    {
        ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.translatable("commands.dross.showcase.bossbars_ratking.not_player"));
            return 0;
        }
        int count = RatKingBarStyles.ALL.size();
        if (option < 1 || option > count)
        {
            source.sendFailure(Component.translatable("commands.dross.showcase.bossbars_ratking.no_such", option, count));
            return 0;
        }
        RatKingBarStyles.Style style = RatKingBarStyles.byNumber(option);
        Shown old = SHOWN.remove(player.getUUID());
        if (old != null)
        {
            old.hide();
        }
        ServerBossEvent normal = bar(option, style, false);
        ServerBossEvent rage = bar(option, style, true);
        normal.addPlayer(player);
        rage.addPlayer(player);
        SHOWN.put(player.getUUID(), new Shown(normal, rage, player.server.getTickCount()));

        MutableComponent message = Component.translatable("commands.dross.showcase.bossbars_ratking.shown", option,
                style.name(), SHOWCASE_SECONDS);
        for (RatKingBarStyles.Style each : RatKingBarStyles.ALL)
        {
            int n = each.number();
            message.append(" ").append(Component.literal("[" + n + "]").withStyle(s -> s
                    .withColor(n == option ? DrossColors.RAT_KING_BAR_BUTTON_SHOWN : DrossColors.RAT_KING_BAR_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, COMMAND + " " + n))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                            Component.translatable("commands.dross.showcase.bossbars_ratking.button", n, each.name())))));
        }
        source.sendSuccess(() -> message, false);
        return 1;
    }

    /** One showcase bar: design {@code option}'s normal or Rage look, named "Option N: name". */
    private static ServerBossEvent bar(int option, RatKingBarStyles.Style style, boolean rage)
    {
        ServerBossEvent bar = new ServerBossEvent(
                Component.translatable(RatKingTuning.showcaseBarKey(option, rage), option, style.name()),
                rage ? BossEvent.BossBarColor.RED : BossEvent.BossBarColor.GREEN, BossEvent.BossBarOverlay.PROGRESS);
        bar.setDarkenScreen(false);
        bar.setPlayBossMusic(false);
        bar.setCreateWorldFog(false);
        bar.setProgress(1.0F);
        return bar;
    }

    /** How full the showcase bars are, this many ticks after they went up: full, draining away, filling up again. */
    static float progressAt(int ticks)
    {
        return 0.5F + 0.5F * Mth.cos((float) (2.0D * Math.PI * ticks / PERIOD_TICKS));
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
            float progress = progressAt(ticks);
            shown.normal().setProgress(progress);
            shown.rage().setProgress(progress);
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
