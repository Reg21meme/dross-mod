package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.huntsman.AlphaMoose;
import com.reg21meme.dross.boss.huntsman.HuntAnims;
import com.reg21meme.dross.boss.huntsman.HuntMob;
import com.reg21meme.dross.boss.huntsman.Huntsman;
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
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * {@code /dross showcase boss_designs huntsman [<animation>]} (test tool, cheats only): the Huntsman and his Alpha Moose
 * on show (the boss area's {@code boss.huntsman}), frozen, in a row ahead of you facing you, each with a sign: the two
 * together as the Wild Hunt (he rides it), the Huntsman on foot, and the Alpha Moose alone.
 * <p>
 * With an animation's name, every one of them within {@link #RANGE} blocks that has it plays it (the riding Huntsman's
 * start with {@code ride_}); the moose carrying him gallops with him ({@code run} with {@code ride_gallop}, either way
 * round). The chat lists every animation as a button. Running it again replaces them.
 */
public final class HuntsmanShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String COMMAND = "/dross showcase boss_designs huntsman";
    /** How far ahead the row stands, and the blocks between them. */
    static final int AHEAD = 10;
    static final int GAP = 9;
    /** Controls and old exhibits reach this far (X and Z). */
    static final int RANGE = 64;

    private HuntsmanShowcase() {}

    /** Every animation name, in the chat's order: riding, on foot, the Alpha Moose. */
    public static List<String> names()
    {
        Set<String> out = new LinkedHashSet<>();
        HuntAnims.RIDING.forEach(a -> out.add(a.name()));
        HuntAnims.HUNTSMAN.forEach(a -> out.add(a.name()));
        HuntAnims.ALPHA_MOOSE.forEach(a -> out.add(a.name()));
        return new ArrayList<>(out);
    }

    /** Runs {@code /dross showcase boss_designs huntsman}: spawns the three. */
    public static int spawn(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));
        int removed = removeOld(level, position);
        List<HuntMob> spawned = spawnRow(level, origin, look);
        LOGGER.info("Huntsman showcase: {} exhibits for {} at {} looking {} (removed {} old)", spawned.size(),
                source.getTextName(), origin.toShortString(), look.getName(), removed);
        source.sendSuccess(() -> Component.literal("The Huntsman on show ahead of you: riding his Alpha Moose (the "
                + "Wild Hunt), on foot, and the Alpha Moose alone. Click an animation to play it:"), true);
        source.sendSuccess(() -> buttons("Riding", HuntAnims.RIDING), false);
        source.sendSuccess(() -> buttons("On foot", HuntAnims.HUNTSMAN), false);
        source.sendSuccess(() -> buttons("Alpha Moose", HuntAnims.ALPHA_MOOSE), false);
        return spawned.size();
    }

    /** The three in a row, facing back towards {@code origin}, each with a sign on a packed ice post in front. */
    public static List<HuntMob> spawnRow(ServerLevel level, BlockPos origin, Direction look)
    {
        Direction right = look.getClockWise();
        float yaw = look.getOpposite().toYRot();
        List<HuntMob> out = new ArrayList<>();
        String[][] signs = {{"The Huntsman", "and his", "Alpha Moose", "(the Wild Hunt)"},
                {"The Huntsman", "on foot", "", ""}, {"The Alpha Moose", "alone", "", ""}};
        for (int i = 0; i < 3; i++)
        {
            BlockPos ground = origin.relative(look, AHEAD).relative(right, (i - 1) * GAP);
            Vec3 feet = Vec3.atBottomCenterOf(ground.above());
            if (i == 0 || i == 2)
            {
                AlphaMoose moose = create(level, ModEntities.ALPHA_MOOSE.get(), feet, yaw);
                if (moose != null)
                {
                    out.add(moose);
                    if (i == 0)
                    {
                        Huntsman rider = create(level, ModEntities.HUNTSMAN.get(), feet, yaw);
                        if (rider != null)
                        {
                            rider.startRiding(moose, true);
                            out.add(rider);
                        }
                    }
                }
            }
            else
            {
                Huntsman h = create(level, ModEntities.HUNTSMAN.get(), feet, yaw);
                if (h != null)
                {
                    out.add(h);
                }
            }
            BlockPos post = ground.relative(look.getOpposite(), 5).above();
            level.setBlock(post, Blocks.PACKED_ICE.defaultBlockState(), Block.UPDATE_ALL);
            Showcases.placeWallSign(level, post.relative(look.getOpposite()), look.getOpposite(), signs[i]);
        }
        return out;
    }

    private static <T extends HuntMob> T create(ServerLevel level, EntityType<T> type, Vec3 feet, float yaw)
    {
        T mob = type.create(level);
        if (mob == null)
        {
            return null;
        }
        mob.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.yHeadRotO = yaw;
        mob.yBodyRotO = yaw;
        level.addFreshEntity(mob);
        return mob;
    }

    /** Runs {@code /dross showcase boss_designs huntsman <animation>}. */
    public static int play(CommandContext<CommandSourceStack> context, String name)
    {
        CommandSourceStack source = context.getSource();
        int played = play(source.getLevel(), source.getPosition(), name);
        if (played == 0)
        {
            source.sendFailure(Component.literal("No Huntsman or Alpha Moose on show near you has an animation called \""
                    + name + "\" (run " + COMMAND + " first)."));
            return 0;
        }
        source.sendSuccess(() -> Component.literal("Playing " + name + " (" + played + ")"), false);
        return played;
    }

    /** Every exhibit within {@link #RANGE} that has {@code name} plays it; a moose carrying the Huntsman gallops with
     * him. Returns how many play it. */
    public static int play(ServerLevel level, Vec3 near, String name)
    {
        return play(nearby(level, near), name);
    }

    /** Each of {@code mobs} that has {@code name} plays it (a moose carrying the Huntsman gallops with him). */
    static int play(List<HuntMob> mobs, String name)
    {
        int played = 0;
        for (HuntMob mob : mobs)
        {
            if (mob.play(name))
            {
                played++;
                if (mob instanceof Huntsman h && h.getVehicle() instanceof AlphaMoose moose && name.equals("ride_gallop"))
                {
                    moose.play("run");
                }
                if (mob instanceof AlphaMoose moose && moose.getFirstPassenger() instanceof Huntsman h)
                {
                    h.play(name.equals("run") || name.equals("charge") ? "ride_gallop" : "ride_idle");
                }
            }
        }
        return played;
    }

    private static Component buttons(String label, List<HuntAnims.Anim> anims)
    {
        MutableComponent line = Component.literal(label + ":");
        for (HuntAnims.Anim a : anims)
        {
            String command = COMMAND + " " + a.name();
            line.append(" ").append(Component.literal("[" + a.name() + "]").withStyle(style -> style
                    .withColor(DrossColors.HUNTER_VILLAGE_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))));
        }
        return line;
    }

    static List<HuntMob> nearby(ServerLevel level, Vec3 position)
    {
        AABB area = Showcases.columnsAround(level, position, RANGE);
        return level.getEntitiesOfClass(HuntMob.class, area, mob -> true);
    }

    private static int removeOld(ServerLevel level, Vec3 position)
    {
        List<HuntMob> old = nearby(level, position);
        old.forEach(m -> m.discard());
        return old.size();
    }
}
