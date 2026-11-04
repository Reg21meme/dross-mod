package com.reg21meme.dross.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * Where the castle's Dross portal frame is and how big it is.
 *
 * @param corner        the bottom corner frame block with the lowest X and Z (what {@link PortalSite#getFramePos} returns)
 * @param axis          the direction the frame runs along ({@code X}: you walk through it along Z; {@code Z}: along X)
 * @param openingWidth  how many blocks wide the opening is (along {@code axis}): 3 for the Fallen Cathedral's
 * @param openingHeight how many blocks tall the opening is: 5 for the Fallen Cathedral's
 */
record SiteFrame(BlockPos corner, Direction.Axis axis, int openingWidth, int openingHeight)
{
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

    /** The middle block of the opening's bottom row (for an even width, the lower of the two middle blocks). */
    BlockPos openingCenter()
    {
        return openingMin().relative(positive(axis), (openingWidth - 1) / 2);
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
