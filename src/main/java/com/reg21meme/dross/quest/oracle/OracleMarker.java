package com.reg21meme.dross.quest.oracle;

/**
 * The marker a player sees over the Blind Oracle, and over the Dross Trader for his quest (each player their own; the
 * villager area's {@code QuestMarkerRenderer} draws them from {@code quest.client.OracleClientState.marker()} and
 * {@code traderMarker()}).
 */
public enum OracleMarker
{
    /** Nothing: every quest is done. */
    NONE,
    /** A red ?: a new quest is waiting, talk to him. */
    NEW,
    /** A yellow ...: the quest is going. */
    ACTIVE,
    /** A green !: it's done, go back to him. */
    DONE;

    /** The marker for a quest state. */
    public static OracleMarker of(OracleState state)
    {
        return switch (state)
        {
            case AVAILABLE -> NEW;
            case ACTIVE -> ACTIVE;
            case DONE -> DONE;
            case FINISHED -> NONE;
        };
    }
}
