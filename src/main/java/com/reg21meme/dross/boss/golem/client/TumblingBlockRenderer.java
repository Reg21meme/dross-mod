package com.reg21meme.dross.boss.golem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.reg21meme.dross.boss.golem.BossProjectile;
import com.reg21meme.dross.boss.golem.ColossusRock;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;

/**
 * Draws what a Cinder Colossus throws as a small tumbling block: the thrown rock as cobblestone (magma in the Lava
 * Core stage), a lava blob as a glowing lump of magma.
 */
public class TumblingBlockRenderer<T extends BossProjectile> extends EntityRenderer<T>
{
    /** How fast it tumbles (degrees a tick). */
    private static final float SPIN = 18.0F;

    private final BlockRenderDispatcher blocks;
    /** Its size (blocks across). */
    private final float size;
    /** True to draw it full-bright (it glows: lava). */
    private final boolean glowing;

    public TumblingBlockRenderer(EntityRendererProvider.Context context, float size, boolean glowing)
    {
        super(context);
        this.blocks = context.getBlockRenderDispatcher();
        this.size = size;
        this.glowing = glowing;
        this.shadowRadius = size * 0.4F;
    }

    /** A thrown rock: 0.8 blocks of cobblestone (or magma), lit by the world (magma glows). */
    public static TumblingBlockRenderer<ColossusRock> rock(EntityRendererProvider.Context context)
    {
        return new TumblingBlockRenderer<>(context, 0.8F, false);
    }

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight)
    {
        BlockState look = entity instanceof ColossusRock rock ? rock.look() : Blocks.MAGMA_BLOCK.defaultBlockState();
        boolean bright = this.glowing || look.is(Blocks.MAGMA_BLOCK);
        float age = entity.tickCount + partialTick;
        poseStack.pushPose();
        poseStack.translate(0.0F, this.size / 2.0F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * SPIN * 0.7F + entity.getId() * 37.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * SPIN));
        poseStack.scale(this.size, this.size, this.size);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        this.blocks.renderSingleBlock(look, poseStack, buffers, bright ? LightTexture.FULL_BRIGHT : packedLight,
                OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        poseStack.popPose();
        super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(T entity)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
