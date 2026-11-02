package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.fire.cave.HermitCaveDesigns;
import com.reg21meme.dross.boss.fire.cave.HermitCaves;
import com.reg21meme.dross.registry.ModBlocks;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.entity.Entity;
import com.reg21meme.dross.boss.cinder.CinderHermit;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * {@code /dross showcase hermit_caves} (test tool, cheats only): builds the hermit crab caves (the boss area's
 * {@link HermitCaveDesigns}, made by tools/fire/hermit_caves.py; the user, October 31, 2026: "I want all of these designs
 * in a showcase") in a row ahead of you, their ways in facing you, each on its own block of prepared ground: Duskstone
 * (the Dross's rock) {@link #UNDER} deep, Ash on top with patches of basalt and blackstone, the air cleared over it. A
 * hillside cave (round 2, the user's "he builds inside the hill") is built into a hill raised behind its way in, the way
 * it sits in the world: basalt and blackstone under ash, rising {@link #HILL_SLOPE} blocks a block from its way in to
 * {@link #HILL_TOP} (a Cinder Wastes mesa's edge; the same hill as tools/fire/hermit_caves.py's previews). An underground
 * one goes under the flat ground. Each has its one Cinder Hermit. A sign by each way in ("No. N", its name); one cave is
 * built each server tick; when it's done the chat lists them with [go] buttons (each stands you at a way in, facing in)
 * and an [overview]. Running it again rebuilds them.
 * In a world whose ground is too near the bottom (a superflat world) the plots' ground is raised so the caves fit.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HermitCaveShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String COMMAND = "/dross showcase hermit_caves";
    /** Blocks between neighbouring plots. */
    static final int GAP = 8;
    /** The plots start this many blocks ahead of you. */
    static final int START = 6;
    /** The prepared ground reaches this far past each cave's footprint, and this deep under its ground block. */
    static final int MARGIN = 3;
    static final int UNDER = 14;
    /** Air is cleared this high over the ground. */
    static final int CLEAR_ABOVE = 16;
    /** A hillside cave's hill: this many blocks over the ground at its way in, rising this many blocks a block back from
     * its way in's row, and dropping this many a block past the sides and back of its footprint (tools/fire/
     * hermit_caves.py's HILL_TOP and HILL_SLOPE). */
    static final int HILL_TOP = 12;
    static final double HILL_SLOPE = 2.5D;
    static final int HILL_SIDE_DROP = 4;
    /** The sign stands this many blocks out from the way in and this many to its right; a [go] button puts you this
     * far out from it. */
    static final int SIGN_OUT = 1;
    static final int SIGN_SIDE = 5;
    static final int STAND_OUT = 2;
    /** The [overview] button: this high over the ground, over the middle of the row, looking down. */
    static final int OVERVIEW_HEIGHT = 45;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /** One plot: its cave, its number, its middle's ground block. */
    public record Plot(int number, HermitCaveDesigns.Cave cave, BlockPos ground)
    {
    }

    /** Where everything goes: the plots, the way they face (their ways in towards you), the ground's height. */
    public record Plan(BlockPos origin, Direction look, int groundY, List<Plot> plots)
    {
        public Direction front()
        {
            return look.getOpposite();
        }
    }

    private record Job(ServerLevel level, Plan plan, Deque<Plot> todo, CommandSourceStack source)
    {
    }

    private static final List<Job> JOBS = new ArrayList<>();

    private HermitCaveShowcase() {}

    /** Lays the plots out in a row ahead of {@code origin} (a ground block), looking {@code look}. */
    public static Plan plan(ServerLevel level, BlockPos origin, Direction look)
    {
        int deepest = 0;
        int above = 0;
        for (HermitCaveDesigns.Cave cave : HermitCaveDesigns.CAVES)
        {
            deepest = Math.max(deepest, cave.groundLayer());
            above = Math.max(above, cave.layers() - cave.groundLayer());
        }
        int groundY = Mth.clamp(origin.getY(), level.getMinBuildHeight() + Math.max(deepest, UNDER) + 2,
                level.getMaxBuildHeight() - above - CLEAR_ABOVE - 2);
        Direction right = look.getClockWise();
        List<HermitCaveDesigns.Cave> caves = HermitCaveDesigns.CAVES;
        int total = GAP * (caves.size() - 1);
        int depth = 0;
        for (HermitCaveDesigns.Cave cave : caves)
        {
            total += cave.width();
            depth = Math.max(depth, cave.depth());
        }
        int lateral = -total / 2;
        List<Plot> plots = new ArrayList<>();
        for (int i = 0; i < caves.size(); i++)
        {
            HermitCaveDesigns.Cave cave = caves.get(i);
            BlockPos ground = new BlockPos(origin.getX(), groundY, origin.getZ())
                    .relative(look, START + depth / 2).relative(right, lateral + cave.width() / 2);
            plots.add(new Plot(i + 1, cave, ground));
            lateral += cave.width() + GAP;
        }
        return new Plan(origin, look, groundY, plots);
    }

    /** Runs {@code /dross showcase hermit_caves}: plans it and starts building, one cave a tick. */
    public static int run(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));
        Plan plan = plan(level, origin, look);
        JOBS.add(new Job(level, plan, new ArrayDeque<>(plan.plots()), source));
        source.sendSuccess(() -> Component.literal("Building the " + plan.plots().size()
                + " hermit crab caves ahead of you (one each tick)..."), true);
        return plan.plots().size();
    }

    /** Builds them all at once (the headless test uses it). */
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

    /** Prepares one plot (its block of ground, the air over it), places its cave and its sign. */
    public static void build(ServerLevel level, Plan plan, Plot plot)
    {
        BoundingBox box = HermitCaves.footprint(plot.ground(), plan.front(), plot.cave());
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState rock = ModBlocks.DUSKSTONE.get().defaultBlockState();
        BlockState ash = ModBlocks.ASH.get().defaultBlockState();
        BlockState basalt = Blocks.BASALT.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.Y);
        BlockState blackstone = Blocks.BLACKSTONE.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int y0 = plan.groundY();
        for (int x = box.minX() - MARGIN; x <= box.maxX() + MARGIN; x++)
        {
            for (int z = box.minZ() - MARGIN; z <= box.maxZ() + MARGIN; z++)
            {
                for (int y = y0 - UNDER; y < y0; y++)
                {
                    level.setBlock(pos.set(x, y, z), rock, FLAGS);
                }
                double n = Math.sin(x * 0.23D) * Math.cos(z * 0.19D) + 0.45D * Math.sin((x - z) * 0.13D + 0.7D);
                int hill = plot.cave().hillside() ? hillHeight(plan, plot, x, z) : 0;
                level.setBlock(pos.set(x, y0, z), hill > 0 ? blackstone : n > 0.95D ? basalt : n < -1.05D ? blackstone : ash,
                        FLAGS);
                for (int y = y0 + 1; y <= y0 + CLEAR_ABOVE; y++)
                {
                    BlockState want = y > y0 + hill ? air : y == y0 + hill ? ash
                            : y >= y0 + hill - 2 || ((x * 3 + y * 5 + z * 7) & 3) != 0 ? basalt : blackstone;
                    if (level.getBlockState(pos.set(x, y, z)) != want)
                    {
                        level.setBlock(pos, want, FLAGS);
                    }
                }
            }
        }
        // the hermits of a cave built here before go with it
        BoundingBox footprint = HermitCaves.footprint(plot.ground(), plan.front(), plot.cave());
        level.getEntitiesOfClass(CinderHermit.class, AABB.of(footprint).inflate(MARGIN, CLEAR_ABOVE, MARGIN)).forEach(Entity::discard);
        if (!HermitCaves.place(level, plot.cave(), plot.ground(), plan.front()))
        {
            return;
        }
        HermitCaves.spawnCrabs(level, HermitCaves.crabSpots(plot.ground(), plan.front(), plot.cave()), null);
        // its sign on a post of polished blackstone beside its way in, facing you
        BlockPos door = HermitCaves.door(plot.ground(), plan.front(), plot.cave());
        BlockPos post = door.relative(plan.front(), SIGN_OUT).relative(plan.look().getClockWise(), SIGN_SIDE);
        level.setBlock(post, Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(post.below(), Blocks.POLISHED_BLACKSTONE.defaultBlockState(), Block.UPDATE_ALL);
        Showcases.placeWallSign(level, post.relative(plan.front()), plan.front(), signLines(plot));
    }

    /**
     * How high a hillside cave's hill stands over the ground at this column: nothing in front of its way in, rising
     * {@link #HILL_SLOPE} blocks a block back from the row of its way in to {@link #HILL_TOP}, dropping
     * {@link #HILL_SIDE_DROP} a block past the sides and back of its footprint.
     */
    static int hillHeight(Plan plan, Plot plot, int x, int z)
    {
        BlockPos door = HermitCaves.door(plot.ground(), plan.front(), plot.cave());
        Direction into = plan.look();
        Direction right = into.getClockWise();
        int back = (x - door.getX()) * into.getStepX() + (z - door.getZ()) * into.getStepZ();
        int side = Math.abs((x - plot.ground().getX()) * right.getStepX() + (z - plot.ground().getZ()) * right.getStepZ());
        if (back < 0)
        {
            return 0;
        }
        int h = Math.min(HILL_TOP, (int) (back * HILL_SLOPE + 0.5D));
        int half = plot.cave().width() / 2;
        if (side > half)
        {
            h -= HILL_SIDE_DROP * (side - half);
        }
        int depth = plot.cave().depth() - 1;
        if (back > depth)
        {
            h -= HILL_SIDE_DROP * (back - depth);
        }
        return Math.max(0, h);
    }

    /** "No. 3", the name over two lines, "hermit cave". */
    static String[] signLines(Plot plot)
    {
        String[] name = HunterIglooShowcase.split(plot.cave().name(), 15);
        return new String[] {"No. " + plot.number(), name[0], name[1], "hermit cave"};
    }

    private static void finish(Job job)
    {
        Plan plan = job.plan();
        CommandSourceStack source = job.source();
        LOGGER.info("Hermit cave showcase: built {} caves from {} looking {} (ground at y {})", plan.plots().size(),
                plan.origin().toShortString(), plan.look().getName(), plan.groundY());
        source.sendSuccess(() -> Component.literal("Built the " + plan.plots().size() + " hermit crab caves (No. 1 on the "
                + "left): hillside dens dug into a hill, and underground dens under a mound with a loot room behind, one "
                + "Cinder Hermit in each. Click [go] to stand at a way in:"), true);
        for (Plot plot : plan.plots())
        {
            source.sendSuccess(() -> goLine(plan, plot), false);
        }
        source.sendSuccess(() -> overviewLine(plan), false);
        source.sendSuccess(() -> Component.literal("Every lava block is walled in, and every floor is lit (try it at "
                + "night, /time set night)."), false);
    }

    /** Where a [go] button stands you: a little out from the way in, facing in. */
    public static BlockPos stand(Plan plan, Plot plot)
    {
        return HermitCaves.door(plot.ground(), plan.front(), plot.cave()).relative(plan.front(), STAND_OUT);
    }

    private static Component goLine(Plan plan, Plot plot)
    {
        BlockPos stand = stand(plan, plot);
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 15", stand.getX() + 0.5D, stand.getY(),
                stand.getZ() + 0.5D, plan.look().toYRot());
        MutableComponent line = Component.literal("[go]").withStyle(style -> style
                .withColor(DrossColors.HERMIT_CAVE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
        return line.append(Component.literal(String.format(Locale.ROOT, " No. %d %s (%s)", plot.number(),
                plot.cave().name(), plot.cave().hillside() ? "hillside" : "underground, a loot room behind")));
    }

    private static Component overviewLine(Plan plan)
    {
        BlockPos middle = plan.plots().get(plan.plots().size() / 2).ground();
        BlockPos over = middle.relative(plan.look(), -20);
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 55", over.getX() + 0.5D,
                plan.groundY() + OVERVIEW_HEIGHT, over.getZ() + 0.5D, plan.look().toYRot());
        return Component.literal("[overview]").withStyle(style -> style
                        .withColor(DrossColors.HERMIT_CAVE_OVERVIEW_BUTTON)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))))
                .append(Component.literal(" high over the row, looking down (fly, or you'll fall)"));
    }
}
