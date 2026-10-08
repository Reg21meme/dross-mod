package com.reg21meme.dross.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Remembers (in the world save) whether the portal site frame has been built, and where.
 * Saved as data/dross_portal_site.dat in the Overworld's save folder.
 */
public class PortalSiteData extends SavedData
{
    private static final String NAME = "dross_portal_site";

    private boolean placed;
    private BlockPos framePos = BlockPos.ZERO;

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
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("Placed", placed);
        tag.putInt("FrameX", framePos.getX());
        tag.putInt("FrameY", framePos.getY());
        tag.putInt("FrameZ", framePos.getZ());
        return tag;
    }

    public boolean isPlaced()
    {
        return placed;
    }

    public BlockPos getFramePos()
    {
        return framePos;
    }

    void markPlaced(BlockPos framePos)
    {
        this.placed = true;
        this.framePos = framePos.immutable();
        setDirty();
    }
}
