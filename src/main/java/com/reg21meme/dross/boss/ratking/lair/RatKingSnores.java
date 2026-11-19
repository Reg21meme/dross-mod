package com.reg21meme.dross.boss.ratking.lair;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.boss.ratking.RatKingDesigns;
import com.reg21meme.dross.dimension.sewer.RatCaves;
import com.reg21meme.dross.dimension.sewer.Sewers;
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
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.protocol.game.ClientboundSoundPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.Vec3;
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
 * <p>
 * <b>Down the pipes</b> (the user, November 9, 2026): while he sleeps (or waits), everyone in his sewer, its tunnels,
 * pipes, junctions and cave, hears each of his snores from the way of his nest, louder the nearer they get, up to his own
 * loudness beside him ({@link #loudness}; numbers {@code PIPE_SNORE_*} in RatKingTuning). On his nest he snores himself
 * ({@link #kingSnores}, from his sleep loop); while he isn't there to (he hasn't appeared yet, his land isn't ticking)
 * the snore comes from his nest on his sleep loop's beat. Players in his sewer hear that, not the pipe's rim snore.
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
    /** When each cave's king last snored on his nest himself (game time): meanwhile the nest's beat stays quiet. */
    private static final Map<Long, Long> KING_SNORED = new HashMap<>();
    /** When each cave's nest snores next while its king isn't there to (game time). */
    private static final Map<Long, Long> PIPE_NEXT = new HashMap<>();
    /** Tests: how loud the last snore down the pipes each player was sent was. */
    private static final Map<UUID, Float> LAST_LOUDNESS = new HashMap<>();

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
            if (server.getTickCount() % RAT_CAVE_SNORE_CHECK_TICKS == 0 && !dross.players().isEmpty())
            {
                snoreDownThePipes(server, dross);
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
        KING_SNORED.clear();
        PIPE_NEXT.clear();
        LAST_LOUDNESS.clear();
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
            Structure network = ratCaveStructure(dross);
            for (ServerPlayer player : dross.players())
            {
                if (network != null && inNetwork(dross, network, player, entrance.cave()))
                {
                    continue; // in his sewer (down the shaft) they hear him from his nest instead
                }
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

    // ------------------------------------------------------------------ down the pipes

    /**
     * A cave's king asleep on his nest snores (from his sleep loop, {@code RatKing}): everyone in his sewer and everyone
     * within {@code PIPE_SNORE_HEARD_AROUND} blocks of him hears it, as loud as {@link #loudness} for how far off they are.
     */
    public static void kingSnores(RatKing king)
    {
        if (!(king.level() instanceof ServerLevel level))
        {
            return;
        }
        Optional<Long> cave = RatKingLairKeeper.caveOf(king);
        cave.ifPresent(id -> KING_SNORED.put(id, level.getServer().overworld().getGameTime()));
        Vec3 source = king.position().add(0.0D, 0.6D, 0.0D);
        Structure network = ratCaveStructure(level);
        for (ServerPlayer player : level.players())
        {
            boolean around = player.getEyePosition().distanceToSqr(source) <= PIPE_SNORE_HEARD_AROUND * PIPE_SNORE_HEARD_AROUND;
            if (around || (cave.isPresent() && network != null && inNetwork(level, network, player, cave.get())))
            {
                send(level, player, source);
            }
        }
    }

    /** While a cave's king isn't on his nest to snore (not appeared yet, his land not ticking), his nest snores for him. */
    private static void snoreDownThePipes(MinecraftServer server, ServerLevel dross)
    {
        Structure network = ratCaveStructure(dross);
        if (network == null)
        {
            return;
        }
        Map<Long, List<ServerPlayer>> byCave = new LinkedHashMap<>();
        Map<Long, RatCaves.RatCave> caves = new HashMap<>();
        for (ServerPlayer player : dross.players())
        {
            StructureStart start = dross.structureManager().getStructureWithPieceAt(player.blockPosition(), network);
            if (!start.isValid())
            {
                continue;
            }
            long id = start.getChunkPos().toLong();
            if (!caves.containsKey(id))
            {
                caves.put(id, RatCaves.of(start).orElse(null));
            }
            byCave.computeIfAbsent(id, k -> new ArrayList<>()).add(player);
        }
        if (byCave.isEmpty())
        {
            return;
        }
        long now = server.overworld().getGameTime();
        int loop = RatKingDesigns.ALL.get(0).sleepTicks();
        RatKingLairData data = RatKingLairData.get(server);
        for (Map.Entry<Long, List<ServerPlayer>> entry : byCave.entrySet())
        {
            long id = entry.getKey();
            RatCaves.RatCave cave = caves.get(id);
            Long snored = KING_SNORED.get(id);
            if (cave == null || (snored != null && now - snored <= loop + 2L * RAT_CAVE_SNORE_CHECK_TICKS) || !sleeps(dross, data, id))
            {
                continue;
            }
            Long next = PIPE_NEXT.get(id);
            if (next == null || next - now > loop)
            {
                PIPE_NEXT.put(id, now + loop / 2); // the first one after a moment
                continue;
            }
            if (now < next)
            {
                continue;
            }
            PIPE_NEXT.put(id, now + loop);
            Vec3 source = RatKingLairKeeper.nestFeet(cave).add(0.0D, 0.6D, 0.0D);
            for (ServerPlayer player : entry.getValue())
            {
                send(dross, player, source);
            }
        }
    }

    /**
     * How loud his snore is this far from him (blocks): his own loudness within {@code PIPE_SNORE_NEAR}, then falling off
     * as (near / distance) ^ {@code PIPE_SNORE_FALLOFF}, never under {@code PIPE_SNORE_FAINTEST}.
     */
    public static float loudness(double distance)
    {
        if (distance <= PIPE_SNORE_NEAR)
        {
            return PIPE_SNORE_LOUDEST;
        }
        double loud = PIPE_SNORE_LOUDEST * Math.pow(PIPE_SNORE_NEAR / distance, PIPE_SNORE_FALLOFF);
        return (float) Math.max(PIPE_SNORE_FAINTEST, loud);
    }

    /** Sends one snore to one player, from his way, as loud as {@link #loudness} says for how far off he is. */
    private static void send(ServerLevel level, ServerPlayer player, Vec3 source)
    {
        Vec3 ear = player.getEyePosition();
        double distance = ear.distanceTo(source);
        float loud = loudness(distance);
        Vec3 at = source;
        float volume = loud;
        if (distance > PIPE_SNORE_SOURCE_GAP)
        {
            // from a little way off towards him: Minecraft fades a sound out over 16 blocks, so it's sent that much louder
            at = ear.add(source.subtract(ear).normalize().scale(PIPE_SNORE_SOURCE_GAP));
            volume = (float) Math.min(1.0D, loud / (1.0D - PIPE_SNORE_SOURCE_GAP / 16.0D));
        }
        float faintness = (PIPE_SNORE_LOUDEST - loud) / (PIPE_SNORE_LOUDEST - PIPE_SNORE_FAINTEST);
        float pitch = 1.0F + (PIPE_SNORE_FAR_PITCH - 1.0F) * Math.max(0.0F, Math.min(1.0F, faintness));
        player.connection.send(new ClientboundSoundPacket(Holder.direct(ModSounds.RAT_KING_SNORE.get()), SoundSource.HOSTILE,
                at.x, at.y, at.z, volume, pitch, level.random.nextLong()));
        HEARD.merge(player.getUUID(), 1, Integer::sum);
        LAST_LOUDNESS.put(player.getUUID(), loud);
    }

    /** True if this player is in the sewer of the rat cave with this id (inside one of its network's pieces). */
    static boolean inNetwork(ServerLevel level, Structure network, ServerPlayer player, long cave)
    {
        StructureStart start = level.structureManager().getStructureWithPieceAt(player.blockPosition(), network);
        return start.isValid() && start.getChunkPos().toLong() == cave;
    }

    @Nullable
    private static Structure ratCaveStructure(ServerLevel level)
    {
        return level.registryAccess().registryOrThrow(Registries.STRUCTURE).get(Sewers.RAT_CAVE);
    }

    /** Tests: how loud the last snore down the pipes sent to this player was (0 if none). */
    static float lastLoudnessFor(ServerPlayer player)
    {
        return LAST_LOUDNESS.getOrDefault(player.getUUID(), 0.0F);
    }
}
