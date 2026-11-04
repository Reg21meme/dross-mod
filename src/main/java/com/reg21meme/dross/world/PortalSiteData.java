package com.reg21meme.dross.world;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Remembers (in the world save) that the portal site has been tidied up once ({@link SiteTidy}: the trees on it felled,
 * its frame's opening cleared, the leak applied), and for which frame. Saved as data/dross_portal_site.dat in the
 * Overworld's save folder. Where the site is needs no saving: it comes from the seed ({@link SiteFinder}).
 */
public class PortalSiteData extends SavedData
{
    private static final String NAME = "dross_portal_site";

    private boolean tidied;
    private BlockPos tidiedFrame = BlockPos.ZERO;
    /** What the tidy-up did, for the log and the tests. */
    private String summary = "";

    /** Must be called on the server thread with the Overworld. */
    public static PortalSiteData get(ServerLevel overworld)
    {
        return overworld.getDataStorage().computeIfAbsent(PortalSiteData::load, PortalSiteData::new, NAME);
    }

    private static PortalSiteData load(CompoundTag tag)
    {
        PortalSiteData data = new PortalSiteData();
        data.tidied = tag.getBoolean("Tidied");
        data.tidiedFrame = BlockPos.of(tag.getLong("TidiedFrame"));
        data.summary = tag.getString("Summary");
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("Tidied", tidied);
        tag.putLong("TidiedFrame", tidiedFrame.asLong());
        tag.putString("Summary", summary);
        return tag;
    }

    /** True once the site whose frame corner is {@code frame} has been tidied up. */
    public boolean isTidied(BlockPos frame)
    {
        return tidied && tidiedFrame.equals(frame);
    }

    public String getSummary()
    {
        return summary;
    }

    void markTidied(BlockPos frame, String summary)
    {
        this.tidied = true;
        this.tidiedFrame = frame.immutable();
        this.summary = summary;
        setDirty();
    }
}
