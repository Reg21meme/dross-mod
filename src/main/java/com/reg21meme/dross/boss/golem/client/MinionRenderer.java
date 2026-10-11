package com.reg21meme.dross.boss.golem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.golem.GolemMinion;
import com.reg21meme.dross.boss.golem.LavaGolem;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.util.Mth;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws a Cinder Colossus minion (a stone golem or a lava golem) with GeckoLib: its texture lit by the world, then
 * its glowing parts full-bright (the stone golem's eyes and dim cracks; the lava golem's whole body). A lava golem
 * about to blow up swells and flashes white, like a creeper.
 */
public class MinionRenderer extends GeoEntityRenderer<GolemMinion>
{
    /** Shadow size (blocks). */
    private static final float SHADOW_RADIUS = 0.5F;
    /** How much bigger a lava golem gets just before it blows up. */
    private static final float MAX_SWELL = 0.3F;

    public MinionRenderer(EntityRendererProvider.Context context)
    {
        super(context, new MinionModel());
        this.shadowRadius = SHADOW_RADIUS;
        this.addRenderLayer(new GlowLayer(this));
    }

    @Override
    public void scaleModelForRender(float widthScale, float heightScale, PoseStack poseStack, GolemMinion minion,
                                    BakedGeoModel model, boolean isReRender, float partialTick, int packedLight, int packedOverlay)
    {
        if (!isReRender && minion instanceof LavaGolem lava)
        {
            float swell = lava.swell(partialTick);
            if (swell > 0.0F)
            {
                float wobble = 1.0F + Mth.sin(swell * 100.0F) * swell * 0.01F;
                float grow = (1.0F + MAX_SWELL * swell) * wobble;
                widthScale *= grow;
                heightScale *= 1.0F + MAX_SWELL * 0.6F * swell;
            }
        }
        super.scaleModelForRender(widthScale, heightScale, poseStack, minion, model, isReRender, partialTick, packedLight,
                packedOverlay);
    }

    /** A lava golem about to blow up flashes white (like a creeper); every minion flashes red when hurt. */
    @Override
    public int getPackedOverlay(GolemMinion minion, float u, float partialTick)
    {
        float white = 0.0F;
        if (minion instanceof LavaGolem lava)
        {
            float swell = lava.swell(partialTick);
            white = (int) (swell * 10.0F) % 2 == 0 ? 0.0F : Mth.clamp(swell, 0.5F, 1.0F);
        }
        return OverlayTexture.pack(OverlayTexture.u(white), OverlayTexture.v(minion.hurtTime > 0 || minion.deathTime > 0));
    }

    /** Its {@code <id>_glow.png} drawn again over it at full brightness. */
    private static class GlowLayer extends GeoRenderLayer<GolemMinion>
    {
        GlowLayer(MinionRenderer renderer)
        {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, GolemMinion minion, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
        {
            RenderType glow = RenderType.entityTranslucentEmissive(MinionModel.files(minion).glow());
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, minion, glow, bufferSource.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }
}
