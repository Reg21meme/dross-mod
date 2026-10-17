package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.world.shrine.GroundFit;
import com.reg21meme.dross.world.shrine.ShrineDesign;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import org.slf4j.Logger;

/**
 * The castle of new worlds: shrine design No. 1, the <b>Fallen Cathedral</b> ({@code world.shrine}), a vast ruined
 * gothic cathedral with the unlit Dross frame on a stepped altar in its apse.
 * <ul>
 *   <li>Its front (the great door) faces south, unturned, so its frame runs along X like the old placeholder's.</li>
 *   <li>It's placed so the middle of its frame opening is on the site column (the spot picked for the world, see
 *       {@link PortalSitePlacer}), not so its footprint is centred there.</li>
 *   <li>It sits on ground that was already nearly flat: the footprint is levelled by a block or two, the ground around
 *       it steps back to the natural terrain one block at a time, and trees in the way are removed whole
 *       ({@code ShrineDesign.startFitted}, {@code GroundFit}).</li>
 *   <li>It's built a step a tick ({@link Building}: felling the trees in short steps, levelling, the template...), so
 *       placing the site never freezes the game for long.</li>
 *   <li>Its frame is checked afterwards with the portal area's own frame test (the one the Rift Key uses).</li>
 * </ul>
 * Used unless the user's own castle template ({@link PortalCastle}) exists and is valid. If the cathedral's template is
 * missing or its frame fails the check, an error is logged and the caller falls back to the small placeholder shrine.
 */
