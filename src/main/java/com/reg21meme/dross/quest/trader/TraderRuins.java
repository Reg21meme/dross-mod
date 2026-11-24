package com.reg21meme.dross.quest.trader;

import com.reg21meme.dross.boss.knight.ruin.KnightRuins;
import com.reg21meme.dross.boss.knight.ruin.RuinSite;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * The nearest knight ruin a player hasn't cleared, for the trader's lines (the boss area's {@link KnightRuins}). Worked
 * out off the server thread, so a right-click never freezes the game: the ruins within {@link #SEARCH_RADII}[0] blocks
 * first, then further out if every one of those is cleared; who has cleared a ruin is read back on the server thread.
 */
final class TraderRuins
{
    /** How far he feels for ruins (blocks from the player), widening: the first radius with a ruin they haven't cleared wins. */
    static final int[] SEARCH_RADII = {1200, 2400, 4800};

    private TraderRuins() {}

    /**
     * The nearest ruin this player hasn't cleared, within the widest search radius (empty if none, or if the world has no
     * ruins). The future completes on the server thread (at once, from there, when the world has none).
     */
    static CompletableFuture<Optional<RuinSite>> nearestUncleared(ServerPlayer player)
    {
        MinecraftServer server = player.server;
        if (!KnightRuins.available(server))
        {
            return CompletableFuture.completedFuture(Optional.empty());
        }
        // (He's always in the Overworld, and so is whoever talks to him; a player elsewhere is measured from the spawn.)
        BlockPos from = player.level().dimension() == Level.OVERWORLD ? player.blockPosition() : server.overworld().getSharedSpawnPos();
        CompletableFuture<Optional<RuinSite>> result = new CompletableFuture<>();
        search(server, from, player.getUUID(), 0, result);
        return result;
    }

    private static void search(MinecraftServer server, BlockPos from, UUID player, int step, CompletableFuture<Optional<RuinSite>> result)
    {
        int radius = SEARCH_RADII[step];
        CompletableFuture.supplyAsync(() -> KnightRuins.within(server, from, radius)).whenComplete((list, error) -> server.execute(() -> {
            if (error != null)
            {
                result.completeExceptionally(error);
                return;
            }
            Optional<RuinSite> found = firstUncleared(server, player, list);
            if (found.isPresent() || step + 1 >= SEARCH_RADII.length)
            {
                result.complete(found);
            }
            else
            {
                search(server, from, player, step + 1, result);
            }
        }));
    }

    /** The first ruin in the list (nearest first) this player hasn't cleared. Server thread. */
    private static Optional<RuinSite> firstUncleared(MinecraftServer server, UUID player, List<RuinSite> list)
    {
        for (RuinSite site : list)
        {
            if (!KnightRuins.hasCleared(server, player, site))
            {
                return Optional.of(site);
            }
        }
        return Optional.empty();
    }
}
