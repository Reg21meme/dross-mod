package com.reg21meme.dross.dimension;

import com.reg21meme.dross.portal.DrossHub;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.IntStream;

/**
 * Statistics of one world seed's Dross terrain, worked out from the noise alone ({@link TerrainProbe}): what the headless
 * terrain checks assert and the previews report. Every number is in blocks unless it says otherwise.
 * <ul>
 *   <li><b>Far window</b> ({@value #FAR_HALF} x 2 blocks square round {@value #FAR_X}, {@value #FAR_Z}, far from the hub's
 *       influence): each biome's share of the land (and the share of any biome that isn't one of the five: none should
 *       be), and the size of its regions. A region's size is the diameter of a
 *       circle of the same area; the "area-weighted median" is the size of the region the middle of the biome's land
 *       lies in (big regions count for more than slivers). Regions touching the window's edge are left out.</li>
 *   <li><b>Heights</b> (a {@value #HEIGHT_HALF} x 2 square inside the far window, every {@value #HEIGHT_CELL} blocks): each
 *       biome's heights and slopes, and the top of every mountain range (a Gravecrags region at least
 *       {@value #MOUNTAIN_MIN_SIDE} blocks square in area).</li>
 *   <li><b>Round the hub</b>: how far out the land is all Silverwood Forest, how near each biome comes (every
 *       {@value #NEAR_CELL} blocks within {@value #NEAR_HALF}), and how flat the natural ground under the hub's footprint is.</li>
 * </ul>
 */
final class TerrainSurvey
{
    /** The far window's centre and half-width, and its cell size (blocks). */
    static final int FAR_X = 24000;
    static final int FAR_Z = -24000;
    static final int FAR_HALF = 4096;
    static final int FAR_CELL = 16;
    /** The height window (inside the far window) and its cell size. */
    static final int HEIGHT_HALF = 2048;
    static final int HEIGHT_CELL = 8;
    /** A Gravecrags region counts as a mountain range if its area is at least this many blocks squared. */
    static final int MOUNTAIN_MIN_SIDE = 256;
    /** Round the hub: the grid for "how near does each biome come". */
    static final int NEAR_HALF = 1600;
    static final int NEAR_CELL = 16;
    /** The hub's footprint (the Shattered Spire's, X -25..49, Z -34..38) and the margin round it whose natural ground is checked. */
    static final int HUB_MIN_X = -25;
    static final int HUB_MAX_X = 49;
    static final int HUB_MIN_Z = -34;
    static final int HUB_MAX_Z = 38;
    static final int HUB_MARGIN = 8;

    static final int BIOMES = DrossBiomes.ALL.size();
    static final int GRAVECRAGS = DrossBiomes.ALL.indexOf(DrossBiomes.GRAVECRAGS);
    static final int SILVERWOOD = DrossBiomes.ALL.indexOf(DrossBiomes.SILVERWOOD_FOREST);

    /** One biome's regions: how many, the median and area-weighted median size, and the biggest. */
    record Regions(int count, double medianSize, double weightedSize, double largest)
    {
        static final Regions NONE = new Regions(0, 0.0D, 0.0D, 0.0D);
    }

    /** One biome's heights: 5th, 50th and 95th percentile, and its slope's median and 95th percentile (blocks per block). */
    record Heights(double p5, double p50, double p95, double slope50, double slope95) {}