final class PortalCathedral
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The shrine design used for the castle: No. 1, the Fallen Cathedral. */
    static final String DESIGN_ID = "fallen_cathedral";
    /** The way its front faces. South (unturned) keeps its frame along X. */
    static final Direction FACING = Direction.SOUTH;

    static Optional<ShrineDesign> design()
    {
        return ShrineDesign.byId(DESIGN_ID);
    }

    /** Where the frame will be with the opening's middle column at {@code x}/{@code z} and the ground at {@code groundY}. */
    @Nullable
    static SiteFrame plannedFrame(int x, int z, int groundY)
    {
        ShrineDesign cathedral = design().orElse(null);
        if (cathedral == null)
        {
            return null;
        }
        ShrineDesign.Built built = cathedral.built(cathedral.centerForFrameAt(x, z, groundY, FACING), FACING);
        return SiteFrame.fromOpening(built.openingMin(), built.frameAxis(), built.openingWidth(), built.openingHeight());
    }

    /**
     * Gets the cathedral ready to be built with its frame opening on the site column, on fitted ground, a step at a time
     * ({@link Building}). Nothing is changed yet (but if no plan is given, one is made now: the ground is surveyed).
     *
     * @param plan the ground plan made for the cathedral's footprint there (by {@link SiteCheck}), or null to make one now
     * @return the build, or null (with an error in the log) if the cathedral can't be built there
     */
    @Nullable
    static Building start(ServerLevel level, int x, int z, @Nullable GroundFit.Plan plan, SiteRules.Level rules)
    {
        ShrineDesign cathedral = design().orElse(null);
        if (cathedral == null)
        {
            LOGGER.error("Dross portal site: there's no shrine design called '{}' (world.shrine.ShrineDesigns), so the cathedral "
                    + "can't be built; falling back to the small placeholder shrine.", DESIGN_ID);
            return null;
        }
        Optional<ShrineDesign.FittedBuild> fitted;
        try
        {
            GroundFit.Plan ground = plan;
            BoundingBox expected = cathedral.footprintForFrameAt(x, z, 0, FACING);
            if (ground == null || !sameColumns(ground.survey.footprint(), expected))
            {
                ground = GroundFit.plan(GroundFit.survey(level, expected, SiteCheck.surveyMargin(rules)), rules.blendMargin(),
                        SiteRules.WATER_CLEARANCE);
                LOGGER.info("Dross portal site: made a ground plan for the {} at {} {}: {}", cathedral.name(), x, z, ground.summary());
            }
            fitted = cathedral.startFitted(level, cathedral.centerForFrameAt(x, z, ground.groundY, FACING), FACING, ground);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Dross portal site: building the {} failed; falling back to the small placeholder shrine.", cathedral.name(), e);
            return null;
        }
        if (fitted.isEmpty())
        {
            LOGGER.error("Dross portal site: the {} couldn't be built (its template {} is missing, or it doesn't fit there: see the "
                    + "error above); falling back to the small placeholder shrine.", cathedral.name(), cathedral.template());
            return null;
        }
        return new Building(level, cathedral, fitted.get(), x, z);
    }

    /**
     * The cathedral being built a step at a time ({@link #step}, one per server tick when it's spread out): its trees,
     * ground and template ({@link ShrineDesign.FittedBuild}), then its frame is checked on a step of its own. From the
     * first tree removed on ({@link #changedWorld}) the world has changed, so it must then be finished ({@link #finish}),
     * never dropped.
     */
    static final class Building
    {
        private final ServerLevel level;
        private final ShrineDesign cathedral;
        private final ShrineDesign.FittedBuild fitted;
        private final int x;
        private final int z;
        private boolean done;
        @Nullable
        private SiteFrame frame;
        private int steps;
        private long millis;
        private long longest = -1L;
        private String longestStage = "";

        private Building(ServerLevel level, ShrineDesign cathedral, ShrineDesign.FittedBuild fitted, int x, int z)
        {
            this.level = level;
            this.cathedral = cathedral;
            this.fitted = fitted;
            this.x = x;
            this.z = z;
        }

        /**
         * The next step: a piece of the cathedral ({@link ShrineDesign.FittedBuild#step}), or, once it's all built, the
         * check of its frame.
         *
         * @return true once it's done ({@link #frame})
         */
        boolean step()
        {
            return step(false);
        }

        /** Everything that's left, now. */
        void finish()
        {
            step(true);
        }

        /** True once the world has been changed (from the first tree removed on). */
        boolean changedWorld()
        {
            return fitted.changedWorld();
        }

        /** The cathedral's frame once it's done, or null (with an error in the log) if it couldn't be built or its frame isn't valid. */
        @Nullable
        SiteFrame frame()
        {
            return frame;
        }

        /** How many steps it took, all their time together (ms) and the longest one, for the log. */
        String describeSteps()
        {
            return String.format("in %d steps, the longest %d ms (%s)", steps, Math.max(0L, longest), longestStage);
        }

        long millis()
        {
            return millis;
        }

        private boolean step(boolean all)
        {
            if (done)
            {
                return true;
            }
            long start = Util.getMillis();
            if (!fitted.done())
            {
                String working = fitted.stageName();
                try
                {
                    if (all)
                    {
                        fitted.finish();
                    }
                    else
                    {
                        fitted.step();
                    }
                }
                catch (RuntimeException e)
                {
                    LOGGER.error("Dross portal site: building the {} failed; falling back to the small placeholder shrine.", cathedral.name(), e);
                    done = true;
                }
                count(working, Util.getMillis() - start);
                if (done || !all)
                {
                    return done; // the frame is checked on a step of its own
                }
                start = Util.getMillis();
            }
            frame = checkFrame(level, cathedral, fitted.built(), x, z);
            done = true;
            count("frame", Util.getMillis() - start);
            return true;
        }

        private void count(String working, long took)
        {
            steps++;
            millis += took;
            if (took > longest)
            {
                longest = took;
                longestStage = working;
            }
        }
    }

    /**
     * The last resort, only when no spot passed even the loosest rules: builds the cathedral the old way, on whatever
     * ground is there (median height, foundation down to solid ground, sloped edges, sealed against water).
     */
    @Nullable
    static SiteFrame placeOnAnyGround(ServerLevel level, int x, int z)
    {
        ShrineDesign cathedral = design().orElse(null);
        if (cathedral == null)
        {
            return null;
        }
        Optional<ShrineDesign.Built> built;
        try
        {
            built = cathedral.buildWithFrameAt(level, x, z, FACING);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Dross portal site: building the {} failed; falling back to the small placeholder shrine.", cathedral.name(), e);
            return null;
        }
        if (built.isEmpty())
        {
            return null;
        }
        return checkFrame(level, cathedral, built.get(), x, z);
    }

    @Nullable
    private static SiteFrame checkFrame(ServerLevel level, ShrineDesign cathedral, ShrineDesign.Built placed, int x, int z)
    {
        SiteFrame frame = SiteFrame.fromOpening(placed.openingMin(), placed.frameAxis(), placed.openingWidth(), placed.openingHeight());
        if (!PortalCastle.isValidInWorld(level, frame))
        {
            LOGGER.error("Dross portal site: the {} was built at {}, but its frame (corner {}, axis {}, opening {}x{}) isn't a complete, "
                            + "empty Dross frame in the world, so the Rift Key couldn't light it; falling back to the small placeholder shrine.",
                    cathedral.name(), placed.center().toShortString(), frame.corner().toShortString(), frame.axis(),
                    frame.openingWidth(), frame.openingHeight());
            return null;
        }
        LOGGER.info("Dross portal site: built the {} (shrine design No. {}) facing {}, ground level {}, its frame opening on the "
                        + "site column {} {}; frame corner {} axis {} (opening {}x{}), valid",
                cathedral.name(), cathedral.number(), FACING.getName(), placed.center().getY(), x, z,
                frame.corner().toShortString(), frame.axis(), frame.openingWidth(), frame.openingHeight());
        return frame;
    }

    private static boolean sameColumns(BoundingBox a, BoundingBox b)
    {
        return a.minX() == b.minX() && a.maxX() == b.maxX() && a.minZ() == b.minZ() && a.maxZ() == b.maxZ();
    }

    private PortalCathedral() {}
}
