package com.reg21meme.dross.dimension.rimefall;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.util.Mth;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * One of Rimefall's big frozen lakes (one per patch; {@link RimefallAreas} plans it): a wobbly round sheet of ice at
 * {@link #iceY()} over dark water, deeper in the middle, with a few open water holes, and a shore that blends back into
 * the land ({@link RimefallLakePiece} builds it). Everything about it follows from its middle, its ice level, its radius
 * and its seed, so world generation, the spawn rules and the checks all see the same lake. Immutable.
 */
public final class RimefallLake
{
    private final int x;
    private final int z;
    private final int iceY;
    private final double radius;
    private final long seed;
    /** The edge's waves' phases. */
    private final double phase2;
    private final double phase3;
    private final double phase5;
    /** The holes: x, z, radius and wobble phase each. */
    private final double[] holes;

    private RimefallLake(int x, int z, int iceY, double radius, long seed)
    {
        this.x = x;
        this.z = z;
        this.iceY = iceY;
        this.radius = radius;
        this.seed = seed;
        Random random = new Random(seed);
        this.phase2 = random.nextDouble() * Math.PI * 2.0D;
        this.phase3 = random.nextDouble() * Math.PI * 2.0D;
        this.phase5 = random.nextDouble() * Math.PI * 2.0D;
        this.holes = placeHoles(random);
    }

    /** The lake with this middle, ice level, radius and seed (its shape and holes follow from the seed). */
    public static RimefallLake of(int x, int z, int iceY, double radius, long seed)
    {
        return new RimefallLake(x, z, iceY, radius, seed);
    }

    /** Its shape alone (middle 0, 0): what the fit test measures a spot with. */
    static RimefallLake shape(double radius, long seed)
    {
        return new RimefallLake(0, 0, 0, radius, seed);
    }

    /** The same lake moved to another middle and ice level. */
    RimefallLake at(int x, int z, int iceY)
    {
        return new RimefallLake(x, z, iceY, this.radius, this.seed);
    }

    private double[] placeHoles(Random random)
    {
        int count = RimefallTuning.HOLES_MIN + random.nextInt(RimefallTuning.HOLES_MAX - RimefallTuning.HOLES_MIN + 1);
        List<double[]> placed = new ArrayList<>();
        for (int tries = 0; tries < 60 && placed.size() < count; tries++)
        {
            double angle = random.nextDouble() * Math.PI * 2.0D;
            double r = this.radius * RimefallTuning.HOLES_REACH * Math.sqrt(0.05D + 0.95D * random.nextDouble());
            double hr = RimefallTuning.HOLE_RADIUS_MIN + random.nextDouble() * (RimefallTuning.HOLE_RADIUS_MAX - RimefallTuning.HOLE_RADIUS_MIN);
            double phase = random.nextDouble() * Math.PI * 2.0D;
            double hx = this.x + Math.cos(angle) * r;
            double hz = this.z + Math.sin(angle) * r;
            if (r + hr * 1.3D > this.edge(angle) - 4.0D)
            {
                continue;
            }
            boolean apart = true;
            for (double[] h : placed)
            {
                if (Math.hypot(h[0] - hx, h[1] - hz) < h[2] + hr + RimefallTuning.HOLE_SPACING)
                {
                    apart = false;
                    break;
                }
            }
            if (apart)
            {
                placed.add(new double[] {hx, hz, hr, phase});
            }
        }
        double[] out = new double[placed.size() * 4];
        for (int i = 0; i < placed.size(); i++)
        {
            System.arraycopy(placed.get(i), 0, out, i * 4, 4);
        }
        return out;
    }

    // ---------------------------------------------------------------- its shape

    /** Its middle (on the ice: y is the ice's). */
    public BlockPos centre()
    {
        return new BlockPos(this.x, this.iceY, this.z);
    }

    /** The y of its ice (the water is below it). */
    public int iceY()
    {
        return this.iceY;
    }

    /** Its middle radius (the edge wobbles round it). */
    public double radius()
    {
        return this.radius;
    }

    public long seed()
    {
        return this.seed;
    }

    /**
     * The most Pond Skaters on it at once: {@link RimefallTuning#SKATERS_PER_LAKE}, or one more on about half the lakes
     * ({@link RimefallTuning#LAKE_SECOND_SKATER_CHANCE}, fixed by its seed: the user, "2 could possibly spawn").
     */
    public int skaterCap()
    {
        boolean second = new Random(this.seed ^ 0x536B61746572734CL).nextDouble() < RimefallTuning.LAKE_SECOND_SKATER_CHANCE;
        return RimefallTuning.SKATERS_PER_LAKE + (second ? 1 : 0);
    }

    /** How many open water holes it has. */
    public int holeCount()
    {
        return this.holes.length / 4;
    }

    /** Hole {@code i}'s middle (y: the ice's). */
    public BlockPos hole(int i)
    {
        return BlockPos.containing(this.holes[i * 4], this.iceY, this.holes[i * 4 + 1]);
    }

    /** The edge's distance from the middle in this direction (radians). */
    public double edge(double angle)
    {
        return this.radius * (1.0D + RimefallTuning.LAKE_WOBBLE_2 * Math.sin(2.0D * angle + this.phase2)
                + RimefallTuning.LAKE_WOBBLE_3 * Math.sin(3.0D * angle + this.phase3)
                + RimefallTuning.LAKE_WOBBLE_5 * Math.sin(5.0D * angle + this.phase5));
    }

    /** The furthest its edge ever reaches from the middle. */
    public double reach()
    {
        return reach(this.radius);
    }

    static double reach(double radius)
    {
        return radius * (1.0D + RimefallTuning.LAKE_WOBBLE_2 + RimefallTuning.LAKE_WOBBLE_3 + RimefallTuning.LAKE_WOBBLE_5);
    }

    /** How far the column's middle lies past the lake's edge (negative: in the lake). */
    public double outside(int bx, int bz)
    {
        double dx = bx + 0.5D - (this.x + 0.5D);
        double dz = bz + 0.5D - (this.z + 0.5D);
        return Math.sqrt(dx * dx + dz * dz) - this.edge(Math.atan2(dz, dx));
    }

    /** True if the column is in the lake (under its ice or a hole). */
    public boolean inLake(int bx, int bz)
    {
        return this.outside(bx, bz) < 0.0D;
    }

    /** True if the column is one of its open water holes. */
    public boolean isHole(int bx, int bz)
    {
        for (int i = 0; i < this.holes.length; i += 4)
        {
            double dx = bx + 0.5D - this.holes[i];
            double dz = bz + 0.5D - this.holes[i + 1];
            double r = this.holes[i + 2] * (1.0D + 0.22D * Math.sin(3.0D * Math.atan2(dz, dx) + this.holes[i + 3]));
            if (dx * dx + dz * dz < r * r)
            {
                return true;
            }
        }
        return false;
    }

    /** The lake bed's y in a column of the lake: the water fills the blocks between it and the ice. */
    public int bedY(int bx, int bz)
    {
        double dx = bx + 0.5D - (this.x + 0.5D);
        double dz = bz + 0.5D - (this.z + 0.5D);
        double t = Mth.clamp(Math.sqrt(dx * dx + dz * dz) / this.edge(Math.atan2(dz, dx)), 0.0D, 1.0D);
        int depth = (int) Math.round(RimefallTuning.DEPTH_EDGE + (RimefallTuning.DEPTH_MIDDLE - RimefallTuning.DEPTH_EDGE) * (1.0D - t * t));
        if (this.isHole(bx, bz))
        {
            depth = Math.max(depth, RimefallTuning.HOLE_MIN_DEPTH);
        }
        return this.iceY - 1 - depth;
    }

    /** The box its building may touch: the lake, its shore, from under its deepest bed to over the most it may cut. */
    public BoundingBox box()
    {
        int r = (int) Math.ceil(this.reach()) + RimefallTuning.SHORE_WIDTH + 1;
        return new BoundingBox(this.x - r, this.iceY - RimefallTuning.DEPTH_MIDDLE - RimefallTuning.MAX_DAM - 4, this.z - r,
                this.x + r, this.iceY + RimefallTuning.MAX_CUT + 24, this.z + r);
    }

    // ---------------------------------------------------------------- saving (with its structure piece)

    public CompoundTag save()
    {
        CompoundTag tag = new CompoundTag();
        tag.putInt("X", this.x);
        tag.putInt("Z", this.z);
        tag.putInt("Ice", this.iceY);
        tag.putDouble("Radius", this.radius);
        tag.putLong("Seed", this.seed);
        return tag;
    }

    public static RimefallLake load(CompoundTag tag)
    {
        return new RimefallLake(tag.getInt("X"), tag.getInt("Z"), tag.getInt("Ice"), tag.getDouble("Radius"), tag.getLong("Seed"));
    }

    @Override
    public String toString()
    {
        return String.format(java.util.Locale.ROOT, "lake at %d, %d (ice y %d, about %d across, %d holes, up to %d Pond Skaters)", this.x,
                this.z, this.iceY, Math.round(this.radius * 2.0D), this.holeCount(), this.skaterCap());
    }
}
