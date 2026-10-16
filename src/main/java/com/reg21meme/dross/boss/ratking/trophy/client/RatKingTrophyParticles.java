package com.reg21meme.dross.boss.ratking.trophy.client;

import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.ratking.client.ZzzParticle;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophies;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophyBlock;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophyModels;
import com.reg21meme.dross.registry.ModParticles;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import javax.annotation.Nullable;

/**
 * The Rat King trophy's small, occasional particles (client only; called from {@code RatKingTrophyBlock.animateTick}),
 * from the spots generated with each design ({@link RatKingTrophyModels.Mark}), turned to the way the trophy faces.
 * How often each happens is in {@link RatKingTrophies} ({@code *_CHANCE}); they're subtle, sized for the little figure:
 * <ul>
 *   <li>every design: a tiny glint of poison green at his staff's orb now and then;</li>
 *   <li>Fast Asleep: small Z's floating up from above his nose (his own {@code dross:zzz}, shrunk);</li>
 *   <li>Tantrum: little puffs of steam from both ears, blowing out the way his ears point;</li>
 *   <li>Ground Pound: a pinch of green dust where his staff's foot strikes the ground;</li>
 *   <li>Pop Goes the King: a crumb of dirt at the rim of his hole.</li>
 * </ul>
 * Nothing shows with the Particles option at Minimal.
 */
public final class RatKingTrophyParticles
{
    /** The Z's size and speed, as a share of the sleeping boss's (ZzzParticle.shrink). */
    private static final float ZZZ_SCALE = 0.35F;
    /** How far a Z may start from its spot (blocks, each way sideways). */
    private static final double ZZZ_JITTER = 0.03D;

    /** Puffs of steam from each ear at a time. */
    private static final int STEAM_PUFFS = 2;
    /** A steam puff's size, as a share of vanilla's poof. */
    private static final float STEAM_SCALE = 0.22F;
    /** How fast a puff leaves the ear (blocks per tick, the way the ear points). */
    private static final double STEAM_SPEED = 0.03D;
    /** How much each puff's direction varies (blocks per tick, each way). */
    private static final double STEAM_JITTER = 0.008D;
    /** How long a puff lasts (ticks): at least... */
    private static final int STEAM_LIFE_MIN = 8;
    /** ...and up to this many more. */
    private static final int STEAM_LIFE_EXTRA = 6;

    /** The orb's glint: its size, as a share of vanilla's glow squid sparkle. */
    private static final float GLINT_SCALE = 0.5F;
    /** How far from the orb's middle it may appear (blocks, each way). */
    private static final double GLINT_JITTER = 0.02D;
    /** How fast it drifts up (blocks per tick). */
    private static final double GLINT_RISE = 0.004D;
    /** How long it lasts (ticks): at least... */
    private static final int GLINT_LIFE_MIN = 8;
    /** ...and up to this many more. */
    private static final int GLINT_LIFE_EXTRA = 8;

    /** Specks in a pinch of slam dust, their size (redstone dust is 1) and spread (blocks, each way). */
    private static final int SLAM_DUST_COUNT = 3;
    private static final float SLAM_DUST_SIZE = 0.4F;
    private static final double SLAM_DUST_SPREAD = 0.03D;
    /** How fast the slam dust rises (blocks per tick). */
    private static final double SLAM_DUST_RISE = 0.01D;

    /** A dirt crumb's size, as a share of a normal block crumb. */
    private static final float CRUMB_SCALE = 0.4F;
    /** How far round the hole's middle it may appear (blocks, each way). */
    private static final double CRUMB_SPREAD = 0.06D;
    /** How fast it pops up (blocks per tick). */
    private static final double CRUMB_POP = 0.04D;

    private RatKingTrophyParticles()
    {
    }

