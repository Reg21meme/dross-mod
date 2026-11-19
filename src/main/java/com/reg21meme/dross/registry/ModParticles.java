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

    // Boss area: the "Z" that floats up from a sleeping Rat King's head
    public static final RegistryObject<SimpleParticleType> ZZZ = PARTICLE_TYPES.register("zzz", () -> new SimpleParticleType(false));

    // Dimension area: the edge of a land the player's Oracle quest hasn't opened yet (dimension/ward/client/LandWardEdge):
    // thin streaks of light rising off the border, and the glowing runes drifting up them
    public static final RegistryObject<SimpleParticleType> WARD_STREAK = PARTICLE_TYPES.register("ward_streak", () -> new SimpleParticleType(false));
    public static final RegistryObject<SimpleParticleType> WARD_RUNE = PARTICLE_TYPES.register("ward_rune", () -> new SimpleParticleType(false));
    // ...and its shards, falling when the barrier breaks (dimension/ward/client/LandWardFall).
    public static final RegistryObject<SimpleParticleType> WARD_SHARD = PARTICLE_TYPES.register("ward_shard", () -> new SimpleParticleType(true));
    // ...and the cracks that run across it just before (dimension/ward/client/WardCrackParticle).
    public static final RegistryObject<SimpleParticleType> WARD_CRACK = PARTICLE_TYPES.register("ward_crack", () -> new SimpleParticleType(true));

    private ModParticles() {}
}
