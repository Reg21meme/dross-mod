package com.reg21meme.dross.boss.lair;

/**
 * Every number of the Cinder Colossus's lair in the Cinder Wastes (the user's round 2 request, made volcanic in R4b):
 * where it goes, its shape, how it's built, and when its Colossus rises. Distances are in blocks, times in ticks
 * (20 ticks = 1 second).
 * <p>
 * The lair is a volcano: a rough cone of dark rock rising well above the land, a jagged crater at its top that opens
 * into a domed cavern (the arena), and a big cave mouth in its flank. Heights are measured from the cavern's floor:
 * "N blocks above the floor" means N blocks of air stand on the floor there.
 */
public final class LairTuning
{
    // ---------------------------------------------------------------- where it goes (the boss-site planner's fit test, LairFinder)

    /*
     * Where the lairs go is the boss-site planner's (dimension/sites/BossSites, the user's rule of October 16): the Cinder
     * Wastes' patches in groups of 3, nearest the hub first, one lair per group. These numbers are the lair's own fit test
     * (LairFinder): where in a patch a volcano fits, read from the world generator's noise alone.
     */

    /**
     * Every column within this many blocks of the lair's middle must be Cinder Wastes: the volcano's foot reaches
     * {@link #CONE_BASE_RADIUS} (52) and its lava pools and the way in a little further.
     */
    public static final int FIT_RADIUS = 64;
    /**
     * In a group where no patch has that much room, the planner asks the roomiest patch for the lair's smallest form:
     * then only this radius must be Cinder Wastes (the cone's foot may reach a little past the biome's edge).
     */
    public static final int RELAXED_FIT_RADIUS = 48;
    /** The quick check of a spot's biome samples rings this far apart... */
    public static final int FIT_COARSE_RING_STEP = 14;
    /** ...and the full check rings this far apart, with samples this far apart round each ring. */
    public static final int FIT_FINE_STEP = 6;
    /** The fit test looks at up to this many spots of a patch, the deepest first (furthest from its edge)... */
    public static final int FINDER_SPOTS = 16;
    /** ...each at least this far from the spots before it. */
    public static final int FINDER_SPOT_SPACING = 24;
    /** Of the spots that fit, this many (the ones that rise most above their surroundings) get a full ground survey. */
    public static final int PREFILTER_KEEP = 8;
    /** The quick "does it rise" check compares the middle with the ground this far out, north, east, south and west. */
    public static final int ROUGH_RISE_DISTANCE = 34;
    /** The best spot is then nudged by these steps (blocks, in 8 directions each) while that buries the dome deeper. */
    public static final int[] REFINE_STEPS = {12, 6};
    /**
     * The volcano must be a landmark: at the spot picked, its rim must be able to stand {@link #RIM_RISE} above the high
     * land round it without going past {@link #MAX_RIM_HEIGHT} (a deep valley under a mesa can't). If it can't, the next
     * best spot is tried, up to this many (in a relaxed patch the best one is used anyway).
     */
    public static final int LANDMARK_TRIES = 4;
    /**
     * The landmark test reads the land's quick height, which can be a block or two off the real ground: it wants the rim's
     * full rise to fit under its cap with this many blocks to spare.
     */
    public static final int LANDMARK_MARGIN = 4;
    /** A ground survey samples the floor's disc every this many blocks. */
    public static final int PLAN_GRID_STEP = 4;
    /** The "high ground" over the dome is this share of the way up the ground heights inside {@link #HIGH_GROUND_RADIUS}... */
    public static final double HIGH_GROUND_PERCENTILE = 0.3D;
    /** ...of the floor's middle. */
    public static final double HIGH_GROUND_RADIUS = 14.0D;
    /** The search's scoring: each block its old-style tunnel would go down counts this much against a spot. */
    public static final double DESCENT_COST = 0.1D;
    /** The search's scoring: a spot scores best when natural rock stands at least this many blocks over the dome. */
    public static final int COVER = 2;
    /** The search's scoring: its scoring tunnel goes down between these many blocks (the first lair's shape, kept as a yardstick). */
    public static final int MIN_DESCENT = 3;
    public static final int MAX_DESCENT = 6;
    /** The scoring tunnel is 2 x 1 + 1 = 3 blocks wide... */
    public static final int TUNNEL_HALF_WIDTH = 1;
    /** ...level for this many blocks out of the cavern, then climbing to its mouth. */
    public static final int TUNNEL_STRAIGHT = 8;

    // ---------------------------------------------------------------- the cavern (the arena)

