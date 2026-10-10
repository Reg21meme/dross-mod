package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.portal.DrossPortalShape;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.shrine.GroundFit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.BlockIgnoreProcessor;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * Places the user's castle template ({@code data/dross/structures/portal_castle.nbt}) at the portal site.
 *
 * <p>How to make the template (creative world, cheats on):
 * <ul>
 *   <li>Build the castle with exactly <b>one</b> Dross portal frame inside it: a nether-portal-shaped frame of
 *       {@code dross:dross_portal_frame} (creative "Dross" tab), opening 2x3 up to 21x21, standing along X or Z,
 *       with the opening left <b>empty</b> (air, not lit).</li>
 *   <li>Save it with a structure block (Save mode) as {@code dross:portal_castle}, including the floor layer.
 *       Structure blocks save at most 48x48x48 blocks in Minecraft 1.20.1, so a bigger castle needs several pieces.</li>
 *   <li>The file lands in {@code run/saves/<world>/generated/dross/structures/portal_castle.nbt}. Copy it to
 *       {@code src/main/resources/data/dross/structures/portal_castle.nbt} so every new world uses it.</li>
 * </ul>
 *
 * <p>The template is centred on the site column (the spot picked for the world). Its bottom layer replaces the top
 * layer of the ground (save the castle with its floor), on ground fitted the same way as the cathedral's (see
 * {@code GroundFit}). Before placing it, the template is checked for exactly one complete, empty frame; if that fails
 * a warning is logged and nothing is placed, so the caller builds the Fallen Cathedral instead.
 */
