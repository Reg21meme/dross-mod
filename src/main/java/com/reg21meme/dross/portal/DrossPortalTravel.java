package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModBlocks;
import java.util.ArrayList;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * Our own "standing in the portal" timer, so we never touch vanilla's Nether portal logic.
 * <ul>
 *   <li>While an entity is inside a Dross portal, its timer counts up once per tick.</li>
 *   <li>When it reaches the entity's portal wait time (4 seconds for survival players, instant
 *       for creative players and other entities, same as vanilla), the entity is teleported.</li>
 *   <li>After a trip the entity gets the normal portal cooldown, and the cooldown keeps being
 *       refreshed while it still stands in a portal, so it never bounces straight back.</li>
 * </ul>
 * Players and other entities (mobs, items) can all travel, like the nether portal.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class DrossPortalTravel
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Key in the entity's persistent data: the Overworld portal block the entity left from. */
    static final String TAG_RETURN_PORTAL = "dross_return_portal";
    /** Key in the entity's persistent data: the dimension that portal is in. */
    static final String TAG_RETURN_DIM = "dross_return_dim";

    private static final Map<Entity, PortalTimer> TIMERS = new WeakHashMap<>();
    private static boolean warnedMissingDimension = false;

    private static final class PortalTimer
    {
        int time;
        boolean insideThisTick;
        BlockPos portalPos = BlockPos.ZERO;
    }

    private DrossPortalTravel() {}

    /** Called (server side) every tick an entity is inside a Dross portal block. */
    public static void onEntityInside(Entity entity, BlockPos portalPos)
    {
        if (entity.isOnPortalCooldown())
        {
            entity.setPortalCooldown(); // like vanilla: you have to step out before you can travel again
            return;
        }
        PortalTimer timer = TIMERS.computeIfAbsent(entity, e -> new PortalTimer());
        timer.insideThisTick = true;
        timer.portalPos = portalPos.immutable();
    }

    /**
     * Server players only get their "inside block" check when the client sends a movement packet,
     * which can skip ticks while they stand still. So we also check their bounding box every tick.
     */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || event.player.isSpectator() || !event.player.canChangeDimensions())
        {
            return;
        }
        Level level = event.player.level();
        AABB box = event.player.getBoundingBox().deflate(1.0E-7D);
        BlockPos min = BlockPos.containing(box.minX, box.minY, box.minZ);
        BlockPos max = BlockPos.containing(box.maxX, box.maxY, box.maxZ);
        for (BlockPos pos : BlockPos.betweenClosed(min, max))
        {
            if (level.getBlockState(pos).is(ModBlocks.DROSS_PORTAL.get()))
            {
                onEntityInside(event.player, pos);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || TIMERS.isEmpty())
        {
            return;
        }
        for (Map.Entry<Entity, PortalTimer> entry : new ArrayList<>(TIMERS.entrySet()))
        {
            Entity entity = entry.getKey();
            PortalTimer timer = entry.getValue();
            if (entity == null || entity.isRemoved())
            {
                TIMERS.remove(entity);
                continue;
            }
            if (timer.insideThisTick)
            {
                timer.insideThisTick = false;
                if (entity.isPassenger())
                {
                    continue;
                }
                if (timer.time++ >= entity.getPortalWaitTime())
                {
                    TIMERS.remove(entity);
                    teleport(entity, timer.portalPos);
                }
            }
            else
            {
                timer.time = Mth.clamp(timer.time - 4, 0, Integer.MAX_VALUE);
                if (timer.time == 0)
                {
                    TIMERS.remove(entity);
                }
            }
        }
    }

    private static void teleport(Entity entity, BlockPos portalPos)
    {
        if (!(entity.level() instanceof ServerLevel from))
        {
            return;
        }
        MinecraftServer server = from.getServer();
        boolean inDross = from.dimension() == ModDimensions.DROSS_LEVEL;
        ResourceKey<Level> destKey = inDross ? Level.OVERWORLD : ModDimensions.DROSS_LEVEL;
        ServerLevel dest = server.getLevel(destKey);
        if (dest == null)
        {
            if (!warnedMissingDimension)
            {
                warnedMissingDimension = true;
                LOGGER.warn("Dross portal: dimension {} is not loaded, so the portal can't teleport anyone.", destKey.location());
            }
            entity.setPortalCooldown();
            return;
        }

        if (!inDross)
        {
            // Remember which portal we left from, so the trip back lands at the same portal.
            CompoundTag data = entity.getPersistentData();
            data.put(TAG_RETURN_PORTAL, NbtUtils.writeBlockPos(portalPos));
            data.putString(TAG_RETURN_DIM, from.dimension().location().toString());
        }

        entity.setPortalCooldown();
        Entity arrived = entity.changeDimension(dest, new DrossTeleporter());
        if (!inDross && arrived instanceof ServerPlayer player)
        {
            DrossArrival.start(player);
        }
    }
}
