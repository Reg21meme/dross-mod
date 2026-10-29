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
     */
    public record Kind(float health, double armor, double cruise, double fast, int pause, double cooldowns, double orbit,
                       double orbitHeight, double leash, int tornadoes, float tornadoSize, int stuck, float width, float height)
    {
    }

    public static final Kind ADULT = new Kind(ADULT_HEALTH, 10.0D, 0.55D, 1.4D, 30, 1.0D, 26.0D, 18.0D, 100.0D, 2, 1.0F, 60,
            5.0F, 11.0F);
    /** The young: 2.5 times as fast (the user, October 29), almost no rest, near its nest. */
    public static final Kind YOUNG = new Kind(YOUNG_HEALTH, 6.0D, 0.55D * 2.5D, 1.4D * 2.5D, 4, 0.4D, 16.0D, 10.0D, 40.0D, 2,
            0.65F, 24, 3.5F, 9.5F);

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

    /** Dive bomb: 7 hearts within this many blocks of where it crashes, everything thrown out and up. */
    public static final float DIVE_DAMAGE = 14.0F;
    public static final double DIVE_RADIUS = 5.0D;
    public static final double DIVE_THROW = 1.1D;
    public static final double DIVE_LIFT = 0.7D;
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

    /** Talon grab: 3 hearts, then it carries you this high and drops you, anywhere (its crater's lava included). */
    public static final float GRAB_DAMAGE = 6.0F;
    public static final double GRAB_REACH = 4.5D;
    public static final double GRAB_CARRY_HEIGHT = 20.0D;
    public static final int GRAB_CARRY_TICKS = 50;
    public static final int GRAB_COOLDOWN = 280;

    /** Beak snap (on the ground): 7 hearts in front of it. */
    public static final float SNAP_DAMAGE = 14.0F;
    /** Its bill reaches from this far in front of it to this far, this far either side (blocks; the adult's). */
    public static final double SNAP_FROM = 3.0D;
    public static final double SNAP_TO = 15.0D;
    public static final double SNAP_SIDE = 4.0D;
    public static final int SNAP_COOLDOWN = 30;

    /** Wing buffet: 4 hearts in a cone in front, throwing you about 20 blocks (the user). */
    public static final float BUFFET_DAMAGE = 8.0F;
    public static final double BUFFET_RANGE = 15.0D;
    public static final double BUFFET_CONE = 75.0D;
    public static final double THROW_20 = 2.2D;
    public static final double THROW_20_LIFT = 0.55D;
    public static final int BUFFET_COOLDOWN = 120;

    /** Screech: 1 heart, slowness, and everyone within this many blocks thrown about 20 blocks (the user). */
    public static final float SCREECH_DAMAGE = 2.0F;
    public static final double SCREECH_RANGE = 20.0D;
    public static final int SCREECH_SLOWNESS_TICKS = 60;
    public static final int SCREECH_COOLDOWN = 300;

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

    /** Which moves it picks, by how far its target is (along the ground): close, mid, far; and on the ground. */
    public static final double CLOSE = 16.0D;
    public static final double MID = 40.0D;

    // ---- Its death and the egg ----

    /** The adult's death: its egg cracks open this far into it (when its body breaks apart, 4.2 s)... */
    public static final int HATCH_TICKS = 84;
    /** ...and it vanishes this many ticks after it died. */
    public static final int ADULT_REMOVE_TICKS = 200;
    /** The young stays an obsidian statue this long after it died. */
    public static final int YOUNG_STATUE_TICKS = 2400;
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
