package com.reg21meme.dross.boss.knight.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.knight.ElementalKnight;
import com.reg21meme.dross.boss.knight.KnightKind;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.EnumMap;
import java.util.Map;

/**
 * A real elemental knight's files (made by {@code tools/knights/mobs.py}): {@code geo/entity/knightmob/<model>.geo.json},
 * {@code animations/entity/knightmob/<model>.animation.json} and {@code textures/entity/knightmob/<model>.png} with its
 * {@code _glow}, {@code _ice} (see-through) and {@code _iceglow} layers (the ones it has: boss/knight/KnightAnims).
 */
public class KnightMobModel extends GeoModel<ElementalKnight>
{
    private static final Map<KnightKind, Files> FILES = new EnumMap<>(KnightKind.class);

    /** One knight's resource locations. */
    public record Files(ResourceLocation model, ResourceLocation animations, ResourceLocation texture,
                        ResourceLocation glow, ResourceLocation clear, ResourceLocation clearGlow)
    {
    }

    public static Files files(KnightKind kind)
    {
        return FILES.computeIfAbsent(kind, k -> new Files(
                new ResourceLocation(Dross.MODID, "geo/entity/knightmob/" + k.model + ".geo.json"),
                new ResourceLocation(Dross.MODID, "animations/entity/knightmob/" + k.model + ".animation.json"),
                new ResourceLocation(Dross.MODID, "textures/entity/knightmob/" + k.model + ".png"),
                new ResourceLocation(Dross.MODID, "textures/entity/knightmob/" + k.model + "_glow.png"),
                new ResourceLocation(Dross.MODID, "textures/entity/knightmob/" + k.model + "_ice.png"),
                new ResourceLocation(Dross.MODID, "textures/entity/knightmob/" + k.model + "_iceglow.png")));
    }

    @Override
    public ResourceLocation getModelResource(ElementalKnight knight)
    {
        return files(knight.kind()).model();
    }

    @Override
    public ResourceLocation getTextureResource(ElementalKnight knight)
    {
        return files(knight.kind()).texture();
    }

    @Override
    public ResourceLocation getAnimationResource(ElementalKnight knight)
    {
        return files(knight.kind()).animations();
    }
}
