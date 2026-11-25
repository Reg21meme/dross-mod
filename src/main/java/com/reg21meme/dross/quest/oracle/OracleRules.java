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
 * in "The Forest Giants" opens the Cinder Wastes and the Gravecrags (his spell at that hand-in); the spell lines in the
 * offers of "Beneath the Rot" and "First Frost" open Blightwood (and its sewers) and Rimefall; Silverwood and every other
 * land are always open. A boss fights a player once the spell line of its own quest's offer has broken its ward
 * ({@link OracleProgress#opened}, {@link OracleSpell}).</p>
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
        return OracleProgress.opened(serverPlayer, opener);
    }

    /**
     * True if this player's Oracle quest has opened this land, whatever their game mode (a land no quest locks: true).
     * For the barrier falling, which creative players see too (the user, November 19, 2026, who tests in creative).
     */
    public static boolean isLandOpenByQuest(ServerPlayer player, ResourceKey<Biome> biome)
    {
        OracleQuest opener = OracleQuests.openerOf(biome);
        return opener == null || OracleProgress.opened(player, opener);
    }

    /**
     * True if this boss may fight this player (the boss area's locks): the Oracle has broken the ward on its home for them
     * (the spell line of the boss's own quest's offer). Pass a server player (any other is answered "allowed").
     */
    public static boolean mayFightBoss(Player player, OracleBoss boss)
    {
        if (exempt(player) || !(player instanceof ServerPlayer serverPlayer))
        {
            return true;
        }
        return OracleProgress.opened(serverPlayer, boss.quest());
    }

    /** Creative and spectator players are never locked out of anything. */
    public static boolean exempt(Player player)
    {
        return player.isCreative() || player.isSpectator();
    }
}
