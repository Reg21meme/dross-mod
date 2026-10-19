package com.reg21meme.dross.boss.rimefall.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the Rimefall candidates' renderer (client only). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RimefallClientEvents
{
    private RimefallClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.RIMEFALL_CANDIDATE.get(), RimefallCandidateRenderer::new);
    }
}
