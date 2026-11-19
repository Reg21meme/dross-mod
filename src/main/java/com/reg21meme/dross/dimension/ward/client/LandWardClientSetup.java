package com.reg21meme.dross.dimension.ward.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** CLIENT ONLY. Registers the land wards' two particles: the curtain's streaks and its runes ({@link LandWardEdge}). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class LandWardClientSetup
{
    private LandWardClientSetup() {}

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event)
    {
        event.registerSpriteSet(ModParticles.WARD_STREAK.get(), WardStreakParticle.Provider::new);
        event.registerSpriteSet(ModParticles.WARD_RUNE.get(), WardRuneParticle.Provider::new);
        event.registerSpriteSet(ModParticles.WARD_SHARD.get(), WardShardParticle.Provider::new);
    }
}
