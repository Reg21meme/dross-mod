package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.ShowcaseOnly;
import com.reg21meme.dross.boss.knight.KnightCandidate;
import com.reg21meme.dross.boss.knight.KnightDesigns;
import com.reg21meme.dross.boss.knight.ElementalKnight;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.boss.rimefall.RimefallCandidate;
import com.reg21meme.dross.boss.rimefall.RimefallDesigns;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModEntities;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /dross showcase knights} (test tool, cheats only): the ten real knights ({@link ElementalKnight}, the user's
 * pairs) on show, element by element in front of broken portals, with buttons that make them play their idle, walk and
 * two moves ({@code /dross showcase knights idle|walk|move1|move2}; the user, November 18, 2026: "give me a showcase of
 * the knights themselves, their models"). {@code /dross showcase boss_designs knights}: the design candidates' gallery
 * (only where their models are: {@link ShowcaseOnly}), as below.
 * <p>
 * The candidates' gallery: builds the elemental knights' gallery in front of you. In the
 * user's plan two knights of each of the five elements guard a broken portal in that element's biomes in the Overworld
 * (November 7, 2026). This shows them element by element, each element's knights standing in front of a broken Dross
 * portal on its own biome's ground (round 2, after the user's picks of round 1):
 * <ul>
 *   <li><b>Ice</b>: its pair, the Thawed Soldiers (the Rimefall candidates 9C and 9D);</li>
 *   <li><b>Poison</b> and <b>Earth</b>: the user's pairs ({@link KnightDesigns#POISON}, {@link KnightDesigns#EARTH});</li>
 *   <li><b>Fire</b>: the Cinder Knight's three weapons to pick from and his partner, Flamehand;</li>
 *   <li><b>Thunder</b> (in the Dross's blue): the anchor knight's three designs and his possible partners.</li>
 * </ul>
 * The layout, seen from where the command was run: the five plots in a row across, {@link #START} blocks ahead, each
 * wide enough for its knights ({@link #KNIGHT_SPACING} blocks apart, at least {@link #PLOT_MIN_WIDE}) and
 * {@link #PLOT_DEEP} deep, {@link #PLOT_GAP} apart, on a floor of stone bricks; on each its
 * element's ground (red sand and terracotta, snow and packed ice, podzol and mud, sand and gravel, grass and stone), the
 * broken portal at its back (Dross portal frame blocks, two pillars and the bottom of a 5 x 7 frame, broken, pieces
 * fallen beside it), its knights in a row in front of it facing you, a post and a sign in front of each, and a sign at
 * the plot's front naming the element and its biomes. The ground is cleared {@link #CLEAR_ABOVE} blocks over the
 * tallest. Use a flat world (superflat is ideal): the gallery is about 175 blocks across and 30 deep.
 * <p>
 * The chat has a line per element: a button for the whole plot and one for each knight (its hover gives its name and
 * idea). The gallery's own models within {@link #REMOVE_RADIUS} blocks are removed first (every knight, and the Thawed
 * Soldiers it put there itself, marked with {@link #GALLERY_TAG}), so running it again replaces it.
 * Right-clicking a model shows its name; only {@code /kill} hurts them.
 */
public final class KnightShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The command that builds the real knights' gallery, and the candidates'. */
    public static final String COMMAND = "/dross showcase knights";
    public static final String DESIGNS_COMMAND = "/dross showcase boss_designs knights";
    /** The tag on the Rimefall and Blightwood candidates this gallery puts in, so a second run removes only those. */
    public static final String GALLERY_TAG = "dross_knight_gallery";

    /** The plots start this many blocks ahead of where you stand. */
    static final int START = 5;
    /** Each plot is at least this wide (across; wider for more knights) and this deep (ahead), and this far from the
     * next. */
    static final int PLOT_MIN_WIDE = 22;
    static final int PLOT_DEEP = 22;
    static final int PLOT_GAP = 6;
    /** The knights stand this far into their plot, the signs in front of them this far, the portal this far. */
    private static final int KNIGHT_ROW = 9;
    private static final int SIGN_ROW = 3;
    private static final int PORTAL_ROW = 17;
    /** Neighbouring knights stand this far apart. */
    private static final double KNIGHT_SPACING = 7.0D;
    /** The stone brick floor reaches this far behind you, past the plots' backs and past their outer sides. */
    private static final int BEHIND = 4;
    private static final int MARGIN = 3;
    /** Air is cleared this many blocks above the tallest model. */
    private static final int CLEAR_ABOVE = 4;
    /** A dip in the floor is filled at most this many blocks deep. */
    private static final int MAX_FILL = 4;
    /** Old gallery models within this many blocks (X and Z, at any height) are removed first. */
    private static final int REMOVE_RADIUS = 300;
    /** Where a [go] button puts your eyes (blocks above the floor) and how far you may look up (degrees). */
    private static final double EYE_HEIGHT = 1.62D;
    private static final float MAX_LOOK_UP = 40.0F;
    /** Floor flags: no neighbour updates (no redstone going off), but players still see every change. */
    private static final int FLOOR_FLAGS = Block.UPDATE_CLIENTS;

    /** Something that can stand in the gallery: one of the knights or a Thawed Soldier. */
    record Member(String label, String name, String mood, String blurb, float top, Spawner spawner)
    {
    }

    /** Spawns one member at its feet, facing {@code yaw}; null if it couldn't. */
    interface Spawner
    {
        @Nullable
        Entity spawn(ServerLevel level, Vec3 feet, float yaw);
    }

    /** One element's plot: its name, its sign, the colour of its name in the chat, its ground, its knights. */
    record Station(String element, String[] sign, int color, BlockState[] ground, List<Member> members)
    {
    }

    /** The five elements, each with its two real knights (on show, frozen; tagged so a second run removes them). */
    static List<Station> realStations()
    {
        List<Station> out = new ArrayList<>();
        for (Station station : stations())
        {
            List<Member> members = new ArrayList<>();
            for (KnightKind kind : KnightKind.values())
            {
                if (kind.element.name().equalsIgnoreCase(station.element()))
                {
                    members.add(new Member(label(kind), name(kind), (int) kind.health + " health", kind.first.anim() + " and "
                            + kind.second.anim().replace('_', ' '), REAL_TOP, (level, feet, yaw) -> spawnReal(level, feet, yaw, kind)));
                }
            }
            out.add(new Station(station.element(), station.sign(), station.color(), station.ground(), members));
        }
        return out;
    }

    /** The real knights' models stand about this tall with their weapons (blocks). */
    private static final float REAL_TOP = 5.0F;

    /** The design each real knight came from (the user's picks). */
    static String label(KnightKind kind)
    {
        return switch (kind)
        {
            case THAWED_AXE -> "9C";
            case THAWED_SPEAR -> "9D";
            case BLIGHT_WARDEN -> "P1";
            case SPOREBOUND -> "P3";
            case CINDER -> "F1B";
            case FLAMEHAND -> "F5";
            case WRECK -> "T3A";
            case STORMBLADE -> "T6";
            case GEODE -> "E3";
            case ROCKFALL -> "E4";
        };
    }

    /** Its name (at most 15 letters, for its sign). */
    static String name(KnightKind kind)
    {
        return switch (kind)
        {
            case THAWED_AXE -> "Thawed Axeman";
            case THAWED_SPEAR -> "Thawed Spearman";
            case BLIGHT_WARDEN -> "Blight Warden";
            case SPOREBOUND -> "Sporebound";
            case CINDER -> "Cinder Knight";
            case FLAMEHAND -> "Flamehand";
            case WRECK -> "Wreck Knight";
            case STORMBLADE -> "Stormblade";
            case GEODE -> "Geode Knight";
            case ROCKFALL -> "Rockfall Knight";
        };
    }

    @Nullable
    private static Entity spawnReal(ServerLevel level, Vec3 feet, float yaw, KnightKind kind)
    {
        ElementalKnight knight = ModEntities.KNIGHTS.get(kind).get().create(level);
        if (knight == null)
        {
            return null;
        }
        knight.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        knight.setYBodyRot(yaw);
        knight.setYHeadRot(yaw);
        knight.setShowcase(true);
        knight.addTag(GALLERY_TAG);
        level.addFreshEntity(knight);
        return knight;
    }

    /** The five elements in the gallery's order, each with its knights. */
    static List<Station> stations()
    {
        List<Station> out = new ArrayList<>();
        List<Member> ice = new ArrayList<>();
        for (String label : new String[] {"9C", "9D"})
        {
            RimefallDesigns.Design design = rimefall(label);
            if (design != null)
            {
                ice.add(new Member(design.label(), design.name(), design.mood(),
                        "The Thawed Soldiers, ice's pair (the Rimefall candidates 9C and 9D).", design.top(),
                        (level, feet, yaw) -> tagged(RimefallCandidate.spawnShowcase(level, feet, yaw, design))));
            }
        }
        out.add(new Station("Ice", new String[] {"Ice knights", "snowy plains,", "taigas, frozen"},
                DrossColors.KNIGHT_ICE, new BlockState[] {Blocks.SNOW_BLOCK.defaultBlockState(),
                Blocks.PACKED_ICE.defaultBlockState(), Blocks.SNOW_BLOCK.defaultBlockState()}, ice));
        out.add(new Station("Poison", new String[] {"Poison knights", "swamps, jungles", "dark forests"},
                DrossColors.KNIGHT_POISON, new BlockState[] {Blocks.PODZOL.defaultBlockState(),
                Blocks.MUD.defaultBlockState(), Blocks.MYCELIUM.defaultBlockState()}, knights(KnightDesigns.POISON)));
        out.add(new Station("Fire", new String[] {"Fire knights", "deserts,", "savannas, mesas"},
                DrossColors.KNIGHT_FIRE, new BlockState[] {Blocks.RED_SAND.defaultBlockState(),
                Blocks.TERRACOTTA.defaultBlockState(), Blocks.ORANGE_TERRACOTTA.defaultBlockState()},
                knights(KnightDesigns.FIRE)));
        out.add(new Station("Thunder", new String[] {"Thunder knights", "oceans, rivers", "beaches, shores"},
                DrossColors.KNIGHT_THUNDER, new BlockState[] {Blocks.SAND.defaultBlockState(),
                Blocks.GRAVEL.defaultBlockState(), Blocks.STONE.defaultBlockState()}, knights(KnightDesigns.THUNDER)));
        out.add(new Station("Earth", new String[] {"Earth knights", "plains, meadows", "hills, peaks"},
                DrossColors.KNIGHT_EARTH, new BlockState[] {Blocks.GRASS_BLOCK.defaultBlockState(),
                Blocks.STONE.defaultBlockState(), Blocks.COBBLESTONE.defaultBlockState()}, knights(KnightDesigns.EARTH)));
        return out;
    }

    private static List<Member> knights(List<KnightDesigns.Design> designs)
    {
        List<Member> out = new ArrayList<>();
        for (KnightDesigns.Design design : designs)
        {
            out.add(new Member(design.label(), design.name(), design.mood(), design.blurb(), design.top(),
                    (level, feet, yaw) -> KnightCandidate.spawnShowcase(level, feet, yaw, design)));
        }
        return out;
    }

    @Nullable
    private static RimefallDesigns.Design rimefall(String label)
    {
        return RimefallDesigns.ALL.stream().filter(d -> d.label().equals(label)).findFirst().orElse(null);
    }

    @Nullable
    private static Entity tagged(@Nullable Entity entity)
    {
        if (entity != null)
        {
            entity.addTag(GALLERY_TAG);
        }
        return entity;
    }

    /** How wide a plot is for this many knights (blocks across). */
    static int plotWide(int knights)
    {
        return Math.max(PLOT_MIN_WIDE, (int) Math.ceil(knights * KNIGHT_SPACING) + 8);
    }

    /** Every plot's middle, in blocks to your right (negative: to your left), the row centred on you. */
    static double[] plotCentres(List<Station> stations)
    {
        double total = PLOT_GAP * (stations.size() - 1);
        for (Station station : stations)
        {
            total += plotWide(station.members().size());
        }
        double[] out = new double[stations.size()];
        double left = -total / 2.0D;
        for (int i = 0; i < stations.size(); i++)
        {
            int wide = plotWide(stations.get(i).members().size());
            out[i] = left + wide / 2.0D;
            left += wide + PLOT_GAP;
        }
        return out;
    }

    /** How many element plots the gallery has (for its test). */
    public static int stationCount()
    {
        return stations().size();
    }

    /** The middle of plot {@code index}, in blocks to the command's right (for its test). */
    public static double plotMiddle(int index)
    {
        return plotCentres(stations())[index];
    }

    /** How far ahead of the command each plot's broken portal stands (for its test). */
    public static int portalAhead()
    {
        return START + PORTAL_ROW;
    }

    /** Where the k-th of n knights stands across its plot, from the plot's middle (blocks to your right). */
    static double knightOffset(int k, int n)
    {
        return (k - (n - 1) / 2.0D) * KNIGHT_SPACING;
    }

    /** Runs {@code /dross showcase knights}: the ten real knights. */
    public static int buildKnights(CommandContext<CommandSourceStack> context)
    {
        return build(context, true);
    }

    /** Runs {@code /dross showcase boss_designs knights}: the design candidates. */
    public static int buildGallery(CommandContext<CommandSourceStack> context)
    {
        return build(context, false);
    }

    private static int build(CommandContext<CommandSourceStack> context, boolean real)
    {
        CommandSourceStack source = context.getSource();
        if (!real && !ShowcaseOnly.knightCandidates())
        {
            source.sendFailure(Component.literal("The knights' gallery models aren't in this build of the mod "
                    + "(they're only in the project, for the dev runs)."));
            return 0;
        }
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction right = look.getClockWise();
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));
        List<Station> stations = real ? realStations() : stations();
        float tallest = 0.0F;
        for (Station station : stations)
        {
            for (Member member : station.members())
            {
                tallest = Math.max(tallest, member.top());
            }
        }
        int removed = removeOld(level, position);
        double[] centres = plotCentres(stations);
        int half = (int) Math.ceil(Math.max(Math.abs(centres[0]) + plotWide(stations.get(0).members().size()) / 2.0D,
                Math.abs(centres[centres.length - 1]) + plotWide(stations.get(centres.length - 1).members().size())
                        / 2.0D)) + MARGIN;
        buildFloor(level, origin, look, right, half, START + PLOT_DEEP + MARGIN, Mth.ceil(tallest) + CLEAR_ABOVE);

        BlockState post = Blocks.STONE_BRICK_WALL.defaultBlockState();
        Direction towardsYou = look.getOpposite();
        int spawned = 0;
        int total = 0;
        List<Component> lines = new ArrayList<>();
        for (int i = 0; i < stations.size(); i++)
        {
            Station station = stations.get(i);
            double centre = centres[i];
            buildPlot(level, origin, look, right, centre, station);
            brokenPortal(level, origin, look, right, centre);
            BlockPos signPost = at(origin, look, right, START, centre).above();
            level.setBlock(signPost, Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            level.setBlock(signPost.above(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
            Showcases.placeWallSign(level, signPost.above().relative(towardsYou), towardsYou, station.sign());
            MutableComponent line = Component.literal(station.element()).withStyle(style -> style
                    .withColor(station.color()).withBold(true));
            line.append(Component.literal(": ").withStyle(style -> style.withBold(false)));
            line.append(goButton("[go]", "The " + station.element().toLowerCase(Locale.ROOT) + " knights' plot",
                    standCommand(origin, look, right, START - 4, centre, 4.0F)));
            int n = station.members().size();
            for (int k = 0; k < n; k++)
            {
                Member member = station.members().get(k);
                total++;
                double across = centre + knightOffset(k, n);
                Vec3 feet = offset(origin, look, right, START + KNIGHT_ROW, across);
                Entity entity = member.spawner().spawn(level, feet, towardsYou.toYRot());
                if (entity == null)
                {
                    LOGGER.warn("Knights' gallery: couldn't spawn No. {} ({})", member.label(), member.name());
                    line.append(Component.literal(" " + member.label() + "(failed)"));
                    continue;
                }
                spawned++;
                BlockPos postPos = at(origin, look, right, START + SIGN_ROW, across).above();
                level.setBlock(postPos, post, Block.UPDATE_ALL);
                level.setBlock(postPos.above(), Blocks.CHISELED_STONE_BRICKS.defaultBlockState(), Block.UPDATE_ALL);
                Showcases.placeWallSign(level, postPos.above().relative(towardsYou), towardsYou, "No. " + member.label(),
                        member.name(), member.mood());
                double distance = KNIGHT_ROW - 1.0D;
                float pitch = (float) -Math.toDegrees(Math.atan2(member.top() * 0.5D - EYE_HEIGHT, distance));
                line.append(Component.literal(" "));
                line.append(goButton("[" + member.label() + "]", "No. " + member.label() + " " + member.name() + " ("
                        + member.mood() + ", " + String.format(Locale.ROOT, "%.1f", member.top()) + " blocks tall)\n"
                        + member.blurb(), standCommand(origin, look, right, START + 1, across,
                        Mth.clamp(pitch, -MAX_LOOK_UP, 10.0F))));
            }
            lines.add(line);
        }
        LOGGER.info("Knights' gallery: spawned {} of {} for {} in {} from {} looking {}; removed {} old", spawned, total,
                source.getTextName(), level.dimension().location(), origin.toShortString(), look.getName(), removed);
        String removedNote = removed == 0 ? "" : " (removed " + removed + " old ones)";
        int count = spawned;
        int all = total;
        source.sendSuccess(() -> Component.literal((real ? "The ten knights: " : "Built the knights' gallery: ")
                + (count == all ? String.valueOf(count) : count + " of " + all) + " knights, element by element in front "
                + "of broken portals ahead of you" + removedNote + (real ? "." : ". Pick a pair for each element.")
                + " Click a button to stand by one:"), true);
        for (Component line : lines)
        {
            source.sendSuccess(() -> line, false);
        }
        MutableComponent overview = goButton("[overview]", "High above the middle of the gallery",
                String.format(Locale.ROOT, "/tp @s %.2f %.2f %.2f %.1f %.1f",
                        offset(origin, look, right, -14.0D, 0.0D).x, origin.getY() + 22.0D,
                        offset(origin, look, right, -14.0D, 0.0D).z, look.toYRot(), 30.0F));
        if (real)
        {
            MutableComponent anims = Component.literal("Make them all play: ");
            for (String anim : new String[] {"idle", "walk", "move1", "move2"})
            {
                anims.append(goButton("[" + anim + "]", "Every knight on show near plays its " + anim
                        + " (move1, move2: its first and second attacks)", COMMAND + " " + anim)).append(" ");
            }
            source.sendSuccess(() -> anims.append(overview), false);
        }
        else
        {
            source.sendSuccess(() -> Component.literal("Right-click a knight to read its idea; ").append(overview), false);
        }
        return spawned;
    }

    /** A button that runs a command, its hover the text and the command. */
    private static MutableComponent goButton(String text, String hover, String command)
    {
        return Component.literal(text).withStyle(style -> style
                .withColor(DrossColors.KNIGHT_SHOWCASE_BUTTON).withBold(false)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover))));
    }

    /** A /tp that stands you {@code ahead} blocks ahead and {@code across} to the right, looking ahead. */
    private static String standCommand(BlockPos origin, Direction look, Direction right, double ahead, double across,
                                       float pitch)
    {
        Vec3 stand = offset(origin, look, right, ahead, across);
        return String.format(Locale.ROOT, "/tp @s %.2f %.2f %.2f %.1f %.1f", stand.x, stand.y, stand.z, look.toYRot(),
                pitch);
    }

    /** A point in the gallery's frame as a world position on the floor (feet height). */
    static Vec3 offset(BlockPos origin, Direction look, Direction right, double ahead, double across)
    {
        return new Vec3(origin.getX() + 0.5D + look.getStepX() * ahead + right.getStepX() * across,
                origin.getY() + 1.0D,
                origin.getZ() + 0.5D + look.getStepZ() * ahead + right.getStepZ() * across);
    }

    /** The ground block at a point of the gallery's frame. */
    static BlockPos at(BlockPos origin, Direction look, Direction right, double ahead, double across)
    {
        return origin.relative(look, (int) Math.round(ahead)).relative(right, (int) Math.round(across));
    }

    /** The whole gallery's floor: cleared {@code clear} blocks up, stone bricks, dips under it filled with dirt. */
    private static void buildFloor(ServerLevel level, BlockPos origin, Direction look, Direction right, int half,
                                   int length, int clear)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState bricks = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int ahead = -BEHIND; ahead <= length; ahead++)
        {
            for (int side = -half; side <= half; side++)
            {
                BlockPos ground = origin.relative(look, ahead).relative(right, side);
                for (int dy = 1; dy <= clear; dy++)
                {
                    if (!level.getBlockState(pos.setWithOffset(ground, 0, dy, 0)).isAir())
                    {
                        level.setBlock(pos, air, FLOOR_FLAGS);
                    }
                }
                level.setBlock(ground, bricks, FLOOR_FLAGS);
                for (int dy = 1; dy <= MAX_FILL; dy++)
                {
                    if (Showcases.isSolidGround(level, pos.setWithOffset(ground, 0, -dy, 0)))
                    {
                        break;
                    }
                    level.setBlock(pos, dirt, FLOOR_FLAGS);
                }
            }
        }
    }

    /** One plot's ground: its element's first block, the others in patches (the same every time for a spot). */
    private static void buildPlot(ServerLevel level, BlockPos origin, Direction look, Direction right, double centre,
                                  Station station)
    {
        BlockState[] ground = station.ground();
        int half = plotWide(station.members().size()) / 2;
        for (int ahead = START; ahead < START + PLOT_DEEP; ahead++)
        {
            for (int side = -half; side <= half; side++)
            {
                BlockPos pos = at(origin, look, right, ahead, centre + side);
                long cell = Mth.getSeed(pos.getX() >> 2, 17, pos.getZ() >> 2);
                long fine = Mth.getSeed(pos.getX(), 23, pos.getZ());
                int pick = 0;
                if ((cell & 7) < 2 && (fine & 3) != 0)
                {
                    pick = 1;
                }
                else if ((fine & 15) == 0)
                {
                    pick = 2;
                }
                level.setBlock(pos, ground[pick], FLOOR_FLAGS);
            }
        }
    }

    /**
     * A broken Dross portal at the back of a plot: the frame (5 wide, 7 tall, across the gallery so you see its
     * opening) with its bottom row whole, its left pillar standing, its right pillar broken half way, a stub of its top,
     * and three of its blocks fallen on the ground beside it. (Broken, so it can never be lit.)
     */
    private static void brokenPortal(ServerLevel level, BlockPos origin, Direction look, Direction right, double centre)
    {
        BlockState frame = ModBlocks.DROSS_PORTAL_FRAME.get().defaultBlockState();
        BlockPos base = at(origin, look, right, START + PORTAL_ROW, centre).above();
        for (int x = -2; x <= 2; x++)
        {
            level.setBlock(base.relative(right, x), frame, FLOOR_FLAGS);
        }
        for (int y = 1; y <= 6; y++)
        {
            level.setBlock(base.relative(right, -2).above(y), frame, FLOOR_FLAGS);
        }
        for (int y = 1; y <= 3; y++)
        {
            level.setBlock(base.relative(right, 2).above(y), frame, FLOOR_FLAGS);
        }
        level.setBlock(base.relative(right, -1).above(6), frame, FLOOR_FLAGS);
        level.setBlock(base.relative(right, 3).relative(look.getOpposite(), 1), frame, FLOOR_FLAGS);
        level.setBlock(base.relative(right, 4).relative(look, 1), frame, FLOOR_FLAGS);
        level.setBlock(base.relative(right, -4).relative(look.getOpposite(), 2), frame, FLOOR_FLAGS);
    }

    /** The gallery's models loaded within {@link #REMOVE_RADIUS} blocks: every knight, and the others it put there. */
    static List<Entity> nearby(ServerLevel level, Vec3 position)
    {
        AABB area = Showcases.columnsAround(level, position, REMOVE_RADIUS);
        List<Entity> out = new ArrayList<>(level.getEntities(ModEntities.KNIGHT_CANDIDATE.get(), area, k -> true));
        out.addAll(level.getEntities(ModEntities.RIMEFALL_CANDIDATE.get(), area, c -> c.getTags().contains(GALLERY_TAG)));
        // (Round 1 put Sir Mildew in too, a Blightwood candidate: one left from then goes with the rest.)
        out.addAll(level.getEntities(ModEntities.BLIGHT_CANDIDATE.get(), area, c -> c.getTags().contains(GALLERY_TAG)));
        // the real knights' gallery's knights
        out.addAll(level.getEntitiesOfClass(ElementalKnight.class, area, c -> c.getTags().contains(GALLERY_TAG)));
        return out;
    }

    private static int removeOld(ServerLevel level, Vec3 position)
    {
        List<Entity> old = nearby(level, position);
        old.forEach(Entity::discard);
        return old.size();
    }

    private KnightShowcase() {}
}
