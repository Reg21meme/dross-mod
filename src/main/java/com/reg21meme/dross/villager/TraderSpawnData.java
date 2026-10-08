package com.reg21meme.dross.villager;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers (per world) that the trader was already spawned and where, so he is never spawned twice. */
public class TraderSpawnData extends SavedData
{
    private static final String NAME = "dross_trader";

    private boolean spawned = false;
    private BlockPos pos = BlockPos.ZERO;

    public static TraderSpawnData get(ServerLevel overworld)
    {
        return overworld.getDataStorage().computeIfAbsent(TraderSpawnData::load, TraderSpawnData::new, NAME);
    }

    public boolean hasSpawned()
    {
        return spawned;
    }

    public BlockPos getPos()
    {
        return pos;
    }

    public void markSpawned(BlockPos where)
    {
        this.spawned = true;
        this.pos = where;
        this.setDirty();
    }

    public static TraderSpawnData load(CompoundTag tag)
    {
        TraderSpawnData data = new TraderSpawnData();
        data.spawned = tag.getBoolean("Spawned");
        if (tag.contains("Pos"))
        {
            data.pos = NbtUtils.readBlockPos(tag.getCompound("Pos"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("Spawned", spawned);
        tag.put("Pos", NbtUtils.writeBlockPos(pos));
        return tag;
    }
}
