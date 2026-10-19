package com.reg21meme.dross.boss.huntsman.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.huntsman.HuntMob;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import javax.annotation.Nullable;

/**
 * Draws the Huntsman or the Alpha Moose the way the Rimefall gallery drew their statues: the solid parts cut out, their
 * glowing bits full-bright (the Huntsman's eyes under the wolf's hood, the moose's eyes), then the see-through ice
 * blended over them (his spear's head, the moose's icicles and arrow fletchings) and the ice's own glow.
 */
public class HuntRenderer<T extends HuntMob> extends GeoEntityRenderer<T>
{
    private final HuntModel<T> huntModel;

    public HuntRenderer(EntityRendererProvider.Context context, HuntModel<T> model, float shadow)
    {
        super(context, model);
        this.huntModel = model;
        this.shadowRadius = shadow;
        this.addRenderLayer(new Layer<>(this, "_glow", true, false));
        this.addRenderLayer(new Layer<>(this, "_ice", false, true));
        this.addRenderLayer(new Layer<>(this, "_iceglow", true, true));
    }

    @Override
    public RenderType getRenderType(T mob, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    private static class Layer<T extends HuntMob> extends GeoRenderLayer<T>
    {
        private final String suffix;
        private final boolean glow;
        private final boolean blend;

        Layer(HuntRenderer<T> renderer, String suffix, boolean glow, boolean blend)
        {
            super(renderer);
            this.suffix = suffix;
            this.glow = glow;
            this.blend = blend;
        }

        @Override
        public void render(PoseStack poseStack, T mob, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay)
        {
            HuntRenderer<T> renderer = (HuntRenderer<T>) this.getRenderer();
            ResourceLocation texture = renderer.huntModel.texture(mob, this.suffix);
            RenderType type = this.glow ? RenderType.entityTranslucentEmissive(texture)
                    : this.blend ? RenderType.entityTranslucent(texture) : RenderType.entityCutoutNoCull(texture);
            renderer.reRender(bakedModel, poseStack, bufferSource, mob, type, bufferSource.getBuffer(type), partialTick,
                    this.glow ? LightTexture.FULL_BRIGHT : packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
