package com.reg21meme.dross.portal;

import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Remembers (in the world save) whether the hub has been built, and where its exit portal is.
 * Saved as data/dross_hub.dat in the Overworld's save folder (the Overworld is always loaded).
 */
public class DrossHubData extends SavedData
{
    private static final String NAME = "dross_hub";

    private boolean built;
    @Nullable
    private BlockPos exitPortal;
    private Direction.Axis exitAxis = Direction.Axis.X;
    /** The exit opening's size. 0 in saves from before the size was remembered (it then counts as 1x1). */
    private int exitWidth;
    private int exitHeight;

    /** Must be called on the server thread. */
    static DrossHubData get(MinecraftServer server)
    {
        return server.overworld().getDataStorage().computeIfAbsent(DrossHubData::load, DrossHubData::new, NAME);
    }

    private static DrossHubData load(CompoundTag tag)
    {
        DrossHubData data = new DrossHubData();
        data.built = tag.getBoolean("Built");
        if (tag.contains("ExitX"))
        {
            data.exitPortal = new BlockPos(tag.getInt("ExitX"), tag.getInt("ExitY"), tag.getInt("ExitZ"));
            data.exitAxis = "z".equals(tag.getString("ExitAxis")) ? Direction.Axis.Z : Direction.Axis.X;
            data.exitWidth = tag.getInt("ExitWidth");
            data.exitHeight = tag.getInt("ExitHeight");
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag)
    {
        tag.putBoolean("Built", this.built);
        if (this.exitPortal != null)
        {
            tag.putInt("ExitX", this.exitPortal.getX());
            tag.putInt("ExitY", this.exitPortal.getY());
            tag.putInt("ExitZ", this.exitPortal.getZ());
            tag.putString("ExitAxis", this.exitAxis.getName());
            tag.putInt("ExitWidth", this.exitWidth);
            tag.putInt("ExitHeight", this.exitHeight);
        }
        return tag;
    }

    boolean isBuilt()
    {
        return this.built;
    }

    /** A block inside the exit portal's opening (its lowest corner), or null if the hub has no exit portal. */
    @Nullable
    BlockPos getExitPortal()
    {
        return this.exitPortal;
    }

    Direction.Axis getExitAxis()
    {
        return this.exitAxis;
    }

    /**
     * Where the exit portal's opening is, as remembered when the hub was built (whether or not its frame is still
     * there), or null if the hub has no exit portal. Old saves didn't remember the size: then it counts as 1x1.
     */
    @Nullable
    PortalOpening getRememberedExit()
    {
        if (this.exitPortal == null)
        {
            return null;
        }
        return new PortalOpening(this.exitPortal, this.exitAxis, Math.max(1, this.exitWidth), Math.max(1, this.exitHeight));
    }

    void markBuilt(@Nullable PortalOpening exit)
    {
        this.built = true;
        if (exit != null)
        {
            this.exitPortal = exit.minCorner().immutable();
            this.exitAxis = exit.axis();
            this.exitWidth = exit.width();
            this.exitHeight = exit.height();
        }
        setDirty();
    }
}
