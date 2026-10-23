package com.reg21meme.dross.dimension.rimefall;

import com.reg21meme.dross.boss.huntsman.village.HunterVillageDesigns;
import com.reg21meme.dross.boss.huntsman.village.HunterVillages;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.BossSitesTuning;
import com.reg21meme.dross.dimension.sites.Patch;
import com.reg21meme.dross.dimension.sites.Site;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.function.IntBinaryOperator;

/**
 * Where Rimefall's hunter villages go (the user, October 23, 2026: "add the villages to the Rimefall biomes ... the same
 * logic: a 1 in 3 of 3 Rimefalls with a guarantee of 1, then the other 2 have a 50% chance of a regular, non-hunter
 * village ... not on the ice lake, any other part"). The boss-site planner (step 12) gives every group of 3 Rimefall
 * patches one Huntsman's village ({@link BossSites#HUNTSMAN_VILLAGE}, the biome's boss) and each of its other two a
 * regular hunter village with a 1 in 2 chance ({@link BossSites#HUNTER_VILLAGE}, its filler). Each patch's plan
 * ({@link RimefallAreas}) works out, after its lake, where each kind would go ({@link #plan}): the flattest spot, deep in
 * the patch (its footprint and the ring its ground is blended back over at least {@link RimefallTuning#VILLAGE_EDGE_MARGIN}
 * blocks inside the patch, so a chase stays in Rimefall), clear of the lake and its shore; its herds keep off it. The
 * village is built with the land ({@code boss/huntsman/village/HunterVillageStructure}).
 */
public final class RimefallVillages
{
    private RimefallVillages() {}

    /**
     * A village's plan: which design ({@link HunterVillageDesigns.Design#number}), where its middle is ({@code ground}: the
     * ground block its ground layer goes on, at the level its ground is cut and filled to), which way its way in faces,
     * and its own seed (which of its decorations it keeps).
     */
    public record VillagePlan(int designNumber, Direction front, BlockPos ground, long seed, boolean relaxed)
    {
        public HunterVillageDesigns.Design design()
        {
            return HunterVillageDesigns.byNumber(this.designNumber);
        }

        public boolean huntsman()
        {
            return this.design().huntsman();
        }

        /** The box the village's templates fill. */
        public BoundingBox footprint()
        {
            return HunterVillages.footprint(this.ground, this.front, this.design());
        }

        /** How far (x, z) is outside the village's footprint, across the ground (0 inside). */
        public double outside(int x, int z)
        {
            BoundingBox b = this.footprint();
            int ex = Math.max(0, Math.max(b.minX() - x, x - b.maxX()));
            int ez = Math.max(0, Math.max(b.minZ() - z, z - b.maxZ()));
            return Math.sqrt((double) ex * ex + (double) ez * ez);
        }

        /** True if (x, z) is in the village, the ring its ground is blended back over, or {@code margin} past that. */
        public boolean covers(int x, int z, int margin)
        {
            return this.outside(x, z) <= RimefallTuning.VILLAGE_BLEND + margin;
        }

        /** The box of {@link #covers} with {@code margin}. */
        public BoundingBox coverBox(int margin)
        {
            int m = RimefallTuning.VILLAGE_BLEND + margin;
            BoundingBox b = this.footprint();
            return new BoundingBox(b.minX() - m, b.minY(), b.minZ() - m, b.maxX() + m, b.maxY(), b.maxZ() + m);
        }

        /** Where to stand at its way in: on the ground just outside its edge there, facing in. */
        public BlockPos wayIn()
        {
            HunterVillageDesigns.Design d = this.design();
            BlockPos local = new BlockPos(d.wayInX(), d.groundLayer() + 1, Math.min(d.depth() - 1, d.wayInZ() + 4));
            return HunterVillages.toWorld(local, this.ground, this.front, d);
        }

        /** Where the Huntsman and his Alpha Moose wait at home: his yard in front of his lodge (or the way in). */
        public BlockPos home()
        {
            HunterVillageDesigns.Design d = this.design();
            if (d.homeX() < 0)
            {
                return this.wayIn();
            }
            return HunterVillages.toWorld(new BlockPos(d.homeX(), d.groundLayer() + 1, d.homeZ()), this.ground, this.front, d);
        }

        /** An id for the village, fixed by where it is. */
        public long id()
        {
            return this.ground.asLong();
        }

