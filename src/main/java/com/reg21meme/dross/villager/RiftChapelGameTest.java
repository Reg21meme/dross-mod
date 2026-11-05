package com.reg21meme.dross.villager;

import com.reg21meme.dross.DrossWorldSettings;
import com.reg21meme.dross.DrossConfig;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.quest.ReadableItem;
import com.reg21meme.dross.quest.WeatheredLetterItem;
import com.reg21meme.dross.registry.ModBlocks;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for the Dross trader's one Rift Chapel, at the edge of a village the seed picks
 * ({@link RiftChapels}, {@link TraderVillages}, the structure {@code dross:rift_chapel}), and its trader. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}; only these, in a brand-new world: set {@link #NAMESPACE} to
 * {@code "dross_chapel"} and add {@code -PgameTestNamespaces=dross_chapel -PgameTestWorld=<a new folder>}. The test
 * server's Overworld is a normal world with a random seed ({@code run/server.properties}). They only exist in the
 * development environment.
 * <ul>
 *   <li>{@link #nothingHappensAtWorldStart}: when the server had started, the home had been worked out once (in the
 *       background, nothing generated for it), and world generation had put at most one trader in at most one chapel.</li>
 *   <li>{@link #homeComesFromTheSeed}: the world has its home; worked out again on another thread, it's the same; its
 *       village is the one world generation really makes there (the village's start in its chunk, its pieces' boxes the
 *       same as worked out); the chapel and its ring keep clear of every piece; its path runs through no house and ends on
 *       a street; how long it all took (logged).</li>
 *   <li>{@link #chapelIsMadeWithTheLand}: the chapel's land loaded with a ticket (waited for by the clock, then its
 *       trader's tidy-up): its structure start; at least 99% of the design's blocks in place; its ground level; no tree's
 *       log or leaves in it; its broken frame unbreakable; its path laid; exactly one trader in it, at home there, whose
 *       first right-click gives the opening speech; {@code /dross goto trader} lands at its door facing it; a Weathered
 *       Letter names it in the village's wording; the "once a village" rule turns away a second trader from its village
 *       and one from another village, but not a spawn-egg trader; after its land is unloaded and loaded again (if it's out
 *       of the spawn chunks), still the one trader.</li>
 * </ul>
 * A wait never fails by itself (Minecraft retries it until the test's tick limit): a wait that gives up keeps its failure
 * and a watchdog sequence fails the test with it ({@link Run#watch}).
 */
@GameTestHolder(RiftChapelGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RiftChapelGameTest
{
    /** The tests' GameTest namespace: the mod's ({@code "dross_chapel"} to run only these). */
    static final String NAMESPACE = Dross.MODID;
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The tests need no blocks: a 1x1x1 air template, made in memory when the server starts. */
    static final String EMPTY = "rift_chapel_test_empty";
    private static final String BATCH = "dross_rift_chapel";
    private static final ResourceKey<Structure> STRUCTURE = ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(Dross.MODID, "rift_chapel"));
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_chapel_test", Comparator.comparingLong(ChunkPos::toLong));
    /** The chapel's land is held this many chunks round its middle (its trader ticks, the tidy-up's land loaded). */
    private static final int TICKET_RADIUS = 4;
    /** A wait gives up after this long (real time: the test server runs ticks far faster than the game). */
    private static final long WAIT_MILLIS = 360_000L;
    /** The tests' tick limit, far past any wait (each wait ends by the clock first). */
    private static final int TIMEOUT_TICKS = 4_000_000;
    /** Chunks this near the spawn's chunk (or nearer) stay loaded: a chapel there isn't unloaded and loaded again. */
    private static final int SPAWN_CHUNKS = 13;
    /** At least this share of the path's columns must be paved (the rest: stone, a tree's roots, a village block...). */
    private static final double PATH_MIN_PAVED = 0.7D;
    /** How long the rule's extra traders are given to be turned away (ticks). */
    private static final int RULE_TICKS = 20;
    /** At least this share of the design's own blocks (not air) must be in place. */
    private static final double DESIGN_MIN_MATCH = 0.99D;
    /** The land this many blocks round the chapel (or nearer) must be within that many blocks of its floor's level. */
    private static final int RING_CHECKED = 2;
    /** How far round the chapel the land's steps are logged (blocks). */
    private static final int RING_LOGGED = 6;
    /** Working the home out again must take less than this (ms). */
    private static final long PLAN_MAX_MILLIS = 20_000L;

    /** What was true when the server had started (before any test ran). */
    private static final class AtStart
    {
        static boolean homeReady;
        static long[] built = new long[4];
        static int tradersLoaded = -1;
        static boolean available;
        /** The region files there were when the server started (their land was made before this run). */
        static final List<String> regions = new ArrayList<>();
    }

    /** The tests' empty template, and what's true when the server has started (after every other mod's handlers). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        MinecraftServer server = event.getServer();
        registerEmpty(server, new ResourceLocation(Dross.MODID, EMPTY));
        if (!NAMESPACE.equals(Dross.MODID))
        {
            registerEmpty(server, new ResourceLocation(NAMESPACE, EMPTY));
        }
        ServerLevel overworld = server.overworld();
        AtStart.homeReady = RiftChapels.homeIfReady(overworld) != null;
        AtStart.built = RiftChapelPiece.timing();
        AtStart.available = RiftChapels.available(overworld);
        int traders = 0;
        for (DrossTrader ignored : overworld.getEntities(com.reg21meme.dross.registry.ModEntities.TRADER.get(), t -> true))
        {
            traders++;
        }
        AtStart.tradersLoaded = traders;
        Path folder = server.getWorldPath(LevelResource.ROOT).resolve("region");
        try (var files = Files.list(folder))
        {
            files.forEach(file -> AtStart.regions.add(file.getFileName().toString()));
        }
        catch (Exception e)
        {
            // (no region folder yet)
        }
        LOGGER.info("[chapel test] When the server had started: the trader's home worked out: {}; world generation had built {} chapel "
                        + "chunks with {} traders ({} loaded); a chapel available: {}",
                AtStart.homeReady, AtStart.built[0], AtStart.built[3], AtStart.tradersLoaded, AtStart.available);
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void nothingHappensAtWorldStart(GameTestHelper helper)
    {
        List<String> problems = new ArrayList<>();
        expect(problems, AtStart.available, "the world makes no Rift Chapel (Generate Structures off, or no dross:rift_chapel set)");
        expect(problems, AtStart.homeReady, "the trader's home wasn't worked out yet when the server had started");
        expect(problems, AtStart.built[3] <= 1L, "world generation had put " + AtStart.built[3] + " traders in chapels (one at most)");
        // (a chapel made in a chunk round the spawn that isn't fully loaded yet has its trader saved in it, not loaded)
        expect(problems, AtStart.tradersLoaded <= AtStart.built[3], AtStart.tradersLoaded + " traders were loaded at the start, but world "
                + "generation had put only " + AtStart.built[3] + " in their chapel (there's no other way for one to come now)");
        fail(problems);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Where they are, from the noise
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 20_000)
    public static void homeComesFromTheSeed(GameTestHelper helper)
    {
        List<String> problems = new ArrayList<>();
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        Optional<RiftChapels.Home> found = RiftChapels.home(overworld);
        if (found.isEmpty())
        {
            throw new GameTestAssertException("the world has no home for the trader (see the log)");
        }
        RiftChapels.Home home = found.get();
        LOGGER.info("[chapel test] The trader's home: {}", home.describe());
        // Worked out again (nothing remembered) on another thread: the same.
        TraderVillages.Context context = RiftChapels.context(overworld);
        long start = System.nanoTime();
        Optional<RiftChapels.Home> again = CompletableFuture.supplyAsync(() -> TraderVillages.plan(context)).join();
        long millis = (System.nanoTime() - start) / 1_000_000L;
        LOGGER.info("[chapel test] Worked out again on another thread in {} ms: {}", millis, again.map(RiftChapels.Home::describe).orElse("none"));
        expect(problems, again.equals(found), "worked out again, the home is " + again.map(RiftChapels.Home::describe).orElse("none"));
        expect(problems, millis < PLAN_MAX_MILLIS, "working the home out took " + millis + " ms");
        expect(problems, home.pick() >= 1 && home.choices() <= TraderVillages.NEAREST, "the pick is No. " + home.pick() + " of " + home.choices());
        // The quick answers: the chapel's own chunk is never one of the chunks answered "no" while the plan is worked out.
        List<TraderVillages.Cell> possible = RiftChapels.possibleVillages(overworld);
        expect(problems, !TraderVillages.neverHolds(context, possible, home.chapel().chunkX(), home.chapel().chunkZ()),
                "the chapel's chunk would have been answered at once with no while the plan was worked out");
        LOGGER.info("[chapel test] {} possible villages within {} blocks of the spawn point; the seed's order starts {}", possible.size(),
                context.searchRadius(), TraderVillages.trialOrder(context, possible).stream().limit(TraderVillages.NEAREST)
                        .map(c -> c.chunkX() + "," + c.chunkZ()).toList());
        if (!home.inVillage())
        {
            // (a seed with no village near its spawn point that has room: the chapel stands on its own; nothing more to check)
            LOGGER.warn("[chapel test] No village near this seed's spawn point had room for the chapel; it stands on its own: {}", home.describe());
            fail(problems);
            helper.succeed();
            return;
        }
        checkVillage(overworld, context, home, problems);
        fail(problems);
        helper.succeed();
    }

    /**
     * The home's village is the one world generation really makes: the start of a village in its chunk (made to the
     * "structure starts" step here), its pieces' boxes those worked out from the seed; the chapel and its ring clear of
     * every piece; the path through no house, ending on a street.
     */
    private static void checkVillage(ServerLevel overworld, TraderVillages.Context context, RiftChapels.Home home, List<String> problems)
    {
        ChunkPos start = new ChunkPos(home.village());
        TraderVillages.Village planned = null;
        List<TraderVillages.VillageSet> sets = TraderVillages.villageSets(context.structures());
        for (int i = 0; i < sets.size() && planned == null; i++)
        {
            planned = TraderVillages.villageAt(context, sets.get(i), new TraderVillages.Cell(i, start.x, start.z, 0L));
        }
        if (planned == null)
        {
            problems.add("worked out again, there's no village in chunk " + start);
            return;
        }
        net.minecraft.world.level.chunk.ChunkAccess chunk = overworld.getChunk(start.x, start.z,
                net.minecraft.world.level.chunk.ChunkStatus.STRUCTURE_STARTS, true);
        StructureStart real = null;
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet())
        {
            if (overworld.registryAccess().registryOrThrow(Registries.STRUCTURE).wrapAsHolder(entry.getKey()).is(net.minecraft.tags.StructureTags.VILLAGE)
                    && entry.getValue().isValid())
            {
                real = entry.getValue();
            }
        }
        if (real == null)
        {
            problems.add("world generation made no village in chunk " + start + " (worked out: " + planned.describe() + ")");
            return;
        }
        List<BoundingBox> realBoxes = real.getPieces().stream().map(piece -> piece.getBoundingBox()).toList();
        List<BoundingBox> plannedBoxes = new ArrayList<>();
        // (the plan keeps houses and streets apart; compare as sets of boxes)
        plannedBoxes.addAll(planned.houses());
        plannedBoxes.addAll(planned.streets());
        boolean same = realBoxes.size() == plannedBoxes.size() && new java.util.HashSet<>(realBoxes).equals(new java.util.HashSet<>(plannedBoxes));
        LOGGER.info("[chapel test] The village in chunk {}: world generation made {} pieces, the plan worked out {} ({} streets): {}", start,
                realBoxes.size(), plannedBoxes.size(), planned.streets().size(), same ? "the same" : "NOT the same");
        expect(problems, same, "the village world generation made in chunk " + start + " has " + realBoxes.size() + " pieces, the plan's "
                + plannedBoxes.size() + ", and their boxes differ");
        // The chapel and its ring clear of every piece; the path through no house, ending on a street.
        BoundingBox foot = home.chapel().footprint();
        int keep = RiftChapelPiece.RING + TraderVillages.PIECE_MARGIN;
        for (BoundingBox b : plannedBoxes)
        {
            boolean clear = b.maxX() < foot.minX() - keep || b.minX() > foot.maxX() + keep || b.maxZ() < foot.minZ() - keep
                    || b.minZ() > foot.maxZ() + keep;
            expect(problems, clear, "the chapel " + foot + " and its ring come within " + keep + " blocks of the village piece " + b);
        }
        for (long cell : RiftChapelPathPiece.cells(home.path(), TraderVillages.PATH_WIDTH / 2))
        {
            int x = ChunkPos.getX(cell);
            int z = ChunkPos.getZ(cell);
            for (BoundingBox house : planned.houses())
            {
                if (!planned.streets().isEmpty())
                {
                    expect(problems, !(x >= house.minX() && x <= house.maxX() && z >= house.minZ() && z <= house.maxZ()),
                            "the path's column " + x + ", " + z + " is in the village piece " + house);
                }
            }
        }
        BlockPos end = home.path().get(home.path().size() - 1);
        List<BoundingBox> targets = planned.streets().isEmpty() ? plannedBoxes : planned.streets();
        expect(problems, targets.stream().anyMatch(b -> end.getX() >= b.minX() && end.getX() <= b.maxX() && end.getZ() >= b.minZ()
                && end.getZ() <= b.maxZ()), "the path ends at " + end.getX() + ", " + end.getZ() + ", not on a street");
        expect(problems, TraderVillages.pathLength(home.path()) <= TraderVillages.MAX_PATH, "the path is " + TraderVillages.pathLength(home.path())
                + " blocks long");
    }

    // ---------------------------------------------------------------------------------------------
    // A chapel, made with the land
    // ---------------------------------------------------------------------------------------------

    /**
     * The settings file's switches for the trader (config/dross-common.toml): this world's are the file's defaults, saved
     * in it; and with {@code atVillages} off, the plan lays out no village and the chapel stands on its own, 384 to 1,536
     * blocks from the spawn point, the same every time.
     */
    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = 20_000)
    public static void configSwitchesTheTraderOffVillages(GameTestHelper helper)
    {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        List<String> problems = new ArrayList<>();
        DrossWorldSettings.Values settings = DrossWorldSettings.of(overworld);
        expect(problems, settings.traderAtVillages() == DrossConfig.traderAtVillages()
                        && settings.traderSearchRadius() == DrossConfig.traderSearchRadius()
                        && settings.siteMinDistance() == DrossConfig.siteMinDistance() && settings.siteMaxDistance() == DrossConfig.siteMaxDistance(),
                "the world's saved settings (" + settings + ") aren't the config file's (a test world is made with them)");
        TraderVillages.Context c = RiftChapels.context(overworld);
        expect(problems, c.atVillages() == settings.traderAtVillages() && c.searchRadius() == settings.traderSearchRadius(),
                "the trader's plan doesn't use the world's settings: at villages " + c.atVillages() + ", radius " + c.searchRadius());
        TraderVillages.Context off = new TraderVillages.Context(c.generator(), c.random(), c.structures(), c.registries(), c.templates(),
                c.heights(), c.seed(), c.spawnX(), c.spawnZ(), false, c.searchRadius());
        long start = System.nanoTime();
        Optional<RiftChapels.Home> home = TraderVillages.plan(off, List.of());
        long millis = (System.nanoTime() - start) / 1_000_000L;
        Optional<RiftChapels.Home> again = TraderVillages.plan(off, List.of());
        LOGGER.info("[chapel test] With atVillages off ({} ms): {}", millis, home.map(RiftChapels.Home::describe).orElse("none"));
        expect(problems, home.isPresent(), "with atVillages off there's no chapel at all");
        home.ifPresent(h -> {
            expect(problems, !h.inVillage() && h.looked() == 0, "with atVillages off the chapel is at a village (" + h.describe() + ")");
            expect(problems, h.distance() >= TraderVillages.FALLBACK_MIN && h.distance() <= TraderVillages.FALLBACK_RADIUS + 32,
                    "with atVillages off the chapel is " + h.distance() + " blocks from the spawn point");
        });
        expect(problems, again.equals(home), "with atVillages off the plan isn't the same twice");
        expect(problems, millis < PLAN_MAX_MILLIS, "with atVillages off the plan took " + millis + " ms");
        fail(problems);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void chapelIsMadeWithTheLand(GameTestHelper helper)
    {
        Run run = new Run(helper);
        run.watch();
        helper.startSequence()
                .thenExecute(() -> run.guard(run::hold))
                .thenWaitUntil(() -> run.waitFor("the chapel's land and its entities", run::loaded, run::loadState))
                .thenExecute(() -> run.guard(() -> {
                    if (run.skipOldWorld())
                    {
                        return;
                    }
                    long[] t = RiftChapelPiece.timing();
                    LOGGER.info("[chapel test] {}'s land is made: {} chapel chunks built this run, {} ms in all, the slowest {} ms; {} traders put in",
                            run.chapel.describe(), t[0], t[1] / 1_000_000L, t[2] / 1_000_000L, t[3]);
                }))
                .thenWaitUntil(() -> run.waitFor("its trader's tidy-up", () -> run.skipped || run.tidied(), run::traderState))
                .thenExecute(() -> run.guard(() -> {
                    if (run.skipped)
                    {
                        return;
                    }
                    checkStart(run);
                    checkBlocks(run);
                    checkGround(run);
                    checkNoTrees(run);
                    checkFrame(run);
                    checkPath(run);
                    run.traderId = checkOneTrader(run);
                    checkVisitor(run);
                    run.spawnRuleTraders();
                }))
                .thenIdle(RULE_TICKS)
                .thenExecute(() -> run.guard(() -> {
                    if (!run.skipped)
                    {
                        checkRule(run);
                    }
                }))
                .thenWaitUntil(() -> run.waitFor("the Weathered Letter's text", () -> run.skipped || run.letterWritten(), () -> "still blank"))
                .thenExecute(() -> run.guard(() -> {
                    if (!run.skipped)
                    {
                        checkLetter(run);
                    }
                    run.leave();
                }))
                // Its land unloaded (nobody near, the ticket gone), then loaded again: still the one trader. (Not in the spawn
                // chunks, which never unload.)
                .thenExecute(() -> run.guard(run::release))
                .thenWaitUntil(() -> run.waitFor("the chapel's land to unload", () -> run.skipped || run.inSpawnChunks() || run.unloaded(), run::loadState))
                .thenExecute(() -> run.guard(run::hold))
                .thenWaitUntil(() -> run.waitFor("the chapel's land to load again", () -> run.skipped || run.loaded(), run::loadState))
                .thenExecute(() -> run.guard(() -> {
                    if (run.skipped)
                    {
                        return;
                    }
                    if (run.inSpawnChunks())
                    {
                        LOGGER.info("[chapel test] The chapel is in the spawn chunks (they never unload): not unloaded and loaded again");
                        checkOneTrader(run);
                        return;
                    }
                    UUID again = checkOneTrader(run);
                    if (!again.equals(run.traderId))
                    {
                        throw new GameTestAssertException("after its land was unloaded and loaded again its trader is " + again + ", not " + run.traderId);
                    }
                    LOGGER.info("[chapel test] Unloaded and loaded again: still the one trader, {}", again);
                }))
                .thenExecute(() -> run.guard(run::release))
                .thenSucceed();
    }

    /** The structure start in the chapel's chunk. */
    private static void checkStart(Run run)
    {
        Structure structure = run.overworld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
        StructureStart start = structure == null ? null
                : run.overworld.getChunk(run.chapel.chunkX(), run.chapel.chunkZ()).getStartForStructure(structure);
        if (start == null || !start.isValid())
        {
            throw new GameTestAssertException("there's no dross:rift_chapel structure start in its chunk " + run.chapel.startChunk());
        }
    }

    /**
     * At least {@value #DESIGN_MIN_MATCH} of the design's own blocks (not air) in place; and of the air it clears, as much
     * still air or something the world puts down that's simply replaced (grass, flowers, snow).
     */
    private static void checkBlocks(Run run)
    {
        Map<BlockPos, BlockState> design = TraderHut.DESIGN.blocks(run.chapel.center(), run.chapel.facing());
        int solid = 0;
        int solidOk = 0;
        int air = 0;
        int airOk = 0;
        List<String> wrong = new ArrayList<>();
        for (Map.Entry<BlockPos, BlockState> entry : design.entrySet())
        {
            BlockState there = run.overworld.getBlockState(entry.getKey());
            if (entry.getValue().isAir())
            {
                air++;
                airOk += there.isAir() || there.canBeReplaced() ? 1 : 0;
                if (!(there.isAir() || there.canBeReplaced()) && wrong.size() < 8)
                {
                    wrong.add(name(there) + " at " + entry.getKey().toShortString() + " (air there)");
                }
            }
            else
            {
                solid++;
                boolean same = closed(there).equals(closed(entry.getValue()));
                solidOk += same ? 1 : 0;
                if (!same && wrong.size() < 8)
                {
                    wrong.add(there + " at " + entry.getKey().toShortString() + ", not " + entry.getValue());
                }
            }
        }
        double solidShare = solid == 0 ? 0.0D : solidOk / (double) solid;
        double airShare = air == 0 ? 1.0D : airOk / (double) air;
        LOGGER.info("[chapel test] {}: {} of the design's {} blocks in place ({}%), {} of its {} cleared cells still clear; e.g. {}",
                run.chapel.describe(), solidOk, solid,
                String.format("%.2f", 100.0D * solidShare), airOk, air, wrong);
        if (solidShare < DESIGN_MIN_MATCH || airShare < DESIGN_MIN_MATCH)
        {
            throw new GameTestAssertException("only " + solidOk + " of the design's " + solid + " blocks and " + airOk + " of its " + air
                    + " cleared cells are right: " + wrong);
        }
    }

    /** A door as if shut (the trader opens his as he goes in and out); anything else as it is. */
    private static BlockState closed(BlockState state)
    {
        return state.hasProperty(net.minecraft.world.level.block.DoorBlock.OPEN)
                ? state.setValue(net.minecraft.world.level.block.DoorBlock.OPEN, false) : state;
    }

    /** Every column of the footprint has solid ground at the floor's level and under it; how far the land round it steps (logged). */
    private static void checkGround(Run run)
    {
        List<String> problems = new ArrayList<>();
        BoundingBox foot = run.chapel.footprint();
        int groundY = foot.minY();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = foot.minX(); x <= foot.maxX(); x++)
        {
            for (int z = foot.minZ(); z <= foot.maxZ(); z++)
            {
                for (int y = groundY - 1; y <= groundY; y++)
                {
                    BlockState state = run.overworld.getBlockState(pos.set(x, y, z));
                    expect(problems, state.blocksMotion() && state.getFluidState().isEmpty(), name(state) + " at " + pos.toShortString()
                            + " (the chapel's ground is solid there; its column from " + (groundY - 10) + " up: " + profile(run, x, groundY - 10, z, 14)
                            + ")");
                }
            }
        }
        // How far the land (its ground: not trees, plants or snow) round it is off the floor's level, d blocks out (logged)
        int[] most = new int[RING_LOGGED + 1];
        String steepest = "none";
        for (int x = foot.minX() - RING_LOGGED; x <= foot.maxX() + RING_LOGGED; x++)
        {
            for (int z = foot.minZ() - RING_LOGGED; z <= foot.maxZ() + RING_LOGGED; z++)
            {
                int d = Math.max(Math.max(foot.minX() - x, x - foot.maxX()), Math.max(foot.minZ() - z, z - foot.maxZ()));
                if (d <= 0)
                {
                    continue;
                }
                int y = run.overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1;
                while (y > groundY - 40 && !isGround(run.overworld.getBlockState(pos.set(x, y, z))))
                {
                    y--;
                }
                if (Math.abs(y - groundY) > most[d])
                {
                    most[d] = Math.abs(y - groundY);
                    if (d == 1)
                    {
                        steepest = x + ", " + z + ": " + profile(run, x, groundY - 10, z, 22);
                    }
                }
            }
        }
        LOGGER.info("[chapel test] {}: its floor at y {}, every footprint column solid there and under it ({} problems); the land's ground "
                + "1, 2, ... {} blocks round it is at most {} blocks off its level (the most 1 block out, from {} up: {})", run.chapel.describe(),
                groundY, problems.size(), RING_LOGGED, Arrays.toString(Arrays.copyOfRange(most, 1, most.length)), groundY - 10, steepest);
        // (its ring steps the land a block at a time: right round it that always holds; further out the land's own cliffs may stay)
        for (int d = 1; d <= RING_CHECKED; d++)
        {
            expect(problems, most[d] <= d, "the land's ground " + d + " block(s) round it is " + most[d] + " blocks off its floor's level "
                    + "(the most 1 block out, from " + (groundY - 10) + " up: " + steepest + ")");
        }
        fail(problems);
    }

    /** The land's own ground: solid, dry, not a tree's log or leaves or a huge mushroom. */
    private static boolean isGround(BlockState state)
    {
        return state.blocksMotion() && state.getFluidState().isEmpty() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
                && !(state.getBlock() instanceof net.minecraft.world.level.block.HugeMushroomBlock);
    }

    /** The blocks of a column from {@code y} up, {@code count} of them (short names), for a failure's message. */
    private static String profile(Run run, int x, int y, int z, int count)
    {
        List<String> out = new ArrayList<>();
        for (int i = 0; i < count; i++)
        {
            out.add(name(run.overworld.getBlockState(new BlockPos(x, y + i, z))).replace("minecraft:", ""));
        }
        return String.join(" ", out);
    }

    /** No tree's log or leaves (or huge mushroom) over the footprint's ground once the trader has tidied it. */
    private static void checkNoTrees(Run run)
    {
        List<String> problems = new ArrayList<>();
        BoundingBox foot = run.chapel.footprint();
        for (BlockPos pos : BlockPos.betweenClosed(foot.minX(), foot.minY() + 1, foot.minZ(), foot.maxX(), foot.maxY(), foot.maxZ()))
        {
            BlockState state = run.overworld.getBlockState(pos);
            expect(problems, !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && !(state.getBlock() instanceof net.minecraft.world.level.block.HugeMushroomBlock),
                    name(state) + " at " + pos.toShortString());
        }
        fail(problems);
    }

    /** The broken Dross frame behind the altar: there, and unbreakable. */
    private static void checkFrame(Run run)
    {
        List<String> problems = new ArrayList<>();
        int pieces = 0;
        for (Map.Entry<BlockPos, BlockState> entry : TraderHut.DESIGN.blocks(run.chapel.center(), run.chapel.facing()).entrySet())
        {
            if (entry.getValue().is(ModBlocks.DROSS_PORTAL_FRAME.get()))
            {
                pieces++;
                BlockState there = run.overworld.getBlockState(entry.getKey());
                expect(problems, there.is(ModBlocks.DROSS_PORTAL_FRAME.get()), name(there) + " at " + entry.getKey().toShortString()
                        + ", not a piece of the frame");
                expect(problems, there.getDestroySpeed(run.overworld, entry.getKey()) < 0.0F, "the frame piece at "
                        + entry.getKey().toShortString() + " can be broken");
            }
        }
        expect(problems, pieces > 0, "the design has no frame pieces");
        fail(problems);
    }

    /**
     * The path laid: of its columns, at least {@value #PATH_MIN_PAVED} have the village's path block (or a plank on water)
     * on their ground (the rest may be stone, a tree, a village block over it...). A chapel on its own has no path.
     */
    private static void checkPath(Run run)
    {
        if (run.home.path().isEmpty())
        {
            LOGGER.info("[chapel test] The chapel stands on its own: no path");
            return;
        }
        int cells = 0;
        int paved = 0;
        List<String> not = new ArrayList<>();
        for (long cell : RiftChapelPathPiece.cells(run.home.path(), TraderVillages.PATH_WIDTH / 2))
        {
            cells++;
            int x = ChunkPos.getX(cell);
            int z = ChunkPos.getZ(cell);
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, run.overworld.getHeight(net.minecraft.world.level.levelgen.Heightmap.Types.WORLD_SURFACE, x, z), z);
            boolean found = false;
            for (int i = 0; i < 12 && !found; i++)
            {
                BlockState state = run.overworld.getBlockState(pos.move(0, -1, 0));
                found = state.is(run.home.pathBlock()) || state.is(Blocks.OAK_PLANKS) || state.is(Blocks.DIRT_PATH);
            }
            paved += found ? 1 : 0;
            if (!found && not.size() < 8)
            {
                not.add(x + ", " + z + ": " + profile(run, x, run.home.chapel().center().getY() - 6, z, 14));
            }
        }
        double share = cells == 0 ? 0.0D : paved / (double) cells;
        LOGGER.info("[chapel test] The path ({} blocks long, of {}): {} of its {} columns paved ({}%); not paved, e.g. {}",
                TraderVillages.pathLength(run.home.path()), name(run.home.pathBlock().defaultBlockState()), paved, cells,
                String.format("%.1f", 100.0D * share), not);
        if (share < PATH_MIN_PAVED)
        {
            throw new GameTestAssertException("only " + paved + " of the path's " + cells + " columns are paved: " + not);
        }
    }

    /**
     * The "once a village" rule ({@link TraderVillageData}): the world's trader is recorded with his village; of the extra
     * traders the test put by the chapel, the one from the same village and the one from another village are removed on
     * their first tick, the spawn-egg one stays (and is then removed by the test).
     */
    private static void checkRule(Run run)
    {
        List<String> problems = new ArrayList<>();
        TraderVillageData data = TraderVillageData.get(run.overworld);
        expect(problems, run.traderId != null && run.traderId.equals(data.trader()), "the world's trader is recorded as " + data.trader()
                + ", not " + run.traderId);
        expect(problems, data.hadTrader(run.home.village()), "his village isn't recorded as having had the trader");
        expect(problems, run.sameVillage != null && run.sameVillage.isRemoved(), "a second trader from his village wasn't turned away");
        expect(problems, run.otherVillage != null && run.otherVillage.isRemoved(), "a trader from another village wasn't turned away");
        expect(problems, run.egg != null && !run.egg.isRemoved(), "a spawn-egg trader was turned away");
        LOGGER.info("[chapel test] The rule: the world's trader {}, {} village(s) had him; the second trader from his village removed: {}, "
                        + "the one from another village removed: {}, the spawn-egg trader kept: {}", data.trader(), data.villageCount(),
                run.sameVillage != null && run.sameVillage.isRemoved(), run.otherVillage != null && run.otherVillage.isRemoved(),
                run.egg != null && !run.egg.isRemoved());
        run.removeRuleTraders();
        fail(problems);
    }

    /** Exactly one trader in and round the chapel (as far as he may wander), at home there; his UUID. */
    private static UUID checkOneTrader(Run run)
    {
        List<DrossTrader> traders = run.traders();
        if (traders.size() != 1)
        {
            throw new GameTestAssertException(traders.size() + " traders in and round " + run.chapel.describe() + ", not one");
        }
        DrossTrader trader = traders.get(0);
        List<String> problems = new ArrayList<>();
        expect(problems, run.chapel.traderSpot().equals(trader.getHome()), "his home is " + trader.getHome() + ", not his spot in the aisle "
                + run.chapel.traderSpot().toShortString());
        expect(problems, run.chapel.footprint().equals(trader.getHutBox()), "his hut is " + trader.getHutBox() + ", not the chapel's footprint "
                + run.chapel.footprint());
        expect(problems, trader.isPersistenceRequired(), "he could despawn");
        fail(problems);
        return trader.getUUID();
    }

    /**
     * A player at the chapel: the trader's first right-click gives the opening speech; {@code /dross goto trader} lands at
     * its door, facing it; a Weathered Letter held there names it (waited for: it's written off the server thread).
     */
    private static void checkVisitor(Run run)
    {
        List<String> problems = new ArrayList<>();
        TestPlayer player = TestPlayer.join(run.overworld, "chapel-test-player");
        run.visitor = player;
        BlockPos near = run.chapel.traderSpot().relative(run.chapel.facing(), 24);
        player.teleportTo(run.overworld, near.getX() + 0.5D, near.getY() + 8, near.getZ() + 0.5D, 0.0F, 0.0F);
        DrossTrader trader = run.traders().get(0);
        trader.mobInteract(player, InteractionHand.MAIN_HAND);
        expect(problems, said(player, "dross.trader.dialogue.intro"), "his first right-click didn't give the opening speech (told: "
                + player.messages.stream().map(Component::getString).toList() + ")");
        int result = player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "dross goto trader");
        BlockPos door = run.chapel.doorstep();
        double off = Math.sqrt(Mth.square(player.getX() - (door.getX() + 0.5D)) + Mth.square(player.getZ() - (door.getZ() + 0.5D)));
        expect(problems, result == 1 && off < 0.6D && Math.abs(player.getY() - door.getY()) < 1.01D
                        && Math.abs(Mth.wrapDegrees(player.getYRot() - run.chapel.doorYaw())) < 1.0F,
                "/dross goto trader returned " + result + " and put the player at " + player.position() + " facing " + player.getYRot()
                        + ", not at the door " + door.toShortString() + " facing " + run.chapel.doorYaw());
        LOGGER.info("[chapel test] The trader's first right-click: {}; /dross goto trader put the player at {} facing {}", player.messages.stream()
                .map(Component::getString).toList(), player.position(), player.getYRot());
        // /dross trader sendhome: the nearest loaded trader (this one, moved to his doorstep first) is back on his spot. (Moved
        // within the chapel's own chunk: moved into a chunk whose entities aren't loaded yet, Minecraft hides an entity from
        // every search until they are, which is how a first version of this check once saw the command pick another trader.)
        BlockPos step = run.chapel.doorstep();
        trader.teleportTo(step.getX() + 0.5D, step.getY(), step.getZ() + 0.5D);
        net.minecraft.commands.CommandSourceStack source = player.createCommandSourceStack().withPermission(2);
        List<String> loaded = new ArrayList<>();
        for (DrossTrader other : run.overworld.getEntities(com.reg21meme.dross.registry.ModEntities.TRADER.get(), t -> true))
        {
            loaded.add(other.getUUID().toString().substring(0, 8) + " at " + other.blockPosition().toShortString() + " home " + other.getHome()
                    + " " + Math.round(Math.sqrt(other.distanceToSqr(source.getPosition()))) + " blocks off");
        }
        LOGGER.info("[chapel test] Before /dross trader sendhome from {}: the traders loaded: {}", source.getPosition(), loaded);
        int home = player.server.getCommands().performPrefixedCommand(source, "dross trader sendhome");
        BlockPos spot = run.chapel.traderSpot();
        expect(problems, home == 1 && trader.blockPosition().equals(spot), "/dross trader sendhome returned " + home + " and left him ("
                + trader.getUUID().toString().substring(0, 8) + ") at " + trader.blockPosition().toShortString() + ", not on his spot "
                + spot.toShortString() + "; the command's source was at " + source.getPosition() + " in " + source.getLevel().dimension().location()
                + "; the traders loaded: " + loaded);
        run.letter = WeatheredLetterItem.createBlank();
        player.getInventory().add(run.letter);
        fail(problems);
    }

    /** True if the player was told this line of the trader's ({@code <Dross Trader> line}). */
    private static boolean said(TestPlayer player, String key)
    {
        for (Component message : player.messages)
        {
            if (message.getContents() instanceof TranslatableContents chat && "chat.type.text".equals(chat.getKey()) && chat.getArgs().length == 2
                    && chat.getArgs()[1] instanceof Component line && line.getContents() instanceof TranslatableContents said
                    && key.equals(said.getKey()))
            {
                return true;
            }
        }
        return false;
    }

    /** The letter names the trader's chapel, rounded to 50, in the village's wording (or the wilds', for a chapel on its own). */
    private static void checkLetter(Run run)
    {
        ItemStack letter = run.heldLetter();
        ListTag pages = letter.getTag() == null ? new ListTag() : letter.getTag().getList(ReadableItem.PAGES_TAG, Tag.TAG_STRING);
        List<TranslatableContents> keys = new ArrayList<>();
        for (int i = 0; i < pages.size(); i++)
        {
            Component page = Component.Serializer.fromJson(pages.getString(i));
            if (page != null && page.getContents() instanceof TranslatableContents t)
            {
                keys.add(t);
            }
        }
        BlockPos spot = run.chapel.traderSpot();
        // (a page read back from its saved text has its numbers as text)
        String[] want = {String.valueOf(round50(spot.getX())), String.valueOf(round50(spot.getZ()))};
        String suffix = run.home.inVillage() ? "" : "_wild";
        TranslatableContents where = keys.size() == 4 ? keys.get(3) : null;
        boolean names = where != null && ((where.getKey().endsWith(".location" + suffix) && where.getArgs().length == 3
                && want[0].equals(String.valueOf(where.getArgs()[1])) && want[1].equals(String.valueOf(where.getArgs()[2])))
                || (where.getKey().endsWith(".location_near_spawn" + suffix) && where.getArgs().length == 2
                && want[0].equals(String.valueOf(where.getArgs()[0])) && want[1].equals(String.valueOf(where.getArgs()[1]))));
        LOGGER.info("[chapel test] The Weathered Letter: {}", keys.stream().map(t -> t.getKey() + Arrays.toString(t.getArgs())).toList());
        if (!names || !keys.get(1).getKey().endsWith(".page2_intro" + suffix))
        {
            throw new GameTestAssertException("the letter's pages are " + keys.stream().map(t -> t.getKey() + Arrays.toString(t.getArgs())).toList()
                    + ", not the " + (run.home.inVillage() ? "village's" : "wilds'") + " wording naming " + Arrays.toString(want));
        }
    }

    private static int round50(int coordinate)
    {
        return (int) Math.round(coordinate / (double) WeatheredLetterItem.LOCATION_ROUNDING) * WeatheredLetterItem.LOCATION_ROUNDING;
    }

    // ---------------------------------------------------------------------------------------------
    // The run: its chapel, its ticket, its waits and its watchdog
    // ---------------------------------------------------------------------------------------------

    /** "Not yet": a wait's check that isn't done. Minecraft tries it again the next tick. */
    private static final class Waiting extends GameTestAssertException
    {
        Waiting(String message)
        {
            super(message);
        }
    }

    private static final class Run
    {
        final GameTestHelper helper;
        final ServerLevel overworld;
        final RiftChapels.Home home;
        final RiftChapels.Chapel chapel;
        final ChunkPos middle;
        @Nullable
        RuntimeException failure;
        long waitingSince = -1L;
        boolean held;
        boolean skipped;
        @Nullable
        UUID traderId;
        @Nullable
        TestPlayer visitor;
        @Nullable
        ItemStack letter;
        /** The rule's extra traders: one from his village, one from another, a spawn-egg one. */
        @Nullable
        DrossTrader sameVillage;
        @Nullable
        DrossTrader otherVillage;
        @Nullable
        DrossTrader egg;

        /** The world's chapel. */
        Run(GameTestHelper helper)
        {
            this.helper = helper;
            this.overworld = helper.getLevel().getServer().overworld();
            this.home = RiftChapels.home(this.overworld).orElseThrow(() -> new GameTestAssertException("the world has no home for the trader"));
            this.chapel = this.home.chapel();
            this.middle = new ChunkPos(this.chapel.traderSpot());
            LOGGER.info("[chapel test] Checking {}, in {}", this.home.describe(), this.overworld.getBiome(this.chapel.center()).unwrapKey()
                    .map(key -> key.location().toString()).orElse("?"));
        }

        /** True if the chapel's chunks are among those Minecraft keeps loaded round the spawn. */
        boolean inSpawnChunks()
        {
            ChunkPos spawn = new ChunkPos(this.overworld.getSharedSpawnPos());
            BoundingBox foot = this.chapel.footprint();
            int dx = Math.min(Math.abs((foot.minX() >> 4) - spawn.x), Math.abs((foot.maxX() >> 4) - spawn.x));
            int dz = Math.min(Math.abs((foot.minZ() >> 4) - spawn.z), Math.abs((foot.maxZ() >> 4) - spawn.z));
            return Math.max(dx, dz) <= SPAWN_CHUNKS;
        }

        /** Puts the rule's three extra traders by the chapel's door. */
        void spawnRuleTraders()
        {
            BlockPos at = this.chapel.doorstep().relative(this.chapel.facing(), 3);
            this.sameVillage = this.extra(at, this.home.village());
            this.otherVillage = this.extra(at.above(2), this.home.village() + 1L);
            this.egg = this.extra(at.above(4), null);
        }

        @Nullable
        private DrossTrader extra(BlockPos at, @Nullable Long village)
        {
            DrossTrader trader = com.reg21meme.dross.registry.ModEntities.TRADER.get().create(this.overworld);
            if (trader == null)
            {
                return null;
            }
            if (village != null)
            {
                trader.setVillage(village);
            }
            trader.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
            this.overworld.addFreshEntity(trader);
            return trader;
        }

        /** Removes the rule's extra traders (safe to call twice). */
        void removeRuleTraders()
        {
            for (DrossTrader trader : new DrossTrader[] {this.sameVillage, this.otherVillage, this.egg})
            {
                if (trader != null && !trader.isRemoved())
                {
                    trader.discard();
                }
            }
        }

        /**
         * Fails the test as soon as a step has failed: a wait takes any failed check for "not yet" and tries it again every
         * tick until the test's tick limit, so a failure found in a wait is kept and this second sequence fails the test
         * with it at once.
         */
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

        /** Runs a step; a real failure is remembered, logged and cleaned up (and given again by every later step). */
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
                LOGGER.error("[chapel test] failed: {}", e.getMessage());
                this.failure = e;
                this.leave();
                this.removeRuleTraders();
                this.release();
                throw e;
            }
        }

        /** A wait: still waiting (throws), or done; after {@value #WAIT_MILLIS} ms by the clock, a real failure. */
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

        void hold()
        {
            this.overworld.getChunkSource().addRegionTicket(TICKET, this.middle, TICKET_RADIUS, this.middle);
            this.held = true;
        }

        /** Safe to call twice. */
        void release()
        {
            if (this.held)
            {
                this.overworld.getChunkSource().removeRegionTicket(TICKET, this.middle, TICKET_RADIUS, this.middle);
                this.held = false;
            }
        }

        /** The stand-in player leaves (safe to call twice). */
        void leave()
        {
            if (this.visitor != null)
            {
                this.visitor.leave();
                this.visitor = null;
            }
        }

        /** The chunks the chapel and its tidy-up's land are in (chunk coordinates). */
        BoundingBox chunks()
        {
            BoundingBox foot = this.chapel.footprint();
            int r = RiftChapelTidy.REACH;
            return new BoundingBox((foot.minX() - r) >> 4, 0, (foot.minZ() - r) >> 4, (foot.maxX() + r) >> 4, 0, (foot.maxZ() + r) >> 4);
        }

        boolean loaded()
        {
            return this.loadState().startsWith("all");
        }

        /** True once none of the chapel's own chunks is loaded (nor their entities), and its trader isn't either. */
        boolean unloaded()
        {
            BoundingBox foot = this.chapel.footprint();
            for (int cx = foot.minX() >> 4; cx <= foot.maxX() >> 4; cx++)
            {
                for (int cz = foot.minZ() >> 4; cz <= foot.maxZ() >> 4; cz++)
                {
                    if (this.overworld.getChunkSource().getChunkNow(cx, cz) != null || this.overworld.areEntitiesLoaded(ChunkPos.asLong(cx, cz)))
                    {
                        return false;
                    }
                }
            }
            return this.traderId == null || this.overworld.getEntity(this.traderId) == null;
        }

        String loadState()
        {
            BoundingBox c = this.chunks();
            int total = 0;
            int full = 0;
            int entities = 0;
            for (int cx = c.minX(); cx <= c.maxX(); cx++)
            {
                for (int cz = c.minZ(); cz <= c.maxZ(); cz++)
                {
                    total++;
                    full += this.overworld.getChunkSource().getChunkNow(cx, cz) != null ? 1 : 0;
                    entities += this.overworld.areEntitiesLoaded(ChunkPos.asLong(cx, cz)) ? 1 : 0;
                }
            }
            return (full == total && entities == total ? "all " : "") + full + " of " + total + " chunks loaded, " + entities + " with their entities";
        }

        /** The traders in and round the chapel, as far as one may wander from it. */
        List<DrossTrader> traders()
        {
            AABB area = AABB.of(this.chapel.footprint()).inflate(64.0D, 16.0D, 64.0D);
            return this.overworld.getEntitiesOfClass(DrossTrader.class, area, t -> t != this.sameVillage && t != this.otherVillage && t != this.egg);
        }

        boolean tidied()
        {
            return this.traders().stream().anyMatch(DrossTrader::isChapelTidied);
        }

        String traderState()
        {
            return this.traders().stream().map(t -> t.getUUID() + " at " + t.blockPosition().toShortString() + (t.isChapelTidied() ? " tidied" : " not tidied"))
                    .toList() + "; " + this.loadState();
        }

        /** The letter in the visitor's inventory. */
        ItemStack heldLetter()
        {
            if (this.visitor != null)
            {
                for (int i = 0; i < this.visitor.getInventory().getContainerSize(); i++)
                {
                    ItemStack stack = this.visitor.getInventory().getItem(i);
                    if (stack.getItem() instanceof WeatheredLetterItem)
                    {
                        return stack;
                    }
                }
            }
            throw new GameTestAssertException("the visitor has no Weathered Letter");
        }

        /** Ticks the letter in the visitor's inventory (a stand-in player isn't ticked); true once it has its text. */
        boolean letterWritten()
        {
            ItemStack letter = this.heldLetter();
            letter.inventoryTick(this.overworld, this.visitor, 0, false);
            return ReadableItem.hasPages(letter);
        }

        /**
         * True (with a log line) if this world's chapel land was made before the chapels came with the land: no structure
         * start in its chunk, and its region file was already there when the server started.
         */
        boolean skipOldWorld()
        {
            ChunkPos start = this.chapel.startChunk();
            String region = "r." + start.getRegionX() + "." + start.getRegionZ() + ".mca";
            Structure structure = this.overworld.registryAccess().registryOrThrow(Registries.STRUCTURE).get(STRUCTURE);
            StructureStart found = structure == null ? null : this.overworld.getChunk(start.x, start.z).getStartForStructure(structure);
            if (!AtStart.regions.contains(region) || (found != null && found.isValid()))
            {
                return false;
            }
            LOGGER.info("[chapel test] skipped: this world's land at the chapel was made before the chapels came with the land; run the tests "
                    + "in a brand-new world");
            this.skipped = true;
            return true;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static void expect(List<String> problems, boolean ok, String problem)
    {
        if (!ok)
        {
            problems.add(problem);
        }
    }

    private static void fail(List<String> problems)
    {
        if (!problems.isEmpty())
        {
            int shown = Math.min(problems.size(), 12);
            throw new GameTestAssertException(problems.size() + " problem(s), the first " + shown + ": " + String.join("; ", problems.subList(0, shown)));
        }
    }

    private static String name(BlockState state)
    {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    /**
     * A stand-in player on the server's player list who remembers every chat message sent to him, in creative mode (nothing
     * hurts him). He gets an in-memory network channel (vanilla's {@code makeMockServerPlayerInLevel} crashes under Forge).
     */
    static final class TestPlayer extends ServerPlayer
    {
        final List<Component> messages = new ArrayList<>();

        private TestPlayer(ServerLevel level, String name)
        {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        }

        static TestPlayer join(ServerLevel level, String name)
        {
            TestPlayer player = new TestPlayer(level, name);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.setGameMode(GameType.CREATIVE);
            return player;
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay)
        {
            messages.add(message);
            super.sendSystemMessage(message, overlay);
        }

        /** Logs him out. Safe to call twice. */
        void leave()
        {
            if (server.getPlayerList().getPlayer(getUUID()) == this)
            {
                server.getPlayerList().remove(this);
            }
        }
    }

    /** GameTests are built on a structure template; this one (a single air block) is made in memory. */
    static void registerEmpty(MinecraftServer server, ResourceLocation id)
    {
        StructureTemplate template = server.getStructureManager().getOrCreate(id);
        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", intList(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
    }

    private static ListTag intList(int... values)
    {
        ListTag list = new ListTag();
        for (int value : values)
        {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    private RiftChapelGameTest() {}
}
