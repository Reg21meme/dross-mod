package com.reg21meme.dross.boss.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.trophy.ColossusTrophies;
import com.reg21meme.dross.boss.trophy.ColossusTrophyItem;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * Draws the Cinder Colossus trophy item (in the hand, the inventory, on the ground, in item frames): the same figure
 * as the placed block, in the stack's design, standing in the middle of the item's block-sized space, facing north
 * (the item model's display settings turn it to the viewer: {@code models/item/colossus_trophy.json}).
 * <p>
 * Every stack of one design shares one animation instance (all trophy items are one Item to GeckoLib), so each design
 * keeps its own held pose even when several designs are drawn in the same frame (the creative tab).
 */
public class TrophyItemRenderer extends GeoItemRenderer<ColossusTrophyItem> implements TrophyLayer.Host
{
    /** The GeckoLib animation instance ids the five designs use (any numbers no real stack gets). */
    private static final long INSTANCE_ID_BASE = -7_000_000L;

    private final TrophyGlow glow = new TrophyGlow();

    public TrophyItemRenderer()
    {
        super(new TrophyGeoModel<>(TrophyGeoModel.ITEM_MODEL));
        this.useAlternateGuiLighting();
        this.addRenderLayer(new TrophyLayer<>(this, this, item -> this.design(), item -> 0.0D));
    }

    /** The design of the stack being drawn. */
    private int design()
    {
        ItemStack stack = this.getCurrentItemStack();
        return stack == null ? 1 : ColossusTrophies.designOf(stack);
    }

    @Override
    public long getInstanceId(ColossusTrophyItem item)
    {
        return INSTANCE_ID_BASE - this.design();
    }

    /**
     * Instead of GeckoLib's own (which scales before moving to the middle, so a scaled figure would stand off-centre):
     * the middle of the floor of the item's block space, then the design's scale.
     */
    @Override
    public void preRender(PoseStack poseStack, ColossusTrophyItem item, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        this.itemRenderTranslations = new Matrix4f(poseStack.last().pose());
        if (!isReRender)
        {
            this.glow.findUp(poseStack);
            float scale = ColossusTrophies.get(this.design()).model().scale();
            poseStack.translate(0.5F, 0.0F, 0.5F);
            poseStack.scale(scale, scale, scale);
        }
    }

    /** Bones the design hides are scaled to 0 by its pose: skip them (and everything on them). */
    @Override
    public void renderRecursively(PoseStack poseStack, ColossusTrophyItem item, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        if (TrophyGlow.hidden(bone))
        {
            return;
        }
        super.renderRecursively(poseStack, item, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer, int packedLight,
                                     int packedOverlay, float red, float green, float blue, float alpha)
    {
        super.createVerticesOfQuad(quad, poseState, this.glow.lift(normal), buffer, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    @Override
    public void drawGlow(Runnable draw)
    {
        this.glow.draw(draw);
    }
}
