package com.reg21meme.dross.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Where the Overworld Dross portal site is. This is the single source of truth:
 * moving the site later only means changing X and Z here.
 * Owned by world-builder; other areas only read it.
 *
 * <p>Frame layout (agreed with portal-builder):
 * <ul>
 *   <li>4 wide x 5 tall, corners included, every block {@code dross:dross_portal_frame}.</li>
 *   <li>Lies along the X axis, so players walk through it along Z.</li>
 *   <li>{@link #getFramePos} is the bottom corner frame block with the lowest X.
 *       The frame fills x..x+3, y..y+4 at that z. The opening (air) is x+1..x+2, y+1..y+3.</li>
 *   <li>The frame is placed so that the site column (X, Z) is inside the opening.</li>
 * </ul>
 */
public final class PortalSite
{
    /** Overworld X/Z of the portal site. */
    public static final int X = 0;
    public static final int Z = 0;

    /** Frame size, including the corners. */
    public static final int FRAME_WIDTH = 4;
    public static final int FRAME_HEIGHT = 5;
    /** The frame lies along this axis (players walk through it along Z). */
    public static final Direction.Axis FRAME_AXIS = Direction.Axis.X;

    /**
     * The bottom corner frame block with the lowest X, in the Overworld.
     * <ul>
     *   <li>After the frame is placed: the exact stored position.</li>
     *   <li>Before it is placed: the position it will be placed at (worked out from the surface at X/Z,
     *       the same way the builder does it). If called from a non-server thread before placement,
     *       it returns a rough guess at sea level instead of loading chunks.</li>
     * </ul>
     * Passing a non-Overworld level is allowed; the Overworld is looked up from it.
     */
    public static BlockPos getFramePos(ServerLevel overworld)
    {
        ServerLevel level = toOverworld(overworld);
        if (!level.getServer().isSameThread())
        {
            // Never touch saved data or load chunks off the main thread.
            return new BlockPos(frameMinX(), level.getSeaLevel(), Z);
        }

        PortalSiteData data = PortalSiteData.get(level);
        if (data.isPlaced())
        {
            return data.getFramePos();
        }
        return PortalSiteBuilder.plannedFramePos(level);
    }

    /** True once the frame has been built in this world. */
    public static boolean isPlaced(ServerLevel overworld)
    {
        ServerLevel level = toOverworld(overworld);
        return level.getServer().isSameThread() && PortalSiteData.get(level).isPlaced();
    }

    /** Lowest X of the frame for the current site X. The site column X is the left block of the opening. */
    static int frameMinX()
    {
        return X - 1;
    }

    private static ServerLevel toOverworld(ServerLevel level)
    {
        if (level.dimension() == Level.OVERWORLD)
        {
            return level;
        }
        ServerLevel overworld = level.getServer().overworld();
        return overworld != null ? overworld : level;
    }

    private PortalSite() {}
}
