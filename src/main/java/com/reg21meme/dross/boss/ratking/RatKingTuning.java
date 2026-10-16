package com.reg21meme.dross.boss.ratking;

/**
 * Every number of the Rat King's fight (CLAUDE.md, "The plan", step 9), in one place so they're easy to tune. Times
 * are in ticks (20 a second), distances in blocks, speeds are Minecraft's movement speed attribute (a walking mob covers
 * about speed x speed x 2.2 blocks a tick: 0.30 is about 4 blocks a second, a walking player 4.3).
 * <p>
 * The fight has two phases: the first, and the <b>Rage</b> from {@link #RAGE_AT} of his health (his eyes glow red; he's
 * faster, his blows hit harder, his moves come back sooner, he throws three poison balls at once, burrows more often
 * and stays under for less time, and calls enraged rats).
 */
public final class RatKingTuning
{
    // ---------------------------------------------------------------- his stats

    /** His health (100 hearts). */
    public static final double MAX_HEALTH = 200.0D;
    /** His armour (iron armour is 15). */
    public static final double ARMOR = 6.0D;
    /** How much knockback he shrugs off (0 none, 1 all). */
    public static final double KNOCKBACK_RESISTANCE = 0.6D;
    /** His speed in the first phase (about 4 blocks a second) and in the Rage (about 5.7: a sprinting player is 5.6). */
    public static final double SPEED = 0.30D;
    public static final double RAGE_SPEED = 0.36D;
    /** How far (blocks) he notices and follows players. */
    public static final double FOLLOW_RANGE = 40.0D;
    /** Experience he drops. */
    public static final int XP = 40;
    /** How high a step he runs up without jumping (blocks). */
    public static final float STEP_HEIGHT = 1.0F;
    /** How fast he turns to face his target during his moves (degrees a tick), first phase and Rage. */
    public static final float TURN_SPEED = 20.0F;
    public static final float RAGE_TURN_SPEED = 30.0F;

    // ---------------------------------------------------------------- waking up

    /** A player (not a spectator) this close wakes him up... */
    public static final double WAKE_RANGE = 12.0D;
    /** ...or this close if they're sneaking. Anything that hurts him wakes him too (that hit does no damage). */
    public static final double WAKE_RANGE_SNEAKING = 8.0D;

    // ---------------------------------------------------------------- the staff sweep (close)

    /** Everything within this many blocks of his middle, in front of him, is hit by the sweep (the user: +1 block). */
    public static final double SWEEP_RANGE = 4.5D;
    /** He starts a sweep once his target is this much inside {@link #SWEEP_RANGE} (so it's still in reach at the hit). */
    public static final double SWEEP_START_MARGIN = 0.5D;
    /** The sweep's arc in front of him (degrees, all of it: half to each side). */
    public static final double SWEEP_ARC = 140.0D;
    /** How far below his feet and above them a victim may stand and still be hit. */
    public static final double SWEEP_BELOW = 1.5D;
    public static final double SWEEP_ABOVE = 2.5D;
    /**
     * Damage (first phase, Rage; the user: +1 heart each), and knockback (vanilla's strength: a sword's is 0.4, Knockback
     * I adds 0.5; the user asked for more than the first version's 0.6).
     */
    public static final float SWEEP_DAMAGE = 9.0F;
    public static final float RAGE_SWEEP_DAMAGE = 11.0F;
    public static final double SWEEP_KNOCKBACK = 1.0D;
    /** Ticks before he can sweep again (first phase, Rage). */
    public static final int SWEEP_COOLDOWN = 30;
    public static final int RAGE_SWEEP_COOLDOWN = 22;

    // ---------------------------------------------------------------- the poison shot (ranged)

    /** He casts at targets this far away (blocks, from his middle), when he can see them. */
    public static final double CAST_MIN = 5.0D;
    public static final double CAST_MAX = 24.0D;
    /** Ticks before he can cast again (first phase, Rage). */
    public static final int CAST_COOLDOWN = 70;
    public static final int RAGE_CAST_COOLDOWN = 50;
    /** Poison balls per cast: one in the first phase, a fan of three in the Rage... */
    public static final int CAST_BALLS = 1;
    public static final int RAGE_CAST_BALLS = 3;
    /** ...the outer two this many degrees to the left and right of the middle one. */
    public static final double FAN_SPREAD = 18.0D;
    /** How fast a poison ball flies (blocks a tick, along the ground), and the shortest and longest flight (ticks). */
    public static final double BALL_SPEED = 0.8D;
    public static final int BALL_MIN_TICKS = 8;
    public static final int BALL_MAX_TICKS = 34;
    /** How fast a poison ball falls (blocks a tick, added each tick): a gentle arc. */
    public static final double BALL_GRAVITY = 0.03D;
    /** He aims ahead of a moving target: this share of where it would be by the time the ball lands. */
    public static final double BALL_LEAD = 0.5D;

    // ---------------------------------------------------------------- between moves

    /** He picks his next move after this many ticks (first phase, Rage); meanwhile he runs at his target. */
    public static final int ATTACK_PAUSE = 12;
    public static final int RAGE_ATTACK_PAUSE = 8;
    /** He stops running at his target when it's this close (blocks between them). */
    public static final double RUN_STOP = 1.2D;

    // ---------------------------------------------------------------- burrowing and summoning his rats

