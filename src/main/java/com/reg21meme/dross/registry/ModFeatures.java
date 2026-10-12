package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.worldgen.BoulderFeature;
import com.reg21meme.dross.dimension.worldgen.FallenLogFeature;
import com.reg21meme.dross.dimension.worldgen.PondFeature;
import com.reg21meme.dross.dimension.worldgen.RockFacesFeature;
import com.reg21meme.dross.dimension.worldgen.TidyUpFeature;
import com.reg21meme.dross.dimension.worldgen.ToweringMushroomFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
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

    // Dimension area: dross:boulder, a rounded boulder half sunk into the ground (mossy Duskstone, packed ice, Cragstone)
    public static final RegistryObject<BoulderFeature> BOULDER = FEATURES.register("boulder",
            () -> new BoulderFeature(BoulderFeature.Config.CODEC));

    // Dimension area: dross:fallen_log, an old trunk lying on the forest floor, sometimes with moss or mushrooms on it
    public static final RegistryObject<FallenLogFeature> FALLEN_LOG = FEATURES.register("fallen_log",
            () -> new FallenLogFeature(FallenLogFeature.Config.CODEC));

    // Dimension area: dross:pond, a small pond in level ground that never cuts into trees (dark, murky and frozen ponds)
    public static final RegistryObject<PondFeature> POND = FEATURES.register("pond",
            () -> new PondFeature(PondFeature.Config.CODEC));

    // Dimension area: dross:towering_mushroom, a towering giant blightcap (a tall stem under a wide flat cap) in the Blightwood
    public static final RegistryObject<ToweringMushroomFeature> TOWERING_MUSHROOM = FEATURES.register("towering_mushroom",
            () -> new ToweringMushroomFeature(ToweringMushroomFeature.Config.AS_VANILLA));

    // Dimension area: dross:tidy_up, the last feature of every Dross chunk: removes loose plants and vines, settles stray leaves
    public static final RegistryObject<TidyUpFeature> TIDY_UP = FEATURES.register("tidy_up",
            () -> new TidyUpFeature(NoneFeatureConfiguration.CODEC));

    private ModFeatures() {}
}
