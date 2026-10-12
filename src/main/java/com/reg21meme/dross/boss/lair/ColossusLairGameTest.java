package com.reg21meme.dross.boss.lair;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.boss.golem.StoneGolem;
import com.reg21meme.dross.boss.golem.WildGolemSpawns;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.LiquidBlockContainer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import static com.reg21meme.dross.boss.lair.LairTuning.*;

/**
 * Headless checks (GameTests) of the Cinder Colossus's lair. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}. They wait in real time for real-time work (the background
 * search, chunk loading, the lair being built), because the test server runs ticks as fast as it can.
 * <ul>
 *   <li>{@link #siteRuleForFixedSeeds}: for a few fixed seeds, from the noise alone: three directions 120 degrees apart
 *       each find a spot, every spot's whole footprint is Cinder Wastes, each lies in its direction's cone and far from
 *       the hub, and the one used is the furthest of the three.</li>
 *   <li>{@link #lairIsBuiltAndShapedRight}: the test world's real lair gets built (waits for it): a round floor 41 blocks
 *       across, a dome at least 22 blocks above the floor round the middle and room for the Colossus everywhere it can
 *       stand, a skylight open to the sky, a tunnel walkable from its mouth on the surface to the floor (no step over
 *       0.6 blocks, room for a player all the way), no lava that flows or can flow, the dome closed except for the
 *       skylight and the tunnel, and no natural spawning inside (where the golems' own rules would allow it).</li>
 *   <li>{@link #colossusRisesAndRespawns}: a stand-in player stepping onto the floor makes the Colossus rise in the middle;
 *       once it's dead it doesn't rise again before the respawn time, and does once the saved time is moved back past
 *       10 minutes. Cleans up after itself (no Colossus left, the lair's boss data reset).</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class ColossusLairGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The fixed seeds of the site check. */
    private static final long[] SEEDS = {1L, 4242L, 20261012L};
    /** The site check's searches get at most this long (real time). */
    private static final long SEARCH_TIMEOUT_MILLIS = 5 * 60 * 1000L;
    /** The lair is built in the background a little after the world starts: wait at most this long (real time). */
    private static final long PLACEMENT_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    /** The Colossus must rise within this long of the stand-in stepping in (real time; loading the chunks takes a moment). */
    private static final long RISE_TIMEOUT_MILLIS = 2 * 60 * 1000L;
    /** The tick limits are only a backstop behind the real-time limits (the test server can run hundreds of ticks a second). */
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** Ticks waited to see that nothing rises (several of the lair's checks). */
    private static final int QUIET_TICKS = 6 * CHECK_INTERVAL_TICKS;
    /** Game time still left on the respawn wait in the "not yet" check (20 seconds). */
    private static final int NOT_YET_TICKS = 400;
    /**
     * Once the respawn time is up, the next Colossus must rise within this many ticks of game time (the lair is checked
     * every {@link LairTuning#CHECK_INTERVAL_TICKS}).
     */
    private static final int PROMPT_TICKS = 100;
    /** A spot's footprint is checked this far out (the lair reaches about 45 blocks from its middle)... */
    private static final int FOOTPRINT_RADIUS = 48;
    /** ...every this many blocks. */
    private static final int FOOTPRINT_STEP = 4;
    /** A spot may lie this many degrees outside its cone (it's nudged a little after the search). */
    private static final double CONE_SLACK_DEGREES = 5.0D;
    /** "Round the middle": the dome must be at least this many blocks above the floor between the skylight and this radius. */
    private static final double MIDDLE_RING = 8.0D;
    private static final int MIDDLE_CLEARANCE = 22;
    /** The Colossus is 5 blocks tall: everywhere on the floor needs at least this much room. */
    private static final int BOSS_CLEARANCE = 6;
    /** A player steps up at most this much without jumping. */
    private static final double PLAYER_STEP = 0.6D;
    /** The flood fill of the dome's air stops after this many blocks (it's about 30,000). */
    private static final int FLOOD_LIMIT = 400_000;
    /** The stand-in player stands this far from the middle of the floor. */
    private static final int PLAYER_OFFSET = 9;
    /** The Colossus must rise this close to the middle (horizontally). */
    private static final double RISE_TOLERANCE = 1.0D;

    private ColossusLairGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The site rule, from the noise
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_lair_site_rule", timeoutTicks = BACKSTOP_TICKS)
    public static void siteRuleForFixedSeeds(GameTestHelper helper)
    {
        ServerLevel dross = drossOf(helper);
        List<LairSearch.Context> contexts = new ArrayList<>();
        List<CompletableFuture<LairSearch.Result>> runs = new ArrayList<>();
        for (long seed : SEEDS)
        {
            LairSearch.Context context = LairSearch.forSeed(dross, seed);
            contexts.add(context);
            runs.add(CompletableFuture.supplyAsync(() -> LairSearch.run(context, () -> false)));
        }
        long deadline = Util.getMillis() + SEARCH_TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!runs.stream().allMatch(CompletableFuture::isDone) && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("still searching");
                    }
                })
                .thenExecute(() -> {
                    List<String> problems = new ArrayList<>();
                    for (int i = 0; i < SEEDS.length; i++)
                    {
                        if (!runs.get(i).isDone())
                        {
                            problems.add("seed " + SEEDS[i] + ": the search took more than " + SEARCH_TIMEOUT_MILLIS / 1000 + " s");
                            continue;
                        }
                        checkSearch(SEEDS[i], contexts.get(i), runs.get(i).join(), problems);
                    }
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the lair's site rule isn't kept: " + String.join("; ", problems));
                    }
                })
                .thenSucceed();
    }

    private static void checkSearch(long seed, LairSearch.Context context, LairSearch.Result result, List<String> problems)
    {
        String name = "seed " + seed;
        double rotation = result.rotation();
        if (rotation < 0.0D || rotation >= DIRECTION_SPACING_DEGREES)
        {
            problems.add(name + ": the seed's turn " + rotation + " isn't between 0 and " + DIRECTION_SPACING_DEGREES);
        }
        if (result.directions().size() != DIRECTIONS)
        {
            problems.add(name + ": " + result.directions().size() + " directions searched, not " + DIRECTIONS);
            return;
        }
        double[] angles = LairSearch.directionAngles(seed);
        double furthest = -1.0D;
        StringBuilder log = new StringBuilder();
        for (int k = 0; k < DIRECTIONS; k++)
        {
            Optional<LairSearch.Candidate> found = result.directions().get(k);
            if (found.isEmpty())
            {
                problems.add(name + ": direction " + (k + 1) + " found nothing within " + MAX_DISTANCE + " blocks");
                continue;
            }
            LairSearch.Candidate c = found.get();
            furthest = Math.max(furthest, c.distance());
            log.append(String.format("%n  direction %d (%.0f degrees): %s", k + 1, LairSearch.normalise(angles[k]), c.describe()));
            if (c.distance() < MIN_DISTANCE)
            {
                problems.add(name + ", direction " + (k + 1) + ": " + Math.round(c.distance()) + " blocks from the hub, under " + MIN_DISTANCE);
            }
            double angle = Math.toDegrees(Math.atan2(c.z() - context.hubZ(), c.x() - context.hubX()));
            double off = Math.abs(Math.IEEEremainder(angle - angles[k], 360.0D));
            if (off > CONE_HALF_ANGLE_DEGREES + CONE_SLACK_DEGREES)
            {
                problems.add(name + ", direction " + (k + 1) + ": the spot is " + Math.round(off) + " degrees off its direction");
            }
            int outside = 0;
            for (int dx = -FOOTPRINT_RADIUS; dx <= FOOTPRINT_RADIUS; dx += FOOTPRINT_STEP)
            {
                for (int dz = -FOOTPRINT_RADIUS; dz <= FOOTPRINT_RADIUS; dz += FOOTPRINT_STEP)
                {
                    if (dx * dx + dz * dz <= FOOTPRINT_RADIUS * FOOTPRINT_RADIUS && !context.isCinder(c.x() + dx, c.z() + dz))
                    {
                        outside++;
                    }
                }
            }
            if (outside > 0)
            {
                problems.add(name + ", direction " + (k + 1) + ": " + outside + " sample(s) of the footprint round " + c.x() + " "
                        + c.z() + " aren't Cinder Wastes");
            }
            LairShape.Plan plan = c.plan();
            if (plan.descent() < MIN_DESCENT || plan.descent() > MAX_DESCENT)
            {
                problems.add(name + ", direction " + (k + 1) + ": the tunnel goes down " + plan.descent() + " blocks");
            }
        }
        if (result.pick().isEmpty())
        {
            problems.add(name + ": nothing was picked");
        }
        else if (result.pick().get().distance() < furthest - 1.0E-6D)
        {
            problems.add(name + ": the pick (" + Math.round(result.pick().get().distance()) + " blocks) isn't the furthest ("
                    + Math.round(furthest) + ")");
        }
        LOGGER.info("[lair test] {}: turned {} degrees, search {} ms{}{}", name, Math.round(rotation), result.millis(), log,
                result.pick().map(p -> String.format("%n  picked direction %d, %d blocks out", p.direction() + 1, Math.round(p.distance())))
                        .orElse(""));
    }

    // ---------------------------------------------------------------------------------------------
    // The real lair's shape
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_lair_shape", timeoutTicks = BACKSTOP_TICKS)
    public static void lairIsBuiltAndShapedRight(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        long deadline = Util.getMillis() + PLACEMENT_TIMEOUT_MILLIS;
        helper.startSequence()
                .thenWaitUntil(() -> waitForLair(server, deadline))
                .thenExecute(() -> {
                    ColossusLairData data = requireLair(server);
                    ServerLevel dross = drossOf(helper);
                    Shape shape = new Shape(dross, data);
                    List<String> problems = new ArrayList<>();
                    shape.checkFloor(problems);
                    shape.checkCeiling(problems);
                    shape.checkSkylight(problems);
                    shape.checkTunnel(problems);
                    shape.checkLava(problems);
                    shape.checkEnclosed(problems);
                    shape.checkNoSpawns(problems);
                    LOGGER.info("[lair test] The lair, cut along its tunnel (left: the far wall, right: the tunnel's mouth) and seen "
                            + "from above at 2 blocks over the floor (# rock, = nether bricks, _ slab, * shroomlight, m magma, ~ lava, "
                            + "| wall, L lantern, : ash, n natural ground, o other):{}{}", shape.drawCut(), shape.drawPlan());
                    LOGGER.info("[lair test] The lair at {} {} (floor y {}, tunnel to the {}, down {}) was built in {} ms; {}",
                            data.centerX(), data.centerZ(), data.floorY(), data.tunnel().getName(), data.descent(), data.buildMillis(),
                            problems.isEmpty() ? "every shape check passed" : problems.size() + " problem(s)");
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the lair isn't right: " + String.join("; ", problems));
                    }
                })
                .thenSucceed();
    }

    /** Reads the built lair back from the world. */
    private static final class Shape
    {
        final ServerLevel level;
        final int cx;
        final int cz;
        final int floor;
        final Direction dir;
        final Direction side;
        final int descent;

        Shape(ServerLevel level, ColossusLairData data)
        {
            this.level = level;
            this.cx = data.centerX();
            this.cz = data.centerZ();
            this.floor = data.floorY();
            this.dir = data.tunnel();
            this.side = dir.getClockWise();
            this.descent = data.descent();
        }

        BlockState at(int x, int y, int z)
        {
            return level.getBlockState(new BlockPos(x, y, z));
        }

        boolean inTunnelBand(int dx, int dz)
        {
            int s = dx * dir.getStepX() + dz * dir.getStepZ();
            int l = dx * side.getStepX() + dz * side.getStepZ();
            return s > FLOOR_RADIUS && Math.abs(l) <= TUNNEL_HALF_WIDTH + 1;
        }

        /** A side view along the tunnel's line through the middle: rows from the top down. */
        String drawCut()
        {
            StringBuilder out = new StringBuilder();
            int to = LairShape.entranceDistance(descent) + 4;
            for (int y = floor + DOME_HEIGHT + 16; y >= floor - UNDER_FLOOR - 2; y--)
            {
                out.append(String.format("%n  %4d ", y));
                for (int s = -(FLOOR_RADIUS + 8); s <= to; s++)
                {
                    out.append(glyph(at(cx + dir.getStepX() * s, y, cz + dir.getStepZ() * s)));
                }
            }
            return out.toString();
        }

        /** A view from above, 2 blocks over the floor. */
        String drawPlan()
        {
            StringBuilder out = new StringBuilder();
            int reach = LairShape.entranceDistance(descent) + 2;
            for (int dz = -reach; dz <= reach; dz++)
            {
                out.append(String.format("%n  "));
                for (int dx = -reach; dx <= reach; dx++)
                {
                    out.append(glyph(at(cx + dx, floor + 2, cz + dz)));
                }
            }
            return out.toString();
        }

        static char glyph(BlockState state)
        {
            if (state.isAir())
            {
                return '.';
            }
            if (state.getFluidState().is(Fluids.LAVA))
            {
                return '~';
            }
            if (LairBuilder.isLairRock(state) || state.is(Blocks.POLISHED_BASALT) || state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                    || state.is(Blocks.CHISELED_POLISHED_BLACKSTONE))
            {
                return '#';
            }
            if (state.is(Blocks.NETHER_BRICK_SLAB))
            {
                return '_';
            }
            if (state.is(Blocks.NETHER_BRICKS) || state.is(Blocks.RED_NETHER_BRICKS) || state.is(Blocks.CRACKED_NETHER_BRICKS)
                    || state.is(Blocks.CHISELED_NETHER_BRICKS))
            {
                return '=';
            }
            if (state.is(Blocks.SHROOMLIGHT))
            {
                return '*';
            }
            if (state.is(Blocks.MAGMA_BLOCK))
            {
                return 'm';
            }
            if (state.is(BlockTags.WALLS))
            {
                return '|';
            }
            if (state.is(Blocks.LANTERN))
            {
                return 'L';
            }
            if (state.is(com.reg21meme.dross.registry.ModBlocks.ASH.get()))
            {
                return ':';
            }
            return state.isSolid() ? 'n' : 'o';
        }

        /** Every floor column is solid underfoot with room above; the cavern ends at the floor's edge all round. */
        void checkFloor(List<String> problems)
        {
            int bad = 0;
            int columns = 0;
            for (int dx = -FLOOR_RADIUS; dx <= FLOOR_RADIUS; dx++)
            {
                for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz++)
                {
                    if (dx * dx + dz * dz > LairShape.FLOOR_EDGE * LairShape.FLOOR_EDGE)
                    {
                        continue;
                    }
                    columns++;
                    BlockPos under = new BlockPos(cx + dx, floor, cz + dz);
                    BlockState above = at(cx + dx, floor + 1, cz + dz);
                    boolean rim = above.is(BlockTags.WALLS);
                    if (!level.getBlockState(under).isFaceSturdy(level, under, Direction.UP) || !(above.isAir() || rim)
                            || !at(cx + dx, floor + 2, cz + dz).isAir())
                    {
                        bad++;
                    }
                }
            }
            if (bad > 0)
            {
                problems.add(bad + " of the floor's " + columns + " columns aren't solid floor with room above");
            }
            // The wall: every 15 degrees (away from the tunnel), the floor's air ends 20.5 to 22.5 blocks out.
            double tunnelAngle = Math.atan2(dir.getStepZ(), dir.getStepX());
            List<String> walls = new ArrayList<>();
            for (int k = 0; k < 24; k++)
            {
                double a = Math.toRadians(k * 15.0D);
                if (Math.abs(Math.IEEEremainder(a - tunnelAngle, 2.0D * Math.PI)) < Math.toRadians(10.0D))
                {
                    continue;
                }
                double reach = -1.0D;
                for (double r = 0.0D; r <= FLOOR_RADIUS + 6; r += 0.5D)
                {
                    int x = cx + (int) Math.round(r * Math.cos(a));
                    int z = cz + (int) Math.round(r * Math.sin(a));
                    if (!at(x, floor + 3, z).isAir())
                    {
                        reach = Math.sqrt(Math.pow(x - cx, 2) + Math.pow(z - cz, 2));
                        break;
                    }
                }
                // (A lava basin's niche, 1 block deep, can put the wall up to about 2.5 blocks past the floor's edge.)
                if (reach < LairShape.FLOOR_EDGE - 0.01D || reach > LairShape.FLOOR_EDGE + 2.6D)
                {
                    walls.add(k * 15 + " degrees: " + (reach < 0 ? "no wall" : String.format("wall at %.1f", reach)));
                }
            }
            if (!walls.isEmpty())
            {
                problems.add("the cavern isn't round (41 across): " + String.join(", ", walls));
            }
        }

        /** Room above the floor: 22+ blocks round the middle, room for the Colossus everywhere. */
        void checkCeiling(List<String> problems)
        {
            int lowMiddle = 0;
            int lowAnywhere = 0;
            int least = Integer.MAX_VALUE;
            for (int dx = -FLOOR_RADIUS; dx <= FLOOR_RADIUS; dx++)
            {
                for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r > LairShape.FLOOR_EDGE || r <= SKYLIGHT_RADIUS)
                    {
                        continue;
                    }
                    int from = at(cx + dx, floor + 1, cz + dz).is(BlockTags.WALLS) ? 2 : 1;
                    int room = 0;
                    while (room < DOME_HEIGHT + 4 && at(cx + dx, floor + from + room, cz + dz).isAir())
                    {
                        room++;
                    }
                    least = Math.min(least, room);
                    if (r <= MIDDLE_RING && room < MIDDLE_CLEARANCE)
                    {
                        lowMiddle++;
                    }
                    if (room < BOSS_CLEARANCE)
                    {
                        lowAnywhere++;
                    }
                }
            }
            if (lowMiddle > 0)
            {
                problems.add(lowMiddle + " column(s) round the middle have under " + MIDDLE_CLEARANCE + " blocks of room");
            }
            if (lowAnywhere > 0)
            {
                problems.add(lowAnywhere + " floor column(s) have under " + BOSS_CLEARANCE + " blocks of room (lowest " + least + ")");
            }
        }

        /** The skylight's columns have nothing above the floor at all: open to the sky. */
        void checkSkylight(List<String> problems)
        {
            int covered = 0;
            for (int dx = -5; dx <= 5; dx++)
            {
                for (int dz = -5; dz <= 5; dz++)
                {
                    if (Math.sqrt(dx * dx + dz * dz) > SKYLIGHT_RADIUS - 0.5D)
                    {
                        continue;
                    }
                    if (level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + dx, cz + dz) != floor + 1)
                    {
                        covered++;
                    }
                }
            }
            if (covered > 0)
            {
                problems.add(covered + " skylight column(s) aren't open to the sky");
            }
        }

        /** Walks the tunnel from the entrance on the surface down to the floor: no step too high, room all the way. */
        void checkTunnel(List<String> problems)
        {
            int start = LairShape.entranceDistance(descent);
            int x0 = cx + dir.getStepX() * start;
            int z0 = cz + dir.getStepZ() * start;
            if (!level.canSeeSky(new BlockPos(x0, floor + descent + 1, z0)))
            {
                problems.add("the tunnel's entrance at " + x0 + " " + (floor + descent + 1) + " " + z0 + " isn't open to the sky");
            }
            double previous = surface(x0, z0, floor + descent + 1);
            if (Math.abs(previous - (floor + descent + 1)) > 1.0E-6D)
            {
                problems.add("the entrance's ground is at " + previous + ", not " + (floor + descent + 1));
            }
            for (int s = start - 1; s >= FLOOR_RADIUS - 2; s--)
            {
                int x = cx + dir.getStepX() * s;
                int z = cz + dir.getStepZ() * s;
                double here = surface(x, z, (int) Math.floor(previous));
                if (Double.isNaN(here))
                {
                    problems.add("no ground in the tunnel at " + x + " " + z);
                    return;
                }
                if (Math.abs(here - previous) > PLAYER_STEP)
                {
                    problems.add(String.format("a step of %.1f blocks in the tunnel at %d %d", here - previous, x, z));
                    return;
                }
                AABB body = new AABB(x + 0.2D, here + 0.01D, z + 0.2D, x + 0.8D, here + 1.8D, z + 0.8D);
                if (!level.noCollision(body) || level.containsAnyLiquid(body))
                {
                    problems.add("no room for a player in the tunnel at " + x + " " + here + " " + z);
                    return;
                }
                previous = here;
            }
            if (Math.abs(previous - (floor + 1)) > 1.0E-6D)
            {
                problems.add("the tunnel ends at " + previous + ", not on the floor (" + (floor + 1) + ")");
            }
        }

        /** The top of whatever a player would stand on in a column, looking down from just above {@code around}. */
        double surface(int x, int z, int around)
        {
            for (int y = around + 1; y >= around - 3; y--)
            {
                BlockPos p = new BlockPos(x, y, z);
                VoxelShape shape = level.getBlockState(p).getCollisionShape(level, p);
                if (!shape.isEmpty())
                {
                    return y + shape.max(Direction.Axis.Y);
                }
            }
            return Double.NaN;
        }

        /**
         * True for a block of the lair's own space and lining (the cavern and its shell, the skylight and its lining, the
         * tunnel and the approach with their walls), where the build controls every fluid. Natural lava pools further out
         * are left as the world made them.
         */
        boolean inLairSpace(int dx, int y, int dz)
        {
            int dy = y - floor;
            double r = Math.sqrt(dx * dx + dz * dz);
            if (r <= LairShape.SHELL_EDGE + 0.5D && dy >= -UNDER_FLOOR && dy <= DOME_HEIGHT + SHELL_THICKNESS)
            {
                return true;
            }
            if (r <= SKYLIGHT_RADIUS + SKYLIGHT_LINING && dy > 0)
            {
                return y <= level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + dx, cz + dz);
            }
            return inTunnelBand(dx, dz) && dy >= -UNDER_FLOOR && dy <= descent + TUNNEL_HEIGHT + 2
                    && dx * dir.getStepX() + dz * dir.getStepZ() <= LairShape.entranceDistance(descent);
        }

        /** No flowing lava in the lair, and no lava that could flow (every lava block hemmed in sideways and below). */
        void checkLava(List<String> problems)
        {
            int reach = LairShape.entranceDistance(MAX_DESCENT) + 3;
            int top = Math.max(DOME_HEIGHT + SHELL_THICKNESS, MOUND_HEIGHT + 4);
            int flowing = 0;
            int loose = 0;
            int sources = 0;
            String example = null;
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    for (int y = floor - UNDER_FLOOR; y <= floor + top + 40; y++)
                    {
                        if (!inLairSpace(dx, y, dz))
                        {
                            continue;
                        }
                        BlockPos p = new BlockPos(cx + dx, y, cz + dz);
                        FluidState fluid = level.getFluidState(p);
                        if (fluid.isEmpty())
                        {
                            continue;
                        }
                        if (!fluid.isSource())
                        {
                            flowing++;
                            example = example == null ? "flowing fluid at " + p.toShortString() : example;
                            continue;
                        }
                        if (!fluid.is(Fluids.LAVA))
                        {
                            continue;
                        }
                        sources++;
                        for (Direction d : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST})
                        {
                            BlockPos next = p.relative(d);
                            BlockState state = level.getBlockState(next);
                            if (level.getFluidState(next).is(Fluids.LAVA))
                            {
                                continue;
                            }
                            if (lavaCouldEnter(next, state))
                            {
                                loose++;
                                example = example == null ? "lava at " + p.toShortString() + " could flow to " + next.toShortString() : example;
                            }
                        }
                    }
                }
            }
            if (flowing > 0 || loose > 0)
            {
                problems.add(flowing + " flowing fluid block(s) and " + loose + " way(s) for lava to flow (e.g. " + example + ")");
            }
            if (sources == 0)
            {
                problems.add("no lava basins (no lava at all)");
            }
        }

        /** Minecraft's own rule for whether lava can flow into a block (FlowingFluid.canHoldFluid). */
        boolean lavaCouldEnter(BlockPos pos, BlockState state)
        {
            Block block = state.getBlock();
            if (block instanceof LiquidBlockContainer container)
            {
                return container.canPlaceLiquid(level, pos, state, Fluids.LAVA);
            }
            if (block instanceof DoorBlock || state.is(BlockTags.SIGNS) || state.is(Blocks.LADDER) || state.is(Blocks.SUGAR_CANE)
                    || state.is(Blocks.BUBBLE_COLUMN) || state.is(Blocks.NETHER_PORTAL) || state.is(Blocks.END_PORTAL)
                    || state.is(Blocks.END_GATEWAY) || state.is(Blocks.STRUCTURE_VOID))
            {
                return false;
            }
            @SuppressWarnings("deprecation")
            boolean blocks = state.blocksMotion();
            return !blocks;
        }

        /**
         * The dome is closed: a flood fill of the open space from the middle of the floor never gets out of the rock
         * lining except up the skylight or out along the tunnel.
         */
        void checkEnclosed(List<String> problems)
        {
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            LongOpenHashSet seen = new LongOpenHashSet();
            BlockPos start = new BlockPos(cx, floor + 1, cz);
            queue.add(start);
            seen.add(start.asLong());
            int count = 0;
            boolean skylight = false;
            boolean tunnel = false;
            BlockPos leak = null;
            while (!queue.isEmpty() && count < FLOOD_LIMIT && leak == null)
            {
                BlockPos p = queue.poll();
                count++;
                int dx = p.getX() - cx;
                int dz = p.getZ() - cz;
                int dy = p.getY() - floor;
                double r = Math.sqrt(dx * dx + dz * dz);
                int s = dx * dir.getStepX() + dz * dir.getStepZ();
                int l = dx * side.getStepX() + dz * side.getStepZ();
                if (r <= SKYLIGHT_RADIUS + 0.5D && dy > DOME_HEIGHT)
                {
                    skylight = true;
                    continue; // up the skylight's shaft (lined with rock) to the sky
                }
                boolean inTunnel = s > FLOOR_RADIUS && Math.abs(l) <= TUNNEL_HALF_WIDTH && dy >= 0 && dy <= descent + TUNNEL_HEIGHT + 1;
                if (inTunnel && s >= LairShape.SHELL_EDGE + 1.0D)
                {
                    tunnel = true;
                    continue; // out along the tunnel (lined with rock) to its mouth
                }
                if (!inTunnel && (r > LairShape.SHELL_EDGE + 0.5D || dy > DOME_HEIGHT + SHELL_THICKNESS + 1 || dy < 0))
                {
                    leak = p;
                    break;
                }
                for (Direction d : Direction.values())
                {
                    BlockPos next = p.relative(d);
                    if (seen.add(next.asLong()) && open(next))
                    {
                        queue.add(next);
                    }
                }
            }
            if (leak != null)
            {
                problems.add("the cavern's air leaks out at " + leak.toShortString() + " (not through the skylight or the tunnel)");
            }
            if (!skylight)
            {
                problems.add("the cavern's air never reaches the skylight");
            }
            if (!tunnel)
            {
                problems.add("the cavern's air never reaches the tunnel");
            }
            if (count >= FLOOD_LIMIT)
            {
                problems.add("the flood fill of the cavern didn't finish");
            }
        }

        boolean open(BlockPos pos)
        {
            BlockState state = level.getBlockState(pos);
            return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
        }

        /** Natural spawning is barred on the floor and in the tunnel, where the golems' own spawn rules would allow it. */
        void checkNoSpawns(List<String> problems)
        {
            EntityType<StoneGolem> golem = ModEntities.STONE_GOLEM.get();
            BlockPos onFloor = new BlockPos(cx + side.getStepX() * PLAYER_OFFSET, floor + 1, cz + side.getStepZ() * PLAYER_OFFSET);
            int s = LairShape.straightEnd() - 1;
            BlockPos inTunnel = new BlockPos(cx + dir.getStepX() * s, floor + 1, cz + dir.getStepZ() * s);
            for (BlockPos spot : List.of(onFloor, inTunnel))
            {
                boolean rules = WildGolemSpawns.checkStoneGolemSpawn(golem, level, MobSpawnType.NATURAL, spot, level.random);
                boolean allowed = SpawnPlacements.checkSpawnRules(golem, level, MobSpawnType.NATURAL, spot, level.random);
                if (!rules)
                {
                    problems.add("the stone golem's own rules already refuse " + spot.toShortString() + " (the check proves nothing)");
                }
                else if (allowed)
                {
                    problems.add("a stone golem could spawn naturally at " + spot.toShortString() + " inside the lair");
                }
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The Colossus rises, and rises again after the respawn time
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_lair_rise", timeoutTicks = BACKSTOP_TICKS)
    public static void colossusRisesAndRespawns(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        long placementDeadline = Util.getMillis() + PLACEMENT_TIMEOUT_MILLIS;
        Rise run = new Rise(server);
        helper.startSequence()
                .thenWaitUntil(() -> waitForLair(server, placementDeadline))
                .thenExecute(() -> run.guard(run::begin))
                .thenWaitUntil(() -> run.waitFor(() -> run.alive() != null, "the Colossus to rise"))
                .thenExecute(() -> run.guard(run::checkRiseAndKill))
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> run.guard(run::checkStillDeadThenNotYet))
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> run.guard(run::checkNotYetThenReady))
                .thenWaitUntil(() -> run.waitFor(() -> run.alive() != null && !run.alive().getUUID().equals(run.first),
                        "the Colossus to rise again"))
                .thenExecute(() -> run.guard(run::checkRoseAgain))
                .thenExecute(run::cleanUp)
                .thenSucceed();
    }

    /** The rise test's state (a stand-in player in the cavern, the first Colossus). */
    private static final class Rise
    {
        final MinecraftServer server;
        ServerLevel dross;
        ColossusLairData data;
        @Nullable
        ServerPlayer player;
        @Nullable
        UUID first;
        long deadline;
        /** The defeat time set for the "not yet" check, and the game time when the wait was made up. */
        long notYetDefeat;
        long readyAt;

        Rise(MinecraftServer server)
        {
            this.server = server;
        }

        /** Runs a step; on a failure, cleans up first. */
        void guard(Runnable step)
        {
            try
            {
                step.run();
            }
            catch (RuntimeException e)
            {
                cleanUp();
                throw e;
            }
        }

        void begin()
        {
            data = requireLair(server);
            dross = server.getLevel(ModDimensions.DROSS_LEVEL);
            clearColossi();
            data.resetBossForTests();
            LairKeeper.Volume lair = LairKeeper.volume();
            if (lair == null)
            {
                throw new GameTestAssertException("the lair is built but the keeper doesn't know its space");
            }
            Direction side = data.tunnel().getClockWise();
            Vec3 spot = lair.middle().add(side.getStepX() * PLAYER_OFFSET, 0.0D, side.getStepZ() * PLAYER_OFFSET);
            player = joinPlayer(dross, "lair-test-player", spot);
            if (!lair.inCavern(player.position()))
            {
                throw new GameTestAssertException("the stand-in player at " + player.position() + " isn't in the cavern");
            }
            deadline = Util.getMillis() + RISE_TIMEOUT_MILLIS;
            LOGGER.info("[lair test] A stand-in player stepped onto the lair's floor at {}; waiting for the Colossus.", player.blockPosition().toShortString());
        }

        /** Throws (keeps waiting) until the condition holds or the deadline passes. */
        void waitFor(java.util.function.BooleanSupplier condition, String what)
        {
            if (!condition.getAsBoolean() && Util.getMillis() < deadline)
            {
                throw new GameTestAssertException("waiting for " + what);
            }
        }

        /** The lair's living Colossus, or null. */
        @Nullable
        CinderColossus alive()
        {
            UUID id = data.boss();
            return id != null && dross.getEntity(id) instanceof CinderColossus boss && boss.isAlive() ? boss : null;
        }

        void checkRiseAndKill()
        {
            CinderColossus boss = alive();
            if (boss == null)
            {
                throw new GameTestAssertException("no Colossus rose within " + RISE_TIMEOUT_MILLIS / 1000 + " s of a player walking in");
            }
            Vec3 middle = LairKeeper.volume().middle();
            double off = Math.sqrt(Math.pow(boss.getX() - middle.x, 2) + Math.pow(boss.getZ() - middle.z, 2));
            if (off > RISE_TOLERANCE || Math.abs(boss.getY() - middle.y) > 1.0D)
            {
                throw new GameTestAssertException("the Colossus rose at " + boss.position() + ", not in the middle " + middle);
            }
            first = boss.getUUID();
            long killedAt = server.overworld().getGameTime();
            boss.kill();
            if (!data.beaten() || data.defeatedAt() != killedAt)
            {
                throw new GameTestAssertException("its death wasn't recorded (beaten " + data.beaten() + ", at " + data.defeatedAt()
                        + ", killed at " + killedAt + ")");
            }
            if (ColossusLair.ticksUntilRespawn(server) != LAIR_RESPAWN_TICKS)
            {
                throw new GameTestAssertException("right after its death the wait should be " + LAIR_RESPAWN_TICKS + " ticks, not "
                        + ColossusLair.ticksUntilRespawn(server));
            }
            LOGGER.info("[lair test] The Colossus rose in the middle and was killed; checking it doesn't rise again too soon.");
        }

        void checkStillDeadThenNotYet()
        {
            if (alive() != null || countAlive() > 0)
            {
                throw new GameTestAssertException("a Colossus rose again straight after the last one died");
            }
            // 20 seconds of the wait still left: nothing may rise yet.
            notYetDefeat = server.overworld().getGameTime() - (LAIR_RESPAWN_TICKS - NOT_YET_TICKS);
            data.setDefeatedAtForTests(notYetDefeat);
        }

        void checkNotYetThenReady()
        {
            if (alive() != null || countAlive() > 0)
            {
                throw new GameTestAssertException("a Colossus rose again before the respawn time was up");
            }
            if (!data.beaten() || data.defeatedAt() != notYetDefeat || ColossusLair.ticksUntilRespawn(server) <= 0L)
            {
                throw new GameTestAssertException("the time it was beaten changed by itself (" + data.defeatedAt() + ", set to "
                        + notYetDefeat + ")");
            }
            readyAt = server.overworld().getGameTime();
            data.setDefeatedAtForTests(readyAt - LAIR_RESPAWN_TICKS - 1L);
            deadline = Util.getMillis() + RISE_TIMEOUT_MILLIS;
            LOGGER.info("[lair test] Nothing rose before the respawn time; the saved time is moved back past 10 minutes.");
        }

        void checkRoseAgain()
        {
            CinderColossus boss = alive();
            if (boss == null || boss.getUUID().equals(first))
            {
                throw new GameTestAssertException("no new Colossus rose once the respawn time was up");
            }
            if (countAlive() != 1)
            {
                throw new GameTestAssertException(countAlive() + " living Colossi in the cavern, not 1");
            }
            long took = server.overworld().getGameTime() - readyAt;
            if (took > PROMPT_TICKS)
            {
                throw new GameTestAssertException("the new Colossus only rose " + took + " ticks after the respawn time was up");
            }
            LOGGER.info("[lair test] A new Colossus rose {} ticks after the respawn time was up.", took);
        }

        /** Living Colossi in the cavern. */
        int countAlive()
        {
            return dross.getEntitiesOfClass(CinderColossus.class, cavernBox(), CinderColossus::isAlive).size();
        }

        AABB cavernBox()
        {
            LairKeeper.Volume lair = LairKeeper.volume();
            Vec3 middle = lair.middle();
            double r = LairShape.FLOOR_EDGE + 4.0D;
            return new AABB(middle.x - r, middle.y - 2.0D, middle.z - r, middle.x + r, middle.y + DOME_HEIGHT + 2.0D, middle.z + r);
        }

        /** Removes every Colossus in the cavern (living or a statue). */
        void clearColossi()
        {
            if (dross != null && LairKeeper.volume() != null)
            {
                for (CinderColossus boss : dross.getEntitiesOfClass(CinderColossus.class, cavernBox(), b -> !b.isShowcase()))
                {
                    boss.discard();
                }
            }
        }

        /** No Colossus left, the stand-in gone, the lair's boss data as if freshly built. */
        void cleanUp()
        {
            clearColossi();
            if (data != null)
            {
                data.resetBossForTests();
            }
            if (player != null && player.server.getPlayerList().getPlayer(player.getUUID()) == player)
            {
                player.server.getPlayerList().remove(player);
            }
            player = null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static ServerLevel drossOf(GameTestHelper helper)
    {
        ServerLevel dross = helper.getLevel().getServer().getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        return dross;
    }

    /** Throws (keeps the test waiting) until the lair is built or the deadline passes. */
    private static void waitForLair(MinecraftServer server, long deadline)
    {
        if (!ColossusLair.isPlaced(server) && Util.getMillis() < deadline)
        {
            throw new GameTestAssertException("waiting for the lair to be built");
        }
    }

    private static ColossusLairData requireLair(MinecraftServer server)
    {
        ColossusLairData data = ColossusLairData.get(server);
        if (!data.isPlaced())
        {
            throw new GameTestAssertException("the lair wasn't built within " + PLACEMENT_TIMEOUT_MILLIS / 1000 + " s"
                    + (LairPlacer.isRunning() ? " (still being placed)" : ""));
        }
        return data;
    }

    /**
     * A stand-in player on the server's player list, in creative mode (the Colossus leaves him alone), standing at
     * {@code feet} in the Dross. He gets an in-memory network channel like the other areas' stand-ins; he isn't ticked.
     */
    private static ServerPlayer joinPlayer(ServerLevel dross, String name, Vec3 feet)
    {
        ServerPlayer player = new ServerPlayer(dross.getServer(), dross, new GameProfile(UUID.randomUUID(), name));
        player.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
        dross.getServer().getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.CREATIVE);
        if (player.serverLevel() != dross || player.position().distanceToSqr(feet) > 0.01D)
        {
            player.teleportTo(dross, feet.x, feet.y, feet.z, 0.0F, 0.0F);
        }
        return player;
    }
}
