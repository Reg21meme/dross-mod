package com.reg21meme.dross.villager.hut;

import java.util.List;

/**
 * The ten candidate designs for the Dross trader's hut, numbered 1 to 10. {@code /dross showcase huts} builds them
 * all in a row to compare. Design 10, the Rift Chapel, was chosen: {@code TraderHut.DESIGN} points at
 * {@link #RIFT_CHAPEL}, so it is the hut every new world gets.
 */
public final class HutDesigns
{
    /**
     * The chosen design, the one the trader lives in. It must be declared above {@link #ALL}: static fields start
     * up top to bottom, so declared the other way round, {@code ALL} would be built holding {@code null}.
     */
    public static final HutDesign RIFT_CHAPEL = new RiftChapel();

    /** All designs, in number order. */
    public static final List<HutDesign> ALL = List.of(
            new CobaltCottage(),
            new CrookedShack(),
            new StargazerTower(),
            new HillsideBurrow(),
            new BlueTileHouse(),
            new FrostLodge(),
            new WanderersTent(),
            new BogStiltHouse(),
            new CopperWorkshop(),
            RIFT_CHAPEL);

    private HutDesigns() {}
}
