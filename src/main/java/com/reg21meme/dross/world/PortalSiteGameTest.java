package com.reg21meme.dross.world;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.command.DrossCommand;
import com.reg21meme.dross.portal.DrossPortalShape;
import com.reg21meme.dross.portal.DrossTeleporter;
import com.reg21meme.dross.portal.PortalOpening;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.villager.DrossCompass;
import com.reg21meme.dross.world.shrine.GroundFit;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.CompassItem;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for the portal site made by world generation: the Fallen Cathedral as the structure
 * {@code dross:portal_site}, at the spot the seed picks ({@link SiteFinder}), and what uses it. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}; only these, in a brand-new world: set {@link #NAMESPACE} to
 * {@code "dross_site"} and add {@code -PgameTestNamespaces=dross_site -PgameTestWorld=<a new folder>}. The test server's
 * Overworld is a normal world with a random seed ({@code run/server.properties}). They only exist in the development
 * environment.
 * <ul>
 *   <li>{@link #nothingHappensAtWorldStart}: when the server had started, the spot was already worked out (on its
 *       background thread) and not one chunk of the site or round it was loaded, and no part of it built.</li>
 *   <li>{@link #spotComesFromTheSeed}: the spot is 3,500-4,500 blocks from the spawn point (or the rung's own distances,
 *       with its WARN), its biomes in the rung's tag, no possible village or outpost start near it, its land as flat as the
 *       rung says; worked out again, on this thread (in under two seconds) and on another, it's the same.</li>
 *   <li>{@link #cathedralIsMadeWithTheLand}: once its chunks are made (a ticket loads them; waited for by the clock) and
 *       the tidy-up has run: the structure start is there; at least 99% of the cathedral's template blocks are in place
 *       (a stone brick the leak cracked counts); the frame is complete, unlit and its opening empty; every column round
 *       it is within d blocks of its ground level d blocks out, so its edges never step more than a block a block; no
 *       tree's log or leaves on it; the leak there; {@code /dross goto cathedral} and the hub's exit portal land in front
 *       of the frame facing it; a Dross Compass aims at the opening's middle; a player who joins is told where it is.
 *       In a world whose site's land was made before it came with the land, it's skipped with a log line.</li>
 * </ul>
 * A wait never fails by itself (Minecraft retries it until the test's tick limit): a wait that gives up keeps its failure
 * and a watchdog sequence fails the test with it ({@link Run#watch}).
 */
@GameTestHolder(PortalSiteGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class PortalSiteGameTest
{
    /** The tests' GameTest namespace: the mod's ({@code "dross_site"} to run only these). */
    static final String NAMESPACE = Dross.MODID;
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The tests need no blocks: a 1x1x1 air template, made in memory when the server starts (no file needed). Other
     * areas' tests that need no blocks use it too ({@code @GameTest(template = PortalSiteGameTest.EMPTY)}).
     */
    public static final String EMPTY = "portal_site_test_empty";
    private static final String BATCH = "dross_portal_site";
    /** Holds the site's chunks loaded while a test looks (always removed at the end). */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_site_test", Comparator.comparingLong(ChunkPos::toLong));
    /** A wait gives up after this long (real time: the test server runs ticks far faster than the game). */
    private static final long WAIT_MILLIS = 360_000L;
    /** The tests' tick limit, far past any wait (each wait ends by the clock first). */
    private static final int TIMEOUT_TICKS = 4_000_000;
    /** Working out the spot must take less than this (ms). */
    private static final long SPOT_MAX_MILLIS = 2_000L;
    /** The real world spawn is within this of the spawn point the site measures from (Minecraft looks 5 chunks round it). */
    private static final int SPAWN_SLACK = 150;
    /** At least this share of the cathedral's template blocks (not air) must be in place. */
    private static final double TEMPLATE_MIN_MATCH = 0.99D;
    /** After {@code /dross goto cathedral} or the hub's exit, within this many blocks (X, Y, Z) of the opening's middle. */
    private static final int NEAR_FRAME = 8;
    /** The stand-in player's name. */
    private static final String PLAYER = "site-test-player";

    /** What was true when the server had started (before any test ran). */
    private static final class AtStart
    {
        @Nullable
        static SiteSpot spot;
        static boolean available;
        /** The spot was already worked out (by its background thread) when the server had started. */
        static boolean ready;
        static long millis = -1L;
        /** How many of the tidy-up's chunks (the site's and round it) were loaded then. */
        static int siteChunksLoaded = -1;
        static long piecesBuilt = -1L;
        /** The region file holding the site's chunk was already there: its land was made before this run. */
        static boolean siteRegionExisted;
    }

    private PortalSiteGameTest() {}

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
        AtStart.available = SiteFinder.available(overworld);
        AtStart.ready = SiteFinder.spotIfReady(overworld) != null;
        // (the spot is normally long worked out by now, on its background thread; this waits for it if it isn't)
        AtStart.spot = SiteFinder.spot(overworld);
        if (AtStart.spot == null)
        {
            return;
        }
        AtStart.millis = AtStart.spot.millis();
        BoundingBox chunks = SiteTidy.chunks(AtStart.spot);
        int loaded = 0;
        for (int cx = chunks.minX(); cx <= chunks.maxX(); cx++)
        {
            for (int cz = chunks.minZ(); cz <= chunks.maxZ(); cz++)
            {
                loaded += overworld.getChunkSource().getChunkNow(cx, cz) != null ? 1 : 0;
            }
        }
        AtStart.siteChunksLoaded = loaded;
        AtStart.piecesBuilt = PortalSitePiece.timing()[0];
        ChunkPos start = AtStart.spot.startChunk();
        Path region = server.getWorldPath(LevelResource.ROOT).resolve("region").resolve("r." + start.getRegionX() + "." + start.getRegionZ() + ".mca");
        AtStart.siteRegionExisted = Files.exists(region);
        LOGGER.info("[site test] When the server had started: the site {}; {} of its {} chunks (and those round it) loaded, {} of its "
                        + "chunks built; its region file {}", AtStart.spot.describe(), loaded, chunks.getXSpan() * chunks.getZSpan(),
                AtStart.piecesBuilt, AtStart.siteRegionExisted ? "was already there (its land was made before this run)" : "wasn't there yet");
    }

    // ---------------------------------------------------------------------------------------------
    // Nothing at world start, and the spot from the seed
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = BATCH)
    public static void nothingHappensAtWorldStart(GameTestHelper helper)
    {
        List<String> problems = new ArrayList<>();
        expect(problems, AtStart.available, "this world's Overworld has no portal site (its structure set isn't one of its possible ones)");
        expect(problems, AtStart.spot != null, "the site had no spot when the server had started");
        expect(problems, AtStart.siteChunksLoaded == 0, AtStart.siteChunksLoaded + " of the site's chunks (or those round it) were loaded "
                + "when the server had started, not 0");
        expect(problems, AtStart.piecesBuilt == 0L, AtStart.piecesBuilt + " of the site's chunks were built before any test ran");
        expect(problems, AtStart.ready, "the spot wasn't worked out yet when the server had started (its background thread started "
                + "with the world)");
        fail(problems);
        // (its time is checked on a quiet server by spotComesFromTheSeed: at start it shares the computer with the spawn's land)
        LOGGER.info("[site test] Nothing of the site happened at world start; its spot came from the seed on its background thread in {} ms "
                + "(while the spawn's land was being made) without loading a chunk", AtStart.millis);
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void spotComesFromTheSeed(GameTestHelper helper)
    {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        SiteSpot spot = SiteFinder.spot(overworld);
        SiteFinder.Context ctx = SiteFinder.context(overworld);
        if (spot == null || ctx == null)
        {
            throw new GameTestAssertException("the site has no spot");
        }
        List<String> problems = new ArrayList<>();
        List<SiteRules.Rung> ladder = ctx.ladder();
        boolean laddered = spot.rung() < ladder.size();
        SiteRules.Rung rung = laddered ? ladder.get(spot.rung()) : ladder.get(ladder.size() - 1);
        int d = SiteFinder.distanceFromSpawn(ctx, spot.x(), spot.z());
        expect(problems, d >= rung.minDistance() && d <= rung.maxDistance(), "the spot is " + d + " blocks from the spawn point, not "
                + rung.minDistance() + "-" + rung.maxDistance());
        BlockPos spawn = overworld.getSharedSpawnPos();
        double real = Math.sqrt(Mth.square((double) (spot.x() - spawn.getX())) + Mth.square((double) (spot.z() - spawn.getZ())));
        expect(problems, real >= rung.minDistance() - SPAWN_SLACK && real <= rung.maxDistance() + SPAWN_SLACK, String.format(Locale.ROOT,
                "the spot is %.0f blocks from the world spawn %s", real, spawn.toShortString()));
        if (laddered)
        {
            // (read at its ground level, where it stands: the finder reads them at the land's smooth height)
            BoundingBox box = spot.footprint();
            BoundingBox ring = box.inflatedBy(SiteRules.WATER_CLEARANCE);
            int qy = QuartPos.fromBlock(spot.groundY());
            int worst = 2;
            String worstBiome = "";
            List<int[]> places = new ArrayList<>();
            for (int x : SiteFinder.samples(box.minX(), box.maxX(), 8))
            {
                for (int z : SiteFinder.samples(box.minZ(), box.maxZ(), 8))
                {
                    places.add(new int[] {x, z, 0});
                }
            }
            for (int x : SiteFinder.samples(ring.minX(), ring.maxX(), 8))
            {
                for (int z : SiteFinder.samples(ring.minZ(), ring.maxZ(), 8))
                {
                    places.add(new int[] {x, z, 1});
                }
            }
            int forbiddenUnder = 0;
            int forbiddenRound = 0;
            for (int[] place : places)
            {
                var biome = ctx.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(place[0]), qy, QuartPos.fromBlock(place[1]),
                        ctx.random().sampler());
                int fit = SiteRules.fit(biome);
                if (place[2] == 0)
                {
                    forbiddenUnder += fit < 0 ? 1 : 0;
                    if (fit < worst)
                    {
                        worst = fit;
                        worstBiome = biome.unwrapKey().map(k -> k.location().toString()).orElse("?") + " at " + place[0] + ", " + place[1];
                    }
                }
                else
                {
                    forbiddenRound += fit < 0 ? 1 : 0;
                }
            }
            var own = ctx.generator().getBiomeSource().getNoiseBiome(QuartPos.fromBlock(spot.x()), qy, QuartPos.fromBlock(spot.z()),
                    ctx.random().sampler());
            expect(problems, SiteRules.fit(own) >= SiteRules.needs(rung.biomes()), "the site column's biome "
                    + own.unwrapKey().map(k -> k.location().toString()).orElse("?") + " doesn't fit the rule " + rung.name());
            expect(problems, forbiddenUnder == 0, forbiddenUnder + " places under it are an ocean, river, beach or swamp (" + worstBiome + ")");
            // (every 8 blocks is finer than the finder looks: a corner of another biome may show, logged)
            LOGGER.info("[site test] Its biomes every 8 blocks at its ground level: under it the worst fit {} ({}); just round it "
                    + "{} of {} places forbidden", worst, worstBiome.isEmpty() ? "all preferred" : worstBiome, forbiddenRound,
                    places.size());
            var villages = new SiteVillages.Finder(ctx.generator(), ctx.random(), ctx.structures(), ctx.villages(), ctx.fullHeights())
                    .villagesNear(spot.footprint().inflatedBy(SiteRules.BLEND_MARGIN), SiteRules.VILLAGE_REACH + SiteRules.VILLAGE_CLEARANCE);
            expect(problems, villages.isEmpty(), "a village or outpost could start near it, in chunk(s) " + villages);
            expect(problems, spot.variation() >= 0 && spot.variation() <= rung.maxVariation(), "its land varies by " + spot.variation()
                    + ", more than the rule's " + rung.maxVariation());
        }
        // The same again, worked out afresh on this thread and on another.
        long start = System.nanoTime();
        SiteSpot again = SiteFinder.compute(ctx);
        long millis = (System.nanoTime() - start) / 1_000_000L;
        AtomicReference<SiteSpot> other = new AtomicReference<>();
        Thread thread = new Thread(() -> other.set(SiteFinder.compute(ctx)), "site test");
        thread.start();
        try
        {
            thread.join(60_000L);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        expect(problems, same(spot, again), "worked out again it's " + again.describe() + ", not " + spot.describe());
        expect(problems, other.get() != null && same(spot, other.get()), "worked out on another thread it's "
                + (other.get() == null ? "nothing" : other.get().describe()));
        expect(problems, millis < SPOT_MAX_MILLIS, "working it out again took " + millis + " ms (" + SPOT_MAX_MILLIS + " at most)");
        fail(problems);
        LOGGER.info("[site test] The spot from the seed: {}; worked out again in {} ms ({} on another thread): the same", spot.describe(),
                millis, other.get().millis());
        helper.succeed();
    }

    private static boolean same(SiteSpot a, SiteSpot b)
    {
        return a.x() == b.x() && a.z() == b.z() && a.groundY() == b.groundY() && a.rung() == b.rung() && a.variation() == b.variation();
    }

    // ---------------------------------------------------------------------------------------------
    // The cathedral, made with the land
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void cathedralIsMadeWithTheLand(GameTestHelper helper)
    {
        Run run = new Run(helper);
        run.watch();
        helper.startSequence()
                .thenExecute(() -> run.guard(run::hold))
                .thenWaitUntil(() -> run.waitFor("the site's chunks and their entities", run::loaded, run::loadState))
                .thenExecute(() -> run.guard(() -> {
                    if (run.skipOldWorld())
                    {
                        return;
                    }
                    long[] t = PortalSitePiece.timing();
                    long[] g = PortalSitePiece.groundWork();
                    LOGGER.info("[site test] The site's land is made: {} of its chunks built this run, {} ms in all, the slowest {} ms; after "
                            + "Minecraft's terrain adaptation the piece cut or filled {} columns, {} blocks of height in all, at most {} in one",
                            t[0], t[1] / 1_000_000L, t[2] / 1_000_000L, g[0], g[1], g[2]);
                }))
                .thenWaitUntil(() -> run.waitFor("the tidy-up", () -> run.skipped || PortalSiteData.get(run.overworld)
                        .isTidied(run.spot.frame().corner()), () -> "tidy-up finished: " + SiteTidy.isFinished()))
                .thenExecute(() -> run.guard(() -> {
                    if (run.skipped)
                    {
                        return;
                    }
                    LOGGER.info("[site test] The tidy-up: {}", PortalSiteData.get(run.overworld).getSummary());
                    checkStart(run);
                    checkBlocks(run);
                    checkFrame(run);
                    checkRing(run);
                    checkNoTrees(run);
                    checkArrivals(run);
                }))
                .thenExecute(() -> run.guard(run::release))
                .thenSucceed();
    }

    /** The structure start in the site's chunk. */
    private static void checkStart(Run run)
    {
        if (!SiteTidy.madeWithTheLand(run.overworld, run.spot))
        {
            throw new GameTestAssertException("there's no dross:portal_site structure start in the site's chunk " + run.spot.startChunk());
        }
    }

    /**
     * At least {@value #TEMPLATE_MIN_MATCH} of the template's blocks (not air) in place, a stone brick the leak cracked
     * counting; and the leak there (some cracked).
     */
    private static void checkBlocks(Run run)
    {
        List<String> problems = new ArrayList<>();
        Map<BlockPos, BlockState> blocks = templateBlocks(run.overworld, run.spot);
        int same = 0;
        int cracked = 0;
        int ores = 0;
        int airSame = 0;
        int air = 0;
        String firstWrong = null;
        Map<String, Integer> wrongs = new HashMap<>();
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet())
        {
            BlockState there = run.overworld.getBlockState(e.getKey());
            BlockState want = e.getValue();
            if (want.isAir())
            {
                air++;
                airSame += there.isAir() ? 1 : 0;
                continue;
            }
            if (there.is(want.getBlock()))
            {
                same++;
            }
            else if (want.is(Blocks.STONE_BRICKS) && there.is(Blocks.CRACKED_STONE_BRICKS))
            {
                same++;
                cracked++;
            }
            else if (oreSwap(want, there))
            {
                // the world's ores and blobs of diorite, granite, gravel... come after the surface structures (vanilla's too)
                same++;
                ores++;
            }
            else
            {
                firstWrong = firstWrong != null ? firstWrong : e.getKey().toShortString() + " is " + name(there) + ", not " + name(want);
                wrongs.merge(name(want) + " -> " + name(there), 1, Integer::sum);
            }
        }
        int solid = blocks.size() - air;
        double share = solid == 0 ? 0.0D : same / (double) solid;
        expect(problems, solid > 0, "the cathedral's template is empty or missing");
        expect(problems, share >= TEMPLATE_MIN_MATCH, String.format(Locale.ROOT, "only %.2f%% of its %d template blocks are in place (%s)",
                100.0D * share, solid, firstWrong));
        expect(problems, cracked > 0, "no stone brick near the frame was cracked by the leak");
        fail(problems);
        LOGGER.info(String.format(Locale.ROOT, "[site test] %.2f%% of the cathedral's %d template blocks are in place (%d stone bricks cracked "
                + "by the leak, %d stone blocks turned to the world's ores or stone blobs); %d of its %d air cells are still air; the others: %s",
                100.0D * share, solid, cracked, ores, airSame, air, wrongs.entrySet().stream()
                        .sorted(Map.Entry.<String, Integer>comparingByValue().reversed()).limit(8)
                        .map(w -> w.getKey() + " " + w.getValue()).toList()));
    }

    /**
     * True if a stone block of the template is now one of the world's ores or stone blobs (diorite, granite, andesite, tuff,
     * gravel, dirt...), which Minecraft's later decoration puts into stone.
     */
    private static boolean oreSwap(BlockState want, BlockState there)
    {
        boolean stone = want.is(BlockTags.STONE_ORE_REPLACEABLES) || want.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES);
        return stone && (there.is(BlockTags.STONE_ORE_REPLACEABLES) || there.is(BlockTags.DEEPSLATE_ORE_REPLACEABLES)
                || there.is(net.minecraftforge.common.Tags.Blocks.ORES) || there.is(BlockTags.DIRT) || there.is(Blocks.GRAVEL)
                || there.is(Blocks.TUFF) || there.is(Blocks.CLAY));
    }

    /** The frame: a complete Dross frame round an empty opening 3 x 5 along X, where the API says. */
    private static void checkFrame(Run run)
    {
        List<String> problems = new ArrayList<>();
        SiteFrame frame = run.spot.frame();
        PortalOpening expected = new PortalOpening(frame.openingMin(), frame.axis(), frame.openingWidth(), frame.openingHeight());
        expect(problems, frame.axis() == Direction.Axis.X && frame.openingWidth() == 3 && frame.openingHeight() == 5,
                "the frame is " + describe(expected) + ", not 3x5 along X");
        expect(problems, PortalSite.getFramePos(run.overworld).equals(frame.corner()) && PortalSite.getFrameAxis(run.overworld) == frame.axis()
                && PortalSite.getOpeningCenter(run.overworld).equals(frame.openingCenter()) && PortalSite.getOpeningWidth(run.overworld) == 3,
                "the PortalSite API doesn't give the spot's frame");
        Optional<DrossPortalShape> shape = DrossPortalShape.findAnyPortalShape(run.overworld, frame.openingCenter(), frame.axis());
        expect(problems, shape.isPresent() && opening(shape.get()).equals(expected), "there's no complete Dross frame round "
                + describe(expected) + " (" + shape.map(s -> "a frame round " + describe(opening(s))).orElse("none") + ")");
        for (int i = 0; i < frame.openingWidth(); i++)
        {
            for (int j = 0; j < frame.openingHeight(); j++)
            {
                BlockPos cell = frame.openingMin().offset(i, j, 0);
                BlockState state = run.overworld.getBlockState(cell);
                expect(problems, state.isAir(), "the opening at " + cell.toShortString() + " is " + name(state) + ", not air (unlit, empty)");
            }
        }
        for (int i = -1; i <= frame.openingWidth(); i++)
        {
            for (int j = -1; j <= frame.openingHeight(); j++)
            {
                boolean edge = i == -1 || i == frame.openingWidth() || j == -1 || j == frame.openingHeight();
                boolean corner = (i == -1 || i == frame.openingWidth()) && (j == -1 || j == frame.openingHeight());
                BlockPos cell = frame.openingMin().offset(i, j, 0);
                if (edge && !corner)
                {
                    BlockState state = run.overworld.getBlockState(cell);
                    expect(problems, state.is(ModBlocks.DROSS_PORTAL_FRAME.get()), "the frame at " + cell.toShortString() + " is " + name(state));
                }
            }
        }
        fail(problems);
        LOGGER.info("[site test] The frame is whole and unlit round an empty opening {}", describe(expected));
    }

    /**
     * The ground round the footprint: every column d blocks out (d below {@link SiteRules#BLEND_MARGIN}) within d of the
     * ground level, so walking off it never meets a step of more than a block a block (trees, plants and boulders on it
     * aren't ground). The steepest step between two neighbours there is logged.
     */
    private static void checkRing(Run run)
    {
        List<String> problems = new ArrayList<>();
        BoundingBox foot = run.spot.footprint();
        int g = run.spot.groundY();
        int margin = SiteRules.BLEND_MARGIN;
        int x0 = foot.minX() - margin;
        int z0 = foot.minZ() - margin;
        int w = foot.getXSpan() + 2 * margin;
        int h = foot.getZSpan() + 2 * margin;
        int[] ground = new int[w * h];
        int checked = 0;
        int wrong = 0;
        int furthest = 0;
        String firstWrong = null;
        for (int i = 0; i < w; i++)
        {
            for (int k = 0; k < h; k++)
            {
                int x = x0 + i;
                int z = z0 + k;
                int d = GroundFit.distanceOutside(foot, x, z);
                if (d == 0 || d >= margin)
                {
                    ground[i * h + k] = Integer.MIN_VALUE;
                    continue;
                }
                int top = groundTop(run.overworld, x, z);
                ground[i * h + k] = top;
                checked++;
                furthest = Math.max(furthest, Math.abs(top - g));
                if (Math.abs(top - g) > d)
                {
                    wrong++;
                    firstWrong = firstWrong != null ? firstWrong : x + ", " + z + " (" + d + " out) at y " + top;
                }
            }
        }
        int steepest = 0;
        int steepestOut = 0;
        for (int i = 0; i < w; i++)
        {
            for (int k = 0; k < h; k++)
            {
                int a = ground[i * h + k];
                if (a == Integer.MIN_VALUE)
                {
                    continue;
                }
                int d = GroundFit.distanceOutside(foot, x0 + i, z0 + k);
                for (int[] next : new int[][] {{i + 1, k}, {i, k + 1}})
                {
                    if (next[0] < w && next[1] < h && ground[next[0] * h + next[1]] != Integer.MIN_VALUE
                            && Math.abs(a - ground[next[0] * h + next[1]]) > steepest)
                    {
                        steepest = Math.abs(a - ground[next[0] * h + next[1]]);
                        steepestOut = d;
                    }
                }
            }
        }
        expect(problems, wrong == 0, wrong + " of " + checked + " columns round the site are further from its ground level y " + g
                + " than they are from it (the first: " + firstWrong + ")");
        fail(problems);
        LOGGER.info("[site test] All {} columns round the site (out to {} blocks) step back to the land at most a block a block; the furthest "
                + "{} from the ground level y {}, the steepest step between two of them {} ({} blocks out: the land's own there)", checked,
                margin - 1, furthest, g, steepest, steepestOut);
    }

    /** The ground's top in a column: under trees, plants, snow layers and boulders (water counts as its top). */
    private static int groundTop(ServerLevel level, int x, int z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z), z);
        for (; pos.getY() > level.getMinBuildHeight(); pos.move(Direction.DOWN))
        {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.is(Blocks.MOSSY_COBBLESTONE)
                    || state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                    || state.is(Blocks.BEE_NEST) || (state.canBeReplaced() && state.getFluidState().isEmpty()) || !state.blocksMotion()
                    && state.getFluidState().isEmpty())
            {
                continue;
            }
            return pos.getY();
        }
        return level.getMinBuildHeight();
    }

    /** No log, leaf or huge mushroom on the footprint above its ground that isn't the cathedral's own. */
    private static void checkNoTrees(Run run)
    {
        BoundingBox foot = run.spot.footprint();
        var own = SiteTidy.templateBlocks(run.overworld, run.spot);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int trees = 0;
        int logs = 0;
        int besideOwn = 0;
        String first = null;
        for (int x = foot.minX(); x <= foot.maxX(); x++)
        {
            for (int z = foot.minZ(); z <= foot.maxZ(); z++)
            {
                int top = run.overworld.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                for (int y = run.spot.groundY() + 1; y <= top; y++)
                {
                    BlockState state = run.overworld.getBlockState(pos.set(x, y, z));
                    boolean tree = state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.is(Blocks.MUSHROOM_STEM)
                            || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK);
                    if (tree && !own.contains(pos.asLong()))
                    {
                        trees++;
                        logs += state.is(BlockTags.LOGS) ? 1 : 0;
                        boolean byOwn = false;
                        for (Direction dir : Direction.values())
                        {
                            byOwn |= own.contains(pos.relative(dir).asLong());
                        }
                        besideOwn += byOwn ? 1 : 0;
                        first = first != null ? first : name(state) + " at " + pos.toShortString();
                    }
                }
            }
        }
        if (trees > 0)
        {
            throw new GameTestAssertException(trees + " blocks of trees or huge mushrooms on the footprint after the tidy-up (" + logs
                    + " logs; " + besideOwn + " beside the cathedral's own blocks; the first: " + first + ")");
        }
        LOGGER.info("[site test] No tree on the cathedral after the tidy-up");
    }

    /**
     * {@code /dross goto cathedral} and the hub's exit portal land in front of the frame (its +Z side) facing it; a Dross
     * Compass aims at the opening's middle; the stand-in player who joined was told where the site is, once.
     */
    private static void checkArrivals(Run run)
    {
        List<String> problems = new ArrayList<>();
        SiteFrame frame = run.spot.frame();
        BlockPos center = frame.openingCenter();
        TestPlayer player = TestPlayer.join(run.overworld, PLAYER);
        try
        {
            int told = 0;
            for (Component message : player.messages)
            {
                if (message.getContents() instanceof TranslatableContents t && SiteAnnouncer.LOCATION_KEY.equals(t.getKey()))
                {
                    told++;
                    expect(problems, Arrays.equals(t.getArgs(), new Object[] {center.getX(), center.getY(), center.getZ()}),
                            "the join message says " + Arrays.toString(t.getArgs()) + ", not the opening's middle " + center.toShortString());
                }
            }
            expect(problems, told == 1, "on joining the player was told where the site is " + told + " times, not once");
            int result = player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2),
                    "dross goto cathedral");
            expect(problems, result == 1, "/dross goto cathedral returned " + result);
            String landed = inFront(player.serverLevel() == run.overworld ? player.position() : null, player.getYRot(), frame);
            expect(problems, landed == null, "/dross goto cathedral put the player at " + player.position() + " facing "
                    + player.getYRot() + ": " + landed);
            ArmorStand stand = EntityType.ARMOR_STAND.create(run.overworld);
            PortalInfo info = stand == null ? null : new DrossTeleporter().getPortalInfo(stand, run.overworld, level -> null);
            String through = info == null ? "no landing" : inFront(info.pos, info.yRot, frame);
            expect(problems, through == null, "the hub's exit lands at " + (info == null ? "nothing" : info.pos + " facing " + info.yRot)
                    + ": " + through);
            ItemStack compass = DrossCompass.create(run.overworld);
            CompoundTag tag = compass.getOrCreateTag();
            BlockPos aim = tag.contains(CompassItem.TAG_LODESTONE_POS) ? NbtUtils.readBlockPos(tag.getCompound(CompassItem.TAG_LODESTONE_POS)) : null;
            expect(problems, center.equals(aim), "the Dross Compass aims at " + aim + ", not the opening's middle " + center.toShortString());
            fail(problems);
            LOGGER.info("[site test] /dross goto cathedral put the player at {} facing {}; the hub's exit lands at {}; the compass aims at {}",
                    player.position(), player.getYRot(), info.pos, aim.toShortString());
        }
        finally
        {
            player.leave();
        }
    }

    /** Null if the spot is in front of the frame (its +Z side), near its opening, facing it (north); else what's wrong. */
    @Nullable
    private static String inFront(@Nullable Vec3 pos, float yaw, SiteFrame frame)
    {
        if (pos == null)
        {
            return "not in the Overworld";
        }
        BlockPos center = frame.openingCenter();
        if (Math.abs(pos.x - center.getX() - 0.5D) > NEAR_FRAME || Math.abs(pos.y - center.getY()) > NEAR_FRAME
                || Math.abs(pos.z - center.getZ() - 0.5D) > NEAR_FRAME)
        {
            return "not near the opening's middle " + center.toShortString();
        }
        if (pos.z <= center.getZ() + 0.5D)
        {
            return "not in front of the frame (its +Z side)";
        }
        return Math.abs(Mth.wrapDegrees(yaw - 180.0F)) < 1.0F ? null : "not facing the frame (north)";
    }

    // ---------------------------------------------------------------------------------------------
    // The run: its ticket, its waits and its watchdog
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
        final SiteSpot spot;
        /** The tidy-up's chunks (the footprint's and the ring round them), in chunk coordinates. */
        final BoundingBox chunks;
        final ChunkPos middle;
        final int radius;
        @Nullable
        RuntimeException failure;
        long waitingSince = -1L;
        boolean held;
        /** The world's site was made before it came with the land: the checks are skipped. */
        boolean skipped;

        Run(GameTestHelper helper)
        {
            this.helper = helper;
            this.overworld = helper.getLevel().getServer().overworld();
            SiteSpot found = SiteFinder.spot(this.overworld);
            if (found == null)
            {
                throw new GameTestAssertException("the site has no spot");
            }
            this.spot = found;
            this.chunks = SiteTidy.chunks(found);
            this.middle = new ChunkPos((chunks.minX() + chunks.maxX()) / 2, (chunks.minZ() + chunks.maxZ()) / 2);
            this.radius = Math.max(chunks.getXSpan(), chunks.getZSpan()) / 2 + 1;
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
                LOGGER.error("[site test] failed: {}", e.getMessage());
                this.failure = e;
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
            this.overworld.getChunkSource().addRegionTicket(TICKET, this.middle, this.radius, this.middle);
            this.held = true;
        }

        /** Safe to call twice. */
        void release()
        {
            if (this.held)
            {
                this.overworld.getChunkSource().removeRegionTicket(TICKET, this.middle, this.radius, this.middle);
                this.held = false;
            }
        }

        boolean loaded()
        {
            return this.loadState() == null;
        }

        /** Null once every chunk the tidy-up needs is fully loaded with its entities; otherwise how far it has got. */
        @Nullable
        String loadState()
        {
            int total = 0;
            int full = 0;
            int entities = 0;
            for (int cx = this.chunks.minX(); cx <= this.chunks.maxX(); cx++)
            {
                for (int cz = this.chunks.minZ(); cz <= this.chunks.maxZ(); cz++)
                {
                    total++;
                    full += this.overworld.getChunkSource().getChunkNow(cx, cz) != null ? 1 : 0;
                    entities += this.overworld.areEntitiesLoaded(ChunkPos.asLong(cx, cz)) ? 1 : 0;
                }
            }
            return full == total && entities == total ? null : full + " of " + total + " chunks loaded, " + entities + " with their entities";
        }

        /**
         * True (with a log line) if this world's site was made before it came with the land: no structure start in its
         * chunk, and its region file was already there when the server started. (Without the region file, a missing start is
         * a failure: {@link #checkStart}.)
         */
        boolean skipOldWorld()
        {
            if (!AtStart.siteRegionExisted || SiteTidy.madeWithTheLand(this.overworld, this.spot))
            {
                return false;
            }
            LOGGER.info("[site test] skipped: this world's site land was made before the site came with the land (its region file was "
                    + "there when the server started, and its chunk has no dross:portal_site start); run the tests in a brand-new world");
            this.skipped = true;
            return true;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** Every block of the cathedral's template (air too), where it goes at the site. */
    private static Map<BlockPos, BlockState> templateBlocks(ServerLevel level, SiteSpot spot)
    {
        Map<BlockPos, BlockState> out = new HashMap<>();
        Optional<StructureTemplate> found = level.getStructureManager().get(PortalCathedral.design().template());
        if (found.isEmpty())
        {
            return out;
        }
        CompoundTag tag = found.get().save(new CompoundTag());
        ListTag palette = tag.contains("palette", Tag.TAG_LIST) ? tag.getList("palette", Tag.TAG_COMPOUND)
                : tag.getList("palettes", Tag.TAG_LIST).getList(0);
        List<BlockState> states = new ArrayList<>();
        for (int i = 0; i < palette.size(); i++)
        {
            states.add(NbtUtils.readBlockState(BuiltInRegistries.BLOCK.asLookup(), palette.getCompound(i)));
        }
        BlockPos origin = PortalCathedral.templateOrigin(spot.x(), spot.z(), spot.groundY());
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++)
        {
            CompoundTag block = blocks.getCompound(i);
            ListTag p = block.getList("pos", Tag.TAG_INT);
            out.put(origin.offset(p.getInt(0), p.getInt(1), p.getInt(2)), states.get(block.getInt("state")));
        }
        return out;
    }

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

    private static PortalOpening opening(DrossPortalShape shape)
    {
        return new PortalOpening(shape.getMinCorner(), shape.getAxis(), shape.getWidth(), shape.getHeight());
    }

    private static String describe(PortalOpening opening)
    {
        return opening.minCorner().toShortString() + " along " + opening.axis() + ", " + opening.width() + "x" + opening.height();
    }

    private static String name(BlockState state)
    {
        return String.valueOf(BuiltInRegistries.BLOCK.getKey(state.getBlock()));
    }

    // ---------------------------------------------------------------------------------------------
    // The stand-in player
    // ---------------------------------------------------------------------------------------------

    /**
     * A stand-in player on the server's player list (so joining fires the normal login event) who remembers every chat
     * message sent to him. Like the enchant area's test owner, he gets an in-memory network channel (vanilla's
     * {@code makeMockServerPlayerInLevel} crashes under Forge).
     */
    private static final class TestPlayer extends ServerPlayer
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
            // creative: monsters ignore him and nothing hurts him before the checks
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

    // ---------------------------------------------------------------------------------------------
    // The empty template
    // ---------------------------------------------------------------------------------------------

    /** GameTests are built on a structure template; this one (a single air block) is made in memory. */
    private static void registerEmpty(MinecraftServer server, ResourceLocation id)
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
}
