package com.reg21meme.dross.boss.emberdactyl;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.ChestBlock;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.SkullBlock;
import net.minecraft.world.level.block.SnowLayerBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.IntBinaryOperator;
import javax.annotation.Nullable;

import static com.reg21meme.dross.boss.emberdactyl.EmberVolcanoTuning.*;

/**
 * The Emberdactyl's volcano: a tall, steep cone with a lava lake in its crater, the nest on a basalt pillar in the
 * middle of the lake (where its egg lies), and heaps of lava on its flanks: six rivers running down gullies as cascades
 * of lava pools, a lava pool at each river's foot with a field of cooled crust round it, and lava pockets between them.
 * A path winds up the cone from the side facing you to a lookout on the rim over the crater; scorched bones, basalt
 * spires, cinder vents and signal-fire smoke lie about it. Every number is in {@link EmberVolcanoTuning}.
 * <p>
 * <b>The lava never spills.</b> Every lava block has lava or something solid under it and on all four sides (the
 * Cinder Colossus's lairs' rule), so it's all still lava sources that never flow: a pool on a slope gets a rock lip on
 * its low side, and the next pool down sits below that lip. It's worked out in {@link #plan} before anything is placed
 * ({@link Plan#contain}), and the lava goes in last, all in one tick, once everything round it stands.
 * <p>
 * It's built a piece at a time ({@link Build}), at most {@link EmberVolcanoTuning#STEP_MILLIS} ms a tick, with its
 * chunks held loaded by tickets; {@link Plan#buildNow} does it all at once (the tests' way).
 */
