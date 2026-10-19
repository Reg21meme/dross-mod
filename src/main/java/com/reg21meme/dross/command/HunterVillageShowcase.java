package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.huntsman.village.HunterVillageDesigns;
import com.reg21meme.dross.boss.huntsman.village.HunterVillages;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
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
 * {@code /dross showcase hunter_villages} (test tool, cheats only): builds the ten hunter village designs and the
 * Huntsman's lodge ahead of you on a snowy floor, so they can be looked at and compared (the boss area's
 * {@link HunterVillageDesigns}, made by tools/huntsman/villages.py).
 * <p>
 * Two rows of plots {@link #PITCH} blocks apart, their ways in facing you: the first row villages 1-5 and the lodge
 * (on the right), the second row villages 6-10. Each plot is cleared ({@link #CLEAR_ABOVE} blocks over its village),
 * floored with Hoarfrost and snow like Rimefall, and gets a sign at its way in. One plot is built each server tick,
 * so the game doesn't freeze; the chat lists them all with [go] buttons when it's done, and an [overview] button that
 * puts you high above the middle looking down. The floor is raised if the ground is too close to the bottom of the
 * world for the deepest village (a superflat world's is). Use a flat world: it's about 430 blocks wide and 150 deep.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HunterVillageShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String COMMAND = "/dross showcase hunter_villages";
    /** Blocks between neighbouring plots' middles (every design is at most 63 across). */
    static final int PITCH = 72;
    /** Plots in a row (the first row has the lodge as its sixth). */
    static final int COLUMNS = 6;
    /** The first row's plots start this many blocks ahead of you. */
    static final int START = 6;
    /** Air is cleared this many blocks above each village's tallest part, and the floor reaches this far past it. */
    static final int CLEAR_ABOVE = 4;
    static final int FLOOR_MARGIN = 4;
    /** Dips under the floor are filled at most this deep. */
    static final int MAX_FILL = 12;
    /** About this share of the floor is snow blocks (in patches), the rest Hoarfrost, like Rimefall's ground. */
    static final double SNOW_SHARE = 0.3D;
    /** The sign stands this many blocks in front of a village's way in, a [go] button puts you this far out. */
    static final int SIGN_OUT = 3;
    static final int STAND_OUT = 7;
    /** The [overview] button: this high over the floor, in the middle of the plots. */
    static final int OVERVIEW_HEIGHT = 90;
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
        List<Plot> plots = new ArrayList<>();
        List<HunterVillageDesigns.Design> villages = HunterVillageDesigns.villages();
        for (int i = 0; i < villages.size(); i++)
        {
            int row = i / 5;
            int column = i % 5;
            plots.add(plot(villages.get(i), row, column, origin, look, right, floorY));
        }
        plots.add(plot(HunterVillageDesigns.lodge(), 0, COLUMNS - 1, origin, look, right, floorY));
        return new Plan(origin, look, floorY, plots);
    }

    private static Plot plot(HunterVillageDesigns.Design design, int row, int column, BlockPos origin, Direction look,
                             Direction right, int floorY)
    {
        int along = START + PITCH / 2 + row * PITCH;
        int lateral = Math.round((column - (COLUMNS - 1) / 2.0F) * PITCH);
        BlockPos ground = new BlockPos(origin.getX(), floorY, origin.getZ()).relative(look, along).relative(right, lateral);
        return new Plot(design, row, column, ground);
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
        source.sendSuccess(() -> Component.literal("Building the ten hunter villages and the Huntsman's lodge ahead of you ("
                + plan.plots().size() + " plots, one each tick)..."), true);
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

    /** Prepares one plot (clears it, lays the floor) and places its village and its sign. */
    static void build(ServerLevel level, Plan plan, Plot plot)
    {
        HunterVillageDesigns.Design design = plot.design();
        BoundingBox box = HunterVillages.footprint(plot.ground(), plan.front(), design);
        int top = plan.floorY() + (design.layers() - design.groundLayer()) + CLEAR_ABOVE;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState hoarfrost = ModBlocks.HOARFROST.get().defaultBlockState();
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
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
        if (!HunterVillages.place(level, plot.ground(), plan.front(), design))
        {
            return;
        }
        // its sign on a post of packed ice, in front of its way in, facing you
        int halfDepth = design.depth() / 2;
        BlockPos post = plot.ground().relative(plan.front(), halfDepth + SIGN_OUT).above();
        level.setBlock(post, Blocks.PACKED_ICE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(post.above(), Blocks.PACKED_ICE.defaultBlockState(), Block.UPDATE_ALL);
        String[] lines = design.isLodge()
                ? new String[] {"The Huntsman's", "Lodge", "and stable", "(joins a village)"}
                : new String[] {"No. " + design.number(), design.name(), design.igloos() + " igloos", ""};
        Showcases.placeWallSign(level, post.above().relative(plan.front()), plan.front(), lines);
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
        source.sendSuccess(() -> Component.literal("Built the hunter villages: the first row is Nos. 1-5 and the "
                + "Huntsman's lodge (right), the second row Nos. 6-10. Click [go] to stand at one's way in:"), true);
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
        BlockPos stand = plot.ground().relative(plan.front(), design.depth() / 2 + STAND_OUT);
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 15", stand.getX() + 0.5D,
                plan.floorY() + 1, stand.getZ() + 0.5D, plan.look().toYRot());
        MutableComponent line = Component.literal("[go]").withStyle(style -> style
                .withColor(DrossColors.HUNTER_VILLAGE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
        String text = design.isLodge()
                ? " The Huntsman's lodge and stable: " + design.mood()
                : String.format(Locale.ROOT, " No. %d %s: %s (%d igloos)", design.number(), design.name(), design.mood(),
                design.igloos());
        return line.append(Component.literal(text));
    }

    private static Component overviewLine(Plan plan)
    {
        BlockPos middle = new BlockPos(plan.origin().getX(), plan.floorY(), plan.origin().getZ())
                .relative(plan.look(), START + PITCH);
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 90", middle.getX() + 0.5D,
                plan.floorY() + OVERVIEW_HEIGHT, middle.getZ() + 0.5D, plan.look().toYRot());
        return Component.literal("[overview]").withStyle(style -> style
                        .withColor(DrossColors.HUNTER_VILLAGE_OVERVIEW_BUTTON)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))))
                .append(Component.literal(" high above the middle, looking down (fly, or you'll fall)"));
    }
}
