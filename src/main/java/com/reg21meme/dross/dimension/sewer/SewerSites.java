package com.reg21meme.dross.dimension.sewer;

import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.sites.BossSites;
import com.reg21meme.dross.dimension.sites.BossSitesTuning;
import com.reg21meme.dross.dimension.sites.Patch;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.dimension.sites.SiteFinder;
import com.reg21meme.dross.dimension.sites.SiteTerrain;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.levelgen.structure.Structure;

/**
 * The sewers' side of the boss-site planner ({@link BossSites}): the fit tests that say where in a Blightwood patch a
 * sewer network fits (the Rat King's, {@link BossSites#RAT_KING_SEWER}; a regular one, {@link BossSites#REGULAR_SEWER}),
 * the layout kept with each planned network, and which entrances are near a spot (world generation keeps its bigger
 * features away from them: {@code dross:not_near_sewer_entrance}).
 * <p>
 * A network fits at a spot if {@link SewerPlanner} can lay it out there with its middle in that spot's chunk: the planner
 * tries the patch's deepest spots first ({@link Patch#spots}). The structure's own generation then lays out the same
 * network again at that chunk (the same seed, the same noise: the same layout), so a network that was planned always
 * generates.
 */
public final class SewerSites
{
    /** Entrances further than this from their network's middle can't be (its junctions reach GRID_RADIUS cells, its pipes stand on junctions). */
    private static final int ENTRANCE_REACH = (SewerTuning.GRID_RADIUS + 1) * SewerTuning.CELL;
    /** The entrances near a chunk are worked out once per chunk (for the feature filter) and remembered, at most this many chunks. */
    private static final int NEAR_CACHE_LIMIT = 8192;

    private SewerSites() {}

    /**
     * One way into a network.
     *
     * @param opening   the air block over the middle of its rim (you stand on the rim at this height)
     * @param groundY   the ground's top block under its body (the highest of them)
     * @param steps     the side its steps go down (and its ladder is on)
     * @param grand     true for the Rat King's grand entrance, false for a regular pipe
     * @param clearance world generation keeps its bigger features this far from its middle (blocks)
     */
    public record Entrance(BlockPos opening, int groundY, Direction steps, boolean grand, int clearance)
    {
        /** Blocks from (x, z) to its middle, along the ground. */
        public double distanceTo(int x, int z)
        {
            double dx = this.opening.getX() - x;
            double dz = this.opening.getZ() - z;
            return Math.sqrt(dx * dx + dz * dz);
        }
    }

    /**
     * A planned network, as {@link SewerPlanner} laid it out (kept with its {@link Site} as its detail).
     *
     * @param ratCave    true: the Rat King's sewer (ending in his rat cave, one grand entrance)
     * @param relaxed    true: its smallest form (its group had no patch with room for the normal one)
     * @param middle     its middle chunk (the structure's start chunk)
     * @param floorY     its floor's height
     * @param entrances  its ways in (regular: 3 pipes; the Rat King's: 1 grand entrance)
     * @param caveCentre the middle of its rat cave's floor (where you'd stand), or null
     * @param junctions  how many junctions it has
     */
    public record Layout(boolean ratCave, boolean relaxed, ChunkPos middle, int floorY, List<Entrance> entrances, @Nullable BlockPos caveCentre,
                         int junctions)
    {
    }

    // ---------------------------------------------------------------------------------------------
    // The fit tests
    // ---------------------------------------------------------------------------------------------

    /** Where the Rat King's sewer fits in a Blightwood patch ({@code relaxed}: its smallest form). */
    public static Optional<SiteFinder.Found> findRatKingSewer(SiteTerrain terrain, Patch patch, boolean relaxed)
    {
        return find(terrain, patch, true, relaxed);
    }

    /** Where a regular sewer fits in a Blightwood patch (it has no smaller form). */
    public static Optional<SiteFinder.Found> findRegularSewer(SiteTerrain terrain, Patch patch, boolean relaxed)
    {
        return find(terrain, patch, false, false);
    }

    private static Optional<SiteFinder.Found> find(SiteTerrain terrain, Patch patch, boolean ratCave, boolean relaxed)
    {
        SewerStructure structure = structure(terrain, ratCave);
        if (structure == null)
        {
            return Optional.empty();
        }
        for (BlockPos spot : patch.spots(BossSitesTuning.SITE_TRIES, BossSitesTuning.SITE_TRY_SPACING))
        {
            ChunkPos chunk = new ChunkPos(spot);
            SewerPlanner.Plan plan = SewerPlanner.plan(terrain, structure, chunk, relaxed);
            if (plan.ok())
            {
                return Optional.of(new SiteFinder.Found(new BlockPos(chunk.getMiddleBlockX(), plan.layout().floorY(), chunk.getMiddleBlockZ()), plan.layout()));
            }
        }
        return Optional.empty();
    }

    /** The registered sewer structure (dross:rat_cave or dross:sewers) with its depths, or null if the datapack lacks it. */
    @Nullable
    static SewerStructure structure(SiteTerrain terrain, boolean ratCave)
    {
        Structure s = terrain.registryAccess().registryOrThrow(Registries.STRUCTURE).get(ratCave ? Sewers.RAT_CAVE : Sewers.SEWERS);
        return s instanceof SewerStructure sewer ? sewer : null;
    }

    // ---------------------------------------------------------------------------------------------
    // Entrances near a spot
    // ---------------------------------------------------------------------------------------------

    /** The planned sewer entrances (both kinds) within {@code radius} blocks of (x, z). Plans the groups there. */
    public static List<Entrance> entrancesNear(BossSites sites, int x, int z, int radius)
    {
        List<Entrance> near = new ArrayList<>();
        for (Site s : sites.sitesNear(DrossBiomes.BLIGHTWOOD, x, z, radius + ENTRANCE_REACH))
        {
            if (s.detail() instanceof Layout layout)
            {
                for (Entrance e : layout.entrances())
                {
                    if (e.distanceTo(x, z) <= radius)
                    {
                        near.add(e);
                    }
                }
            }
        }
        return near;
    }

    /** True if (x, z) is within an entrance's {@link Entrance#clearance} (world generation's bigger features stay out). */
    public static boolean nearEntrance(BossSites sites, int x, int z)
    {
        Map<Long, Object> cache = sites.cache("sewer_entrances_by_chunk");
        long chunk = ChunkPos.asLong(x >> 4, z >> 4);
        Object known = cache.get(chunk);
        List<?> entrances;
        if (known instanceof List<?> list)
        {
            entrances = list;
        }
        else
        {
            // every entrance whose clearance could reach into this chunk (its middle is at most 12 blocks from any of its blocks)
            entrances = entrancesNear(sites, (x & ~15) + 8, (z & ~15) + 8, SewerTuning.GRAND_CLEARANCE + 12);
            if (cache.size() > NEAR_CACHE_LIMIT)
            {
                cache.clear();
            }
            cache.put(chunk, entrances);
        }
        for (Object o : entrances)
        {
            if (o instanceof Entrance e && e.distanceTo(x, z) <= e.clearance())
            {
                return true;
            }
        }
        return false;
    }
}
