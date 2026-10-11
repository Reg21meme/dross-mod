package com.reg21meme.dross.boss.golem;

/**
 * What a Cinder Colossus is doing, which picks the animation its body plays (see {@link CinderColossus}).
 * The animation names are in each design's {@code animations/entity/cinder_colossus/<id>.animation.json}.
 */
public enum GolemAction
{
    /** Breathing and looking around (loops). */
    IDLE("idle"),
    /** Knuckle-walking (loops). The showcase golems walk on the spot. */
    WALK("walk"),
    /** The volcano on its back erupts and lava pours down over its shell (plays once). Afterwards it's ERUPTED. */
    ERUPT("erupt"),
    /** Bursting out of its shell (plays once). Afterwards it's in its lava core form. */
    BREAK_SHELL("break_shell"),
    /** Dying: it sinks onto its knuckles while it cools into obsidian (plays once). Afterwards it's a statue. */
    DEATH("death"),
    /** The death's last pose, cooled: an obsidian statue. */
    STATUE("statue");

    private final String animation;

    GolemAction(String animation)
    {
        this.animation = animation;
    }

    /** The animation's full name in the animation file. */
    public String animationName()
    {
        return "animation.cinder_colossus." + this.animation;
    }

    /** True for the actions that end in a still pose: the death and the statue. */
    public boolean isDead()
    {
        return this == DEATH || this == STATUE;
    }

    public static GolemAction byName(String name)
    {
        for (GolemAction action : values())
        {
            if (action.name().equalsIgnoreCase(name))
            {
                return action;
            }
        }
        return IDLE;
    }
}
