package com.reg21meme.dross.boss.fire.cave;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.cinder.CinderHermit;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless checks of the hermit crab caves ({@link HermitCaveDesigns}, made by tools/fire/hermit_caves.py):
 * <ul>
 *   <li>{@code caveTemplatesMatchTheirTable}: every cave's template is in the mod at its table's size, with exactly one
 *       crab spot (the user, November 2, 2026: "only 1 per cave") and its way in at its south edge; there are hillside
 *       and underground ones; a hillside one knows its rooms and how high its shell stands; an underground one's barrel
 *       and suspicious gravel point at loot tables that exist;</li>
 *   <li>{@code showcaseCavesStandAndKeepTheirLava}: {@code /dross showcase hermit_caves} built in the world: every lava
 *       block of every cave is there, still lava and a source a few seconds later, with no flowing lava anywhere round
 *       them; the crab spot's box (7 wide, 6 tall, 7 deep) is free and one Cinder Hermit is in each cave; every cave's
 *       darkest floor cell is lit (nothing spawns there);</li>
 *   <li>{@code cavesArePlannedInCinderWastes}: the Cinder Wastes' first patches each get 1-2 caves, inside the patch, clear
 *       of its Colossus lair and Emberdactyl volcano and of each other, the same when planned again from scratch; every
 *       hillside cave where the real ground (the generator's top blocks) over its den's middle rises at least
 *       {@link HermitCaveTuning#HILL_MIN_RISE} over its way in, its rooms mostly under the hill;</li>
 *   <li>{@code hillsideCaveIsBuiltInTheWorld}, {@code undergroundCaveIsBuiltInTheWorld}: the nearest cave of each kind,
 *       generated with the land, is its template block for block, its lava still and walled in, one Cinder Hermit in it;
 *       the hillside one's ground over its den's middle higher than its way in (the land there before the cave too).</li>
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
        if (HermitCaveDesigns.indexesOf(HermitCaveDesigns.Kind.HILLSIDE).isEmpty()
                || HermitCaveDesigns.indexesOf(HermitCaveDesigns.Kind.UNDERGROUND).isEmpty())
        {
            throw new GameTestAssertException("there should be hillside and underground hermit caves");
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
            if (cave.crabs().size() != 1)
            {
                throw new GameTestAssertException(cave.name() + " has " + cave.crabs().size() + " crab spots (one expected)");
            }
            if (cave.rooms().isEmpty() || (cave.hillside() && cave.roofRise() < HermitCaveTuning.HILL_MIN_RISE))
            {
                throw new GameTestAssertException(cave.name() + " doesn't know its rooms or its shell's height");
            }
            if (!cave.hillside())
            {
                checkLoot(level, cave, template, Blocks.BARREL);
                checkLoot(level, cave, template, Blocks.SUSPICIOUS_GRAVEL);
            }
            if (cave.doorZ() != cave.depth() - 1)
            {
                throw new GameTestAssertException(cave.name() + "'s way in isn't at its south edge");
            }
        }
        helper.succeed();
    }

    /** The template has some {@code block} carrying a loot table that's in the game. */
    private static void checkLoot(ServerLevel level, HermitCaveDesigns.Cave cave, StructureTemplate template,
                                  net.minecraft.world.level.block.Block block)
    {
        List<StructureTemplate.StructureBlockInfo> found = template.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block);
        if (found.isEmpty())
        {
            throw new GameTestAssertException(cave.name() + " has no " + block.getName().getString());
        }
        for (StructureTemplate.StructureBlockInfo info : found)
        {
            String table = info.nbt() == null ? "" : info.nbt().getString("LootTable");
            if (table.isEmpty() || level.getServer().getLootData().getLootTable(new ResourceLocation(table)) == LootTable.EMPTY)
            {
                throw new GameTestAssertException(cave.name() + "'s " + block.getName().getString()
                        + " has no loot table that's in the game (" + table + ")");
            }
        }
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
                        // (counted a few seconds on: a freshly loaded chunk's mobs aren't always found at once)
                        int crabs = level.getEntitiesOfClass(CinderHermit.class,
                                AABB.of(HermitCaves.footprint(plot.ground(), plan.front(), plot.cave())).inflate(2.0D)).size();
                        if (crabs != 1)
                        {
                            later.add(plot.cave().name() + " has " + crabs + " Cinder Hermits (one expected)");
                        }
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
        int hillsides = 0;
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
                if (cave.cave().hillside())
                {
                    hillsides++;
                    double cover = HermitCaveSites.hillCover(sites.terrain(), cave.cave(), m.getX(), m.getZ(), cave.front());
                    BlockPos den = HermitCaves.toWorld(cave.cave().den(), m.atY(0), cave.front(), cave.cave());
                    BlockPos door = HermitCaves.door(m.atY(0), cave.front(), cave.cave());
                    double rise = sites.terrain().topBlock(den.getX(), den.getZ()) - sites.terrain().topBlock(door.getX(), door.getZ());
                    if (cover < HermitCaveTuning.HILL_MIN_COVER || rise < HermitCaveTuning.HILL_MIN_RISE)
                    {
                        problems.add(String.format("the hillside cave at %s doesn't sit in a hill (its den %.1f blocks over its way "
                                + "in, the hill %.0f%% over its rooms)", m.toShortString(), rise, 100.0D * cover));
                    }
                }
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
        LOGGER.info("[hermit caves] {} caves in the Cinder Wastes' first {} patches ({} hillside, {} underground; {} with two, "
                + "{} rolled a second); problems: {}", caves, PATCHES, hillsides, caves - hillsides, seconds, rolled, problems.size());
        if (caves < PATCHES)
        {
            problems.add("only " + caves + " caves in " + PATCHES + " patches");
        }
        return problems;
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hermit_cave_world", timeoutTicks = BACKSTOP_TICKS)
    public static void hillsideCaveIsBuiltInTheWorld(GameTestHelper helper)
    {
        caveIsBuiltInTheWorld(helper, HermitCaveDesigns.Kind.HILLSIDE);
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hermit_cave_world_underground", timeoutTicks = BACKSTOP_TICKS)
    public static void undergroundCaveIsBuiltInTheWorld(GameTestHelper helper)
    {
        caveIsBuiltInTheWorld(helper, HermitCaveDesigns.Kind.UNDERGROUND);
    }

    static void caveIsBuiltInTheWorld(GameTestHelper helper, HermitCaveDesigns.Kind kind)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<Optional<HermitCaveSites.CavePlan>> planned = CompletableFuture.supplyAsync(
                () -> HermitCaveSites.nearest(BossSites.forLevel(dross), BlockPos.ZERO, HermitCaveTuning.GOTO_SEARCH_RADIUS,
                        cave -> cave.cave().kind() == kind),
                Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        Object[] built = new Object[2];
        long[] settled = new long[1];
        String[] failed = new String[1];
        String what = kind == HermitCaveDesigns.Kind.HILLSIDE ? "hillside" : "underground";
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(planned, deadline, "the nearest " + what + " hermit cave to be planned"))
                .thenExecute(() -> {
                    HermitCaveSites.CavePlan plan = planned.join()
                            .orElseThrow(() -> new GameTestAssertException("no " + what + " hermit cave within 12,000 blocks of the hub"));
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
                    if (plan.cave().hillside())
                    {
                        stats += ", " + checkHill(dross, plan, ground, problems);
                    }
                    LOGGER.info("[hermit caves] the {} at {} facing {}: {} ({} ms to generate and check)", plan.cave().name(),
                            ground.toShortString(), plan.front().getName(), stats, Util.getMillis() - started);
                    LOGGER.info("[hermit caves] a slice through its way in (the way out to the right; # its own blocks, . air, "
                            + "~ lava, + the land in front):\n{}", slice(dross, plan, ground));
                    if (!problems.isEmpty())
                    {
                        failed[0] = "the hermit cave at " + ground.toShortString() + " isn't right: "
                                + String.join("; ", problems.subList(0, Math.min(8, problems.size())));
                        throw new GameTestAssertException(failed[0]);
                    }
                    settled[0] = Util.getMillis() + SETTLE_MILLIS;
                })
                .thenWaitUntil(() -> {
                    if (failed[0] == null && Util.getMillis() < settled[0])
                    {
                        throw new GameTestAssertException("letting the lava settle");
                    }
                })
                .thenExecute(() -> {
                    if (failed[0] != null)
                    {
                        throw new GameTestAssertException(failed[0]);
                    }
                    HermitCaveSites.CavePlan plan = (HermitCaveSites.CavePlan) built[0];
                    BlockPos ground = (BlockPos) built[1];
                    List<String> problems = new ArrayList<>();
                    checkLava(dross, plan.cave(), ground, plan.front(), problems, true);
                    BoundingBox footprint = HermitCaves.footprint(ground, plan.front(), plan.cave());
                    BlockPos spot = HermitCaves.crabSpots(ground, plan.front(), plan.cave()).get(0);
                    List<CinderHermit> crabs = dross.getEntitiesOfClass(CinderHermit.class, AABB.of(footprint).inflate(2.0D),
                            CinderHermit::isPersistenceRequired);
                    if (crabs.size() != 1 || crabs.get(0).blockPosition().distManhattan(spot) > 3)
                    {
                        problems.add(crabs.size() + " of its own Cinder Hermits in it (one expected, at its crab spot "
                                + spot.toShortString() + ")");
                    }
                    BoundingBox box = footprint.inflatedBy(16);
                    for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
                    {
                        for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                        {
                            dross.setChunkForced(cx, cz, false);
                        }
                    }
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("a few seconds on, the hermit cave isn't right: " + String.join("; ", problems));
                    }
                })
                .thenSucceed();
    }

    /**
     * A hillside cave sits in its hill: the ground over its den's middle (the world's top block there) higher than its
     * way in's floor, and the land there as the generator made it (before the cave) at least
     * {@link HermitCaveTuning#HILL_MIN_RISE} over the ground at its way in (the planner checks the same top blocks).
     */
    private static String checkHill(ServerLevel dross, HermitCaveSites.CavePlan plan, BlockPos ground, List<String> problems)
    {
        BlockPos den = HermitCaves.toWorld(plan.cave().den(), ground, plan.front(), plan.cave());
        int top = dross.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, den.getX(), den.getZ()) - 1;
        int land = dross.getChunkSource().getGenerator().getBaseHeight(den.getX(), den.getZ(), Heightmap.Types.OCEAN_FLOOR_WG,
                dross, dross.getChunkSource().randomState()) - 1;
        if (top <= ground.getY() + 1 || land < ground.getY() + HermitCaveTuning.HILL_MIN_RISE)
        {
            problems.add("it isn't in a hill: the ground over its den's middle is at y " + top + " (the land there " + land
                    + "), its way in's ground at y " + ground.getY());
        }
        return "the hill over its den " + (top - ground.getY()) + " blocks over its way in (the land " + (land - ground.getY()) + ")";
    }

    /** The vertical slice through the cave's middle column along its way in, as text (for the log). */
    static String slice(ServerLevel dross, HermitCaveSites.CavePlan plan, BlockPos ground)
    {
        HermitCaveDesigns.Cave cave = plan.cave();
        StringBuilder out = new StringBuilder();
        for (int y = cave.layers() + 6; y >= 0; y--)
        {
            out.append(String.format("%3d ", y - cave.groundLayer()));
            for (int z = 0; z < cave.depth() + 6; z++)
            {
                BlockPos at = HermitCaves.toWorld(new BlockPos(cave.width() / 2, y, z), ground, plan.front(), cave);
                BlockState state = dross.getBlockState(at);
                out.append(state.isAir() ? '.' : state.getFluidState().is(Fluids.LAVA) ? '~' : z >= cave.depth() ? '+' : '#');
            }
            out.append('\n');
        }
        return out.toString();
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
