package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.portal.camp.HubCamp;
import com.reg21meme.dross.world.shrine.ShrineDesign;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.Vec3;
import org.slf4j.Logger;

/**
 * The hub at Dross 0,0: where everyone arrives in the Dross, and the only way out (its exit portal leads to the castle
 * portal in the Overworld).
 * <ul>
 *   <li><b>Made with the land</b> (the user's decision of November 2, 2026, after two players' modded worlds froze while
 *       it was built at world start): a world-generation structure, {@code dross:dross_hub} ({@link DrossHubStructure},
 *       {@link DrossHubPiece}), built chunk by chunk the first time its chunks are generated, like any village. Nothing
 *       happens at world start; its chunks are made the normal way the first time someone enters the Dross.</li>
 *   <li>It's the <b>Shattered Spire</b> (shrine design No. 2, the user's pick), its frame opening's middle column on
 *       {@link #CENTER} and its gate facing south, the survivors' <b>camp</b> at the foot of its front steps
 *       ({@link HubCamp}: No. 3A), and its frame <b>lit</b>: the exit portal.</li>
 *   <li>It sits properly on the terrain ({@link DrossHubGround}): its footprint is levelled at the median of the land's
 *       own heights under it, and the ground round it steps back to the land at most one block per step
 *       ({@value #HUB_BLEND_MARGIN} blocks wide). The trees, plants and ores that come after it in world generation keep
 *       off it ({@code dross:not_on_hub}).</li>
 *   <li>{@link #layout} tells other areas where everything is. It's pure arithmetic from the world's seed, the same before
 *       any of the hub's chunks exist as after.</li>
 *   <li>Arriving ({@link #arrival}) loads its chunks the normal way, as vanilla does for a nether portal trip, and relights
 *       the exit if its frame is whole but its portal has gone out.</li>
 * </ul>
 * The spire's loose frame "shards" (on the ground, in its broken top, floating above it) are just decoration: they're
 * never looked for, and they can't make a frame.
 */
