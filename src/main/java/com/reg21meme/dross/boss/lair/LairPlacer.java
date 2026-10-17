package com.reg21meme.dross.boss.lair;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.BossSitesTuning;
import com.reg21meme.dross.dimension.sites.Group;
import com.reg21meme.dross.dimension.sites.Site;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * Builds the Colossus lairs lazily, one per group of 3 Cinder Wastes patches (the boss-site planner's
 * {@link BossSites#COLOSSUS_LAIR} sites, chosen by {@link LairFinder}), without ever freezing the game:
 * <ol>
 *   <li><b>Who's near what</b>: every {@link LairTuning#NEAR_CHECK_TICKS} ticks the Dross players' positions are handed to
 *       a background thread, which asks the planner (noise only) for the lair sites within {@link LairTuning#BUILD_RANGE}
 *       of each. The first time an area is asked about, the planner works out its groups (a second or so); after that
 *       it's instant.</li>
 *   <li><b>Load</b>: a site with no lair yet is queued; its chunks ({@link LairTuning#LOAD_RADIUS} round it) are generated
 *       in the background with chunk tickets, at most {@link LairTuning#MAX_BUILDS_AT_ONCE} lair at a time.</li>
 *   <li><b>Build</b> ({@link LairBuilder}) a step a tick ({@link LairTuning#BUILD_STEPS} steps), then the chunks stay
 *       loaded a few seconds for their lighting. The lair is saved with its patch ({@link ColossusLairData}), so each is
 *       built once and a patch never gets two.</li>
 * </ol>
 * {@code /dross goto lair colossus} asks for the nearest site and waits for its lair here ({@link #whenBuilt}). If the
 * server stops while a lair is half built, it's finished at once (the world is still there); one that was still loading
 * is simply built the next time someone comes near.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
final class LairPlacer
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Keeps a lair's chunks loaded while it's built. */
    private static final TicketType<Long> LOAD_TICKET = TicketType.create("dross_colossus_lair", Long::compare);
    /** The planner is asked on this one low-priority thread, so neither the server nor world generation waits for it. */
    private static final ExecutorService LOOKUP_THREAD = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Dross Colossus lairs");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 2);
        return thread;
    });

    /** The lair sites waiting to be built (by their patch's id), first come first served. Server thread only. */
    private static final Map<Long, Site> QUEUE = new LinkedHashMap<>();
    /** The lairs being loaded or built. */
    private static final List<Job> JOBS = new ArrayList<>();
    /** Who's waiting for a lair to be built (by its patch's id): {@code goto lair colossus}. */
    private static final Map<Long, List<Consumer<ColossusLairData.Entry>>> WAITING = new LinkedHashMap<>();
    /** Sites whose build failed this run (not tried again until the next start, so a broken one doesn't loop). */
    private static final Set<Long> FAILED = new HashSet<>();
    private static final List<Hold> HOLDS = new ArrayList<>();
    @Nullable
    private static CompletableFuture<List<Site>> nearLookup;
    /** The first group's lair site (worked out as the world starts), for {@link ColossusLair#plannedSite}. */
    @Nullable
    private static volatile Site firstSite;
    private static long nextTicketKey;

    private LairPlacer() {}

    /** Low priority: after the hub is built. Works out the first groups' sites in the background (they're asked for first). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerStarted(ServerStartedEvent event)
    {
        MinecraftServer server = event.getServer();
        resetState();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            return;
        }
        ColossusLairData data = ColossusLairData.get(server);
        LairKeeper.refresh(data);
        LOGGER.info("Colossus lairs: {} built in this world so far; the others are built when a player first comes within {} blocks.",
                data.all().size(), BUILD_RANGE);
        CompletableFuture.runAsync(() -> {
            long started = Util.getMillis();
            BossSites sites = BossSites.forLevel(dross);
            StringBuilder found = new StringBuilder();
            for (int g = 0; g < WARM_UP_GROUPS; g++)
            {
                Optional<Group> group = sites.group(DrossBiomes.CINDER_WASTES, g);
                Optional<Site> site = group.flatMap(Group::bossSite);
                if (g == 0)
                {
                    firstSite = site.orElse(null);
                }
                found.append(String.format("%n  group %d: %s", g + 1, site.map(s -> "patch " + s.patch().number() + " at " + s.pos().getX() + " "
                        + s.pos().getZ() + ", " + Math.round(s.distanceTo(0, 0)) + " blocks from the hub" + (s.relaxed() ? " (its smallest form)" : ""))
                        .orElse(group.isPresent() ? "none (no patch of it can hold a lair)" : "none (past the planner's reach)")));
            }
            LOGGER.info("Colossus lairs: the first {} group(s) of Cinder Wastes patches hold their lairs at ({} ms):{}", WARM_UP_GROUPS,
                    Util.getMillis() - started, found);
        }, LOOKUP_THREAD).exceptionally(e -> {
            LOGGER.error("Colossus lairs: working out the first groups' lair sites failed.", e);
            return null;
        });
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        MinecraftServer server = event.getServer();
        releaseHolds(server.getTickCount());
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            return;
        }
        try
        {
            lookNear(server, dross);
            startJobs(server, dross);
            stepJobs(server, dross);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Colossus lairs: building the lairs failed.", e);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        MinecraftServer server = event.getServer();
        for (Job job : new ArrayList<>(JOBS))
        {
            if (job.build != null && !job.build.done())
            {
                LOGGER.info("Colossus lairs: the server is stopping while the lair at {} {} is half built; finishing it now.", job.x, job.z);
                try
                {
                    while (!job.build.step())
                    {
                        // the rest at once
                    }
                    job.finish(server);
                }
                catch (RuntimeException e)
                {
                    LOGGER.error("Colossus lairs: finishing the lair at {} {} failed.", job.x, job.z, e);
                }
            }
        }
        resetState();
        LairKeeper.clear();
    }

    private static void resetState()
    {
        QUEUE.clear();
        JOBS.clear();
        WAITING.clear();
        FAILED.clear();
        HOLDS.clear(); // chunk tickets go away with the server
        nearLookup = null;
        firstSite = null;
    }

    // ---------------------------------------------------------------- the API's side (ColossusLair)

    /** The first group's lair site, once it's worked out (a moment after the world starts). */
    static Optional<Site> firstSite()
    {
        return Optional.ofNullable(firstSite);
    }

    /** The planned lair site nearest {@code from} (along the ground), worked out on the background thread. */
    static CompletableFuture<Optional<Site>> nearestSite(ServerLevel dross, BlockPos from)
    {
        return CompletableFuture.supplyAsync(() -> BossSites.forLevel(dross).nearest(BossSites.COLOSSUS_LAIR, from, BossSitesTuning.MAX_DISTANCE),
                LOOKUP_THREAD);
    }

    /**
     * Runs {@code then} on the server thread once the site's lair is built: now if it's built already, otherwise as soon as
     * it's done (it's put first in the queue). Server thread.
     */
    static void whenBuilt(MinecraftServer server, Site site, Consumer<ColossusLairData.Entry> then)
    {
        long id = site.patch().id();
        ColossusLairData.Entry built = ColossusLairData.get(server).lair(id);
        if (built != null)
        {
            then.accept(built);
            return;
        }
        WAITING.computeIfAbsent(id, k -> new ArrayList<>()).add(then);
        FAILED.remove(id);
        if (!isBuilding(id))
        {
            // to the front of the queue: someone is waiting for it
            Map<Long, Site> rest = new LinkedHashMap<>(QUEUE);
            QUEUE.clear();
            QUEUE.put(id, site);
            rest.forEach(QUEUE::putIfAbsent);
        }
    }

    /** True while the lair of this patch is waiting, loading or being built. Server thread. */
    static boolean isPending(long id)
    {
        return QUEUE.containsKey(id) || isBuilding(id);
    }

    private static boolean isBuilding(long id)
    {
        for (Job job : JOBS)
        {
            if (job.id == id)
            {
                return true;
            }
        }
        return false;
    }

    /** True while any lair is waiting, loading or being built (tests). */
    static boolean isBusy()
    {
        return !QUEUE.isEmpty() || !JOBS.isEmpty();
    }

    // ---------------------------------------------------------------- who's near which site

    private static void lookNear(MinecraftServer server, ServerLevel dross)
    {
        CompletableFuture<List<Site>> lookup = nearLookup;
        if (lookup != null)
        {
            if (!lookup.isDone())
            {
                return;
            }
            nearLookup = null;
            List<Site> near;
            try
            {
                near = lookup.join();
            }
            catch (CompletionException e)
            {
                LOGGER.error("Colossus lairs: asking the planner which lairs are near the players failed.", e.getCause());
                return;
            }
            ColossusLairData data = ColossusLairData.get(server);
            for (Site site : near)
            {
                long id = site.patch().id();
                if (!data.isBuilt(id) && !isPending(id) && !FAILED.contains(id))
                {
                    LOGGER.info("Colossus lairs: a player is within {} blocks of the lair site of Cinder Wastes group {} (patch {}) at {} {}; "
                            + "building it.", BUILD_RANGE, site.patch().group() + 1, site.patch().number(), site.pos().getX(), site.pos().getZ());
                    QUEUE.put(id, site);
                }
            }
            return;
        }
        if (server.getTickCount() % NEAR_CHECK_TICKS != 0 || dross.players().isEmpty())
        {
            return;
        }
        List<BlockPos> where = new ArrayList<>();
        for (ServerPlayer player : dross.players())
        {
            where.add(player.blockPosition());
        }
        nearLookup = CompletableFuture.supplyAsync(() -> {
            BossSites sites = BossSites.forLevel(dross);
            Map<Long, Site> found = new LinkedHashMap<>();
            for (BlockPos pos : where)
            {
                for (Site site : sites.sitesNear(DrossBiomes.CINDER_WASTES, pos.getX(), pos.getZ(), BUILD_RANGE))
                {
                    if (site.use() == BossSites.COLOSSUS_LAIR)
                    {
                        found.putIfAbsent(site.patch().id(), site);
                    }
                }
            }
            return new ArrayList<>(found.values());
        }, LOOKUP_THREAD);
    }

    // ---------------------------------------------------------------- the builds

    private static void startJobs(MinecraftServer server, ServerLevel dross)
    {
        Iterator<Map.Entry<Long, Site>> it = QUEUE.entrySet().iterator();
        while (JOBS.size() < MAX_BUILDS_AT_ONCE && it.hasNext())
        {
            Site site = it.next().getValue();
            it.remove();
            if (ColossusLairData.get(server).isBuilt(site.patch().id()))
            {
                continue;
            }
            JOBS.add(new Job(dross, site));
        }
    }

    private static void stepJobs(MinecraftServer server, ServerLevel dross)
    {
        Iterator<Job> it = JOBS.iterator();
        while (it.hasNext())
        {
            Job job = it.next();
            boolean finished;
            try
            {
                finished = job.step(server);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Colossus lairs: building the lair at {} {} failed; it's tried again the next time the world starts.", job.x, job.z, e);
                job.releaseTickets();
                FAILED.add(job.id);
                WAITING.remove(job.id);
                finished = true;
            }
            if (finished)
            {
                it.remove();
            }
        }
    }

    /** One lair: its chunks loading, then built a step a tick. */
    private static final class Job
    {
        final ServerLevel dross;
        final Site site;
        final long id;
        final int x;
        final int z;
        final ChunkArea area;
        final long ticketKey;
        final long requestedAt = Util.getMillis();
        long loadMillis = -1L;
        /** The generator's heights round the spot, worked out on the background thread while the chunks load. */
        final CompletableFuture<Map<Long, Integer>> noise;
        boolean ready;
        @Nullable
        LairBuilder.Build build;
        boolean holding;

        Job(ServerLevel dross, Site site)
        {
            this.dross = dross;
            this.site = site;
            this.id = site.patch().id();
            this.x = site.pos().getX();
            this.z = site.pos().getZ();
            this.area = ChunkArea.round(x, z, LOAD_RADIUS);
            this.ticketKey = nextTicketKey++;
            area.addTickets(dross, ticketKey);
            noise = CompletableFuture.supplyAsync(() -> LairBuilder.prefetchNoise(dross, x, z), LOOKUP_THREAD);
            LOGGER.info("Colossus lairs: loading {} chunks round {} {} in the background to build the lair of Cinder Wastes group {}.", area.count(),
                    x, z, site.patch().group() + 1);
        }

        /** One tick of work; true once it's finished. */
        boolean step(MinecraftServer server)
        {
            if (build == null)
            {
                if (!ready)
                {
                    boolean all = area.allLoaded(dross);
                    long waited = Util.getMillis() - requestedAt;
                    if ((!all || !noise.isDone()) && waited < LOAD_TIMEOUT_MILLIS)
                    {
                        return false;
                    }
                    loadMillis = waited;
                    if (!all)
                    {
                        LOGGER.warn("Colossus lairs: the chunks round {} {} still weren't all loaded after {} s; building anyway.", x, z, waited / 1000);
                    }
                    ready = true; // planned on the next tick, never in the same one as the check
                    return false;
                }
                Map<Long, Integer> heights = null;
                if (noise.isDone() && !noise.isCompletedExceptionally())
                {
                    heights = noise.join();
                }
                else
                {
                    LOGGER.warn("Colossus lairs: the generator's heights round {} {} weren't ready; planning reads them itself.", x, z);
                }
                build = LairBuilder.start(dross, x, z, heights);
                return false;
            }
            if (!build.step())
            {
                return false;
            }
            finish(server);
            return true;
        }

        /** The lair is built: saved, known to the keeper, its chunks held a moment, and whoever waited told. */
        void finish(MinecraftServer server)
        {
            LairBuilder.Result result = build.result();
            LairBuilder.Layout layout = result.layout();
            ColossusLairData data = ColossusLairData.get(server);
            ColossusLairData.Entry entry = data.markBuilt(id, site.patch().group(), x, z, layout, result.millis());
            LairKeeper.refresh(data);
            LOGGER.info("Colossus lairs: built the volcano of Cinder Wastes group {} (patch {}) at {} {} in {} ms of work over {} ticks (the longest "
                            + "tick's share {} ms; chunk loading {} s): {}; cave mouth {} blocks out, gully {} blocks, way in: {}, entrance {} {} {}; "
                            + "{} blocks changed, {} tree(s) removed, {} block(s) sealed against fluids, {} stalactite(s), {} fissure block(s) of "
                            + "lava under the floor, {} lava rivulet(s) with {} lava block(s) outside.", site.patch().group() + 1, site.patch().number(),
                    x, z, result.millis(), BUILD_STEPS + 1, build.longestStep(), loadMillis < 0 ? "?" : Math.round(loadMillis / 1000.0D),
                    result.plan().describe(), layout.mouth(), result.gullyLength(), result.approach().describe(), Math.round(layout.entrance().x),
                    Math.round(layout.entrance().y), Math.round(layout.entrance().z), result.changed(), result.trees(), result.sealed(),
                    result.stalactites(), result.fissureCells(), result.rivulets(), result.outsideLava());
            if (!result.approach().meetsLand())
            {
                LOGGER.warn("Colossus lairs: the land in front of the volcano at {} {} is too steep for the way in to meet it within {} blocks "
                        + "of the apron in any direction: the last step onto the land is {} blocks (the way in itself never steps more than 1).",
                        x, z, BLEND + APPROACH_MAX_EXTRA, result.approach().seam());
            }
            if (!holding)
            {
                holding = true;
                HOLDS.add(new Hold(dross, area, ticketKey, server.getTickCount() + HOLD_AFTER_BUILD_TICKS));
            }
            List<Consumer<ColossusLairData.Entry>> waiting = WAITING.remove(id);
            if (waiting != null)
            {
                for (Consumer<ColossusLairData.Entry> then : waiting)
                {
                    try
                    {
                        then.accept(entry);
                    }
                    catch (RuntimeException e)
                    {
                        LOGGER.error("Colossus lairs: telling someone the lair at {} {} is built failed.", x, z, e);
                    }
                }
            }
        }

        void releaseTickets()
        {
            if (!holding)
            {
                area.removeTickets(dross, ticketKey);
            }
        }
    }

    private static void releaseHolds(long now)
    {
        Iterator<Hold> it = HOLDS.iterator();
        while (it.hasNext())
        {
            Hold hold = it.next();
            if (now >= hold.releaseAt())
            {
                hold.chunks().removeTickets(hold.level(), hold.key());
                it.remove();
            }
        }
    }

    /** Chunk tickets to remove at {@code releaseAt}. */
    private record Hold(ServerLevel level, ChunkArea chunks, long key, long releaseAt) {}

    /** A square of chunks, inclusive. */
    private record ChunkArea(int minX, int minZ, int maxX, int maxZ)
    {
        static ChunkArea round(int x, int z, int radius)
        {
            return new ChunkArea(SectionPos.blockToSectionCoord(x - radius), SectionPos.blockToSectionCoord(z - radius),
                    SectionPos.blockToSectionCoord(x + radius), SectionPos.blockToSectionCoord(z + radius));
        }

        int count()
        {
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }

        void addTickets(ServerLevel level, long key)
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    level.getChunkSource().addRegionTicket(LOAD_TICKET, new ChunkPos(x, z), 0, key);
                }
            }
        }

        void removeTickets(ServerLevel level, long key)
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    level.getChunkSource().removeRegionTicket(LOAD_TICKET, new ChunkPos(x, z), 0, key);
                }
            }
        }

        /** True once every chunk is fully loaded. Server thread. */
        boolean allLoaded(ServerLevel level)
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    if (level.getChunkSource().getChunkNow(x, z) == null)
                    {
                        return false;
                    }
                }
            }
            return true;
        }
    }
}
