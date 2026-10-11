package com.reg21meme.dross.boss.golem;

/**
 * What a Cinder Colossus is doing, which picks the animation its body plays (see {@link CinderColossus}).
 * The animation names are in each design's {@code animations/entity/cinder_colossus/<id>.animation.json}.
 * The fight's moves ({@link #isAttack}) are started by its AI ({@link ColossusAttackGoal}).
 */
public enum GolemAction
{
    /** Breathing and looking around, or knuckle-walking while it moves (loops). */
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
    STATUE("statue"),
    /** The fight: a backhand swipe of its right fist (its melee hit). */
    SWIPE("swipe"),
    /** The fight: it tears a rock out of the ground and throws it. */
    THROW("throw"),
    /** The fight: both fists raised high, then smashed into the ground (a shockwave). */
    SLAM("slam"),
    /** The fight: head down, a fist raking the ground, then a gallop in a straight line (charge_start, then charge). */
    CHARGE("charge"),
    /** The fight: its volcanoes spit lava blobs that make pools where they land. */
    LOB("lob"),
    /** The fight: it pounds the ground three times and a minion rises at each pound. */
    SUMMON("summon");

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

    /** True for the fight's moves (the attacks its AI starts). */
    public boolean isAttack()
    {
        return this.ordinal() >= SWIPE.ordinal();
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
