package com.reg21meme.dross.boss.huntsman.village;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * A village's fire (the Frostfolk's courtyards, their cooking fires, the halls): a campfire to look at (its own model,
 * the same light and crackle), with no block entity and only now and then a wisp of smoke. A village of them was a
 * campfire's column of smoke from each of 40-odd fires at once (measured October 24, 2026: over a thousand smoke
 * particles, a good share of the frames). The soul kind is the softer light the village tool puts near clear ice.
 */
public class VillageFireBlock extends HorizontalDirectionalBlock
{
    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    private static final VoxelShape SHAPE = Block.box(0.0D, 0.0D, 0.0D, 16.0D, 7.0D, 16.0D);
    /** On one of its few display ticks (Minecraft gives a block near you one every couple of seconds): a wisp of smoke
     * this often, a crackle this often. */
    private static final int SMOKE_ONE_IN = 1;
    private static final int CRACKLE_ONE_IN = 4;

    private final boolean soul;

    public VillageFireBlock(boolean soul, Properties properties)
    {
        super(properties);
        this.soul = soul;
        this.registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, true));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder)
    {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context)
    {
        return this.defaultBlockState().setValue(FACING, context.getHorizontalDirection());
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context)
    {
        return SHAPE;
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (!state.getValue(LIT))
        {
            return;
        }
        if (random.nextInt(CRACKLE_ONE_IN) == 0)
        {
            level.playLocalSound(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, SoundEvents.CAMPFIRE_CRACKLE,
                    SoundSource.BLOCKS, 0.5F + random.nextFloat(), random.nextFloat() * 0.7F + 0.6F, false);
        }
        if (random.nextInt(SMOKE_ONE_IN) == 0)
        {
            for (int i = 0; i < 2; i++)
            {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, pos.getX() + 0.35D + random.nextDouble() * 0.3D,
                        pos.getY() + 0.6D, pos.getZ() + 0.35D + random.nextDouble() * 0.3D, 0.0D, 0.06D, 0.0D);
            }
        }
        if (!this.soul && random.nextInt(5) == 0)
        {
            level.addParticle(ParticleTypes.LAVA, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D,
                    random.nextFloat() / 2.0F, 5.0E-5D, random.nextFloat() / 2.0F);
        }
    }
}
