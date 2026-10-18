package com.reg21meme.dross.boss.lair;

import com.reg21meme.dross.registry.ModBlocks;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.synth.ImprovedNoise;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.Vec3;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * Builds a Colossus lair into the real world: a volcano (its chunks must be loaded; server thread), a piece at a time
 * ({@link #start}, {@link Build#step}: {@link LairPlacer} gives each server tick at most about
 * {@link LairTuning#STEP_MILLIS} ms of it, so no tick stalls; the lava's work always gets one tick of its own). The pieces
 * always run in the same order, so the blocks come out exactly the same however the work is cut up (checked headlessly:
 * {@code SteppedLairGameTest}). In order:
 * <ol>
 *   <li>The plan ({@link #plan}, from {@link #planningGround}): which way the cave mouth faces, the floor's height
 *       (level with the land in front of the mouth), the rim's height, the land under the cone, and the way in outside
 *       the mouth ({@link LairShape#approach}: where the gully ends and the height of every column in front of it).</li>
 *   <li>Trees in the way (charred trees) are removed whole, so no floating bits are left.</li>
 *   <li>The cone: dark rock (basalt, blackstone, smooth basalt) from the land up to the volcano's surface: rough slopes
 *       with lumps and ridges running down them, ash on its shoulders, glowing magma here and there near the top, a
 *       jagged crest of obsidian and blackstone round the crater, and a brow of rock over the cave mouth.</li>
 *   <li>The cavern (the arena): a round floor 41 blocks across, walls {@link LairTuning#WALL_HEIGHT} high curving into a
 *       dome {@link LairTuning#DOME_HEIGHT} high, roughened, lined with rock.</li>
 *   <li>The crater: a funnel from the jagged rim down to the throat in the top of the dome, its walls flecked with
 *       obsidian and magma.</li>
 *   <li>The floor: obsidian with patches of blackstone, a polished blackstone medallion, border and two rings of bricks
 *       with magma blocks set evenly round them, a few more magma blocks scattered.</li>
 *   <li>The way in: a cave mouth 9 wide and 8 tall in the flank (a jagged lip of obsidian and blackstone, obsidian teeth
 *       hanging at its top, magma in its walls), narrowing to a passage 5 wide and 6 tall into the cavern; a gully cut
 *       into the flank in front of it, a level apron and the land in front shaped out to the land beyond, never a step
 *       of more than a block anywhere (on steep land a ramp).</li>
 *   <li>Obsidian stalactites hanging from the dome (never low over the middle).</li>
 *   <li>Safety: any fluid touching the carved space is turned to rock, and plants left without ground are removed.</li>
 *   <li>Magma veins up the walls and over the dome.</li>
 *   <li>Lava under the floor: glowing fissures, glass in the floor with lava one block under it, sealed in rock (also
 *       along the passage).</li>
 *   <li>Lava outside: rivulets running down the slopes from just under the rim, stepping down corner to corner (lava
 *       only touches the next block of the rivulet at a corner, so it can't flow), ending in pools at the foot; their
 *       banks a glowing crust of magma and obsidian.</li>
 *   <li>Every lava block is walled in (below and on its four sides: more lava or a block lava can't flow into), and
 *       nothing that can burn is left near any of it.</li>
 *   <li>Last, any fluid already there (a natural lava pool on the volcano's flank) next to a block this build emptied
 *       outside the carved spaces (over a rivulet, where a plant fell, where something burnable was cleared) gets that
 *       block back as rock, so it can't flow out either.</li>
 * </ol>
 * Blocks with a block entity (chests...) are never changed. Blocks are set without poking their neighbours (nothing
 * starts flowing or falling), but players see every change. The smoke rising from the crater is {@link LairSmoke}'s.
 */
final class LairBuilder
{
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockState LAVA = Blocks.LAVA.defaultBlockState();
    /** The fissures' glass: orange, so the lava under it glows like a hot crack. */
    static final Block FISSURE_GLASS = Blocks.ORANGE_STAINED_GLASS;
    private static final Direction[] LAVA_FLOWS = {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** A fluid in any of these neighbours of a block could flow into it (fluids flow down and sideways, never up). */
    private static final Direction[] FLOWS_IN_FROM = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};
    /** A block in any of these neighbours of an emptied one may have stood on it or leant on it. */
    private static final Direction[] HOLDS_UP = {Direction.UP, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    private LairBuilder() {}

    /**
     * The built lair.
     *
     * @param centerX     the floor's middle block (X)
     * @param centerZ     the floor's middle block (Z)
     * @param floorY      the floor blocks' Y (players stand at floorY + 1)
     * @param tunnel      the way the way in runs out of the cavern, towards its mouth
     * @param mouth       the cave mouth's distance from the middle (its lip)
     * @param rimY        the crater rim's crest (Y)
     * @param highLand    the high land round the volcano it was planned to stand above (Y; {@link LairShape.VolcanoPlan#highLand})
     * @param gullyEnd    where the gully in front of the mouth ends (its distance from the middle); the level apron and the
     *                    blend to the land follow it
     * @param approachEnd the last row of the land shaped in front of the mouth (its distance from the middle): the way in
     *                    meets the land just beyond it
     * @param entrance    where a player arriving stands: in the gully in front of the mouth, feet position
     * @param entranceYaw the way to face from there: into the mouth
     */
    record Layout(int centerX, int centerZ, int floorY, Direction tunnel, int mouth, int rimY, int highLand, int gullyEnd, int approachEnd,
                  Vec3 entrance, float entranceYaw)
    {
    }

    /** What building did: the layout, the plan it followed, the way in it shaped, and counts for the log. */
    record Result(Layout layout, LairShape.VolcanoPlan plan, LairShape.Approach approach, int changed, int trees, int plants,
                  int sealed, int veins, int fissureCells, int stalactites, int rivulets, int outsideLava, int fireGuarded,
                  int openingsSealed, int gullyLength, long millis)
    {
    }

    /**
     * The volcano's outer surface: the smooth cone with its lumps, the ridges running down its slopes, the jagged crest
     * round the crater and the brow of rock over the mouth. Plain maths from the plan and the world's seed, so the
     * headless checks can plan a way in exactly as the builder does without building anything.
     */
    static final class Surface
    {
        final LairShape.VolcanoPlan plan;
        /** The world's seed it was planned with (the builder's randomness comes from it too). */
        final long worldSeed;
        /** The cave mouth's distance from the middle ({@link LairShape#volcanoMouth}). */
        final int mouth;
        /** The lair's own noise (its lumps; also the builder's roughness, patches and rivulets). */
        final ImprovedNoise noise;
        private final Direction dir;
        private final Direction side;

        Surface(LairShape.VolcanoPlan plan, long worldSeed)
        {
            this.plan = plan;
            this.worldSeed = worldSeed;
            this.mouth = LairShape.volcanoMouth(plan);
            this.noise = new ImprovedNoise(RandomSource.create(seed(worldSeed, plan.x(), plan.z())));
            this.dir = plan.tunnel();
            this.side = dir.getClockWise();
        }

        /** The lair's seed: the world's, mixed with where it is. */
        static long seed(long worldSeed, int cx, int cz)
        {
            return worldSeed ^ (cx * 341873128712L + cz * 132897987541L);
        }

        /** The surface's height (Y, before rounding) at a column. */
        double at(int x, int z)
        {
            int dx = x - plan.x();
            int dz = z - plan.z();
            int floor = plan.floorY();
            double r = Math.sqrt(dx * dx + dz * dz);
            double top = plan.cone(dx, dz);
            double theta = Math.atan2(dz, dx);
            if (r > RIM_RADIUS)
            {
                double t = Math.min(1.0D, (r - RIM_RADIUS) / (CONE_BASE_RADIUS - RIM_RADIUS));
                double grow = Math.min(1.0D, t * 3.0D);
                top += LUMPS * grow * (1.0D - 0.6D * t) * noise.noise(x / LUMP_SCALE, 300.5D, z / LUMP_SCALE);
                top += FINE_LUMPS * grow * noise.noise(x / FINE_LUMP_SCALE, 600.5D, z / FINE_LUMP_SCALE);
                double twist = 2.2D * noise.noise(Math.cos(theta) * 1.7D, r / 14.0D, Math.sin(theta) * 1.7D + 50.0D);
                double rib = Math.cos(RIBS * theta + twist);
                top += RIB_HEIGHT * Math.pow(Math.max(0.0D, rib), 4.0D) * Math.sin(Math.PI * t);
            }
            if (r >= CRATER_RADIUS - 1.0D && r <= RIM_RADIUS + 2.0D)
            {
                double n = noise.noise(Math.cos(theta) * 2.6D, 1200.5D, Math.sin(theta) * 2.6D);
                double teeth = n > 0.0D ? RIM_TEETH * Math.pow(Math.min(1.0D, n / 0.55D), 1.5D)
                        : -RIM_NOTCHES * Math.pow(Math.min(1.0D, -n / 0.55D), 1.2D);
                double spike = hash(x, 77, z) < 0.15F ? 1.0D + 2.0D * hash(x, 78, z) : 0.0D;
                double middle = (CRATER_RADIUS + RIM_RADIUS) / 2.0D;
                double crest = Mth.clamp(1.0D - Math.abs(r - middle) / ((RIM_RADIUS - CRATER_RADIUS) / 2.0D + 2.0D), 0.0D, 1.0D);
                top += (teeth + spike) * crest;
            }
            if (r <= LairShape.SHELL_EDGE + 1.5D)
            {
                top = Math.max(top, floor + LairShape.shellTop(r) + MIN_COVER);
            }
            int s = dx * dir.getStepX() + dz * dir.getStepZ();
            if (s >= FLOOR_RADIUS && s <= mouth)
            {
                double need = floor + LairShape.passageHeight(s, mouth) + 1 + BROW
                        + 1.5D * noise.noise(x / 4.0D, 1500.5D, z / 4.0D);
                double over = Math.abs(dx * side.getStepX() + dz * side.getStepZ()) - (LairShape.passageHalfWidth(s, mouth) + 1.5D);
                if (over > 0.0D)
                {
                    need -= over * 1.2D;
                }
                top = Math.max(top, need);
            }
            return top;
        }

        /** The Y of the cone's top block in a column (what the cone is built up to), or {@link Integer#MIN_VALUE} past its foot. */
        int top(int x, int z)
        {
            int dx = x - plan.x();
            int dz = z - plan.z();
            return dx * dx + dz * dz <= (CONE_BASE_RADIUS + 1.0D) * (CONE_BASE_RADIUS + 1.0D) ? (int) Math.floor(at(x, z))
                    : Integer.MIN_VALUE;
        }
    }

    /** A volcano planned for a spot: its plan, its outer surface and its way in. */
    record Site(LairShape.VolcanoPlan plan, Surface surface, LairShape.Approach approach)
    {
        int mouth()
        {
            return surface.mouth;
        }
    }

    /**
     * Plans the volcano at {@code x}, {@code z} from {@code ground} (the land before anything is built): the best of
     * {@link LairShape#volcanoPlans} (the mouth facing the lowest, most even land) whose way in
     * ({@link LairShape#approach}, planned over the land with the cone on it) meets the land beyond; where none does,
     * the one that comes closest. Pure maths: the same for the builder and the headless checks.
     */
    static Site plan(int x, int z, LairShape.Ground ground, long worldSeed, int lowestFloor, int highestRim)
    {
        Site best = null;
        for (LairShape.VolcanoPlan plan : LairShape.volcanoPlans(x, z, ground, lowestFloor, highestRim))
        {
            Surface surface = new Surface(plan, worldSeed);
            LairShape.Approach approach = LairShape.approach(plan, surface.mouth,
                    (gx, gz) -> Math.max(ground.top(gx, gz), surface.top(gx, gz)));
            Site site = new Site(plan, surface, approach);
            if (approach.meetsLand())
            {
                return site;
            }
            if (best == null || approach.seam() < best.approach().seam())
            {
                best = site;
            }
        }
        return best;
    }

    /** The lowest floor the world's height allows (room under it for its rock and the fills). */
    static int lowestFloor(LevelHeightAccessor level)
    {
        return level.getMinBuildHeight() + UNDER_FLOOR + FILL_DEPTH + 2;
    }

    /** The highest rim the world's height allows (room over it for its teeth and smoke). */
    static int highestRim(LevelHeightAccessor level)
    {
        return level.getMaxBuildHeight() - (int) Math.ceil(RIM_TEETH) - 8;
    }

    /** Builds the lair with its floor's middle at {@code x}, {@code z}, all at once (every piece in this tick). */
    static Result build(ServerLevel level, int x, int z)
    {
        return start(level, x, z).finish();
    }

    /**
     * Starts building the lair with its floor's middle at {@code x}, {@code z}: {@link Build#step} plans it (from the
     * ground as it is then) and builds it a piece at a time, so the work can be spread over several ticks. Its chunks
     * must stay loaded until it's done. Server thread.
     */
    static Build start(ServerLevel level, int x, int z)
    {
        return start(level, x, z, null);
    }

    /**
     * As {@link #start(ServerLevel, int, int)}, with the generator's heights round the spot already worked out
     * ({@link #prefetchNoise}, on a background thread), so planning on the server thread only reads the real blocks (a
     * piece at a time, before the plan is made).
     */
    static Build start(ServerLevel level, int x, int z, @Nullable Map<Long, Integer> noiseHeights)
    {
        return new Build(level, x, z, noiseHeights);
    }

    /** Starts building a volcano already planned ({@link #plan}): the headless checks plan theirs on a made-up ground. */
    static Build start(ServerLevel level, Site site)
    {
        return new Build(new Work(level, site));
    }

    /**
     * Works out the generator's own heights of every column the volcano's plan at x, z reads (by planning it from the
     * generator's land alone and keeping each height it read). Reading them is the slow part of planning (about a
     * millisecond a column, thousands of columns), so the placer does it on its background thread while the chunks load;
     * the real plan then reads the same columns (nearly all of them) from this map. Any thread.
     */
    static Map<Long, Integer> prefetchNoise(ServerLevel level, int x, int z)
    {
        Map<Long, Integer> heights = new ConcurrentHashMap<>();
        LairShape.Ground noise = noiseGround(level);
        plan(x, z, (gx, gz) -> heights.computeIfAbsent(column(gx, gz), k -> noise.top(gx, gz)), level.getSeed(), lowestFloor(level),
                highestRim(level));
        return heights;
    }

    private static long column(int x, int z)
    {
        return ((long) x << 32) ^ (z & 0xFFFFFFFFL);
    }

    // ---------------------------------------------------------------- building a piece at a time

    /** A time limit for one step of a build (one server tick's share). */
    static final class Budget
    {
        private static final Budget UNLIMITED = new Budget(0L, true);
        private final long deadline;
        private final boolean unlimited;

        private Budget(long deadline, boolean unlimited)
        {
            this.deadline = deadline;
            this.unlimited = unlimited;
        }

        /** Work for at most about {@code millis} milliseconds from now (0: one piece of work, then stop). */
        static Budget millis(long millis)
        {
            return new Budget(System.nanoTime() + millis * 1_000_000L, false);
        }

        /** No limit: everything that's left. */
        static Budget unlimited()
        {
            return UNLIMITED;
        }

        /** True once the time is up. */
        boolean spent()
        {
            return !unlimited && System.nanoTime() - deadline >= 0L;
        }
    }

    /**
     * One kind of work in a build (the cone, the cavern, the lava's walls...), cut into pieces done in order: a step runs
     * at least one piece, then more while its time lasts. {@code begin} runs once, just before the first piece (where a
     * kind of work takes its own copy of a list, as it did when it ran all at once).
     */
    static final class Phase
    {
        final String name;
        /**
         * True for the lava's work (placing it, walling it in, its banks and the fire guard): all of it runs in one step
         * of its own ({@link Build#step}), so no lava is ever left unwalled, or next to something that burns, between
         * ticks.
         */
        final boolean lava;
        private final Runnable begin;
        private final IntSupplier pieces;
        private final IntConsumer piece;
        private boolean begun;
        private int count;
        private int next;

        private Phase(String name, boolean lava, Runnable begin, IntSupplier pieces, IntConsumer piece)
        {
            this.name = name;
            this.lava = lava;
            this.begin = begin;
            this.pieces = pieces;
            this.piece = piece;
        }

        /** Pieces {@code 0 .. count - 1}, the count read when it begins. */
        static Phase of(String name, Runnable begin, IntSupplier pieces, IntConsumer piece)
        {
            return new Phase(name, false, begin, pieces, piece);
        }

        /** One piece for each value from {@code from} to {@code to} (a row of columns, say). */
        static Phase rows(String name, int from, int to, IntConsumer row)
        {
            return new Phase(name, false, () -> {}, () -> Math.max(0, to - from + 1), i -> row.accept(from + i));
        }

        /** The whole work as one piece. */
        static Phase once(String name, Runnable work)
        {
            return new Phase(name, false, () -> {}, () -> 1, i -> work.run());
        }

        /** The same work, marked as part of the lava's work. */
        Phase lava()
        {
            return new Phase(name, true, begin, pieces, piece);
        }

        /** Does pieces until it's finished (true) or the budget is spent (false: call it again for the rest). */
        boolean run(Budget budget)
        {
            if (!begun)
            {
                begun = true;
                begin.run();
                count = pieces.getAsInt();
            }
            boolean any = false;
            while (next < count)
            {
                if (any && budget.spent())
                {
                    return false;
                }
                piece.accept(next++);
                any = true;
            }
            return true;
        }
    }

    /** How many pieces of at most {@code per} things a list of {@code size} things makes. */
    private static int piecesOf(int size, int per)
    {
        return (size + per - 1) / per;
    }

    /**
     * A lair being built, a piece at a time: planning it (reading the real ground first, when the generator's heights came
     * from the background thread), then the build's kinds of work in order ({@link Work#phases}). Each {@link #step} does
     * at least one piece and then more until its budget is spent; with no limit, all of it.
     */
    static final class Build
    {
        private final List<Phase> phases = new ArrayList<>();
        @Nullable
        private Work work;
        private int next;
        private int steps;
        private long nanos;
        private long longestNanos;
        /** Time (ns), steps and the last step for each kind of work, by name, in the order they first ran. */
        private final Map<String, long[]> kinds = new LinkedHashMap<>();
        /** The real ground of the columns the plan will read, worked out a piece at a time before it's made. */
        @Nullable
        private Map<Long, Integer> realGround;

        private Build(ServerLevel level, int x, int z, @Nullable Map<Long, Integer> noiseHeights)
        {
            LairShape.Ground ground = planningGround(level, noiseHeights);
            if (noiseHeights != null && !noiseHeights.isEmpty())
            {
                long[] columns = noiseHeights.keySet().stream().mapToLong(Long::longValue).sorted().toArray();
                Map<Long, Integer> real = new HashMap<>(columns.length * 2);
                realGround = real;
                int per = GROUND_COLUMNS_PER_PIECE;
                phases.add(Phase.of("reading the ground", () -> {}, () -> piecesOf(columns.length, per), i -> {
                    for (int k = i * per; k < Math.min(columns.length, (i + 1) * per); k++)
                    {
                        long c = columns[k];
                        real.put(c, ground.top((int) (c >> 32), (int) c));
                    }
                }));
            }
            phases.add(Phase.once("planning", () -> {
                Map<Long, Integer> read = realGround;
                LairShape.Ground known = read == null ? ground : (gx, gz) -> {
                    Integer top = read.get(column(gx, gz));
                    return top != null ? top : ground.top(gx, gz);
                };
                Site site = plan(x, z, known, level.getSeed(), lowestFloor(level), highestRim(level));
                realGround = null;
                begin(new Work(level, site));
            }));
        }

        private Build(Work work)
        {
            begin(work);
        }

        private void begin(Work work)
        {
            this.work = work;
            phases.addAll(work.phases());
        }

        /**
         * Does the next pieces of work, for about as long as the budget allows (always at least one piece); true once the
         * lair is finished (then {@link #result} is ready). The lava's work is the exception: it starts at the beginning of
         * a step and all of it runs in that step, whatever the budget (about 5 to 30 ms). Lava placed in the world starts
         * to flow about 30 ticks later, and can set fire to things near it any tick, so it's never left half walled in.
         */
        boolean step(Budget budget)
        {
            if (done())
            {
                return true;
            }
            long started = System.nanoTime();
            int step = steps++;
            boolean worked = false;
            while (next < phases.size())
            {
                Phase phase = phases.get(next);
                if (phase.lava && worked && !phases.get(next - 1).lava)
                {
                    break; // the lava starts a step of its own
                }
                long t0 = System.nanoTime();
                boolean finished = phase.run(phase.lava ? Budget.unlimited() : budget);
                worked = true;
                long[] kind = kinds.computeIfAbsent(phase.name, k -> new long[3]);
                kind[0] += System.nanoTime() - t0;
                if (kind[2] != step + 1L)
                {
                    kind[1]++; // one more step this kind of work ran in
                    kind[2] = step + 1L;
                }
                if (!finished)
                {
                    break;
                }
                next++;
                if (next >= phases.size() || budget.spent() && !(phase.lava && phases.get(next).lava))
                {
                    break;
                }
            }
            long took = System.nanoTime() - started;
            nanos += took;
            longestNanos = Math.max(longestNanos, took);
            return done();
        }

        /** Builds everything that's left, now (still in steps: the lava's work gets one of its own), and says what was built. */
        Result finish()
        {
            while (!step(Budget.unlimited()))
            {
                // the next step at once
            }
            return result();
        }

        /** True once every piece has run. */
        boolean done()
        {
            return work != null && next >= phases.size();
        }

        /** Tests: true when the next piece of work is the lava's (the leak check puts natural lava down just before it). */
        boolean atLava()
        {
            return work != null && next < phases.size() && phases.get(next).lava;
        }

        /** How many steps it has taken. */
        int steps()
        {
            return steps;
        }

        /** The longest one step took (ms), for the log. */
        long longestStepMillis()
        {
            return longestNanos / 1_000_000L;
        }

        /** Each kind of work's time and how many steps it ran in, for the log: "planning 12 ms (1), cone 85 ms (3), ...". */
        String describeSteps()
        {
            StringBuilder out = new StringBuilder();
            for (Map.Entry<String, long[]> e : kinds.entrySet())
            {
                if (!out.isEmpty())
                {
                    out.append(", ");
                }
                out.append(e.getKey()).append(' ').append(e.getValue()[0] / 1_000_000L).append(" ms (").append(e.getValue()[1]).append(')');
            }
            return out.toString();
        }

        /**
         * Tests: from now on remembers each block as it was before this build first changed it, by position
         * ({@link BlockPos#asLong}); call it before the first step of a planned build.
         */
        Map<Long, BlockState> recordChanges()
        {
            if (work == null)
            {
                throw new IllegalStateException("only a planned build's changes can be recorded (start(level, site))");
            }
            work.journal = new LinkedHashMap<>();
            return work.journal;
        }

        /** The planned layout (known once it's planned). */
        Layout layout()
        {
            if (work == null)
            {
                throw new IllegalStateException("the lair isn't planned yet");
            }
            LairShape.VolcanoPlan plan = work.plan;
            return new Layout(work.cx, work.cz, plan.floorY(), plan.tunnel(), work.mouth, plan.rimY(), plan.highLand(), work.gullyEnd,
                    work.wayIn.end(), work.entranceSpot(), plan.tunnel().getOpposite().toYRot());
        }

        /** What was built (once {@link #done}). */
        Result result()
        {
            return new Result(layout(), work.plan, work.wayIn, work.changed, work.trees, work.plants, work.sealed, work.veins,
                    work.fissureCells, work.stalactites, work.rivulets, work.lava.size() - work.fissureCells, work.fireGuarded,
                    work.openingsSealed, work.gullyEnd - work.mouth, nanos / 1_000_000L);
        }
    }

    /**
     * The ground the plan is made from: the lower of the real ground ({@link #groundTop}) and the world generator's own
     * terrain (before trees, basalt spires and other features). So a spire or a boulder standing in front of the mouth
     * doesn't lift the floor, while land dug lower than the generator made it (a lava pool's hollow) is still followed. A column
     * whose chunk isn't loaded (only if loading timed out) is read from the generator alone.
     */
    static LairShape.Ground planningGround(ServerLevel level)
    {
        return planningGround(level, null);
    }

    /** As {@link #planningGround(ServerLevel)}, reading the generator's heights from {@code noiseHeights} where it has them. */
    static LairShape.Ground planningGround(ServerLevel level, @Nullable Map<Long, Integer> noiseHeights)
    {
        LairShape.Ground generator = noiseGround(level);
        LairShape.Ground noise = noiseHeights == null ? generator : (x, z) -> {
            Integer known = noiseHeights.get(column(x, z));
            return known != null ? known : generator.top(x, z);
        };
        return (x, z) -> {
            if (!level.hasChunk(x >> 4, z >> 4))
            {
                return noise.top(x, z);
            }
            return Math.min(groundTop(level, x, z), noise.top(x, z));
        };
    }

    /** The world generator's own ground (the Y of a column's top ground block, before trees, rocks and lava pools). */
    static LairShape.Ground noiseGround(ServerLevel level)
    {
        net.minecraft.world.level.chunk.ChunkGenerator generator = level.getChunkSource().getGenerator();
        net.minecraft.world.level.levelgen.RandomState random = level.getChunkSource().randomState();
        return (x, z) -> generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
    }

    /** The Y of a column's top ground block: the highest full-topped block that isn't a log, leaves or fluid. */
    static int groundTop(LevelReader level, int x, int z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int y = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
        for (; y > level.getMinBuildHeight(); y--)
        {
            pos.set(x, y, z);
            if (isGround(level, pos, level.getBlockState(pos)))
            {
                return y;
            }
        }
        return y;
    }

    private static boolean isGround(BlockGetter level, BlockPos pos, BlockState state)
    {
        return !state.isAir() && state.getFluidState().isEmpty() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES)
                && state.isFaceSturdy(level, pos, Direction.UP);
    }

    /** True for the lair's rock (the lining's and the cone's blocks): veins may only replace these. */
    static boolean isLairRock(BlockState state)
    {
        return state.is(Blocks.BASALT) || state.is(Blocks.BLACKSTONE) || state.is(Blocks.SMOOTH_BASALT);
    }

    /**
     * Minecraft's own rule for whether lava can flow into a block ({@code FlowingFluid.canHoldFluid}): anything that
     * doesn't block movement (air, plants, fire...), except doors, signs, ladders, sugar cane, bubble columns and portals;
     * a waterloggable block only takes water.
     */
    static boolean lavaCouldEnter(BlockGetter level, BlockPos pos, BlockState state)
    {
        Block block = state.getBlock();
        if (block instanceof LiquidBlockContainer container)
        {
            return container.canPlaceLiquid(level, pos, state, Fluids.LAVA);
        }
        if (block instanceof DoorBlock || state.is(BlockTags.SIGNS) || state.is(Blocks.LADDER) || state.is(Blocks.SUGAR_CANE)
                || state.is(Blocks.BUBBLE_COLUMN) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)
                || state.is(Blocks.END_GATEWAY) || state.is(Blocks.STRUCTURE_VOID))
        {
            return false;
        }
        @SuppressWarnings("deprecation")
        boolean blocks = state.blocksMotion();
        return !blocks;
    }

    /** True if lava next to this block could set it alight (Forge's flammability, or vanilla's "ignited by lava"). */
    static boolean burns(BlockGetter level, BlockPos pos, BlockState state)
    {
        if (state.isAir())
        {
            return false;
        }
        if (state.ignitedByLava())
        {
            return true;
        }
        for (Direction face : Direction.values())
        {
            if (state.isFlammable(level, pos, face))
            {
                return true;
            }
        }
        return false;
    }

    /** A steady 0-1 number for a block (the same every time). */
    static float hash(int x, int y, int z)
    {
        long h = x * 73856093L ^ y * 83492791L ^ z * 19349663L;
        h ^= h >>> 13;
        h *= 0x5bd1e995L;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0F;
    }

    /** True for a ring's block that is one of its {@code count} evenly spaced magma blocks. */
    private static boolean isRingSpot(int dx, int dz, int count)
    {
        double angle = Math.atan2(dz, dx);
        double step = 2.0D * Math.PI / count;
        double off = Math.abs(Math.IEEEremainder(angle, step));
        return off * Math.sqrt(dx * dx + dz * dz) < 0.5D;
    }

    /** The angle between two directions (radians, 0 to pi). */
    static double angleBetween(double a, double b)
    {
        return Math.abs(Math.IEEEremainder(a - b, 2.0D * Math.PI));
    }

    // ---------------------------------------------------------------- one build

    private static final class Work
    {
        final ServerLevel level;
        final LairShape.VolcanoPlan plan;
        final int cx;
        final int cz;
        final int floor;
        final int rimY;
        final Direction dir;
        final Direction side;
        final int mouth;
        /** The volcano's outer surface. */
        final Surface shape;
        /** The way in outside the mouth, as planned: the gully's end and the shaped land in front. */
        final LairShape.Approach wayIn;
        final ImprovedNoise noise;
        final RandomSource random;
        /** Every block this build made (or found) air in the lair's space, for the safety passes. */
        final LongOpenHashSet carved = new LongOpenHashSet();
        /** Every lava block this build placed. */
        final List<BlockPos> lava = new ArrayList<>();
        /**
         * Every block this build emptied outside the carved spaces (over a rivulet or pool, a plant that fell, something
         * burnable cleared from near the lava), in order: the last pass seals any of them a fluid could flow into.
         */
        final LongArrayList opened = new LongArrayList();
        final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        /** A copy of {@link #carved} taken when a safety pass begins (it works through that copy a piece at a time). */
        LongArrayList snapshot = new LongArrayList();
        /** The fire guard's blocks looked at so far. */
        final LongOpenHashSet fireSeen = new LongOpenHashSet();
        /** Tests: each block as it was before this build first changed it, or null (not recording). */
        @Nullable
        Map<Long, BlockState> journal;
        /** Where the gully in front of the mouth ends (its distance from the middle). */
        int gullyEnd;
        int changed;
        int trees;
        int plants;
        int sealed;
        int veins;
        int fissureCells;
        int stalactites;
        int rivulets;
        int fireGuarded;
        int openingsSealed;

        Work(ServerLevel level, Site site)
        {
            this.level = level;
            this.plan = site.plan();
            this.cx = plan.x();
            this.cz = plan.z();
            this.floor = plan.floorY();
            this.rimY = plan.rimY();
            this.dir = plan.tunnel();
            this.side = dir.getClockWise();
            this.mouth = site.mouth();
            this.shape = site.surface();
            this.wayIn = site.approach();
            this.gullyEnd = wayIn.gullyEnd();
            this.noise = shape.noise;
            this.random = RandomSource.create(Surface.seed(shape.worldSeed, cx, cz) + 1L);
        }

        /**
         * The build's kinds of work, in order, each cut into pieces (a row of columns, a rivulet, a few thousand blocks of a
         * safety pass). Done one after another, piece by piece, they make exactly the same blocks however the work is cut
         * up. The lava's work (the fissures, the rivulets, walling it all in, its banks and the fire guard) is marked: it
         * runs all in one step.
         */
        List<Phase> phases()
        {
            int treeReach = (int) Math.ceil(CONE_BASE_RADIUS) + BLEND + 2;
            int coneReach = (int) Math.ceil(CONE_BASE_RADIUS) + 1;
            int shellReach = (int) Math.ceil(LairShape.SHELL_EDGE);
            int cavernReach = (int) Math.ceil(LairShape.FLOOR_EDGE + ROUGHNESS);
            int craterReach = (int) Math.ceil(CRATER_RADIUS + CRATER_ROUGHNESS + 2.0D);
            // The gully ends where the flank in front of the mouth is no higher than the floor (+1): the plan's gullyEnd.
            int maxSide = (int) Math.ceil(gullyHalf(gullyEnd) + (rimY - floor) * GULLY_WALL_SLOPE + 2.0D);
            return List.of(
                    Phase.rows("trees", -treeReach, treeReach, dx -> treeRow(dx, treeReach)),
                    Phase.rows("trees", treeReach + 1, wayIn.end() + 2, this::treesInFront),
                    Phase.rows("cone", -coneReach, coneReach, dx -> coneRow(dx, coneReach)),
                    Phase.rows("shell", -shellReach, shellReach, dx -> shellRow(dx, shellReach)),
                    Phase.rows("cavern", -cavernReach, cavernReach, dx -> cavernRow(dx, cavernReach)),
                    Phase.rows("crater", -craterReach, craterReach, dx -> craterRow(dx, craterReach)),
                    Phase.rows("crater", -craterReach, craterReach, dx -> craterWallRow(dx, craterReach)),
                    Phase.rows("floor", -FLOOR_RADIUS, FLOOR_RADIUS, this::floorRow),
                    Phase.rows("way in", FLOOR_RADIUS - 3, gullyEnd, s -> entranceRow(s, maxSide)),
                    Phase.once("way in", () -> {
                        mouthLip(maxSide);
                        teeth();
                        passageLights();
                    }),
                    Phase.rows("approach", wayIn.first(), wayIn.end(), this::approachRow),
                    Phase.once("stalactites", this::stalactites),
                    Phase.of("sealing fluids", () -> snapshot = new LongArrayList(carved),
                            () -> piecesOf(snapshot.size(), SAFETY_BLOCKS_PER_PIECE), this::sealFluids),
                    Phase.of("loose plants", () -> snapshot = new LongArrayList(carved),
                            () -> piecesOf(snapshot.size(), SAFETY_BLOCKS_PER_PIECE), this::dropUnsupported),
                    Phase.once("veins", this::veins),
                    Phase.once("lava", this::fissures).lava(),
                    Phase.rows("lava", 0, RIVULETS - 1, this::rivulet).lava(),
                    // (a pool may have opened up over a rivulet's last step)
                    Phase.of("lava walls", () -> lava.removeIf(at -> !level.getFluidState(at).is(Fluids.LAVA)),
                            () -> piecesOf(lava.size(), LAVA_BLOCKS_PER_PIECE), this::wallLava).lava(),
                    Phase.of("lava banks", () -> {}, () -> piecesOf(lava.size(), LAVA_BLOCKS_PER_PIECE), this::banks).lava(),
                    Phase.of("fire guard", () -> {}, () -> piecesOf(lava.size(), LAVA_BLOCKS_PER_PIECE), this::fireGuard).lava(),
                    Phase.of("sealing openings", () -> {}, () -> piecesOf(opened.size(), SAFETY_BLOCKS_PER_PIECE), this::sealOpenings));
        }

        Vec3 entranceSpot()
        {
            int s = mouth + ENTRANCE_GAP;
            return new Vec3(tx(s, 0) + 0.5D, floor + 1.0D, tz(s, 0) + 0.5D);
        }

        // ------------------------------------------------------------ helpers

        int tx(int s, int l)
        {
            return cx + dir.getStepX() * s + side.getStepX() * l;
        }

        int tz(int s, int l)
        {
            return cz + dir.getStepZ() * s + side.getStepZ() * l;
        }

        /** Distance along the way in (s) of a column offset from the middle. */
        int along(int dx, int dz)
        {
            return dx * dir.getStepX() + dz * dir.getStepZ();
        }

        /** Distance to the side of the way in (l) of a column offset from the middle. */
        int across(int dx, int dz)
        {
            return dx * side.getStepX() + dz * side.getStepZ();
        }

        BlockState get(int x, int y, int z)
        {
            return level.getBlockState(pos.set(x, y, z));
        }

        /** Sets a block (never one with a block entity). */
        void set(int x, int y, int z, BlockState state)
        {
            pos.set(x, y, z);
            BlockState here = level.getBlockState(pos);
            if (here.hasBlockEntity() || here == state)
            {
                return;
            }
            if (journal != null)
            {
                journal.putIfAbsent(pos.asLong(), here);
            }
            level.setBlock(pos, state, FLAGS);
            changed++;
        }

        /** Empties a block outside the carved spaces (remembered: the last pass makes sure no fluid can flow into it). */
        void empty(int x, int y, int z)
        {
            set(x, y, z, AIR);
            opened.add(BlockPos.asLong(x, y, z));
        }

        /** Makes a block part of the lair's open space. */
        void carve(int x, int y, int z)
        {
            set(x, y, z, AIR);
            carved.add(BlockPos.asLong(x, y, z));
        }

        /** True for what fills may replace: air, fluids, plants and other soft or see-through blocks. */
        boolean fillable(BlockState state)
        {
            return !state.hasBlockEntity() && (state.isAir() || !state.getFluidState().isEmpty() || state.canBeReplaced()
                    || !state.isSolid());
        }

        /** The volcano's rock at a block: basalt, blackstone or smooth basalt, mixed. */
        BlockState rock(int x, int y, int z)
        {
            float h = hash(x, y, z);
            if (h < 0.48F)
            {
                return Blocks.BASALT.defaultBlockState();
            }
            return (h < 0.87F ? Blocks.BLACKSTONE : Blocks.SMOOTH_BASALT).defaultBlockState();
        }

        /** Dark, glassy rock round hot places (the rim, the mouth's lip, the crater): obsidian, blackstone, now and then magma. */
        BlockState hotRock(int x, int y, int z, float magma)
        {
            float h = hash(x, y + 17, z);
            if (h < magma)
            {
                return Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            return (h < 0.5F ? Blocks.OBSIDIAN : h < 0.82F ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState();
        }

        /** A 0-1 roughness from a smooth noise ({@code salt} picks an independent one). */
        double rough01(int x, int y, int z, double scale, double salt)
        {
            double n = noise.noise(x / scale + salt, y / scale, z / scale - salt);
            return Mth.clamp((n + 0.6D) / 1.2D, 0.0D, 1.0D);
        }

        boolean inCavern(int x, int y, int z)
        {
            int dx = x - cx;
            int dz = z - cz;
            return LairShape.inCavern(Math.sqrt(dx * dx + dz * dz), y - floor, rough01(x, y, z, ROUGHNESS_SCALE, 0.0D) * ROUGHNESS);
        }

        /** Fills soft blocks under a column from {@code y} down, until solid ground (at most {@link LairTuning#FILL_DEPTH}). */
        void fillDown(int x, int y, int z)
        {
            for (int i = 0; i < FILL_DEPTH && y > level.getMinBuildHeight(); i++, y--)
            {
                if (!fillable(get(x, y, z)))
                {
                    return;
                }
                set(x, y, z, rock(x, y, z));
            }
        }

        /** The half-width of the gully's floor (at the floor's level + 1) at distance {@code s}. */
        double gullyHalf(int s)
        {
            return LairShape.gullyHalf(s, mouth);
        }

        // ------------------------------------------------------------ 1. trees in the way

        /** The trees in one row of the square round the volcano... */
        void treeRow(int dx, int reach)
        {
            for (int dz = -reach; dz <= reach; dz++)
            {
                removeTreesAt(cx + dx, cz + dz);
            }
        }

        /** ...and on the land shaped in front of the mouth (it can reach further out on steep land), a row at a time. */
        void treesInFront(int s)
        {
            for (int l = -wayIn.reachL() - 2; l <= wayIn.reachL() + 2; l++)
            {
                removeTreesAt(tx(s, l), tz(s, l));
            }
        }

        /** Removes every tree with a log in this column. */
        void removeTreesAt(int x, int z)
        {
            int top = level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            int ground = groundTop(level, x, z);
            for (int y = top; y > ground; y--)
            {
                if (get(x, y, z).is(BlockTags.LOGS))
                {
                    removeTree(new BlockPos(x, y, z));
                }
            }
        }

        /** Removes a whole tree (its logs and leaves, touching even corner to corner) from one of its logs. */
        void removeTree(BlockPos start)
        {
            LongOpenHashSet seen = new LongOpenHashSet();
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            queue.add(start);
            seen.add(start.asLong());
            int removed = 0;
            while (!queue.isEmpty() && removed < TREE_MAX_BLOCKS)
            {
                BlockPos at = queue.poll();
                BlockState state = level.getBlockState(at);
                if (!state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES))
                {
                    continue;
                }
                set(at.getX(), at.getY(), at.getZ(), AIR);
                carved.add(at.asLong());
                removed++;
                for (int dx = -1; dx <= 1; dx++)
                {
                    for (int dy = -1; dy <= 1; dy++)
                    {
                        for (int dz = -1; dz <= 1; dz++)
                        {
                            BlockPos next = at.offset(dx, dy, dz);
                            if (Math.abs(next.getX() - start.getX()) > TREE_REACH || Math.abs(next.getY() - start.getY()) > TREE_REACH
                                    || Math.abs(next.getZ() - start.getZ()) > TREE_REACH || !seen.add(next.asLong()))
                            {
                                continue;
                            }
                            queue.add(next);
                        }
                    }
                }
            }
            if (removed > 0)
            {
                trees++;
            }
        }

        // ------------------------------------------------------------ 2. the cone

        /** The volcano's surface (Y, before rounding) at a column: the smooth cone with its lumps, ridges, crest and brow. */
        double surface(int x, int z)
        {
            return shape.at(x, z);
        }

        /** The block on top of the cone at a column {@code r} blocks from the middle. */
        BlockState surfaceBlock(int x, int y, int z, double r)
        {
            if (r <= RIM_RADIUS + 1.5D)
            {
                return hotRock(x, y, z, 0.06F);
            }
            if (r <= HOT_SLOPE_RADIUS && hash(x, y + 5, z) < HOT_SLOPE_MAGMA)
            {
                return Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            if (hash(x, y + 31, z) < (r <= ASH_RADIUS ? SHOULDER_ASH : LOWER_ASH))
            {
                return ModBlocks.ASH.get().defaultBlockState();
            }
            return rock(x, y, z);
        }

        /** One row of the cone. */
        void coneRow(int dx, int reach)
        {
            for (int dz = -reach; dz <= reach; dz++)
            {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > CONE_BASE_RADIUS + 1.0D)
                {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int top = (int) Math.floor(surface(x, z));
                int ground = groundTop(level, x, z);
                for (int y = ground + 1; y <= top; y++)
                {
                    if (inCavern(x, y, z) || !fillable(get(x, y, z)))
                    {
                        continue;
                    }
                    set(x, y, z, y == top ? surfaceBlock(x, y, z, r) : rock(x, y, z));
                }
            }
        }

        // ------------------------------------------------------------ 3. the cavern's rock lining

        /** One row of the cavern's rock lining. */
        void shellRow(int dx, int reach)
        {
            for (int dz = -reach; dz <= reach; dz++)
            {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > LairShape.SHELL_EDGE)
                {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int top = floor + (int) Math.floor(LairShape.shellTop(r));
                for (int y = floor - UNDER_FLOOR; y <= top; y++)
                {
                    if (!inCavern(x, y, z))
                    {
                        set(x, y, z, rock(x, y, z));
                    }
                }
            }
        }

        // ------------------------------------------------------------ 4. the cavern and the crater

        /** One row of the cavern. */
        void cavernRow(int dx, int reach)
        {
            for (int dz = -reach; dz <= reach; dz++)
            {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r > LairShape.FLOOR_EDGE + ROUGHNESS)
                {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                int top = floor + (int) Math.ceil(LairShape.ceiling(Math.min(r, LairShape.FLOOR_EDGE)) + ROUGHNESS);
                for (int y = floor + 1; y <= top; y++)
                {
                    if (inCavern(x, y, z))
                    {
                        carve(x, y, z);
                    }
                }
            }
        }

        /** How far the crater is open at a block (its radius there, roughened). */
        double craterReach(int x, int y, int z)
        {
            return LairShape.craterRadius(y - floor, rimY - floor) + rough01(x, y, z, 4.0D, 21.5D) * CRATER_ROUGHNESS;
        }

        /** The crater's top block (its teeth and a little air over them). */
        int craterTop()
        {
            return rimY + (int) Math.ceil(RIM_TEETH) + 5;
        }

        /** One row of the crater (the funnel carved out)... */
        void craterRow(int dx, int reach)
        {
            int from = floor + DOME_HEIGHT - 3;
            int to = craterTop();
            for (int dz = -reach; dz <= reach; dz++)
            {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                for (int y = from; y <= to; y++)
                {
                    if (r <= craterReach(x, y, z))
                    {
                        if (!get(x, y, z).isAir() || y <= rimY)
                        {
                            carve(x, y, z);
                        }
                    }
                }
            }
        }

        /** ...then, once it's all carved, one row of its walls: obsidian and glowing magma among the rock. */
        void craterWallRow(int dx, int reach)
        {
            int to = craterTop();
            for (int dz = -reach; dz <= reach; dz++)
            {
                double r = Math.sqrt(dx * dx + dz * dz);
                int x = cx + dx;
                int z = cz + dz;
                for (int y = floor + DOME_HEIGHT + 2; y <= to; y++)
                {
                    double open = craterReach(x, y, z);
                    if (r > open && r <= open + 1.3D && isLairRock(get(x, y, z)))
                    {
                        set(x, y, z, hotRock(x, y, z, 0.14F));
                    }
                }
            }
        }

        // ------------------------------------------------------------ 5. the floor

        /** The cavern floor's block at an offset from the middle. */
        BlockState floorBlock(int dx, int dz)
        {
            int x = cx + dx;
            int z = cz + dz;
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r < 0.5D)
            {
                return Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
            }
            if (r <= MEDALLION)
            {
                return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            }
            for (int i = 0; i < RINGS.length; i++)
            {
                if (Math.abs(r - RINGS[i]) < 0.5D)
                {
                    if (isRingSpot(dx, dz, RING_MAGMA[i]))
                    {
                        return Blocks.MAGMA_BLOCK.defaultBlockState();
                    }
                    return (hash(x, 3, z) < CRACKED_SHARE ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS : Blocks.POLISHED_BLACKSTONE_BRICKS)
                            .defaultBlockState();
                }
            }
            if (r >= LairShape.FLOOR_EDGE - BORDER)
            {
                return Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            }
            return plainFloor(x, z);
        }

        /** Obsidian with patches of blackstone, and the odd magma block: the arena's floor and the passage's. */
        BlockState plainFloor(int x, int z)
        {
            if (hash(x, 7, z) < SCATTERED_MAGMA)
            {
                return Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            double n = noise.noise(x / PATCH_SCALE, 2100.5D, z / PATCH_SCALE);
            return (n > BLACKSTONE_PATCHES ? Blocks.BLACKSTONE : Blocks.OBSIDIAN).defaultBlockState();
        }

        /** One row of the floor. */
        void floorRow(int dx)
        {
            for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz++)
            {
                if (dx * dx + dz * dz <= LairShape.FLOOR_EDGE * LairShape.FLOOR_EDGE)
                {
                    set(cx + dx, floor, cz + dz, floorBlock(dx, dz));
                }
            }
        }

        // ------------------------------------------------------------ 6. the way in

        double entranceRough(int x, int y, int z)
        {
            return rough01(x, y, z, 3.0D, 41.5D) * MOUTH_ROUGHNESS;
        }

        /**
         * One row (at distance {@code s} from the middle) of the way in: the passage, the mouth and the gully carved, and
         * the walkway's floor. Then the mouth's lip, its teeth and the passage's lights ({@link #phases}).
         */
        void entranceRow(int s, int maxSide)
        {
            for (int l = -maxSide; l <= maxSide; l++)
            {
                int x = tx(s, l);
                int z = tz(s, l);
                int top = s <= mouth ? floor + MOUTH_HEIGHT + 3 : Math.max(groundTop(level, x, z),
                        level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                for (int y = floor + 1; y <= top; y++)
                {
                    if (LairShape.inEntrance(s, l, y - floor, mouth, entranceRough(x, y, z)))
                    {
                        carve(x, y, z);
                    }
                }
                // The walkway's floor, level with the cavern's: also where the land dips below it (filled under).
                boolean walkway = LairShape.inEntrance(s, l, 1, mouth, entranceRough(x, floor + 1, z));
                int dx = x - cx;
                int dz = z - cz;
                if (walkway && dx * dx + dz * dz > LairShape.FLOOR_EDGE * LairShape.FLOOR_EDGE)
                {
                    set(x, floor, z, s <= mouth ? plainFloor(x, z) : gullyFloor(x, z));
                    set(x, floor - 1, z, rock(x, floor - 1, z));
                    set(x, floor - 2, z, rock(x, floor - 2, z));
                    fillDown(x, floor - 3, z);
                }
            }
        }

        /** The gully's floor: blackstone and basalt under a scatter of ash (a worn path). */
        BlockState gullyFloor(int x, int z)
        {
            float h = hash(x, floor, z);
            if (h < 0.45F)
            {
                return ModBlocks.ASH.get().defaultBlockState();
            }
            return (h < 0.8F ? Blocks.BLACKSTONE : Blocks.BASALT).defaultBlockState();
        }

        /** The rock round the mouth (its lip and the brow's face) becomes obsidian, blackstone and the odd glowing magma. */
        void mouthLip(int maxSide)
        {
            for (int s = mouth - 2; s <= mouth + 2; s++)
            {
                for (int l = -maxSide; l <= maxSide; l++)
                {
                    int x = tx(s, l);
                    int z = tz(s, l);
                    for (int y = floor + 1; y <= floor + MOUTH_HEIGHT + BROW + 4; y++)
                    {
                        BlockState state = get(x, y, z);
                        if (state.isAir() || !(isLairRock(state) || state.is(ModBlocks.ASH.get())) || !nextToCarved(x, y, z))
                        {
                            continue;
                        }
                        set(x, y, z, hotRock(x, y, z, LIP_MAGMA));
                    }
                }
            }
        }

        boolean nextToCarved(int x, int y, int z)
        {
            for (Direction d : Direction.values())
            {
                if (carved.contains(BlockPos.asLong(x + d.getStepX(), y + d.getStepY(), z + d.getStepZ())))
                {
                    return true;
                }
            }
            return false;
        }

        /** Obsidian teeth hanging from the top of the mouth (never over its middle three columns, never below 5 blocks up). */
        void teeth()
        {
            int[] spots = {-3, 3, -2, 2, -4, 4};
            int made = 0;
            for (int i = 0; i < spots.length && made < MOUTH_TEETH; i++)
            {
                int l = spots[i];
                for (int s : new int[] {mouth, mouth - 1})
                {
                    int x = tx(s, l);
                    int z = tz(s, l);
                    int y = floor + 2;
                    while (y <= floor + MOUTH_HEIGHT + 3 && get(x, y, z).isAir())
                    {
                        y++;
                    }
                    if (y > floor + MOUTH_HEIGHT + 3)
                    {
                        continue;
                    }
                    int length = 1 + (hash(x, y, z) < 0.5F ? 1 : 0);
                    for (int k = 1; k <= length && y - k >= floor + 5; k++)
                    {
                        set(x, y - k, z, Blocks.OBSIDIAN.defaultBlockState());
                    }
                    if (s == mouth)
                    {
                        made++;
                    }
                }
            }
        }

        /** Dim magma lights in the passage's walls, both sides, every few blocks: the glow inside the mouth. */
        void passageLights()
        {
            for (int s = FLOOR_RADIUS + 2; s <= mouth - 1; s += PASSAGE_LIGHT_SPACING)
            {
                for (int sign : new int[] {-1, 1})
                {
                    for (int l = 1; l <= MOUTH_HALF_WIDTH + 3; l++)
                    {
                        int x = tx(s, sign * l);
                        int z = tz(s, sign * l);
                        BlockState state = get(x, floor + 2, z);
                        if (state.isAir())
                        {
                            continue;
                        }
                        if (isLairRock(state) || state.is(Blocks.OBSIDIAN))
                        {
                            set(x, floor + 2, z, Blocks.MAGMA_BLOCK.defaultBlockState());
                        }
                        break;
                    }
                }
            }
        }

        // ------------------------------------------------------------ 7. the apron and the blend to the land

        /**
         * Shapes the land in front of the gully to the plan ({@link LairShape#approach}): the level apron, and the blend
         * (and, on steep land, the ramp) out to the land, never stepping more than a block. Every column ends up with its
         * top block exactly at the planned height: anything over it is cut away (a spire, a lump of the cone), and under it
         * is filled with rock. Columns already at their height keep their own ground; on the walkway straight out from the
         * apron anything there that would trip a player (a drift of ash, a fallen log) is cleared.
         */
        void approachRow(int s)
        {
            for (int l = -wayIn.reachL(); l <= wayIn.reachL(); l++)
            {
                int x = tx(s, l);
                int z = tz(s, l);
                level.getChunk(x >> 4, z >> 4); // (loaded already, unless the chunk loading timed out: its heights must be real)
                int h = wayIn.height(s, l);
                boolean apron = wayIn.apronDistance(s, l) == 0;
                boolean walkway = apron || Math.abs(l) <= wayIn.apronHalf();
                int ground = groundTop(level, x, z);
                int top = Math.max(ground, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                if (ground == h && !apron)
                {
                    if (needsClearing(x, h, z, top, walkway))
                    {
                        for (int y = h + 1; y <= top; y++)
                        {
                            carve(x, y, z);
                        }
                    }
                    continue;
                }
                for (int y = h + 1; y <= top; y++)
                {
                    carve(x, y, z);
                }
                for (int y = ground + 1; y < h; y++)
                {
                    set(x, y, z, rock(x, y, z));
                }
                set(x, h, z, apron ? gullyFloor(x, z) : surfaceBlock(x, h, z, CONE_BASE_RADIUS));
                if (ground >= h)
                {
                    fillDown(x, h - 1, z);
                }
            }
        }

        /**
         * True if a column of the shaped land already at its height {@code h} must still be cleared above it: lava or
         * water stands on it (a natural pool: ground under a fluid is its bottom; left there, the fluid would be sealed to
         * rock next to the cut land, a block or two above it), or, on the walkway, something a player would trip over.
         */
        boolean needsClearing(int x, int h, int z, int top, boolean walkway)
        {
            for (int y = h + 1; y <= top; y++)
            {
                pos.set(x, y, z);
                BlockState state = level.getBlockState(pos);
                if (!state.getFluidState().isEmpty() || walkway && y <= h + 2 && !state.getCollisionShape(level, pos).isEmpty())
                {
                    return true;
                }
            }
            return false;
        }

        // ------------------------------------------------------------ 8. stalactites

        void stalactites()
        {
            double tunnelAngle = Math.atan2(dir.getStepZ(), dir.getStepX());
            double inner2 = STALACTITE_MIN_RADIUS * STALACTITE_MIN_RADIUS;
            double outer2 = STALACTITE_MAX_RADIUS * STALACTITE_MAX_RADIUS;
            for (int tries = 0; tries < STALACTITES * 4 && stalactites < STALACTITES; tries++)
            {
                double r = Math.sqrt(inner2 + random.nextDouble() * (outer2 - inner2));
                double a = random.nextDouble() * 2.0D * Math.PI;
                if (angleBetween(a, tunnelAngle) < Math.toRadians(STALACTITE_TUNNEL_CLEARANCE_DEGREES))
                {
                    continue;
                }
                int x = cx + (int) Math.round(r * Math.cos(a));
                int z = cz + (int) Math.round(r * Math.sin(a));
                int y = floor + 2;
                while (y <= floor + DOME_HEIGHT + 3 && get(x, y, z).isAir())
                {
                    y++;
                }
                if (y > floor + DOME_HEIGHT + 3 || !get(x - 1, y, z).isSolid() && !get(x + 1, y, z).isSolid())
                {
                    continue; // no ceiling here (the throat), or it's a lone bump
                }
                int top = y - 1; // the highest air block under the ceiling
                double rr = Math.sqrt(Math.pow(x - cx, 2) + Math.pow(z - cz, 2));
                // (its thick top and plus-shaped arms reach a block or two inwards of its middle)
                int most = Math.min(STALACTITE_MAX_LENGTH, top - floor - LairShape.stalactiteClearance(rr - 1.5D));
                if (most < STALACTITE_MIN_LENGTH)
                {
                    continue;
                }
                int length = STALACTITE_MIN_LENGTH + random.nextInt(most - STALACTITE_MIN_LENGTH + 1);
                spike(x, top, z, length);
                stalactites++;
            }
        }

        /** One stalactite hanging from {@code top} down: thick at the top, a plus shape, then a point. */
        void spike(int x, int top, int z, int length)
        {
            for (int k = 0; k < length; k++)
            {
                int y = top - k;
                double share = k / (double) length;
                int reach = share < STALACTITE_THICK_SHARE && length >= STALACTITE_THICK_LENGTH ? 2
                        : share < STALACTITE_PLUS_SHARE ? 1 : 0;
                for (int dx = -1; dx <= 1; dx++)
                {
                    for (int dz = -1; dz <= 1; dz++)
                    {
                        int far = Math.abs(dx) + Math.abs(dz);
                        if (far > reach)
                        {
                            continue;
                        }
                        if (far > 0 && !get(x + dx, y, z + dz).isAir())
                        {
                            continue;
                        }
                        BlockState block = far == 0 || hash(x + dx, y, z + dz) < 0.7F ? Blocks.OBSIDIAN.defaultBlockState()
                                : Blocks.BLACKSTONE.defaultBlockState();
                        set(x + dx, y, z + dz, block);
                        carved.remove(BlockPos.asLong(x + dx, y, z + dz));
                    }
                }
            }
        }

        // ------------------------------------------------------------ 9. safety

        /** Any fluid touching the open space becomes rock, so no lava or water can flow in (piece {@code p} of it). */
        void sealFluids(int p)
        {
            LongArrayList open = snapshot;
            for (int i = p * SAFETY_BLOCKS_PER_PIECE; i < Math.min(open.size(), (p + 1) * SAFETY_BLOCKS_PER_PIECE); i++)
            {
                BlockPos at = BlockPos.of(open.getLong(i));
                for (Direction d : Direction.values())
                {
                    BlockPos next = at.relative(d);
                    if (carved.contains(next.asLong()))
                    {
                        continue;
                    }
                    BlockState state = level.getBlockState(next);
                    if (!state.getFluidState().isEmpty() && !state.hasBlockEntity())
                    {
                        set(next.getX(), next.getY(), next.getZ(), rock(next.getX(), next.getY(), next.getZ()));
                        sealed++;
                    }
                }
            }
        }

        /** Plants and other blocks left without what holds them up (an ash layer on a cut block...) are removed (piece {@code p}). */
        void dropUnsupported(int p)
        {
            LongArrayList open = snapshot;
            for (int i = p * SAFETY_BLOCKS_PER_PIECE; i < Math.min(open.size(), (p + 1) * SAFETY_BLOCKS_PER_PIECE); i++)
            {
                BlockPos at = BlockPos.of(open.getLong(i));
                for (Direction d : HOLDS_UP)
                {
                    BlockPos next = at.relative(d);
                    while (!carved.contains(next.asLong()))
                    {
                        BlockState state = level.getBlockState(next);
                        if (state.isAir() || state.canSurvive(level, next))
                        {
                            break;
                        }
                        empty(next.getX(), next.getY(), next.getZ());
                        plants++;
                        next = next.above();
                    }
                }
            }
        }

        // ------------------------------------------------------------ 10. veins

        /** Glowing magma veins wandering up the walls and over the dome. */
        void veins()
        {
            double tunnelAngle = Math.atan2(dir.getStepZ(), dir.getStepX());
            for (int k = 0; k < MAGMA_VEINS; k++)
            {
                double theta = random.nextDouble() * 2.0D * Math.PI;
                for (int tries = 0; tries < 8 && angleBetween(theta, tunnelAngle) < Math.toRadians(VEIN_TUNNEL_CLEARANCE_DEGREES); tries++)
                {
                    theta = random.nextDouble() * 2.0D * Math.PI;
                }
                int startY = 2 + random.nextInt(4);
                int length = VEIN_MIN_LENGTH + random.nextInt(VEIN_MAX_LENGTH - VEIN_MIN_LENGTH + 1);
                boolean any = false;
                for (int i = 0; i < length; i++)
                {
                    int dy = startY + i;
                    double radius = dy <= WALL_HEIGHT ? LairShape.FLOOR_EDGE
                            : LairShape.FLOOR_EDGE * Math.sqrt(Math.max(0.0D, 1.0D - Math.pow((dy - WALL_HEIGHT) / (double) (DOME_HEIGHT - WALL_HEIGHT), 2.0D)));
                    if (radius < SKYLIGHT_RADIUS + 3.0D)
                    {
                        break;
                    }
                    for (double r = radius - 1.0D; r <= radius + 3.0D; r += 0.5D)
                    {
                        int x = cx + (int) Math.round(r * Math.cos(theta));
                        int z = cz + (int) Math.round(r * Math.sin(theta));
                        BlockState state = get(x, floor + dy, z);
                        if (state.isAir())
                        {
                            continue;
                        }
                        if (isLairRock(state))
                        {
                            set(x, floor + dy, z, Blocks.MAGMA_BLOCK.defaultBlockState());
                            any = true;
                        }
                        break;
                    }
                    theta += (random.nextDouble() - 0.5D) * 2.0D * VEIN_WOBBLE;
                }
                if (any)
                {
                    veins++;
                }
            }
        }

        // ------------------------------------------------------------ 11. lava under the floor

        /** Glowing fissures: glass in the floor with sealed lava one block under it, out from the medallion and along the way in. */
        void fissures()
        {
            LongOpenHashSet columns = new LongOpenHashSet();
            List<int[]> cells = new ArrayList<>();
            double rotation = random.nextDouble() * 2.0D * Math.PI;
            for (int k = 0; k < FISSURES; k++)
            {
                double angle = rotation + k * 2.0D * Math.PI / FISSURES + (random.nextDouble() - 0.5D) * 0.6D;
                double end = FISSURE_MIN_END + random.nextDouble() * (FISSURE_MAX_END - FISSURE_MIN_END);
                crack(angle, FISSURE_START, end, true, columns, cells);
            }
            // Along the way in, from the cavern to just short of the mouth.
            int l = 0;
            for (int s = FLOOR_RADIUS - 2; s <= mouth - PASSAGE_FISSURE_SHORT; s++)
            {
                addCell(new int[] {tx(s, l), tz(s, l)}, columns, cells);
                if (random.nextFloat() < 0.3F)
                {
                    l = Mth.clamp(l + (random.nextBoolean() ? 1 : -1), -1, 1);
                    addCell(new int[] {tx(s, l), tz(s, l)}, columns, cells); // the jog sideways, so it stays one line
                }
            }
            BlockState glass = FISSURE_GLASS.defaultBlockState();
            for (int[] cell : cells)
            {
                int x = cell[0];
                int z = cell[1];
                int dx = x - cx;
                int dz = z - cz;
                double r = Math.sqrt(dx * dx + dz * dz);
                int s = along(dx, dz);
                int side = Math.abs(across(dx, dz));
                boolean onFloor = r <= LairShape.FLOOR_EDGE - BORDER - 0.5D && r > MEDALLION + 0.5D;
                boolean inPassage = r > LairShape.FLOOR_EDGE - BORDER - 0.5D && s <= mouth - PASSAGE_FISSURE_SHORT
                        && side <= PASSAGE_HALF_WIDTH - 1;
                if (!onFloor && !inPassage)
                {
                    continue;
                }
                if (!get(x, floor + 1, z).isAir())
                {
                    continue; // not under open floor (it never is, but a fissure must never be roofed)
                }
                set(x, floor, z, glass);
                set(x, floor - 1, z, LAVA);
                lava.add(new BlockPos(x, floor - 1, z));
                fissureCells++;
            }
        }

        void addCell(int[] cell, LongOpenHashSet columns, List<int[]> cells)
        {
            if (columns.add(BlockPos.asLong(cell[0], 0, cell[1])))
            {
                cells.add(cell);
            }
        }

        /** One crack wandering outwards from {@code fromR} to {@code toR}, 4-connected (so it reads as one line). */
        void crack(double angle, double fromR, double toR, boolean branches, LongOpenHashSet columns, List<int[]> cells)
        {
            double px = cx + 0.5D + fromR * Math.cos(angle);
            double pz = cz + 0.5D + fromR * Math.sin(angle);
            int[] last = {Mth.floor(px), Mth.floor(pz)};
            addCell(last, columns, cells);
            double a = angle;
            for (int step = 0; step < 200; step++)
            {
                double radial = Math.atan2(pz - (cz + 0.5D), px - (cx + 0.5D));
                a += (random.nextDouble() - 0.5D) * 2.0D * FISSURE_WOBBLE;
                a = radial + Math.IEEEremainder(a - radial, 2.0D * Math.PI) * 0.75D; // keep heading outwards
                px += Math.cos(a) * 0.8D;
                pz += Math.sin(a) * 0.8D;
                int[] cell = {Mth.floor(px), Mth.floor(pz)};
                if (cell[0] == last[0] && cell[1] == last[1])
                {
                    continue;
                }
                if (cell[0] != last[0] && cell[1] != last[1])
                {
                    addCell(new int[] {cell[0], last[1]}, columns, cells);
                }
                addCell(cell, columns, cells);
                last = cell;
                double r = Math.hypot(px - (cx + 0.5D), pz - (cz + 0.5D));
                if (r >= toR)
                {
                    return;
                }
                if (branches && random.nextDouble() < FISSURE_BRANCH_CHANCE)
                {
                    int length = FISSURE_BRANCH_MIN + random.nextInt(FISSURE_BRANCH_MAX - FISSURE_BRANCH_MIN + 1);
                    crack(a + (random.nextBoolean() ? 0.9D : -0.9D), r, Math.min(toR, r + length), false, columns, cells);
                }
            }
        }

        // ------------------------------------------------------------ 12. lava outside

        /** True if a column is in the way in's band (the gully, the shaped land in front and a margin round them): no lava there. */
        boolean nearWayIn(int x, int z)
        {
            int dx = x - cx;
            int dz = z - cz;
            int s = along(dx, dz);
            double reach = gullyHalf(Math.min(s, gullyEnd)) + FIRE_GUARD_REACH + 3;
            if (s > gullyEnd)
            {
                reach = Math.max(reach, wayIn.reachL() + 2);
            }
            return s >= FLOOR_RADIUS - 4 && s <= Math.max(gullyEnd + APRON + BLEND, wayIn.end()) + 4 && Math.abs(across(dx, dz)) <= reach;
        }

        /** Places one lava block of a rivulet or pool: open above it, held up below. */
        void placeLava(int x, int y, int z)
        {
            BlockState here = get(x, y, z);
            if (here.hasBlockEntity() || here.getFluidState().is(Fluids.LAVA))
            {
                return;
            }
            set(x, y, z, LAVA);
            lava.add(new BlockPos(x, y, z));
            int top = Math.max(level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1, y);
            for (int yy = y + 1; yy <= top; yy++)
            {
                if (!get(x, yy, z).isAir())
                {
                    empty(x, yy, z);
                }
            }
            fillDown(x, y - 1, z);
        }

        /** Rivulet {@code k} (of {@link LairTuning#RIVULETS}, spread round the side away from the way in) and its pool. */
        void rivulet(int k)
        {
            double entranceAngle = Math.atan2(dir.getStepZ(), dir.getStepX());
            double spread = 2.0D * Math.PI - 2.0D * Math.toRadians(RIVULET_CLEARANCE_DEGREES);
            double spacing = spread / RIVULETS;
            double angle = entranceAngle + Math.PI + (k - (RIVULETS - 1) / 2.0D) * spacing
                    + (random.nextDouble() - 0.5D) * Math.toRadians(16.0D);
            double r0 = RIVULET_START + random.nextDouble() * 3.0D;
            int x = cx + (int) Math.round(r0 * Math.cos(angle));
            int z = cz + (int) Math.round(r0 * Math.sin(angle));
            if (nearWayIn(x, z))
            {
                return;
            }
            int y = groundTop(level, x, z);
            int lastX = 0;
            int lastZ = 0;
            for (int step = 0; step < RIVULET_MAX_STEPS; step++)
            {
                placeLava(x, y, z);
                if (Math.hypot(x - cx, z - cz) >= POOL_DISTANCE)
                {
                    break;
                }
                double out = Math.atan2(z - cz, x - cx) + RIVULET_MEANDER * 2.0D * noise.noise(step * 0.15D, k * 10.0D + 0.5D, 1500.5D);
                double ux = Math.cos(out);
                double uz = Math.sin(out);
                int sx = ux >= 0.0D ? 1 : -1;
                int sz = uz >= 0.0D ? 1 : -1;
                int ax = Math.abs(ux) >= Math.abs(uz) ? sx : 0;
                int az = ax == 0 ? sz : 0;
                // After a step down, an axis move must keep going the same way (never back beside the higher lava).
                if ((ax != 0 && ax == -lastX) || (az != 0 && az == -lastZ))
                {
                    ax = ax != 0 ? 0 : lastX;
                    az = az != 0 ? 0 : lastZ;
                    if (ax == 0 && az == 0)
                    {
                        ax = lastX;
                    }
                }
                int nx;
                int nz;
                if (groundTop(level, x + ax, z + az) >= y)
                {
                    nx = x + ax; // level ground: on along it at the same height
                    nz = z + az;
                    lastX = 0;
                    lastZ = 0;
                }
                else
                {
                    nx = x + sx; // it falls away: one step down, corner to corner
                    nz = z + sz;
                    int drop = Mth.clamp(y - groundTop(level, nx, nz), 1, RIVULET_MAX_DROP);
                    y -= drop;
                    lastX = sx;
                    lastZ = sz;
                }
                if (nearWayIn(nx, nz))
                {
                    break;
                }
                x = nx;
                z = nz;
            }
            pool(x, y, z);
            rivulets++;
        }

        /** A pool of lava at the rivulet's end, level at {@code y}. */
        void pool(int x0, int y, int z0)
        {
            int reach = (int) Math.ceil(POOL_RADIUS + 1.0D);
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    int x = x0 + dx;
                    int z = z0 + dz;
                    double edge = POOL_RADIUS + 0.8D * rough01(x, y, z, 2.5D, 61.5D);
                    if (dx * dx + dz * dz > edge * edge || nearWayIn(x, z))
                    {
                        continue;
                    }
                    placeLava(x, y, z);
                }
            }
        }

        /**
         * Every lava block is walled in: below it and on its four sides there is more lava, or a block lava can't flow
         * into (anything else becomes rock). So none of it can ever spread. Piece {@code p} of the lava.
         */
        void wallLava(int p)
        {
            BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
            for (BlockPos at : lavaPiece(p))
            {
                for (Direction d : LAVA_FLOWS)
                {
                    next.setWithOffset(at, d);
                    BlockState state = level.getBlockState(next);
                    if (state.getFluidState().is(Fluids.LAVA) && state.getFluidState().isSource())
                    {
                        continue;
                    }
                    if (lavaCouldEnter(level, next, state) || !state.getFluidState().isEmpty())
                    {
                        set(next.getX(), next.getY(), next.getZ(), rock(next.getX(), next.getY(), next.getZ()));
                        sealed++;
                    }
                }
            }
        }

        /** Piece {@code p} of the lava blocks placed ({@link LairTuning#LAVA_BLOCKS_PER_PIECE} of them). */
        List<BlockPos> lavaPiece(int p)
        {
            return lava.subList(p * LAVA_BLOCKS_PER_PIECE, Math.min(lava.size(), (p + 1) * LAVA_BLOCKS_PER_PIECE));
        }

        /** The banks of the lava outside: a glowing crust of magma and cooled obsidian, so the streams show from afar (piece {@code p}). */
        void banks(int p)
        {
            BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
            for (BlockPos at : lavaPiece(p))
            {
                if (at.getY() == floor - 1)
                {
                    continue; // under the floor's fissures
                }
                for (Direction d : Direction.Plane.HORIZONTAL)
                {
                    next.setWithOffset(at, d);
                    BlockState state = level.getBlockState(next);
                    if (!isLairRock(state) && !state.is(ModBlocks.ASH.get()))
                    {
                        continue;
                    }
                    float h = hash(next.getX(), next.getY() + 9, next.getZ());
                    if (h < BANK_MAGMA)
                    {
                        set(next.getX(), next.getY(), next.getZ(), Blocks.MAGMA_BLOCK.defaultBlockState());
                    }
                    else if (h < BANK_MAGMA + BANK_OBSIDIAN)
                    {
                        set(next.getX(), next.getY(), next.getZ(), Blocks.OBSIDIAN.defaultBlockState());
                    }
                }
            }
        }

        /** Nothing that can burn is left within reach of the lava (lava sets fire to burnable blocks near it). Piece {@code p}. */
        void fireGuard(int p)
        {
            BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
            LongOpenHashSet seen = fireSeen;
            for (BlockPos source : lavaPiece(p))
            {
                for (int dx = -FIRE_GUARD_REACH; dx <= FIRE_GUARD_REACH; dx++)
                {
                    for (int dz = -FIRE_GUARD_REACH; dz <= FIRE_GUARD_REACH; dz++)
                    {
                        for (int dy = -1; dy <= FIRE_GUARD_UP; dy++)
                        {
                            at.set(source.getX() + dx, source.getY() + dy, source.getZ() + dz);
                            if (!seen.add(at.asLong()))
                            {
                                continue;
                            }
                            BlockState state = level.getBlockState(at);
                            if (!state.hasBlockEntity() && burns(level, at, state))
                            {
                                if (state.canBeReplaced() || !state.isSolid())
                                {
                                    empty(at.getX(), at.getY(), at.getZ());
                                }
                                else
                                {
                                    set(at.getX(), at.getY(), at.getZ(), rock(at.getX(), at.getY(), at.getZ()));
                                }
                                fireGuarded++;
                            }
                        }
                    }
                }
            }
        }

        // ------------------------------------------------------------ 13. the last seal

        /**
         * A block this build emptied outside the carved spaces ({@link #empty}) that a fluid could still flow into (lava
         * or water beside it or above it, which nothing walled in) becomes rock again (piece {@code p}). The build's own
         * lava is walled in already, so this only ever finds fluid that was there before: a natural lava pool on the
         * volcano's flank that a rivulet ran under or beside. Opening the space above the rivulet ({@link #placeLava})
         * emptied the pool's lava over it, or the bank between them, and the rest of the pool could have poured out
         * sideways (the headless check found it on some seeds).
         */
        void sealOpenings(int p)
        {
            for (int i = p * SAFETY_BLOCKS_PER_PIECE; i < Math.min(opened.size(), (p + 1) * SAFETY_BLOCKS_PER_PIECE); i++)
            {
                BlockPos at = BlockPos.of(opened.getLong(i));
                BlockState state = level.getBlockState(at);
                if (!state.getFluidState().isEmpty() || !lavaCouldEnter(level, at, state))
                {
                    continue; // filled since (a wall, more lava), or something no fluid flows into
                }
                for (Direction d : FLOWS_IN_FROM)
                {
                    if (!level.getFluidState(at.relative(d)).isEmpty())
                    {
                        set(at.getX(), at.getY(), at.getZ(), rock(at.getX(), at.getY(), at.getZ()));
                        openingsSealed++;
                        break;
                    }
                }
            }
        }
    }
}
