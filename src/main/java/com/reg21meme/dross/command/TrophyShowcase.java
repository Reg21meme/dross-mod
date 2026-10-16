package com.reg21meme.dross.command;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophies;
import com.reg21meme.dross.boss.trophy.ColossusTrophies;
import com.reg21meme.dross.boss.trophy.TrophyStyle;
import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * {@code /dross showcase trophies <boss>} (test tool, cheats only): places every trophy design of a boss in a row in
 * front of you, so they can be compared and one picked:
 * <ul>
 *   <li>{@code /dross showcase trophies colossus} places the 5 Cinder Colossus trophies (the boss area's
 *       {@code boss.trophy.ColossusTrophies});</li>
 *   <li>{@code /dross showcase trophies ratking} places the 5 Rat King trophies (the boss area's
 *       {@code boss.ratking.trophy.RatKingTrophies}).</li>
 * </ul>
 * A future boss adds one literal in {@code DrossCommand} and one small method here that turns its designs into
 * {@link Trophy} entries for {@link #placeRow}.
 * <p>
 * The layout, seen from where the command was run:
 * <ul>
 *   <li>one row across your view, centred on you, No. 1 on the left; each trophy {@link #AHEAD} blocks ahead and
 *       {@link #SPACING} blocks from the next;</li>
 *   <li>each trophy stands on a chiseled polished blackstone pedestal (a small post, so the figure is at eye level),
 *       facing you;</li>
 *   <li>a waxed oak sign on the front of each pedestal: "No. N", the design's name and its look;</li>
 *   <li>the space around them is cleared and the floor made flat and solid, so you can walk round them.</li>
 * </ul>
 * Running it again replaces the row: old showcase trophies within {@link #REMOVE_RADIUS} blocks (X and Z), at any
 * height (on steep ground an old row can stand far above or below the new one), are removed with their pedestals and
 * signs first, and the cleared area takes anything else away. A showcase trophy is one on a showcase pedestal with
 * its "No. N" sign on the pedestal's front; trophies placed by players are left alone.
 * <p>
 * It uses the command's position, rotation and dimension, so it also works from the server console and command
 * blocks, for example
 * {@code execute in minecraft:overworld positioned 0 -60 0 rotated 0 0 run dross showcase trophies colossus}.
 */
public final class TrophyShowcase
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Blocks from where you stand to the row of trophies. */
    private static final int AHEAD = 4;
    /** Blocks from one trophy to the next, centre to centre (two clear blocks between pedestals). */
    private static final int SPACING = 3;
    /** Old showcase trophies within this many blocks (X and Z) of you, at any height, go before the new row is placed. */
    private static final int REMOVE_RADIUS = 24;
    /** The cleared, flat floor reaches this many blocks past each end of the row... */
    private static final int FLOOR_SIDE_MARGIN = 2;
    /** ...and this many blocks behind it, so you can walk round the trophies and see their backs. */
    private static final int FLOOR_BEHIND = 2;
    /** Air is cleared this many blocks above the floor: the pedestal, the trophy and headroom. */
    private static final int CLEAR_HEIGHT = 4;
    /** A dip in the floor is filled with dirt at most this many blocks deep. */
    private static final int MAX_FILL = 4;
    /** What each trophy stands on. Also how old showcase trophies are recognised (with their sign). */
    private static final Block PEDESTAL = Blocks.CHISELED_POLISHED_BLACKSTONE;
    /** How each sign's first line starts ("No. 3"). Also how old showcase trophies are recognised. */
    private static final String SIGN_NUMBER = "No. ";

    /**
     * One trophy to place.
     *
     * @param number its design number (the sign's first line)
     * @param name   its name (translatable, so each player reads it in their own language)
     * @param look   a short word or two about its look, for the sign's third line
     * @param block  the block for this design, the figure facing the given direction
     */
    public record Trophy(int number, Component name, String look, Function<Direction, BlockState> block) {}

    /** Runs {@code /dross showcase trophies colossus}: the 5 Cinder Colossus trophy designs. */
    public static int placeColossusTrophies(CommandContext<CommandSourceStack> context)
    {
        List<Trophy> trophies = new ArrayList<>();
        for (ColossusTrophies.Design design : ColossusTrophies.DESIGNS)
        {
            trophies.add(new Trophy(design.number(), design.displayName(), lookOf(design.style()), design::blockState));
        }
        return placeRow(context.getSource(), "Cinder Colossus", trophies,
                state -> state.is(ModBlocks.COLOSSUS_TROPHY.get()));
    }

    /** Runs {@code /dross showcase trophies ratking}: the 5 Rat King trophy designs. */
    public static int placeRatKingTrophies(CommandContext<CommandSourceStack> context)
    {
        List<Trophy> trophies = new ArrayList<>();
        for (RatKingTrophies.Design design : RatKingTrophies.DESIGNS)
        {
            trophies.add(new Trophy(design.number(), design.displayName(), design.look(), design::blockState));
        }
        return placeRow(context.getSource(), "Rat King", trophies, state -> state.is(ModBlocks.RAT_KING_TROPHY.get()));
    }

    /** The sign's word for a Colossus trophy's look. */
    private static String lookOf(TrophyStyle style)
    {
        return switch (style)
        {
            case DORMANT -> "Dormant";
            case ERUPTED -> "Erupted";
            case CRACKING -> "Cracking";
            case CORE -> "Lava core";
        };
    }

    /**
     * Places a boss's trophies in a row in front of the command's position (see the class comment).
     *
     * @param boss     the boss's name, for chat and the log
     * @param trophies its designs, No. 1 first
     * @param isTrophy whether a block is one of this boss's trophies (to find an old row)
     * @return how many trophies were placed
     */
    public static int placeRow(CommandSourceStack source, String boss, List<Trophy> trophies, Predicate<BlockState> isTrophy)
    {
        if (trophies.isEmpty())
        {
            source.sendFailure(Component.literal("The " + boss + " has no trophy designs to show."));
            return 0;
        }

        ServerLevel level = source.getLevel();
        Vec3 position = source.getPosition();
        Direction look = Direction.fromYRot(source.getRotation().y); // where the command looks: north, south, east or west
        Direction facing = look.getOpposite();  // every trophy faces back toward you
        Direction right = look.getClockWise();  // No. 1 on the left, the last one on the right
        BlockPos origin = new BlockPos(Mth.floor(position.x), Showcases.groundY(level, position), Mth.floor(position.z));

        // 1. An old row nearby goes first (trophy, pedestal and sign), so running it again replaces it.
        int removed = removeOldTrophies(level, origin, isTrophy);

        // 2. A clear, flat floor from your spot to a little behind the row, a little past both ends.
        int firstOffset = -((trophies.size() - 1) * SPACING) / 2; // blocks to your right of No. 1 (negative = to your left)
        int lastOffset = firstOffset + (trophies.size() - 1) * SPACING;
        Showcases.clearFloor(level, origin, look, right, firstOffset - FLOOR_SIDE_MARGIN, lastOffset + FLOOR_SIDE_MARGIN,
                AHEAD + FLOOR_BEHIND, CLEAR_HEIGHT, MAX_FILL);

        // 3. Pedestals, signs and trophies. The trophy stands on top of its pedestal: 2 blocks above the ground.
        BlockState pedestalBlock = PEDESTAL.defaultBlockState();
        List<Trophy> failed = new ArrayList<>();
        for (int i = 0; i < trophies.size(); i++)
        {
            Trophy trophy = trophies.get(i);
            BlockPos pedestal = origin.relative(look, AHEAD).relative(right, firstOffset + i * SPACING).above();
            level.setBlock(pedestal, pedestalBlock, Block.UPDATE_ALL);
            BlockPos sign = pedestal.relative(facing);
            if (!Showcases.placeWallSign(level, sign, facing,
                    Component.literal(SIGN_NUMBER + trophy.number()), trophy.name(), Component.literal(trophy.look())))
            {
                LOGGER.warn("Trophy showcase: couldn't put the sign for {} No. {} at {}", boss, trophy.number(), sign.toShortString());
            }
            BlockPos at = pedestal.above();
            BlockState state = trophy.block().apply(facing);
            level.setBlock(at, state, Block.UPDATE_ALL);
            if (!level.getBlockState(at).equals(state))
            {
                failed.add(trophy);
                LOGGER.warn("Trophy showcase: couldn't place {} No. {} at {}", boss, trophy.number(), at.toShortString());
            }
        }

        int placed = trophies.size() - failed.size();
        BlockPos first = origin.relative(look, AHEAD).relative(right, firstOffset).above(2);
        BlockPos last = origin.relative(look, AHEAD).relative(right, lastOffset).above(2);
        LOGGER.info("Trophy showcase: placed {} of {} {} trophies for {} in {}, from {} (No. {}) to {} (No. {}), facing {}; "
                        + "removed {} old showcase trophy(s)", placed, trophies.size(), boss, source.getTextName(),
                level.dimension().location(), first.toShortString(), trophies.get(0).number(), last.toShortString(),
                trophies.get(trophies.size() - 1).number(), facing.getName(), removed);

        String removedNote = removed == 0 ? "no old showcase trophies nearby"
                : "removed " + removed + " old showcase troph" + (removed == 1 ? "y" : "ies");
        String summary = "Placed " + (failed.isEmpty() ? String.valueOf(placed) : placed + " of " + trophies.size())
                + " " + boss + " trophies in a row in front of you, No. " + trophies.get(0).number() + " on the left ("
                + removedNote + "):";
        source.sendSuccess(() -> Component.literal(summary), true);
        for (Trophy trophy : trophies)
        {
            Component line = Component.literal(trophy.number() + ". ").append(trophy.name())
                    .append(" (" + trophy.look().toLowerCase(Locale.ROOT) + ")");
            if (failed.contains(trophy))
            {
                source.sendFailure(Component.empty().append(line).append(": couldn't place it, see the game log"));
            }
            else
            {
                source.sendSuccess(() -> line, false);
            }
        }
        return placed;
    }

    /**
     * Removes the old showcase trophies near {@code origin} (see {@link #findOldTrophies}), each with its pedestal and
     * the sign on the pedestal's front.
     *
     * @return how many trophies were removed
     */
    private static int removeOldTrophies(ServerLevel level, BlockPos origin, Predicate<BlockState> isTrophy)
    {
        List<BlockPos> found = findOldTrophies(level, origin, isTrophy);
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos trophy : found)
        {
            BlockPos pedestal = trophy.below();
            // The sign first (it hangs on the pedestal), then the trophy (it stands on it), then the pedestal.
            level.setBlock(signOf(level, trophy), air, Block.UPDATE_ALL);
            level.setBlock(trophy, air, Block.UPDATE_ALL);
            level.setBlock(pedestal, air, Block.UPDATE_ALL);
        }
        return found.size();
    }

    /**
     * Finds the old showcase trophies within {@link #REMOVE_RADIUS} blocks (X and Z) of {@code origin}, at <b>any
     * height</b>: on a steep hillside an old row run from a few blocks away can stand far above or below the new one.
     * A showcase trophy stands on a showcase {@link #PEDESTAL} with the showcase's sign on the pedestal's front (a wall
     * sign whose first line starts with {@link #SIGN_NUMBER}), so a trophy a player put on such a block for display
     * is left alone. Only loaded chunks are looked at, and only the parts of them that hold a trophy at all (quick).
     */
    private static List<BlockPos> findOldTrophies(ServerLevel level, BlockPos origin, Predicate<BlockState> isTrophy)
    {
        List<BlockPos> found = new ArrayList<>();
        int minX = origin.getX() - REMOVE_RADIUS;
        int maxX = origin.getX() + REMOVE_RADIUS;
        int minZ = origin.getZ() - REMOVE_RADIUS;
        int maxZ = origin.getZ() + REMOVE_RADIUS;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int chunkX = SectionPos.blockToSectionCoord(minX); chunkX <= SectionPos.blockToSectionCoord(maxX); chunkX++)
        {
            for (int chunkZ = SectionPos.blockToSectionCoord(minZ); chunkZ <= SectionPos.blockToSectionCoord(maxZ); chunkZ++)
            {
                LevelChunk chunk = level.getChunkSource().getChunkNow(chunkX, chunkZ);
                if (chunk == null)
                {
                    continue; // not loaded
                }
                int fromX = Math.max(minX, SectionPos.sectionToBlockCoord(chunkX));
                int toX = Math.min(maxX, SectionPos.sectionToBlockCoord(chunkX, 15));
                int fromZ = Math.max(minZ, SectionPos.sectionToBlockCoord(chunkZ));
                int toZ = Math.min(maxZ, SectionPos.sectionToBlockCoord(chunkZ, 15));
                LevelChunkSection[] sections = chunk.getSections();
                for (int index = 0; index < sections.length; index++)
                {
                    LevelChunkSection section = sections[index];
                    // A section's palette lists every kind of block in it: most hold no trophy and are skipped.
                    if (section.hasOnlyAir() || !section.maybeHas(isTrophy))
                    {
                        continue;
                    }
                    int bottomY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(index));
                    for (int x = fromX; x <= toX; x++)
                    {
                        for (int z = fromZ; z <= toZ; z++)
                        {
                            for (int y = 0; y < SectionPos.SECTION_SIZE; y++)
                            {
                                BlockState state = section.getBlockState(x & 15, y, z & 15);
                                if (isTrophy.test(state) && isShowcaseTrophy(level, pos.set(x, bottomY + y, z), state))
                                {
                                    found.add(pos.immutable());
                                }
                            }
                        }
                    }
                }
            }
        }
        return found;
    }

    /** A trophy on a showcase {@link #PEDESTAL}, with the showcase's sign ("No. N") on the pedestal's front. */
    private static boolean isShowcaseTrophy(ServerLevel level, BlockPos trophy, BlockState state)
    {
        if (!state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) || !level.getBlockState(trophy.below()).is(PEDESTAL))
        {
            return false;
        }
        BlockPos sign = signOf(level, trophy);
        BlockState signState = level.getBlockState(sign);
        return signState.getBlock() instanceof WallSignBlock
                && signState.getValue(WallSignBlock.FACING) == state.getValue(BlockStateProperties.HORIZONTAL_FACING)
                && level.getBlockEntity(sign) instanceof SignBlockEntity text
                && text.getFrontText().getMessage(0, false).getString().startsWith(SIGN_NUMBER);
    }

    /** Where a showcase trophy's sign hangs: on the front of its pedestal, the side the trophy faces. */
    private static BlockPos signOf(ServerLevel level, BlockPos trophy)
    {
        return trophy.below().relative(level.getBlockState(trophy).getValue(BlockStateProperties.HORIZONTAL_FACING));
    }

    private TrophyShowcase() {}
}
