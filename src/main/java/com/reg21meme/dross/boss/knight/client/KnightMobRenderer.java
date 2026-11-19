package com.reg21meme.dross.boss.knight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.knight.ElementalKnight;
import com.reg21meme.dross.boss.knight.KnightAnims;
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
 * Draws a real elemental knight with GeckoLib in four passes, like the ones on show (KnightCandidateRenderer): its solid
 * parts cut out, what glows on them full-bright, its see-through parts blended (ice, crystals), and their glow; each
 * pass after the first only for a knight that has it. While the Wreck Knight's anchor is thrown, the one on his shoulder
 * isn't drawn (bone {@code anchor}).
 */
public class KnightMobRenderer extends GeoEntityRenderer<ElementalKnight>
{
    public KnightMobRenderer(EntityRendererProvider.Context context)
    {
        super(context, new KnightMobModel());
        this.shadowRadius = 0.9F;
        for (int layer = 0; layer < 3; layer++)
        {
            this.addRenderLayer(new Pass(this, layer));
        }
    }

    @Override
    public void preRender(PoseStack poseStack, ElementalKnight knight, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        model.getBone("anchor").ifPresent(bone -> bone.setHidden(knight.isAnchorOut()));
        super.preRender(poseStack, knight, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    @Override
    public RenderType getRenderType(ElementalKnight knight, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    /** One of the passes after the solid parts: 0 the glow, 1 the see-through parts, 2 their glow. */
    private static class Pass extends GeoRenderLayer<ElementalKnight>
    {
        private final int layer;

        Pass(KnightMobRenderer renderer, int layer)
        {
            super(renderer);
            this.layer = layer;
        }

        @Override
        public void render(PoseStack poseStack, ElementalKnight knight, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay)
        {
            if (!KnightAnims.hasLayer(knight.kind().model, this.layer))
            {
                return;
            }
            KnightMobModel.Files files = KnightMobModel.files(knight.kind());
            RenderType type;
            int light;
            switch (this.layer)
            {
                case 0 ->
                {
                    type = RenderType.entityTranslucentEmissive(files.glow());
                    light = LightTexture.FULL_BRIGHT;
                }
                case 1 ->
                {
                    type = RenderType.entityTranslucent(files.clear());
                    light = packedLight;
                }
                default ->
                {
                    type = RenderType.entityTranslucentEmissive(files.clearGlow());
                    light = LightTexture.FULL_BRIGHT;
                }
            }
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, knight, type, bufferSource.getBuffer(type),
                    partialTick, light, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
