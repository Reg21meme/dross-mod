package com.reg21meme.dross.boss.huntsman.trophy.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModBlockEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the Huntsman trophy's block renderer (client only). Its item renderer comes from the item itself. */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HuntsmanTrophyClientEvents
{
    private HuntsmanTrophyClientEvents()
    {
    }

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerBlockEntityRenderer(ModBlockEntities.HUNTSMAN_TROPHY.get(), HuntsmanTrophyBlockRenderer::new);
    }
}
