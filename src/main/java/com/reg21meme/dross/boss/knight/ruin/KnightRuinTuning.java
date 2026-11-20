package com.reg21meme.dross.boss.knight.ruin;

import com.reg21meme.dross.boss.knight.KnightRuinDesigns;

import java.util.Set;

/**
 * Every number of the knight ruins in the Overworld (CLAUDE.md step 18; the user, November 19, 2026). The ones a
 * modpack maker may want to change are in the settings file ({@code [knight_ruins]} in {@code config/dross-common.toml},
 * saved per world: {@link com.reg21meme.dross.DrossWorldSettings}); these are the rest.
 */
public final class KnightRuinTuning
{
    // ---------------------------------------------------------------- reading the biome map

    /** The biome map is read every this many blocks (one sample a cell, at the land's own height). */
    public static final int CELL = 16;
    /** Cells in a tile of the map (read all at once, then kept): 64 x 64 cells, 1,024 blocks a side. */
    public static final int TILE = 64;
    /** The near-spawn plan reads the map this far past its radius (so patches near its edge are measured whole enough). */
    public static final int NEAR_WINDOW_PAD = 320;
    /** A region's plan reads the map this far round it. */
    public static final int REGION_MARGIN = 256;

    // ---------------------------------------------------------------- where a ruin may stand

    /** A "big" biome patch: at least this many square blocks (about 200 x 200). Near spawn only big ones count, and
     * further out a land ruin needs one too. */
    public static final int BIG_PATCH = 40_000;
    /** A ruin keeps at least this far in from its patch's edge (the user: "30 is fine")... */
    public static final int EDGE = 30;
    /** ... from its footprint's edge: a ruin is about 25 blocks across, so its middle keeps this much more. */
    public static final int HALF_RUIN = 13;
    /** A land ruin's ground may vary at most this much over its footprint (else the next best spot is tried). */
    public static final int MAX_RELIEF = 14;
    /** A land ruin's footprint may have at most this share of its samples under water. */
    public static final double MAX_WET = 0.2D;
    /** Its ground is the median of the land's heights every this many blocks over its footprint. */
    public static final int GROUND_STEP = 6;
    /** Spots tried in a patch before it gives up (the best first). */
    public static final int SPOT_TRIES = 6;
    /** Rare areas: a big patch of one always gets its ruin when a region holds one (the user: badlands, dark forest are
     * "much rarer" than plains and forests). */
    public static final Set<KnightRuinDesigns.Area> RARE = Set.of(KnightRuinDesigns.FIRE_BADLANDS, KnightRuinDesigns.POISON_DARK_FOREST,
            KnightRuinDesigns.POISON_MUSHROOM_FIELDS, KnightRuinDesigns.POISON_JUNGLE);
    /** On the peaks' highest biomes (jagged, frozen, stony), this share of the ruins are the storm's (thunder's Storm
     * Peaks layouts: the user, November 18: "in big mountain areas a chance of lightning layout"). Never near spawn. */
    public static final double STORM_PEAKS_SHARE = 0.3D;

    // ---------------------------------------------------------------- shores (thunder's, and ice's frozen shore)

    /** A shore ruin's back faces the water: the water is looked for this far out from its middle, every way. */
    public static final int SHORE_LOOK = 24;
    /** A shore ruin's land side (its front half) must be dry for this share of its samples, its sea side wet. */
    public static final double SHORE_SIDE_SHARE = 0.6D;

    // ---------------------------------------------------------------- the knights

    /** Its tidy-up (once: what grew into it felled, its blocks put back, {@link KnightRuinTidy}) when a player is within this
     * many blocks and its land is loaded: about when it comes into view, before the knights. */
    public static final int TIDY_RANGE = 256;
    /** Its two knights come when a player (not a spectator) is within this many blocks of it and its land is loaded. */
    public static final int KNIGHTS_COME = 112;
    /** Once both are beaten they come back (DrossConfig: 10 minutes) only while no player is within this many blocks
     * (the user: "if no one is within 64 blocks, that's fair"). */
    public static final int CLEAR_RANGE = 64;
    /** Every player within this many blocks when its second knight falls has cleared it (for the quest). */
    public static final int CREDIT_RANGE = 48;
    /** A knight keeps near its ruin: with nothing to fight it walks back once it's this far from its spot... */
    public static final double HOME_RETURN = 12.0D;
    /** ... and it gives up a foe this far from its spot. */
    public static final double LEASH = 36.0D;
    /** How often the keeper looks round (ticks). */
    public static final int KEEPER_EVERY = 40;
    /** Regions round each player are worked out in the background this far ahead (blocks), so world generation never waits. */
    public static final int WARM_AHEAD = 1600;
    /** How often the regions round the players are queued for working out (ticks). */
    public static final int WARM_EVERY = 100;

    private KnightRuinTuning() {}
}
