package com.reg21meme.dross.world.shrine;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.BushBlock;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.VineBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.NoiseBasedChunkGenerator;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for {@link GroundFit} on other worlds' ground: the Dross's, through the block tags
 * {@code #dross:fit_surface} and {@code #dross:fit_soil}. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}, or in a dev world with {@code /test runall}. They only exist
 * in the development environment: in a normal game nothing here runs.
 * <ul>
 *   <li>{@link #groundBlocksStillCount}: every vanilla block that counted as a top-of-the-ground block, as soil or as
 *       grass (turned to soil when buried) still counts exactly as before, so the Overworld's cathedral is fitted as it
 *       always was; every block in the two tags counts too; huge mushrooms (vanilla's and the Dross's blightcaps) are
 *       looked through and felled whole; the Dross's plants and trees aren't ground; deep fill is the level's base
 *       block (stone in the Overworld, netherrack in the Nether).</li>
 *   <li>{@link #bumpyDrossGroundIsFitted}: a small footprint on a bumpy patch of Dross ground (Silvermoss over dirt over
 *       Duskstone, with an Ash hilltop, a dip where Silvermoss lies straight on Duskstone, a pit, a pillar, a hidden
 *       cave, Glimmer Ferns, a silverwood tree with Stormvine and two giant blightcaps) is surveyed, planned and
 *       applied. The survey must see the real ground under the plants, the tree and the blightcaps. Afterwards: the
 *       footprint is level; every column keeps its own top block (Silvermoss or Ash, never grass); every buried top
 *       became its column's soil (dirt, or Duskstone where there was no dirt); deep fill is the level's base block; the
 *       staircase rule holds (a column d blocks out is never more than d above or below the build level); the tree and
 *       the blightcap that touched the reshaped ground are gone whole, while the other blightcap and the far fern stay.
 *       The patch is built high in the Dross's sky, far from the hub ({@link #ARENA_SPOTS}), because GroundFit
 *       measures each column from its top down, and the test removes all of it afterwards.</li>
 * </ul>
 * Both use world-builder's empty template ({@link PortalSiteGameTest#EMPTY}) and their own batch.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class GroundFitTagsGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "dross_ground_fit";

    // ---------------------------------------------------------------------------------------------
    // What counted before the tags (copied from GroundFit: a change to its vanilla lists fails here)
    // ---------------------------------------------------------------------------------------------

    /** Vanilla's top-of-the-ground blocks. */
    private static final List<Block> VANILLA_SURFACE = List.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM,
            Blocks.COARSE_DIRT, Blocks.MOSS_BLOCK, Blocks.SAND, Blocks.RED_SAND, Blocks.SNOW_BLOCK, Blocks.GRAVEL,
            Blocks.PACKED_ICE, Blocks.MUD);
    /** Vanilla's soil blocks (every terracotta too, {@code #minecraft:terracotta}). */
    private static final List<Block> VANILLA_SOIL = List.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.SAND, Blocks.RED_SAND,
            Blocks.SANDSTONE, Blocks.RED_SANDSTONE, Blocks.GRAVEL, Blocks.SNOW_BLOCK, Blocks.PACKED_ICE, Blocks.STONE,
            Blocks.CLAY);
    /** Vanilla's tops that turn into soil when buried. */
    private static final List<Block> VANILLA_GRASS_LIKE = List.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM);
    /** Vanilla blocks that never counted as each kind, and still mustn't. */
    private static final List<Block> NEVER_SURFACE = List.of(Blocks.STONE, Blocks.DIRT, Blocks.CLAY, Blocks.SANDSTONE, Blocks.DEEPSLATE);
    private static final List<Block> NEVER_SOIL = List.of(Blocks.GRASS_BLOCK, Blocks.PODZOL, Blocks.MYCELIUM, Blocks.MOSS_BLOCK,
            Blocks.MUD, Blocks.DEEPSLATE);
    private static final List<Block> NEVER_GRASS_LIKE = List.of(Blocks.DIRT, Blocks.COARSE_DIRT, Blocks.SAND, Blocks.STONE,
            Blocks.SNOW_BLOCK);
    /** Vanilla's huge mushroom blocks. */
    private static final List<Block> VANILLA_HUGE_MUSHROOM = List.of(Blocks.MUSHROOM_STEM, Blocks.RED_MUSHROOM_BLOCK,
            Blocks.BROWN_MUSHROOM_BLOCK);

    // ---------------------------------------------------------------------------------------------
    // The bumpy patch of Dross ground. Positions in it are "footprint-relative": fx and fz count from the footprint's
    // lowest corner (0..8 is the footprint), heights from the ground level.
    // ---------------------------------------------------------------------------------------------

    /** Where the footprint's lowest corner goes (X, Z in the Dross), far from the hub at 0, 0; then spares, in case the land is too high. */
    private static final int[][] ARENA_SPOTS = {{5000, -5000}, {-5000, 5000}, {9000, 9000}};
    /** The footprint is this many blocks square. */
    private static final int FOOT = 9;
    /** The staircase may reach this many blocks out (the hub uses 16; a small one keeps the patch small). */
    private static final int BLEND = 6;
    /** Water this close to the footprint is counted (there is none). */
    private static final int WATER_CLEARANCE = 4;
    /** The patch's flat ground is at this height, high in the sky above any land (higher if the land there is). */
    private static final int PREFERRED_GROUND = 280;
    /** The patch is solid this many blocks under its ground level (the pit is 5 deep: 7 more solid blocks under it). */
    private static final int BASE_DEPTH = 12;
    /** And it floats at least this many blocks above the land under it. */
    private static final int AIR_GAP = 4;
    /** Every column has this many soil blocks under its top block; Duskstone below them. */
    private static final int SOIL_LAYERS = 2;
    /** Inside the footprint everything is cleared up to this many blocks above the ground level (the hill's blightcap reaches 9). */
    private static final int CLEAR_UP = 12;
    /** GroundFit fills new ground with the column's soil this many blocks deep, then the level's base block. */
    private static final int FILL_SOIL_DEPTH = 3;

    /** A hill east of the footprint, partly over it: its top 5 up, 1 block lower every step out (Ash on its top 3x3). */
    private static final int HILL_X = 10;
    private static final int HILL_Z = 4;
    private static final int HILL_HEIGHT = 5;
    private static final int ASH_TOP = 1;
    /** A dip west of the footprint: 4 down at the middle, 1 block less every step out. */
    private static final int DIP_X = -3;
    private static final int DIP_Z = 4;
    private static final int DIP_DEPTH = 4;
    /** West of the footprint and from this fz south, Silvermoss lies straight on Duskstone (no dirt). */
    private static final int NO_DIRT_FROM_Z = 5;
    /** A one-block pit in the footprint, 5 deep (filled: 3 soil, then the level's base block). */
    private static final int PIT_X = 2;
    private static final int PIT_Z = 6;
    private static final int PIT_DEPTH = 5;
    /** A one-block pillar in the footprint, 3 high (cut). */
    private static final int PILLAR_X = 4;
    private static final int PILLAR_Z = 6;
    private static final int PILLAR_HEIGHT = 3;
    /** A hidden cave under a footprint column: air 2 and 3 blocks under the ground (filled with the level's base block). */
    private static final int CAVE_X = 3;
    private static final int CAVE_Z = 2;
    /** A silverwood tree in the dip (its column is raised by the staircase, so the tree must go whole), 4 logs tall. */
    private static final int TREE_X = -2;
    private static final int TREE_Z = 4;
    private static final int TRUNK = 4;
    /** A giant blightcap on the hilltop (touched: it must go whole), and one far out on flat ground (untouched: it stays). */
    private static final int FAR_CAP_X = 4;
    private static final int FAR_CAP_Z = -9;
    /** A blightcap's stem is this tall, under a 3x3 cap. */
    private static final int STEM = 3;
    /** Glimmer Ferns: in the footprint (cleared), on a raised ring column (cleared) and far out (stays). */
    private static final int[] FERN_IN_FOOTPRINT = {1, 7};
    private static final int[] FERN_ON_RING = {-1, 3};
    private static final int[] FERN_FAR = {16, 16};

    private GroundFitTagsGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Which blocks count
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH)
    public static void groundBlocksStillCount(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos pos = helper.absolutePos(BlockPos.ZERO); // only for the blocks' collision shapes
        List<String> problems = new ArrayList<>();

        // 1. Vanilla: exactly as before.
        for (Block block : VANILLA_SURFACE)
        {
            expect(problems, GroundFit.isSurfaceBlock(block.defaultBlockState()), name(block) + " should still be a top-of-the-ground block");
        }
        List<Block> soil = new ArrayList<>(VANILLA_SOIL);
        tagMembers(BlockTags.TERRACOTTA).forEach(soil::add);
        for (Block block : soil)
        {
            expect(problems, GroundFit.isSoil(block.defaultBlockState()), name(block) + " should still be soil");
        }
        for (Block block : VANILLA_GRASS_LIKE)
        {
            expect(problems, GroundFit.isGrassLike(block.defaultBlockState()), name(block) + " should still turn to soil when buried");
        }
        for (Block block : NEVER_SURFACE)
        {
            expect(problems, !GroundFit.isSurfaceBlock(block.defaultBlockState()), name(block) + " must not be a top-of-the-ground block");
        }
        for (Block block : NEVER_SOIL)
        {
            expect(problems, !GroundFit.isSoil(block.defaultBlockState()), name(block) + " must not be soil");
        }
        for (Block block : NEVER_GRASS_LIKE)
        {
            expect(problems, !GroundFit.isGrassLike(block.defaultBlockState()), name(block) + " must not turn to soil when buried");
        }

        // 2. The Dross's ground, through the tags (the Dimension area fills them).
        List<Block> surface = tagMembers(GroundFit.FIT_SURFACE);
        List<Block> fitSoil = tagMembers(GroundFit.FIT_SOIL);
        expect(problems, !surface.isEmpty(), "#dross:fit_surface is empty (not loaded?)");
        expect(problems, !fitSoil.isEmpty(), "#dross:fit_soil is empty (not loaded?)");
        for (Block block : surface)
        {
            BlockState state = block.defaultBlockState();
            expect(problems, GroundFit.isSurfaceBlock(state), name(block) + " (#dross:fit_surface) should be a top-of-the-ground block");
            expect(problems, GroundFit.isGrassLike(state), name(block) + " (#dross:fit_surface) should turn to soil when buried");
            expect(problems, GroundFit.isNaturalGround(level, pos, state), name(block) + " (#dross:fit_surface) should be natural ground");
        }
        for (Block block : fitSoil)
        {
            expect(problems, GroundFit.isSoil(block.defaultBlockState()), name(block) + " (#dross:fit_soil) should be soil");
            expect(problems, GroundFit.isNaturalGround(level, pos, block.defaultBlockState()), name(block) + " (#dross:fit_soil) should be natural ground");
        }
        // The bumpy-ground test builds with these, so they must be in the tags.
        expect(problems, surface.contains(ModBlocks.SILVERMOSS.get()) && surface.contains(ModBlocks.ASH.get()),
                "#dross:fit_surface should hold Silvermoss and Ash (bumpyDrossGroundIsFitted builds with them)");
        expect(problems, fitSoil.contains(ModBlocks.DUSKSTONE.get()) && fitSoil.contains(ModBlocks.ASH.get()),
                "#dross:fit_soil should hold Duskstone and Ash (bumpyDrossGroundIsFitted builds with them)");

        // 3. Huge mushrooms (vanilla's and the Dross's blightcaps) are looked through and felled whole; plants and trees aren't ground.
        List<Block> mushrooms = new ArrayList<>(VANILLA_HUGE_MUSHROOM);
        mushrooms.addAll(List.of(ModBlocks.BLIGHTCAP_BLOCK.get(), ModBlocks.ROTTING_BLIGHTCAP.get(), ModBlocks.BLIGHTCAP_STEM.get()));
        for (Block block : mushrooms)
        {
            BlockState state = block.defaultBlockState();
            expect(problems, ShrineGround.isTreeOrPlant(state), name(block) + " should be a huge mushroom (looked through)");
            expect(problems, TreeFelling.isMushroom(state), name(block) + " should be felled with its huge mushroom");
            expect(problems, !GroundFit.isNaturalGround(level, pos, state), name(block) + " must not be ground");
        }
        for (Block block : List.of(ModBlocks.SILVERWOOD_LOG.get(), ModBlocks.SILVERWOOD_LEAVES.get(), ModBlocks.STORMVINE.get(),
                ModBlocks.CHARRED_LOG.get(), ModBlocks.FROSTBITTEN_LOG.get(), ModBlocks.GLIMMER_FERN.get(), ModBlocks.WISPBLOOM.get(),
                ModBlocks.GLOOMCAP.get(), ModBlocks.EMBERWEED.get(), ModBlocks.ASH_LAYER.get(), ModBlocks.ICICLE.get()))
        {
            expect(problems, !GroundFit.isNaturalGround(level, pos, block.defaultBlockState()), name(block) + " must not be ground");
        }

        // 4. Deep fill is the level's base block.
        MinecraftServer server = level.getServer();
        BlockState overworldFill = GroundFit.deepFillBlock(server.overworld());
        expect(problems, overworldFill.is(Blocks.STONE), "the Overworld's deep fill should be stone, not " + name(overworldFill.getBlock()));
        ServerLevel nether = server.getLevel(Level.NETHER);
        if (nether != null && nether.getChunkSource().getGenerator() instanceof NoiseBasedChunkGenerator)
        {
            BlockState netherFill = GroundFit.deepFillBlock(nether);
            expect(problems, netherFill.is(Blocks.NETHERRACK), "the Nether's deep fill should be netherrack, not " + name(netherFill.getBlock()));
        }
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        String drossFill = dross == null ? "(no Dross)" : name(GroundFit.deepFillBlock(dross).getBlock());

        if (!problems.isEmpty())
        {
            throw new GameTestAssertException(problems.size() + " problem(s): " + String.join("; ", problems));
        }
        LOGGER.info("[ground fit test] {} vanilla top, {} soil and {} grass blocks count as before; {} #dross:fit_surface and {} "
                        + "#dross:fit_soil blocks count; {} huge mushroom blocks are looked through; deep fill: Overworld {}, Dross {}",
                VANILLA_SURFACE.size(), soil.size(), VANILLA_GRASS_LIKE.size(), surface.size(), fitSoil.size(), mushrooms.size(),
                name(overworldFill.getBlock()), drossFill);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Fitting a footprint into bumpy Dross ground
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH)
    public static void bumpyDrossGroundIsFitted(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("the Dross dimension isn't loaded");
        }
        int margin = GroundFit.surveyMargin(BLEND);
        Arena arena = findRoom(dross, margin);
        try
        {
            build(dross, arena);
            List<String> problems = new ArrayList<>();
            BoundingBox footprint = new BoundingBox(arena.x0, arena.bottom(), arena.z0, arena.x0 + FOOT - 1, arena.top(),
                    arena.z0 + FOOT - 1);
            GroundFit.Survey survey = GroundFit.survey(dross, footprint, margin);
            GroundFit.Plan plan = GroundFit.plan(survey, BLEND, WATER_CLEARANCE);
            checkSurvey(problems, arena, survey, plan);
            if (problems.isEmpty())
            {
                GroundFit.Report report = GroundFit.apply(dross, plan, arena.ground + CLEAR_UP);
                LOGGER.info("[ground fit test] Bumpy Dross ground at {}, {}, {}: {}; {}", arena.x0, arena.ground, arena.z0,
                        plan.summary(), report.summary());
                checkResult(problems, dross, arena, survey, report);
            }
            if (!problems.isEmpty())
            {
                int shown = Math.min(problems.size(), 12);
                throw new GameTestAssertException(problems.size() + " problem(s), the first " + shown + ": "
                        + String.join("; ", problems.subList(0, shown)));
            }
            LOGGER.info("[ground fit test] The footprint is level, every column kept its own top (Silvermoss or Ash), buried tops "
                    + "became soil, deep fill is {}, the staircase steps at most 1 block, the tree and the touched blightcap went "
                    + "whole and the rest stayed", name(survey.deepFill().getBlock()));
        }
        finally
        {
            clear(dross, arena, margin);
        }
        helper.succeed();
    }

    /** The checks before anything changes: the survey saw the real ground, and the plan is what the patch was made for. */
    private static void checkSurvey(List<String> problems, Arena a, GroundFit.Survey survey, GroundFit.Plan plan)
    {
        for (int fx = -a.margin; fx < FOOT + a.margin; fx++)
        {
            for (int fz = -a.margin; fz < FOOT + a.margin; fz++)
            {
                int measured = survey.surfaceAt(a.x0 + fx, a.z0 + fz);
                int expected = a.ground + natural(fx, fz);
                if (measured != expected)
                {
                    problems.add("the survey measured column " + fx + ", " + fz + " at y " + measured + ", but its ground is at "
                            + expected + " (plants, the tree and blightcaps must be looked through)");
                }
            }
        }
        expect(problems, plan.groundY == a.ground, "the plan's ground level is " + plan.groundY + ", not the median " + a.ground);
        expect(problems, plan.overflow == 0, "the staircase doesn't fit in " + BLEND + " blocks (" + plan.overflow + " columns)");
        expect(problems, plan.steepSteps == 0, "the plan has " + plan.steepSteps + " steep steps");
        expect(problems, plan.liquidInTheWay == 0, "the plan found water in the way");
        expect(problems, plan.ringReach < BLEND, "the staircase reaches " + plan.ringReach + " blocks out");
    }

    /** The checks after the plan was applied. */
    private static void checkResult(List<String> problems, ServerLevel dross, Arena a, GroundFit.Survey survey, GroundFit.Report report)
    {
        BlockState deepFill = GroundFit.deepFillBlock(dross);
        expect(problems, survey.deepFill() == deepFill && report.deepFill() == deepFill,
                "the deep fill isn't the level's base block " + name(deepFill.getBlock()));
        expect(problems, report.felling().trees() == 1, "felled " + report.felling().trees() + " trees, not the 1 silverwood");
        expect(problems, report.felling().mushrooms() == 1, "felled " + report.felling().mushrooms() + " huge mushrooms, not the 1 blightcap on the hill");
        BlockState silvermoss = ModBlocks.SILVERMOSS.get().defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int fx = -a.margin; fx < FOOT + a.margin; fx++)
        {
            for (int fz = -a.margin; fz < FOOT + a.margin; fz++)
            {
                int x = a.x0 + fx;
                int z = a.z0 + fz;
                String column = "column " + fx + ", " + fz;
                int d = GroundFit.distanceOutside(survey.footprint(), x, z);
                int h = natural(fx, fz);
                int t = d == 0 ? 0 : Mth.clamp(h, -d, d);
                int top = groundTop(dross, a, x, z);
                if (top != a.ground + t)
                {
                    problems.add(column + " (" + d + " out): its ground is at y " + top + ", expected " + (a.ground + t)
                            + " (natural " + (a.ground + h) + ")");
                    continue;
                }
                if (d > 0 && Math.abs(top - a.ground) > d)
                {
                    problems.add(column + " is " + d + " blocks out but " + Math.abs(top - a.ground) + " from the ground level");
                }
                BlockState topState = dross.getBlockState(pos.set(x, top, z));
                if (topState != topOf(fx, fz))
                {
                    problems.add(column + ": its top is " + name(topState.getBlock()) + ", not its own " + name(topOf(fx, fz).getBlock()));
                }
                if (t > h)
                {
                    // Filled: the buried top became the column's soil; new ground is soil near the top, the base block deeper.
                    BlockState buried = dross.getBlockState(pos.set(x, a.ground + h, z));
                    if (buried != soilOf(fx, fz))
                    {
                        problems.add(column + ": its buried top became " + name(buried.getBlock()) + ", not its soil "
                                + name(soilOf(fx, fz).getBlock()));
                    }
                    for (int y = a.ground + h + 1; y < top; y++)
                    {
                        BlockState expected = top - y <= FILL_SOIL_DEPTH ? soilOf(fx, fz) : deepFill;
                        BlockState found = dross.getBlockState(pos.set(x, y, z));
                        if (found != expected)
                        {
                            problems.add(column + ": filled with " + name(found.getBlock()) + " at y " + y + ", not " + name(expected.getBlock()));
                        }
                    }
                }
                int clearedTo = d == 0 ? a.ground + CLEAR_UP : a.ground + h;
                for (int y = top + 1; y <= clearedTo; y++)
                {
                    if (!dross.getBlockState(pos.set(x, y, z)).isAir())
                    {
                        problems.add(column + ": " + name(dross.getBlockState(pos).getBlock()) + " left at y " + y + " above its new ground");
                        break;
                    }
                }
                for (int y = a.bottom(); y < top; y++)
                {
                    if (dross.getBlockState(pos.set(x, y, z)) == silvermoss)
                    {
                        problems.add(column + ": Silvermoss buried at y " + y + " (it should have become soil)");
                        break;
                    }
                }
            }
        }
        // The hidden cave was filled with the level's base block.
        for (int dy = 2; dy <= 3; dy++)
        {
            BlockState found = dross.getBlockState(pos.set(a.x0 + CAVE_X, a.ground - dy, a.z0 + CAVE_Z));
            expect(problems, found == deepFill, "the hidden cave " + dy + " under the ground holds " + name(found.getBlock())
                    + ", not " + name(deepFill.getBlock()));
        }
        // The tree went whole (logs, leaves, Stormvine), and the blightcap on the hill; the far blightcap and fern stayed.
        int treeParts = count(dross, a, state -> state.is(ModBlocks.SILVERWOOD_LOG.get()) || state.is(ModBlocks.SILVERWOOD_LEAVES.get())
                || state.is(ModBlocks.STORMVINE.get()));
        expect(problems, treeParts == 0, treeParts + " pieces of the silverwood tree (logs, leaves, Stormvine) are left");
        for (BlockPos cap : blightcap(a, HILL_X, HILL_Z))
        {
            expect(problems, !(dross.getBlockState(cap).getBlock() instanceof HugeMushroomBlock),
                    "a piece of the hill's blightcap is left at " + cap.toShortString());
        }
        for (BlockPos cap : blightcap(a, FAR_CAP_X, FAR_CAP_Z))
        {
            expect(problems, dross.getBlockState(cap).getBlock() instanceof HugeMushroomBlock,
                    "the far blightcap (untouched) lost its piece at " + cap.toShortString());
        }
        expect(problems, isFern(dross, fern(a, FERN_FAR)), "the far Glimmer Fern (untouched) is gone");
        expect(problems, !isFern(dross, fern(a, FERN_IN_FOOTPRINT)), "the Glimmer Fern in the footprint is still there");
        expect(problems, !isFern(dross, fern(a, FERN_ON_RING)), "the Glimmer Fern on the raised ring column is still there");
    }

    // ---------------------------------------------------------------------------------------------
    // The patch
    // ---------------------------------------------------------------------------------------------

    /**
     * Where the patch goes: its footprint's lowest corner ({@code x0}, {@code z0}), its ground level and how far the
     * survey reaches round the footprint.
     */
    private record Arena(int x0, int z0, int ground, int margin)
    {
        /** The patch's lowest block. */
        int bottom()
        {
            return ground - BASE_DEPTH;
        }

        /** The highest y the test cleans up. */
        int top()
        {
            return ground + CLEAR_UP + 2;
        }

        BlockPos at(int fx, int y, int fz)
        {
            return new BlockPos(x0 + fx, y, z0 + fz);
        }
    }

    /** The first spot whose land leaves room for the patch in the sky above it (its chunks are loaded on the way). */
    private static Arena findRoom(ServerLevel dross, int margin)
    {
        StringBuilder tried = new StringBuilder();
        for (int[] spot : ARENA_SPOTS)
        {
            int land = dross.getMinBuildHeight();
            for (int x = spot[0] - margin - 1; x <= spot[0] + FOOT + margin; x++)
            {
                for (int z = spot[1] - margin - 1; z <= spot[1] + FOOT + margin; z++)
                {
                    dross.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)); // loaded first
                    land = Math.max(land, dross.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                }
            }
            int ground = Math.max(PREFERRED_GROUND, land + AIR_GAP + BASE_DEPTH + 1);
            if (ground + CLEAR_UP + 2 < dross.getMaxBuildHeight())
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
        if (fx == PIT_X && fz == PIT_Z)
        {
            return -PIT_DEPTH;
        }
        if (fx == PILLAR_X && fz == PILLAR_Z)
        {
            return PILLAR_HEIGHT;
        }
        int hill = Math.max(0, HILL_HEIGHT - chebyshev(fx - HILL_X, fz - HILL_Z));
        int dip = Math.max(0, DIP_DEPTH - chebyshev(fx - DIP_X, fz - DIP_Z));
        return hill - dip;
    }

    /** A column's own top block: Ash on the hilltop, Silvermoss everywhere else. */
    private static BlockState topOf(int fx, int fz)
    {
        return chebyshev(fx - HILL_X, fz - HILL_Z) <= ASH_TOP ? ModBlocks.ASH.get().defaultBlockState()
                : ModBlocks.SILVERMOSS.get().defaultBlockState();
    }

    /** A column's soil (the blocks under its top): Ash under Ash, Duskstone in the dip's south-west, dirt elsewhere. */
    private static BlockState soilOf(int fx, int fz)
    {
        if (chebyshev(fx - HILL_X, fz - HILL_Z) <= ASH_TOP)
        {
            return ModBlocks.ASH.get().defaultBlockState();
        }
        return fx < 0 && fz >= NO_DIRT_FROM_Z ? ModBlocks.DUSKSTONE.get().defaultBlockState() : Blocks.DIRT.defaultBlockState();
    }

    /** Builds the patch: its ground, the cave, the ferns, the tree and the blightcaps. Nothing pokes its neighbours. */
    private static void build(ServerLevel dross, Arena a)
    {
        BlockState duskstone = ModBlocks.DUSKSTONE.get().defaultBlockState();
        for (int fx = -a.margin; fx < FOOT + a.margin; fx++)
        {
            for (int fz = -a.margin; fz < FOOT + a.margin; fz++)
            {
                int h = a.ground + natural(fx, fz);
                for (int y = a.bottom(); y <= h; y++)
                {
                    set(dross, a.at(fx, y, fz), y == h ? topOf(fx, fz) : y >= h - SOIL_LAYERS ? soilOf(fx, fz) : duskstone);
                }
            }
        }
        BlockState air = Blocks.AIR.defaultBlockState();
        set(dross, a.at(CAVE_X, a.ground - 2, CAVE_Z), air);
        set(dross, a.at(CAVE_X, a.ground - 3, CAVE_Z), air);
        for (int[] fern : List.of(FERN_IN_FOOTPRINT, FERN_ON_RING, FERN_FAR))
        {
            set(dross, fern(a, fern), ModBlocks.GLIMMER_FERN.get().defaultBlockState());
        }

        // The silverwood tree: a trunk, leaves round and over its top log, Stormvine on its west side.
        int base = a.ground + natural(TREE_X, TREE_Z) + 1;
        int crown = base + TRUNK - 1;
        for (int y = base; y <= crown; y++)
        {
            set(dross, a.at(TREE_X, y, TREE_Z), ModBlocks.SILVERWOOD_LOG.get().defaultBlockState());
        }
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                for (int dy = 0; dy <= 1; dy++)
                {
                    if (dx != 0 || dz != 0 || dy != 0)
                    {
                        set(dross, a.at(TREE_X + dx, crown + dy, TREE_Z + dz), ModBlocks.SILVERWOOD_LEAVES.get().defaultBlockState());
                    }
                }
            }
        }
        BlockState vine = ModBlocks.STORMVINE.get().defaultBlockState().setValue(VineBlock.EAST, true);
        set(dross, a.at(TREE_X - 1, crown - 1, TREE_Z), vine);
        set(dross, a.at(TREE_X - 1, crown - 2, TREE_Z), vine);

        for (int[] cap : new int[][] {{HILL_X, HILL_Z}, {FAR_CAP_X, FAR_CAP_Z}})
        {
            List<BlockPos> blocks = blightcap(a, cap[0], cap[1]);
            for (int i = 0; i < blocks.size(); i++)
            {
                Block block = i < STEM ? ModBlocks.BLIGHTCAP_STEM.get() : ModBlocks.BLIGHTCAP_BLOCK.get();
                set(dross, blocks.get(i), block.defaultBlockState());
            }
        }
    }

    /** A giant blightcap standing on the column: its stem first (bottom up), then its 3x3 cap. */
    private static List<BlockPos> blightcap(Arena a, int fx, int fz)
    {
        int ground = a.ground + natural(fx, fz);
        List<BlockPos> blocks = new ArrayList<>();
        for (int i = 1; i <= STEM; i++)
        {
            blocks.add(a.at(fx, ground + i, fz));
        }
        for (int dx = -1; dx <= 1; dx++)
        {
            for (int dz = -1; dz <= 1; dz++)
            {
                blocks.add(a.at(fx + dx, ground + STEM + 1, fz + dz));
            }
        }
        return blocks;
    }

    /** Where a fern stands: on its column's natural ground. */
    private static BlockPos fern(Arena a, int[] column)
    {
        return a.at(column[0], a.ground + natural(column[0], column[1]) + 1, column[1]);
    }

    private static boolean isFern(ServerLevel dross, BlockPos pos)
    {
        return dross.getBlockState(pos).is(ModBlocks.GLIMMER_FERN.get());
    }

    /** The top of a column's ground: its highest block that isn't air, a plant, a tree, a vine or a huge mushroom. */
    private static int groundTop(ServerLevel dross, Arena a, int x, int z)
    {
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int y = a.top(); y >= a.bottom(); y--)
        {
            BlockState state = dross.getBlockState(pos.set(x, y, z));
            Block block = state.getBlock();
            if (state.isAir() || block instanceof BushBlock || block instanceof HugeMushroomBlock || block instanceof VineBlock
                    || state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES))
            {
                continue;
            }
            return y;
        }
        return a.bottom() - 1;
    }

    /** How many blocks of the patch (and the air over it) match. */
    private static int count(ServerLevel dross, Arena a, Predicate<BlockState> kind)
    {
        int found = 0;
        for (BlockPos pos : BlockPos.betweenClosed(a.x0 - a.margin, a.bottom(), a.z0 - a.margin, a.x0 + FOOT + a.margin - 1, a.top(),
                a.z0 + FOOT + a.margin - 1))
        {
            if (kind.test(dross.getBlockState(pos)))
            {
                found++;
            }
        }
        return found;
    }

    /** Removes the whole patch (and anything placed on or around it), leaving air. */
    private static void clear(ServerLevel dross, Arena a, int margin)
    {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos pos : BlockPos.betweenClosed(a.x0 - margin - 1, a.bottom() - 1, a.z0 - margin - 1, a.x0 + FOOT + margin,
                a.top(), a.z0 + FOOT + margin))
        {
            if (!dross.getBlockState(pos).isAir())
            {
                dross.setBlock(pos, air, Block.UPDATE_CLIENTS);
            }
        }
    }

    private static void set(ServerLevel dross, BlockPos pos, BlockState state)
    {
        dross.setBlock(pos, state, GroundFit.FLAGS);
    }

    // ---------------------------------------------------------------------------------------------
    // Small helpers
    // ---------------------------------------------------------------------------------------------

    private static int chebyshev(int dx, int dz)
    {
        return Math.max(Math.abs(dx), Math.abs(dz));
    }

    private static List<Block> tagMembers(TagKey<Block> tag)
    {
        List<Block> blocks = new ArrayList<>();
        ForgeRegistries.BLOCKS.tags().getTag(tag).forEach(blocks::add);
        return blocks;
    }

    private static void expect(List<String> problems, boolean ok, String problem)
    {
        if (!ok)
        {
            problems.add(problem);
        }
    }

    private static String name(Block block)
    {
        return String.valueOf(ForgeRegistries.BLOCKS.getKey(block));
    }
}
