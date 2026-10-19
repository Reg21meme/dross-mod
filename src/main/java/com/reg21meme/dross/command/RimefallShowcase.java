package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.rimefall.RimefallCandidate;
import com.reg21meme.dross.boss.rimefall.RimefallDesigns;
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
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * {@code /dross showcase boss_designs rimefall} (test tool, cheats only): builds a snowy gallery in front of you with the
 * Rimefall candidates (the boss area's {@code boss.rimefall.RimefallDesigns}: the user's picks, the Ice Necromancer and
 * its three servants and the hand kept for its fight, then Rimefall's regular mobs; frozen models labelled "1A", "2B"...
 * from about 3 to 21 blocks tall), so they can be looked at side by side. Use a flat world (superflat is ideal): the
 * gallery is about 130 blocks long and 60 wide.
 * <p>
 * The layout, seen from where the command was run:
 * <ul>
 *   <li>a walkway of calcite runs straight ahead, {@link #WALK_HALF} blocks either side of you (no lights: the user's
 *       request);</li>
 *   <li>the bosses first, then (after a gap) the regular mobs, each section opening with a sign on a post either side
 *       of the walkway; the candidates stand on snow either side of it in the list's order, taking turns left and right
 *       (so the variants of one design face each other across it), each facing the walkway; one much longer than it is
 *       wide ({@link #SIDEWAYS_RATIO}) stands sideways instead, facing back towards you, so you see it from the side;</li>
 *   <li>by each one, at the walkway's edge, a packed ice post with a sign: its label ("No. 1A"), name and variant;</li>
 *   <li>the ground is cleared (up to {@link #CLEAR_ABOVE} blocks over the tallest) and made flat.</li>
 * </ul>
 * The chat lists them all, each with a [go] button that puts you on the walkway facing it. Old candidates within
 * {@link #REMOVE_RADIUS} blocks (X and Z, at any height) are removed first, so running it again replaces the gallery.
 * Right-clicking a candidate shows its label, name and variant; it can't be hurt except by {@code /kill}.
 * <p>
 * It uses the command's position, rotation and dimension, so it also works from the server console and command blocks.
 */
public final class RimefallShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The walkway reaches this many blocks either side of its middle (it's 5 wide). */
    static final int WALK_HALF = 2;
    /** Blocks between the walkway's edge and the nearest part of a candidate. */
    private static final double FRONT_GAP = 3.0D;
    /** Blocks between neighbouring candidates along the walkway. */
    private static final double GAP = 4.0D;
    /** The first candidates start this many blocks ahead of where you stand (after the first section's signs). */
    private static final double START = 5.0D;
    /** The gap between the bosses and the regular mobs (the second section's signs stand in it). */
    private static final double SECTION_GAP = 10.0D;
    /** The floor reaches this far past the farthest candidate at the sides, and past the last one at the end. */
    private static final int SIDE_MARGIN = 3;
    private static final int END_MARGIN = 4;
    /** ...and this far behind you. */
    private static final int BEHIND = 2;
    /** Air is cleared this many blocks above the tallest candidate. */
    private static final int CLEAR_ABOVE = 3;
    /** A dip in the floor is filled at most this many blocks deep. */
    private static final int MAX_FILL = 4;
    /** A candidate at least this many times longer (front to back) than it is wide stands sideways. */
    static final float SIDEWAYS_RATIO = 1.6F;
    /** Old candidates within this many blocks (X and Z, at any height) are removed first. */
    private static final int REMOVE_RADIUS = 300;
    /** Where a [go] button puts your eyes (blocks above the floor) and how far up or down you may look (degrees). */
    private static final double EYE_HEIGHT = 1.62D;
    private static final float MAX_LOOK_UP = 40.0F;
    /** Floor flags: no neighbour updates (no redstone going off), but players still see every change. */
    private static final int FLOOR_FLAGS = Block.UPDATE_CLIENTS;

    /** The command that builds the gallery. */
    public static final String COMMAND = "/dross showcase boss_designs rimefall";

    /**
     * Where one candidate goes, in the gallery's own frame: {@code along} blocks ahead of the command's spot,
     * {@code lateral} blocks out from the walkway's middle line to the side of its row.
     *
     * @param left     true for the row on your left
     * @param sideways true if it faces back along the walkway instead of facing it
     * @param depth    how far out from the walkway's middle line the candidate reaches (blocks)
     */
    record Slot(RimefallDesigns.Design design, boolean left, boolean sideways, double along, double lateral, double depth)
    {
    }

    /** A section's start: its signs stand this many blocks ahead, either side of the walkway. */
    record Section(String title, double along)
    {
    }

    /** The whole gallery's layout: every slot, the sections, its length and how far each row reaches out. */
    record Layout(List<Slot> slots, List<Section> sections, double length, double leftDepth, double rightDepth)
    {
    }

    /**
     * Lays the candidates out (in the gallery's own frame, so it doesn't depend on where the command is run). Each
     * candidate's extents round its feet are its measured ones ({@code front}, {@code back}, {@code left},
     * {@code right}), turned to the way it faces. They take turns left and right in the list's order; the regular mobs
     * start a new section after a gap.
     */
    static Layout layout(List<RimefallDesigns.Design> designs)
    {
        List<Slot> slots = new ArrayList<>();
        List<Section> sections = new ArrayList<>();
        double[] cursor = {START, START};
        double[] depth = {0.0D, 0.0D};
        Boolean section = null;
        int inSection = 0;
        for (RimefallDesigns.Design d : designs)
        {
            if (section == null || section != d.boss())
            {
                double at = section == null ? START - 3.0D : Math.max(cursor[0], cursor[1]) - GAP + SECTION_GAP / 2.0D;
                if (section != null)
                {
                    cursor[0] = cursor[1] = Math.max(cursor[0], cursor[1]) - GAP + SECTION_GAP;
                }
                sections.add(new Section(d.boss() ? "Bosses" : "Regular mobs", at));
                section = d.boss();
                inSection = 0;
            }
            boolean left = inSection % 2 == 0;
            inSection++;
            int row = left ? 0 : 1;
            boolean sideways = d.front() + d.back() >= SIDEWAYS_RATIO * (d.left() + d.right());
            // Its extents towards the start, towards the end, towards the walkway and away from it.
            double towardsStart;
            double towardsEnd;
            double towardsWalk;
            double away;
            if (sideways)
            {
                // Facing back towards the start: its front is towards the start, its own left (right) towards the
                // walkway in the left (right) row.
                towardsStart = d.front();
                towardsEnd = d.back();
                towardsWalk = left ? d.left() : d.right();
                away = left ? d.right() : d.left();
            }
            else
            {
                // Facing the walkway: its front is towards the walkway, its own left towards the end in the left row
                // (and towards the start in the right row).
                towardsWalk = d.front();
                away = d.back();
                towardsEnd = left ? d.left() : d.right();
                towardsStart = left ? d.right() : d.left();
            }
            double along = cursor[row] + towardsStart;
            cursor[row] = along + towardsEnd + GAP;
            double lateral = WALK_HALF + 1 + FRONT_GAP + towardsWalk;
            depth[row] = Math.max(depth[row], lateral + away);
            slots.add(new Slot(d, left, sideways, along, lateral, lateral + away));
        }
        return new Layout(slots, sections, Math.max(cursor[0], cursor[1]) - GAP, depth[0], depth[1]);
    }

    /** Runs {@code /dross showcase boss_designs rimefall}. */
    public static int buildGallery(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction leftSide = look.getCounterClockWise();
        Direction rightSide = look.getClockWise();
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));
        Layout layout = layout(RimefallDesigns.ALL);
        float tallest = 0.0F;
        for (RimefallDesigns.Design design : RimefallDesigns.ALL)
        {
            tallest = Math.max(tallest, design.top());
        }

        int removed = removeOld(level, position);
        buildFloor(level, origin, look, rightSide, Mth.ceil(layout.length()) + END_MARGIN,
                Mth.ceil(layout.leftDepth()) + SIDE_MARGIN, Mth.ceil(layout.rightDepth()) + SIDE_MARGIN,
                Mth.ceil(tallest) + CLEAR_ABOVE);

        BlockState post = Blocks.PACKED_ICE.defaultBlockState();
        for (Section section : layout.sections())
        {
            for (Direction side : new Direction[] {leftSide, rightSide})
            {
                BlockPos postPos = origin.relative(look, (int) Math.round(section.along())).relative(side, WALK_HALF + 1)
                        .above();
                level.setBlock(postPos, post, Block.UPDATE_ALL);
                level.setBlock(postPos.above(), post, Block.UPDATE_ALL);
                Direction towardsWalk = side.getOpposite();
                Showcases.placeWallSign(level, postPos.above().relative(towardsWalk), towardsWalk, "Rimefall",
                        section.title(), "the picks");
            }
        }
        List<RimefallDesigns.Design> failed = new ArrayList<>();
        List<Component> lines = new ArrayList<>();
        for (Slot slot : layout.slots())
        {
            RimefallDesigns.Design design = slot.design();
            Direction side = slot.left() ? leftSide : rightSide;
            Direction towardsWalk = side.getOpposite();
            Direction facing = slot.sideways() ? look.getOpposite() : towardsWalk;
            Vec3 feet = offset(origin, look, side, slot.along(), slot.lateral());
            // The post and its sign at the walkway's edge, the sign facing the walkway.
            BlockPos postPos = origin.relative(look, (int) Math.round(slot.along())).relative(side, WALK_HALF + 1).above();
            level.setBlock(postPos, post, Block.UPDATE_ALL);
            if (!Showcases.placeWallSign(level, postPos.relative(towardsWalk), towardsWalk, "No. " + design.label(),
                    design.name(), design.mood()))
            {
                LOGGER.warn("Rimefall showcase: couldn't put the sign for No. {} at {}", design.label(),
                        postPos.relative(towardsWalk).toShortString());
            }
            if (RimefallCandidate.spawnShowcase(level, feet, facing.toYRot(), design) == null)
            {
                failed.add(design);
                LOGGER.warn("Rimefall showcase: couldn't spawn No. {} ({})", design.label(), design.name());
                lines.add(Component.literal(design.label() + ". " + design.name() + ": couldn't spawn, see the game log"));
                continue;
            }
            lines.add(goLine(slot, origin, look, side));
        }

        int spawned = layout.slots().size() - failed.size();
        LOGGER.info("Rimefall showcase: spawned {} of {} for {} in {}, a gallery {} blocks long from {} looking {}; "
                        + "removed {} old", spawned, layout.slots().size(), source.getTextName(), level.dimension().location(),
                Math.round(layout.length()), origin.toShortString(), look.getName(), removed);
        String removedNote = removed == 0 ? "no old candidates nearby"
                : "removed " + removed + " old candidate" + (removed == 1 ? "" : "s");
        String summary = "Built the Rimefall gallery (your picks): " + (failed.isEmpty() ? String.valueOf(spawned)
                : spawned + " of " + layout.slots().size()) + " models along a walkway ahead of you, the bosses first, "
                + "then the regular mobs (" + removedNote + "). Click [go] to stand by one:";
        source.sendSuccess(() -> Component.literal(summary), true);
        for (Component line : lines)
        {
            source.sendSuccess(() -> line, false);
        }
        source.sendSuccess(() -> Component.literal("Right-click one to see its name; try it at night too (/time set night)."),
                false);
        return spawned;
    }

    /** A point in the gallery's frame as a world position on the floor (feet height). */
    private static Vec3 offset(BlockPos origin, Direction look, Direction side, double along, double lateral)
    {
        return new Vec3(origin.getX() + 0.5D + look.getStepX() * along + side.getStepX() * lateral,
                origin.getY() + 1.0D,
                origin.getZ() + 0.5D + look.getStepZ() * along + side.getStepZ() * lateral);
    }

    /** "[go] No. 1A Name (variant), X tall": the button stands you on the far side of the walkway, looking at it. */
    private static Component goLine(Slot slot, BlockPos origin, Direction look, Direction side)
    {
        RimefallDesigns.Design design = slot.design();
        Vec3 stand = offset(origin, look, side, slot.along(), -WALK_HALF);
        double distance = slot.lateral() + WALK_HALF;
        float pitch = (float) -Math.toDegrees(Math.atan2(design.top() * 0.5D - EYE_HEIGHT, distance));
        pitch = Mth.clamp(pitch, -MAX_LOOK_UP, 10.0F);
        String command = String.format(Locale.ROOT, "/tp @s %.2f %.2f %.2f %.1f %.1f", stand.x, stand.y, stand.z,
                side.toYRot(), pitch);
        MutableComponent line = Component.literal("[go]").withStyle(style -> style
                .withColor(DrossColors.RIMEFALL_SHOWCASE_BUTTON)
                .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT,
                        Component.literal("Stand by No. " + design.label() + "\n" + command))));
        String text = String.format(Locale.ROOT, " %s. %s (%s), %.1f blocks tall%s", design.label(), design.name(),
                design.mood(), design.top(), design.boss() ? "" : ", regular mob");
        return line.append(Component.literal(text));
    }

    /**
     * The gallery's floor: cleared {@code clear} blocks up, snow, with the calcite walkway down the middle, dips under it
     * filled with dirt.
     *
     * @param length    blocks ahead of the command's spot it reaches
     * @param leftWide  blocks out to the left of the walkway's middle line
     * @param rightWide blocks out to the right
     */
    private static void buildFloor(ServerLevel level, BlockPos origin, Direction look, Direction right, int length,
                                   int leftWide, int rightWide, int clear)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState walk = Blocks.CALCITE.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int ahead = -BEHIND; ahead <= length; ahead++)
        {
            for (int side = -leftWide; side <= rightWide; side++)
            {
                BlockPos ground = origin.relative(look, ahead).relative(right, side);
                for (int dy = 1; dy <= clear; dy++)
                {
                    if (!level.getBlockState(pos.setWithOffset(ground, 0, dy, 0)).isAir())
                    {
                        level.setBlock(pos, air, FLOOR_FLAGS);
                    }
                }
                level.setBlock(ground, Math.abs(side) <= WALK_HALF ? walk : snow, FLOOR_FLAGS);
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

    /** The candidates loaded within {@link #REMOVE_RADIUS} blocks (X and Z, at any height). */
    static List<RimefallCandidate> nearby(ServerLevel level, Vec3 position)
    {
        AABB area = Showcases.columnsAround(level, position, REMOVE_RADIUS);
        return level.getEntities(ModEntities.RIMEFALL_CANDIDATE.get(), area, candidate -> true);
    }

    private static int removeOld(ServerLevel level, Vec3 position)
    {
        List<RimefallCandidate> old = nearby(level, position);
        for (RimefallCandidate candidate : old)
        {
            candidate.discard();
        }
        return old.size();
    }

    private RimefallShowcase() {}
}
