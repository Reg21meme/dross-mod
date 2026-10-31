package com.reg21meme.dross.boss.fire.cave;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Structure placement {@code dross:hermit_cave}: the hermit crab caves' structure set starts its structure only in the
 * chunks where {@link HermitCaveSites} planned a cave (one or two in every Cinder Wastes patch of the boss-site planner,
 * clear of its boss lairs):
 * <pre>{"type": "dross:hermit_cave", "salt": 1031202601}</pre>
 * (The salt is required by Minecraft's placement format; nothing here uses it.) Minecraft's own {@code /locate structure}
 * can't search it; {@code /dross goto hermit_cave} asks {@link HermitCaveSites#nearest} instead.
 */
public final class HermitCavePlacement extends StructurePlacement
{
    public static final Codec<HermitCavePlacement> CODEC = RecordCodecBuilder.create(instance -> placementCodec(instance)
            .apply(instance, HermitCavePlacement::new));

    private HermitCavePlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt,
                                Optional<ExclusionZone> exclusion)
    {
        super(locateOffset, method, frequency, salt, exclusion);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ)
    {
        BossSites sites = BossSites.forWorldgen(state.randomState());
        return sites != null && HermitCaveSites.caveAt(sites, new ChunkPos(chunkX, chunkZ)).isPresent();
    }

    @Override
    public StructurePlacementType<?> type()
    {
        return ModStructures.HERMIT_CAVE_PLACEMENT.get();
    }
}
