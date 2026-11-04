package com.reg21meme.dross.portal;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.portal.camp.HubCamp;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.villager.Survivor;
import com.reg21meme.dross.villager.SurvivorGrounds;
import com.reg21meme.dross.villager.SurvivorRole;
import com.reg21meme.dross.villager.SurvivorSpawner;
import com.reg21meme.dross.world.PortalSite;
import com.reg21meme.dross.world.shrine.GroundFit;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
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
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for the hub at Dross 0,0, made with the land ({@link DrossHubStructure}), and for finding
 * safe ground. Run them with {@code .\gradlew.bat runGameTestServer --console=plain}; only these, in a brand-new world:
 * set {@link #NAMESPACE} to {@code "dross_hub"} and add {@code -PgameTestNamespaces=dross_hub -PgameTestWorld=<a new
 * folder>}. They only exist in the development environment.
 * <ul>
 *   <li>{@link #nothingHappensAtWorldStart}: when the server had started, no chunk of the Dross was loaded and no part of
 *       the hub built, and working out its layout loaded nothing (and took well under a second).</li>
 *   <li>{@link #hubIsMadeWithTheLand}: once the hub's chunks are made (a ticket loads them; waited for by the clock), its
 *       structure start is there; the layout is the spire's (footprint X -25..49, Z -34..38, exit 3x5 along X on the
 *       centre, 9 over the ground, the arrival on the step in front of it facing south, the camp's rest spots) and the
 *       same as when the server started; at least 99% of the spire's and the camp's template blocks are in place; the
 *       exit is lit; no log or leaves of a tree in the footprint; every column of the staircase ring has its ground where
 *       {@link DrossHubGround#target} says (from the generator's own heights), and no step between two is steeper than
 *       the land's own or a block; the arrival spot is free to stand on; exactly one Blind Oracle and one Ragged Seeker,
 *       each other's partners, their homes the camp's. In a world whose hub was made before it came with the land (its
 *       region file already there when the server started, and no structure start), it's skipped with a log line.</li>
 *   <li>{@link #survivorsComeOnlyOnce}: the hub's chunks loaded, unloaded and loaded again: still exactly the same two
 *       survivors.</li>
 *   <li>{@link #arrivalThroughPortal}: an armor stand sent from the Overworld through {@link DrossTeleporter} lands on the
 *       layout's arrival spot facing south, standing on the step.</li>
 *   <li>{@link #gotoHubLandsOnTheStep}: {@code /dross goto hub}'s teleport puts a player on that same spot.</li>
 *   <li>{@link #exitIsRelit}: with its portal blocks gone (the frame whole), {@code /dross goto hub} lights the exit again.</li>
 *   <li>{@link #safeLandingOnRoughGround}: on a platform built high in the Dross's sky far from the hub, with a tree, a
 *       block floating 20 blocks up, a column of lava and a giant blightcap, {@link DrossTeleporter#findSafeSpot} lands
 *       under the floating block on the platform, never in the trunk's, the lava's or the stem's column, never on leaves,
 *       logs, a mushroom cap or lava.</li>
 * </ul>
 * A wait never fails by itself (Minecraft retries it until the test's tick limit): a wait that gives up keeps its failure
 * and a watchdog sequence fails the test with it ({@link Run#watch}).
 */
@GameTestHolder(DrossHubGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DrossHubGameTest
{
    /** The tests' GameTest namespace: the mod's ({@code "dross_hub"} to run only these). */
    static final String NAMESPACE = Dross.MODID;
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The tests' own empty template (a block of air), made when the test server starts. */
    private static final String EMPTY = "hub_test_empty";
    private static final ResourceLocation EMPTY_ID = new ResourceLocation(NAMESPACE, EMPTY);
    private static final String BATCH = "dross_hub";
    private static final String RELOAD_BATCH = "dross_hub_reload";
    private static final String RELIGHT_BATCH = "dross_hub_relight";
    private static final String WARM_BATCH = "dross_hub_warm";
    /** Holds the hub's chunks loaded while a test looks (no time limit; always removed at the end). */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_hub_test", Comparator.comparingLong(ChunkPos::toLong));
    /** The ticket's radius round the hub's middle chunk: every chunk the hub's piece touches is loaded with its entities. */
    private static final int HOLD_RADIUS = 6;
    /** A wait gives up after this long (real time: the test server runs ticks far faster than the game). */
    private static final long WAIT_MILLIS = 240_000L;
    /** The tests' tick limit, far past any wait (each wait ends by the clock first). */
    private static final int TIMEOUT_TICKS = 2_000_000;
    /** Working out the layout from the seed must take less than this (ms). */
    private static final long LAYOUT_MAX_MILLIS = 1_000L;

    // The Shattered Spire, as built at the hub (read from its template, shattered_spire.nbt)
    /** The spire's frame opening is this many blocks above its ground level. */
    private static final int SPIRE_OPENING_ABOVE_GROUND = 9;
    /** Its footprint, seen from above, with its frame opening's middle column on 0, 0. */
    private static final int SPIRE_MIN_X = -25;
    private static final int SPIRE_MAX_X = 49;
    private static final int SPIRE_MIN_Z = -34;
    private static final int SPIRE_MAX_Z = 38;
    /** It reaches this far under its ground level, and this far over it. */
    private static final int SPIRE_BELOW = 2;
    private static final int SPIRE_ABOVE = 61;
    /** The arrival spot is the exit's lowest corner plus this: on the step in front of the frame. */
    private static final Vec3 SPIRE_ARRIVAL_OFFSET = new Vec3(1.5D, 0.0D, 1.5D);
    /** At least this share of the spire's and of the camp's template blocks must be at the hub. */
    private static final double TEMPLATE_MIN_MATCH = 0.99D;
    /** The staircase ring checked: every column up to this far outside the footprint (the ring and the row past it). */
    private static final int RING_CHECKED = DrossHub.HUB_BLEND_MARGIN + 1;

    // The rough ground for findSafeSpot: a platform high in the sky, far from the hub
    /** Where the platform's middle goes (X, Z in the Dross); then spares, in case the land is too high. */
    private static final int[][] LANDING_SPOTS = {{4000, 4000}, {-4000, 4000}, {4000, -4000}, {-4000, -4000}};
    /** The platform reaches this far from its middle (9x9). */
    private static final int PLATFORM_HALF = 4;
    /** It's this many blocks thick (its top block included). */
    private static final int PLATFORM_DEPTH = 4;
    /** Its top is at this height, high above any land (higher if the land there is). */
    private static final int PREFERRED_TOP = 270;
    /** And at least this many blocks above the land under it. */
    private static final int AIR_GAP = 6;
    /** A block floats this many blocks above the platform's middle. */
    private static final int FLOAT_HEIGHT = 20;
    /** A silverwood trunk this tall stands this far east of the middle, under a 3x3x2 canopy of leaves. */
    private static final int TRUNK_HEIGHT = 5;
    private static final int TREE_EAST = 2;
    /** A column of lava, 2 blocks (sunk into the platform's top and one above it), this far west of the middle. */
    private static final int LAVA_WEST = 2;
    /** A giant blightcap (a stem this tall under a 3x3 cap) stands this far south of the middle. */
    private static final int MUSHROOM_SOUTH = 3;
    private static final int MUSHROOM_STEM = 3;
    /** findSafeSpot looks this far round the tree's and the lava's columns (all of it on the platform). */
    private static final int LANDING_RADIUS = 2;


    /** What was true when the server had started (before any test ran). */
    private static final class AtStart
    {
        /** -1: the Dross wasn't loaded (or the snapshot wasn't taken). */
        static int drossChunks = -1;
        static long hubChunksBuilt = -1L;
        static int chunksAfterLayout = -1;
        static double layoutMillis = -1.0D;
        @Nullable
        static DrossHub.Layout layout;
        /** The region file holding the hub's middle chunk was already there: its land was made before this run. */
        static boolean hubRegionExisted;
    }

    private DrossHubGameTest() {}

    /** The tests' empty template, and what's true when the server has started (after every other mod's handlers). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        MinecraftServer server = event.getServer();
        registerEmpty(server);
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            return;
        }
        ChunkPos middle = new ChunkPos(DrossHub.spireCenter(0));
        Path region = DimensionType.getStorageFolder(dross.dimension(), server.getWorldPath(LevelResource.ROOT))
                .resolve("region").resolve("r." + middle.getRegionX() + "." + middle.getRegionZ() + ".mca");
        AtStart.hubRegionExisted = Files.exists(region);
        AtStart.drossChunks = dross.getChunkSource().getLoadedChunksCount();
        AtStart.hubChunksBuilt = DrossHubPiece.timing()[0];
        long start = System.nanoTime();
        AtStart.layout = DrossHub.layout(server).orElse(null);
        AtStart.layoutMillis = (System.nanoTime() - start) / 1.0E6D;
        AtStart.chunksAfterLayout = dross.getChunkSource().getLoadedChunksCount();
        LOGGER.info("[hub test] When the server had started: {} Dross chunks loaded, {} of the hub's chunks built; the layout took {} ms "
                        + "and left {} Dross chunks loaded; the hub's region file {}", AtStart.drossChunks, AtStart.hubChunksBuilt,
                String.format(Locale.ROOT, "%.1f", AtStart.layoutMillis), AtStart.chunksAfterLayout,
                AtStart.hubRegionExisted ? "was already there (its land was made before this run)" : "wasn't there yet");
        if ("1".equals(System.getenv(ARRIVAL_PROBE)))
        {
            // A probe, not a check: the first arrival in a new world, as a player's first trip makes it (its chunks made then).
            ArmorStand stand = EntityType.ARMOR_STAND.create(server.overworld());
            long arriving = System.nanoTime();
            net.minecraft.world.level.portal.PortalInfo info = DrossHub.arrival(dross, stand);
            LOGGER.info("[hub test] probe: the first arrival in this world took {} ms (the hub's chunks made then and there), landing at {}; "
                            + "{} of the hub's chunks built", (System.nanoTime() - arriving) / 1_000_000L, info.pos, DrossHubPiece.timing()[0]);
        }
    }

    /** With this environment variable set to 1, the test server times the first arrival in its world when it starts. */
    private static final String ARRIVAL_PROBE = "DROSS_HUB_ARRIVAL_PROBE";

    // ---------------------------------------------------------------------------------------------
    // Nothing at world start
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = BATCH)
    public static void nothingHappensAtWorldStart(GameTestHelper helper)
    {
        List<String> problems = new ArrayList<>();
        expect(problems, AtStart.drossChunks == 0, "when the server had started " + AtStart.drossChunks + " Dross chunks were loaded, not 0");
        expect(problems, AtStart.hubChunksBuilt == 0L, AtStart.hubChunksBuilt + " of the hub's chunks were built before any test ran");
        expect(problems, AtStart.layout != null, "the hub had no layout when the server had started");
        expect(problems, AtStart.chunksAfterLayout == AtStart.drossChunks, "working out the layout loaded "
                + (AtStart.chunksAfterLayout - AtStart.drossChunks) + " Dross chunks");
        expect(problems, AtStart.layoutMillis >= 0.0D && AtStart.layoutMillis < LAYOUT_MAX_MILLIS, "working out the layout took "
                + AtStart.layoutMillis + " ms (" + LAYOUT_MAX_MILLIS + " at most)");
        fail(problems);
        LOGGER.info("[hub test] Nothing of the hub happened at world start; its layout came from the seed in {} ms without loading a chunk "
                + "(the generator's heights: {} ms)", String.format(Locale.ROOT, "%.1f", AtStart.layoutMillis), DrossHubGround.lastMillis());
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // The hub, made with the land
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void hubIsMadeWithTheLand(GameTestHelper helper)
    {
        Run run = new Run(helper);
        run.watch();
        helper.startSequence()
                .thenExecute(() -> run.guard(run::hold))
                .thenWaitUntil(() -> run.waitFor("the hub's chunks and their entities", run::loaded, run::loadState))
                .thenExecute(() -> run.guard(() -> {
                    if (run.skipOldWorld())
                    {
                        return;
                    }
                    checkLayout(run);
                    checkBlocks(run);
                    checkRing(run);
                    checkSurvivors(run, "made with the land");
                    long[] t = DrossHubPiece.timing();
                    LOGGER.info("[hub test] The hub came with the land: {} of its chunks built this run, {} ms in all, the slowest {} ms; "
                            + "the ground level from the seed in {} ms", t[0], t[1] / 1_000_000L, t[2] / 1_000_000L, DrossHubGround.lastMillis());
                }))
                .thenExecute(() -> run.guard(run::release))
                .thenSucceed();
    }

    /** The layout: the spire's, from the seed, the same as when the server had started. */
    private static void checkLayout(Run run)
    {
        List<String> problems = new ArrayList<>();
        DrossHub.Layout layout = run.layout;
        int g = layout.groundY();
        expect(problems, g == DrossHub.groundY(run.dross), "the layout's ground level " + g + " isn't the seed's " + DrossHub.groundY(run.dross));
        expect(problems, AtStart.layout != null && sameLayout(AtStart.layout, layout), "the layout isn't the same as when the server had "
                + "started: " + (AtStart.layout == null ? "none then" : DrossHub.describe(AtStart.layout)) + " then, " + DrossHub.describe(layout) + " now");
        BoundingBox box = layout.footprint();
        expect(problems, box.minX() == SPIRE_MIN_X && box.maxX() == SPIRE_MAX_X && box.minZ() == SPIRE_MIN_Z && box.maxZ() == SPIRE_MAX_Z
                && box.minY() == g - SPIRE_BELOW && box.maxY() == g + SPIRE_ABOVE, "the footprint is " + describe(box) + ", not the spire's");
        PortalOpening exit = layout.exit();
        expect(problems, exit.axis() == Direction.Axis.X && exit.width() == 3 && exit.height() == 5
                && exit.minCorner().equals(new BlockPos(DrossHub.CENTER.getX() - 1, g + SPIRE_OPENING_ABOVE_GROUND, DrossHub.CENTER.getZ())),
                "the exit is " + describe(exit) + ", not the spire's frame on the centre");
        Vec3 step = Vec3.atLowerCornerOf(exit.minCorner()).add(SPIRE_ARRIVAL_OFFSET);
        expect(problems, layout.arrival().distanceTo(step) < 1.0E-6D && Math.abs(Mth.wrapDegrees(layout.arrivalYaw())) < 1.0E-3F,
                "the arrival is " + layout.arrival() + " facing " + layout.arrivalYaw() + ", not " + step + " facing south");
        HubCamp.Spots camp = layout.camp();
        expect(problems, camp != null && layout.restSpots().equals(List.of(camp.mentor(), camp.student())),
                "the rest spots " + layout.restSpots() + " aren't the camp's");
        Structure hub = run.dross.registryAccess().registryOrThrow(Registries.STRUCTURE)
                .get(new ResourceLocation(Dross.MODID, "dross_hub"));
        ChunkPos middle = new ChunkPos(DrossHub.spireCenter(g));
        expect(problems, hub != null && run.dross.getChunk(middle.x, middle.z).getStartForStructure(hub) != null,
                "there's no dross:dross_hub structure start in the spire's middle chunk " + middle);
        String standing = notStandable(run.dross, BlockPos.containing(layout.arrival()));
        expect(problems, standing == null, "the arrival spot " + layout.arrival() + " isn't free to stand on: " + standing);
        fail(problems);
        LOGGER.info("[hub test] {}", DrossHub.describe(layout));
    }

    private static boolean sameLayout(DrossHub.Layout a, DrossHub.Layout b)
    {
        return a.groundY() == b.groundY() && a.footprint().equals(b.footprint()) && a.exit().equals(b.exit()) && a.arrival().equals(b.arrival())
                && a.arrivalYaw() == b.arrivalYaw() && a.restSpots().equals(b.restSpots());
    }

    /** The spire's and the camp's template blocks in place, the exit lit, and no tree in the footprint. */
    private static void checkBlocks(Run run)
    {
        List<String> problems = new ArrayList<>();
        ServerLevel dross = run.dross;
        DrossHub.Layout layout = run.layout;
        BlockPos center = DrossHub.spireCenter(layout.groundY());
        var spire = DrossHub.spire();
        Map<BlockPos, BlockState> spireBlocks = templateBlocks(dross, spire.template(),
                center.offset(-spire.width() / 2, -spire.groundLayer(), -spire.depth() / 2), new BlockPos(spire.width() / 2, 0, spire.depth() / 2));
        Map<BlockPos, BlockState> campBlocks = templateBlocks(dross, new ResourceLocation(Dross.MODID, HubCamp.hubCamp().template()),
                HubCamp.origin(center), HubCamp.pivot());
        List<BlockPos> opening = openingCells(layout.exit());
        spireBlocks.keySet().removeAll(campBlocks.keySet()); // the camp goes on over the spire
        opening.forEach(spireBlocks::remove);
        opening.forEach(campBlocks::remove);
        String spireWrong = notInPlace(dross, spireBlocks, "the spire's");
        String campWrong = notInPlace(dross, campBlocks, "the camp's");
        expect(problems, spireWrong == null, spireWrong);
        expect(problems, campWrong == null, campWrong);

        // The exit, lit: every cell of its opening a portal block of its axis, in a whole frame.
        PortalOpening exit = layout.exit();
        Optional<DrossPortalShape> lit = DrossPortalShape.findLitPortalShape(dross, exit.minCorner(), exit.axis());
        expect(problems, lit.isPresent() && PortalOpening.of(lit.get()).equals(exit), "the exit " + describe(exit) + " isn't lit ("
                + lit.map(shape -> "a lit portal " + describe(PortalOpening.of(shape))).orElse("no lit portal there") + ")");
        for (BlockPos cell : opening)
        {
            BlockState state = dross.getBlockState(cell);
            expect(problems, state.is(ModBlocks.DROSS_PORTAL.get()) && state.getValue(DrossPortalBlock.AXIS) == exit.axis(),
                    "the exit's opening at " + cell.toShortString() + " is " + name(state));
        }

        // No log or leaves of a tree in the footprint (only the templates' own).
        BoundingBox box = layout.footprint();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int trees = 0;
        String firstTree = null;
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                for (int y = box.minY(); y <= box.maxY(); y++)
                {
                    BlockState state = dross.getBlockState(pos.set(x, y, z));
                    if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES))
                    {
                        continue;
                    }
                    BlockState own = campBlocks.containsKey(pos) ? campBlocks.get(pos) : spireBlocks.get(pos);
                    if (own == null || !own.is(state.getBlock()))
                    {
                        trees++;
                        firstTree = firstTree != null ? firstTree : name(state) + " at " + pos.toShortString();
                    }
                }
            }
        }
        expect(problems, trees == 0, trees + " tree blocks in the footprint (the first: " + firstTree + ")");
        fail(problems);
        LOGGER.info("[hub test] The spire's {} and the camp's {} template blocks are in place, the exit is lit, no tree in the footprint",
                spireBlocks.size(), campBlocks.size());
    }

    /** Null if at least {@value #TEMPLATE_MIN_MATCH} of the blocks are there; otherwise what's wrong. */
    @Nullable
    private static String notInPlace(ServerLevel dross, Map<BlockPos, BlockState> blocks, String whose)
    {
        int same = 0;
        String firstWrong = null;
        for (Map.Entry<BlockPos, BlockState> e : blocks.entrySet())
        {
            BlockState there = dross.getBlockState(e.getKey());
            if (there.is(e.getValue().getBlock()))
            {
                same++;
            }
            else if (firstWrong == null)
            {
                firstWrong = e.getKey().toShortString() + " is " + name(there) + ", not " + name(e.getValue());
            }
        }
        if (blocks.isEmpty())
        {
            return whose + " template is empty or missing";
        }
        double share = same / (double) blocks.size();
        return share >= TEMPLATE_MIN_MATCH ? null : String.format(Locale.ROOT, "only %.2f%% of %s %d template blocks are in place (%s)",
                100.0D * share, whose, blocks.size(), firstWrong);
    }

    /** Every block of a template (air too), where it goes when placed at {@code origin} turned as the hub's are. */
    private static Map<BlockPos, BlockState> templateBlocks(ServerLevel dross, ResourceLocation id, BlockPos origin, BlockPos pivot)
    {
        Map<BlockPos, BlockState> out = new HashMap<>();
        Optional<StructureTemplate> found = dross.getStructureManager().get(id);
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
        var turn = HubCamp.rotation(DrossHub.SPIRE_FACING);
        ListTag blocks = tag.getList("blocks", Tag.TAG_COMPOUND);
        for (int i = 0; i < blocks.size(); i++)
        {
            CompoundTag block = blocks.getCompound(i);
            ListTag p = block.getList("pos", Tag.TAG_INT);
            BlockPos local = new BlockPos(p.getInt(0), p.getInt(1), p.getInt(2));
            out.put(StructureTemplate.transform(local, Mirror.NONE, turn, pivot).offset(origin), states.get(block.getInt("state")).rotate(turn));
        }
        return out;
    }

    /** The cells of an opening. */
    private static List<BlockPos> openingCells(PortalOpening exit)
    {
        List<BlockPos> cells = new ArrayList<>();
        boolean alongX = exit.axis() == Direction.Axis.X;
        for (int i = 0; i < exit.width(); i++)
        {
            for (int j = 0; j < exit.height(); j++)
            {
                cells.add(exit.minCorner().offset(alongX ? i : 0, j, alongX ? 0 : i));
            }
        }
        return cells;
    }

    /**
     * The ground round the footprint, out to {@value #RING_CHECKED} blocks: every column where {@link DrossHubGround#target}
     * puts it (from the generator's own height there), within d blocks of the ground level d blocks out, and no step
     * between two neighbours steeper than a block or the land's own step there.
     */
    private static void checkRing(Run run)
    {
        List<String> problems = new ArrayList<>();
        ServerLevel dross = run.dross;
        int g = run.layout.groundY();
        BoundingBox columns = DrossHub.columns();
        var generator = dross.getChunkSource().getGenerator();
        var random = dross.getChunkSource().randomState();
        int x0 = columns.minX() - RING_CHECKED;
        int z0 = columns.minZ() - RING_CHECKED;
        int w = columns.getXSpan() + 2 * RING_CHECKED;
        int h = columns.getZSpan() + 2 * RING_CHECKED;
        int[] natural = new int[w * h];
        int[] ground = new int[w * h];
        boolean[] ring = new boolean[w * h];
        int checked = 0;
        int wrong = 0;
        int furthest = 0;
        String firstWrong = null;
        long start = System.nanoTime();
        for (int i = 0; i < w; i++)
        {
            for (int k = 0; k < h; k++)
            {
                int x = x0 + i;
                int z = z0 + k;
                int d = GroundFit.distanceOutside(columns, x, z);
                if (d == 0 || d > RING_CHECKED)
                {
                    continue;
                }
                int index = i * h + k;
                ring[index] = true;
                natural[index] = generator.getBaseHeight(x, z, Heightmap.Types.WORLD_SURFACE_WG, dross, random) - 1;
                ground[index] = groundTop(dross, x, z);
                checked++;
                int target = DrossHubGround.target(x, z, natural[index], g);
                if (ground[index] != target)
                {
                    wrong++;
                    firstWrong = firstWrong != null ? firstWrong : x + ", " + z + " (" + d + " out): ground at y " + ground[index]
                            + ", not " + target + " (the land's own " + natural[index] + ")";
                }
                if (d < DrossHub.HUB_BLEND_MARGIN)
                {
                    furthest = Math.max(furthest, Math.abs(ground[index] - g));
                    expect(problems, Math.abs(ground[index] - g) <= d, "the column " + x + ", " + z + " (" + d + " out) is at y "
                            + ground[index] + ", more than " + d + " from the ground level " + g);
                }
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        expect(problems, wrong == 0, wrong + " of " + checked + " columns round the hub don't have their ground where the plan puts it "
                + "(the first: " + firstWrong + ")");
        int steepest = 0;
        for (int i = 0; i < w; i++)
        {
            for (int k = 0; k < h; k++)
            {
                for (int[] step : new int[][] {{1, 0}, {0, 1}})
                {
                    int a = i * h + k;
                    int ni = i + step[0];
                    int nk = k + step[1];
                    if (!ring[a] || ni >= w || nk >= h || !ring[ni * h + nk])
                    {
                        continue;
                    }
                    int b = ni * h + nk;
                    int rise = Math.abs(ground[a] - ground[b]);
                    steepest = Math.max(steepest, rise);
                    expect(problems, rise <= Math.max(1, Math.abs(natural[a] - natural[b])), "a step of " + rise + " between "
                            + (x0 + i) + ", " + (z0 + k) + " and " + (x0 + ni) + ", " + (z0 + nk) + " (the land's own: "
                            + Math.abs(natural[a] - natural[b]) + ")");
                }
            }
        }
        fail(problems);
        LOGGER.info("[hub test] The ground round the hub: {} columns out to {} blocks, each where the plan puts it; the most any within the "
                + "staircase is off the ground level {}: {}; the steepest step {} (checked in {} ms)", checked, RING_CHECKED, g, furthest,
                steepest, millis);
    }

    /** The top of the ground in a column: the highest block that stops movement, but a tree's or a plant's. */
    private static int groundTop(ServerLevel dross, int x, int z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = dross.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z) - 1; y > dross.getMinBuildHeight(); y--)
        {
            BlockState state = dross.getBlockState(pos.set(x, y, z));
            if (!state.isAir() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES) && state.getFluidState().isEmpty()
                    && !state.getCollisionShape(dross, pos).isEmpty())
            {
                return y;
            }
        }
        return dross.getMinBuildHeight();
    }

    /** Exactly one Blind Oracle and one Ragged Seeker at the hub, each other's partners, living in the camp. */
    private static void checkSurvivors(Run run, String when)
    {
        List<String> problems = new ArrayList<>();
        ServerLevel dross = run.dross;
        DrossHub.Layout layout = run.layout;
        UUID mentorId = SurvivorSpawner.mentorId(dross.getSeed());
        UUID studentId = SurvivorSpawner.studentId(dross.getSeed());
        BoundingBox area = SurvivorGrounds.areaFor(layout);
        UUID[] ids = {mentorId, studentId};
        SurvivorRole[] roles = {SurvivorRole.MENTOR, SurvivorRole.STUDENT};
        for (int i = 0; i < 2; i++)
        {
            Entity found = dross.getEntity(ids[i]);
            if (!(found instanceof Survivor survivor))
            {
                problems.add("the " + roles[i].id() + " (" + ids[i] + ") isn't at the hub (" + found + ")");
                continue;
            }
            expect(problems, survivor.getRole() == roles[i], "the " + roles[i].id() + " is a " + survivor.getRole());
            expect(problems, ids[1 - i].equals(survivor.getPartnerId()), "the " + roles[i].id() + "'s partner is " + survivor.getPartnerId());
            expect(problems, layout.restSpots().get(i).equals(survivor.getRestSpot()), "the " + roles[i].id() + "'s rest spot is "
                    + survivor.getRestSpot() + ", not the camp's " + layout.restSpots().get(i));
            expect(problems, area.equals(survivor.getArea()) && area.isInside(survivor.blockPosition()), "the " + roles[i].id() + " is at "
                    + survivor.blockPosition().toShortString() + " with the area " + survivor.getArea() + ", not in the camp's " + area);
        }
        List<Survivor> all = dross.getEntitiesOfClass(Survivor.class, AABB.of(layout.footprint().inflatedBy(DrossHub.HUB_BLEND_MARGIN)));
        expect(problems, all.size() == 2, "there are " + all.size() + " survivors at the hub, not 2: "
                + all.stream().map(s -> s.getRole().id() + " " + s.getUUID()).toList());
        fail(problems);
        LOGGER.info("[hub test] The hub's two survivors ({}): the Blind Oracle at {} and the Ragged Seeker at {}, partners, at home in the "
                + "camp", when, dross.getEntity(mentorId).blockPosition().toShortString(), dross.getEntity(studentId).blockPosition().toShortString());
    }

    // ---------------------------------------------------------------------------------------------
    // The survivors come once; the exit is relit
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = RELOAD_BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void survivorsComeOnlyOnce(GameTestHelper helper)
    {
        Run run = new Run(helper);
        run.watch();
        ChunkPos camp = new ChunkPos(run.layout.restSpots().get(0));
        helper.startSequence()
                .thenExecute(() -> run.guard(run::hold))
                .thenWaitUntil(() -> run.waitFor("the hub's chunks and their entities", run::loaded, run::loadState))
                .thenExecute(() -> run.guard(() -> {
                    if (!run.skipOldWorld())
                    {
                        checkSurvivors(run, "the first time its chunks are loaded");
                    }
                    run.release();
                }))
                .thenWaitUntil(() -> run.waitFor("the camp's chunk " + camp + " to unload", () -> run.skipped || run.unloaded(camp),
                        () -> "it's still " + (run.dross.getChunkSource().getChunkNow(camp.x, camp.z) != null ? "loaded" : "holding its entities")))
                .thenExecute(() -> run.guard(() -> {
                    if (!run.skipped)
                    {
                        run.hold();
                    }
                }))
                .thenWaitUntil(() -> run.waitFor("the hub's chunks and their entities, again", () -> run.skipped || run.loaded(), run::loadState))
                .thenExecute(() -> run.guard(() -> {
                    if (!run.skipped)
                    {
                        checkSurvivors(run, "after its chunks were unloaded and loaded again");
                    }
                }))
                .thenExecute(() -> run.guard(run::release))
                .thenSucceed();
    }

    @GameTest(template = EMPTY, batch = RELIGHT_BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void exitIsRelit(GameTestHelper helper)
    {
        Run run = new Run(helper);
        run.watch();
        helper.startSequence()
                .thenExecute(() -> run.guard(run::hold))
                .thenWaitUntil(() -> run.waitFor("the hub's chunks and their entities", run::loaded, run::loadState))
                .thenExecute(() -> run.guard(() -> {
                    if (run.skipOldWorld())
                    {
                        return;
                    }
                    PortalOpening exit = run.layout.exit();
                    for (BlockPos cell : openingCells(exit))
                    {
                        run.dross.setBlock(cell, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                    }
                    if (DrossPortalShape.findLitPortalShape(run.dross, exit.minCorner(), exit.axis()).isPresent())
                    {
                        throw new GameTestAssertException("the exit is still lit with its portal blocks gone");
                    }
                    TestPlayer player = TestPlayer.join(helper.getLevel(), "hub-relight-test");
                    try
                    {
                        if (!DrossHub.teleportToHub(player))
                        {
                            throw new GameTestAssertException("/dross goto hub's teleport failed (no Dross?)");
                        }
                        List<String> problems = new ArrayList<>();
                        for (BlockPos cell : openingCells(exit))
                        {
                            expect(problems, run.dross.getBlockState(cell).is(ModBlocks.DROSS_PORTAL.get()), "the exit's opening at "
                                    + cell.toShortString() + " is " + name(run.dross.getBlockState(cell)) + " after /dross goto hub");
                        }
                        checkArrival(problems, run.dross, run.layout, player.position(), player.getYRot(), "/dross goto hub after the relight");
                        fail(problems);
                    }
                    finally
                    {
                        player.leave();
                    }
                    LOGGER.info("[hub test] With its portal blocks gone, /dross goto hub lit the exit again");
                }))
                .thenExecute(() -> run.guard(run::release))
                .thenSucceed();
    }
    /**
     * Stepping into a Dross portal has the far side's chunks made in the background ({@link DrossHub#warmArrival} for the
     * hub's landing, {@code PortalSite.warmArrival} for the cathedral's frame): once each landing chunk is unloaded (no
     * test holds it), the warm-up alone brings it back, here asked again every tick as a player standing in the portal
     * keeps it asked.
     */
    @GameTest(template = EMPTY, batch = WARM_BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void warmUpMakesTheLandingChunks(GameTestHelper helper)
    {
        Run run = new Run(helper);
        run.watch();
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        ChunkPos hub = new ChunkPos(BlockPos.containing(run.layout.arrival()));
        boolean site = PortalSite.isPlaced(overworld);
        ChunkPos frame = site ? new ChunkPos(PortalSite.getOpeningCenter(overworld)) : hub;
        ServerLevel frameLevel = site ? overworld : run.dross;
        helper.startSequence()
                .thenWaitUntil(() -> run.waitFor("the hub's landing chunk " + hub + " to unload", () -> run.dross.getChunkSource().getChunkNow(hub.x, hub.z) == null,
                        () -> "it's still loaded"))
                .thenWaitUntil(() -> run.waitFor("the warm-up to make the hub's landing chunk " + hub, () -> {
                    DrossHub.warmArrival(run.dross);
                    return run.dross.getChunkSource().getChunkNow(hub.x, hub.z) != null;
                }, () -> "it isn't loaded"))
                .thenWaitUntil(() -> run.waitFor("the cathedral's frame chunk " + frame + " to unload", () -> !site || frameLevel.getChunkSource().getChunkNow(frame.x, frame.z) == null,
                        () -> "it's still loaded"))
                .thenWaitUntil(() -> run.waitFor("the warm-up to make the cathedral's frame chunk " + frame, () -> {
                    if (!site)
                    {
                        return true;
                    }
                    PortalSite.warmArrival(overworld);
                    return overworld.getChunkSource().getChunkNow(frame.x, frame.z) != null;
                }, () -> "it isn't loaded"))
                .thenExecute(() -> run.guard(() -> LOGGER.info("[hub test] The portal's warm-up made the hub's landing chunk {}{}", hub,
                        site ? " and the cathedral's frame chunk " + frame : " (this world has no cathedral)")))
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Arriving
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = BATCH)
    public static void arrivalThroughPortal(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = drossOf(server);
        DrossHub.Layout layout = layoutOf(server);
        if (oldWorld(dross, layout))
        {
            helper.succeed();
            return;
        }
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, BlockPos.ZERO);
        Entity arrived;
        try
        {
            arrived = stand.changeDimension(dross, new DrossTeleporter());
        }
        finally
        {
            if (!stand.isRemoved())
            {
                stand.discard();
            }
        }
        if (arrived == null)
        {
            throw new GameTestAssertException("the armor stand didn't go through to the Dross");
        }
        try
        {
            List<String> problems = new ArrayList<>();
            expect(problems, arrived.level() == dross, "the armor stand arrived in " + arrived.level().dimension().location() + ", not the Dross");
            checkArrival(problems, dross, layout, arrived.position(), arrived.getYRot(), "the armor stand");
            fail(problems);
            LOGGER.info("[hub test] An armor stand sent through a Dross portal landed at {} facing {}, on the layout's arrival spot",
                    arrived.position(), Direction.fromYRot(arrived.getYRot()).getName());
        }
        finally
        {
            arrived.discard();
        }
        helper.succeed();
    }

    @GameTest(template = EMPTY, batch = BATCH)
    public static void gotoHubLandsOnTheStep(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = drossOf(server);
        DrossHub.Layout layout = layoutOf(server);
        if (oldWorld(dross, layout))
        {
            helper.succeed();
            return;
        }
        TestPlayer player = TestPlayer.join(helper.getLevel(), "hub-test-player");
        try
        {
            if (!DrossHub.teleportToHub(player))
            {
                throw new GameTestAssertException("/dross goto hub's teleport failed (no Dross?)");
            }
            List<String> problems = new ArrayList<>();
            expect(problems, player.serverLevel() == dross, "the player went to " + player.serverLevel().dimension().location() + ", not the Dross");
            checkArrival(problems, dross, layout, player.position(), player.getYRot(), "/dross goto hub");
            fail(problems);
            LOGGER.info("[hub test] /dross goto hub put a player at {} facing {}, on the layout's arrival spot", player.position(),
                    Direction.fromYRot(player.getYRot()).getName());
        }
        finally
        {
            player.leave();
        }
        helper.succeed();
    }

    /** The spot is the layout's arrival spot, facing its way, standing on the block under it (the spire's: the step, facing south). */
    private static void checkArrival(List<String> problems, ServerLevel dross, DrossHub.Layout layout, Vec3 at, float yaw, String who)
    {
        expect(problems, at.distanceTo(layout.arrival()) < 1.0E-6D, who + " landed at " + at + ", not on the arrival spot " + layout.arrival());
        expect(problems, Math.abs(Mth.wrapDegrees(yaw - layout.arrivalYaw())) < 1.0E-3F, who + " faces " + yaw + ", not " + layout.arrivalYaw());
        Vec3 step = Vec3.atLowerCornerOf(layout.exit().minCorner()).add(SPIRE_ARRIVAL_OFFSET);
        expect(problems, at.distanceTo(step) < 1.0E-6D, who + " landed at " + at + ", not on the step in front of the frame " + step);
        expect(problems, Math.abs(Mth.wrapDegrees(yaw)) < 1.0E-3F, who + " faces " + yaw + ", not south (0), toward the gate");
        BlockPos below = BlockPos.containing(at).below();
        BlockState ground = dross.getBlockState(below);
        VoxelShape shape = ground.getCollisionShape(dross, below);
        expect(problems, !shape.isEmpty() && Math.abs(below.getY() + shape.max(Direction.Axis.Y) - at.y) < 1.0E-6D,
                who + " isn't standing on " + name(ground) + " under it at " + below.toShortString());
    }


    /** True (with a log line) if this world's hub was made before it came with the land ({@link Run#skipOldWorld}). */
    private static boolean oldWorld(ServerLevel dross, DrossHub.Layout layout)
    {
        ChunkPos middle = new ChunkPos(DrossHub.spireCenter(layout.groundY()));
        if (!AtStart.hubRegionExisted || madeWithTheLand(dross, middle))
        {
            return false;
        }
        LOGGER.info("[hub test] skipped: this world's hub was made before it came with the land; run the tests in a brand-new world");
        return true;
    }
    // ---------------------------------------------------------------------------------------------
    // Safe ground on rough ground
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = EMPTY, batch = BATCH)
    public static void safeLandingOnRoughGround(GameTestHelper helper)
    {
        ServerLevel dross = drossOf(helper.getLevel().getServer());
        Platform p = findRoom(dross);
        try
        {
            p.build(dross);
            List<String> problems = new ArrayList<>();
            BlockState top = ModBlocks.SILVERMOSS.get().defaultBlockState();

            // 1. Under the floating block: on the platform in its own column (the floating block is no ground).
            Optional<Vec3> underFloat = DrossTeleporter.findSafeSpot(dross, null, p.x, p.z, 0);
            Vec3 expected = new Vec3(p.x + 0.5D, p.top + 1, p.z + 0.5D);
            expect(problems, underFloat.isPresent() && underFloat.get().distanceTo(expected) < 1.0E-6D,
                    "under the floating block, the spot is " + underFloat.map(Vec3::toString).orElse("none") + ", not " + expected + " on the platform");

            // 2. The tree: its own column has no safe spot (its trunk stands there); round it, on the platform, never on leaves or logs.
            int treeX = p.x + TREE_EAST;
            expect(problems, DrossTeleporter.findSafeSpot(dross, null, treeX, p.z, 0).isEmpty(), "found a spot in the trunk's own column");
            Optional<Vec3> byTree = DrossTeleporter.findSafeSpot(dross, null, treeX, p.z, LANDING_RADIUS);
            checkLanding(problems, dross, p, top, byTree, treeX, p.z, "by the tree");

            // 3. The lava: its own column has no safe spot; round it, on the platform, with no lava in or under the spot.
            int lavaX = p.x - LAVA_WEST;
            expect(problems, DrossTeleporter.findSafeSpot(dross, null, lavaX, p.z, 0).isEmpty(), "found a spot in the lava's own column");
            Optional<Vec3> byLava = DrossTeleporter.findSafeSpot(dross, null, lavaX, p.z, LANDING_RADIUS);
            checkLanding(problems, dross, p, top, byLava, lavaX, p.z, "by the lava");

            // 4. The giant blightcap: never on top of it; its stem's column has no safe spot; next to the stem, under the cap.
            int mushroomZ = p.z + MUSHROOM_SOUTH;
            expect(problems, DrossTeleporter.findSafeSpot(dross, null, p.x, mushroomZ, 0).isEmpty(), "found a spot in the blightcap's stem column");
            Optional<Vec3> byMushroom = DrossTeleporter.findSafeSpot(dross, null, p.x, mushroomZ, 1);
            checkLanding(problems, dross, p, top, byMushroom, p.x, mushroomZ, "under the blightcap");

            fail(problems);
            LOGGER.info("[hub test] Safe ground at {}, {} (platform top y {}): under the floating block {}, by the tree {}, by the lava {}, "
                            + "under the blightcap {}",
                    p.x, p.z, p.top, underFloat.orElse(null), byTree.orElse(null), byLava.orElse(null), byMushroom.orElse(null));
        }
        finally
        {
            p.clear(dross);
        }
        helper.succeed();
    }

    /**
     * The spot exists, isn't in the column {@code avoidX}/{@code avoidZ}, stands on the platform's top, and has no
     * leaves, logs, giant mushroom or lava in or under it.
     */
    private static void checkLanding(List<String> problems, ServerLevel dross, Platform p, BlockState top, Optional<Vec3> spot, int avoidX,
                                     int avoidZ, String where)
    {
        if (spot.isEmpty())
        {
            problems.add(where + ": no safe spot found");
            return;
        }
        BlockPos feet = BlockPos.containing(spot.get());
        expect(problems, !(feet.getX() == avoidX && feet.getZ() == avoidZ), where + ": the spot " + spot.get() + " is in that very column");
        expect(problems, feet.getY() == p.top + 1 && Math.abs(spot.get().y - (p.top + 1)) < 1.0E-6D,
                where + ": the spot " + spot.get() + " isn't on the platform's top (y " + (p.top + 1) + ")");
        BlockState under = dross.getBlockState(feet.below());
        expect(problems, under == top, where + ": the spot stands on " + name(under) + ", not the platform's " + name(top));
        for (BlockPos cell : List.of(feet.below(), feet, feet.above()))
        {
            BlockState state = dross.getBlockState(cell);
            expect(problems, !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !(state.getBlock() instanceof HugeMushroomBlock)
                            && !state.getFluidState().is(FluidTags.LAVA),
                    where + ": " + name(state) + " at " + cell.toShortString() + ", in or under the spot");
        }
    }

    /** The test platform: its middle column, and the y of its top block. */
    private record Platform(int x, int z, int top)
    {
        void build(ServerLevel dross)
        {
            BlockState duskstone = ModBlocks.DUSKSTONE.get().defaultBlockState();
            BlockState silvermoss = ModBlocks.SILVERMOSS.get().defaultBlockState();
            for (int dx = -PLATFORM_HALF; dx <= PLATFORM_HALF; dx++)
            {
                for (int dz = -PLATFORM_HALF; dz <= PLATFORM_HALF; dz++)
                {
                    for (int y = top - PLATFORM_DEPTH + 1; y <= top; y++)
                    {
                        set(dross, new BlockPos(x + dx, y, z + dz), y == top ? silvermoss : duskstone);
                    }
                }
            }
            // A block floating high over the middle.
            set(dross, new BlockPos(x, top + FLOAT_HEIGHT, z), duskstone);
            // A silverwood tree: its trunk, and a 3x3x2 canopy round its top log.
            int treeX = x + TREE_EAST;
            for (int y = top + 1; y <= top + TRUNK_HEIGHT; y++)
            {
                set(dross, new BlockPos(treeX, y, z), ModBlocks.SILVERWOOD_LOG.get().defaultBlockState());
            }
            BlockState leaves = ModBlocks.SILVERWOOD_LEAVES.get().defaultBlockState();
            if (leaves.hasProperty(LeavesBlock.PERSISTENT))
            {
                leaves = leaves.setValue(LeavesBlock.PERSISTENT, true); // they mustn't decay while the test runs
            }
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    for (int dy = 0; dy <= 1; dy++)
                    {
                        if (dx != 0 || dz != 0 || dy != 0)
                        {
                            set(dross, new BlockPos(treeX + dx, top + TRUNK_HEIGHT + dy, z + dz), leaves);
                        }
                    }
                }
            }
            // A column of lava: sunk into the platform's top, and one block over it.
            BlockState lava = Blocks.LAVA.defaultBlockState();
            set(dross, new BlockPos(x - LAVA_WEST, top, z), lava);
            set(dross, new BlockPos(x - LAVA_WEST, top + 1, z), lava);
            // A giant blightcap: its stem, and a 3x3 cap on it.
            int mushroomZ = z + MUSHROOM_SOUTH;
            for (int y = top + 1; y <= top + MUSHROOM_STEM; y++)
            {
                set(dross, new BlockPos(x, y, mushroomZ), ModBlocks.BLIGHTCAP_STEM.get().defaultBlockState());
            }
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    set(dross, new BlockPos(x + dx, top + MUSHROOM_STEM + 1, mushroomZ + dz), ModBlocks.BLIGHTCAP_BLOCK.get().defaultBlockState());
                }
            }
        }

        /** Removes everything the test built, leaving air. */
        void clear(ServerLevel dross)
        {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (BlockPos pos : BlockPos.betweenClosed(x - PLATFORM_HALF - 1, top - PLATFORM_DEPTH, z - PLATFORM_HALF - 1,
                    x + PLATFORM_HALF + 1, top + FLOAT_HEIGHT + 1, z + PLATFORM_HALF + 1))
            {
                if (!dross.getBlockState(pos).isAir())
                {
                    dross.setBlock(pos, air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The first spot whose land leaves room for the platform (and the block over it) in the sky; its chunks are loaded on the way. */
    private static Platform findRoom(ServerLevel dross)
    {
        StringBuilder tried = new StringBuilder();
        int reach = PLATFORM_HALF + 1;
        for (int[] spot : LANDING_SPOTS)
        {
            int land = dross.getMinBuildHeight();
            for (int x = spot[0] - reach; x <= spot[0] + reach; x++)
            {
                for (int z = spot[1] - reach; z <= spot[1] + reach; z++)
                {
                    dross.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)); // loaded first
                    land = Math.max(land, dross.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                }
            }
            int top = Math.max(PREFERRED_TOP, land + AIR_GAP + PLATFORM_DEPTH);
            if (top + FLOAT_HEIGHT + 2 < dross.getMaxBuildHeight())
            {
                return new Platform(spot[0], spot[1], top);
            }
            tried.append(" ").append(spot[0]).append(", ").append(spot[1]).append(" (land up to y ").append(land).append(")");
        }
        throw new GameTestAssertException("no room in the Dross's sky for the test platform at" + tried);
    }


    // ---------------------------------------------------------------------------------------------
    // One test's run: holding the hub's chunks, waiting by the clock, failing at once
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
        final ServerLevel dross;
        final DrossHub.Layout layout;
        /** The chunk holding the spire's middle: the ticket's centre and the structure's start. */
        final ChunkPos middle;
        /** The chunks the hub's piece touches (chunk coordinates in X and Z). */
        final int chunkX0;
        final int chunkX1;
        final int chunkZ0;
        final int chunkZ1;
        @Nullable
        RuntimeException failure;
        long waitingSince = -1L;
        boolean held;
        /** The world's hub was made before it came with the land: the checks are skipped. */
        boolean skipped;

        Run(GameTestHelper helper)
        {
            this.helper = helper;
            MinecraftServer server = helper.getLevel().getServer();
            this.dross = drossOf(server);
            this.layout = layoutOf(server);
            this.middle = new ChunkPos(DrossHub.spireCenter(this.layout.groundY()));
            BoundingBox cover = DrossHub.columns().inflatedBy(DrossHub.HUB_BLEND_MARGIN);
            this.chunkX0 = SectionPos.blockToSectionCoord(cover.minX());
            this.chunkX1 = SectionPos.blockToSectionCoord(cover.maxX());
            this.chunkZ0 = SectionPos.blockToSectionCoord(cover.minZ());
            this.chunkZ1 = SectionPos.blockToSectionCoord(cover.maxZ());
        }

        /**
         * Fails the test as soon as a step has failed: a wait takes any failed check for "not yet" and tries it again every
         * tick until the test's tick limit, so a failure found in a wait is kept and this second sequence (started before
         * the test's own) fails the test with it at once.
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
                LOGGER.error("[hub test] failed: {}", e.getMessage());
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
            this.dross.getChunkSource().addRegionTicket(TICKET, this.middle, HOLD_RADIUS, this.middle);
            this.held = true;
        }

        /** Safe to call twice. */
        void release()
        {
            if (this.held)
            {
                this.dross.getChunkSource().removeRegionTicket(TICKET, this.middle, HOLD_RADIUS, this.middle);
                this.held = false;
            }
        }

        /** True once every chunk the hub's piece touches is fully loaded with its entities. */
        boolean loaded()
        {
            return this.loadState() == null;
        }

        /** Null once loaded; otherwise how far it has got. */
        @Nullable
        String loadState()
        {
            int total = 0;
            int full = 0;
            int entities = 0;
            for (int cx = this.chunkX0; cx <= this.chunkX1; cx++)
            {
                for (int cz = this.chunkZ0; cz <= this.chunkZ1; cz++)
                {
                    total++;
                    full += this.dross.getChunkSource().getChunkNow(cx, cz) != null ? 1 : 0;
                    entities += this.dross.areEntitiesLoaded(ChunkPos.asLong(cx, cz)) ? 1 : 0;
                }
            }
            return full == total && entities == total ? null : full + " of " + total + " chunks loaded, " + entities + " with their entities";
        }

        boolean unloaded(ChunkPos chunk)
        {
            return this.dross.getChunkSource().getChunkNow(chunk.x, chunk.z) == null && !this.dross.areEntitiesLoaded(chunk.toLong());
        }

        /**
         * True (with a log line) if this world's hub was made before the hub came with the land: no structure start in its
         * middle chunk, and its region file was already there when the server started. (Without the region file, a missing
         * start is a failure: {@link #checkLayout}.)
         */
        boolean skipOldWorld()
        {
            if (!AtStart.hubRegionExisted || madeWithTheLand(this.dross, this.middle))
            {
                return false;
            }
            LOGGER.info("[hub test] skipped: this world's hub was made before it came with the land (its region file was there when the "
                    + "server started, and its middle chunk has no dross:dross_hub start); run the tests in a brand-new world");
            this.skipped = true;
            return true;
        }
    }

    /** True if the chunk holding the spire's middle has the hub's structure start. */
    private static boolean madeWithTheLand(ServerLevel dross, ChunkPos middle)
    {
        Structure hub = dross.registryAccess().registryOrThrow(Registries.STRUCTURE).get(new ResourceLocation(Dross.MODID, "dross_hub"));
        return hub != null && dross.getChunk(middle.x, middle.z).getStartForStructure(hub) != null;
    }
    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static ServerLevel drossOf(MinecraftServer server)
    {
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("the Dross dimension isn't loaded");
        }
        return dross;
    }

    private static DrossHub.Layout layoutOf(MinecraftServer server)
    {
        return DrossHub.layout(server).orElseThrow(() -> new GameTestAssertException("the hub has no layout (no Dross?)"));
    }

    /** Why a villager-sized body can't stand in this cell, or null if it can. */
    @Nullable
    private static String notStandable(ServerLevel dross, BlockPos feet)
    {
        for (BlockPos cell : List.of(feet, feet.above()))
        {
            BlockState state = dross.getBlockState(cell);
            VoxelShape inCell = state.getCollisionShape(dross, cell);
            // (a thin floor in the feet's own cell, a carpet, is stood on: the camp's Oracle rests on his runner)
            boolean thinFloor = cell.equals(feet) && !inCell.isEmpty() && inCell.max(Direction.Axis.Y) <= 0.2D;
            if (!inCell.isEmpty() && !thinFloor)
            {
                return name(state) + " at " + cell.toShortString();
            }
            if (!state.getFluidState().isEmpty())
            {
                return "liquid at " + cell.toShortString();
            }
            if (state.is(ModBlocks.DROSS_PORTAL.get()) || state.is(BlockTags.PORTALS))
            {
                return "a portal at " + cell.toShortString();
            }
        }
        BlockPos belowPos = feet.below();
        BlockState below = dross.getBlockState(belowPos);
        VoxelShape shape = below.getCollisionShape(dross, belowPos);
        if (shape.isEmpty() || shape.max(Direction.Axis.Y) < 1.0D)
        {
            return "nothing full to stand on (" + name(below) + ")";
        }
        if (below.is(BlockTags.LEAVES))
        {
            return "it stands on leaves";
        }
        return null;
    }

    private static void set(ServerLevel dross, BlockPos pos, BlockState state)
    {
        dross.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
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

    private static String describe(PortalOpening exit)
    {
        return exit.minCorner().toShortString() + " along " + exit.axis() + ", " + exit.width() + "x" + exit.height();
    }

    private static String describe(BoundingBox box)
    {
        return "X " + box.minX() + ".." + box.maxX() + ", Y " + box.minY() + ".." + box.maxY() + ", Z " + box.minZ() + ".." + box.maxZ();
    }

    private static String name(BlockState state)
    {
        return String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }

    // ---------------------------------------------------------------------------------------------
    // The stand-in player (like world-builder's site test: an in-memory network channel, because vanilla's
    // makeMockServerPlayerInLevel crashes under Forge)
    // ---------------------------------------------------------------------------------------------

    private static final class TestPlayer extends ServerPlayer
    {
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
            return player;
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

    /** The tests' empty template (a block of air). */
    private static void registerEmpty(MinecraftServer server)
    {
        StructureTemplate template = server.getStructureManager().getOrCreate(EMPTY_ID);
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
    }

    private static ListTag ints(int... values)
    {
        ListTag list = new ListTag();
        for (int v : values)
        {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }
}
