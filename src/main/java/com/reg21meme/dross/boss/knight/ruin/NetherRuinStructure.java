package com.reg21meme.dross.boss.knight.ruin;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A knight ruin in the Nether as a world-generation structure ({@code dross:nether_knight_ruin}; PRE_DROSS_PLAN.md, stage
 * T3): a broken Dross portal in its Nether biome area's layout, on an open floor under the Nether's roof where the seed
 * plans it ({@link NetherRuinSites}); its one piece ({@link NetherRuinPiece}) builds it chunk by chunk. Its two knights come
 * when a player is near ({@link KnightRuinKeeper}). Built in the "surface structures" step.
 */
public final class NetherRuinStructure extends Structure
{
    public static final Codec<NetherRuinStructure> CODEC = simpleCodec(NetherRuinStructure::new);

    public NetherRuinStructure(StructureSettings settings)
    {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context)
    {
        RuinSite site = NetherRuinSites.siteStartingIn(context.randomState(), context.chunkPos().x, context.chunkPos().z);
        if (site == null)
        {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(site.ground(), builder -> builder.addPiece(new NetherRuinPiece(site))));
    }

    /** The site was chosen by its biome already: its start is never refused for its biome here. */
    @Override
    public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context)
    {
        return this.findGenerationPoint(context);
    }

    @Override
    public StructureType<?> type()
    {
        return ModStructures.NETHER_KNIGHT_RUIN.get();
    }
}
