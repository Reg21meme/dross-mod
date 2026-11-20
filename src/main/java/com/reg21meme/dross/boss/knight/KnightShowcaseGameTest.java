package com.reg21meme.dross.boss.knight;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.command.KnightShowcase;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
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
 * Headless tests (GameTest) for the elemental knights ({@code .\gradlew.bat runGameTestServer}):
 * <ul>
 *   <li><b>Frozen exhibits</b> (in the Cinder Colossus tests' arena, {@link ColossusGameTest#ARENA}): every knight spawns
 *       with its design, its hitbox the design's measured size; an ordinary hit doesn't hurt it, a push doesn't move it,
 *       it has no AI, and it's still standing in the same spot a while later.</li>
 *   <li><b>The gallery</b> ({@code /dross showcase knights}, run far from everything else at {@link #GALLERY_X},
 *       {@link #GALLERY_Z}): every knight once, with the Thawed Soldiers (9C, 9D), all on one
 *       floor, each facing back along the way the command looked; a broken portal on every plot; running it again
 *       replaces them (still one of each).</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class KnightShowcaseGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Far more damage than a knight's health. */
    private static final float BIG_HIT = 1000.0F;
    /** How long the knights are watched after spawning (ticks). */
    private static final int WATCH_TICKS = 40;
    private static final double SIZE_TOLERANCE = 0.01D;
    /** Where the gallery test runs: far from spawn, the portal site and the other galleries' tests. */
    private static final int GALLERY_X = -2200;
    private static final int GALLERY_Z = -2200;
    /** The gallery's models are looked for this far round its spot (X and Z), at every height. */
    private static final int SEARCH_RADIUS = 320;
    /** The models the gallery puts in besides the knights: the two Thawed Soldiers. */
    private static final int OTHERS = 2;

    private KnightShowcaseGameTest() {}

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_knight_showcase",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 200)
    public static void knightsStayFrozen(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, KnightShowcaseGameTest::knightsStayFrozen))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<KnightCandidate> knights = new ArrayList<>();
        List<Vec3> spots = new ArrayList<>();
        for (KnightDesigns.Design design : KnightDesigns.ALL)
        {
            Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(ColossusGameTest.MIDDLE));
            KnightCandidate knight = KnightCandidate.spawnShowcase(level, feet, 0.0F, design);
            if (knight == null || knight.getDesign().number() != design.number())
            {
                throw new GameTestAssertException("couldn't spawn knight No. " + design.label());
            }
            AABB box = knight.getBoundingBox();
            if (Math.abs(box.getXsize() - design.width()) > SIZE_TOLERANCE
                    || Math.abs(box.getYsize() - design.height()) > SIZE_TOLERANCE)
            {
                throw new GameTestAssertException("No. " + design.label() + "'s hitbox is " + box.getXsize() + " x "
                        + box.getYsize() + ", not " + design.width() + " x " + design.height());
            }
            if (!knight.isNoAi() || knight.isPushable())
            {
                throw new GameTestAssertException("No. " + design.label() + " should have no AI and not be pushable");
            }
            float before = knight.getHealth();
            knight.hurt(level.damageSources().generic(), BIG_HIT);
            knight.push(2.0D, 0.0D, 2.0D);
            if (knight.isRemoved() || knight.getHealth() < before)
            {
                throw new GameTestAssertException("No. " + design.label() + " was hurt by an ordinary hit");
            }
            knights.add(knight);
            spots.add(feet);
        }
        helper.runAfterDelay(WATCH_TICKS, () -> {
            try
            {
                for (int i = 0; i < knights.size(); i++)
                {
                    KnightCandidate knight = knights.get(i);
                    Vec3 spot = spots.get(i);
                    if (!knight.isAlive() || Math.abs(knight.getX() - spot.x) > SIZE_TOLERANCE
                            || Math.abs(knight.getZ() - spot.z) > SIZE_TOLERANCE)
                    {
                        throw new GameTestAssertException("No. " + knight.getDesign().label() + " didn't stay where it "
                                + "was put (alive " + knight.isAlive() + ", at " + knight.position() + ")");
                    }
                }
                LOGGER.info("[knight test] {} knights spawned with their designs' hitboxes, shrugged off a big hit and "
                        + "a push, and stayed put", knights.size());
            }
            finally
            {
                knights.forEach(KnightCandidate::discard);
            }
            helper.succeed();
        });
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_knight_gallery", timeoutTicks = 400)
    public static void galleryHoldsEveryKnight(GameTestHelper helper)
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
            LOGGER.info("[knight test] The gallery holds all {} knights with the Thawed Soldiers, each "
                    + "once, on one floor and facing back towards the command, a broken portal on every plot; running "
                    + "it again replaced them", KnightDesigns.ALL.size());
        }
        finally
        {
            gallery(overworld, column).forEach(Entity::discard);
        }
        helper.succeed();
    }

    /** Runs {@code /dross showcase knights} at {@code column}, feet at {@code feetY}, looking south. */
    private static int runGallery(MinecraftServer server, ServerLevel level, BlockPos column, int feetY)
    {
        var source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                .withPosition(new Vec3(column.getX() + 0.5D, feetY, column.getZ() + 0.5D)).withRotation(new Vec2(0.0F, 0.0F))
                .withSuppressedOutput();
        return server.getCommands().performPrefixedCommand(source, "dross showcase boss_designs knights");
    }

    private static AABB area(ServerLevel level, BlockPos column)
    {
        return new AABB(column.getX() - SEARCH_RADIUS, level.getMinBuildHeight(), column.getZ() - SEARCH_RADIUS,
                column.getX() + SEARCH_RADIUS, level.getMaxBuildHeight(), column.getZ() + SEARCH_RADIUS);
    }

    private static List<Entity> gallery(ServerLevel level, BlockPos column)
    {
        AABB area = area(level, column);
        List<Entity> out = new ArrayList<>(level.getEntities(ModEntities.KNIGHT_CANDIDATE.get(), area, k -> true));
        out.addAll(level.getEntities(ModEntities.RIMEFALL_CANDIDATE.get(), area,
                c -> c.getTags().contains(KnightShowcase.GALLERY_TAG)));
        out.addAll(level.getEntities(ModEntities.BLIGHT_CANDIDATE.get(), area,
                c -> c.getTags().contains(KnightShowcase.GALLERY_TAG)));
        return out;
    }

    private static void checkGallery(ServerLevel level, BlockPos column, int result, String when)
    {
        int count = KnightDesigns.ALL.size() + OTHERS;
        if (result != count)
        {
            throw new GameTestAssertException(when + ": the command spawned " + result + " models, not " + count);
        }
        List<KnightCandidate> knights = level.getEntities(ModEntities.KNIGHT_CANDIDATE.get(), area(level, column),
                k -> true);
        List<Entity> all = gallery(level, column);
        Set<Integer> numbers = new TreeSet<>();
        for (KnightCandidate knight : knights)
        {
            numbers.add(knight.getDesign().number());
        }
        if (knights.size() != KnightDesigns.ALL.size() || numbers.size() != KnightDesigns.ALL.size()
                || all.size() != count)
        {
            throw new GameTestAssertException(when + ": found " + knights.size() + " knights with " + numbers.size()
                    + " different designs and " + (all.size() - knights.size()) + " others, not "
                    + KnightDesigns.ALL.size() + " of each and " + OTHERS);
        }
        double floor = all.get(0).getY();
        for (Entity entity : all)
        {
            if (Math.abs(entity.getY() - floor) > SIZE_TOLERANCE)
            {
                throw new GameTestAssertException(when + ": " + entity.getName().getString() + " stands at y "
                        + entity.getY() + ", not on the gallery's floor (y " + floor + ")");
            }
            // The command looked south (yaw 0), so every model faces north (yaw 180) towards it.
            if (Math.abs(Math.IEEEremainder(entity.getYRot() - 180.0F, 360.0D)) > 0.01D)
            {
                throw new GameTestAssertException(when + ": " + entity.getName().getString() + " faces "
                        + entity.getYRot() + " degrees, not back towards the command");
            }
        }
        // Every plot has its broken portal: frame blocks round each plot's back.
        int stations = KnightShowcase.stationCount();
        for (int i = 0; i < stations; i++)
        {
            double across = KnightShowcase.plotMiddle(i);
            int frames = 0;
            BlockPos base = new BlockPos(column.getX() - (int) Math.round(across),
                    (int) Math.floor(floor), column.getZ() + KnightShowcase.portalAhead());
            for (BlockPos pos : BlockPos.betweenClosed(base.offset(-5, 0, -3), base.offset(5, 7, 3)))
            {
                if (level.getBlockState(pos).is(ModBlocks.DROSS_PORTAL_FRAME.get()))
                {
                    frames++;
                }
            }
            if (frames < 10)
            {
                throw new GameTestAssertException(when + ": plot " + (i + 1) + " has " + frames + " portal frame blocks "
                        + "round " + base.toShortString() + " (its broken portal should have 17)");
            }
        }
    }
}
