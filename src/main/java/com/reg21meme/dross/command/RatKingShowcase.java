package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.boss.ratking.RatKingAction;
import com.reg21meme.dross.boss.ratking.RatKingDesigns;
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

/**
 * {@code /dross showcase boss_designs ratking} (test tool, cheats only): spawns one frozen Rat King per candidate design
 * (the boss area's {@code boss.ratking.RatKingDesigns}) in a row in front of you, standing, so they can be compared and
 * one picked. Use a flat world (superflat is ideal): the row is about 35 blocks wide.
 * <p>
 * The layout, seen from where the command was run:
 * <ul>
 *   <li>one row across your view, centred on you, No. 1 on the left; each Rat King {@link #AHEAD} blocks ahead and
 *       {@link #SPACING} blocks from the next, all facing you;</li>
 *   <li>in front of each one a polished blackstone post with a sign: his number, name and style;</li>
 *   <li>the space is cleared and the floor made flat, with room behind them (they fall flat on their backs).</li>
 * </ul>
 * Old showcase Rat Kings within {@link #REMOVE_RADIUS} blocks (X and Z, at any height) are removed first, so running it
 * again replaces the row.
 * <p>
 * Controls (every showcase Rat King within {@link #REMOVE_RADIUS} blocks; also clickable in chat after spawning), one
 * per animation: {@code sleep}, {@code wake}, {@code idle}, {@code walk}, {@code burrow}, {@code popup}, {@code rage},
 * {@code charge} (the spear charge on the spot), {@code die} (for example {@code /dross showcase boss_designs ratking rage}). Right-clicking one Rat King plays his
 * next animation; sneak + right-click: he dies. They can't be hurt except by {@code /kill}.
 * <p>
 * It uses the command's position, rotation and dimension, so it also works from the server console and command blocks.
 */
