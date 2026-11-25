package com.reg21meme.dross.boss.knight.ruin;

/**
 * A ruin's one tidy-up as it comes into view, a step a server tick ({@link KnightRuinKeeper}): the Overworld's
 * ({@link KnightRuinTidy}: what grew into it felled) or the Nether's ({@link NetherRuinTidy}: fungi, basalt, lava).
 */
interface RuinTidy
{
    /** A step of it (a few tens of milliseconds at most); true once it's all done. */
    boolean step();
}
