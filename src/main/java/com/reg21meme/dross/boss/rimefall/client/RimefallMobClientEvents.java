package com.reg21meme.dross.boss.rimefall.client;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.rimefall.Moose;
import com.reg21meme.dross.boss.rimefall.PondSkater;
import com.reg21meme.dross.boss.rimefall.Rimehorn;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;
import java.util.Set;

/**
 * Registers the renderers of Rimefall's animals (client only): the Rimehorn (a male's or a female's coat; males and
 * calves hide the snow on a female's back; clear ice horns), the moose (cows and calves hide the antlers) and the Pond
 * Skater.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class RimefallMobClientEvents
{
    /** A calf's size (its head is drawn bigger: the second number), Rimehorn and moose (tools/rimefall/rimemobs/). */
    private static final float RIMEHORN_CALF = 0.42F;
    private static final float RIMEHORN_CALF_HEAD = 1.45F;
    private static final float MOOSE_CALF = 0.45F;
    private static final float MOOSE_CALF_HEAD = 1.4F;
    private static final Set<String> SNOW = Set.of("snow");
    private static final Set<String> ANTLERS = Set.of("antlers");

    private RimefallMobClientEvents() {}

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event)
    {
        event.registerEntityRenderer(ModEntities.RIMEHORN.get(), context -> new RimefallMobRenderer<>(context,
                new RimefallMobModel<Rimehorn>(Rimehorn.MOB, r -> r.isMale() ? "male" : "female", List.of("snow"),
                        r -> r.isMale() || r.isBaby() ? SNOW : Set.of(), RIMEHORN_CALF_HEAD),
                2.0F, RIMEHORN_CALF, true));
        event.registerEntityRenderer(ModEntities.MOOSE.get(), context -> new RimefallMobRenderer<>(context,
                new RimefallMobModel<Moose>(Moose.MOB, m -> m.isSnowy() ? "moose" : "moose_plain", List.of("antlers"),
                        m -> m.isBull() && !m.isBaby() ? Set.of() : ANTLERS, MOOSE_CALF_HEAD),
                1.5F, MOOSE_CALF, false));
        event.registerEntityRenderer(ModEntities.POND_SKATER.get(), context -> new RimefallMobRenderer<>(context,
                new RimefallMobModel<PondSkater>(PondSkater.MOB, s -> "pondskater", List.of(), s -> Set.of(), 1.0F),
                1.2F, 1.0F, false));
    }
}
