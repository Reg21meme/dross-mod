package com.reg21meme.dross.villager.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.reg21meme.dross.quest.client.OracleClientState;
import com.reg21meme.dross.villager.DrossTrader;
import com.reg21meme.dross.villager.TraderSkins;
import net.minecraft.client.model.VillagerModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.resources.ResourceLocation;

/**
 * Draws the trader with the vanilla villager model and the quest trader's skin from {@link TraderSkins} (skin 2, The
 * Archivist). A skin texture fills the whole villager layout (hood or hat, robe, brim) in one image. Over him floats this
 * player's marker for his quest ({@link QuestMarkerRenderer}; PRE_DROSS_PLAN.md, "His quests"), like the Blind Oracle's.
 */
public class DrossTraderRenderer extends MobRenderer<DrossTrader, VillagerModel<DrossTrader>>
{
    private static final ResourceLocation TEXTURE = TraderSkins.lookFor(TraderSkins.QUEST_TRADER).texture();

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

    /** Draws him, then this player's marker for his quest. */
    @Override
    public void render(DrossTrader trader, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight)
    {
        super.render(trader, entityYaw, partialTick, poseStack, buffers, packedLight);
        QuestMarkerRenderer.render(trader, OracleClientState.traderMarker(), partialTick, poseStack, buffers, this.entityRenderDispatcher);
    }

    /**
     * Minecraft only draws an entity whose box is in view. His marker floats over it, so he's also drawn while only his
     * marker is in view (say, you look up at it standing beside him).
     */
    @Override
    public boolean shouldRender(DrossTrader trader, Frustum frustum, double cameraX, double cameraY, double cameraZ)
    {
        if (super.shouldRender(trader, frustum, cameraX, cameraY, cameraZ))
        {
            return true;
        }
        return trader.shouldRender(cameraX, cameraY, cameraZ) && QuestMarkerRenderer.isShown(OracleClientState.traderMarker())
                && frustum.isVisible(QuestMarkerRenderer.cullingBox(trader));
    }
}
