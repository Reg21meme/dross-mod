package com.reg21meme.dross.boss.ratking.lair;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Remembers (in the world save) each rat cave's Rat King: which one he is, whether he's awake, and whether (and when)
 * he was beaten: a beaten cave stays empty for good (no respawn, the user's decision of October 16). Saved as
 * {@code data/dross_rat_king_lairs.dat} in the Overworld's save folder (the Overworld is always loaded), like the Colossus
 * lairs' data. A cave is known by its id ({@code RatCaves.RatCave.id()}, its sewer network's first chunk), which never
 * changes.
 * <p>
 * {@code Caves}: a list of {@code Id} (the cave's id), {@code King} (his UUID, while he exists), {@code Awake} (he has
 * been woken), {@code Beaten} (true for good once he's beaten), {@code DefeatedAt} (the Overworld's game
 * time he was beaten), {@code Appearances} (how many kings this cave has had). A cave nobody has been near has no entry.
 * Server thread only.
 */
public final class RatKingLairData extends SavedData
{
    private static final String NAME = "dross_rat_king_lairs";

    private final Map<Long, Entry> caves = new HashMap<>();

    /** One cave's king. */
    private static final class Entry
    {
        @Nullable
        UUID king;
        boolean awake;
        boolean beaten;
        long defeatedAt;
        int appearances;
    }

    static RatKingLairData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(RatKingLairData::load, RatKingLairData::new, NAME);
    }

    private static RatKingLairData load(CompoundTag tag)
    {
        RatKingLairData data = new RatKingLairData();
        ListTag list = tag.getList("Caves", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag cave = list.getCompound(i);
            Entry entry = new Entry();
            entry.king = cave.hasUUID("King") ? cave.getUUID("King") : null;
            entry.awake = cave.getBoolean("Awake");
            entry.beaten = cave.getBoolean("Beaten");
            entry.defeatedAt = cave.getLong("DefeatedAt");
            entry.appearances = cave.getInt("Appearances");
            data.caves.put(cave.getLong("Id"), entry);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Entry> e : this.caves.entrySet())
        {
            Entry entry = e.getValue();
            CompoundTag cave = new CompoundTag();
            cave.putLong("Id", e.getKey());
            if (entry.king != null)
            {
                cave.putUUID("King", entry.king);
            }
            cave.putBoolean("Awake", entry.awake);
            cave.putBoolean("Beaten", entry.beaten);
            cave.putLong("DefeatedAt", entry.defeatedAt);
            cave.putInt("Appearances", entry.appearances);
            list.add(cave);
        }
        tag.put("Caves", list);
        return tag;
    }

    private Entry entry(long cave)
    {
        return this.caves.computeIfAbsent(cave, id -> new Entry());
    }

    @Nullable
    private Entry find(long cave)
    {
        return this.caves.get(cave);
    }

    // ---------------------------------------------------------------- reading

    /** The cave's Rat King (living, lying dead, or not loaded), or null if it has none now. */
    @Nullable
    UUID king(long cave)
    {
        Entry entry = find(cave);
        return entry == null ? null : entry.king;
    }

    /** True while the cave's king has been woken (and still exists). */
    boolean awake(long cave)
    {
        Entry entry = find(cave);
        return entry != null && entry.king != null && entry.awake;
    }

    /** True for good once the cave's king has been beaten (no respawn). */
    boolean beaten(long cave)
    {
        Entry entry = find(cave);
        return entry != null && entry.beaten;
    }

    /** The game time the cave's king was last beaten (only meaningful while {@link #beaten}). */
    long defeatedAt(long cave)
    {
        Entry entry = find(cave);
        return entry == null ? 0L : entry.defeatedAt;
    }

    /** How many kings this cave has had. */
    int appearances(long cave)
    {
        Entry entry = find(cave);
        return entry == null ? 0 : entry.appearances;
    }


    // ---------------------------------------------------------------- changes

    /** A new king has appeared in the cave: he's its king now, asleep, not beaten. */
    void setKing(long cave, UUID king)
    {
        Entry entry = entry(cave);
        entry.king = king;
        entry.awake = false;
        entry.beaten = false;
        entry.appearances++;
        setDirty();
    }

    /** A king of the cave who had been forgotten (carried off, unloaded) turned up again before a new one appeared: he's its king again. */
    void adoptKing(long cave, UUID king)
    {
        entry(cave).king = king;
        setDirty();
    }

    /** Notes whether the cave's king is awake (only marks the data changed when it changes). */
    void noteAwake(long cave, boolean awake)
    {
        Entry entry = find(cave);
        if (entry != null && entry.king != null && entry.awake != awake)
        {
            entry.awake = awake;
            setDirty();
        }
    }

    /** The cave's king was beaten at {@code gameTime}. */
    void recordDefeat(long cave, long gameTime)
    {
        Entry entry = entry(cave);
        entry.beaten = true;
        entry.awake = false;
        entry.defeatedAt = gameTime;
        setDirty();
    }

    /** The cave's king is gone (his body vanished, or he was removed): only the time he was beaten is kept. */
    void forgetKing(long cave)
    {
        Entry entry = find(cave);
        if (entry != null && entry.king != null)
        {
            entry.king = null;
            entry.awake = false;
            setDirty();
        }
    }

    /** Tests: as if the cave's king was beaten at {@code gameTime} (long ago: a long wait faked). It may be before the world began. */
    void setDefeatedAtForTests(long cave, long gameTime)
    {
        Entry entry = entry(cave);
        entry.beaten = true;
        entry.defeatedAt = gameTime;
        setDirty();
    }

    /** Tests: the cave as if nobody had ever been near it (no king, never beaten). */
    void resetForTests(long cave)
    {
        if (this.caves.remove(cave) != null)
        {
            setDirty();
        }
    }
}
