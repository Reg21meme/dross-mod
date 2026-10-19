package com.reg21meme.dross.boss.huntsman.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.HuntMob;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * The Huntsman's or the Alpha Moose's files (made by {@code tools/rimefall/mobs.py}): {@code geo/entity/<model>/},
 * {@code animations/entity/<model>/} and {@code textures/entity/<model>/<model>.png} with its {@code _glow},
 * {@code _ice} and {@code _iceglow} layers. Which model is worn can change (the Huntsman in the saddle wears
 * {@code huntsman_rider}, on foot {@code huntsman}).
 */
public class HuntModel<T extends HuntMob> extends GeoModel<T>
{
    private final Function<T, String> model;
    private final Map<String, ResourceLocation> cache = new HashMap<>();

    public HuntModel(Function<T, String> model)
    {
        this.model = model;
    }

    private ResourceLocation file(String path)
    {
        return this.cache.computeIfAbsent(path, p -> new ResourceLocation(Dross.MODID, p));
    }

    /** A texture layer: "" (the solid parts), "_glow", "_ice" or "_iceglow". */
    public ResourceLocation texture(T mob, String layer)
    {
        String m = this.model.apply(mob);
        return this.file("textures/entity/" + m + "/" + m + layer + ".png");
    }

    @Override
    public ResourceLocation getModelResource(T mob)
    {
        String m = this.model.apply(mob);
        return this.file("geo/entity/" + m + "/" + m + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(T mob)
    {
        return this.texture(mob, "");
    }

    @Override
    public ResourceLocation getAnimationResource(T mob)
    {
        String m = this.model.apply(mob);
        return this.file("animations/entity/" + m + "/" + m + ".animation.json");
    }
}
