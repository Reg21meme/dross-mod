package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Structure placement {@code dross:knight_ruin}: the knight ruins' structure set starts a ruin only in the chunk holding a
 * ruin's middle column, as {@link KnightRuinSites} plans them from the seed:
 * <pre>{"type": "dross:knight_ruin", "salt": 1119202601}</pre>
 * It looks the world up by its random state, so another dimension with Overworld biomes (a mod's) never gets one.
 * Minecraft's {@code /locate structure} can't search it; {@code /dross goto ruin} and {@code /dross locate ruins} can.
 */
public final class KnightRuinPlacement extends StructurePlacement
{
    public static final Codec<KnightRuinPlacement> CODEC = RecordCodecBuilder.create(instance -> placementCodec(instance)
            .apply(instance, KnightRuinPlacement::new));

    private KnightRuinPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt,
                                Optional<ExclusionZone> exclusion)
    {
        super(locateOffset, method, frequency, salt, exclusion);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ)
    {
        return KnightRuinSites.isStartChunk(state.randomState(), chunkX, chunkZ);
    }

    @Override
    public StructurePlacementType<?> type()
    {
        return ModStructures.KNIGHT_RUIN_PLACEMENT.get();
    }
}
