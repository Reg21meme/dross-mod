package com.reg21meme.dross.portal;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSite;
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
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
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
 *       on the ground at the site's X/Z and a warning is logged.</li>
 * </ul>
 * The entity is always placed on solid ground in free space next to the portal (or, if both sides are
 * blocked, inside the portal itself, standing on the frame), never inside blocks or over a drop.
 */
public class DrossTeleporter implements ITeleporter
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** How far around the castle frame to look for a lit Dross portal, if the frame isn't where PortalSite says. */
    private static final int CASTLE_SEARCH_RADIUS = 16;

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
            Optional<PortalOpening> castle = findCastlePortal(dest);
            if (castle.isPresent())
            {
                return arrivalAt(dest, entity, castle.get(), true);
            }
        }
        LOGGER.warn("Dross portal: couldn't find the castle portal frame in {} (site X {} Z {}); landing {} on the ground there instead.",
                dest.dimension().location(), PortalSite.X, PortalSite.Z, entity.getName().getString());
        return safeLanding(dest, entity, PortalSite.X, PortalSite.Z);
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
                    if (isSafe(level, entity, pos))
                    {
                        // Minecraft yaw: 0 faces +Z (south), 180 faces -Z, -90 faces +X (east), 90 faces -X.
                        // This is the direction pointing away from the portal; turn around to face it.
                        float yaw = alongX ? (side > 0 ? 0.0F : 180.0F) : (side > 0 ? -90.0F : 90.0F);
                        if (facePortal)
                        {
                            yaw = Mth.wrapDegrees(yaw + 180.0F);
                        }
                        return new PortalInfo(pos, Vec3.ZERO, yaw, 0.0F);
                    }
                }
            }
        }

        // Both sides blocked: stand inside the opening, on top of the frame's bottom row.
        // The portal cooldown stops it from sending the entity straight back.
        Vec3 inside = alongX ? new Vec3(along, y, perp) : new Vec3(perp, y, along);
        return new PortalInfo(inside, Vec3.ZERO, entity.getYRot(), entity.getXRot());
    }

    /** Lands on the ground at X/Z; if that column is unsafe (void, lava...), at the world spawn instead. */
    static PortalInfo safeLanding(ServerLevel level, Entity entity, int x, int z)
    {
        Vec3 spot = surfaceSpot(level, x, z);
        if (spot != null && (isSafe(level, entity, spot) || level.getFluidState(BlockPos.containing(spot).below()).is(FluidTags.WATER)))
        {
            return new PortalInfo(spot, Vec3.ZERO, entity.getYRot(), entity.getXRot());
        }
        BlockPos spawn = level.getSharedSpawnPos();
        Vec3 spawnSpot = surfaceSpot(level, spawn.getX(), spawn.getZ());
        return new PortalInfo(spawnSpot != null ? spawnSpot : Vec3.atBottomCenterOf(spawn), Vec3.ZERO, entity.getYRot(), entity.getXRot());
    }

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

    /** Free space for the entity's body, no liquid in it, and solid, non-burning ground right under its feet. */
    private static boolean isSafe(ServerLevel level, Entity entity, Vec3 pos)
    {
        AABB box = entity.getDimensions(entity.getPose()).makeBoundingBox(pos);
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
        return level.getBlockStatesIfLoaded(floor.minmax(box)).noneMatch(s ->
                s.is(BlockTags.FIRE) || s.is(Blocks.MAGMA_BLOCK) || s.is(BlockTags.CAMPFIRES) || s.getFluidState().is(FluidTags.LAVA));
    }

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

    /** First free y above the top solid/liquid block of a column (loads the chunk if needed). */
    static int surfaceY(ServerLevel level, int x, int z)
    {
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }
}
