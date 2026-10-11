package com.reg21meme.dross.command;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.WallSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Small helpers shared by the showcase commands ({@link ShrineShowcase}, {@link SkinShowcase}, {@link GolemShowcase},
 * {@link TrophyShowcase}).
 */
final class Showcases
{
    /** A sign has four lines of text. */
    private static final int SIGN_LINES = 4;
    /** {@link #clearFloor} doesn't poke neighbours (no redstone going off), but players still see every change. */
    private static final int FLOOR_FLAGS = Block.UPDATE_CLIENTS;

    /**
     * The ground level for a showcase: the block under the command's position (the one whose top is at, or just
     * under, the feet, so slabs, paths and carpets work too). In the air, the first solid ground below instead.
     * Uses the command's position, so it works for players, the server console ({@code execute positioned ...})
     * and command blocks alike.
     */
    static int groundY(ServerLevel level, Vec3 position)
    {
        // Never below the bottom of the world (a position at the very bottom would otherwise build out of it).
        BlockPos underFeet = BlockPos.containing(position.x, Math.max(position.y - 0.5D, level.getMinBuildHeight()), position.z);
        BlockPos.MutableBlockPos pos = underFeet.mutable();
        while (!isSolidGround(level, pos) && pos.getY() > level.getMinBuildHeight())
        {
            pos.move(Direction.DOWN);
        }
        return isSolidGround(level, pos) ? pos.getY() : underFeet.getY();
    }

    /**
     * The box {@code radius} blocks round {@code position} (X and Z), from the bottom of the world to its top: where a
     * showcase looks for the showcase traders or golems of an earlier run. Every height, because on a steep hillside an
     * old row run from a few blocks away can stand far above or below the new one.
     */
    static AABB columnsAround(ServerLevel level, Vec3 position, int radius)
    {
        return new AABB(position.x - radius, level.getMinBuildHeight(), position.z - radius,
                position.x + radius, level.getMaxBuildHeight(), position.z + radius);
    }

    /** Something you can stand on: not air, not water or lava, and has a collision box. */
    static boolean isSolidGround(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return !state.isAir() && state.getFluidState().isEmpty() && !state.getCollisionShape(level, pos).isEmpty();
    }

    /**
     * A waxed oak wall sign at {@code pos}, hanging on the block behind it, its text facing {@code facing}, with up
     * to four lines. Waxed, so a click doesn't open the sign editor.
     *
     * @return false if it couldn't be placed (nothing solid to hang on)
     */
    static boolean placeWallSign(ServerLevel level, BlockPos pos, Direction facing, String... lines)
    {
        Component[] text = new Component[lines.length];
        for (int i = 0; i < lines.length; i++)
        {
            text[i] = Component.literal(lines[i]);
        }
        return placeWallSign(level, pos, facing, text);
    }

    /**
     * The same, with lines that can be translated (for example a lang key's name), so each player reads the sign in
     * their own language.
     *
     * @return false if it couldn't be placed (nothing solid to hang on)
     */
    static boolean placeWallSign(ServerLevel level, BlockPos pos, Direction facing, Component... lines)
    {
        BlockState state = Blocks.OAK_WALL_SIGN.defaultBlockState().setValue(WallSignBlock.FACING, facing);
        if (!state.canSurvive(level, pos))
        {
            return false;
        }
        level.setBlock(pos, state, Block.UPDATE_ALL);
        if (!(level.getBlockEntity(pos) instanceof SignBlockEntity sign))
        {
            return false;
        }
        SignText text = sign.getFrontText();
        for (int i = 0; i < Math.min(lines.length, SIGN_LINES); i++)
        {
            text = text.setMessage(i, lines[i]);
        }
        sign.setText(text, true);
        sign.setWaxed(true);
        // Make sure players see the text straight away.
        sign.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        return true;
    }

    /**
     * Clears a showcase's area, from the command's spot out to a little behind its row (air for {@code clearHeight}
     * blocks above the ground), and makes its floor flat and solid: solid ground stays as it is, gaps get grass with
     * dirt filling the dip below (at most {@code maxFill} blocks deep). Floor blocks don't poke their neighbours (no
     * redstone going off), but players still see every change.
     *
     * @param origin    the ground block under the command's spot
     * @param look      the way the command looks (the row is ahead)
     * @param right     the command's right
     * @param fromRight the leftmost column, in blocks to the right (negative = to the left)
     * @param toRight   the rightmost column
     * @param depth     how many blocks ahead it reaches (0 is the command's own spot)
     */
    static void clearFloor(ServerLevel level, BlockPos origin, Direction look, Direction right, int fromRight, int toRight,
                           int depth, int clearHeight, int maxFill)
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
                for (int dy = 1; dy <= clearHeight; dy++)
                {
                    if (!level.getBlockState(pos.setWithOffset(ground, 0, dy, 0)).isAir())
                    {
                        level.setBlock(pos, air, FLOOR_FLAGS);
                    }
                }
                if (isSolidGround(level, ground))
                {
                    continue;
                }
                level.setBlock(ground, grass, FLOOR_FLAGS);
                for (int dy = 1; dy <= maxFill; dy++)
                {
                    if (isSolidGround(level, pos.setWithOffset(ground, 0, -dy, 0)))
                    {
                        break;
                    }
                    level.setBlock(pos, dirt, FLOOR_FLAGS);
                }
            }
        }
    }

    private Showcases() {}
}
