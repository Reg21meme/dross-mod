package com.reg21meme.dross.boss.frostfolk.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.client.PartLayer;
import com.reg21meme.dross.boss.frostfolk.FolkRole;
import com.reg21meme.dross.boss.frostfolk.Frostfolk;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;

/**
 * Draws one of the Frostfolk: its calling's texture, cut out, then its eyes glowing full-bright out of the dark under
 * its hood (its head drawn once more: {@link PartLayer}; a village holds a lot of them, the user saw it lag), showing
 * only its own calling's hood style, cape and tools ({@link FolkRole#shows}).
 * They die the vanilla way: tipping over, flashing red (the user, October 24, 2026: "just the regular death
 * animation").
 */
public class FolkRenderer extends GeoEntityRenderer<Frostfolk>
{
    public FolkRenderer(EntityRendererProvider.Context context)
    {
        super(context, new FolkModel());
        this.shadowRadius = 0.45F;
        // its blue eyes, full-bright (they shine in the dark): only its head is drawn again for them
        this.addRenderLayer(new PartLayer<>(this, folk -> FolkModel.glow(folk.role()), RenderType::entityTranslucentEmissive,
                true));
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
}
