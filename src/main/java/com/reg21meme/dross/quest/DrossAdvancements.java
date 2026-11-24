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
 * <p>Example (from any area): {@code DrossAdvancements.grant(player, DrossAdvancements.SET_IN_STONE);}</p>
 *
 * <p>The Dross tab, in order: {@link #ROOT} (silent, unlocks the tab) → {@link #WEATHERED_LETTER} →
 * {@link #KNIGHTFALL} → {@link #PORTAL_ORB} → {@link #ENTERED_THE_DROSS} (PRE_DROSS_PLAN.md), whose child is the
 * Blind Oracle's chain (QUEST_PLAN.md): {@link #ORACLE_PLEA} → {@link #GIANT_SLAYER} → {@link #INTO_THE_ASHES} →
 * {@link #SET_IN_STONE} → {@link #BENEATH_THE_ROT} → {@link #DOWN_THE_DRAIN} → {@link #FIRST_FROST} →
 * {@link #THE_HUNTER_HUNTED} → {@link #WINGS_CLIPPED} → {@link #DEFEAT_THE_DROSS}.
 * The Oracle's own (regular) ones and "Defeat the Dross" are granted by {@code quest.oracle}; the bosses' by the boss
 * area, as before.</p>
 */
public final class DrossAdvancements
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The tab's root. Granted silently (no toast, no chat) together with {@link #WEATHERED_LETTER}. */
    public static final String ROOT = "root";
    /** "A Weathered Letter": granted by the quest area when a letter is in the player's inventory. */
    public static final String WEATHERED_LETTER = "weathered_letter";
    /** "Knightfall": granted by {@code quest.trader.TraderProgress} when the third knight ruin is cleared for the Dross Trader. */
    public static final String KNIGHTFALL = "knightfall";
    /** "The Portal Orb": granted by {@code quest.trader.TraderProgress} when the Dross Trader makes a player their Portal Orb. */
    public static final String PORTAL_ORB = "portal_orb";
    /** "Enter the Dross" (the main achievement): the portal area grants it at the end of the arrival sequence. */
    public static final String ENTERED_THE_DROSS = "entered_the_dross";
    /** "Set in Stone": the boss area grants it when a player defeats the Cinder Colossus. It also finishes the Oracle's quest for that boss. */
    public static final String SET_IN_STONE = "set_in_stone";
    /** "Down the Drain": the boss area grants it when a player defeats the Rat King. It also finishes the Oracle's quest for that boss. */
    public static final String DOWN_THE_DRAIN = "down_the_drain";
    /** "The Hunter Hunted" (the user's pick of name): the boss area grants it when a player helps beat the Huntsman and
     * his Alpha Moose. It also finishes the Oracle's quest for that boss. */
    public static final String THE_HUNTER_HUNTED = "the_hunter_hunted";
    /** "Wings Clipped" (the user's name): the boss area grants it when a player helps beat the Emberdactyl's young
     * reborn, the end of its fight (the user: only for killing the young). It also finishes the Oracle's quest for that boss. */
    public static final String WINGS_CLIPPED = "wings_clipped";

    /** "The Oracle's Plea" (regular): the end of the Blind Oracle's story, his first quest. */
    public static final String ORACLE_PLEA = "oracle_plea";
    /** "Giant Slayer" (regular): three Silverwood Giants (the boss area's {@code dross:silverwood_giant}) killed for the Oracle. */
    public static final String GIANT_SLAYER = "giant_slayer";
    /** "Into the Ashes" (regular): three of the Cinder Wastes' mobs killed for the Oracle. */
    public static final String INTO_THE_ASHES = "into_the_ashes";
    /** "Beneath the Rot" (regular): five rats killed for the Oracle. */
    public static final String BENEATH_THE_ROT = "beneath_the_rot";
    /** "First Frost" (regular): two of Rimefall's grown beasts hunted for the Oracle. */
    public static final String FIRST_FROST = "first_frost";
    /** "Defeat the Dross" (challenge, with blue fireworks): the Oracle's last words, after the Emberdactyl. */
    public static final String DEFEAT_THE_DROSS = "defeat_the_dross";

    /** The advancements {@code /dross quest reset} takes away ("Set in Stone" and "Down the Drain" stay: they're about the bosses, not the quest). */
    static final List<String> QUEST_ADVANCEMENTS = List.of(ROOT, WEATHERED_LETTER, KNIGHTFALL, PORTAL_ORB, ENTERED_THE_DROSS);

    private DrossAdvancements() {}

    /**
     * Grants the advancement {@code dross:<id>} to the player (every criterion it has).
     * Does nothing if they already have it. If no such advancement exists, logs a warning and does nothing.
     *
     * @param id the advancement's file name without ".json", for example {@link #SET_IN_STONE} ("set_in_stone")
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
