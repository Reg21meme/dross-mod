package com.reg21meme.dross.boss.knight;

import com.reg21meme.dross.Dross;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.FloatTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Quaternionf;
import org.joml.Vector3f;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The spikes the elemental knights make burst out of the ground: the Thawed axeman's clear ice (the Alpha Moose's spike,
 * {@code dross:hunt_ice_spike}) and the Geode Knight's amethyst (Minecraft's amethyst cluster). Each is a block display:
 * nothing in the world is changed. It starts tiny, grows to its size in a few ticks, stands a while and goes; one saved
 * with its land before its time was up doesn't come back. (The same way as the Huntsman's, boss/huntsman/HuntFight.)
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class KnightSpikes
{
    static final String TAG = "dross_knight_spike";
    /** How small a spike starts (a share of its size). */
    private static final float SEED = 0.05F;
    /** How long it takes to grow (ticks). */
    private static final int GROW = 3;

    private static final Map<Entity, Long> UP = new LinkedHashMap<>();
    private static final Map<Entity, CompoundTag> GROWING = new LinkedHashMap<>();
    private static final Map<Entity, Long> GROW_AT = new LinkedHashMap<>();

    private KnightSpikes() {}

    /**
     * One spike of {@code state} at {@code at} (its foot's middle), {@code w} wide and {@code h} tall (blocks), leaning
     * by {@code tilt}, turned {@code yaw}; it goes {@code life} ticks later.
     */
    public static void spike(ServerLevel level, BlockState state, Vec3 at, float w, float h, Quaternionf tilt, float yaw,
                             int life)
    {
        CompoundTag tag = new CompoundTag();
        tag.putString("id", "minecraft:block_display");
        tag.put("block_state", NbtUtils.writeBlockState(state));
        tag.put("transformation", shape(w * SEED, h * SEED, tilt));
        ListTag tags = new ListTag();
        tags.add(StringTag.valueOf(TAG));
        tag.put("Tags", tags);
        Entity spike = EntityType.loadEntityRecursive(tag, level, e ->
        {
            e.moveTo(at.x, at.y, at.z, yaw, 0.0F);
            return e;
        });
        if (spike != null && level.addFreshEntity(spike))
        {
            long now = level.getGameTime();
            UP.put(spike, now + life);
            CompoundTag full = new CompoundTag();
            full.put("transformation", shape(w, h, tilt));
            full.putInt("start_interpolation", 0);
            full.putInt("interpolation_duration", GROW);
            GROWING.put(spike, full);
            GROW_AT.put(spike, now + 1);
        }
    }

    /** A leaning spike: a tilt of up to {@code maxLean} degrees about a random line across. */
    public static Quaternionf lean(net.minecraft.util.RandomSource random, double maxLean)
    {
        double a = random.nextDouble() * Math.PI * 2.0D;
        return new Quaternionf().rotationAxis((float) Math.toRadians(random.nextDouble() * maxLean),
                (float) Math.cos(a), 0.0F, (float) Math.sin(a));
    }

    /** A spike leaning away from {@code from}'s middle by {@code lean} degrees. */
    public static Quaternionf leanAway(Vec3 from, Vec3 at, double lean)
    {
        double a = Math.atan2(at.z - from.z, at.x - from.x);
        return new Quaternionf().rotationAxis((float) Math.toRadians(lean), (float) -Math.sin(a), 0.0F, (float) Math.cos(a));
    }

    /** A display's shape: the block scaled, leaning about its foot's middle (which stays on the display's spot). */
    private static CompoundTag shape(float w, float h, Quaternionf tilt)
    {
        Vector3f foot = tilt.transform(new Vector3f(0.5F * w, 0.0F, 0.5F * w));
        CompoundTag transform = new CompoundTag();
        transform.put("translation", floats(-foot.x, -foot.y, -foot.z));
        transform.put("scale", floats(w, h, w));
        transform.put("left_rotation", floats(tilt.x, tilt.y, tilt.z, tilt.w));
        transform.put("right_rotation", floats(0.0F, 0.0F, 0.0F, 1.0F));
        return transform;
    }

    private static ListTag floats(float... values)
    {
        ListTag list = new ListTag();
        for (float v : values)
        {
            list.add(FloatTag.valueOf(v));
        }
        return list;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        long now = event.getServer().overworld().getGameTime();
        GROW_AT.entrySet().removeIf(e ->
        {
            if (e.getKey().isRemoved())
            {
                GROWING.remove(e.getKey());
                return true;
            }
            if (now < e.getValue())
            {
                return false;
            }
            CompoundTag full = GROWING.remove(e.getKey());
            if (full != null)
            {
                CompoundTag tag = e.getKey().saveWithoutId(new CompoundTag());
                tag.merge(full);
                e.getKey().load(tag);
            }
            return true;
        });
        UP.entrySet().removeIf(e ->
        {
            if (e.getKey().isRemoved())
            {
                return true;
            }
            if (now >= e.getValue())
            {
                e.getKey().discard();
                return true;
            }
            return false;
        });
    }

    /** A spike left over (saved with its land before its time was up) doesn't come back. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event)
    {
        if (!event.getLevel().isClientSide() && event.getEntity() instanceof Display && event.loadedFromDisk()
                && event.getEntity().getTags().contains(TAG) && !UP.containsKey(event.getEntity()))
        {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        UP.keySet().forEach(Entity::discard);
        UP.clear();
        GROWING.clear();
        GROW_AT.clear();
    }
}