        public CompoundTag save()
        {
            CompoundTag tag = new CompoundTag();
            tag.putInt("Design", this.designNumber);
            tag.putString("Front", this.front.getSerializedName());
            tag.putLong("Ground", this.ground.asLong());
            tag.putLong("Seed", this.seed);
            tag.putBoolean("Relaxed", this.relaxed);
            return tag;
        }

        public static VillagePlan load(CompoundTag tag)
        {
            Direction front = Direction.byName(tag.getString("Front"));
            return new VillagePlan(tag.getInt("Design"), front != null ? front : Direction.SOUTH,
                    BlockPos.of(tag.getLong("Ground")), tag.getLong("Seed"), tag.getBoolean("Relaxed"));
        }
    }

    // ---------------------------------------------------------------- the fit test (from the noise)

    /**
     * Where a village of the kind fits best in the patch: of its deepest spots, the one whose ground under the footprint
     * is flattest (the ground is cut and filled to its middle height, the median); its footprint and blend ring at least
     * {@link RimefallTuning#VILLAGE_EDGE_MARGIN} inside the patch and {@link RimefallTuning#VILLAGE_LAKE_CLEARANCE} off
     * the lake's edge; none if the ground under every footprint varies by more than
     * {@link RimefallTuning#VILLAGE_MAX_STEP} (or {@link RimefallTuning#VILLAGE_MAX_STEP_RELAXED}, {@code relaxed}).
     * Its design (of the kind) and its facing are picked by the seed among those that fit.
     *
     * @param top the ground's top block at a column, from the noise
     */
    static Optional<VillagePlan> plan(IntBinaryOperator top, Patch patch, @Nullable RimefallLake lake, long seed,
                                      boolean huntsman, boolean relaxed)
    {
        List<VillagePlan> all = candidates(top, patch, lake, seed, huntsman, relaxed);
        return all.isEmpty() ? Optional.empty() : Optional.of(all.get(0));
    }

    /**
     * Every place {@link #plan} could put the village, best first: the first design (in the seed's order) that fits
     * anywhere, its spots from the flattest; then the next design's. {@link #plan} takes the first; a lake that has to
     * go round the Huntsman's village tries them in turn ({@code RimefallAreas}).
     */
    static List<VillagePlan> candidates(IntBinaryOperator top, Patch patch, @Nullable RimefallLake lake, long seed,
                                        boolean huntsman, boolean relaxed)
    {
        List<VillagePlan> out = new ArrayList<>();
        Random random = new Random(seed ^ (huntsman ? 0x48756E74736D616EL : 0x48756E7465727321L) ^ (relaxed ? 0x52656CL : 0L));
        List<HunterVillageDesigns.Design> designs = new ArrayList<>(huntsman ? HunterVillageDesigns.huntsmanVillages()
                : HunterVillageDesigns.regular());
        Collections.shuffle(designs, random);
        int maxStep = relaxed ? RimefallTuning.VILLAGE_MAX_STEP_RELAXED : RimefallTuning.VILLAGE_MAX_STEP;
        int tried = 0;
        for (HunterVillageDesigns.Design design : designs)
        {
            if (tried++ >= RimefallTuning.VILLAGE_DESIGN_TRIES)
            {
                break;
            }
            boolean[] across = random.nextBoolean() ? new boolean[] {false, true} : new boolean[] {true, false};
            List<Map.Entry<Double, VillagePlan>> fitting = new ArrayList<>();
            for (boolean eastWest : across)
            {
                int w = eastWest ? design.depth() : design.width();
                int d = eastWest ? design.width() : design.depth();
                Direction front = eastWest ? (random.nextBoolean() ? Direction.EAST : Direction.WEST)
                        : (random.nextBoolean() ? Direction.SOUTH : Direction.NORTH);
                for (BlockPos spot : patch.spots(relaxed ? RimefallTuning.VILLAGE_TRIES_RELAXED : RimefallTuning.VILLAGE_TRIES,
                        relaxed ? RimefallTuning.VILLAGE_SPOT_SPACING_RELAXED : RimefallTuning.VILLAGE_SPOT_SPACING))
                {
                    double cost = fits(top, patch, lake, spot.getX(), spot.getZ(), w, d, maxStep, relaxed);
                    if (cost < Double.MAX_VALUE)
                    {
                        int level = level(top, spot.getX(), spot.getZ(), w, d);
                        fitting.add(Map.entry(cost, new VillagePlan(design.number(), front,
                                new BlockPos(spot.getX(), level, spot.getZ()), seed ^ spot.asLong(), relaxed)));
                    }
                }
            }
            fitting.sort(Map.Entry.comparingByKey());           // (stable: the first of equal costs stays first)
            fitting.forEach(e -> out.add(e.getValue()));
        }
        return out;
    }

