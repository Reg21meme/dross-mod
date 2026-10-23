package com.reg21meme.dross.dimension.rimefall;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.rimefall.PondSkater;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Patch;
import com.reg21meme.dross.dimension.sites.SiteFinder;
import com.reg21meme.dross.dimension.sites.SiteTerrain;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.AABB;

/**
 * Rimefall's areas (CLAUDE.md, "The plan", step 14; the user's requests of October 19, 2026): each Rimefall patch (the
 * boss-site planner's patches, {@link BossSites}) gets
 * <ul>
 *   <li><b>one big frozen lake</b> ({@link RimefallLake}), about 100 blocks across, in its flattest roomy spot (a smaller
 *       one, about 64 across, where there's no room for that; none in a patch too small even for it), built by the
 *       structure {@code dross:rimefall_lake}; and</li>
 *   <li><b>a few Rimehorn herds</b> ({@link #herdCount}: 2, or 3 a quarter of the time; a small patch 1 or 2),
 *       spread out over it away from the lake, placed as the land is made ({@code dross:rimehorn_herd}).</li>
 * </ul>
 * Both are worked out from the terrain's noise alone, the same every time for the same seed: the planner's per-patch use
 * {@link BossSites#RIMEFALL_AREA} (this class's {@link #find}) holds them as one {@link RimefallArea}. The Pond Skaters'
 * spawn rule asks {@link #skaterMayAppear}.
 */
public final class RimefallAreas
{
    /** The big lake's structure ({@code data/dross/worldgen/structure/rimefall_lake.json}, written by worldgen.py). */
    public static final ResourceKey<Structure> LAKE_STRUCTURE = ResourceKey.create(Registries.STRUCTURE,
            new ResourceLocation(Dross.MODID, "rimefall_lake"));

    private RimefallAreas() {}

    /**
     * A Rimefall patch's plan: its lake (null if not even a small one fits), its herds' spots (y: on the ground), and where
     * each kind of hunter village would go in it (null: none fits; whether the patch gets one is its group's plan:
     * {@link BossSites#HUNTSMAN_VILLAGE}, {@link BossSites#HUNTER_VILLAGE}): the Huntsman's, the Huntsman's by the looser
     * rule (only worked out when his doesn't fit by the normal one), a regular one. The herds keep off all of them.
     */
    public record RimefallArea(@Nullable RimefallLake lake, List<BlockPos> herds,
                               @Nullable RimefallVillages.VillagePlan huntsmanVillage,
                               @Nullable RimefallVillages.VillagePlan huntsmanVillageRelaxed,
                               @Nullable RimefallVillages.VillagePlan hunterVillage)
    {
        List<RimefallVillages.VillagePlan> villages()
        {
            List<RimefallVillages.VillagePlan> out = new ArrayList<>(3);
            for (RimefallVillages.VillagePlan v : new RimefallVillages.VillagePlan[] {this.huntsmanVillage,
                    this.huntsmanVillageRelaxed, this.hunterVillage})
            {
                if (v != null)
                {
                    out.add(v);
                }
            }
            return out;
        }
    }

    /** Each patch's plan, once worked out (the planner asks for it as three uses: the area and the two villages). */
    private static final Map<Long, RimefallArea> AREAS = new ConcurrentHashMap<>();
    /** Past this many remembered plans the memory starts over (a world has a few hundred Rimefall patches in reach). */
    private static final int AREAS_KEPT = 8192;

    // ---------------------------------------------------------------- the plan (the planner's fit test)

    /** The planner's fit test for {@link BossSites#RIMEFALL_AREA}: every patch gets an area (its spot: the lake's middle, or its deepest point). */
    public static Optional<SiteFinder.Found> find(SiteTerrain terrain, Patch patch, boolean relaxed)
    {
        RimefallArea area = area(terrain, patch);
        BlockPos pos = area.lake() != null ? area.lake().centre() : patch.deepest().atY((int) Math.floor(
                terrain.height(patch.deepest().getX(), patch.deepest().getZ())));
        return Optional.of(new SiteFinder.Found(pos, area));
    }

