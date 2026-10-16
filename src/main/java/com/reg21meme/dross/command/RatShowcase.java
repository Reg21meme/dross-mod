package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.ratking.Rat;
import com.reg21meme.dross.boss.ratking.RatDesigns;
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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /dross showcase rats} (test tool, cheats only): spawns the Rat King's rats in a row in front of you, frozen,
 * each on a pedestal with a sign: the four regular rats (R1-R4, {@code dross:rat}) on the left, then the four enraged
 * rats (E1-E4, {@code dross:enraged_rat}, a separate breed) on the right (the boss area's {@code RatDesigns}).
 * <p>
 * Controls (every showcase rat within {@link #REMOVE_RADIUS} blocks; also clickable in chat): {@code /dross showcase
 * rats idle} and {@code /dross showcase rats run} (running on the spot). Right-clicking one rat switches it between the
 * two. Running the command again replaces the row. They can't be hurt except by {@code /kill}.
 */
public final class RatShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Blocks from where you stand to the row of rats. */
    private static final int AHEAD = 5;
    /** Blocks between regular rats, between enraged rats (they're bigger), and the extra gap between the two kinds. */
    private static final int REGULAR_SPACING = 2;
    private static final int ENRAGED_SPACING = 3;
    private static final int KIND_GAP = 2;
    private static final int REMOVE_RADIUS = 64;
    private static final int FLOOR_SIDE_MARGIN = 3;
    /** The floor reaches this far behind the row (their tails). */
    private static final int FLOOR_BEHIND = 4;
    private static final int CLEAR_HEIGHT = 4;
    private static final int MAX_FILL = 4;
    /** What each rat stands on. */
    private static final BlockState PEDESTAL = Blocks.POLISHED_DEEPSLATE.defaultBlockState();

    public static final String COMMAND = "/dross showcase rats";

    /** The controls: idle and running on the spot. */
    public enum Control
    {
        IDLE("idle", "Idle", "Idle: regular rats sniff, rear up and groom; enraged rats hiss, bristle and lunge", "idle", false),
        RUN("run", "Run", "Run on the spot", "running on the spot", true);

        private final String word;
        private final String button;
        private final String hover;
        private final String doing;
        private final boolean running;

        Control(String word, String button, String hover, String doing, boolean running)
        {
            this.word = word;
            this.button = button;
            this.hover = hover;
            this.doing = doing;
            this.running = running;
        }

        public String word()
        {
            return this.word;
        }

        public String command()
        {
            return COMMAND + " " + this.word;
        }
    }

    /** Runs {@code /dross showcase rats}. */
    public static int spawnRats(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction facing = look.getOpposite();
        Direction right = look.getClockWise();
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));

        List<RatDesigns.Design> designs = new ArrayList<>(RatDesigns.REGULAR);
        designs.addAll(RatDesigns.ENRAGED);
        List<Integer> offsets = new ArrayList<>();
        int x = 0;
        for (int i = 0; i < designs.size(); i++)
        {
            if (i > 0)
            {
                boolean enraged = designs.get(i).enraged();
                x += enraged ? ENRAGED_SPACING : REGULAR_SPACING;
                if (enraged && !designs.get(i - 1).enraged())
                {
                    x += KIND_GAP;
                }
            }
            offsets.add(x);
        }
        int shift = -x / 2;

        int removed = removeOld(level, position);
        Showcases.clearFloor(level, origin, look, right, shift - FLOOR_SIDE_MARGIN, shift + x + FLOOR_SIDE_MARGIN,
                AHEAD + FLOOR_BEHIND, CLEAR_HEIGHT, MAX_FILL);

        float yaw = facing.toYRot();
        List<RatDesigns.Design> failed = new ArrayList<>();
        for (int i = 0; i < designs.size(); i++)
        {
            RatDesigns.Design design = designs.get(i);
            BlockPos pedestal = origin.relative(look, AHEAD).relative(right, shift + offsets.get(i)).above();
            level.setBlock(pedestal, PEDESTAL, Block.UPDATE_ALL);
            BlockPos sign = pedestal.relative(facing);
            String kind = design.enraged() ? "enraged rat" : "rat";
            if (!Showcases.placeWallSign(level, sign, facing, "No. " + design.label(), design.name(), design.mood(), kind))
            {
                LOGGER.warn("Rat showcase: couldn't put the sign for {} at {}", design.label(), sign.toShortString());
            }
            if (Rat.spawnShowcase(level, Vec3.atBottomCenterOf(pedestal.above()), yaw, design) == null)
            {
                failed.add(design);
                LOGGER.warn("Rat showcase: couldn't spawn {} ({})", design.label(), design.name());
            }
        }
        int spawned = designs.size() - failed.size();
        LOGGER.info("Rat showcase: spawned {} of {} rats for {}, removed {} old", spawned, designs.size(),
                source.getTextName(), removed);
        source.sendSuccess(() -> Component.literal("Spawned " + spawned + " rats in a row in front of you: the regular "
                + "rats (R1-R4) on the left, the enraged breed (E1-E4) on the right"
                + (removed == 0 ? "" : " (removed " + removed + " old showcase rats)") + "."), true);
        for (RatDesigns.Design design : designs)
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
        source.sendSuccess(RatShowcase::controls, false);
        source.sendSuccess(() -> Component.literal("Or right-click one rat to switch it between idle and running."), false);
        return spawned;
    }

    /** Runs {@code /dross showcase rats idle|run}. */
    public static int control(CommandContext<CommandSourceStack> context, Control control)
    {
        CommandSourceStack source = context.getSource();
        List<Rat> rats = nearby(source.getLevel(), source.getPosition());
        if (rats.isEmpty())
        {
            source.sendFailure(Component.literal("There are no showcase rats within " + REMOVE_RADIUS
                    + " blocks. Spawn them with " + COMMAND + "."));
            return 0;
        }
        for (Rat rat : rats)
        {
            rat.setRunning(control.running);
        }
        int count = rats.size();
        source.sendSuccess(() -> Component.literal(count + " showcase rat" + (count == 1 ? "" : "s") + ": " + control.doing), false);
        return count;
    }

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

    /** Both kinds of showcase rat loaded within {@link #REMOVE_RADIUS} blocks (X and Z, at any height). */
    private static List<Rat> nearby(ServerLevel level, Vec3 position)
    {
        AABB area = Showcases.columnsAround(level, position, REMOVE_RADIUS);
        List<Rat> out = new ArrayList<>();
        for (EntityType<Rat> type : List.of(ModEntities.RAT.get(), ModEntities.ENRAGED_RAT.get()))
        {
            out.addAll(level.getEntities(type, area, Rat::isShowcase));
        }
        return out;
    }

    private static int removeOld(ServerLevel level, Vec3 position)
    {
        List<Rat> old = nearby(level, position);
        for (Rat rat : old)
        {
            rat.discard();
        }
        return old.size();
    }

    private RatShowcase() {}
}
