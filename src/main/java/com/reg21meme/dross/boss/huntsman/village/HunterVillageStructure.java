package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

import java.util.Optional;

/**
 * A hunter village in Rimefall (structure type {@code dross:hunter_village}; the user, October 23, 2026: "add the
 * villages to the Rimefall biomes"): the Huntsman's ({@code huntsman: true}, one in every group of 3 Rimefall patches)
 * or a regular one (in each of the other two half the time). Where it goes is the boss-site planner's
 * ({@link BossSites#HUNTSMAN_VILLAGE}, {@link BossSites#HUNTER_VILLAGE}; the spot in the patch: {@link RimefallVillages});
 * its structure set's placement ({@code dross:boss_site}) starts it only in its planned chunk, and its one piece
 * ({@link HunterVillagePiece}) builds it chunk by chunk with the land, in the "surface structures" step (the trees and
 * plants come after it and keep off it).
 */
public final class HunterVillageStructure extends Structure
{
    public static final Codec<HunterVillageStructure> CODEC = RecordCodecBuilder.create(i -> i.group(
            settingsCodec(i),
            Codec.BOOL.fieldOf("huntsman").forGetter(s -> s.huntsman)
    ).apply(i, HunterVillageStructure::new));
    /** The start's position is given this high (its biome is checked there): always the surface's biome. */
    private static final int SURFACE_BIOME_Y = 319;

    private final boolean huntsman;

    public HunterVillageStructure(StructureSettings settings, boolean huntsman)
    {
        super(settings);
        this.huntsman = huntsman;
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
        Optional<Site> site = sites.siteAtChunk(this.huntsman ? BossSites.HUNTSMAN_VILLAGE : BossSites.HUNTER_VILLAGE, chunk);
        if (site.isEmpty() || !(site.get().detail() instanceof RimefallVillages.VillagePlan plan))
        {
            return Optional.empty();
        }
        return Optional.of(new GenerationStub(new BlockPos(plan.ground().getX(), SURFACE_BIOME_Y, plan.ground().getZ()),
                builder -> builder.addPiece(new HunterVillagePiece(plan))));
    }

    @Override
    public StructureType<?> type()
    {
        return ModStructures.HUNTER_VILLAGE.get();
    }
}