    /** The planner's fit test for {@link BossSites#HUNTSMAN_VILLAGE}: where in the patch his village goes (its middle). */
    public static Optional<SiteFinder.Found> findHuntsmanVillage(SiteTerrain terrain, Patch patch, boolean relaxed)
    {
        RimefallArea area = area(terrain, patch);
        RimefallVillages.VillagePlan plan = relaxed ? area.huntsmanVillageRelaxed() : area.huntsmanVillage();
        return Optional.ofNullable(plan).map(p -> new SiteFinder.Found(p.ground(), p));
    }

    /** The planner's fit test for {@link BossSites#HUNTER_VILLAGE}: where in the patch a regular village goes. */
    public static Optional<SiteFinder.Found> findHunterVillage(SiteTerrain terrain, Patch patch, boolean relaxed)
    {
        RimefallVillages.VillagePlan plan = area(terrain, patch).hunterVillage();
        return Optional.ofNullable(plan).map(p -> new SiteFinder.Found(p.ground(), p));
    }

    /** The patch's plan (worked out once: every answer the same for the same seed and patch). */
    static RimefallArea area(SiteTerrain terrain, Patch patch)
    {
        long key = mix(terrain.seed(), patch.id());
        RimefallArea known = AREAS.get(key);
        if (known != null)
        {
            return known;
        }
        RimefallArea area = plan(terrain, patch);
        if (AREAS.size() > AREAS_KEPT)
        {
            AREAS.clear();
        }
        RimefallArea previous = AREAS.putIfAbsent(key, area);
        return previous != null ? previous : area;
    }

    private static RimefallArea plan(SiteTerrain terrain, Patch patch)
    {
        long seed = mix(terrain.seed(), patch.id());
        Heights heights = new Heights(terrain);
        // The Huntsman's village is big (about 200 x 240) and every group of three patches has to hold one (the user: "a
        // guarantee of 1"), so its spot is kept first, from the land alone, and the lake goes round it: his village's
        // places from the best, until one leaves the lake room; only a patch where none does has its lake planned
        // first instead (October 23, 2026: with the lake first, half of Rimefall's groups had no room for him).
        List<RimefallVillages.VillagePlan> options = new ArrayList<>(
                RimefallVillages.candidates(heights::top, patch, null, seed, true, false));
        options.addAll(RimefallVillages.candidates(heights::top, patch, null, seed, true, true));
        RimefallLake lake = null;
        // (the big lake round any of them first, then the small one: a village never costs a lake its size if it can help)
        for (int size = 0; size < LAKE_SIZES && lake == null; size++)
        {
            for (int i = 0; i < options.size() && i < RimefallTuning.VILLAGE_RESERVE_TRIES && lake == null; i++)
            {
                lake = findLake(heights, patch, seed, size, options.get(i));
            }
        }
        if (lake == null)
        {
            lake = findLakes(heights, patch, seed, null);
        }
        RimefallLake lakeFound = lake;
        // its villages' spots, off the lake (the user: "not on the ice lake, any other part"), then its herds off them
        RimefallVillages.VillagePlan huntsman = RimefallVillages.plan(heights::top, patch, lake, seed, true, false)
                .orElse(null);
        RimefallVillages.VillagePlan relaxedHuntsman = huntsman != null ? null
                : RimefallVillages.plan(heights::top, patch, lake, seed, true, true).orElse(null);
        RimefallVillages.VillagePlan hunter = RimefallVillages.plan(heights::top, patch, lake, seed, false, false)
                .or(() -> RimefallVillages.plan(heights::top, patch, lakeFound, seed, false, true)).orElse(null);
        List<RimefallVillages.VillagePlan> villages = new ArrayList<>(3);
        for (RimefallVillages.VillagePlan v : new RimefallVillages.VillagePlan[] {huntsman, relaxedHuntsman, hunter})
        {
            if (v != null)
            {
                villages.add(v);
            }
        }
        List<BlockPos> herds = herdSpots(heights, patch, lake, villages, seed);
        return new RimefallArea(lake, List.copyOf(herds), huntsman, relaxedHuntsman, hunter);
    }

    /** The ground's top block from the noise, remembered (the fit test asks about the same spots many times). */
    static final class Heights
    {
        private final SiteTerrain terrain;
        private final Long2IntOpenHashMap known = new Long2IntOpenHashMap();

        Heights(SiteTerrain terrain)
        {
            this.terrain = terrain;
        }

