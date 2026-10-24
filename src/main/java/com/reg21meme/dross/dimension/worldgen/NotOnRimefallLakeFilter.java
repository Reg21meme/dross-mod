package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallAreas;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
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
    /** A feature keeps this far off a hunter village's courtyards, lanes, igloos, pens and decorations too (a tree's
     * crown, a boulder's spread). */
    private static final int VILLAGE_MARGIN = 6;
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
        BossSites sites = BossSites.forLevel(level);
        // nor on a hunter village's parts or by them (built before the trees); the land between them keeps its own trees
        // and hills (the user, October 23, 2026: "if there are hills or trees that don't interfere with the spawning of
        // the village then that's fine")
        return !RimefallAreas.nearLake(sites, pos.getX(), pos.getZ())
                && RimefallVillages.villageAt(sites, pos.getX(), pos.getZ(), VILLAGE_MARGIN)
                        .filter(v -> v.keepsClear(pos.getX(), pos.getZ(), VILLAGE_MARGIN)).isEmpty();
    }

    @Override
    public PlacementModifierType<?> type()
    {
        return ModPlacementModifiers.NOT_ON_RIMEFALL_LAKE.get();
    }
}
