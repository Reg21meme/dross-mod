package com.reg21meme.dross.boss.lair;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
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
 * Places the Colossus's lair once per world, without freezing the game (like the portal site):
 * <ol>
 *   <li><b>Search</b> ({@link LairSearch}) on a background thread when the server has started (after the hub is
 *       built), from the world generator's noise. The spot is saved as the world's planned spot.</li>
 *   <li><b>Load</b>: the chunks round it are generated in the background (chunk tickets).</li>
 *   <li><b>Build</b> ({@link LairBuilder}) on a later tick, then the chunks stay loaded a few seconds for their lighting.</li>
 * </ol>
 * Old worlds get their lair the same way, the first time they start with it. If no direction finds a spot (it never
 * should), a warning is logged and it's tried again the next time the world starts. If the world closes before it's
 * built, it carries on from the planned spot next time.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
final class LairPlacer
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Keeps the lair's chunks loaded while it's built. */
    private static final TicketType<Long> LOAD_TICKET = TicketType.create("dross_colossus_lair", Long::compare);
    /** The search runs on its own low-priority thread, so neither the server nor world generation waits for it. */
    private static final Executor SEARCH_THREAD = task -> {
        Thread thread = new Thread(task, "Dross Colossus lair search");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 2);
        thread.start();
    };

    /** The placement in progress, or null. Server thread only. */
    @Nullable
    private static Job job;
    private static final List<Hold> HOLDS = new ArrayList<>();
    private static long nextTicketKey;

    private LairPlacer() {}

    /** Low priority: runs after the hub is built (that happens at normal priority, in portal/DrossHub). */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onServerStarted(ServerStartedEvent event)
    {
        MinecraftServer server = event.getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            return;
        }
        try
        {
            ColossusLairData data = ColossusLairData.get(server);
            LairKeeper.refresh(data);
            if (data.isPlaced())
            {
                LOGGER.info("Colossus lair: this world's lair is at {} {} ({}, floor y {}, way in to the {}, entrance {} {} {}).",
                        data.centerX(), data.centerZ(), data.version() >= 2 ? "a volcano, rim y " + data.rimY()
                                : "an R2c cavern, kept as it was built", data.floorY(), data.tunnel().getName(), Math.round(data.entrance().x),
                        Math.round(data.entrance().y), Math.round(data.entrance().z));
                return;
            }
            job = new Job(server, dross, data);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Colossus lair: starting to place the lair failed; it's tried again the next time the world starts.", e);
            job = null;
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || (job == null && HOLDS.isEmpty()))
        {
            return;
        }
        releaseHolds(event.getServer().getTickCount());
        Job j = job;
        if (j == null)
        {
            return;
        }
        try
        {
            j.step();
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Colossus lair: placing the lair failed; it's tried again the next time the world starts.", e);
            j.releaseTickets();
            job = null;
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        Job j = job;
        if (j != null)
        {
            j.cancelled.set(true);
            LOGGER.info("Colossus lair: the server stopped before the lair was built; it carries on next time.");
        }
        job = null;
        HOLDS.clear(); // chunk tickets go away with the server
        LairKeeper.clear();
    }

    /** True while a placement is running (tests). */
    static boolean isRunning()
    {
        return job != null;
    }

    // ---------------------------------------------------------------- one placement

    private static final class Job
    {
        final MinecraftServer server;
        final ServerLevel dross;
        final ColossusLairData data;
        final long startedAt = Util.getMillis();
        final AtomicBoolean cancelled = new AtomicBoolean();
        @Nullable
        CompletableFuture<LairSearch.Result> search;
        int x;
        int z;
        @Nullable
        ChunkArea area;
        long ticketKey;
        long requestedAt;
        boolean loading;
        boolean ready;
        long searchMillis = -1L;
        long loadMillis = -1L;

        Job(MinecraftServer server, ServerLevel dross, ColossusLairData data)
        {
            this.server = server;
            this.dross = dross;
            this.data = data;
            if (data.isPlanned())
            {
                x = data.plannedX();
                z = data.plannedZ();
                LOGGER.info("Colossus lair: carrying on with the planned spot {} {}.", x, z);
                startLoading();
                return;
            }
            LairSearch.Context context = LairSearch.forWorld(dross);
            double[] angles = LairSearch.directionAngles(context.seed());
            StringBuilder directions = new StringBuilder();
            for (double angle : angles)
            {
                directions.append(directions.length() > 0 ? ", " : "").append(Math.round(LairSearch.normalise(angle)))
                        .append(" degrees (").append(LairSearch.compass(angle)).append(")");
            }
            LOGGER.info("Colossus lair: looking for the Cinder Wastes in three directions from the hub: {} (turned {} degrees by the "
                            + "seed; cones of +-{} degrees, {} to {} blocks out). The furthest of the three nearest spots is used.",
                    directions, Math.round(LairSearch.rotation(context.seed())), Math.round(CONE_HALF_ANGLE_DEGREES), MIN_DISTANCE,
                    MAX_DISTANCE);
            search = CompletableFuture.supplyAsync(() -> LairSearch.run(context, cancelled::get), SEARCH_THREAD);
        }

        void step()
        {
            if (search != null)
            {
                if (!search.isDone())
                {
                    return;
                }
                LairSearch.Result result;
                try
                {
                    result = search.join();
                }
                catch (CompletionException e)
                {
                    search = null;
                    throw new IllegalStateException("the lair search failed", e.getCause());
                }
                search = null;
                onSearch(result);
                return;
            }
            if (loading)
            {
                boolean all = area.allLoaded(dross);
                long waited = Util.getMillis() - requestedAt;
                if (!all && waited < LOAD_TIMEOUT_MILLIS)
                {
                    return;
                }
                loadMillis = waited;
                if (all)
                {
                    LOGGER.info("Colossus lair: the {} chunks round {} {} were loaded in {} s.", area.count(), x, z,
                            Math.round(waited / 1000.0D));
                }
                else
                {
                    LOGGER.warn("Colossus lair: the chunks round {} {} still weren't all loaded after {} s; building anyway.", x, z,
                            waited / 1000);
                }
                loading = false;
                ready = true; // built on the next tick, never in the same one as the check
                return;
            }
            if (ready)
            {
                ready = false;
                build();
            }
        }

        private void onSearch(LairSearch.Result result)
        {
            searchMillis = result.millis();
            if (result.cancelled())
            {
                job = null;
                return;
            }
            for (int i = 0; i < result.directions().size(); i++)
            {
                Optional<LairSearch.Candidate> found = result.directions().get(i);
                double angle = LairSearch.directionAngles(dross.getSeed())[i];
                if (found.isPresent())
                {
                    LOGGER.info("Colossus lair:   direction {} ({} degrees, {}): {}", i + 1, Math.round(LairSearch.normalise(angle)),
                            LairSearch.compass(angle), found.get().describe());
                }
                else
                {
                    LOGGER.info("Colossus lair:   direction {} ({} degrees, {}): no Cinder Wastes big enough within {} blocks.", i + 1,
                            Math.round(LairSearch.normalise(angle)), LairSearch.compass(angle), MAX_DISTANCE);
                }
            }
            if (result.pick().isEmpty())
            {
                data.noteFailedSearch();
                LOGGER.warn("Colossus lair: no direction found a Cinder Wastes spot that can hold the lair within {} blocks of the hub "
                        + "(search took {} ms); it's tried again the next time the world starts ({} failed search(es) so far).",
                        MAX_DISTANCE, result.millis(), data.failedSearches());
                job = null;
                return;
            }
            LairSearch.Candidate pick = result.pick().get();
            x = pick.x();
            z = pick.z();
            LOGGER.info("Colossus lair: picked direction {}: {} {}, {} blocks from the hub (the furthest of the three; search took {} ms).",
                    pick.direction() + 1, x, z, Math.round(pick.distance()), result.millis());
            data.setPlanned(x, z);
            startLoading();
        }

        private void startLoading()
        {
            area = ChunkArea.round(x, z, LOAD_RADIUS);
            ticketKey = nextTicketKey++;
            area.addTickets(dross, ticketKey);
            requestedAt = Util.getMillis();
            loading = true;
            LOGGER.info("Colossus lair: loading {} chunks round {} {} in the background to build it.", area.count(), x, z);
        }

        private void build()
        {
            LairBuilder.Result result = LairBuilder.build(dross, x, z);
            LairBuilder.Layout layout = result.layout();
            data.markPlaced(layout, result.millis());
            LairKeeper.refresh(data);
            LOGGER.info("Colossus lair: built a volcano at {} {} in {} ms: {}; cave mouth {} blocks out, gully {} blocks, entrance {} {} {}; "
                            + "{} blocks changed, {} tree(s) removed, {} plant(s) without ground removed, {} block(s) sealed against "
                            + "fluids, {} magma vein(s), {} stalactite(s), {} fissure block(s) of lava under the floor, {} lava "
                            + "rivulet(s) with {} lava block(s) outside, {} burnable block(s) cleared. Search {} ms, chunk loading {} s, "
                            + "{} s after the server started.",
                    layout.centerX(), layout.centerZ(), result.millis(), result.plan().describe(), layout.mouth(), result.gullyLength(),
                    Math.round(layout.entrance().x), Math.round(layout.entrance().y), Math.round(layout.entrance().z),
                    result.changed(), result.trees(), result.plants(), result.sealed(), result.veins(), result.stalactites(),
                    result.fissureCells(), result.rivulets(), result.outsideLava(), result.fireGuarded(), searchMillis,
                    loadMillis < 0 ? "?" : Math.round(loadMillis / 1000.0D), Math.round((Util.getMillis() - startedAt) / 1000.0D));
            if (area != null)
            {
                HOLDS.add(new Hold(dross, area, ticketKey, server.getTickCount() + HOLD_AFTER_BUILD_TICKS));
                area = null;
            }
            if (job == this)
            {
                job = null;
            }
        }

        void releaseTickets()
        {
            if (area != null)
            {
                area.removeTickets(dross, ticketKey);
                area = null;
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
