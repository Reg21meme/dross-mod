package com.reg21meme.dross.registry;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.worldgen.HubFalloff;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/** Density function types: new kinds of terrain maths the worldgen JSON can use (data/dross/worldgen/density_function/). */
public final class ModDensityFunctions
{
    public static final DeferredRegister<Codec<? extends DensityFunction>> DENSITY_FUNCTION_TYPES =
            DeferredRegister.create(Registries.DENSITY_FUNCTION_TYPE, Dross.MODID);

    // Dimension area: dross:hub_falloff, 1 near the hub at Dross 0, 0 and 0 far away (shapes the land round the hub)
    public static final RegistryObject<Codec<? extends DensityFunction>> HUB_FALLOFF =
            DENSITY_FUNCTION_TYPES.register("hub_falloff", HubFalloff.CODEC::codec);

    private ModDensityFunctions() {}
}
