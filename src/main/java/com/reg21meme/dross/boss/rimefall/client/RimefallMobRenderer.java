package com.reg21meme.dross.boss.rimefall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reg21meme.dross.boss.client.PartLayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;

/**
 * Draws one of Rimefall's animals with GeckoLib, the way the Rimefall gallery draws its statues: the solid parts cut out,
 * then (if it has any) the see-through ice blended over them and the ice's own glow full-bright (the Rimehorn's clear
 * horns). A grown one is drawn at {@code scale} of its model (the Pond Skater smaller than its gallery design), a calf at
 * {@code babyScale} of a grown one (its head scaled back up by {@link RimefallMobModel}).
 */
public class RimefallMobRenderer<T extends LivingEntity & GeoEntity> extends GeoEntityRenderer<T>
{
    private final RimefallMobModel<T> mobModel;
    private final float babyScale;
    private final float scale;
    private final float shadow;

    public RimefallMobRenderer(EntityRendererProvider.Context context, RimefallMobModel<T> model, float shadow, float babyScale,
                               boolean ice)
    {
        this(context, model, shadow, babyScale, 1.0F, ice);
    }

    public RimefallMobRenderer(EntityRendererProvider.Context context, RimefallMobModel<T> model, float shadow, float babyScale,
                               float scale, boolean ice)
    {
        super(context, model);
        this.mobModel = model;
        this.babyScale = babyScale;
        this.scale = scale;
        this.shadow = shadow * scale;
        this.shadowRadius = this.shadow;
        if (ice)
        {
            // the see-through ice (blended, lit by the world) and its glow (full-bright): only the parts made of ice
            // drawn again for them (the Rimehorn's horns: {@link PartLayer})
            this.addRenderLayer(new PartLayer<>(this, animal -> model.texture(animal, "_ice"), RenderType::entityTranslucent,
                    false));
            this.addRenderLayer(new PartLayer<>(this, animal -> model.texture(animal, "_iceglow"),
                    RenderType::entityTranslucentEmissive, true));
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
        float k = (animal.isBaby() ? this.babyScale : 1.0F) * this.scale;
        super.scaleModelForRender(widthScale * k, heightScale * k, poseStack, animal, model, isReRender, partialTick,
                packedLight, packedOverlay);
    }

    /** The solid parts: cut out (the ice's texels are holes in this texture), both sides of every face drawn. */
    @Override
    public RenderType getRenderType(T animal, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }
}