    /** The round floor reaches this many blocks from its middle block (2 x 20 + 1 = 41 blocks across). */
    public static final int FLOOR_RADIUS = 20;
    /** At the wall the ceiling is this many blocks above the floor (the Colossus is 5 tall)... */
    public static final int WALL_HEIGHT = 10;
    /**
     * ...and in the middle this many: the dome. Its lava lobs peak about 14 blocks above the floor halfway to their
     * target, where the dome is still about 20 high.
     */
    public static final int DOME_HEIGHT = 24;
    /** The cavern's walls and dome are lined with dark rock this many blocks thick (whatever was there before)... */
    public static final int SHELL_THICKNESS = 3;
    /** ...and so is the ground under its floor. */
    public static final int UNDER_FLOOR = 3;
    /** The walls and ceiling are carved up to this many blocks further out, by a smooth noise, so they look rough. */
    public static final double ROUGHNESS = 1.0D;
    /** The roughness noise's size (blocks per bump). */
    public static final double ROUGHNESS_SCALE = 5.0D;
    /** The crater's throat: where it opens through the top of the dome, this radius (11 blocks across). */
    public static final double SKYLIGHT_RADIUS = 5.0D;
    /** Glowing magma veins running up the walls and over the dome... */
    public static final int MAGMA_VEINS = 10;
    /** ...each this many blocks long... */
    public static final int VEIN_MIN_LENGTH = 8;
    public static final int VEIN_MAX_LENGTH = 18;
    /** ...wandering sideways by up to this much (radians) per block... */
    public static final double VEIN_WOBBLE = 0.12D;
    /** ...none starting within this many degrees of the way in. */
    public static final double VEIN_TUNNEL_CLEARANCE_DEGREES = 14.0D;

    // ---------------------------------------------------------------- the floor

    /** The floor is obsidian, with patches of blackstone where a smooth noise is above this (about a third of it)... */
    public static final double BLACKSTONE_PATCHES = 0.18D;
    /** ...a noise this many blocks per patch. */
    public static final double PATCH_SCALE = 6.0D;
    /** The middle: a chiseled polished blackstone block in a disc of polished blackstone this far out. */
    public static final double MEDALLION = 2.5D;
    /** Two rings of polished blackstone bricks, this far from the middle (the arena's pattern)... */
    public static final double[] RINGS = {7.0D, 14.0D};
    /** ...with this many magma blocks evenly spaced round each (the "periodic" magma)... */
    public static final int[] RING_MAGMA = {6, 10};
    /** ...and this share of the ring's bricks cracked. */
    public static final float CRACKED_SHARE = 0.12F;
    /** Besides the rings' magma, this share of the floor is a magma block (sparse: magma burns anyone not sneaking). */
    public static final float SCATTERED_MAGMA = 0.012F;
    /** The floor's last block row is a border of polished blackstone. */
    public static final double BORDER = 1.0D;

    // ---------------------------------------------------------------- the lava under the floor

    /**
     * Glowing fissures: cracks of glass in the floor with lava one block under them (sealed in rock: nobody can fall in
     * or touch it). This many run out from the medallion towards the wall...
     */
    public static final int FISSURES = 5;
    /** ...starting this far from the middle... */
    public static final double FISSURE_START = 3.5D;
    /** ...and ending between these distances from the middle (always inside the floor's polished border)... */
    public static final double FISSURE_MIN_END = 14.5D;
    public static final double FISSURE_MAX_END = 18.5D;
    /** ...wandering sideways by up to this much (radians) per step... */
    public static final double FISSURE_WOBBLE = 0.35D;
    /** ...and each grows a short side crack with this chance per step (side cracks are this many blocks long). */
    public static final double FISSURE_BRANCH_CHANCE = 0.08D;
    public static final int FISSURE_BRANCH_MIN = 3;
    public static final int FISSURE_BRANCH_MAX = 6;
    /** A fissure also runs along the way in, from the cavern to this many blocks short of the mouth (the glow inside it). */
    public static final int PASSAGE_FISSURE_SHORT = 2;

    // ---------------------------------------------------------------- the stalactites

