package com.reg21meme.dross.quest.oracle;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.biome.Biome;

import java.util.List;

import java.util.Locale;

/**
 * One player's Oracle quest progress, saved inside their entry of the quest area's {@code QuestProgressData}
 * ({@code data/dross_quest.dat}, per player and per world). Anyone may read it; only this package changes it (through
 * {@link OracleProgress}, the only way to change progress).
 *
 * <p>The quest is saved by its id (and its index as a fallback), so inserting a quest in the list later keeps saves
 * pointing at the right one. An old save without any of this starts at the first quest, AVAILABLE.</p>
 */
public final class OracleRecord
{
    private static final String KEY_QUEST = "OracleQuest";
    private static final String KEY_INDEX = "OracleIndex";
    private static final String KEY_STATE = "OracleState";
    private static final String KEY_KILLS = "OracleKills";
    private static final String KEY_LINE = "OracleLine";
    private static final String KEY_FALLEN = "OracleFallenLands";

    /** The current quest's place in {@link OracleQuests#ALL}. */
    int index;
    /** Where they are in it. */
    OracleState state = OracleState.AVAILABLE;
    /** Kills counted for the current KILL quest. */
    int kills;
    /** The next line he'll say in the current offer or thanks (so walking off mid-talk carries on from there). */
    int line;
    /**
     * The lands whose barrier they've seen fall, a bit each by {@link OracleQuests#lockableLands()} (saved by name, so a
     * new land in the list never shifts them).
     */
    int fallen;

    /** A fresh record: the first quest, AVAILABLE. */
    public OracleRecord() {}

    public int index()
    {
        return index;
    }

    public OracleQuest quest()
    {
        return OracleQuests.get(index);
    }

    public OracleState state()
    {
        return state;
    }

    public int kills()
    {
        return kills;
    }

    public int line()
    {
        return line;
    }

    /** Reads a record from a player's entry (missing keys: the first quest, AVAILABLE). */
    public static OracleRecord read(CompoundTag entry)
    {
        OracleRecord record = new OracleRecord();
        OracleQuest quest = entry.contains(KEY_QUEST, Tag.TAG_STRING) ? OracleQuests.byId(entry.getString(KEY_QUEST)) : null;
        record.index = quest != null ? quest.index() : Math.max(0, Math.min(OracleQuests.ALL.size() - 1, entry.getInt(KEY_INDEX)));
        record.state = OracleState.AVAILABLE;
        if (entry.contains(KEY_STATE, Tag.TAG_STRING))
        {
            try
            {
                record.state = OracleState.valueOf(entry.getString(KEY_STATE).toUpperCase(Locale.ROOT));
            }
            catch (IllegalArgumentException ignored)
            {
                // An unknown state (a newer save?): start this quest again.
            }
        }
        record.kills = Math.max(0, entry.getInt(KEY_KILLS));
        record.line = Math.max(0, entry.getInt(KEY_LINE));
        if (entry.contains(KEY_FALLEN, Tag.TAG_LIST))
        {
            ListTag names = entry.getList(KEY_FALLEN, Tag.TAG_STRING);
            for (int i = 0; i < names.size(); i++)
            {
                int bit = landBit(names.getString(i));
                if (bit >= 0)
                {
                    record.fallen |= 1 << bit;
                }
            }
        }
        return record;
    }

    /** Writes it into a player's entry. */
    public void write(CompoundTag entry)
    {
        entry.putString(KEY_QUEST, quest().id());
        entry.putInt(KEY_INDEX, index);
        entry.putString(KEY_STATE, state.name().toLowerCase(Locale.ROOT));
        entry.putInt(KEY_KILLS, kills);
        entry.putInt(KEY_LINE, line);
        ListTag names = new ListTag();
        List<ResourceKey<Biome>> lands = OracleQuests.lockableLands();
        for (int i = 0; i < lands.size(); i++)
        {
            if ((fallen & (1 << i)) != 0)
            {
                names.add(StringTag.valueOf(lands.get(i).location().toString()));
            }
        }
        entry.put(KEY_FALLEN, names);
    }

    public int fallen()
    {
        return fallen;
    }

    /** The bit of the lockable land with this name, or -1. */
    private static int landBit(String name)
    {
        List<ResourceKey<Biome>> lands = OracleQuests.lockableLands();
        for (int i = 0; i < lands.size(); i++)
        {
            if (lands.get(i).location().toString().equals(name))
            {
                return i;
            }
        }
        return -1;
    }

    /** Back to the first quest, AVAILABLE. */
    void clear()
    {
        fallen = 0;
        index = 0;
        state = OracleState.AVAILABLE;
        kills = 0;
        line = 0;
    }

    /** Starts the quest at this index, AVAILABLE, with nothing counted. */
    void moveTo(int newIndex)
    {
        index = newIndex;
        state = OracleState.AVAILABLE;
        kills = 0;
        line = 0;
    }

    /** Changes the state and starts its lines from the beginning. */
    void enter(OracleState newState)
    {
        state = newState;
        line = 0;
    }
}
