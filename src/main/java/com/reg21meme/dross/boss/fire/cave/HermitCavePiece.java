package com.reg21meme.dross.boss.fire.cave;

import com.reg21meme.dross.registry.ModStructures;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.StructureManager;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.TemplateStructurePiece;
import net.minecraft.world.level.levelgen.structure.pieces.StructurePieceSerializationContext;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplateManager;

/**
 * A hermit crab cave's one piece (structure piece {@code dross:hermit_cave}): its template ({@link HermitCaveDesigns}),
 * placed chunk by chunk like any template structure, turned round its middle column so its way in faces the planned way
 * (exactly as {@link HermitCaves#place} places it in the showcase). Saved with its template, corner and turn.
 */
public final class HermitCavePiece extends TemplateStructurePiece
{
    public HermitCavePiece(StructureTemplateManager manager, HermitCaveDesigns.Cave cave, BlockPos ground, Direction front)
    {
        super(ModStructures.HERMIT_CAVE_PIECE.get(), 0, manager, cave.template(), cave.template().toString(),
                makeSettings(HermitCaves.rotationFor(front), cave.template()), HermitCaves.origin(ground, cave));
    }

    public HermitCavePiece(StructureTemplateManager manager, CompoundTag tag)
    {
        super(ModStructures.HERMIT_CAVE_PIECE.get(), tag, manager,
                location -> makeSettings(Rotation.valueOf(tag.getString("Rot")), location));
    }

    private static StructurePlaceSettings makeSettings(Rotation rotation, ResourceLocation location)
    {
        HermitCaveDesigns.Cave cave = byTemplate(location);
        return new StructurePlaceSettings()
                .setRotation(rotation)
                .setRotationPivot(HermitCaves.pivot(cave))
                .setIgnoreEntities(true)
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK);
    }

    /** The cave a template belongs to (the first one if it's unknown: they're all the same size). */
    private static HermitCaveDesigns.Cave byTemplate(ResourceLocation location)
    {
        for (HermitCaveDesigns.Cave cave : HermitCaveDesigns.CAVES)
        {
            if (cave.template().equals(location))
            {
                return cave;
            }
        }
        return HermitCaveDesigns.CAVES.get(0);
    }

    @Override
    protected void addAdditionalSaveData(StructurePieceSerializationContext context, CompoundTag tag)
    {
        super.addAdditionalSaveData(context, tag);
        tag.putString("Rot", this.placeSettings.getRotation().name());
    }

    /** The cave, then its Cinder Hermits on its crab spots in this chunk (the user, October 31: the caves are their homes). */
    @Override
    public void postProcess(WorldGenLevel level, StructureManager structures, ChunkGenerator generator, RandomSource random,
                            BoundingBox box, ChunkPos chunk, BlockPos pos)
    {
        super.postProcess(level, structures, generator, random, box, chunk, pos);
        HermitCaveDesigns.Cave cave = byTemplate(new ResourceLocation(this.templateName));
        HermitCaves.spawnCrabs(level, cave.crabs().stream()
                .map(local -> StructureTemplate.calculateRelativePosition(this.placeSettings, local).offset(this.templatePosition))
                .toList(), box);
    }

    @Override
    protected void handleDataMarker(String name, BlockPos pos, ServerLevelAccessor level, RandomSource random, BoundingBox box)
    {
        // (the caves have no data markers)
    }
}
