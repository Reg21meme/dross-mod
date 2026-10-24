package com.reg21meme.dross.boss.frostfolk.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.frostfolk.FolkRole;
import com.reg21meme.dross.boss.frostfolk.Frostfolk;
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
 * Draws one of the Frostfolk: its calling's texture, cut out, then its eyes glowing full-bright out of the dark under
 * its hood (one more pass; no ice layers: a village holds a lot of them, the user saw it lag), showing only its own
 * calling's hood style, cape and tools ({@link FolkRole#shows}).
 * They die the vanilla way: tipping over, flashing red (the user, October 24, 2026: "just the regular death
 * animation").
 */
public class FolkRenderer extends GeoEntityRenderer<Frostfolk>
{
    public FolkRenderer(EntityRendererProvider.Context context)
    {
        super(context, new FolkModel());
        this.shadowRadius = 0.45F;
        this.addRenderLayer(new Eyes(this));
    }

    @Override
    public RenderType getRenderType(Frostfolk folk, ResourceLocation texture, @Nullable MultiBufferSource bufferSource,
                                    float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    /** Only its own shapes: every calling's bone it doesn't show is hidden (the model is shared, so each is set every
     * time one is drawn). */
    @Override
    public void preRender(PoseStack poseStack, Frostfolk folk, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
                          @Nullable VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight,
                          int packedOverlay, float red, float green, float blue, float alpha)
    {
        if (!isReRender)
        {
            FolkRole role = folk.role();
            for (String gear : FolkRole.GEAR)
            {
                model.getBone(gear).ifPresent(bone -> bone.setHidden(!role.shows.contains(gear)));
            }
        }
        super.preRender(poseStack, folk, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    /** Its blue eyes, full-bright (they shine in the dark). */
    private static class Eyes extends GeoRenderLayer<Frostfolk>
    {
        Eyes(FolkRenderer renderer)
        {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, Frostfolk folk, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay)
        {
            RenderType type = RenderType.entityTranslucentEmissive(FolkModel.glow(folk.role()));
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, folk, type, bufferSource.getBuffer(type),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
