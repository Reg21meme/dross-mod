package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Places a hunter village ({@link HunterVillageDesigns}: a Sunken Warren, or a Huntsman's village with his lodge) from
 * its two structure templates: its middle on a ground block, its way in facing any way. First the village itself, then
 * its decorations, each kept with a {@link #DECOR_CHANCE} chance (the user's "1/2 chance for everything to spawn") and
 * the rest left out by {@link HunterDecorFilter}. The templates are made by tools/huntsman/villages.py with their way in
 * facing south; every decoration stands on the village or the ground on its own, so any mix of them is whole.
 */
public final class HunterVillages
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Each decoration's chance to be there. */
    public static final double DECOR_CHANCE = 0.5D;

    private HunterVillages() {}

    /** The village's template (without its decorations), or empty (and an error in the log) if it's missing. */
    public static Optional<StructureTemplate> template(ServerLevel level, HunterVillageDesigns.Design design)
    {
        return find(level, design, design.template());
    }

    /** Its decorations' template, or empty (and an error in the log) if it's missing. */
    public static Optional<StructureTemplate> decorTemplate(ServerLevel level, HunterVillageDesigns.Design design)
    {
        return find(level, design, design.decorTemplate());
    }

    private static Optional<StructureTemplate> find(ServerLevel level, HunterVillageDesigns.Design design,
                                                    net.minecraft.resources.ResourceLocation id)
    {
        Optional<StructureTemplate> found = level.getStructureManager().get(id);
        if (found.isEmpty())
        {
            LOGGER.error("Hunter village {} ({}): its template {} is missing", design.number(), design.name(), id);
        }
        return found;
    }

    /**
     * Places the design with its middle on {@code ground} (the ground block: the design's ground layer goes there) and
     * its way in facing {@code front}, each decoration there with a {@link #DECOR_CHANCE} chance. Nothing round it is
     * touched: prepare the ground first.
     *
     * @return how many decorations it kept, or -1 if a template is missing
     */
    public static int place(ServerLevel level, BlockPos ground, Direction front, HunterVillageDesigns.Design design)
    {
        return place(level, ground, front, design, level.getRandom(), DECOR_CHANCE);
    }

    /** As {@link #place(ServerLevel, BlockPos, Direction, HunterVillageDesigns.Design)}, with its own dice and chance. */
    public static int place(ServerLevel level, BlockPos ground, Direction front, HunterVillageDesigns.Design design,
                            RandomSource random, double chance)
    {
        return place(level, ground, front, design, roll(design, random, chance));
    }

    /** Rolls each of the design's decorations (in {@link HunterVillageDesigns.Design#decorations()}'s order): kept with
     * {@code chance}. */
    public static boolean[] roll(HunterVillageDesigns.Design design, RandomSource random, double chance)
    {
        List<BoundingBox> boxes = design.decorations();
        boolean[] keep = new boolean[boxes.size()];
        for (int i = 0; i < keep.length; i++)
        {
            keep[i] = random.nextDouble() < chance;
        }
        return keep;
    }

    /** Places the village and the decorations {@code keep} says (one flag each, in their order). Returns how many it
     * kept, or -1 if a template is missing. */
    public static int place(ServerLevel level, BlockPos ground, Direction front, HunterVillageDesigns.Design design,
                            boolean[] keep)
    {
        Optional<StructureTemplate> found = template(level, design);
        Optional<StructureTemplate> decor = decorTemplate(level, design);
        if (found.isEmpty() || decor.isEmpty())
        {
            return -1;
        }
        BlockPos origin = origin(ground, design);
        found.get().placeInWorld(level, origin, origin, settings(design, front), level.getRandom(), Block.UPDATE_CLIENTS);
        // the animals of its pens (a moose or two, the Huntsman's Rimehorn herd, as written), each once its land is
        // ready to keep it
        VillageAnimals.place(level, found.get(), origin, settings(design, front, true));
        List<BoundingBox> boxes = design.decorations();
        List<BoundingBox> dropped = new ArrayList<>();
        int kept = 0;
        for (int i = 0; i < boxes.size(); i++)
        {
            if (i < keep.length && keep[i])
            {
                kept++;
            }
            else
            {
                dropped.add(boxes.get(i));
            }
        }
        StructurePlaceSettings withDecor = settings(design, front).addProcessor(new HunterDecorFilter(dropped));
        decor.get().placeInWorld(level, origin, origin, withDecor, level.getRandom(), Block.UPDATE_CLIENTS);
        relight(level, footprint(ground, front, design));
        return kept;
    }

    /**
     * Has the game work out the light of every lamp and fire the village put down: a part of the world that was
     * empty until the village filled it can miss the light of the blocks put in it (a room read dark in a test, its
     * lanterns and fire there), and the rooms must be lit so nothing spawns in them.
     */
    static void relight(ServerLevel level, BoundingBox box)
    {
        LevelLightEngine light = level.getChunkSource().getLightEngine();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                for (int y = box.minY(); y <= box.maxY(); y++)
                {
                    if (level.getBlockState(pos.set(x, y, z)).getLightEmission(level, pos) > 0)
                    {
                        light.checkBlock(pos);
                    }
                }
            }
        }
    }

    /** How the templates are placed: turned round their middle column to face {@code front}, no entities. */
    public static StructurePlaceSettings settings(HunterVillageDesigns.Design design, Direction front)
    {
        return settings(design, front, false);
    }

    /** As {@link #settings(HunterVillageDesigns.Design, Direction)}; {@code animals}: with the template's animals (the
     * village's own template holds its pens' animals; their saved data is kept as written, not rolled again). */
    public static StructurePlaceSettings settings(HunterVillageDesigns.Design design, Direction front, boolean animals)
    {
        return new StructurePlaceSettings()
                .setRotation(rotationFor(front))
                .setRotationPivot(pivot(design))
                .setIgnoreEntities(!animals);
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

    /** The templates' corner (their first block) for a village whose middle is on {@code ground}. */
    public static BlockPos origin(BlockPos ground, HunterVillageDesigns.Design design)
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
