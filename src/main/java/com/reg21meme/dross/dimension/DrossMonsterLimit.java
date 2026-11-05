package com.reg21meme.dross.dimension;

import com.reg21meme.dross.Dross;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * A lower monster limit in Silverwood Forest and the Gravecrags (the user, November 5, 2026, after playing 0.3.0: the
 * netherite zombies and skeletons were "like they are invading dimensions": "a 75% nerf", "specifically limit Silverwood
 * Forest and the Gravecrags for now, as we will return that number once we get actual mobs for them").
 *
 * <p>Minecraft keeps up to 70 monsters round each player and spawns more whenever there are fewer; in these two biomes
 * almost all of them are the netherite zombies and skeletons (96% of their list). Skipping some of their spawns would only
 * slow the filling up (monsters near a player never despawn), so instead: a monster spawns naturally in one of these two
 * biomes only while fewer than {@link #LIMIT} monsters are within {@link #RADIUS} blocks of the spot, a quarter of the
 * usual crowd. The other biomes keep the normal limit. Spawners, spawn eggs and commands aren't natural spawns: never
 * stopped. Monsters that wander in from elsewhere still come.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DrossMonsterLimit
{
    /** Natural monsters allowed near a spot in Silverwood Forest or the Gravecrags: a quarter of vanilla's 70. */
    public static final int LIMIT = 18;
    /** How far round the spot they're counted (vanilla's limit counts the land within about 128 blocks of a player). */
    public static final int RADIUS = 128;

    /** The monsters' positions, worked out once a tick (a spawn pass asks many times in one tick). */
    private static long countedAt = Long.MIN_VALUE;
    private static ServerLevel countedIn;
    private static final List<double[]> MONSTERS = new ArrayList<>();

    private DrossMonsterLimit() {}

    // LOWEST priority, like the hub's safe zone: our DENY wins over another mod's ALLOW.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event)
    {
        if (event.getSpawnType() != MobSpawnType.NATURAL || event.getEntityType().getCategory() != MobCategory.MONSTER)
        {
            return;
        }
        ServerLevel level = event.getLevel().getLevel();
        if (!level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return;
        }
        BlockPos pos = event.getPos();
        var biome = event.getLevel().getBiome(pos);
        if (!biome.is(DrossBiomes.SILVERWOOD_FOREST) && !biome.is(DrossBiomes.GRAVECRAGS))
        {
            return;
        }
        if (nearby(level, pos.getX() + 0.5D, pos.getZ() + 0.5D) >= LIMIT)
        {
            event.setResult(Event.Result.DENY);
        }
    }

    /** How many natural monsters are within {@link #RADIUS} blocks (sideways) of (x, z) in this level. */
    public static int nearby(ServerLevel level, double x, double z)
    {
        long now = level.getGameTime();
        if (now != countedAt || level != countedIn)
        {
            countedAt = now;
            countedIn = level;
            MONSTERS.clear();
            for (Entity e : level.getAllEntities())
            {
                if (e instanceof Mob mob && mob.isAlive() && mob.getType().getCategory() == MobCategory.MONSTER
                        && !mob.isPersistenceRequired())
                {
                    MONSTERS.add(new double[] {mob.getX(), mob.getZ()});
                }
            }
        }
        int count = 0;
        double r2 = (double) RADIUS * RADIUS;
        for (double[] m : MONSTERS)
        {
            double dx = m[0] - x;
            double dz = m[1] - z;
            if (dx * dx + dz * dz <= r2)
            {
                count++;
            }
        }
        return count;
    }
}
