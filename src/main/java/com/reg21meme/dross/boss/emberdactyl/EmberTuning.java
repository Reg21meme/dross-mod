package com.reg21meme.dross.boss.emberdactyl;

/**
 * Every number of the Emberdactyl's fight and its young's (the user's plan and approved numbers, October 27-29, 2026;
 * damage in health points: 2 a heart). {@link Kind} holds what differs between the adult and the young: the young (the
 * user: "bam bam ... no rest") makes the same moves, each hit as hard as the adult's, but flies and moves 2.5 times as
 * fast with almost no rest between moves.
 */
public final class EmberTuning
{
    // ---- Health and the two of them ----

    /** The adult's health (the user, October 29: 400). */
    public static final float ADULT_HEALTH = 400.0F;
    /** The young's health (the user, October 29: 175). */
    public static final float YOUNG_HEALTH = 175.0F;
    /** At or under this share of its health the adult flies and moves faster, makes a third tornado and its volcano
     *  rains lava bombs round its crater (the user: no new look or animation, just faster, and the extras). */
    public static final float ENRAGE_AT = 0.5F;
    /** Enraged: its speeds times this... */
    public static final double ENRAGED_SPEED = 1.2D;
    /** ...its cooldowns and pauses times this. */
    public static final double ENRAGED_COOLDOWNS = 0.7D;

    /**
     * What differs between the adult and the young.
     *
     * @param health      its health
     * @param armor       its armour
     * @param cruise      its flying speed between moves (blocks a tick)
     * @param fast        its speed swooping, carrying and in the dive's plunge (blocks a tick)
     * @param pause       ticks between one move and the next
     * @param cooldowns   its moves' cooldowns times this
     * @param orbit       how far from its target it circles between moves (blocks)
     * @param orbitHeight how high over its target it circles (blocks)
     * @param leash       how far from its home it fights (blocks, along the ground)
     * @param tornadoes   how many fire tornadoes its gust sends out
     * @param tornadoSize the tornadoes' size (1: the adult's)
     * @param stuck       ticks it lies stuck on the ground after its dive (the melee window)
     * @param width       its body's hitbox width (blocks)...
     * @param height      ...and height, from its feet
     * @param deathFall   the tick of its death animation its body starts falling to the ground (before it, it screams
     *                    in the air): it glides down to where it'll lie by then
     * @param standLift   how much higher its body is in its death's first pose than standing (blocks: its model's
     *                    root, flying against perched), so one dying on the ground doesn't pop up
     * @param lieRadius   the ground it lies on: the flattest spot within {@code lieSearch} blocks, measured this far
     *                    round (blocks)
     * @param lieSearch   ...searched this far round where it died
     */
    public record Kind(float health, double armor, double cruise, double fast, int pause, double cooldowns, double orbit,
                       double orbitHeight, double leash, int tornadoes, float tornadoSize, int stuck, float width, float height,
                       int deathFall, double standLift, double lieRadius, double lieSearch)
    {
    }

    public static final Kind ADULT = new Kind(ADULT_HEALTH, 10.0D, 0.55D, 1.4D, 30, 1.0D, 26.0D, 18.0D, 100.0D, 2, 1.0F, 60,
            5.0F, 11.0F, 38, (8.0D + 48.313D) / 16.0D, 7.0D, 30.0D);
    /** The young: 2.5 times as fast (the user, October 29), almost no rest. Its leash as long as the adult's and more
     *  (the user's first fight: "its battle range needs to be longer": at 40 blocks from its nest it never reached a
     *  player who'd beaten the adult further out). */
    public static final Kind YOUNG = new Kind(YOUNG_HEALTH, 6.0D, 0.55D * 2.5D, 1.4D * 2.5D, 4, 0.4D, 16.0D, 10.0D, 130.0D, 2,
            0.65F, 24, 3.5F, 9.5F, 23, (30.957D + 33.485D) / 16.0D, 4.5D, 20.0D);

    // ---- Waking ----

