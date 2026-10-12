package com.reg21meme.dross.boss.lair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.Direction;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * The lair's shape, as plain maths (no world needed, safe on any thread), and the plan for a spot: how deep its floor
 * goes and which way its tunnel runs, worked out from the ground heights round it.
 * <p>
 * Seen from above, the lair is a round floor ({@link #FLOOR_EDGE}) with the tunnel running out of it north, east, south
 * or west. Along the tunnel, "s" is the distance from the floor's middle: the tunnel's first block is at
 * {@code s = FLOOR_RADIUS + 1}, it runs level to {@link #straightEnd()}, climbs half a block per block to its mouth
 * ({@link #mouthDistance}) and the cut approach in front of the mouth ends at {@link #entranceDistance}.
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

    /** The mound's top (before its lumps), in blocks above the floor, {@code r} blocks from the middle; 0 beyond it. */
    static double moundTop(double r)
    {
        if (r >= MOUND_RADIUS)
        {
            return 0.0D;
        }
        double t = r / MOUND_RADIUS;
        return MOUND_HEIGHT * Math.sqrt(1.0D - t * t);
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

    // ---------------------------------------------------------------- the tunnel

    /** The last block (s) of the tunnel's level part. */
    static int straightEnd()
    {
        return FLOOR_RADIUS + TUNNEL_STRAIGHT;
    }

    /** The tunnel's mouth (its last covered block, s), for an entrance that goes down {@code descent} blocks. */
    static int mouthDistance(int descent)
    {
        return straightEnd() + 2 * descent;
    }

    /** The outermost block of the approach in front of the mouth: where a player arriving stands. */
    static int entranceDistance(int descent)
    {
        return mouthDistance(descent) + APPROACH;
    }

    /**
     * The height of the walking surface above the floor block's bottom... put simply: how many blocks above the floor
     * level (the floor block's own Y) a player stands at tunnel block {@code s}: 1 on the level part (standing on a
     * floor-level block), then half a block more per block up the slope, {@code 1 + descent} at the mouth and beyond.
     */
    static double walkTop(int s, int descent)
    {
        int end = straightEnd();
        if (s <= end)
        {
            return 1.0D;
        }
        if (s <= mouthDistance(descent))
        {
            return 1.0D + 0.5D * (s - end);
        }
        return 1.0D + descent;
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
