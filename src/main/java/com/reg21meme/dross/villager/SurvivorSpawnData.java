package com.reg21meme.dross.villager;

import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Remembers (per world, in the Overworld's data as {@code dross_survivors}) that the hub's two survivors were spawned,
 * and which entities they are, so they're never made twice (like the trader, see {@link TraderSpawnData}). Survivors
 * made by the headless tests never touch it.
 */
public class SurvivorSpawnData extends SavedData
{
    private static final String NAME = "dross_survivors";

    private boolean spawned;
    @Nullable
    private UUID mentorId;
    @Nullable
    private UUID studentId;

    public static SurvivorSpawnData get(ServerLevel overworld)
    {
        return overworld.getDataStorage().computeIfAbsent(SurvivorSpawnData::load, SurvivorSpawnData::new, NAME);
    }

    public boolean hasSpawned()
    {
        return this.spawned;
    }

    /** The Blind Oracle's entity UUID (null until they've spawned). */
    @Nullable
    public UUID getMentorId()
    {
        return this.mentorId;
    }

    /** The Ragged Seeker's entity UUID (null until they've spawned). */
    @Nullable
    public UUID getStudentId()
    {
        return this.studentId;
    }

    public void markSpawned(UUID mentor, UUID student)
    {
        this.spawned = true;
        this.mentorId = mentor;
        this.studentId = student;
        this.setDirty();
    }

    public static SurvivorSpawnData load(CompoundTag tag)
    {
        SurvivorSpawnData data = new SurvivorSpawnData();
        data.spawned = tag.getBoolean("Spawned");
        if (tag.hasUUID("MentorId"))
        {
            data.mentorId = tag.getUUID("MentorId");
        }
        if (tag.hasUUID("StudentId"))
        {
            data.studentId = tag.getUUID("StudentId");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("Spawned", this.spawned);
        if (this.mentorId != null)
        {
            tag.putUUID("MentorId", this.mentorId);
        }
        if (this.studentId != null)
        {
            tag.putUUID("StudentId", this.studentId);
        }
        return tag;
    }
}
