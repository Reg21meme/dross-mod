package com.reg21meme.dross.villager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reg21meme.dross.villager.DrossTrader;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/** Draws the trader with the vanilla villager model and the vanilla villager skin (placeholder). */
public class DrossTraderRenderer extends MobRenderer<DrossTrader, VillagerModel<DrossTrader>>
{
    /**
     * PLACEHOLDER SKIN. Points at the vanilla villager texture. To use a custom skin later, put a PNG in
     * assets/dross/textures/entity/ and change this one line to new ResourceLocation("dross", "textures/entity/dross_trader.png").
     */
    public static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/entity/villager/villager.png");

    public DrossTraderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(DrossTrader entity)
    {
        return TEXTURE;
    }

    @Override
    protected void scale(DrossTrader entity, PoseStack poseStack, float partialTick)
    {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
