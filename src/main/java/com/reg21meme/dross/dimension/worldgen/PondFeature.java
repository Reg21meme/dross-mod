package com.reg21meme.dross.dimension.worldgen;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.WorldGenRegion;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.FeatureConfiguration;
import net.minecraft.world.level.levelgen.feature.stateproviders.BlockStateProvider;
import net.minecraft.world.level.material.FluidState;

/**
 * Feature {@code dross:pond}: a small pond sunk into level ground, with its bed and shore lined with the
 * {@code barrier} block (mud, clay, packed ice). The Silverwood Forest's dark ponds, the Blightwood's murky ones and
 * Rimefall's frozen ones.
 *
 * <p>Its shape is Minecraft's own lake's ({@code minecraft:lake}): 4 to 7 overlapping blobs in a box {@code size}
 * blocks square (16, vanilla's, unless the config asks for smaller pools) and 8 tall, starting at its origin's column
 * and reaching into the chunks to the east and south; the blobs' lower half fills with the fluid, their upper half is
 * cleared. Its surface lies at the lowest ground under it and round its rim (the land as the terrain made it), so the
 * ground holds the fluid in on every side; where that ground varies by more than {@code max_bank} blocks it doesn't form
 * (no ponds dug into hillsides). The same feature makes the Cinder Wastes' lava pools (fluid lava): every lava block is
 * held in on every side and below. Three things differ from vanilla's lake:
 * <ul>
 *   <li>It never digs into anything standing on the ground. Vanilla's lake clears whatever is in its upper half, so
 *       where a neighbouring chunk already has its trees, it cuts through their trunks and leaves them floating. A pond
 *       only forms if everything it would clear is air, a small plant (or snow or ash layer, or vine) or the ground
 *       itself (block tag {@code ground}), nothing but the plain ground (a bank) would be left resting on cleared
 *       space, and no log, leaves, giant mushroom or fungus, or block with a block entity is in its way or right beside
 *       it; otherwise it doesn't form at all (nothing is changed).</li>
 *   <li>Only the ground (tag {@code ground}) becomes its shore.</li>
 *   <li>Where its biome is cold enough for water to freeze, its top freezes over as it forms (the whole pond at once,
 *       even the part in the next chunk). Vanilla's lake means to do this too, but checks one block too high.</li>
 * </ul>
 *
 * <p>Config (written by {@code tools/terrain/worldgen.py}, FEATURES):
 * <pre>{"fluid": state provider, "barrier": state provider, "ground": "#dross:pond_ground", "size": 16, "max_bank": 2}</pre>
 * ({@code size} is optional: 8 to 16, default 16; the blobs shrink with it. {@code max_bank} is optional: 0 to 4, default 2.)
 */
public class PondFeature extends Feature<PondFeature.Config>
{
    /** The box the pond is shaped in: at most this wide (X and Z; Minecraft's lake's)... */
    private static final int MAX_SIZE = 16;
    /** ...and this tall; the lower half (below the ground's top) holds the fluid. */
    private static final int HEIGHT = 8;
    private static final int FLUID_LAYERS = HEIGHT / 2;

    /**
     * Whether tries are counted and refusals described (only the headless checks turn it on, with {@link #recordStats}):
     * off in a normal game, so world generation never pays for it (a refusal's description is a string built per try).
     */
    private static volatile boolean recording = false;
    /** Why tries made no pond (only for the headless check's log): tries, ponds made, too uneven, leaks, things in the way. */
    private static final AtomicLong TRIES = new AtomicLong();
    private static final AtomicLong MADE = new AtomicLong();
    private static final AtomicLong UNEVEN = new AtomicLong();
    private static final AtomicLong LEAKS = new AtomicLong();
    private static final AtomicLong BLOCKED = new AtomicLong();
    /** Why the last try made no pond (for the headless checks' messages). */
    private static volatile String lastRefusal = "none yet";

