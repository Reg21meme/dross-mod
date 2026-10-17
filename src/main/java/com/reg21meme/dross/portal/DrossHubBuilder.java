package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.shrine.GroundFit;
import com.reg21meme.dross.world.shrine.ShrineDesign;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.IntPredicate;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import org.slf4j.Logger;

/**
 * Builds the hub at Dross 0,0, once per world (see {@link DrossHub} for when, and {@link DrossHubPlacer} for how it's
 * spread over several server ticks). Which hub, first match wins:
 * <ol>
 *   <li>The user's own design, if the template {@code data/dross/structures/dross_hub.nbt} exists: centred on
 *       {@link DrossHub#CENTER}, on fitted ground (below), with its Dross frame found and lit.</li>
 *   <li>Otherwise the <b>Shattered Spire</b> (shrine design No. 2), its frame opening (3 wide, 5 tall, along X) on the
 *       centre and its gate facing south, on fitted ground. Its frame is lit: it's the exit portal.</li>
 *   <li>Only if the spire can't be built or lit (an error is logged): the <b>placeholder</b>, an 11x11 stone platform
 *       with a lit 4x5 exit frame (opening 2x3) in its middle.</li>
 * </ol>
 * <b>Fitted ground</b> (world-builder's {@link GroundFit}, the same as the Overworld's cathedral): the ground under and
 * {@value DrossHub#HUB_BLEND_MARGIN} blocks around the footprint is surveyed (trees, plants, Stormvine, huge mushrooms
 * and snow are looked through), the footprint is levelled at the natural ground's median height, the edges become a
 * staircase back to the natural terrain (at most one block per step), and trees, huge mushrooms and the like that touch
 * it are removed whole. The plan, what was removed and the time taken are logged, with a warning if the ground was rough.
 * <p>
 * A {@link Build} does it a step at a time, one per server tick when it's spread out: survey the ground, plan it, then
 * build the spire in short steps (felling, levelling, the template...) and light its frame. Only the build steps change
 * the world.
 */
