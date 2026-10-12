package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.worldgen.RockFacesFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Features: things world generation places (used by the worldgen JSON in data/dross/worldgen/configured_feature/). */
public final class ModFeatures
{
    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Dross.MODID);

    // Dimension area: dross:rock_faces, bare rock on every steep face of the Dross's land
    public static final RegistryObject<RockFacesFeature> ROCK_FACES = FEATURES.register("rock_faces",
            () -> new RockFacesFeature(RockFacesFeature.Config.CODEC));

    private ModFeatures() {}
}
