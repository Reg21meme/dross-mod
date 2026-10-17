package com.reg21meme.dross.boss.ratking;

import java.util.List;

/**
 * Every number of the Rat King's fight (CLAUDE.md, "The plan", step 9), in one place so they're easy to tune. Times
 * are in ticks (20 a second), distances in blocks, speeds are Minecraft's movement speed attribute (a walking mob covers
 * about speed x speed x 2.2 blocks a tick: 0.33 is about 4.8 blocks a second, a walking player 4.3).
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
    /**
     * His speed in the first phase (about 4.8 blocks a second) and in the Rage (about 6.4: a sprinting player is 5.6).
     * The user's numbers (October 16: "I'd rather increase his speed and his staff attacks than him spam poison"; the
     * first version's were 0.30 and 0.36).
     */
    public static final double SPEED = 0.33D;
    public static final double RAGE_SPEED = 0.38D;
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
    /** Ticks before he can sweep again (first phase, Rage; the user, October 16: sooner than the first version's 30 / 22). */
    public static final int SWEEP_COOLDOWN = 22;
    public static final int RAGE_SWEEP_COOLDOWN = 16;

    // ---------------------------------------------------------------- the spear charge (the user's request, October 16)

    /**
     * The wind-up (ticks from the start of the move): he crouches, his staff levelled forward like a spear, turning to
     * face his target; at its end he locks his target's spot and dashes (0.4 s; the animation is made to fill it).
     */
    public static final int CHARGE_WINDUP_TICKS = 8;
    /** He charges at targets this far away (blocks, from his middle; it's in the move table's mid and far bands)... */
    public static final double CHARGE_MIN = 5.0D;
    public static final double CHARGE_MAX = 24.0D;
    /** ...at most this much above or below him... */
    public static final double CHARGE_MAX_RISE = 1.5D;
    /** ...and runs at most this far (blocks), straight to the locked spot (never past it). */
    public static final double CHARGE_MAX_DISTANCE = 24.0D;
    /**
     * He only charges when he can see his target and nothing solid (and no liquid) stands in this share of the way to it
     * (checked every {@link #CHARGE_CHECK_STEP} blocks; low steps are fine, he runs up them). A wall in the rest of the
     * way stops him (then he recovers).
     */
    public static final double CHARGE_CLEAR_SHARE = 0.9D;
    public static final double CHARGE_CHECK_STEP = 1.0D;
    /** How fast he dashes (blocks a tick: 9 and 11 blocks a second, faster than his run). */
    public static final double CHARGE_SPEED = 0.45D;
    public static final double RAGE_CHARGE_SPEED = 0.55D;
    /** A dash gives up this many ticks after it should have arrived (stuck on something). */
    public static final int CHARGE_SLACK_TICKS = 6;
    /** He's there when he's this close (blocks) to the locked spot. */
    public static final double CHARGE_ARRIVED = 0.05D;
    /**
     * The first thing in his path (alive, not his own rats) takes this much (first phase, Rage), with only a small
     * knockback (vanilla's strength: a sword's is 0.4) so it stays in reach of the sweep that follows.
     */
    public static final float CHARGE_DAMAGE = 6.0F;
    public static final float RAGE_CHARGE_DAMAGE = 8.0F;
    public static final double CHARGE_KNOCKBACK = 0.2D;
    /** The lance hits what's within the dash's reach in front of him (generated: chargeReach) and this much to each side of his body. */
    public static final double CHARGE_HIT_SIDE = 0.3D;
    /**
     * If he misses (the target dodged) he skids to a stop at the spot, and if he runs into a wall he stops there: either
     * way he needs this many ticks to recover (he can't act; 0.5 s) before he moves on. Meanwhile he turns to face his
     * target again.
     */
    public static final int CHARGE_RECOVER_TICKS = 10;
    /** Ticks before he can charge again (first phase 5 s, Rage 3.5 s). */
    public static final int CHARGE_COOLDOWN = 100;
    public static final int RAGE_CHARGE_COOLDOWN = 70;

    // ---------------------------------------------------------------- which move he makes (the user's table, October 16)

    /** His choices when he's free to pick a move, in percent: a staff sweep, the spear charge, the poison shot, or running in. */
    public record Chances(int sweep, int charge, int poison, int runIn) {}

    /**
     * The bands of his target's distance: close (in his sweep's reach, about 4 blocks: {@link #SWEEP_RANGE} less
     * {@link #SWEEP_START_MARGIN}), mid (from {@link #BAND_MID_FROM} to {@link #BAND_FAR_FROM}), far (to
     * {@link #BAND_FAR_TO}), and beyond (further, or out of sight). Between close and mid (about 4 to 5 blocks) he just
     * runs in, without a roll.
     */
    public static final double BAND_MID_FROM = 5.0D;
    public static final double BAND_FAR_FROM = 16.0D;
    public static final double BAND_FAR_TO = 24.0D;
    /**
     * The first phase: close, mid, far, beyond. Moves that are on their cooldown or can't be made right now (no clear
     * run for the charge...) drop out, and the others share their chance.
     */
    public static final List<Chances> FIRST_CHANCES = List.of(
            //          sweep  charge  poison  run in
            new Chances(100,   0,      0,      0),
            new Chances(0,     60,     25,     15),
            new Chances(0,     40,     30,     30),
            new Chances(0,     0,      0,      100));
    /** The Rage: close, mid, far, beyond. */
    public static final List<Chances> RAGE_CHANCES = List.of(
            //          sweep  charge  poison  run in
            new Chances(100,   0,      0,      0),
            new Chances(0,     60,     35,     5),
            new Chances(0,     35,     35,     30),
            new Chances(0,     0,      0,      100));
    /**
     * "Run in": he runs at his target and doesn't roll again for this many ticks (2 s); only his close band's sweep
     * (not a roll: it's certain) can come sooner, if his target gets into its reach.
     */
    public static final int RUN_IN_TICKS = 40;

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

    /**
     * He burrows every time he has lost this much health: in the first phase (at 175, 150 and 125, then the rage at 100),
     * and in the Rage, counted from the rage (at 75, 50 and 25). The user, October 17: every 25 in both (from 30 and 20).
     */
    public static final float BURROW_EVERY = 25.0F;
    public static final float RAGE_BURROW_EVERY = 25.0F;
    /** Ticks he stays under the ground (first phase, Rage). */
    public static final int UNDERGROUND_TICKS = 80;
    public static final int RAGE_UNDERGROUND_TICKS = 50;
    /** How fast he tunnels towards his target under the ground (blocks a tick). */
    public static final double UNDERGROUND_SPEED = 0.35D;
    /** A dig sound every this many ticks while he tunnels. */
    public static final int UNDERGROUND_DIG_EVERY = 12;
    /**
     * Rats each burrow calls, one number per burrow of the phase (the first, the second, the third; a later one calls the
     * last number again): regular ones in the first phase, enraged ones in the Rage. The user, October 17: 10, 12, 14
     * and 8, 9, 10 (from 10 each and 7 each). There's no limit on how many of his rats can be alive at once (the user's
     * request)...
     */
    public static final List<Integer> RATS_PER_BURROW = List.of(10, 12, 14);
    public static final List<Integer> RAGE_RATS_PER_BURROW = List.of(8, 9, 10);
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

    /**
     * At this share of his health he flies into a rage (the health stops there; he can't be hurt while it plays). The
     * user, October 17: at 100 of his 200 (from 40%).
     */
    public static final float RAGE_AT = 0.50F;

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
    /** ...and each of its bites poisons for this long (Poison I; the user, October 17: 2.5 s, from 2). */
    public static final int ENRAGED_BITE_POISON_TICKS = 50;
    /** Experience a rat drops. */
    public static final int RAT_XP = 1;
    /** A wild regular rat flees this fast (times its speed) when hurt. */
    public static final double RAT_PANIC_SPEED = 1.5D;
    /** A summoned rat checks its king is still alive (and near) every this many ticks. */
    public static final int RAT_MASTER_CHECK = 20;
    /** A summoned rat hops this high (blocks a tick) as it bursts out of the ground. */
    public static final double RAT_HOP = 0.3D;

    // ---------------------------------------------------------------- his home: the rat caves under Blightwood

    /**
     * A player (not a spectator) this close to a rat cave's nest (blocks, in a straight line) makes the cave's Rat King
     * appear there, asleep on the nest (if it has none and he hasn't been beaten there: no respawn, the user's decision of
     * October 16). It's well beyond his {@link #WAKE_RANGE}, so he's lying there asleep before anyone gets close.
     */
    public static final double RAT_CAVE_KING_RANGE = 48.0D;
    /** The rat caves near players are looked at every this many ticks (twice a second). */
    public static final int RAT_CAVE_CHECK_INTERVAL_TICKS = 10;
    /**
     * He never pops into view: he doesn't appear while a player who was already in the cave at the last look is still in
     * it (its box, its walls and this many blocks past them: the doorways). Someone who has only just arrived (walked in
     * from a tunnel, teleported in, logged in) doesn't count.
     */
    public static final int RAT_CAVE_WATCH_MARGIN = 2;
    /** A cave's king found more than this many blocks outside his cave's box is put back on his nest (like the Colossus). */
    public static final int RAT_CAVE_HOME_MARGIN = 4;
    /** His feet on the nest: this far up the top carpet's block (a carpet is 1/16 of a block tall). */
    public static final double RAT_CAVE_NEST_CARPET = 1.0D / 16.0D;
    /**
     * Plague Ravagers that spawn by themselves inside a rat cave are stronger than the ones elsewhere (the user's
     * request): this many times the health and the bite damage (9 health and 3 damage become 13.5 and 4.5). His own
     * summoned rats and rats anywhere else stay normal.
     */
    public static final double RAT_CAVE_ENRAGED_HEALTH_MULTIPLIER = 1.5D;
    public static final double RAT_CAVE_ENRAGED_DAMAGE_MULTIPLIER = 1.5D;

    // ---------------------------------------------------------------- his snores at the grand entrance

    /**
     * While a rat cave's Rat King is asleep on his nest (or hasn't appeared yet and hasn't been beaten), players this close
     * to his sewer's grand entrance (blocks from the air over its crowned rim) now and then hear him snore down the pipe.
     * A sound at the volume below can be heard about 16 blocks off, so this matches it.
     */
    public static final double RAT_CAVE_SNORE_RANGE = 16.0D;
    /** The snore rising out of the pipe is quieter than his own on the nest (0.7)... */
    public static final float RAT_CAVE_SNORE_VOLUME = 0.4F;
    /** ...and a little deeper, echoing up the copper (its pitch is this, give or take {@link #RAT_CAVE_SNORE_PITCH_SPREAD}). */
    public static final float RAT_CAVE_SNORE_PITCH = 0.85F;
    public static final float RAT_CAVE_SNORE_PITCH_SPREAD = 0.05F;
    /** A snore comes up at most every this many ticks... */
    public static final int RAT_CAVE_SNORE_MIN_TICKS = 100;
    /** ...and at least every this many (a random wait between: 5 to 11 seconds). */
    public static final int RAT_CAVE_SNORE_MAX_TICKS = 220;
    /** Which grand entrances are near the players is asked (from the planner, on a background thread) every this many ticks. */
    public static final int RAT_CAVE_SNORE_LOOKUP_TICKS = 40;
    /** The snores are looked after every this many ticks. */
    public static final int RAT_CAVE_SNORE_CHECK_TICKS = 10;

    // ---------------------------------------------------------------- the rats' spawn light in the sewers

    /**
     * Rats spawn by themselves only in the dark, like any monster (in the Dross no block light at all), except inside the
     * sewers (the {@code dross:sewers} biome under Blightwood, and the sewer networks' rooms, tunnels and rat caves out of
     * the sky's reach): there they may spawn in dim light up to this block light (the user's request: the sewers' glow
     * lichen gives 7). Brighter spots (candles round his nest, the grand entrance's Blight Vents) stay rat-free.
     */
    public static final int RAT_SEWER_SPAWN_MAX_BLOCK_LIGHT = 7;

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
