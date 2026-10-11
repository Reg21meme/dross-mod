package com.reg21meme.dross.villager;

import com.reg21meme.dross.Dross;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraftforge.event.entity.living.LivingChangeTargetEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Hostile mobs ignore the hub's survivors: whenever a hostile mob ({@link Enemy}: zombies, skeletons, piglins, the
 * Cinder Colossus...) is about to pick a survivor as its target, that's cancelled and it keeps the target it had. This
 * covers both ways a mob picks a target: {@code Mob.setTarget} (most mobs) and the brain's attack behaviour (piglins,
 * hoglins). It runs last, so it also sees a target another mod's handler swapped in.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class SurvivorEvents
{
    private SurvivorEvents() {}

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onChangeTarget(LivingChangeTargetEvent event)
    {
        if (event.getNewTarget() instanceof Survivor && event.getEntity() instanceof Enemy)
        {
            event.setCanceled(true);
        }
    }
}
