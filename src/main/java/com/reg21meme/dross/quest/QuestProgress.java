package com.reg21meme.dross.quest;

import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.quest.trader.TraderProgress;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;

/**
 * THE PUBLIC QUEST API for the parts of the quest that aren't one character's: the Weathered Letter, the Dross Guide
 * Book, and the test commands' reset and complete. Progress is per player and per world ({@link QuestProgressData}).
 *
 * <h2>The way into the Dross (PRE_DROSS_PLAN.md)</h2>
 * <ol>
 *   <li>The player finds a Weathered Letter (this area notices it by itself: "A Weathered Letter", and the Dross tab).</li>
 *   <li>The Dross Trader's quest ({@code quest.trader.TraderProgress}, {@code TraderDialogue}): his story and 3 golden
 *       apples, three knight ruins ("Knightfall"), the Portal Orb ("The Portal Orb") and where the cathedral is.</li>
 *   <li>The orb lights the cathedral's frame; the portal area grants "Enter the Dross" and gives the Guide Book
 *       ({@link #giveGuideBookOnFirstArrival}); the Blind Oracle's quests ({@code quest.oracle}) follow.</li>
 * </ol>
 */
public final class QuestProgress
{
    private QuestProgress() {}

    /** True once a Weathered Letter has been in the player's inventory. */
    public static boolean hasFoundLetter(ServerPlayer player)
    {
        return quest(player).letterFound;
    }

    // ------------------------------------------------------------------ Dross Guide Book

    /**
     * Gives the Dross Guide Book, but only the first time this is called for this player (in this world).
     * The flag survives death. If their inventory is full, the book drops at their feet.
     * The portal area calls this when a player arrives in the Dross.
     *
     * @return true if the book was given now, false if they already got it before
     */
    public static boolean giveGuideBookOnFirstArrival(ServerPlayer player)
    {
        QuestProgressData.PlayerQuest quest = quest(player);
        if (quest.guideBookGiven)
        {
            return false;
        }
        quest.guideBookGiven = true;
        data(player).setDirty();

        ItemStack book = DrossGuideBookItem.create();
        if (!player.getInventory().add(book) && !book.isEmpty())
        {
            // Inventory full: drop it right at their feet, ready to pick up.
            ItemEntity entity = new ItemEntity(player.level(), player.getX(), player.getY(), player.getZ(), book);
            entity.setDeltaMovement(0, 0, 0);
            entity.setNoPickUpDelay();
            entity.setTarget(player.getUUID());
            player.level().addFreshEntity(entity);
        }
        return true;
    }

    /** True if the player already got the Dross Guide Book on a first arrival. */
    public static boolean hasReceivedGuideBook(ServerPlayer player)
    {
        return quest(player).guideBookGiven;
    }

    // ------------------------------------------------------------------ Test commands

    /**
     * Clears all of this player's quest progress (the letter and Guide Book flags, the Dross Trader's quest and the Blind
     * Oracle's) and takes away the quest advancements (root, A Weathered Letter, Knightfall, The Portal Orb, Enter the
     * Dross, and the Oracle's own ones and "Defeat the Dross"; never the bosses'). Items they already have are not taken.
     */
    public static void reset(ServerPlayer player)
    {
        data(player).remove(player.getUUID());
        for (String id : DrossAdvancements.QUEST_ADVANCEMENTS)
        {
            DrossAdvancements.revoke(player, id);
        }
        TraderProgress.reset(player);
        OracleProgress.reset(player);
    }

    /**
     * Marks the way into the Dross as done, as if the trader had just given the Portal Orb (no items are given): letter
     * found, his quest done. Grants the tab root, "A Weathered Letter", "Knightfall" and "The Portal Orb".
     */
    public static void completeAll(ServerPlayer player)
    {
        QuestProgressData.PlayerQuest quest = quest(player);
        quest.letterFound = true;
        data(player).setDirty();
        DrossAdvancements.grant(player, DrossAdvancements.ROOT);
        DrossAdvancements.grant(player, DrossAdvancements.WEATHERED_LETTER);
        TraderProgress.complete(player);
    }

    // ------------------------------------------------------------------ Quest area only

    /** Called by the Weathered Letter while it's in the player's inventory. Grants the tab root and "A Weathered Letter". */
    static void onLetterInInventory(ServerPlayer player)
    {
        QuestProgressData.PlayerQuest quest = quest(player);
        if (!quest.letterFound)
        {
            quest.letterFound = true;
            data(player).setDirty();
        }
        // Root first, silently (no toast or chat): this is what makes the Dross tab appear.
        DrossAdvancements.grant(player, DrossAdvancements.ROOT);
        DrossAdvancements.grant(player, DrossAdvancements.WEATHERED_LETTER);
    }

    private static QuestProgressData data(ServerPlayer player)
    {
        return QuestProgressData.get(player.server.overworld());
    }

    private static QuestProgressData.PlayerQuest quest(ServerPlayer player)
    {
        return data(player).get(player.getUUID());
    }
}
