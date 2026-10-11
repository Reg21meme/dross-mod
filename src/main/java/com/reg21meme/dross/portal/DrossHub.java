package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The hub at Dross 0,0: where everyone arrives in the Dross, and the only way out (its exit portal
 * leads to the castle portal in the Overworld).
 * <ul>
 *   <li>Built <b>once per world</b> (remembered in {@link DrossHubData}): when the world starts (before anyone can
 *       join; a few seconds on real terrain), or at the latest right before the first entity arrives. Worlds that already
 *       have a hub keep it: it's never rebuilt.</li>
 *   <li>Which hub, first match wins ({@link DrossHubBuilder}): the user's own template {@code dross:dross_hub} if it
 *       exists; otherwise the <b>Shattered Spire</b> (shrine design No. 2) with its frame opening centred on
 *       {@link #CENTER} and its gate facing south; only if the spire can't be built or lit, a small <b>placeholder</b>
 *       platform with a 2x3 exit portal.</li>
 *   <li>It sits properly on the terrain: its footprint is levelled at the natural ground's median height, the ground
 *       around it steps back to the natural terrain at most one block per step ({@value #HUB_BLEND_MARGIN} blocks
 *       wide), and trees in the way are removed whole (world-builder's {@code GroundFit}, like the Overworld's cathedral).</li>
 *   <li>The exit portal's position, direction and size are remembered, and only that frame is the exit. The spire's
 *       loose frame "shards" (on the ground, in its broken top, floating above it) are just decoration: they're never
 *       looked for, and they can't make a frame. If the exit ever goes out, it's relit.</li>
 *   <li>{@link #layout} tells other areas where everything is (the hub's two survivors live by it).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class DrossHub
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The hub's centre in the Dross. Only X and Z matter (the hub is built on the ground, wherever that is),
     * so Y is just 0. The dimension area reads this for the hub's no-spawn safe zone: keep the name.
     */
    public static final BlockPos CENTER = new BlockPos(0, 0, 0);

    /** The hub template's id: {@code data/dross/structures/dross_hub.nbt}. */
    public static final ResourceLocation TEMPLATE = new ResourceLocation(Dross.MODID, "dross_hub");

    /**
     * How far around the hub's footprint (blocks) the ground is blended back into the natural terrain: a staircase, at
     * most one block per step. The same as the Overworld cathedral's.
     */
    public static final int HUB_BLEND_MARGIN = 16;
    /** Water or lava this close to the hub's footprint (blocks) is counted in the log. It never stops the build. */
    public static final int HUB_WATER_CLEARANCE = 4;
    /**
     * The walkway straight out in front of the exit portal, this many blocks deep (as wide as the opening), is kept
     * clear: the survivors' rest spots are never on it. In the spire it runs from the step down through the gate.
     */
    public static final int WALKWAY_DEPTH = 7;

    /** Which hub a world has. */
    public enum Kind
    {
        /** The user's own structure-block design, {@code dross:dross_hub}. */
        TEMPLATE,
        /** The Shattered Spire (shrine design No. 2): the hub of every new world. */
        SPIRE,
        /** The small fallback platform with a 2x3 exit portal (and the hub of the oldest worlds). */
        PLACEHOLDER,
        /** Something else: an older hub whose exit frame doesn't match any of these. */
        UNKNOWN
    }

    /**
     * Where everything at the hub is.
     *
     * @param kind       which hub it is
     * @param footprint  the box it covers. Spire: its whole design, X -25..49, Z -34..38, from 2 blocks under its ground
     *                   level to 61 above. Placeholder: its 11x11 platform. Use X and Z to tell "at the hub" (the
     *                   survivors' area is this grown by 20 blocks in X and Z).
     * @param exit       the exit portal's opening (remembered where it was if its frame is broken)
     * @param arrival    where a player arriving through any Dross portal lands (feet; the middle of the block), in front
     *                   of the exit portal. Spire: (0.5, ground+9, 1.5), on the step in front of the frame.
     * @param arrivalYaw the way they face there (0 = south: in the spire, towards the gate)
     * @param restSpots  two cells side by side (feet) where the survivors rest, mentor's first: never in the opening, on
     *                   the steps beside it, on the walkway out in front of it or within a block of the arrival spot.
     *                   Spire: (3, ground+8, 1) and (3, ground+8, 2), on the dais east of the step. Empty only if no free
     *                   pair was found (never for the spire or the placeholder).
     * @param groundY    the Y of the ground block the hub stands on (its levelled ground)
     * @param fitted     true if the ground under and around {@code footprint} was fitted (levelled, staircase edges) when
     *                   it was built; false for older worlds' hubs and fallbacks built next to something else
     */
    public record Layout(Kind kind, BoundingBox footprint, PortalOpening exit, Vec3 arrival, float arrivalYaw,
                         List<BlockPos> restSpots, int groundY, boolean fitted)
    {
        /** A copy (a bounding box can be changed in place). */
        @Override
        public BoundingBox footprint()
        {
            return new BoundingBox(footprint.minX(), footprint.minY(), footprint.minZ(), footprint.maxX(), footprint.maxY(), footprint.maxZ());
        }
    }

    /** An older world's broken exit frame is only reported once per run. */
    private static boolean warnedBrokenOldFrame;
    /**
     * True while the hub is being built: something the build sets off (a chunk loading, say) that asks for the hub
     * again gets "no exit portal yet" instead of starting a second build.
     */
    private static boolean building;

    private DrossHub() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        MinecraftServer server = event.getServer();
        if (server.getLevel(ModDimensions.DROSS_LEVEL) == null)
        {
            return;
        }
        try
        {
            // Builds the hub the first time (before anyone can join), and works out an older hub's layout once.
            Optional<Layout> layout = layout(server);
            if (layout.isPresent())
            {
                LOGGER.info(describe(layout.get()));
            }
            else
            {
                LOGGER.warn("Dross hub: the hub has no exit portal, so it has no layout.");
            }
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Dross hub: getting the hub ready at server start failed.", e);
        }
    }

    /**
     * Makes sure the hub exists (building it the first time) and returns its exit portal's opening.
     * Empty only if the exit portal's frame has been destroyed (for example with commands), or (asked from something the
     * build itself set off) while it's still being built.
     * Must be called on the server thread with the Dross level.
     */
    public static Optional<PortalOpening> ensureBuilt(ServerLevel dross)
    {
        DrossHubData data = DrossHubData.get(dross.getServer());
        if (!data.isBuilt())
        {
            if (building)
            {
                return Optional.empty();
            }
            long start = Util.getMillis();
            DrossHubBuilder.Result result;
            building = true;
            try
            {
                result = DrossHubBuilder.build(dross);
            }
            catch (RuntimeException e)
            {
                LOGGER.error("Dross hub: building the hub failed, so this world has no hub (arrivals land on safe ground near its centre).", e);
                result = DrossHubBuilder.Result.failed();
            }
            finally
            {
                building = false;
            }
            long millis = Util.getMillis() - start;
            data.markBuilt(result, millis);
            if (result.exit() != null)
            {
                PortalInfo spot = DrossTeleporter.arrivalSpot(dross, result.exit());
                data.setArrival(spot.pos, spot.yRot);
            }
            LOGGER.info("Dross hub: built ({}{}) in {} ms", result.kind(), result.fitted() ? ", on fitted ground" : "", millis);
        }
        return findExit(dross, data);
    }

    /**
     * Where everything at the hub is (see {@link Layout}). Builds the hub first if it isn't built yet. For a world whose
     * hub was built before layouts were saved, the layout is worked out once from its real exit frame and saved (no
     * blocks are changed): a 3x5 opening on the centre is the spire, a 2x3 opening the placeholder.
     * Server thread only.
     *
     * @return empty if the Dross isn't loaded, or the hub has no exit portal at all
     */
    public static Optional<Layout> layout(MinecraftServer server)
    {
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            return Optional.empty();
        }
        Optional<PortalOpening> lit = ensureBuilt(dross);
        DrossHubData data = DrossHubData.get(server);
        PortalOpening exit = lit.orElseGet(data::getRememberedExit);
        if (exit == null)
        {
            return Optional.empty();
        }

        Kind kind;
        BoundingBox footprint;
        int groundY;
        if (data.hasLayout())
        {
            kind = data.getKind();
            footprint = data.getFootprint();
            groundY = data.getGroundY();
        }
        else
        {
            DrossHubLayouts.Derived derived = DrossHubLayouts.derive(exit);
            kind = derived.kind();
            footprint = derived.footprint();
            groundY = derived.groundY();
            if (lit.isPresent())
            {
                data.rememberExit(exit);
                data.rememberLayout(kind, footprint, groundY);
                LOGGER.info("Dross hub: this world's hub was built before its layout was saved; worked out from its exit frame "
                                + "(opening {} along {}, {}x{}): {}, ground level {}, footprint {}. Nothing was rebuilt.",
                        exit.minCorner().toShortString(), exit.axis(), exit.width(), exit.height(), kind, groundY, describe(footprint));
            }
            else if (!warnedBrokenOldFrame)
            {
                warnedBrokenOldFrame = true;
                LOGGER.warn("Dross hub: this world's hub was built before its layout was saved, and its exit frame at {} is broken, so its "
                        + "layout is only a guess ({}) and isn't saved.", exit.minCorner().toShortString(), kind);
            }
        }

        Vec3 arrival = data.getArrival();
        float yaw = data.getArrivalYaw();
        if (arrival == null)
        {
            PortalInfo spot = DrossTeleporter.arrivalSpot(dross, exit);
            arrival = spot.pos;
            yaw = spot.yRot;
            if (lit.isPresent())
            {
                data.setArrival(arrival, yaw);
            }
        }
        List<BlockPos> restSpots = DrossHubLayouts.restSpots(dross, exit, arrival);
        return Optional.of(new Layout(kind, footprint, exit, arrival, yaw, restSpots, groundY, data.isFitted()));
    }

    /**
     * Teleports a player to the hub, in front of the exit portal (for the {@code /dross goto hub} test command).
     * Doesn't play the arrival sequence: that's only for travelling through the portal.
     *
     * @return false if the Dross dimension isn't loaded
     */
    public static boolean teleportToHub(ServerPlayer player)
    {
        ServerLevel dross = player.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            return false;
        }
        PortalInfo info = arrival(dross, player);
        player.teleportTo(dross, info.pos.x, info.pos.y, info.pos.z, info.yRot, info.xRot);
        return true;
    }

    /**
     * Where an entity arriving in the Dross lands: in front of the hub's exit portal, facing away from it. In the
     * spire that's on the step in front of the frame, facing the gate.
     */
    static PortalInfo arrival(ServerLevel dross, Entity entity)
    {
        Optional<PortalOpening> exit = ensureBuilt(dross);
        if (exit.isPresent())
        {
            return DrossTeleporter.arrivalAt(dross, entity, exit.get());
        }
        // The exit portal's frame is broken (only creative players or commands can do that). Land where the exit was,
        // if that's still safe, rather than on top of whatever is highest at the centre (the spire's highest frame
        // shard floats some 50 blocks above its hall).
        DrossHubData data = DrossHubData.get(dross.getServer());
        PortalOpening remembered = data.getRememberedExit();
        if (remembered != null)
        {
            Optional<PortalInfo> spot = DrossTeleporter.safeArrivalNear(dross, entity, remembered);
            if (spot.isPresent())
            {
                LOGGER.warn("Dross hub: the exit portal's frame at {} is broken; landing {} where it was.",
                        remembered.minCorner().toShortString(), entity.getName().getString());
                return spot.get();
            }
        }
        // Then the hub's arrival spot, if it's still safe.
        Vec3 saved = data.getArrival();
        if (saved != null && DrossTeleporter.isSafeFor(dross, entity, saved))
        {
            LOGGER.warn("Dross hub: the exit portal is missing; landing {} on the hub's arrival spot.", entity.getName().getString());
            return new PortalInfo(saved, Vec3.ZERO, data.getArrivalYaw(), 0.0F);
        }
        LOGGER.warn("Dross hub: the exit portal is missing; landing {} on safe ground near the hub centre instead.", entity.getName().getString());
        return DrossTeleporter.safeLanding(dross, entity, CENTER.getX(), CENTER.getZ());
    }

    /**
     * The remembered exit portal (never any other frame). Relights it if its frame is still complete but the portal
     * has gone out.
     */
    private static Optional<PortalOpening> findExit(ServerLevel dross, DrossHubData data)
    {
        BlockPos exit = data.getExitPortal();
        if (exit == null)
        {
            return Optional.empty();
        }
        Optional<DrossPortalShape> shape = DrossPortalShape.findAnyPortalShape(dross, exit, data.getExitAxis());
        if (shape.isEmpty())
        {
            return Optional.empty();
        }
        if (!shape.get().isComplete())
        {
            shape.get().createPortalBlocks();
            LOGGER.info("Dross hub: relit the exit portal at {}", exit.toShortString());
        }
        return Optional.of(PortalOpening.of(shape.get()));
    }

    /** One line for the log. */
    static String describe(Layout layout)
    {
        PortalOpening exit = layout.exit();
        Vec3 arrival = layout.arrival();
        String rest = layout.restSpots().isEmpty() ? "none found"
                : layout.restSpots().stream().map(BlockPos::toShortString).collect(Collectors.joining(" and "));
        return String.format(Locale.ROOT, "Dross hub: %s%s, ground level %d, footprint %s; exit portal %s along %s (%dx%d); arrival %.2f, %.2f, %.2f "
                        + "facing %s; survivors' rest spots %s",
                layout.kind(), layout.fitted() ? " on fitted ground" : "", layout.groundY(), describe(layout.footprint()),
                exit.minCorner().toShortString(), exit.axis(), exit.width(), exit.height(), arrival.x, arrival.y, arrival.z,
                Direction.fromYRot(layout.arrivalYaw()).getName(), rest);
    }

    private static String describe(BoundingBox box)
    {
        return String.format(Locale.ROOT, "X %d..%d, Y %d..%d, Z %d..%d", box.minX(), box.maxX(), box.minY(), box.maxY(), box.minZ(), box.maxZ());
    }
}
