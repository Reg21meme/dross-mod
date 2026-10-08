package com.reg21meme.dross.dimension;

import com.reg21meme.dross.Dross;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;

/**
 * Keys for the Dross dimension. The dimension itself is defined in data JSON
 * (data/dross/dimension/dross.json and data/dross/dimension_type/dross.json).
 * Owned by dimension-builder; other areas only read these keys.
 */
public final class ModDimensions
{
    public static final ResourceKey<Level> DROSS_LEVEL =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Dross.MODID, "dross"));

    public static final ResourceKey<DimensionType> DROSS_DIM_TYPE =
            ResourceKey.create(Registries.DIMENSION_TYPE, new ResourceLocation(Dross.MODID, "dross"));

    private ModDimensions() {}
}
