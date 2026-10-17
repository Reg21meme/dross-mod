package com.reg21meme.dross.dimension.block;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Blight Vent: sludge-caked stone split by a crack glowing poison green (drawn full-bright by its model; its light level
 * is in its block properties), found at the Rat King's sewer: in the wall of its grand entrance's shaft, in the corners
 * of the pool room below and in the poisoned ground round it. It breathes spores: now and then a puff of poison-green
 * specks drifts out of each open side and up (so out of the shaft's opening at night).
 *
 * <p>It's harmless: the spores are only particles. Mine it with a pickaxe; it drops itself.
 */
public class BlightVentBlock extends Block
{
    // A block within 16 blocks of the player gets an animateTick call about every 2.5 seconds.
    /** 1 in this many animateTick calls breathes a puff of spores (about every 5 seconds per vent). */
    private static final int PUFF_CHANCE = 2;
    /** How many spores one puff lets out of each open side. */
    private static final int SPORES = 4;
    /** Size of a spore (1.0 = redstone dust size). */
    private static final float SPORE_SIZE = 0.8F;
    /** How fast they rise (as a particle's upward speed; dust moves at a tenth of it). */
    private static final double RISE = 1.4D;
    /** How far across a side the spores start (blocks from its middle). */
    private static final double SPREAD = 0.35D;

    private static final DustColorTransitionOptions SPORE = new DustColorTransitionOptions(
            DrossColors.vector(DrossColors.BLIGHT_VENT_SPORE), DrossColors.vector(DrossColors.BLIGHT_VENT_SPORE_FADE), SPORE_SIZE);

    public BlightVentBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (random.nextInt(PUFF_CHANCE) != 0)
        {
            return;
        }
        for (Direction side : Direction.values())
        {
            if (side == Direction.DOWN)
            {
                continue;
            }
            BlockPos next = pos.relative(side);
            BlockState there = level.getBlockState(next);
            if (there.isFaceSturdy(level, next, side.getOpposite()) || !there.getFluidState().isEmpty())
            {
                continue;   // covered (or under water): nothing comes out that side
            }
            double x = pos.getX() + 0.5D + side.getStepX() * 0.55D;
            double y = pos.getY() + 0.5D + side.getStepY() * 0.55D;
            double z = pos.getZ() + 0.5D + side.getStepZ() * 0.55D;
            for (int i = 0; i < SPORES; i++)
            {
                double ax = side.getStepX() == 0 ? spread(random) : 0.0D;
                double ay = side.getStepY() == 0 ? spread(random) : 0.0D;
                double az = side.getStepZ() == 0 ? spread(random) : 0.0D;
                level.addParticle(SPORE, x + ax, y + ay, z + az, side.getStepX() * 0.3D, RISE, side.getStepZ() * 0.3D);
            }
        }
    }

    private static double spread(RandomSource random)
    {
        return (random.nextDouble() * 2.0D - 1.0D) * SPREAD;
    }
}
