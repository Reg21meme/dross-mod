package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.huntsman.village.HunterIglooDesigns;
import com.reg21meme.dross.boss.huntsman.village.HunterIgloos;
import com.reg21meme.dross.boss.huntsman.village.VillageAnimals;
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

import javax.annotation.Nullable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Locale;

/**
 * {@code /dross showcase hunter_igloos} (test tool, cheats only): builds every hunter igloo and every decoration ahead of
 * you on a snowy floor, each on its own plot facing you, for the user to pick from before they're joined into villages
 * (the plan of October 20, 2026: "do an igloo showcase, and I'll decide which one"; "a decoration showcase ... I'll
 * note the ones I don't like"). The boss area's {@link HunterIglooDesigns} (made by tools/huntsman/igloos.py).
 * <p>
 * Rows of plots, their fronts (an igloo's doorway) facing you: the six homes; the storehouse, cookhouse, barracks,
 * workshop and the two halls; the extras, the Snow Angel's shrine, the Huntsman's lodge and his barn; the farms (the
 * Root House, the Lichen House, the Rimeberry Patch); then the decorations, {@link #DECOR_PER_ROW} to a row. A plot built
 * again loses the armor stands (and dropped items) of the one before. Each plot is cleared, floored with Hoarfrost and snow and gets a numbered sign in front of it (igloos "No. N",
 * decorations "D N", as in the previews). One plot is built each server tick; when it's done the chat lists the igloos
 * with [go] buttons (each stands you at its doorway) and an [overview] button. Use a flat world: it's about 220 blocks
 * wide and 160 deep.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class HunterIglooShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String COMMAND = "/dross showcase hunter_igloos";
    /** Blocks between neighbouring plots, and between rows (more between the igloos' rows and the decorations'). */
    static final int GAP = 6;
    static final int ROW_GAP = 9;
    /** The first row's plots start this many blocks ahead of you. */
    static final int START = 6;
    /** Decorations to a row. */
    static final int DECOR_PER_ROW = 10;
    /** Air is cleared this many blocks above each plot's tallest part, and the floor reaches this far past it. */
    static final int CLEAR_ABOVE = 3;
    static final int FLOOR_MARGIN = 3;
    /** Dips under the floor are filled at most this deep (with snow). */
    static final int MAX_FILL = 12;
    /** The sign stands this many blocks out from a plot's front edge; a [go] button puts you this far out from a doorway. */
    static final int SIGN_OUT = 2;
    static final int STAND_OUT = 2;
    /** The [overview] button: this high over the floor, in the middle of the plots. */
    static final int OVERVIEW_HEIGHT = 70;
    /** About this share of the floor is snow blocks (in patches), the rest Hoarfrost. */
    static final double SNOW_SHARE = 0.3D;
    private static final int FLAGS = Block.UPDATE_CLIENTS;

    /** One plot: what's on it (an igloo, or a decoration), its number in its list, its middle's ground block. */
    public record Plot(int number, String name, HunterIgloos.Piece piece, @Nullable HunterIglooDesigns.Igloo igloo,
                       BlockPos ground)
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

    private record Job(ServerLevel level, Plan plan, Deque<Plot> todo, CommandSourceStack source)
    {
    }

    private static final List<Job> JOBS = new ArrayList<>();

    private HunterIglooShowcase() {}

    /** Each row of igloos, by kind: the homes; the work igloos and the halls; the extras, the shrine and the Huntsman's
     * two; the farms. The decorations' rows come after them. */
    static final String[][] IGLOO_ROWS = {{"home"}, {"storage", "cook", "barracks", "workshop", "hall"},
            {"extra", "shrine", "lodge"}, {"farm"}};

    /** The igloos' rows ({@link #IGLOO_ROWS}), then the decorations'. */
    static List<List<Plot>> rows()
    {
        List<List<Plot>> rows = new ArrayList<>();
        List<HunterIglooDesigns.Igloo> all = HunterIglooDesigns.IGLOOS;
        String[][] kinds = IGLOO_ROWS;
        for (String[] row : kinds)
        {
            List<Plot> plots = new ArrayList<>();
            for (int i = 0; i < all.size(); i++)
            {
                HunterIglooDesigns.Igloo igloo = all.get(i);
                if (List.of(row).contains(igloo.kind()))
                {
                    plots.add(new Plot(i + 1, igloo.name(), HunterIgloos.Piece.of(igloo), igloo, BlockPos.ZERO));
                }
            }
            rows.add(plots);
        }
        List<HunterIglooDesigns.Decoration> decor = HunterIglooDesigns.DECORATIONS;
        for (int i = 0; i < decor.size(); i += DECOR_PER_ROW)
        {
            List<Plot> plots = new ArrayList<>();
            for (int k = i; k < Math.min(decor.size(), i + DECOR_PER_ROW); k++)
            {
                HunterIglooDesigns.Decoration d = decor.get(k);
                plots.add(new Plot(k + 1, d.name(), HunterIgloos.Piece.of(d), null, BlockPos.ZERO));
            }
            rows.add(plots);
        }
        return rows;
    }

    /** Lays the plots out ahead of {@code origin} (a ground block), looking {@code look}. */
    public static Plan plan(ServerLevel level, BlockPos origin, Direction look)
    {
        int floorY = Math.max(origin.getY(), level.getMinBuildHeight() + 3);
        Direction right = look.getClockWise();
        List<Plot> plots = new ArrayList<>();
        int along = START;
        List<List<Plot>> rows = rows();
        for (int r = 0; r < rows.size(); r++)
        {
            List<Plot> row = rows.get(r);
            int total = GAP * (row.size() - 1);
            int deepest = 0;
            for (Plot p : row)
            {
                total += p.piece().width();
                deepest = Math.max(deepest, p.piece().depth());
            }
            int lateral = -total / 2;
            for (Plot p : row)
            {
                BlockPos ground = new BlockPos(origin.getX(), floorY, origin.getZ())
                        .relative(look, along + deepest / 2).relative(right, lateral + p.piece().width() / 2);
                plots.add(new Plot(p.number(), p.name(), p.piece(), p.igloo(), ground));
                lateral += p.piece().width() + GAP;
            }
            along += deepest + (r == IGLOO_ROWS.length - 1 ? 2 * ROW_GAP : ROW_GAP);
        }
        return new Plan(origin, look, floorY, plots);
    }

    /** Runs {@code /dross showcase hunter_igloos}: plans it and starts building, one plot a tick. */
    public static int run(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));
        Plan plan = plan(level, origin, look);
        JOBS.add(new Job(level, plan, new ArrayDeque<>(plan.plots()), source));
        source.sendSuccess(() -> Component.literal("Building the hunter igloos and decorations ahead of you ("
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

    /** Prepares one plot (clears it, lays the floor), places its igloo or decoration and its sign. */
    static void build(ServerLevel level, Plan plan, Plot plot)
    {
        BoundingBox box = HunterIgloos.footprint(plot.ground(), plan.front(), plot.piece());
        int top = plan.floorY() + (plot.piece().layers() - plot.piece().groundLayer()) + CLEAR_ABOVE;
        // the armor stands of a showcase built here before (a template brings its own), and the crops' pickings
        AABB area = new AABB(box.minX() - FLOOR_MARGIN, plan.floorY(), box.minZ() - FLOOR_MARGIN,
                box.maxX() + FLOOR_MARGIN + 1, top + 1, box.maxZ() + FLOOR_MARGIN + 1);
        VillageAnimals.cancelIn(level, area);
        level.getEntitiesOfClass(ArmorStand.class, area).forEach(Entity::discard);
        level.getEntitiesOfClass(ItemEntity.class, area).forEach(Entity::discard);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState hoarfrost = ModBlocks.HOARFROST.get().defaultBlockState();
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
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
                    level.setBlock(pos, snow, FLAGS);
                }
            }
        }
        if (!HunterIgloos.place(level, plot.piece(), plot.ground(), plan.front()))
        {
            return;
        }
        // its sign on a post of packed ice in front of it, facing you
        int out = plot.piece().depth() / 2 + SIGN_OUT;
        BlockPos post = plot.ground().relative(plan.front(), out).above();
        if (plot.igloo() != null)
        {
            post = post.relative(plan.look().getClockWise(), 2);
        }
        level.setBlock(post, Blocks.PACKED_ICE.defaultBlockState(), Block.UPDATE_ALL);
        Showcases.placeWallSign(level, post.relative(plan.front()), plan.front(), signLines(plot));
    }

    /** "No. 3" / "D 12", the name over two lines, and what kind it is. */
    static String[] signLines(Plot plot)
    {
        String head = plot.igloo() != null ? "No. " + plot.number() : "D " + plot.number();
        String[] name = split(plot.name(), 15);
        String kind = plot.igloo() != null ? plot.igloo().kind() : "decoration";
        return new String[] {head, name[0], name[1], kind};
    }

    /** A name over two lines of at most {@code width} characters (cut at a space). */
    static String[] split(String name, int width)
    {
        if (name.length() <= width)
        {
            return new String[] {name, ""};
        }
        int cut = name.lastIndexOf(' ', width);
        if (cut <= 0)
        {
            return new String[] {name.substring(0, width), name.substring(width)};
        }
        return new String[] {name.substring(0, cut), name.substring(cut + 1)};
    }

    /** Snow patches in the Hoarfrost floor (a smooth pattern, the same every time). */
    private static boolean patch(int x, int z)
    {
        double n = Math.sin(x * 0.21D) * Math.cos(z * 0.17D) + 0.5D * Math.sin((x + z) * 0.11D + 1.3D);
        return n > 1.0D - 2.0D * SNOW_SHARE;
    }

    /** Where an igloo's doorway is (its outer cell, at floor height), in the world. */
    public static BlockPos doorway(Plan plan, Plot plot)
    {
        HunterIglooDesigns.Igloo igloo = plot.igloo();
        return HunterIgloos.toWorld(new BlockPos(igloo.doorX(), igloo.groundLayer() + 1, igloo.doorZ()), plot.ground(),
                plan.front(), plot.piece());
    }

    private static void finish(Job job)
    {
        Plan plan = job.plan();
        CommandSourceStack source = job.source();
        LOGGER.info("Hunter igloo showcase: built {} plots from {} looking {} (floor at y {})", plan.plots().size(),
                plan.origin().toShortString(), plan.look().getName(), plan.floorY());
        source.sendSuccess(() -> Component.literal("Built the hunter igloos (rows of homes, work igloos and halls, the "
                + "extras and the Huntsman's lodge and barn) and, behind them, the " + HunterIglooDesigns.DECORATIONS.size()
                + " decorations (signs D 1 to D " + HunterIglooDesigns.DECORATIONS.size() + "). Tell me which igloos you "
                + "want and which decorations you don't. Click [go] to stand at an igloo's doorway:"), true);
        for (Plot plot : plan.plots())
        {
            if (plot.igloo() != null)
            {
                source.sendSuccess(() -> goLine(plan, plot), false);
            }
        }
        source.sendSuccess(() -> overviewLine(plan), false);
        source.sendSuccess(() -> Component.literal("Try them at night too (/time set night): every room is lit."), false);
    }

    private static Component goLine(Plan plan, Plot plot)
    {
        BlockPos stand = doorway(plan, plot).relative(plan.front(), STAND_OUT);
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 10", stand.getX() + 0.5D, plan.floorY() + 1,
                stand.getZ() + 0.5D, plan.look().toYRot());
        MutableComponent line = Component.literal("[go]").withStyle(style -> style
                .withColor(DrossColors.HUNTER_VILLAGE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
        return line.append(Component.literal(String.format(Locale.ROOT, " No. %d %s (%s)", plot.number(), plot.name(),
                plot.igloo().kind())));
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
