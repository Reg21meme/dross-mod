package com.reg21meme.dross.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Structure placement {@code dross:rift_chapel}: the trader's chapel's structure set starts its structure only in the one
 * chunk the seed picked for it, at the edge of the trader's village ({@link RiftChapels#isChapelChunk}), so a world has
 * exactly one:
 * <pre>{"type": "dross:rift_chapel", "salt": 1104202603}</pre>
 * (The salt is required by Minecraft's placement format; the pick's own salt is {@link TraderVillages#PICK_SALT}.) It
 * looks the world up by its random state, so another dimension with Overworld biomes (a mod's) never gets one. A chunk out
 * of the chapel's reach is answered at once; one within it waits for the home if it isn't worked out yet. Minecraft's
 * {@code /locate structure} can't search it; {@code /dross goto trader} goes there.
 */
public final class RiftChapelPlacement extends StructurePlacement
{
    public static final Codec<RiftChapelPlacement> CODEC = RecordCodecBuilder.create(instance -> placementCodec(instance)
            .apply(instance, RiftChapelPlacement::new));

    private RiftChapelPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt, Optional<ExclusionZone> exclusion)
    {
        super(locateOffset, method, frequency, salt, exclusion);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ)
    {
        return RiftChapels.isChapelChunk(state.randomState(), chunkX, chunkZ);
    }

    @Override
    public StructurePlacementType<?> type()
    {
        return ModStructures.RIFT_CHAPEL_PLACEMENT.get();
    }
}
