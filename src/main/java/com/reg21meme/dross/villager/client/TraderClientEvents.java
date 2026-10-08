package com.reg21meme.dross.villager.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the trader's renderer (client only). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class TraderClientEvents
{
    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.TRADER.get(), DrossTraderRenderer::new);
    }
}