    /** The cost (the ground's mean step to its median under the footprint) of a w x d village centred on (cx, cz), or
     * {@code Double.MAX_VALUE} if it doesn't fit there. */
    private static double fits(IntBinaryOperator top, Patch patch, @Nullable RimefallLake lake, int cx, int cz, int w, int d,
                               int maxStep, boolean relaxed)
    {
        // (relaxed, only the village itself must be in the patch: its blend ring may run out over the border)
        int reach = relaxed ? 0 : RimefallTuning.VILLAGE_BLEND + (int) RimefallTuning.VILLAGE_EDGE_MARGIN;
        int x0 = cx - w / 2 - reach;
        int x1 = cx + (w - w / 2) + reach;
        int z0 = cz - d / 2 - reach;
        int z1 = cz + (d - d / 2) + reach;
        if (patch.depthAt(cx, cz) < Math.min(w, d) / 2.0D + reach)
        {
            return Double.MAX_VALUE;
        }
        // every 16-block cell it covers must be the patch's: its edges sampled alone missed a corner cell past the last
        // sample and any pocket of another biome inside it (a test saw one reach out of its patch, October 23, 2026)
        for (int gx = Math.floorDiv(x0, BossSitesTuning.CELL); gx <= Math.floorDiv(x1, BossSitesTuning.CELL); gx++)
        {
            for (int gz = Math.floorDiv(z0, BossSitesTuning.CELL); gz <= Math.floorDiv(z1, BossSitesTuning.CELL); gz++)
            {
                if (!patch.containsChunk(new ChunkPos(gx, gz)))
                {
                    return Double.MAX_VALUE;
                }
            }
        }
        int step = RimefallTuning.VILLAGE_EDGE_STEP;
        if (lake != null)
        {
            int ring = RimefallTuning.VILLAGE_BLEND;
            double far = Math.hypot(w / 2.0D + ring, d / 2.0D + ring);
            double gap = Math.hypot(lake.centre().getX() - cx, lake.centre().getZ() - cz) - lake.reach() - far;
            if (gap < RimefallTuning.VILLAGE_LAKE_CLEARANCE)
            {
                for (int x = cx - w / 2 - ring; x <= cx + w / 2 + ring; x += step)
                {
                    for (int z = cz - d / 2 - ring; z <= cz + d / 2 + ring; z += step)
                    {
                        if (lake.outside(x, z) < RimefallTuning.VILLAGE_LAKE_CLEARANCE)
                        {
                            return Double.MAX_VALUE;
                        }
                    }
                }
            }
        }
        int[] tops = tops(top, cx, cz, w, d);
        int median = median(tops);
        double cost = 0.0D;
        for (int t : tops)
        {
            int dev = Math.abs(t - median);
            if (dev > maxStep)
            {
                return Double.MAX_VALUE;
            }
            cost += dev;
        }
        return cost / tops.length;
    }

