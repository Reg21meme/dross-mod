package com.reg21meme.dross.dimension.block;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Silverwood leaves: deep blue leaves that decay like any leaves (they count the distance to the nearest
 * {@code #minecraft:logs} block), burn like oak leaves, and now and then let a tiny glowing blue mote drift down
 * from their underside (client side only, see {@link #animateTick}).
 */
public class SilverwoodLeavesBlock extends LeavesBlock
{
    // Minecraft calls animateTick on many random blocks around the player every tick (about 1,300 calls a tick),
    // so in a dense forest a lot of those land on leaves. Only leaves with open air (or a plant) below them can give
    // off a mote, and then only 1 call in MOTE_CHANCE does: in a dense forest that's a handful of motes a second.
    /** 1 in this many animateTick calls on a leaf block with open space below gives off a mote. */
    private static final int MOTE_CHANCE = 80;
    /** Size of the mote (1.0 = redstone dust size; smaller ones also fade sooner). */
    private static final float MOTE_SIZE = 0.8F;
    /** The mote's push downwards when it appears (dust particles slow down quickly, so it just drifts). */
    private static final double MOTE_FALL_SPEED = 0.6D;

    /** The mote starts pale blue and fades to the Dross's electric blue as it drifts. */
    private static final DustColorTransitionOptions MOTE = new DustColorTransitionOptions(
            DrossColors.vector(DrossColors.SILVERWOOD_LEAF_MOTE), DrossColors.vector(DrossColors.SILVERWOOD_LEAF_MOTE_FADE), MOTE_SIZE);

    public SilverwoodLeavesBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        super.animateTick(state, level, pos, random); // vanilla's rain drips
        if (random.nextInt(MOTE_CHANCE) != 0)
        {
            return;
        }
        BlockPos below = pos.below();
        BlockState belowState = level.getBlockState(below);
        if (isFaceFull(belowState.getCollisionShape(level, below), Direction.UP))
        {
            return; // more leaves (or something solid) underneath: the mote would be hidden
        }
        double x = pos.getX() + random.nextDouble();
        double y = pos.getY() - 0.05D;
        double z = pos.getZ() + random.nextDouble();
        level.addParticle(MOTE, x, y, z, 0.0D, -MOTE_FALL_SPEED, 0.0D);
    }

    @Override
    public boolean isFlammable(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return true;
    }

    @Override
    public int getFlammability(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.LEAVES_BURN;
    }

    @Override
    public int getFireSpreadSpeed(BlockState state, BlockGetter level, BlockPos pos, Direction direction)
    {
        return Flammability.LEAVES_SPREAD;
    }
}
