package com.reg21meme.dross.boss.huntsman.spear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * A spear in flight, drawn with the Moonsilver Spear's own 3D model (models/item/moonsilver_spear_in_hand.json, made by
 * tools/huntsman/spear.py) pointing the way it flies: a player's thrown Moonsilver Spear, and at
 * {@link com.reg21meme.dross.boss.huntsman.spear.SpearTuning#HUNTSMAN_SPEAR_SCALE} the Huntsman's own.
 */
public class SpearRenderer<T extends AbstractArrow> extends EntityRenderer<T>
{
    private final ItemRenderer items;
    private final float scale;
    private ItemStack stack;

    public SpearRenderer(EntityRendererProvider.Context context, float scale)
    {
        super(context);
        this.items = context.getItemRenderer();
        this.scale = scale;
    }

    @Override
    public void render(T spear, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light)
    {
        if (this.stack == null)
        {
            this.stack = new ItemStack(ModItems.MOONSILVER_SPEAR.get());
        }
        pose.pushPose();
        // (as Minecraft turns an arrow; the model's point is up its +y, so a quarter turn less than the trident's)
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(partialTicks, spear.yRotO, spear.getYRot()) - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(partialTicks, spear.xRotO, spear.getXRot()) - 90.0F));
        pose.scale(this.scale, this.scale, this.scale);
        this.items.renderStatic(this.stack, ItemDisplayContext.NONE, light, OverlayTexture.NO_OVERLAY, pose, buffers,
                spear.level(), spear.getId());
        pose.popPose();
        super.render(spear, yaw, partialTicks, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(T spear)
    {
        return InventoryMenu.BLOCK_ATLAS;
    }
}
