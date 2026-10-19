package com.reg21meme.dross.boss.rimefall.client;

import com.reg21meme.dross.Dross;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import software.bernie.geckolib.core.animatable.GeoAnimatable;
import software.bernie.geckolib.core.animatable.model.CoreGeoBone;
import software.bernie.geckolib.core.animation.AnimationState;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * One of Rimefall's animals' files (made by {@code tools/rimefall/mobs.py}): {@code geo/entity/<mob>/<mob>.geo.json},
 * {@code animations/entity/<mob>/<mob>.animation.json} and {@code textures/entity/<mob>/<texture>.png} (and its
 * {@code _ice} and {@code _iceglow} layers when it has see-through ice). After the animations it hides the bones this
 * animal doesn't show (a male Rimehorn's snow, a cow moose's antlers) and gives a calf its bigger head.
 */
public class RimefallMobModel<T extends LivingEntity & GeoAnimatable> extends GeoModel<T>
{
    /**
     * @param mob       its files' folder and name ({@code rimehorn})
     * @param texture   which texture it wears ({@code male}, {@code female}...)
     * @param toggled   every bone that some of them hide
     * @param hidden    the bones this one hides
     * @param babyHead  how much bigger a calf's head is drawn (its whole body is scaled down by the renderer)
     */
    public RimefallMobModel(String mob, Function<T, String> texture, List<String> toggled, Function<T, Set<String>> hidden,
                            float babyHead)
    {
        this.mob = mob;
        this.texture = texture;
        this.toggled = toggled;
        this.hidden = hidden;
        this.babyHead = babyHead;
        this.model = new ResourceLocation(Dross.MODID, "geo/entity/" + mob + "/" + mob + ".geo.json");
        this.animations = new ResourceLocation(Dross.MODID, "animations/entity/" + mob + "/" + mob + ".animation.json");
    }

    private final String mob;
    private final Function<T, String> texture;
    private final List<String> toggled;
    private final Function<T, Set<String>> hidden;
    private final float babyHead;
    private final ResourceLocation model;
    private final ResourceLocation animations;
    private final Map<String, ResourceLocation> textures = new HashMap<>();

    /** A texture layer: "" (the solid parts), "_ice" or "_iceglow". */
    public ResourceLocation texture(T animal, String layer)
    {
        return this.textures.computeIfAbsent(this.texture.apply(animal) + layer, name ->
                new ResourceLocation(Dross.MODID, "textures/entity/" + this.mob + "/" + name + ".png"));
    }

    @Override
    public ResourceLocation getModelResource(T animal)
    {
        return this.model;
    }

    @Override
    public ResourceLocation getTextureResource(T animal)
    {
        return this.texture(animal, "");
    }

    @Override
    public ResourceLocation getAnimationResource(T animal)
    {
        return this.animations;
    }

    /**
     * After the animations: hides the bones this one doesn't show and scales a calf's head up. Set on every animal
     * every frame (GeckoLib shares the bones of one model between every animal that wears it), and the head's state
     * marks are cleared so GeckoLib poses it afresh next frame.
     */
    @Override
    public void setCustomAnimations(T animal, long instanceId, AnimationState<T> state)
    {
        Set<String> hide = this.hidden.apply(animal);
        for (String name : this.toggled)
        {
            CoreGeoBone bone = this.getAnimationProcessor().getBone(name);
            if (bone != null)
            {
                bone.setHidden(hide.contains(name));
            }
        }
        if (animal.isBaby() && this.babyHead != 1.0F)
        {
            CoreGeoBone head = this.getAnimationProcessor().getBone("head");
            if (head != null)
            {
                head.setScaleX(head.getScaleX() * this.babyHead);
                head.setScaleY(head.getScaleY() * this.babyHead);
                head.setScaleZ(head.getScaleZ() * this.babyHead);
                head.resetStateChanges();
            }
        }
    }
}
