package com.reg21meme.dross.boss.huntsman.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophies;
import com.reg21meme.dross.boss.huntsman.trophy.HuntsmanTrophyItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

import javax.annotation.Nullable;

/**
 * Draws the Huntsman trophy item (in the hand, the inventory, on the ground, in item frames): the same figure as the
 * placed block, in the stack's design, standing in the middle of the item's block-sized space, facing north (the item
 * model's display settings turn it to the viewer: {@code models/item/huntsman_trophy.json}).
 * <p>
 * Every stack of one design shares one animation instance (all trophy items are one Item to GeckoLib), so each design
 * keeps its own held pose even when several designs are drawn in the same frame (the creative tab).
 */
public class HuntsmanTrophyItemRenderer extends GeoItemRenderer<HuntsmanTrophyItem>
{
    /** The GeckoLib animation instance ids the five designs use (any numbers no real stack gets; not another trophy's). */
    private static final long INSTANCE_ID_BASE = -7_200_000L;

    public HuntsmanTrophyItemRenderer()
    {
        super(new HuntsmanTrophyGeoModel<>(HuntsmanTrophyGeoModel.ITEM_MODEL));
        this.useAlternateGuiLighting();
        this.addRenderLayer(new HuntsmanTrophyLayer<>(this, item -> this.design()));
    }

    /** The design of the stack being drawn. */
    private int design()
    {
        ItemStack stack = this.getCurrentItemStack();
        return stack == null ? 1 : HuntsmanTrophies.designOf(stack);
    }

    @Override
    public long getInstanceId(HuntsmanTrophyItem item)
    {
        return INSTANCE_ID_BASE - this.design();
    }

    /** The solid parts cut out (the ice is drawn after by the layer). */
    @Override
    public RenderType getRenderType(HuntsmanTrophyItem item, ResourceLocation texture, @Nullable MultiBufferSource bufferSource,
                                    float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    /**
     * Instead of GeckoLib's own (which scales before moving to the middle, so a scaled figure would stand off-centre):
     * the middle of the floor of the item's block space, then the design's scale.
     */
    @Override
    public void preRender(PoseStack poseStack, HuntsmanTrophyItem item, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        this.itemRenderTranslations = new Matrix4f(poseStack.last().pose());
        if (!isReRender)
        {
            float scale = HuntsmanTrophies.get(this.design()).model().scale();
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.scale(scale, scale, scale);
        }
    }

    /** Bones the design hides are scaled to 0 by its pose: skip them (and everything on them). */
    @Override
    public void renderRecursively(PoseStack poseStack, HuntsmanTrophyItem item, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        if (HuntsmanTrophyLayer.hidden(bone))
        {
            return;
        }
        super.renderRecursively(poseStack, item, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }
}
