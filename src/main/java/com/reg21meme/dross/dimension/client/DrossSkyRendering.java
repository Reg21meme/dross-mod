package com.reg21meme.dross.dimension.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import java.util.Map;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.slf4j.Logger;

/**
 * Two touches to the Dross's night (client only; {@link DrossSkyEffects} is the rest):
 * <ul>
 *   <li><b>A glow over the night sky.</b> Minecraft draws the night sky black overhead, fading into the fog colour only
 *       just above the horizon. Right after the sky (before any land), a see-through dome is drawn round the camera in the
 *       fog's own colour mixed with {@code DrossColors.DROSS_SKY_GLOW}: strongest at the horizon (where it matches the
 *       fog), fainter overhead, so the whole sky fades from the biome's fog colour to a dark blue instead of black. The
 *       stars still shine through. It never replaces Minecraft's sky, only lies over it.</li>
 *   <li><b>Thicker fog in some biomes</b>: Blightwood thick and sickly, the Nether Scar and the Cinder Wastes smoky,
 *       Rimefall hazy ({@link #BIOME_FOG}). The fog eases in and out over {@value #FOG_EASE_SECONDS} seconds as you walk
 *       between biomes, so it never jumps.</li>
 * </ul>
 * Both only run in the Dross, each has an on/off switch, and if one ever throws it's switched off for the rest of the
 * session (one error in the log) and Minecraft's own look is used.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class DrossSkyRendering
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // Switches
    public static final boolean SKY_GLOW_ENABLED = true;
    public static final boolean BIOME_FOG_ENABLED = true;

    // The glow
    /** How much of DROSS_SKY_GLOW is mixed into the fog colour for the glow (0 = the fog colour itself). */
    private static final float GLOW_MIX = 0.35F;
    /** The glow's opacity at the horizon and straight overhead (0 = none, 1 = solid). */
    private static final float GLOW_ALPHA_HORIZON = 0.6F;
    private static final float GLOW_ALPHA_ZENITH = 0.28F;
    /** The dome's size (blocks; it's drawn round the camera, behind everything) and how finely it's cut. */
    private static final float DOME_RADIUS = 50.0F;
    private static final int DOME_RINGS = 12;
    private static final int DOME_SEGMENTS = 32;

    // The fog
    /**
     * Per biome: how far the fog reaches (times the normal distance, 1 = normal) and where it starts (times that
     * distance; 0 = right in front of you). Biomes not listed keep Minecraft's normal fog.
     */
    private static final Map<ResourceKey<Biome>, float[]> BIOME_FOG = Map.of(
            DrossBiomes.BLIGHTWOOD, new float[] {0.35F, 0.0F},
            DrossBiomes.NETHER_SCAR, new float[] {0.6F, 0.2F},
            DrossBiomes.CINDER_WASTES, new float[] {0.7F, 0.3F},
            DrossBiomes.RIMEFALL, new float[] {0.8F, 0.15F});
    /** Fog changes ease toward the new biome's over about this many seconds. */
    private static final float FOG_EASE_SECONDS = 2.5F;

    private static boolean glowFailed;
    private static boolean fogFailed;
    /** The fog's current reach and start factors (eased), and when they were last updated. */
    private static float fogFar = 1.0F;
    private static float fogNear = -1.0F;
    private static long lastFogMillis;

    private DrossSkyRendering() {}

    private static boolean inDross()
    {
        ClientLevel level = Minecraft.getInstance().level;
        return level != null && level.dimension().equals(ModDimensions.DROSS_LEVEL);
    }

    // ------------------------------------------------------------------ the glow

    @SubscribeEvent
    public static void onRenderStage(RenderLevelStageEvent event)
    {
        if (!SKY_GLOW_ENABLED || glowFailed || !DrossSkyEffects.ENABLED || event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY || !inDross())
        {
            return;
        }
        try
        {
            drawGlow(event.getPoseStack());
        }
        catch (Throwable t)
        {
            glowFailed = true;
            LOGGER.error("Dross sky: drawing the glow over the night sky failed; it's switched off for this session", t);
            try
            {
                restoreState();
            }
            catch (Throwable ignored)
            {
                // nothing more to do
            }
        }
    }

    private static void drawGlow(PoseStack poseStack)
    {
        float[] fog = RenderSystem.getShaderFogColor();
        int glow = DrossColors.DROSS_SKY_GLOW;
        float r = Mth.lerp(GLOW_MIX, fog[0], DrossColors.red(glow));
        float g = Mth.lerp(GLOW_MIX, fog[1], DrossColors.green(glow));
        float b = Mth.lerp(GLOW_MIX, fog[2], DrossColors.blue(glow));

        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        Matrix4f pose = poseStack.last().pose();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        for (int ring = 0; ring < DOME_RINGS; ring++)
        {
            float lo = (float) (ring * Math.PI / 2.0D / DOME_RINGS);
            float hi = (float) ((ring + 1) * Math.PI / 2.0D / DOME_RINGS);
            float aLo = alphaAt(lo);
            float aHi = alphaAt(hi);
            for (int seg = 0; seg < DOME_SEGMENTS; seg++)
            {
                float a0 = (float) (seg * 2.0D * Math.PI / DOME_SEGMENTS);
                float a1 = (float) ((seg + 1) * 2.0D * Math.PI / DOME_SEGMENTS);
                vertex(builder, pose, lo, a0, r, g, b, aLo);
                vertex(builder, pose, lo, a1, r, g, b, aLo);
                vertex(builder, pose, hi, a1, r, g, b, aHi);
                vertex(builder, pose, hi, a0, r, g, b, aHi);
            }
        }
        BufferUploader.drawWithShader(builder.end());
        restoreState();
    }

    /** The glow's opacity at an elevation (radians above the horizon). */
    private static float alphaAt(float elevation)
    {
        float t = (float) Math.sqrt(Math.sin(elevation));
        return Mth.lerp(t, GLOW_ALPHA_HORIZON, GLOW_ALPHA_ZENITH);
    }

    private static void vertex(BufferBuilder builder, Matrix4f pose, float elevation, float azimuth, float r, float g, float b, float a)
    {
        float horizontal = Mth.cos(elevation) * DOME_RADIUS;
        builder.vertex(pose, horizontal * Mth.cos(azimuth), Mth.sin(elevation) * DOME_RADIUS, horizontal * Mth.sin(azimuth))
                .color(r, g, b, a).endVertex();
    }

    /** What Minecraft's sky leaves behind: blending off, depth writing on, culling on, a white shader colour. */
    private static void restoreState()
    {
        RenderSystem.enableCull();
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.depthMask(true);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
    }

    // ------------------------------------------------------------------ the fog

    @SubscribeEvent
    public static void onRenderFog(ViewportEvent.RenderFog event)
    {
        if (!BIOME_FOG_ENABLED || fogFailed || !DrossSkyEffects.ENABLED || event.getMode() != FogRenderer.FogMode.FOG_TERRAIN
                || event.getType() != FogType.NONE)
        {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || !level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            fogFar = 1.0F;
            fogNear = -1.0F;
            return;
        }
        try
        {
            Holder<Biome> biome = level.getBiome(event.getCamera().getBlockPosition());
            float[] target = biome.unwrapKey().map(BIOME_FOG::get).orElse(null);
            float normalNear = event.getNearPlaneDistance() / Math.max(1.0F, event.getFarPlaneDistance());
            float wantFar = target == null ? 1.0F : target[0];
            float wantNear = target == null ? normalNear : target[1];
            long now = Util.getMillis();
            float dt = lastFogMillis == 0L ? 1.0E6F : (now - lastFogMillis) / 1000.0F;
            lastFogMillis = now;
            float step = 1.0F - (float) Math.exp(-dt / FOG_EASE_SECONDS);
            fogFar = Mth.lerp(step, fogFar, wantFar);
            fogNear = fogNear < 0.0F ? wantNear : Mth.lerp(step, fogNear, wantNear);
            if (Math.abs(fogFar - 1.0F) < 0.002F && Math.abs(fogNear - normalNear) < 0.002F)
            {
                return; // Minecraft's own fog
            }
            float far = event.getFarPlaneDistance() * fogFar;
            event.setFarPlaneDistance(far);
            event.setNearPlaneDistance(Math.min(far * Mth.clamp(fogNear, 0.0F, 1.0F), event.getNearPlaneDistance()));
            event.setCanceled(true); // required for the new distances to be used
        }
        catch (RuntimeException e)
        {
            fogFailed = true;
            LOGGER.error("Dross sky: changing the fog failed; it's switched off for this session", e);
        }
    }
}
