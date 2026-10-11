package com.reg21meme.dross.boss.trophy;

/**
 * The look of a Cinder Colossus trophy, one of the boss's own looks (see {@link TrophyModels}, which says which design
 * has which). It sets how much light the block gives off and how often lava drips off it and its craters smoke.
 * How strongly its lava glows is generated with the design ({@link TrophyModels.Model#glow}).
 */
public enum TrophyStyle
{
    /** Its dark rock shell, the lava only seeping through: little light, rare drips. */
    DORMANT(3, 0.06F, 0.25F, 0.0F),
    /** The erupted shell, lava poured over it. */
    ERUPTED(6, 0.15F, 0.12F, 0.03F),
    /** The fight's Cracking stage: plates knocked loose, bright cracks. */
    CRACKING(6, 0.15F, 0.12F, 0.03F),
    /** A body of lava. */
    CORE(8, 0.2F, 0.1F, 0.05F);

    /** Light the block gives off (0 to 15; a torch is 14). */
    public final int light;
    /**
     * Chance that lava drips off it each time the game picks it for a random effect (the client does that for each
     * block near the player a few times a second right next to it, about once a second a few blocks away).
     */
    public final float dripChance;
    /** Chance of a wisp of smoke from a crater then. */
    public final float smokeChance;
    /** Chance of a lava pop from a crater then (just a particle: it never sets anything on fire). */
    public final float popChance;

    TrophyStyle(int light, float dripChance, float smokeChance, float popChance)
    {
        this.light = light;
        this.dripChance = dripChance;
        this.smokeChance = smokeChance;
        this.popChance = popChance;
    }
}