        int top(int x, int z)
        {
            long key = ChunkPos.asLong(x, z);
            if (this.known.containsKey(key))
            {
                return this.known.get(key);
            }
            int top = (int) Math.floor(this.terrain.height(x, z)) - 1;
            this.known.put(key, top);
            return top;
        }
    }

    /**
     * The lake of this radius that fits best in the patch: of its deepest spots (deep enough for the lake, its shore and
     * a margin), the one whose ground is flattest, with the ice at a low share of the ground's heights; none if every
     * spot would need cutting more than {@code maxCut} or a dam over {@code maxDam}.
     */
    @Nullable
    private static RimefallLake findLakes(Heights heights, Patch patch, long seed, @Nullable RimefallVillages.VillagePlan avoid)
    {
        RimefallLake lake = null;
        for (int size = 0; size < LAKE_SIZES && lake == null; size++)
        {
            lake = findLake(heights, patch, seed, size, avoid);
        }
        return lake;
    }

    /** The lake's sizes tried, biggest first: the big lake, the small one, the small one cutting and damming more. */
    private static final int LAKE_SIZES = 3;

    @Nullable
    private static RimefallLake findLake(Heights heights, Patch patch, long seed, int size,
                                         @Nullable RimefallVillages.VillagePlan avoid)
    {
        return switch (size)
        {
            case 0 -> findLake(heights, patch, RimefallTuning.LAKE_RADIUS, seed, RimefallTuning.MAX_CUT,
                    RimefallTuning.MAX_DAM, avoid);
            case 1 -> findLake(heights, patch, RimefallTuning.LAKE_SMALL_RADIUS, seed, RimefallTuning.MAX_CUT,
                    RimefallTuning.MAX_DAM, avoid);
            default -> findLake(heights, patch, RimefallTuning.LAKE_SMALL_RADIUS, seed, RimefallTuning.LAST_RESORT_MAX_CUT,
                    RimefallTuning.LAST_RESORT_MAX_DAM, avoid);
        };
    }

    /** As {@link #findLake(Heights, Patch, double, long, int, int, RimefallVillages.VillagePlan)} with nothing to avoid. */
    @Nullable
    static RimefallLake findLake(Heights heights, Patch patch, double radius, long seed, int maxCut, int maxDam)
    {
        return findLake(heights, patch, radius, seed, maxCut, maxDam, null);
    }

    /** ... and, with {@code avoid}, never where its edge comes within {@link RimefallTuning#VILLAGE_LAKE_CLEARANCE} of
     * that village or its blend ring. */
    @Nullable
    static RimefallLake findLake(Heights heights, Patch patch, double radius, long seed, int maxCut, int maxDam,
                                 @Nullable RimefallVillages.VillagePlan avoid)
    {
        RimefallLake shape = RimefallLake.shape(radius, seed);
        double reach = shape.reach();
        double need = reach + RimefallTuning.SHORE_WIDTH + RimefallTuning.EDGE_MARGIN;
        double bestScore = Double.MAX_VALUE;
        RimefallLake best = null;
        for (BlockPos spot : patch.spots(RimefallTuning.SITE_TRIES, RimefallTuning.SITE_TRY_SPACING))
        {
            if (patch.depthAt(spot.getX(), spot.getZ()) + 8.0D < need)
            {
                continue;
            }
            RimefallLake moved = shape.at(spot.getX(), spot.getZ(), 0);
            if (avoid != null && !RimefallVillages.clearOf(avoid, moved))
            {
                continue;
            }
            int step = RimefallTuning.SAMPLE_STEP;
            int span = (int) Math.ceil(reach) + RimefallTuning.SHORE_WIDTH;
            List<Integer> inside = new ArrayList<>();
            List<int[]> shore = new ArrayList<>();      // (top, distance past the edge)
            for (int gx = Math.floorDiv(spot.getX() - span, step) * step; gx <= spot.getX() + span; gx += step)
            {
                for (int gz = Math.floorDiv(spot.getZ() - span, step) * step; gz <= spot.getZ() + span; gz += step)
                {
                    double d = moved.outside(gx, gz);
                    if (d > RimefallTuning.SHORE_WIDTH)
                    {
                        continue;
                    }
                    int top = heights.top(gx, gz);
                    if (d < 0.0D)
                    {
                        inside.add(top);
                    }
                    else
                    {
                        shore.add(new int[] {top, (int) Math.floor(d)});
                    }
                }
            }
            if (inside.isEmpty())
            {
                continue;
            }
            int[] tops = inside.stream().mapToInt(Integer::intValue).toArray();
            Arrays.sort(tops);
            int ice = tops[(int) Math.floor((tops.length - 1) * RimefallTuning.ICE_LEVEL_SHARE)];
            if (tops[tops.length - 1] - ice > maxCut)
            {
                continue;
            }
            boolean dam = false;
            double cost = 0.0D;
            for (int top : tops)
            {
                cost += Math.abs(top - ice);
            }
            cost /= tops.length;
            double shoreCost = 0.0D;
            for (int[] s : shore)
            {
                int top = s[0];
                int d = s[1];
                if (d <= RimefallTuning.SHORE_FLAT && top < ice - maxDam)
                {
                    dam = true;
                    break;
                }
                double fade = 1.0D - (double) d / RimefallTuning.SHORE_WIDTH;
                shoreCost += Math.max(0, top - ice) * fade + Math.max(0, ice - top) * fade * 2.0D;
            }
            if (dam)
            {
                continue;
            }
            double score = cost + 0.3D * (shore.isEmpty() ? 0.0D : shoreCost / shore.size());
            if (score < bestScore)
            {
                bestScore = score;
                best = shape.at(spot.getX(), spot.getZ(), ice);
            }
        }
        return best;
    }