    /** Obsidian spikes hanging from the ceiling: this many... */
    public static final int STALACTITES = 30;
    /** ...each this many blocks long (shorter where the room below must stay clear)... */
    public static final int STALACTITE_MIN_LENGTH = 2;
    public static final int STALACTITE_MAX_LENGTH = 8;
    /** ...thick (3 x 3) at the top for this share of its length when it's at least this long... */
    public static final double STALACTITE_THICK_SHARE = 0.3D;
    public static final int STALACTITE_THICK_LENGTH = 5;
    /** ...a plus shape for this share... */
    public static final double STALACTITE_PLUS_SHARE = 0.65D;
    /** ...hanging between these distances from the middle (not over the throat), none within this angle of the way in. */
    public static final double STALACTITE_MIN_RADIUS = SKYLIGHT_RADIUS + 2.5D;
    public static final double STALACTITE_MAX_RADIUS = FLOOR_RADIUS - 1.5D;
    public static final double STALACTITE_TUNNEL_CLEARANCE_DEGREES = 16.0D;
    /**
     * Over the middle (within {@link #LOB_CLEARANCE_RADIUS} of it) every tip stays at least this many blocks above the
     * floor: the lava lobs peak about 14 up, and the Colossus rears up with its fists high...
     */
    public static final int LOB_CLEARANCE = 16;
    public static final double LOB_CLEARANCE_RADIUS = 13.0D;
    /** ...and everywhere else at least this many (the Colossus is 5 tall; its slam lifts its fists to about 7). */
    public static final int STALACTITE_CLEARANCE = 8;

    // ---------------------------------------------------------------- the volcano

    /** The cone meets the land at most this far from the middle. */
    public static final double CONE_BASE_RADIUS = 52.0D;
    /** The crater's rim: a crest of jagged rock between these distances from the middle (the crater is ~19 across). */
    public static final double CRATER_RADIUS = 9.5D;
    public static final double RIM_RADIUS = 13.0D;
    /** The rim's crest is at least this many blocks above the floor... */
    public static final int RIM_HEIGHT = 40;
    /**
     * ...and at least this many above the high land round it (within {@link #RIM_LAND_RADIUS}, out past its foot): it
     * always stands out...
     */
    public static final int RIM_RISE = 20;
    public static final double RIM_LAND_RADIUS = 60.0D;
    /**
     * "The high land round it" is the higher of two measures: the 90th percentile of the land inside
     * {@link #RIM_LAND_RADIUS}, and the land on a ring this far from the middle (just past the cone's foot), at this
     * percentile of this many samples round it. The second is how the headless check measures the landmark, so a cone on
     * land rising outwards (or on a steep slope) is planned tall enough too (it once came out a block short: R7).
     */
    public static final double LANDMARK_RING = CONE_BASE_RADIUS + 8.0D;
    public static final double LANDMARK_RING_PERCENTILE = 0.8D;
    public static final int LANDMARK_RING_SAMPLES = 72;
    /** ...but never more than this many above the floor. */
    public static final int MAX_RIM_HEIGHT = 64;
    /**
     * The slopes: the cone's height above the land falls from the rim to its foot as {@code (1 - t)^CONE_PROFILE}
     * ({@code t} 0 at the rim, 1 at the foot): steep near the top, a long gentle skirt.
     */
    public static final double CONE_PROFILE = 1.3D;
    /** The land under the cone is taken as a sloping plane (fitted to the ground round its foot), tilted at most this much. */
    public static final double MAX_BASE_TILT = 0.35D;
    /** The plane sits this far up the ground heights round the foot (low: the cone fills dips, never builds a step). */
    public static final double BASE_PERCENTILE = 0.35D;
    /** Rough slopes: lumps of up to this many blocks, this many blocks across... */
    public static final double LUMPS = 2.5D;
    public static final double LUMP_SCALE = 11.0D;
    /** ...small bumps on them... */
    public static final double FINE_LUMPS = 1.2D;
    public static final double FINE_LUMP_SCALE = 4.5D;
    /** ...and this many ridges running down the slopes, up to this many blocks high. */
    public static final int RIBS = 9;
    public static final double RIB_HEIGHT = 3.0D;
    /** The rim's crest: jagged teeth up to this many blocks above it, and notches down to this many below. */
    public static final double RIM_TEETH = 4.5D;
    public static final double RIM_NOTCHES = 2.5D;
    /** The crater narrows from the rim to the throat like a funnel; this shapes it (below 1: it stays wide, then drops). */
    public static final double CRATER_FLARE = 0.7D;
    /** The crater's walls are carved up to this many blocks further out (rough). */
    public static final double CRATER_ROUGHNESS = 1.5D;
    /** At least this many blocks of rock over the cavern's lining everywhere. */
    public static final int MIN_COVER = 3;
    /** On the upper slopes (within this distance of the middle) some surface blocks are magma, glowing at night. */
    public static final double HOT_SLOPE_RADIUS = 24.0D;
    public static final float HOT_SLOPE_MAGMA = 0.07F;
    /** Ash lies on the volcano's shoulders: this share of the surface between the rim and {@link #ASH_RADIUS}... */
    public static final float SHOULDER_ASH = 0.6F;
    public static final double ASH_RADIUS = 34.0D;
    /** ...and this share further down. */
    public static final float LOWER_ASH = 0.3F;

