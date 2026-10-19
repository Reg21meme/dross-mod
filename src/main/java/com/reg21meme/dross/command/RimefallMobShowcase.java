package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.rimefall.Moose;
import com.reg21meme.dross.boss.rimefall.PondSkater;
import com.reg21meme.dross.boss.rimefall.RimefallAnimal;
import com.reg21meme.dross.boss.rimefall.RimefallAnims;
import com.reg21meme.dross.boss.rimefall.Rimehorn;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /dross showcase rimefall_mobs} (test tool, cheats only): Rimefall's animals in a row in front of you, frozen,
 * each on a packed-ice pedestal with a sign, facing you: a male, a female and a calf Rimehorn, a bull, a cow and a calf
 * moose, and a Pond Skater (the boss area's {@code Rimehorn}, {@code Moose}, {@code PondSkater}).
 * <p>
 * Controls (every showcase animal within {@link #REMOVE_RADIUS} blocks; also clickable in chat): {@code idle},
 * {@code walk}, {@code run}, {@code charge}, {@code eat}, {@code attack}. Each plays its own version: "attack" is the
 * Rimehorn's horn flick, the moose's ram and the Pond Skater's stab; "charge" the Rimehorn's rush and the moose's
 * charge; "run" the Pond Skater's skating. Right-clicking one plays its next animation. Running the command again
 * replaces the row. They can't be hurt except by {@code /kill}.
 */
public final class RimefallMobShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Blocks from you to the row (the Rimehorns' heads reach about 5 blocks towards you). */
    private static final int AHEAD = 10;
    private static final int REMOVE_RADIUS = 80;
    private static final int FLOOR_BEHIND = 7;
    private static final int FLOOR_SIDE_MARGIN = 4;
    private static final int CLEAR_HEIGHT = 7;
    private static final int MAX_FILL = 4;
    private static final BlockState PEDESTAL = Blocks.PACKED_ICE.defaultBlockState();
    /** Blocks from each animal's pedestal towards you to its sign's post. */
    private static final int POST_AHEAD = 6;

    public static final String COMMAND = "/dross showcase rimefall_mobs";

    /** One animal in the row: its sign's lines, how much room it takes along the row, and how it's made. */
    private record Slot(String name, String kind, int width, Spawner spawn)
    {
    }

    @FunctionalInterface
    private interface Spawner
    {
        Mob apply(ServerLevel level, Vec3 feet, float yaw);
    }

    private static final List<Slot> SLOTS = List.of(
            new Slot("Rimehorn", "male", 7, (level, feet, yaw) -> Rimehorn.spawnShowcase(level, feet, yaw, true, false)),
            new Slot("Rimehorn", "female", 7, (level, feet, yaw) -> Rimehorn.spawnShowcase(level, feet, yaw, false, false)),
            new Slot("Rimehorn", "calf", 4, (level, feet, yaw) -> Rimehorn.spawnShowcase(level, feet, yaw, false, true)),
            new Slot("Moose", "bull", 6, (level, feet, yaw) -> Moose.spawnShowcase(level, feet, yaw, true, false)),
            new Slot("Moose", "cow", 5, (level, feet, yaw) -> Moose.spawnShowcase(level, feet, yaw, false, false)),
            new Slot("Moose", "calf", 4, (level, feet, yaw) -> Moose.spawnShowcase(level, feet, yaw, false, true)),
            new Slot("Pond Skater", "", 14, (level, feet, yaw) -> PondSkater.spawnShowcase(level, feet, yaw)));

    /** The controls; each animal plays the first of its names it has. */
    public enum Control
    {
        IDLE("idle", "Idle", "Standing about: breathing, looking round", "idle"),
        WALK("walk", "Walk", "Walking on the spot (the Pond Skater: its stepping gait off the ice)", "walk"),
        RUN("run", "Run", "Running on the spot: the Rimehorn's gallop, the moose's trot, the Pond Skater skating", "run", "skate"),
        CHARGE("charge", "Charge", "The Rimehorn's rush and the moose's charge (head down)", "rush", "charge", "skate"),
        EAT("eat", "Eat", "Grazing: the Rimehorn sweeps the snow aside, the moose kneels", "eat", "idle"),
        ATTACK("attack", "Attack", "The Rimehorn's horn flick, the moose's ram, the Pond Skater's stab", "flick", "attack");

        private final String word;
        private final String button;
        private final String hover;
        private final String[] names;

        Control(String word, String button, String hover, String... names)
        {
            this.word = word;
            this.button = button;
            this.hover = hover;
            this.names = names;
        }

        public String word()
        {
            return this.word;
        }

        public String command()
        {
            return COMMAND + " " + this.word;
        }

        /** The animation this control plays on {@code animal}: the first of its names the animal has. */
        String pick(RimefallAnimal animal)
        {
            for (String name : this.names)
            {
                if (RimefallAnims.named(animal.animations(), name) != null)
                {
                    return name;
                }
            }
            return "idle";
        }
    }

    /** Runs {@code /dross showcase rimefall_mobs}. */
    public static int spawnAnimals(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction facing = look.getOpposite();
        Direction right = look.getClockWise();
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));

        List<Integer> offsets = new ArrayList<>();
        int x = 0;
        for (int i = 0; i < SLOTS.size(); i++)
        {
            if (i > 0)
            {
                x += (SLOTS.get(i - 1).width + SLOTS.get(i).width) / 2;
            }
            offsets.add(x);
        }
        int shift = -x / 2;
        int removed = removeOld(level, position);
        Showcases.clearFloor(level, origin, look, right, shift - FLOOR_SIDE_MARGIN - 7, shift + x + FLOOR_SIDE_MARGIN + 7,
                AHEAD + FLOOR_BEHIND, CLEAR_HEIGHT, MAX_FILL);

        float yaw = facing.toYRot();
        int spawned = 0;
        for (int i = 0; i < SLOTS.size(); i++)
        {
            Slot slot = SLOTS.get(i);
            BlockPos pedestal = origin.relative(look, AHEAD).relative(right, shift + offsets.get(i)).above();
            level.setBlock(pedestal, PEDESTAL, Block.UPDATE_ALL);
            // A packed-ice post in front of it (clear of a Rimehorn's horn) with its sign facing you.
            BlockPos post = pedestal.relative(facing, POST_AHEAD);
            level.setBlock(post, PEDESTAL, Block.UPDATE_ALL);
            BlockPos sign = post.relative(facing);
            if (!Showcases.placeWallSign(level, sign, facing, slot.name, slot.kind, "Rimefall", "animal"))
            {
                LOGGER.warn("Rimefall animals showcase: couldn't put the sign for {} {} at {}", slot.name, slot.kind,
                        sign.toShortString());
            }
            if (slot.spawn.apply(level, Vec3.atBottomCenterOf(pedestal.above()), yaw) != null)
            {
                spawned++;
            }
            else
            {
                LOGGER.warn("Rimefall animals showcase: couldn't spawn the {} {}", slot.name, slot.kind);
            }
        }
        int count = spawned;
        LOGGER.info("Rimefall animals showcase: spawned {} of {} for {}, removed {} old", count, SLOTS.size(),
                source.getTextName(), removed);
        source.sendSuccess(() -> Component.literal("Spawned Rimefall's animals in a row in front of you: a male, a female "
                + "and a calf Rimehorn, a bull, a cow and a calf moose, and a Pond Skater"
                + (removed == 0 ? "" : " (removed " + removed + " old ones)") + "."), true);
        source.sendSuccess(RimefallMobShowcase::controls, false);
        source.sendSuccess(() -> Component.literal("Or right-click one to play its next animation."), false);
        return count;
    }

    /** Runs {@code /dross showcase rimefall_mobs <control>}. */
    public static int control(CommandContext<CommandSourceStack> context, Control control)
    {
        CommandSourceStack source = context.getSource();
        List<Entity> animals = nearby(source.getLevel(), source.getPosition());
        if (animals.isEmpty())
        {
            source.sendFailure(Component.literal("There are no showcase animals within " + REMOVE_RADIUS
                    + " blocks. Spawn them with " + COMMAND + "."));
            return 0;
        }
        for (Entity entity : animals)
        {
            RimefallAnimal animal = (RimefallAnimal) entity;
            animal.playShowcase(control.pick(animal));
        }
        int count = animals.size();
        source.sendSuccess(() -> Component.literal(count + " showcase animal" + (count == 1 ? "" : "s") + ": "
                + control.button.toLowerCase()), false);
        return count;
    }

    private static Component controls()
    {
        MutableComponent line = Component.literal("Animations: ");
        for (Control control : Control.values())
        {
            String command = control.command();
            line.append(Component.literal("[" + control.button + "]").withStyle(style -> style
                    .withColor(DrossColors.RIMEFALL_SHOWCASE_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(control.hover + "\n" + command)))));
            line.append(" ");
        }
        return line;
    }

    /** Every showcase animal loaded within {@link #REMOVE_RADIUS} blocks (X and Z, at any height). */
    private static List<Entity> nearby(ServerLevel level, Vec3 position)
    {
        return level.getEntities((Entity) null, Showcases.columnsAround(level, position, REMOVE_RADIUS),
                e -> e instanceof RimefallAnimal animal && animal.isShowcase());
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

    private RimefallMobShowcase() {}
}
