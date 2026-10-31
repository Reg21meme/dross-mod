package com.reg21meme.dross.boss.fire.cave;

import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Patch;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.dimension.sites.SiteTerrain;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import static com.reg21meme.dross.boss.fire.cave.HermitCaveTuning.*;

/**
 * Where the hermit crab caves go in the world (the user, October 31, 2026: "1-2 caves per Cinder Wastes area"): every
 * Cinder Wastes patch of the boss-site planner ({@link BossSites}) gets one cave, and a second half the time
 * ({@link HermitCaveTuning#SECOND_CAVE_CHANCE}), each one of the seven designs at random (all equally) with its way in
 * facing any way, at least {@link HermitCaveTuning#MIN_DEPTH} blocks inside the patch, well clear of the patch's Cinder
 * Colossus lair and Emberdactyl volcano ({@link HermitCaveTuning#BOSS_CLEARANCE}; both are built later, near players)
 * and of each other, on the flattest land the seed's tries find.
 * <p>
 * Worked out from the noise alone (the planner's {@link SiteTerrain}), the same every time for a seed and any thread, and
 * remembered with the planner ({@link BossSites#cache}). The structure {@code dross:hermit_cave} starts only in a planned
 * cave's chunk ({@link HermitCavePlacement}) and builds it there with the land ({@link HermitCaveStructure}).
 */
public final class HermitCaveSites
{
    private static final String CACHE = "dross:hermit_caves";

    private HermitCaveSites() {}

    /**
     * One cave in the world: its middle column (y: the land's top block there, from the noise), which design, which way
     * its way in faces, and the patch it's in.
     */
    public record CavePlan(BlockPos middle, int design, Direction front, long patch)
    {
        public HermitCaveDesigns.Cave cave()
        {
            return HermitCaveDesigns.CAVES.get(this.design);
        }

        public ChunkPos chunk()
        {
            return new ChunkPos(this.middle);
        }
    }

    /** The caves of a Cinder Wastes patch (one or two; none only if nowhere in it fits). */
    @SuppressWarnings("unchecked")
    public static List<CavePlan> plan(BossSites sites, Patch patch)
    {
        Map<Long, Object> cache = sites.cache(CACHE);
        Object known = cache.get(patch.id());
        if (known != null)
        {
            return (List<CavePlan>) known;
        }
        List<CavePlan> made = make(sites, patch);
        Object previous = cache.putIfAbsent(patch.id(), made);
        return previous != null ? (List<CavePlan>) previous : made;
    }

    /** The cave whose middle is in this chunk (its structure's start chunk), if any. World generation calls it. */
    public static Optional<CavePlan> caveAt(BossSites sites, ChunkPos chunk)
    {
        Optional<Patch> patch = sites.patchAt(DrossBiomes.CINDER_WASTES, chunk.getMiddleBlockX(), chunk.getMiddleBlockZ());
        if (patch.isEmpty())
        {
            return Optional.empty();
        }
        for (CavePlan cave : plan(sites, patch.get()))
        {
            if (cave.chunk().equals(chunk))
            {
                return Optional.of(cave);
            }
        }
        return Optional.empty();
    }

    /**
     * The planned cave nearest to {@code from} (along the ground) within {@code maxDistance} blocks: the Cinder Wastes
     * patches are read nearest the hub first, until none further on could hold a nearer one.
     */
    public static Optional<CavePlan> nearest(BossSites sites, BlockPos from, int maxDistance)
    {
        double fromHub = sites.hubDistance(from.getX(), from.getZ());
        CavePlan best = null;
        double bestDistance = maxDistance;
        for (int n = 1; ; n++)
        {
            Optional<Patch> patch = sites.patch(DrossBiomes.CINDER_WASTES, n);
            if (patch.isEmpty())
            {
                break;
            }
            // a patch's nearest point is this far from the hub; anything in it is at least that minus where we are
            if (patch.get().distance() - fromHub > bestDistance)
            {
                break;
            }
            Patch p = patch.get();
            double boxX = Math.max(0, Math.max(p.minX() - from.getX(), from.getX() - p.maxX()));
            double boxZ = Math.max(0, Math.max(p.minZ() - from.getZ(), from.getZ() - p.maxZ()));
            if (Math.hypot(boxX, boxZ) >= bestDistance)
            {
                continue;   // (nothing in it could be nearer: no need to plan it)
            }
            for (CavePlan cave : plan(sites, p))
            {
                double d = Math.hypot(cave.middle().getX() - from.getX(), cave.middle().getZ() - from.getZ());
                if (d < bestDistance)
                {
                    bestDistance = d;
                    best = cave;
                }
            }
        }
        return Optional.ofNullable(best);
    }

    // ---------------------------------------------------------------------------------------------

    private record Candidate(int x, int z, int relief, int top, int order)
    {
    }

