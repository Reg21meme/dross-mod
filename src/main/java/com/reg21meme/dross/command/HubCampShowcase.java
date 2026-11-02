package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.portal.camp.HubCampDesigns;
import com.reg21meme.dross.world.shrine.ShrineDesign;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * {@code /dross showcase hub_camps} (test tool, cheats only): the survivors' camp candidates (the user's plan of October
 * 28, 2026: the Blind Oracle and the Ragged Seeker live in a camp at the foot of the Shattered Spire's front steps; the
 * user, November 2: "include the hub camps as well into the showcase"). Builds the Shattered Spire once per camp
 * ({@link ShrineDesign#build}, as the game builds the hub), in a row ahead of you, their fronts facing you, then each
 * camp's template ({@link HubCampDesigns}, made by tools/hub_camp/camps.py: only what the camp changes on the spire) on
 * top at the same corner and turn. A sign on the road in front of each camp ("No. N", its name); one spire and camp a
 * server tick; when it's done the chat lists them with [go] buttons (each stands you on the road in front of a camp,
 * facing the steps) and an [overview]. The survivors aren't in it (their home is the real hub). Running it again builds
 * them again in front of where you stand.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HubCampShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String COMMAND = "/dross showcase hub_camps";
    /** Blocks between neighbouring spires' footprints. */
    static final int GAP = 16;
    /** The spires' fronts start this many blocks ahead of you. */
    static final int START = 8;
    /** A [go] button stands you this many blocks in front of the camp's box, on the road; the sign stands one block
     *  this many out (its post a block nearer, just outside the camp's box), this many blocks to the side. */
    static final int STAND_OUT = 5;
    static final int SIGN_OUT = 2;
    static final int SIGN_SIDE = 4;
    /** The [overview] button: this high over the ground, this far back from the row, looking down. */
    static final int OVERVIEW_HEIGHT = 70;
    static final int OVERVIEW_BACK = 40;

    /** One plot: its camp and the spire's center (its ground block in the middle of the footprint). */
    public record Plot(HubCampDesigns.Camp camp, BlockPos center)
    {
    }

    /** The row: the plots, the way you look (into the spires; their fronts face the other way), the ground's height. */
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

    private HubCampShowcase() {}

    static ShrineDesign spire()
    {
        return ShrineDesign.byId("shattered_spire").orElseThrow();
    }

    /** Lays the spires out in a row ahead of {@code origin} (a ground block), looking {@code look}. */
    public static Plan plan(ServerLevel level, BlockPos origin, Direction look)
    {
        ShrineDesign spire = spire();
        int groundY = Mth.clamp(origin.getY(), level.getMinBuildHeight() + spire.groundLayer() + 1,
                level.getMaxBuildHeight() - spire.height() - 8);
        Direction right = look.getClockWise();
        List<HubCampDesigns.Camp> camps = HubCampDesigns.CAMPS;
        int total = camps.size() * spire.width() + (camps.size() - 1) * GAP;
        int lateral = -total / 2 + spire.width() / 2;
        List<Plot> plots = new ArrayList<>();
        for (HubCampDesigns.Camp camp : camps)
        {
            BlockPos center = new BlockPos(origin.getX(), groundY, origin.getZ())
                    .relative(look, START + spire.depth() / 2).relative(right, lateral);
            plots.add(new Plot(camp, center));
            lateral += spire.width() + GAP;
        }
        return new Plan(origin, look, groundY, plots);
    }

    /** Runs {@code /dross showcase hub_camps}: plans the row and starts building, one spire and camp a tick. */
    public static int run(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));
        Plan plan = plan(level, origin, look);
        JOBS.add(new Job(level, plan, new ArrayDeque<>(plan.plots()), source));
        source.sendSuccess(() -> Component.literal("Building the Shattered Spire " + plan.plots().size()
                + " times ahead of you, each with one of the survivors' camp designs at its steps (one a tick)..."), true);
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

    /** Builds one plot: the spire (its ground prepared as the game prepares the hub's), its camp on top, the sign. */
    public static boolean build(ServerLevel level, Plan plan, Plot plot)
    {
        ShrineDesign spire = spire();
        if (!spire.build(level, plot.center(), plan.front()))
        {
            return false;
        }
        ResourceLocation id = new ResourceLocation(Dross.MODID, plot.camp().template());
        Optional<StructureTemplate> found = level.getStructureManager().get(id);
        if (found.isEmpty())
        {
            LOGGER.error("Hub camp showcase: the camp template {} is missing", id);
            return false;
        }
        StructurePlaceSettings settings = new StructurePlaceSettings()
                .setRotation(rotation(plan.front()))
                .setRotationPivot(pivot())
                .setIgnoreEntities(true);
        BlockPos origin = origin(plot);
        found.get().placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_ALL);
        // its sign on a post of stripped silverwood by the road in front of the camp, facing you
        BlockPos sign = toWorld(plan, plot, campFront(plot.camp(), SIGN_OUT)).relative(plan.look().getClockWise(), SIGN_SIDE);
        BlockPos post = sign.relative(plan.look());
        level.setBlock(post, Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(post.above(), Blocks.STRIPPED_SPRUCE_LOG.defaultBlockState(), Block.UPDATE_ALL);
        Showcases.placeWallSign(level, sign.above(), plan.front(), signLines(plot.camp()));
        return true;
    }

    /** "No. 2", the name over two lines, "hub camp". */
    static String[] signLines(HubCampDesigns.Camp camp)
    {
        String name = camp.name().startsWith("The ") ? camp.name().substring(4) : camp.name();
        String[] lines = HunterIglooShowcase.split(name, 15);
        return new String[] {"No. " + camp.number(), lines[0], lines[1], "hub camp"};
    }

    /** The template's corner (its ground at the spire's center's height), as the spire's own. */
    static BlockPos origin(Plot plot)
    {
        return plot.center().offset(-HubCampDesigns.WIDTH / 2, -HubCampDesigns.GROUND_LAYER, -HubCampDesigns.DEPTH / 2);
    }

    /** The template turns round its middle column, like the spire's (so both land on the same blocks). */
    static BlockPos pivot()
    {
        return new BlockPos(HubCampDesigns.WIDTH / 2, 0, HubCampDesigns.DEPTH / 2);
    }

    /** The turn that takes "front faces south" (how the templates are made) to "front faces {@code front}". */
    static Rotation rotation(Direction front)
    {
        return switch (front)
        {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    /** A template cell (unturned) to the world, for this plot. */
    static BlockPos toWorld(Plan plan, Plot plot, BlockPos local)
    {
        return StructureTemplate.transform(local, Mirror.NONE, rotation(plan.front()), pivot()).offset(origin(plot));
    }

    /** The road's middle in front of the camp's box, {@code out} blocks past its front edge, a block over the ground. */
    static BlockPos campFront(HubCampDesigns.Camp camp, int out)
    {
        int x = (camp.focusMinX() + camp.focusMaxX()) / 2;
        int z = Math.min(HubCampDesigns.DEPTH - 1, camp.focusMaxZ() + out);
        return new BlockPos(x, HubCampDesigns.GROUND_LAYER + 1, z);
    }

    /** Where a [go] button stands you: on the road in front of the camp, facing the spire's steps. */
    public static BlockPos stand(Plan plan, Plot plot)
    {
        return toWorld(plan, plot, campFront(plot.camp(), STAND_OUT));
    }

    private static void finish(Job job)
    {
        Plan plan = job.plan();
        CommandSourceStack source = job.source();
        LOGGER.info("Hub camp showcase: built {} spires and camps from {} looking {} (ground at y {})",
                plan.plots().size(), plan.origin().toShortString(), plan.look().getName(), plan.groundY());
        source.sendSuccess(() -> Component.literal("Built the Shattered Spire " + plan.plots().size() + " times (No. 1 on "
                + "the left), each with one camp for the Blind Oracle and the Ragged Seeker at the foot of its steps. "
                + "Click [go] to stand in front of one:"), true);
        for (Plot plot : plan.plots())
        {
            source.sendSuccess(() -> goLine(plan, plot), false);
        }
        source.sendSuccess(() -> overviewLine(plan), false);
    }

    private static Component goLine(Plan plan, Plot plot)
    {
        BlockPos stand = stand(plan, plot);
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 10", stand.getX() + 0.5D, stand.getY(),
                stand.getZ() + 0.5D, plan.look().toYRot());
        MutableComponent line = Component.literal("[go]").withStyle(style -> style
                .withColor(DrossColors.HUB_CAMP_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal(plot.camp().blurb() + "\n" + command))));
        return line.append(Component.literal(String.format(Locale.ROOT, " No. %d %s", plot.camp().number(),
                plot.camp().name())));
    }

    private static Component overviewLine(Plan plan)
    {
        BlockPos middle = plan.plots().get(plan.plots().size() / 2).center();
        BlockPos over = middle.relative(plan.look(), -(spire().depth() / 2 + OVERVIEW_BACK));
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 40", over.getX() + 0.5D,
                plan.groundY() + OVERVIEW_HEIGHT, over.getZ() + 0.5D, plan.look().toYRot());
        return Component.literal("[overview]").withStyle(style -> style
                        .withColor(DrossColors.HUB_CAMP_OVERVIEW_BUTTON)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))))
                .append(Component.literal(" high over the row, looking down at the camps (fly, or you'll fall)"));
    }
}
