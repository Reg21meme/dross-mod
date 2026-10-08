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
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;
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
 *   <li><b>Into Dross:</b> same X/Z (1:1). Uses a lit Dross portal within 16 blocks if there is one;
 *       otherwise builds a new, already lit return portal (frame of {@code dross_portal_frame}) on the surface.</li>
 *   <li><b>Back to the Overworld:</b> the portal the entity originally left from; else any lit Dross portal
 *       near the same X/Z; else the portal site. Never builds anything in the Overworld. If no portal is
 *       found, the entity lands safely on the surface (or at world spawn) and a warning is logged.</li>
 * </ul>
 * The entity is always placed on solid ground in free space next to the portal (or, if both sides are
 * blocked, inside the portal itself, standing on the frame), never inside blocks or over a drop.
 */
public class DrossTeleporter implements ITeleporter
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final int SEARCH_RADIUS = 16;
    private static final int LINKED_SEARCH_RADIUS = 4;

    @Override
    public boolean isVanilla()
    {
        return false;
    }

    @Override
    public boolean playTeleportSound(net.minecraft.server.level.ServerPlayer player, ServerLevel sourceWorld, ServerLevel destWorld)
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
            return toDross(entity, destWorld);
        }
        return toOverworld(entity, destWorld);
    }

    // ------------------------------------------------------------------ Overworld -> Dross

    private static PortalInfo toDross(Entity entity, ServerLevel dross)
    {
        BlockPos target = clampToBorder(dross, entity.getX(), entity.getY(), entity.getZ());
        BlockUtil.FoundRectangle portal = findPortal(dross, target, SEARCH_RADIUS)
                .orElseGet(() -> buildReturnPortal(dross, target));
        return arrivalAt(dross, entity, portal);
    }

    /**
     * Builds a 4 wide x 5 tall {@code dross_portal_frame} frame (corners included, opening 2x3, along X),
     * already lit, standing on the surface at {@code target}. Makes sure there is solid floor in front of
     * and behind it and clears the air around it.
     */
    private static BlockUtil.FoundRectangle buildReturnPortal(ServerLevel level, BlockPos target)
    {
        int x0 = target.getX() - 1; // frame x0..x0+3, so the opening (x0+1..x0+2) covers the arrival column
        int z = target.getZ();

        int ground = Integer.MIN_VALUE;
        for (int dx = -1; dx <= 4; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                ground = Math.max(ground, surfaceY(level, x0 + dx, z + dz));
            }
        }
        int minY = level.getMinBuildHeight();
        if (ground <= minY + 1)
        {
            // No ground in this column (void). Build on a floating floor instead of over the drop.
            ground = Math.max(level.getSeaLevel(), minY + 2);
        }
        ground = Math.min(ground, level.getMaxBuildHeight() - 5);
        int g = ground; // first free y above the surface: the opening starts here, the frame's bottom row is at g-1

        BlockState frame = ModBlocks.DROSS_PORTAL_FRAME.get().defaultBlockState();
        BlockState floor = Blocks.OBSIDIAN.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        for (int dx = -1; dx <= 4; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                boolean inFramePlane = dz == 0 && dx >= 0 && dx <= 3;
                if (!inFramePlane)
                {
                    m.set(x0 + dx, g - 1, z + dz);
                    BlockState below = level.getBlockState(m);
                    if (!below.isFaceSturdy(level, m, Direction.UP) || !below.getFluidState().isEmpty())
                    {
                        level.setBlockAndUpdate(m, floor);
                    }
                }
                for (int dy = 0; dy <= 4; dy++)
                {
                    if (inFramePlane && dy <= 3)
                    {
                        continue; // frame / opening, placed below
                    }
                    m.set(x0 + dx, g + dy, z + dz);
                    if (!level.getBlockState(m).isAir())
                    {
                        level.setBlockAndUpdate(m, air);
                    }
                }
            }
        }

        for (int dx = 0; dx <= 3; dx++)
        {
            for (int dy = -1; dy <= 3; dy++)
            {
                if (dx == 0 || dx == 3 || dy == -1 || dy == 3)
                {
                    level.setBlockAndUpdate(m.set(x0 + dx, g + dy, z), frame);
                }
            }
        }

        BlockState portal = ModBlocks.DROSS_PORTAL.get().defaultBlockState().setValue(DrossPortalBlock.AXIS, Direction.Axis.X);
        for (int dx = 1; dx <= 2; dx++)
        {
            for (int dy = 0; dy <= 2; dy++)
            {
                level.setBlock(m.set(x0 + dx, g + dy, z), portal, 18);
            }
        }

        LOGGER.info("Built a Dross return portal at {} {} {} in {}", x0, g - 1, z, level.dimension().location());
        return new BlockUtil.FoundRectangle(new BlockPos(x0 + 1, g, z), 2, 3);
    }

    // ------------------------------------------------------------------ Dross -> Overworld

    private static PortalInfo toOverworld(Entity entity, ServerLevel dest)
    {
        // 1. The portal this entity left from (remembered when it went into Dross).
        CompoundTag data = entity.getPersistentData();
        if (data.contains(DrossPortalTravel.TAG_RETURN_PORTAL, Tag.TAG_COMPOUND)
                && dest.dimension().location().toString().equals(data.getString(DrossPortalTravel.TAG_RETURN_DIM)))
        {
            BlockPos linked = NbtUtils.readBlockPos(data.getCompound(DrossPortalTravel.TAG_RETURN_PORTAL));
            Optional<BlockUtil.FoundRectangle> found = findPortal(dest, linked, LINKED_SEARCH_RADIUS);
            if (found.isPresent())
            {
                return arrivalAt(dest, entity, found.get());
            }
        }

        // 2. Any lit Dross portal near the same X/Z.
        BlockPos target = clampToBorder(dest, entity.getX(), entity.getY(), entity.getZ());
        Optional<BlockUtil.FoundRectangle> nearby = findPortal(dest, target, SEARCH_RADIUS);
        if (nearby.isPresent())
        {
            return arrivalAt(dest, entity, nearby.get());
        }

        // 3. The portal site.
        if (dest.dimension() == Level.OVERWORLD)
        {
            BlockPos site = PortalSite.getFramePos(dest);
            if (site != null)
            {
                Optional<BlockUtil.FoundRectangle> atSite = findPortal(dest, site, SEARCH_RADIUS);
                if (atSite.isPresent())
                {
                    return arrivalAt(dest, entity, atSite.get());
                }
            }
        }

        // 4. Nothing found: land safely at the same X/Z, never build a frame in the Overworld.
        LOGGER.warn("Dross portal: no lit Dross portal found in {} near {} or at the portal site; landing {} on the surface instead.",
                dest.dimension().location(), target.toShortString(), entity.getName().getString());
        return safeLanding(dest, entity, target);
    }

    private static PortalInfo safeLanding(ServerLevel level, Entity entity, BlockPos target)
    {
        Vec3 spot = surfaceSpot(level, target.getX(), target.getZ());
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

    // ------------------------------------------------------------------ helpers

    /** Where to stand next to a found portal: in front (+Z/+X side first), then behind, then inside it. */
    private static PortalInfo arrivalAt(ServerLevel level, Entity entity, BlockUtil.FoundRectangle rect)
    {
        BlockPos min = rect.minCorner;
        BlockState state = level.getBlockState(min);
        Direction.Axis axis = state.hasProperty(DrossPortalBlock.AXIS) ? state.getValue(DrossPortalBlock.AXIS) : Direction.Axis.X;
        boolean alongX = axis == Direction.Axis.X;
        double along = (alongX ? min.getX() : min.getZ()) + rect.axis1Size / 2.0D;
        double perp = (alongX ? min.getZ() : min.getX()) + 0.5D;
        double y = min.getY();

        for (int side : new int[] {1, -1})
        {
            for (int dy : new int[] {0, -1, 1})
            {
                double p = perp + side;
                Vec3 pos = alongX ? new Vec3(along, y + dy, p) : new Vec3(p, y + dy, along);
                if (isSafe(level, entity, pos))
                {
                    // Face away from the portal, as if you just walked out of it.
                    float yaw = alongX ? (side > 0 ? 0.0F : 180.0F) : (side > 0 ? -90.0F : 90.0F);
                    return new PortalInfo(pos, Vec3.ZERO, yaw, 0.0F);
                }
            }
        }

        // Both sides blocked: stand inside the portal, on top of the frame's bottom row.
        // The portal cooldown stops it from sending you straight back.
        Vec3 inside = alongX ? new Vec3(along, y, perp) : new Vec3(perp, y, along);
        return new PortalInfo(inside, Vec3.ZERO, entity.getYRot(), entity.getXRot());
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
     * Finds the nearest lit Dross portal within {@code radius} blocks (horizontally, any height) and returns
     * its rectangle. Cheap: chunk sections whose block palette can't contain the portal block are skipped
     * without looking at their blocks.
     */
    static Optional<BlockUtil.FoundRectangle> findPortal(ServerLevel level, BlockPos center, int radius)
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
        return Optional.of(BlockUtil.getLargestRectangleAround(bottom, axis, DrossPortalShape.MAX_WIDTH, Direction.Axis.Y,
                DrossPortalShape.MAX_HEIGHT, p -> level.getBlockState(p) == bottomState));
    }

    /** First free y above the top solid/liquid block of a column (loads the chunk if needed). */
    private static int surfaceY(ServerLevel level, int x, int z)
    {
        LevelChunk chunk = level.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z));
        return chunk.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
    }

    private static BlockPos clampToBorder(ServerLevel level, double x, double y, double z)
    {
        WorldBorder border = level.getWorldBorder();
        double cx = Mth.clamp(x, border.getMinX() + 16.0D, border.getMaxX() - 16.0D);
        double cz = Mth.clamp(z, border.getMinZ() + 16.0D, border.getMaxZ() - 16.0D);
        double cy = Mth.clamp(y, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        return BlockPos.containing(cx, cy, cz);
    }
}
