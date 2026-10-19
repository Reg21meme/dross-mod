package com.reg21meme.dross.boss.rimefall;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gives the Rimefall candidates their attributes (mod bus). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RimefallEvents
{
    private RimefallEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.RIMEFALL_CANDIDATE.get(), RimefallCandidate.createAttributes().build());
    }
}
