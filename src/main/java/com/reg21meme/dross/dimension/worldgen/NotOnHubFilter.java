package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.portal.DrossHubGround;
import com.reg21meme.dross.registry.ModPlacementModifiers;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.levelgen.placement.PlacementContext;
import net.minecraft.world.level.levelgen.placement.PlacementFilter;
import net.minecraft.world.level.levelgen.placement.PlacementModifierType;

/**
 * Placement filter {@code dross:not_on_hub}: a feature isn't placed on the hub at Dross 0,0 or within {@code margin} blocks
 * of its footprint (a box, along X and Z: {@link DrossHub#isOnHub}), so the forest still comes right up to it.
 *
 * <p>The hub (the Shattered Spire) is made with the land, in the "surface structures" step ({@code portal.DrossHubPiece}),
 * and the trees, plants and ores come after it: without this they'd grow through its walls and over its staircase ring.
 * With {@code depth}, it only keeps the feature off from that many blocks under the hub's ground level up (the ores:
 * none in the spire's blackstone, the ground under it keeps its own):
 * <pre>{"type": "dross:not_on_hub", "margin": 24}
 * {"type": "dross:not_on_hub", "margin": 8, "depth": 8}</pre>
 * The numbers are labeled constants in {@code tools/terrain/worldgen.py} (FEATURES). (Ponds, boulders and fallen logs,
 * which come before the hub, keep much further away: {@link NotNearHubFilter}.)
 */
public class NotOnHubFilter extends PlacementFilter
{
    public static final Codec<NotOnHubFilter> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ExtraCodecs.NON_NEGATIVE_INT.fieldOf("margin").forGetter(filter -> filter.margin),
            ExtraCodecs.NON_NEGATIVE_INT.optionalFieldOf("depth").forGetter(filter -> filter.depth)
    ).apply(instance, NotOnHubFilter::new));

    private final int margin;
    private final Optional<Integer> depth;

    public NotOnHubFilter(int margin, Optional<Integer> depth)
    {
        this.margin = margin;
        this.depth = depth;
    }

    @Override
    protected boolean shouldPlace(PlacementContext context, RandomSource random, BlockPos pos)
    {
        if (!DrossHub.isOnHub(pos.getX(), pos.getZ(), this.margin))
        {
            return true;
        }
        if (this.depth.isEmpty())
        {
            return false;
        }
        int ground = DrossHubGround.groundY(context.generator(), context.getLevel().getLevel().getChunkSource().randomState(),
                context.getLevel());
        return pos.getY() < ground - this.depth.get();
    }

    @Override
    public PlacementModifierType<?> type()
    {
        return ModPlacementModifiers.NOT_ON_HUB.get();
    }
}
