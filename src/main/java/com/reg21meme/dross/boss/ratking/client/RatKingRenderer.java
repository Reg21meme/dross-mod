package com.reg21meme.dross.boss.ratking.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.boss.ratking.RatKingAction;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3d;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import javax.annotation.Nullable;

/**
 * Draws the Rat King with GeckoLib: his texture lit by the world, then two full-bright layers on top: the glowing
 * parts of his design (an orb, gems, candle flames, a censer: {@code <id>_glow.png}) and his red eyes once he's
 * enraged ({@code <id>_rage.png}, faded by {@link RatKing#rageStrength}).
 * <p>
 * It also reads where his marker bones are each time it draws him (the spot above his head the Zzz float up from,
 * and his ears and which way the steam blows out of them) and hands them to him for his particles. Bones are shared
 * by every Rat King of the same design, so they're read right after drawing each one. His dirt hole (the {@code hole}
 * bone) is hidden unless he's burrowing, under the ground or popping up.
 */
public class RatKingRenderer extends GeoEntityRenderer<RatKing>
{
    /** Shadow size (blocks); none while he's under the ground. */
    private static final float SHADOW_RADIUS = 0.8F;
    private static final String[] MARKERS = {"zzz", "ear_l_steam", "ear_l_steam_aim", "ear_r_steam", "ear_r_steam_aim"};
    /** The bone of his dirt hole and mound (with its clods): drawn only while he burrows or pops up. */
    private static final String HOLE_BONE = "hole";

    public RatKingRenderer(EntityRendererProvider.Context context)
    {
        super(context, new RatKingModel());
        this.shadowRadius = SHADOW_RADIUS;
        this.addRenderLayer(new GlowLayer(this));
        this.addRenderLayer(new RageLayer(this));
    }

    @Override
    public void render(RatKing king, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource,
                       int packedLight)
    {
        // No shadow while he's under the ground (the dirt mound shows where he is).
        this.shadowRadius = king.getAction() == RatKingAction.BURROWED ? 0.0F : SHADOW_RADIUS;
        super.render(king, entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    /** A fighting king's silly death lies him flat on his back himself: no vanilla tip-over (a showcase king's /kill keeps it). */
    @Override
    protected float getDeathMaxRotation(RatKing king)
    {
        return king.isShowcase() ? super.getDeathMaxRotation(king) : 0.0F;
    }

    /** He flashes red when hurt, but not all the while he lies dead. */
    @Override
    public int getPackedOverlay(RatKing king, float u, float partialTick)
    {
        if (king.isShowcase())
        {
            return super.getPackedOverlay(king, u, partialTick);
        }
        return OverlayTexture.pack(OverlayTexture.u(u), OverlayTexture.v(king.hurtTime > 0 && !king.isDeadOrDying()));
    }

    @Override
    public void preRender(PoseStack poseStack, RatKing king, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        if (!isReRender)
        {
            for (String name : MARKERS)
            {
                model.getBone(name).ifPresent(bone -> bone.setTrackingMatrices(true));
            }
        }
        // His dirt hole only shows while he burrows, is under the ground or pops up; the rest of the time it's hidden
        // (it waits just under the ground, and would show under him when he's knocked up into the air). Set on every
        // draw (the layers' too): GeckoLib shares the bones between every Rat King of the same design.
        boolean hole = showsHole(king.getAction());
        model.getBone(HOLE_BONE).ifPresent(bone -> bone.setHidden(!hole));
        super.preRender(poseStack, king, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    /** True while his dirt hole may show: while he burrows, is under the ground, or pops up (a showcase king too). */
    static boolean showsHole(RatKingAction action)
    {
        return action == RatKingAction.BURROW || action == RatKingAction.BURROWED || action == RatKingAction.POPUP;
    }

    @Override
    public void postRender(PoseStack poseStack, RatKing king, BakedGeoModel model, MultiBufferSource bufferSource,
                           VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                           float red, float green, float blue, float alpha)
    {
        super.postRender(poseStack, king, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
        if (!isReRender)
        {
            Vec3 zzz = spot(model, "zzz");
            Vec3 earL = spot(model, "ear_l_steam");
            Vec3 earR = spot(model, "ear_r_steam");
            king.setMarkers(zzz, earL, aim(earL, spot(model, "ear_l_steam_aim")), earR, aim(earR, spot(model, "ear_r_steam_aim")));
        }
    }

    @Nullable
    private static Vec3 spot(BakedGeoModel model, String name)
    {
        GeoBone bone = model.getBone(name).orElse(null);
        if (bone == null)
        {
            return null;
        }
        Vector3d p = bone.getWorldPosition();
        return new Vec3(p.x, p.y, p.z);
    }

    @Nullable
    private static Vec3 aim(@Nullable Vec3 from, @Nullable Vec3 to)
    {
        if (from == null || to == null)
        {
            return null;
        }
        Vec3 d = to.subtract(from);
        return d.lengthSqr() > 1.0E-6D ? d.normalize() : null;
    }

    /** {@code <id>_glow.png} at full brightness, for the designs that have glowing parts. */
    private static class GlowLayer extends GeoRenderLayer<RatKing>
    {
        GlowLayer(RatKingRenderer renderer)
        {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, RatKing king, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
        {
            if (!king.getDesign().hasGlow())
            {
                return;
            }
            RenderType glow = RenderType.entityTranslucentEmissive(RatKingModel.files(king).glow());
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, king, glow, bufferSource.getBuffer(glow),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /** His red eyes ({@code <id>_rage.png}) at full brightness, faded in and out by {@link RatKing#rageStrength}. */
    private static class RageLayer extends GeoRenderLayer<RatKing>
    {
        RageLayer(RatKingRenderer renderer)
        {
            super(renderer);
        }

        @Override
        public void render(PoseStack poseStack, RatKing king, BakedGeoModel bakedModel, RenderType renderType,
                           MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight, int packedOverlay)
        {
            float strength = king.rageStrength(partialTick);
            if (strength < 0.01F)
            {
                return;
            }
            RenderType rage = RenderType.entityTranslucentEmissive(RatKingModel.files(king).rage());
            this.getRenderer().reRender(bakedModel, poseStack, bufferSource, king, rage, bufferSource.getBuffer(rage),
                    partialTick, LightTexture.FULL_BRIGHT, packedOverlay, 1.0F, 1.0F, 1.0F, strength);
        }
    }
}
