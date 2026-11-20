package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.boss.knight.ruin.KnightRuins;
import com.reg21meme.dross.boss.knight.ruin.RuinSite;
import com.reg21meme.dross.portal.DrossTeleporter;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * {@code /dross goto ruin [<element>]}, {@code /dross goto ruin <x> <z>} and {@code /dross locate ruins}: the elemental
 * knights' ruins in the Overworld (the boss area's {@link KnightRuins}). Searched from where the player stands in the
 * Overworld, from the world spawn elsewhere; worked out off the server thread (a moment the first time), then the ruin's
 * chunks are made (it's built with them) and the player lands on safe ground just out of its way in, facing it. The
 * locate list has a [go] button per ruin ({@code goto ruin <x> <z>}). Wired into {@link DrossCommand}.
 */
public final class GotoRuin
{
    private static final Logger LOGGER = LogUtils.getLogger();

    static final String NONE = "commands.dross.goto.ruin.none";
    static final String LOOKING = "commands.dross.goto.ruin.looking";
    static final String NOT_FOUND = "commands.dross.goto.ruin.not_found";
    static final String STOPPED = "commands.dross.goto.ruin.stopped";
    static final String SUCCESS = "commands.dross.goto.ruin.success";
    static final String LIST_HEADER = "commands.dross.locate.ruins.header";
    static final String LIST_NONE = "commands.dross.locate.ruins.none";
    static final String LIST_LINE = "commands.dross.locate.ruins.line";
    static final String LIST_GO = "commands.dross.locate.ruins.go";
    static final String NEAR_SPAWN = "commands.dross.locate.ruins.near_spawn";
    static final String BEATEN = "commands.dross.locate.ruins.beaten";

    /** How far the search for the nearest ruin goes. */
    static final int SEARCH_RADIUS = 6000;
    /** The locate list: ruins within this many blocks, at most this many. */
    static final int LIST_RADIUS = 2400;
    static final int LIST_COUNT = 8;
    /** A [go] button's ruin is looked for this far round its spot. */
    static final int AT_RADIUS = 600;
    static final int FALLBACK_RADIUS = 8;

    private GotoRuin() {}

    static int nearest(CommandSourceStack source, @Nullable KnightKind.Element element) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        if (!KnightRuins.available(server))
        {
            source.sendFailure(Component.translatable(NONE));
            return 0;
        }
        BlockPos from = from(player);
        long started = Util.getMillis();
        source.sendSuccess(() -> Component.translatable(LOOKING), false);
        KnightRuins.nearestAsync(server, from, element, SEARCH_RADIUS).whenComplete((found, error) -> server.execute(() ->
        {
            if (error != null || found == null || found.isEmpty())
            {
                if (error != null)
                {
                    LOGGER.error("[goto ruin] the search failed", error);
                }
                source.sendFailure(Component.translatable(NOT_FOUND, SEARCH_RADIUS));
                return;
            }
            LOGGER.info("[goto ruin] {}: found {} from {} ({} ms)", player.getName().getString(), found.get().describe(),
                    from.toShortString(), Util.getMillis() - started);
            teleport(source, player, found.get());
        }));
        return 1;
    }

    static int at(CommandSourceStack source, int x, int z) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        if (!KnightRuins.available(server))
        {
            source.sendFailure(Component.translatable(NONE));
            return 0;
        }
        KnightRuins.nearestAsync(server, new BlockPos(x, 0, z), null, AT_RADIUS).whenComplete((found, error) -> server.execute(() ->
        {
            if (error != null || found == null || found.isEmpty())
            {
                source.sendFailure(Component.translatable(NOT_FOUND, AT_RADIUS));
                return;
            }
            teleport(source, player, found.get());
        }));
        return 1;
    }

    static int locate(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        MinecraftServer server = source.getServer();
        if (!KnightRuins.available(server))
        {
            source.sendFailure(Component.translatable(NONE));
            return 0;
        }
        BlockPos from = from(player);
        boolean fromHere = player.level().dimension() == Level.OVERWORLD;
        long started = Util.getMillis();
        source.sendSuccess(() -> Component.translatable(LOOKING), false);
        CompletableFuture.supplyAsync(() -> KnightRuins.within(server, from, LIST_RADIUS)).whenComplete((list, error) -> server.execute(() ->
        {
            if (error != null || list == null || list.isEmpty())
            {
                if (error != null)
                {
                    LOGGER.error("[locate ruins] the search failed", error);
                }
                source.sendFailure(Component.translatable(LIST_NONE, LIST_RADIUS));
                return;
            }
            List<RuinSite> shown = list.subList(0, Math.min(LIST_COUNT, list.size()));
            LOGGER.info("[locate ruins] {}: {} ruin(s) within {} of {} ({} ms)", player.getName().getString(), list.size(), LIST_RADIUS,
                    from.toShortString(), Util.getMillis() - started);
            source.sendSuccess(() -> Component.translatable(LIST_HEADER, shown.size(),
                    fromHere ? Component.literal("you") : Component.literal("the world spawn")), false);
            int n = 0;
            for (RuinSite site : shown)
            {
                n++;
                int distance = (int) Math.round(Math.sqrt(Mth.square((double) (site.x() - from.getX())) + Mth.square((double) (site.z() - from.getZ()))));
                String command = String.format(Locale.ROOT, "/dross goto ruin %d %d", site.x(), site.z());
                MutableComponent go = Component.translatable(LIST_GO).withStyle(style -> style
                        .withColor(DrossColors.KNIGHT_RUIN_BUTTON)
                        .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                        .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
                int colour = elementColour(site.element());
                MutableComponent element = Component.literal(title(site.element())).withStyle(style -> style.withColor(colour));
                MutableComponent extra = Component.empty();
                if (site.nearSpawn())
                {
                    extra.append(Component.translatable(NEAR_SPAWN));
                }
                if (KnightRuins.knightsBeaten(server, site))
                {
                    extra.append(Component.translatable(BEATEN));
                }
                Component line = Component.translatable(LIST_LINE, n, element, site.design().area().name(), site.design().name(), site.x(),
                        site.groundY(), site.z(), distance, extra);
                Component full = go.append(Component.literal(" ")).append(line);
                source.sendSuccess(() -> full, false);
            }
        }));
        return 1;
    }

    private static BlockPos from(ServerPlayer player)
    {
        if (player.level().dimension() == Level.OVERWORLD)
        {
            return player.blockPosition();
        }
        return player.getServer().overworld().getSharedSpawnPos();
    }

    /** Puts the player just out of the ruin's way in, facing it. */
    private static void teleport(CommandSourceStack source, ServerPlayer player, RuinSite site)
    {
        ServerLevel overworld = source.getServer().overworld();
        overworld.getChunkAt(site.ground());
        BlockPos stand = site.doorstep();
        overworld.getChunkAt(stand);
        Optional<Vec3> landing = DrossTeleporter.findSafeSpot(overworld, player, stand.getX(), stand.getZ(), FALLBACK_RADIUS);
        if (landing.isEmpty())
        {
            source.sendFailure(Component.translatable(STOPPED));
            return;
        }
        Vec3 spot = landing.get();
        float yaw = (float) (Mth.atan2(site.z() + 0.5D - spot.z, site.x() + 0.5D - spot.x) * (180.0D / Math.PI)) - 90.0F;
        player.teleportTo(overworld, spot.x, spot.y, spot.z, Set.of(), yaw, 10.0F);
        if (player.level() != overworld)
        {
            source.sendFailure(Component.translatable(STOPPED));
            return;
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        BlockPos feet = BlockPos.containing(spot);
        LOGGER.info("[goto ruin] {}: teleported to {}", player.getName().getString(), site.describe());
        source.sendSuccess(() -> Component.translatable(SUCCESS, title(site.element()), site.design().area().name(), site.design().name(),
                feet.getX(), feet.getY(), feet.getZ()), true);
    }

    static String title(KnightKind.Element element)
    {
        String n = element.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(n.charAt(0)) + n.substring(1);
    }

    static int elementColour(KnightKind.Element element)
    {
        return switch (element)
        {
            case FIRE -> DrossColors.KNIGHT_FIRE;
            case ICE -> DrossColors.KNIGHT_ICE;
            case POISON -> DrossColors.KNIGHT_POISON;
            case THUNDER -> DrossColors.KNIGHT_THUNDER;
            case EARTH -> DrossColors.KNIGHT_EARTH;
        };
    }
}