    /** How many herds the patch gets: 2, or 1 in a small patch, and one more a quarter of the time (fixed by the seed). */
    public static int herdCount(Patch patch, long seed)
    {
        int base = patch.area() >= RimefallTuning.HERDS_SMALL_AREA ? RimefallTuning.HERDS : RimefallTuning.HERDS - 1;
        boolean extra = new java.util.Random(seed ^ 0x48657264734C6F74L).nextDouble() < RimefallTuning.HERD_EXTRA_CHANCE;
        return Math.max(1, base) + (extra ? 1 : 0);
    }

    /** The herds' spots: the patch's deepest spots, {@link RimefallTuning#HERD_SPACING} apart, well away from the lake
     * and off the places its villages would go ({@link RimefallTuning#HERD_VILLAGE_CLEARANCE} past their blend rings). */
    static List<BlockPos> herdSpots(Heights heights, Patch patch, @Nullable RimefallLake lake,
                                    List<RimefallVillages.VillagePlan> villages, long seed)
    {
        int count = herdCount(patch, seed);
        List<BlockPos> spots = new ArrayList<>();
        for (BlockPos spot : patch.spots(RimefallTuning.HERD_TRIES, RimefallTuning.HERD_SPACING))
        {
            if (spots.size() >= count)
            {
                break;
            }
            if (lake != null && lake.outside(spot.getX(), spot.getZ()) < RimefallTuning.SHORE_WIDTH + RimefallTuning.HERD_LAKE_CLEARANCE)
            {
                continue;
            }
            if (inVillage(villages, spot))
            {
                continue;
            }
            spots.add(spot.atY(heights.top(spot.getX(), spot.getZ()) + 1));
        }
        if (spots.isEmpty())
        {
            // A small patch its lake fills: one herd at the spot furthest from the lake, off its shore.
            BlockPos best = null;
            double far = RimefallTuning.SHORE_WIDTH + RimefallTuning.HERD_LAKE_CLEARANCE_LAST;
            for (BlockPos spot : patch.spots(RimefallTuning.HERD_LAST_TRIES, RimefallTuning.HERD_LAST_SPACING))
            {
                double out = lake == null ? Double.MAX_VALUE : lake.outside(spot.getX(), spot.getZ());
                if (inVillage(villages, spot))
                {
                    continue;
                }
                if (out >= far)
                {
                    far = out;
                    best = spot;
                    if (lake == null)
                    {
                        break;
                    }
                }
            }
            if (best != null)
            {
                spots.add(best.atY(heights.top(best.getX(), best.getZ()) + 1));
            }
        }
        return spots;
    }

