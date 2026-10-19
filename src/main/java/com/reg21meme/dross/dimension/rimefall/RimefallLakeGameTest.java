package com.reg21meme.dross.dimension.rimefall;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.rimefall.Rimehorn;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Patch;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless checks (GameTests) of Rimefall's big frozen lakes and its Rimehorn herds (the user's requests of October 19,
 * 2026; {@link RimefallAreas}):
 * <ul>
 *   <li>{@link #rimefallAreasArePlanned}: Rimefall's nearest patches each get a lake (every patch with room for one),
 *       inside the patch with its shore, about 100 blocks across (a smaller one where there's no room), with 4-6 holes,
 *       and 3 (a big patch 4) herd spots well apart and away from the lake; a fresh plan of the seed agrees.</li>
 *   <li>{@link #rimefallLakeIsBuilt}: the nearest lake, generated: a sheet of ice (and open holes) at its level with water
 *       under it, nothing standing on it, the holes never freezing, the water walled in by its shore; Pond Skaters may
 *       spawn on it until 2 are there.</li>
 *   <li>{@link #rimehornHerdsAreThere}: a herd stands at a planned herd spot once its chunk is made (6-8 Rimehorns, the
 *       first two males).</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RimefallLakeGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** Rimefall's nearest this many patches are planned and checked. */
    private static final int PATCHES = 9;
    /** A lake must be at least this share of the small lake's width across and at most this share of the big one's. */
    private static final double MIN_ACROSS = 0.8D * 2.0D * RimefallTuning.LAKE_SMALL_RADIUS;
    private static final double MAX_ACROSS = 1.25D * 2.0D * RimefallTuning.LAKE_RADIUS;
    /** At least this many of a generated lake's columns are open water (its holes). */
    private static final int MIN_HOLE_COLUMNS = 12;

    private RimefallLakeGameTest() {}

    private static ServerLevel dross(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        return dross;
    }

    private static void waitFor(CompletableFuture<?> future, long deadline, String what)
    {
        if (!future.isDone() && Util.getMillis() < deadline)
        {
            throw new GameTestAssertException("waiting for " + what);
        }
        if (!future.isDone())
        {
            throw new GameTestAssertException("gave up waiting for " + what);
        }
    }

    // ---------------------------------------------------------------- the plan

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_rimefall_areas", timeoutTicks = BACKSTOP_TICKS)
    public static void rimefallAreasArePlanned(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<List<String>> checked = CompletableFuture.supplyAsync(() -> checkPlans(dross), Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(checked, deadline, "Rimefall's areas to be planned"))
                .thenExecute(() -> {
                    List<String> problems = checked.join();
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("Rimefall's lakes and herds aren't right: "
                                + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
                    }
                })
                .thenSucceed();
    }

    private static List<String> checkPlans(ServerLevel dross)
    {
        List<String> problems = new ArrayList<>();
        BossSites sites = BossSites.forLevel(dross);
        BossSites fresh = BossSites.forSeed(dross, dross.getSeed());
        List<Patch> patches = sites.patches(DrossBiomes.RIMEFALL, PATCHES);
        if (patches.isEmpty())
        {
            problems.add("the planner found no Rimefall patch");
            return problems;
        }
        double needSmall = RimefallLake.reach(RimefallTuning.LAKE_SMALL_RADIUS) + RimefallTuning.SHORE_WIDTH + RimefallTuning.EDGE_MARGIN;
        int lakes = 0;
        int big = 0;
        for (Patch patch : patches)
        {
            long started = Util.getMillis();
            Optional<Site> site = sites.site(BossSites.RIMEFALL_AREA, patch);
            long millis = Util.getMillis() - started;
            if (site.isEmpty() || !(site.get().detail() instanceof RimefallAreas.RimefallArea area))
            {
                problems.add("Rimefall patch " + patch.number() + " has no plan");
                continue;
            }
            RimefallLake lake = area.lake();
            String name = "Rimefall patch " + patch.number();
            if (lake == null)
            {
                if (patch.depth() + 8.0D >= needSmall + 16.0D)
                {
                    problems.add(name + " is " + Math.round(patch.depth()) + " deep (room for a lake) but has none");
                }
            }
            else
            {
                lakes++;
                big += lake.radius() >= RimefallTuning.LAKE_RADIUS ? 1 : 0;
                double across = 2.0D * lake.radius();
                if (across < MIN_ACROSS || across > MAX_ACROSS)
                {
                    problems.add(name + "'s lake is " + Math.round(across) + " across");
                }
                if (lake.holeCount() < RimefallTuning.HOLES_MIN || lake.holeCount() > RimefallTuning.HOLES_MAX)
                {
                    problems.add(name + "'s lake has " + lake.holeCount() + " holes");
                }
                for (int i = 0; i < 48; i++)
                {
                    double a = Math.PI * 2.0D * i / 48;
                    double r = lake.edge(a) + RimefallTuning.SHORE_WIDTH;
                    int x = (int) Math.round(lake.centre().getX() + Math.cos(a) * r);
                    int z = (int) Math.round(lake.centre().getZ() + Math.sin(a) * r);
                    if (!patch.contains(x, z))
                    {
                        problems.add(name + "'s lake's shore reaches out of the patch at " + x + ", " + z);
                        break;
                    }
                }
                for (int i = 0; i < lake.holeCount(); i++)
                {
                    BlockPos h = lake.hole(i);
                    if (!lake.inLake(h.getX(), h.getZ()) || !lake.isHole(h.getX(), h.getZ()))
                    {
                        problems.add(name + "'s lake's hole " + (i + 1) + " isn't in it");
                    }
                }
            }
            // 2 herds, 3 a quarter of the time; a small patch 1 or 2 (it may fit fewer, never none)
            int base = patch.area() >= RimefallTuning.HERDS_SMALL_AREA ? RimefallTuning.HERDS : RimefallTuning.HERDS - 1;
            List<BlockPos> herds = area.herds();
            if (herds.isEmpty() || herds.size() > base + 1)
            {
                problems.add(name + " (" + patch.area() + " blocks²) has " + herds.size() + " herds (" + base + " or " + (base + 1) + " wanted)");
            }
            for (int i = 0; i < herds.size(); i++)
            {
                BlockPos h = herds.get(i);
                if (!patch.contains(h.getX(), h.getZ()))
                {
                    problems.add(name + "'s herd " + (i + 1) + " is outside it");
                }
                if (lake != null && lake.outside(h.getX(), h.getZ()) < RimefallTuning.SHORE_WIDTH + RimefallTuning.HERD_LAKE_CLEARANCE - 0.5D)
                {
                    problems.add(name + "'s herd " + (i + 1) + " is by its lake");
                }
                for (int j = 0; j < i; j++)
                {
                    if (h.distSqr(herds.get(j).atY(h.getY())) < (double) RimefallTuning.HERD_SPACING * RimefallTuning.HERD_SPACING - 1.0D)
                    {
                        problems.add(name + "'s herds " + (j + 1) + " and " + (i + 1) + " are too close");
                    }
                }
            }
            Optional<Site> again = fresh.patch(DrossBiomes.RIMEFALL, patch.number()).flatMap(p -> fresh.site(BossSites.RIMEFALL_AREA, p));
            String a = describe(area);
            String b = again.map(s -> describe((RimefallAreas.RimefallArea) s.detail())).orElse("none");
            if (!a.equals(b))
            {
                problems.add(name + " is planned differently a second time: " + a + " / " + b);
            }
            LOGGER.info("[rimefall areas] patch {} ({} blocks², {} deep): {}; planned in {} ms", patch.number(), patch.area(),
                    Math.round(patch.depth()), a, millis);
        }
        LOGGER.info("[rimefall areas] {} of Rimefall's {} nearest patches have a lake ({} big)", lakes, patches.size(), big);
        if (lakes == 0)
        {
            problems.add("none of Rimefall's " + patches.size() + " nearest patches has a lake");
        }
        return problems;
    }

    private static String describe(RimefallAreas.RimefallArea area)
    {
        return (area.lake() == null ? "no lake" : area.lake().toString()) + ", herds at "
                + area.herds().stream().map(p -> p.getX() + "/" + p.getZ()).toList();
    }

    // ---------------------------------------------------------------- the lake, built

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_rimefall_lake", timeoutTicks = BACKSTOP_TICKS)
    public static void rimefallLakeIsBuilt(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<Optional<RimefallLake>> planned = CompletableFuture.supplyAsync(() -> firstLake(dross), Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(planned, deadline, "the nearest Rimefall lake to be planned"))
                .thenExecute(() -> {
                    RimefallLake lake = planned.join().orElseThrow(() -> new GameTestAssertException("no Rimefall patch near the hub has a lake"));
                    long started = Util.getMillis();
                    ChunkPos startChunk = new ChunkPos(lake.centre());
                    StructureStart start = startAt(dross, startChunk);
                    BoundingBox box = start.getBoundingBox();
                    for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
                    {
                        for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                        {
                            dross.getChunk(cx, cz);
                        }
                    }
                    List<String> problems = new ArrayList<>();
                    String stats = checkLake(dross, lake, problems);
                    LOGGER.info("[rimefall lake] {}: {} ({} ms to generate and check)", lake, stats, Util.getMillis() - started);
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the Rimefall lake at " + lake.centre().toShortString() + " isn't right: "
                                + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
                    }
                })
                .thenSucceed();
    }

    /** The lake of Rimefall's nearest patch that has one. */
    private static Optional<RimefallLake> firstLake(ServerLevel dross)
    {
        BossSites sites = BossSites.forLevel(dross);
        for (Patch patch : sites.patches(DrossBiomes.RIMEFALL, PATCHES))
        {
            Optional<RimefallLake> lake = sites.site(BossSites.RIMEFALL_AREA, patch)
                    .map(s -> ((RimefallAreas.RimefallArea) s.detail()).lake());
            if (lake.isPresent())
            {
                return lake;
            }
        }
        return Optional.empty();
    }

    private static StructureStart startAt(ServerLevel dross, ChunkPos chunkPos)
    {
        Structure structure = dross.registryAccess().registryOrThrow(Registries.STRUCTURE).get(RimefallAreas.LAKE_STRUCTURE);
        ChunkAccess chunk = dross.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = structure == null ? null : dross.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
        if (start == null || !start.isValid())
        {
            throw new GameTestAssertException("no " + RimefallAreas.LAKE_STRUCTURE.location() + " starts in its planned chunk " + chunkPos);
        }
        return start;
    }

    private static String checkLake(ServerLevel dross, RimefallLake lake, List<String> problems)
    {
        int ice = lake.iceY();
        int r = (int) Math.ceil(lake.reach()) + RimefallTuning.SHORE_WIDTH;
        int cx = lake.centre().getX();
        int cz = lake.centre().getZ();
        int lakeColumns = 0;
        int iceColumns = 0;
        int holeColumns = 0;
        int covered = 0;
        int leaks = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        BlockPos aHole = null;
        BlockPos anIce = null;
        for (int x = cx - r; x <= cx + r; x++)
        {
            for (int z = cz - r; z <= cz + r; z++)
            {
                double d = lake.outside(x, z);
                if (d < 0.0D)
                {
                    lakeColumns++;
                    BlockState top = dross.getBlockState(at.set(x, ice, z));
                    boolean open = top.is(Blocks.LIGHT) && top.getFluidState().is(Fluids.WATER);
                    if (top.is(Blocks.ICE))
                    {
                        iceColumns++;
                        if (anIce == null && d < -6.0D && !lake.isHole(x, z))
                        {
                            anIce = new BlockPos(x, ice, z);
                        }
                    }
                    else if (open)
                    {
                        holeColumns++;
                        aHole = aHole == null ? new BlockPos(x, ice, z) : aHole;
                    }
                    else
                    {
                        problems.add(String.format(Locale.ROOT, "%d, %d, %d on the lake is %s (ice or open water expected)", x, ice, z, top));
                    }
                    if (!dross.getFluidState(at.set(x, ice - 1, z)).is(Fluids.WATER))
                    {
                        problems.add(String.format(Locale.ROOT, "no water under the ice at %d, %d, %d", x, ice - 1, z));
                    }
                    for (int y = ice + 1; y <= ice + 12; y++)
                    {
                        BlockState above = dross.getBlockState(at.set(x, y, z));
                        if (!above.isAir())
                        {
                            covered++;
                            if (covered <= 3)
                            {
                                problems.add(String.format(Locale.ROOT, "%s stands on the lake at %d, %d, %d", above.getBlock().getName().getString(), x, y, z));
                            }
                            break;
                        }
                    }
                }
                else if (d < 1.5D)
                {
                    // the wall that holds the water in: solid at the water's level and the ice's
                    for (int y = ice - 1; y <= ice; y++)
                    {
                        BlockState wall = dross.getBlockState(at.set(x, y, z));
                        if (wall.isAir() || !wall.getFluidState().isEmpty())
                        {
                            leaks++;
                            if (leaks <= 3)
                            {
                                problems.add(String.format(Locale.ROOT, "the shore at %d, %d, %d is %s (the water could get out)", x, y, z,
                                        wall.getBlock().getName().getString()));
                            }
                        }
                    }
                }
            }
        }
        if (covered > 3)
        {
            problems.add(covered + " lake columns have something standing on them");
        }
        if (holeColumns < MIN_HOLE_COLUMNS)
        {
            problems.add("only " + holeColumns + " columns of open water (its " + lake.holeCount() + " holes)");
        }
        if (aHole != null && dross.getBiome(aHole).value().shouldFreeze(dross, aHole))
        {
            problems.add("its holes would freeze over (" + aHole.toShortString() + ")");
        }
        // Pond Skaters: on the lake while fewer than its cap (1 or 2) are on it
        String skaters = "no ice to try a Pond Skater on";
        int cap = lake.skaterCap();
        if (cap < RimefallTuning.SKATERS_PER_LAKE || cap > RimefallTuning.SKATERS_PER_LAKE + 1)
        {
            problems.add("its cap is " + cap + " Pond Skaters");
        }
        if (anIce != null)
        {
            BlockPos feet = anIce.above();
            boolean first = RimefallAreas.skaterMayAppear(dross, feet);
            List<net.minecraft.world.entity.Entity> placed = new ArrayList<>();
            for (int i = 0; i < cap; i++)
            {
                var skater = ModEntities.POND_SKATER.get().create(dross);
                if (skater != null)
                {
                    skater.setNoAi(true);
                    skater.moveTo(feet.getX() + 0.5D + 3 * i, feet.getY(), feet.getZ() + 0.5D, 0.0F, 0.0F);
                    dross.addFreshEntity(skater);
                    placed.add(skater);
                }
            }
            boolean third = RimefallAreas.skaterMayAppear(dross, feet);
            placed.forEach(net.minecraft.world.entity.Entity::discard);
            if (!first || third)
            {
                problems.add("a Pond Skater should spawn on the lake (it may: " + first + ") until " + cap
                        + " are on it (then it may: " + third + ")");
            }
            skaters = "Pond Skaters may spawn on it until " + cap + " are there (its cap)";
        }
        return String.format(Locale.ROOT, "%d lake columns (%d ice, %d open water), the shore holds; %s", lakeColumns, iceColumns,
                holeColumns, skaters);
    }

    // ---------------------------------------------------------------- the herds

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_rimehorn_herds", timeoutTicks = BACKSTOP_TICKS)
    public static void rimehornHerdsAreThere(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<Optional<BlockPos>> planned = CompletableFuture.supplyAsync(() -> {
            BossSites sites = BossSites.forLevel(dross);
            for (Patch patch : sites.patches(DrossBiomes.RIMEFALL, PATCHES))
            {
                Optional<BlockPos> spot = sites.site(BossSites.RIMEFALL_AREA, patch)
                        .flatMap(s -> ((RimefallAreas.RimefallArea) s.detail()).herds().stream().findFirst());
                if (spot.isPresent())
                {
                    return spot;
                }
            }
            return Optional.empty();
        }, Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        ChunkPos[] chunk = new ChunkPos[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(planned, deadline, "the nearest Rimehorn herd to be planned"))
                .thenExecute(() -> {
                    BlockPos spot = planned.join().orElseThrow(() -> new GameTestAssertException("no Rimefall patch near the hub has a herd"));
                    chunk[0] = new ChunkPos(spot);
                    dross.setChunkForced(chunk[0].x, chunk[0].z, true);
                    dross.getChunk(chunk[0].x, chunk[0].z);
                })
                .thenWaitUntil(() -> {
                    List<Rimehorn> herd = herdIn(dross, chunk[0]);
                    if (herd.isEmpty() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("waiting for the herd at " + chunk[0] + " to load");
                    }
                })
                .thenExecute(() -> {
                    List<Rimehorn> herd = herdIn(dross, chunk[0]);
                    dross.setChunkForced(chunk[0].x, chunk[0].z, false);
                    long males = herd.stream().filter(Rimehorn::isMale).count();
                    LOGGER.info("[rimehorn herds] the herd at {}: {} Rimehorns, {} males", chunk[0], herd.size(), males);
                    if (herd.size() < RimefallTuning.HERD_MIN - 1 || herd.size() > RimefallTuning.HERD_MAX || males < 1 || males > 2)
                    {
                        throw new GameTestAssertException("the herd at " + chunk[0] + " has " + herd.size() + " Rimehorns (" + males
                                + " males); " + RimefallTuning.HERD_MIN + "-" + RimefallTuning.HERD_MAX + ", the first two males, expected");
                    }
                })
                .thenSucceed();
    }

    private static List<Rimehorn> herdIn(ServerLevel dross, ChunkPos chunk)
    {
        AABB box = new AABB(chunk.getMinBlockX() - 8, dross.getMinBuildHeight(), chunk.getMinBlockZ() - 8, chunk.getMaxBlockX() + 9,
                dross.getMaxBuildHeight(), chunk.getMaxBlockZ() + 9);
        return dross.getEntitiesOfClass(Rimehorn.class, box, Rimehorn::isAlive);
    }
}
