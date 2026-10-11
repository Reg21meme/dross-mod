package com.reg21meme.dross.boss.golem;

/**
 * Every tuning number of the Cinder Colossus fight in one place (the numbers that change per stage are in
 * {@link ColossusStage}). Times are in ticks (20 ticks = 1 second), distances in blocks, damage in half-hearts
 * (a player has 20). Damage to players is also scaled by the difficulty, like every mob's (Easy less, Hard more).
 */
public final class ColossusTuning
{
    // ---------------------------------------------------------------- health and stages

    /** Its health (a player has 20). */
    public static final float MAX_HEALTH = 300.0F;
    /** The Cracking stage starts at this share of its health... */
    public static final float CRACKING_AT = 0.60F;
    /** ...and the Lava Core stage at this share. A big hit never skips a stage: the damage stops at the threshold. */
    public static final float LAVA_CORE_AT = 0.35F;
    /** It can't be knocked back at all (1 = fully resistant). */
    public static final double KNOCKBACK_RESISTANCE = 1.0D;
    /** How far it notices and follows players (it must see them first). */
    public static final double FOLLOW_RANGE = 40.0D;
    /** The highest step it walks up without jumping. */
    public static final float STEP_HEIGHT = 1.0F;
    /** Experience it drops when it dies. */
    public static final int XP_REWARD = 50;
    /** Its boss bar shows for players within this many blocks, and the defeat advancement goes to them too. */
    public static final double BOSS_BAR_RANGE = 40.0D;
    /** In the Lava Core stage every hit sets you on fire for this many seconds. */
    public static final int CORE_FIRE_SECONDS = 5;
    /** Ticks it waits between choosing attacks when it can't use any yet (out of range, all on cooldown). */
    public static final int RETHINK_TICKS = 5;

    // ---------------------------------------------------------------- swipe (its melee hit)

    /** Damage of a swipe (times the stage's damage scale). */
    public static final float SWIPE_DAMAGE = 12.0F;
    /** How far past the edge of its body the swipe reaches. */
    public static final double SWIPE_REACH = 3.0D;
    /** The swipe hits everything within this many degrees either side of straight ahead. */
    public static final float SWIPE_HALF_ARC = 80.0F;
    /** How hard a swipe knocks you back (sideways and up). */
    public static final double SWIPE_KNOCKBACK = 1.0D;
    public static final double SWIPE_LIFT = 0.35D;
    /** Ticks before it can swipe again. */
    public static final int SWIPE_COOLDOWN = 25;

    // ---------------------------------------------------------------- thrown cobblestone

    /** Damage of a thrown rock that hits you (times the stage's damage scale). It never places a block. */
    public static final float ROCK_DAMAGE = 9.0F;
    /** How fast it flies (sideways, blocks a tick) and how fast it falls (blocks a tick, each tick). */
    public static final double ROCK_SPEED = 0.9D;
    public static final double ROCK_GRAVITY = 0.045D;
    /** How hard a rock knocks you back. */
    public static final double ROCK_KNOCKBACK = 0.8D;
    /** It throws at players between these distances... */
    public static final double THROW_MIN_RANGE = 6.0D;
    public static final double THROW_MAX_RANGE = 28.0D;
    /** ...at most this often (ticks). */
    public static final int THROW_COOLDOWN = 110;

    // ---------------------------------------------------------------- ground slam

    /** The shockwave reaches this far from where its fists land... */
    public static final double SLAM_RADIUS = 6.5D;
    /** ...does very little damage (times the stage's damage scale)... */
    public static final float SLAM_DAMAGE = 2.0F;
    /** ...but throws you far: this fast sideways at the middle (a little over half that at the edge), and this fast up. */
    public static final double SLAM_KNOCKBACK = 1.8D;
    public static final double SLAM_LIFT = 0.75D;
    /** It slams when a player is this close to it (from its middle)... */
    public static final double SLAM_TRIGGER_RANGE = 7.0D;
    /** ...at most this often (ticks). */
    public static final int SLAM_COOLDOWN = 200;

    // ---------------------------------------------------------------- charge

    /** How far it gallops in a straight line, head first... */
    public static final double CHARGE_DISTANCE = 10.0D;
    /** ...and how fast (blocks a tick: 0.6 is 12 blocks a second). */
    public static final double CHARGE_SPEED = 0.6D;
    /** It gives up a charge after this many ticks (if something slows it down). */
    public static final int CHARGE_MAX_TICKS = 30;
    /** Damage to everything it runs into (times the stage's damage scale), and how hard it knocks them aside and up. */
    public static final float CHARGE_DAMAGE = 11.0F;
    public static final double CHARGE_KNOCKBACK = 1.6D;
    public static final double CHARGE_LIFT = 0.55D;
    /** It charges at players between these distances... */
    public static final double CHARGE_MIN_RANGE = 7.0D;
    public static final double CHARGE_MAX_RANGE = 18.0D;
    /** ...at most this often (ticks). */
    public static final int CHARGE_COOLDOWN = 180;
    /** If it runs into a wall it's dazed for this many ticks. */
    public static final int CHARGE_DAZED_TICKS = 25;
    /** Lava Core stage: the trail of fire its charge leaves lasts between these many ticks (4 to 5 seconds). */
    public static final int FIRE_TRAIL_MIN_TICKS = 80;
    public static final int FIRE_TRAIL_MAX_TICKS = 100;

