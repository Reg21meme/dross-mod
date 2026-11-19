package com.reg21meme.dross.boss.giant.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.giant.GiantMoments;
import com.reg21meme.dross.boss.giant.SilverwoodGiant;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * The Silverwood Giant's files, by its look (made by {@code tools/forest_giant/}): {@code geo/entity/forest_giant/<look>.geo.json},
 * {@code animations/entity/forest_giant/<look>.animation.json}, {@code textures/entity/forest_giant/<look>.png} and its
 * glow, {@code <look>_glow.png}. After the animations it hides the bones a look shows only in its throw (G2's and G3's
 * rock: {@link GiantMoments.Look#hiddenBones}; the animations already scale it to nothing, this keeps it from showing in
 * the blend between two of them).
 */
public class GiantModel extends GeoModel<SilverwoodGiant>
{
    private final Map<String, ResourceLocation> files = new HashMap<>();

    private ResourceLocation file(String path)
    {
        return this.files.computeIfAbsent(path, p -> new ResourceLocation(Dross.MODID, p));
    }

    @Override
    public ResourceLocation getModelResource(SilverwoodGiant giant)
    {
        return this.file("geo/entity/forest_giant/" + giant.look().id() + ".geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SilverwoodGiant giant)
    {
        return this.file("textures/entity/forest_giant/" + giant.look().id() + ".png");
    }

    /** What glows: its eyes, its people's sign, its spear's edge... drawn full-bright. */
    public ResourceLocation glow(SilverwoodGiant giant)
    {
        return this.file("textures/entity/forest_giant/" + giant.look().id() + "_glow.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SilverwoodGiant giant)
    {
        return this.file("animations/entity/forest_giant/" + giant.look().id() + ".animation.json");
    }

    /** Set on every giant every frame (GeckoLib shares a model's bones between everything that wears it). */
    @Override
    public void setCustomAnimations(SilverwoodGiant giant, long instanceId, AnimationState<SilverwoodGiant> state)
    {
        boolean throwing = "throw".equals(giant.action());
        for (String name : giant.look().hiddenBones())
        {
            CoreGeoBone bone = this.getAnimationProcessor().getBone(name);
            if (bone != null)
            {
                bone.setHidden(!throwing);
            }
        }
    }
}
