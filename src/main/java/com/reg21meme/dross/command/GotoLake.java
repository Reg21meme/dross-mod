package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallAreas;
import com.reg21meme.dross.dimension.rimefall.RimefallLake;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
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
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * {@code /dross goto lake}: teleports to the shore of the nearest of Rimefall's big frozen lakes (one in every Rimefall
 * patch: the dimension area's {@link RimefallAreas}), on the side facing where you searched from, facing the lake.
 * Searched from where the player stands if they're in the Dross, otherwise from the hub ({@link DrossHub#CENTER}). The
 * shore's chunk is generated first (its part of the lake is built with it); the landing is the nearest safe ground there
 * ({@link DrossTeleporter#findSafeSpot}). Wired into {@link DrossCommand}.
 */
public final class GotoLake
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---- Chat messages: lang keys in en_us.json ----
    static final String NO_DROSS = "commands.dross.goto.lake.no_dross";
    static final String NOT_FOUND = "commands.dross.goto.lake.not_found";
    static final String STOPPED = "commands.dross.goto.lake.stopped";
    static final String SUCCESS = "commands.dross.goto.lake.success";

    // ---- Tuning ----
    /** How far it looks for a lake (blocks). */
    public static final int SEARCH_RADIUS = 12_000;
    /** You land this many blocks past the lake's edge, on its shore. */
    public static final int SHORE_STEP = 4;
    /** If that spot isn't safe, the nearest safe ground is looked for this far round it. */
    public static final int FALLBACK_RADIUS = 6;

    private GotoLake() {}

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
        Optional<RimefallLake> found = BossSites.forLevel(dross).nearest(BossSites.RIMEFALL_AREA, from, SEARCH_RADIUS)
                .map(Site::detail).map(d -> ((RimefallAreas.RimefallArea) d).lake());
        if (found.isEmpty())
        {
            LOGGER.info("[goto lake] {}: no Rimefall lake found from {} ({} ms)", player.getName().getString(), from.toShortString(),
                    Util.getMillis() - started);
            source.sendFailure(Component.translatable(NOT_FOUND));
            return 0;
        }
        RimefallLake lake = found.get();
        BlockPos centre = lake.centre();
        double angle = Math.atan2(from.getZ() - centre.getZ(), from.getX() - centre.getX());
        double r = lake.edge(angle) + SHORE_STEP;
        int x = (int) Math.round(centre.getX() + Math.cos(angle) * r);
        int z = (int) Math.round(centre.getZ() + Math.sin(angle) * r);
        Optional<Vec3> landing = Optional.empty();
        for (int ring = 0; ring <= FALLBACK_RADIUS && landing.isEmpty(); ring++)
        {
            for (int dx = -ring; dx <= ring && landing.isEmpty(); dx++)
            {
                for (int dz = -ring; dz <= ring && landing.isEmpty(); dz++)
                {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == ring && !lake.inLake(x + dx, z + dz))
                    {
                        dross.getChunkAt(new BlockPos(x + dx, 0, z + dz));
                        landing = DrossTeleporter.findSafeSpot(dross, player, x + dx, z + dz, 0);
                    }
                }
            }
        }
        if (landing.isEmpty())
        {
            source.sendFailure(Component.translatable(NOT_FOUND));
            return 0;
        }
        Vec3 spot = landing.get();
        float yaw = (float) (Mth.atan2(centre.getZ() + 0.5D - spot.z, centre.getX() + 0.5D - spot.x) * (180.0D / Math.PI)) - 90.0F;
        player.teleportTo(dross, spot.x, spot.y, spot.z, Set.of(), yaw, 10.0F);
        if (player.level() != dross)
        {
            source.sendFailure(Component.translatable(STOPPED));
            return 0;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        BlockPos feet = BlockPos.containing(spot);
        LOGGER.info("[goto lake] {}: teleported to the shore of the {} ({} ms)", player.getName().getString(), lake, Util.getMillis() - started);
        source.sendSuccess(() -> Component.translatable(SUCCESS, feet.getX(), feet.getY(), feet.getZ(), Math.round(2.0D * lake.radius()),
                lake.holeCount()), true);
        return 1;
    }
}
