package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.AlphaMoose;
import com.reg21meme.dross.boss.huntsman.Huntsman;
import com.reg21meme.dross.boss.rimefall.RimefallAnimals;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallAreas;
import com.reg21meme.dross.dimension.rimefall.RimefallLake;
import com.reg21meme.dross.dimension.rimefall.RimefallTuning;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Group;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * Headless checks (GameTests) of Rimefall's hunter villages in the world (October 23, 2026; the user: one Huntsman's
 * village in every group of 3 Rimefall patches, a regular village on each of the other two half the time, never on or
 * by the big lake; the Huntsman comes home to his village):
 * <ul>
 *   <li>{@link #hunterVillagesArePlanned}: Rimefall's first {@value #GROUPS} groups: a Huntsman's village in the boss's
 *       patch (a Huntsman design), regular designs only on the other two (and only where their roll came up), every
 *       village inside its patch, clear of its lake and its shore, no herd spot inside a village, the same when planned
 *       again.</li>
 *   <li>{@link #huntsmanVillageIsBuilt}: the nearest Huntsman's village, generated: its template's packed ice and snow
 *       blocks where the plan puts them, ground to stand on at its way in and in his yard; then the keeper: he comes
 *       with his Alpha Moose to his yard, him in the saddle, with his home; asked again, no second pair; removed without
 *       dying, a new pair; beaten, never again.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HunterVillageWorldGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TIMEOUT_MILLIS = 10 * 60 * 1000L;
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** Rimefall's first this many groups are planned and checked. */
    private static final int GROUPS = 4;
    /** At least this share of a built village's sampled template blocks must be in the world as planned. */
    private static final double MIN_MATCH = 0.97D;
    /** At least half the groups must have a Huntsman's village (one fails only where nothing fits). */
    private static final double MIN_HUNTSMAN_SHARE = 0.5D;

    private HunterVillageWorldGameTest() {}

    private static ServerLevel dross(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        return dross;
    }

    private static void waitFor(CompletableFuture<?> future, long deadline, String what)
    {
        if (!future.isDone() && Util.getMillis() < deadline)
        {
            throw new GameTestAssertException("waiting for " + what);
        }
        if (!future.isDone())
        {
            throw new GameTestAssertException("gave up waiting for " + what);
        }
    }

    // ---------------------------------------------------------------- the plan

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_village_plan", timeoutTicks = BACKSTOP_TICKS)
    public static void hunterVillagesArePlanned(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<List<String>> checked = CompletableFuture.supplyAsync(() -> checkPlans(dross), Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(checked, deadline, "Rimefall's villages to be planned"))
                .thenExecute(() -> {
                    List<String> problems = checked.join();
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("Rimefall's hunter villages aren't right: "
                                + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
                    }
                })
                .thenSucceed();
    }

    private static List<String> checkPlans(ServerLevel dross)
    {
        List<String> problems = new ArrayList<>();
        BossSites sites = BossSites.forLevel(dross);
        BossSites fresh = BossSites.forSeed(dross, dross.getSeed());
        int huntsmen = 0;
        int regular = 0;
        int rolled = 0;
        int groups = 0;
        for (int g = 0; g < GROUPS; g++)
        {
            Optional<Group> found = sites.group(DrossBiomes.RIMEFALL, g);
            if (found.isEmpty())
            {
                break;
            }
            groups++;
            Group group = found.get();
            for (int k = 0; k < group.patches().size(); k++)
            {
                boolean boss = group.slots().get(k) == 0;
                Optional<Site> site = boss ? group.bossSite() : group.fillers().get(k);
                String name = "Rimefall group " + (g + 1) + " patch " + group.patches().get(k).number();
                rolled += !boss && group.rolled().get(k) ? 1 : 0;
                if (site.isEmpty())
                {
                    continue;
                }
                if (!(site.get().detail() instanceof RimefallVillages.VillagePlan plan))
                {
                    problems.add(name + "'s site isn't a village plan");
                    continue;
                }
                if (plan.huntsman() != boss)
                {
                    problems.add(name + " has " + (plan.huntsman() ? "a Huntsman's" : "a regular") + " village in the "
                            + (boss ? "boss's" : "kept") + " patch");
                }
                if (boss)
                {
                    huntsmen++;
                }
                else
                {
                    regular++;
                }
                checkVillage(sites, plan, site.get(), name, problems);
                // each of its parts on its own ground (its zones' levels), and none of Rimefall's animals spawning in it
                if (plan.levels() == null || plan.zones() == null || plan.levels().length != plan.zones().zones())
                {
                    problems.add(name + "'s village has no levels for its zones");
                }
                if (!RimefallAnimals.inHunterVillage(dross, MobSpawnType.NATURAL, plan.ground())
                        || RimefallAnimals.inHunterVillage(dross, MobSpawnType.SPAWN_EGG, plan.ground()))
                {
                    problems.add(name + ": Rimefall's animals should never spawn by themselves in its village (a spawn egg "
                            + "still works)");
                }
                // the same a second time, from a fresh plan of the seed
                Optional<Site> again = fresh.group(DrossBiomes.RIMEFALL, g).flatMap(gr -> {
                    for (int j = 0; j < gr.patches().size(); j++)
                    {
                        if (gr.patches().get(j).number() == site.get().patch().number())
                        {
                            return gr.slots().get(j) == 0 ? gr.bossSite() : gr.fillers().get(j);
                        }
                    }
                    return Optional.empty();
                });
                if (again.isEmpty() || !plan.equals(again.get().detail()))
                {
                    problems.add(name + "'s village is planned differently a second time: " + plan + " / "
                            + again.map(Site::detail).orElse("none"));
                }
                LOGGER.info("[hunter villages] {}: {} village design {} at {} facing {}{}", name, boss ? "the Huntsman's" : "a hunter",
                        plan.designNumber(), plan.ground().toShortString(), plan.front().getName(), plan.relaxed() ? " (relaxed)" : "");
            }
        }
        LOGGER.info("[hunter villages] {} groups: {} Huntsman's villages, {} regular ones ({} kept patches rolled)", groups, huntsmen,
                regular, rolled);
        if (groups == 0)
        {
            problems.add("the planner found no Rimefall group");
        }
        else if (huntsmen < Math.ceil(groups * MIN_HUNTSMAN_SHARE))
        {
            problems.add("only " + huntsmen + " of " + groups + " groups have a Huntsman's village");
        }
        return problems;
    }

    /** A village lies in its patch, clear of its lake and shore, and holds no herd spot. */
    private static void checkVillage(BossSites sites, RimefallVillages.VillagePlan plan, Site site, String name, List<String> problems)
    {
        BoundingBox box = plan.footprint();
        Optional<RimefallAreas.RimefallArea> area = sites.site(BossSites.RIMEFALL_AREA, site.patch())
                .map(s -> (RimefallAreas.RimefallArea) s.detail());
        RimefallLake lake = area.map(RimefallAreas.RimefallArea::lake).orElse(null);
        int outside = 0;
        int onLake = 0;
        for (int x = box.minX(); x <= box.maxX(); x += 4)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z += 4)
            {
                if (!site.patch().contains(x, z))
                {
                    outside++;
                }
                if (lake != null && lake.outside(x, z) < RimefallTuning.SHORE_WIDTH)
                {
                    onLake++;
                }
            }
        }
        if (outside > 0)
        {
            problems.add(String.format(Locale.ROOT, "%s's village at %s reaches out of its patch (%d samples)", name,
                    plan.ground().toShortString(), outside));
        }
        if (onLake > 0)
        {
            problems.add(String.format(Locale.ROOT, "%s's village at %s is on its lake or its shore (%d samples)", name,
                    plan.ground().toShortString(), onLake));
        }
        for (BlockPos herd : area.map(RimefallAreas.RimefallArea::herds).orElse(List.of()))
        {
            if (plan.covers(herd.getX(), herd.getZ(), 0))
            {
                problems.add(name + "'s herd spot " + herd.toShortString() + " is inside its village");
            }
        }
    }

    // ---------------------------------------------------------------- the Huntsman's village, built, and him at home

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_huntsman_village", timeoutTicks = BACKSTOP_TICKS)
    public static void huntsmanVillageIsBuilt(GameTestHelper helper)
    {
        ServerLevel dross = dross(helper);
        CompletableFuture<Optional<Site>> planned = CompletableFuture.supplyAsync(
                () -> BossSites.forLevel(dross).nearest(BossSites.HUNTSMAN_VILLAGE, BlockPos.ZERO, 12_000), Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        RimefallVillages.VillagePlan[] village = new RimefallVillages.VillagePlan[1];
        List<ChunkPos> forced = new ArrayList<>();
        helper.startSequence()
                .thenWaitUntil(() -> waitFor(planned, deadline, "the nearest Huntsman's village to be planned"))
                .thenExecute(() -> {
                    Site site = planned.join().orElseThrow(() -> new GameTestAssertException("no Huntsman's village within 12,000 blocks"));
                    RimefallVillages.VillagePlan plan = (RimefallVillages.VillagePlan) site.detail();
                    village[0] = plan;
                    long started = Util.getMillis();
                    BoundingBox box = plan.coverBox(0);
                    for (int cx = box.minX() >> 4; cx <= box.maxX() >> 4; cx++)
                    {
                        for (int cz = box.minZ() >> 4; cz <= box.maxZ() >> 4; cz++)
                        {
                            dross.getChunk(cx, cz);
                        }
                    }
                    List<String> problems = new ArrayList<>();
                    String stats = checkBuilt(dross, plan, problems);
                    LOGGER.info("[huntsman village] design {} at {} facing {}: {} ({} ms to generate and check)", plan.designNumber(),
                            plan.ground().toShortString(), plan.front().getName(), stats, Util.getMillis() - started);
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the Huntsman's village at " + plan.ground().toShortString() + " isn't right: "
                                + String.join("; ", problems.subList(0, Math.min(8, problems.size()))));
                    }
                    // his yard's land loaded with its entities, for the keeper
                    ChunkPos home = new ChunkPos(plan.home());
                    for (int dx = -1; dx <= 1; dx++)
                    {
                        for (int dz = -1; dz <= 1; dz++)
                        {
                            ChunkPos c = new ChunkPos(home.x + dx, home.z + dz);
                            dross.setChunkForced(c.x, c.z, true);
                            forced.add(c);
                        }
                    }
                    HuntsmanVillageData.get(dross).forget(plan.id());
                })
                .thenWaitUntil(() -> {
                    if (!dross.isPositionEntityTicking(village[0].home()) && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("waiting for his yard to tick");
                    }
                })
                .thenExecute(() -> {
                    String problem;
                    try
                    {
                        problem = checkKeeper(dross, village[0]);
                    }
                    finally
                    {
                        forced.forEach(c -> dross.setChunkForced(c.x, c.z, false));
                        HuntsmanVillageData.get(dross).forget(village[0].id());
                    }
                    if (problem != null)
                    {
                        throw new GameTestAssertException(problem);
                    }
                })
                .thenSucceed();
    }

    /** The template's packed ice and snow blocks where the plan puts them (each part on its zone's level: the zones'
     * own levels, HunterVillageZones); ground at the way in and in his yard. */
    private static String checkBuilt(ServerLevel dross, RimefallVillages.VillagePlan plan, List<String> problems)
    {
        HunterVillageDesigns.Design design = plan.design();
        Optional<StructureTemplate> template = HunterVillages.template(dross, design);
        if (template.isEmpty())
        {
            problems.add("its template " + design.template() + " is missing");
            return "no template";
        }
        StructurePlaceSettings settings = HunterVillages.settings(design, plan.front());
        BlockPos origin = HunterVillages.origin(plan.ground(), design);
        int sampled = 0;
        int matched = 0;
        for (Block block : new Block[] {Blocks.PACKED_ICE, Blocks.SNOW_BLOCK})
        {
            for (StructureTemplate.StructureBlockInfo info : template.get().filterBlocks(origin, settings, block))
            {
                BlockPos local = HunterVillages.toLocal(info.pos(), plan.ground(), plan.front(), design);
                int level = plan.levelAt(local.getY() > design.groundLayer(), local.getX(), local.getZ());
                if (level == HunterVillageZones.NONE)
                {
                    continue;                   // (on its own ground: a path, a lantern post)
                }
                BlockPos at = info.pos().above(level - plan.ground().getY());
                sampled++;
                if (dross.getBlockState(at).is(block))
                {
                    matched++;
                }
                else if (sampled - matched <= 3)
                {
                    LOGGER.info("[huntsman village] {} is {} ({} planned)", at.toShortString(), dross.getBlockState(at), block);
                }
            }
        }
        double share = sampled == 0 ? 0.0D : matched / (double) sampled;
        if (share < MIN_MATCH)
        {
            problems.add(String.format(Locale.ROOT, "only %d of its %d packed ice and snow blocks are in place (%.1f%%)", matched,
                    sampled, 100.0D * share));
        }
        // (its way in on the land as it is there: the heightmap's; his yard at its zone's level)
        BlockPos way = plan.wayIn();
        way = way.atY(dross.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, way.getX(), way.getZ()));
        for (BlockPos at : new BlockPos[] {way, plan.home()})
        {
            if (!dross.getBlockState(at.below()).isFaceSturdy(dross, at.below(), Direction.UP)
                    || !dross.getBlockState(at).getCollisionShape(dross, at).isEmpty()
                    || !dross.getBlockState(at.above()).getCollisionShape(dross, at.above()).isEmpty())
            {
                problems.add("nowhere to stand at " + at.toShortString() + " (" + dross.getBlockState(at.below()) + " under "
                        + dross.getBlockState(at) + ")");
            }
        }
        return String.format(Locale.ROOT, "%d of %d packed ice and snow blocks in place", matched, sampled);
    }

    /** The keeper: he comes home, once; removed without dying a new pair comes; beaten, never again. */
    private static String checkKeeper(ServerLevel dross, RimefallVillages.VillagePlan plan)
    {
        HuntsmanVillageData data = HuntsmanVillageData.get(dross);
        HuntsmanVillages.keep(dross, plan);
        HuntsmanVillageData.Entry entry = data.find(plan.id());
        Entity first = entry == null || entry.huntsman() == null ? null : dross.getEntity(entry.huntsman());
        if (!(first instanceof Huntsman h))
        {
            return "no Huntsman came to his village at " + plan.home().toShortString();
        }
        if (!(h.getVehicle() instanceof AlphaMoose moose))
        {
            return "he should come in his Alpha Moose's saddle";
        }
        if (h.homeVillage() != plan.id() || moose.homeVillage() != plan.id() || h.home() == null || moose.home() == null)
        {
            return "he and the moose should both have his village as their home";
        }
        // on his patrol round the village (at a loaded point of it), or in his yard if none of it is loaded
        java.util.List<BlockPos> loop = HuntsmanVillages.patrolLoop(plan);
        if (moose.patrol().size() != loop.size())
        {
            return "the moose should have his patrol round the village (" + moose.patrol().size() + " points of "
                    + loop.size() + ")";
        }
        double off = moose.position().distanceTo(net.minecraft.world.phys.Vec3.atBottomCenterOf(plan.home()));
        for (BlockPos p : loop)
        {
            off = Math.min(off, Math.hypot(moose.getX() - (p.getX() + 0.5D), moose.getZ() - (p.getZ() + 0.5D)));
        }
        if (off > 14.0D)
        {
            return String.format(Locale.ROOT, "they came %.1f blocks from his patrol and his yard", off);
        }
        int generation = entry.generation();
        HuntsmanVillages.keep(dross, plan);
        if (data.find(plan.id()).generation() != generation)
        {
            return "asked again while he's there, the keeper brought a second pair";
        }
        // removed without dying (Peaceful): not beaten, a new pair next time
        h.discard();
        moose.discard();
        HuntsmanVillages.keep(dross, plan);
        HuntsmanVillageData.Entry next = data.find(plan.id());
        Entity second = next.huntsman() == null ? null : dross.getEntity(next.huntsman());
        if (!(second instanceof Huntsman h2) || next.generation() != generation + 1)
        {
            return "removed without dying, a new pair should come (generation " + next.generation() + ")";
        }
        // beaten: never again
        HuntsmanVillages.beaten(h2);
        Entity vehicle = h2.getVehicle();
        h2.discard();
        if (vehicle != null)
        {
            vehicle.discard();
        }
        HuntsmanVillages.keep(dross, plan);
        HuntsmanVillageData.Entry last = data.find(plan.id());
        if (!last.beaten() || last.huntsman() != null || last.generation() != generation + 1)
        {
            return "beaten, he should never come back (beaten " + last.beaten() + ", generation " + last.generation() + ")";
        }
        LOGGER.info("[huntsman village] the keeper: he came home ({} blocks from his patrol or yard), once; a new pair after he was removed; "
                + "none once beaten", Math.round(off));
        return null;
    }
}
