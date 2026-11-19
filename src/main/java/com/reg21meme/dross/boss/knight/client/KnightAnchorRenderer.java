package com.reg21meme.dross.boss.knight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.knight.KnightAnchor;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import javax.annotation.Nullable;

/**
 * Draws the Wreck Knight's thrown anchor: its own model ({@code knightmob/wreck_anchor}, made by tools/knights/props.py),
 * its crown pointing the way it flies, its glow full-bright, and its chain (Minecraft's chain texture) back to his hand.
 */
public class KnightAnchorRenderer extends GeoEntityRenderer<KnightAnchor>
{
    private static final ResourceLocation GEO = new ResourceLocation(Dross.MODID, "geo/entity/knightmob/wreck_anchor.geo.json");
    private static final ResourceLocation TEX = new ResourceLocation(Dross.MODID, "textures/entity/knightmob/wreck_anchor.png");
    private static final ResourceLocation GLOW = new ResourceLocation(Dross.MODID,
            "textures/entity/knightmob/wreck_anchor_glow.png");
    private static final ResourceLocation ANIM = new ResourceLocation(Dross.MODID,
            "animations/entity/knightmob/wreck_anchor.animation.json");
    private static final ResourceLocation CHAIN = new ResourceLocation("minecraft", "textures/block/chain.png");
    /** The chain's width (blocks) and the share of its texture across that is the links. */
    private static final float CHAIN_WIDTH = 0.19F;
    private static final float CHAIN_U = 3.0F / 16.0F;

    public KnightAnchorRenderer(EntityRendererProvider.Context context)
    {
        super(context, new Model());
        this.addRenderLayer(new Glow(this));
    }

    @Override
    protected void applyRotations(KnightAnchor anchor, PoseStack poseStack, float ageInTicks, float rotationYaw,
                                  float partialTick)
    {
        poseStack.translate(0.0D, 0.4D, 0.0D);
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - Mth.rotLerp(partialTick, anchor.yRotO, anchor.getYRot())));
        poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, anchor.xRotO, anchor.getXRot())));
    }

    @Override
    public RenderType getRenderType(KnightAnchor anchor, ResourceLocation texture, @Nullable MultiBufferSource bufferSource,
                                    float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    @Override
    public void render(KnightAnchor anchor, float entityYaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource bufferSource, int packedLight)
    {
        super.render(anchor, entityYaw, partialTick, poseStack, bufferSource, packedLight);
        Vec3 start = anchor.chainStart(partialTick);
        if (start == null)
        {
            return;
        }
        Vec3 here = anchor.getPosition(partialTick).add(0.0D, 0.4D, 0.0D);
        Vec3 d = start.subtract(here);
        double length = d.length();
        if (length < 0.2D)
        {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.4D, 0.0D);
        VertexConsumer buffer = bufferSource.getBuffer(RenderType.entityCutoutNoCull(CHAIN));
        PoseStack.Pose pose = poseStack.last();
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        Vec3 way = d.normalize();
        Vec3 up = Math.abs(way.y) > 0.95D ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 a = way.cross(up).normalize().scale(CHAIN_WIDTH / 2.0D);
        Vec3 b = way.cross(a).normalize().scale(CHAIN_WIDTH / 2.0D);
        float v = (float) length;     // (the texture repeats once a block)
        quad(buffer, m, n, a.scale(-1.0D), a, d, v, packedLight);
        quad(buffer, m, n, b.scale(-1.0D), b, d, v, packedLight);
        poseStack.popPose();
    }

    private static void quad(VertexConsumer buffer, Matrix4f m, Matrix3f n, Vec3 left, Vec3 right, Vec3 d, float v, int light)
    {
        vertex(buffer, m, n, left, 0.0F, 0.0F, light);
        vertex(buffer, m, n, right, CHAIN_U, 0.0F, light);
        vertex(buffer, m, n, right.add(d), CHAIN_U, v, light);
        vertex(buffer, m, n, left.add(d), 0.0F, v, light);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f m, Matrix3f n, Vec3 p, float u, float v, int light)
    {
        buffer.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0.0F, 1.0F, 0.0F).endVertex();
    }

    @Override
    public boolean shouldRender(KnightAnchor anchor, Frustum frustum, double x, double y, double z)
    {
        return true;     // (its chain reaches back to the knight, out of its own box)
    }

    /** The anchor's glow, full-bright. */
    private static class Glow extends GeoRenderLayer<KnightAnchor>
    {
        Glow(KnightAnchorRenderer renderer)
        {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, KnightAnchor anchor, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                           int packedOverlay)
        {
            RenderType type = RenderType.entityTranslucentEmissive(GLOW);
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, anchor, type, bufferSource.getBuffer(type),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** The anchor's files. */
    private static class Model extends GeoModel<KnightAnchor>
    {
        @Override
        public ResourceLocation getModelResource(KnightAnchor anchor)
        {
            return GEO;
        }

        @Override
        public ResourceLocation getTextureResource(KnightAnchor anchor)
        {
            return TEX;
        }

        @Override
        public ResourceLocation getAnimationResource(KnightAnchor anchor)
        {
            return ANIM;
        }
    }
}
