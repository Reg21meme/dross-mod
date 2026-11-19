package com.reg21meme.dross.boss.knight;

/**
 * Every number of the elemental knights' fights (boss/knight; their health, armour, speed and when they use their moves
 * are in {@link KnightKind}). Damage is in half hearts (2 = a heart), distances in blocks, times in ticks (20 a second).
 */
public final class KnightTuning
{
    // ---------------------------------------------------------------- all of them

    /** How far they notice a foe (blocks). */
    public static final double FOLLOW_RANGE = 40.0D;
    /** Experience each one gives. */
    public static final int XP = 30;
    /** The pause after a move before the next (ticks). */
    public static final int PAUSE = 12;
    /** Hurt, it calls the other knights this close to help. */
    public static final double HELP_RANGE = 32.0D;
    /** Its plain melee hit when both moves cool down and its foe is right by it (a punch, a shove). */
    public static final float PLAIN_HIT = 5.0F;
    public static final int PLAIN_HIT_COOLDOWN = 30;
    public static final double PLAIN_HIT_REACH = 3.0D;

    // ---------------------------------------------------------------- 9C, the Thawed axeman

    /** The axe's swing: a wide arc in front of him. */
    public static final float AXE_SWING_DAMAGE = 9.0F;
    public static final double AXE_SWING_REACH = 4.0D;
    public static final double AXE_SWING_ARC = 180.0D;   // a flat sweep (November 18, 2026)
    public static final double AXE_SWING_KNOCK = 0.8D;
    /** The swing chills: Slowness I this long. */
    public static final int AXE_SWING_CHILL = 40;
    /** The smash: the axe into the ground, and the ice bursts up in a line from it to his foe (one small spike every
     * {@link #SMASH_STEP} blocks, one a tick), then a big spike under where his foe stood (the Alpha Moose's ice). */
    public static final double SMASH_STEP = 1.3D;
    public static final double SMASH_LINE_MAX = 12.0D;
    /** Anything this close to a small spike as it bursts up takes this, and is chilled. */
    public static final double SMASH_SMALL_HIT = 1.4D;
    public static final float SMASH_SMALL_DAMAGE = 5.0F;
    /** The big spike: this tall, this close counts, this much damage, thrown up this hard, Slowness II and frost. */
    public static final float SMASH_BIG_HEIGHT = 2.2F;
    public static final double SMASH_BIG_HIT = 1.6D;
    public static final float SMASH_BIG_DAMAGE = 9.0F;
    public static final double SMASH_BIG_LAUNCH = 0.85D;
    public static final int SMASH_BIG_SLOW = 60;
    public static final int SMASH_FROST = 140;
    /** The spikes stand this long, then go. */
    public static final int SPIKE_LIFE = 50;
    /** The small spikes' size (blocks tall). */
    public static final float SMASH_SMALL_MIN = 0.55F;
    public static final float SMASH_SMALL_MAX = 1.0F;

    // ---------------------------------------------------------------- 9D, the Thawed spearman

    /** The thrust: long and narrow. */
    public static final float THRUST_DAMAGE = 8.0F;
    public static final double THRUST_REACH = 5.0D;
    public static final double THRUST_ARC = 45.0D;
    public static final double THRUST_KNOCK = 0.6D;
    /** The shield wall: his shield up, every hit takes only this share of its damage, and five quick pokes. */
    public static final float GUARD_DAMAGE_TAKEN = 0.3F;
    public static final float POKE_DAMAGE = 4.0F;
    public static final double POKE_REACH = 4.5D;
    public static final double POKE_ARC = 55.0D;
    public static final double POKE_KNOCK = 0.25D;

    // ---------------------------------------------------------------- P1, the Blight Warden

    /** His staff's ball of poison (the Rat King's plague ball): it flies this many ticks a block, at least and at most. */
    public static final double CAST_TICKS_PER_BLOCK = 1.3D;
    public static final int CAST_TICKS_MIN = 8;
    public static final int CAST_TICKS_MAX = 32;
    /** The blight rain: poison falling round him this far out, for this long; every so often everything in it is
     * poisoned (Poison II) and stung. */
    public static final double RAIN_RADIUS = 7.0D;
    public static final int RAIN_TICKS = 80;
    public static final int RAIN_EVERY = 10;
    public static final float RAIN_DAMAGE = 1.5F;
    public static final int RAIN_POISON = 60;
    /** How many drops of it fall each tick (particles). */
    public static final int RAIN_DROPS = 26;
    public static final double RAIN_HEIGHT = 7.0D;

    // ---------------------------------------------------------------- P3, the Sporebound

