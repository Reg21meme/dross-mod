package com.reg21meme.dross.dimension.block;

import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;

/**
 * A silverwood log or wood block (and their stripped versions): placed along an axis like any log, it burns like oak,
 * and an axe strips it (right-click) into its {@code stripped} version, keeping the same axis.
 * The stripped versions pass {@code null} for {@code stripped}: there's nothing left to strip.
 */
public class SilverwoodLogBlock extends RotatedPillarBlock
{
    @Nullable
    private final Supplier<? extends Block> stripped;

    public SilverwoodLogBlock(Properties properties, @Nullable Supplier<? extends Block> stripped)
    {
        super(properties);
        this.stripped = stripped;
    }

    @Override
    @Nullable
    public BlockState getToolModifiedState(BlockState state, UseOnContext context, ToolAction toolAction, boolean simulate)
    {
        if (toolAction == ToolActions.AXE_STRIP && stripped != null && context.getItemInHand().canPerformAction(ToolActions.AXE_STRIP))
        {
            return stripped.get().defaultBlockState().setValue(AXIS, state.getValue(AXIS));
        }
        return super.getToolModifiedState(state, context, toolAction, simulate);
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.LOG_BURN;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.LOG_SPREAD;
    }
}
