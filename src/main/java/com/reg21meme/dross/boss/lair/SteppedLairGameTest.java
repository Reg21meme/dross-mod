package com.reg21meme.dross.boss.lair;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless test (GameTest) that building a Colossus lair a piece at a time gives <b>exactly</b> the same blocks as
 * building it all at once. The lairs are built a piece at a time in the game ({@link LairBuilder.Build#step}, at most
 * about {@link LairTuning#STEP_MILLIS} ms of work a tick, {@link LairPlacer}), and must come out as if built in one go.
 * Run it with {@code .\gradlew.bat runGameTestServer --console=plain}; it only exists in the development environment.
 * <ul>
 *   <li>{@link #steppedLairMatchesAtOnce}: a made-up patch of Cinder-Wastes-like land high in the Dross's sky, far from
 *       the hub (ash, basalt and blackstone, rolling, with a mesa or two; ten trees and eight small natural lava pools on
 *       it), always the same (fixed seeds, whatever the world's seed). A volcano is planned on it from those seeds. A trial
 *       build (put back afterwards) finds where a rivulet's trench opens beside rock that stays: both builds below get a
 *       natural lava block there just before their lava's work, the old leak (a natural pool on the flank could pour out
 *       into the space the build opened over its rivulet). Then the volcano is built all at once (every block it changes
 *       is remembered), the land is put back exactly as it was, and it's built again, this time a piece at a time with no
 *       time to spare (one piece per step, so every place a step can stop at is used; the lava's work still in one step).
 *       Every block must match, and so must the counts; no lava (the build's, the natural pools' or the one beside the
 *       trench) may be able to flow anywhere, and the opening beside the trench must have been sealed.</li>
 * </ul>
 * The building and comparing all happen in one server tick (so nothing else changes the blocks meanwhile); the land
 * and the volcano are removed afterwards. It uses world-builder's empty template ({@link PortalSiteGameTest#EMPTY}) and
 * its own batch.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class SteppedLairGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "dross_stepped_lair";
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_stepped_lair_test", Comparator.comparingLong(ChunkPos::toLong));

    /** Where the volcano's middle goes (X, Z in the Dross), far from the hub and the other tests; then spares. */
    private static final int[][] SPOTS = {{-9000, 9000}, {9000, -9000}, {-9000, -9000}};
    /** The made-up land reaches this far from the middle (the volcano and its way in reach about 100). */
    private static final int HALF = 112;
    /** Its land is about this high (higher if the real land under it is), floating at least this far above the real land... */
    private static final int PREFERRED_BASE = 200;
    private static final int AIR_GAP = 8;
    /** ...solid this many blocks under its lowest ground... */
    private static final int SLAB_DEPTH = 6;
    /** ...and its rim may reach this far above the base (a rim is at most 64 over the floor), with room left over it. */
    private static final int ROOM_ABOVE = 90;
    /** The land rolls up and down by about this much, and its mesas stand this high. */
    private static final double ROLL = 5.0D;
    private static final double BUMPS = 2.0D;
    private static final int MESA = 6;
    /** The seeds of the land and of the volcano (its shape and the builder's randomness). */
    private static final long LAND_SEED = 0x4C616E6453L;
    private static final long PLAN_SEED = 20261018L;
    /** Ten trees: their distance from the middle and their direction (degrees). */
    private static final int[][] TREES = {{18, 10}, {24, 50}, {29, 95}, {33, 140}, {37, 185}, {41, 230}, {45, 275}, {49, 320},
            {56, 30}, {60, 200}};
    /** Eight 2 x 2 natural lava pools, sunk into the land: their distance from the middle and their direction (degrees). */
    private static final int[][] POOLS = {{26, 30}, {30, 75}, {34, 120}, {38, 165}, {42, 210}, {46, 255}, {50, 300}, {54, 345}};
    /** Chunk loading gets at most this long (real time). */
    private static final long LOAD_TIMEOUT_MILLIS = 3 * 60 * 1000L;
    /** The stepped build must take at least this many steps, or it wasn't really cut into pieces. */
    private static final int MIN_STEPS = 300;
    /** A failing check names at most this many blocks. */
    private static final int MAX_SHOWN = 6;

    private SteppedLairGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = 2_000_000)
    public static void steppedLairMatchesAtOnce(GameTestHelper helper)
    {
        Run run = new Run(helper);
        helper.startSequence()
                .thenExecute(run::load)
                .thenWaitUntil(run::waitForChunks)
                .thenExecute(run::compare)
                .thenSucceed();
    }

    /** The test's state: where, its loaded chunks, the made-up land. */
    private static final class Run
    {
        final GameTestHelper helper;
        ServerLevel dross;
        final List<ChunkPos> held = new ArrayList<>();
        int[] spot;
        int spotIndex;
        long deadline;
        /** The real land's highest block under the spot, and the made-up land's base (once its chunks are loaded). */
        int realLand;
        int base;
        /** Why the land couldn't be made (the chunks didn't load, no room in the sky), or null. */
        String failure;

        Run(GameTestHelper helper)
        {
            this.helper = helper;
        }

        /** Asks for the first spot's chunks (made in the background). */
        void load()
        {
            dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
            if (dross == null)
            {
                throw new GameTestAssertException("the Dross dimension isn't loaded");
            }
            hold(SPOTS[spotIndex]);
        }

        void hold(int[] at)
        {
            release();
            spot = at;
            deadline = Util.getMillis() + LOAD_TIMEOUT_MILLIS;
            int r = HALF + 16;
            for (int x = SectionPos.blockToSectionCoord(at[0] - r); x <= SectionPos.blockToSectionCoord(at[0] + r); x++)
            {
                for (int z = SectionPos.blockToSectionCoord(at[1] - r); z <= SectionPos.blockToSectionCoord(at[1] + r); z++)
                {
                    ChunkPos chunk = new ChunkPos(x, z);
                    dross.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
                    held.add(chunk);
                }
            }
        }

        void release()
        {
            for (ChunkPos chunk : held)
            {
                dross.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
            }
            held.clear();
        }

        /** Waits for the spot's chunks, then picks the land's height over the real land (or moves on to the next spot). */
        void waitForChunks()
        {
            for (ChunkPos chunk : held)
            {
                if (dross.getChunkSource().getChunkNow(chunk.x, chunk.z) == null)
                {
                    if (Util.getMillis() > deadline)
                    {
                        failure = "the test land's chunks at " + spot[0] + ", " + spot[1] + " didn't load within " + LOAD_TIMEOUT_MILLIS / 1000 + " s";
                        return; // (the next step fails)
                    }
                    throw new GameTestAssertException("waiting for the test land's chunks");
                }
            }
            realLand = dross.getMinBuildHeight();
            for (int x = spot[0] - HALF - 4; x <= spot[0] + HALF + 4; x++)
            {
                for (int z = spot[1] - HALF - 4; z <= spot[1] + HALF + 4; z++)
                {
                    realLand = Math.max(realLand, dross.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                }
            }
            base = Math.max(PREFERRED_BASE, realLand + AIR_GAP + SLAB_DEPTH + (int) Math.ceil(ROLL + BUMPS) + 2);
            if (base + ROOM_ABOVE > dross.getMaxBuildHeight())
            {
                LOGGER.info("[stepped lair test] The real land at {}, {} reaches y {}: no room above it.", spot[0], spot[1], realLand);
                if (++spotIndex >= SPOTS.length)
                {
                    failure = "no room in the Dross's sky for the test land";
                    return; // (the next step fails)
                }
                hold(SPOTS[spotIndex]);
                throw new GameTestAssertException("waiting for the next spot's chunks");
            }
        }

        /** The whole check, in this one tick. */
        void compare()
        {
            if (failure != null)
            {
                release();
                throw new GameTestAssertException(failure);
            }
            int cx = spot[0];
            int cz = spot[1];
            Land land = new Land(cx, cz, base, realLand);
            List<String> problems = new ArrayList<>();
            Map<Long, BlockState> journalB = null;
            Map<Long, BlockState> journalA = null;
            try
            {
                land.build(dross);
                LairBuilder.Site site = LairBuilder.plan(cx, cz, land::top, PLAN_SEED, LairBuilder.lowestFloor(dross), LairBuilder.highestRim(dross));

                // 0. Where a rivulet's trench will open beside rock that stays (found by a trial build, then put back): just
                //    before the lava's work, both builds below get a natural lava block there. The old leak: a natural
                //    pool on the flank could pour out into the space the build opened over its rivulet.
                BlockPos natural = leakSpot(site);
                if (natural == null)
                {
                    throw new GameTestAssertException("the trial build's rivulets cut no trench beside rock that stays (the leak check "
                            + "needs another test land)");
                }
                land.extraLava.add(natural);

                // 1. All at once (everything up to the lava, the natural lava put down, then the rest), remembering every
                //    block it changes.
                LairBuilder.Build once = LairBuilder.start(dross, site);
                journalA = once.recordChanges();
                once.step(LairBuilder.Budget.unlimited());
                expect(problems, once.atLava(), "building all at once didn't stop just before the lava's work");
                dross.setBlock(natural, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
                LairBuilder.Result atOnce = once.finish();
                Map<Long, BlockState> builtOnce = new HashMap<>(journalA.size() * 2);
                BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
                for (long at : journalA.keySet())
                {
                    builtOnce.put(at, dross.getBlockState(pos.set(at)));
                }
                checkLava(problems, journalA.keySet(), land);

                // 2. Put the land back exactly as it was.
                putBack(journalA);
                int unrestored = 0;
                for (Map.Entry<Long, BlockState> e : journalA.entrySet())
                {
                    unrestored += dross.getBlockState(pos.set(e.getKey())) == e.getValue() ? 0 : 1;
                }
                expect(problems, unrestored == 0, unrestored + " blocks weren't put back as they were (the test itself is broken)");
                Map<Long, BlockState> undone = journalA;
                journalA = null;

                // 3. A piece at a time, one piece per step.
                LairBuilder.Build stepped = LairBuilder.start(dross, site);
                journalB = stepped.recordChanges();
                int steps = 0;
                while (!stepped.atLava())
                {
                    stepped.step(LairBuilder.Budget.millis(0L));
                    steps++;
                }
                dross.setBlock(natural, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
                boolean finished = false;
                while (!finished)
                {
                    finished = stepped.step(LairBuilder.Budget.millis(0L));
                    steps++;
                }
                LairBuilder.Result inPieces = stepped.result();

                // 4. Every block either build changed, and the counts.
                Set<Long> every = new LinkedHashSet<>(undone.keySet());
                every.addAll(journalB.keySet());
                int different = 0;
                List<String> shown = new ArrayList<>();
                for (long at : every)
                {
                    BlockState expected = builtOnce.containsKey(at) ? builtOnce.get(at) : journalB.get(at);
                    BlockState found = dross.getBlockState(pos.set(at));
                    if (found != expected)
                    {
                        different++;
                        if (shown.size() < MAX_SHOWN)
                        {
                            shown.add(BlockPos.of(at).toShortString() + ": all at once " + expected + ", a piece at a time " + found);
                        }
                    }
                }
                expect(problems, different == 0, different + " blocks differ between building all at once and a piece at a time, for "
                        + "example " + String.join("; ", shown));
                String a = counts(atOnce);
                String b = counts(inPieces);
                expect(problems, a.equals(b), "the counts differ: all at once " + a + ", a piece at a time " + b);
                expect(problems, steps >= MIN_STEPS, "the stepped build took only " + steps + " steps (at least " + MIN_STEPS
                        + " expected: is it really cut into pieces?)");
                expect(problems, atOnce.trees() >= 5, "only " + atOnce.trees() + " trees were removed (at least 5 of the 10 stand in the way)");
                expect(problems, atOnce.rivulets() >= 2 && atOnce.outsideLava() > 0, "only " + atOnce.rivulets() + " rivulet(s) with "
                        + atOnce.outsideLava() + " lava block(s) outside");
                expect(problems, atOnce.openingsSealed() > 0 && dross.getFluidState(natural).is(Fluids.LAVA), "the natural lava beside "
                        + "the rivulet's trench at " + natural.toShortString() + " wasn't kept walled in by sealing the opening (openings "
                        + "sealed " + atOnce.openingsSealed() + ", still lava " + dross.getFluidState(natural).is(Fluids.LAVA) + ")");
                if (!problems.isEmpty())
                {
                    throw new GameTestAssertException(problems.size() + " problem(s): " + String.join("; ", problems));
                }
                LOGGER.info("[stepped lair test] A volcano on made-up land at {}, {} (base y {}; {}) built a piece at a time ({} steps; each kind "
                                + "of work, ms (steps): {}) gave exactly the same {} blocks as all at once (built in {} ms: {}); {}.", cx, cz, base,
                        site.plan().describe(), steps, stepped.describeSteps(), every.size(), atOnce.millis(), once.describeSteps(), a);
            }
            finally
            {
                if (journalA != null)
                {
                    putBack(journalA);
                }
                if (journalB != null)
                {
                    putBack(journalB);
                }
                land.clear(dross);
                release();
            }
        }

        /**
         * Where to put the leak check's natural lava: a trial build is run up to the lava's work, then its changes from
         * there on are remembered separately. It's searched for a block the lava's work empties right over its own lava (a
         * rivulet's or pool's trench, cut into the cone's rock) with a solid block beside it that the lava's work leaves
         * alone and that is walled in below and on its three other sides. Lava put there just before the lava's work is a
         * natural pool's edge the trench opens: the build must seal the opening. Both journals are put back (the land is
         * as it was). Null if there's no such spot.
         */
        @javax.annotation.Nullable
        BlockPos leakSpot(LairBuilder.Site site)
        {
            LairBuilder.Build trial = LairBuilder.start(dross, site);
            Map<Long, BlockState> before = trial.recordChanges();
            Map<Long, BlockState> lavaWork = null;
            try
            {
                trial.step(LairBuilder.Budget.unlimited());
                if (!trial.atLava())
                {
                    return null;
                }
                lavaWork = trial.recordChanges(); // from here on: each block as it was when the lava's work began
                trial.finish();
                for (Map.Entry<Long, BlockState> e : lavaWork.entrySet())
                {
                    BlockPos opened = BlockPos.of(e.getKey());
                    if (!e.getValue().isSolid() || !dross.getBlockState(opened).isAir() || !dross.getFluidState(opened.below()).is(Fluids.LAVA))
                    {
                        continue;
                    }
                    for (Direction d : Direction.Plane.HORIZONTAL)
                    {
                        BlockPos beside = opened.relative(d);
                        if (!lavaWork.containsKey(beside.asLong()) && dross.getBlockState(beside).isSolid()
                                && solidWhenLavaBegan(beside.below(), lavaWork) && walledExcept(beside, d.getOpposite(), lavaWork))
                        {
                            return beside;
                        }
                    }
                }
                return null;
            }
            finally
            {
                if (lavaWork != null)
                {
                    putBack(lavaWork);
                }
                putBack(before);
            }
        }

        /** True if the block was solid when the lava's work began. */
        boolean solidWhenLavaBegan(BlockPos at, Map<Long, BlockState> lavaWork)
        {
            BlockState was = lavaWork.get(at.asLong());
            return (was != null ? was : dross.getBlockState(at)).isSolid();
        }

        /** True if every side of {@code at} but {@code open} was solid when the lava's work began. */
        boolean walledExcept(BlockPos at, Direction open, Map<Long, BlockState> lavaWork)
        {
            for (Direction d : Direction.Plane.HORIZONTAL)
            {
                if (d != open && !solidWhenLavaBegan(at.relative(d), lavaWork))
                {
                    return false;
                }
            }
            return true;
        }

        /** Every lava block the build changed or the land's pools hold: walled in by Minecraft's flow rule. */
        void checkLava(List<String> problems, Set<Long> changed, Land land)
        {
            Set<BlockPos> lava = new LinkedHashSet<>();
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (long at : changed)
            {
                if (dross.getFluidState(pos.set(at)).is(Fluids.LAVA))
                {
                    lava.add(BlockPos.of(at));
                }
            }
            lava.addAll(land.poolBlocks());
            lava.addAll(land.extraLava);
            int loose = 0;
            String example = null;
            for (BlockPos at : lava)
            {
                if (!dross.getFluidState(at).is(Fluids.LAVA))
                {
                    continue; // a pool the volcano covered
                }
                for (Direction d : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST})
                {
                    BlockPos next = at.relative(d);
                    BlockState state = dross.getBlockState(next);
                    if (!state.getFluidState().is(Fluids.LAVA) && LairBuilder.lavaCouldEnter(dross, next, state))
                    {
                        loose++;
                        example = example == null ? at.toShortString() + " could flow to " + next.toShortString() + " (" + state + ")" : example;
                    }
                }
            }
            expect(problems, loose == 0, loose + " way(s) for lava to flow after the build (e.g. " + example + ")");
        }

        void putBack(Map<Long, BlockState> journal)
        {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (Map.Entry<Long, BlockState> e : journal.entrySet())
            {
                pos.set(e.getKey());
                if (dross.getBlockState(pos) != e.getValue())
                {
                    dross.setBlock(pos, e.getValue(), Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    private static String counts(LairBuilder.Result r)
    {
        return "changed " + r.changed() + ", trees " + r.trees() + ", plants " + r.plants() + ", sealed " + r.sealed() + ", veins " + r.veins()
                + ", fissure lava " + r.fissureCells() + ", stalactites " + r.stalactites() + ", rivulets " + r.rivulets() + ", lava outside "
                + r.outsideLava() + ", fire guarded " + r.fireGuarded() + ", openings sealed " + r.openingsSealed() + ", gully " + r.gullyLength();
    }

    private static void expect(List<String> problems, boolean ok, String problem)
    {
        if (!ok)
        {
            problems.add(problem);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The made-up land
    // ---------------------------------------------------------------------------------------------

    /** The made-up land round (cx, cz), its ground about at {@code base}. */
    private static final class Land
    {
        final int cx;
        final int cz;
        final int base;
        /** The real land's highest block under it: clearing up never goes this low. */
        final int realLand;
        final ImprovedNoise roll;
        final ImprovedNoise bumps;
        final ImprovedNoise mesas;
        /** Natural lava added after the land was made (the leak check's block beside a rivulet's trench). */
        final List<BlockPos> extraLava = new ArrayList<>();

        Land(int cx, int cz, int base, int realLand)
        {
            this.cx = cx;
            this.cz = cz;
            this.base = base;
            this.realLand = realLand;
            RandomSource random = RandomSource.create(LAND_SEED);
            this.roll = new ImprovedNoise(random);
            this.bumps = new ImprovedNoise(random);
            this.mesas = new ImprovedNoise(random);
        }

        /** The land's top ground block at a column (Y): the same every time, whatever the world. */
        int top(int x, int z)
        {
            double h = ROLL * roll.noise(x / 48.0D, 10.5D, z / 48.0D) + BUMPS * bumps.noise(x / 12.0D, 20.5D, z / 12.0D);
            if (mesas.noise(x / 70.0D, 30.5D, z / 70.0D) > 0.3D)
            {
                h += MESA;
            }
            return base + (int) Math.floor(h);
        }

        int bottom()
        {
            return base - (int) Math.ceil(ROLL + BUMPS) - SLAB_DEPTH;
        }

        BlockPos ring(int distance, int degrees, int dy)
        {
            double a = Math.toRadians(degrees);
            int x = cx + (int) Math.round(distance * Math.cos(a));
            int z = cz + (int) Math.round(distance * Math.sin(a));
            return new BlockPos(x, top(x, z) + dy, z);
        }

        /** The ground (ash, blackstone and basalt over basalt), the trees and the lava pools. Nothing pokes its neighbours. */
        void build(ServerLevel dross)
        {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockState ash = ModBlocks.ASH.get().defaultBlockState();
            BlockState basalt = Blocks.BASALT.defaultBlockState();
            BlockState blackstone = Blocks.BLACKSTONE.defaultBlockState();
            for (int x = cx - HALF; x <= cx + HALF; x++)
            {
                for (int z = cz - HALF; z <= cz + HALF; z++)
                {
                    int h = top(x, z);
                    for (int y = bottom(); y <= h; y++)
                    {
                        BlockState state = y < h ? basalt : (LairBuilder.hash(x, 5, z) < 0.4F ? ash : LairBuilder.hash(x, 6, z) < 0.5F ? blackstone : basalt);
                        dross.setBlock(pos.set(x, y, z), state, Block.UPDATE_CLIENTS);
                    }
                }
            }
            BlockState log = Blocks.OAK_LOG.defaultBlockState();
            BlockState leaves = Blocks.OAK_LEAVES.defaultBlockState().setValue(LeavesBlock.PERSISTENT, true);
            for (int[] tree : TREES)
            {
                BlockPos foot = ring(tree[0], tree[1], 1);
                for (int k = 0; k < 5; k++)
                {
                    dross.setBlock(foot.above(k), log, Block.UPDATE_CLIENTS);
                }
                for (int dx = -2; dx <= 2; dx++)
                {
                    for (int dz = -2; dz <= 2; dz++)
                    {
                        for (int dy = 3; dy <= 5; dy++)
                        {
                            BlockPos at = foot.offset(dx, dy, dz);
                            if (Math.abs(dx) + Math.abs(dz) + (dy == 5 ? 2 : 0) <= 3 && dross.getBlockState(at).isAir())
                            {
                                dross.setBlock(at, leaves, Block.UPDATE_CLIENTS);
                            }
                        }
                    }
                }
            }
            for (BlockPos at : poolBlocks())
            {
                dross.setBlock(at, Blocks.LAVA.defaultBlockState(), Block.UPDATE_CLIENTS);
            }
        }

        /** The pools' lava blocks: 2 x 2, sunk to the lowest ground round them, so the land walls them in. */
        List<BlockPos> poolBlocks()
        {
            List<BlockPos> blocks = new ArrayList<>();
            for (int[] pool : POOLS)
            {
                BlockPos corner = ring(pool[0], pool[1], 0);
                int y = Integer.MAX_VALUE;
                for (int dx = -1; dx <= 2; dx++)
                {
                    for (int dz = -1; dz <= 2; dz++)
                    {
                        y = Math.min(y, top(corner.getX() + dx, corner.getZ() + dz));
                    }
                }
                for (int dx = 0; dx <= 1; dx++)
                {
                    for (int dz = 0; dz <= 1; dz++)
                    {
                        blocks.add(new BlockPos(corner.getX() + dx, y, corner.getZ() + dz));
                    }
                }
            }
            return blocks;
        }

        /** Removes the land (and anything left on it) again. */
        void clear(ServerLevel dross)
        {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();
            int topY = Math.min(dross.getMaxBuildHeight() - 1, base + ROOM_ABOVE);
            for (int x = cx - HALF - 4; x <= cx + HALF + 4; x++)
            {
                for (int z = cz - HALF - 4; z <= cz + HALF + 4; z++)
                {
                    int surface = Math.min(topY, dross.getHeight(Heightmap.Types.WORLD_SURFACE, x, z));
                    for (int y = Math.max(bottom() - 2, realLand + 1); y <= surface; y++)
                    {
                        if (!dross.getBlockState(pos.set(x, y, z)).isAir())
                        {
                            dross.setBlock(pos, air, Block.UPDATE_CLIENTS);
                        }
                    }
                }
            }
        }
    }
}
