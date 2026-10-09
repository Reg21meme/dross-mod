package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModParticles
{
    public static final DeferredRegister<ParticleType<?>> PARTICLE_TYPES = DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, Dross.MODID);

    // Portal area: electric blue swirl particles around the Dross portal
    public static final RegistryObject<SimpleParticleType> DROSS_PORTAL = PARTICLE_TYPES.register("dross_portal", () -> new SimpleParticleType(false));

    private ModParticles() {}
}
