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

    /** "Souls: X / Y" tooltip text. Stays gold (user decision). Used in enchant/client/SoulTooltip. */
    public static final ChatFormatting SOUL_TOOLTIP = ChatFormatting.GOLD;

    // ---------------------------------------------------------------- Quest (quest-builder)

    /** The "Right-click to read" tooltip line on the Weathered Letter and the Dross Guide Book. Used in quest/ReadableItem. */
    public static final ChatFormatting READABLE_TOOLTIP = ChatFormatting.GRAY;

    // ---------------------------------------------------------------- World site (world-builder)

    /** Clickable "teleport there" lines in a showcase command's chat output (any RGB works in chat). Used in command/ShrineShowcase. */
    public static final int SHOWCASE_LINK = PALETTE_HIGHLIGHT;

    /**
     * The clickable control buttons of /dross showcase boss_designs colossus ([Dormant], [Erupt!], [Break shell]...).
     * Lava orange, not the Dross blue: the golems serve the Fire Necromancer. Used in command/GolemShowcase.
     */
    public static final int GOLEM_SHOWCASE_BUTTON = 0xFFA328;

    // ---------------------------------------------------------------- Dimension (dimension-builder)
    // The Dross's own blocks and plants. (Their texture colors live in the Python palettes in tools/terrain/draw_blocks.py.)

    /** The tiny glowing mote that drifts down from silverwood leaves now and then: its color when it appears (pale blue). Used in dimension/block/SilverwoodLeavesBlock. */
    public static final int SILVERWOOD_LEAF_MOTE = PALETTE_HIGHLIGHT;

    /** The silverwood leaf mote: the color it fades to as it drifts (electric blue). Used in dimension/block/SilverwoodLeavesBlock. */
    public static final int SILVERWOOD_LEAF_MOTE_FADE = PALETTE_MAIN;

    /** Cinder Wastes: the tiny ember that drifts up off Emberweed now and then, when it appears (bright ember orange). Used in dimension/block/EmberweedBlock. */
    public static final int EMBERWEED_EMBER = 0xFF9A3C;

    /** The Emberweed ember: the color it cools to as it drifts (dark red). Used in dimension/block/EmberweedBlock. */
    public static final int EMBERWEED_EMBER_FADE = 0x7A1E0A;

    /** Cinder Wastes: the rare spark from a Cinder Vent, the Dross leaking through (pale electric blue). Used in dimension/block/CinderVentBlock. */
    public static final int CINDER_VENT_SPARK = PALETTE_HIGHLIGHT;

    /** The Cinder Vent spark: the color it fades to (electric blue). Used in dimension/block/CinderVentBlock. */
    public static final int CINDER_VENT_SPARK_FADE = PALETTE_MAIN;

    /** Blightwood: the faint spore a Gloomcap lets go now and then (pale glowing teal). Used in dimension/block/GloomcapBlock. */
    public static final int GLOOMCAP_SPORE = 0x8FF0E4;

    /** The Gloomcap spore: the color it fades to (deep teal). Used in dimension/block/GloomcapBlock. */
    public static final int GLOOMCAP_SPORE_FADE = 0x1E7F86;

    /** Blightwood: the sickly spores a Sporepod puffs out, idly or when something walks into it (sickly yellow-green). Used in dimension/block/SporepodBlock. */
    public static final int SPOREPOD_SPORE = 0xB9C77A;

    /** The Sporepod spores: the color they fade to (murky olive). Used in dimension/block/SporepodBlock. */
    public static final int SPOREPOD_SPORE_FADE = 0x56613A;

    /** Nether Scar: the red embers a Scar Vent puffs out (hot red-orange). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_RED = 0xFF5A2A;

    /** The Scar Vent's red embers: the color they cool to (dark nether red). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_RED_FADE = 0x6E0F0F;

    /** Nether Scar: the electric blue embers a Scar Vent puffs out with the red ones (where the Dross meets the Nether). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_BLUE = PALETTE_HIGHLIGHT;

    /** The Scar Vent's blue embers: the color they fade to (deep cobalt). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_BLUE_FADE = PALETTE_DEEP_COBALT;

    // ---------------------------------------------------------------- Boss (boss-builder)
    // The Cinder Colossus serves the Fire Necromancer: fire and obsidian colors only, never the Dross blue.
    // (Its texture colors live in the Python palettes in tools/golems/kit/materials.py.)

    /** Cinder Colossus spawn egg base: scorched near-black stone. Used in registry/ModItems. */
    public static final int COLOSSUS_EGG_BASE = 0x221F1E;

    /** Cinder Colossus spawn egg spots: lava orange. Used in registry/ModItems. */
    public static final int COLOSSUS_EGG_SPOTS = 0xF47A15;

    /** Boss bar (all stages): the dark outline round the bar. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_OUTLINE = 0x0B0710;

    /** Boss bar: the empty part of the bar (health already lost). Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_EMPTY = 0x1C1424;

    /** Boss bar, Shell stage: obsidian purple, light top edge. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_OBSIDIAN_LIGHT = 0x6A5492;

    /** Boss bar, Shell stage: obsidian purple, the middle. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_OBSIDIAN = 0x433158;

    /** Boss bar, Shell stage: obsidian purple, dark bottom edge. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_OBSIDIAN_DARK = 0x231A33;

    /** Boss bar, Shell stage: the glassy glints in the obsidian. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_OBSIDIAN_GLINT = 0x9C86C8;

    /** Boss bar, Cracking stage: the glowing orange cracks over the obsidian. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_CRACK = 0xFF8A1F;

    /** Boss bar, Cracking stage: the white-hot middle of the cracks. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_CRACK_HOT = 0xFFD45A;

    /** Boss bar, Cracking stage: the dim glow either side of a crack. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_CRACK_GLOW = 0xA5440F;

    /** Boss bar, Lava Core stage: lava red, bright top edge. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_LAVA_LIGHT = 0xFF6A1F;

    /** Boss bar, Lava Core stage: lava red, the middle. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_LAVA = 0xD42E0A;

    /** Boss bar, Lava Core stage: lava red, dark bottom edge. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_LAVA_DARK = 0x7A1606;

    /** Boss bar, Lava Core stage: the bright streaks flowing along the lava. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_LAVA_HOT = 0xFFB347;

    /** Boss bar names (the text above the bar), one per stage. Used in boss/client/DrossBossBars. */
    public static final int BOSS_BAR_NAME_SHELL = 0xC8B6EA;
    public static final int BOSS_BAR_NAME_CRACKING = 0xFFB066;
    public static final int BOSS_BAR_NAME_CORE = 0xFF7A3A;

    /** The "[Summon the Cinder Colossus]" button in /dross boss arena colossus's chat message. Used in boss/BossCommands. */
    public static final int BOSS_COMMAND_BUTTON = 0xFFA328;

    /** Stone Golem spawn egg base: grey stone. Used in registry/ModItems. */
    public static final int STONE_GOLEM_EGG_BASE = 0x4E4A47;

    /** Stone Golem spawn egg spots: its dim red-orange cracks. Used in registry/ModItems. */
    public static final int STONE_GOLEM_EGG_SPOTS = 0xA5440F;

    /** Lava Golem spawn egg base: dark cooling crust. Used in registry/ModItems. */
    public static final int LAVA_GOLEM_EGG_BASE = 0x3C170C;

    /** Lava Golem spawn egg spots: bright lava. Used in registry/ModItems. */
    public static final int LAVA_GOLEM_EGG_SPOTS = 0xFFA328;

    /** Cinder Colossus trophy: the item's name, lava orange. Used in boss/trophy/ColossusTrophyItem. */
    public static final int TROPHY_NAME = 0xFF8A1F;

    /** Cinder Colossus trophy: the tooltip line naming its design. Used in boss/trophy/ColossusTrophyItem. */
    public static final ChatFormatting TROPHY_TOOLTIP = ChatFormatting.GRAY;

    /** Cinder Colossus trophy: the tooltip hint "Right-click to change its pose". Used in boss/trophy/ColossusTrophyItem. */
    public static final ChatFormatting TROPHY_HINT = ChatFormatting.GRAY;

    /** Cinder Colossus trophy: the ember dust puffed out when it's right-clicked to a new pose. Used in boss/trophy/ColossusTrophyBlock. */
    public static final int TROPHY_CYCLE_DUST = 0xFF6A1F;

    /** Cinder Colossus, Shell and Cracking stages: the sparks where an arrow (any projectile) glances off its stone. Used in boss/golem/CinderColossus. */
    public static final int COLOSSUS_RANGED_SPARK = 0xFFC45A;

    /** Cinder Colossus, Lava Core stage: the embers left when an arrow (any projectile) burns up in its lava. Used in boss/golem/CinderColossus. */
    public static final int COLOSSUS_RANGED_EMBER = 0xFF6A1F;

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
