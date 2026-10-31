package com.reg21meme.dross.boss.fire.cave;

/**
 * Every number of the hermit crab caves in the world (the user, October 31, 2026: "1-2 caves per Cinder Wastes area"),
 * in one place. The caves themselves are {@link HermitCaveDesigns} (made by tools/fire/hermit_caves.py).
 */
public final class HermitCaveTuning
{
    private HermitCaveTuning() {}

    // ---------------------------------------------------------------- how many, where in a patch

    /** Every Cinder Wastes area (a patch of the boss-site planner) gets one cave, and a second this share of the time. */
    public static final double SECOND_CAVE_CHANCE = 0.5D;
    /** Spots tried per patch (picked by the seed in the box round it); the flattest of those that fit win. */
    public static final int TRIES = 96;
    /** A cave's middle is at least this many blocks inside its patch (the template is 29 across: its corners stay
     * well inside the Cinder Wastes). */
    public static final int MIN_DEPTH = 28;
    /** ...or, when the normal rules leave a patch short of its caves, this many. */
    public static final int RELAXED_MIN_DEPTH = 20;
    /** A cave's middle at least this many blocks from the spot of the patch's Cinder Colossus lair or Emberdactyl
     * volcano (both are built later, near players, and reshape the land out to about 90 blocks round them). */
    public static final int BOSS_CLEARANCE = 112;
    /** ...or this many, with the looser rules. */
    public static final int RELAXED_BOSS_CLEARANCE = 100;
    /** The two caves of a patch at least this many blocks apart. */
    public static final int CAVE_SPACING = 56;
    /** The land under a cave (its middle, corners and way in, from the noise) may rise this many blocks at most from its
     * lowest to its highest point; spots over it are tried only when nothing flatter fits. */
    public static final int MAX_RELIEF = 6;
    /** How far out from a cave's middle the land is read for its relief (blocks). */
    public static final int RELIEF_REACH = 13;
    /** Salt for the caves' own random numbers (from the world seed and the patch). */
    public static final long SALT = 0x4E31_7C0A_9B25_D3F1L;

    // ---------------------------------------------------------------- the structure

    /** The cave's start is given this high (its biome is checked there): always the surface's biome. */
    public static final int SURFACE_BIOME_Y = 319;

    // ---------------------------------------------------------------- /dross goto hermit_cave

    /** How far it looks for a cave (blocks). */
    public static final int GOTO_SEARCH_RADIUS = 12_000;
    /** You land this many blocks out from the cave's way in, facing it. */
    public static final int GOTO_STAND_OUT = 3;
}
