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
 * (data/dross/dimension/dross.json and data/dross/dimension_type/dross.json).
 * Owned by dimension-builder; other areas only read these keys.
 *
 * <p>This class is the single source of truth for Dross dimension names in Java.
 * Terrain and spawns live in data JSON under {@code data/dross/}, so they can be
 * changed later (for example swapping the superflat generator for real terrain)
 * without touching these keys.
 */
public final class ModDimensions
{
    /** The Dross dimension itself (dross:dross). Used by the portal to teleport players. */
    public static final ResourceKey<Level> DROSS_LEVEL =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Dross.MODID, "dross"));

    /** The Dross dimension type (dross:dross): permanent night, overworld-like rules. */
    public static final ResourceKey<DimensionType> DROSS_DIM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation(Dross.MODID, "dross"));

    /** The Dross dimension's own biome (dross:dross_flats), defined in data/dross/worldgen/biome/. */
    public static final ResourceKey<Biome> DROSS_FLATS_BIOME =
            ResourceKey.create(Registries.BIOME, new ResourceLocation(Dross.MODID, "dross_flats"));

    private ModDimensions() {}
}
