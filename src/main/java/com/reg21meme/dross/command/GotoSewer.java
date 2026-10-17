package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.Sewers;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.portal.DrossTeleporter;
import java.util.Optional;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * {@code /dross goto sewer}: teleports to the nearest regular sewer under Blightwood (the dimension area's
 * {@link Sewers#findNearest}: a network of tunnels with 3 copper pipes for ways in, rats only, no king), at the foot of its
 * first pipe's steps, facing the pipe. Searched from where the player stands if they're in the Dross, otherwise from the
 * hub ({@link DrossHub#CENTER}). The pipe's ground is generated first; the landing is that spot if it's safe to stand on,
 * otherwise the nearest safe ground within {@value #APPROACH_FALLBACK_RADIUS} blocks of it ({@link DrossTeleporter#findSafeSpot}),
 * still facing the pipe. Wired into {@link DrossCommand}. (The Rat King's sewer is {@code goto lair ratking}.)
 */
public final class GotoSewer
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---- Chat messages: lang keys in en_us.json ----
    /** "The Dross dimension isn't loaded, so there's no sewer to go to." */
    static final String NO_DROSS = "commands.dross.goto.sewer.no_dross";
    /** "Found no sewer in the Dross (...)." */
    static final String NOT_FOUND = "commands.dross.goto.sewer.not_found";
    /** "Something stopped you from going to the Dross." */
    static final String STOPPED = "commands.dross.goto.sewer.stopped";
    /** "Teleported to the foot of a sewer pipe at X, Y, Z (this sewer has N ways in)..." */
    static final String SUCCESS = "commands.dross.goto.sewer.success";

    // ---- Tuning ----
    /** If the foot of the pipe's steps isn't safe to stand on, the nearest safe ground is looked for this far round it. */
    public static final int APPROACH_FALLBACK_RADIUS = 6;
    /** The safe spot used must be within this many blocks (up or down) of the ground the pipe stands on. */
    public static final int APPROACH_MAX_HEIGHT_OFF = 4;

    private GotoSewer() {}

    /**
     * Runs {@code /dross goto sewer}.
     *
     * @return 1 if the player was teleported; 0 if not (the reason is said in chat)
     * @throws CommandSyntaxException if it wasn't used by a player
     */
    static int teleport(CommandSourceStack source) throws CommandSyntaxException
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
        Optional<Sewers.Network> found = Sewers.findNearest(dross, from);
        if (found.isEmpty() || found.get().entrances().isEmpty())
        {
            LOGGER.info("[goto sewer] {}: no sewer found from {} ({} ms)", player.getName().getString(), from.toShortString(), Util.getMillis() - started);
            source.sendFailure(Component.translatable(NOT_FOUND));
            return 0;
        }
        Sewers.Network network = found.get();
        Sewers.Entrance entrance = network.entrances().get(0);
        Optional<Vec3> landing = landing(dross, player, entrance);
        if (landing.isEmpty())
        {
            LOGGER.warn("[goto sewer] {}: no safe ground at the foot of the sewer pipe at {} (within {} blocks)", player.getName().getString(),
                    entrance.opening().toShortString(), APPROACH_FALLBACK_RADIUS);
            source.sendFailure(Component.translatable(NOT_FOUND));
            return 0;
        }
        Vec3 spot = landing.get();
        player.teleportTo(dross, spot.x, spot.y, spot.z, Set.of(), entrance.approachYaw(), 0.0F);
        if (player.level() != dross)
        {
            source.sendFailure(Component.translatable(STOPPED));
            return 0;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(true);
        player.resetFallDistance();
        BlockPos feet = BlockPos.containing(spot);
        int ways = network.entrances().size();
        LOGGER.info("[goto sewer] {}: teleported to the foot of the sewer pipe at {} (network {} at {}, {} ways in; searched from {} {}, {} ms)",
                player.getName().getString(), feet.toShortString(), network.id(), network.startChunk(), ways, fromHere ? "where they stood," : "the hub,",
                from.toShortString(), Util.getMillis() - started);
        source.sendSuccess(() -> Component.translatable(SUCCESS, feet.getX(), feet.getY(), feet.getZ(), ways), true);
        return 1;
    }

    /**
     * Where to land for this entrance: the foot of its steps if it's safe ground, otherwise the nearest safe ground round it
     * (within {@value #APPROACH_FALLBACK_RADIUS} blocks, and within {@value #APPROACH_MAX_HEIGHT_OFF} blocks of the
     * ground the pipe stands on, so never on the pipe's rim). Generates the chunks there.
     */
    static Optional<Vec3> landing(ServerLevel dross, ServerPlayer player, Sewers.Entrance entrance)
    {
        BlockPos approach = entrance.approach();
        dross.getChunkAt(approach); // its blocks (the pipe, its steps) are put down as its chunk generates
        Vec3 exact = new Vec3(approach.getX() + 0.5D, approach.getY(), approach.getZ() + 0.5D);
        if (GotoLair.canStand(dross, player, exact))
        {
            return Optional.of(exact);
        }
        for (int r = 0; r <= APPROACH_FALLBACK_RADIUS; r++)
        {
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                    {
                        continue; // only this ring: nearest first
                    }
                    Optional<Vec3> near = DrossTeleporter.findSafeSpot(dross, player, approach.getX() + dx, approach.getZ() + dz, 0);
                    if (near.isPresent() && Math.abs(near.get().y - (entrance.groundY() + 1)) <= APPROACH_MAX_HEIGHT_OFF)
                    {
                        return near;
                    }
                }
            }
        }
        return Optional.empty();
    }
}
