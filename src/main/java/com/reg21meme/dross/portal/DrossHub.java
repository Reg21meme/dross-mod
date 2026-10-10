package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.shrine.ShrineDesign;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;

/**
 * The hub at Dross 0,0: where everyone arrives in the Dross, and the only way out (its exit portal
 * leads to the castle portal in the Overworld).
 * <ul>
 *   <li>Built <b>once per world</b> (remembered in {@link DrossHubData}): when the world starts, or at the
 *       latest right before the first entity arrives. Worlds that already have a hub keep it.</li>
 *   <li>Which hub, first match wins:
 *     <ol>
 *       <li>The user's own design: if the structure template {@code data/dross/structures/dross_hub.nbt} exists
 *           (saved with a structure block as {@code dross:dross_hub}), it's placed centred on {@link #CENTER}, and
 *           its Dross portal frame is found (and lit if it isn't already).</li>
 *       <li>Otherwise the <b>Shattered Spire</b> (shrine design No. 2, see {@code world.shrine.ShrineDesign}): a
 *           colossal snapped tower whose hall holds a Dross frame on a dais. It's built with that frame's opening
 *           (3 wide, 5 tall, running along X) centred on {@link #CENTER} and its gate facing south, and the frame is
 *           lit: it's the exit portal.</li>
 *       <li>Only if the spire can't be built, or its frame can't be found or lit (an error is logged): a
 *           <b>placeholder</b>, an 11x11 stone platform on the ground with a lit exit portal (a 4x5
 *           {@code dross_portal_frame} frame along X, opening 2x3) in the middle.</li>
 *     </ol></li>
 *   <li>The exit portal's position, direction and size are remembered, and only that frame is the exit. The spire's
 *       loose frame "shards" (on the ground, in its broken top, floating above it) are just decoration: they're never
 *       looked for, and they can't make a frame. If the exit ever goes out, it's relit.</li>
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
     * The template's bottom layer goes this many blocks below the first air block above the ground.
     * -1 means the template's bottom layer replaces the top layer of the ground (save the template with its floor).
     */
    private static final int TEMPLATE_Y_OFFSET = -1;

    // The Shattered Spire
    /** The shrine design built as the hub when the user has no hub template of their own: No. 2, the Shattered Spire. */
    private static final String SPIRE_DESIGN_ID = "shattered_spire";
    /** The way the spire's gate faces. South (the design as drawn, unturned) keeps its frame along X. */
    private static final Direction SPIRE_FACING = Direction.SOUTH;

    // Placeholder hub
    /** The platform reaches this many blocks out from the centre on each side (5 -> 11x11). */
    private static final int PLATFORM_RADIUS = 5;
    /** Air cleared above the platform. */
    private static final int CLEAR_HEIGHT = 6;
    /** Under the platform, fill up to this many blocks of air/liquid so it never floats over a drop. */
    private static final int SUPPORT_DEPTH = 3;
    /** Exit portal frame, corners included: 4 wide x 5 tall (opening 2x3, like the smallest nether portal). */
    private static final int EXIT_FRAME_WIDTH = 4;
    private static final int EXIT_FRAME_HEIGHT = 5;
    private static final BlockState PLATFORM = Blocks.POLISHED_DEEPSLATE.defaultBlockState();
    private static final BlockState PLATFORM_BORDER = Blocks.POLISHED_BLACKSTONE_BRICKS.defaultBlockState();

    private DrossHub() {}

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        ServerLevel dross = event.getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross != null)
        {
            ensureBuilt(dross);
        }
    }

    /**
     * Makes sure the hub exists (building it the first time) and returns its exit portal's opening.
     * Empty only if the exit portal's frame has been destroyed (for example with commands).
     * Must be called on the server thread with the Dross level.
     */
    public static Optional<PortalOpening> ensureBuilt(ServerLevel dross)
    {
        DrossHubData data = DrossHubData.get(dross.getServer());
        if (!data.isBuilt())
        {
            data.markBuilt(build(dross));
        }
        return findExit(dross, data);
    }

    /**
     * Teleports a player to the hub, in front of the exit portal (for the {@code /dross hub} test command).
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
     * spire that's on the steps up to the frame (or the dais), facing the gate.
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
        PortalOpening remembered = DrossHubData.get(dross.getServer()).getRememberedExit();
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
        LOGGER.warn("Dross hub: the exit portal is missing; landing {} at the hub centre instead.", entity.getName().getString());
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

    // ------------------------------------------------------------------ building

    /** Builds the hub (the user's template, the spire, or the placeholder) and returns its lit exit portal (null if it has none). */
    @Nullable
    private static PortalOpening build(ServerLevel dross)
    {
        // 1. The user's own hub design.
        Optional<StructureTemplate> template = dross.getStructureManager().get(TEMPLATE);
        if (template.isPresent())
        {
            PortalOpening exit = placeTemplate(dross, template.get());
            if (exit != null)
            {
                LOGGER.info("Dross hub: placed the {} template; exit portal at {}", TEMPLATE, exit.minCorner().toShortString());
                return exit;
            }
            LOGGER.warn("Dross hub: the {} template has no complete Dross portal frame, so the placeholder exit portal is built over it.", TEMPLATE);
            return buildPlaceholder(dross, CENTER.getX(), CENTER.getZ());
        }

        // 2. The Shattered Spire.
        Optional<ShrineDesign> spire = ShrineDesign.byId(SPIRE_DESIGN_ID);
        if (spire.isEmpty())
        {
            LOGGER.error("Dross hub: there's no shrine design called '{}' (world.shrine.ShrineDesigns), so the placeholder hub is built instead.",
                    SPIRE_DESIGN_ID);
            return buildPlaceholder(dross, CENTER.getX(), CENTER.getZ());
        }
        return buildSpire(dross, spire.get());
    }

    /**
     * Builds the Shattered Spire with its frame opening centred on the hub centre, and lights that frame.
     * Falls back to the placeholder (with an error in the log) if the spire can't be built or its frame can't be lit.
     */
    @Nullable
    private static PortalOpening buildSpire(ServerLevel dross, ShrineDesign spire)
    {
        if (dross.getStructureManager().get(spire.template()).isEmpty())
        {
            // Checked first so that nothing is built at all: the placeholder then has the centre to itself.
            LOGGER.error("Dross hub: the {}'s template {} is missing (data/dross/structures/shrine/{}.nbt), so the placeholder hub is built instead.",
                    spire.name(), spire.template(), spire.id());
            return buildPlaceholder(dross, CENTER.getX(), CENTER.getZ());
        }

        Optional<ShrineDesign.Built> built;
        try
        {
            built = spire.buildWithFrameAt(dross, CENTER.getX(), CENTER.getZ(), SPIRE_FACING);
        }
        catch (RuntimeException e)
        {
            LOGGER.error("Dross hub: building the {} failed, so the placeholder hub is built in front of where its gate would be.", spire.name(), e);
            return buildPlaceholderBesideSpire(dross, spire);
        }
        if (built.isEmpty())
        {
            // buildWithFrameAt only gives up if the template is missing, and then it hasn't built anything.
            LOGGER.error("Dross hub: the {} couldn't be built (its template {} is missing), so the placeholder hub is built instead.",
                    spire.name(), spire.template());
            return buildPlaceholder(dross, CENTER.getX(), CENTER.getZ());
        }

        ShrineDesign.Built placed = built.get();
        PortalOpening exit = lightSpireFrame(dross, placed);
        if (exit == null)
        {
            return buildPlaceholderBesideSpire(dross, spire); // the error is already in the log
        }
        LOGGER.info("Dross hub: built the {} (shrine design No. {}) facing {}, ground level {}; its frame is the exit portal: "
                        + "opening {} along {} ({}x{}), lit",
                spire.name(), spire.number(), placed.facing().getName(), placed.center().getY(),
                exit.minCorner().toShortString(), exit.axis(), exit.width(), exit.height());
        return exit;
    }

    /**
     * Lights the built spire's frame, found exactly where its design says it is (the frame around the design's opening;
     * the loose shards are never looked at).
     *
     * @return the lit exit portal, or null (with an error in the log) if there's no complete frame there or it didn't light
     */
    @Nullable
    private static PortalOpening lightSpireFrame(ServerLevel dross, ShrineDesign.Built built)
    {
        String name = built.design().name();
        Optional<DrossPortalShape> shape = DrossPortalShape.findAnyPortalShape(dross, built.openingMin(), built.frameAxis());
        if (shape.isEmpty())
        {
            LOGGER.error("Dross hub: the {} was built at {}, but there's no complete Dross portal frame around its opening at {} (axis {}, {}x{}), "
                            + "so its exit portal can't be lit; the placeholder hub is built in front of its gate instead.",
                    name, built.center().toShortString(), built.openingMin().toShortString(), built.frameAxis(),
                    built.openingWidth(), built.openingHeight());
            return null;
        }
        if (!shape.get().isComplete())
        {
            shape.get().createPortalBlocks();
        }
        PortalOpening exit = PortalOpening.of(shape.get());
        if (DrossPortalShape.findLitPortalShape(dross, exit.minCorner(), exit.axis()).isEmpty())
        {
            LOGGER.error("Dross hub: the {}'s frame at {} didn't light, so the placeholder hub is built in front of its gate instead.",
                    name, exit.minCorner().toShortString());
            return null;
        }
        if (!exit.minCorner().equals(built.openingMin()) || exit.axis() != built.frameAxis()
                || exit.width() != built.openingWidth() || exit.height() != built.openingHeight())
        {
            LOGGER.warn("Dross hub: the {}'s frame opening is {} along {} ({}x{}), not {} along {} ({}x{}) as its design says; "
                            + "using the frame that's there.",
                    name, exit.minCorner().toShortString(), exit.axis(), exit.width(), exit.height(),
                    built.openingMin().toShortString(), built.frameAxis(), built.openingWidth(), built.openingHeight());
        }
        return exit;
    }

    /**
     * The placeholder, for when the spire stands (maybe only partly) at the hub centre but has no working exit portal:
     * on open ground straight in front of its gate, clear of its footprint, so nobody arrives on top of the spire.
     */
    @Nullable
    private static PortalOpening buildPlaceholderBesideSpire(ServerLevel dross, ShrineDesign spire)
    {
        BlockPos center = spire.centerForFrameAt(CENTER.getX(), CENTER.getZ(), 0, SPIRE_FACING);
        BlockPos spot = spire.entrance(center, SPIRE_FACING).relative(SPIRE_FACING, PLATFORM_RADIUS + 1);
        return buildPlaceholder(dross, spot.getX(), spot.getZ());
    }

    /** Places the template centred on the hub centre, on the ground, then finds (and lights) its exit portal. */
    @Nullable
    private static PortalOpening placeTemplate(ServerLevel dross, StructureTemplate template)
    {
        Vec3i size = template.getSize();
        int ground = groundY(dross, CENTER.getX(), CENTER.getZ());
        BlockPos origin = new BlockPos(CENTER.getX() - size.getX() / 2, ground + TEMPLATE_Y_OFFSET, CENTER.getZ() - size.getZ() / 2);
        // "Known shape": don't let blocks react to each other while the template is half placed
        // (a portal block placed before its frame would otherwise go out straight away).
        StructurePlaceSettings settings = new StructurePlaceSettings().setKnownShape(true);
        template.placeInWorld(dross, origin, origin, settings, dross.getRandom(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);

        // 1. A portal saved in the template (lit when it was saved): make sure it's fully lit.
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, ModBlocks.DROSS_PORTAL.get()))
        {
            Direction.Axis axis = info.state().hasProperty(DrossPortalBlock.AXIS) ? info.state().getValue(DrossPortalBlock.AXIS) : Direction.Axis.X;
            Optional<DrossPortalShape> shape = DrossPortalShape.findAnyPortalShape(dross, info.pos(), axis);
            if (shape.isPresent())
            {
                if (!shape.get().isComplete())
                {
                    shape.get().createPortalBlocks();
                }
                return PortalOpening.of(shape.get());
            }
        }
        // 2. Otherwise an empty frame in the template: light it.
        for (StructureTemplate.StructureBlockInfo info : template.filterBlocks(origin, settings, ModBlocks.DROSS_PORTAL_FRAME.get()))
        {
            Optional<DrossPortalShape> shape = DrossPortalShape.findEmptyPortalShape(dross, info.pos().above(), Direction.Axis.X);
            if (shape.isPresent())
            {
                shape.get().createPortalBlocks();
                return PortalOpening.of(shape.get());
            }
        }
        return null;
    }

    /**
     * The placeholder: an 11x11 platform whose top is level with the ground, with the exit portal frame in the
     * middle. The frame's bottom row is set into the platform, so you walk straight into the portal.
     * Seen from above (Z grows downwards, F = frame, the opening is the middle two F columns' gap):
     * <pre>
     *   # # # # # # # # # # #
     *   # . . . . . . . . . #
     *   # . . . F F F F . . #    z = centre; frame x from centre-2 to centre+1
     *   # . . . . . . . . . #    arrival: one block in front (+Z), facing away from the portal
     *   # # # # # # # # # # #
     * </pre>
     * Normally centred on the hub centre; {@code cx}/{@code cz} is its centre column.
     */
    @Nullable
    private static PortalOpening buildPlaceholder(ServerLevel dross, int cx, int cz)
    {
        int ground = groundY(dross, cx, cz);
        int floorY = ground - 1;
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int dx = -PLATFORM_RADIUS; dx <= PLATFORM_RADIUS; dx++)
        {
            for (int dz = -PLATFORM_RADIUS; dz <= PLATFORM_RADIUS; dz++)
            {
                boolean border = Math.abs(dx) == PLATFORM_RADIUS || Math.abs(dz) == PLATFORM_RADIUS;
                dross.setBlock(pos.set(cx + dx, floorY, cz + dz), border ? PLATFORM_BORDER : PLATFORM, Block.UPDATE_ALL);
                for (int d = 1; d <= SUPPORT_DEPTH; d++)
                {
                    pos.set(cx + dx, floorY - d, cz + dz);
                    BlockState below = dross.getBlockState(pos);
                    if (!below.isAir() && below.getFluidState().isEmpty())
                    {
                        break;
                    }
                    dross.setBlock(pos, PLATFORM, Block.UPDATE_ALL);
                }
                for (int y = ground; y < ground + CLEAR_HEIGHT; y++)
                {
                    pos.set(cx + dx, y, cz + dz);
                    if (!dross.getBlockState(pos).isAir())
                    {
                        dross.setBlock(pos, air, Block.UPDATE_ALL);
                    }
                }
            }
        }

        // The exit frame, along X, centred on the platform: the opening is x = cx-1 .. cx.
        BlockState frame = ModBlocks.DROSS_PORTAL_FRAME.get().defaultBlockState();
        int frameMinX = cx - EXIT_FRAME_WIDTH / 2;
        for (int i = 0; i < EXIT_FRAME_WIDTH; i++)
        {
            for (int j = 0; j < EXIT_FRAME_HEIGHT; j++)
            {
                boolean edge = i == 0 || i == EXIT_FRAME_WIDTH - 1 || j == 0 || j == EXIT_FRAME_HEIGHT - 1;
                if (edge)
                {
                    dross.setBlock(pos.set(frameMinX + i, floorY + j, cz), frame, Block.UPDATE_ALL);
                }
            }
        }

        Optional<DrossPortalShape> shape = DrossPortalShape.findEmptyPortalShape(dross, new BlockPos(frameMinX + 1, ground, cz), Direction.Axis.X);
        if (shape.isEmpty())
        {
            LOGGER.error("Dross hub: built the placeholder platform at {} {}, but its exit portal frame isn't complete; the hub has no exit portal.", cx, cz);
            return null;
        }
        shape.get().createPortalBlocks();
        PortalOpening exit = PortalOpening.of(shape.get());
        LOGGER.info("Dross hub: built the placeholder hub at {} {}; exit portal at {}", cx, cz, exit.minCorner().toShortString());
        return exit;
    }

    /** The first air block above the ground at this column (found with the heightmap, never a fixed Y). */
    private static int groundY(ServerLevel dross, int x, int z)
    {
        int ground = DrossTeleporter.surfaceY(dross, x, z);
        if (ground <= dross.getMinBuildHeight() + 1)
        {
            // No ground at all here (void): build at sea level, on the support filled in under the platform.
            ground = dross.getSeaLevel();
        }
        return Mth.clamp(ground, dross.getMinBuildHeight() + SUPPORT_DEPTH + 1, dross.getMaxBuildHeight() - CLEAR_HEIGHT - 1);
    }
}
