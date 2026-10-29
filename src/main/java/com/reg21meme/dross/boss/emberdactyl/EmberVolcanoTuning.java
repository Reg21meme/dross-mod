package com.reg21meme.dross.boss.emberdactyl;

/**
 * Every number of the Emberdactyl's volcano ({@link EmberVolcano}): its home on the Cinder Wastes, where it guards its
 * egg (the user's request, October 26, 2026: "a tall, steep volcano with an open crater: a lava lake at the top and the
 * nest on a basalt pillar ... lava streams, smoke, scorched bones, and a climbable path ... make this one have much more
 * lava, like heaps of lava"). For now it's only built by the showcase ({@code /dross showcase boss_designs
 * emberdactyl}); later the boss-site planner places one in every group of 3 Cinder Wastes patches, like the Cinder
 * Colossus's lair.
 * <p>
 * Distances are in blocks from the volcano's middle, heights in blocks above the ground it stands on.
 */
public final class EmberVolcanoTuning
{
    // ---- The cone ----

    /** The crater's rim stands this high above the ground. */
    public static final int HEIGHT = 70;
    /** The cone's foot: it meets the ground this far out (before its lobes push it in or out). */
    public static final double BASE_RADIUS = 58.0D;
    /** The rim's crest runs round this far out. */
    public static final double RIM_RADIUS = 18.0D;
    /** How the flanks steepen towards the top: the height at a share t of the way up is HEIGHT * t^power (1 = a straight
     *  cone; higher is a broad foot and a steep top, like a real stratovolcano). */
    public static final double PROFILE_POWER = 1.6D;
    /** The cone's foot is pushed in and out by these much (shares of its radius) in 3, 5 and 2 lobes round it. */
    public static final double LOBE_3 = 0.06D;
    public static final double LOBE_5 = 0.04D;
    public static final double LOBE_2 = 0.05D;
    /** Fine ridges round the flanks (blocks, at their highest halfway up) and how many there are round it. */
    public static final double RIDGE_HEIGHT = 1.6D;
    public static final int RIDGES = 11;
    /** Bumps all over the flanks (blocks either way) and their size. */
    public static final double ROUGHNESS = 1.4D;
    public static final double ROUGHNESS_SCALE = 6.0D;
    /** The rim's jagged crest: two waves (4 and 9 round it) and noise, in blocks up and down. */
    public static final double RIM_JAG_4 = 2.2D;
    public static final double RIM_JAG_9 = 1.4D;
    public static final double RIM_JAG_NOISE = 1.5D;
    /** Where the path reaches the rim the crest dips this many blocks (a notch), over this many radians either side. */
    public static final int NOTCH_DEPTH = 5;
    public static final double NOTCH_WIDTH = 0.24D;

    // ---- The crater, its lava lake and the nest ----

    /** The lava lake's surface lies this far below the rim's height. */
    public static final int CRATER_DEPTH = 13;
    /** The lake reaches this far out (its shore wobbles by LAKE_WOBBLE either way). */
    public static final double LAKE_RADIUS = 13.5D;
    public static final double LAKE_WOBBLE = 1.2D;
    /** The lake is this deep. */
    public static final int LAKE_DEPTH = 4;
    /** The crater's inner wall rises from the shore to the crest like u^power (higher: a cliff under the crest). */
    public static final double INNER_POWER = 1.3D;
    /** The nest's basalt pillar rises this high out of the lake, this wide across its top and its foot (round 2,
     *  October 27, 2026: the egg grew to hold its young curled up inside, about 11.4 blocks across for the 45-block
     *  Emberdactyl, so the nest's top is wide enough for it and its ring). */
    public static final int NEST_HEIGHT = 6;
    public static final double NEST_TOP_RADIUS = 7.8D;
    public static final double NEST_FOOT_RADIUS = 9.4D;
    /** The nest's ring of bones and charred logs on the pillar's top: from this far out to this far (just clear of the
     *  egg's widest, about 5.9 out). */
    public static final double NEST_RING_INNER = 6.2D;
    public static final double NEST_RING_OUTER = 7.6D;
    /** A bowl of ash this far round the egg's middle, magma round it out to the ring. */
    public static final double NEST_ASH_RADIUS = 4.5D;

    // ---- The lava on the flanks ----

