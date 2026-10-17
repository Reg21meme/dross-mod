package com.reg21meme.dross.boss.ratking;

import java.util.List;

/**
 * The Rat King's design candidates, written by {@code tools/ratking/build.py} (don't edit by hand: change the designs
 * in {@code tools/ratking/ratdesigns/} and run the build again). The showcase spawns one Rat King per design; the
 * renderer finds each design's files by its {@link Design#id()}.
 * <p>
 * Times are ticks from the start of each animation (GeckoLib starts it {@code RatKing.ANIMATION_BLEND_TICKS} after the
 * action does); the mod times its particles, effects and sounds to them: the Zzz and snores while asleep, the startle
 * and the yawn, the dirt flying while he digs and dives and when he bursts out, his eyes turning red, the train whistle
 * and the steam out of his ears in the rage, the thud, the staff's clatter and the crown's clink in the death; and in
 * the fight, the sweep's hit, the moment (and the spot) his cast lets the poison ball go, and his spear charge's dash
 * (its speed and reach).
 */
public final class RatKingDesigns
{
    /**
     * One design.
     *
     * @param number      the showcase's order (1, 2, 3 ...)
     * @param label       what its sign says after "No. "
     * @param id          its files' name ({@code geo/entity/rat_king/<id>.geo.json} ...)
     * @param name        its working name (the sign's second line)
     * @param mood        a few words about it (the sign's third line)
     * @param curledSleep true: he sleeps curled up on the ground (his staff beside him); false: slumped on his staff
     * @param hasGlow     true if its {@code <id>_glow.png} has anything in it (an orb, gems, candle flames, a censer)
     * @param slamForward where his staff's foot strikes the ground in the rage: blocks in front of him...
     * @param slamRight   ...and to his right
     * @param runBlocks   how far one loop of his run would carry him (blocks): play it at
     *                    (blocks a tick he moves) / (runBlocks / runTicks) times its speed so his feet don't slide
     * @param runTicks    the run loop's length
     * @param sweepTicks  the sweep's length (his close-range staff blow)
     * @param sweepHit    when the staff passes his front: the moment it hits
     * @param sweepReach  how far from his middle the staff's orb reaches at the hit (blocks)
     * @param castTicks   the cast's length (he launches a poison ball)
     * @param castRelease when the ball leaves the staff's orb
     * @param castForward where it leaves from (the orb at the release): blocks in front of him...
     * @param castUp      ...above his feet...
     * @param castRight   ...and to his right
     * @param popupHoleForward how far in front of his feet (blocks) the hole he pops up out of is: right under him
     *                    (under his head as he stands straight up, so he comes straight up out of it)
     * @param chargeStartTicks the spear charge's wind-up animation (it follows GeckoLib's blend; the mod's wind-up is
     *                    RatKingTuning.CHARGE_WINDUP_TICKS, from the start of the move)
     * @param chargeBlocks how far one loop of his dash would carry him (blocks): play it at
     *                    (blocks a tick he moves) / (chargeBlocks / chargeTicks) times its speed so his feet don't slide
     * @param chargeTicks the dash loop's length
     * @param chargeReach how far in front of his middle (blocks) the staff's orb leads in the dash: the charge hits
     *                    what's within it
     * @param chargeSkidTicks the skid when the charge misses (it follows GeckoLib's blend; the mod's recovery is
     *                    RatKingTuning.CHARGE_RECOVER_TICKS)
     */
    public record Design(int number, String label, String id, String name, String mood, boolean curledSleep,
                         boolean hasGlow, int wakeTicks, int wakeStartle, int burrowTicks, int digFrom, int digTo,
                         int dive, int popupTicks, int popupPeek, int popupBurst, int popupLand, int rageTicks,
                         int rageEyes, List<Integer> rageSteam, int rageSlam, List<Integer> rageStomps, int deathTicks,
                         int deathFall, int deathTongue, int deathXEyes, float walkBlocks, int walkTicks,
                         int sleepTicks, int wakeYawn, int wakeGrab, int rageScreech, int deathClatter,
                         int deathCrown, float slamForward, float slamRight, float runBlocks, int runTicks,
                         int sweepTicks, int sweepHit, float sweepReach, int castTicks, int castRelease,
                         float castForward, float castUp, float castRight, float popupHoleForward,
                         int chargeStartTicks, float chargeBlocks, int chargeTicks, float chargeReach,
                         int chargeSkidTicks)
    {
    }

    public static final List<Design> ALL = List.of(
            new Design(1, "1C", "gnawrick", "Gnawrick", "broad shoulders", true, true,
                    72, 9, 56, 11, 27, 29,
                    52, 11, 25, 32, 74, 18, List.of(23, 31, 39, 47), 57, List.of(11, 17, 22, 27, 31, 34),
                    72, 32, 37, 40, 0.812F, 32,
                    80, 32, 44, 40, 33, 42, 1.040F, 0.627F,
                    2.625F, 8, 18, 9, 4.050F, 22, 15,
                    2.902F, 2.284F, 0.371F, 0.0625F,
                    4, 2.700F, 6, 3.791F, 6));

    /** How far in front of his feet (blocks) the dirt hole he digs and dives into is (his mound while he tunnels). */
    public static final float HOLE_FORWARD = 0.8750F;

    /** The design with this number, or the first one. */
    public static Design byNumber(int number)
    {
        for (Design d : ALL)
        {
            if (d.number() == number)
            {
                return d;
            }
        }
        return ALL.get(0);
    }

    private RatKingDesigns() {}
}