    /** The reap: his scythe swept round him, side to side. */
    public static final float REAP_DAMAGE = 9.0F;
    public static final double REAP_REACH = 4.5D;
    public static final double REAP_ARC = 200.0D;
    public static final double REAP_KNOCK = 0.7D;
    public static final int REAP_POISON = 60;
    /** The spore burst: the Blightcap on his head puffs out a cloud this far round him: damage, sickness, poison, a moment
     * blind, pushed out; then clouds of poison linger round him (the Rat King's). */
    public static final double SPORE_RADIUS = 6.5D;
    public static final float SPORE_DAMAGE = 4.0F;
    public static final int SPORE_NAUSEA = 140;
    public static final int SPORE_POISON = 80;
    public static final int SPORE_BLIND = 30;
    public static final double SPORE_PUSH = 1.0D;
    public static final int SPORE_CLOUDS = 3;
    public static final double SPORE_CLOUD_OUT = 3.0D;

    // ---------------------------------------------------------------- F1B, the Cinder Knight

    /** The hammer's swing. */
    public static final float HAMMER_DAMAGE = 10.0F;
    public static final double HAMMER_REACH = 4.0D;
    public static final double HAMMER_ARC = 180.0D;   // a flat sweep (November 18, 2026)
    public static final double HAMMER_KNOCK = 1.1D;
    public static final int HAMMER_FIRE_SECONDS = 4;
    /** The slam: a shockwave this far round where the hammer lands, its damage falling to half at the edge, fire, thrown
     * up and out; then a ring of fire (the Colossus's own, it goes out by itself) round him. */
    public static final double SLAM_FORWARD = 2.5D;
    public static final double SLAM_RADIUS = 6.0D;
    public static final float SLAM_DAMAGE = 10.0F;
    public static final int SLAM_FIRE_SECONDS = 5;
    public static final double SLAM_LAUNCH = 0.7D;
    public static final double SLAM_KNOCK = 1.0D;
    public static final int SLAM_FIRES = 34;   // (16 until the user, November 18, 2026: "a little more fire")
    public static final double SLAM_FIRE_NEAR = 1.5D;
    public static final double SLAM_FIRE_FAR = 7.0D;
    public static final int SLAM_FIRE_MIN_TICKS = 90;
    public static final int SLAM_FIRE_MAX_TICKS = 150;

    // ---------------------------------------------------------------- F5, Flamehand

    /** A fireball: this fast (blocks a tick), this much, this long burning. Never sets a block alight. */
    public static final double FIREBALL_SPEED = 1.1D;
    public static final float FIREBALL_DAMAGE = 5.0F;
    public static final int FIREBALL_FIRE_SECONDS = 3;
    /** The great fireball: slow and big; it bursts on whatever it hits, this far round, its damage falling to half at the
     * edge, thrown out, a ring of fire left behind. Never breaks a block. */
    public static final double GREAT_SPEED = 0.55D;
    public static final double GREAT_RADIUS = 4.5D;
    public static final float GREAT_DAMAGE = 14.0F;
    public static final int GREAT_FIRE_SECONDS = 6;
    public static final double GREAT_KNOCK = 1.4D;
    public static final int GREAT_FIRES = 10;
    /** The great fireball is drawn this many times a fireball's size. */
    public static final float GREAT_SCALE = 3.5F;

    // ---------------------------------------------------------------- T3A, the Wreck Knight

    /** The anchor throw: it flies at this speed, at most this far; what it hits takes this and is hauled back to him (at
     * this speed, until it's this close or this many ticks have gone); then the anchor comes back to his hand. */
    public static final double ANCHOR_SPEED = 1.3D;
    public static final double ANCHOR_MAX_OUT = 22.0D;
    public static final float ANCHOR_DAMAGE = 7.0F;
    public static final double ANCHOR_HAUL_SPEED = 0.65D;
    public static final double ANCHOR_HAUL_STOP = 3.0D;
    public static final int ANCHOR_HAUL_TICKS = 30;
    public static final double ANCHOR_RETURN_SPEED = 1.4D;
    /** The anchor slam: swung over and down onto the ground in front of him, crackling: damage, knocked away, stunned
     * (Slowness II). */
    public static final float ANCHOR_SLAM_DAMAGE = 11.0F;
    public static final double ANCHOR_SLAM_REACH = 5.0D;
    public static final double ANCHOR_SLAM_ARC = 150.0D;
    public static final double ANCHOR_SLAM_KNOCK = 1.2D;
    public static final int ANCHOR_SLAM_STUN = 40;

    // ---------------------------------------------------------------- T6, the Stormblade

    /** The slash: two quick cuts. */
    public static final float SLASH_DAMAGE = 6.0F;
    public static final double SLASH_REACH = 3.8D;
    public static final double SLASH_ARC = 120.0D;
    public static final double SLASH_KNOCK = 0.3D;
    /** The dash (the user: "electric dashes where there's electricity behind him, kind of like the Flash"): he runs
     * straight through his foe at this speed, on past it this far (at most this long), cutting everything he passes;
     * lightning stays on the ground behind him this long, stinging whatever stands in it. */
    public static final double DASH_SPEED = 1.6D;
    public static final double DASH_PAST = 4.0D;
    public static final int DASH_MAX_TICKS = 14;
    public static final double DASH_HIT = 1.6D;
    public static final float DASH_DAMAGE = 7.0F;
    public static final double DASH_KNOCK = 0.6D;
    public static final int TRAIL_TICKS = 40;
    public static final double TRAIL_HIT = 0.9D;
    public static final float TRAIL_DAMAGE = 3.0F;
    public static final int TRAIL_EVERY = 10;
    /** After a dash he stands a moment before his next move. */
    public static final int DASH_RECOVER = 8;

