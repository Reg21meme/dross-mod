package com.reg21meme.dross.boss.huntsman.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reg21meme.dross.boss.client.PartLayer;
import com.reg21meme.dross.boss.huntsman.AlphaMoose;
import com.reg21meme.dross.boss.huntsman.HuntMob;
import com.reg21meme.dross.boss.huntsman.Huntsman;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;

/**
 * Draws the Huntsman or the Alpha Moose the way the Rimefall gallery drew their statues: the solid parts cut out, their
 * glowing bits full-bright (the Huntsman's eyes under the wolf's hood, the moose's eyes), then the see-through ice
 * blended over them (his spear's head, the moose's icicles and arrow fletchings) and the ice's own glow.
 */
public class HuntRenderer<T extends HuntMob> extends GeoEntityRenderer<T>
{
    private final HuntModel<T> huntModel;

    public HuntRenderer(EntityRendererProvider.Context context, HuntModel<T> model, float shadow)
    {
        super(context, model);
        this.huntModel = model;
        this.shadowRadius = shadow;
        // the glow (full-bright), the see-through ice (blended) and its glow: only the parts that show in each drawn again
        // for it ({@link PartLayer}: his eyes, his spear's head, the moose's eyes and icicles)
        this.addRenderLayer(new PartLayer<>(this, mob -> model.texture(mob, "_glow"), RenderType::entityTranslucentEmissive, true));
        this.addRenderLayer(new PartLayer<>(this, mob -> model.texture(mob, "_ice"), RenderType::entityTranslucent, false));
        this.addRenderLayer(new PartLayer<>(this, mob -> model.texture(mob, "_iceglow"), RenderType::entityTranslucentEmissive,
                true));
    }

    @Override
    public RenderType getRenderType(T mob, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

    /** In the saddle the Huntsman isn't drawn: the moose wears the Wild Hunt's model then, him in its saddle
     * ({@link AlphaMoose}). */
    @Override
    public void render(T mob, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int light)
    {
        if (mob instanceof Huntsman h && h.isMounted())
        {
            return;
        }
        super.render(mob, yaw, partialTick, poseStack, buffers, light);
    }

    /** Fighting, they die in their own death animations only: no tipping over onto their side as well (the user saw the
     * moose end up upside down, its legs through the snow). */
    @Override
    protected float getDeathMaxRotation(T mob)
    {
        return mob.isShowcase() ? super.getDeathMaxRotation(mob) : 0.0F;
    }

    /** They flash red when hurt, but not all the while they lie dead (the user saw both turn red as they died). */
    @Override
    public int getPackedOverlay(T mob, float u, float partialTick)
    {
        if (mob.isShowcase())
        {
            return super.getPackedOverlay(mob, u, partialTick);
        }
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(mob.hurtTime > 0 && !mob.isDeadOrDying()));
    }
}
