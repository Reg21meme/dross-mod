package com.reg21meme.dross.portal;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * The opening (the inside) of a Dross portal frame, lit or not.
 *
 * @param minCorner the opening block with the lowest X, Y and Z
 * @param axis      the direction the frame runs along ({@code X}: you walk through it along Z; {@code Z}: along X)
 * @param width     how many blocks wide the opening is (along {@code axis})
 * @param height    how many blocks tall the opening is
 */
public record PortalOpening(BlockPos minCorner, Direction.Axis axis, int width, int height)
{
    static PortalOpening of(DrossPortalShape shape)
    {
        return new PortalOpening(shape.getMinCorner(), shape.getAxis(), shape.getWidth(), shape.getHeight());
    }

    /** The middle of the opening, in block coordinates (can be between two blocks). */
    public double centerAlong()
    {
        return (this.axis == Direction.Axis.X ? this.minCorner.getX() : this.minCorner.getZ()) + this.width / 2.0D;
    }
}
