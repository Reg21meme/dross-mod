package com.reg21meme.dross.dimension;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.sewer.Sewers;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.MultifaceBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Headless check (GameTest) of real generated Dross land: for each of the six biomes it finds a sample site in this
 * world (from the noise: a spot round {@value #SITE_X}, {@value #SITE_Z} whose surroundings, {@value #SITE_CLEAR} blocks
 * every way, are all that biome; for the Gravecrags the highest such spot), generates {@value #SITE_CHUNKS} x
 * {@value #SITE_CHUNKS} chunks there for real, and checks:
 * <ul>
 *   <li>the biome in the middle is the one asked for, and the hub at 0, 0 stands in Silverwood Forest (read from the
 *       noise, so an older world whose hub chunk was made flat, as Dross Flats, still passes);</li>
 *   <li>bedrock at the world's floor in every column, and none from 5 blocks above it up;</li>
 *   <li>no air more than {@value #AIR_DEPTH} blocks under the land's natural top, as the noise made it (no caves or gaps
 *       in the terrain; boulders, ice spikes and the like stand on that top and may overhang a slope, so the ground's
 *       real top isn't used for this);</li>
 *   <li>the ground's top blocks: at least 99% are the biome's own ({@link #GROUND}: its ground cover and rock, and what its
 *       features add to the ground, such as boulders, pond shores and vents), its ground cover (not bare rock) covers at
 *       least 60%, and its own signature block is there (at least 15%; 40% Cragstone or snow in the Gravecrags); in the
 *       Nether Scar, instead, the netherrack ground its soul and nylium patches leave is there (at least 3% of the site)
 *       and 5-92% of it is Scarred Netherrack (the cracks mixed with plain netherrack; see SCAR_CRACKED_MIN for why);</li>
 *   <li>every fluid is a still source, lava has something solid (or lava) on every side and below, and the Nether
 *       Scar has no lava or fire; every plant, vine, lichen and snow layer could stay where it is.</li>
 * </ul>
 * A sewer network under a Blightwood site (the only open space underground; its waterfalls flow) is left out of these
 * checks, block by block, and so are the columns its pipes stand on: {@link SewersGameTest} checks the sewers.
 * The time to generate each site is logged ("[terrain chunks]"), per chunk (with every feature: trees, plants, ponds,
 * ores). The sites are also what {@link TerrainPreviewGameTest} draws. What grows on the land is checked in detail by
 * {@link TerrainFeaturesGameTest}.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class TerrainChunksGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Where to look for sample sites (far enough from the hub that its pushes don't matter), and how far round it. */
    static final int SITE_X = 6000;
    static final int SITE_Z = 6000;
    static final int SITE_SEARCH = 3072;
    static final int SITE_CELL = 16;
    /** A site's surroundings this far every way must all be its biome. */
    static final int SITE_CLEAR = 64;
    /** A site is this many chunks square (centred on the spot found). */
    static final int SITE_CHUNKS = 5;
    /** Brand-new Overworld chunks generated here for a speed comparison (far from spawn and the portal site's search). */
    private static final int OVERWORLD_BASELINE_X = -14000;
    private static final int OVERWORLD_BASELINE_Z = 14000;
    /** No air allowed more than this far under the ground's top. */
    private static final int AIR_DEPTH = 8;
    /** At least this share of the top blocks must be the biome's own. */
    private static final double OWN_GROUND = 0.99D;

    /**
     * Each biome's ground (keep in step with SURFACE in tools/terrain/worldgen.py):
     * @param allowed        every top block it may have (99% of a site's top blocks must be one of these)
     * @param cover          its ground cover (not bare rock): must cover at least {@code coverShare} of a site, so slopes and
     *                       rock don't take over
     * @param signature      its own block(s): at least {@code signatureShare} of a site, so its rules really ran (a patch of
     *                       another cover block can be large on one 80-block site, so this is low)
     */
    record Ground(Set<String> allowed, Set<String> cover, double coverShare, Set<String> signature, double signatureShare) {}

    static final Map<ResourceKey<Biome>, Ground> GROUND = Map.of(
            // (mud and clay: the shores and beds of the ponds; Duskstone and moss: the mossy boulders)
            DrossBiomes.SILVERWOOD_FOREST, new Ground(Set.of("dross:silvermoss", "minecraft:podzol", "minecraft:moss_block", "dross:duskstone",
                    "minecraft:mud", "minecraft:clay"),
                    Set.of("dross:silvermoss", "minecraft:podzol", "minecraft:moss_block"), 0.60D, Set.of("dross:silvermoss"), 0.15D),
            // (blackstone: under the lava pools; Cinder Vents set into the ground)
            DrossBiomes.CINDER_WASTES, new Ground(Set.of("dross:ash", "minecraft:magma_block", "minecraft:basalt", "minecraft:blackstone",
                    "dross:cinder_vent"),
                    Set.of("dross:ash", "minecraft:magma_block", "minecraft:basalt"), 0.60D, Set.of("dross:ash"), 0.15D),
            // (mud: its wet patches and the murky ponds' shores)
            DrossBiomes.BLIGHTWOOD, new Ground(Set.of("dross:sporeloam", "minecraft:mud", "minecraft:coarse_dirt", "dross:duskstone"),
                    Set.of("dross:sporeloam", "minecraft:mud", "minecraft:coarse_dirt"), 0.60D, Set.of("dross:sporeloam"), 0.15D),
            // (ice: the frozen ponds)
            DrossBiomes.RIMEFALL, new Ground(Set.of("dross:hoarfrost", "minecraft:snow_block", "minecraft:packed_ice", "dross:duskstone",
                    "minecraft:ice"),
                    Set.of("dross:hoarfrost", "minecraft:snow_block", "minecraft:packed_ice"), 0.60D, Set.of("dross:hoarfrost"), 0.15D),
            // (Scarred Netherrack lies in clusters among plain netherrack, on the ground the soul and nylium patches leave: its
            // share of a whole site swings too much to judge, so it's judged by the cracks' own mix instead: SCAR_* below)
            DrossBiomes.NETHER_SCAR, new Ground(Set.of("dross:scarred_netherrack", "minecraft:crimson_nylium", "minecraft:warped_nylium",
                    "minecraft:soul_soil", "minecraft:soul_sand", "minecraft:netherrack", "dross:scar_vent"),
                    Set.of("dross:scarred_netherrack", "minecraft:crimson_nylium", "minecraft:warped_nylium", "minecraft:soul_soil",
                            "minecraft:soul_sand", "minecraft:netherrack"), 0.60D, Set.of("dross:scarred_netherrack"), 0.0D),
            DrossBiomes.GRAVECRAGS, new Ground(Set.of("dross:cragstone", "minecraft:snow_block", "minecraft:gravel", "minecraft:moss_block"),
                    Set.of("dross:cragstone", "minecraft:snow_block", "minecraft:gravel", "minecraft:moss_block"), 0.60D,
                    Set.of("dross:cragstone", "minecraft:snow_block"), 0.40D));

    /*
     * The Nether Scar's cracks. Its top is decided in this order (SURFACE in tools/terrain/worldgen.py): soul soil and soul
     * sand patches, then crimson and warped nylium patches, then, on the ground those leave (its "crack ground"), Scarred
     * Netherrack in ragged clusters mixed with plain netherrack (SCARRED_PATCH, SCARRED_SPECKLE: about 55% cracked; the
     * user found it too busy when it was all cracked). The patches can cover most of an 80-block site, so the cracked share
     * of a whole site swings a lot (from about 4% to 63%; 6.8% on the seed where the old 10% check failed), but the cracked
     * share of its crack ground is the cracks' own rules at work, and steadier.
     *
     * Measured (round 2, R6) with the surface rules' own noises and thresholds (which match real generated chunks to
     * 0.3%), on 5,317 sites like these (80 blocks square, all Nether Scar for 64 blocks round) on 405 seeds, 405 of them
     * the very sites this test picks:
     *   cracked share of the crack ground: lowest 11.0% (then 12.3%, 18.2%), 1 in 1000 below 20%, middle 56%, highest
     *   82.7% (the sites this test picks: 24% to 80%);
     *   crack ground share of the site: lowest 6.9%, 1 in 1000 below 13% (the sites this test picks: 13.5% at least);
     *   the old check (Scarred Netherrack on 10% of the whole site) failed on 1.4% of them (6 of the 405 picked sites),
     *   and the one before it (plain netherrack on 5%) on 0.2%.
     * The limits below sit well outside all of that, so they only fail when the rules really change.
     */
    /**
     * At least this share of a Scar site's crack ground must be Scarred Netherrack (else it looks like plain netherrack):
     * under half the lowest measured (11%).
     */
    private static final double SCAR_CRACKED_MIN = 0.05D;
    /**
     * At most this share of it may be (else the cracks aren't mixed with plain netherrack any more, as before round 2):
     * above the highest measured (82.7%).
     */
    private static final double SCAR_CRACKED_MAX = 0.92D;
    /**
     * At least this share of a Scar site must be crack ground at all (else the soul and nylium patches have taken over the
     * Scar): under half the lowest measured (6.9%).
     */
    private static final double SCAR_CRACK_GROUND_MIN = 0.03D;

    private TerrainChunksGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_terrain_chunks", timeoutTicks = TerrainSamplingGameTest.BACKSTOP_TICKS)
    public static void realChunksPerBiome(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        TerrainProbe probe = TerrainProbe.forWorld(dross);
        CompletableFuture<Map<ResourceKey<Biome>, BlockPos>> sites = CompletableFuture.supplyAsync(() -> findSites(probe, SITE_X, SITE_Z));
        long deadline = Util.getMillis() + TerrainSamplingGameTest.TIMEOUT_MILLIS;
        List<String> problems = new ArrayList<>();
        GameTestSequence sequence = helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!sites.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still looking for sample sites");
                    }
                })
                .thenExecute(() -> {
                    if (!sites.isDone())
                    {
                        throw new GameTestAssertException("looking for sample sites took too long");
                    }
                    // From the noise, not the chunk: an older world's hub chunk was made flat (Dross Flats) before the terrain.
                    BlockPos hub = DrossHub.CENTER.atY(TerrainProbe.BIOME_Y);
                    if (!DrossBiomes.isBiome(dross, hub, DrossBiomes.SILVERWOOD_FOREST))
                    {
                        problems.add("the hub at 0, 0 stands in " + TerrainProbe.forWorld(dross).biomeKey(hub.getX(), hub.getZ())
                                + " (by the noise), not Silverwood Forest");
                    }
                });
        for (ResourceKey<Biome> biome : DrossBiomes.ALL)
        {
            sequence = sequence.thenExecute(() -> {
                BlockPos site = sites.join().get(biome);
                if (site == null)
                {
                    problems.add("no sample site for " + DrossBiomes.name(biome) + " within " + SITE_SEARCH + " blocks of " + SITE_X + ", " + SITE_Z);
                    return;
                }
                long t = Util.getNanos();
                List<LevelChunk> chunks = generate(dross, site);
                double ms = (Util.getNanos() - t) / 1.0E6D / chunks.size();
                checkSite(dross, probe, biome, site, chunks, ms, problems);
            });
        }
        sequence.thenExecute(() -> {
            // For comparison: the same number of brand-new Overworld chunks (vanilla's own terrain), far from everything.
            ServerLevel overworld = helper.getLevel().getServer().overworld();
            long t = Util.getNanos();
            List<LevelChunk> chunks = generate(overworld, new BlockPos(OVERWORLD_BASELINE_X, 0, OVERWORLD_BASELINE_Z));
            LOGGER.info("[terrain chunks] for comparison, {} new Overworld chunks took {} ms per chunk", chunks.size(),
                    String.format(Locale.ROOT, "%.1f", (Util.getNanos() - t) / 1.0E6D / chunks.size()));
        }).thenExecute(() -> {
            if (!problems.isEmpty())
            {
                throw new GameTestAssertException("the generated land isn't right: " + String.join("; ", problems.subList(0, Math.min(12, problems.size())))
                        + (problems.size() > 12 ? " (and " + (problems.size() - 12) + " more)" : ""));
            }
        }).thenSucceed();
    }

    /** {@link #findSites(TerrainProbe, int, int)} round SITE_X, SITE_Z (the sites the previews draw too). */
    static Map<ResourceKey<Biome>, BlockPos> findSites(TerrainProbe probe)
    {
        return findSites(probe, SITE_X, SITE_Z);
    }

    /**
     * A sample site for each biome near centreX, centreZ (within SITE_SEARCH): the nearest spot whose surroundings
     * (SITE_CLEAR blocks every way) are all that biome; for the Gravecrags the highest such spot. Noise only; off the
     * server thread.
     */
    static Map<ResourceKey<Biome>, BlockPos> findSites(TerrainProbe probe, int centreX, int centreZ)
    {
        return findSites(probe, centreX, centreZ, SITE_CLEAR);
    }

    /** As {@link #findSites(TerrainProbe, int, int)}, with the surroundings that must be all one biome {@code clear} blocks every way. */
    static Map<ResourceKey<Biome>, BlockPos> findSites(TerrainProbe probe, int centreX, int centreZ, int clear)
    {
        int n = 2 * SITE_SEARCH / SITE_CELL + 1;
        int x0 = centreX - SITE_SEARCH;
        int z0 = centreZ - SITE_SEARCH;
        int[] grid = TerrainSurvey.biomeGrid(probe, x0, z0, SITE_CELL, n, n);
        int r = (clear + SITE_CELL - 1) / SITE_CELL;
        Map<ResourceKey<Biome>, BlockPos> sites = new LinkedHashMap<>();
        Map<Integer, double[]> best = new HashMap<>();
        for (int j = r; j < n - r; j++)
        {
            for (int i = r; i < n - r; i++)
            {
                int b = grid[j * n + i];
                if (b < 0 || !clear(grid, n, i, j, r, b))
                {
                    continue;
                }
                int x = x0 + i * SITE_CELL;
                int z = z0 + j * SITE_CELL;
                double score = b == TerrainSurvey.GRAVECRAGS ? -probe.height(x, z) : Math.hypot(x - centreX, z - centreZ);
                double[] old = best.get(b);
                if (old == null || score < old[0])
                {
                    best.put(b, new double[] {score, x, z});
                }
            }
        }
        for (int b = 0; b < TerrainSurvey.BIOMES; b++)
        {
            double[] found = best.get(b);
            if (found != null)
            {
                sites.put(DrossBiomes.ALL.get(b), new BlockPos((int) found[1], 0, (int) found[2]));
            }
        }
        return sites;
    }

    private static boolean clear(int[] grid, int n, int i, int j, int r, int b)
    {
        for (int dj = -r; dj <= r; dj++)
        {
            for (int di = -r; di <= r; di++)
            {
                if (grid[(j + dj) * n + i + di] != b)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** Generates (or loads) the site's chunks ({@value #SITE_CHUNKS} x {@value #SITE_CHUNKS}), fully. Server thread. */
    static List<LevelChunk> generate(ServerLevel dross, BlockPos site)
    {
        return generate(dross, site, SITE_CHUNKS);
    }

    /** Generates (or loads) {@code size} x {@code size} chunks centred on the site's chunk, fully. Server thread. */
    static List<LevelChunk> generate(ServerLevel dross, BlockPos site, int size)
    {
        int half = size / 2;
        int cx = site.getX() >> 4;
        int cz = site.getZ() >> 4;
        List<LevelChunk> chunks = new ArrayList<>();
        for (int dz = -half; dz <= half; dz++)
        {
            for (int dx = -half; dx <= half; dx++)
            {
                chunks.add(dross.getChunk(cx + dx, cz + dz));
            }
        }
        return chunks;
    }

    private static void checkSite(ServerLevel dross, TerrainProbe probe, ResourceKey<Biome> biome, BlockPos site, List<LevelChunk> chunks,
                                  double msPerChunk, List<String> problems)
    {
        String name = DrossBiomes.name(biome);
        ResourceKey<Biome> middle = dross.getBiome(site.atY(80)).unwrapKey().orElse(null);
        if (!biome.equals(middle))
        {
            problems.add(name + " site at " + site.getX() + ", " + site.getZ() + " is " + middle);
        }
        int minY = dross.getMinBuildHeight();
        Map<String, Integer> tops = new HashMap<>();
        int columns = 0;
        int noBedrock = 0;
        int highBedrock = 0;
        int air = 0;
        int badFluid = 0;
        int scarHeat = 0;
        int unhappyPlants = 0;
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        Map<Long, Integer> groundAt = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // A sewer network under Blightwood (dimension/sewer) is the only open space underground: its own blocks (air,
        // water, its pipes standing on the ground) are skipped here; SewersGameTest checks them.
        List<BoundingBox> sewers = Sewers.pieceBoxes(dross, chunks.stream().map(LevelChunk::getPos).toList());
        List<BoundingBox> pipes = Sewers.pipeBoxes(dross, chunks.stream().map(LevelChunk::getPos).toList());
        int sewerBlocks = 0;
        for (LevelChunk chunk : chunks)
        {
            int bx = chunk.getPos().getMinBlockX();
            int bz = chunk.getPos().getMinBlockZ();
            for (int x = bx; x < bx + 16; x++)
            {
                for (int z = bz; z < bz + 16; z++)
                {
                    if (Sewers.columnInAny(pipes, x, z))
                    {
                        continue; // a sewer pipe stands here
                    }
                    columns++;
                    if (!chunk.getBlockState(pos.set(x, minY, z)).is(Blocks.BEDROCK))
                    {
                        noBedrock++;
                    }
                    for (int y = minY + 5; y < minY + 10; y++)
                    {
                        if (chunk.getBlockState(pos.set(x, y, z)).is(Blocks.BEDROCK))
                        {
                            highBedrock++;
                        }
                    }
                    int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                    // the real ground (the heightmaps stop at tree crowns, giant mushrooms and fungi)
                    int groundY = groundTop(chunk, x, z, surface, minY);
                    // the land's top as the noise made it (what holes in the terrain are measured from)
                    int naturalTop = probe.baseHeight(x, z) - 1;
                    for (int y = surface; y >= minY; y--)
                    {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        if (Sewers.inAny(sewers, x, y, z))
                        {
                            sewerBlocks++;
                            continue;
                        }
                        if (y < naturalTop - AIR_DEPTH && state.isAir())
                        {
                            air++;
                        }
                        FluidState fluid = state.getFluidState();
                        if (!fluid.isEmpty())
                        {
                            if (!fluid.isSource() || (fluid.is(FluidTags.LAVA) && !enclosed(dross, pos.immutable())))
                            {
                                badFluid++;
                            }
                            if (fluid.is(FluidTags.LAVA) && biome.equals(DrossBiomes.NETHER_SCAR))
                            {
                                scarHeat++;
                            }
                        }
                        if (biome.equals(DrossBiomes.NETHER_SCAR) && state.is(BlockTags.FIRE))
                        {
                            scarHeat++;
                        }
                        if ((state.getBlock() instanceof BushBlock || state.getBlock() instanceof VineBlock || state.getBlock() instanceof MultifaceBlock
                                || state.getBlock() instanceof SnowLayerBlock) && !state.canSurvive(dross, pos))
                        {
                            unhappyPlants++;
                        }
                    }
                    groundAt.put(BlockPos.asLong(x, 0, z), groundY);
                    BlockState ground = chunk.getBlockState(pos.set(x, groundY, z));
                    tops.merge(ForgeRegistries.BLOCKS.getKey(ground.getBlock()).toString(), 1, Integer::sum);
                    lowest = Math.min(lowest, groundY);
                    highest = Math.max(highest, groundY);
                }
            }
        }
        Ground want = GROUND.get(biome);
        int own = 0;
        int cover = 0;
        int signature = 0;
        for (Map.Entry<String, Integer> e : tops.entrySet())
        {
            if (want.allowed().contains(e.getKey()))
            {
                own += e.getValue();
            }
            if (want.cover().contains(e.getKey()))
            {
                cover += e.getValue();
            }
            if (want.signature().contains(e.getKey()))
            {
                signature += e.getValue();
            }
        }
        String where = name + " site at " + site.getX() + ", " + site.getZ();
        if (own < OWN_GROUND * columns)
        {
            problems.add(String.format(Locale.ROOT, "%s: only %.1f%% of the top blocks are its own (%s)", where, 100.0D * own / columns, tops));
        }
        if (cover < want.coverShare() * columns)
        {
            problems.add(String.format(Locale.ROOT, "%s: its ground cover covers only %.1f%% (the rest is bare rock: %s)", where, 100.0D * cover / columns, tops));
        }
        if (signature < want.signatureShare() * columns)
        {
            problems.add(String.format(Locale.ROOT, "%s: its own %s covers only %.1f%% (%s)", where, want.signature(), 100.0D * signature / columns, tops));
        }
        if (biome.equals(DrossBiomes.NETHER_SCAR))
        {
            checkScarCracks(where, tops, columns, problems);
        }
        if (noBedrock > 0 || highBedrock > 0)
        {
            problems.add(where + ": " + noBedrock + " columns without bedrock at the floor, " + highBedrock + " bedrock blocks 5+ above it");
        }
        if (air > 0)
        {
            problems.add(where + ": " + air + " air blocks more than " + AIR_DEPTH + " under the ground");
        }
        if (badFluid > 0 || scarHeat > 0 || unhappyPlants > 0)
        {
            problems.add(where + ": " + badFluid + " flowing or open lava fluid blocks, " + scarHeat + " lava/fire in the Scar, " + unhappyPlants
                    + " plants that couldn't stay");
        }
        // How much of the site is steep (a column at least 2 above a neighbour: the rock_faces feature makes those bare rock)
        int steep = 0;
        int inner = 0;
        for (Map.Entry<Long, Integer> e : groundAt.entrySet())
        {
            BlockPos at = BlockPos.of(e.getKey());
            Integer n = groundAt.get(BlockPos.asLong(at.getX(), 0, at.getZ() - 1));
            Integer s = groundAt.get(BlockPos.asLong(at.getX(), 0, at.getZ() + 1));
            Integer w = groundAt.get(BlockPos.asLong(at.getX() - 1, 0, at.getZ()));
            Integer east = groundAt.get(BlockPos.asLong(at.getX() + 1, 0, at.getZ()));
            if (n == null || s == null || w == null || east == null)
            {
                continue;
            }
            inner++;
            if (e.getValue() - Math.min(Math.min(n, s), Math.min(w, east)) >= 2)
            {
                steep++;
            }
        }
        LOGGER.info("[terrain chunks] {} ({} chunks, {} ms per chunk): ground y {}..{}; steep (bare rock) {}% of the columns; top blocks {}{}",
                where, chunks.size(), String.format(Locale.ROOT, "%.1f", msPerChunk), lowest, highest,
                String.format(Locale.ROOT, "%.1f", inner == 0 ? 0.0D : 100.0D * steep / inner), sorted(tops, columns),
                sewers.isEmpty() ? "" : "; a sewer network under it (" + sewerBlocks + " of its blocks skipped, " + pipes.size() + " pipe(s))");
    }

    /**
     * The Nether Scar's cracks (see SCAR_CRACKED_MIN): its crack ground (Scarred Netherrack and plain netherrack on top: what
     * the soul and nylium patches leave) is there, and is a mix of the two.
     */
    private static void checkScarCracks(String where, Map<String, Integer> tops, int columns, List<String> problems)
    {
        int cracked = tops.getOrDefault("dross:scarred_netherrack", 0);
        int plain = tops.getOrDefault("minecraft:netherrack", 0);
        int ground = cracked + plain;
        if (ground < SCAR_CRACK_GROUND_MIN * columns)
        {
            problems.add(String.format(Locale.ROOT, "%s: only %.1f%% of it is netherrack or Scarred Netherrack, the soul and nylium patches cover the rest (%s)",
                    where, 100.0D * ground / columns, tops));
            return;
        }
        double share = cracked / (double) ground;
        if (share < SCAR_CRACKED_MIN)
        {
            problems.add(String.format(Locale.ROOT, "%s: only %.1f%% of its netherrack ground is Scarred Netherrack, so it looks like plain netherrack (%s)",
                    where, 100.0D * share, tops));
        }
        else if (share > SCAR_CRACKED_MAX)
        {
            problems.add(String.format(Locale.ROOT, "%s: %.1f%% of its netherrack ground is Scarred Netherrack, so the cracks aren't mixed with plain netherrack (%s)",
                    where, 100.0D * share, tops));
        }
        LOGGER.info("[terrain chunks] {}: its crack ground (netherrack and Scarred Netherrack, {}% of the site) is {}% cracked (allowed {}-{}%)",
                where, String.format(Locale.ROOT, "%.1f", 100.0D * ground / columns), String.format(Locale.ROOT, "%.1f", 100.0D * share),
                Math.round(100 * SCAR_CRACKED_MIN), Math.round(100 * SCAR_CRACKED_MAX));
    }

    /**
     * The top of the ground in a column: the highest block that isn't air, a fluid, a plant, a carpet, leaves, a log (trunks,
     * fallen logs and fungus stems), a giant mushroom or a huge fungus's hat (wart blocks and shroomlights).
     */
    static int groundTop(LevelChunk chunk, int x, int z, int surface, int minY)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = surface; y > minY; y--)
        {
            BlockState state = chunk.getBlockState(pos.set(x, y, z));
            if (isAboveGround(state) || state.getCollisionShape(chunk, pos).isEmpty())
            {
                continue;
            }
            return y;
        }
        return minY;
    }

    /** Things that stand on (or hang over) the ground rather than being it: see {@link #groundTop}. */
    static boolean isAboveGround(BlockState state)
    {
        return state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS)
                || state.getBlock() instanceof HugeMushroomBlock || state.getBlock() instanceof CarpetBlock || state.is(BlockTags.WART_BLOCKS)
                || state.is(Blocks.SHROOMLIGHT);
    }

    /** True if lava here has something solid (or more lava) on every side and below. */
    private static boolean enclosed(ServerLevel level, BlockPos pos)
    {
        for (Direction d : Direction.values())
        {
            if (d == Direction.UP)
            {
                continue;
            }
            BlockState next = level.getBlockState(pos.relative(d));
            if (!(next.getFluidState().is(FluidTags.LAVA) || next.isSolid()))
            {
                return false;
            }
        }
        return true;
    }

    private static String sorted(Map<String, Integer> counts, int total)
    {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> b.getValue() - a.getValue());
        StringBuilder s = new StringBuilder();
        for (Map.Entry<String, Integer> e : list)
        {
            if (s.length() > 0)
            {
                s.append(", ");
            }
            s.append(e.getKey().replace("minecraft:", "")).append(String.format(Locale.ROOT, " %.1f%%", 100.0D * e.getValue() / total));
        }
        return s.toString();
    }

}
