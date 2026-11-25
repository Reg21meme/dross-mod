package com.reg21meme.dross.quest.trader;

import com.reg21meme.dross.boss.knight.ruin.RuinSite;
import com.reg21meme.dross.quest.DrossAdvancements;
import net.minecraft.Util;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * The Dross Trader's quest talk, a line or two a right-click (PRE_DROSS_PLAN.md, "His quests" and "His lines"). The
 * villager area calls {@link #talk} on every right-click of him, with a {@link Speaker} that prints his lines as
 * {@code <Dross Trader> ...} and plays his effects. Every word is a lang key under {@code dross.trader.}; the line he's at
 * is saved, so walking off mid-talk carries on where it stopped.
 *
 * <ul>
 *   <li>{@link TraderState#OFFER} (red ?): each click is the next offer line ({@code offer.1} ... {@code offer.6}, the last
 *       asking if you'll help); the click after it is your answer ({@code answer}, "Yes, of course.", in chat as you), his
 *       thanks ({@code accepted.1}) and the golden apples: the quest is going.</li>
 *   <li>{@link TraderState#RUINS} (yellow ...): the first click after you agreed is {@code accepted.2}, said with the nearest
 *       ruin you haven't cleared once it's worked out (or {@code accepted.2.no_ruin}); then each click is
 *       the reminder with your count and that ruin ({@code remind}; {@code remind_new} for a new orb).</li>
 *   <li>{@link TraderState#READY} (green !): his hand-in, a line a click ({@code done.1} ... {@code done.4}): he makes the
 *       Portal Orb on {@code done.2} and names the cathedral's spot in the same line. For a new orb, one line
 *       ({@code new_orb}) and the orb.</li>
 *   <li>{@link TraderState#ORB_GIVEN} (no marker): been to the Dross ("Enter the Dross"), his lines for an old friend in
 *       turn ({@code after.<n>}); else, with no orb on you and the cathedral's portal not lit yet, {@code orb_lost} and a
 *       new round of ruins; else the reminder of the cathedral's spot ({@code orb_given}). His shop may open after.</li>
 * </ul>
 * A line naming a ruin waits for the search ({@link TraderRuins}, off the server thread); clicking again meanwhile starts
 * no second search.
 */
public final class TraderDialogue
{
    /** How long a search for a player's nearest ruin is waited for before another click may start a new one (ms). */
    private static final long SEARCH_STALE_MILLIS = 60_000L;

    /** The players whose nearest-ruin search is going, and when it started. */
    private static final Map<UUID, Long> SEARCHING = new ConcurrentHashMap<>();

    /** How the trader says things (the villager area's {@code DrossTrader}). */
    public interface Speaker
    {
        /** He says this line to the player (in chat, {@code <Dross Trader> line}). Server thread; may come a moment later. */
        void say(Component line);

        /** He makes a Portal Orb in his hands: the puff and the sound (the quest area gives the orb itself). */
        void conjure();
    }

    /** What a click did. */
    public enum Result
    {
        /** He talked (or is working out a ruin's spot). */
        TALKED,
        /** He talked, and he's made this player an orb before: his shop may open after (if he has something for them). */
        SHOP
    }

    private TraderDialogue() {}

    /** One right-click of the trader by this player: his next line or two, and whatever that click does. Server side only. */
    public static Result talk(ServerPlayer player, Speaker speaker)
    {
        TraderRecord record = TraderProgress.record(player);
        return switch (record.state())
        {
            case OFFER -> offer(player, record, speaker);
            case RUINS -> ruins(player, record, speaker);
            case READY -> handIn(player, record, speaker);
            case ORB_GIVEN -> orbGiven(player, record, speaker);
        };
    }

    /** Forgets the searches under way (the server is stopping). */
    public static void forgetSearches()
    {
        SEARCHING.clear();
    }

    // ------------------------------------------------------------------ Each state

    private static Result offer(ServerPlayer player, TraderRecord record, Speaker speaker)
    {
        if (record.line() < TraderProgress.OFFER_LINES)
        {
            speaker.say(Component.translatable("dross.trader.offer." + (record.line() + 1)));
            record.line++;
            TraderProgress.saved(player);
            return Result.TALKED;
        }
        // Your answer (your own line, as you in chat), his thanks and the golden apples: the quest is going.
        player.sendSystemMessage(Component.translatable("chat.type.text", player.getDisplayName(),
                Component.translatable("dross.trader.answer")));
        speaker.say(Component.translatable("dross.trader.accepted.1"));
        TraderProgress.accept(player);
        return Result.TALKED;
    }

    private static Result ruins(ServerPlayer player, TraderRecord record, Speaker speaker)
    {
        if (record.isReplacementRound() && (TraderProgress.hasOrb(player) || TraderProgress.isCathedralLit(player.server)
                || DrossAdvancements.has(player, DrossAdvancements.ENTERED_THE_DROSS)))
        {
            // A new orb isn't needed any more: they found theirs, the portal's lit, or they've been through.
            TraderProgress.cancelRound(player);
            return orbGiven(player, TraderProgress.record(player), speaker);
        }
        if (record.line() == 0)
        {
            // The rest of his thanks, what to do and the nearest ruin, in one line (said once the ruin is worked out).
            record.line = 1;
            TraderProgress.saved(player);
            sayNearestRuin(player, speaker, found -> ruinLine("dross.trader.accepted.2", "dross.trader.accepted.2.no_ruin", found));
            return Result.TALKED;
        }
        String key = record.isReplacementRound() ? "dross.trader.remind_new" : "dross.trader.remind";
        sayNearestRuin(player, speaker, found -> {
            TraderRecord now = TraderProgress.record(player);
            return ruinLine(key, key + ".no_ruin", found, now.ruins(), TraderProgress.ruinsNeeded(now));
        });
        return Result.TALKED;
    }

    private static Result handIn(ServerPlayer player, TraderRecord record, Speaker speaker)
    {
        if (record.isReplacementRound())
        {
            speaker.say(Component.translatable("dross.trader.new_orb"));
            TraderProgress.makeOrb(player);
            speaker.conjure();
            TraderProgress.handedIn(player);
            return Result.TALKED;
        }
        int line = Math.min(record.line(), TraderProgress.DONE_LINES - 1) + 1;
        speaker.say(line == TraderProgress.CATHEDRAL_LINE
                ? TraderProgress.cathedralLine(player.server, "dross.trader.done." + line)
                : Component.translatable("dross.trader.done." + line));
        if (line == TraderProgress.ORB_LINE)
        {
            TraderProgress.makeOrb(player);
            speaker.conjure();
        }
        if (line >= TraderProgress.DONE_LINES)
        {
            TraderProgress.handedIn(player);
        }
        else
        {
            record.line = line;
            TraderProgress.saved(player);
        }
        return Result.TALKED;
    }

    private static Result orbGiven(ServerPlayer player, TraderRecord record, Speaker speaker)
    {
        if (DrossAdvancements.has(player, DrossAdvancements.ENTERED_THE_DROSS))
        {
            int line = Math.floorMod(record.line(), TraderProgress.AFTER_LINES);
            record.line = (line + 1) % TraderProgress.AFTER_LINES;
            TraderProgress.saved(player);
            speaker.say(Component.translatable("dross.trader.after." + (line + 1)));
            return Result.SHOP;
        }
        if (!TraderProgress.hasOrb(player) && !TraderProgress.isCathedralLit(player.server))
        {
            speaker.say(Component.translatable("dross.trader.orb_lost"));
            TraderProgress.orbLost(player);
            return Result.TALKED;
        }
        speaker.say(TraderProgress.cathedralLine(player.server, "dross.trader.orb_given"));
        return Result.SHOP;
    }

    // ------------------------------------------------------------------ The nearest ruin

    /**
     * Says the line {@code line} makes of the nearest ruin this player hasn't cleared, once it's worked out (off the
     * server thread), if they're still on the server. A search already going for them: nothing more is started.
     */
    private static void sayNearestRuin(ServerPlayer player, Speaker speaker, Function<Optional<RuinSite>, Component> line)
    {
        UUID id = player.getUUID();
        long now = Util.getMillis();
        Long started = SEARCHING.get(id);
        if (started != null && now - started < SEARCH_STALE_MILLIS)
        {
            return;
        }
        SEARCHING.put(id, now);
        MinecraftServer server = player.server;
        TraderRuins.nearestUncleared(player).whenComplete((found, error) -> server.execute(() -> {
            SEARCHING.remove(id);
            if (server.getPlayerList().getPlayer(id) != player)
            {
                return;
            }
            speaker.say(line.apply(error != null || found == null ? Optional.empty() : found));
        }));
    }

    /** {@code key} with these arguments and then the ruin's X and Z (rounded to 10), or {@code noneKey} with the arguments. */
    private static Component ruinLine(String key, String noneKey, Optional<RuinSite> found, Object... before)
    {
        if (found.isEmpty())
        {
            return Component.translatable(noneKey, before);
        }
        Object[] args = new Object[before.length + 2];
        System.arraycopy(before, 0, args, 0, before.length);
        args[before.length] = TraderProgress.rounded(found.get().x(), TraderProgress.RUIN_ROUNDING);
        args[before.length + 1] = TraderProgress.rounded(found.get().z(), TraderProgress.RUIN_ROUNDING);
        return Component.translatable(key, args);
    }
}
