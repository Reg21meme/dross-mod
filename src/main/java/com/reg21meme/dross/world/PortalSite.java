package com.reg21meme.dross.world;

import com.mojang.logging.LogUtils;
import java.util.Comparator;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import org.slf4j.Logger;

/**
 * Where the Overworld Dross portal site (the castle: the Fallen Cathedral) is, and where its frame is. This is the single
 * source of truth: other areas only read it. Owned by world-builder.
 *
 * <p><b>Where:</b> the cathedral is a world-generation structure, {@code dross:portal_site}, one per world, made with the
 * land the first time its chunks are generated. Its spot comes from the seed alone ({@link SiteFinder}, the rules in
 * {@link SiteRules}): 4,000 to 5,000 blocks from the spawn point in a direction the seed picks, nudged into forests,
 * taigas or snowy land, away from villages, on fairly flat dry ground. So every getter here is arithmetic on that spot:
 * the same before and after the cathedral's chunks exist, on any thread, and none of them loads a chunk.
 * <ul>
 *   <li>The frame: {@code dross:dross_portal_frame} with an empty (air) opening 3 wide and 5 tall, along the X axis
 *       ({@link #getFrameAxis}); the cathedral's front faces south, so the frame's front is its +Z side.</li>
 *   <li>{@link #getFramePos} is the bottom corner frame block with the lowest X and Z. The opening starts one block up
 *       and one block along the frame from it: x+1..x+3.</li>
 *   <li>{@link #getOpeningCenter} is the middle block of the opening's bottom row (aim compasses and searches at it,
 *       and use its X/Z when you need "where the site is").</li>
 * </ul>
 * None of the getters throw. Called before the spot is worked out (it's started when the world is made and takes about a
 * second), they wait for it. Each accepts any {@link ServerLevel}: the Overworld is looked up from it. A world whose
 * Overworld doesn't generate the site's structure (a superflat world's own list of structures) has none:
 * {@link #isPlaced} is false there.
 */
public final class PortalSite
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The frame's axis: the Fallen Cathedral's runs along X. */
    public static final Direction.Axis FRAME_AXIS = Direction.Axis.X;
    /**
     * {@link #loadAround} adds vanilla's portal ticket this many chunks round the frame (a nether portal trip's), and loads
     * the frame's own chunks at once.
     */
    private static final int ARRIVAL_TICKET_RADIUS = 3;
    /**
     * While a player stands in the hub's exit portal (a survival player's trip comes after vanilla's 4-second wait), the
     * chunks this many round the frame are made in the background ({@link #warmArrival}), by a ticket that lapses after
     * {@value #WARM_TICKS} ticks.
     */
    private static final int WARM_RADIUS = 2;
    private static final int WARM_TICKS = 20 * 20;
    private static final TicketType<ChunkPos> WARM_TICKET = TicketType.create("dross_site_warm",
            Comparator.comparingLong(ChunkPos::toLong), WARM_TICKS);

    /** The bottom corner frame block with the lowest X and Z, in the Overworld. */
    public static BlockPos getFramePos(ServerLevel anyLevel)
    {
        return frame(anyLevel).corner();
    }

    /** The direction the frame runs along: {@code X} (players walk through it along Z). */
    public static Direction.Axis getFrameAxis(ServerLevel anyLevel)
    {
        return frame(anyLevel).axis();
    }

    /**
     * The middle block of the opening's bottom row, in the Overworld (always an opening block, inside the frame). Its X/Z
     * is the site column.
     */
    public static BlockPos getOpeningCenter(ServerLevel anyLevel)
    {
        return frame(anyLevel).openingCenter();
    }

    /** How many blocks wide the opening is (along {@link #getFrameAxis}): 3. */
    public static int getOpeningWidth(ServerLevel anyLevel)
    {
        return frame(anyLevel).openingWidth();
    }

    /**
     * True if this world has a portal site: always in a normal Overworld (its spot is known from the start, and the
     * cathedral is made with its land); never in a superflat world whose list of structures leaves it out.
     */
    public static boolean isPlaced(ServerLevel anyLevel)
    {
        try
        {
            return SiteFinder.available(toOverworld(anyLevel));
        }
        catch (RuntimeException e)
        {
            return false;
        }
    }

    /**
     * Has the chunks round the frame made in the background, ahead of a trip from the hub's exit portal, so it finds the
     * cathedral's chunks ready rather than making them on the server thread as it lands. Nothing waits for it, and its
     * ticket lapses by itself. Does nothing in a world with no site. Server thread.
     */
    public static void warmArrival(ServerLevel anyLevel)
    {
        ServerLevel overworld = toOverworld(anyLevel);
        if (isPlaced(overworld))
        {
            ChunkPos at = new ChunkPos(getOpeningCenter(overworld));
            overworld.getChunkSource().addRegionTicket(WARM_TICKET, at, WARM_RADIUS, at);
        }
    }

    /**
     * Loads the chunks the frame and the ground in front of and behind it are in, now (the first time, generated: the
     * cathedral with them), and adds vanilla's portal ticket round the frame, as a nether portal trip loads its
     * destination. For the hub's exit portal and {@code /dross goto cathedral}. Server thread.
     *
     * @return how many of those chunks weren't loaded yet
     */
    public static int loadAround(ServerLevel anyLevel)
    {
        ServerLevel overworld = toOverworld(anyLevel);
        BlockPos center = getOpeningCenter(overworld);
        overworld.getChunkSource().addRegionTicket(TicketType.PORTAL, new ChunkPos(center), ARRIVAL_TICKET_RADIUS, center);
        int made = 0;
        for (int cx = SectionPos.blockToSectionCoord(center.getX() - 4); cx <= SectionPos.blockToSectionCoord(center.getX() + 4); cx++)
        {
            for (int cz = SectionPos.blockToSectionCoord(center.getZ() - 6); cz <= SectionPos.blockToSectionCoord(center.getZ() + 6); cz++)
            {
                if (overworld.getChunkSource().getChunkNow(cx, cz) == null)
                {
                    made++;
                }
                overworld.getChunk(cx, cz);
            }
        }
        return made;
    }

    /** The seed's spot for this world, or null if there's none to be had. Never throws. */
    @Nullable
    static SiteSpot spot(ServerLevel anyLevel)
    {
        try
        {
            return SiteFinder.spot(toOverworld(anyLevel));
        }
        catch (RuntimeException e)
        {
            LOGGER.warn("Dross portal site: couldn't work out the site's spot ({})", e.toString());
            return null;
        }
    }

    /** The frame at the seed's spot, or (if there's none to be had) a rough guess at sea level by the spawn. Never throws. */
    static SiteFrame frame(ServerLevel anyLevel)
    {
        SiteSpot spot = spot(anyLevel);
        if (spot != null)
        {
            return spot.frame();
        }
        BlockPos spawn;
        try
        {
            spawn = anyLevel.getSharedSpawnPos();
        }
        catch (RuntimeException e)
        {
            spawn = BlockPos.ZERO;
        }
        return SiteFrame.fromOpening(new BlockPos(spawn.getX() - 1, anyLevel.getSeaLevel() + 1, spawn.getZ()), FRAME_AXIS, 3, 5);
    }

    static ServerLevel toOverworld(ServerLevel level)
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