    private static boolean inVillage(List<RimefallVillages.VillagePlan> villages, BlockPos spot)
    {
        for (RimefallVillages.VillagePlan v : villages)
        {
            if (v.covers(spot.getX(), spot.getZ(), RimefallTuning.HERD_VILLAGE_CLEARANCE))
            {
                return true;
            }
        }
        return false;
    }

    private static long mix(long seed, long id)
    {
        long h = seed * 0x9E3779B97F4A7C15L ^ id * 0xC2B2AE3D27D4EB4FL ^ 0x52696D6566616C6CL;   // ("Rimefall")
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        return h ^ h >>> 29;
    }

    // ---------------------------------------------------------------- asking about a place

    /** The plan of the Rimefall patch the column is in (empty: not Rimefall there, a speck, or past the planner's reach). */
    public static Optional<RimefallArea> areaAt(BossSites sites, int x, int z)
    {
        return sites.patchAt(DrossBiomes.RIMEFALL, x, z).flatMap(p -> sites.site(BossSites.RIMEFALL_AREA, p))
                .map(s -> (RimefallArea) s.detail());
    }

    /** The lake of the Rimefall patch the column is in, if it has one. */
    public static Optional<RimefallLake> lakeAt(BossSites sites, int x, int z)
    {
        return areaAt(sites, x, z).map(RimefallArea::lake);
    }

    /** The planned lakes of the Rimefall patches whose lake lies within {@code radius} of (x, z), nearest first. */
    public static List<RimefallLake> lakesNear(BossSites sites, int x, int z, int radius)
    {
        List<RimefallLake> lakes = new ArrayList<>();
        sites.sitesNear(DrossBiomes.RIMEFALL, x, z, radius + (int) RimefallLake.reach(RimefallTuning.LAKE_RADIUS)).forEach(s -> {
            if (s.use() == BossSites.RIMEFALL_AREA && s.detail() instanceof RimefallArea area && area.lake() != null
                    && area.lake().outside(x, z) <= radius)
            {
                lakes.add(area.lake());
            }
        });
        lakes.sort((a, b) -> Double.compare(a.outside(x, z), b.outside(x, z)));
        return lakes;
    }

    /** The nearest planned lake to {@code from} (in the Dross), within {@code maxDistance} blocks. */
    public static Optional<RimefallLake> nearestLake(ServerLevel dross, BlockPos from, int maxDistance)
    {
        List<RimefallLake> near = lakesNear(BossSites.forLevel(dross), from.getX(), from.getZ(), maxDistance);
        return near.isEmpty() ? Optional.empty() : Optional.of(near.get(0));
    }

    /** True if world generation should keep its features off this column: on a planned lake or within {@link RimefallTuning#FEATURE_CLEARANCE} of it. */
    public static boolean nearLake(BossSites sites, int x, int z)
    {
        Optional<RimefallLake> lake = lakeAt(sites, x, z);
        return lake.isPresent() && lake.get().outside(x, z) < RimefallTuning.FEATURE_CLEARANCE;
    }

    // ---------------------------------------------------------------- Pond Skaters

