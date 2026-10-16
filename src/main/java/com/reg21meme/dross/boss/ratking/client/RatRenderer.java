package com.reg21meme.dross.boss.ratking.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.ratking.Rat;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/** Draws a rat (regular or enraged) with GeckoLib, its glowing parts (red eyes, boils) full-bright on top. */
public class RatRenderer extends GeoEntityRenderer<Rat>
{
    private static final float REGULAR_SHADOW = 0.3F;
    private static final float ENRAGED_SHADOW = 0.45F;

    public RatRenderer(EntityRendererProvider.Context context, boolean enraged)
    {
        super(context, new RatModel());
        this.shadowRadius = enraged ? ENRAGED_SHADOW : REGULAR_SHADOW;
        this.addRenderLayer(new GlowLayer(this));
    }

    public static RatRenderer regular(EntityRendererProvider.Context context)
    {
        return new RatRenderer(context, false);
    }

    public static RatRenderer enraged(EntityRendererProvider.Context context)
    {
        return new RatRenderer(context, true);
    }

    private static class GlowLayer extends GeoRenderLayer<Rat>
    {
        GlowLayer(RatRenderer renderer)
        {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, Rat rat, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
        {
            if (!rat.getDesign().hasGlow())
            {
                return;
            }
            RenderType glow = RenderType.entityTranslucentEmissive(RatModel.files(rat).glow());
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, rat, glow, bufferSource.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
