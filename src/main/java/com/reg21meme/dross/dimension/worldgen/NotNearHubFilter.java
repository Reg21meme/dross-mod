package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.registry.ModPlacementModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code dross:not_near_hub}: a feature is only placed more than {@code radius} blocks (measured along
 * the ground, X and Z) from the hub's centre, {@link DrossHub#CENTER} (Dross 0, 0).
 *
 * <p>The hub (the Shattered Spire) is built on fitted ground when a world starts: it removes the trees and giant
 * mushrooms touching it, whole, and levels the ground under it. Ponds, boulders and fallen logs right next to it would
 * look odd, could block its walls or the survivors' walks, and would count as "ground" when the hub is fitted, so the
 * worldgen JSON keeps them away with this filter. Used like vanilla's filters:
 * <pre>{"type": "dross:not_near_hub", "radius": 112}</pre>
 * The numbers are labeled constants in {@code tools/terrain/worldgen.py} (FEATURES).
 */
public class NotNearHubFilter extends PlacementFilter
{
    public static final Codec<NotNearHubFilter> CODEC = ExtraCodecs.NON_NEGATIVE_INT.fieldOf("radius")
            .xmap(NotNearHubFilter::new, filter -> filter.radius).codec();

    private final int radius;

    public NotNearHubFilter(int radius)
    {
        this.radius = radius;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos)
    {
        long dx = pos.getX() - DrossHub.CENTER.getX();
        long dz = pos.getZ() - DrossHub.CENTER.getZ();
        return dx * dx + dz * dz > (long) radius * radius;
    }

    @Override
    public PlacementModifierType<?> type()
    {
        return ModPlacementModifiers.NOT_NEAR_HUB.get();
    }
}
