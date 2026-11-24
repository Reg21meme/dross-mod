package com.reg21meme.dross.quest.trader;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.knight.ruin.KnightRuinClearedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The trader quest's events: a knight ruin cleared (the boss area's {@link KnightRuinClearedEvent}, once for each player
 * who cleared it) counts for his quest the first time that player clears that ruin. (Each player's markers are sent on
 * login, respawn and a change of dimension by the Oracle's events: one message carries both.)
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class TraderQuestEvents
{
    private TraderQuestEvents() {}

    @SubscribeEvent
    public static void onRuinCleared(KnightRuinClearedEvent event)
    {
        TraderProgress.ruinCleared(event.getPlayer(), event.isFirstTime());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        TraderDialogue.forgetSearches();
    }
}
