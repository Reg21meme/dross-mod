package com.reg21meme.dross.boss.knight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.ShowcaseOnly;
import com.reg21meme.dross.boss.knight.KnightCandidate;
import com.reg21meme.dross.boss.knight.KnightDesigns;
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
 * Draws an elemental knight on show with GeckoLib, in four passes, in this order (the textures are made to match by the
 * Rimefall kit's {@code tools/rimefall/rimekit/texture.py}, which {@code tools/knights/build.py} uses):
 * <ol>
 *   <li>its solid parts ({@code <id>.png}, cut out, lit by the world; what you can see through is a hole here);</li>
 *   <li>what glows on them ({@code <id>_glow.png}, full-bright): eyes, lava, lightning, runes. Drawn before the
 *       see-through parts, so what glows behind them shines through;</li>
 *   <li>the see-through parts ({@code <id>_clear.png}, blended, lit by the world): crystals, glass, flames;</li>
 *   <li>what glows on the see-through parts ({@code <id>_clearglow.png}, full-bright).</li>
 * </ol>
 * Each pass after the first only runs for designs that have that texture. The shadow under it is sized to the design.
 */
public class KnightCandidateRenderer extends GeoEntityRenderer<KnightCandidate>
{
    public KnightCandidateRenderer(EntityRendererProvider.Context context)
    {
        super(context, new KnightCandidateModel());
        this.addRenderLayer(new Pass(this, Kind.GLOW));
        this.addRenderLayer(new Pass(this, Kind.CLEAR));
        this.addRenderLayer(new Pass(this, Kind.CLEAR_GLOW));
    }

    @Override
    public void render(KnightCandidate candidate, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight)
    {
        if (!ShowcaseOnly.has(candidate.getDesign()))
        {
            return; // a showcase-only model the released jar leaves out (boss/ShowcaseOnly): nothing to draw
        }
        this.shadowRadius = candidate.getDesign().shadow();
        super.render(candidate, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** The solid parts: cut out (the see-through parts' texels are holes in this texture). */
    @Override
    public RenderType getRenderType(KnightCandidate candidate, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    private enum Kind
    {
        GLOW, CLEAR, CLEAR_GLOW
    }

    /** One of the passes after the solid parts. */
    private static class Pass extends GeoRenderLayer<KnightCandidate>
    {
        private final Kind kind;

        Pass(KnightCandidateRenderer renderer, Kind kind)
        {
            super(renderer);
            this.kind = kind;
        }

        @Override
        public void render(PoseStack poseStack, KnightCandidate candidate, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay)
        {
            KnightDesigns.Design design = candidate.getDesign();
            KnightCandidateModel.Files files = KnightCandidateModel.files(candidate);
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
                case CLEAR ->
                {
                    if (!design.hasClear())
                    {
                        return;
                    }
                    type = RenderType.entityTranslucent(files.clear());
                    light = packedLight;
                }
                default ->
                {
                    if (!design.hasClearGlow())
                    {
                        return;
                    }
                    type = RenderType.entityTranslucentEmissive(files.clearGlow());
                    light = LightTexture.FULL_BRIGHT;
                }
            }
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, candidate, type, bufferSource.getBuffer(type),
                    partialTick, light, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
