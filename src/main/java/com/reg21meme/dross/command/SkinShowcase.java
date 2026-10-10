package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.villager.DrossTrader;
import com.reg21meme.dross.villager.TraderSkins;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
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
 * {@code /dross showcase skins} (test tool, cheats only): spawns one frozen Dross trader per candidate skin (the
 * villager area's {@code villager.TraderSkins}) in a row in front of you, so they can be compared and one picked.
 * <p>
 * The layout, seen from where the command was run:
 * <ul>
 *   <li>one row across your view, centred on you, No. 1 on the left to No. 10 on the right;</li>
 *   <li>each trader stands on a polished deepslate pedestal {@link #AHEAD} blocks ahead, {@link #SPACING} blocks
 *       from the next, all facing back toward you;</li>
 *   <li>a waxed oak sign on the front of each pedestal: number, name and mood;</li>
 *   <li>the space around them is cleared and the floor made flat and solid, so you can walk round them and see
 *       their backs.</li>
 * </ul>
 * Old showcase traders within {@link #REMOVE_RADIUS} blocks are removed first, so running it again replaces the row.
 * The real trader is never removed (he isn't a showcase trader). Showcase traders don't move, can't be hurt except by
 * {@code /kill}, and right-clicking one names its skin (the villager area's {@code DrossTrader.spawnShowcase}).
 * <p>
 * It uses the command's position, rotation and dimension, so it also works from the server console and command
 * blocks, for example {@code execute in minecraft:overworld positioned 0 -60 0 rotated 0 0 run dross showcase skins}.
 */
public final class SkinShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Blocks from where you stand to the row of traders. */
    private static final int AHEAD = 6;
    /** Blocks from one trader to the next, centre to centre. That leaves 2 clear blocks between pedestals, which the Wayfarer's hat brim needs. */
    private static final int SPACING = 3;
    /** Old showcase traders within this many blocks of you are removed before the new row is spawned. */
    private static final int REMOVE_RADIUS = 64;
    /** The cleared, flat floor reaches this many blocks past each end of the row... */
    private static final int FLOOR_SIDE_MARGIN = 3;
    /** ...and this many blocks behind it, so you can walk round the traders and see their backs. */
    private static final int FLOOR_BEHIND = 3;
    /** Air is cleared this many blocks above the floor: the pedestal, then room for a trader and his hat. */
    private static final int CLEAR_HEIGHT = 5;
    /** A dip in the floor is filled with dirt at most this many blocks deep. */
    private static final int MAX_FILL = 4;
    /** Floor blocks don't poke their neighbours (no redstone going off), but players still see every change. */
    private static final int FLOOR_FLAGS = Block.UPDATE_CLIENTS;

    /** Runs {@code /dross showcase skins}. */
    public static int spawnSkins(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        List<TraderSkins.TraderSkin> skins = TraderSkins.ALL;
        if (skins.isEmpty())
        {
            source.sendFailure(Component.literal("There are no trader skins to show."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y); // where the command looks: north, south, east or west
        Direction facing = look.getOpposite();  // every trader faces back toward you
        Direction right = look.getClockWise();  // No. 1 on the left, the last one on the right
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));

        // 1. Old showcase traders nearby go first. The real trader is never a showcase trader, so he stays.
        int removed = removeOldTraders(level, position);

        // 2. Where each pedestal goes: one row across your view, centred on you.
        int firstOffset = -((skins.size() - 1) * SPACING) / 2; // blocks to your right of No. 1 (negative = to your left)
        int lastOffset = firstOffset + (skins.size() - 1) * SPACING;
        List<BlockPos> pedestals = new ArrayList<>();
        for (int i = 0; i < skins.size(); i++)
        {
            pedestals.add(origin.relative(look, AHEAD).relative(right, firstOffset + i * SPACING).above());
        }

        // 3. A clear, flat floor from your spot to a little behind the row, a little past both ends.
        clearFloor(level, origin, look, right, firstOffset - FLOOR_SIDE_MARGIN, lastOffset + FLOOR_SIDE_MARGIN, AHEAD + FLOOR_BEHIND);

        // 4. Pedestals, signs and traders. His feet go on top of the pedestal: 2 blocks above the ground.
        BlockState pedestalBlock = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
        float yaw = facing.toYRot();
        List<TraderSkins.TraderSkin> failed = new ArrayList<>();
        for (int i = 0; i < skins.size(); i++)
        {
            TraderSkins.TraderSkin skin = skins.get(i);
            BlockPos pedestal = pedestals.get(i);
            level.setBlock(pedestal, pedestalBlock, Block.UPDATE_ALL);
            BlockPos sign = pedestal.relative(facing);
            if (!Showcases.placeWallSign(level, sign, facing, "No. " + skin.number(), skin.name(), skin.mood()))
            {
                LOGGER.warn("Skin showcase: couldn't put the sign for No. {} at {}", skin.number(), sign.toShortString());
            }
            if (DrossTrader.spawnShowcase(level, Vec3.atBottomCenterOf(pedestal.above()), yaw, skin) == null)
            {
                failed.add(skin);
                LOGGER.warn("Skin showcase: couldn't spawn the trader for No. {} ({})", skin.number(), skin.name());
            }
        }

        int spawned = skins.size() - failed.size();
        BlockPos firstFeet = pedestals.get(0).above();
        BlockPos lastFeet = pedestals.get(pedestals.size() - 1).above();
        LOGGER.info("Skin showcase: spawned {} of {} showcase traders for {} in {}, feet from {} (No. {}) to {} (No. {}), "
                        + "facing {}; removed {} old showcase trader(s)",
                spawned, skins.size(), source.getTextName(), level.dimension().location(), firstFeet.toShortString(),
                skins.get(0).number(), lastFeet.toShortString(), skins.get(skins.size() - 1).number(), facing.getName(), removed);

        String removedNote = removed == 0 ? "no old showcase traders nearby"
                : "removed " + removed + " old showcase trader" + (removed == 1 ? "" : "s");
        String summary = (failed.isEmpty() ? "Spawned " + spawned : "Spawned " + spawned + " of " + skins.size())
                + " trader skins in a row in front of you, No. " + skins.get(0).number() + " on the left (" + removedNote + "):";
        source.sendSuccess(() -> Component.literal(summary), true);
        for (TraderSkins.TraderSkin skin : skins)
        {
            String line = skin.number() + ". " + skin.name() + " (" + skin.mood() + ")";
            if (failed.contains(skin))
            {
                source.sendFailure(Component.literal(line + ": couldn't spawn, see the game log"));
            }
            else
            {
                source.sendSuccess(() -> Component.literal(line), false);
            }
        }
        return spawned;
    }

    /** Removes the showcase traders loaded within {@link #REMOVE_RADIUS} blocks, and says how many there were. */
    private static int removeOldTraders(ServerLevel level, Vec3 position)
    {
        AABB area = new AABB(position, position).inflate(REMOVE_RADIUS);
        List<DrossTrader> old = level.getEntities(ModEntities.TRADER.get(), area, DrossTrader::isShowcase);
        for (DrossTrader trader : old)
        {
            trader.discard();
        }
        return old.size();
    }

    /**
     * Clears the area from your spot out to a little behind the row (air for {@link #CLEAR_HEIGHT} blocks above the
     * ground) and makes its floor flat and solid: solid ground stays as it is, gaps get grass with dirt filling
     * the dip below.
     *
     * @param fromRight the leftmost column, in blocks to your right (negative = to your left)
     * @param toRight   the rightmost column
     * @param depth     how many blocks ahead it reaches (0 is your own spot)
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

    private SkinShowcase() {}
}