    // ---------------------------------------------------------------- volcano lob (Cracking and Lava Core)

    /** How many lava blobs its volcanoes spit out at once. */
    public static final int LOB_BLOBS = 2;
    /** Each lands this far from the player (near them, never on them). */
    public static final double LOB_LAND_MIN = 4.0D;
    public static final double LOB_LAND_MAX = 6.0D;
    /** Ticks a blob is in the air, and how fast it falls (blocks a tick, each tick): high arcs. */
    public static final int LOB_FLIGHT_TICKS = 32;
    public static final double LOB_GRAVITY = 0.06D;
    /** Damage of a blob that lands right on someone (it sets them on fire too). */
    public static final float LOB_DIRECT_DAMAGE = 6.0F;
    /** It lobs at players between these distances... */
    public static final double LOB_MIN_RANGE = 6.0D;
    public static final double LOB_MAX_RANGE = 32.0D;
    /** ...at most this often (ticks). */
    public static final int LOB_COOLDOWN = 240;

    // ---------------------------------------------------------------- lava pools (where a blob lands)

    /** A pool reaches at most this far from its middle block in any direction (a rough circle). */
    public static final double POOL_RADIUS = 3.0D;
    /** It lasts between these many ticks (20 to 40 seconds), then cools and is gone (its edge goes first). */
    public static final int POOL_MIN_TICKS = 400;
    public static final int POOL_MAX_TICKS = 800;

    // ---------------------------------------------------------------- summoning (Cracking and Lava Core)

    /** Minions rise this far from its middle: one in front of it, one to its left, one to its right. */
    public static final double SUMMON_DISTANCE = 4.5D;
    /** It only summons when fewer than this many of its minions are alive... */
    public static final int SUMMON_BELOW = 2;
    /** ...and never has more than this many alive at once. */
    public static final int MAX_MINIONS = 6;
    /** It summons when its target is within this distance... */
    public static final double SUMMON_RANGE = 24.0D;
    /** ...at most this often (ticks). */
    public static final int SUMMON_COOLDOWN = 500;

    // ---------------------------------------------------------------- stone golems (Cracking stage)

    public static final float STONE_GOLEM_HEALTH = 12.0F;
    public static final float STONE_GOLEM_DAMAGE = 3.0F;
    /** 15% faster than the first version (0.23), at the user's request. */
    public static final double STONE_GOLEM_SPEED = 0.2645D;
    public static final double STONE_GOLEM_ARMOR = 4.0D;

    // ---------------------------------------------------------------- lava golems (Lava Core stage)

    public static final float LAVA_GOLEM_HEALTH = 8.0F;
    public static final float LAVA_GOLEM_DAMAGE = 2.0F;
    /** 15% faster than the first version (0.28), at the user's request. */
    public static final double LAVA_GOLEM_SPEED = 0.322D;
    /** Its hits set you on fire for this many seconds. */
    public static final int LAVA_GOLEM_FIRE_SECONDS = 4;
    /** If nobody kills it, it blows up this many ticks after it climbs out of the ground... */
    public static final int LAVA_GOLEM_FUSE_TICKS = 140;
    /** ...swelling and hissing for the last this-many ticks... */
    public static final int LAVA_GOLEM_SWELL_TICKS = 30;
    /** ...hurting everything within this distance (most at the middle) and setting it on fire. It breaks no blocks. */
    public static final double LAVA_GOLEM_BLAST_RADIUS = 3.0D;
    public static final float LAVA_GOLEM_BLAST_DAMAGE = 7.0F;
    public static final int LAVA_GOLEM_BLAST_FIRE_SECONDS = 4;

    // ---------------------------------------------------------------- minions (both kinds)

    /** Minions check this often (ticks) that their Colossus is still alive and near; if not, they crumble. */
    public static final int MINION_MASTER_CHECK = 20;
    /** How far from their Colossus minions still count as its own (for the summon limit and crumbling). */
    public static final double MINION_RANGE = 64.0D;

    // ---------------------------------------------------------------- death

    /** After it has cooled into an obsidian statue it stands this many ticks (2 minutes), then crumbles away. */
    public static final int STATUE_TICKS = 2400;

    // ---------------------------------------------------------------- the reward

    /**
     * The trophy design every player who helped beat it gets (1 to 5, see boss/trophy/ColossusTrophies): design 1,
     * Volcano's Wrath. Any design is fine: players right-click a placed trophy to turn it to any of the five poses.
     */
    public static final int DROP_DESIGN = 1;

    private ColossusTuning() {}
}