    // ---------------------------------------------------------------- the way in: a cave mouth in the flank

    /** The mouth is 2 x 4 + 1 = 9 blocks wide at the floor... */
    public static final int MOUTH_HALF_WIDTH = 4;
    /** ...and 8 tall (9 in the middle: it's an arch). */
    public static final int MOUTH_HEIGHT = 8;
    /** Inside, it narrows to a passage 2 x 2 + 1 = 5 wide and 6 tall (the Colossus, 4 wide and 5 tall, just fits)... */
    public static final int PASSAGE_HALF_WIDTH = 2;
    public static final int PASSAGE_HEIGHT = 6;
    /** ...over this many blocks behind the mouth. */
    public static final int MOUTH_FLARE = 5;
    /**
     * The mouth sits where the volcano's flank is still this many blocks above its top (its brow), but between these
     * distances from the middle (rock is built up over it if the flank is lower).
     */
    public static final int BROW = 4;
    public static final int MIN_MOUTH_DISTANCE = 30;
    public static final int MAX_MOUTH_DISTANCE = 40;
    /** Its rock lip is jagged: walls and roof carved up to this much further (the noise), and obsidian teeth hang at its top. */
    public static final double MOUTH_ROUGHNESS = 0.8D;
    public static final int MOUTH_TEETH = 4;
    /** The rock framing the mouth is obsidian and blackstone, with this share glowing magma (so the mouth shows from afar). */
    public static final float LIP_MAGMA = 0.25F;
    /** In front of the mouth a gully is cut into the flank down to the floor's level: this much wider per block out... */
    public static final double GULLY_FLARE = 0.45D;
    /** ...and per block up (its walls lean back)... */
    public static final double GULLY_WALL_SLOPE = 0.45D;
    /** ...until the flank is no higher than the floor, at most this many blocks out from the mouth. */
    public static final int MAX_GULLY = 20;
    /**
     * Then a level apron this many blocks long, and a blend to the land at least this long: a column n blocks out from
     * the apron stays within n blocks of the floor, and no column is ever more than a block above or below the next
     * (north, east, south or west), so the way in can always be walked (R6b: before, steep or uneven land could leave a
     * 2-block step in the blend).
     */
    public static final int APRON = 4;
    public static final int BLEND = 8;
    /**
     * Where the land just beyond the blend is still more than a block above or below it (steep or uneven land in front),
     * the shaped land goes on out, a ramp of at most a block per step, until it meets the land: at most this many rows
     * further. If it still can't meet the land, the mouth faces the next best way ({@link LairShape#volcanoPlans}).
     */
    public static final int APPROACH_MAX_EXTRA = 24;
    /** The direction of the way in is picked where the land in front of the flank is lowest and most even (between these). */
    public static final int APPROACH_SURVEY_FROM = 32;
    public static final int APPROACH_SURVEY_TO = 50;
    /** Where a player arriving stands: this many blocks out from the mouth, in the gully, facing in. */
    public static final int ENTRANCE_GAP = 4;
    /** A dim magma light is set into the passage's walls every this many blocks. */
    public static final int PASSAGE_LIGHT_SPACING = 4;

    // ---------------------------------------------------------------- lava outside the volcano

    /** Lava rivulets running down the slopes from just under the rim: this many... */
    public static final int RIVULETS = 4;
    /** ...starting this far from the middle (plus up to 3)... */
    public static final double RIVULET_START = 17.0D;
    /** ...none within this many degrees of the way in... */
    public static final double RIVULET_CLEARANCE_DEGREES = 55.0D;
    /** ...stepping down at most this many blocks at a time... */
    public static final int RIVULET_MAX_DROP = 3;
    /** ...wandering by up to this much (radians)... */
    public static final double RIVULET_MEANDER = 0.35D;
    /** ...and pooling at the volcano's foot, at most this far out, in a pool this big (radius). */
    public static final double POOL_DISTANCE = CONE_BASE_RADIUS - 6.0D;
    public static final double POOL_RADIUS = 2.6D;
    /** The rock banks beside the outside lava: this share magma (a glowing crust), this share obsidian (cooled lava). */
    public static final float BANK_MAGMA = 0.5F;
    public static final float BANK_OBSIDIAN = 0.3F;
    /** A rivulet gives up after this many steps (it then ends in its pool). */
    public static final int RIVULET_MAX_STEPS = 90;
    /** Nothing that can burn is left this far round any lava (sideways), or this far above it (lava sets fire upwards). */
    public static final int FIRE_GUARD_REACH = 3;
    public static final int FIRE_GUARD_UP = 4;

