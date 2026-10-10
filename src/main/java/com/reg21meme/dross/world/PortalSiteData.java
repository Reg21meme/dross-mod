package com.reg21meme.dross.world;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Remembers (in the world save) where the portal site is going to be, whether it has been built, and where its frame
 * is. Saved as data/dross_portal_site.dat in the Overworld's save folder.
 *
 * <ul>
 *   <li><b>Planned</b>: the spot picked for this world (its site column X/Z and ground level), chosen once when the
 *       world is new and kept for good. {@link PortalSite} reports the frame there until it's built.</li>
 *   <li><b>Placed</b>: the site is built; the frame's corner, axis and opening size are stored. Worlds that already
 *       have a site (Placed) keep it: they never get a planned spot.</li>
 * </ul>
 * Worlds saved before the castle existed only have Placed and FrameX/Y/Z: they get the old bare frame's
 * values (axis X, opening 2x3, kind "bare frame").
 */
public class PortalSiteData extends SavedData
{
    private static final String NAME = "dross_portal_site";

    /** What was built. */
    public static final String KIND_TEMPLATE = "castle template";
    public static final String KIND_CATHEDRAL = "fallen cathedral";
    public static final String KIND_PLACEHOLDER = "placeholder shrine";
    public static final String KIND_BARE_FRAME = "bare frame (old world)";

    private boolean placed;
    private BlockPos framePos = BlockPos.ZERO;
    private Direction.Axis axis = PortalSite.FRAME_AXIS;
    private int openingWidth = PortalSite.FRAME_WIDTH - 2;
    private int openingHeight = PortalSite.FRAME_HEIGHT - 2;
    private String kind = KIND_BARE_FRAME;

    private boolean planned;
    private int siteX;
    private int siteZ;
    private int plannedGroundY;
    private int planLevel;
    private final LongArrayList rejected = new LongArrayList();

    /** Must be called on the server thread with the Overworld. */
    public static PortalSiteData get(ServerLevel overworld)
    {
        return overworld.getDataStorage().computeIfAbsent(PortalSiteData::load, PortalSiteData::new, NAME);
    }

    private static PortalSiteData load(CompoundTag tag)
    {
        PortalSiteData data = new PortalSiteData();
        data.placed = tag.getBoolean("Placed");
        data.framePos = new BlockPos(tag.getInt("FrameX"), tag.getInt("FrameY"), tag.getInt("FrameZ"));
        Direction.Axis axis = Direction.Axis.byName(tag.getString("Axis"));
        data.axis = axis == Direction.Axis.Z ? Direction.Axis.Z : Direction.Axis.X;
        if (tag.getInt("OpeningWidth") > 0)
        {
            data.openingWidth = tag.getInt("OpeningWidth");
        }
        if (tag.getInt("OpeningHeight") > 0)
        {
            data.openingHeight = tag.getInt("OpeningHeight");
        }
        if (tag.contains("Kind"))
        {
            data.kind = tag.getString("Kind");
        }
        data.planned = tag.getBoolean("Planned");
        data.siteX = tag.getInt("SiteX");
        data.siteZ = tag.getInt("SiteZ");
        data.plannedGroundY = tag.getInt("PlannedGround");
        data.planLevel = tag.getInt("PlanLevel");
        for (long packed : tag.getLongArray("Rejected"))
        {
            data.rejected.add(packed);
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("Placed", placed);
        tag.putInt("FrameX", framePos.getX());
        tag.putInt("FrameY", framePos.getY());
        tag.putInt("FrameZ", framePos.getZ());
        tag.putString("Axis", axis.getName());
        tag.putInt("OpeningWidth", openingWidth);
        tag.putInt("OpeningHeight", openingHeight);
        tag.putString("Kind", kind);
        tag.putBoolean("Planned", planned);
        tag.putInt("SiteX", siteX);
        tag.putInt("SiteZ", siteZ);
        tag.putInt("PlannedGround", plannedGroundY);
        tag.putInt("PlanLevel", planLevel);
        tag.putLongArray("Rejected", rejected.toLongArray());
        return tag;
    }

    public boolean isPlaced()
    {
        return placed;
    }

    /** The bottom corner frame block with the lowest X and Z. */
    public BlockPos getFramePos()
    {
        return framePos;
    }

    public Direction.Axis getAxis()
    {
        return axis;
    }

    public String getKind()
    {
        return kind;
    }

    /** True once a spot has been picked for this world (it may not be built yet). */
    public boolean isPlanned()
    {
        return planned;
    }

    /** The picked spot's site column (where the frame opening's middle goes), and its planned ground level. */
    public int getSiteX()
    {
        return siteX;
    }

    public int getSiteZ()
    {
        return siteZ;
    }

    public int getPlannedGroundY()
    {
        return plannedGroundY;
    }

    /** Which step of {@link SiteRules#LADDER} the search had reached (0 = the strict rules). */
    int getPlanLevel()
    {
        return planLevel;
    }

    /** Spots that were checked in the real world and failed, so they aren't tried again. */
    long[] getRejected()
    {
        return rejected.toLongArray();
    }

    SiteFrame getFrame()
    {
        return new SiteFrame(framePos, axis, openingWidth, openingHeight);
    }

    void setPlanned(int x, int z, int groundY, int level)
    {
        if (planned && siteX == x && siteZ == z && plannedGroundY == groundY && planLevel == level)
        {
            return;
        }
        this.planned = true;
        this.siteX = x;
        this.siteZ = z;
        this.plannedGroundY = groundY;
        this.planLevel = level;
        setDirty();
    }

    void setPlanLevel(int level)
    {
        if (planLevel != level)
        {
            planLevel = level;
            setDirty();
        }
    }

    void addRejected(int x, int z)
    {
        rejected.add(BlockPos.asLong(x, 0, z));
        setDirty();
    }

    void markPlaced(SiteFrame frame, String kind)
    {
        this.placed = true;
        this.framePos = frame.corner().immutable();
        this.axis = frame.axis();
        this.openingWidth = frame.openingWidth();
        this.openingHeight = frame.openingHeight();
        this.kind = kind;
        setDirty();
    }
}
