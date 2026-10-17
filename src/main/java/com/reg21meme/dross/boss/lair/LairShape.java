package com.reg21meme.dross.boss.lair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.function.IntBinaryOperator;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * The lair's shape, as plain maths (no world needed, safe on any thread), and the plans for a spot.
 * <p>
 * Seen from above, the lair is a round floor ({@link #FLOOR_EDGE}) inside a volcano, with the way in running out of it
 * north, east, south or west. Along the way in, "s" is the distance from the floor's middle and "l" the distance to its
 * side: a passage 5 wide runs out of the cavern, flares to the cave mouth ({@link #volcanoMouth}, 9 wide and 8 tall) in
 * the volcano's flank, and a gully cut into the flank leads out to the land.
 * <ul>
 *   <li>{@link #volcanoPlan}: the volcano's plan for a spot (the builder's): which way the mouth faces, the floor's
 *       height, the rim's height and the land under the cone ({@link #volcanoPlans}: one for each way the mouth could
 *       face).</li>
 *   <li>{@link #approach}: the way in outside the mouth: where the gully ends, the level apron, and the land in front
 *       shaped so it never steps more than a block, out to where it meets the land.</li>
 *   <li>{@link #plan}: the first (R2c) lair's plan, still used by the fit test ({@link LairFinder}) to score spots (it
 *       prefers high ground, so the cavern is carved into rock).</li>
 * </ul>
 */
final class LairShape
{
    /** The floor's edge (a column is floor if its middle is at most this far from the middle block's middle). */
    static final double FLOOR_EDGE = FLOOR_RADIUS + 0.5D;
    /** The outer edge of the rock lining round the walls. */
    static final double SHELL_EDGE = FLOOR_EDGE + SHELL_THICKNESS;

    /** The floor columns a ground survey samples (offsets from the middle). */
    private static final int[][] PLAN_SAMPLES = planSamples();

    private LairShape() {}

    // ---------------------------------------------------------------- the cavern

    /** How many blocks above the floor the ceiling is, {@code r} blocks from the middle (before the roughness). */
    static double ceiling(double r)
    {
        if (r >= FLOOR_EDGE)
        {
            return WALL_HEIGHT;
        }
        double t = r / FLOOR_EDGE;
        return WALL_HEIGHT + (DOME_HEIGHT - WALL_HEIGHT) * Math.sqrt(1.0D - t * t);
    }

    /** The top of the rock lining round the dome, in blocks above the floor, {@code r} blocks from the middle. */
    static double shellTop(double r)
    {
        double t = Math.min(r, SHELL_EDGE) / SHELL_EDGE;
        return WALL_HEIGHT + SHELL_THICKNESS + (DOME_HEIGHT - WALL_HEIGHT) * Math.sqrt(Math.max(0.0D, 1.0D - t * t));
    }

    /**
     * True if a block {@code dy} above the floor, {@code r} blocks from the middle, is inside the cavern (air), given
     * the roughness there ({@code rough}, 0 to {@link LairTuning#ROUGHNESS}). The first layer above the floor keeps
     * the floor's exact circle, so the floor's edge is clean.
     */
    static boolean inCavern(double r, int dy, double rough)
    {
        if (dy < 1)
        {
            return false;
        }
        double edge = dy == 1 ? FLOOR_EDGE : FLOOR_EDGE + rough;
        if (r > edge)
        {
            return false;
        }
        return dy <= ceiling(Math.min(r, FLOOR_EDGE)) + rough;
    }

    // ---------------------------------------------------------------- the scoring tunnel (the fit test's yardstick)

    /** The last block (s) of the scoring tunnel's level part. */
    static int straightEnd()
    {
        return FLOOR_RADIUS + TUNNEL_STRAIGHT;
    }

    /** The scoring tunnel's mouth (its last covered block, s), for an entrance that goes down {@code descent} blocks. */
    static int mouthDistance(int descent)
    {
        return straightEnd() + 2 * descent;
    }

    // ---------------------------------------------------------------- the volcano

    /** How much of the rim's height above the land the cone has {@code r} blocks from the middle: 1 inside the rim, 0 at its foot. */
    static double coneShare(double r)
    {
        if (r <= RIM_RADIUS)
        {
            return 1.0D;
        }
        double t = Math.min(1.0D, (r - RIM_RADIUS) / (CONE_BASE_RADIUS - RIM_RADIUS));
        return Math.pow(1.0D - t, CONE_PROFILE);
    }

    /**
     * The crater's radius {@code dy} blocks above the floor, for a rim {@code rimDy} above the floor: the throat
     * ({@link LairTuning#SKYLIGHT_RADIUS}) up to the top of the dome, then widening like a funnel to
     * {@link LairTuning#CRATER_RADIUS} at the rim (and on above it).
     */
    static double craterRadius(int dy, int rimDy)
    {
        if (dy <= DOME_HEIGHT)
        {
            return SKYLIGHT_RADIUS;
        }
        double u = Math.min(1.0D, (dy - DOME_HEIGHT) / (double) Math.max(1, rimDy - DOME_HEIGHT));
        return SKYLIGHT_RADIUS + (CRATER_RADIUS - SKYLIGHT_RADIUS) * Math.pow(u, CRATER_FLARE);
    }

    /** How high the stalactites' tips must stay above the floor, {@code r} blocks from the middle. */
    static int stalactiteClearance(double r)
    {
        return r <= LOB_CLEARANCE_RADIUS ? LOB_CLEARANCE : STALACTITE_CLEARANCE;
    }

    /** The way in's half-width at distance {@code s} (a block row's middle is at its whole number), mouth at {@code mouth}. */
    static double passageHalfWidth(double s, int mouth)
    {
        double from = mouth - MOUTH_FLARE;
        if (s <= from)
        {
            return PASSAGE_HALF_WIDTH + 0.5D;
        }
        double t = Math.min(1.0D, (s - from) / MOUTH_FLARE);
        return PASSAGE_HALF_WIDTH + 0.5D + (MOUTH_HALF_WIDTH - PASSAGE_HALF_WIDTH) * t;
    }

    /** The way in's height (air blocks over its floor in the middle, before the arch's top block) at distance {@code s}. */
    static double passageHeight(double s, int mouth)
    {
        double from = mouth - MOUTH_FLARE;
        if (s <= from)
        {
            return PASSAGE_HEIGHT;
        }
        double t = Math.min(1.0D, (s - from) / MOUTH_FLARE);
        return PASSAGE_HEIGHT + (MOUTH_HEIGHT - PASSAGE_HEIGHT) * t;
    }

    /**
     * True if the block at distance {@code s} along the way in, {@code l} to its side and {@code dy} above the floor is
     * open: the passage and the mouth are an arch (flat-sided, round-topped), the gully in front of the mouth is open
     * to the sky with walls leaning back. {@code rough} (0 to about 1) widens it a little, so its rock looks broken.
     */
    static boolean inEntrance(double s, double l, int dy, int mouth, double rough)
    {
        if (dy < 1 || s < FLOOR_RADIUS - 3)
        {
            return false;
        }
        double side = Math.abs(l);
        if (s > mouth)
        {
            return side <= MOUTH_HALF_WIDTH + 0.5D + (s - mouth) * GULLY_FLARE + (dy - 1) * GULLY_WALL_SLOPE + rough;
        }
        double a = side / (passageHalfWidth(s, mouth) + rough);
        double b = (dy - 0.5D) / (passageHeight(s, mouth) + 0.5D + rough);
        return a * a * a + b * b * b <= 1.0D;
    }

    /**
     * The volcano's plan for a lair whose floor's middle is at {@code x}, {@code z}.
     *
     * @param floorY   the Y of the floor blocks (the way in is level with the floor, at the land in front of the flank)
     * @param tunnel   the way the way in runs out of the cavern, towards its mouth
     * @param rimY     the rim's crest (Y)
     * @param baseY    the land under the cone, at the middle (Y): a plane tilted by {@code tiltX} and {@code tiltZ}
     *                 (blocks up per block east and south)
     * @param highLand the high land round the rim (Y)
     * @param spread   how uneven the land in front of the flank is (blocks)
     */
    record VolcanoPlan(int x, int z, int floorY, Direction tunnel, int rimY, double baseY, double tiltX, double tiltZ,
                       int highLand, int spread)
    {
        /** The land plane under the cone, at an offset from the middle. */
        double base(double dx, double dz)
        {
            return baseY + tiltX * dx + tiltZ * dz;
        }

        /** The cone's smooth surface (no lumps, ridges or teeth) at an offset from the middle. */
        double cone(double dx, double dz)
        {
            double g = coneShare(Math.sqrt(dx * dx + dz * dz));
            return base(dx, dz) * (1.0D - g) + rimY * g;
        }

        String describe()
        {
            return String.format("floor y %d, mouth to the %s, rim y %d (%d above the floor, %d above the high land), land "
                            + "under the cone y %.0f tilted %.2f, land in front of the flank %d blocks uneven",
                    floorY, tunnel.getName(), rimY, rimY - floorY, rimY - highLand, baseY, Math.hypot(tiltX, tiltZ), spread);
        }
    }

    /**
     * Works out the volcano's plan from the ground round {@code x}, {@code z}:
     * <ol>
     *   <li>The way in faces the direction where the land in front of the flank (between
     *       {@link LairTuning#APPROACH_SURVEY_FROM} and {@link LairTuning#APPROACH_SURVEY_TO} blocks out) is lowest and
     *       most even; the floor is level with that land (its median), so the way in is level.</li>
     *   <li>The land under the cone is a plane fitted to the ground round its foot (tilted at most
     *       {@link LairTuning#MAX_BASE_TILT}), set low ({@link LairTuning#BASE_PERCENTILE}) so the cone only ever fills.</li>
     *   <li>The rim stands {@link LairTuning#RIM_HEIGHT} above the floor and at least {@link LairTuning#RIM_RISE} above the
     *       high land round it, at most {@link LairTuning#MAX_RIM_HEIGHT} above the floor.</li>
     * </ol>
     * {@code lowestFloor} and {@code highestRim} keep it inside the world's height.
     */
    static VolcanoPlan volcanoPlan(int x, int z, Ground ground, int lowestFloor, int highestRim)
    {
        return volcanoPlans(x, z, ground, lowestFloor, highestRim).get(0);
    }

    /**
     * The volcano's plan for each of the four ways its mouth could face, best first (the lowest and most even land in
     * front; {@link #volcanoPlan} is the first). The builder takes the next one only where the land in front of the
     * first can't be reached by a walkable way in ({@link #approach}).
     */
    static List<VolcanoPlan> volcanoPlans(int x, int z, Ground ground, int lowestFloor, int highestRim)
    {
        List<double[]> fronts = new ArrayList<>(); // {direction index, cost, median, spread}, in Direction.Plane.HORIZONTAL order
        List<Direction> directions = new ArrayList<>();
        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            Direction side = direction.getClockWise();
            List<Integer> heights = new ArrayList<>();
            for (int s = APPROACH_SURVEY_FROM; s <= APPROACH_SURVEY_TO; s += 2)
            {
                for (int l = -MOUTH_HALF_WIDTH; l <= MOUTH_HALF_WIDTH; l += 2)
                {
                    heights.add(ground.top(x + direction.getStepX() * s + side.getStepX() * l,
                            z + direction.getStepZ() * s + side.getStepZ() * l));
                }
            }
            int[] sorted = heights.stream().mapToInt(Integer::intValue).sorted().toArray();
            int median = sorted[sorted.length / 2];
            int spread = sorted[(int) (sorted.length * 0.9D)] - sorted[(int) (sorted.length * 0.1D)];
            double cost = median + 0.5D * spread;
            fronts.add(new double[] {directions.size(), cost, median, spread});
            directions.add(direction);
        }
        // Best first; equal costs keep the north, east, south, west order (a stable sort).
        fronts.sort(Comparator.comparingDouble(f -> f[1]));

        // The land round the cone's foot, as a plane (least squares).
        List<double[]> ring = new ArrayList<>();
        for (double r = CONE_BASE_RADIUS - 6.0D; r <= CONE_BASE_RADIUS + 3.0D; r += 3.0D)
        {
            for (int k = 0; k < 36; k++)
            {
                double a = Math.toRadians(k * 10.0D);
                int dx = (int) Math.round(r * Math.cos(a));
                int dz = (int) Math.round(r * Math.sin(a));
                ring.add(new double[] {dx, dz, ground.top(x + dx, z + dz)});
            }
        }
        double mean = 0.0D;
        double mx = 0.0D;
        double mz = 0.0D;
        for (double[] p : ring)
        {
            mean += p[2];
            mx += p[0];
            mz += p[1];
        }
        mean /= ring.size();
        mx /= ring.size();
        mz /= ring.size();
        double sxx = 0.0D;
        double szz = 0.0D;
        double sxz = 0.0D;
        double sxh = 0.0D;
        double szh = 0.0D;
        for (double[] p : ring)
        {
            double dx = p[0] - mx;
            double dz = p[1] - mz;
            double dh = p[2] - mean;
            sxx += dx * dx;
            szz += dz * dz;
            sxz += dx * dz;
            sxh += dx * dh;
            szh += dz * dh;
        }
        double det = sxx * szz - sxz * sxz;
        double tiltX = det > 1.0E-9D ? (sxh * szz - szh * sxz) / det : 0.0D;
        double tiltZ = det > 1.0E-9D ? (szh * sxx - sxh * sxz) / det : 0.0D;
        double tilt = Math.hypot(tiltX, tiltZ);
        if (tilt > MAX_BASE_TILT)
        {
            tiltX *= MAX_BASE_TILT / tilt;
            tiltZ *= MAX_BASE_TILT / tilt;
        }
        double baseY = mean - tiltX * mx - tiltZ * mz;
        double[] residuals = new double[ring.size()];
        for (int i = 0; i < ring.size(); i++)
        {
            double[] p = ring.get(i);
            residuals[i] = p[2] - (baseY + tiltX * p[0] + tiltZ * p[1]);
        }
        Arrays.sort(residuals);
        baseY += residuals[(int) (BASE_PERCENTILE * residuals.length)];

        // The high land round the rim.
        List<Integer> inner = new ArrayList<>();
        int reach = (int) RIM_LAND_RADIUS;
        for (int dx = -reach; dx <= reach; dx += 4)
        {
            for (int dz = -reach; dz <= reach; dz += 4)
            {
                if (dx * dx + dz * dz <= RIM_LAND_RADIUS * RIM_LAND_RADIUS)
                {
                    inner.add(ground.top(x + dx, z + dz));
                }
            }
        }
        int[] sortedInner = inner.stream().mapToInt(Integer::intValue).sorted().toArray();
        int high = Math.max(sortedInner[(int) (sortedInner.length * 0.9D)], landmarkLand(x, z, ground));

        List<VolcanoPlan> plans = new ArrayList<>();
        for (double[] front : fronts)
        {
            int floor = Math.max(lowestFloor, Math.min((int) front[2], highestRim - RIM_HEIGHT));
            int rim = Math.min(Math.max(floor + RIM_HEIGHT, high + RIM_RISE), Math.min(floor + MAX_RIM_HEIGHT, highestRim));
            plans.add(new VolcanoPlan(x, z, floor, directions.get((int) front[0]), rim, Math.min(baseY, rim - 1.0D), tiltX, tiltZ,
                    high, (int) front[3]));
        }
        return plans;
    }

    /**
     * The land on the ring {@link LairTuning#LANDMARK_RING} blocks round the middle, at its
     * {@link LairTuning#LANDMARK_RING_PERCENTILE} (of {@link LairTuning#LANDMARK_RING_SAMPLES} samples): the land the rim
     * must stand well above to be a landmark (the headless check measures it the same way).
     */
    static int landmarkLand(int x, int z, Ground ground)
    {
        int[] land = new int[LANDMARK_RING_SAMPLES];
        for (int k = 0; k < LANDMARK_RING_SAMPLES; k++)
        {
            double a = 2.0D * Math.PI * k / LANDMARK_RING_SAMPLES;
            land[k] = ground.top(x + (int) Math.round(LANDMARK_RING * Math.cos(a)), z + (int) Math.round(LANDMARK_RING * Math.sin(a)));
        }
        Arrays.sort(land);
        return land[(int) (land.length * LANDMARK_RING_PERCENTILE)];
    }

    /**
     * Where the cave mouth goes (its distance from the middle): the first distance from
     * {@link LairTuning#MIN_MOUTH_DISTANCE} out where the cone's smooth surface over the way in is less than
     * {@link LairTuning#BROW} blocks above the mouth's top, at most {@link LairTuning#MAX_MOUTH_DISTANCE}. Rock is built up
     * over the mouth where the flank is lower (its brow).
     */
    static int volcanoMouth(VolcanoPlan plan)
    {
        Direction d = plan.tunnel();
        for (int s = MIN_MOUTH_DISTANCE; s < MAX_MOUTH_DISTANCE; s++)
        {
            if (plan.cone(d.getStepX() * s, d.getStepZ() * s) < plan.floorY() + MOUTH_HEIGHT + 1 + BROW)
            {
                return s;
            }
        }
        return MAX_MOUTH_DISTANCE;
    }

    // ---------------------------------------------------------------- the way in outside the mouth

    /** The half-width of the gully's floor (at the floor's level + 1) at distance {@code s}, mouth at {@code mouth}. */
    static double gullyHalf(int s, int mouth)
    {
        return MOUTH_HALF_WIDTH + 0.5D + Math.max(0, s - mouth) * GULLY_FLARE;
    }

    /**
     * How far a column in front of the gully is from the level apron (0 on the apron), counted the way a player walks:
     * the shaped land {@code n} blocks from the apron stays within {@code n} blocks above or below the floor.
     */
    static int apronDistance(int s, int l, int apronEnd, double apronHalf)
    {
        return Math.max(Math.max(0, s - apronEnd), Math.max(0, (int) Math.ceil(Math.abs(l) - apronHalf)));
    }

    /**
     * The way in outside the cave mouth, planned from the ground (R6b): where the gully ends, and the height of every
     * column of the land shaped in front of it. Columns are addressed by their distance along the way in ({@code s},
     * rows {@link #first} to {@link #end}) and to its side ({@code l}, up to {@link #reachL} either way).
     *
     * @param gullyEnd  where the gully ends (its last row); the level apron starts on the next row
     * @param apronEnd  the apron's last row
     * @param apronHalf the apron's half-width
     * @param reachL    how far to each side the shaped land reaches
     * @param end       the shaped land's last row (at least {@link LairTuning#BLEND} past the apron, further where the land
     *                  in front is steep or uneven)
     * @param heights   the Y of each column's top block, row by row ({@link #height})
     * @param seam      the biggest step from the last row to the land just beyond it, across the passage's width (5
     *                  blocks: the way in's narrowest part)
     * @param changed   how many columns differ from the land
     * @param maxChange the most any column was raised or lowered
     */
    record Approach(int gullyEnd, int apronEnd, double apronHalf, int reachL, int end, int[] heights, int seam, int changed,
                    int maxChange)
    {
        int first()
        {
            return gullyEnd + 1;
        }

        int width()
        {
            return 2 * reachL + 1;
        }

        boolean contains(int s, int l)
        {
            return s >= first() && s <= end && Math.abs(l) <= reachL;
        }

        /** The Y of the top block of the column at {@code s}, {@code l} (inside: {@link #contains}). */
        int height(int s, int l)
        {
            return heights[(s - first()) * width() + l + reachL];
        }

        int apronDistance(int s, int l)
        {
            return LairShape.apronDistance(s, l, apronEnd, apronHalf);
        }

        /** True if the way in meets the land beyond it (a block's step at most, across the passage's width). */
        boolean meetsLand()
        {
            return seam <= 1;
        }

        String describe()
        {
            return String.format("gully to %d, apron to %d, the land shaped out to %d (%d past the apron, %d wide), %d column(s) "
                            + "raised or lowered (at most %d), meeting the land beyond within %d", gullyEnd, apronEnd, end,
                    end - apronEnd, width(), changed, maxChange, seam);
        }
    }

    /**
     * Plans the way in outside the mouth from {@code terrain} (the ground with the volcano's cone on it, before the way in
     * is cut), so that nobody ever has to climb or drop more than a block anywhere on it:
     * <ol>
     *   <li>The gully runs out from the mouth until the ground in front is no higher than the floor (+1), at most
     *       {@link LairTuning#MAX_GULLY} blocks; then the level apron, {@link LairTuning#APRON} rows at the floor's
     *       level.</li>
     *   <li>Round the apron the land is held within {@code n} blocks of the floor {@code n} blocks out
     *       ({@link #apronDistance}) and then made a staircase: of all the heights that never step more than a block
     *       between neighbours (north, east, south, west), the one that changes the land least (half-way between the
     *       highest such heights under the land and the lowest over it). Land that already steps a block at most is
     *       left as it is.</li>
     *   <li>It reaches {@link LairTuning#BLEND} rows past the apron, or further (up to
     *       {@link LairTuning#APPROACH_MAX_EXTRA} more) until its last row meets the land beyond within a block across the
     *       passage's width (5 blocks): on steep or uneven land it becomes a ramp out to the land.</li>
     * </ol>
     */
    static Approach approach(VolcanoPlan plan, int mouth, Ground terrain)
    {
        Direction dir = plan.tunnel();
        Direction side = dir.getClockWise();
        int floor = plan.floorY();
        IntBinaryOperator land = (s, l) -> terrain.top(plan.x() + dir.getStepX() * s + side.getStepX() * l,
                plan.z() + dir.getStepZ() * s + side.getStepZ() * l);
        int gullyEnd = mouth + MAX_GULLY;
        for (int s = mouth + 1; s <= mouth + MAX_GULLY; s++)
        {
            if (land.applyAsInt(s, 0) <= floor + 1)
            {
                gullyEnd = s;
                break;
            }
        }
        int apronEnd = gullyEnd + APRON;
        double apronHalf = gullyHalf(gullyEnd, mouth);
        int reachL = (int) Math.ceil(apronHalf) + BLEND;
        int width = 2 * reachL + 1;
        int first = gullyEnd + 1;
        int shortest = apronEnd + BLEND;
        int longest = shortest + APPROACH_MAX_EXTRA;
        int[][] rows = new int[longest + 2 - first][]; // the land, read a row at a time as it's needed
        Approach best = null;
        for (int end = shortest; end <= longest; end++)
        {
            for (int s = first; s <= end + 1; s++)
            {
                if (rows[s - first] == null)
                {
                    int[] row = new int[width];
                    for (int l = -reachL; l <= reachL; l++)
                    {
                        row[l + reachL] = land.applyAsInt(s, l);
                    }
                    rows[s - first] = row;
                }
            }
            int[] heights = staircase(floor, first, end, apronEnd, apronHalf, reachL, rows);
            int seam = 0;
            for (int l = -PASSAGE_HALF_WIDTH; l <= PASSAGE_HALF_WIDTH; l++)
            {
                seam = Math.max(seam, Math.abs(heights[(end - first) * width + l + reachL] - rows[end + 1 - first][l + reachL]));
            }
            int changed = 0;
            int maxChange = 0;
            for (int i = 0; i < heights.length; i++)
            {
                int change = Math.abs(heights[i] - rows[i / width][i % width]);
                changed += change > 0 ? 1 : 0;
                maxChange = Math.max(maxChange, change);
            }
            Approach approach = new Approach(gullyEnd, apronEnd, apronHalf, reachL, end, heights, seam, changed, maxChange);
            if (approach.meetsLand())
            {
                return approach;
            }
            if (best == null || seam < best.seam())
            {
                best = approach;
            }
        }
        return best;
    }

    /**
     * The shaped land's heights for rows {@code first} to {@code end} (see {@link #approach}). {@code rows} holds the
     * land, a row per {@code s} from {@code first}.
     */
    private static int[] staircase(int floor, int first, int end, int apronEnd, double apronHalf, int reachL, int[][] rows)
    {
        int width = 2 * reachL + 1;
        int count = end - first + 1;
        int[] below = new int[count * width]; // the highest staircase under the land
        int[] above = new int[count * width]; // the lowest staircase over it
        for (int r = 0; r < count; r++)
        {
            for (int c = 0; c < width; c++)
            {
                int n = apronDistance(first + r, c - reachL, apronEnd, apronHalf);
                int h = Mth.clamp(rows[r][c], floor - n, floor + n);
                below[r * width + c] = h;
                above[r * width + c] = h;
            }
        }
        // A block's step between neighbours: two sweeps along each row, then two along each column (exact for N/E/S/W steps).
        for (int r = 0; r < count; r++)
        {
            for (int c = 1; c < width; c++)
            {
                relax(below, above, r * width + c, r * width + c - 1);
            }
            for (int c = width - 2; c >= 0; c--)
            {
                relax(below, above, r * width + c, r * width + c + 1);
            }
        }
        for (int c = 0; c < width; c++)
        {
            for (int r = 1; r < count; r++)
            {
                relax(below, above, r * width + c, (r - 1) * width + c);
            }
            for (int r = count - 2; r >= 0; r--)
            {
                relax(below, above, r * width + c, (r + 1) * width + c);
            }
        }
        int[] heights = new int[count * width];
        for (int i = 0; i < heights.length; i++)
        {
            heights[i] = Math.floorDiv(below[i] + above[i], 2);
        }
        return heights;
    }

    /** Keeps a column within a block of its neighbour: the "below" staircase only drops, the "above" one only rises. */
    private static void relax(int[] below, int[] above, int at, int from)
    {
        below[at] = Math.min(below[at], below[from] + 1);
        above[at] = Math.max(above[at], above[from] - 1);
    }

    // ---------------------------------------------------------------- the plan for a spot

    /** The ground height of a column: the Y of its top ground block (not plants, trees or fluids). */
    @FunctionalInterface
    interface Ground
    {
        int top(int x, int z);
    }

    /**
     * The plan for a lair whose floor's middle is at {@code x}, {@code z}.
     *
     * @param floorY     the Y of the floor blocks
     * @param tunnel     the way the tunnel runs out of the cavern (towards its mouth)
     * @param descent    how many blocks the tunnel goes down from its mouth to the cavern floor
     * @param exposure   how many blocks of the dome (plus {@link LairTuning#COVER}) stick out above the natural ground,
     *                   on average over the floor's columns: 0 is fully inside rock; lower is better
     * @param highGround the high ground over the dome (Y)
     * @param mouthGround the ground at the tunnel's mouth (Y)
     * @param buried     the share of the floor's columns with natural rock all the way over the dome (0 to 1)
     */
    record Plan(int x, int z, int floorY, Direction tunnel, int descent, double exposure, int highGround, int mouthGround,
                double buried)
    {
        /** How good the spot is: lower is better (most of the dome inside rock, a short slope down). */
        double cost()
        {
            return exposure + DESCENT_COST * descent;
        }

        String describe()
        {
            return String.format("floor y %d, tunnel to the %s going down %d, high ground y %d, mouth ground y %d, "
                            + "%.0f%% of the dome inside natural rock, %.1f blocks sticking out on average",
                    floorY, tunnel.getName(), descent, highGround, mouthGround, buried * 100.0D, exposure);
        }
    }

    /**
     * Works out the best plan for a lair at {@code x}, {@code z} from the ground round it: for each of the four tunnel
     * directions and each descent ({@link LairTuning#MIN_DESCENT} to {@link LairTuning#MAX_DESCENT}), the floor goes
     * {@code descent} blocks below the ground at the tunnel's mouth; the shallowest descent that buries the dome under
     * {@link LairTuning#COVER} blocks of rock is used, or the deepest if none does. The direction with the lowest
     * {@link Plan#cost} wins: the tunnel comes out where the ground falls away (a mesa's or a cliff's edge).
     */
    static Plan plan(int x, int z, Ground ground)
    {
        int n = PLAN_SAMPLES.length;
        int[] heights = new int[n];
        double[] radii = new double[n];
        List<Integer> inner = new ArrayList<>();
        for (int i = 0; i < n; i++)
        {
            int dx = PLAN_SAMPLES[i][0];
            int dz = PLAN_SAMPLES[i][1];
            heights[i] = ground.top(x + dx, z + dz);
            radii[i] = Math.sqrt(dx * dx + dz * dz);
            if (radii[i] <= HIGH_GROUND_RADIUS)
            {
                inner.add(heights[i]);
            }
        }
        int[] sorted = inner.stream().mapToInt(Integer::intValue).sorted().toArray();
        int high = sorted[Math.min(sorted.length - 1, (int) Math.floor(HIGH_GROUND_PERCENTILE * sorted.length))];
        // The floor that would put the dome's top COVER blocks under the high ground.
        int idealFloor = high - DOME_HEIGHT - COVER;

        Plan best = null;
        for (Direction direction : Direction.Plane.HORIZONTAL)
        {
            Direction side = direction.getClockWise();
            int chosenDescent = -1;
            int chosenFloor = Integer.MAX_VALUE;
            int chosenMouth = 0;
            for (int descent = MIN_DESCENT; descent <= MAX_DESCENT; descent++)
            {
                int s = mouthDistance(descent);
                int[] mouth = new int[2 * TUNNEL_HALF_WIDTH + 1];
                for (int l = -TUNNEL_HALF_WIDTH; l <= TUNNEL_HALF_WIDTH; l++)
                {
                    mouth[l + TUNNEL_HALF_WIDTH] = ground.top(x + direction.getStepX() * s + side.getStepX() * l,
                            z + direction.getStepZ() * s + side.getStepZ() * l);
                }
                Arrays.sort(mouth);
                int mouthGround = mouth[mouth.length / 2];
                int floor = mouthGround - descent;
                if (floor <= idealFloor)
                {
                    chosenDescent = descent;
                    chosenFloor = floor;
                    chosenMouth = mouthGround;
                    break;
                }
                if (floor < chosenFloor)
                {
                    chosenDescent = descent;
                    chosenFloor = floor;
                    chosenMouth = mouthGround;
                }
            }
            double exposed = 0.0D;
            int buried = 0;
            for (int i = 0; i < n; i++)
            {
                double top = chosenFloor + ceiling(radii[i]);
                exposed += Math.max(0.0D, top + COVER - heights[i]);
                if (heights[i] >= top + 1.0D)
                {
                    buried++;
                }
            }
            Plan plan = new Plan(x, z, chosenFloor, direction, chosenDescent, exposed / n, high, chosenMouth, (double) buried / n);
            if (best == null || plan.cost() < best.cost() - 1.0E-9D)
            {
                best = plan;
            }
        }
        return best;
    }

    private static int[][] planSamples()
    {
        List<int[]> samples = new ArrayList<>();
        for (int dx = -FLOOR_RADIUS; dx <= FLOOR_RADIUS; dx += PLAN_GRID_STEP)
        {
            for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz += PLAN_GRID_STEP)
            {
                if (dx * dx + dz * dz <= FLOOR_EDGE * FLOOR_EDGE)
                {
                    samples.add(new int[] {dx, dz});
                }
            }
        }
        return samples.toArray(new int[0][]);
    }
}