    // ---------------------------------------------------------------- smoke from the crater

    /** Smoke and embers rise from the crater for players within this many blocks of it... */
    public static final double SMOKE_RANGE = 224.0D;
    /** ...every this many ticks: tall plumes of smoke... */
    public static final int SMOKE_INTERVAL_TICKS = 4;
    public static final int SMOKE_PLUMES = 2;
    /** ...rising this fast (blocks per tick; each plume lives about 15 s, so it rises about 20 blocks)... */
    public static final double SMOKE_RISE = 0.075D;
    /** ...a dark puff now and then (1 in this many times)... */
    public static final int SMOKE_PUFF_ONE_IN = 3;
    /** ...and embers: lava pops and small flames (1 in this many times, this many each). */
    public static final int EMBER_ONE_IN = 2;
    public static final int EMBERS = 3;

    // ---------------------------------------------------------------- building it (lazily: when a player first comes near)

    /**
     * Every planned lair (one per group of 3 Cinder Wastes patches) is built the first time a player in the Dross comes
     * within this many blocks of its middle: further than anyone can see a volcano rise (a client's render distance is at
     * most 32 chunks, 512 blocks, but the volcano's own land only loads round a player within the server's view distance),
     * so it's normally standing long before anyone gets there.
     */
    public static final int BUILD_RANGE = 384;
    /** Which planned lairs are near each player is asked (on a background thread) this often (ticks). */
    public static final int NEAR_CHECK_TICKS = 40;
    /** At most this many lairs are loaded and built at the same time (each loads about 200 chunks). */
    public static final int MAX_BUILDS_AT_ONCE = 1;
    /**
     * A lair is built in steps, one step a tick (trees, the cone, the shell and cavern, the crater and floor, the way in
     * and stalactites, the safety passes and veins, then all the lava at once), so the server never stalls for the whole
     * build (about 1.6 s) in one tick.
     */
    public static final int BUILD_STEPS = 7;
    /** As the world starts, the first this many groups' lair spots are worked out on a background thread (so they're ready). */
    public static final int WARM_UP_GROUPS = 2;

    /**
     * Chunks this many blocks round the middle are loaded (generated if new) before it's built: the cone, and the way in
     * at its longest (the mouth 40 out, a 20-block gully, the apron, the blend and its ramp: about 97 out, 22 to the side).
     */
    public static final int LOAD_RADIUS = 104;
    /** If they still aren't loaded after this long (3 minutes, real time), it's built anyway (the rest load then). */
    public static final long LOAD_TIMEOUT_MILLIS = 3 * 60 * 1000L;
    /** After building, the chunks stay loaded this many ticks (15 seconds) so their lighting is done before they're saved. */
    public static final int HOLD_AFTER_BUILD_TICKS = 300;
    /** A tree in the way is removed whole: at most this many logs and leaves... */
    public static final int TREE_MAX_BLOCKS = 400;
    /** ...within this many blocks of where it was found. */
    public static final int TREE_REACH = 16;
    /** Under the passage and the apron, gaps are filled down to the ground, at most this many blocks. */
    public static final int FILL_DEPTH = 12;

    // ---------------------------------------------------------------- the boss

    /*
     * No respawn (the user's decision, October 16: "do not include respawn feature"): a lair's Colossus rises once, for the
     * first player who steps onto its floor, and once it's beaten that lair stays empty for good.
     */
    /** Whether a player has walked in is checked this often (ticks). */
    public static final int CHECK_INTERVAL_TICKS = 10;
    /** A living lair Colossus found further than this from the floor's edge (it fits up the passage now) is brought back. */
    public static final double HOME_MARGIN = 1.5D;
    /** No natural spawning this far beyond the floor's edge (and in the passage and the gully), so the fight stays clean. */
    public static final double NO_SPAWN_MARGIN = 2.0D;
    /** The no-spawn zone reaches this many blocks out from the mouth, along the gully. */
    public static final int NO_SPAWN_GULLY = 6;

    private LairTuning() {}
}
