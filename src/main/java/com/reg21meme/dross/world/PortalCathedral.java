package com.reg21meme.dross.world;

import com.reg21meme.dross.world.shrine.ShrineDesign;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LevelHeightAccessor;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * The portal site's castle: shrine design No. 1, the <b>Fallen Cathedral</b> ({@code world.shrine}), a vast ruined gothic
 * cathedral with the unlit Dross frame (opening 3x5, along X) on a stepped altar in its apse. Its front (the great door)
 * faces south, unturned, and it's placed so the middle of its frame opening is on the site column (the spot the seed
 * picked, {@link SiteFinder}). Built with the land by the world-generation structure {@code dross:portal_site}
 * ({@link PortalSitePiece}); this class is its arithmetic only: nothing here looks at the world.
 */
final class PortalCathedral
{
    /** The shrine design used: No. 1, the Fallen Cathedral. */
    static final String DESIGN_ID = "fallen_cathedral";
    /** The way its front faces. South (unturned) keeps its frame along X. */
    static final Direction FACING = Direction.SOUTH;

    private static final ShrineDesign DESIGN = ShrineDesign.byId(DESIGN_ID)
            .orElseThrow(() -> new IllegalStateException("There's no shrine design called " + DESIGN_ID));

    static ShrineDesign design()
    {
        return DESIGN;
    }

    /** Where everything ends up with the opening's middle column at {@code x}/{@code z} and the ground at {@code groundY}. */
    static ShrineDesign.Built built(int x, int z, int groundY)
    {
        return DESIGN.built(DESIGN.centerForFrameAt(x, z, groundY, FACING), FACING);
    }

    /** Its frame, for the site column {@code x}/{@code z} and the ground at {@code groundY}. */
    static SiteFrame frame(int x, int z, int groundY)
    {
        ShrineDesign.Built built = built(x, z, groundY);
        return SiteFrame.fromOpening(built.openingMin(), built.frameAxis(), built.openingWidth(), built.openingHeight());
    }

    /** Its footprint seen from above (Y from its underground layers to its top, for the ground at {@code groundY}). */
    static BoundingBox footprint(int x, int z, int groundY)
    {
        return DESIGN.footprintForFrameAt(x, z, groundY, FACING);
    }

    /** The template's lowest corner (where it's placed from) for the ground at {@code groundY}. */
    static BlockPos templateOrigin(int x, int z, int groundY)
    {
        BlockPos center = DESIGN.centerForFrameAt(x, z, groundY, FACING);
        return center.offset(-DESIGN.width() / 2, -DESIGN.groundLayer(), -DESIGN.depth() / 2);
    }

    /** {@code wanted} if the cathedral fits in the world's height with its ground there, else the nearest level that does. */
    static int fitting(LevelHeightAccessor heights, int wanted)
    {
        if (DESIGN.fitsHeightAt(heights, wanted))
        {
            return wanted;
        }
        for (int d = 1; d <= heights.getHeight(); d++)
        {
            if (DESIGN.fitsHeightAt(heights, wanted - d))
            {
                return wanted - d;
            }
            if (DESIGN.fitsHeightAt(heights, wanted + d))
            {
                return wanted + d;
            }
        }
        return wanted;
    }

    private PortalCathedral() {}
}
