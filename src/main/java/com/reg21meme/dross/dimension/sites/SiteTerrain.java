package com.reg21meme.dross.dimension.sites;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.KeyDispatchDataCodec;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSource;
import net.minecraft.world.level.biome.Climate;
import net.minecraft.world.level.levelgen.DensityFunction;
import net.minecraft.world.level.levelgen.DensityFunctions;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.NoiseRouter;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraft.world.level.levelgen.synth.NormalNoise;
import org.slf4j.Logger;

/**
 * The Dross's land as its noise makes it, for one world seed: what {@link BossSites} and the uses' fit tests
 * ({@link SiteFinder}) read. Nothing here loads or generates a chunk, and it's safe on any thread (world generation's
 * too): every call makes its own working state (or uses its thread's own), and the noises never change.
 * <ul>
 *   <li>{@link #biome} / {@link #isBiome}: the surface biome of a column (read at {@link BossSitesTuning#BIOME_Y}).</li>
 *   <li>{@link #height}: the terrain's ground height {@code dross:terrain/height} (quick; the top block is about this
 *       minus 1, before the little roughness on rocky land).</li>
 *   <li>{@link #topBlock}: the generator's own top block of a column (exact, slower: about 0.1 ms).</li>
 *   <li>{@link #discIsBiome}: every column within a radius is one biome (sampled on rings).</li>
 * </ul>
 * For anything else, the world's own generator objects are here too ({@link #generator}, {@link #randomState},
 * {@link #biomeSource}, {@link #heights}, {@link #registryAccess}).
 * <h2>Quicker biome samples</h2>
 * Minecraft's own climate sampler ({@link RandomState#sampler()}) works every climate value out from scratch, so each of
 * the Dross's four climate noises works the two shared warp noises out again (12 noise reads a sample). {@link #biome}
 * uses a copy of the same climate functions in which each shared piece (each {@code flat_cache} / {@code cache_2d} of
 * the data files) remembers its value for the point being sampled, so the warps are read once (6 noise reads). It's the
 * same maths on the same numbers, so it gives exactly the same biome; to be sure, a new SiteTerrain compares the two on
 * {@link #CHECK_POINTS} spread-out points and falls back to Minecraft's sampler (an ERROR in the log) if they ever
 * differ. Each thread has its own copy (the remembered values aren't shared).
 */
