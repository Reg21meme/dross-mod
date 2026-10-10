package com.reg21meme.dross.villager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reg21meme.dross.villager.DrossTrader;
import com.reg21meme.dross.villager.TraderSkins;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the trader with the vanilla villager model and his skin from {@link TraderSkins}: the quest trader's look
 * (skin 2, The Archivist) for every real trader (skin number 0), or the candidate skin a showcase trader's number
 * names. A skin texture fills the whole villager layout (hood or hat, robe, brim) in one image.
 */
public class DrossTraderRenderer extends MobRenderer<DrossTrader, VillagerModel<DrossTrader>>
{
    public DrossTraderRenderer(EntityRendererProvider.Context context)
    {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(DrossTrader entity)
    {
        return TraderSkins.lookFor(entity.getSkin()).texture();
    }

    @Override
    protected void scale(DrossTrader entity, PoseStack poseStack, float partialTick)
    {
        poseStack.scale(0.9375F, 0.9375F, 0.9375F);
    }
}
