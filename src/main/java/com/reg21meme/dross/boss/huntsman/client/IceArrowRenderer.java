package com.reg21meme.dross.boss.huntsman.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.IceArrow;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.ArrowRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;

/**
 * CLIENT ONLY. Draws the Huntsman's {@link IceArrow}: the arrow's own shape, its texture see-through (so its ice head
 * shows the world through it like the models' clear ice), then its glow added over it full-bright (its head glows
 * faintly in the night, the fletchings a little). It draws the arrow as Minecraft does, only handing that drawing the
 * see-through and glow layers in place of the plain cut-out one.
 */
public class IceArrowRenderer extends ArrowRenderer<IceArrow>
{
    public static final ResourceLocation TEXTURE =
            new ResourceLocation(Dross.MODID, "textures/entity/huntsman/ice_arrow.png");
    public static final ResourceLocation GLOW =
            new ResourceLocation(Dross.MODID, "textures/entity/huntsman/ice_arrow_glow.png");

    public IceArrowRenderer(EntityRendererProvider.Context context)
    {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(IceArrow arrow)
    {
        return TEXTURE;
    }

    @Override
    public void render(IceArrow arrow, float yaw, float partialTicks, PoseStack pose, MultiBufferSource buffers, int light)
    {
        RenderType cutout = RenderType.entityCutout(TEXTURE);
        super.render(arrow, yaw, partialTicks, pose,
                type -> buffers.getBuffer(type == cutout ? RenderType.entityTranslucent(TEXTURE) : type), light);
        super.render(arrow, yaw, partialTicks, pose,
                type -> buffers.getBuffer(type == cutout ? RenderType.eyes(GLOW) : type), light);
    }
}
