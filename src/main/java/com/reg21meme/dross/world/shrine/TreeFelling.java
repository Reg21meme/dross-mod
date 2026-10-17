package com.reg21meme.dross.world.shrine;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Removes trees, huge mushrooms, ice spikes and mossy boulders that touch the footprint or the reshaped ring
 * ({@link GroundFit.Plan#isTouched}) <b>whole</b>, and leaves every other one alone, even where canopies touch.
 * <ul>
 *   <li>A tree is a connected piece of logs. It goes if one of its logs stands in a touched column, or if a leaf in a
 *       touched column belongs to it: a leaf belongs to its <b>nearest</b> trunk(s), found through the leaves the way
 *       vanilla measures a leaf's distance (up to 6). Nothing floods through a forest.</li>
 *   <li>Leaves that will be cleared anyway (above the footprint) never take a piece out of a tree that stays: if one
 *       is still held up by another trunk once its own tree is gone, that trunk goes too. Repeated until none is.</li>
 *   <li>Afterwards every leaf near a removed tree is measured again: the ones no remaining log holds up are removed at
 *       once (no floating leaves, no slow decay), and the others get their correct distance.</li>
 *   <li>Vines, bee nests, cocoa and snow layers on a removed block go with it.</li>
 *   <li>A huge mushroom is its connected stem and caps; an ice spike is its connected packed ice above the ground (the
 *       root in the ground stays, like ground); a boulder is its connected mossy cobblestone above the ground.</li>
 * </ul>
 */
public final class TreeFelling
{
    /** Leaves are held up by a log at most this many leaves away (vanilla: distance 7 means "no tree"). */
    static final int LEAF_REACH = LeavesBlock.DECAY_DISTANCE - 1;
    /** Safety limits on how big one tree, mushroom, spike or boulder can be. Bigger pieces are cut off at the limit (logged). */
    private static final int MAX_TREE_LOGS = 2000;
    private static final int MAX_MUSHROOM_BLOCKS = 800;
    private static final int MAX_SPIKE_BLOCKS = 8000;
    private static final int MAX_ROCK_BLOCKS = 300;
    /** At most this many rounds of "a cleared leaf is still held up by another trunk". */
    private static final int MAX_ROUNDS = 32;

    private static final Direction[] DIRECTIONS = Direction.values();

    /**
     * What was removed.
     *
     * @param columns   every column something was removed from (packed with {@link BlockPos#asLong}, Y = 0)
     * @param truncated true if a piece was bigger than its safety limit
     */
    public record Result(int trees, int logs, int leaves, int leavesFixed, int mushrooms, int mushroomBlocks, int spikes,
                         int spikeBlocks, int rocks, int rockBlocks, int attached, int rounds, LongSet columns, boolean truncated)
    {
        public String summary()
        {
            return "removed " + trees + " trees (" + logs + " logs, " + leaves + " leaves; " + leavesFixed + " leaves of other trees "
                    + "re-measured), " + mushrooms + " huge mushrooms (" + mushroomBlocks + " blocks), " + spikes + " ice spikes ("
                    + spikeBlocks + " blocks), " + rocks + " boulders (" + rockBlocks + " blocks), " + attached
                    + " vines/bee nests/snow on them" + (truncated ? " (a piece hit its size limit)" : "");
        }
    }

    /**
     * Works out what touches and removes it, a piece at a time ({@link #step}), so a big felling (the Dross hub's: about
     * 120 trees, 20,000 blocks) can be spread over several server ticks. The order is fixed, and the same whether it's
     * done in one go or in many steps:
     * <ol>
     *   <li><b>working out</b> (reads the world, changes nothing): scan the touched columns, find the trees their leaves
     *       belong to, then the rounds of "a cleared leaf is still held up by another trunk" (each measuring how far
     *       every leaf near a removed tree is from a log that stays);</li>
     *   <li><b>removing</b>: the logs, then the leaves nothing holds up any more (and the others' distances corrected),
     *       then the huge mushrooms, ice spikes and boulders, then the vines, bee nests and snow on all of them.</li>
     * </ol>
     * Every set and map is filled in the same order as in one go, and gone through in its own order, so the result is
     * exactly the same as long as nothing else changes these blocks between the steps. Run it before the ground is
     * changed. Server thread only.
     */
    static final class Felling
    {
        private enum Stage
        {
            SCAN, TOUCHED_LEAVES, SUPPORT, ROUND, REMOVE_LOGS, REMOVE_LEAVES, REMOVE_OTHERS, ATTACHED, DONE
        }

        final ServerLevel level;
        final GroundFit.Plan plan;
        final Set<BlockPos> logs = new HashSet<>();
        final List<BlockPos> touchedLeaves = new ArrayList<>();
        final Set<BlockPos> others = new HashSet<>();
        int trees;
        int mushrooms;
        int mushroomBlocks;
        int spikes;
        int spikeBlocks;
        int rocks;
        int rockBlocks;
        boolean truncated;

        private Stage stage = Stage.SCAN;
        /** Where the current stage has got to (a column index, or an index into the list it goes through). */
        private int cursor;
        private int rounds;
        private int treesBeforeRound;
        /** Every leaf near a removed log with its distance to a log that stays, once measured ({@link Support}). */
        private Map<BlockPos, Integer> support = Map.of();
        @Nullable
        private Support measuring;
        /** The support map's entries, in its own order, while a round or the leaf removal goes through them. */
        private List<Map.Entry<BlockPos, Integer>> entries = List.of();
        /** The logs and the other pieces to remove, in their sets' own order. */
        private List<BlockPos> logList = List.of();
        private List<BlockPos> otherList = List.of();
        private final List<BlockPos> removedLeaves = new ArrayList<>();
        private final LongSet columns = new LongOpenHashSet();
        private int leavesRemoved;
        private int leavesFixed;
        private int attached;
        /** Which removed blocks the vines and nests are looked for round: 0 the logs, 1 the leaves, 2 the other pieces. */
        private int attachedPart;
        private long nanosWorkingOut;
        private long nanosRemoving;
        @Nullable
        private Result result;

        Felling(ServerLevel level, GroundFit.Plan plan)
        {
            this.level = level;
            this.plan = plan;
        }

        /**
         * Does the next pieces of work until {@code budget} is spent (at least one piece).
         *
         * @return true once everything is removed ({@link #result})
         */
        boolean step(GroundFit.Budget budget)
        {
            while (stage != Stage.DONE)
            {
                Stage now = stage;
                long start = System.nanoTime();
                boolean finished = switch (now)
                {
                    case SCAN -> scan(budget);
                    case TOUCHED_LEAVES -> touchedLeaves(budget);
                    case SUPPORT -> measureSupport(budget);
                    case ROUND -> round(budget);
                    case REMOVE_LOGS -> removeLogs(budget);
                    case REMOVE_LEAVES -> removeLeaves(budget);
                    case REMOVE_OTHERS -> removeOthers(budget);
                    default -> removeAttached(budget);
                };
                long took = System.nanoTime() - start;
                if (now.ordinal() >= Stage.REMOVE_LOGS.ordinal())
                {
                    nanosRemoving += took;
                }
                else
                {
                    nanosWorkingOut += took;
                }
                if (!finished || budget.spent())
                {
                    break;
                }
            }
            return stage == Stage.DONE;
        }

        /** True once the first block has been removed. */
        boolean changedWorld()
        {
            return stage.ordinal() >= Stage.REMOVE_LOGS.ordinal();
        }

        /** What was removed; only once it's done. */
        Result result()
        {
            if (result == null)
            {
                throw new IllegalStateException("The felling isn't done yet");
            }
            return result;
        }

        /** Time spent working out what goes, and removing it, in nanoseconds (for the log). */
        long nanosWorkingOut()
        {
            return nanosWorkingOut;
        }

        long nanosRemoving()
        {
            return nanosRemoving;
        }

        // ------------------------------------------------------------ working out what goes (nothing changes)

        /** Finds every log, leaf, huge mushroom, ice spike and boulder in a touched column, above its ground. */
        private boolean scan(GroundFit.Budget budget)
        {
            GroundFit.Survey s = plan.survey;
            int total = s.sizeX * s.sizeZ;
            while (cursor < total)
            {
                int i = cursor / s.sizeZ;
                int k = cursor % s.sizeZ;
                cursor++;
                int x = s.minX + i;
                int z = s.minZ + k;
                if (!plan.isTouched(x, z))
                {
                    continue;
                }
                scanColumn(x, z);
                if (cursor < total && budget.spent())
                {
                    return false;
                }
            }
            cursor = 0;
            stage = Stage.TOUCHED_LEAVES;
            return true;
        }

        private void scanColumn(int x, int z)
        {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            int natural = plan.naturalAt(x, z);
            int ground = Math.min(natural, plan.targetAt(x, z));
            int top = ShrineGround.topY(level, x, z);
            for (int y = ground + 1; y <= top; y++)
            {
                BlockState state = level.getBlockState(pos.set(x, y, z));
                if (state.isAir())
                {
                    continue;
                }
                if (state.is(BlockTags.LOGS))
                {
                    addTree(pos.immutable());
                }
                else if (state.is(BlockTags.LEAVES))
                {
                    if (isNaturalLeaf(state))
                    {
                        touchedLeaves.add(pos.immutable());
                    }
                }
                else if (isMushroom(state))
                {
                    if (!others.contains(pos))
                    {
                        mushrooms++;
                        mushroomBlocks += addPiece(pos.immutable(), TreeFelling::isMushroom, Integer.MIN_VALUE, MAX_MUSHROOM_BLOCKS);
                    }
                }
                else if (isSpikeIce(state) && y > natural)
                {
                    if (!others.contains(pos))
                    {
                        spikes++;
                        spikeBlocks += addPiece(pos.immutable(), TreeFelling::isSpikeIce, natural, MAX_SPIKE_BLOCKS);
                    }
                }
                else if (state.is(Blocks.MOSSY_COBBLESTONE) && y > natural)
                {
                    if (!others.contains(pos))
                    {
                        rocks++;
                        rockBlocks += addPiece(pos.immutable(), b -> b.is(Blocks.MOSSY_COBBLESTONE), natural, MAX_ROCK_BLOCKS);
                    }
                }
            }
        }

        /** Each touched leaf takes the tree(s) it belongs to: its nearest trunk(s). */
        private boolean touchedLeaves(GroundFit.Budget budget)
        {
            while (cursor < touchedLeaves.size())
            {
                BlockPos leaf = touchedLeaves.get(cursor++);
                for (BlockPos log : nearestLogs(leaf))
                {
                    addTree(log);
                }
                if (cursor < touchedLeaves.size() && budget.spent())
                {
                    return false;
                }
            }
            measuring = new Support();
            stage = Stage.SUPPORT;
            return true;
        }

        /** Measures the support map, then starts the next round (at most {@link #MAX_ROUNDS}) or the removal. */
        private boolean measureSupport(GroundFit.Budget budget)
        {
            if (measuring == null)
            {
                measuring = new Support();
            }
            if (!measuring.step(budget))
            {
                return false;
            }
            support = measuring.distance;
            measuring = null;
            if (rounds < MAX_ROUNDS)
            {
                rounds++;
                treesBeforeRound = trees;
                entries = new ArrayList<>(support.entrySet());
                cursor = 0;
                stage = Stage.ROUND;
            }
            else
            {
                startRemoving();
            }
            return true;
        }

        /**
         * One round: a leaf that will be cleared anyway but is still held up by another trunk takes that trunk's tree too.
         * If no tree was added, that's the last round; otherwise the support map is measured again.
         */
        private boolean round(GroundFit.Budget budget)
        {
            while (cursor < entries.size())
            {
                Map.Entry<BlockPos, Integer> entry = entries.get(cursor++);
                if (entry.getValue() <= LEAF_REACH && destroyed(entry.getKey()))
                {
                    for (BlockPos log : nearestLogs(entry.getKey()))
                    {
                        addTree(log);
                    }
                }
                if (cursor < entries.size() && budget.spent())
                {
                    return false;
                }
            }
            if (trees == treesBeforeRound)
            {
                startRemoving();
            }
            else
            {
                measuring = new Support();
                stage = Stage.SUPPORT;
            }
            return true;
        }

        // ------------------------------------------------------------ removing it

        private void startRemoving()
        {
            logList = new ArrayList<>(logs);
            cursor = 0;
            stage = Stage.REMOVE_LOGS;
        }

        private boolean removeLogs(GroundFit.Budget budget)
        {
            while (cursor < logList.size())
            {
                BlockPos log = logList.get(cursor++);
                level.setBlock(log, Blocks.AIR.defaultBlockState(), GroundFit.FLAGS);
                columns.add(BlockPos.asLong(log.getX(), 0, log.getZ()));
                if (cursor < logList.size() && budget.spent())
                {
                    return false;
                }
            }
            entries = new ArrayList<>(support.entrySet());
            cursor = 0;
            stage = Stage.REMOVE_LEAVES;
            return true;
        }

        /** The leaves nothing holds up any more go; the others get their correct distance. */
        private boolean removeLeaves(GroundFit.Budget budget)
        {
            while (cursor < entries.size())
            {
                Map.Entry<BlockPos, Integer> entry = entries.get(cursor++);
                BlockPos pos = entry.getKey();
                int distance = entry.getValue();
                BlockState state = level.getBlockState(pos);
                if (state.is(BlockTags.LEAVES))
                {
                    if (distance > LEAF_REACH)
                    {
                        level.setBlock(pos, Blocks.AIR.defaultBlockState(), GroundFit.FLAGS);
                        removedLeaves.add(pos);
                        leavesRemoved++;
                    }
                    else if (state.hasProperty(LeavesBlock.DISTANCE) && state.getValue(LeavesBlock.DISTANCE) != distance)
                    {
                        level.setBlock(pos, state.setValue(LeavesBlock.DISTANCE, distance), GroundFit.FLAGS);
                        leavesFixed++;
                    }
                }
                if (cursor < entries.size() && budget.spent())
                {
                    return false;
                }
            }
            entries = List.of();
            otherList = new ArrayList<>(others);
            cursor = 0;
            stage = Stage.REMOVE_OTHERS;
            return true;
        }

        private boolean removeOthers(GroundFit.Budget budget)
        {
            while (cursor < otherList.size())
            {
                BlockPos pos = otherList.get(cursor++);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), GroundFit.FLAGS);
                columns.add(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
                if (cursor < otherList.size() && budget.spent())
                {
                    return false;
                }
            }
            attachedPart = 0;
            cursor = 0;
            stage = Stage.ATTACHED;
            return true;
        }

        /** Vines, bee nests, cocoa and snow layers on the removed logs, then leaves, then other pieces. */
        private boolean removeAttached(GroundFit.Budget budget)
        {
            while (attachedPart < 3)
            {
                List<BlockPos> removed = attachedPart == 0 ? logList : attachedPart == 1 ? removedLeaves : otherList;
                if (cursor >= removed.size())
                {
                    attachedPart++;
                    cursor = 0;
                    continue;
                }
                attached += removeAttachedAt(removed.get(cursor++));
                if (budget.spent())
                {
                    return false;
                }
            }
            result = new Result(trees, logs.size(), leavesRemoved, leavesFixed, mushrooms, mushroomBlocks, spikes, spikeBlocks,
                    rocks, rockBlocks, attached, rounds, columns, truncated);
            stage = Stage.DONE;
            return true;
        }

        /** True for a block the earthwork will clear or bury: above the footprint's ground, or in the staircase's cut or fill. */
        private boolean destroyed(BlockPos pos)
        {
            GroundFit.Survey s = plan.survey;
            int x = pos.getX();
            int z = pos.getZ();
            if (!s.contains(x, z))
            {
                return false;
            }
            if (s.distance(x, z) == 0)
            {
                return pos.getY() > plan.groundY;
            }
            if (plan.isRingChanged(x, z))
            {
                return pos.getY() <= Math.max(plan.naturalAt(x, z), plan.targetAt(x, z));
            }
            return false;
        }

        /** Adds the tree (connected logs, corners included) that {@code start} belongs to, if it isn't in yet. */
        private void addTree(BlockPos start)
        {
            if (logs.contains(start))
            {
                return;
            }
            trees++;
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            logs.add(start);
            int count = 0;
            while (!queue.isEmpty())
            {
                BlockPos log = queue.poll();
                if (++count > MAX_TREE_LOGS)
                {
                    truncated = true;
                    return;
                }
                for (BlockPos next : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1)))
                {
                    if (!logs.contains(next) && level.getBlockState(next).is(BlockTags.LOGS))
                    {
                        BlockPos found = next.immutable();
                        logs.add(found);
                        queue.add(found);
                    }
                }
            }
        }

        /** Adds a connected piece (faces touching) of blocks matching {@code kind} above {@code aboveY}. Returns its size. */
        private int addPiece(BlockPos start, Predicate<BlockState> kind, int aboveY, int limit)
        {
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            others.add(start);
            int count = 0;
            while (!queue.isEmpty())
            {
                BlockPos pos = queue.poll();
                if (++count > limit)
                {
                    truncated = true;
                    break;
                }
                for (Direction dir : DIRECTIONS)
                {
                    BlockPos next = pos.relative(dir);
                    if (next.getY() > aboveY && !others.contains(next) && kind.test(level.getBlockState(next)))
                    {
                        others.add(next);
                        queue.add(next);
                    }
                }
            }
            return count;
        }

        /**
         * The trunk logs nearest to a leaf, measured through leaves (faces touching) the way vanilla measures a leaf's
         * distance, ignoring logs already being removed. Empty if no log is within {@link #LEAF_REACH}.
         */
        private List<BlockPos> nearestLogs(BlockPos leaf)
        {
            Set<BlockPos> seen = new HashSet<>();
            seen.add(leaf);
            List<BlockPos> frontier = List.of(leaf);
            for (int depth = 1; depth <= LEAF_REACH && !frontier.isEmpty(); depth++)
            {
                List<BlockPos> found = new ArrayList<>();
                List<BlockPos> next = new ArrayList<>();
                for (BlockPos pos : frontier)
                {
                    for (Direction dir : DIRECTIONS)
                    {
                        BlockPos other = pos.relative(dir);
                        if (!seen.add(other))
                        {
                            continue;
                        }
                        BlockState state = level.getBlockState(other);
                        if (state.is(BlockTags.LOGS))
                        {
                            if (!logs.contains(other))
                            {
                                found.add(other);
                            }
                        }
                        else if (state.is(BlockTags.LEAVES))
                        {
                            next.add(other);
                        }
                    }
                }
                if (!found.isEmpty())
                {
                    return found;
                }
                frontier = next;
            }
            return List.of();
        }

        /**
         * Measures the support map, a piece at a time: every natural leaf within reach of a log being removed (plus the
         * touched leaves), with its distance to the nearest log that stays (7 = nothing holds it up), measured like
         * vanilla does. The logs being removed don't change while it's measured.
         */
        private final class Support
        {
            /** 0: start from the removed logs; 1: spread through the leaves; 2: add the touched leaves; 3: measure; 4: pass distances on. */
            private int part;
            private int cursor;
            /** The distance being passed on in part 4. */
            private int d = 1;
            /** The removed logs, in their set's own order. */
            private final List<BlockPos> seeds = new ArrayList<>(logs);
            /** The region: leaves reachable from removed logs within LEAF_REACH steps, plus the touched leaves. */
            private final Set<BlockPos> region = new HashSet<>();
            private final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            private final Map<BlockPos, Integer> steps = new HashMap<>();
            /** The region in its set's own order, while it's measured. */
            private List<BlockPos> regionList = List.of();
            final Map<BlockPos, Integer> distance = new HashMap<>();
            private final List<List<BlockPos>> buckets = new ArrayList<>();

            /** @return true once {@link #distance} is complete */
            boolean step(GroundFit.Budget budget)
            {
                while (part < 5)
                {
                    if (part == 0)
                    {
                        if (cursor >= seeds.size())
                        {
                            part = 1;
                            continue;
                        }
                        BlockPos log = seeds.get(cursor++);
                        for (Direction dir : DIRECTIONS)
                        {
                            BlockPos next = log.relative(dir);
                            if (!steps.containsKey(next) && isNaturalLeaf(level.getBlockState(next)))
                            {
                                steps.put(next, 1);
                                queue.add(next);
                            }
                        }
                    }
                    else if (part == 1)
                    {
                        if (queue.isEmpty())
                        {
                            part = 2;
                            cursor = 0;
                            continue;
                        }
                        BlockPos pos = queue.poll();
                        region.add(pos);
                        int step = steps.get(pos);
                        if (step < LEAF_REACH)
                        {
                            for (Direction dir : DIRECTIONS)
                            {
                                BlockPos next = pos.relative(dir);
                                if (!steps.containsKey(next) && isNaturalLeaf(level.getBlockState(next)))
                                {
                                    steps.put(next, step + 1);
                                    queue.add(next);
                                }
                            }
                        }
                    }
                    else if (part == 2)
                    {
                        if (cursor >= touchedLeaves.size())
                        {
                            // Distances: from logs that stay and from leaves outside the region (their distance is still right).
                            regionList = new ArrayList<>(region);
                            for (int i = 0; i <= LEAF_REACH; i++)
                            {
                                buckets.add(new ArrayList<>());
                            }
                            part = 3;
                            cursor = 0;
                            continue;
                        }
                        BlockPos leaf = touchedLeaves.get(cursor++);
                        if (isNaturalLeaf(level.getBlockState(leaf)))
                        {
                            region.add(leaf);
                        }
                    }
                    else if (part == 3)
                    {
                        if (cursor >= regionList.size())
                        {
                            regionList = List.of();
                            part = 4;
                            cursor = 0;
                            d = 1;
                            continue;
                        }
                        measure(regionList.get(cursor++));
                    }
                    else
                    {
                        if (d >= LEAF_REACH)
                        {
                            part = 5;
                            break;
                        }
                        List<BlockPos> bucket = buckets.get(d);
                        if (cursor >= bucket.size())
                        {
                            d++;
                            cursor = 0;
                            continue;
                        }
                        passOn(bucket.get(cursor++));
                    }
                    if (budget.spent())
                    {
                        return false;
                    }
                }
                return true;
            }

            private void measure(BlockPos pos)
            {
                int best = LEAF_REACH + 1;
                for (Direction dir : DIRECTIONS)
                {
                    BlockPos next = pos.relative(dir);
                    if (region.contains(next))
                    {
                        continue;
                    }
                    BlockState state = level.getBlockState(next);
                    if (state.is(BlockTags.LOGS))
                    {
                        if (!logs.contains(next))
                        {
                            best = 1;
                            break;
                        }
                    }
                    else if (state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.DISTANCE))
                    {
                        best = Math.min(best, state.getValue(LeavesBlock.DISTANCE) + 1);
                    }
                }
                distance.put(pos, best);
                if (best <= LEAF_REACH)
                {
                    buckets.get(best).add(pos);
                }
            }

            /** A leaf at distance {@link #d} gives its neighbours in the region distance d + 1, if that's shorter. */
            private void passOn(BlockPos pos)
            {
                if (distance.get(pos) != d)
                {
                    return;
                }
                for (Direction dir : DIRECTIONS)
                {
                    BlockPos next = pos.relative(dir);
                    Integer current = distance.get(next);
                    if (current != null && current > d + 1)
                    {
                        distance.put(next, d + 1);
                        buckets.get(d + 1).add(next);
                    }
                }
            }
        }

        /** Removes vines, bee nests, cocoa and snow layers attached to one removed block. Returns how many. */
        private int removeAttachedAt(BlockPos pos)
        {
            int count = 0;
            for (Direction dir : DIRECTIONS)
            {
                BlockPos next = pos.relative(dir);
                BlockState state = level.getBlockState(next);
                if (state.getBlock() instanceof VineBlock)
                {
                    // The vine and everything hanging under it.
                    BlockPos.MutableBlockPos chain = next.mutable();
                    while (level.getBlockState(chain).getBlock() instanceof VineBlock)
                    {
                        level.setBlock(chain, Blocks.AIR.defaultBlockState(), GroundFit.FLAGS);
                        count++;
                        chain.move(Direction.DOWN);
                    }
                }
                else if (state.is(BlockTags.BEEHIVES) || state.is(Blocks.COCOA) || (dir == Direction.UP && state.is(Blocks.SNOW)))
                {
                    level.setBlock(next, Blocks.AIR.defaultBlockState(), GroundFit.FLAGS);
                    count++;
                }
            }
            return count;
        }
    }

    private static boolean isNaturalLeaf(BlockState state)
    {
        return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
    }

    /**
     * A huge mushroom's block: any {@link HugeMushroomBlock}, so vanilla's stems and caps and other mushrooms built the
     * same way (the Dross's giant blightcaps).
     */
    static boolean isMushroom(BlockState state)
    {
        return state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.getBlock() instanceof HugeMushroomBlock;
    }

    static boolean isSpikeIce(BlockState state)
    {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    private TreeFelling() {}
}
