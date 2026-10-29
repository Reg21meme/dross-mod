package com.reg21meme.dross.boss.fire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.fire.FireCandidate;
import com.reg21meme.dross.boss.fire.FireDesigns;
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
 * Draws a fire servant candidate with GeckoLib, in four passes, in this order (the textures are made to match by
 * {@code tools/fire/build.py}, through the Rimefall kit's {@code texture.py}):
 * <ol>
 *   <li>its solid parts ({@code <id>.png}, cut out, lit by the world): flames are cut out too, like Minecraft's own
 *       fire block;</li>
 *   <li>what glows on them ({@code <id>_glow.png}, full-bright): lava, embers, eyes, flames. Drawn before the clear
 *       parts, so a flame inside a lantern or lava inside a glass belly shines through the glass;</li>
 *   <li>what you can see through ({@code <id>_clear.png}, blended, lit by the world): glass, heat haze;</li>
 *   <li>what glows on the clear parts ({@code <id>_clearglow.png}, full-bright).</li>
 * </ol>
 * Each pass after the first only runs for designs that have that texture. The shadow under it is sized to the design.
 */
public class FireCandidateRenderer extends GeoEntityRenderer<FireCandidate>
{
    public FireCandidateRenderer(EntityRendererProvider.Context context)
    {
        super(context, new FireCandidateModel());
        this.addRenderLayer(new Pass(this, Kind.GLOW));
        this.addRenderLayer(new Pass(this, Kind.CLEAR));
        this.addRenderLayer(new Pass(this, Kind.CLEAR_GLOW));
        this.addRenderLayer(new FireDeathLook(this));
    }

    @Override
    public void render(FireCandidate candidate, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight)
    {
        this.shadowRadius = candidate.getDesign().shadow();
        super.render(candidate, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** The Emberdactyl's wing markers: tracked while it's drawn, for the fire falling from its wings (EmberWingFire). */
    @Override
    public void preRender(PoseStack poseStack, FireCandidate candidate, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        EmberWingFire.prepare(model, isReRender);
        super.preRender(poseStack, candidate, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void postRender(PoseStack poseStack, FireCandidate candidate, BakedGeoModel model, MultiBufferSource bufferSource,
                           VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                           float red, float green, float blue, float alpha)
    {
        super.postRender(poseStack, candidate, model, bufferSource, buffer, isReRender, partialTick, packedLight,
                packedOverlay, red, green, blue, alpha);
        EmberWingFire.capture(candidate, model, isReRender);
    }

    /** The solid parts: cut out (the clear parts' texels are holes in this texture). */
    @Override
    public RenderType getRenderType(FireCandidate candidate, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    private enum Kind
    {
        GLOW, CLEAR, CLEAR_GLOW
    }

    /** One of the passes after the solid parts. */
    private static class Pass extends GeoRenderLayer<FireCandidate>
    {
        private final Kind kind;

        Pass(FireCandidateRenderer renderer, Kind kind)
        {
            super(renderer);
            this.kind = kind;
        }

        @Override
        public void render(PoseStack poseStack, FireCandidate candidate, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay)
        {
            FireDesigns.Design design = candidate.getDesign();
            FireCandidateModel.Files files = FireCandidateModel.files(candidate);
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
                    type = RenderType.entityTranslucentEmissive(FireCandidateModel.glow(candidate));
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
