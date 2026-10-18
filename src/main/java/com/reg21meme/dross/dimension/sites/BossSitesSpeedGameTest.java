package com.reg21meme.dross.dimension.sites;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.SewerSites;
import com.reg21meme.dross.portal.DrossHub;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * A timing probe of the boss-site planner ({@link BossSites}): how long its answers take for places 2,000 / 5,000 /
 * 8,000 / 12,000 blocks from the hub, and how long a world-generation thread can be kept waiting. It's slow (about a
 * minute) and only measures, so it isn't part of the normal run: it has its own GameTest namespace, {@code dross_speed}.
 * Run it with
 * <pre>.\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_speed -PgameTestWorld=&lt;a new folder&gt; --console=plain</pre>
 * and read the {@code [boss sites speed]} lines in the log. What it does:
 * <ol>
 *   <li><b>sampling</b>: what one biome sample costs (Minecraft's own climate sampler, and the planner's
 *       {@link SiteTerrain#biome}).</li>
 *   <li><b>cold</b>: a fresh plan of this world's seed (nothing read yet, no background reading), asked about a Blightwood
 *       chunk 2,000 blocks out, then 5,000, 8,000 and 12,000: the time to read the biome map out to it ({@code patchAt})
 *       and to plan its group (the fit tests).</li>
 *   <li><b>contention</b>: on a fresh plan already read out to 1,500 blocks, one thread asks about 12,000 blocks out
 *       while 8 others keep asking about places 1,500 blocks out (already read): their worst wait.</li>
 *   <li><b>world</b>: this world's own plan, as world generation uses it: for a spot 2,000, 5,000, 8,000 and 12,000
 *       blocks out, 8 threads ask what structure placement and the feature filter ask for each of the 25 x 25 chunks
 *       round it (as if a player had just arrived there): the worst single call, and the time for the whole area.</li>
 * </ol>
 */
@GameTestHolder(BossSitesSpeedGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class BossSitesSpeedGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The probe's own GameTest namespace (so the normal run, which runs only "dross", leaves it out). */
    public static final String NAMESPACE = "dross_speed";
    private static final String EMPTY = "empty";
    private static final ResourceLocation EMPTY_ID = new ResourceLocation(NAMESPACE, EMPTY);
    /** The distances asked about (blocks from the hub). */
    private static final int[] DISTANCES = {2_000, 5_000, 8_000, 12_000};
    /** Samples timed for the sampling cost. */
    private static final int SAMPLES = 100_000;
    /** Threads that play world generation's workers. */
    private static final int WORKERS = 8;
    /** The area round each spot the workers go through (chunks from its middle). */
    private static final int AREA_RADIUS = 12;
    /** The contention scenario's near place, already read (blocks from the hub). */
    private static final int NEAR = 1_500;
    private static final long TIMEOUT_MILLIS = 20 * 60 * 1000L;
    private static final int BACKSTOP_TICKS = 2_000_000;

    private BossSitesSpeedGameTest() {}

    @GameTest(template = EMPTY, batch = "dross_speed", timeoutTicks = BACKSTOP_TICKS)
    public static void bossSitesSpeed(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        CompletableFuture<List<String>> run = CompletableFuture.supplyAsync(() -> probe(dross), Util.backgroundExecutor());
        long deadline = Util.getMillis() + TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!run.isDone() && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still measuring");
                    }
                })
                .thenExecute(() -> {
                    if (!run.isDone())
                    {
                        throw new GameTestAssertException("the probe took more than " + TIMEOUT_MILLIS / 1000 + " s");
                    }
                    for (String line : run.join())
                    {
                        LOGGER.info("[boss sites speed] {}", line);
                    }
                })
                .thenSucceed();
    }

    private static List<String> probe(ServerLevel dross)
    {
        List<String> out = new ArrayList<>();
        out.add("seed " + dross.getSeed() + ", " + Runtime.getRuntime().availableProcessors() + " processors");
        // 1. sampling
        BossSites fresh = BossSites.forSeed(dross, dross.getSeed());
        SiteTerrain terrain = fresh.terrain();
        long t0 = System.nanoTime();
        long sink = 0;
        for (int i = 0; i < SAMPLES; i++)
        {
            int x = 40_000 + (i % 400) * 16;
            int z = -30_000 + (i / 400) * 16;
            sink += terrain.biomeSource().getNoiseBiome(QuartPos.fromBlock(x), QuartPos.fromBlock(BossSitesTuning.BIOME_Y), QuartPos.fromBlock(z),
                    terrain.randomState().sampler()).hashCode();
        }
        long vanilla = System.nanoTime() - t0;
        t0 = System.nanoTime();
        for (int i = 0; i < SAMPLES; i++)
        {
            int x = -40_000 + (i % 400) * 16;
            int z = 30_000 + (i / 400) * 16;
            sink += terrain.biome(x, z).hashCode();
        }
        long planner = System.nanoTime() - t0;
        out.add(String.format(Locale.ROOT, "sampling: Minecraft's climate sampler %.2f us a sample, SiteTerrain.biome %.2f us a sample (%d)",
                vanilla / 1000.0D / SAMPLES, planner / 1000.0D / SAMPLES, sink & 1));
        // the spots
        List<BlockPos> spots = new ArrayList<>();
        for (int d : DISTANCES)
        {
            spots.add(blightwoodAt(terrain, d));
        }
        // 2. cold
        BossSites cold = BossSites.forSeed(dross, dross.getSeed());
        for (int i = 0; i < DISTANCES.length; i++)
        {
            BlockPos p = spots.get(i);
            long samples = SiteTerrain.SAMPLES.sum();
            long a = System.nanoTime();
            Optional<Patch> patch = cold.patchAt(DrossBiomes.BLIGHTWOOD, p.getX(), p.getZ());
            long scan = System.nanoTime() - a;
            samples = SiteTerrain.SAMPLES.sum() - samples;
            a = System.nanoTime();
            Optional<Group> group = patch.flatMap(pp -> cold.group(DrossBiomes.BLIGHTWOOD, pp.group()));
            long plan = System.nanoTime() - a;
            a = System.nanoTime();
            cold.isSiteChunk(BossSites.RAT_KING_SEWER, p.getX() >> 4, p.getZ() >> 4);
            long check = System.nanoTime() - a;
            out.add(String.format(Locale.ROOT, "cold %5d blocks out (%s): read the map out to it %6d ms (%d samples), plan its group %5d ms (%s), then isSiteChunk %d ms",
                    DISTANCES[i], p.toShortString(), scan / 1_000_000, samples, plan / 1_000_000,
                    patch.map(pp -> "patch " + pp.number() + ", group " + (pp.group() + 1)).orElse("no patch") + group.map(g -> "").orElse(""),
                    check / 1_000_000));
        }
        // 3. contention
        BossSites shared = BossSites.forSeed(dross, dross.getSeed());
        BlockPos near = blightwoodAt(terrain, NEAR);
        shared.patchAt(DrossBiomes.BLIGHTWOOD, near.getX(), near.getZ()).ifPresent(p -> shared.group(DrossBiomes.BLIGHTWOOD, p.group()));
        BlockPos far = spots.get(spots.size() - 1);
        AtomicLong farTook = new AtomicLong();
        Thread farThread = new Thread(() -> {
            long a = System.nanoTime();
            shared.patchAt(DrossBiomes.BLIGHTWOOD, far.getX(), far.getZ());
            farTook.set(System.nanoTime() - a);
        }, "boss sites probe far");
        farThread.start();
        sleep(100);
        AtomicLong worstNear = new AtomicLong();
        AtomicInteger nearCalls = new AtomicInteger();
        ExecutorService pool = Executors.newFixedThreadPool(WORKERS);
        List<Future<?>> jobs = new ArrayList<>();
        for (int w = 0; w < WORKERS; w++)
        {
            final int ww = w;
            jobs.add(pool.submit(() -> {
                for (int k = 0; k < 40; k++)
                {
                    int x = near.getX() + (ww * 40 + k) % 7 * 16;
                    int z = near.getZ() + (ww * 40 + k) / 7 % 7 * 16;
                    long a = System.nanoTime();
                    shared.patchAt(DrossBiomes.BLIGHTWOOD, x, z);
                    shared.isSiteChunk(BossSites.RAT_KING_SEWER, x >> 4, z >> 4);
                    worstNear.accumulateAndGet(System.nanoTime() - a, Math::max);
                    nearCalls.incrementAndGet();
                }
            }));
        }
        for (Future<?> f : jobs)
        {
            join(f);
        }
        try
        {
            farThread.join();
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        out.add(String.format(Locale.ROOT, "contention: one thread read the map out to %d blocks in %d ms; meanwhile %d calls about %d blocks out "
                + "(already read) on %d threads waited at worst %d ms", DISTANCES[DISTANCES.length - 1], farTook.get() / 1_000_000, nearCalls.get(), NEAR,
                WORKERS, worstNear.get() / 1_000_000));
        // 4. world
        BossSites world = BossSites.forLevel(dross);
        out.add(String.format(Locale.ROOT, "world: this world's plan had read the biome map out to %d blocks when the probe got to it",
                Math.round(world.readDistance())));
        for (int i = 0; i < DISTANCES.length; i++)
        {
            BlockPos p = spots.get(i);
            ChunkPos middle = new ChunkPos(p);
            List<ChunkPos> chunks = new ArrayList<>();
            for (int dz = -AREA_RADIUS; dz <= AREA_RADIUS; dz++)
            {
                for (int dx = -AREA_RADIUS; dx <= AREA_RADIUS; dx++)
                {
                    chunks.add(new ChunkPos(middle.x + dx, middle.z + dz));
                }
            }
            chunks.sort((c1, c2) -> Integer.compare(c1.getChessboardDistance(middle), c2.getChessboardDistance(middle)));
            AtomicInteger next = new AtomicInteger();
            AtomicLong worst = new AtomicLong();
            AtomicInteger calls = new AtomicInteger();
            long a = System.nanoTime();
            List<Future<?>> area = new ArrayList<>();
            for (int w = 0; w < WORKERS; w++)
            {
                area.add(pool.submit(() -> {
                    int k;
                    while ((k = next.getAndIncrement()) < chunks.size())
                    {
                        ChunkPos c = chunks.get(k);
                        long b = System.nanoTime();
                        world.isSiteChunk(BossSites.RAT_KING_SEWER, c.x, c.z);
                        world.isSiteChunk(BossSites.REGULAR_SEWER, c.x, c.z);
                        if (world.terrain().isBiome(DrossBiomes.BLIGHTWOOD, c.getMiddleBlockX(), c.getMiddleBlockZ()))
                        {
                            SewerSites.nearEntrance(world, c.getMiddleBlockX(), c.getMiddleBlockZ());
                        }
                        worst.accumulateAndGet(System.nanoTime() - b, Math::max);
                        calls.incrementAndGet();
                    }
                }));
            }
            for (Future<?> f : area)
            {
                join(f);
            }
            out.add(String.format(Locale.ROOT, "world %5d blocks out: %d chunks on %d threads in %d ms, worst single chunk's calls %d ms",
                    DISTANCES[i], calls.get(), WORKERS, (System.nanoTime() - a) / 1_000_000, worst.get() / 1_000_000));
        }
        pool.shutdown();
        try
        {
            pool.awaitTermination(1, TimeUnit.MINUTES);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
        return out;
    }

    /** A Blightwood chunk's middle about {@code distance} blocks from the hub (the first one going round from the east). */
    private static BlockPos blightwoodAt(SiteTerrain terrain, int distance)
    {
        for (int r = distance; r < distance + 4_000; r += 64)
        {
            for (int step = 0; step < 720; step++)
            {
                double a = Math.toRadians(step * 0.5D);
                int x = DrossHub.CENTER.getX() + (int) Math.round(r * Math.cos(a));
                int z = DrossHub.CENTER.getZ() + (int) Math.round(r * Math.sin(a));
                ChunkPos c = new ChunkPos(new BlockPos(x, 0, z));
                if (terrain.isBiome(DrossBiomes.BLIGHTWOOD, c.getMiddleBlockX(), c.getMiddleBlockZ()))
                {
                    return new BlockPos(c.getMiddleBlockX(), 0, c.getMiddleBlockZ());
                }
            }
        }
        throw new IllegalStateException("no Blightwood found about " + distance + " blocks out");
    }

    private static void sleep(long millis)
    {
        try
        {
            Thread.sleep(millis);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    private static void join(Future<?> f)
    {
        try
        {
            f.get();
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The probe's empty template (made in memory, like PortalSiteGameTest's)
    // ---------------------------------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(EMPTY_ID);
        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", intList(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        template.load(BuiltInRegistries.BLOCK.asLookup(), tag);
    }

    private static ListTag intList(int... values)
    {
        ListTag list = new ListTag();
        for (int v : values)
        {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }
}
