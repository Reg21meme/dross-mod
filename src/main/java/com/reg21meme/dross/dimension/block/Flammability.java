package com.reg21meme.dross.dimension.block;

/**
 * How easily the Dross's plants and wood catch fire and burn away, as labeled constants.
 *
 * <p>Minecraft keeps these numbers for its own blocks in {@code FireBlock}; mod blocks give them through Forge's
 * {@code getFlammability} (how quickly fire destroys the block: higher burns away sooner) and
 * {@code getFireSpreadSpeed} (how readily fire jumps onto it: higher catches sooner). The values below are vanilla's,
 * so silverwood burns exactly like oak, Stormvine like vines, and the plants like grass and flowers.
 */
public final class Flammability
{
    /** Logs and wood (vanilla oak log: spreads 5, burns 5). */
    public static final int LOG_SPREAD = 5;
    public static final int LOG_BURN = 5;

    /** Planks (vanilla oak planks: spreads 5, burns 20). */
    public static final int PLANKS_SPREAD = 5;
    public static final int PLANKS_BURN = 20;

    /** Leaves (vanilla oak leaves: spreads 30, burns 60). */
    public static final int LEAVES_SPREAD = 30;
    public static final int LEAVES_BURN = 60;

    /** Stormvine (vanilla vine: spreads 15, burns 100). */
    public static final int VINE_SPREAD = 15;
    public static final int VINE_BURN = 100;

    /** Small plants: Glimmer Fern and Wispbloom, and the Frostbloom and Crag Thistle (vanilla fern and flowers: spread 60, burn 100). */
    public static final int PLANT_SPREAD = 60;
    public static final int PLANT_BURN = 100;

    /** Frostbitten logs: dead wood crusted with rime, so it catches and burns much more slowly than oak (5, 5). */
    public static final int FROSTBITTEN_LOG_SPREAD = 2;
    public static final int FROSTBITTEN_LOG_BURN = 2;

    /** Storm Lichen (vanilla glow lichen: spreads 15, burns 100). */
    public static final int LICHEN_SPREAD = 15;
    public static final int LICHEN_BURN = 100;

    // Never burn (no numbers on purpose): everything in the Cinder Wastes (Ash, Ash Layer, Charred Log, Emberweed,
    // Cinder Vent), since lava pools lie next to it, and everything in the Nether Scar (Scarred Netherrack, Scar Roots,
    // Soulwick, Scar Vent), where no fire is wanted at all. Blocks that don't override isFlammable never burn.

    private Flammability() {}
}
