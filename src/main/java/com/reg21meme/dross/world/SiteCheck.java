package com.reg21meme.dross.world;

import com.reg21meme.dross.world.shrine.GroundFit;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.QuartPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.StructureTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.StructureStart;

/**
 * Checks a spot for the portal site in the <b>real, generated</b> world, after the noise search liked it: the real
 * ground heights (with lakes, caves, boulders), water, biomes, villages and other structures, and whether the
 * staircase back to the natural terrain fits. If it passes, the {@link GroundFit.Plan} it made is what gets built.
 * Reads the chunks around the spot (loading them if they aren't loaded yet), so load them in the background first.
 */
final class SiteCheck
{
    /**
     * Extra blocks loaded around the footprint beyond the blending ring: the survey's edge, the look-around for ice
     * spikes and boulders, and whole trees whose canopy reaches into the ring.
     */
    private static final int EXTRA_LOAD = GroundFit.PROTRUSION_WINDOW + 18;

    /**
     * The verdict.
     *
     * @param problems why it fails (empty if it passes)
     * @param plan     the ground plan (also when it fails, for the log)
     */
    record Result(int x, int z, List<String> problems, @Nullable GroundFit.Plan plan, int distance, Set<String> biomes)
    {
        boolean ok()
        {
            return problems.isEmpty() && plan != null;
        }

        String describe()
        {
            return x + " " + z + " (" + distance + " blocks from spawn, " + String.join(", ", biomes) + ")"
                    + (plan != null ? ": " + plan.summary() : "");
        }
    }

    /**
     * How much the natural ground under the footprint varies, highest minus lowest, leaving out small dips (at most
     * {@link SiteRules#SMALL_DIP_SHARE} of the columns), which are filled.
     */
    static int variation(GroundFit.Plan plan)
    {
        return plan.variationIgnoringLowest((int) Math.floor(plan.footprintColumns() * SiteRules.SMALL_DIP_SHARE));
    }

    /** How far around the footprint the chunks must be loaded for {@link #check} and building. */
    static int loadMargin(SiteRules.Level rules)
    {
        return surveyMargin(rules) + EXTRA_LOAD;
    }

    /** How far around the footprint the ground is surveyed. */
    static int surveyMargin(SiteRules.Level rules)
    {
        return rules.blendMargin() + 2 + GroundFit.PROTRUSION_WINDOW;
    }

    /** Checks the spot with its site column at {@code x}/{@code z}. Server thread. */
    static Result check(ServerLevel level, SiteLayout layout, int x, int z, SiteRules.Level rules)
    {
        List<String> problems = new ArrayList<>();
        BlockPos spawn = level.getSharedSpawnPos();
        int distance = (int) Math.round(Math.sqrt((double) (x - spawn.getX()) * (x - spawn.getX())
                + (double) (z - spawn.getZ()) * (z - spawn.getZ())));
        if (distance < rules.minDistance() || distance > rules.maxDistance())
        {
            problems.add(distance + " blocks from spawn (must be " + rules.minDistance() + " to " + rules.maxDistance() + ")");
        }

        BoundingBox footprint = layout.footprintAt(x, z, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1);
        GroundFit.Survey survey = GroundFit.survey(level, footprint, surveyMargin(rules));
        GroundFit.Plan plan = GroundFit.plan(survey, rules.blendMargin(), SiteRules.WATER_CLEARANCE);
        if (plan.liquidInFootprint > 0)
        {
            problems.add(plan.liquidInFootprint + " columns of water (or ice over water) under the footprint");
        }
        if (plan.liquidNear > 0)
        {
            problems.add(plan.liquidNear + " columns of water within " + SiteRules.WATER_CLEARANCE + " blocks of the footprint");
        }
        int variation = variation(plan);
        if (variation > rules.maxVariation())
        {
            problems.add("the ground varies by " + variation + " blocks, not counting small dips (at most " + rules.maxVariation()
                    + "; " + extremes(level, survey) + ")");
        }
        if (rules.staircaseRequired())
        {
            if (plan.overflow > 0)
            {
                problems.add("the staircase back to the natural ground doesn't end within " + rules.blendMargin() + " blocks ("
                        + plan.overflow + " columns)");
            }
            if (plan.steepSteps > 0)
            {
                problems.add(plan.steepSteps + " steps of more than one block where the ground is reshaped");
            }
            if (plan.maxRingChange > rules.maxRingChange())
            {
                problems.add("the staircase would raise or lower a column by " + plan.maxRingChange + " blocks (at most "
                        + rules.maxRingChange() + ")");
            }
            if (plan.liquidInTheWay > 0)
            {
                problems.add(plan.liquidInTheWay + " water columns where the staircase would go");
            }
        }
        if (plan.deepestDip > rules.maxDipFill())
        {
            problems.add("a dip " + plan.deepestDip + " blocks deep in the footprint (at most " + rules.maxDipFill() + ")");
        }

        BoundingBox ring = footprint.inflatedBy(rules.blendMargin());
        Set<String> biomes = new LinkedHashSet<>();
        Set<String> wrong = new LinkedHashSet<>();
        int biomeY = QuartPos.fromBlock(plan.groundY + 1);
        for (int qx = QuartPos.fromBlock(ring.minX()); qx <= QuartPos.fromBlock(ring.maxX()); qx++)
        {
            for (int qz = QuartPos.fromBlock(ring.minZ()); qz <= QuartPos.fromBlock(ring.maxZ()); qz++)
            {
                Holder<Biome> biome = level.getNoiseBiome(qx, biomeY, qz);
                String name = SiteSearch.name(biome);
                biomes.add(name);
                if (!rules.allows(biome))
                {
                    wrong.add(name);
                }
            }
        }
        if (!wrong.isEmpty())
        {
            problems.add("biomes that aren't allowed under the footprint or ring: " + String.join(", ", wrong));
        }

        List<ChunkPos> villages = SiteVillages.Finder.of(level).villagesNear(ring, SiteRules.VILLAGE_REACH + SiteRules.VILLAGE_CLEARANCE);
        if (!villages.isEmpty())
        {
            problems.add("a village could start at chunk " + villages.get(0) + " (too close)");
        }
        List<String> realVillages = villagesIn(level, ring.inflatedBy(SiteRules.VILLAGE_CLEARANCE));
        if (!realVillages.isEmpty())
        {
            problems.add("a village within " + SiteRules.VILLAGE_CLEARANCE + " blocks of the ring: " + String.join(", ", realVillages));
        }
        List<String> structures = structuresIn(level, ring, plan.groundY - SiteRules.STRUCTURE_DEPTH);
        if (!structures.isEmpty())
        {
            problems.add("structures in the way: " + String.join(", ", structures));
        }
        return new Result(x, z, problems, plan, distance, biomes);
    }

