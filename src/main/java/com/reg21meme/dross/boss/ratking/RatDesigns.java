package com.reg21meme.dross.boss.ratking;

import java.util.List;

/**
 * The Rat King's rats, written by {@code tools/ratking/build.py} (don't edit by hand: change
 * {@code tools/ratking/ratdesigns/rats.py} and run the build again): the regular rats ({@code dross:rat}) and the
 * enraged rats, a separate scarier breed ({@code dross:enraged_rat}). The renderer finds each design's files by its
 * {@link Design#id()} under {@code entity/rat/}.
 */
public final class RatDesigns
{
    /**
     * One rat design.
     *
     * @param number   1 to 4 within its kind (the showcase's order)
     * @param label    what its sign says after "No. " (R1-R4, E1-E4)
     * @param id       its files' name
     * @param name     its working name
     * @param mood     a few words about it
     * @param enraged  true for the enraged breed
     * @param hasGlow  true if its {@code <id>_glow.png} has anything in it (glowing eyes, boils)
     * @param runBlocks how far it would travel in one loop of its run animation, in blocks
     * @param runTicks  the run animation's length
     */
    public record Design(int number, String label, String id, String name, String mood, boolean enraged,
                         boolean hasGlow, float runBlocks, int runTicks)
    {
    }

    public static final List<Design> REGULAR = List.of(
            new Design(1, "R3", "plague", "Plague Rat", "mangy, sickly", false, false, 0.675F, 8));

    public static final List<Design> ENRAGED = List.of(
            new Design(1, "E3", "ravager", "Plague Ravager", "glowing boils", true, true, 0.900F, 7));

    /** The design with this number (1-4) of a kind, or that kind's first one. */
    public static Design byNumber(boolean enraged, int number)
    {
        for (Design d : enraged ? ENRAGED : REGULAR)
        {
            if (d.number() == number)
            {
                return d;
            }
        }
        return (enraged ? ENRAGED : REGULAR).get(0);
    }

    private RatDesigns() {}
}
