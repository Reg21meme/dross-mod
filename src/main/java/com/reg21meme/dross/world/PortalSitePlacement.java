package com.reg21meme.dross.world;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.Vec3i;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Structure placement {@code dross:portal_site}: the portal site's structure set starts its structure only in the one chunk
 * that holds the site column the seed picked ({@link SiteFinder#isSiteChunk}), so a world has exactly one, in its
 * Overworld:
 * <pre>{"type": "dross:portal_site", "salt": 1104202602}</pre>
 * (The salt is required by Minecraft's placement format; the spot's own salt is {@link SiteRules#SPOT_SALT}.) It looks up
 * the world by its random state, so another dimension with Overworld biomes (a mod's) never gets one. A chunk out of the
 * site's reach (nearly every chunk) is answered at once; the first one within it waits for the spot if it isn't worked out
 * yet. Minecraft's {@code /locate structure} can't search it; {@code /dross goto cathedral} goes there.
 */
public final class PortalSitePlacement extends StructurePlacement
{
    public static final Codec<PortalSitePlacement> CODEC = RecordCodecBuilder.create(instance -> placementCodec(instance)
            .apply(instance, PortalSitePlacement::new));

    private PortalSitePlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt,
                                Optional<ExclusionZone> exclusion)
    {
        super(locateOffset, method, frequency, salt, exclusion);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ)
    {
        return SiteFinder.isSiteChunk(state.randomState(), chunkX, chunkZ);
    }

    @Override
    public StructurePlacementType<?> type()
    {
        return ModStructures.PORTAL_SITE_PLACEMENT.get();
    }
}
