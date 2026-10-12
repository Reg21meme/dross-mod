package com.reg21meme.dross.boss.lair;

/**
 * Every number of the Cinder Colossus's lair in the Cinder Wastes (the user's round 2 request): where it goes, its
 * shape, how it's built, and when its Colossus rises. Distances are in blocks, times in ticks (20 ticks = 1 second).
 * <p>
 * The cavern is measured from its floor: "N blocks above the floor" means N blocks of air stand on the floor there.
 */
public final class LairTuning
{
    // ---------------------------------------------------------------- where it goes (searched with the world generator's noise)

    /** The lair is looked for in this many directions from the hub at 0,0... */
    public static final int DIRECTIONS = 3;
    /** ...this many degrees apart... */
    public static final double DIRECTION_SPACING_DEGREES = 120.0D;
    /**
     * ...turned by an angle from the world seed (0 to 120 degrees, so each world's directions differ). The seed is mixed
     * with this number first, so the angle isn't tied to other seed-based choices.
     */
    public static final long ROTATION_SALT = 0x436F6C6F7373L;
    /** Along each direction it looks in a narrow cone, this many degrees either side of the line. */
    public static final double CONE_HALF_ANGLE_DEGREES = 20.0D;
    /** Nothing closer to the hub than this (the hub's Silverwood Forest reaches about 200 blocks out anyway)... */
    public static final int MIN_DISTANCE = 256;
    /** ...and nothing further than this (every biome is usually within about 1,500 blocks of the hub). */
    public static final int MAX_DISTANCE = 6144;
    /** The cone is walked outwards in steps of this many blocks... */
    public static final int DISTANCE_STEP = 16;
    /** ...with spots across it at most this many blocks apart. */
    public static final int LATERAL_STEP = 32;
    /**
     * The first spot (going outwards) where the lair fits is the nearest Cinder Wastes; the spots up to this many blocks
     * further out are compared too, and the one on the highest ground (the most rock over the dome) is used.
     */
    public static final int WINDOW = 192;
    /** Every column within this many blocks of the lair's middle must be Cinder Wastes (the lair reaches about 47). */
    public static final int FIT_RADIUS = 56;
    /** The quick check of a spot's biome samples rings this far apart... */
    public static final int FIT_COARSE_RING_STEP = 14;
    /** ...and the full check rings this far apart, with samples this far apart round each ring. */
    public static final int FIT_FINE_STEP = 6;
    /** Of the spots that fit, this many (the ones that rise most above their surroundings) get a full ground survey. */
    public static final int PREFILTER_KEEP = 12;
    /** The quick "does it rise" check compares the middle with the ground this far out, north, east, south and west. */
    public static final int ROUGH_RISE_DISTANCE = 34;
    /** The best spot is then nudged by these steps (blocks, in 8 directions each) while that buries the dome deeper. */
    public static final int[] REFINE_STEPS = {12, 6};
    /** A ground survey samples the floor's disc every this many blocks. */
    public static final int PLAN_GRID_STEP = 4;
    /** The "high ground" over the dome is this share of the way up the ground heights inside {@link #HIGH_GROUND_RADIUS}... */
    public static final double HIGH_GROUND_PERCENTILE = 0.3D;
    /** ...of the floor's middle. */
    public static final double HIGH_GROUND_RADIUS = 14.0D;
    /** Each block the entrance goes down counts this much against a spot (a short slope is nicer to walk). */
    public static final double DESCENT_COST = 0.1D;
    /** The biome is read this high (the Dross's biomes are the same at every height). */
    public static final int BIOME_Y = 80;

    // ---------------------------------------------------------------- the cavern

