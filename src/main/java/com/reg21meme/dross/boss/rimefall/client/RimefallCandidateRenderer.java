package com.reg21meme.dross.boss.rimefall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.rimefall.RimefallCandidate;
import com.reg21meme.dross.boss.rimefall.RimefallDesigns;
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
 * Draws a Rimefall candidate with GeckoLib, in four passes, in this order (the textures are made to match by
 * {@code tools/rimefall/rimekit/texture.py}):
 * <ol>
 *   <li>its solid parts ({@code <id>.png}, cut out, lit by the world; the see-through ice is a hole here);</li>
 *   <li>what glows on them ({@code <id>_glow.png}, full-bright): eyes, a glowing heart, a warm window. Drawn before the
 *       ice, so a glowing heart inside a see-through body shines through it;</li>
 *   <li>the see-through ice ({@code <id>_ice.png}, blended, lit by the world);</li>
 *   <li>what glows on the ice itself ({@code <id>_iceglow.png}, full-bright): the aurora's curtains, glowing cracks.</li>
 * </ol>
 * Each pass after the first only runs for designs that have that texture. A design's real Minecraft blocks (the Fisher's
 * bait cabin) are drawn with the solid parts ({@link RimefallBlocksLayer}). The shadow under it is sized to the design.
 */
public class RimefallCandidateRenderer extends GeoEntityRenderer<RimefallCandidate>
{
    public RimefallCandidateRenderer(EntityRendererProvider.Context context)
    {
        super(context, new RimefallCandidateModel());
        this.addRenderLayer(new RimefallBlocksLayer(this));
        this.addRenderLayer(new Pass(this, Kind.GLOW));
        this.addRenderLayer(new Pass(this, Kind.ICE));
        this.addRenderLayer(new Pass(this, Kind.ICE_GLOW));
    }

    @Override
    public void render(RimefallCandidate candidate, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight)
    {
        this.shadowRadius = candidate.getDesign().shadow();
        super.render(candidate, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** The solid parts: cut out (the ice's texels are holes in this texture). */
    @Override
    public RenderType getRenderType(RimefallCandidate candidate, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    private enum Kind
    {
        GLOW, ICE, ICE_GLOW
    }

    /** One of the passes after the solid parts. */
    private static class Pass extends GeoRenderLayer<RimefallCandidate>
    {
        private final Kind kind;

        Pass(RimefallCandidateRenderer renderer, Kind kind)
        {
            super(renderer);
            this.kind = kind;
        }

        @Override
        public void render(PoseStack poseStack, RimefallCandidate candidate, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay)
        {
            RimefallDesigns.Design design = candidate.getDesign();
            RimefallCandidateModel.Files files = RimefallCandidateModel.files(candidate);
            RenderType type;
            int light;
            switch (this.kind)
            {
                case GLOW ->
                {
                    if (!design.hasGlow())
                    {
                        return;
                    }
                    type = RenderType.entityTranslucentEmissive(files.glow());
                    light = LightTexture.FULL_BRIGHT;
                }
                case ICE ->
                {
                    if (!design.hasIce())
                    {
                        return;
                    }
                    type = RenderType.entityTranslucent(files.ice());
                    light = packedLight;
                }
                default ->
                {
                    if (!design.hasIceGlow())
                    {
                        return;
                    }
                    type = RenderType.entityTranslucentEmissive(files.iceGlow());
                    light = LightTexture.FULL_BRIGHT;
                }
            }
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, candidate, type, bufferSource.getBuffer(type),
                    partialTick, light, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
