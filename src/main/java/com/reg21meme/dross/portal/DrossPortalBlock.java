package com.reg21meme.dross.portal;

import com.reg21meme.dross.registry.ModParticles;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The lit, orange Dross portal. Works like the vanilla nether portal block:
 * no collision, translucent, can't be mined, light level 11, ambient sound and swirl particles,
 * and it disappears when its frame is no longer complete.
 * <p>
 * Standing in it does NOT use vanilla's {@code Entity#handleInsidePortal} (that would start a Nether trip).
 * Instead it tells {@link DrossPortalTravel}, which runs its own wait timer and cooldown.
 */
public class DrossPortalBlock extends Block
{
    public static final EnumProperty<Direction.Axis> AXIS = BlockStateProperties.HORIZONTAL_AXIS;
    protected static final VoxelShape X_AXIS_AABB = Block.box(0.0D, 0.0D, 6.0D, 16.0D, 16.0D, 10.0D);
    protected static final VoxelShape Z_AXIS_AABB = Block.box(6.0D, 0.0D, 0.0D, 10.0D, 16.0D, 16.0D);

    public DrossPortalBlock()
    {
        super(BlockBehaviour.Properties.of()
                .noCollission()
                .strength(-1.0F, 3600000.0F)
                .sound(SoundType.GLASS)
                .lightLevel(state -> 11)
                .noLootTable()
                .pushReaction(PushReaction.BLOCK));
        this.registerDefaultState(this.stateDefinition.any().setValue(AXIS, Direction.Axis.X));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return state.getValue(AXIS) == Direction.Axis.Z ? Z_AXIS_AABB : X_AXIS_AABB;
    }

    /** Same rule as the nether portal: if the frame around it is broken, the portal turns into air. */
    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighborState,
                                  LevelAccessor level, BlockPos pos, BlockPos neighborPos)
    {
        Direction.Axis updateAxis = direction.getAxis();
        Direction.Axis portalAxis = state.getValue(AXIS);
        boolean sideways = portalAxis != updateAxis && updateAxis.isHorizontal();
        if (!sideways && !neighborState.is(this) && !new DrossPortalShape(level, pos, portalAxis).isComplete())
        {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighborState, level, pos, neighborPos);
    }

    @Override
    public void entityInside(BlockState state, Level level, BlockPos pos, Entity entity)
    {
        if (!level.isClientSide && entity.canChangeDimensions())
        {
            DrossPortalTravel.onEntityInside(entity, pos);
        }
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (random.nextInt(100) == 0)
        {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    SoundEvents.PORTAL_AMBIENT, SoundSource.BLOCKS, 0.5F, random.nextFloat() * 0.4F + 0.8F, false);
        }

        for (int i = 0; i < 4; ++i)
        {
            double x = pos.getX() + random.nextDouble();
            double y = pos.getY() + random.nextDouble();
            double z = pos.getZ() + random.nextDouble();
            double dx = (random.nextFloat() - 0.5D) * 0.5D;
            double dy = (random.nextFloat() - 0.5D) * 0.5D;
            double dz = (random.nextFloat() - 0.5D) * 0.5D;
            int side = random.nextInt(2) * 2 - 1;
            if (!level.getBlockState(pos.west()).is(this) && !level.getBlockState(pos.east()).is(this))
            {
                x = pos.getX() + 0.5D + 0.25D * side;
                dx = random.nextFloat() * 2.0F * side;
            }
            else
            {
                z = pos.getZ() + 0.5D + 0.25D * side;
                dz = random.nextFloat() * 2.0F * side;
            }
            level.addParticle(ModParticles.DROSS_PORTAL.get(), x, y, z, dx, dy, dz);
        }
    }

    @Override
    public ItemStack getCloneItemStack(BlockGetter level, BlockPos pos, BlockState state)
    {
        return ItemStack.EMPTY;
    }

    @Override
    public BlockState rotate(BlockState state, Rotation rotation)
    {
        if (rotation == Rotation.CLOCKWISE_90 || rotation == Rotation.COUNTERCLOCKWISE_90)
        {
            return state.setValue(AXIS, state.getValue(AXIS) == Direction.Axis.X ? Direction.Axis.Z : Direction.Axis.X);
        }
        return state;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(AXIS);
    }
}
