package com.reg21meme.dross.boss.huntsman.folk.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.huntsman.folk.FolkAnims;
import com.reg21meme.dross.boss.huntsman.folk.HunterFolk;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * The hunter villagers' one model (made by {@code tools/rimefall/mobs.py hunter_folk}): {@code geo/entity/hunter_folk/},
 * {@code animations/entity/hunter_folk/} and {@code textures/entity/hunter_folk/hunter_folk.png} with its {@code _glow},
 * {@code _ice} and {@code _iceglow} layers. Every calling wears it; the renderer hides the gear that isn't theirs.
 */
public class FolkModel extends GeoModel<HunterFolk>
{
    private static final String M = FolkAnims.MOB;
    private static final ResourceLocation GEO = new ResourceLocation(Dross.MODID, "geo/entity/" + M + "/" + M + ".geo.json");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(Dross.MODID,
            "animations/entity/" + M + "/" + M + ".animation.json");

    /** A texture layer: "" (the solid parts), "_glow", "_ice" or "_iceglow". */
    public static ResourceLocation texture(String layer)
    {
        return new ResourceLocation(Dross.MODID, "textures/entity/" + M + "/" + M + layer + ".png");
    }

    private static final ResourceLocation TEXTURE = texture("");

    @Override
    public ResourceLocation getModelResource(HunterFolk folk)
    {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(HunterFolk folk)
    {
        return TEXTURE;
    }

    @Override
    public ResourceLocation getAnimationResource(HunterFolk folk)
    {
        return ANIMATIONS;
    }
}