    /**
     * Everything about one seed.
     *
     * @param shares        each biome's share of the far window (in {@link DrossBiomes#ALL}'s order)
     * @param otherShare    the share of the far window that's some other biome (none of {@link DrossBiomes#ALL}: should be 0)
     * @param regions       each biome's regions in the far window
     * @param nearest       how far from the hub each biome's nearest sample is (Double.MAX_VALUE if none within NEAR_HALF)
     * @param heights       each biome's heights (null if too few samples)
     * @param peaks         the top of each mountain range in the height window
     * @param minHeight     lowest ground in the height window
     * @param maxHeight     highest
     * @param hubRange      the natural ground under the hub's footprint and margin: highest minus lowest (rounded down)
     * @param hubStd        ...its standard deviation
     * @param hubSlope      ...the biggest difference between two neighbouring columns
     */
    record SeedStats(long seed, double[] shares, double otherShare, Regions[] regions, double[] nearest, Heights[] heights, List<Double> peaks,
                     double minHeight, double maxHeight, int hubRange, double hubStd, int hubSlope)
    {
        double allSilverwoodRadius()
        {
            double r = Double.MAX_VALUE;
            for (int b = 0; b < BIOMES; b++)
            {
                if (b != SILVERWOOD)
                {
                    r = Math.min(r, nearest[b]);
                }
            }
            return r;
        }

        String summary()
        {
            StringBuilder s = new StringBuilder(String.format(Locale.ROOT, "seed %d: all Silverwood within %.0f; nearest", seed, allSilverwoodRadius()));
            for (int b = 0; b < BIOMES; b++)
            {
                if (b != SILVERWOOD)
                {
                    s.append(String.format(Locale.ROOT, " %s %.0f", name(b), nearest[b]));
                }
            }
            s.append("; shares");
            for (int b = 0; b < BIOMES; b++)
            {
                s.append(String.format(Locale.ROOT, " %s %.3f", name(b), shares[b]));
            }
            s.append(String.format(Locale.ROOT, " other %.3f", otherShare));
            s.append("; region sizes (area-weighted median)");
            for (int b = 0; b < BIOMES; b++)
            {
                s.append(String.format(Locale.ROOT, " %s %.0f", name(b), regions[b].weightedSize()));
            }
            s.append(String.format(Locale.ROOT, "; heights %.0f..%.0f; %d mountain ranges, peaks %s; hub ground range %d, std %.2f, steepest step %d",
                    minHeight, maxHeight, peaks.size(), peaks.stream().map(p -> String.valueOf(Math.round(p))).toList(), hubRange, hubStd, hubSlope));
            return s.toString();
        }
    }

    private TerrainSurvey() {}

    static String name(int biome)
    {
        return biome >= 0 && biome < BIOMES ? DrossBiomes.name(DrossBiomes.ALL.get(biome)) : "other";
    }

