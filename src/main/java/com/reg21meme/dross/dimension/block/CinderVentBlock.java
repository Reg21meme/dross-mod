package com.reg21meme.dross.dimension.block;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Cinder Vent: a cracked basalt-like block of the Cinder Wastes with a glowing vent mouth on top (the mouth is drawn
 * full-bright by its model; its light level is in its block properties). Now and then it puffs a short column of
 * smoke, and rarely a few electric blue sparks fly out with it: the Dross leaking through.
 *
 * <p>It's harmless: only particles come out (no damage, no fire, nothing catches light), and it never burns. Mine it
 * with a pickaxe; it drops itself. Nothing comes out while a solid block covers its mouth.
 */
public class CinderVentBlock extends Block
{
    // A block within 16 blocks of the player gets an animateTick call about every 2.5 seconds.
    /** 1 in this many animateTick calls puffs smoke (about every 7 seconds per vent). */
    private static final int SMOKE_CHANCE = 3;
    /** How many smoke clouds make up one puff, each a little higher and faster than the last (a short column). */
    private static final int SMOKE_CLOUDS = 4;
    /** Upward speed of the lowest cloud, and how much faster each next one rises (blocks per tick). */
    private static final double SMOKE_SPEED = 0.03D;
    private static final double SMOKE_SPEED_STEP = 0.015D;
    /** 1 in this many animateTick calls also throws out sparks (about every 40 seconds per vent). */
    private static final int SPARK_CHANCE = 16;
    /** How many sparks fly out at once, and how big they are (1.0 = redstone dust size). */
    private static final int SPARKS = 4;
    private static final float SPARK_SIZE = 0.7F;
    /** How far from the middle of the top the mouth reaches (blocks): smoke and sparks come from inside it. */
    private static final double MOUTH_RADIUS = 0.2D;

    /** A spark starts pale blue and fades to the Dross's electric blue. */
    private static final DustColorTransitionOptions SPARK = new DustColorTransitionOptions(
            DrossColors.vector(DrossColors.CINDER_VENT_SPARK), DrossColors.vector(DrossColors.CINDER_VENT_SPARK_FADE), SPARK_SIZE);

    public CinderVentBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        BlockPos above = pos.above();
        if (level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN))
        {
            return; // its mouth is covered
        }
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.02D;
        double z = pos.getZ() + 0.5D;
        if (random.nextInt(SMOKE_CHANCE) == 0)
        {
            for (int i = 0; i < SMOKE_CLOUDS; i++)
            {
                level.addParticle(ParticleTypes.LARGE_SMOKE, x + mouth(random), y + i * 0.15D, z + mouth(random),
                        0.0D, SMOKE_SPEED + i * SMOKE_SPEED_STEP, 0.0D);
            }
        }
        if (random.nextInt(SPARK_CHANCE) == 0)
        {
            for (int i = 0; i < SPARKS; i++)
            {
                level.addParticle(SPARK, x + mouth(random), y, z + mouth(random), mouth(random), 1.0D, mouth(random));
            }
        }
    }

    private static double mouth(RandomSource random)
    {
        return (random.nextDouble() * 2.0D - 1.0D) * MOUTH_RADIUS;
    }
}
