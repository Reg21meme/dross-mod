package com.reg21meme.dross.boss.emberdactyl.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.emberdactyl.trophy.EmberTrophyModels;
import com.reg21meme.dross.boss.emberdactyl.trophy.EmberdactylTrophies;
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
 * What an Emberdactyl trophy's figure has on top of its solid parts, drawn after the model by both the block and the
 * item renderer, as the boss's own renderer draws them ({@code boss/fire/client/FireCandidateRenderer},
 * {@code FireDeathLook}):
 * <ol>
 *   <li>the glow: the trophy's {@code _glow} texture at full brightness (its lava, embers and eyes, the volcano's lava
 *       rivers and lake, the egg's warm foot), at the design's strength ({@link EmberTrophyModels.Model#glow});</li>
 *   <li>the death's magma cracks ({@code _cracks}, full-bright), only in the designs that show them, at their strength
 *       ({@link EmberTrophyModels.Model#cracks}).</li>
 * </ol>
 */
public class EmberTrophyLayer<T extends GeoAnimatable> extends GeoRenderLayer<T>
{
    /** Below this a glow layer isn't drawn at all. */
    private static final float MIN_STRENGTH = 0.01F;

    private final ToIntFunction<T> design;
    private final RenderType glow = RenderType.entityTranslucentEmissive(EmberTrophyGeoModel.GLOW);
    private final RenderType cracks = RenderType.entityTranslucentEmissive(EmberTrophyGeoModel.CRACKS);

    /** @param design which design the thing being drawn is */
    public EmberTrophyLayer(GeoRenderer<T> renderer, ToIntFunction<T> design)
    {
        super(renderer);
        this.design = design;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
    {
        EmberTrophyModels.Model model = EmberdactylTrophies.get(this.design.applyAsInt(animatable)).model();
        if (model.glow() >= MIN_STRENGTH)
        {
            this.pass(this.glow, model.glow(), poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);
        }
        if (model.cracks() >= MIN_STRENGTH)
        {
            this.pass(this.cracks, model.cracks(), poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);
        }
    }

    /** One more full-bright pass of the model with one of the trophy's textures. */
    private void pass(RenderType type, float alpha, PoseStack poseStack, T animatable, BakedGeoModel bakedModel,
                      MultiBufferSource bufferSource, float partialTick, int packedOverlay)
    {
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type),
                partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, alpha);
    }

    /** True for a bone the design's pose hides (scaled to 0): the renderers skip it and everything on it. */
    static boolean hidden(GeoBone bone)
    {
        return bone.getScaleX() == 0.0F && bone.getScaleY() == 0.0F && bone.getScaleZ() == 0.0F;
    }
}