final class PortalCastle
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Extra air cleared above the template's top, so tree tops or overhangs don't hang over the castle. */
    private static final int CLEAR_ABOVE = 8;

    /** Nether-portal opening limits (the same as the portal area's DrossPortalShape). */
    private static final int MIN_WIDTH = DrossPortalShape.MIN_WIDTH;
    private static final int MAX_WIDTH = DrossPortalShape.MAX_WIDTH;
    private static final int MIN_HEIGHT = DrossPortalShape.MIN_HEIGHT;
    private static final int MAX_HEIGHT = DrossPortalShape.MAX_HEIGHT;

    /** True if the template has exactly one complete Dross frame with an empty opening (so it can be used). */
    static boolean isUsable(StructureTemplate template)
    {
        Vec3i size = template.getSize();
        return size.getX() > 0 && size.getY() > 0 && size.getZ() > 0
                && findFrames(template, BlockPos.ZERO, settings()).size() == 1;
    }

    /** Where the template's frame will be with the site column at {@code x}/{@code z} and the ground at {@code groundY}. */
    @Nullable
    static SiteFrame plannedFrame(StructureTemplate template, int x, int z, int groundY)
    {
        List<SiteFrame> frames = findFrames(template, origin(template.getSize(), x, z, groundY), settings());
        return frames.size() == 1 ? frames.get(0) : null;
    }

    /**
     * Checks the template, then places it centred on the site column on fitted ground and finds its frame.
     *
     * @param plan the ground plan made for the template's footprint there (by {@link SiteCheck}); its ground level is
     *             where the template's bottom layer goes
     * @return the frame, or null if the template doesn't have exactly one complete, empty frame (nothing placed)
     */
    @Nullable
    static SiteFrame place(ServerLevel level, StructureTemplate template, int x, int z, GroundFit.Plan plan)
    {
        Vec3i size = template.getSize();
        if (size.getX() <= 0 || size.getY() <= 0 || size.getZ() <= 0)
        {
            LOGGER.warn("Dross portal site: the castle template {} is empty; building the Fallen Cathedral instead.", PortalSite.CASTLE_TEMPLATE);
            return null;
        }

        int groundY = PortalSiteBuilder.clampY(level, plan.groundY, size.getY() + CLEAR_ABOVE);
        BlockPos origin = origin(size, x, z, groundY);
        StructurePlaceSettings settings = settings();

        // 1. Look for the frame in the template itself, before placing anything.
        List<SiteFrame> frames = findFrames(template, origin, settings);
        if (frames.isEmpty())
        {
            LOGGER.warn("Dross portal site: the castle template {} has no complete Dross portal frame with an empty opening "
                    + "(it needs exactly one unlit frame of dross:dross_portal_frame, opening 2x3 to 21x21 of air, along X or Z); "
                    + "building the Fallen Cathedral instead.", PortalSite.CASTLE_TEMPLATE);
            return null;
        }
        if (frames.size() > 1)
        {
            LOGGER.warn("Dross portal site: the castle template {} has {} complete Dross portal frames (it needs exactly one), "
                    + "corners at {}; building the Fallen Cathedral instead.",
                    PortalSite.CASTLE_TEMPLATE, frames.size(), frames.stream().map(f -> f.corner().toShortString() + " " + f.axis()).toList());
            return null;
        }
        SiteFrame frame = frames.get(0);
        BoundingBox expected = SiteLayout.forTemplate(size).footprintAt(x, z, 0, 0);
        BoundingBox planned = plan.survey.footprint();
        if (planned.minX() != expected.minX() || planned.maxX() != expected.maxX()
                || planned.minZ() != expected.minZ() || planned.maxZ() != expected.maxZ() || groundY != plan.groundY)
        {
            LOGGER.warn("Dross portal site: the ground plan doesn't match the castle template's footprint; building the Fallen "
                    + "Cathedral instead.");
            return null;
        }

        // 2. Fit the ground (whole trees removed, footprint levelled, staircase around it), then place it.
        GroundFit.Report ground = GroundFit.apply(level, plan, Math.min(origin.getY() + size.getY() - 1 + CLEAR_ABOVE,
                level.getMaxBuildHeight() - 1));
        template.placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
        GroundFit.snowFootprint(level, plan);

        // 3. Double-check with the portal area's own frame check (what the Rift Key uses).
        if (!isValidInWorld(level, frame))
        {
            LOGGER.warn("Dross portal site: the castle template {} was placed, but its frame at {} (axis {}) isn't a valid empty "
                    + "Dross frame in the world; building the Fallen Cathedral over it instead.",
                    PortalSite.CASTLE_TEMPLATE, frame.corner().toShortString(), frame.axis());
            return null;
        }
        LOGGER.info("Dross portal site: placed the castle template {} at {} (size {}x{}x{}); frame corner {} axis {} (opening {}x{}); "
                        + "ground: {}",
                PortalSite.CASTLE_TEMPLATE, origin.toShortString(), size.getX(), size.getY(), size.getZ(),
                frame.corner().toShortString(), frame.axis(), frame.openingWidth(), frame.openingHeight(), ground.summary());
        return frame;
    }

    /** The template's lowest corner: centred on the site column, its bottom layer replacing the top layer of the ground. */
    private static BlockPos origin(Vec3i size, int x, int z, int groundY)
    {
        return new BlockPos(x - size.getX() / 2, groundY, z - size.getZ() / 2);
    }

    private static StructurePlaceSettings settings()
    {
        return new StructurePlaceSettings()
                .addProcessor(BlockIgnoreProcessor.STRUCTURE_BLOCK) // a structure block saved inside the castle isn't placed
                .setKnownShape(true); // don't let blocks react to each other while the template is half placed
    }

    /** True if the portal area's frame check finds exactly this empty frame in the world. */
    static boolean isValidInWorld(ServerLevel level, SiteFrame frame)
    {
        BlockPos openingMin = frame.openingMin();
        Optional<DrossPortalShape> shape = DrossPortalShape.findEmptyPortalShape(level, openingMin, frame.axis());
        return shape.isPresent()
                && shape.get().getAxis() == frame.axis()
                && shape.get().getMinCorner().equals(openingMin)
                && shape.get().getWidth() == frame.openingWidth()
                && shape.get().getHeight() == frame.openingHeight();
    }

    /**
     * Every complete Dross frame with an all-air opening in the template, in world positions (as if placed at
     * {@code origin}). Same rules as a nether portal: corners don't matter.
     */
    private static List<SiteFrame> findFrames(StructureTemplate template, BlockPos origin, StructurePlaceSettings settings)
    {
        Set<BlockPos> frameBlocks = new HashSet<>();
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, ModBlocks.DROSS_PORTAL_FRAME.get()))
        {
            frameBlocks.add(info.pos().immutable());
        }
        Set<BlockPos> air = new HashSet<>();
        for (Block airBlock : new Block[] {Blocks.AIR, Blocks.CAVE_AIR, Blocks.VOID_AIR})
        {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, airBlock))
            {
                air.add(info.pos().immutable());
            }
        }

        List<SiteFrame> found = new ArrayList<>();
        for (BlockPos pos : air)
        {
            for (Direction.Axis axis : new Direction.Axis[] {Direction.Axis.X, Direction.Axis.Z})
            {
                SiteFrame frame = frameWithOpeningMinAt(pos, axis, frameBlocks, air);
                if (frame != null)
                {
                    found.add(frame);
                }
            }
        }
        return found;
    }

    /** The frame whose opening's lowest corner is {@code min}, if there is a complete one along {@code axis}. */
    @Nullable
    private static SiteFrame frameWithOpeningMinAt(BlockPos min, Direction.Axis axis, Set<BlockPos> frameBlocks, Set<BlockPos> air)
    {
        Direction along = SiteFrame.positive(axis);
        // The lowest corner of an opening has frame below it and frame on its low side.
        if (!frameBlocks.contains(min.below()) || !frameBlocks.contains(min.relative(along.getOpposite())))
        {
            return null;
        }

        int width = 0;
        while (width <= MAX_WIDTH && air.contains(min.relative(along, width)) && frameBlocks.contains(min.relative(along, width).below()))
        {
            width++;
        }
        int height = 0;
        while (height <= MAX_HEIGHT && air.contains(min.above(height)))
        {
            height++;
        }
        if (width < MIN_WIDTH || width > MAX_WIDTH || height < MIN_HEIGHT || height > MAX_HEIGHT)
        {
            return null;
        }

        for (int i = 0; i < width; i++)
        {
            BlockPos column = min.relative(along, i);
            if (!frameBlocks.contains(column.below()) || !frameBlocks.contains(column.above(height)))
            {
                return null; // bottom or top row incomplete
            }
            for (int j = 0; j < height; j++)
            {
                if (!air.contains(column.above(j)))
                {
                    return null; // opening not empty
                }
            }
        }
        for (int j = 0; j < height; j++)
        {
            if (!frameBlocks.contains(min.relative(along.getOpposite()).above(j)) || !frameBlocks.contains(min.relative(along, width).above(j)))
            {
                return null; // a side column is incomplete
            }
        }
        return SiteFrame.fromOpening(min, axis, width, height);
    }

    private PortalCastle() {}
}
