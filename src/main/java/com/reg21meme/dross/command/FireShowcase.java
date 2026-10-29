package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.emberdactyl.EmberVolcano;
import com.reg21meme.dross.boss.emberdactyl.EmberVolcanoTuning;
import com.reg21meme.dross.boss.fire.FireAnims;
import com.reg21meme.dross.boss.fire.FireCandidate;
import com.reg21meme.dross.boss.fire.FireDesigns;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The fire candidates' galleries (test tools, cheats only): frozen models from the boss area's
 * {@code boss.fire.FireDesigns}, labelled "1A", "E1B", "M2J"..., on blackstone. Use a flat world (superflat is ideal).
 * <ul>
 *   <li>{@code /dross showcase boss_designs cinder_wastes}: the fire servant candidates (fifteen possible bosses for the
 *       Fire Necromancer's two open servant places, each its original look and three variations), about 230 blocks
 *       wide and 80 deep;</li>
 *   <li>{@code /dross showcase boss_designs emberdactyl}: the Emberdactyl (the user's volcanic pterosaur, round 2): at
 *       35, 45 and 55 blocks across its wings, each in three looks, its young and its eggs; and behind them its volcano
 *       ({@code boss.emberdactyl.EmberVolcano}), built a piece at a time, with an egg in its nest and an adult hovering
 *       over its crater once it stands. About 200 blocks wide and 320 deep, the volcano 70 tall;</li>
 *   <li>{@code /dross showcase cinder_mobs}: the Cinder Wastes' regular mobs, the Pallbearer and the Cinder Hermit in its
 *       eight shells, in one row.</li>
 * </ul>
 * The layout, seen from where the command was run: a walkway of polished blackstone bricks runs across in front of you,
 * {@link #WALK_FROM} to {@link #WALK_TO} blocks ahead; past it each column's first model stands in a row facing you,
 * the rest of the column behind it (also facing you), with lanes between the columns to walk back along; in front of
 * each model a post of polished basalt with a sign: its label ("No. 1A"), name and look; the ground is blackstone,
 * cleared over each column up to {@link #CLEAR_ABOVE} blocks above its tallest model.
 * <p>
 * The chat lists the designs, each with a button per look that stands you in front of that model, and an [overview]
 * button high above the gallery. Old candidates within {@link #REMOVE_RADIUS} blocks (X and Z, at any height) are
 * removed first, so running it again replaces the gallery. Right-clicking a candidate shows its label, name and look;
 * it can't be hurt except by {@code /kill}.
 * <p>
 * It uses the command's position, rotation and dimension, so it also works from the server console and command blocks.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class FireShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The walkway across in front of the first row: from this many blocks ahead to this many. */
    static final int WALK_FROM = 2;
    static final int WALK_TO = 6;
    /** Blocks between the walkway's far edge and the front of the nearest model. */
    private static final double FRONT_GAP = 3.0D;
    /** Blocks between one model's back and the front of the one behind it (room to stand and read its sign). */
    private static final double ROW_GAP = 5.0D;
    /** Blocks between neighbouring columns (a lane down the middle of each gap). */
    private static final double COL_GAP = 5.0D;
    /** Half the lanes' width (they're 3 wide). */
    private static final int LANE_HALF = 1;
    /** The floor reaches this far past the outermost columns and past the last row, and this far behind you. */
    private static final int SIDE_MARGIN = 4;
    private static final int END_MARGIN = 4;
    private static final int BEHIND = 2;
    /** Air is cleared this many blocks above the tallest model of each column (and the walkway this high). */
    private static final int CLEAR_ABOVE = 3;
    private static final int WALK_CLEAR = 5;
    /** A dip in the floor is filled at most this many blocks deep. */
    private static final int MAX_FILL = 4;
    /** A sign's post stands this many blocks in front of its model's front. */
    private static final double SIGN_GAP = 1.5D;
    /** Old candidates within this many blocks (X and Z, at any height) are removed first. */
    private static final int REMOVE_RADIUS = 320;
    /** Where a [go] button stands you: this many blocks in front of the model's front. */
    private static final double STAND_GAP = 3.2D;
    private static final double EYE_HEIGHT = 1.62D;
    private static final float MAX_LOOK_UP = 45.0F;
    /** The [overview] button: this far back from the walkway and this high above the floor, looking down this much. */
    private static final double OVERVIEW_BACK = 18.0D;
    private static final double OVERVIEW_UP = 34.0D;
    private static final float OVERVIEW_PITCH = 32.0F;
    /** Floor flags: no neighbour updates (no redstone going off), but players still see every change. */
    private static final int FLOOR_FLAGS = Block.UPDATE_CLIENTS;
    /** The volcano's whole plan (its ground round it too) starts this many blocks past the gallery's floor. */
    private static final int VOLCANO_GAP = 2;
    /** On show its nest holds this egg, and this adult hovers over its crater (labels in FireDesigns: the egg for the
     *  45-block one, with more magma; the 45-block one in its volcanic look), this many blocks over the egg's top. */
    static final String NEST_EGG = "E3B";
    static final String HOVERING_ADULT = "E1A";
    private static final double HOVER_OVER_EGG = 2.0D;
    /** Blocks round you the animation controls reach (the gallery and its volcano's hovering adult). */
    static final double PLAY_RADIUS = 220.0D;
    /** The [volcano] button: this far in front of the volcano's plan (over the gallery) and this high, looking down this
     *  much: the whole cone in view. */
    private static final double VOLCANO_VIEW_BACK = 10.0D;
    private static final double VOLCANO_VIEW_UP = 30.0D;
    private static final float VOLCANO_VIEW_PITCH = 8.0F;
    private static final float LOOKOUT_PITCH = 25.0F;

    /** The commands that build the galleries. */
    public static final String COMMAND = "/dross showcase boss_designs cinder_wastes";
    public static final String EMBERDACTYL_COMMAND = "/dross showcase boss_designs emberdactyl";
    public static final String CINDER_MOBS_COMMAND = "/dross showcase cinder_mobs";

    /** The volcanoes being built, a piece each tick (the first one first). */
    private static final List<VolcanoJob> JOBS = new ArrayList<>();
    /** The last volcano planned (the tests look at it). */
    private static EmberVolcano.Plan lastVolcano;

    /** One gallery: which designs, and what the chat says about them. */
    enum Gallery
    {
        SERVANTS(FireDesigns.SERVANTS, "fire servant gallery",
                "the fifteen originals in a row ahead of you (No. 1 on the left), each one's variations behind it", COMMAND),
        EMBERDACTYL(FireDesigns.EMBERDACTYL, "Emberdactyl gallery",
                "the Emberdactyl (33 blocks across its wings) ahead of you on the left, its young beside it, then its "
                        + "egg in its four looks, one behind the other", EMBERDACTYL_COMMAND),
        CINDER_MOBS(FireDesigns.CINDER_MOBS, "Cinder Wastes' mobs",
                "the Pallbearer and the Cinder Hermit in its eight shells, in a row ahead of you", CINDER_MOBS_COMMAND);

        final List<FireDesigns.Design> designs;
        final String title;
        final String where;
        final String command;

        Gallery(List<FireDesigns.Design> designs, String title, String where, String command)
        {
            this.designs = designs;
            this.title = title;
            this.where = where;
            this.command = command;
        }

        /** The animations of its designs that move, each once, in the order their mobs list them. */
        List<String> animations()
        {
            List<String> out = new ArrayList<>();
            for (FireDesigns.Design design : this.designs)
            {
                FireAnims.Mob mob = design.animated() ? FireAnims.get(design.mob()) : null;
                if (mob == null)
                {
                    continue;
                }
                for (FireAnims.Anim anim : mob.anims())
                {
                    if (!out.contains(anim.name()))
                    {
                        out.add(anim.name());
                    }
                }
            }
            return out;
        }
    }

    /**
     * Where one candidate goes, in the gallery's own frame: {@code along} blocks ahead of the command's spot,
     * {@code across} blocks to its right (negative: to its left). It faces back towards the command's spot.
     */
    record Slot(FireDesigns.Design design, double along, double across)
    {
    }

    /** One column: its middle (across), how far it reaches either side, its depth and its tallest model. */
    record Column(int column, double across, double leftWide, double rightWide, double depth, float tallest)
    {
    }

    /** The whole gallery's layout: every slot, every column, and its extent. */
    record Layout(List<Slot> slots, List<Column> columns, double leftEdge, double rightEdge, double depth)
    {
    }

    /** A volcano going up behind an Emberdactyl gallery. */
    private record VolcanoJob(EmberVolcano.Build build, CommandSourceStack source, BlockPos origin, Direction look,
                              Direction right)
    {
    }

    /**
     * Lays the candidates out by their columns (in the gallery's own frame, so it doesn't depend on where the command
     * is run). Each candidate faces the command's spot, so its own left is towards the command's right: across the
     * gallery it reaches {@code left} to the right and {@code right} to the left; along it, {@code front} towards you
     * and {@code back} away.
     */
    static Layout layout(List<FireDesigns.Design> designs)
    {
        Map<Integer, List<FireDesigns.Design>> byColumn = new LinkedHashMap<>();
        for (FireDesigns.Design d : designs)
        {
            byColumn.computeIfAbsent(d.column(), c -> new ArrayList<>()).add(d);
        }
        // Each column's width first, to centre the row of columns on the command's spot.
        double total = 0.0D;
        List<double[]> widths = new ArrayList<>();
        for (List<FireDesigns.Design> column : byColumn.values())
        {
            double toRight = 0.0D;
            double toLeft = 0.0D;
            for (FireDesigns.Design d : column)
            {
                toRight = Math.max(toRight, d.left());
                toLeft = Math.max(toLeft, d.right());
            }
            widths.add(new double[] {toLeft, toRight});
            total += toLeft + toRight;
        }
        total += COL_GAP * (byColumn.size() - 1);
        double cursor = -total / 2.0D;
        List<Slot> slots = new ArrayList<>();
        List<Column> columns = new ArrayList<>();
        double deepest = 0.0D;
        int i = 0;
        for (Map.Entry<Integer, List<FireDesigns.Design>> entry : byColumn.entrySet())
        {
            double[] w = widths.get(i++);
            double across = cursor + w[0];
            double frontLine = WALK_TO + 1 + FRONT_GAP;
            float tallest = 0.0F;
            for (FireDesigns.Design d : entry.getValue())
            {
                double along = frontLine + d.front();
                slots.add(new Slot(d, along, across));
                frontLine = along + d.back() + ROW_GAP;
                tallest = Math.max(tallest, d.top());
            }
            double depth = frontLine - ROW_GAP;
            deepest = Math.max(deepest, depth);
            columns.add(new Column(entry.getKey(), across, w[0], w[1], depth, tallest));
            cursor = across + w[1] + COL_GAP;
        }
        return new Layout(slots, columns, -total / 2.0D, total / 2.0D, deepest);
    }

    /** Runs {@code /dross showcase boss_designs cinder_wastes}. */
    public static int buildGallery(CommandContext<CommandSourceStack> context)
    {
        return build(context.getSource(), Gallery.SERVANTS);
    }

    /** Runs {@code /dross showcase boss_designs emberdactyl}. */
    public static int buildEmberdactyl(CommandContext<CommandSourceStack> context)
    {
        return build(context.getSource(), Gallery.EMBERDACTYL);
    }

    /** Runs {@code /dross showcase cinder_mobs}. */
    public static int buildCinderMobs(CommandContext<CommandSourceStack> context)
    {
        return build(context.getSource(), Gallery.CINDER_MOBS);
    }

    /** The Emberdactyl gallery's animations (its adult's and its young's): one literal each under its command. */
    public static List<String> emberdactylAnimations()
    {
        return Gallery.EMBERDACTYL.animations();
    }

    /** The Cinder Wastes' mobs' animations (the Pallbearer's and the Cinder Hermit's). */
    public static List<String> cinderMobAnimations()
    {
        return Gallery.CINDER_MOBS.animations();
    }

    /** Runs {@code /dross showcase boss_designs emberdactyl <animation>}. */
    public static int playEmberdactyl(CommandContext<CommandSourceStack> context, String animation)
    {
        return play(context.getSource(), Gallery.EMBERDACTYL, animation);
    }

    /** Runs {@code /dross showcase cinder_mobs <animation>}. */
    public static int playCinderMobs(CommandContext<CommandSourceStack> context, String animation)
    {
        return play(context.getSource(), Gallery.CINDER_MOBS, animation);
    }

    /**
     * Plays one animation on every candidate of that gallery near you that has it (within {@link #PLAY_RADIUS}
     * blocks: the hovering adult over the volcano too). A loop keeps going; a move holds its last pose.
     */
    static int play(CommandSourceStack source, Gallery gallery, String animation)
    {
        ServerLevel level = source.getLevel();
        Vec3 at = source.getPosition();
        String set = gallery.designs.get(0).set();
        int played = 0;
        for (FireCandidate candidate : level.getEntitiesOfClass(FireCandidate.class, new AABB(at, at).inflate(PLAY_RADIUS),
                c -> c.getDesign().set().equals(set)))
        {
            if (candidate.playAnimation(animation))
            {
                played++;
            }
        }
        int n = played;
        if (n == 0)
        {
            source.sendFailure(Component.literal("Nothing in the " + gallery.title + " within " + Math.round(PLAY_RADIUS)
                    + " blocks has the animation \"" + animation + "\" (run " + gallery.command + " first)."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Playing " + animation + " on " + n + " model" + (n == 1 ? "" : "s")
                + "."), false);
        return n;
    }

    /** "Moves: [hover] [fly] ...": a button per animation of the gallery's designs that move (none: null). */
    static Component controls(Gallery gallery)
    {
        List<String> names = gallery.animations();
        if (names.isEmpty())
        {
            return null;
        }
        MutableComponent line = Component.literal("Their moves (right-click one for its next): ");
        for (String name : names)
        {
            String command = gallery.command + " " + name;
            line.append(Component.literal("[" + name + "]").withStyle(style -> style
                    .withColor(DrossColors.FIRE_SHOWCASE_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))))
                    .append(Component.literal(" "));
        }
        return line;
    }

    private static int build(CommandSourceStack source, Gallery gallery)
    {
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction right = look.getClockWise();
        Direction facing = look.getOpposite();
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));
        Layout layout = layout(gallery.designs);

        if (gallery == Gallery.EMBERDACTYL)
        {
            // a volcano still going up from a run before is left as it stands
            JOBS.forEach(job -> job.build().abandon());
            JOBS.clear();
        }
        int removed = removeOld(level, position);
        buildFloor(level, origin, look, right, layout);

        BlockState post = Blocks.POLISHED_BASALT.defaultBlockState();
        List<FireDesigns.Design> failed = new ArrayList<>();
        for (Slot slot : layout.slots())
        {
            FireDesigns.Design design = slot.design();
            Vec3 feet = offset(origin, look, right, slot.along(), slot.across());
            // The post and its sign in front of the model, the sign facing you.
            BlockPos postPos = blockAt(origin, look, right, slot.along() - design.front() - SIGN_GAP, slot.across()).above();
            level.setBlock(postPos, post, Block.UPDATE_ALL);
            if (!Showcases.placeWallSign(level, postPos.relative(facing), facing, "No. " + design.label(), design.name(),
                    design.mood()))
            {
                LOGGER.warn("Fire showcase: couldn't put the sign for No. {} at {}", design.label(),
                        postPos.relative(facing).toShortString());
            }
            if (FireCandidate.spawnShowcase(level, feet, facing.toYRot(), design) == null)
            {
                failed.add(design);
                LOGGER.warn("Fire showcase: couldn't spawn No. {} ({})", design.label(), design.name());
            }
        }

        int spawned = layout.slots().size() - failed.size();
        LOGGER.info("Fire showcase ({}): spawned {} of {} for {} in {}, a gallery {} blocks wide and {} deep from {} looking "
                        + "{}; removed {} old", gallery.title, spawned, layout.slots().size(), source.getTextName(),
                level.dimension().location(), Math.round(layout.rightEdge() - layout.leftEdge()),
                Math.round(layout.depth()), origin.toShortString(), look.getName(), removed);
        String removedNote = removed == 0 ? "no old candidates nearby"
                : "removed " + removed + " old candidate" + (removed == 1 ? "" : "s");
        String summary = "Built the " + gallery.title + ": " + (failed.isEmpty() ? String.valueOf(spawned)
                : spawned + " of " + layout.slots().size()) + " models, " + gallery.where + " (" + removedNote + "). "
                + "Click a letter to stand by that model:";
        source.sendSuccess(() -> Component.literal(summary), true);
        Map<String, List<Slot>> byDesign = new LinkedHashMap<>();
        for (Slot slot : layout.slots())
        {
            byDesign.computeIfAbsent(designLabel(slot.design()), b -> new ArrayList<>()).add(slot);
        }
        for (List<Slot> design : byDesign.values())
        {
            MutableComponent line = Component.literal(String.format(Locale.ROOT, "No. %s %s: ",
                    designLabel(design.get(0).design()), design.get(0).design().name()));
            for (Slot slot : design)
            {
                line.append(goButton(slot, origin, look, right)).append(Component.literal(" "));
            }
            source.sendSuccess(() -> line, false);
        }
        source.sendSuccess(() -> overviewButton(origin, look, right, layout), false);
        Component controls = controls(gallery);
        if (controls != null)
        {
            source.sendSuccess(() -> controls, false);
        }
        source.sendSuccess(() -> Component.literal("Right-click one to see its name; try it at night too "
                + "(/time set night)."), false);
        if (gallery == Gallery.EMBERDACTYL)
        {
            startVolcano(source, level, origin, look, right, facing, layout);
        }
        return spawned;
    }

    /** "E1" for "E1A", "15" for "15D": the design's own number, without the letter of its look. */
    static String designLabel(FireDesigns.Design design)
    {
        String label = design.label();
        int end = label.length();
        while (end > 0 && Character.isLetter(label.charAt(end - 1)))
        {
            end--;
        }
        return label.substring(0, end);
    }

    /**
     * Plans the Emberdactyl's volcano behind the gallery (its path's foot facing you) and starts building it, a piece
     * each tick; its egg and its hovering adult come once it stands.
     */
    private static void startVolcano(CommandSourceStack source, ServerLevel level, BlockPos origin, Direction look,
                                     Direction right, Direction facing, Layout layout)
    {
        double along = Math.ceil(layout.depth()) + END_MARGIN + VOLCANO_GAP + EmberVolcano.REACH;
        BlockPos ground = blockAt(origin, look, right, along, 0.0D);
        EmberVolcano.Plan plan = EmberVolcano.plan(ground, facing, EmberVolcanoTuning.SHOWCASE_SEED);
        lastVolcano = plan;
        JOBS.add(new VolcanoJob(plan.start(level), source, origin, look, right));
        source.sendSuccess(() -> Component.literal(String.format(Locale.ROOT, "Its volcano is going up behind the "
                + "gallery, a piece each tick (%d blocks of lava; about a minute). Its egg and an adult come once it "
                + "stands.", plan.lavaBlocks())), true);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || JOBS.isEmpty())
        {
            return;
        }
        VolcanoJob job = JOBS.get(0);
        if (job.build().step(EmberVolcanoTuning.STEP_MILLIS * 1_000_000L))
        {
            JOBS.remove(0);
            finishVolcano(job);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        JOBS.forEach(job -> job.build().abandon());
        JOBS.clear();
    }

    /** True while a volcano is still going up (the tests wait for it). */
    static boolean buildingVolcano()
    {
        return !JOBS.isEmpty();
    }

    /** The last volcano planned, or null. */
    static EmberVolcano.Plan lastVolcano()
    {
        return lastVolcano;
    }

    /** The volcano stands: its egg into the nest, an adult over the crater, and buttons to go and look. */
    private static void finishVolcano(VolcanoJob job)
    {
        EmberVolcano.Plan plan = job.build().plan();
        ServerLevel level = job.source().getLevel();
        FireCandidate egg = FireCandidate.spawnShowcase(level, plan.eggFeet(), plan.frontYaw(), byLabel(NEST_EGG));
        FireCandidate adult = FireCandidate.spawnShowcase(level, hoverFeet(plan), plan.frontYaw(), byLabel(HOVERING_ADULT));
        if (adult != null)
        {
            adult.setNoGravity(true);
        }
        if (egg == null || adult == null)
        {
            LOGGER.warn("Fire showcase: couldn't put the egg (spawned {}) or the adult (spawned {}) at the volcano", egg != null,
                    adult != null);
        }
        Vec3 path = plan.pathFoot();
        Vec3 lookout = plan.lookout();
        Vec3 view = offset(job.origin(), job.look(), job.right(), alongOf(job, plan) - EmberVolcano.REACH - VOLCANO_VIEW_BACK, 0.0D)
                .add(0.0D, VOLCANO_VIEW_UP, 0.0D);
        MutableComponent line = Component.literal("The volcano stands (its egg in the nest, an adult over the crater): ")
                .append(tpButton("[volcano]", view, job.look().toYRot(), VOLCANO_VIEW_PITCH, "The whole volcano, from in front"))
                .append(Component.literal(" "))
                .append(tpButton("[path]", path, plan.pathFootYaw(), 0.0F, "The foot of the path up"))
                .append(Component.literal(" "))
                .append(tpButton("[crater]", lookout, plan.lookoutYaw(), LOOKOUT_PITCH, "The lookout on the rim, over the nest"));
        job.source().sendSuccess(() -> line, true);
    }

    /** Where the adult hovers on show: over the nest, its feet just clear of the egg's top. */
    static Vec3 hoverFeet(EmberVolcano.Plan plan)
    {
        return plan.eggFeet().add(0.0D, byLabel(NEST_EGG).top() + HOVER_OVER_EGG, 0.0D);
    }

    /** How far ahead of the gallery's spot the volcano's middle is. */
    private static double alongOf(VolcanoJob job, EmberVolcano.Plan plan)
    {
        BlockPos ground = plan.ground();
        return (ground.getX() - job.origin().getX()) * job.look().getStepX() + (ground.getZ() - job.origin().getZ()) * job.look().getStepZ();
    }

    private static FireDesigns.Design byLabel(String label)
    {
        for (FireDesigns.Design design : FireDesigns.ALL)
        {
            if (design.label().equals(label))
            {
                return design;
            }
        }
        throw new IllegalStateException("no fire design " + label);
    }

    /** A point in the gallery's frame as a world position on the floor (feet height). */
    private static Vec3 offset(BlockPos origin, Direction look, Direction right, double along, double across)
    {
        return new Vec3(origin.getX() + 0.5D + look.getStepX() * along + right.getStepX() * across,
                origin.getY() + 1.0D,
                origin.getZ() + 0.5D + look.getStepZ() * along + right.getStepZ() * across);
    }

    /** The floor block at a point of the gallery's frame. */
    private static BlockPos blockAt(BlockPos origin, Direction look, Direction right, double along, double across)
    {
        return origin.relative(look, (int) Math.round(along)).relative(right, (int) Math.round(across));
    }

    /** "[A]": stands you in front of that model, looking at it. */
    private static Component goButton(Slot slot, BlockPos origin, Direction look, Direction right)
    {
        FireDesigns.Design design = slot.design();
        double distance = design.front() + STAND_GAP;
        Vec3 stand = offset(origin, look, right, slot.along() - distance, slot.across());
        float pitch = (float) -Math.toDegrees(Math.atan2(design.top() * 0.5D - EYE_HEIGHT, distance));
        pitch = Mth.clamp(pitch, -MAX_LOOK_UP, 10.0F);
        String command = String.format(Locale.ROOT, "/tp @s %.2f %.2f %.2f %.1f %.1f", stand.x, stand.y, stand.z,
                look.toYRot(), pitch);
        String letter = design.label().substring(designLabel(design).length());
        return Component.literal("[" + letter + "]").withStyle(style -> style
                .withColor(DrossColors.FIRE_SHOWCASE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(String.format(Locale.ROOT,
                        "No. %s %s (%s), %.1f blocks tall%n%s", design.label(), design.name(), design.mood(),
                        design.top(), command)))));
    }

    /** A button that teleports you to a spot, looking this way. */
    private static Component tpButton(String text, Vec3 stand, float yaw, float pitch, String what)
    {
        String command = String.format(Locale.ROOT, "/tp @s %.2f %.2f %.2f %.1f %.1f", stand.x, stand.y, stand.z, yaw, pitch);
        return Component.literal(text).withStyle(style -> style
                .withColor(DrossColors.FIRE_SHOWCASE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(what + "\n" + command))));
    }

    /** "[overview]": high above the walkway's middle, looking down over the whole gallery. */
    private static Component overviewButton(BlockPos origin, Direction look, Direction right, Layout layout)
    {
        Vec3 stand = offset(origin, look, right, -OVERVIEW_BACK, (layout.leftEdge() + layout.rightEdge()) / 2.0D)
                .add(0.0D, OVERVIEW_UP, 0.0D);
        return tpButton("[overview]", stand, look.toYRot(), OVERVIEW_PITCH, "High above the gallery, looking over it")
                .copy().append(Component.literal(" the whole gallery from above"));
    }

    /**
     * The gallery's floor: blackstone, the walkway and the lanes between the columns of polished blackstone bricks,
     * cleared above each column up to its tallest model (and the walkway {@link #WALK_CLEAR} high), dips filled.
     */
    private static void buildFloor(ServerLevel level, BlockPos origin, Direction look, Direction right, Layout layout)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState ground = Blocks.BLACKSTONE.defaultBlockState();
        BlockState path = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int fromAcross = Mth.floor(layout.leftEdge()) - SIDE_MARGIN;
        int toAcross = Mth.ceil(layout.rightEdge()) + SIDE_MARGIN;
        int depth = Mth.ceil(layout.depth()) + END_MARGIN;
        List<Column> columns = layout.columns();
        for (int across = fromAcross; across <= toAcross; across++)
        {
            // Which column this line belongs to (for how high to clear), and whether it's a lane between two.
            int clear = WALK_CLEAR;
            boolean lane = false;
            for (int c = 0; c < columns.size(); c++)
            {
                Column col = columns.get(c);
                if (across >= col.across() - col.leftWide() - COL_GAP / 2.0D - 0.5D
                        && across <= col.across() + col.rightWide() + COL_GAP / 2.0D + 0.5D)
                {
                    clear = Math.max(clear, Mth.ceil(col.tallest()) + CLEAR_ABOVE);
                }
                if (c + 1 < columns.size())
                {
                    double mid = (col.across() + col.rightWide() + columns.get(c + 1).across()
                            - columns.get(c + 1).leftWide()) / 2.0D;
                    lane |= Math.abs(across - mid) <= LANE_HALF + 0.5D;
                }
            }
            for (int ahead = -BEHIND; ahead <= depth; ahead++)
            {
                BlockPos floor = origin.relative(look, ahead).relative(right, across);
                boolean walk = ahead >= WALK_FROM && ahead <= WALK_TO;
                int height = walk || ahead < WALK_FROM ? Math.max(WALK_CLEAR, ahead < WALK_FROM ? WALK_CLEAR : clear)
                        : clear;
                for (int dy = 1; dy <= height; dy++)
                {
                    if (!level.getBlockState(pos.setWithOffset(floor, 0, dy, 0)).isAir())
                    {
                        level.setBlock(pos, air, FLOOR_FLAGS);
                    }
                }
                level.setBlock(floor, walk || (lane && ahead > WALK_TO) ? path : ground, FLOOR_FLAGS);
                for (int dy = 1; dy <= MAX_FILL; dy++)
                {
                    if (Showcases.isSolidGround(level, pos.setWithOffset(floor, 0, -dy, 0)))
                    {
                        break;
                    }
                    level.setBlock(pos, ground, FLOOR_FLAGS);
                }
            }
        }
    }

    /** The candidates loaded within {@link #REMOVE_RADIUS} blocks (X and Z, at any height). */
    static List<FireCandidate> nearby(ServerLevel level, Vec3 position)
    {
        AABB area = Showcases.columnsAround(level, position, REMOVE_RADIUS);
        return level.getEntities(ModEntities.FIRE_CANDIDATE.get(), area, candidate -> true);
    }

    private static int removeOld(ServerLevel level, Vec3 position)
    {
        List<FireCandidate> old = nearby(level, position);
        for (FireCandidate candidate : old)
        {
            candidate.discard();
        }
        return old.size();
    }

    private FireShowcase() {}
}
