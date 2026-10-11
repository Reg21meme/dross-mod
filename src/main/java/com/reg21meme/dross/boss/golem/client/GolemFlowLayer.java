package com.reg21meme.dross.boss.golem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.golem.CinderColossus;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The lava poured over a Cinder Colossus's shell when its volcano erupts: its {@code <id>_flow.png} texture, drawn
 * over the model unlit (it glows in the dark).
 * <p>
 * The flow texture's alpha says how soon the lava reaches each pixel (1 - 0.25 r, r = 0 at the crater to 1 farthest
 * away, at most 254/255). It's drawn with Minecraft's dissolve shader ({@link RenderType#dragonExplosionAlpha}, the
 * one the ender dragon's death uses), which drops every pixel whose alpha is below the vertex alpha. Lowering the
 * vertex alpha from 1 to 0.75 as {@link CinderColossus#flowProgress} goes from 0 to 1 pours the lava down from the
 * crater over the body. The lava pools in the craters and the eruption's blast column are fully opaque (alpha 255), so
 * at progress 0 they're all that shows: the craters always hold bright lava, even while the golem is dormant, and the
 * blast is bright however dim its glow still is. While it dies, the poured lava is drawn by the emissive layer
 * instead and fades out with the rest of its glow.
 */
public class GolemFlowLayer extends GeoRenderLayer<CinderColossus>
{
    /** The flow texture's alphas run from 1 (at the crater) down to 1 - this (farthest away). */
    private static final float FLOW_ALPHA_RANGE = 0.25F;

    private final CinderColossusRenderer renderer;

    public GolemFlowLayer(CinderColossusRenderer renderer)
    {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(PoseStack poseStack, CinderColossus golem, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
    {
        float progress = golem.flowProgress(partialTick);
        if (golem.getAction().isDead())
        {
            // Cooling: fade the lava out with the rest of its glow (the statue layer then covers it). A golem that
            // dies before its eruption has no poured lava; its crater pools cool with the glow layer.
            float glow = golem.glowStrength(partialTick);
            if (progress <= 0.0F || glow < 0.01F)
            {
                return;
            }
            RenderType fading = RenderType.entityTranslucentEmissive(CinderColossusModel.files(golem).flow());
            this.renderer.drawGlow(() -> this.getRenderer().reRender(bakedModel, poseStack, bufferSource, golem, fading,
                    bufferSource.getBuffer(fading), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, glow));
            return;
        }
        float threshold = 1.0F - FLOW_ALPHA_RANGE * Math.min(1.0F, progress);
        RenderType pour = RenderType.dragonExplosionAlpha(CinderColossusModel.files(golem).flow());
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, golem, pour, bufferSource.getBuffer(pour), partialTick,
                LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, threshold);
    }
}
