package com.reg21meme.dross.boss.fire;

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
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * Headless tests (GameTest) for the fire servant candidates ({@code .\gradlew.bat runGameTestServer}):
 * <ul>
 *   <li><b>Frozen exhibits</b> (in the Cinder Colossus tests' arena, {@link ColossusGameTest#ARENA}): every design of every
 *       set (the servants, the Emberdactyl's, the Cinder Wastes' mobs) spawns
 *       as a candidate of that design, its hitbox the design's measured size; an ordinary hit doesn't hurt it, a push
 *       doesn't move it, it has no AI, and it's still standing in the same spot a while later.</li>
 *   <li><b>The gallery</b> ({@code /dross showcase boss_designs cinder_wastes}, run far from everything else at
 *       {@link #GALLERY_X}, {@link #GALLERY_Z}, looking south): every servant candidate stands on its floor, each design once,
 *       each facing back towards where the command was run (north); the originals in a row from west to east (boss 1
 *       on the left), each boss's variations behind its original (further south) in the same column; running it again
 *       replaces them (still one of each).</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class FireShowcaseGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Far more damage than a candidate's health. */
    private static final float BIG_HIT = 1000.0F;
    /** How long the candidates are watched after spawning (ticks). */
    private static final int WATCH_TICKS = 40;
    private static final double SIZE_TOLERANCE = 0.01D;
    /** Where the gallery test runs: far from spawn, the portal site and the other tests (the Rimefall one's 2200). */
    private static final int GALLERY_X = 2600;
    private static final int GALLERY_Z = -2600;
    /** The gallery's candidates are looked for this far round its spot (X and Z), at every height. */
    private static final int SEARCH_RADIUS = 320;

    private FireShowcaseGameTest() {}

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_fire_showcase",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 200)
    public static void fireCandidatesStayFrozen(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, FireShowcaseGameTest::fireCandidatesStayFrozen))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<FireCandidate> candidates = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        for (FireDesigns.Design design : FireDesigns.ALL)
        {
            Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(ColossusGameTest.MIDDLE));
            FireCandidate candidate = FireCandidate.spawnShowcase(level, feet, 0.0F, design);
            if (candidate == null || candidate.getDesign().number() != design.number())
            {
                throw new GameTestAssertException("couldn't spawn fire candidate No. " + design.label());
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
                    FireCandidate candidate = candidates.get(i);
                    Vec3 spot = spots.get(i);
                    if (!candidate.isAlive() || Math.abs(candidate.getX() - spot.x) > SIZE_TOLERANCE
                            || Math.abs(candidate.getZ() - spot.z) > SIZE_TOLERANCE)
                    {
                        throw new GameTestAssertException("No. " + candidate.getDesign().label() + " didn't stay where it "
                                + "was put (alive " + candidate.isAlive() + ", at " + candidate.position() + ")");
                    }
                }
                LOGGER.info("[fire test] {} candidates spawned with their designs' hitboxes, shrugged off a big hit and "
                        + "a push, and stayed put", candidates.size());
            }
            finally
            {
                candidates.forEach(FireCandidate::discard);
            }
            helper.succeed();
        });
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_fire_gallery", timeoutTicks = 600)
    public static void fireGalleryHoldsEveryCandidate(GameTestHelper helper)
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
            LOGGER.info("[fire test] The gallery holds all {} servant candidates, each design once, on its floor and "
                    + "facing back, the originals in a row west to east and each one's variations behind it; running it "
                    + "again replaced them", FireDesigns.SERVANTS.size());
        }
        finally
        {
            found(overworld, column).forEach(FireCandidate::discard);
        }
        helper.succeed();
    }

    /** Runs {@code /dross showcase boss_designs cinder_wastes} at {@code column}, feet at {@code feetY}, looking south. */
    private static int runGallery(MinecraftServer server, ServerLevel level, BlockPos column, int feetY)
    {
        var source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                .withPosition(new Vec3(column.getX() + 0.5D, feetY, column.getZ() + 0.5D)).withRotation(new Vec2(0.0F, 0.0F))
                .withSuppressedOutput();
        return server.getCommands().performPrefixedCommand(source, "dross showcase boss_designs cinder_wastes");
    }

    private static List<FireCandidate> found(ServerLevel level, BlockPos column)
    {
        AABB area = new AABB(column.getX() - SEARCH_RADIUS, level.getMinBuildHeight(), column.getZ() - SEARCH_RADIUS,
                column.getX() + SEARCH_RADIUS, level.getMaxBuildHeight(), column.getZ() + SEARCH_RADIUS);
        return level.getEntities(ModEntities.FIRE_CANDIDATE.get(), area, candidate -> true);
    }

    private static void checkGallery(ServerLevel level, BlockPos column, int result, String when)
    {
        int count = FireDesigns.SERVANTS.size();
        if (result != count)
        {
            throw new GameTestAssertException(when + ": the command spawned " + result + " candidates, not " + count);
        }
        List<FireCandidate> candidates = found(level, column);
        Set<Integer> numbers = new TreeSet<>();
        Map<Integer, FireCandidate> byNumber = new HashMap<>();
        double floor = Double.NaN;
        for (FireCandidate candidate : candidates)
        {
            FireDesigns.Design design = candidate.getDesign();
            numbers.add(design.number());
            byNumber.put(design.number(), candidate);
            if (Double.isNaN(floor))
            {
                floor = candidate.getY();
            }
            if (Math.abs(candidate.getY() - floor) > SIZE_TOLERANCE)
            {
                throw new GameTestAssertException(when + ": No. " + design.label() + " stands at y " + candidate.getY()
                        + ", not on the gallery's floor (y " + floor + ")");
            }
            // Looking south, so each one faces north, back towards where the command was run.
            if (Math.abs(Math.IEEEremainder(candidate.getYRot() - 180.0F, 360.0D)) > 0.01D)
            {
                throw new GameTestAssertException(when + ": No. " + design.label() + " faces " + candidate.getYRot()
                        + " degrees, not back towards the command (180)");
            }
        }
        if (candidates.size() != count || numbers.size() != count)
        {
            throw new GameTestAssertException(when + ": found " + candidates.size() + " candidates with " + numbers.size()
                    + " different designs, not " + count + " of each");
        }
        // The layout: looking south, the command's right is west. The originals go from left (east) to right (west),
        // boss by boss; each variation stands further south than the one before it, in the same column.
        double lastX = Double.POSITIVE_INFINITY;
        Map<Integer, FireCandidate> previous = new HashMap<>();
        for (FireDesigns.Design design : FireDesigns.SERVANTS)
        {
            FireCandidate candidate = byNumber.get(design.number());
            if (design.row() == 0)
            {
                if (candidate.getX() >= lastX)
                {
                    throw new GameTestAssertException(when + ": No. " + design.label() + " isn't to the right of the "
                            + "boss before it in the row of originals");
                }
                lastX = candidate.getX();
            }
            else
            {
                FireCandidate before = previous.get(design.column());
                if (before == null || Math.abs(before.getX() - candidate.getX()) > SIZE_TOLERANCE
                        || candidate.getZ() <= before.getZ())
                {
                    throw new GameTestAssertException(when + ": No. " + design.label() + " doesn't stand behind the "
                            + "look before it in its boss's column");
                }
            }
            previous.put(design.column(), candidate);
        }
    }
}
