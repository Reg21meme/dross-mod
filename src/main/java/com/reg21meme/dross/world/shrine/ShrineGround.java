package com.reg21meme.dross.world.shrine;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.CarpetBlock;
import net.minecraft.world.level.block.Fallable;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Gets natural ground ready for a big build (a shrine design), so the build never floats, never has terrain hanging
 * over it and never gets flooded, on any terrain: hills, forests, rivers, the sea.
 * <ol>
 *   <li><b>Ground level</b> ({@link #chooseGroundY}): the median height of the natural ground under the whole
 *       footprint. Trees, leaves, plants and ice are ignored, and the top of water or lava counts as ground. Where most
 *       of the footprint is water, it's one block above the water, so the build stands on a foundation instead of on
 *       the sea floor.</li>
 *   <li><b>Cut</b>: inside the footprint, everything above the ground level is removed, all the way up to the sky:
 *       hills, trees, water.</li>
 *   <li><b>Fill</b>: under the ground level, every column is filled down to solid natural ground, however deep:
 *       grass on top, then {@link #DIRT_DEPTH} blocks of dirt, then stone (sand over sandstone where the ground around
 *       is mostly sand, red sand over terracotta in the badlands: see {@link Surface}). Over a lake or the sea that makes a stone
 *       foundation reaching down to the bottom (through any sea ice or iceberg, which floats). Sand and gravel only
 *       count as solid ground if something solid holds them up (not a cave or an underground water pocket), and
 *       inside the footprint there are always at least {@link #MIN_FOUNDATION} solid blocks under the ground.</li>
 *   <li><b>Blend</b>: in a band {@link #BLEND_MARGIN} blocks wide around the footprint, the ground slopes from the
 *       build's level back to the natural terrain (cut down or built up), so there's a slope instead of a sheer wall
 *       or a sudden drop. Water and lava there are only ever filled in, never drained.</li>
 *   <li><b>Seal</b>: water or lava right next to any space that was cleared is turned into stone, so nothing pours in.</li>
 *   <li><b>Tidy</b>: bits of trees left hanging by the cut (loose branches) are removed, and leaves that lost their
 *       tree wither away by themselves (vanilla leaf decay).</li>
 * </ol>
 * On flat ground (a superflat world, the Dross) the natural ground is already at the build's level, so nothing is
 * filled or sloped: only what stands inside the footprint is cleared.
 * <p>
 * Every change uses {@link #FLAGS}: players see it, but no block reacts to its neighbours while the ground is being
 * worked on (water doesn't start flowing, sand doesn't fall, plants don't pop off).
 */
final class ShrineGround
{
    /** How far (in blocks) outside the footprint the ground slopes back from the build's level to the natural terrain. */
    static final int BLEND_MARGIN = 16;
    /** Under the top grass block, filled ground is dirt this many blocks deep; deeper down it's stone. */
    private static final int DIRT_DEPTH = 3;
    /**
     * Inside the footprint there are always at least this many solid blocks under the ground layer. A thin natural
     * crust over a cave or an underground water pocket isn't enough: the build's ground layer can put gravel or sand
     * on it. Any gap this close under the ground is filled, down to solid ground.
     */
    private static final int MIN_FOUNDATION = 4;
    /**
     * Logs left hanging by the cut (a branch whose trunk was removed) are removed, unless the piece is bigger than
     * this: then it's assumed to be part of a tree that still stands, and kept.
     */
    private static final int MAX_LOOSE_LOGS = 96;
    /** Clients see every change, but nothing pokes its neighbours (no flowing water, falling sand or popping plants). */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    /** What water or lava next to cleared space turns into. */
    private static final BlockState SEAL = Blocks.STONE.defaultBlockState();

    /**
     * The ground level picked for a footprint, and what the natural ground under it looked like (for the log).
     *
     * @param groundY      the Y of the ground block the build stands on
     * @param lowest       the lowest natural ground under the footprint
     * @param median       the median natural ground under the footprint
     * @param highest      the highest natural ground under the footprint
     * @param liquidShare  how much of the footprint was water or lava, 0 to 1
     */
    record GroundChoice(int groundY, int lowest, int median, int highest, double liquidShare)
    {
        String summary()
        {
            return String.format(Locale.ROOT, "ground level %d (natural ground under it: lowest %d, median %d, highest %d, %d%% water or lava)",
                    groundY, lowest, median, highest, Math.round(liquidShare * 100));
        }
    }

    /**
     * What preparing the ground did (for the log and for checks).
     *
     * @param cutColumns    columns whose natural ground was above the target and was cut down
     * @param highestCut    the most a column was cut down (blocks of natural ground removed above the target)
     * @param fillColumns   columns that were built up from below
     * @param deepestFill   the deepest fill in one column (blocks placed, the top grass included)
     * @param removed       blocks removed (terrain, trees, water above the ground)
     * @param placed        blocks placed for the foundation and the slopes
     * @param sealed        water or lava blocks next to cleared space turned into stone
     * @param looseLogs     hanging logs removed
     * @param leavesChecked leaves next to removed trees, told to check whether they still have a tree (decay)
     * @param surface       what new ground was topped with ("grass", "sand" or "red sand")
     */
    record Report(int cutColumns, int highestCut, int fillColumns, int deepestFill, int removed, int placed, int sealed,
                  int looseLogs, int leavesChecked, String surface)
    {
        String summary()
        {
            return "cut " + cutColumns + " columns (up to " + highestCut + " blocks), filled " + fillColumns
                    + " columns (up to " + deepestFill + " blocks deep), removed " + removed + " blocks, placed " + placed
                    + ", sealed " + sealed + " water/lava blocks, removed " + looseLogs + " hanging logs, new ground topped with "
                    + surface;
        }
    }

    // ---------------------------------------------------------------- the ground level

    /**
     * The median height of the natural ground under the footprint (see {@link #surfaceY}); if most of the footprint is
     * water or lava, one block above its surface instead. Kept between {@code minAllowed} and {@code maxAllowed} (so
     * the build fits in the world). ({@code Level.getSeaLevel()} isn't used: in 1.20.1 it's 63 in every dimension,
     * the flat Dross included.)
     */
    static GroundChoice chooseGroundY(ServerLevel level, int minX, int minZ, int maxX, int maxZ, int minAllowed, int maxAllowed)
    {
        int count = (maxX - minX + 1) * (maxZ - minZ + 1);
        int[] heights = new int[count];
        int liquid = 0;
        int i = 0;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                int y = surfaceY(level, x, z);
                heights[i++] = y;
                if (!level.getBlockState(pos.set(x, y, z)).getFluidState().isEmpty())
                {
                    liquid++;
                }
            }
        }
        Arrays.sort(heights);
        int median = heights[count / 2];
        // Mostly water or lava: the median is its surface. Stand one block above it, on a foundation.
        int ground = liquid * 2 >= count ? median + 1 : median;
        ground = Mth.clamp(ground, minAllowed, maxAllowed);
        return new GroundChoice(ground, heights[0], median, heights[count - 1], liquid / (double) count);
    }

    /**
     * The Y of the natural ground's top block in this column: solid ground, or the top of water or lava. Trees,
     * leaves, plants, snow layers and ice are looked through.
     */
    static int surfaceY(ServerLevel level, int x, int z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, 0, z);
        int top = topY(level, x, z);
        for (int y = top; y > level.getMinBuildHeight(); y--)
        {
            BlockState state = level.getBlockState(pos.setY(y));
            if (!state.getFluidState().isEmpty())
            {
                return y; // the top of water or lava (also a block full of water: seagrass, waterlogged roots...)
            }
            if (isGround(level, pos, state))
            {
                return y;
            }
        }
        return level.getMinBuildHeight();
    }

    /**
     * The Y of the highest block that isn't air in this column. Its chunk is loaded (and generated if needed) first:
     * {@code Level.getHeight} would just answer "the bottom of the world" for a chunk that isn't loaded yet.
     */
    static int topY(ServerLevel level, int x, int z)
    {
        return level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z))
                .getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
    }

    /**
     * Natural ground you could build on: solid, dry, not a plant or a tree, not something that's simply replaced
     * (snow layers, grass), and not ice of any kind (sea ice and icebergs float on water, with water pockets
     * inside them; ice spikes stick up like trees).
     */
    static boolean isGround(ServerLevel level, BlockPos pos, BlockState state)
    {
        return !state.isAir()
                && state.getFluidState().isEmpty()
                && !state.canBeReplaced()
                && !isTreeOrPlant(state)
                && !state.is(BlockTags.ICE)
                && !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * Trees (logs, leaves, bee nests, huge mushrooms, mangrove roots) and plants, including the ones with a small
     * collision box (lily pads, sea pickles, cactus, bamboo, dripleaves, carpets of moss...).
     */
    static boolean isTreeOrPlant(BlockState state)
    {
        return state.getBlock() instanceof BushBlock // flowers, grass, saplings, crops, lily pads, sea pickles...
                || state.is(BlockTags.LOGS)
                || state.is(BlockTags.LEAVES)
                || state.is(BlockTags.BEEHIVES)
                || state.is(BlockTags.FLOWERS)
                || state.is(Blocks.MANGROVE_ROOTS)
                || state.is(Blocks.BIG_DRIPLEAF)
                || state.is(Blocks.BIG_DRIPLEAF_STEM)
                || state.is(Blocks.COCOA)
                || state.is(Blocks.TURTLE_EGG)
                || state.is(Blocks.POINTED_DRIPSTONE)
                || state.is(Blocks.MUSHROOM_STEM)
                || state.is(Blocks.RED_MUSHROOM_BLOCK)
                || state.is(Blocks.BROWN_MUSHROOM_BLOCK)
                || state.is(Blocks.AZALEA)
                || state.is(Blocks.BAMBOO)
                || state.is(Blocks.CACTUS)
                || state.is(Blocks.PUMPKIN)
                || state.is(Blocks.MELON)
                || state.getBlock() instanceof CarpetBlock;
    }

    // ---------------------------------------------------------------- preparing the ground

    /**
     * Prepares the ground for a build: cuts the footprint down to {@code groundY} (and clears it up to at least
     * {@code clearTop}), fills under it down to solid ground, slopes the band around it back to the natural terrain,
     * seals water and lava next to the cleared space, and tidies up hanging tree parts. See the class comment.
     *
     * @param footprint the build's box (only its X and Z are used)
     * @param groundY   the Y of the ground block the build stands on
     * @param clearTop  inside the footprint, everything up to this Y is cleared (more if the terrain is higher)
     */
    static Report prepare(ServerLevel level, BoundingBox footprint, int groundY, int clearTop)
    {
        Work work = new Work(level, footprint, Surface.around(level, footprint));
        for (int i = 0; i < work.sizeX; i++)
        {
            for (int k = 0; k < work.sizeZ; k++)
            {
                work.column(i, k, groundY, clearTop);
            }
        }
        work.seal();
        work.removeLooseLogs();
        return new Report(work.cutColumns, work.highestCut, work.fillColumns, work.deepestFill, work.removed, work.placed,
                work.sealed, work.looseLogs, work.leavesChecked, work.newGround.name());
    }

    /** How far a column is outside the footprint (0 inside it), measured flat from the nearest footprint column. */
    static double distanceOutside(BoundingBox footprint, int x, int z)
    {
        int dx = Math.max(0, Math.max(footprint.minX() - x, x - footprint.maxX()));
        int dz = Math.max(0, Math.max(footprint.minZ() - z, z - footprint.maxZ()));
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** 0 at 0, 1 at 1, flat at both ends: the slope starts gently by the build and meets the natural ground gently. */
    private static double smoothstep(double t)
    {
        double c = Mth.clamp(t, 0.0D, 1.0D);
        return c * c * (3.0D - 2.0D * c);
    }

    /**
     * What new ground is made of, to match the natural ground around the build: grass over dirt in most places, sand
     * over sandstone where the ground is mostly sand (deserts, beaches), red sand over terracotta in the badlands.
     * Deeper fill is always stone.
     *
     * @param top   the block on top of filled or freshly cut ground
     * @param under the next {@link #DIRT_DEPTH} blocks under it
     * @param name  for the log
     */
    record Surface(BlockState top, BlockState under, String name)
    {
        static final Surface GRASS = new Surface(Blocks.GRASS_BLOCK.defaultBlockState(), Blocks.DIRT.defaultBlockState(), "grass");
        static final Surface SAND = new Surface(Blocks.SAND.defaultBlockState(), Blocks.SANDSTONE.defaultBlockState(), "sand");
        static final Surface RED_SAND = new Surface(Blocks.RED_SAND.defaultBlockState(), Blocks.TERRACOTTA.defaultBlockState(), "red sand");

        /**
         * The surface that matches the natural ground on the outer edge of the blend band (which stays as it was):
         * sand or red sand if at least half of the dry ground there is, grass otherwise.
         */
        static Surface around(ServerLevel level, BoundingBox footprint)
        {
            int sand = 0;
            int redSand = 0;
            int dry = 0;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int x = footprint.minX() - BLEND_MARGIN; x <= footprint.maxX() + BLEND_MARGIN; x++)
            {
                for (int z = footprint.minZ() - BLEND_MARGIN; z <= footprint.maxZ() + BLEND_MARGIN; z++)
                {
                    double distance = distanceOutside(footprint, x, z);
                    if (distance < BLEND_MARGIN - 2 || distance > BLEND_MARGIN)
                    {
                        continue;
                    }
                    int y = surfaceY(level, x, z);
                    BlockState top = level.getBlockState(pos.set(x, y, z));
                    if (!top.getFluidState().isEmpty())
                    {
                        continue;
                    }
                    dry++;
                    if (top.is(Blocks.RED_SAND) || top.is(BlockTags.TERRACOTTA))
                    {
                        redSand++;
                    }
                    else if (top.is(BlockTags.SAND) || top.is(Blocks.SANDSTONE))
                    {
                        sand++;
                    }
                }
            }
            if (dry > 0 && redSand * 2 >= dry)
            {
                return RED_SAND;
            }
            if (dry > 0 && sand * 2 >= dry)
            {
                return SAND;
            }
            return GRASS;
        }

        /** Ground that was underground until the cut, which gets {@link #top} on it like the natural surface. */
        static boolean wasUnderground(BlockState state)
        {
            return state.is(BlockTags.DIRT)
                    || state.is(BlockTags.BASE_STONE_OVERWORLD)
                    || state.is(BlockTags.SAND)
                    || state.is(BlockTags.TERRACOTTA)
                    || state.is(Blocks.SANDSTONE)
                    || state.is(Blocks.RED_SANDSTONE);
        }
    }

    /** One run of {@link #prepare}: the area worked on (the footprint plus the blend band) and what's been done so far. */
    private static final class Work
    {
        final ServerLevel level;
        final BoundingBox footprint;
        final int minX;
        final int minZ;
        final int sizeX;
        final int sizeZ;
        /** Per cut column: the air space above its new ground, lowest and highest Y (low > high: not cut). */
        final int[] clearedLow;
        final int[] clearedHigh;
        final List<BlockPos> removedLogs = new ArrayList<>();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        /** A second cursor, for looking around without moving {@link #pos}. */
        final BlockPos.MutableBlockPos probe = new BlockPos.MutableBlockPos();
        /** What new ground is made of. */
        final Surface newGround;
        int cutColumns;
        int highestCut;
        int fillColumns;
        int deepestFill;
        int removed;
        int placed;
        int sealed;
        int looseLogs;
        int leavesChecked;

        Work(ServerLevel level, BoundingBox footprint, Surface surface)
        {
            this.level = level;
            this.footprint = footprint;
            this.newGround = surface;
            this.minX = footprint.minX() - BLEND_MARGIN;
            this.minZ = footprint.minZ() - BLEND_MARGIN;
            this.sizeX = footprint.getXSpan() + 2 * BLEND_MARGIN;
            this.sizeZ = footprint.getZSpan() + 2 * BLEND_MARGIN;
            this.clearedLow = new int[sizeX * sizeZ];
            this.clearedHigh = new int[sizeX * sizeZ];
            Arrays.fill(clearedLow, Integer.MAX_VALUE);
            Arrays.fill(clearedHigh, Integer.MIN_VALUE);
        }

        /** Works out the column's new ground height and cuts or fills it to that. */
        void column(int i, int k, int groundY, int clearTop)
        {
            int x = minX + i;
            int z = minZ + k;
            double distance = distanceOutside(footprint, x, z);
            if (distance > BLEND_MARGIN)
            {
                return; // the corners of the band: the band is rounded
            }
            boolean inside = distance == 0.0D;
            int surface = surfaceY(level, x, z);
            boolean liquid = !level.getBlockState(pos.set(x, surface, z)).getFluidState().isEmpty();
            int target;
            if (inside)
            {
                target = groundY;
            }
            else
            {
                // The band: from the build's level by the footprint to the natural height at the band's outer edge.
                target = (int) Math.round(groundY + (surface - groundY) * smoothstep(distance / BLEND_MARGIN));
                if (target == surface || (liquid && target < surface))
                {
                    return; // already right, or water/lava that would have to be drained (it's sealed off instead)
                }
            }

            int index = i * sizeZ + k;
            boolean cut = inside || target < surface;
            if (cut)
            {
                // Everything above the new ground goes, up to the sky (the terrain, trees, water...).
                int top = Math.max(topY(level, x, z), inside ? clearTop : Integer.MIN_VALUE);
                for (int y = target + 1; y <= top; y++)
                {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (state.isAir())
                    {
                        continue;
                    }
                    if (state.is(BlockTags.LOGS))
                    {
                        removedLogs.add(pos.immutable());
                    }
                    level.setBlock(pos, AIR, FLAGS);
                    removed++;
                }
                if (top > target)
                {
                    // All of it is air now: the sealing pass keeps water and lava out of this whole space.
                    clearedLow[index] = target + 1;
                    clearedHigh[index] = top;
                }
                if (surface > target)
                {
                    cutColumns++;
                    highestCut = Math.max(highestCut, surface - target);
                }
            }

            BlockState atTarget = level.getBlockState(pos.set(x, target, z));
            if (isFirmGround(x, target, z, atTarget))
            {
                if (surface > target && atTarget != newGround.top() && Surface.wasUnderground(atTarget))
                {
                    // Ground that was underground until the cut: give it a top like the natural surface's.
                    level.setBlock(pos, newGround.top(), FLAGS);
                    placed++;
                }
                else if (atTarget.hasProperty(BlockStateProperties.SNOWY) && atTarget.getValue(BlockStateProperties.SNOWY))
                {
                    level.setBlock(pos, atTarget.setValue(BlockStateProperties.SNOWY, false), FLAGS); // its snow was cleared
                }
            }
            else
            {
                // Air, water, lava, plants, a tree trunk or loose sand at the new ground height: build it up from
                // solid ground.
                level.setBlock(pos, newGround.top(), FLAGS);
                int depth = 1;
                for (int y = target - 1; y > level.getMinBuildHeight(); y--)
                {
                    BlockState state = level.getBlockState(pos.set(x, y, z));
                    if (isFirmGround(x, y, z, state))
                    {
                        break;
                    }
                    level.setBlock(pos, target - y <= DIRT_DEPTH ? newGround.under() : STONE, FLAGS);
                    depth++;
                }
                placed += depth;
                fillColumns++;
                deepestFill = Math.max(deepestFill, depth);
            }

            if (inside)
            {
                // At least MIN_FOUNDATION solid blocks under the ground layer: a gap there (a cave or a water pocket
                // under a thin crust) is filled down to solid ground.
                for (int y = target - 1; y >= target - MIN_FOUNDATION && y > level.getMinBuildHeight(); y--)
                {
                    if (!isFirmGround(x, y, z, level.getBlockState(pos.set(x, y, z))))
                    {
                        fillDown(x, y, z, target);
                        break;
                    }
                }
            }

            if (!cut)
            {
                // A raised column outside the footprint: whatever stood on the old ground is buried now, apart from
                // what reaches above the new ground (a tree trunk stays; the loose top of a tall plant goes).
                BlockState above = level.getBlockState(pos.set(x, target + 1, z));
                if (!above.isAir() && above.getFluidState().isEmpty() && !above.canSurvive(level, pos))
                {
                    level.setBlock(pos, AIR, FLAGS);
                    removed++;
                }
            }
        }

        /** Fills the column from {@code fromY} down to solid ground (dirt near {@code groundY}, stone deeper down). */
        private void fillDown(int x, int fromY, int z, int groundY)
        {
            int depth = 0;
            for (int y = fromY; y > level.getMinBuildHeight(); y--)
            {
                BlockState state = level.getBlockState(pos.set(x, y, z));
                if (isFirmGround(x, y, z, state))
                {
                    break;
                }
                level.setBlock(pos, groundY - y <= DIRT_DEPTH ? newGround.under() : STONE, FLAGS);
                depth++;
            }
            placed += depth;
            deepestFill = Math.max(deepestFill, groundY - fromY + depth);
        }

        /**
         * Ground that will hold the build up: {@link #isGround}, and for sand, gravel and other blocks that fall, only
         * if the first block under them that doesn't fall is ground too (not a cave or an underground water pocket,
         * which they would drop into the first time a neighbour changes).
         */
        private boolean isFirmGround(int x, int y, int z, BlockState state)
        {
            if (!isGround(level, probe.set(x, y, z), state))
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
                    return isGround(level, probe, under);
                }
            }
            return false;
        }

        /**
         * Water or lava right next to space that was cleared would pour in: turn it into stone. Leaves next to it
         * (their tree may have been removed) are told to check whether they still have a tree.
         */
        void seal()
        {
            for (int i = 0; i < sizeX; i++)
            {
                for (int k = 0; k < sizeZ; k++)
                {
                    int index = i * sizeZ + k;
                    if (clearedLow[index] > clearedHigh[index])
                    {
                        continue;
                    }
                    for (Direction dir : Direction.Plane.HORIZONTAL)
                    {
                        int ni = i + dir.getStepX();
                        int nk = k + dir.getStepZ();
                        int neighbour = ni >= 0 && ni < sizeX && nk >= 0 && nk < sizeZ ? ni * sizeZ + nk : -1;
                        for (int y = clearedLow[index]; y <= clearedHigh[index]; y++)
                        {
                            if (neighbour >= 0 && y >= clearedLow[neighbour] && y <= clearedHigh[neighbour])
                            {
                                continue; // cleared there too: it's air
                            }
                            pos.set(minX + ni, y, minZ + nk);
                            BlockState state = level.getBlockState(pos);
                            if (!state.getFluidState().isEmpty())
                            {
                                level.setBlock(pos, SEAL, FLAGS);
                                sealed++;
                            }
                            else if (state.is(BlockTags.LEAVES))
                            {
                                checkLeaves(pos, state);
                            }
                        }
                    }
                }
            }
        }

        /**
         * Removes logs left hanging by the cut: every piece of logs (touching each other, corners included) next to a
         * removed log that no longer stands on anything. A piece that still stands on the ground somewhere (a tree
         * outside the cut whose branch reached in) is kept.
         */
        void removeLooseLogs()
        {
            Set<BlockPos> seen = new HashSet<>();
            for (BlockPos cutLog : removedLogs)
            {
                for (BlockPos start : BlockPos.betweenClosed(cutLog.offset(-1, -1, -1), cutLog.offset(1, 1, 1)))
                {
                    if (seen.contains(start) || !level.getBlockState(start).is(BlockTags.LOGS))
                    {
                        continue;
                    }
                    List<BlockPos> piece = new ArrayList<>();
                    if (collectPiece(start.immutable(), seen, piece))
                    {
                        continue;
                    }
                    for (BlockPos log : piece)
                    {
                        level.setBlock(log, AIR, FLAGS);
                    }
                    looseLogs += piece.size();
                    removed += piece.size();
                    for (BlockPos log : piece)
                    {
                        for (Direction dir : Direction.values())
                        {
                            BlockPos next = log.relative(dir);
                            BlockState state = level.getBlockState(next);
                            if (state.is(BlockTags.LEAVES))
                            {
                                checkLeaves(next, state);
                            }
                        }
                    }
                }
            }
        }

        /**
         * Collects the piece of logs that {@code start} belongs to into {@code piece}.
         *
         * @return true if the piece stands on something (or is too big to be a loose branch), so it stays
         */
        private boolean collectPiece(BlockPos start, Set<BlockPos> seen, List<BlockPos> piece)
        {
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start);
            boolean standing = false;
            while (!queue.isEmpty())
            {
                BlockPos log = queue.poll();
                piece.add(log);
                if (piece.size() > MAX_LOOSE_LOGS)
                {
                    return true;
                }
                BlockPos below = log.below();
                if (isGround(level, below, level.getBlockState(below)))
                {
                    standing = true;
                }
                for (BlockPos next : BlockPos.betweenClosed(log.offset(-1, -1, -1), log.offset(1, 1, 1)))
                {
                    if (!seen.contains(next) && level.getBlockState(next).is(BlockTags.LOGS))
                    {
                        BlockPos found = next.immutable();
                        seen.add(found);
                        queue.add(found);
                    }
                }
            }
            return standing;
        }

        /** Leaves recheck their distance to a log on their next tick; with no tree left they decay (as in vanilla). */
        private void checkLeaves(BlockPos at, BlockState state)
        {
            if (state.hasProperty(BlockStateProperties.PERSISTENT) && !state.getValue(BlockStateProperties.PERSISTENT))
            {
                level.scheduleTick(at.immutable(), state.getBlock(), 1);
                leavesChecked++;
            }
        }
    }

    private ShrineGround() {}
}
