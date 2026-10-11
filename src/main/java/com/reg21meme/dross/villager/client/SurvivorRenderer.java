package com.reg21meme.dross.villager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reg21meme.dross.villager.Survivor;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a hub survivor like the trader: the vanilla villager model with its trader skin (the Blind Oracle skin 8, the
 * Ragged Seeker skin 3), at the trader's size. Its name ("Blind Oracle", "Ragged Seeker") shows while you look at it,
 * the way a named mob's does.
 */
public class SurvivorRenderer extends LivingEntityRenderer<Survivor, VillagerModel<Survivor>>
{
    /** The trader's size (a villager's model, drawn a little smaller, like vanilla villagers). */
    private static final float SCALE = 0.9375F;

    public SurvivorRenderer(EntityRendererProvider.Context context)
    {
        super(context, new VillagerModel<>(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
    }

    @Override
    public ResourceLocation getTextureLocation(Survivor survivor)
    {
        return survivor.getRole().skin().texture();
    }

    @Override
    protected void scale(Survivor survivor, PoseStack poseStack, float partialTick)
    {
        poseStack.scale(SCALE, SCALE, SCALE);
    }

    /** Like a mob with a name tag: its name shows while you look at it (and always if it's set to show). */
    @Override
    protected boolean shouldShowName(Survivor survivor)
    {
        return super.shouldShowName(survivor)
                && (survivor.shouldShowName() || survivor == this.entityRenderDispatcher.crosshairPickEntity);
    }
}
