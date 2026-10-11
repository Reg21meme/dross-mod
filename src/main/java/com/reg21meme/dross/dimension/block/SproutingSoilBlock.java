package com.reg21meme.dross.dimension.block;

import java.util.function.Supplier;
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
 * A ground cover over soil, like vanilla grass, mycelium or podzol: Silvermoss, Sporeloam and Hoarfrost. They're all
 * in {@code #minecraft:dirt}, so saplings and plants grow on them, and they drop themselves.
 *
 * <ul>
 *   <li>A shovel flattens it into a dirt path; a hoe tills it into farmland only if it's {@code tillable} (like
 *       grass; mycelium and podzol can't be tilled).</li>
 *   <li>If it has a {@code plant}, bone meal on it sprouts that plant on the same ground round about, and 1 in
 *       {@code rareOneIn} times the {@code rarePlant} instead (like bone meal on grass sprouts grass and flowers).
 *       Without a plant, bone meal does nothing on it.</li>
 * </ul>
 */
public class SproutingSoilBlock extends Block implements BonemealableBlock
{
    /** How many tries one bone meal makes at sprouting a plant (vanilla grass: 128; fewer makes a smaller patch). */
    private static final int BONEMEAL_TRIES = 64;
    /** Every this many tries, a try may wander one block further from where the bone meal was used. */
    private static final int TRIES_PER_STEP = 16;

    private final boolean tillable;
    @Nullable
    private final Supplier<? extends Block> plant;
    @Nullable
    private final Supplier<? extends Block> rarePlant;
    private final int rareOneIn;

    /**
     * @param tillable  a hoe tills it into farmland
     * @param plant     what bone meal sprouts on it (null: bone meal does nothing)
     * @param rarePlant what bone meal sprouts 1 in {@code rareOneIn} times instead (null: always {@code plant})
     */
    public SproutingSoilBlock(Properties properties, boolean tillable, @Nullable Supplier<? extends Block> plant,
            @Nullable Supplier<? extends Block> rarePlant, int rareOneIn)
    {
        super(properties);
        this.tillable = tillable;
        this.plant = plant;
        this.rarePlant = rarePlant;
        this.rareOneIn = Math.max(rareOneIn, 1);
    }

    @Override
    @Nullable
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction toolAction, boolean simulate)
    {
        if (context.getItemInHand().canPerformAction(toolAction))
        {
            if (tillable && toolAction == ToolActions.HOE_TILL && context.getClickedFace() != Direction.DOWN
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
        return plant != null && level.getBlockState(pos.above()).isAir();
    }

    @Override
    public boolean isBonemealSuccess(Level level, RandomSource random, BlockPos pos, BlockState state)
    {
        return plant != null;
    }

    /** Like vanilla grass: each try wanders a few random steps over this ground and plants in the first air it ends on. */
    @Override
    public void performBonemeal(ServerLevel level, RandomSource random, BlockPos pos, BlockState state)
    {
        if (plant == null)
        {
            return;
        }
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
            Block sprout = rarePlant != null && random.nextInt(rareOneIn) == 0 ? rarePlant.get() : plant.get();
            BlockState sproutState = sprout.defaultBlockState();
            if (sproutState.canSurvive(level, at))
            {
                level.setBlock(at, sproutState, Block.UPDATE_ALL);
            }
        }
    }
}
