package com.reg21meme.dross.dimension.sewer;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.dimension.sites.SiteUse;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.random.WeightedRandomList;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;
import org.slf4j.Logger;

/**
 * A sewer network under Blightwood (structure type {@code dross:sewers}; the user's request, October 2026). Two
 * structures use it ({@code data/dross/worldgen/structure/}, written by {@code tools/terrain/worldgen.py}):
 * {@code dross:sewers}, a regular network of tangled tunnels with 3 pipe entrances, and {@code dross:rat_cave}, the Rat
 * King's sewer, ending in his rat cave, with one grand entrance and a royal road. {@code #dross:sewers} is both.
 * <p>
 * Where they go is the boss-site planner's ({@link BossSites}, the user's decision of October 16): in each group of 3
 * Blightwood patches, one gets the Rat King's sewer and each of the other two a regular one with a 1 in 2 chance. Each
 * has its own structure set whose placement ({@code dross:boss_site}) starts it only in its planned chunk, and there
 * {@link #findGenerationPoint} lays it out again with {@link SewerPlanner}, exactly as the planner checked it (from the
 * terrain's noise only: no chunks are needed); the pieces build it chunk by chunk.
 * <p>
 * The sewer water is ordinary water. It looks murky green because the whole Blightwood underground is its own biome,
 * {@code dross:sewers} (worldgen.py: its colours are {@code DrossColors.SEWERS_*}, its only mobs rats); the surface biome
 * map doesn't change. The network always lies deep enough that every block of it is in that biome.
 */
public final class SewerStructure extends Structure
{
    public static final Codec<SewerStructure> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            settingsCodec(instance),
            Codec.BOOL.fieldOf("rat_cave").forGetter(SewerStructure::hasRatCave),
            Codec.INT.fieldOf("min_cover").forGetter(SewerStructure::minCover),
            Codec.INT.fieldOf("floor_min_y").forGetter(SewerStructure::floorMinY),
            Codec.INT.fieldOf("floor_max_y").forGetter(SewerStructure::floorMaxY),
            Codec.INT.fieldOf("air_max_y").forGetter(SewerStructure::airMaxY),
            WeightedRandomList.codec(MobSpawnSettings.SpawnerData.CODEC).optionalFieldOf("rat_cave_monsters", WeightedRandomList.create())
                    .forGetter(SewerStructure::ratCaveMonsters)
    ).apply(instance, SewerStructure::new));

    private static final Logger LOGGER = LogUtils.getLogger();
    /** The start's position is given this high (its biome is checked there): above every hill, so it's always the surface's biome. */
    public static final int SURFACE_BIOME_Y = 319;

    private final boolean ratCave;
    private final int minCover;
    private final int floorMinY;
    private final int floorMaxY;
    private final int airMaxY;
    private final WeightedRandomList<MobSpawnSettings.SpawnerData> ratCaveMonsters;

    public SewerStructure(StructureSettings settings, boolean ratCave, int minCover, int floorMinY, int floorMaxY, int airMaxY,
                          WeightedRandomList<MobSpawnSettings.SpawnerData> ratCaveMonsters)
    {
        super(settings);
        this.ratCave = ratCave;
        this.minCover = minCover;
        this.floorMinY = floorMinY;
        this.floorMaxY = floorMaxY;
        this.airMaxY = airMaxY;
        this.ratCaveMonsters = ratCaveMonsters;
    }

    /** True for dross:rat_cave: the network ends in a rat cave. */
    public boolean hasRatCave()
    {
        return this.ratCave;
    }

    /** At least this many blocks of rock over every tunnel's air (so all of it lies in the sewers biome, under the surface's). */
    public int minCover()
    {
        return this.minCover;
    }

    /** The network's floor is somewhere between these heights. */
    public int floorMinY()
    {
        return this.floorMinY;
    }

    public int floorMaxY()
    {
        return this.floorMaxY;
    }

    /** No air block of a network is higher than this (the sewers biome only reaches up to a few blocks above it). */
    public int airMaxY()
    {
        return this.airMaxY;
    }

    /** The monsters that spawn in its rat cave (rats only; worldgen.py's RAT_CAVE_* numbers). Empty for dross:sewers. */
    public WeightedRandomList<MobSpawnSettings.SpawnerData> ratCaveMonsters()
    {
        return this.ratCaveMonsters;
    }

    /** The planner's use this structure is placed for: the Rat King's sewer or a regular one. */
    public SiteUse use()
    {
        return this.ratCave ? BossSites.RAT_KING_SEWER : BossSites.REGULAR_SEWER;
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context)
    {
        ChunkPos chunk = context.chunkPos();
        BossSites sites = BossSites.forWorldgen(context.randomState());
        if (sites == null)
        {
            return Optional.empty();
        }
        Optional<Site> site = sites.siteAtChunk(use(), chunk);
        if (site.isEmpty())
        {
            return Optional.empty();
        }
        SewerPlanner.Plan plan = SewerPlanner.plan(sites.terrain(), this, chunk, site.get().relaxed());
        if (!plan.ok())
        {
            // (never: the planner only plans a network where this same layout fitted)
            LOGGER.warn("[sewers] the {} planned at {} didn't fit when it was generated ({}); it's left out", use(), chunk, plan.failure());
            return Optional.empty();
        }
        // (The position's height is only used for the biome check: the surface's biome.)
        return Optional.of(new GenerationStub(new BlockPos(chunk.getMiddleBlockX(), SURFACE_BIOME_Y, chunk.getMiddleBlockZ()),
                builder -> plan.pieces().forEach(builder::addPiece)));
    }

    @Override
    public StructureType<?> type()
    {
        return ModStructures.SEWERS.get();
    }
}
