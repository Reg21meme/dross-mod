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
 * Core stage), a lava blob as a glowing lump of magma. A Rockfall Knight's boulder ({@link ColossusRock#boulderSize})
 * is a lumpy clump: a core of mossy cobblestone with knobs of stone, andesite, tuff and moss bulging out of it,
 * different on every boulder, tumbling slower the bigger it is.
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

    /** A boulder's knobs: where each sits (a share of its size from its middle) and how big (a share of its size). */
    private static final float[][] KNOBS = {
            {0.34F, 0.08F, 0.05F, 0.62F}, {-0.32F, -0.06F, 0.1F, 0.6F}, {0.05F, 0.33F, -0.08F, 0.58F},
            {-0.06F, -0.3F, 0.02F, 0.6F}, {0.1F, 0.04F, 0.34F, 0.56F}, {-0.04F, 0.1F, -0.33F, 0.6F},
            {0.24F, 0.24F, 0.22F, 0.42F}, {-0.25F, 0.2F, -0.22F, 0.4F}};
    private static final BlockState[] KNOB_LOOKS = {Blocks.STONE.defaultBlockState(), Blocks.ANDESITE.defaultBlockState(),
            Blocks.MOSSY_COBBLESTONE.defaultBlockState(), Blocks.TUFF.defaultBlockState(), Blocks.MOSS_BLOCK.defaultBlockState(),
            Blocks.COBBLESTONE.defaultBlockState()};

    @Override
    public void render(T entity, float yaw, float partialTick, PoseStack poseStack, MultiBufferSource buffers, int packedLight)
    {
        if (entity instanceof ColossusRock rock && rock.boulderSize() > 0.0F)
        {
            this.renderBoulder(rock, rock.boulderSize(), partialTick, poseStack, buffers, packedLight);
            super.render(entity, yaw, partialTick, poseStack, buffers, packedLight);
            return;
        }
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

    private void renderBoulder(ColossusRock rock, float size, float partialTick, PoseStack poseStack, MultiBufferSource buffers,
                               int packedLight)
    {
        float age = rock.tickCount + partialTick;
        float spin = SPIN * 0.55F / Math.max(size, 0.5F);
        int seed = rock.getId();
        poseStack.pushPose();
        poseStack.translate(0.0F, size * 0.5F, 0.0F);
        poseStack.mulPose(Axis.YP.rotationDegrees(age * spin * 0.6F + seed * 37.0F));
        poseStack.mulPose(Axis.XP.rotationDegrees(age * spin + seed * 11.0F));
        // its core, a little smaller than the whole
        this.cube(Blocks.MOSSY_COBBLESTONE.defaultBlockState(), 0.0F, 0.0F, 0.0F, size * 0.72F, poseStack, buffers, packedLight);
        for (int i = 0; i < KNOBS.length; i++)
        {
            float[] k = KNOBS[i];
            if (((seed >> i) & 3) == 0 && i >= 6)
            {
                continue;
            }
            BlockState look = KNOB_LOOKS[Math.floorMod(seed * 7 + i * 3, KNOB_LOOKS.length)];
            this.cube(look, k[0] * size, k[1] * size, k[2] * size, k[3] * size, poseStack, buffers, packedLight);
        }
        poseStack.popPose();
    }

    private void cube(BlockState look, float x, float y, float z, float edge, PoseStack poseStack, MultiBufferSource buffers,
                      int packedLight)
    {
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.scale(edge, edge, edge);
        poseStack.translate(-0.5F, -0.5F, -0.5F);
        this.blocks.renderSingleBlock(look, poseStack, buffers, packedLight, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
        poseStack.popPose();
    }

    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(T entity)
    {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
