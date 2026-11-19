package com.reg21meme.dross.boss.knight.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.knight.KnightCandidate;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * An elemental knight's files (made by {@code tools/knights/build.py}): {@code geo/entity/knight/<id>.geo.json} and
 * {@code textures/entity/knight/<id>.png} with its {@code <id>_glow.png}, {@code <id>_clear.png} and
 * {@code <id>_clearglow.png} (the ones its design has). The models never move, so they all share one empty animation
 * file (GeckoLib asks for one, and never reads it without an animation to play).
 */
public class KnightCandidateModel extends GeoModel<KnightCandidate>
{
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(Dross.MODID,
            "animations/entity/knight/candidate.animation.json");
    private static final Map<String, Files> FILES = new HashMap<>();

    /** One design's resource locations. */
    public record Files(ResourceLocation model, ResourceLocation texture, ResourceLocation glow, ResourceLocation clear,
                        ResourceLocation clearGlow)
    {
    }

    public static Files files(KnightCandidate candidate)
    {
        return FILES.computeIfAbsent(candidate.getDesign().id(), id -> new Files(
                new ResourceLocation(Dross.MODID, "geo/entity/knight/" + id + ".geo.json"),
                new ResourceLocation(Dross.MODID, "textures/entity/knight/" + id + ".png"),
                new ResourceLocation(Dross.MODID, "textures/entity/knight/" + id + "_glow.png"),
                new ResourceLocation(Dross.MODID, "textures/entity/knight/" + id + "_clear.png"),
                new ResourceLocation(Dross.MODID, "textures/entity/knight/" + id + "_clearglow.png")));
    }

    @Override
    public ResourceLocation getModelResource(KnightCandidate candidate)
    {
        return files(candidate).model();
    }

    @Override
    public ResourceLocation getTextureResource(KnightCandidate candidate)
    {
        return files(candidate).texture();
    }

    @Override
    public ResourceLocation getAnimationResource(KnightCandidate candidate)
    {
        return ANIMATIONS;
    }
}
