package com.reg21meme.dross.world;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;

/**
 * Where a world's portal site is, worked out from its seed alone ({@link SiteFinder}): the same answer on every thread and
 * every time for a world, before and after the cathedral's chunks exist.
 *
 * @param x         the site column's X: the middle of the cathedral's frame opening
 * @param z         the site column's Z
 * @param groundY   the cathedral's ground level (the Y of the ground block it stands on)
 * @param rung      which rung of {@link SiteRules#ladder} found it (0 = the strict rules; {@link SiteRules#LAST_RESORT})
 * @param rungName  that rung's name, for the log
 * @param variation how much the land's heights under the footprint vary (the check grid's highest minus lowest)
 * @param distance  how far it is from the spawn point (flat blocks)
 * @param tried     how many spots were looked at
 * @param heights   how many columns' heights were read from the generator
 * @param millis    how long working it out took
 */
record SiteSpot(int x, int z, int groundY, int rung, String rungName, int variation, int distance, int tried, int heights,
                long millis)
{
    /** The chunk the structure starts in: the one holding the site column. */
    ChunkPos startChunk()
    {
        return new ChunkPos(x >> 4, z >> 4);
    }

    /** The cathedral's frame. */
    SiteFrame frame()
    {
        return PortalCathedral.frame(x, z, groundY);
    }

    /** The cathedral's footprint (from its underground layers to its top). */
    BoundingBox footprint()
    {
        return PortalCathedral.footprint(x, z, groundY);
    }

    String describe()
    {
        return String.format("site column %d %d, ground level y %d, %d blocks from the spawn point, the land varying by %d under it "
                        + "(rule %d: %s); %d spots looked at, %d columns' heights read, in %d ms", x, z, groundY, distance, variation, rung,
                rungName, tried, heights, millis);
    }
}