    // ---------------------------------------------------------------- E3, the Geode Knight

    /** The pick's strike. */
    public static final float PICK_DAMAGE = 10.0F;
    public static final double PICK_REACH = 4.5D;
    public static final double PICK_ARC = 170.0D;   // a flat sweep, his pick's spike leading (November 18, 2026)
    public static final double PICK_KNOCK = 0.5D;
    /** The crystal burst (the user: "slamming the ground, crystals pop out around him, purple crystals"): a ring of
     * amethyst bursts up round him, then a wider one, then one under his foe; each throws up and hurts what's on it. */
    public static final double BURST_INNER = 2.5D;
    public static final int BURST_INNER_COUNT = 8;
    public static final double BURST_OUTER = 4.8D;
    public static final int BURST_OUTER_COUNT = 13;
    public static final int BURST_OUTER_DELAY = 5;
    public static final int BURST_FOE_DELAY = 10;
    public static final double BURST_HIT = 1.4D;
    public static final float BURST_DAMAGE = 8.0F;
    public static final double BURST_LAUNCH = 0.8D;
    /** The crystals' size (blocks tall). */
    public static final float CRYSTAL_MIN = 1.2F;
    public static final float CRYSTAL_MAX = 2.0F;
    public static final float CRYSTAL_FOE = 2.6F;

    // ---------------------------------------------------------------- E4, the Rockfall Knight

    /** The boulder throw (the Colossus's rock): this hard, flying this many ticks a block, at least and at most. */
    public static final float BOULDER_DAMAGE = 9.0F;
    public static final double BOULDER_TICKS_PER_BLOCK = 1.1D;
    public static final int BOULDER_TICKS_MIN = 10;
    public static final int BOULDER_TICKS_MAX = 30;
    /** The rockfall: his boulder brought down on the ground (a shockwave round him), and rocks rain down round his foe:
     * this many, over this long, from this high, this far round it, each this hard. */
    public static final double ROCKFALL_SLAM_RADIUS = 4.0D;
    public static final float ROCKFALL_SLAM_DAMAGE = 8.0F;
    public static final double ROCKFALL_SLAM_KNOCK = 0.9D;
    public static final int ROCKFALL_ROCKS = 16;   // (7 until the user, November 18, 2026: "his move 2 needs to do more throws")
    public static final int ROCKFALL_SPREAD_TICKS = 50;
    public static final double ROCKFALL_HEIGHT = 16.0D;
    public static final int ROCKFALL_FALL_TICKS = 14;
    public static final double ROCKFALL_RADIUS = 5.5D;
    public static final float ROCKFALL_DAMAGE = 6.0F;

    // ---------------------------------------------------------------- their sounds (tools/knights/sounds.py)

    /** About how often one makes its idle sound (ticks: Minecraft's chance a tick is one in this). */
    public static final int AMBIENT_INTERVAL = 160;
    /** How loud its footsteps are: the heavy knights' (Wreck, Rockfall, Cinder) clank, the light ones' (Stormblade,
     * Flamehand, Blight Warden) patter, the rest's. */
    public static final float STEP_VOLUME_HEAVY = 0.9F;
    public static final float STEP_VOLUME_LIGHT = 0.45F;
    public static final float STEP_VOLUME = 0.65F;

    /** The Rockfall Knight's thrown boulder and the rockfall's rocks: lumpy clumps of mossy stone this many blocks
     * across (the Colossus's own rock is a 0.8 block of cobblestone). */
    public static final float BOULDER_SIZE = 1.5F;
    public static final float ROCKFALL_ROCK_SIZE = 1.0F;

    /**
     * How much further than a melee move's reach its blow still lands, and how much wider its arc (degrees): you step
     * back during its wind-up, and the blow looked as if it hit (the user, November 18, 2026: "sometimes the mob does a
     * move 1 but it doesn't hit ... increase the range slightly").
     */
    public static final double REACH_SLACK = 1.0D;
    public static final double ARC_SLACK = 30.0D;
    /** The Cinder Knight's hammer swing leaves this many patches of fire on the ground in its arc, so long. */
    public static final int HAMMER_FIRES = 5;
    public static final int HAMMER_FIRE_MIN_TICKS = 50;
    public static final int HAMMER_FIRE_MAX_TICKS = 80;
    /** The Blight Warden's rain: poison drops falling from its cloud all the way down to the ground. */
    public static final int RAIN_FALLING = 10;

    private KnightTuning() {}
}
