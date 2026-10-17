package com.reg21meme.dross.dimension.sewer;

import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * The rat caves (the Rat King's battlefields), for the boss area: finding one, telling whether a spot is inside one, and
 * where its nest, a safe arrival spot and its sewer's grand entrance are. A rat cave is the final chamber of a
 * {@code dross:rat_cave} sewer network under Blightwood ({@link RatCavePiece}); the boss-site planner ({@link BossSites})
 * puts one in each group of 3 Blightwood patches ({@link BossSites#RAT_KING_SEWER}).
 * <p>
 * Use these on the server thread. {@link #findNearest} reads the noise and loads (not fully generates) the network's start
 * chunk; {@link #entrancesNear} reads only the noise; the others only read chunks that are already loaded. Before
 * teleporting anyone to a cave's {@link RatCave#arrival()}, let the chunk generate fully as usual (any teleport does): the
 * cave's blocks are put down when its chunks are generated.
 */
public final class RatCaves
{
    /** How far {@link #findNearest(ServerLevel, BlockPos)} looks (blocks from where it starts). */
    public static final int SEARCH_RADIUS = 12_000;

    private RatCaves() {}

    /**
     * One rat cave.
     *
     * @param id         a number that stays the same for this cave forever (its network's start chunk), for keeping
     *                   track of it (who beat its king, when he comes back...)
     * @param startChunk its network's start chunk
     * @param bounds     a box round the whole cave (its walls and roof included); use {@link #contains} for "inside"
     * @param centre     the middle of its floor, where you'd stand
     * @param nest       the block the Rat King's feet are in when he lies on his nest: the carpet on top of the heap (his
     *                   feet go at its bottom, y + 1/16)
     * @param arrival    a safe spot to arrive at (the block your feet are in): on the cave's brick floor at the end of a
     *                   tunnel leading in, at least {@value SewerTuning#ARRIVAL_MIN_DISTANCE} blocks from the nest, never in
     *                   sludge or water, with room to stand
     * @param arrivalYaw the way to face at the arrival spot (towards the nest), in Minecraft's degrees
     * @param floorY     the height of its floor blocks
     * @param entrance   its sewer's one grand entrance: the air block over the middle of its crowned rim (where the drop
     *                   starts; the glow and the spores come out of it), or null if this network has none
     */
    public record RatCave(long id, ChunkPos startChunk, BoundingBox bounds, BlockPos centre, BlockPos nest, BlockPos arrival,
                          float arrivalYaw, int floorY, RatCavePiece piece, @Nullable BlockPos entrance)
    {
        /** True if pos is in the cave itself (its air or floor), not in the rock round it or the tunnels leading in. */
        public boolean contains(BlockPos pos)
        {
            return this.piece.contains(pos);
        }
    }

    /** The nearest rat cave to {@code from} in the Dross, within {@link #SEARCH_RADIUS} blocks. */
    public static Optional<RatCave> findNearest(ServerLevel dross, BlockPos from)
    {
        return findNearest(dross, from, SEARCH_RADIUS);
    }

    /**
     * The nearest rat cave to {@code from} in the Dross (by its sewer's middle), within {@code radius} blocks: the
     * planner (noise only) says where, then its network's start chunk is loaded to its structure stage (no terrain) to
     * read the cave. A cave that was already found or explored is found again (the same id).
     */
    public static Optional<RatCave> findNearest(ServerLevel dross, BlockPos from, int radius)
    {
        if (!dross.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return Optional.empty();
        }
        return BossSites.forLevel(dross).nearest(BossSites.RAT_KING_SEWER, from, radius).flatMap(site -> Sewers.start(dross, site, Sewers.RAT_CAVE))
                .flatMap(RatCaves::of);
    }

    /**
     * The grand entrances of the planned Rat King's sewers within {@code radius} blocks of pos (along the ground): the air
     * block over the middle of each crowned rim. Noise only (no chunks), so it's quick to ask often (the answers near a
     * spot are worked out once).
     */
    public static List<BlockPos> entrancesNear(ServerLevel dross, BlockPos pos, int radius)
    {
        List<BlockPos> found = new ArrayList<>();
        if (!dross.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return found;
        }
        for (Site site : BossSites.forLevel(dross).sitesNear(DrossBiomes.BLIGHTWOOD, pos.getX(), pos.getZ(), radius + SewerTuning.GRID_RADIUS * SewerTuning.CELL + SewerTuning.CELL))
        {
            if (site.use() == BossSites.RAT_KING_SEWER && site.detail() instanceof SewerSites.Layout layout)
            {
                for (SewerSites.Entrance e : layout.entrances())
                {
                    if (e.grand() && e.distanceTo(pos.getX(), pos.getZ()) <= radius)
                    {
                        found.add(e.opening());
                    }
                }
            }
        }
        return found;
    }

    /** The rat cave {@code pos} is inside (its air or floor), if any. The chunk at pos must be loaded. */
    public static Optional<RatCave> caveAt(ServerLevel level, BlockPos pos)
    {
        if (!level.dimension().equals(ModDimensions.DROSS_LEVEL) || !level.isLoaded(pos))
        {
            return Optional.empty();
        }
        Optional<Structure> structure = level.registryAccess().registryOrThrow(Registries.STRUCTURE).getOptional(Sewers.RAT_CAVE);
        if (structure.isEmpty())
        {
            return Optional.empty();
        }
        StructureStart start = level.structureManager().getStructureAt(pos, structure.get());
        if (!start.isValid())
        {
            return Optional.empty();
        }
        return of(start).filter(cave -> cave.contains(pos));
    }

    /** True if pos is inside a rat cave (its air or floor). */
    public static boolean isInRatCave(ServerLevel level, BlockPos pos)
    {
        return caveAt(level, pos).isPresent();
    }

    /** The rat cave of a {@code dross:rat_cave} network, if this start is one. */
    public static Optional<RatCave> of(StructureStart start)
    {
        if (start == null || !start.isValid())
        {
            return Optional.empty();
        }
        RatCavePiece cave = null;
        BlockPos entrance = null;
        for (StructurePiece piece : start.getPieces())
        {
            if (piece instanceof RatCavePiece c)
            {
                cave = c;
            }
            else if (piece instanceof SewerGrandPipePiece grand)
            {
                entrance = grand.opening();
            }
        }
        if (cave == null)
        {
            return Optional.empty();
        }
        BlockPos nest = cave.nest();
        BlockPos arrival = cave.arrival();
        float yaw = (float) (Mth.atan2(nest.getZ() - arrival.getZ(), nest.getX() - arrival.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        return Optional.of(new RatCave(start.getChunkPos().toLong(), start.getChunkPos(), cave.getBoundingBox(), cave.centre(), nest,
                arrival, Mth.wrapDegrees(yaw), cave.floorY(), cave, entrance));
    }
}
