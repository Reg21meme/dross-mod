package com.reg21meme.dross.portal.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The electric blue swirl drawn over the screen while you stand in a Dross portal.
 * Vanilla draws its purple swirl only for nether portals, so we keep our own fade-in/fade-out
 * strength (same speeds as vanilla) and draw the recolored blue portal sprite with it.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, value = Dist.CLIENT)
public final class DrossPortalOverlay
{
    private static float intensity;
    private static float oIntensity;

    private DrossPortalOverlay() {}

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null)
        {
            intensity = 0.0F;
            oIntensity = 0.0F;
            return;
        }
        if (mc.isPaused())
        {
            return;
        }

        oIntensity = intensity;
        float change;
        if (isInsideDrossPortal(player))
        {
            if (mc.screen != null && !mc.screen.isPauseScreen() && !(mc.screen instanceof DeathScreen))
            {
                if (mc.screen instanceof AbstractContainerScreen)
                {
                    player.closeContainer();
                }
                mc.setScreen(null);
            }
            if (intensity == 0.0F)
            {
                mc.getSoundManager().play(SimpleSoundInstance.forLocalAmbience(SoundEvents.PORTAL_TRIGGER, player.getRandom().nextFloat() * 0.4F + 0.8F, 0.25F));
            }
            change = 0.0125F;
        }
        else
        {
            change = intensity > 0.0F ? -0.05F : 0.0F;
        }
        intensity = Mth.clamp(intensity + change, 0.0F, 1.0F);
    }

    private static boolean isInsideDrossPortal(LocalPlayer player)
    {
        AABB box = player.getBoundingBox().deflate(1.0E-7D);
        return player.level().getBlockStatesIfLoaded(box).anyMatch(state -> state.is(ModBlocks.DROSS_PORTAL.get()));
    }

    /** Same drawing as vanilla's {@code Gui#renderPortalOverlay}, but with the Dross portal sprite. */
    public static void render(ForgeGui gui, GuiGraphics guiGraphics, float partialTick, int screenWidth, int screenHeight)
    {
        float strength = Mth.lerp(partialTick, oIntensity, intensity);
        if (strength <= 0.0F)
        {
            return;
        }
        if (strength < 1.0F)
        {
            strength *= strength;
            strength *= strength;
            strength = strength * 0.8F + 0.2F;
        }

        gui.setupOverlayRenderState(true, false);
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, strength);
        TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer().getBlockModelShaper()
                .getParticleIcon(ModBlocks.DROSS_PORTAL.get().defaultBlockState());
        guiGraphics.blit(0, 0, -90, screenWidth, screenHeight, sprite);
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        guiGraphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
    }
}
