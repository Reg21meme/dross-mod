package com.reg21meme.dross.boss.golem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.golem.CinderColossus;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * The fight's Cracking stage: bright lava cracks across a Cinder Colossus's shell (its {@code <id>_cracks.png}
 * texture), drawn full-bright like the glow layer and faded in as the shell cracks
 * ({@link CinderColossus#crackAmount}); they cool and fade with the rest of its glow if it dies like that. With the
 * loose plates ({@link CinderColossusModel}) this is what makes the Cracking stage read.
 */
public class GolemCrackLayer extends GeoRenderLayer<CinderColossus>
{
    /** Below this the cracks aren't drawn at all. */
    private static final float MIN_STRENGTH = 0.01F;

    private final CinderColossusRenderer renderer;

    public GolemCrackLayer(CinderColossusRenderer renderer)
    {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(PoseStack poseStack, CinderColossus golem, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
    {
        float strength = golem.crackAmount(partialTick) * Math.min(1.0F, golem.glowStrength(partialTick));
        if (strength < MIN_STRENGTH)
        {
            return;
        }
        RenderType cracks = RenderType.entityTranslucentEmissive(CinderColossusModel.files(golem).cracks());
        this.renderer.drawGlow(() -> this.getRenderer().reRender(bakedModel, poseStack, bufferSource, golem, cracks,
                bufferSource.getBuffer(cracks), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, strength));
    }
}
