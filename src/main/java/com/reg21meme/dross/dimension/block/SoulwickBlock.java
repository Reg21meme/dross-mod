package com.reg21meme.dross.dimension.block;

import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Soulwick: a flower whose bloom is a blue soul flame (its light level is in its block properties; the flame is drawn
 * full-bright by its model). Now and then a soul fire flame flickers up from it, like from a soul torch. It came from the
 * Nether Scar, a biome until October 17, 2026; now it's only placed by hand (it no longer generates).
 *
 * <p>It's a flower, not fire: it never burns, never sets anything alight and never hurts. It grows on soul soil and
 * soul sand ({@code #dross:soulwick_grows_on}).
 */
public class SoulwickBlock extends DrossFlowerBlock
{
    // A block within 16 blocks of the player gets an animateTick call about every 2.5 seconds.
    /** 1 in this many animateTick calls sends up a flame (about every 7 seconds per flower). */
    private static final int FLAME_CHANCE = 3;
    /** Where the top of the flame is (blocks above the ground). */
    private static final double FLAME_TOP = 0.78D;

    public SoulwickBlock(Supplier<MobEffect> stewEffect, int stewSeconds, Properties properties)
    {
        super(stewEffect, stewSeconds, PlantGround.SOULWICK, false, properties);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        if (random.nextInt(FLAME_CHANCE) != 0)
        {
            return;
        }
        Vec3 offset = state.getOffset(level, pos);
        double x = pos.getX() + 0.5D + offset.x + (random.nextDouble() - 0.5D) * 0.06D;
        double z = pos.getZ() + 0.5D + offset.z + (random.nextDouble() - 0.5D) * 0.06D;
        level.addParticle(ParticleTypes.SOUL_FIRE_FLAME, x, pos.getY() + FLAME_TOP, z, 0.0D, 0.01D, 0.0D);
    }
}
