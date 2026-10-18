package com.reg21meme.dross.dimension.sites;

/**
 * Every number of the boss-site planner ({@link BossSites}; the user's decision, October 16 2026): how a boss biome's
 * patches are found and grouped, where a use is tried inside a patch, and the chances. Distances are in blocks.
 * <p>
 * The measurements these are based on (60 seeds, the real Dross biome source; the coordinator's measuring run, October
 * 16): the nearest Cinder Wastes patches start about 418 / 623 / 848 blocks from the hub, Blightwood's 419 / 552 / 724;
 * group 3 ends about 1,500-1,700 blocks out, group 10 about 3,100-3,400, group 30 about 5,700-6,200 (70-85 groups per
 * biome within 10,000 blocks). A typical patch is about 360 x 360 (Cinder) or 325 x 325 (Blightwood); the widest circle
 * inside one has a median diameter of 242 (Cinder) or 208 (Blightwood).
 */
public final class BossSitesTuning
{
    private BossSitesTuning() {}

    // ---------------------------------------------------------------------------------------------
    // Patches: the separate regions of a boss biome
    // ---------------------------------------------------------------------------------------------

    /**
     * The biome map the patches are read from: one sample per chunk, at the chunk's middle block (x = 16i + 8,
     * z = 16j + 8), so every map cell is a chunk. Two cells that touch along a side (not just at a corner) and are both
     * the biome are in the same patch.
     */
    public static final int CELL = 16;
    /** The biome is read this high: above every hill, so it's always the surface biome (the sewers lie far below). */
    public static final int BIOME_Y = 80;
    /**
     * A patch smaller than this (blocks², 16 x 16 per cell) is a speck: it's skipped, never numbered (about half the
     * pieces by count, under 1% of the land; the same rule as the measurements).
     */
    public static final int MIN_PATCH_AREA = 10_000;
    /**
     * A patch is followed out at most this many cells (16 million blocks², far bigger than any real one); if one ever
     * reached it, the rest would count as a patch of its own.
     */
    public static final int MAX_PATCH_CELLS = 65_536;
    /** Nothing is planned further than this from the hub: past it no boss site or sewer is placed. */
    public static final int MAX_DISTANCE = 32_000;

    // ---------------------------------------------------------------------------------------------
    // Reading the biome map (so world generation doesn't wait for it; see BossSites, "How it's worked out")
    // ---------------------------------------------------------------------------------------------

    /**
     * When the world starts, a background thread reads the biome map out to this far from the hub (blocks; about 1 s of
     * one thread's time, measured October 17 2026; the first 15-20 groups of each boss biome)...
     */
    public static final int READ_AT_START = 6_000;
    /**
     * ...then keeps it read this far past the furthest player in the Dross (blocks; reading a ring 10,000 blocks out
     * takes about 10 ms, so it keeps ahead of an elytra flight with plenty to spare).
     */
    public static final int READ_AHEAD = 3_000;
    /**
     * It also plans the groups of every patch within this distance of a player (blocks), so world generation finds them
     * planned (planning a group, its fit tests, takes about half a second).
     */
    public static final int PREPLAN_RADIUS = 1_024;
    /** Where the players are is noted this often (server ticks)... */
    public static final int READER_PLAYER_TICKS = 20;
    /** ...and the background reader looks at it this often once it has caught up (milliseconds, real time). */
    public static final int READER_PAUSE_MILLIS = 1_000;
    /** The background reader's thread priority (below normal: world generation and the server come first). */
    public static final int READER_THREAD_PRIORITY = Thread.NORM_PRIORITY - 2;
    /** The map is read a band of rings at a time: about this many cells (16 x 16 blocks each)... */
    public static final int BAND_CELLS = 16_384;
    /** ...but at least this many rings and at most this many. */
    public static final int BAND_MIN_RINGS = 4;
    public static final int BAND_MAX_RINGS = 64;
    /**
     * Rings are sampled this far ahead of the rings being joined into patches (a patch found on a ring reaches out past
     * it, and following it reads those cells; 64 rings = 1,024 blocks, more than nearly any patch reaches: with 24, a
     * sixth of the cells were left to be sampled one by one while joining).
     */
    public static final int SAMPLE_AHEAD_RINGS = 64;
    /** A thread that needs the map read further than it is looks this often whether another thread has read it meanwhile (ms). */
    public static final int READ_WAIT_POLL_MILLIS = 5;
    /** A wait for the map longer than this is logged (ms). */
    public static final int SLOW_READ_LOG_MILLIS = 250;

    // ---------------------------------------------------------------------------------------------
    // Groups: the 3 nearest patches, then the next 3, and so on forever
    // ---------------------------------------------------------------------------------------------

    /** Patches per group: each boss biome will have this many bosses, one per patch of a group. */
    public static final int GROUP_SIZE = 3;
    /** The seed is mixed with this before picking a group's boss patch (among the patches where the boss fits). */
    public static final long BOSS_PICK_SALT = 0x426F737350696B6BL;
    /** ...and with this before a patch's roll for a filler (a regular sewer in a Blightwood patch kept for a future boss). */
    public static final long FILLER_ROLL_SALT = 0x46696C6C6572526FL;

    // ---------------------------------------------------------------------------------------------
    // Where a use is tried inside a patch
    // ---------------------------------------------------------------------------------------------

    /**
     * A use (a boss lair, a sewer) is tried at most this many spots of a patch, the deepest first (furthest from the
     * patch's edge, where it has the most room round it)...
     */
    public static final int SITE_TRIES = 6;
    /** ...each at least this far from the spots tried before it. */
    public static final int SITE_TRY_SPACING = 48;

    // ---------------------------------------------------------------------------------------------
    // The uses
    // ---------------------------------------------------------------------------------------------

    /**
     * Blightwood: each of a group's two patches kept for future bosses gets a regular sewer with this chance (the
     * user's 1 in 2; a roll per patch fixed by the world seed), if one fits there.
     */
    public static final double REGULAR_SEWER_CHANCE = 0.5D;
    /**
     * The Cinder Colossus's lair, until the boss area plugs in its own test ({@link BossSites#setFinder}): a spot where
     * every column within this radius is Cinder Wastes (its volcano's foot, pools and way in reach about 64 blocks)...
     */
    public static final int COLOSSUS_FIT_RADIUS = 64;
    /** ...or, in a group where no patch has that much room, within this radius (the roomiest patch). */
    public static final int COLOSSUS_RELAXED_FIT_RADIUS = 48;
    /** The disc test samples rings this far apart, with samples this far apart round each ring. */
    public static final int DISC_SAMPLE_STEP = 8;
}
