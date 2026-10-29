package com.reg21meme.dross.boss.emberdactyl;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** The Emberdactyl's, its young's and its egg's attributes (mod event bus). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EmberSetupEvents
{
    private EmberSetupEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.EMBERDACTYL.get(), Emberdactyl.createAttributes().build());
        event.put(ModEntities.EMBERYOUNG.get(), EmberYoung.createAttributes().build());
        event.put(ModEntities.EMBERDACTYL_EGG.get(), EmberEgg.createAttributes().build());
    }
}
