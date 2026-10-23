package com.reg21meme.dross.boss.huntsman.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.AlphaMoose;
import com.reg21meme.dross.boss.huntsman.HuntAnims;
import com.reg21meme.dross.boss.huntsman.Huntsman;
import com.reg21meme.dross.boss.huntsman.spear.SpearTuning;
import com.reg21meme.dross.boss.huntsman.spear.client.SpearRenderer;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.client.renderer.entity.ThrownItemRenderer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the Huntsman's and the Alpha Moose's renderers (client only): the Huntsman wears his riding model in the
 * saddle and his own on foot; his bola, his thrown spear and a player's thrown Moonsilver Spear. */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class HuntsmanClientEvents
{
    private HuntsmanClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.HUNTSMAN.get(), context -> new HuntRenderer<>(context,
                new HuntModel<Huntsman>(h -> h.isMounted() ? HuntAnims.RIDER : HuntAnims.FOOT), 0.8F));
        event.registerEntityRenderer(ModEntities.ALPHA_MOOSE.get(), context -> new HuntRenderer<>(context,
                new HuntModel<AlphaMoose>(m -> m.isRidden() ? HuntAnims.WILD : HuntAnims.MOOSE), 2.0F));
        // his bola flies as its item; his spear, and a player's Moonsilver Spear, with the Moonsilver Spear's model
        event.registerEntityRenderer(ModEntities.BOLA.get(), ThrownItemRenderer::new);
        event.registerEntityRenderer(ModEntities.HUNT_SPEAR.get(),
                context -> new SpearRenderer<>(context, SpearTuning.HUNTSMAN_SPEAR_SCALE));
        event.registerEntityRenderer(ModEntities.MOONSILVER_SPEAR.get(), context -> new SpearRenderer<>(context, 1.0F));
    }
}