final class DrossHubBuilder
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // Fitting the hub onto the terrain (the blend margin and water clearance are in DrossHub)
    /** Warn in the log if the staircase changes one column by more than this many blocks. */
    private static final int WARN_RING_CHANGE = 8;
    /** Warn in the log if a dip deeper than this (blocks) under the footprint has to be filled. */
    private static final int WARN_DIP = 8;
    /**
     * The chunks this many blocks past the surveyed ring are loaded before building too: removing a tree that touches
     * the hub follows its leaves out past the surveyed ring, and those chunks should be ready as well.
     */
    static final int LOAD_PAST_SURVEY = 16;

    // The user's own template
    /**
     * The template's bottom layer goes this many blocks below the first air block above the ground.
     * -1 means the template's bottom layer replaces the top layer of the ground (save the template with its floor).
     */
    private static final int TEMPLATE_Y_OFFSET = -1;
    /** Air cleared above the user's template (blocks), so tree tops or overhangs don't hang over it. */
    private static final int TEMPLATE_CLEAR_ABOVE = 4;

    // The Shattered Spire
    /** The shrine design built as the hub when the user has no hub template of their own: No. 2, the Shattered Spire. */
    static final String SPIRE_DESIGN_ID = "shattered_spire";
    /** The way the spire's gate faces. South (the design as drawn, unturned) keeps its frame along X. */
    static final Direction SPIRE_FACING = Direction.SOUTH;

    // Placeholder hub
    /** The platform reaches this many blocks out from the centre on each side (5 -> 11x11). */
    private static final int PLATFORM_RADIUS = 5;
    /** Air cleared above the platform. */
    private static final int CLEAR_HEIGHT = 6;
    /** Under the platform, fill up to this many blocks of air/liquid so it never floats over a drop. */
    private static final int SUPPORT_DEPTH = 3;
    /** Exit portal frame, corners included: 4 wide x 5 tall (opening 2x3, like the smallest nether portal). */
    private static final int EXIT_FRAME_WIDTH = 4;
    private static final int EXIT_FRAME_HEIGHT = 5;
    /**
     * The staircase around a placeholder built next to something that already stands (a spire whose frame failed, or
     * the user's template without a frame): none, so it never cuts into that.
     */
    private static final int BESIDE_BLEND_MARGIN = 0;
    private static final BlockState PLATFORM = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState PLATFORM_BORDER = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

    /**
     * What was built.
     *
     * @param exit      the lit exit portal, or null if the hub has none (only if even the placeholder failed)
     * @param kind      which hub it is (null only if nothing was built)
     * @param footprint the box it covers (null only if nothing was built)
     * @param groundY   the Y of the ground block it stands on (its levelled ground)
     * @param fitted    true if the ground under and around {@code footprint} was fitted (levelled, with a staircase)
     */
    record Result(@Nullable PortalOpening exit, @Nullable DrossHub.Kind kind, @Nullable BoundingBox footprint, int groundY, boolean fitted)
    {
        /** Building failed completely: no hub, no exit portal. */
        static Result failed()
        {
            return new Result(null, null, null, 0, false);
        }
    }

    /** A placeholder that was built: its exit portal (null if its frame isn't complete), its box and its floor level. */
    private record Placed(@Nullable PortalOpening exit, BoundingBox box, int floorY) {}

    private DrossHubBuilder() {}

    // ------------------------------------------------------------------ one build, a step at a time

    /**
     * One hub build, a step at a time ({@link #step}, one per server tick when it's spread out; {@link DrossHubPlacer}
     * loads the chunks first, in the background):
     * <ol>
     *   <li><b>survey</b> the ground under and around the footprint (reads blocks, changes nothing);</li>
     *   <li><b>plan</b> it: the ground level and the staircase (changes nothing);</li>
     *   <li><b>build</b>: the spire is built a step at a time (world-builder's {@link ShrineDesign.FittedBuild}: felling the
     *       trees that touch, in steps of at most about {@link GroundFit#STEP_MILLIS} ms, then levelling, sealing, snow,
     *       the template in one step, snow inside), then its <b>frame</b> is lit on a step of its own. The user's template
     *       and the placeholder are built in one step. Or a fallback if anything goes wrong.</li>
     * </ol>
     * The world only changes in the build steps, from the first tree removed on ({@link #changedWorld}): a build that has
     * got that far must be finished, never dropped. Which hub it builds is decided when it's made. Server thread only.
     */
    static final class Build
    {
        private final ServerLevel dross;
        /** What's planned: the user's template, the spire, or (if the spire's design or template is missing) the placeholder. */
        private final DrossHub.Kind planned;
        @Nullable
        private final StructureTemplate template;
        @Nullable
        private final ShrineDesign spire;
        /** The footprint's columns (only X and Z count: the ground level isn't known yet). */
        private final BoundingBox columns;
        private final IntPredicate fits;
        private final String what;
        @Nullable
        private GroundFit.Plan plan;
        @Nullable
        private GroundFit.Survey survey;
        /** Surveying or planning failed: the placeholder is built at the centre instead. */
        private boolean groundFailed;
        /** The spire being built a step at a time, once its build has started. */
        @Nullable
        private ShrineDesign.FittedBuild fitted;
        @Nullable
        private BlockPos spireCenter;
        @Nullable
        private Result result;
        private int stepsDone;
        private long millis;
        private long longestStep = -1L;
        private String longestStepName = "";
        /** Each kind of step's total time (ms), how many steps (ticks) and the longest one, in the order they ran. */
        private final Map<String, long[]> stepTimes = new LinkedHashMap<>();

        Build(ServerLevel dross)
        {
            this.dross = dross;
            Optional<StructureTemplate> own = dross.getStructureManager().get(DrossHub.TEMPLATE);
            Optional<ShrineDesign> design = ShrineDesign.byId(SPIRE_DESIGN_ID);
            if (own.isPresent())
            {
                // 1. The user's own hub design.
                Vec3i size = own.get().getSize();
                int minX = DrossHub.CENTER.getX() - size.getX() / 2;
                int minZ = DrossHub.CENTER.getZ() - size.getZ() / 2;
                this.planned = DrossHub.Kind.TEMPLATE;
                this.template = own.get();
                this.spire = null;
                this.columns = new BoundingBox(minX, 0, minZ, minX + size.getX() - 1, 0, minZ + size.getZ() - 1);
                this.fits = y -> templateFits(dross, y, size);
                this.what = "the " + DrossHub.TEMPLATE + " template";
            }
            else if (design.isPresent() && dross.getStructureManager().get(design.get().template()).isPresent())
            {
                // 2. The Shattered Spire. Only X and Z of its box matter for the survey: the ground level isn't known yet.
                ShrineDesign found = design.get();
                this.planned = DrossHub.Kind.SPIRE;
                this.template = null;
                this.spire = found;
                this.columns = found.footprintForFrameAt(DrossHub.CENTER.getX(), DrossHub.CENTER.getZ(), 0, SPIRE_FACING);
                this.fits = y -> found.fitsHeightAt(dross, y);
                this.what = "the " + found.name();
            }
            else
            {
                // Checked first so that nothing is built at all: the placeholder then has the centre to itself.
                if (design.isEmpty())
                {
                    LOGGER.error("Dross hub: there's no shrine design called '{}' (world.shrine.ShrineDesigns), so the placeholder hub is built instead.",
                            SPIRE_DESIGN_ID);
                }
                else
                {
                    LOGGER.error("Dross hub: the {}'s template {} is missing (data/dross/structures/shrine/{}.nbt), so the placeholder hub is built instead.",
                            design.get().name(), design.get().template(), design.get().id());
                }
                this.planned = DrossHub.Kind.PLACEHOLDER;
                this.template = null;
                this.spire = null;
                this.columns = placeholderColumns(DrossHub.CENTER.getX(), DrossHub.CENTER.getZ());
                this.fits = y -> placeholderFits(dross, y);
                this.what = "the placeholder hub";
            }
        }

        /** The columns whose chunks the build reads and changes (only X and Z count): the footprint, the surveyed ring and a bit more. */
        BoundingBox loadArea()
        {
            return columns.inflatedBy(GroundFit.surveyMargin(DrossHub.HUB_BLEND_MARGIN) + LOAD_PAST_SURVEY);
        }

        /** The columns the survey reads (only X and Z count): the footprint and the surveyed ring round it. */
        BoundingBox surveyArea()
        {
            return columns.inflatedBy(GroundFit.surveyMargin(DrossHub.HUB_BLEND_MARGIN));
        }

        /** What it's going to build (a fallback may still be built instead if something goes wrong). */
        DrossHub.Kind planned()
        {
            return planned;
        }

        /**
         * Runs the next step.
         *
         * @return true once the build is finished ({@link #result})
         */
        boolean step()
        {
            if (result != null)
            {
                return true;
            }
            String name = stepName();
            long start = Util.getMillis();
            try
            {
                if (stepsDone == 0)
                {
                    survey();
                }
                else if (stepsDone == 1)
                {
                    plan();
                }
                else if (fitted != null)
                {
                    result = stepSpire();
                }
                else
                {
                    result = build();
                }
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Dross hub: building the hub failed, so this world has no hub (arrivals land on safe ground near its centre).", e);
                result = Result.failed();
            }
            long took = Util.getMillis() - start;
            stepsDone++;
            millis += took;
            long[] times = stepTimes.computeIfAbsent(name, key -> new long[3]);
            times[0] += took;
            times[1]++;
            times[2] = Math.max(times[2], took);
            if (took > longestStep)
            {
                longestStep = took;
                longestStepName = name;
            }
            return result != null;
        }

        /** What the next step does, for the log. */
        private String stepName()
        {
            if (stepsDone == 0)
            {
                return "survey";
            }
            if (stepsDone == 1)
            {
                return "plan";
            }
            if (fitted != null)
            {
                return fitted.done() ? "frame" : fitted.stageName();
            }
            return "build";
        }

        /**
         * True while the spire is half built: the world has changed (from the first tree removed on) but the build isn't
         * finished. Such a build must be finished ({@link DrossHubPlacer} does so when the server stops), never dropped.
         */
        boolean changedWorld()
        {
            return result == null && fitted != null && fitted.changedWorld();
        }

        boolean done()
        {
            return result != null;
        }

        /** What was built; only once {@link #done}. */
        Result result()
        {
            if (result == null)
            {
                throw new IllegalStateException("The hub isn't built yet");
            }
            return result;
        }

        /** How many steps have run (one tick each when spread out). */
        int steps()
        {
            return stepsDone;
        }

        /** The time all the steps took together, in milliseconds. */
        long millis()
        {
            return millis;
        }

        /** The longest one step took, in milliseconds: the most hub work one server tick did when it was spread out. */
        long longestStep()
        {
            return Math.max(0L, longestStep);
        }

        String longestStepName()
        {
            return longestStepName;
        }

        /**
         * Each kind of step's time, for the log: "survey 40 ms, plan 3 ms, felling 900 ms in 23 steps (the longest 41 ms),
         * levelling 25 ms, ..., template 110 ms, snow inside 2 ms, frame 1 ms".
         */
        String stepTimes()
        {
            List<String> parts = new ArrayList<>();
            stepTimes.forEach((name, times) -> parts.add(name + " " + times[0] + " ms"
                    + (times[1] > 1 ? " in " + times[1] + " steps (the longest " + times[2] + " ms)" : "")));
            return String.join(", ", parts);
        }

        private void survey()
        {
            try
            {
                survey = GroundFit.survey(dross, columns, GroundFit.surveyMargin(DrossHub.HUB_BLEND_MARGIN));
            }
            catch (RuntimeException e)
            {
                groundFailed("surveying", e);
            }
        }

        private void plan()
        {
            if (groundFailed || survey == null)
            {
                return;
            }
            try
            {
                plan = planFrom(dross, survey, DrossHub.HUB_BLEND_MARGIN, fits, what);
            }
            catch (RuntimeException e)
            {
                groundFailed("planning", e);
            }
            survey = null; // the plan keeps what it needs
        }

        /** Surveying or planning the ground failed: nothing was changed, and the placeholder is built at the centre instead. */
        private void groundFailed(String doing, RuntimeException e)
        {
            if (planned == DrossHub.Kind.PLACEHOLDER)
            {
                throw e; // nothing left to fall back on
            }
            LOGGER.error("Dross hub: {} the ground for {} failed (nothing was changed), so the placeholder hub is built instead.", doing, what, e);
            groundFailed = true;
        }

        private Result build()
        {
            if (groundFailed || plan == null)
            {
                return placeholderAtCenter(dross);
            }
            return switch (planned)
            {
                case TEMPLATE -> buildTemplate(dross, template, columns, plan);
                case SPIRE -> startSpire();
                case PLACEHOLDER ->
                {
                    Placed placed = placePlaceholder(dross, DrossHub.CENTER.getX(), DrossHub.CENTER.getZ(), plan);
                    yield new Result(placed.exit(), DrossHub.Kind.PLACEHOLDER, placed.box(), placed.floorY(), true);
                }
            };
        }

        /**
         * Starts building the Shattered Spire on the planned ground, its frame opening centred on the hub centre. Nothing
         * is changed yet: its first step runs on the next tick (this one may load the build's classes).
         *
         * @return null while it's being built (the next steps carry on), or the fallback if it can't be built
         */
        @Nullable
        private Result startSpire()
        {
            BlockPos center = spire.centerForFrameAt(DrossHub.CENTER.getX(), DrossHub.CENTER.getZ(), plan.groundY, SPIRE_FACING);
            spireCenter = center;
            Optional<ShrineDesign.FittedBuild> started;
            try
            {
                started = spire.startFitted(dross, center, SPIRE_FACING, plan);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Dross hub: building the {} failed, so the placeholder hub is built in front of where its gate would be.", spire.name(), e);
                return placeholderBesideSpire(dross, spire, center);
            }
            if (started.isEmpty())
            {
                // startFitted has logged why, and changed nothing.
                LOGGER.error("Dross hub: the {} couldn't be built (see the error above), so the placeholder hub is built instead.", spire.name());
                return placeholderAtCenter(dross);
            }
            fitted = started.get();
            return null;
        }

        /**
         * The spire's next step; once it's all built, its frame is lit on a step of its own.
         *
         * @return null while it's being built, or what was built (the spire, or a fallback if something went wrong)
         */
        @Nullable
        private Result stepSpire()
        {
            if (!fitted.done())
            {
                try
                {
                    fitted.step();
                }
                catch (RuntimeException e)
                {
                    LOGGER.error("Dross hub: building the {} failed, so the placeholder hub is built in front of where its gate would be.", spire.name(), e);
                    return placeholderBesideSpire(dross, spire, spireCenter);
                }
                return null;
            }
            return finishSpire(dross, spire, spireCenter, plan, fitted.report());
        }
    }

    // ------------------------------------------------------------------ the Shattered Spire

    /**
     * The Shattered Spire is built on the planned ground with its frame opening centred on the hub centre: lights that
     * frame. Falls back to the placeholder (with an error in the log) if its frame can't be lit.
     */
    private static Result finishSpire(ServerLevel dross, ShrineDesign spire, BlockPos center, GroundFit.Plan plan, GroundFit.Report report)
    {
        LOGGER.info("Dross hub: trees and the like cleared for the {}: {}", spire.name(), report.felling().summary());

        ShrineDesign.Built placed = spire.built(center, SPIRE_FACING);
        PortalOpening exit = lightSpireFrame(dross, placed);
        if (exit == null)
        {
            return placeholderBesideSpire(dross, spire, center); // the error is already in the log
        }
        LOGGER.info("Dross hub: built the {} (shrine design No. {}) facing {} on fitted ground, ground level {}; its frame is the exit "
                        + "portal: opening {} along {} ({}x{}), lit",
                spire.name(), spire.number(), placed.facing().getName(), plan.groundY,
                exit.minCorner().toShortString(), exit.axis(), exit.width(), exit.height());
        return new Result(exit, DrossHub.Kind.SPIRE, placed.footprint(), plan.groundY, true);
    }

    /**
     * Lights the built spire's frame, found exactly where its design says it is (the frame around the design's opening;
     * the loose shards are never looked at).
     *
     * @return the lit exit portal, or null (with an error in the log) if there's no complete frame there or it didn't light
     */
    @Nullable
    private static PortalOpening lightSpireFrame(ServerLevel dross, ShrineDesign.Built built)
    {
        String name = built.design().name();
        Optional<DrossPortalShape> shape = DrossPortalShape.findAnyPortalShape(dross, built.openingMin(), built.frameAxis());
        if (shape.isEmpty())
        {
            LOGGER.error("Dross hub: the {} was built at {}, but there's no complete Dross portal frame around its opening at {} (axis {}, {}x{}), "
                            + "so its exit portal can't be lit; the placeholder hub is built in front of its gate instead.",
                    name, built.center().toShortString(), built.openingMin().toShortString(), built.frameAxis(),
                    built.openingWidth(), built.openingHeight());
            return null;
        }
        if (!shape.get().isComplete())
        {
            shape.get().createPortalBlocks();
        }
        PortalOpening exit = PortalOpening.of(shape.get());
        if (DrossPortalShape.findLitPortalShape(dross, exit.minCorner(), exit.axis()).isEmpty())
        {
            LOGGER.error("Dross hub: the {}'s frame at {} didn't light, so the placeholder hub is built in front of its gate instead.",
                    name, exit.minCorner().toShortString());
            return null;
        }
        if (!exit.minCorner().equals(built.openingMin()) || exit.axis() != built.frameAxis()
                || exit.width() != built.openingWidth() || exit.height() != built.openingHeight())
        {
            LOGGER.warn("Dross hub: the {}'s frame opening is {} along {} ({}x{}), not {} along {} ({}x{}) as its design says; "
                            + "using the frame that's there.",
                    name, exit.minCorner().toShortString(), exit.axis(), exit.width(), exit.height(),
                    built.openingMin().toShortString(), built.frameAxis(), built.openingWidth(), built.openingHeight());
        }
        return exit;
    }

    // ------------------------------------------------------------------ the user's template

    /** Places the user's template centred on the hub centre, on the planned ground, then finds (and lights) its exit portal. */
    private static Result buildTemplate(ServerLevel dross, StructureTemplate template, BoundingBox columns, GroundFit.Plan plan)
    {
        Vec3i size = template.getSize();
        int minX = columns.minX();
        int minZ = columns.minZ();
        String what = "the " + DrossHub.TEMPLATE + " template";
        int originY = plan.groundY + 1 + TEMPLATE_Y_OFFSET;
        int clearTop = Math.min(originY + size.getY() - 1 + TEMPLATE_CLEAR_ABOVE, dross.getMaxBuildHeight() - 1);
        GroundFit.Report report = GroundFit.apply(dross, plan, clearTop);
        LOGGER.info("Dross hub: ground fitted for {}: {}", what, report.summary());

        BlockPos origin = new BlockPos(minX, originY, minZ);
        PortalOpening exit = placeTemplate(dross, template, origin);
        BoundingBox box = new BoundingBox(minX, originY, minZ, minX + size.getX() - 1, originY + size.getY() - 1, minZ + size.getZ() - 1);
        if (exit != null)
        {
            LOGGER.info("Dross hub: placed the {} template on fitted ground (ground level {}); exit portal at {} along {} ({}x{})",
                    DrossHub.TEMPLATE, plan.groundY, exit.minCorner().toShortString(), exit.axis(), exit.width(), exit.height());
            return new Result(exit, DrossHub.Kind.TEMPLATE, box, plan.groundY, true);
        }
        LOGGER.warn("Dross hub: the {} template has no complete Dross portal frame, so the placeholder exit portal is built over it.",
                DrossHub.TEMPLATE);
        Placed placed = buildPlaceholder(dross, DrossHub.CENTER.getX(), DrossHub.CENTER.getZ(), BESIDE_BLEND_MARGIN);
        return new Result(placed.exit(), DrossHub.Kind.PLACEHOLDER, union(box, placed.box()), placed.floorY(), false);
    }

    /** True if the template, with its bottom layer at this ground level, fits in the world's height. */
    private static boolean templateFits(ServerLevel dross, int groundY, Vec3i size)
    {
        int originY = groundY + 1 + TEMPLATE_Y_OFFSET;
        return originY > dross.getMinBuildHeight() && originY + size.getY() - 1 + TEMPLATE_CLEAR_ABOVE <= dross.getMaxBuildHeight() - 1;
    }

    /** Places the template at {@code origin}, then finds (and lights) its exit portal. */
    @Nullable
    private static PortalOpening placeTemplate(ServerLevel dross, StructureTemplate template, BlockPos origin)
    {
        // "Known shape": don't let blocks react to each other while the template is half placed
        // (a portal block placed before its frame would otherwise go out straight away).
        StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true);
        template.placeInWorld(dross, origin, origin, settings, dross.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);

        // 1. A portal saved in the template (lit when it was saved): make sure it's fully lit.
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, ModBlocks.DROSS_PORTAL.get()))
        {
            Direction.Axis axis = info.state().hasProperty(DrossPortalBlock.AXIS) ? info.state().getValue(DrossPortalBlock.AXIS) : Direction.Axis.X;
            Optional<DrossPortalShape> shape = DrossPortalShape.findAnyPortalShape(dross, info.pos(), axis);
            if (shape.isPresent())
            {
                if (!shape.get().isComplete())
                {
                    shape.get().createPortalBlocks();
                }
                return PortalOpening.of(shape.get());
            }
        }
        // 2. Otherwise an empty frame in the template: light it.
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, ModBlocks.DROSS_PORTAL_FRAME.get()))
        {
            Optional<DrossPortalShape> shape = DrossPortalShape.findEmptyPortalShape(dross, info.pos().above(), Direction.Axis.X);
            if (shape.isPresent())
            {
                shape.get().createPortalBlocks();
                return PortalOpening.of(shape.get());
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ the placeholder

    /** The placeholder on its own at the hub centre, on fitted ground (surveyed, planned and built at once: a fallback). */
    private static Result placeholderAtCenter(ServerLevel dross)
    {
        Placed placed = buildPlaceholder(dross, DrossHub.CENTER.getX(), DrossHub.CENTER.getZ(), DrossHub.HUB_BLEND_MARGIN);
        return new Result(placed.exit(), DrossHub.Kind.PLACEHOLDER, placed.box(), placed.floorY(), true);
    }

    /**
     * The placeholder, for when the spire stands (maybe only partly) at the hub centre but has no working exit portal:
     * on open ground straight in front of its gate, clear of its footprint, so nobody arrives on top of the spire.
     */
    private static Result placeholderBesideSpire(ServerLevel dross, ShrineDesign spire, BlockPos center)
    {
        BlockPos spot = spire.entrance(center, SPIRE_FACING).relative(SPIRE_FACING, PLATFORM_RADIUS + 1);
        Placed placed = buildPlaceholder(dross, spot.getX(), spot.getZ(), BESIDE_BLEND_MARGIN);
        BoundingBox both = union(spire.built(center, SPIRE_FACING).footprint(), placed.box());
        return new Result(placed.exit(), DrossHub.Kind.PLACEHOLDER, both, placed.floorY(), false);
    }

    /** The placeholder centred on {@code cx}/{@code cz}, its ground surveyed and planned first (a staircase {@code blendMargin} wide). */
    private static Placed buildPlaceholder(ServerLevel dross, int cx, int cz, int blendMargin)
    {
        GroundFit.Plan plan = planGround(dross, placeholderColumns(cx, cz), blendMargin, y -> placeholderFits(dross, y), "the placeholder hub");
        return placePlaceholder(dross, cx, cz, plan);
    }

    /** The placeholder's 11x11 columns round its centre column (only X and Z count). */
    private static BoundingBox placeholderColumns(int cx, int cz)
    {
        return new BoundingBox(cx - PLATFORM_RADIUS, 0, cz - PLATFORM_RADIUS, cx + PLATFORM_RADIUS, 0, cz + PLATFORM_RADIUS);
    }

    /**
     * The placeholder: an 11x11 platform whose top is level with the (levelled) ground, with the exit portal frame in
     * the middle. The frame's bottom row is set into the platform, so you walk straight into the portal.
     * Seen from above (Z grows downwards, F = frame, the opening is the middle two F columns' gap):
     * <pre>
     *   # # # # # # # # # # #
     *   # . . . . . . . . . #
     *   # . . . F F F F . . #    z = centre; frame x from centre-2 to centre+1
     *   # . . . . . . . . . #    arrival: one block in front (+Z), facing away from the portal
     *   # # # # # # # # # # #
     * </pre>
     * {@code cx}/{@code cz} is its centre column. The ground is fitted first ({@link GroundFit}, with {@code plan}: levelled
     * at the natural ground's median, trees touching it removed whole, a staircase around it).
     */
    private static Placed placePlaceholder(ServerLevel dross, int cx, int cz, GroundFit.Plan plan)
    {
        int floorY = plan.groundY;
        int ground = floorY + 1;
        GroundFit.Report report = GroundFit.apply(dross, plan, floorY + CLEAR_HEIGHT);
        LOGGER.info("Dross hub: ground fitted for the placeholder hub: {}", report.summary());

        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++)
        {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++)
            {
                boolean border = Math.abs(dx) == PLATFORM_RADIUS || Math.abs(dz) == PLATFORM_RADIUS;
                dross.setBlock(pos.set(cx + dx, floorY, cz + dz), border ? PLATFORM_BORDER : PLATFORM, Block.UPDATE_ALL);
                for (int d = 1; d <= SUPPORT_DEPTH; d++)
                {
                    pos.set(cx + dx, floorY - d, cz + dz);
                    BlockState below = dross.getBlockState(pos);
                    if (!below.isAir() && below.getFluidState().isEmpty())
                    {
                        break;
                    }
                    dross.setBlock(pos, PLATFORM, Block.UPDATE_ALL);
                }
                for (int y = ground; y < ground + CLEAR_HEIGHT; y++)
                {
                    pos.set(cx + dx, y, cz + dz);
                    if (!dross.getBlockState(pos).isAir())
                    {
                        dross.setBlock(pos, air, Block.UPDATE_ALL);
                    }
                }
            }
        }

        // The exit frame, along X, centred on the platform: the opening is x = cx-1 .. cx.
        BlockState frame = ModBlocks.DROSS_PORTAL_FRAME.get().defaultBlockState();
        int frameMinX = cx - EXIT_FRAME_WIDTH / 2;
        for (int i = 0; i < EXIT_FRAME_WIDTH; i++)
        {
            for (int j = 0; j < EXIT_FRAME_HEIGHT; j++)
            {
                boolean edge = i == 0 || i == EXIT_FRAME_WIDTH - 1 || j == 0 || j == EXIT_FRAME_HEIGHT - 1;
                if (edge)
                {
                    dross.setBlock(pos.set(frameMinX + i, floorY + j, cz), frame, Block.UPDATE_ALL);
                }
            }
        }

        BoundingBox box = placeholderBox(cx, cz, floorY);
        Optional<DrossPortalShape> shape = DrossPortalShape.findEmptyPortalShape(dross, new BlockPos(frameMinX + 1, ground, cz), Direction.Axis.X);
        if (shape.isEmpty())
        {
            LOGGER.error("Dross hub: built the placeholder platform at {} {}, but its exit portal frame isn't complete; the hub has no exit portal.", cx, cz);
            return new Placed(null, box, floorY);
        }
        shape.get().createPortalBlocks();
        PortalOpening exit = PortalOpening.of(shape.get());
        LOGGER.info("Dross hub: built the placeholder hub at {} {} (floor level {}); exit portal at {}", cx, cz, floorY, exit.minCorner().toShortString());
        return new Placed(exit, box, floorY);
    }

    /** The box a placeholder covers: its 11x11 platform, the support under it and the air cleared above it. */
    private static BoundingBox placeholderBox(int cx, int cz, int floorY)
    {
        return new BoundingBox(cx - PLATFORM_RADIUS, floorY - SUPPORT_DEPTH, cz - PLATFORM_RADIUS,
                cx + PLATFORM_RADIUS, floorY + CLEAR_HEIGHT, cz + PLATFORM_RADIUS);
    }

    /** True if a placeholder with its floor at this level fits in the world's height (support under it, air over it). */
    private static boolean placeholderFits(ServerLevel dross, int floorY)
    {
        return floorY - SUPPORT_DEPTH >= dross.getMinBuildHeight() && floorY + CLEAR_HEIGHT <= dross.getMaxBuildHeight() - 1;
    }

    // ------------------------------------------------------------------ fitting the ground

    /**
     * Surveys the ground under and around {@code columns} (only its X and Z matter) and plans it ({@link #planFrom}).
     * Changes nothing; loads (and if needed generates) the chunks.
     */
    private static GroundFit.Plan planGround(ServerLevel dross, BoundingBox columns, int blendMargin, IntPredicate fits, String what)
    {
        GroundFit.Survey survey = GroundFit.survey(dross, columns, GroundFit.surveyMargin(blendMargin));
        return planFrom(dross, survey, blendMargin, fits, what);
    }

    /**
     * Plans the surveyed ground at the natural ground's median (or the nearest level that fits in the world's height), with
     * a staircase {@code blendMargin} blocks wide. Logs the plan, and warns if the ground is rough. Changes nothing.
     */
    private static GroundFit.Plan planFrom(ServerLevel dross, GroundFit.Survey survey, int blendMargin, IntPredicate fits, String what)
    {
        GroundFit.Plan plan = GroundFit.plan(survey, blendMargin, DrossHub.HUB_WATER_CLEARANCE);
        if (!fits.test(plan.groundY))
        {
            int level = nearestFitting(dross, plan.groundY, fits);
            LOGGER.warn("Dross hub: {} doesn't fit in the world's height with its ground at y {} (the natural ground's median), so it's "
                    + "levelled at y {} instead.", what, plan.groundY, level);
            plan = GroundFit.plan(survey, level, blendMargin, DrossHub.HUB_WATER_CLEARANCE);
        }
        BoundingBox columns = survey.footprint();
        LOGGER.info("Dross hub: ground plan for {} (footprint X {}..{}, Z {}..{}): {}", what, columns.minX(), columns.maxX(),
                columns.minZ(), columns.maxZ(), plan.summary());
        warnIfRough(plan, what);
        return plan;
    }

    /** The ground level nearest to {@code groundY} that {@code fits}, or {@code groundY} itself if none does. */
    private static int nearestFitting(ServerLevel dross, int groundY, IntPredicate fits)
    {
        int range = dross.getMaxBuildHeight() - dross.getMinBuildHeight();
        for (int d = 1; d <= range; d++)
        {
            if (fits.test(groundY + d))
            {
                return groundY + d;
            }
            if (fits.test(groundY - d))
            {
                return groundY - d;
            }
        }
        return groundY;
    }

    /** A warning in the log if fitting the ground has to do more than gentle terrain needs (it's built anyway). */
    private static void warnIfRough(GroundFit.Plan plan, String what)
    {
        List<String> rough = new ArrayList<>();
        if (plan.overflow > 0)
        {
            rough.add(plan.overflow + " columns where the staircase doesn't fit in " + plan.blendMargin + " blocks");
        }
        if (plan.steepSteps > 0)
        {
            rough.add(plan.steepSteps + " steps of 2 or more blocks next to the reshaped ground");
        }
        if (plan.maxRingChange > WARN_RING_CHANGE)
        {
            rough.add("a column around it changed by " + plan.maxRingChange + " blocks");
        }
        if (plan.deepestDip > WARN_DIP)
        {
            rough.add("a dip " + plan.deepestDip + " blocks deep under it filled");
        }
        if (plan.liquidInTheWay > 0)
        {
            rough.add(plan.liquidInTheWay + " water or lava columns in the staircase's way (never reshaped)");
        }
        if (!rough.isEmpty())
        {
            LOGGER.warn("Dross hub: the ground for {} is rough: {}. Built anyway.", what, String.join("; ", rough));
        }
    }

    /** The smallest box holding both. */
    private static BoundingBox union(BoundingBox a, BoundingBox b)
    {
        return BoundingBox.encapsulatingBoxes(List.of(a, b)).orElse(a);
    }
}
