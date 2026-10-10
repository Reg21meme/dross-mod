package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.world.shrine.GroundFit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Picks the spot for the portal site in a new world and builds it there, without freezing the game.
 * <ol>
 *   <li><b>Search</b> ({@link SiteSearch}) on a background thread, with the world generator's noise: random spots 3,000
 *       to 10,000 blocks from spawn that look right (biome, flat ground, no village anywhere near). The best one is
 *       saved at once as the world's <b>planned</b> spot ({@link PortalSiteData}), which {@link PortalSite} reports
 *       until the site is built.</li>
 *   <li><b>Load</b>: the spot's chunks are generated in the background (chunk tickets, like the shrine showcase).</li>
 *   <li><b>Check</b> ({@link SiteCheck}) in the real world: real heights, lakes, biomes, villages and other structures.
 *       If the spot fails, the next one is tried (and the failed one is remembered, so it's never tried again).</li>
 *   <li><b>Build</b>: the castle template or the Fallen Cathedral on fitted ground, then the leak effects.</li>
 * </ol>
 * The number of checks is bounded. If nothing passes, the rules are relaxed step by step ({@link SiteRules#LADDER},
 * with a WARN each time); the last resort builds on the best spot found, on whatever ground is there, so a world is
 * never left without a site. If the site is needed before all this is done (for example {@code /dross site}, or
 * leaving the Dross), {@link #placeNow} finishes it on the spot, keeping the wait short (at most about
 * {@link #NOW_TOTAL_MILLIS} ms). If the world is closed halfway, it carries on from the planned spot next time.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class PortalSitePlacer
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** If a spot's chunks still aren't loaded after this long (3 minutes), it's checked anyway (the rest load then). */
    private static final long LOAD_TIMEOUT_MILLIS = 3 * 60 * 1000L;
    /** After building, the chunks stay loaded this many ticks (5 seconds) so their lighting is done before they're saved. */
    private static final int HOLD_AFTER_BUILD_TICKS = 5 * 20;
    /** At most this many searches at one step of the ladder before relaxing the rules. */
    private static final int SEARCHES_PER_LEVEL = 2;
    /**
     * When the site is needed right away ({@link #placeNow}), the game waits, so that is kept short: a search gets at
     * most this long (it then uses what it found so far)...
     */
    private static final long NOW_SEARCH_MILLIS = 20_000L;
    /** ...at most this many spots are checked per step of the ladder (loading their chunks on the spot)... */
    private static final int NOW_CHECKS_PER_LEVEL = 2;
    /** ...and after this long the best spot found is built on, whatever its ground (a dedicated server gives up after 60 s). */
    private static final long NOW_TOTAL_MILLIS = 45_000L;

    /** Keeps a spot's chunks loaded while it's checked and built. Each spot gets its own key. */
    private static final TicketType<Long> LOAD_TICKET = TicketType.create("dross_portal_site", Long::compare);
    /** Searches run on their own low-priority thread, so neither the server nor world generation waits for them. */
    private static final Executor SEARCH_THREAD = task -> {
        Thread thread = new Thread(task, "Dross portal site search");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 2);
        thread.start();
    };

    /** The placement in progress, or null. Only touched on the server thread. */
    @Nullable
    private static Job job;
    private static final List<Hold> HOLDS = new ArrayList<>();
    private static long nextTicketKey;
    /** The last planned frame worked out, so asking for it every second (the compass) stays cheap. */
    @Nullable
    private static Planned plannedCache;
    /** What this server's worlds build (the castle template's analysis isn't free). */
    @Nullable
    private static SiteLayout layoutCache;

    // ---------------------------------------------------------------- the API

    /** Starts placing the site in the background, once per world. Called when the server has started. */
    static void start(ServerLevel overworld)
    {
        PortalSiteData data = PortalSiteData.get(overworld);
        if (data.isPlaced() || (job != null && job.level == overworld))
        {
            return;
        }
        job = new Job(overworld, false);
    }

    /**
     * Makes sure the site exists, building it right now if needed (waiting for a search that's still running, loading
     * chunks on the spot). Server thread. Never returns null.
     */
    static SiteFrame placeNow(ServerLevel overworld)
    {
        PortalSiteData data = PortalSiteData.get(overworld);
        if (data.isPlaced())
        {
            return data.getFrame();
        }
        Job j = job != null && job.level == overworld ? job : new Job(overworld, true);
        job = j;
        j.sync = true;
        long begin = System.currentTimeMillis();
        LOGGER.info("Dross portal site: needed right now, so it's finished without waiting for the background work.");
        for (int guard = 0; !j.done && guard < 10_000; guard++)
        {
            if (System.currentTimeMillis() - begin > NOW_TOTAL_MILLIS)
            {
                j.lastResort("it was needed right away and finding a good spot took too long");
                break;
            }
            j.step();
        }
        if (!data.isPlaced())
        {
            LOGGER.error("Dross portal site: placing the site didn't finish; building the last resort now.");
            j.lastResort("placing didn't finish");
        }
        if (job == j)
        {
            job = null;
        }
        return data.getFrame();
    }

    /**
     * The frame where the site will be, before it's built: at the planned spot. It never waits: if no spot has been
     * picked yet (only in a new world's first minute or two, while the search runs), it's a guess at sea level straight
     * east of spawn, and the planned spot as soon as the search picks one. Server thread.
     */
    static SiteFrame plannedFrame(ServerLevel overworld)
    {
        PortalSiteData data = PortalSiteData.get(overworld);
        if (data.isPlaced())
        {
            return data.getFrame();
        }
        if (!data.isPlanned() && (job == null || job.level != overworld))
        {
            start(overworld); // nothing is looking for a spot yet (asked before the server finished starting)
        }
        int x = data.isPlanned() ? data.getSiteX() : fallbackX(overworld);
        int z = data.isPlanned() ? data.getSiteZ() : overworld.getSharedSpawnPos().getZ();
        int groundY = data.isPlanned() ? data.getPlannedGroundY() : overworld.getSeaLevel();
        Planned cached = plannedCache;
        if (cached != null && cached.x == x && cached.z == z && cached.groundY == groundY)
        {
            return cached.frame;
        }
        SiteFrame frame = frameFor(overworld, layout(overworld), x, z, groundY);
        plannedCache = new Planned(x, z, groundY, frame);
        return frame;
    }

    // ---------------------------------------------------------------- events

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || (job == null && HOLDS.isEmpty()))
        {
            return;
        }
        long now = event.getServer().getTickCount();
        releaseHolds(now);
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
            // An error here must never crash the server, and the world must still get a site.
            LOGGER.error("Dross portal site: placing the site failed; building the last resort instead.", e);
            try
            {
                j.lastResort("an error (see above)");
            }
            catch (RuntimeException e2)
            {
                LOGGER.error("Dross portal site: the last resort failed too; the site will be built when it's first needed.", e2);
                j.releaseTickets();
                job = null;
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        Job j = job;
        if (j != null)
        {
            j.cancelled.set(true);
            LOGGER.info("Dross portal site: the server stopped before the site was built; it carries on next time{}.",
                    PortalSiteData.get(j.level).isPlanned() ? " from the planned spot" : "");
        }
        // Chunk tickets go away with the server.
        job = null;
        HOLDS.clear();
        plannedCache = null;
        layoutCache = null;
        PortalSite.forgetLastKnown();
    }

    // ---------------------------------------------------------------- one placement

    private static final class Job
    {
        final ServerLevel level;
        final PortalSiteData data;
        final SiteLayout layout;
        final long startedAt = System.currentTimeMillis();
        final AtomicBoolean cancelled = new AtomicBoolean();
        final ArrayDeque<SiteSearch.Candidate> queue = new ArrayDeque<>();
        /** In sync mode nothing waits for the background: searches run here and chunks load on the spot. */
        boolean sync;
        boolean done;
        /** The step of {@link SiteRules#LADDER} in use. */
        int ladder;
        int searches;
        int searchesAtLevel;
        int checksAtLevel;
        int checks;
        @Nullable
        CompletableFuture<SiteSearch.Result> search;
        /** The spot being loaded and checked, its chunks and when they were asked for (wall-clock milliseconds). */
        @Nullable
        SiteSearch.Candidate current;
        @Nullable
        ChunkArea area;
        long ticketKey;
        long requestedAt;
        /** The best spot any search found, for the last resort. */
        @Nullable
        SiteSearch.Candidate best;
        /** True if the last search already scanned the whole ring (searching again with the same rules is pointless). */
        boolean exhausted;
        /** A spot that passed its check, built on the next step (so checking and building never share one tick). */
        @Nullable
        SiteCheck.Result passed;

        Job(ServerLevel level, boolean sync)
        {
            this.level = level;
            this.data = PortalSiteData.get(level);
            this.layout = layout(level);
            this.sync = sync;
            this.ladder = Math.min(Math.max(0, data.getPlanLevel()), SiteRules.LADDER.size() - 1);
            BlockPos spawn = level.getSharedSpawnPos();
            if (data.isPlanned())
            {
                LOGGER.info("Dross portal site: carrying on with the planned spot {} {} (rules '{}').", data.getSiteX(), data.getSiteZ(),
                        rules().name());
                queue.add(new SiteSearch.Candidate(data.getSiteX(), data.getSiteZ(), data.getPlannedGroundY(), 0, 0,
                        "the planned spot", true));
            }
            else
            {
                LOGGER.info("Dross portal site: picking a spot {} to {} blocks from world spawn ({} {}) for the {} ({} x {} blocks).",
                        SiteRules.MIN_DISTANCE, SiteRules.MAX_DISTANCE, spawn.getX(), spawn.getZ(), layout.kind(),
                        layout.width(), layout.depth());
                startSearch();
            }
        }

        SiteRules.Level rules()
        {
            return SiteRules.LADDER.get(ladder);
        }

        long tick()
        {
            return level.getServer().getTickCount();
        }

        /** One step. In the background it's called every tick and returns at once if there's nothing to do yet. */
        void step()
        {
            if (done)
            {
                return;
            }
            if (passed != null)
            {
                SiteCheck.Result spot = passed;
                passed = null;
                build(spot.x(), spot.z(), spot.plan());
                finish();
                return;
            }
            if (search != null)
            {
                if (!sync && !search.isDone())
                {
                    return;
                }
                SiteSearch.Result result;
                try
                {
                    if (sync && !search.isDone())
                    {
                        // Needed right away: give the background search a little longer, then take what it has.
                        try
                        {
                            result = search.get(NOW_SEARCH_MILLIS, TimeUnit.MILLISECONDS);
                        }
                        catch (TimeoutException e)
                        {
                            cancelled.set(true);
                            result = search.join();
                            cancelled.set(false);
                        }
                    }
                    else
                    {
                        result = search.join();
                    }
                }
                catch (InterruptedException e)
                {
                    Thread.currentThread().interrupt();
                    result = SiteSearch.Result.empty();
                }
                catch (ExecutionException | RuntimeException e)
                {
                    LOGGER.error("Dross portal site: the search failed.", e);
                    result = SiteSearch.Result.empty();
                }
                search = null;
                onSearchResult(result);
                return;
            }
            if (current == null)
            {
                nextSpot();
                return;
            }
            if (!sync && area != null)
            {
                boolean ready = area.allLoaded(level);
                if (!ready && System.currentTimeMillis() - requestedAt < LOAD_TIMEOUT_MILLIS)
                {
                    return;
                }
                if (!ready)
                {
                    LOGGER.warn("Dross portal site: the chunks around {} {} still weren't loaded after {} seconds; checking it anyway.",
                            current.x(), current.z(), LOAD_TIMEOUT_MILLIS / 1000);
                }
                else
                {
                    LOGGER.info("Dross portal site: the chunks around {} {} were loaded in the background in {} s.", current.x(),
                            current.z(), Math.round((System.currentTimeMillis() - requestedAt) / 1000.0D));
                }
            }
            checkAndBuild();
        }

        private void startSearch()
        {
            SiteSearch.Context context = SiteSearch.capture(level, layout, data.getRejected());
            SiteRules.Level rules = rules();
            int number = searches++;
            searchesAtLevel++;
            if (sync)
            {
                long deadline = System.currentTimeMillis() + NOW_SEARCH_MILLIS;
                search = CompletableFuture.completedFuture(SiteSearch.run(context, rules, number,
                        () -> System.currentTimeMillis() > deadline));
            }
            else
            {
                search = CompletableFuture.supplyAsync(() -> SiteSearch.run(context, rules, number, cancelled::get), SEARCH_THREAD);
            }
        }

        private void onSearchResult(SiteSearch.Result result)
        {
            BlockPos spawn = level.getSharedSpawnPos();
            exhausted = result.exhaustive();
            LOGGER.info("Dross portal site: search {} (rules '{}'): {}", searches, rules().name(), result.summary());
            for (int i = 0; i < result.candidates().size(); i++)
            {
                SiteSearch.Candidate c = result.candidates().get(i);
                if (i < 3)
                {
                    LOGGER.info("Dross portal site:   spot {}: {}", i + 1, c.describe(spawn.getX(), spawn.getZ()));
                }
                queue.add(c);
            }
            if (!result.candidates().isEmpty())
            {
                SiteSearch.Candidate first = result.candidates().get(0);
                if (best == null || first.score() < best.score())
                {
                    best = first;
                }
                data.setPlanned(first.x(), first.z(), first.groundY(), ladder);
            }
        }

        /** Takes the next spot and asks for its chunks; or searches again, or relaxes the rules, if there's none. */
        private void nextSpot()
        {
            int maxChecks = sync ? NOW_CHECKS_PER_LEVEL : SiteRules.MAX_CHECKS_PER_LEVEL;
            if (queue.isEmpty() || checksAtLevel >= maxChecks)
            {
                if (checksAtLevel < maxChecks && searchesAtLevel < SEARCHES_PER_LEVEL && !exhausted && !sync)
                {
                    LOGGER.info("Dross portal site: no spot left to check; searching again (rules '{}').", rules().name());
                    startSearch();
                }
                else
                {
                    relax(checksAtLevel + " spot(s) checked in the real world, none passed");
                }
                return;
            }
            current = queue.poll();
            checksAtLevel++;
            data.setPlanned(current.x(), current.z(), current.groundY(), ladder);
            if (!sync)
            {
                BoundingBox load = layout.footprintAt(current.x(), current.z(), 0, 0).inflatedBy(SiteCheck.loadMargin(rules()));
                area = ChunkArea.covering(load);
                ticketKey = nextTicketKey++;
                area.addTickets(level, ticketKey);
                requestedAt = System.currentTimeMillis();
                LOGGER.info("Dross portal site: loading {} chunks around {} {} in the background to check it.", area.count(),
                        current.x(), current.z());
            }
        }

        private void checkAndBuild()
        {
            SiteSearch.Candidate spot = current;
            long begin = System.currentTimeMillis();
            SiteCheck.Result check = SiteCheck.check(level, layout, spot.x(), spot.z(), rules());
            checks++;
            long checkedIn = System.currentTimeMillis() - begin;
            if (!check.ok())
            {
                LOGGER.info("Dross portal site: spot {} {} failed the real-world check ({} ms): {}. {}", spot.x(), spot.z(), checkedIn,
                        String.join("; ", check.problems()), check.describe());
                data.addRejected(spot.x(), spot.z());
                releaseTickets();
                current = null;
                return;
            }
            LOGGER.info("Dross portal site: spot {} {} passed the real-world check ({} ms): {}", spot.x(), spot.z(), checkedIn,
                    check.describe());
            data.setPlanned(spot.x(), spot.z(), check.plan().groundY, ladder);
            passed = check;
        }

        /** Builds at a checked spot: the castle template if there's a usable one, else the cathedral, else the placeholder. */
        private void build(int x, int z, GroundFit.Plan plan)
        {
            long begin = System.currentTimeMillis();
            SiteFrame frame = null;
            String kind = PortalSiteData.KIND_PLACEHOLDER;
            if (PortalSiteData.KIND_TEMPLATE.equals(layout.kind()))
            {
                Optional<StructureTemplate> template = level.getStructureManager().get(PortalSite.CASTLE_TEMPLATE);
                if (template.isPresent())
                {
                    frame = PortalCastle.place(level, template.get(), x, z, plan);
                    kind = PortalSiteData.KIND_TEMPLATE;
                }
            }
            if (frame == null)
            {
                frame = PortalCathedral.place(level, x, z, PortalSiteData.KIND_CATHEDRAL.equals(layout.kind()) ? plan : null, rules());
                kind = PortalSiteData.KIND_CATHEDRAL;
            }
            if (frame == null)
            {
                LOGGER.error("Dross portal site: the Fallen Cathedral couldn't be used (see the error above), so the small placeholder "
                        + "shrine is built instead.");
                frame = PortalShrine.build(level, PortalSiteBuilder.placeholderFramePos(level, x, z));
                kind = PortalSiteData.KIND_PLACEHOLDER;
            }
            placed(frame, kind, x, z, System.currentTimeMillis() - begin);
        }

        private void placed(SiteFrame frame, String kind, int x, int z, long buildMillis)
        {
            SiteLeak.apply(level, frame);
            data.markPlaced(frame, kind);
            plannedCache = null;
            BlockPos spawn = level.getSharedSpawnPos();
            LOGGER.info("Dross portal site: placed the {} at {} {} ({} blocks from spawn, rules '{}', {} real-world check(s), {} search(es)); "
                            + "frame corner {} axis {}, opening centre {}; built in {} ms, {} s after starting",
                    kind, x, z, Math.round(Math.sqrt((double) (x - spawn.getX()) * (x - spawn.getX())
                            + (double) (z - spawn.getZ()) * (z - spawn.getZ()))),
                    rules().name(), checks, searches, frame.corner().toShortString(), frame.axis(),
                    frame.openingCenter().toShortString(), buildMillis, Math.round((System.currentTimeMillis() - startedAt) / 1000.0D));
        }

        /** Relaxes the rules one step (with a WARN), or builds the last resort if they're as loose as they go. */
        private void relax(String why)
        {
            releaseTickets();
            current = null;
            queue.clear();
            if (ladder + 1 < SiteRules.LADDER.size())
            {
                String before = rules().name();
                ladder++;
                checksAtLevel = 0;
                searchesAtLevel = 0;
                exhausted = false;
                data.setPlanLevel(ladder);
                LOGGER.warn("Dross portal site: no spot passed the '{}' rules ({}); relaxing them to '{}'.", before, why, rules().name());
                startSearch();
            }
            else
            {
                lastResort(why);
            }
        }

        /** Builds on the best spot found (or a spot straight east of spawn), on whatever ground is there. Never fails. */
        void lastResort(String why)
        {
            releaseTickets();
            current = null;
            passed = null;
            queue.clear();
            BlockPos spawn = level.getSharedSpawnPos();
            int x = best != null ? best.x() : data.isPlanned() ? data.getSiteX() : fallbackX(level);
            int z = best != null ? best.z() : data.isPlanned() ? data.getSiteZ() : spawn.getZ();
            LOGGER.warn("Dross portal site: no spot passed even the loosest rules ({}); building at {} {} on whatever ground is there.",
                    why, x, z);
            long begin = System.currentTimeMillis();
            SiteFrame frame = null;
            String kind = PortalSiteData.KIND_PLACEHOLDER;
            if (PortalSiteData.KIND_TEMPLATE.equals(layout.kind()))
            {
                Optional<StructureTemplate> template = level.getStructureManager().get(PortalSite.CASTLE_TEMPLATE);
                if (template.isPresent())
                {
                    SiteRules.Level loosest = SiteRules.LADDER.get(SiteRules.LADDER.size() - 1);
                    GroundFit.Plan plan = GroundFit.plan(GroundFit.survey(level, layout.footprintAt(x, z, 0, 0),
                            SiteCheck.surveyMargin(loosest)), loosest.blendMargin(), SiteRules.WATER_CLEARANCE);
                    frame = PortalCastle.place(level, template.get(), x, z, plan);
                    kind = PortalSiteData.KIND_TEMPLATE;
                }
            }
            if (frame == null)
            {
                frame = PortalCathedral.placeOnAnyGround(level, x, z);
                kind = PortalSiteData.KIND_CATHEDRAL;
            }
            if (frame == null)
            {
                frame = PortalShrine.build(level, PortalSiteBuilder.placeholderFramePos(level, x, z));
                kind = PortalSiteData.KIND_PLACEHOLDER;
            }
            data.setPlanned(x, z, frame.corner().getY(), SiteRules.LADDER.size());
            placed(frame, kind, x, z, System.currentTimeMillis() - begin);
            finish();
        }

        private void finish()
        {
            done = true;
            cancelled.set(true);
            if (area != null)
            {
                HOLDS.add(new Hold(level, area, ticketKey, tick() + HOLD_AFTER_BUILD_TICKS));
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
                area.removeTickets(level, ticketKey);
                area = null;
            }
        }
    }

    // ---------------------------------------------------------------- helpers

    /** What this world builds at the site (cached for the server's lifetime). */
    private static SiteLayout layout(ServerLevel level)
    {
        SiteLayout layout = layoutCache;
        if (layout == null)
        {
            layout = SiteLayout.forWorld(level);
            layoutCache = layout;
        }
        return layout;
    }

    /** The frame of what will be built with the site column at {@code x}/{@code z} and the ground at {@code groundY}. */
    private static SiteFrame frameFor(ServerLevel level, SiteLayout layout, int x, int z, int groundY)
    {
        SiteFrame frame = null;
        if (PortalSiteData.KIND_TEMPLATE.equals(layout.kind()))
        {
            Optional<StructureTemplate> template = level.getStructureManager().get(PortalSite.CASTLE_TEMPLATE);
            if (template.isPresent())
            {
                frame = PortalCastle.plannedFrame(template.get(), x, z, groundY);
            }
        }
        if (frame == null && !PortalSiteData.KIND_PLACEHOLDER.equals(layout.kind()))
        {
            frame = PortalCathedral.plannedFrame(x, z, groundY);
        }
        if (frame == null)
        {
            frame = SiteFrame.standard(new BlockPos(x - 1, groundY + 1, z), Direction.Axis.X);
        }
        return frame;
    }

    /** A spot straight east of spawn at the minimum distance, if nothing better is known. */
    private static int fallbackX(ServerLevel level)
    {
        return level.getSharedSpawnPos().getX() + SiteRules.MIN_DISTANCE;
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

    private record Planned(int x, int z, int groundY, SiteFrame frame) {}

    /** Chunk tickets to remove at {@code releaseAt}. */
    private record Hold(ServerLevel level, ChunkArea chunks, long key, long releaseAt) {}

    /** A rectangle of chunks, inclusive. */
    private record ChunkArea(int minX, int minZ, int maxX, int maxZ)
    {
        static ChunkArea covering(BoundingBox box)
        {
            return new ChunkArea(SectionPos.blockToSectionCoord(box.minX()), SectionPos.blockToSectionCoord(box.minZ()),
                    SectionPos.blockToSectionCoord(box.maxX()), SectionPos.blockToSectionCoord(box.maxZ()));
        }

        int count()
        {
            return (maxX - minX + 1) * (maxZ - minZ + 1);
        }

        /** Asks for every chunk to be loaded (generated if it's new) in the background, and kept loaded. */
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

        /** True once every chunk is fully loaded ({@code getChunkNow}: not just asked for). Server thread. */
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

    private PortalSitePlacer() {}
}
