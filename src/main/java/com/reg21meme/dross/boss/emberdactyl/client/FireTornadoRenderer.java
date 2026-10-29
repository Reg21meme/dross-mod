package com.reg21meme.dross.boss.emberdactyl.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.emberdactyl.FireTornado;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

/**
 * Draws a fire tornado: the servant candidate Firestorm's model (its fire column look, {@code fire/firestorma}), shrunk
 * to its size and spinning round, cut out, with its glow drawn full-bright over it.
 */
public class FireTornadoRenderer extends GeoEntityRenderer<FireTornado>
{
    private static final ResourceLocation MODEL = new ResourceLocation(Dross.MODID, "geo/entity/fire/firestorma.geo.json");
    private static final ResourceLocation TEXTURE = new ResourceLocation(Dross.MODID, "textures/entity/fire/firestorma.png");
    private static final ResourceLocation GLOW = new ResourceLocation(Dross.MODID, "textures/entity/fire/firestorma_glow.png");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(Dross.MODID, "animations/entity/fire/candidate.animation.json");
    /** Degrees a tick it spins. */
    private static final float SPIN = 24.0F;

    public FireTornadoRenderer(EntityRendererProvider.Context context)
    {
        super(context, new GeoModel<>()
        {
            @Override
            public ResourceLocation getModelResource(FireTornado tornado)
            {
                return MODEL;
            }

            @Override
            public ResourceLocation getTextureResource(FireTornado tornado)
            {
                return TEXTURE;
            }

            @Override
            public ResourceLocation getAnimationResource(FireTornado tornado)
            {
                return ANIMATIONS;
            }
        });
        this.shadowRadius = 1.2F;
        this.addRenderLayer(new GeoRenderLayer<>(this)
        {
            @Override
            public void render(PoseStack poseStack, FireTornado tornado, BakedGeoModel bakedModel, RenderType renderType,
                               MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                               int packedOverlay)
            {
                RenderType glow = RenderType.entityTranslucentEmissive(GLOW);
                this.getRenderer().reRender(bakedModel, poseStack, bufferSource, tornado, glow, bufferSource.getBuffer(glow),
                        partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
            }
        });
    }

    @Override
    public void render(FireTornado tornado, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight)
    {
        poseStack.pushPose();
        float scale = FireTornado.MODEL_SCALE * tornado.getSize();
        poseStack.mulPose(Axis.YP.rotationDegrees((tornado.tickCount + partialTick) * SPIN));
        poseStack.scale(scale, scale, scale);
        super.render(tornado, 0.0F, partialTick, poseStack, bufferSource, packedLight);
        poseStack.popPose();
    }

    @Override
    public RenderType getRenderType(FireTornado tornado, ResourceLocation texture, @Nullable MultiBufferSource bufferSource,
                                    float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }
}
