package com.reg21meme.dross.boss.frostfolk.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.frostfolk.FolkAnims;
import com.reg21meme.dross.boss.frostfolk.FolkRole;
import com.reg21meme.dross.boss.frostfolk.Frostfolk;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.EnumMap;
import java.util.Map;

/**
 * The Frostfolk's one model (made by {@code tools/rimefall/mobs.py frostfolk}): {@code geo/entity/frostfolk/},
 * {@code animations/entity/frostfolk/} and a texture for each calling, {@code textures/entity/frostfolk/<calling>.png}
 * (its hood's blue, its clothes; the dark under its hood) and its glow, {@code <calling>_glow.png} (its blue eyes).
 * Every calling wears it; the renderer hides the shapes that aren't theirs.
 */
public class FolkModel extends GeoModel<Frostfolk>
{
    private static final String M = FolkAnims.MOB;
    private static final ResourceLocation GEO = new ResourceLocation(Dross.MODID, "geo/entity/" + M + "/" + M + ".geo.json");
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(Dross.MODID,
            "animations/entity/" + M + "/" + M + ".animation.json");
    private static final Map<FolkRole, ResourceLocation> TEXTURES = new EnumMap<>(FolkRole.class);
    private static final Map<FolkRole, ResourceLocation> GLOWS = new EnumMap<>(FolkRole.class);

    static
    {
        for (FolkRole role : FolkRole.values())
        {
            TEXTURES.put(role, new ResourceLocation(Dross.MODID, "textures/entity/" + M + "/" + role.key + ".png"));
            GLOWS.put(role, new ResourceLocation(Dross.MODID, "textures/entity/" + M + "/" + role.key + "_glow.png"));
        }
    }

    @Override
    public ResourceLocation getModelResource(Frostfolk folk)
    {
        return GEO;
    }

    @Override
    public ResourceLocation getTextureResource(Frostfolk folk)
    {
        return TEXTURES.get(folk.role());
    }

    /** Its calling's glow: its eyes. */
    public static ResourceLocation glow(FolkRole role)
    {
        return GLOWS.get(role);
    }

    @Override
    public ResourceLocation getAnimationResource(Frostfolk folk)
    {
        return ANIMATIONS;
    }
}
