package com.reg21meme.dross.boss.emberdactyl.client;

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
 * Registers the fighting Emberdactyl's, its young's and its egg's renderer (the fire models' own, {@link FireCandidateRenderer}:
 * they're fire candidates that live) and the fire tornadoes' (client only).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EmberClientEvents
{
    private EmberClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.EMBERDACTYL.get(), fire());
        event.registerEntityRenderer(ModEntities.EMBERYOUNG.get(), fire());
        event.registerEntityRenderer(ModEntities.EMBERDACTYL_EGG.get(), fire());
        event.registerEntityRenderer(ModEntities.FIRE_TORNADO.get(), FireTornadoRenderer::new);
    }

    /** The fire models' renderer, for an entity type of one of its kinds. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static <T extends Entity> EntityRendererProvider<T> fire()
    {
        EntityRendererProvider<FireCandidate> provider = FireCandidateRenderer::new;
        return (EntityRendererProvider) provider;
    }
}
