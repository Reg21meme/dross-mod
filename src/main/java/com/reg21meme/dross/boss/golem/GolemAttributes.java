package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Gives the Cinder Colossus its attributes (health, armour...). Runs on the mod event bus. */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class GolemAttributes
{
    @SubscribeEvent
    public static void onAttributes(EntityAttributeCreationEvent event)
    {
        event.put(ModEntities.CINDER_COLOSSUS.get(), CinderColossus.createAttributes().build());
    }
}
