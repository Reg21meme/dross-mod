package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallAreas;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.registry.ModPlacementModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code dross:not_on_rimefall_lake}: a feature isn't placed on a Rimefall patch's big frozen lake or
 * within {@code RimefallTuning.FEATURE_CLEARANCE} blocks of its edge (the lake is planned from the noise alone by the
 * boss-site planner, {@link RimefallAreas}, so this works before the lake's own chunks are made). Rimefall's trees, ice
 * spikes, boulders, ponds, plants and snow drifts use it, so nothing grows on the ice or hangs over it. Used like
 * vanilla's filters, with no settings:
 * <pre>{"type": "dross:not_on_rimefall_lake"}</pre>
 */
public class NotOnRimefallLakeFilter extends PlacementFilter
{
    public static final NotOnRimefallLakeFilter INSTANCE = new NotOnRimefallLakeFilter();
    public static final Codec<NotOnRimefallLakeFilter> CODEC = Codec.unit(() -> INSTANCE);

    private NotOnRimefallLakeFilter() {}

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos)
    {
        ServerLevel level = context.getLevel().getLevel();
        if (!level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return true;
        }
        return !RimefallAreas.nearLake(BossSites.forLevel(level), pos.getX(), pos.getZ());
    }

    @Override
    public PlacementModifierType<?> type()
    {
        return ModPlacementModifiers.NOT_ON_RIMEFALL_LAKE.get();
    }
}
