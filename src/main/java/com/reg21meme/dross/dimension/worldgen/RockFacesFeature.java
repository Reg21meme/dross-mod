package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;

/**
 * Feature {@code dross:rock_faces}: bare rock on every steep face of the Dross's land (cliffs, mesa edges, mountain
 * slopes, the sides of rocky knolls), whichever way it faces.
 *
 * <p>Why a feature: the surface rules pick each column's blocks from noise alone, so they can't tell where the land is
 * really steep (Minecraft's own "steep" rule only spots slopes facing north or east, and only inside one chunk). This
 * runs once per chunk, first of all the features, after the surface is built: it reads the ground height of every
 * column and its four neighbours (the neighbours may be in the next chunk), and where a column's ground stands at least
 * {@code min_exposed} blocks above a neighbour's (so its side shows), it swaps that column's top blocks (the biome's
 * ground cover and soil, block tag {@code replaceable}) for the biome's rock, as deep as the side shows plus one (at most
 * {@code max_depth}). Gentle slopes and flat ground keep their cover.
 *
 * <p>Config (written by {@code tools/terrain/worldgen.py}, configured feature {@code dross:rock_faces}):
 * <pre>{"rocks": [{"biome": "dross:cinder_wastes", "rock": {"Name": "minecraft:basalt"}}, ...],
 *  "default_rock": {"Name": "dross:duskstone"}, "min_exposed": 2, "max_depth": 8, "replaceable": "#dross:rock_face_replaceable"}</pre>
 */
public class RockFacesFeature extends Feature<RockFacesFeature.Config>
{
    /** The rock of one biome. */
    public record BiomeRock(ResourceKey<Biome> biome, BlockState rock)
    {
        public static final Codec<BiomeRock> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ResourceKey.codec(Registries.BIOME).fieldOf("biome").forGetter(BiomeRock::biome),
                BlockState.CODEC.fieldOf("rock").forGetter(BiomeRock::rock)
        ).apply(instance, BiomeRock::new));
    }

    /**
     * @param rocks       each biome's rock
     * @param defaultRock the rock of any other biome
     * @param minExposed  a column is a face if its ground is at least this many blocks above a neighbour's
     * @param maxDepth    at most this many blocks of a column are swapped
     * @param replaceable only these blocks are swapped (ground cover and soil, never ores, logs or anything built)
     */
    public record Config(List<BiomeRock> rocks, BlockState defaultRock, int minExposed, int maxDepth, TagKey<Block> replaceable)
            implements FeatureConfiguration
    {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BiomeRock.CODEC.listOf().fieldOf("rocks").forGetter(Config::rocks),
                BlockState.CODEC.fieldOf("default_rock").forGetter(Config::defaultRock),
                Codec.intRange(1, 64).fieldOf("min_exposed").forGetter(Config::minExposed),
                Codec.intRange(1, 64).fieldOf("max_depth").forGetter(Config::maxDepth),
                TagKey.hashedCodec(Registries.BLOCK).fieldOf("replaceable").forGetter(Config::replaceable)
        ).apply(instance, Config::new));

        BlockState rockFor(Holder<Biome> biome)
        {
            for (BiomeRock entry : rocks)
            {
                if (biome.is(entry.biome()))
                {
                    return entry.rock();
                }
            }
            return defaultRock;
        }
    }

    /** The ground is never searched for deeper than this under the heightmap (blocks): past a tree's crown and trunk. */
    private static final int MAX_GROUND_SEARCH = 48;

    public RockFacesFeature(Codec<Config> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context)
    {
        WorldGenLevel level = context.level();
        Config config = context.config();
        int x0 = SectionPos.blockToSectionCoord(context.origin().getX()) << 4;
        int z0 = SectionPos.blockToSectionCoord(context.origin().getZ()) << 4;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        // Ground heights of the chunk and a one-column border round it (from the neighbouring chunks).
        int[] ground = new int[18 * 18];
        for (int j = 0; j < 18; j++)
        {
            for (int i = 0; i < 18; i++)
            {
                ground[j * 18 + i] = groundY(level, x0 + i - 1, z0 + j - 1, pos);
            }
        }

        boolean changed = false;
        for (int j = 1; j <= 16; j++)
        {
            for (int i = 1; i <= 16; i++)
            {
                int h = ground[j * 18 + i];
                int lowest = Math.min(Math.min(ground[j * 18 + i - 1], ground[j * 18 + i + 1]), Math.min(ground[(j - 1) * 18 + i], ground[(j + 1) * 18 + i]));
                int exposed = h - lowest;
                if (exposed < config.minExposed() || h <= level.getMinBuildHeight())
                {
                    continue;
                }
                int x = x0 + i - 1;
                int z = z0 + j - 1;
                BlockState rock = config.rockFor(level.getBiome(pos.set(x, h, z)));
                int depth = Math.min(exposed + 1, config.maxDepth());
                for (int k = 0; k < depth; k++)
                {
                    pos.set(x, h - k, z);
                    BlockState here = level.getBlockState(pos);
                    if (!here.is(config.replaceable()))
                    {
                        break;
                    }
                    if (here != rock)
                    {
                        level.setBlock(pos, rock, Block.UPDATE_CLIENTS);
                        changed = true;
                    }
                }
            }
        }
        return changed;
    }

    /** The y of the column's top ground block: the heightmap, then down past anything that isn't ground (trees, plants...). */
    private static int groundY(WorldGenLevel level, int x, int z, BlockPos.MutableBlockPos pos)
    {
        int top = level.getHeight(Heightmap.Types.OCEAN_FLOOR_WG, x, z) - 1;
        int bottom = Math.max(level.getMinBuildHeight(), top - MAX_GROUND_SEARCH);
        for (int y = top; y > bottom; y--)
        {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            if (state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.getBlock() instanceof HugeMushroomBlock || state.canBeReplaced()
                    || !state.getFluidState().isEmpty())
            {
                continue;
            }
            return y;
        }
        return top;
    }
}
