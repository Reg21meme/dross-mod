package com.reg21meme.dross.boss.lair;

import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/**
 * The Cinder Colossus's lairs in the Cinder Wastes, for other areas ({@code /dross goto lair colossus},
 * {@code /dross goto biome}, the terrain previews).
 * <p>
 * Where they go is the boss-site planner's: the Cinder Wastes' patches are taken nearest the hub first in groups of 3,
 * and one patch of every group holds a lair (the planner picks it among the patches where {@link LairFinder} says a
 * volcano fits), out as far as anyone goes. Each lair is built the first time a player comes within
 * {@link LairTuning#BUILD_RANGE} blocks of it ({@link LairPlacer}): a volcano rising well above the land, with a jagged
 * smoking crater at its top that opens into a round domed cavern 41 blocks across (the arena), entered by a big cave mouth
 * in its flank. Its Colossus rises when a player first walks in; once it's beaten, that lair stays empty for good (no
 * respawn).
 * <p>
 * Everything here is for the server thread (it reads the world's saved data), except the futures' work.
 */
public final class ColossusLair
{
    private ColossusLair() {}

    /**
     * A built lair, in the Dross ({@code ModDimensions.DROSS_LEVEL}).
     *
     * @param id              the lair's id: its Cinder Wastes patch's ({@code Patch.id()}), the same forever
     * @param group           the planner's group it's the lair of (0 = the 3 patches nearest the hub)
     * @param center          the floor's middle block (the cavern's centre at floor level; players stand one block above it)
     * @param arenaMiddle     where the Colossus rises: the middle of the floor, feet position
     * @param entrance        a safe place to stand in front of the way in, open to the sky (feet position: the middle of a
     *                        block's top, two blocks of air above): in the gully just outside the volcano's cave mouth
     * @param entranceYaw     the way to face from {@code entrance}: into the mouth, towards the cavern (Minecraft's yaw:
     *                        0 south, 90 west, 180 north, -90/270 east)
     * @param tunnelDirection the way the way in runs out of the cavern, towards its mouth (north, east, south or west)
     */
    public record Lair(long id, int group, BlockPos center, Vec3 arenaMiddle, Vec3 entrance, float entranceYaw, Direction tunnelDirection)
    {
        /** Blocks from (x, z) to the floor's middle, along the ground. */
        public double distanceTo(double x, double z)
        {
            return Math.hypot(x - (center.getX() + 0.5D), z - (center.getZ() + 0.5D));
        }

        /** How far its way in reaches from its middle (to its entrance), along the ground. */
        public double reach()
        {
            return distanceTo(entrance.x, entrance.z);
        }
    }

    /** A lair's Colossus: it rises when someone walks in, it's up (someone woke it and it's still about), or it's been beaten. */
    public enum State
    {
        READY, AWAKE, BEATEN
    }

    static Lair of(ColossusLairData.Entry e)
    {
        BlockPos center = new BlockPos(e.centerX(), e.floorY(), e.centerZ());
        Vec3 middle = new Vec3(e.centerX() + 0.5D, e.floorY() + 1.0D, e.centerZ() + 0.5D);
        return new Lair(e.id(), e.group(), center, middle, e.entrance(), e.entranceYaw(), e.tunnel());
    }

    /** Every lair built so far in this world. */
    public static List<Lair> all(MinecraftServer server)
    {
        List<Lair> list = new ArrayList<>();
        for (ColossusLairData.Entry e : ColossusLairData.get(server).all())
        {
            list.add(of(e));
        }
        return list;
    }

    /** The built lair with this id (its patch's), if it's built. */
    public static Optional<Lair> get(MinecraftServer server, long id)
    {
        ColossusLairData.Entry e = ColossusLairData.get(server).lair(id);
        return e == null ? Optional.empty() : Optional.of(of(e));
    }

    /** The built lair of a planned site, if it's built. */
    public static Optional<Lair> get(MinecraftServer server, Site site)
    {
        return get(server, site.patch().id());
    }

    /** Its Colossus's state: it rises when someone walks in, it's up, or it's been beaten (for good). */
    public static State state(MinecraftServer server, Lair lair)
    {
        ColossusLairData.Entry e = ColossusLairData.get(server).lair(lair.id());
        if (e == null || e.boss() == null)
        {
            return e != null && e.beaten() ? State.BEATEN : State.READY;
        }
        if (e.beaten())
        {
            return State.BEATEN;
        }
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        Entity boss = dross == null ? null : dross.getEntity(e.boss());
        return boss == null || boss instanceof CinderColossus colossus && colossus.isAlive() ? State.AWAKE : State.READY;
    }

    /**
     * The lair site of the planner's first group (the 3 Cinder Wastes patches nearest the hub), once it's worked out (in
     * the background, a moment after the world starts); empty before. X and Z of the planned spot (Y the ground there).
     */
    public static Optional<BlockPos> plannedSite(MinecraftServer server)
    {
        return LairPlacer.firstSite().map(Site::pos);
    }

    /** The planned lair site nearest {@code from} in the Dross (built or not), worked out on a background thread. */
    public static CompletableFuture<Optional<Site>> findNearestSite(ServerLevel dross, BlockPos from)
    {
        return LairPlacer.nearestSite(dross, from);
    }

    /**
     * Calls {@code then} (on the server thread) with the site's lair once it's built: at once if it's built already,
     * otherwise once it's been built (it goes to the front of the queue). Never called if building it fails.
     */
    public static void whenBuilt(MinecraftServer server, Site site, Consumer<Lair> then)
    {
        LairPlacer.whenBuilt(server, site, entry -> then.accept(of(entry)));
    }

    /** True while the site's lair is waiting to be built, its chunks loading, or being built. */
    public static boolean isBeingBuilt(Site site)
    {
        return LairPlacer.isPending(site.patch().id());
    }
}
