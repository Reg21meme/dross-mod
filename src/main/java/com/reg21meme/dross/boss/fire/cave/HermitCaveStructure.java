package com.reg21meme.dross.boss.fire.cave;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.registry.ModStructures;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureType;

/**
 * A hermit crab cave in the Cinder Wastes (structure type {@code dross:hermit_cave}; the user, October 31, 2026: "1-2
 * caves per Cinder Wastes area"): one of {@link HermitCaveDesigns}' designs, where {@link HermitCaveSites} planned it (a hillside one into a hill, an underground one on flat land). Its
 * structure set's placement ({@link HermitCavePlacement}) starts it only in its planned chunk; its one piece
 * ({@link HermitCavePiece}) places its template there with the land: its ground layer at the height of the land under its
 * way in (the generator's own height, before any feature), its way in facing the planned way. Built in the "surface
 * structures" step, after the Cinder Wastes' lava pools, spires and vents (so none of them can break into it), before
 * its trees and plants.
 */
public final class HermitCaveStructure extends Structure
{
    public static final Codec<HermitCaveStructure> CODEC = simpleCodec(HermitCaveStructure::new);

    public HermitCaveStructure(StructureSettings settings)
    {
        super(settings);
    }

    @Override
    protected Optional<GenerationStub> findGenerationPoint(GenerationContext context)
    {
        BossSites sites = BossSites.forWorldgen(context.randomState());
        if (sites == null)
        {
            return Optional.empty();
        }
        ChunkPos chunk = context.chunkPos();
        Optional<HermitCaveSites.CavePlan> found = HermitCaveSites.caveAt(sites, chunk);
        if (found.isEmpty())
        {
            return Optional.empty();
        }
        HermitCaveSites.CavePlan plan = found.get();
        HermitCaveDesigns.Cave cave = plan.cave();
        BlockPos middle = plan.middle();
        BlockPos ground = ground(plan, context.chunkGenerator(), context.heightAccessor(), context.randomState());
        return Optional.of(new GenerationStub(new BlockPos(middle.getX(), HermitCaveTuning.SURFACE_BIOME_Y, middle.getZ()),
                builder -> builder.addPiece(new HermitCavePiece(context.structureTemplateManager(), cave, ground, plan.front()))));
    }

    /**
     * Where a planned cave's middle ground block goes: its middle column, at the height of the land under its way in (the
     * generator's own height there, before any feature).
     */
    public static BlockPos ground(HermitCaveSites.CavePlan plan, ChunkGenerator generator, LevelHeightAccessor heights,
                                  RandomState random)
    {
        BlockPos door = HermitCaves.door(plan.middle().atY(0), plan.front(), plan.cave());
        int groundY = generator.getBaseHeight(door.getX(), door.getZ(), Heightmap.Types.OCEAN_FLOOR_WG, heights, random) - 1;
        return plan.middle().atY(groundY);
    }

    @Override
    public StructureType<?> type()
    {
        return ModStructures.HERMIT_CAVE.get();
    }
}
