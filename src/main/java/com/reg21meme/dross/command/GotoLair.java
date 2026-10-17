package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.lair.ColossusLair;
import com.reg21meme.dross.boss.ratking.lair.RatKingLair;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.RatCaves;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.portal.DrossTeleporter;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * {@code /dross goto lair colossus} and {@code /dross goto lair ratking}: teleports to a boss's lair. Wired into
 * {@link DrossCommand}. A future boss's lair adds one literal under {@code goto lair}.
 * <ul>
 *   <li><b>{@code goto lair colossus}</b> ({@link #colossus}): to the entrance of the <b>nearest</b> Cinder Colossus lair
 *       (the boss area's {@link ColossusLair}: one volcano per group of 3 Cinder Wastes patches): nearest to the player if
 *       they're in the Dross, otherwise to the hub ({@link DrossHub#CENTER}). The lairs are only built when someone first
 *       comes near, so: it says it's looking; the planner finds the nearest lair site (on a background thread); if its
 *       lair is built, the player is teleported at once; if not, it says it's building it, and the player is teleported
 *       the moment it's finished (normally well under a minute; never onto a half-built volcano). The landing: the lair's
 *       entrance (in the gully just outside the cave mouth), facing in; if its blocks were changed so it's no longer safe,
 *       the nearest safe spot within {@value #ENTRANCE_FALLBACK_RADIUS} blocks. The chat line says whether the Colossus
 *       rises when you walk in, is awake (someone woke it and left), has been beaten (the lair stays empty for good: no
 *       respawn), or never rises (Peaceful). If the player logs out, or asks again, before it's ready, the old request is
 *       dropped; after {@value #BUILD_WAIT_MILLIS} ms of waiting it gives up with a message.</li>
 *   <li><b>{@code goto lair ratking}</b> ({@link #ratKing}): into the nearest rat cave, the Rat King's home under Blightwood
 *       (the dimension area's {@code RatCaves}, his home the boss area's {@code RatKingLair}): onto its arrival spot at the
 *       end of a tunnel leading in, facing his nest. The note says whether he's asleep there, awake (someone woke him and
 *       left), beaten (the cave stays empty for good) or never there (Peaceful).</li>
 * </ul>
 * Plain teleports, like {@code /dross goto hub}: no arrival title or notes, and walking in (or coming near) is what
 * wakes the boss, not the command.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class GotoLair
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---- Chat messages: lang keys in en_us.json ----
    /** "Looking for the nearest Cinder Colossus lair..." */
    static final String SEARCHING = "commands.dross.goto.lair.searching";
    /** "The nearest Cinder Colossus lair, at X, Z, hasn't been built yet: building it now..." */
    static final String BUILDING = "commands.dross.goto.lair.building";
    /** "Found no Cinder Colossus lair in the Dross..." */
    static final String NOT_FOUND = "commands.dross.goto.lair.not_found";
    /** "The lair took too long to build, so you weren't moved..." */
    static final String TIMEOUT = "commands.dross.goto.lair.timeout";
    /** "The Dross dimension isn't loaded, so there's no lair to go to." */
    static final String NO_DROSS = "commands.dross.goto.lair.no_dross";
    /** "Something stopped you from going to the Dross." (another mod cancelled the change of dimension) */
    static final String STOPPED = "commands.dross.goto.lair.stopped";
    /** "Teleported to the entrance of the Cinder Colossus's lair at X, Y, Z. " followed by one of the notes below. */
    static final String SUCCESS = "commands.dross.goto.lair.success";
    /** "It rises when you walk in." */
    static final String READY = "commands.dross.goto.lair.ready";
    /** "Its Colossus is awake: someone woke it and left." */
    static final String AWAKE = "commands.dross.goto.lair.awake";
    /** "Its Colossus has been beaten: this lair stays empty for good." */
    static final String BEATEN = "commands.dross.goto.lair.beaten";
    /** "It never rises on Peaceful." */
    static final String PEACEFUL = "commands.dross.goto.lair.peaceful";

    // ---- Chat messages of goto lair ratking: lang keys in en_us.json ----
    /** "Found no rat cave in the Dross (...)." */
    static final String RAT_KING_NOT_FOUND = "commands.dross.goto.lair.ratking.not_found";
    /** "Teleported into the Rat King's cave at X, Y, Z. " followed by one of the notes below. */
    static final String RAT_KING_SUCCESS = "commands.dross.goto.lair.ratking.success";
    /** "He's asleep on his nest in the middle." */
    static final String RAT_KING_READY = "commands.dross.goto.lair.ratking.ready";
    /** "He's awake: someone woke him and left." */
    static final String RAT_KING_AWAKE = "commands.dross.goto.lair.ratking.awake";
    /** "He has been beaten: this cave stays empty for good." */
    static final String RAT_KING_BEATEN = "commands.dross.goto.lair.ratking.beaten";
    /** "He never appears on Peaceful." */
    static final String RAT_KING_PEACEFUL = "commands.dross.goto.lair.ratking.peaceful";

    // ---- Tuning ----
    /** If the entrance is no longer safe to stand on (its blocks were changed), a safe spot is looked for this far round it. */
    public static final int ENTRANCE_FALLBACK_RADIUS = 8;
    /**
     * {@code goto lair colossus} waits at most this long (real time) for the nearest lair to be found and built (finding
     * it takes a moment, loading its land and building it normally 10-60 s); then it gives up with a message.
     */
    public static final long BUILD_WAIT_MILLIS = 5 * 60 * 1000L;
    /**
     * If a rat cave's arrival spot is no longer safe to stand on (its blocks were changed), a safe spot on the cave's floor
     * is looked for this far round it (blocks, sideways)...
     */
    public static final int CAVE_ARRIVAL_FALLBACK_RADIUS = 4;
    /** ...from this many blocks below the arrival spot to this many above it. */
    public static final int CAVE_ARRIVAL_FALLBACK_DOWN = 1;
    public static final int CAVE_ARRIVAL_FALLBACK_UP = 2;

    /** The {@code goto lair colossus} requests still waiting, by player (a newer request replaces an older one). Server thread. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static long nextRequest;

    /** One waiting {@code goto lair colossus}. */
    private record Pending(long number, CommandSourceStack source, ServerPlayer player, long deadline)
    {
    }

    private GotoLair() {}

    // ---------------------------------------------------------------- goto lair colossus

    /**
     * Runs {@code /dross goto lair colossus}: starts the search for the nearest lair (the teleport follows when it's
     * found and built).
     *
     * @return 1 if the search started; 0 if not (the reason is said in chat)
     * @throws CommandSyntaxException if it wasn't used by a player (the server console, a command block)
     */
    static int colossus(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            source.sendFailure(Component.translatable(NO_DROSS));
            return 0;
        }
        boolean fromHere = player.level() == dross;
        BlockPos from = fromHere ? player.blockPosition() : DrossHub.CENTER;
        Pending request = new Pending(++nextRequest, source, player, Util.getMillis() + BUILD_WAIT_MILLIS);
        PENDING.put(player.getUUID(), request);
        source.sendSuccess(() -> Component.translatable(SEARCHING), false);
        long started = Util.getMillis();
        ColossusLair.findNearestSite(dross, from).whenCompleteAsync((found, error) -> {
            if (!isCurrent(request))
            {
                return;
            }
            if (error != null)
            {
                LOGGER.error("[goto lair] {}: finding the nearest Colossus lair failed", player.getName().getString(), error);
                PENDING.remove(player.getUUID());
                source.sendFailure(Component.translatable(NOT_FOUND));
                return;
            }
            if (found.isEmpty())
            {
                LOGGER.info("[goto lair] {}: no Colossus lair site found from {} ({} ms)", player.getName().getString(), from.toShortString(),
                        Util.getMillis() - started);
                PENDING.remove(player.getUUID());
                source.sendFailure(Component.translatable(NOT_FOUND));
                return;
            }
            Site site = found.get();
            LOGGER.info("[goto lair] {}: the nearest Colossus lair (from {} {}) is the one of Cinder Wastes group {} at {} {}, {} ({} ms)",
                    player.getName().getString(), fromHere ? "where they stood," : "the hub,", from.toShortString(), site.patch().group() + 1,
                    site.pos().getX(), site.pos().getZ(), ColossusLair.get(server, site).isPresent() ? "built" : "not built yet",
                    Util.getMillis() - started);
            if (ColossusLair.get(server, site).isEmpty())
            {
                source.sendSuccess(() -> Component.translatable(BUILDING, site.pos().getX(), site.pos().getZ()), false);
            }
            ColossusLair.whenBuilt(server, site, lair -> {
                if (isCurrent(request))
                {
                    PENDING.remove(player.getUUID());
                    teleportTo(source, dross, player, lair);
                }
            });
        }, server);
        return 1;
    }

    /** True if this request is still the player's newest, and the player is still online. */
    private static boolean isCurrent(Pending request)
    {
        Pending now = PENDING.get(request.player().getUUID());
        return now != null && now.number() == request.number()
                && request.player().server.getPlayerList().getPlayer(request.player().getUUID()) == request.player();
    }

    /** Drops requests whose player has gone, and gives up on the ones that have waited too long. */
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty())
        {
            return;
        }
        long now = Util.getMillis();
        Iterator<Pending> it = PENDING.values().iterator();
        while (it.hasNext())
        {
            Pending request = it.next();
            if (request.player().server.getPlayerList().getPlayer(request.player().getUUID()) != request.player())
            {
                it.remove(); // logged out
            }
            else if (now > request.deadline())
            {
                it.remove();
                LOGGER.warn("[goto lair] {}: gave up waiting for the Colossus lair after {} s", request.player().getName().getString(),
                        BUILD_WAIT_MILLIS / 1000);
                request.source().sendFailure(Component.translatable(TIMEOUT));
            }
        }
    }

    /** True while the player's {@code goto lair colossus} is still waiting (tests). */
    static boolean isWaiting(ServerPlayer player)
    {
        return PENDING.containsKey(player.getUUID());
    }

    /**
     * Teleports the player to a built lair's entrance (or the nearest safe spot round it), facing in, with the chat line.
     *
     * @return 1 if the player was teleported; 0 if not (the reason is said in chat)
     */
    static int teleportTo(CommandSourceStack source, ServerLevel dross, ServerPlayer player, ColossusLair.Lair lair)
    {
        Vec3 spot = lair.entrance();
        float yaw = lair.entranceYaw();
        if (!canStand(dross, player, spot))
        {
            BlockPos entrance = BlockPos.containing(spot);
            Optional<Vec3> nearby = DrossTeleporter.findSafeSpot(dross, player, entrance.getX(), entrance.getZ(), ENTRANCE_FALLBACK_RADIUS);
            if (nearby.isPresent())
            {
                LOGGER.warn("[goto lair] The Colossus lair's entrance at {} isn't safe to stand on any more (its blocks were changed); "
                        + "using {} instead.", entrance.toShortString(), BlockPos.containing(nearby.get()).toShortString());
                spot = nearby.get();
            }
            else
            {
                LOGGER.warn("[goto lair] The Colossus lair's entrance at {} isn't safe to stand on any more, and there's no safe spot "
                        + "within {} blocks; using it anyway.", entrance.toShortString(), ENTRANCE_FALLBACK_RADIUS);
            }
        }

        // Like vanilla's /tp: keeps the landing chunk loaded for a moment, gets the player off a mount or out of bed.
        player.teleportTo(dross, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F);
        if (player.level() != dross)
        {
            LOGGER.warn("[goto lair] {}: something stopped the teleport to the Dross (another mod cancelled the change of dimension)",
                    player.getName().getString());
            source.sendFailure(Component.translatable(STOPPED));
            return 0;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(true);
        player.resetFallDistance(); // a fall before the teleport doesn't hurt on landing

        BlockPos feet = BlockPos.containing(spot);
        ColossusLair.State state = ColossusLair.state(dross.getServer(), lair);
        Component note = riseNote(dross, state);
        LOGGER.info("[goto lair] {}: teleported to the entrance of the Colossus lair at {} {} (group {}) at {}, facing the tunnel ({}); its Colossus: {}",
                player.getName().getString(), lair.center().getX(), lair.center().getZ(), lair.group() + 1, feet.toShortString(),
                lair.tunnelDirection().getOpposite().getName(), state);
        source.sendSuccess(() -> Component.translatable(SUCCESS, feet.getX(), feet.getY(), feet.getZ(), note), true);
        return 1;
    }

    /** Whether the Colossus rises when you walk in, is awake, has been beaten (for good), or never rises (Peaceful). */
    static Component riseNote(ServerLevel dross, ColossusLair.State state)
    {
        if (state == ColossusLair.State.BEATEN)
        {
            return Component.translatable(BEATEN);
        }
        if (dross.getDifficulty() == Difficulty.PEACEFUL)
        {
            return Component.translatable(PEACEFUL);
        }
        return Component.translatable(state == ColossusLair.State.AWAKE ? AWAKE : READY);
    }

    // ---------------------------------------------------------------- goto lair ratking

    /**
     * Runs {@code /dross goto lair ratking}: into the nearest rat cave (the Rat King's home under Blightwood, the boss area's
     * {@link RatKingLair}; the caves themselves are the dimension area's {@code RatCaves}): searching from where the player
     * stands if they're in the Dross, otherwise from the hub ({@link DrossHub#CENTER}). The cave's chunks are generated
     * fully first, then the player lands on its arrival spot (the end of a tunnel leading in, at least 16 blocks from the
     * nest, on the cave's floor), facing the nest. If someone has changed the blocks there so it's no longer safe to
     * stand, the nearest safe spot on the cave's floor within {@value #CAVE_ARRIVAL_FALLBACK_RADIUS} blocks is used
     * (still facing the nest).
     *
     * @return 1 if the player was teleported; 0 if not (the reason is said in chat)
     * @throws CommandSyntaxException if it wasn't used by a player (the server console, a command block)
     */
    static int ratKing(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            source.sendFailure(Component.translatable(NO_DROSS));
            return 0;
        }
        boolean fromHere = player.level() == dross;
        BlockPos from = fromHere ? player.blockPosition() : DrossHub.CENTER;
        long started = Util.getMillis();
        Optional<RatCaves.RatCave> found = RatKingLair.nearest(dross, from);
        if (found.isEmpty())
        {
            LOGGER.info("[goto lair] {}: no rat cave found from {} ({} ms)", player.getName().getString(), from.toShortString(),
                    Util.getMillis() - started);
            source.sendFailure(Component.translatable(RAT_KING_NOT_FOUND));
            return 0;
        }
        RatCaves.RatCave cave = found.get();
        RatKingLair.prepare(dross, cave); // its blocks are put down as its chunks generate: all of them first
        BlockPos arrival = cave.arrival();
        Vec3 spot = new Vec3(arrival.getX() + 0.5D, arrival.getY(), arrival.getZ() + 0.5D);
        if (!canStand(dross, player, spot))
        {
            Optional<Vec3> nearby = caveFloorSpot(dross, player, arrival);
            if (nearby.isPresent())
            {
                LOGGER.warn("[goto lair] The rat cave's arrival spot at {} isn't safe to stand on any more (its blocks were changed); using {} instead.",
                        arrival.toShortString(), BlockPos.containing(nearby.get()).toShortString());
                spot = nearby.get();
            }
            else
            {
                LOGGER.warn("[goto lair] The rat cave's arrival spot at {} isn't safe to stand on any more, and there's no safe spot within {} "
                        + "blocks on its floor; using it anyway.", arrival.toShortString(), CAVE_ARRIVAL_FALLBACK_RADIUS);
            }
        }
        float yaw = cave.arrivalYaw();

        player.teleportTo(dross, spot.x, spot.y, spot.z, Set.of(), yaw, 0.0F);
        if (player.level() != dross)
        {
            LOGGER.warn("[goto lair] {}: something stopped the teleport to the Dross (another mod cancelled the change of dimension)",
                    player.getName().getString());
            source.sendFailure(Component.translatable(STOPPED));
            return 0;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(true);
        player.resetFallDistance();

        BlockPos feet = BlockPos.containing(spot);
        Component note = ratKingNote(dross, RatKingLair.isBeaten(server, cave), RatKingLair.isKingAwake(server, cave));
        LOGGER.info("[goto lair] {}: teleported into the rat cave at {} (its nest at {}; searched from {} {}, {} ms): \"{}\"",
                player.getName().getString(), feet.toShortString(), cave.nest().toShortString(), fromHere ? "where they stood," : "the hub,",
                from.toShortString(), Util.getMillis() - started, note.getString());
        source.sendSuccess(() -> Component.translatable(RAT_KING_SUCCESS, feet.getX(), feet.getY(), feet.getZ(), note), true);
        return 1;
    }

    /** Whether the Rat King is asleep on his nest, awake, beaten (for good), or never there (Peaceful). */
    static Component ratKingNote(ServerLevel dross, boolean beaten, boolean awake)
    {
        if (beaten)
        {
            return Component.translatable(RAT_KING_BEATEN);
        }
        if (dross.getDifficulty() == Difficulty.PEACEFUL)
        {
            return Component.translatable(RAT_KING_PEACEFUL);
        }
        return Component.translatable(awake ? RAT_KING_AWAKE : RAT_KING_READY);
    }

    /** The nearest spot round a rat cave's arrival spot, on its floor, where the player can stand (nearest first). */
    private static Optional<Vec3> caveFloorSpot(ServerLevel dross, ServerPlayer player, BlockPos arrival)
    {
        for (int r = 1; r <= CAVE_ARRIVAL_FALLBACK_RADIUS; r++)
        {
            for (int dy = -CAVE_ARRIVAL_FALLBACK_DOWN; dy <= CAVE_ARRIVAL_FALLBACK_UP; dy++)
            {
                for (int dx = -r; dx <= r; dx++)
                {
                    for (int dz = -r; dz <= r; dz++)
                    {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                        {
                            continue; // only this ring
                        }
                        Vec3 spot = new Vec3(arrival.getX() + dx + 0.5D, arrival.getY() + dy, arrival.getZ() + dz + 0.5D);
                        if (canStand(dross, player, spot))
                        {
                            return Optional.of(spot);
                        }
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Room for the player standing up, no liquid, and something solid right under the feet. Loads the chunk. */
    static boolean canStand(ServerLevel level, ServerPlayer player, Vec3 pos)
    {
        level.getChunkAt(BlockPos.containing(pos));
        AABB box = player.getDimensions(Pose.STANDING).makeBoundingBox(pos);
        if (!level.noCollision(player, box) || level.containsAnyLiquid(box))
        {
            return false;
        }
        AABB floor = new AABB(box.minX, box.minY - 0.5D, box.minZ, box.maxX, box.minY, box.maxZ);
        return !level.noCollision(player, floor);
    }
}
