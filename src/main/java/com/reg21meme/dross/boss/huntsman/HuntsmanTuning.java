package com.reg21meme.dross.boss.huntsman;

/**
 * Every number of the Huntsman's fight (the user's attack table, approved October 20, 2026, with their edits: the
 * Antler Charge 6.5 hearts, the Back Kick 4.5, no marks warning where the arrow rain lands, the horn when you first
 * attack the pair and in stage 2 too). Damage is in health points (2 = one heart); times are in ticks (20 a second);
 * distances in blocks. Chances are weights: a move that can't be made right now drops out and the others share its
 * share.
 */
public final class HuntsmanTuning
{
    private HuntsmanTuning() {}

    // ---------------------------------------------------------------- health and the stages

    /** Stage 1, the Wild Hunt: one bar for both of them (the user, October 23, 2026: "increase the Wild Hunt's health
     * by 50"; 200 before). */
    public static final float WILD_HUNT_HEALTH = 250.0F;
    /** At home in his village (since October 23, 2026), calm, they wander at most this far from their yard. */
    public static final int HOME_WANDER = 24;
    /** A fight broken off this far or more from home: they go back home (the user: "walk away: they're back home"). */
    public static final double HOME_RETURN = 16.0D;
    /** Stage 2: each of them on his own bar. */
    public static final float HUNTSMAN_HEALTH = 150.0F;
    public static final float MOOSE_HEALTH = 150.0F;
    /** At the split neither can be hurt for this long (the moose rears, he leaps off, the horn). */
    public static final int SPLIT_INVULNERABLE_TICKS = 40;
    /** The Huntsman's horn at the split, this long after he lands. */
    public static final int SPLIT_HORN_DELAY = 6;
    /** Their bars show to players this close. */
    public static final double BOSS_BAR_RANGE = 40.0D;
    /** They go for players this close (once the fight has started). */
    public static final double FOLLOW_RANGE = 48.0D;
    /** With no player this close for {@link #BREAK_OFF_TICKS} the fight is broken off: both come back at full health,
     * the Huntsman back in the saddle (the user: "you can't kill one of them, come back all healed up and kill the
     * other"). In a village they'll ride off and come back later; for now (a spawn egg, the arena) they stay. */
    public static final double BREAK_OFF_RANGE = 64.0D;
    public static final int BREAK_OFF_TICKS = 600;
    /** A kill is the player's if they hurt one of them this recently. */
    public static final int CREDIT_TICKS = 600;
    /** Experience for each of them. */
    public static final int XP_EACH = 25;
    /** A body lies there this long after its death animation, then it's gone in a puff of snow. */
    public static final int CORPSE_TICKS = 200;

    // ---------------------------------------------------------------- speeds and pauses

