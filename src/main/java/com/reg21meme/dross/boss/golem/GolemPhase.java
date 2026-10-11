package com.reg21meme.dross.boss.golem;

/**
 * Which of its three looks a Cinder Colossus has (see {@link CinderColossus}):
 * <ol>
 *   <li>{@link #DORMANT}: its rock shell, a few dim cracks, the volcano on its back only smoking (the start of the
 *       first stage);</li>
 *   <li>{@link #ERUPTED}: still in its shell, but the volcano has erupted and lava has poured down over it (halfway
 *       through the first stage);</li>
 *   <li>{@link #CORE}: the shell has broken off: a body of lava, its volcano bigger and erupting all the time (the
 *       second stage).</li>
 * </ol>
 */
public enum GolemPhase
{
    DORMANT,
    ERUPTED,
    CORE;

    /** True while it still wears its rock shell. */
    public boolean hasShell()
    {
        return this != CORE;
    }

    public static GolemPhase byName(String name)
    {
        for (GolemPhase phase : values())
        {
            if (phase.name().equalsIgnoreCase(name))
            {
                return phase;
            }
        }
        return DORMANT;
    }
}