public final class EmberVolcano
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_ember_volcano",
            Comparator.comparingLong(ChunkPos::toLong));
    /** Blocks are placed without poking their neighbours (no lava ticks, no redstone), but players see every change. */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;
    private static final int NO_LAVA = Integer.MIN_VALUE;
    /** How far the plan reaches from the middle: the cone's widest foot, then the ground round it. */
    private static final int EXT = Mth.ceil(BASE_RADIUS * (1.0D + LOBE_3 + LOBE_5 + LOBE_2) + FRINGE_OUT) + 1;
    private static final int N = 2 * EXT + 1;
    /** How far from its middle (X and Z) it may change anything: the cone, the ground round it and its fringe. */
    public static final int REACH = EXT;
    private static final double PATH_STEP = 0.5D;

    // What each column of the plan is.
    private static final byte OUTSIDE = 0;
    private static final byte FRINGE = 1;
    private static final byte APRON = 2;
    private static final byte CONE = 3;
    private static final byte CRATER = 4;
    private static final byte NEST = 5;
    private static final byte PATH = 6;
    private static final byte LIP = 7;
    private static final byte CRUST = 8;
    private static final byte PERCH = 9;

    private EmberVolcano() {}

    /**
     * Plans a volcano standing on {@code ground} (the ground block under its middle), its path starting on the side
     * {@code front} points to (towards whoever looks at it), shaped from {@code seed}.
     */
    public static Plan plan(BlockPos ground, Direction front, long seed)
    {
        Plan plan = new Plan(ground, Math.atan2(front.getStepZ(), front.getStepX()), seed, null);
        plan.shape();
        return plan;
    }

    /**
     * Plans a volcano on the real land round (x, z) (its home in the Cinder Wastes), shaped from {@code seed}.
     * {@code landTop} gives a column's top ground block as a world height (the generator's own heights: any thread). Its
     * ground level is chosen low on the land round its foot ({@link EmberVolcanoTuning#LAND_BASE_SHARE}) and its path
     * starts where the land just outside its foot is lowest; where the land is higher than its flanks it runs on into them
     * (re-laid as the volcano's rock), where it's lower the ground round its foot steps down to it a block at a time.
     */
    public static Plan planOnLand(int x, int z, IntBinaryOperator landTop, long seed)
    {
        Plan plan = new Plan(new BlockPos(x, 0, z), 0.0D, seed, landTop);
        plan.shape();
        return plan;
    }

    /** One point of the path's line: where (blocks from the middle), its height, how far along it is. */
    private record PathPoint(double x, double z, int height, double along)
    {
    }

    /** The volcano worked out in full, column by column, before a block is placed. */
    public static final class Plan
    {
        private BlockPos ground;
        private double front;
        private final long seed;
        /** Where the real land's heights come from (null: the showcase's flat floor at its ground). */
        @Nullable
        private final IntBinaryOperator landTop;
        /** The land's top block in each column, from its ground (all 0 on a flat floor). */
        private final int[] land = new int[N * N];
        /** The lowest and highest it changes, from its ground. */
        private int lowest = -FOUNDATION;
        private int highest = HEIGHT + CLEAR_ABOVE;
        private final double[] riverAngle = new double[RIVERS];
        private final double[] riverPhase = new double[RIVERS];
        private final double[] lobePhase = new double[3];
        private final double[] jagPhase = new double[2];
        private final List<PathPoint> path = new ArrayList<>();
        private double notchAngle;

        private final byte[] kind = new byte[N * N];
        private final int[] top = new int[N * N];
        private final int[] lava = new int[N * N];
        private final float[] slope = new float[N * N];
        private final byte[] lavaNear = new byte[N * N];
        private final boolean[] nearPath = new boolean[N * N];
        private final int[] pathHeight = new int[N * N];
        /** Path columns at its edge (a pale border) and with a light set into them (its dotted middle line). */
        private final boolean[] pathEdge = new boolean[N * N];
        private final boolean[] pathLight = new boolean[N * N];
        private final Map<Long, BlockState> decor = new HashMap<>();
        /** The Emberdactyl's perch: the room kept for its body and head (nothing stands there), and 2 round it (no lava). */
        private final boolean[] perchClear = new boolean[N * N];
        private final boolean[] perchNear = new boolean[N * N];
        private double perchAngle;
        private double perchRadius;
        private int perchLevel;
        private int lakeLevel;
        private int nestTop;
        private int lavaBlocks;
        private PathPoint lookout;

        private Plan(BlockPos ground, double front, long seed, @Nullable IntBinaryOperator landTop)
        {
            this.ground = ground.immutable();
            this.front = front;
            this.seed = seed;
            this.landTop = landTop;
        }

        // ---- What the showcase (and later the boss) needs ----

        /** The ground block under the volcano's middle. */
        public BlockPos ground()
        {
            return this.ground;
        }

        /** Every block it may change. */
        public BoundingBox box()
        {
            return new BoundingBox(this.ground.getX() - EXT, this.ground.getY() + this.lowest, this.ground.getZ() - EXT,
                    this.ground.getX() + EXT, this.ground.getY() + this.highest, this.ground.getZ() + EXT);
        }

        /** Where the egg stands: the middle of the nest's top. */
        public Vec3 eggFeet()
        {
            return new Vec3(this.ground.getX() + 0.5D, this.ground.getY() + this.nestTop + 1.0D, this.ground.getZ() + 0.5D);
        }

        /** The way the volcano's front faces (its path's foot), as a yaw: what something standing on show faces. */
        public float frontYaw()
        {
            return yaw(Math.cos(this.front), Math.sin(this.front));
        }

        /** The path's foot, standing on it, and the way up it (yaw). */
        public Vec3 pathFoot()
        {
            return feetOf(this.path.get(0));
        }

        public float pathFootYaw()
        {
            PathPoint a = this.path.get(0);
            PathPoint b = this.path.get(Math.min(this.path.size() - 1, 8));
            return yaw(b.x() - a.x(), b.z() - a.z());
        }

        /** The lookout at the path's top, standing on it, and the way to the nest (yaw). */
        public Vec3 lookout()
        {
            return feetOf(this.lookout);
        }

        public float lookoutYaw()
        {
            return yaw(-this.lookout.x(), -this.lookout.z());
        }

        /** Where the Emberdactyl perches: standing on its ledge on the rim, opposite the lookout. */
        public Vec3 perch()
        {
            return new Vec3(this.ground.getX() + 0.5D + Math.cos(this.perchAngle) * this.perchRadius, this.ground.getY() + this.perchLevel + 1.0D,
                    this.ground.getZ() + 0.5D + Math.sin(this.perchAngle) * this.perchRadius);
        }

        /** ...facing outwards, over the flank. */
        public float perchYaw()
        {
            return yaw(Math.cos(this.perchAngle), Math.sin(this.perchAngle));
        }

        /** The path's line from its foot to the lookout, a block each, standing on it (on each column's own height: it
         *  takes the nearest point of the line's, so it can be a block under the point that falls in it). */
        public List<BlockPos> pathLine()
        {
            List<BlockPos> line = new ArrayList<>();
            for (PathPoint p : this.path)
            {
                int dx = (int) Math.floor(p.x() + 0.5D);
                int dz = (int) Math.floor(p.z() + 0.5D);
                BlockPos pos = new BlockPos(this.ground.getX() + dx, this.ground.getY() + this.top[idx(dx, dz)] + 1,
                        this.ground.getZ() + dz);
                if (line.isEmpty() || !line.get(line.size() - 1).equals(pos))
                {
                    line.add(pos);
                }
            }
            return line;
        }

        /** How many lava blocks it has (all of them sources). */
        public int lavaBlocks()
        {
            return this.lavaBlocks;
        }

        /** Every lava block it places. */
        public void forEachLava(java.util.function.Consumer<BlockPos> action)
        {
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            for (int i = 0; i < N * N; i++)
            {
                if (this.lava[i] == NO_LAVA)
                {
                    continue;
                }
                for (int y = this.top[i] + 1; y <= this.lava[i]; y++)
                {
                    action.accept(pos.set(this.ground.getX() + i / N - EXT, this.ground.getY() + y, this.ground.getZ() + i % N - EXT));
                }
            }
        }

        /** Whether it lays this column's ground itself (the cone and the ground round it, not the thinning fringe). */
        public boolean manages(int x, int z)
        {
            int dx = x - this.ground.getX();
            int dz = z - this.ground.getZ();
            return inGrid(dx, dz) && this.kind[idx(dx, dz)] != OUTSIDE && this.kind[idx(dx, dz)] != FRINGE;
        }

        /** The lava lake's surface, as a world height. */
        public int lakeY()
        {
            return this.ground.getY() + this.lakeLevel;
        }

        /** The rim's height, as a world height. */
        public int rimY()
        {
            return this.ground.getY() + HEIGHT;
        }

        /** Starts building it a piece at a time: {@link Build#step} it every tick until it says it's done. */
        public Build start(ServerLevel level)
        {
            return new Build(this, level);
        }

        /** Builds it all at once. */
        public void buildNow(ServerLevel level)
        {
            Build build = start(level);
            while (!build.step(Long.MAX_VALUE))
            {
                // every phase in turn
            }
        }

        private Vec3 feetOf(PathPoint p)
        {
            int dx = (int) Math.floor(p.x() + 0.5D);
            int dz = (int) Math.floor(p.z() + 0.5D);
            return new Vec3(this.ground.getX() + dx + 0.5D, this.ground.getY() + this.top[idx(dx, dz)] + 1.0D,
                    this.ground.getZ() + dz + 0.5D);
        }

        // ---- Working it out ----

        private void shape()
        {
            Random random = new Random(this.seed);
            double[] riverJitter = new double[RIVERS];
            for (int k = 0; k < RIVERS; k++)
            {
                riverJitter[k] = (random.nextDouble() - 0.5D) * 0.3D;
                this.riverPhase[k] = random.nextDouble() * 2.0D * Math.PI;
            }
            for (int i = 0; i < this.lobePhase.length; i++)
            {
                this.lobePhase[i] = random.nextDouble() * 2.0D * Math.PI;
            }
            for (int i = 0; i < this.jagPhase.length; i++)
            {
                this.jagPhase[i] = random.nextDouble() * 2.0D * Math.PI;
            }
            if (this.landTop != null)
            {
                readLand();
            }
            for (int k = 0; k < RIVERS; k++)
            {
                this.riverAngle[k] = this.front + RIVER_PHASE + 2.0D * Math.PI * k / RIVERS + riverJitter[k];
            }
            Arrays.fill(this.lava, NO_LAVA);
            Arrays.fill(this.pathHeight, Integer.MIN_VALUE);
            planPath();
            planLand();
            planLakeAndNest();
            markPath();
            fitLand();
            planPerch();
            planFootPools(random);
            planRivers();
            planPockets(random);
            contain();
            skirt();
            measureLava();
            measureHeights();
            planDecor(random);
            LOGGER.info("[ember volcano] planned at {}: {} lava blocks, a path of {} blocks up to the rim",
                    this.ground.toShortString(), this.lavaBlocks, Math.round(this.path.get(this.path.size() - 1).along()));
        }

        /** The cone's foot is pushed in and out round it: its radius there, as a share of BASE_RADIUS. */
        private double lobes(double angle)
        {
            return 1.0D + LOBE_3 * Math.sin(3.0D * angle + this.lobePhase[0]) + LOBE_5 * Math.sin(5.0D * angle + this.lobePhase[1])
                    + LOBE_2 * Math.sin(2.0D * angle + this.lobePhase[2]);
        }

        /** The smooth cone (no ridges, gullies, bumps or notch) at a distance from the middle measured in lobes. */
        private static double smooth(double rr)
        {
            if (rr >= BASE_RADIUS)
            {
                return 0.0D;
            }
            double t = Math.min(1.0D, (BASE_RADIUS - rr) / (BASE_RADIUS - RIM_RADIUS));
            return HEIGHT * Math.pow(t, PROFILE_POWER);
        }

        private double smoothAt(double x, double z)
        {
            double angle = Math.atan2(z, x);
            return smooth(Math.sqrt(x * x + z * z) / lobes(angle));
        }

        // ---- On the real land ----

        /**
         * Reads the land round it (every {@link EmberVolcanoTuning#LAND_SAMPLE_STEP} blocks, blended between), then picks its
         * ground level (low on the land round its foot) and its front (where the land just outside its foot is lowest, so its
         * path starts on the land).
         */
        private void readLand()
        {
            int cx = this.ground.getX();
            int cz = this.ground.getZ();
            int step = Math.max(1, LAND_SAMPLE_STEP);
            int cells = Mth.ceil((double) (N - 1) / step);
            int[][] samples = new int[cells + 1][cells + 1];
            for (int a = 0; a <= cells; a++)
            {
                for (int b = 0; b <= cells; b++)
                {
                    samples[a][b] = this.landTop.applyAsInt(cx - EXT + Math.min(a * step, N - 1), cz - EXT + Math.min(b * step, N - 1));
                }
            }
            int[] abs = new int[N * N];
            for (int ix = 0; ix < N; ix++)
            {
                int a = Math.min(ix / step, cells - 1);
                int a0 = a * step;
                int a1 = Math.min((a + 1) * step, N - 1);
                double fa = a1 == a0 ? 0.0D : (double) (ix - a0) / (a1 - a0);
                for (int iz = 0; iz < N; iz++)
                {
                    int b = Math.min(iz / step, cells - 1);
                    int b0 = b * step;
                    int b1 = Math.min((b + 1) * step, N - 1);
                    double fb = b1 == b0 ? 0.0D : (double) (iz - b0) / (b1 - b0);
                    double h = samples[a][b] * (1.0D - fa) * (1.0D - fb) + samples[a + 1][b] * fa * (1.0D - fb)
                            + samples[a][b + 1] * (1.0D - fa) * fb + samples[a + 1][b + 1] * fa * fb;
                    abs[ix * N + iz] = (int) Math.round(h);
                }
            }
            // its ground: low on the land round its foot
            List<Integer> foot = new ArrayList<>();
            for (int dx = -EXT; dx <= EXT; dx++)
            {
                for (int dz = -EXT; dz <= EXT; dz++)
                {
                    double rr = Math.sqrt(dx * dx + dz * dz) / lobes(Math.atan2(dz, dx));
                    if (rr >= BASE_RADIUS * 0.97D && rr <= BASE_RADIUS * 1.06D)
                    {
                        foot.add(abs[idx(dx, dz)]);
                    }
                }
            }
            Collections.sort(foot);
            int base = foot.get((int) Math.floor(LAND_BASE_SHARE * (foot.size() - 1)));
            // its front: where the land just outside its foot is lowest
            double bestAngle = 0.0D;
            double bestHeight = Double.MAX_VALUE;
            for (int c = 0; c < FRONT_CHOICES; c++)
            {
                double angle = 2.0D * Math.PI * c / FRONT_CHOICES;
                double sum = 0.0D;
                for (int out = 2; out <= 8; out += 2)
                {
                    double r = BASE_RADIUS * lobes(angle) + out;
                    int dx = Mth.clamp((int) Math.floor(Math.cos(angle) * r + 0.5D), -EXT, EXT);
                    int dz = Mth.clamp((int) Math.floor(Math.sin(angle) * r + 0.5D), -EXT, EXT);
                    sum += abs[idx(dx, dz)];
                }
                if (sum < bestHeight - 1.0E-9D)
                {
                    bestHeight = sum;
                    bestAngle = angle;
                }
            }
            this.front = bestAngle;
            this.ground = new BlockPos(cx, base, cz);
            for (int i = 0; i < N * N; i++)
            {
                this.land[i] = abs[i] - base;
            }
        }

        /** Where the land is higher than the cone or the ground round its foot, they rise to it (re-laid as its rock). */
        private void fitLand()
        {
            for (int i = 0; i < N * N; i++)
            {
                if (this.kind[i] == CONE || this.kind[i] == APRON)
                {
                    this.top[i] = Math.max(this.top[i], this.land[i]);
                }
            }
        }

        /**
         * Where the land is lower than its ground, the ground round its foot steps down to it, at most
         * {@link EmberVolcanoTuning#SKIRT_STEP} a block, from everything that keeps its height (the cone, the path, the crust,
         * the lips and every column holding or walling in lava): the apron is lowered towards the land, and as much of the
         * fringe as the steps need is filled (and becomes apron). On a flat floor nothing changes.
         */
        private void skirt()
        {
            boolean[] soft = new boolean[N * N];
            int[] support = new int[N * N];
            for (int dx = -EXT; dx <= EXT; dx++)
            {
                for (int dz = -EXT; dz <= EXT; dz++)
                {
                    int i = idx(dx, dz);
                    byte k = this.kind[i];
                    soft[i] = (k == APRON || k == FRINGE) && this.lava[i] == NO_LAVA && !nextToLava(dx, dz);
                    support[i] = soft[i] || k == OUTSIDE ? Integer.MIN_VALUE / 2 : this.top[i];
                }
            }
            // the steps: a soft column is held up by its highest neighbour less a step (sweeps both ways until settled)
            boolean changed = true;
            for (int pass = 0; changed && pass < 4 * N; pass++)
            {
                changed = false;
                for (int sweep = 0; sweep < 2; sweep++)
                {
                    for (int n = 0; n < N * N; n++)
                    {
                        int i = sweep == 0 ? n : N * N - 1 - n;
                        if (!soft[i])
                        {
                            continue;
                        }
                        int dx = i / N - EXT;
                        int dz = i % N - EXT;
                        int best = support[i];
                        for (int ox = -1; ox <= 1; ox++)
                        {
                            for (int oz = -1; oz <= 1; oz++)
                            {
                                if ((ox != 0 || oz != 0) && inGrid(dx + ox, dz + oz))
                                {
                                    best = Math.max(best, support[idx(dx + ox, dz + oz)] - SKIRT_STEP);
                                }
                            }
                        }
                        if (best > support[i])
                        {
                            support[i] = best;
                            changed = true;
                        }
                    }
                }
            }
            int[] height = new int[N * N];
            for (int i = 0; i < N * N; i++)
            {
                if (soft[i])
                {
                    height[i] = this.kind[i] == APRON ? Math.min(this.top[i], Math.max(this.land[i], support[i]))
                            : Math.max(this.land[i], support[i]);
                }
            }
            // ...and among themselves: no soft column more than a step under a soft neighbour, so a natural ledge in the
            // ground round its foot is filled into steps too (the way onto its path is a block's climb at most)
            changed = true;
            for (int pass = 0; changed && pass < 4 * N; pass++)
            {
                changed = false;
                for (int sweep = 0; sweep < 2; sweep++)
                {
                    for (int n = 0; n < N * N; n++)
                    {
                        int i = sweep == 0 ? n : N * N - 1 - n;
                        if (!soft[i])
                        {
                            continue;
                        }
                        int dx = i / N - EXT;
                        int dz = i % N - EXT;
                        for (int ox = -1; ox <= 1; ox++)
                        {
                            for (int oz = -1; oz <= 1; oz++)
                            {
                                int nx = dx + ox;
                                int nz = dz + oz;
                                if ((ox != 0 || oz != 0) && inGrid(nx, nz) && soft[idx(nx, nz)]
                                        && height[idx(nx, nz)] - SKIRT_STEP > height[i])
                                {
                                    height[i] = height[idx(nx, nz)] - SKIRT_STEP;
                                    changed = true;
                                }
                            }
                        }
                    }
                }
            }
            for (int i = 0; i < N * N; i++)
            {
                if (!soft[i])
                {
                    continue;
                }
                if (this.kind[i] == APRON)
                {
                    this.top[i] = height[i];
                }
                else if (height[i] > this.land[i])
                {
                    this.kind[i] = APRON;
                    this.top[i] = height[i];
                }
            }
        }

        private boolean nextToLava(int dx, int dz)
        {
            for (Direction side : Direction.Plane.HORIZONTAL)
            {
                int nx = dx + side.getStepX();
                int nz = dz + side.getStepZ();
                if (inGrid(nx, nz) && this.lava[idx(nx, nz)] != NO_LAVA)
                {
                    return true;
                }
            }
            return false;
        }

        /** The lowest and highest heights it changes (for its box). */
        private void measureHeights()
        {
            for (int i = 0; i < N * N; i++)
            {
                if (this.kind[i] == OUTSIDE)
                {
                    continue;
                }
                this.lowest = Math.min(this.lowest, Math.min(this.top[i], this.land[i]) - FOUNDATION);
                this.highest = Math.max(this.highest, Math.max(this.land[i], clearTo(i)));
            }
        }

        /** The top ground block it lays in a column it manages (the cone, the path, the ground round its foot), as a world
         *  height; the land's elsewhere. */
        public int groundY(int x, int z)
        {
            return manages(x, z) ? this.ground.getY() + this.top[idx(x - this.ground.getX(), z - this.ground.getZ())] : landY(x, z);
        }

        /** The land's top block in a column, as a world height (its ground on a flat floor). */
        public int landY(int x, int z)
        {
            int dx = x - this.ground.getX();
            int dz = z - this.ground.getZ();
            return this.ground.getY() + (inGrid(dx, dz) ? this.land[idx(dx, dz)] : 0);
        }

        /**
         * The path: from its foot, just out from the cone in front, it winds up round the cone, each step the most
         * inward one that climbs no more than {@link EmberVolcanoTuning#PATH_GRADE} a block, until it's nearly at the
         * rim; then it runs on, level, over the rim to the lookout. Worked out on the smooth cone (the land's bumps
         * are cut or filled to it).
         */
        private void planPath()
        {
            double angle = this.front;
            double r = (BASE_RADIUS + PATH_START_OUT) * lobes(angle);
            double x = Math.cos(angle) * r;
            double z = Math.sin(angle) * r;
            int end = HEIGHT - NOTCH_DEPTH - PATH_CUT;
            double along = 0.0D;
            this.path.add(new PathPoint(x, z, 0, 0.0D));
            for (int guard = 0; guard < 4000; guard++)
            {
                double here = smoothAt(x, z);
                if (here - PATH_CUT >= end)
                {
                    break;
                }
                double radial = Math.sqrt(x * x + z * z);
                double ux = x / radial;
                double uz = z / radial;
                // tangent, going round anticlockwise (seen from above)
                double tx = -uz;
                double tz = ux;
                double bestX = x + tx * PATH_STEP;
                double bestZ = z + tz * PATH_STEP;
                for (double in = 1.0D; in >= -0.6D; in -= 0.05D)
                {
                    double along2 = Math.sqrt(Math.max(0.0D, 1.0D - in * in));
                    double nx = x + (-ux * in + tx * along2) * PATH_STEP;
                    double nz = z + (-uz * in + tz * along2) * PATH_STEP;
                    if (smoothAt(nx, nz) - here <= PATH_GRADE * PATH_STEP)
                    {
                        bestX = nx;
                        bestZ = nz;
                        break;
                    }
                }
                x = bestX;
                z = bestZ;
                along += PATH_STEP;
                int height = Math.max(0, Math.min(end, (int) Math.floor(smoothAt(x, z)) - PATH_CUT));
                this.path.add(new PathPoint(x, z, height, along));
            }
            // level from here, inwards over the rim to the lookout
            PathPoint last = this.path.get(this.path.size() - 1);
            this.notchAngle = Math.atan2(last.z(), last.x());
            double radial = Math.sqrt(last.x() * last.x() + last.z() * last.z());
            double target = (RIM_RADIUS - LOOKOUT_IN) * lobes(this.notchAngle);
            double ux = last.x() / radial;
            double uz = last.z() / radial;
            for (double in = radial - PATH_STEP; in >= target; in -= PATH_STEP)
            {
                along += PATH_STEP;
                this.path.add(new PathPoint(ux * in, uz * in, end, along));
            }
            this.lookout = this.path.get(this.path.size() - 1);
        }

        /** The rim's crest at this angle: jagged, with a notch where the path comes over it. */
        private double crest(double angle)
        {
            double jag = RIM_JAG_4 * Math.sin(4.0D * angle + this.jagPhase[0]) + RIM_JAG_9 * Math.sin(9.0D * angle + this.jagPhase[1])
                    + RIM_JAG_NOISE * (2.0D * noise(Math.cos(angle) * 4.0D, Math.sin(angle) * 4.0D, 11) - 1.0D);
            double off = angleBetween(angle, this.notchAngle) / NOTCH_WIDTH;
            double notch = Math.exp(-off * off);
            return HEIGHT + jag * (1.0D - notch) - NOTCH_DEPTH * notch;
        }

        /** How far across (blocks, signed) a spot is from river k's line, at its distance rr (measured in lobes). */
        private double riverOffset(int k, double angle, double rr)
        {
            double wiggle = RIVER_WIGGLE * Math.sin(rr / RIVER_WIGGLE_LENGTH + this.riverPhase[k]);
            return angleBetween(angle, this.riverAngle[k]) * rr - wiggle;
        }

        /** The land: every column's height, from the cone's shape, its ridges, gullies and bumps. */
        private void planLand()
        {
            this.lakeLevel = HEIGHT - CRATER_DEPTH;
            for (int dx = -EXT; dx <= EXT; dx++)
            {
                for (int dz = -EXT; dz <= EXT; dz++)
                {
                    int i = idx(dx, dz);
                    double r = Math.sqrt(dx * dx + dz * dz);
                    double angle = Math.atan2(dz, dx);
                    double lobe = lobes(angle);
                    double rr = r / lobe;
                    double outside = r - BASE_RADIUS * lobe;
                    if (outside >= FRINGE_OUT)
                    {
                        this.kind[i] = OUTSIDE;
                        continue;
                    }
                    if (outside >= APRON_OUT)
                    {
                        this.kind[i] = FRINGE;
                        this.slope[i] = -1.0F;
                        continue;
                    }
                    if (rr >= BASE_RADIUS)
                    {
                        this.kind[i] = APRON;
                        this.slope[i] = -1.0F;
                        continue;
                    }
                    double crest = crest(angle);
                    if (rr >= RIM_RADIUS)
                    {
                        double t = (BASE_RADIUS - rr) / (BASE_RADIUS - RIM_RADIUS);
                        double bell = Math.sin(Math.PI * t);
                        double h = crest * Math.pow(t, PROFILE_POWER);
                        h += RIDGE_HEIGHT * Math.sin(RIDGES * angle + rr / 6.0D) * bell;
                        h += ROUGHNESS * (2.0D * noise(dx / ROUGHNESS_SCALE, dz / ROUGHNESS_SCALE, 3) - 1.0D) * Math.sqrt(t);
                        double gully = 0.0D;
                        for (int k = 0; k < RIVERS; k++)
                        {
                            double off = riverOffset(k, angle, rr) / (GULLY_HALF_WIDTH * 0.7D);
                            gully = Math.max(gully, GULLY_DEPTH * Math.exp(-off * off));
                        }
                        h -= gully * Math.pow(Math.max(0.0D, bell), 0.7D);
                        this.kind[i] = CONE;
                        this.top[i] = Math.max(0, (int) Math.round(h));
                        this.slope[i] = (float) t;
                    }
                    else
                    {
                        double lakeR = LAKE_RADIUS + LAKE_WOBBLE * (2.0D * noise(Math.cos(angle) * 3.0D, Math.sin(angle) * 3.0D, 17) - 1.0D);
                        this.kind[i] = CRATER;
                        this.slope[i] = 2.0F;
                        if (rr >= lakeR)
                        {
                            double u = Math.min(1.0D, (rr - lakeR) / (RIM_RADIUS - lakeR));
                            double h = this.lakeLevel + 1 + (crest - this.lakeLevel - 1) * Math.pow(u, INNER_POWER)
                                    + 0.8D * (2.0D * noise(dx / 3.0D, dz / 3.0D, 5) - 1.0D) * u;
                            this.top[i] = Math.max(this.lakeLevel + 1, (int) Math.round(h));
                        }
                        else
                        {
                            this.top[i] = this.lakeLevel - LAKE_DEPTH;
                            this.lava[i] = this.lakeLevel;
                        }
                    }
                }
            }
        }

        /** The nest's basalt pillar out of the lake, a little narrower at its top. */
        private void planLakeAndNest()
        {
            this.nestTop = this.lakeLevel + NEST_HEIGHT;
            int reach = Mth.ceil(NEST_FOOT_RADIUS);
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r > NEST_FOOT_RADIUS)
                    {
                        continue;
                    }
                    int i = idx(dx, dz);
                    int h = r <= NEST_TOP_RADIUS ? this.nestTop
                            : this.lakeLevel + (int) Math.round(NEST_HEIGHT * (NEST_FOOT_RADIUS - r) / (NEST_FOOT_RADIUS - NEST_TOP_RADIUS));
                    if (h <= this.lakeLevel)
                    {
                        continue;
                    }
                    this.kind[i] = NEST;
                    this.top[i] = h;
                    this.lava[i] = NO_LAVA;
                }
            }
        }

        /** The path's columns: each takes the height of the nearest point of its line (cut or filled to it). */
        private void markPath()
        {
            double[] best = new double[N * N];
            Arrays.fill(best, Double.MAX_VALUE);
            int reach = Mth.ceil(Math.max(PATH_HALF_WIDTH, LOOKOUT_HALF_WIDTH)) + 1;
            for (PathPoint p : this.path)
            {
                boolean atLookout = p == this.lookout;
                double half = atLookout ? LOOKOUT_HALF_WIDTH : PATH_HALF_WIDTH;
                int cx = (int) Math.floor(p.x() + 0.5D);
                int cz = (int) Math.floor(p.z() + 0.5D);
                for (int dx = cx - reach; dx <= cx + reach; dx++)
                {
                    for (int dz = cz - reach; dz <= cz + reach; dz++)
                    {
                        if (!inGrid(dx, dz))
                        {
                            continue;
                        }
                        double d = Math.hypot(dx - p.x(), dz - p.z());
                        int i = idx(dx, dz);
                        if (d <= half && d < best[i])
                        {
                            best[i] = d;
                            this.pathHeight[i] = p.height();
                        }
                    }
                }
            }
            for (int i = 0; i < N * N; i++)
            {
                if (this.pathHeight[i] != Integer.MIN_VALUE)
                {
                    this.kind[i] = PATH;
                    this.top[i] = this.pathHeight[i];
                    this.lava[i] = NO_LAVA;
                }
            }
            // its border (a path column beside one that isn't) and the lights down its middle
            for (int dx = -EXT + 1; dx < EXT; dx++)
            {
                for (int dz = -EXT + 1; dz < EXT; dz++)
                {
                    int i = idx(dx, dz);
                    if (this.kind[i] == PATH && (this.kind[idx(dx + 1, dz)] != PATH || this.kind[idx(dx - 1, dz)] != PATH
                            || this.kind[idx(dx, dz + 1)] != PATH || this.kind[idx(dx, dz - 1)] != PATH))
                    {
                        this.pathEdge[i] = true;
                    }
                }
            }
            double nextLight = PATH_LIGHT_EVERY * 0.5D;
            for (PathPoint p : this.path)
            {
                if (p.along() < nextLight && p != this.lookout)
                {
                    continue;
                }
                int dx = (int) Math.floor(p.x() + 0.5D);
                int dz = (int) Math.floor(p.z() + 0.5D);
                if (inGrid(dx, dz) && this.kind[idx(dx, dz)] == PATH)
                {
                    this.pathLight[idx(dx, dz)] = true;
                    this.pathEdge[idx(dx, dz)] = false;
                }
                nextLight = p.along() + PATH_LIGHT_EVERY;
            }
            for (int dx = -EXT; dx <= EXT; dx++)
            {
                for (int dz = -EXT; dz <= EXT; dz++)
                {
                    if (this.kind[idx(dx, dz)] != PATH)
                    {
                        continue;
                    }
                    for (int ox = -LAVA_PATH_CLEARANCE; ox <= LAVA_PATH_CLEARANCE; ox++)
                    {
                        for (int oz = -LAVA_PATH_CLEARANCE; oz <= LAVA_PATH_CLEARANCE; oz++)
                        {
                            if (inGrid(dx + ox, dz + oz))
                            {
                                this.nearPath[idx(dx + ox, dz + oz)] = true;
                            }
                        }
                    }
                }
            }
        }

        /**
         * The Emberdactyl's perch (the user, October 29: "make his spot on the edge of the top of the volcano facing
         * outwards"): a flat ledge of rock at the crest's height opposite the lookout, jutting out over the outer flank,
         * and room kept clear for its perched body (the crest beside it cut down to the ledge) and for its neck and head
         * held out in front of it: no spire, vent, lava or anything else in it.
         */
        private void planPerch()
        {
            this.perchAngle = this.notchAngle + Math.PI;
            this.perchRadius = (RIM_RADIUS + PERCH_RIM_OUT) * lobes(this.perchAngle);
            this.perchLevel = (int) Math.round(crest(this.perchAngle));
            double fx = Math.cos(this.perchAngle);
            double fz = Math.sin(this.perchAngle);
            double px = fx * this.perchRadius;
            double pz = fz * this.perchRadius;
            int reach = Mth.ceil(Math.hypot(PERCH_HEAD_FRONT, PERCH_BODY_HALF_WIDTH)) + 1;
            for (int dx = (int) Math.floor(px) - reach; dx <= (int) Math.floor(px) + reach; dx++)
            {
                for (int dz = (int) Math.floor(pz) - reach; dz <= (int) Math.floor(pz) + reach; dz++)
                {
                    if (!inGrid(dx, dz))
                    {
                        continue;
                    }
                    int i = idx(dx, dz);
                    byte k = this.kind[i];
                    if (k == PATH || k == NEST || k == OUTSIDE || k == FRINGE)
                    {
                        continue;
                    }
                    double ox = dx - px;
                    double oz = dz - pz;
                    double along = ox * fx + oz * fz;          // out from the crater: in front of it
                    double across = Math.abs(oz * fx - ox * fz);
                    if (across <= PERCH_LEDGE_HALF_WIDTH && along >= -PERCH_LEDGE_BACK && along <= PERCH_LEDGE_FRONT)
                    {
                        this.kind[i] = PERCH;
                        this.top[i] = this.perchLevel;
                        this.lava[i] = NO_LAVA;
                    }
                    else if (across <= PERCH_BODY_HALF_WIDTH && along >= -PERCH_BODY_BACK && along <= PERCH_BODY_FRONT)
                    {
                        this.top[i] = Math.min(this.top[i], this.perchLevel);
                    }
                    else if (across <= PERCH_HEAD_HALF_WIDTH && along > PERCH_BODY_FRONT && along <= PERCH_HEAD_FRONT)
                    {
                        this.top[i] = Math.min(this.top[i], this.perchLevel + PERCH_HEAD_LOW);
                    }
                    else
                    {
                        continue;
                    }
                    this.perchClear[i] = true;
                }
            }
            for (int dx = -EXT; dx <= EXT; dx++)
            {
                for (int dz = -EXT; dz <= EXT; dz++)
                {
                    if (!this.perchClear[idx(dx, dz)])
                    {
                        continue;
                    }
                    for (int ox = -2; ox <= 2; ox++)
                    {
                        for (int oz = -2; oz <= 2; oz++)
                        {
                            if (inGrid(dx + ox, dz + oz))
                            {
                                this.perchNear[idx(dx + ox, dz + oz)] = true;
                            }
                        }
                    }
                }
            }
        }

        /** How high the air is cleared over a column (from its ground): over the perch, high enough for its body. */
        private int clearTo(int i)
        {
            return this.perchClear[i] ? Math.max(HEIGHT + CLEAR_ABOVE, this.perchLevel + PERCH_BODY_HEIGHT) : HEIGHT + CLEAR_ABOVE;
        }

        /** Can lava go in this column: on the volcano (not the fringe), clear of the path and the plan's edge? */
        private boolean lavaAllowed(int dx, int dz)
        {
            for (int ox = -2; ox <= 2; ox++)
            {
                for (int oz = -2; oz <= 2; oz++)
                {
                    if (!inGrid(dx + ox, dz + oz))
                    {
                        return false;
                    }
                    byte k = this.kind[idx(dx + ox, dz + oz)];
                    if (k == OUTSIDE || k == FRINGE)
                    {
                        return false;
                    }
                }
            }
            int i = idx(dx, dz);
            return !this.nearPath[i] && !this.perchNear[i] && this.kind[i] != NEST && this.kind[i] != PATH && this.kind[i] != PERCH;
        }

        private void setLava(int i, int level, int depth)
        {
            this.top[i] = level - depth;
            this.lava[i] = level;
        }

        /** A lava pool at each river's foot, and the field of cooled crust round it (lava showing through here and there). */
        private void planFootPools(Random random)
        {
            for (int k = 0; k < RIVERS; k++)
            {
                double angle = this.riverAngle[k];
                double rr = BASE_RADIUS + FOOT_POOL_OUT;
                double lobe = lobes(angle);
                double r = rr * lobe;
                double wiggle = RIVER_WIGGLE * Math.sin(rr / RIVER_WIGGLE_LENGTH + this.riverPhase[k]);
                double cx = Math.cos(angle) * r - Math.sin(angle) * wiggle;
                double cz = Math.sin(angle) * r + Math.cos(angle) * wiggle;
                double radius = FOOT_POOL_RADIUS + random.nextDouble() * FOOT_POOL_RADIUS_SPREAD;
                int reach = Mth.ceil(radius + CRUST_REACH) + 1;
                for (int dx = (int) cx - reach; dx <= (int) cx + reach; dx++)
                {
                    for (int dz = (int) cz - reach; dz <= (int) cz + reach; dz++)
                    {
                        if (!inGrid(dx, dz))
                        {
                            continue;
                        }
                        int i = idx(dx, dz);
                        double ox = dx - cx;
                        double oz = dz - cz;
                        double d = Math.sqrt(ox * ox + oz * oz);
                        double a = Math.atan2(oz, ox);
                        double wobble = 0.9D * (2.0D * noise(Math.cos(a) * 2.5D + k * 7.0D, Math.sin(a) * 2.5D, 23) - 1.0D);
                        double outward = Math.cos(angleBetween(a, angle));
                        if (d <= radius + wobble && lavaAllowed(dx, dz) && this.top[i] <= 1)
                        {
                            setLava(i, 0, FOOT_POOL_DEPTH);
                        }
                        else if (d <= radius + wobble + CRUST_REACH * (0.55D + 0.45D * outward)
                                + 1.5D * (2.0D * noise(dx / 2.5D, dz / 2.5D, 29) - 1.0D)
                                && (this.kind[i] == APRON || this.kind[i] == CONE) && this.top[i] <= 1
                                && this.lava[i] == NO_LAVA && !this.nearPath[i])
                        {
                            this.kind[i] = CRUST;
                            this.top[i] = 0;
                            if (hash(dx, dz, 31) < CRUST_HOLES && lavaAllowed(dx, dz))
                            {
                                setLava(i, 0, 1);
                            }
                        }
                    }
                }
            }
        }

        /**
         * The rivers: from just under the rim down each gully to its foot pool, a cascade of lava pools, each sunk to
         * the lowest ground under it (cut into the slope behind it), a rock lip between one pool and the next.
         */
        private void planRivers()
        {
            double start = RIM_RADIUS + RIVER_SOURCE_OUT;
            double stride = POOL_LENGTH + POOL_GAP;
            for (int k = 0; k < RIVERS; k++)
            {
                Map<Integer, List<Integer>> pools = new HashMap<>();
                for (int dx = -EXT; dx <= EXT; dx++)
                {
                    for (int dz = -EXT; dz <= EXT; dz++)
                    {
                        int i = idx(dx, dz);
                        if (this.kind[i] != CONE || this.lava[i] != NO_LAVA || !lavaAllowed(dx, dz))
                        {
                            continue;
                        }
                        double angle = Math.atan2(dz, dx);
                        if (Math.abs(angleBetween(angle, this.riverAngle[k])) > 0.6D)
                        {
                            continue;
                        }
                        double rr = Math.sqrt(dx * dx + dz * dz) / lobes(angle);
                        if (rr < start || rr >= BASE_RADIUS)
                        {
                            continue;
                        }
                        int pool = (int) Math.floor((rr - start) / stride);
                        double u = (rr - start - pool * stride) / POOL_LENGTH;
                        if (u > 1.0D)
                        {
                            continue;
                        }
                        double share = (rr - RIM_RADIUS) / (BASE_RADIUS - RIM_RADIUS);
                        double half = POOL_HALF_WIDTH_TOP + (POOL_HALF_WIDTH_FOOT - POOL_HALF_WIDTH_TOP) * share;
                        double round = Math.sqrt(Math.max(0.0D, 1.0D - 0.55D * (2.0D * u - 1.0D) * (2.0D * u - 1.0D)));
                        if (Math.abs(riverOffset(k, angle, rr)) <= half * round)
                        {
                            pools.computeIfAbsent(pool, p -> new ArrayList<>()).add(i);
                        }
                    }
                }
                for (List<Integer> pool : pools.values())
                {
                    int level = Integer.MAX_VALUE;
                    for (int i : pool)
                    {
                        level = Math.min(level, this.top[i]);
                    }
                    if (level < 1)
                    {
                        continue;
                    }
                    for (int i : pool)
                    {
                        if (this.top[i] <= level + POOL_MAX_CUT)
                        {
                            setLava(i, level, 1);
                        }
                    }
                }
            }
        }

        /** Lava pockets sunk into the flanks between the rivers. */
        private void planPockets(Random random)
        {
            int placed = 0;
            for (int tries = 0; tries < POCKETS * 30 && placed < POCKETS; tries++)
            {
                double angle = random.nextDouble() * 2.0D * Math.PI;
                boolean byRiver = false;
                for (int k = 0; k < RIVERS; k++)
                {
                    byRiver |= Math.abs(angleBetween(angle, this.riverAngle[k])) < 0.28D;
                }
                if (byRiver)
                {
                    continue;
                }
                double t = 0.12D + random.nextDouble() * 0.63D;
                double rr = BASE_RADIUS - t * (BASE_RADIUS - RIM_RADIUS);
                double r = rr * lobes(angle);
                double cx = Math.cos(angle) * r;
                double cz = Math.sin(angle) * r;
                double radius = POCKET_RADIUS_MIN + random.nextDouble() * (POCKET_RADIUS_MAX - POCKET_RADIUS_MIN);
                List<Integer> cells = new ArrayList<>();
                int reach = Mth.ceil(radius);
                boolean ok = true;
                for (int dx = (int) Math.floor(cx) - reach; dx <= (int) Math.floor(cx) + reach && ok; dx++)
                {
                    for (int dz = (int) Math.floor(cz) - reach; dz <= (int) Math.floor(cz) + reach; dz++)
                    {
                        if (Math.hypot(dx - cx, dz - cz) > radius)
                        {
                            continue;
                        }
                        if (!inGrid(dx, dz) || this.kind[idx(dx, dz)] != CONE || this.lava[idx(dx, dz)] != NO_LAVA
                                || !lavaAllowed(dx, dz))
                        {
                            ok = false;
                            break;
                        }
                        cells.add(idx(dx, dz));
                    }
                }
                if (!ok || cells.isEmpty())
                {
                    continue;
                }
                int level = Integer.MAX_VALUE;
                for (int i : cells)
                {
                    level = Math.min(level, this.top[i]);
                }
                for (int i : cells)
                {
                    if (this.top[i] <= level + POOL_MAX_CUT)
                    {
                        setLava(i, level, 1);
                    }
                }
                placed++;
            }
        }

        /**
         * Walls the lava in: every lava column's four neighbours must reach its lava's top, solid or lava all the way.
         * A neighbour that falls short is raised with rock to it (a lip); a neighbour holding lower lava gives up its
         * lava and becomes the lip (so a lower pool's first row is the rock between it and the pool above). Raising
         * rock never leaves other lava open, so one pass over the lava is enough.
         */
        private void contain()
        {
            for (int dx = -EXT; dx <= EXT; dx++)
            {
                for (int dz = -EXT; dz <= EXT; dz++)
                {
                    int i = idx(dx, dz);
                    if (this.lava[i] == NO_LAVA)
                    {
                        continue;
                    }
                    int level = this.lava[i];
                    for (Direction side : Direction.Plane.HORIZONTAL)
                    {
                        int nx = dx + side.getStepX();
                        int nz = dz + side.getStepZ();
                        int n = idx(nx, nz);
                        int reaches = this.lava[n] != NO_LAVA ? this.lava[n] : this.top[n];
                        if (reaches >= level)
                        {
                            continue;
                        }
                        this.lava[n] = NO_LAVA;
                        this.top[n] = level;
                        if (this.kind[n] != PATH && this.kind[n] != NEST)
                        {
                            this.kind[n] = LIP;
                        }
                    }
                }
            }
        }

        /** Counts the lava, and how near each column is to it (square distance, up to 3). */
        private void measureLava()
        {
            Arrays.fill(this.lavaNear, (byte) 4);
            ArrayDeque<int[]> queue = new ArrayDeque<>();
            this.lavaBlocks = 0;
            for (int dx = -EXT; dx <= EXT; dx++)
            {
                for (int dz = -EXT; dz <= EXT; dz++)
                {
                    int i = idx(dx, dz);
                    if (this.lava[i] != NO_LAVA)
                    {
                        this.lavaBlocks += this.lava[i] - this.top[i];
                        this.lavaNear[i] = 0;
                        queue.add(new int[] {dx, dz});
                    }
                }
            }
            while (!queue.isEmpty())
            {
                int[] at = queue.poll();
                int d = this.lavaNear[idx(at[0], at[1])];
                if (d >= 3)
                {
                    continue;
                }
                for (int ox = -1; ox <= 1; ox++)
                {
                    for (int oz = -1; oz <= 1; oz++)
                    {
                        int nx = at[0] + ox;
                        int nz = at[1] + oz;
                        if (inGrid(nx, nz) && this.lavaNear[idx(nx, nz)] > d + 1)
                        {
                            this.lavaNear[idx(nx, nz)] = (byte) (d + 1);
                            queue.add(new int[] {nx, nz});
                        }
                    }
                }
            }
        }

        // ---- What lies on it ----

        /** A column something can stand on: on the volcano, dry, not the path, nothing put there yet. */
        private boolean free(int dx, int dz, int nearLava)
        {
            if (!inGrid(dx, dz))
            {
                return false;
            }
            int i = idx(dx, dz);
            byte k = this.kind[i];
            return (k == CONE || k == APRON || k == CRATER || k == LIP || k == CRUST) && this.lava[i] == NO_LAVA
                    && this.lavaNear[i] >= nearLava && !this.nearPath[i] && !this.perchClear[i]
                    && !this.decor.containsKey(key(dx, this.top[i] + 1, dz));
        }

        private void put(int dx, int y, int dz, BlockState state)
        {
            this.decor.put(key(dx, y, dz), state);
        }

        private long key(int dx, int y, int dz)
        {
            return BlockPos.asLong(this.ground.getX() + dx, this.ground.getY() + y, this.ground.getZ() + dz);
        }

        /** A random column on the cone between these shares of the way up. */
        private int[] onCone(Random random, double tMin, double tMax)
        {
            double angle = random.nextDouble() * 2.0D * Math.PI;
            double t = tMin + random.nextDouble() * (tMax - tMin);
            double r = (BASE_RADIUS - t * (BASE_RADIUS - RIM_RADIUS)) * lobes(angle);
            return new int[] {(int) Math.floor(Math.cos(angle) * r + 0.5D), (int) Math.floor(Math.sin(angle) * r + 0.5D)};
        }

        private void planDecor(Random random)
        {
            BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
            BlockState basalt = Blocks.BASALT.defaultBlockState();
            // the nest: a ring of bones and charred logs on the pillar's top, ash in its bowl, magma round the egg
            int reach = Mth.ceil(NEST_RING_OUTER);
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r < NEST_RING_INNER || r > NEST_RING_OUTER)
                    {
                        continue;
                    }
                    double roll = hash(dx, dz, 41);
                    Direction.Axis along = Math.abs(dx) > Math.abs(dz) ? Direction.Axis.Z : Direction.Axis.X;
                    BlockState log = ModBlocks.CHARRED_LOG.get().defaultBlockState().setValue(RotatedPillarBlock.AXIS, along);
                    if (hash(dx, dz, 71) < NEST_GOLD)
                    {
                        put(dx, this.nestTop + 1, dz, hash(dx, dz, 83) < 0.5D ? Blocks.GOLD_BLOCK.defaultBlockState()
                                : Blocks.RAW_GOLD_BLOCK.defaultBlockState());
                        continue;
                    }
                    if (roll < 0.55D)
                    {
                        put(dx, this.nestTop + 1, dz, bone.setValue(RotatedPillarBlock.AXIS, along));
                    }
                    else if (roll < 0.9D)
                    {
                        put(dx, this.nestTop + 1, dz, log);
                    }
                    if (hash(dx, dz, 43) < 0.35D && roll < 0.9D)
                    {
                        put(dx, this.nestTop + 2, dz, hash(dx, dz, 47) < 0.5D ? log : bone.setValue(RotatedPillarBlock.AXIS, along));
                    }
                }
            }
            // basalt spires on the crest (not by the lookout), topped with blackstone or obsidian
            for (int s = 0; s < RIM_SPIRES; s++)
            {
                double angle = 2.0D * Math.PI * s / RIM_SPIRES + random.nextDouble() * 0.35D;
                if (Math.abs(angleBetween(angle, this.notchAngle)) < 0.45D)
                {
                    continue;
                }
                double r = (RIM_RADIUS + random.nextDouble() * 1.4D - 0.7D) * lobes(angle);
                int dx = (int) Math.floor(Math.cos(angle) * r + 0.5D);
                int dz = (int) Math.floor(Math.sin(angle) * r + 0.5D);
                if (!free(dx, dz, 2))
                {
                    continue;
                }
                int height = SPIRE_MIN + random.nextInt(SPIRE_MAX - SPIRE_MIN + 1);
                int base = this.top[idx(dx, dz)];
                for (int y = 1; y <= height; y++)
                {
                    put(dx, base + y, dz, y < height ? basalt
                            : random.nextBoolean() ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState());
                }
            }
            // obsidian shards on the upper flanks
            for (int s = 0, tries = 0; s < OBSIDIAN_SHARDS && tries < 400; tries++)
            {
                int[] at = onCone(random, 0.55D, 0.9D);
                if (!free(at[0], at[1], 2))
                {
                    continue;
                }
                int base = this.top[idx(at[0], at[1])];
                int height = 2 + random.nextInt(3);
                for (int y = 1; y <= height; y++)
                {
                    put(at[0], base + y, at[1], Blocks.OBSIDIAN.defaultBlockState());
                }
                s++;
            }
            // cinder vents: on the upper flanks, in the crater's wall, and where each river seeps out
            BlockState vent = ModBlocks.CINDER_VENT.get().defaultBlockState();
            for (int s = 0, tries = 0; s < VENTS && tries < 600; tries++)
            {
                int[] at = s < VENTS * 2 / 3 ? onCone(random, 0.5D, 0.95D) : inCrater(random);
                if (at == null || !free(at[0], at[1], 1) || this.decor.containsKey(key(at[0], this.top[idx(at[0], at[1])], at[1])))
                {
                    continue;
                }
                put(at[0], this.top[idx(at[0], at[1])], at[1], vent);
                s++;
            }
            for (int k = 0; k < RIVERS; k++)
            {
                double rr = RIM_RADIUS + RIVER_SOURCE_OUT - 1.0D;
                double r = rr * lobes(this.riverAngle[k]);
                double wiggle = RIVER_WIGGLE * Math.sin(rr / RIVER_WIGGLE_LENGTH + this.riverPhase[k]);
                double a = this.riverAngle[k];
                int dx = (int) Math.floor(Math.cos(a) * r - Math.sin(a) * wiggle + 0.5D);
                int dz = (int) Math.floor(Math.sin(a) * r + Math.cos(a) * wiggle + 0.5D);
                if (inGrid(dx, dz) && this.lava[idx(dx, dz)] == NO_LAVA && !this.nearPath[idx(dx, dz)] && !this.perchClear[idx(dx, dz)])
                {
                    put(dx, this.top[idx(dx, dz)], dz, vent);
                }
            }
            // signal fires in the crest: their smoke rises out of the crater
            for (int s = 0; s < SMOKE_PLUMES; s++)
            {
                double angle = this.notchAngle + Math.PI * 0.5D + 2.0D * Math.PI * s / SMOKE_PLUMES * 0.75D;
                double r = (RIM_RADIUS - 1.2D) * lobes(angle);
                int dx = (int) Math.floor(Math.cos(angle) * r + 0.5D);
                int dz = (int) Math.floor(Math.sin(angle) * r + 0.5D);
                if (!free(dx, dz, 3))
                {
                    continue;
                }
                int y = this.top[idx(dx, dz)];
                put(dx, y, dz, Blocks.HAY_BLOCK.defaultBlockState());
                put(dx, y + 1, dz, Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true)
                        .setValue(CampfireBlock.SIGNAL_FIRE, true));
            }
            // scorched bones: piles of bone blocks and skulls on the lower flanks and the ground round it
            for (int s = 0, tries = 0; s < BONE_PILES && tries < 900; tries++)
            {
                int[] at = random.nextInt(3) == 0 ? onApron(random) : onCone(random, 0.0D, 0.6D);
                if (!free(at[0], at[1], 1))
                {
                    continue;
                }
                int base = this.top[idx(at[0], at[1])];
                Direction.Axis axis = Direction.Axis.values()[random.nextInt(3)];
                put(at[0], base + 1, at[1], bone.setValue(RotatedPillarBlock.AXIS, axis));
                if (random.nextInt(3) == 0)
                {
                    put(at[0], base + 2, at[1], bone.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                }
                Direction side = Direction.from2DDataValue(random.nextInt(4));
                int sx = at[0] + side.getStepX();
                int sz = at[1] + side.getStepZ();
                if (random.nextBoolean() && free(sx, sz, 1) && this.top[idx(sx, sz)] == base)
                {
                    put(sx, base + 1, sz, bone.setValue(RotatedPillarBlock.AXIS, side.getAxis()));
                }
                s++;
            }
            for (int s = 0, tries = 0; s < SKULLS && tries < 900; tries++)
            {
                int[] at = random.nextInt(3) == 0 ? onApron(random) : onCone(random, 0.0D, 0.75D);
                if (!free(at[0], at[1], 2))
                {
                    continue;
                }
                BlockState skull = (random.nextInt(10) < 7 ? Blocks.SKELETON_SKULL : Blocks.WITHER_SKELETON_SKULL).defaultBlockState()
                        .setValue(SkullBlock.ROTATION, random.nextInt(16));
                put(at[0], this.top[idx(at[0], at[1])] + 1, at[1], skull);
                s++;
            }
            for (int s = 0, tries = 0; s < RIBCAGES && tries < 400; tries++)
            {
                double angle = random.nextDouble() * 2.0D * Math.PI;
                boolean clear = Math.abs(angleBetween(angle, this.front)) > 0.4D;
                for (int k = 0; k < RIVERS; k++)
                {
                    clear &= Math.abs(angleBetween(angle, this.riverAngle[k])) > 0.32D;
                }
                if (clear && ribcage(angle, random))
                {
                    s++;
                }
            }
            // lanterns on bone posts beside the path
            double next = PATH_LANTERN_EVERY;
            for (PathPoint p : this.path)
            {
                if (p.along() < next || p == this.lookout)
                {
                    continue;
                }
                double radial = Math.max(1.0D, Math.hypot(p.x(), p.z()));
                for (int side : new int[] {1, -1})
                {
                    double out = PATH_HALF_WIDTH + 1.2D;
                    int dx = (int) Math.floor(p.x() + p.x() / radial * out * side + 0.5D);
                    int dz = (int) Math.floor(p.z() + p.z() / radial * out * side + 0.5D);
                    if (!inGrid(dx, dz))
                    {
                        continue;
                    }
                    int i = idx(dx, dz);
                    if (this.kind[i] == PATH || this.lava[i] != NO_LAVA || this.lavaNear[i] < 2
                            || Math.abs(this.top[i] - p.height()) > 1 || this.decor.containsKey(key(dx, this.top[i] + 1, dz)))
                    {
                        continue;
                    }
                    put(dx, this.top[i] + 1, dz, bone.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y));
                    put(dx, this.top[i] + 2, dz, Blocks.LANTERN.defaultBlockState());
                    next = p.along() + PATH_LANTERN_EVERY;
                    break;
                }
            }
            hoard(random);
            // drifts of ash on the upper flanks
            BlockState ash = ModBlocks.ASH_LAYER.get().defaultBlockState();
            for (int dx = -EXT; dx <= EXT; dx++)
            {
                for (int dz = -EXT; dz <= EXT; dz++)
                {
                    int i = idx(dx, dz);
                    if (this.kind[i] != CONE || this.slope[i] < 0.45F || hash(dx, dz, 53) >= ASH_DRIFTS || !free(dx, dz, 2)
                            || this.decor.containsKey(key(dx, this.top[i], dz)))
                    {
                        continue;
                    }
                    put(dx, this.top[i] + 1, dz, ash.setValue(SnowLayerBlock.LAYERS, 1 + (hash(dx, dz, 59) < 0.4D ? 1 : 0)));
                }
            }
        }

        /**
         * The hoard at the lookout (the user: "add chests or gold at the top or ores and stuff"): its chests either side
         * of it, facing its middle, empty for now (the user, October 27: "remove all the loot from the chests, I will
         * decide what to put in them later"), and gold heaped round it on the rock beside the path (never on the path,
         * never by lava).
         */
        private void hoard(Random random)
        {
            PathPoint p = this.lookout;
            double radial = Math.max(1.0D, Math.hypot(p.x(), p.z()));
            double tx = -p.z() / radial;
            double tz = p.x() / radial;
            for (int c = 0; c < HOARD_CHESTS; c++)
            {
                double side = (c % 2 == 0 ? 1.0D : -1.0D) * (LOOKOUT_HALF_WIDTH - 0.6D);
                int dx = (int) Math.floor(p.x() + tx * side + 0.5D);
                int dz = (int) Math.floor(p.z() + tz * side + 0.5D);
                if (!inGrid(dx, dz) || this.kind[idx(dx, dz)] != PATH)
                {
                    continue;
                }
                Direction facing = Direction.getNearest(-tx * Math.signum(side), 0.0D, -tz * Math.signum(side));
                put(dx, this.top[idx(dx, dz)] + 1, dz, Blocks.CHEST.defaultBlockState().setValue(ChestBlock.FACING, facing));
            }
            int reach = Mth.ceil(HOARD_REACH);
            int cx = (int) Math.floor(p.x() + 0.5D);
            int cz = (int) Math.floor(p.z() + 0.5D);
            List<int[]> spots = new ArrayList<>();
            for (int dx = cx - reach; dx <= cx + reach; dx++)
            {
                for (int dz = cz - reach; dz <= cz + reach; dz++)
                {
                    if (!inGrid(dx, dz) || Math.hypot(dx - p.x(), dz - p.z()) > HOARD_REACH)
                    {
                        continue;
                    }
                    int i = idx(dx, dz);
                    byte k = this.kind[i];
                    if (k == PATH || k == NEST || k == OUTSIDE || k == FRINGE || this.lava[i] != NO_LAVA || this.lavaNear[i] < 2
                            || Math.abs(this.top[i] - p.height()) > 2 || this.decor.containsKey(key(dx, this.top[i] + 1, dz)))
                    {
                        continue;
                    }
                    spots.add(new int[] {dx, dz});
                }
            }
            Collections.shuffle(spots, random);
            BlockState[] gold = {Blocks.GOLD_BLOCK.defaultBlockState(), Blocks.RAW_GOLD_BLOCK.defaultBlockState(),
                    Blocks.GILDED_BLACKSTONE.defaultBlockState(), Blocks.GOLD_BLOCK.defaultBlockState()};
            for (int s = 0; s < Math.min(HOARD_GOLD, spots.size()); s++)
            {
                int dx = spots.get(s)[0];
                int dz = spots.get(s)[1];
                int y = this.top[idx(dx, dz)] + 1;
                put(dx, y, dz, gold[random.nextInt(gold.length)]);
                if (random.nextInt(3) == 0)
                {
                    put(dx, y + 1, dz, gold[random.nextInt(gold.length)]);
                }
            }
        }

        /** A random column in the crater's inner wall. */
        private int[] inCrater(Random random)
        {
            double angle = random.nextDouble() * 2.0D * Math.PI;
            double r = (LAKE_RADIUS + 1.8D + random.nextDouble() * (RIM_RADIUS - LAKE_RADIUS - 2.6D)) * lobes(angle);
            return new int[] {(int) Math.floor(Math.cos(angle) * r + 0.5D), (int) Math.floor(Math.sin(angle) * r + 0.5D)};
        }

        /** A random column on the ground round the cone. */
        private int[] onApron(Random random)
        {
            double angle = random.nextDouble() * 2.0D * Math.PI;
            double r = (BASE_RADIUS + random.nextDouble() * (APRON_OUT - 2.0D)) * lobes(angle);
            return new int[] {(int) Math.floor(Math.cos(angle) * r + 0.5D), (int) Math.floor(Math.sin(angle) * r + 0.5D)};
        }

        /**
         * A great ribcage half buried at the cone's foot, along it: a spine of bone blocks and ribs arching up either
         * side. Only into the air (never into the ground, lava or the path).
         */
        private boolean ribcage(double angle, Random random)
        {
            double r = (BASE_RADIUS - 2.0D) * lobes(angle);
            double cx = Math.cos(angle) * r;
            double cz = Math.sin(angle) * r;
            double tx = -Math.sin(angle);
            double tz = Math.cos(angle);
            int spineLength = 11;
            for (int s = -spineLength / 2; s <= spineLength / 2; s++)
            {
                int dx = (int) Math.floor(cx + tx * s + 0.5D);
                int dz = (int) Math.floor(cz + tz * s + 0.5D);
                if (!free(dx, dz, 2))
                {
                    return false;
                }
            }
            BlockState bone = Blocks.BONE_BLOCK.defaultBlockState();
            Direction.Axis spineAxis = Math.abs(tx) > Math.abs(tz) ? Direction.Axis.X : Direction.Axis.Z;
            int baseY = this.top[idx((int) Math.floor(cx + 0.5D), (int) Math.floor(cz + 0.5D))];
            for (int s = -spineLength / 2; s <= spineLength / 2; s++)
            {
                double px = cx + tx * s;
                double pz = cz + tz * s;
                placeBone((int) Math.floor(px + 0.5D), baseY + 1, (int) Math.floor(pz + 0.5D), bone.setValue(RotatedPillarBlock.AXIS, spineAxis));
                if (s % 2 != 0 || Math.abs(s) > spineLength / 2 - 1)
                {
                    continue;
                }
                double ribR = 3.6D - Math.abs(s) * 0.25D + random.nextDouble() * 0.4D;
                for (int sideSign : new int[] {1, -1})
                {
                    for (double phi = 0.25D; phi <= 2.5D; phi += 0.22D)
                    {
                        double lateral = Math.sin(phi) * ribR * sideSign;
                        double up = (1.0D - Math.cos(phi)) * ribR * 0.9D;
                        double ox = px + Math.cos(angle) * lateral;
                        double oz = pz + Math.sin(angle) * lateral;
                        placeBone((int) Math.floor(ox + 0.5D), baseY + 1 + (int) Math.round(up), (int) Math.floor(oz + 0.5D),
                                bone.setValue(RotatedPillarBlock.AXIS, phi < 0.9D || phi > 1.9D ? Direction.Axis.Y
                                        : Math.abs(Math.cos(angle)) > Math.abs(Math.sin(angle)) ? Direction.Axis.X : Direction.Axis.Z));
                    }
                }
            }
            return true;
        }

        private void placeBone(int dx, int y, int dz, BlockState state)
        {
            if (!inGrid(dx, dz))
            {
                return;
            }
            int i = idx(dx, dz);
            byte k = this.kind[i];
            if (y <= this.top[i] || this.lava[i] != NO_LAVA || k == PATH || k == OUTSIDE || k == FRINGE || this.nearPath[i]
                    || this.perchClear[i])
            {
                return;
            }
            put(dx, y, dz, state);
        }

        // ---- The blocks ----

        /** The block at {@code depth} under this column's top (0 is the top block). */
        private BlockState solid(int dx, int dz, int depth, int y)
        {
            int i = idx(dx, dz);
            byte k = this.kind[i];
            double roll = hash(dx * 31 + y, dz, 61);
            if (depth == 0)
            {
                return surface(dx, dz, i, k);
            }
            if (k == LIP && depth < 4 || this.lavaNear[i] <= 1 && depth < 2)
            {
                return roll < 0.45D ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : roll < 0.75D ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            }
            if (k == CRATER || k == NEST)
            {
                return roll < 0.5D ? Blocks.BASALT.defaultBlockState()
                        : roll < 0.9D ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            if (depth <= 2)
            {
                return roll < 0.4D ? Blocks.BASALT.defaultBlockState() : roll < 0.85D ? Blocks.BLACKSTONE.defaultBlockState()
                        : roll < 0.93D ? Blocks.SMOOTH_BASALT.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
            }
            return roll < 0.72D ? Blocks.BLACKSTONE.defaultBlockState()
                    : roll < 0.94D ? Blocks.BASALT.defaultBlockState() : Blocks.MAGMA_BLOCK.defaultBlockState();
        }

        /** A column's top block. */
        private BlockState surface(int dx, int dz, int i, byte k)
        {
            double roll = hash(dx, dz, 67);
            if (k == PATH)
            {
                if (this.pathLight[i])
                {
                    return Blocks.SHROOMLIGHT.defaultBlockState();
                }
                if (this.pathEdge[i])
                {
                    return Blocks.BONE_BLOCK.defaultBlockState();
                }
                return roll < 0.8D ? Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : roll < 0.95D ? Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS.defaultBlockState()
                        : Blocks.CHISELED_POLISHED_BLACKSTONE.defaultBlockState();
            }
            if (k == NEST)
            {
                double r = Math.sqrt(dx * dx + dz * dz);
                if (r <= NEST_ASH_RADIUS)
                {
                    return ModBlocks.ASH.get().defaultBlockState();
                }
                return r <= NEST_RING_INNER ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : roll < 0.6D ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            }
            if (k == PERCH)
            {
                // its ledge: worn smooth rock, scorched dark
                return roll < 0.4D ? Blocks.SMOOTH_BASALT.defaultBlockState() : roll < 0.7D ? Blocks.BASALT.defaultBlockState()
                        : roll < 0.9D ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.POLISHED_BLACKSTONE.defaultBlockState();
            }
            if (k == LIP || k == CRUST || this.lavaNear[i] == 1)
            {
                return roll < 0.52D ? Blocks.MAGMA_BLOCK.defaultBlockState()
                        : roll < 0.82D ? Blocks.OBSIDIAN.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            }
            if (this.lavaNear[i] == 2)
            {
                return roll < 0.2D ? Blocks.MAGMA_BLOCK.defaultBlockState() : roll < 0.35D ? Blocks.OBSIDIAN.defaultBlockState()
                        : roll < 0.7D ? Blocks.BASALT.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            }
            if (k == CRATER && hash(dx, dz, 73) < CRATER_ORES)
            {
                double ore = hash(dx, dz, 79);
                return ore < 0.45D ? ModBlocks.DUSKSTONE_GOLD_ORE.get().defaultBlockState()
                        : ore < 0.75D ? ModBlocks.MOONSILVER_ORE.get().defaultBlockState()
                        : ore < 0.9D ? ModBlocks.STORMITE_ORE.get().defaultBlockState() : Blocks.GILDED_BLACKSTONE.defaultBlockState();
            }
            if (k == CRATER)
            {
                return roll < 0.4D ? Blocks.BASALT.defaultBlockState() : roll < 0.75D ? Blocks.BLACKSTONE.defaultBlockState()
                        : roll < 0.9D ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.OBSIDIAN.defaultBlockState();
            }
            double t = this.slope[i];
            double angle = Math.atan2(dz, dx);
            double streak = Math.sin(13.0D * angle + 3.0D * Math.sin(Math.hypot(dx, dz) / 9.0D));
            if (k == CONE && streak > 0.86D)
            {
                return roll < 0.45D ? Blocks.OBSIDIAN.defaultBlockState()
                        : roll < 0.7D ? Blocks.MAGMA_BLOCK.defaultBlockState() : Blocks.BLACKSTONE.defaultBlockState();
            }
            if (t > 0.93D)
            {
                return roll < 0.45D ? Blocks.BASALT.defaultBlockState() : roll < 0.8D ? Blocks.BLACKSTONE.defaultBlockState()
                        : roll < 0.88D ? Blocks.OBSIDIAN.defaultBlockState() : ModBlocks.ASH.get().defaultBlockState();
            }
            if (t > 0.55D)
            {
                return roll < 0.45D ? ModBlocks.ASH.get().defaultBlockState() : roll < 0.73D ? Blocks.BASALT.defaultBlockState()
                        : roll < 0.9D ? Blocks.BLACKSTONE.defaultBlockState() : Blocks.SMOOTH_BASALT.defaultBlockState();
            }
            return roll < 0.38D ? ModBlocks.ASH.get().defaultBlockState() : roll < 0.68D ? Blocks.BLACKSTONE.defaultBlockState()
                    : roll < 0.86D ? Blocks.BASALT.defaultBlockState() : roll < 0.94D ? Blocks.SMOOTH_BASALT.defaultBlockState()
                    : Blocks.MAGMA_BLOCK.defaultBlockState();
        }

        // ---- Small helpers ----

        private static boolean inGrid(int dx, int dz)
        {
            return dx >= -EXT && dx <= EXT && dz >= -EXT && dz <= EXT;
        }

        private static int idx(int dx, int dz)
        {
            return (dx + EXT) * N + (dz + EXT);
        }

        private double hash(int x, int z, int salt)
        {
            long h = this.seed ^ x * 0x9E3779B97F4A7C15L ^ z * 0xC2B2AE3D27D4EB4FL ^ salt * 0x165667B19E3779F9L;
            h = (h ^ h >>> 33) * 0xFF51AFD7ED558CCDL;
            h = (h ^ h >>> 33) * 0xC4CEB9FE1A85EC53L;
            h ^= h >>> 33;
            return (h >>> 11) * 0x1.0p-53;
        }

        /** Smooth value noise, 0 to 1. */
        private double noise(double x, double z, int salt)
        {
            int x0 = (int) Math.floor(x);
            int z0 = (int) Math.floor(z);
            double fx = x - x0;
            double fz = z - z0;
            double ux = fx * fx * (3.0D - 2.0D * fx);
            double uz = fz * fz * (3.0D - 2.0D * fz);
            double a = hash(x0, z0, salt);
            double b = hash(x0 + 1, z0, salt);
            double c = hash(x0, z0 + 1, salt);
            double d = hash(x0 + 1, z0 + 1, salt);
            return Mth.lerp(uz, Mth.lerp(ux, a, b), Mth.lerp(ux, c, d));
        }
    }

    /** The signed angle from {@code b} to {@code a}, -pi to pi. */
    private static double angleBetween(double a, double b)
    {
        return Mth.wrapDegrees(Math.toDegrees(a - b)) * Math.PI / 180.0D;
    }

    /** A Minecraft yaw for facing along (x, z). */
    private static float yaw(double x, double z)
    {
        return (float) Math.toDegrees(Math.atan2(-x, z));
    }

    /**
     * Building a planned volcano a piece at a time: clearing the air over every column, then laying each column's
     * ground, then the things that lie on it, then all the lava at once.
     */
    public static final class Build
    {
        private enum Phase { CLEAR, GROUND, DECOR, LAVA, DONE }

        private final Plan plan;
        private final ServerLevel level;
        private final List<Map.Entry<Long, BlockState>> decor;
        private final List<ChunkPos> chunks = new ArrayList<>();
        private Phase phase = Phase.CLEAR;
        private int cursor;
        private int ticks;
        private long busyNanos;
        private long longestNanos;
        private int placed;

        private Build(Plan plan, ServerLevel level)
        {
            this.plan = plan;
            this.level = level;
            this.decor = new ArrayList<>(plan.decor.entrySet());
            this.decor.sort(Comparator.comparingInt(e -> BlockPos.getY(e.getKey())));
            BoundingBox box = plan.box();
            for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
            {
                for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                {
                    ChunkPos chunk = new ChunkPos(cx, cz);
                    this.chunks.add(chunk);
                    level.getChunkSource().addRegionTicket(TICKET, chunk, 0, chunk);
                }
            }
        }

        /** The volcano being built. */
        public Plan plan()
        {
            return this.plan;
        }

        public boolean isDone()
        {
            return this.phase == Phase.DONE;
        }

        /** How long it has worked so far (milliseconds, its own steps only). */
        public long workMillis()
        {
            return this.busyNanos / 1_000_000L;
        }

        /**
         * Does up to {@code budgetNanos} of work (the lava always all at once, on a tick of its own).
         *
         * @return true once it's all built
         */
        public boolean step(long budgetNanos)
        {
            if (this.phase == Phase.DONE)
            {
                return true;
            }
            long start = System.nanoTime();
            long end = budgetNanos == Long.MAX_VALUE ? Long.MAX_VALUE : start + budgetNanos;
            this.ticks++;
            BlockPos ground = this.plan.ground;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
            BlockState air = Blocks.AIR.defaultBlockState();
            while (this.phase != Phase.DONE && System.nanoTime() < end)
            {
                if (this.phase == Phase.CLEAR || this.phase == Phase.GROUND)
                {
                    if (this.cursor >= N * N)
                    {
                        this.cursor = 0;
                        this.phase = this.phase == Phase.CLEAR ? Phase.GROUND : Phase.DECOR;
                        continue;
                    }
                    int i = this.cursor++;
                    int dx = i / N - EXT;
                    int dz = i % N - EXT;
                    byte kind = this.plan.kind[i];
                    if (kind == OUTSIDE)
                    {
                        continue;
                    }
                    int x = ground.getX() + dx;
                    int z = ground.getZ() + dz;
                    int top = this.plan.top[i];
                    int land = this.plan.land[i];
                    if (this.phase == Phase.CLEAR)
                    {
                        int from = kind == FRINGE ? land + 1 : top + 1;
                        for (int y = from, to = Math.max(this.plan.clearTo(i), land); y <= to; y++)
                        {
                            pos.set(x, ground.getY() + y, z);
                            if (!this.level.getBlockState(pos).isAir())
                            {
                                this.level.setBlock(pos, air, FLAGS);
                                this.placed++;
                            }
                        }
                    }
                    else if (kind == FRINGE)
                    {
                        double outside = Math.hypot(dx, dz) - BASE_RADIUS * this.plan.lobes(Math.atan2(dz, dx));
                        double cover = 1.0D - (outside - APRON_OUT) / (FRINGE_OUT - APRON_OUT);
                        // (any lava or water just under the land here is sealed: the ground beside it may have been lowered)
                        for (int y = land - FOUNDATION; y <= land; y++)
                        {
                            pos.set(x, ground.getY() + y, z);
                            if (!this.level.getFluidState(pos).isEmpty())
                            {
                                this.level.setBlock(pos, Blocks.BLACKSTONE.defaultBlockState(), FLAGS);
                                this.placed++;
                            }
                        }
                        if (this.plan.hash(dx, dz, 71) < cover * 0.85D)
                        {
                            pos.set(x, ground.getY() + land, z);
                            this.level.setBlock(pos, this.plan.hash(dx, dz, 73) < 0.75D ? ModBlocks.ASH.get().defaultBlockState()
                                    : Blocks.BLACKSTONE.defaultBlockState(), FLAGS);
                            this.placed++;
                        }
                    }
                    else
                    {
                        // (from under the land too: a natural lava pool dug into it is re-laid as rock)
                        for (int y = Math.min(-FOUNDATION, land - FOUNDATION); y <= top; y++)
                        {
                            pos.set(x, ground.getY() + y, z);
                            this.level.setBlock(pos, this.plan.solid(dx, dz, top - y, y), FLAGS);
                            this.placed++;
                        }
                    }
                }
                else if (this.phase == Phase.DECOR)
                {
                    if (this.cursor >= this.decor.size())
                    {
                        this.cursor = 0;
                        this.phase = Phase.LAVA;
                        // the lava gets a tick of its own
                        break;
                    }
                    Map.Entry<Long, BlockState> entry = this.decor.get(this.cursor++);
                    this.level.setBlock(pos.set(entry.getKey()), entry.getValue(), FLAGS);
                    this.placed++;
                }
                else if (this.phase == Phase.LAVA)
                {
                    BlockState lava = Blocks.LAVA.defaultBlockState();
                    for (int i = 0; i < N * N; i++)
                    {
                        if (this.plan.lava[i] == NO_LAVA)
                        {
                            continue;
                        }
                        int x = ground.getX() + i / N - EXT;
                        int z = ground.getZ() + i % N - EXT;
                        for (int y = this.plan.top[i] + 1; y <= this.plan.lava[i]; y++)
                        {
                            this.level.setBlock(pos.set(x, ground.getY() + y, z), lava, FLAGS);
                            this.placed++;
                        }
                    }
                    this.phase = Phase.DONE;
                    for (ChunkPos chunk : this.chunks)
                    {
                        this.level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
                    }
                }
            }
            long spent = System.nanoTime() - start;
            this.busyNanos += spent;
            this.longestNanos = Math.max(this.longestNanos, spent);
            if (this.phase == Phase.DONE)
            {
                LOGGER.info("[ember volcano] built at {}: {} blocks set ({} of them lava) in {} ms of work over {} ticks, the "
                                + "longest tick {} ms", this.plan.ground.toShortString(), this.placed, this.plan.lavaBlocks,
                        this.busyNanos / 1_000_000L, this.ticks, this.longestNanos / 1_000_000L);
                return true;
            }
            return false;
        }

        /** Stops building and lets its chunks go (a server stopping, a new showcase over it). */
        public void abandon()
        {
            if (this.phase != Phase.DONE)
            {
                this.phase = Phase.DONE;
                for (ChunkPos chunk : this.chunks)
                {
                    this.level.getChunkSource().removeRegionTicket(TICKET, chunk, 0, chunk);
                }
            }
        }
    }
}
