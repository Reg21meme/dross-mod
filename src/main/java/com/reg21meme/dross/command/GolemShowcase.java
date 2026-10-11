package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.boss.golem.GolemDesigns;
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
import java.util.function.Consumer;

/**
 * {@code /dross showcase golems} (test tool, cheats only): spawns one frozen Cinder Colossus per candidate design
 * (the boss area's {@code boss.golem.GolemDesigns}) in a row in front of you, in their dormant rock shells, so they
 * can be compared and one picked. Use a flat world (superflat is ideal): the row is about 35 blocks wide.
 * <p>
 * The layout, seen from where the command was run:
 * <ul>
 *   <li>one row across your view, centred on you, No. 1 on the left; each golem {@link #AHEAD} blocks ahead and
 *       {@link #SPACING} blocks from the next, all facing you;</li>
 *   <li>in front of each one a polished blackstone post with a sign: its number (3E, 3F...), name and style;</li>
 *   <li>the space is cleared and the floor made flat, so you can walk round them.</li>
 * </ul>
 * Old showcase golems within {@link #REMOVE_RADIUS} blocks are removed first, so running it again replaces the row.
 * <p>
 * Controls (all the golems within {@link #REMOVE_RADIUS} blocks; also clickable in chat after spawning). The stages
 * of the fight are dormant (rock shell), erupted (the volcano has blown and lava poured over the shell) and the lava
 * core:
 * <ul>
 *   <li>{@code /dross showcase golems dormant}: back in their dormant shells, standing;</li>
 *   <li>{@code /dross showcase golems erupt}: dormant, then the volcano erupts and lava pours over them;</li>
 *   <li>{@code /dross showcase golems erupted}: straight to the erupted shell;</li>
 *   <li>{@code /dross showcase golems break}: erupted, then the shell breaks off (they end as lava);</li>
 *   <li>{@code /dross showcase golems core}: straight to the lava core form, the volcano erupting;</li>
 *   <li>{@code /dross showcase golems die}: the death, cooling into obsidian statues (in their current form);</li>
 *   <li>{@code /dross showcase golems walk} / {@code idle}: knuckle-walking on the spot / standing.</li>
 * </ul>
 * Right-clicking one golem moves just that one on to its next stage (dormant: erupts; erupted: breaks out; lava or
 * statue: dormant again), sneak + right-click makes it die. They can't be hurt except by {@code /kill}.
 * <p>
 * It uses the command's position, rotation and dimension, so it also works from the server console and command
 * blocks, for example {@code execute in minecraft:overworld positioned 0 -60 0 rotated 0 0 run dross showcase golems}.
 */
