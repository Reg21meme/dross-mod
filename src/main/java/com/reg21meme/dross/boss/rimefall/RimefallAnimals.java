package com.reg21meme.dross.boss.rimefall;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossHubSafeZone;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallAreas;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Rimefall's animals (CLAUDE.md, "The plan", step 14): their attributes, where they may spawn, and the showcase's
 * frozen ones. Which animals spawn in Rimefall, and how often, is the biome's spawn list
 * ({@code tools/terrain/worldgen.py}, {@code SPAWN_OVERRIDES["rimefall"]}): only these three (the user's rule).
 * <ul>
 *   <li>The Rimehorn stands on Rimefall's ground (snow, packed ice, Hoarfrost: the block tag
 *       {@code dross:rimefall_animals_spawnable_on}); wild ones are only the few herds put in as the land is made (the
 *       Dimension area's {@code dross:rimehorn_herd}: "a few per area", the user's answer).</li>
 *   <li>The moose spawns on Rimefall's ground and Silverwood Forest's ({@code dross:moose_spawnable_on}), in any light
 *       (the Dross is always night), never round the hub.</li>
 *   <li>The Pond Skater spawns on ice ({@code #minecraft:ice}, even plain ice, which vanilla keeps for polar bears) in
 *       the dark, like any monster, never on Peaceful, and only on a big frozen lake (2 at most) or a frozen pond at
 *       least 30 blocks across (1 at most).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RimefallAnimals
{
    /** The ground Rimefall's animals spawn on. */
    public static final TagKey<Block> ANIMALS_SPAWN_ON = BlockTags.create(new ResourceLocation(Dross.MODID, "rimefall_animals_spawnable_on"));
    /** The ground a moose spawns on: Rimefall's, and Silverwood Forest's (the user, October 19: moose "in the main Dross
     * forest too, with less probability"; how often is worldgen.py's). */
    public static final TagKey<Block> MOOSE_SPAWN_ON = BlockTags.create(new ResourceLocation(Dross.MODID, "moose_spawnable_on"));

    private RimefallAnimals() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.RIMEHORN.get(), Rimehorn.createAttributes().build());
        event.put(ModEntities.MOOSE.get(), Moose.createAttributes().build());
        event.put(ModEntities.POND_SKATER.get(), PondSkater.createAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event)
    {
        event.register(ModEntities.RIMEHORN.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RimefallAnimals::checkAnimalSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntities.MOOSE.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RimefallAnimals::checkMooseSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntities.POND_SKATER.get(), SpawnPlacements.Type.NO_RESTRICTIONS, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RimefallAnimals::checkSkaterSpawn, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    /** A Rimehorn or a moose: on Rimefall's ground, not in water. */
    public static <T extends Mob> boolean checkAnimalSpawn(EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason,
                                                           BlockPos pos, RandomSource random)
    {
        return level.getBlockState(pos.below()).is(ANIMALS_SPAWN_ON) && level.getFluidState(pos).isEmpty();
    }

    /**
     * A moose: on Rimefall's or Silverwood Forest's ground, not in water, and never round the hub (a mother there would
     * go for players arriving: the hub's safe zone, {@link DrossHubSafeZone}).
     */
    public static boolean checkMooseSpawn(EntityType<Moose> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                          RandomSource random)
    {
        if (level.getLevel().dimension().equals(ModDimensions.DROSS_LEVEL) && DrossHubSafeZone.isInSafeZone(pos))
        {
            return false;
        }
        return level.getBlockState(pos.below()).is(MOOSE_SPAWN_ON) && level.getFluidState(pos).isEmpty();
    }

    /**
     * A Pond Skater: on ice, in the dark, never on Peaceful (a spawner skips the ground and the light); and only on a big
     * frozen lake (2 at most) or a frozen pond at least 30 blocks across (1 at most): the Dimension area's
     * {@link RimefallAreas#skaterMayAppear} (the user: they saw 10 on one small pond).
     */
    public static boolean checkSkaterSpawn(EntityType<PondSkater> type, ServerLevelAccessor level, MobSpawnType reason,
                                           BlockPos pos, RandomSource random)
    {
        if (level.getDifficulty() == Difficulty.PEACEFUL)
        {
            return false;
        }
        if (reason == MobSpawnType.SPAWNER)
        {
            return true;
        }
        return level.getBlockState(pos.below()).is(BlockTags.ICE) && level.getBlockState(pos).getCollisionShape(level, pos).isEmpty()
                && level.getFluidState(pos).isEmpty() && Monster.isDarkEnoughToSpawn(level, pos, random)
                && RimefallAreas.skaterMayAppear(level.getLevel(), pos);
    }

    /** Puts a showcase animal in the world, frozen: no AI, silent, never despawning, facing {@code yaw}. */
    static void freeze(Mob mob, ServerLevel level, Vec3 feet, float yaw)
    {
        mob.moveTo(feet.x, feet.y, feet.z, yaw, 0.0F);
        mob.setYHeadRot(yaw);
        mob.setYBodyRot(yaw);
        mob.yHeadRotO = yaw;
        mob.yBodyRotO = yaw;
        mob.setNoAi(true);
        mob.setSilent(true);
        mob.setPersistenceRequired();
        level.addFreshEntity(mob);
    }
}
