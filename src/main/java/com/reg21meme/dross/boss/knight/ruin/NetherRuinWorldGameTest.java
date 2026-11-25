package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.knight.ElementalKnight;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSiteGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for the knight ruins in the Nether ({@link NetherRuinSites}, {@link NetherRuinPiece},
 * {@link NetherRuinTidy}, {@link KnightRuinKeeper}; PRE_DROSS_PLAN.md, stage T3). The test server's Nether is the game's
 * own, from a random seed. They only exist in the development environment, in their own batch.
 * <ul>
 *   <li>{@link #netherRuinsArePlanned}: only the Nether's generator has their structure set (not the Overworld's or the
 *       Dross's), and the Overworld's never comes to the Nether; every ruin within 3,000 blocks of 0, 0 stands in one of
 *       the Nether's areas, in its own biome (its tag), on a ground in the band, in its square's middle part, its id a
 *       Nether one that finds it again; no two nearer than a third of the spacing; at least a quarter of the squares
 *       wholly inside the radius have one; planned again from nothing, the same.</li>
 *   <li>{@link #netherRuinIsBuiltAndGuarded}: the ruin nearest 0, 0 made with its land: its structure, its broken frame
 *       (6 or more frame blocks, never lit), a ground under every footprint column (it never floats); a player near:
 *       after its tidy-up at least 97% of its template in place, next to nothing else over its ground (never buried), no
 *       lava loose in it or round it (every lava block a source held on its four sides and under), standing room on its
 *       guard spots; its two knights come, its element's, on their spots; both killed: the player near is told it was
 *       cleared ({@link KnightRuinClearedEvent}) and remembered (so it counts for the trader), they don't come back at
 *       once nor 10 minutes on with the player near, but do with the player 80 blocks off.</li>
 * </ul>
 */
@GameTestHolder(NetherRuinWorldGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class NetherRuinWorldGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The tests' GameTest namespace: the mod's. */
    static final String NAMESPACE = Dross.MODID;
    /** Their own batch (the Overworld ruins' is "dross_knight_ruins_world"). */
    private static final String BATCH = "dross_knight_ruins_nether";
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_nether_ruin_test", Comparator.comparingLong(ChunkPos::toLong));
    private static final long WAIT_MILLIS = 480_000L;
    private static final int TIMEOUT_TICKS = 4_000_000;
    /** The planning test looks this far round 0, 0 (Nether blocks). */
    private static final int PLAN_RADIUS = 3000;
    /** At least this share of the squares wholly inside that radius must have a ruin (about one every square). */
    private static final double MIN_SQUARE_SHARE = 0.25D;
    /** At least this share of the template's blocks (not air) must be in place after the tidy-up. */
    private static final double TEMPLATE_MIN_MATCH = 0.97D;
    /** At most this share of the footprint's cells over its ground may hold something its template doesn't (a vine grown in). */
    private static final double EXTRA_MAX = 0.01D;

    private NetherRuinWorldGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The plan
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void netherRuinsArePlanned(GameTestHelper helper)
    {
        Steps steps = new Steps(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = nether(server);
        NetherRuinSites.World w = NetherRuinSites.work(nether);
        if (w == null || !w.ctx.available())
        {
            throw new GameTestAssertException("the test world's Nether has no knight ruins (it should generate them)");
        }
        checkSets(server);
        CompletableFuture<List<RuinSite>> planned = CompletableFuture.supplyAsync(() -> NetherRuinSites.standing(w, 0, 0, PLAN_RADIUS));
        CompletableFuture<List<RuinSite>> again = CompletableFuture.supplyAsync(
                () -> NetherRuinSites.standing(NetherRuinSites.fresh(w.ctx), 0, 0, PLAN_RADIUS));
        steps.watch();
        helper.startSequence()
                .thenWaitUntil(() -> steps.waitFor("the plan", () -> planned.isDone() && again.isDone(), () -> "working it out"))
                .thenExecute(() -> steps.guard(() -> {
                    List<RuinSite> list = planned.join();
                    checkPlan(server, w, list);
                    List<String> a = list.stream().map(RuinSite::describe).toList();
                    List<String> b = again.join().stream().map(RuinSite::describe).toList();
                    if (!a.equals(b))
                    {
                        throw new GameTestAssertException("planned again from nothing, the Nether's ruins differ: " + a + " / " + b);
                    }
                }))
                .thenSucceed();
    }

    /** Only the Nether's generator may start them; the Overworld's (and the Dross's) never. */
    private static void checkSets(MinecraftServer server)
    {
        ServerLevel nether = nether(server);
        if (!hasSet(nether, NetherRuinSites.SET) || hasSet(nether, KnightRuinSites.SET))
        {
            throw new GameTestAssertException("the Nether's structure sets: nether_knight_ruin " + hasSet(nether, NetherRuinSites.SET)
                    + ", the Overworld's knight_ruin " + hasSet(nether, KnightRuinSites.SET) + " (want true, false)");
        }
        if (hasSet(server.overworld(), NetherRuinSites.SET))
        {
            throw new GameTestAssertException("the Overworld's generator has the Nether's ruins' structure set");
        }
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross != null && (hasSet(dross, NetherRuinSites.SET) || hasSet(dross, KnightRuinSites.SET)))
        {
            throw new GameTestAssertException("the Dross's generator has a knight ruin structure set");
        }
    }

    private static boolean hasSet(ServerLevel level, ResourceKey<StructureSet> set)
    {
        return level.getChunkSource().getGeneratorState().possibleStructureSets().stream().anyMatch(s -> s.is(set));
    }

    private static void checkPlan(MinecraftServer server, NetherRuinSites.World w, List<RuinSite> list)
    {
        NetherRuinSites.Context ctx = w.ctx;
        Map<KnightKind.Element, Integer> elements = new EnumMap<>(KnightKind.Element.class);
        list.forEach(s -> elements.merge(s.element(), 1, Integer::sum));
        LOGGER.info("[nether ruin test] {} ruins within {} blocks of 0, 0 in the Nether: {}", list.size(), PLAN_RADIUS, elements);
        list.forEach(s -> LOGGER.info("[nether ruin test]   {}", s.describe()));
        int s = ctx.spacing();
        int margin = ctx.margin();
        ServerLevel overworld = server.overworld();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        for (RuinSite site : list)
        {
            if (!site.inNether() || !RuinAreas.NETHER.contains(site.design().area()) || !RuinSite.isNetherId(site.id()))
            {
                throw new GameTestAssertException("a Nether ruin that isn't one of the Nether's: " + site.describe() + " (" + site.id() + ")");
            }
            if (!NetherRuinSites.inItsBiome(w, site))
            {
                throw new GameTestAssertException("the ruin " + site.describe() + " isn't in its area's biomes");
            }
            if (site.groundY() < KnightRuinTuning.NETHER_FLOOR_MIN || site.groundY() > KnightRuinTuning.NETHER_FLOOR_MAX)
            {
                throw new GameTestAssertException("the ruin " + site.describe() + " stands outside the floors' band");
            }
            int ix = Math.floorMod(site.x(), s);
            int iz = Math.floorMod(site.z(), s);
            if (ix < margin || ix >= s - margin || iz < margin || iz >= s - margin)
            {
                throw new GameTestAssertException("the ruin " + site.describe() + " isn't in its square's middle part");
            }
            Optional<RuinSite> again = KnightRuins.byId(server, site.id());
            if (again.isEmpty() || !again.get().equals(site))
            {
                throw new GameTestAssertException("the ruin's id " + site.id() + " doesn't find it again (" + again + ")");
            }
            // (only the Nether's world is known to the planner: the Overworld's and the Dross's generators never get one)
            int cx = site.startChunk().x;
            int cz = site.startChunk().z;
            if (NetherRuinSites.isStartChunk(overworld.getChunkSource().randomState(), cx, cz)
                    || (dross != null && NetherRuinSites.isStartChunk(dross.getChunkSource().randomState(), cx, cz)))
            {
                throw new GameTestAssertException("a Nether ruin's start would come to the Overworld or the Dross at chunk " + cx + ", " + cz);
            }
            if (!NetherRuinSites.isStartChunk(nether(server).getChunkSource().randomState(), cx, cz))
            {
                throw new GameTestAssertException("the Nether's generator doesn't start the ruin " + site.describe() + " in its own chunk");
            }
        }
        double nearest = 0;
        for (int i = 0; i < list.size(); i++)
        {
            double best = Double.MAX_VALUE;
            for (int j = 0; j < list.size(); j++)
            {
                if (i != j)
                {
                    double d = KnightRuinSites.dist(list.get(i).x(), list.get(i).z(), list.get(j).x(), list.get(j).z());
                    if (d < 2 * margin - 1)
                    {
                        throw new GameTestAssertException("two Nether ruins only " + (int) d + " blocks apart: " + list.get(i).describe()
                                + " and " + list.get(j).describe());
                    }
                    best = Math.min(best, d);
                }
            }
            nearest += list.size() > 1 ? best : 0;
        }
        // the squares wholly inside the radius: about one ruin each
        int squares = 0;
        int held = 0;
        for (int rx = Math.floorDiv(-PLAN_RADIUS, s); rx <= Math.floorDiv(PLAN_RADIUS, s); rx++)
        {
            for (int rz = Math.floorDiv(-PLAN_RADIUS, s); rz <= Math.floorDiv(PLAN_RADIUS, s); rz++)
            {
                double far = Math.sqrt(Math.pow(Math.max(Math.abs(rx * s), Math.abs(rx * s + s)), 2)
                        + Math.pow(Math.max(Math.abs(rz * s), Math.abs(rz * s + s)), 2));
                if (far > PLAN_RADIUS)
                {
                    continue;
                }
                squares++;
                int fx = rx;
                int fz = rz;
                if (list.stream().anyMatch(site -> Math.floorDiv(site.x(), s) == fx && Math.floorDiv(site.z(), s) == fz))
                {
                    held++;
                }
            }
        }
        LOGGER.info("[nether ruin test] {} of the {} squares wholly within {} blocks have a ruin; each ruin's nearest is {} blocks off on "
                + "average", held, squares, PLAN_RADIUS, list.size() > 1 ? (int) (nearest / list.size()) : -1);
        if (squares > 0 && held < squares * MIN_SQUARE_SHARE)
        {
            throw new GameTestAssertException("only " + held + " of the " + squares + " squares wholly within " + PLAN_RADIUS
                    + " blocks have a ruin (about one each wanted, at least a quarter)");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // A ruin made with its land, and its knights
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void netherRuinIsBuiltAndGuarded(GameTestHelper helper)
    {
        Steps steps = new Steps(helper);
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel nether = nether(server);
        NetherRuinSites.World w = NetherRuinSites.work(nether);
        if (w == null || !w.ctx.available())
        {
            throw new GameTestAssertException("the test world's Nether has no knight ruins (it should generate them)");
        }
        CompletableFuture<Optional<RuinSite>> found = CompletableFuture.supplyAsync(() -> NetherRuinSites.nearest(nether, 0, 0, null, 6000));
        Run run = new Run(nether);
        List<KnightRuinClearedEvent> told = new ArrayList<>();
        Consumer<KnightRuinClearedEvent> listener = told::add;
        steps.watch();
        steps.onFail(() -> {
            run.release();
            MinecraftForge.EVENT_BUS.unregister(listener);
        });
        helper.startSequence()
                .thenWaitUntil(() -> steps.waitFor("a Nether ruin to test", found::isDone, () -> "searching"))
                .thenExecute(() -> steps.guard(() -> {
                    run.site = found.join().orElseThrow(() -> new GameTestAssertException("no Nether ruin within 6,000 blocks of 0, 0"));
                    LOGGER.info("[nether ruin test] testing {}", run.site.describe());
                    run.hold();
                }))
                .thenWaitUntil(() -> steps.waitFor("its land", run::loaded, run::loadState))
                .thenExecute(() -> steps.guard(() -> {
                    checkBuilt(run);
                    MinecraftForge.EVENT_BUS.addListener(net.minecraftforge.eventbus.api.EventPriority.NORMAL, false, KnightRuinClearedEvent.class, listener);
                    run.player = join(nether, run.site.doorstep(), "nether-ruin-test-player");
                    run.player.setGameMode(GameType.CREATIVE);
                }))
                .thenWaitUntil(() -> steps.waitFor("its tidy-up", () -> {
                    KnightRuinKeeper.keep(nether, run.site);
                    KnightRuinData.Entry e = KnightRuinKeeper.data(nether).find(run.site.id());
                    return e != null && e.tidied();
                }, () -> "tidying (" + KnightRuinKeeper.state(nether, run.site) + ")"))
                .thenExecute(() -> steps.guard(() -> {
                    checkTemplate(run);
                    checkLava(run);
                }))
                .thenWaitUntil(() -> steps.waitFor("its two knights (a player near)", () -> {
                    KnightRuinKeeper.keep(nether, run.site);
                    return knights(nether, run.site).size() >= 2;
                }, () -> knights(nether, run.site).size() + " knights (" + KnightRuinKeeper.state(nether, run.site) + ")"))
                .thenExecute(() -> steps.guard(() -> {
                    List<ElementalKnight> knights = knights(nether, run.site);
                    if (knights.size() != 2)
                    {
                        throw new GameTestAssertException("a player near: 2 knights should guard it, but " + knights.size() + " do");
                    }
                    for (ElementalKnight k : knights)
                    {
                        if (k.kind().element != run.site.element())
                        {
                            throw new GameTestAssertException("a " + k.kind() + " guards a " + run.site.element() + " ruin");
                        }
                        BlockPos cell = run.site.guardCell(k.ruinSlot());
                        if (k.blockPosition().distManhattan(cell) > 2)
                        {
                            throw new GameTestAssertException("its knight " + k.kind() + " is at " + k.blockPosition().toShortString()
                                    + ", not on its guard spot " + cell.toShortString());
                        }
                    }
                    run.player.setGameMode(GameType.SURVIVAL);
                    knights.forEach(k -> k.kill());
                }))
                .thenIdle(25)
                .thenExecute(() -> steps.guard(() -> {
                    run.player.setGameMode(GameType.CREATIVE);
                    KnightRuinData.Entry e = KnightRuinKeeper.data(nether).find(run.site.id());
                    if (e == null || !e.bothBeaten())
                    {
                        throw new GameTestAssertException("both knights killed: the Nether ruin should be beaten");
                    }
                    if (told.stream().noneMatch(t -> t.getPlayer() == run.player && t.getRuin().id().equals(run.site.id()) && t.isFirstTime()))
                    {
                        throw new GameTestAssertException("the player near wasn't told the Nether ruin was cleared (for the first time)");
                    }
                    if (!KnightRuins.hasCleared(server, run.player.getUUID(), run.site))
                    {
                        throw new GameTestAssertException("the player near isn't remembered as clearing it (the trader's count reads that)");
                    }
                    KnightRuinKeeper.keep(nether, run.site);
                    expectKnights(nether, run.site, 0, "just beaten");
                    KnightRuinKeeper.data(nether).rewind(run.site.id(), KnightRuinKeeper.returnTicks() + 20);
                    KnightRuinKeeper.keep(nether, run.site);
                    expectKnights(nether, run.site, 0, "10 minutes on with a player within 64 blocks");
                    BlockPos away = run.site.ground().relative(run.site.front(), 80).above(2);
                    run.player.teleportTo(nether, away.getX() + 0.5D, away.getY(), away.getZ() + 0.5D, 0.0F, 0.0F);
                }))
                .thenWaitUntil(() -> steps.waitFor("its knights to come back (10 minutes on, the player 80 blocks off)", () -> {
                    KnightRuinKeeper.keep(nether, run.site);
                    return knights(nether, run.site).size() == 2;
                }, () -> knights(nether, run.site).size() + " knights (" + KnightRuinKeeper.state(nether, run.site) + ")"))
                .thenExecute(() -> steps.guard(() -> {
                    knights(nether, run.site).forEach(k -> k.discard());
                    MinecraftForge.EVENT_BUS.unregister(listener);
                    leave(run.player);
                    run.release();
                }))
                .thenSucceed();
    }

    /** Made with its land: its structure, its broken frame, and a ground under every footprint column. */
    private static void checkBuilt(Run run)
    {
        ServerLevel level = run.level;
        RuinSite site = run.site;
        if (!KnightRuinKeeper.built(level, site))
        {
            throw new GameTestAssertException("there's no dross:nether_knight_ruin structure at " + site.ground().toShortString());
        }
        BoundingBox foot = site.footprint();
        int frames = 0;
        int lit = 0;
        for (BlockPos pos : BlockPos.betweenClosed(foot.minX(), foot.minY(), foot.minZ(), foot.maxX(), foot.maxY(), foot.maxZ()))
        {
            BlockState state = level.getBlockState(pos);
            if (state.is(ModBlocks.DROSS_PORTAL_FRAME.get()))
            {
                frames++;
            }
            else if (state.is(ModBlocks.DROSS_PORTAL.get()))
            {
                lit++;
            }
        }
        if (frames < 6 || lit > 0)
        {
            throw new GameTestAssertException("its broken frame: " + frames + " frame blocks, " + lit + " portal blocks");
        }
        int floating = 0;
        BlockPos first = null;
        for (int x = foot.minX(); x <= foot.maxX(); x++)
        {
            for (int z = foot.minZ(); z <= foot.maxZ(); z++)
            {
                boolean ground = false;
                for (int y = site.groundY(); y >= site.groundY() - NetherRuinPiece.FOUNDATION && !ground; y--)
                {
                    BlockPos p = new BlockPos(x, y, z);
                    ground = !level.getBlockState(p).getCollisionShape(level, p).isEmpty();
                }
                if (!ground)
                {
                    floating++;
                    first = first == null ? new BlockPos(x, site.groundY(), z) : first;
                }
            }
        }
        if (floating > 0)
        {
            throw new GameTestAssertException(floating + " of its footprint's columns have nothing solid at or under its ground (the first at "
                    + first.toShortString() + ")");
        }
        LOGGER.info("[nether ruin test] {}: built with the land, {} frame blocks", site.describe(), frames);
    }

    /** After the tidy-up: its template in place, (next to) nothing else over its ground, standing room on its guard spots. */
    private static void checkTemplate(Run run)
    {
        ServerLevel level = run.level;
        RuinSite site = run.site;
        StructureTemplate template = level.getStructureManager().get(site.design().template()).orElseThrow();
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(site.rotation()).setRotationPivot(site.pivot());
        Set<BlockPos> placed = new HashSet<>();
        int same = 0;
        int total = 0;
        Map<String, Integer> wrong = new java.util.HashMap<>();
        for (Block b : net.minecraftforge.registries.ForgeRegistries.BLOCKS)
        {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(site.origin(), settings, b))
            {
                placed.add(info.pos());
                if (b == Blocks.AIR)
                {
                    continue;
                }
                total++;
                BlockState there = level.getBlockState(info.pos());
                if (there.getBlock() == b)
                {
                    same++;
                }
                else
                {
                    wrong.merge(BuiltInRegistries.BLOCK.getKey(b).getPath() + " -> " + BuiltInRegistries.BLOCK.getKey(there.getBlock()).getPath(), 1, Integer::sum);
                }
            }
        }
        double match = total == 0 ? 0.0D : same / (double) total;
        String worst = wrong.entrySet().stream().sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(6)
                .map(e -> e.getKey() + " " + e.getValue()).collect(java.util.stream.Collectors.joining(", "));
        // over its ground: anything its template doesn't place (a basalt column, a fungus, rock it should have cut away)
        BoundingBox foot = site.footprint();
        int cells = 0;
        int extra = 0;
        Map<String, Integer> extras = new java.util.HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(foot.minX(), site.groundY() + 1, foot.minZ(), foot.maxX(), foot.maxY(), foot.maxZ()))
        {
            cells++;
            BlockState state = level.getBlockState(pos);
            if (!state.isAir() && !placed.contains(pos))
            {
                extra++;
                extras.merge(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath(), 1, Integer::sum);
            }
        }
        LOGGER.info("[nether ruin test] {}: {}% of its template in place after its tidy-up (the rest: {}); {} other blocks over its ground {}",
                site.describe(), Math.round(match * 1000) / 10.0D, worst, extra, extras);
        if (match < TEMPLATE_MIN_MATCH)
        {
            throw new GameTestAssertException("only " + Math.round(match * 100) + "% of its template's blocks are in place (" + worst + ")");
        }
        if (extra > cells * EXTRA_MAX)
        {
            throw new GameTestAssertException(extra + " blocks over its ground that its template doesn't place (buried?): " + extras);
        }
        for (int i = 0; i < 2; i++)
        {
            BlockPos cell = site.guardCell(i);
            if (!level.getBlockState(cell).getCollisionShape(level, cell).isEmpty()
                    || !level.getBlockState(cell.above()).getCollisionShape(level, cell.above()).isEmpty()
                    || level.getBlockState(cell.below()).getCollisionShape(level, cell.below()).isEmpty())
            {
                throw new GameTestAssertException("its guard spot " + cell.toShortString() + " has no room to stand");
            }
        }
    }

    /** No lava loose in it or round it: every lava block a source, held on its four sides and under. */
    private static void checkLava(Run run)
    {
        ServerLevel level = run.level;
        RuinSite site = run.site;
        BoundingBox foot = site.footprint();
        int m = NetherRuinTidy.LAVA_MARGIN;
        int sources = 0;
        for (BlockPos pos : BlockPos.betweenClosed(foot.minX() - m, site.groundY() - NetherRuinPiece.FOUNDATION, foot.minZ() - m,
                foot.maxX() + m, foot.maxY() + NetherRuinPiece.ABOVE, foot.maxZ() + m))
        {
            FluidState fluid = level.getFluidState(pos);
            if (fluid.isEmpty())
            {
                continue;
            }
            if (!fluid.isSource())
            {
                throw new GameTestAssertException("flowing " + BuiltInRegistries.FLUID.getKey(fluid.getType()).getPath() + " at "
                        + pos.toShortString() + " (its lava got loose)");
            }
            sources++;
            for (Direction d : new Direction[] {Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST, Direction.DOWN})
            {
                if (!NetherRuinPiece.holds(level.getBlockState(pos.relative(d))))
                {
                    throw new GameTestAssertException("the lava at " + pos.toShortString() + " isn't held on its " + d.getName() + " side ("
                            + BuiltInRegistries.BLOCK.getKey(level.getBlockState(pos.relative(d)).getBlock()).getPath() + ")");
                }
            }
        }
        LOGGER.info("[nether ruin test] {}: {} lava sources, all held", site.describe(), sources);
    }

    private static void expectKnights(ServerLevel level, RuinSite site, int count, String when)
    {
        int n = knights(level, site).size();
        if (n != count)
        {
            throw new GameTestAssertException(when + ": " + count + " knights should guard it, but " + n + " do ("
                    + KnightRuinKeeper.state(level, site) + ")");
        }
    }

    private static List<ElementalKnight> knights(ServerLevel level, RuinSite site)
    {
        return level.getEntitiesOfClass(ElementalKnight.class, new AABB(site.ground()).inflate(64.0D),
                k -> k.isAlive() && site.id().equals(k.ruin()));
    }

    private static ServerLevel nether(MinecraftServer server)
    {
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether == null)
        {
            throw new GameTestAssertException("the test server has no Nether");
        }
        return nether;
    }

    private static ServerPlayer join(ServerLevel level, BlockPos at, String name)
    {
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        player.moveTo(at.getX() + 0.5D, at.getY() + 1, at.getZ() + 0.5D, 0.0F, 0.0F);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        if (player.serverLevel() != level)
        {
            player.teleportTo(level, at.getX() + 0.5D, at.getY() + 1, at.getZ() + 0.5D, 0.0F, 0.0F);
        }
        return player;
    }

    private static void leave(@Nullable ServerPlayer player)
    {
        if (player != null && player.server.getPlayerList().getPlayer(player.getUUID()) == player)
        {
            player.server.getPlayerList().remove(player);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The run's land, its waits and its watchdog (as the Overworld ruins' tests)
    // ---------------------------------------------------------------------------------------------

    private static final class Run
    {
        final ServerLevel level;
        RuinSite site;
        ServerPlayer player;
        ChunkPos middle;
        boolean held;
        static final int RADIUS = 4;

        Run(ServerLevel level)
        {
            this.level = level;
        }

        void hold()
        {
            this.middle = this.site.startChunk();
            this.level.getChunkSource().addRegionTicket(TICKET, this.middle, RADIUS, this.middle);
            this.held = true;
        }

        void release()
        {
            if (this.held)
            {
                this.level.getChunkSource().removeRegionTicket(TICKET, this.middle, RADIUS, this.middle);
                this.held = false;
            }
            leave(this.player);
        }

        boolean loaded()
        {
            return this.loadState() == null;
        }

        @Nullable
        String loadState()
        {
            int total = 0;
            int ready = 0;
            for (int dx = -2; dx <= 2; dx++)
            {
                for (int dz = -2; dz <= 2; dz++)
                {
                    total++;
                    int cx = this.middle.x + dx;
                    int cz = this.middle.z + dz;
                    if (this.level.getChunkSource().getChunkNow(cx, cz) != null && this.level.areEntitiesLoaded(ChunkPos.asLong(cx, cz))
                            && this.level.isPositionEntityTicking(new BlockPos(cx * 16 + 8, this.site.groundY(), cz * 16 + 8)))
                    {
                        ready++;
                    }
                }
            }
            return ready == total ? null : ready + " of " + total + " chunks ready";
        }
    }

    /** "Not yet": a wait's check that isn't done. */
    private static final class Waiting extends GameTestAssertException
    {
        Waiting(String message)
        {
            super(message);
        }
    }

    private static final class Steps
    {
        final GameTestHelper helper;
        @Nullable
        RuntimeException failure;
        long waitingSince = -1L;
        @Nullable
        Runnable onFail;

        Steps(GameTestHelper helper)
        {
            this.helper = helper;
        }

        void onFail(Runnable cleanup)
        {
            this.onFail = cleanup;
        }

        void watch()
        {
            this.helper.startSequence()
                    .thenWaitUntil(() -> {
                        if (this.failure == null)
                        {
                            throw new GameTestAssertException("the test ran out of time");
                        }
                    })
                    .thenFail(() -> this.failure);
        }

        void guard(Runnable step)
        {
            if (this.failure != null)
            {
                throw this.failure;
            }
            try
            {
                step.run();
            }
            catch (Waiting w)
            {
                throw w;
            }
            catch (RuntimeException e)
            {
                LOGGER.error("[nether ruin test] failed: {}", e.getMessage(), e);
                this.failure = e;
                if (this.onFail != null)
                {
                    this.onFail.run();
                }
                throw e;
            }
        }

        void waitFor(String what, BooleanSupplier done, Supplier<String> state)
        {
            this.guard(() -> {
                if (done.getAsBoolean())
                {
                    this.waitingSince = -1L;
                    return;
                }
                long now = Util.getMillis();
                if (this.waitingSince < 0L)
                {
                    this.waitingSince = now;
                }
                if (now - this.waitingSince > WAIT_MILLIS)
                {
                    throw new GameTestAssertException("gave up waiting for " + what + " after " + WAIT_MILLIS / 1000L + " s (" + state.get() + ")");
                }
                throw new Waiting("waiting for " + what);
            });
        }
    }
}
