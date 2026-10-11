package com.reg21meme.dross.dimension.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.RootsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.PlantType;

/**
 * Scar Roots: twisted crimson roots with electric blue tips, where the Dross meets the Nether. Like vanilla's crimson
 * roots (it's a {@link RootsBlock}): you walk through them, placing a block replaces them, they drop themselves, and
 * they never burn. They grow on the ground in {@code #dross:scar_roots_grow_on} (nylium, netherrack, Scarred
 * Netherrack and soul soil; see {@link PlantGround}).
 */
public class ScarRootsBlock extends RootsBlock
{
    public ScarRootsBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    protected boolean mayPlaceOn(BlockState ground, BlockGetter level, BlockPos pos)
    {
        return ground.is(PlantGround.SCAR_ROOTS);
    }

    @Override
    public PlantType getPlantType(BlockGetter level, BlockPos pos)
    {
        return PlantGround.PLANT_TYPE;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        Vec3 offset = state.getOffset(level, pos); // nudged randomly like vanilla roots: the outline follows
        return SHAPE.move(offset.x, offset.y, offset.z);
    }
}
