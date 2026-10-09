package com.reg21meme.dross;

import net.minecraft.ChatFormatting;
import org.joml.Vector3f;

/**
 * Every color the mod uses, in one place, as labeled constants (shared file).
 *
 * Rules (see CLAUDE.md, "Rules for every agent"):
 * - Feature code reads its colors from here. Never hard-code a color.
 * - Need a new color? Add a labeled constant in your own area's section.
 * - Only change the values in your own area's section.
 *
 * RGB colors are ints written as 0xRRGGBB. Scoreboard-team glow colors must be a
 * {@link ChatFormatting} (teams can't use any other color).
 *
 * Colors baked into textures (the portal, frame and Admin Sword PNGs) are not code
 * constants and are not listed here.
 *
 * The values below are what the mod uses today (orange/gold). The planned electric blue
 * palette (main 0x2E6BFF, deep cobalt 0x0B2A9E, highlight 0x7FA8FF) arrives with the
 * orange-to-blue switch in EARLY_GAME_PLAN.md. Existing code is moved onto these
 * constants as each area is next changed.
 */
public final class DrossColors {

    private DrossColors() {
    }

    // ---------------------------------------------------------------- Portal (portal-builder)

    /** Portal swirl particles. The particle provider scales it by a random brightness. Used in portal/client/DrossPortalParticleProvider. */
    public static final int PORTAL_PARTICLE = 0xFF7314;

    /** "The Dross" arrival title. Used in portal/DrossArrival. */
    public static final ChatFormatting ARRIVAL_TITLE = ChatFormatting.GOLD;

    // ---------------------------------------------------------------- Villager (villager-builder)

    /** The trader's glow outline (any RGB works here, no team needed). Used in villager/DrossTrader. */
    public static final int TRADER_GLOW = 0xFFAA00;

    /** Trader spawn egg base color. Used in registry/ModItems. */
    public static final int TRADER_EGG_BASE = 0x10101C;

    /** Trader spawn egg spot color. Used in registry/ModItems. */
    public static final int TRADER_EGG_SPOTS = 0xDB7D1F;

    // ---------------------------------------------------------------- Enchantments (enchant-builder)

    /** Soul wisp that flies from a killed mob to the player. Used in enchant/NecromancyEvents. */
    public static final int SOUL_WISP = 0xFF8C00;

    /** Puff when risen undead rise or crumble. Used in enchant/RisenUndead. */
    public static final int RISEN_PUFF = 0xFF8000;

    /** Deathforged X glow outline (a scoreboard team, so a chat color). Used in enchant/RisenUndead. */
    public static final ChatFormatting RISEN_GLOW_TEAM = ChatFormatting.GOLD;

    /** "Souls: X / Y" tooltip text. Used in enchant/client/SoulTooltip. */
    public static final ChatFormatting SOUL_TOOLTIP = ChatFormatting.GOLD;

    // ---------------------------------------------------------------- Helpers

    /** Red part of an 0xRRGGBB color, from 0 to 1. */
    public static float red(int rgb) {
        return ((rgb >> 16) & 0xFF) / 255.0F;
    }

    /** Green part of an 0xRRGGBB color, from 0 to 1. */
    public static float green(int rgb) {
        return ((rgb >> 8) & 0xFF) / 255.0F;
    }

    /** Blue part of an 0xRRGGBB color, from 0 to 1. */
    public static float blue(int rgb) {
        return (rgb & 0xFF) / 255.0F;
    }

    /** An 0xRRGGBB color as a vector, for DustParticleOptions. */
    public static Vector3f vector(int rgb) {
        return new Vector3f(red(rgb), green(rgb), blue(rgb));
    }
}
