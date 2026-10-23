package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.DrossColors;
import com.reg21meme.dross.boss.huntsman.village.HuntsmanVillages;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.rimefall.RimefallVillages;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.portal.DrossTeleporter;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * Rimefall's hunter villages (October 23, 2026; the villages are the dimension area's {@link RimefallVillages}, placed
 * by the boss-site planner: one Huntsman's village in every group of 3 Rimefall patches, a regular one on each of the
 * other two half the time). Wired into {@link DrossCommand}:
 * <ul>
 *   <li>{@code /dross goto lair huntsman} ({@link #huntsman}): the way in of the nearest Huntsman's village, facing in,
 *       with a note: he's at home, beaten, or never there (Peaceful). He and his Alpha Moose come to his yard once
 *       you're near.</li>
 *   <li>{@code /dross goto village} ({@link #nearest}): the way in of the nearest hunter village of either kind;
 *       {@code goto village <x> <z>} the one nearest that spot (the list's [go] buttons).</li>
 *   <li>{@code /dross locate villages} ({@link #locate}, a test tool for now: the user's "temporary command"): the
 *       {@value #LIST_COUNT} nearest villages in chat, their kind, where and how far, each with a [go] button.</li>
 * </ul>
 * Searched from where the player stands if they're in the Dross, otherwise from the hub ({@link DrossHub#CENTER}). The
 * landing's chunk is generated first (the village is built with its chunks); the landing is the nearest safe ground
 * round the way in ({@link DrossTeleporter#findSafeSpot}).
 */
public final class GotoVillage
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---- Chat messages: lang keys in en_us.json ----
    static final String NO_DROSS = "commands.dross.goto.village.no_dross";
    static final String STOPPED = "commands.dross.goto.village.stopped";
    static final String NOT_FOUND = "commands.dross.goto.village.not_found";
    static final String SUCCESS = "commands.dross.goto.village.success";
    static final String KIND_HUNTSMAN = "commands.dross.goto.village.kind.huntsman";
    static final String KIND_REGULAR = "commands.dross.goto.village.kind.regular";
    static final String HUNTSMAN_NOT_FOUND = "commands.dross.goto.lair.huntsman.not_found";
    static final String HUNTSMAN_SUCCESS = "commands.dross.goto.lair.huntsman.success";
    static final String HUNTSMAN_READY = "commands.dross.goto.lair.huntsman.ready";
    static final String HUNTSMAN_BEATEN = "commands.dross.goto.lair.huntsman.beaten";
    static final String HUNTSMAN_PEACEFUL = "commands.dross.goto.lair.huntsman.peaceful";
    static final String LIST_HEADER = "commands.dross.locate.villages.header";
    static final String LIST_LINE = "commands.dross.locate.villages.line";
    static final String LIST_NONE = "commands.dross.locate.villages.none";
    static final String LIST_GO = "commands.dross.locate.villages.go";

    // ---- Tuning ----
    /** How far it looks for a village (blocks). */
    public static final int SEARCH_RADIUS = 12_000;
    /** How many villages {@code locate villages} lists. */
    public static final int LIST_COUNT = 5;
    /** If the way in isn't safe, the nearest safe ground is looked for this far round it. */
    public static final int FALLBACK_RADIUS = 8;

    private GotoVillage() {}

    /** {@code /dross goto lair huntsman}: the nearest Huntsman's village. */
    static int huntsman(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel dross = source.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            source.sendFailure(Component.translatable(NO_DROSS));
            return 0;
        }
        BlockPos from = from(player, dross);
        Optional<Site> found = BossSites.forLevel(dross).nearest(BossSites.HUNTSMAN_VILLAGE, from, SEARCH_RADIUS)
                .filter(s -> s.detail() instanceof RimefallVillages.VillagePlan);
        if (found.isEmpty())
        {
            source.sendFailure(Component.translatable(HUNTSMAN_NOT_FOUND));
            return 0;
        }
        RimefallVillages.VillagePlan plan = (RimefallVillages.VillagePlan) found.get().detail();
        Optional<BlockPos> feet = teleport(source, player, dross, plan);
        if (feet.isEmpty())
        {
            return 0;
        }
        Component note = Component.translatable(HuntsmanVillages.isBeaten(dross, plan) ? HUNTSMAN_BEATEN
                : dross.getDifficulty() == Difficulty.PEACEFUL ? HUNTSMAN_PEACEFUL : HUNTSMAN_READY);
        BlockPos at = feet.get();
        source.sendSuccess(() -> Component.translatable(HUNTSMAN_SUCCESS, at.getX(), at.getY(), at.getZ(), note), true);
        return 1;
    }

    /** {@code /dross goto village}: the nearest hunter village of either kind (from where you are, or the hub). */
    static int nearest(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel dross = source.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            source.sendFailure(Component.translatable(NO_DROSS));
            return 0;
        }
        return nearestTo(source, player, dross, from(player, dross));
    }

    /** {@code /dross goto village <x> <z>}: the village nearest that spot (the list's [go] buttons). */
    static int nearestTo(CommandSourceStack source, int x, int z) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel dross = source.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            source.sendFailure(Component.translatable(NO_DROSS));
            return 0;
        }
        return nearestTo(source, player, dross, new BlockPos(x, 0, z));
    }

    private static int nearestTo(CommandSourceStack source, ServerPlayer player, ServerLevel dross, BlockPos from)
    {
        List<Site> found = RimefallVillages.nearest(BossSites.forLevel(dross), from, SEARCH_RADIUS, 1);
        if (found.isEmpty())
        {
            source.sendFailure(Component.translatable(NOT_FOUND));
            return 0;
        }
        RimefallVillages.VillagePlan plan = (RimefallVillages.VillagePlan) found.get(0).detail();
        Optional<BlockPos> feet = teleport(source, player, dross, plan);
        if (feet.isEmpty())
        {
            return 0;
        }
        BlockPos at = feet.get();
        Component kind = Component.translatable(plan.huntsman() ? KIND_HUNTSMAN : KIND_REGULAR);
        source.sendSuccess(() -> Component.translatable(SUCCESS, kind, at.getX(), at.getY(), at.getZ(),
                plan.design().igloos()), true);
        return 1;
    }

    /** {@code /dross locate villages}: the nearest {@value #LIST_COUNT} villages in chat, each with a [go] button. */
    static int locate(CommandSourceStack source) throws CommandSyntaxException
    {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel dross = source.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            source.sendFailure(Component.translatable(NO_DROSS));
            return 0;
        }
        BlockPos from = from(player, dross);
        long started = Util.getMillis();
        List<Site> found = RimefallVillages.nearest(BossSites.forLevel(dross), from, SEARCH_RADIUS, LIST_COUNT);
        LOGGER.info("[locate villages] {}: {} village(s) from {} ({} ms)", player.getName().getString(), found.size(),
                from.toShortString(), Util.getMillis() - started);
        if (found.isEmpty())
        {
            source.sendFailure(Component.translatable(LIST_NONE));
            return 0;
        }
        boolean fromHere = player.level() == dross;
        source.sendSuccess(() -> Component.translatable(LIST_HEADER, found.size(),
                fromHere ? Component.literal("you") : Component.literal("the hub")), false);
        int n = 0;
        for (Site site : found)
        {
            n++;
            RimefallVillages.VillagePlan plan = (RimefallVillages.VillagePlan) site.detail();
            BlockPos way = plan.wayIn();
            int distance = (int) Math.round(Math.sqrt(way.distSqr(new BlockPos(from.getX(), way.getY(), from.getZ()))));
            String command = String.format(Locale.ROOT, "/dross goto village %d %d", way.getX(), way.getZ());
            MutableComponent go = Component.translatable(LIST_GO).withStyle(style -> style
                    .withColor(DrossColors.HUNTER_VILLAGE_BUTTON)
                    .withClickEvent(new ClickEvent(ClickEvent.Action.RUN_COMMAND, command))
                    .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(command))));
            MutableComponent kind = Component.translatable(plan.huntsman() ? KIND_HUNTSMAN : KIND_REGULAR);
            if (plan.huntsman())
            {
                kind = kind.withStyle(style -> style.withColor(DrossColors.HUNTSMAN_VILLAGE_LABEL));
            }
            Component line = Component.translatable(LIST_LINE, n, kind, way.getX(), way.getY(), way.getZ(), distance,
                    plan.design().igloos());
            Component full = go.append(Component.literal(" ")).append(line);
            source.sendSuccess(() -> full, false);
        }
        return found.size();
    }

    // ---------------------------------------------------------------- shared

    private static BlockPos from(ServerPlayer player, ServerLevel dross)
    {
        return player.level() == dross ? player.blockPosition() : DrossHub.CENTER;
    }

    /** Puts the player at the village's way in, facing in. Returns where their feet are, or empty (the failure said). */
    private static Optional<BlockPos> teleport(CommandSourceStack source, ServerPlayer player, ServerLevel dross,
            RimefallVillages.VillagePlan plan)
    {
        BlockPos way = plan.wayIn();
        dross.getChunkAt(way);
        Optional<Vec3> landing = DrossTeleporter.findSafeSpot(dross, player, way.getX(), way.getZ(), FALLBACK_RADIUS);
        if (landing.isEmpty())
        {
            source.sendFailure(Component.translatable(NOT_FOUND));
            return Optional.empty();
        }
        Vec3 spot = landing.get();
        BlockPos middle = plan.footprint().getCenter();
        float yaw = (float) (Mth.atan2(middle.getZ() + 0.5D - spot.z, middle.getX() + 0.5D - spot.x) * (180.0D / Math.PI)) - 90.0F;
        player.teleportTo(dross, spot.x, spot.y, spot.z, Set.of(), yaw, 10.0F);
        if (player.level() != dross)
        {
            source.sendFailure(Component.translatable(STOPPED));
            return Optional.empty();
        }
        player.setDeltaMovement(Vec3.ZERO);
        player.resetFallDistance();
        LOGGER.info("[goto village] {}: teleported to the way in of the {} village (design {}) at {}",
                player.getName().getString(), plan.huntsman() ? "Huntsman's" : "hunter", plan.designNumber(),
                BlockPos.containing(spot).toShortString());
        return Optional.of(BlockPos.containing(spot));
    }
}
