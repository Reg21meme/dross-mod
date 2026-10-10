package com.reg21meme.dross.world;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;

/**
 * Where villages can be, worked out the way the world generator decides it, so no chunk is generated (the same idea
 * as the villager area's {@code TraderSpawner}; this is world site's own read-only copy):
 * <ol>
 *   <li>Every region of each village structure set's placement grid has one <b>possible</b> village chunk.</li>
 *   <li>The world generator only starts a village there if the biome at the village's starting point suits one of the
 *       set's village types (plains, desert, savanna, snowy and taiga villages, plus any a mod adds). That's read from
 *       the noise too, at a 3 x 3 spread of points 16 blocks apart around the start (the town centre can be turned any
 *       way), so a village is assumed whenever any of them suits one.</li>
 * </ol>
 * So the site keeps clear of every village the world has, the trader's included, without avoiding forests and ice
 * fields where villages can never be.
 */
final class SiteVillages
{
    /** How far around a possible village's start chunk corner the biome is checked (blocks, each way). */
    private static final int START_SPREAD = 16;

    /** One village structure set: its placement grid and the village structures it can start. */
    record VillageSet(RandomSpreadStructurePlacement placement, List<Structure> structures) {}

    /** The world's village structure sets. Read them on the server thread, use them anywhere. */
    static List<VillageSet> villageSets(ChunkGeneratorStructureState state)
    {
        List<VillageSet> found = new ArrayList<>();
        for (Holder<StructureSet> holder : state.possibleStructureSets())
        {
            StructureSet set = holder.value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement))
            {
                continue;
            }
            List<Structure> villages = new ArrayList<>();
            for (StructureSet.StructureSelectionEntry entry : set.structures())
            {
                if (entry.structure().is(StructureTags.VILLAGE))
                {
                    villages.add(entry.structure().value());
                }
            }
            if (!villages.isEmpty())
            {
                found.add(new VillageSet(placement, List.copyOf(villages)));
            }
        }
        return found;
    }

    /** Reads villages from the world generator's noise. Keeps what it has worked out; not thread-safe (one per thread). */
    static final class Finder
    {
        final ChunkGenerator generator;
        final RandomState random;
        final ChunkGeneratorStructureState state;
        final List<VillageSet> sets;
        final LevelHeightAccessor heights;
        /** Per village set, per possible village chunk: 1 = a village could start there, 2 = it can't. */
        final Long2ByteOpenHashMap[] known;

        Finder(ChunkGenerator generator, RandomState random, ChunkGeneratorStructureState state, List<VillageSet> sets,
               LevelHeightAccessor heights)
        {
            this.generator = generator;
            this.random = random;
            this.state = state;
            this.sets = sets;
            this.heights = heights;
            this.known = new Long2ByteOpenHashMap[sets.size()];
            for (int i = 0; i < known.length; i++)
            {
                known[i] = new Long2ByteOpenHashMap();
            }
        }

        static Finder of(ServerLevel level)
        {
            ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
            return new Finder(level.getChunkSource().getGenerator(), level.getChunkSource().randomState(), state,
                    villageSets(state), LevelHeightAccessor.create(level.getMinBuildHeight(), level.getHeight()));
        }

        /**
         * Every chunk within {@code reach} blocks (flat, measured from the box's edge to the chunk's middle) of {@code area}
         * where a village could start. Pure maths on the world seed and noise.
         */
        List<ChunkPos> villagesNear(BoundingBox area, int reach)
        {
            List<ChunkPos> found = new ArrayList<>();
            int minChunkX = (area.minX() - reach) >> 4;
            int maxChunkX = (area.maxX() + reach) >> 4;
            int minChunkZ = (area.minZ() - reach) >> 4;
            int maxChunkZ = (area.maxZ() + reach) >> 4;
            for (int setIndex = 0; setIndex < sets.size(); setIndex++)
            {
                VillageSet set = sets.get(setIndex);
                RandomSpreadStructurePlacement placement = set.placement();
                int spacing = placement.spacing();
                for (int regionX = Math.floorDiv(minChunkX, spacing); regionX <= Math.floorDiv(maxChunkX, spacing); regionX++)
                {
                    for (int regionZ = Math.floorDiv(minChunkZ, spacing); regionZ <= Math.floorDiv(maxChunkZ, spacing); regionZ++)
                    {
                        ChunkPos chunk = placement.getPotentialStructureChunk(state.getLevelSeed(), regionX * spacing, regionZ * spacing);
                        int x = chunk.getMiddleBlockX();
                        int z = chunk.getMiddleBlockZ();
                        int dx = Math.max(0, Math.max(area.minX() - x, x - area.maxX()));
                        int dz = Math.max(0, Math.max(area.minZ() - z, z - area.maxZ()));
                        if ((long) dx * dx + (long) dz * dz > (long) reach * reach
                                || !placement.isStructureChunk(state, chunk.x, chunk.z))
                        {
                            continue;
                        }
                        if (couldStart(setIndex, chunk))
                        {
                            found.add(chunk);
                        }
                    }
                }
            }
            return found;
        }

        /** True if the biome anywhere around the possible village's start suits one of the set's village types. */
        private boolean couldStart(int setIndex, ChunkPos chunk)
        {
            VillageSet set = sets.get(setIndex);
            long key = chunk.toLong();
            byte cached = known[setIndex].get(key);
            if (cached != 0)
            {
                return cached == 1;
            }
            int startX = chunk.getMinBlockX();
            int startZ = chunk.getMinBlockZ();
            int y = generator.getBaseHeight(startX, startZ, Heightmap.Types.WORLD_SURFACE_WG, heights, random);
            boolean village = false;
            for (int ox = -START_SPREAD; ox <= START_SPREAD && !village; ox += START_SPREAD)
            {
                for (int oz = -START_SPREAD; oz <= START_SPREAD && !village; oz += START_SPREAD)
                {
                    Holder<Biome> biome = generator.getBiomeSource().getNoiseBiome(QuartPos.fromBlock(startX + ox),
                            QuartPos.fromBlock(y), QuartPos.fromBlock(startZ + oz), random.sampler());
                    for (Structure structure : set.structures())
                    {
                        if (structure.biomes().contains(biome))
                        {
                            village = true;
                            break;
                        }
                    }
                }
            }
            known[setIndex].put(key, (byte) (village ? 1 : 2));
            return village;
        }
    }

    private SiteVillages() {}
}