    /** Where the lowest and highest natural ground under the footprint is, and what's there (for the log). */
    private static String extremes(ServerLevel level, GroundFit.Survey survey)
    {
        BoundingBox box = survey.footprint();
        int lowX = box.minX();
        int lowZ = box.minZ();
        int highX = box.minX();
        int highZ = box.minZ();
        int lowCount = 0;
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                int y = survey.surfaceAt(x, z);
                if (y < min)
                {
                    min = y;
                    lowX = x;
                    lowZ = z;
                }
                if (y > max)
                {
                    max = y;
                    highX = x;
                    highZ = z;
                }
            }
        }
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                if (survey.surfaceAt(x, z) < min + 2)
                {
                    lowCount++;
                }
            }
        }
        BlockPos low = new BlockPos(lowX, min, lowZ);
        BlockPos high = new BlockPos(highX, max, highZ);
        StringBuilder column = new StringBuilder();
        for (int y = min; y <= max + 2; y++)
        {
            column.append(y == min ? "" : ",").append(blockName(level, new BlockPos(lowX, y, lowZ)));
        }
        return "lowest " + low.toShortString() + " " + blockName(level, low) + " (" + lowCount + " columns that low; going up: "
                + column + "), highest " + high.toShortString() + " " + blockName(level, high);
    }

    private static String blockName(ServerLevel level, BlockPos pos)
    {
        var key = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(level.getBlockState(pos).getBlock());
        return key != null ? key.getPath() : "?";
    }

    /**
     * Every village whose bounding box (all its houses, streets and fields) reaches into the area, seen from above.
     * Found through the structure references the chunks there keep (cheap: no chunk is fully generated for it).
     */
    static List<String> villagesIn(ServerLevel level, BoundingBox area)
    {
        Set<String> found = new LinkedHashSet<>();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++)
        {
            for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++)
            {
                for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(cx, cz),
                        structure -> registry.wrapAsHolder(structure).is(StructureTags.VILLAGE)))
                {
                    BoundingBox box = start.getBoundingBox();
                    if (box.maxX() >= area.minX() && box.minX() <= area.maxX() && box.maxZ() >= area.minZ() && box.minZ() <= area.maxZ())
                    {
                        var key = registry.getKey(start.getStructure());
                        found.add((key != null ? key.toString() : "a village") + " from " + box.minX() + " " + box.minZ()
                                + " to " + box.maxX() + " " + box.maxZ());
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    /** Every structure whose box reaches into the area (seen from above) and up to at least {@code minTopY}. */
    static List<String> structuresIn(ServerLevel level, BoundingBox area, int minTopY)
    {
        Set<String> found = new LinkedHashSet<>();
        var registry = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (int cx = area.minX() >> 4; cx <= area.maxX() >> 4; cx++)
        {
            for (int cz = area.minZ() >> 4; cz <= area.maxZ() >> 4; cz++)
            {
                for (StructureStart start : level.structureManager().startsForStructure(new ChunkPos(cx, cz), structure -> true))
                {
                    BoundingBox box = start.getBoundingBox();
                    if (box.maxY() >= minTopY && box.maxX() >= area.minX() && box.minX() <= area.maxX()
                            && box.maxZ() >= area.minZ() && box.minZ() <= area.maxZ())
                    {
                        var key = registry.getKey(start.getStructure());
                        found.add((key != null ? key.toString() : "a structure") + " at " + box.getCenter().toShortString());
                    }
                }
            }
        }
        return new ArrayList<>(found);
    }

    private SiteCheck() {}
}
