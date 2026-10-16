package com.reg21meme.dross.boss.ratking.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * A "Z" floating up from a sleeping Rat King's head ({@code dross:zzz}; its sprite, {@code textures/particle/zzz.png},
 * is drawn by {@code tools/ratking/build.py}). It drifts up and sways sideways, growing as it rises, and fades out.
 * A trophy's sleeping figure makes smaller ones ({@link #shrink}).
 */
public class ZzzParticle extends TextureSheetParticle
{
    /** How long a Z lasts (ticks). */
    private static final int LIFETIME = 44;
    /** How fast it rises (blocks per tick). */
    private static final double RISE = 0.022D;
    /** Its size when it appears and when it's gone. */
    private static final float START_SIZE = 0.09F;
    private static final float END_SIZE = 0.26F;
    /** How far it sways sideways each tick (blocks, at most), and drifts the same way each tick. */
    private static final double SWAY = 0.012D;
    private static final double DRIFT = 0.006D;

    private final double swayPhase;
    /** This one's sizes and speeds: the constants above, or less for a trophy's small Z ({@link #shrink}). */
    private float startSize = START_SIZE;
    private float endSize = END_SIZE;
    private double rise = RISE;
    private double sway = SWAY;
    private double drift = DRIFT;

    protected ZzzParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites)
    {
        super(level, x, y, z);
        this.lifetime = LIFETIME;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.swayPhase = this.random.nextDouble() * Math.PI * 2.0D;
        this.quadSize = START_SIZE;
        this.pickSprite(sprites);
        this.xd = 0.0D;
        this.yd = RISE;
        this.zd = 0.0D;
    }

    @Override
    public void tick()
    {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime)
        {
            this.remove();
            return;
        }
        float life = (float) this.age / this.lifetime;
        double sway = Math.sin(this.age * 0.18D + this.swayPhase) * this.sway;
        this.move(sway + this.drift, this.rise, sway * 0.5D);
        this.quadSize = Mth.lerp(life, this.startSize, this.endSize);
        this.alpha = life < 0.7F ? 1.0F : 1.0F - (life - 0.7F) / 0.3F;
    }

    /**
     * Makes this Z smaller and slower by {@code factor} (0 to 1): its size, how fast it rises, sways and drifts. The
     * Rat King trophy's sleeping figure uses it, so its Z's fit the little figure.
     */
    public ZzzParticle shrink(float factor)
    {
        this.startSize = START_SIZE * factor;
        this.endSize = END_SIZE * factor;
        this.quadSize = this.startSize;
        this.rise = RISE * factor;
        this.sway = SWAY * factor;
        this.drift = DRIFT * factor;
        this.yd = this.rise;
        return this;
    }

    @Override
    public ParticleRenderType getRenderType()
    {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Makes the Zzz particle from its sprite. */
    public static class Provider implements ParticleProvider<SimpleParticleType>
    {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites)
        {
            this.sprites = sprites;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xd, double yd, double zd)
        {
            return new ZzzParticle(level, x, y, z, this.sprites);
        }
    }
}
