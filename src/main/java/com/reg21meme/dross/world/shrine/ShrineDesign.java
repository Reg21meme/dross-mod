package com.reg21meme.dross.world.shrine;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * One design for a grand ruined portal shrine (see {@link ShrineDesigns} for all of them). No. 1, the Fallen
 * Cathedral, is the Overworld portal site (the castle).
 * <p>
 * Each design is a structure template, {@code data/dross/structures/shrine/<id>.nbt}, made by the generator in
 * {@code tools/shrines/} (which also draws its preview images in {@code shrine-previews/}). Every template:
 * <ul>
 *   <li>has its front (the way in) on its south side, and is turned to face wherever it's built;</li>
 *   <li>has its ground at layer {@link #groundLayer()}: the layers below it reach a little underground (pools,
 *       foundations), and the cells it leaves empty keep whatever the world has there;</li>
 *   <li>contains exactly one complete, unlit Dross portal frame with an all-air opening (the castle code finds the
 *       frame the same way).</li>
 * </ul>
 * Three ways to build one:
 * <ul>
 *   <li>{@link #build}: by its <b>center</b> (the ground block in the middle of the footprint), at a ground level you
 *       choose (the showcase uses this).</li>
 *   <li>{@link #buildWithFrameAt}: with its frame <b>opening on a given column</b>, at the ground level the natural
 *       terrain there calls for (the Dross hub uses this, and the portal site as its very last resort).</li>
 *   <li>{@link #buildFitted}: on ground that's already nearly flat, with a {@link GroundFit.Plan} (the portal site uses
 *       this): whole trees removed where they touch, the footprint levelled by a block or two, and a one-block-per-step
 *       staircase back to the natural terrain.</li>
 * </ul>
 * The first two prepare the ground with {@link ShrineGround}: cut down and filled up to the ground level, sloped back
 * to the natural terrain around the footprint, and sealed against water and lava, so the design never floats or
 * floods, on any terrain.
 */
public final class ShrineDesign
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Blocks of air cleared above the design's top, so tree tops or overhangs don't hang over it. */
    private static final int CLEAR_ABOVE = 4;
    /**
     * How far (in blocks) around the footprint building a design may change the ground (the slopes back to the
     * natural terrain). Load the chunks this far out first if the build mustn't wait for chunks.
     */
    public static final int GROUND_MARGIN = ShrineGround.BLEND_MARGIN;

    private final int number;
    private final String id;
    private final String name;
    private final String mood;
    private final int width;
    private final int depth;
    private final int layers;
    private final int groundLayer;
    private final int height;
    private final BlockPos openingMin;
    private final Direction.Axis frameAxis;
    private final int openingWidth;
    private final int openingHeight;
    private final ResourceLocation template;

    /**
     * @param width       blocks across the front (x in the template)
     * @param depth       blocks from the back to the front (z in the template)
     * @param layers      the template's height in layers, including the ones below the ground
     * @param groundLayer the template layer that sits at the ground
     * @param height      how many blocks it rises above the ground
     * @param openingMin  the frame opening's lowest corner, in template coordinates (unrotated)
     */
    ShrineDesign(int number, String id, String name, String mood, int width, int depth, int layers, int groundLayer,
                 int height, BlockPos openingMin, Direction.Axis frameAxis, int openingWidth, int openingHeight)
    {
        this.number = number;
        this.id = id;
        this.name = name;
        this.mood = mood;
        this.width = width;
        this.depth = depth;
        this.layers = layers;
        this.groundLayer = groundLayer;
        this.height = height;
        this.openingMin = openingMin;
        this.frameAxis = frameAxis;
        this.openingWidth = openingWidth;
        this.openingHeight = openingHeight;
        this.template = new ResourceLocation(Dross.MODID, "shrine/" + id);
    }

    /** The design with this template id (for example {@code "fallen_cathedral"} or {@code "shattered_spire"}). */
    public static Optional<ShrineDesign> byId(String id)
    {
        return ShrineDesigns.ALL.stream().filter(design -> design.id.equals(id)).findFirst();
    }

    /** The design with this number, 1 to 10. */
    public static Optional<ShrineDesign> byNumber(int number)
    {
        return ShrineDesigns.ALL.stream().filter(design -> design.number == number).findFirst();
    }

    /** 1 to 10. */
    public int number()
    {
        return number;
    }

    /** The template's file name, for example {@code fallen_cathedral}. */
    public String id()
    {
        return id;
    }

    /** Short enough for one line of a sign. */
    public String name()
    {
        return name;
    }

    /** A word or two about it, short enough for one line of a sign. */
    public String mood()
    {
        return mood;
    }

    /** Blocks across the front. */
    public int width()
    {
        return width;
    }

    /** Blocks from the back to the front. */
    public int depth()
    {
        return depth;
    }

    /** How many blocks it rises above the ground. */
    public int height()
    {
        return height;
    }

    /** The template layer that sits at the ground (the layers below it go underground). */
    public int groundLayer()
    {
        return groundLayer;
    }

    /** The structure template, {@code dross:shrine/<id>}. */
    public ResourceLocation template()
    {
        return template;
    }

    /** The frame's opening (unrotated, in template coordinates): lowest corner, axis, width and height. */
    public BlockPos openingMin()
    {
        return openingMin;
    }

    /** The frame's axis in the template (unrotated). For the axis in the world, use {@link #frameAxis(Direction)}. */
    public Direction.Axis frameAxis()
    {
        return frameAxis;
    }

    /** How many blocks wide the frame's opening is (along the frame). */
    public int openingWidth()
    {
        return openingWidth;
    }

    /** How many blocks tall the frame's opening is. */
    public int openingHeight()
    {
        return openingHeight;
    }

    /** Everything the design covers when built at {@code center} facing {@code facing}: underground to its top. */
    public BoundingBox footprint(BlockPos center, Direction facing)
    {
        return BoundingBox.fromCorners(
                toWorld(center, facing, new BlockPos(0, 0, 0)),
                toWorld(center, facing, new BlockPos(width - 1, layers - 1, depth - 1)));
    }

    /**
     * The ground block just outside the footprint, straight in front of the way in. Every design's way in lines up
     * with its frame, which isn't always the template's middle column (the cathedral's churchyard, for one, makes
     * it lopsided), so this follows the frame.
     */
    public BlockPos entrance(BlockPos center, Direction facing)
    {
        int column = frameAxis == Direction.Axis.X ? openingMin.getX() + (openingWidth - 1) / 2 : width / 2;
        return toWorld(center, facing, new BlockPos(column, groundLayer, depth));
    }

    /** The middle block of the frame opening's bottom row, in the world. */
    public BlockPos frameOpeningCenter(BlockPos center, Direction facing)
    {
        BlockPos mid = frameAxis == Direction.Axis.X
                ? openingMin.offset((openingWidth - 1) / 2, 0, 0)
                : openingMin.offset(0, 0, (openingWidth - 1) / 2);
        return toWorld(center, facing, mid);
    }

    /** The frame opening's block with the lowest X, Y and Z, in the world. */
    public BlockPos frameOpeningMin(BlockPos center, Direction facing)
    {
        BlockPos far = frameAxis == Direction.Axis.X
                ? openingMin.offset(openingWidth - 1, openingHeight - 1, 0)
                : openingMin.offset(0, openingHeight - 1, openingWidth - 1);
        BlockPos a = toWorld(center, facing, openingMin);
        BlockPos b = toWorld(center, facing, far);
        return new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
    }

    /** The frame's axis in the world when the design faces {@code facing} (turning to face east or west swaps X and Z). */
    public Direction.Axis frameAxis(Direction facing)
    {
        if (horizontal(facing).getAxis() == Direction.Axis.X)
        {
            return frameAxis == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X;
        }
        return frameAxis;
    }

    /**
     * The center to build at so that the frame opening's middle column is at {@code x}/{@code z}, with the ground
     * at {@code groundY}. Pure arithmetic: nothing in the world is looked at.
     */
    public BlockPos centerForFrameAt(int x, int z, int groundY, Direction facing)
    {
        BlockPos probe = new BlockPos(0, groundY, 0);
        BlockPos opening = frameOpeningCenter(probe, facing);
        return new BlockPos(x - opening.getX(), groundY, z - opening.getZ());
    }

    /**
     * The ground level for the design built with its frame opening's middle column at {@code x}/{@code z}: the median
     * height of the natural ground under the whole footprint (trees, plants and ice ignored, water counts at its top),
     * or one block above the water if most of the footprint is water. On flat ground it's simply the ground. Loads
     * (and if needed generates) the chunks under the footprint.
     */
    public int groundLevelForFrameAt(ServerLevel level, int x, int z, Direction facing)
    {
        return chooseGround(level, x, z, horizontal(facing)).groundY();
    }

    /**
     * Builds the design on the natural terrain with its frame opening's middle column at {@code x}/{@code z}: picks
     * the ground level ({@link #groundLevelForFrameAt}), prepares the ground (cut, fill, slopes, sealing) and places
     * the template. Server thread only; it can take a few seconds (chunks are loaded or generated as needed).
     *
     * @return where everything ended up (the frame's opening and axis, to light it or store it), or empty (and an
     *         error in the log) if the template file is missing
     */
    public Optional<Built> buildWithFrameAt(ServerLevel level, int x, int z, Direction facing)
    {
        Direction front = horizontal(facing);
        ShrineGround.GroundChoice ground = chooseGround(level, x, z, front);
        BlockPos center = centerForFrameAt(x, z, ground.groundY(), front);
        LOGGER.info("Shrine design {} ({}): frame column {} {}, {}", number, name, x, z, ground.summary());
        if (!build(level, center, front))
        {
            return Optional.empty();
        }
        return Optional.of(built(center, front));
    }

    /** Where the frame and the rest end up when the design is built at {@code center} facing {@code facing}. */
    public Built built(BlockPos center, Direction facing)
    {
        Direction front = horizontal(facing);
        return new Built(this, center.immutable(), front, footprint(center, front), frameOpeningMin(center, front),
                frameAxis(front), openingWidth, openingHeight);
    }

    /**
     * Builds the design at {@code center} (a ground block), its front facing {@code facing}: prepares the ground at
     * {@code center}'s Y (everything above it inside the footprint is cleared, dips under it are filled down to solid
     * ground, the band around it is sloped back to the natural terrain, water and lava next to the cleared space are
     * sealed), then places the template. On flat ground only what stands inside the footprint is cleared.
     *
     * @return false (and logs an error) if the template file is missing
     */
    public boolean build(ServerLevel level, BlockPos center, Direction facing)
    {
        Optional<StructureTemplate> found = level.getStructureManager().get(template);
        if (found.isEmpty())
        {
            LOGGER.error("Shrine design {} ({}): its template {} is missing", number, name, template);
            return false;
        }
        Direction front = horizontal(facing);
        BoundingBox box = footprint(center, front);
        int clearTop = Math.min(box.maxY() + CLEAR_ABOVE, level.getMaxBuildHeight() - 1);
        ShrineGround.Report ground = ShrineGround.prepare(level, box, center.getY(), clearTop);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotationFor(front))
                .setRotationPivot(pivot())
                .setIgnoreEntities(true);
        BlockPos origin = center.offset(-width / 2, -groundLayer, -depth / 2);
        found.get().placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        LOGGER.info("Shrine design {} ({}) built at {} facing {} ({} x {} x {}); ground: {}",
                number, name, center.toShortString(), front.getName(), width, depth, height, ground.summary());
        return true;
    }

    /**
     * The footprint (seen from above; Y from the underground layers to the top) of the design built with its frame
     * opening's middle column at {@code x}/{@code z}, its ground at {@code groundY}. Pure arithmetic.
     */
    public BoundingBox footprintForFrameAt(int x, int z, int groundY, Direction facing)
    {
        Direction front = horizontal(facing);
        return footprint(centerForFrameAt(x, z, groundY, front), front);
    }

    /**
     * Builds the design on ground that is already nearly flat, gently (the portal site uses this): the ground is
     * prepared by a {@link GroundFit.Plan} made for this footprint (whole trees removed where they touch, the footprint
     * levelled at {@code center}'s Y, a one-block-per-step staircase back to the natural terrain), then the template is
     * placed, then open natural ground inside it gets snow in a cold biome. Server thread only.
     *
     * @return the ground report, or empty (and an error in the log) if the template file is missing or the plan was made
     *         for another footprint or ground level
     */
    public Optional<GroundFit.Report> buildFitted(ServerLevel level, BlockPos center, Direction facing, GroundFit.Plan plan)
    {
        Optional<StructureTemplate> found = level.getStructureManager().get(template);
        if (found.isEmpty())
        {
            LOGGER.error("Shrine design {} ({}): its template {} is missing", number, name, template);
            return Optional.empty();
        }
        Direction front = horizontal(facing);
        BoundingBox box = footprint(center, front);
        BoundingBox planned = plan.survey.footprint();
        if (box.minX() != planned.minX() || box.maxX() != planned.maxX() || box.minZ() != planned.minZ()
                || box.maxZ() != planned.maxZ() || center.getY() != plan.groundY)
        {
            LOGGER.error("Shrine design {} ({}): the ground plan is for footprint {} at ground {}, not {} at ground {}; not built",
                    number, name, planned, plan.groundY, box, center.getY());
            return Optional.empty();
        }
        int clearTop = Math.min(box.maxY() + CLEAR_ABOVE, level.getMaxBuildHeight() - 1);
        GroundFit.Report ground = GroundFit.apply(level, plan, clearTop);

        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotationFor(front))
                .setRotationPivot(pivot())
                .setIgnoreEntities(true);
        BlockPos origin = center.offset(-width / 2, -groundLayer, -depth / 2);
        found.get().placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        int snowed = GroundFit.snowFootprint(level, plan);
        LOGGER.info("Shrine design {} ({}) built at {} facing {} ({} x {} x {}) on fitted ground: {}; {} snow layers in the open inside",
                number, name, center.toShortString(), front.getName(), width, depth, height, ground.summary(), snowed);
        return Optional.of(ground);
    }

    private ShrineGround.GroundChoice chooseGround(ServerLevel level, int x, int z, Direction front)
    {
        BoundingBox box = footprint(centerForFrameAt(x, z, 0, front), front);
        // Keep the underground layers above the world's bottom, and the top (plus the cleared air) below its roof.
        int minAllowed = level.getMinBuildHeight() + groundLayer + 1;
        int maxAllowed = level.getMaxBuildHeight() - 1 - CLEAR_ABOVE - (layers - 1 - groundLayer);
        return ShrineGround.chooseGroundY(level, box.minX(), box.minZ(), box.maxX(), box.maxZ(), minAllowed, maxAllowed);
    }

    /** Template position (unrotated) to world position, for the design built at {@code center} facing {@code facing}. */
    private BlockPos toWorld(BlockPos center, Direction facing, BlockPos local)
    {
        BlockPos origin = center.offset(-width / 2, -groundLayer, -depth / 2);
        return StructureTemplate.transform(local, Mirror.NONE, rotationFor(horizontal(facing)), pivot()).offset(origin);
    }

    /** The template turns around its middle column, so the design's center stays put whichever way it faces. */
    private BlockPos pivot()
    {
        return new BlockPos(width / 2, 0, depth / 2);
    }

    /** The turn that takes "front faces south" (how templates are made) to "front faces {@code facing}". */
    static Rotation rotationFor(Direction facing)
    {
        return switch (facing)
        {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    private static Direction horizontal(Direction facing)
    {
        return facing.getAxis().isHorizontal() ? facing : Direction.SOUTH;
    }

    /**
     * Where a built design ended up.
     *
     * @param design        the design
     * @param center        its center: the ground block in the middle of the footprint (its Y is the ground level)
     * @param facing        the way its front faces
     * @param footprint     everything it covers, from its underground layers to its top
     * @param openingMin    the frame opening's block with the lowest X, Y and Z
     * @param frameAxis     the direction the frame runs along in the world ({@code X}: you walk through it along Z)
     * @param openingWidth  how many blocks wide the opening is (along {@code frameAxis})
     * @param openingHeight how many blocks tall the opening is
     */
    public record Built(ShrineDesign design, BlockPos center, Direction facing, BoundingBox footprint, BlockPos openingMin,
                        Direction.Axis frameAxis, int openingWidth, int openingHeight)
    {
        /** The middle block of the opening's bottom row (for an even width, the one with the lower X or Z). */
        public BlockPos openingCenter()
        {
            return openingMin.relative(Direction.fromAxisAndDirection(frameAxis, Direction.AxisDirection.POSITIVE), (openingWidth - 1) / 2);
        }

        /** The frame's bottom corner block with the lowest X and Z (one below and one before the opening). */
        public BlockPos frameCorner()
        {
            return openingMin.below().relative(Direction.fromAxisAndDirection(frameAxis, Direction.AxisDirection.NEGATIVE));
        }
    }
}
