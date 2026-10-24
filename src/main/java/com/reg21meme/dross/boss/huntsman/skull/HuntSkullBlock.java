package com.reg21meme.dross.boss.huntsman.skull;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
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
import net.minecraftforge.registries.ForgeRegistries;

import javax.annotation.Nullable;
import java.util.EnumMap;
import java.util.Map;

/**
 * A mounted skull (the user, October 22, 2026: bare skulls "like the Ice and Fire dragon heads", in the hunter villages
 * in place of the skeleton skulls on fences, and players can get them): the Alpha Moose's, a bull moose's or a
 * Rimehorn's ({@code dross:alpha_moose_skull}, {@code moose_skull}, {@code rimehorn_skull}). Placed on top of a block it
 * stands there (on a post) facing whoever placed it; placed against the side of a block it hangs on that wall facing
 * out. Its 3D model is baked (tools/huntsman/skulls.py: Forge's OBJ loader), so it needs no block entity; its antlers
 * and horns reach well past its block, but only the skull itself can be touched: its hitbox is fitted to the skull, as
 * much of it as lies in its block ({@link HuntSkullShapes}, made with the models; a block's hitbox can't reach outside
 * it). Standing, it lies level on its jaw (the user, October 23, 2026: they "sit weird on poles").
 */
public class HuntSkullBlock extends Block
{
    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty WALL = BooleanProperty.create("wall");

    /** A hitbox for a skull without one of its own in {@link HuntSkullShapes} (facing north: on the floor, on a wall). */
    private static final double[][] FALLBACK = {{3.0D, 0.0D, 3.0D, 13.0D, 9.0D, 13.0D}, {3.0D, 3.0D, 7.0D, 13.0D, 12.0D, 16.0D}};

    /** Its hitboxes, worked out the first time they're wanted (its id is known only once it's registered). */
    private Map<Direction, VoxelShape> floors;
    private Map<Direction, VoxelShape> walls;

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
        if (this.floors == null)
        {
            ResourceLocation id = ForgeRegistries.BLOCKS.getKey(this);
            double[][] boxes = HuntSkullShapes.BOXES.getOrDefault(id == null ? "" : id.getPath(), FALLBACK);
            Map<Direction, VoxelShape> f = new EnumMap<>(Direction.class);
            Map<Direction, VoxelShape> w = new EnumMap<>(Direction.class);
            for (Direction d : Direction.Plane.HORIZONTAL)
            {
                f.put(d, turned(boxes[0], d));
                w.put(d, turned(boxes[1], d));
            }
            this.walls = w;
            this.floors = f;
        }
        return (state.getValue(WALL) ? this.walls : this.floors).get(state.getValue(FACING));
    }

    /** A north-facing box (pixels) turned to face {@code facing}, the way its blockstate turns its model (a quarter
     * turn clockwise seen from above for each step from north: east, south, west). */
    static VoxelShape turned(double[] b, Direction facing)
    {
        double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
        for (int k = 0; k < (facing.get2DDataValue() + 2) % 4; k++)
        {
            double nx0 = 16.0D - z1, nx1 = 16.0D - z0, nz0 = x0, nz1 = x1;
            x0 = nx0;
            x1 = nx1;
            z0 = nz0;
            z1 = nz1;
        }
        return Block.box(x0, b[1], z0, x1, b[4], z1);
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
