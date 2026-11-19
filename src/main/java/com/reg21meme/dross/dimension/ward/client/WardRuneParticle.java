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
 * CLIENT ONLY. A glowing rune drifting up a land ward's curtain ({@code dross:ward_rune}; QUEST_PLAN.md, "Locked
 * lands"): one of the Dross's own glyphs ({@code textures/particle/ward_rune_<n>.png}, 16 x 16, white, drawn by
 * {@code tools/terrain/ward_particles.py}), rising slowly with a little sway, pulsing between
 * {@link DrossColors#LAND_WARD_RUNE} and {@link DrossColors#LAND_WARD_RUNE_DIM}, fading in and out. Made by
 * {@link LandWardEdge}.
 */
public class WardRuneParticle extends TextureSheetParticle
{
    /** How long a rune lasts (ticks), at least and at most. */
    public static final int MIN_LIFETIME = 80;
    public static final int MAX_LIFETIME = 140;
    /** How fast it rises (blocks a tick), at least and at most. */
    public static final float MIN_RISE_SPEED = 0.03F;
    public static final float MAX_RISE_SPEED = 0.06F;
    /** Its size (blocks across), at least and at most. */
    public static final float MIN_SIZE = 0.5F;
    public static final float MAX_SIZE = 0.7F;
    /** How far it sways sideways each tick (blocks, at most), and how fast (radians a tick). */
    public static final float SWAY = 0.01F;
    public static final float SWAY_SPEED = 0.12F;
    /** Its brightest (0 to 1). */
    public static final float MAX_ALPHA = 0.95F;
    /** It fades in over this share of its life and out over the same share at its end. */
    public static final float FADE = 0.2F;
    /** Its pulse, bright to dim and back: radians a tick. */
    public static final float PULSE_SPEED = 0.16F;

    private final float phase;
    private final double swayX;
    private final double swayZ;

    protected WardRuneParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites)
    {
        super(level, x, y, z);
        this.lifetime = Mth.nextInt(this.random, MIN_LIFETIME, MAX_LIFETIME);
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.friction = 1.0F;
        this.quadSize = Mth.nextFloat(this.random, MIN_SIZE, MAX_SIZE) * 0.5F; // a quad's size is half its width
        this.phase = this.random.nextFloat() * Mth.TWO_PI;
        double angle = this.random.nextDouble() * Math.PI * 2.0D;
        this.swayX = Math.cos(angle);
        this.swayZ = Math.sin(angle);
        this.xd = 0.0D;
        this.yd = Mth.nextFloat(this.random, MIN_RISE_SPEED, MAX_RISE_SPEED);
        this.zd = 0.0D;
        this.pickSprite(sprites);
        this.shade();
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
        double sway = Mth.sin(this.age * SWAY_SPEED + this.phase) * SWAY;
        this.move(this.swayX * sway, this.yd, this.swayZ * sway);
        this.shade();
    }

    private void shade()
    {
        float pulse = 0.5F + 0.5F * Mth.sin(this.age * PULSE_SPEED + this.phase);
        int color = WardStreakParticle.lerpColor(DrossColors.LAND_WARD_RUNE_DIM, DrossColors.LAND_WARD_RUNE, pulse);
        this.rCol = (color >> 16 & 0xFF) / 255.0F;
        this.gCol = (color >> 8 & 0xFF) / 255.0F;
        this.bCol = (color & 0xFF) / 255.0F;
        float life = (float) this.age / this.lifetime;
        float fade = Math.min(1.0F, Math.min(life / FADE, (1.0F - life) / FADE));
        this.alpha = MAX_ALPHA * Math.max(0.0F, fade) * (0.75F + 0.25F * pulse);
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

    /** Makes a rune from its sprites (a random one of the ten). */
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
            return new WardRuneParticle(level, x, y, z, this.sprites);
        }
    }
}
