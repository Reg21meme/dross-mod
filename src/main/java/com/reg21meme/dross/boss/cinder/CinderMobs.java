package com.reg21meme.dross.boss.cinder;

import com.reg21meme.dross.Dross;
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

/**
 * The Cinder Wastes' two regular mobs (CLAUDE.md step 16): their attributes, and where they may spawn by themselves.
 * Which mobs spawn there, and how often, is the biome's spawn list ({@code tools/terrain/worldgen.py}, the Cinder Wastes'
 * {@code SPAWN_OVERRIDES}: the user's shares by count, stone golems 45%, lava golems 13%, Cinder Hermits 30%, Pallbearers
 * 12%; all four share one crowding limit, the spawn costs). Minecraft asks these rules before every natural spawn, so
 * they're kept cheap.
 * <ul>
 *   <li>Both stand on the ground (a block a mob may spawn on), in any light (like the golems: the Dross is always night,
 *       and these belong in the lava-lit wastes), never in a fluid, never on Peaceful.</li>
 *   <li>A Pallbearer (9.6 blocks tall) needs {@link CinderMobTuning#PALLBEARER_HEADROOM} blocks of open air over the 3 x 3
 *       blocks round where it stands, and never spawns within {@link CinderMobTuning#PALLBEARER_SPACING} blocks of
 *       another.</li>
 *   <li>Never more than {@link CinderMobTuning#HERMIT_CROWD} Cinder Hermits within
 *       {@link CinderMobTuning#HERMIT_CROWD_RANGE} blocks.</li>
 * </ul>
 * The extra rules are for natural spawning only (a spawner skips the ground; spawn eggs and commands skip them all).
 * Runs on the mod event bus.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class CinderMobs
{
    private CinderMobs() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.PALLBEARER.get(), Pallbearer.createAttributes().build());
        event.put(ModEntities.CINDER_HERMIT.get(), CinderHermit.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event)
    {
        event.register(ModEntities.PALLBEARER.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CinderMobs::checkPallbearerSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntities.CINDER_HERMIT.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                CinderMobs::checkHermitSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    /** A Pallbearer: on dry ground, with room over it and no other Pallbearer near. */
    public static boolean checkPallbearerSpawn(EntityType<Pallbearer> type, ServerLevelAccessor level, MobSpawnType reason,
                                               BlockPos pos, RandomSource random)
    {
        if (!onDryGround(type, level, reason, pos, random))
        {
            return false;
        }
        if (!natural(reason))
        {
            return true;
        }
        return hasHeadroom(level, pos) && count(level, Pallbearer.class, pos, CinderMobTuning.PALLBEARER_SPACING) == 0;
    }

    /** A Cinder Hermit: on dry ground, and not too many already round it. */
    public static boolean checkHermitSpawn(EntityType<CinderHermit> type, ServerLevelAccessor level, MobSpawnType reason,
                                           BlockPos pos, RandomSource random)
    {
        if (!onDryGround(type, level, reason, pos, random))
        {
            return false;
        }
        return !natural(reason) || count(level, CinderHermit.class, pos, CinderMobTuning.HERMIT_CROWD_RANGE) < CinderMobTuning.HERMIT_CROWD;
    }

    private static boolean natural(MobSpawnType reason)
    {
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION;
    }

    /** Not Peaceful, standing on a block a mob may spawn on (a spawner skips the ground), in any light; no fluid. */
    private static boolean onDryGround(EntityType<? extends Mob> type, ServerLevelAccessor level, MobSpawnType reason,
                                       BlockPos pos, RandomSource random)
    {
        return level.getDifficulty() != Difficulty.PEACEFUL && Mob.checkMobSpawnRules(type, level, reason, pos, random)
                && level.getFluidState(pos).isEmpty();
    }

    /** True if the 3 x 3 columns round {@code pos} are open air (nothing to bump into, no fluid) for
     *  {@link CinderMobTuning#PALLBEARER_HEADROOM} blocks up. */
    public static boolean hasHeadroom(ServerLevelAccessor level, BlockPos pos)
    {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dy = 0; dy < CinderMobTuning.PALLBEARER_HEADROOM; dy++)
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

    /** How many living mobs of this kind stand within {@code range} blocks of {@code pos}. */
    private static int count(ServerLevelAccessor level, Class<? extends CinderMob> kind, BlockPos pos, double range)
    {
        double rangeSq = range * range;
        return level.getEntitiesOfClass(kind, new AABB(pos).inflate(range),
                e -> e.isAlive() && e.distanceToSqr(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D) <= rangeSq).size();
    }
}
