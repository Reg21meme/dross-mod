package com.reg21meme.dross.boss.cinder.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.fire.FireCandidate;
import com.reg21meme.dross.boss.fire.client.FireCandidateRenderer;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Registers the Pallbearer's and the Cinder Hermit's renderer (client only): the fire models' own,
 * {@link FireCandidateRenderer} (their look, glow, moving flames and animations; they're fire candidates that live).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class CinderMobClientEvents
{
    private CinderMobClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.PALLBEARER.get(), fire());
        event.registerEntityRenderer(ModEntities.CINDER_HERMIT.get(), fire());
    }

    /** The fire models' renderer, for an entity type of one of its kinds. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Entity> EntityRendererProvider<T> fire()
    {
        EntityRendererProvider<FireCandidate> provider = FireCandidateRenderer::new;
        return (EntityRendererProvider) provider;
    }
}
