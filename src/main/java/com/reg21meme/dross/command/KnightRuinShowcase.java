package com.reg21meme.dross.command;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.knight.ElementalKnight;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.boss.knight.KnightRuinDesigns;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;
import javax.annotation.Nullable;

/**
 * {@code /dross showcase knight_ruins <element>} (test tool, cheats only): one element's knight ruin candidates (the boss
 * area's {@link KnightRuinDesigns}, made by tools/knights/ruins.py; the user, November 18, 2026: "each biome should have
 * its own unique layout ... little areas where you fight these guys") ahead of you, a row for each of its biome areas
 * (desert, savanna, badlands...), that area's layouts side by side, their ways in facing you, each on a plot of its
 * area's ground: a beach's back part in the sea, a cavern's walled in with rock under a roof (open at the front), a
 * mountain's on rock. In each its element's two knights stand guard, frozen ({@link ElementalKnight} on show). A sign
 * by each way in; one ruin a server tick; then the chat lists them with [go] buttons and an [overview], and buttons
 * that make every knight near play its idle, its walk, its first or its second move:
 * {@code /dross showcase knight_ruins idle|walk|move1|move2}. With no word, the chat offers a button per element.
 */
@Mod.EventBusSubscriber(modid = com.reg21meme.dross.Dross.MODID)
public final class KnightRuinShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String COMMAND = "/dross showcase knight_ruins";
    /** Blocks between neighbouring plots, and from you to the first row. */
    static final int GAP = 8;
    static final int START = 8;
    /** All five together: blocks between two elements' sections. */
    static final int SECTION_GAP = 24;
    /** Each plot's ground: this far past its footprint, this deep; air cleared over it to its template's top + this. */
    static final int MARGIN = 2;
    static final int UNDER = 5;
    static final int CLEAR_EXTRA = 4;
    /** On a shore, the sea's depth. */
    static final int SEA_DEPTH = 2;
    /** A cavern's rock: its walls this thick, its roof this far over the template's top. */
    static final int CAVE_WALL = 2;
    static final int CAVE_ROOF_GAP = 2;
    /** The [overview] button: this high, this far back. */
    static final int OVERVIEW_HEIGHT = 60;
    /** The animation buttons reach the knights this far round you. */
    static final double PLAY_RANGE = 300.0D;
    private static final int FLAGS = Block.UPDATE_CLIENTS;
    private static final List<String> ANIMATIONS = List.of("idle", "walk", "move1", "move2");

    /** One plot: its ruin, its middle's ground block. */
    public record Plot(KnightRuinDesigns.Design ruin, BlockPos ground)
    {
    }

    /** One build: its element, or null for all five together. */
    public record Plan(BlockPos origin, Direction look, int groundY, @Nullable KnightKind.Element element, List<Plot> plots)
    {
        public Direction front()
        {
            return this.look.getOpposite();
        }
    }

    private record Job(ServerLevel level, Plan plan, Deque<Plot> todo, CommandSourceStack source)
    {
    }

    private static final List<Job> JOBS = new ArrayList<>();

    private KnightRuinShowcase() {}

    /** {@code showcase knight_ruins [<element>|idle|walk|move1|move2]}. */
    public static LiteralArgumentBuilder<CommandSourceStack> command()
    {
        return Commands.literal("knight_ruins").executes(KnightRuinShowcase::runAll)
                .then(Commands.argument("what", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(Stream.concat(
                                Arrays.stream(KnightKind.Element.values()).map(KnightRuinShowcase::key),
                                ANIMATIONS.stream()), b))
                        .executes(c -> word(c, StringArgumentType.getString(c, "what"))));
    }

    static String key(KnightKind.Element element)
    {
        return element.name().toLowerCase(Locale.ROOT);
    }

    static String title(KnightKind.Element element)
    {
        return element.name().charAt(0) + element.name().substring(1).toLowerCase(Locale.ROOT);
    }

    static int colour(KnightKind.Element element)
    {
        return switch (element)
        {
            case ICE -> DrossColors.KNIGHT_ICE;
            case POISON -> DrossColors.KNIGHT_POISON;
            case FIRE -> DrossColors.KNIGHT_FIRE;
            case THUNDER -> DrossColors.KNIGHT_THUNDER;
            case EARTH -> DrossColors.KNIGHT_EARTH;
        };
    }

    private static int word(CommandContext<CommandSourceStack> context, String word)
    {
        if (ANIMATIONS.contains(word))
        {
            return play(context, word);
        }
        for (KnightKind.Element element : KnightKind.Element.values())
        {
            if (key(element).equals(word))
            {
                return run(context, element);
            }
        }
        context.getSource().sendFailure(Component.literal("Unknown: " + word + " (an element: ice, poison, fire, thunder,"
                + " earth; or idle, walk, move1, move2)"));
        return 0;
    }

    /** The element's areas (in the table's order) that have ruins. */
    static List<KnightRuinDesigns.Area> areasOf(KnightKind.Element element)
    {
        return KnightRuinDesigns.AREAS.stream().filter(a -> a.element() == element)
                .filter(a -> KnightRuinDesigns.ALL.stream().anyMatch(d -> d.area() == a)).toList();
    }

    /** A plot's cell: as big as the biggest of these ruins, the gap and a cavern's walls. */
    static int cellFor(List<KnightRuinDesigns.Design> designs)
    {
        return designs.stream().mapToInt(d -> Math.max(d.width(), d.depth())).max().orElse(25) + GAP + 2 * CAVE_WALL;
    }

    /** The most ruins any of the element's areas has (its rows' width in cells). */
    static int columnsOf(KnightKind.Element element)
    {
        return areasOf(element).stream().mapToInt(a -> (int) KnightRuinDesigns.ALL.stream().filter(d -> d.area() == a)
                .count()).max().orElse(0);
    }

    /** Lays the plots out ahead of {@code origin} (a ground block), looking {@code look}: a row an area, its layouts
     * across, every plot in a cell as big as the element's biggest ruin. */
    public static Plan plan(BlockPos origin, Direction look, KnightKind.Element element)
    {
        List<KnightRuinDesigns.Design> mine = KnightRuinDesigns.ALL.stream().filter(d -> d.element() == element).toList();
        List<Plot> plots = new ArrayList<>();
        grid(plots, origin, look, element, cellFor(mine));
        return new Plan(origin, look, origin.getY(), element, plots);
    }

    /** All five together (the user, November 18, 2026: "a showcase of all 5 ... together and their ruins"): each
     * element's rows side by side, ice on the left, {@link #SECTION_GAP} between them, every cell the same size. */
    public static Plan planAll(BlockPos origin, Direction look)
    {
        Direction right = look.getClockWise();
        int cell = cellFor(KnightRuinDesigns.ALL);
        List<KnightKind.Element> elements = Arrays.stream(KnightKind.Element.values()).filter(e -> columnsOf(e) > 0).toList();
        int total = elements.stream().mapToInt(e -> columnsOf(e) * cell).sum() + SECTION_GAP * Math.max(0, elements.size() - 1);
        int left = -total / 2;
        List<Plot> plots = new ArrayList<>();
        for (KnightKind.Element element : elements)
        {
            int width = columnsOf(element) * cell;
            grid(plots, origin.relative(right, left + width / 2), look, element, cell);
            left += width + SECTION_GAP;
        }
        return new Plan(origin, look, origin.getY(), null, plots);
    }

    private static void grid(List<Plot> plots, BlockPos origin, Direction look, KnightKind.Element element, int cell)
    {
        Direction right = look.getClockWise();
        int row = 0;
        for (KnightRuinDesigns.Area area : areasOf(element))
        {
            List<KnightRuinDesigns.Design> designs = KnightRuinDesigns.ALL.stream().filter(d -> d.area() == area).toList();
            for (int col = 0; col < designs.size(); col++)
            {
                int ahead = START + cell / 2 + row * cell;
                int side = Math.round((col - (designs.size() - 1) / 2.0F) * cell);
                plots.add(new Plot(designs.get(col), origin.relative(look, ahead).relative(right, side)));
            }
            row++;
        }
    }

    private static BlockPos origin(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        int y = Mth.clamp(Showcases.groundY(level, position), level.getMinBuildHeight() + UNDER + 8,
                level.getMaxBuildHeight() - 50);
        return new BlockPos(Mth.floor(position.x), y, Mth.floor(position.z));
    }

    private static int run(CommandContext<CommandSourceStack> context, KnightKind.Element element)
    {
        CommandSourceStack source = context.getSource();
        Plan plan = plan(origin(source), Direction.fromYRot(source.getRotation().y), element);
        if (plan.plots().isEmpty())
        {
            source.sendFailure(Component.literal("No " + key(element) + " knight ruins yet"));
            return 0;
        }
        JOBS.add(new Job(source.getLevel(), plan, new ArrayDeque<>(plan.plots()), source));
        source.sendSuccess(() -> Component.literal("Building the " + plan.plots().size() + " " + key(element)
                + " knight ruins ahead of you, a row for each of its areas (one ruin each tick)..."), true);
        return plan.plots().size();
    }

    /** No word: every element's ruins together, each element's rows side by side. */
    private static int runAll(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        Plan plan = planAll(origin(source), Direction.fromYRot(source.getRotation().y));
        if (plan.plots().isEmpty())
        {
            source.sendFailure(Component.literal("No knight ruins yet"));
            return 0;
        }
        JOBS.add(new Job(source.getLevel(), plan, new ArrayDeque<>(plan.plots()), source));
        source.sendSuccess(() -> Component.literal("Building all " + plan.plots().size() + " knight ruins ahead of you, "
                + "the five elements side by side (ice on the left), a row for each area (one ruin each tick, about "
                + (plan.plots().size() / 20 + 1) + " s)..."), true);
        return plan.plots().size();
    }

    /** Builds one element's at once (the headless test uses it). */
    public static Plan buildNow(ServerLevel level, BlockPos origin, Direction look, KnightKind.Element element)
    {
        Plan plan = plan(origin, look, element);
        plan.plots().forEach(plot -> build(level, plan, plot));
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

    // ---------------------------------------------------------------- placing

    static Rotation rotationFor(Direction front)
    {
        return switch (front)
        {
            case WEST -> Rotation.CLOCKWISE_90;
            case NORTH -> Rotation.CLOCKWISE_180;
            case EAST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
    }

    static BlockPos pivot(KnightRuinDesigns.Design ruin)
    {
        return new BlockPos(ruin.width() / 2, 0, ruin.depth() / 2);
    }

    static BlockPos origin(BlockPos ground, KnightRuinDesigns.Design ruin)
    {
        return ground.offset(-ruin.width() / 2, -ruin.ground(), -ruin.depth() / 2);
    }

    /** Where a cell of the template (its own coordinates, layer 0 at its bottom; may lie outside it) ends up. */
    public static BlockPos toWorld(BlockPos local, BlockPos ground, Direction front, KnightRuinDesigns.Design ruin)
    {
        return StructureTemplate.transform(local, Mirror.NONE, rotationFor(front), pivot(ruin)).offset(origin(ground, ruin));
    }

    public static BoundingBox footprint(BlockPos ground, Direction front, KnightRuinDesigns.Design ruin)
    {
        BlockPos a = toWorld(BlockPos.ZERO, ground, front, ruin);
        BlockPos b = toWorld(new BlockPos(ruin.width() - 1, ruin.layers() - 1, ruin.depth() - 1), ground, front, ruin);
        return BoundingBox.fromCorners(a, b);
    }

    /** Its element's two knights (in KnightKind's order). */
    public static List<KnightKind> knightsOf(KnightKind.Element element)
    {
        return Arrays.stream(KnightKind.values()).filter(k -> k.element == element).toList();
    }

    static BlockState block(String id, BlockState fallback)
    {
        ResourceLocation key = ResourceLocation.tryParse(id);
        Block found = key == null ? null : ForgeRegistries.BLOCKS.getValue(key);
        return found == null || found == Blocks.AIR ? fallback : found.defaultBlockState();
    }

    /** Prepares one plot (its ground, the sea or the cave round it, the air over it), places its ruin, its two knights
     * and its sign. */
    public static void build(ServerLevel level, Plan plan, Plot plot)
    {
        KnightRuinDesigns.Design ruin = plot.ruin();
        KnightRuinDesigns.Area area = ruin.area();
        boolean cavern = area.setting() == KnightRuinDesigns.Setting.CAVERN;
        boolean shoreSetting = area.setting() == KnightRuinDesigns.Setting.SHORE;
        BoundingBox box = footprint(plot.ground(), plan.front(), ruin);
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState top = block(area.ground(), Blocks.GRASS_BLOCK.defaultBlockState());
        BlockState under = block(area.under(), Blocks.DIRT.defaultBlockState());
        BlockState stone = cavern ? Blocks.DEEPSLATE.defaultBlockState() : Blocks.STONE.defaultBlockState();
        int shore = KnightRuinDesigns.shoreLine(ruin.depth());
        int y0 = plot.ground().getY();
        int clearTop = y0 + ruin.layers() - ruin.ground() + CLEAR_EXTRA;
        int roof = clearTop + CAVE_ROOF_GAP;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int lx = -MARGIN - CAVE_WALL; lx < ruin.width() + MARGIN + CAVE_WALL; lx++)
        {
            for (int lz = -MARGIN - CAVE_WALL; lz < ruin.depth() + MARGIN; lz++)
            {
                boolean inPlot = lx >= -MARGIN && lx < ruin.width() + MARGIN && lz >= -MARGIN;
                boolean caveWall = cavern && !inPlot;
                if (!inPlot && !caveWall)
                {
                    continue;
                }
                BlockPos c = toWorld(new BlockPos(lx, 0, lz), plot.ground(), plan.front(), ruin);
                boolean sea = shoreSetting && lz < shore;
                for (int y = y0 - UNDER; y <= y0; y++)
                {
                    BlockState state;
                    if (sea)
                    {
                        state = y > y0 - SEA_DEPTH ? Blocks.WATER.defaultBlockState() : Blocks.SAND.defaultBlockState();
                    }
                    else
                    {
                        state = y == y0 ? top : y >= y0 - 2 ? under : stone;
                    }
                    level.setBlock(pos.set(c.getX(), y, c.getZ()), state, FLAGS);
                }
                int yTop = cavern ? roof + 1 : clearTop;
                for (int y = y0 + 1; y <= yTop; y++)
                {
                    pos.set(c.getX(), y, c.getZ());
                    BlockState want = air;
                    if (cavern && (caveWall || y >= roof))
                    {
                        // walls round three sides, the roof over all of it; open at the front
                        want = Math.floorMod(lx * 7 + y * 3 + lz * 5, 6) == 0 ? Blocks.TUFF.defaultBlockState() : stone;
                    }
                    if (level.getBlockState(pos) != want)
                    {
                        level.setBlock(pos, want, FLAGS);
                    }
                }
            }
        }
        level.getEntitiesOfClass(ElementalKnight.class, AABB.of(box).inflate(MARGIN, CLEAR_EXTRA, MARGIN))
                .forEach(Entity::discard);
        Optional<StructureTemplate> template = level.getStructureManager().get(ruin.template());
        if (template.isEmpty())
        {
            LOGGER.error("Knight ruin template {} is missing", ruin.template());
            return;
        }
        BlockPos origin = origin(plot.ground(), ruin);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(rotationFor(plan.front()))
                .setRotationPivot(pivot(ruin)).setIgnoreEntities(true);
        template.get().placeInWorld(level, origin, origin, settings, level.getRandom(), Block.UPDATE_CLIENTS);
        relight(level, box);
        // its two knights, on guard, facing out of the way in (towards you)
        List<KnightKind> kinds = knightsOf(ruin.element());
        for (int i = 0; i < ruin.guards().size() && i < kinds.size(); i++)
        {
            KnightRuinDesigns.Guard guard = ruin.guards().get(i);
            BlockPos cell = toWorld(new BlockPos(guard.x(), ruin.ground() + guard.y(), guard.z()), plot.ground(),
                    plan.front(), ruin);
            ElementalKnight knight = ModEntities.KNIGHTS.get(kinds.get(i)).get().create(level);
            if (knight == null)
            {
                continue;
            }
            float yaw = plan.front().toYRot() + guard.yaw();
            knight.moveTo(cell.getX() + 0.5D, cell.getY(), cell.getZ() + 0.5D, yaw, 0.0F);
            knight.setYBodyRot(yaw);
            knight.setYHeadRot(yaw);
            knight.setShowcase(true);
            level.addFreshEntity(knight);
        }
        // its sign on a post by the way in
        BlockPos door = toWorld(new BlockPos(ruin.width() / 2, ruin.ground() + 1, ruin.depth() - 1), plot.ground(),
                plan.front(), ruin);
        BlockPos post = door.relative(plan.front(), MARGIN + 1).relative(plan.look().getClockWise(), 5);
        post = new BlockPos(post.getX(), y0 + 1, post.getZ());
        level.setBlock(post, Blocks.STONE_BRICK_WALL.defaultBlockState(), Block.UPDATE_ALL);
        level.setBlock(post.above(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
        Showcases.placeWallSign(level, post.above().relative(plan.front()), plan.front(), signLines(ruin));
    }

    static void relight(ServerLevel level, BoundingBox box)
    {
        LevelLightEngine light = level.getChunkSource().getLightEngine();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                for (int y = box.minY(); y <= box.maxY(); y++)
                {
                    if (level.getBlockState(pos.set(x, y, z)).getLightEmission(level, pos) > 0)
                    {
                        light.checkBlock(pos);
                    }
                }
            }
        }
    }

    /** Its area, "No. 3", the layout's name, "Fire knights" (each at most 15 letters). */
    static String[] signLines(KnightRuinDesigns.Design ruin)
    {
        return new String[] {ruin.area().name(), "No. " + ruin.number(), ruin.name(), title(ruin.element()) + " knights"};
    }

    // ---------------------------------------------------------------- the chat

    private static void finish(Job job)
    {
        Plan plan = job.plan();
        CommandSourceStack source = job.source();
        String which = plan.element() == null ? "all" : key(plan.element());
        LOGGER.info("Knight ruin showcase: built {} {} ruins from {} looking {}", plan.plots().size(), which,
                plan.origin().toShortString(), plan.look().getName());
        source.sendSuccess(() -> Component.literal("Built " + (plan.element() == null ? "all " : "the ")
                + plan.plots().size() + " " + (plan.element() == null ? "" : which + " ")
                + "knight ruins: a row for each area (the first nearest), the two knights of its element on guard in "
                + "each. Click one to stand at its way in:"), true);
        List<KnightKind.Element> elements = plan.element() == null ? Arrays.asList(KnightKind.Element.values())
                : List.of(plan.element());
        for (KnightRuinDesigns.Area area : elements.stream().flatMap(e -> areasOf(e).stream()).toList())
        {
            MutableComponent line = Component.literal(area.name() + ":").withStyle(style -> style
                    .withColor(colour(area.element()))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(area.biomes()))));
            for (Plot plot : plan.plots())
            {
                if (plot.ruin().area() == area)
                {
                    line.append(Component.literal(" ")).append(goButton(plan, plot));
                }
            }
            source.sendSuccess(() -> line, false);
        }
        source.sendSuccess(() -> overview(plan), false);
        MutableComponent anims = Component.literal("The knights:");
        for (String name : ANIMATIONS)
        {
            String command = COMMAND + " " + name;
            anims.append(Component.literal(" ")).append(Component.literal("[" + name + "]").withStyle(style -> style
                    .withColor(DrossColors.KNIGHT_SHOWCASE_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))));
        }
        source.sendSuccess(() -> anims, false);
    }

    private static Component goButton(Plan plan, Plot plot)
    {
        KnightRuinDesigns.Design ruin = plot.ruin();
        BlockPos door = toWorld(new BlockPos(ruin.width() / 2, ruin.ground() + 1, ruin.depth() - 1), plot.ground(),
                plan.front(), ruin).relative(plan.front(), MARGIN + 2);
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 10", door.getX() + 0.5D,
                plan.groundY() + 1, door.getZ() + 0.5D, plan.look().toYRot());
        return Component.literal("[" + ruin.number() + " " + ruin.name() + "]").withStyle(style -> style
                .withColor(DrossColors.KNIGHT_SHOWCASE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
    }

    private static Component overview(Plan plan)
    {
        BlockPos middle = plan.plots().get(plan.plots().size() / 2).ground();
        boolean all = plan.element() == null;
        if (all)
        {
            middle = plan.origin().relative(plan.look(), START + 90);
        }
        BlockPos over = middle.relative(plan.look(), all ? -150 : -55);
        String command = String.format(Locale.ROOT, "/tp @s %.1f %d %.1f %.1f 45", over.getX() + 0.5D,
                plan.groundY() + (all ? OVERVIEW_HEIGHT * 2 : OVERVIEW_HEIGHT), over.getZ() + 0.5D, plan.look().toYRot());
        return Component.literal("[overview]").withStyle(style -> style
                        .withColor(DrossColors.KNIGHT_SHOWCASE_BUTTON)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))))
                .append(Component.literal(" high over the rows, looking down (fly, or you'll fall)"));
    }

    /** Every knight on show near plays its idle, its walk, its first move or its second. */
    static int play(CommandContext<CommandSourceStack> context, String animation)
    {
        CommandSourceStack source = context.getSource();
        Vec3 at = source.getPosition();
        int n = 0;
        for (ElementalKnight knight : source.getLevel().getEntitiesOfClass(ElementalKnight.class,
                new AABB(at, at).inflate(PLAY_RANGE), ElementalKnight::isShowcase))
        {
            String name = switch (animation)
            {
                case "move1" -> knight.kind().first.anim();
                case "move2" -> knight.kind().second.anim();
                default -> animation;
            };
            if (knight.showcasePlay(name))
            {
                n++;
            }
        }
        int count = n;
        source.sendSuccess(() -> Component.literal(count + " knights play their " + animation), false);
        return n;
    }
}
