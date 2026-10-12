package com.reg21meme.dross.dimension;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
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
 *       least 60%, and its own signature block is there (at least 15%; 40% Cragstone or snow in the Gravecrags);</li>
 *   <li>every fluid is a still source, lava has something solid (or lava) on every side and below, and the Nether
 *       Scar has no lava or fire; every plant, vine, lichen and snow layer could stay where it is.</li>
 * </ul>
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
            DrossBiomes.BLIGHTWOOD, new Ground(Set.of("dross:sporeloam", "minecraft:mycelium", "minecraft:coarse_dirt", "dross:duskstone",
                    "minecraft:mud"),
                    Set.of("dross:sporeloam", "minecraft:mycelium", "minecraft:coarse_dirt"), 0.60D, Set.of("dross:sporeloam"), 0.15D),
            // (ice: the frozen ponds)
            DrossBiomes.RIMEFALL, new Ground(Set.of("dross:hoarfrost", "minecraft:snow_block", "minecraft:packed_ice", "dross:duskstone",
                    "minecraft:ice"),
                    Set.of("dross:hoarfrost", "minecraft:snow_block", "minecraft:packed_ice"), 0.60D, Set.of("dross:hoarfrost"), 0.15D),
            DrossBiomes.NETHER_SCAR, new Ground(Set.of("dross:scarred_netherrack", "minecraft:crimson_nylium", "minecraft:warped_nylium",
                    "minecraft:soul_soil", "minecraft:soul_sand", "minecraft:netherrack", "dross:scar_vent"),
                    Set.of("dross:scarred_netherrack", "minecraft:crimson_nylium", "minecraft:warped_nylium", "minecraft:soul_soil",
                            "minecraft:soul_sand"), 0.60D, Set.of("dross:scarred_netherrack"), 0.15D),
            DrossBiomes.GRAVECRAGS, new Ground(Set.of("dross:cragstone", "minecraft:snow_block", "minecraft:gravel", "minecraft:moss_block"),
                    Set.of("dross:cragstone", "minecraft:snow_block", "minecraft:gravel", "minecraft:moss_block"), 0.60D,
                    Set.of("dross:cragstone", "minecraft:snow_block"), 0.40D));

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
        for (LevelChunk chunk : chunks)
        {
            int bx = chunk.getPos().getMinBlockX();
            int bz = chunk.getPos().getMinBlockZ();
            for (int x = bx; x < bx + 16; x++)
            {
                for (int z = bz; z < bz + 16; z++)
                {
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
        LOGGER.info("[terrain chunks] {} ({} chunks, {} ms per chunk): ground y {}..{}; steep (bare rock) {}% of the columns; top blocks {}",
                where, chunks.size(), String.format(Locale.ROOT, "%.1f", msPerChunk), lowest, highest,
                String.format(Locale.ROOT, "%.1f", inner == 0 ? 0.0D : 100.0D * steep / inner), sorted(tops, columns));
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
