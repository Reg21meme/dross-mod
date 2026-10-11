package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSite;
import com.reg21meme.dross.world.PortalSiteBuilder;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.util.ITeleporter;
import org.slf4j.Logger;

/**
 * Decides where an entity lands when it goes through a Dross portal.
 * <ul>
 *   <li><b>Into the Dross</b> (any Dross portal outside the Dross): always the <b>hub</b> at Dross 0,0, standing
 *       in front of its exit portal (see {@link DrossHub}). The hub is built the first time it's needed.</li>
 *   <li><b>Out of the Dross</b> (the hub's exit portal): always the <b>castle portal</b> in the Overworld
 *       ({@link PortalSite}), wherever the entity came in. Lit or not, it lands in front of that frame.
 *       Nothing is ever built in the Overworld. If the castle frame can't be found, the entity lands safely
 *       on the ground near the site's X/Z and a warning is logged.</li>
 * </ul>
 * The entity is always placed on solid ground in free space next to the portal (or, if both sides are
 * blocked, inside the portal itself, standing on the frame), never inside blocks or over a drop.
 * <p>
 * {@link #findSafeSpot} finds a safe place to stand on the ground near any column (other areas use it too, for
 * example to teleport players to a place in the Dross).
 */
public class DrossTeleporter implements ITeleporter
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far around the castle frame to look for a lit Dross portal, if the frame isn't where PortalSite says. */
    private static final int CASTLE_SEARCH_RADIUS = 16;
    /** How far (blocks, along X and Z) {@link #safeLanding} looks around its column for a safe spot ({@link #findSafeSpot}). */
    public static final int LANDING_SEARCH_RADIUS = 16;
    /**
     * The ground a safe spot stands on must have at least this many more solid blocks right under it, so a lone block
     * floating in the air (like the hub spire's frame shard, high over its hall) or a block on a branch is never "the ground".
     */
    public static final int GROUND_SUPPORT = 2;
    /** The size used when there's no entity to measure: a standing player (0.6 wide, 1.8 tall). */
    static final EntityDimensions PLAYER_SIZE = EntityType.PLAYER.getDimensions();

    @Override
    public boolean isVanilla()
    {
        return false;
    }

    @Override
    public boolean playTeleportSound(ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld)
    {
        return true; // the normal "portal travel" whoosh
    }

    @Override
    public Entity placeEntity(Entity entity, ServerLevel currentWorld, ServerLevel destWorld, float yaw, Function<Boolean, Entity> repositionEntity)
    {
        // false = don't let vanilla build anything (it only does that for the End anyway).
        return repositionEntity.apply(false);
    }

    @Override
    public PortalInfo getPortalInfo(Entity entity, ServerLevel destWorld, Function<ServerLevel, PortalInfo> defaultPortalInfo)
    {
        if (destWorld.dimension() == ModDimensions.DROSS_LEVEL)
        {
            return toHub(entity, destWorld);
        }
        return toCastle(entity, destWorld);
    }

    // ------------------------------------------------------------------ into the Dross: the hub

    private static PortalInfo toHub(Entity entity, ServerLevel dross)
    {
        return DrossHub.arrival(dross, entity);
    }

    // ------------------------------------------------------------------ out of the Dross: the castle portal

    private static PortalInfo toCastle(Entity entity, ServerLevel dest)
    {
        if (dest.dimension() == Level.OVERWORLD)
        {
            // In a brand-new world the castle may not be built yet (it's placed far from spawn in the background):
            // build it now. Does nothing once it exists.
            PortalSiteBuilder.ensurePlaced(dest);
            Optional<PortalOpening> castle = findCastlePortal(dest);
            if (castle.isPresent())
            {
                return arrivalAt(dest, entity, castle.get(), true);
            }
        }
        BlockPos site = PortalSite.getOpeningCenter(dest);
        LOGGER.warn("Dross portal: couldn't find the castle portal frame in {} (site X {} Z {}); landing {} on the ground there instead.",
                dest.dimension().location(), site.getX(), site.getZ(), entity.getName().getString());
        return safeLanding(dest, entity, site.getX(), site.getZ());
    }

    /**
     * The castle frame's opening (lit or not), facing either way. Uses the frame axis and opening centre stored
     * by the world site area; if there's no complete frame there, looks for a lit Dross portal near it.
     */
    private static Optional<PortalOpening> findCastlePortal(ServerLevel overworld)
    {
        // X = the frame runs along X (walk through it along Z); Z = the frame runs along Z (walk through along X).
        Direction.Axis axis = PortalSite.getFrameAxis(overworld);
        // The middle block of the opening's bottom row: always inside the frame, whichever way it faces.
        BlockPos openingGuess = PortalSite.getOpeningCenter(overworld);
        Optional<DrossPortalShape> frame = DrossPortalShape.findAnyPortalShape(overworld, openingGuess, axis);
        if (frame.isPresent())
        {
            return Optional.of(PortalOpening.of(frame.get()));
        }
        return findLitPortal(overworld, openingGuess, CASTLE_SEARCH_RADIUS);
    }

    // ------------------------------------------------------------------ helpers (also used by DrossHub)

    /**
     * Where to stand next to a portal opening: in front (+Z / +X side) first, then behind, one block out
     * and then two; if all of that is blocked, inside the opening itself (standing on the frame).
     * Faces away from the portal, as if the entity had just walked out of it.
     */
    static PortalInfo arrivalAt(ServerLevel level, Entity entity, PortalOpening opening)
    {
        return arrivalAt(level, entity, opening, false);
    }

    /**
     * Same as {@link #arrivalAt(ServerLevel, Entity, PortalOpening)}, but with {@code facePortal} true the entity
     * looks at the portal instead of away from it (used for the castle, so players see the frame they came out of).
     * Works for frames along X (stands on the +Z / -Z side) and along Z (stands on the +X / -X side).
     */
    static PortalInfo arrivalAt(ServerLevel level, Entity entity, PortalOpening opening, boolean facePortal)
    {
        // Both sides blocked: stand inside the opening, on top of the frame's bottom row.
        // The portal cooldown stops it from sending the entity straight back.
        return spotBeside(level, entity, sizeOf(entity), opening, facePortal)
                .orElseGet(() -> spotInside(opening, entity.getYRot(), entity.getXRot()));
    }

    /**
     * Where a standing player arriving through this opening lands (the hub's arrival spot), worked out the same way as
     * {@link #arrivalAt(ServerLevel, Entity, PortalOpening)}: in front of the opening if there's room, facing away from it.
     */
    static PortalInfo arrivalSpot(ServerLevel level, PortalOpening opening)
    {
        return spotBeside(level, null, PLAYER_SIZE, opening, false)
                .orElseGet(() -> spotInside(opening, frontYaw(opening), 0.0F));
    }

    /**
     * Like {@link #arrivalAt(ServerLevel, Entity, PortalOpening)}, but for an opening whose frame may be broken (so
     * the opening itself can't be trusted either): the spot inside the opening is only used if it's safe too, and if
     * no spot is safe the result is empty.
     */
    static Optional<PortalInfo> safeArrivalNear(ServerLevel level, Entity entity, PortalOpening opening)
    {
        EntityDimensions size = sizeOf(entity);
        Optional<PortalInfo> beside = spotBeside(level, entity, size, opening, false);
        if (beside.isPresent())
        {
            return beside;
        }
        PortalInfo inside = spotInside(opening, entity.getYRot(), entity.getXRot());
        return isSafe(level, entity, size, inside.pos) ? Optional.of(inside) : Optional.empty();
    }

    /**
     * The first safe spot next to the opening: in front (+Z / +X side) first, then behind, one block out and then two,
     * each at the opening's bottom level, one lower and one higher (so steps up to the frame are fine). Faces away from
     * the portal, or towards it with {@code facePortal}.
     */
    private static Optional<PortalInfo> spotBeside(ServerLevel level, @Nullable Entity entity, EntityDimensions size,
                                                   PortalOpening opening, boolean facePortal)
    {
        BlockPos min = opening.minCorner();
        boolean alongX = opening.axis() == Direction.Axis.X;
        double along = opening.centerAlong();
        double perp = (alongX ? min.getZ() : min.getX()) + 0.5D;
        double y = min.getY();

        for (int distance = 1; distance <= 2; distance++)
        {
            for (int side : new int[] {1, -1})
            {
                for (int dy : new int[] {0, -1, 1})
                {
                    double p = perp + side * distance;
                    Vec3 pos = alongX ? new Vec3(along, y + dy, p) : new Vec3(p, y + dy, along);
                    if (isSafe(level, entity, size, pos))
                    {
                        // Minecraft yaw: 0 faces +Z (south), 180 faces -Z, -90 faces +X (east), 90 faces -X.
                        // This is the direction pointing away from the portal; turn around to face it.
                        float yaw = alongX ? (side > 0 ? 0.0F : 180.0F) : (side > 0 ? -90.0F : 90.0F);
                        if (facePortal)
                        {
                            yaw = Mth.wrapDegrees(yaw + 180.0F);
                        }
                        return Optional.of(new PortalInfo(pos, Vec3.ZERO, yaw, 0.0F));
                    }
                }
            }
        }
        return Optional.empty();
    }

    /** Standing inside the opening, on top of the frame's bottom row, with the given rotation. */
    private static PortalInfo spotInside(PortalOpening opening, float yaw, float pitch)
    {
        BlockPos min = opening.minCorner();
        boolean alongX = opening.axis() == Direction.Axis.X;
        double along = opening.centerAlong();
        double perp = (alongX ? min.getZ() : min.getX()) + 0.5D;
        Vec3 inside = alongX ? new Vec3(along, min.getY(), perp) : new Vec3(perp, min.getY(), along);
        return new PortalInfo(inside, Vec3.ZERO, yaw, pitch);
    }

    /** The yaw that faces out of the front (+Z / +X side) of the opening. */
    private static float frontYaw(PortalOpening opening)
    {
        return opening.axis() == Direction.Axis.X ? 0.0F : -90.0F;
    }

    /**
     * Lands on safe ground near X/Z ({@link #findSafeSpot}, up to {@link #LANDING_SEARCH_RADIUS} blocks away), or on
     * open water right there. If there's neither: in the Dross, on the hub's arrival spot (never anywhere near the
     * Overworld's spawn); anywhere else, near the world spawn.
     */
    static PortalInfo safeLanding(ServerLevel level, Entity entity, int x, int z)
    {
        Optional<Vec3> spot = findSafeSpot(level, entity, x, z, LANDING_SEARCH_RADIUS);
        if (spot.isPresent())
        {
            return new PortalInfo(spot.get(), Vec3.ZERO, entity.getYRot(), entity.getXRot());
        }
        Vec3 surface = surfaceSpot(level, x, z);
        if (surface != null && level.getFluidState(BlockPos.containing(surface).below()).is(FluidTags.WATER))
        {
            return new PortalInfo(surface, Vec3.ZERO, entity.getYRot(), entity.getXRot()); // open water: it swims
        }
        if (level.dimension() == ModDimensions.DROSS_LEVEL)
        {
            PortalInfo hub = hubFallback(level, entity, x, z);
            LOGGER.warn("Dross portal: no safe ground within {} blocks of {} {} in the Dross; landing {} on the hub's arrival spot {} instead.",
                    LANDING_SEARCH_RADIUS, x, z, entity.getName().getString(), BlockPos.containing(hub.pos).toShortString());
            return hub;
        }
        BlockPos spawn = level.getSharedSpawnPos();
        Optional<Vec3> nearSpawn = findSafeSpot(level, entity, spawn.getX(), spawn.getZ(), LANDING_SEARCH_RADIUS);
        if (nearSpawn.isPresent())
        {
            return new PortalInfo(nearSpawn.get(), Vec3.ZERO, entity.getYRot(), entity.getXRot());
        }
        Vec3 spawnSpot = surfaceSpot(level, spawn.getX(), spawn.getZ());
        return new PortalInfo(spawnSpot != null ? spawnSpot : Vec3.atBottomCenterOf(spawn), Vec3.ZERO, entity.getYRot(), entity.getXRot());
    }

    /**
     * The last resort in the Dross: the hub's remembered arrival spot; if there is none, in front of its remembered exit
     * portal; if the hub has no exit portal at all, the ground at X/Z, whatever it is.
     */
    private static PortalInfo hubFallback(ServerLevel dross, Entity entity, int x, int z)
    {
        DrossHubData data = DrossHubData.get(dross.getServer());
        Vec3 arrival = data.getArrival();
        if (arrival != null)
        {
            return new PortalInfo(arrival, Vec3.ZERO, data.getArrivalYaw(), 0.0F);
        }
        PortalOpening exit = data.getRememberedExit();
        if (exit != null)
        {
            BlockPos min = exit.minCorner();
            Vec3 front = exit.axis() == Direction.Axis.X
                    ? new Vec3(exit.centerAlong(), min.getY(), min.getZ() + 1.5D)
                    : new Vec3(min.getX() + 1.5D, min.getY(), exit.centerAlong());
            return new PortalInfo(front, Vec3.ZERO, frontYaw(exit), 0.0F);
        }
        Vec3 surface = surfaceSpot(dross, x, z);
        return new PortalInfo(surface != null ? surface : new Vec3(x + 0.5D, dross.getSeaLevel(), z + 0.5D), Vec3.ZERO,
                entity.getYRot(), entity.getXRot());
    }

    // ------------------------------------------------------------------ finding safe ground

    /**
     * A safe place for {@code entity} to stand on the ground, as near to the column {@code x}/{@code z} as possible: the
     * columns are tried nearest first (spiralling out to {@code radius} blocks along X and Z), and in each column the
     * highest ground is used if it's safe:
     * <ul>
     *   <li>Looking down from the top of the column, the ground is the first block with a collision shape (at most one
     *       block tall) that isn't leaves, a log, a giant mushroom, a fluid or a portal, and that has at least
     *       {@link #GROUND_SUPPORT} more solid blocks right under it. So tree tops, mushroom caps, floating blocks and
     *       one-block ledges are looked past, and plants, snow and ash layers are stood in.</li>
     *   <li>The spot is safe if the entity, standing up, fits there without touching any block, liquid or portal, has
     *       something to stand on, and nothing under or around its feet burns or hurts (fire, lava, magma, cactus...).
     *       If the column's ground isn't safe (a tree trunk stands on it, lava covers it...), the next column is tried.</li>
     * </ul>
     * Loads (and if needed generates) the chunks it looks at. Server thread only. With a null entity, the spot is sized
     * for a standing player.
     *
     * @return the spot (the middle of the block's top face it stands on), or empty if no column within {@code radius} has one
     */
    public static Optional<Vec3> findSafeSpot(ServerLevel level, @Nullable Entity entity, int x, int z, int radius)
    {
        EntityDimensions size = sizeOf(entity);
        for (int[] offset : spiral(Math.max(0, radius)))
        {
            Vec3 spot = safeSpotInColumn(level, entity, size, x + offset[0], z + offset[1]);
            if (spot != null)
            {
                return Optional.of(spot);
            }
        }
        return Optional.empty();
    }

    /** The column's highest ground (see {@link #findSafeSpot}), if it's safe to stand on; null otherwise. */
    @Nullable
    private static Vec3 safeSpotInColumn(ServerLevel level, @Nullable Entity entity, EntityDimensions size, int x, int z)
    {
        if (!level.getWorldBorder().isWithinBounds(x + 0.5D, z + 0.5D))
        {
            return null;
        }
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z); // the highest block that isn't air
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = top; y >= level.getMinBuildHeight() + GROUND_SUPPORT; y--)
        {
            BlockState state = level.getBlockState(pos.set(x, y, z));
            double height = groundHeight(level, pos, state);
            if (height <= 0.0D || !isSupported(level, x, y, z))
            {
                continue; // air, plants, leaves, logs, water, portals; or a block floating in the air
            }
            Vec3 spot = new Vec3(x + 0.5D, y + height, z + 0.5D);
            return isSafe(level, entity, size, spot) ? spot : null;
        }
        return null;
    }

    /**
     * How high the top of this block is (0 to 1) if it can be stood on as ground; 0 if it isn't ground: air, a fluid
     * (or a waterlogged block), part of a tree or a giant mushroom, a portal, anything without a collision shape
     * (plants, thin snow) or taller than a block (fences, walls).
     */
    private static double groundHeight(ServerLevel level, BlockPos pos, BlockState state)
    {
        if (state.isAir() || !state.getFluidState().isEmpty() || isTreePart(state) || isPortal(state))
        {
            return 0.0D;
        }
        VoxelShape shape = state.getCollisionShape(level, pos);
        if (shape.isEmpty())
        {
            return 0.0D;
        }
        double height = shape.max(Direction.Axis.Y);
        return height > 1.0D ? 0.0D : height;
    }

    /**
     * True if the {@link #GROUND_SUPPORT} blocks right under (x, y, z) are all solid (not air, a fluid, part of a tree or
     * a giant mushroom, or a portal).
     */
    private static boolean isSupported(ServerLevel level, int x, int y, int z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int i = 1; i <= GROUND_SUPPORT; i++)
        {
            BlockState state = level.getBlockState(pos.set(x, y - i, z));
            if (state.isAir() || !state.getFluidState().isEmpty() || isTreePart(state) || isPortal(state)
                    || state.getCollisionShape(level, pos).isEmpty())
            {
                return false;
            }
        }
        return true;
    }

    /**
     * Leaves, logs (the Dross's fallen charred and frostbitten logs too) and giant mushrooms (vanilla's and the Dross's
     * blightcaps): never "the ground", so nothing lands on a tree top or a mushroom cap.
     */
    private static boolean isTreePart(BlockState state)
    {
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.LOGS) || state.getBlock() instanceof HugeMushroomBlock;
    }

    /** Column offsets within {@code radius} along X and Z, nearest first (equally near ones in a fixed order). */
    private static List<int[]> spiral(int radius)
    {
        List<int[]> offsets = new ArrayList<>((2 * radius + 1) * (2 * radius + 1));
        for (int dx = -radius; dx <= radius; dx++)
        {
            for (int dz = -radius; dz <= radius; dz++)
            {
                offsets.add(new int[] {dx, dz});
            }
        }
        offsets.sort(Comparator.<int[]>comparingInt(o -> o[0] * o[0] + o[1] * o[1]).thenComparingInt(o -> o[1]).thenComparingInt(o -> o[0]));
        return offsets;
    }

    // ------------------------------------------------------------------ is a spot safe

    /** The entity's size standing up (a player's if there's no entity): it'll stand up once it's there. */
    private static EntityDimensions sizeOf(@Nullable Entity entity)
    {
        return entity == null ? PLAYER_SIZE : entity.getDimensions(Pose.STANDING);
    }

    /** True if something {@code size} big can stand at {@code pos} safely (see {@link #isSafe(ServerLevel, Entity, EntityDimensions, Vec3)}). */
    static boolean isStandable(ServerLevel level, EntityDimensions size, Vec3 pos)
    {
        return isSafe(level, null, size, pos);
    }

    /** True if this entity can land at {@code pos} safely (standing up). */
    static boolean isSafeFor(ServerLevel level, Entity entity, Vec3 pos)
    {
        return isSafe(level, entity, sizeOf(entity), pos);
    }

    /**
     * Free space for the body (no block, liquid or portal in it), and solid ground right under its feet with nothing
     * that burns or hurts under or around them (fire, lava, magma, campfires, cactus, berry bushes, powder snow, wither
     * roses, cobwebs, the Cinder Colossus's temporary lava and fire).
     */
    private static boolean isSafe(ServerLevel level, @Nullable Entity entity, EntityDimensions size, Vec3 pos)
    {
        AABB box = size.makeBoundingBox(pos);
        if (!level.getWorldBorder().isWithinBounds(box))
        {
            return false;
        }
        if (!level.noCollision(entity, box) || level.containsAnyLiquid(box))
        {
            return false;
        }
        AABB floor = new AABB(box.minX, box.minY - 0.5D, box.minZ, box.maxX, box.minY, box.maxZ);
        if (level.noCollision(entity, floor))
        {
            return false; // nothing to stand on
        }
        return level.getBlockStatesIfLoaded(floor.minmax(box).deflate(1.0E-7D)).noneMatch(DrossTeleporter::isHazard);
    }

    /** Something an entity mustn't land in or on. */
    private static boolean isHazard(BlockState state)
    {
        return state.is(BlockTags.FIRE) || state.is(Blocks.MAGMA_BLOCK) || state.is(BlockTags.CAMPFIRES)
                || state.getFluidState().is(FluidTags.LAVA) || isPortal(state)
                || state.is(Blocks.CACTUS) || state.is(Blocks.SWEET_BERRY_BUSH) || state.is(Blocks.POWDER_SNOW)
                || state.is(Blocks.WITHER_ROSE) || state.is(Blocks.COBWEB)
                || state.is(ModBlocks.CINDER_LAVA.get()) || state.is(ModBlocks.CINDER_FIRE.get());
    }

    /** A portal block: the Dross portal, a nether portal, an end portal or an end gateway. */
    private static boolean isPortal(BlockState state)
    {
        return state.is(ModBlocks.DROSS_PORTAL.get()) || state.is(BlockTags.PORTALS);
    }

    // ------------------------------------------------------------------ finding portals

    /**
     * Finds the nearest lit Dross portal within {@code radius} blocks (horizontally, any height).
     * Cheap: chunk sections whose block palette can't contain the portal block are skipped
     * without looking at their blocks.
     */
    static Optional<PortalOpening> findLitPortal(ServerLevel level, BlockPos center, int radius)
    {
        Block portal = ModBlocks.DROSS_PORTAL.get();
        BlockPos best = null;
        double bestDist = Double.MAX_VALUE;

        int minCX = SectionPos.blockToSectionCoord(center.getX() - radius);
        int maxCX = SectionPos.blockToSectionCoord(center.getX() + radius);
        int minCZ = SectionPos.blockToSectionCoord(center.getZ() - radius);
        int maxCZ = SectionPos.blockToSectionCoord(center.getZ() + radius);

        for (int cx = minCX; cx <= maxCX; cx++)
        {
            for (int cz = minCZ; cz <= maxCZ; cz++)
            {
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++)
                {
                    LevelChunkSection section = sections[i];
                    if (section == null || section.hasOnlyAir() || !section.maybeHas(s -> s.is(portal)))
                    {
                        continue;
                    }
                    int baseY = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    for (int y = 0; y < 16; y++)
                    {
                        for (int z = 0; z < 16; z++)
                        {
                            for (int x = 0; x < 16; x++)
                            {
                                if (!section.getBlockState(x, y, z).is(portal))
                                {
                                    continue;
                                }
                                int wx = (cx << 4) + x;
                                int wy = baseY + y;
                                int wz = (cz << 4) + z;
                                if (Math.abs(wx - center.getX()) > radius || Math.abs(wz - center.getZ()) > radius)
                                {
                                    continue;
                                }
                                double dx = wx - center.getX();
                                double dy = wy - center.getY();
                                double dz = wz - center.getZ();
                                double dist = dx * dx + dy * dy + dz * dz;
                                if (dist < bestDist)
                                {
                                    bestDist = dist;
                                    best = new BlockPos(wx, wy, wz);
                                }
                            }
                        }
                    }
                }
            }
        }

        if (best == null)
        {
            return Optional.empty();
        }
        BlockPos bottom = best;
        while (level.getBlockState(bottom.below()).is(portal))
        {
            bottom = bottom.below();
        }
        BlockState bottomState = level.getBlockState(bottom);
        Direction.Axis axis = bottomState.getValue(DrossPortalBlock.AXIS);
        BlockUtil.FoundRectangle rect = BlockUtil.getLargestRectangleAround(bottom, axis, DrossPortalShape.MAX_WIDTH, Direction.Axis.Y,
                DrossPortalShape.MAX_HEIGHT, p -> level.getBlockState(p) == bottomState);
        return Optional.of(new PortalOpening(rect.minCorner, axis, rect.axis1Size, rect.axis2Size));
    }

    // ------------------------------------------------------------------ the old surface helpers

    /** The first free spot above the top solid or liquid block at X/Z (logs count, leaves don't), or null over the void. */
    @Nullable
    private static Vec3 surfaceSpot(ServerLevel level, int x, int z)
    {
        int y = surfaceY(level, x, z);
        if (y <= level.getMinBuildHeight() + 1)
        {
            return null; // void column
        }
        return new Vec3(x + 0.5D, y, z + 0.5D);
    }

    /** First free y above the top solid/liquid block of a column (loads the chunk if needed). */
    static int surfaceY(ServerLevel level, int x, int z)
    {
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }
}