    /** Walking speed (the attribute): calm, they walk. */
    public static final double MOOSE_SPEED = 0.3D;
    public static final double HUNTSMAN_SPEED = 0.3D;
    /** In the fight they run at this many times it (a walking mob covers about 2.15 x its speed squared blocks a
     * tick: the moose about 6.5 blocks a second, him about 5.6, a sprinting player's). */
    public static final double MOOSE_RUN = 1.3D;
    public static final double HUNTSMAN_RUN = 1.2D;
    /** Between two moves. */
    public static final int PAUSE_WILD_HUNT = 16;
    /** Between his own moves from the saddle (stage 1: his timer runs beside the moose's). */
    public static final int PAUSE_SADDLE = 12;
    public static final int PAUSE_HUNTSMAN = 12;
    public static final int PAUSE_MOOSE = 14;
    public static final int PAUSE_ANGRY = 8;
    /** Angry (the other has died): faster, harder hits, moves back sooner. */
    public static final double ANGRY_SPEED = 1.2D;
    public static final float ANGRY_DAMAGE = 1.25F;
    public static final double ANGRY_COOLDOWN = 0.7D;
    /** How fast they turn to face their target (degrees a tick). */
    public static final float TURN = 12.0F;
    /** The moose's body: from its middle to its antlers in front and its rump behind (its hitbox is a 2.8-block square,
     * its model 6.8 long). */
    public static final double MOOSE_FRONT = 2.6D;
    public static final double MOOSE_BACK = 2.4D;
    /** His hand, to throw from: this far up in the saddle and on foot, this far ahead of him. (Since October 23, 2026
     * he's a tenth smaller, round his seat in the saddle (4.5 over the moose's feet) and his feet on foot: tools/rimefall
     * rimemobs/hunter_rig.SHRINK; until then 5.6, 3.4 and 0.8.) */
    public static final double RIDER_HAND_UP = 5.5D;
    public static final double FOOT_HAND_UP = 3.06D;
    public static final double HAND_AHEAD = 0.72D;
    /** In the saddle his hitbox sits this far above the moose's feet (his model is drawn as far lower), over the moose's
     * back round his legs, body and head, a little wider than the moose's (so a swing at his legs and the saddle hits
     * him, one at the moose's body below them the moose): until October 22, 2026 it stood inside the moose's, and every
     * swing and arrow at him hit the moose (the user: "I need to be able to damage both the moose and the hunter"). */
    public static final double RIDER_SEAT = 3.87D;
    public static final float RIDER_WIDTH = 2.9F;
    public static final float RIDER_HEIGHT = 3.6F;
    /** His eyes, that high above his seat (under the wolf's hood). (3.8, 3.2, 4.0 and 3.0 before he was a tenth
     * smaller.) */
    public static final float RIDER_EYES = 2.7F;

    // ---------------------------------------------------------------- spotting, turning, moving (October 22, 2026)

    /** Calm, he blows his horn and the hunt is on as soon as one of them sees a foe this close: a player (not in
     * creative or spectating), a hostile mob, or (raged) a mob of another team (the user: "it should blow its horn
     * when there is a hostile nearby, not when you first attack it"). Hitting them still starts it too. */
    public static final double SPOT_RANGE = 30.0D;
    /** How often (ticks) they look about for one, calm. */
    public static final int SPOT_INTERVAL = 10;
    /** How fast they turn (degrees a tick) as they move: the moose a big animal's slow, steady turn, him a man's. */
    public static final float MOOSE_MOVE_TURN = 9.0F;
    public static final float HUNTSMAN_MOVE_TURN = 20.0F;
    /** Turning further than the first from where it's going (degrees), it slows down to turn rather than swing wide;
     * from the second on, to the last share of its speed. */
    public static final float TURN_SLOW_ANGLE = 50.0F;
    public static final float TURN_SLOWEST_ANGLE = 90.0F;
    public static final float TURN_SLOW_LEAST = 0.3F;
    /** Their gait (blocks a tick, its speed smoothed over a few ticks, {@link #GAIT_SMOOTHING} of each tick's): over the
     * first they run, under the second they stop running; over the third they walk, under the last they stop. The gap
     * keeps them from flickering between two gaits (the user saw the walk "awkward and jittery"). */
    public static final double RUN_ON = 0.18D;
    public static final double RUN_OFF = 0.12D;
    public static final double WALK_ON = 0.03D;
    public static final double WALK_OFF = 0.015D;
    public static final double GAIT_SMOOTHING = 0.35D;
    /** A new gait has to last this many ticks before they change to it (straight away out of a move). */
    public static final int GAIT_HOLD = 4;
    /** How far their gaits go as made (blocks a second: tools/rimefall/rimemobs/alpha_moose.py WALK, GALLOP and CHARGE,
     * huntsman.py WALK and RUN). A walk or run is played at the speed they really go over this, so hooves and boots keep
     * pace with the ground: the moose's gallop at about 0.8 (6.5 blocks a second in a chase), its dash at about 1.1 (11),
     * his run at about 0.8 (5.6). */
    public static final double MOOSE_WALK_PACE = 1.38D;
    public static final double MOOSE_GALLOP_PACE = 8.42D;
    public static final double MOOSE_CHARGE_PACE = 10.26D;
    public static final double HUNTSMAN_WALK_PACE = 2.02D;
    public static final double HUNTSMAN_RUN_PACE = 7.2D;
    /** ... never slower or faster than these, and changed only when it's off by more than the last (each change moves
     * the loop's beat, {@link HuntMob#setPace}). */
    public static final float PACE_LEAST = 0.5F;
    public static final float PACE_MOST = 1.6F;
    public static final float PACE_STEP = 0.08F;
    /** In stage 2 he keeps clear of the moose (the user saw them walk into each other): out of an oval round it this far
     * past its body's middle ahead (its head and antlers) and to the sides (its antlers' spread), plus his own half
     * width; inside it he's pushed out this hard (blocks a tick at its middle), the moose a little the other way. */
    public static final double APART_AHEAD = 3.6D;
    public static final double APART_SIDE = 2.2D;
    public static final double APART_PUSH_HIM = 0.18D;
    public static final double APART_PUSH_MOOSE = 0.04D;

