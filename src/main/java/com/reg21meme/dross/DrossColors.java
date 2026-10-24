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

    /**
     * The clickable control buttons of /dross showcase boss_designs colossus ([Dormant], [Erupt!], [Break shell]...).
     * Lava orange, not the Dross blue: the golems serve the Fire Necromancer. Used in command/GolemShowcase.
     */
    public static final int GOLEM_SHOWCASE_BUTTON = 0xFFA328;

    /**
     * The clickable control buttons of /dross showcase boss_designs ratking and /dross showcase rats ([Sleep],
     * [Rage!]...). Poison green, not the Dross blue: the Rat King serves the Plague Necromancer. Used in
     * command/RatKingShowcase and command/RatShowcase.
     */
    public static final int RAT_SHOWCASE_BUTTON = 0x8FD14F;

    /**
     * The clickable [go] buttons of /dross showcase boss_designs rimefall (one per candidate). Pale frost blue, not the
     * Dross's electric blue: the Rimefall bosses serve the Ice Necromancer. Used in command/RimefallShowcase.
     */
    public static final int RIMEFALL_SHOWCASE_BUTTON = 0xA6D8F2;

    /** In /dross locate villages (command/GotoVillage; its [go] buttons are the Boss section's HUNTER_VILLAGE_BUTTON),
     * the Huntsman's villages stand out: antler amber. */
    public static final int HUNTSMAN_VILLAGE_LABEL = 0xE0B060;

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

    /** The Rat King's sewer: the spores a Blight Vent breathes out of its grand entrance (bright poison green). Used in dimension/block/BlightVentBlock. */
    public static final int BLIGHT_VENT_SPORE = 0xA8F046;

    /** The Blight Vent's spores: the color they fade to as they drift up (dark poison green). Used in dimension/block/BlightVentBlock. */
    public static final int BLIGHT_VENT_SPORE_FADE = 0x3C6A12;

    /** Scar Vent (a building block now: its biome, the Nether Scar, was removed): the red embers it puffs out (hot red-orange). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_RED = 0xFF5A2A;

    /** The Scar Vent's red embers: the color they cool to (dark nether red). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_RED_FADE = 0x6E0F0F;

    /** Scar Vent: the electric blue embers it puffs out with the red ones (where the Dross meets the Nether). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_BLUE = PALETTE_HIGHLIGHT;

    /** The Scar Vent's blue embers: the color they fade to (deep cobalt). Used in dimension/block/ScarVentBlock. */
    public static final int SCAR_VENT_EMBER_BLUE_FADE = PALETTE_DEEP_COBALT;

    // The five terrain biomes: their night fog, sky and water. tools/terrain/worldgen.py reads these and writes them into
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

    /** The Gravecrags: night fog, cold grey-blue. */
    public static final int GRAVECRAGS_FOG = 0x222C42;
    /** The Gravecrags: sky. */
    public static final int GRAVECRAGS_SKY = 0x3A4A6E;
    /** The Gravecrags: water. */
    public static final int GRAVECRAGS_WATER = 0x26385A;
    /** The Gravecrags: under water. */
    public static final int GRAVECRAGS_WATER_FOG = 0x0A111E;

    /** The sewers under Blightwood (biome dross:sewers, the Blightwood underground): the haze in their tunnels, a murky dark green (and thick: see DrossSkyRendering). */
    public static final int SEWERS_FOG = 0x1B2310;
    /** The sewers: sky (never seen underground). */
    public static final int SEWERS_SKY = 0x2F3F17;
    /** The sewers: their water, murky brownish green (the user: "murky green, not normal blue"). */
    public static final int SEWERS_WATER = 0x56602A;
    /** The sewers: under their water, a dark murky green. */
    public static final int SEWERS_WATER_FOG = 0x1C2108;

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
    /** Terrain previews: the Gravecrags (stone grey). */
    public static final int PREVIEW_GRAVECRAGS = 0x6E6A78;
    /** Terrain previews: any other biome (none should show: a leftover biome would). */
    public static final int PREVIEW_OTHER = 0xFF00FF;
    /** Terrain previews: the hub, its rings and the marks drawn on the maps (pale electric blue). */
    public static final int PREVIEW_MARK = PALETTE_HIGHLIGHT;
    /** Terrain previews: the background and text panels (near black). */
    public static final int PREVIEW_BACKGROUND = 0x101018;
    /** Terrain previews: text. */
    public static final int PREVIEW_TEXT = 0xE8ECF4;
    /** Terrain previews: the snowy tint mixed into the relief maps above the snow line (snow white with a hint of blue). */
    public static final int PREVIEW_SNOW = 0xF4F8FF;
    /** Terrain previews: the Cinder Colossus's lair, a small triangle on this world's maps (lava orange). */
    public static final int PREVIEW_LAIR = 0xFF7A1A;

    // The boss-site planner's map (terrain-previews/boss_sites.png, drawn by the headless dimension/sites/BossSitesGameTest)

    /** Boss sites map: a boss biome's patch outline and its number (near white). */
    public static final int PREVIEW_PATCH_LINE = 0xF2F2F2;
    /** Boss sites map: the patch with its group's current boss (the Colossus's lair, the Rat King's sewer), a gold ring. */
    public static final int PREVIEW_BOSS_SITE = 0xFFC83C;
    /** Boss sites map: a regular sewer in a patch kept for a future boss (copper green). */
    public static final int PREVIEW_REGULAR_SEWER = 0x46D2A8;
    /** Boss sites map: a patch kept for a future boss whose roll came up but no sewer fits there (grey). */
    public static final int PREVIEW_NO_FIT = 0x9A9AA6;
    /** Boss sites map: each patch's group number next to its label ("g3"; pale electric blue). */
    public static final int PREVIEW_GROUP_LINE = PALETTE_HIGHLIGHT;
    /** Boss sites map: the land that isn't a boss biome (dark grey-blue). */
    public static final int PREVIEW_OTHER_LAND = 0x23262F;

    // ---------------------------------------------------------------- Boss (boss-builder)
    // The Cinder Colossus serves the Fire Necromancer: fire and obsidian colors only, never the Dross blue.
    // (Its texture colors live in the Python palettes in tools/golems/kit/materials.py.)

    /** The Cinder Colossus's spawn egg is drawn whole (its dormant shell with lava up one great crack: the user's pick,
     * C1, October 23, 2026; tools/eggs/eggs.py), so it's tinted white: it shows as drawn. Used in registry/ModItems.
     * (It was scorched stone 0x221F1E with lava orange 0xF47A15 spots.) */
    public static final int COLOSSUS_EGG_BASE = 0xFFFFFF;
    public static final int COLOSSUS_EGG_SPOTS = 0xFFFFFF;

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

    /** The Rat King's spawn egg is drawn whole (his crowned rat face: the user's pick, R1, October 23, 2026;
     * tools/eggs/eggs.py), so it's tinted white: it shows as drawn. Used in registry/ModItems. (It was grey-green
     * 0x41493C with blood-red 0x87191C spots.) */
    public static final int RAT_KING_EGG_BASE = 0xFFFFFF;
    public static final int RAT_KING_EGG_SPOTS = 0xFFFFFF;

    /** The green sparks where the Rat King's staff strikes the ground in his rage. Used in boss/ratking/RatKing. */
    public static final int RAT_KING_SLAM_SPARK = 0x79C93A;

    /** Plague Rat spawn egg base: its mangy grey-green fur. Used in registry/ModItems. */
    public static final int RAT_EGG_BASE = 0x525A45;

    /** Plague Rat spawn egg spots: its sickly green pustules. Used in registry/ModItems. */
    public static final int RAT_EGG_SPOTS = 0x8AA83A;

    /** Plague Ravager spawn egg base: its dark grey-green fur. Used in registry/ModItems. */
    public static final int ENRAGED_RAT_EGG_BASE = 0x2C3224;

    /** Plague Ravager spawn egg spots: its glowing green boils. Used in registry/ModItems. */
    public static final int ENRAGED_RAT_EGG_SPOTS = 0xA6EC5B;
    /** Spawn egg: the Rimehorn (frosted wool, clear ice horn). */
    public static final int RIMEHORN_EGG_BASE = 0xD3D8DE;
    public static final int RIMEHORN_EGG_SPOTS = 0x8FC6EC;
    /** Spawn egg: the moose (near-black brown coat, pale legs and antlers). */
    public static final int MOOSE_EGG_BASE = 0x2A231D;
    public static final int MOOSE_EGG_SPOTS = 0xB9B1A7;
    /** Spawn egg: the Pond Skater (dark frosted chitin, pale frost). */
    public static final int POND_SKATER_EGG_BASE = 0x1C2733;
    public static final int POND_SKATER_EGG_SPOTS = 0xA9D4F0;
    /** The Huntsman's spawn egg is drawn whole (his wolf hood with its fangs, black under it, his blue eyes: the
     * user's pick, H2, October 23, 2026; tools/eggs/eggs.py), so it's tinted white: it shows as drawn. */
    public static final int HUNTSMAN_EGG_BASE = 0xFFFFFF;
    public static final int HUNTSMAN_EGG_SPOTS = 0xFFFFFF;
    /** The Frostfolk's spawn eggs (boss/frostfolk/FolkRole): all their hoods' blue (the user, October 24, 2026: "blue
     * hoods"), each its calling's spots. Used in registry/ModItems. */
    public static final int FROSTFOLK_EGG_BASE = 0x22406E;
    /** Warriors: the spearman glacier blue, the axeman iron grey, the pikeman bone, the archer fletching ice, the
     * horn-blower his flag's red. */
    public static final int FROSTFOLK_SPEARMAN_EGG_SPOTS = 0x6FA8D6;
    public static final int FROSTFOLK_AXEMAN_EGG_SPOTS = 0x9AA1A8;
    public static final int FROSTFOLK_PIKEMAN_EGG_SPOTS = 0xE2D9C2;
    public static final int FROSTFOLK_ARCHER_EGG_SPOTS = 0xA9DCF2;
    public static final int FROSTFOLK_HORNBLOWER_EGG_SPOTS = 0xA8322D;
    /** Workers: the farmer frostroot pale, the butcher raw meat, the herder straw, the fisher fish silver, the tanner
     * leather. */
    public static final int FROSTFOLK_FARMER_EGG_SPOTS = 0xBFE3E8;
    public static final int FROSTFOLK_BUTCHER_EGG_SPOTS = 0xB5524A;
    public static final int FROSTFOLK_HERDER_EGG_SPOTS = 0xD9B866;
    public static final int FROSTFOLK_FISHER_EGG_SPOTS = 0x9FB7C4;
    public static final int FROSTFOLK_TANNER_EGG_SPOTS = 0x8A5A36;
    /** The elder: antler gold. */
    public static final int FROSTFOLK_ELDER_EGG_SPOTS = 0xE0B95A;
    /** Snared (his bola, his aimed shot): the effect's swirl. */
    public static final int HUNTSMAN_SNARED = 0xA8D8F0;
    /** The [Summon the Huntsman] button his arena's message has. */
    public static final int HUNTSMAN_COMMAND_BUTTON = 0x96D2F0;

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

    /** Rat King: the green sparks along the arc his staff sweeps through. Used in boss/ratking/RatKingAttackGoal. */
    public static final int RAT_KING_SWEEP_TRAIL = 0x79C93A;

    /** Rat King's poison ball: its brownish-green trail and burst dust, and the darker specks in it. Used in boss/ratking/PlagueBall. */
    public static final int PLAGUE_BALL_TRAIL = 0x6E7A34;
    public static final int PLAGUE_BALL_TRAIL_DARK = 0x4A4A22;

    /** Rat King's poison cloud: its brownish-green dust, and the darker specks in it. Used in boss/ratking/PoisonCloud. */
    public static final int POISON_CLOUD = 0x7F8A3C;
    public static final int POISON_CLOUD_DARK = 0x575A2A;

    /** Rat King's boss bar: the name over it, normal and in the Rage (as the bar previews draw it). Used in boss/client/DrossBossBars. */
    public static final int RAT_KING_BAR_NAME = 0xFFFFFF;
    public static final int RAT_KING_BAR_NAME_RAGE = 0xFFBEB0;
    /** The Huntsman's boss bars: the names over them (as tools/huntsman/bossbars.py's previews draw them): the Wild Hunt
     * (frost white), the Huntsman (pale ice), the Alpha Moose (pale amber), and either one angry (pale red). Used in
     * boss/client/DrossBossBars. */
    public static final int HUNTSMAN_BAR_NAME_WILD = 0xECF6FC;
    public static final int HUNTSMAN_BAR_NAME_HUNTER = 0xCEEAFA;
    public static final int HUNTSMAN_BAR_NAME_MOOSE = 0xFCE4B8;
    public static final int HUNTSMAN_BAR_NAME_ANGRY = 0xFFB2A2;
    /** The Huntsman's boss bar showcase: its chat button (the other set of bars). */
    public static final int HUNTSMAN_BAR_BUTTON = 0x96D2F0;
    /** The hunter village showcase (/dross showcase frostfolk_villages): its [go] buttons (pale ice) and the [overview]
     * button (snow white). Used in command/HunterVillageShowcase. */
    public static final int HUNTER_VILLAGE_BUTTON = 0x96D2F0;
    public static final int HUNTER_VILLAGE_OVERVIEW_BUTTON = 0xF2F8FC;

    /** The "[Summon the Rat King]" button in /dross boss arena ratking's chat message: poison green. Used in boss/BossCommands. */
    public static final int RAT_KING_COMMAND_BUTTON = 0x8FD14F;

    /** Rage Wand: its tooltip line saying what it does. Used in boss/rage/RageWandItem. */
    public static final ChatFormatting RAGE_WAND_TOOLTIP = ChatFormatting.GRAY;

    /** The Rage effect's colour, an angry red (its swirl is hidden; raged mobs show their team's colour). Used in boss/rage/RageEffect. */
    public static final int RAGE_EFFECT = 0xD01C1C;

    /** Rat King trophy: the item's name, poison green. Used in boss/ratking/trophy/RatKingTrophyItem. */
    public static final int RAT_KING_TROPHY_NAME = 0x8FD14F;

    /** Rat King trophy: the tooltip line naming its design. Used in boss/ratking/trophy/RatKingTrophyItem. */
    public static final ChatFormatting RAT_KING_TROPHY_TOOLTIP = ChatFormatting.GRAY;

    /** Rat King trophy: the tooltip hint "Right-click to change its pose". Used in boss/ratking/trophy/RatKingTrophyItem. */
    public static final ChatFormatting RAT_KING_TROPHY_HINT = ChatFormatting.GRAY;

    /** Rat King trophy: the green dust puffed out when it's right-clicked to a new pose. Used in boss/ratking/trophy/RatKingTrophyBlock. */
    public static final int RAT_KING_TROPHY_CYCLE_DUST = 0x79C93A;

    /** Rat King trophy: the little glint of poison green at his staff's orb now and then. Used in boss/ratking/trophy/client/RatKingTrophyParticles. */
    public static final int RAT_KING_TROPHY_GLINT = 0xA6EC5B;

    /** Rat King trophy (Ground Pound): the green dust where his staff's foot strikes. Used in boss/ratking/trophy/client/RatKingTrophyParticles. */
    public static final int RAT_KING_TROPHY_SLAM_DUST = 0x79C93A;

    /** Huntsman trophy: the item's name, glacier blue (like his boss bar). Used in boss/huntsman/trophy/HuntsmanTrophyItem. */
    public static final int HUNTSMAN_TROPHY_NAME = 0x9ED2F0;

    /** Huntsman trophy: the tooltip line naming its design. Used in boss/huntsman/trophy/HuntsmanTrophyItem. */
    public static final ChatFormatting HUNTSMAN_TROPHY_TOOLTIP = ChatFormatting.GRAY;

    /** Huntsman trophy: the tooltip hint "Right-click to change its pose". Used in boss/huntsman/trophy/HuntsmanTrophyItem. */
    public static final ChatFormatting HUNTSMAN_TROPHY_HINT = ChatFormatting.GRAY;

    /** Huntsman trophy: the frost puffed out when it's right-clicked to a new pose. Used in boss/huntsman/trophy/HuntsmanTrophyBlock. */
    public static final int HUNTSMAN_TROPHY_CYCLE_DUST = 0xDCEFFA;

    /** Huntsman trophy: the Alpha Moose's frosty breath now and then. Used in boss/huntsman/trophy/client/HuntsmanTrophyParticles. */
    public static final int HUNTSMAN_TROPHY_BREATH = 0xE8F3FA;

    /**
     * Rage Wand teams (the user's request, October 16): each team's colour, for the dust over its raged mobs, the burst
     * when one is raged or switches team, and the team's name on the action bar and in the wand's tooltip. Used in
     * boss/rage/RageTeam. (The wand's crystal in each colour is drawn by tools/ragewand/draw.py.) Blue is the Dross's
     * own electric blue.
     */
    public static final int RAGE_TEAM_BLUE = PALETTE_MAIN;
    public static final int RAGE_TEAM_RED = 0xE8261F;
    public static final int RAGE_TEAM_GREEN = 0x3CCF3A;
    public static final int RAGE_TEAM_YELLOW = 0xFFD22E;
    public static final int RAGE_TEAM_PURPLE = 0xA642EA;

    /** Rage Wand: the gray hint line "Right-click to change team" in its tooltip. Used in boss/rage/RageWandItem. */
    public static final ChatFormatting RAGE_WAND_HINT = ChatFormatting.DARK_GRAY;

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
