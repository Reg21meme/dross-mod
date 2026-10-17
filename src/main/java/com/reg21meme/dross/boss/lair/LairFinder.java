package com.reg21meme.dross.boss.lair;

import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.BossSitesTuning;
import com.reg21meme.dross.dimension.sites.Patch;
import com.reg21meme.dross.dimension.sites.SiteFinder;
import com.reg21meme.dross.dimension.sites.SiteTerrain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.BlockPos;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * The Cinder Colossus's own fit test for the boss-site planner ({@link BossSites#COLOSSUS_LAIR}, plugged in by
 * {@link LairSetup}): where in a Cinder Wastes patch its volcano goes. The planner groups the patches (3 at a time, nearest
 * the hub first) and gives each group's lair to one patch where this test says it fits; the lair is then built there the
 * first time a player comes near ({@link LairPlacer}).
 * <p>
 * The test, from the world generator's noise alone (through {@link SiteTerrain}, its quick height: no chunk is touched,
 * the same answer every time for the same patch and seed, any thread):
 * <ol>
 *   <li>The patch's deepest spots ({@link Patch#spots}, up to {@link LairTuning#FINDER_SPOTS}) where every column within
 *       {@link LairTuning#FIT_RADIUS} blocks is Cinder Wastes ({@link LairTuning#RELAXED_FIT_RADIUS} for the smallest form)
 *       fit.</li>
 *   <li>The ones that rise most above the ground round them get a full ground survey ({@link LairShape#plan}: how much
 *       natural rock stands over the dome), and the best is nudged about while that buries the dome deeper (staying in the
 *       patch and fitting).</li>
 *   <li>The volcano must be a landmark: its rim must be able to stand {@link LairTuning#RIM_RISE} blocks over the high
 *       land round it without being capped by {@link LairTuning#MAX_RIM_HEIGHT} ({@link LairShape#volcanoPlan}); if not,
 *       the next best spot is tried (up to {@link LairTuning#LANDMARK_TRIES}). The smallest form takes the best spot
 *       anyway.</li>
 * </ol>
 * The spot's y is the ground's top block there + 1 (the floor is planned when it's built, from the real ground).
 */
public final class LairFinder implements SiteFinder
{
    public static final LairFinder INSTANCE = new LairFinder();

    private LairFinder() {}

    @Override
    public Optional<Found> find(SiteTerrain terrain, Patch patch, boolean relaxed)
    {
        int radius = relaxed ? RELAXED_FIT_RADIUS : FIT_RADIUS;
        LairShape.Ground ground = quickGround(terrain);
        List<BlockPos> fitting = new ArrayList<>();
        for (BlockPos spot : patch.spots(FINDER_SPOTS, FINDER_SPOT_SPACING))
        {
            if (patch.depthAt(spot.getX(), spot.getZ()) + BossSitesTuning.CELL < radius)
            {
                continue; // (too near the edge to hold the disc: no need to sample it)
            }
            if (fits(terrain, spot.getX(), spot.getZ(), radius))
            {
                fitting.add(spot);
            }
        }
        if (fitting.isEmpty())
        {
            return Optional.empty();
        }
        // A quick look first: how far each spot's middle stands above the ground round it (ties: the deeper spot, as listed).
        List<BlockPos> keep = new ArrayList<>(fitting);
        keep.sort(Comparator.comparingInt((BlockPos p) -> -roughRise(ground, p.getX(), p.getZ())));
        keep = keep.subList(0, Math.min(PREFILTER_KEEP, keep.size()));
        List<LairShape.Plan> surveyed = new ArrayList<>();
        for (BlockPos spot : keep)
        {
            surveyed.add(LairShape.plan(spot.getX(), spot.getZ(), ground));
        }
        surveyed.sort(LairFinder::compare);
        int lowest = LairBuilder.lowestFloor(terrain.heights());
        int highest = LairBuilder.highestRim(terrain.heights());
        LairShape.Plan first = null;
        for (int i = 0; i < surveyed.size() && i < LANDMARK_TRIES; i++)
        {
            LairShape.Plan best = refine(terrain, patch, radius, ground, surveyed.get(i));
            first = first == null ? best : first;
            if (isLandmark(best.x(), best.z(), ground, lowest, highest))
            {
                return Optional.of(found(terrain, best));
            }
        }
        // No spot lets the rim stand tall enough: the smallest form takes the best one anyway; the normal test doesn't fit.
        return relaxed ? Optional.of(found(terrain, first)) : Optional.empty();
    }

    private static Found found(SiteTerrain terrain, LairShape.Plan plan)
    {
        return new Found(new BlockPos(plan.x(), terrain.topBlock(plan.x(), plan.z()) + 1, plan.z()), null);
    }

    /** Lower cost first; equal costs: the smaller x, then z (a fixed order). */
    private static int compare(LairShape.Plan a, LairShape.Plan b)
    {
        if (Math.abs(a.cost() - b.cost()) > 1.0E-9D)
        {
            return Double.compare(a.cost(), b.cost());
        }
        return a.x() != b.x() ? Integer.compare(a.x(), b.x()) : Integer.compare(a.z(), b.z());
    }

    /** Nudges a spot about (by {@link LairTuning#REFINE_STEPS}) while that buries the dome deeper, staying in the patch and fitting. */
    private static LairShape.Plan refine(SiteTerrain terrain, Patch patch, int radius, LairShape.Ground ground, LairShape.Plan start)
    {
        LairShape.Plan best = start;
        for (int step : REFINE_STEPS)
        {
            LairShape.Plan from = best;
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dz == 0)
                    {
                        continue;
                    }
                    int x = from.x() + dx * step;
                    int z = from.z() + dz * step;
                    if (!patch.contains(x, z) || patch.depthAt(x, z) + BossSitesTuning.CELL < radius || !fits(terrain, x, z, radius))
                    {
                        continue;
                    }
                    LairShape.Plan plan = LairShape.plan(x, z, ground);
                    if (plan.cost() < best.cost() - 1.0E-9D)
                    {
                        best = plan;
                    }
                }
            }
        }
        return best;
    }

    /**
     * True if the volcano planned at x, z can have its rim {@link LairTuning#RIM_RISE} over the high land round it without
     * the {@link LairTuning#MAX_RIM_HEIGHT} (or the world's top) holding it down, with {@link LairTuning#LANDMARK_MARGIN}
     * blocks to spare (the ground read here is the quick height, a block or two off the real ground in places).
     */
    static boolean isLandmark(int x, int z, LairShape.Ground ground, int lowestFloor, int highestRim)
    {
        LairShape.VolcanoPlan plan = LairShape.volcanoPlan(x, z, ground, lowestFloor, highestRim);
        int cap = Math.min(plan.floorY() + MAX_RIM_HEIGHT, highestRim);
        return plan.rimY() - plan.highLand() >= RIM_RISE && plan.highLand() + RIM_RISE + LANDMARK_MARGIN <= cap;
    }

    /**
     * The ground as the fit test reads it: the terrain's quick height ({@link SiteTerrain#height}, the top block about a
     * block under it), not the generator's exact top block (hundreds of times slower), so a group of three patches is
     * planned in about a second. The volcano itself is planned from the real ground when it's built.
     */
    static LairShape.Ground quickGround(SiteTerrain terrain)
    {
        return (x, z) -> (int) Math.floor(terrain.height(x, z)) - 1;
    }

    /** How far the middle stands above the lowest of the ground {@link LairTuning#ROUGH_RISE_DISTANCE} blocks out. */
    private static int roughRise(LairShape.Ground ground, int x, int z)
    {
        int lowest = Math.min(Math.min(ground.top(x + ROUGH_RISE_DISTANCE, z), ground.top(x - ROUGH_RISE_DISTANCE, z)),
                Math.min(ground.top(x, z + ROUGH_RISE_DISTANCE), ground.top(x, z - ROUGH_RISE_DISTANCE)));
        return ground.top(x, z) - lowest;
    }

    /**
     * True if every column within {@code radius} of {@code x}, {@code z} is Cinder Wastes: a quick check on a few rings
     * first, then rings every {@link LairTuning#FIT_FINE_STEP} blocks with samples that far apart.
     */
    static boolean fits(SiteTerrain terrain, int x, int z, int radius)
    {
        if (!terrain.isBiome(DrossBiomes.CINDER_WASTES, x, z))
        {
            return false;
        }
        for (int r = FIT_COARSE_RING_STEP; r <= radius; r += FIT_COARSE_RING_STEP)
        {
            if (!ringIsCinder(terrain, x, z, r, FIT_COARSE_RING_STEP))
            {
                return false;
            }
        }
        for (int r = FIT_FINE_STEP; r <= radius; r += FIT_FINE_STEP)
        {
            if (!ringIsCinder(terrain, x, z, r, FIT_FINE_STEP))
            {
                return false;
            }
        }
        return ringIsCinder(terrain, x, z, radius, FIT_FINE_STEP);
    }

    private static boolean ringIsCinder(SiteTerrain terrain, int x, int z, int r, int spacing)
    {
        int points = Math.max(6, (int) Math.ceil(2.0D * Math.PI * r / spacing));
        for (int i = 0; i < points; i++)
        {
            double a = 2.0D * Math.PI * i / points;
            if (!terrain.isBiome(DrossBiomes.CINDER_WASTES, (int) Math.round(x + r * Math.cos(a)), (int) Math.round(z + r * Math.sin(a))))
            {
                return false;
            }
        }
        return true;
    }
}
