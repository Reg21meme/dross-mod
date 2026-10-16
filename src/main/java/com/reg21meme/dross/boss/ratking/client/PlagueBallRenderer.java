package com.reg21meme.dross.boss.ratking.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.ratking.PlagueBall;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws the Rat King's poison ball with GeckoLib: its model ({@code geo/entity/plague_ball/plague_ball.geo.json},
 * tumbling with {@code animation.plague_ball.spin}), lit by the world, with its faint glow
 * ({@code plague_ball_glow.png}: alpha is the strength) full-bright on top. It doesn't turn to face where it flies.
 */
public class PlagueBallRenderer extends GeoEntityRenderer<PlagueBall>
{
    private static final ResourceLocation MODEL = new ResourceLocation(Dross.MODID, "geo/entity/plague_ball/plague_ball.geo.json");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(Dross.MODID,
            "animations/entity/plague_ball/plague_ball.animation.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Dross.MODID, "textures/entity/plague_ball/plague_ball.png");
    private static final ResourceLocation GLOW = new ResourceLocation(Dross.MODID, "textures/entity/plague_ball/plague_ball_glow.png");
    /** Its shadow (blocks). */
    private static final float SHADOW_RADIUS = 0.25F;

    public PlagueBallRenderer(EntityRendererProvider.Context context)
    {
        super(context, new Model());
        this.shadowRadius = SHADOW_RADIUS;
        this.addRenderLayer(new GlowLayer(this));
    }

    /** The ball's files (one design). */
    private static class Model extends GeoModel<PlagueBall>
    {
        @Override
        public ResourceLocation getModelResource(PlagueBall ball)
        {
            return MODEL;
        }

        @Override
        public ResourceLocation getTextureResource(PlagueBall ball)
        {
            return TEXTURE;
        }

        @Override
        public ResourceLocation getAnimationResource(PlagueBall ball)
        {
            return ANIMATIONS;
        }
    }

    /** Its glow at full brightness (the texture's alpha is how strongly each part glows). */
    private static class GlowLayer extends GeoRenderLayer<PlagueBall>
    {
        GlowLayer(PlagueBallRenderer renderer)
        {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, PlagueBall ball, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
        {
            RenderType glow = RenderType.entityTranslucentEmissive(GLOW);
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, ball, glow, bufferSource.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
