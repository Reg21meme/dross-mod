package com.reg21meme.dross.boss.frostfolk;

/** Every number of the Frostfolk ({@link Frostfolk}), in one place. */
public final class FolkTuning
{
    // ---------------------------------------------------------------- their size and pace

    /** Their speed attribute: warriors, workers, the elder. */
    public static final double WARRIOR_SPEED = 0.28D;
    public static final double WORKER_SPEED = 0.25D;
    public static final double ELDER_SPEED = 0.22D;
    /** They go after what they fight this far, and wander about at this share of their speed. */
    public static final double FOLLOW_RANGE = 32.0D;
    public static final double STROLL_SPEED = 0.6D;
    /** Above this ground speed (blocks a second) they run, below it they walk. */
    public static final double RUN_ABOVE = 2.6D;
    /** A village's Frostfolk wanders within this many blocks of where the village put it (its home), and walks back
     * there after a fight; angry, it chases you anywhere. (Spawn-egg ones have no home.) */
    public static final int HOME_RADIUS = 24;
    /** It walks back home at this share of its speed. */
    public static final double HOME_SPEED = 0.8D;

    // ---------------------------------------------------------------- fighting

    /** Ticks between two of their attacks (after the last one's animation). */
    public static final int ATTACK_PAUSE = 12;
    /** The archer: he shoots from this far at most, keeps at least this far off, and waits this long between shots. */
    public static final double BOW_RANGE = 22.0D;
    public static final double BOW_KEEP_OFF = 7.0D;
    public static final int BOW_PAUSE = 24;
    /** His arrow: its speed, how much it wanders, its damage (a skeleton's is 2 to 4 on Normal). */
    public static final float ARROW_SPEED = 1.8F;
    public static final float ARROW_SPREAD = 6.0F;
    public static final double ARROW_DAMAGE = 2.5D;
    /** The horn-blower's horn: once a fight starts it calls everyone this near, giving them Speed I and Strength I for
     * this long, and he blows it again no sooner than this. */
    public static final double HORN_RANGE = 24.0D;
    public static final int HORN_EFFECT_TICKS = 600;
    public static final int HORN_PAUSE = 1200;

    // ---------------------------------------------------------------- the village turning on you

    /** Kill one of them and every one of them this near turns on you, for this long (the user: "as soon as you kill one
     * of them the entire village turns on you ... they remain hostile for, let's say, 10 minutes"). */
    public static final double VILLAGE_RANGE = 64.0D;
    public static final int VILLAGE_ANGER_TICKS = 12_000;
    /** This many of them shout as they turn (the others turn silently). */
    public static final int CRIERS = 3;
    /** Hit one (but not kill it) and it fights back for this long. */
    public static final int HIT_ANGER_TICKS = 600;
    /** The elder: hit him and he's angry at you until you've been this far off for this long (the user: "if you attack
     * him you can leave and it resets his anger after 10 minutes"). */
    public static final double ELDER_NEAR = 48.0D;
    public static final int ELDER_FORGIVE_TICKS = 12_000;
    /** How often their anger is looked at (ticks). */
    public static final int ANGER_CHECK_TICKS = 20;

    // ---------------------------------------------------------------- talking

    /** Lines each says when you talk to it (right-click), and the elder (the one you talk to: the user's). */
    public static final int FOLK_LINES = 3;
    public static final int ELDER_LINES = 6;
    /** It won't talk again sooner than this (ticks). */
    public static final int TALK_PAUSE = 30;

    private FolkTuning() {}
}
