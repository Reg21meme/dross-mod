package com.reg21meme.dross.dimension.sewer;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * A way into a sewer network: a regular pipe ({@link SewerPipePiece}) or the Rat King's grand entrance
 * ({@link SewerGrandPipePiece}). Both stand on the ground over a shaft that drops into a pool.
 */
public interface SewerEntrance
{
    /** The air block over the middle of its rim (you stand on the rim at this height; the drop starts here). */
    BlockPos opening();

    /** The middle of its pool room's floor (the pool's water is level with it). */
    BlockPos centre();

    /** The ground's top block under its body (the highest of them). */
    int groundY();

    /** Its rim's top block. */
    int rimTopY();

    /** The side its steps go down (and its ladder is on). */
    Direction stepsSide();

    /** The ladder's column (inside the shaft, against the wall on the steps' side); y 0. */
    BlockPos ladderColumn();

    /** Half its opening's width (1: 3 x 3; 2: 5 x 5). */
    int openingHalf();

    /** True for the Rat King's grand entrance. */
    boolean grand();

    /** Where you'd stand on the ground at the foot of its steps, facing it (the block your feet are in; check it's safe first). */
    BlockPos approach();

    /** Its piece's bounding box (the grand entrance's includes its poisoned ground). */
    BoundingBox box();
}
