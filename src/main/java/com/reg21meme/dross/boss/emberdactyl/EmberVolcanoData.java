package com.reg21meme.dross.boss.emberdactyl;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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
     * @param buildMillis how long building it took (work only)
     */
    public record Volcano(long id, int group, BlockPos ground, int rimY, int lakeY, Vec3 egg, Vec3 pathFoot, float pathFootYaw,
                          Vec3 lookout, float lookoutYaw, long buildMillis)
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
                plan.pathFootYaw(), plan.lookout(), plan.lookoutYaw(), buildMillis);
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
            data.volcanoes.put(id, new Volcano(id, v.getInt("Group"), new BlockPos(v.getInt("X"), v.getInt("Y"), v.getInt("Z")),
                    v.getInt("RimY"), v.getInt("LakeY"), vec(v, "Egg"), vec(v, "PathFoot"), v.getFloat("PathFootYaw"), vec(v, "Lookout"),
                    v.getFloat("LookoutYaw"), v.getLong("BuildMillis")));
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
            v.putLong("BuildMillis", volcano.buildMillis());
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
