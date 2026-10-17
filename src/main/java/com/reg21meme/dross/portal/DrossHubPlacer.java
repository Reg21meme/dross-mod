package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Unit;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Builds the hub ({@link DrossHubBuilder}) in the background after a new world starts, so creating the world never
 * waits for it and no server tick does much of it:
 * <ol>
 *   <li><b>Load</b>: when the world has started, the hub's chunks (its footprint, the ground round it and a bit more:
 *       {@link DrossHubBuilder.Build#loadArea}) get chunk tickets, so the world generator's threads make them while the
 *       game runs. They're loaded but not ticking: nothing grows, decays or spawns in them meanwhile.</li>
 *   <li><b>Build</b> a step a tick ({@link DrossHubBuilder.Build}: survey, plan, then the spire in short steps (felling
 *       the trees in the way, levelling, the template...) and its frame lit), then save it ({@link DrossHubData}) and keep
 *       its chunks loaded {@value #HOLD_AFTER_BUILD_TICKS} more ticks for their lighting.</li>
 * </ol>
 * Anything that needs the hub before it's finished ({@link DrossHub#ensureBuilt}: an arrival through a portal,
 * {@code /dross goto hub}, {@link DrossHub#layout}) has the rest done at once ({@link #finishNow}): the surveyed area's
 * chunks still missing are loaded (in parallel, as fast as the world generator can) and the remaining steps run in that
 * tick, as before this was spread out. Nobody ever gets a half-built hub. {@link DrossHub#whenBuilt} waits instead.
 * <p>
 * Only the build steps change the world, from the first tree removed on. If the server stops before that (the hub still
 * loading, being planned, or its trees being worked out), nothing has changed yet: it's simply built the next time the
 * world starts. If it stops after that, the rest is finished at once before the world is saved, so it's never left half
 * built or built again on top. It's never built twice: the saved {@link DrossHubData#isBuilt} flag is checked first, and
 * there's only ever one build at a time.
 * <p>
 * The log says how long each step took, the longest one tick's share, and how long the chunks took to load.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
final class DrossHubPlacer
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Keeps the hub's chunks loaded (not ticking) while it's built. */
    private static final TicketType<Unit> LOAD_TICKET = TicketType.create("dross_hub", (a, b) -> 0);
    /** Stop waiting for the hub's chunks after this long and build anyway (the build then loads what's missing itself). */
    static final long LOAD_TIMEOUT_MILLIS = 120_000L;
    /** The hub's chunks stay loaded this many ticks (10 seconds) after it's built, so their lighting settles. */
    static final int HOLD_AFTER_BUILD_TICKS = 200;

    /** The build in progress, if any. Server thread only. */
    @Nullable
    private static Job job;
    /** Who's waiting for the hub ({@link DrossHub#whenBuilt}). */
    private static final List<Consumer<Optional<DrossHub.Layout>>> WAITING = new ArrayList<>();
    /** The hub was finished at once in the middle of something else: tell whoever's waiting at the end of the next tick. */
    private static boolean notifyNextTick;
    /** True while a build step runs: something it sets off that asks for the hub gets "not yet" instead of a second build. */
    private static boolean working;
    /** The finished hub's chunks, kept loaded a moment longer. */
    @Nullable
    private static Hold hold;

    private DrossHubPlacer() {}

    @SubscribeEvent
    public static void onServerAboutToStart(ServerAboutToStartEvent event)
    {
        reset();
    }

    /**
     * Starts building the hub in the background the first time (a new world); an existing hub's layout is logged. High
     * priority, so its chunks are queued before the other start-up jobs' (the trader's villages, the portal site).
     */
    @SubscribeEvent(priority = EventPriority.HIGH)
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
            if (DrossHubData.get(server).isBuilt())
            {
                logLayout(server);
            }
            else
            {
                start(dross);
            }
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Dross hub: getting the hub ready at server start failed.", e);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        MinecraftServer server = event.getServer();
        releaseHold(server.getTickCount());
        if (notifyNextTick)
        {
            notifyNextTick = false;
            notifyWaiting(server);
        }
        Job current = job;
        if (current == null || working)
        {
            return;
        }
        boolean finished;
        working = true;
        try
        {
            finished = current.step();
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Dross hub: building the hub in the background failed; it's tried again when something needs it, or the next time "
                    + "the world starts.", e);
            drop(current);
            return;
        }
        finally
        {
            working = false;
        }
        if (finished)
        {
            finish(server, current, null, 0L);
            notifyWaiting(server);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        MinecraftServer server = event.getServer();
        Job current = job;
        if (current != null && current.build.changedWorld())
        {
            // Half built: the rest is done now (the world is still there, and saved after this), so it's never built again
            // on top of a half-felled forest. Whoever waits is told the next time the world starts.
            LOGGER.info("Dross hub: the server is stopping while the hub is half built; finishing it now.");
            long started = Util.getMillis();
            working = true;
            try
            {
                current.finishNow();
                finish(server, current, "the stopping server", Util.getMillis() - started);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Dross hub: finishing the half-built hub as the server stopped failed.", e);
            }
            finally
            {
                working = false;
            }
        }
        else if (current != null)
        {
            // Still loading, surveying, planning or working out which trees go: nothing in the world has changed yet.
            LOGGER.info("Dross hub: the server is stopping before the hub was built (nothing in the world has changed yet); it's built the "
                    + "next time the world starts.");
        }
        if (notifyNextTick)
        {
            notifyWaiting(server); // finished at once this tick; whoever waited still hears about it
        }
        reset();
    }

    private static void reset()
    {
        job = null;
        WAITING.clear();
        notifyNextTick = false;
        working = false;
        hold = null; // chunk tickets go away with the server
    }

    // ---------------------------------------------------------------- DrossHub's side

    /** See {@link DrossHub#whenBuilt}. */
    static void whenBuilt(MinecraftServer server, Consumer<Optional<DrossHub.Layout>> then)
    {
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            then.accept(Optional.empty());
            return;
        }
        if (DrossHubData.get(server).isBuilt())
        {
            then.accept(DrossHub.layout(server));
            return;
        }
        WAITING.add(then);
        start(dross);
    }

    /**
     * Finishes the hub right now if it isn't built yet: the chunks still missing are loaded, then the remaining steps run
     * in this tick. Whoever waits ({@link DrossHub#whenBuilt}) is told at the end of the next tick, not in the middle of
     * whatever needed the hub (a teleport, say).
     *
     * @return false only if asked from inside the build itself, or if building it failed with an error
     */
    static boolean finishNow(ServerLevel dross)
    {
        if (working)
        {
            return false;
        }
        MinecraftServer server = dross.getServer();
        if (DrossHubData.get(server).isBuilt())
        {
            return true;
        }
        String forcedBy = caller();
        long started = Util.getMillis();
        Job current;
        working = true;
        try
        {
            current = start(dross);
            current.finishNow();
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Dross hub: finishing the hub at once failed.", e);
            if (job != null)
            {
                drop(job);
            }
            return false;
        }
        finally
        {
            working = false;
        }
        finish(server, current, forcedBy, Util.getMillis() - started);
        notifyNextTick = true;
        return true;
    }

    // ---------------------------------------------------------------- the build

    /** The build in progress, or a new one (its chunks start loading). */
    private static Job start(ServerLevel dross)
    {
        if (job == null)
        {
            job = new Job(dross);
        }
        return job;
    }

    /** Gives up on a build that failed with an error (nothing was saved, so it's tried again later). */
    private static void drop(Job failed)
    {
        failed.area.removeTickets(failed.dross);
        if (job == failed)
        {
            job = null;
        }
    }

    /** The hub is built: saved (with its arrival spot), logged, its chunks held a moment. */
    private static void finish(MinecraftServer server, Job done, @Nullable String forcedBy, long forcedMillis)
    {
        job = null;
        DrossHubBuilder.Result result = done.build.result();
        PortalInfo spot = result.exit() == null ? null : DrossTeleporter.arrivalSpot(done.dross, result.exit());
        DrossHubData.get(server).markBuilt(result, spot == null ? null : spot.pos, spot == null ? 0.0F : spot.yRot);

        DrossHubBuilder.Build build = done.build;
        String how = forcedBy == null
                ? String.format("spread over %d ticks, the longest one tick's share %d ms (%s); its %d chunks loaded in the background in %.1f s",
                        build.steps(), build.longestStep(), build.longestStepName(), done.area.count(), done.loadMillis / 1000.0D)
                : String.format("the rest finished at once in one tick (%d ms, %d ms of it waiting for the %d chunks round it) because %s "
                        + "needed the hub", forcedMillis, done.waitMillis, done.core.count(), forcedBy);
        LOGGER.info("Dross hub: built ({}{}) in {} ms of work ({}); {}; {} s after it started",
                result.kind() == null ? "nothing" : result.kind(), result.fitted() ? ", on fitted ground" : "", build.millis(), build.stepTimes(),
                how, String.format("%.1f", (Util.getMillis() - done.startedAt) / 1000.0D));

        hold = new Hold(done.dross, done.area, server.getTickCount() + HOLD_AFTER_BUILD_TICKS);
        logLayout(server);
    }

    private static void notifyWaiting(MinecraftServer server)
    {
        if (WAITING.isEmpty())
        {
            return;
        }
        List<Consumer<Optional<DrossHub.Layout>>> now = new ArrayList<>(WAITING);
        WAITING.clear();
        Optional<DrossHub.Layout> layout = DrossHub.layout(server);
        for (Consumer<Optional<DrossHub.Layout>> then : now)
        {
            try
            {
                then.accept(layout);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Dross hub: telling someone the hub is built failed.", e);
            }
        }
    }

    private static void logLayout(MinecraftServer server)
    {
        Optional<DrossHub.Layout> layout = DrossHub.layout(server);
        if (layout.isPresent())
        {
            LOGGER.info(DrossHub.describe(layout.get()));
        }
        else
        {
            LOGGER.warn("Dross hub: the hub has no exit portal, so it has no layout.");
        }
    }

    private static void releaseHold(int now)
    {
        if (hold != null && now >= hold.releaseAt())
        {
            hold.chunks().removeTickets(hold.level());
            hold = null;
        }
    }

    /** Who asked for the hub to be finished at once (the first caller outside the hub's own classes), for the log. */
    private static String caller()
    {
        return StackWalker.getInstance().walk(frames -> frames
                .map(StackWalker.StackFrame::getClassName)
                .filter(name -> !name.startsWith(DrossHub.class.getName()))
                .findFirst()
                .map(name -> name.substring(name.lastIndexOf('.') + 1))
                .orElse("something"));
    }

    /** One hub build: its chunks loading, then a build step a tick. */
    private static final class Job
    {
        final ServerLevel dross;
        final DrossHubBuilder.Build build;
        /** Every chunk the build may touch: these get tickets. */
        final ChunkArea area;
        /** The chunks the survey reads: a forced finish waits for these (the build loads any others it needs itself). */
        final ChunkArea core;
        final long startedAt = Util.getMillis();
        /** How long the chunks took to load in the background (-1 until they have). */
        long loadMillis = -1L;
        /** How long a forced finish waited for chunks. */
        long waitMillis;
        boolean loaded;

        Job(ServerLevel dross)
        {
            this.dross = dross;
            this.build = new DrossHubBuilder.Build(dross);
            BoundingBox columns = build.loadArea();
            this.area = ChunkArea.of(columns);
            this.core = ChunkArea.of(build.surveyArea());
            area.addTickets(dross);
            LOGGER.info("Dross hub: building the {} in the background; first loading its {} chunks (X {}..{}, Z {}..{}).",
                    build.planned(), area.count(), columns.minX(), columns.maxX(), columns.minZ(), columns.maxZ());
        }

        /** One tick of work; true once the hub is built. */
        boolean step()
        {
            if (!loaded)
            {
                long waited = Util.getMillis() - startedAt;
                boolean all = area.allLoaded(dross);
                if (!all && waited < LOAD_TIMEOUT_MILLIS)
                {
                    return false;
                }
                if (!all)
                {
                    LOGGER.warn("Dross hub: its chunks still weren't all loaded after {} s; building anyway.", waited / 1000L);
                }
                loadMillis = waited;
                loaded = true;
                return false; // the first step runs on the next tick, never in the same one as the check
            }
            return build.step();
        }

        /**
         * Everything that's left, now: the surveyed area's missing chunks (as fast as the world generator can make them;
         * the rest keep loading in the background, and the build loads any it needs), then every remaining step, one
         * after another in this tick.
         */
        void finishNow()
        {
            if (!loaded)
            {
                long waitStart = Util.getMillis();
                core.waitUntilLoaded(dross, waitStart + LOAD_TIMEOUT_MILLIS);
                waitMillis = Util.getMillis() - waitStart;
                loaded = true;
            }
            while (!build.step())
            {
                // the remaining steps, one after another
            }
        }
    }

    /** Chunk tickets to remove at {@code releaseAt}. */
    private record Hold(ServerLevel level, ChunkArea chunks, int releaseAt) {}

    /** A rectangle of chunks, inclusive. */
    private record ChunkArea(int minX, int minZ, int maxX, int maxZ)
    {
        static ChunkArea of(BoundingBox columns)
        {
            return new ChunkArea(SectionPos.blockToSectionCoord(columns.minX()), SectionPos.blockToSectionCoord(columns.minZ()),
                    SectionPos.blockToSectionCoord(columns.maxX()), SectionPos.blockToSectionCoord(columns.maxZ()));
        }

        int count()
        {
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }

        void addTickets(ServerLevel level)
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    level.getChunkSource().addRegionTicket(LOAD_TICKET, new ChunkPos(x, z), 0, Unit.INSTANCE);
                }
            }
        }

        void removeTickets(ServerLevel level)
        {
            for (int x = minX; x <= maxX; x++)
            {
                for (int z = minZ; z <= maxZ; z++)
                {
                    level.getChunkSource().removeRegionTicket(LOAD_TICKET, new ChunkPos(x, z), 0, Unit.INSTANCE);
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

        /**
         * Waits on the server thread until every chunk is loaded (or {@code deadline}), running the level's chunk work
         * meanwhile, the way Minecraft itself waits for a chunk it needs at once. The tickets are already there, so the
         * world generator makes them all in parallel.
         */
        void waitUntilLoaded(ServerLevel level, long deadline)
        {
            while (!allLoaded(level) && Util.getMillis() < deadline)
            {
                if (!level.getChunkSource().pollTask())
                {
                    LockSupport.parkNanos("waiting for the Dross hub's chunks", 100_000L);
                }
            }
        }
    }
}
