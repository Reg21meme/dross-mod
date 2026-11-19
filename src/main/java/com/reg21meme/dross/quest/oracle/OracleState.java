package com.reg21meme.dross.quest.oracle;

/** Where a player is in their current Oracle quest. */
public enum OracleState
{
    /** Waiting to be given: right-clicking the Oracle gives its offer lines (red ? over him). */
    AVAILABLE,
    /** Accepted and going (yellow ... over him): kills count, a boss's advancement finishes it. */
    ACTIVE,
    /** Done: go back to him for his thanks (green ! over him). */
    DONE,
    /** Every quest is done ("Defeat the Dross"): he's back to his old lore lines. */
    FINISHED
}
