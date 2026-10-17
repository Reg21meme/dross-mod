package com.reg21meme.dross.boss.ratking.lair;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.SewerSites;
import com.reg21meme.dross.dimension.sewer.SewerTuning;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.registry.ModSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import static com.reg21meme.dross.boss.ratking.RatKingTuning.*;

/**
 * His snores at the grand entrance (the user's request, October 16): while a rat cave's Rat King is asleep on his nest,
 * or hasn't appeared yet and hasn't been beaten, players within {@link com.reg21meme.dross.boss.ratking.RatKingTuning#RAT_CAVE_SNORE_RANGE}
 * blocks of his sewer's grand entrance (the crowned pipe) now and then hear him snore, rising out of the pipe: his own
 * snore ({@code entity.rat_king.snore}), quieter and a little deeper, every 5 to 11 seconds. Once he's awake or beaten,
 * the pipe goes quiet. Only a sound: sent to the players near, from the air over the pipe's rim.
 * <p>
 * Where the grand entrances are comes from the boss-site planner (noise only), asked on a background thread every
 * {@link com.reg21meme.dross.boss.ratking.RatKingTuning#RAT_CAVE_SNORE_LOOKUP_TICKS} ticks (the first time an area is
 * asked about it can take a second); each is known by its rat cave's id (its network's start chunk, as
 * {@code RatCaves.RatCave.id()}).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RatKingSnores
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The planner is asked on this low-priority thread. */
    private static final ExecutorService LOOKUP_THREAD = Executors.newSingleThreadExecutor(task -> {
        Thread thread = new Thread(task, "Dross Rat King snores");
        thread.setDaemon(true);
        thread.setPriority(Thread.NORM_PRIORITY - 2);
        return thread;
    });

    /** A grand entrance near a player: its rat cave's id and the air block over its rim. */
    record Entrance(long cave, BlockPos opening)
    {
    }

    /** The grand entrances near the players at the last lookup. Server thread. */
    private static List<Entrance> near = List.of();
    @Nullable
    private static CompletableFuture<List<Entrance>> lookup;
    /** When each entrance snores next (game time), by its cave's id. */
    private static final Map<Long, Long> NEXT = new HashMap<>();
    /** Tests: how many snores each player has been sent. */
    private static final Map<UUID, Integer> HEARD = new HashMap<>();

    private RatKingSnores() {}

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END)
        {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            return;
        }
        try
        {
            look(server, dross);
            if (server.getTickCount() % RAT_CAVE_SNORE_CHECK_TICKS == 0 && !near.isEmpty() && !dross.players().isEmpty())
            {
                snore(server, dross);
            }
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Rat caves: the Rat King's snores at the grand entrances failed.", e);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        near = List.of();
        lookup = null;
        NEXT.clear();
        HEARD.clear();
    }

    /** Asks the planner (on the background thread) which grand entrances are near the players, and picks up the answer. */
    private static void look(MinecraftServer server, ServerLevel dross)
    {
        CompletableFuture<List<Entrance>> pending = lookup;
        if (pending != null)
        {
            if (pending.isDone())
            {
                lookup = null;
                try
                {
                    near = pending.join();
                }
                catch (CompletionException e)
                {
                    LOGGER.error("Rat caves: finding the grand entrances near the players failed.", e.getCause());
                }
            }
            return;
        }
        if (server.getTickCount() % RAT_CAVE_SNORE_LOOKUP_TICKS != 0)
        {
            return;
        }
        if (dross.players().isEmpty())
        {
            near = List.of();
            return;
        }
        List<BlockPos> where = new ArrayList<>();
        for (ServerPlayer player : dross.players())
        {
            where.add(player.blockPosition());
        }
        lookup = CompletableFuture.supplyAsync(() -> entrancesNear(dross, where), LOOKUP_THREAD);
    }

    /** The grand entrances within the snores' range (plus a margin, for players on the move) of any of these spots. */
    static List<Entrance> entrancesNear(ServerLevel dross, List<BlockPos> where)
    {
        BossSites sites = BossSites.forLevel(dross);
        int radius = (int) Math.ceil(RAT_CAVE_SNORE_RANGE) * 2;
        int reach = radius + (SewerTuning.GRID_RADIUS + 1) * SewerTuning.CELL + SewerTuning.CELL;
        Map<Long, Entrance> found = new LinkedHashMap<>();
        for (BlockPos pos : where)
        {
            for (Site site : sites.sitesNear(DrossBiomes.BLIGHTWOOD, pos.getX(), pos.getZ(), reach))
            {
                if (site.use() != BossSites.RAT_KING_SEWER || !(site.detail() instanceof SewerSites.Layout layout))
                {
                    continue;
                }
                for (SewerSites.Entrance e : layout.entrances())
                {
                    if (e.grand() && e.distanceTo(pos.getX(), pos.getZ()) <= radius)
                    {
                        found.putIfAbsent(site.chunk().toLong(), new Entrance(site.chunk().toLong(), e.opening()));
                    }
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    /** Each grand entrance near a player whose king sleeps (or waits) snores when its time comes. */
    private static void snore(MinecraftServer server, ServerLevel dross)
    {
        long now = server.overworld().getGameTime();
        RatKingLairData data = RatKingLairData.get(server);
        for (Entrance entrance : near)
        {
            List<ServerPlayer> hearing = new ArrayList<>();
            for (ServerPlayer player : dross.players())
            {
                if (player.position().distanceToSqr(entrance.opening().getX() + 0.5D, entrance.opening().getY() + 0.5D,
                        entrance.opening().getZ() + 0.5D) <= RAT_CAVE_SNORE_RANGE * RAT_CAVE_SNORE_RANGE)
                {
                    hearing.add(player);
                }
            }
            if (hearing.isEmpty() || !sleeps(dross, data, entrance.cave()))
            {
                continue;
            }
            Long next = NEXT.get(entrance.cave());
            if (next == null)
            {
                NEXT.put(entrance.cave(), now + wait(dross)); // the first one comes after a moment, not the instant you arrive
                continue;
            }
            if (now < next)
            {
                continue;
            }
            NEXT.put(entrance.cave(), now + wait(dross));
            SoundEvent sound = ModSounds.RAT_KING_SNORE.get();
            float pitch = RAT_CAVE_SNORE_PITCH + (dross.random.nextFloat() * 2.0F - 1.0F) * RAT_CAVE_SNORE_PITCH_SPREAD;
            BlockPos at = entrance.opening();
            for (ServerPlayer player : hearing)
            {
                player.connection.send(new ClientboundSoundPacket(Holder.direct(sound), SoundSource.HOSTILE, at.getX() + 0.5D, at.getY() + 0.5D,
                        at.getZ() + 0.5D, RAT_CAVE_SNORE_VOLUME, pitch, dross.random.nextLong()));
                HEARD.merge(player.getUUID(), 1, Integer::sum);
            }
        }
    }

    /** Ticks until the next snore: between {@code RAT_CAVE_SNORE_MIN_TICKS} and {@code RAT_CAVE_SNORE_MAX_TICKS}. */
    private static long wait(ServerLevel dross)
    {
        return RAT_CAVE_SNORE_MIN_TICKS + dross.random.nextInt(RAT_CAVE_SNORE_MAX_TICKS - RAT_CAVE_SNORE_MIN_TICKS + 1);
    }

    /**
     * True if the cave's Rat King is asleep: he hasn't been beaten, and either he hasn't appeared yet, or he's lying on
     * his nest asleep (if he isn't loaded, as the saved data last saw him: not woken).
     */
    static boolean sleeps(ServerLevel dross, RatKingLairData data, long cave)
    {
        if (data.beaten(cave))
        {
            return false;
        }
        UUID king = data.king(cave);
        if (king == null)
        {
            return true;
        }
        Entity entity = dross.getEntity(king);
        if (entity instanceof RatKing rat)
        {
            return rat.isAlive() && rat.isAsleep();
        }
        return !data.awake(cave);
    }

    /** Tests: how many snores this player has been sent so far. */
    static int snoresHeardBy(ServerPlayer player)
    {
        return HEARD.getOrDefault(player.getUUID(), 0);
    }
}