    /** It notices a player within this many blocks of its volcano's foot (the user: "within 60 blocks"); one from a
     *  spawn egg, within this many of where it was spawned. */
    public static final double NOTICE_RANGE = 60.0D;
    /** Its volcano's foot is about this far from its middle (blocks; {@link EmberVolcanoTuning#BASE_RADIUS} and its lobes). */
    public static final double VOLCANO_FOOT = EmberVolcanoTuning.BASE_RADIUS * 1.1D;
    /** With nobody to fight for this many ticks it goes back to resting (its health stays where it is). */
    public static final int GIVE_UP_TICKS = 300;
    /** Its boss bar shows to players within this many blocks. */
    public static final double BAR_RANGE = 110.0D;
    /** One from a spawn egg hovers this high over where it was spawned while calm, circling this far round it. */
    public static final double CALM_HOVER = 14.0D;
    public static final double CALM_CIRCLE = 12.0D;
    /** It never flies lower than this over the ground under it (except in its dive, its strafing run and on the ground). */
    public static final double MIN_CLEARANCE = 7.0D;
    /** How fast it turns (degrees a tick). */
    public static final float TURN = 9.0F;
    /** How quickly its speed changes towards the wanted one (share a tick). */
    public static final double ACCELERATION = 0.12D;
    /** Its screech is heard this far (the user: from afar, not only near it): its volume (16 blocks each). */
    public static final float SCREECH_VOLUME = 12.0F;
    /** Its other sounds' volume (16 blocks each). */
    public static final float VOLUME = 5.0F;

    // ---- The moves (the user's approved table, October 29; magma spit removed) ----

    /** Dive bomb: 7 hearts within this many blocks of where it crashes, everything thrown out and up (the user's first
     *  fight: "make the explosion when he dives much bigger, like a 5-7 block radius with knockback and knock up"; it was
     *  5 blocks, thrown 1.1 and lifted 0.7). Its throw is this strong in the middle, {@link #DIVE_EDGE_SHARE} of it at
     *  the edge. */
    public static final float DIVE_DAMAGE = 14.0F;
    public static final double DIVE_RADIUS = 7.0D;
    public static final double DIVE_THROW = 1.6D;
    public static final double DIVE_LIFT = 0.95D;
    public static final double DIVE_EDGE_SHARE = 0.6D;
    /** The crash's blasts: this many explosions round where it lands, and this many patches of fire left burning. */
    public static final int DIVE_BLASTS = 6;
    public static final int DIVE_FIRES = 12;
    public static final int DIVE_FIRE_MIN = 40;
    public static final int DIVE_FIRE_MAX = 70;
    public static final int DIVE_COOLDOWN = 160;

    /** Fire tornadoes: 1.5 hearts a second and fire while you're in one; each lasts 8 s, drifts after its target, pulls
     *  you in and throws you up into the sky (the user: "the tornado also makes you go into the sky and fall down"). */
    public static final float TORNADO_DAMAGE = 3.0F;
    public static final int TORNADO_HURT_EVERY = 20;
    public static final int TORNADO_LIFE = 160;
    public static final double TORNADO_SPEED = 0.18D;
    /** Within this many blocks of its middle it pulls you in... */
    public static final double TORNADO_PULL_RANGE = 6.0D;
    public static final double TORNADO_PULL = 0.08D;
    /** ...within this many it throws you up (and again only after this many ticks). */
    public static final double TORNADO_CORE = 1.8D;
    public static final double TORNADO_LIFT = 1.35D;
    public static final int TORNADO_LIFT_EVERY = 50;
    /** Fire it sets you on (seconds). */
    public static final int TORNADO_FIRE_SECONDS = 4;
    /** Its tornadoes go out this far apart (degrees). */
    public static final float TORNADO_SPREAD = 28.0F;
    public static final int TORNADO_COOLDOWN = 260;

    /** Strafing run: low over you in a line, breathing fire: 4 hearts and fire, and a trail of fire on the ground. */
    public static final float STRAFE_DAMAGE = 8.0F;
    public static final int STRAFE_FIRE_SECONDS = 5;
    /** It flies this high over the ground, from this far before you to this far past you. */
    public static final double STRAFE_HEIGHT = 6.0D;
    public static final double STRAFE_REACH = 24.0D;
    /** Its breath reaches this far either side of its line. */
    public static final double STRAFE_WIDTH = 2.5D;
    /** Its fire on the ground burns this long (ticks). */
    public static final int STRAFE_TRAIL_MIN = 80;
    public static final int STRAFE_TRAIL_MAX = 100;
    public static final double STRAFE_SPEED = 0.95D;
    public static final int STRAFE_COOLDOWN = 200;

