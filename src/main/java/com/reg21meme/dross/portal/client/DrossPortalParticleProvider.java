package com.reg21meme.dross.portal.client;

import javax.annotation.Nullable;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.PortalParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;

/**
 * Makes the Dross portal swirl particles: the same movement and sprites as vanilla's purple
 * portal particles, but tinted orange.
 */
public class DrossPortalParticleProvider implements ParticleProvider<SimpleParticleType>
{
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
            float brightness = level.random.nextFloat() * 0.6F + 0.4F;
            particle.setColor(brightness, brightness * 0.45F, brightness * 0.08F);
        }
        return particle;
    }
}
