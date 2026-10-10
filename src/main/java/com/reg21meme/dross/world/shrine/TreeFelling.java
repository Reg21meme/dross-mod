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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Blocks;
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

    /** Works out what touches and removes it. Run before the ground is changed. */
    static Result fell(ServerLevel level, GroundFit.Plan plan)
    {
        return new Felling(level, plan).run();
    }

    private static final class Felling
    {
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

        Felling(ServerLevel level, GroundFit.Plan plan)
        {
            this.level = level;
            this.plan = plan;
        }

        Result run()
        {
            scanTouchedColumns();
            for (BlockPos leaf : touchedLeaves)
            {
                for (BlockPos log : nearestLogs(leaf))
                {
                    addTree(log);
                }
            }
            int rounds = 0;
            Map<BlockPos, Integer> support = support();
            while (rounds < MAX_ROUNDS)
            {
                rounds++;
                int before = trees;
                for (Map.Entry<BlockPos, Integer> entry : support.entrySet())
                {
                    if (entry.getValue() <= LEAF_REACH && destroyed(entry.getKey()))
                    {
                        for (BlockPos log : nearestLogs(entry.getKey()))
                        {
                            addTree(log);
                        }
                    }
                }
                if (trees == before)
                {
                    break;
                }
                support = support();
            }

            // Remove: logs, then the leaves nothing holds up any more, then the rest.
            LongSet columns = new LongOpenHashSet();
            for (BlockPos log : logs)
            {
                level.setBlock(log, Blocks.AIR.defaultBlockState(), GroundFit.FLAGS);
                columns.add(BlockPos.asLong(log.getX(), 0, log.getZ()));
            }
            int leavesRemoved = 0;
            int leavesFixed = 0;
            List<BlockPos> removedLeaves = new ArrayList<>();
            for (Map.Entry<BlockPos, Integer> entry : support.entrySet())
            {
                BlockPos pos = entry.getKey();
                int distance = entry.getValue();
                BlockState state = level.getBlockState(pos);
                if (!state.is(BlockTags.LEAVES))
                {
                    continue;
                }
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
            for (BlockPos pos : others)
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), GroundFit.FLAGS);
                columns.add(BlockPos.asLong(pos.getX(), 0, pos.getZ()));
            }
            int attached = removeAttached(logs) + removeAttached(removedLeaves) + removeAttached(others);
            return new Result(trees, logs.size(), leavesRemoved, leavesFixed, mushrooms, mushroomBlocks, spikes, spikeBlocks,
                    rocks, rockBlocks, attached, rounds, columns, truncated);
        }

        /** Finds every log, leaf, huge mushroom, ice spike and boulder in a touched column, above its ground. */
        private void scanTouchedColumns()
        {
            GroundFit.Survey s = plan.survey;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < s.sizeX; i++)
            {
                for (int k = 0; k < s.sizeZ; k++)
                {
                    int x = s.minX + i;
                    int z = s.minZ + k;
                    if (!plan.isTouched(x, z))
                    {
                        continue;
                    }
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
            }
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
         * Every natural leaf within reach of a log being removed (plus the touched leaves), with its distance to the
         * nearest log that stays (7 = nothing holds it up), measured like vanilla does.
         */
        private Map<BlockPos, Integer> support()
        {
            // The region: leaves reachable from removed logs within LEAF_REACH steps, plus the touched leaves.
            Set<BlockPos> region = new HashSet<>();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            Map<BlockPos, Integer> steps = new HashMap<>();
            for (BlockPos log : logs)
            {
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
            while (!queue.isEmpty())
            {
                BlockPos pos = queue.poll();
                region.add(pos);
                int step = steps.get(pos);
                if (step >= LEAF_REACH)
                {
                    continue;
                }
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
            for (BlockPos leaf : touchedLeaves)
            {
                if (isNaturalLeaf(level.getBlockState(leaf)))
                {
                    region.add(leaf);
                }
            }

            // Distances: from logs that stay and from leaves outside the region (their distance is still right).
            Map<BlockPos, Integer> distance = new HashMap<>();
            List<List<BlockPos>> buckets = new ArrayList<>();
            for (int i = 0; i <= LEAF_REACH; i++)
            {
                buckets.add(new ArrayList<>());
            }
            for (BlockPos pos : region)
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
            for (int d = 1; d < LEAF_REACH; d++)
            {
                for (BlockPos pos : buckets.get(d))
                {
                    if (distance.get(pos) != d)
                    {
                        continue;
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
            return distance;
        }

        /** Removes vines, bee nests, cocoa and snow layers attached to removed blocks. Returns how many. */
        private int removeAttached(Iterable<BlockPos> removed)
        {
            int count = 0;
            for (BlockPos pos : removed)
            {
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
            }
            return count;
        }
    }

    private static boolean isNaturalLeaf(BlockState state)
    {
        return state.is(BlockTags.LEAVES) && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT);
    }

    static boolean isMushroom(BlockState state)
    {
        return state.is(Blocks.MUSHROOM_STEM) || state.is(Blocks.RED_MUSHROOM_BLOCK) || state.is(Blocks.BROWN_MUSHROOM_BLOCK);
    }

    static boolean isSpikeIce(BlockState state)
    {
        return state.is(Blocks.PACKED_ICE) || state.is(Blocks.BLUE_ICE);
    }

    private TreeFelling() {}
}