public final class SiteTerrain
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Biome samples taken so far (all plans): for the planner's log and the timing probe. */
    static final LongAdder SAMPLES = new LongAdder();
    /** The quick sampler is checked against Minecraft's on this many points (a grid reaching about 24,000 blocks out). */
    private static final int CHECK_POINTS = 256;

    private final long seed;
    private final RandomState randomState;
    private final NoiseBasedChunkGenerator generator;
    private final BiomeSource biomeSource;
    /** Minecraft's own climate sampler (thread-safe, no memory between calls). */
    private final Climate.Sampler sampler;
    /** The quick sampler, one per thread; null if it failed its check (then {@link #sampler} is used). */
    private final ThreadLocal<Climate.Sampler> quick;
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
        ThreadLocal<Climate.Sampler> quick = ThreadLocal.withInitial(() -> rememberingSampler(randomState));
        this.quick = sameAsMinecraft(quick.get()) ? quick : null;
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
        SAMPLES.increment();
        Climate.Sampler s = this.quick != null ? this.quick.get() : this.sampler;
        return this.biomeSource.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(BossSitesTuning.BIOME_Y), QuartPos.fromBlock(z), s);
    }

    /** The surface biome of the column through Minecraft's own climate sampler (for the checks: {@link #biome} must agree). */
    Holder<Biome> biomeByMinecraft(int x, int z)
    {
        return this.biomeSource.getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(BossSitesTuning.BIOME_Y), QuartPos.fromBlock(z), this.sampler);
    }

    /** True if {@link #biome} uses the quick sampler (it passed its check). */
    boolean quickSampler()
    {
        return this.quick != null;
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

    // ---------------------------------------------------------------------------------------------
    // The quick sampler
    // ---------------------------------------------------------------------------------------------

    /**
     * Minecraft's climate functions for this seed (its router, noises already made), put together the way
     * {@link RandomState} puts its own sampler together (references to registered functions inlined), except that each
     * cache marker becomes a {@link PointMemo} instead of being dropped. Equal pieces become the same object, so a piece
     * several climate values use (the warps) is worked out once per point.
     */
    private static Climate.Sampler rememberingSampler(RandomState state)
    {
        NoiseRouter router = state.router();
        DensityFunction.Visitor visitor = new DensityFunction.Visitor()
        {
            private final Map<DensityFunction, DensityFunction> made = new HashMap<>();

            @Override
            public DensityFunction apply(DensityFunction f)
            {
                return this.made.computeIfAbsent(f, g -> {
                    if (g instanceof DensityFunctions.HolderHolder holder)
                    {
                        return holder.function().value();
                    }
                    return g instanceof DensityFunctions.MarkerOrMarked marker ? new PointMemo(marker.wrapped()) : g;
                });
            }
        };
        return new Climate.Sampler(router.temperature().mapAll(visitor), router.vegetation().mapAll(visitor), router.continents().mapAll(visitor),
                router.erosion().mapAll(visitor), router.depth().mapAll(visitor), router.ridges().mapAll(visitor), state.sampler().spawnTarget());
    }

    /** True if the quick sampler gives exactly Minecraft's climate values on a spread of points (at the surface and deep down). */
    private boolean sameAsMinecraft(Climate.Sampler quick)
    {
        int side = (int) Math.sqrt(CHECK_POINTS);
        for (int i = 0; i < CHECK_POINTS; i++)
        {
            // a grid about 3,000 blocks apart, nudged a little differently at each point so they fall anywhere in their cells
            int x = (i % side - side / 2) * 3001 + 7 * i;
            int z = (i / side - side / 2) * 2999 - 5 * i;
            int y = i % 2 == 0 ? BossSitesTuning.BIOME_Y : 0;
            int qx = QuartPos.fromBlock(x);
            int qy = QuartPos.fromBlock(y);
            int qz = QuartPos.fromBlock(z);
            Climate.TargetPoint mine = quick.sample(qx, qy, qz);
            Climate.TargetPoint theirs = this.sampler.sample(qx, qy, qz);
            if (!mine.equals(theirs))
            {
                LOGGER.error("[boss sites] the quick biome sampler disagrees with Minecraft's at {}, {}, {} ({} / {}); using Minecraft's (slower)",
                        x, y, z, mine, theirs);
                return false;
            }
        }
        return true;
    }

    /**
     * A climate function that remembers its value for the last point it was asked about (one thread's copy only).
     * Density functions are pure (the same point always gives the same value), so this changes nothing but the time.
     */
    private static final class PointMemo implements DensityFunction
    {
        private final DensityFunction wrapped;
        private boolean known;
        private int x;
        private int y;
        private int z;
        private double value;

        PointMemo(DensityFunction wrapped)
        {
            this.wrapped = wrapped;
        }

        @Override
        public double compute(FunctionContext context)
        {
            int bx = context.blockX();
            int by = context.blockY();
            int bz = context.blockZ();
            if (this.known && bx == this.x && by == this.y && bz == this.z)
            {
                return this.value;
            }
            double v = this.wrapped.compute(context);
            this.x = bx;
            this.y = by;
            this.z = bz;
            this.value = v;
            this.known = true;
            return v;
        }

        @Override
        public void fillArray(double[] values, ContextProvider provider)
        {
            provider.fillAllDirectly(values, this);
        }

        @Override
        public DensityFunction mapAll(Visitor visitor)
        {
            return visitor.apply(new PointMemo(this.wrapped.mapAll(visitor)));
        }

        @Override
        public double minValue()
        {
            return this.wrapped.minValue();
        }

        @Override
        public double maxValue()
        {
            return this.wrapped.maxValue();
        }

        @Override
        public KeyDispatchDataCodec<? extends DensityFunction> codec()
        {
            throw new UnsupportedOperationException("the boss-site planner's remembering climate function is never saved");
        }
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
