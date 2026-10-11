package com.reg21meme.dross.boss.golem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.golem.CinderColossus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The obsidian statue a Cinder Colossus cools into when it dies: its {@code <id>_statue.png} texture drawn over
 * everything, fading in while it dies ({@link CinderColossus#statueStrength}) and fully there once it's a statue.
 */
public class GolemStatueLayer extends GeoRenderLayer<CinderColossus>
{
    /** Below this the statue isn't drawn at all. */
    private static final float MIN_STRENGTH = 0.01F;

    public GolemStatueLayer(CinderColossusRenderer renderer)
    {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, CinderColossus golem, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
    {
        float strength = golem.statueStrength(partialTick);
        if (strength < MIN_STRENGTH)
        {
            return;
        }
        RenderType statue = RenderType.entityTranslucent(CinderColossusModel.files(golem).statue());
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, golem, statue, bufferSource.getBuffer(statue),
                partialTick, packedLight, packedOverlay, 1.0F, 1.0F, 1.0F, strength);
    }
}
