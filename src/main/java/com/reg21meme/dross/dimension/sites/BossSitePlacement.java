package com.reg21meme.dross.dimension.sites;

import com.mojang.logging.LogUtils;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;
import org.slf4j.Logger;

/**
 * Structure placement {@code dross:boss_site}: a structure set's structures start only in the chunks where
 * {@link BossSites} planned a site of one use (the structure set JSON names it; written by
 * {@code tools/terrain/worldgen.py}):
 * <pre>{"type": "dross:boss_site", "use": "dross:rat_king_sewer", "salt": 1610201626}</pre>
 * (The salt is required by Minecraft's placement format; nothing here uses it.)
 * <p>
 * Minecraft's own {@code /locate structure} can only search its own placements (random spread, rings), so it can't find
 * these: {@code Sewers.findNearest} and {@code RatCaves.findNearest} ask the planner instead.
 */
public final class BossSitePlacement extends StructurePlacement
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final Codec<BossSitePlacement> CODEC = RecordCodecBuilder.create(instance -> placementCodec(instance)
            .and(ResourceLocation.CODEC.fieldOf("use").forGetter(BossSitePlacement::useId))
            .apply(instance, BossSitePlacement::new));

    private final ResourceLocation useId;
    private volatile boolean warned;

    private BossSitePlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt, Optional<ExclusionZone> exclusion,
                              ResourceLocation useId)
    {
        super(locateOffset, method, frequency, salt, exclusion);
        this.useId = useId;
    }

    public ResourceLocation useId()
    {
        return this.useId;
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ)
    {
        Optional<SiteUse> use = BossSites.use(this.useId);
        if (use.isEmpty())
        {
            if (!this.warned)
            {
                this.warned = true;
                LOGGER.error("[boss sites] a structure set's placement names the use {}, which doesn't exist; it never places anything", this.useId);
            }
            return false;
        }
        BossSites sites = BossSites.forWorldgen(state.randomState());
        return sites != null && sites.isSiteChunk(use.get(), chunkX, chunkZ);
    }

    @Override
    public StructurePlacementType<?> type()
    {
        return ModStructures.BOSS_SITE_PLACEMENT.get();
    }
}
