package com.reg21meme.dross.command;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Headless check (GameTest) of the hunter village commands ({@link GotoVillage}, October 23, 2026): from the Overworld,
 * {@code /dross locate villages} lists the {@value GotoVillage#LIST_COUNT} nearest villages to the hub (as many as
 * there are), each line with a [go] button; {@code goto village} lands at the nearest one's way in;
 * {@code goto village <x> <z>} (a [go] button) at the second's; {@code goto lair huntsman} at the nearest Huntsman's
 * village's way in, with its note.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class GotoVillageGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** A landing this near the way in (X and Z) counts as at it (the safe spot is looked for round it). */
    private static final double NEAR = GotoVillage.FALLBACK_RADIUS + 1.5D;

    private GotoVillageGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_goto_village", timeoutTicks = BACKSTOP_TICKS)
    public static void gotoVillages(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        // (planned off the server thread first: the commands then find the plans ready)
        CompletableFuture<List<Site>> planned = CompletableFuture.supplyAsync(() -> {
            BossSites sites = BossSites.forLevel(dross);
            sites.nearest(BossSites.HUNTSMAN_VILLAGE, DrossHub.CENTER, GotoVillage.SEARCH_RADIUS);
            return RimefallVillages.nearest(sites, DrossHub.CENTER, GotoVillage.SEARCH_RADIUS, GotoVillage.LIST_COUNT);
        }, Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!planned.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("waiting for the villages to be planned");
                    }
                })
                .thenExecute(() -> {
                    List<Site> villages = planned.join();
                    if (villages.isEmpty())
                    {
                        throw new GameTestAssertException("no hunter village planned within 12,000 blocks of the hub");
                    }
                    ServerPlayer player = null;
                    try
                    {
                        player = GotoBiomeGameTest.joinPlayer(server.overworld(), "goto-village-test", GotoBiomeGameTest.overworldSpawn(server));
                        // the list
                        GotoBiomeGameTest.Chat chat = new GotoBiomeGameTest.Chat();
                        int listed = GotoBiomeGameTest.run(chat, player, "dross locate villages");
                        long buttons = chat.lines.stream().filter(l -> l.getString().startsWith("[go]")).count();
                        if (listed != villages.size() || buttons != villages.size())
                        {
                            throw new GameTestAssertException("locate villages gave " + listed + " with " + buttons + " [go] lines; "
                                    + villages.size() + " expected: " + chat);
                        }
                        // the nearest, then the second by its [go] button's command
                        RimefallVillages.VillagePlan first = (RimefallVillages.VillagePlan) villages.get(0).detail();
                        expectAt(player, dross, "dross goto village", first, GotoVillage.SUCCESS);
                        if (villages.size() > 1)
                        {
                            RimefallVillages.VillagePlan second = (RimefallVillages.VillagePlan) villages.get(1).detail();
                            BlockPos way = second.wayIn();
                            expectAt(player, dross, "dross goto village " + way.getX() + " " + way.getZ(), second, GotoVillage.SUCCESS);
                        }
                        // the Huntsman's
                        Optional<Site> his = BossSites.forLevel(dross).nearest(BossSites.HUNTSMAN_VILLAGE, player.blockPosition(),
                                GotoVillage.SEARCH_RADIUS);
                        if (his.isPresent())
                        {
                            expectAt(player, dross, "dross goto lair huntsman", (RimefallVillages.VillagePlan) his.get().detail(),
                                    GotoVillage.HUNTSMAN_SUCCESS);
                        }
                        LOGGER.info("[goto village test] listed {}; went to the nearest, the second and {}", listed,
                                his.isPresent() ? "the Huntsman's" : "(no Huntsman's village)");
                    }
                    finally
                    {
                        GotoBiomeGameTest.logOut(player);
                    }
                })
                .thenSucceed();
    }

    private static void expectAt(ServerPlayer player, ServerLevel dross, String command, RimefallVillages.VillagePlan plan, String key)
    {
        GotoBiomeGameTest.Chat chat = new GotoBiomeGameTest.Chat();
        int result = GotoBiomeGameTest.run(chat, player, command);
        chat.expectLast(key, command);
        BlockPos way = plan.wayIn();
        double dx = player.getX() - (way.getX() + 0.5D);
        double dz = player.getZ() - (way.getZ() + 0.5D);
        if (result != 1 || player.level() != dross || Math.max(Math.abs(dx), Math.abs(dz)) > NEAR)
        {
            throw new GameTestAssertException(command + ": gave " + result + ", the player at " + player.blockPosition().toShortString()
                    + " in " + player.level().dimension().location() + ", the way in at " + way.toShortString());
        }
    }
}
