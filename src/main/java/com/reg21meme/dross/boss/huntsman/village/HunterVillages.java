package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Places a hunter village design (or the Huntsman's lodge) from its structure template ({@link HunterVillageDesigns}):
 * its middle on a ground block, its way in facing any way. The templates are made by tools/huntsman/villages.py with
 * their way in facing south.
 */
public final class HunterVillages
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private HunterVillages() {}

    /** The template, or empty (and an error in the log) if it's missing. */
    public static Optional<StructureTemplate> template(ServerLevel level, HunterVillageDesigns.Design design)
    {
        Optional<StructureTemplate> found = level.getStructureManager().get(design.template());
        if (found.isEmpty())
        {
            LOGGER.error("Hunter village {} ({}): its template {} is missing", design.number(), design.name(),
                    design.template());
        }
        return found;
    }

    /**
     * Places the design with its middle on {@code ground} (the ground block: the design's ground layer goes there) and
     * its way in facing {@code front}. Nothing round it is touched: prepare the ground first.
     *
     * @return false if the template is missing
     */
    public static boolean place(ServerLevel level, BlockPos ground, Direction front, HunterVillageDesigns.Design design)
    {
        Optional<StructureTemplate> found = template(level, design);
        if (found.isEmpty())
        {
            return false;
        }
        BlockPos origin = origin(ground, design);
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotationFor(front))
                .setRotationPivot(pivot(design))
                .setIgnoreEntities(true);
        found.get().placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        return true;
    }

    /** The box the design fills when placed like {@link #place}. */
    public static BoundingBox footprint(BlockPos ground, Direction front, HunterVillageDesigns.Design design)
    {
        BlockPos a = StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, rotationFor(front), pivot(design));
        BlockPos b = StructureTemplate.transform(new BlockPos(design.width() - 1, design.layers() - 1, design.depth() - 1),
                Mirror.NONE, rotationFor(front), pivot(design));
        return BoundingBox.fromCorners(a.offset(origin(ground, design)), b.offset(origin(ground, design)));
    }

    /** Where a block of the template (its own coordinates, layer 0 at the bottom) ends up. */
    public static BlockPos toWorld(BlockPos local, BlockPos ground, Direction front, HunterVillageDesigns.Design design)
    {
        return StructureTemplate.transform(local, Mirror.NONE, rotationFor(front), pivot(design)).offset(origin(ground, design));
    }

    private static BlockPos origin(BlockPos ground, HunterVillageDesigns.Design design)
    {
        return ground.offset(-design.width() / 2, -design.groundLayer(), -design.depth() / 2);
    }

    /** The template turns round its middle column, so the village's middle stays put whichever way it faces. */
    private static BlockPos pivot(HunterVillageDesigns.Design design)
    {
        return new BlockPos(design.width() / 2, 0, design.depth() / 2);
    }

    /** The turn that takes "the way in faces south" (how the templates are made) to "faces {@code front}". */
    public static Rotation rotationFor(Direction front)
    {
        return switch (front)
        {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }
}
