package com.reg21meme.dross.villager;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.reg21meme.dross.Dross;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/**
 * The trader's test commands. {@code DrossCommand} (the /dross root) hooks them up:
 * <ul>
 *   <li>{@code /dross trader}: teleports you to the hut trader, even if his area isn't loaded
 *       (then you go to where he was last seen, which is where he still is).</li>
 *   <li>{@code /dross trader home}: sends him home. If his area isn't loaded, it's loaded first.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class TraderCommands
{
    private static final TicketType<ChunkPos> LOAD_TICKET =
            TicketType.create("dross_trader_load", Comparator.comparingLong(ChunkPos::toLong));
    /** Chunks loaded around his last known position and around his hut. */
    private static final int TICKET_RADIUS = 2;
    /** Give up looking for him after 10 seconds. */
    private static final int LOAD_TIMEOUT_TICKS = 10 * 20;

    /** "/dross trader home" requests waiting for his area to load. */
    private static final List<PendingHome> PENDING = new ArrayList<>();

    private record PendingHome(UUID trader, ChunkPos from, ChunkPos home, long deadline, CommandSourceStack source) {}

    private TraderCommands() {}

    public static int teleportToTrader(CommandContext<CommandSourceStack> context) throws CommandSyntaxException
    {
        CommandSourceStack source = context.getSource();
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel overworld = source.getServer().overworld();
        TraderSpawnData data = TraderSpawnData.get(overworld);
        if (!data.hasSpawned())
        {
            source.sendFailure(Component.literal("The Dross trader hasn't spawned in this world."));
            return 0;
        }
        Entity trader = data.getTraderId() == null ? null : overworld.getEntity(data.getTraderId());
        Vec3 target = trader != null ? trader.position() : Vec3.atBottomCenterOf(data.getLastPos());
        player.teleportTo(overworld, target.x, target.y, target.z, player.getYRot(), player.getXRot());
        BlockPos at = BlockPos.containing(target);
        source.sendSuccess(() -> Component.literal("Teleported to the Dross trader at "
                + at.getX() + ", " + at.getY() + ", " + at.getZ()), true);
        return 1;
    }

    public static int sendTraderHome(CommandContext<CommandSourceStack> context)
    {
        CommandSourceStack source = context.getSource();
        ServerLevel overworld = source.getServer().overworld();
        TraderSpawnData data = TraderSpawnData.get(overworld);
        if (!data.hasSpawned() || data.getTraderId() == null)
        {
            source.sendFailure(Component.literal("The Dross trader hasn't spawned in this world."));
            return 0;
        }
        if (overworld.getEntity(data.getTraderId()) instanceof DrossTrader trader)
        {
            trader.teleportHome(overworld);
            source.sendSuccess(() -> Component.literal("Sent the Dross trader home."), true);
            return 1;
        }

        // He isn't loaded: load the chunks around where he was last seen and around his hut,
        // then send him home as soon as he appears (see onServerTick).
        ChunkPos from = new ChunkPos(data.getLastPos());
        ChunkPos home = new ChunkPos(data.getPos());
        overworld.getChunkSource().addRegionTicket(LOAD_TICKET, from, TICKET_RADIUS, from);
        overworld.getChunkSource().addRegionTicket(LOAD_TICKET, home, TICKET_RADIUS, home);
        PENDING.add(new PendingHome(data.getTraderId(), from, home, source.getServer().getTickCount() + LOAD_TIMEOUT_TICKS, source));
        source.sendSuccess(() -> Component.literal("The trader's area isn't loaded. Loading it, then sending him home..."), true);
        return 1;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event)
    {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty() || server == null)
        {
            return;
        }
        ServerLevel overworld = server.overworld();
        Iterator<PendingHome> it = PENDING.iterator();
        while (it.hasNext())
        {
            PendingHome p = it.next();
            if (overworld.getEntity(p.trader()) instanceof DrossTrader trader)
            {
                trader.teleportHome(overworld);
                p.source().sendSuccess(() -> Component.literal("Sent the Dross trader home."), true);
            }
            else if (server.getTickCount() > p.deadline())
            {
                p.source().sendFailure(Component.literal("Couldn't find the Dross trader near where he was last seen."));
            }
            else
            {
                continue;
            }
            // Done: let the chunks unload normally again.
            overworld.getChunkSource().removeRegionTicket(LOAD_TICKET, p.from(), TICKET_RADIUS, p.from());
            overworld.getChunkSource().removeRegionTicket(LOAD_TICKET, p.home(), TICKET_RADIUS, p.home());
            it.remove();
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event)
    {
        PENDING.clear();
    }
}
