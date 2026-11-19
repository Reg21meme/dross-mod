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
 *
 * <p>Two spells (the user, November 19, 2026: "the big spell, the one we have right now, and a medium spell, a smaller
 * version where it's just one circle and a beam shooting the shield around the nests, while the [lands] require a big
 * spell; the medium spell is quick, nothing too complex"): the <b>big</b> one when a hand-in opens a land, the
 * <b>medium</b> one ({@code MEDIUM_*}) when it opens a boss's home.</p>
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
    /** Eight lances shoot out of the beam, one each way round the compass (the user, November 19, 2026: "shoot like 7
     * more beams in each major cardinal and sub-cardinal direction"), down into the ring of barrier round him... */
    public static final int LANCE_START = 120;
    /** ...reaching it over this long. */
    public static final int LANCE_GROW = 8;
    /** They strike it: a flash and a shock ring where each hits, and the ring of barrier shatters (it stood up
     * {@code LandWardFall.SHATTER_AT} ticks before, so it breaks at this moment). */
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
    /** The lances' core and glow half-widths. */
    public static final float LANCE_CORE = 0.45F;
    public static final float LANCE_GLOW = 1.5F;
    /** How many lances (round the compass, the first to the east), where they leave the beam (blocks over his hands)... */
    public static final int LANCES = 8;
    public static final float LANCE_FROM = 30.0F;
    /** ...and where they hit the ring of barrier: its radius round him, and how high over his feet. */
    public static final float BARRIER_RADIUS = 20.0F;
    public static final float LANCE_HIT_HEIGHT = 7.0F;
    /** Where each lance hits: a flash this big (blocks across), swelling this much a tick, for this long... */
    public static final float FLASH_SIZE = 5.0F;
    public static final float FLASH_GROWTH = 0.6F;
    public static final int FLASH_TICKS = 24;
    /** ...and a shock ring from this size, growing this much a tick, for this long. */
    public static final float RING_START_SIZE = 3.0F;
    public static final float RING_GROWTH = 1.2F;
    public static final int RING_TICKS = 30;
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

    // ------------------------------------------------------------------ The medium spell (a boss's home)

    /** The whole medium spell (5 seconds); he stands still for it, and for the pause before it. */
    public static final int MEDIUM_DURATION = 110;
    /** His arms up by here, down from {@link #MEDIUM_ARMS_DOWN} to {@link #MEDIUM_ARMS_DOWN_END}. */
    public static final int MEDIUM_ARMS_UP = 10;
    public static final int MEDIUM_ARMS_DOWN = 88;
    public static final int MEDIUM_ARMS_DOWN_END = 102;
    /** Its one circle: opening over this long, this big (blocks, its radius), fading from {@link #MEDIUM_CIRCLE_FADE}. */
    public static final int MEDIUM_CIRCLE_GROW = 14;
    public static final float MEDIUM_CIRCLE_RADIUS = 3.5F;
    public static final int MEDIUM_CIRCLE_FADE = 86;
    /** The beam shoots straight up out of his hands into the sky from here (the user, November 19, 2026: "just shoot
     * the beam of light into the sky instead of at an angle"), reaching its top over this long; as it does, the small
     * ring of barrier round him shatters (it stood up {@code LandWardFall.SHATTER_AT} ticks before). */
    public static final int MEDIUM_BEAM_START = 40;
    public static final int MEDIUM_BEAM_GROW = 5;
    /** How high it goes over his hands, and the ring of barrier's radius round him. */
    public static final float MEDIUM_BEAM_HEIGHT = 40.0F;
    public static final float MEDIUM_BARRIER_RADIUS = 7.5F;
    /** Its core's and glow's half-widths. */
    public static final float MEDIUM_BEAM_CORE = 0.35F;
    public static final float MEDIUM_BEAM_GLOW = 1.3F;
    /** It breaks off from here, gone over {@link #MEDIUM_BEAM_FADE}. */
    public static final int MEDIUM_BEAM_END = 80;
    public static final int MEDIUM_BEAM_FADE = 8;
    /** At its top as it gets there: a flash this big and a ring this fast, for this long. */
    public static final float MEDIUM_FLASH_SIZE = 6.0F;
    public static final int MEDIUM_FLASH_TICKS = 18;
    public static final float MEDIUM_RING_GROWTH = 1.4F;
    public static final int MEDIUM_RING_TICKS = 24;
    /** The ground shakes a little as it fires (degrees, dying out over {@link #SHAKE_STRIKE_TICKS}). */
    public static final float MEDIUM_SHAKE = 0.6F;
    /** Light rising off its circle a tick, and shards at its burst and where it hits. */
    public static final float MEDIUM_RIM_STREAKS = 1.2F;
    public static final int MEDIUM_BURST_SHARDS = 16;
    public static final int MEDIUM_HIT_SHARDS = 24;

    /** How long the spell runs (the server keeps him still for it). */
    public static int duration(boolean big)
    {
        return big ? DURATION : MEDIUM_DURATION;
    }
}
