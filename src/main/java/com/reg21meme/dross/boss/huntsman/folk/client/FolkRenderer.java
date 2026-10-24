package com.reg21meme.dross.boss.huntsman.folk.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.huntsman.folk.FolkRole;
import com.reg21meme.dross.boss.huntsman.folk.FolkTuning;
import com.reg21meme.dross.boss.huntsman.folk.HunterFolk;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import javax.annotation.Nullable;

/**
 * Draws a hunter villager like the Huntsman ({@code HuntRenderer}): the solid parts cut out, the glowing eyes
 * full-bright, the see-through ice blended over them and its glow; at half his size ({@link FolkTuning#MODEL_SCALE}),
 * showing only its own calling's gear ({@link FolkRole#shows}: the spearman's shield, the farmer's hoe and basket, the
 * elder's sword and scarred face...).
 */
public class FolkRenderer extends GeoEntityRenderer<HunterFolk>
{
    public FolkRenderer(EntityRendererProvider.Context context)
    {
        super(context, new FolkModel());
        this.withScale(FolkTuning.MODEL_SCALE);
        this.shadowRadius = 0.45F;
        this.addRenderLayer(new Layer(this, "_glow", true, false));
        this.addRenderLayer(new Layer(this, "_ice", false, true));
        this.addRenderLayer(new Layer(this, "_iceglow", true, true));
    }

    @Override
    public RenderType getRenderType(HunterFolk folk, ResourceLocation texture, @Nullable MultiBufferSource bufferSource,
                                    float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    /** Only its own gear: every gear bone of the model it doesn't carry is hidden (the model is shared, so each is set
     * every time one is drawn). */
    @Override
    public void preRender(PoseStack poseStack, HunterFolk folk, BakedGeoModel model, @Nullable MultiBufferSource bufferSource,
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

    /** They die in their own death animation only (on show too: no tipping over onto their side as well). */
    @Override
    protected float getDeathMaxRotation(HunterFolk folk)
    {
        return 0.0F;
    }

    /** They flash red when hurt, but not all the while they lie dead. */
    @Override
    public int getPackedOverlay(HunterFolk folk, float u, float partialTick)
    {
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(folk.hurtTime > 0 && !folk.isDeadOrDying()));
    }

    private static class Layer extends GeoRenderLayer<HunterFolk>
    {
        private final ResourceLocation texture;
        private final boolean glow;
        private final boolean blend;

        Layer(FolkRenderer renderer, String suffix, boolean glow, boolean blend)
        {
            super(renderer);
            this.texture = FolkModel.texture(suffix);
            this.glow = glow;
            this.blend = blend;
        }

        @Override
        public void render(PoseStack poseStack, HunterFolk folk, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay)
        {
            RenderType type = this.glow ? RenderType.entityTranslucentEmissive(this.texture)
                    : this.blend ? RenderType.entityTranslucent(this.texture) : RenderType.entityCutoutNoCull(this.texture);
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, folk, type, bufferSource.getBuffer(type),
                    partialTick, this.glow ? LightTexture.FULL_BRIGHT : packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
