package com.reg21meme.dross.boss.ratking;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.Sewers;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.ServerLevelAccessor;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.event.entity.SpawnPlacementRegisterEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Gives the Rat King and his rats their attributes (their numbers are in {@link RatKingTuning}), and the rats their
 * natural spawn rules. Mod event bus.
 * <p>
 * <b>Where rats spawn by themselves</b> (the sewers' and the rat caves' spawn lists, dimension-builder's, say that they
 * spawn there and how often; these rules say where exactly, {@link #checkRatSpawnRules}): both kinds like any monster
 * standing on the ground (a solid block a mob may spawn on: the sewers' bricks and tiles, Sewer Sludge, mud...), with
 * room for them, never on Peaceful, and in the dark: everywhere only in the dark (no block light at all in the Dross, and
 * little sky light), but inside the sewers (the {@code dross:sewers} biome, or a sewer network's piece out of the sky's
 * reach, rat caves included) also in dim light up to {@link RatKingTuning#RAT_SEWER_SPAWN_MAX_BLOCK_LIGHT} (the glow lichen
 * on their walls). A natural Plague Rat is harmless wildlife (it wanders and flees when hurt); a natural Plague Ravager is
 * hostile (and, inside a rat cave, stronger: {@link Rat#finalizeSpawn}).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RatKingEvents
{
    private RatKingEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.RAT_KING.get(), RatKing.createAttributes().build());
        event.put(ModEntities.RAT.get(), Rat.createAttributes().build());
        event.put(ModEntities.ENRAGED_RAT.get(), Rat.createEnragedAttributes().build());
    }

    @SubscribeEvent
    public static void onSpawnPlacements(SpawnPlacementRegisterEvent event)
    {
        event.register(ModEntities.RAT.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RatKingEvents::checkRatSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
        event.register(ModEntities.ENRAGED_RAT.get(), SpawnPlacements.Type.ON_GROUND, Heightmap.Types.MOTION_BLOCKING_NO_LEAVES,
                RatKingEvents::checkRatSpawnRules, SpawnPlacementRegisterEvent.Operation.REPLACE);
    }

    /**
     * Both rats' natural spawn rule: never on Peaceful, on spawnable ground; in the sewers ({@link #isSewerSpot}) where the
     * sky's light doesn't reach much (Minecraft's own sky test) and the block light is at most
     * {@link RatKingTuning#RAT_SEWER_SPAWN_MAX_BLOCK_LIGHT}; anywhere else Minecraft's monster rule (in the dark).
     */
    public static boolean checkRatSpawnRules(EntityType<? extends Monster> type, ServerLevelAccessor level, MobSpawnType reason, BlockPos pos,
                                             RandomSource random)
    {
        if (level.getDifficulty() == Difficulty.PEACEFUL)
        {
            return false;
        }
        if (!isSewerSpot(level, pos))
        {
            return Monster.checkMonsterSpawnRules(type, level, reason, pos, random);
        }
        if (!Mob.checkMobSpawnRules(type, level, reason, pos, random) || level.getBrightness(LightLayer.SKY, pos) > random.nextInt(32))
        {
            return false;
        }
        return level.getBrightness(LightLayer.BLOCK, pos) <= RatKingTuning.RAT_SEWER_SPAWN_MAX_BLOCK_LIGHT;
    }

    /**
     * True if pos is inside the sewers: in the {@code dross:sewers} biome (Blightwood's underground), or, in the running
     * Dross, in a sewer network's piece (its tunnels, rooms and rat cave) that the sky can't see (so never the poisoned
     * ground round a grand entrance or the top of a pipe).
     */
    public static boolean isSewerSpot(ServerLevelAccessor level, BlockPos pos)
    {
        if (level.getBiome(pos).is(DrossBiomes.SEWERS))
        {
            return true;
        }
        return level instanceof ServerLevel server && server.dimension().equals(ModDimensions.DROSS_LEVEL) && !server.canSeeSky(pos)
                && Sewers.isInSewers(server, pos);
    }
}
