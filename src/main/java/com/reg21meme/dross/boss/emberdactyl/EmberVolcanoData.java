package com.reg21meme.dross.boss.emberdactyl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers (in the world save) every Emberdactyl volcano that has been built in the Dross: where it stands and where its
 * nest, path and lookout are. A volcano is known by the id of the Cinder Wastes patch the boss-site planner put it in
 * ({@code Patch.id()}, the same for the same seed forever), so a patch never gets two. Saved as
 * {@code data/dross_ember_volcanoes.dat} in the Overworld's save folder (always loaded). A planned volcano nobody has
 * come near has no entry yet. Server thread only.
 */
public final class EmberVolcanoData extends SavedData
{
    private static final String NAME = "dross_ember_volcanoes";

    private final Map<Long, Volcano> volcanoes = new LinkedHashMap<>();
    private final Map<Long, State> states = new LinkedHashMap<>();
    /** Each volcano's Emberdactyl now (the adult, then its young), by its id; one removed without dying is "lost". */
    private final Map<Long, UUID> bosses = new LinkedHashMap<>();
    private final Set<Long> lost = new HashSet<>();

    /** Where a volcano's fight is: its egg and Emberdactyl not come yet, the adult there, its young out, or both beaten
     *  (for good: no respawn, the user's rule for every boss). */
    public enum State
    {
        WAITING, ADULT, YOUNG, BEATEN
    }

    /** A built volcano's fight. */
    public State state(long id)
    {
        return this.states.getOrDefault(id, State.WAITING);
    }

    public void setState(long id, State state)
    {
        if (this.state(id) != state)
        {
            this.states.put(id, state);
            setDirty();
        }
    }

    /**
     * The volcano's own Emberdactyl now, wherever it is (the user, November 2: one chased a Wither past where its volcano
     * looked for it, and a second came), or null if none is recorded.
     */
    @Nullable
    public UUID boss(long id)
    {
        return this.bosses.get(id);
    }

    /** Records the volcano's Emberdactyl (null: none, a new one may come). */
    public void setBoss(long id, @Nullable UUID boss)
    {
        if (boss == null)
        {
            this.bosses.remove(id);
        }
        else
        {
            this.bosses.put(id, boss);
        }
        this.lost.remove(id);
        setDirty();
    }

    /** Its Emberdactyl was removed without dying (Peaceful): a new one may come. */
    public void markLost(long id, UUID boss)
    {
        if (boss.equals(this.bosses.get(id)) && this.lost.add(id))
        {
            setDirty();
        }
    }

    /** True if a new Emberdactyl may come to it: none recorded, or the recorded one was removed without dying. */
    public boolean mayBring(long id)
    {
        return !this.bosses.containsKey(id) || this.lost.contains(id);
    }

