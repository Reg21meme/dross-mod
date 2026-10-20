package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

import java.util.Optional;

/**
 * Places one hunter igloo or one decoration ({@link HunterIglooDesigns}: a template each, made by
 * tools/huntsman/igloos.py, its doorway or front at the south) with its middle on a ground block and its front facing
 * any way: the igloo showcase uses it now, the villages will once the user has picked their igloos.
 */
public final class HunterIgloos
{
    private static final Logger LOGGER = LogUtils.getLogger();

    private HunterIgloos() {}

    /** One template's size and ground layer (an igloo's or a decoration's). */
    public record Piece(ResourceLocation template, int width, int depth, int layers, int groundLayer)
    {
        public static Piece of(HunterIglooDesigns.Igloo igloo)
        {
            return new Piece(igloo.template(), igloo.width(), igloo.depth(), igloo.layers(), igloo.groundLayer());
        }

        public static Piece of(HunterIglooDesigns.Decoration decoration)
        {
            return new Piece(decoration.template(), decoration.width(), decoration.depth(), decoration.layers(),
                    decoration.groundLayer());
        }
    }

    /** The template, or empty (and an error in the log) if it's missing. */
    public static Optional<StructureTemplate> template(ServerLevel level, Piece piece)
    {
        Optional<StructureTemplate> found = level.getStructureManager().get(piece.template());
        if (found.isEmpty())
        {
            LOGGER.error("Hunter igloo template {} is missing", piece.template());
        }
        return found;
    }

    /**
     * Places it with its middle on {@code ground} (the ground block: the template's ground layer goes there), its front
     * (an igloo's doorway) facing {@code front}, and has the game light it. Nothing round it is touched. Returns false if
     * its template is missing.
     */
    public static boolean place(ServerLevel level, Piece piece, BlockPos ground, Direction front)
    {
        Optional<StructureTemplate> found = template(level, piece);
        if (found.isEmpty())
        {
            return false;
        }
        BlockPos origin = origin(ground, piece);
        found.get().placeInWorld(level, origin, origin, settings(piece, front), level.getRandom(), Block.UPDATE_CLIENTS);
        HunterVillages.relight(level, footprint(ground, front, piece));
        return true;
    }

    public static StructurePlaceSettings settings(Piece piece, Direction front)
    {
        return new StructurePlaceSettings()
                .setRotation(HunterVillages.rotationFor(front))
                .setRotationPivot(pivot(piece))
                .setIgnoreEntities(true);
    }

    /** The box it fills when placed like {@link #place}. */
    public static BoundingBox footprint(BlockPos ground, Direction front, Piece piece)
    {
        BlockPos a = StructureTemplate.transform(BlockPos.ZERO, Mirror.NONE, HunterVillages.rotationFor(front), pivot(piece));
        BlockPos b = StructureTemplate.transform(new BlockPos(piece.width() - 1, piece.layers() - 1, piece.depth() - 1),
                Mirror.NONE, HunterVillages.rotationFor(front), pivot(piece));
        return BoundingBox.fromCorners(a.offset(origin(ground, piece)), b.offset(origin(ground, piece)));
    }

    /** Where a block of the template (its own coordinates, layer 0 at the bottom) ends up. */
    public static BlockPos toWorld(BlockPos local, BlockPos ground, Direction front, Piece piece)
    {
        return StructureTemplate.transform(local, Mirror.NONE, HunterVillages.rotationFor(front), pivot(piece))
                .offset(origin(ground, piece));
    }

    /** The template's corner (its first block) for a piece whose middle is on {@code ground}. */
    public static BlockPos origin(BlockPos ground, Piece piece)
    {
        return ground.offset(-piece.width() / 2, -piece.groundLayer(), -piece.depth() / 2);
    }

    private static BlockPos pivot(Piece piece)
    {
        return new BlockPos(piece.width() / 2, 0, piece.depth() / 2);
    }
}