    /** How many lava rivers run down the flanks, and how far round from the path's foot the first one starts (rad). */
    public static final int RIVERS = 8;
    public static final double RIVER_PHASE = 0.55D;
    /** Each river wanders this many blocks either side of its line, in waves this long. */
    public static final double RIVER_WIGGLE = 2.4D;
    public static final double RIVER_WIGGLE_LENGTH = 7.0D;
    /** A river is a cascade of lava pools (terraces), each this long down the slope with this much more between it and
     *  the next (with none, the lower pool's first row becomes the rock lip that holds the upper one), as wide (either
     *  side) as these at the top and at the foot. */
    public static final double POOL_LENGTH = 3.6D;
    public static final double POOL_GAP = 0.0D;
    public static final double POOL_HALF_WIDTH_TOP = 2.2D;
    public static final double POOL_HALF_WIDTH_FOOT = 4.6D;
    /** A pool (or pocket) is sunk to the lowest ground under it; ground more than this many blocks higher stays rock
     *  (so on the steep upper flanks the pools don't cut deep slots into the cone). */
    public static final int POOL_MAX_CUT = 5;
    /** The first pool of each river starts this far outside the rim, where it seeps out of a cinder vent. */
    public static final double RIVER_SOURCE_OUT = 3.0D;
    /** The gully each river runs down: this deep (blocks, halfway down) and this wide (blocks either side). */
    public static final double GULLY_DEPTH = 2.8D;
    public static final double GULLY_HALF_WIDTH = 4.5D;
    /** At each river's foot a lava pool this far past the cone's foot, this big round (plus up to POOL_RADIUS_SPREAD). */
    public static final double FOOT_POOL_OUT = 5.0D;
    public static final double FOOT_POOL_RADIUS = 5.6D;
    public static final double FOOT_POOL_RADIUS_SPREAD = 2.0D;
    public static final int FOOT_POOL_DEPTH = 2;
    /** Round each foot pool a field of cooled lava crust (magma and obsidian) this far past its edge, with lava showing
     *  through this share of it. */
    public static final double CRUST_REACH = 7.0D;
    public static final double CRUST_HOLES = 0.07D;
    /** Lava pockets sunk into the flanks between the rivers: how many, and how big round. */
    public static final int POCKETS = 26;
    public static final double POCKET_RADIUS_MIN = 1.2D;
    public static final double POCKET_RADIUS_MAX = 2.8D;
    /** Lava never comes within this many blocks of the path (it would spill onto it), measured square. */
    public static final int LAVA_PATH_CLEARANCE = 2;

    // ---- The path up ----

    /** The path is this many blocks wide either side of its line. */
    public static final double PATH_HALF_WIDTH = 1.5D;
    /** It climbs at most this many blocks for each block along it (it winds round the cone to keep to it). */
    public static final double PATH_GRADE = 0.6D;
    /** It starts this far past the cone's foot, right in front of the viewer, and is cut this far into the slope. */
    public static final double PATH_START_OUT = 2.0D;
    public static final int PATH_CUT = 1;
    /** At the top it runs on, level, this far inside the rim: a lookout over the crater, this wide either side. */
    public static final double LOOKOUT_IN = 2.5D;
    public static final double LOOKOUT_HALF_WIDTH = 2.5D;
    /** A bone post with a lantern on it beside the path every this many blocks (9 until the user asked for the way up
     * to be plainer to see, October 27). */
    public static final double PATH_LANTERN_EVERY = 6.0D;
    /**
     * The way up made plain (the user, October 27, 2026: "make the path up a little more obvious"): it's paved with
     * polished blackstone bricks, edged with a border of pale bone blocks (two pale lines winding up the dark cone by
     * day), and a shroomlight is set into its middle every this many blocks (a dotted line of light by night; the
     * lookout's middle gets one too).
     */
    public static final double PATH_LIGHT_EVERY = 3.0D;

    // ---- The hoard (the user, October 27, 2026: "add chests or gold at the top or ores and stuff") ----

    /** Chests at the lookout (either side of it, facing its middle), empty for now: the user will decide what goes in
     * them (October 27, 2026). */
    public static final int HOARD_CHESTS = 2;
    /** Gold heaped round the lookout (gold blocks, raw gold, gilded blackstone; now and then two high), within this
     * many blocks of its middle. */
    public static final int HOARD_GOLD = 16;
    public static final double HOARD_REACH = 5.5D;
    /** The share of the nest's ring of bones and logs that's gold instead (the egg on its hoard). */
    public static final double NEST_GOLD = 0.3D;
    /** The share of the crater wall's surface that's ore (Dross gold, Moonsilver, Stormite) glinting in the rock. */
    public static final double CRATER_ORES = 0.06D;

    // ---- What lies on it ----

    /** Basalt spires on the crest (how many, how tall) and obsidian shards on the upper flanks. */
    public static final int RIM_SPIRES = 12;
    public static final int SPIRE_MIN = 3;
    public static final int SPIRE_MAX = 7;
    public static final int OBSIDIAN_SHARDS = 9;
    /** Cinder vents (smoke and embers) on the upper flanks and in the crater. */
    public static final int VENTS = 30;
    /** Signal fires hidden in the crest: their smoke columns rise out of the crater. */
    public static final int SMOKE_PLUMES = 4;
    /** Scorched bones: piles of bone blocks, skulls, and two great ribcages half buried at the cone's foot. */
    public static final int BONE_PILES = 30;
    public static final int SKULLS = 16;
    public static final int RIBCAGES = 2;
    /** Ash lies in drifts (ash layers) on this share of the upper flanks. */
    public static final double ASH_DRIFTS = 0.16D;

    // ---- The ground round it ----

    /** Past the cone's foot the ground is re-laid as ash and basalt out to this far past it; beyond that, out to the
     *  edge, the ash thins out over the land that was there. */
    public static final double APRON_OUT = 15.0D;
    public static final double FRINGE_OUT = 22.0D;
    /** The ground under it is re-laid this many blocks deep (so nothing under the lava is hollow). */
    public static final int FOUNDATION = 4;
    /** Air is cleared this many blocks over the rim's height (so a volcano built before, or anything there, goes). */
    public static final int CLEAR_ABOVE = 16;

    // ---- Building ----

    /** It's built a piece at a time, at most this many milliseconds of work a tick (like the Colossus's lairs). */
    public static final long STEP_MILLIS = 40L;
    /** The seed the showcase's volcano is shaped from (the same volcano every time). */
    public static final long SHOWCASE_SEED = 0x456D6265725F566FL;

    private EmberVolcanoTuning() {}
}
