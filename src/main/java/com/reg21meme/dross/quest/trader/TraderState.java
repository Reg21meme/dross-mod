package com.reg21meme.dross.quest.trader;

import com.reg21meme.dross.quest.oracle.OracleMarker;

/** Where a player is in the Dross Trader's quest (PRE_DROSS_PLAN.md, "His quests"). */
public enum TraderState
{
    /** His offer is waiting (red ? over him): his story a line a click, then "will you help?", then your answer. */
    OFFER,
    /** Clearing knight ruins (yellow ... over him): a ruin you clear for the first time counts. */
    RUINS,
    /** Enough ruins cleared (green ! over him): go back to him for the Portal Orb. */
    READY,
    /**
     * He's made you an orb (nothing over him): a click reminds you where the cathedral is; a lost orb starts a new round
     * of ruins ({@link #RUINS} again); once you've been to the Dross, his lines for an old friend. His shop is open.
     */
    ORB_GIVEN;

    /** The marker over him for this state (the same markers as the Blind Oracle's). */
    public OracleMarker marker()
    {
        return switch (this)
        {
            case OFFER -> OracleMarker.NEW;
            case RUINS -> OracleMarker.ACTIVE;
            case READY -> OracleMarker.DONE;
            case ORB_GIVEN -> OracleMarker.NONE;
        };
    }
}