    // ---------------------------------------------------------------- the distance bands of the move tables

    public static final double WILD_CLOSE = 5.0D;
    public static final double WILD_MID = 14.0D;
    public static final double WILD_FAR = 28.0D;
    public static final double WILD_VERY_FAR = 40.0D;
    public static final double FOOT_CLOSE = 4.0D;
    public static final double FOOT_MID = 12.0D;
    public static final double FOOT_FAR = 28.0D;
    public static final double FOOT_VERY_FAR = 40.0D;
    public static final double MOOSE_CLOSE = 5.0D;
    public static final double MOOSE_MID = 14.0D;
    public static final double MOOSE_FAR = 30.0D;

    // ---------------------------------------------------------------- stage 1: the Wild Hunt

    public static final float THRUST_DAMAGE = 8.0F;              // 4 hearts
    public static final double THRUST_REACH = 4.5D;
    public static final int THRUST_COOLDOWN = 40;

    public static final float CHARGE_DAMAGE = 13.0F;             // 6.5 hearts (the user)
    public static final int CHARGE_COOLDOWN = 120;
    public static final int CHARGE_COOLDOWN_STAGE2 = 100;
    public static final double CHARGE_MIN = 7.0D;
    public static final double CHARGE_MAX = 28.0D;
    /** It lowers its antlers and paws for this long, then dashes to where its target stood at the end of it. */
    public static final int CHARGE_WINDUP = 12;
    public static final double CHARGE_SPEED = 0.55D;             // blocks a tick (11 a second)
    public static final double CHARGE_KNOCK = 1.6D;
    public static final double CHARGE_KNOCK_UP = 0.4D;
    /** After a dash that hit nothing it skids and can't act for this long. */
    public static final int CHARGE_SKID = 10;

    public static final float BACK_KICK_DAMAGE = 9.0F;           // 4.5 hearts (the user)
    public static final double BACK_KICK_REACH = 3.0D;
    /** Behind it: this far round from straight ahead (degrees). */
    public static final double BACK_KICK_ANGLE = 120.0D;
    public static final int BACK_KICK_COOLDOWN = 80;
    public static final double BACK_KICK_KNOCK = 2.2D;
    public static final double BACK_KICK_KNOCK_UP = 0.45D;

