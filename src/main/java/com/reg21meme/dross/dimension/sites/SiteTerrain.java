package com.reg21meme.dross.dimension.sites;

import com.reg21meme.dross.Dross;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;

/**
 * The Dross's land as its noise makes it, for one world seed: what {@link BossSites} and the uses' fit tests
 * ({@link SiteFinder}) read. Nothing here loads or generates a chunk, and it's safe on any thread (world generation's
 * too): every call makes its own working state, and the noises never change.
 * <ul>
 *   <li>{@link #biome} / {@link #isBiome}: the surface biome of a column (read at {@link BossSitesTuning#BIOME_Y}).</li>
 *   <li>{@link #height}: the terrain's ground height {@code dross:terrain/height} (quick; the top block is about this
 *       minus 1, before the little roughness on rocky land).</li>
 *   <li>{@link #topBlock}: the generator's own top block of a column (exact, slower: about 0.1 ms).</li>
 *   <li>{@link #discIsBiome}: every column within a radius is one biome (sampled on rings).</li>
 * </ul>
 * For anything else, the world's own generator objects are here too ({@link #generator}, {@link #randomState},
 * {@link #biomeSource}, {@link #heights}, {@link #registryAccess}).
 */
public final class SiteTerrain
{
    private final long seed;
    private final RandomState randomState;
    private final NoiseBasedChunkGenerator generator;
    private final BiomeSource biomeSource;
    private final Climate.Sampler sampler;
    private final LevelHeightAccessor heights;
    private final RegistryAccess registryAccess;
    private final DensityFunction height;

    private SiteTerrain(ServerLevel dross, long seed, RandomState randomState)
    {
        if (!(dross.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator noise))
        {
            throw new IllegalStateException("the Dross isn't generated from noise (" + dross.getChunkSource().getGenerator() + ")");
        }
        this.seed = seed;
        this.randomState = randomState;
        this.generator = noise;
        this.biomeSource = noise.getBiomeSource();
        this.sampler = randomState.sampler();
        this.heights = dross;
        this.registryAccess = dross.registryAccess();
        this.height = wired(new ResourceLocation(Dross.MODID, "terrain/height"));
    }

    /** The running world's own land (the same noise its chunks are made from). */
    static SiteTerrain forWorld(ServerLevel dross)
    {
        return new SiteTerrain(dross, dross.getSeed(), dross.getChunkSource().randomState());
    }

    /** The land a world with this seed would have (for the headless checks and previews). */
    static SiteTerrain forSeed(ServerLevel dross, long seed)
    {
        NoiseBasedChunkGenerator noise = (NoiseBasedChunkGenerator) dross.getChunkSource().getGenerator();
        RandomState state = RandomState.create(noise.generatorSettings().value(), dross.registryAccess().lookupOrThrow(Registries.NOISE), seed);
        return new SiteTerrain(dross, seed, state);
    }

    public long seed()
    {
        return this.seed;
    }

    public RandomState randomState()
    {
        return this.randomState;
    }

    public NoiseBasedChunkGenerator generator()
    {
        return this.generator;
    }

    public BiomeSource biomeSource()
    {
        return this.biomeSource;
    }

    /** The Dross level's heights (its lowest block and how tall it is), for {@link NoiseBasedChunkGenerator} calls. */
    public LevelHeightAccessor heights()
    {
        return this.heights;
    }

    public RegistryAccess registryAccess()
    {
        return this.registryAccess;
    }

    /** The surface biome of the column. */
    public Holder<Biome> biome(int x, int z)
    {
        return this.biomeSource.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(BossSitesTuning.BIOME_Y), QuartPos.fromBlock(z), this.sampler);
    }

    /** True if the column's surface biome is {@code biome}. */
    public boolean isBiome(ResourceKey<Biome> biome, int x, int z)
    {
        return biome(x, z).is(biome);
    }

    /** The terrain's ground height {@code dross:terrain/height} at the column (the top block is at about this minus 1). */
    public double height(int x, int z)
    {
        return this.height.compute(new DensityFunction.SinglePointContext(x, 0, z));
    }

    /** The column's top ground block as the generator makes it (before plants, trees, ponds and structures). */
    public int topBlock(int x, int z)
    {
        return this.generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, this.heights, this.randomState) - 1;
    }

    /**
     * True if every column within {@code radius} of x, z is {@code biome}: the middle, then rings {@code step} blocks
     * apart with samples about {@code step} apart round each (and the outer ring at the radius itself).
     */
    public boolean discIsBiome(ResourceKey<Biome> biome, int x, int z, int radius, int step)
    {
        if (!isBiome(biome, x, z))
        {
            return false;
        }
        for (int r = step; r < radius + step; r += step)
        {
            int ring = Math.min(r, radius);
            int points = Math.max(6, (int) Math.ceil(2.0D * Math.PI * ring / step));
            for (int i = 0; i < points; i++)
            {
                double a = 2.0D * Math.PI * i / points;
                if (!isBiome(biome, (int) Math.round(x + ring * Math.cos(a)), (int) Math.round(z + ring * Math.sin(a))))
                {
                    return false;
                }
            }
        }
        return true;
    }

    /** The registered density function {@code id}, with its noises made for this seed (as world generation does). */
    private DensityFunction wired(ResourceLocation id)
    {
        DensityFunction function = this.registryAccess.registryOrThrow(Registries.DENSITY_FUNCTION)
                .getOrThrow(ResourceKey.create(Registries.DENSITY_FUNCTION, id));
        return function.mapAll(new DensityFunction.Visitor()
        {
            @Override
            public DensityFunction apply(DensityFunction f)
            {
                return f;
            }

            @Override
            public DensityFunction.NoiseHolder visitNoise(DensityFunction.NoiseHolder noise)
            {
                Holder<NormalNoise.NoiseParameters> data = noise.noiseData();
                return new DensityFunction.NoiseHolder(data, SiteTerrain.this.randomState.getOrCreateNoise(data.unwrapKey().orElseThrow()));
            }
        });
    }
}
