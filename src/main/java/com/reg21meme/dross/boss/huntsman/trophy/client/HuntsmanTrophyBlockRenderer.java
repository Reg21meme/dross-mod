package com.reg21meme.dross.boss.huntsman.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophies;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophyBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

import javax.annotation.Nullable;

/**
 * Draws a placed Huntsman trophy: the Wild Hunt in its design's held pose, scaled down to fit the block (the design's
 * scale, {@code HuntsmanTrophyModels}), standing in the middle of it and facing its {@code FACING}, the solid parts cut
 * out; then {@link HuntsmanTrophyLayer} (the glow, the clear ice and its glow).
 */
public class HuntsmanTrophyBlockRenderer extends GeoBlockRenderer<HuntsmanTrophyBlockEntity>
{
    public HuntsmanTrophyBlockRenderer(BlockEntityRendererProvider.Context context)
    {
        super(new HuntsmanTrophyGeoModel<>(HuntsmanTrophyGeoModel.BLOCK_MODEL));
        this.addRenderLayer(new HuntsmanTrophyLayer<>(this, HuntsmanTrophyBlockEntity::design));
    }

    /** The solid parts cut out (their see-through texels are holes; the ice is drawn after by the layer). */
    @Override
    public RenderType getRenderType(HuntsmanTrophyBlockEntity trophy, ResourceLocation texture,
                                    @Nullable MultiBufferSource bufferSource, float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    /** Turns the figure to face its way (GeckoLib), then scales it down to fit the block. */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack)
    {
        super.rotateBlock(facing, poseStack);
        float scale = HuntsmanTrophies.get(this.animatable.design()).model().scale();
        poseStack.scale(scale, scale, scale);
    }

    /** Bones the design hides are scaled to 0 by its pose: skip them (and everything on them). */
    @Override
    public void renderRecursively(PoseStack poseStack, HuntsmanTrophyBlockEntity trophy, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        if (HuntsmanTrophyLayer.hidden(bone))
        {
            return;
        }
        super.renderRecursively(poseStack, trophy, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }
}
