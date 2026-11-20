package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A knight ruin as a world-generation structure ({@code dross:knight_ruin}): a broken Dross portal in its biome area's
 * layout, made with the Overworld's land where the seed plans it ({@link KnightRuinSites}); its one piece
 * ({@link KnightRuinPiece}) builds it chunk by chunk. Its two knights come when a player is near
 * ({@link KnightRuinKeeper}). Built in the "surface structures" step.
 */
public final class KnightRuinStructure extends Structure
{
    public static final Codec<KnightRuinStructure> CODEC = simpleCodec(KnightRuinStructure::new);

    public KnightRuinStructure(StructureSettings settings)
    {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context)
    {
        RuinSite site = KnightRuinSites.siteStartingIn(context.randomState(), context.chunkPos().x, context.chunkPos().z);
        if (site == null)
        {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(site.ground(), builder -> builder.addPiece(new KnightRuinPiece(site))));
    }

    /** The site was chosen by its biomes already: its start is never refused for its biome here. */
    @Override
    public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context)
    {
        return this.findGenerationPoint(context);
    }

    @Override
    public StructureType<?> type()
    {
        return ModStructures.KNIGHT_RUIN.get();
    }
}
