package com.reg21meme.dross.quest;

import com.reg21meme.dross.quest.QuestRequirement.Group;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * THE PUBLIC QUEST API. Other areas (the trader, the portal) read and change a player's quest
 * progress only through these methods. Progress is per player and per world ({@link QuestProgressData}).
 *
 * <h2>The quest, in order</h2>
 * <ol>
 *   <li>The player finds a Weathered Letter (this area notices it by itself).</li>
 *   <li>The trader says his opening line once: {@link #hasSpokenIntro} / {@link #markIntroSpoken}.</li>
 *   <li>Stage {@link Stage#TRIBUTES}: the player hands in the three tributes with {@link #handIn}.
 *       The last one returns {@link Outcome#TRIBUTES_COMPLETE}: "Proven Worthy" is granted here automatically,
 *       and the trader gives the Dross Compass.</li>
 *   <li>Stage {@link Stage#KEY_MATERIALS}: the three key materials, also with {@link #handIn}.
 *       The last one returns {@link Outcome#KEY_FORGED}: "Keymaster" is granted here automatically,
 *       and the trader gives the Rift Key.</li>
 *   <li>Stage {@link Stage#COMPLETE}: the quest is done ({@link #isQuestComplete}). The key materials can be
 *       brought again for a replacement key ({@link #handIn} returns {@link Outcome#KEY_FORGED} again).</li>
 * </ol>
 * The amounts are constants in {@link QuestRequirement}.
 */
public final class QuestProgress
{
    private QuestProgress() {}

    /** Where a player is in the quest. */
    public enum Stage
    {
        /** Bringing the three tributes. */
        TRIBUTES,
        /** All tributes are in (compass earned), bringing the three key materials. */
        KEY_MATERIALS,
        /** The Rift Key was given at least once. The quest is complete and the trader's shop is open. */
        COMPLETE
    }

    /** What happened when a player tried to hand something in. */
    public enum Outcome
    {
        /** The trader never wants this item. Nothing was taken. */
        NOT_WANTED,
        /** A key material, but the tributes aren't all in yet. Nothing was taken. */
        NOT_YET,
        /** He already has this one (for the current tributes or the current key). Nothing was taken. */
        ALREADY_HANDED_IN,
        /** The right item, but too few of them. Nothing was taken. Say "bring {@code requirement.getAmount()}". */
        NOT_ENOUGH,
        /** Taken (exactly the needed amount), and more is still needed in this stage. */
        ACCEPTED,
        /** Taken, and it was the last tribute: give the Dross Compass. "Proven Worthy" was already granted. */
        TRIBUTES_COMPLETE,
        /** Taken, and it was the last key material: give a Rift Key. "Keymaster" was already granted. */
        KEY_FORGED
    }

    /**
     * The result of {@link #handIn}.
     *
     * @param outcome     what happened
     * @param requirement the requirement the held item counts towards, or null for {@link Outcome#NOT_WANTED}
     */
    public record HandInResult(Outcome outcome, @Nullable QuestRequirement requirement)
    {
        /** True if items were taken from the player's stack. */
        public boolean tookItems()
        {
            return outcome == Outcome.ACCEPTED || outcome == Outcome.TRIBUTES_COMPLETE || outcome == Outcome.KEY_FORGED;
        }
    }

    // ------------------------------------------------------------------ Reading progress

    public static Stage getStage(ServerPlayer player)
    {
        QuestProgressData.PlayerQuest quest = quest(player);
        if (quest.keyGiven)
        {
            return Stage.COMPLETE;
        }
        return quest.compassEarned ? Stage.KEY_MATERIALS : Stage.TRIBUTES;
    }

    /**
     * What's still needed in the player's current stage, in a fixed order (use
     * {@link QuestRequirement#getAmount()} / {@link QuestRequirement#describe()} for the amounts).
     * Empty once the quest is complete; for a replacement key, see {@link #getMissingKeyMaterials}.
     */
    public static List<QuestRequirement> getStillNeeded(ServerPlayer player)
    {
        return switch (getStage(player))
        {
            case TRIBUTES -> missing(Group.TRIBUTE, quest(player).tributes);
            case KEY_MATERIALS -> missing(Group.KEY_MATERIAL, quest(player).keyMaterials);
            case COMPLETE -> List.of();
        };
    }

    /**
     * The key materials still needed for the NEXT key: the first key, or a replacement after the quest is complete.
     * Empty before all tributes are in (they can't be handed in yet).
     */
    public static List<QuestRequirement> getMissingKeyMaterials(ServerPlayer player)
    {
        QuestProgressData.PlayerQuest quest = quest(player);
        return quest.compassEarned ? missing(Group.KEY_MATERIAL, quest.keyMaterials) : List.of();
    }

    /** True if this requirement is in: a tribute ever handed in, or a key material handed in towards the next key. */
    public static boolean hasHandedIn(ServerPlayer player, QuestRequirement requirement)
    {
        QuestProgressData.PlayerQuest quest = quest(player);
        return requirement.getGroup() == Group.TRIBUTE ? quest.tributes.contains(requirement) : quest.keyMaterials.contains(requirement);
    }

    /**
     * The requirement this stack would count towards right now, or null if the trader doesn't want it now
     * (not a quest item, already handed in, or a key material before the tributes are done).
     * The amount isn't checked. Handy for deciding whether to open the trading screen.
     */
    @Nullable
    public static QuestRequirement getWantedRequirement(ServerPlayer player, ItemStack stack)
    {
        QuestRequirement requirement = QuestRequirement.forItem(stack);
        if (requirement == null)
        {
            return null;
        }
        QuestProgressData.PlayerQuest quest = quest(player);
        if (requirement.getGroup() == Group.TRIBUTE)
        {
            return quest.tributes.contains(requirement) ? null : requirement;
        }
        return quest.compassEarned && !quest.keyMaterials.contains(requirement) ? requirement : null;
    }

    /** True once all tributes are in ("Proven Worthy"), so the player earned the Dross Compass. Stays true. */
    public static boolean hasEarnedCompass(ServerPlayer player)
    {
        return quest(player).compassEarned;
    }

    /** True once the player was given a Rift Key: the quest is complete and the trader's shop is open for them. */
    public static boolean isQuestComplete(ServerPlayer player)
    {
        return quest(player).keyGiven;
    }

    /** How many Rift Keys were forged for this player (replacements included). */
    public static int getKeysForged(ServerPlayer player)
    {
        return quest(player).keysForged;
    }

    /** True once a Weathered Letter has been in the player's inventory. */
    public static boolean hasFoundLetter(ServerPlayer player)
    {
        return quest(player).letterFound;
    }

    // ------------------------------------------------------------------ Changing progress

    /** True if the trader has already said his opening line to this player. */
    public static boolean hasSpokenIntro(ServerPlayer player)
    {
        return quest(player).introSpoken;
    }

    /** Remember that the trader said his opening line to this player. */
    public static void markIntroSpoken(ServerPlayer player)
    {
        QuestProgressData.PlayerQuest quest = quest(player);
        if (!quest.introSpoken)
        {
            quest.introSpoken = true;
            data(player).setDirty();
        }
    }

    /**
     * Hands in the stack the player is holding (pass their main-hand stack).
     * <ul>
     *   <li>Full amount only: if the stack has at least the needed amount, exactly that many are taken
     *       from it ({@code stack.shrink}) and recorded. Otherwise nothing is taken.</li>
     *   <li>The last tribute grants "Proven Worthy" and returns {@link Outcome#TRIBUTES_COMPLETE}
     *       (the caller gives the compass).</li>
     *   <li>The last key material grants "Keymaster", records a forged key, and returns
     *       {@link Outcome#KEY_FORGED} (the caller gives the Rift Key). The key materials then start
     *       over, so a lost key can be replaced by bringing them again (never for free).</li>
     * </ul>
     * Creative players have items taken too, like a normal trade.
     */
    public static HandInResult handIn(ServerPlayer player, ItemStack stack)
    {
        QuestRequirement requirement = QuestRequirement.forItem(stack);
        if (requirement == null)
        {
            return new HandInResult(Outcome.NOT_WANTED, null);
        }
        QuestProgressData.PlayerQuest quest = quest(player);
        boolean tribute = requirement.getGroup() == Group.TRIBUTE;
        Set<QuestRequirement> handedIn = tribute ? quest.tributes : quest.keyMaterials;

        if (!tribute && !quest.compassEarned)
        {
            return new HandInResult(Outcome.NOT_YET, requirement);
        }
        if (handedIn.contains(requirement))
        {
            return new HandInResult(Outcome.ALREADY_HANDED_IN, requirement);
        }
        if (stack.getCount() < requirement.getAmount())
        {
            return new HandInResult(Outcome.NOT_ENOUGH, requirement);
        }

        stack.shrink(requirement.getAmount());
        handedIn.add(requirement);
        Outcome outcome = Outcome.ACCEPTED;
        if (tribute && missing(Group.TRIBUTE, handedIn).isEmpty())
        {
            quest.compassEarned = true;
            DrossAdvancements.grant(player, DrossAdvancements.PROVEN_WORTHY);
            outcome = Outcome.TRIBUTES_COMPLETE;
        }
        else if (!tribute && missing(Group.KEY_MATERIAL, handedIn).isEmpty())
        {
            forgeKey(player, quest);
            outcome = Outcome.KEY_FORGED;
        }
        data(player).setDirty();
        return new HandInResult(outcome, requirement);
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
     * Clears all of this player's quest progress (including the Guide Book flag) and takes away the
     * quest advancements (root, A Weathered Letter, Proven Worthy, Keymaster, Entered the Dross).
     * Items they already have are not taken.
     */
    public static void reset(ServerPlayer player)
    {
        data(player).remove(player.getUUID());
        for (String id : DrossAdvancements.QUEST_ADVANCEMENTS)
        {
            DrossAdvancements.revoke(player, id);
        }
    }

    /**
     * Marks the whole quest as done, as if the Rift Key had just been given (no items are given):
     * letter found, opening line spoken, all tributes in, compass earned, key given.
     * Grants the tab root, "A Weathered Letter", "Proven Worthy" and "Keymaster".
     */
    public static void completeAll(ServerPlayer player)
    {
        QuestProgressData.PlayerQuest quest = quest(player);
        quest.letterFound = true;
        quest.introSpoken = true;
        quest.tributes.addAll(QuestRequirement.inGroup(Group.TRIBUTE));
        quest.compassEarned = true;
        DrossAdvancements.grant(player, DrossAdvancements.ROOT);
        DrossAdvancements.grant(player, DrossAdvancements.WEATHERED_LETTER);
        DrossAdvancements.grant(player, DrossAdvancements.PROVEN_WORTHY);
        if (!quest.keyGiven)
        {
            forgeKey(player, quest);
        }
        DrossAdvancements.grant(player, DrossAdvancements.KEYMASTER);
        data(player).setDirty();
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

    private static void forgeKey(ServerPlayer player, QuestProgressData.PlayerQuest quest)
    {
        quest.keyGiven = true;
        quest.keysForged++;
        // Start over, so a replacement key needs all the materials again.
        quest.keyMaterials.clear();
        DrossAdvancements.grant(player, DrossAdvancements.KEYMASTER);
    }

    private static List<QuestRequirement> missing(Group group, Set<QuestRequirement> handedIn)
    {
        List<QuestRequirement> list = new ArrayList<>();
        for (QuestRequirement requirement : QuestRequirement.inGroup(group))
        {
            if (!handedIn.contains(requirement))
            {
                list.add(requirement);
            }
        }
        return list;
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
