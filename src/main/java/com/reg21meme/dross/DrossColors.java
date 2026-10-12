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

    /** Blightwood: the faint spore a Gloomcap lets go now and then (pale glowing poison green). Used in dimension/block/GloomcapBlock. */
    public static final int GLOOMCAP_SPORE = 0xC2F56A;

    /** The Gloomcap spore: the color it fades to (deep poison green). Used in dimension/block/GloomcapBlock. */
    public static final int GLOOMCAP_SPORE_FADE = 0x3A7410;

    /** Blightwood: the poisonous spores a Sporepod puffs out, idly or when something walks into it (poison green). Used in dimension/block/SporepodBlock. */
    public static final int SPOREPOD_SPORE = 0x9CD43A;

    /** The Sporepod spores: the color they fade to (murky dark green). Used in dimension/block/SporepodBlock. */
    public static final int SPOREPOD_SPORE_FADE = 0x3F5A14;

    /** Nether Scar: the red embers a Scar Vent puffs out (hot red-orange). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_RED = 0xFF5A2A;

    /** The Scar Vent's red embers: the color they cool to (dark nether red). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_RED_FADE = 0x6E0F0F;

    /** Nether Scar: the electric blue embers a Scar Vent puffs out with the red ones (where the Dross meets the Nether). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_BLUE = PALETTE_HIGHLIGHT;

    /** The Scar Vent's blue embers: the color they fade to (deep cobalt). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_BLUE_FADE = PALETTE_DEEP_COBALT;

    // The six terrain biomes: their night fog, sky and water. tools/terrain/worldgen.py reads these and writes them into
    // the biome JSON (data/dross/worldgen/biome/<biome>.json); run it again after changing one. It's always night in the
    // Dross, so the fog color is what you see as the haze in the distance and at the horizon (dimension/client/
    // DrossSkyEffects shows it as it is, instead of darkening it to black like the Overworld at night). The sky color only
    // counts by day (never, here); the night sky's blue comes from the fog (see DROSS_SKY_* below). Water: the color of the
    // water itself, and the haze you see under water.

    /** Silverwood Forest: night fog, deep blue (the Dross's main forest). */
    public static final int SILVERWOOD_FOG = 0x0E1E58;
    /** Silverwood Forest: sky. */
    public static final int SILVERWOOD_SKY = 0x1C3A9E;
    /** Silverwood Forest: dark water in its ponds. */
    public static final int SILVERWOOD_WATER = 0x172B5E;
    /** Silverwood Forest: under water. */
    public static final int SILVERWOOD_WATER_FOG = 0x050C22;
    /** Silverwood Forest: the tiny motes drifting in the air (ambient particles), when they appear (pale blue). */
    public static final int SILVERWOOD_AIR_MOTE = PALETTE_HIGHLIGHT;
    /** Silverwood Forest: the air motes, the color they fade to (electric blue). */
    public static final int SILVERWOOD_AIR_MOTE_FADE = PALETTE_MAIN;

    /** Cinder Wastes: night fog, smoky blue-grey with a warm hint. */
    public static final int CINDER_FOG = 0x2E2834;
    /** Cinder Wastes: sky. */
    public static final int CINDER_SKY = 0x4A3E52;
    /** Cinder Wastes: water (rare here). */
    public static final int CINDER_WATER = 0x2B2833;
    /** Cinder Wastes: under water. */
    public static final int CINDER_WATER_FOG = 0x0C0A10;

    /** Blightwood: night fog, a dark poison green (and thick: see DrossSkyRendering). */
    public static final int BLIGHTWOOD_FOG = 0x213D0B;
    /** Blightwood: sky. */
    public static final int BLIGHTWOOD_SKY = 0x3C6414;
    /** Blightwood: murky poison-green water in its ponds. */
    public static final int BLIGHTWOOD_WATER = 0x4A5E16;
    /** Blightwood: under water. */
    public static final int BLIGHTWOOD_WATER_FOG = 0x101A05;

    /** Rimefall: night fog, pale icy blue (and hazy). */
    public static final int RIMEFALL_FOG = 0x344E70;
    /** Rimefall: sky. */
    public static final int RIMEFALL_SKY = 0x5A7EB0;
    /** Rimefall: icy water (its ponds freeze over). */
    public static final int RIMEFALL_WATER = 0x3D5E8F;
    /** Rimefall: under water. */
    public static final int RIMEFALL_WATER_FOG = 0x0F1C30;

    /** Nether Scar: night fog, red-violet with blue (where the Nether bleeds through). */
    public static final int NETHER_SCAR_FOG = 0x3A123E;
    /** Nether Scar: sky. */
    public static final int NETHER_SCAR_SKY = 0x5A2266;
    /** Nether Scar: water. */
    public static final int NETHER_SCAR_WATER = 0x3B1A3F;
    /** Nether Scar: under water. */
    public static final int NETHER_SCAR_WATER_FOG = 0x120614;

    /** The Gravecrags: night fog, cold grey-blue. */
    public static final int GRAVECRAGS_FOG = 0x222C42;
    /** The Gravecrags: sky. */
    public static final int GRAVECRAGS_SKY = 0x3A4A6E;
    /** The Gravecrags: water. */
    public static final int GRAVECRAGS_WATER = 0x26385A;
    /** The Gravecrags: under water. */
    public static final int GRAVECRAGS_WATER_FOG = 0x0A111E;

    // The Dross's night sky and light (dimension/client/DrossSkyEffects). Vanilla draws the night sky black overhead,
    // fading into the fog color at the horizon; DrossSkyEffects lays a see-through glow over it, so it fades from the fog
    // color at the horizon to a dark blue overhead instead of black.

    /** The glow over the night sky: mixed into the fog color, so it stays blue in every biome (deep cobalt). Used in dimension/client/DrossSkyEffects. */
    public static final int DROSS_SKY_GLOW = PALETTE_DEEP_COBALT;
    /** Light from the sky is tinted a little toward this (pale blue) at night. Used in dimension/client/DrossSkyEffects. */
    public static final int DROSS_SKY_LIGHT_TINT = PALETTE_HIGHLIGHT;

    // The terrain previews (terrain-previews/*.png, drawn by the headless dimension/TerrainPreviewGameTest): one map color
    // per biome, picked to tell them apart at a glance (they aren't the in-game colors).

    /** Terrain previews: Silverwood Forest (blue-green). */
    public static final int PREVIEW_SILVERWOOD = 0x3E7C8C;
    /** Terrain previews: Cinder Wastes (ash grey with a warm hint). */
    public static final int PREVIEW_CINDER = 0x8A6F5E;
    /** Terrain previews: Blightwood (poison green). */
    public static final int PREVIEW_BLIGHTWOOD = 0x8CBF2E;
    /** Terrain previews: Rimefall (icy white-blue). */
    public static final int PREVIEW_RIMEFALL = 0xC8DCF0;
    /** Terrain previews: Nether Scar (nether red). */
    public static final int PREVIEW_NETHER_SCAR = 0xB0303A;
    /** Terrain previews: the Gravecrags (stone grey). */
    public static final int PREVIEW_GRAVECRAGS = 0x6E6A78;
    /** Terrain previews: any other biome (old worlds' Dross Flats). */
    public static final int PREVIEW_OTHER = 0xFF00FF;
    /** Terrain previews: the hub, its rings and the marks drawn on the maps (pale electric blue). */
    public static final int PREVIEW_MARK = PALETTE_HIGHLIGHT;
    /** Terrain previews: the background and text panels (near black). */
    public static final int PREVIEW_BACKGROUND = 0x101018;
    /** Terrain previews: text. */
    public static final int PREVIEW_TEXT = 0xE8ECF4;
    /** Terrain previews: the snowy tint mixed into the relief maps above the snow line (snow white with a hint of blue). */
    public static final int PREVIEW_SNOW = 0xF4F8FF;

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
