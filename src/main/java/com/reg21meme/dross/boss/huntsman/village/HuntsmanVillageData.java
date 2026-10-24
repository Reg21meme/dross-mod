package com.reg21meme.dross.boss.huntsman.village;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * What became of the Huntsman of each of his villages (saved with the Dross: {@code data/dross_huntsman_villages.dat}):
 * the pair now there (the Huntsman's UUID) and which of the village's pairs it is, and whether he's been beaten there
 * (then the village stays empty for good, like the other bosses' lairs).
 */
public final class HuntsmanVillageData extends SavedData
{
    private static final String NAME = "dross_huntsman_villages";

    /** One village's record. */
    public static final class Entry
    {
        @Nullable
        UUID huntsman;
        int generation;
        boolean beaten;

        public boolean beaten()
        {
            return this.beaten;
        }


        @Nullable
        public UUID huntsman()
        {
            return this.huntsman;
        }

        public int generation()
        {
            return this.generation;
        }
    }

    private final Map<Long, Entry> villages = new HashMap<>();

    public static HuntsmanVillageData get(ServerLevel dross)
    {
        return dross.getDataStorage().computeIfAbsent(HuntsmanVillageData::load, HuntsmanVillageData::new, NAME);
    }

    /** The village's record (a new, empty one if it has none). */
    public Entry entry(long village)
    {
        return this.villages.computeIfAbsent(village, v -> new Entry());
    }

    /** The village's record, or null if nothing has happened there yet. */
    @Nullable
    public Entry find(long village)
    {
        return this.villages.get(village);
    }

    /** A new pair came to the village: its Huntsman, its number among the village's pairs. */
    public int spawned(long village, UUID huntsman)
    {
        Entry e = this.entry(village);
        e.huntsman = huntsman;
        e.generation++;
        this.setDirty();
        return e.generation;
    }

    /** The village's Huntsman is now {@code huntsman} (a broken-off fight brought him back), the same pair. */
    public void replaced(long village, UUID huntsman)
    {
        this.entry(village).huntsman = huntsman;
        this.setDirty();
    }

    /** The pair is gone without being beaten (removed on Peaceful): the next player near finds a new one. */
    public void gone(long village)
    {
        Entry e = this.entry(village);
        e.huntsman = null;
        this.setDirty();
    }

    /** Both of them died: he's beaten there, for good. */
    public void beaten(long village)
    {
        Entry e = this.entry(village);
        e.beaten = true;
        e.huntsman = null;
        this.setDirty();
    }

    /** Forgets the village's record, as if nothing had happened there (the headless test starts afresh with it). */
    void forget(long village)
    {
        this.villages.remove(village);
        this.setDirty();
    }

    private static HuntsmanVillageData load(CompoundTag tag)
    {
        HuntsmanVillageData data = new HuntsmanVillageData();
        ListTag list = tag.getList("Villages", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag v = list.getCompound(i);
            Entry e = data.entry(v.getLong("Id"));
            e.huntsman = v.hasUUID("Huntsman") ? v.getUUID("Huntsman") : null;
            e.generation = v.getInt("Generation");
            e.beaten = v.getBoolean("Beaten");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for (Map.Entry<Long, Entry> en : this.villages.entrySet())
        {
            CompoundTag v = new CompoundTag();
            v.putLong("Id", en.getKey());
            if (en.getValue().huntsman != null)
            {
                v.putUUID("Huntsman", en.getValue().huntsman);
            }
            v.putInt("Generation", en.getValue().generation);
            v.putBoolean("Beaten", en.getValue().beaten);
            list.add(v);
        }
        tag.put("Villages", list);
        return tag;
    }
}
