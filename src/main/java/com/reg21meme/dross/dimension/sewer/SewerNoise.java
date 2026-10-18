package com.reg21meme.dross.dimension.sewer;

import java.util.Arrays;

/**
 * Small, fixed random numbers for the sewers: the same answer for the same position every time, on every thread and in
 * every chunk. A sewer network is built chunk by chunk (each chunk places its own part, maybe minutes apart), so every
 * "random" choice of a block (a cracked brick, a patch of sludge) has to come from its position and the network's own
 * seed, never from a running random number generator, or the parts wouldn't match at the chunk borders.
 */
final class SewerNoise
{
    private SewerNoise() {}

    /** A well-mixed 64-bit number from a seed and a position (SplitMix64's finaliser). */
    static long hash(long seed, int x, int y, int z)
    {
        long h = seed ^ (x * 0x9E3779B97F4A7C15L) ^ (y * 0xC2B2AE3D27D4EB4FL) ^ (z * 0x165667B19E3779F9L);
        h = (h ^ (h >>> 30)) * 0xBF58476D1CE4E5B9L;
        h = (h ^ (h >>> 27)) * 0x94D049BB133111EBL;
        return h ^ (h >>> 31);
    }

    /** A number from 0 (included) to 1 (excluded), fixed for this seed and position. */
    static double unit(long seed, int x, int y, int z)
    {
        return (hash(seed, x, y, z) >>> 11) * 0x1.0p-53;
    }

    /** Smooth 2D value noise, -1 to 1, with features about 1 unit apart. */
    static double value2(long seed, double x, double z)
    {
        int x0 = (int) Math.floor(x);
        int z0 = (int) Math.floor(z);
        double fx = smooth(x - x0);
        double fz = smooth(z - z0);
        double a = lattice(seed, x0, 0, z0);
        double b = lattice(seed, x0 + 1, 0, z0);
        double c = lattice(seed, x0, 0, z0 + 1);
        double d = lattice(seed, x0 + 1, 0, z0 + 1);
        return lerp(fz, lerp(fx, a, b), lerp(fx, c, d));
    }

    /**
     * Patchy 2D noise for floors (sludge patches): three octaves of {@link #value2}, the biggest features about
     * {@code scale} blocks apart. Roughly -1 to 1.
     */
    static double patches(long seed, int x, int z, double scale)
    {
        double v = value2(seed, x / scale, z / scale) * 0.6
                + value2(seed + 101, x / (scale * 0.5), z / (scale * 0.5)) * 0.3
                + value2(seed + 202, x / (scale * 0.25), z / (scale * 0.25)) * 0.1;
        return v;
    }

    private static double lattice(long seed, int x, int y, int z)
    {
        return unit(seed, x, y, z) * 2.0D - 1.0D;
    }

    private static double smooth(double t)
    {
        return t * t * (3.0D - 2.0D * t);
    }

    private static double lerp(double t, double a, double b)
    {
        return a + (b - a) * t;
    }

    // ---------------------------------------------------------------------------------------------
    // Turning a share of the floor ("about a quarter of it is sludge") into a noise threshold
    // ---------------------------------------------------------------------------------------------

    private static final double[] PATCH_SAMPLES = samplePatches();

    /**
     * The value {@link #patches} is above on about {@code share} of the floor (0 to 1): measured once on a fixed
     * sample, so a share in {@link SewerTuning} really covers that much of the floor.
     */
    static double thresholdFor(double share)
    {
        if (share <= 0.0D)
        {
            return Double.MAX_VALUE;
        }
        if (share >= 1.0D)
        {
            return -Double.MAX_VALUE;
        }
        int index = (int) Math.round((1.0D - share) * (PATCH_SAMPLES.length - 1));
        return PATCH_SAMPLES[Math.max(0, Math.min(PATCH_SAMPLES.length - 1, index))];
    }

    private static double[] samplePatches()
    {
        int n = 192;
        double[] v = new double[n * n];
        for (int z = 0; z < n; z++)
        {
            for (int x = 0; x < n; x++)
            {
                v[z * n + x] = patches(20261016L, x * 3, z * 3, SewerTuning.SLUDGE_PATCH_SIZE);
            }
        }
        Arrays.sort(v);
        return v;
    }
}
