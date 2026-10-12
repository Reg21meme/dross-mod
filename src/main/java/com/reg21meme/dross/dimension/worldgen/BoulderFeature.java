package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.FloatProvider;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Feature {@code dross:boulder}: a rounded boulder half sunk into the ground, made of a few overlapping lumps (like
 * vanilla's mossy-cobblestone forest rocks, but bigger if asked and of any stone): the mossy Duskstone boulders of the
 * Silverwood Forest, the packed-ice boulders of Rimefall, the Cragstone boulders of the Gravecrags.
 *
 * <p>Its origin is the air block just above the ground (a {@code heightmap} placement). Each lump is a ball of
 * {@code lump_radius} blocks; the next lump starts a block or so away, sideways or lower, so the boulder spreads along the
 * ground instead of stacking up. A lump only fills air, loose things (plants, snow layers) and the ground cover and soil
 * in the tag {@code replaceable}: never logs, water or anything built. It's built from the bottom up, and a block only
 * goes where something solid (the ground, or the boulder itself) is right under it, so on a slope the boulder hugs the
 * ground instead of hanging out over the drop. Blocks of the boulder with open air above them become {@code top} with
 * the chance {@code top_chance} (moss on the Duskstone, snow on the ice).
 *
 * <p>Config (written by {@code tools/terrain/worldgen.py}, FEATURES):
 * <pre>{"stone": state provider, "top": state provider, "top_chance": 0.7, "lumps": int provider,
 *  "lump_radius": float provider, "replaceable": "#dross:rock_face_replaceable"}</pre>
 */
public class BoulderFeature extends Feature<BoulderFeature.Config>
{
    /**
     * @param stone       what the boulder is made of
     * @param top         what its top blocks become (with {@code topChance})
     * @param topChance   chance (0 to 1) a top block (one with air above it) becomes {@code top}
     * @param lumps       how many lumps (balls) make up the boulder
     * @param lumpRadius  each lump's radius (blocks)
     * @param replaceable the ground the boulder may sink into (besides air and loose plants)
     */
    public record Config(BlockStateProvider stone, BlockStateProvider top, float topChance, IntProvider lumps, FloatProvider lumpRadius,
                         TagKey<Block> replaceable) implements FeatureConfiguration
    {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockStateProvider.CODEC.fieldOf("stone").forGetter(Config::stone),
                BlockStateProvider.CODEC.fieldOf("top").forGetter(Config::top),
                Codec.floatRange(0.0F, 1.0F).fieldOf("top_chance").forGetter(Config::topChance),
                IntProvider.codec(1, 8).fieldOf("lumps").forGetter(Config::lumps),
                FloatProvider.codec(0.5F, 4.0F).fieldOf("lump_radius").forGetter(Config::lumpRadius),
                TagKey.hashedCodec(Registries.BLOCK).fieldOf("replaceable").forGetter(Config::replaceable)
        ).apply(instance, Config::new));
    }

    public BoulderFeature(Codec<Config> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context)
    {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        BlockPos origin = context.origin();
        if (origin.getY() <= level.getMinBuildHeight() + 4 || !level.getBlockState(origin.below()).isSolid())
        {
            return false;
        }
        // The lumps' blocks (slightly squashed balls: a boulder is wider than it is tall)...
        Set<BlockPos> cells = new HashSet<>();
        BlockPos.MutableBlockPos centre = origin.mutable();
        int lumps = config.lumps().sample(random);
        for (int lump = 0; lump < lumps; lump++)
        {
            float radius = config.lumpRadius().sample(random);
            int reach = (int) Math.ceil(radius);
            float squash = 1.25F;
            for (BlockPos pos : BlockPos.betweenClosed(centre.offset(-reach, -reach, -reach), centre.offset(reach, reach, reach)))
            {
                float dx = pos.getX() - centre.getX();
                float dy = (pos.getY() - centre.getY()) * squash;
                float dz = pos.getZ() - centre.getZ();
                if (dx * dx + dy * dy + dz * dz <= radius * radius)
                {
                    cells.add(pos.immutable());
                }
            }
            // The next lump: a block or so to the side, the same height or one lower (it hugs the ground).
            centre.move(random.nextInt(3) - 1, -random.nextInt(2), random.nextInt(3) - 1);
        }
        // ...built from the bottom up, each only where something solid (the ground or the boulder) is right under it.
        List<BlockPos> order = new ArrayList<>(cells);
        order.sort(Comparator.comparingInt(BlockPos::getY));
        Set<BlockPos> placed = new HashSet<>();
        for (BlockPos at : order)
        {
            BlockPos below = at.below();
            if (canReplace(level.getBlockState(at), config) && (placed.contains(below) || level.getBlockState(below).isSolid()))
            {
                level.setBlock(at, config.stone().getState(random, at), Block.UPDATE_CLIENTS);
                placed.add(at);
            }
        }
        if (placed.isEmpty())
        {
            return false;
        }
        if (config.topChance() > 0.0F)
        {
            for (BlockPos pos : placed)
            {
                BlockPos above = pos.above();
                if (!placed.contains(above) && level.getBlockState(above).isAir() && random.nextFloat() < config.topChance())
                {
                    level.setBlock(pos, config.top().getState(random, pos), Block.UPDATE_CLIENTS);
                }
            }
        }
        return true;
    }

    /** Air, loose plants and snow layers, and the ground cover and soil in the config's tag (never fluids, logs or leaves). */
    private static boolean canReplace(BlockState state, Config config)
    {
        if (!state.getFluidState().isEmpty())
        {
            return false;
        }
        return state.isAir() || state.canBeReplaced() || state.is(config.replaceable());
    }
}
