package com.reg21meme.dross.dimension.worldgen;

import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.mojang.serialization.Codec;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;

/**
 * Feature {@code dross:tidy_up}: the last feature of every Dross chunk, which tidies up after the others.
 *
 * <p>Why: Minecraft decorates the chunks one at a time, and a feature may reach a few blocks into the chunks round its
 * own, some already decorated and some not yet. So a patch of ferns can spill into a neighbour that later turns that
 * ground into bare rock (or a pond shore, or a vent); a tree's crown can grow over the top of an older tree's hanging
 * Stormvine; and a tree sees only its own trunk when it works out how far each of its leaves is from a log, so where
 * another tree's trunk stands in its crown, or one of its branches had no room, a few leaves come out marked "too far
 * from a log" and would decay.
 *
 * <p>What it does: it looks over the 3 x 3 chunks round its own (all that this chunk's features could have touched),
 * every column from the top down to the ground (at least down to the land's natural top, as the terrain made it, so it
 * also finds what a boulder or an ice spike has closed in, like a snow layer sealed inside a spike):
 * <ul>
 *   <li>anything that couldn't stay where it is (a plant on the wrong ground, a vine or lichen hanging from nothing, a
 *       snow layer on ice...) is removed (from the top down, so a whole loose strand goes);</li>
 *   <li>natural leaves marked too far from a log get their real distance (measured through leaves, the way the game
 *       does, to any log), or are removed if no log holds them up (with any vine hanging from them).</li>
 * </ul>
 * Nothing else is changed: logs, ground, water and lava are never touched, and nor are the fittings only structures
 * put down (ladders, banners, candles: {@link #isBuiltFitting}), since a structure built across a chunk border isn't
 * finished until its next chunk is.
 */
public class TidyUpFeature extends Feature<NoneFeatureConfiguration>
{
    /** How far round its own chunk it looks (chunks): one chunk every way, as far as features may write. */
    private static final int REACH_CHUNKS = 1;
    /** Leaves are held up by a log at most this many leaves away (vanilla: distance 7 means "no log"). */
    private static final int LEAF_REACH = LeavesBlock.DECAY_DISTANCE - 1;
    /** Most blocks searched from one loose leaf looking for a log. */
    private static final int MAX_LEAF_SEARCH = 4000;

    private static final Direction[] DIRECTIONS = Direction.values();

    /** How many tidy-ups have run and how long they took in all (nanoseconds): only for the headless check's log. */
    private static final AtomicLong RUNS = new AtomicLong();
    private static final AtomicLong NANOS = new AtomicLong();

    public TidyUpFeature(Codec<NoneFeatureConfiguration> codec)
    {
        super(codec);
    }

    /** {runs, nanoseconds}: how many tidy-ups have run since the server started and how long they took in all. */
    public static long[] timing()
    {
        return new long[] {RUNS.get(), NANOS.get()};
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
    {
        long start = System.nanoTime();
        try
        {
            return tidy(context);
        }
        finally
        {
            NANOS.addAndGet(System.nanoTime() - start);
            RUNS.incrementAndGet();
        }
    }

    /** The hunter villages whose ground reaches into the chunks round {@code centre} (none unless one is Rimefall). */
    private static List<RimefallVillages.VillagePlan> villagesNear(WorldGenLevel level, ChunkPos centre)
    {
        if (!level.getLevel().dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return List.of();
        }
        BossSites sites = BossSites.forLevel(level.getLevel());
        boolean rimefall = false;
        for (int cx = centre.x - REACH_CHUNKS; cx <= centre.x + REACH_CHUNKS && !rimefall; cx++)
        {
            for (int cz = centre.z - REACH_CHUNKS; cz <= centre.z + REACH_CHUNKS && !rimefall; cz++)
            {
                rimefall = sites.patchAt(DrossBiomes.RIMEFALL, (cx << 4) + 8, (cz << 4) + 8).isPresent();
            }
        }
        return rimefall ? RimefallVillages.villagesAround(sites, centre.x, centre.z, REACH_CHUNKS, 0) : List.of();
    }

    private static boolean inVillage(List<RimefallVillages.VillagePlan> villages, int x, int z)
    {
        for (RimefallVillages.VillagePlan v : villages)
        {
            if (v.covers(x, z, 0))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean tidy(FeaturePlaceContext<NoneFeatureConfiguration> context)
    {
        WorldGenLevel level = context.level();
        ChunkPos centre = new ChunkPos(context.origin());
        int minY = level.getMinBuildHeight();
        boolean changed = false;
        // a hunter village is left as it's built (its wall skulls, lanterns and crops stand on blocks that may be in a
        // neighbouring chunk not built yet): October 23, 2026
        List<RimefallVillages.VillagePlan> villages = villagesNear(level, centre);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int cx = centre.x - REACH_CHUNKS; cx <= centre.x + REACH_CHUNKS; cx++)
        {
            for (int cz = centre.z - REACH_CHUNKS; cz <= centre.z + REACH_CHUNKS; cz++)
            {
                if (!level.hasChunk(cx, cz))
                {
                    continue;
                }
                ChunkAccess chunk = level.getChunk(cx, cz);
                // The top of the highest section that has anything in it: no block of this chunk is higher.
                int highest = chunk.getHighestFilledSectionIndex();
                if (highest < 0)
                {
                    continue;
                }
                int top = Math.min(SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(highest)) + 15, level.getMaxBuildHeight() - 1);
                int x0 = cx << 4;
                int z0 = cz << 4;
                for (int x = x0; x < x0 + 16; x++)
                {
                    for (int z = z0; z < z0 + 16; z++)
                    {
                        if (inVillage(villages, x, z))
                        {
                            continue;
                        }
                        // the land's top as the terrain made it (world generation's own heightmap: features never change it)
                        int naturalTop = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x & 15, z & 15);
                        changed |= tidyColumn(level, chunk, pos, x, z, top, minY, naturalTop);
                    }
                }
            }
        }
        return changed;
    }

