package com.reg21meme.dross.boss.fire.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.fire.FireAnims;
import com.reg21meme.dross.boss.fire.FireCandidate;
import com.reg21meme.dross.boss.fire.FireDesigns;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

import java.util.HashMap;
import java.util.Map;

/**
 * A fire candidate's files: a frozen statue's (made by {@code tools/fire/build.py}) are
 * {@code geo/entity/fire/<id>.geo.json} and {@code textures/entity/fire/<id>.png} with its {@code <id>_glow.png},
 * {@code <id>_clear.png} and {@code <id>_clearglow.png} (the ones its design has), sharing one empty animation file
 * (GeckoLib asks for one, and never reads it without an animation to play); a design that moves ({@code mob}, made by
 * {@code tools/fire/mobs.py}) has the same files under {@code firemob/}, its model on its mob's skeleton, and its mob's
 * animations, {@code animations/entity/firemob/<mob>.animation.json} (or its own, {@code <id>.animation.json}, for a mob
 * whose models each move a little differently: the Cinder Hermit hiding in its own shell). A design whose flames move
 * ({@link FireAnims#FIRE}: the Pallbearer's coffin, the brazier hermit's fire) has its texture and glow painted again
 * at each moment of the flames' loop, {@code <id>_fire<k>.png} and {@code <id>_fire<k>_glow.png}, shown in turn.
 */
public class FireCandidateModel extends GeoModel<FireCandidate>
{
    private static final ResourceLocation ANIMATIONS = new ResourceLocation(Dross.MODID,
            "animations/entity/fire/candidate.animation.json");
    private static final Map<String, Files> FILES = new HashMap<>();

    /** One design's resource locations. */
    public record Files(ResourceLocation model, ResourceLocation texture, ResourceLocation glow, ResourceLocation clear,
                        ResourceLocation clearGlow, ResourceLocation animations, ResourceLocation[] fire,
                        ResourceLocation[] fireGlow)
    {
    }

    public static Files files(FireCandidate candidate)
    {
        FireDesigns.Design design = candidate.getDesign();
        return FILES.computeIfAbsent(design.id(), id ->
        {
            String folder = design.animated() ? "firemob/" : "fire/";
            FireAnims.Mob mob = design.animated() ? FireAnims.get(design.mob()) : null;
            int frames = design.animated() ? FireAnims.FIRE.getOrDefault(id, 0) : 0;
            ResourceLocation[] fire = new ResourceLocation[frames];
            ResourceLocation[] fireGlow = new ResourceLocation[frames];
            for (int k = 0; k < frames; k++)
            {
                String name = k == 0 ? id : id + "_fire" + k;
                fire[k] = new ResourceLocation(Dross.MODID, "textures/entity/" + folder + name + ".png");
                fireGlow[k] = new ResourceLocation(Dross.MODID, "textures/entity/" + folder + name + "_glow.png");
            }
            return new Files(
                    new ResourceLocation(Dross.MODID, "geo/entity/" + folder + id + ".geo.json"),
                    new ResourceLocation(Dross.MODID, "textures/entity/" + folder + id + ".png"),
                    new ResourceLocation(Dross.MODID, "textures/entity/" + folder + id + "_glow.png"),
                    new ResourceLocation(Dross.MODID, "textures/entity/" + folder + id + "_clear.png"),
                    new ResourceLocation(Dross.MODID, "textures/entity/" + folder + id + "_clearglow.png"),
                    design.animated() ? new ResourceLocation(Dross.MODID, "animations/entity/firemob/"
                            + (mob != null && mob.perModel() ? id : design.mob()) + ".animation.json") : ANIMATIONS,
                    fire, fireGlow);
        });
    }

    /** Which frame of its flames' loop a candidate shows now (each one a little out of step with the next). */
    private static int fireFrame(FireCandidate candidate, int frames)
    {
        long ticks = candidate.level().getGameTime() + candidate.getId() * 5L;
        return (int) Math.floorMod(ticks / FireAnims.FIRE_FRAME_TICKS, (long) frames);
    }

    /** Its texture now (the frame of its flames' loop, if they move). */
    public static ResourceLocation texture(FireCandidate candidate)
    {
        Files files = files(candidate);
        return files.fire().length > 1 ? files.fire()[fireFrame(candidate, files.fire().length)] : files.texture();
    }

    /** Its glow now (the frame of its flames' loop, if they move). */
    public static ResourceLocation glow(FireCandidate candidate)
    {
        Files files = files(candidate);
        return files.fireGlow().length > 1 ? files.fireGlow()[fireFrame(candidate, files.fireGlow().length)]
                : files.glow();
    }

    @Override
    public ResourceLocation getModelResource(FireCandidate candidate)
    {
        return files(candidate).model();
    }

    @Override
    public ResourceLocation getTextureResource(FireCandidate candidate)
    {
        return texture(candidate);
    }

    @Override
    public ResourceLocation getAnimationResource(FireCandidate candidate)
    {
        return files(candidate).animations();
    }
}
