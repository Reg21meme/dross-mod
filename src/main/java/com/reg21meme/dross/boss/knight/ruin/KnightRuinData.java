package com.reg21meme.dross.boss.knight.ruin;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * What became of each knight ruin's knights (saved with the Overworld: {@code data/dross_knight_ruins.dat}), by the
 * ruin's id (its middle column): each of its two knights (its UUID once it came, whether it's been beaten), when both
 * were last beaten (game time, while both are), and the players who have cleared it (for the quest, through
 * {@link KnightRuins}).
 */
public final class KnightRuinData extends SavedData
{
    private static final String NAME = "dross_knight_ruins";

    /** One ruin's record. */
    public static final class Entry
    {
        final UUID[] knights = new UUID[2];
        final boolean[] beaten = new boolean[2];
        /** Both beaten (until they come back), and when (game time). */
        boolean bothBeaten;
        long beatenAt;
        final Set<UUID> clearedBy = new LinkedHashSet<>();
        /** Its tidy-up is done (the trees that grew into it felled: {@link KnightRuinTidy}). */
        boolean tidied;

        public boolean tidied()
        {
            return this.tidied;
        }

        public boolean bothBeaten()
        {
            return this.bothBeaten;
        }

        public long beatenAt()
        {
            return this.beatenAt;
        }

        @Nullable
        public UUID knight(int slot)
        {
            return this.knights[slot];
        }

        public boolean beaten(int slot)
        {
            return this.beaten[slot];
        }

        public Set<UUID> clearedBy()
        {
            return Set.copyOf(this.clearedBy);
        }
    }

    private final Map<String, Entry> ruins = new HashMap<>();

    public static KnightRuinData get(ServerLevel overworld)
    {
        return overworld.getDataStorage().computeIfAbsent(KnightRuinData::load, KnightRuinData::new, NAME);
    }

    public Entry entry(String ruin)
    {
        return this.ruins.computeIfAbsent(ruin, r -> new Entry());
    }

    @Nullable
    public Entry find(String ruin)
    {
        return this.ruins.get(ruin);
    }

    void spawned(String ruin, int slot, UUID knight)
    {
        Entry e = this.entry(ruin);
        e.knights[slot] = knight;
        e.beaten[slot] = false;
        this.setDirty();
    }

    /** A knight of the ruin fell; true if that was the second (both now beaten). */
    boolean fell(String ruin, int slot, long gameTime)
    {
        Entry e = this.entry(ruin);
        e.beaten[slot] = true;
        boolean both = e.beaten[0] && e.beaten[1];
        if (both && !e.bothBeaten)
        {
            e.bothBeaten = true;
            e.beatenAt = gameTime;
        }
        this.setDirty();
        return both;
    }

    /** A knight left without being beaten (Peaceful): its place is empty, it comes again. */
    void lost(String ruin, int slot)
    {
        Entry e = this.entry(ruin);
        e.knights[slot] = null;
        this.setDirty();
    }

    /** Both come back: the ruin's record starts again (its clearers kept). */
    void reset(String ruin)
    {
        Entry e = this.entry(ruin);
        e.knights[0] = null;
        e.knights[1] = null;
        e.beaten[0] = false;
        e.beaten[1] = false;
        e.bothBeaten = false;
        e.beatenAt = 0L;
        this.setDirty();
    }

    void tidied(String ruin)
    {
        this.entry(ruin).tidied = true;
        this.setDirty();
    }

    /** For the tests: winds the clock back on when both were beaten. */
    void rewind(String ruin, long ticks)
    {
        Entry e = this.entry(ruin);
        if (e.bothBeaten)
        {
            e.beatenAt -= ticks;
            this.setDirty();
        }
    }

    void cleared(String ruin, UUID player)
    {
        if (this.entry(ruin).clearedBy.add(player))
        {
            this.setDirty();
        }
    }

    private static KnightRuinData load(CompoundTag tag)
    {
        KnightRuinData data = new KnightRuinData();
        ListTag list = tag.getList("Ruins", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag r = list.getCompound(i);
            Entry e = data.entry(r.getString("Id"));
            for (int slot = 0; slot < 2; slot++)
            {
                if (r.hasUUID("Knight" + slot))
                {
                    e.knights[slot] = r.getUUID("Knight" + slot);
                }
                e.beaten[slot] = r.getBoolean("Beaten" + slot);
            }
            e.bothBeaten = r.getBoolean("BothBeaten");
            e.beatenAt = r.getLong("BeatenAt");
            e.tidied = r.getBoolean("Tidied");
            ListTag players = r.getList("ClearedBy", Tag.TAG_INT_ARRAY);
            for (Tag p : players)
            {
                e.clearedBy.add(NbtUtils.loadUUID(p));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        this.ruins.forEach((id, e) ->
        {
            CompoundTag r = new CompoundTag();
            r.putString("Id", id);
            for (int slot = 0; slot < 2; slot++)
            {
                if (e.knights[slot] != null)
                {
                    r.putUUID("Knight" + slot, e.knights[slot]);
                }
                r.putBoolean("Beaten" + slot, e.beaten[slot]);
            }
            r.putBoolean("BothBeaten", e.bothBeaten);
            r.putLong("BeatenAt", e.beatenAt);
            r.putBoolean("Tidied", e.tidied);
            ListTag players = new ListTag();
            e.clearedBy.forEach(p -> players.add(NbtUtils.createUUID(p)));
            r.put("ClearedBy", players);
            list.add(r);
        });
        tag.put("Ruins", list);
        return tag;
    }
}
