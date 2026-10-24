package com.reg21meme.dross.boss.huntsman.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophies;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophyModels;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.function.ToIntFunction;

/**
 * What a Huntsman trophy's figure has on top of its solid parts, drawn after the model by both the block and the item
 * renderer, as the boss's own renderer draws them ({@code boss/huntsman/client/HuntRenderer}):
 * <ol>
 *   <li>the glow: the trophy's {@code _glow} texture at full brightness (his eyes under the wolf's hood, the moose's
 *       eyes), at the design's strength ({@link HuntsmanTrophyModels.Model#glow});</li>
 *   <li>the clear ice, blended over them (his spear's head, the arrows' fletchings, the moose's icicles, the Parting
 *       Shot's spikes), in the block's own light;</li>
 *   <li>the ice's own glow, full-bright.</li>
 * </ol>
 */
public class HuntsmanTrophyLayer<T extends GeoAnimatable> extends GeoRenderLayer<T>
{
    /** Below this the glow layers aren't drawn at all. */
    private static final float MIN_STRENGTH = 0.01F;

    private final ToIntFunction<T> design;
    private final RenderType glow = RenderType.entityTranslucentEmissive(HuntsmanTrophyGeoModel.GLOW);
    private final RenderType ice = RenderType.entityTranslucent(HuntsmanTrophyGeoModel.ICE);
    private final RenderType iceGlow = RenderType.entityTranslucentEmissive(HuntsmanTrophyGeoModel.ICE_GLOW);

    /** @param design which design the thing being drawn is */
    public HuntsmanTrophyLayer(GeoRenderer<T> renderer, ToIntFunction<T> design)
    {
        super(renderer);
        this.design = design;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
    {
        HuntsmanTrophyModels.Model model = HuntsmanTrophies.get(this.design.applyAsInt(animatable)).model();
        if (model.glow() >= MIN_STRENGTH)
        {
            this.pass(this.glow, LightTexture.FULL_BRIGHT, model.glow(), poseStack, animatable, bakedModel, bufferSource,
                    partialTick, packedOverlay);
        }
        this.pass(this.ice, packedLight, 1.0F, poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);
        if (model.glow() >= MIN_STRENGTH)
        {
            this.pass(this.iceGlow, LightTexture.FULL_BRIGHT, model.glow(), poseStack, animatable, bakedModel, bufferSource,
                    partialTick, packedOverlay);
        }
    }

    /** One more pass of the model with one of the trophy's textures. */
    private void pass(RenderType type, int light, float alpha, PoseStack poseStack, T animatable, BakedGeoModel bakedModel,
                      MultiBufferSource bufferSource, float partialTick, int packedOverlay)
    {
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type),
                partialTick, light, packedOverlay, 1.0F, 1.0F, 1.0F, alpha);
    }

    /** True for a bone the design's pose hides (scaled to 0): the renderers skip it and everything on it. */
    static boolean hidden(GeoBone bone)
    {
        return bone.getScaleX() == 0.0F && bone.getScaleY() == 0.0F && bone.getScaleZ() == 0.0F;
    }
}
