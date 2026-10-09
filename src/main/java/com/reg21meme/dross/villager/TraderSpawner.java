package com.reg21meme.dross.villager;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkGeneratorStructureState;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureSet;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.level.levelgen.structure.placement.RandomSpreadStructurePlacement;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Spawns the Dross trader exactly once per world, in his own hut (the Rift Chapel, see {@link TraderHut})
 * at a village, close to its houses, with a wide path to its nearest street.
 * <p>
 * <b>Which village.</b> Not just the nearest one: villages are checked in batches of {@link #VILLAGES_PER_BATCH},
 * nearest to world spawn first, and he moves into the closest one that has a good spot.
 * <ol>
 *   <li>When the server starts, list every place a village could be (the village structure sets' own placement
 *       grid: one possible village chunk per region), out to {@link #MAX_SEARCH_RADIUS} blocks from spawn. Then,
 *       a few per tick, read each one's structure start (cheap: it says whether a village really generated
 *       there, and gives its pieces) until a batch is full. The first batch only looks at villages within
 *       {@link #FIRST_SEARCH_RADIUS} blocks of spawn in X and Z (a 2,000 x 2,000 square).</li>
 *   <li>Every village of the batch is asked to generate its chunks, plus enough around it for the spot search
 *       (a chunk "ticket" each, so it happens in the background without freezing the game). Once a second the
 *       nearest village not yet judged is looked at: when its chunks are all generated (or
 *       {@link #WAIT_TIMEOUT_TICKS} have passed) the spot search runs on it, and its ticket is dropped.
 *       The first village, nearest first, with a good spot wins: the hut and path are built and the trader
 *       spawned inside, in the aisle. (Farther villages of the batch are not searched, since the nearest one with
 *       a spot wins anyway.)</li>
 *   <li>If none of the batch has a spot, the next batch of the nearest unchecked villages follows. When the square
 *       has no villages left it grows by {@link #SEARCH_RADIUS_STEP}, up to {@link #MAX_SEARCH_RADIUS}.</li>
 *   <li><b>Emergency:</b> only if no village can be used at all (structures turned off, or nothing usable within
 *       {@link #MAX_SEARCH_RADIUS} blocks) is the hut built on the best flat, dry ground around world spawn, in any
 *       biome, with a warning in the log. {@link TraderSpawnData#isInVillage()} records which it was.</li>
 * </ol>
 * If the world is closed while this is going on, it simply starts again the next time. Each village checked and the
 * final choice are logged on one line each.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public class TraderSpawner
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How many villages are generated and checked at the same time. */
    static final int VILLAGES_PER_BATCH = 5;
    /** The first batches only use villages this far from world spawn in X and Z (blocks): a 2,000 x 2,000 square. */
    static final int FIRST_SEARCH_RADIUS = 1000;
    /** When the square has no unchecked villages left, it grows by this much on each side (blocks). */
    static final int SEARCH_RADIUS_STEP = 1000;
    /** The square never grows past this many blocks from spawn in X and Z. */
    static final int MAX_SEARCH_RADIUS = 5000;
    /** How many possible villages are looked at (structure start read) per server tick while filling a batch. */
    private static final int STARTS_PER_TICK = 4;
    /**
     * Extra blocks generated around each village, so the hut spot and its path are ready too. The hut search
     * says how much it really reads ({@link TraderHut#searchReach()}); this is at least that, plus one.
     */
    private static final int VILLAGE_MARGIN = 32;
    /** Upper limit for a chunk ticket's radius (in chunks). */
    private static final int MAX_TICKET_RADIUS = 16;
    /**
     * Give up waiting for a batch's chunks to generate after this long (server ticks). Generous: up to five
     * villages generate at once. Villages that aren't ready by then are skipped.
     */
    static final int WAIT_TIMEOUT_TICKS = 180 * 20;
    private static final TicketType<ChunkPos> VILLAGE_TICKET =
            TicketType.create("dross_trader_village", Comparator.comparingLong(ChunkPos::toLong));

    /** Emergency spot: how far around world spawn we look for a dry, safe standing spot, ring by ring (blocks). */
    private static final int EMERGENCY_SEARCH_STEP = 32;
    private static final int EMERGENCY_SEARCH_RADIUS = 96;
    /** Emergency spot: standing spots are tried every this many blocks. */
    private static final int SPOT_GRID = 4;

    /** The search in progress, or null when there is none. */
    @Nullable
    private static Search search;

    /** A village that really generated, with the chunk ticket that is generating its surroundings. */
    private static final class Village
    {
        final StructureStart start;
        final BlockPos center;
        final double distance;
        final ChunkPos ticketCenter;
        final int ticketRadius;
        boolean ticketHeld;

        Village(StructureStart start, BlockPos center, double distance, ChunkPos ticketCenter, int ticketRadius)
        {
            this.start = start;
            this.center = center;
            this.distance = distance;
            this.ticketCenter = ticketCenter;
            this.ticketRadius = ticketRadius;
        }
    }

    private static final class Search
    {
        final VillageSearchPlan<Village> plan;
        /** The batch being generated and judged (nearest first), or null while the next one is being filled. */
        @Nullable
        List<Village> batch;
        int batchNumber;
        /** The index in {@link #batch} of the next village to judge. */
        int judged;
        long deadline;
        /** Villages judged so far, in all batches. */
        int checked;

        Search(VillageSearchPlan<Village> plan)
        {
            this.plan = plan;
        }
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        search = null;
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (TraderSpawnData.get(overworld).hasSpawned())
        {
            return;
        }

        if (!server.getWorldData().worldGenOptions().generateStructures())
        {
            spawnAtEmergencySpot(overworld, "this world has structures turned off, so it has no villages");
            return;
        }
        BlockPos spawn = overworld.getSharedSpawnPos();
        List<VillageSearchPlan.Candidate> candidates = findPossibleVillages(overworld, spawn);
        if (candidates.isEmpty())
        {
            spawnAtEmergencySpot(overworld, "no village can be placed within " + MAX_SEARCH_RADIUS + " blocks of world spawn");
            return;
        }
        search = new Search(
                new VillageSearchPlan<>(candidates, candidate -> resolveVillage(overworld, candidate, spawn),
                        FIRST_SEARCH_RADIUS, SEARCH_RADIUS_STEP, MAX_SEARCH_RADIUS, VILLAGES_PER_BATCH));
        LOGGER.info("[Dross] Trader hut: looking for a village. {} possible village spots within {} blocks of world spawn; "
                        + "checking the nearest {} at a time, starting within {} blocks (a {} x {} square).",
                candidates.size(), MAX_SEARCH_RADIUS, VILLAGES_PER_BATCH, FIRST_SEARCH_RADIUS,
                2 * FIRST_SEARCH_RADIUS, 2 * FIRST_SEARCH_RADIUS);
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        Search s = search;
        if (event.phase != TickEvent.Phase.END || s == null)
        {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null)
        {
            return;
        }
        ServerLevel overworld = server.overworld();
        try
        {
            if (s.batch == null)
            {
                // Filling the next batch: a few possible villages per tick.
                List<Village> found = s.plan.advance(STARTS_PER_TICK);
                if (found == null)
                {
                    return;
                }
                if (found.isEmpty())
                {
                    search = null;
                    spawnAtEmergencySpot(overworld, "none of the " + s.checked + " villages within " + MAX_SEARCH_RADIUS
                            + " blocks of world spawn had a usable spot (or none exist)");
                    return;
                }
                startBatch(server, overworld, s, found);
                return;
            }
            if (server.getTickCount() % 20 != 0)
            {
                return;
            }
            if (TraderSpawnData.get(overworld).hasSpawned())
            {
                releaseAll(overworld, s);
                search = null;
                return;
            }
            judgeNext(server, overworld, s);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("[Dross] Trader hut: the village search failed.", e);
            search = null;
            releaseAll(overworld, s);
            if (!TraderSpawnData.get(overworld).hasSpawned())
            {
                spawnAtEmergencySpot(overworld, "the village search failed (see the error above)");
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        // If the world closes while we're still searching, we simply start again next time it opens.
        search = null;
    }

    // ---------------------------------------------------------------- finding villages

    /**
     * Every chunk where a village could be, out to {@link #MAX_SEARCH_RADIUS} from spawn: each region of each village
     * structure set's placement grid has one potential chunk (the same maths the world generator uses).
     * Most of them turn out to have no village (wrong biome); {@link #resolveVillage} finds out.
     */
    private static List<VillageSearchPlan.Candidate> findPossibleVillages(ServerLevel level, BlockPos spawn)
    {
        ChunkGeneratorStructureState state = level.getChunkSource().getGeneratorState();
        int minChunkX = (spawn.getX() - MAX_SEARCH_RADIUS) >> 4;
        int maxChunkX = (spawn.getX() + MAX_SEARCH_RADIUS) >> 4;
        int minChunkZ = (spawn.getZ() - MAX_SEARCH_RADIUS) >> 4;
        int maxChunkZ = (spawn.getZ() + MAX_SEARCH_RADIUS) >> 4;
        List<VillageSearchPlan.Candidate> found = new ArrayList<>();
        Set<Long> seen = new HashSet<>();
        for (Holder<StructureSet> holder : state.possibleStructureSets())
        {
            StructureSet set = holder.value();
            if (!(set.placement() instanceof RandomSpreadStructurePlacement placement)
                    || set.structures().stream().noneMatch(entry -> entry.structure().is(StructureTags.VILLAGE)))
            {
                continue;
            }
            int spacing = placement.spacing();
            for (int regionX = Math.floorDiv(minChunkX, spacing); regionX <= Math.floorDiv(maxChunkX, spacing); regionX++)
            {
                for (int regionZ = Math.floorDiv(minChunkZ, spacing); regionZ <= Math.floorDiv(maxChunkZ, spacing); regionZ++)
                {
                    ChunkPos chunk = placement.getPotentialStructureChunk(state.getLevelSeed(),
                            regionX * spacing, regionZ * spacing);
                    if (!placement.isStructureChunk(state, chunk.x, chunk.z))
                    {
                        continue;
                    }
                    VillageSearchPlan.Candidate candidate =
                            VillageSearchPlan.Candidate.of(chunk.x, chunk.z, spawn.getX(), spawn.getZ());
                    if (candidate.ring() <= MAX_SEARCH_RADIUS && seen.add(chunk.toLong()))
                    {
                        found.add(candidate);
                    }
                }
            }
        }
        return found;
    }

    /**
     * Reads the structure start of a possible village chunk: its bounding box and all its pieces. Only that one chunk
     * is brought to the "structure starts" stage, which is cheap. Returns null if no village generated there.
     */
    @Nullable
    private static Village resolveVillage(ServerLevel level, VillageSearchPlan.Candidate candidate, BlockPos spawn)
    {
        ChunkAccess chunk = level.getChunk(candidate.chunkX(), candidate.chunkZ(), ChunkStatus.STRUCTURE_STARTS);
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet())
        {
            StructureStart start = entry.getValue();
            if (!start.isValid() || !structures.wrapAsHolder(entry.getKey()).is(StructureTags.VILLAGE))
            {
                continue;
            }
            BoundingBox box = start.getBoundingBox();
            BlockPos center = box.getCenter();
            double dx = center.getX() - spawn.getX();
            double dz = center.getZ() - spawn.getZ();

            // Generate the village and everything around it that the hut search will read.
            int margin = Math.max(VILLAGE_MARGIN, TraderHut.searchReach() + 1);
            BoundingBox area = box.inflatedBy(margin);
            ChunkPos ticketCenter = new ChunkPos(area.getCenter());
            int radius = Math.max(
                    Math.max(Math.abs((area.minX() >> 4) - ticketCenter.x), Math.abs((area.maxX() >> 4) - ticketCenter.x)),
                    Math.max(Math.abs((area.minZ() >> 4) - ticketCenter.z), Math.abs((area.maxZ() >> 4) - ticketCenter.z)));
            return new Village(start, center, Math.sqrt(dx * dx + dz * dz), ticketCenter,
                    Math.min(radius, MAX_TICKET_RADIUS));
        }
        return null;
    }

    // ---------------------------------------------------------------- one batch

    /** Starts generating all the villages of a batch at once, nearest first. */
    private static void startBatch(MinecraftServer server, ServerLevel level, Search s, List<Village> villages)
    {
        List<Village> batch = new ArrayList<>(villages);
        batch.sort(Comparator.comparingDouble((Village v) -> v.distance));
        for (Village v : batch)
        {
            level.getChunkSource().addRegionTicket(VILLAGE_TICKET, v.ticketCenter, v.ticketRadius, v.ticketCenter);
            v.ticketHeld = true;
        }
        s.batch = batch;
        s.judged = 0;
        s.batchNumber++;
        s.deadline = server.getTickCount() + WAIT_TIMEOUT_TICKS;
        StringBuilder list = new StringBuilder();
        for (Village v : batch)
        {
            list.append(list.length() == 0 ? "" : "; ").append(v.center.getX()).append(", ").append(v.center.getZ())
                    .append(" (").append(Math.round(v.distance)).append(" blocks)");
        }
        LOGGER.info("[Dross] Trader hut: batch {} (within {} blocks of spawn): generating {} village(s) at once: {}.",
                s.batchNumber, s.plan.radius(), batch.size(), list);
    }

    /**
     * Judges the nearest village of the batch that hasn't been judged yet, once its chunks are generated (or time is
     * up). If it has a good spot the hut is built there and the search ends. Otherwise the next one is judged on the
     * next call, and when the batch is used up the next batch is started.
     */
    private static void judgeNext(MinecraftServer server, ServerLevel level, Search s)
    {
        List<Village> batch = s.batch;
        Village village = batch.get(s.judged);
        boolean ready = allChunksLoaded(level, village.ticketCenter, village.ticketRadius);
        if (!ready && server.getTickCount() <= s.deadline)
        {
            return;
        }
        int index = s.judged + 1;
        s.judged = index;
        s.checked++;
        String which = "village " + index + "/" + batch.size() + " of batch " + s.batchNumber + " at "
                + village.center.getX() + ", " + village.center.getZ() + " (" + Math.round(village.distance)
                + " blocks from spawn)";

        TraderHut.Site site = null;
        if (!ready)
        {
            LOGGER.warn("[Dross] Trader hut: {}: its chunks didn't finish generating within {} seconds, skipped.",
                    which, WAIT_TIMEOUT_TICKS / 20);
        }
        else
        {
            long started = System.nanoTime();
            site = findVillageSite(level, village.start);
            long ms = (System.nanoTime() - started) / 1_000_000L;
            if (site == null)
            {
                LOGGER.info("[Dross] Trader hut: {}: no spot ({} ms).", which, ms);
            }
            else
            {
                LOGGER.info("[Dross] Trader hut: {}: spot found, door facing {}, path {} blocks ({} ms).",
                        which, site.facing().getName(), site.path().size(), ms);
            }
        }

        if (site != null)
        {
            LOGGER.info("[Dross] Trader hut: picked the village at {}, {} ({} blocks from spawn): the closest village with "
                            + "a good spot (village {} of {} in batch {}; the {} village(s) checked before it had none).",
                    village.center.getX(), village.center.getZ(), Math.round(village.distance), index, batch.size(),
                    s.batchNumber, s.checked - 1);
            search = null;
            try
            {
                buildAndSpawn(level, site, true);
            }
            finally
            {
                // Remove the tickets last, so the chunks stay loaded while we build (and even if something went wrong).
                releaseAll(level, s);
            }
            return;
        }

        release(level, village);
        if (index >= batch.size())
        {
            LOGGER.info("[Dross] Trader hut: none of the {} village(s) in batch {} had a good spot; trying the next ones.",
                    batch.size(), s.batchNumber);
            s.batch = null;
        }
    }

    /** The hut search, guarded: an error is logged and counts as "no spot", so the next village can be tried. */
    @Nullable
    private static TraderHut.Site findVillageSite(ServerLevel overworld, StructureStart village)
    {
        try
        {
            return TraderHut.findVillageSite(overworld, village);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("[Dross] Searching for the trader's hut spot at the village failed.", e);
            return null;
        }
    }

    private static void release(ServerLevel level, Village village)
    {
        if (village.ticketHeld)
        {
            village.ticketHeld = false;
            level.getChunkSource().removeRegionTicket(VILLAGE_TICKET, village.ticketCenter, village.ticketRadius,
                    village.ticketCenter);
        }
    }

    private static void releaseAll(ServerLevel level, Search s)
    {
        if (s.batch != null)
        {
            for (Village village : s.batch)
            {
                release(level, village);
            }
        }
    }

    /**
     * True once every chunk of the ticket area is fully generated. (Not {@code hasChunk}: that only says the chunk
     * has a ticket, not that it's finished, and then the hut search would generate chunks on the server thread.)
     * Server thread only, which the tick handler is.
     */
    private static boolean allChunksLoaded(ServerLevel level, ChunkPos center, int radius)
    {
        for (int x = center.x - radius; x <= center.x + radius; x++)
        {
            for (int z = center.z - radius; z <= center.z + radius; z++)
            {
                if (level.getChunkSource().getChunkNow(x, z) == null)
                {
                    return false;
                }
            }
        }
        return true;
    }

    // ---------------------------------------------------------------- building

    /**
     * Builds the hut and its path, then spawns the trader inside, in his spot, facing the door.
     * If the build throws, the error is logged and he is still spawned and remembered: an error in
     * a tick handler would otherwise crash the game every time the world is reloaded.
     *
     * @param inVillage true if the hut is at a village, false for the emergency spot (saved for the Weathered Letter)
     */
    private static void buildAndSpawn(ServerLevel overworld, TraderHut.Site site, boolean inVillage)
    {
        BlockPos spot = site.traderSpot();
        try
        {
            TraderHut.build(overworld, site);
            TraderHut.layPath(overworld, site);
            LOGGER.info("[Dross] Built the Dross trader's hut at {}, {}, {} (path of {} blocks)",
                    spot.getX(), spot.getY(), spot.getZ(), site.path().size());
        }
        catch (RuntimeException e)
        {
            LOGGER.error("[Dross] Building the Dross trader's hut at {}, {}, {} failed. Spawning him there anyway.",
                    spot.getX(), spot.getY(), spot.getZ(), e);
        }

        DrossTrader trader = ModEntities.TRADER.get().create(overworld);
        if (trader == null)
        {
            LOGGER.warn("[Dross] Could not create the Dross trader entity.");
            return;
        }
        trader.setHome(spot, site.footprint());
        trader.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, site.facing().toYRot(), 0.0F);
        trader.setPersistenceRequired();
        overworld.addFreshEntity(trader);

        TraderSpawnData.get(overworld).markSpawned(spot, trader.getUUID(), inVillage);
        LOGGER.info("[Dross] Spawned the Dross trader at {}, {}, {}", spot.getX(), spot.getY(), spot.getZ());
    }

    // ---------------------------------------------------------------- the emergency spot

    /**
     * No village could be used: put the hut on flat, dry ground near world spawn, in any biome, door facing spawn
     * (or turned if the ground needs it). The first safe spot, nearest spawn first, that has room for the whole hut
     * wins. It is logged as a warning, and saved as "not in a village".
     */
    private static void spawnAtEmergencySpot(ServerLevel overworld, String why)
    {
        BlockPos worldSpawn = overworld.getSharedSpawnPos();
        LOGGER.warn("[Dross] Trader hut: EMERGENCY spot near world spawn ({}, {}), not at a village: {}.",
                worldSpawn.getX(), worldSpawn.getZ(), why);

        TraderHut.Site site = null;
        try
        {
            int inner = -1;
            for (int outer = EMERGENCY_SEARCH_STEP; outer <= EMERGENCY_SEARCH_RADIUS && site == null;
                 outer += EMERGENCY_SEARCH_STEP)
            {
                for (BlockPos spot : findSafeSpots(overworld, worldSpawn, inner, outer))
                {
                    site = TraderHut.fallbackSite(overworld, spot, worldSpawn);
                    if (site != null)
                    {
                        break;
                    }
                }
                inner = outer;
            }
        }
        catch (RuntimeException e)
        {
            LOGGER.error("[Dross] Searching for a flat spot for the trader's hut failed.", e);
        }
        if (site == null)
        {
            LOGGER.warn("[Dross] Trader hut: no flat, dry ground with room for the hut within {} blocks of world spawn. The Dross trader was NOT spawned.",
                    EMERGENCY_SEARCH_RADIUS);
            return;
        }
        BlockPos spot = site.traderSpot();
        LOGGER.warn("[Dross] Trader hut: building the emergency hut at {}, {}, {} ({} blocks from spawn).",
                spot.getX(), spot.getY(), spot.getZ(),
                Math.round(Math.sqrt(spot.distSqr(new BlockPos(worldSpawn.getX(), spot.getY(), worldSpawn.getZ())))));
        buildAndSpawn(overworld, site, false);
    }

    /**
     * Looks around the given position, in the square ring between {@code innerRadius} (exclusive) and
     * {@code outerRadius} (inclusive), for every surface block that is solid, dry and has two free blocks above it.
     * Returns the positions a trader could stand in, nearest first.
     */
    private static List<BlockPos> findSafeSpots(ServerLevel level, BlockPos around, int innerRadius, int outerRadius)
    {
        List<BlockPos> spots = new ArrayList<>();
        for (int dx = -outerRadius; dx <= outerRadius; dx += SPOT_GRID)
        {
            for (int dz = -outerRadius; dz <= outerRadius; dz += SPOT_GRID)
            {
                if (Math.max(Math.abs(dx), Math.abs(dz)) <= innerRadius)
                {
                    continue;
                }
                int x = around.getX() + dx;
                int z = around.getZ() + dz;
                // Loads (or generates) the chunk so the heightmap is real.
                level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL);
                BlockPos stand = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (isSafe(level, stand))
                {
                    spots.add(stand);
                }
            }
        }
        spots.sort(Comparator.comparingLong((BlockPos spot) ->
        {
            long dx = spot.getX() - around.getX();
            long dz = spot.getZ() - around.getZ();
            return dx * dx + dz * dz;
        }));
        return spots;
    }

    private static boolean isSafe(ServerLevel level, BlockPos stand)
    {
        BlockPos below = stand.below();
        return level.getFluidState(below).isEmpty()
                && level.getFluidState(stand).isEmpty()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()
                && level.getBlockState(stand.above()).getCollisionShape(level, stand.above()).isEmpty();
    }
}