    /** The round floor reaches this many blocks from its middle block (2 x 20 + 1 = 41 blocks across). */
    public static final int FLOOR_RADIUS = 20;
    /** At the wall the ceiling is this many blocks above the floor (the Colossus is 5 tall)... */
    public static final int WALL_HEIGHT = 10;
    /**
     * ...and in the middle this many: the dome. Its lava lobs peak about 14 blocks above the floor halfway to their
     * target, where the dome is still about 20 high.
     */
    public static final int DOME_HEIGHT = 24;
    /** The cavern's walls and dome are lined with Cinder rock this many blocks thick (whatever was there before)... */
    public static final int SHELL_THICKNESS = 3;
    /** ...and so is the ground under its floor. */
    public static final int UNDER_FLOOR = 3;
    /** The walls and ceiling are carved up to this many blocks further out, by a smooth noise, so they look rough. */
    public static final double ROUGHNESS = 1.0D;
    /** The roughness noise's size (blocks per bump). */
    public static final double ROUGHNESS_SCALE = 5.0D;
    /** A spot scores best when natural rock stands at least this many blocks over the dome. */
    public static final int COVER = 2;
    /**
     * Where the dome rises above the natural ground, a mound of rock covers it: this far out from the middle...
     */
    public static final double MOUND_RADIUS = 27.5D;
    /** ...and this high above the floor in the middle (a rounded, lumpy volcanic dome)... */
    public static final int MOUND_HEIGHT = 28;
    /** ...made lumpy by up to this many blocks. */
    public static final double MOUND_ROUGHNESS = 2.0D;
    /** The skylight: a round shaft from the top of the dome to the open sky, this radius (11 blocks across, about 10 corner to corner). */
    public static final double SKYLIGHT_RADIUS = 5.0D;
    /** The shaft's walls are lined with Cinder rock this thick. */
    public static final double SKYLIGHT_LINING = 2.0D;
    /** A low wall runs round the top of the shaft, this far out from its edge (so nobody walks into a 25-block drop). */
    public static final double SKYLIGHT_RIM = 1.2D;
    /** The floor's middle medallion (chiseled nether bricks) reaches this far out. */
    public static final double MEDALLION = 1.5D;
    /** Two rings of nether bricks with shroomlights set in them, this far from the middle... */
    public static final double INNER_RING = 7.0D;
    public static final double OUTER_RING = 14.0D;
    /** ...with this many lights on each. */
    public static final int INNER_RING_LIGHTS = 6;
    public static final int OUTER_RING_LIGHTS = 10;
    /** How much of the red nether brick floor is cracked. */
    public static final float CRACKED_SHARE = 0.1F;
    /** Lava basins set into the foot of the wall (lava you can see over a low wall, which can't flow anywhere)... */
    public static final int LAVA_BASINS = 6;
    /** ...none within this many degrees of the tunnel. */
    public static final double BASIN_TUNNEL_CLEARANCE_DEGREES = 35.0D;
    /** The basin's open niche above the lava is this many blocks tall. */
    public static final int BASIN_NICHE_HEIGHT = 3;
    /** Glowing magma veins running up the walls and over the dome... */
    public static final int MAGMA_VEINS = 10;
    /** ...each this many blocks long... */
    public static final int VEIN_MIN_LENGTH = 8;
    public static final int VEIN_MAX_LENGTH = 18;
    /** ...wandering sideways by up to this much (radians) per block. */
    public static final double VEIN_WOBBLE = 0.12D;

    // ---------------------------------------------------------------- the tunnel

    /** The tunnel is 2 x 1 + 1 = 3 blocks wide (the Colossus's 4-block body can't get in)... */
    public static final int TUNNEL_HALF_WIDTH = 1;
    /** ...and 5 blocks tall. */
    public static final int TUNNEL_HEIGHT = 5;
    /** It runs straight and level for this many blocks out from the cavern's floor edge... */
    public static final int TUNNEL_STRAIGHT = 8;
    /** ...then climbs (half a block per block, on nether brick slabs) to the surface, between these many blocks. */
    public static final int MIN_DESCENT = 3;
    public static final int MAX_DESCENT = 6;
    /** In front of the mouth this many blocks of level ground are cut (or filled) to the mouth's height. */
    public static final int APPROACH = 3;
    /** Under the tunnel and the approach, gaps are filled down to the ground, at most this many blocks. */
    public static final int FILL_DEPTH = 12;
    /** A dim magma light is set into the tunnel's walls every this many blocks. */
    public static final int TUNNEL_LIGHT_SPACING = 4;
    /** No magma vein starts within this many degrees of the tunnel. */
    public static final double VEIN_TUNNEL_CLEARANCE_DEGREES = 12.0D;

    // ---------------------------------------------------------------- building it

    /** Chunks this many blocks round the middle are loaded (generated if new) before it's built. */
    public static final int LOAD_RADIUS = 64;
    /** If they still aren't loaded after this long (3 minutes, real time), it's built anyway (the rest load then). */
    public static final long LOAD_TIMEOUT_MILLIS = 3 * 60 * 1000L;
    /** After building, the chunks stay loaded this many ticks (10 seconds) so their lighting is done before they're saved. */
    public static final int HOLD_AFTER_BUILD_TICKS = 200;
    /** A tree in the way is removed whole: at most this many logs and leaves... */
    public static final int TREE_MAX_BLOCKS = 400;
    /** ...within this many blocks of where it was found. */
    public static final int TREE_REACH = 16;

    // ---------------------------------------------------------------- the boss

    /**
     * After the lair's Colossus is beaten, it rises again for the next player who walks in once this many ticks of game
     * time have passed: 10 minutes. A stand-in until the "boss revive" feature (the user's, later).
     */
    public static final int LAIR_RESPAWN_TICKS = 12000;
    /** Whether a player has walked in is checked this often (ticks). */
    public static final int CHECK_INTERVAL_TICKS = 10;
    /** A living lair Colossus found further than this from the floor's edge (pushed or teleported out) is brought back. */
    public static final double HOME_MARGIN = 1.5D;
    /** No natural spawning this far beyond the floor's edge (and in the tunnel), so the fight stays clean. */
    public static final double NO_SPAWN_MARGIN = 2.0D;

    private LairTuning() {}
}
