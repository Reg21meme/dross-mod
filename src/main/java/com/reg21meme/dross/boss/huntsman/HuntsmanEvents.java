package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gives the Huntsman and the Alpha Moose their attributes (mod bus). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class HuntsmanEvents
{
    private HuntsmanEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.HUNTSMAN.get(), Huntsman.createAttributes().build());
        event.put(ModEntities.ALPHA_MOOSE.get(), AlphaMoose.createAttributes().build());
    }
}