    /**
     * One built volcano.
     *
     * @param id          its patch's id
     * @param group       the planner's group of Cinder Wastes patches (0 = the nearest)
     * @param ground      the ground block under its middle (its rim stands {@link EmberVolcanoTuning#HEIGHT} over it)
     * @param rimY        its rim's height
     * @param lakeY       its crater's lava lake's surface
     * @param egg         where its egg stands (the middle of the nest's top)
     * @param pathFoot    the foot of its path, standing on it, and the way up it
     * @param lookout     the lookout at the path's top, standing on it, and the way to the nest
     * @param perch       where the Emberdactyl perches (on its ledge on the rim, standing on it) and the way it faces (out);
     *                    null for a volcano saved before it had one
     * @param buildMillis how long building it took (work only)
     */
    public record Volcano(long id, int group, BlockPos ground, int rimY, int lakeY, Vec3 egg, Vec3 pathFoot, float pathFootYaw,
                          Vec3 lookout, float lookoutYaw, @Nullable Vec3 perch, float perchYaw, long buildMillis)
    {
        /** Blocks from (x, z) to its middle, along the ground. */
        public double distanceTo(double x, double z)
        {
            double dx = this.ground.getX() + 0.5D - x;
            double dz = this.ground.getZ() + 0.5D - z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    public static EmberVolcanoData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(EmberVolcanoData::load, EmberVolcanoData::new, NAME);
    }

    /** The volcano of this patch, if it's built. */
    @Nullable
    public Volcano volcano(long id)
    {
        return this.volcanoes.get(id);
    }

    public boolean isBuilt(long id)
    {
        return this.volcanoes.containsKey(id);
    }

    /** Every built volcano, in the order they were built. */
    public List<Volcano> all()
    {
        return Collections.unmodifiableList(new ArrayList<>(this.volcanoes.values()));
    }

    /** Records a finished volcano. */
    Volcano markBuilt(long id, int group, EmberVolcano.Plan plan, long buildMillis)
    {
        Volcano volcano = new Volcano(id, group, plan.ground(), plan.rimY(), plan.lakeY(), plan.eggFeet(), plan.pathFoot(),
                plan.pathFootYaw(), plan.lookout(), plan.lookoutYaw(), plan.perch(), plan.perchYaw(), buildMillis);
        this.volcanoes.put(id, volcano);
        setDirty();
        return volcano;
    }

    private static EmberVolcanoData load(CompoundTag tag)
    {
        EmberVolcanoData data = new EmberVolcanoData();
        for (Tag t : tag.getList("Volcanoes", Tag.TAG_COMPOUND))
        {
            CompoundTag v = (CompoundTag) t;
            long id = v.getLong("Id");
            if (v.contains("State"))
            {
                data.states.put(id, State.valueOf(v.getString("State")));
            }
            if (v.hasUUID("Boss"))
            {
                data.bosses.put(id, v.getUUID("Boss"));
                if (v.getBoolean("BossLost"))
                {
                    data.lost.add(id);
                }
            }
            data.volcanoes.put(id, new Volcano(id, v.getInt("Group"), new BlockPos(v.getInt("X"), v.getInt("Y"), v.getInt("Z")),
                    v.getInt("RimY"), v.getInt("LakeY"), vec(v, "Egg"), vec(v, "PathFoot"), v.getFloat("PathFootYaw"), vec(v, "Lookout"),
                    v.getFloat("LookoutYaw"), v.contains("PerchX") ? vec(v, "Perch") : null, v.getFloat("PerchYaw"),
                    v.getLong("BuildMillis")));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        ListTag list = new ListTag();
        for (Volcano volcano : this.volcanoes.values())
        {
            CompoundTag v = new CompoundTag();
            v.putLong("Id", volcano.id());
            v.putInt("Group", volcano.group());
            v.putInt("X", volcano.ground().getX());
            v.putInt("Y", volcano.ground().getY());
            v.putInt("Z", volcano.ground().getZ());
            v.putInt("RimY", volcano.rimY());
            v.putInt("LakeY", volcano.lakeY());
            putVec(v, "Egg", volcano.egg());
            putVec(v, "PathFoot", volcano.pathFoot());
            v.putFloat("PathFootYaw", volcano.pathFootYaw());
            putVec(v, "Lookout", volcano.lookout());
            v.putFloat("LookoutYaw", volcano.lookoutYaw());
            if (volcano.perch() != null)
            {
                putVec(v, "Perch", volcano.perch());
                v.putFloat("PerchYaw", volcano.perchYaw());
            }
            v.putLong("BuildMillis", volcano.buildMillis());
            v.putString("State", this.state(volcano.id()).name());
            UUID boss = this.bosses.get(volcano.id());
            if (boss != null)
            {
                v.putUUID("Boss", boss);
                v.putBoolean("BossLost", this.lost.contains(volcano.id()));
            }
            list.add(v);
        }
        tag.put("Volcanoes", list);
        return tag;
    }

    private static Vec3 vec(CompoundTag tag, String key)
    {
        return new Vec3(tag.getDouble(key + "X"), tag.getDouble(key + "Y"), tag.getDouble(key + "Z"));
    }

    private static void putVec(CompoundTag tag, String key, Vec3 v)
    {
        tag.putDouble(key + "X", v.x);
        tag.putDouble(key + "Y", v.y);
        tag.putDouble(key + "Z", v.z);
    }
}
