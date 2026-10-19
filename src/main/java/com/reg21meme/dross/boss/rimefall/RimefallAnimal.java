package com.reg21meme.dross.boss.rimefall;

import java.util.List;

/**
 * One of Rimefall's animals (the Rimehorn, the moose, the Pond Skater), as the showcase sees it
 * ({@code /dross showcase rimefall_mobs}): a showcase animal stands frozen and plays the animation it's told to.
 */
public interface RimefallAnimal
{
    /** True for a showcase animal (frozen, can't be hurt except by {@code /kill}). */
    boolean isShowcase();

    /** Its animations, by name ({@code idle}, {@code walk}...). */
    List<RimefallAnims.Anim> animations();

    /** A showcase animal plays one of its animations (a loop keeps playing; a move plays once). */
    void playShowcase(String animation);
}
