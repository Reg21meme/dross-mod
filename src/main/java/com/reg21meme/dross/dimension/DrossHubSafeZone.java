package com.reg21meme.dross.dimension;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.portal.DrossHub;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The hub's safe zone: no <b>natural</b> hostile mob spawning near {@link DrossHub#CENTER}
 * in the Dross.
 *
 * <p>How it works: before vanilla spawns a mob naturally, it asks "may this mob type spawn
 * here?" ({@code SpawnPlacements.checkSpawnRules}), and Forge turns that question into
 * {@link MobSpawnEvent.SpawnPlacementCheck}. If the spot is in the Dross, inside the square
 * around the hub, and the mob is a monster spawning naturally, we answer "no" (DENY). The mob
 * is never created, so nothing else (like {@link DrossMobGear}) ever sees it.
 *
 * <p>Not blocked: spawners ({@code SPAWNER}), spawn eggs and {@code /summon} (they don't ask this
 * question as a natural spawn), and mobs that walk in from outside the zone.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DrossHubSafeZone
{
    /**
     * Half-width of the safe square, in blocks, measured from the hub center along X and Z.
     * 48 means no natural hostile spawns from CENTER-48 to CENTER+48 on both axes.
     */
    public static final int SAFE_ZONE_RADIUS = 48;

    private DrossHubSafeZone() {}

    // LOWEST priority so we run last and our DENY wins over another mod's ALLOW.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onSpawnPlacementCheck(MobSpawnEvent.SpawnPlacementCheck event)
    {
        MobSpawnType type = event.getSpawnType();
        // Only natural spawning: the regular spawn cycle and mobs placed when a chunk is first generated.
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION)
        {
            return;
        }
        // Only hostile mobs (zombies, skeletons, endermen, ...). Animals etc. are left alone.
        if (event.getEntityType().getCategory() != MobCategory.MONSTER)
        {
            return;
        }
        // getLevel() can be a world-gen region during chunk generation; getLevel().getLevel() is the real level.
        if (!event.getLevel().getLevel().dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return;
        }
        if (isInSafeZone(event.getPos()))
        {
            event.setResult(Event.Result.DENY);
        }
    }

    /** True if this position is inside the hub's safe square (X and Z only, height doesn't matter). */
    public static boolean isInSafeZone(BlockPos pos)
    {
        BlockPos center = DrossHub.CENTER;
        return Math.abs(pos.getX() - center.getX()) <= SAFE_ZONE_RADIUS
                && Math.abs(pos.getZ() - center.getZ()) <= SAFE_ZONE_RADIUS;
    }
}
