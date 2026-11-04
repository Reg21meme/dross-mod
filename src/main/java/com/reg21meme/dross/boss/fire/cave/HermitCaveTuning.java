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
    /** Each cave is an underground one (a shallow den under a mound, on flat land) this share of the time, when one
     * fits, else a hillside one (dug into a hill), when one fits; a patch with no hillside gets underground ones (the
     * user, November 2, 2026: "there's a hill, and he builds inside the hill ... we can have some underground ones"). */
    public static final double UNDERGROUND_CHANCE = 0.3D;
    /** Spots tried per patch (picked by the seed in the box round it). An underground cave goes on the flattest of the
     * first {@link #TRIES} that fit; a hillside one where the hill over it is fullest of all {@link #HILL_TRIES}. */
    public static final int TRIES = 96;
    public static final int HILL_TRIES = 192;
    /** A cave's middle is at least this many blocks inside its patch (the templates are 33 and 37 across: their corners
     * stay inside the Cinder Wastes). */
    public static final int MIN_DEPTH = 28;
    /** ...or, when the normal rules leave a patch short of its caves, this many. */
    public static final int RELAXED_MIN_DEPTH = 20;
    /** A cave's middle at least this many blocks from the spot of the patch's Cinder Colossus lair or Emberdactyl
     * volcano (both are built later, near players, and reshape the land out to about 90 blocks round them). */
    public static final int BOSS_CLEARANCE = 112;

    // ---------------------------------------------------------------- its crab

    /** A crab spot's free room is this many blocks wide (and deep) round the crab's middle: a cave's crab wears only a
     *  shell whose hitbox fits in it (the horned skull's, 7.93 wide, doesn't; the others are 6.26). */
    public static final double CRAB_ROOM_WIDTH = 7.0D;
    /** ...or this many, with the looser rules. */
    public static final int RELAXED_BOSS_CLEARANCE = 100;
    /** The two caves of a patch at least this many blocks apart. */
    public static final int CAVE_SPACING = 56;
    /** The land under an underground cave (its middle, corners and way in, from the noise) may rise this many blocks at
     * most from its lowest to its highest point; spots over it are tried only when nothing flatter fits. */
    public static final int MAX_RELIEF = 6;
    /** How far out from an underground cave's middle the land is read for its relief (blocks; its template is 37
     * across). */
    public static final int RELIEF_REACH = 18;

    // ---------------------------------------------------------------- a hillside cave's hill (from the noise)

    /** The land over a hillside cave's den (its middle) at least this many blocks over the ground at its way in: the
     * way in is in the hillside's face and the hill is its roof. */
    public static final int HILL_MIN_RISE = 6;
    /** ...and over its rooms (its den and burrows), on average, at least this share of the way up to its shell's top
     * ({@link HermitCaveDesigns.Cave#roofRise}); the rest of the shell shows as a rocky outcrop. Of the spots that fit,
     * the one with the hill fullest over it wins. */
    public static final double HILL_MIN_COVER = 0.6D;
    /** A first quick look: the land at the spot's middle at least this many blocks over the land at its way in, before
     * the rest is read. */
    public static final int HILL_QUICK_RISE = 4;
    /** The spots and facings are first screened with the noise's quick 2D height; the best this many are then checked
     * against the real ground (the generator's own top block, the ground the cave is built on): on rocky ground the
     * terrain's 3D roughness moves it a few blocks, now and then up to 7, enough to put a cave the quick height saw in
     * a hillside onto a knoll. */
    public static final int HILL_VERIFY = 8;
    /** The land this many blocks out in front of the way in (where you walk up to it)... */
    public static final int APPROACH_OUT = 3;
    /** ...at most this many blocks under the ground at the way in (a gentle walk up to it, not a cliff)... */
    public static final int APPROACH_DROP = 3;
    /** ...and at most this many over it (the way in isn't in a hole). */
    public static final int APPROACH_RISE = 1;
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
