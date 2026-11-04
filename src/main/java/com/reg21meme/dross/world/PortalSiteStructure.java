package com.reg21meme.dross.world;

import com.reg21meme.dross.registry.ModStructures;
import com.mojang.serialization.Codec;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * The portal site as a world-generation structure (structure type {@code dross:portal_site}; the user's decision of
 * November 2, 2026): the Fallen Cathedral round its unlit Dross frame, made with the Overworld's land, one per world, at
 * the spot the seed picked ({@link SiteFinder}). Its structure set's placement ({@link PortalSitePlacement}) starts it only
 * in the chunk holding the site column; its one piece ({@link PortalSitePiece}) builds it chunk by chunk. Built in the
 * "surface structures" step with Minecraft's terrain adaptation {@code beard_thin} (the land shaped to its floor, like a
 * village house's); the trees that grow on and round it afterwards are felled once by {@link SiteTidy}.
 */
public final class PortalSiteStructure extends Structure
{
    public static final Codec<PortalSiteStructure> CODEC = simpleCodec(PortalSiteStructure::new);

    public PortalSiteStructure(StructureSettings settings)
    {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context)
    {
        SiteSpot spot = SiteFinder.spot(context.randomState());
        if (spot == null || !spot.startChunk().equals(context.chunkPos()))
        {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(new BlockPos(spot.x(), spot.groundY(), spot.z()),
                builder -> builder.addPiece(new PortalSitePiece(spot.x(), spot.z(), spot.groundY()))));
    }

    /** The spot was chosen with its biomes already ({@link SiteRules}): its start is never refused for its biome here. */
    @Override
    public Optional<GenerationStub> findValidGenerationPoint(GenerationContext context)
    {
        return this.findGenerationPoint(context);
    }

    @Override
    public StructureType<?> type()
    {
        return ModStructures.PORTAL_SITE.get();
    }
}
