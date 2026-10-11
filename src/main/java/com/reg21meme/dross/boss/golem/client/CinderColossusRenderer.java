package com.reg21meme.dross.boss.golem.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.boss.golem.CinderColossus;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3d;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.cache.object.GeoBone;
import software.bernie.geckolib.cache.object.GeoQuad;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

/**
 * Draws a Cinder Colossus with GeckoLib, in four passes:
 * <ol>
 *   <li>the model with its main texture (lit by the world like any mob);</li>
 *   <li>{@link GolemFlowLayer}: the lava poured over the shell when its volcano erupts, at full brightness;</li>
 *   <li>{@link GolemGlowLayer}: the glowing parts (lava cracks, the lava body, the eyes) at full brightness;</li>
 *   <li>{@link GolemStatueLayer}: the obsidian statue texture, faded in while it dies.</li>
 * </ol>
 * Bones it shouldn't show right now are skipped (see {@link CinderColossus#showsBone}). After drawing, it tells the
 * golem where its volcano craters are (the {@code *_crater} bones) and which way they point (towards the
 * {@code *_crater_aim} bone on each cone's axis), so the particles come out of them along the volcano.
 */
public class CinderColossusRenderer extends GeoEntityRenderer<CinderColossus>
{
    /** Shadow size (blocks): it's about four blocks across. */
    private static final float SHADOW_RADIUS = 1.8F;
    /**
     * The glow pass bends every face's normal this far towards "up" before Minecraft lights it, so glowing lava stays
     * bright on the golem's sides instead of dropping to half brightness. The preview renderer uses the same number
     * (tools/golems/kit/render.py, GLOW_NORMAL_LIFT).
     */
    private static final float GLOW_NORMAL_LIFT = 0.5F;

    /** Straight up, for a crater with no aim bone. */
    private static final Vec3 UP = new Vec3(0.0D, 1.0D, 0.0D);
    /** "Up" in view space for the golem being drawn (normals are in view space). */
    private final Vector3f viewUp = new Vector3f(0.0F, 1.0F, 0.0F);
    private boolean glowPass;
    /** Each model's crater bones, each with the bone on its cone's axis (or null), found once per model. */
    private final Map<BakedGeoModel, List<GeoBone[]>> craters = new IdentityHashMap<>();

    public CinderColossusRenderer(EntityRendererProvider.Context context)
    {
        super(context, new CinderColossusModel());
        this.shadowRadius = SHADOW_RADIUS;
        this.addRenderLayer(new GolemFlowLayer(this));
        this.addRenderLayer(new GolemGlowLayer(this));
        this.addRenderLayer(new GolemStatueLayer(this));
    }

    @Override
    public void preRender(PoseStack poseStack, CinderColossus golem, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha)
    {
        if (!isReRender)
        {
            // The pose here is the camera's rotation (plus a move to the golem), so this is world "up" as the camera sees it.
            poseStack.last().normal().transform(this.viewUp.set(0.0F, 1.0F, 0.0F)).normalize();
            // Track the craters' positions this frame (GeckoLib works them out while it draws those bones).
            for (GeoBone[] crater : this.craterBones(model))
            {
                for (GeoBone bone : crater)
                {
                    if (bone != null)
                    {
                        bone.setTrackingMatrices(true);
                    }
                }
            }
        }
        super.preRender(poseStack, golem, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
    }

    @Override
    public void postRender(PoseStack poseStack, CinderColossus golem, BakedGeoModel model, MultiBufferSource bufferSource,
                           VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                           float red, float green, float blue, float alpha)
    {
        super.postRender(poseStack, golem, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay,
                red, green, blue, alpha);
        if (!isReRender)
        {
            // Bones are shared by every golem with this model, so read them now, straight after drawing this one.
            List<CinderColossus.Crater> spots = new ArrayList<>();
            for (GeoBone[] crater : this.craterBones(model))
            {
                Vector3d p = crater[0].getWorldPosition();
                Vec3 aim = UP;
                if (crater[1] != null)
                {
                    Vector3d q = crater[1].getWorldPosition();
                    Vec3 d = new Vec3(q.x - p.x, q.y - p.y, q.z - p.z);
                    if (d.lengthSqr() > 1.0E-6D)
                    {
                        aim = d.normalize();
                    }
                }
                spots.add(new CinderColossus.Crater(new Vec3(p.x, p.y, p.z), aim));
            }
            golem.setCraters(spots);
        }
    }

    /** The marker bones at the volcano craters (named {@code *_crater}), each with its {@code *_crater_aim} bone. */
    private List<GeoBone[]> craterBones(BakedGeoModel model)
    {
        return this.craters.computeIfAbsent(model, m -> {
            List<GeoBone[]> found = new ArrayList<>();
            collectCraters(m.topLevelBones(), found);
            return found;
        });
    }

    private static void collectCraters(List<GeoBone> bones, List<GeoBone[]> found)
    {
        for (GeoBone bone : bones)
        {
            if (bone.getName().endsWith("_crater"))
            {
                GeoBone aim = null;
                if (bone.getParent() != null)
                {
                    for (GeoBone sibling : bone.getParent().getChildBones())
                    {
                        if (sibling.getName().equals(bone.getName() + "_aim"))
                        {
                            aim = sibling;
                        }
                    }
                }
                found.add(new GeoBone[] {bone, aim});
            }
            collectCraters(bone.getChildBones(), found);
        }
    }

    @Override
    public void renderRecursively(PoseStack poseStack, CinderColossus golem, GeoBone bone, RenderType renderType,
                                  MultiBufferSource bufferSource, VertexConsumer buffer, boolean isReRender, float partialTick,
                                  int packedLight, int packedOverlay, float red, float green, float blue, float alpha)
    {
        if (!golem.showsBone(bone.getName(), partialTick))
        {
            return;
        }
        super.renderRecursively(poseStack, golem, bone, renderType, bufferSource, buffer, isReRender, partialTick,
                packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public void createVerticesOfQuad(GeoQuad quad, Matrix4f poseState, Vector3f normal, VertexConsumer buffer, int packedLight,
                                     int packedOverlay, float red, float green, float blue, float alpha)
    {
        if (this.glowPass)
        {
            normal = new Vector3f(normal).add(this.viewUp.x() * GLOW_NORMAL_LIFT, this.viewUp.y() * GLOW_NORMAL_LIFT,
                    this.viewUp.z() * GLOW_NORMAL_LIFT).normalize();
        }
        super.createVerticesOfQuad(quad, poseState, normal, buffer, packedLight, packedOverlay, red, green, blue, alpha);
    }

    /** Runs a draw with the glow pass's softer lighting (used by {@link GolemGlowLayer} and {@link GolemFlowLayer}). */
    void drawGlow(Runnable draw)
    {
        this.glowPass = true;
        try
        {
            draw.run();
        }
        finally
        {
            this.glowPass = false;
        }
    }
}
