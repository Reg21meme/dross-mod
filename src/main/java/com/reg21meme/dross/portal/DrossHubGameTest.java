package com.reg21meme.dross.portal;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.world.PortalSiteGameTest;
import com.reg21meme.dross.world.shrine.GroundFit;
import com.reg21meme.dross.world.shrine.ShrineDesign;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HugeMushroomBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for the hub at Dross 0,0 and for finding safe ground. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}. They only exist in the development environment.
 * <ul>
 *   <li>{@link #hubLayout}: the hub has a layout; its kind matches its exit portal (a 3x5 exit on the centre is the
 *       Shattered Spire, which a hub built in this run must be, on fitted ground; a 2x3 exit is the placeholder); its
 *       footprint is what the exit implies (the spire's: X -25..49, Z -34..38); the spire's arrival spot is on the step
 *       in front of the frame, facing the gate; the two rest spots are side by side, free to stand in, off the walkway out of the portal and
 *       not by the arrival spot (the spire's: (3, ground+8, 1) and (3, ground+8, 2)).</li>
 *   <li>{@link #arrivalThroughPortal}: an armor stand sent from the Overworld through {@link DrossTeleporter} lands on the
 *       layout's arrival spot (the spire's: the exit's corner + (1.5, 0, 1.5)), facing its way (south), standing on the
 *       step.</li>
 *   <li>{@link #gotoHubLandsOnTheStep}: {@code /dross goto hub}'s teleport puts a player on that same spot, facing the
 *       same way.</li>
 *   <li>{@link #groundAroundTheHub}: if the hub's ground was fitted, the ground round it is surveyed again: every dry
 *       column d blocks outside the footprint (d up to 15) is within d blocks of the hub's ground level, no two
 *       neighbouring dry columns within 16 blocks differ by 3 or more, and none within 4 by 2 or more. Trivial while the
 *       Dross is flat; the real check once it has terrain. A placeholder built next to something else (not fitted) is
 *       skipped, with a log line.</li>
 *   <li>{@link #whenBuiltOnceTheHubIs}: once the hub is built, {@link DrossHub#isBuilt} says so and
 *       {@link DrossHub#whenBuilt} runs straight away with the same layout (the hub's survivors spawn through it).</li>
 *   <li>{@link #safeLandingOnRoughGround}: on a platform built high in the Dross's sky far from the hub, with a tree, a
 *       block floating 20 blocks up, a column of lava and a giant blightcap, {@link DrossTeleporter#findSafeSpot} lands
 *       under the floating block on the platform, never in the trunk's, the lava's or the stem's column, never on leaves,
 *       logs, a mushroom cap or lava.</li>
 * </ul>
 * They use world-builder's empty template ({@link PortalSiteGameTest#EMPTY}) and their own batch.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class DrossHubGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "dross_hub";

    // The Shattered Spire, as built at the hub (read from its template, shattered_spire.nbt)
    /** The spire's frame opening is this many blocks above its ground level. */
    private static final int SPIRE_OPENING_ABOVE_GROUND = 9;
    /** Its footprint, seen from above, with its frame opening's middle column on 0, 0. */
    private static final int SPIRE_MIN_X = -25;
    private static final int SPIRE_MAX_X = 49;
    private static final int SPIRE_MIN_Z = -34;
    private static final int SPIRE_MAX_Z = 38;
    /** The arrival spot is the exit's lowest corner plus this: on the step in front of the frame. */
    private static final Vec3 SPIRE_ARRIVAL_OFFSET = new Vec3(1.5D, 0.0D, 1.5D);
    /** The rest spots: on the dais east of the step, (3, ground+8, 1) and (3, ground+8, 2). */
    private static final BlockPos[] SPIRE_REST_SPOTS = {new BlockPos(3, 8, 1), new BlockPos(3, 8, 2)};

    // The ground around the hub
    /** The ground is surveyed this far round the footprint. */
    private static final int GROUND_SURVEY_MARGIN = 18;
    /** Every dry column up to this far out is within as many blocks of the hub's ground level as it is far out. */
    private static final int STAIRCASE_REACH = DrossHub.HUB_BLEND_MARGIN - 1;
    /** No step of 3 or more between neighbouring dry columns up to this far out... */
    private static final int NO_CLIFF_REACH = DrossHub.HUB_BLEND_MARGIN;
    private static final int CLIFF_STEP = 3;
    /** ...and no step of 2 or more up to this far out. */
    private static final int GENTLE_REACH = 4;
    private static final int GENTLE_STEP = 2;

    // The rough ground for findSafeSpot: a platform high in the sky, far from the hub
    /** Where the platform's middle goes (X, Z in the Dross); then spares, in case the land is too high. */
    private static final int[][] LANDING_SPOTS = {{4000, 4000}, {-4000, 4000}, {4000, -4000}, {-4000, -4000}};
    /** The platform reaches this far from its middle (9x9). */
    private static final int PLATFORM_HALF = 4;
    /** It's this many blocks thick (its top block included). */
    private static final int PLATFORM_DEPTH = 4;
    /** Its top is at this height, high above any land (higher if the land there is). */
    private static final int PREFERRED_TOP = 270;
    /** And at least this many blocks above the land under it. */
    private static final int AIR_GAP = 6;
    /** A block floats this many blocks above the platform's middle. */
    private static final int FLOAT_HEIGHT = 20;
    /** A silverwood trunk this tall stands this far east of the middle, under a 3x3x2 canopy of leaves. */
    private static final int TRUNK_HEIGHT = 5;
    private static final int TREE_EAST = 2;
    /** A column of lava, 2 blocks (sunk into the platform's top and one above it), this far west of the middle. */
    private static final int LAVA_WEST = 2;
    /** A giant blightcap (a stem this tall under a 3x3 cap) stands this far south of the middle. */
    private static final int MUSHROOM_SOUTH = 3;
    private static final int MUSHROOM_STEM = 3;
    /** findSafeSpot looks this far round the tree's and the lava's columns (all of it on the platform). */
    private static final int LANDING_RADIUS = 2;

    private DrossHubGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The layout
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH)
    public static void hubLayout(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = drossOf(server);
        DrossHub.Layout layout = layoutOf(server);
        DrossHubData data = DrossHubData.get(server);
        PortalOpening exit = layout.exit();
        BoundingBox footprint = layout.footprint();
        boolean template = dross.getStructureManager().get(DrossHub.TEMPLATE).isPresent();
        List<String> problems = new ArrayList<>();

        // 1. The kind matches the exit portal.
        boolean spireExit = isSpireExit(exit);
        boolean placeholderExit = exit.width() == 2 && exit.height() == 3;
        if (template)
        {
            expect(problems, layout.kind() == DrossHub.Kind.TEMPLATE || layout.kind() == DrossHub.Kind.PLACEHOLDER,
                    "the user's hub template exists, but the hub is " + layout.kind());
        }
        else
        {
            DrossHub.Kind expected = spireExit ? DrossHub.Kind.SPIRE : placeholderExit ? DrossHub.Kind.PLACEHOLDER : null;
            if (expected == null)
            {
                problems.add("the exit portal (" + describe(exit) + ") is neither the spire's nor the placeholder's");
            }
            expect(problems, layout.kind() == expected, "the exit portal (" + describe(exit) + ") makes it " + expected + ", but its layout says "
                    + layout.kind());
            if (data.wasBuiltThisSession())
            {
                expect(problems, layout.kind() == DrossHub.Kind.SPIRE && layout.fitted(),
                        "the hub was built in this run, so it should be the Shattered Spire on fitted ground, not " + layout.kind()
                                + (layout.fitted() ? " (fitted)" : " (not fitted)"));
            }
        }

        // 2. The footprint is what the exit implies.
        if (layout.kind() == DrossHub.Kind.SPIRE)
        {
            int ground = exit.minCorner().getY() - SPIRE_OPENING_ABOVE_GROUND;
            expect(problems, layout.groundY() == ground, "the spire's ground level is " + layout.groundY() + ", but its exit implies " + ground);
            ShrineDesign spire = ShrineDesign.byId("shattered_spire").orElseThrow(() -> new GameTestAssertException("no Shattered Spire design"));
            BoundingBox expected = spire.footprintForFrameAt(DrossHub.CENTER.getX(), DrossHub.CENTER.getZ(), ground, Direction.SOUTH);
            expect(problems, footprint.equals(expected), "the spire's footprint is " + describe(footprint) + ", not " + describe(expected));
            expect(problems, footprint.minX() == SPIRE_MIN_X && footprint.maxX() == SPIRE_MAX_X && footprint.minZ() == SPIRE_MIN_Z
                    && footprint.maxZ() == SPIRE_MAX_Z, "the spire's footprint is " + describe(footprint) + ", not X " + SPIRE_MIN_X + ".."
                    + SPIRE_MAX_X + ", Z " + SPIRE_MIN_Z + ".." + SPIRE_MAX_Z);
            Vec3 arrival = Vec3.atLowerCornerOf(exit.minCorner()).add(SPIRE_ARRIVAL_OFFSET);
            expect(problems, layout.arrival().distanceTo(arrival) < 1.0E-6D && Math.abs(Mth.wrapDegrees(layout.arrivalYaw())) < 1.0E-3F,
                    "the spire's arrival spot is " + layout.arrival() + " facing " + layout.arrivalYaw() + ", not " + arrival + " facing south (0)");
            List<BlockPos> rest = new ArrayList<>();
            for (BlockPos spot : SPIRE_REST_SPOTS)
            {
                rest.add(spot.offset(0, ground, 0));
            }
            expect(problems, layout.restSpots().equals(rest), "the spire's rest spots are " + layout.restSpots() + ", not " + rest);
        }
        else if (layout.kind() == DrossHub.Kind.PLACEHOLDER)
        {
            BlockPos min = exit.minCorner();
            int cx = exit.axis() == Direction.Axis.X ? min.getX() + 1 : min.getX();
            int cz = exit.axis() == Direction.Axis.X ? min.getZ() : min.getZ() + 1;
            expect(problems, footprint.minX() <= cx - 5 && footprint.maxX() >= cx + 5 && footprint.minZ() <= cz - 5 && footprint.maxZ() >= cz + 5,
                    "the placeholder's footprint " + describe(footprint) + " doesn't hold its 11x11 platform round " + cx + ", " + cz);
        }
        else
        {
            expect(problems, footprint.isInside(exit.minCorner()), "the footprint " + describe(footprint) + " doesn't hold the exit portal at "
                    + exit.minCorner().toShortString());
        }

        // 3. The rest spots: two, side by side, free to stand in, off the walkway and away from the arrival spot.
        List<BlockPos> rest = layout.restSpots();
        if (rest.size() != 2)
        {
            problems.add("there are " + rest.size() + " rest spots, not 2");
        }
        else
        {
            BlockPos a = rest.get(0);
            BlockPos b = rest.get(1);
            expect(problems, a.getY() == b.getY() && Math.abs(a.getX() - b.getX()) + Math.abs(a.getZ() - b.getZ()) == 1,
                    "the rest spots " + a.toShortString() + " and " + b.toShortString() + " aren't side by side");
            BlockPos arrivalCell = BlockPos.containing(layout.arrival());
            for (BlockPos spot : rest)
            {
                String why = notStandable(dross, spot);
                if (why != null)
                {
                    problems.add("the rest spot " + spot.toShortString() + " isn't free to stand in: " + why);
                }
                expect(problems, !onWalkway(exit, spot), "the rest spot " + spot.toShortString() + " is on the walkway out of the portal");
                expect(problems, !inOpening(exit, spot), "the rest spot " + spot.toShortString() + " is in the portal");
                expect(problems, Math.abs(spot.getX() - arrivalCell.getX()) > 1 || Math.abs(spot.getZ() - arrivalCell.getZ()) > 1
                        || Math.abs(spot.getY() - arrivalCell.getY()) > 1, "the rest spot " + spot.toShortString() + " is by the arrival spot "
                        + arrivalCell.toShortString());
            }
        }

        fail(problems);
        LOGGER.info("[hub test] {}{}; the kind, footprint, arrival spot and rest spots are right", DrossHub.describe(layout),
                data.wasBuiltThisSession() ? " (built in this run)" : " (built before this run)");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Arriving
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH)
    public static void arrivalThroughPortal(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = drossOf(server);
        DrossHub.Layout layout = layoutOf(server);
        ArmorStand stand = helper.spawn(EntityType.ARMOR_STAND, BlockPos.ZERO);
        Entity arrived;
        try
        {
            arrived = stand.changeDimension(dross, new DrossTeleporter());
        }
        finally
        {
            if (!stand.isRemoved())
            {
                stand.discard();
            }
        }
        if (arrived == null)
        {
            throw new GameTestAssertException("the armor stand didn't go through to the Dross");
        }
        try
        {
            List<String> problems = new ArrayList<>();
            expect(problems, arrived.level() == dross, "the armor stand arrived in " + arrived.level().dimension().location() + ", not the Dross");
            checkArrival(problems, dross, layout, arrived.position(), arrived.getYRot(), "the armor stand");
            fail(problems);
            LOGGER.info("[hub test] An armor stand sent through a Dross portal landed at {} facing {}, on the layout's arrival spot",
                    arrived.position(), Direction.fromYRot(arrived.getYRot()).getName());
        }
        finally
        {
            arrived.discard();
        }
        helper.succeed();
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH)
    public static void gotoHubLandsOnTheStep(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = drossOf(server);
        DrossHub.Layout layout = layoutOf(server);
        TestPlayer player = TestPlayer.join(helper.getLevel(), "hub-test-player");
        try
        {
            if (!DrossHub.teleportToHub(player))
            {
                throw new GameTestAssertException("/dross goto hub's teleport failed (no Dross?)");
            }
            List<String> problems = new ArrayList<>();
            expect(problems, player.serverLevel() == dross, "the player went to " + player.serverLevel().dimension().location() + ", not the Dross");
            checkArrival(problems, dross, layout, player.position(), player.getYRot(), "/dross goto hub");
            fail(problems);
            LOGGER.info("[hub test] /dross goto hub put a player at {} facing {}, on the layout's arrival spot", player.position(),
                    Direction.fromYRot(player.getYRot()).getName());
        }
        finally
        {
            player.leave();
        }
        helper.succeed();
    }

    /** The spot is the layout's arrival spot, facing its way, standing on the block under it (the spire's: the step, facing south). */
    private static void checkArrival(List<String> problems, ServerLevel dross, DrossHub.Layout layout, Vec3 at, float yaw, String who)
    {
        expect(problems, at.distanceTo(layout.arrival()) < 1.0E-6D, who + " landed at " + at + ", not on the arrival spot " + layout.arrival());
        expect(problems, Math.abs(Mth.wrapDegrees(yaw - layout.arrivalYaw())) < 1.0E-3F, who + " faces " + yaw + ", not " + layout.arrivalYaw());
        if (layout.kind() == DrossHub.Kind.SPIRE)
        {
            Vec3 step = Vec3.atLowerCornerOf(layout.exit().minCorner()).add(SPIRE_ARRIVAL_OFFSET);
            expect(problems, at.distanceTo(step) < 1.0E-6D, who + " landed at " + at + ", not on the step in front of the frame " + step);
            expect(problems, Math.abs(Mth.wrapDegrees(yaw)) < 1.0E-3F, who + " faces " + yaw + ", not south (0), toward the gate");
        }
        BlockPos below = BlockPos.containing(at).below();
        BlockState ground = dross.getBlockState(below);
        VoxelShape shape = ground.getCollisionShape(dross, below);
        expect(problems, !shape.isEmpty() && Math.abs(below.getY() + shape.max(Direction.Axis.Y) - at.y) < 1.0E-6D,
                who + " isn't standing on " + name(ground) + " under it at " + below.toShortString());
    }

    // ---------------------------------------------------------------------------------------------
    // The ground round the hub
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH)
    public static void groundAroundTheHub(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = drossOf(server);
        DrossHub.Layout layout = layoutOf(server);
        if (!layout.fitted())
        {
            LOGGER.info("[hub test] groundAroundTheHub skipped: this world's hub ({}) wasn't built on fitted ground (a fallback built next to "
                    + "something else)", layout.kind());
            helper.succeed();
            return;
        }
        BoundingBox footprint = layout.footprint();
        int ground = layout.groundY();
        GroundFit.Survey survey = GroundFit.survey(dross, footprint, GROUND_SURVEY_MARGIN);
        GroundFit.Plan again = GroundFit.plan(survey, ground, DrossHub.HUB_BLEND_MARGIN, DrossHub.HUB_WATER_CLEARANCE);
        LOGGER.info("[hub test] The ground round the hub as it is now, planned again at its ground level (the \"natural ground under the "
                + "footprint\" numbers are the hub's own blocks; the staircase numbers are what count): {}", again.summary());

        List<String> problems = new ArrayList<>();
        int checked = 0;
        int wet = 0;
        int furthest = 0;
        int steepest = 0;
        for (int x = footprint.minX() - GROUND_SURVEY_MARGIN; x <= footprint.maxX() + GROUND_SURVEY_MARGIN; x++)
        {
            for (int z = footprint.minZ() - GROUND_SURVEY_MARGIN; z <= footprint.maxZ() + GROUND_SURVEY_MARGIN; z++)
            {
                int d = survey.distance(x, z);
                if (d == 0)
                {
                    continue; // the hub itself
                }
                if (survey.liquidAt(x, z))
                {
                    wet++;
                    continue;
                }
                checked++;
                int h = survey.surfaceAt(x, z);
                if (d <= STAIRCASE_REACH)
                {
                    furthest = Math.max(furthest, Math.abs(h - ground));
                    if (Math.abs(h - ground) > d)
                    {
                        problems.add("the column " + x + ", " + z + " (" + d + " out) is at y " + h + ", more than " + d + " from the ground level " + ground);
                    }
                }
                for (int[] step : new int[][] {{1, 0}, {0, 1}})
                {
                    int nx = x + step[0];
                    int nz = z + step[1];
                    if (!survey.contains(nx, nz) || survey.distance(nx, nz) == 0 || survey.liquidAt(nx, nz))
                    {
                        continue;
                    }
                    int near = Math.min(d, survey.distance(nx, nz));
                    int rise = Math.abs(h - survey.surfaceAt(nx, nz));
                    if (near <= NO_CLIFF_REACH)
                    {
                        steepest = Math.max(steepest, rise);
                    }
                    if ((near <= NO_CLIFF_REACH && rise >= CLIFF_STEP) || (near <= GENTLE_REACH && rise >= GENTLE_STEP))
                    {
                        problems.add("a step of " + rise + " between " + x + ", " + z + " and " + nx + ", " + nz + " (" + near + " out)");
                    }
                }
            }
        }
        fail(problems);
        LOGGER.info("[hub test] The ground round the hub ({} dry columns checked, {} water/lava): every column up to {} out is within its "
                        + "distance of the ground level {} (the most any is off: {}); the steepest step up to {} out is {} (overflow {})",
                checked, wet, STAIRCASE_REACH, ground, furthest, NO_CLIFF_REACH, steepest, again.overflow);
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Waiting for the hub
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH)
    public static void whenBuiltOnceTheHubIs(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        DrossHub.Layout layout = layoutOf(server); // finishes the hub at once if it's still being built
        List<String> problems = new ArrayList<>();
        expect(problems, DrossHub.isBuilt(server), "the hub has a layout, but DrossHub.isBuilt says it isn't built");
        List<Optional<DrossHub.Layout>> told = new ArrayList<>();
        DrossHub.whenBuilt(server, told::add);
        if (told.size() != 1 || told.get(0).isEmpty())
        {
            problems.add("DrossHub.whenBuilt didn't run straight away with the layout once the hub was built (it ran " + told.size() + " time(s))");
        }
        else
        {
            DrossHub.Layout given = told.get(0).get();
            expect(problems, given.kind() == layout.kind() && given.exit().equals(layout.exit()) && given.arrival().equals(layout.arrival())
                    && given.footprint().equals(layout.footprint()), "DrossHub.whenBuilt gave a different layout: " + DrossHub.describe(given));
        }
        fail(problems);
        LOGGER.info("[hub test] The hub is built, and DrossHub.whenBuilt runs straight away with its layout");
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Safe ground on rough ground
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH)
    public static void safeLandingOnRoughGround(GameTestHelper helper)
    {
        ServerLevel dross = drossOf(helper.getLevel().getServer());
        Platform p = findRoom(dross);
        try
        {
            p.build(dross);
            List<String> problems = new ArrayList<>();
            BlockState top = ModBlocks.SILVERMOSS.get().defaultBlockState();

            // 1. Under the floating block: on the platform in its own column (the floating block is no ground).
            Optional<Vec3> underFloat = DrossTeleporter.findSafeSpot(dross, null, p.x, p.z, 0);
            Vec3 expected = new Vec3(p.x + 0.5D, p.top + 1, p.z + 0.5D);
            expect(problems, underFloat.isPresent() && underFloat.get().distanceTo(expected) < 1.0E-6D,
                    "under the floating block, the spot is " + underFloat.map(Vec3::toString).orElse("none") + ", not " + expected + " on the platform");

            // 2. The tree: its own column has no safe spot (its trunk stands there); round it, on the platform, never on leaves or logs.
            int treeX = p.x + TREE_EAST;
            expect(problems, DrossTeleporter.findSafeSpot(dross, null, treeX, p.z, 0).isEmpty(), "found a spot in the trunk's own column");
            Optional<Vec3> byTree = DrossTeleporter.findSafeSpot(dross, null, treeX, p.z, LANDING_RADIUS);
            checkLanding(problems, dross, p, top, byTree, treeX, p.z, "by the tree");

            // 3. The lava: its own column has no safe spot; round it, on the platform, with no lava in or under the spot.
            int lavaX = p.x - LAVA_WEST;
            expect(problems, DrossTeleporter.findSafeSpot(dross, null, lavaX, p.z, 0).isEmpty(), "found a spot in the lava's own column");
            Optional<Vec3> byLava = DrossTeleporter.findSafeSpot(dross, null, lavaX, p.z, LANDING_RADIUS);
            checkLanding(problems, dross, p, top, byLava, lavaX, p.z, "by the lava");

            // 4. The giant blightcap: never on top of it; its stem's column has no safe spot; next to the stem, under the cap.
            int mushroomZ = p.z + MUSHROOM_SOUTH;
            expect(problems, DrossTeleporter.findSafeSpot(dross, null, p.x, mushroomZ, 0).isEmpty(), "found a spot in the blightcap's stem column");
            Optional<Vec3> byMushroom = DrossTeleporter.findSafeSpot(dross, null, p.x, mushroomZ, 1);
            checkLanding(problems, dross, p, top, byMushroom, p.x, mushroomZ, "under the blightcap");

            fail(problems);
            LOGGER.info("[hub test] Safe ground at {}, {} (platform top y {}): under the floating block {}, by the tree {}, by the lava {}, "
                            + "under the blightcap {}",
                    p.x, p.z, p.top, underFloat.orElse(null), byTree.orElse(null), byLava.orElse(null), byMushroom.orElse(null));
        }
        finally
        {
            p.clear(dross);
        }
        helper.succeed();
    }

    /**
     * The spot exists, isn't in the column {@code avoidX}/{@code avoidZ}, stands on the platform's top, and has no
     * leaves, logs, giant mushroom or lava in or under it.
     */
    private static void checkLanding(List<String> problems, ServerLevel dross, Platform p, BlockState top, Optional<Vec3> spot, int avoidX,
                                     int avoidZ, String where)
    {
        if (spot.isEmpty())
        {
            problems.add(where + ": no safe spot found");
            return;
        }
        BlockPos feet = BlockPos.containing(spot.get());
        expect(problems, !(feet.getX() == avoidX && feet.getZ() == avoidZ), where + ": the spot " + spot.get() + " is in that very column");
        expect(problems, feet.getY() == p.top + 1 && Math.abs(spot.get().y - (p.top + 1)) < 1.0E-6D,
                where + ": the spot " + spot.get() + " isn't on the platform's top (y " + (p.top + 1) + ")");
        BlockState under = dross.getBlockState(feet.below());
        expect(problems, under == top, where + ": the spot stands on " + name(under) + ", not the platform's " + name(top));
        for (BlockPos cell : List.of(feet.below(), feet, feet.above()))
        {
            BlockState state = dross.getBlockState(cell);
            expect(problems, !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS) && !(state.getBlock() instanceof HugeMushroomBlock)
                            && !state.getFluidState().is(FluidTags.LAVA),
                    where + ": " + name(state) + " at " + cell.toShortString() + ", in or under the spot");
        }
    }

    /** The test platform: its middle column, and the y of its top block. */
    private record Platform(int x, int z, int top)
    {
        void build(ServerLevel dross)
        {
            BlockState duskstone = ModBlocks.DUSKSTONE.get().defaultBlockState();
            BlockState silvermoss = ModBlocks.SILVERMOSS.get().defaultBlockState();
            for (int dx = -PLATFORM_HALF; dx <= PLATFORM_HALF; dx++)
            {
                for (int dz = -PLATFORM_HALF; dz <= PLATFORM_HALF; dz++)
                {
                    for (int y = top - PLATFORM_DEPTH + 1; y <= top; y++)
                    {
                        set(dross, new BlockPos(x + dx, y, z + dz), y == top ? silvermoss : duskstone);
                    }
                }
            }
            // A block floating high over the middle.
            set(dross, new BlockPos(x, top + FLOAT_HEIGHT, z), duskstone);
            // A silverwood tree: its trunk, and a 3x3x2 canopy round its top log.
            int treeX = x + TREE_EAST;
            for (int y = top + 1; y <= top + TRUNK_HEIGHT; y++)
            {
                set(dross, new BlockPos(treeX, y, z), ModBlocks.SILVERWOOD_LOG.get().defaultBlockState());
            }
            BlockState leaves = ModBlocks.SILVERWOOD_LEAVES.get().defaultBlockState();
            if (leaves.hasProperty(LeavesBlock.PERSISTENT))
            {
                leaves = leaves.setValue(LeavesBlock.PERSISTENT, true); // they mustn't decay while the test runs
            }
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    for (int dy = 0; dy <= 1; dy++)
                    {
                        if (dx != 0 || dz != 0 || dy != 0)
                        {
                            set(dross, new BlockPos(treeX + dx, top + TRUNK_HEIGHT + dy, z + dz), leaves);
                        }
                    }
                }
            }
            // A column of lava: sunk into the platform's top, and one block over it.
            BlockState lava = Blocks.LAVA.defaultBlockState();
            set(dross, new BlockPos(x - LAVA_WEST, top, z), lava);
            set(dross, new BlockPos(x - LAVA_WEST, top + 1, z), lava);
            // A giant blightcap: its stem, and a 3x3 cap on it.
            int mushroomZ = z + MUSHROOM_SOUTH;
            for (int y = top + 1; y <= top + MUSHROOM_STEM; y++)
            {
                set(dross, new BlockPos(x, y, mushroomZ), ModBlocks.BLIGHTCAP_STEM.get().defaultBlockState());
            }
            for (int dx = -1; dx <= 1; dx++)
            {
                for (int dz = -1; dz <= 1; dz++)
                {
                    set(dross, new BlockPos(x + dx, top + MUSHROOM_STEM + 1, mushroomZ + dz), ModBlocks.BLIGHTCAP_BLOCK.get().defaultBlockState());
                }
            }
        }

        /** Removes everything the test built, leaving air. */
        void clear(ServerLevel dross)
        {
            BlockState air = Blocks.AIR.defaultBlockState();
            for (BlockPos pos : BlockPos.betweenClosed(x - PLATFORM_HALF - 1, top - PLATFORM_DEPTH, z - PLATFORM_HALF - 1,
                    x + PLATFORM_HALF + 1, top + FLOAT_HEIGHT + 1, z + PLATFORM_HALF + 1))
            {
                if (!dross.getBlockState(pos).isAir())
                {
                    dross.setBlock(pos, air, Block.UPDATE_CLIENTS);
                }
            }
        }
    }

    /** The first spot whose land leaves room for the platform (and the block over it) in the sky; its chunks are loaded on the way. */
    private static Platform findRoom(ServerLevel dross)
    {
        StringBuilder tried = new StringBuilder();
        int reach = PLATFORM_HALF + 1;
        for (int[] spot : LANDING_SPOTS)
        {
            int land = dross.getMinBuildHeight();
            for (int x = spot[0] - reach; x <= spot[0] + reach; x++)
            {
                for (int z = spot[1] - reach; z <= spot[1] + reach; z++)
                {
                    dross.getChunk(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(z)); // loaded first
                    land = Math.max(land, dross.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1);
                }
            }
            int top = Math.max(PREFERRED_TOP, land + AIR_GAP + PLATFORM_DEPTH);
            if (top + FLOAT_HEIGHT + 2 < dross.getMaxBuildHeight())
            {
                return new Platform(spot[0], spot[1], top);
            }
            tried.append(" ").append(spot[0]).append(", ").append(spot[1]).append(" (land up to y ").append(land).append(")");
        }
        throw new GameTestAssertException("no room in the Dross's sky for the test platform at" + tried);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static ServerLevel drossOf(MinecraftServer server)
    {
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("the Dross dimension isn't loaded");
        }
        return dross;
    }

    private static DrossHub.Layout layoutOf(MinecraftServer server)
    {
        return DrossHub.layout(server).orElseThrow(() -> new GameTestAssertException("the hub has no layout (no exit portal?)"));
    }

    /** True if the exit is where the Shattered Spire's frame is: 3x5 along X, its middle column on the hub centre. */
    private static boolean isSpireExit(PortalOpening exit)
    {
        return exit.axis() == Direction.Axis.X && exit.width() == 3 && exit.height() == 5
                && exit.minCorner().getX() == DrossHub.CENTER.getX() - 1 && exit.minCorner().getZ() == DrossHub.CENTER.getZ();
    }

    /** True if the cell is on the walkway straight out in front of the opening (as wide as it, 7 blocks deep). */
    private static boolean onWalkway(PortalOpening exit, BlockPos cell)
    {
        boolean alongX = exit.axis() == Direction.Axis.X;
        int along = alongX ? cell.getX() : cell.getZ();
        int perp = alongX ? cell.getZ() : cell.getX();
        int minAlong = alongX ? exit.minCorner().getX() : exit.minCorner().getZ();
        int front = alongX ? exit.minCorner().getZ() : exit.minCorner().getX();
        return along >= minAlong && along < minAlong + exit.width() && perp >= front + 1 && perp <= front + DrossHub.WALKWAY_DEPTH;
    }

    /** True if a body standing in the cell (2 blocks tall) would reach into the opening. */
    private static boolean inOpening(PortalOpening exit, BlockPos cell)
    {
        boolean alongX = exit.axis() == Direction.Axis.X;
        int along = alongX ? cell.getX() : cell.getZ();
        int perp = alongX ? cell.getZ() : cell.getX();
        int minAlong = alongX ? exit.minCorner().getX() : exit.minCorner().getZ();
        int plane = alongX ? exit.minCorner().getZ() : exit.minCorner().getX();
        int minY = exit.minCorner().getY();
        return perp == plane && along >= minAlong && along < minAlong + exit.width()
                && cell.getY() + 1 >= minY && cell.getY() < minY + exit.height();
    }

    /** Why a villager-sized body can't stand in this cell, or null if it can. */
    @Nullable
    private static String notStandable(ServerLevel dross, BlockPos feet)
    {
        for (BlockPos cell : List.of(feet, feet.above()))
        {
            BlockState state = dross.getBlockState(cell);
            if (!state.getCollisionShape(dross, cell).isEmpty())
            {
                return name(state) + " at " + cell.toShortString();
            }
            if (!state.getFluidState().isEmpty())
            {
                return "liquid at " + cell.toShortString();
            }
            if (state.is(ModBlocks.DROSS_PORTAL.get()) || state.is(BlockTags.PORTALS))
            {
                return "a portal at " + cell.toShortString();
            }
        }
        BlockPos belowPos = feet.below();
        BlockState below = dross.getBlockState(belowPos);
        VoxelShape shape = below.getCollisionShape(dross, belowPos);
        if (shape.isEmpty() || shape.max(Direction.Axis.Y) < 1.0D)
        {
            return "nothing full to stand on (" + name(below) + ")";
        }
        if (below.is(BlockTags.LEAVES))
        {
            return "it stands on leaves";
        }
        return null;
    }

    private static void set(ServerLevel dross, BlockPos pos, BlockState state)
    {
        dross.setBlock(pos, state, Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
    }

    private static void expect(List<String> problems, boolean ok, String problem)
    {
        if (!ok)
        {
            problems.add(problem);
        }
    }

    private static void fail(List<String> problems)
    {
        if (!problems.isEmpty())
        {
            int shown = Math.min(problems.size(), 12);
            throw new GameTestAssertException(problems.size() + " problem(s), the first " + shown + ": " + String.join("; ", problems.subList(0, shown)));
        }
    }

    private static String describe(PortalOpening exit)
    {
        return exit.minCorner().toShortString() + " along " + exit.axis() + ", " + exit.width() + "x" + exit.height();
    }

    private static String describe(BoundingBox box)
    {
        return "X " + box.minX() + ".." + box.maxX() + ", Y " + box.minY() + ".." + box.maxY() + ", Z " + box.minZ() + ".." + box.maxZ();
    }

    private static String name(BlockState state)
    {
        return String.valueOf(ForgeRegistries.BLOCKS.getKey(state.getBlock()));
    }

    // ---------------------------------------------------------------------------------------------
    // The stand-in player (like world-builder's site test: an in-memory network channel, because vanilla's
    // makeMockServerPlayerInLevel crashes under Forge)
    // ---------------------------------------------------------------------------------------------

    private static final class TestPlayer extends ServerPlayer
    {
        private TestPlayer(ServerLevel level, String name)
        {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        }

        static TestPlayer join(ServerLevel level, String name)
        {
            TestPlayer player = new TestPlayer(level, name);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            return player;
        }

        /** Logs him out. Safe to call twice. */
        void leave()
        {
            if (server.getPlayerList().getPlayer(getUUID()) == this)
            {
                server.getPlayerList().remove(this);
            }
        }
    }
}
