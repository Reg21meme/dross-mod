package com.reg21meme.dross.dimension;

import com.reg21meme.dross.Dross;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Keys for the Dross dimension. The dimension itself is defined in data JSON
 * (data/dross/dimension/dross.json and data/dross/dimension_type/dross.json), written with all its terrain by
 * {@code tools/terrain/worldgen.py}. Owned by dimension-builder; other areas only read these keys.
 *
 * <p>This class is the single source of truth for Dross dimension names in Java (the six terrain biomes are in
 * {@link DrossBiomes}). Terrain and spawns live in data JSON under {@code data/dross/}, so they can be changed without
 * touching these keys.
 */
public final class ModDimensions
{
    /** The Dross dimension itself (dross:dross). Used by the portal to teleport players. */
    public static final ResourceKey<Level> DROSS_LEVEL =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Dross.MODID, "dross"));

    /** The Dross dimension type (dross:dross): permanent night, overworld-like rules. */
    public static final ResourceKey<DimensionType> DROSS_DIM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation(Dross.MODID, "dross"));

    /**
     * The old superflat Dross's only biome (dross:dross_flats), defined in data/dross/worldgen/biome/. New land never uses
     * it (see {@link DrossBiomes}); it stays registered because old worlds' chunks name it, and every new biome copies its
     * mob spawns.
     */
    public static final ResourceKey<Biome> DROSS_FLATS_BIOME =
            ResourceKey.create(Registries.BIOME, new ResourceLocation(Dross.MODID, "dross_flats"));

    private ModDimensions() {}
}
