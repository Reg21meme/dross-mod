package com.reg21meme.dross.boss.giant;

/**
 * Every number of the Silverwood Giant ({@link SilverwoodGiant}; QUEST_PLAN.md, "The Silverwood Giant"). Distances are
 * in blocks, times in ticks (20 a second) unless the name says seconds, damage in half-hearts. When its animations
 * happen and where its blows land are the generated {@link GiantMoments}.
 */
public final class GiantTuning
{
    // ---------------------------------------------------------------- its body

    /** About 100 health (QUEST_PLAN.md: "about 100 health and heavy armour"). */
    public static final double HEALTH = 100.0D;
    /** Heavy armour: as much as a full set of iron and some (a diamond set is 20), and some toughness. */
    public static final double ARMOR = 12.0D;
    public static final double ARMOR_TOUGHNESS = 4.0D;
    /** It hardly budges when hit. */
    public static final double KNOCKBACK_RESISTANCE = 0.85D;
    /**
     * Its speed (a mob covers about 45 x (speed x the goal's modifier) squared blocks a second): with the modifiers below
     * it wanders at about 1.3 blocks a second and runs at about 3.5, slower than a player walks (4.3), so you can always
     * get away (QUEST_PLAN.md: "it's slow").
     */
    public static final double SPEED = 0.2D;
    public static final double WANDER_SPEED = 0.85D;
    public static final double CHASE_SPEED = 1.4D;
    /** Walking back after it gave up a chase. */
    public static final double RETURN_SPEED = 0.85D;
    public static final double FOLLOW_RANGE = 32.0D;
    /** Its attack stat (its smash's damage, below): what the Rage Wand and other mods read. */
    public static final double ATTACK_DAMAGE = 10.0D;
    /** It steps up a block (and a bit) without jumping. */
    public static final float STEP_HEIGHT = 1.1F;
    /** XP for killing one (QUEST_PLAN.md: about 15-20). */
    public static final int XP = 18;
    /** The most it turns a tick as it walks (a heavy giant doesn't spin round). */
    public static final float MAX_TURN = 14.0F;

    // ---------------------------------------------------------------- patience (QUEST_PLAN.md: "Neutral, with patience")

    /** A survival or adventure player within this many blocks of it tries its patience... */
    public static final double PATIENCE_RANGE = 7.0D;
    /** ...it warns them after this long (about 4 seconds: it stares, its eyes flare, it huffs and stamps)... */
    public static final int WARN_TICKS = 80;
    /** ...and attacks them after this long (about 8 seconds). */
    public static final int ANGER_TICKS = 160;
    /** Moving off calms it down again: their count drains this much a tick while they're further away... */
    public static final int PATIENCE_DRAIN = 2;
    /** ...and once it's under this, it would warn them again. */
    public static final int WARN_AGAIN_BELOW = 40;
    /** How often it looks round for players trying its patience (ticks; each look counts as that many ticks). */
    public static final int PATIENCE_CHECK = 5;

    // ---------------------------------------------------------------- anger and its leash (like the moose's)

    /** Its anger runs out after this long, even while it chases you (about 30 seconds). */
    public static final int ANGER_MIN_SECONDS = 25;
    public static final int ANGER_MAX_SECONDS = 35;
    /** It gives up a chase this far from where it began and walks back there. */
    public static final double CHASE_LEASH = 24.0D;

    // ---------------------------------------------------------------- its blows

    /** It swings at its target once it's this close (blocks from its middle to the target's edge)... */
    public static final double MELEE_REACH = 3.6D;
    /** ...and it pauses this long between two moves. */
    public static final int MOVE_PAUSE = 16;
    /** It keeps turning to its target during a move until this many ticks before the blow lands. */
    public static final int COMMIT_TICKS = 5;

    /** The smash: an overhead blow of about 5 hearts on everything within SMASH_RADIUS of where it lands. */
    public static final float SMASH_DAMAGE = 10.0F;
    public static final double SMASH_RADIUS = 2.2D;
    public static final double SMASH_KNOCKBACK = 0.6D;
    public static final int SMASH_COOLDOWN = 50;
    public static final int SMASH_WEIGHT = 45;

    /** The sweep: a backhand across in front of it (an arc of about 160 degrees, out to SWEEP_REACH), knocking whoever
     *  it hits away to its left (SWEEP_THROW along the ground, SWEEP_LIFT up). */
    public static final float SWEEP_DAMAGE = 7.0F;
    public static final double SWEEP_REACH = 4.5D;
    public static final double SWEEP_ARC_COS = 0.17D;
    public static final double SWEEP_THROW = 1.6D;
    public static final double SWEEP_LIFT = 0.4D;
    public static final int SWEEP_COOLDOWN = 40;
    public static final int SWEEP_WEIGHT = 35;

