package com.reg21meme.dross.portal;

import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;

/**
 * Remembers (in the world save) whether the hub has been built, where its exit portal is, and how the hub is laid out.
 * Saved as data/dross_hub.dat in the Overworld's save folder (the Overworld is always loaded).
 * <ul>
 *   <li>{@code Built}, {@code ExitX/Y/Z}, {@code ExitAxis}, {@code ExitWidth/Height}: the exit portal's opening (older
 *       saves have no width and height).</li>
 *   <li>Since Terrain v1, the layout: {@code Kind} (which hub was built), {@code Footprint} (the box it covers: min X, Y,
 *       Z, max X, Y, Z), {@code GroundY} (the ground level it stands on), {@code Fitted} (its ground was levelled and
 *       blended into the terrain with a staircase), {@code BuildMillis} (how long building it took) and the arrival spot
 *       ({@code ArrivalX/Y/Z}, {@code ArrivalYaw}). Older saves have none of these: {@link DrossHub#layout} works them
 *       out once from the real frame and saves them (it never changes any blocks).</li>
 * </ul>
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

    /** Which hub was built; null in saves from before the layout was remembered. */
    @Nullable
    private DrossHub.Kind kind;
    @Nullable
    private BoundingBox footprint;
    private int groundY;
    private boolean fitted;
    /** How long building the hub took, in milliseconds; -1 if unknown (built before this was remembered). */
    private long buildMillis = -1L;
    @Nullable
    private Vec3 arrival;
    private float arrivalYaw;
    /** True if the hub was built while this world was loaded this time (not saved; tests use it). */
    private boolean builtThisSession;

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
        if (tag.contains("Kind", Tag.TAG_STRING))
        {
            data.kind = kindNamed(tag.getString("Kind"));
        }
        int[] box = tag.getIntArray("Footprint");
        if (box.length == 6)
        {
            data.footprint = new BoundingBox(box[0], box[1], box[2], box[3], box[4], box[5]);
        }
        data.groundY = tag.getInt("GroundY");
        data.fitted = tag.getBoolean("Fitted");
        data.buildMillis = tag.contains("BuildMillis") ? tag.getLong("BuildMillis") : -1L;
        if (tag.contains("ArrivalX"))
        {
            data.arrival = new Vec3(tag.getDouble("ArrivalX"), tag.getDouble("ArrivalY"), tag.getDouble("ArrivalZ"));
            data.arrivalYaw = tag.getFloat("ArrivalYaw");
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
        if (this.kind != null)
        {
            tag.putString("Kind", this.kind.name().toLowerCase(Locale.ROOT));
        }
        if (this.footprint != null)
        {
            BoundingBox box = this.footprint;
            tag.putIntArray("Footprint", new int[] {box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()});
        }
        tag.putInt("GroundY", this.groundY);
        tag.putBoolean("Fitted", this.fitted);
        if (this.buildMillis >= 0L)
        {
            tag.putLong("BuildMillis", this.buildMillis);
        }
        if (this.arrival != null)
        {
            tag.putDouble("ArrivalX", this.arrival.x);
            tag.putDouble("ArrivalY", this.arrival.y);
            tag.putDouble("ArrivalZ", this.arrival.z);
            tag.putFloat("ArrivalYaw", this.arrivalYaw);
        }
        return tag;
    }

    @Nullable
    private static DrossHub.Kind kindNamed(String name)
    {
        for (DrossHub.Kind value : DrossHub.Kind.values())
        {
            if (value.name().equalsIgnoreCase(name))
            {
                return value;
            }
        }
        return null;
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

    /** Remembers a freshly built hub: its exit portal (if it has one), what was built and how long it took. */
    void markBuilt(DrossHubBuilder.Result result, long millis)
    {
        this.built = true;
        if (result.exit() != null)
        {
            rememberExit(result.exit());
        }
        this.kind = result.kind();
        this.footprint = copy(result.footprint());
        this.groundY = result.groundY();
        this.fitted = result.fitted();
        this.buildMillis = millis;
        this.builtThisSession = true;
        setDirty();
    }

    /** Remembers the exit portal's real opening (older saves didn't have its size). */
    void rememberExit(PortalOpening exit)
    {
        this.exitPortal = exit.minCorner().immutable();
        this.exitAxis = exit.axis();
        this.exitWidth = exit.width();
        this.exitHeight = exit.height();
        setDirty();
    }

    /** Remembers a layout worked out for a hub built before layouts were saved. */
    void rememberLayout(DrossHub.Kind kind, BoundingBox footprint, int groundY)
    {
        this.kind = kind;
        this.footprint = copy(footprint);
        this.groundY = groundY;
        this.fitted = false;
        setDirty();
    }

    /** True once the hub's kind and footprint are known (always for hubs built since Terrain v1). */
    boolean hasLayout()
    {
        return this.kind != null && this.footprint != null;
    }

    @Nullable
    DrossHub.Kind getKind()
    {
        return this.kind;
    }

    /** A copy of the footprint (bounding boxes can be changed in place), or null if it isn't known. */
    @Nullable
    BoundingBox getFootprint()
    {
        return copy(this.footprint);
    }

    int getGroundY()
    {
        return this.groundY;
    }

    boolean isFitted()
    {
        return this.fitted;
    }

    long getBuildMillis()
    {
        return this.buildMillis;
    }

    boolean wasBuiltThisSession()
    {
        return this.builtThisSession;
    }

    /** Where a player arriving through the hub's portal lands, or null if it isn't known yet. */
    @Nullable
    Vec3 getArrival()
    {
        return this.arrival;
    }

    float getArrivalYaw()
    {
        return this.arrivalYaw;
    }

    void setArrival(Vec3 spot, float yaw)
    {
        this.arrival = spot;
        this.arrivalYaw = yaw;
        setDirty();
    }

    @Nullable
    private static BoundingBox copy(@Nullable BoundingBox box)
    {
        return box == null ? null : new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }
}