    /** Talon grab: 3 hearts, then it carries you up and drops you near where it caught you (within
     *  {@link #GRAB_DRIFT}; the user, October 30: "why does he bring me up to the volcano, just drop me when we are
     *  fighting on the ground, no need to do that": until then one with a nest always took you over its crater, and
     *  one without 20 blocks off). Carried, you hang from its talons as its passenger (your game moves you with it, smoothly, and you
     *  look round as you like: the user's second fight, October 29, "I want to be able to move around as he carries me
     *  ... it also looks very stuttery"; sneak and you struggle free and fall), always in open air: it first climbs
     *  until you're {@link #GRAB_CLEARANCE} blocks over the highest ground on its way (the user's first fight: it had
     *  carried them straight through its volcano's side, "he tried to suffocate me"), flies across, comes down to the
     *  drop's height and lets go (or after {@link #GRAB_CARRY_TICKS}). The drop's height (the user, October 29: "make it
     *  so he can't drop me from any more than where it does at max half of my health"): 3 blocks plus
     *  {@link #GRAB_FALL_SHARE} of your health then (a fall's damage is a heart for every 2 blocks past the first 3),
     *  between {@link #GRAB_DROP_MIN} and {@link #GRAB_CARRY_HEIGHT}; and when you land (let go, or struggled free) the
     *  fall never takes more than that share of the health you have then. */
    public static final float GRAB_DAMAGE = 6.0F;
    public static final double GRAB_REACH = 4.5D;
    public static final double GRAB_CARRY_HEIGHT = 20.0D;
    public static final double GRAB_DROP_MIN = 4.0D;
    public static final float GRAB_FALL_SHARE = 0.5F;
    /** ...its fall counts as the grab's for this long after it let go (then a fall is a fall). */
    public static final int GRAB_FALL_GUARD_TICKS = 600;
    public static final double GRAB_CLEARANCE = 4.0D;
    public static final int GRAB_CARRY_TICKS = 160;
    /** It drops you at most this many blocks (along the ground) from where it caught you. */
    public static final double GRAB_DRIFT = 6.0D;
    public static final int GRAB_COOLDOWN = 280;

    /** Beak snap (on the ground): this many pecks one after another (the user: "make sure it can do 5 beak pecks all at
     *  once"), each this much in front of it (4 hearts a peck; it was one snap of 7). */
    public static final int SNAP_PECKS = 5;
    public static final float SNAP_DAMAGE = 8.0F;
    /** Each peck lands when its bill strikes the ground, this share of the way through the snap (the animation's "hit":
     *  until October 30 it landed at 0.35, while the adult's bill was still 11 blocks up). Its bill strikes this far in
     *  front of its feet (measured from the models: the adult's 14.4 blocks, the young's 10.5; it was 12 for the adult
     *  and 8.4 for the young), and the peck hurts everything round that spot within this far (the user's third look:
     *  "the peck does AoE damage ... I was in front of the beak and it does no damage to me"; it reached only 15 blocks
     *  ahead, so standing just past the tip was safe), and everything in front of it from under its chest (this far
     *  behind its feet) out to the strike, this far either side (blocks; the young's splash and width at its own
     *  scale). */
    public static final double SNAP_HIT_SHARE = 0.5D;
    public static final double SNAP_FROM = -4.0D;
    public static final double SNAP_SIDE = 5.0D;
    public static final double SNAP_STRIKE = 14.4D;
    public static final double YOUNG_SNAP_STRIKE = 10.5D;
    public static final double SNAP_SPLASH = 5.0D;
    /** (how far up or down from the strike's ground the splash reaches) */
    public static final double SNAP_SPLASH_HEIGHT = 5.0D;
    public static final int SNAP_COOLDOWN = 30;
    /** Lying stuck after a dive with its target near, the chance it pecks (else its buffet, its screech, or it takes off;
     *  the user's third look: peck more: 0.6 until then). The adult's chance to screech there (0.4 until then: "the 1st
     *  stage screeches too much"), the young's. */
    public static final float STUCK_SNAP_CHANCE = 0.85F;
    public static final float STUCK_SCREECH_CHANCE = 0.08F;
    public static final float YOUNG_STUCK_SCREECH_CHANCE = 0.1F;

