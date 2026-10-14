package com.reg21meme.dross.boss.golem;

import java.util.List;

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
    /** It throws at players between these distances (in the Shell and Cracking stages: never in the Lava Core)... */
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

    // ---------------------------------------------------------------- charge (it closes the gap)

    /**
     * It charges at the spot where its target stood when the charge started (the target may have moved by the time it
     * gallops) and runs all the way to that spot, head first, at most this far...
     */
    public static final double CHARGE_MAX_DISTANCE = 32.0D;
    /** ...this fast (blocks a tick: 0.6 is 12 blocks a second). */
    public static final double CHARGE_SPEED = 0.6D;
    /** It gives up a charge this many ticks after it should have arrived (if something slows it down). */
    public static final int CHARGE_SLACK_TICKS = 15;
    /** Damage to everything it runs into (times the stage's damage scale), and how hard it knocks them aside and up. */
    public static final float CHARGE_DAMAGE = 11.0F;
    public static final double CHARGE_KNOCKBACK = 1.6D;
    public static final double CHARGE_LIFT = 0.55D;
    /** It charges at targets between these distances (it must see them, with a clear-ish run, see below)... */
    public static final double CHARGE_MIN_RANGE = 7.0D;
    public static final double CHARGE_MAX_RANGE = 32.0D;
    /** ...not more than this many blocks above or below it... */
    public static final double CHARGE_MAX_RISE = 2.5D;
    /**
     * ...and only when nothing solid stands in this share of the way (the start of the run): a clear-ish run. A wall
     * further on still stops it (and dazes it).
     */
    public static final double CHARGE_CLEAR_SHARE = 0.75D;
    /**
     * It charges at most this often (ticks) in each stage: 9, 6 and 4 seconds. (Not scaled by the stage's cooldown
     * multiplier, like the other moves are.)
     */
    public static final int CHARGE_COOLDOWN_SHELL = 180;
    public static final int CHARGE_COOLDOWN_CRACKING = 120;
    public static final int CHARGE_COOLDOWN_CORE = 80;
    /** If it runs into a wall it's dazed for this many ticks. */
    public static final int CHARGE_DAZED_TICKS = 25;
    /**
     * Every charge in the Cracking and Lava Core stages leaves a trail of fire behind it. In the Lava Core it lasts
     * between these many ticks (4 to 5 seconds)...
     */
    public static final int FIRE_TRAIL_MIN_TICKS = 80;
    public static final int FIRE_TRAIL_MAX_TICKS = 100;
    /** ...and in the Cracking stage half as long (2 to 2.5 seconds). */
    public static final int CRACKING_FIRE_MIN_TICKS = 40;
    public static final int CRACKING_FIRE_MAX_TICKS = 50;

    // ---------------------------------------------------------------- volcano lob (Cracking and Lava Core)

    /** How many lava blobs its volcanoes spit out at once. */
    public static final int LOB_BLOBS = 2;
    /**
     * When it bursts out of its shell (entering the Lava Core stage, after its roar) its volcanoes spew this many blobs
     * at once, landing round the players near it (shared out between them), never on them.
     */
    public static final int STAGE_CHANGE_LOB_BLOBS = 4;
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
    /** A summon raises this many minions (one at each of its three pounds), unless risen undead are near (below). */
    public static final int SUMMON_COUNT = 3;
    /**
     * When its eruption has finished (entering the Cracking stage, after its roar) this many stone golems climb out of
     * the ground at once, all round it (in front, to its right, behind, to its left), never past {@link #MAX_MINIONS}.
     */
    public static final int STAGE_CHANGE_MINIONS = 4;
    /** It only summons when fewer than this many of its minions are alive... */
    public static final int SUMMON_BELOW = 2;
    /** ...and never has more than this many alive at once. */
    public static final int MAX_MINIONS = 6;
    /** It summons when its target is within this distance... */
    public static final double SUMMON_RANGE = 24.0D;
    /** ...at most this often (ticks). */
    public static final int SUMMON_COOLDOWN = 500;

    // ---------------------------------------------------------------- the roar

    /**
     * It roars, with a shockwave of dust, the first time it's hurt in a fight (once per Colossus) and at both stage
     * changes (at the roar in the eruption and in the shell bursting). Every player within this many blocks of it is
     * thrown back...
     */
    public static final double ROAR_RADIUS = 15.0D;
    /**
     * ...this fast sideways (away from it) and this fast up: about 15 blocks on flat ground (less in armour that resists
     * knockback). Knockback only: no damage, and only players (its minions and other mobs stay put).
     */
    public static final double ROAR_KNOCKBACK = 1.6D;
    public static final double ROAR_LIFT = 0.75D;
    /** Ticks its ring of dust takes to roll out to {@link #ROAR_RADIUS}. */
    public static final int ROAR_RING_TICKS = 6;

    // ---------------------------------------------------------------- choosing a move: chances by distance

    /**
     * How likely each move is (percent), for one band of distance to its target. Only the moves it can make right now
     * count (ready after their cooldown, in range, in sight...): the others' share is spread over them (a 60 / 40 pair
     * with the 60 on cooldown is a sure 40). With none it walks closer.
     */
    public record Chances(int swipe, int throwRock, int slam, int charge, int lob, int summon) {}

    /**
     * The distance bands (blocks from its middle to its target's): close (its swipe and slam range) up to this...
     */
    public static final double CLOSE_BAND = 7.0D;
    /** ...mid up to this, far up to this, and very far up to {@link #CHARGE_MAX_RANGE} (beyond it, it walks closer). */
    public static final double MID_BAND = 18.0D;
    public static final double FAR_BAND = 28.0D;

    /** The Shell stage: close, mid, far, very far. */
    public static final List<Chances> SHELL_CHANCES = List.of(
            //          swipe  throw  slam  charge  lob  summon
            new Chances(60,    0,     40,   0,      0,   0),
            new Chances(0,     50,    0,    50,     0,   0),
            new Chances(0,     60,    0,    40,     0,   0),
            new Chances(0,     0,     0,    100,    0,   0));
    /** The Cracking stage: close, mid, far, very far. */
    public static final List<Chances> CRACKING_CHANCES = List.of(
            //          swipe  throw  slam  charge  lob  summon
            new Chances(40,    0,     25,   0,      0,   35),
            new Chances(0,     20,    0,    35,     25,  20),
            new Chances(0,     15,    0,    45,     25,  15),
            new Chances(0,     0,     0,    70,     30,  0));
    /** The Lava Core stage: close, mid, far, very far. No rock throw at all any more. */
    public static final List<Chances> CORE_CHANCES = List.of(
            //          swipe  throw  slam  charge  lob  summon
            new Chances(40,    0,     30,   0,      0,   30),
            new Chances(0,     0,     0,    50,     35,  15),
            new Chances(0,     0,     0,    60,     40,  0),
            new Chances(0,     0,     0,    70,     30,  0));
    /**
     * Chosen from a little out of its swipe's reach (still in the close band), the swipe makes it step in first: for
     * at most this many ticks, then it thinks again.
     */
    public static final int SWIPE_STEP_IN_TICKS = 30;

    // ---------------------------------------------------------------- against Necromancy's risen undead

    /** Risen undead (a player's, raised by Necromancy) within this many blocks of it count... */
    public static final double RISEN_RANGE = 24.0D;
    /**
     * ...and then a summon raises one golem for every this many of them (half, rounded up; never past
     * {@link #MAX_MINIONS} alive), and the golems go for the risen undead first. While they're near it may summon in
     * every stage (the Shell too) whenever it has fewer golems than half the risen undead.
     */
    public static final int RISEN_PER_GOLEM = 2;
    /** The summon's chance (percent) in a band whose table has none (the Shell; the far bands) while it may summon. */
    public static final int RISEN_SUMMON_CHANCE = 35;
    /** When at least this many risen undead are attacking it within its slam range ({@link #SLAM_TRIGGER_RANGE})... */
    public static final int SWARM_ATTACKERS = 3;
    /** ...this many points of the close band's chance move from the swipe to the slam (back when there are fewer). */
    public static final int SWARM_SLAM_SHIFT = 20;

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
    /**
     * If nobody kills it, a summoned one blows up this many ticks after it climbs out of the ground (a wild one waits
     * for a player instead: {@link #WILD_LAVA_GOLEM_FUSE_RANGE})...
     */
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

    // ---------------------------------------------------------------- wild golems (the Cinder Wastes)
    // Stone and lava golems also spawn by themselves in the Cinder Wastes (the biome's spawn list says which and how
    // often; the spawn rules are in WildGolemSpawns). A wild golem has no Colossus: it never crumbles, despawns like
    // any monster when no player is near, and its numbers (health, damage, speed) are the summoned ones above.

    /**
     * A lava golem only spawns by itself near lava: a block of lava at most this many blocks away sideways (a 13 x 13
     * square round the spot)...
     */
    public static final int WILD_LAVA_SEARCH_SIDEWAYS = 6;
    /** ...and at most this many blocks above or below the spot. */
    public static final int WILD_LAVA_SEARCH_UP_DOWN = 3;
    /**
     * A wild lava golem has no timer: its fuse is lit (creeper-like) when its target is this close (blocks, middle to
     * middle) and in sight. Then it swells and hisses for {@link #LAVA_GOLEM_SWELL_TICKS} ticks and blows up like a
     * summoned one; once lit, it can't be put out (only killed).
     */
    public static final double WILD_LAVA_GOLEM_FUSE_RANGE = 2.5D;
    /** A wild golem with nothing to fight wanders about at this share of its speed (1 = its full speed). */
    public static final double WILD_GOLEM_WANDER_SPEED = 1.0D;

    // ---------------------------------------------------------------- the lava golem top-up (WildLavaGolemTopUp)
    // Lava is rare in the Cinder Wastes, so natural spawning alone makes far fewer lava golems than stone golems. The
    // top-up adds wild lava golems next to lava near players in the Cinder Wastes until they are about this share.

    /** Lava golems should be about this share of the wild golems near a player (0.2 = 1 in 5; the nearest whole number). */
    public static final double WILD_LAVA_GOLEM_SHARE = 0.2D;
    /** It checks every player this often (ticks: 5 seconds) and adds at most one lava golem per player each time. */
    public static final int WILD_TOP_UP_INTERVAL = 100;
    /** It counts the wild stone and lava golems within this many blocks of the player. */
    public static final double WILD_TOP_UP_COUNT_RANGE = 64.0D;
    /** It looks for lava at most this many blocks from the player sideways... */
    public static final int WILD_TOP_UP_LAVA_RANGE = 48;
    /** ...and this many blocks above or below the player. No lava there: nothing is added. */
    public static final int WILD_TOP_UP_LAVA_UP_DOWN = 24;
    /** Never closer than this to any player (blocks; Minecraft's own rule for natural spawning). */
    public static final double WILD_TOP_UP_PLAYER_GAP = 24.0D;
    /**
     * A spot is at most this many blocks sideways from its lava block (well inside {@link #WILD_LAVA_SEARCH_SIDEWAYS},
     * the lava golem's own spawn rule, which every spot must still pass). Lava blocks this close to one already tried
     * count as the same pool and are skipped.
     */
    public static final int WILD_TOP_UP_SPOT_SPREAD = 4;
    /** It tries at most this many pools (nearest first)... */
    public static final int WILD_TOP_UP_LAVA_TRIES = 8;
    /** ...and this many random spots round each. */
    public static final int WILD_TOP_UP_SPOTS_PER_LAVA = 4;

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
