package com.reg21meme.dross.boss.rage;

import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.BossEvent;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.monster.warden.Warden;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A plain boss bar over a raged Ender Dragon or warden (the user, October 31, 2026: "I wanna see the ender dragon health
 * bar now only when the rage wand is active, same with warden ... basic is fine"): Minecraft's own bar, its name and
 * health, shown to the players within {@link RageTuning#BAR_RANGE} while it's raged and alive. A dragon of the End's own
 * fight already has the End's bar and gets none of these. Server side.
 */
final class RageBars
{
    private RageBars() {}

    /** Each raged dragon's or warden's bar, and the game tick its mob last ticked (a mob whose land stops ticking, or
     *  that's unloaded, takes its bar off every screen: {@link #sweep}). */
    private static final Map<Mob, Bar> BARS = new HashMap<>();

    private static final class Bar
    {
        final ServerBossEvent event;
        long lastTick;

        Bar(ServerBossEvent event)
        {
            this.event = event;
        }
    }

    /** Whether this mob shows a bar while it's raged. */
    static boolean wants(Mob mob)
    {
        return mob instanceof Warden || (mob instanceof EnderDragon dragon && dragon.getDragonFight() == null);
    }

    /** A raged, living dragon or warden, every tick: its bar's health, and who sees it. */
    static void tick(Mob mob, ServerLevel level)
    {
        Bar bar = BARS.computeIfAbsent(mob, m -> new Bar(new ServerBossEvent(m.getDisplayName(),
                m instanceof EnderDragon ? RageTuning.BAR_COLOR_DRAGON : RageTuning.BAR_COLOR_WARDEN,
                BossEvent.BossBarOverlay.PROGRESS)));
        bar.lastTick = level.getGameTime();
        bar.event.setProgress(Mth.clamp(mob.getHealth() / mob.getMaxHealth(), 0.0F, 1.0F));
        if ((mob.tickCount + mob.getId()) % RageTuning.BAR_PLAYERS_EVERY != 0)
        {
            return;
        }
        double r = RageTuning.BAR_RANGE;
        for (ServerPlayer player : List.copyOf(bar.event.getPlayers()))
        {
            if (player.hasDisconnected() || player.level() != level || player.distanceToSqr(mob) > r * r)
            {
                bar.event.removePlayer(player);
            }
        }
        for (ServerPlayer player : level.players())
        {
            if (player.distanceToSqr(mob) <= r * r)
            {
                bar.event.addPlayer(player);
            }
        }
    }

    /** Every server tick: the bar of a mob that's gone, dead, no longer raged or not ticking any more leaves every
     *  screen (a bar never sticks on a screen). */
    static void sweep()
    {
        Iterator<Map.Entry<Mob, Bar>> it = BARS.entrySet().iterator();
        while (it.hasNext())
        {
            Map.Entry<Mob, Bar> entry = it.next();
            Mob mob = entry.getKey();
            Bar bar = entry.getValue();
            if (mob.isRemoved() || !mob.isAlive() || !Rage.isRaged(mob)
                    || mob.level().getGameTime() - bar.lastTick > RageTuning.BAR_STALE_TICKS)
            {
                bar.event.removeAllPlayers();
                it.remove();
            }
        }
    }

    /** The server stops: every bar off. */
    static void clear()
    {
        for (Bar bar : BARS.values())
        {
            bar.event.removeAllPlayers();
        }
        BARS.clear();
    }

    /** For the headless test: this mob's bar, if it has one. */
    static ServerBossEvent barOf(Mob mob)
    {
        Bar bar = BARS.get(mob);
        return bar == null ? null : bar.event;
    }
}
