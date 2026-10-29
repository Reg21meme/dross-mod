package com.reg21meme.dross.boss.fire;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.client.EmberdactylBarArt;
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
 * The Emberdactyl's boss bar showcase ({@code /dross showcase bossbars emberdactyl [<page>]}; world-builder's
 * {@code DrossCommand} wires the command): shows the ten candidate designs ({@code EmberdactylBarArt}, drawn by
 * {@code tools/fire/bossbars.py}; the client's {@code DrossBossBars} draws them) to the player who ran it, five at a
 * time, for {@link #SHOWCASE_SECONDS} seconds, the bars draining away and filling up again so the cut-off at every
 * health shows. Page 1: designs 1-5, the adult's look ("The Sky That Burns"); page 2: designs 6-10, the adult's; page 3:
 * designs 1-5, the young reborn's ("The Ember That Remains"); page 4: designs 6-10, the young's. Showing a page again
 * replaces the last; the chat line has a button for each other page.
 * <p>
 * Each bar's name is the translation key {@link #KEY_PREFIX} + {@code design_<n>_<adult|young>} ("No. 3 Volcano
 * (adult)"): that's how the client knows what to draw. The fight's own bars are {@link #KEY_PREFIX} + {@code adult} and
 * {@code young}, drawn with the design {@code EmberdactylBarArt.PICK}.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class EmberdactylBarShowcase
{
    /** The command that shows the bars (a page number added at the end); the chat buttons run it. */
    public static final String COMMAND = "/dross showcase bossbars emberdactyl";
    /** The Emberdactyl's bars' names: this and the look ("adult", "young", "design_3_young"...). */
    public static final String KEY_PREFIX = "boss.dross.emberdactyl.bar.";
    /** How many bars a page shows. */
    public static final int PER_PAGE = 5;
    /** How many pages there are: the designs five at a time, the adult's looks, then the young's. */
    public static final int PAGES = (EmberdactylBarArt.DESIGNS + PER_PAGE - 1) / PER_PAGE * 2;
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

    /** Whether page {@code page} (1 to {@link #PAGES}) shows the young reborn's looks (the second half of the pages). */
    public static boolean pageIsYoung(int page)
    {
        return page > PAGES / 2;
    }

    /** The first design page {@code page} shows. */
    public static int firstDesign(int page)
    {
        return ((page - 1) % (PAGES / 2)) * PER_PAGE + 1;
    }

    /** The bars' looks page {@code page} shows, top to bottom ("design_1_adult"...). */
    public static List<String> barsFor(int page)
    {
        List<String> looks = new ArrayList<>();
        String look = pageIsYoung(page) ? "young" : "adult";
        int first = firstDesign(page);
        for (int design = first; design < first + PER_PAGE && design <= EmberdactylBarArt.DESIGNS; design++)
        {
            looks.add("design_" + design + "_" + look);
        }
        return looks;
    }

    /**
     * Shows page {@code page} ({@link #barsFor}) to the player who ran the command, replacing any shown before, with a
     * chat line saying what it is and a button for every other page.
     *
     * @return 1 if they were shown, 0 if not (not a player, or no such page)
     */
    public static int show(CommandSourceStack source, int page)
    {
        ServerPlayer player = source.getPlayer();
        if (player == null)
        {
            source.sendFailure(Component.translatable("commands.dross.showcase.bossbars_emberdactyl.not_player"));
            return 0;
        }
        if (page < 1 || page > PAGES)
        {
            return 0;
        }
        Shown old = SHOWN.remove(player.getUUID());
        if (old != null)
        {
            old.hide();
        }
        List<ServerBossEvent> bars = new ArrayList<>();
        for (String look : barsFor(page))
        {
            ServerBossEvent bar = bar(look);
            bar.addPlayer(player);
            bars.add(bar);
        }
        SHOWN.put(player.getUUID(), new Shown(bars, player.server.getTickCount()));

        int first = firstDesign(page);
        int last = Math.min(EmberdactylBarArt.DESIGNS, first + PER_PAGE - 1);
        MutableComponent message = Component.translatable("commands.dross.showcase.bossbars_emberdactyl.shown", page, PAGES,
                first, last, Component.translatable(pageIsYoung(page) ? "commands.dross.showcase.bossbars_emberdactyl.look_young"
                        : "commands.dross.showcase.bossbars_emberdactyl.look_adult"), SHOWCASE_SECONDS, EmberdactylBarArt.PICK);
        for (int other = 1; other <= PAGES; other++)
        {
            if (other == page)
            {
                continue;
            }
            String command = COMMAND + " " + other;
            message.append(" ").append(Component.translatable("commands.dross.showcase.bossbars_emberdactyl.page_button", other)
                    .withStyle(s -> s.withColor(DrossColors.EMBERDACTYL_BAR_BUTTON)
                            .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))));
        }
        source.sendSuccess(() -> message, false);
        source.sendSuccess(() -> Component.translatable("commands.dross.showcase.bossbars_emberdactyl.hint")
                .withStyle(s -> s.withColor(DrossColors.EMBERDACTYL_BAR_HINT)), false);
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
