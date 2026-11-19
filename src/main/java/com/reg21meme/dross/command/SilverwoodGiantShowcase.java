package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.giant.GiantMoments;
import com.reg21meme.dross.boss.giant.SilverwoodGiant;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;

/**
 * {@code /dross showcase silverwood_giants} (test tool, cheats only): the Silverwood Giant's three looks (the boss area's
 * {@code SilverwoodGiant}: G1 the Boulder-Hurler, G2 the Hoplite, G3 the Old Warrior) in a row in front of you on a
 * patch of Silvermoss, frozen, facing you, a silverwood post and a sign before each.
 * <p>
 * Controls (every showcase giant within {@link #REMOVE_RADIUS} blocks; also clickable in chat): {@code /dross showcase
 * silverwood_giants <animation>} for each of its animations ({@link SilverwoodGiant#ANIMATIONS}: idle, walk, run, warn,
 * smash, sweep, stomp, throw, hurt, death), played on all three at once with their sounds. Right-clicking one plays its
 * next animation. Running the command again replaces the row. They can't be hurt except by {@code /kill}.
 */
public final class SilverwoodGiantShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String COMMAND = "/dross showcase silverwood_giants";
    /** Blocks from you to the row, between the giants, and of floor behind and beside it. */
    private static final int AHEAD = 9;
    private static final int SPACING = 7;
    private static final int FLOOR_BEHIND = 7;
    private static final int FLOOR_SIDE_MARGIN = 6;
    /** Air cleared over the floor (G2's spear stands 5.6 blocks), and the deepest dip the floor fills. */
    private static final int CLEAR_HEIGHT = 8;
    private static final int MAX_FILL = 4;
    /** Blocks from each giant towards you to its sign's post. */
    private static final int POST_AHEAD = 4;
    static final int REMOVE_RADIUS = 80;

    /** What each animation's button says (hovered). */
    private static final List<String> HOVERS = List.of(
            "Standing: heavy breathing, a slow look round, a blink",
            "Its slow, heavy walk (on the spot: in the game it moves along)",
            "Its lumbering run for the chase (on the spot)",
            "Its warning: it stares, its eyes flare, it huffs and stamps",
            "An overhead blow (G1's fist, G2's shield, G3's club)",
            "A backhand sweep, knocking you away to its left",
            "A stamp that throws whoever's round it up",
            "It hurls a boulder (G1 from its shoulder, then tears up a new one; G2 and G3 tear one out of the ground)",
            "A flinch",
            "It falls on its face and stays down");

    private SilverwoodGiantShowcase() {}

    /** Runs {@code /dross showcase silverwood_giants}. */
    public static int spawnGiants(CommandContext<CommandSourceStack> context)
    {
        return spawnGiants(context.getSource());
    }

    /** The showcase, from a command source (the tests call this). */
    public static int spawnGiants(CommandSourceStack source)
    {
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction facing = look.getOpposite();
        Direction right = look.getClockWise();
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));
        List<GiantMoments.Look> looks = GiantMoments.LOOKS;
        int width = (looks.size() - 1) * SPACING;
        int shift = -width / 2;
        int removed = removeOld(level, position);
        int fromRight = shift - FLOOR_SIDE_MARGIN;
        int toRight = shift + width + FLOOR_SIDE_MARGIN;
        Showcases.clearFloor(level, origin, look, right, fromRight, toRight, AHEAD + FLOOR_BEHIND, CLEAR_HEIGHT, MAX_FILL);
        // Silvermoss under the row, as in their forest
        BlockState moss = ModBlocks.SILVERMOSS.get().defaultBlockState();
        for (int across = fromRight; across <= toRight; across++)
        {
            for (int along = 1; along <= AHEAD + FLOOR_BEHIND; along++)
            {
                level.setBlock(origin.relative(look, along).relative(right, across), moss, Block.UPDATE_CLIENTS);
            }
        }
        BlockState post = ModBlocks.SILVERWOOD_LOG.get().defaultBlockState();
        float yaw = facing.toYRot();
        int spawned = 0;
        for (int i = 0; i < looks.size(); i++)
        {
            GiantMoments.Look giantLook = looks.get(i);
            BlockPos ground = origin.relative(look, AHEAD).relative(right, shift + i * SPACING);
            BlockPos postAt = ground.relative(facing, POST_AHEAD).above();
            level.setBlock(postAt, post, Block.UPDATE_ALL);
            BlockPos sign = postAt.relative(facing);
            if (!Showcases.placeWallSign(level, sign, facing, "No. " + giantLook.label(), "Silverwood Giant",
                    giantLook.mood(), giantLook == GiantMoments.DEFAULT ? "(in the game)" : ""))
            {
                LOGGER.warn("Silverwood giants showcase: couldn't put the sign for {} at {}", giantLook.id(), sign.toShortString());
            }
            if (SilverwoodGiant.spawnShowcase(level, Vec3.atBottomCenterOf(ground.above()), yaw, giantLook) != null)
            {
                spawned++;
            }
            else
            {
                LOGGER.warn("Silverwood giants showcase: couldn't spawn {}", giantLook.id());
            }
        }
        int count = spawned;
        LOGGER.info("Silverwood giants showcase: spawned {} of {} for {}, removed {} old", count, looks.size(),
                source.getTextName(), removed);
        source.sendSuccess(() -> Component.literal("Spawned the Silverwood Giant's three looks in a row in front of you: G1 "
                + "the Boulder-Hurler (the one in the game), G2 the Hoplite, G3 the Old Warrior"
                + (removed == 0 ? "" : " (removed " + removed + " old ones)") + "."), true);
        source.sendSuccess(SilverwoodGiantShowcase::controls, false);
        source.sendSuccess(() -> Component.literal("Or right-click one to play its next animation."), false);
        return count;
    }

    /** Runs {@code /dross showcase silverwood_giants <animation>}. */
    public static int play(CommandContext<CommandSourceStack> context, String animation)
    {
        return play(context.getSource(), animation);
    }

    /** Plays an animation on every showcase giant near the command (the tests call this). */
    public static int play(CommandSourceStack source, String animation)
    {
        List<Entity> giants = nearby(source.getLevel(), source.getPosition());
        if (giants.isEmpty())
        {
            source.sendFailure(Component.literal("There are no showcase giants within " + REMOVE_RADIUS
                    + " blocks. Spawn them with " + COMMAND + "."));
            return 0;
        }
        for (Entity entity : giants)
        {
            ((SilverwoodGiant) entity).playShowcase(animation);
        }
        int count = giants.size();
        source.sendSuccess(() -> Component.literal(count + " showcase giant" + (count == 1 ? "" : "s") + ": " + animation), false);
        return count;
    }

    /** Its animations, each a button. */
    public static List<String> animations()
    {
        return SilverwoodGiant.ANIMATIONS;
    }

    private static Component controls()
    {
        MutableComponent line = Component.literal("Animations: ");
        for (int i = 0; i < SilverwoodGiant.ANIMATIONS.size(); i++)
        {
            String name = SilverwoodGiant.ANIMATIONS.get(i);
            String command = COMMAND + " " + name;
            String hover = HOVERS.get(i);
            line.append(Component.literal("[" + name + "]").withStyle(style -> style
                    .withColor(DrossColors.SILVERWOOD_GIANT_SHOWCASE_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(hover + "\n" + command)))));
            line.append(" ");
        }
        return line;
    }

    /** Every showcase giant loaded within {@link #REMOVE_RADIUS} blocks (X and Z, at any height). */
    static List<Entity> nearby(ServerLevel level, Vec3 position)
    {
        return level.getEntities((Entity) null, Showcases.columnsAround(level, position, REMOVE_RADIUS),
                e -> e instanceof SilverwoodGiant giant && giant.isShowcase());
    }

    private static int removeOld(ServerLevel level, Vec3 position)
    {
        List<Entity> old = nearby(level, position);
        for (Entity entity : old)
        {
            entity.discard();
        }
        return old.size();
    }
}
