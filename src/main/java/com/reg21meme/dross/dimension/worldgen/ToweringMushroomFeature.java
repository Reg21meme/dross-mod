package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.util.valueproviders.IntProvider;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.HugeBrownMushroomFeature;
import net.minecraft.world.level.levelgen.feature.configurations.HugeMushroomFeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;

/**
 * Feature {@code dross:towering_mushroom}: a towering giant blightcap of the Blightwood, a landmark over the ordinary
 * ones: a tall pale stem under a wide flat cap. It's Minecraft's huge brown mushroom (its flat cap, its rule that the
 * whole space must be open and the ground below dirt or mushroom ground), with a stem as tall as the config's
 * {@code height} instead of vanilla's 4 to 6 (now and then doubled).
 *
 * <p>Config (written by {@code tools/terrain/worldgen.py}, FEATURES):
 * <pre>{"cap_provider": state provider, "stem_provider": state provider, "foliage_radius": 4, "height": int provider}</pre>
 */
public class ToweringMushroomFeature extends HugeBrownMushroomFeature
{
    /** Vanilla's huge mushroom config plus the stem's height. */
    public static class Config extends HugeMushroomFeatureConfiguration
    {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockStateProvider.CODEC.fieldOf("cap_provider").forGetter(config -> config.capProvider),
                BlockStateProvider.CODEC.fieldOf("stem_provider").forGetter(config -> config.stemProvider),
                Codec.intRange(1, 8).fieldOf("foliage_radius").forGetter(config -> config.foliageRadius),
                IntProvider.codec(4, 32).fieldOf("height").forGetter(config -> config.height)
        ).apply(instance, Config::new));

        /** The registered codec, as the vanilla config type the parent feature works with (only these configs pass through it). */
        public static final Codec<HugeMushroomFeatureConfiguration> AS_VANILLA = CODEC.<HugeMushroomFeatureConfiguration>xmap(config -> config,
                config -> (Config) config);

        public final IntProvider height;

        public Config(BlockStateProvider capProvider, BlockStateProvider stemProvider, int foliageRadius, IntProvider height)
        {
            super(capProvider, stemProvider, foliageRadius);
            this.height = height;
        }
    }

    public ToweringMushroomFeature(Codec<HugeMushroomFeatureConfiguration> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<HugeMushroomFeatureConfiguration> context)
    {
        WorldGenLevel level = context.level();
        BlockPos origin = context.origin();
        RandomSource random = context.random();
        HugeMushroomFeatureConfiguration config = context.config();
        int height = config instanceof Config towering ? towering.height.sample(random) : getTreeHeight(random);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        if (!isValidPosition(level, origin, height, pos, config))
        {
            return false;
        }
        makeCap(level, random, origin, height, pos, config);
        placeTrunk(level, random, origin, config, height, pos);
        return true;
    }
}
