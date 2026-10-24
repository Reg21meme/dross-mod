package com.reg21meme.dross.boss.huntsman;

/**
 * Where the Huntsman and his Alpha Moose are in their fight ({@link HuntFight}); both keep their own copy, the same.
 */
public enum HuntStage
{
    /** Not fighting: the moose walks about with him in the saddle; the first hit starts the fight (the user: the horn
     * "when you first attack the duo"). */
    CALM,
    /** Stage 1: he rides it, one bar for both ({@link HuntsmanTuning#WILD_HUNT_HEALTH}). */
    WILD_HUNT,
    /** The split: the moose rears, he leaps off and blows his horn; neither can be hurt. */
    SPLIT,
    /** Stage 2: each on his own bar; when one dies the other is angry. */
    DUEL,
    /** Both beaten. */
    WON;

    /** In these stages damage to either goes on the one bar they share. */
    public boolean sharesBar()
    {
        return this == CALM || this == WILD_HUNT;
    }

    /** In these stages they're fighting. */
    public boolean fighting()
    {
        return this == WILD_HUNT || this == SPLIT || this == DUEL;
    }
}
