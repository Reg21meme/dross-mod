package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.fire.cave.HermitCaveSites;
import com.reg21meme.dross.boss.fire.cave.HermitCaveTuning;
import com.reg21meme.dross.boss.fire.cave.HermitCaves;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.portal.DrossTeleporter;
import java.util.Optional;
import java.util.Set;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * {@code /dross goto hermit_cave}: teleports to the way in of the nearest hermit crab cave (the boss area's caves, 1-2
 * in every Cinder Wastes area: {@link HermitCaveSites}), a few blocks out in front of it, facing in. Searched from where
 * the player stands if they're in the Dross, otherwise from the hub ({@link DrossHub#CENTER}). The cave's chunks are
 * generated first (it's built with them); the landing is the nearest safe ground there
 * ({@link DrossTeleporter#findSafeSpot}). Wired into {@link DrossCommand}.
 */
public final class GotoHermitCave
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---- Chat messages: lang keys in en_us.json ----
    static final String NO_DROSS = "commands.dross.goto.hermit_cave.no_dross";
    static final String NOT_FOUND = "commands.dross.goto.hermit_cave.not_found";
    static final String STOPPED = "commands.dross.goto.hermit_cave.stopped";
    static final String SUCCESS = "commands.dross.goto.hermit_cave.success";

    /** If the spot in front of the way in isn't safe, the nearest safe ground is looked for this far round it. */
    static final int FALLBACK_RADIUS = 6;

    private GotoHermitCave() {}

    static int teleport(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel dross = source.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            source.sendFailure(Component.translatable(NO_DROSS));
            return 0;
        }
        boolean fromHere = player.level() == dross;
        BlockPos from = fromHere ? player.blockPosition() : DrossHub.CENTER;
        long started = Util.getMillis();
        Optional<HermitCaveSites.CavePlan> found = HermitCaveSites.nearest(BossSites.forLevel(dross), from,
                HermitCaveTuning.GOTO_SEARCH_RADIUS);
        if (found.isEmpty())
        {
            LOGGER.info("[goto hermit_cave] {}: no cave found from {} ({} ms)", player.getName().getString(),
                    from.toShortString(), Util.getMillis() - started);
            source.sendFailure(Component.translatable(NOT_FOUND));
            return 0;
        }
        HermitCaveSites.CavePlan cave = found.get();
        // its chunks (and so the cave itself) are made before anyone stands there
        dross.getChunkAt(cave.middle());
        BlockPos door = HermitCaves.door(cave.middle().atY(0), cave.front(), cave.cave());
        BlockPos stand = door.relative(cave.front(), HermitCaveTuning.GOTO_STAND_OUT);
        dross.getChunkAt(stand);
        Optional<Vec3> landing = DrossTeleporter.findSafeSpot(dross, player, stand.getX(), stand.getZ(), FALLBACK_RADIUS);
        if (landing.isEmpty())
        {
            source.sendFailure(Component.translatable(NOT_FOUND));
            return 0;
        }
        Vec3 spot = landing.get();
        float yaw = cave.front().getOpposite().toYRot();
        player.teleportTo(dross, spot.x, spot.y, spot.z, Set.of(), yaw, 15.0F);
        if (player.level() != dross)
        {
            source.sendFailure(Component.translatable(STOPPED));
            return 0;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        BlockPos feet = BlockPos.containing(spot);
        LOGGER.info("[goto hermit_cave] {}: teleported to the {} at {} ({} ms)", player.getName().getString(),
                cave.cave().name(), cave.middle().toShortString(), Util.getMillis() - started);
        source.sendSuccess(() -> Component.translatable(SUCCESS, cave.cave().name(), feet.getX(), feet.getY(), feet.getZ()), true);
        return 1;
    }
}
