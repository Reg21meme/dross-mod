package com.reg21meme.dross.boss.knight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reg21meme.dross.boss.knight.KnightFireball;
import com.reg21meme.dross.boss.knight.KnightTuning;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemDisplayContext;

/**
 * Draws Flamehand's fireballs as Minecraft's fire charge facing the camera, full-bright: a small one at a fireball's
 * size, a great one {@link KnightTuning#GREAT_SCALE} times as big (Minecraft's own thrown-item renderer has one size).
 */
public class KnightFireballRenderer extends EntityRenderer<KnightFireball>
{
    private static final float SMALL_SCALE = 0.75F;
    private final ItemRenderer items;

    public KnightFireballRenderer(EntityRendererProvider.Context context)
    {
        super(context);
        this.items = context.getItemRenderer();
    }

    @Override
    protected int getBlockLightLevel(KnightFireball ball, BlockPos pos)
    {
        return 15;
    }

    @Override
    public void render(KnightFireball ball, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                       int packedLight)
    {
        float scale = ball.isGreat() ? SMALL_SCALE * KnightTuning.GREAT_SCALE : SMALL_SCALE;
        poseStack.pushPose();
        poseStack.translate(0.0D, 0.25D, 0.0D);
        poseStack.scale(scale, scale, scale);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F));
        this.items.renderStatic(ball.getItem(), ItemDisplayContext.GROUND, packedLight, OverlayTexture.NO_OVERLAY, poseStack,
                buffers, ball.level(), ball.getId());
        poseStack.popPose();
        super.render(ball, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(KnightFireball ball)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