    /**
     * Is there room for a Pond Skater to spawn naturally with its feet at {@code pos} (on ice)? Never while
     * {@link RimefallTuning#SKATERS_PER_AREA} (3) are within {@link RimefallTuning#SKATER_AREA_RADIUS} blocks; on a big
     * lake while fewer than its {@link RimefallLake#skaterCap()} (1 or 2) are on it or its shore; on any other patch of
     * ice at least {@link RimefallTuning#PATCH_MIN_ACROSS} blocks across while fewer than
     * {@link RimefallTuning#SKATERS_PER_PATCH} are on it or near it; anywhere smaller never (the user: "one that's 10
     * blocks shouldn't even get one"). Skaters that wandered off count where they came from
     * ({@link RimefallTuning#SKATER_WANDER_MARGIN}). The spawn rule also rolls its chance
     * ({@link RimefallTuning#SKATER_SPAWN_CHANCE}).
     */
    public static boolean skaterMayAppear(ServerLevel level, BlockPos pos)
    {
        AABB area = new AABB(pos).inflate(RimefallTuning.SKATER_AREA_RADIUS, RimefallTuning.SKATER_AREA_HEIGHT,
                RimefallTuning.SKATER_AREA_RADIUS);
        if (skatersIn(level, area) >= RimefallTuning.SKATERS_PER_AREA)
        {
            return false;
        }
        double margin = RimefallTuning.SKATER_WANDER_MARGIN;
        Optional<RimefallLake> lake = level.dimension().equals(ModDimensions.DROSS_LEVEL)
                ? lakeAt(BossSites.forLevel(level), pos.getX(), pos.getZ()) : Optional.empty();
        if (lake.isPresent() && lake.get().inLake(pos.getX(), pos.getZ()))
        {
            RimefallLake l = lake.get();
            double r = l.reach() + margin;
            AABB box = new AABB(l.centre().getX() - r, l.iceY() - 6, l.centre().getZ() - r, l.centre().getX() + r, l.iceY() + 8,
                    l.centre().getZ() + r);
            return skatersIn(level, box) < l.skaterCap();
        }
        Pond patch = measurePatch(level, pos.below());
        if (patch == null || patch.across() < RimefallTuning.PATCH_MIN_ACROSS)
        {
            return false;
        }
        return skatersIn(level, patch.box().inflate(margin, 4.0D, margin)) < RimefallTuning.SKATERS_PER_PATCH;
    }

    /**
     * The Pond Skaters (alive) in the box. By class, not by entity type: a Pond Skater's leg hitboxes (its parts) carry
     * its entity type too, and a search by type returns them as well (that crashed the game when a skater spawned).
     */
    private static int skatersIn(ServerLevel level, AABB box)
    {
        return level.getEntitiesOfClass(PondSkater.class, box, PondSkater::isAlive).size();
    }

    /** A patch of ice: how far across it reaches (the longer side of the box round it) and that box. */
    record Pond(int across, AABB box)
    {
    }

    /**
     * The patch of ice {@code ice} belongs to: every open ice block (any ice: a frozen pond, packed ice; nothing solid on
     * it) joined to it along its sides at the same height (only in loaded chunks; at most
     * {@link RimefallTuning#PATCH_SEARCH_LIMIT}); null if {@code ice} isn't open ice.
     */
    @Nullable
    static Pond measurePatch(ServerLevel level, BlockPos ice)
    {
        if (!isPatchIce(level, ice))
        {
            return null;
        }
        int y = ice.getY();
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        long first = ChunkPos.asLong(ice.getX(), ice.getZ());
        seen.add(first);
        queue.enqueue(first);
        int minX = ice.getX();
        int maxX = minX;
        int minZ = ice.getZ();
        int maxZ = minZ;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        while (!queue.isEmpty() && seen.size() < RimefallTuning.PATCH_SEARCH_LIMIT)
        {
            long c = queue.dequeueLong();
            int cx = ChunkPos.getX(c);
            int cz = ChunkPos.getZ(c);
            minX = Math.min(minX, cx);
            maxX = Math.max(maxX, cx);
            minZ = Math.min(minZ, cz);
            maxZ = Math.max(maxZ, cz);
            for (int[] d : new int[][] {{1, 0}, {-1, 0}, {0, 1}, {0, -1}})
            {
                int nx = cx + d[0];
                int nz = cz + d[1];
                long n = ChunkPos.asLong(nx, nz);
                if (seen.contains(n))
                {
                    continue;
                }
                at.set(nx, y, nz);
                if (level.isLoaded(at) && isPatchIce(level, at))
                {
                    seen.add(n);
                    queue.enqueue(n);
                }
            }
        }
        int across = Math.max(maxX - minX, maxZ - minZ) + 1;
        if (seen.size() >= RimefallTuning.PATCH_SEARCH_LIMIT)
        {
            across = Math.max(across, RimefallTuning.PATCH_MIN_ACROSS);
        }
        return new Pond(across, new AABB(minX - 1, y - 2, minZ - 1, maxX + 2, y + 5, maxZ + 2));
    }

    /** Ice (a frozen pond's, packed ice...) with nothing solid on it. */
    private static boolean isPatchIce(ServerLevel level, BlockPos pos)
    {
        BlockPos above = pos.above();
        return level.getBlockState(pos).is(BlockTags.ICE) && level.getBlockState(above).getCollisionShape(level, above).isEmpty();
    }
}
