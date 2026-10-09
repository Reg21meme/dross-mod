package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Where the Overworld Dross portal site (the castle) is, and where its frame is. This is the single source of
 * truth: moving the site later only means changing X and Z here. Owned by world-builder; other areas only read it.
 *
 * <p>The castle frame (template or placeholder shrine), built once per world:
 * <ul>
 *   <li>A nether-portal-shaped frame of {@code dross:dross_portal_frame} with an empty (air) opening, standing
 *       along the X axis or the Z axis ({@link #getFrameAxis}). The placeholder is always 4x5 along X
 *       (opening 2x3); a castle template's frame can face either way and be bigger.</li>
 *   <li>{@link #getFramePos} is the bottom corner frame block with the lowest X and Z. The opening starts one block
 *       up and one block along the frame from it: along X the opening is x+1..x+width, along Z it's z+1..z+width.</li>
 *   <li>{@link #getOpeningCenter} is the middle block of the opening's bottom row (aim compasses and searches at it).</li>
 * </ul>
 *
 * <p>None of the getters throw. Before the site is built they return where the placeholder will be;
 * called off the server thread they return a rough guess at sea level instead of loading chunks.
 * Each accepts any {@link ServerLevel}: the Overworld is looked up from it.
 */
public final class PortalSite
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Overworld X/Z of the portal site. The castle is centred here; the placeholder's opening includes this column. */
    public static final int X = 0;
    public static final int Z = 0;

    /** The castle template: {@code data/dross/structures/portal_castle.nbt}. */
    public static final ResourceLocation CASTLE_TEMPLATE = new ResourceLocation(Dross.MODID, "portal_castle");

    /** The placeholder frame's size, including the corners (opening 2x3). */
    public static final int FRAME_WIDTH = 4;
    public static final int FRAME_HEIGHT = 5;
    /**
     * The axis of the placeholder shrine's frame (and of old worlds' bare frame). A castle template's frame can
     * run along Z instead: use {@link #getFrameAxis} for the real one.
     */
    public static final Direction.Axis FRAME_AXIS = Direction.Axis.X;

    /**
     * The bottom corner frame block with the lowest X and Z, in the Overworld.
     * After the site is built: the stored position. Before: where the placeholder frame will go.
     */
    public static BlockPos getFramePos(ServerLevel anyLevel)
    {
        return frame(anyLevel).corner();
    }

    /**
     * The direction the frame runs along: {@code X} (players walk through it along Z) or {@code Z}
     * (players walk through it along X). Old worlds and the placeholder: {@code X}.
     */
    public static Direction.Axis getFrameAxis(ServerLevel anyLevel)
    {
        return frame(anyLevel).axis();
    }

    /**
     * The middle block of the opening's bottom row, in the Overworld (always an opening block, inside the frame).
     * For the usual 2-wide opening it's the opening block with the lower X (or Z); for the placeholder it's
     * exactly ({@link #X}, frame Y + 1, {@link #Z}).
     */
    public static BlockPos getOpeningCenter(ServerLevel anyLevel)
    {
        return frame(anyLevel).openingCenter();
    }

    /** How many blocks wide the opening is (along {@link #getFrameAxis}); 2 for the usual 4x5 frame. */
    public static int getOpeningWidth(ServerLevel anyLevel)
    {
        return frame(anyLevel).openingWidth();
    }

    /** How many blocks tall the opening is; 3 for the usual 4x5 frame. */
    public static int getOpeningHeight(ServerLevel anyLevel)
    {
        return frame(anyLevel).openingHeight();
    }

    /** True once the site has been built in this world. */
    public static boolean isPlaced(ServerLevel anyLevel)
    {
        try
        {
            ServerLevel level = toOverworld(anyLevel);
            return level.getServer().isSameThread() && PortalSiteData.get(level).isPlaced();
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }

    /** The stored frame, or the planned placeholder frame before the site is built. Never throws. */
    static SiteFrame frame(ServerLevel anyLevel)
    {
        ServerLevel level = toOverworld(anyLevel);
        try
        {
            if (!level.getServer().isSameThread())
            {
                // Never touch saved data or load chunks off the main thread.
                return roughGuess(level);
            }
            PortalSiteData data = PortalSiteData.get(level);
            if (data.isPlaced())
            {
                return data.getFrame();
            }
            return SiteFrame.standard(PortalSiteBuilder.plannedFramePos(level), FRAME_AXIS);
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("Dross portal site: couldn't look up the frame ({}); using a rough guess at sea level.", e.toString());
            return roughGuess(level);
        }
    }

    private static SiteFrame roughGuess(ServerLevel level)
    {
        return SiteFrame.standard(new BlockPos(frameMinX(), level.getSeaLevel(), Z), FRAME_AXIS);
    }

    /** Lowest X of the placeholder frame for the current site X. The site column X is the left block of the opening. */
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
