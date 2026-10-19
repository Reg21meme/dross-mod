package com.reg21meme.dross.dimension.rimefall;

import com.reg21meme.dross.boss.rimefall.RimefallAnimals;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.registry.ModEntities;
import java.util.Optional;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.FeaturePlaceContext;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * Feature {@code dross:rimehorn_herd}: puts Rimefall's Rimehorn herds into the world as the land is made (the user,
 * October 19: herds "a few per area"). Each Rimefall patch's herds have their spots in its plan
 * ({@link RimefallAreas.RimefallArea#herds}); a chunk holding one gets a herd of {@link RimefallTuning#HERD_MIN} to
 * {@link RimefallTuning#HERD_MAX} round it (its first two males, the rest females, as any natural pack: the Rimehorn's
 * own spawn rules), on the ground within {@link RimefallTuning#HERD_SPREAD} blocks. Rimehorns aren't in Rimefall's spawn
 * list, so these are the only wild ones.
 */
public class RimehornHerdFeature extends Feature<NoneFeatureConfiguration>
{
    public RimehornHerdFeature()
    {
        super(NoneFeatureConfiguration.CODEC);
    }

    @Override
    public boolean place(FeaturePlaceContext<NoneFeatureConfiguration> context)
    {
        WorldGenLevel level = context.level();
        ServerLevel server = level.getLevel();
        if (!server.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return false;
        }
        ChunkPos chunk = new ChunkPos(context.origin());
        Optional<RimefallAreas.RimefallArea> area = RimefallAreas.areaAt(BossSites.forLevel(server), chunk.getMiddleBlockX(),
                chunk.getMiddleBlockZ());
        if (area.isEmpty())
        {
            return false;
        }
        boolean placed = false;
        for (BlockPos spot : area.get().herds())
        {
            if (new ChunkPos(spot).equals(chunk))
            {
                placed |= spawnHerd(level, server, chunk, spot, context.random()) > 0;
            }
        }
        return placed;
    }

    /** A herd round {@code spot} (kept inside the chunk being made); how many stood up. */
    static int spawnHerd(WorldGenLevel level, ServerLevel server, ChunkPos chunk, BlockPos spot, RandomSource random)
    {
        EntityType<?> type = ModEntities.RIMEHORN.get();
        int size = RimefallTuning.HERD_MIN + random.nextInt(RimefallTuning.HERD_MAX - RimefallTuning.HERD_MIN + 1);
        SpawnGroupData group = null;
        int count = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int i = 0; i < size; i++)
        {
            for (int tries = 0; tries < RimefallTuning.HERD_NEAR_TRIES + RimefallTuning.HERD_CHUNK_TRIES; tries++)
            {
                // Round its spot first; where that's full of trees or boulders, anywhere in the chunk.
                boolean near = tries < RimefallTuning.HERD_NEAR_TRIES;
                int x = near ? clamp(spot.getX() + random.nextInt(2 * RimefallTuning.HERD_SPREAD + 1) - RimefallTuning.HERD_SPREAD,
                        chunk.getMinBlockX() + 1, chunk.getMaxBlockX() - 1)
                        : chunk.getMinBlockX() + 2 + random.nextInt(12);
                int z = near ? clamp(spot.getZ() + random.nextInt(2 * RimefallTuning.HERD_SPREAD + 1) - RimefallTuning.HERD_SPREAD,
                        chunk.getMinBlockZ() + 1, chunk.getMaxBlockZ() - 1)
                        : chunk.getMinBlockZ() + 2 + random.nextInt(12);
                int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x, z);
                at.set(x, y, z);
                if (!level.getBlockState(at.below()).is(RimefallAnimals.ANIMALS_SPAWN_ON) || !level.getFluidState(at).isEmpty()
                        || !level.noCollision(type.getAABB(x + 0.5D, y, z + 0.5D)))
                {
                    continue;
                }
                if (!(type.create(server) instanceof Mob mob))
                {
                    return count;
                }
                mob.moveTo(x + 0.5D, y, z + 0.5D, random.nextFloat() * 360.0F, 0.0F);
                group = ForgeEventFactory.onFinalizeSpawn(mob, level, level.getCurrentDifficultyAt(at), MobSpawnType.CHUNK_GENERATION, group, null);
                level.addFreshEntityWithPassengers(mob);
                count++;
                break;
            }
        }
        return count;
    }

    private static int clamp(int v, int lo, int hi)
    {
        return Math.max(lo, Math.min(hi, v));
    }
}