    /** The stomp: everything within STOMP_RADIUS of its stamping foot is hurt and thrown up (STOMP_LIFT: about 3.5 blocks
     *  high) and a little out. More likely with two or more round it. */
    public static final float STOMP_DAMAGE = 6.0F;
    public static final double STOMP_RADIUS = 4.0D;
    public static final double STOMP_LIFT = 0.75D;
    public static final double STOMP_OUT = 0.35D;
    public static final int STOMP_COOLDOWN = 120;
    public static final int STOMP_WEIGHT = 20;
    /** With this many or more in reach of its stomp, the stomp's weight is this instead. */
    public static final int STOMP_CROWD = 2;
    public static final int STOMP_CROWD_WEIGHT = 120;

    // ---------------------------------------------------------------- its boulder

    /** It hurls a boulder (only at someone it's angry with) from this far to this far, with a clear line... */
    public static final double THROW_MIN = 8.0D;
    public static final double THROW_MAX = 28.0D;
    /** ...not again for this long. */
    public static final int THROW_COOLDOWN = 140;
    /** The boulder: its damage, how it knocks you, how fast it falls (blocks a tick, added each tick). */
    public static final float BOULDER_DAMAGE = 8.0F;
    public static final double BOULDER_KNOCKBACK = 0.9D;
    public static final double BOULDER_GRAVITY = 0.05D;
    /** Its flight: this many ticks a block of distance, within these. */
    public static final double BOULDER_TICKS_PER_BLOCK = 1.2D;
    public static final int BOULDER_MIN_TICKS = 12;
    public static final int BOULDER_MAX_TICKS = 34;
    /** Dust when it shatters. */
    public static final int BOULDER_DUST = 40;

    // ---------------------------------------------------------------- its animations

    /** GeckoLib blends into a new animation over this long, and only then starts it (the moments count from then). */
    public static final int BLEND_TICKS = 4;
    /** Slower than this (blocks a second) it stands; faster than RUN_ABOVE its run shows, else its walk. */
    public static final double MOVING = 0.15D;
    public static final double RUN_ABOVE = 2.2D;
    /** Its walk and run play at most this much slower or faster than they were made. */
    public static final double MIN_SPEEDUP = 0.5D;
    public static final double MAX_SPEEDUP = 2.0D;
    /** It lies dead this long after its death animation ends, then goes in a puff of smoke. */
    public static final int DEATH_LINGER_TICKS = 40;
    /** Its sounds: how far its roar and its blows carry (volume; 1 is 16 blocks), and its footsteps' loudness. */
    public static final float ROAR_VOLUME = 2.5F;
    public static final float BLOW_VOLUME = 2.0F;
    public static final float STEP_VOLUME = 0.9F;
    /** Its sweep's whoosh peaks this long after it starts (so it's started that much before the hit); the throw's yell
     *  this long before the boulder leaves its hands. */
    public static final int SWEEP_SOUND_LEAD = 8;
    public static final int THROW_SOUND_LEAD = 10;
    /** Ticks between its grumbles, on average. */
    public static final int AMBIENT_INTERVAL = 240;

    // ---------------------------------------------------------------- where it spawns (SilverwoodGiants)

    /**
     * Never within this many blocks of another giant, so a few roam within sight of you (since November 19, 2026, the
     * user: "I don't see any ... giants at all in the forests"; at 96 a probe found one within 128 blocks of a player, out
     * of sight in the trees)...
     */
    public static final double SPAWN_SPACING = 64.0D;
    /**
     * ...and never while this many giants already stand within {@link #SPAWN_AREA} blocks (with only the spacing, the
     * game's own two minutes near the hub gave 8 within 128 blocks: too many)...
     */
    public static final int SPAWN_AREA_CAP = 4;
    public static final double SPAWN_AREA = 128.0D;
    /** ...never within this many blocks of the hub's middle (128 until November 19)... */
    public static final double HUB_CLEARANCE = 96.0D;
    /** ...and only with this many blocks of open air over the 3 x 3 blocks round where it stands (its 4.4 blocks). */
    public static final int SPAWN_HEADROOM = 5;
    /** Clients see it up to this many chunks away. */
    public static final int TRACKING_CHUNKS = 8;

    private GiantTuning() {}
}
