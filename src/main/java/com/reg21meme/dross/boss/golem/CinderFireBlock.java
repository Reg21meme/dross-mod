package com.reg21meme.dross.boss.golem;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;

/**
 * {@code dross:cinder_fire}: the trail of fire a Cinder Colossus leaves behind its charge in the Lava Core stage
 * (see {@link LavaPools#placeFire}). It burns whoever walks into it like ordinary fire, but:
 * <ul>
 *   <li>it never spreads and never burns anything (it isn't Minecraft's fire, which does);</li>
 *   <li>it never lights a nether portal;</li>
 *   <li>it goes out by itself after {@link ColossusTuning#FIRE_TRAIL_MIN_TICKS} to
 *       {@link ColossusTuning#FIRE_TRAIL_MAX_TICKS} ticks: a scheduled block tick, saved with the chunk, with a random
 *       tick as a safety net in case that tick is ever lost.</li>
 * </ul>
 * Like any fire you can punch it out.
 */
public class CinderFireBlock extends BaseFireBlock
{
    /** Damage a tick to anything standing in it (vanilla fire's). */
    private static final float FIRE_DAMAGE = 1.0F;

    public CinderFireBlock()
    {
        super(BlockBehaviour.Properties.of()
                .mapColor(MapColor.FIRE)
                .replaceable()
                .noCollission()
                .instabreak()
                .randomTicks()
                .lightLevel(state -> 15)
                .sound(SoundType.WOOL)
                .pushReaction(PushReaction.DESTROY)
                .noLootTable(), FIRE_DAMAGE);
    }

    @Override
    protected boolean canBurn(BlockState state)
    {
        return true;
    }

    /** Unlike vanilla fire: no nether portal is ever lit, and it starts its own countdown. */
    @Override
    public void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston)
    {
        if (level.isClientSide || oldState.is(this))
        {
            return;
        }
        if (!state.canSurvive(level, pos))
        {
            level.removeBlock(pos, false);
            return;
        }
        int life = ColossusTuning.FIRE_TRAIL_MIN_TICKS
                + level.random.nextInt(ColossusTuning.FIRE_TRAIL_MAX_TICKS - ColossusTuning.FIRE_TRAIL_MIN_TICKS + 1);
        level.scheduleTick(pos, this, life);
    }

    @Override
    public void tick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
    }

    /** A safety net: if its countdown was ever lost, a random tick puts it out. */
    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random)
    {
        if (!level.getBlockTicks().hasScheduledTick(pos, this))
        {
            level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        }
    }

    /** It burns on top of solid ground only. */
    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos)
    {
        BlockPos below = pos.below();
        return level.getBlockState(below).isFaceSturdy(level, below, Direction.UP);
    }

    @Override
    public BlockState updateShape(BlockState state, Direction direction, BlockState neighbor, LevelAccessor level,
                                  BlockPos pos, BlockPos neighborPos)
    {
        return this.canSurvive(state, level, pos) ? state : Blocks.AIR.defaultBlockState();
    }

    @Override
    public boolean isBurning(BlockState state, net.minecraft.world.level.BlockGetter level, BlockPos pos)
    {
        return true;
    }
}
