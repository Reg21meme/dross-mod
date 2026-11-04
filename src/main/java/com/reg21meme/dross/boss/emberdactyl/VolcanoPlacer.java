package com.reg21meme.dross.boss.emberdactyl;

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
import java.util.function.IntBinaryOperator;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkGenerator;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.RandomState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import static com.reg21meme.dross.boss.emberdactyl.EmberVolcanoTuning.*;

/**
 * Builds the Emberdactyl's volcanoes lazily, one per group of 3 Cinder Wastes patches (the boss-site planner's
 * {@link BossSites#EMBER_VOLCANO} sites, chosen by {@link VolcanoFinder}), the way the Colossus's lairs are built, without
 * ever freezing the game:
 * <ol>
 *   <li><b>Who's near what</b>: every {@link EmberVolcanoTuning#NEAR_CHECK_TICKS} ticks the Dross players' positions go to a
 *       background thread, which asks the planner for the volcano sites within {@link EmberVolcanoTuning#BUILD_RANGE}.</li>
 *   <li><b>Load and plan</b>: a site with no volcano yet is queued; its chunks are generated with chunk tickets while the
 *       background thread plans it on the generator's own land ({@link EmberVolcano#planOnLand}).</li>
 *   <li><b>Build</b> ({@link EmberVolcano.Build}) a piece at a time, at most {@link EmberVolcanoTuning#STEP_MILLIS} ms of
 *       work a tick (the lava on a tick of its own); then it's saved with its patch ({@link EmberVolcanoData}), so each is
 *       built once and nobody is sent to a half-built one.</li>
 * </ol>
 * {@code /dross goto lair emberdactyl} asks for the nearest site and waits for its volcano here ({@link #whenBuilt}). A
 * volcano half built when the server stops is finished at once; one still loading is built the next time someone comes.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
final class VolcanoPlacer
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Keeps a volcano's chunks loaded while they generate. */
    private static final TicketType<Long> LOAD_TICKET = TicketType.create("dross_ember_volcano_site", Long::compare);
    /** The planner is asked (and volcanoes planned) on this one low-priority thread. */
    private static final ExecutorService LOOKUP_THREAD = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Dross Emberdactyl volcanoes");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 2);
        return thread;
    });

    /** The volcano sites waiting to be built (by their patch's id), first come first served. Server thread only. */
    private static final Map<Long, Site> QUEUE = new LinkedHashMap<>();
    private static final List<Job> JOBS = new ArrayList<>();
    /** Who's waiting for a volcano to be built (by its patch's id): {@code goto lair emberdactyl}. */
    private static final Map<Long, List<Consumer<EmberVolcanoData.Volcano>>> WAITING = new LinkedHashMap<>();
    /** Sites whose build failed this run (not tried again until the next start). */
    private static final Set<Long> FAILED = new HashSet<>();
    private static final List<Hold> HOLDS = new ArrayList<>();
    @Nullable
    private static CompletableFuture<List<Site>> nearLookup;
    private static long nextTicketKey;

    private VolcanoPlacer() {}

    /** Low priority: after the hub. Works out the first groups' sites in the background and logs them. */
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
        LOGGER.info("Emberdactyl volcanoes: {} built in this world so far; the others are built when a player first comes within {} blocks.",
                EmberVolcanoData.get(server).all().size(), BUILD_RANGE);
        CompletableFuture.runAsync(() -> {
            long started = Util.getMillis();
            BossSites sites = BossSites.forLevel(dross);
            StringBuilder found = new StringBuilder();
            for (int g = 0; g < WARM_UP_GROUPS; g++)
            {
                Optional<Group> group = sites.group(DrossBiomes.CINDER_WASTES, g);
                Optional<Site> site = group.flatMap(Group::secondSite);
                found.append(String.format("%n  group %d: %s", g + 1, site.map(s -> "patch " + s.patch().number() + " at " + s.pos().getX() + " "
                        + s.pos().getZ() + ", " + Math.round(s.distanceTo(0, 0)) + " blocks from the hub" + (s.relaxed() ? " (its smallest form)" : ""))
                        .orElse(group.isPresent() ? "none (no free patch of it can hold a volcano)" : "none (past the planner's reach)")));
            }
            LOGGER.info("Emberdactyl volcanoes: the first {} group(s) of Cinder Wastes patches hold their volcanoes at ({} ms):{}", WARM_UP_GROUPS,
                    Util.getMillis() - started, found);
        }, LOOKUP_THREAD).exceptionally(e -> {
            LOGGER.error("Emberdactyl volcanoes: working out the first groups' sites failed.", e);
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
            stepJobs(server);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Emberdactyl volcanoes: building the volcanoes failed.", e);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        MinecraftServer server = event.getServer();
        for (Job job : new ArrayList<>(JOBS))
        {
            if (job.build != null && !job.build.isDone())
            {
                LOGGER.info("Emberdactyl volcanoes: the server is stopping while the volcano at {} {} is half built; finishing it now.", job.x, job.z);
                try
                {
                    while (!job.build.step(Long.MAX_VALUE))
                    {
                        // the rest at once
                    }
                    job.finish(server);
                }
                catch (RuntimeException e)
                {
                    LOGGER.error("Emberdactyl volcanoes: finishing the volcano at {} {} failed.", job.x, job.z, e);
                }
            }
        }
        resetState();
    }

    private static void resetState()
    {
        QUEUE.clear();
        JOBS.clear();
        WAITING.clear();
        FAILED.clear();
        HOLDS.clear(); // chunk tickets go away with the server
        nearLookup = null;
    }

    // ---------------------------------------------------------------- the API's side (EmberVolcanoes)

    /** The planned volcano site nearest {@code from} (along the ground), worked out on the background thread. */
    static CompletableFuture<Optional<Site>> nearestSite(ServerLevel dross, net.minecraft.core.BlockPos from)
    {
        return CompletableFuture.supplyAsync(() -> BossSites.forLevel(dross).nearest(BossSites.EMBER_VOLCANO, from, BossSitesTuning.MAX_DISTANCE),
                LOOKUP_THREAD);
    }

    /** Runs {@code then} on the server thread once the site's volcano is built (now if it is; otherwise it's put first). */
    static void whenBuilt(MinecraftServer server, Site site, Consumer<EmberVolcanoData.Volcano> then)
    {
        long id = site.patch().id();
        EmberVolcanoData.Volcano built = EmberVolcanoData.get(server).volcano(id);
        if (built != null)
        {
            then.accept(built);
            return;
        }
        WAITING.computeIfAbsent(id, k -> new ArrayList<>()).add(then);
        FAILED.remove(id);
        if (!isBuilding(id))
        {
            Map<Long, Site> rest = new LinkedHashMap<>(QUEUE);
            QUEUE.clear();
            QUEUE.put(id, site);
            rest.forEach(QUEUE::putIfAbsent);
        }
    }

    /** True while the volcano of this patch is waiting, loading or being built. Server thread. */
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

    /** The generator's own land (a column's top ground block before trees, rocks and lava pools): any thread. */
    static IntBinaryOperator generatorLand(ServerLevel level)
    {
        ChunkGenerator generator = level.getChunkSource().getGenerator();
        RandomState random = level.getChunkSource().randomState();
        return (x, z) -> generator.getBaseHeight(x, z, Heightmap.Types.OCEAN_FLOOR_WG, level, random) - 1;
    }

    /** The seed a site's volcano is shaped from (fixed by the world and its patch). */
    static long shapeSeed(ServerLevel level, Site site)
    {
        return level.getSeed() * 0x9E3779B97F4A7C15L ^ site.patch().id() ^ 0x456D626572566F6CL;
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
                LOGGER.error("Emberdactyl volcanoes: asking the planner which volcanoes are near the players failed.", e.getCause());
                return;
            }
            EmberVolcanoData data = EmberVolcanoData.get(server);
            for (Site site : near)
            {
                long id = site.patch().id();
                if (!data.isBuilt(id) && !isPending(id) && !FAILED.contains(id))
                {
                    LOGGER.info("Emberdactyl volcanoes: a player is within {} blocks of the volcano site of Cinder Wastes group {} (patch {}) at {} {}; "
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
        List<net.minecraft.core.BlockPos> where = new ArrayList<>();
        for (ServerPlayer player : dross.players())
        {
            where.add(player.blockPosition());
        }
        nearLookup = CompletableFuture.supplyAsync(() -> {
            BossSites sites = BossSites.forLevel(dross);
            Map<Long, Site> found = new LinkedHashMap<>();
            for (net.minecraft.core.BlockPos pos : where)
            {
                for (Site site : sites.sitesNear(DrossBiomes.CINDER_WASTES, pos.getX(), pos.getZ(), BUILD_RANGE))
                {
                    if (site.use() == BossSites.EMBER_VOLCANO)
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
            if (EmberVolcanoData.get(server).isBuilt(site.patch().id()))
            {
                continue;
            }
            JOBS.add(new Job(dross, site));
        }
    }

    private static void stepJobs(MinecraftServer server)
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
                LOGGER.error("Emberdactyl volcanoes: building the volcano at {} {} failed; it's tried again the next time the world starts.",
                        job.x, job.z, e);
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

    /** One volcano: its chunks loading while it's planned, then built a piece a tick. */
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
        /** When it next says it's still waiting for its plan (once a minute, not every tick). */
        long nextWaitNote = LOAD_TIMEOUT_MILLIS;
        final CompletableFuture<EmberVolcano.Plan> plan;
        @Nullable
        EmberVolcano.Build build;
        boolean holding;

        Job(ServerLevel dross, Site site)
        {
            this.dross = dross;
            this.site = site;
            this.id = site.patch().id();
            this.x = site.pos().getX();
            this.z = site.pos().getZ();
            this.area = ChunkArea.round(x, z, EmberVolcano.LAND_REACH);
            this.ticketKey = nextTicketKey++;
            area.addTickets(dross, ticketKey);
            long seed = shapeSeed(dross, site);
            IntBinaryOperator land = generatorLand(dross);
            this.plan = CompletableFuture.supplyAsync(() -> EmberVolcano.planOnLand(x, z, land, seed), LOOKUP_THREAD);
            LOGGER.info("Emberdactyl volcanoes: loading {} chunks round {} {} in the background to build the volcano of Cinder Wastes group {}.",
                    area.count(), x, z, site.patch().group() + 1);
        }

        /** One tick of work; true once it's finished. */
        boolean step(MinecraftServer server)
        {
            if (build == null)
            {
                boolean all = area.allLoaded(dross);
                long waited = Util.getMillis() - requestedAt;
                if ((!all || !plan.isDone()) && waited < LOAD_TIMEOUT_MILLIS)
                {
                    return false;
                }
                if (!plan.isDone())
                {
                    if (waited >= nextWaitNote)
                    {
                        LOGGER.warn("Emberdactyl volcanoes: planning the volcano at {} {} still wasn't done after {} s; it waits on.", x, z, waited / 1000);
                        nextWaitNote = waited + WAIT_NOTE_MILLIS;
                    }
                    return false;
                }
                if (!all)
                {
                    LOGGER.warn("Emberdactyl volcanoes: the chunks round {} {} still weren't all loaded after {} s; building anyway.", x, z, waited / 1000);
                }
                loadMillis = waited;
                build = plan.join().start(dross); // (its own tickets hold its chunks from here on)
                return false;
            }
            if (!build.step(STEP_MILLIS * 1_000_000L))
            {
                return false;
            }
            finish(server);
            return true;
        }

        /** The volcano is built: saved, its chunks held a moment for their lighting, and whoever waited told. */
        void finish(MinecraftServer server)
        {
            EmberVolcano.Plan built = build.plan();
            EmberVolcanoData.Volcano volcano = EmberVolcanoData.get(server).markBuilt(id, site.patch().group(), built,
                    build.workMillis());
            LOGGER.info("Emberdactyl volcanoes: built the volcano of Cinder Wastes group {} (patch {}) at {} {} on ground {} (chunk loading {} s); "
                            + "its egg at {} {} {}, its path's foot at {} {} {}.", site.patch().group() + 1, site.patch().number(), x, z,
                    built.ground().getY(), loadMillis < 0 ? "?" : Math.round(loadMillis / 1000.0D), Math.round(volcano.egg().x),
                    Math.round(volcano.egg().y), Math.round(volcano.egg().z), Math.round(volcano.pathFoot().x), Math.round(volcano.pathFoot().y),
                    Math.round(volcano.pathFoot().z));
            if (!holding)
            {
                holding = true;
                HOLDS.add(new Hold(dross, area, ticketKey, server.getTickCount() + HOLD_AFTER_BUILD_TICKS));
            }
            List<Consumer<EmberVolcanoData.Volcano>> waiting = WAITING.remove(id);
            if (waiting != null)
            {
                for (Consumer<EmberVolcanoData.Volcano> then : waiting)
                {
                    try
                    {
                        then.accept(volcano);
                    }
                    catch (RuntimeException e)
                    {
                        LOGGER.error("Emberdactyl volcanoes: telling someone the volcano at {} {} is built failed.", x, z, e);
                    }
                }
            }
        }

        void releaseTickets()
        {
            if (build != null)
            {
                build.abandon();
            }
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
