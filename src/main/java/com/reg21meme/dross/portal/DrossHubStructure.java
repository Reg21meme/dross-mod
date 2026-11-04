package com.reg21meme.dross.portal;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The hub at Dross 0,0 as a world-generation structure (structure type {@code dross:dross_hub}; the user's decision of
 * November 2, 2026): the Shattered Spire with its camp and its lit exit portal, made with the land. Its structure set's
 * placement ({@link DrossHubPlacement}) starts it only in the chunk holding the spire's middle; its one piece
 * ({@link DrossHubPiece}) builds it chunk by chunk, on the ground level worked out from the seed
 * ({@link DrossHubGround#groundY}). Built in the "surface structures" step: the ponds, boulders and fallen logs before
 * it keep well away ({@code dross:not_near_hub}), and the trees, plants and ores after it keep off it
 * ({@code dross:not_on_hub}).
 */
public final class DrossHubStructure extends Structure
{
    public static final Codec<DrossHubStructure> CODEC = simpleCodec(DrossHubStructure::new);
    /** The start's position is given this high (its biome is checked there): always the surface's biome. */
    private static final int SURFACE_BIOME_Y = 319;

    public DrossHubStructure(StructureSettings settings)
    {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context)
    {
        BlockPos middle = DrossHub.spireCenter(0);
        if (!context.chunkPos().equals(new ChunkPos(middle)))
        {
            return Optional.empty();
        }
        int groundY = DrossHubGround.groundY(context.chunkGenerator(), context.randomState(), context.heightAccessor());
        return Optional.of(new GenerationStub(new BlockPos(middle.getX(), SURFACE_BIOME_Y, middle.getZ()),
                builder -> builder.addPiece(new DrossHubPiece(groundY))));
    }

    @Override
    public StructureType<?> type()
    {
        return ModStructures.DROSS_HUB.get();
    }
}
