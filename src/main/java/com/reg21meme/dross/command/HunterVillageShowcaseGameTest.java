package com.reg21meme.dross.command;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.village.HunterVillageDesigns;
import com.reg21meme.dross.boss.huntsman.village.HunterVillages;
import com.reg21meme.dross.boss.huntsman.village.VillageAnimals;
import com.reg21meme.dross.boss.rimefall.Moose;
import com.reg21meme.dross.boss.rimefall.Rimehorn;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Headless checks of the hunter villages ({@code /dross showcase hunter_villages}, the boss area's
 * {@link HunterVillageDesigns}): the Sunken Warren's eight layouts (15 to 20 igloos) and the Huntsman's two villages
 * (25 to 30: the user's "massive" villages).
 * <ul>
 *   <li>Both templates of every layout (the village and its decorations) are in the mod at its table's size, and every
 *       decoration's box lies inside it, apart from the others; the showcase's plots are one per layout and apart.</li>
 *   <li>Every village, built in the world the way the showcase builds it with all its decorations (the most light and
 *       the most in the way: each layout facing a different way), stands as its templates say, with whole beds and no
 *       clear ice or snow layer lit bright enough to melt once the game has worked its light out.</li>
 *   <li>Decorations come and go: built with a seeded roll, every decoration that won is there and every one that lost
 *       isn't (the user's "1/2 chance for everything to spawn"); the rolls keep about half of them.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HunterVillageShowcaseGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Where the villages are tried: far from spawn and the other tests, in the air (so no terrain or water). */
    private static final BlockPos TRY_AT = new BlockPos(-41000, 200, 37000);
    /** Blocks between two tried villages (the biggest, the Huntsman's 29 igloos, is about 240 across). */
    private static final int SPACING = 320;
    /** Ticks given to the light engine before the light is read (the villages are big: tens of lanterns each), and
     * once more before a dark room counts. */
    private static final int LIGHT_TICKS = 100;
    private static final int LIGHT_RETRY_TICKS = 200;
    private static final int LIGHT_RETRIES = 3;
    /** ...and for at least this long by the clock: the game's light engine works through a village's lamps on its own
     * thread at its own pace, and the test server can run its ticks several times faster than the game does (a room
     * once read light 0 for all three retries, about 4 seconds; the same room is lit on other runs). */
    private static final long LIGHT_WAIT_MILLIS = 15000L;
    /** Room for the slow cases in the timeout (ticks: the waits above go by the clock as well as by ticks). */
    private static final int LIGHT_SLACK_TICKS = 20000;
    /** While a village's animals wait for their land, it's looked at again this often. */
    private static final int ANIMAL_POLL_TICKS = 20;
    /** Clear ice melts over this block light, a snow layer over the next (IceBlock, SnowLayerBlock). */
    private static final int ICE_MELTS_ABOVE = 10;
    private static final int SNOW_MELTS_ABOVE = 11;
    /** A room's floor must have at least this block light (the Dross spawns monsters only in block light 0). */
    private static final int MIN_ROOM_LIGHT = 1;
    /** At least this share of a template's blocks must be in the world where it put them. */
    private static final double MIN_MATCH = 0.98D;
    /** The village's own blocks the checks look at, and the decorations'. */
    private static final List<Block> VILLAGE_BLOCKS = List.of(Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.ICE,
            Blocks.BLUE_ICE, Blocks.SNOW, Blocks.WHITE_BED, Blocks.BROWN_BED, Blocks.GRAY_BED, Blocks.LIGHT_GRAY_BED,
            Blocks.SPRUCE_LOG, Blocks.BONE_BLOCK, Blocks.SPRUCE_STAIRS, Blocks.CHEST, Blocks.SPRUCE_PLANKS);
    private static final List<Block> DECOR_BLOCKS = List.of(Blocks.SPRUCE_LOG, Blocks.STRIPPED_SPRUCE_LOG,
            Blocks.SPRUCE_FENCE, Blocks.BONE_BLOCK, Blocks.SKELETON_SKULL, Blocks.NETHER_WART_BLOCK,
            Blocks.DRIED_KELP_BLOCK, Blocks.BARREL, Blocks.CAMPFIRE, Blocks.SOUL_CAMPFIRE, Blocks.CHAIN,
            Blocks.SPRUCE_SLAB, Blocks.BROWN_WOOL, Blocks.LIGHT_GRAY_WOOL, Blocks.COBBLESTONE, Blocks.LIGHTNING_ROD,
            Blocks.BIRCH_FENCE, Blocks.SPRUCE_STAIRS, Blocks.WHITE_BANNER, Blocks.BROWN_BANNER, Blocks.LIGHT_GRAY_BANNER);

    private HunterVillageShowcaseGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_villages_table")
    public static void templatesMatchTheirTable(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        if (HunterVillageDesigns.regular().size() != 9 || HunterVillageDesigns.huntsmanVillages().size() != 2)
        {
            throw new GameTestAssertException("there should be 8 Sunken Warrens and 2 Huntsman's villages, but there are "
                    + HunterVillageDesigns.regular().size() + " and " + HunterVillageDesigns.huntsmanVillages().size());
        }
        for (HunterVillageDesigns.Design design : HunterVillageDesigns.ALL)
        {
            int fewest = design.huntsman() ? 25 : 15;
            int most = design.huntsman() ? 30 : 20;
            if (design.igloos() < fewest || design.igloos() > most)
            {
                throw new GameTestAssertException(design.id() + " has " + design.igloos() + " igloos (they should be "
                        + fewest + " to " + most + ")");
            }
            for (Optional<StructureTemplate> template : List.of(HunterVillages.template(level, design),
                    HunterVillages.decorTemplate(level, design)))
            {
                if (template.isEmpty())
                {
                    throw new GameTestAssertException("a template of " + design.id() + " is missing");
                }
                Vec3i size = template.get().getSize();
                if (size.getX() != design.width() || size.getY() != design.layers() || size.getZ() != design.depth())
                {
                    throw new GameTestAssertException(design.id() + "'s template is " + size + ", its table says "
                            + design.width() + " x " + design.layers() + " x " + design.depth());
                }
            }
            List<BoundingBox> boxes = design.decorations();
            if (boxes.size() < 20)
            {
                throw new GameTestAssertException(design.id() + " has only " + boxes.size() + " decorations");
            }
            BoundingBox all = new BoundingBox(0, 0, 0, design.width() - 1, design.layers() - 1, design.depth() - 1);
            for (int i = 0; i < boxes.size(); i++)
            {
                BoundingBox a = boxes.get(i);
                if (!all.isInside(a.minX(), a.minY(), a.minZ()) || !all.isInside(a.maxX(), a.maxY(), a.maxZ()))
                {
                    throw new GameTestAssertException(design.id() + "'s decoration " + i + " " + a + " is outside the template");
                }
                for (int j = i + 1; j < boxes.size(); j++)
                {
                    if (a.intersects(boxes.get(j)))
                    {
                        throw new GameTestAssertException(design.id() + "'s decorations " + i + " and " + j + " overlap");
                    }
                }
            }
        }
        // the showcase: one plot per layout, none closer than 4 blocks to another
        HunterVillageShowcase.Plan plan = HunterVillageShowcase.plan(level, TRY_AT, Direction.NORTH);
        Set<String> ids = new HashSet<>();
        for (HunterVillageShowcase.Plot plot : plan.plots())
        {
            if (!ids.add(plot.design().id()))
            {
                throw new GameTestAssertException("the showcase has " + plot.design().id() + " twice");
            }
            BoundingBox a = HunterVillages.footprint(plot.ground(), plan.front(), plot.design()).inflatedBy(2);
            for (HunterVillageShowcase.Plot other : plan.plots())
            {
                if (other != plot && a.intersects(HunterVillages.footprint(other.ground(), plan.front(), other.design()).inflatedBy(2)))
                {
                    throw new GameTestAssertException(plot.design().id() + " and " + other.design().id() + " overlap");
                }
            }
        }
        if (ids.size() != HunterVillageDesigns.ALL.size())
        {
            throw new GameTestAssertException("the showcase has " + ids.size() + " plots, not " + HunterVillageDesigns.ALL.size());
        }
        helper.succeed();
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_villages_built",
            timeoutTicks = 11 * (LIGHT_TICKS + 30) + 6 * LIGHT_RETRY_TICKS + 2 * VillageAnimals.MAX_WAIT_TICKS + 600
                    + LIGHT_SLACK_TICKS)
    public static void villagesStandAndNeverMelt(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel().getServer().overworld();
        tryNext(helper, level, 0);
    }

    private static void tryNext(GameTestHelper helper, ServerLevel level, int index)
    {
        List<HunterVillageDesigns.Design> all = HunterVillageDesigns.ALL;
        if (index >= all.size())
        {
            LOGGER.info("[hunter village test] all {} layouts stand as their templates say, beds whole, no ice or snow "
                    + "bright enough to melt", all.size());
            helper.succeed();
            return;
        }
        HunterVillageDesigns.Design design = all.get(index);
        Direction front = Direction.from2DDataValue(index % 4);
        BlockPos ground = TRY_AT.offset(index * SPACING, 0, 0);
        List<ChunkPos> forced = load(level, ground, design);
        HunterVillageShowcase.Plot plot = new HunterVillageShowcase.Plot(design, 0, 0, ground);
        HunterVillageShowcase.Plan plan = new HunterVillageShowcase.Plan(ground, front.getOpposite(), ground.getY(), List.of(plot));
        boolean[] keep = new boolean[design.decorations().size()];
        Arrays.fill(keep, true);
        HunterVillageShowcase.build(level, plan, plot, keep);
        helper.runAfterDelay(LIGHT_TICKS, () -> whenAnimalsAreIn(helper, level, design, 0, () ->
        {
            check(level, design, ground, front);
            int[] decor = decorations(level, design, ground, front, keep);
            if (decor[0] < decor[1] * MIN_MATCH)
            {
                throw new GameTestAssertException(design.id() + ": only " + decor[0] + " of " + decor[1]
                        + " of its decorations' blocks are there with all of them kept");
            }
            // (the light engine can still be catching up on a village this big: a dark room is looked at again, a few
            // times; one read light 0 once beside its lanterns, then 7 the next run)
            whenLit(helper, level, design, ground, front, LIGHT_RETRIES, System.nanoTime(), () ->
            {
                unload(level, forced);
                tryNext(helper, level, index + 1);
            });
        }));
    }

    /** Runs {@code then} once every room of the village reads lit, looking again {@code tries} more times if not, and
     * for at least {@link #LIGHT_WAIT_MILLIS} after {@code since} (a {@link System#nanoTime}). */
    private static void whenLit(GameTestHelper helper, ServerLevel level, HunterVillageDesigns.Design design, BlockPos ground,
                                Direction front, int tries, long since, Runnable then)
    {
        String dark = darkRoom(level, design, ground, front);
        if (dark == null)
        {
            then.run();
            return;
        }
        if (tries <= 0 && System.nanoTime() - since > LIGHT_WAIT_MILLIS * 1_000_000L)
        {
            throw new GameTestAssertException(dark);
        }
        helper.runAfterDelay(LIGHT_RETRY_TICKS,
                () -> whenLit(helper, level, design, ground, front, Math.max(0, tries - 1), since, then));
    }

    /**
     * Runs {@code then} once every animal the village brought is in the world ({@link VillageAnimals}: each waits for
     * its land to be ready for it), or once they've waited as long as they ever do.
     */
    private static void whenAnimalsAreIn(GameTestHelper helper, ServerLevel level, HunterVillageDesigns.Design design,
                                         int waited, Runnable then)
    {
        if (VillageAnimals.waiting(level) == 0 || waited > VillageAnimals.MAX_WAIT_TICKS)
        {
            if (waited > 0)
            {
                LOGGER.info("[hunter village test] {}: its animals were in {} ticks after the light check", design.id(), waited);
            }
            then.run();
            return;
        }
        if (waited == 0)
        {
            LOGGER.info("[hunter village test] {}: animals still waiting for their land: {}", design.id(),
                    VillageAnimals.describeWaiting(level));
        }
        helper.runAfterDelay(ANIMAL_POLL_TICKS, () -> whenAnimalsAreIn(helper, level, design, waited + ANIMAL_POLL_TICKS, then));
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_hunter_villages_decor", timeoutTicks = 200)
    public static void decorationsComeAndGo(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel().getServer().overworld();
        HunterVillageDesigns.Design design = HunterVillageDesigns.ALL.get(0);
        BlockPos ground = TRY_AT.offset(0, 0, SPACING * 2);
        List<ChunkPos> forced = load(level, ground, design);
        boolean[] keep = HunterVillages.roll(design, RandomSource.create(20261019L), HunterVillages.DECOR_CHANCE);
        int kept = 0;
        for (boolean k : keep)
        {
            kept += k ? 1 : 0;
        }
        int n = keep.length;
        if (kept < n * 0.25D || kept > n * 0.75D)
        {
            throw new GameTestAssertException("a roll kept " + kept + " of " + n + " decorations: far from half");
        }
        HunterVillageShowcase.Plot plot = new HunterVillageShowcase.Plot(design, 0, 0, ground);
        HunterVillageShowcase.Plan plan = new HunterVillageShowcase.Plan(ground, Direction.NORTH, ground.getY(), List.of(plot));
        HunterVillageShowcase.build(level, plan, plot, keep);
        int[] there = decorations(level, design, ground, Direction.SOUTH, keep);
        if (there[0] < there[1] * MIN_MATCH)
        {
            throw new GameTestAssertException(design.id() + ": only " + there[0] + " of the " + there[1]
                    + " blocks of the decorations it kept are there");
        }
        if (there[2] > there[3] * 0.05D)
        {
            throw new GameTestAssertException(design.id() + ": " + there[2] + " of the " + there[3]
                    + " blocks of the decorations it left out are there anyway");
        }
        LOGGER.info("[hunter village test] {}: kept {} of {} decorations; {} of {} of their blocks there, {} of {} of the "
                + "others' (the ground's own)", design.id(), kept, n, there[0], there[1], there[2], there[3]);
        unload(level, forced);
        helper.succeed();
    }

    /**
     * The land round a village loaded and kept loaded (forced) while it's looked at: a chunk loaded only for a moment
     * unloads again, and when it's loaded back for a look its blocks are there at once but its animals only a little
     * later. Returns the chunks, for {@link #unload}.
     */
    private static List<ChunkPos> load(ServerLevel level, BlockPos ground, HunterVillageDesigns.Design design)
    {
        int r = Math.max(design.width(), design.depth()) / 2 + 24;
        List<ChunkPos> forced = new ArrayList<>();
        for (int dx = -r; dx <= r; dx += 16)
        {
            for (int dz = -r; dz <= r; dz += 16)
            {
                ChunkPos c = new ChunkPos(ground.offset(dx, 0, dz));
                level.setChunkForced(c.x, c.z, true);
                level.getChunk(c.x, c.z);
                forced.add(c);
            }
        }
        return forced;
    }

    private static void unload(ServerLevel level, List<ChunkPos> forced)
    {
        forced.forEach(c -> level.setChunkForced(c.x, c.z, false));
    }

    /**
     * Looks at the decorations' blocks where the village put them: {the kept ones' blocks there, the kept ones' blocks,
     * the dropped ones' blocks there anyway, the dropped ones' blocks}.
     */
    private static int[] decorations(ServerLevel level, HunterVillageDesigns.Design design, BlockPos ground, Direction front,
                                     boolean[] keep)
    {
        StructureTemplate decor = HunterVillages.decorTemplate(level, design).orElseThrow();
        List<BoundingBox> boxes = design.decorations();
        int[] out = new int[4];
        for (Block block : DECOR_BLOCKS)
        {
            for (StructureTemplate.StructureBlockInfo info : decor.filterBlocks(BlockPos.ZERO, new StructurePlaceSettings(), block))
            {
                BlockPos local = info.pos();
                int box = -1;
                for (int i = 0; i < boxes.size(); i++)
                {
                    if (boxes.get(i).isInside(local))
                    {
                        box = i;
                        break;
                    }
                }
                if (box < 0)
                {
                    throw new GameTestAssertException(design.id() + ": a decoration block at " + local.toShortString()
                            + " is in no decoration's box");
                }
                boolean there = level.getBlockState(HunterVillages.toWorld(local, ground, front, design)).is(block);
                if (keep[box])
                {
                    out[1]++;
                    out[0] += there ? 1 : 0;
                }
                else
                {
                    out[3]++;
                    out[2] += there ? 1 : 0;
                }
            }
        }
        return out;
    }

    /**
     * Every room lit enough that nothing spawns in it (the user: "make sure no mobs spawn in the igloos"): its darkest
     * floor cell, as the generator found it. Returns what's wrong, or null.
     */
    private static String darkRoom(ServerLevel level, HunterVillageDesigns.Design design, BlockPos ground, Direction front)
    {
        for (BlockPos probe : design.probes())
        {
            BlockPos at = HunterVillages.toWorld(probe, ground, front, design);
            int light = level.getBrightness(LightLayer.BLOCK, at);
            if (light < MIN_ROOM_LIGHT)
            {
                return design.id() + ": the room floor at " + at.toShortString() + " has block light " + light
                        + ": something could spawn there";
            }
        }
        return null;
    }

    private static void check(ServerLevel level, HunterVillageDesigns.Design design, BlockPos ground, Direction front)
    {
        StructureTemplate template = HunterVillages.template(level, design).orElseThrow();
        StructurePlaceSettings settings = HunterVillages.settings(design, front);
        BlockPos origin = HunterVillages.origin(ground, design);
        int total = 0;
        int matched = 0;
        int ice = 0;
        int snowLayers = 0;
        int beds = 0;
        for (Block block : VILLAGE_BLOCKS)
        {
            for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, block))
            {
                total++;
                BlockState there = level.getBlockState(info.pos());
                if (there.is(block))
                {
                    matched++;
                }
                if (block == Blocks.ICE && there.is(Blocks.ICE))
                {
                    ice++;
                    int light = level.getBrightness(LightLayer.BLOCK, info.pos());
                    if (light > ICE_MELTS_ABOVE)
                    {
                        throw new GameTestAssertException(design.id() + ": the clear ice at " + info.pos().toShortString()
                                + " has block light " + light + " and would melt");
                    }
                }
                if (block == Blocks.SNOW && there.is(Blocks.SNOW))
                {
                    snowLayers++;
                    int light = level.getBrightness(LightLayer.BLOCK, info.pos());
                    if (light > SNOW_MELTS_ABOVE)
                    {
                        throw new GameTestAssertException(design.id() + ": the snow layer at " + info.pos().toShortString()
                                + " has block light " + light + " and would melt");
                    }
                    if (!there.canSurvive(level, info.pos()))
                    {
                        throw new GameTestAssertException(design.id() + ": the snow layer at " + info.pos().toShortString()
                                + " can't lie on what's under it");
                    }
                }
                if (there.getBlock() instanceof BedBlock && there.getValue(BedBlock.PART) == BedPart.HEAD)
                {
                    beds++;
                    BlockState foot = level.getBlockState(info.pos().relative(there.getValue(BedBlock.FACING).getOpposite()));
                    if (!(foot.getBlock() instanceof BedBlock) || foot.getValue(BedBlock.PART) != BedPart.FOOT)
                    {
                        throw new GameTestAssertException(design.id() + ": the bed at " + info.pos().toShortString()
                                + " has no foot");
                    }
                }
            }
        }
        if (total == 0 || matched < total * MIN_MATCH)
        {
            throw new GameTestAssertException(design.id() + ": only " + matched + " of " + total
                    + " of its snow, ice, logs, bones, stairs and beds are where its template put them");
        }
        if (beds < design.igloos() / 2 || ice == 0)
        {
            throw new GameTestAssertException(design.id() + " has " + beds + " beds and " + ice + " clear ice blocks");
        }
        int darkest = 15;
        for (BlockPos probe : design.probes())
        {
            darkest = Math.min(darkest, level.getBrightness(LightLayer.BLOCK, HunterVillages.toWorld(probe, ground, front, design)));
        }
        // its animals: a moose or two in a regular village's paddock, the Huntsman's Rimehorn herd in his corral
        BoundingBox box = HunterVillages.footprint(ground, front, design);
        AABB area = new AABB(box.minX(), box.minY(), box.minZ(), box.maxX() + 1, box.maxY() + 1, box.maxZ() + 1);
        int moose = level.getEntitiesOfClass(Moose.class, area, m -> true).size();
        int rimehorns = level.getEntitiesOfClass(Rimehorn.class, area, r -> true).size();
        if (moose != design.moose() || rimehorns != design.rimehorns())
        {
            StringBuilder near = new StringBuilder();
            for (Mob m : level.getEntitiesOfClass(Mob.class, area.inflate(64.0D), m -> m instanceof Moose || m instanceof Rimehorn))
            {
                near.append(m.getClass().getSimpleName()).append(" at ").append(m.blockPosition().toShortString())
                        .append(area.contains(m.position()) ? " (in it)" : " (outside it)").append("; ");
            }
            throw new GameTestAssertException(design.id() + " should have " + design.moose() + " moose and "
                    + design.rimehorns() + " Rimehorns, but has " + moose + " and " + rimehorns + " (its footprint "
                    + box + "; animals near: " + (near.length() == 0 ? "none" : near) + "; still waiting for their land: "
                    + VillageAnimals.waiting(level) + " " + VillageAnimals.describeWaiting(level) + ")");
        }
        // every one penned (they never go for anyone) and still inside its pen's fence, a while after it was put in
        // (the user saw moose and Rimehorns out of their pens, October 20: the fences are two high now)
        for (Mob m : level.getEntitiesOfClass(Mob.class, area, m -> m instanceof Moose || m instanceof Rimehorn))
        {
            boolean penned = m instanceof Moose moose1 ? moose1.isPenned() : ((Rimehorn) m).isPenned();
            if (!penned)
            {
                throw new GameTestAssertException(design.id() + ": the " + m.getClass().getSimpleName() + " at "
                        + m.blockPosition().toShortString() + " isn't penned");
            }
            boolean inside = false;
            for (int[] pen : design.pens())
            {
                BlockPos a = HunterVillages.toWorld(new BlockPos(pen[0], design.groundLayer(), pen[1]), ground, front, design);
                BlockPos b = HunterVillages.toWorld(new BlockPos(pen[2], design.groundLayer(), pen[3]), ground, front, design);
                AABB fence = new AABB(Math.min(a.getX(), b.getX()), a.getY() - 2, Math.min(a.getZ(), b.getZ()),
                        Math.max(a.getX(), b.getX()) + 1, a.getY() + 6, Math.max(a.getZ(), b.getZ()) + 1);
                inside |= fence.contains(m.position());
            }
            if (!inside)
            {
                throw new GameTestAssertException(design.id() + ": the " + m.getClass().getSimpleName() + " at "
                        + m.blockPosition().toShortString() + " is out of its pen");
            }
        }
        LOGGER.info("[hunter village test] {} (facing {}): {} of {} checked blocks in place, {} clear ice and {} snow layers "
                + "dim enough, {} whole beds, its rooms' floors at block light {} or more, {} moose, {} Rimehorns",
                design.id(), front.getName(), matched, total, ice, snowLayers, beds, darkest, moose, rimehorns);
    }
}