    /** How many come down in both stages: four for each of the 12 he shoots up (the user's pick, October 23, 2026: "48:
     * 4 per arrow"; 15 in the Wild Hunt and 10 on foot before, which a player standing still was hit by about once in
     * six rains). */
    public static final int RAIN_ARROWS = 48;
    public static final int RAIN_ARROWS_STAGE2 = 48;
    public static final double RAIN_RADIUS = 6.0D;
    /** How the arrows spread over the circle: a spot's distance from the middle is the radius times a random 0-1
     * raised to this (0.5 would spread them evenly over it; 0.75 crowds them towards where you are, so standing still
     * in it you take about two of the 48, and you run out of it). */
    public static final double RAIN_SPREAD_POWER = 0.75D;
    /** The arrows come down this long after the last loosing, spread over {@link #RAIN_SPREAD} ticks. */
    public static final int RAIN_DELAY = 16;
    public static final int RAIN_SPREAD = 30;
    public static final double RAIN_HEIGHT = 18.0D;
    /** Their speed coming down, and the base damage that makes a hit 3 (1.5 hearts) at it. */
    public static final double RAIN_SPEED = 1.5D;
    public static final double RAIN_BASE_DAMAGE = 2.0D;
    public static final int RAIN_COOLDOWN = 280;
    public static final int RAIN_COOLDOWN_STAGE2 = 320;
    /** A frost arrow's hit: Slowness I and the frosted screen of powder snow (never long enough to freeze). */
    public static final int FROST_SLOW_TICKS = 60;
    public static final int FROST_FREEZE_TICKS = 100;

    public static final double CIRCLE_RADIUS = 10.0D;
    /** The circling hunt lasts two of his saddle shots (four arrows). */
    public static final int CIRCLE_SHOTS_ANIMS = 2;
    public static final double CIRCLE_SPEED = 1.4D;
    /** It aims for a point this far round the circle ahead of it (radians), so it keeps going round. */
    public static final double CIRCLE_LEAD = 0.5D;
    /** His arrows: speed 3 (a fully drawn bow) and the base damage that makes a hit 5 (2.5 hearts). */
    public static final double SHOT_BASE_DAMAGE = 1.67D;
    public static final int CIRCLE_COOLDOWN = 240;
    /** The circling hunt: the moose gallops round its target this long (he shoots from its back meanwhile, on his own
     * timer: it keeps the target on his left). */
    public static final int CIRCLE_TICKS = 120;
    /** His bow from the saddle: two arrows a time ({@code ride_shoot}), at a target this far round his left (degrees
     * from straight out to his left: the moose's antlers are in front of him), between these distances. */
    public static final double SADDLE_SHOT_HALF_ANGLE = 60.0D;
    public static final double SADDLE_SHOT_MIN = 4.0D;
    public static final double SADDLE_SHOT_MAX = 28.0D;
    public static final int SADDLE_SHOT_COOLDOWN = 30;

    public static final float HORN_DAMAGE = 2.0F;                // 1 heart
    public static final double HORN_LENGTH = 10.0D;
    public static final double HORN_HALF_ANGLE = 30.0D;
    public static final double HORN_PUSH = 2.0D;                 // about 8 blocks
    public static final double HORN_PUSH_UP = 0.35D;
    public static final int HORN_SLOW_TICKS = 80;
    public static final int HORN_SLOW_LEVEL = 1;                 // Slowness II
    public static final int HORN_COOLDOWN = 360;

    public static final float BOLA_DAMAGE = 2.0F;                // 1 heart
    public static final double BOLA_MIN = 5.0D;
    public static final double BOLA_MAX = 18.0D;
    public static final float BOLA_SPEED = 1.6F;
    public static final int BOLA_SNARE_TICKS = 40;               // held still 2 s
    public static final int BOLA_SLOW_TICKS = 100;               // then Slowness I 5 s
    public static final int BOLA_COOLDOWN = 300;

    /** Ride Off: the moose gallops out to this far from its target (or for at most {@link #RIDE_OFF_TICKS}). */
    public static final double RIDE_OFF_MIN = 10.0D;
    public static final double RIDE_OFF_MAX = 14.0D;
    public static final int RIDE_OFF_TICKS = 60;
    /** Ride In / Run In: closing in, for at most this long before choosing again. */
    public static final int RUN_IN_TICKS = 40;

