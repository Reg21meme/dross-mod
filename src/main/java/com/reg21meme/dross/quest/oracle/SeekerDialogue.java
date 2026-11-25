package com.reg21meme.dross.quest.oracle;

import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The Ragged Seeker's quest talk (the user, November 9, 2026: "the Seeker should say something funny if you click him
 * while on a major boss line ... only for major bosses; the rest of the quest give him basic lines"). The villager area
 * asks {@link #talk} on every right-click of him; an empty answer means his basic lines (his old lore lines,
 * {@code dross.survivor.student.line.<n>}). He never changes the quest.
 * <ul>
 *   <li>A BOSS quest going (ACTIVE: the Colossus, the Rat King, the Huntsman, the Emberdactyl): his jokes about that
 *       boss, {@value #BOSS_LINES} of them in turn ({@code dross.seeker.<quest>.<n>}, the quest's advancement name).</li>
 *   <li>A BOSS quest done (DONE: the boss beaten, not yet told to the Oracle): his one line for it
 *       ({@code dross.seeker.<quest>.done}).</li>
 *   <li>Every quest done (FINISHED): his {@value #AFTER_LINES} lines for after, in turn ({@code dross.seeker.after.<n>}).</li>
 *   <li>Anything else: empty.</li>
 * </ul>
 * Which joke he's at is kept per player and quest while the server runs (not saved: they just start from the first).
 */
public final class SeekerDialogue
{
    /** How many jokes he has for each boss (each cycled in turn). */
    public static final int BOSS_LINES = 3;
    /** How many lines he has once every quest is done ({@code dross.seeker.after.<n>}, in turn). */
    public static final int AFTER_LINES = 4;

    /** The joke each player hears next, and for which quest. */
    private static final Map<UUID, Next> NEXT = new HashMap<>();

    private record Next(String quest, int index) {}

    private SeekerDialogue() {}

    /** His next quest-related line for this player, or empty for his basic lines. Server side only. */
    public static Optional<Component> talk(ServerPlayer player)
    {
        OracleQuest quest = OracleProgress.current(player);
        OracleState state = OracleProgress.state(player);
        if (state == OracleState.FINISHED)
        {
            // Every quest done (the user, November 25, 2026: "I might actually touch grass ... can't wait to see the sun").
            Next next = NEXT.get(player.getUUID());
            int index = next != null && next.quest().equals("after") ? next.index() : 0;
            NEXT.put(player.getUUID(), new Next("after", (index + 1) % AFTER_LINES));
            return Optional.of(Component.translatable(key("after", String.valueOf(index + 1))));
        }
        if (quest.kind() != OracleQuest.Kind.BOSS)
        {
            return Optional.empty();
        }
        String id = quest.advancement();
        if (state == OracleState.DONE)
        {
            return Optional.of(Component.translatable(key(id, "done")));
        }
        if (state != OracleState.ACTIVE)
        {
            return Optional.empty();
        }
        Next next = NEXT.get(player.getUUID());
        int index = next != null && next.quest().equals(id) ? next.index() : 0;
        NEXT.put(player.getUUID(), new Next(id, (index + 1) % BOSS_LINES));
        return Optional.of(Component.translatable(key(id, String.valueOf(index + 1))));
    }

    /** The lang key of one of his lines about this quest's boss. */
    public static String key(String questId, String line)
    {
        return "dross.seeker." + questId + "." + line;
    }
}
