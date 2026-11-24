package com.reg21meme.dross.quest.trader;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

import java.util.Locale;

/**
 * One player's Dross Trader quest progress, saved inside their entry of the quest area's {@code QuestProgressData}
 * ({@code data/dross_quest.dat}, per player and per world). Anyone may read it; only this package changes it (through
 * {@link TraderProgress}, the only way to change progress). A save without any of this starts at his offer.
 */
public final class TraderRecord
{
    private static final String KEY_STATE = "TraderState";
    private static final String KEY_LINE = "TraderLine";
    private static final String KEY_RUINS = "TraderRuins";
    private static final String KEY_ROUND = "TraderRound";
    private static final String KEY_ORBS = "TraderOrbs";

    /** Where they are. */
    TraderState state = TraderState.OFFER;
    /** The next line he'll say in the current state's talk (so walking off mid-talk carries on from there). */
    int line;
    /** Ruins cleared for the first time in this round (towards {@link TraderProgress#RUINS_NEEDED}). */
    int ruins;
    /** The round of ruins: 1 for the first orb, 2 and on for a new one after a lost orb (0 before his offer is accepted). */
    int round;
    /** How many Portal Orbs he's made for them. */
    int orbs;

    /** A fresh record: his offer waiting. */
    public TraderRecord() {}

    public TraderState state()
    {
        return state;
    }

    public int line()
    {
        return line;
    }

    public int ruins()
    {
        return ruins;
    }

    public int round()
    {
        return round;
    }

    public int orbs()
    {
        return orbs;
    }

    /** True in a round of ruins for a new orb (one was made before and lost). */
    public boolean isReplacementRound()
    {
        return round > 1;
    }

    /** Reads a record from a player's entry (missing keys: his offer waiting). */
    public static TraderRecord read(CompoundTag entry)
    {
        TraderRecord record = new TraderRecord();
        if (entry.contains(KEY_STATE, Tag.TAG_STRING))
        {
            try
            {
                record.state = TraderState.valueOf(entry.getString(KEY_STATE).toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored)
            {
                // An unknown state (a newer save?): his offer again.
            }
        }
        record.line = Math.max(0, entry.getInt(KEY_LINE));
        record.ruins = Math.max(0, entry.getInt(KEY_RUINS));
        record.round = Math.max(0, entry.getInt(KEY_ROUND));
        record.orbs = Math.max(0, entry.getInt(KEY_ORBS));
        return record;
    }

    /** Writes it into a player's entry. */
    public void write(CompoundTag entry)
    {
        entry.putString(KEY_STATE, state.name().toLowerCase(Locale.ROOT));
        entry.putInt(KEY_LINE, line);
        entry.putInt(KEY_RUINS, ruins);
        entry.putInt(KEY_ROUND, round);
        entry.putInt(KEY_ORBS, orbs);
    }

    /** Back to his offer, nothing counted. */
    void clear()
    {
        state = TraderState.OFFER;
        line = 0;
        ruins = 0;
        round = 0;
        orbs = 0;
    }

    /** Changes the state and starts its lines from the beginning. */
    void enter(TraderState newState)
    {
        state = newState;
        line = 0;
    }
}