    /**
     * The Wild Hunt runs on two timers at once (the user, October 23, 2026: "the hunter needs to be shooting or hitting
     * me with a spear while the moose attacks"): the moose's moves and his. Until then one table ran both, the user's of
     * October 20 (close: thrust 50, horn 20, ride off 20, bola 10; mid: circle 30, charge 30, bola 20, rain 20; far:
     * charge 40, rain 40, circle 20; very far: rain 60, ride in 40), and he sat still while it charged.
     * <p>
     * The moose in the Wild Hunt, with all its attacks (the user's answer: "yes, all its attacks"): close / mid / far /
     * very far ({@link #WILD_CLOSE}...), each {move: weight}; the back kick is made at once whenever a foe is behind it.
     */
    public static final Object[][] WILD_MOOSE_CHANCES = {
            {"ram", 30, "toss", 20, "stomp", 20, "bellow", 10, "ride_off", 20},
            {"charge", 30, "circle", 35, "stamp", 15, "bellow", 10, "ride_off", 10},
            {"charge", 40, "circle", 40, "stamp", 20},
            {"charge", 40, "ride_in", 60}};
    /** His own moves from the saddle, using what fits where his target is (the user's answer): the spear in reach ahead or
     * on his right, the bow on his left, the bola and the horn ahead, the rain anywhere (a move that doesn't fit drops
     * out and the others share its weight). */
    public static final Object[][] SADDLE_CHANCES = {
            {"thrust", 50, "horn", 25, "shoot", 15, "rain", 10},
            {"shoot", 40, "bola", 25, "rain", 20, "horn", 15},
            {"shoot", 50, "rain", 30, "bola", 20},
            {"rain", 70, "shoot", 30}};

    // ---------------------------------------------------------------- stage 2: the Huntsman on foot

    public static final float COMBO_DAMAGE_1 = 6.0F;
    public static final float COMBO_DAMAGE_2 = 6.0F;
    public static final float COMBO_DAMAGE_3 = 8.0F;
    public static final double SPEAR_REACH = 3.5D;
    public static final double SWEEP_HALF_ANGLE = 70.0D;
    public static final int COMBO_COOLDOWN = 80;
    public static final float THRUST_SWEEP_DAMAGE_1 = 7.0F;
    public static final float THRUST_SWEEP_DAMAGE_2 = 8.0F;
    public static final int THRUST_SWEEP_COOLDOWN = 60;
    public static final float LEAP_DAMAGE = 10.0F;
    public static final double LEAP_MIN = 4.0D;
    public static final double LEAP_MAX = 10.0D;
    public static final double LEAP_SHOCK_RADIUS = 2.5D;
    public static final double LEAP_SHOCK_KNOCK = 0.9D;
    public static final int LEAP_COOLDOWN = 160;
    /** The leap carries him from the crouch's end to the landing. */
    public static final int LEAP_TAKEOFF = 8;
    public static final float SPEAR_THROW_DAMAGE = 9.0F;
    public static final double SPEAR_THROW_MIN = 6.0D;
    public static final double SPEAR_THROW_MAX = 22.0D;
    public static final float SPEAR_THROW_SPEED = 2.4F;
    public static final int SPEAR_THROW_COOLDOWN = 200;
    /** A spear that hasn't come back by then just comes back to his hand. */
    public static final int SPEAR_RETURN_TICKS = 80;
    public static final float AIMED_DAMAGE = 10.0F;
    public static final double AIMED_BASE_DAMAGE = 3.34D;        // 10 at speed 3
    public static final double AIMED_MIN = 8.0D;
    public static final double AIMED_MAX = 35.0D;
    public static final int AIMED_FREEZE_TICKS = 30;             // frozen 1.5 s
    public static final int AIMED_COOLDOWN = 240;
    public static final double BACKSTEP_BASE_DAMAGE = 2.0D;      // 6 at speed 3 (3 hearts)
    /** The quick shot (the user, October 23, 2026: on foot at range "he keeps shooting"): what he shoots whenever his
     * bigger moves are cooling down, at a foe in sight between these distances; one plain arrow. */
    public static final double QUICK_SHOT_BASE_DAMAGE = 1.34D;   // 4 at speed 3 (2 hearts)
    public static final double QUICK_SHOT_MIN = 6.0D;
    public static final double QUICK_SHOT_MAX = 32.0D;
    public static final int QUICK_SHOT_COOLDOWN = 20;
    public static final int BACKSTEP_COOLDOWN = 120;

