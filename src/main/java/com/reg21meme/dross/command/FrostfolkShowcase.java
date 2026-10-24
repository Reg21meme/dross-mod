package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.frostfolk.FolkRole;
import com.reg21meme.dross.boss.frostfolk.Frostfolk;
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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * {@code /dross showcase frostfolk} (October 23, 2026; the user: "make them interactable ... a unique voice that is nice
 * ... just edit the showcase so I can see"): the boss area's Frostfolk ({@link Frostfolk}, one of each
 * {@link FolkRole}), live but frozen, in two rows ahead of you, facing you, on a snow floor, the back row a step up so
 * both rows show: the five warriors in front, the five workers and the elder behind; a packed ice post and a sign in
 * front of each (its name, warrior, worker or elder, its health). Right-click one and it talks to you (a line in chat,
 * its voice); {@code /dross showcase frostfolk <control>} (the chat's buttons) plays an animation on every one near.
 * Running it again replaces them (old ones within {@value #REMOVE_RADIUS} blocks removed, the statues of the first
 * hunter designs too).
 */
public final class FrostfolkShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();
    static final String COMMAND = "/dross showcase frostfolk";
    /** Blocks between two villagers in a row. */
    static final int SPACING = 3;
    /** The first row's distance ahead of you, and the second's. */
    static final int FIRST_ROW = 7;
    static final int SECOND_ROW = 12;
    /** The widest row (the workers and the elder). */
    static final int WIDEST = 6;
    /** Air cleared over the floor, and how deep a dip under it is filled. */
    static final int CLEAR_ABOVE = 5;
    static final int MAX_FILL = 6;
    /** Old ones this near (X and Z) are removed first. */
    static final int REMOVE_RADIUS = 48;

    private FrostfolkShowcase() {}

    /** The rows: the warriors in front, the workers and the elder behind. */
    static List<List<FolkRole>> rows()
    {
        List<FolkRole> front = new ArrayList<>();
        List<FolkRole> back = new ArrayList<>();
        for (FolkRole role : FolkRole.values())
        {
            (role.kind == FolkRole.Kind.WARRIOR ? front : back).add(role);
        }
        return List.of(front, back);
    }

    public static int run(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y);
        Direction right = look.getClockWise();
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));

        int removed = removeOld(level, position);
        int half = (WIDEST - 1) * SPACING / 2 + 3;
        Showcases.clearFloor(level, origin, look, right, -half, half, SECOND_ROW + 3, CLEAR_ABOVE + 1, MAX_FILL);
        BlockState snow = Blocks.SNOW_BLOCK.defaultBlockState();
        BlockState post = Blocks.PACKED_ICE.defaultBlockState();
        for (int ahead = 1; ahead <= SECOND_ROW + 3; ahead++)
        {
            for (int side = -half; side <= half; side++)
            {
                BlockPos ground = origin.relative(look, ahead).relative(right, side);
                level.setBlock(ground, snow, Block.UPDATE_CLIENTS);
                if (ahead >= SECOND_ROW - 2)
                {
                    level.setBlock(ground.above(), snow, Block.UPDATE_CLIENTS);     // the back row's step
                }
            }
        }

        int spawned = 0;
        int total = 0;
        List<List<FolkRole>> rows = rows();
        for (int row = 0; row < rows.size(); row++)
        {
            List<FolkRole> roles = rows.get(row);
            for (int column = 0; column < roles.size(); column++)
            {
                FolkRole role = roles.get(column);
                total++;
                int ahead = row == 0 ? FIRST_ROW : SECOND_ROW;
                int side = Math.round(column * SPACING - (roles.size() - 1) * SPACING / 2.0F);
                BlockPos ground = origin.relative(look, ahead).relative(right, side).above(row);
                Vec3 feet = Vec3.atBottomCenterOf(ground.above());
                if (Frostfolk.spawnShowcase(level, role, feet, look.getOpposite().toYRot()) != null)
                {
                    spawned++;
                }
                else
                {
                    LOGGER.warn("Hunter showcase: couldn't spawn the {}", role.key);
                }
                // the post and its sign, two blocks in front of him, the sign facing you
                BlockPos postPos = ground.relative(look.getOpposite(), 2).above();
                level.setBlock(postPos, post, Block.UPDATE_ALL);
                Showcases.placeWallSign(level, postPos.relative(look.getOpposite()), look.getOpposite(),
                        Component.translatable("entity.dross." + role.typeName()),
                        Component.literal(switch (role.kind)
                        {
                            case WARRIOR -> "Warrior";
                            case WORKER -> "Worker";
                            case ELDER -> "The one to talk to";
                        }),
                        Component.literal((int) role.health + " health"));
            }
        }
        int made = spawned;
        int all = total;
        int gone = removed;
        LOGGER.info("Hunter showcase: spawned {} of {} for {} at {} looking {}; removed {} old", made, all,
                source.getTextName(), origin.toShortString(), look.getName(), gone);
        source.sendSuccess(() -> Component.translatable("dross.frostfolk.showcase.done", made, all,
                gone == 0 ? "" : "; removed " + gone + " old"), true);
        source.sendSuccess(FrostfolkShowcase::controls, false);
        return made;
    }

    /** Runs {@code /dross showcase frostfolk <control>} ({@link Frostfolk#SHOW}). */
    public static int control(CommandContext<CommandSourceStack> context, String control)
    {
        CommandSourceStack source = context.getSource();
        List<Frostfolk> near = nearby(source.getLevel(), source.getPosition());
        if (near.isEmpty())
        {
            source.sendFailure(Component.literal("There are no showcase Frostfolk within " + REMOVE_RADIUS
                    + " blocks. Spawn them with " + COMMAND + "."));
            return 0;
        }
        near.forEach(folk -> folk.playShowcase(control));
        int count = near.size();
        source.sendSuccess(() -> Component.literal(count + " of the Frostfolk: " + control
                + (control.equals("horn") ? " (only the horn-blower has one)" : "")), false);
        return count;
    }

    private static Component controls()
    {
        MutableComponent line = Component.literal("Animations: ");
        for (String control : Frostfolk.SHOW)
        {
            String command = COMMAND + " " + control;
            String label = control.substring(0, 1).toUpperCase() + control.substring(1);
            line.append(Component.literal("[" + label + "]").withStyle(style -> style
                    .withColor(DrossColors.RIMEFALL_SHOWCASE_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command)))));
            line.append(" ");
        }
        return line;
    }

    /** Every showcase Frostfolk loaded within {@link #REMOVE_RADIUS} blocks (X and Z, at any height). */
    static List<Frostfolk> nearby(ServerLevel level, Vec3 position)
    {
        return level.getEntitiesOfClass(Frostfolk.class, Showcases.columnsAround(level, position, REMOVE_RADIUS),
                Frostfolk::isShowcase);
    }

    /** Removes the showcase villagers near, and the statues of the first hunter designs (Nos. 10A-10L). */
    private static int removeOld(ServerLevel level, Vec3 position)
    {
        int removed = 0;
        AABB area = Showcases.columnsAround(level, position, REMOVE_RADIUS);
        List<Entity> old = new ArrayList<>(nearby(level, position));
        old.addAll(level.getEntities(ModEntities.RIMEFALL_CANDIDATE.get(), area,
                c -> RimefallDesigns.HUNTERS.contains(c.getDesign())));
        for (Entity entity : old)
        {
            entity.discard();
            removed++;
        }
        return removed;
    }

    /** (For the tests) The callings, in the order they stand. */
    static List<FolkRole> order()
    {
        return rows().stream().flatMap(List::stream).toList();
    }
}