    /** Wing buffet: 4 hearts in a cone in front, throwing you about 20 blocks (the user). In the air it first swoops
     *  down to this far from its target, this high over it, so its beat reaches it (from its circle, 18 blocks up, it
     *  missed everyone: the user's first fight, "it needs to push me back"). Everything in the cone is thrown, hurt or
     *  not (a player on fire, just hurt, wasn't). */
    public static final float BUFFET_DAMAGE = 8.0F;
    public static final double BUFFET_RANGE = 18.0D;
    public static final double BUFFET_REACH_DOWN = 12.0D;
    public static final double BUFFET_CONE = 75.0D;
    public static final double BUFFET_APPROACH = 9.0D;
    public static final double BUFFET_APPROACH_HEIGHT = 4.0D;
    public static final double THROW_20 = 2.2D;
    public static final double THROW_20_LIFT = 0.55D;
    public static final int BUFFET_COOLDOWN = 120;

    /** Screech: 1 heart, slowness, and everyone within this many blocks thrown about 20 blocks (the user). */
    public static final float SCREECH_DAMAGE = 2.0F;
    public static final double SCREECH_RANGE = 20.0D;
    public static final int SCREECH_SLOWNESS_TICKS = 60;
    /** (the adult's: 30 s, 15 s until the user's third look, "the 1st stage screeches too much") */
    public static final int SCREECH_COOLDOWN = 600;
    /** The young's screech comes back only after this long, not scaled by its short cooldowns (the user: it screeched
     *  "a little too much"). */
    public static final int YOUNG_SCREECH_COOLDOWN = 500;

    /** The young's dash: straight through you and on past, 5 hearts. */
    public static final float DASH_DAMAGE = 10.0F;
    public static final double DASH_PAST = 10.0D;
    public static final double DASH_WIDTH = 2.5D;
    public static final int DASH_COOLDOWN = 60;

    /** Enraged, the adult's volcano erupts every this many ticks: this many lava bombs onto spots near the players. */
    public static final int ERUPT_EVERY = 120;
    public static final int ERUPT_BOMBS = 3;
    public static final float ERUPT_DAMAGE = 7.0F;
    public static final double ERUPT_SPREAD = 6.0D;
    /** It erupts only while a player is within this many blocks of its nest. */
    public static final double ERUPT_RANGE = 60.0D;

    /** Which moves it picks, by how far its target is (along the ground): close, mid, far; and on the ground. The
     *  chances (out of each row's total): the user's approved table, then (their first fight) the tornadoes 5 points
     *  more likely in both stages, taken from the row's biggest; then (their third look, the adult only: "the 1st stage
     *  screeches too much, make it do other attacks like tornado more and peck more or dive more") the adult's close row
     *  grab 25, buffet 15, dive 35 (each dive ends in its pecks), tornado 20, screech 5 (it was grab 30, buffet 25, dive
     *  20, screech 15, no tornado), its mid row dive 32, tornado 38, strafe 18, grab 12 (it was 25, 30, 25, 20). The
     *  young's rows as they were (its dash and quieter screech). */
    public static final double CLOSE = 16.0D;
    public static final double MID = 40.0D;
    public static final int CLOSE_GRAB = 25, CLOSE_BUFFET = 15, CLOSE_DIVE = 35, CLOSE_TORNADO = 20, CLOSE_SCREECH = 5;
    public static final int MID_DIVE = 32, MID_TORNADO = 38, MID_STRAFE = 18, MID_GRAB = 12;
    public static final int YOUNG_CLOSE_GRAB = 30, YOUNG_CLOSE_BUFFET = 25, YOUNG_CLOSE_DIVE = 20, YOUNG_CLOSE_SCREECH = 5;
    public static final int CLOSE_DASH = 25;
    public static final int YOUNG_MID_DIVE = 24, YOUNG_MID_TORNADO = 31, YOUNG_MID_STRAFE = 25, YOUNG_MID_GRAB = 20;
    public static final int MID_DASH = 20;
    /** Against another Emberdactyl raged on another team (the Rage Wand: the user's third look, "I used the rage wand on
     *  the adult then ... the younger one, nothing seems to happen between the two"), a fight in the air: its talon
     *  strike (the grab's swoop, its talons raking it, no carrying), buffet, screech and tornadoes (and the young's dash);
     *  its dive only onto one lying stuck on the ground, and no strafing run. The talon strike reaches this much past
     *  their two bodies' half widths, and throws its target this hard. */
    public static final int DUEL_STRIKE = 35, DUEL_BUFFET = 20, DUEL_SCREECH = 10, DUEL_TORNADO = 15, DUEL_DIVE = 30,
            DUEL_DASH = 25;
    public static final double DUEL_STRIKE_REACH = 4.0D;
    public static final double DUEL_STRIKE_THROW = 0.8D;

