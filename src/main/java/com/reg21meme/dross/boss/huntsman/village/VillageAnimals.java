package com.reg21meme.dross.boss.huntsman.village;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * A hunter village's animals (its pens' moose, the Huntsman's Rimehorn herd, saved in its template), put in the world
 * once the land under each one is ready to keep it: loaded for its creatures and ticking. A village is often placed in
 * land loaded only for it (the showcase's far plots, a test), and an animal put in such land straight away can be lost:
 * a test village once came up a moose short, now and then, with nothing in the log. Meanwhile each one's land is held
 * loaded by a ticket, which runs out by itself a little after.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class VillageAnimals
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The longest one waits for its land, in ticks (a minute); then it's put there anyway (with a warning). */
    public static final int MAX_WAIT_TICKS = 1200;
    /** Holds an animal's land loaded while it waits: given again every {@link #TICKET_RENEW_TICKS}, it runs out by
     * itself this long after it was last given. */
    private static final int TICKET_TICKS = 300;
    private static final int TICKET_RENEW_TICKS = 100;
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_village_animals",
            Comparator.comparingLong(ChunkPos::toLong), TICKET_TICKS);
    /** The ticket's distance: 2 makes its own chunk tick its creatures. */
    private static final int TICKET_DISTANCE = 2;

    /** One animal waiting: its saved data (its place in the world already in it), the turn of its village. */
    private record Waiting(ServerLevel level, CompoundTag tag, Vec3 pos, Rotation rotation, ChunkPos chunk, long since)
    {
    }

    private static final List<Waiting> WAITING = new ArrayList<>();
    /** Each template's animals, read once (a template keeps them to itself). */
    private static final Map<StructureTemplate, List<StructureTemplate.StructureEntityInfo>> ANIMALS = new WeakHashMap<>();

    private VillageAnimals() {}

    /** Puts {@code template}'s animals where placing it at {@code origin} with {@code settings} would (each as soon as
     * its land is ready). Returns how many. */
    public static int place(ServerLevel level, StructureTemplate template, BlockPos origin, StructurePlaceSettings settings)
    {
        int n = 0;
        for (StructureTemplate.StructureEntityInfo info : StructureTemplate.processEntityInfos(template, level, origin,
                settings, animals(template)))
        {
            CompoundTag tag = info.nbt.copy();
            ListTag pos = new ListTag();
            pos.add(DoubleTag.valueOf(info.pos.x));
            pos.add(DoubleTag.valueOf(info.pos.y));
            pos.add(DoubleTag.valueOf(info.pos.z));
            tag.put("Pos", pos);
            tag.remove("UUID");
            ChunkPos chunk = new ChunkPos(BlockPos.containing(info.pos));
            level.getChunkSource().addRegionTicket(TICKET, chunk, TICKET_DISTANCE, chunk);
            WAITING.add(new Waiting(level, tag, info.pos, settings.getRotation(), chunk, level.getGameTime()));
            n++;
        }
        return n;
    }

    /** Drops the animals still waiting to be put in {@code area} (a village built there again brings its own). */
    public static void cancelIn(ServerLevel level, AABB area)
    {
        WAITING.removeIf(w -> w.level() == level && area.contains(w.pos()));
    }

    /** How many animals are still waiting for their land in {@code level}. */
    public static int waiting(ServerLevel level)
    {
        return (int) WAITING.stream().filter(w -> w.level() == level).count();
    }

    /** What each animal still waiting in {@code level} waits for (for a test's message or the log). */
    public static String describeWaiting(ServerLevel level)
    {
        StringBuilder out = new StringBuilder();
        for (Waiting w : WAITING)
        {
            if (w.level() == level)
            {
                out.append(describe(w)).append("; ");
            }
        }
        return out.toString();
    }

    private static String describe(Waiting w)
    {
        BlockPos at = BlockPos.containing(w.pos());
        return w.tag().getString("id") + " at " + at.toShortString() + " (chunk " + w.chunk() + ", "
                + (w.level().areEntitiesLoaded(w.chunk().toLong()) ? "its creatures loaded" : "its creatures NOT loaded") + ", "
                + (w.level().isPositionEntityTicking(at) ? "ticking" : "NOT ticking") + ", "
                + (w.level().getChunkSource().getChunkNow(w.chunk().x, w.chunk().z) != null ? "the land loaded" : "the land NOT loaded")
                + ", waiting " + (w.level().getGameTime() - w.since()) + " ticks)";
    }

    private static List<StructureTemplate.StructureEntityInfo> animals(StructureTemplate template)
    {
        return ANIMALS.computeIfAbsent(template, t ->
        {
            List<StructureTemplate.StructureEntityInfo> out = new ArrayList<>();
            for (Tag e : t.save(new CompoundTag()).getList("entities", Tag.TAG_COMPOUND))
            {
                CompoundTag entity = (CompoundTag) e;
                ListTag p = entity.getList("pos", Tag.TAG_DOUBLE);
                ListTag b = entity.getList("blockPos", Tag.TAG_INT);
                out.add(new StructureTemplate.StructureEntityInfo(new Vec3(p.getDouble(0), p.getDouble(1), p.getDouble(2)),
                        new BlockPos(b.getInt(0), b.getInt(1), b.getInt(2)), entity.getCompound("nbt")));
            }
            return out;
        });
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || WAITING.isEmpty())
        {
            return;
        }
        for (Iterator<Waiting> it = WAITING.iterator(); it.hasNext(); )
        {
            Waiting w = it.next();
            long waited = w.level().getGameTime() - w.since();
            boolean ready = w.level().areEntitiesLoaded(w.chunk().toLong())
                    && w.level().isPositionEntityTicking(BlockPos.containing(w.pos()));
            if (ready || waited >= MAX_WAIT_TICKS)
            {
                if (!ready)
                {
                    LOGGER.warn("Hunter village: the land wasn't ready for its animal after {} ticks; it's put there anyway: {}",
                            MAX_WAIT_TICKS, describe(w));
                }
                it.remove();
                add(w);
            }
            else if (waited > 0 && waited % TICKET_RENEW_TICKS == 0)
            {
                w.level().getChunkSource().addRegionTicket(TICKET, w.chunk(), TICKET_DISTANCE, w.chunk());
            }
        }
    }

    /** The server stopping: whatever is still waiting goes in now, so it's saved with its land. */
    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event)
    {
        List<Waiting> all = new ArrayList<>(WAITING);
        WAITING.clear();
        all.forEach(VillageAnimals::add);
    }

    /** Puts it in the world the way a structure template does its entities. */
    private static void add(Waiting w)
    {
        EntityType.create(w.tag(), w.level()).ifPresentOrElse(entity ->
        {
            float yaw = entity.rotate(w.rotation()) + entity.mirror(Mirror.NONE) - entity.getYRot();
            entity.moveTo(w.pos().x, w.pos().y, w.pos().z, yaw, entity.getXRot());
            if (!w.level().addFreshEntity(entity))
            {
                LOGGER.warn("Hunter village: its {} at {} couldn't be added", EntityType.getKey(entity.getType()),
                        entity.blockPosition().toShortString());
            }
        }, () -> LOGGER.warn("Hunter village: an animal ({}) couldn't be made from its saved data", w.tag().getString("id")));
    }
}
