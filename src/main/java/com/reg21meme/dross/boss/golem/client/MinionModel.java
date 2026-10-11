package com.reg21meme.dross.boss.golem.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.GolemMinion;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * A Cinder Colossus minion's files (made by {@code tools/golems/build.py} from {@code designs/minions.py}):
 * {@code geo/entity/golem_minion/<id>.geo.json}, {@code animations/entity/golem_minion/<id>.animation.json} and
 * {@code textures/entity/golem_minion/<id>.png} with its {@code <id>_glow.png}.
 */
public class MinionModel extends GeoModel<GolemMinion>
{
    private static final Map<String, Files> FILES = new HashMap<>();

    /** One minion's resource locations. */
    public record Files(ResourceLocation model, ResourceLocation animations, ResourceLocation texture, ResourceLocation glow)
    {
    }

    public static Files files(GolemMinion minion)
    {
        return FILES.computeIfAbsent(minion.design().id(), id -> new Files(
                new ResourceLocation(Dross.MODID, "geo/entity/golem_minion/" + id + ".geo.json"),
                new ResourceLocation(Dross.MODID, "animations/entity/golem_minion/" + id + ".animation.json"),
                new ResourceLocation(Dross.MODID, "textures/entity/golem_minion/" + id + ".png"),
                new ResourceLocation(Dross.MODID, "textures/entity/golem_minion/" + id + "_glow.png")));
    }

    @Override
    public ResourceLocation getModelResource(GolemMinion minion)
    {
        return files(minion).model();
    }

    @Override
    public ResourceLocation getTextureResource(GolemMinion minion)
    {
        return files(minion).texture();
    }

    @Override
    public ResourceLocation getAnimationResource(GolemMinion minion)
    {
        return files(minion).animations();
    }
}