    /**
     * @param fluid   what fills it (water)
     * @param barrier what its bed and shore become
     * @param ground  the plain ground it may dig into and line with {@code barrier}
     * @param size    the width of the box it's shaped in (8 to 16): smaller makes smaller pools
     * @param maxBank the most the ground under the pond and round its rim may rise above its surface (blocks)
     */
    public record Config(BlockStateProvider fluid, BlockStateProvider barrier, TagKey<Block> ground, int size, int maxBank)
            implements FeatureConfiguration
    {
        public static final Codec<Config> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BlockStateProvider.CODEC.fieldOf("fluid").forGetter(Config::fluid),
                BlockStateProvider.CODEC.fieldOf("barrier").forGetter(Config::barrier),
                TagKey.hashedCodec(Registries.BLOCK).fieldOf("ground").forGetter(Config::ground),
                Codec.intRange(8, MAX_SIZE).optionalFieldOf("size", MAX_SIZE).forGetter(Config::size),
                Codec.intRange(0, 4).optionalFieldOf("max_bank", 2).forGetter(Config::maxBank)
        ).apply(instance, Config::new));
    }

    /**
     * Starts counting tries and describing refusals ({@link #stats}, {@link #lastRefusal}) for the rest of this run. Only
     * the headless checks call it; it stays on once on (several checks may read the counts at once).
     */
    public static void recordStats()
    {
        recording = true;
    }

    /**
     * {tries, ponds made, tries on ground too uneven, tries that would leak, tries with something in the way} since
     * {@link #recordStats} was first called (all 0 if it never was).
     */
    public static long[] stats()
    {
        return new long[] {TRIES.get(), MADE.get(), UNEVEN.get(), LEAKS.get(), BLOCKED.get()};
    }

    /** Why the last try made no pond, in words (for the headless checks' messages; only once {@link #recordStats} was called). */
    public static String lastRefusal()
    {
        return lastRefusal;
    }

    private static boolean refuse(AtomicLong counter, String why, BlockPos pos, BlockState state)
    {
        if (recording)
        {
            counter.incrementAndGet();
            lastRefusal = why + " at " + pos.toShortString() + (state == null ? "" : " (" + state + ")");
        }
        return false;
    }

    public PondFeature(Codec<Config> codec)
    {
        super(codec);
    }

    @Override
    public boolean place(FeaturePlaceContext<Config> context)
    {
        WorldGenLevel level = context.level();
        RandomSource random = context.random();
        Config config = context.config();
        if (recording)
        {
            TRIES.incrementAndGet();
        }
        int size = config.size();
        BlockPos corner = context.origin();
        boolean[] inside = shape(random, size);

        // 0. Its surface: the lowest natural ground top under the pond and round its rim (the terrain's own heightmap,
        // which features don't change), if that ground is level enough.
        int lowest = Integer.MAX_VALUE;
        int highest = Integer.MIN_VALUE;
        for (int x = 0; x < size; x++)
        {
            for (int z = 0; z < size; z++)
            {
                if (nearColumn(inside, x, z, size))
                {
                    int top = groundTop(level, corner.getX() + x, corner.getZ() + z, corner.getY() + config.maxBank() + 1);
                    lowest = Math.min(lowest, top);
                    highest = Math.max(highest, top);
                }
            }
        }
        if (lowest == Integer.MAX_VALUE || highest - lowest > config.maxBank())
        {
            return refuse(UNEVEN, recording ? "the ground under it varies from y " + lowest + " to " + highest : "", corner, null);
        }
        BlockPos base = new BlockPos(corner.getX(), lowest + 1 - FLUID_LAYERS, corner.getZ());
        if (base.getY() <= level.getMinBuildHeight())
        {
            return refuse(UNEVEN, "the ground is at the bottom of the world", corner, null);
        }
        BlockState fluid = config.fluid().getState(random, base);

        // 1. Would it hold its fluid, and is anything in its way? (Nothing is changed until everything checks out.)
        for (int x = 0; x < size; x++)
        {
            for (int z = 0; z < size; z++)
            {
                for (int y = 0; y < HEIGHT; y++)
                {
                    BlockPos pos = base.offset(x, y, z);
                    boolean upper = y >= FLUID_LAYERS;
                    if (inside[index(x, y, z)])
                    {
                        BlockState state = level.getBlockState(pos);
                        if (upper ? !clearable(state, config) : isStanding(state))
                        {
                            return refuse(BLOCKED, upper ? "something it can't clear" : "something standing where its water would be", pos, state);
                        }
                        // only the plain ground (a bank) may be left resting on cleared space, never a tree or a spire
                        if (upper && y + 1 < HEIGHT && !inside[index(x, y + 1, z)] && !clearable(level.getBlockState(pos.above()), config))
                        {
                            return refuse(BLOCKED, "something resting on the space it would clear", pos.above(), level.getBlockState(pos.above()));
                        }
                    }
                    else if (touchesInside(inside, x, y, z, size))
                    {
                        BlockState state = level.getBlockState(pos);
                        if (upper)
                        {
                            if (state.liquid() || isStanding(state))
                            {
                                return refuse(BLOCKED, "something standing right beside it", pos, state);
                            }
                        }
                        else if (!state.isSolid() && state != fluid)
                        {
                            return refuse(LEAKS, "it would leak", pos, state);
                        }
                    }
                }
            }
        }

        // 2. Dig it and fill it (the same order and random numbers as Minecraft's lake).
        for (int x = 0; x < size; x++)
        {
            for (int z = 0; z < size; z++)
            {
                for (int y = 0; y < HEIGHT; y++)
                {
                    if (inside[index(x, y, z)])
                    {
                        BlockPos pos = base.offset(x, y, z);
                        boolean upper = y >= FLUID_LAYERS;
                        level.setBlock(pos, upper ? Blocks.AIR.defaultBlockState() : fluid, Block.UPDATE_CLIENTS);
                        if (upper)
                        {
                            markAboveForPostProcessing(level, pos);
                        }
                    }
                }
            }
        }

        // 3. Line its bed and shore (below the ground's top always, above it now and then), only where it's plain ground.
        BlockState barrier = config.barrier().getState(random, base);
        if (!barrier.isAir())
        {
            for (int x = 0; x < size; x++)
            {
                for (int z = 0; z < size; z++)
                {
                    for (int y = 0; y < HEIGHT; y++)
                    {
                        if (!inside[index(x, y, z)] && touchesInside(inside, x, y, z, size) && (y < FLUID_LAYERS || random.nextInt(2) != 0))
                        {
                            BlockPos pos = base.offset(x, y, z);
                            BlockState state = level.getBlockState(pos);
                            if (state.isSolid() && state.is(config.ground()))
                            {
                                level.setBlock(pos, barrier, Block.UPDATE_CLIENTS);
                                markAboveForPostProcessing(level, pos);
                            }
                        }
                    }
                }
            }
        }

        // 4. In the cold, its top freezes over: in each column, the highest fluid block with open air above it.
        for (int x = 0; x < size; x++)
        {
            for (int z = 0; z < size; z++)
            {
                for (int y = FLUID_LAYERS - 1; y >= 0; y--)
                {
                    if (inside[index(x, y, z)])
                    {
                        BlockPos pos = base.offset(x, y, z);
                        if (level.getBlockState(pos.above()).isAir() && level.getBiome(pos).value().shouldFreeze(level, pos, false))
                        {
                            level.setBlock(pos, Blocks.ICE.defaultBlockState(), Block.UPDATE_CLIENTS);
                        }
                        break;
                    }
                }
            }
        }
        if (recording)
        {
            MADE.incrementAndGet();
        }
        return true;
    }

    /**
     * The pond's blobs, as a mask of the box ({@code size} square, 8 tall). At size 16 it's Minecraft's lake's shape, with
     * the same random numbers; smaller boxes shrink the blobs' width with them.
     */
    private static boolean[] shape(RandomSource random, int size)
    {
        boolean[] inside = new boolean[MAX_SIZE * MAX_SIZE * HEIGHT];
        double scale = size / (double) MAX_SIZE;
        int blobs = random.nextInt(4) + 4;
        for (int blob = 0; blob < blobs; blob++)
        {
            double sizeX = (random.nextDouble() * 6.0D + 3.0D) * scale;
            double sizeY = random.nextDouble() * 4.0D + 2.0D;
            double sizeZ = (random.nextDouble() * 6.0D + 3.0D) * scale;
            double centreX = random.nextDouble() * (size - sizeX - 2.0D) + 1.0D + sizeX / 2.0D;
            double centreY = random.nextDouble() * (8.0D - sizeY - 4.0D) + 2.0D + sizeY / 2.0D;
            double centreZ = random.nextDouble() * (size - sizeZ - 2.0D) + 1.0D + sizeZ / 2.0D;
            for (int x = 1; x < size - 1; x++)
            {
                for (int z = 1; z < size - 1; z++)
                {
                    for (int y = 1; y < HEIGHT - 1; y++)
                    {
                        double dx = (x - centreX) / (sizeX / 2.0D);
                        double dy = (y - centreY) / (sizeY / 2.0D);
                        double dz = (z - centreZ) / (sizeZ / 2.0D);
                        if (dx * dx + dy * dy + dz * dz < 1.0D)
                        {
                            inside[index(x, y, z)] = true;
                        }
                    }
                }
            }
        }
        return inside;
    }

    private static int index(int x, int y, int z)
    {
        return (x * MAX_SIZE + z) * HEIGHT + y;
    }

    /**
     * The top of the ground in a column. In world generation: the terrain's own heightmap (the land as the noise and
     * surface made it, which features don't change). Placed in a finished world (the {@code /place feature} command, the
     * headless checks): looking down from {@code fromY} (just above where it was placed), the first block that isn't air,
     * a fluid, a small loose thing or something standing on the ground.
     */
    private static int groundTop(WorldGenLevel level, int x, int z, int fromY)
    {
        if (level instanceof WorldGenRegion)
        {
            return level.getHeight(Heightmap.Types.WORLD_SURFACE_WG, x, z) - 1;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(x, Math.min(fromY, level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1), z);
        while (pos.getY() > level.getMinBuildHeight())
        {
            BlockState state = level.getBlockState(pos);
            if (!(state.isAir() || isLoose(state) || isStanding(state) || !state.getFluidState().isEmpty()))
            {
                return pos.getY();
            }
            pos.move(Direction.DOWN);
        }
        return level.getMinBuildHeight();
    }

    /** True if the pond reaches this column or one beside it (its rim). */
    private static boolean nearColumn(boolean[] inside, int x, int z, int size)
    {
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                int cx = x + dx;
                int cz = z + dz;
                if (cx < 0 || cz < 0 || cx >= size || cz >= size)
                {
                    continue;
                }
                for (int y = 0; y < HEIGHT; y++)
                {
                    if (inside[index(cx, y, cz)])
                    {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /** True if a block next to this one (sides, above or below) is inside the pond (box {@code size} square). */
    private static boolean touchesInside(boolean[] inside, int x, int y, int z, int size)
    {
        return x < size - 1 && inside[index(x + 1, y, z)] || x > 0 && inside[index(x - 1, y, z)]
                || z < size - 1 && inside[index(x, y, z + 1)] || z > 0 && inside[index(x, y, z - 1)]
                || y < HEIGHT - 1 && inside[index(x, y + 1, z)] || y > 0 && inside[index(x, y - 1, z)];
    }

    /** What may be cleared above the water: air, small loose things, the plain ground (a low bank). */
    private static boolean clearable(BlockState state, Config config)
    {
        return state.isAir() || isLoose(state) || state.is(config.ground()) && !state.hasBlockEntity();
    }

    /** Small loose things a pond simply washes away: plants, snow and ash layers, vines, lichen. */
    private static boolean isLoose(BlockState state)
    {
        return state.canBeReplaced() && state.getFluidState().isEmpty() && !state.hasBlockEntity();
    }

    /**
     * Something that stands on the ground and mustn't be cut or undermined: a tree (logs and leaves, fungus stems), a
     * giant mushroom or fungus cap, anything with a block entity, or a block no feature may replace.
     */
    private static boolean isStanding(BlockState state)
    {
        FluidState fluid = state.getFluidState();
        return state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES) || state.getBlock() instanceof HugeMushroomBlock
                || state.is(BlockTags.WART_BLOCKS) || state.is(Blocks.SHROOMLIGHT) || state.hasBlockEntity()
                || state.is(BlockTags.FEATURES_CANNOT_REPLACE) || !fluid.isEmpty() && !fluid.isSource();
    }
}
