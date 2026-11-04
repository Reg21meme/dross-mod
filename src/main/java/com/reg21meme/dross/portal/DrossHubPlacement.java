package com.reg21meme.dross.portal;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Structure placement {@code dross:dross_hub}: the hub's structure set starts its structure only in the one chunk that
 * holds the Shattered Spire's middle ({@link DrossHub#spireCenter}), so a world has exactly one hub, at Dross 0,0:
 * <pre>{"type": "dross:dross_hub", "salt": 1104202601}</pre>
 * (The salt is required by Minecraft's placement format; nothing here uses it.) Minecraft's own {@code /locate structure}
 * can't search it; {@code /dross goto hub} goes there.
 */
public final class DrossHubPlacement extends StructurePlacement
{
    public static final Codec<DrossHubPlacement> CODEC = RecordCodecBuilder.create(instance -> placementCodec(instance)
            .apply(instance, DrossHubPlacement::new));

    private DrossHubPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt,
                              Optional<ExclusionZone> exclusion)
    {
        super(locateOffset, method, frequency, salt, exclusion);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ)
    {
        ChunkPos middle = new ChunkPos(DrossHub.spireCenter(0));
        return chunkX == middle.x && chunkZ == middle.z;
    }

    @Override
    public StructurePlacementType<?> type()
    {
        return ModStructures.DROSS_HUB_PLACEMENT.get();
    }
}
