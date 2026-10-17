package com.reg21meme.dross.boss.ratking.lair;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.RatCaves;
import com.reg21meme.dross.dimension.sewer.Sewers;
import it.unimi.dsi.fastutil.longs.LongIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

import static com.reg21meme.dross.boss.ratking.RatKingTuning.*;

/**
 * Looks after the Rat King of every rat cave under Blightwood (server side; the caves themselves are dimension-builder's
 * {@link RatCaves}). It copies the Cinder Colossus's lair ({@code boss.lair.LairKeeper}) wherever the user didn't say
 * otherwise:
 * <ul>
 *   <li><b>He appears</b> asleep on the cave's nest of carpets (his feet on the top carpet, facing the cave's arrival
 *       tunnel) when a player (not a spectator; creative counts, so testers see him) comes within
 *       {@link com.reg21meme.dross.boss.ratking.RatKingTuning#RAT_CAVE_KING_RANGE} blocks of the nest and the cave has no
 *       Rat King and has never had one beaten. Never on Peaceful. Only once every chunk of the cave is loaded with its entities (so "no king" really means none),
 *       and never in view: not while a player who was already in the cave at the last look is still in it
 *       ({@code RAT_CAVE_WATCH_MARGIN}). He's the same fighting Rat King as {@code /dross boss summon ratking} makes
 *       ({@link RatKing#spawnBoss}): asleep, he wakes exactly as in the fight (a player within 12 blocks, 8 sneaking, or a
 *       hit), with his boss bar, his rewards ("Down the Drain", the trophy) and everything else unchanged. No sound or
 *       particles: he's simply lying there.</li>
 *   <li><b>One per cave</b>: his UUID is saved for the cave ({@link RatKingLairData}) and his cave in his own saved data
 *       ({@value #HOME_TAG}), so unloading, restarting or his chunk unloading never makes a second one. An older king of a
 *       cave that already has a newer one (it was carried off and stayed unloaded while the cave got a new one) is removed
 *       as it loads.</li>
 *   <li><b>Beaten</b>: any death counts, his normal death (the silly death, "Down the Drain", the trophy) and
 *       {@code /kill} too (which gives no trophy or advancement, as always). <b>No respawn</b> (the user's decision, October
 *       16): a beaten cave stays empty for good. One that's removed without dying (Peaceful) isn't beaten: the next player
 *       who comes near (not on Peaceful) finds a new one.</li>
 *   <li><b>Players leave mid-fight</b>: like the Colossus, he stays where he is, awake, with the health he has (he never
 *       despawns or falls asleep again), until someone comes back to finish the fight.</li>
 *   <li><b>Kept home</b>: a living cave king found more than {@code RAT_CAVE_HOME_MARGIN} blocks outside his cave's box
 *       (he chased someone down a tunnel, or was teleported out) is put back on his nest, but never in the middle of a
 *       burrow. If his saved UUID can't be found once the cave's entities are loaded, he's gone (his body vanished, or he
 *       was removed) and is forgotten.</li>
 * </ul>
 * Natural spawning inside the caves stays as dimension-builder made it (only rats, {@code RatCaveSpawns}); their
 * Plague Ravagers are stronger ({@code Rat#finalizeSpawn}).
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RatKingLairKeeper
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A cave's king carries his cave in his own saved data under this name: its id, his nest and the cave's box. */
    static final String HOME_TAG = "DrossRatCave";
    /** Who was in a cave at its last look only counts if that look was at most this many looks ago (else nobody was). */
    private static final int WATCH_FRESH_LOOKS = 2;

    /** The rat caves found so far this server run, by their network's first chunk (empty: none there). Server thread. */
    private static final Map<Long, Optional<RatCaves.RatCave>> CAVES = new HashMap<>();
    /** Who was in each cave near a player at its last look, and when (not saved: only for "were they already watching?"). */
    private static final Map<Long, Watch> WATCH = new HashMap<>();

    /** The players in a cave at a look, and the game time of that look. */
    private record Watch(Set<UUID> inside, long gameTime)
    {
    }

    private RatKingLairKeeper() {}

    // ---------------------------------------------------------------- finding caves near players

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % RAT_CAVE_CHECK_INTERVAL_TICKS != 0)
        {
            return;
        }
        MinecraftServer server = event.getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null || dross.players().isEmpty())
        {
            WATCH.clear();
            return;
        }
        try
        {
            check(server, dross);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Rat caves: looking after the caves' Rat Kings failed.", e);
        }
    }

    /** One look: every rat cave with a player near its nest gets its king if it should. */
    static void check(MinecraftServer server, ServerLevel dross)
    {
        Structure structure = ratCaveStructure(dross);
        if (structure == null)
        {
            return;
        }
        Map<Long, RatCaves.RatCave> near = new LinkedHashMap<>();
        for (ServerPlayer player : dross.players())
        {
            if (!counts(player))
            {
                continue;
            }
            for (RatCaves.RatCave cave : cavesAround(dross, structure, player.blockPosition()))
            {
                if (player.position().distanceToSqr(nestFeet(cave)) <= RAT_CAVE_KING_RANGE * RAT_CAVE_KING_RANGE)
                {
                    near.putIfAbsent(cave.id(), cave);
                }
            }
        }
        long now = server.overworld().getGameTime();
        for (RatCaves.RatCave cave : near.values())
        {
            checkCave(server, dross, cave, now);
        }
        WATCH.keySet().retainAll(near.keySet()); // nobody near any more: whoever comes next has only just arrived
    }

    /** A player who counts: not a spectator, alive. */
    private static boolean counts(ServerPlayer player)
    {
        return !player.isSpectator() && player.isAlive();
    }

    /**
     * The rat caves whose network reaches into a loaded chunk within reach of {@code pos} (enough to find every nest
     * within {@code RAT_CAVE_KING_RANGE}: a nest's own chunk always knows its network). Reads loaded chunks only.
     */
    static List<RatCaves.RatCave> cavesAround(ServerLevel dross, Structure structure, BlockPos pos)
    {
        int reach = Mth.ceil(RAT_CAVE_KING_RANGE / 16.0D);
        int cx = SectionPos.blockToSectionCoord(pos.getX());
        int cz = SectionPos.blockToSectionCoord(pos.getZ());
        Set<Long> seen = new HashSet<>();
        List<RatCaves.RatCave> found = new ArrayList<>();
        for (int x = cx - reach; x <= cx + reach; x++)
        {
            for (int z = cz - reach; z <= cz + reach; z++)
            {
                LevelChunk chunk = dross.getChunkSource().getChunkNow(x, z);
                if (chunk == null)
                {
                    continue;
                }
                LongIterator starts = chunk.getReferencesForStructure(structure).iterator();
                while (starts.hasNext())
                {
                    long start = starts.nextLong();
                    if (seen.add(start))
                    {
                        resolve(dross, structure, start).ifPresent(found::add);
                    }
                }
            }
        }
        return found;
    }

    /** The rat cave of the network that starts in this chunk (found once, then remembered). */
    private static Optional<RatCaves.RatCave> resolve(ServerLevel dross, Structure structure, long startChunk)
    {
        Optional<RatCaves.RatCave> known = CAVES.get(startChunk);
        if (known != null)
        {
            return known;
        }
        ChunkPos pos = new ChunkPos(startChunk);
        ChunkAccess chunk = dross.getChunk(pos.x, pos.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = dross.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
        Optional<RatCaves.RatCave> cave = RatCaves.of(start);
        CAVES.put(startChunk, cave);
        return cave;
    }

    @Nullable
    private static Structure ratCaveStructure(ServerLevel dross)
    {
        return dross.registryAccess().registryOrThrow(Registries.STRUCTURE).get(Sewers.RAT_CAVE);
    }

    // ---------------------------------------------------------------- one cave

    /** One look at a cave with a player near: its king (forgotten if he's gone), then whether a new one appears. */
    static void checkCave(MinecraftServer server, ServerLevel dross, RatCaves.RatCave cave, long now)
    {
        RatKingLairData data = RatKingLairData.get(server);
        long id = cave.id();
        boolean loaded = entitiesLoaded(dross, cave);
        boolean watched = false;
        if (loaded)
        {
            // Someone who was in the cave at the last look (while it was loaded) and still is would see him appear.
            Set<UUID> inside = playersInside(dross, cave);
            Watch last = WATCH.get(id);
            if (last != null && now - last.gameTime() <= (long) WATCH_FRESH_LOOKS * RAT_CAVE_CHECK_INTERVAL_TICKS)
            {
                for (UUID player : inside)
                {
                    if (last.inside().contains(player))
                    {
                        watched = true;
                        break;
                    }
                }
            }
            WATCH.put(id, new Watch(inside, now));
        }
        UUID kingId = data.king(id);
        if (kingId != null)
        {
            Entity entity = dross.getEntity(kingId);
            if (entity instanceof RatKing king && !king.isRemoved())
            {
                if (!king.isAlive() && !data.beaten(id))
                {
                    data.recordDefeat(id, now); // dead (lying there), but his death wasn't seen
                }
                return; // he's there (asleep, fighting or lying dead): nothing more appears
            }
            if (!loaded)
            {
                return; // can't tell yet whether he's there
            }
            LOGGER.info("Rat cave {}: its Rat King ({}) is gone{}.", where(cave), kingId, data.beaten(id)
                    ? " (beaten; his body has vanished)" : " without being beaten (removed), so the next player who comes near finds a new one at once");
            data.forgetKing(id);
        }
        if (dross.getDifficulty() == Difficulty.PEACEFUL || data.beaten(id) || !loaded || watched) // (beaten: never again)
        {
            return;
        }
        appear(dross, data, cave, now);
    }

    /** The cave's Rat King appears, asleep on his nest. */
    @Nullable
    static RatKing appear(ServerLevel dross, RatKingLairData data, RatCaves.RatCave cave, long now)
    {
        Vec3 feet = nestFeet(cave);
        RatKing king = RatKing.spawnBoss(dross, feet, kingYaw(cave));
        if (king == null)
        {
            LOGGER.warn("Rat cave {}: the Rat King couldn't be created.", where(cave));
            return null;
        }
        king.getPersistentData().put(HOME_TAG, homeTag(cave));
        data.setKing(cave.id(), king.getUUID());
        LOGGER.info("Rat cave {}: the Rat King appears asleep on his nest at {} (king {} of this cave, game time {}).", where(cave),
                BlockPos.containing(feet).toShortString(), data.appearances(cave.id()), now);
        return king;
    }

    /** Where his feet go on his nest: the middle of its top carpet. */
    static Vec3 nestFeet(RatCaves.RatCave cave)
    {
        BlockPos nest = cave.nest();
        return new Vec3(nest.getX() + 0.5D, nest.getY() + RAT_CAVE_NEST_CARPET, nest.getZ() + 0.5D);
    }

    /** The way he lies on his nest: facing the cave's arrival tunnel (where {@code /dross goto lair ratking} lands). */
    static float kingYaw(RatCaves.RatCave cave)
    {
        return Mth.wrapDegrees(cave.arrivalYaw() + 180.0F);
    }

    /** The chunks of the cave's box, and as far round it as a king may stray before he's put back. */
    static List<ChunkPos> chunks(RatCaves.RatCave cave)
    {
        BoundingBox box = cave.bounds();
        List<ChunkPos> chunks = new ArrayList<>();
        for (int x = SectionPos.blockToSectionCoord(box.minX() - RAT_CAVE_HOME_MARGIN); x <= SectionPos.blockToSectionCoord(box.maxX() + RAT_CAVE_HOME_MARGIN); x++)
        {
            for (int z = SectionPos.blockToSectionCoord(box.minZ() - RAT_CAVE_HOME_MARGIN); z <= SectionPos.blockToSectionCoord(box.maxZ() + RAT_CAVE_HOME_MARGIN); z++)
            {
                chunks.add(new ChunkPos(x, z));
            }
        }
        return chunks;
    }

    /** True once the entities of every chunk round the cave are loaded (so "not found" really means gone). */
    static boolean entitiesLoaded(ServerLevel dross, RatCaves.RatCave cave)
    {
        for (ChunkPos chunk : chunks(cave))
        {
            if (!dross.areEntitiesLoaded(chunk.toLong()))
            {
                return false;
            }
        }
        return true;
    }

    /** The players (who count) in the cave: its box, and {@code RAT_CAVE_WATCH_MARGIN} past it (the doorways). */
    private static Set<UUID> playersInside(ServerLevel dross, RatCaves.RatCave cave)
    {
        AABB box = AABB.of(cave.bounds()).inflate(RAT_CAVE_WATCH_MARGIN);
        Set<UUID> inside = new HashSet<>();
        for (ServerPlayer player : dross.players())
        {
            if (counts(player) && box.contains(player.position()))
            {
                inside.add(player.getUUID());
            }
        }
        return inside;
    }

    private static String where(RatCaves.RatCave cave)
    {
        return cave.centre().toShortString();
    }

    // ---------------------------------------------------------------- his home, in his own saved data

    private static CompoundTag homeTag(RatCaves.RatCave cave)
    {
        CompoundTag tag = new CompoundTag();
        tag.putLong("Id", cave.id());
        Vec3 feet = nestFeet(cave);
        tag.putDouble("NestX", feet.x);
        tag.putDouble("NestY", feet.y);
        tag.putDouble("NestZ", feet.z);
        BoundingBox box = cave.bounds();
        tag.putIntArray("Box", new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        return tag;
    }

    /** True if this king belongs to a rat cave (he appeared on its nest). */
    public static boolean isCaveKing(RatKing king)
    {
        return king.getPersistentData().contains(HOME_TAG);
    }

    /** The id of the cave this king belongs to, or empty. */
    static Optional<Long> caveOf(RatKing king)
    {
        CompoundTag home = king.getPersistentData().getCompound(HOME_TAG);
        return home.isEmpty() ? Optional.empty() : Optional.of(home.getLong("Id"));
    }

    /** A living cave king who has strayed too far from his cave is put back on his nest (once a look, never mid-burrow). */
    @SubscribeEvent
    public static void onLivingTick(LivingEvent.LivingTickEvent event)
    {
        if (!(event.getEntity() instanceof RatKing king) || !(king.level() instanceof ServerLevel level)
                || king.tickCount % RAT_CAVE_CHECK_INTERVAL_TICKS != 0 || king.isShowcase() || !king.isAlive()
                || !level.dimension().equals(ModDimensions.DROSS_LEVEL)) // (carried off to another dimension: his cave isn't there)
        {
            return;
        }
        CompoundTag home = king.getPersistentData().getCompound(HOME_TAG);
        if (home.isEmpty())
        {
            return;
        }
        RatKingLairData.get(level.getServer()).noteAwake(home.getLong("Id"), king.isAwake());
        int[] b = home.getIntArray("Box");
        if (b.length != 6 || king.isBurrowing())
        {
            return;
        }
        AABB box = new AABB(b[0], b[1], b[2], b[3] + 1.0D, b[4] + 1.0D, b[5] + 1.0D).inflate(RAT_CAVE_HOME_MARGIN);
        if (!box.contains(king.position()))
        {
            LOGGER.info("Rat cave: its Rat King was found outside his cave at {}; he's put back on his nest.", king.blockPosition().toShortString());
            king.getNavigation().stop();
            king.setDeltaMovement(Vec3.ZERO);
            king.teleportTo(home.getDouble("NestX"), home.getDouble("NestY"), home.getDouble("NestZ"));
        }
    }

    /** A cave's king dies (any death, /kill too): the cave stays empty for good. */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onDeath(LivingDeathEvent event)
    {
        if (!(event.getEntity() instanceof RatKing king) || !(king.level() instanceof ServerLevel level) || king.isShowcase())
        {
            return;
        }
        Optional<Long> cave = caveOf(king);
        if (cave.isEmpty())
        {
            return;
        }
        RatKingLairData data = RatKingLairData.get(level.getServer());
        UUID saved = data.king(cave.get());
        if (saved != null && !saved.equals(king.getUUID()))
        {
            return; // an old copy, not the cave's king now
        }
        data.recordDefeat(cave.get(), level.getServer().overworld().getGameTime());
        LOGGER.info("Rat cave: its Rat King was beaten ({}); the cave stays empty for good (no respawn).", event.getSource().getMsgId());
    }

    /** One king per cave: an old king of a cave that already has a newer one is removed as he loads; a lost one is taken back. */
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event)
    {
        if (!(event.getEntity() instanceof RatKing king) || !(event.getLevel() instanceof ServerLevel level) || king.isShowcase()
                || !level.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return;
        }
        Optional<Long> cave = caveOf(king);
        if (cave.isEmpty())
        {
            return;
        }
        RatKingLairData data = RatKingLairData.get(level.getServer());
        UUID saved = data.king(cave.get());
        if (saved == null)
        {
            if (king.isAlive())
            {
                data.adoptKing(cave.get(), king.getUUID()); // he was carried off and forgotten; nobody has replaced him yet
            }
        }
        else if (!saved.equals(king.getUUID()))
        {
            LOGGER.info("Rat cave: an old Rat King of this cave ({}) turned up at {}, but the cave has a newer one ({}); the old one is removed.",
                    king.getUUID(), king.blockPosition().toShortString(), saved);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        CAVES.clear();
        WATCH.clear();
    }
}
