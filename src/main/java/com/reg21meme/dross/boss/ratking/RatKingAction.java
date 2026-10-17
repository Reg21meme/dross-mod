package com.reg21meme.dross.boss.ratking;

/**
 * What the Rat King's body is doing: each action plays one of his animations ({@code animation.rat_king.<name>}, made
 * by {@code tools/ratking/build.py}). Clients get the action through synced entity data (see {@link RatKing}).
 */
public enum RatKingAction
{
    /** Asleep (slumped on his staff or curled up on the ground), with the Zzz floating up. Loops. */
    SLEEP("sleep", Mode.LOOP),
    /** Startles awake, stretches, grabs his staff, stands; then he idles. */
    WAKE("wake", Mode.ONCE),
    /** Standing: breathing, shifty glances, his own gesture. Loops. */
    IDLE("idle", Mode.LOOP),
    /** Walking (on the spot in the showcase). Loops. */
    WALK("walk", Mode.LOOP),
    /** Digs and dives into the ground; ends underground ({@link #BURROWED}). */
    BURROW("burrow", Mode.HOLD),
    /** Underground: only the dirt mound shows (a Rat King loaded while burrowed). */
    BURROWED("burrowed", Mode.HOLD),
    /** Pops up out of the ground; then he idles. */
    POPUP("popup", Mode.ONCE),
    /** The rage: stomping, red eyes, steam out of his ears; then he idles, still red-eyed. */
    RAGE("rage", Mode.ONCE),
    /** The silly death: flat on his back, tongue out, X eyes; ends lying there ({@link #DEAD}). */
    DEATH("death", Mode.HOLD),
    /** Lying dead (a Rat King loaded after his showcase death). */
    DEAD("dead", Mode.HOLD),
    /** The fight: a fast, low scurry, played as fast as he really moves. Loops. */
    RUN("run", Mode.LOOP),
    /** The fight: his close blow, a flat sweep of the staff round his front; then he idles. */
    SWEEP("sweep", Mode.ONCE),
    /** The fight: he flicks his staff and a poison ball flies from its orb; then he idles. */
    CAST("cast", Mode.ONCE),
    /** The fight, the spear charge's wind-up: crouched low, the staff levelled forward like a spear. Holds its end. */
    CHARGE_START("charge_start", Mode.HOLD),
    /** The spear charge's dash: a fast, low scurry, the staff held out like a lance, played as fast as he dashes. Loops. */
    CHARGE("charge", Mode.LOOP),
    /** The spear charge missed (or hit a wall): he skids to a stop and straightens up into his idle hold. Holds its end. */
    CHARGE_SKID("charge_skid", Mode.HOLD);

    /** How an action's animation plays: looping, once (then the server switches him to idle), or held at its end. */
    public enum Mode
    {
        LOOP, ONCE, HOLD
    }

    private final String suffix;
    private final Mode mode;

    RatKingAction(String suffix, Mode mode)
    {
        this.suffix = suffix;
        this.mode = mode;
    }

    public Mode mode()
    {
        return this.mode;
    }

    /** The animation's name in his {@code .animation.json}. */
    public String animationName()
    {
        return "animation.rat_king." + this.suffix;
    }

    /** True while he lies dead (the death, or after it). */
    public boolean isDead()
    {
        return this == DEATH || this == DEAD;
    }

    /** True for the fight's moves (a sweep, a cast, the spear charge): a save made during one loads as standing. */
    public boolean isMove()
    {
        return this == SWEEP || this == CAST || this.isCharge();
    }

    /** True during the spear charge: the wind-up, the dash or the skid. */
    public boolean isCharge()
    {
        return this == CHARGE_START || this == CHARGE || this == CHARGE_SKID;
    }

    /** True while he's under the ground or diving into it. */
    public boolean isUnderground()
    {
        return this == BURROW || this == BURROWED;
    }

    public static RatKingAction byName(String name)
    {
        for (RatKingAction a : values())
        {
            if (a.name().equals(name))
            {
                return a;
            }
        }
        return IDLE;
    }
}
