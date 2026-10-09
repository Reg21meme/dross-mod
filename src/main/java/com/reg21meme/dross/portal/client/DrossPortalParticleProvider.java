package com.reg21meme.dross.portal.client;

import com.reg21meme.dross.DrossColors;
import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Makes the Dross portal swirl particles: the same movement and sprites as vanilla's purple
 * portal particles, but tinted electric blue ({@link DrossColors#PORTAL_PARTICLE}).
 */
public class DrossPortalParticleProvider implements ParticleProvider<SimpleParticleType>
{
    /** Each particle gets a random brightness between these two, like vanilla's portal particles. */
    private static final float MIN_BRIGHTNESS = 0.4F;
    private static final float MAX_BRIGHTNESS = 1.0F;

    private final PortalParticle.Provider vanilla;

    public DrossPortalParticleProvider(SpriteSet sprites)
    {
        this.vanilla = new PortalParticle.Provider(sprites);
    }

    @Nullable
    @Override
    public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                   double xSpeed, double ySpeed, double zSpeed)
    {
        Particle particle = this.vanilla.createParticle(type, level, x, y, z, xSpeed, ySpeed, zSpeed);
        if (particle != null)
        {
            float brightness = MIN_BRIGHTNESS + level.random.nextFloat() * (MAX_BRIGHTNESS - MIN_BRIGHTNESS);
            int color = DrossColors.PORTAL_PARTICLE;
            particle.setColor(DrossColors.red(color) * brightness, DrossColors.green(color) * brightness, DrossColors.blue(color) * brightness);
        }
        return particle;
    }
}
