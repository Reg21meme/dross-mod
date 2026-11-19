package com.reg21meme.dross.dimension.ward.client;

import com.reg21meme.dross.DrossColors;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;

/**
 * CLIENT ONLY. A shard of a land's barrier as it breaks ({@code dross:ward_shard}; {@link LandWardFall}): one of the
 * curtain's runes torn loose, tumbling as it falls and fading from {@link DrossColors#LAND_WARD_SHARD} to
 * {@link DrossColors#LAND_WARD_SHARD_FADE}. It uses the runes' own sprites ({@code particles/ward_shard.json}). Its
 * starting speed is the one it's made with (thrown out and up a little), then it falls.
 */
public class WardShardParticle extends TextureSheetParticle
{
    /** How long a shard lasts (ticks), at least and at most. */
    public static final int MIN_LIFETIME = 30;
    public static final int MAX_LIFETIME = 55;
    /** Its size (blocks across), at least and at most. */
    public static final float MIN_SIZE = 0.35F;
    public static final float MAX_SIZE = 0.8F;
    /** How fast it falls (Minecraft's particle gravity: blocks a tick each tick, times 0.04). */
    public static final float GRAVITY = 0.9F;
    /** How fast it tumbles (radians a tick), at most either way. */
    public static final float MAX_SPIN = 0.35F;
    /** Its brightest (0 to 1). */
    public static final float MAX_ALPHA = 0.95F;

    private final float spin;

    protected WardShardParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                SpriteSet sprites)
    {
        super(level, x, y, z, xd, yd, zd);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = Mth.nextInt(this.random, MIN_LIFETIME, MAX_LIFETIME);
        this.gravity = GRAVITY;
        this.friction = 0.96F;
        this.hasPhysics = true;
        this.quadSize = Mth.nextFloat(this.random, MIN_SIZE, MAX_SIZE) / 2.0F;
        this.spin = Mth.nextFloat(this.random, -MAX_SPIN, MAX_SPIN);
        this.roll = this.random.nextFloat() * Mth.TWO_PI;
        this.oRoll = this.roll;
        this.pickSprite(sprites);
        this.shade();
    }

    @Override
    public void tick()
    {
        super.tick();
        this.oRoll = this.roll;
        if (!this.onGround)
        {
            this.roll += this.spin;
        }
        this.shade();
    }

    private void shade()
    {
        float life = Mth.clamp((float) this.age / this.lifetime, 0.0F, 1.0F);
        int color = WardStreakParticle.lerpColor(DrossColors.LAND_WARD_SHARD, DrossColors.LAND_WARD_SHARD_FADE, life);
        this.rCol = (color >> 16 & 0xFF) / 255.0F;
        this.gCol = (color >> 8 & 0xFF) / 255.0F;
        this.bCol = (color & 0xFF) / 255.0F;
        this.alpha = MAX_ALPHA * (1.0F - life * life);
    }

    /** Light of its own: it glows at full brightness whatever's round it. */
    @Override
    protected int getLightColor(float partialTicks)
    {
        return LightTexture.FULL_BRIGHT;
    }

    @Override
    public ParticleRenderType getRenderType()
    {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Makes a shard from the runes' sprites (a random one of the ten), with the speed it's given. */
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
            return new WardShardParticle(level, x, y, z, xd, yd, zd, this.sprites);
        }
    }
}
