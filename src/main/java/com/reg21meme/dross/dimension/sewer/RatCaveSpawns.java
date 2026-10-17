package com.reg21meme.dross.dimension.sewer;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Inside a rat cave only its own monsters spawn: the rat cave list from {@code dross:rat_cave}'s JSON
 * ({@code rat_cave_monsters}: rats only, worldgen.py's RAT_CAVE_* numbers). Everywhere else in the sewers the sewers
 * biome's list applies (rats only too); on Blightwood's surface, Blightwood's.
 * <p>
 * How: before Minecraft picks a mob to spawn naturally somewhere, Forge asks which mobs may spawn there
 * ({@link LevelEvent.PotentialSpawns}); inside a rat cave the answer is replaced by the cave's list. (The rats' own
 * spawn rules, such as needing darkness, are the boss area's.)
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RatCaveSpawns
{
    private RatCaveSpawns() {}

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPotentialSpawns(LevelEvent.PotentialSpawns event)
    {
        if (event.getMobCategory() != MobCategory.MONSTER || !(event.getLevel() instanceof ServerLevel level)
                || !level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return;
        }
        Optional<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOptional(Sewers.RAT_CAVE);
        if (structure.isEmpty() || !(structure.get() instanceof SewerStructure sewers))
        {
            return;
        }
        // (cheap away from rat caves: the chunk's list of structures reaching into it is empty)
        if (!level.structureManager().getStructureAt(event.getPos(), structure.get()).isValid() || !RatCaves.isInRatCave(level, event.getPos()))
        {
            return;
        }
        List<MobSpawnSettings.SpawnerData> cave = sewers.ratCaveMonsters().unwrap();
        for (MobSpawnSettings.SpawnerData data : new ArrayList<>(event.getSpawnerDataList()))
        {
            event.removeSpawnerData(data);
        }
        cave.forEach(event::addSpawnerData);
    }
}
