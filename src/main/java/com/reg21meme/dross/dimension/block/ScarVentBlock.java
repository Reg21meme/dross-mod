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
 * Scar Vent: a crack in netherrack, glowing red-hot round an electric blue heart (drawn full-bright by its model; its
 * light level is in its block properties). Now and then it puffs out red and electric blue embers with a wisp of smoke.
 * It came from the Nether Scar, a biome until October 17, 2026 (the user had it removed); it's a building block now and
 * no longer generates.
 *
 * <p>It's harmless: the embers are only particles (no damage, no fire), it never burns, and it isn't a fire source
 * like netherrack (it's not in {@code #minecraft:infiniburn_*}). Mine it with a pickaxe; it drops itself. Nothing
 * comes out while a solid block covers it.
 */
public class ScarVentBlock extends Block
{
    // A block within 16 blocks of the player gets an animateTick call about every 2.5 seconds.
    /** 1 in this many animateTick calls puffs embers (about every 5 seconds per vent). */
    private static final int PUFF_CHANCE = 2;
    /** How many embers one puff throws out. */
    private static final int EMBERS = 5;
    /** 1 in this many embers is electric blue; the rest are red. */
    private static final int BLUE_ONE_IN = 3;
    /** Size of an ember (1.0 = redstone dust size). */
    private static final float EMBER_SIZE = 0.65F;
    /** How far from the middle of the top the crack reaches (blocks): the embers come from inside it. */
    private static final double CRACK_RADIUS = 0.25D;

    private static final DustColorTransitionOptions RED_EMBER = new DustColorTransitionOptions(
            DrossColors.vector(DrossColors.SCAR_VENT_EMBER_RED), DrossColors.vector(DrossColors.SCAR_VENT_EMBER_RED_FADE), EMBER_SIZE);
    private static final DustColorTransitionOptions BLUE_EMBER = new DustColorTransitionOptions(
            DrossColors.vector(DrossColors.SCAR_VENT_EMBER_BLUE), DrossColors.vector(DrossColors.SCAR_VENT_EMBER_BLUE_FADE), EMBER_SIZE);

    public ScarVentBlock(Properties properties)
    {
        super(properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        BlockPos above = pos.above();
        if (random.nextInt(PUFF_CHANCE) != 0 || level.getBlockState(above).isFaceSturdy(level, above, Direction.DOWN))
        {
            return;
        }
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 1.02D;
        double z = pos.getZ() + 0.5D;
        for (int i = 0; i < EMBERS; i++)
        {
            DustColorTransitionOptions ember = random.nextInt(BLUE_ONE_IN) == 0 ? BLUE_EMBER : RED_EMBER;
            level.addParticle(ember, x + crack(random), y, z + crack(random), crack(random), 1.0D, crack(random));
        }
        level.addParticle(ParticleTypes.SMOKE, x + crack(random), y, z + crack(random), 0.0D, 0.02D, 0.0D);
    }

    private static double crack(RandomSource random)
    {
        return (random.nextDouble() * 2.0D - 1.0D) * CRACK_RADIUS;
    }
}
