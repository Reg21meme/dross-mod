package com.reg21meme.dross.boss.cinder;

import com.reg21meme.dross.Dross;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Cinder Wastes' mobs coming into the world by themselves (Forge bus): natural spawning only made room for a small
 * box ({@code ModEntities}), so each is settled where its whole body fits ({@link CinderMob#settle}: a few blocks over or
 * a little up); where it can't be, that spawn doesn't happen (the user's shares by count come out near what they asked
 * for, and none comes half inside a bank). Spawn eggs and commands are only settled, never stopped.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class CinderMobEvents
{
    private CinderMobEvents() {}

    @SubscribeEvent
    public static void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event)
    {
        MobSpawnType reason = event.getSpawnType();
        if (event.getEntity() instanceof CinderMob mob && (reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION)
                && !mob.settle(event.getLevel()))
        {
            event.setSpawnCancelled(true);
        }
    }
}
