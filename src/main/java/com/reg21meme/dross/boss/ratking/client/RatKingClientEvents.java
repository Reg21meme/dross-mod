package com.reg21meme.dross.boss.ratking.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModParticles;
import net.minecraft.client.renderer.entity.NoopRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the Rat King's and the rats' renderers, his poison ball's (its poison cloud is only particles, which it makes
 * itself) and the Zzz particle (client only).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RatKingClientEvents
{
    private RatKingClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.RAT_KING.get(), RatKingRenderer::new);
        event.registerEntityRenderer(ModEntities.RAT.get(), RatRenderer::regular);
        event.registerEntityRenderer(ModEntities.ENRAGED_RAT.get(), RatRenderer::enraged);
        event.registerEntityRenderer(ModEntities.PLAGUE_BALL.get(), PlagueBallRenderer::new);
        event.registerEntityRenderer(ModEntities.POISON_CLOUD.get(), NoopRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event)
    {
        event.registerSpriteSet(ModParticles.ZZZ.get(), ZzzParticle.Provider::new);
    }
}
