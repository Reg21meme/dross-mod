package com.reg21meme.dross.boss.giant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reg21meme.dross.boss.giant.GiantBoulder;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.data.ModelData;

/** Draws a giant's boulder as a tumbling block of Cragstone, about the size of the one on G1's shoulder. */
public class GiantBoulderRenderer extends EntityRenderer<GiantBoulder>
{
    /** Its size (blocks across) and how fast it tumbles (degrees a tick). */
    private static final float SIZE = 1.0F;
    private static final float SPIN = 14.0F;

    private final BlockRenderDispatcher blocks;

    public GiantBoulderRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.shadowRadius = SIZE * 0.4F;
    }

    @Override
    public void render(GiantBoulder boulder, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight)
    {
        float age = boulder.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0F, SIZE / 2.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * SPIN * 0.7F + boulder.getId() * 37.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * SPIN));
        poseStack.scale(SIZE, SIZE, SIZE);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        this.blocks.renderSingleBlock(GiantBoulder.look(), poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY,
                ModelData.EMPTY, null);
        poseStack.popPose();
        super.render(boulder, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(GiantBoulder boulder)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
