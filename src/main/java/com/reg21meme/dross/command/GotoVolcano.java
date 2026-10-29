package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.emberdactyl.EmberVolcanoData;
import com.reg21meme.dross.boss.emberdactyl.EmberVolcanoes;
import com.reg21meme.dross.dimension.ModDimensions;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * {@code /dross goto lair emberdactyl}: to the foot of the path up the <b>nearest</b> Emberdactyl volcano (the boss area's
 * {@link EmberVolcanoes}: one in every group of 3 Cinder Wastes patches), nearest to the player if they're in the Dross,
 * to the hub otherwise, facing up the path. A volcano is built the first time a player comes near it, so if the nearest
 * hasn't been built yet the command says so, has it built at once (it takes a little while: its land loads first) and
 * teleports the player when it's ready, never onto a half-built one; it gives up after {@link GotoLair#BUILD_WAIT_MILLIS}.
 * Wired into {@code /dross goto lair} by {@link DrossCommand}.
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class GotoVolcano
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---- Chat messages: lang keys in en_us.json ----
    /** "Looking for the nearest Emberdactyl volcano..." */
    static final String SEARCHING = "commands.dross.goto.lair.emberdactyl.searching";
    /** "The nearest Emberdactyl volcano, at X, Z, hasn't been built yet: building it now..." */
    static final String BUILDING = "commands.dross.goto.lair.emberdactyl.building";
    /** "Found no Emberdactyl volcano in the Dross..." */
    static final String NOT_FOUND = "commands.dross.goto.lair.emberdactyl.not_found";
    /** "The volcano took too long to build, so you weren't moved..." */
    static final String TIMEOUT = "commands.dross.goto.lair.emberdactyl.timeout";
    /** "Teleported to the foot of the path up the Emberdactyl's volcano at X, Y, Z." */
    static final String SUCCESS = "commands.dross.goto.lair.emberdactyl.success";

    /** The requests still waiting, by player (a newer one replaces an older one). Server thread. */
    private static final Map<UUID, Pending> PENDING = new HashMap<>();
    private static long nextRequest;

    private record Pending(long number, CommandSourceStack source, ServerPlayer player, long deadline)
    {
    }

    private GotoVolcano() {}

    /**
     * Runs {@code /dross goto lair emberdactyl}: starts the search for the nearest volcano (the teleport follows when it's
     * found and built).
     *
     * @return 1 if the search started; 0 if not (the reason is said in chat)
     */
    static int emberdactyl(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            source.sendFailure(Component.translatable(GotoLair.NO_DROSS));
            return 0;
        }
        boolean fromHere = player.level() == dross;
        BlockPos from = fromHere ? player.blockPosition() : DrossHub.CENTER;
        Pending request = new Pending(++nextRequest, source, player, Util.getMillis() + GotoLair.BUILD_WAIT_MILLIS);
        PENDING.put(player.getUUID(), request);
        source.sendSuccess(() -> Component.translatable(SEARCHING), false);
        long started = Util.getMillis();
        EmberVolcanoes.findNearestSite(dross, from).whenCompleteAsync((found, error) -> {
            if (!isCurrent(request))
            {
                return;
            }
            if (error != null || found.isEmpty())
            {
                if (error != null)
                {
                    LOGGER.error("[goto lair] {}: finding the nearest Emberdactyl volcano failed", player.getName().getString(), error);
                }
                PENDING.remove(player.getUUID());
                source.sendFailure(Component.translatable(NOT_FOUND));
                return;
            }
            Site site = found.get();
            boolean built = EmberVolcanoes.get(server, site).isPresent();
            LOGGER.info("[goto lair] {}: the nearest Emberdactyl volcano (from {} {}) is the one of Cinder Wastes group {} at {} {}, {} ({} ms)",
                    player.getName().getString(), fromHere ? "where they stood," : "the hub,", from.toShortString(), site.patch().group() + 1,
                    site.pos().getX(), site.pos().getZ(), built ? "built" : "not built yet", Util.getMillis() - started);
            if (!built)
            {
                source.sendSuccess(() -> Component.translatable(BUILDING, site.pos().getX(), site.pos().getZ()), false);
            }
            EmberVolcanoes.whenBuilt(server, site, volcano -> {
                if (isCurrent(request))
                {
                    PENDING.remove(player.getUUID());
                    teleportTo(source, dross, player, volcano);
                }
            });
        }, server);
        return 1;
    }

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
                it.remove();
            }
            else if (now > request.deadline())
            {
                it.remove();
                LOGGER.warn("[goto lair] {}: gave up waiting for the Emberdactyl volcano after {} s", request.player().getName().getString(),
                        GotoLair.BUILD_WAIT_MILLIS / 1000);
                request.source().sendFailure(Component.translatable(TIMEOUT));
            }
        }
    }

    /** Teleports the player to a built volcano's path foot (or the nearest safe spot round it), facing up the path. */
    static int teleportTo(CommandSourceStack source, ServerLevel dross, ServerPlayer player, EmberVolcanoData.Volcano volcano)
    {
        Vec3 spot = volcano.pathFoot();
        if (!GotoLair.canStand(dross, player, spot))
        {
            BlockPos foot = BlockPos.containing(spot);
            Optional<Vec3> nearby = DrossTeleporter.findSafeSpot(dross, player, foot.getX(), foot.getZ(), GotoLair.ENTRANCE_FALLBACK_RADIUS);
            if (nearby.isPresent())
            {
                LOGGER.warn("[goto lair] The Emberdactyl volcano's path foot at {} isn't safe to stand on; using {} instead.", foot.toShortString(),
                        BlockPos.containing(nearby.get()).toShortString());
                spot = nearby.get();
            }
        }
        player.teleportTo(dross, spot.x, spot.y, spot.z, Set.of(), volcano.pathFootYaw(), 0.0F);
        if (player.level() != dross)
        {
            source.sendFailure(Component.translatable(GotoLair.STOPPED));
            return 0;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.setOnGround(true);
        player.resetFallDistance();
        BlockPos feet = BlockPos.containing(spot);
        LOGGER.info("[goto lair] {}: teleported to the foot of the path up the Emberdactyl volcano at {} {} (group {}) at {}",
                player.getName().getString(), volcano.ground().getX(), volcano.ground().getZ(), volcano.group() + 1, feet.toShortString());
        source.sendSuccess(() -> Component.translatable(SUCCESS, feet.getX(), feet.getY(), feet.getZ()), true);
        return 1;
    }
}
