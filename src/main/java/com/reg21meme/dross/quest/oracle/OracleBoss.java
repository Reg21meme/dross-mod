package com.reg21meme.dross.quest.oracle;

/**
 * The bosses a player may only fight once their Oracle quest has reached them ({@link OracleRules#mayFightBoss}).
 * Which quest that is comes from the quest list ({@link OracleQuests#questFor}), never a number kept here.
 * A new boss: add it here and give its BOSS quest in {@link OracleQuests} this constant.
 */
public enum OracleBoss
{
    /** The Cinder Colossus (quest "set_in_stone"). */
    COLOSSUS,
    /** The Rat King (quest "down_the_drain"). */
    RAT_KING,
    /** The Huntsman and his Alpha Moose (quest "the_hunter_hunted"). */
    HUNTSMAN,
    /** The Emberdactyl and its young (quest "wings_clipped"). */
    EMBERDACTYL;

    /** The quest that has to be reached before this boss fights the player. */
    public OracleQuest quest()
    {
        return OracleQuests.questFor(this);
    }
}
