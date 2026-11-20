package com.reg21meme.dross.boss.knight.ruin;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.eventbus.api.Event;

/**
 * Posted on Forge's event bus when a knight ruin's second knight falls, once for each player who cleared it: the player
 * credited with that knight's death and every player (not a spectator) within {@link KnightRuinTuning#CREDIT_RANGE}
 * blocks of the ruin (the user, November 19, 2026: the quest that counts them is built separately; "your job is to place
 * the things for the quest builder later on"). Again each time the ruin's knights come back and are beaten again
 * ({@link #isFirstTime} tells the player's first clearing of that ruin). Any death counts (a {@code /kill} too, as at
 * the bosses' lairs); {@link #isKilledBy} tells the player credited with the last one.
 */
public final class KnightRuinClearedEvent extends Event
{
    private final ServerPlayer player;
    private final RuinSite ruin;
    private final boolean firstTime;
    private final boolean killedBy;

    public KnightRuinClearedEvent(ServerPlayer player, RuinSite ruin, boolean firstTime, boolean killedBy)
    {
        this.player = player;
        this.ruin = ruin;
        this.firstTime = firstTime;
        this.killedBy = killedBy;
    }

    public ServerPlayer getPlayer()
    {
        return this.player;
    }

    public RuinSite getRuin()
    {
        return this.ruin;
    }

    /** True the first time this player has cleared this ruin. */
    public boolean isFirstTime()
    {
        return this.firstTime;
    }

    /** True if this player is the one credited with the last knight's death (else they were near). */
    public boolean isKilledBy()
    {
        return this.killedBy;
    }
}
