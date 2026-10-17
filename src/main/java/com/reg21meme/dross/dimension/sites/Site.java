package com.reg21meme.dross.dimension.sites;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.ChunkPos;

/**
 * Where the planner put a use: in which patch, at which spot.
 *
 * @param use     what it is
 * @param patch   the patch it's in
 * @param pos     its spot, as its {@link SiteFinder} gave it (a sewer network: the middle of its middle chunk; its y is the
 *                finder's, often 0: find the ground before standing anyone there)
 * @param relaxed true if its group had no patch where it fits by the normal rules, so it's the use's smallest form in the
 *                roomiest patch ({@link SiteFinder})
 * @param detail  what the finder kept with it (sewers: their {@code SewerSites.Layout}), or null
 */
public record Site(SiteUse use, Patch patch, BlockPos pos, boolean relaxed, @Nullable Object detail)
{
    /** The chunk its spot is in (a structure's start chunk). */
    public ChunkPos chunk()
    {
        return new ChunkPos(this.pos);
    }

    /** Blocks from (x, z) to its spot, along the ground. */
    public double distanceTo(int x, int z)
    {
        double dx = this.pos.getX() - x;
        double dz = this.pos.getZ() - z;
        return Math.sqrt(dx * dx + dz * dz);
    }
}
