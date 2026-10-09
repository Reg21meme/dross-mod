package com.reg21meme.dross.world;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Remembers (in the world save) whether the portal site has been built, and where its frame is.
 * Saved as data/dross_portal_site.dat in the Overworld's save folder.
 *
 * <p>Worlds saved before the castle existed only have Placed and FrameX/Y/Z: they get the old bare frame's
 * values (axis X, opening 2x3, kind "bare frame").
 */
public class PortalSiteData extends SavedData
{
    private static final String NAME = "dross_portal_site";

    /** What was built. */
    public static final String KIND_TEMPLATE = "castle template";
    public static final String KIND_PLACEHOLDER = "placeholder shrine";
    public static final String KIND_BARE_FRAME = "bare frame (old world)";

    private boolean placed;
    private BlockPos framePos = BlockPos.ZERO;
    private Direction.Axis axis = PortalSite.FRAME_AXIS;
    private int openingWidth = PortalSite.FRAME_WIDTH - 2;
    private int openingHeight = PortalSite.FRAME_HEIGHT - 2;
    private String kind = KIND_BARE_FRAME;

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

    SiteFrame getFrame()
    {
        return new SiteFrame(framePos, axis, openingWidth, openingHeight);
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
