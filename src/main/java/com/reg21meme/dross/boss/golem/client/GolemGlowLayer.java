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
 * The glowing parts of a Cinder Colossus: its {@code <id>_glow.png} texture drawn again over the model at full
 * brightness (it glows in the dark), see-through where that texture is. In the shell form that's the lava cracks
 * and the eyes; in the core form it's the whole lava body. While it dies the glow fades out
 * ({@link CinderColossus#glowStrength}), which leaves the darker crust of its main texture: the lava cooling.
 * <p>
 * GeckoLib's own glow layer isn't used because it cuts the glowing pixels out of the main texture, which would leave
 * holes when the glow fades.
 */
public class GolemGlowLayer extends GeoRenderLayer<CinderColossus>
{
    /** Below this the glow isn't drawn at all. */
    private static final float MIN_STRENGTH = 0.01F;

    private final CinderColossusRenderer renderer;

    public GolemGlowLayer(CinderColossusRenderer renderer)
    {
        super(renderer);
        this.renderer = renderer;
    }

    @Override
    public void render(PoseStack poseStack, CinderColossus golem, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
    {
        float strength = golem.glowStrength(partialTick);
        if (strength < MIN_STRENGTH)
        {
            return;
        }
        RenderType glow = RenderType.entityTranslucentEmissive(CinderColossusModel.files(golem).glow());
        this.renderer.drawGlow(() -> this.getRenderer().reRender(bakedModel, poseStack, bufferSource, golem, glow,
                bufferSource.getBuffer(glow), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, strength));
    }
}
