package com.reg21meme.dross.boss.knight;

import com.reg21meme.dross.DrossColors;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * The elemental knights' extra effects (the user, November 19, 2026: "add some more effects, make some attacks look
 * better"): particles only, nothing they do changes. Every number here is a look, not a rule of the fight.
 * <ul>
 *   <li><b>Wind-ups</b> ({@link #windup}): from a move's start to its blow, its weapon gathers its element (frost on the
 *       raised axe, fire on the hammer, sparks on the anchor...), and the big moves show where they'll land: the
 *       Warden's poison cloud gathering over him before it rains, the dust falling where the Rockfall Knight's rocks
 *       will come down, the ground cracking under the Geode Knight's ring of crystals, sparks on the ground where the
 *       anchor slam's lightning will strike.</li>
 *   <li><b>Shockwaves</b> ({@link #shockwave}): a ring of the ground's own blocks and the knight's element rolling out
 *       from every slam.</li>
 *   <li><b>Impacts</b> ({@link #impact}): a burst of its element on whatever its blow hits.</li>
 *   <li><b>Its strikes</b>: the spear's thrust leaves a streak of frost, the pick a spray of amethyst, the Stormblade's
 *       cuts an arc of lightning.</li>
 *   <li><b>Its death</b> ({@link #death}): its element bursts out of it (ice shards, spores, embers and smoke, a
 *       crackle of lightning, stone and amethyst).</li>
 *   <li><b>Living touches</b> ({@link #ambient}, client): the Cinder Knight sheds embers, Flamehand's fists burn, the
 *       Thawed Soldiers' breath frosts, the Warden's globe drips, spores drift off the Sporebound, sparks crawl on the
 *       thunder knights' steel, amethyst glints on the Geode Knight, grit falls off the Rockfall Knight's boulder.</li>
 * </ul>
 */
final class KnightEffects
{
    /** Ticks a slam's shockwave takes to roll out to its edge. */
    static final int WAVE_TICKS = 6;

    private KnightEffects()
    {
    }

    private static DustParticleOptions dust(int color, float size)
    {
        return new DustParticleOptions(DrossColors.vector(color), size);
    }

    /** The particle of a knight's element (a puff of it). */
    static ParticleOptions element(KnightKind.Element e)
    {
        return switch (e)
        {
            case ICE -> ParticleTypes.SNOWFLAKE;
            case POISON -> dust(DrossColors.KNIGHT_SPORES, 1.4F);
            case FIRE -> ParticleTypes.FLAME;
            case THUNDER -> ParticleTypes.ELECTRIC_SPARK;
            case EARTH -> dust(DrossColors.KNIGHT_STONE_DUST, 1.6F);
        };
    }

    // ---------------------------------------------------------------- wind-ups

    /**
     * One tick of a move's wind-up: {@code t} ticks since it started, {@code until} the tick its blow lands (or what it
     * holds leaves its hand). Called only before then.
     */
    static void windup(ServerLevel level, ElementalKnight k, int move, int t, int until)
    {
        if (until <= 0 || t >= until)
        {
            return;
        }
        double phase = t / (double) until;
        RandomSource r = k.getRandom();
        switch (k.kind())
        {
            case THAWED_AXE ->
            {
                if (move == 1 && phase > 0.35)
                {
                    // frost gathering on the raised axe
                    Vec3 at = k.at(0.4D, 0.4D, 5.2D);
                    level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 2, 0.4D, 0.4D, 0.4D, 0.01D);
                    level.sendParticles(dust(DrossColors.KNIGHT_FROST, 1.2F), at.x, at.y, at.z, 1, 0.3D, 0.3D, 0.3D, 0.0D);
                }
                else if (move == 0 && phase > 0.5)
                {
                    Vec3 at = k.at(0.3D, 1.4D, 2.6D);
                    level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 1, 0.3D, 0.3D, 0.3D, 0.01D);
                }
            }
            case THAWED_SPEAR ->
            {
                if (move == 0 && phase > 0.55)
                {
                    // a frost glint running down the levelled spear
                    Vec3 at = k.at(2.4D, 0.6D, 2.6D);
                    level.sendParticles(dust(DrossColors.KNIGHT_FROST, 1.0F), at.x, at.y, at.z, 2, 0.5D, 0.05D, 0.5D, 0.0D);
                }
            }
            case BLIGHT_WARDEN ->
            {
                if (move == 1)
                {
                    // the globe raised high, glowing, and the cloud the rain will fall from gathering over him
                    Vec3 globe = k.at(0.2D, 0.6D, 5.3D);
                    level.sendParticles(dust(DrossColors.KNIGHT_POISON_RAIN, 1.5F), globe.x, globe.y, globe.z, 2,
                            0.25D, 0.25D, 0.25D, 0.0D);
                    if (phase > 0.3 && t % 2 == 0)
                    {
                        double rr = KnightTuning.RAIN_RADIUS * phase;
                        level.sendParticles(dust(DrossColors.KNIGHT_POISON_RAIN_DARK, 3.0F), k.getX(),
                                k.getY() + KnightTuning.RAIN_HEIGHT + 0.5D, k.getZ(), 4 + (int) (6 * phase),
                                rr * 0.5D, 0.3D, rr * 0.5D, 0.0D);
                    }
                }
                else if (phase > 0.3)
                {
                    Vec3 globe = k.at(-0.2D, 0.7D, 4.6D);
                    level.sendParticles(dust(DrossColors.KNIGHT_POISON_RAIN, 1.2F), globe.x, globe.y, globe.z, 1,
                            0.2D, 0.2D, 0.2D, 0.0D);
                }
            }
            case SPOREBOUND ->
            {
                if (move == 1)
                {
                    // spores seeping out of the swelling cap, more and more
                    Vec3 cap = k.at(-0.1D, 0.0D, 4.7D);
                    int n = 1 + (int) (phase * 4.0D);
                    level.sendParticles(dust(DrossColors.KNIGHT_SPORES, 1.6F), cap.x, cap.y, cap.z, n, 0.7D, 0.4D,
                            0.7D, 0.0D);
                    if (r.nextInt(3) == 0)
                    {
                        level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, cap.x, cap.y, cap.z, 1, 0.8D, 0.3D, 0.8D, 0.0D);
                    }
                }
                else if (phase > 0.4)
                {
                    Vec3 blade = k.at(-0.4D, 1.6D, 2.8D);
                    level.sendParticles(dust(DrossColors.KNIGHT_SPORES, 1.0F), blade.x, blade.y, blade.z, 1, 0.3D,
                            0.3D, 0.3D, 0.0D);
                }
            }
            case CINDER ->
            {
                if (move == 1)
                {
                    // the hammer burning over his head, and the ground where it'll land starting to smoke
                    Vec3 head = k.at(0.0D, 0.6D, 5.6D);
                    level.sendParticles(ParticleTypes.FLAME, head.x, head.y, head.z, 2, 0.35D, 0.35D, 0.35D, 0.02D);
                    if (r.nextInt(3) == 0)
                    {
                        level.sendParticles(ParticleTypes.LAVA, head.x, head.y, head.z, 1, 0.2D, 0.2D, 0.2D, 0.0D);
                    }
                    if (phase > 0.55 && t % 2 == 0)
                    {
                        Vec3 at = k.at(KnightTuning.SLAM_FORWARD, 0.0D, 0.0D);
                        double gy = k.groundY(at.x, at.z, k.getY());
                        level.sendParticles(ParticleTypes.SMOKE, at.x, gy + 0.1D, at.z, 4, 1.0D, 0.0D, 1.0D, 0.01D);
                        level.sendParticles(ParticleTypes.SMALL_FLAME, at.x, gy + 0.1D, at.z, 2, 0.8D, 0.0D, 0.8D, 0.0D);
                    }
                }
                else if (phase > 0.35)
                {
                    Vec3 head = k.at(-0.6D, 1.8D, 2.4D);
                    level.sendParticles(ParticleTypes.FLAME, head.x, head.y, head.z, 1, 0.25D, 0.25D, 0.25D, 0.01D);
                }
            }
            case FLAMEHAND ->
            {
                if (move == 1 && phase > 0.2)
                {
                    // flames drawn in to the fireball between his raised hands
                    Vec3 orb = k.at(0.6D, 0.0D, 5.4D);
                    for (int i = 0; i < 2; i++)
                    {
                        double a = r.nextDouble() * Math.PI * 2.0D;
                        double b = (r.nextDouble() - 0.5D) * Math.PI;
                        Vec3 out = new Vec3(Math.cos(a) * Math.cos(b), Math.sin(b), Math.sin(a) * Math.cos(b)).scale(1.8D);
                        Vec3 from = orb.add(out);
                        level.sendParticles(ParticleTypes.FLAME, from.x, from.y, from.z, 0, -out.x, -out.y, -out.z, 0.08D);
                    }
                }
                else if (move == 0 && phase > 0.3)
                {
                    Vec3 fist = k.at(0.9D, -0.6D, 3.1D);
                    level.sendParticles(ParticleTypes.FLAME, fist.x, fist.y, fist.z, 2, 0.15D, 0.15D, 0.15D, 0.02D);
                }
            }
            case WRECK ->
            {
                if (move == 1)
                {
                    // lightning crackling on the raised anchor; sparks skittering where it'll strike
                    Vec3 anchor = k.at(-0.3D, 0.9D, 5.2D);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, anchor.x, anchor.y, anchor.z, 3, 0.5D, 0.5D, 0.5D, 0.15D);
                    if (r.nextInt(2) == 0)
                    {
                        level.sendParticles(dust(DrossColors.KNIGHT_SPARK_BRIGHT, 1.4F), anchor.x, anchor.y, anchor.z, 1,
                                0.4D, 0.4D, 0.4D, 0.0D);
                    }
                    if (phase > 0.6)
                    {
                        Vec3 at = k.at(3.0D, 0.0D, 0.0D);
                        double gy = k.groundY(at.x, at.z, k.getY());
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, at.x, gy + 0.15D, at.z, 3, 0.7D, 0.05D, 0.7D, 0.1D);
                    }
                }
                else if (phase > 0.25)
                {
                    Vec3 anchor = k.at(-1.0D, 0.9D, 3.2D);
                    level.sendParticles(ParticleTypes.ELECTRIC_SPARK, anchor.x, anchor.y, anchor.z, 1, 0.4D, 0.4D, 0.4D, 0.1D);
                }
            }
            case STORMBLADE ->
            {
                if (move == 0)
                {
                    for (double side : new double[] {0.9D, -0.9D})
                    {
                        Vec3 blade = k.at(0.7D, side, 2.0D);
                        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, blade.x, blade.y, blade.z, 1, 0.3D, 0.3D, 0.3D, 0.1D);
                    }
                }
            }
            case GEODE ->
            {
                if (move == 1)
                {
                    // the pick glinting overhead, and the ground cracking open in a ring where the crystals will burst
                    Vec3 pick = k.at(0.2D, 0.8D, 5.0D);
                    level.sendParticles(dust(DrossColors.KNIGHT_AMETHYST_DUST, 1.4F), pick.x, pick.y, pick.z, 2, 0.4D,
                            0.4D, 0.4D, 0.0D);
                    if (phase > 0.45 && t % 2 == 0)
                    {
                        ring(level, k, k.position(), KnightTuning.BURST_INNER, 10,
                                dust(DrossColors.KNIGHT_AMETHYST_DUST, 1.1F), true);
                    }
                }
                else if (phase > 0.35)
                {
                    Vec3 pick = k.at(-0.4D, 1.8D, 3.0D);
                    level.sendParticles(dust(DrossColors.KNIGHT_AMETHYST_DUST, 1.0F), pick.x, pick.y, pick.z, 1, 0.3D,
                            0.3D, 0.3D, 0.0D);
                }
            }
            case ROCKFALL ->
            {
                // grit falling off the boulder over his head
                Vec3 boulder = k.at(0.0D, 0.0D, 5.6D);
                level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()),
                        boulder.x, boulder.y, boulder.z, 1, 0.6D, 0.3D, 0.6D, 0.0D);
                LivingEntity target = k.getTarget();
                if (move == 1 && phase > 0.4 && target != null)
                {
                    // dust trickling down out of the sky where the rocks will fall
                    Vec3 mid = target.position();
                    for (int i = 0; i < 2; i++)
                    {
                        double a = r.nextDouble() * Math.PI * 2.0D;
                        double d = Math.sqrt(r.nextDouble()) * KnightTuning.ROCKFALL_RADIUS;
                        level.sendParticles(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.STONE.defaultBlockState()),
                                mid.x + Math.cos(a) * d, mid.y + 7.0D + r.nextDouble() * 3.0D, mid.z + Math.sin(a) * d,
                                1, 0.0D, 0.0D, 0.0D, 0.0D);
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- shockwaves

    /**
     * A shockwave rolling out from {@code at} over {@link #WAVE_TICKS} ticks to {@code radius}: the ground's own
     * blocks kicked up in a ring, and the knight's {@code element}.
     */
    static void shockwave(ServerLevel level, ElementalKnight k, Vec3 at, double radius, ParticleOptions element)
    {
        for (int i = 0; i < WAVE_TICKS; i++)
        {
            double rr = radius * (i + 1) / WAVE_TICKS;
            k.later(i, () -> ring(level, k, at, rr, Math.max(10, (int) (rr * 5.0D)), element, false));
        }
    }

    /** One ring of {@code count} puffs round {@code at} at {@code radius}, on the ground under each. */
    static void ring(ServerLevel level, ElementalKnight k, Vec3 at, double radius, int count, ParticleOptions element,
                     boolean faint)
    {
        RandomSource r = k.getRandom();
        for (int i = 0; i < count; i++)
        {
            double a = 2.0D * Math.PI * (i + r.nextDouble() * 0.6D) / count;
            double x = at.x + Math.cos(a) * radius;
            double z = at.z + Math.sin(a) * radius;
            double gy = k.groundY(x, z, at.y);
            BlockState ground = level.getBlockState(BlockPos.containing(x, gy - 0.5D, z));
            if (!faint && !ground.isAir())
            {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, ground), x, gy + 0.1D, z, 2,
                        0.15D, 0.05D, 0.15D, 0.15D);
            }
            if (faint || i % 2 == 0)
            {
                // (pushed outwards as it rolls)
                level.sendParticles(element, x, gy + 0.2D, z, 0, Math.cos(a), faint ? 0.6D : 0.25D, Math.sin(a),
                        faint ? 0.02D : 0.12D);
            }
        }
    }

    // ---------------------------------------------------------------- blows landing

    /** A burst of its element on {@code hit}, where its blow landed. */
    static void impact(ServerLevel level, LivingEntity hit, KnightKind.Element e)
    {
        double x = hit.getX();
        double y = hit.getY() + hit.getBbHeight() * 0.55D;
        double z = hit.getZ();
        double w = hit.getBbWidth() * 0.4D;
        level.sendParticles(ParticleTypes.CRIT, x, y, z, 6, w, 0.3D, w, 0.3D);
        switch (e)
        {
            case ICE ->
            {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                        x, y, z, 10, w, 0.3D, w, 0.15D);
                level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 8, w, 0.4D, w, 0.04D);
            }
            case POISON ->
            {
                level.sendParticles(dust(DrossColors.KNIGHT_SPORES, 1.5F), x, y, z, 10, w, 0.4D, w, 0.0D);
                level.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 6, w, 0.3D, w, 0.1D);
            }
            case FIRE ->
            {
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 12, w, 0.4D, w, 0.06D);
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 2, w, 0.2D, w, 0.0D);
            }
            case THUNDER ->
            {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 16, w, 0.5D, w, 0.35D);
                level.sendParticles(dust(DrossColors.KNIGHT_SPARK_BRIGHT, 1.6F), x, y, z, 6, w, 0.4D, w, 0.0D);
            }
            case EARTH ->
            {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        x, y, z, 10, w, 0.3D, w, 0.15D);
                level.sendParticles(dust(DrossColors.KNIGHT_STONE_DUST, 1.8F), x, y, z, 6, w, 0.3D, w, 0.0D);
            }
        }
    }

    /** The Thawed spear's thrust: a streak of frost along it, from its fist out to {@code reach}. */
    static void thrustStreak(ServerLevel level, ElementalKnight k, double reach)
    {
        for (int i = 0; i <= 8; i++)
        {
            double d = 1.0D + (reach - 0.5D) * i / 8.0D;
            Vec3 at = k.at(d, 0.6D, 2.6D);
            level.sendParticles(dust(DrossColors.KNIGHT_FROST, 1.3F), at.x, at.y, at.z, 2, 0.08D, 0.08D, 0.08D, 0.0D);
            if (i % 2 == 0)
            {
                level.sendParticles(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 1, 0.1D, 0.1D, 0.1D, 0.02D);
            }
        }
        Vec3 tip = k.at(reach, 0.6D, 2.6D);
        level.sendParticles(ParticleTypes.SNOWFLAKE, tip.x, tip.y, tip.z, 10, 0.25D, 0.25D, 0.25D, 0.08D);
    }

    /**
     * A sweep's trail: {@code n} puffs along an arc in front of it at {@code radius}, {@code up} high, from
     * {@code fromDeg} to {@code toDeg} (0 straight ahead, positive to its right).
     */
    static void arc(ServerLevel level, ElementalKnight k, double radius, double up, double fromDeg, double toDeg, int n,
                    ParticleOptions particle, double speed)
    {
        for (int i = 0; i <= n; i++)
        {
            double a = Math.toRadians(Mth.lerp(i / (double) n, fromDeg, toDeg));
            Vec3 at = k.at(Math.cos(a) * radius, Math.sin(a) * radius, up);
            level.sendParticles(particle, at.x, at.y, at.z, 1, 0.05D, 0.05D, 0.05D, speed);
        }
    }

    // ---------------------------------------------------------------- its death

    /** Its element bursting out of it as it dies. */
    static void death(ServerLevel level, ElementalKnight k)
    {
        double x = k.getX();
        double y = k.getY() + k.getBbHeight() * 0.5D;
        double z = k.getZ();
        double w = k.getBbWidth() * 0.5D;
        double h = k.getBbHeight() * 0.35D;
        level.sendParticles(ParticleTypes.POOF, x, y, z, 20, w, h, w, 0.04D);
        switch (k.kind().element)
        {
            case ICE ->
            {
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.PACKED_ICE.defaultBlockState()),
                        x, y, z, 50, w, h, w, 0.25D);
                level.sendParticles(ParticleTypes.SNOWFLAKE, x, y, z, 40, w, h, w, 0.1D);
            }
            case POISON ->
            {
                level.sendParticles(dust(DrossColors.KNIGHT_SPORES, 2.2F), x, y, z, 60, w * 1.4D, h, w * 1.4D, 0.0D);
                level.sendParticles(ParticleTypes.SPORE_BLOSSOM_AIR, x, y, z, 30, w * 1.5D, h, w * 1.5D, 0.0D);
                level.sendParticles(ParticleTypes.ITEM_SLIME, x, y, z, 20, w, h, w, 0.15D);
            }
            case FIRE ->
            {
                level.sendParticles(ParticleTypes.FLAME, x, y, z, 50, w, h, w, 0.08D);
                level.sendParticles(ParticleTypes.LAVA, x, y, z, 12, w, h, w, 0.0D);
                level.sendParticles(ParticleTypes.LARGE_SMOKE, x, y, z, 25, w, h, w, 0.03D);
            }
            case THUNDER ->
            {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, x, y, z, 70, w, h, w, 0.5D);
                level.sendParticles(dust(DrossColors.KNIGHT_SPARK_BRIGHT, 2.0F), x, y, z, 30, w, h, w, 0.0D);
                level.sendParticles(dust(DrossColors.KNIGHT_SPARK, 2.0F), x, y, z, 30, w * 1.3D, h, w * 1.3D, 0.0D);
            }
            case EARTH ->
            {
                BlockState stone = k.kind() == KnightKind.GEODE ? Blocks.AMETHYST_BLOCK.defaultBlockState()
                        : Blocks.MOSSY_COBBLESTONE.defaultBlockState();
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, stone), x, y, z, 50, w, h, w, 0.25D);
                level.sendParticles(new BlockParticleOption(ParticleTypes.BLOCK, Blocks.STONE.defaultBlockState()),
                        x, y, z, 40, w, h, w, 0.25D);
                level.sendParticles(dust(DrossColors.KNIGHT_STONE_DUST, 2.4F), x, y, z, 25, w, h, w, 0.0D);
            }
        }
    }

    // ---------------------------------------------------------------- living touches (client)

    /** (client, every tick) Its element now and then: embers, burning fists, frosty breath, sparks... */
    static void ambient(Level level, ElementalKnight k)
    {
        RandomSource r = k.getRandom();
        switch (k.kind())
        {
            case CINDER ->
            {
                if (r.nextFloat() < 0.3F)
                {
                    Vec3 at = k.at((r.nextDouble() - 0.5D) * 0.9D, (r.nextDouble() - 0.5D) * 1.2D, 1.0D + r.nextDouble() * 3.0D);
                    level.addParticle(r.nextInt(6) == 0 ? ParticleTypes.LAVA : ParticleTypes.SMALL_FLAME, at.x, at.y,
                            at.z, 0.0D, 0.02D, 0.0D);
                }
            }
            case FLAMEHAND ->
            {
                for (double side : new double[] {0.85D, -0.85D})
                {
                    if (r.nextFloat() < 0.5F)
                    {
                        Vec3 fist = k.at(0.7D, side, 2.7D);
                        level.addParticle(ParticleTypes.FLAME, fist.x + (r.nextDouble() - 0.5D) * 0.3D, fist.y,
                                fist.z + (r.nextDouble() - 0.5D) * 0.3D, 0.0D, 0.03D, 0.0D);
                    }
                }
                if (r.nextFloat() < 0.15F)
                {
                    Vec3 helm = k.at(0.0D, 0.0D, 5.3D);
                    level.addParticle(ParticleTypes.FLAME, helm.x, helm.y, helm.z, 0.0D, 0.05D, 0.0D);
                }
            }
            case THAWED_AXE, THAWED_SPEAR ->
            {
                // its breath frosting, now and then (a breath every couple of seconds)
                if (k.tickCount % 40 < 6 && r.nextFloat() < 0.5F)
                {
                    Vec3 mouth = k.at(0.55D, 0.0D, 3.9D);
                    Vec3 f = k.forward();
                    level.addParticle(dust(DrossColors.KNIGHT_FROST, 0.9F), mouth.x, mouth.y, mouth.z, f.x * 0.05D, 0.0D,
                            f.z * 0.05D);
                }
                if (r.nextFloat() < 0.04F)
                {
                    Vec3 at = k.at((r.nextDouble() - 0.5D), (r.nextDouble() - 0.5D) * 1.2D, 1.0D + r.nextDouble() * 3.0D);
                    level.addParticle(ParticleTypes.SNOWFLAKE, at.x, at.y, at.z, 0.0D, -0.02D, 0.0D);
                }
            }
            case BLIGHT_WARDEN ->
            {
                if (r.nextFloat() < 0.2F)
                {
                    Vec3 globe = k.at(0.35D, 1.3D, 4.6D);
                    level.addParticle(dust(DrossColors.KNIGHT_POISON_RAIN, 1.0F), globe.x, globe.y, globe.z, 0.0D, 0.0D, 0.0D);
                }
                if (r.nextFloat() < 0.04F)
                {
                    Vec3 globe = k.at(0.35D, 1.3D, 4.3D);
                    level.addParticle(ParticleTypes.ITEM_SLIME, globe.x, globe.y, globe.z, 0.0D, -0.05D, 0.0D);
                }
            }
            case SPOREBOUND ->
            {
                if (r.nextFloat() < 0.15F)
                {
                    Vec3 cap = k.at((r.nextDouble() - 0.5D) * 1.6D, (r.nextDouble() - 0.5D) * 1.6D, 4.4D + r.nextDouble() * 0.6D);
                    level.addParticle(dust(DrossColors.KNIGHT_SPORES, 1.0F), cap.x, cap.y, cap.z, 0.0D, 0.0D, 0.0D);
                }
                if (r.nextFloat() < 0.03F)
                {
                    Vec3 cap = k.at(0.0D, 0.0D, 4.6D);
                    level.addParticle(ParticleTypes.SPORE_BLOSSOM_AIR, cap.x, cap.y, cap.z, 0.0D, 0.0D, 0.0D);
                }
            }
            case WRECK ->
            {
                if (!k.isAnchorOut() && r.nextFloat() < 0.1F)
                {
                    Vec3 anchor = k.at(-0.2D - r.nextDouble() * 1.6D, 0.9D, 3.5D + r.nextDouble() * 0.3D);
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, anchor.x, anchor.y, anchor.z, 0.0D, 0.0D, 0.0D);
                }
            }
            case STORMBLADE ->
            {
                if (r.nextFloat() < 0.15F)
                {
                    double side = r.nextBoolean() ? 1.0D : -1.0D;
                    Vec3 blade = k.at(0.3D + r.nextDouble() * 0.6D, 0.9D * side, 1.5D + r.nextDouble() * 0.6D);
                    level.addParticle(ParticleTypes.ELECTRIC_SPARK, blade.x, blade.y, blade.z, 0.0D, 0.0D, 0.0D);
                }
            }
            case GEODE ->
            {
                if (r.nextFloat() < 0.08F)
                {
                    Vec3 at = k.at((r.nextDouble() - 0.5D) * 1.0D, (r.nextDouble() - 0.5D) * 1.4D, 1.5D + r.nextDouble() * 3.4D);
                    level.addParticle(dust(DrossColors.KNIGHT_AMETHYST_DUST, 0.8F), at.x, at.y, at.z, 0.0D, 0.0D, 0.0D);
                }
            }
            case ROCKFALL ->
            {
                if (r.nextFloat() < 0.1F)
                {
                    Vec3 boulder = k.at((r.nextDouble() - 0.5D) * 1.2D, (r.nextDouble() - 0.5D) * 1.2D, 5.0D);
                    level.addParticle(new BlockParticleOption(ParticleTypes.FALLING_DUST, Blocks.GRAVEL.defaultBlockState()),
                            boulder.x, boulder.y, boulder.z, 0.0D, 0.0D, 0.0D);
                }
            }
        }
    }
}
