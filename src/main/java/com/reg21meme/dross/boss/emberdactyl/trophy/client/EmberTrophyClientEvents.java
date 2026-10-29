package com.reg21meme.dross.boss.emberdactyl.trophy.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the Emberdactyl trophy's block renderer (client only). Its item renderer comes from the item itself. */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class EmberTrophyClientEvents
{
    private EmberTrophyClientEvents()
    {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModBlockEntities.EMBERDACTYL_TROPHY.get(), EmberTrophyBlockRenderer::new);
    }
}
