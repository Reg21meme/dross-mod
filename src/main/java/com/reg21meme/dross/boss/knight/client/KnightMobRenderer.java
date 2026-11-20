package com.reg21meme.dross.boss.knight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.client.PartLayer;
import com.reg21meme.dross.boss.knight.ElementalKnight;
import com.reg21meme.dross.boss.knight.KnightAnims;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;

/**
 * Draws a real elemental knight with GeckoLib: its solid parts cut out, then three more passes, each only for a knight
 * that has it and only for the boxes that show anything in it ({@link PartLayer}: until November 19, 2026 each pass
 * drew the whole model again): what glows on it full-bright, its see-through parts blended (ice, crystals), and their
 * glow. While the Wreck Knight's anchor is thrown, the one on his shoulder isn't drawn (bone {@code anchor}).
 */
public class KnightMobRenderer extends GeoEntityRenderer<ElementalKnight>
{
    public KnightMobRenderer(EntityRendererProvider.Context context)
    {
        super(context, new KnightMobModel());
        this.shadowRadius = 0.9F;
        this.addRenderLayer(new PartLayer<>(this, k -> layer(k, 0), RenderType::entityTranslucentEmissive, true));
        this.addRenderLayer(new PartLayer<>(this, k -> layer(k, 1), RenderType::entityTranslucent, false));
        this.addRenderLayer(new PartLayer<>(this, k -> layer(k, 2), RenderType::entityTranslucentEmissive, true));
    }

    /** A knight's texture for pass {@code layer} (0 its glow, 1 its see-through parts, 2 their glow), or null. */
    @Nullable
    private static ResourceLocation layer(ElementalKnight knight, int layer)
    {
        if (!KnightAnims.hasLayer(knight.kind().model, layer))
        {
            return null;
        }
        KnightMobModel.Files files = KnightMobModel.files(knight.kind());
        return layer == 0 ? files.glow() : layer == 1 ? files.clear() : files.clearGlow();
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
}
