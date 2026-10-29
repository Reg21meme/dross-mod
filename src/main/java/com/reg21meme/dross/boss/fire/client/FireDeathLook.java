package com.reg21meme.dross.boss.fire.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.fire.FireCandidate;
import com.reg21meme.dross.boss.fire.FireDesigns;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;
import software.bernie.geckolib.renderer.layer.GeoRenderLayer;

import java.util.HashMap;
import java.util.Map;

/**
 * The Emberdactyl's deaths' looks, drawn after its other passes: the adult's glowing magma cracks spreading over it
 * as it lies dead, before it breaks apart ({@code <id>_cracks.png}, full-bright) and its young hardening into
 * an obsidian statue ({@code <id>_statue.png}, lit by the world, faded in over its glow). The textures are made by
 * {@code tools/fire/mobs.py} ({@code firemobs/emberdactyl.py}, {@code extra_textures}); each look's strength over its
 * animation's time is in {@link #LOOKS} (seconds, matching {@code firemobs/emberdactyl.py}'s {@code CRASH},
 * {@code BREAK_AT} and {@code HARDEN}; the young's 1.6 times quicker).
 */
public class FireDeathLook extends GeoRenderLayer<FireCandidate>
{
    /** Below this the look isn't drawn at all. */
    private static final float MIN_STRENGTH = 0.01F;

    /** One look: its texture's suffix, whether it glows, and its strength at moments of its animation (seconds,
     * strength, seconds, strength...; held before the first and after the last). */
    private record Look(String suffix, boolean glows, float[] keys)
    {
    }

    /** "mob/animation" to its look. */
    private static final Map<String, Look> LOOKS = Map.of(
            "emberdactyl/death", new Look("_cracks", true, new float[] {2.6F, 0.0F, 4.0F, 1.0F}),
            "emberyoung/death", new Look("_statue", false, new float[] {1.8F, 0.0F, 3.9F, 1.0F}));
    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<>();

    public FireDeathLook(GeoEntityRenderer<FireCandidate> renderer)
    {
        super(renderer);
    }

    @Override
    public void render(PoseStack poseStack, FireCandidate candidate, BakedGeoModel bakedModel, RenderType renderType,
                       MultiBufferSource bufferSource, VertexConsumer buffer, float partialTick, int packedLight,
                       int packedOverlay)
    {
        FireDesigns.Design design = candidate.getDesign();
        if (!design.animated())
        {
            return;
        }
        Look look = LOOKS.get(design.mob() + "/" + candidate.animation());
        if (look == null)
        {
            return;
        }
        float strength = strength(look.keys(), candidate.animationSeconds(partialTick));
        if (strength < MIN_STRENGTH)
        {
            return;
        }
        ResourceLocation texture = TEXTURES.computeIfAbsent(design.id() + look.suffix(),
                name -> new ResourceLocation(Dross.MODID, "textures/entity/firemob/" + name + ".png"));
        RenderType type = look.glows() ? RenderType.entityTranslucentEmissive(texture)
                : RenderType.entityTranslucent(texture);
        this.getRenderer().reRender(bakedModel, poseStack, bufferSource, candidate, type, bufferSource.getBuffer(type),
                partialTick, look.glows() ? LightTexture.FULL_BRIGHT : packedLight, packedOverlay,
                1.0F, 1.0F, 1.0F, strength);
    }

    /** A look's strength at `seconds` into its animation (straight lines between its keys). */
    static float strength(float[] keys, float seconds)
    {
        if (seconds <= keys[0])
        {
            return keys[1];
        }
        for (int i = 2; i < keys.length; i += 2)
        {
            if (seconds <= keys[i])
            {
                float a = (seconds - keys[i - 2]) / (keys[i] - keys[i - 2]);
                return keys[i - 1] + (keys[i + 1] - keys[i - 1]) * a;
            }
        }
        return keys[keys.length - 1];
    }
}