    /** The Huntsman on foot's table (the user's, with the Horn of the Hunt fitted in: "he should blow the horn ...
     * then on stage 2"). Since October 22, 2026 he never runs in himself (the user: "the moose is the close range
     * attacker, the hunter long range"): the middle row's Run In is a Fall Back, and with nothing to make he falls back
     * (close), keeps his range moving round his target (middle, far) or closes in only to bow range (very far). */
    public static final Object[][] FOOT_CHANCES = {
            {"combo", 35, "thrust_sweep", 30, "backstep", 20, "horn", 15},
            {"leap", 30, "throw_spear", 25, "bola", 20, "horn", 10, "fall_back", 15},
            {"aimed", 35, "rain", 30, "throw_spear", 20, "bola", 15},
            {"aimed", 50, "rain", 50}};
    /** Falling back: he runs out to this far from his target (or for at most {@link #FALL_BACK_TICKS}). */
    public static final double FALL_BACK_TO = 18.0D;
    public static final int FALL_BACK_TICKS = 50;
    /** Keeping his range: he walks round his target this long (a speed modifier: his walk's own pace). */
    public static final int CIRCLE_ON_FOOT_TICKS = 30;
    public static final double HUNTSMAN_WALK = 0.72D;
    /** Very far off he closes in, but only to this far (bow range). */
    public static final double CLOSE_IN_TO = 22.0D;

    // ---------------------------------------------------------------- stage 2: the Alpha Moose

    public static final float RAM_DAMAGE = 9.0F;
    public static final double RAM_REACH = 3.0D;
    public static final int RAM_COOLDOWN = 40;
    public static final float TOSS_DAMAGE = 8.0F;
    public static final double TOSS_UP = 1.15D;                  // about 6 blocks up
    public static final int TOSS_COOLDOWN = 140;
    public static final float STOMP_DAMAGE = 10.0F;
    public static final double STOMP_REACH = 2.5D;
    public static final int STOMP_COOLDOWN = 100;
    public static final float BELLOW_DAMAGE = 2.0F;
    public static final double BELLOW_LENGTH = 9.0D;
    public static final double BELLOW_HALF_ANGLE = 35.0D;
    public static final double BELLOW_PUSH = 1.5D;
    public static final int BELLOW_COOLDOWN = 280;
    public static final float STAMP_DAMAGE = 10.0F;
    public static final double STAMP_MIN = 6.0D;
    public static final double STAMP_MAX = 20.0D;
    /** The cracks run out from its hooves at this many blocks a tick, then the spikes burst up round the end. */
    public static final double STAMP_CRACK_SPEED = 0.6D;
    public static final double STAMP_RADIUS = 1.5D;
    public static final double STAMP_LAUNCH = 0.6D;
    /** The spikes stand this long. */
    public static final int STAMP_SPIKE_TICKS = 30;
    public static final int STAMP_COOLDOWN = 240;

    /** The Alpha Moose's table (the user's). */
    public static final Object[][] MOOSE_CHANCES = {
            {"ram", 35, "toss", 25, "stomp", 25, "bellow", 15},
            {"charge", 45, "stamp", 30, "bellow", 25},
            {"charge", 60, "stamp", 40},
            {"charge", 70, "run_in", 30}};
}
