package com.reg21meme.dross.villager;

import com.reg21meme.dross.villager.hut.HutDesign;
import com.reg21meme.dross.villager.hut.HutDesigns;

/**
 * The Dross trader's hut: {@link #DESIGN}, the Rift Chapel (see {@code villager.hut}): 9 x 13 blocks on the ground and
 * 18 high, with the door in one short side. Since November 4, 2026 (the user's decision of November 2) it's no longer built
 * at a village when a world starts: every Rift Chapel is a world-generation structure of its own in the countryside
 * ({@link RiftChapels}, {@link RiftChapelPiece}), made with the land, its trader in it.
 */
public final class TraderHut
{
    /** The design every chapel is built from. */
    static final HutDesign DESIGN = HutDesigns.RIFT_CHAPEL;

    private TraderHut() {}
}
