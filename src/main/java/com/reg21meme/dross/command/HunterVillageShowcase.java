package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.huntsman.folk.FolkTuning;
import com.reg21meme.dross.boss.huntsman.folk.HunterFolk;
import com.reg21meme.dross.boss.huntsman.village.HunterVillageDesigns;
import com.reg21meme.dross.boss.huntsman.village.HunterVillages;
import com.reg21meme.dross.boss.huntsman.village.VillageAnimals;
import com.reg21meme.dross.boss.rimefall.Moose;
import com.reg21meme.dross.boss.rimefall.Rimehorn;
import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * {@code /dross showcase hunter_villages} (test tool, cheats only): builds the hunter villages ahead of you on a snowy
 * floor, so they can be looked at and compared (the boss area's {@link HunterVillageDesigns}, made by
 * tools/huntsman/villages.py): the Sunken Warren's nine layouts (15 to 20 of the hand-designed igloos, the shrine in
 * every third) and the Huntsman's two villages.
 * Every village rolls its decorations as the game will (each a 1 in 2 chance), so building them again shows other
 * mixes.
 * <p>
 * Four rows of plots, their ways in facing you, {@link #GAP} blocks between them: Sunken Warrens 1-3, 4-6, 7-9, then
 * the Huntsman's villages. Each plot is cleared ({@link #CLEAR_ABOVE} blocks over its village), floored with
 * Hoarfrost and snow like Rimefall, and gets a sign at its way in. One plot is built each server tick, so the game
 * doesn't freeze; the chat lists them all with [go] buttons when it's done (each stands you at the top of its way in),
 * and an [overview] button that puts you high above the middle looking down. The floor is raised if the ground is too
 * close to the bottom of the world for the villages' dug courtyards (a superflat world's is). Use a flat world: it's
 * about 580 blocks wide and 820 deep.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HunterVillageShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String COMMAND = "/dross showcase hunter_villages";
    /** Blocks between neighbouring plots (and rows). */
    static final int GAP = 14;
    /** The rows: Sunken Warrens 1-3, 4-6, 7-9, then the Huntsman's villages. */
    static final int PER_ROW = 3;
    /** The first row's plots start this many blocks ahead of you. */
    static final int START = 6;
    /** Air is cleared this many blocks above each village's tallest part, and the floor reaches this far past it. */
    static final int CLEAR_ABOVE = 4;
    static final int FLOOR_MARGIN = 4;
    /** Dips under the floor are filled at most this deep. */
    static final int MAX_FILL = 12;
    /** About this share of the floor is snow blocks (in patches), the rest Hoarfrost, like Rimefall's ground. */
    static final double SNOW_SHARE = 0.3D;
    /** The sign stands this many blocks out from a village's way in (and this far to the side), a [go] button puts you
     * this far out, looking at it. */
    static final int SIGN_OUT = 3;
    static final int SIGN_SIDE = 3;
    static final int STAND_OUT = 2;
    /** The [overview] button: this high over the floor, in the middle of the plots. */
    static final int OVERVIEW_HEIGHT = 150;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /** One village's plot: its design, its row and column, and its middle's ground block. */
    public record Plot(HunterVillageDesigns.Design design, int row, int column, BlockPos ground)
    {
    }

    /** Where everything goes: the plots, the way they face (back towards you), the floor's height. */
    public record Plan(BlockPos origin, Direction look, int floorY, List<Plot> plots)
    {
        public Direction front()
        {
            return look.getOpposite();
        }
    }

    /** A build in progress: the plots still to build, and who to tell when it's done. */
    private record Job(ServerLevel level, Plan plan, Deque<Plot> todo, CommandSourceStack source)
    {
    }

    private static final List<Job> JOBS = new ArrayList<>();

    private HunterVillageShowcase() {}

    /** Lays the plots out ahead of {@code origin} (a ground block), looking {@code look}. */
    public static Plan plan(ServerLevel level, BlockPos origin, Direction look)
    {
        int deepest = 0;
        for (HunterVillageDesigns.Design d : HunterVillageDesigns.ALL)
        {
            deepest = Math.max(deepest, d.groundLayer());
        }
        int floorY = Math.max(origin.getY(), level.getMinBuildHeight() + deepest + 2);
        Direction right = look.getClockWise();
        List<List<HunterVillageDesigns.Design>> rows = new ArrayList<>();
        List<HunterVillageDesigns.Design> regular = HunterVillageDesigns.regular();
        for (int i = 0; i < regular.size(); i += PER_ROW)
        {
            rows.add(regular.subList(i, Math.min(regular.size(), i + PER_ROW)));
        }
        rows.add(HunterVillageDesigns.huntsmanVillages());
        List<Plot> plots = new ArrayList<>();
        int along = START;
        for (int row = 0; row < rows.size(); row++)
        {
            List<HunterVillageDesigns.Design> designs = rows.get(row);
            int total = GAP * (designs.size() - 1);
            int deepestRow = 0;
            for (HunterVillageDesigns.Design d : designs)
            {
                total += d.width();
                deepestRow = Math.max(deepestRow, d.depth());
            }
            int lateral = -total / 2;
            for (int column = 0; column < designs.size(); column++)
            {
                HunterVillageDesigns.Design d = designs.get(column);
                // its way in faces you: its depth runs ahead of you, its width sideways
                BlockPos ground = new BlockPos(origin.getX(), floorY, origin.getZ())
                        .relative(look, along + d.depth() / 2).relative(right, lateral + d.width() / 2);
                plots.add(new Plot(d, row, column, ground));
                lateral += d.width() + GAP;
            }
            along += deepestRow + GAP;
        }
        return new Plan(origin, look, floorY, plots);
    }

    /** Where a village's way in is (the open snow at the top of its steps), in the world. */
    public static BlockPos wayIn(Plan plan, Plot plot)
    {
        HunterVillageDesigns.Design d = plot.design();
        return HunterVillages.toWorld(new BlockPos(d.wayInX(), d.groundLayer() + 1, d.wayInZ()), plot.ground(),
                plan.front(), d);
    }

    /** Runs {@code /dross showcase hunter_villages}: plans it and starts building, one plot a tick. */
    public static int run(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));
        Plan plan = plan(level, origin, look);
        JOBS.add(new Job(level, plan, new ArrayDeque<>(plan.plots()), source));
        source.sendSuccess(() -> Component.literal("Building the hunter villages ahead of you (" + plan.plots().size()
                + " plots, one each tick)..."), true);
        return plan.plots().size();
    }

    /** Builds everything at once (the headless tests use it). */
    public static Plan buildNow(ServerLevel level, BlockPos origin, Direction look)
    {
        Plan plan = plan(level, origin, look);
        for (Plot plot : plan.plots())
        {
            build(level, plan, plot);
        }
        return plan;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty())
        {
            return;
        }
        Job job = JOBS.get(0);
        Plot plot = job.todo().poll();
        if (plot != null)
        {
            build(job.level(), job.plan(), plot);
        }
        if (job.todo().isEmpty())
        {
            JOBS.remove(0);
            finish(job);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        JOBS.clear();
    }

    /** Prepares one plot (clears it, lays the floor) and places its village (each decoration a 1 in 2 chance) and its
     * sign. */
    static void build(ServerLevel level, Plan plan, Plot plot)
    {
        build(level, plan, plot, HunterVillages.roll(plot.design(), level.getRandom(), HunterVillages.DECOR_CHANCE));
    }

    /** As {@link #build(ServerLevel, Plan, Plot)}, keeping the decorations {@code keep} says (the tests' way). */
    static void build(ServerLevel level, Plan plan, Plot plot, boolean[] keep)
    {
        HunterVillageDesigns.Design design = plot.design();
        BoundingBox box = HunterVillages.footprint(plot.ground(), plan.front(), design);
        int top = plan.floorY() + (design.layers() - design.groundLayer()) + CLEAR_ABOVE;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState hoarfrost = ModBlocks.HOARFROST.get().defaultBlockState();
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        // the animals of a village built here before go first (each village brings its own: they'd pile up)
        AABB area = new AABB(box.minX() - FLOOR_MARGIN, plan.floorY() - MAX_FILL, box.minZ() - FLOOR_MARGIN,
                box.maxX() + FLOOR_MARGIN + 1, top + 1, box.maxZ() + FLOOR_MARGIN + 1);
        level.getEntitiesOfClass(Mob.class, area, m -> m instanceof Moose || m instanceof Rimehorn).forEach(Mob::discard);
        // its villagers, wherever they've wandered (within their home's reach)
        level.getEntitiesOfClass(HunterFolk.class, area.inflate(FolkTuning.HOME_RADIUS), h -> !h.isShowcase()
                && (area.contains(h.position()) || h.home() != null && area.contains(Vec3.atBottomCenterOf(h.home()))))
                .forEach(Entity::discard);
        VillageAnimals.cancelIn(level, area);
        level.getEntitiesOfClass(ArmorStand.class, area).forEach(Entity::discard);   // the guards too
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX() - FLOOR_MARGIN; x <= box.maxX() + FLOOR_MARGIN; x++)
        {
            for (int z = box.minZ() - FLOOR_MARGIN; z <= box.maxZ() + FLOOR_MARGIN; z++)
            {
                for (int y = plan.floorY() + 1; y <= top; y++)
                {
                    if (!level.getBlockState(pos.set(x, y, z)).isAir())
                    {
                        level.setBlock(pos, air, FLAGS);
                    }
                }
                level.setBlock(pos.set(x, plan.floorY(), z), patch(x, z) ? snow : hoarfrost, FLAGS);
                for (int dy = 1; dy <= MAX_FILL; dy++)
                {
                    pos.set(x, plan.floorY() - dy, z);
                    if (pos.getY() < level.getMinBuildHeight() || Showcases.isSolidGround(level, pos))
                    {
                        break;
                    }
                    level.setBlock(pos, dirt, FLAGS);
                }
            }
        }
        int kept = HunterVillages.place(level, plot.ground(), plan.front(), design, keep);
        if (kept < 0)
        {
            return;
        }
        // its sign on a post of packed ice, beside its way in, facing you
        BlockPos post = wayIn(plan, plot).relative(plan.front(), SIGN_OUT).relative(plan.look().getClockWise(), SIGN_SIDE);
        level.setBlock(post, Blocks.PACKED_ICE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(post.above(), Blocks.PACKED_ICE.defaultBlockState(), Block.UPDATE_ALL);
        String[] lines = design.huntsman()
                ? new String[] {"The Huntsman's", "village " + (design.number() - HunterVillageDesigns.regular().size()), design.igloos() + " igloos",
                        "his lodge, his barn"}
                : new String[] {"Sunken Warren", "No. " + design.number(), design.igloos() + " igloos",
                        design.courts() + " courtyards"};
        Showcases.placeWallSign(level, post.above().relative(plan.front()), plan.front(), lines);
        LOGGER.info("Hunter village showcase: {} at {} facing {}: {} of {} decorations", design.id(),
                plot.ground().toShortString(), plan.front().getName(), kept, design.decorations().size());
    }

    /** Snow patches in the Hoarfrost floor (a smooth pattern, the same every time). */
    private static boolean patch(int x, int z)
    {
        double n = Math.sin(x * 0.21D) * Math.cos(z * 0.17D) + 0.5D * Math.sin((x + z) * 0.11D + 1.3D);
        return n > 1.0D - 2.0D * SNOW_SHARE;
    }

    private static void finish(Job job)
    {
        Plan plan = job.plan();
        CommandSourceStack source = job.source();
        LOGGER.info("Hunter village showcase: built {} plots from {} looking {} (floor at y {})", plan.plots().size(),
                plan.origin().toShortString(), plan.look().getName(), plan.floorY());
        source.sendSuccess(() -> Component.literal("Built the hunter villages: the Sunken Warrens 1-3, 4-6 and 7-9 (the "
                + "shrine in 3, 6 and 9), then the Huntsman's two villages (his lodge and barn at the back). Each "
                + "decoration is a 1 in 2 chance, so "
                + "building them again gives other mixes. Click [go] to stand at one's way in:"), true);
        for (Plot plot : plan.plots())
        {
            source.sendSuccess(() -> goLine(plan, plot), false);
        }
        source.sendSuccess(() -> overviewLine(plan), false);
        source.sendSuccess(() -> Component.literal("Try them at night too (/time set night): the windows glow."), false);
    }

    private static Component goLine(Plan plan, Plot plot)
    {
        HunterVillageDesigns.Design design = plot.design();
        BlockPos stand = wayIn(plan, plot).relative(plan.front(), STAND_OUT);
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 15", stand.getX() + 0.5D,
                plan.floorY() + 1, stand.getZ() + 0.5D, plan.look().toYRot());
        MutableComponent line = Component.literal("[go]").withStyle(style -> style
                .withColor(DrossColors.HUNTER_VILLAGE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
        String text = String.format(Locale.ROOT, " %s: %s (%d decorations, each a 1 in 2 chance)", design.name(),
                design.mood(), design.decorations().size());
        return line.append(Component.literal(text));
    }

    private static Component overviewLine(Plan plan)
    {
        int far = 0;
        for (Plot plot : plan.plots())
        {
            far = Math.max(far, Math.abs(plot.ground().getX() - plan.origin().getX())
                    + Math.abs(plot.ground().getZ() - plan.origin().getZ()));
        }
        BlockPos middle = new BlockPos(plan.origin().getX(), plan.floorY(), plan.origin().getZ())
                .relative(plan.look(), far / 2);
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 90", middle.getX() + 0.5D,
                plan.floorY() + OVERVIEW_HEIGHT, middle.getZ() + 0.5D, plan.look().toYRot());
        return Component.literal("[overview]").withStyle(style -> style
                        .withColor(DrossColors.HUNTER_VILLAGE_OVERVIEW_BUTTON)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))))
                .append(Component.literal(" high above the middle, looking down (fly, or you'll fall)"));
    }
}
