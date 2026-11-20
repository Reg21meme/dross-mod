package com.reg21meme.dross.boss.knight.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Registers the elemental knights' renderers, on show and the real ones, and their anchor's and fireballs' (client only). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class KnightClientEvents
{
    private KnightClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.KNIGHT_CANDIDATE.get(), KnightCandidateRenderer::new);
        ModEntities.KNIGHTS.values().forEach(type -> event.registerEntityRenderer(type.get(), KnightMobRenderer::new));
        event.registerEntityRenderer(ModEntities.KNIGHT_ANCHOR.get(), KnightAnchorRenderer::new);
        event.registerEntityRenderer(ModEntities.KNIGHT_FIREBALL.get(), KnightFireballRenderer::new);
    }
}
