package com.reg21meme.dross.villager;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gives the trader his attributes (health, speed). Runs on the mod event bus. */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class TraderAttributes
{
    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.TRADER.get(), DrossTrader.createAttributes().build());
    }
}
