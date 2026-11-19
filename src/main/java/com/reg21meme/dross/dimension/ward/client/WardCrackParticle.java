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
 * CLIENT ONLY. A speck of a crack running across a land's barrier just before it breaks ({@code dross:ward_crack};
 * {@link LandWardFall}): it hangs still where the crack has reached, glowing almost white
 * ({@link DrossColors#LAND_WARD_CRACK}) and flickering, until the barrier shatters (its life is the {@code xd} it's made
 * with, in ticks), then it's gone. A row of them makes a glowing jagged line.
 */
public class WardCrackParticle extends TextureSheetParticle
{
    /** Its size (blocks across), at least and at most. */
    public static final float MIN_SIZE = 0.18F;
    public static final float MAX_SIZE = 0.32F;
    /** Its brightest (0 to 1), and how much it flickers. */
    public static final float MAX_ALPHA = 1.0F;
    public static final float FLICKER = 0.35F;

    protected WardCrackParticle(ClientLevel level, double x, double y, double z, int life, SpriteSet sprites)
    {
        super(level, x, y, z);
        this.xd = 0.0D;
        this.yd = 0.0D;
        this.zd = 0.0D;
        this.gravity = 0.0F;
        this.hasPhysics = false;
        this.lifetime = Math.max(2, life);
        this.quadSize = Mth.nextFloat(this.random, MIN_SIZE, MAX_SIZE) / 2.0F;
        int color = DrossColors.LAND_WARD_CRACK;
        this.rCol = (color >> 16 & 0xFF) / 255.0F;
        this.gCol = (color >> 8 & 0xFF) / 255.0F;
        this.bCol = (color & 0xFF) / 255.0F;
        this.pickSprite(sprites);
        this.alpha = MAX_ALPHA;
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
        this.alpha = MAX_ALPHA * (1.0F - FLICKER * this.random.nextFloat());
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

    /** Makes a speck; its life (ticks) is the {@code xd} it's made with. */
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
            return new WardCrackParticle(level, x, y, z, (int) Math.round(xd), this.sprites);
        }
    }
}
