package com.reg21meme.dross.boss.ratking;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gives the Rat King and his rats their attributes (their numbers are in {@link RatKingTuning}). Mod event bus. */
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
}
