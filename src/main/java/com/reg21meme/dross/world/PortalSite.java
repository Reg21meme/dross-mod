package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Where the Overworld Dross portal site (the castle) is, and where its frame is. This is the single source of truth:
 * other areas only read it. Owned by world-builder.
 *
 * <p><b>Where:</b> each new world picks its own random spot, {@link SiteRules#MIN_DISTANCE} to
 * {@link SiteRules#MAX_DISTANCE} blocks from world spawn, on flat ground in an allowed biome and away from villages
 * (the rules are in {@link SiteRules}; the search and building are {@link PortalSitePlacer}'s). The spot is saved in
 * the world ({@link PortalSiteData}). Worlds that already had a site keep it (old worlds: at 0, 0).
 *
 * <p>The castle frame, built once per world (the user's castle template if there is one, otherwise the Fallen
 * Cathedral, or the small placeholder shrine if the cathedral can't be built; old worlds keep what they have):
 * <ul>
 *   <li>A nether-portal-shaped frame of {@code dross:dross_portal_frame} with an empty (air) opening, standing
 *       along the X axis or the Z axis ({@link #getFrameAxis}). The Fallen Cathedral's frame runs along X with a
 *       3x5 opening whose middle column is the site column; the small placeholder's is 4x5 along X (opening
 *       2x3); a castle template's frame can face either way and be bigger.</li>
 *   <li>{@link #getFramePos} is the bottom corner frame block with the lowest X and Z. The opening starts one block
 *       up and one block along the frame from it: along X the opening is x+1..x+width, along Z it's z+1..z+width.</li>
 *   <li>{@link #getOpeningCenter} is the middle block of the opening's bottom row (aim compasses and searches at it,
 *       and use its X/Z when you need "where the site is").</li>
 * </ul>
 *
 * <p>None of the getters throw or make the game wait. Before the site is built they return where its frame <b>will</b>
 * be, at the world's planned spot. Only in a new world's first minute or two, before the background search has picked
 * the spot, they return a guess at sea level straight east of spawn. If you need the real thing right away, call
 * {@code PortalSiteBuilder.ensurePlaced} first (it builds the site on the spot). Called off the server thread they
 * return the last known frame (or a rough guess) instead of touching the world. Each accepts any {@link ServerLevel}:
 * the Overworld is looked up from it.
 */
public final class PortalSite
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The castle template: {@code data/dross/structures/portal_castle.nbt}. */
    public static final ResourceLocation CASTLE_TEMPLATE = new ResourceLocation(Dross.MODID, "portal_castle");

    /** The small placeholder frame's size, including the corners (opening 2x3). The cathedral's is 5x7 (opening 3x5). */
    public static final int FRAME_WIDTH = 4;
    public static final int FRAME_HEIGHT = 5;
    /**
     * The axis of the cathedral's and the placeholder shrine's frame (and of old worlds' bare frame). A castle
     * template's frame can run along Z instead: use {@link #getFrameAxis} for the real one.
     */
    public static final Direction.Axis FRAME_AXIS = Direction.Axis.X;

    /** The last frame looked up on the server thread, for callers on other threads. */
    @Nullable
    private static volatile SiteFrame lastKnown;

    /**
     * The bottom corner frame block with the lowest X and Z, in the Overworld.
     * After the site is built: the stored position. Before: where the frame will be at the planned spot.
     */
    public static BlockPos getFramePos(ServerLevel anyLevel)
    {
        return frame(anyLevel).corner();
    }

    /**
     * The direction the frame runs along: {@code X} (players walk through it along Z) or {@code Z}
     * (players walk through it along X). Old worlds, the cathedral and the placeholder: {@code X}.
     */
    public static Direction.Axis getFrameAxis(ServerLevel anyLevel)
    {
        return frame(anyLevel).axis();
    }

    /**
     * The middle block of the opening's bottom row, in the Overworld (always an opening block, inside the frame).
     * For an even width (like the placeholder's 2) it's the middle block with the lower X (or Z). Its X/Z is the site
     * column.
     */
    public static BlockPos getOpeningCenter(ServerLevel anyLevel)
    {
        return frame(anyLevel).openingCenter();
    }

    /** How many blocks wide the opening is (along {@link #getFrameAxis}): 3 for the cathedral, 2 for the small 4x5 frame. */
    public static int getOpeningWidth(ServerLevel anyLevel)
    {
        return frame(anyLevel).openingWidth();
    }

    /** How many blocks tall the opening is: 5 for the cathedral, 3 for the small 4x5 frame. */
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

    /** The stored frame, or the planned one before the site is built. Never throws. */
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
            SiteFrame frame = data.isPlaced() ? data.getFrame() : PortalSitePlacer.plannedFrame(level);
            lastKnown = frame;
            return frame;
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("Dross portal site: couldn't look up the frame ({}); using a rough guess.", e.toString());
            return roughGuess(level);
        }
    }

    /** The last frame looked up, or a frame at sea level by world spawn if there's none yet. */
    private static SiteFrame roughGuess(ServerLevel level)
    {
        SiteFrame known = lastKnown;
        if (known != null)
        {
            return known;
        }
        BlockPos spawn;
        try
        {
            spawn = level.getSharedSpawnPos();
        }
        catch (RuntimeException e)
        {
            spawn = BlockPos.ZERO;
        }
        return SiteFrame.standard(new BlockPos(spawn.getX() - 1, level.getSeaLevel(), spawn.getZ()), FRAME_AXIS);
    }

    /** Forgets the last known frame (the server stopped; the next world may be another one). */
    static void forgetLastKnown()
    {
        lastKnown = null;
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