public final class RatKingShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Blocks from where you stand to the row of Rat Kings. */
    private static final int AHEAD = 9;
    /** Blocks from one Rat King to the next: about a block and a half of rat and his staff, and a gap. */
    private static final int SPACING = 6;
    /** The sign posts stand this many blocks in front of each Rat King (clear of the hole he digs). */
    private static final int SIGN_IN_FRONT = 3;
    /** Old showcase Rat Kings within this many blocks (X and Z, at any height) are removed first; the controls reach this far too. */
    private static final int REMOVE_RADIUS = 64;
    /** The cleared, flat floor reaches this many blocks past each end of the row... */
    private static final int FLOOR_SIDE_MARGIN = 4;
    /** ...and this many blocks behind it (the death lays him flat on his back, about 3.5 blocks long). */
    private static final int FLOOR_BEHIND = 6;
    /** Air is cleared this many blocks above the floor (he's about 4 blocks tall with his staff; popping up, he jumps). */
    private static final int CLEAR_HEIGHT = 7;
    /** A dip in the floor is filled with dirt at most this many blocks deep. */
    private static final int MAX_FILL = 4;

    /** The command that spawns the row; each control is this plus its {@link Control#word()}. */
    public static final String COMMAND = "/dross showcase boss_designs ratking";

    /** The controls, one per animation, in this order (also the chat buttons). */
    public enum Control
    {
        SLEEP(RatKingAction.SLEEP, "Sleep", "Fall asleep (slumped on the staff or curled up), the Zzz floating up", "asleep"),
        WAKE(RatKingAction.WAKE, "Wake up", "Startle awake, stretch and yawn, grab the staff, stand", "waking up"),
        IDLE(RatKingAction.IDLE, "Stand", "Stand: breathing, shifty glances, each one's gesture", "standing"),
        WALK(RatKingAction.WALK, "Walk", "Walk on the spot", "walking on the spot"),
        BURROW(RatKingAction.BURROW, "Burrow", "Dig, dive into the ground head first; the dirt mound stays", "burrowing"),
        POPUP(RatKingAction.POPUP, "Pop up", "Pop up out of the ground (best after Burrow)", "popping up"),
        RAGE(RatKingAction.RAGE, "Rage!", "Stomp, eyes turn red, steam out of the ears (sleep calms them down)", "flying into a rage"),
        CHARGE(RatKingAction.CHARGE_START, "Charge", "Crouch with the staff levelled like a spear, dash (on the spot), skid", "charging"),
        DIE(RatKingAction.DEATH, "Die", "The silly death: flat on the back, tongue out, X eyes", "dying, very dramatically");

        private final RatKingAction action;
        private final String button;
        private final String hover;
        private final String doing;

        Control(RatKingAction action, String button, String hover, String doing)
        {
            this.action = action;
            this.button = button;
            this.hover = hover;
            this.doing = doing;
        }

        /** The subcommand word, for example {@code rage}. */
        public String word()
        {
            return RatKing.word(this.action);
        }

        /** The full command, for example {@code /dross showcase boss_designs ratking rage}. */
        public String command()
        {
            return COMMAND + " " + this.word();
        }
    }

    /** Runs {@code /dross showcase boss_designs ratking}. */
    public static int spawnKings(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        List<RatKingDesigns.Design> designs = RatKingDesigns.ALL;
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction facing = look.getOpposite();
        Direction right = look.getClockWise();
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));

        int removed = removeOld(level, position);

        int firstOffset = -((designs.size() - 1) * SPACING) / 2;
        int lastOffset = firstOffset + (designs.size() - 1) * SPACING;
        Showcases.clearFloor(level, origin, look, right, firstOffset - FLOOR_SIDE_MARGIN, lastOffset + FLOOR_SIDE_MARGIN,
                AHEAD + FLOOR_BEHIND, CLEAR_HEIGHT, MAX_FILL);

        BlockState post = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        float yaw = facing.toYRot();
        List<RatKingDesigns.Design> failed = new ArrayList<>();
        for (int i = 0; i < designs.size(); i++)
        {
            RatKingDesigns.Design design = designs.get(i);
            BlockPos ground = origin.relative(look, AHEAD).relative(right, firstOffset + i * SPACING);
            BlockPos postPos = ground.relative(facing, SIGN_IN_FRONT).above();
            level.setBlock(postPos, post, Block.UPDATE_ALL);
            BlockPos sign = postPos.relative(facing);
            if (!Showcases.placeWallSign(level, sign, facing, "No. " + design.label(), design.name(), design.mood()))
            {
                LOGGER.warn("Rat King showcase: couldn't put the sign for No. {} at {}", design.label(), sign.toShortString());
            }
            if (RatKing.spawnShowcase(level, Vec3.atBottomCenterOf(ground.above()), yaw, design) == null)
            {
                failed.add(design);
                LOGGER.warn("Rat King showcase: couldn't spawn No. {} ({})", design.label(), design.name());
            }
        }

        int spawned = designs.size() - failed.size();
        LOGGER.info("Rat King showcase: spawned {} of {} for {} in {}, row centre {} blocks ahead of {}, facing {}; removed {} old",
                spawned, designs.size(), source.getTextName(), level.dimension().location(), AHEAD, origin.toShortString(),
                facing.getName(), removed);

        String removedNote = removed == 0 ? "no old showcase Rat Kings nearby"
                : "removed " + removed + " old showcase Rat King" + (removed == 1 ? "" : "s");
        String summary = "Spawned " + (failed.isEmpty() ? String.valueOf(spawned) : spawned + " of " + designs.size())
                + " Rat King designs in a row in front of you, No. " + designs.get(0).label() + " on the left ("
                + removedNote + "):";
        source.sendSuccess(() -> Component.literal(summary), true);
        for (RatKingDesigns.Design design : designs)
        {
            String line = design.label() + ". " + design.name() + " (" + design.mood() + ")";
            if (failed.contains(design))
            {
                source.sendFailure(Component.literal(line + ": couldn't spawn, see the game log"));
            }
            else
            {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        source.sendSuccess(RatKingShowcase::controls, false);
        source.sendSuccess(() -> Component.literal("Or right-click one Rat King to play his next animation; "
                + "sneak + right-click: he dies."), false);
        return spawned;
    }

    /** Runs one of the controls ({@code /dross showcase boss_designs ratking sleep|wake|...|die}). */
    public static int control(CommandContext<CommandSourceStack> context, Control control)
    {
        CommandSourceStack source = context.getSource();
        List<RatKing> kings = nearby(source.getLevel(), source.getPosition());
        if (kings.isEmpty())
        {
            source.sendFailure(Component.literal("There are no showcase Rat Kings within " + REMOVE_RADIUS
                    + " blocks. Spawn them with " + COMMAND + "."));
            return 0;
        }
        for (RatKing king : kings)
        {
            king.play(control.action);
        }
        int count = kings.size();
        source.sendSuccess(() -> Component.literal(count + " showcase Rat King" + (count == 1 ? "" : "s") + ": " + control.doing), false);
        return count;
    }

    /** A chat line of clickable buttons, one per control. */
    private static Component controls()
    {
        MutableComponent line = Component.literal("Animations: ");
        for (Control control : Control.values())
        {
            String command = control.command();
            line.append(Component.literal("[" + control.button + "]").withStyle(style -> style
                    .withColor(DrossColors.RAT_SHOWCASE_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(control.hover + "\n" + command)))));
            line.append(" ");
        }
        return line;
    }

    /** The showcase Rat Kings loaded within {@link #REMOVE_RADIUS} blocks (X and Z, at any height). */
    private static List<RatKing> nearby(ServerLevel level, Vec3 position)
    {
        AABB area = Showcases.columnsAround(level, position, REMOVE_RADIUS);
        return level.getEntities(ModEntities.RAT_KING.get(), area, RatKing::isShowcase);
    }

    private static int removeOld(ServerLevel level, Vec3 position)
    {
        List<RatKing> old = nearby(level, position);
        for (RatKing king : old)
        {
            king.discard();
        }
        return old.size();
    }

    private RatKingShowcase() {}
}
