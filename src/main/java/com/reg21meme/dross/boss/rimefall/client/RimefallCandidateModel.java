package com.reg21meme.dross.boss.rimefall.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.rimefall.RimefallCandidate;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * A Rimefall candidate's files (made by {@code tools/rimefall/build.py}): {@code geo/entity/rimefall/<id>.geo.json}
 * and {@code textures/entity/rimefall/<id>.png} with its {@code <id>_glow.png}, {@code <id>_ice.png} and
 * {@code <id>_iceglow.png} (the ones its design has). The models never move, so they all share one empty animation
 * file (GeckoLib asks for one, and never reads it without an animation to play).
 */
public class RimefallCandidateModel extends GeoModel<RimefallCandidate>
{
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(Dross.MODID,
            "animations/entity/rimefall/candidate.animation.json");
    private static final Map<String, Files> FILES = new HashMap<>();

    /** One design's resource locations. */
    public record Files(ResourceLocation model, ResourceLocation texture, ResourceLocation glow, ResourceLocation ice,
                        ResourceLocation iceGlow)
    {
    }

    public static Files files(RimefallCandidate candidate)
    {
        return FILES.computeIfAbsent(candidate.getDesign().id(), id -> new Files(
                new ResourceLocation(Dross.MODID, "geo/entity/rimefall/" + id + ".geo.json"),
                new ResourceLocation(Dross.MODID, "textures/entity/rimefall/" + id + ".png"),
                new ResourceLocation(Dross.MODID, "textures/entity/rimefall/" + id + "_glow.png"),
                new ResourceLocation(Dross.MODID, "textures/entity/rimefall/" + id + "_ice.png"),
                new ResourceLocation(Dross.MODID, "textures/entity/rimefall/" + id + "_iceglow.png")));
    }

    @Override
    public ResourceLocation getModelResource(RimefallCandidate candidate)
    {
        return files(candidate).model();
    }

    @Override
    public ResourceLocation getTextureResource(RimefallCandidate candidate)
    {
        return files(candidate).texture();
    }

    @Override
    public ResourceLocation getAnimationResource(RimefallCandidate candidate)
    {
        return ANIMATIONS;
    }
}
