package com.reg21meme.dross.boss.rimefall;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Headless tests (GameTest) for the Rimefall boss candidates ({@code .\gradlew.bat runGameTestServer}):
 * <ul>
 *   <li><b>Frozen exhibits</b> (in the Cinder Colossus tests' arena, {@link ColossusGameTest#ARENA}): every design spawns
 *       as a candidate of that design, its hitbox the design's measured size; an ordinary hit doesn't hurt it, a push
 *       doesn't move it, it has no AI, and it's still standing in the same spot a while later.</li>
 *   <li><b>The gallery</b> ({@code /dross showcase boss_designs rimefall}, run far from everything else at
 *       {@link #GALLERY_X}, {@link #GALLERY_Z}): every candidate stands on its floor, each design once, each facing
 *       straight along a compass direction; running it again replaces them (still one of each).</li>
 *   <li><b>Real blocks</b>: every block a candidate is drawn with (the Fisher's bait cabin) is a real block state the
 *       game can read, and none is air.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RimefallShowcaseGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Far more damage than a candidate's health. */
    private static final float BIG_HIT = 1000.0F;
    /** How long the candidates are watched after spawning (ticks). */
    private static final int WATCH_TICKS = 40;
    private static final double SIZE_TOLERANCE = 0.01D;
    /** Where the gallery test runs: far from spawn, the portal site (3,000 blocks out and more) and the other tests. */
    private static final int GALLERY_X = 2200;
    private static final int GALLERY_Z = 2200;
    /** The gallery's candidates are looked for this far round its spot (X and Z), at every height. */
    private static final int SEARCH_RADIUS = 320;

    private RimefallShowcaseGameTest() {}

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_rimefall_showcase",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 200)
    public static void candidatesStayFrozen(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallShowcaseGameTest::candidatesStayFrozen))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<RimefallCandidate> candidates = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        for (RimefallDesigns.Design design : RimefallDesigns.ALL)
        {
            Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(ColossusGameTest.MIDDLE));
            RimefallCandidate candidate = RimefallCandidate.spawnShowcase(level, feet, 0.0F, design);
            if (candidate == null || candidate.getDesign().number() != design.number())
            {
                throw new GameTestAssertException("couldn't spawn Rimefall candidate No. " + design.label());
            }
            AABB box = candidate.getBoundingBox();
            if (Math.abs(box.getXsize() - design.width()) > SIZE_TOLERANCE
                    || Math.abs(box.getYsize() - design.height()) > SIZE_TOLERANCE)
            {
                throw new GameTestAssertException("No. " + design.label() + "'s hitbox is " + box.getXsize() + " x "
                        + box.getYsize() + ", not " + design.width() + " x " + design.height());
            }
            if (!candidate.isNoAi() || candidate.isPushable())
            {
                throw new GameTestAssertException("No. " + design.label() + " should have no AI and not be pushable");
            }
            float before = candidate.getHealth();
            candidate.hurt(level.damageSources().generic(), BIG_HIT);
            candidate.push(2.0D, 0.0D, 2.0D);
            if (candidate.isRemoved() || candidate.getHealth() < before)
            {
                throw new GameTestAssertException("No. " + design.label() + " was hurt by an ordinary hit");
            }
            candidates.add(candidate);
            spots.add(feet);
        }
        helper.runAfterDelay(WATCH_TICKS, () -> {
            try
            {
                for (int i = 0; i < candidates.size(); i++)
                {
                    RimefallCandidate candidate = candidates.get(i);
                    Vec3 spot = spots.get(i);
                    if (!candidate.isAlive() || Math.abs(candidate.getX() - spot.x) > SIZE_TOLERANCE
                            || Math.abs(candidate.getZ() - spot.z) > SIZE_TOLERANCE)
                    {
                        throw new GameTestAssertException("No. " + candidate.getDesign().label() + " didn't stay where it "
                                + "was put (alive " + candidate.isAlive() + ", at " + candidate.position() + ")");
                    }
                }
                LOGGER.info("[rimefall test] {} candidates spawned with their designs' hitboxes, shrugged off a big hit "
                        + "and a push, and stayed put", candidates.size());
            }
            finally
            {
                candidates.forEach(RimefallCandidate::discard);
            }
            helper.succeed();
        });
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_rimefall_gallery", timeoutTicks = 400)
    public static void galleryHoldsEveryCandidate(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel overworld = server.overworld();
        BlockPos column = new BlockPos(GALLERY_X, 0, GALLERY_Z);
        overworld.getChunkAt(column);
        int feetY = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
        try
        {
            int result = runGallery(server, overworld, column, feetY);
            checkGallery(overworld, column, result, "first run");
            result = runGallery(server, overworld, column, feetY);
            checkGallery(overworld, column, result, "second run");
            LOGGER.info("[rimefall test] The gallery holds all {} candidates, each design once, standing on its floor and "
                    + "facing along the compass; running it again replaced them", RimefallDesigns.ALL.size());
        }
        finally
        {
            found(overworld, column).forEach(RimefallCandidate::discard);
        }
        helper.succeed();
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_rimefall_blocks")
    public static void candidateBlocksAreRealBlocks(GameTestHelper helper)
    {
        int count = 0;
        for (RimefallDesigns.Design design : RimefallDesigns.ALL)
        {
            for (RimefallDesigns.Placed placed : design.blocks())
            {
                BlockState state;
                try
                {
                    state = RimefallBlocks.parse(placed.state());
                }
                catch (CommandSyntaxException e)
                {
                    throw new GameTestAssertException("No. " + design.label() + ": can't read the block state '"
                            + placed.state() + "': " + e.getMessage());
                }
                if (state.isAir())
                {
                    throw new GameTestAssertException("No. " + design.label() + ": '" + placed.state() + "' is air");
                }
                count++;
            }
        }
        if (count == 0)
        {
            throw new GameTestAssertException("no candidate has any real blocks (the Fisher's cabin should)");
        }
        LOGGER.info("[rimefall test] All {} real blocks the candidates are drawn with are block states the game reads",
                count);
        helper.succeed();
    }

    /** Runs {@code /dross showcase boss_designs rimefall} at {@code column}, feet at {@code feetY}, looking south. */
    private static int runGallery(MinecraftServer server, ServerLevel level, BlockPos column, int feetY)
    {
        var source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                .withPosition(new Vec3(column.getX() + 0.5D, feetY, column.getZ() + 0.5D)).withRotation(new Vec2(0.0F, 0.0F))
                .withSuppressedOutput();
        return server.getCommands().performPrefixedCommand(source, "dross showcase boss_designs rimefall");
    }

    private static List<RimefallCandidate> found(ServerLevel level, BlockPos column)
    {
        AABB area = new AABB(column.getX() - SEARCH_RADIUS, level.getMinBuildHeight(), column.getZ() - SEARCH_RADIUS,
                column.getX() + SEARCH_RADIUS, level.getMaxBuildHeight(), column.getZ() + SEARCH_RADIUS);
        return level.getEntities(ModEntities.RIMEFALL_CANDIDATE.get(), area, candidate -> true);
    }

    private static void checkGallery(ServerLevel level, BlockPos column, int result, String when)
    {
        int count = RimefallDesigns.ALL.size();
        if (result != count)
        {
            throw new GameTestAssertException(when + ": the command spawned " + result + " candidates, not " + count);
        }
        List<RimefallCandidate> candidates = found(level, column);
        Set<Integer> numbers = new TreeSet<>();
        double floor = Double.NaN;
        for (RimefallCandidate candidate : candidates)
        {
            numbers.add(candidate.getDesign().number());
            if (Double.isNaN(floor))
            {
                floor = candidate.getY();
            }
            if (Math.abs(candidate.getY() - floor) > SIZE_TOLERANCE)
            {
                throw new GameTestAssertException(when + ": No. " + candidate.getDesign().label() + " stands at y "
                        + candidate.getY() + ", not on the gallery's floor (y " + floor + ")");
            }
            float yaw = candidate.getYRot();
            if (Math.abs(Math.IEEEremainder(yaw, 90.0D)) > 0.01D)
            {
                throw new GameTestAssertException(when + ": No. " + candidate.getDesign().label() + " faces " + yaw
                        + " degrees, not straight along a compass direction");
            }
        }
        if (candidates.size() != count || numbers.size() != count)
        {
            throw new GameTestAssertException(when + ": found " + candidates.size() + " candidates with " + numbers.size()
                    + " different designs, not " + count + " of each");
        }
    }
}
