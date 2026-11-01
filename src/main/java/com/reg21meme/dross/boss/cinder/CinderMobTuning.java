package com.reg21meme.dross.boss.cinder;

/**
 * Every number for the Cinder Wastes' two regular mobs (CLAUDE.md, "The plan", step 16; the user's answers, October 31,
 * 2026): the Pallbearer and the Cinder Hermit. Speeds are Minecraft's movement-speed attribute (a walking mob covers
 * about 43 x speed² blocks a second: 0.2 is about 1.7); distances in blocks; times in ticks (20 a second); damage in
 * half hearts. How often they spawn is the biome's spawn list ({@code tools/terrain/worldgen.py}, the Cinder Wastes'
 * {@code SPAWN_OVERRIDES}); where they may is {@link CinderMobs}.
 */
public final class CinderMobTuning
{
    // ---------------------------------------------------------------- both: neutral

    /** Being angry: how long (seconds, picked at random in this range), like a Rimehorn's. The anger runs out even while
     *  it chases you. */
    public static final int ANGER_MIN_SECONDS = 20;
    public static final int ANGER_MAX_SECONDS = 40;
    /** It gives up a chase once it's this far from where the chase began, and walks back there. */
    public static final double CHASE_LEASH = 24.0D;
    /** Below this (blocks a second) it counts as standing still (its idle shows, not its walk). */
    public static final double MOVING = 0.15D;
    /** Its walk plays at most this much faster or slower than it was made (to keep its feet from sliding). */
    public static final double MIN_WALK_SPEEDUP = 0.4D;
    public static final double MAX_WALK_SPEEDUP = 2.6D;
    /** It may step up this high without jumping (they're giants: a block's ledge is a step to them). */
    public static final float STEP_HEIGHT = 1.1F;
    /** Coming into the world: natural spawning only makes room for a small box (ModEntities: their legs' spread and the
     *  Pallbearer's height don't count, or the ash's bumps would nearly always keep them out); then each is moved to the
     *  nearest spot within SPAWN_SHIFT blocks sideways and SPAWN_LIFT up where its whole body is clear and something is
     *  under its middle within SPAWN_GROUND_BELOW blocks; where there's none, a natural spawn doesn't happen (an egg's
     *  or a command's stays and walks out of the bumps: CinderMobEvents). */
    public static final int SPAWN_SHIFT = 3;
    public static final double SPAWN_LIFT = 1.5D;
    public static final double SPAWN_GROUND_BELOW = 1.6D;
    /** A dead one lies this many ticks after its death animation ends, then goes in a puff of smoke. */
    public static final int DEATH_LINGER_TICKS = 20;

    // ---------------------------------------------------------------- the Pallbearer

    public static final double PALLBEARER_HEALTH = 60.0D;
    public static final double PALLBEARER_ARMOR = 6.0D;
    /** Slow (the user: about 0.2): about 1.7 blocks a second at full stride. */
    public static final double PALLBEARER_SPEED = 0.2D;
    public static final double PALLBEARER_KNOCKBACK_RESISTANCE = 0.8D;
    public static final double PALLBEARER_FOLLOW_RANGE = 24.0D;
    /** Wandering (times its speed): a slow funeral march, about 0.85 blocks a second, near its walk's own pace. */
    public static final double PALLBEARER_WANDER_SPEED = 0.7D;
    /** Closing in on what it fights (times its speed): about 2.1 blocks a second. */
    public static final double PALLBEARER_CHASE_SPEED = 1.1D;
    /** Blocks a second its walk's feet cover as made ({@code tools/fire/firemobs/anims.json}, its walk's "speed"). */
    public static final double PALLBEARER_WALK_PACE = 1.008D;
    /** Blocks it walks between two footfalls (its stride: 26 units a foot). */
    public static final double PALLBEARER_STEP_LENGTH = 1.6D;
    /** While it walks, its funeral drum beats at most this often. */
    public static final int PALLBEARER_DRUM_TICKS = 140;
    public static final int PALLBEARER_XP = 15;
    /** How far its sounds carry (16 blocks times this): a giant. */
    public static final float PALLBEARER_VOLUME = 2.0F;

    /** The swipe: a backhand of its whole arm (its animation 1.7 s, the blow at 0.85 s), everything in an arc in front
     *  of it within reach of its middle; knocked away. */
    public static final float SWIPE_DAMAGE = 6.0F;
    public static final int SWIPE_TICKS = 34;
    public static final int SWIPE_HIT_TICK = 17;
    public static final int SWIPE_COOLDOWN = 20;
    public static final double SWIPE_REACH = 5.0D;
    /** The arc: this much of a half circle each side of straight ahead (cosine of the widest angle: 0 is 90 degrees). */
    public static final double SWIPE_ARC_COS = -0.17D;
    public static final double SWIPE_KNOCKBACK = 1.2D;
    public static final double SWIPE_KNOCKBACK_UP = 0.25D;
    /** It turns towards its target until this tick, then the blow is committed. */
    public static final int SWIPE_COMMIT_TICK = 11;

    /** The slam: the coffin heaved up and its weight brought down in front of it (its animation 2.2 s, the slam at
     *  1.2 s): everything within reach of where it lands, thrown up; a shockwave of ash. */
    public static final float SLAM_DAMAGE = 8.0F;
    public static final int SLAM_TICKS = 44;
    public static final int SLAM_HIT_TICK = 24;
    public static final int SLAM_COMMIT_TICK = 16;
    public static final int SLAM_COOLDOWN = 160;
    /** Where it lands: this far in front of its feet; what's this close to there (and no more than SLAM_HEIGHT above
     *  the ground there) is hit. */
    public static final double SLAM_FORWARD = 2.5D;
    public static final double SLAM_RADIUS = 3.2D;
    public static final double SLAM_HEIGHT = 2.5D;
    public static final double SLAM_THROW_UP = 0.75D;
    public static final double SLAM_THROW_OUT = 0.6D;
    /** It slams (rather than swipes) this often when both are ready and its target is in reach; always with two or more
     *  foes round it. */
    public static final float SLAM_CHANCE = 0.4F;
    /** A target this close to its middle is in the slam's reach. */
    public static final double SLAM_START_REACH = 5.0D;

