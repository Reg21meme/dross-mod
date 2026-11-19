package com.reg21meme.dross.quest.oracle;

import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.biome.Biome;

/**
 * The rules other areas ask about a player's Oracle quest (SERVER side; a player's game has
 * {@code quest.client.OracleClientState} for the same answers). Players in creative or spectator mode are always exempt:
 * every land open, every boss allowed.
 *
 * <p>Which quest opens what comes from the quest list ({@link OracleQuests}), never a number kept here: today handing
 * in "The Forest Giants" opens the Cinder Wastes and the Gravecrags, "The Cinder Colossus" Blightwood and its sewers,
 * "The Rat King" Rimefall; Silverwood and every other land are always open. A boss fights a player once the quest
 * before its own is handed in (its own is AVAILABLE or further on). Each of those hand-ins ends with the Oracle's spell
 * ({@link OracleSpell}).</p>
 */
public final class OracleRules
{
    private OracleRules() {}

    /**
     * True if this land is open for this player (the dimension area's wards). A land no quest locks is always open.
     * Pass a server player: a player's game asks {@code OracleClientState.isLandOpen} instead (here any other player is
     * answered "open").
     */
    public static boolean isLandOpen(Player player, ResourceKey<Biome> biome)
    {
        if (exempt(player))
        {
            return true;
        }
        OracleQuest opener = OracleQuests.openerOf(biome);
        if (opener == null || !(player instanceof ServerPlayer serverPlayer))
        {
            return true;
        }
        return OracleProgress.hasHandedIn(serverPlayer, opener);
    }

    /**
     * True if this boss may fight this player (the boss area's locks): they've handed in the quest before the boss's own
     * (the Oracle broke the ward on its home). Pass a server player (any other is answered "allowed").
     */
    public static boolean mayFightBoss(Player player, OracleBoss boss)
    {
        if (exempt(player) || !(player instanceof ServerPlayer serverPlayer))
        {
            return true;
        }
        return OracleProgress.isAtOrPast(serverPlayer, boss.quest());
    }

    /** Creative and spectator players are never locked out of anything. */
    public static boolean exempt(Player player)
    {
        return player.isCreative() || player.isSpectator();
    }
}
