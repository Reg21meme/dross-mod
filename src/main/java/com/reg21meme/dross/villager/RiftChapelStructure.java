package com.reg21meme.dross.villager;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The Rift Chapel as a world-generation structure (structure type {@code dross:rift_chapel}; the user's decision of
 * November 2, 2026): the Dross trader's chapel standing on its own in the countryside, made with the Overworld's land, its
 * trader in it. Its structure set's placement ({@link RiftChapelPlacement}) gives each grid cell one possible chunk; there
 * a chapel starts if the land fits ({@link RiftChapels#fit}, from the noise alone, so {@link RiftChapels#nearest} knows
 * where every one is). Its one piece ({@link RiftChapelPiece}) builds it chunk by chunk, in the "surface structures" step,
 * with Minecraft's terrain adaptation {@code beard_thin} (the land drawn to its floor, like a village house's).
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
        return RiftChapels.fitCached(context.chunkGenerator(), context.randomState(), context.seed(), chunk.x, chunk.z)
                .map(chapel -> new GenerationStub(chapel.center(), builder -> builder.addPiece(new RiftChapelPiece(chapel))));
    }

    /** The fit has already checked its biomes, the same way {@link RiftChapels#nearest} does: never refused for it here. */
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
