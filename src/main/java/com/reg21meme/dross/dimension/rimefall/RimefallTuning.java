package com.reg21meme.dross.dimension.rimefall;

/**
 * Every number for Rimefall's big lakes, its Rimehorn herds and where its Pond Skaters may spawn (CLAUDE.md, "The plan",
 * step 14; the user's requests of October 19, 2026). Distances in blocks.
 */
public final class RimefallTuning
{
    // ---------------------------------------------------------------- the big lake
    // The user: "add a large ice lake for the biome, guaranteed", "each rimefall should have 1 big lake", "like 100
    // blocks across", with open water holes. One per Rimefall patch (the boss-site planner's patches), in its flattest
    // roomy spot.

    /** Its middle radius (the edge wobbles round it): about 100 blocks across. */
    public static final double LAKE_RADIUS = 50.0D;
    /** A patch with no room (or no flat enough ground) for that gets a smaller lake: about 64 across. */
    public static final double LAKE_SMALL_RADIUS = 32.0D;
    /** How much the edge wobbles: the shares of the radius its 2-, 3- and 5-lobed waves add (a lake, not a circle). */
    public static final double LAKE_WOBBLE_2 = 0.10D;
    public static final double LAKE_WOBBLE_3 = 0.07D;
    public static final double LAKE_WOBBLE_5 = 0.04D;
    /** Round the water the ground blends from the ice's level back to the land's over this many blocks. */
    public static final int SHORE_WIDTH = 16;
    /** ...and the first this many are at least as high as the ice (the wall that holds the water in); past them a dip next
     * to the lake is filled in steps of at most one block. */
    public static final int SHORE_FLAT = 3;
    /** The lake and its shore stay at least this far inside the patch (so the shore is Rimefall too). */
    public static final double EDGE_MARGIN = 8.0D;
    /** The most ground the lake may cut away anywhere in it (flatter spots are picked first). */
    public static final int MAX_CUT = 12;
    /** The most a low spot right beside the water may need raising (a taller wall of fill looks wrong). */
    public static final int MAX_DAM = 4;
    /** A patch with no spot within those two (rough ground) still gets the small lake, cutting and filling up to these:
     * every patch with room for a lake has one (the user: "guaranteed"). */
    public static final int LAST_RESORT_MAX_CUT = 24;
    public static final int LAST_RESORT_MAX_DAM = 8;
    /** Water under the ice: blocks of it at the edge and in the middle. */
    public static final int DEPTH_EDGE = 2;
    public static final int DEPTH_MIDDLE = 7;
    /** The ice lies at this share of the ground's heights inside it (low: mostly cut down, little filled up). */
    public static final double ICE_LEVEL_SHARE = 0.25D;
    /** The fit test samples the ground this far apart, at up to this many spots (the patch's deepest, this far apart). */
    public static final int SAMPLE_STEP = 6;
    public static final int SITE_TRIES = 48;
    public static final int SITE_TRY_SPACING = 24;
    /** The lake bed: gravel, with this share in patches of clay. */
    public static final double BED_CLAY_SHARE = 0.25D;

    // ---------------------------------------------------------------- its open water holes
    // The user's pick: "a few holes in the ice showing dark water, which you can fall into". They never freeze over (their
    // top is water in an invisible light block of level 0, which Minecraft doesn't freeze).

    public static final int HOLES_MIN = 4;
    public static final int HOLES_MAX = 6;
    public static final double HOLE_RADIUS_MIN = 1.6D;
    public static final double HOLE_RADIUS_MAX = 3.4D;
    /** Holes lie within this share of the radius from the middle, at least this far apart (edge to edge). */
    public static final double HOLES_REACH = 0.7D;
    public static final double HOLE_SPACING = 10.0D;
    /** At least this much water under a hole. */
    public static final int HOLE_MIN_DEPTH = 3;

    /** No feature (trees, boulders, ponds, plants, snow drifts) starts within this many blocks of the lake's edge
     * ({@code dross:not_on_rimefall_lake}), so nothing grows on the ice or hangs over it. */
    public static final double FEATURE_CLEARANCE = 6.0D;