    /** The roar, when it gets angry (its animation 2.6 s, the cry at 0.45 s): Slowness on the players round it. */
    public static final int ROAR_TICKS = 52;
    public static final int ROAR_HIT_TICK = 9;
    public static final double ROAR_RANGE = 10.0D;
    public static final int ROAR_SLOW_TICKS = 100;
    /** Slowness II (amplifier 1). */
    public static final int ROAR_SLOW_LEVEL = 1;

    /** Natural spawning: open air this many blocks up over where it stands (it's 9.6 tall), and never this close to
     *  another Pallbearer. */
    public static final int PALLBEARER_HEADROOM = 10;
    public static final double PALLBEARER_SPACING = 64.0D;

    // ---------------------------------------------------------------- the Cinder Hermit

    public static final double HERMIT_HEALTH = 30.0D;
    public static final double HERMIT_ARMOR = 8.0D;
    public static final double HERMIT_SPEED = 0.21D;
    public static final double HERMIT_KNOCKBACK_RESISTANCE = 0.5D;
    public static final double HERMIT_FOLLOW_RANGE = 16.0D;
    /** Wandering, and closing in (times its speed): about 1.2 and 2.5 blocks a second. */
    public static final double HERMIT_WANDER_SPEED = 0.8D;
    public static final double HERMIT_CHASE_SPEED = 1.15D;
    /** Blocks a second its walk's feet cover as made ({@code tools/fire/firemobs/anims.json}). */
    public static final double HERMIT_WALK_PACE = 1.818D;
    public static final double HERMIT_STEP_LENGTH = 0.9D;
    public static final int HERMIT_XP = 5;
    public static final float HERMIT_VOLUME = 1.2F;
    /** Hurt, the other hermits within this many blocks come for whoever did it (like a Rimehorn's pack). */
    public static final double HERMIT_CALL_RANGE = 12.0D;

    /** The snap: its big magma claw raised and slammed shut (its animation 1.3 s, the snap at 0.62 s). */
    public static final float SNAP_DAMAGE = 5.0F;
    public static final int SNAP_TICKS = 26;
    public static final int SNAP_HIT_TICK = 12;
    public static final int SNAP_COMMIT_TICK = 8;
    public static final int SNAP_COOLDOWN = 24;
    public static final double SNAP_KNOCKBACK = 0.8D;
    /** The pinch: two quick nips of its small claw (its animation 1.1 s, the nips at 0.35 and 0.7 s), half each. */
    public static final float PINCH_DAMAGE = 3.0F;
    public static final int PINCH_TICKS = 22;
    public static final int PINCH_HIT_TICK = 7;
    public static final int PINCH_HIT2_TICK = 14;
    public static final int PINCH_COOLDOWN = 12;
    /** How far its claws reach, from its middle (its claws stick out about 6 blocks in front of its feet), and the arc
     *  in front (cosine of the widest angle). */
    public static final double CLAW_REACH = 5.6D;
    public static final double CLAW_ARC_COS = 0.2D;
    /** It snaps (rather than pinches) this often when both are ready. */
    public static final float SNAP_CHANCE = 0.55F;

    /** Hiding: hurt, it pulls into its shell this often (more as it weakens: up to HIDE_CHANCE + HIDE_CHANCE_HURT at
     *  no health), then waits before it may hide again. */
    public static final float HIDE_CHANCE = 0.3F;
    public static final float HIDE_CHANCE_HURT = 0.4F;
    public static final int HIDE_COOLDOWN = 200;
    /** In its shell it takes this share of the damage (80% less), and it can't be knocked back. */
    public static final float HIDDEN_DAMAGE_TAKEN = 0.2F;
    /** Its hide animation (1.2 s), then hidden for 2-3 s, then it peeks (its eyes out: the whole look round, 4.1 s, or
     *  just a glance when it's angry), then comes out (2.45 s; out of its shell at 1.4 s). */
    public static final int HIDE_TICKS = 24;
    public static final int HIDDEN_MIN_TICKS = 40;
    public static final int HIDDEN_MAX_TICKS = 60;
    public static final int PEEK_TICKS = 82;
    public static final int PEEK_ANGRY_TICKS = 34;
    public static final int COME_OUT_TICKS = 49;
    public static final int COME_OUT_FREE_TICK = 28;

    /** Natural spawning: never more than this many hermits within HERMIT_CROWD_RANGE blocks. */
    public static final int HERMIT_CROWD = 4;
    public static final double HERMIT_CROWD_RANGE = 48.0D;

    /** Its claws' own hitbox in front of its body (its body's is a square round its middle; the crab reaches about 6
     *  blocks in front of its feet): its spot (its left +x, up, its front -z) and size; tucked into its shell while it
     *  hides. */
    public static final double[][] HERMIT_CLAW_SPOT = {{0.0D, 0.0D, -4.6D}};
    public static final double[][] HERMIT_CLAW_SPOT_HIDDEN = {{0.0D, 0.0D, -1.0D}};
    public static final float HERMIT_CLAW_WIDTH = 3.0F;
    public static final float HERMIT_CLAW_HEIGHT = 2.6F;

    private CinderMobTuning() {}
}
