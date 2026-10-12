package com.reg21meme.dross.boss.lair;

import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.portal.DrossHub;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.BooleanSupplier;
import net.minecraft.Util;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * Finds where the Colossus's lair goes, from the world generator's noise alone (no chunks are generated, so it can run
 * on a background thread while the game goes on):
 * <ol>
 *   <li>Three directions from the hub ({@link DrossHub#CENTER}, 0,0), 120 degrees apart, turned by an angle from the
 *       world seed.</li>
 *   <li>Along each, in a cone {@link LairTuning#CONE_HALF_ANGLE_DEGREES} degrees either side, walking outwards: the
 *       first spot where the whole lair (with a margin, {@link LairTuning#FIT_RADIUS}) lies in the Cinder Wastes is the
 *       nearest Cinder Wastes in that direction. The spots up to {@link LairTuning#WINDOW} blocks further out are
 *       compared too, and the one on the highest ground (the most natural rock over the dome: {@link LairShape#plan})
 *       wins, nudged a little to bury the dome deeper.</li>
 *   <li>Of the three, the one furthest from the hub is the lair's spot (the user's rule).</li>
 * </ol>
 * The world's own noise ({@link #forWorld}) or any seed's ({@link #forSeed}, for the headless checks).
 */
final class LairSearch
{
    private LairSearch() {}

    /** What the search reads: the Dross's biome source and terrain noise for one seed. */
    record Context(BiomeSource biomes, Climate.Sampler sampler, NoiseBasedChunkGenerator generator, RandomState random,
                   LevelHeightAccessor heights, long seed, int hubX, int hubZ)
    {
        /** True if the column is in the Cinder Wastes. */
        boolean isCinder(int x, int z)
        {
            return biomes.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(BIOME_Y), QuartPos.fromBlock(z), sampler)
                    .is(DrossBiomes.CINDER_WASTES);
        }

        /** The Y of the column's top ground block, from the terrain noise (before plants, trees and lava pools). */
        int ground(int x, int z)
        {
            return generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, heights, random) - 1;
        }
    }

    /** The running world's own terrain. Server thread (it only reads finished objects, so the search can then run anywhere). */
    static Context forWorld(ServerLevel dross)
    {
        NoiseBasedChunkGenerator generator = generatorOf(dross);
        RandomState random = dross.getChunkSource().randomState();
        return new Context(generator.getBiomeSource(), random.sampler(), generator, random, dross, dross.getSeed(),
                DrossHub.CENTER.getX(), DrossHub.CENTER.getZ());
    }

    /** The terrain a world with this seed would have (for the headless checks). */
    static Context forSeed(ServerLevel dross, long seed)
    {
        NoiseBasedChunkGenerator generator = generatorOf(dross);
        RandomState random = RandomState.create(generator.generatorSettings().value(),
                dross.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        return new Context(generator.getBiomeSource(), random.sampler(), generator, random, dross, seed,
                DrossHub.CENTER.getX(), DrossHub.CENTER.getZ());
    }

    private static NoiseBasedChunkGenerator generatorOf(ServerLevel dross)
    {
        if (!(dross.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator generator))
        {
            throw new IllegalStateException("the Dross isn't generated from noise (" + dross.getChunkSource().getGenerator() + ")");
        }
        return generator;
    }

    /**
     * The best spot found in one direction.
     *
     * @param direction  0, 1 or 2
     * @param degrees    the direction's angle (0 = east, 90 = south, 180 = west, 270 = north)
     * @param distance   the spot's distance from the hub
     * @param plan       the lair's plan there
     * @param fitting    how many spots in the window fit (all Cinder Wastes)
     * @param surveyed   how many of them got a full ground survey
     * @param firstFit   the distance of the first spot that fit (the nearest Cinder Wastes that can hold the lair)
     */
    record Candidate(int direction, double degrees, double distance, LairShape.Plan plan, int fitting, int surveyed, int firstFit)
    {
        int x()
        {
            return plan.x();
        }

        int z()
        {
            return plan.z();
        }

        String describe()
        {
            return String.format("%d, %d, %d blocks from the hub (the nearest Cinder Wastes that fits starts %d blocks out; "
                            + "%d spots fit, %d surveyed): %s",
                    x(), z(), Math.round(distance), firstFit, fitting, surveyed, plan.describe());
        }
    }

    /**
     * The search's result.
     *
     * @param rotation   the seed's turn of the three directions (degrees, 0 to 120)
     * @param directions the best spot in each direction (empty where none was found within {@link LairTuning#MAX_DISTANCE})
     * @param pick       the one furthest from the hub (empty if none was found at all)
     * @param millis     how long the search took
     * @param cancelled  true if it was stopped early (the server stopped)
     */
    record Result(double rotation, List<Optional<Candidate>> directions, Optional<Candidate> pick, long millis, boolean cancelled)
    {
    }

    /** The three directions' angles for a seed (degrees: 0 = east, 90 = south). */
    static double[] directionAngles(long seed)
    {
        double rotation = rotation(seed);
        double[] angles = new double[DIRECTIONS];
        for (int i = 0; i < DIRECTIONS; i++)
        {
            angles[i] = rotation + i * DIRECTION_SPACING_DEGREES;
        }
        return angles;
    }

    /** The seed's turn of the directions, 0 to 120 degrees. */
    static double rotation(long seed)
    {
        return RandomSource.create(seed ^ ROTATION_SALT).nextDouble() * DIRECTION_SPACING_DEGREES;
    }

    /** Runs the whole search. Any thread. */
    static Result run(Context context, BooleanSupplier cancelled)
    {
        long start = Util.getMillis();
        double[] angles = directionAngles(context.seed());
        List<Optional<Candidate>> found = new ArrayList<>();
        for (int i = 0; i < angles.length; i++)
        {
            if (cancelled.getAsBoolean())
            {
                return new Result(rotation(context.seed()), found, Optional.empty(), Util.getMillis() - start, true);
            }
            found.add(searchDirection(context, i, angles[i], cancelled));
        }
        Optional<Candidate> pick = found.stream().flatMap(Optional::stream).max(Comparator.comparingDouble(Candidate::distance));
        return new Result(rotation(context.seed()), found, pick, Util.getMillis() - start, cancelled.getAsBoolean());
    }

    /** The best spot in one direction's cone, or empty. */
    static Optional<Candidate> searchDirection(Context context, int index, double degrees, BooleanSupplier cancelled)
    {
        Map<Long, Integer> groundCache = new HashMap<>();
        LairShape.Ground ground = (x, z) -> groundCache.computeIfAbsent(((long) x << 32) ^ (z & 0xFFFFFFFFL), k -> context.ground(x, z));
        double centre = Math.toRadians(degrees);
        double half = Math.toRadians(CONE_HALF_ANGLE_DEGREES);
        int firstFit = -1;
        List<int[]> fitting = new ArrayList<>();
        for (int d = MIN_DISTANCE; d <= MAX_DISTANCE; d += DISTANCE_STEP)
        {
            if (firstFit >= 0 && d > firstFit + WINDOW)
            {
                break;
            }
            if (cancelled.getAsBoolean())
            {
                return Optional.empty();
            }
            int across = Math.max(1, (int) Math.ceil(2.0D * d * Math.tan(half) / LATERAL_STEP));
            for (int i = 0; i <= across; i++)
            {
                double angle = centre - half + 2.0D * half * i / across;
                int x = (int) Math.round(context.hubX() + d * Math.cos(angle));
                int z = (int) Math.round(context.hubZ() + d * Math.sin(angle));
                if (fits(context, x, z))
                {
                    if (firstFit < 0)
                    {
                        firstFit = d;
                    }
                    fitting.add(new int[] {x, z});
                }
            }
        }
        if (fitting.isEmpty())
        {
            return Optional.empty();
        }
        // A quick look first: how far each spot's middle stands above the ground round it.
        List<int[]> keep = new ArrayList<>(fitting);
        keep.sort(Comparator.comparingInt((int[] spot) -> -roughRise(ground, spot[0], spot[1])));
        keep = keep.subList(0, Math.min(PREFILTER_KEEP, keep.size()));
        LairShape.Plan best = null;
        int surveyed = 0;
        for (int[] spot : keep)
        {
            LairShape.Plan plan = LairShape.plan(spot[0], spot[1], ground);
            surveyed++;
            if (best == null || better(context, plan, best))
            {
                best = plan;
            }
        }
        // Then nudge the best one about while that buries the dome deeper.
        for (int step : REFINE_STEPS)
        {
            LairShape.Plan start = best;
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    if (dx == 0 && dz == 0 || cancelled.getAsBoolean())
                    {
                        continue;
                    }
                    int x = start.x() + dx * step;
                    int z = start.z() + dz * step;
                    if (distance(context, x, z) < MIN_DISTANCE || !fits(context, x, z))
                    {
                        continue;
                    }
                    LairShape.Plan plan = LairShape.plan(x, z, ground);
                    surveyed++;
                    if (plan.cost() < best.cost() - 1.0E-9D)
                    {
                        best = plan;
                    }
                }
            }
        }
        return Optional.of(new Candidate(index, normalise(degrees), distance(context, best.x(), best.z()), best, fitting.size(),
                surveyed, firstFit));
    }

    /** True if {@code plan} beats {@code than}: a lower cost, or the same cost nearer the hub. */
    private static boolean better(Context context, LairShape.Plan plan, LairShape.Plan than)
    {
        if (Math.abs(plan.cost() - than.cost()) > 1.0E-9D)
        {
            return plan.cost() < than.cost();
        }
        return distance(context, plan.x(), plan.z()) < distance(context, than.x(), than.z());
    }

    /** How far the middle stands above the lowest of the ground {@link LairTuning#ROUGH_RISE_DISTANCE} blocks out. */
    private static int roughRise(LairShape.Ground ground, int x, int z)
    {
        int lowest = Math.min(Math.min(ground.top(x + ROUGH_RISE_DISTANCE, z), ground.top(x - ROUGH_RISE_DISTANCE, z)),
                Math.min(ground.top(x, z + ROUGH_RISE_DISTANCE), ground.top(x, z - ROUGH_RISE_DISTANCE)));
        return ground.top(x, z) - lowest;
    }

    /**
     * True if every column within {@link LairTuning#FIT_RADIUS} of {@code x}, {@code z} is Cinder Wastes: a quick
     * check on a few rings first, then rings every {@link LairTuning#FIT_FINE_STEP} blocks with samples that far apart.
     */
    static boolean fits(Context context, int x, int z)
    {
        if (!context.isCinder(x, z))
        {
            return false;
        }
        for (int r = FIT_COARSE_RING_STEP; r <= FIT_RADIUS; r += FIT_COARSE_RING_STEP)
        {
            if (!ringIsCinder(context, x, z, r, FIT_COARSE_RING_STEP))
            {
                return false;
            }
        }
        for (int r = FIT_FINE_STEP; r <= FIT_RADIUS; r += FIT_FINE_STEP)
        {
            if (!ringIsCinder(context, x, z, r, FIT_FINE_STEP))
            {
                return false;
            }
        }
        return ringIsCinder(context, x, z, FIT_RADIUS, FIT_FINE_STEP);
    }

    private static boolean ringIsCinder(Context context, int x, int z, int r, int spacing)
    {
        int points = Math.max(6, (int) Math.ceil(2.0D * Math.PI * r / spacing));
        for (int i = 0; i < points; i++)
        {
            double a = 2.0D * Math.PI * i / points;
            if (!context.isCinder((int) Math.round(x + r * Math.cos(a)), (int) Math.round(z + r * Math.sin(a))))
            {
                return false;
            }
        }
        return true;
    }

    static double distance(Context context, int x, int z)
    {
        double dx = x - context.hubX();
        double dz = z - context.hubZ();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** An angle as 0 to 360 degrees. */
    static double normalise(double degrees)
    {
        double d = degrees % 360.0D;
        return d < 0.0D ? d + 360.0D : d;
    }

    /** A compass name for an angle (0 = east, 90 = south), for the log. */
    static String compass(double degrees)
    {
        String[] names = {"east", "south-east", "south", "south-west", "west", "north-west", "north", "north-east"};
        return names[(int) Math.floor(normalise(degrees + 22.5D) / 45.0D) % 8];
    }
}
