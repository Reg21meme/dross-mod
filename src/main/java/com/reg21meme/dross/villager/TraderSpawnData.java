package com.reg21meme.dross.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Remembers (per world) that the trader was already spawned, so he (and his hut) are never made twice.
 * The saved position is his spot in the hut, where he stands (the chapel's aisle).
 * It also remembers which entity he is and where he was last seen, so {@code /dross trader}
 * can find him even when his area isn't loaded.
 */
public class TraderSpawnData extends SavedData
{
    private static final String NAME = "dross_trader";

    private boolean spawned = false;
    private BlockPos pos = BlockPos.ZERO;
    @Nullable
    private UUID traderId;
    private BlockPos lastPos = BlockPos.ZERO;

    public static TraderSpawnData get(ServerLevel overworld)
    {
        return overworld.getDataStorage().computeIfAbsent(TraderSpawnData::load, TraderSpawnData::new, NAME);
    }

    public boolean hasSpawned()
    {
        return spawned;
    }

    /** His spot in the hut: where he stands, and where he teleports home to. */
    public BlockPos getPos()
    {
        return pos;
    }

    /** The hut trader's entity UUID, or null in worlds from before this was saved. */
    @Nullable
    public UUID getTraderId()
    {
        return traderId;
    }

    /** Where the hut trader was last seen (updated every second while his area is loaded). */
    public BlockPos getLastPos()
    {
        return lastPos;
    }

    public void markSpawned(BlockPos where, UUID trader)
    {
        this.spawned = true;
        this.pos = where;
        this.traderId = trader;
        this.lastPos = where;
        this.setDirty();
    }

    public void setLastPos(BlockPos where)
    {
        if (!where.equals(this.lastPos))
        {
            this.lastPos = where.immutable();
            this.setDirty();
        }
    }

    public static TraderSpawnData load(CompoundTag tag)
    {
        TraderSpawnData data = new TraderSpawnData();
        data.spawned = tag.getBoolean("Spawned");
        if (tag.contains("Pos"))
        {
            data.pos = NbtUtils.readBlockPos(tag.getCompound("Pos"));
        }
        if (tag.hasUUID("Trader"))
        {
            data.traderId = tag.getUUID("Trader");
        }
        data.lastPos = tag.contains("LastPos") ? NbtUtils.readBlockPos(tag.getCompound("LastPos")) : data.pos;
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("Spawned", spawned);
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        if (traderId != null)
        {
            tag.putUUID("Trader", traderId);
        }
        tag.put("LastPos", NbtUtils.writeBlockPos(lastPos));
        return tag;
    }
}
