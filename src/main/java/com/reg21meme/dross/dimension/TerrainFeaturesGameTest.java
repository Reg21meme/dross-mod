package com.reg21meme.dross.dimension;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.sewer.Sewers;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSiteGameTest;
import com.reg21meme.dross.dimension.worldgen.PondFeature;
import com.reg21meme.dross.dimension.worldgen.TidyUpFeature;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless check (GameTest) of what grows and lies on the Dross's land, and its ores, in real generated chunks. For each
 * of the five biomes it finds a sample site in this world (like {@link TerrainChunksGameTest}, but round
 * {@value #SITE_X}, {@value #SITE_Z}, so these chunks are brand new and their generation time counts every feature),
 * generates {@value #SITE_CHUNKS} x {@value #SITE_CHUNKS} chunks there, and checks:
 * <ul>
 *   <li>each biome's own features are there in sensible numbers ({@link #markers}): silverwood logs, leaves and Stormvine
 *       in the Silverwood Forest; lava, basalt spires and charred logs in the Cinder Wastes; giant blightcaps in the
 *       Blightwood; frostbitten logs and snow in Rimefall; Storm Lichen and Cragstone in the Gravecrags;</li>
 *   <li>none of the removed Nether Scar's blocks ({@link #netherScarBlocks}: its four own blocks, kept as building blocks,
 *       and the Nether ground and plants it grew) generate anywhere;</li>
 *   <li>everything above the ground could stay where it is (plants, vines, lichen, snow and ash layers, icicles,
 *       carpets: {@code canSurvive});</li>
 *   <li>every fluid is a still source; every lava block has something solid (or lava) on every side and below; no fire
 *       anywhere;</li>
 *   <li>no tree's natural leaves are too far from a log to stay (distance 7: they'd decay);</li>
 *   <li>nothing floats: every tree, fallen log and giant mushroom (each connected group of logs, stems and mushroom
 *       blocks, with at least one log or mushroom block) rests on the ground or leans on rock somewhere (a pond or other
 *       feature that cut through one would leave it hanging; a branch that meets an ice spike may lean on it). Groups
 *       reaching the site's edge are skipped (their ground may be outside it);</li>
 *   <li>the ores: every ore block lies within its height range (plus a vein's reach; {@link #ORES}), and all four ores
 *       are found across the sites.</li>
 * </ul>
 * Ponds and lava pools are rare (a site may have none), so {@link #pondsHoldTheirFluidAndNeverCutTrees} also places them
 * on prepared ground: a dark pond and a lava pool hold their fluid, and a pond never forms where a tree trunk stands.
 *
 * <p>It logs ("[terrain features]") each site's generation time per chunk (and how much of it the tidy-up,
 * {@code dross:tidy_up}, took), its feature counts, its ponds and lava pools (and how the pond feature's tries went), how
 * much of its floor is lit by glowing blocks (hostile mobs only spawn in block light 0, so glowing plants and lava make
 * safe spots), and the ores found.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class TerrainFeaturesGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The sample sites are looked for round here: away from TerrainChunksGameTest's, so these chunks are new. */
    static final int SITE_X = -6000;
    static final int SITE_Z = 6000;
    /** A site is this many chunks square (112 blocks): room for the rarer features, like the lava pools. */
    static final int SITE_CHUNKS = 7;
    /** A site's surroundings this far every way (blocks) must all be its biome (the site reaches 56 from its middle). */
    static final int SITE_CLEAR = 64;
    /** An ore block may lie this many blocks beyond its height range (a vein spreads a few blocks round where it starts). */
    private static final int ORE_REACH = 8;

    /**
     * A kind of feature block the site should have.
     *
     * @param label  its name in the log
     * @param blocks the blocks that count
     * @param where  which ones count (all of them, or only those above the land's natural ground)
     * @param least  the site must have at least this many (0: only counted and logged)
     */
    record Marker(String label, Set<Block> blocks, Where where, int least) {}

    /** Where a marker's blocks are counted. */
    enum Where
    {
        /** Anywhere in the column. */
        ANYWHERE,
        /** Only on or above the natural ground's top (the land as the noise made it, before any feature): boulders, spires... */
        ABOVE_NATURAL_GROUND
    }

    /**
     * An ore and its heights (keep in step with ORES in tools/terrain/worldgen.py).
     *
     * @param highestGravecrags the highest y in the Gravecrags, which have an extra placement (Stormite)
     */
    record Ore(String label, Block block, int lowest, int highest, int highestGravecrags) {}

    /** The random seed of the prepared-ground pond tests (any works: on open, flat ground a pond always forms). */
    private static final long POND_SEED = 20261012L;
    /** The prepared ground's top (test coordinates; the arena's floor is y 1): ponds dig up to 4 deep. */
    private static final int POND_GROUND_TOP = 6;
    /** How high the space above the prepared ground is cleared between the tries. */
    private static final int POND_CLEAR_ABOVE = 10;

    private TerrainFeaturesGameTest() {}

    /** The markers of each biome (blocks of the registry, so built when the test runs). */
    static Map<ResourceKey<Biome>, List<Marker>> markers()
    {
        Map<ResourceKey<Biome>, List<Marker>> m = new LinkedHashMap<>();
        m.put(DrossBiomes.SILVERWOOD_FOREST, List.of(
                new Marker("silverwood logs", Set.of(ModBlocks.SILVERWOOD_LOG.get()), Where.ANYWHERE, 1),
                new Marker("silverwood leaves", Set.of(ModBlocks.SILVERWOOD_LEAVES.get()), Where.ANYWHERE, 1),
                new Marker("Stormvine", Set.of(ModBlocks.STORMVINE.get()), Where.ANYWHERE, 1),
                new Marker("Glimmer Ferns", Set.of(ModBlocks.GLIMMER_FERN.get()), Where.ANYWHERE, 1),
                new Marker("Wispblooms", Set.of(ModBlocks.WISPBLOOM.get()), Where.ANYWHERE, 0),
                new Marker("boulder Duskstone", Set.of(ModBlocks.DUSKSTONE.get()), Where.ABOVE_NATURAL_GROUND, 0),
                new Marker("moss carpets", Set.of(Blocks.MOSS_CARPET), Where.ANYWHERE, 0),
                new Marker("pond water", Set.of(Blocks.WATER), Where.ANYWHERE, 0)));
        m.put(DrossBiomes.CINDER_WASTES, List.of(
                new Marker("lava", Set.of(Blocks.LAVA), Where.ANYWHERE, 0),
                new Marker("basalt spires", Set.of(Blocks.BASALT), Where.ABOVE_NATURAL_GROUND, 1),
                new Marker("charred logs", Set.of(ModBlocks.CHARRED_LOG.get()), Where.ANYWHERE, 1),
                new Marker("Emberweed", Set.of(ModBlocks.EMBERWEED.get()), Where.ANYWHERE, 1),
                new Marker("Ash Layers", Set.of(ModBlocks.ASH_LAYER.get()), Where.ANYWHERE, 1),
                new Marker("Cinder Vents", Set.of(ModBlocks.CINDER_VENT.get()), Where.ANYWHERE, 0)));
        m.put(DrossBiomes.BLIGHTWOOD, List.of(
                new Marker("blightcap caps", Set.of(ModBlocks.BLIGHTCAP_BLOCK.get(), ModBlocks.ROTTING_BLIGHTCAP.get()), Where.ANYWHERE, 1),
                new Marker("blightcap stems", Set.of(ModBlocks.BLIGHTCAP_STEM.get()), Where.ANYWHERE, 1),
                new Marker("Sporepods", Set.of(ModBlocks.SPOREPOD.get()), Where.ANYWHERE, 1),
                new Marker("Gloomcaps", Set.of(ModBlocks.GLOOMCAP.get()), Where.ANYWHERE, 0),
                new Marker("fallen logs", Set.of(ModBlocks.SILVERWOOD_LOG.get()), Where.ANYWHERE, 0),
                new Marker("pond water", Set.of(Blocks.WATER), Where.ANYWHERE, 0)));
        m.put(DrossBiomes.RIMEFALL, List.of(
                new Marker("frostbitten logs", Set.of(ModBlocks.FROSTBITTEN_LOG.get()), Where.ANYWHERE, 1),
                new Marker("snow layers", Set.of(Blocks.SNOW), Where.ANYWHERE, 1),
                new Marker("packed ice", Set.of(Blocks.PACKED_ICE), Where.ANYWHERE, 1),
                new Marker("ice spikes and boulders", Set.of(Blocks.PACKED_ICE), Where.ABOVE_NATURAL_GROUND, 0),
                new Marker("Icicles", Set.of(ModBlocks.ICICLE.get()), Where.ANYWHERE, 0),
                new Marker("Frostblooms", Set.of(ModBlocks.FROSTBLOOM.get()), Where.ANYWHERE, 0),
                new Marker("pond ice", Set.of(Blocks.ICE), Where.ANYWHERE, 0)));
        m.put(DrossBiomes.GRAVECRAGS, List.of(
                new Marker("Storm Lichen", Set.of(ModBlocks.STORM_LICHEN.get()), Where.ANYWHERE, 1),
                new Marker("Cragstone", Set.of(ModBlocks.CRAGSTONE.get()), Where.ANYWHERE, 1),
                new Marker("Crag Thistles", Set.of(ModBlocks.CRAG_THISTLE.get()), Where.ANYWHERE, 0),
                new Marker("boulder Cragstone", Set.of(ModBlocks.CRAGSTONE.get()), Where.ABOVE_NATURAL_GROUND, 0),
                new Marker("silverwood logs", Set.of(ModBlocks.SILVERWOOD_LOG.get()), Where.ANYWHERE, 0),
                new Marker("snow layers", Set.of(Blocks.SNOW), Where.ANYWHERE, 0)));
        return m;
    }

    /**
     * The removed Nether Scar's blocks (the user had the biome removed on October 17, 2026): its four own blocks, kept as
     * building blocks (they stay in the creative tab and can be placed by hand), and the Nether ground and plants it grew.
     * None of them may generate anywhere now.
     */
    static Set<Block> netherScarBlocks()
    {
        return Set.of(ModBlocks.SCARRED_NETHERRACK.get(), ModBlocks.SCAR_ROOTS.get(), ModBlocks.SOULWICK.get(), ModBlocks.SCAR_VENT.get(),
                Blocks.NETHERRACK, Blocks.CRIMSON_NYLIUM, Blocks.WARPED_NYLIUM, Blocks.SOUL_SOIL, Blocks.SOUL_SAND, Blocks.CRIMSON_STEM,
                Blocks.WARPED_STEM, Blocks.NETHER_WART_BLOCK, Blocks.WARPED_WART_BLOCK, Blocks.CRIMSON_ROOTS, Blocks.WARPED_ROOTS,
                Blocks.NETHER_SPROUTS, Blocks.CRIMSON_FUNGUS, Blocks.WARPED_FUNGUS);
    }

    /** The ores and their heights (keep in step with ORES and STORMITE_GRAVECRAGS in tools/terrain/worldgen.py). */
    static List<Ore> ores()
    {
        return List.of(
                new Ore("Duskstone Coal Ore", ModBlocks.DUSKSTONE_COAL_ORE.get(), -32, 160, 160),
                new Ore("Duskstone Gold Ore", ModBlocks.DUSKSTONE_GOLD_ORE.get(), -48, 128, 128),
                new Ore("Moonsilver Ore", ModBlocks.MOONSILVER_ORE.get(), -48, 64, 64),
                new Ore("Stormite Ore", ModBlocks.STORMITE_ORE.get(), -64, 0, 96));
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_terrain_features", timeoutTicks = TerrainSamplingGameTest.BACKSTOP_TICKS)
    public static void featuresPerBiome(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        PondFeature.recordStats();   // the ponds' tries and refusals are only counted for the headless checks
        TerrainProbe probe = TerrainProbe.forWorld(dross);
        CompletableFuture<Map<ResourceKey<Biome>, BlockPos>> sites = CompletableFuture.supplyAsync(
                () -> TerrainChunksGameTest.findSites(probe, SITE_X, SITE_Z, SITE_CLEAR));
        long deadline = Util.getMillis() + TerrainSamplingGameTest.TIMEOUT_MILLIS;
        List<String> problems = new ArrayList<>();
        Map<Block, int[]> oresFound = new HashMap<>();   // block -> {count, lowest y, highest y}
        Map<ResourceKey<Biome>, List<Marker>> markers = markers();
        List<Ore> ores = ores();
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
                });
        for (ResourceKey<Biome> biome : DrossBiomes.ALL)
        {
            sequence = sequence.thenExecute(() -> {
                BlockPos site = sites.join().get(biome);
                if (site == null)
                {
                    problems.add("no sample site for " + DrossBiomes.name(biome) + " near " + SITE_X + ", " + SITE_Z);
                    return;
                }
                long[] tidyBefore = TidyUpFeature.timing();
                long[] pondsBefore = PondFeature.stats();
                long t = Util.getNanos();
                List<LevelChunk> chunks = TerrainChunksGameTest.generate(dross, site, SITE_CHUNKS);
                double ms = (Util.getNanos() - t) / 1.0E6D / chunks.size();
                long[] tidyAfter = TidyUpFeature.timing();
                long tidyRuns = tidyAfter[0] - tidyBefore[0];
                double tidyMs = tidyRuns == 0 ? 0.0D : (tidyAfter[1] - tidyBefore[1]) / 1.0E6D / tidyRuns;
                long[] pondsAfter = PondFeature.stats();
                String pondTries = String.format(Locale.ROOT, "%d tries: %d made, %d on uneven ground, %d would leak, %d with something in the way",
                        pondsAfter[0] - pondsBefore[0], pondsAfter[1] - pondsBefore[1], pondsAfter[2] - pondsBefore[2], pondsAfter[3] - pondsBefore[3],
                        pondsAfter[4] - pondsBefore[4]);
                checkSite(dross, probe, biome, site, chunks, ms, tidyMs, pondTries, markers.get(biome), ores, oresFound, problems);
            });
        }
        sequence.thenExecute(() -> {
            StringBuilder found = new StringBuilder();
            for (Ore ore : ores)
            {
                int[] f = oresFound.get(ore.block());
                if (f == null || f[0] == 0)
                {
                    problems.add("no " + ore.label() + " found in any of the sites");
                    continue;
                }
                found.append(found.length() > 0 ? "; " : "").append(ore.label()).append(' ').append(f[0]).append(" blocks, y ")
                        .append(f[1]).append("..").append(f[2]);
            }
            LOGGER.info("[terrain features] ores in all five sites: {}", found);
            if (!problems.isEmpty())
            {
                throw new GameTestAssertException(problems.size() + " problem(s) with the features: " + String.join("; ",
                        problems.subList(0, Math.min(12, problems.size()))) + (problems.size() > 12 ? " (and " + (problems.size() - 12) + " more)" : ""));
            }
        }).thenSucceed();
    }

    private static void checkSite(ServerLevel dross, TerrainProbe probe, ResourceKey<Biome> biome, BlockPos site, List<LevelChunk> chunks, double msPerChunk,
                                  double tidyMsPerRun, String pondTries, List<Marker> markers, List<Ore> ores, Map<Block, int[]> oresFound,
                                  List<String> problems)
    {
        String where = DrossBiomes.name(biome) + " site at " + site.getX() + ", " + site.getZ();
        boolean crags = biome.equals(DrossBiomes.GRAVECRAGS);
        Set<Block> scarBlocks = netherScarBlocks();
        int scarFound = 0;
        String firstScar = null;
        int minY = dross.getMinBuildHeight();
        int[] markerCounts = new int[markers.size()];
        Map<Block, Integer> oreCounts = new LinkedHashMap<>();
        List<String> oreProblems = new ArrayList<>();
        int unhappy = 0;
        String firstUnhappy = null;
        int flowing = 0;
        int openLava = 0;
        int fire = 0;
        int decaying = 0;
        int floor = 0;
        int lit = 0;
        Set<Long> structure = new HashSet<>();    // logs, stems, mushroom and wart blocks: what must stand on the ground
        Set<Long> water = new HashSet<>();        // pond water and pond ice
        Set<Long> lava = new HashSet<>();
        int minX = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        // a sewer network's own blocks (its waterfalls flow) are SewersGameTest's to check
        List<BoundingBox> sewers = Sewers.pieceBoxes(dross, chunks.stream().map(LevelChunk::getPos).toList());
        for (LevelChunk chunk : chunks)
        {
            int bx = chunk.getPos().getMinBlockX();
            int bz = chunk.getPos().getMinBlockZ();
            minX = Math.min(minX, bx);
            minZ = Math.min(minZ, bz);
            maxX = Math.max(maxX, bx + 15);
            maxZ = Math.max(maxZ, bz + 15);
            for (int x = bx; x < bx + 16; x++)
            {
                for (int z = bz; z < bz + 16; z++)
                {
                    int surface = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                    int groundY = TerrainChunksGameTest.groundTop(chunk, x, z, surface, minY);
                    int natural = probe.baseHeight(x, z);   // the first air above the land as the noise made it
                    for (int y = surface; y >= minY; y--)
                    {
                        BlockState state = chunk.getBlockState(pos.set(x, y, z));
                        if (state.isAir())
                        {
                            continue;
                        }
                        Block block = state.getBlock();
                        if (scarBlocks.contains(block))
                        {
                            scarFound++;
                            if (firstScar == null)
                            {
                                firstScar = state + " at " + pos.toShortString();
                            }
                        }
                        if (isStructure(state))
                        {
                            structure.add(BlockPos.asLong(x, y, z));
                        }
                        else if (y >= natural - 6 && (state.is(Blocks.WATER) || state.is(Blocks.ICE)))
                        {
                            water.add(BlockPos.asLong(x, y, z));
                        }
                        else if (state.is(Blocks.LAVA))
                        {
                            lava.add(BlockPos.asLong(x, y, z));
                        }
                        for (int i = 0; i < markers.size(); i++)
                        {
                            Marker marker = markers.get(i);
                            if (marker.blocks().contains(block) && (marker.where() == Where.ANYWHERE || y >= natural))
                            {
                                markerCounts[i]++;
                            }
                        }
                        // everything on or above the ground could stay where it is (also what's sealed inside a boulder or
                        // an ice spike standing on the land's natural top)
                        if (y >= Math.min(groundY, natural) && !state.canSurvive(dross, pos))
                        {
                            unhappy++;
                            if (firstUnhappy == null)
                            {
                                firstUnhappy = state + " at " + pos.toShortString();
                            }
                        }
                        FluidState fluid = state.getFluidState();
                        if (!fluid.isEmpty() && !Sewers.inAny(sewers, x, y, z))
                        {
                            if (!fluid.isSource())
                            {
                                flowing++;
                            }
                            if (fluid.is(FluidTags.LAVA) && !enclosed(dross, pos))
                            {
                                openLava++;
                            }
                        }
                        if (state.is(BlockTags.FIRE))
                        {
                            fire++;
                        }
                        if (block instanceof LeavesBlock && !state.getValue(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE)
                        {
                            decaying++;
                        }
                        for (Ore ore : ores)
                        {
                            if (block == ore.block())
                            {
                                oreCounts.merge(block, 1, Integer::sum);
                                int[] f = oresFound.computeIfAbsent(block, b -> new int[] {0, Integer.MAX_VALUE, Integer.MIN_VALUE});
                                f[0]++;
                                f[1] = Math.min(f[1], y);
                                f[2] = Math.max(f[2], y);
                                int highest = crags ? ore.highestGravecrags() : ore.highest();
                                if ((y < ore.lowest() - ORE_REACH || y > highest + ORE_REACH) && oreProblems.size() < 3)
                                {
                                    oreProblems.add(ore.label() + " at y " + y + " (its range is " + ore.lowest() + ".." + highest + ")");
                                }
                            }
                        }
                    }
                    // the floor where a monster could stand: the spot above the ground, unless it's under water or lava
                    BlockPos spot = new BlockPos(x, groundY + 1, z);
                    if (dross.getBlockState(spot).getFluidState().isEmpty())
                    {
                        floor++;
                        if (dross.getBrightness(LightLayer.BLOCK, spot) > 0)
                        {
                            lit++;
                        }
                    }
                }
            }
        }
        StringBuilder counts = new StringBuilder();
        for (int i = 0; i < markers.size(); i++)
        {
            Marker marker = markers.get(i);
            counts.append(i > 0 ? ", " : "").append(marker.label()).append(' ').append(markerCounts[i]);
            if (markerCounts[i] < marker.least())
            {
                problems.add(where + ": only " + markerCounts[i] + " " + marker.label() + " (at least " + marker.least() + " expected)");
            }
        }
        if (unhappy > 0)
        {
            problems.add(where + ": " + unhappy + " block(s) that couldn't stay where they are, the first " + firstUnhappy);
        }
        if (flowing > 0 || openLava > 0 || fire > 0)
        {
            problems.add(where + ": " + flowing + " flowing fluid blocks, " + openLava + " lava blocks open at a side or below, " + fire
                    + " fire blocks");
        }
        if (scarFound > 0)
        {
            problems.add(where + ": " + scarFound + " block(s) of the removed Nether Scar generated, the first " + firstScar);
        }
        if (decaying > 0)
        {
            problems.add(where + ": " + decaying + " natural leaves too far from a log (they'd decay)");
        }
        List<BlockPos> floating = floating(dross, structure, minX, minZ, maxX, maxZ);
        if (!floating.isEmpty())
        {
            problems.add(where + ": " + floating.size() + " tree(s), fallen log(s), mushroom(s) or fungi hanging in the air, the first at "
                    + floating.get(0).toShortString());
        }
        for (String p : oreProblems)
        {
            problems.add(where + ": " + p);
        }
        StringBuilder oreText = new StringBuilder();
        for (Ore ore : ores)
        {
            oreText.append(oreText.length() > 0 ? ", " : "").append(ore.label()).append(' ').append(oreCounts.getOrDefault(ore.block(), 0));
        }
        List<Integer> ponds = groups(water);
        List<Integer> pools = groups(lava);
        LOGGER.info("[terrain features] {} ({} chunks, {} ms per chunk, of which the tidy-up {} ms): {}; {} pond(s) {}, {} lava pool(s) {}"
                        + " (pond feature: {}); floor lit by glowing blocks {}% ({} of {} spots); ores: {}",
                where, chunks.size(), String.format(Locale.ROOT, "%.1f", msPerChunk), String.format(Locale.ROOT, "%.1f", tidyMsPerRun), counts,
                ponds.size(), ponds, pools.size(), pools, pondTries,
                String.format(Locale.ROOT, "%.1f", floor == 0 ? 0.0D : 100.0D * lit / floor), lit, floor, oreText);
    }

    /** What must stand on the ground: logs (trunks, branches, fallen logs), giant mushroom blocks (and, should any appear, fungus hats). */
    private static boolean isStructure(BlockState state)
    {
        return state.is(BlockTags.LOGS) || state.getBlock() instanceof HugeMushroomBlock || state.is(BlockTags.WART_BLOCKS)
                || state.is(Blocks.SHROOMLIGHT);
    }

    /**
     * The groups of structure blocks (touching, corners too) with at least one log or mushroom block that hang in the
     * air: no block of the group has a solid block that isn't part of a structure right under it or beside it. Groups
     * reaching the site's edge are left out. Returns one block of each such group.
     */
    private static List<BlockPos> floating(ServerLevel dross, Set<Long> structure, int minX, int minZ, int maxX, int maxZ)
    {
        List<BlockPos> out = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long start : structure)
        {
            if (!seen.add(start))
            {
                continue;
            }
            boolean grounded = false;
            boolean atEdge = false;
            boolean woody = false;
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty())
            {
                BlockPos p = BlockPos.of(queue.poll());
                if (p.getX() <= minX || p.getX() >= maxX || p.getZ() <= minZ || p.getZ() >= maxZ)
                {
                    atEdge = true;
                }
                BlockState here = dross.getBlockState(p);
                if (here.is(BlockTags.LOGS) || here.getBlock() instanceof HugeMushroomBlock)
                {
                    woody = true;
                }
                for (Direction d : Direction.values())
                {
                    BlockPos next = p.relative(d);
                    if (d != Direction.UP && !grounded && !structure.contains(next.asLong()))
                    {
                        BlockState other = dross.getBlockState(next);
                        if (other.isSolid() && !other.is(BlockTags.LEAVES))
                        {
                            grounded = true;
                        }
                    }
                }
                for (int dx = -1; dx <= 1; dx++)
                {
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        for (int dz = -1; dz <= 1; dz++)
                        {
                            long next = BlockPos.asLong(p.getX() + dx, p.getY() + dy, p.getZ() + dz);
                            if (structure.contains(next) && seen.add(next))
                            {
                                queue.add(next);
                            }
                        }
                    }
                }
            }
            if (!grounded && !atEdge && woody)
            {
                out.add(BlockPos.of(start));
            }
        }
        return out;
    }

    /** The sizes (blocks) of the groups of touching blocks (sides only) in the set, biggest first. */
    private static List<Integer> groups(Set<Long> blocks)
    {
        List<Integer> sizes = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (long start : blocks)
        {
            if (!seen.add(start))
            {
                continue;
            }
            int size = 0;
            ArrayDeque<Long> queue = new ArrayDeque<>();
            queue.add(start);
            while (!queue.isEmpty())
            {
                BlockPos p = BlockPos.of(queue.poll());
                size++;
                for (Direction d : Direction.values())
                {
                    long next = p.relative(d).asLong();
                    if (blocks.contains(next) && seen.add(next))
                    {
                        queue.add(next);
                    }
                }
            }
            sizes.add(size);
        }
        sizes.sort((a, b) -> b - a);
        return sizes;
    }

    /** True if lava here has something solid (or more lava) on every side and below. */
    private static boolean enclosed(ServerLevel level, BlockPos pos)
    {
        return held(level, pos, FluidTags.LAVA);
    }

    /** True if the fluid here has something solid (or more of the same fluid) on every side and below. */
    private static boolean held(ServerLevel level, BlockPos pos, TagKey<Fluid> fluid)
    {
        for (Direction d : Direction.values())
        {
            if (d == Direction.UP)
            {
                continue;
            }
            BlockState next = level.getBlockState(pos.relative(d));
            if (!(next.getFluidState().is(fluid) || next.isSolid()))
            {
                return false;
            }
        }
        return true;
    }

    // ---------------------------------------------------------------------------------------------
    // Ponds and lava pools on prepared ground
    // ---------------------------------------------------------------------------------------------

    /**
     * The Silverwood Forest's pond and the Cinder Wastes' lava pool (configured features {@code dross:pond_silverwood}
     * and {@code dross:lava_pool}) placed on flat prepared ground in the test arena: each forms, every fluid block is a
     * still source held in on every side and below, and the pond gets its mud or clay shore. Its top is water, or ice
     * exactly where the arena's Overworld biome freezes water (an old world's arena can stand in a snowy biome; a pond
     * freezes over there as it forms). Then the same pond (same shape) with a tree trunk standing where its surface would
     * be: it doesn't form at all (no water, no ice), and the trunk stays whole.
     */
    @GameTest(template = TerrainBlocksGameTest.ARENA, batch = "dross_terrain_ponds")
    public static void pondsHoldTheirFluidAndNeverCutTrees(GameTestHelper helper)
    {
        PondFeature.recordStats();   // so a pond that doesn't form says why
        ServerLevel level = helper.getLevel();
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        Registry<ConfiguredFeature<?, ?>> features = level.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        ConfiguredFeature<?, ?> pond = features.get(new ResourceLocation(Dross.MODID, "pond_silverwood"));
        ConfiguredFeature<?, ?> lavaPool = features.get(new ResourceLocation(Dross.MODID, "lava_pool"));
        if (pond == null || lavaPool == null)
        {
            throw new GameTestAssertException("the configured features dross:pond_silverwood and dross:lava_pool should exist");
        }
        BlockPos corner = helper.absolutePos(new BlockPos(1, POND_GROUND_TOP + 1, 1));   // the air above the ground

        // 1. A dark pond on flat Silvermoss.
        prepareGround(helper, ModBlocks.SILVERMOSS.get(), Blocks.DIRT);
        if (!pond.place(level, generator, RandomSource.create(POND_SEED), corner))
        {
            throw new GameTestAssertException("a pond should form on open, flat ground (it didn't: " + PondFeature.lastRefusal() + ")");
        }
        List<BlockPos> water = blocksOf(helper, Blocks.WATER);
        List<BlockPos> ice = blocksOf(helper, Blocks.ICE);
        int shore = blocksOf(helper, Blocks.MUD).size() + blocksOf(helper, Blocks.CLAY).size();
        checkHeld(helper, water, FluidTags.WATER, "pond water");
        if (shore == 0)
        {
            throw new GameTestAssertException("the pond should have a mud or clay shore");
        }
        // Its surface (the ground's top layer) is water, or ice where the arena's biome freezes water: the arena stands in
        // whatever Overworld biome the test world has there (plains in a new test world, but an old world's arena can be in
        // a snowy taiga), and a pond freezes over as it forms in the cold (PondFeature, step 4). Ice only ever on its top.
        String arenaBiome = level.getBiome(helper.absolutePos(new BlockPos(1, POND_GROUND_TOP, 1))).unwrapKey()
                .map(k -> k.location().toString()).orElse("an unnamed biome");
        List<BlockPos> surface = new ArrayList<>();
        for (BlockPos w : water)
        {
            if (w.getY() == POND_GROUND_TOP)
            {
                surface.add(w);
            }
        }
        for (BlockPos i : ice)
        {
            if (i.getY() != POND_GROUND_TOP)
            {
                throw new GameTestAssertException("the pond has ice at " + i + ", below its top (" + arenaBiome + ")");
            }
            surface.add(i);
        }
        for (BlockPos s : surface)
        {
            boolean frozen = helper.getBlockState(s).is(Blocks.ICE);
            if (frozen != freezes(level, helper.absolutePos(s)))
            {
                throw new GameTestAssertException("the pond's top at " + s + " is " + (frozen ? "ice" : "water") + ", but the arena's biome ("
                        + arenaBiome + ") " + (frozen ? "doesn't freeze" : "freezes") + " water there");
            }
        }

        // 2. The same pond where a tree trunk stands in its water: it mustn't form.
        BlockPos trunk = null;
        for (BlockPos s : surface)
        {
            if (trunk == null || Math.abs(s.getX() - 8) + Math.abs(s.getZ() - 8) < Math.abs(trunk.getX() - 8) + Math.abs(trunk.getZ() - 8))
            {
                trunk = s.above();   // standing on the ground right where the pond's surface was
            }
        }
        if (trunk == null)
        {
            throw new GameTestAssertException("the pond had no water (or ice) at the ground's top (" + arenaBiome + ")");
        }
        prepareGround(helper, ModBlocks.SILVERMOSS.get(), Blocks.DIRT);
        for (int y = 0; y < 5; y++)
        {
            helper.setBlock(trunk.above(y), ModBlocks.SILVERWOOD_LOG.get());
        }
        if (pond.place(level, generator, RandomSource.create(POND_SEED), corner))
        {
            throw new GameTestAssertException("a pond formed with a tree trunk standing in it (at " + trunk + ")");
        }
        if (!blocksOf(helper, Blocks.WATER).isEmpty() || !blocksOf(helper, Blocks.ICE).isEmpty()
                || blocksOf(helper, ModBlocks.SILVERWOOD_LOG.get()).size() != 5)
        {
            throw new GameTestAssertException("the pond that didn't form still changed something (water or ice placed, or the trunk cut)");
        }

        // 3. A lava pool on flat Ash.
        prepareGround(helper, ModBlocks.ASH.get(), Blocks.BLACKSTONE);
        if (!lavaPool.place(level, generator, RandomSource.create(POND_SEED), corner))
        {
            throw new GameTestAssertException("a lava pool should form on open, flat ground (it didn't: " + PondFeature.lastRefusal() + ")");
        }
        List<BlockPos> lava = blocksOf(helper, Blocks.LAVA);
        checkHeld(helper, lava, FluidTags.LAVA, "lava");
        LOGGER.info("[terrain features] On prepared ground ({}): a pond of {} water blocks{} with {} shore blocks; with a trunk in it, no pond; "
                + "a lava pool of {} lava blocks, all held in", arenaBiome, water.size(),
                ice.isEmpty() ? "" : " under " + ice.size() + " ice (the biome is cold, so its top froze)", shore, lava.size());
        helper.succeed();
    }

    /**
     * True if water would freeze here as a pond forms: what {@code PondFeature} checks ({@code Biome.shouldFreeze} without
     * the edge rule: a biome too cold for rain, and not lit by a block light of 10 or more).
     */
    private static boolean freezes(ServerLevel level, BlockPos pos)
    {
        return !level.getBiome(pos).value().warmEnoughToRain(pos) && level.getBrightness(LightLayer.BLOCK, pos) < 10;
    }

    /** The arena's ground made flat: {@code soil} up to just under {@link #POND_GROUND_TOP}, {@code cover} on top, air above. */
    private static void prepareGround(GameTestHelper helper, Block cover, Block soil)
    {
        int size = TerrainBlocksGameTest.ARENA_SIZE;
        for (int x = 0; x < size; x++)
        {
            for (int z = 0; z < size; z++)
            {
                for (int y = 1; y <= POND_GROUND_TOP + POND_CLEAR_ABOVE; y++)
                {
                    BlockState state = y < POND_GROUND_TOP ? soil.defaultBlockState() : y == POND_GROUND_TOP ? cover.defaultBlockState()
                            : Blocks.AIR.defaultBlockState();
                    helper.getLevel().setBlock(helper.absolutePos(new BlockPos(x, y, z)), state, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** Every block of this kind in the arena's lower part (test coordinates). */
    private static List<BlockPos> blocksOf(GameTestHelper helper, Block block)
    {
        List<BlockPos> out = new ArrayList<>();
        int size = TerrainBlocksGameTest.ARENA_SIZE;
        for (int x = 0; x < size; x++)
        {
            for (int z = 0; z < size; z++)
            {
                for (int y = 1; y <= POND_GROUND_TOP + POND_CLEAR_ABOVE; y++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (helper.getBlockState(pos).is(block))
                    {
                        out.add(pos);
                    }
                }
            }
        }
        return out;
    }

    /** Fails unless there is some of the fluid and every block of it is a still source held in on every side and below. */
    private static void checkHeld(GameTestHelper helper, List<BlockPos> blocks, TagKey<Fluid> fluid, String what)
    {
        if (blocks.isEmpty())
        {
            throw new GameTestAssertException("no " + what + " was placed");
        }
        for (BlockPos pos : blocks)
        {
            BlockPos abs = helper.absolutePos(pos);
            if (!helper.getLevel().getFluidState(abs).isSource() || !held(helper.getLevel(), abs, fluid))
            {
                throw new GameTestAssertException(what + " at " + pos + " isn't a still source held in on every side and below");
            }
        }
    }
}