public final class GolemShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Blocks from where you stand to the row of golems (their centres). */
    private static final int AHEAD = 13;
    /** Blocks from one golem's centre to the next: about five blocks of golem and a four-block gap. */
    private static final int SPACING = 9;
    /** The sign posts stand this many blocks in front of each golem's centre (clear of its fists). */
    private static final int SIGN_IN_FRONT = 5;
    /** Old showcase golems within this many blocks are removed first; the controls reach this far too. */
    private static final int REMOVE_RADIUS = 64;
    /** The cleared, flat floor reaches this many blocks past each end of the row... */
    private static final int FLOOR_SIDE_MARGIN = 5;
    /** ...and this many blocks behind it. */
    private static final int FLOOR_BEHIND = 5;
    /** Air is cleared this many blocks above the floor (the golems are about five and a half blocks tall; lava flies higher). */
    private static final int CLEAR_HEIGHT = 10;
    /** A dip in the floor is filled with dirt at most this many blocks deep. */
    private static final int MAX_FILL = 4;
    /** Floor blocks don't poke their neighbours (no redstone going off), but players still see every change. */
    private static final int FLOOR_FLAGS = Block.UPDATE_CLIENTS;

    /** What the controls do to every showcase golem in range. */
    public enum Control
    {
        DORMANT("dormant", "back in their dormant shells", CinderColossus::showDormant),
        ERUPT("erupt", "erupting: lava pours over their shells", CinderColossus::playErupt),
        ERUPTED("erupted", "in their erupted shells", CinderColossus::showErupted),
        BREAK("break", "breaking out of their shells", CinderColossus::playBreakShell),
        CORE("core", "in their lava core form, the volcanoes erupting", CinderColossus::showCore),
        DIE("die", "dying: cooling into obsidian statues", CinderColossus::playDeath),
        WALK("walk", "knuckle-walking on the spot", golem -> golem.setWalking(true)),
        IDLE("idle", "standing", golem -> golem.setWalking(false));

        private final String word;
        private final String doing;
        private final Consumer<CinderColossus> action;

        Control(String word, String doing, Consumer<CinderColossus> action)
        {
            this.word = word;
            this.doing = doing;
            this.action = action;
        }

        /** The subcommand word, for example {@code break}. */
        public String word()
        {
            return this.word;
        }
    }

    /** Runs {@code /dross showcase golems}. */
    public static int spawnGolems(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        List<GolemDesigns.Design> designs = GolemDesigns.ALL;
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction facing = look.getOpposite();
        Direction right = look.getClockWise();
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));

        int removed = removeOldGolems(level, position);

        int firstOffset = -((designs.size() - 1) * SPACING) / 2;
        int lastOffset = firstOffset + (designs.size() - 1) * SPACING;
        clearFloor(level, origin, look, right, firstOffset - FLOOR_SIDE_MARGIN, lastOffset + FLOOR_SIDE_MARGIN, AHEAD + FLOOR_BEHIND);

        BlockState post = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();
        float yaw = facing.toYRot();
        List<GolemDesigns.Design> failed = new ArrayList<>();
        for (int i = 0; i < designs.size(); i++)
        {
            GolemDesigns.Design design = designs.get(i);
            BlockPos ground = origin.relative(look, AHEAD).relative(right, firstOffset + i * SPACING);
            BlockPos postPos = ground.relative(facing, SIGN_IN_FRONT).above();
            level.setBlock(postPos, post, Block.UPDATE_ALL);
            BlockPos sign = postPos.relative(facing);
            if (!Showcases.placeWallSign(level, sign, facing, "No. " + design.label(), design.name(), design.mood()))
            {
                LOGGER.warn("Golem showcase: couldn't put the sign for No. {} at {}", design.label(), sign.toShortString());
            }
            if (CinderColossus.spawnShowcase(level, Vec3.atBottomCenterOf(ground.above()), yaw, design) == null)
            {
                failed.add(design);
                LOGGER.warn("Golem showcase: couldn't spawn No. {} ({})", design.label(), design.name());
            }
        }

        int spawned = designs.size() - failed.size();
        LOGGER.info("Golem showcase: spawned {} of {} golems for {} in {}, row centre {} blocks ahead of {}, facing {}; "
                        + "removed {} old showcase golem(s)", spawned, designs.size(), source.getTextName(),
                level.dimension().location(), AHEAD, origin.toShortString(), facing.getName(), removed);

        String removedNote = removed == 0 ? "no old showcase golems nearby"
                : "removed " + removed + " old showcase golem" + (removed == 1 ? "" : "s");
        String summary = "Spawned " + (failed.isEmpty() ? String.valueOf(spawned) : spawned + " of " + designs.size())
                + " Cinder Colossus designs in a row in front of you, No. " + designs.get(0).label() + " on the left ("
                + removedNote + "):";
        source.sendSuccess(() -> Component.literal(summary), true);
        for (GolemDesigns.Design design : designs)
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
        source.sendSuccess(GolemShowcase::controls, false);
        source.sendSuccess(() -> Component.literal("Or right-click one golem to move it on a stage (dormant > erupts > "
                + "breaks out > dormant again); sneak + right-click: it dies."), false);
        return spawned;
    }

    /** Runs one of the controls ({@code /dross showcase golems shell|core|break|die|walk|idle}). */
    public static int control(CommandContext<CommandSourceStack> context, Control control)
    {
        CommandSourceStack source = context.getSource();
        List<CinderColossus> golems = nearbyGolems(source.getLevel(), source.getPosition());
        if (golems.isEmpty())
        {
            source.sendFailure(Component.literal("There are no showcase golems within " + REMOVE_RADIUS
                    + " blocks. Spawn them with /dross showcase golems."));
            return 0;
        }
        for (CinderColossus golem : golems)
        {
            control.action.accept(golem);
        }
        int count = golems.size();
        source.sendSuccess(() -> Component.literal(count + " showcase golem" + (count == 1 ? "" : "s") + ": " + control.doing), false);
        return count;
    }

    /** A chat line of clickable buttons, one per control. */
    private static Component controls()
    {
        MutableComponent line = Component.literal("Controls: ");
        String[][] buttons = {
                {"Dormant", "dormant", "Stage 1: the dormant rock shell, the volcano smoking"},
                {"Erupt!", "erupt", "Halfway through stage 1: the volcano erupts and lava pours over the shell"},
                {"Erupted", "erupted", "Straight to the erupted shell"},
                {"Break shell", "break", "Stage 2: the shell breaks off"},
                {"Lava core", "core", "Straight to the lava core form, its volcano erupting"},
                {"Die", "die", "Cool into obsidian statues"},
                {"Walk", "walk", "Knuckle-walk on the spot"},
                {"Stand", "idle", "Stand still"},
        };
        for (String[] b : buttons)
        {
            String command = "/dross showcase golems " + b[1];
            line.append(Component.literal("[" + b[0] + "]").withStyle(style -> style
                    .withColor(DrossColors.GOLEM_SHOWCASE_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(b[2] + "\n" + command)))));
            line.append(" ");
        }
        return line;
    }

    private static List<CinderColossus> nearbyGolems(ServerLevel level, Vec3 position)
    {
        AABB area = new AABB(position, position).inflate(REMOVE_RADIUS);
        return level.getEntities(ModEntities.CINDER_COLOSSUS.get(), area, CinderColossus::isShowcase);
    }

    /** Removes the showcase golems loaded within {@link #REMOVE_RADIUS} blocks, and says how many there were. */
    private static int removeOldGolems(ServerLevel level, Vec3 position)
    {
        List<CinderColossus> old = nearbyGolems(level, position);
        for (CinderColossus golem : old)
        {
            golem.discard();
        }
        return old.size();
    }

    /**
     * Clears the area from your spot out to a little behind the row (air for {@link #CLEAR_HEIGHT} blocks above the
     * ground) and makes its floor flat and solid: solid ground stays as it is, gaps get grass with dirt below.
     */
    private static void clearFloor(ServerLevel level, BlockPos origin, Direction look, Direction right, int fromRight, int toRight, int depth)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int ahead = 0; ahead <= depth; ahead++)
        {
            for (int side = fromRight; side <= toRight; side++)
            {
                BlockPos ground = origin.relative(look, ahead).relative(right, side);
                for (int dy = 1; dy <= CLEAR_HEIGHT; dy++)
                {
                    if (!level.getBlockState(pos.setWithOffset(ground, 0, dy, 0)).isAir())
                    {
                        level.setBlock(pos, air, FLOOR_FLAGS);
                    }
                }
                if (Showcases.isSolidGround(level, ground))
                {
                    continue;
                }
                level.setBlock(ground, grass, FLOOR_FLAGS);
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

    private GolemShowcase() {}
}
