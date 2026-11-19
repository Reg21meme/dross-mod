package com.reg21meme.dross.boss.giant.client;

import com.reg21meme.dross.boss.client.PartLayer;
import com.reg21meme.dross.boss.giant.SilverwoodGiant;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import javax.annotation.Nullable;

/**
 * Draws the Silverwood Giant with GeckoLib: its look cut out (its ragged kilt, its hair), both sides of every face, then
 * its glow full-bright (its electric blue eyes, its people's sign, the edge of its spear; only the parts that show any:
 * {@link PartLayer}). It dies by its own animation (it falls on its face): no tipping over and no red tint while it lies
 * there.
 */
public class GiantRenderer extends GeoEntityRenderer<SilverwoodGiant>
{
    /** Its shadow's radius (blocks). */
    private static final float SHADOW = 1.3F;

    public GiantRenderer(EntityRendererProvider.Context context)
    {
        this(context, new GiantModel());
    }

    private GiantRenderer(EntityRendererProvider.Context context, GiantModel model)
    {
        super(context, model);
        this.shadowRadius = SHADOW;
        this.addRenderLayer(new PartLayer<>(this, model::glow, RenderType::entityTranslucentEmissive, true));
    }

    @Override
    public RenderType getRenderType(SilverwoodGiant giant, ResourceLocation texture, @Nullable MultiBufferSource bufferSource,
                                    float partialTick)
    {
        return RenderType.entityCutoutNoCull(texture);
    }

}
