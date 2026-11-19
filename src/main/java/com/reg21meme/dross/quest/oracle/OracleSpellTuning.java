package com.reg21meme.dross.quest.oracle;

/**
 * Every number of the Blind Oracle's spell (QUEST_PLAN.md, "The Oracle's spell"; the user, November 18, 2026: "him
 * lifting his hand, a giant spell circle being cast, then a giant laser going in the air, and then you hear an explosion
 * in the background ... make it cool, a cool destruction thing"; then "give more time to that animation as well, make it
 * last a little longer and more grand with the sounds ... the timing before the spell as well, there needs to be a break
 * for the people to read the message he says before he uses the spell"). Times are in ticks (20 a second) from the
 * moment the spell starts, after {@link #PAUSE}; distances in blocks. The server uses {@link #PAUSE} and
 * {@link #DURATION} (he stands still and says nothing meanwhile); the rest is how {@code quest/client/OracleSpellEffects}
 * draws it.
 */
public final class OracleSpellTuning
{
    private OracleSpellTuning() {}

    // ------------------------------------------------------------------ When

    /** After his last thanks line he waits this long before casting, so it can be read (3 seconds). */
    public static final int PAUSE = 60;
    /** The whole spell (about 12 seconds). He stands still for it, and for the pause before it. */
    public static final int DURATION = 240;
    /** His arms are up over his head from here... */
    public static final int ARMS_UP = 22;
    /** ...until here, then back down by {@link #ARMS_DOWN_END}. */
    public static final int ARMS_DOWN = 205;
    public static final int ARMS_DOWN_END = 225;
    /** The rune circle opens over this long, from nothing to its full size. */
    public static final int CIRCLE_GROW = 40;
    /** The great outer ring opens from here, over {@link #OUTER_GROW}. */
    public static final int OUTER_START = 30;
    public static final int OUTER_GROW = 30;
    /** The gathering: runes spiral in and up round him, the light rising off the rim thickens, until the beam. */
    public static final int GATHER_START = 20;
    /** The circle fades out from here to the end. */
    public static final int CIRCLE_FADE = 200;
    /** The beam bursts up out of his hands (and a shock ring rolls out over the ground). */
    public static final int BEAM_START = 66;
    /** ...over this long. */
    public static final int BEAM_GROW = 8;
    /** The ground's shock ring at the beam's start: how long it rolls out. */
    public static final int GROUND_RING_TICKS = 26;
    /** Three thinner beams rise from the circle's rim and join the great one from here... */
    public static final int SATELLITE_START = 80;
    /** The beam bends out from the sky towards the barrier it breaks (the lance)... */
    public static final int LANCE_START = 120;
    /** ...reaching it over this long. */
    public static final int LANCE_GROW = 8;
    /** It strikes: the flash, the shock rings and a pillar of light far off. */
    public static final int STRIKE = 128;
    /** The boom is heard this long after the flash (it's far off), and its echo this long after that. */
    public static final int BOOM_DELAY = 18;
    public static final int ECHO_DELAY = 22;
    /** The beams and the lance shatter into falling shards. */
    public static final int SHATTER = 182;
    /** ...gone over this long. */
    public static final int SHATTER_GROW = 8;

    // ------------------------------------------------------------------ Where and how big

    /** The rune circle's radius at its full size (blocks): about 13 across. */
    public static final float CIRCLE_RADIUS = 6.5F;
    /** The small inner ring's radius, as a share of the circle's. */
    public static final float RING_SHARE = 0.6F;
    /** The great outer ring's radius, as a share of the circle's. */
    public static final float OUTER_SHARE = 1.7F;
    /** How fast the circle and the rings turn (degrees a tick; the inner ring turns the other way). */
    public static final float CIRCLE_SPIN = 1.2F;
    public static final float RING_SPIN = -2.4F;
    public static final float OUTER_SPIN = 0.5F;
    /** The beam leaves his hands this high over his feet. */
    public static final float HANDS_HEIGHT = 2.35F;
    /** The beam's height into the sky. */
    public static final float BEAM_HEIGHT = 90.0F;
    /** The beam's core's half-width, and its glow's (blocks). */
    public static final float BEAM_CORE = 0.6F;
    public static final float BEAM_GLOW = 2.2F;
    /** How much the beam's width pulses (a share), and how fast (radians a tick). */
    public static final float BEAM_PULSE = 0.2F;
    public static final float BEAM_PULSE_SPEED = 0.6F;
    /** The three thin beams from the rim: their half-width, and where they join the great beam (blocks over his feet). */
    public static final float SATELLITE_WIDTH = 0.5F;
    public static final float SATELLITE_JOIN = 34.0F;
    /** How fast they wind round him (degrees a tick). */
    public static final float SATELLITE_SPIN = 1.6F;
    /** The lance's core and glow half-widths. */
    public static final float LANCE_CORE = 0.5F;
    public static final float LANCE_GLOW = 1.8F;
    /**
     * The strike is drawn this far out towards the barrier (or nearer, if the barrier is nearer), at least this far, and
     * this high over his feet, so it's seen over the trees.
     */
    public static final float STRIKE_DISTANCE = 100.0F;
    public static final float STRIKE_MIN_DISTANCE = 50.0F;
    public static final float STRIKE_HEIGHT = 38.0F;
    /** The flash's size (blocks across) at the strike, and as it swells. */
    public static final float FLASH_SIZE = 14.0F;
    public static final float FLASH_GROWTH = 1.8F;
    /** How long the flash lasts. */
    public static final int FLASH_TICKS = 40;
    /** The shock rings: from this size, growing this much a tick, for this long. */
    public static final float RING_START_SIZE = 8.0F;
    public static final float RING_GROWTH = 3.0F;
    public static final int RING_TICKS = 46;
    /** The pillar of light at the strike: how far down it reaches, and how long it stands. */
    public static final float PILLAR_DEPTH = 70.0F;
    public static final int PILLAR_TICKS = 50;
    /** The ground's shock ring at the beam's start: how far it rolls out. */
    public static final float GROUND_RING_REACH = 22.0F;

    // ------------------------------------------------------------------ Feel

    /** The ground shakes (the camera wobbles) for players this near him... */
    public static final double SHAKE_RANGE = 32.0D;
    /** ...a little while it gathers, more while the beam stands (degrees)... */
    public static final float SHAKE_GATHER = 0.15F;
    public static final float SHAKE_BEAM = 0.4F;
    /** ...and up to this much at the beam's burst and the strike, dying out over {@link #SHAKE_STRIKE_TICKS}. */
    public static final float SHAKE_STRIKE = 1.6F;
    public static final int SHAKE_STRIKE_TICKS = 22;

    /** Rising streaks off the circle's rim a tick (more while it gathers), and runes a tick, while it stands. */
    public static final float RIM_STREAKS = 2.0F;
    public static final float GATHER_STREAKS = 5.0F;
    public static final float CIRCLE_RUNES = 0.4F;
    /** Runes spiralling in a tick while it gathers. */
    public static final float GATHER_RUNES = 2.5F;
    /** Shards a tick falling from the beams as they shatter. */
    public static final int SHATTER_SPARKS = 34;
}
