package com.reg21meme.dross.boss.knight;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gives the elemental knights their attributes, on show and the real ones (mod bus). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KnightEvents
{
    private KnightEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.KNIGHT_CANDIDATE.get(), KnightCandidate.createAttributes().build());
        ModEntities.KNIGHTS.forEach((kind, type) -> event.put(type.get(), ElementalKnight.createAttributes(kind).build()));
    }
}
