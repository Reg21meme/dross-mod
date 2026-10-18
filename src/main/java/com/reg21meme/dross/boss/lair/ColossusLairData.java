package com.reg21meme.dross.boss.lair;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers (in the world save) every Cinder Colossus lair that has been built: where, how it's laid out, and its
 * Colossus (which one, whether it's been beaten). A lair is known by its <b>id</b>: the id of the Cinder Wastes patch the
 * boss-site planner put it in ({@code Patch.id()}, the same for the same seed forever), so a patch never gets two. Saved as
 * {@code data/dross_colossus_lairs.dat} in the Overworld's save folder (the Overworld is always loaded).
 * <p>
 * {@code Lairs}: a list of {@code Id}, {@code Group} (the planner's group, 0 = the nearest), {@code CenterX/Z} (the
 * planned spot), {@code FloorY}, {@code Tunnel}, {@code Mouth}, {@code RimY}, {@code HighLand}, {@code GullyEnd},
 * {@code ApproachEnd}, {@code EntranceX/Y/Z}, {@code EntranceYaw}, {@code BuildMillis}, {@code Boss} (UUID, while it has
 * one), {@code Beaten} (true for good once its Colossus is beaten: no respawn). A planned lair nobody has come near has no
 * entry yet.
 * Server thread only.
 */
public final class ColossusLairData extends SavedData
{
    private static final String NAME = "dross_colossus_lairs";

    private final Map<Long, Entry> lairs = new LinkedHashMap<>();

    /** One built lair. */
    static final class Entry
    {
        final long id;
        int group;
        int centerX;
        int centerZ;
        int floorY;
        Direction tunnel = Direction.NORTH;
        int mouth;
        int rimY;
        int highLand;
        int gullyEnd;
        int approachEnd;
        Vec3 entrance = Vec3.ZERO;
        float entranceYaw;
        long buildMillis;
        @Nullable
        UUID boss;
        boolean beaten;

        Entry(long id)
        {
            this.id = id;
        }

        long id()
        {
            return id;
        }

        int group()
        {
            return group;
        }

        int centerX()
        {
            return centerX;
        }

        int centerZ()
        {
            return centerZ;
        }

        int floorY()
        {
            return floorY;
        }

        Direction tunnel()
        {
            return tunnel;
        }

        int mouth()
        {
            return mouth;
        }

        int rimY()
        {
            return rimY;
        }

        /** The high land round it the rim was planned to stand above. */
        int highLand()
        {
            return highLand;
        }

        int gullyEnd()
        {
            return gullyEnd;
        }

        /** The last row of the land shaped in front of the mouth (its distance from the middle): the way in meets the land just beyond. */
        int approachEnd()
        {
            return approachEnd;
        }

        Vec3 entrance()
        {
            return entrance;
        }

        float entranceYaw()
        {
            return entranceYaw;
        }

        long buildMillis()
        {
            return buildMillis;
        }

        @Nullable
        UUID boss()
        {
            return boss;
        }

        /** True for good once its Colossus has been beaten (no respawn: the lair stays empty). */
        boolean beaten()
        {
            return beaten;
        }
    }

    static ColossusLairData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(ColossusLairData::load, ColossusLairData::new, NAME);
    }

    private static ColossusLairData load(CompoundTag tag)
    {
        ColossusLairData data = new ColossusLairData();
        ListTag list = tag.getList("Lairs", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++)
        {
            CompoundTag t = list.getCompound(i);
            Entry e = new Entry(t.getLong("Id"));
            e.group = t.getInt("Group");
            e.centerX = t.getInt("CenterX");
            e.centerZ = t.getInt("CenterZ");
            e.floorY = t.getInt("FloorY");
            Direction tunnel = Direction.byName(t.getString("Tunnel"));
            e.tunnel = tunnel != null && tunnel.getAxis().isHorizontal() ? tunnel : Direction.NORTH;
            e.mouth = t.getInt("Mouth");
            e.rimY = t.getInt("RimY");
            e.highLand = t.getInt("HighLand");
            e.gullyEnd = t.getInt("GullyEnd");
            e.approachEnd = t.getInt("ApproachEnd");
            e.entrance = new Vec3(t.getDouble("EntranceX"), t.getDouble("EntranceY"), t.getDouble("EntranceZ"));
            e.entranceYaw = t.getFloat("EntranceYaw");
            e.buildMillis = t.getLong("BuildMillis");
            e.boss = t.hasUUID("Boss") ? t.getUUID("Boss") : null;
            e.beaten = t.getBoolean("Beaten");
            data.lairs.put(e.id, e);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for (Entry e : lairs.values())
        {
            CompoundTag t = new CompoundTag();
            t.putLong("Id", e.id);
            t.putInt("Group", e.group);
            t.putInt("CenterX", e.centerX);
            t.putInt("CenterZ", e.centerZ);
            t.putInt("FloorY", e.floorY);
            t.putString("Tunnel", e.tunnel.getName());
            t.putInt("Mouth", e.mouth);
            t.putInt("RimY", e.rimY);
            t.putInt("HighLand", e.highLand);
            t.putInt("GullyEnd", e.gullyEnd);
            t.putInt("ApproachEnd", e.approachEnd);
            t.putDouble("EntranceX", e.entrance.x);
            t.putDouble("EntranceY", e.entrance.y);
            t.putDouble("EntranceZ", e.entrance.z);
            t.putFloat("EntranceYaw", e.entranceYaw);
            t.putLong("BuildMillis", e.buildMillis);
            if (e.boss != null)
            {
                t.putUUID("Boss", e.boss);
            }
            t.putBoolean("Beaten", e.beaten);
            list.add(t);
        }
        tag.put("Lairs", list);
        return tag;
    }

    // ---------------------------------------------------------------- the lairs

    /** The built lair with this id, or null. */
    @Nullable
    Entry lair(long id)
    {
        return lairs.get(id);
    }

    /** True if the lair with this id is built. */
    boolean isBuilt(long id)
    {
        return lairs.containsKey(id);
    }

    /** Every built lair, in the order they were built. */
    Collection<Entry> all()
    {
        return Collections.unmodifiableCollection(lairs.values());
    }

    /** The built lairs as a list (a copy). */
    List<Entry> list()
    {
        return new ArrayList<>(lairs.values());
    }

    /** A lair has been built at its planned site (the floor's middle is the planned spot). */
    Entry markBuilt(long id, int group, LairBuilder.Layout layout, long millis)
    {
        Entry e = new Entry(id);
        e.group = group;
        e.centerX = layout.centerX();
        e.centerZ = layout.centerZ();
        e.floorY = layout.floorY();
        e.tunnel = layout.tunnel();
        e.mouth = layout.mouth();
        e.rimY = layout.rimY();
        e.highLand = layout.highLand();
        e.gullyEnd = layout.gullyEnd();
        e.approachEnd = layout.approachEnd();
        e.entrance = layout.entrance();
        e.entranceYaw = layout.entranceYaw();
        e.buildMillis = millis;
        lairs.put(id, e);
        setDirty();
        return e;
    }

    // ---------------------------------------------------------------- its Colossus

    /** A Colossus has risen in the lair: it's the lair's now. */
    void setRisen(Entry e, UUID id)
    {
        e.boss = id;
        setDirty();
    }

    /** The lair's Colossus was beaten: the lair stays empty for good. */
    void recordDefeat(Entry e)
    {
        e.beaten = true;
        setDirty();
    }

    /** The lair's Colossus is gone (its statue crumbled, or it was removed). */
    void forgetBoss(Entry e)
    {
        e.boss = null;
        setDirty();
    }

    /** Tests: no Colossus, never beaten (as freshly built). */
    void resetBossForTests(Entry e)
    {
        e.boss = null;
        e.beaten = false;
        setDirty();
    }
}
