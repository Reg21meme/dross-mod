package com.reg21meme.dross.boss.knight.ruin;

import com.reg21meme.dross.Dross;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The Nether's knight ruins looked after (PRE_DROSS_PLAN.md, stage T3), exactly as the Overworld's are
 * ({@link KnightRuinKeeper#keep}: the tidy-up as it comes into view, its two knights when a player is near, back
 * {@code knightsReturnMinutes} after both are beaten only with nobody within 64 blocks, never on Peaceful; the cleared
 * signal, {@link KnightRuinClearedEvent}, and the saved clearers). This class starts the Nether's planning when it loads
 * ({@link NetherRuinSites#register}), keeps the squares round its players worked out ahead of them in the background,
 * and every couple of seconds looks after the ruins near them.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class NetherRuinKeeper
{
    private NetherRuinKeeper() {}

    @SubscribeEvent
    public static void onLevelLoad(LevelEvent.Load event)
    {
        if (event.getLevel() instanceof ServerLevel level && level.dimension() == Level.NETHER)
        {
            NetherRuinSites.register(level);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.NETHER)
        {
            return;
        }
        if (level.getServer() instanceof net.minecraft.gametest.framework.GameTestServer)
        {
            return; // (the test server: its tests drive the keeper themselves, as for the Overworld's ruins)
        }
        long t = level.getGameTime();
        boolean warm = t % KnightRuinTuning.WARM_EVERY == 0;
        boolean keep = t % KnightRuinTuning.KEEPER_EVERY == 0;
        if ((!warm && !keep) || level.players().isEmpty() || !NetherRuinSites.available(level))
        {
            return;
        }
        Set<String> done = new HashSet<>();
        for (ServerPlayer player : level.players())
        {
            if (warm)
            {
                NetherRuinSites.warmAround(level, player.getBlockX(), player.getBlockZ(), KnightRuinTuning.WARM_AHEAD);
            }
            if (keep && !player.isSpectator())
            {
                for (RuinSite site : NetherRuinSites.readyNear(level, player.getBlockX(), player.getBlockZ(), KnightRuinTuning.TIDY_RANGE + 32))
                {
                    if (done.add(site.id()))
                    {
                        KnightRuinKeeper.keep(level, site);
                    }
                }
            }
        }
    }
}
