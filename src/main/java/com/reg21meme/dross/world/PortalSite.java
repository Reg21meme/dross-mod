package com.reg21meme.dross.world;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Where the Overworld Dross portal site is. This is the single source of truth:
 * moving the site later only means changing X and Z here.
 * Owned by world-builder; other areas only read it.
 */
public final class PortalSite
{
    /** Overworld X/Z of the portal site. */
    public static final int X = 0;
    public static final int Z = 0;

    /**
     * The position of the portal site frame in the given Overworld level.
     * Minimal starting version: just the surface at X/Z. world-builder replaces the body
     * so it returns the real frame position once the frame has been placed.
     */
    public static BlockPos getFramePos(ServerLevel overworld)
    {
        return overworld.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, new BlockPos(X, 0, Z));
    }

    private PortalSite() {}
}
