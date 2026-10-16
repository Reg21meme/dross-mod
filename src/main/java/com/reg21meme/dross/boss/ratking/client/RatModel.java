package com.reg21meme.dross.boss.ratking.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.ratking.Rat;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * A rat design's files (made by {@code tools/ratking/build.py}): {@code geo/entity/rat/<id>.geo.json},
 * {@code animations/entity/rat/<id>.animation.json}, {@code textures/entity/rat/<id>.png} and {@code <id>_glow.png}
 * (an enraged rat's red eyes, a ravager's boils).
 */
public class RatModel extends GeoModel<Rat>
{
    private static final Map<String, Files> FILES = new HashMap<>();

    /** One rat design's resource locations. */
    public record Files(ResourceLocation model, ResourceLocation animations, ResourceLocation texture, ResourceLocation glow)
    {
    }

    public static Files files(Rat rat)
    {
        return FILES.computeIfAbsent(rat.getDesign().id(), id -> new Files(
                new ResourceLocation(Dross.MODID, "geo/entity/rat/" + id + ".geo.json"),
                new ResourceLocation(Dross.MODID, "animations/entity/rat/" + id + ".animation.json"),
                new ResourceLocation(Dross.MODID, "textures/entity/rat/" + id + ".png"),
                new ResourceLocation(Dross.MODID, "textures/entity/rat/" + id + "_glow.png")));
    }

    @Override
    public ResourceLocation getModelResource(Rat rat)
    {
        return files(rat).model();
    }

    @Override
    public ResourceLocation getTextureResource(Rat rat)
    {
        return files(rat).texture();
    }

    @Override
    public ResourceLocation getAnimationResource(Rat rat)
    {
        return files(rat).animations();
    }
}
