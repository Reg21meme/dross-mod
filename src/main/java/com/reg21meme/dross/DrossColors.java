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
 * The Dross's color is electric blue: see the "Palette" section below. Each area points its
 * own constants at the palette as it switches from the old orange/gold (some areas are
 * still orange until their step of the early-game build runs).
 */
public final class DrossColors {

    private DrossColors() {
    }

    // ---------------------------------------------------------------- Palette (shared)
    // The Dross's electric blue. Areas point their own constants at these.
    // Change these values only when the user asks for a different palette.

    /** Main electric blue. */
    public static final int PALETTE_MAIN = 0x2E6BFF;

    /** Deep cobalt (the dark shade). */
    public static final int PALETTE_DEEP_COBALT = 0x0B2A9E;

    /** Highlight (the light shade). */
    public static final int PALETTE_HIGHLIGHT = 0x7FA8FF;

    // ---------------------------------------------------------------- Portal (portal-builder)

    /** Portal swirl particles. The particle provider scales it by a random brightness. Used in portal/client/DrossPortalParticleProvider. */
    public static final int PORTAL_PARTICLE = PALETTE_MAIN;

    /** "The Dross" arrival title (any RGB works for titles). Used in portal/DrossArrival. */
    public static final int ARRIVAL_TITLE = PALETTE_MAIN;

    /** Drifting dust that "leaks" out of every portal frame block. Used in portal/DrossPortalFrameBlock. */
    public static final int FRAME_LEAK_PARTICLE = PALETTE_MAIN;

    /** The Rift Key's item name. Used in portal/RiftKeyItem. */
    public static final int RIFT_KEY_NAME = PALETTE_HIGHLIGHT;

    // ---------------------------------------------------------------- Villager (villager-builder)

    /** The trader's glow outline (any RGB works here, no team needed). Used in villager/DrossTrader. */
    public static final int TRADER_GLOW = PALETTE_MAIN;

    /** Trader spawn egg base color. Used in registry/ModItems. */
    public static final int TRADER_EGG_BASE = 0x10101C;

    /** Trader spawn egg spot color. Used in registry/ModItems. */
    public static final int TRADER_EGG_SPOTS = PALETTE_MAIN;

    // ---------------------------------------------------------------- Enchantments (enchant-builder)

    /** Soul wisp that flies from a killed mob to the player. Used in enchant/NecromancyEvents. */
    public static final int SOUL_WISP = PALETTE_MAIN;

    /** Puff when risen undead rise or crumble. Used in enchant/RisenUndead. */
    public static final int RISEN_PUFF = PALETTE_MAIN;

    /** Deathforged X glow outline (a scoreboard team, so a chat color: BLUE is the closest to the palette). Used in enchant/RisenUndead. */
    public static final ChatFormatting RISEN_GLOW_TEAM = ChatFormatting.BLUE;

    /** "Souls: X / Y" tooltip text. Stays gold (user decision). Used in enchant/client/SoulTooltip. */
    public static final ChatFormatting SOUL_TOOLTIP = ChatFormatting.GOLD;

    // ---------------------------------------------------------------- Quest (quest-builder)

    /** The "Right-click to read" tooltip line on the Weathered Letter and the Dross Guide Book. Used in quest/ReadableItem. */
    public static final ChatFormatting READABLE_TOOLTIP = ChatFormatting.GRAY;

    // ---------------------------------------------------------------- World site (world-builder)

    /** Clickable "teleport there" lines in a showcase command's chat output (any RGB works in chat). Used in command/ShrineShowcase. */
    public static final int SHOWCASE_LINK = PALETTE_HIGHLIGHT;

    /**
     * The clickable control buttons of /dross showcase golems ([Shell], [Break shell]...). Lava orange, not the Dross
     * blue: the golems serve the Fire Necromancer. Used in command/GolemShowcase.
     */
    public static final int GOLEM_SHOWCASE_BUTTON = 0xFFA328;

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
