package com.reg21meme.dross.boss.trophy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Vector3f;
import software.bernie.geckolib.cache.object.GeoBone;

/**
 * Small helpers both trophy renderers share.
 * <ul>
 *   <li>The glow passes' softer lighting: like the boss's renderer, every face's normal is bent halfway towards "up"
 *       before Minecraft lights it, so the glowing lava stays bright on the figure's sides.</li>
 *   <li>Bones a design hides (scaled to 0 by its pose) aren't drawn at all.</li>
 * </ul>
 */
final class TrophyGlow
{
    /** How far the glow passes bend normals towards "up" (the boss uses the same: CinderColossusRenderer). */
    private static final float GLOW_NORMAL_LIFT = 0.5F;

    /** "Up" in view space for the figure being drawn (normals are in view space). */
    private final Vector3f viewUp = new Vector3f(0.0F, 1.0F, 0.0F);
    private boolean glowPass;

    /** Works out where "up" is for this figure (before it's drawn; the pose holds the camera's turn and the item's). */
    void findUp(PoseStack poseStack)
    {
        poseStack.last().normal().transform(this.viewUp.set(0.0F, 1.0F, 0.0F)).normalize();
    }

    /** The normal to light a face with: as it is, or bent towards "up" during a glow pass. */
    Vector3f lift(Vector3f normal)
    {
        if (!this.glowPass)
        {
            return normal;
        }
        return new Vector3f(normal).add(this.viewUp.x() * GLOW_NORMAL_LIFT, this.viewUp.y() * GLOW_NORMAL_LIFT,
                this.viewUp.z() * GLOW_NORMAL_LIFT).normalize();
    }

    /** Runs a draw with the glow pass's softer lighting. */
    void draw(Runnable draw)
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

    /** True for a bone the design's pose hides (scaled to 0). */
    static boolean hidden(GeoBone bone)
    {
        return bone.getScaleX() == 0.0F && bone.getScaleY() == 0.0F && bone.getScaleZ() == 0.0F;
    }
}