    /** He burrows every time he has lost this much health: in the first phase, and in the Rage (counted from the rage). */
    public static final float BURROW_EVERY = 30.0F;
    public static final float RAGE_BURROW_EVERY = 20.0F;
    /** Ticks he stays under the ground (first phase, Rage). */
    public static final int UNDERGROUND_TICKS = 80;
    public static final int RAGE_UNDERGROUND_TICKS = 50;
    /** How fast he tunnels towards his target under the ground (blocks a tick). */
    public static final double UNDERGROUND_SPEED = 0.35D;
    /** A dig sound every this many ticks while he tunnels. */
    public static final int UNDERGROUND_DIG_EVERY = 12;
    /**
     * Rats each burrow calls: regular ones in the first phase (the user asked for 10), enraged ones in the Rage. There's
     * no limit on how many of his rats can be alive at once (the user's request)...
     */
    public static final int RATS_PER_BURROW = 10;
    public static final int RAGE_RATS_PER_BURROW = 7;
    /** ...each bursting out of the ground this far round his target... */
    public static final double RAT_RING_MIN = 3.0D;
    public static final double RAT_RING_MAX = 6.0D;
    /** ...the first one this many ticks after he goes under, then one every {@link #RAT_STAGGER_TICKS} (10 in 2.5 s). */
    public static final int RAT_FIRST_DELAY = 8;
    public static final int RAT_STAGGER_TICKS = 5;
    /** He pops up this far from his target, facing it. */
    public static final double POPUP_DISTANCE = 4.0D;
    /** His rats within this many blocks count as his (and crumble with him). */
    public static final double RAT_RANGE = 48.0D;

    // ---------------------------------------------------------------- the rage

    /** At this share of his health he flies into a rage (the health stops there; he can't be hurt while it plays). */
    public static final float RAGE_AT = 0.40F;

    // ---------------------------------------------------------------- boss bar and the end

    /** His boss bar shows to players within this many blocks, once he's awake. */
    public static final double BOSS_BAR_RANGE = 40.0D;
    /**
     * Which bar design he uses (boss.client.RatKingBarStyles; /dross showcase bossbars ratking): the user's pick, 9,
     * "Pipe & eyes" (option 6's sewer pipe with option 8's rat peeking over it, browner, no pink fingers on the bar).
     */
    public static final int BOSS_BAR_STYLE = 9;
    /** The start of his boss bars' names (the client's DrossBossBars draws every bar whose name starts with it). */
    public static final String BAR_KEY_PREFIX = "boss.dross.rat_king.";
    /** He lies there this many ticks after his death animation, then vanishes in a puff of smoke. */
    public static final int CORPSE_TICKS = 200;
    /** A kill counts as a player's if they hit him within this many ticks before he died. */
    public static final int CREDIT_TICKS = 100;
    /**
     * The trophy design every player who helped beat him gets (1 to 5, see boss/ratking/trophy/RatKingTrophies):
     * design 5, Belly Up (him beaten). Any is fine: a placed trophy turns to any pose when right-clicked.
     */
    public static final int TROPHY_DROP_DESIGN = 5;

    // ---------------------------------------------------------------- the poison cloud

    /** A poison ball bursts into a cloud this wide (radius, blocks)... */
    public static final float CLOUD_RADIUS = 2.5F;
    /** ...that lasts this many ticks, shrinking to this share of its size as it fades... */
    public static final int CLOUD_TICKS = 60;
    public static final float CLOUD_END_SHARE = 0.3F;
    /** ...and every this many ticks poisons the living things inside it (Poison I, for this long: always short). */
    public static final int CLOUD_APPLY_EVERY = 10;
    public static final int POISON_TICKS = 50;
    /**
     * It only gives (or tops up) that poison when the one inside has none, or less than this many ticks of it left.
     * (Minecraft's Poison I hurts on the tick it's given and then every 25 ticks: topping it up at every check would
     * hurt every half second. This way standing in the cloud is a steady Poison I, a point of damage every 1.25 s.)
     */
    public static final int CLOUD_TOPUP_BELOW = 10;
    /** The cloud reaches this far below and above its middle. */
    public static final double CLOUD_BELOW = 0.5D;
    public static final double CLOUD_ABOVE = 2.0D;

    // ---------------------------------------------------------------- his rats

    /** A regular rat (the Plague Rat): health, bite damage, speed. */
    public static final double RAT_HEALTH = 6.0D;
    public static final double RAT_DAMAGE = 2.0D;
    public static final double RAT_SPEED = 0.3D;
    /** An enraged rat (the Plague Ravager): health, bite damage, speed... */
    public static final double ENRAGED_RAT_HEALTH = 9.0D;
    public static final double ENRAGED_RAT_DAMAGE = 3.0D;
    public static final double ENRAGED_RAT_SPEED = 0.34D;
    /** ...and each of its bites poisons for this long (Poison I). */
    public static final int ENRAGED_BITE_POISON_TICKS = 40;
    /** Experience a rat drops. */
    public static final int RAT_XP = 1;
    /** A wild regular rat flees this fast (times its speed) when hurt. */
    public static final double RAT_PANIC_SPEED = 1.5D;
    /** A summoned rat checks its king is still alive (and near) every this many ticks. */
    public static final int RAT_MASTER_CHECK = 20;
    /** A summoned rat hops this high (blocks a tick) as it bursts out of the ground. */
    public static final double RAT_HOP = 0.3D;

    private RatKingTuning() {}

    /** The name key of his boss bar in a design (1-8) and look: {@code boss.dross.rat_king.option<n>[.rage]}. */
    public static String barKey(int option, boolean rage)
    {
        return BAR_KEY_PREFIX + "option" + option + (rage ? ".rage" : "");
    }

    /** The name key of a showcase bar ({@code /dross showcase bossbars ratking}): {@code boss.dross.rat_king.showcase.option<n>[.rage]}. */
    public static String showcaseBarKey(int option, boolean rage)
    {
        return BAR_KEY_PREFIX + "showcase.option" + option + (rage ? ".rage" : "");
    }
}
