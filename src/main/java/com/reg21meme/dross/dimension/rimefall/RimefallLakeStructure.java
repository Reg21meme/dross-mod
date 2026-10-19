package com.reg21meme.dross.dimension.rimefall;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A Rimefall patch's big frozen lake (structure type {@code dross:rimefall_lake}; the user's request of October 19,
 * 2026: "each rimefall should have 1 big lake", about 100 blocks across, with open water holes). Where it goes is the
 * boss-site planner's ({@link BossSites#RIMEFALL_AREA}, one in every Rimefall patch: {@link RimefallAreas}); its
 * structure set's placement ({@code dross:boss_site}) starts it only in its planned chunk, and its one piece
 * ({@link RimefallLakePiece}) builds it chunk by chunk, in the "lakes" step (before the trees and plants, which keep off
 * it: {@code dross:not_on_rimefall_lake}).
 */
public final class RimefallLakeStructure extends Structure
{
    public static final Codec<RimefallLakeStructure> CODEC = simpleCodec(RimefallLakeStructure::new);
    /** The start's position is given this high (its biome is checked there): always the surface's biome. */
    private static final int SURFACE_BIOME_Y = 319;

    public RimefallLakeStructure(StructureSettings settings)
    {
        super(settings);
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
        Optional<Site> site = sites.siteAtChunk(BossSites.RIMEFALL_AREA, chunk);
        if (site.isEmpty() || !(site.get().detail() instanceof RimefallAreas.RimefallArea area) || area.lake() == null)
        {
            return Optional.empty();
        }
        RimefallLake lake = area.lake();
        return Optional.of(new GenerationStub(new BlockPos(lake.centre().getX(), SURFACE_BIOME_Y, lake.centre().getZ()),
                builder -> builder.addPiece(new RimefallLakePiece(lake))));
    }

    @Override
    public StructureType<?> type()
    {
        return ModStructures.RIMEFALL_LAKE.get();
    }
}
