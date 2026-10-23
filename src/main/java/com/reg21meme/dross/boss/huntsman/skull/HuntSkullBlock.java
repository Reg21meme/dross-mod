package com.reg21meme.dross.boss.huntsman.skull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * A mounted skull (the user, October 22, 2026: bare skulls "like the Ice and Fire dragon heads", in the hunter villages
 * in place of the skeleton skulls on fences, and players can get them): the Alpha Moose's, a bull moose's or a
 * Rimehorn's ({@code dross:alpha_moose_skull}, {@code moose_skull}, {@code rimehorn_skull}). Placed on top of a block it
 * stands there (on a post) facing whoever placed it; placed against the side of a block it hangs on that wall facing
 * out. Its 3D model is baked (tools/huntsman/skulls.py: Forge's OBJ loader), so it needs no block entity; its antlers
 * and horns reach well past its block, but only the skull itself can be touched.
 */
public class HuntSkullBlock extends Block
{
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WALL = BooleanProperty.create("wall");

    private static final VoxelShape FLOOR = Block.box(3.0D, 0.0D, 3.0D, 13.0D, 9.0D, 13.0D);
    /** On a wall facing each way: against the wall behind it (north-facing: the wall is to its south). */
    private static final Map<Direction, VoxelShape> WALLS = new EnumMap<>(Map.of(
            Direction.NORTH, Block.box(3.0D, 3.0D, 7.0D, 13.0D, 12.0D, 16.0D),
            Direction.SOUTH, Block.box(3.0D, 3.0D, 0.0D, 13.0D, 12.0D, 9.0D),
            Direction.EAST, Block.box(0.0D, 3.0D, 3.0D, 9.0D, 12.0D, 13.0D),
            Direction.WEST, Block.box(7.0D, 3.0D, 3.0D, 16.0D, 12.0D, 13.0D)));

    public HuntSkullBlock()
    {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.SAND).strength(1.0F).sound(SoundType.BONE_BLOCK)
                .noOcclusion().pushReaction(PushReaction.DESTROY));
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(WALL, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, WALL);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal())
        {
            return this.defaultBlockState().setValue(FACING, face).setValue(WALL, true);
        }
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite()).setValue(WALL, false);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(WALL) ? WALLS.get(state.getValue(FACING)) : FLOOR;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type)
    {
        return false;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation)
    {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @SuppressWarnings("deprecation")
    @Override
    public BlockState mirror(BlockState state, Mirror mirror)
    {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }
}
