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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
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
 *   <li>{@link #lairIsBuiltAndShapedRight}: the test world's real lair gets built (waits for it) and is a volcano: a round
 *       floor 41 blocks across, mostly obsidian with sparse magma and glowing fissures (glass over sealed lava); room over
 *       the middle for the lobs and room for the Colossus everywhere; obsidian stalactites; the crater open to the sky over
 *       the middle and standing well above the land; a cave mouth at least 9 wide and 8 tall; the way in walkable from the
 *       land to the floor (a block's step at most outside, 0.6 inside); the open space closed except up the crater and out
 *       of the mouth, with no lava touching any of it; every lava block (under the floor and outside on the slopes) walled
 *       in by Minecraft's flow rule, none in front of the mouth and nothing burnable near it; no natural spawning inside.
 *       With {@code DROSS_LAIR_PREVIEWS} set it writes the blocks for the preview images (tools/golems/lair_preview.py).
 *       An R2c lair (an old world's) is left alone: these checks don't apply to it.</li>
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
    /** The floor is at least this share obsidian... */
    private static final double MIN_OBSIDIAN_SHARE = 0.4D;
    /** ...with at least this many magma blocks, but no more than this share of it... */
    private static final int MIN_FLOOR_MAGMA = 12;
    private static final double MAX_MAGMA_SHARE = 0.06D;
    /** ...and at least this many blocks of glowing fissure (glass over lava). */
    private static final int MIN_FISSURE_GLASS = 30;
    /** At least this many stalactite tips hang from the dome. */
    private static final int MIN_STALACTITE_TIPS = 10;
    /** The rim stands at least this many blocks above the high land round the volcano's foot (its 80th percentile). */
    private static final int MIN_LANDMARK_RISE = 16;
    /** At least this many lava blocks outside on the volcano (its rivulets and pools). */
    private static final int MIN_OUTSIDE_LAVA = 40;
    /** Outside the mouth, a step up or down of at most a block (a jump) on the way in. */
    private static final double OUTSIDE_STEP = 1.0D;
    /** With this set (to anything), the shape test writes the volcano's blocks for tools/golems/lair_preview.py. */
    private static final String PREVIEW_ENV = "DROSS_LAIR_PREVIEWS";
    /** A player steps up at most this much without jumping. */
    private static final double PLAYER_STEP = 0.6D;
    /** The flood fill of the open space stops after this many blocks (it's about 30,000). */
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
                    if (data.version() < LairBuilder.VERSION)
                    {
                        LOGGER.info("[lair test] This world has an R2c lair (version {}), kept as it was built: the volcano's shape checks "
                                + "don't apply to it.", data.version());
                        return;
                    }
                    Shape shape = new Shape(dross, data);
                    List<String> problems = new ArrayList<>();
                    shape.checkFloor(problems);
                    shape.checkCeiling(problems);
                    shape.checkStalactites(problems);
                    shape.checkCrater(problems);
                    shape.checkMouth(problems);
                    shape.checkWayIn(problems);
                    shape.checkEnclosedAndLavaOutOfReach(problems);
                    shape.checkLava(problems);
                    shape.checkNoSpawns(problems);
                    LOGGER.info("[lair test] The lair, cut along its way in (left: the far wall, right: the gully), and seen from above "
                            + "at 2 blocks over the floor (# rock, O obsidian, = polished blackstone, g glass, m magma, ~ lava, "
                            + ": ash, n natural ground, o other):{}{}", shape.drawCut(), shape.drawPlan());
                    LOGGER.info("[lair test] The volcano at {} {} (floor y {}, rim y {}, mouth {} blocks out to the {}, gully to {}) was "
                                    + "built in {} ms; {}", data.centerX(), data.centerZ(), data.floorY(), data.rimY(), data.mouth(),
                            data.tunnel().getName(), data.gullyEnd(), data.buildMillis(),
                            problems.isEmpty() ? "every shape check passed" : problems.size() + " problem(s)");
                    if (System.getenv(PREVIEW_ENV) != null)
                    {
                        shape.dumpViews();
                    }
                    if (!problems.isEmpty())
                    {
                        throw new GameTestAssertException("the lair isn't right: " + String.join("; ", problems));
                    }
                })
                .thenSucceed();
    }

    /** Reads the built volcano back from the world. */
    private static final class Shape
    {
        final ServerLevel level;
        final int cx;
        final int cz;
        final int floor;
        final int rimY;
        final int mouth;
        final int gullyEnd;
        final Direction dir;
        final Direction side;

        Shape(ServerLevel level, ColossusLairData data)
        {
            this.level = level;
            this.cx = data.centerX();
            this.cz = data.centerZ();
            this.floor = data.floorY();
            this.rimY = data.rimY();
            this.mouth = data.mouth();
            this.gullyEnd = data.gullyEnd();
            this.dir = data.tunnel();
            this.side = dir.getClockWise();
            // Load every chunk the checks read (a height map of an unloaded chunk reads as the bottom of the world).
            int reach = (int) CONE_BASE_RADIUS + 16;
            for (int x = (cx - reach) >> 4; x <= (cx + reach) >> 4; x++)
            {
                for (int z = (cz - reach) >> 4; z <= (cz + reach) >> 4; z++)
                {
                    level.getChunk(x, z);
                }
            }
        }

        BlockState at(int x, int y, int z)
        {
            return level.getBlockState(new BlockPos(x, y, z));
        }

        int tx(int s, int l)
        {
            return cx + dir.getStepX() * s + side.getStepX() * l;
        }

        int tz(int s, int l)
        {
            return cz + dir.getStepZ() * s + side.getStepZ() * l;
        }

        /** A side view along the way in's line through the middle: rows from the top down. */
        String drawCut()
        {
            StringBuilder out = new StringBuilder();
            for (int y = rimY + 8; y >= floor - UNDER_FLOOR - 1; y--)
            {
                out.append(String.format("%n  %4d ", y));
                for (int s = -(int) CONE_BASE_RADIUS; s <= gullyEnd + APRON + BLEND; s++)
                {
                    out.append(glyph(at(tx(s, 0), y, tz(s, 0))));
                }
            }
            return out.toString();
        }

        /** A view from above, 2 blocks over the floor. */
        String drawPlan()
        {
            StringBuilder out = new StringBuilder();
            int reach = mouth + 4;
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
            if (LairBuilder.isLairRock(state))
            {
                return '#';
            }
            if (state.is(Blocks.OBSIDIAN))
            {
                return 'O';
            }
            if (state.is(Blocks.POLISHED_BLACKSTONE) || state.is(Blocks.POLISHED_BLACKSTONE_BRICKS)
                    || state.is(Blocks.CRACKED_POLISHED_BLACKSTONE_BRICKS) || state.is(Blocks.CHISELED_POLISHED_BLACKSTONE))
            {
                return '=';
            }
            if (state.is(LairBuilder.FISSURE_GLASS))
            {
                return 'g';
            }
            if (state.is(Blocks.MAGMA_BLOCK))
            {
                return 'm';
            }
            if (state.is(com.reg21meme.dross.registry.ModBlocks.ASH.get()))
            {
                return ':';
            }
            return state.isSolid() ? 'n' : 'o';
        }

        /**
         * Every floor column is solid underfoot with room above; the cavern ends at the floor's edge all round; the floor
         * is mostly obsidian, with a sparse sprinkling of magma and glowing fissures (glass) in it, and no lava on it.
         */
        void checkFloor(List<String> problems)
        {
            int bad = 0;
            int columns = 0;
            int obsidian = 0;
            int magma = 0;
            int glass = 0;
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
                    BlockState ground = level.getBlockState(under);
                    if (!ground.isFaceSturdy(level, under, Direction.UP) || !at(cx + dx, floor + 1, cz + dz).isAir()
                            || !at(cx + dx, floor + 2, cz + dz).isAir())
                    {
                        bad++;
                    }
                    obsidian += ground.is(Blocks.OBSIDIAN) ? 1 : 0;
                    magma += ground.is(Blocks.MAGMA_BLOCK) ? 1 : 0;
                    glass += ground.is(LairBuilder.FISSURE_GLASS) ? 1 : 0;
                }
            }
            if (bad > 0)
            {
                problems.add(bad + " of the floor's " + columns + " columns aren't solid floor with room above");
            }
            if (obsidian < columns * MIN_OBSIDIAN_SHARE)
            {
                problems.add("only " + obsidian + " of the floor's " + columns + " blocks are obsidian");
            }
            if (magma < MIN_FLOOR_MAGMA || magma > columns * MAX_MAGMA_SHARE)
            {
                problems.add(magma + " magma blocks on the floor (want " + MIN_FLOOR_MAGMA + " to " + Math.round(columns * MAX_MAGMA_SHARE)
                        + ": some, but sparse)");
            }
            if (glass < MIN_FISSURE_GLASS)
            {
                problems.add("only " + glass + " glass blocks of glowing fissures in the floor");
            }
            // The wall: every 15 degrees (away from the way in), the floor's air ends 20.5 to 23.1 blocks out (it's rough).
            double tunnelAngle = Math.atan2(dir.getStepZ(), dir.getStepX());
            List<String> walls = new ArrayList<>();
            for (int k = 0; k < 24; k++)
            {
                double a = Math.toRadians(k * 15.0D);
                if (Math.abs(Math.IEEEremainder(a - tunnelAngle, 2.0D * Math.PI)) < Math.toRadians(12.0D))
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

        /** Room above every floor column: over the middle at least the lobs' clearance, elsewhere room for the Colossus. */
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
                    int room = room(cx + dx, cz + dz);
                    least = Math.min(least, room);
                    if (r <= LOB_CLEARANCE_RADIUS && room < LOB_CLEARANCE)
                    {
                        lowMiddle++;
                    }
                    if (room < STALACTITE_CLEARANCE)
                    {
                        lowAnywhere++;
                    }
                }
            }
            if (lowMiddle > 0)
            {
                problems.add(lowMiddle + " column(s) within " + LOB_CLEARANCE_RADIUS + " of the middle have under " + LOB_CLEARANCE
                        + " blocks of room (the lobs' arcs)");
            }
            if (lowAnywhere > 0)
            {
                problems.add(lowAnywhere + " floor column(s) have under " + STALACTITE_CLEARANCE + " blocks of room (lowest " + least + ")");
            }
        }

        /** Air blocks over the floor in a column, up to the first solid one. */
        int room(int x, int z)
        {
            int room = 0;
            while (room < DOME_HEIGHT + 4 && at(x, floor + 1 + room, z).isAir())
            {
                room++;
            }
            return room;
        }

        /** Obsidian stalactites hang from the dome: tips well below the dome's smooth ceiling, at least a dozen of them. */
        void checkStalactites(List<String> problems)
        {
            int tips = 0;
            for (int dx = -FLOOR_RADIUS; dx <= FLOOR_RADIUS; dx++)
            {
                for (int dz = -FLOOR_RADIUS; dz <= FLOOR_RADIUS; dz++)
                {
                    double r = Math.sqrt(dx * dx + dz * dz);
                    if (r > LairShape.FLOOR_EDGE - 1.0D || r <= SKYLIGHT_RADIUS + 1.0D)
                    {
                        continue;
                    }
                    int room = room(cx + dx, cz + dz);
                    if (room > LairShape.ceiling(r) - STALACTITE_MIN_LENGTH || !at(cx + dx, floor + 1 + room, cz + dz).is(Blocks.OBSIDIAN))
                    {
                        continue;
                    }
                    boolean lowest = true;
                    for (Direction d : Direction.Plane.HORIZONTAL)
                    {
                        lowest &= room(cx + dx + d.getStepX(), cz + dz + d.getStepZ()) > room;
                    }
                    tips += lowest ? 1 : 0;
                }
            }
            if (tips < MIN_STALACTITE_TIPS)
            {
                problems.add("only " + tips + " stalactite tip(s) hang from the dome (want at least " + MIN_STALACTITE_TIPS + ")");
            }
            LOGGER.info("[lair test] {} stalactite tips hang from the dome.", tips);
        }

        /**
         * The crater: its throat open to the sky over the middle of the floor, open at the rim, and the rim standing well
         * above the land round the volcano's foot (a landmark).
         */
        void checkCrater(List<String> problems)
        {
            int covered = 0;
            for (int dx = -5; dx <= 5; dx++)
            {
                for (int dz = -5; dz <= 5; dz++)
                {
                    if (Math.sqrt(dx * dx + dz * dz) <= SKYLIGHT_RADIUS - 0.5D
                            && level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + dx, cz + dz) != floor + 1)
                    {
                        covered++;
                    }
                }
            }
            if (covered > 0)
            {
                problems.add(covered + " column(s) of the crater's throat aren't open to the sky");
            }
            int shut = 0;
            for (int dx = -9; dx <= 9; dx++)
            {
                for (int dz = -9; dz <= 9; dz++)
                {
                    if (Math.sqrt(dx * dx + dz * dz) <= CRATER_RADIUS - 1.5D && !at(cx + dx, rimY, cz + dz).isAir())
                    {
                        shut++;
                    }
                }
            }
            if (shut > 0)
            {
                problems.add(shut + " block(s) inside the crater at the rim's height");
            }
            List<Integer> crest = new ArrayList<>();
            List<Integer> land = new ArrayList<>();
            for (int k = 0; k < 72; k++)
            {
                double a = Math.toRadians(k * 5.0D);
                double rc = (CRATER_RADIUS + RIM_RADIUS) / 2.0D + 0.5D;
                crest.add(level.getHeight(Heightmap.Types.WORLD_SURFACE, cx + (int) Math.round(rc * Math.cos(a)),
                        cz + (int) Math.round(rc * Math.sin(a))) - 1);
                double rl = CONE_BASE_RADIUS + 8.0D;
                land.add(LairBuilder.groundTop(level, cx + (int) Math.round(rl * Math.cos(a)), cz + (int) Math.round(rl * Math.sin(a))));
            }
            crest.sort(null);
            land.sort(null);
            int crestMedian = crest.get(crest.size() / 2);
            int landHigh = land.get((int) (land.size() * 0.8D));
            if (crestMedian < rimY - 1 || crestMedian - landHigh < MIN_LANDMARK_RISE)
            {
                problems.add("the rim's crest (median y " + crestMedian + ", planned " + rimY + ") isn't at least " + MIN_LANDMARK_RISE
                        + " blocks above the land round the foot (y " + landHigh + ")");
            }
            LOGGER.info("[lair test] The crater's rim: median y {}, lowest {}, highest {}; the land round the foot up to y {} ({} below).",
                    crestMedian, crest.get(0), crest.get(crest.size() - 1), landHigh, crestMedian - landHigh);
        }

        /** The cave mouth at its lip: at least 9 wide at the floor and 8 tall in the middle, with rock over it. */
        void checkMouth(List<String> problems)
        {
            int width = 1;
            for (int sign : new int[] {-1, 1})
            {
                for (int l = sign; Math.abs(l) <= MOUTH_HALF_WIDTH + 3 && at(tx(mouth, l), floor + 1, tz(mouth, l)).isAir(); l += sign)
                {
                    width++;
                }
            }
            int height = 0;
            while (height < MOUTH_HEIGHT + 4 && at(tx(mouth, 0), floor + 1 + height, tz(mouth, 0)).isAir())
            {
                height++;
            }
            boolean roofed = !at(tx(mouth, 0), floor + 1 + height, tz(mouth, 0)).isAir();
            if (!at(tx(mouth, 0), floor + 1, tz(mouth, 0)).isAir() || width < 2 * MOUTH_HALF_WIDTH + 1 || height < MOUTH_HEIGHT || !roofed)
            {
                problems.add("the cave mouth's lip is " + width + " wide and " + height + " tall" + (roofed ? "" : " (no rock over it)")
                        + ", not at least " + (2 * MOUTH_HALF_WIDTH + 1) + " x " + MOUTH_HEIGHT);
            }
            LOGGER.info("[lair test] The cave mouth's lip is {} wide and {} tall.", width, height);
        }

        /**
         * Walks in from the land beyond the apron to the cavern's floor along the middle of the way in: outside the mouth
         * no step higher than a block (and the entrance spot on the way, open to the sky), inside none over 0.6; room for
         * a player all the way, no fluid.
         */
        void checkWayIn(List<String> problems)
        {
            int start = gullyEnd + APRON + BLEND;
            int x0 = tx(start, 0);
            int z0 = tz(start, 0);
            double previous = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x0, z0);
            int steps = 0;
            double highest = 0.0D;
            for (int s = start - 1; s >= FLOOR_RADIUS - 2; s--)
            {
                int x = tx(s, 0);
                int z = tz(s, 0);
                double here = surface(x, z, (int) Math.floor(previous));
                if (Double.isNaN(here))
                {
                    problems.add("no ground on the way in at " + x + " " + z);
                    return;
                }
                double step = Math.abs(here - previous);
                double allowed = s > mouth ? OUTSIDE_STEP : PLAYER_STEP;
                if (step > allowed + 1.0E-6D)
                {
                    problems.add(String.format("a step of %.1f blocks on the way in at %d %.1f %d (%d blocks out; at most %.1f there)",
                            here - previous, x, here, z, s, allowed));
                    return;
                }
                steps += step > 1.0E-6D ? 1 : 0;
                highest = Math.max(highest, step);
                AABB body = new AABB(x + 0.2D, here + 0.01D, z + 0.2D, x + 0.8D, here + 1.8D, z + 0.8D);
                if (!level.noCollision(body) || level.containsAnyLiquid(body))
                {
                    problems.add("no room for a player on the way in at " + x + " " + here + " " + z);
                    return;
                }
                if (s == mouth + ENTRANCE_GAP && (Math.abs(here - (floor + 1)) > 1.0E-6D
                        || level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) > floor + 1))
                {
                    problems.add("the entrance spot at " + x + " " + z + " isn't on the floor's level open to the sky");
                }
                previous = here;
            }
            if (Math.abs(previous - (floor + 1)) > 1.0E-6D)
            {
                problems.add("the way in ends at " + previous + ", not on the floor (" + (floor + 1) + ")");
            }
            LOGGER.info("[lair test] Walked in from {} blocks out: {} step(s) up or down, the biggest {} block(s).", start, steps, highest);
        }

        /** The top of whatever a player would stand on in a column, looking down from just above {@code around}. */
        double surface(int x, int z, int around)
        {
            for (int y = around + 2; y >= around - 3; y--)
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
         * A flood fill of the open space from the middle of the floor: it must never get out of the volcano except up the
         * crater (to the open sky) or out of the mouth, and no lava may touch any of it (players thrown about by the
         * Colossus never land in lava, inside or round the rim).
         */
        void checkEnclosedAndLavaOutOfReach(List<String> problems)
        {
            ArrayDeque<BlockPos> queue = new ArrayDeque<>();
            LongOpenHashSet seen = new LongOpenHashSet();
            BlockPos start = new BlockPos(cx, floor + 1, cz);
            queue.add(start);
            seen.add(start.asLong());
            int count = 0;
            boolean crater = false;
            boolean entrance = false;
            BlockPos leak = null;
            BlockPos lavaNear = null;
            while (!queue.isEmpty() && count < FLOOD_LIMIT && leak == null)
            {
                BlockPos p = queue.poll();
                count++;
                for (Direction d : Direction.values())
                {
                    if (level.getFluidState(p.relative(d)).is(Fluids.LAVA) && lavaNear == null)
                    {
                        lavaNear = p;
                    }
                }
                int dx = p.getX() - cx;
                int dz = p.getZ() - cz;
                int dy = p.getY() - floor;
                double r = Math.sqrt(dx * dx + dz * dz);
                int s = dx * dir.getStepX() + dz * dir.getStepZ();
                int l = dx * side.getStepX() + dz * side.getStepZ();
                if (dy >= DOME_HEIGHT - 2 && p.getY() >= level.getHeight(Heightmap.Types.WORLD_SURFACE, p.getX(), p.getZ()))
                {
                    crater = true;
                    continue; // up the crater to the open sky
                }
                boolean inPassage = s >= FLOOR_RADIUS - 3 && Math.abs(l) <= MOUTH_HALF_WIDTH + MOUTH_ROUGHNESS + 1.0D
                        && dy >= 1 && dy <= MOUTH_HEIGHT + 2;
                if (inPassage && s > mouth)
                {
                    entrance = true;
                    continue; // out of the mouth into the gully
                }
                boolean inCavern = r <= LairShape.FLOOR_EDGE + ROUGHNESS + 0.6D && dy >= 1 && dy <= DOME_HEIGHT + ROUGHNESS + 1;
                boolean inCrater = r <= CRATER_RADIUS + CRATER_ROUGHNESS + 1.5D && dy >= 1;
                if (!inCavern && !inCrater && !inPassage)
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
                problems.add("the cavern's air leaks out at " + leak.toShortString() + " (not through the crater or the mouth)");
            }
            if (!crater)
            {
                problems.add("the cavern's air never reaches the open sky up the crater");
            }
            if (!entrance)
            {
                problems.add("the cavern's air never reaches the gully through the mouth");
            }
            if (count >= FLOOD_LIMIT)
            {
                problems.add("the flood fill of the cavern didn't finish");
            }
            if (lavaNear != null)
            {
                problems.add("lava touches open space a player can reach inside the volcano, at " + lavaNear.toShortString());
            }
            LOGGER.info("[lair test] The cavern, the way in and the crater hold {} blocks of open space; no lava touches any of it: {}.",
                    count, lavaNear == null);
        }

        boolean open(BlockPos pos)
        {
            BlockState state = level.getBlockState(pos);
            return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
        }

        /**
         * All lava in the volcano's footprint (under the floor and outside on its slopes): no flowing lava, every lava
         * block walled in by Minecraft's flow rule (below and on its four sides: lava, or a block lava can't flow into),
         * lava both under the floor and outside, none in front of the way in, and nothing that burns near any of it.
         */
        void checkLava(List<String> problems)
        {
            int reach = (int) Math.ceil(CONE_BASE_RADIUS) + 2;
            int flowing = 0;
            int loose = 0;
            int under = 0;
            int outside = 0;
            int inWay = 0;
            int burnable = 0;
            String example = null;
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            BlockPos.MutableBlockPos q = new BlockPos.MutableBlockPos();
            for (int dx = -reach; dx <= reach; dx++)
            {
                for (int dz = -reach; dz <= reach; dz++)
                {
                    if (dx * dx + dz * dz > reach * reach)
                    {
                        continue;
                    }
                    for (int y = floor - UNDER_FLOOR - 2; y <= rimY + (int) Math.ceil(RIM_TEETH) + 4; y++)
                    {
                        p.set(cx + dx, y, cz + dz);
                        FluidState fluid = level.getFluidState(p);
                        if (!fluid.is(Fluids.LAVA))
                        {
                            continue;
                        }
                        if (!fluid.isSource())
                        {
                            flowing++;
                            example = example == null ? "flowing lava at " + p.toShortString() : example;
                            continue;
                        }
                        if (y == floor - 1 && level.getBlockState(p.above()).is(LairBuilder.FISSURE_GLASS))
                        {
                            under++;
                        }
                        else
                        {
                            outside++;
                            int s = dx * dir.getStepX() + dz * dir.getStepZ();
                            int l = dx * side.getStepX() + dz * side.getStepZ();
                            if (s > mouth && s <= gullyEnd + APRON && Math.abs(l) <= MOUTH_HALF_WIDTH + 1 + (s - mouth) * GULLY_FLARE)
                            {
                                boolean exposed = false;
                                for (Direction d : Direction.values())
                                {
                                    exposed |= open(p.relative(d)) && !level.getFluidState(p.relative(d)).is(Fluids.LAVA);
                                }
                                if (exposed) // (lava buried under the gully's floor, sealed in the ground, is no danger)
                                {
                                    inWay++;
                                    example = example == null ? "lava in the way in at " + p.toShortString() + " (" + (y - floor)
                                            + " above the floor)" : example;
                                }
                            }
                        }
                        for (Direction d : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST})
                        {
                            q.setWithOffset(p, d);
                            BlockState state = level.getBlockState(q);
                            if (state.getFluidState().is(Fluids.LAVA))
                            {
                                continue;
                            }
                            if (LairBuilder.lavaCouldEnter(level, q, state))
                            {
                                loose++;
                                example = example == null ? "lava at " + p.toShortString() + " could flow to " + q.toShortString() : example;
                            }
                        }
                        for (int ox = -FIRE_GUARD_REACH; ox <= FIRE_GUARD_REACH; ox++)
                        {
                            for (int oz = -FIRE_GUARD_REACH; oz <= FIRE_GUARD_REACH; oz++)
                            {
                                for (int oy = -1; oy <= FIRE_GUARD_UP; oy++)
                                {
                                    q.set(p.getX() + ox, y + oy, p.getZ() + oz);
                                    if (LairBuilder.burns(level, q, level.getBlockState(q)))
                                    {
                                        burnable++;
                                        example = example == null ? "a block that burns at " + q.toShortString() + " near lava" : example;
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (flowing > 0 || loose > 0 || burnable > 0)
            {
                problems.add(flowing + " flowing lava block(s), " + loose + " way(s) for lava to flow and " + burnable
                        + " burnable block(s) near lava (e.g. " + example + ")");
            }
            if (under < MIN_FISSURE_GLASS)
            {
                problems.add("only " + under + " lava block(s) under the floor's glass");
            }
            if (outside < MIN_OUTSIDE_LAVA)
            {
                problems.add("only " + outside + " lava block(s) outside on the volcano (want at least " + MIN_OUTSIDE_LAVA + ")");
            }
            if (inWay > 0)
            {
                problems.add(inWay + " lava block(s) open to the gully or the apron in front of the mouth (e.g. " + example + ")");
            }
            LOGGER.info("[lair test] Lava: {} block(s) under the floor's fissures, {} outside on the volcano; all of it walled in: {}.",
                    under, outside, flowing == 0 && loose == 0);
        }

        /** Natural spawning is barred on the floor and in the passage, where the golems' own spawn rules would allow it. */
        void checkNoSpawns(List<String> problems)
        {
            EntityType<StoneGolem> golem = ModEntities.STONE_GOLEM.get();
            int rx = cx + side.getStepX() * PLAYER_OFFSET;
            int rz = cz + side.getStepZ() * PLAYER_OFFSET;
            BlockPos onFloor = new BlockPos(rx, floor + 1, rz);
            for (int k = 0; k < 8 && !at(rx, floor, rz).is(Blocks.OBSIDIAN) && !at(rx, floor, rz).is(Blocks.BLACKSTONE); k++)
            {
                rx += dir.getStepX(); // golems don't spawn on magma or glass: find a plain floor block
                rz += dir.getStepZ();
                onFloor = new BlockPos(rx, floor + 1, rz);
            }
            BlockPos inPassage = new BlockPos(tx(mouth - 2, 0), floor + 1, tz(mouth - 2, 0));
            for (int l = 1; l <= 2 && !(at(inPassage.getX(), floor, inPassage.getZ()).is(Blocks.OBSIDIAN)
                    || at(inPassage.getX(), floor, inPassage.getZ()).is(Blocks.BLACKSTONE)); l++)
            {
                inPassage = new BlockPos(tx(mouth - 2, l), floor + 1, tz(mouth - 2, l));
            }
            for (BlockPos spot : List.of(onFloor, inPassage))
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

        // ------------------------------------------------------------ the preview images

        /**
         * Writes the volcano's real blocks for tools/golems/lair_preview.py (build/lair/views/): the outside, turned so
         * the mouth faces south (the drawing's camera looks from the south-east), and a cut-away with its east half
         * removed, so the cavern, the crater and the passage show in section.
         */
        void dumpViews()
        {
            int outsideHalf = (int) CONE_BASE_RADIUS + 12;
            dumpView("lair_outside", "The Cinder Colossus's volcano", outsideHalf, floor - 6, rimY + 30, false);
            int cutHalf = Math.max(gullyEnd + APRON + 2, (int) LairShape.SHELL_EDGE + 12);
            dumpView("lair_cutaway", "The volcano cut open: the cavern, the crater and the way in", cutHalf, floor - 5, rimY + 8, true);
        }

        /** Offsets in the turned view (i east, j south when the mouth faces south) to a world column. */
        int[] world(int i, int j)
        {
            Direction east = dir.getCounterClockWise();
            return new int[] {cx + east.getStepX() * i + dir.getStepX() * j, cz + east.getStepZ() * i + dir.getStepZ() * j};
        }

        boolean cutAway(int i, boolean cut)
        {
            return cut && i > 0;
        }

        boolean opaqueAt(int i, int y, int j, boolean cut, int half)
        {
            if (cutAway(i, cut) || i >= half || j >= half)
            {
                return false;
            }
            int[] w = world(i, j);
            BlockPos p = new BlockPos(w[0], y, w[1]);
            return level.getBlockState(p).isSolidRender(level, p);
        }

        void dumpView(String name, String title, int half, int y0, int y1, boolean cut)
        {
            java.util.Map<String, Integer> palette = new java.util.LinkedHashMap<>();
            com.google.gson.JsonArray blocks = new com.google.gson.JsonArray();
            for (int j = -half; j < half; j++)
            {
                for (int i = -half; i < half; i++)
                {
                    if (cutAway(i, cut))
                    {
                        continue;
                    }
                    int[] w = world(i, j);
                    for (int y = y0; y <= y1; y++)
                    {
                        BlockPos p = new BlockPos(w[0], y, w[1]);
                        BlockState state = level.getBlockState(p);
                        if (state.isAir() || opaqueAt(i, y + 1, j, cut, half) && opaqueAt(i, y, j + 1, cut, half)
                                && opaqueAt(i + 1, y, j, cut, half))
                        {
                            continue;
                        }
                        String key = net.minecraft.commands.arguments.blocks.BlockStateParser.serialize(state);
                        int index = palette.computeIfAbsent(key, k -> palette.size());
                        int light = level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, p);
                        for (BlockPos n : new BlockPos[] {p.above(), new BlockPos(world(i, j + 1)[0], y, world(i, j + 1)[1]),
                                new BlockPos(world(i + 1, j)[0], y, world(i + 1, j)[1])})
                        {
                            light = Math.max(light, level.getBrightness(net.minecraft.world.level.LightLayer.BLOCK, n));
                        }
                        com.google.gson.JsonArray b = new com.google.gson.JsonArray();
                        b.add(i + half);
                        b.add(y - y0);
                        b.add(j + half);
                        b.add(index);
                        b.add(light);
                        blocks.add(b);
                    }
                }
            }
            com.google.gson.JsonObject root = new com.google.gson.JsonObject();
            root.addProperty("note", "Written by ColossusLairGameTest; drawn by tools/golems/lair_preview.py");
            root.addProperty("name", name);
            root.addProperty("title", title);
            root.addProperty("size", 2 * half);
            root.addProperty("floor", floor - y0);
            root.addProperty("rim", rimY - y0);
            root.addProperty("cut", cut);
            com.google.gson.JsonArray names = new com.google.gson.JsonArray();
            palette.keySet().forEach(names::add);
            root.add("palette", names);
            root.add("blocks", blocks);
            java.nio.file.Path file = net.minecraftforge.fml.loading.FMLPaths.GAMEDIR.get().toAbsolutePath().getParent()
                    .resolve("build").resolve("lair").resolve("views").resolve(name + ".json");
            try
            {
                java.nio.file.Files.createDirectories(file.getParent());
                java.nio.file.Files.writeString(file, new com.google.gson.GsonBuilder().create().toJson(root));
                LOGGER.info("[lair test] Wrote {} ({} blocks) for the preview.", file, blocks.size());
            }
            catch (java.io.IOException e)
            {
                LOGGER.warn("[lair test] Couldn't write {}", file, e);
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
