package com.reg21meme.dross.dimension.block;

import com.reg21meme.dross.registry.ModBlocks;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/**
 * Silvermoss: the Silverwood Forest's floor, silver-blue moss over the ground.
 *
 * <ul>
 *   <li>It's in {@code #minecraft:dirt}, so saplings, flowers and ferns grow on it.</li>
 *   <li>Like grass: a hoe tills it into farmland, a shovel flattens it into a dirt path.</li>
 *   <li>Bone meal on it sprouts Glimmer Ferns, and now and then a Wispbloom, on the silvermoss around it (like bone
 *       meal on grass sprouts grass and flowers).</li>
 *   <li>It drops itself (mine it with a shovel or a hoe).</li>
 * </ul>
 */
public class SilvermossBlock extends Block implements BonemealableBlock
{
    /** How many tries one bone meal makes at sprouting a plant (vanilla grass: 128; fewer makes a smaller patch). */
    private static final int BONEMEAL_TRIES = 64;
    /** Every this many tries, a try may wander one block further from where the bone meal was used. */
    private static final int TRIES_PER_STEP = 16;
    /** 1 in this many sprouted plants is a Wispbloom; the rest are Glimmer Ferns. */
    private static final int WISPBLOOM_ONE_IN = 8;

    public SilvermossBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    @Nullable
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction toolAction, boolean simulate)
    {
        if (context.getItemInHand().canPerformAction(toolAction))
        {
            if (toolAction == ToolActions.HOE_TILL && context.getClickedFace() != Direction.DOWN
                    && context.getLevel().getBlockState(context.getClickedPos().above()).isAir())
            {
                return Blocks.FARMLAND.defaultBlockState();
            }
            if (toolAction == ToolActions.SHOVEL_FLATTEN)
            {
                return Blocks.DIRT_PATH.defaultBlockState(); // the shovel itself checks for air above
            }
        }
        return super.getToolModifiedState(state, context, toolAction, simulate);
    }

    // ---------------------------------------------------------------------------------------------
    // Bone meal
    // ---------------------------------------------------------------------------------------------

    @Override
    public boolean isValidBonemealTarget(LevelReader level, BlockPos pos, BlockState state, boolean isClient)
    {
        return level.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state)
    {
        return true;
    }

    /** Like vanilla grass: each try wanders a few random steps over the silvermoss and plants in the first air it ends on. */
    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state)
    {
        BlockPos start = pos.above();
        tries:
        for (int i = 0; i < BONEMEAL_TRIES; i++)
        {
            BlockPos at = start;
            for (int step = 0; step < i / TRIES_PER_STEP; step++)
            {
                at = at.offset(random.nextInt(3) - 1, (random.nextInt(3) - 1) * random.nextInt(3) / 2, random.nextInt(3) - 1);
                if (!level.getBlockState(at.below()).is(this) || level.getBlockState(at).isCollisionShapeFullBlock(level, at))
                {
                    continue tries;
                }
            }
            if (!level.getBlockState(at).isAir())
            {
                continue;
            }
            Block plant = random.nextInt(WISPBLOOM_ONE_IN) == 0 ? ModBlocks.WISPBLOOM.get() : ModBlocks.GLIMMER_FERN.get();
            BlockState plantState = plant.defaultBlockState();
            if (plantState.canSurvive(level, at))
            {
                level.setBlock(at, plantState, Block.UPDATE_ALL);
            }
        }
    }
}
