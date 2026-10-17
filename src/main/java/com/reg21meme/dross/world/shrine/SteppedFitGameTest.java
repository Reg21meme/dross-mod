package com.reg21meme.dross.world.shrine;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.feature.ConfiguredFeature;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless test (GameTest) that fitting the ground a step at a time gives <b>exactly</b> the same blocks as fitting it
 * all at once. The Dross hub and the Overworld's cathedral are built a step a tick ({@link ShrineDesign.FittedBuild},
 * {@link GroundFit.Fitting}), and must come out the same as before that. Run it with
 * {@code .\gradlew.bat runGameTestServer --console=plain}; it only exists in the development environment.
 * <ul>
 *   <li>{@link #steppedFitMatchesAtOnce}: a patch of grass high in the Dross's sky, far from the hub, with a hill and a
 *       dip round a 12x12 footprint and a small forest of vanilla trees grown from fixed seeds (a fancy oak with a bee
 *       nest, a dark oak, a birch, a jungle tree with vines and cocoa, oaks, a spruce, a swamp oak with vines, a huge
 *       mushroom, a mossy boulder; canopies touching), plus snow layers on the ground. It's fitted all at once
 *       ({@link GroundFit#apply}), every block of it and round it is saved, the patch is put back exactly as it was,
 *       and it's fitted again, this time a step at a time with no time to spare (one piece of work per step, so every
 *       place a step can stop at is used). Every block must match, and so must the reports.</li>
 * </ul>
 * Everything happens in one server tick, so nothing else changes the blocks meanwhile, and the patch is removed after.
 * It uses world-builder's empty template ({@link PortalSiteGameTest#EMPTY}) and its own batch.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class SteppedFitGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "dross_stepped_fit";

    /** Where the footprint's lowest corner goes (X, Z in the Dross), far from the hub and the other tests; then spares. */
    private static final int[][] ARENA_SPOTS = {{-7000, 7000}, {7000, 7000}, {-7000, -7000}};
    /** The footprint is this many blocks square. */
    private static final int FOOT = 12;
    /** The staircase may reach this many blocks out. */
    private static final int BLEND = 8;
    /** Water this close to the footprint is counted (there is none). */
    private static final int WATER_CLEARANCE = 4;
    /** The patch's flat ground is at this height, above any land (higher if the land there is). */
    private static final int PREFERRED_GROUND = 260;
    /** The patch is solid this many blocks under its ground level, and floats at least AIR_GAP above the land. */
    private static final int BASE_DEPTH = 8;
    private static final int AIR_GAP = 4;
    /** Every column has this many dirt blocks under its grass; Duskstone below them. */
    private static final int SOIL_LAYERS = 3;
    /** The footprint is cleared up to this many blocks above the ground level. */
    private static final int CLEAR_UP = 20;
    /** The saved blocks reach this far above the ground level (the tallest tree is well under it)... */
    private static final int TREE_ROOM = 34;
    /** ...and this far round the surveyed area (canopies near its edge). */
    private static final int PAD = 10;
    /** The stepped fitting must take at least this many steps, or it wasn't really cut into pieces. */
    private static final int MIN_STEPS = 500;
    /** At least this many trees must touch and be felled, or the forest didn't grow. */
    private static final int MIN_TREES = 5;

    /**
     * A hill just east of the footprint, its top 6 up, 1 lower every step out: it reaches into the footprint (cut) and
     * the staircase cuts its slopes outside.
     */
    private static final int HILL_X = 13;
    private static final int HILL_Z = 5;
    private static final int HILL_HEIGHT = 6;
    /** A dip just west of the footprint, 5 down at the middle: it reaches into the footprint and the staircase fills it. */
    private static final int DIP_X = -3;
    private static final int DIP_Z = 6;
    private static final int DIP_DEPTH = 5;

    /** The forest, grown in this order: footprint-relative column, vanilla configured feature, its seed. */
    private record Grow(int fx, int fz, String feature, long seed) {}

    private static final List<Grow> FOREST = List.of(
            new Grow(3, 2, "fancy_oak_bees", 11L),     // in the footprint, with a bee nest
            new Grow(6, 8, "dark_oak", 12L),           // in the footprint
            new Grow(5, -2, "birch", 13L),             // outside, its leaves over the footprint
            new Grow(-2, 0, "jungle_tree", 14L),       // outside, its leaves over the footprint; vines, cocoa
            new Grow(14, 5, "oak", 15L),               // on the hill, which the staircase cuts
            new Grow(-8, -2, "fancy_oak", 16L),        // further out, its canopy by the jungle tree's
            new Grow(6, 13, "spruce", 17L),            // outside, its leaves over the footprint
            new Grow(-2, 6, "swamp_oak", 18L),         // in the dip, which the staircase fills; vines
            new Grow(-6, 12, "oak", 19L),              // further out, by the swamp oak
            new Grow(-9, 9, "birch", 20L),             // further out, by that oak
            new Grow(20, 14, "oak", 21L),              // far out: stays
            new Grow(12, 10, "huge_red_mushroom", 22L), // its cap over the footprint
            new Grow(10, 1, "forest_rock", 23L));      // a mossy boulder in the footprint
    /** Snow layers lie on these footprint-relative columns (the hill's and the dip's reshaped slopes, and the footprint). */
    private static final int[][] SNOW = {{12, 2}, {12, 3}, {-1, 6}, {-1, 7}, {3, 9}};

    private SteppedFitGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH)
    public static void steppedFitMatchesAtOnce(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("the Dross dimension isn't loaded");
        }
        int margin = GroundFit.surveyMargin(BLEND);
        Arena arena = findRoom(dross, margin);
        BoundingBox saved = arena.savedBox();
        try
        {
            int grown = build(dross, arena);
            BoundingBox footprint = new BoundingBox(arena.x0, arena.bottom(), arena.z0, arena.x0 + FOOT - 1, arena.ground + CLEAR_UP,
                    arena.z0 + FOOT - 1);
            int clearTop = arena.ground + CLEAR_UP;
            BlockState[] before = snapshot(dross, saved);

            // 1. All at once.
            GroundFit.Plan plan = GroundFit.plan(GroundFit.survey(dross, footprint, margin), BLEND, WATER_CLEARANCE);
            GroundFit.Report atOnce = GroundFit.apply(dross, plan, clearTop);
            BlockState[] once = snapshot(dross, saved);

            // 2. Put the patch back exactly as it was.
            restore(dross, saved, before);
            List<String> problems = new ArrayList<>();
            int unrestored = differences(before, snapshot(dross, saved), saved, "before", "put back", new ArrayList<>());
            expect(problems, unrestored == 0, unrestored + " blocks weren't put back as they were (the test itself is broken)");

            // 3. A step at a time, one piece of work per step.
            GroundFit.Plan again = GroundFit.plan(GroundFit.survey(dross, footprint, margin), BLEND, WATER_CLEARANCE);
            expect(problems, again.summary().equals(plan.summary()), "the second plan differs from the first: " + again.summary()
                    + " / " + plan.summary());
            GroundFit.Fitting fitting = new GroundFit.Fitting(dross, again, clearTop);
            int steps = 1;
            while (!fitting.step(GroundFit.Budget.millis(0L)))
            {
                steps++;
            }
            GroundFit.Report stepped = fitting.report();
            BlockState[] inSteps = snapshot(dross, saved);

            // 4. Every block, and the reports.
            List<String> shown = new ArrayList<>();
            int different = differences(once, inSteps, saved, "all at once", "a step at a time", shown);
            expect(problems, different == 0, different + " blocks differ between fitting all at once and a step at a time, for "
                    + "example " + String.join("; ", shown));
            expect(problems, stepped.summary().equals(atOnce.summary()), "the reports differ: all at once \"" + atOnce.summary()
                    + "\", a step at a time \"" + stepped.summary() + "\"");
            expect(problems, stepped.felling().columns().equals(atOnce.felling().columns()),
                    "the columns trees were removed from differ");
            expect(problems, atOnce.felling().trees() >= MIN_TREES, "only " + atOnce.felling().trees() + " trees were felled (at least "
                    + MIN_TREES + " expected: did the forest grow? " + grown + " of " + FOREST.size() + " features grew)");
            expect(problems, steps >= MIN_STEPS, "the stepped fitting took only " + steps + " steps (at least " + MIN_STEPS
                    + " expected: is it really cut into pieces?)");
            if (!problems.isEmpty())
            {
                throw new GameTestAssertException(problems.size() + " problem(s): " + String.join("; ", problems));
            }
            LOGGER.info("[stepped fit test] Fitting a patch of forest at {}, {}, {} a step at a time ({} steps: {}) gave exactly the same "
                            + "{} blocks as all at once; {} of {} features grew; {}", arena.x0, arena.ground, arena.z0, steps,
                    fitting.timings(), once.length, grown, FOREST.size(), atOnce.summary());
        }
        finally
        {
            clear(dross, saved);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // The patch
    // ---------------------------------------------------------------------------------------------

    /** Where the patch goes: its footprint's lowest corner, its ground level and how far the survey reaches round the footprint. */
    private record Arena(int x0, int z0, int ground, int margin)
    {
        int bottom()
        {
            return ground - BASE_DEPTH;
        }

        BlockPos at(int fx, int y, int fz)
        {
            return new BlockPos(x0 + fx, y, z0 + fz);
        }

        /** Every block the test saves, compares and removes: the surveyed area and a bit more, from under the patch to over the trees. */
        BoundingBox savedBox()
        {
            int reach = margin + PAD;
            return new BoundingBox(x0 - reach, bottom() - 2, z0 - reach, x0 + FOOT - 1 + reach, ground + TREE_ROOM, z0 + FOOT - 1 + reach);
        }
    }

    /** The first spot whose land leaves room for the patch in the sky above it (its chunks are loaded on the way). */
    private static Arena findRoom(ServerLevel dross, int margin)
    {
        StringBuilder tried = new StringBuilder();
        for (int[] spot : ARENA_SPOTS)
        {
            BoundingBox box = new Arena(spot[0], spot[1], 0, margin).savedBox();
            int land = dross.getMinBuildHeight();
            for (int x = box.minX(); x <= box.maxX(); x++)
            {
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                {
                    dross.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)); // loaded first
                    land = Math.max(land, dross.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                }
            }
            int ground = Math.max(PREFERRED_GROUND, land + AIR_GAP + BASE_DEPTH + 3);
            if (ground + TREE_ROOM < dross.getMaxBuildHeight())
            {
                return new Arena(spot[0], spot[1], ground, margin);
            }
            tried.append(" ").append(spot[0]).append(", ").append(spot[1]).append(" (land up to y ").append(land).append(")");
        }
        throw new GameTestAssertException("no room in the Dross's sky for the test patch at" + tried);
    }

    /** The patch's natural ground at a column, relative to the ground level. */
    private static int natural(int fx, int fz)
    {
        int hill = Math.max(0, HILL_HEIGHT - Math.max(Math.abs(fx - HILL_X), Math.abs(fz - HILL_Z)));
        int dip = Math.max(0, DIP_DEPTH - Math.max(Math.abs(fx - DIP_X), Math.abs(fz - DIP_Z)));
        return hill - dip;
    }

    /**
     * Builds the patch: grass over dirt over Duskstone, the snow layers, then the forest from its fixed seeds.
     *
     * @return how many of the features grew
     */
    private static int build(ServerLevel dross, Arena a)
    {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState duskstone = ModBlocks.DUSKSTONE.get().defaultBlockState();
        for (int fx = -a.margin; fx < FOOT + a.margin; fx++)
        {
            for (int fz = -a.margin; fz < FOOT + a.margin; fz++)
            {
                int h = a.ground + natural(fx, fz);
                for (int y = a.bottom(); y <= h; y++)
                {
                    dross.setBlock(a.at(fx, y, fz), y == h ? grass : y >= h - SOIL_LAYERS ? dirt : duskstone, GroundFit.FLAGS);
                }
            }
        }
        for (int[] column : SNOW)
        {
            dross.setBlock(a.at(column[0], a.ground + natural(column[0], column[1]) + 1, column[1]), Blocks.SNOW.defaultBlockState(),
                    GroundFit.FLAGS);
        }
        int grown = 0;
        var features = dross.registryAccess().registryOrThrow(Registries.CONFIGURED_FEATURE);
        for (Grow tree : FOREST)
        {
            ResourceKey<ConfiguredFeature<?, ?>> key = ResourceKey.create(Registries.CONFIGURED_FEATURE, new ResourceLocation("minecraft", tree.feature()));
            Holder<ConfiguredFeature<?, ?>> feature = features.getHolder(key)
                    .orElseThrow(() -> new GameTestAssertException("there's no configured feature " + key.location()));
            BlockPos base = a.at(tree.fx(), a.ground + natural(tree.fx(), tree.fz()) + 1, tree.fz());
            if (feature.value().place(dross, dross.getChunkSource().getGenerator(), RandomSource.create(tree.seed()), base))
            {
                grown++;
            }
        }
        return grown;
    }

    /** Every block in the box, X outermost, then Y, then Z. */
    private static BlockState[] snapshot(ServerLevel dross, BoundingBox box)
    {
        BlockState[] states = new BlockState[box.getXSpan() * box.getYSpan() * box.getZSpan()];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int y = box.minY(); y <= box.maxY(); y++)
            {
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                {
                    states[i++] = dross.getBlockState(pos.set(x, y, z));
                }
            }
        }
        return states;
    }

    /** Puts every block in the box back to the saved one (nothing pokes its neighbours, so leaves keep their distances). */
    private static void restore(ServerLevel dross, BoundingBox box, BlockState[] states)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int i = 0;
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int y = box.minY(); y <= box.maxY(); y++)
            {
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                {
                    BlockState state = states[i++];
                    if (dross.getBlockState(pos.set(x, y, z)) != state)
                    {
                        dross.setBlock(pos, state, GroundFit.FLAGS);
                    }
                }
            }
        }
    }

    /** How many blocks differ between two snapshots of the box; the first few are described in {@code shown}. */
    private static int differences(BlockState[] a, BlockState[] b, BoundingBox box, String aName, String bName, List<String> shown)
    {
        int different = 0;
        int i = 0;
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int y = box.minY(); y <= box.maxY(); y++)
            {
                for (int z = box.minZ(); z <= box.maxZ(); z++)
                {
                    if (a[i] != b[i])
                    {
                        different++;
                        if (shown.size() < 8)
                        {
                            shown.add(x + " " + y + " " + z + ": " + aName + " " + a[i] + ", " + bName + " " + b[i]);
                        }
                    }
                    i++;
                }
            }
        }
        return different;
    }

    /** Removes everything in the box, leaving air. */
    private static void clear(ServerLevel dross, BoundingBox box)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(box.minX(), box.minY(), box.minZ(), box.maxX(), box.maxY(), box.maxZ()))
        {
            if (!dross.getBlockState(pos).isAir())
            {
                dross.setBlock(pos, air, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void expect(List<String> problems, boolean ok, String problem)
    {
        if (!ok)
        {
            problems.add(problem);
        }
    }
}
