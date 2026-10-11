package com.reg21meme.dross.boss.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.trophy.ColossusTrophies;
import com.reg21meme.dross.boss.trophy.TrophyModels;
import net.minecraft.Util;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.util.Mth;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.Arrays;
import java.util.function.ToDoubleFunction;
import java.util.function.ToIntFunction;

/**
 * Everything a Cinder Colossus trophy's figure has on top of its stone: drawn after the model, by both the block and
 * the item renderer.
 * <ol>
 *   <li>The glow: the boss's {@code _glow} texture at full brightness (lava cracks, eyes, the lava body), at the
 *       design's subtle strength ({@link TrophyModels.Model#glow}).</li>
 *   <li>The Cracking look's bright cracks (design 4 only, {@link TrophyModels.Model#cracks}).</li>
 *   <li>The flowing lava: the design's streams (painted over the figure in its own pose, running downhill from the
 *       craters), drawn with Minecraft's dissolve shader ({@link RenderType#dragonExplosionAlpha}, as the boss's
 *       poured lava is): a texel shows only where its alpha is at least the vertex alpha, and each texel's alpha says
 *       how far down its stream it is (1 - 0.25 r). So: the cool streams everywhere, then for each bright band, the hot
 *       streams down to the band's front and the cool ones again down to its back. The bands slide down and loop
 *       ({@link ColossusTrophies#FLOW_PERIOD_SECONDS}), so the lava seems to keep running down the body. The crater
 *       pools are fully opaque and bright in both textures, so they always glow.</li>
 * </ol>
 * The dissolve passes rely on Minecraft drawing each render type's batch as soon as another one is asked for (every
 * entity render type works that way), so they land in the order they're asked for.
 */
public class TrophyLayer<T extends GeoAnimatable> extends GeoRenderLayer<T>
{
    /** The renderer's softer lighting for glowing parts (normals bent towards "up", like the boss's glow). */
    public interface Host
    {
        void drawGlow(Runnable draw);
    }

    /** Below this the glow and cracks aren't drawn at all. */
    private static final float MIN_STRENGTH = 0.01F;
    /** The streams' alphas run from 1 (at the crater) down to 1 - this (farthest away). */
    private static final float FLOW_ALPHA_RANGE = 0.25F;
    /** The lowest alpha a stream texel has (r = 1); a pass at this shows every stream. */
    private static final int LOWEST_STREAM_ALPHA = 191;

    private final Host host;
    private final ToIntFunction<T> design;
    private final ToDoubleFunction<T> phase;
    private final RenderType[] hot = new RenderType[ColossusTrophies.count()];
    private final RenderType[] cool = new RenderType[ColossusTrophies.count()];
    private final float[] fronts = new float[ColossusTrophies.FLOW_BANDS];

    /**
     * @param design which design the thing being drawn is
     * @param phase  where in the flow's loop it is (0 to 1), so trophies side by side don't flow in step
     */
    public TrophyLayer(GeoRenderer<T> renderer, Host host, ToIntFunction<T> design, ToDoubleFunction<T> phase)
    {
        super(renderer);
        this.host = host;
        this.design = design;
        this.phase = phase;
        for (int i = 0; i < ColossusTrophies.count(); i++)
        {
            this.hot[i] = RenderType.dragonExplosionAlpha(TrophyGeoModel.flow(i + 1, true));
            this.cool[i] = RenderType.dragonExplosionAlpha(TrophyGeoModel.flow(i + 1, false));
        }
    }

    @Override
    public void render(PoseStack poseStack, T animatable, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
    {
        ColossusTrophies.Design design = ColossusTrophies.get(this.design.applyAsInt(animatable));
        TrophyModels.Model model = design.model();
        this.emissive(TrophyGeoModel.GLOW, model.glow(), poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);
        this.emissive(TrophyGeoModel.CRACKS, model.cracks(), poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);

        // The flowing lava: the bands' fronts (in stream lengths, 0 at the crater), farthest down first.
        double loop = Util.getMillis() / 1000.0D / ColossusTrophies.FLOW_PERIOD_SECONDS + this.phase.applyAsDouble(animatable);
        for (int i = 0; i < this.fronts.length; i++)
        {
            this.fronts[i] = (float) Mth.frac(loop + (double) i / this.fronts.length) * (1.0F + ColossusTrophies.FLOW_BAND);
        }
        Arrays.sort(this.fronts);
        RenderType hotStreams = this.hot[design.number() - 1];
        RenderType coolStreams = this.cool[design.number() - 1];
        this.stream(coolStreams, LOWEST_STREAM_ALPHA, poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);
        for (int i = this.fronts.length - 1; i >= 0; i--)
        {
            float front = this.fronts[i];
            this.stream(hotStreams, alphaFor(front), poseStack, animatable, bakedModel, bufferSource, partialTick, packedOverlay);
            this.stream(coolStreams, alphaFor(front - ColossusTrophies.FLOW_BAND), poseStack, animatable, bakedModel,
                    bufferSource, partialTick, packedOverlay);
        }
    }

    /** A full-bright pass of one of the boss's textures (the glow or the cracks) at this strength. */
    private void emissive(ResourceLocation texture, float strength, PoseStack poseStack, T animatable, BakedGeoModel bakedModel,
                          MultiBufferSource bufferSource, float partialTick, int packedOverlay)
    {
        if (strength < MIN_STRENGTH)
        {
            return;
        }
        RenderType type = RenderType.entityTranslucentEmissive(texture);
        this.host.drawGlow(() -> this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type,
                bufferSource.getBuffer(type), partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, strength));
    }

    /** One dissolve pass of the streams: every stream texel whose alpha is at least {@code alpha} (0 to 255). */
    private void stream(RenderType type, int alpha, PoseStack poseStack, T animatable, BakedGeoModel bakedModel,
                        MultiBufferSource bufferSource, float partialTick, int packedOverlay)
    {
        // The vertex alpha is stored as a byte: aim for the middle of the wanted step so it can't round to the next.
        float vertexAlpha = (alpha + 0.5F) / 255.0F;
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, animatable, type, bufferSource.getBuffer(type),
                partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, vertexAlpha);
    }

    /**
     * The alpha a dissolve pass needs to show the streams from their crater down to {@code front} (in stream lengths):
     * texels with r up to there have alpha at least 255 (1 - 0.25 front). Never below the lowest stream alpha (so
     * see-through texels never draw), never above 255 (then only the always-bright pools show).
     */
    private static int alphaFor(float front)
    {
        int alpha = Mth.ceil(255.0F * (1.0F - FLOW_ALPHA_RANGE * front) - 1.0E-3F);
        return Mth.clamp(alpha, LOWEST_STREAM_ALPHA, 255);
    }
}
