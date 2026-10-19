package com.reg21meme.dross.boss.rimefall.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.rimefall.RimefallBlocks;
import com.reg21meme.dross.boss.rimefall.RimefallCandidate;
import com.reg21meme.dross.boss.rimefall.RimefallDesigns;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.List;

/**
 * Draws a candidate's real Minecraft blocks ({@link RimefallDesigns.Placed}: the Fisher's bait cabin) with the game's
 * own block models and textures, so they look exactly like blocks in the world. They're drawn with the model's root
 * bone (every candidate's boxes sit on it and it never moves), in GeckoLib's model space: x is the model's own x
 * flipped, so a block whose lowest corner is at Bedrock (x, y, z) units fills GeckoLib x from -(x + 16) / 16 to -x / 16.
 * A bright block (a lit lantern) is drawn full-bright.
 */
public class RimefallBlocksLayer extends GeoRenderLayer<RimefallCandidate>
{
    private static final String ROOT = "root";

    public RimefallBlocksLayer(GeoRenderer<RimefallCandidate> renderer)
    {
        super(renderer);
    }

    @Override
    public void renderForBone(PoseStack poseStack, RimefallCandidate candidate, GeoBone bone, RenderType renderType,
                              MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                              int packedOverlay)
    {
        if (!ROOT.equals(bone.getName()))
        {
            return;
        }
        List<RimefallDesigns.Placed> blocks = candidate.getDesign().blocks();
        if (blocks.isEmpty())
        {
            return;
        }
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        for (RimefallDesigns.Placed placed : blocks)
        {
            BlockState state = RimefallBlocks.stateOf(placed.state());
            if (state.isAir())
            {
                continue;
            }
            poseStack.pushPose();
            poseStack.translate(-(placed.x() + 16.0F) / 16.0F, placed.y() / 16.0F, placed.z() / 16.0F);
            dispatcher.renderSingleBlock(state, poseStack, bufferSource,
                    placed.bright() ? LightTexture.FULL_BRIGHT : packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY,
                    null);
            poseStack.popPose();
        }
    }
}
