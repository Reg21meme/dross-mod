package com.reg21meme.dross.dimension.sites;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.SewerSites;
import com.reg21meme.dross.dimension.sewer.Sewers;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.IntStream;
import javax.imageio.ImageIO;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless checks (GameTests) of the boss-site planner ({@link BossSites}; the user's decision, October 16 2026). Run
 * them with {@code .\gradlew.bat runGameTestServer -PgameTestWorld=<a new folder> --console=plain}.
 * <ul>
 *   <li>{@link #plannerGroupsNearestFirst}: for a few fixed seeds (the noise only), for Blightwood and the Cinder Wastes:
 *       the patches are numbered nearest the hub first, none smaller than {@link BossSitesTuning#MIN_PATCH_AREA}; group
 *       g is patches 3g + 1 to 3g + 3; every group has exactly one boss patch (slot 0) and two kept free (slots 1 and 2);
 *       a group's boss site and fillers lie inside their patch and in its biome; fillers (regular sewers) only in kept
 *       patches; the regular sewers' rolls come up on about half the kept Blightwood patches; and a second plan of the
 *       same seed, asked in another order (a far chunk first), gives exactly the same answers, as does the running
 *       world's own plan.</li>
 *   <li>{@link #sameAnswersWithOrWithoutTheBackgroundReader}: for two more seeds, one plan is read the way the background
 *       reader reads it (sampling without the lock) while 6 threads ask it about places all over at once (so some wait and
 *       read further themselves, with the helper threads), and a second plan is asked the same things on one thread, the
 *       furthest first: every answer (each place's patch, the numbered patches, the first groups' plans and their site
 *       chunks) is the same. Also: the quick biome sampler is in use and agrees with Minecraft's on 20,000 columns.</li>
 *   <li>{@link #sewersStartAtTheirPlannedSites}: in this world, every planned sewer of Blightwood's first two groups
 *       starts in its planned chunk, as the right kind (dross:rat_cave or dross:sewers), and none starts in the deepest
 *       chunk of a kept patch without one.</li>
 *   <li>{@link #bossSitesMap}: with {@code DROSS_SEWER_PREVIEWS=1} or {@code DROSS_TERRAIN_PREVIEWS=1}, draws
 *       {@code terrain-previews/boss_sites.png}: this world's land round the hub, its Cinder Wastes and Blightwood patches
 *       numbered by distance, their groups of 3, and what each holds.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class BossSitesGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The fixed seeds planned (each in its own thread). */
    private static final long[] SEEDS = {1L, 2L, 20261011L, 987654321L};
    /** How many groups of each biome are planned per seed. */
    private static final int BLIGHTWOOD_GROUPS = 6;
    private static final int CINDER_GROUPS = 4;
    /** The second plan is asked about this group's boss chunk first (a far one), to check the answers don't depend on the order. */
    private static final int FAR_GROUP = 4;
    /** The regular sewers' rolls must come up on this share of the kept Blightwood patches, give or take (REGULAR_SEWER_CHANCE, 0.5). */
    private static final double ROLL_TOLERANCE = 0.2D;
    /** A group may lack a boss only rarely: at most this share of the groups planned. */
    private static final double MAX_NO_BOSS_SHARE = 0.05D;
    /** Real-time limit for the planning. */
    private static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;
    private static final int BACKSTOP_TICKS = 2_000_000;
    private static final String PREVIEW_ENV = "DROSS_SEWER_PREVIEWS";
    private static final String TERRAIN_PREVIEW_ENV = "DROSS_TERRAIN_PREVIEWS";
    /** The map reaches this far from the hub every way (blocks), this many blocks a pixel. */
    private static final int MAP_HALF = 3200;
    private static final int MAP_CELL = 8;
    private static final int MAP_LEGEND = 300;
    /** Groups are drawn while their nearest patch is within the map's corners (this share of MAP_HALF out). */
    private static final double MAP_CORNER = 1.42D;

    private BossSitesGameTest() {}

    /** What one seed's checks found. */
    private record SeedResult(long seed, List<String> problems, int groups, int noBoss, int relaxed, int kept, int rolled, int fitted, String summary)
    {
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_boss_sites", timeoutTicks = BACKSTOP_TICKS)
    public static void plannerGroupsNearestFirst(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        long started = Util.getMillis();
        List<CompletableFuture<SeedResult>> runs = new ArrayList<>();
        for (long seed : SEEDS)
        {
            runs.add(CompletableFuture.supplyAsync(() -> checkSeed(dross, seed), Util.backgroundExecutor()));
        }
        // the world's own plan against a fresh plan of its seed
        CompletableFuture<List<String>> world = CompletableFuture.supplyAsync(() -> {
            List<String> problems = new ArrayList<>();
            BossSites own = BossSites.forLevel(dross);
            BossSites fresh = BossSites.forSeed(dross, dross.getSeed());
            for (BossSites.BossBiome b : BossSites.BOSS_BIOMES)
            {
                String a = describe(own.group(b.biome(), 0));
                String c = describe(fresh.group(b.biome(), 0));
                if (!a.equals(c))
                {
                    problems.add("this world's own plan and a fresh plan of its seed differ for " + b.biome().location().getPath() + " group 1: " + a + " / " + c);
                }
            }
            return problems;
        }, Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        CompletableFuture<Void> all = CompletableFuture.allOf(CompletableFuture.allOf(runs.toArray(new CompletableFuture[0])), world);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!all.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still planning");
                    }
                })
                .thenExecute(() -> {
                    if (!all.isDone())
                    {
                        throw new GameTestAssertException("planning took more than " + TIMEOUT_MILLIS / 1000 + " s");
                    }
                    List<String> problems = new ArrayList<>(world.join());
                    int groups = 0;
                    int noBoss = 0;
                    int relaxed = 0;
                    int kept = 0;
                    int rolled = 0;
                    int fitted = 0;
                    for (CompletableFuture<SeedResult> run : runs)
                    {
                        SeedResult r = run.join();
                        problems.addAll(r.problems());
                        groups += r.groups();
                        noBoss += r.noBoss();
                        relaxed += r.relaxed();
                        kept += r.kept();
                        rolled += r.rolled();
                        fitted += r.fitted();
                        LOGGER.info("[boss sites] seed {}: {}", r.seed(), r.summary());
                    }
                    double rollShare = kept == 0 ? 0.0D : rolled / (double) kept;
                    LOGGER.info("[boss sites] {} seeds, {} groups planned in {} s: {} without a boss, {} with the boss's smallest form; regular sewers rolled "
                                    + "on {} of {} kept Blightwood patches ({}%), {} of them fit", SEEDS.length, groups, (Util.getMillis() - started) / 1000,
                            noBoss, relaxed, rolled, kept, Math.round(100.0D * rollShare), fitted);
                    if (Math.abs(rollShare - BossSitesTuning.REGULAR_SEWER_CHANCE) > ROLL_TOLERANCE)
                    {
                        problems.add(String.format(Locale.ROOT, "regular sewers rolled on %.0f%% of the kept Blightwood patches (about %.0f%% expected)",
                                100.0D * rollShare, 100.0D * BossSitesTuning.REGULAR_SEWER_CHANCE));
                    }
                    if (noBoss > Math.ceil(groups * MAX_NO_BOSS_SHARE))
                    {
                        problems.add(noBoss + " of " + groups + " groups have no boss");
                    }
                    if (rolled > 0 && fitted == 0)
                    {
                        problems.add("no regular sewer fits in any of the " + rolled + " kept patches whose roll came up");
                    }
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the boss-site plan isn't right: " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
                    }
                })
                .thenSucceed();
    }

    /** Plans one seed twice (in two orders) and checks the rules. Any thread. */
    private static SeedResult checkSeed(ServerLevel dross, long seed)
    {
        long started = Util.getMillis();
        List<String> problems = new ArrayList<>();
        BossSites a = BossSites.forSeed(dross, seed);
        int groups = 0;
        int noBoss = 0;
        int relaxed = 0;
        int kept = 0;
        int rolled = 0;
        int fitted = 0;
        Map<ResourceKey<Biome>, List<String>> described = new HashMap<>();
        StringBuilder summary = new StringBuilder();
        for (BossSites.BossBiome b : BossSites.BOSS_BIOMES)
        {
            int count = b.biome().equals(DrossBiomes.BLIGHTWOOD) ? BLIGHTWOOD_GROUPS : CINDER_GROUPS;
            String name = b.biome().location().getPath();
            // numbering: nearest first, consecutive, no specks
            List<Patch> patches = a.patches(b.biome(), count * BossSitesTuning.GROUP_SIZE);
            for (int i = 0; i < patches.size(); i++)
            {
                Patch p = patches.get(i);
                if (p.number() != i + 1 || i > 0 && p.distance() < patches.get(i - 1).distance() || p.area() < BossSitesTuning.MIN_PATCH_AREA
                        || !p.biome().equals(b.biome()) || !p.contains(p.nearestPoint().getX(), p.nearestPoint().getZ()))
                {
                    problems.add(String.format(Locale.ROOT, "seed %d %s: patch %d is out of order or too small (%s)", seed, name, i + 1, p));
                }
            }
            List<String> lines = new ArrayList<>();
            summary.append(name).append(": ");
            for (int g = 0; g < count; g++)
            {
                Optional<Group> found = a.group(b.biome(), g);
                if (found.isEmpty())
                {
                    problems.add("seed " + seed + " " + name + ": no group " + (g + 1));
                    break;
                }
                Group group = found.get();
                groups++;
                lines.add(describe(found));
                // the group is patches 3g+1 to 3g+3
                for (int k = 0; k < group.patches().size(); k++)
                {
                    if (group.patches().get(k).number() != g * BossSitesTuning.GROUP_SIZE + k + 1)
                    {
                        problems.add("seed " + seed + " " + name + " group " + (g + 1) + " holds patch " + group.patches().get(k).number());
                    }
                }
                // exactly one boss patch, the other two kept free
                long bosses = group.slots().stream().filter(s -> s == 0).count();
                if (group.bossSite().isEmpty())
                {
                    noBoss++;
                    if (bosses != 0)
                    {
                        problems.add("seed " + seed + " " + name + " group " + (g + 1) + ": a slot 0 but no boss site");
                    }
                }
                else
                {
                    Site boss = group.bossSite().get();
                    relaxed += boss.relaxed() ? 1 : 0;
                    if (bosses != 1 || group.slotOf(boss.patch()) != 0 || boss.use() != b.boss())
                    {
                        problems.add("seed " + seed + " " + name + " group " + (g + 1) + ": " + bosses + " boss slots, the boss site in slot "
                                + group.slotOf(boss.patch()));
                    }
                    checkInside(problems, seed, name, g, boss, a);
                }
                if (!group.slots().containsAll(List.of(1, 2)) && group.patches().size() == BossSitesTuning.GROUP_SIZE)
                {
                    problems.add("seed " + seed + " " + name + " group " + (g + 1) + ": slots " + group.slots() + " (0, 1 and 2 expected)");
                }
                for (int k = 0; k < group.patches().size(); k++)
                {
                    Patch p = group.patches().get(k);
                    boolean boss = group.slots().get(k) == 0;
                    Optional<Site> filler = group.fillers().get(k);
                    if (boss && (filler.isPresent() || group.rolled().get(k)))
                    {
                        problems.add("seed " + seed + " " + name + " group " + (g + 1) + ": the boss's patch " + p.number() + " also has a filler");
                    }
                    if (!boss && b.filler() != null)
                    {
                        kept++;
                        if (group.rolled().get(k))
                        {
                            rolled++;
                            fitted += filler.isPresent() ? 1 : 0;
                        }
                        else if (filler.isPresent())
                        {
                            problems.add("seed " + seed + " " + name + " patch " + p.number() + " has a filler but its roll didn't come up");
                        }
                    }
                    filler.ifPresent(s -> checkInside(problems, seed, name, -1, s, a));
                }
            }
            described.put(b.biome(), lines);
            summary.append(lines.size()).append(" groups; ");
        }
        // the same seed planned again, asked in another order: a far group's boss chunk first, then the groups backwards
        BossSites b = BossSites.forSeed(dross, seed);
        a.group(DrossBiomes.BLIGHTWOOD, FAR_GROUP).flatMap(Group::bossSite).ifPresent(site -> {
            if (!b.isSiteChunk(BossSites.RAT_KING_SEWER, site.chunk().x, site.chunk().z))
            {
                problems.add("seed " + seed + ": a second plan doesn't put the Rat King's sewer of group " + (FAR_GROUP + 1) + " at " + site.chunk());
            }
        });
        for (BossSites.BossBiome bb : BossSites.BOSS_BIOMES)
        {
            List<String> lines = described.get(bb.biome());
            for (int g = lines.size() - 1; g >= 0; g--)
            {
                String again = describe(b.group(bb.biome(), g));
                if (!again.equals(lines.get(g)))
                {
                    problems.add("seed " + seed + " " + bb.biome().location().getPath() + " group " + (g + 1) + " differs when planned again: "
                            + lines.get(g) + " / " + again);
                }
            }
        }
        summary.append(String.format(Locale.ROOT, "planned twice in %d ms; Blightwood group 1: %s", Util.getMillis() - started,
                described.getOrDefault(DrossBiomes.BLIGHTWOOD, List.of("-")).stream().findFirst().orElse("-")));
        return new SeedResult(seed, problems, groups, noBoss, relaxed, kept, rolled, fitted, summary.toString());
    }

    /** A site lies in its patch, in the patch's biome. */
    private static void checkInside(List<String> problems, long seed, String name, int group, Site site, BossSites sites)
    {
        BlockPos p = site.pos();
        if (!site.patch().contains(p.getX(), p.getZ()) || !sites.terrain().isBiome(site.patch().biome(), p.getX(), p.getZ()))
        {
            problems.add("seed " + seed + " " + name + (group >= 0 ? " group " + (group + 1) : "") + ": the " + site.use() + " site " + p.toShortString()
                    + " isn't inside its patch " + site.patch().number());
        }
    }

    /** One line saying everything about a group, to compare two plans. */
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
            s.append('[').append(p.number()).append(" id ").append(p.id()).append(" slot ").append(g.slots().get(k));
            g.fillers().get(k).ifPresent(f -> s.append(" filler ").append(f.pos().toShortString()));
            s.append(']');
        }
        g.bossSite().ifPresent(b -> s.append(" boss ").append(b.patch().number()).append(" at ").append(b.pos().toShortString())
                .append(b.relaxed() ? " (smallest form)" : ""));
        return s.toString();
    }

    // ---------------------------------------------------------------------------------------------
    // The same answers whoever reads the map, in whatever order (the background reader, waiting threads, one thread)
    // ---------------------------------------------------------------------------------------------

    /** The seeds compared (not the ones above). */
    private static final long[] READER_SEEDS = {7L, 424_242L};
    /** The background-style reading goes this far... */
    private static final int READER_DISTANCE = 3_000;
    /** ...while the questions reach this far (blocks from the hub), so some of them have to wait and read further. */
    private static final int QUESTION_DISTANCE = 5_000;
    private static final int QUESTIONS = 3_000;
    private static final int QUESTION_THREADS = 6;
    private static final int COMPARED_GROUPS = 4;
    private static final int SAMPLER_CHECKS = 20_000;

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_boss_sites_reader", timeoutTicks = BACKSTOP_TICKS)
    public static void sameAnswersWithOrWithoutTheBackgroundReader(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        List<CompletableFuture<List<String>>> runs = new ArrayList<>();
        for (long seed : READER_SEEDS)
        {
            runs.add(CompletableFuture.supplyAsync(() -> compareReaders(dross, seed), Util.backgroundExecutor()));
        }
        CompletableFuture<Void> all = CompletableFuture.allOf(runs.toArray(new CompletableFuture[0]));
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!all.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still comparing");
                    }
                })
                .thenExecute(() -> {
                    if (!all.isDone())
                    {
                        throw new GameTestAssertException("comparing took more than " + TIMEOUT_MILLIS / 1000 + " s");
                    }
                    List<String> problems = new ArrayList<>();
                    runs.forEach(r -> problems.addAll(r.join()));
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the answers depend on who reads the map: " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
                    }
                })
                .thenSucceed();
    }

    /** Asks two plans of one seed the same things, read in two different ways; returns what differs. Any thread. */
    private static List<String> compareReaders(ServerLevel dross, long seed)
    {
        long started = Util.getMillis();
        List<String> problems = new ArrayList<>();
        java.util.Random random = new java.util.Random(seed);
        int[][] places = new int[QUESTIONS][];
        for (int i = 0; i < QUESTIONS; i++)
        {
            places[i] = new int[] {DrossHub.CENTER.getX() + random.nextInt(2 * QUESTION_DISTANCE + 1) - QUESTION_DISTANCE,
                    DrossHub.CENTER.getZ() + random.nextInt(2 * QUESTION_DISTANCE + 1) - QUESTION_DISTANCE};
        }
        // plan A: the background reader's way, with 6 threads asking at the same time
        BossSites a = BossSites.forSeed(dross, seed);
        String[] answersA = new String[QUESTIONS];
        CompletableFuture<Void> reading = CompletableFuture.runAsync(() -> a.readInBackgroundTo(READER_DISTANCE), Util.backgroundExecutor());
        List<CompletableFuture<Void>> asking = new ArrayList<>();
        for (int t = 0; t < QUESTION_THREADS; t++)
        {
            final int tt = t;
            asking.add(CompletableFuture.runAsync(() -> {
                for (int i = tt; i < QUESTIONS; i += QUESTION_THREADS)
                {
                    answersA[i] = answer(a, places[i]);
                }
            }, Util.backgroundExecutor()));
        }
        reading.join();
        asking.forEach(CompletableFuture::join);
        // plan B: one thread, the furthest places first
        BossSites b = BossSites.forSeed(dross, seed);
        Integer[] order = new Integer[QUESTIONS];
        for (int i = 0; i < QUESTIONS; i++)
        {
            order[i] = i;
        }
        java.util.Arrays.sort(order, (i, j) -> Double.compare(b.hubDistance(places[j][0], places[j][1]), b.hubDistance(places[i][0], places[i][1])));
        String[] answersB = new String[QUESTIONS];
        for (int i : order)
        {
            answersB[i] = answer(b, places[i]);
        }
        int different = 0;
        for (int i = 0; i < QUESTIONS; i++)
        {
            if (!answersA[i].equals(answersB[i]))
            {
                if (different++ < 3)
                {
                    problems.add(String.format(Locale.ROOT, "seed %d: the patch at %d, %d is %s read by the background reader and waiting threads, %s read "
                            + "by one thread", seed, places[i][0], places[i][1], answersA[i], answersB[i]));
                }
            }
        }
        if (different > 3)
        {
            problems.add("seed " + seed + ": " + different + " places differ in all");
        }
        // the numbered patches and the first groups' plans, and their sites' chunks
        for (BossSites.BossBiome bb : BossSites.BOSS_BIOMES)
        {
            String name = bb.biome().location().getPath();
            List<Patch> pa = a.patches(bb.biome(), 40);
            List<Patch> pb = b.patches(bb.biome(), 40);
            for (int k = 0; k < Math.max(pa.size(), pb.size()); k++)
            {
                String da = k < pa.size() ? pa.get(k).toString() + " " + pa.get(k).id() : "none";
                String db = k < pb.size() ? pb.get(k).toString() + " " + pb.get(k).id() : "none";
                if (!da.equals(db))
                {
                    problems.add("seed " + seed + " " + name + " patch " + (k + 1) + ": " + da + " / " + db);
                    break;
                }
            }
            for (int g = 0; g < COMPARED_GROUPS; g++)
            {
                Optional<Group> ga = a.group(bb.biome(), g);
                String da = describe(ga);
                String db = describe(b.group(bb.biome(), g));
                if (!da.equals(db))
                {
                    problems.add("seed " + seed + " " + name + " group " + (g + 1) + ": " + da + " / " + db);
                }
                for (Site site : ga.map(Group::sites).orElse(List.of()))
                {
                    ChunkPos c = site.chunk();
                    boolean bothYes = a.isSiteChunk(site.use(), c.x, c.z) && b.isSiteChunk(site.use(), c.x, c.z);
                    boolean bothNo = !a.isSiteChunk(site.use(), c.x + 1, c.z) && !b.isSiteChunk(site.use(), c.x + 1, c.z);
                    if (!bothYes || !bothNo)
                    {
                        problems.add("seed " + seed + " " + name + " group " + (g + 1) + ": the " + site.use() + " site's chunk " + c + " isn't answered the same");
                    }
                }
            }
        }
        // the quick biome sampler: in use, and the same biomes as Minecraft's own
        SiteTerrain terrain = a.terrain();
        if (!terrain.quickSampler())
        {
            problems.add("seed " + seed + ": the quick biome sampler failed its check and isn't in use (see the ERROR above)");
        }
        int wrong = 0;
        for (int i = 0; i < SAMPLER_CHECKS; i++)
        {
            int x = random.nextInt(64_001) - 32_000;
            int z = random.nextInt(64_001) - 32_000;
            if (!terrain.biome(x, z).equals(terrain.biomeByMinecraft(x, z)))
            {
                wrong++;
            }
        }
        if (wrong > 0)
        {
            problems.add("seed " + seed + ": the quick biome sampler disagrees with Minecraft's at " + wrong + " of " + SAMPLER_CHECKS + " columns");
        }
        LOGGER.info("[boss sites] seed {}: a plan read in the background while {} threads asked {} questions, and a plan asked on one thread, furthest "
                + "first, gave {} ({} ms)", seed, QUESTION_THREADS, QUESTIONS, problems.isEmpty() ? "the same answers" : problems.size() + " differences",
                Util.getMillis() - started);
        return problems;
    }

    /** What a plan says about a place: its Blightwood or Cinder Wastes patch (number and id), or none. */
    private static String answer(BossSites sites, int[] place)
    {
        StringBuilder s = new StringBuilder();
        for (BossSites.BossBiome bb : BossSites.BOSS_BIOMES)
        {
            s.append(sites.patchAt(bb.biome(), place[0], place[1]).map(p -> p.number() + "/" + p.id()).orElse("-")).append(' ');
        }
        return s.toString();
    }

    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_boss_sites_world", timeoutTicks = BACKSTOP_TICKS)
    public static void sewersStartAtTheirPlannedSites(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<List<Group>> planned = CompletableFuture.supplyAsync(() -> BossSites.forLevel(dross).groups(DrossBiomes.BLIGHTWOOD, 2),
                Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!planned.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still planning");
                    }
                })
                .thenExecute(() -> {
                    List<String> problems = new ArrayList<>();
                    int sites = 0;
                    int empty = 0;
                    for (Group group : planned.join())
                    {
                        for (int k = 0; k < group.patches().size(); k++)
                        {
                            Patch p = group.patches().get(k);
                            Optional<Site> site = group.slots().get(k) == 0 ? group.bossSite() : group.fillers().get(k);
                            if (site.isPresent())
                            {
                                sites++;
                                ResourceKey<Structure> want = site.get().use() == BossSites.RAT_KING_SEWER ? Sewers.RAT_CAVE : Sewers.SEWERS;
                                ResourceKey<Structure> other = want == Sewers.RAT_CAVE ? Sewers.SEWERS : Sewers.RAT_CAVE;
                                if (start(dross, want, site.get().chunk()) == null || start(dross, other, site.get().chunk()) != null)
                                {
                                    problems.add("no " + want.location() + " (alone) starts in its planned chunk " + site.get().chunk() + " (patch " + p.number() + ")");
                                }
                                else if (site.get().detail() instanceof SewerSites.Layout layout && layout.ratCave() != (want == Sewers.RAT_CAVE))
                                {
                                    problems.add("the layout planned at " + site.get().chunk() + " is the wrong kind");
                                }
                            }
                            else
                            {
                                // a kept patch without a sewer: nothing starts at its deepest chunk
                                ChunkPos deepest = new ChunkPos(p.deepest());
                                empty++;
                                if (start(dross, Sewers.SEWERS, deepest) != null || start(dross, Sewers.RAT_CAVE, deepest) != null)
                                {
                                    problems.add("a sewer starts at " + deepest + " in patch " + p.number() + ", where none was planned");
                                }
                            }
                        }
                    }
                    LOGGER.info("[boss sites] this world's first two Blightwood groups: {} sewers start where planned, {} patches without one checked", sites, empty);
                    if (sites == 0)
                    {
                        problems.add("no sewer is planned in this world's first two Blightwood groups");
                    }
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the sewers aren't where they were planned: " + String.join("; ", problems));
                    }
                })
                .thenSucceed();
    }

    private static StructureStart start(ServerLevel dross, ResourceKey<Structure> key, ChunkPos chunkPos)
    {
        Structure structure = dross.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        ChunkAccess chunk = dross.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = structure == null ? null : dross.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
        return start != null && start.isValid() ? start : null;
    }

    // ---------------------------------------------------------------------------------------------
    // The map (terrain-previews/boss_sites.png)
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_boss_sites_map", timeoutTicks = BACKSTOP_TICKS)
    public static void bossSitesMap(GameTestHelper helper)
    {
        if (!"1".equals(System.getenv(PREVIEW_ENV)) && !"1".equals(System.getenv(TERRAIN_PREVIEW_ENV)))
        {
            LOGGER.info("[boss sites] map skipped (set {}=1 or {}=1 to draw terrain-previews/boss_sites.png)", PREVIEW_ENV, TERRAIN_PREVIEW_ENV);
            helper.succeed();
            return;
        }
        System.setProperty("java.awt.headless", "true");
        ServerLevel dross = dross(helper);
        Path out = FMLPaths.GAMEDIR.get().toAbsolutePath().getParent().resolve("terrain-previews").resolve("boss_sites.png");
        CompletableFuture<String> drawn = CompletableFuture.supplyAsync(() -> drawMap(dross, out), Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!drawn.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still drawing the map");
                    }
                })
                .thenExecute(() -> LOGGER.info("[boss sites] {}", drawn.join()))
                .thenSucceed();
    }

    private static String drawMap(ServerLevel dross, Path out)
    {
        long started = Util.getMillis();
        BossSites sites = BossSites.forLevel(dross);
        int n = 2 * MAP_HALF / MAP_CELL;
        int x0 = DrossHub.CENTER.getX() - MAP_HALF;
        int z0 = DrossHub.CENTER.getZ() - MAP_HALF;
        int[] land = new int[n * n];
        IntStream.range(0, n).parallel().forEach(j -> {
            for (int i = 0; i < n; i++)
            {
                Holder<Biome> b = sites.terrain().biome(x0 + i * MAP_CELL + MAP_CELL / 2, z0 + j * MAP_CELL + MAP_CELL / 2);
                land[j * n + i] = b.is(DrossBiomes.CINDER_WASTES) ? 1 : b.is(DrossBiomes.BLIGHTWOOD) ? 2 : 0;
            }
        });
        BufferedImage img = new BufferedImage(n + MAP_LEGEND, n + 40, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(DrossColors.PREVIEW_BACKGROUND));
        g.fillRect(0, 0, img.getWidth(), img.getHeight());
        int oy = 40;
        for (int j = 0; j < n; j++)
        {
            for (int i = 0; i < n; i++)
            {
                int rgb = land[j * n + i] == 1 ? DrossColors.PREVIEW_CINDER : land[j * n + i] == 2 ? DrossColors.PREVIEW_BLIGHTWOOD : DrossColors.PREVIEW_OTHER_LAND;
                img.setRGB(i, oy + j, darken(rgb, land[j * n + i] == 0 ? 1.0D : 0.62D));
            }
        }
        // the patches: outlined (where the map's biome changes), numbered, joined in their groups, and what they hold
        g.setClip(0, oy, n, n);   // (marks of patches near the edge stay on the map)
        int drawnPatches = 0;
        int drawnGroups = 0;
        List<String> lines = new ArrayList<>();
        for (BossSites.BossBiome bb : BossSites.BOSS_BIOMES)
        {
            boolean blight = bb.biome().equals(DrossBiomes.BLIGHTWOOD);
            for (int gi = 0; ; gi++)
            {
                Optional<Patch> first = sites.patch(bb.biome(), gi * BossSitesTuning.GROUP_SIZE + 1);
                if (first.isEmpty() || first.get().distance() > MAP_HALF * MAP_CORNER)
                {
                    break;
                }
                Optional<Group> group = sites.group(bb.biome(), gi);
                if (group.isEmpty())
                {
                    break;
                }
                drawnGroups++;
                Group grp = group.get();
                for (int k = 0; k < grp.patches().size(); k++)
                {
                    Patch p = grp.patches().get(k);
                    drawnPatches++;
                    outline(img, land, n, oy, p, x0, z0, blight ? 2 : 1);
                    int[] at = toMap(new int[] {p.deepest().getX() - x0, oy + p.deepest().getZ() - z0}, oy);
                    boolean boss = grp.slots().get(k) == 0;
                    Optional<Site> filler = grp.fillers().get(k);
                    int ring = boss ? DrossColors.PREVIEW_BOSS_SITE : filler.isPresent() ? DrossColors.PREVIEW_REGULAR_SEWER
                            : grp.rolled().get(k) ? DrossColors.PREVIEW_NO_FIT : -1;
                    Optional<Site> site = boss ? grp.bossSite() : filler;
                    if (site.isPresent())
                    {
                        int[] s = toMap(new int[] {site.get().pos().getX() - x0, oy + site.get().pos().getZ() - z0}, oy);
                        g.setColor(new Color(ring));
                        g.setStroke(new BasicStroke(boss ? 3.0F : 2.2F));
                        int r = boss ? 9 : 6;
                        g.drawOval(s[0] - r, s[1] - r, 2 * r, 2 * r);
                        if (boss)
                        {
                            g.fillOval(s[0] - 3, s[1] - 3, 6, 6);
                        }
                    }
                    else if (ring != -1)
                    {
                        g.setColor(new Color(ring));
                        g.setStroke(new BasicStroke(1.6F));
                        g.drawOval(at[0] - 5, at[1] - 5, 10, 10);
                    }
                    // its label: the patch's number, then its group's in the group's own colour
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                    String label = (blight ? "B" : "C") + p.number();
                    String groupLabel = " g" + (gi + 1);
                    int w1 = g.getFontMetrics().stringWidth(label);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                    int w2 = g.getFontMetrics().stringWidth(groupLabel);
                    g.setColor(new Color(0, 0, 0, 175));
                    g.fillRoundRect(at[0] + 8, at[1] - 9, w1 + w2 + 6, 15, 5, 5);
                    g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 12));
                    g.setColor(new Color(DrossColors.PREVIEW_PATCH_LINE));
                    g.drawString(label, at[0] + 11, at[1] + 2);
                    g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
                    g.setColor(new Color(DrossColors.PREVIEW_GROUP_LINE));
                    g.drawString(groupLabel, at[0] + 11 + w1, at[1] + 2);
                    lines.add(String.format(Locale.ROOT, "%s %s: group %d slot %d%s", blight ? "Blightwood" : "Cinder Wastes", p.number(), gi + 1,
                            grp.slots().get(k), boss ? " (boss)" : filler.isPresent() ? " (regular sewer)" : grp.rolled().get(k) ? " (rolled, none fits)" : ""));
                }
            }
        }
        // the hub (still on the map), then the title and legend round it
        int hx = DrossHub.CENTER.getX() - x0;
        int hz = DrossHub.CENTER.getZ() - z0;
        int[] hub = toMap(new int[] {hx, oy + hz}, oy);
        g.setColor(new Color(DrossColors.PREVIEW_MARK));
        g.setStroke(new BasicStroke(2.0F));
        g.drawOval(hub[0] - 6, hub[1] - 6, 12, 12);
        g.drawLine(hub[0] - 10, hub[1], hub[0] + 10, hub[1]);
        g.drawLine(hub[0], hub[1] - 10, hub[0], hub[1] + 10);
        for (int ringBlocks = 1000; ringBlocks <= MAP_HALF; ringBlocks += 1000)
        {
            int r = ringBlocks / MAP_CELL;
            g.setColor(withAlpha(DrossColors.PREVIEW_MARK, 60));
            g.setStroke(new BasicStroke(1.0F));
            g.drawOval(hub[0] - r, hub[1] - r, 2 * r, 2 * r);
            g.drawString(ringBlocks + "", hub[0] + 3, hub[1] - r + 12);
        }
        // the title and the legend
        g.setClip(null);
        g.setColor(new Color(DrossColors.PREVIEW_TEXT));
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.drawString(String.format(Locale.ROOT, "Boss sites, seed %d: the Cinder Wastes (C) and Blightwood (B) patches, numbered nearest the hub first, %d blocks every way",
                dross.getSeed(), MAP_HALF), 10, 18);
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString("1 pixel = " + MAP_CELL + " blocks; north is up; rings every 1,000 blocks from the hub (the cross); B7 g3 = Blightwood patch 7, in group 3",
                10, 34);
        int lx = n + 14;
        int ly = oy + 10;
        ly = legend(g, lx, ly, DrossColors.PREVIEW_CINDER, "Cinder Wastes (patches C1, C2...)", false);
        ly = legend(g, lx, ly, DrossColors.PREVIEW_BLIGHTWOOD, "Blightwood (patches B1, B2...)", false);
        ly = legend(g, lx, ly, DrossColors.PREVIEW_OTHER_LAND, "other biomes", false);
        ly = legend(g, lx, ly, DrossColors.PREVIEW_BOSS_SITE, "the group's boss (C: the Colossus's lair,", true);
        g.setColor(new Color(DrossColors.PREVIEW_TEXT));
        g.drawString("B: the Rat King's sewer); its patch is slot 0", lx + 24, ly - 6);
        ly += 12;
        ly = legend(g, lx, ly, DrossColors.PREVIEW_REGULAR_SEWER, "a regular sewer (a kept patch, 1 in 2)", true);
        ly = legend(g, lx, ly, DrossColors.PREVIEW_NO_FIT, "rolled a regular sewer, none fits", true);
        g.setColor(new Color(DrossColors.PREVIEW_TEXT));
        g.drawString("Unmarked patches are kept free for", lx, ly + 8);
        g.drawString("the biome's two future bosses.", lx, ly + 24);
        g.drawString("Each group (g1, g2...) is the next 3", lx, ly + 48);
        g.drawString("patches out from the hub: one boss", lx, ly + 64);
        g.drawString("each (gold), two kept for later.", lx, ly + 80);
        g.drawString("Darker land: patches further out than", lx, ly + 104);
        g.drawString("the groups drawn.", lx, ly + 120);
        g.dispose();
        try
        {
            Files.createDirectories(out.getParent());
            ImageIO.write(img, "png", out.toFile());
        }
        catch (IOException e)
        {
            throw new IllegalStateException("can't write " + out, e);
        }
        LOGGER.info("[boss sites] the map's patches: {}", String.join("; ", lines));
        return String.format(Locale.ROOT, "drew %s (%d patches in %d groups) in %d ms", out, drawnPatches, drawnGroups, Util.getMillis() - started);
    }

    private static int[] toMap(int[] p, int oy)
    {
        return new int[] {p[0] / MAP_CELL, oy + (p[1] - oy) / MAP_CELL};
    }

    /** Lights up the patch's pixels and draws its edge (map pixels of the patch next to pixels that aren't its biome). */
    private static void outline(BufferedImage img, int[] land, int n, int oy, Patch p, int x0, int z0, int kind)
    {
        int i0 = Math.max(0, (p.minX() - x0) / MAP_CELL);
        int i1 = Math.min(n - 1, (p.maxX() - x0) / MAP_CELL);
        int j0 = Math.max(0, (p.minZ() - z0) / MAP_CELL);
        int j1 = Math.min(n - 1, (p.maxZ() - z0) / MAP_CELL);
        int base = kind == 1 ? DrossColors.PREVIEW_CINDER : DrossColors.PREVIEW_BLIGHTWOOD;
        for (int j = j0; j <= j1; j++)
        {
            for (int i = i0; i <= i1; i++)
            {
                int x = x0 + i * MAP_CELL + MAP_CELL / 2;
                int z = z0 + j * MAP_CELL + MAP_CELL / 2;
                if (!p.contains(x, z) || land[j * n + i] != kind)
                {
                    continue;
                }
                boolean edge = i == 0 || j == 0 || i == n - 1 || j == n - 1 || land[j * n + i - 1] != kind || land[j * n + i + 1] != kind
                        || land[(j - 1) * n + i] != kind || land[(j + 1) * n + i] != kind;
                img.setRGB(i, oy + j, edge ? DrossColors.PREVIEW_PATCH_LINE : base);
            }
        }
    }

    private static int legend(Graphics2D g, int x, int y, int rgb, String text, boolean ring)
    {
        g.setColor(new Color(rgb));
        if (ring)
        {
            g.setStroke(new BasicStroke(2.2F));
            g.drawOval(x + 2, y - 10, 12, 12);
        }
        else
        {
            g.fillRect(x, y - 12, 16, 16);
        }
        g.setColor(new Color(DrossColors.PREVIEW_TEXT));
        g.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
        g.drawString(text, x + 24, y + 1);
        return y + 24;
    }

    private static int darken(int rgb, double f)
    {
        int r = (int) (((rgb >> 16) & 255) * f);
        int gg = (int) (((rgb >> 8) & 255) * f);
        int b = (int) ((rgb & 255) * f);
        return (r << 16) | (gg << 8) | b;
    }

    private static Color withAlpha(int rgb, int alpha)
    {
        return new Color((rgb >> 16) & 255, (rgb >> 8) & 255, rgb & 255, alpha);
    }

    private static ServerLevel dross(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        return dross;
    }
}
