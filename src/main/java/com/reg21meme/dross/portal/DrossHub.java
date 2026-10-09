package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModBlocks;
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
 *       latest right before the first entity arrives.</li>
 *   <li>If the structure template {@code data/dross/structures/dross_hub.nbt} exists (the user designs it later
 *       with a structure block, saved as {@code dross:dross_hub}), it's placed centred on {@link #CENTER},
 *       and its Dross portal frame is found (and lit if it isn't already).</li>
 *   <li>Otherwise a <b>placeholder</b> is built: an 11x11 stone platform on the ground with a lit exit portal
 *       (a 4x5 {@code dross_portal_frame} frame along X, opening 2x3) in the middle.</li>
 *   <li>The exit portal's position and direction are remembered. If it ever goes out, it's relit.</li>
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

    /** Where an entity arriving in the Dross lands: in front of the hub's exit portal. */
    static PortalInfo arrival(ServerLevel dross, Entity entity)
    {
        Optional<PortalOpening> exit = ensureBuilt(dross);
        if (exit.isPresent())
        {
            return DrossTeleporter.arrivalAt(dross, entity, exit.get());
        }
        LOGGER.warn("Dross hub: the exit portal is missing; landing {} at the hub centre instead.", entity.getName().getString());
        return DrossTeleporter.safeLanding(dross, entity, CENTER.getX(), CENTER.getZ());
    }

    /** The remembered exit portal. Relights it if its frame is still complete but the portal has gone out. */
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

    /** Builds the hub (template or placeholder) and returns its lit exit portal (null if it has none). */
    @Nullable
    private static PortalOpening build(ServerLevel dross)
    {
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
        }
        PortalOpening exit = buildPlaceholder(dross);
        LOGGER.info("Dross hub: built the placeholder hub; exit portal at {}", exit == null ? "(none)" : exit.minCorner().toShortString());
        return exit;
    }

    /** Places the template centred on the hub centre, on the ground, then finds (and lights) its exit portal. */
    @Nullable
    private static PortalOpening placeTemplate(ServerLevel dross, StructureTemplate template)
    {
        Vec3i size = template.getSize();
        int ground = groundY(dross);
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
     */
    @Nullable
    private static PortalOpening buildPlaceholder(ServerLevel dross)
    {
        int cx = CENTER.getX();
        int cz = CENTER.getZ();
        int ground = groundY(dross);
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

        // The exit frame, along X, centred on the hub centre: the opening is x = cx-1 .. cx.
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
            return null;
        }
        shape.get().createPortalBlocks();
        return PortalOpening.of(shape.get());
    }

    /** The first air block above the ground at the hub centre (found with the heightmap, never a fixed Y). */
    private static int groundY(ServerLevel dross)
    {
        int ground = DrossTeleporter.surfaceY(dross, CENTER.getX(), CENTER.getZ());
        if (ground <= dross.getMinBuildHeight() + 1)
        {
            // No ground at all here (void): build at sea level, on the support filled in under the platform.
            ground = dross.getSeaLevel();
        }
        return Mth.clamp(ground, dross.getMinBuildHeight() + SUPPORT_DEPTH + 1, dross.getMaxBuildHeight() - CLEAR_HEIGHT - 1);
    }
}
