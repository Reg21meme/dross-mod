package com.reg21meme.dross.boss.ratking.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophies;
import com.reg21meme.dross.boss.ratking.trophy.RatKingTrophyItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Draws the Rat King trophy item (in the hand, the inventory, on the ground, in item frames): the same figure as the
 * placed block, in the stack's design, standing in the middle of the item's block-sized space, facing north (the item
 * model's display settings turn it to the viewer: {@code models/item/rat_king_trophy.json}).
 * <p>
 * Every stack of one design shares one animation instance (all trophy items are one Item to GeckoLib), so each design
 * keeps its own held pose even when several designs are drawn in the same frame (the creative tab).
 */
public class RatKingTrophyItemRenderer extends GeoItemRenderer<RatKingTrophyItem>
{
    /** The GeckoLib animation instance ids the five designs use (any numbers no real stack gets; not the Colossus's). */
    private static final long INSTANCE_ID_BASE = -7_100_000L;

    public RatKingTrophyItemRenderer()
    {
        super(new RatKingTrophyGeoModel<>(RatKingTrophyGeoModel.ITEM_MODEL));
        this.useAlternateGuiLighting();
        this.addRenderLayer(new RatKingTrophyLayer<>(this, item -> this.design()));
    }

    /** The design of the stack being drawn. */
    private int design()
    {
        ItemStack stack = this.getCurrentItemStack();
        return stack == null ? 1 : RatKingTrophies.designOf(stack);
    }

    @Override
    public long getInstanceId(RatKingTrophyItem item)
    {
        return INSTANCE_ID_BASE - this.design();
    }

    /**
     * Instead of GeckoLib's own (which scales before moving to the middle, so a scaled figure would stand off-centre):
     * the middle of the floor of the item's block space, then the design's scale.
     */
    @Override
    public void preRender(PoseStack poseStack, RatKingTrophyItem item, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        this.itemRenderTranslations = new Matrix4f(poseStack.last().pose());
        if (!isReRender)
        {
            float scale = RatKingTrophies.get(this.design()).model().scale();
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.scale(scale, scale, scale);
        }
    }

    /** Bones the design hides are scaled to 0 by its pose: skip them (and everything on them). */
    @Override
    public void renderRecursively(PoseStack poseStack, RatKingTrophyItem item, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        if (RatKingTrophyLayer.hidden(bone))
        {
            return;
        }
        super.renderRecursively(poseStack, item, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }
}
