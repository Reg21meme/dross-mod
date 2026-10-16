package com.reg21meme.dross.boss.ratking.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.ratking.RatKing;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * A Rat King design's files (made by {@code tools/ratking/build.py}): {@code geo/entity/rat_king/<id>.geo.json},
 * {@code animations/entity/rat_king/<id>.animation.json}, and {@code textures/entity/rat_king/<id>.png} with its
 * {@code <id>_glow.png} (orbs, gems, candle flames) and {@code <id>_rage.png} (his red eyes).
 */
public class RatKingModel extends GeoModel<RatKing>
{
    private static final Map<String, Files> FILES = new HashMap<>();

    /** One design's resource locations. */
    public record Files(ResourceLocation model, ResourceLocation animations, ResourceLocation texture,
                        ResourceLocation glow, ResourceLocation rage)
    {
    }

    public static Files files(RatKing king)
    {
        return FILES.computeIfAbsent(king.getDesign().id(), id -> new Files(
                new ResourceLocation(Dross.MODID, "geo/entity/rat_king/" + id + ".geo.json"),
                new ResourceLocation(Dross.MODID, "animations/entity/rat_king/" + id + ".animation.json"),
                new ResourceLocation(Dross.MODID, "textures/entity/rat_king/" + id + ".png"),
                new ResourceLocation(Dross.MODID, "textures/entity/rat_king/" + id + "_glow.png"),
                new ResourceLocation(Dross.MODID, "textures/entity/rat_king/" + id + "_rage.png")));
    }

    @Override
    public ResourceLocation getModelResource(RatKing king)
    {
        return files(king).model();
    }

    @Override
    public ResourceLocation getTextureResource(RatKing king)
    {
        return files(king).texture();
    }

    @Override
    public ResourceLocation getAnimationResource(RatKing king)
    {
        return files(king).animations();
    }
}
