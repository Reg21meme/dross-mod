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
import net.minecraft.world.phys.Vec3;

/** Small helpers shared by the showcase commands ({@link ShrineShowcase}, {@link SkinShowcase}). */
final class Showcases
{
    /** A sign has four lines of text. */
    private static final int SIGN_LINES = 4;

    /**
     * The ground level for a showcase: the block under the command's position (the one whose top is at, or just
     * under, the feet, so slabs, paths and carpets work too). In the air, the first solid ground below instead.
     * Uses the command's position, so it works for players, the server console ({@code execute positioned ...})
     * and command blocks alike.
     */
    static int groundY(ServerLevel level, Vec3 position)
    {
        BlockPos underFeet = BlockPos.containing(position.x, position.y - 0.5D, position.z);
        BlockPos.MutableBlockPos pos = underFeet.mutable();
        while (!isSolidGround(level, pos) && pos.getY() > level.getMinBuildHeight())
        {
            pos.move(Direction.DOWN);
        }
        return isSolidGround(level, pos) ? pos.getY() : underFeet.getY();
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
            text = text.setMessage(i, Component.literal(lines[i]));
        }
        sign.setText(text, true);
        sign.setWaxed(true);
        // Make sure players see the text straight away.
        sign.setChanged();
        level.sendBlockUpdated(pos, state, state, Block.UPDATE_CLIENTS);
        return true;
    }

    private Showcases() {}
}
