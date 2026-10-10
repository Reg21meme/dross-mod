package com.reg21meme.dross.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where the castle's Dross portal frame is and how big it is.
 *
 * @param corner        the bottom corner frame block with the lowest X and Z (what {@link PortalSite#getFramePos} returns).
 *                      Nether-style frames may leave their corners out, so in a castle template this spot can be
 *                      some other block; the placeholder shrine always has its corners.
 * @param axis          the direction the frame runs along ({@code X}: you walk through it along Z; {@code Z}: along X)
 * @param openingWidth  how many blocks wide the opening is (along {@code axis}), 2 for the usual 4x5 frame
 * @param openingHeight how many blocks tall the opening is, 3 for the usual 4x5 frame
 */
record SiteFrame(BlockPos corner, Direction.Axis axis, int openingWidth, int openingHeight)
{
    /** The usual 4x5 frame (opening 2x3) with this bottom corner. */
    static SiteFrame standard(BlockPos corner, Direction.Axis axis)
    {
        return new SiteFrame(corner, axis, PortalSite.FRAME_WIDTH - 2, PortalSite.FRAME_HEIGHT - 2);
    }

    /** Builds one from the opening block with the lowest X, Y and Z. */
    static SiteFrame fromOpening(BlockPos openingMin, Direction.Axis axis, int width, int height)
    {
        return new SiteFrame(openingMin.below().relative(negative(axis)).immutable(), axis, width, height);
    }

    /** The opening block with the lowest X, Y and Z (one up and one along the frame from the corner). */
    BlockPos openingMin()
    {
        return corner.above().relative(positive(axis));
    }

    /**
     * The middle block of the opening's bottom row. For an even width (like the usual 2) it's the lower of
     * the two middle blocks, so for the placeholder it's exactly the site column.
     */
    BlockPos openingCenter()
    {
        return openingMin().relative(positive(axis), (openingWidth - 1) / 2);
    }

    /** The exact middle of the opening along the frame (can be between two blocks). */
    double centerAlong()
    {
        BlockPos min = openingMin();
        return (axis == Direction.Axis.X ? min.getX() : min.getZ()) + openingWidth / 2.0D;
    }

    /** The middle of the frame's thickness across it (the block's centre line you walk through along). */
    double centerAcross()
    {
        return (axis == Direction.Axis.X ? corner.getZ() : corner.getX()) + 0.5D;
    }

    static Direction positive(Direction.Axis axis)
    {
        return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.POSITIVE);
    }

    static Direction negative(Direction.Axis axis)
    {
        return Direction.fromAxisAndDirection(axis, Direction.AxisDirection.NEGATIVE);
    }
}
