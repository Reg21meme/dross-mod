package com.reg21meme.dross.boss.ratking.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophies;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophyBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws a placed Rat King trophy: his model in its design's held pose, scaled down to fit the block (the design's
 * scale, {@code RatKingTrophyModels}), standing in the middle of it and facing its {@code FACING}; then
 * {@link RatKingTrophyLayer} (his glowing orb and gem, and his red eyes in the rage poses).
 */
public class RatKingTrophyBlockRenderer extends GeoBlockRenderer<RatKingTrophyBlockEntity>
{
    public RatKingTrophyBlockRenderer(BlockEntityRendererProvider.Context context)
    {
        super(new RatKingTrophyGeoModel<>(RatKingTrophyGeoModel.BLOCK_MODEL));
        this.addRenderLayer(new RatKingTrophyLayer<>(this, RatKingTrophyBlockEntity::design));
    }

    /** Turns the figure to face its way (GeckoLib), then scales it down to fit the block. */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack)
    {
        super.rotateBlock(facing, poseStack);
        float scale = RatKingTrophies.get(this.animatable.design()).model().scale();
        poseStack.scale(scale, scale, scale);
    }

    /** Bones the design hides are scaled to 0 by its pose: skip them (and everything on them). */
    @Override
    public void renderRecursively(PoseStack poseStack, RatKingTrophyBlockEntity trophy, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        if (RatKingTrophyLayer.hidden(bone))
        {
            return;
        }
        super.renderRecursively(poseStack, trophy, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }
}
