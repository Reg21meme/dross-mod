package com.reg21meme.dross.dimension;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import net.minecraft.Util;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.NoiseColumn;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless check (GameTest) of the Dross's terrain from its noise alone: no chunks are generated, so it can look at
 * thousands of square blocks of several worlds in seconds. Run it with {@code .\gradlew.bat runGameTestServer --console=plain}.
 * It checks {@link #TEST_SEEDS} and the running world's own seed with the game's own generator ({@link TerrainProbe}),
 * logs what it measured ({@link TerrainSurvey}, one line per seed, "[terrain test]"), and fails if:
 * <ol>
 *   <li>anything within {@value #SILVERWOOD_RADIUS} blocks of the hub isn't Silverwood Forest (any seed);</li>
 *   <li>any Gravecrags lie within {@value #NO_MOUNTAIN_RADIUS} blocks of the hub (any seed);</li>
 *   <li>a biome's share of the land far from the hub (averaged over the seeds) is outside its range: Silverwood 32-48%,
 *       Blightwood, Cinder Wastes, Rimefall and the Gravecrags 9-20% each, the Nether Scar 2.5-8%;</li>
 *   <li>the area-weighted median size of the regions (averaged over the seeds) is outside 400-800 blocks for Blightwood,
 *       the Cinder Wastes, Rimefall and the Gravecrags, or 120-350 for the Nether Scar's patches (the user asked for
 *       regions about 400-800 across, the Scar smaller);</li>
 *   <li>some biome isn't within {@value #EVERY_BIOME_WITHIN} blocks of the hub (any seed);</li>
 *   <li>the ground goes below y {@value #HEIGHT_MIN} or above {@value #HEIGHT_MAX}, the median top of a mountain range
 *       is outside y 225-265, or Silverwood Forest's median height is outside 66-80;</li>
 *   <li>the natural ground under the hub's footprint (and 8 blocks round it) varies by more than {@value #HUB_RANGE}
 *       blocks, has a standard deviation over {@value #HUB_STD}, or steps by more than {@value #HUB_STEP} between
 *       neighbours;</li>
 *   <li>a column of the generator's own blocks has a gap (air) anywhere under its top block (no caves or overhangs);</li>
 *   <li>at the corners of the generator's 4x4 cells, its own height differs from the noise's height by more than 1 block
 *       where the ground is smooth, or more than 9 on rough rock;</li>
 *   <li>sampling the same places in a different order on several threads gives a different biome anywhere.</li>
 * </ol>
 * The sampling runs on a background thread; the server keeps ticking while it waits (at most {@link #TIMEOUT_MILLIS}).
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class TerrainSamplingGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The fixed seeds checked besides the running world's (so every run checks the same 8 worlds, plus a new one). */
    static final long[] TEST_SEEDS = {1L, 2L, 3L, 20261011L, -4172368451L, 987654321L, 77L, 1234567890123L};

    private static final int SILVERWOOD_RADIUS = 200;
    private static final int NO_MOUNTAIN_RADIUS = 650;
    private static final double[] SHARE_SILVERWOOD = {0.32D, 0.48D};
    private static final double[] SHARE_MAIN = {0.09D, 0.20D};
    private static final double[] SHARE_SCAR = {0.025D, 0.08D};
    private static final double[] REGION_MAIN = {400.0D, 800.0D};
    private static final double[] REGION_SCAR = {120.0D, 350.0D};
    private static final int EVERY_BIOME_WITHIN = 1500;
    private static final int HEIGHT_MIN = 30;
    private static final int HEIGHT_MAX = 290;
    private static final double[] PEAK_MEDIAN = {225.0D, 265.0D};
    private static final double[] SILVERWOOD_MEDIAN = {66.0D, 80.0D};
    private static final int HUB_RANGE = 5;
    private static final double HUB_STD = 1.2D;
    private static final int HUB_STEP = 1;
    /** Columns checked for gaps, and cell corners whose height is compared, per seed. */
    private static final int COLUMNS_CHECKED = 48;
    private static final int CORNERS_COMPARED = 200;
    private static final int SMOOTH_TOLERANCE = 1;
    private static final int ROUGH_TOLERANCE = 9;
    /** Places sampled twice for the determinism check. */
    private static final int DETERMINISM_POINTS = 2000;
    /** The Gravecrags and the Scar: their places in DrossBiomes.ALL. */
    private static final int SCAR = DrossBiomes.ALL.indexOf(DrossBiomes.NETHER_SCAR);
    /** The sampling takes about half a minute; give up after this long (real time). */
    static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;
    /** The tick limit is only a backstop behind TIMEOUT_MILLIS (the test server can run hundreds of ticks a second while it waits). */
    static final int BACKSTOP_TICKS = 2_000_000;

    private TerrainSamplingGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_terrain_sampling", timeoutTicks = BACKSTOP_TICKS)
    public static void terrainFromTheNoise(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        CompletableFuture<List<String>> job = CompletableFuture.supplyAsync(() -> check(dross));
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!job.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still sampling the terrain");
                    }
                })
                .thenExecute(() -> {
                    if (!job.isDone())
                    {
                        throw new GameTestAssertException("sampling the terrain took more than " + TIMEOUT_MILLIS / 1000 + " s");
                    }
                    List<String> problems = job.join();
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the terrain isn't right: " + String.join("; ", problems.subList(0, Math.min(12,
                                problems.size()))) + (problems.size() > 12 ? " (and " + (problems.size() - 12) + " more)" : ""));
                    }
                })
                .thenSucceed();
    }

    /**
     * {@link DrossBiomes#findNearest} (behind {@code /dross goto biome}) finds every biome from the hub, within
     * {@value #EVERY_BIOME_WITHIN} blocks (measured along X and Z, as the search goes), and the place it gives is that biome.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_terrain_find_biome")
    public static void findNearestFindsEveryBiome(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        List<String> problems = new ArrayList<>();
        StringBuilder found = new StringBuilder();
        for (var biome : DrossBiomes.ALL)
        {
            long t = Util.getNanos();
            var at = DrossBiomes.findNearest(dross, DrossHub.CENTER.atY(80), biome);
            double ms = (Util.getNanos() - t) / 1.0E6D;
            if (at.isEmpty())
            {
                problems.add("no " + DrossBiomes.name(biome) + " found");
                continue;
            }
            var pos = at.get();
            int far = Math.max(Math.abs(pos.getX() - DrossHub.CENTER.getX()), Math.abs(pos.getZ() - DrossHub.CENTER.getZ()));
            if (!DrossBiomes.isBiome(dross, pos, biome))
            {
                problems.add(DrossBiomes.name(biome) + " was found at " + pos.toShortString() + ", which is "
                        + TerrainProbe.forWorld(dross).biomeKey(pos.getX(), pos.getZ()).location());
            }
            if (far > EVERY_BIOME_WITHIN)
            {
                problems.add("the nearest " + DrossBiomes.name(biome) + " is " + far + " blocks out (at most " + EVERY_BIOME_WITHIN + ")");
            }
            if (DrossBiomes.byName(DrossBiomes.name(biome)).orElse(null) != biome)
            {
                problems.add("byName(\"" + DrossBiomes.name(biome) + "\") doesn't give " + biome);
            }
            found.append(String.format(Locale.ROOT, " %s at %d, %d (%.0f ms);", DrossBiomes.name(biome), pos.getX(), pos.getZ(), ms));
        }
        if (DrossBiomes.byName("not_a_biome").isPresent())
        {
            problems.add("byName(\"not_a_biome\") found something");
        }
        if (!problems.isEmpty())
        {
            throw new GameTestAssertException(String.join("; ", problems));
        }
        LOGGER.info("[terrain test] findNearest from the hub:{}", found);
        helper.succeed();
    }

    /** Checks every seed; returns the problems found (empty if all is well). Runs off the server thread. */
    private static List<String> check(ServerLevel dross)
    {
        long started = Util.getMillis();
        List<String> problems = Collections.synchronizedList(new ArrayList<>());
        List<TerrainSurvey.SeedStats> all = new ArrayList<>();
        long[] seeds = Arrays.copyOf(TEST_SEEDS, TEST_SEEDS.length + 1);
        seeds[TEST_SEEDS.length] = dross.getSeed();
        for (int s = 0; s < seeds.length; s++)
        {
            long seed = seeds[s];
            TerrainProbe probe = s == TEST_SEEDS.length ? TerrainProbe.forWorld(dross) : TerrainProbe.forSeed(dross, seed);
            String who = "seed " + seed + (s == TEST_SEEDS.length ? " (this world)" : "");
            checkNearTheHub(probe, who, problems);
            TerrainSurvey.SeedStats stats = TerrainSurvey.survey(probe);
            all.add(stats);
            LOGGER.info("[terrain test] {}", stats.summary());
            for (int b = 0; b < TerrainSurvey.BIOMES; b++)
            {
                if (stats.nearest()[b] > EVERY_BIOME_WITHIN)
                {
                    problems.add(who + ": the nearest " + TerrainSurvey.name(b) + " is " + (stats.nearest()[b] == Double.MAX_VALUE ? "not within "
                            + TerrainSurvey.NEAR_HALF : String.format(Locale.ROOT, "%.0f", stats.nearest()[b])) + " blocks from the hub (at most "
                            + EVERY_BIOME_WITHIN + ")");
                }
            }
            if (stats.hubRange() > HUB_RANGE || stats.hubStd() > HUB_STD || stats.hubSlope() > HUB_STEP)
            {
                problems.add(String.format(Locale.ROOT, "%s: the natural ground under the hub varies by %d (at most %d), std %.2f (at most %.1f), "
                        + "steepest step %d (at most %d)", who, stats.hubRange(), HUB_RANGE, stats.hubStd(), HUB_STD, stats.hubSlope(), HUB_STEP));
            }
            checkColumns(probe, who, problems);
        }

        // Averages over the seeds
        double[] shares = new double[TerrainSurvey.BIOMES];
        double[] sizes = new double[TerrainSurvey.BIOMES];
        for (TerrainSurvey.SeedStats st : all)
        {
            for (int b = 0; b < TerrainSurvey.BIOMES; b++)
            {
                shares[b] += st.shares()[b] / all.size();
                sizes[b] += st.regions()[b].weightedSize() / all.size();
            }
        }
        StringBuilder line = new StringBuilder("[terrain test] Averages over " + all.size() + " seeds: shares");
        for (int b = 0; b < TerrainSurvey.BIOMES; b++)
        {
            line.append(String.format(Locale.ROOT, " %s %.3f", TerrainSurvey.name(b), shares[b]));
        }
        line.append("; region sizes (area-weighted median)");
        for (int b = 0; b < TerrainSurvey.BIOMES; b++)
        {
            line.append(String.format(Locale.ROOT, " %s %.0f", TerrainSurvey.name(b), sizes[b]));
        }
        for (int b = 0; b < TerrainSurvey.BIOMES; b++)
        {
            double[] range = b == TerrainSurvey.SILVERWOOD ? SHARE_SILVERWOOD : b == SCAR ? SHARE_SCAR : SHARE_MAIN;
            if (shares[b] < range[0] || shares[b] > range[1])
            {
                problems.add(String.format(Locale.ROOT, "%s covers %.3f of the land (%.3f to %.3f)", TerrainSurvey.name(b), shares[b], range[0], range[1]));
            }
            if (b != TerrainSurvey.SILVERWOOD)
            {
                double[] want = b == SCAR ? REGION_SCAR : REGION_MAIN;
                if (sizes[b] < want[0] || sizes[b] > want[1])
                {
                    problems.add(String.format(Locale.ROOT, "%s's regions are %.0f blocks across (area-weighted median; %.0f to %.0f)",
                            TerrainSurvey.name(b), sizes[b], want[0], want[1]));
                }
            }
        }

        // Heights
        double min = all.stream().mapToDouble(TerrainSurvey.SeedStats::minHeight).min().orElse(0.0D);
        double max = all.stream().mapToDouble(TerrainSurvey.SeedStats::maxHeight).max().orElse(0.0D);
        List<Double> peaks = new ArrayList<>();
        List<Double> silverwood = new ArrayList<>();
        for (TerrainSurvey.SeedStats st : all)
        {
            peaks.addAll(st.peaks());
            if (st.heights()[TerrainSurvey.SILVERWOOD] != null)
            {
                silverwood.add(st.heights()[TerrainSurvey.SILVERWOOD].p50());
            }
        }
        double peakMedian = TerrainSurvey.percentile(peaks, 50);
        double silverwoodMedian = TerrainSurvey.percentile(silverwood, 50);
        line.append(String.format(Locale.ROOT, "; heights %.0f..%.0f; %d mountain ranges, median peak %.0f (p10 %.0f, p90 %.0f); Silverwood median height %.0f",
                min, max, peaks.size(), peakMedian, TerrainSurvey.percentile(peaks, 10), TerrainSurvey.percentile(peaks, 90), silverwoodMedian));
        for (int b = 0; b < TerrainSurvey.BIOMES; b++)
        {
            int biome = b;
            List<TerrainSurvey.Heights> hs = all.stream().map(st -> st.heights()[biome]).filter(h -> h != null).toList();
            if (!hs.isEmpty())
            {
                line.append(String.format(Locale.ROOT, "; %s heights p5/p50/p95 %.0f/%.0f/%.0f, slope p50/p95 %.2f/%.2f", TerrainSurvey.name(b),
                        hs.stream().mapToDouble(TerrainSurvey.Heights::p5).average().orElse(0), hs.stream().mapToDouble(TerrainSurvey.Heights::p50).average().orElse(0),
                        hs.stream().mapToDouble(TerrainSurvey.Heights::p95).average().orElse(0),
                        hs.stream().mapToDouble(TerrainSurvey.Heights::slope50).average().orElse(0),
                        hs.stream().mapToDouble(TerrainSurvey.Heights::slope95).average().orElse(0)));
            }
        }
        if (min < HEIGHT_MIN || max > HEIGHT_MAX)
        {
            problems.add(String.format(Locale.ROOT, "the ground goes from y %.0f to %.0f (%d to %d)", min, max, HEIGHT_MIN, HEIGHT_MAX));
        }
        if (peaks.isEmpty() || peakMedian < PEAK_MEDIAN[0] || peakMedian > PEAK_MEDIAN[1])
        {
            problems.add(String.format(Locale.ROOT, "the median mountain range tops out at y %.0f (%.0f to %.0f; %d ranges)", peakMedian, PEAK_MEDIAN[0],
                    PEAK_MEDIAN[1], peaks.size()));
        }
        if (silverwoodMedian < SILVERWOOD_MEDIAN[0] || silverwoodMedian > SILVERWOOD_MEDIAN[1])
        {
            problems.add(String.format(Locale.ROOT, "Silverwood Forest's median height is %.0f (%.0f to %.0f)", silverwoodMedian, SILVERWOOD_MEDIAN[0],
                    SILVERWOOD_MEDIAN[1]));
        }

        checkDeterminism(TerrainProbe.forWorld(dross), problems);
        line.append(String.format(Locale.ROOT, "; took %.1f s", (Util.getMillis() - started) / 1000.0D));
        LOGGER.info(line.toString());
        return problems;
    }

    /** 1 and 2: all Silverwood within SILVERWOOD_RADIUS of the hub, no Gravecrags within NO_MOUNTAIN_RADIUS (every 4 / 16 blocks). */
    private static void checkNearTheHub(TerrainProbe probe, String who, List<String> problems)
    {
        int cx = DrossHub.CENTER.getX();
        int cz = DrossHub.CENTER.getZ();
        int[] wrong = new int[2];
        int step = 4;
        IntStream.rangeClosed(-SILVERWOOD_RADIUS / step, SILVERWOOD_RADIUS / step).parallel().forEach(j -> {
            for (int i = -SILVERWOOD_RADIUS / step; i <= SILVERWOOD_RADIUS / step; i++)
            {
                int x = i * step;
                int z = j * step;
                if (x * x + z * z <= SILVERWOOD_RADIUS * SILVERWOOD_RADIUS && probe.biome(cx + x, cz + z) != TerrainSurvey.SILVERWOOD)
                {
                    synchronized (wrong)
                    {
                        wrong[0]++;
                    }
                }
            }
        });
        int coarse = 16;
        IntStream.rangeClosed(-NO_MOUNTAIN_RADIUS / coarse, NO_MOUNTAIN_RADIUS / coarse).parallel().forEach(j -> {
            for (int i = -NO_MOUNTAIN_RADIUS / coarse; i <= NO_MOUNTAIN_RADIUS / coarse; i++)
            {
                int x = i * coarse;
                int z = j * coarse;
                if (x * x + z * z <= NO_MOUNTAIN_RADIUS * NO_MOUNTAIN_RADIUS && probe.biome(cx + x, cz + z) == TerrainSurvey.GRAVECRAGS)
                {
                    synchronized (wrong)
                    {
                        wrong[1]++;
                    }
                }
            }
        });
        if (wrong[0] > 0)
        {
            problems.add(who + ": " + wrong[0] + " places within " + SILVERWOOD_RADIUS + " blocks of the hub aren't Silverwood Forest");
        }
        if (wrong[1] > 0)
        {
            problems.add(who + ": " + wrong[1] + " places within " + NO_MOUNTAIN_RADIUS + " blocks of the hub are Gravecrags");
        }
    }

    /**
     * 8 and 9: the generator's own columns have no gaps under their top block, and at cell corners its height matches the
     * noise's. The columns are picked where it matters most: rough rock (mountains and cliffs) first, then anywhere.
     */
    private static void checkColumns(TerrainProbe probe, String who, List<String> problems)
    {
        Random random = new Random(probe.seed ^ 0x5DEECE66DL);
        DensityFunction jagGate = probe.function("terrain/jag_gate");
        List<int[]> rough = new ArrayList<>();
        List<int[]> any = new ArrayList<>();
        for (int tries = 0; tries < 4000 && (rough.size() < CORNERS_COMPARED / 2 || any.size() < CORNERS_COMPARED / 2); tries++)
        {
            int x = TerrainSurvey.FAR_X + (random.nextInt(2 * TerrainSurvey.FAR_HALF) - TerrainSurvey.FAR_HALF & ~3);
            int z = TerrainSurvey.FAR_Z + (random.nextInt(2 * TerrainSurvey.FAR_HALF) - TerrainSurvey.FAR_HALF & ~3);
            if (TerrainProbe.at(jagGate, x, z) > 0.5D)
            {
                if (rough.size() < CORNERS_COMPARED / 2)
                {
                    rough.add(new int[] {x, z});
                }
            }
            else if (any.size() < CORNERS_COMPARED / 2)
            {
                any.add(new int[] {x, z});
            }
        }
        List<int[]> corners = new ArrayList<>(rough);
        corners.addAll(any);
        List<String> found = Collections.synchronizedList(new ArrayList<>());
        int[] worst = new int[2];
        IntStream.range(0, corners.size()).parallel().forEach(i -> {
            int x = corners.get(i)[0];
            int z = corners.get(i)[1];
            double gate = TerrainProbe.at(jagGate, x, z);
            int noiseTop = (int) Math.floor(probe.height(x, z));
            int top = probe.baseHeight(x, z) - 1;
            int diff = Math.abs(top - noiseTop);
            int allowed = gate > 0.0D ? ROUGH_TOLERANCE : SMOOTH_TOLERANCE;
            synchronized (worst)
            {
                worst[gate > 0.0D ? 1 : 0] = Math.max(worst[gate > 0.0D ? 1 : 0], diff);
            }
            if (diff > allowed)
            {
                found.add(String.format(Locale.ROOT, "%s: at %d, %d the generator's ground is y %d but the noise's is %d (rough %.2f)", who, x, z, top, noiseTop, gate));
            }
            if (i < COLUMNS_CHECKED)
            {
                NoiseColumn column = probe.column(x, z);
                int minY = probe.dross.getMinBuildHeight();
                int y = probe.dross.getMaxBuildHeight() - 1;
                while (y > minY && column.getBlock(y).isAir())
                {
                    y--;
                }
                int gaps = 0;
                for (; y >= minY; y--)
                {
                    if (column.getBlock(y).isAir())
                    {
                        gaps++;
                    }
                }
                if (gaps > 0)
                {
                    found.add(who + ": the column at " + x + ", " + z + " has " + gaps + " air blocks under its top block");
                }
            }
        });
        problems.addAll(found);
        LOGGER.info("[terrain test] {}: {} columns had no gaps under their top; at {} cell corners the generator's height was within {} of the "
                + "noise's on smooth ground and {} on rough rock", who, Math.min(COLUMNS_CHECKED, corners.size()), corners.size(), worst[0], worst[1]);
    }

    /** 10: the same places sampled one by one and then shuffled on several threads give the same biomes. */
    private static void checkDeterminism(TerrainProbe probe, List<String> problems)
    {
        Random random = new Random(42L);
        int[][] points = new int[DETERMINISM_POINTS][];
        for (int i = 0; i < points.length; i++)
        {
            points[i] = new int[] {random.nextInt(20000) - 10000, random.nextInt(20000) - 10000};
        }
        int[] first = new int[points.length];
        for (int i = 0; i < points.length; i++)
        {
            first[i] = probe.biome(points[i][0], points[i][1]);
        }
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < points.length; i++)
        {
            order.add(i);
        }
        Collections.shuffle(order, random);
        int[] second = new int[points.length];
        order.parallelStream().forEach(i -> second[i] = probe.biome(points[i][0], points[i][1]));
        int differ = 0;
        for (int i = 0; i < points.length; i++)
        {
            if (first[i] != second[i])
            {
                differ++;
            }
        }
        if (differ > 0)
        {
            problems.add(differ + " of " + points.length + " places got a different biome when sampled again in another order");
        }
    }
}
