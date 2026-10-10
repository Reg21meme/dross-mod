package com.reg21meme.dross.world.shrine;

import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Fits a big build (the Fallen Cathedral at the portal site) into <b>natural, already flat</b> ground, gently:
 * <ol>
 *   <li><b>Survey</b> ({@link #survey}): the natural ground's height in every column of the footprint and the ring
 *       around it. Trees, leaves, plants, snow layers, huge mushrooms, ice spikes and mossy boulders are looked
 *       through (they aren't ground); water counts at its top, ice over water counts as water.</li>
 *   <li><b>Plan</b> ({@link #plan}): the footprint is levelled at the median natural height. Around it the ground
 *       becomes a <b>staircase</b>: a column {@code d} blocks outside the footprint goes to its natural height, but
 *       never more than {@code d} blocks above or below the build's level. Then, wherever natural ground next to the
 *       reshaped ground still steps by two or more, it's pulled to within one block too, outwards as far as needed. So
 *       the ground changes by at most one block per step until it meets the natural terrain, with no cliffs or walls of
 *       dirt; natural steps away from the reshaped ground are left alone.</li>
 *   <li><b>Apply</b> ({@link #apply}): trees, huge mushrooms, ice spikes and boulders that touch the footprint or the
 *       reshaped ring are removed <b>whole</b> ({@link TreeFelling}); every other one is left alone. Then the footprint
 *       is cut and filled to its level (small caves under it are filled down to solid ground) and the ring is shaped.
 *       New ground gets the same top as the column had (grass, podzol, sand over sandstone, snow...).</li>
 * </ol>
 * Changes use {@link #FLAGS}: players see them, but no block reacts to its neighbours meanwhile (water doesn't flow,
 * sand doesn't fall, leaves don't start decaying).
 */
public final class GroundFit
{
    /** Clients see every change, but nothing pokes its neighbours. */
    static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    /** How far around a column (blocks) we look to tell an ice spike or a boulder from the flat ground under it. */
    public static final int PROTRUSION_WINDOW = 6;
    /** Inside the footprint there are always at least this many solid blocks under the ground (small caves are filled). */
    private static final int MIN_FOUNDATION = 4;
    /** Filled ground is the column's own under-block this many blocks deep; deeper down it's stone. */
    private static final int SOIL_DEPTH = 3;
    /**
     * A cave hidden under the footprint's ground (within {@link #MIN_FOUNDATION} blocks) is filled with stone, but at most
     * this deep under the ground level; a bigger cave keeps its lower part (out of sight, and stone doesn't fall).
     */
    private static final int CAVE_FILL_LIMIT = 16;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();

    private GroundFit() {}

    // ---------------------------------------------------------------- survey

    /** The natural ground in the footprint and a ring {@code margin} blocks wide around it (columns). */
    public static final class Survey
    {
        final ServerLevel level;
        final BoundingBox footprint;
        final int margin;
        final int minX;
        final int minZ;
        final int sizeX;
        final int sizeZ;
        /** Y of the natural ground's top block (or of the water's top). */
        final int[] surface;
        /** The natural top is water or lava (ice over water included). */
        final boolean[] liquid;
        /** The natural top block, and the block under it. */
        final BlockState[] top;
        final BlockState[] under;
        /** A snow layer lay on the natural top. */
        final boolean[] snow;
        /** An ice spike or a boulder stood on this column: its ground is the height around it ({@link #surface}). */
        final boolean[] protruding;
        /** What most of the natural ground here is topped with, and what lies under that. */
        final BlockState dominantTop;
        final BlockState dominantUnder;

        private Survey(ServerLevel level, BoundingBox footprint, int margin)
        {
            this.level = level;
            this.footprint = footprint;
            this.margin = margin;
            this.minX = footprint.minX() - margin;
            this.minZ = footprint.minZ() - margin;
            this.sizeX = footprint.getXSpan() + 2 * margin;
            this.sizeZ = footprint.getZSpan() + 2 * margin;
            int n = sizeX * sizeZ;
            this.surface = new int[n];
            this.liquid = new boolean[n];
            this.top = new BlockState[n];
            this.under = new BlockState[n];
            this.snow = new boolean[n];
            this.protruding = new boolean[n];
            measure();
            findProtrusions();
            BlockState[] dominant = dominant();
            this.dominantTop = dominant[0];
            this.dominantUnder = dominant[1];
        }

        public BoundingBox footprint()
        {
            return footprint;
        }

        /** True if the column is inside the surveyed area. */
        public boolean contains(int x, int z)
        {
            return x >= minX && z >= minZ && x < minX + sizeX && z < minZ + sizeZ;
        }

        int index(int x, int z)
        {
            return (x - minX) * sizeZ + (z - minZ);
        }

        /** The natural ground's height at a surveyed column. */
        public int surfaceAt(int x, int z)
        {
            return surface[index(x, z)];
        }

        public boolean liquidAt(int x, int z)
        {
            return liquid[index(x, z)];
        }

        /** True if an ice spike or a boulder stood on this column. */
        public boolean protrudingAt(int x, int z)
        {
            return protruding[index(x, z)];
        }

        /** How far the column is outside the footprint (0 inside it), rounded down. */
        public int distance(int x, int z)
        {
            return distanceOutside(footprint, x, z);
        }

        private void measure()
        {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < sizeX; i++)
            {
                for (int k = 0; k < sizeZ; k++)
                {
                    int x = minX + i;
                    int z = minZ + k;
                    int index = i * sizeZ + k;
                    int topY = ShrineGround.topY(level, x, z);
                    surface[index] = level.getMinBuildHeight();
                    for (int y = topY; y > level.getMinBuildHeight(); y--)
                    {
                        BlockState state = level.getBlockState(pos.set(x, y, z));
                        if (state.isAir())
                        {
                            continue;
                        }
                        if (!state.getFluidState().isEmpty())
                        {
                            surface[index] = y;
                            liquid[index] = true;
                            top[index] = state;
                            break;
                        }
                        if (isNaturalGround(level, pos, state))
                        {
                            surface[index] = y;
                            top[index] = state;
                            under[index] = level.getBlockState(pos.set(x, y - 1, z));
                            snow[index] = level.getBlockState(pos.set(x, y + 1, z)).is(Blocks.SNOW);
                            break;
                        }
                    }
                }
            }
        }

        /**
         * Ice spikes and mossy boulders sit on the ground (and dig a little into it), so they look like ground. A column
         * topped with packed ice or mossy cobblestone that stands higher than the plain ground around it (the median of
         * the columns within {@link #PROTRUSION_WINDOW} that aren't topped with either) is one: its ground is that
         * median height. Flat ice patches, which are part of the ground, stay ground.
         */
        private void findProtrusions()
        {
            int[] adjusted = surface.clone();
            int[] window = new int[(2 * PROTRUSION_WINDOW + 1) * (2 * PROTRUSION_WINDOW + 1)];
            for (int i = 0; i < sizeX; i++)
            {
                for (int k = 0; k < sizeZ; k++)
                {
                    int index = i * sizeZ + k;
                    if (liquid[index] || top[index] == null || !isProtrusionMaterial(top[index]))
                    {
                        continue;
                    }
                    int count = 0;
                    for (int di = -PROTRUSION_WINDOW; di <= PROTRUSION_WINDOW; di++)
                    {
                        for (int dk = -PROTRUSION_WINDOW; dk <= PROTRUSION_WINDOW; dk++)
                        {
                            int ni = i + di;
                            int nk = k + dk;
                            if (ni < 0 || nk < 0 || ni >= sizeX || nk >= sizeZ)
                            {
                                continue;
                            }
                            int other = ni * sizeZ + nk;
                            if (!liquid[other] && top[other] != null && !isProtrusionMaterial(top[other]))
                            {
                                window[count++] = surface[other];
                            }
                        }
                    }
                    if (count < 3)
                    {
                        continue; // all ice or rock around: treat it as ground
                    }
                    Arrays.sort(window, 0, count);
                    int ground = window[count / 2];
                    if (surface[index] > ground)
                    {
                        adjusted[index] = ground;
                        protruding[index] = true;
                        snow[index] = false;
                    }
                }
            }
            System.arraycopy(adjusted, 0, surface, 0, surface.length);
        }

        /** The most common natural top (and the block under it) on dry, plain ground: what new ground is made of. */
        private BlockState[] dominant()
        {
            Map<Block, Integer> tops = new HashMap<>();
            Map<Block, Integer> unders = new HashMap<>();
            for (int index = 0; index < surface.length; index++)
            {
                if (liquid[index] || protruding[index] || top[index] == null || !isSurfaceBlock(top[index]))
                {
                    continue;
                }
                tops.merge(top[index].getBlock(), 1, Integer::sum);
                if (under[index] != null && isSoil(under[index]))
                {
                    unders.merge(under[index].getBlock(), 1, Integer::sum);
                }
            }
            Block topBlock = tops.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Blocks.GRASS_BLOCK);
            Block underBlock = unders.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(Blocks.DIRT);
            return new BlockState[] {topBlock.defaultBlockState(), underBlock.defaultBlockState()};
        }

        /** The top block new or freshly cut ground in this column gets: its own natural top, or the area's. */
        BlockState topFor(int index)
        {
            BlockState natural = top[index];
            if (!protruding[index] && !liquid[index] && natural != null && isSurfaceBlock(natural))
            {
                return withoutSnow(natural);
            }
            return dominantTop;
        }

        /** The block under the top of new ground in this column: its own natural under-block, or the area's. */
        BlockState underFor(int index)
        {
            BlockState natural = under[index];
            if (!protruding[index] && !liquid[index] && natural != null && isSoil(natural))
            {
                return natural;
            }
            return dominantUnder;
        }
    }

    /**
     * Surveys the footprint and a ring {@code margin} blocks wide around it. Loads (and if needed generates) the chunks;
     * load them in the background first if the server mustn't wait.
     */
    public static Survey survey(ServerLevel level, BoundingBox footprint, int margin)
    {
        return new Survey(level, footprint, margin);
    }

    // ---------------------------------------------------------------- plan

    /** The levelled footprint and the staircase around it, worked out before anything changes, plus what it would take. */
    public static final class Plan
    {
        public final Survey survey;
        /** The Y of the ground block the build stands on. */
        public final int groundY;
        /** The staircase may reach this far outside the footprint. */
        public final int blendMargin;
        final int[] target;
        final boolean[] changed;
        /** The natural ground under the footprint: lowest, median and highest. */
        public final int lowest;
        public final int median;
        public final int highest;
        /** Footprint columns that are water or lava. */
        public final int liquidInFootprint;
        /** Water or lava columns close outside the footprint (within the water clearance asked for). */
        public final int liquidNear;
        /** Water or lava in the ring that the staircase would have to reshape (it never does). */
        public final int liquidInTheWay;
        /** Columns outside the footprint the staircase changes, and the most one changes. */
        public final int ringChanged;
        public final int maxRingChange;
        /** The farthest changed column, in blocks outside the footprint. */
        public final int ringReach;
        /** Columns at the ring's outer edge that would still need changing: the staircase doesn't fit in the ring. */
        public final int overflow;
        /** Neighbouring columns (at least one of them changed) more than one block apart afterwards. */
        public final int steepSteps;
        /** The deepest dip in the footprint's natural ground that would be filled up to the ground level (blocks). */
        public final int deepestDip;
        /** The deepest hidden cave right under the footprint's ground (blocks under the ground level; filled with stone, capped). */
        public final int deepestCave;
        /** The footprint's natural ground heights, lowest first. */
        private final int[] sortedHeights;
        /** Footprint blocks that would be cut away or filled in, all together. */
        public final int footprintWork;

        private Plan(Survey survey, int groundY, int blendMargin, int waterClearance)
        {
            this.survey = survey;
            this.groundY = groundY;
            this.blendMargin = Math.min(blendMargin, survey.margin - 1);
            this.target = new int[survey.surface.length];
            this.changed = new boolean[survey.surface.length];

            int[] heights = new int[survey.footprint.getXSpan() * survey.footprint.getZSpan()];
            int h = 0;
            int liquidIn = 0;
            int near = 0;
            int work = 0;
            boolean[] overflowAt = new boolean[target.length];
            // 1. The staircase: d blocks out, the natural height but never more than d above or below the build's level.
            for (int i = 0; i < survey.sizeX; i++)
            {
                for (int k = 0; k < survey.sizeZ; k++)
                {
                    int x = survey.minX + i;
                    int z = survey.minZ + k;
                    int index = i * survey.sizeZ + k;
                    int natural = survey.surface[index];
                    boolean wet = survey.liquid[index];
                    int d = survey.distance(x, z);
                    int t = natural;
                    if (d == 0)
                    {
                        heights[h++] = natural;
                        if (wet)
                        {
                            liquidIn++;
                        }
                        t = groundY;
                        work += Math.abs(natural - groundY);
                    }
                    else
                    {
                        if (wet && d <= waterClearance)
                        {
                            near++;
                        }
                        int wanted = Mth.clamp(natural, groundY - d, groundY + d);
                        if (d <= this.blendMargin && wanted != natural && !wet)
                        {
                            if (d == this.blendMargin)
                            {
                                overflowAt[index] = true; // the staircase must end inside the ring
                            }
                            else
                            {
                                t = wanted;
                            }
                        }
                    }
                    target[index] = t;
                    changed[index] = t != natural;
                }
            }
            // 2. Next to reshaped ground, the natural ground can still step by 2 or more: pull such neighbours to within one
            //    block, outwards as far as needed (never the footprint, never past the ring's edge, never water).
            ArrayDeque<Integer> queue = new ArrayDeque<>();
            boolean[] queued = new boolean[target.length];
            for (int index = 0; index < target.length; index++)
            {
                if (changed[index])
                {
                    queue.add(index);
                    queued[index] = true;
                }
            }
            int pops = 0;
            int limit = 40 * target.length;
            while (!queue.isEmpty() && pops++ < limit)
            {
                int index = queue.poll();
                queued[index] = false;
                int i = index / survey.sizeZ;
                int k = index % survey.sizeZ;
                for (Direction dir : Direction.Plane.HORIZONTAL)
                {
                    int ni = i + dir.getStepX();
                    int nk = k + dir.getStepZ();
                    if (ni < 0 || nk < 0 || ni >= survey.sizeX || nk >= survey.sizeZ)
                    {
                        continue;
                    }
                    int other = ni * survey.sizeZ + nk;
                    if (Math.abs(target[index] - target[other]) <= 1)
                    {
                        continue;
                    }
                    int d = survey.distance(survey.minX + ni, survey.minZ + nk);
                    if (d == 0 || survey.liquid[other])
                    {
                        continue; // the footprint is fixed; water is never reshaped (counted below)
                    }
                    if (d >= this.blendMargin)
                    {
                        overflowAt[other] = true;
                        continue;
                    }
                    target[other] = Mth.clamp(target[other], target[index] - 1, target[index] + 1);
                    changed[other] = target[other] != survey.surface[other];
                    if (!queued[other])
                    {
                        queue.add(other);
                        queued[other] = true;
                    }
                }
            }
            int inTheWay = 0;
            int ring = 0;
            int maxChange = 0;
            int reach = 0;
            int over = 0;
            for (int i = 0; i < survey.sizeX; i++)
            {
                for (int k = 0; k < survey.sizeZ; k++)
                {
                    int index = i * survey.sizeZ + k;
                    int d = survey.distance(survey.minX + i, survey.minZ + k);
                    if (overflowAt[index])
                    {
                        over++;
                    }
                    if (d == 0)
                    {
                        changed[index] = survey.surface[index] != groundY;
                        continue;
                    }
                    if (survey.liquid[index] && d <= this.blendMargin
                            && Math.abs(survey.surface[index] - groundY) > d)
                    {
                        inTheWay++;
                    }
                    if (changed[index])
                    {
                        ring++;
                        maxChange = Math.max(maxChange, Math.abs(target[index] - survey.surface[index]));
                        reach = Math.max(reach, d);
                    }
                }
            }
            Arrays.sort(heights, 0, h);
            this.sortedHeights = Arrays.copyOf(heights, h);
            this.lowest = h > 0 ? heights[0] : groundY;
            this.median = h > 0 ? heights[h / 2] : groundY;
            this.highest = h > 0 ? heights[h - 1] : groundY;
            this.liquidInFootprint = liquidIn;
            this.liquidNear = near;
            this.liquidInTheWay = inTheWay;
            this.ringChanged = ring;
            this.maxRingChange = maxChange;
            this.ringReach = reach;
            this.overflow = over;
            this.footprintWork = work;
            this.steepSteps = countSteepSteps();
            this.deepestDip = Math.max(0, groundY - this.lowest);
            this.deepestCave = findDeepestCave();
        }

        /** Highest minus lowest natural ground under the footprint. */
        public int variation()
        {
            return highest - lowest;
        }

        /**
         * Highest minus lowest natural ground under the footprint, leaving out the {@code ignored} lowest columns (small
         * dips, like a cave mouth or a little pit, which are simply filled).
         */
        public int variationIgnoringLowest(int ignored)
        {
            if (sortedHeights.length == 0)
            {
                return 0;
            }
            int i = Math.max(0, Math.min(ignored, sortedHeights.length - 1));
            return highest - sortedHeights[i];
        }

        /** How many footprint columns there are. */
        public int footprintColumns()
        {
            return sortedHeights.length;
        }

        /** True if the column is in the footprint or its ground changes (the "blending ring"). */
        public boolean isTouched(int x, int z)
        {
            if (!survey.contains(x, z))
            {
                return false;
            }
            return survey.distance(x, z) == 0 || changed[survey.index(x, z)];
        }

        /** True if the column is outside the footprint and its ground changes. */
        public boolean isRingChanged(int x, int z)
        {
            return survey.contains(x, z) && survey.distance(x, z) > 0 && changed[survey.index(x, z)];
        }

        /** The ground height the plan gives a surveyed column. */
        public int targetAt(int x, int z)
        {
            return target[survey.index(x, z)];
        }

        /** The natural ground height of a surveyed column. */
        public int naturalAt(int x, int z)
        {
            return survey.surface[survey.index(x, z)];
        }

        public String summary()
        {
            return String.format(Locale.ROOT, "ground level %d (natural ground under the footprint: lowest %d, median %d, highest %d, "
                            + "variation %d; %d water columns under it, %d close by); staircase: %d columns changed (up to %d blocks, "
                            + "reaching %d blocks out, ring %d), %d too far out, %d steep steps, %d water in the way; deepest dip to fill %d, "
                            + "deepest hidden cave under the ground %d",
                    groundY, lowest, median, highest, variation(), liquidInFootprint, liquidNear, ringChanged, maxRingChange,
                    ringReach, blendMargin, overflow, steepSteps, liquidInTheWay, deepestDip, deepestCave);
        }

        private int countSteepSteps()
        {
            int steep = 0;
            for (int i = 0; i < survey.sizeX; i++)
            {
                for (int k = 0; k < survey.sizeZ; k++)
                {
                    int index = i * survey.sizeZ + k;
                    int x = survey.minX + i;
                    int z = survey.minZ + k;
                    if (survey.distance(x, z) > blendMargin + 1)
                    {
                        continue;
                    }
                    boolean here = changed[index] || survey.distance(x, z) == 0;
                    if (i + 1 < survey.sizeX)
                    {
                        int other = (i + 1) * survey.sizeZ + k;
                        boolean there = changed[other] || survey.distance(x + 1, z) == 0;
                        if ((here || there) && Math.abs(target[index] - target[other]) > 1)
                        {
                            steep++;
                        }
                    }
                    if (k + 1 < survey.sizeZ)
                    {
                        int other = index + 1;
                        boolean there = changed[other] || survey.distance(x, z + 1) == 0;
                        if ((here || there) && Math.abs(target[index] - target[other]) > 1)
                        {
                            steep++;
                        }
                    }
                }
            }
            return steep;
        }

        /** The deepest cave or water pocket within MIN_FOUNDATION blocks under the footprint's ground (for the log). */
        private int findDeepestCave()
        {
            ServerLevel level = survey.level;
            BoundingBox box = survey.footprint;
            BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
            int deepest = 0;
            for (int x = box.minX(); x <= box.maxX(); x++)
            {
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                {
                    int natural = survey.surfaceAt(x, z);
                    int fill = 0;
                    for (int y = Math.min(natural, groundY) - 1; y >= groundY - MIN_FOUNDATION && y > level.getMinBuildHeight(); y--)
                    {
                        if (!isFirmGround(level, probe, x, y, z))
                        {
                            int bottom = y;
                            while (bottom > level.getMinBuildHeight() && !isFirmGround(level, probe, x, bottom, z) && groundY - bottom < 64)
                            {
                                bottom--;
                            }
                            fill = Math.max(fill, groundY - bottom);
                            break;
                        }
                    }
                    deepest = Math.max(deepest, fill);
                }
            }
            return deepest;
        }
    }

    /** Plans the footprint at the median natural height, with a staircase up to {@code blendMargin} blocks wide around it. */
    public static Plan plan(Survey survey, int blendMargin, int waterClearance)
    {
        BoundingBox box = survey.footprint;
        int[] heights = new int[box.getXSpan() * box.getZSpan()];
        int h = 0;
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                heights[h++] = survey.surfaceAt(x, z);
            }
        }
        Arrays.sort(heights, 0, h);
        return new Plan(survey, heights[h / 2], blendMargin, waterClearance);
    }

    /** Plans the footprint at the given ground level. */
    public static Plan plan(Survey survey, int groundY, int blendMargin, int waterClearance)
    {
        return new Plan(survey, groundY, blendMargin, waterClearance);
    }

    // ---------------------------------------------------------------- apply

    /**
     * What applying a plan did.
     *
     * @param felling what was removed whole (trees, huge mushrooms, ice spikes, boulders)
     */
    public record Report(TreeFelling.Result felling, int footprintCut, int footprintFilled, int ringCut, int ringFilled,
                         int removed, int placed, int dipsFilled, int sealed, int snowed)
    {
        public String summary()
        {
            return felling.summary() + "; footprint: " + footprintCut + " columns cut, " + footprintFilled + " filled, "
                    + dipsFilled + " gaps under it filled; staircase: " + ringCut + " columns cut, " + ringFilled + " filled; "
                    + removed + " blocks removed, " + placed + " placed, " + sealed + " water/lava blocks sealed, "
                    + snowed + " snow layers put back";
        }
    }

    /**
     * Applies a plan: fells what touches the footprint or the reshaped ring, levels the footprint (clearing everything
     * above its ground up to at least {@code clearTop}), shapes the staircase, seals off water and puts back snow.
     */
    public static Report apply(ServerLevel level, Plan plan, int clearTop)
    {
        Applier applier = new Applier(level, plan);
        TreeFelling.Result felling = TreeFelling.fell(level, plan);
        Survey s = plan.survey;
        for (int i = 0; i < s.sizeX; i++)
        {
            for (int k = 0; k < s.sizeZ; k++)
            {
                int x = s.minX + i;
                int z = s.minZ + k;
                int d = s.distance(x, z);
                if (d == 0)
                {
                    applier.footprintColumn(x, z, clearTop);
                }
                else if (plan.changed[i * s.sizeZ + k])
                {
                    applier.ringColumn(x, z);
                }
            }
        }
        applier.seal(clearTop);
        int snowed = 0;
        for (long column : felling.columns())
        {
            int x = BlockPos.getX(column);
            int z = BlockPos.getZ(column);
            if (!(s.contains(x, z) && s.distance(x, z) == 0) && snowIfCold(level, x, z))
            {
                snowed++;
            }
        }
        return new Report(felling, applier.footprintCut, applier.footprintFilled, applier.ringCut, applier.ringFilled,
                applier.removed, applier.placed, applier.dips, applier.sealed, applier.snowed + snowed);
    }

    /**
     * After the build is placed: in a cold biome, natural ground left open to the sky in the footprint (a churchyard)
     * gets a snow layer, like the snowy ground around it. Returns how many snow layers were placed.
     */
    public static int snowFootprint(ServerLevel level, Plan plan)
    {
        BoundingBox box = plan.survey.footprint;
        int snowed = 0;
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                if (snowIfCold(level, x, z))
                {
                    snowed++;
                }
            }
        }
        return snowed;
    }

    /** Does the cutting and filling, column by column, and keeps count. */
    private static final class Applier
    {
        final ServerLevel level;
        final Plan plan;
        final Survey s;
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        int footprintCut;
        int footprintFilled;
        int ringCut;
        int ringFilled;
        int removed;
        int placed;
        int dips;
        int sealed;
        int snowed;

        Applier(ServerLevel level, Plan plan)
        {
            this.level = level;
            this.plan = plan;
            this.s = plan.survey;
        }

        void footprintColumn(int x, int z, int clearTop)
        {
            int index = s.index(x, z);
            int natural = s.surface[index];
            int ground = plan.groundY;
            // Everything above the ground goes: terrain, plants, snow, whatever trees left behind.
            int top = Math.max(ShrineGround.topY(level, x, z), clearTop);
            for (int y = ground + 1; y <= top; y++)
            {
                set(x, y, z, AIR);
            }
            if (natural > ground)
            {
                footprintCut++;
            }
            else if (natural < ground)
            {
                footprintFilled++;
            }
            shapeTop(x, z, index, ground, natural);
            // At least MIN_FOUNDATION solid blocks under the ground: a cave under a thin crust is filled with stone
            // (stone never falls), at most CAVE_FILL_LIMIT blocks under the ground.
            for (int y = ground - 1; y >= ground - MIN_FOUNDATION && y > level.getMinBuildHeight(); y--)
            {
                if (!isFirmGround(level, probe, x, y, z))
                {
                    for (int fill = y; fill >= ground - CAVE_FILL_LIMIT && fill > level.getMinBuildHeight(); fill--)
                    {
                        if (isFirmGround(level, probe, x, fill, z))
                        {
                            break;
                        }
                        set(x, fill, z, STONE);
                    }
                    dips++;
                    break;
                }
            }
        }

        void ringColumn(int x, int z)
        {
            int index = s.index(x, z);
            int natural = s.surface[index];
            int t = plan.target[index];
            // Plants, snow layers, cacti... standing on the old ground go (trees were felled already).
            clearLooseAbove(x, natural, z);
            if (t < natural)
            {
                ringCut++;
                for (int y = t + 1; y <= natural; y++)
                {
                    set(x, y, z, AIR);
                }
            }
            else
            {
                ringFilled++;
            }
            shapeTop(x, z, index, t, natural);
            if (s.snow[index])
            {
                putSnow(x, t, z);
            }
        }

        /** Gives the column its new top at {@code ground}: cut ground gets the natural top, a dip is filled up to it. */
        private void shapeTop(int x, int z, int index, int ground, int natural)
        {
            BlockState topBlock = s.topFor(index);
            BlockState underBlock = s.underFor(index);
            if (natural < ground && !s.liquid[index])
            {
                // The old top is buried: grass turns to soil under it.
                BlockState old = level.getBlockState(pos.set(x, natural, z));
                if (isGrassLike(old))
                {
                    set(x, natural, z, underBlock);
                }
            }
            BlockState atGround = level.getBlockState(pos.set(x, ground, z));
            if (isFirmGround(level, probe, x, ground, z))
            {
                if (atGround != topBlock && (natural != ground || !isSurfaceBlock(atGround) || s.protruding[index]))
                {
                    set(x, ground, z, topBlock);
                }
                else if (atGround.hasProperty(BlockStateProperties.SNOWY) && atGround.getValue(BlockStateProperties.SNOWY))
                {
                    set(x, ground, z, atGround.setValue(BlockStateProperties.SNOWY, false)); // its snow is cleared
                }
                return;
            }
            // Air, water, a plant or loose sand at the new ground height: build it up from firm ground.
            set(x, ground, z, topBlock);
            fillDown(x, ground - 1, z, ground, index);
        }

        /** Fills the column from {@code fromY} down to firm ground: the column's soil near the top, stone deeper down. */
        private void fillDown(int x, int fromY, int z, int ground, int index)
        {
            BlockState underBlock = s.underFor(index);
            for (int y = fromY; y > level.getMinBuildHeight(); y--)
            {
                if (isFirmGround(level, probe, x, y, z))
                {
                    break;
                }
                set(x, y, z, ground - y <= SOIL_DEPTH ? underBlock : STONE);
            }
        }

        /** Removes the loose things standing on the natural ground (plants, snow layers, cacti, sugar cane...). */
        private void clearLooseAbove(int x, int natural, int z)
        {
            for (int y = natural + 1; y < level.getMaxBuildHeight(); y++)
            {
                BlockState state = level.getBlockState(pos.set(x, y, z));
                if (state.isAir() || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || !isLoose(state))
                {
                    return;
                }
                set(x, y, z, AIR);
            }
        }

        private void putSnow(int x, int ground, int z)
        {
            BlockState above = level.getBlockState(pos.set(x, ground + 1, z));
            BlockState snow = Blocks.SNOW.defaultBlockState();
            if (above.isAir() && snow.canSurvive(level, pos))
            {
                level.setBlock(pos, snow, FLAGS);
                placed++;
                snowed++;
                BlockState below = level.getBlockState(pos.set(x, ground, z));
                if (below.hasProperty(BlockStateProperties.SNOWY))
                {
                    level.setBlock(pos, below.setValue(BlockStateProperties.SNOWY, true), FLAGS);
                }
            }
        }

        /**
         * Water or lava right next to space that was cleared (above the footprint's ground, or where the staircase cut
         * down) would pour in: it's turned into the ground around it. With the site rules there is none.
         */
        void seal(int clearTop)
        {
            int highestNatural = Integer.MIN_VALUE;
            for (int value : s.surface)
            {
                highestNatural = Math.max(highestNatural, value);
            }
            for (int i = 0; i < s.sizeX; i++)
            {
                for (int k = 0; k < s.sizeZ; k++)
                {
                    int x = s.minX + i;
                    int z = s.minZ + k;
                    int index = i * s.sizeZ + k;
                    int d = s.distance(x, z);
                    int low;
                    int high;
                    if (d == 0)
                    {
                        low = plan.groundY + 1;
                        high = Math.min(clearTop, highestNatural + 1);
                    }
                    else if (plan.changed[index] && plan.target[index] < s.surface[index])
                    {
                        low = plan.target[index] + 1;
                        high = s.surface[index];
                    }
                    else
                    {
                        continue;
                    }
                    for (Direction dir : Direction.Plane.HORIZONTAL)
                    {
                        int nx = x + dir.getStepX();
                        int nz = z + dir.getStepZ();
                        if (s.contains(nx, nz) && s.distance(nx, nz) == 0)
                        {
                            continue; // cleared too
                        }
                        for (int y = low; y <= high; y++)
                        {
                            BlockState state = level.getBlockState(pos.set(nx, y, nz));
                            if (!state.getFluidState().isEmpty())
                            {
                                BlockState seal = s.contains(nx, nz) ? s.underFor(s.index(nx, nz)) : STONE;
                                level.setBlock(pos, seal, FLAGS);
                                sealed++;
                            }
                        }
                    }
                }
            }
        }

        private void set(int x, int y, int z, BlockState state)
        {
            BlockState old = level.getBlockState(pos.set(x, y, z));
            if (old == state)
            {
                return;
            }
            level.setBlock(pos, state, FLAGS);
            if (state.isAir())
            {
                removed++;
            }
            else
            {
                placed++;
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** How far a column is outside the footprint (0 inside it): the flat distance to the nearest footprint column, rounded down. */
    public static int distanceOutside(BoundingBox footprint, int x, int z)
    {
        int dx = Math.max(0, Math.max(footprint.minX() - x, x - footprint.maxX()));
        int dz = Math.max(0, Math.max(footprint.minZ() - z, z - footprint.maxZ()));
        if (dx == 0 || dz == 0)
        {
            return dx + dz;
        }
        return (int) Math.floor(Math.sqrt((double) dx * dx + (double) dz * dz));
    }

    /**
     * Natural ground for measuring: solid, dry, not a plant, a tree or a huge mushroom, not something simply replaced
     * (snow layers, grass), and not water ice (ice over water is water). Packed ice and mossy cobblestone count here;
     * the survey then tells ice spikes and boulders apart from flat ground.
     */
    static boolean isNaturalGround(ServerLevel level, BlockPos pos, BlockState state)
    {
        if (state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE))
        {
            return true;
        }
        return ShrineGround.isGround(level, pos, state);
    }

    /** Ice spikes are packed ice, boulders are mossy cobblestone. */
    static boolean isProtrusionMaterial(BlockState state)
    {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE) || state.is(Blocks.MOSSY_COBBLESTONE);
    }

    /** A natural top-of-the-ground block, the kind new ground in that column gets on top. */
    static boolean isSurfaceBlock(BlockState state)
    {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.MOSS_BLOCK) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND) || state.is(Blocks.SNOW_BLOCK)
                || state.is(Blocks.GRAVEL) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.MUD);
    }

    /** Ground that can go under the top block of new ground (never grass: that turns to dirt under a block). */
    static boolean isSoil(BlockState state)
    {
        return state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT) || state.is(Blocks.SAND) || state.is(Blocks.RED_SAND)
                || state.is(Blocks.SANDSTONE) || state.is(Blocks.RED_SANDSTONE) || state.is(Blocks.GRAVEL)
                || state.is(Blocks.SNOW_BLOCK) || state.is(Blocks.PACKED_ICE) || state.is(Blocks.STONE)
                || state.is(Blocks.CLAY) || state.is(BlockTags.TERRACOTTA);
    }

    private static boolean isGrassLike(BlockState state)
    {
        return state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.MYCELIUM);
    }

    /** Loose things on the ground: plants, snow layers and anything else simply replaced or without a full shape. */
    static boolean isLoose(BlockState state)
    {
        return ShrineGround.isTreeOrPlant(state) || state.canBeReplaced() || state.is(Blocks.SNOW);
    }

    private static BlockState withoutSnow(BlockState state)
    {
        return state.hasProperty(BlockStateProperties.SNOWY) ? state.setValue(BlockStateProperties.SNOWY, false) : state;
    }

    /**
     * Ground that holds a build up: natural ground, and for sand and gravel only if what's under them holds too (not a
     * cave or a water pocket they'd drop into).
     */
    static boolean isFirmGround(ServerLevel level, BlockPos.MutableBlockPos probe, int x, int y, int z)
    {
        BlockState state = level.getBlockState(probe.set(x, y, z));
        if (!isNaturalGround(level, probe, state))
        {
            return false;
        }
        if (!(state.getBlock() instanceof Fallable))
        {
            return true;
        }
        for (int below = y - 1; below > level.getMinBuildHeight(); below--)
        {
            BlockState under = level.getBlockState(probe.set(x, below, z));
            if (!(under.getBlock() instanceof Fallable))
            {
                return isNaturalGround(level, probe, under);
            }
        }
        return false;
    }

    /**
     * In a biome cold enough to snow, puts a snow layer on the column's top block if it's natural ground open to the
     * sky (grass, podzol, dirt), like the snow that fell everywhere else. Returns true if it placed one.
     */
    static boolean snowIfCold(ServerLevel level, int x, int z)
    {
        int topY = ShrineGround.topY(level, x, z);
        BlockPos ground = new BlockPos(x, topY, z);
        BlockState state = level.getBlockState(ground);
        if (!(state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.PODZOL) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)
                || state.is(Blocks.MOSS_BLOCK)))
        {
            return false;
        }
        BlockPos above = ground.above();
        if (!level.getBlockState(above).isAir() || !level.getBiome(above).value().coldEnoughToSnow(above))
        {
            return false;
        }
        BlockState snow = Blocks.SNOW.defaultBlockState();
        if (!snow.canSurvive(level, above))
        {
            return false;
        }
        level.setBlock(above, snow, FLAGS);
        if (state.hasProperty(BlockStateProperties.SNOWY))
        {
            level.setBlock(ground, state.setValue(BlockStateProperties.SNOWY, true), FLAGS);
        }
        return true;
    }
}
