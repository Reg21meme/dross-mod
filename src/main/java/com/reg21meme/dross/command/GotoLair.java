package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.lair.ColossusLair;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.portal.DrossTeleporter;
import java.util.Optional;
import java.util.Set;
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
import org.slf4j.Logger;

/**
 * {@code /dross goto lair colossus}: teleports a player to the entrance of the Cinder Colossus's lair in the Dross (the
 * boss area's {@link ColossusLair}: a domed cavern in the Cinder Wastes, entered by a tunnel). World site wires it into
 * {@link DrossCommand}. A future boss's lair adds one literal under {@code goto lair}.
 * <ul>
 *   <li><b>Where:</b> the lair's {@link ColossusLair.Lair#entrance() entrance} (on the surface just outside the tunnel's
 *       mouth), facing into the tunnel ({@link ColossusLair.Lair#entranceYaw() entranceYaw}), from any dimension. If
 *       someone has changed the blocks there so it's no longer safe to stand, the nearest safe spot within
 *       {@value #ENTRANCE_FALLBACK_RADIUS} blocks is used instead (still facing the tunnel).</li>
 *   <li><b>Before the lair is built</b> (it's built in the background a little while after a world first starts with
 *       the Dross), it builds nothing and fails: "The Cinder Colossus's lair hasn't been placed in this world yet." (like
 *       {@code /dross goto cathedral}).</li>
 *   <li><b>The chat line</b> gives the entrance's coordinates and whether the Colossus will rise when you walk in, or
 *       was beaten recently and can rise again in N minutes (its 10-minute wait, {@link ColossusLair#ticksUntilRespawn}),
 *       or never rises because the world is on Peaceful.</li>
 * </ul>
 * Like {@code /dross goto hub}, it's a plain teleport: no arrival title or notes (those are only for the portal), and
 * walking in is what wakes the Colossus, not arriving at the entrance.
 */
public final class GotoLair
{
    private static final Logger LOGGER = LogUtils.getLogger();

    // ---- Chat messages: lang keys in en_us.json ----
    /** "The Cinder Colossus's lair hasn't been placed in this world yet." */
    static final String NOT_PLACED = "commands.dross.goto.lair.not_placed";
    /** "The Dross dimension isn't loaded, so there's no lair to go to." */
    static final String NO_DROSS = "commands.dross.goto.lair.no_dross";
    /** "Something stopped you from going to the Dross." (another mod cancelled the change of dimension) */
    static final String STOPPED = "commands.dross.goto.lair.stopped";
    /** "Teleported to the entrance of the Cinder Colossus's lair at X, Y, Z. " followed by one of the three below. */
    static final String SUCCESS = "commands.dross.goto.lair.success";
    /** "It rises when you walk in." */
    static final String READY = "commands.dross.goto.lair.ready";
    /** "It was beaten recently and can rise again in N minutes." */
    static final String WAITING = "commands.dross.goto.lair.waiting";
    /** "It was beaten recently and can rise again in 1 minute." */
    static final String WAITING_ONE = "commands.dross.goto.lair.waiting_one";
    /** "It never rises on Peaceful." */
    static final String PEACEFUL = "commands.dross.goto.lair.peaceful";

    // ---- Tuning ----
    /** If the entrance is no longer safe to stand on (its blocks were changed), a safe spot is looked for this far round it. */
    public static final int ENTRANCE_FALLBACK_RADIUS = 8;
    /** Ticks in a minute (for "can rise again in N minutes"). */
    private static final int TICKS_PER_MINUTE = 20 * 60;

    private GotoLair() {}

    /**
     * Runs {@code /dross goto lair colossus}.
     *
     * @return 1 if the player was teleported; 0 if not (the reason is said in chat)
     * @throws CommandSyntaxException if the lair is built but it wasn't used by a player (the server console, a command block)
     */
    static int colossus(CommandSourceStack source) throws CommandSyntaxException
    {
        MinecraftServer server = source.getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            source.sendFailure(Component.translatable(NO_DROSS));
            return 0;
        }
        return teleport(source, dross, ColossusLair.get(server), ColossusLair.ticksUntilRespawn(server));
    }

    /**
     * The command's work, given the lair (empty: not built yet) and how many ticks are left before its Colossus can rise
     * again. Separate so the headless test can check the "not built yet" answer in a world whose lair is already built.
     */
    static int teleport(CommandSourceStack source, ServerLevel dross, Optional<ColossusLair.Lair> lair, long ticksUntilRespawn)
            throws CommandSyntaxException
    {
        if (lair.isEmpty())
        {
            // Never build it from here: it's built in the background a little while after the world starts.
            source.sendFailure(Component.translatable(NOT_PLACED));
            return 0;
        }
        ServerPlayer player = source.getPlayerOrException();
        ColossusLair.Lair built = lair.get();
        Vec3 spot = built.entrance();
        float yaw = built.entranceYaw();
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
        Component rise = riseNote(dross, ticksUntilRespawn);
        LOGGER.info("[goto lair] {}: teleported to the Colossus lair's entrance at {}, facing the tunnel ({}); the Colossus {}",
                player.getName().getString(), feet.toShortString(), built.tunnelDirection().getOpposite().getName(),
                ticksUntilRespawn > 0L ? "can rise again in " + ticksUntilRespawn + " ticks" : "can rise now");
        source.sendSuccess(() -> Component.translatable(SUCCESS, feet.getX(), feet.getY(), feet.getZ(), rise), true);
        return 1;
    }

    /** Whether the Colossus rises when you walk in, can rise again in N minutes, or never (Peaceful). */
    static Component riseNote(ServerLevel dross, long ticksUntilRespawn)
    {
        if (dross.getDifficulty() == Difficulty.PEACEFUL)
        {
            return Component.translatable(PEACEFUL);
        }
        if (ticksUntilRespawn <= 0L)
        {
            return Component.translatable(READY);
        }
        long minutes = Math.max(1L, (ticksUntilRespawn + TICKS_PER_MINUTE - 1) / TICKS_PER_MINUTE); // rounded up
        return minutes == 1L ? Component.translatable(WAITING_ONE) : Component.translatable(WAITING, minutes);
    }

    /** Room for the player standing up, no liquid, and something solid right under the feet. Loads the chunk. */
    private static boolean canStand(ServerLevel level, ServerPlayer player, Vec3 pos)
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
