package com.reg21meme.dross.villager;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reg21meme.dross.registry.ModEntities;
import java.util.Optional;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;

/**
 * The trader's test commands. {@code DrossCommand} (the /dross root) hooks them up:
 * <ul>
 *   <li>{@code /dross goto trader}: teleports you to the door of the nearest Rift Chapel ({@link RiftChapels#nearest}: to
 *       you in the Overworld, to the Overworld's place for you in the Nether, to the world spawn elsewhere), facing it. Its
 *       chunks are loaded first (the first time, made: the chapel and its trader with them), as a nether portal trip loads
 *       its destination.</li>
 *   <li>{@code /dross trader sendhome}: the nearest trader whose land is loaded teleports home.</li>
 * </ul>
 */
public final class TraderCommands
{
    /** {@code /dross goto trader} looks this far for a chapel (blocks). */
    static final int GOTO_RADIUS = 12_000;
    /** The arrival's chunks are held this many chunks round it for a moment (vanilla's portal ticket). */
    private static final int ARRIVAL_TICKET_RADIUS = 3;

    private TraderCommands() {}

    public static int teleportToTrader(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel overworld = source.getServer().overworld();
        if (!RiftChapels.available(overworld))
        {
            source.sendFailure(Component.literal("This world has no Rift Chapels: Generate Structures is off, or its kind of world makes none."));
            return 0;
        }
        BlockPos from = RiftChapels.searchOrigin(overworld, player.level(), player.blockPosition());
        Optional<RiftChapels.Chapel> found = RiftChapels.nearest(overworld, from, GOTO_RADIUS);
        if (found.isEmpty())
        {
            source.sendFailure(Component.literal("There's no Rift Chapel within " + GOTO_RADIUS + " blocks of " + from.getX() + ", " + from.getZ() + "."));
            return 0;
        }
        RiftChapels.Chapel chapel = found.get();
        BlockPos at = landing(overworld, chapel);
        player.teleportTo(overworld, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, chapel.doorYaw(), 0.0F);
        BlockPos spot = chapel.traderSpot();
        long distance = Math.round(Math.sqrt(chapel.distanceSqr(from)));
        source.sendSuccess(() -> Component.literal("Teleported to the Rift Chapel at " + spot.getX() + ", " + spot.getY() + ", " + spot.getZ()
                + " (" + distance + " blocks from " + from.getX() + ", " + from.getZ() + ")"), true);
        return 1;
    }

    /**
     * Loads the chapel's chunks (and the doorstep's), now (the first time, generated), adds vanilla's portal ticket round
     * the door, and gives the spot to land on: the doorstep if it's free (it is, on the chapel's own ground), else the top
     * of the ground there. Server thread.
     */
    static BlockPos landing(ServerLevel overworld, RiftChapels.Chapel chapel)
    {
        BlockPos door = chapel.doorstep();
        overworld.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(door), ARRIVAL_TICKET_RADIUS, door);
        BoundingBox foot = chapel.footprint();
        for (int cx = SectionPos.blockToSectionCoord(foot.minX()); cx <= SectionPos.blockToSectionCoord(foot.maxX()); cx++)
        {
            for (int cz = SectionPos.blockToSectionCoord(foot.minZ()); cz <= SectionPos.blockToSectionCoord(foot.maxZ()); cz++)
            {
                overworld.getChunk(cx, cz);
            }
        }
        if (free(overworld, door) && free(overworld, door.above()) && !free(overworld, door.below()))
        {
            return door;
        }
        return overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, door);
    }

    private static boolean free(ServerLevel level, BlockPos pos)
    {
        return level.getBlockState(pos).getCollisionShape(level, pos).isEmpty() && level.getFluidState(pos).isEmpty();
    }

    public static int sendTraderHome(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel overworld = source.getServer().overworld();
        Vec3 from = source.getLevel() == overworld ? source.getPosition() : Vec3.atCenterOf(overworld.getSharedSpawnPos());
        DrossTrader nearest = null;
        for (DrossTrader trader : overworld.getEntities(ModEntities.TRADER.get(), trader -> trader.isAlive() && trader.getHome() != null))
        {
            if (nearest == null || trader.distanceToSqr(from) < nearest.distanceToSqr(from))
            {
                nearest = trader;
            }
        }
        if (nearest == null)
        {
            source.sendFailure(Component.literal("No Dross trader's land is loaded. /dross goto trader takes you to the nearest Rift Chapel."));
            return 0;
        }
        nearest.teleportHome(overworld);
        BlockPos home = nearest.getHome();
        source.sendSuccess(() -> Component.literal("Sent the Dross trader home, to " + home.getX() + ", " + home.getY() + ", " + home.getZ() + "."), true);
        return 1;
    }
}
