package com.reg21meme.dross.villager;

import com.mojang.datafixers.util.Pair;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.function.Predicate;

/** Spawns the Dross trader exactly once per world, in the plains/desert biome nearest world spawn. */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public class TraderSpawner
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** How far from world spawn (in blocks) we look for a plains or desert biome. Same radius vanilla /locate uses. */
    private static final int SEARCH_RADIUS = 6400;
    /** How far around the biome hit we look for a dry, safe standing spot. */
    private static final int SPOT_SEARCH_RADIUS = 32;

    private static final Predicate<Holder<Biome>> PLAINS_OR_DESERT = biome ->
            biome.is(Biomes.PLAINS) || biome.is(Biomes.SUNFLOWER_PLAINS) || biome.is(Biomes.DESERT);

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        ServerLevel overworld = event.getServer().overworld();
        TraderSpawnData data = TraderSpawnData.get(overworld);
        if (data.hasSpawned())
        {
            return;
        }

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

        DrossTrader trader = ModEntities.TRADER.get().create(overworld);
        if (trader == null)
        {
            LOGGER.warn("[Dross] Could not create the Dross trader entity.");
            return;
        }
        trader.moveTo(spot.getX() + 0.5D, spot.getY(), spot.getZ() + 0.5D, overworld.random.nextFloat() * 360.0F, 0.0F);
        trader.setPersistenceRequired();
        overworld.addFreshEntity(trader);

        data.markSpawned(spot);
        LOGGER.info("[Dross] Spawned the Dross trader at {}, {}, {}", spot.getX(), spot.getY(), spot.getZ());
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
