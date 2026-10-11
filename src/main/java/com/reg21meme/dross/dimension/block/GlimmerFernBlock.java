package com.reg21meme.dross.dimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Glimmer Fern: a silvery fern with blue-tipped fronds. It behaves like vanilla grass and ferns: it grows on
 * {@code #minecraft:dirt} (silvermoss included), you walk through it, placing a block on it replaces it, it burns,
 * and only shears pick it up (loot table).
 */
public class GlimmerFernBlock extends BushBlock
{
    /** The outline: the same size as vanilla grass (12 pixels wide, 13 tall). */
    private static final VoxelShape SHAPE = Block.box(2.0D, 0.0D, 2.0D, 14.0D, 13.0D, 14.0D);

    public GlimmerFernBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        Vec3 offset = state.getOffset(level, pos); // it's nudged randomly, like grass: the outline follows
        return SHAPE.move(offset.x, offset.y, offset.z);
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.PLANT_BURN;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.PLANT_SPREAD;
    }
}
