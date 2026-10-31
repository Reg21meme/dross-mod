package com.reg21meme.dross.boss.fire.cave;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.command.HermitCaveShowcase;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Patch;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless checks of the hermit crab caves ({@link HermitCaveDesigns}, made by tools/fire/hermit_caves.py):
 * <ul>
 *   <li>{@code caveTemplatesMatchTheirTable}: every cave's template is in the mod at its table's size, with 1-3 crab
 *       spots and its way in at its south edge;</li>
 *   <li>{@code showcaseCavesStandAndKeepTheirLava}: {@code /dross showcase hermit_caves} built in the world: every lava
 *       block of every cave is there, still lava and a source a few seconds later, with no flowing lava anywhere round
 *       them; every crab spot's box (7 wide, 6 tall, 7 deep) is free; every cave's darkest floor cell is lit (nothing
 *       spawns there);</li>
 *   <li>{@code cavesArePlannedInCinderWastes}: the Cinder Wastes' first patches each get 1-2 caves, inside the patch, clear
 *       of its Colossus lair and Emberdactyl volcano and of each other, the same when planned again from scratch;</li>
 *   <li>{@code caveIsBuiltInTheWorld}: the cave nearest the hub, generated with the land, is its template block for
 *       block, its lava still and walled in.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HermitCaveGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Where the showcase is built: far from spawn and the other tests, in the air. */
    private static final BlockPos TRY_AT = new BlockPos(-52000, 150, 47000);
    /** Waits are by the clock (the test server ticks as fast as it can). */
    private static final long SETTLE_MILLIS = 5_000L;
    private static final long LIGHT_MILLIS = 60_000L;
    private static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** The Cinder Wastes' first this many patches are planned and checked. */
    private static final int PATCHES = 9;
    /** At least this share of a built cave's template blocks must be in the world as placed (ores may take a few). */
    private static final double MIN_MATCH = 0.97D;

    private HermitCaveGameTest() {}

    // ---------------------------------------------------------------- the templates

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hermit_cave_table")
    public static void caveTemplatesMatchTheirTable(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        if (HermitCaveDesigns.CAVES.size() != 7)
        {
            throw new GameTestAssertException("there should be 7 hermit caves, but there are " + HermitCaveDesigns.CAVES.size());
        }
        for (HermitCaveDesigns.Cave cave : HermitCaveDesigns.CAVES)
        {
            StructureTemplate template = HermitCaves.template(level, cave)
                    .orElseThrow(() -> new GameTestAssertException("the template " + cave.template() + " is missing"));
            Vec3i size = template.getSize();
            if (size.getX() != cave.width() || size.getY() != cave.layers() || size.getZ() != cave.depth())
            {
                throw new GameTestAssertException(cave.template() + " is " + size + ", its table says " + cave.width() + " x "
                        + cave.layers() + " x " + cave.depth());
            }
            if (cave.crabs().isEmpty() || cave.crabs().size() > 3)
            {
                throw new GameTestAssertException(cave.name() + " has " + cave.crabs().size() + " crab spots (1-3 expected)");
            }
            if (cave.doorZ() != cave.depth() - 1)
            {
                throw new GameTestAssertException(cave.name() + "'s way in isn't at its south edge");
            }
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the showcase

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hermit_cave_showcase", timeoutTicks = BACKSTOP_TICKS)
    public static void showcaseCavesStandAndKeepTheirLava(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel().getServer().overworld();
        HermitCaveShowcase.Plan plan = HermitCaveShowcase.plan(level, TRY_AT, Direction.NORTH);
        List<ChunkPos> forced = forceChunks(level, plan);
        for (HermitCaveShowcase.Plot plot : plan.plots())
        {
            HermitCaveShowcase.build(level, plan, plot);
        }
        // straight after: every lava block there, every crab's room free
        List<String> problems = new ArrayList<>();
        for (HermitCaveShowcase.Plot plot : plan.plots())
        {
            checkLava(level, plot.cave(), plot.ground(), plan.front(), problems, false);
            checkCrabs(level, plot.cave(), plot.ground(), plan.front(), problems);
        }
        if (!problems.isEmpty())
        {
            release(level, forced);
            throw new GameTestAssertException("the hermit caves aren't right: " + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
        }
        long settled = Util.getMillis() + SETTLE_MILLIS;
        long lit = Util.getMillis() + LIGHT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (Util.getMillis() < settled)
                    {
                        throw new GameTestAssertException("letting the lava settle");
                    }
                })
                .thenExecute(() -> {
                    List<String> later = new ArrayList<>();
                    for (HermitCaveShowcase.Plot plot : plan.plots())
                    {
                        checkLava(level, plot.cave(), plot.ground(), plan.front(), later, true);
                    }
                    if (!later.isEmpty())
                    {
                        release(level, forced);
                        throw new GameTestAssertException("a few seconds on, the hermit caves' lava isn't right: "
                                + String.join("; ", later.subList(0, Math.min(8, later.size()))));
                    }
                })
                .thenWaitUntil(() -> {
                    List<String> dark = new ArrayList<>();
                    for (HermitCaveShowcase.Plot plot : plan.plots())
                    {
                        BlockPos probe = HermitCaves.toWorld(plot.cave().probe(), plot.ground(), plan.front(), plot.cave());
                        int light = level.getBrightness(LightLayer.BLOCK, probe);
                        if (light < 1)
                        {
                            dark.add(plot.cave().name() + "'s darkest floor cell " + probe.toShortString() + " has block light " + light);
                        }
                    }
                    if (!dark.isEmpty())
                    {
                        if (Util.getMillis() < lit)
                        {
                            throw new GameTestAssertException("waiting for the light: " + dark.get(0));
                        }
                        release(level, forced);
                        throw new GameTestAssertException(String.join("; ", dark));
                    }
                })
                .thenExecute(() -> {
                    release(level, forced);
                    LOGGER.info("[hermit caves] the showcase's {} caves stand, their lava still, every floor lit", plan.plots().size());
                })
                .thenSucceed();
    }

    private static List<ChunkPos> forceChunks(ServerLevel level, HermitCaveShowcase.Plan plan)
    {
        List<ChunkPos> forced = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (HermitCaveShowcase.Plot plot : plan.plots())
        {
            BoundingBox box = HermitCaves.footprint(plot.ground(), plan.front(), plot.cave()).inflatedBy(8);
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
            {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                {
                    if (seen.add(ChunkPos.asLong(cx, cz)))
                    {
                        level.setChunkForced(cx, cz, true);
                        level.getChunk(cx, cz);
                        forced.add(new ChunkPos(cx, cz));
                    }
                }
            }
        }
        return forced;
    }

    private static void release(ServerLevel level, List<ChunkPos> forced)
    {
        forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
    }

    /**
     * Every lava block of the cave's template is lava in the world (a source); with {@code still}, also no flowing lava
     * anywhere in its box (it would be spilling).
     */
    static void checkLava(ServerLevel level, HermitCaveDesigns.Cave cave, BlockPos ground, Direction front, List<String> problems,
                          boolean still)
    {
        StructureTemplate template = HermitCaves.template(level, cave).orElseThrow();
        BlockPos origin = HermitCaves.origin(ground, cave);
        StructurePlaceSettings settings = HermitCaves.settings(cave, front);
        List<StructureTemplate.StructureBlockInfo> lava = template.filterBlocks(origin, settings, Blocks.LAVA);
        if (lava.size() != cave.lava())
        {
            problems.add(cave.name() + "'s template has " + lava.size() + " lava blocks, its table says " + cave.lava());
        }
        for (StructureTemplate.StructureBlockInfo info : lava)
        {
            FluidState fluid = level.getFluidState(info.pos());
            if (!fluid.is(Fluids.LAVA))
            {
                problems.add(cave.name() + ": no lava at " + info.pos().toShortString() + " (" + level.getBlockState(info.pos()) + ")");
                return;
            }
        }
        if (still)
        {
            BoundingBox box = HermitCaves.footprint(ground, front, cave).inflatedBy(2);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = box.minX(); x <= box.maxX(); x++)
            {
                for (int y = box.minY(); y <= box.maxY(); y++)
                {
                    for (int z = box.minZ(); z <= box.maxZ(); z++)
                    {
                        FluidState fluid = level.getFluidState(pos.set(x, y, z));
                        if (fluid.is(Fluids.FLOWING_LAVA))
                        {
                            problems.add(cave.name() + ": lava is flowing at " + pos.toShortString());
                            return;
                        }
                    }
                }
            }
        }
    }

    /** Every crab spot's box (7 wide, 6 tall, 7 deep round its feet) has nothing in it a crab would bump into. */
    static void checkCrabs(ServerLevel level, HermitCaveDesigns.Cave cave, BlockPos ground, Direction front, List<String> problems)
    {
        for (BlockPos spot : cave.crabs())
        {
            BlockPos feet = HermitCaves.toWorld(spot, ground, front, cave);
            if (level.getBlockState(feet.below()).getCollisionShape(level, feet.below()).isEmpty())
            {
                problems.add(cave.name() + ": the crab spot " + feet.toShortString() + " has no floor");
            }
            for (int dx = -3; dx <= 3; dx++)
            {
                for (int dz = -3; dz <= 3; dz++)
                {
                    for (int dy = 0; dy < 6; dy++)
                    {
                        BlockPos at = feet.offset(dx, dy, dz);
                        BlockState state = level.getBlockState(at);
                        if (!state.getCollisionShape(level, at).isEmpty())
                        {
                            problems.add(cave.name() + ": " + state + " at " + at.toShortString() + " in the crab spot round "
                                    + feet.toShortString());
                            return;
                        }
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- in the world

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

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hermit_cave_plan", timeoutTicks = BACKSTOP_TICKS)
    public static void cavesArePlannedInCinderWastes(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<List<String>> checked = CompletableFuture.supplyAsync(() -> checkPlans(dross), Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(checked, deadline, "the hermit caves to be planned"))
                .thenExecute(() -> {
                    List<String> problems = checked.join();
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the hermit caves aren't planned right: "
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
        int caves = 0;
        int seconds = 0;
        int rolled = 0;
        for (Patch patch : sites.patches(DrossBiomes.CINDER_WASTES, PATCHES))
        {
            List<HermitCaveSites.CavePlan> plan = HermitCaveSites.plan(sites, patch);
            caves += plan.size();
            seconds += plan.size() > 1 ? 1 : 0;
            rolled += HermitCaveSites.wantsTwo(sites, patch) ? 1 : 0;
            if (plan.isEmpty() && patch.depth() >= 2 * HermitCaveTuning.MIN_DEPTH)
            {
                problems.add(patch + " (" + Math.round(patch.depth()) + " deep) has no cave");
            }
            if (plan.size() > 2)
            {
                problems.add(patch + " has " + plan.size() + " caves");
            }
            List<BlockPos> bosses = new ArrayList<>();
            sites.site(BossSites.COLOSSUS_LAIR, patch).map(Site::pos).ifPresent(bosses::add);
            sites.site(BossSites.EMBER_VOLCANO, patch).map(Site::pos).ifPresent(bosses::add);
            for (HermitCaveSites.CavePlan cave : plan)
            {
                BlockPos m = cave.middle();
                if (!patch.contains(m.getX(), m.getZ()) || patch.depthAt(m.getX(), m.getZ()) < HermitCaveTuning.RELAXED_MIN_DEPTH)
                {
                    problems.add("the cave at " + m.toShortString() + " isn't well inside " + patch);
                }
                for (BlockPos boss : bosses)
                {
                    if (Math.hypot(boss.getX() - m.getX(), boss.getZ() - m.getZ()) < HermitCaveTuning.RELAXED_BOSS_CLEARANCE)
                    {
                        problems.add("the cave at " + m.toShortString() + " is too near the boss site at " + boss.toShortString());
                    }
                }
                if (HermitCaveSites.caveAt(sites, cave.chunk()).filter(c -> c.equals(cave)).isEmpty())
                {
                    problems.add("the cave at " + m.toShortString() + " isn't found from its own chunk");
                }
            }
            if (plan.size() == 2 && Math.hypot(plan.get(0).middle().getX() - plan.get(1).middle().getX(),
                    plan.get(0).middle().getZ() - plan.get(1).middle().getZ()) < HermitCaveTuning.CAVE_SPACING)
            {
                problems.add(patch + "'s two caves are too close together");
            }
            Optional<Patch> again = fresh.patch(DrossBiomes.CINDER_WASTES, patch.number());
            if (again.isEmpty() || !HermitCaveSites.plan(fresh, again.get()).equals(plan))
            {
                problems.add(patch + "'s caves come out differently when planned again");
            }
        }
        LOGGER.info("[hermit caves] {} caves in the Cinder Wastes' first {} patches ({} with two, {} rolled a second); problems: {}",
                caves, PATCHES, seconds, rolled, problems.size());
        if (caves < PATCHES)
        {
            problems.add("only " + caves + " caves in " + PATCHES + " patches");
        }
        return problems;
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hermit_cave_world", timeoutTicks = BACKSTOP_TICKS)
    public static void caveIsBuiltInTheWorld(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<Optional<HermitCaveSites.CavePlan>> planned = CompletableFuture.supplyAsync(
                () -> HermitCaveSites.nearest(BossSites.forLevel(dross), BlockPos.ZERO, HermitCaveTuning.GOTO_SEARCH_RADIUS),
                Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        Object[] built = new Object[2];
        long[] settled = new long[1];
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(planned, deadline, "the nearest hermit cave to be planned"))
                .thenExecute(() -> {
                    HermitCaveSites.CavePlan plan = planned.join()
                            .orElseThrow(() -> new GameTestAssertException("no hermit cave within 12,000 blocks of the hub"));
                    long started = Util.getMillis();
                    BlockPos ground = HermitCaveStructure.ground(plan, dross.getChunkSource().getGenerator(), dross,
                            dross.getChunkSource().randomState());
                    BoundingBox box = HermitCaves.footprint(ground, plan.front(), plan.cave()).inflatedBy(16);
                    for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
                    {
                        for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                        {
                            dross.setChunkForced(cx, cz, true);
                            dross.getChunk(cx, cz);
                        }
                    }
                    built[0] = plan;
                    built[1] = ground;
                    List<String> problems = new ArrayList<>();
                    String stats = checkBuilt(dross, plan, ground, problems);
                    checkLava(dross, plan.cave(), ground, plan.front(), problems, false);
                    LOGGER.info("[hermit caves] the {} at {} facing {}: {} ({} ms to generate and check)", plan.cave().name(),
                            ground.toShortString(), plan.front().getName(), stats, Util.getMillis() - started);
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the hermit cave at " + ground.toShortString() + " isn't right: "
                                + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
                    }
                    settled[0] = Util.getMillis() + SETTLE_MILLIS;
                })
                .thenWaitUntil(() -> {
                    if (Util.getMillis() < settled[0])
                    {
                        throw new GameTestAssertException("letting the lava settle");
                    }
                })
                .thenExecute(() -> {
                    HermitCaveSites.CavePlan plan = (HermitCaveSites.CavePlan) built[0];
                    BlockPos ground = (BlockPos) built[1];
                    List<String> problems = new ArrayList<>();
                    checkLava(dross, plan.cave(), ground, plan.front(), problems, true);
                    BoundingBox box = HermitCaves.footprint(ground, plan.front(), plan.cave()).inflatedBy(16);
                    for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
                    {
                        for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                        {
                            dross.setChunkForced(cx, cz, false);
                        }
                    }
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("a few seconds on, the hermit cave's lava isn't right: "
                                + String.join("; ", problems));
                    }
                })
                .thenSucceed();
    }

    /** At least {@link #MIN_MATCH} of the template's blocks (of its main kinds) are in the world as placed. */
    private static String checkBuilt(ServerLevel dross, HermitCaveSites.CavePlan plan, BlockPos ground, List<String> problems)
    {
        HermitCaveDesigns.Cave cave = plan.cave();
        StructureTemplate template = HermitCaves.template(dross, cave).orElseThrow();
        BlockPos origin = HermitCaves.origin(ground, cave);
        StructurePlaceSettings settings = HermitCaves.settings(cave, plan.front());
        int total = 0;
        int same = 0;
        BlockPos firstWrong = null;
        for (net.minecraft.world.level.block.Block block : List.of(Blocks.LAVA, Blocks.BASALT, Blocks.BLACKSTONE, Blocks.AIR,
                Blocks.MAGMA_BLOCK, Blocks.SMOOTH_BASALT, Blocks.POLISHED_BASALT))
        {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, block))
            {
                total++;
                if (dross.getBlockState(info.pos()).is(block))
                {
                    same++;
                }
                else if (firstWrong == null)
                {
                    firstWrong = info.pos();
                }
            }
        }
        double share = total == 0 ? 0.0D : same / (double) total;
        if (share < MIN_MATCH)
        {
            problems.add(String.format("only %.1f%% of its %d template blocks are there (the first wrong at %s: %s)", 100.0D * share,
                    total, firstWrong == null ? "-" : firstWrong.toShortString(),
                    firstWrong == null ? "-" : dross.getBlockState(firstWrong)));
        }
        return String.format("%d of %d sampled template blocks in place", same, total);
    }
}
