package com.reg21meme.dross.villager.hut;

/**
 * The trader's hut design: the Rift Chapel (the user's pick). {@code TraderHut.DESIGN} points at
 * {@link #RIFT_CHAPEL}, so it is the hut every world gets.
 */
public final class HutDesigns
{
    /** The design the trader lives in. */
    public static final HutDesign RIFT_CHAPEL = new RiftChapel();

    private HutDesigns() {}
}