    // ---------------------------------------------------------------- the Rimehorn herds
    // The user: herds "a few per area", then (October 19) "only 2-3 per biome, 2 being 75% and 3 being 25% ... smaller
    // ones should have 1 or 2 max". They're placed as the land is made, at spots spread out over the patch, away from its
    // lake; Rimehorns don't spawn on their own afterwards (a herd that's gone stays gone, but they breed).

    /** Herds in a patch (in a small one, under this many blocks²: one fewer), and the chance of one more (a roll fixed by the seed). */
    public static final int HERDS = 2;
    public static final int HERDS_SMALL_AREA = 120_000;
    public static final double HERD_EXTRA_CHANCE = 0.25D;
    /** Herds are at least this far apart, and this far past the lake's shore. */
    public static final int HERD_SPACING = 96;
    public static final int HERD_TRIES = 64;
    public static final double HERD_LAKE_CLEARANCE = 24.0D;
    /** A patch so small that its lake leaves no spot that far out still gets one herd: the spot furthest from the lake,
     * at least this far past its shore (and these many candidate spots, this far apart, are looked at for it). */
    public static final double HERD_LAKE_CLEARANCE_LAST = 6.0D;
    public static final int HERD_LAST_TRIES = 256;
    public static final int HERD_LAST_SPACING = 12;
    /** A herd's size (the user's 6-8: its first 2 males, the rest females) and how far round its spot they stand. */
    public static final int HERD_MIN = 6;
    public static final int HERD_MAX = 8;
    public static final int HERD_SPREAD = 6;
    /** Tries at a free spot for each Rimehorn round its herd's spot, then anywhere in the herd's chunk. */
    public static final int HERD_NEAR_TRIES = 15;
    public static final int HERD_CHUNK_TRIES = 40;

    // ---------------------------------------------------------------- Pond Skaters
    // The user: a big lake holds 2 at most; a frozen pond at least ~30 blocks across, 1; anything smaller none ("one
    // that's 10 blocks shouldn't even get one"; they saw 10 on one small pond). Then (October 19): "don't force 2 to
    // always be in a large lake, just make it so 2 could possibly spawn", and "a little more common": any patch of ice
    // 20 blocks across or more may have 1 ("if I'm not at a large lake but a decent sized patch of ice"). Then (October
    // 20, after seeing 7 on one pond): "no more than ... maybe 3 can spawn in an area, and the big lake in every biome has
    // the max of 2", and "don't make them instantly spawn in, they should have a random spawn rate". (The 7 came from
    // skaters wandering off their pond, where it no longer counted them, and from a pond's ice at several heights, each
    // counted as its own pond.)

    /** Never more than this many within {@link #SKATER_AREA_RADIUS} blocks (sideways; {@link #SKATER_AREA_HEIGHT} up and
     * down) of where one would spawn, whatever the ice. */
    public static final int SKATERS_PER_AREA = 3;
    public static final double SKATER_AREA_RADIUS = 48.0D;
    public static final double SKATER_AREA_HEIGHT = 24.0D;
    /** A skater this far off its lake or patch (wandered onto the shore, or on a step of the same ice) still counts for it. */
    public static final double SKATER_WANDER_MARGIN = 16.0D;
    /** A natural spawn that may happen happens only this often (so they turn up now and then, not the moment there's room). */
    public static final double SKATER_SPAWN_CHANCE = 0.15D;

    /** A big lake holds this many at most, or one more with this chance (a roll per lake, fixed by its seed). */
    public static final int SKATERS_PER_LAKE = 1;
    public static final double LAKE_SECOND_SKATER_CHANCE = 0.5D;
    /** Any other patch of ice (frozen pond or not) this many blocks across or more holds this many at most; smaller, none. */
    public static final int SKATERS_PER_PATCH = 1;
    public static final int PATCH_MIN_ACROSS = 20;
    /** The most ice a patch's measuring follows (a bigger one counts as big enough). */
    public static final int PATCH_SEARCH_LIMIT = 4000;

    private RimefallTuning() {}
}
