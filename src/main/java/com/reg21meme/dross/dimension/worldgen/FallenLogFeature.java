package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Feature {@code dross:fallen_log}: an old trunk lying on the forest floor, a straight row of logs along X or Z, now and
 * then with something growing on it (moss carpet in the Silverwood Forest; moss, Sporepods and Gloomcaps on the rotting
 * logs of the Blightwood).
 *
 * <p>Its origin is the air block just above the ground (a {@code heightmap} placement). It lies along X or Z (if the
 * first way doesn't fit, the other is tried), only where the whole row is free (air or loose plants, never water), and
 * resting on the ground: both ends and at least {@value #MIN_RESTING} of its logs have firm ground right under them,
 * and nowhere is there more than one block of air under it (it may lie across a small dip, never over a drop or into a
 * slope); otherwise nothing is placed. The things on top are only placed where they could grow.
 *
 * <p>Config (written by {@code tools/terrain/worldgen.py}, FEATURES):
 * <pre>{"log": state provider (its axis is set here), "length": int provider, "top": state provider, "top_chance": 0.3}</pre>
 */
public class FallenLogFeature extends Feature<FallenLogFeature.Config>
{
    /**
     * @param log       the log (a block with an {@code axis} property: it's turned to lie along the row)
     * @param length    how long the row is (blocks)
     * @param top       what may grow on a log of it (air for nothing)
     * @param topChance chance (0 to 1) each log gets {@code top} on it
     */
    public record Config(BlockStateProvider log, IntProvider length, BlockStateProvider top, float topChance) implements FeatureConfiguration
    {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockStateProvider.CODEC.fieldOf("log").forGetter(Config::log),
                IntProvider.codec(2, 16).fieldOf("length").forGetter(Config::length),
                BlockStateProvider.CODEC.fieldOf("top").forGetter(Config::top),
                Codec.floatRange(0.0F, 1.0F).fieldOf("top_chance").forGetter(Config::topChance)
        ).apply(instance, Config::new));
    }

    public FallenLogFeature(Codec<Config> codec)
    {
        super(codec);
    }

    /** At least this share of a fallen log's blocks rest right on the ground (the rest may lie across a dip of one block). */
    private static final double MIN_RESTING = 0.6;

    @Override
    public boolean place(FeaturePlaceContext<Config> context)
    {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        int length = config.length().sample(random);
        Direction first = random.nextBoolean() ? Direction.EAST : Direction.SOUTH;
        Direction second = first == Direction.EAST ? Direction.SOUTH : Direction.EAST;
        List<BlockPos> row = fits(level, context.origin(), first, length);
        Direction along = first;
        if (row == null)
        {
            row = fits(level, context.origin(), second, length);
            along = second;
        }
        if (row == null)
        {
            return false;
        }
        for (BlockPos pos : row)
        {
            BlockState log = config.log().getState(random, pos);
            if (log.hasProperty(RotatedPillarBlock.AXIS))
            {
                log = log.setValue(RotatedPillarBlock.AXIS, along.getAxis());
            }
            level.setBlock(pos, log, Block.UPDATE_CLIENTS);
        }
        if (config.topChance() > 0.0F)
        {
            for (BlockPos pos : row)
            {
                BlockPos above = pos.above();
                if (random.nextFloat() < config.topChance() && level.getBlockState(above).isAir())
                {
                    BlockState top = config.top().getState(random, above);
                    if (!top.isAir() && top.canSurvive(level, above))
                    {
                        level.setBlock(above, top, Block.UPDATE_CLIENTS);
                    }
                }
            }
        }
        return true;
    }

    /** The row of a log of this length lying this way round the origin, or null if it can't lie there (see the class notes). */
    private static List<BlockPos> fits(WorldGenLevel level, BlockPos origin, Direction along, int length)
    {
        BlockPos start = origin.relative(along.getOpposite(), length / 2);
        List<BlockPos> row = new ArrayList<>();
        int resting = 0;
        for (int i = 0; i < length; i++)
        {
            BlockPos pos = start.relative(along, i);
            BlockState here = level.getBlockState(pos);
            if (!(here.isAir() || here.canBeReplaced()) || !here.getFluidState().isEmpty() || here.hasBlockEntity())
            {
                return null;
            }
            BlockPos below = pos.below();
            if (firm(level, below))
            {
                resting++;
            }
            else if (i == 0 || i == length - 1 || !level.getBlockState(below).isAir() || !firm(level, below.below()))
            {
                return null; // an end in the air, or more than one block of air (or something loose) under it
            }
            row.add(pos);
        }
        return resting >= MIN_RESTING * length ? row : null;
    }

    /** Firm ground to lie on: a sturdy top, and not leaves (a crown reaching over from the next chunk). */
    private static boolean firm(WorldGenLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.isFaceSturdy(level, pos, Direction.UP) && !state.is(BlockTags.LEAVES);
    }
}
