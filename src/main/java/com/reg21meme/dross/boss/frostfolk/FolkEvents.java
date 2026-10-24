package com.reg21meme.dross.boss.frostfolk;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gives every Frostfolk calling its attributes (mod bus). */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class FolkEvents
{
    private FolkEvents() {}

    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        ModEntities.FROSTFOLK.forEach((role, type) -> event.put(type.get(), Frostfolk.createAttributes(role).build()));
    }
}
