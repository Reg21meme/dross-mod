package com.reg21meme.dross.boss.blight;

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
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * Headless tests (GameTest) for the Blightwood candidates ({@code .\gradlew.bat runGameTestServer}):
 * <ul>
 *   <li><b>Frozen exhibits</b> (in the Cinder Colossus tests' arena, {@link ColossusGameTest#ARENA}): every design spawns
 *       as a candidate of that design, its hitbox the design's measured size; an ordinary hit doesn't hurt it, a push
 *       doesn't move it, it has no AI, and it's still standing in the same spot a while later.</li>
 *   <li><b>The gallery</b> ({@code /dross showcase boss_designs blightwood}, run far from everything else at
 *       {@link #GALLERY_X}, {@link #GALLERY_Z}): every candidate stands on its floor, each design once, each facing
 *       straight along a compass direction; running it again replaces them (still one of each).</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class BlightShowcaseGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Far more damage than a candidate's health. */
    private static final float BIG_HIT = 1000.0F;
    /** How long the candidates are watched after spawning (ticks). */
    private static final int WATCH_TICKS = 40;
    private static final double SIZE_TOLERANCE = 0.01D;
    /** Where the gallery test runs: far from spawn, the portal site (3,500 blocks out and more) and the other tests. */
    private static final int GALLERY_X = -2600;
    private static final int GALLERY_Z = 2600;
    /** The gallery's candidates are looked for this far round its spot (X and Z), at every height. */
    private static final int SEARCH_RADIUS = 320;

    private BlightShowcaseGameTest() {}

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_blight_showcase",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 200)
    public static void candidatesStayFrozen(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, BlightShowcaseGameTest::candidatesStayFrozen))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<BlightCandidate> candidates = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        for (BlightDesigns.Design design : BlightDesigns.ALL)
        {
            Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(ColossusGameTest.MIDDLE));
            BlightCandidate candidate = BlightCandidate.spawnShowcase(level, feet, 0.0F, design);
            if (candidate == null || candidate.getDesign().number() != design.number())
            {
                throw new GameTestAssertException("couldn't spawn Blightwood candidate No. " + design.label());
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
                    BlightCandidate candidate = candidates.get(i);
                    Vec3 spot = spots.get(i);
                    if (!candidate.isAlive() || Math.abs(candidate.getX() - spot.x) > SIZE_TOLERANCE
                            || Math.abs(candidate.getZ() - spot.z) > SIZE_TOLERANCE)
                    {
                        throw new GameTestAssertException("No. " + candidate.getDesign().label() + " didn't stay where it "
                                + "was put (alive " + candidate.isAlive() + ", at " + candidate.position() + ")");
                    }
                }
                LOGGER.info("[blight test] {} candidates spawned with their designs' hitboxes, shrugged off a big hit "
                        + "and a push, and stayed put", candidates.size());
            }
            finally
            {
                candidates.forEach(BlightCandidate::discard);
            }
            helper.succeed();
        });
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_blight_gallery", timeoutTicks = 400)
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
            LOGGER.info("[blight test] The gallery holds all {} candidates, each design once, standing on its floor and "
                    + "facing along the compass; running it again replaced them", BlightDesigns.ALL.size());
        }
        finally
        {
            found(overworld, column).forEach(BlightCandidate::discard);
        }
        helper.succeed();
    }

    /** Runs {@code /dross showcase boss_designs blightwood} at {@code column}, feet at {@code feetY}, looking south. */
    private static int runGallery(MinecraftServer server, ServerLevel level, BlockPos column, int feetY)
    {
        var source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                .withPosition(new Vec3(column.getX() + 0.5D, feetY, column.getZ() + 0.5D)).withRotation(new Vec2(0.0F, 0.0F))
                .withSuppressedOutput();
        return server.getCommands().performPrefixedCommand(source, "dross showcase boss_designs blightwood");
    }

    private static List<BlightCandidate> found(ServerLevel level, BlockPos column)
    {
        AABB area = new AABB(column.getX() - SEARCH_RADIUS, level.getMinBuildHeight(), column.getZ() - SEARCH_RADIUS,
                column.getX() + SEARCH_RADIUS, level.getMaxBuildHeight(), column.getZ() + SEARCH_RADIUS);
        return level.getEntities(ModEntities.BLIGHT_CANDIDATE.get(), area, candidate -> true);
    }

    private static void checkGallery(ServerLevel level, BlockPos column, int result, String when)
    {
        int count = BlightDesigns.ALL.size();
        if (result != count)
        {
            throw new GameTestAssertException(when + ": the command spawned " + result + " candidates, not " + count);
        }
        List<BlightCandidate> candidates = found(level, column);
        Set<Integer> numbers = new TreeSet<>();
        double floor = Double.NaN;
        for (BlightCandidate candidate : candidates)
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
