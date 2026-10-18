package com.reg21meme.dross.dimension.sites;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.dimension.ModDimensions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * A fingerprint of the boss-site plan for a few seeds, to compare two versions of the planner (it must give exactly the
 * same answers): for each seed and boss biome, a hash of every patch out to {@link #DISTANCE} blocks (its number, id,
 * distance, depth, deepest spot and every cell) and of the first {@link #GROUPS} groups' plans (patches, slots, boss site
 * and fillers). It only logs (lines starting {@code [boss sites fingerprint]}); run it in the {@code dross_speed}
 * namespace like {@link BossSitesSpeedGameTest}:
 * <pre>.\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_speed -PgameTestWorld=&lt;a new folder&gt; --console=plain</pre>
 */
@GameTestHolder(BossSitesSpeedGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class BossSitesFingerprintGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long[] SEEDS = {1L, 2L, 20261011L, 987654321L, 1752286405569615815L};
    private static final double DISTANCE = 8_000.0D;
    private static final int GROUPS = 6;
    private static final long TIMEOUT_MILLIS = 20 * 60 * 1000L;

    private BossSitesFingerprintGameTest() {}

    @GameTest(template = "empty", batch = "dross_speed_fingerprint", timeoutTicks = 2_000_000)
    public static void bossSitesFingerprint(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        long started = Util.getMillis();
        List<CompletableFuture<List<String>>> runs = new ArrayList<>();
        for (long seed : SEEDS)
        {
            runs.add(CompletableFuture.supplyAsync(() -> fingerprint(dross, seed), Util.backgroundExecutor()));
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(runs.toArray(new CompletableFuture[0]));
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!all.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still working");
                    }
                })
                .thenExecute(() -> {
                    for (CompletableFuture<List<String>> run : runs)
                    {
                        run.join().forEach(line -> LOGGER.info("[boss sites fingerprint] {}", line));
                    }
                    LOGGER.info("[boss sites fingerprint] done in {} ms", Util.getMillis() - started);
                })
                .thenSucceed();
    }

    private static List<String> fingerprint(ServerLevel dross, long seed)
    {
        List<String> out = new ArrayList<>();
        BossSites sites = BossSites.forSeed(dross, seed);
        for (BossSites.BossBiome b : BossSites.BOSS_BIOMES)
        {
            long h = 17L;
            int n = 0;
            for (int number = 1; ; number++)
            {
                Optional<Patch> found = sites.patch(b.biome(), number);
                if (found.isEmpty() || found.get().distance() > DISTANCE)
                {
                    break;
                }
                Patch p = found.get();
                n++;
                h = h * 31 + p.number();
                h = h * 31 + p.id();
                h = h * 31 + Double.doubleToLongBits(p.distance());
                h = h * 31 + Double.doubleToLongBits(p.depth());
                h = h * 31 + p.deepest().asLong();
                h = h * 31 + p.chunkCount();
                h = h * 31 + Arrays.hashCode(p.chunks());
                h = h * 31 + p.spots(BossSitesTuning.SITE_TRIES, BossSitesTuning.SITE_TRY_SPACING).hashCode();
            }
            long g = 17L;
            StringBuilder first = new StringBuilder();
            for (int i = 0; i < GROUPS; i++)
            {
                Optional<Group> group = sites.group(b.biome(), i);
                String d = describe(group);
                g = g * 31 + d.hashCode();
                if (i == 0)
                {
                    first.append(d);
                }
            }
            out.add(String.format("seed %d %s: %d patches within %.0f blocks, patch hash %016x; groups 1-%d hash %016x; group 1: %s", seed,
                    b.biome().location().getPath(), n, DISTANCE, h, GROUPS, g, first));
        }
        return out;
    }

    private static String describe(Optional<Group> group)
    {
        if (group.isEmpty())
        {
            return "none";
        }
        Group g = group.get();
        StringBuilder s = new StringBuilder();
        for (int k = 0; k < g.patches().size(); k++)
        {
            Patch p = g.patches().get(k);
            s.append('[').append(p.number()).append(" id ").append(p.id()).append(" slot ").append(g.slots().get(k)).append(" rolled ")
                    .append(g.rolled().get(k));
            g.fillers().get(k).ifPresent(f -> s.append(" filler ").append(f.pos().toShortString()));
            s.append(']');
        }
        g.bossSite().ifPresent(b -> s.append(" boss ").append(b.patch().number()).append(" at ").append(b.pos().toShortString())
                .append(b.relaxed() ? " (smallest form)" : ""));
        return s.toString();
    }
}
