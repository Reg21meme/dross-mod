package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.SewerSites;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.registry.ModPlacementModifiers;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code dross:not_near_sewer_entrance}: a feature isn't placed near a planned sewer entrance (within
 * {@code SewerTuning.PIPE_CLEARANCE} of a regular pipe's middle, {@code SewerTuning.GRAND_CLEARANCE} of the Rat King's
 * grand entrance, whose poisoned ground has its own dead plants). Blightwood's trees, giant mushrooms, fallen logs, ponds
 * and plants use it, so nothing grows over an opening, its steps or the poisoned ground. The entrances come from the
 * boss-site planner ({@link BossSites}: noise only), so it works before the sewer's own chunks are made. Used like
 * vanilla's filters, with no settings:
 * <pre>{"type": "dross:not_near_sewer_entrance"}</pre>
 */
public class NotNearSewerEntranceFilter extends PlacementFilter
{
    public static final NotNearSewerEntranceFilter INSTANCE = new NotNearSewerEntranceFilter();
    public static final Codec<NotNearSewerEntranceFilter> CODEC = Codec.unit(() -> INSTANCE);

    private NotNearSewerEntranceFilter() {}

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos)
    {
        ServerLevel level = context.getLevel().getLevel();
        if (!level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return true;
        }
        return !SewerSites.nearEntrance(BossSites.forLevel(level), pos.getX(), pos.getZ());
    }

    @Override
    public PlacementModifierType<?> type()
    {
        return ModPlacementModifiers.NOT_NEAR_SEWER_ENTRANCE.get();
    }
}