    /** Now and then a few particles for the trophy at {@code pos} (see the class comment). */
    public static void animate(BlockState state, Level level, BlockPos pos, RandomSource random)
    {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.options.particles().get() == ParticleStatus.MINIMAL || !state.hasProperty(RatKingTrophyBlock.DESIGN))
        {
            return;
        }
        RatKingTrophyModels.Model model = RatKingTrophies.get(state.getValue(RatKingTrophyBlock.DESIGN)).model();
        Direction facing = state.getValue(RatKingTrophyBlock.FACING);
        Vec3 corner = Vec3.atLowerCornerOf(pos);
        // One roll for each kind each time, so both ears puff together.
        boolean glint = random.nextFloat() < RatKingTrophies.ORB_GLINT_CHANCE;
        boolean zzz = random.nextFloat() < RatKingTrophies.ZZZ_CHANCE;
        boolean steam = random.nextFloat() < RatKingTrophies.STEAM_CHANCE;
        boolean slam = random.nextFloat() < RatKingTrophies.SLAM_CHANCE;
        boolean crumb = random.nextFloat() < RatKingTrophies.HOLE_CHANCE;
        for (RatKingTrophyModels.Mark mark : model.marks())
        {
            Vec3 at = corner.add(RatKingTrophyBlock.turn(mark.at(), facing));
            switch (mark.effect())
            {
                case ORB -> {
                    if (glint)
                    {
                        glint(minecraft, at, random);
                    }
                }
                case ZZZ -> {
                    if (zzz)
                    {
                        Particle z = add(minecraft, ModParticles.ZZZ.get(), at.add(jitter(random, ZZZ_JITTER), 0.0D,
                                jitter(random, ZZZ_JITTER)), Vec3.ZERO);
                        if (z instanceof ZzzParticle sleepy)
                        {
                            sleepy.shrink(ZZZ_SCALE);
                        }
                    }
                }
                case STEAM -> {
                    if (steam)
                    {
                        Vec3 toward = corner.add(RatKingTrophyBlock.turn(mark.toward(), facing)).subtract(at);
                        Vec3 aim = toward.lengthSqr() > 1.0E-8D ? toward.normalize() : new Vec3(0.0D, 1.0D, 0.0D);
                        for (int i = 0; i < STEAM_PUFFS; i++)
                        {
                            steamPuff(minecraft, at, aim, random);
                        }
                    }
                }
                case SLAM -> {
                    if (slam)
                    {
                        slamDust(level, at, random);
                    }
                }
                case HOLE -> {
                    if (crumb)
                    {
                        dirtCrumb(minecraft, at, random);
                    }
                }
                default -> {
                }
            }
        }
    }

    /** A tiny, short glint of poison green at the orb, drifting up. */
    private static void glint(Minecraft minecraft, Vec3 at, RandomSource random)
    {
        Particle p = add(minecraft, ParticleTypes.GLOW, at.add(jitter(random, GLINT_JITTER), jitter(random, GLINT_JITTER),
                jitter(random, GLINT_JITTER)), Vec3.ZERO);
        if (p == null)
        {
            return;
        }
        int rgb = DrossColors.RAT_KING_TROPHY_GLINT;
        p.setColor(DrossColors.red(rgb), DrossColors.green(rgb), DrossColors.blue(rgb));
        p.setParticleSpeed(0.0D, GLINT_RISE, 0.0D);
        p.setLifetime(GLINT_LIFE_MIN + random.nextInt(GLINT_LIFE_EXTRA + 1));
        p.scale(GLINT_SCALE);
    }

    /** One small puff of steam out of an ear, blowing out along {@code aim}. */
    private static void steamPuff(Minecraft minecraft, Vec3 at, Vec3 aim, RandomSource random)
    {
        Particle p = add(minecraft, ParticleTypes.POOF, at, Vec3.ZERO);
        if (p == null)
        {
            return;
        }
        p.setParticleSpeed(aim.x * STEAM_SPEED + jitter(random, STEAM_JITTER), aim.y * STEAM_SPEED + jitter(random, STEAM_JITTER),
                aim.z * STEAM_SPEED + jitter(random, STEAM_JITTER));
        p.setLifetime(STEAM_LIFE_MIN + random.nextInt(STEAM_LIFE_EXTRA + 1));
        p.scale(STEAM_SCALE);
    }

    /** A pinch of green dust where the staff's foot strikes. */
    private static void slamDust(Level level, Vec3 at, RandomSource random)
    {
        int rgb = DrossColors.RAT_KING_TROPHY_SLAM_DUST;
        DustParticleOptions dust = new DustParticleOptions(
                new Vector3f(DrossColors.red(rgb), DrossColors.green(rgb), DrossColors.blue(rgb)), SLAM_DUST_SIZE);
        for (int i = 0; i < SLAM_DUST_COUNT; i++)
        {
            level.addParticle(dust, at.x + jitter(random, SLAM_DUST_SPREAD), at.y, at.z + jitter(random, SLAM_DUST_SPREAD),
                    0.0D, SLAM_DUST_RISE, 0.0D);
        }
    }

    /** A crumb of dirt popping up at the rim of his hole. */
    private static void dirtCrumb(Minecraft minecraft, Vec3 at, RandomSource random)
    {
        Particle p = add(minecraft, new BlockParticleOption(ParticleTypes.BLOCK, Blocks.DIRT.defaultBlockState()),
                at.add(jitter(random, CRUMB_SPREAD), 0.0D, jitter(random, CRUMB_SPREAD)), Vec3.ZERO);
        if (p == null)
        {
            return;
        }
        p.setParticleSpeed(0.0D, CRUMB_POP, 0.0D);
        p.scale(CRUMB_SCALE);
    }

    /** Adds a particle and hands it back, to be shrunk, coloured or slowed (null if it couldn't be made). */
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
