package com.reg21meme.dross.boss.emberdactyl.trophy.client;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.emberdactyl.trophy.EmberTrophyBlock;
import com.reg21meme.dross.boss.emberdactyl.trophy.EmberTrophyModels;
import com.reg21meme.dross.boss.emberdactyl.trophy.EmberdactylTrophies;
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
 * The Emberdactyl trophy's small, occasional particles (client only; called from {@code EmberTrophyBlock.animateTick}),
 * from the spots generated with each design ({@link EmberTrophyModels.Mark}), turned to the way the trophy faces. How
 * often each happens is in {@link EmberdactylTrophies} ({@code *_CHANCE}); they're subtle, sized for the little figure:
 * <ul>
 *   <li>an ember drifting down from a wing tip now and then (the boss sheds fire from its wings as it flies);</li>
 *   <li>a wisp of smoke from its volcano's crater, the burst egg, or its body breaking in its death;</li>
 *   <li>a small lava pop out of the volcano's crater.</li>
 * </ul>
 * Nothing shows with the Particles option at Minimal.
 */
public final class EmberTrophyParticles
{
    /** Specks in an ember, their size (redstone dust is 1) and how far they may start apart (blocks, each way). */
    private static final int EMBER_SPECKS = 2;
    private static final float EMBER_SIZE = 0.3F;
    private static final double EMBER_JITTER = 0.01D;
    /** How fast an ember drifts (blocks per tick, the way it points: down). */
    private static final double EMBER_SPEED = 0.01D;

    /** A wisp of smoke's size, as a share of vanilla's. */
    private static final float SMOKE_SCALE = 0.35F;
    /** How far from its spot it may appear (blocks, each way sideways). */
    private static final double SMOKE_JITTER = 0.02D;
    /** How fast it rises (blocks per tick). */
    private static final double SMOKE_RISE = 0.01D;
    /** How long it lasts (ticks): at least... */
    private static final int SMOKE_LIFE_MIN = 14;
    /** ...and up to this many more. */
    private static final int SMOKE_LIFE_EXTRA = 10;

    /** A lava pop's size, as a share of vanilla's. */
    private static final float LAVA_SCALE = 0.25F;

    private EmberTrophyParticles()
    {
    }

    /** Now and then a few particles for the trophy at {@code pos} (see the class comment). */
    public static void animate(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.particles().get() == ParticleStatus.MINIMAL || !state.hasProperty(EmberTrophyBlock.DESIGN))
        {
            return;
        }
        EmberTrophyModels.Model model = EmberdactylTrophies.get(state.getValue(EmberTrophyBlock.DESIGN)).model();
        Direction facing = state.getValue(EmberTrophyBlock.FACING);
        Vec3 corner = Vec3.atLowerCornerOf(pos);
        boolean ember = random.nextFloat() < EmberdactylTrophies.EMBER_CHANCE;
        boolean smoke = random.nextFloat() < EmberdactylTrophies.SMOKE_CHANCE;
        boolean lava = random.nextFloat() < EmberdactylTrophies.LAVA_CHANCE;
        for (EmberTrophyModels.Mark mark : model.marks())
        {
            Vec3 at = corner.add(EmberTrophyBlock.turn(mark.at(), facing));
            switch (mark.effect())
            {
                case EMBER -> {
                    // (one of the wing tips at a time, so it doesn't look like a pattern)
                    if (ember && random.nextBoolean())
                    {
                        Vec3 toward = corner.add(EmberTrophyBlock.turn(mark.toward(), facing)).subtract(at);
                        Vec3 aim = toward.lengthSqr() > 1.0E-8D ? toward.normalize() : new Vec3(0.0D, -1.0D, 0.0D);
                        ember(level, at, aim, random);
                    }
                }
                case SMOKE -> {
                    if (smoke)
                    {
                        smoke(minecraft, at, random);
                    }
                }
                case LAVA -> {
                    if (lava)
                    {
                        Particle p = add(minecraft, ParticleTypes.LAVA, at, Vec3.ZERO);
                        if (p != null)
                        {
                            p.scale(LAVA_SCALE);
                        }
                    }
                }
                default -> {
                }
            }
        }
    }

    /** A tiny ember falling from a wing tip, along {@code aim}. */
    private static void ember(Level level, Vec3 at, Vec3 aim, RandomSource random)
    {
        int rgb = DrossColors.EMBERDACTYL_TROPHY_EMBER;
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(DrossColors.red(rgb), DrossColors.green(rgb), DrossColors.blue(rgb)), EMBER_SIZE);
        for (int i = 0; i < EMBER_SPECKS; i++)
        {
            level.addParticle(dust, at.x + jitter(random, EMBER_JITTER), at.y + jitter(random, EMBER_JITTER),
                    at.z + jitter(random, EMBER_JITTER), aim.x * EMBER_SPEED, aim.y * EMBER_SPEED, aim.z * EMBER_SPEED);
        }
    }

    /** A small wisp of smoke drifting up. */
    private static void smoke(Minecraft minecraft, Vec3 at, RandomSource random)
    {
        Particle p = add(minecraft, ParticleTypes.SMOKE, at.add(jitter(random, SMOKE_JITTER), 0.0D,
                jitter(random, SMOKE_JITTER)), Vec3.ZERO);
        if (p == null)
        {
            return;
        }
        p.setParticleSpeed(0.0D, SMOKE_RISE, 0.0D);
        p.setLifetime(SMOKE_LIFE_MIN + random.nextInt(SMOKE_LIFE_EXTRA + 1));
        p.scale(SMOKE_SCALE);
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
