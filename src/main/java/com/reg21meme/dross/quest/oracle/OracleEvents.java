package com.reg21meme.dross.quest.oracle;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.quest.DrossAdvancements;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AdvancementEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Oracle quest's other events: a boss's advancement finishing its quest, sending each player their quest state
 * (login, respawn, a change of dimension), the fireworks' rockets (one a few ticks, never hurting anyone).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class OracleEvents
{
    private OracleEvents() {}

    /** The boss area grants a boss's advancement on the kill: that finishes the player's quest for it, if it's going. */
    @SubscribeEvent
    public static void onAdvancementEarned(AdvancementEvent.AdvancementEarnEvent event)
    {
        if (!(event.getEntity() instanceof ServerPlayer player))
        {
            return;
        }
        ResourceLocation id = event.getAdvancement().getId();
        OracleQuest quest = OracleProgress.current(player);
        if (quest.kind() == OracleQuest.Kind.BOSS && OracleProgress.state(player) == OracleState.ACTIVE
                && id.getNamespace().equals(Dross.MODID) && id.getPath().equals(quest.advancement()))
        {
            OracleProgress.bossBeaten(player);
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            // A boss quest going while its advancement came some other way (a command, an older version): it's done.
            OracleQuest quest = OracleProgress.current(player);
            if (quest.kind() == OracleQuest.Kind.BOSS && OracleProgress.state(player) == OracleState.ACTIVE
                    && DrossAdvancements.has(player, quest.advancement()))
            {
                OracleProgress.bossBeaten(player);
            }
            OracleProgress.sync(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            OracleProgress.sync(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event)
    {
        if (event.getEntity() instanceof ServerPlayer player)
        {
            OracleProgress.sync(player);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase == TickEvent.Phase.END)
        {
            OracleFireworks.tick(event.getServer());
        }
    }

    /** The Oracle's rockets never hurt anyone (a rocket bursting on the camp's roof would hit the player under it). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent event)
    {
        if (event.getSource().getDirectEntity() != null && OracleFireworks.isOurs(event.getSource().getDirectEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        OracleFireworks.clear();
    }
}
