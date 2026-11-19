package com.reg21meme.dross.quest.oracle;

import com.reg21meme.dross.villager.Survivor;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * The Blind Oracle's quest talk. The villager area calls {@link #talk} on every right-click of him and prints what comes
 * back as {@code <Blind Oracle> ...}.
 *
 * <ul>
 *   <li>AVAILABLE: each click is the next offer line ({@code dross.oracle.<id>.offer.<n>}); after the last one the quest
 *       is accepted ({@link OracleProgress#accept}).</li>
 *   <li>ACTIVE: each click is one reminder ({@code .remind}; a kill quest's gets the count and the total).</li>
 *   <li>DONE: each click is the next thanks line ({@code .done.<n>}); after the last one the next quest is AVAILABLE,
 *       and a hand-in that opens a land or a boss's home ends with his spell ({@link OracleSpell}).</li>
 *   <li>FINISHED (every quest done, "Defeat the Dross"): his lines for an old friend, {@value #AFTER_LINES} of them in
 *       turn ({@code dross.oracle.after.<n>}; the user, November 9, 2026: "when you defeat the Dross he should have some
 *       new lines after, not cycle through the same 5 ones, cause he knows you by then"). His first lore lines, for a
 *       stranger, are never said to them again.</li>
 * </ul>
 * The line he's at is saved, so walking off mid-talk carries on where it stopped.
 */
public final class OracleDialogue
{
    /** How many lines he has for a player who has done every quest (said in turn). */
    public static final int AFTER_LINES = 7;

    private OracleDialogue() {}

    /**
     * His next line for this player, and whatever that click does. Server side only.
     *
     * @return the line (without the speaker's name), or empty when he has no quest talk left for them
     */
    public static Optional<Component> talk(ServerPlayer player)
    {
        return talk(player, null);
    }

    /**
     * His next line for this player, said by this Oracle (who casts the spell when a hand-in ends with one: null casts
     * none, as from a test). Server side only.
     */
    public static Optional<Component> talk(ServerPlayer player, @Nullable Survivor oracle)
    {
        OracleRecord record = OracleProgress.record(player);
        OracleQuest quest = record.quest();
        switch (record.state())
        {
            case FINISHED ->
            {
                int line = Math.floorMod(record.line(), AFTER_LINES);
                record.line = (line + 1) % AFTER_LINES;
                OracleProgress.saved(player);
                return Optional.of(Component.translatable("dross.oracle.after." + (line + 1)));
            }
            case AVAILABLE ->
            {
                int line = Math.min(record.line(), quest.offerLines() - 1);
                Component text = Component.translatable(quest.key("offer." + (line + 1)));
                if (line + 1 >= quest.offerLines())
                {
                    OracleProgress.accept(player);
                }
                else
                {
                    record.line = line + 1;
                    OracleProgress.saved(player);
                }
                return Optional.of(text);
            }
            case ACTIVE ->
            {
                return Optional.of(Component.translatable(quest.key("remind"), record.kills(), quest.count()));
            }
            case DONE ->
            {
                if (quest.doneLines() <= 0)
                {
                    // (Never in the list: a done quest always has thanks. Just move on.)
                    OracleProgress.handIn(player, oracle);
                    return talk(player, oracle);
                }
                int line = Math.min(record.line(), quest.doneLines() - 1);
                Component text = Component.translatable(quest.key("done." + (line + 1)));
                if (line + 1 >= quest.doneLines())
                {
                    OracleProgress.handIn(player, oracle);
                }
                else
                {
                    record.line = line + 1;
                    OracleProgress.saved(player);
                }
                return Optional.of(text);
            }
        }
        return Optional.empty();
    }
}
