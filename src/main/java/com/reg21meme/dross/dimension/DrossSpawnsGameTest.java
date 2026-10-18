package com.reg21meme.dross.dimension;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.WildGolemSpawns;
import com.reg21meme.dross.dimension.sewer.SewerStructure;
import com.reg21meme.dross.dimension.sewer.Sewers;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.PotentialCalculator;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless checks (GameTests) of which mobs spawn in each Dross biome (the spawn lists are in the biome JSON, written by
 * {@code tools/terrain/worldgen.py}: BIOMES, {@code SPAWN_OVERRIDES}). Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}.
 * <ul>
 *   <li>{@link #spawnListsPerBiome}: three biomes (Silverwood Forest, Rimefall, the Gravecrags) have the Dross's base
 *       spawn lists exactly (worldgen.py's {@code BASE_MONSTERS}, {@code BASE_CREATURES}, {@code BASE_AMBIENT}: the plains
 *       monsters minus slimes, creepers and spiders, the plains animals, bats; Silverwood Forest's lists are the reference
 *       the other two must match);
 *       Blightwood keeps its animals and bats, but its monsters are only rats and witches (no zombies, skeletons or endermen:
 *       witches 7% of the mobs spawning there, 1 in 5 of the rats enraged); the sewers
 *       biome under it and the rat cave list only rats (3 in 5 enraged); and the Cinder Wastes list only the Cinder
 *       Colossus's golems (the user's round 2 request): stone and lava golems as monsters, a lava golem 1 pick in
 *       {@value #LAVA_GOLEM_ONE_IN}, both with a crowding limit (spawn costs), and nothing in any other list (no animals,
 *       no bats).</li>
 *   <li>{@link #cinderWastesSpawnOnlyGolems}: a natural-spawning pass on real Cinder Wastes ground (the sample site of
 *       {@link TerrainChunksGameTest}, generated for real) gives stone golems and nothing else that isn't a golem: no
 *       zombies or skeletons; lava golems only near lava; and the crowding limit keeps them few.</li>
 * </ul>
 * Nothing is added to the world: the pass only asks Minecraft's spawning rules where a mob would go.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class DrossSpawnsGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The user's rule for the Cinder Wastes: near lava about 1 golem in this many is a lava golem (weights 20 : 80). */
    private static final int LAVA_GOLEM_ONE_IN = 5;
    /** Mobs the base lists must have, and the ones the user removed from them (slimes, creepers, spiders). */
    private static final List<EntityType<?>> BASE_MONSTERS_KEPT = List.of(EntityType.ZOMBIE, EntityType.SKELETON);
    private static final List<EntityType<?>> BASE_MONSTERS_REMOVED = List.of(EntityType.SLIME, EntityType.CREEPER, EntityType.SPIDER);
    /**
     * The user's rats (October 2026): on Blightwood's surface only rats and witches spawn (October 17: no zombies,
     * skeletons or endermen), the witches 7% of the mobs, 1 in 5 of the rats enraged; in the sewers and the rat cave only
     * rats, 3 in 5 enraged. (Counting group sizes; worldgen.py's SEWERS.)
     */
    private static final double BLIGHTWOOD_WITCH_SHARE = 0.07D;
    private static final double BLIGHTWOOD_ENRAGED_SHARE = 1.0D / 5.0D;
    private static final double SEWER_ENRAGED_SHARE = 3.0D / 5.0D;
    private static final double RAT_CAVE_ENRAGED_SHARE = 3.0D / 5.0D;
    private static final double SHARE_TOLERANCE = 0.02D;

    /** Spawn attempts in the natural-spawning pass, each at a random column of the site (on its ground). */
    private static final int ATTEMPTS = 3000;
    /** The pass's own random numbers (the same picks every run, for the same world). */
    private static final long PASS_SEED = 20261012L;
    /** At least this many stone golems must spawn on the site (it's 80 x 80 blocks; about 7 or 8 do). */
    private static final int MIN_STONE_GOLEMS = 1;
    /**
     * At most this many golems may spawn on the site: the crowding limit (charge 1.0, budget 0.12) allows about 7 or 8
     * there, so this is twice that. Without it, golems would spawn at nearly every attempt (they ignore light).
     */
    private static final int MAX_GOLEMS_ON_SITE = 16;

    private DrossSpawnsGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The spawn lists
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_spawn_lists")
    public static void spawnListsPerBiome(GameTestHelper helper)
    {
        Registry<Biome> biomes = helper.getLevel().registryAccess().registryOrThrow(Registries.BIOME);
        // The base lists, as the main biome (Silverwood Forest) has them; the other plain biomes must match it exactly.
        Biome reference = biomes.get(DrossBiomes.SILVERWOOD_FOREST);
        if (reference == null)
        {
            throw new GameTestAssertException("the biome " + DrossBiomes.SILVERWOOD_FOREST.location() + " isn't registered");
        }
        MobSpawnSettings baseMobs = reference.getMobSettings();
        List<String> problems = new ArrayList<>();

        // The base lists themselves: the user's rule (no slimes, creepers or spiders), and zombies and skeletons kept.
        Set<EntityType<?>> baseMonsters = types(baseMobs.getMobs(MobCategory.MONSTER));
        for (EntityType<?> kept : BASE_MONSTERS_KEPT)
        {
            if (!baseMonsters.contains(kept))
            {
                problems.add("the Dross's base monsters should include " + EntityType.getKey(kept) + " (" + describe(baseMobs.getMobs(MobCategory.MONSTER)) + ")");
            }
        }
        for (EntityType<?> removed : BASE_MONSTERS_REMOVED)
        {
            if (baseMobs.getEntityTypes().contains(removed))
            {
                problems.add("the user removed " + EntityType.getKey(removed) + " from the Dross, but the base lists have it");
            }
        }
        if (baseMobs.getMobs(MobCategory.CREATURE).isEmpty() || baseMobs.getMobs(MobCategory.AMBIENT).isEmpty())
        {
            problems.add("the Dross's base lists should have animals and bats, but they're " + describe(baseMobs.getMobs(MobCategory.CREATURE))
                    + " and " + describe(baseMobs.getMobs(MobCategory.AMBIENT)));
        }

        for (ResourceKey<Biome> key : DrossBiomes.ALL)
        {
            Biome biome = biomes.get(key);
            if (biome == null)
            {
                problems.add(DrossBiomes.name(key) + " isn't registered");
                continue;
            }
            if (key.equals(DrossBiomes.CINDER_WASTES))
            {
                checkCinderLists(biome.getMobSettings(), problems);
            }
            else if (key.equals(DrossBiomes.BLIGHTWOOD))
            {
                checkBlightwoodLists(biome.getMobSettings(), baseMobs, problems);
            }
            else if (!key.equals(DrossBiomes.SILVERWOOD_FOREST))
            {
                checkSameAsBase(DrossBiomes.name(key), biome.getMobSettings(), baseMobs, problems);
            }
        }
        // The sewers under Blightwood: only rats (3 in 5 enraged), nothing in any other list; the rat cave's own list the same
        Biome sewers = biomes.get(DrossBiomes.SEWERS);
        if (sewers == null)
        {
            problems.add("the sewers biome " + DrossBiomes.SEWERS.location() + " isn't registered");
        }
        else
        {
            String rats = SewersGameTest.ratsOnly("the sewers", sewers.getMobSettings().getMobs(MobCategory.MONSTER), SEWER_ENRAGED_SHARE);
            if (rats != null)
            {
                problems.add(rats);
            }
            for (MobCategory category : MobCategory.values())
            {
                if (category != MobCategory.MONSTER && !sewers.getMobSettings().getMobs(category).isEmpty())
                {
                    problems.add("the sewers' " + category.getName() + " list should be empty (only rats there), but it's "
                            + describe(sewers.getMobSettings().getMobs(category)));
                }
            }
        }
        Structure ratCave = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE).get(Sewers.RAT_CAVE);
        if (!(ratCave instanceof SewerStructure caveStructure) || !caveStructure.hasRatCave())
        {
            problems.add("the structure " + Sewers.RAT_CAVE.location() + " isn't a rat cave network");
        }
        else
        {
            String rats = SewersGameTest.ratsOnly("the rat cave", caveStructure.ratCaveMonsters(), RAT_CAVE_ENRAGED_SHARE);
            if (rats != null)
            {
                problems.add(rats);
            }
        }
        if (!problems.isEmpty())
        {
            throw new GameTestAssertException("the spawn lists aren't right: " + String.join("; ", problems));
        }
        helper.succeed();
    }

    /** Every list, every spawn cost and the animal chance exactly as the base lists (Silverwood Forest's) have them. */
    private static void checkSameAsBase(String name, MobSpawnSettings mobs, MobSpawnSettings baseMobs, List<String> problems)
    {
        for (MobCategory category : MobCategory.values())
        {
            List<String> have = describe(mobs.getMobs(category));
            List<String> want = describe(baseMobs.getMobs(category));
            if (!have.equals(want))
            {
                problems.add(name + "'s " + category.getName() + " list should be the base Dross list " + want + ", but it's " + have);
            }
        }
        Set<EntityType<?>> everyType = new LinkedHashSet<>(baseMobs.getEntityTypes());
        everyType.addAll(mobs.getEntityTypes());
        for (EntityType<?> type : everyType)
        {
            MobSpawnSettings.MobSpawnCost have = mobs.getMobSpawnCost(type);
            MobSpawnSettings.MobSpawnCost want = baseMobs.getMobSpawnCost(type);
            if (!Objects.equals(have, want))
            {
                problems.add(name + " gives " + EntityType.getKey(type) + " the spawn cost " + have + ", the base lists " + want);
            }
        }
        if (mobs.getCreatureProbability() != baseMobs.getCreatureProbability())
        {
            problems.add(name + "'s animal chance is " + mobs.getCreatureProbability() + ", the base lists' " + baseMobs.getCreatureProbability());
        }
    }

    /**
     * Blightwood (the user's request, October 17): its monsters are only rats and witches, no zombies, skeletons or
     * endermen; the witches are 7% of the mobs spawning there and 1 in 5 of the rats is enraged (counting group sizes:
     * weight x average group). Its other lists (animals, bats) stay the base Dross lists.
     */
    private static void checkBlightwoodLists(MobSpawnSettings mobs, MobSpawnSettings baseMobs, List<String> problems)
    {
        EntityType<?> rat = ModEntities.RAT.get();
        EntityType<?> enraged = ModEntities.ENRAGED_RAT.get();
        List<String> others = new ArrayList<>();
        double rats = 0.0D;
        double enragedRats = 0.0D;
        double witches = 0.0D;
        double all = 0.0D;
        for (MobSpawnSettings.SpawnerData data : mobs.getMobs(MobCategory.MONSTER).unwrap())
        {
            double count = data.getWeight().asInt() * (data.minCount + data.maxCount) / 2.0D;
            all += count;
            if (data.type == rat || data.type == enraged)
            {
                rats += count;
                enragedRats += data.type == enraged ? count : 0.0D;
            }
            else if (data.type == EntityType.WITCH)
            {
                witches += count;
            }
            else
            {
                others.add(EntityType.getKey(data.type) + " " + data.getWeight().asInt() + " " + data.minCount + "-" + data.maxCount);
            }
        }
        if (!others.isEmpty())
        {
            problems.add("Blightwood's monsters should be only rats and witches, but it also has " + others);
        }
        if (all <= 0.0D || rats <= 0.0D || Math.abs(witches / all - BLIGHTWOOD_WITCH_SHARE) > SHARE_TOLERANCE
                || Math.abs(enragedRats / rats - BLIGHTWOOD_ENRAGED_SHARE) > SHARE_TOLERANCE)
        {
            problems.add(String.format(Locale.ROOT, "in Blightwood witches should be 7%% of the mobs (they're %.1f%%), the rest rats, 1 in 5 of "
                    + "them enraged (%.1f%%): %s", 100.0D * witches / Math.max(1.0D, all), 100.0D * enragedRats / Math.max(1.0D, rats),
                    describe(mobs.getMobs(MobCategory.MONSTER))));
        }
        LOGGER.info("[dross spawns] Blightwood: rats are {}% of the mobs spawning ({}% of them enraged), witches {}%; the sewers and the rat cave: rats only",
                String.format(Locale.ROOT, "%.1f", 100.0D * rats / Math.max(1.0D, all)), String.format(Locale.ROOT, "%.1f", 100.0D * enragedRats / Math.max(1.0D, rats)),
                String.format(Locale.ROOT, "%.1f", 100.0D * witches / Math.max(1.0D, all)));
        for (MobCategory category : MobCategory.values())
        {
            if (category != MobCategory.MONSTER && !describe(mobs.getMobs(category)).equals(describe(baseMobs.getMobs(category))))
            {
                problems.add("Blightwood's " + category.getName() + " list should be the base Dross list");
            }
        }
        if (mobs.getCreatureProbability() != baseMobs.getCreatureProbability())
        {
            problems.add("Blightwood's animal chance should be the base lists'");
        }
    }

    /** The Cinder Wastes: only stone and lava golems, as monsters, 1 in 5 a lava golem, both with spawn costs; nothing else. */
    private static void checkCinderLists(MobSpawnSettings mobs, List<String> problems)
    {
        EntityType<?> stone = ModEntities.STONE_GOLEM.get();
        EntityType<?> lava = ModEntities.LAVA_GOLEM.get();
        WeightedRandomList<MobSpawnSettings.SpawnerData> monsters = mobs.getMobs(MobCategory.MONSTER);
        List<EntityType<?>> listed = monsters.unwrap().stream().<EntityType<?>>map(data -> data.type).toList();
        if (listed.size() != 2 || !listed.contains(stone) || !listed.contains(lava))
        {
            problems.add("the Cinder Wastes' monsters should be just the stone golem and the lava golem, but they're " + describe(monsters));
        }
        else
        {
            int total = 0;
            int lavaWeight = 0;
            for (MobSpawnSettings.SpawnerData data : monsters.unwrap())
            {
                total += data.getWeight().asInt();
                if (data.type == lava)
                {
                    lavaWeight = data.getWeight().asInt();
                }
            }
            if (lavaWeight * LAVA_GOLEM_ONE_IN != total)
            {
                problems.add("in the Cinder Wastes 1 golem pick in " + LAVA_GOLEM_ONE_IN + " should be a lava golem, but the weights are "
                        + describe(monsters));
            }
        }
        for (EntityType<?> golem : List.of(stone, lava))
        {
            if (golem.getCategory() != MobCategory.MONSTER)
            {
                problems.add(EntityType.getKey(golem) + " should be a monster (it's " + golem.getCategory() + ")");
            }
            MobSpawnSettings.MobSpawnCost cost = mobs.getMobSpawnCost(golem);
            if (cost == null || cost.charge() <= 0.0D || cost.energyBudget() <= 0.0D)
            {
                problems.add("the Cinder Wastes should give " + EntityType.getKey(golem) + " a spawn cost (crowding limit), but it's " + cost);
            }
        }
        for (MobCategory category : MobCategory.values())
        {
            if (category != MobCategory.MONSTER && !mobs.getMobs(category).isEmpty())
            {
                problems.add("the Cinder Wastes' " + category.getName() + " list should be empty (only golems there), but it's "
                        + describe(mobs.getMobs(category)));
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // A natural-spawning pass on real Cinder Wastes ground
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_spawn_cinder", timeoutTicks = TerrainSamplingGameTest.BACKSTOP_TICKS)
    public static void cinderWastesSpawnOnlyGolems(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        TerrainProbe probe = TerrainProbe.forWorld(dross);
        CompletableFuture<Map<ResourceKey<Biome>, BlockPos>> sites = CompletableFuture.supplyAsync(() -> TerrainChunksGameTest.findSites(probe));
        long deadline = Util.getMillis() + TerrainSamplingGameTest.TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!sites.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still looking for a Cinder Wastes site");
                    }
                })
                .thenExecute(() -> {
                    if (!sites.isDone())
                    {
                        throw new GameTestAssertException("looking for a Cinder Wastes site took too long");
                    }
                    BlockPos site = sites.join().get(DrossBiomes.CINDER_WASTES);
                    if (site == null)
                    {
                        throw new GameTestAssertException("no Cinder Wastes site within " + TerrainChunksGameTest.SITE_SEARCH + " blocks of "
                                + TerrainChunksGameTest.SITE_X + ", " + TerrainChunksGameTest.SITE_Z);
                    }
                    List<LevelChunk> chunks = TerrainChunksGameTest.generate(dross, site);
                    checkPass(site, spawnPass(dross, chunks));
                })
                .thenSucceed();
    }

    /** What the pass found. */
    private record Pass(int attempts, int outside, int emptyLists, Map<EntityType<?>, Integer> picks, int lavaPicksNearLava,
                        int crowdedOut, List<Spawned> spawned) {}

    /** A mob the pass would have spawned (never added to the world). */
    private record Spawned(EntityType<?> type, BlockPos pos, boolean nearLava) {}

    /**
     * Minecraft's natural spawning of monsters, step by step, at {@value #ATTEMPTS} random columns of the chunks (on the
     * ground: the spot above the top block a mob can stand on): the biome's monster list there (with Forge's additions)
     * picks a mob by weight; then the same checks as {@code NaturalSpawner}: its placement (on solid ground, room, no
     * fluid), its spawn rules (for the golems: not Peaceful, a block it may spawn on, and lava near for a lava golem),
     * room for its body, the mob's own check, and the biome's crowding limit (spawn costs: Minecraft's own
     * {@link PotentialCalculator}, charged with each mob the pass "spawns"). No player is needed and nothing is added.
     */
    private static Pass spawnPass(ServerLevel dross, List<LevelChunk> chunks)
    {
        RandomSource random = RandomSource.create(PASS_SEED);
        ChunkGenerator generator = dross.getChunkSource().getGenerator();
        StructureManager structures = dross.structureManager();
        PotentialCalculator crowding = new PotentialCalculator();
        Map<EntityType<?>, Integer> picks = new LinkedHashMap<>();
        List<Spawned> spawned = new ArrayList<>();
        int outside = 0;
        int emptyLists = 0;
        int lavaPicksNearLava = 0;
        int crowdedOut = 0;
        for (int i = 0; i < ATTEMPTS; i++)
        {
            LevelChunk chunk = chunks.get(random.nextInt(chunks.size()));
            int x = chunk.getPos().getMinBlockX() + random.nextInt(16);
            int z = chunk.getPos().getMinBlockZ() + random.nextInt(16);
            BlockPos pos = new BlockPos(x, chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1, z);
            Holder<Biome> biome = dross.getBiome(pos);
            if (!biome.is(DrossBiomes.CINDER_WASTES))
            {
                outside++;
                continue;
            }
            WeightedRandomList<MobSpawnSettings.SpawnerData> list = ForgeEventFactory.getPotentialSpawns(dross, MobCategory.MONSTER, pos,
                    generator.getMobsAt(biome, structures, MobCategory.MONSTER, pos));
            Optional<MobSpawnSettings.SpawnerData> pick = list.getRandom(random);
            if (pick.isEmpty())
            {
                emptyLists++;
                continue;
            }
            EntityType<?> type = pick.get().type;
            picks.merge(type, 1, Integer::sum);
            boolean nearLava = WildGolemSpawns.isLavaNear(dross, pos);
            if (type == ModEntities.LAVA_GOLEM.get() && nearLava)
            {
                lavaPicksNearLava++;
            }
            if (!wouldSpawnNaturally(dross, type, pos, random))
            {
                continue;
            }
            MobSpawnSettings.MobSpawnCost cost = biome.value().getMobSettings().getMobSpawnCost(type);
            if (cost != null && crowding.getPotentialEnergyChange(pos, cost.charge()) > cost.energyBudget())
            {
                crowdedOut++;
                continue;
            }
            spawned.add(new Spawned(type, pos, nearLava));
            if (cost != null)
            {
                crowding.addCharge(pos, cost.charge());
            }
        }
        return new Pass(ATTEMPTS, outside, emptyLists, picks, lavaPicksNearLava, crowdedOut, spawned);
    }

    private static void checkPass(BlockPos site, Pass pass)
    {
        EntityType<?> stone = ModEntities.STONE_GOLEM.get();
        EntityType<?> lava = ModEntities.LAVA_GOLEM.get();
        int stoneSpawned = (int) pass.spawned().stream().filter(s -> s.type() == stone).count();
        int lavaSpawned = (int) pass.spawned().stream().filter(s -> s.type() == lava).count();
        double closest = Double.POSITIVE_INFINITY;
        for (int a = 0; a < pass.spawned().size(); a++)
        {
            for (int b = a + 1; b < pass.spawned().size(); b++)
            {
                closest = Math.min(closest, Math.sqrt(pass.spawned().get(a).pos().distSqr(pass.spawned().get(b).pos())));
            }
        }
        Map<String, Integer> picks = new LinkedHashMap<>();
        pass.picks().forEach((type, n) -> picks.put(EntityType.getKey(type).toString(), n));
        String where = "the Cinder Wastes site at " + site.getX() + ", " + site.getZ();
        LOGGER.info("[dross spawns] {}: {} attempts ({} outside the biome), picked {} ({} lava golem picks near lava); would spawn {} stone"
                        + " golem(s) and {} lava golem(s), {} more kept out by the crowding limit; the closest two {} blocks apart",
                where, pass.attempts(), pass.outside(), picks, pass.lavaPicksNearLava(), stoneSpawned, lavaSpawned, pass.crowdedOut(),
                Double.isInfinite(closest) ? "-" : String.format(Locale.ROOT, "%.1f", closest));

        List<String> problems = new ArrayList<>();
        if (pass.outside() == pass.attempts())
        {
            problems.add("none of the site's columns are Cinder Wastes");
        }
        if (pass.emptyLists() > 0)
        {
            problems.add(pass.emptyLists() + " spots had no monster to pick at all");
        }
        for (EntityType<?> type : pass.picks().keySet())
        {
            if (type != stone && type != lava)
            {
                problems.add(EntityType.getKey(type) + " was picked to spawn there (only golems should)");
            }
        }
        for (EntityType<?> golem : List.of(stone, lava))
        {
            if (!pass.picks().containsKey(golem))
            {
                problems.add(EntityType.getKey(golem) + " was never picked in " + pass.attempts() + " attempts");
            }
        }
        for (Spawned s : pass.spawned())
        {
            if (s.type() != stone && s.type() != lava)
            {
                problems.add(EntityType.getKey(s.type()) + " would spawn at " + s.pos().toShortString());
            }
            if (s.type() == lava && !s.nearLava())
            {
                problems.add("a lava golem would spawn at " + s.pos().toShortString() + " with no lava near");
            }
        }
        if (stoneSpawned < MIN_STONE_GOLEMS)
        {
            problems.add("no stone golem would spawn on the site's ground");
        }
        if (pass.spawned().size() > MAX_GOLEMS_ON_SITE)
        {
            problems.add(pass.spawned().size() + " golems would spawn on the 80 x 80 site (at most " + MAX_GOLEMS_ON_SITE
                    + ": the crowding limit should keep them few)");
        }
        if (!problems.isEmpty())
        {
            throw new GameTestAssertException("natural spawning on " + where + " isn't right: " + String.join("; ", problems));
        }
    }

    /**
     * True if natural spawning would put a mob of this type at {@code pos} (its feet): the same checks, in the same order,
     * as Minecraft's {@code NaturalSpawner} (its placement, its spawn rules, room for its body, then the mob's own check
     * and nothing in the way). The mob made for the last check is never added to the world.
     */
    private static boolean wouldSpawnNaturally(ServerLevel level, EntityType<?> type, BlockPos pos, RandomSource random)
    {
        if (type.getCategory() == MobCategory.MISC || !type.canSummon()
                || !NaturalSpawner.isSpawnPositionOk(SpawnPlacements.getPlacementType(type), level, pos, type)
                || !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, random)
                || !level.noCollision(type.getAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)))
        {
            return false;
        }
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob))
        {
            return false;
        }
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        return ForgeEventFactory.checkSpawnPosition(mob, level, MobSpawnType.NATURAL);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static Set<EntityType<?>> types(WeightedRandomList<MobSpawnSettings.SpawnerData> list)
    {
        Set<EntityType<?>> out = new LinkedHashSet<>();
        for (MobSpawnSettings.SpawnerData data : list.unwrap())
        {
            out.add(data.type);
        }
        return out;
    }

    /** A list as "id weight min-max" lines, in its order (for comparing and for messages). */
    private static List<String> describe(WeightedRandomList<MobSpawnSettings.SpawnerData> list)
    {
        List<String> out = new ArrayList<>();
        for (MobSpawnSettings.SpawnerData data : list.unwrap())
        {
            out.add(EntityType.getKey(data.type) + " " + data.getWeight().asInt() + " " + data.minCount + "-" + data.maxCount);
        }
        return out;
    }
}
