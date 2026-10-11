package com.reg21meme.dross.dimension.worldgen;

import com.reg21meme.dross.Dross;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;

/**
 * Keys for the Dross's tree shapes. The shapes themselves are data JSON in
 * {@code data/dross/worldgen/configured_feature/}, written by {@code tools/terrain/worldgen.py} (change a tree there,
 * then run the script again).
 */
public final class DrossTreeFeatures
{
    /** The common silverwood: a straight pale trunk about 9-13 blocks tall under a rounded blue crown. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> SILVERWOOD = key("silverwood");

    /** The tall silverwood: a straight trunk about 14-20 blocks tall with a narrow crown high up. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> TALL_SILVERWOOD = key("tall_silverwood");

    /** The great silverwood: a 2x2 trunk about 20-28 blocks tall, a few branches and a wide flat canopy. */
    public static final ResourceKey<ConfiguredFeature<?, ?>> GREAT_SILVERWOOD = key("great_silverwood");

    private DrossTreeFeatures() {}

    private static ResourceKey<ConfiguredFeature<?, ?>> key(String name)
    {
        return ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceLocation(Dross.MODID, name));
    }
}
