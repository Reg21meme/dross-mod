package com.reg21meme.dross.villager;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Rift Chapel as a world-generation structure (structure type {@code dross:rift_chapel}): the Dross trader's one
 * chapel, at the edge of the village the seed picked for him ({@link RiftChapels}, {@link TraderVillages}), made with the
 * Overworld's land, its trader in it. Its structure set's placement ({@link RiftChapelPlacement}) starts it only in the
 * chapel's own chunk. Its pieces build it chunk by chunk, in the "surface structures" step: the chapel
 * ({@link RiftChapelPiece}, with Minecraft's terrain adaptation {@code beard_thin}: the land drawn to its floor, like a
 * village house's), then its path to the village's nearest street ({@link RiftChapelPathPiece}).
 */
public final class RiftChapelStructure extends Structure
{
    public static final Codec<RiftChapelStructure> CODEC = simpleCodec(RiftChapelStructure::new);

    public RiftChapelStructure(StructureSettings settings)
    {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context)
    {
        ChunkPos chunk = context.chunkPos();
        RiftChapels.Home home = RiftChapels.homeOf(context.randomState());
        if (home == null || home.chapel().chunkX() != chunk.x || home.chapel().chunkZ() != chunk.z)
        {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(home.chapel().center(), builder -> {
            builder.addPiece(new RiftChapelPiece(home));
            if (!home.path().isEmpty())
            {
                builder.addPiece(new RiftChapelPathPiece(home));
            }
        }));
    }

    /** The home has already checked its land, the same way the commands and the letter see it: never refused for its biome here. */
    @Override
    public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context)
    {
        return this.findGenerationPoint(context);
    }

    @Override
    public StructureType<?> type()
    {
        return ModStructures.RIFT_CHAPEL.get();
    }
}
