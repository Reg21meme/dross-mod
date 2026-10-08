package com.reg21meme.dross.portal.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModParticles;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only registrations for the portal: the orange particle and the orange screen swirl. */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class DrossPortalClientSetup
{
    private DrossPortalClientSetup() {}

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event)
    {
        event.registerSpriteSet(ModParticles.DROSS_PORTAL.get(), DrossPortalParticleProvider::new);
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event)
    {
        event.registerAbove(VanillaGuiOverlay.PORTAL.id(), "dross_portal", DrossPortalOverlay::render);
    }
}
