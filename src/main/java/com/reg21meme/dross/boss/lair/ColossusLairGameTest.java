package com.reg21meme.dross.boss.lair;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.boss.golem.StoneGolem;
import com.reg21meme.dross.boss.golem.WildGolemSpawns;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Group;
import com.reg21meme.dross.dimension.sites.Patch;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.dimension.sites.SiteTerrain;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * Headless checks (GameTests) of the Cinder Colossus's lairs. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}. They wait in real time for real-time work (the planner's
 * background thread, chunk loading, a lair being built), because the test server runs ticks as fast as it can.
 * <ul>
 *   <li>{@link #fitTestForFixedSeeds}: for a few fixed seeds (one of them the world where the landmark check once came out
 *       a block short), from the noise alone: the planner's first {@value #PLANNED_GROUPS} Cinder Wastes groups each hold a
 *       lair site (or say why not), inside its patch, its whole footprint Cinder Wastes; a normal site's volcano can stand
 *       its full rise over the land round it (a landmark); the patches with lairs are all different. Then the volcano and
 *       its way in are planned at every site and at spots nudged round each ({@link #checkPlannedWayIn}).</li>
 *   <li>{@link #wayInIsWalkableOnHardGround}: the way in planned on 224 made-up hard grounds (steep, terraced, bumpy,
 *       mesas, cliffs, ditches): walkable everywhere, never a step of more than a block, and meeting the land.</li>
 *   <li>{@link #lairsAreBuiltWhenAPlayerComesNear}: this world's lairs of the planner's groups 1 and 2: each is built only
 *       once a stand-in player comes within {@link LairTuning#BUILD_RANGE} blocks (a far group's isn't), exactly at its
 *       planned site, one per patch, and is a volcano: a round floor 41 blocks across, mostly obsidian with sparse magma
 *       and glowing fissures (glass over sealed lava); room over the middle for the lobs and room for the Colossus
 *       everywhere; obsidian stalactites; the crater open to the sky over the middle and standing well above the land; a
 *       cave mouth at least 9 wide and 8 tall; the way in walkable from the land to the floor along its middle and both
 *       sides of the passage (a block's step at most outside, 0.6 inside), and the land shaped in front of the mouth never
 *       stepping more than a block between neighbours; the open space closed except up the crater and out of the mouth,
 *       with no lava touching any of it; every lava block (under the floor and outside on the slopes) walled in by
 *       Minecraft's flow rule, none in front of the mouth and nothing burnable near it; no natural spawning inside. With
 *       {@code DROSS_LAIR_PREVIEWS} set it writes the first lair's blocks for the preview images
 *       (tools/golems/lair_preview.py).</li>
 *   <li>{@link #colossusRisesOnceAndNeverAgain}: a stand-in player stepping onto a lair's floor makes the Colossus rise in
 *       the middle; once it's dead the lair stays empty, for the player who stayed (over two waits) and for a newcomer
 *       (no respawn).
 *       Cleans up after itself (no Colossus left, the lair's boss data reset).</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class ColossusLairGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The fixed seeds of the fit test (-8826770357676534336 is the world where R6's check found a 2-block step in front of
     * the mouth; -2463643320505446998 the one where the rim came out 15 blocks over the land round it, not 16).
     */
    private static final long[] SEEDS = {1L, 4242L, 20261012L, -8826770357676534336L, -2463643320505446998L};
    /** The fit test plans this many of each seed's Cinder Wastes groups. */
    private static final int PLANNED_GROUPS = 2;
    /** The way in is also planned at each site moved by these offsets (more kinds of ground). */
    private static final int[][] WAY_IN_NUDGES = {{0, 0}, {24, -24}, {-24, 24}};
    /** The made-up hard grounds: these slopes (blocks down per block)... */
    private static final double[] HARD_TILTS = {0.0D, 0.15D, 0.35D, 0.5D};
    /** ...falling towards this many directions, the first this many degrees round from east (so none lines up with the mouth)... */
    private static final int HARD_DIRECTIONS = 8;
    private static final double HARD_FIRST_DIRECTION = 10.0D;
    /** ...round a site here, at this height, with the volcano's noise from this seed. */
    private static final int HARD_X = 4000;
    private static final int HARD_Z = -3000;
    private static final int HARD_BASE = 100;
    private static final long HARD_SEED = 0x6861726447726FL;
    /** A failing check names at most this many problems. */
    private static final int MAX_REPORTED = 8;
    /** The fit test's planning gets at most this long (real time). */
    private static final long SEARCH_TIMEOUT_MILLIS = 5 * 60 * 1000L;
    /** A lair is built in the background once a player comes near: wait at most this long for each (real time). */
    private static final long PLACEMENT_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    /** The stand-in who makes a lair be built stands this far from its middle (inside BUILD_RANGE, well outside the volcano). */
    private static final int NEAR_STANDIN_DISTANCE = 200;
    /** The lairs of the planner's groups 0 and 1 are built and checked; this group's, which nobody comes near, must not be. */
    private static final int FAR_GROUP = 6;
    /** The Colossus must rise within this long of the stand-in stepping in (real time; loading the chunks takes a moment). */
    private static final long RISE_TIMEOUT_MILLIS = 2 * 60 * 1000L;
    /** The tick limits are only a backstop behind the real-time limits (the test server can run hundreds of ticks a second). */
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** Ticks waited to see that nothing rises (several of the lair's checks). */
    private static final int QUIET_TICKS = 6 * CHECK_INTERVAL_TICKS;
    /** A spot's footprint is checked this far out (the lair reaches about 45 blocks from its middle)... */
    private static final int FOOTPRINT_RADIUS = 48;
    /** ...every this many blocks. */
    private static final int FOOTPRINT_STEP = 4;
    /** The floor is at least this share obsidian... */
    private static final double MIN_OBSIDIAN_SHARE = 0.4D;
    /** ...with at least this many magma blocks, but no more than this share of it... */
    private static final int MIN_FLOOR_MAGMA = 12;
    private static final double MAX_MAGMA_SHARE = 0.06D;
    /** ...and at least this many blocks of glowing fissure (glass over lava). */
    private static final int MIN_FISSURE_GLASS = 30;
    /** At least this many stalactite tips hang from the dome. */
    private static final int MIN_STALACTITE_TIPS = 10;
    /** The rim stands at least this many blocks above the high land round the volcano's foot (its 80th percentile). */
    private static final int MIN_LANDMARK_RISE = 16;
    /** At least this many lava blocks outside on the volcano (its rivulets and pools). */
    private static final int MIN_OUTSIDE_LAVA = 40;
    /** Outside the mouth, a step up or down of at most a block (a jump) on the way in. */
    private static final double OUTSIDE_STEP = 1.0D;
    /** With this set (to anything), the shape test writes the volcano's blocks for tools/golems/lair_preview.py. */
    private static final String PREVIEW_ENV = "DROSS_LAIR_PREVIEWS";
    /** A player steps up at most this much without jumping. */
    private static final double PLAYER_STEP = 0.6D;
    /** The flood fill of the open space stops after this many blocks (it's about 30,000). */
    private static final int FLOOD_LIMIT = 400_000;
    /** The stand-in player stands this far from the middle of the floor. */
    private static final int PLAYER_OFFSET = 9;
    /** The Colossus must rise this close to the middle (horizontally). */
    private static final double RISE_TOLERANCE = 1.0D;

    private ColossusLairGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The fit test, from the noise
    // ---------------------------------------------------------------------------------------------

    /** One seed's planned groups: their lair sites (or empty), and what's wrong. */
    private record SeedPlan(long seed, BossSites sites, List<Optional<Site>> lairs, List<String> problems, long millis)
    {
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_lair_site_rule", timeoutTicks = BACKSTOP_TICKS)
    public static void fitTestForFixedSeeds(GameTestHelper helper)
    {
        ServerLevel dross = drossOf(helper);
        List<CompletableFuture<SeedPlan>> runs = new ArrayList<>();
        for (long seed : SEEDS)
        {
            runs.add(CompletableFuture.supplyAsync(() -> planSeed(dross, seed)));
        }
        long deadline = Util.getMillis() + SEARCH_TIMEOUT_MILLIS;
        List<CompletableFuture<WayInReport>> wayIns = new ArrayList<>();
        long[] wayInStart = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!runs.stream().allMatch(CompletableFuture::isDone) && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still planning");
                    }
                })
                .thenExecute(() -> {
                    List<String> problems = new ArrayList<>();
                    for (int i = 0; i < SEEDS.length; i++)
                    {
                        if (!runs.get(i).isDone())
                        {
                            problems.add("seed " + SEEDS[i] + ": planning took more than " + SEARCH_TIMEOUT_MILLIS / 1000 + " s");
                            continue;
                        }
                        problems.addAll(runs.get(i).join().problems());
                    }
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the lairs' fit test isn't kept: " + String.join("; ",
                                problems.subList(0, Math.min(problems.size(), MAX_REPORTED))));
                    }
                    // Then the way in at every site (and at spots nudged round each), planned from the noise.
                    wayInStart[0] = Util.getMillis();
                    int lowest = LairBuilder.lowestFloor(dross);
                    int highest = LairBuilder.highestRim(dross);
                    for (CompletableFuture<SeedPlan> run : runs)
                    {
                        SeedPlan plan = run.join();
                        for (Optional<Site> found : plan.lairs())
                        {
                            if (found.isEmpty())
                            {
                                continue;
                            }
                            Site site = found.get();
                            for (int[] nudge : WAY_IN_NUDGES)
                            {
                                int x = site.pos().getX() + nudge[0];
                                int z = site.pos().getZ() + nudge[1];
                                String name = String.format("seed %d, group %d, spot %d %d", plan.seed(), site.patch().group() + 1, x, z);
                                wayIns.add(CompletableFuture.supplyAsync(() -> planWayIn(name, plan.sites().terrain(), x, z, lowest, highest)));
                            }
                        }
                    }
                })
                .thenWaitUntil(() -> {
                    if (!wayIns.stream().allMatch(CompletableFuture::isDone) && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still planning the ways in");
                    }
                })
                .thenExecute(() -> {
                    List<String> problems = new ArrayList<>();
                    WayInStats stats = new WayInStats();
                    for (CompletableFuture<WayInReport> run : wayIns)
                    {
                        if (!run.isDone())
                        {
                            problems.add("planning the ways in took more than " + SEARCH_TIMEOUT_MILLIS / 1000 + " s");
                            break;
                        }
                        WayInReport report = run.join();
                        problems.addAll(report.problems());
                        stats.add(report);
                    }
                    LOGGER.info("[lair test] The way in planned from the noise at {} lair sites (and nudged spots) in {} ms on up to {} "
                                    + "thread(s): {}", wayIns.size(), Util.getMillis() - wayInStart[0],
                            java.util.concurrent.ForkJoinPool.getCommonPoolParallelism(), stats.describe());
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("a planned way in isn't walkable: " + String.join("; ",
                                problems.subList(0, Math.min(problems.size(), MAX_REPORTED))));
                    }
                })
                .thenSucceed();
    }

    /** Plans a seed's first groups (the noise only, with the lair's own fit test plugged in) and checks their lair sites. Any thread. */
    private static SeedPlan planSeed(ServerLevel dross, long seed)
    {
        long started = Util.getMillis();
        BossSites sites = BossSites.forSeed(dross, seed);
        String name = "seed " + seed;
        List<String> problems = new ArrayList<>();
        List<Optional<Site>> lairs = new ArrayList<>();
        java.util.Set<Long> patches = new java.util.HashSet<>();
        StringBuilder log = new StringBuilder();
        int lowest = LairBuilder.lowestFloor(dross);
        int highest = LairBuilder.highestRim(dross);
        for (int g = 0; g < PLANNED_GROUPS; g++)
        {
            Optional<Group> group = sites.group(DrossBiomes.CINDER_WASTES, g);
            if (group.isEmpty())
            {
                problems.add(name + ": no Cinder Wastes group " + (g + 1));
                break;
            }
            Optional<Site> lair = group.get().bossSite();
            lairs.add(lair);
            if (lair.isEmpty())
            {
                log.append(String.format("%n  group %d: no lair (none of its %d patches can hold one)", g + 1, group.get().patches().size()));
                continue;
            }
            Site site = lair.get();
            int x = site.pos().getX();
            int z = site.pos().getZ();
            if (site.use() != BossSites.COLOSSUS_LAIR || !site.patch().contains(x, z) || site.patch().group() != g)
            {
                problems.add(name + ", group " + (g + 1) + ": the lair site " + x + " " + z + " isn't a Colossus lair in its group's patch");
            }
            if (!patches.add(site.patch().id()))
            {
                problems.add(name + ": two lairs in the patch " + site.patch().number());
            }
            int radius = site.relaxed() ? RELAXED_FIT_RADIUS : FIT_RADIUS;
            int outside = 0;
            int reach = Math.min(FOOTPRINT_RADIUS, radius);
            for (int dx = -reach; dx <= reach; dx += FOOTPRINT_STEP)
            {
                for (int dz = -reach; dz <= reach; dz += FOOTPRINT_STEP)
                {
                    if (dx * dx + dz * dz <= reach * reach && !sites.terrain().isBiome(DrossBiomes.CINDER_WASTES, x + dx, z + dz))
                    {
                        outside++;
                    }
                }
            }
            if (outside > 0)
            {
                problems.add(name + ", group " + (g + 1) + ": " + outside + " sample(s) of the footprint round " + x + " " + z + " aren't Cinder Wastes");
            }
            LairShape.Ground ground = (gx, gz) -> sites.terrain().topBlock(gx, gz);
            LairShape.VolcanoPlan plan = LairShape.volcanoPlan(x, z, ground, lowest, highest);
            if (!site.relaxed() && plan.rimY() - plan.highLand() < RIM_RISE)
            {
                problems.add(String.format("%s, group %d: the volcano at %d %d can only stand %d over the land round it (not %d): %s", name,
                        g + 1, x, z, plan.rimY() - plan.highLand(), RIM_RISE, plan.describe()));
            }
            log.append(String.format("%n  group %d: patch %d (of %s), %d blocks from the hub, at %d %d%s: %s", g + 1, site.patch().number(),
                    group.get().patches().stream().map(p -> String.valueOf(p.number())).toList(), Math.round(site.distanceTo(0, 0)), x, z,
                    site.relaxed() ? " (its smallest form)" : "", plan.describe()));
        }
        long millis = Util.getMillis() - started;
        LOGGER.info("[lair test] {}: planned in {} ms{}", name, millis, log);
        return new SeedPlan(seed, sites, lairs, problems, millis);
    }

    // ---------------------------------------------------------------------------------------------
    // The way in, planned on many sites (maths only)
    // ---------------------------------------------------------------------------------------------

    /**
     * The way in planned (by {@link LairBuilder#plan}, exactly as the builder plans it) on hard made-up ground: slopes
     * falling up to 0.5 blocks per block (steeper than the cone's {@link LairTuning#MAX_BASE_TILT}) in eight directions,
     * each plain, in 2-block terraces, bumpy, on a mesa top, with a 5- or 12-block cliff or a ditch across the front;
     * every one must be walkable ({@link #checkPlannedWayIn}).
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_lair_way_in", timeoutTicks = BACKSTOP_TICKS)
    public static void wayInIsWalkableOnHardGround(GameTestHelper helper)
    {
        long start = Util.getMillis();
        ServerLevel dross = drossOf(helper);
        int lowest = LairBuilder.lowestFloor(dross);
        int highest = LairBuilder.highestRim(dross);
        ImprovedNoise bumps = new ImprovedNoise(RandomSource.create(HARD_SEED));
        List<String> problems = new ArrayList<>();
        WayInStats stats = new WayInStats();
        for (double tilt : HARD_TILTS)
        {
            for (int k = 0; k < HARD_DIRECTIONS; k++)
            {
                double degrees = HARD_FIRST_DIRECTION + k * 360.0D / HARD_DIRECTIONS;
                double angle = Math.toRadians(degrees);
                for (HardGround kind : HardGround.values())
                {
                    LairShape.Ground ground = (x, z) -> kind.height(tilt, angle, bumps, x - HARD_X, z - HARD_Z);
                    String name = String.format("%s ground falling %.2f towards %.0f degrees", kind.name().toLowerCase(java.util.Locale.ROOT),
                            tilt, degrees);
                    LairBuilder.Site site = LairBuilder.plan(HARD_X, HARD_Z, ground, HARD_SEED, lowest, highest);
                    List<String> found = new ArrayList<>();
                    checkPlannedWayIn(name, site, ground, found);
                    problems.addAll(found);
                    stats.add(new WayInReport(name, site, LairShape.volcanoPlan(HARD_X, HARD_Z, ground, lowest, highest).tunnel(), found));
                }
            }
        }
        LOGGER.info("[lair test] The way in planned on {} hard made-up grounds in {} ms: {}", stats.sites, Util.getMillis() - start,
                stats.describe());
        if (!problems.isEmpty())
        {
            throw new GameTestAssertException(problems.size() + " problem(s) with the way in on hard ground: " + String.join("; ",
                    problems.subList(0, Math.min(problems.size(), MAX_REPORTED))));
        }
        helper.succeed();
    }

    /** Made-up ground for {@link #wayInIsWalkableOnHardGround}, round a site's middle (dx, dz from it). */
    private enum HardGround
    {
        PLANE, TERRACES, BUMPS, MESA, CLIFF_5, CLIFF_12, DITCH;

        /** The Y of the top block, on ground falling {@code tilt} blocks per block towards {@code angle}. */
        int height(double tilt, double angle, ImprovedNoise bumps, int dx, int dz)
        {
            double along = dx * Math.cos(angle) + dz * Math.sin(angle);
            double across = -dx * Math.sin(angle) + dz * Math.cos(angle);
            double y = HARD_BASE - tilt * along;
            switch (this)
            {
                case TERRACES -> y = HARD_BASE - 2.0D * Math.floor(tilt * along / 2.0D) + (Math.floorMod((int) Math.floor(across), 12) < 6 ? 2 : 0);
                case BUMPS -> y += 4.0D * bumps.noise(dx / 6.0D, 0.5D, dz / 6.0D);
                case MESA -> {
                    double a = Math.atan2(dz, dx);
                    double edge = 56.0D + 6.0D * bumps.noise(Math.cos(a) * 2.0D, 7.5D, Math.sin(a) * 2.0D);
                    y += 12.0D * Mth.clamp((edge - Math.hypot(dx, dz)) / 3.0D, 0.0D, 1.0D);
                }
                case CLIFF_5 -> y -= along > 60.0D ? 5.0D : 0.0D;
                case CLIFF_12 -> y -= along > 64.0D ? 12.0D : 0.0D;
                case DITCH -> y -= Math.abs(along - 60.0D) < 2.0D ? 3.0D : 0.0D;
                default -> { }
            }
            return (int) Math.floor(y);
        }
    }

    /** One planned way in: the site, which way the mouth would have faced without the way in's say, and what's wrong. */
    private record WayInReport(String name, LairBuilder.Site site, Direction firstChoice, List<String> problems)
    {
    }

    /** Counts over many planned ways in, for the log. */
    private static final class WayInStats
    {
        int sites;
        int ramps;
        int longest;
        int turned;
        int mostChanged;
        int steepest;
        String steepestName = "";
        String turnedName = "";
        String longestName = "";

        void add(WayInReport report)
        {
            LairShape.Approach a = report.site().approach();
            sites++;
            int past = a.end() - a.apronEnd();
            ramps += past > BLEND ? 1 : 0;
            if (past > longest)
            {
                longest = past;
                longestName = report.name();
            }
            if (report.site().plan().tunnel() != report.firstChoice())
            {
                turned++;
                turnedName = report.name() + ", to the " + report.site().plan().tunnel().getName() + " instead of the "
                        + report.firstChoice().getName();
            }
            mostChanged = Math.max(mostChanged, a.maxChange());
            double tilt = Math.hypot(report.site().plan().tiltX(), report.site().plan().tiltZ());
            if (Math.round(tilt * 100.0D) > steepest)
            {
                steepest = (int) Math.round(tilt * 100.0D);
                steepestName = report.name();
            }
        }

        String describe()
        {
            return String.format("%d site(s); %d needed a ramp past the blend (the longest %d past the apron: %s); %d had the mouth "
                            + "turned to another side (e.g. %s); columns raised or lowered by at most %d; the steepest land under a cone "
                            + "tilted %.2f (%s)", sites, ramps, longest, longestName, turned, turnedName, mostChanged, steepest / 100.0D,
                    steepestName);
        }
    }

    /** Plans the volcano and its way in at {@code x}, {@code z} from a seed's noise alone (any thread) and checks the way in. */
    private static WayInReport planWayIn(String name, SiteTerrain terrain, int x, int z, int lowest, int highest)
    {
        java.util.Map<Long, Integer> cache = new java.util.HashMap<>();
        LairShape.Ground ground = (gx, gz) -> cache.computeIfAbsent(((long) gx << 32) ^ (gz & 0xFFFFFFFFL), k -> terrain.topBlock(gx, gz));
        LairBuilder.Site site = LairBuilder.plan(x, z, ground, terrain.seed(), lowest, highest);
        List<String> problems = new ArrayList<>();
        checkPlannedWayIn(name, site, ground, problems);
        return new WayInReport(name, site, LairShape.volcanoPlan(x, z, ground, lowest, highest).tunnel(), problems);
    }

    /**
     * Checks a way in as planned (maths only, from {@code ground}, the land before anything is built): on the land
     * shaped in front of the gully no column is more than a block above or below its neighbours (north, east, south,
     * west) or further from the floor than from the apron (so the apron is level with the floor, and the gully's floor
     * meets it); and its last row meets the land just beyond within a block across the passage's width. So the way in
     * can be walked from the land to the mouth without a jump of more than a block.
     */
    private static void checkPlannedWayIn(String name, LairBuilder.Site site, LairShape.Ground ground, List<String> problems)
    {
        LairShape.VolcanoPlan plan = site.plan();
        LairShape.Approach a = site.approach();
        Direction dir = plan.tunnel();
        Direction side = dir.getClockWise();
        int floor = plan.floorY();
        int found = 0;
        String example = null;
        for (int s = a.first(); s <= a.end(); s++)
        {
            for (int l = -a.reachL(); l <= a.reachL(); l++)
            {
                int h = a.height(s, l);
                int n = a.apronDistance(s, l);
                if (Math.abs(h - floor) > n)
                {
                    found++;
                    example = example == null ? String.format("y %d at %d out, %d to the side, is %d from the floor (y %d) but only %d "
                            + "from the apron", h, s, l, Math.abs(h - floor), floor, n) : example;
                }
                if (l < a.reachL() && Math.abs(h - a.height(s, l + 1)) > 1 || s < a.end() && Math.abs(h - a.height(s + 1, l)) > 1)
                {
                    found++;
                    example = example == null ? String.format("a step of more than a block next to %d out, %d to the side", s, l) : example;
                }
            }
        }
        int s = a.end() + 1;
        for (int l = -PASSAGE_HALF_WIDTH; l <= PASSAGE_HALF_WIDTH; l++)
        {
            int x = plan.x() + dir.getStepX() * s + side.getStepX() * l;
            int z = plan.z() + dir.getStepZ() * s + side.getStepZ() * l;
            int land = Math.max(ground.top(x, z), site.surface().top(x, z));
            int step = Math.abs(a.height(a.end(), l) - land);
            if (step > 1)
            {
                found++;
                example = example == null ? String.format("a step of %d blocks onto the land %d out, %d to the side", step, s, l) : example;
            }
        }
        if (found > 0)
        {
            problems.add(String.format("%s: %d problem(s), e.g. %s (mouth to the %s, floor y %d; %s)", name, found, example, dir.getName(),
                    floor, a.describe()));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The real lair's shape
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_lair_shape", timeoutTicks = BACKSTOP_TICKS)
    public static void lairsAreBuiltWhenAPlayerComesNear(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = drossOf(helper);
        Built run = new Built(server, dross);
        helper.startSequence()
                .thenExecute(() -> run.guard(run::plan))
                .thenWaitUntil(() -> run.guard(() -> run.waitFor(() -> run.planned.isDone(), "the planner's lair sites")))
                .thenExecute(() -> run.guard(() -> run.comeNear(0)))
                .thenWaitUntil(() -> run.guard(() -> run.waitFor(() -> run.built(0), "the lair of group 1 to be built")))
                .thenExecute(() -> run.guard(() -> run.check(0)))
                .thenExecute(() -> run.guard(() -> run.comeNear(1)))
                .thenWaitUntil(() -> run.guard(() -> run.waitFor(() -> run.built(1), "the lair of group 2 to be built")))
                .thenExecute(() -> run.guard(() -> run.check(1)))
                .thenExecute(() -> run.guard(run::checkOnePerPatchAndFarOneUnbuilt))
                .thenExecute(run::cleanUp)
                .thenSucceed();
    }

    /** The built-lairs test's state: the planner's sites, and the stand-ins who came near them. */
    private static final class Built
    {
        final MinecraftServer server;
        final ServerLevel dross;
        CompletableFuture<List<Optional<Site>>> planned;
        final List<ServerPlayer> players = new ArrayList<>();
        long deadline;
        boolean failed;

        Built(MinecraftServer server, ServerLevel dross)
        {
            this.server = server;
            this.dross = dross;
        }

        void guard(Runnable step)
        {
            if (failed)
            {
                return;
            }
            try
            {
                step.run();
            }
            catch (Waiting e)
            {
                throw e;
            }
            catch (RuntimeException e)
            {
                failed = true;
                LOGGER.error("[lair test] {}", e.getMessage());
                cleanUp();
                throw e instanceof GameTestAssertException assertion ? assertion : new GameTestAssertException("the test threw " + e);
            }
        }

        void waitFor(java.util.function.BooleanSupplier condition, String what)
        {
            if (!condition.getAsBoolean() && Util.getMillis() < deadline)
            {
                throw new Waiting("waiting for " + what);
            }
        }

        /** The planner's lair sites of groups 0 and 1, and the far group's (on a background thread). */
        void plan()
        {
            deadline = Util.getMillis() + PLACEMENT_TIMEOUT_MILLIS;
            planned = CompletableFuture.supplyAsync(() -> {
                BossSites sites = BossSites.forLevel(dross);
                List<Optional<Site>> list = new ArrayList<>();
                for (int g : new int[] {0, 1, FAR_GROUP})
                {
                    list.add(sites.group(DrossBiomes.CINDER_WASTES, g).flatMap(Group::bossSite));
                }
                return list;
            });
        }

        Site site(int index)
        {
            Optional<Site> site = planned.join().get(index);
            if (site.isEmpty())
            {
                throw new GameTestAssertException("the planner put no Colossus lair in Cinder Wastes group " + (index == 2 ? FAR_GROUP + 1 : index + 1)
                        + " of this world");
            }
            return site.get();
        }

        boolean built(int index)
        {
            return ColossusLair.get(server, site(index)).isPresent();
        }

        /** A stand-in comes within BUILD_RANGE of the group's lair site (unless it's built already: an earlier test came near). */
        void comeNear(int index)
        {
            if (!planned.isDone())
            {
                throw new GameTestAssertException("the planner didn't say where the lairs go within " + PLACEMENT_TIMEOUT_MILLIS / 1000 + " s");
            }
            Site site = site(index);
            deadline = Util.getMillis() + PLACEMENT_TIMEOUT_MILLIS;
            if (built(index))
            {
                LOGGER.info("[lair test] The lair of group {} at {} {} was already built (someone came near it earlier).", index + 1,
                        site.pos().getX(), site.pos().getZ());
                return;
            }
            int x = site.pos().getX() + NEAR_STANDIN_DISTANCE;
            int z = site.pos().getZ();
            int y = dross.getChunkSource().getGenerator().getBaseHeight(x, z, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, dross,
                    dross.getChunkSource().randomState()) + 2;
            players.add(joinPlayer(dross, "lair-near-" + (index + 1), new Vec3(x + 0.5D, y, z + 0.5D)));
            LOGGER.info("[lair test] A stand-in came {} blocks from the lair site of group {} at {} {} (not built yet); waiting for it.",
                    NEAR_STANDIN_DISTANCE, index + 1, site.pos().getX(), site.pos().getZ());
        }

        /** The group's lair: at its planned site, saved with its patch and group, and a well-shaped volcano. */
        void check(int index)
        {
            Site site = site(index);
            ColossusLair.Lair lair = ColossusLair.get(server, site).orElseThrow(() -> new GameTestAssertException(
                    "the lair of group " + (index + 1) + " at " + site.pos().getX() + " " + site.pos().getZ() + " wasn't built within "
                            + PLACEMENT_TIMEOUT_MILLIS / 1000 + " s of a player coming near"));
            if (lair.center().getX() != site.pos().getX() || lair.center().getZ() != site.pos().getZ() || lair.group() != index
                    || lair.id() != site.patch().id())
            {
                throw new GameTestAssertException("the lair of group " + (index + 1) + " is at " + lair.center().toShortString() + " (group "
                        + (lair.group() + 1) + ", patch id " + lair.id() + "), not at its planned site " + site.pos().getX() + " " + site.pos().getZ()
                        + " (patch id " + site.patch().id() + ")");
            }
            ColossusLairData.Entry entry = ColossusLairData.get(server).lair(lair.id());
            Shape shape = new Shape(dross, entry);
            List<String> problems = new ArrayList<>();
            shape.checkFloor(problems);
            shape.checkCeiling(problems);
            shape.checkStalactites(problems);
            shape.checkCrater(problems);
            shape.checkMouth(problems);
            shape.checkWayIn(problems);
            shape.checkShapedLand(problems);
            shape.checkEnclosedAndLavaOutOfReach(problems);
            shape.checkLava(problems);
            shape.checkNoSpawns(problems);
            LOGGER.info("[lair test] Lair of group {}, cut along its way in (left: the far wall, right: the gully), and seen from above "
                    + "at 2 blocks over the floor (# rock, O obsidian, = polished blackstone, g glass, m magma, ~ lava, "
                    + ": ash, n natural ground, o other):{}{}", index + 1, shape.drawCut(), shape.drawPlan());
            LOGGER.info("[lair test] The volcano of group {} at {} {} (floor y {}, rim y {}, high land y {}, mouth {} blocks out to the {}, gully "
                            + "to {}, shaped land to {}) was built in {} ms of work; {}", index + 1, entry.centerX(), entry.centerZ(),
                    entry.floorY(), entry.rimY(), entry.highLand(), entry.mouth(), entry.tunnel().getName(), entry.gullyEnd(), entry.approachEnd(),
                    entry.buildMillis(), problems.isEmpty() ? "every shape check passed" : problems.size() + " problem(s)");
            if (index == 0 && System.getenv(PREVIEW_ENV) != null)
            {
                shape.dumpViews();
            }
            if (!problems.isEmpty())
            {
                throw new GameTestAssertException("the lair of group " + (index + 1) + " isn't right: " + String.join("; ", problems));
            }
        }

        /** Every built lair has its own patch, and the far group's lair (nobody near it) isn't built. */
        void checkOnePerPatchAndFarOneUnbuilt()
        {
            BossSites sites = BossSites.forLevel(dross);
            java.util.Set<Long> patches = new java.util.HashSet<>();
            for (ColossusLair.Lair lair : ColossusLair.all(server))
            {
                Optional<Patch> patch = sites.patchAt(DrossBiomes.CINDER_WASTES, lair.center().getX(), lair.center().getZ());
                if (patch.isEmpty() || patch.get().id() != lair.id() || !patches.add(lair.id()))
                {
                    throw new GameTestAssertException("the lair at " + lair.center().toShortString() + " isn't the only one in its own patch ("
                            + patch.map(Patch::toString).orElse("no patch") + ")");
                }
            }
            Optional<Site> far = planned.join().get(2);
            if (far.isPresent())
            {
                Site site = far.get();
                boolean someoneNear = dross.players().stream().anyMatch(p -> site.distanceTo(p.getBlockX(), p.getBlockZ()) <= BUILD_RANGE);
                if (!someoneNear && (ColossusLair.get(server, site).isPresent() || ColossusLair.isBeingBuilt(site)))
                {
                    throw new GameTestAssertException("the lair of the far group " + (FAR_GROUP + 1) + " at " + site.pos().getX() + " "
                            + site.pos().getZ() + " was built though nobody came near it");
                }
            }
            LOGGER.info("[lair test] {} lair(s) built, each alone in its own patch; the far group's lair isn't built.", patches.size());
        }

        void cleanUp()
        {
            for (ServerPlayer player : players)
            {
                logOut(player);
            }
            players.clear();
        }
    }

    /** Reads the built volcano back from the world. */
    private static final class Shape
    {
        final ServerLevel level;
        final int cx;
        final int cz;
        final int floor;
        final int rimY;
        final int mouth;
        final int gullyEnd;
        /** The last row of the land shaped in front of the mouth. */
        final int approachEnd;
        /** The way in is walked from the land beyond the shaped land (every volcano's way in is planned to meet the land). */
        final boolean planned = true;
        /** The high land round it the rim was planned to stand above, and whether it stands its full rise over it. */
        final int highLand;
        final boolean fullRise;
        final Direction dir;
        final Direction side;

        Shape(ServerLevel level, ColossusLairData.Entry data)
        {
            this.level = level;
            this.cx = data.centerX();
            this.cz = data.centerZ();
            this.floor = data.floorY();
            this.rimY = data.rimY();
            this.mouth = data.mouth();
            this.gullyEnd = data.gullyEnd();
            this.approachEnd = data.approachEnd();
            this.highLand = data.highLand();
            this.fullRise = data.rimY() - data.highLand() >= RIM_RISE;
            this.dir = data.tunnel();
            this.side = dir.getClockWise();
            // Load every chunk the checks read (a height map of an unloaded chunk reads as the bottom of the world).
            int reach = Math.max((int) CONE_BASE_RADIUS + 16, approachEnd + 4);
            for (int x = (cx - reach) >> 4; x <= (cx + reach) >> 4; x++)
            {
                for (int z = (cz - reach) >> 4; z <= (cz + reach) >> 4; z++)
                {
                    level.getChunk(x, z);
                }
            }
        }

        BlockState at(int x, int y, int z)
        {
            return level.getBlockState(new BlockPos(x, y, z));
        }

        int tx(int s, int l)
        {
            return cx + dir.getStepX() * s + side.getStepX() * l;
        }

        int tz(int s, int l)
        {
            return cz + dir.getStepZ() * s + side.getStepZ() * l;
        }

        /** A side view along the way in's line through the middle: rows from the top down. */
        String drawCut()
        {
            StringBuilder out = new StringBuilder();
            for (int y = rimY + 8; y >= floor - UNDER_FLOOR - 1; y--)
            {
                out.append(String.format("%n  %4d ", y));
                for (int s = -(int) CONE_BASE_RADIUS; s <= approachEnd + 1; s++)
                {
                    out.append(glyph(at(tx(s, 0), y, tz(s, 0))));
                }
            }
            return out.toString();
        }

        /** A view from above, 2 blocks over the floor. */
        String drawPlan()
        {
            StringBuilder out = new StringBuilder();
            int reach = mouth + 4;
            for (int dz = -reach; dz <= reach; dz++)
            {
                out.append(String.format("%n  "));
                for (int dx = -reach; dx <= reach; dx++)
                {
                    out.append(glyph(at(cx + dx, floor + 2, cz + dz)));
                }
            }
            return out.toString();
        }

        static char glyph(BlockState state)
        {
            if (state.isAir())
            {
                return '.';
            }
            if (state.getFluidState().is(Fluids.LAVA))
            {
                return '~';
            }
            if (LairBuilder.isLairRock(state))
            {
                return '#';
            }
            if (state.is(Blocks.OBSIDIAN))
            {
                return 'O';
            }
            if (state.is(Blocks.POLISHED_BLACKSTONE) || state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                    || state.is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) || state.is(Blocks.CHISELED_POLISHED_BLACKSTONE))
            {
                return '=';
            }
            if (state.is(LairBuilder.FISSURE_GLASS))
            {
                return 'g';
            }
            if (state.is(Blocks.MAGMA_BLOCK))
            {
                return 'm';
            }
            if (state.is(com.reg21meme.dross.registry.ModBlocks.ASH.get()))
            {
                return ':';
            }
            return state.isSolid() ? 'n' : 'o';
        }

        /**
         * Every floor column is solid underfoot with room above; the cavern ends at the floor's edge all round; the floor
         * is mostly obsidian, with a sparse sprinkling of magma and glowing fissures (glass) in it, and no lava on it.
         */
        void checkFloor(List<String> problems)
        {
            int bad = 0;
            int columns = 0;
            int obsidian = 0;
            int magma = 0;
            int glass = 0;
            for (int dx = -FLOOR_RADIUS; dx <= FLOOR_RADIUS; dx++)
            {
                for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz++)
                {
                    if (dx * dx + dz * dz > LairShape.FLOOR_EDGE * LairShape.FLOOR_EDGE)
                    {
                        continue;
                    }
                    columns++;
                    BlockPos under = new BlockPos(cx + dx, floor, cz + dz);
                    BlockState ground = level.getBlockState(under);
                    if (!ground.isFaceSturdy(level, under, Direction.UP) || !at(cx + dx, floor + 1, cz + dz).isAir()
                            || !at(cx + dx, floor + 2, cz + dz).isAir())
                    {
                        bad++;
                    }
                    obsidian += ground.is(Blocks.OBSIDIAN) ? 1 : 0;
                    magma += ground.is(Blocks.MAGMA_BLOCK) ? 1 : 0;
                    glass += ground.is(LairBuilder.FISSURE_GLASS) ? 1 : 0;
                }
            }
            if (bad > 0)
            {
                problems.add(bad + " of the floor's " + columns + " columns aren't solid floor with room above");
            }
            if (obsidian < columns * MIN_OBSIDIAN_SHARE)
            {
                problems.add("only " + obsidian + " of the floor's " + columns + " blocks are obsidian");
            }
            if (magma < MIN_FLOOR_MAGMA || magma > columns * MAX_MAGMA_SHARE)
            {
                problems.add(magma + " magma blocks on the floor (want " + MIN_FLOOR_MAGMA + " to " + Math.round(columns * MAX_MAGMA_SHARE)
                        + ": some, but sparse)");
            }
            if (glass < MIN_FISSURE_GLASS)
            {
                problems.add("only " + glass + " glass blocks of glowing fissures in the floor");
            }
            // The wall: every 15 degrees (away from the way in), the floor's air ends 20.5 to 23.1 blocks out (it's rough).
            double tunnelAngle = Math.atan2(dir.getStepZ(), dir.getStepX());
            List<String> walls = new ArrayList<>();
            for (int k = 0; k < 24; k++)
            {
                double a = Math.toRadians(k * 15.0D);
                if (Math.abs(Math.IEEEremainder(a - tunnelAngle, 2.0D * Math.PI)) < Math.toRadians(12.0D))
                {
                    continue;
                }
                double reach = -1.0D;
                for (double r = 0.0D; r <= FLOOR_RADIUS + 6; r += 0.5D)
                {
                    int x = cx + (int) Math.round(r * Math.cos(a));
                    int z = cz + (int) Math.round(r * Math.sin(a));
                    if (!at(x, floor + 3, z).isAir())
                    {
                        reach = Math.sqrt(Math.pow(x - cx, 2) + Math.pow(z - cz, 2));
                        break;
                    }
                }
                if (reach < LairShape.FLOOR_EDGE - 0.01D || reach > LairShape.FLOOR_EDGE + 2.6D)
                {
                    walls.add(k * 15 + " degrees: " + (reach < 0 ? "no wall" : String.format("wall at %.1f", reach)));
                }
            }
            if (!walls.isEmpty())
            {
                problems.add("the cavern isn't round (41 across): " + String.join(", ", walls));
            }
        }

        /** Room above every floor column: over the middle at least the lobs' clearance, elsewhere room for the Colossus. */
        void checkCeiling(List<String> problems)
        {
            int lowMiddle = 0;
            int lowAnywhere = 0;
            int least = Integer.MAX_VALUE;
            for (int dx = -FLOOR_RADIUS; dx <= FLOOR_RADIUS; dx++)
            {
                for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r > LairShape.FLOOR_EDGE || r <= SKYLIGHT_RADIUS)
                    {
                        continue;
                    }
                    int room = room(cx + dx, cz + dz);
                    least = Math.min(least, room);
                    if (r <= LOB_CLEARANCE_RADIUS && room < LOB_CLEARANCE)
                    {
                        lowMiddle++;
                    }
                    if (room < STALACTITE_CLEARANCE)
                    {
                        lowAnywhere++;
                    }
                }
            }
            if (lowMiddle > 0)
            {
                problems.add(lowMiddle + " column(s) within " + LOB_CLEARANCE_RADIUS + " of the middle have under " + LOB_CLEARANCE
                        + " blocks of room (the lobs' arcs)");
            }
            if (lowAnywhere > 0)
            {
                problems.add(lowAnywhere + " floor column(s) have under " + STALACTITE_CLEARANCE + " blocks of room (lowest " + least + ")");
            }
        }

        /** Air blocks over the floor in a column, up to the first solid one. */
        int room(int x, int z)
        {
            int room = 0;
            while (room < DOME_HEIGHT + 4 && at(x, floor + 1 + room, z).isAir())
            {
                room++;
            }
            return room;
        }

        /** Obsidian stalactites hang from the dome: tips well below the dome's smooth ceiling, at least a dozen of them. */
        void checkStalactites(List<String> problems)
        {
            int tips = 0;
            for (int dx = -FLOOR_RADIUS; dx <= FLOOR_RADIUS; dx++)
            {
                for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r > LairShape.FLOOR_EDGE - 1.0D || r <= SKYLIGHT_RADIUS + 1.0D)
                    {
                        continue;
                    }
                    int room = room(cx + dx, cz + dz);
                    if (room > LairShape.ceiling(r) - STALACTITE_MIN_LENGTH || !at(cx + dx, floor + 1 + room, cz + dz).is(Blocks.OBSIDIAN))
                    {
                        continue;
                    }
                    boolean lowest = true;
                    for (Direction d : Direction.Plane.HORIZONTAL)
                    {
                        lowest &= room(cx + dx + d.getStepX(), cz + dz + d.getStepZ()) > room;
                    }
                    tips += lowest ? 1 : 0;
                }
            }
            if (tips < MIN_STALACTITE_TIPS)
            {
                problems.add("only " + tips + " stalactite tip(s) hang from the dome (want at least " + MIN_STALACTITE_TIPS + ")");
            }
            LOGGER.info("[lair test] {} stalactite tips hang from the dome.", tips);
        }

        /**
         * The crater: its throat open to the sky over the middle of the floor, open at the rim, and the rim standing well
         * above the land round the volcano's foot (a landmark).
         */
        void checkCrater(List<String> problems)
        {
            int covered = 0;
            for (int dx = -5; dx <= 5; dx++)
            {
                for (int dz = -5; dz <= 5; dz++)
                {
                    if (Math.sqrt(dx * dx + dz * dz) <= SKYLIGHT_RADIUS - 0.5D
                            && level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + dx, cz + dz) != floor + 1)
                    {
                        covered++;
                    }
                }
            }
            if (covered > 0)
            {
                problems.add(covered + " column(s) of the crater's throat aren't open to the sky");
            }
            int shut = 0;
            for (int dx = -9; dx <= 9; dx++)
            {
                for (int dz = -9; dz <= 9; dz++)
                {
                    if (Math.sqrt(dx * dx + dz * dz) <= CRATER_RADIUS - 1.5D && !at(cx + dx, rimY, cz + dz).isAir())
                    {
                        shut++;
                    }
                }
            }
            if (shut > 0)
            {
                problems.add(shut + " block(s) inside the crater at the rim's height");
            }
            List<Integer> crest = new ArrayList<>();
            for (int k = 0; k < 72; k++)
            {
                double a = Math.toRadians(k * 5.0D);
                double rc = (CRATER_RADIUS + RIM_RADIUS) / 2.0D + 0.5D;
                crest.add(level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + (int) Math.round(rc * Math.cos(a)),
                        cz + (int) Math.round(rc * Math.sin(a))) - 1);
            }
            crest.sort(null);
            int crestMedian = crest.get(crest.size() / 2);
            // The land round the foot, measured as the plan measures it (LairShape.landmarkLand, over the ground the builder
            // plans from: the land itself, not the rocks and spires standing on it), so the check and the plan agree.
            int landHigh = LairShape.landmarkLand(cx, cz, LairBuilder.planningGround(level));
            if (crestMedian < rimY - 1)
            {
                problems.add("the rim's crest (median y " + crestMedian + ") isn't at its planned height (y " + rimY + ")");
            }
            if (fullRise && crestMedian - landHigh < MIN_LANDMARK_RISE)
            {
                problems.add("the rim's crest (median y " + crestMedian + ", planned " + rimY + " over high land y " + highLand + ") isn't at least "
                        + MIN_LANDMARK_RISE + " blocks above the land round the foot (y " + landHigh + ")");
            }
            LOGGER.info("[lair test] The crater's rim: median y {}, lowest {}, highest {}; the land round the foot up to y {} ({} below){}.",
                    crestMedian, crest.get(0), crest.get(crest.size() - 1), landHigh, crestMedian - landHigh,
                    fullRise ? "" : " (a lair's smallest form whose rim is capped: only its height is checked)");
        }

        /** The cave mouth at its lip: at least 9 wide at the floor and 8 tall in the middle, with rock over it. */
        void checkMouth(List<String> problems)
        {
            int width = 1;
            for (int sign : new int[] {-1, 1})
            {
                for (int l = sign; Math.abs(l) <= MOUTH_HALF_WIDTH + 3 && at(tx(mouth, l), floor + 1, tz(mouth, l)).isAir(); l += sign)
                {
                    width++;
                }
            }
            int height = 0;
            while (height < MOUTH_HEIGHT + 4 && at(tx(mouth, 0), floor + 1 + height, tz(mouth, 0)).isAir())
            {
                height++;
            }
            boolean roofed = !at(tx(mouth, 0), floor + 1 + height, tz(mouth, 0)).isAir();
            if (!at(tx(mouth, 0), floor + 1, tz(mouth, 0)).isAir() || width < 2 * MOUTH_HALF_WIDTH + 1 || height < MOUTH_HEIGHT || !roofed)
            {
                problems.add("the cave mouth's lip is " + width + " wide and " + height + " tall" + (roofed ? "" : " (no rock over it)")
                        + ", not at least " + (2 * MOUTH_HALF_WIDTH + 1) + " x " + MOUTH_HEIGHT);
            }
            LOGGER.info("[lair test] The cave mouth's lip is {} wide and {} tall.", width, height);
        }

        /**
         * Walks in from the land to the cavern's floor along the middle of the way in and along both sides of the passage:
         * outside the mouth no step higher than a block (and the entrance spot on the way, open to the sky), inside none
         * over 0.6; room for a player all the way, no fluid. A volcano built since R6b is walked from the land just beyond
         * the shaped land (its ground as the builder reads it, {@link LairBuilder#planningGround}), so the last step onto
         * the land counts too; an older one from the blend's last row (its land isn't the builder's to answer for).
         */
        void checkWayIn(List<String> problems)
        {
            for (int l : new int[] {0, -PASSAGE_HALF_WIDTH, PASSAGE_HALF_WIDTH})
            {
                walkIn(l, problems);
            }
        }

        void walkIn(int l, List<String> problems)
        {
            int start;
            double previous;
            if (planned)
            {
                start = approachEnd + 1;
                previous = LairBuilder.planningGround(level).top(tx(start, l), tz(start, l)) + 1;
            }
            else
            {
                start = gullyEnd + APRON + BLEND;
                previous = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, tx(start, l), tz(start, l));
            }
            int steps = 0;
            double highest = 0.0D;
            for (int s = start - 1; s >= FLOOR_RADIUS - 2; s--)
            {
                int x = tx(s, l);
                int z = tz(s, l);
                double here = surface(x, z, (int) Math.floor(previous));
                if (Double.isNaN(here))
                {
                    problems.add("no ground on the way in at " + x + " " + z);
                    return;
                }
                double step = Math.abs(here - previous);
                double allowed = s > mouth ? OUTSIDE_STEP : PLAYER_STEP;
                if (step > allowed + 1.0E-6D)
                {
                    problems.add(String.format("a step of %.1f blocks on the way in at %d %.1f %d (%d blocks out, %d to the side; at most "
                            + "%.1f there)", here - previous, x, here, z, s, l, allowed));
                    return;
                }
                steps += step > 1.0E-6D ? 1 : 0;
                highest = Math.max(highest, step);
                AABB body = new AABB(x + 0.2D, here + 0.01D, z + 0.2D, x + 0.8D, here + 1.8D, z + 0.8D);
                if (!level.noCollision(body) || level.containsAnyLiquid(body))
                {
                    problems.add("no room for a player on the way in at " + x + " " + here + " " + z);
                    return;
                }
                if (l == 0 && s == mouth + ENTRANCE_GAP && (Math.abs(here - (floor + 1)) > 1.0E-6D
                        || level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) > floor + 1))
                {
                    problems.add("the entrance spot at " + x + " " + z + " isn't on the floor's level open to the sky");
                }
                previous = here;
            }
            if (Math.abs(previous - (floor + 1)) > 1.0E-6D)
            {
                problems.add("the way in ends at " + previous + ", not on the floor (" + (floor + 1) + ")");
            }
            LOGGER.info("[lair test] Walked in {} the middle from {} blocks out{}: {} step(s) up or down, the biggest {} block(s).",
                    l == 0 ? "along" : l + " blocks to the side of", start, planned ? " (the land beyond the shaped land)" : "", steps,
                    highest);
        }

        /**
         * The land shaped in front of the mouth (a volcano built since R6b): the apron level with the floor, and no column
         * more than a block above or below its neighbour (north, east, south or west) anywhere on it, so it can be walked
         * every way, not just along the middle.
         */
        void checkShapedLand(List<String> problems)
        {
            if (!planned)
            {
                return;
            }
            double apronHalf = LairShape.gullyHalf(gullyEnd, mouth);
            int reachL = (int) Math.ceil(apronHalf) + BLEND;
            int first = gullyEnd + 1;
            int[][] tops = new int[approachEnd - first + 1][2 * reachL + 1];
            for (int s = first; s <= approachEnd; s++)
            {
                for (int l = -reachL; l <= reachL; l++)
                {
                    tops[s - first][l + reachL] = LairBuilder.groundTop(level, tx(s, l), tz(s, l));
                }
            }
            int bad = 0;
            int biggest = 0;
            String example = null;
            for (int s = first; s <= approachEnd; s++)
            {
                for (int l = -reachL; l <= reachL; l++)
                {
                    int h = tops[s - first][l + reachL];
                    if (LairShape.apronDistance(s, l, gullyEnd + APRON, apronHalf) == 0 && h != floor)
                    {
                        bad++;
                        example = example == null ? String.format("the apron at %d %d %d isn't level with the floor (y %d)", tx(s, l), h,
                                tz(s, l), floor) : example;
                    }
                    int[][] next = {{s + 1, l}, {s, l + 1}};
                    for (int[] n : next)
                    {
                        if (n[0] > approachEnd || n[1] > reachL)
                        {
                            continue;
                        }
                        int step = Math.abs(h - tops[n[0] - first][n[1] + reachL]);
                        biggest = Math.max(biggest, step);
                        if (step > 1)
                        {
                            bad++;
                            example = example == null ? String.format("a step of %d blocks between %d %d %d and %d %d %d", step, tx(s, l), h,
                                    tz(s, l), tx(n[0], n[1]), tops[n[0] - first][n[1] + reachL], tz(n[0], n[1])) : example;
                        }
                    }
                }
            }
            if (bad > 0)
            {
                problems.add(bad + " problem(s) on the land shaped in front of the mouth (e.g. " + example + ")");
            }
            LOGGER.info("[lair test] The land shaped in front of the mouth: rows {} to {} ({} past the apron), {} wide; the biggest step "
                    + "between neighbours {} block(s).", first, approachEnd, approachEnd - gullyEnd - APRON, 2 * reachL + 1, biggest);
        }

        /** The top of whatever a player would stand on in a column, looking down from just above {@code around}. */
        double surface(int x, int z, int around)
        {
            for (int y = around + 2; y >= around - 3; y--)
            {
                BlockPos p = new BlockPos(x, y, z);
                VoxelShape shape = level.getBlockState(p).getCollisionShape(level, p);
                if (!shape.isEmpty())
                {
                    return y + shape.max(Direction.Axis.Y);
                }
            }
            return Double.NaN;
        }

        /**
         * A flood fill of the open space from the middle of the floor: it must never get out of the volcano except up the
         * crater (to the open sky) or out of the mouth, and no lava may touch any of it (players thrown about by the
         * Colossus never land in lava, inside or round the rim).
         */
        void checkEnclosedAndLavaOutOfReach(List<String> problems)
        {
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            LongOpenHashSet seen = new LongOpenHashSet();
            BlockPos start = new BlockPos(cx, floor + 1, cz);
            queue.add(start);
            seen.add(start.asLong());
            int count = 0;
            boolean crater = false;
            boolean entrance = false;
            BlockPos leak = null;
            BlockPos lavaNear = null;
            while (!queue.isEmpty() && count < FLOOD_LIMIT && leak == null)
            {
                BlockPos p = queue.poll();
                count++;
                for (Direction d : Direction.values())
                {
                    if (level.getFluidState(p.relative(d)).is(Fluids.LAVA) && lavaNear == null)
                    {
                        lavaNear = p;
                    }
                }
                int dx = p.getX() - cx;
                int dz = p.getZ() - cz;
                int dy = p.getY() - floor;
                double r = Math.sqrt(dx * dx + dz * dz);
                int s = dx * dir.getStepX() + dz * dir.getStepZ();
                int l = dx * side.getStepX() + dz * side.getStepZ();
                if (dy >= DOME_HEIGHT - 2 && p.getY() >= level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ()))
                {
                    crater = true;
                    continue; // up the crater to the open sky
                }
                boolean inPassage = s >= FLOOR_RADIUS - 3 && Math.abs(l) <= MOUTH_HALF_WIDTH + MOUTH_ROUGHNESS + 1.0D
                        && dy >= 1 && dy <= MOUTH_HEIGHT + 2;
                if (inPassage && s > mouth)
                {
                    entrance = true;
                    continue; // out of the mouth into the gully
                }
                boolean inCavern = r <= LairShape.FLOOR_EDGE + ROUGHNESS + 0.6D && dy >= 1 && dy <= DOME_HEIGHT + ROUGHNESS + 1;
                boolean inCrater = r <= CRATER_RADIUS + CRATER_ROUGHNESS + 1.5D && dy >= 1;
                if (!inCavern && !inCrater && !inPassage)
                {
                    leak = p;
                    break;
                }
                for (Direction d : Direction.values())
                {
                    BlockPos next = p.relative(d);
                    if (seen.add(next.asLong()) && open(next))
                    {
                        queue.add(next);
                    }
                }
            }
            if (leak != null)
            {
                problems.add("the cavern's air leaks out at " + leak.toShortString() + " (not through the crater or the mouth)");
            }
            if (!crater)
            {
                problems.add("the cavern's air never reaches the open sky up the crater");
            }
            if (!entrance)
            {
                problems.add("the cavern's air never reaches the gully through the mouth");
            }
            if (count >= FLOOD_LIMIT)
            {
                problems.add("the flood fill of the cavern didn't finish");
            }
            if (lavaNear != null)
            {
                problems.add("lava touches open space a player can reach inside the volcano, at " + lavaNear.toShortString());
            }
            LOGGER.info("[lair test] The cavern, the way in and the crater hold {} blocks of open space; no lava touches any of it: {}.",
                    count, lavaNear == null);
        }

        boolean open(BlockPos pos)
        {
            BlockState state = level.getBlockState(pos);
            return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
        }

        /**
         * All lava in the volcano's footprint (under the floor and outside on its slopes): no flowing lava, every lava
         * block walled in by Minecraft's flow rule (below and on its four sides: lava, or a block lava can't flow into),
         * lava both under the floor and outside, none in front of the way in, and nothing that burns near any of it.
         */
        void checkLava(List<String> problems)
        {
            int reach = (int) Math.ceil(CONE_BASE_RADIUS) + 2;
            int flowing = 0;
            int loose = 0;
            int under = 0;
            int outside = 0;
            int inWay = 0;
            int burnable = 0;
            String example = null;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    if (dx * dx + dz * dz > reach * reach)
                    {
                        continue;
                    }
                    for (int y = floor - UNDER_FLOOR - 2; y <= rimY + (int) Math.ceil(RIM_TEETH) + 4; y++)
                    {
                        p.set(cx + dx, y, cz + dz);
                        FluidState fluid = level.getFluidState(p);
                        if (!fluid.is(Fluids.LAVA))
                        {
                            continue;
                        }
                        if (!fluid.isSource())
                        {
                            flowing++;
                            example = example == null ? "flowing lava at " + p.toShortString() : example;
                            continue;
                        }
                        if (y == floor - 1 && level.getBlockState(p.above()).is(LairBuilder.FISSURE_GLASS))
                        {
                            under++;
                        }
                        else
                        {
                            outside++;
                            int s = dx * dir.getStepX() + dz * dir.getStepZ();
                            int l = dx * side.getStepX() + dz * side.getStepZ();
                            if (s > mouth && s <= gullyEnd + APRON && Math.abs(l) <= MOUTH_HALF_WIDTH + 1 + (s - mouth) * GULLY_FLARE)
                            {
                                boolean exposed = false;
                                for (Direction d : Direction.values())
                                {
                                    exposed |= open(p.relative(d)) && !level.getFluidState(p.relative(d)).is(Fluids.LAVA);
                                }
                                if (exposed) // (lava buried under the gully's floor, sealed in the ground, is no danger)
                                {
                                    inWay++;
                                    example = example == null ? "lava in the way in at " + p.toShortString() + " (" + (y - floor)
                                            + " above the floor)" : example;
                                }
                            }
                        }
                        for (Direction d : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST})
                        {
                            q.setWithOffset(p, d);
                            BlockState state = level.getBlockState(q);
                            if (state.getFluidState().is(Fluids.LAVA))
                            {
                                continue;
                            }
                            if (LairBuilder.lavaCouldEnter(level, q, state))
                            {
                                loose++;
                                example = example == null ? "lava at " + p.toShortString() + " could flow to " + q.toShortString() + " ("
                                        + state + ")" : example;
                            }
                        }
                        for (int ox = -FIRE_GUARD_REACH; ox <= FIRE_GUARD_REACH; ox++)
                        {
                            for (int oz = -FIRE_GUARD_REACH; oz <= FIRE_GUARD_REACH; oz++)
                            {
                                for (int oy = -1; oy <= FIRE_GUARD_UP; oy++)
                                {
                                    q.set(p.getX() + ox, y + oy, p.getZ() + oz);
                                    if (LairBuilder.burns(level, q, level.getBlockState(q)))
                                    {
                                        burnable++;
                                        example = example == null ? "a block that burns at " + q.toShortString() + " near lava" : example;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (flowing > 0 || loose > 0 || burnable > 0)
            {
                problems.add(flowing + " flowing lava block(s), " + loose + " way(s) for lava to flow and " + burnable
                        + " burnable block(s) near lava (e.g. " + example + ")");
            }
            if (under < MIN_FISSURE_GLASS)
            {
                problems.add("only " + under + " lava block(s) under the floor's glass");
            }
            if (outside < MIN_OUTSIDE_LAVA)
            {
                problems.add("only " + outside + " lava block(s) outside on the volcano (want at least " + MIN_OUTSIDE_LAVA + ")");
            }
            if (inWay > 0)
            {
                problems.add(inWay + " lava block(s) open to the gully or the apron in front of the mouth (e.g. " + example + ")");
            }
            LOGGER.info("[lair test] Lava: {} block(s) under the floor's fissures, {} outside on the volcano; all of it walled in: {}.",
                    under, outside, flowing == 0 && loose == 0);
        }

        /** Natural spawning is barred on the floor and in the passage, where the golems' own spawn rules would allow it. */
        void checkNoSpawns(List<String> problems)
        {
            EntityType<StoneGolem> golem = ModEntities.STONE_GOLEM.get();
            int rx = cx + side.getStepX() * PLAYER_OFFSET;
            int rz = cz + side.getStepZ() * PLAYER_OFFSET;
            BlockPos onFloor = new BlockPos(rx, floor + 1, rz);
            for (int k = 0; k < 8 && !at(rx, floor, rz).is(Blocks.OBSIDIAN) && !at(rx, floor, rz).is(Blocks.BLACKSTONE); k++)
            {
                rx += dir.getStepX(); // golems don't spawn on magma or glass: find a plain floor block
                rz += dir.getStepZ();
                onFloor = new BlockPos(rx, floor + 1, rz);
            }
            BlockPos inPassage = new BlockPos(tx(mouth - 2, 0), floor + 1, tz(mouth - 2, 0));
            for (int l = 1; l <= 2 && !(at(inPassage.getX(), floor, inPassage.getZ()).is(Blocks.OBSIDIAN)
                    || at(inPassage.getX(), floor, inPassage.getZ()).is(Blocks.BLACKSTONE)); l++)
            {
                inPassage = new BlockPos(tx(mouth - 2, l), floor + 1, tz(mouth - 2, l));
            }
            for (BlockPos spot : List.of(onFloor, inPassage))
            {
                boolean rules = WildGolemSpawns.checkStoneGolemSpawn(golem, level, MobSpawnType.NATURAL, spot, level.random);
                boolean allowed = SpawnPlacements.checkSpawnRules(golem, level, MobSpawnType.NATURAL, spot, level.random);
                if (!rules)
                {
                    problems.add("the stone golem's own rules already refuse " + spot.toShortString() + " (the check proves nothing)");
                }
                else if (allowed)
                {
                    problems.add("a stone golem could spawn naturally at " + spot.toShortString() + " inside the lair");
                }
            }
        }

        // ------------------------------------------------------------ the preview images

        /**
         * Writes the volcano's real blocks for tools/golems/lair_preview.py (build/lair/views/): the outside, turned so
         * the mouth faces south (the drawing's camera looks from the south-east), and a cut-away with its east half
         * removed, so the cavern, the crater and the passage show in section.
         */
        void dumpViews()
        {
            int outsideHalf = (int) CONE_BASE_RADIUS + 12;
            dumpView("lair_outside", "The Cinder Colossus's volcano", outsideHalf, floor - 6, rimY + 30, false);
            int cutHalf = Math.max(gullyEnd + APRON + 2, (int) LairShape.SHELL_EDGE + 12);
            dumpView("lair_cutaway", "The volcano cut open: the cavern, the crater and the way in", cutHalf, floor - 5, rimY + 8, true);
        }

        /** Offsets in the turned view (i east, j south when the mouth faces south) to a world column. */
        int[] world(int i, int j)
        {
            Direction east = dir.getCounterClockWise();
            return new int[] {cx + east.getStepX() * i + dir.getStepX() * j, cz + east.getStepZ() * i + dir.getStepZ() * j};
        }

        boolean cutAway(int i, boolean cut)
        {
            return cut && i > 0;
        }

        boolean opaqueAt(int i, int y, int j, boolean cut, int half)
        {
            if (cutAway(i, cut) || i >= half || j >= half)
            {
                return false;
            }
            int[] w = world(i, j);
            BlockPos p = new BlockPos(w[0], y, w[1]);
            return level.getBlockState(p).isSolidRender(level, p);
        }

        void dumpView(String name, String title, int half, int y0, int y1, boolean cut)
        {
            java.util.Map<String, Integer> palette = new java.util.LinkedHashMap<>();
            com.google.gson.JsonArray blocks = new com.google.gson.JsonArray();
            for (int j = -half; j < half; j++)
            {
                for (int i = -half; i < half; i++)
                {
                    if (cutAway(i, cut))
                    {
                        continue;
                    }
                    int[] w = world(i, j);
                    for (int y = y0; y <= y1; y++)
                    {
                        BlockPos p = new BlockPos(w[0], y, w[1]);
                        BlockState state = level.getBlockState(p);
                        if (state.isAir() || opaqueAt(i, y + 1, j, cut, half) && opaqueAt(i, y, j + 1, cut, half)
                                && opaqueAt(i + 1, y, j, cut, half))
                        {
                            continue;
                        }
                        String key = net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state);
                        int index = palette.computeIfAbsent(key, k -> palette.size());
                        int light = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p);
                        for (BlockPos n : new BlockPos[] {p.above(), new BlockPos(world(i, j + 1)[0], y, world(i, j + 1)[1]),
                                new BlockPos(world(i + 1, j)[0], y, world(i + 1, j)[1])})
                        {
                            light = Math.max(light, level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, n));
                        }
                        com.google.gson.JsonArray b = new com.google.gson.JsonArray();
                        b.add(i + half);
                        b.add(y - y0);
                        b.add(j + half);
                        b.add(index);
                        b.add(light);
                        blocks.add(b);
                    }
                }
            }
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("note", "Written by ColossusLairGameTest; drawn by tools/golems/lair_preview.py");
            root.addProperty("name", name);
            root.addProperty("title", title);
            root.addProperty("size", 2 * half);
            root.addProperty("floor", floor - y0);
            root.addProperty("rim", rimY - y0);
            root.addProperty("cut", cut);
            com.google.gson.JsonArray names = new com.google.gson.JsonArray();
            palette.keySet().forEach(names::add);
            root.add("palette", names);
            root.add("blocks", blocks);
            java.nio.file.Path file = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().getParent()
                    .resolve("build").resolve("lair").resolve("views").resolve(name + ".json");
            try
            {
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().create().toJson(root));
                LOGGER.info("[lair test] Wrote {} ({} blocks) for the preview.", file, blocks.size());
            }
            catch (java.io.IOException e)
            {
                LOGGER.warn("[lair test] Couldn't write {}", file, e);
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The Colossus rises once, and never again after it is beaten
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_lair_rise", timeoutTicks = BACKSTOP_TICKS)
    public static void colossusRisesOnceAndNeverAgain(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        Rise run = new Rise(server);
        helper.startSequence()
                .thenExecute(() -> run.guard(run::requestLair))
                .thenWaitUntil(() -> run.guard(() -> run.waitFor(() -> run.lair != null, "a lair to be built")))
                .thenExecute(() -> run.guard(run::begin))
                .thenWaitUntil(() -> run.guard(() -> run.waitFor(() -> run.alive() != null, "the Colossus to rise")))
                .thenExecute(() -> run.guard(run::checkRiseAndKill))
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> run.guard(run::checkStillEmpty))
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> run.guard(run::checkStillEmptyThenNewcomer))
                .thenIdle(2 * QUIET_TICKS)
                .thenExecute(() -> run.guard(run::checkNeverAgain))
                .thenExecute(run::cleanUp)
                .thenSucceed();
    }

    /** The rise test's state (a lair, a stand-in player in its cavern, the first Colossus). */
    private static final class Rise
    {
        final MinecraftServer server;
        ServerLevel dross;
        @Nullable
        ColossusLair.Lair lair;
        ColossusLairData.Entry entry;
        @Nullable
        ServerPlayer player;
        @Nullable
        UUID first;
        long deadline;
        int arrivals;
        boolean failed;

        Rise(MinecraftServer server)
        {
            this.server = server;
        }

        /** Runs a step; on a failure, cleans up first. */
        void guard(Runnable step)
        {
            if (failed)
            {
                return;
            }
            try
            {
                step.run();
            }
            catch (Waiting e)
            {
                throw e;
            }
            catch (RuntimeException e)
            {
                failed = true;
                LOGGER.error("[lair test] {}", e.getMessage());
                cleanUp();
                throw e instanceof GameTestAssertException assertion ? assertion : new GameTestAssertException("the test threw " + e);
            }
        }

        /** The lair nearest the hub (its planned site found on the background thread), built if it isn't yet. */
        void requestLair()
        {
            dross = server.getLevel(ModDimensions.DROSS_LEVEL);
            if (dross == null)
            {
                throw new GameTestAssertException("there's no Dross level");
            }
            deadline = Util.getMillis() + PLACEMENT_TIMEOUT_MILLIS;
            ColossusLair.findNearestSite(dross, com.reg21meme.dross.portal.DrossHub.CENTER).thenAcceptAsync(site -> {
                if (site.isEmpty())
                {
                    LOGGER.error("[lair test] The planner found no Colossus lair site in this world.");
                    return;
                }
                ColossusLair.whenBuilt(server, site.get(), built -> lair = built);
            }, server);
        }

        void waitFor(java.util.function.BooleanSupplier condition, String what)
        {
            if (!condition.getAsBoolean() && Util.getMillis() < deadline)
            {
                throw new Waiting("waiting for " + what);
            }
        }

        void begin()
        {
            if (lair == null)
            {
                throw new GameTestAssertException("no lair was built within " + PLACEMENT_TIMEOUT_MILLIS / 1000 + " s of asking for one");
            }
            entry = ColossusLairData.get(server).lair(lair.id());
            clearColossi();
            ColossusLairData.get(server).resetBossForTests(entry);
            arrive();
            LairKeeper.Volume volume = LairKeeper.volume(lair.id());
            if (volume == null || !volume.inCavern(player.position()))
            {
                throw new GameTestAssertException("the stand-in player at " + player.position() + " isn't in the cavern (or the keeper doesn't know it)");
            }
            LOGGER.info("[lair test] A stand-in player stepped onto the floor of the lair at {}; waiting for the Colossus.", lair.center().toShortString());
        }

        /** A new stand-in steps onto the floor (the one before him, if any, has left). */
        void arrive()
        {
            logOut(player);
            arrivals++;
            Direction side = lair.tunnelDirection().getClockWise();
            Vec3 spot = lair.arenaMiddle().add(side.getStepX() * PLAYER_OFFSET, 0.0D, side.getStepZ() * PLAYER_OFFSET);
            player = joinPlayer(dross, "lair-test-player-" + arrivals, spot);
            deadline = Util.getMillis() + RISE_TIMEOUT_MILLIS;
        }

        /** The lair's living Colossus, or null. */
        @Nullable
        CinderColossus alive()
        {
            UUID id = entry == null ? null : entry.boss();
            return id != null && dross.getEntity(id) instanceof CinderColossus boss && boss.isAlive() ? boss : null;
        }

        void checkRiseAndKill()
        {
            CinderColossus boss = alive();
            if (boss == null)
            {
                throw new GameTestAssertException("no Colossus rose within " + RISE_TIMEOUT_MILLIS / 1000 + " s of a player walking in");
            }
            Vec3 middle = lair.arenaMiddle();
            double off = Math.sqrt(Math.pow(boss.getX() - middle.x, 2) + Math.pow(boss.getZ() - middle.z, 2));
            if (off > RISE_TOLERANCE || Math.abs(boss.getY() - middle.y) > 1.0D)
            {
                throw new GameTestAssertException("the Colossus rose at " + boss.position() + ", not in the middle " + middle);
            }
            if (ColossusLair.state(server, lair) != ColossusLair.State.AWAKE)
            {
                throw new GameTestAssertException("with its Colossus up, the lair's state is " + ColossusLair.state(server, lair) + ", not AWAKE");
            }
            first = boss.getUUID();
            boss.kill();
            if (!entry.beaten() || ColossusLair.state(server, lair) != ColossusLair.State.BEATEN)
            {
                throw new GameTestAssertException("its death wasn't recorded (beaten " + entry.beaten() + ", state "
                        + ColossusLair.state(server, lair) + ")");
            }
            LOGGER.info("[lair test] The Colossus rose in the middle and was killed; checking it never rises again.");
        }

        void checkStillEmpty()
        {
            if (alive() != null || countAlive() > 0)
            {
                throw new GameTestAssertException("a Colossus rose again straight after the last one died");
            }
        }

        void checkStillEmptyThenNewcomer()
        {
            if (alive() != null || countAlive() > 0)
            {
                throw new GameTestAssertException("a Colossus rose again a while later, for the player who stayed");
            }
            arrive(); // someone new walks in
        }

        void checkNeverAgain()
        {
            if (alive() != null || countAlive() > 0)
            {
                throw new GameTestAssertException("a Colossus rose again for a newcomer after the lair's Colossus was beaten");
            }
            if (ColossusLair.state(server, lair) != ColossusLair.State.BEATEN)
            {
                throw new GameTestAssertException("the beaten lair's state is " + ColossusLair.state(server, lair));
            }
            LOGGER.info("[lair test] Beaten, the lair stayed empty for the player who stayed and for a newcomer (no respawn).");
        }

        /** Living Colossi in the cavern. */
        int countAlive()
        {
            return dross.getEntitiesOfClass(CinderColossus.class, cavernBox(), CinderColossus::isAlive).size();
        }

        AABB cavernBox()
        {
            Vec3 middle = lair.arenaMiddle();
            double r = LairShape.FLOOR_EDGE + 4.0D;
            return new AABB(middle.x - r, middle.y - 2.0D, middle.z - r, middle.x + r, middle.y + DOME_HEIGHT + 2.0D, middle.z + r);
        }

        /** Removes every Colossus in the cavern (living or a statue). */
        void clearColossi()
        {
            if (dross != null && lair != null)
            {
                for (CinderColossus boss : dross.getEntitiesOfClass(CinderColossus.class, cavernBox(), b -> !b.isShowcase()))
                {
                    boss.discard();
                }
            }
        }

        /** No Colossus left, the stand-in gone, the lair's boss data as if freshly built. */
        void cleanUp()
        {
            clearColossi();
            if (entry != null)
            {
                ColossusLairData.get(server).resetBossForTests(entry);
            }
            logOut(player);
            player = null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static ServerLevel drossOf(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        return dross;
    }

    /** "Still waiting" in a waiting step: never a failure by itself. */
    private static final class Waiting extends GameTestAssertException
    {
        Waiting(String message)
        {
            super(message);
        }
    }

    private static void logOut(@Nullable ServerPlayer player)
    {
        if (player != null && player.server.getPlayerList().getPlayer(player.getUUID()) == player)
        {
            player.server.getPlayerList().remove(player);
        }
    }

    /**
     * A stand-in player on the server's player list, in creative mode (the Colossus leaves him alone), standing at
     * {@code feet} in the Dross. He gets an in-memory network channel like the other areas' stand-ins; he isn't ticked.
     */
    private static ServerPlayer joinPlayer(ServerLevel dross, String name, Vec3 feet)
    {
        ServerPlayer player = new ServerPlayer(dross.getServer(), dross, new GameProfile(UUID.randomUUID(), name));
        player.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
        dross.getServer().getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.CREATIVE);
        if (player.serverLevel() != dross || player.position().distanceToSqr(feet) > 0.01D)
        {
            player.teleportTo(dross, feet.x, feet.y, feet.z, 0.0F, 0.0F);
        }
        return player;
    }
}
