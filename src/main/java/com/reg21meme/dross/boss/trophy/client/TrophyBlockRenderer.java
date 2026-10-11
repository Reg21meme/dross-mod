package com.reg21meme.dross.boss.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.trophy.ColossusTrophies;
import com.reg21meme.dross.boss.trophy.ColossusTrophyBlockEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.renderer.GeoBlockRenderer;

/**
 * Draws a placed Cinder Colossus trophy: the boss's model in its design's held pose, scaled down to fit the block
 * (the design's scale, {@code TrophyModels}), standing in the middle of it and facing its {@code FACING}; then
 * {@link TrophyLayer} (the glow, the cracks and the flowing lava).
 */
public class TrophyBlockRenderer extends GeoBlockRenderer<ColossusTrophyBlockEntity> implements TrophyLayer.Host
{
    /** How much each block position shifts its flow's loop, so trophies side by side don't flow in step. */
    private static final double PHASE_SPREAD = 0.37D;

    private final TrophyGlow glow = new TrophyGlow();

    public TrophyBlockRenderer(BlockEntityRendererProvider.Context context)
    {
        super(new TrophyGeoModel<>(TrophyGeoModel.BLOCK_MODEL));
        this.addRenderLayer(new TrophyLayer<>(this, this, ColossusTrophyBlockEntity::design,
                trophy -> Mth.frac(trophy.getBlockPos().hashCode() * PHASE_SPREAD)));
    }

    /** Turns the figure to face its way (GeckoLib), then scales it down to fit the block. */
    @Override
    protected void rotateBlock(Direction facing, PoseStack poseStack)
    {
        super.rotateBlock(facing, poseStack);
        float scale = ColossusTrophies.get(this.animatable.design()).model().scale();
        poseStack.scale(scale, scale, scale);
    }

    @Override
    public void preRender(PoseStack poseStack, ColossusTrophyBlockEntity trophy, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        if (!isReRender)
        {
            this.glow.findUp(poseStack);
        }
        super.preRender(poseStack, trophy, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    /** Bones the design hides are scaled to 0 by its pose: skip them (and everything on them). */
    @Override
    public void renderRecursively(PoseStack poseStack, ColossusTrophyBlockEntity trophy, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        if (TrophyGlow.hidden(bone))
        {
            return;
        }
        super.renderRecursively(poseStack, trophy, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer, int packedLight,
                                     int packedOverlay, float red, float green, float blue, float alpha)
    {
        super.createVerticesOfQuad(quad, poseState, this.glow.lift(normal), buffer, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    @Override
    public void drawGlow(Runnable draw)
    {
        this.glow.draw(draw);
    }
}