    /** Works out everything for one seed (a few seconds; uses all the processor's cores). */
    static SeedStats survey(TerrainProbe probe)
    {
        // Far window: shares and regions
        int n = FAR_HALF * 2 / FAR_CELL;
        int[] far = biomeGrid(probe, FAR_X - FAR_HALF, FAR_Z - FAR_HALF, FAR_CELL, n, n);
        double[] shares = new double[BIOMES];
        double other = 0.0D;
        for (int b : far)
        {
            if (b >= 0)
            {
                shares[b]++;
            }
            else
            {
                other++;
            }
        }
        other /= far.length;
        Regions[] regions = new Regions[BIOMES];
        for (int b = 0; b < BIOMES; b++)
        {
            shares[b] /= far.length;
            regions[b] = regions(far, n, n, b, FAR_CELL);
        }

        // Heights
        int m = HEIGHT_HALF * 2 / HEIGHT_CELL;
        int x0 = FAR_X - HEIGHT_HALF;
        int z0 = FAR_Z - HEIGHT_HALF;
        int[] hb = biomeGrid(probe, x0, z0, HEIGHT_CELL, m, m);
        double[] h = new double[m * m];
        IntStream.range(0, m).parallel().forEach(j -> {
            for (int i = 0; i < m; i++)
            {
                h[j * m + i] = probe.height(x0 + i * HEIGHT_CELL, z0 + j * HEIGHT_CELL);
            }
        });
        Heights[] heights = new Heights[BIOMES];
        for (int b = 0; b < BIOMES; b++)
        {
            List<Double> values = new ArrayList<>();
            List<Double> slopes = new ArrayList<>();
            for (int j = 1; j < m - 1; j++)
            {
                for (int i = 1; i < m - 1; i++)
                {
                    int k = j * m + i;
                    if (hb[k] == b)
                    {
                        values.add(h[k]);
                        double gx = (h[k + 1] - h[k - 1]) / (2.0D * HEIGHT_CELL);
                        double gz = (h[k + m] - h[k - m]) / (2.0D * HEIGHT_CELL);
                        slopes.add(Math.sqrt(gx * gx + gz * gz));
                    }
                }
            }
            if (values.size() >= 100)
            {
                heights[b] = new Heights(percentile(values, 5), percentile(values, 50), percentile(values, 95), percentile(slopes, 50),
                        percentile(slopes, 95));
            }
        }
        List<Double> peaks = mountainPeaks(hb, h, m);
        double min = Arrays.stream(h).min().orElse(0.0D);
        double max = Arrays.stream(h).max().orElse(0.0D);

        // Round the hub
        int cx = DrossHub.CENTER.getX();
        int cz = DrossHub.CENTER.getZ();
        int k = NEAR_HALF * 2 / NEAR_CELL + 1;
        int[] near = biomeGrid(probe, cx - NEAR_HALF, cz - NEAR_HALF, NEAR_CELL, k, k);
        double[] nearest = new double[BIOMES];
        Arrays.fill(nearest, Double.MAX_VALUE);
        for (int j = 0; j < k; j++)
        {
            for (int i = 0; i < k; i++)
            {
                int b = near[j * k + i];
                if (b >= 0)
                {
                    double dx = i * NEAR_CELL - NEAR_HALF;
                    double dz = j * NEAR_CELL - NEAR_HALF;
                    nearest[b] = Math.min(nearest[b], Math.sqrt(dx * dx + dz * dz));
                }
            }
        }

        // The natural ground under the hub (whole blocks, like the ground the hub is fitted onto)
        int w = HUB_MAX_X - HUB_MIN_X + 1 + 2 * HUB_MARGIN;
        int d = HUB_MAX_Z - HUB_MIN_Z + 1 + 2 * HUB_MARGIN;
        int[] ground = new int[w * d];
        IntStream.range(0, d).parallel().forEach(j -> {
            for (int i = 0; i < w; i++)
            {
                ground[j * w + i] = (int) Math.floor(probe.height(cx + HUB_MIN_X - HUB_MARGIN + i, cz + HUB_MIN_Z - HUB_MARGIN + j));
            }
        });
        int lo = Arrays.stream(ground).min().orElse(0);
        int hi = Arrays.stream(ground).max().orElse(0);
        double mean = Arrays.stream(ground).average().orElse(0.0D);
        double var = Arrays.stream(ground).mapToDouble(g -> (g - mean) * (g - mean)).average().orElse(0.0D);
        int slope = 0;
        for (int j = 0; j < d; j++)
        {
            for (int i = 0; i < w; i++)
            {
                if (i + 1 < w)
                {
                    slope = Math.max(slope, Math.abs(ground[j * w + i] - ground[j * w + i + 1]));
                }
                if (j + 1 < d)
                {
                    slope = Math.max(slope, Math.abs(ground[j * w + i] - ground[(j + 1) * w + i]));
                }
            }
        }
        return new SeedStats(probe.seed, shares, other, regions, nearest, heights, peaks, min, max, hi - lo, Math.sqrt(var), slope);
    }

    /** The biome of every cell of a grid (row by row: index = row * width + column), sampled at each cell's corner. */
    static int[] biomeGrid(TerrainProbe probe, int x0, int z0, int cell, int width, int depth)
    {
        int[] grid = new int[width * depth];
        IntStream.range(0, depth).parallel().forEach(j -> {
            for (int i = 0; i < width; i++)
            {
                grid[j * width + i] = probe.biome(x0 + i * cell, z0 + j * cell);
            }
        });
        return grid;
    }

