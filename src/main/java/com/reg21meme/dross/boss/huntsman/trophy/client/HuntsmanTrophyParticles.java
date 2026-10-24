package com.reg21meme.dross.boss.huntsman.trophy.client;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophies;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophyBlock;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophyModels;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * The Huntsman trophy's small, occasional particles (client only; called from {@code HuntsmanTrophyBlock.animateTick}),
 * from the spots generated with each design ({@link HuntsmanTrophyModels.Mark}), turned to the way the trophy faces.
 * How often each happens is in {@link HuntsmanTrophies} ({@code *_CHANCE}); they're subtle, sized for the little figure:
 * <ul>
 *   <li>every design: a wisp of frosty breath from the Alpha Moose's muzzle now and then;</li>
 *   <li>The Parting Shot: a snowflake drifting up off the ice.</li>
 * </ul>
 * Nothing shows with the Particles option at Minimal.
 */
public final class HuntsmanTrophyParticles
{
    /** Specks in a wisp of breath, their size (redstone dust is 1) and how far they may start apart (blocks, each way). */
    private static final int BREATH_SPECKS = 3;
    private static final float BREATH_SIZE = 0.35F;
    private static final double BREATH_JITTER = 0.01D;
    /** How fast the breath drifts out of the muzzle (blocks per tick, the way it points). */
    private static final double BREATH_SPEED = 0.012D;

    /** A snowflake's size, as a share of vanilla's. */
    private static final float FROST_SCALE = 0.4F;
    /** How far from its spot it may appear (blocks, each way sideways). */
    private static final double FROST_JITTER = 0.04D;
    /** How fast it drifts up (blocks per tick). */
    private static final double FROST_RISE = 0.006D;
    /** How long it lasts (ticks): at least... */
    private static final int FROST_LIFE_MIN = 12;
    /** ...and up to this many more. */
    private static final int FROST_LIFE_EXTRA = 10;

    private HuntsmanTrophyParticles()
    {
    }

    /** Now and then a few particles for the trophy at {@code pos} (see the class comment). */
    public static void animate(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.particles().get() == ParticleStatus.MINIMAL || !state.hasProperty(HuntsmanTrophyBlock.DESIGN))
        {
            return;
        }
        HuntsmanTrophyModels.Model model = HuntsmanTrophies.get(state.getValue(HuntsmanTrophyBlock.DESIGN)).model();
        Direction facing = state.getValue(HuntsmanTrophyBlock.FACING);
        Vec3 corner = Vec3.atLowerCornerOf(pos);
        boolean breath = random.nextFloat() < HuntsmanTrophies.BREATH_CHANCE;
        boolean frost = random.nextFloat() < HuntsmanTrophies.FROST_CHANCE;
        for (HuntsmanTrophyModels.Mark mark : model.marks())
        {
            Vec3 at = corner.add(HuntsmanTrophyBlock.turn(mark.at(), facing));
            switch (mark.effect())
            {
                case BREATH -> {
                    if (breath)
                    {
                        Vec3 toward = corner.add(HuntsmanTrophyBlock.turn(mark.toward(), facing)).subtract(at);
                        Vec3 aim = toward.lengthSqr() > 1.0E-8D ? toward.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
                        breath(level, at, aim, random);
                    }
                }
                case FROST -> {
                    if (frost)
                    {
                        snowflake(minecraft, at, random);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** A wisp of pale breath out of the moose's muzzle, drifting out along {@code aim}. */
    private static void breath(Level level, Vec3 at, Vec3 aim, RandomSource random)
    {
        int rgb = DrossColors.HUNTSMAN_TROPHY_BREATH;
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(DrossColors.red(rgb), DrossColors.green(rgb), DrossColors.blue(rgb)), BREATH_SIZE);
        for (int i = 0; i < BREATH_SPECKS; i++)
        {
            level.addParticle(dust, at.x + jitter(random, BREATH_JITTER), at.y + jitter(random, BREATH_JITTER),
                    at.z + jitter(random, BREATH_JITTER), aim.x * BREATH_SPEED, aim.y * BREATH_SPEED, aim.z * BREATH_SPEED);
        }
    }

    /** A small snowflake drifting up off the ice. */
    private static void snowflake(Minecraft minecraft, Vec3 at, RandomSource random)
    {
        Particle p = add(minecraft, ParticleTypes.SNOWFLAKE, at.add(jitter(random, FROST_JITTER), 0.0D,
                jitter(random, FROST_JITTER)), Vec3.ZERO);
        if (p == null)
        {
            return;
        }
        p.setParticleSpeed(0.0D, FROST_RISE, 0.0D);
        p.setLifetime(FROST_LIFE_MIN + random.nextInt(FROST_LIFE_EXTRA + 1));
        p.scale(FROST_SCALE);
    }

    /** Adds a particle and hands it back, to be shrunk or slowed (null if it couldn't be made). */
    @Nullable
    private static Particle add(Minecraft minecraft, ParticleOptions options, Vec3 at, Vec3 speed)
    {
        return minecraft.particleEngine.createParticle(options, at.x, at.y, at.z, speed.x, speed.y, speed.z);
    }

    /** A random offset from -{@code size} to {@code size}. */
    private static double jitter(RandomSource random, double size)
    {
        return (random.nextDouble() * 2.0D - 1.0D) * size;
    }
}
