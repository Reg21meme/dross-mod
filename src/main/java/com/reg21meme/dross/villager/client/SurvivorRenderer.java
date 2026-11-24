package com.reg21meme.dross.villager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reg21meme.dross.quest.client.OracleClientState;
import com.reg21meme.dross.villager.Survivor;
import com.reg21meme.dross.villager.SurvivorRole;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws a hub survivor like the trader: the vanilla villager model with its trader skin (the Blind Oracle skin 8, the
 * Ragged Seeker skin 3), at the trader's size; the Oracle raises his arms while he casts his spell ({@link SurvivorModel}). Its name ("Blind Oracle", "Ragged Seeker") shows while you look at it,
 * the way a named mob's does. Over the Blind Oracle floats this player's quest marker ({@link QuestMarkerRenderer}).
 */
public class SurvivorRenderer extends LivingEntityRenderer<Survivor, VillagerModel<Survivor>>
{
    /** The trader's size (a villager's model, drawn a little smaller, like vanilla villagers). */
    private static final float SCALE = 0.9375F;

    public SurvivorRenderer(EntityRendererProvider.Context context)
    {
        super(context, new SurvivorModel(context.bakeLayer(ModelLayers.VILLAGER)), 0.5F);
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

    /** Draws him, then (only over the Blind Oracle, never the Seeker) this player's quest marker. */
    @Override
    public void render(Survivor survivor, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight)
    {
        super.render(survivor, entityYaw, partialTick, poseStack, buffers, packedLight);
        if (survivor.getRole() == SurvivorRole.MENTOR)
        {
            QuestMarkerRenderer.render(survivor, OracleClientState.marker(), partialTick, poseStack, buffers,
                    this.entityRenderDispatcher);
        }
    }

    /**
     * Minecraft only draws an entity whose box is in view. The Oracle's marker floats over it, so he's also drawn while
     * only his marker is in view (say, you look up at it standing beside him).
     */
    @Override
    public boolean shouldRender(Survivor survivor, Frustum frustum, double cameraX, double cameraY, double cameraZ)
    {
        if (super.shouldRender(survivor, frustum, cameraX, cameraY, cameraZ))
        {
            return true;
        }
        return survivor.getRole() == SurvivorRole.MENTOR && survivor.shouldRender(cameraX, cameraY, cameraZ)
                && QuestMarkerRenderer.isShown(OracleClientState.marker())
                && frustum.isVisible(QuestMarkerRenderer.cullingBox(survivor));
    }

    /** Like a mob with a name tag: its name shows while you look at it (and always if it's set to show). */
    @Override
    protected boolean shouldShowName(Survivor survivor)
    {
        return super.shouldShowName(survivor)
                && (survivor.shouldShowName() || survivor == this.entityRenderDispatcher.crosshairPickEntity);
    }
}