    /** Its flight's loops by its speed (hover, glide, fly): it changes over only this share past the speed at the line
     *  and stays at least this long (ticks) after a change (until October 30 a speed near the line flicked it between
     *  them every tick, each change starting the loop again: the user saw its head snap back as it flew). */
    public static final double FLIGHT_LOOP_MARGIN = 0.25D;
    public static final int FLIGHT_LOOP_HOLD = 30;
    /** Far off (the user's second fight: "when you're far away he shouldn't tornado ... he always starts with a tornado
     *  ... instead make him do his fire glide thing then dive at you once he is closer"): its glide in (75) or its
     *  strafing run (25); no tornadoes. (It was strafe 35, tornado 40, dive 25.) */
    public static final int FAR_GLIDE = 75, FAR_STRAFE = 25;

    /** The glide in: from far off it glides at its target this high over the ground, fire dropping off it onto the
     *  ground every this many ticks, and dives once it's this close (along the ground); it gives up after this long. */
    public static final double GLIDE_HEIGHT = 12.0D;
    public static final int GLIDE_FIRE_EVERY = 3;
    public static final double GLIDE_DIVE_AT = 22.0D;
    public static final int GLIDE_TICKS = 300;
    public static final int GLIDE_COOLDOWN = 200;

    /** The young drops fire onto the ground under it whenever it's in the air fighting (the user: "whenever he does his
     *  glide animation he drops fire onto the ground, whenever he flies"; then, October 30, "much more aggressive ...
     *  not just 1 fire every couple blocks"): a solid trail, a row of fire across its path every this many blocks it
     *  flies, this many blocks either side of its middle (5 wide), and hovering in place a patch under it every this
     *  many ticks; burning this long (ticks), only where the ground is at most this far under it. A jump of more than
     *  this many blocks (a teleport) starts a new trail rather than filling the gap. (Until October 30 one patch every
     *  5 ticks, 50-80 ticks long: at its speed the patches came several blocks apart.) */
    public static final double YOUNG_TRAIL_STEP = 1.0D;
    public static final int YOUNG_TRAIL_HALF_WIDTH = 2;
    public static final int YOUNG_FIRE_EVERY = 3;
    public static final int YOUNG_FIRE_MIN = 70;
    public static final int YOUNG_FIRE_MAX = 110;
    public static final double YOUNG_FIRE_REACH = 40.0D;
    public static final double YOUNG_TRAIL_JUMP = 12.0D;

    // ---- Its death and the egg ----

    /** The adult's death: its egg cracks open this far into it (when its body breaks apart, 4.2 s)... */
    public static final int HATCH_TICKS = 84;
    /** ...and it vanishes this many ticks after it died. */
    public static final int ADULT_REMOVE_TICKS = 200;
    /** The young stays an obsidian statue this long after it died. */
    public static final int YOUNG_STATUE_TICKS = 2400;
    /** Dying, it glides down to where it'll lie at most this fast (blocks a tick); further away, it falls (its dive's
     *  plunge) until it can reach it in its scream, then dies. */
    public static final double DEATH_GLIDE = 1.1D;
    /** Lying on a fluid's top (the crater's lava lake) counts as this many blocks of uneven ground. */
    public static final double DEATH_FLUID_COST = 4.0D;
    /** Experience for each. */
    public static final int ADULT_XP = 50;
    public static final int YOUNG_XP = 50;
    /** "Wings Clipped" goes to the player credited with killing the young and every player within this many blocks. */
    public static final double VICTOR_RANGE = 64.0D;

    // ---- Its home (built volcanoes) ----

    /** A volcano's Emberdactyl and egg come when a player is within this many blocks of its middle. */
    public static final double KEEPER_RANGE = 200.0D;
    public static final int KEEPER_CHECK_TICKS = 40;
    /** The perched adult sits on its crater's rim, this far from its middle (blocks), opposite its lookout. */
    public static final double PERCH_OUT = 22.0D;

    private EmberTuning() {}
}
