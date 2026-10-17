package com.reg21meme.dross.dimension.sewer;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.Site;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import net.minecraft.world.level.levelgen.structure.StructureStart;
import org.slf4j.Logger;

/**
 * The sewers under Blightwood: their ids and lookups (the rat caves have their own API, {@link RatCaves}).
 * <ul>
 *   <li>{@link #SEWERS} ({@code dross:sewers}): a regular network (3 pipes, rats only); {@link #RAT_CAVE}
 *       ({@code dross:rat_cave}): the Rat King's (one grand entrance, ending in his rat cave). {@link #ALL}
 *       ({@code #dross:sewers}) is both.</li>
 *   <li>Where they are is planned by {@link BossSites} (in each group of 3 Blightwood patches: one Rat King's sewer, and a
 *       regular one in each of the other two patches with a 1 in 2 chance). Minecraft's {@code /locate structure} can't
 *       search that placement, so {@link #findNearest} asks the planner.</li>
 *   <li>The sewer water is green because the Blightwood underground is its own biome, {@link com.reg21meme.dross.dimension.DrossBiomes#SEWERS}.</li>
 * </ul>
 * Server thread. {@link #findNearest} reads the noise and may load (not fully generate) the network's start chunk; the
 * other lookups only read chunks that are already loaded.
 */
public final class Sewers
{
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final ResourceKey<Structure> SEWERS = key("sewers");
    public static final ResourceKey<Structure> RAT_CAVE = key("rat_cave");
    public static final TagKey<Structure> ALL = TagKey.create(Registries.STRUCTURE, new ResourceLocation(Dross.MODID, "sewers"));

    /** How far {@link #findNearest(ServerLevel, BlockPos)} looks (blocks from where it starts). */
    public static final int SEARCH_RADIUS = 12_000;

    private Sewers() {}

    private static ResourceKey<Structure> key(String name)
    {
        return ResourceKey.create(Registries.STRUCTURE, new ResourceLocation(Dross.MODID, name));
    }

    /**
     * One sewer network.
     *
     * @param id         a number that stays the same for it forever (its start chunk, {@link ChunkPos#toLong})
     * @param startChunk its start chunk (its middle)
     * @param ratKing    true for the Rat King's sewer, false for a regular one
     * @param bounds     a box round all of it
     * @param floorY     its floor's height (you walk at floorY + 1)
     * @param entrances  its ways in (a regular network: 3 pipes; the Rat King's: 1 grand entrance)
     */
    public record Network(long id, ChunkPos startChunk, boolean ratKing, BoundingBox bounds, int floorY, List<Entrance> entrances)
    {
    }

    /**
     * One way in.
     *
     * @param opening    the air block over the middle of its rim (the drop starts here)
     * @param approach   where you'd stand on the ground at the foot of its steps (the block your feet are in): its y is
     *                   the ground under the pipe plus one, so check the spot is safe (and find the ground) first
     * @param approachYaw the way to face there (towards the pipe), in Minecraft's degrees
     * @param groundY    the ground's top block under its body
     * @param grand      true for the Rat King's grand entrance
     */
    public record Entrance(BlockPos opening, BlockPos approach, float approachYaw, int groundY, boolean grand)
    {
    }

    /** The nearest regular sewer (not the Rat King's) to {@code from} in the Dross, within {@link #SEARCH_RADIUS} blocks. */
    public static Optional<Network> findNearest(ServerLevel dross, BlockPos from)
    {
        return findNearest(dross, from, SEARCH_RADIUS);
    }

    /**
     * The nearest regular sewer to {@code from} in the Dross (by its middle), within {@code radius} blocks, through the
     * planner (noise only), then its start chunk is loaded to its structure stage (no terrain) to read the network. A
     * network found again is the same one (the same id).
     */
    public static Optional<Network> findNearest(ServerLevel dross, BlockPos from, int radius)
    {
        if (!dross.dimension().equals(ModDimensions.DROSS_LEVEL))
        {
            return Optional.empty();
        }
        return BossSites.forLevel(dross).nearest(BossSites.REGULAR_SEWER, from, radius).flatMap(site -> start(dross, site, SEWERS)).flatMap(Sewers::of);
    }