    private static RandomSource random(BossSites sites, Patch patch)
    {
        return new LegacyRandomSource(sites.seed() ^ SALT ^ (patch.id() * 0x9E3779B97F4A7C15L));
    }

    /** True if the patch rolled a second cave (whether or not one fits): for the headless check's log. */
    static boolean wantsTwo(BossSites sites, Patch patch)
    {
        return random(sites, patch).nextDouble() < SECOND_CAVE_CHANCE;
    }

    private static List<CavePlan> make(BossSites sites, Patch patch)
    {
        SiteTerrain terrain = sites.terrain();
        RandomSource random = random(sites, patch);
        int wanted = 1 + (random.nextDouble() < SECOND_CAVE_CHANCE ? 1 : 0);
        List<BlockPos> bosses = new ArrayList<>();
        sites.site(BossSites.COLOSSUS_LAIR, patch).map(Site::pos).ifPresent(bosses::add);
        sites.site(BossSites.EMBER_VOLCANO, patch).map(Site::pos).ifPresent(bosses::add);
        int spanX = patch.maxX() - patch.minX() + 1;
        int spanZ = patch.maxZ() - patch.minZ() + 1;
        List<int[]> spots = new ArrayList<>();
        for (int i = 0; i < TRIES; i++)
        {
            spots.add(new int[] {patch.minX() + random.nextInt(spanX), patch.minZ() + random.nextInt(spanZ)});
        }
        // the designs and facings are rolled up front, so they don't depend on which spots fit
        int[] designs = {random.nextInt(HermitCaveDesigns.CAVES.size()), random.nextInt(HermitCaveDesigns.CAVES.size())};
        Direction[] fronts = {Direction.from2DDataValue(random.nextInt(4)), Direction.from2DDataValue(random.nextInt(4))};
        // the normal rules first; if they leave the patch short, the looser ones (deeper in fewer patches, nearer a boss)
        List<CavePlan> caves = new ArrayList<>();
        pick(caves, wanted, candidates(terrain, patch, spots, bosses, MIN_DEPTH, BOSS_CLEARANCE), designs, fronts, patch);
        if (caves.size() < wanted)
        {
            pick(caves, wanted, candidates(terrain, patch, spots, bosses, RELAXED_MIN_DEPTH, RELAXED_BOSS_CLEARANCE), designs,
                    fronts, patch);
        }
        return List.copyOf(caves);
    }

    /** Adds caves at the flattest of the spots (then in the seed's order) until there are `wanted`, each apart from the
     * others. */
    private static void pick(List<CavePlan> caves, int wanted, List<Candidate> fitting, int[] designs, Direction[] fronts,
                             Patch patch)
    {
        fitting.sort(Comparator.comparingInt((Candidate c) -> c.relief() > MAX_RELIEF ? 1 : 0)
                .thenComparingInt(Candidate::order));
        for (Candidate c : fitting)
        {
            if (caves.size() >= wanted)
            {
                break;
            }
            boolean apart = true;
            for (CavePlan other : caves)
            {
                if (Math.hypot(other.middle().getX() - c.x(), other.middle().getZ() - c.z()) < CAVE_SPACING)
                {
                    apart = false;
                    break;
                }
            }
            if (apart)
            {
                int k = caves.size();
                caves.add(new CavePlan(new BlockPos(c.x(), c.top(), c.z()), designs[k], fronts[k], patch.id()));
            }
        }
    }

    private static List<Candidate> candidates(SiteTerrain terrain, Patch patch, List<int[]> spots, List<BlockPos> bosses,
                                              int minDepth, int clearance)
    {
        List<Candidate> out = new ArrayList<>();
        for (int i = 0; i < spots.size(); i++)
        {
            int x = spots.get(i)[0];
            int z = spots.get(i)[1];
            if (!patch.contains(x, z) || patch.depthAt(x, z) < minDepth)
            {
                continue;
            }
            boolean clear = true;
            for (BlockPos boss : bosses)
            {
                if (Math.hypot(boss.getX() - x, boss.getZ() - z) < clearance)
                {
                    clear = false;
                    break;
                }
            }
            if (!clear || !terrain.isBiome(DrossBiomes.CINDER_WASTES, x, z))
            {
                continue;
            }
            int lo = Integer.MAX_VALUE;
            int hi = Integer.MIN_VALUE;
            for (int dx = -RELIEF_REACH; dx <= RELIEF_REACH; dx += RELIEF_REACH)
            {
                for (int dz = -RELIEF_REACH; dz <= RELIEF_REACH; dz += RELIEF_REACH)
                {
                    int h = (int) Math.floor(terrain.height(x + dx, z + dz));
                    lo = Math.min(lo, h);
                    hi = Math.max(hi, h);
                }
            }
            out.add(new Candidate(x, z, hi - lo, terrain.topBlock(x, z), i));
        }
        return out;
    }
}