public final class DrossHub
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * The hub's centre in the Dross. Only X and Z matter (the hub is built on the ground, wherever that is),
     * so Y is just 0. The dimension area reads this for the hub's no-spawn safe zone: keep the name.
     */
    public static final BlockPos CENTER = new BlockPos(0, 0, 0);

    /**
     * How far around the hub's footprint (blocks) the ground is blended back into the natural terrain: a staircase, at
     * most one block per step. The same as the Overworld cathedral's.
     */
    public static final int HUB_BLEND_MARGIN = 16;
    /**
     * The walkway straight out in front of the exit portal, this many blocks deep (as wide as the opening), is kept
     * clear: the survivors' rest spots are never on it. In the spire it runs from the step down through the gate.
     */
    public static final int WALKWAY_DEPTH = 7;

    /** The shrine design built as the hub: No. 2, the Shattered Spire. */
    static final String SPIRE_DESIGN_ID = "shattered_spire";
    /** The way the spire's gate faces. South (the design as drawn, unturned) keeps its frame along X. */
    public static final Direction SPIRE_FACING = Direction.SOUTH;
    /**
     * An arrival loads the area round its spot with a portal ticket this many chunks round (vanilla's for a nether portal
     * trip), and the chunks its spot and the exit's frame are in at once.
     */
    private static final int ARRIVAL_TICKET_RADIUS = 3;
    /** An arrival that has to wait longer than this (ms) for its chunks is logged. */
    private static final long SLOW_ARRIVAL_MILLIS = 50L;
    /**
     * While a player stands in a Dross portal in the Overworld (a survival player's trip comes after vanilla's 4-second
     * wait), the chunks this many round their landing spot are made in the background ({@link #warmArrival}), by a ticket
     * that lapses after {@value #WARM_TICKS} ticks.
     */
    private static final int WARM_RADIUS = 2;
    private static final int WARM_TICKS = 20 * 20;
    private static final TicketType<ChunkPos> WARM_TICKET = TicketType.create("dross_hub_warm",
            Comparator.comparingLong(ChunkPos::toLong), WARM_TICKS);

    private static final ShrineDesign SPIRE = ShrineDesign.byId(SPIRE_DESIGN_ID)
            .orElseThrow(() -> new IllegalStateException("There's no shrine design called " + SPIRE_DESIGN_ID));
    /** The spire's footprint seen from above (its Y is for a ground level of 0). */
    private static final BoundingBox COLUMNS = SPIRE.footprintForFrameAt(CENTER.getX(), CENTER.getZ(), 0, SPIRE_FACING);

    /**
     * Where everything at the hub is.
     *
     * @param footprint  the box the spire covers: its whole design, X -25..49, Z -34..38, from 2 blocks under its ground
     *                   level to 61 above. Use X and Z to tell "at the hub".
     * @param exit       the exit portal's opening (where it is, whether or not its frame still stands)
     * @param arrival    where a player arriving through any Dross portal lands (feet; the middle of the block): on the step
     *                   in front of the frame, (0.5, ground+9, 1.5)
     * @param arrivalYaw the way they face there (0 = south: towards the gate)
     * @param restSpots  the survivors' two rest spots in their camp, mentor's first
     * @param groundY    the Y of the ground block the hub stands on (its levelled ground)
     * @param camp       where the survivors live in the spire's camp, at the foot of its front steps (their rest spots, the
     *                   places they walk to, its box); null only if the camp's design says nothing of them
     */
    public record Layout(BoundingBox footprint, PortalOpening exit, Vec3 arrival, float arrivalYaw, List<BlockPos> restSpots,
                         int groundY, @Nullable HubCamp.Spots camp)
    {
        /** A copy (a bounding box can be changed in place). */
        @Override
        public BoundingBox footprint()
        {
            return copy(footprint);
        }
    }

    private DrossHub() {}

    // ------------------------------------------------------------------ where it is (pure arithmetic)

    /** The Shattered Spire's design. */
    public static ShrineDesign spire()
    {
        return SPIRE;
    }

    /** The hub's footprint seen from above: only its X and Z count (a copy). */
    public static BoundingBox columns()
    {
        return copy(COLUMNS);
    }

    /** True if the column is on the hub's footprint, or within {@code margin} blocks of it (along X and Z). */
    public static boolean isOnHub(int x, int z, int margin)
    {
        return x >= COLUMNS.minX() - margin && x <= COLUMNS.maxX() + margin && z >= COLUMNS.minZ() - margin && z <= COLUMNS.maxZ() + margin;
    }

    /** The middle of the spire's footprint (its ground block) when it stands on ground level {@code groundY}. */
    public static BlockPos spireCenter(int groundY)
    {
        return SPIRE.centerForFrameAt(CENTER.getX(), CENTER.getZ(), groundY, SPIRE_FACING);
    }

    /** The hub's ground level in this Dross ({@link DrossHubGround#groundY}: from the world's seed, remembered). */
    public static int groundY(ServerLevel dross)
    {
        return DrossHubGround.groundY(dross.getChunkSource().getGenerator(), dross.getChunkSource().randomState(), dross);
    }

    /** Where everything at a hub standing on ground level {@code groundY} is. */
    public static Layout layoutAt(int groundY)
    {
        BlockPos center = spireCenter(groundY);
        ShrineDesign.Built built = SPIRE.built(center, SPIRE_FACING);
        PortalOpening exit = new PortalOpening(built.openingMin(), built.frameAxis(), built.openingWidth(), built.openingHeight());
        // On the step one block out in front of the opening (its +Z side for a frame along X), at its bottom, facing out.
        BlockPos min = exit.minCorner();
        boolean alongX = exit.axis() == Direction.Axis.X;
        Vec3 arrival = alongX ? new Vec3(exit.centerAlong(), min.getY(), min.getZ() + 1.5D)
                : new Vec3(min.getX() + 1.5D, min.getY(), exit.centerAlong());
        float yaw = alongX ? 0.0F : -90.0F;
        HubCamp.Spots camp = HubCamp.spots(HubCamp.hubCamp(), center, SPIRE_FACING);
        List<BlockPos> rest = camp == null ? List.of() : List.of(camp.mentor(), camp.student());
        return new Layout(built.footprint(), exit, arrival, yaw, rest, groundY, camp);
    }

    /**
     * Where everything at the hub is (see {@link Layout}), worked out from the world's seed: nothing is loaded or built.
     * The first call in a world samples the generator's heights (about a tenth of a second). Any thread.
     *
     * @return empty only if the Dross isn't loaded
     */
    public static Optional<Layout> layout(MinecraftServer server)
    {
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        return dross == null ? Optional.empty() : Optional.of(layoutAt(groundY(dross)));
    }

    // ------------------------------------------------------------------ arriving

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
     * Where an entity arriving in the Dross lands: in front of the hub's exit portal, facing away from it (in the spire,
     * on the step in front of the frame, facing the gate). Like a nether portal trip, the chunks its spot and the exit's
     * frame are in are loaded (and the first time, generated, the hub with them) before it lands, and a portal ticket keeps
     * the area round it loading. The exit is relit if its frame is whole but its portal has gone out. Server thread.
     */
    static PortalInfo arrival(ServerLevel dross, Entity entity)
    {
        Layout layout = layoutAt(groundY(dross));
        loadForArrival(dross, layout);
        Optional<PortalOpening> exit = relightExit(dross, layout);
        if (exit.isPresent())
        {
            return DrossTeleporter.arrivalAt(dross, entity, exit.get());
        }
        // The exit portal's frame is broken (only creative players or commands can do that). Land where the exit is, if
        // that's still safe, rather than on top of whatever is highest at the centre (the spire's highest frame shard
        // floats some 50 blocks above its hall).
        Optional<PortalInfo> spot = DrossTeleporter.safeArrivalNear(dross, entity, layout.exit());
        if (spot.isPresent())
        {
            LOGGER.warn("Dross hub: the exit portal's frame at {} is broken; landing {} where it was.",
                    layout.exit().minCorner().toShortString(), entity.getName().getString());
            return spot.get();
        }
        if (DrossTeleporter.isSafeFor(dross, entity, layout.arrival()))
        {
            LOGGER.warn("Dross hub: the exit portal is missing; landing {} on the hub's arrival spot.", entity.getName().getString());
            return new PortalInfo(layout.arrival(), Vec3.ZERO, layout.arrivalYaw(), 0.0F);
        }
        LOGGER.warn("Dross hub: the exit portal is missing; landing {} on safe ground near the hub's arrival spot instead.",
                entity.getName().getString());
        return DrossTeleporter.safeLanding(dross, entity, Mth.floor(layout.arrival().x), Mth.floor(layout.arrival().z));
    }

    /**
     * Has the chunks an arrival lands in made in the background, ahead of it (a player standing in a Dross portal: the
     * trip comes when its wait is over), so the first trip into the Dross finds the hub's chunks ready rather than making
     * them on the server thread as it lands. Nothing waits for it, and its ticket lapses by itself. Server thread.
     */
    public static void warmArrival(ServerLevel dross)
    {
        ChunkPos at = new ChunkPos(BlockPos.containing(layoutAt(groundY(dross)).arrival()));
        dross.getChunkSource().addRegionTicket(WARM_TICKET, at, WARM_RADIUS, at);
    }

    /**
     * The chunks the arrival spot and the exit's frame are in, loaded now (generated the first time, the hub with them),
     * and a portal ticket round the arrival spot, as a nether portal trip does.
     */
    private static void loadForArrival(ServerLevel dross, Layout layout)
    {
        long start = System.nanoTime();
        BlockPos spot = BlockPos.containing(layout.arrival());
        ChunkPos at = new ChunkPos(spot);
        dross.getChunkSource().addRegionTicket(TicketType.PORTAL, at, ARRIVAL_TICKET_RADIUS, spot);
        BoundingBox frame = frameBox(layout.exit());
        int made = 0;
        for (int cx = Math.min(at.x, SectionPos.blockToSectionCoord(frame.minX())); cx <= Math.max(at.x, SectionPos.blockToSectionCoord(frame.maxX())); cx++)
        {
            for (int cz = Math.min(at.z, SectionPos.blockToSectionCoord(frame.minZ())); cz <= Math.max(at.z, SectionPos.blockToSectionCoord(frame.maxZ())); cz++)
            {
                if (dross.getChunkSource().getChunkNow(cx, cz) == null)
                {
                    made++;
                }
                dross.getChunk(cx, cz);
            }
        }
        long millis = (System.nanoTime() - start) / 1_000_000L;
        if (made > 0 && millis > SLOW_ARRIVAL_MILLIS)
        {
            LOGGER.info("Dross hub: an arrival waited {} ms for {} of the hub's chunks to load (or be made the first time)", millis, made);
        }
    }

    /**
     * The hub's exit portal, if its frame is whole: relit if its portal has gone out. Only looked at if the chunks its frame
     * is in are loaded (never loads anything); empty if they aren't, or the frame is broken.
     */
    static Optional<PortalOpening> relightExit(ServerLevel dross, Layout layout)
    {
        PortalOpening exit = layout.exit();
        BoundingBox frame = frameBox(exit);
        for (int cx = SectionPos.blockToSectionCoord(frame.minX()); cx <= SectionPos.blockToSectionCoord(frame.maxX()); cx++)
        {
            for (int cz = SectionPos.blockToSectionCoord(frame.minZ()); cz <= SectionPos.blockToSectionCoord(frame.maxZ()); cz++)
            {
                if (dross.getChunkSource().getChunkNow(cx, cz) == null)
                {
                    return Optional.empty();
                }
            }
        }
        Optional<DrossPortalShape> shape = DrossPortalShape.findAnyPortalShape(dross, exit.minCorner(), exit.axis());
        if (shape.isEmpty())
        {
            return Optional.empty();
        }
        if (!shape.get().isComplete())
        {
            shape.get().createPortalBlocks();
            LOGGER.info("Dross hub: relit the exit portal at {}", exit.minCorner().toShortString());
        }
        return Optional.of(PortalOpening.of(shape.get()));
    }

    /** The exit's frame round its opening (corners included). */
    private static BoundingBox frameBox(PortalOpening exit)
    {
        BlockPos min = exit.minCorner();
        boolean alongX = exit.axis() == Direction.Axis.X;
        int maxAlong = exit.width();
        return new BoundingBox(min.getX() - (alongX ? 1 : 0), min.getY() - 1, min.getZ() - (alongX ? 0 : 1),
                min.getX() + (alongX ? maxAlong : 0), min.getY() + exit.height(), min.getZ() + (alongX ? 0 : maxAlong));
    }

    // ------------------------------------------------------------------ helpers

    /** One line for the log. */
    static String describe(Layout layout)
    {
        PortalOpening exit = layout.exit();
        Vec3 arrival = layout.arrival();
        String rest = layout.restSpots().isEmpty() ? "none"
                : layout.restSpots().stream().map(BlockPos::toShortString).collect(Collectors.joining(" and "));
        BoundingBox box = layout.footprint();
        return String.format(Locale.ROOT, "Dross hub: the %s, ground level %d, footprint X %d..%d, Y %d..%d, Z %d..%d; exit portal %s along %s "
                        + "(%dx%d); arrival %.2f, %.2f, %.2f facing %s; survivors' rest spots %s%s",
                SPIRE.name(), layout.groundY(), box.minX(), box.maxX(), box.minY(), box.maxY(), box.minZ(), box.maxZ(),
                exit.minCorner().toShortString(), exit.axis(), exit.width(), exit.height(), arrival.x, arrival.y, arrival.z,
                Direction.fromYRot(layout.arrivalYaw()).getName(), rest, layout.camp() == null ? "" : " (in their camp)");
    }

    private static BoundingBox copy(BoundingBox box)
    {
        return new BoundingBox(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ());
    }
}