    /** True if the lake's edge stays {@link RimefallTuning#VILLAGE_LAKE_CLEARANCE} off every column of the village and
     * its blend ring (sampled more finely than {@link #fits} does, so a lake planned round a village never fails it). */
    static boolean clearOf(VillagePlan village, RimefallLake lake)
    {
        BoundingBox box = village.coverBox(0);
        double far = Math.hypot((box.maxX() - box.minX()) / 2.0D, (box.maxZ() - box.minZ()) / 2.0D);
        double gap = Math.hypot(lake.centre().getX() - (box.minX() + box.maxX()) / 2.0D,
                lake.centre().getZ() - (box.minZ() + box.maxZ()) / 2.0D) - lake.reach() - far;
        if (gap >= RimefallTuning.VILLAGE_LAKE_CLEARANCE)
        {
            return true;
        }
        for (int x = box.minX(); x <= box.maxX(); x += CLEAR_STEP)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z += CLEAR_STEP)
            {
                if (lake.outside(x, z) < RimefallTuning.VILLAGE_LAKE_CLEARANCE + CLEAR_STEP)
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** {@link #clearOf}'s sampling step (blocks). */
    private static final int CLEAR_STEP = 4;

    /** The level the village's ground is cut and filled to: the median of the ground's tops under it. */
    private static int level(IntBinaryOperator top, int cx, int cz, int w, int d)
    {
        return median(tops(top, cx, cz, w, d));
    }

    /** The ground's tops under a w x d footprint, on a grid aligned to {@link RimefallTuning#VILLAGE_SAMPLE_STEP} (so
     * neighbouring tries share their samples). */
    private static int[] tops(IntBinaryOperator top, int cx, int cz, int w, int d)
    {
        int step = RimefallTuning.VILLAGE_SAMPLE_STEP;
        List<Integer> out = new ArrayList<>();
        for (int x = Math.floorDiv(cx - w / 2, step) * step + step; x <= cx + w / 2; x += step)
        {
            for (int z = Math.floorDiv(cz - d / 2, step) * step + step; z <= cz + d / 2; z += step)
            {
                out.add(top.applyAsInt(x, z));
            }
        }
        return out.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int median(int[] values)
    {
        int[] sorted = values.clone();
        Arrays.sort(sorted);
        return sorted.length == 0 ? 0 : sorted[sorted.length / 2];
    }

    // ---------------------------------------------------------------- asking about a place

    /** The planned village (either kind) of the Rimefall patch at (x, z) whose footprint, blend ring and {@code margin}
     * cover the column, if any. Safe on any thread (world generation asks it). */
    public static Optional<VillagePlan> villageAt(BossSites sites, int x, int z, int margin)
    {
        Optional<Patch> patch = sites.patchAt(DrossBiomes.RIMEFALL, x, z);
        if (patch.isEmpty())
        {
            return Optional.empty();
        }
        for (Site site : villages(sites, patch.get()))
        {
            VillagePlan plan = (VillagePlan) site.detail();
            if (plan.covers(x, z, margin))
            {
                return Optional.of(plan);
            }
        }
        return Optional.empty();
    }

    /** The planned villages (at most one) of the patch. */
    public static List<Site> villages(BossSites sites, Patch patch)
    {
        List<Site> out = new ArrayList<>(1);
        for (var use : new com.reg21meme.dross.dimension.sites.SiteUse[] {BossSites.HUNTSMAN_VILLAGE, BossSites.HUNTER_VILLAGE})
        {
            sites.site(use, patch).filter(s -> s.detail() instanceof VillagePlan).ifPresent(out::add);
        }
        return out;
    }

    /** The planned villages whose cover (with {@code margin}) reaches into the box of chunks round (chunkX, chunkZ),
     * {@code reach} chunks every way. */
    public static List<VillagePlan> villagesAround(BossSites sites, int chunkX, int chunkZ, int reach, int margin)
    {
        int x0 = (chunkX - reach) * 16;
        int z0 = (chunkZ - reach) * 16;
        int x1 = (chunkX + reach) * 16 + 15;
        int z1 = (chunkZ + reach) * 16 + 15;
        List<VillagePlan> out = new ArrayList<>();
        for (Site site : sites.sitesNear(DrossBiomes.RIMEFALL, (x0 + x1) / 2, (z0 + z1) / 2,
                RimefallTuning.VILLAGE_QUERY_RADIUS))
        {
            if (site.detail() instanceof VillagePlan plan && plan.coverBox(margin).intersects(x0, z0, x1, z1)
                    && !out.contains(plan))
            {
                out.add(plan);
            }
        }
        return out;
    }

    /** The planned villages (either kind) whose middles are nearest to {@code from}, nearest first, within
     * {@code maxDistance} blocks: at most {@code count}. */
    public static List<Site> nearest(BossSites sites, BlockPos from, int maxDistance, int count)
    {
        List<Site> found = new ArrayList<>();
        int radius = Math.min(maxDistance, RimefallTuning.VILLAGE_LIST_STEP);
        while (true)
        {
            found.clear();
            for (Site site : sites.sitesNear(DrossBiomes.RIMEFALL, from.getX(), from.getZ(), radius))
            {
                if (site.detail() instanceof VillagePlan)
                {
                    found.add(site);
                }
            }
            if (found.size() >= count || radius >= maxDistance)
            {
                break;
            }
            radius = Math.min(maxDistance, radius * 2);
        }
        found.sort((a, b) -> Double.compare(a.distanceTo(from.getX(), from.getZ()), b.distanceTo(from.getX(), from.getZ())));
        return found.size() > count ? new ArrayList<>(found.subList(0, count)) : found;
    }
}
