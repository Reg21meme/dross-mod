package com.reg21meme.dross.dimension;

import com.reg21meme.dross.Dross;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;

/**
 * Keys for the Dross dimension. The dimension itself is defined in data JSON
 * (data/dross/dimension/dross.json and data/dross/dimension_type/dross.json), written with all its terrain by
 * {@code tools/terrain/worldgen.py}. Owned by dimension-builder; other areas only read these keys.
 *
 * <p>This class is the single source of truth for the Dross dimension's name in Java (the six terrain biomes are in
 * {@link DrossBiomes}). Terrain and spawns live in data JSON under {@code data/dross/}, so they can be changed without
 * touching these keys.
 */
public final class ModDimensions
{
    /** The Dross dimension itself (dross:dross). Used by the portal to teleport players. */
    public static final ResourceKey<Level> DROSS_LEVEL =
            ResourceKey.create(Registries.DIMENSION, new ResourceLocation(Dross.MODID, "dross"));

    private ModDimensions() {}
}
