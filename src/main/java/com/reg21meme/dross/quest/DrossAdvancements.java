package com.reg21meme.dross.quest;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import net.minecraft.advancements.Advancement;
import net.minecraft.advancements.AdvancementProgress;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * The shared helper every area uses to grant a Dross advancement (the JSON files live in
 * {@code data/dross/advancements/}). All Dross advancements use the {@code minecraft:impossible}
 * trigger, so the only way to get them is from code, through {@link #grant}.
 *
 * <p>Example (from any area): {@code DrossAdvancements.grant(player, DrossAdvancements.RISE);}</p>
 *
 * <p>The Dross tab, in order: {@link #ROOT} (silent, unlocks the tab) → {@link #WEATHERED_LETTER} →
 * {@link #PROVEN_WORTHY} → {@link #KEYMASTER} → {@link #ENTERED_THE_DROSS} → {@link #RISE}.</p>
 */
public final class DrossAdvancements
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The tab's root. Granted silently (no toast, no chat) together with {@link #WEATHERED_LETTER}. */
    public static final String ROOT = "root";
    /** "A Weathered Letter": granted by the quest area when a letter is in the player's inventory. */
    public static final String WEATHERED_LETTER = "weathered_letter";
    /** "Proven Worthy": granted by {@link QuestProgress} when the last tribute is handed in. */
    public static final String PROVEN_WORTHY = "proven_worthy";
    /** "Keymaster": granted by {@link QuestProgress} when the Rift Key is forged. */
    public static final String KEYMASTER = "keymaster";
    /** "Enter the Dross" (the main achievement): the portal area grants it at the end of the arrival sequence. */
    public static final String ENTERED_THE_DROSS = "entered_the_dross";
    /** "Rise!": the enchantments area grants it the first time a player raises undead. */
    public static final String RISE = "rise";

    /** The advancements {@code /dross quest reset} takes away ("Rise!" stays: it's about Necromancy, not the quest). */
    static final List<String> QUEST_ADVANCEMENTS = List.of(ROOT, WEATHERED_LETTER, PROVEN_WORTHY, KEYMASTER, ENTERED_THE_DROSS);

    private DrossAdvancements() {}

    /**
     * Grants the advancement {@code dross:<id>} to the player (every criterion it has).
     * Does nothing if they already have it. If no such advancement exists, logs a warning and does nothing.
     *
     * @param id the advancement's file name without ".json", for example {@link #RISE} ("rise")
     */
    public static void grant(ServerPlayer player, String id)
    {
        Advancement advancement = find(player, id);
        if (advancement == null)
        {
            return;
        }
        AdvancementProgress progress = player.getAdvancements().getOrStartProgress(advancement);
        if (progress.isDone())
        {
            return;
        }
        // Copy first: awarding a criterion changes the list we'd be looping over.
        List<String> remaining = new ArrayList<>();
        progress.getRemainingCriteria().forEach(remaining::add);
        for (String criterion : remaining)
        {
            player.getAdvancements().award(advancement, criterion);
        }
    }

    /** True if the player has the advancement {@code dross:<id>}. */
    public static boolean has(ServerPlayer player, String id)
    {
        Advancement advancement = find(player, id);
        return advancement != null && player.getAdvancements().getOrStartProgress(advancement).isDone();
    }

    /** Takes the advancement {@code dross:<id>} away again (used by {@code /dross quest reset}). */
    public static void revoke(ServerPlayer player, String id)
    {
        Advancement advancement = find(player, id);
        if (advancement == null)
        {
            return;
        }
        List<String> completed = new ArrayList<>();
        player.getAdvancements().getOrStartProgress(advancement).getCompletedCriteria().forEach(completed::add);
        for (String criterion : completed)
        {
            player.getAdvancements().revoke(advancement, criterion);
        }
    }

    private static Advancement find(ServerPlayer player, String id)
    {
        ResourceLocation location = new ResourceLocation(Dross.MODID, id);
        Advancement advancement = player.server.getAdvancements().getAdvancement(location);
        if (advancement == null)
        {
            LOGGER.warn("Dross advancement {} doesn't exist (is data/dross/advancements/{}.json missing?)", location, id);
        }
        return advancement;
    }
}
