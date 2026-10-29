package com.reg21meme.dross.boss.emberdactyl;

import com.reg21meme.dross.boss.emberdactyl.trophy.EmberdactylTrophies;
import net.minecraft.server.level.ServerPlayer;

/**
 * The Emberdactyl's trophy, the reward for beating its young (the user's plan: a trophy like the other bosses', its poses
 * to cycle). The fifteen candidate designs are on show ({@code /dross showcase trophies emberdactyl}, the trophy block
 * {@code dross:emberdactyl_trophy} in {@code boss/emberdactyl/trophy/}); until the user picks, beating it gives
 * {@link #DROP_DESIGN}. Any trophy can be turned to any pose with a right-click.
 */
public final class EmberTrophies
{
    /** The design each player who helped gets (1: The Sky That Burns, until the user picks). */
    public static final int DROP_DESIGN = 1;

    private EmberTrophies() {}

    /**
     * Gives a player who helped beat the young their trophy: straight into their inventory, dropped a block in front of
     * them with a full inventory, or where they died for a dead player; a dropped one is theirs alone and can't be
     * destroyed (the shared {@code TrophyRewards}, like the other bosses').
     */
    public static void reward(ServerPlayer player)
    {
        EmberdactylTrophies.giveTo(player, DROP_DESIGN);
    }
}
