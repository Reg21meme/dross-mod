package com.reg21meme.dross.boss.huntsman.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import com.reg21meme.dross.boss.huntsman.HuntSpear;
import net.minecraft.client.model.TridentModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.entity.ThrownTridentRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

/**
 * The Huntsman's thrown spear in flight. PLACEHOLDER: drawn as Minecraft's trident (its model and texture, the way the
 * game draws a thrown one) until the user's own spear model exists (the user, October 20, 2026: "we will eventually
 * create a spear model and a spear item").
 */
public class HuntSpearRenderer extends EntityRenderer<HuntSpear>
{
    private final TridentModel model;

    public HuntSpearRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.model = new TridentModel(context.bakeLayer(ModelLayers.TRIDENT));
    }

    @Override
    public void render(HuntSpear spear, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light)
    {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, spear.yRotO, spear.getYRot()) - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, spear.xRotO, spear.getXRot()) + 90.0F));
        VertexConsumer vertices = ItemRenderer.getFoilBufferDirect(buffers, this.model.renderType(this.getTextureLocation(spear)),
                false, false);
        this.model.renderToBuffer(pose, vertices, light, OverlayTexture.NO_OVERLAY, 1.0F, 1.0F, 1.0F, 1.0F);
        pose.popPose();
        super.render(spear, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(HuntSpear spear)
    {
        return ThrownTridentRenderer.TRIDENT_LOCATION;
    }
}
