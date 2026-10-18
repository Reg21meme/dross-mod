package com.reg21meme.dross.dimension;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.dimension.block.TerrainBlocks;
import com.reg21meme.dross.dimension.sewer.RatCaves;
import com.reg21meme.dross.dimension.sewer.SewerEntrance;
import com.reg21meme.dross.dimension.sewer.SewerGrandPipePiece;
import com.reg21meme.dross.dimension.sewer.SewerNodePiece;
import com.reg21meme.dross.dimension.sewer.SewerPipePiece;
import com.reg21meme.dross.dimension.sewer.SewerSites;
import com.reg21meme.dross.dimension.sewer.SewerStructure;
import com.reg21meme.dross.dimension.sewer.SewerTuning;
import com.reg21meme.dross.dimension.sewer.SewerTunnelPiece;
import com.reg21meme.dross.dimension.sewer.Sewers;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Group;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.dimension.worldgen.TidyUpFeature;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CandleBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.FenceBlock;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.WallBannerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Headless checks (GameTests) of the sewers under Blightwood (the user's requests, October 2026; the structures
 * {@code dross:sewers} and {@code dross:rat_cave}, {@code dimension/sewer/}, placed by the boss-site planner
 * {@code dimension/sites/BossSites}). Run them with {@code .\gradlew.bat runGameTestServer -PgameTestWorld=<a new folder>
 * --console=plain}.
 * <ul>
 *   <li>{@link #sewerNetworkInBlightwood}: the regular sewer nearest the hub ({@link Sewers#findNearest}, through the
 *       planner), generated for real at its planned chunk: under Blightwood (the surface biome map unchanged above it),
 *       exactly {@value SewerTuning#PIPES_MIN} pipes, each standing out of the ground with its opening clear to the sky, a
 *       ladder all the way up one inside wall and a pool at least 3 deep under the whole opening; the pool in the sewers
 *       biome with murky green water ({@code DrossColors.SEWERS_WATER}, never vanilla blue); a walk along the tunnels
 *       from one pipe reaches every other; a maze (dead ends, loops, main and side tunnels); Sewer Sludge on its floors;
 *       glow lichen on its walls.</li>
 *   <li>{@link #ratCaveHasNestHoardAndDrains}: the rat cave nearest the hub ({@link RatCaves#findNearest}): its size, the
 *       nest of carpets (where {@link RatCaves.RatCave#nest()} says), the gold about 70% ore / 30% gold blocks, sludge
 *       patches on its floor, the arrival spot (16+ from the nest, safe), its pipes pouring into drains that never flood
 *       the floor (its water is ticked as Minecraft would), lit green candles round the nest and on the gold, glow lichen
 *       on its walls, no red carpet leading in to the nest, the entrance leading to it, {@code isInRatCave}, and its own spawn
 *       list (rats only, 3 in 5 enraged).</li>
 *   <li>{@link #ratKingSewerHasItsGrandEntrance}: the same Rat King's sewer has exactly one entrance, the grand one: its
 *       crowned rim (a bright copper band, copper points tipped with glowing Blight Vents, no gold), tattered red banners, the 5 x 5 drop into a deep pool, the
 *       ladder, the green glow (Blight Vents in the shaft, lit), the poisoned ground round it (coarse dirt, sludge
 *       puddles, dead bushes, rat nests of bone and carpet, cobwebs, gnawed fences; no trees or giant mushrooms), and the
 *       royal road to the cave (banners, lit green candles, bones, gold; no carpet showing the way).</li>
 *   <li>{@link #everySewerEntranceIsWhole}: every entrance of the sewers in the first {@value #ENTRANCE_GROUPS} groups of
 *       Blightwood patches (and the nearest regular sewer): its whole ladder (each rung held by the wall behind it), its
 *       open shaft and its pool.</li>
 *   <li>{@link #tidyUpLeavesAHalfBuiltPipeAlone}: the tidy-up at the end of each chunk's features leaves a ladder or a
 *       banner alone when the wall behind it (in the next chunk, not built yet) isn't there: the cause of a gap in one
 *       world's ladder (October 16).</li>
 *   <li>{@link #sewerSludgeSlowsWalkers}: things moving over Sewer Sludge slow down, less than on soul sand.</li>
 * </ul>
 * With {@code DROSS_SEWER_PREVIEWS=1} they also write the blocks {@code tools/terrain/preview_sewers.py} draws
 * ({@code build/tv1/sewers/}: maps of both kinds of network, cutaways of a pipe, the grand entrance, the royal road and
 * the rat cave).
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class SewersGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** At least this much water under every block of a pipe's opening (the user's ask: the fall must be broken). */
    private static final int MIN_POOL_DEPTH = 3;
    /** Other code reads "the biome of a column" at fixed heights (y 72, 80): there it must always be the surface's biome. */
    private static final int FIXED_HEIGHT_READ = 72;
    /** Vanilla's ordinary water colour (the sewers' water must never be it). */
    private static final int VANILLA_WATER = 0x3F76E4;
    /** The rat cave: at least this wide (its floor, blocks) and tall (its highest air column), the user's 35-40 x 12-16. */
    private static final int CAVE_MIN_WIDTH = 34;
    private static final int CAVE_MIN_HEIGHT = 12;
    /** The hoard's mix (the user's request: about 70% Dross gold ore, 30% gold blocks), give or take this much. */
    private static final double GOLD_ORE_SHARE = 0.70D;
    private static final double GOLD_TOLERANCE = 0.12D;
    private static final int GOLD_MIN = 40;
    /**
     * The hoard lies within this many blocks of the nest (its heaps reach SewerTuning.GOLD_HEAP_DISTANCE_MAX +
     * GOLD_HEAP_RADIUS_MAX, the scattered blocks 2 further); gold further out is natural ore in the rock.
     */
    private static final double HOARD_REACH = 13.0D;
    /** The cave floor's sludge share must be between these (SewerTuning.SLUDGE_RAT_CAVE, 0.26, in patches; the nest, hoard, drains and paths are never sludge). */
    private static final double CAVE_SLUDGE_MIN = 0.08D;
    private static final double CAVE_SLUDGE_MAX = 0.50D;
    /** Rounds of fluid ticks run over the rat cave's water before checking it stays in its drains. */
    private static final int FLUID_ROUNDS = 12;
    /** The rat cave's spawn list: 3 in 5 of its rats enraged (counting group sizes), give or take. */
    private static final double CAVE_ENRAGED_SHARE = 3.0D / 5.0D;
    private static final double SHARE_TOLERANCE = 0.03D;
    /** Sewer Sludge must slow a sliding item to between these shares of its distance on stone (soul sand: about 0.54). */
    private static final double SLUDGE_SLOW_MIN = 0.55D;
    private static final double SLUDGE_SLOW_MAX = 0.85D;
    private static final int SLIDE_TICKS = 30;
    private static final double SLIDE_SPEED = 0.35D;
    /** A spot this many blocks east of a rat cave's middle is outside it (it's about 40 across). */
    private static final int NOT_IN_CAVE = 40;
    /** The fewest junctions a network may have (its smallest form). */
    private static final int MIN_JUNCTIONS = Math.min(SewerTuning.NODES_SMALLEST, SewerTuning.RELAXED_NODES_SMALLEST);
    /** At least this much glow lichen in a whole network (SewerTuning.LICHEN_TUNNELS of its wall spots), and in the rat cave. */
    private static final int MIN_NETWORK_LICHEN = 8;
    private static final int MIN_CAVE_LICHEN = 5;
    /** At least this many spots of lit green candles round the nest and on the gold (SewerTuning.NEST_CANDLE_SPOTS + heaps). */
    private static final int MIN_CAVE_CANDLES = 6;
    /** The poisoned ground round the grand entrance: at least this many of each. */
    private static final int MIN_COARSE_DIRT = 25;
    /** (its layout plans at least SewerTuning.POISON_SLUDGE_MIN; a bump, a plant or a pond can take a few of them) */
    private static final int MIN_POISON_SLUDGE = 4;
    private static final int MIN_DEAD_BUSHES = 3;
    private static final int MIN_BONES = 4;
    private static final int MIN_NEST_CARPETS = 4;
    private static final int MIN_COBWEBS = 2;
    private static final int MIN_FENCES = 3;
    private static final int MIN_CROWN_TIPS = 4;
    /** The royal road: at least this many banners and candles (and no carpet on it: it mustn't show the way to his nest). */
    private static final int MIN_ROYAL_BANNERS = 1;
    private static final int MIN_ROYAL_CANDLES = 2;
    /** The green glow: block light at least this in the shaft just under the rim. */
    private static final int MIN_SHAFT_LIGHT = 10;
    /** Set this environment variable to 1 to write the sewer previews' blocks (tools/terrain/preview_sewers.py draws them). */
    static final String PREVIEW_ENV = "DROSS_SEWER_PREVIEWS";
    /** The royal road's view reaches at most this far from its middle. */
    private static final int ROYAL_VIEW_MAX_HALF = 32;
    /** The pipe cutaway reaches this far under its pool. */
    private static final int VIEW_BELOW_POOL = 6;
    /** Every entrance of the sewers in this many groups of Blightwood patches (nearest the hub first) is checked. */
    private static final int ENTRANCE_GROUPS = 3;

    private SewersGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // A regular sewer
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_sewers", timeoutTicks = TerrainSamplingGameTest.BACKSTOP_TICKS)
    public static void sewerNetworkInBlightwood(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        // the planner's work (noise only) off the server thread first; then the network's chunks on it
        CompletableFuture<Optional<Site>> planned = CompletableFuture.supplyAsync(
                () -> BossSites.forLevel(dross).nearest(BossSites.REGULAR_SEWER, DrossHub.CENTER, Sewers.SEARCH_RADIUS));
        long deadline = Util.getMillis() + TerrainSamplingGameTest.TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(planned, deadline, "the planner to find the regular sewer nearest the hub"))
                .thenExecute(() -> {
                    Site site = planned.join().orElseThrow(() -> new GameTestAssertException("the planner put no regular sewer within "
                            + Sewers.SEARCH_RADIUS + " blocks of the hub"));
                    long started = Util.getMillis();
                    Sewers.Network network = Sewers.findNearest(dross, DrossHub.CENTER)
                            .orElseThrow(() -> new GameTestAssertException("Sewers.findNearest found no regular sewer (planned at " + site.chunk() + ")"));
                    List<String> problems = new ArrayList<>();
                    if (!network.startChunk().equals(site.chunk()) || network.ratKing())
                    {
                        problems.add("Sewers.findNearest gave the network at " + network.startChunk() + " (Rat King's: " + network.ratKing()
                                + "), but the planner put the nearest regular one at " + site.chunk());
                    }
                    StructureStart start = startAt(dross, Sewers.SEWERS, network.startChunk());
                    loadAll(dross, start.getBoundingBox());
                    String stats = checkNetwork(dross, start, problems);
                    LOGGER.info("[sewers] {} ({} ms to find and generate; planned in patch {} of Blightwood)", stats, Util.getMillis() - started,
                            site.patch().number());
                    if (previews())
                    {
                        dumpNetwork(dross, start, "network", "A regular sewer under Blightwood");
                    }
                    fail(problems, "the regular sewer isn't right");
                })
                .thenSucceed();
    }

    /** Checks one generated network (either kind); returns a summary line. */
    private static String checkNetwork(ServerLevel dross, StructureStart start, List<String> problems)
    {
        boolean ratCave = start.getStructure() instanceof SewerStructure s && s.hasRatCave();
        List<SewerEntrance> pipes = new ArrayList<>();
        int junctions = 0;
        int rooms = 0;
        int caves = 0;
        int deadEnds = 0;
        int main = 0;
        int side = 0;
        for (StructurePiece piece : start.getPieces())
        {
            if (piece instanceof SewerEntrance pipe)
            {
                pipes.add(pipe);
            }
            else if (piece instanceof SewerTunnelPiece tunnel)
            {
                if (tunnel.isMain())
                {
                    main++;
                }
                else
                {
                    side++;
                }
            }
            else if (piece instanceof SewerNodePiece node)
            {
                junctions++;
                rooms += node.kind() == SewerNodePiece.Kind.ROOM || node.kind() == SewerNodePiece.Kind.HALL ? 1 : 0;
                caves += node.kind() == SewerNodePiece.Kind.CAVE ? 1 : 0;
                deadEnds += node.linkCount() == 1 && node.kind() != SewerNodePiece.Kind.POOL && node.kind() != SewerNodePiece.Kind.GRAND_POOL ? 1 : 0;
                // under Blightwood (by the noise: the surface biome map), and the air over the junction in the sewers biome
                BlockPos c = node.centre();
                if (!DrossBiomes.isBiome(dross, c.atY(SewerStructure.SURFACE_BIOME_Y), DrossBiomes.BLIGHTWOOD))
                {
                    problems.add("a junction at " + c.toShortString() + " isn't under Blightwood");
                }
                // code that reads a column's biome at a fixed height (y 72 or 80) still gets Blightwood: the sewers biome stays below y 64
                if (!DrossBiomes.isBiome(dross, c.atY(FIXED_HEIGHT_READ), DrossBiomes.BLIGHTWOOD))
                {
                    problems.add("over the junction at " + c.toShortString() + " the biome read at y " + FIXED_HEIGHT_READ + " isn't Blightwood");
                }
                if (!dross.getBiome(c.above()).is(DrossBiomes.SEWERS))
                {
                    problems.add("the air of the junction at " + c.above().toShortString() + " isn't in the sewers biome ("
                            + dross.getBiome(c.above()).unwrapKey().map(k -> k.location().toString()).orElse("?") + ")");
                }
            }
        }
        int tunnels = main + side;
        int loops = tunnels - (junctions - 1);
        long grand = pipes.stream().filter(SewerEntrance::grand).count();
        if (ratCave ? pipes.size() != 1 || grand != 1 : pipes.size() < SewerTuning.PIPES_MIN || pipes.size() > SewerTuning.PIPES_MAX || grand != 0)
        {
            problems.add(pipes.size() + " entrances (" + grand + " grand; " + (ratCave ? "exactly 1 grand" : SewerTuning.PIPES_MIN + " pipes") + " expected)");
        }
        if (deadEnds < 1 || loops < 1 || main < (ratCave ? 1 : 2) || side < 2 || junctions < MIN_JUNCTIONS)
        {
            problems.add("not much of a maze: " + junctions + " junctions, " + deadEnds + " dead ends, " + loops + " loops, " + main + " main and "
                    + side + " side tunnels");
        }
        StringBuilder depths = new StringBuilder();
        for (SewerEntrance pipe : pipes)
        {
            checkPipe(dross, pipe, problems);
            depths.append(depths.length() > 0 ? ", " : "").append(pipe.rimTopY() - pipe.centre().getY());
        }
        // a walk along the tunnels from the first entrance's pool reaches every other entrance (and the rat cave)
        if (!pipes.isEmpty())
        {
            int floor = pipes.get(0).centre().getY();
            boolean[] reached = walk(dross, start.getBoundingBox(), floor, pipes.get(0).centre());
            for (SewerEntrance pipe : pipes)
            {
                if (!reachedAt(reached, start.getBoundingBox(), pipe.centre()))
                {
                    problems.add("the tunnels from the entrance at " + pipes.get(0).centre().toShortString() + " don't reach the one at "
                            + pipe.centre().toShortString());
                }
            }
            if (ratCave)
            {
                RatCaves.of(start).ifPresent(cave -> {
                    if (!reachedAt(reached, start.getBoundingBox(), cave.arrival()))
                    {
                        problems.add("the tunnels don't reach the rat cave's arrival spot " + cave.arrival().toShortString());
                    }
                });
            }
        }
        int sludge = count(dross, start.getBoundingBox(), s -> s.is(ModBlocks.SEWER_SLUDGE.get()));
        if (sludge < 20)
        {
            problems.add("only " + sludge + " Sewer Sludge blocks in the whole network");
        }
        int lichen = count(dross, start.getBoundingBox(), s -> s.is(Blocks.GLOW_LICHEN));
        if (lichen < MIN_NETWORK_LICHEN)
        {
            problems.add("only " + lichen + " glow lichen on its walls (at least " + MIN_NETWORK_LICHEN + " expected)");
        }
        return String.format(Locale.ROOT, "%s at %s: %d junctions (%d rooms, %d rough caves, %d dead ends), %d tunnels (%d main, %d side, %d loops), "
                        + "%d entrances (falls %s blocks), %d sludge blocks, %d glow lichen, box %s",
                ratCave ? "Rat King's sewer" : "regular sewer", start.getChunkPos(), junctions, rooms, caves, deadEnds, tunnels, main, side, loops,
                pipes.size(), depths, sludge, lichen, start.getBoundingBox());
    }

    /** One entrance: above the ground, clear above, a ladder all the way, a deep pool in the sewers biome with green water. */
    private static void checkPipe(ServerLevel dross, SewerEntrance pipe, List<String> problems)
    {
        BlockPos c = pipe.centre();
        int floor = c.getY();
        int rim = pipe.rimTopY();
        int open = pipe.openingHalf();
        String where = (pipe.grand() ? "the grand entrance at " : "the pipe at ") + c.getX() + ", " + c.getZ();
        // its rim stands out of the ground (copper; the grand entrance's rim top is a band of bright copper)
        BlockState rimBlock = dross.getBlockState(new BlockPos(c.getX() + open + 2, rim, c.getZ()));
        boolean rimOk = pipe.grand() ? isIn(SewerTuning.CROWN_BAND, rimBlock) : rimBlock.getBlock().getDescriptionId().contains("copper");
        if (!rimOk)
        {
            problems.add(where + ": its rim isn't " + (pipe.grand() ? "banded with bright copper" : "copper") + " (" + rimBlock + ")");
        }
        if (rim - pipe.groundY() < 3)
        {
            problems.add(where + ": it only stands " + (rim - pipe.groundY()) + " blocks out of the ground");
        }
        if (!DrossBiomes.isBiome(dross, c.atY(SewerStructure.SURFACE_BIOME_Y), DrossBiomes.BLIGHTWOOD)
                || !dross.getBiome(new BlockPos(c.getX(), rim + 1, c.getZ())).is(DrossBiomes.BLIGHTWOOD))
        {
            problems.add(where + ": its top isn't in Blightwood");
        }
        BlockPos ladder = pipe.ladderColumn();
        for (int dx = -open; dx <= open; dx++)
        {
            for (int dz = -open; dz <= open; dz++)
            {
                int x = c.getX() + dx;
                int z = c.getZ() + dz;
                boolean ladderColumn = x == ladder.getX() && z == ladder.getZ();
                // clear to the sky
                for (int y = rim + 1; y <= rim + 4; y++)
                {
                    if (!dross.getBlockState(new BlockPos(x, y, z)).isAir())
                    {
                        problems.add(where + ": its opening is blocked at " + x + ", " + y + ", " + z);
                    }
                }
                // the shaft: open all the way down, a ladder up the one column, from just over the pool's water to the rim,
                // every rung facing into the shaft and held by the wall behind it (so no block update can pop it off)
                for (int y = floor + 1; y <= rim; y++)
                {
                    BlockPos at = new BlockPos(x, y, z);
                    BlockState state = dross.getBlockState(at);
                    boolean isLadder = state.getBlock() instanceof LadderBlock;
                    boolean ok = ladderColumn
                            ? isLadder && state.getValue(LadderBlock.FACING) == pipe.stepsSide().getOpposite() && state.canSurvive(dross, at)
                            : state.isAir();
                    if (!ok)
                    {
                        problems.add(where + ": " + (!ladderColumn ? "its shaft is blocked" : isLadder ? "its ladder hangs loose" : "its ladder is missing")
                                + " at y " + y + " (" + state + "; the pipe's floor is y " + floor + ", its rim y " + rim + ")");
                        break;
                    }
                }
                // the pool under it
                int depth = 0;
                for (int y = floor; y > floor - 8 && dross.getFluidState(new BlockPos(x, y, z)).isSource(); y--)
                {
                    depth++;
                }
                if (depth < MIN_POOL_DEPTH)
                {
                    problems.add(where + ": only " + depth + " blocks of water under " + x + ", " + z);
                }
            }
        }
        BlockPos pool = new BlockPos(c.getX(), floor, c.getZ());
        Holder<Biome> biome = dross.getBiome(pool);
        int water = biome.value().getWaterColor();
        int r = (water >> 16) & 255;
        int g = (water >> 8) & 255;
        int b = water & 255;
        if (!biome.is(DrossBiomes.SEWERS) || water != DrossColors.SEWERS_WATER || water == VANILLA_WATER || g <= b || g < r)
        {
            problems.add(String.format(Locale.ROOT, "%s: its pool is in %s with water colour #%06X (want the sewers' green #%06X)", where,
                    biome.unwrapKey().map(k -> k.location().toString()).orElse("?"), water, DrossColors.SEWERS_WATER));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Every entrance near the hub, and the ladder at a chunk's edge
    // ---------------------------------------------------------------------------------------------

    /**
     * Every sewer entrance (regular pipes and grand entrances) of the networks in the first {@value #ENTRANCE_GROUPS}
     * groups of Blightwood patches (nearest the hub first), plus the nearest regular sewer, is checked as in
     * {@link #checkPipe}: the whole ladder, the open shaft, the pool. A pipe's ladder can sit on a chunk border (its wall
     * in the next chunk) anywhere, so the more pipes checked, the likelier such a one is among them.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_sewer_entrances", timeoutTicks = TerrainSamplingGameTest.BACKSTOP_TICKS)
    public static void everySewerEntranceIsWhole(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<List<Site>> planned = CompletableFuture.supplyAsync(() -> {
            BossSites sites = BossSites.forLevel(dross);
            List<Site> sewers = new ArrayList<>();
            for (Group group : sites.groups(DrossBiomes.BLIGHTWOOD, ENTRANCE_GROUPS))
            {
                sewers.addAll(group.sites());
            }
            sites.nearest(BossSites.REGULAR_SEWER, DrossHub.CENTER, Sewers.SEARCH_RADIUS).ifPresent(nearest -> {
                if (sewers.stream().noneMatch(s -> s.use() == nearest.use() && s.chunk().equals(nearest.chunk())))
                {
                    sewers.add(nearest);
                }
            });
            return sewers;
        });
        long deadline = Util.getMillis() + TerrainSamplingGameTest.TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(planned, deadline, "the planner to plan the first " + ENTRANCE_GROUPS + " groups of Blightwood patches"))
                .thenExecute(() -> {
                    long started = Util.getMillis();
                    List<String> problems = new ArrayList<>();
                    int grand = 0;
                    int pipes = 0;
                    int networks = 0;
                    for (Site site : planned.join())
                    {
                        boolean ratKing = site.use() == BossSites.RAT_KING_SEWER;
                        if (!ratKing && site.use() != BossSites.REGULAR_SEWER)
                        {
                            continue;
                        }
                        networks++;
                        StructureStart start = startAt(dross, ratKing ? Sewers.RAT_CAVE : Sewers.SEWERS, site.chunk());
                        int expected = site.detail() instanceof SewerSites.Layout layout ? layout.entrances().size() : -1;
                        int found = 0;
                        for (StructurePiece piece : start.getPieces())
                        {
                            if (piece instanceof SewerEntrance entrance)
                            {
                                found++;
                                grand += entrance.grand() ? 1 : 0;
                                pipes += entrance.grand() ? 0 : 1;
                                loadAll(dross, piece.getBoundingBox());
                                checkPipe(dross, entrance, problems);
                            }
                        }
                        if (found != expected)
                        {
                            problems.add("the " + (ratKing ? "Rat King's" : "regular") + " sewer at " + site.chunk() + " has " + found
                                    + " entrances, but the planner planned " + expected);
                        }
                    }
                    if (grand < 1 || pipes < SewerTuning.PIPES_MIN)
                    {
                        problems.add("only " + grand + " grand entrance(s) and " + pipes + " pipe(s) to check (at least 1 and " + SewerTuning.PIPES_MIN
                                + " expected)");
                    }
                    LOGGER.info("[sewer entrances] {} networks in the first {} Blightwood groups (and the nearest regular sewer): {} grand entrances "
                            + "and {} pipes checked in {} ms", networks, ENTRANCE_GROUPS, grand, pipes, Util.getMillis() - started);
                    fail(problems, "a sewer entrance isn't whole");
                })
                .thenSucceed();
    }

    /**
     * The cause of a gap found in one world's pipe (October 16): its ladder stood in the last column of one chunk and the
     * pipe's wall behind it in the next. That chunk was finished, and tidied up ({@code dross:tidy_up}), before the next
     * one's part of the pipe was built, so the tidy-up saw ladders hanging on air and pulled them down as far as the
     * hillside behind them began. This rebuilds that half-built moment in the arena (a ladder and a rim banner with only
     * air behind them) and runs the tidy-up's own column pass over it: they stay. A fern over nothing, which the tidy-up
     * is there to clear, goes (so the pass really ran).
     */
    @GameTest(template = TerrainBlocksGameTest.ARENA, batch = "dross_sewer_tidy_up")
    public static void tidyUpLeavesAHalfBuiltPipeAlone(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        int floor = 1;
        int rungs = 4;
        BlockPos ladder = new BlockPos(4, floor + 1, 4);
        BlockPos banner = new BlockPos(8, floor + 3, 4);
        BlockPos fern = new BlockPos(12, floor + 3, 4);
        // the pipe's wall would be east of them (in the next chunk, not built yet): only air there
        for (BlockPos p : List.of(ladder, banner, fern))
        {
            for (int y = floor + 1; y <= floor + rungs + 2; y++)
            {
                helper.setBlock(new BlockPos(p.getX(), y, p.getZ()), Blocks.AIR);
                helper.setBlock(new BlockPos(p.getX() + 1, y, p.getZ()), Blocks.AIR);
            }
        }
        int noUpdates = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
        BlockState rung = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, Direction.WEST);
        for (int i = 0; i < rungs; i++)
        {
            level.setBlock(helper.absolutePos(ladder.above(i)), rung, noUpdates);
        }
        level.setBlock(helper.absolutePos(banner), Blocks.RED_WALL_BANNER.defaultBlockState().setValue(WallBannerBlock.FACING, Direction.WEST), noUpdates);
        level.setBlock(helper.absolutePos(fern), Blocks.FERN.defaultBlockState(), noUpdates);
        if (rung.canSurvive(level, helper.absolutePos(ladder)))
        {
            throw new GameTestAssertException("the test ladder has a wall behind it (it should hang on air, like a half-built pipe's)");
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int absFloor = helper.absolutePos(new BlockPos(0, floor, 0)).getY();
        for (BlockPos p : List.of(ladder, banner, fern))
        {
            BlockPos a = helper.absolutePos(p);
            TidyUpFeature.tidyColumn(level, level, pos, a.getX(), a.getZ(), absFloor + rungs + 4, absFloor - 1, absFloor);
        }
        List<String> problems = new ArrayList<>();
        for (int i = 0; i < rungs; i++)
        {
            if (!helper.getBlockState(ladder.above(i)).is(Blocks.LADDER))
            {
                problems.add("the tidy-up pulled down the ladder at y +" + (i + 1) + " (" + helper.getBlockState(ladder.above(i)) + ")");
            }
        }
        if (!helper.getBlockState(banner).is(Blocks.RED_WALL_BANNER))
        {
            problems.add("the tidy-up took down the rim's banner (" + helper.getBlockState(banner) + ")");
        }
        if (!helper.getBlockState(fern).isAir())
        {
            problems.add("the tidy-up left a fern floating over nothing (so its pass didn't run)");
        }
        fail(problems, "the tidy-up broke a half-built pipe");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // The rat cave
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_rat_cave", timeoutTicks = TerrainSamplingGameTest.BACKSTOP_TICKS)
    public static void ratCaveHasNestHoardAndDrains(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<Optional<Site>> planned = CompletableFuture.supplyAsync(
                () -> BossSites.forLevel(dross).nearest(BossSites.RAT_KING_SEWER, DrossHub.CENTER, RatCaves.SEARCH_RADIUS));
        long deadline = Util.getMillis() + TerrainSamplingGameTest.TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(planned, deadline, "the planner to find the Rat King's sewer nearest the hub"))
                .thenExecute(() -> {
                    planned.join().orElseThrow(() -> new GameTestAssertException("the planner put no Rat King's sewer within " + RatCaves.SEARCH_RADIUS
                            + " blocks of the hub"));
                    long started = Util.getMillis();
                    RatCaves.RatCave cave = RatCaves.findNearest(dross, DrossHub.CENTER)
                            .orElseThrow(() -> new GameTestAssertException("RatCaves.findNearest found no rat cave from the hub"));
                    long searchMillis = Util.getMillis() - started;
                    StructureStart start = startAt(dross, Sewers.RAT_CAVE, cave.startChunk());
                    loadAll(dross, start.getBoundingBox());
                    List<String> problems = new ArrayList<>();
                    String network = checkNetwork(dross, start, problems);
                    String stats = checkCave(dross, cave, start, problems);
                    LOGGER.info("[rat cave] {}; its network: {} (found in {} ms, {} ms in all)", stats, network, searchMillis, Util.getMillis() - started);
                    if (previews())
                    {
                        dumpNetwork(dross, start, "rat_cave_network", "The Rat King's sewer, ending in his rat cave");
                        dumpCave(dross, cave);
                    }
                    fail(problems, "the rat cave isn't right");
                })
                .thenSucceed();
    }

    private static String checkCave(ServerLevel dross, RatCaves.RatCave cave, StructureStart start, List<String> problems)
    {
        BoundingBox box = cave.bounds();
        int f = cave.floorY();
        int minX = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxZ = Integer.MIN_VALUE;
        int tallest = 0;
        int floorCells = 0;
        int sludge = 0;
        int ore = 0;
        int goldBlocks = 0;
        int carpets = 0;
        int redCarpets = 0;
        int copper = 0;
        int falls = 0;
        int candles = 0;
        int lichen = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                if (!cave.contains(pos.set(x, f + 1, z)))
                {
                    continue;
                }
                int air = 0;
                for (int y = f + 1; y <= box.maxY(); y++)
                {
                    BlockState s = dross.getBlockState(pos.set(x, y, z));
                    if (s.isAir())
                    {
                        air = y - f;
                    }
                    // the hoard (heaps round the nest; natural gold ore veins can be left in the rock at the cave's edge)
                    boolean hoard = y <= f + 3 && Math.hypot(x - cave.centre().getX(), z - cave.centre().getZ()) <= HOARD_REACH;
                    if (hoard && s.is(ModBlocks.DUSKSTONE_GOLD_ORE.get()))
                    {
                        ore++;
                    }
                    if (hoard && s.is(Blocks.GOLD_BLOCK))
                    {
                        goldBlocks++;
                    }
                    if (s.getBlock().getDescriptionId().endsWith("_carpet"))
                    {
                        carpets++;
                        redCarpets += s.is(Blocks.RED_CARPET) ? 1 : 0;
                    }
                    if (s.getBlock().getDescriptionId().contains("copper"))
                    {
                        copper++;
                    }
                    if (s.is(Blocks.GREEN_CANDLE) && s.getValue(CandleBlock.LIT) && Math.hypot(x - cave.centre().getX(), z - cave.centre().getZ()) <= HOARD_REACH)
                    {
                        candles++;
                    }
                    if (s.is(Blocks.GLOW_LICHEN))
                    {
                        lichen++;
                    }
                }
                tallest = Math.max(tallest, air);
                BlockState above = dross.getBlockState(pos.set(x, f + 1, z));
                BlockState floor = dross.getBlockState(pos.set(x, f, z));
                if (above.isAir())
                {
                    floorCells++;
                    sludge += floor.is(ModBlocks.SEWER_SLUDGE.get()) ? 1 : 0;
                    minX = Math.min(minX, x);
                    maxX = Math.max(maxX, x);
                    minZ = Math.min(minZ, z);
                    maxZ = Math.max(maxZ, z);
                }
                if (dross.getFluidState(pos.set(x, f + 1, z)).is(net.minecraft.tags.FluidTags.WATER)
                        && dross.getFluidState(pos.set(x, f, z)).isSource())
                {
                    falls++;
                }
            }
        }
        int width = Math.min(maxX - minX + 1, maxZ - minZ + 1);
        if (width < CAVE_MIN_WIDTH || tallest < CAVE_MIN_HEIGHT)
        {
            problems.add("it's only " + width + " blocks across and " + tallest + " tall (at least " + CAVE_MIN_WIDTH + " x " + CAVE_MIN_HEIGHT + " expected)");
        }
        // the nest: carpets on wool, where the API says the king lies
        BlockState nest = dross.getBlockState(cave.nest());
        if (!nest.getBlock().getDescriptionId().endsWith("_carpet") || !dross.getBlockState(cave.nest().below()).getBlock().getDescriptionId().endsWith("_wool")
                || carpets < 9)
        {
            problems.add("no nest of carpets at " + cave.nest().toShortString() + " (" + nest + ", " + carpets + " carpets in the cave)");
        }
        int gold = ore + goldBlocks;
        double oreShare = gold == 0 ? 0.0D : ore / (double) gold;
        if (gold < GOLD_MIN || Math.abs(oreShare - GOLD_ORE_SHARE) > GOLD_TOLERANCE)
        {
            problems.add(String.format(Locale.ROOT, "the hoard is %d gold ore and %d gold blocks (%.0f%% ore; about 70%% expected, at least %d blocks)",
                    ore, goldBlocks, 100.0D * oreShare, GOLD_MIN));
        }
        double sludgeShare = floorCells == 0 ? 0.0D : sludge / (double) floorCells;
        if (sludgeShare < CAVE_SLUDGE_MIN || sludgeShare > CAVE_SLUDGE_MAX)
        {
            problems.add(String.format(Locale.ROOT, "%.0f%% of its floor is sludge (%.0f-%.0f%% expected)", 100.0D * sludgeShare,
                    100.0D * CAVE_SLUDGE_MIN, 100.0D * CAVE_SLUDGE_MAX));
        }
        if (copper < 30 || falls < 1)
        {
            problems.add(copper + " copper pipe blocks and " + falls + " waterfalls into drains (pipes along its walls and roof, pouring into it, expected)");
        }
        if (candles < MIN_CAVE_CANDLES)
        {
            problems.add("only " + candles + " spots of lit green candles round the nest and on the gold (at least " + MIN_CAVE_CANDLES + " expected)");
        }
        if (lichen < MIN_CAVE_LICHEN)
        {
            problems.add("only " + lichen + " glow lichen on its walls (at least " + MIN_CAVE_LICHEN + " expected)");
        }
        if (redCarpets > 0)
        {
            problems.add(redCarpets + " red carpets in the cave: no carpet should lead in to his nest");
        }
        // the arrival spot
        BlockPos arrival = cave.arrival();
        double toNest = Math.hypot(arrival.getX() - cave.nest().getX(), arrival.getZ() - cave.nest().getZ());
        BlockState under = dross.getBlockState(arrival.below());
        if (toNest < 16.0D || !dross.getBlockState(arrival).isAir() || !dross.getBlockState(arrival.above()).isAir()
                || !dross.getBlockState(arrival.above(2)).isAir() || under.is(ModBlocks.SEWER_SLUDGE.get()) || !under.getFluidState().isEmpty()
                || !under.isFaceSturdy(dross, arrival.below(), net.minecraft.core.Direction.UP) || !cave.contains(arrival))
        {
            problems.add("the arrival spot " + arrival.toShortString() + " isn't safe (" + String.format(Locale.ROOT, "%.1f", toNest) + " from the nest, standing on "
                    + under + ")");
        }
        // the API
        if (!RatCaves.isInRatCave(dross, cave.nest()) || RatCaves.caveAt(dross, cave.centre()).map(c -> c.id() != cave.id()).orElse(true)
                || RatCaves.isInRatCave(dross, cave.centre().offset(NOT_IN_CAVE, 0, 0)))
        {
            problems.add("isInRatCave / caveAt don't agree with the cave");
        }
        if (cave.entrance() == null)
        {
            problems.add("the cave knows no grand entrance");
        }
        else if (!RatCaves.entrancesNear(dross, cave.entrance(), 4).contains(cave.entrance()))
        {
            problems.add("RatCaves.entrancesNear doesn't find the grand entrance at " + cave.entrance().toShortString());
        }
        // the water stays in its drains: tick every water block of the cave as Minecraft would, a few rounds, then look
        for (int round = 0; round < FLUID_ROUNDS; round++)
        {
            for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
            {
                FluidState fluid = dross.getFluidState(p);
                if (!fluid.isEmpty())
                {
                    fluid.tick(dross, p.immutable());
                }
            }
        }
        int flooded = 0;
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                for (int y = f + 1; y <= f + 3; y++)
                {
                    if (cave.contains(pos.set(x, y, z)) && !dross.getFluidState(pos).isEmpty() && dross.getFluidState(pos.set(x, y - 1, z)).isEmpty())
                    {
                        flooded++;
                    }
                }
            }
        }
        if (flooded > 0)
        {
            problems.add(flooded + " blocks of water spread over the cave's floor (it must only pour into the drains)");
        }
        // its entrance leads here
        boolean[] reached = walk(dross, start.getBoundingBox(), f, cave.arrival().below());
        int entrances = 0;
        for (StructurePiece piece : start.getPieces())
        {
            if (piece instanceof SewerEntrance pipe)
            {
                entrances++;
                if (!reachedAt(reached, start.getBoundingBox(), pipe.centre()))
                {
                    problems.add("the entrance at " + pipe.centre().toShortString() + " doesn't lead to the rat cave");
                }
            }
        }
        // its own spawns: rats only, 3 in 5 enraged (and the sewers biome's list round it is rats only too)
        WeightedRandomList<MobSpawnSettings.SpawnerData> biomeList = dross.getBiome(cave.centre()).value().getMobSettings().getMobs(MobCategory.MONSTER);
        WeightedRandomList<MobSpawnSettings.SpawnerData> inCave = ForgeEventFactory.getPotentialSpawns(dross, MobCategory.MONSTER, cave.centre().above(), biomeList);
        String spawnProblem = ratsOnly("the rat cave", inCave, CAVE_ENRAGED_SHARE);
        if (spawnProblem != null)
        {
            problems.add(spawnProblem);
        }
        return String.format(Locale.ROOT, "at %s (floor y %d): %d x %d across, %d tall, %d floor blocks (%.0f%% sludge), nest at %s, hoard %d ore + %d gold "
                        + "blocks (%.0f%% ore), %d copper pipe blocks, %d waterfalls, %d candle spots, %d glow lichen, %d red carpets, arrival %s "
                        + "(%.1f from the nest), %d entrance(s) lead to it",
                cave.centre().toShortString(), f, maxX - minX + 1, maxZ - minZ + 1, tallest, floorCells, 100.0D * sludgeShare, cave.nest().toShortString(),
                ore, goldBlocks, 100.0D * oreShare, copper, falls, candles, lichen, redCarpets, arrival.toShortString(), toNest, entrances);
    }

    // ---------------------------------------------------------------------------------------------
    // The Rat King's grand entrance and royal road
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_rat_king_sewer", timeoutTicks = TerrainSamplingGameTest.BACKSTOP_TICKS)
    public static void ratKingSewerHasItsGrandEntrance(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<Optional<Site>> planned = CompletableFuture.supplyAsync(
                () -> BossSites.forLevel(dross).nearest(BossSites.RAT_KING_SEWER, DrossHub.CENTER, RatCaves.SEARCH_RADIUS));
        long deadline = Util.getMillis() + TerrainSamplingGameTest.TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(planned, deadline, "the planner to find the Rat King's sewer nearest the hub"))
                .thenExecute(() -> {
                    Site site = planned.join().orElseThrow(() -> new GameTestAssertException("the planner put no Rat King's sewer near the hub"));
                    StructureStart start = startAt(dross, Sewers.RAT_CAVE, site.chunk());
                    loadAll(dross, start.getBoundingBox());
                    List<String> problems = new ArrayList<>();
                    List<SewerGrandPipePiece> grand = new ArrayList<>();
                    int regularPipes = 0;
                    List<SewerTunnelPiece> royal = new ArrayList<>();
                    for (StructurePiece piece : start.getPieces())
                    {
                        if (piece instanceof SewerGrandPipePiece g)
                        {
                            grand.add(g);
                        }
                        else if (piece instanceof SewerPipePiece)
                        {
                            regularPipes++;
                        }
                        else if (piece instanceof SewerTunnelPiece t && t.isRoyal())
                        {
                            royal.add(t);
                        }
                    }
                    if (grand.size() != 1 || regularPipes != 0)
                    {
                        throw new GameTestAssertException("the Rat King's sewer has " + grand.size() + " grand entrance(s) and " + regularPipes
                                + " regular pipe(s) (exactly 1 grand entrance expected)");
                    }
                    SewerGrandPipePiece entrance = grand.get(0);
                    String crown = checkCrown(dross, entrance, problems);
                    String poison = checkPoisonedGround(dross, entrance, problems);
                    String road = checkRoyalRoad(dross, royal, problems);
                    LOGGER.info("[rat king sewer] at {} (planned in Blightwood patch {}, group {}): its grand entrance at {}: {}; poisoned ground: {}; "
                                    + "royal road: {}", start.getChunkPos(), site.patch().number(), site.patch().group() + 1, entrance.opening().toShortString(),
                            crown, poison, road);
                    if (previews())
                    {
                        dumpGrandEntrance(dross, entrance);
                        dumpRoyalRoad(dross, royal);
                    }
                    fail(problems, "the Rat King's sewer isn't right");
                })
                .thenSucceed();
    }

    /** The crowned rim, the banners and the green glow. */
    private static String checkCrown(ServerLevel dross, SewerGrandPipePiece entrance, List<String> problems)
    {
        BlockPos o = entrance.opening();
        int rim = entrance.rimTopY();
        int reach = (int) Math.ceil(SewerTuning.GRAND_RIM_RADIUS) + 1;
        int tips = 0;
        int crownCopper = 0;
        int band = 0;
        int gold = 0;
        int banners = 0;
        for (int dx = -reach; dx <= reach; dx++)
        {
            for (int dz = -reach; dz <= reach; dz++)
            {
                for (int y = rim - SewerTuning.GRAND_RIM_HEIGHT - SewerTuning.GRAND_BODY_RISE; y <= rim + SewerTuning.CROWN_TALL + 1; y++)
                {
                    BlockState s = dross.getBlockState(new BlockPos(o.getX() + dx, y, o.getZ() + dz));
                    if (y > rim && s.is(ModBlocks.BLIGHT_VENT.get()))
                    {
                        tips++;
                    }
                    if (y > rim && s.getBlock().getDescriptionId().contains("copper"))
                    {
                        crownCopper++;
                    }
                    if (y == rim && isIn(SewerTuning.CROWN_BAND, s))
                    {
                        band++;
                    }
                    // (the user's rule: no gold blocks on the entrance)
                    if (s.is(Blocks.GOLD_BLOCK) || s.is(Blocks.RAW_GOLD_BLOCK))
                    {
                        gold++;
                    }
                    if (s.is(Blocks.RED_WALL_BANNER))
                    {
                        banners++;
                    }
                }
            }
        }
        if (tips < MIN_CROWN_TIPS || crownCopper < MIN_CROWN_TIPS || band < 20)
        {
            problems.add("its rim isn't crowned: " + tips + " green tips on " + crownCopper + " copper points, " + band + " blocks of copper band on its top");
        }
        if (gold > 0)
        {
            problems.add(gold + " gold or raw gold blocks on the grand entrance (none expected)");
        }
        if (banners < 1)
        {
            problems.add("no red banner hangs off its rim");
        }
        int vents = 0;
        int light = 0;
        for (BlockPos v : entrance.shaftVents())
        {
            vents += dross.getBlockState(v).is(ModBlocks.BLIGHT_VENT.get()) ? 1 : 0;
        }
        for (int y = rim - SewerTuning.SHAFT_VENT_DEPTH - 1; y <= rim + 1; y++)
        {
            light = Math.max(light, dross.getBrightness(LightLayer.BLOCK, new BlockPos(o.getX() + 1, y, o.getZ())));
        }
        if (vents < 4 || light < MIN_SHAFT_LIGHT)
        {
            problems.add("no green glow in its shaft: " + vents + " Blight Vents in its wall, block light " + light + " (at least " + MIN_SHAFT_LIGHT + ")");
        }
        return String.format(Locale.ROOT, "%d crown tips, %d copper band blocks, no gold, %d banners, %d shaft vents, light %d in the shaft, a %d-block drop",
                tips, band, banners, vents, light, rim - entrance.centre().getY());
    }

    /** The poisoned ground round the grand entrance. */
    private static String checkPoisonedGround(ServerLevel dross, SewerGrandPipePiece entrance, List<String> problems)
    {
        BlockPos o = entrance.opening();
        int r = SewerTuning.POISON_RADIUS;
        int coarse = 0;
        int sludge = 0;
        int bushes = 0;
        int bones = 0;
        int carpets = 0;
        int webs = 0;
        int fences = 0;
        int vents = 0;
        int trees = 0;
        List<String> treeBlocks = new ArrayList<>();
        // No tree or giant mushroom block within this many blocks (a circle round the opening): world generation keeps
        // Blightwood's trees, mushrooms and logs from starting within GRAND_CLEARANCE (26), and their caps and logs reach
        // at most about 4.5 blocks from where they start, so the nearest can be about 21 out (outside the poisoned ground).
        int clear = SewerTuning.GRAND_CLEARANCE - 10;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -clear; dx <= clear; dx++)
        {
            for (int dz = -clear; dz <= clear; dz++)
            {
                double d = Math.hypot(dx, dz);
                int x = o.getX() + dx;
                int z = o.getZ() + dz;
                for (int y = entrance.groundY() - 10; y <= entrance.rimTopY() + 12; y++)
                {
                    BlockState s = dross.getBlockState(pos.set(x, y, z));
                    if (s.isAir())
                    {
                        continue;
                    }
                    if (d <= clear && (s.is(BlockTags.LOGS) || s.is(ModBlocks.BLIGHTCAP_BLOCK.get()) || s.is(ModBlocks.ROTTING_BLIGHTCAP.get())
                            || s.is(ModBlocks.BLIGHTCAP_STEM.get())))
                    {
                        trees++;
                        if (treeBlocks.size() < 6)
                        {
                            treeBlocks.add(String.format(Locale.ROOT, "%s at %d, %d, %d (%.1f blocks out, biome %s)", ForgeRegistries.BLOCKS.getKey(s.getBlock()),
                                    x, y, z, d, dross.getBiome(pos).unwrapKey().map(k -> k.location().getPath()).orElse("?")));
                        }
                    }
                    if (d > r * (1.0D + SewerTuning.POISON_WOBBLE) || d <= SewerTuning.GRAND_RIM_RADIUS + 0.7D)
                    {
                        continue;
                    }
                    boolean top = dross.getBlockState(pos.set(x, y + 1, z)).isAir() || !dross.getBlockState(pos).isSolid();
                    pos.set(x, y, z);
                    coarse += s.is(Blocks.COARSE_DIRT) && top ? 1 : 0;
                    sludge += s.is(ModBlocks.SEWER_SLUDGE.get()) && top ? 1 : 0;
                    bushes += s.is(Blocks.DEAD_BUSH) ? 1 : 0;
                    bones += s.is(Blocks.BONE_BLOCK) ? 1 : 0;
                    carpets += s.getBlock() instanceof CarpetBlock ? 1 : 0;
                    webs += s.is(Blocks.COBWEB) ? 1 : 0;
                    fences += s.getBlock() instanceof FenceBlock ? 1 : 0;
                    vents += s.is(ModBlocks.BLIGHT_VENT.get()) ? 1 : 0;
                }
            }
        }
        if (coarse < MIN_COARSE_DIRT || sludge < MIN_POISON_SLUDGE || bushes < MIN_DEAD_BUSHES || bones < MIN_BONES || carpets < MIN_NEST_CARPETS
                || webs < MIN_COBWEBS || fences < MIN_FENCES)
        {
            problems.add(String.format(Locale.ROOT, "the ground round it isn't poisoned enough: %d coarse dirt (%d+), %d sludge puddles (%d+; %d planned), "
                            + "%d dead bushes (%d+), %d bone blocks (%d+), %d nest carpets (%d+), %d cobwebs (%d+), %d fence posts (%d+)", coarse, MIN_COARSE_DIRT,
                    sludge, MIN_POISON_SLUDGE, entrance.plannedSludge(), bushes, MIN_DEAD_BUSHES, bones, MIN_BONES, carpets, MIN_NEST_CARPETS, webs,
                    MIN_COBWEBS, fences, MIN_FENCES));
        }
        if (trees > 0)
        {
            problems.add(trees + " tree or giant mushroom blocks within " + clear + " blocks of it (world generation should keep them off): " + treeBlocks);
        }
        return String.format(Locale.ROOT, "%d coarse dirt, %d sludge puddles (%d planned), %d dead bushes, %d bone blocks, %d carpets, %d cobwebs, "
                + "%d fence posts, %d vents, %d tree blocks within %d", coarse, sludge, entrance.plannedSludge(), bushes, bones, carpets, webs, fences,
                vents, trees, clear);
    }

    /** The royal road: the main tunnel from the grand entrance to the cave. */
    private static String checkRoyalRoad(ServerLevel dross, List<SewerTunnelPiece> royal, List<String> problems)
    {
        if (royal.isEmpty())
        {
            problems.add("no royal road (no main tunnel in the Rat King's sewer)");
            return "none";
        }
        int middle = 0;
        int carpet = 0;
        int banners = 0;
        int candles = 0;
        int bones = 0;
        int gold = 0;
        int length = 0;
        for (SewerTunnelPiece t : royal)
        {
            BoundingBox box = t.getBoundingBox();
            int f = t.start().getY() - 1;
            boolean alongX = t.start().getZ() == t.end().getZ();
            int line = alongX ? t.start().getZ() : t.start().getX();
            int from = alongX ? t.start().getX() : t.start().getZ();
            int to = alongX ? t.end().getX() : t.end().getZ();
            length += to - from + 1;
            for (int u = from; u <= to; u++)
            {
                for (int v = -1; v <= 1; v++)
                {
                    BlockPos p = alongX ? new BlockPos(u, f + 1, line + v) : new BlockPos(line + v, f + 1, u);
                    middle++;
                    carpet += dross.getBlockState(p).getBlock() instanceof CarpetBlock ? 1 : 0;
                }
            }
            banners += count(dross, box, s -> s.is(Blocks.RED_WALL_BANNER));
            candles += count(dross, box, s -> s.is(Blocks.GREEN_CANDLE) && s.getValue(CandleBlock.LIT));
            bones += count(dross, box, s -> s.is(Blocks.BONE_BLOCK));
            gold += count(dross, box, s -> s.is(Blocks.RAW_GOLD_BLOCK) || s.is(Blocks.LIGHT_WEIGHTED_PRESSURE_PLATE));
        }
        if (carpet > 0 || banners < MIN_ROYAL_BANNERS || candles < MIN_ROYAL_CANDLES || bones + gold < 1)
        {
            problems.add(String.format(Locale.ROOT, "the royal road (%d blocks long) isn't right: %d of its %d middle blocks carpeted (none expected), %d banners "
                    + "(%d+), %d lit candles (%d+), %d bone piles and %d gold", length, carpet, middle, banners, MIN_ROYAL_BANNERS, candles, MIN_ROYAL_CANDLES,
                    bones, gold));
        }
        return String.format(Locale.ROOT, "%d tunnels, %d blocks long, no carpet, %d banners, %d lit candles, %d bone piles, %d gold",
                royal.size(), length, banners, candles, bones, gold);
    }

    /** Null if the list is just the two rats with that share of them enraged (counting group sizes), else what's wrong. */
    @Nullable
    static String ratsOnly(String what, WeightedRandomList<MobSpawnSettings.SpawnerData> list, double enragedShare)
    {
        double regular = 0.0D;
        double enraged = 0.0D;
        for (MobSpawnSettings.SpawnerData data : list.unwrap())
        {
            double count = data.getWeight().asInt() * (data.minCount + data.maxCount) / 2.0D;
            if (data.type == ModEntities.RAT.get())
            {
                regular += count;
            }
            else if (data.type == ModEntities.ENRAGED_RAT.get())
            {
                enraged += count;
            }
            else
            {
                return what + "'s monsters include " + EntityType.getKey(data.type) + " (rats only expected)";
            }
        }
        if (regular <= 0.0D || enraged <= 0.0D || Math.abs(enraged / (regular + enraged) - enragedShare) > SHARE_TOLERANCE)
        {
            return String.format(Locale.ROOT, "%s's rats are %.0f%% enraged (%.0f%% expected)", what, 100.0D * enraged / Math.max(1.0D, regular + enraged),
                    100.0D * enragedShare);
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Sewer Sludge
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = TerrainBlocksGameTest.ARENA, batch = "dross_sewer_sludge")
    public static void sewerSludgeSlowsWalkers(GameTestHelper helper)
    {
        float factor = ModBlocks.SEWER_SLUDGE.get().getSpeedFactor();
        float soulSand = Blocks.SOUL_SAND.getSpeedFactor();
        if (factor != TerrainBlocks.SEWER_SLUDGE_SPEED || factor <= soulSand || factor >= 1.0F)
        {
            throw new GameTestAssertException("Sewer Sludge's speed factor is " + factor + " (it should slow things, less than soul sand's " + soulSand + ")");
        }
        // three lanes along x: stone, Sewer Sludge, soul sand; an item slides along each with the same push
        BlockState[] lanes = {Blocks.STONE.defaultBlockState(), ModBlocks.SEWER_SLUDGE.get().defaultBlockState(), Blocks.SOUL_SAND.defaultBlockState()};
        List<ItemEntity> items = new ArrayList<>();
        for (int lane = 0; lane < lanes.length; lane++)
        {
            int z = 5 + lane * 6;
            for (int x = 1; x < TerrainBlocksGameTest.ARENA_SIZE - 1; x++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    helper.setBlock(new BlockPos(x, 1, z + dz), lanes[lane]);
                }
            }
            ItemEntity item = new ItemEntity(helper.getLevel(), helper.absolutePos(new BlockPos(2, 2, z)).getX() + 0.5D,
                    helper.absolutePos(new BlockPos(2, 2, z)).getY() + 0.05D, helper.absolutePos(new BlockPos(2, 2, z)).getZ() + 0.5D,
                    Items.COBBLESTONE.getDefaultInstance());
            item.setPickUpDelay(32767);
            item.setDeltaMovement(Vec3.ZERO);
            helper.getLevel().addFreshEntity(item);
            items.add(item);
        }
        double[] startX = new double[3];
        helper.startSequence()
                .thenIdle(10)    // let them settle onto the floor
                .thenExecute(() -> {
                    for (int i = 0; i < items.size(); i++)
                    {
                        startX[i] = items.get(i).getX();
                        items.get(i).setDeltaMovement(SLIDE_SPEED, 0.0D, 0.0D);
                    }
                })
                .thenIdle(SLIDE_TICKS)
                .thenExecute(() -> {
                    double stone = items.get(0).getX() - startX[0];
                    double sludge = items.get(1).getX() - startX[1];
                    double sand = items.get(2).getX() - startX[2];
                    double share = sludge / stone;
                    LOGGER.info("[sewer sludge] an item pushed at {} blocks a tick slides {} blocks on stone, {} on Sewer Sludge ({}%), {} on soul sand ({}%)",
                            SLIDE_SPEED, String.format(Locale.ROOT, "%.2f", stone), String.format(Locale.ROOT, "%.2f", sludge),
                            Math.round(100.0D * share), String.format(Locale.ROOT, "%.2f", sand), Math.round(100.0D * sand / stone));
                    if (!(stone > sludge && sludge > sand) || share < SLUDGE_SLOW_MIN || share > SLUDGE_SLOW_MAX)
                    {
                        throw new GameTestAssertException(String.format(Locale.ROOT, "pushed the same, items slid %.2f blocks on stone, %.2f on Sewer Sludge and "
                                + "%.2f on soul sand (sludge should slow them, less than soul sand)", stone, sludge, sand));
                    }
                    items.forEach(ItemEntity::discard);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static ServerLevel dross(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        return dross;
    }

    /** Keeps a sequence waiting (in real time, up to the deadline) until the future is done. */
    static void waitFor(CompletableFuture<?> future, long deadline, String what)
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

    private static void fail(List<String> problems, String what)
    {
        if (!problems.isEmpty())
        {
            throw new GameTestAssertException(what + ": " + String.join("; ", problems.subList(0, Math.min(10, problems.size()))));
        }
    }

    /** The start of the structure in its start chunk (loaded to its structure stage), or a failure. */
    static StructureStart startAt(ServerLevel dross, ResourceKey<Structure> key, ChunkPos chunkPos)
    {
        Structure structure = dross.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        ChunkAccess chunk = dross.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = structure == null ? null : dross.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
        if (start == null || !start.isValid())
        {
            throw new GameTestAssertException("no " + key.location() + " starts in its planned chunk " + chunkPos);
        }
        return start;
    }

    /** Generates (or loads) every chunk the box touches, fully (server thread). */
    private static void loadAll(ServerLevel dross, BoundingBox box)
    {
        for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
        {
            for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
            {
                dross.getChunk(cx, cz);
            }
        }
    }

    /**
     * Which columns of the box a walk at floor + 1 (feet) reaches from {@code from}: through every column whose feet and
     * head blocks are open (air, water, a ladder, a carpet, a cobweb...). 4-way steps, the same height throughout (the
     * sewers are one level).
     */
    private static boolean[] walk(ServerLevel dross, BoundingBox box, int floor, BlockPos from)
    {
        int w = box.getXSpan();
        int d = box.getZSpan();
        boolean[] seen = new boolean[w * d];
        ArrayDeque<int[]> queue = new ArrayDeque<>();
        int sx = from.getX() - box.minX();
        int sz = from.getZ() - box.minZ();
        if (sx < 0 || sz < 0 || sx >= w || sz >= d)
        {
            return seen;
        }
        seen[sz * w + sx] = true;
        queue.add(new int[] {sx, sz});
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[][] steps = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}};
        while (!queue.isEmpty())
        {
            int[] c = queue.poll();
            for (int[] s : steps)
            {
                int nx = c[0] + s[0];
                int nz = c[1] + s[1];
                if (nx < 0 || nz < 0 || nx >= w || nz >= d || seen[nz * w + nx])
                {
                    continue;
                }
                int x = box.minX() + nx;
                int z = box.minZ() + nz;
                if (open(dross, pos.set(x, floor + 1, z)) && open(dross, pos.set(x, floor + 2, z)))
                {
                    seen[nz * w + nx] = true;
                    queue.add(new int[] {nx, nz});
                }
            }
        }
        return seen;
    }

    private static boolean open(ServerLevel dross, BlockPos pos)
    {
        BlockState state = dross.getBlockState(pos);
        return state.getCollisionShape(dross, pos).isEmpty() || !state.getFluidState().isEmpty() || state.getBlock() instanceof LadderBlock
                || state.getBlock() instanceof CarpetBlock;
    }

    private static boolean reachedAt(boolean[] reached, BoundingBox box, BlockPos pos)
    {
        int x = pos.getX() - box.minX();
        int z = pos.getZ() - box.minZ();
        return x >= 0 && z >= 0 && x < box.getXSpan() && z < box.getZSpan() && reached[z * box.getXSpan() + x];
    }

    private static int count(ServerLevel dross, BoundingBox box, Predicate<BlockState> what)
    {
        int n = 0;
        for (BlockPos p : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
        {
            if (what.test(dross.getBlockState(p)))
            {
                n++;
            }
        }
        return n;
    }

    /** True if the block is one of the mix's blocks. */
    private static boolean isIn(SewerTuning.Mix mix, BlockState state)
    {
        return mix.states().stream().anyMatch(s -> state.is(s.getBlock()));
    }

    // ---------------------------------------------------------------------------------------------
    // Previews (tools/terrain/preview_sewers.py draws them)
    // ---------------------------------------------------------------------------------------------

    private static boolean previews()
    {
        return "1".equals(System.getenv(PREVIEW_ENV));
    }

    private static Path previewDir()
    {
        return FMLPaths.GAMEDIR.get().toAbsolutePath().getParent().resolve("build").resolve("tv1").resolve("sewers");
    }

    /**
     * A top-down map of the network: for each column, what's at the floor (tunnel floor, water, sludge, rough cave, rat
     * cave, nest, gold, carpet, entrance, or rock), and whether the surface over it is Blightwood. Written as rows of letters.
     */
    private static void dumpNetwork(ServerLevel dross, StructureStart start, String name, String title)
    {
        BoundingBox box = start.getBoundingBox();
        int floor = Integer.MIN_VALUE;
        List<int[]> pipes = new ArrayList<>();
        List<BoundingBox> caveSections = new ArrayList<>();
        List<BoundingBox> road = new ArrayList<>();
        Optional<RatCaves.RatCave> cave = RatCaves.of(start);
        for (StructurePiece piece : start.getPieces())
        {
            if (piece instanceof SewerEntrance pipe)
            {
                floor = pipe.centre().getY();
                double rim = pipe.grand() ? SewerTuning.GRAND_RIM_RADIUS : SewerTuning.PIPE_RIM_RADIUS;
                pipes.add(new int[] {pipe.centre().getX(), pipe.centre().getZ(), pipe.rimTopY(), pipe.groundY(), pipe.openingHalf(), (int) Math.floor(rim * rim),
                        pipe.grand() ? 1 : 0});
            }
            else if (piece instanceof SewerNodePiece node && node.kind() == SewerNodePiece.Kind.CAVE)
            {
                caveSections.add(node.getBoundingBox());
            }
            // the royal road (it has no carpet to see it by): its tunnels' middle 3 blocks and its junctions' insides
            if (piece instanceof SewerTunnelPiece tunnel && tunnel.isRoyal())
            {
                road.add(middleOf(tunnel));
            }
            else if (piece instanceof SewerNodePiece node && node.isRoyal() && node.kind() != SewerNodePiece.Kind.GRAND_POOL)
            {
                road.add(node.getBoundingBox().inflatedBy(-1));
            }
        }
        if (floor == Integer.MIN_VALUE)
        {
            return;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        JsonArray rows = new JsonArray();
        JsonArray surface = new JsonArray();
        for (int z = box.minZ(); z <= box.maxZ(); z++)
        {
            StringBuilder row = new StringBuilder();
            StringBuilder top = new StringBuilder();
            for (int x = box.minX(); x <= box.maxX(); x++)
            {
                row.append(classify(dross, pos, x, z, floor, cave, caveSections, road, pipes));
                top.append(DrossBiomes.isBiome(dross, new BlockPos(x, SewerStructure.SURFACE_BIOME_Y, z), DrossBiomes.BLIGHTWOOD) ? 'b' : '.');
            }
            rows.add(row.toString());
            surface.add(top.toString());
        }
        JsonObject root = new JsonObject();
        root.addProperty("note", "Written by SewersGameTest; drawn by tools/terrain/preview_sewers.py");
        root.addProperty("title", title);
        root.addProperty("x0", box.minX());
        root.addProperty("z0", box.minZ());
        root.addProperty("floor", floor);
        root.add("rows", rows);
        root.add("surface", surface);
        JsonArray pipeList = new JsonArray();
        for (int[] p : pipes)
        {
            JsonArray a = new JsonArray();
            for (int v : p)
            {
                a.add(v);
            }
            pipeList.add(a);
        }
        root.add("pipes", pipeList);
        cave.ifPresent(c -> {
            JsonObject o = new JsonObject();
            o.addProperty("x", c.centre().getX());
            o.addProperty("z", c.centre().getZ());
            o.addProperty("arrival_x", c.arrival().getX());
            o.addProperty("arrival_z", c.arrival().getZ());
            root.add("cave", o);
        });
        write(previewDir().resolve("map_" + name + ".json"), root);
        // and a cutaway of its first regular pipe
        for (int[] p : pipes)
        {
            if (p[6] == 0)
            {
                dumpView(dross, p[0], p[1], 10, floor - VIEW_BELOW_POOL, p[2] + 3, (x, y, z) -> z <= p[1], "pipe",
                        "A pipe entrance, cut in half", dross.getBiome(new BlockPos(p[0], floor, p[1])).value().getWaterColor(), false);
                break;
            }
        }
    }

    /** The middle 3 blocks of a tunnel all the way along it, at its feet height (for the map). */
    private static BoundingBox middleOf(SewerTunnelPiece t)
    {
        BlockPos a = t.start();
        BlockPos b = t.end();
        boolean alongX = a.getZ() == b.getZ();
        return alongX ? new BoundingBox(Math.min(a.getX(), b.getX()), a.getY(), a.getZ() - 1, Math.max(a.getX(), b.getX()), a.getY(), a.getZ() + 1)
                : new BoundingBox(a.getX() - 1, a.getY(), Math.min(a.getZ(), b.getZ()), a.getX() + 1, a.getY(), Math.max(a.getZ(), b.getZ()));
    }

    private static char classify(ServerLevel dross, BlockPos.MutableBlockPos pos, int x, int z, int floor, Optional<RatCaves.RatCave> cave,
                                 List<BoundingBox> caveSections, List<BoundingBox> road, List<int[]> pipes)
    {
        for (int[] p : pipes)
        {
            int dx = x - p[0];
            int dz = z - p[1];
            if (Math.abs(dx) <= p[4] && Math.abs(dz) <= p[4])
            {
                return 'O';
            }
            if (dx * dx + dz * dz <= p[5])
            {
                return 'P';
            }
        }
        BlockState feet = dross.getBlockState(pos.set(x, floor + 1, z));
        BlockState below = dross.getBlockState(pos.set(x, floor, z));
        String feetId = feet.getBlock().getDescriptionId();
        for (BoundingBox r : road)
        {
            if (x >= r.minX() && x <= r.maxX() && z >= r.minZ() && z <= r.maxZ())
            {
                return 'R';
            }
        }
        if (feetId.endsWith("_carpet") || feetId.endsWith("_wool"))
        {
            return 'N';
        }
        boolean inCave = cave.isPresent() && cave.get().contains(pos.set(x, floor + 1, z));
        if (inCave && (feet.is(Blocks.GOLD_BLOCK) || feet.is(ModBlocks.DUSKSTONE_GOLD_ORE.get())))
        {
            return 'G';
        }
        if (feet.isAir() || !feet.getFluidState().isEmpty() || feet.is(ModBlocks.GLOOMCAP.get()) || feet.is(Blocks.COBWEB)
                || feet.getCollisionShape(dross, pos.set(x, floor + 1, z)).isEmpty() || feet.is(Blocks.GREEN_CANDLE))
        {
            BlockState under = dross.getBlockState(pos.set(x, floor, z));
            if (!under.getFluidState().isEmpty())
            {
                return 'W';
            }
            if (under.is(ModBlocks.SEWER_SLUDGE.get()))
            {
                return 'S';
            }
            if (inCave)
            {
                return 'C';
            }
            for (BoundingBox b : caveSections)
            {
                if (b.isInside(x, floor, z))
                {
                    return 'c';
                }
            }
            return 'T';
        }
        if (feet.is(ModBlocks.SEWER_SLUDGE.get()))
        {
            return 'S';
        }
        if (feetId.contains("copper"))
        {
            return 'p';
        }
        return inCave ? 'C' : below.isAir() ? '.' : '.';
    }

    /** Cutaways of the rat cave: from above with its roof off (lit only by its candles, lichen and Gloomcaps), and cut in half. */
    private static void dumpCave(ServerLevel dross, RatCaves.RatCave cave)
    {
        int f = cave.floorY();
        int cx = cave.centre().getX();
        int cz = cave.centre().getZ();
        int half = 25;
        int water = dross.getBiome(cave.centre()).value().getWaterColor();
        dumpView(dross, cx, cz, half, f - 2, f + 7, (x, y, z) -> true, "rat_cave_top", "The rat cave with its roof off, lit by its own light", water, true);
        dumpElevation(dross, cx, cz, half, f - 2, f + 18, "rat_cave_section", "The rat cave, cut in half", water);
    }

    /** The grand entrance: from outside at night (its own light), and cut in half down to its pool. */
    private static void dumpGrandEntrance(ServerLevel dross, SewerGrandPipePiece entrance)
    {
        BlockPos c = entrance.centre();
        int g = entrance.groundY();
        int water = dross.getBiome(c).value().getWaterColor();
        int below = 14;   // (deep enough to reach the lowest ground of the poisoned area)
        dumpView(dross, c.getX(), c.getZ(), SewerTuning.POISON_RADIUS + 4, g - below, entrance.rimTopY() + SewerTuning.CROWN_TALL + 2, (x, y, z) -> true,
                "grand_entrance", "The Rat King's grand entrance and its poisoned ground", water, false);
        dumpView(dross, c.getX(), c.getZ(), SewerTuning.POISON_RADIUS + 4, g - below, entrance.rimTopY() + SewerTuning.CROWN_TALL + 2, (x, y, z) -> true,
                "grand_entrance_night", "The Rat King's grand entrance at night, lit by its own glow", water, true);
        dumpView(dross, c.getX(), c.getZ(), 12, c.getY() - SewerTuning.GRAND_POOL_DEPTH - 2, entrance.rimTopY() + SewerTuning.CROWN_TALL + 2,
                (x, y, z) -> z <= c.getZ(), "grand_entrance_cut", "The Rat King's grand entrance, cut in half", water, false);
    }

    /** The whole royal road with everything over its floor's first block taken away, lit by its candles. */
    private static void dumpRoyalRoad(ServerLevel dross, List<SewerTunnelPiece> royal)
    {
        if (royal.isEmpty())
        {
            return;
        }
        int x0 = Integer.MAX_VALUE;
        int z0 = Integer.MAX_VALUE;
        int x1 = Integer.MIN_VALUE;
        int z1 = Integer.MIN_VALUE;
        for (SewerTunnelPiece t : royal)
        {
            BoundingBox b = t.getBoundingBox();
            x0 = Math.min(x0, b.minX());
            z0 = Math.min(z0, b.minZ());
            x1 = Math.max(x1, b.maxX());
            z1 = Math.max(z1, b.maxZ());
        }
        int f = royal.get(0).start().getY() - 1;
        int cx = (x0 + x1) / 2;
        int cz = (z0 + z1) / 2;
        int half = Math.min(ROYAL_VIEW_MAX_HALF, Math.max(x1 - x0, z1 - z0) / 2 + 4);
        dumpView(dross, cx, cz, half, f - 1, f + 1, (x, y, z) -> true, "royal_road",
                "The royal road to the Rat King's cave, cut just over its floor, lit by its candles",
                dross.getBiome(new BlockPos(cx, f + 1, cz)).value().getWaterColor(), true);
    }

    /**
     * A side view of the far half of a cave (looking north from the line through its middle): for each column x and
     * height y, the first block behind the cut and how far behind it is. preview_sewers.py draws it face on.
     */
    private static void dumpElevation(ServerLevel dross, int cx, int cz, int half, int y0, int y1, String name, String title, int waterColor)
    {
        Map<String, Integer> palette = new LinkedHashMap<>();
        JsonArray rows = new JsonArray();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = y1; y >= y0; y--)
        {
            JsonArray row = new JsonArray();
            for (int x = cx - half; x <= cx + half; x++)
            {
                int index = -1;
                int depth = 0;
                for (int d = 0; d <= half; d++)
                {
                    BlockState state = dross.getBlockState(pos.set(x, y, cz + d));
                    if (!state.isAir())
                    {
                        index = palette.computeIfAbsent(BlockStateParser.serialize(state), k -> palette.size());
                        depth = d;
                        break;
                    }
                }
                JsonArray cell = new JsonArray();
                cell.add(index);
                cell.add(depth);
                row.add(cell);
            }
            rows.add(row);
        }
        JsonObject root = new JsonObject();
        root.addProperty("note", "Written by SewersGameTest; drawn by tools/terrain/preview_sewers.py");
        root.addProperty("title", title);
        root.addProperty("y0", y0);
        root.addProperty("y1", y1);
        root.addProperty("water_color", waterColor);
        JsonArray names = new JsonArray();
        palette.keySet().forEach(names::add);
        root.add("palette", names);
        root.add("rows", rows);
        write(previewDir().resolve("elevation_" + name + ".json"), root);
    }

    private interface Keep
    {
        boolean test(int x, int y, int z);
    }

    /**
     * Writes the blocks of a box (x and z within half of cx, cz; y from y0 to y1) that pass {@code keep} and can be seen
     * from above and the south-east, for preview_sites.py's renderer: lit all through (block light 13), or by the real
     * block light round each block ({@code realLight}: a night view).
     */
    private static void dumpView(ServerLevel dross, int cx, int cz, int half, int y0, int y1, Keep keep, String name, String title, int waterColor,
                                 boolean realLight)
    {
        int n = 2 * half + 1;
        int x0 = cx - half;
        int z0 = cz - half;
        Map<String, Integer> palette = new LinkedHashMap<>();
        JsonArray blocks = new JsonArray();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for (int j = 0; j < n; j++)
        {
            for (int i = 0; i < n; i++)
            {
                for (int y = y0; y <= y1; y++)
                {
                    int x = x0 + i;
                    int z = z0 + j;
                    if (!keep.test(x, y, z))
                    {
                        continue;
                    }
                    BlockState state = dross.getBlockState(pos.set(x, y, z));
                    if (state.isAir())
                    {
                        continue;
                    }
                    boolean hidden = y < y1 && i < n - 1 && j < n - 1 && keep.test(x, y + 1, z) && keep.test(x, y, z + 1) && keep.test(x + 1, y, z)
                            && solid(dross, x, y + 1, z) && solid(dross, x, y, z + 1) && solid(dross, x + 1, y, z);
                    if (hidden)
                    {
                        continue;
                    }
                    String key = BlockStateParser.serialize(state);
                    int index = palette.computeIfAbsent(key, k -> palette.size());
                    int light = 13;
                    if (realLight)
                    {
                        light = Math.max(dross.getBrightness(LightLayer.BLOCK, pos),
                                Math.max(dross.getBrightness(LightLayer.BLOCK, next.setWithOffset(pos, 0, 1, 0)),
                                        Math.max(dross.getBrightness(LightLayer.BLOCK, next.setWithOffset(pos, 0, 0, 1)),
                                                dross.getBrightness(LightLayer.BLOCK, next.setWithOffset(pos, 1, 0, 0)))));
                    }
                    JsonArray b = new JsonArray();
                    b.add(i);
                    b.add(y - y0);
                    b.add(j);
                    b.add(index);
                    b.add(light);
                    blocks.add(b);
                }
            }
        }
        JsonObject root = new JsonObject();
        root.addProperty("note", "Written by SewersGameTest; drawn by tools/terrain/preview_sewers.py");
        root.addProperty("name", name);
        root.addProperty("title", title);
        root.addProperty("x0", x0);
        root.addProperty("y0", y0);
        root.addProperty("z0", z0);
        root.addProperty("size", n);
        root.addProperty("water_color", waterColor);
        JsonArray names = new JsonArray();
        palette.keySet().forEach(names::add);
        root.add("palette", names);
        root.add("blocks", blocks);
        write(previewDir().resolve("view_" + name + ".json"), root);
    }

    private static boolean solid(ServerLevel dross, int x, int y, int z)
    {
        BlockPos p = new BlockPos(x, y, z);
        return dross.getBlockState(p).isSolidRender(dross, p);
    }

    private static void write(Path file, JsonObject json)
    {
        try
        {
            Files.createDirectories(file.getParent());
            Files.writeString(file, new GsonBuilder().create().toJson(json), StandardCharsets.UTF_8);
        }
        catch (IOException e)
        {
            LOGGER.warn("[sewers] couldn't write {}", file, e);
        }
    }
}
