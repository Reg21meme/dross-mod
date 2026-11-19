package com.reg21meme.dross.boss.giant.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the Silverwood Giant's renderer and its boulder's (client only). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class GiantClientEvents
{
    private GiantClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.SILVERWOOD_GIANT.get(), GiantRenderer::new);
        event.registerEntityRenderer(ModEntities.GIANT_BOULDER.get(), GiantBoulderRenderer::new);
    }
}
