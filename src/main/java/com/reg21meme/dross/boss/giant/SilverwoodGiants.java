package com.reg21meme.dross.boss.giant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import javax.annotation.Nullable;

/**
 * The Silverwood Giant's attributes, and where it may spawn by itself (QUEST_PLAN.md, "How many"). How often is the
 * Silverwood Forest's monster list ({@code tools/terrain/worldgen.py}, {@code SILVERWOOD_GIANT_WEIGHT}); these rules,
 * asked before every natural spawn, keep it apart:
 * <ul>
 *   <li>only in the Silverwood Forest, on the ground, in any light (it lives there), never in a fluid, never on
 *       Peaceful;</li>
 *   <li>never within {@link GiantTuning#SPAWN_SPACING} blocks of another giant, so you meet about one every hundred
 *       blocks or so;</li>
 *   <li>never within {@link GiantTuning#HUB_CLEARANCE} blocks of the hub ({@link DrossHub#CENTER});</li>
 *   <li>only where its whole body fits: {@link GiantTuning#SPAWN_HEADROOM} blocks of open air over the 3 x 3 blocks
 *       round where it stands (the Pallbearer's rule).</li>
 * </ul>
 * The extra rules are for natural spawning only (spawn eggs and commands skip them). Runs on the mod event bus.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SilverwoodGiants
{
    private SilverwoodGiants() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.SILVERWOOD_GIANT.get(), SilverwoodGiant.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event)
    {
        event.register(ModEntities.SILVERWOOD_GIANT.get(), SpawnPlacements.Type.ON_GROUND,
                Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, SilverwoodGiants::checkSpawn,
                SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    /** Minecraft's question before a giant spawns at {@code pos}. */
    public static boolean checkSpawn(EntityType<SilverwoodGiant> type, ServerLevelAccessor level, MobSpawnType reason,
                                     BlockPos pos, RandomSource random)
    {
        if (level.getDifficulty() == Difficulty.PEACEFUL || !Mob.checkMobSpawnRules(type, level, reason, pos, random)
                || !level.getFluidState(pos).isEmpty())
        {
            return false;
        }
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION)
        {
            return true;
        }
        boolean inDross = level.getLevel().dimension().equals(ModDimensions.DROSS_LEVEL);
        return refusal(level, pos, level.getBiome(pos).is(DrossBiomes.SILVERWOOD_FOREST), inDross) == null;
    }

    /**
     * Why a natural spawn at {@code pos} is refused, or null if it isn't (the tests ask this, telling it the biome).
     *
     * @param inSilverwood true if {@code pos} is in the Silverwood Forest
     * @param inDross      true if it's in the Dross (where the hub is)
     */
    @Nullable
    public static String refusal(ServerLevelAccessor level, BlockPos pos, boolean inSilverwood, boolean inDross)
    {
        if (!inSilverwood)
        {
            return "not in the Silverwood Forest";
        }
        if (inDross && nearHub(pos))
        {
            return "too near the hub";
        }
        if (!hasRoom(level, pos))
        {
            return "no room for its body";
        }
        if (anotherNear(level, pos))
        {
            return "another giant within " + (int) GiantTuning.SPAWN_SPACING + " blocks";
        }
        return null;
    }

    /** True if {@code pos} is within {@link GiantTuning#HUB_CLEARANCE} blocks of the hub's middle (along the ground). */
    public static boolean nearHub(BlockPos pos)
    {
        double dx = pos.getX() + 0.5D - (DrossHub.CENTER.getX() + 0.5D);
        double dz = pos.getZ() + 0.5D - (DrossHub.CENTER.getZ() + 0.5D);
        return dx * dx + dz * dz < GiantTuning.HUB_CLEARANCE * GiantTuning.HUB_CLEARANCE;
    }

    /** True if the 3 x 3 columns round {@code pos} are open air (nothing to bump into, no fluid) for
     *  {@link GiantTuning#SPAWN_HEADROOM} blocks up. */
    public static boolean hasRoom(ServerLevelAccessor level, BlockPos pos)
    {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < GiantTuning.SPAWN_HEADROOM; dy++)
        {
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    BlockState state = level.getBlockState(at);
                    if (!state.getCollisionShape(level, at).isEmpty() || !state.getFluidState().isEmpty())
                    {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    /** True if a living giant stands within {@link GiantTuning#SPAWN_SPACING} blocks of {@code pos} (of those loaded). */
    public static boolean anotherNear(ServerLevelAccessor level, BlockPos pos)
    {
        double range = GiantTuning.SPAWN_SPACING;
        double rangeSq = range * range;
        return !level.getEntitiesOfClass(SilverwoodGiant.class, new AABB(pos).inflate(range, 64.0D, range),
                e -> e.isAlive() && e.distanceToSqr(pos.getX() + 0.5D, e.getY(), pos.getZ() + 0.5D) <= rangeSq).isEmpty();
    }
}