    /** The regions of one biome in a grid (4-neighbour connected), leaving out those touching the grid's edge. */
    static Regions regions(int[] grid, int width, int depth, int biome, int cell)
    {
        List<Integer> sizes = components(grid, width, depth, biome, true, null);
        if (sizes.isEmpty())
        {
            return Regions.NONE;
        }
        double[] diameters = sizes.stream().mapToDouble(cells -> 2.0D * Math.sqrt(cells * (double) cell * cell / Math.PI)).sorted().toArray();
        double total = sizes.stream().mapToDouble(Integer::doubleValue).sum();
        List<Integer> sorted = new ArrayList<>(sizes);
        sorted.sort(null);
        double running = 0.0D;
        double weighted = diameters[diameters.length - 1];
        for (int i = 0; i < sorted.size(); i++)
        {
            running += sorted.get(i);
            if (running >= total / 2.0D)
            {
                weighted = 2.0D * Math.sqrt(sorted.get(i) * (double) cell * cell / Math.PI);
                break;
            }
        }
        return new Regions(sizes.size(), diameters[diameters.length / 2], weighted, diameters[diameters.length - 1]);
    }

    /** The top of every mountain range (a Gravecrags region of at least MOUNTAIN_MIN_SIDE^2 blocks) in a height grid. */
    static List<Double> mountainPeaks(int[] biomes, double[] heights, int size)
    {
        List<int[]> members = new ArrayList<>();
        components(biomes, size, size, GRAVECRAGS, false, members);
        int minCells = MOUNTAIN_MIN_SIDE * MOUNTAIN_MIN_SIDE / (HEIGHT_CELL * HEIGHT_CELL);
        List<Double> peaks = new ArrayList<>();
        for (int[] cells : members)
        {
            if (cells.length >= minCells)
            {
                double top = Double.NEGATIVE_INFINITY;
                for (int c : cells)
                {
                    top = Math.max(top, heights[c]);
                }
                peaks.add(top);
            }
        }
        peaks.sort(null);
        return peaks;
    }

    /**
     * The 4-neighbour connected regions of {@code biome} in the grid: their sizes (cells), and if {@code members} isn't
     * null each region's cell indexes. {@code dropEdge} leaves out regions touching the grid's edge.
     */
    private static List<Integer> components(int[] grid, int width, int depth, int biome, boolean dropEdge, List<int[]> members)
    {
        boolean[] seen = new boolean[grid.length];
        int[] queue = new int[grid.length];
        List<Integer> sizes = new ArrayList<>();
        for (int start = 0; start < grid.length; start++)
        {
            if (seen[start] || grid[start] != biome)
            {
                continue;
            }
            int head = 0;
            int tail = 0;
            queue[tail++] = start;
            seen[start] = true;
            boolean edge = false;
            while (head < tail)
            {
                int c = queue[head++];
                int x = c % width;
                int z = c / width;
                if (x == 0 || z == 0 || x == width - 1 || z == depth - 1)
                {
                    edge = true;
                }
                int[] next = {x > 0 ? c - 1 : -1, x < width - 1 ? c + 1 : -1, z > 0 ? c - width : -1, z < depth - 1 ? c + width : -1};
                for (int o : next)
                {
                    if (o >= 0 && !seen[o] && grid[o] == biome)
                    {
                        seen[o] = true;
                        queue[tail++] = o;
                    }
                }
            }
            if (dropEdge && edge)
            {
                continue;
            }
            sizes.add(tail);
            if (members != null)
            {
                members.add(Arrays.copyOf(queue, tail));
            }
        }
        return sizes;
    }

    static double percentile(List<Double> values, double p)
    {
        if (values.isEmpty())
        {
            return Double.NaN;
        }
        double[] sorted = values.stream().mapToDouble(Double::doubleValue).sorted().toArray();
        int i = (int) Math.round((p / 100.0D) * (sorted.length - 1));
        return sorted[Math.max(0, Math.min(sorted.length - 1, i))];
    }
}
