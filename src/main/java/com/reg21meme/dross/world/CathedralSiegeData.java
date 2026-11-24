package com.reg21meme.dross.world;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * What became of the Fallen Cathedral's siege (saved with the Overworld: {@code data/dross_cathedral_siege.dat};
 * {@link CathedralSiege}): whether it's broken (for good, for everyone in the world), when and by whom; and each of its
 * {@value CathedralSiege#GUARDS} knights: its UUID once it came, whether it has fallen, and its own spot round the
 * cathedral (picked once, the first time it came). Which knights they are comes from the seed, not from here
 * ({@link CathedralSiege#plan}).
 *
 * <p>{@code /dross siege reset} starts it again with a new <b>generation</b>: the knights of an older generation (one in
 * a chunk that wasn't loaded at the reset) are refused when they load ({@link CathedralSiege#onJoin}).</p>
 */
final class CathedralSiegeData extends SavedData
{
    private static final String NAME = "dross_cathedral_siege";

    private final UUID[] knights = new UUID[CathedralSiege.GUARDS];
    private final boolean[] fallen = new boolean[CathedralSiege.GUARDS];
    private final BlockPos[] homes = new BlockPos[CathedralSiege.GUARDS];
    private boolean broken;
    /** The game time it broke. */
    private long brokenAt;
    /** Who brought down its last knight (a player's name), or how it broke ("/dross siege break"). */
    private String brokenBy = "";
    private int generation;

    /** Server thread, with the Overworld. */
    static CathedralSiegeData get(ServerLevel overworld)
    {
        return overworld.getDataStorage().computeIfAbsent(CathedralSiegeData::load, CathedralSiegeData::new, NAME);
    }

    boolean broken()
    {
        return this.broken;
    }

    long brokenAt()
    {
        return this.brokenAt;
    }

    String brokenBy()
    {
        return this.brokenBy;
    }

    int generation()
    {
        return this.generation;
    }

    @Nullable
    UUID knight(int slot)
    {
        return this.knights[slot];
    }

    boolean fallen(int slot)
    {
        return this.fallen[slot];
    }

    @Nullable
    BlockPos home(int slot)
    {
        return this.homes[slot];
    }

    /** How many of its knights have fallen. */
    int fallenCount()
    {
        int n = 0;
        for (boolean f : this.fallen)
        {
            n += f ? 1 : 0;
        }
        return n;
    }

    boolean allFallen()
    {
        return this.fallenCount() == CathedralSiege.GUARDS;
    }

    void home(int slot, BlockPos home)
    {
        this.homes[slot] = home.immutable();
        this.setDirty();
    }

    void spawned(int slot, UUID knight)
    {
        this.knights[slot] = knight;
        this.setDirty();
    }

    /** A knight left without falling (Peaceful, taken away): its place is empty, it comes again. */
    void lost(int slot)
    {
        this.knights[slot] = null;
        this.setDirty();
    }

    void fell(int slot)
    {
        this.fallen[slot] = true;
        this.setDirty();
    }

    void markBroken(long gameTime, String by)
    {
        this.broken = true;
        this.brokenAt = gameTime;
        this.brokenBy = by;
        this.setDirty();
    }

    /** Starts it all again (a new generation): unbroken, no knight come or fallen, their spots to be picked again. */
    void reset()
    {
        for (int slot = 0; slot < CathedralSiege.GUARDS; slot++)
        {
            this.knights[slot] = null;
            this.fallen[slot] = false;
            this.homes[slot] = null;
        }
        this.broken = false;
        this.brokenAt = 0L;
        this.brokenBy = "";
        this.generation++;
        this.setDirty();
    }

    static CathedralSiegeData load(CompoundTag tag)
    {
        CathedralSiegeData data = new CathedralSiegeData();
        data.broken = tag.getBoolean("Broken");
        data.brokenAt = tag.getLong("BrokenAt");
        data.brokenBy = tag.getString("BrokenBy");
        data.generation = tag.getInt("Generation");
        ListTag list = tag.getList("Knights", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag k = list.getCompound(i);
            int slot = k.getInt("Slot");
            if (slot < 0 || slot >= CathedralSiege.GUARDS)
            {
                continue;
            }
            if (k.hasUUID("Knight"))
            {
                data.knights[slot] = k.getUUID("Knight");
            }
            data.fallen[slot] = k.getBoolean("Fallen");
            if (k.contains("Home", Tag.TAG_LONG))
            {
                data.homes[slot] = BlockPos.of(k.getLong("Home"));
            }
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("Broken", this.broken);
        tag.putLong("BrokenAt", this.brokenAt);
        tag.putString("BrokenBy", this.brokenBy);
        tag.putInt("Generation", this.generation);
        ListTag list = new ListTag();
        for (int slot = 0; slot < CathedralSiege.GUARDS; slot++)
        {
            CompoundTag k = new CompoundTag();
            k.putInt("Slot", slot);
            if (this.knights[slot] != null)
            {
                k.putUUID("Knight", this.knights[slot]);
            }
            k.putBoolean("Fallen", this.fallen[slot]);
            if (this.homes[slot] != null)
            {
                k.putLong("Home", this.homes[slot].asLong());
            }
            list.add(k);
        }
        tag.put("Knights", list);
        return tag;
    }
}
