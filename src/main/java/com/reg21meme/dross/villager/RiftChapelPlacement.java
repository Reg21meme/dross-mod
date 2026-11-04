package com.reg21meme.dross.villager;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.Holder;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadType;
import net.minecraft.world.level.levelgen.structure.placement.StructurePlacementType;

/**
 * Structure placement {@code dross:rift_chapel}: Minecraft's {@code random_spread} (the igloos' and witch huts' kind: one
 * possible spot in every {@code spacing} x {@code spacing} chunks, at least {@code separation} chunks from the next cell's),
 * with its {@code exclusion_zone} keeping it off the villages, and one rule more: never within
 * {@value #PORTAL_SITE_CLEARANCE} chunks of the portal site's chunk (world-builder's Fallen Cathedral, one per world:
 * the two would build over each other). Written like vanilla's, so {@code /locate structure} works the vanilla way:
 * <pre>{"type": "dross:rift_chapel", "spacing": 32, "separation": 8, "salt": 1104202603,
 *  "exclusion_zone": {"other_set": "minecraft:villages", "chunk_count": 6}}</pre>
 */
public final class RiftChapelPlacement extends RandomSpreadStructurePlacement
{
    /** The portal site's structure set (its own placement says which chunk holds the site). */
    private static final ResourceKey<StructureSet> PORTAL_SITE = ResourceKey.create(Registries.STRUCTURE_SET,
            new ResourceLocation(Dross.MODID, "portal_site"));
    /**
     * No chapel starts this many chunks or closer to the site's chunk (the cathedral and its ring reach about 4 chunks
     * from it; a chapel and its beard about 1).
     */
    static final int PORTAL_SITE_CLEARANCE = 6;

    public static final Codec<RiftChapelPlacement> CODEC = ExtraCodecs.validate(RecordCodecBuilder.mapCodec(instance -> placementCodec(instance)
            .and(instance.group(
                    Codec.intRange(0, 4096).fieldOf("spacing").forGetter(RandomSpreadStructurePlacement::spacing),
                    Codec.intRange(0, 4096).fieldOf("separation").forGetter(RandomSpreadStructurePlacement::separation),
                    RandomSpreadType.CODEC.optionalFieldOf("spread_type", RandomSpreadType.LINEAR).forGetter(RandomSpreadStructurePlacement::spreadType)))
            .apply(instance, RiftChapelPlacement::new)), RiftChapelPlacement::validate).codec();

    private RiftChapelPlacement(Vec3i locateOffset, FrequencyReductionMethod method, float frequency, int salt, Optional<ExclusionZone> exclusion,
                                int spacing, int separation, RandomSpreadType spread)
    {
        super(locateOffset, method, frequency, salt, exclusion, spacing, separation, spread);
    }

    private static DataResult<RiftChapelPlacement> validate(RiftChapelPlacement placement)
    {
        return placement.spacing() <= placement.separation() ? DataResult.error(() -> "Spacing has to be larger than separation")
                : DataResult.success(placement);
    }

    @Override
    protected boolean isPlacementChunk(ChunkGeneratorStructureState state, int chunkX, int chunkZ)
    {
        return super.isPlacementChunk(state, chunkX, chunkZ) && !nearPortalSite(state, chunkX, chunkZ);
    }

    private static boolean nearPortalSite(ChunkGeneratorStructureState state, int chunkX, int chunkZ)
    {
        for (Holder<StructureSet> set : state.possibleStructureSets())
        {
            if (set.is(PORTAL_SITE))
            {
                return state.hasStructureChunkInRange(set, chunkX, chunkZ, PORTAL_SITE_CLEARANCE);
            }
        }
        return false;
    }

    @Override
    public StructurePlacementType<?> type()
    {
        return ModStructures.RIFT_CHAPEL_PLACEMENT.get();
    }
}
