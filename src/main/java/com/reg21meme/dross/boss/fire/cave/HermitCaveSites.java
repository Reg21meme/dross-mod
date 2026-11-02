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
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.LegacyRandomSource;

import static com.reg21meme.dross.boss.fire.cave.HermitCaveTuning.*;

/**
 * Where the hermit crab caves go in the world (the user, October 31, 2026: "1-2 caves per Cinder Wastes area"): every
 * Cinder Wastes patch of the boss-site planner ({@link BossSites}) gets one cave, and a second half the time
 * ({@link HermitCaveTuning#SECOND_CAVE_CHANCE}), at least {@link HermitCaveTuning#MIN_DEPTH} blocks inside the patch,
 * well clear of the patch's Cinder Colossus lair and Emberdactyl volcano ({@link HermitCaveTuning#BOSS_CLEARANCE}; both
 * are built later, near players) and of each other.
 * <p>
 * Since round 2 (the user, November 2, 2026: "there's a hill, and he builds inside the hill ... we can have some
 * underground ones") a cave is one of two kinds ({@link HermitCaveDesigns.Kind}). A <b>hillside</b> cave goes where the
 * land really rises behind its way in (screened with the noise's quick height, the best then checked against the real
 * ground, the generator's own top blocks: {@link #hillCover}; the land over its den's middle at least
 * {@link HermitCaveTuning#HILL_MIN_RISE} over the ground at its way in, its rooms mostly under the hill, a gentle walk up
 * to its way in), facing out of the slope (its way in on the downhill side): of every spot and facing that fits, the one
 * with the hill fullest over it. An <b>underground</b> one goes on the flattest land the seed's tries find, its way in
 * facing any way. Each cave is underground {@link HermitCaveTuning#UNDERGROUND_CHANCE} of the time when one fits, else
 * hillside when one fits; a patch with no hillside gets underground ones. Its design (of its kind) is picked at random.
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
        return nearest(sites, from, maxDistance, cave -> true);
    }

    /** The nearest planned cave that {@code which} accepts (for example only hillside ones), like {@link #nearest}. */
    public static Optional<CavePlan> nearest(BossSites sites, BlockPos from, int maxDistance, Predicate<CavePlan> which)
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
                if (!which.test(cave))
                {
                    continue;
                }
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

    private record Candidate(int x, int z, Direction front, double score, int order)
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
        for (int i = 0; i < Math.max(TRIES, HILL_TRIES); i++)
        {
            spots.add(new int[] {patch.minX() + random.nextInt(spanX), patch.minZ() + random.nextInt(spanZ)});
        }
        // the designs, the underground caves' facings and which kind each cave tries first are rolled up front, so
        // they don't depend on which spots fit
        List<Integer> hills = HermitCaveDesigns.indexesOf(HermitCaveDesigns.Kind.HILLSIDE);
        List<Integer> flats = HermitCaveDesigns.indexesOf(HermitCaveDesigns.Kind.UNDERGROUND);
        int[] hillDesign = new int[2];
        int[] flatDesign = new int[2];
        Direction[] flatFront = new Direction[2];
        boolean[] flatFirst = new boolean[2];
        for (int k = 0; k < 2; k++)
        {
            hillDesign[k] = hills.get(random.nextInt(hills.size()));
            flatDesign[k] = flats.get(random.nextInt(flats.size()));
            flatFront[k] = Direction.from2DDataValue(random.nextInt(4));
            flatFirst[k] = random.nextDouble() < UNDERGROUND_CHANCE;
        }
        // the normal rules first; if they leave the patch short, the looser ones (deeper in fewer patches, nearer a boss)
        List<CavePlan> caves = new ArrayList<>();
        for (int pass = 0; pass < 2 && caves.size() < wanted; pass++)
        {
            int minDepth = pass == 0 ? MIN_DEPTH : RELAXED_MIN_DEPTH;
            int clearance = pass == 0 ? BOSS_CLEARANCE : RELAXED_BOSS_CLEARANCE;
            while (caves.size() < wanted)
            {
                int k = caves.size();
                Supplier<Optional<CavePlan>> hill = () -> hillside(terrain, patch, spots, bosses, caves, hillDesign[k],
                        minDepth, clearance);
                Supplier<Optional<CavePlan>> flat = () -> underground(terrain, patch, spots, bosses, caves, flatDesign[k],
                        flatFront[k], minDepth, clearance);
                Optional<CavePlan> next = flatFirst[k] ? flat.get().or(hill) : hill.get().or(flat);
                if (next.isEmpty())
                {
                    break;
                }
                caves.add(next.get());
            }
        }
        return List.copyOf(caves);
    }

    /** True if a cave's middle here is far enough inside the patch, clear of its bosses and the caves already planned. */
    private static boolean allowed(SiteTerrain terrain, Patch patch, int x, int z, List<BlockPos> bosses, List<CavePlan> caves,
                                   int minDepth, int clearance)
    {
        if (!patch.contains(x, z) || patch.depthAt(x, z) < minDepth)
        {
            return false;
        }
        for (BlockPos boss : bosses)
        {
            if (Math.hypot(boss.getX() - x, boss.getZ() - z) < clearance)
            {
                return false;
            }
        }
        for (CavePlan other : caves)
        {
            if (Math.hypot(other.middle().getX() - x, other.middle().getZ() - z) < CAVE_SPACING)
            {
                return false;
            }
        }
        return terrain.isBiome(DrossBiomes.CINDER_WASTES, x, z);
    }

    /**
     * A hillside cave of this design: of the spots and facings where the land rises behind its way in
     * ({@link #hillCover}), the one with the hill fullest over it (then the seed's order).
     */
    private static Optional<CavePlan> hillside(SiteTerrain terrain, Patch patch, List<int[]> spots, List<BlockPos> bosses,
                                               List<CavePlan> caves, int design, int minDepth, int clearance)
    {
        HermitCaveDesigns.Cave cave = HermitCaveDesigns.CAVES.get(design);
        List<Candidate> screened = new ArrayList<>();
        for (int i = 0; i < Math.min(spots.size(), HILL_TRIES); i++)
        {
            int x = spots.get(i)[0];
            int z = spots.get(i)[1];
            if (!allowed(terrain, patch, x, z, bosses, caves, minDepth, clearance))
            {
                continue;
            }
            double middle = terrain.height(x, z);
            for (Direction front : Direction.Plane.HORIZONTAL)
            {
                BlockPos door = HermitCaves.door(new BlockPos(x, 0, z), front, cave);
                double atDoor = terrain.height(door.getX(), door.getZ());
                if (middle - atDoor < HILL_QUICK_RISE)
                {
                    continue;
                }
                double cover = hillCover(terrain::height, cave, x, z, front);
                if (cover >= HILL_MIN_COVER)
                {
                    screened.add(new Candidate(x, z, front, cover, i));
                }
            }
        }
        // the best by the quick height, then checked against the real ground: the fullest hill of those wins
        screened.sort(Comparator.comparingDouble((Candidate c) -> -c.score()).thenComparingInt(Candidate::order));
        Candidate best = null;
        double bestCover = -1.0D;
        for (int i = 0; i < Math.min(screened.size(), HILL_VERIFY); i++)
        {
            Candidate c = screened.get(i);
            double cover = hillCover(terrain, cave, c.x(), c.z(), c.front());
            if (cover >= HILL_MIN_COVER && cover > bestCover)
            {
                best = c;
                bestCover = cover;
            }
        }
        if (best == null)
        {
            return Optional.empty();
        }
        return Optional.of(new CavePlan(new BlockPos(best.x(), terrain.topBlock(best.x(), best.z()), best.z()), design,
                best.front(), patch.id()));
    }

    /** Ground heights for the fit (only their differences count). */
    @FunctionalInterface
    private interface Heights
    {
        double at(int x, int z);
    }

    /**
     * How well a hillside cave with its middle at x, z and its way in facing {@code front} sits in the real land (the
     * generator's own top blocks, from the noise: exactly the ground it's built on) ({@link #hillCover(Heights,
     * HermitCaveDesigns.Cave, int, int, Direction)}).
     */
    public static double hillCover(SiteTerrain terrain, HermitCaveDesigns.Cave cave, int x, int z, Direction front)
    {
        return hillCover((px, pz) -> terrain.topBlock(px, pz), cave, x, z, front);
    }

    /**
     * How well a hillside cave with its middle at x, z and its way in facing {@code front} sits in the land (by these
     * heights): -1 if it doesn't fit (the land over its den's middle less than {@link HermitCaveTuning#HILL_MIN_RISE} over
     * the ground at its way in, the land in front of its way in too low or too high, or the land under it anywhere lower
     * than its foundation reaches), else how much of its shell the hill covers over its rooms, on average (0 to 1).
     */
    private static double hillCover(Heights heights, HermitCaveDesigns.Cave cave, int x, int z, Direction front)
    {
        BlockPos ground = new BlockPos(x, 0, z);
        BlockPos door = HermitCaves.door(ground, front, cave);
        double atDoor = heights.at(door.getX(), door.getZ());
        BlockPos out = door.relative(front, APPROACH_OUT);
        double atOut = heights.at(out.getX(), out.getZ());
        if (atOut < atDoor - APPROACH_DROP || atOut > atDoor + APPROACH_RISE)
        {
            return -1.0D;
        }
        double need = Math.max(1, cave.roofRise());
        double cover = 0.0D;
        for (int i = 0; i < cave.rooms().size(); i++)
        {
            BlockPos room = HermitCaves.toWorld(cave.rooms().get(i), ground, front, cave);
            double rise = heights.at(room.getX(), room.getZ()) - atDoor;
            if (i == 0 && rise < HILL_MIN_RISE)
            {
                return -1.0D;
            }
            cover += Math.max(0.0D, Math.min(rise, need)) / need;
        }
        int w = cave.width() - 1;
        int d = cave.depth() - 1;
        for (int[] corner : new int[][] {{0, 0}, {w, 0}, {0, d}, {w, d}, {w / 2, 0}, {0, d / 2}, {w, d / 2}})
        {
            BlockPos p = HermitCaves.toWorld(new BlockPos(corner[0], 0, corner[1]), ground, front, cave);
            if (heights.at(p.getX(), p.getZ()) < atDoor - (cave.groundLayer() - 1))
            {
                return -1.0D;
            }
        }
        return cover / cave.rooms().size();
    }

    /** An underground cave of this design: on the flattest of the spots that fit (then in the seed's order). */
    private static Optional<CavePlan> underground(SiteTerrain terrain, Patch patch, List<int[]> spots, List<BlockPos> bosses,
                                                  List<CavePlan> caves, int design, Direction front, int minDepth,
                                                  int clearance)
    {
        Candidate best = null;
        int bestRelief = Integer.MAX_VALUE;
        for (int i = 0; i < Math.min(spots.size(), TRIES); i++)
        {
            int x = spots.get(i)[0];
            int z = spots.get(i)[1];
            if (!allowed(terrain, patch, x, z, bosses, caves, minDepth, clearance))
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
            int relief = Math.max(0, hi - lo - MAX_RELIEF);      // within MAX_RELIEF counts as flat: the seed's order
            if (best == null || relief < bestRelief)
            {
                best = new Candidate(x, z, front, -relief, i);
                bestRelief = relief;
            }
        }
        if (best == null)
        {
            return Optional.empty();
        }
        return Optional.of(new CavePlan(new BlockPos(best.x(), terrain.topBlock(best.x(), best.z()), best.z()), design, front,
                patch.id()));
    }
}
