package com.reg21meme.dross.boss.ratking.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophies;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophyModels;
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
 * What a Rat King trophy's figure has on top of its fur, drawn after the model by both the block and the item renderer
 * (like the boss's own renderer, {@code RatKingRenderer}):
 * <ol>
 *   <li>The glow: the trophy's {@code _glow} texture at full brightness (his orb and his crown's gem), at the design's
 *       strength ({@link RatKingTrophyModels.Model#glow}).</li>
 *   <li>His red eyes: the {@code _rage} texture at full brightness, in the two rage poses (Tantrum and Ground Pound,
 *       {@link RatKingTrophyModels.Model#rage}).</li>
 * </ol>
 */
public class RatKingTrophyLayer<T extends GeoAnimatable> extends GeoRenderLayer<T>
{
    /** Below this a layer isn't drawn at all. */
    private static final float MIN_STRENGTH = 0.01F;

    private final ToIntFunction<T> design;
    private final RenderType glow = RenderType.entityTranslucentEmissive(RatKingTrophyGeoModel.GLOW);
    private final RenderType rage = RenderType.entityTranslucentEmissive(RatKingTrophyGeoModel.RAGE);

    /** @param design which design the thing being drawn is */
    public RatKingTrophyLayer(GeoRenderer<T> renderer, ToIntFunction<T> design)
    {
        super(renderer);
        this.design = design;
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
    {
        RatKingTrophyModels.Model model = RatKingTrophies.get(this.design.applyAsInt(animatable)).model();
        this.emissive(this.glow, model.glow(), poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);
        this.emissive(this.rage, model.rage(), poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);
    }

    /** A full-bright pass of one of the trophy's textures at this strength. */
    private void emissive(RenderType type, float strength, PoseStack poseStack, T animatable, BakedGeoModel bakedModel,
                          MultiBufferSource bufferSource, float partialTick, int packedOverlay)
    {
        if (strength < MIN_STRENGTH)
        {
            return;
        }
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type),
                partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, strength);
    }

    /** True for a bone the design's pose hides (scaled to 0): the renderers skip it and everything on it. */
    static boolean hidden(GeoBone bone)
    {
        return bone.getScaleX() == 0.0F && bone.getScaleY() == 0.0F && bone.getScaleZ() == 0.0F;
    }
}
