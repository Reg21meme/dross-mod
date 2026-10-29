package com.reg21meme.dross.boss.emberdactyl;

import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.BossSitesTuning;
import com.reg21meme.dross.dimension.sites.Patch;
import com.reg21meme.dross.dimension.sites.SiteFinder;
import com.reg21meme.dross.dimension.sites.SiteTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;

import static com.reg21meme.dross.boss.emberdactyl.EmberVolcanoTuning.*;

/**
 * The Emberdactyl volcano's own fit test for the boss-site planner ({@link BossSites#EMBER_VOLCANO}, plugged in by
 * {@link VolcanoSetup}): where in a Cinder Wastes patch its volcano goes. The planner gives each group of 3 Cinder Wastes
 * patches one volcano, in a patch the Cinder Colossus's lair doesn't hold; it's built there the first time a player comes
 * near ({@link VolcanoPlacer}).
 * <p>
 * From the noise alone (through {@link SiteTerrain}: no chunk is touched, the same answer every time, any thread): of the
 * patch's deepest spots, those where every column within {@link EmberVolcanoTuning#FIT_RADIUS} is Cinder Wastes, whose
 * land rises at most {@link EmberVolcanoTuning#MAX_RELIEF} blocks under its foot, where the land round it stands at most
 * {@link EmberVolcanoTuning#MAX_SURROUND} over its ground (so it isn't half buried in a hollow) and whose rim stays under
 * the world's top: the one with the least relief plus twice the land standing over its ground round it wins (ties: the
 * deeper spot). Its smallest form ({@code relaxed}): a smaller radius of Cinder Wastes, more relief allowed, any
 * surroundings.
 */
public final class VolcanoFinder implements SiteFinder
{
    public static final VolcanoFinder INSTANCE = new VolcanoFinder();
    /** The land under it is read out to its widest foot (its base radius and lobes). */
    private static final double FOOT_REACH = BASE_RADIUS * 1.15D;

    private VolcanoFinder() {}

    @Override
    public Optional<Found> find(SiteTerrain terrain, Patch patch, boolean relaxed)
    {
        int radius = relaxed ? RELAXED_FIT_RADIUS : FIT_RADIUS;
        int maxRelief = relaxed ? RELAXED_MAX_RELIEF : MAX_RELIEF;
        int top = terrain.heights().getMaxBuildHeight() - 1;
        BlockPos best = null;
        int bestScore = Integer.MAX_VALUE;
        int bestBase = 0;
        for (BlockPos spot : patch.spots(FINDER_SPOTS, FINDER_SPOT_SPACING))
        {
            if (patch.depthAt(spot.getX(), spot.getZ()) + BossSitesTuning.CELL < radius)
            {
                continue;   // (too near the edge: no need to sample it)
            }
            if (!terrain.discIsBiome(DrossBiomes.CINDER_WASTES, spot.getX(), spot.getZ(), radius, BossSitesTuning.DISC_SAMPLE_STEP))
            {
                continue;
            }
            int[] land = land(terrain, spot.getX(), spot.getZ(), 0.0D, FOOT_REACH);
            int relief = share(land, 0.9D) - share(land, 0.1D);
            // its ground: the middle of the land round its foot (as EmberVolcano.planOnLand picks it); the land round it
            int base = share(land(terrain, spot.getX(), spot.getZ(), BASE_RADIUS * 0.95D, BASE_RADIUS * 1.06D), LAND_BASE_SHARE);
            int surround = share(land(terrain, spot.getX(), spot.getZ(), BASE_RADIUS * 1.05D, BASE_RADIUS * 1.5D), 0.75D) - base;
            if (relief > maxRelief || !relaxed && surround > MAX_SURROUND || base + HEIGHT + CLEAR_ABOVE > top)
            {
                continue;
            }
            int score = relief + 2 * Math.max(0, surround);
            if (score < bestScore)
            {
                best = spot;
                bestScore = score;
                bestBase = base;
            }
        }
        return best == null ? Optional.empty() : Optional.of(new Found(best.atY(bestBase + 1), null));
    }

    /**
     * The land's quick heights (top blocks) between {@code from} and {@code to} blocks of (x, z), every
     * {@link EmberVolcanoTuning#FIT_SAMPLE_STEP} blocks, sorted.
     */
    private static int[] land(SiteTerrain terrain, int x, int z, double from, double to)
    {
        List<Integer> heights = new ArrayList<>();
        int reach = (int) Math.ceil(to);
        for (int dx = -reach; dx <= reach; dx += FIT_SAMPLE_STEP)
        {
            for (int dz = -reach; dz <= reach; dz += FIT_SAMPLE_STEP)
            {
                int d2 = dx * dx + dz * dz;
                if (d2 >= from * from && d2 <= to * to)
                {
                    heights.add((int) Math.floor(terrain.height(x + dx, z + dz)) - 1);
                }
            }
        }
        return heights.stream().mapToInt(Integer::intValue).sorted().toArray();
    }

    /** The height this share of the way up the sorted heights. */
    private static int share(int[] sorted, double share)
    {
        return sorted[(int) Math.floor(share * (sorted.length - 1))];
    }
}
