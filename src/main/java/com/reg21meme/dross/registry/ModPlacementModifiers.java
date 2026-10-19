package com.reg21meme.dross.registry;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.worldgen.NotNearHubFilter;
import com.reg21meme.dross.dimension.worldgen.NotNearSewerEntranceFilter;
import com.reg21meme.dross.dimension.worldgen.NotOnRimefallLakeFilter;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Placement modifiers: new rules for where world generation places a feature (used by the placed-feature JSON in
 * data/dross/worldgen/placed_feature/).
 */
public final class ModPlacementModifiers
{
    public static final DeferredRegister<PlacementModifierType<?>> PLACEMENT_MODIFIERS =
            DeferredRegister.create(Registries.PLACEMENT_MODIFIER_TYPE, Dross.MODID);

    // Dimension area: dross:not_near_hub, keeps ponds, boulders and fallen logs away from the hub at Dross 0, 0
    public static final RegistryObject<PlacementModifierType<NotNearHubFilter>> NOT_NEAR_HUB =
            PLACEMENT_MODIFIERS.register("not_near_hub", () -> () -> NotNearHubFilter.CODEC);

    // Dimension area: dross:not_near_sewer_entrance, keeps Blightwood's trees, mushrooms, logs, ponds and plants off the
    // sewers' entrances and the Rat King's poisoned ground
    public static final RegistryObject<PlacementModifierType<NotNearSewerEntranceFilter>> NOT_NEAR_SEWER_ENTRANCE =
            PLACEMENT_MODIFIERS.register("not_near_sewer_entrance", () -> () -> NotNearSewerEntranceFilter.CODEC);

    // Dimension area: dross:not_on_rimefall_lake, keeps Rimefall's trees, spikes, boulders, ponds, plants and drifts off its
    // big frozen lakes
    public static final RegistryObject<PlacementModifierType<NotOnRimefallLakeFilter>> NOT_ON_RIMEFALL_LAKE =
            PLACEMENT_MODIFIERS.register("not_on_rimefall_lake", () -> () -> NotOnRimefallLakeFilter.CODEC);

    private ModPlacementModifiers() {}
}
