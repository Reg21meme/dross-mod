package com.reg21meme.dross.boss.rimefall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import javax.annotation.Nullable;

/**
 * Draws one of Rimefall's animals with GeckoLib, the way the Rimefall gallery draws its statues: the solid parts cut out,
 * then (if it has any) the see-through ice blended over them and the ice's own glow full-bright (the Rimehorn's clear
 * horns). A calf is drawn at {@code babyScale} of a grown one (its head scaled back up by {@link RimefallMobModel}).
 */
public class RimefallMobRenderer<T extends LivingEntity & GeoEntity> extends GeoEntityRenderer<T>
{
    private final RimefallMobModel<T> mobModel;
    private final float babyScale;
    private final float shadow;

    public RimefallMobRenderer(EntityRendererProvider.Context context, RimefallMobModel<T> model, float shadow, float babyScale,
                               boolean ice)
    {
        super(context, model);
        this.mobModel = model;
        this.babyScale = babyScale;
        this.shadow = shadow;
        this.shadowRadius = shadow;
        if (ice)
        {
            this.addRenderLayer(new IceLayer<>(this, false));
            this.addRenderLayer(new IceLayer<>(this, true));
        }
    }

    @Override
    public void render(T animal, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight)
    {
        this.shadowRadius = animal.isBaby() ? this.shadow * this.babyScale : this.shadow;
        super.render(animal, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, T animal, BakedGeoModel model,
                                    boolean isReRender, float partialTick, int packedLight, int packedOverlay)
    {
        float k = animal.isBaby() ? this.babyScale : 1.0F;
        super.scaleModelForRender(widthScale * k, heightScale * k, poseStack, animal, model, isReRender, partialTick,
                packedLight, packedOverlay);
    }

    /** The solid parts: cut out (the ice's texels are holes in this texture), both sides of every face drawn. */
    @Override
    public RenderType getRenderType(T animal, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    /** The see-through ice (blended, lit by the world), or its glow (full-bright). */
    private static class IceLayer<T extends LivingEntity & GeoEntity> extends GeoRenderLayer<T>
    {
        private final boolean glow;

        IceLayer(RimefallMobRenderer<T> renderer, boolean glow)
        {
            super(renderer);
            this.glow = glow;
        }

        @Override
        public void render(PoseStack poseStack, T animal, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay)
        {
            RimefallMobRenderer<T> renderer = (RimefallMobRenderer<T>) this.getRenderer();
            ResourceLocation texture = renderer.mobModel.texture(animal, this.glow ? "_iceglow" : "_ice");
            RenderType type = this.glow ? RenderType.entityTranslucentEmissive(texture) : RenderType.entityTranslucent(texture);
            renderer.reRender(bakedModel, poseStack, bufferSource, animal, type, bufferSource.getBuffer(type), partialTick,
                    this.glow ? LightTexture.FULL_BRIGHT : packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