    /**
     * Tidies one column, from {@code top} down to the ground (stopping at the first ground block at or under
     * {@code naturalTop}, never going below {@code bottom}). {@code blocks} is what it reads from (the column's chunk, or
     * the level). Returns true if it changed anything. (Public for the headless check, SewersGameTest.)
     */
    public static boolean tidyColumn(WorldGenLevel level, BlockGetter blocks, BlockPos.MutableBlockPos pos, int x, int z,
                                     int top, int bottom, int naturalTop)
    {
        boolean changed = false;
        for (int y = top; y > bottom; y--)
        {
            BlockState state = blocks.getBlockState(pos.set(x, y, z));
            if (state.isAir())
            {
                continue;
            }
            if (isNaturalLeaf(state) && state.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE)
            {
                changed |= settleLeaf(level, pos.immutable(), state);
            }
            else if (state.getFluidState().isEmpty() && !isBuiltFitting(state) && !state.canSurvive(level, pos))
            {
                level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                changed = true;
                continue;
            }
            if (y <= naturalTop && isGround(level, pos, state))
            {
                break; // the ground: nothing below it needs looking at
            }
        }
        return changed;
    }

    /**
     * A fitting only built things use, never a feature: a ladder, a banner, a candle (the sewers' pipes and the Rat King's
     * road). The tidy-up leaves them alone even when they look loose, because a structure is built one chunk at a time:
     * a pipe's ladder in the last column of one chunk hangs on the pipe's wall in the next chunk, and until that chunk's
     * part of the pipe is built there's only air (or a hillside) behind it. (Before October 16 the tidy-up pulled such a
     * ladder down as far as the hillside behind it reached: a gap at the top of the climb out.)
     */
    public static boolean isBuiltFitting(BlockState state)
    {
        return state.is(Blocks.LADDER) || state.is(BlockTags.BANNERS) || state.is(BlockTags.CANDLES);
    }

    /** A leaf marked too far from a log: its real distance, or (if no log holds it up) it goes. Returns true if it changed anything. */
    private static boolean settleLeaf(WorldGenLevel level, BlockPos leaf, BlockState state)
    {
        int distance = distanceToLog(level, leaf);
        if (distance <= LEAF_REACH)
        {
            level.setBlock(leaf, state.setValue(LeavesBlock.DISTANCE, distance), Block.UPDATE_CLIENTS);
            return true;
        }
        level.setBlock(leaf, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
        // Anything that hung from it (a strand of Stormvine) or stood on it.
        for (Direction dir : DIRECTIONS)
        {
            dropLoose(level, leaf.relative(dir));
        }
        return true;
    }

    /** Removes the block at pos if it can't stay any more, and the loose blocks hanging under it. */
    private static void dropLoose(WorldGenLevel level, BlockPos start)
    {
        BlockPos.MutableBlockPos pos = start.mutable();
        for (int i = 0; i < 32; i++)
        {
            BlockState state = level.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.canSurvive(level, pos) || isNaturalLeaf(state)
                    || state.is(BlockTags.LOGS) || isBuiltFitting(state))
            {
                return;
            }
            level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
            pos.move(Direction.DOWN);
        }
    }

    /** How many leaves away the nearest log is (through leaves, faces touching), or more than LEAF_REACH if none is in reach. */
    private static int distanceToLog(WorldGenLevel level, BlockPos leaf)
    {
        Set<BlockPos> seen = new HashSet<>();
        seen.add(leaf);
        List<BlockPos> frontier = List.of(leaf);
        for (int depth = 1; depth <= LEAF_REACH && !frontier.isEmpty(); depth++)
        {
            List<BlockPos> next = new ArrayList<>();
            for (BlockPos p : frontier)
            {
                for (Direction dir : DIRECTIONS)
                {
                    BlockPos other = p.relative(dir);
                    if (!seen.add(other))
                    {
                        continue;
                    }
                    if (seen.size() > MAX_LEAF_SEARCH)
                    {
                        return LEAF_REACH + 1;
                    }
                    BlockState s = level.getBlockState(other);
                    if (s.is(BlockTags.LOGS))
                    {
                        return depth;
                    }
                    if (s.getBlock() instanceof LeavesBlock)
                    {
                        next.add(other);
                    }
                }
            }
            frontier = next;
        }
        return LEAF_REACH + 1;
    }

    private static boolean isNaturalLeaf(BlockState state)
    {
        return state.getBlock() instanceof LeavesBlock && state.hasProperty(LeavesBlock.PERSISTENT) && !state.getValue(LeavesBlock.PERSISTENT)
                && state.hasProperty(LeavesBlock.DISTANCE);
    }

    /**
     * The ground (or a rock, boulder or spire standing on it): solid, and not a tree, giant mushroom or fungus, carpet or
     * anything simply replaced.
     */
    private static boolean isGround(WorldGenLevel level, BlockPos pos, BlockState state)
    {
        if (!state.getFluidState().isEmpty())
        {
            return true; // water or lava: what's under it is a pond's or pool's bed
        }
        if (state.canBeReplaced() || state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.getBlock() instanceof HugeMushroomBlock
                || state.getBlock() instanceof CarpetBlock || state.is(BlockTags.WART_BLOCKS) || state.is(Blocks.SHROOMLIGHT))
        {
            return false;
        }
        return !state.getCollisionShape(level, pos).isEmpty();
    }
}
