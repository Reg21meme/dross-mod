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
 * Structure placement {@code dross:nether_knight_ruin}: the Nether knight ruins' structure set starts a ruin only in the
 * chunk holding a ruin's middle column, as {@link NetherRuinSites} plans them from the seed:
 * <pre>{"type": "dross:nether_knight_ruin", "salt": 1125202603}</pre>
 * It looks the world up by its random state, which only the world's own Nether registers, so another dimension with the
 * Nether's biomes (a mod's) never gets one. {@code /locate structure} can't search it; {@code /dross goto ruin} and
 * {@code /dross locate ruins} (from the Nether) can.
 */
public final class NetherRuinPlacement extends StructurePlacement
{
    public static final Codec<NetherRuinPlacement> CODEC = RecordCodecBuilder.create(instance -> placementCodec(instance)
            .apply(instance, NetherRuinPlacement::new));

    private NetherRuinPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt,
                                Optional<ExclusionZone> exclusion)
    {
        super(locateOffset, method, frequency, salt, exclusion);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ)
    {
        return NetherRuinSites.isStartChunk(state.randomState(), chunkX, chunkZ);
    }

    @Override
    public StructurePlacementType<?> type()
    {
        return ModStructures.NETHER_KNIGHT_RUIN_PLACEMENT.get();
    }
}
