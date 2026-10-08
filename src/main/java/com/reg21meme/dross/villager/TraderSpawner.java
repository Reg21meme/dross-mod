package com.reg21meme.dross.villager;

import com.mojang.datafixers.util.Pair;
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
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.Comparator;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Spawns the Dross trader exactly once per world, in his own hut at the edge of the village nearest world spawn.
 * <ol>
 *   <li>When the server starts, find the nearest village and ask Minecraft to generate its chunks
 *       (a chunk "ticket", so it happens in the background without freezing the game).</li>
 *   <li>Once every chunk is generated, pick a spot, build the hut and the path, and spawn the trader.</li>
 *   <li>If there's no village nearby (or it doesn't generate in time, or there's no open spot),
 *       build the hut in the plains/desert nearest world spawn instead, with no path.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public class TraderSpawner
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How far from world spawn (in chunks) we look for a village. 100 chunks = 1,600 blocks. */
    private static final int VILLAGE_SEARCH_CHUNKS = 100;
    /** Extra blocks generated around the village, so the hut spot and its path are ready too. */
    private static final int VILLAGE_MARGIN = 32;
    /** Upper limit for the chunk ticket's radius (in chunks). */
    private static final int MAX_TICKET_RADIUS = 16;
    /** Give up waiting for the village to generate after 60 seconds. */
    private static final int WAIT_TIMEOUT_TICKS = 60 * 20;
    private static final TicketType<ChunkPos> VILLAGE_TICKET =
            TicketType.create("dross_trader_village", Comparator.comparingLong(ChunkPos::toLong));

    /** Fallback: how far from world spawn (in blocks) we look for a plains or desert biome. Same radius vanilla /locate uses. */
    private static final int SEARCH_RADIUS = 6400;
    /** Fallback: how far around the biome hit we look for a dry, safe standing spot. */
    private static final int SPOT_SEARCH_RADIUS = 32;

    private static final Predicate<Holder<Biome>> PLAINS_OR_DESERT = biome ->
            biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(Biomes.DESERT);

    /** The village we're waiting on while its chunks generate, or null when we're not waiting. */
    private static PendingVillage pending;

    private record PendingVillage(StructureStart village, ChunkPos ticketCenter, int ticketRadius, long deadline) {}

    /** True while we're waiting for the village to generate (used by the testing join message). */
    public static boolean isWaiting()
    {
        return pending != null;
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        pending = null;
        MinecraftServer server = event.getServer();
        ServerLevel overworld = server.overworld();
        if (TraderSpawnData.get(overworld).hasSpawned())
        {
            return;
        }

        StructureStart village = findNearestVillage(overworld, overworld.getSharedSpawnPos());
        if (village == null)
        {
            LOGGER.info("[Dross] No village within {} blocks of world spawn, so the trader's hut goes in the nearest plains/desert.",
                    VILLAGE_SEARCH_CHUNKS * 16);
            spawnInFallbackSpot(overworld);
            return;
        }

        BoundingBox area = village.getBoundingBox().inflatedBy(VILLAGE_MARGIN);
        ChunkPos center = new ChunkPos(area.getCenter());
        int radius = Math.max(
                Math.max(Math.abs((area.minX() >> 4) - center.x), Math.abs((area.maxX() >> 4) - center.x)),
                Math.max(Math.abs((area.minZ() >> 4) - center.z), Math.abs((area.maxZ() >> 4) - center.z)));
        radius = Math.min(radius, MAX_TICKET_RADIUS);
        overworld.getChunkSource().addRegionTicket(VILLAGE_TICKET, center, radius, center);
        pending = new PendingVillage(village, center, radius, server.getTickCount() + WAIT_TIMEOUT_TICKS);
        LOGGER.info("[Dross] Waiting for the village at {} to generate before building the trader's hut.",
                village.getBoundingBox().getCenter());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || pending == null)
        {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getTickCount() % 20 != 0)
        {
            return;
        }
        ServerLevel overworld = server.overworld();
        PendingVillage p = pending;
        boolean ready = allChunksLoaded(overworld, p.ticketCenter(), p.ticketRadius());
        boolean timedOut = server.getTickCount() > p.deadline();
        if (!ready && !timedOut)
        {
            return;
        }
        pending = null;

        if (!TraderSpawnData.get(overworld).hasSpawned())
        {
            TraderHut.Site site = null;
            if (!ready)
            {
                LOGGER.warn("[Dross] The village didn't finish generating within {} seconds. Using the plains/desert fallback for the trader's hut.",
                        WAIT_TIMEOUT_TICKS / 20);
            }
            else
            {
                site = TraderHut.findVillageSite(overworld, p.village());
                if (site == null)
                {
                    LOGGER.warn("[Dross] No open, connectable spot at the edge of the village. Using the plains/desert fallback for the trader's hut.");
                }
            }

            if (site != null)
            {
                buildAndSpawn(overworld, site);
            }
            else
            {
                spawnInFallbackSpot(overworld);
            }
        }
        // Remove the ticket last, so the chunks stay loaded while we build.
        overworld.getChunkSource().removeRegionTicket(VILLAGE_TICKET, p.ticketCenter(), p.ticketRadius(), p.ticketCenter());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        // If the world closes while we're still waiting, we simply try again next time it opens.
        pending = null;
    }

    /** The nearest village's structure start (its bounding box and all its pieces), or null. */
    private static StructureStart findNearestVillage(ServerLevel level, BlockPos from)
    {
        BlockPos found = level.findNearestMapStructure(StructureTags.VILLAGE, from, VILLAGE_SEARCH_CHUNKS, false);
        if (found == null)
        {
            return null;
        }
        // Only the village's starting chunk is needed to know its full layout.
        ChunkAccess chunk = level.getChunk(found.getX() >> 4, found.getZ() >> 4, ChunkStatus.STRUCTURE_STARTS);
        Registry<Structure> structures = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Map.Entry<Structure, StructureStart> entry : chunk.getAllStarts().entrySet())
        {
            if (entry.getValue().isValid() && structures.wrapAsHolder(entry.getKey()).is(StructureTags.VILLAGE))
            {
                return entry.getValue();
            }
        }
        return null;
    }

    private static boolean allChunksLoaded(ServerLevel level, ChunkPos center, int radius)
    {
        for (int x = center.x - radius; x <= center.x + radius; x++)
        {
            for (int z = center.z - radius; z <= center.z + radius; z++)
            {
                if (!level.getChunkSource().hasChunk(x, z))
                {
                    return false;
                }
            }
        }
        return true;
    }

    private static void buildAndSpawn(ServerLevel overworld, TraderHut.Site site)
    {
        TraderHut.build(overworld, site);
        TraderHut.layPath(overworld, site);
        BlockPos center = site.center();
        LOGGER.info("[Dross] Built the Dross trader's hut at {}, {}, {} (path of {} blocks)",
                center.getX(), center.getY(), center.getZ(), site.path().size());

        DrossTrader trader = ModEntities.TRADER.get().create(overworld);
        if (trader == null)
        {
            LOGGER.warn("[Dross] Could not create the Dross trader entity.");
            return;
        }
        trader.setHome(center);
        trader.moveTo(center.getX() + 0.5D, center.getY(), center.getZ() + 0.5D, site.doorSide().toYRot(), 0.0F);
        trader.setPersistenceRequired();
        overworld.addFreshEntity(trader);

        TraderSpawnData.get(overworld).markSpawned(center, trader.getUUID());
        LOGGER.info("[Dross] Spawned the Dross trader at {}, {}, {}", center.getX(), center.getY(), center.getZ());
    }

    /** No usable village: put the hut in the plains or desert nearest world spawn, door facing spawn. */
    private static void spawnInFallbackSpot(ServerLevel overworld)
    {
        BlockPos worldSpawn = overworld.getSharedSpawnPos();
        Pair<BlockPos, Holder<Biome>> found = overworld.findClosestBiome3d(PLAINS_OR_DESERT, worldSpawn, SEARCH_RADIUS, 32, 64);
        if (found == null)
        {
            LOGGER.warn("[Dross] No plains or desert biome found within {} blocks of world spawn. The Dross trader was NOT spawned.", SEARCH_RADIUS);
            return;
        }

        BlockPos spot = findSafeSpot(overworld, found.getFirst());
        if (spot == null)
        {
            LOGGER.warn("[Dross] Found a plains/desert biome near {} but no safe dry ground around it. The Dross trader was NOT spawned.", found.getFirst());
            return;
        }
        buildAndSpawn(overworld, TraderHut.fallbackSite(spot, worldSpawn));
    }

    /**
     * Looks around the given position for the closest surface block that is solid, dry, has two free blocks
     * above it, and is still plains or desert. Returns the position the trader should stand in, or null.
     */
    private static BlockPos findSafeSpot(ServerLevel level, BlockPos around)
    {
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;
        for (int dx = -SPOT_SEARCH_RADIUS; dx <= SPOT_SEARCH_RADIUS; dx += 4)
        {
            for (int dz = -SPOT_SEARCH_RADIUS; dz <= SPOT_SEARCH_RADIUS; dz += 4)
            {
                int x = around.getX() + dx;
                int z = around.getZ() + dz;
                // Loads (or generates) the chunk so the heightmap is real.
                level.getChunk(x >> 4, z >> 4, ChunkStatus.FULL);
                BlockPos stand = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(x, 0, z));
                if (isSafe(level, stand))
                {
                    double dist = (double) dx * dx + (double) dz * dz;
                    if (dist < bestDist)
                    {
                        bestDist = dist;
                        best = stand;
                    }
                }
            }
        }
        return best;
    }

    private static boolean isSafe(ServerLevel level, BlockPos stand)
    {
        BlockPos below = stand.below();
        return level.getFluidState(below).isEmpty()
                && level.getFluidState(stand).isEmpty()
                && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)
                && level.getBlockState(stand).getCollisionShape(level, stand).isEmpty()
                && level.getBlockState(stand.above()).getCollisionShape(level, stand.above()).isEmpty()
                && PLAINS_OR_DESERT.test(level.getBiome(stand));
    }
}
