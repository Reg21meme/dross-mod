package com.reg21meme.dross.boss.rimefall;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossHubSafeZone;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallAreas;
import com.reg21meme.dross.dimension.rimefall.RimefallTuning;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.dimension.sites.BossSites;
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

    /** A Rimehorn or a moose: on Rimefall's ground, not in water, never in a hunter village ({@link #inHunterVillage}). */
    public static <T extends Mob> boolean checkAnimalSpawn(EntityType<T> type, ServerLevelAccessor level, MobSpawnType reason,
                                                           BlockPos pos, RandomSource random)
    {
        return level.getBlockState(pos.below()).is(ANIMALS_SPAWN_ON) && level.getFluidState(pos).isEmpty()
                && !inHunterVillage(level, reason, pos);
    }

    /**
     * Whether a natural spawn at {@code pos} would be in a hunter village (its footprint, which holds its ring of lights,
     * and round it): none of Rimefall's animals spawn there (the user, October 23, 2026: "within the light perimeter no
     * Rimehorns or skaters or mooses should spawn besides the ones within the fences"; those come with the village).
     * Spawn eggs, commands and spawners aren't stopped.
     */
    public static boolean inHunterVillage(ServerLevelAccessor level, MobSpawnType reason, BlockPos pos)
    {
        if (reason != MobSpawnType.NATURAL && reason != MobSpawnType.CHUNK_GENERATION)
        {
            return false;
        }
        ServerLevel server = level.getLevel();
        return server.dimension().equals(ModDimensions.DROSS_LEVEL)
                && RimefallVillages.villageAt(BossSites.forLevel(server), pos.getX(), pos.getZ(), 0).isPresent();
    }

    /**
     * A moose: on Rimefall's or Silverwood Forest's ground, not in water, and never round the hub (a mother there would
     * go for players arriving: the hub's safe zone, {@link DrossHubSafeZone}) or in a hunter village
     * ({@link #inHunterVillage}).
     */
    public static boolean checkMooseSpawn(EntityType<Moose> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                          RandomSource random)
    {
        if (level.getLevel().dimension().equals(ModDimensions.DROSS_LEVEL) && DrossHubSafeZone.isInSafeZone(pos))
        {
            return false;
        }
        return level.getBlockState(pos.below()).is(MOOSE_SPAWN_ON) && level.getFluidState(pos).isEmpty()
                && !inHunterVillage(level, reason, pos);
    }

    /**
     * A Pond Skater: on ice, in the dark, never on Peaceful (a spawner skips the ground and the light); only now and then
     * ({@link #skaterRolls}); and only where there's room: never more than 3 in an area, a big frozen lake 2 at most, a
     * patch of ice at least 20 blocks across 1 at most (the Dimension area's {@link RimefallAreas#skaterMayAppear}; the
     * user saw 10 on one small pond, then 7); never in a hunter village ({@link #inHunterVillage}).
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
                && skaterRolls(random) && !inHunterVillage(level, reason, pos) && RimefallAreas.skaterMayAppear(level.getLevel(), pos);
    }

    /** A Pond Skater's natural spawn happens only now and then ({@link RimefallTuning#SKATER_SPAWN_CHANCE}; the user:
     * "they should have a random spawn rate"). */
    public static boolean skaterRolls(RandomSource random)
    {
        return random.nextDouble() < RimefallTuning.SKATER_SPAWN_CHANCE;
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