    /** The structure start of a planned site's network (its chunk loaded to its structure stage), if it's there. */
    static Optional<StructureStart> start(ServerLevel dross, Site site, ResourceKey<Structure> key)
    {
        Structure structure = dross.registryAccess().registryOrThrow(Registries.STRUCTURE).get(key);
        if (structure == null)
        {
            return Optional.empty();
        }
        ChunkPos chunkPos = site.chunk();
        ChunkAccess chunk = dross.getChunk(chunkPos.x, chunkPos.z, ChunkStatus.STRUCTURE_STARTS);
        StructureStart start = dross.structureManager().getStartForStructure(SectionPos.bottomOf(chunk), structure, chunk);
        if (start == null || !start.isValid())
        {
            // (never in a world made since the planner: every planned network is made in its chunk)
            LOGGER.warn("[sewers] the {} planned at {} isn't in its chunk (made before the planner, or the plan changed?)", site.use(), chunkPos);
            return Optional.empty();
        }
        return Optional.of(start);
    }

    /** The network of a sewer structure start (either kind), if it is one. */
    public static Optional<Network> of(@Nullable StructureStart start)
    {
        if (start == null || !start.isValid() || !(start.getStructure() instanceof SewerStructure structure))
        {
            return Optional.empty();
        }
        List<Entrance> entrances = new ArrayList<>();
        int floor = Integer.MIN_VALUE;
        for (StructurePiece piece : start.getPieces())
        {
            if (piece instanceof SewerEntrance e)
            {
                entrances.add(entrance(e));
                floor = e.centre().getY();
            }
        }
        return Optional.of(new Network(start.getChunkPos().toLong(), start.getChunkPos(), structure.hasRatCave(), start.getBoundingBox(), floor,
                List.copyOf(entrances)));
    }

    static Entrance entrance(SewerEntrance e)
    {
        BlockPos approach = e.approach();
        BlockPos opening = e.opening();
        float yaw = (float) (Mth.atan2(opening.getZ() - approach.getZ(), opening.getX() - approach.getX()) * Mth.RAD_TO_DEG) - 90.0F;
        return new Entrance(opening, approach, Mth.wrapDegrees(yaw), e.groundY(), e.grand());
    }

    /** True if pos is inside a piece of a sewer network (its tunnels, junctions, entrances or rat cave; their walls included). */
    public static boolean isInSewers(ServerLevel level, BlockPos pos)
    {
        return level.structureManager().getStructureWithPieceAt(pos, ALL).isValid();
    }

    /** The sewer networks (their starts) that reach into any of these chunks, each once. The chunks must be loaded. */
    public static List<StructureStart> networksIn(ServerLevel level, Collection<ChunkPos> chunks)
    {
        Map<String, StructureStart> found = new LinkedHashMap<>();
        for (ChunkPos chunk : chunks)
        {
            for (StructureStart start : level.structureManager().startsForStructure(chunk, s -> s instanceof SewerStructure))
            {
                if (start.isValid())
                {
                    found.putIfAbsent(start.getChunkPos().toLong() + "/" + ((SewerStructure) start.getStructure()).hasRatCave(), start);
                }
            }
        }
        return new ArrayList<>(found.values());
    }

    /** The bounding boxes of every piece of the sewer networks reaching into these chunks (for checks that skip the sewers). */
    public static List<BoundingBox> pieceBoxes(ServerLevel level, Collection<ChunkPos> chunks)
    {
        List<BoundingBox> boxes = new ArrayList<>();
        for (StructureStart start : networksIn(level, chunks))
        {
            for (StructurePiece piece : start.getPieces())
            {
                boxes.add(piece.getBoundingBox());
            }
        }
        return boxes;
    }

    /**
     * The bounding boxes of the entrances (the only parts of a network that stand on the ground; the Rat King's grand
     * entrance's includes its poisoned ground) reaching into these chunks.
     */
    public static List<BoundingBox> pipeBoxes(ServerLevel level, Collection<ChunkPos> chunks)
    {
        List<BoundingBox> boxes = new ArrayList<>();
        for (StructureStart start : networksIn(level, chunks))
        {
            for (StructurePiece piece : start.getPieces())
            {
                if (piece instanceof SewerEntrance)
                {
                    boxes.add(piece.getBoundingBox());
                }
            }
        }
        return boxes;
    }

    /** True if any of the boxes contains the block. */
    public static boolean inAny(List<BoundingBox> boxes, int x, int y, int z)
    {
        for (BoundingBox box : boxes)
        {
            if (box.isInside(x, y, z))
            {
                return true;
            }
        }
        return false;
    }

    /** True if any of the boxes covers the column (any height). */
    public static boolean columnInAny(List<BoundingBox> boxes, int x, int z)
    {
        for (BoundingBox box : boxes)
        {
            if (x >= box.minX() && x <= box.maxX() && z >= box.minZ() && z <= box.maxZ())
            {
                return true;
            }
        }
        return false;
    }
}
