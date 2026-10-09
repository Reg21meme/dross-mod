package com.reg21meme.dross.villager;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * The order in which {@link TraderSpawner} looks at villages: which ones, how many at a time, and how the search
 * widens when none of them works. It is plain Java (no Minecraft classes), so it can be tested on its own.
 * <p>
 * It holds every <i>possible</i> village spot ({@link Candidate}: a chunk where the world could have a village),
 * nearest to world spawn first. {@link #advance} turns candidates into real villages by asking the
 * {@link Resolver} (which reads the structure start and says "no village here" for most of them), nearest first,
 * until it has a <b>batch</b> of {@code perBatch} villages. Only candidates inside the current square count: at
 * first {@code firstRadius} blocks from spawn in X and Z (a square twice that wide). When no candidate is left in
 * the square the square grows by {@code step}, up to {@code maxRadius}. When even that is used up, the search is
 * exhausted.
 *
 * @param <V> a resolved village
 */
final class VillageSearchPlan<V>
{
    /**
     * A chunk where a village could be.
     * @param distance the distance from world spawn to the chunk's middle, in blocks
     * @param ring     the distance in the larger of X and Z: the half width of the square around spawn that holds it
     */
    record Candidate(int chunkX, int chunkZ, double distance, int ring)
    {
        static Candidate of(int chunkX, int chunkZ, int spawnX, int spawnZ)
        {
            int dx = chunkX * 16 + 8 - spawnX;
            int dz = chunkZ * 16 + 8 - spawnZ;
            return new Candidate(chunkX, chunkZ, Math.sqrt((double) dx * dx + (double) dz * dz),
                    Math.max(Math.abs(dx), Math.abs(dz)));
        }
    }

    /** Turns a candidate into a village, or null if there is none there. */
    interface Resolver<V>
    {
        @Nullable
        V resolve(Candidate candidate);
    }

    private final List<Candidate> remaining;
    private final Resolver<V> resolver;
    private final int step;
    private final int maxRadius;
    private final int perBatch;
    private final List<V> batch = new ArrayList<>();
    private int radius;
    private int resolved;

    /**
     * @param candidates  every possible village spot, in any order (they are sorted nearest first here)
     * @param firstRadius half width of the first square around spawn, in blocks
     * @param step        how much the square's half width grows each time it runs dry, in blocks
     * @param maxRadius   the largest half width, in blocks
     * @param perBatch    how many villages make a batch
     */
    VillageSearchPlan(List<Candidate> candidates, Resolver<V> resolver, int firstRadius, int step, int maxRadius,
                      int perBatch)
    {
        this.remaining = new ArrayList<>(candidates);
        this.remaining.sort(Comparator.comparingDouble(Candidate::distance));
        this.resolver = resolver;
        this.radius = Math.min(firstRadius, maxRadius);
        this.step = step;
        this.maxRadius = maxRadius;
        this.perBatch = perBatch;
    }

    /**
     * Resolves up to {@code budget} candidates (the ones that are cheap to rule out cost the same as real
     * villages, so a tick can't be swamped). Returns
     * <ul>
     *   <li>{@code null} if the budget ran out first: call again later;</li>
     *   <li>a non-empty list: the next batch, nearest candidate first (at most {@code perBatch}; fewer if the
     *       current square ran dry; it is never filled from a bigger square);</li>
     *   <li>an empty list: every candidate up to {@code maxRadius} has been used, so there is nothing left.</li>
     * </ul>
     */
    @Nullable
    List<V> advance(int budget)
    {
        while (true)
        {
            if (batch.size() >= perBatch)
            {
                return takeBatch();
            }
            int index = indexWithin(radius);
            if (index < 0)
            {
                if (!batch.isEmpty())
                {
                    return takeBatch();
                }
                if (radius >= maxRadius)
                {
                    return List.of();
                }
                radius = Math.min(maxRadius, radius + step);
                continue; // widening the square costs nothing
            }
            if (budget <= 0)
            {
                return null;
            }
            budget--;
            resolved++;
            V village = resolver.resolve(remaining.remove(index));
            if (village != null)
            {
                batch.add(village);
            }
        }
    }

    /** The half width of the square being searched, in blocks. */
    int radius()
    {
        return radius;
    }

    /** How many candidates have been looked at so far. */
    int resolvedCount()
    {
        return resolved;
    }

    private List<V> takeBatch()
    {
        List<V> out = new ArrayList<>(batch);
        batch.clear();
        return out;
    }

    /** The index of the nearest remaining candidate inside the square, or -1 if there is none. */
    private int indexWithin(int halfWidth)
    {
        for (int i = 0; i < remaining.size(); i++)
        {
            if (remaining.get(i).ring() <= halfWidth)
            {
                return i;
            }
        }
        return -1;
    }
}
