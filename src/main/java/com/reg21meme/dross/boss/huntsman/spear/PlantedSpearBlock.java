package com.reg21meme.dross.boss.huntsman.spear;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.level.pathfinder.PathComputationType;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * The Planted Moonsilver Spear ({@code dross:planted_spear}): a spear standing upright, its butt in the ground (the
 * hunter villages' spears: they replaced the lightning rods that stood in for them, the user's request of October 22,
 * 2026). Decoration: it's drawn as the spear's own 3D model at 0.7 of its size, 1.75 blocks tall, and drops itself.
 */
public class PlantedSpearBlock extends Block
{
    private static final double LO = 8.0D - SpearTuning.PLANTED_HALF_WIDTH;
    private static final double HI = 8.0D + SpearTuning.PLANTED_HALF_WIDTH;
    private static final VoxelShape SHAPE = Block.box(LO, 0.0D, LO, HI, 16.0D, HI);

    public PlantedSpearBlock()
    {
        super(BlockBehaviour.Properties.of().mapColor(MapColor.METAL).strength(0.5F).sound(SoundType.LANTERN)
                .noOcclusion().pushReaction(PushReaction.DESTROY));
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public boolean isPathfindable(BlockState state, BlockGetter level, BlockPos pos, PathComputationType type)
    {
        return false;
    }
}
