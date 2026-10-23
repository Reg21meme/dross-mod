package com.reg21meme.dross.dimension.rimefall;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.BossSitesSpeedGameTest;
import com.reg21meme.dross.dimension.sites.Group;
import com.reg21meme.dross.dimension.sites.Site;
import net.minecraft.Util;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * A measuring probe, not a check (October 23, 2026): over {@link #SEEDS} and Rimefall's first {@value #GROUPS} groups,
 * how often a group has the Huntsman's village (and how often only in its relaxed form), how often a kept patch whose
 * roll came up has a regular village, and how many patches have a big, a small or no lake. In the
 * {@link BossSitesSpeedGameTest#NAMESPACE} namespace, so the normal run leaves it out:
 * <pre>.\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_speed -PgameTestWorld=&lt;a new folder&gt; --console=plain</pre>
 */
@GameTestHolder(BossSitesSpeedGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class VillageFitProbeGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long[] SEEDS = {1L, 2L, 20261011L, 987654321L, 7L, 424_242L, 11L, 12_345L};
    private static final int GROUPS = 6;
    private static final long TIMEOUT_MILLIS = 30 * 60 * 1000L;
    private static final int BACKSTOP_TICKS = 4_000_000;

    private VillageFitProbeGameTest() {}

    @GameTest(template = "empty", batch = "dross_village_fit", timeoutTicks = BACKSTOP_TICKS)
    public static void villageFitProbe(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        CompletableFuture<List<String>> run = CompletableFuture.supplyAsync(() -> probe(dross), Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!run.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still measuring");
                    }
                })
                .thenExecute(() -> {
                    if (!run.isDone())
                    {
                        throw new GameTestAssertException("the probe took more than " + TIMEOUT_MILLIS / 1000 + " s");
                    }
                    for (String line : run.join())
                    {
                        LOGGER.info("[village fit] {}", line);
                    }
                })
                .thenSucceed();
    }

    private static List<String> probe(ServerLevel dross)
    {
        List<String> out = new ArrayList<>();
        int groups = 0;
        int huntsman = 0;
        int relaxed = 0;
        int rolled = 0;
        int regular = 0;
        int patches = 0;
        int big = 0;
        int small = 0;
        int noLake = 0;
        for (long seed : SEEDS)
        {
            long started = Util.getMillis();
            BossSites sites = BossSites.forSeed(dross, seed);
            StringBuilder line = new StringBuilder("seed " + seed + ":");
            for (int g = 0; g < GROUPS; g++)
            {
                Optional<Group> found = sites.group(DrossBiomes.RIMEFALL, g);
                if (found.isEmpty())
                {
                    break;
                }
                Group group = found.get();
                groups++;
                group.bossSite().ifPresentOrElse(s -> line.append(" g").append(group.index() + 1)
                        .append(s.relaxed() ? "=H(relaxed)" : "=H"), () -> line.append(" g").append(group.index() + 1).append("=none"));
                if (group.bossSite().isEmpty())
                {
                    StringBuilder why = new StringBuilder("  seed " + seed + " group " + (group.index() + 1) + " has none; its patches:");
                    for (var p : group.patches())
                    {
                        why.append(String.format(Locale.ROOT, " #%d %d blocks2 %.0f deep", p.number(), p.area(), p.depth()));
                    }
                    out.add(why.toString());
                }
                if (group.bossSite().isPresent())
                {
                    huntsman++;
                    relaxed += group.bossSite().get().relaxed() ? 1 : 0;
                }
                for (int k = 0; k < group.patches().size(); k++)
                {
                    patches++;
                    Optional<Site> area = sites.site(BossSites.RIMEFALL_AREA, group.patches().get(k));
                    RimefallLake lake = area.map(s -> ((RimefallAreas.RimefallArea) s.detail()).lake()).orElse(null);
                    if (lake == null)
                    {
                        noLake++;
                    }
                    else if (lake.radius() >= RimefallTuning.LAKE_RADIUS - 1.0D)
                    {
                        big++;
                    }
                    else
                    {
                        small++;
                    }
                    if (group.slots().get(k) != 0 && group.rolled().get(k))
                    {
                        rolled++;
                        regular += group.fillers().get(k).isPresent() ? 1 : 0;
                    }
                }
            }
            line.append(String.format(Locale.ROOT, " (%d ms)", Util.getMillis() - started));
            out.add(line.toString());
        }
        out.add(String.format(Locale.ROOT, "%d groups: the Huntsman's village in %d (%.0f%%, %d relaxed); regular villages in %d of "
                        + "%d rolled kept patches (%.0f%%); lakes: %d big, %d small, %d none of %d patches", groups, huntsman,
                100.0D * huntsman / Math.max(1, groups), relaxed, regular, rolled, 100.0D * regular / Math.max(1, rolled), big, small,
                noLake, patches));
        return out;
    }
}
