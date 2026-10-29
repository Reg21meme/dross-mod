package com.reg21meme.dross.command;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.emberdactyl.EmberVolcano;
import com.reg21meme.dross.boss.fire.FireAnims;
import com.reg21meme.dross.boss.fire.FireCandidate;
import com.reg21meme.dross.boss.fire.FireDesigns;
import com.reg21meme.dross.boss.fire.FireSounds;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;

/**
 * Headless tests (GameTest) for the Emberdactyl's gallery and its volcano, and the Cinder Wastes' mobs' gallery
 * ({@link FireShowcase}; the servants' gallery is checked by {@code boss.fire.FireShowcaseGameTest}):
 * <ul>
 *   <li><b>The Emberdactyl</b> ({@code /dross showcase boss_designs emberdactyl}, run far from everything else at
 *       {@link #EMBER_X}, {@link #EMBER_Z}, looking south): its 15 models stand in the gallery, each once, facing back;
 *       its volcano goes up behind them, then: every lava block it planned is lava, a still source, with lava or
 *       something solid under it and on all four sides, and a while later there's still exactly as much lava and none
 *       of it flowing; its rim stands about its height; its path climbs at most a block a step, with room to walk all
 *       the way to the lookout; the egg stands in the nest (on its top, bones and charred logs round it) and the adult
 *       hovers over it without falling.</li>
 *   <li><b>The Cinder Wastes' mobs</b> ({@code /dross showcase cinder_mobs}, at {@link #MOBS_X}, {@link #MOBS_Z}):
 *       the Pallbearer and the eight Cinder Hermits, each once, in one row from east to west.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class FireGalleriesGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Where the tests run: far from spawn, the portal site and the other tests' galleries. */
    private static final int EMBER_X = -3600;
    private static final int EMBER_Z = 3600;
    private static final int MOBS_X = -3600;
    private static final int MOBS_Z = -3600;
    /** Candidates are looked for this far round the command's spot (X and Z), at every height. */
    private static final int SEARCH_RADIUS = 340;
    private static final double TOLERANCE = 0.01D;
    /** How long the volcano may take to go up, and how long the lava is left before it's checked again (ticks: lava
     *  in the Overworld moves every 30). */
    private static final int VOLCANO_TIMEOUT_TICKS = 12_000;
    private static final int LAVA_SETTLE_TICKS = 120;
    /** The rim stands at least this close to its planned height (its crest is jagged, notched at the path). */
    private static final int RIM_SLACK = 9;
    /** The Emberdactyl's test lays its own plain: its top this high, this thick, cleared this far over it. */
    private static final int PLAIN_Y = 180;
    private static final int PLAIN_DEPTH = 10;
    private static final int PLAIN_CLEAR = 100;
    /** The tests' chunk tickets: each holds its chunk at entity ticking (two levels in from where a ticket loads). */
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_fire_galleries_test",
            Comparator.comparingLong(ChunkPos::toLong));
    private static final int HOLD_DISTANCE = 2;
    /** The nest's ring holds at least this many bones and charred logs. */
    private static final int NEST_RING_MIN = 12;

    private FireGalleriesGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_fire_emberdactyl", timeoutTicks = VOLCANO_TIMEOUT_TICKS + 2000)
    public static void emberdactylGalleryAndVolcano(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        BlockPos column = new BlockPos(EMBER_X, 0, EMBER_Z);
        // the gallery and the volcano held loaded (with no player there, nothing else would), on a flat plain high over
        // whatever land (or sea) is there, like the superflat world the showcase is meant for
        double reach = FireShowcase.layout(FireDesigns.EMBERDACTYL).depth() + 2.0D * EmberVolcano.REACH + 20.0D;
        int half = (int) Math.ceil(Math.max(EmberVolcano.REACH, (FireShowcase.layout(FireDesigns.EMBERDACTYL).rightEdge()
                - FireShowcase.layout(FireDesigns.EMBERDACTYL).leftEdge()) / 2.0D));
        int x0 = column.getX() - half - 12;
        int z0 = column.getZ() - 8;
        int x1 = column.getX() + half + 12;
        int z1 = column.getZ() + (int) Math.ceil(reach);
        List<ChunkPos> held = hold(level, x0, z0, x1, z1);
        plain(level, x0, z0, x1, z1);
        double galleryEnd = column.getZ() + FireShowcase.layout(FireDesigns.EMBERDACTYL).depth() + 6.0D;
        EmberVolcano.Plan[] plan = {null};
        helper.startSequence()
                .thenWaitUntil(() -> ready(level, held))
                .thenExecute(() -> {
                    int result = run(server, level, column, PLAIN_Y + 1, "dross showcase boss_designs emberdactyl");
                    if (result != FireDesigns.EMBERDACTYL.size())
                    {
                        throw new GameTestAssertException("the command spawned " + result + " candidates, not "
                                + FireDesigns.EMBERDACTYL.size());
                    }
                    plan[0] = FireShowcase.lastVolcano();
                })
                .thenWaitUntil(() -> {
                    if (FireShowcase.buildingVolcano())
                    {
                        throw new GameTestAssertException("waiting for the volcano to go up");
                    }
                })
                .thenExecute(() -> {
                    checkGallery(level, column, galleryEnd);
                    checkLava(level, plan[0], "as built");
                    checkRim(level, plan[0]);
                    checkPerch(level, plan[0]);
                    checkPath(level, plan[0]);
                    checkNest(level, plan[0], column, galleryEnd);
                })
                .thenIdle(LAVA_SETTLE_TICKS)
                .thenExecute(() -> {
                    checkLava(level, plan[0], LAVA_SETTLE_TICKS + " ticks later");
                    checkNoStrayLava(level, plan[0]);
                    checkNest(level, plan[0], column, galleryEnd);
                    LOGGER.info("[fire test] The Emberdactyl gallery holds its {} models; its volcano stands ({} lava blocks, "
                                    + "all still and walled in, a path of {} blocks up to the lookout, the egg in the nest and "
                                    + "the adult over it)", FireDesigns.EMBERDACTYL.size(), plan[0].lavaBlocks(),
                            plan[0].pathLine().size());
                    found(level, column).forEach(FireCandidate::discard);
                    release(level, held);
                })
                .thenSucceed();
    }

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_fire_cinder_mobs", timeoutTicks = 6000)
    public static void cinderMobsGallery(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel level = server.overworld();
        BlockPos column = new BlockPos(MOBS_X, 0, MOBS_Z);
        double half = (FireShowcase.layout(FireDesigns.CINDER_MOBS).rightEdge() - FireShowcase.layout(FireDesigns.CINDER_MOBS).leftEdge()) / 2.0D;
        double depth = FireShowcase.layout(FireDesigns.CINDER_MOBS).depth();
        List<ChunkPos> held = hold(level, column.getX() - (int) half - 16, column.getZ() - 16, column.getX() + (int) half + 16,
                column.getZ() + (int) depth + 16);
        helper.startSequence()
                .thenWaitUntil(() -> ready(level, held))
                .thenExecute(() -> {
                    try
                    {
                        level.getChunkAt(column);
                        int feetY = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column.getX(), column.getZ());
                        int result = run(server, level, column, feetY, "dross showcase cinder_mobs");
                        List<FireDesigns.Design> designs = FireDesigns.CINDER_MOBS;
                        if (result != designs.size())
                        {
                            throw new GameTestAssertException("the command spawned " + result + " candidates, not " + designs.size());
                        }
                        Map<Integer, FireCandidate> byNumber = byNumber(found(level, column), designs.size(), "the mobs' gallery");
                        double lastX = Double.POSITIVE_INFINITY;
                        for (FireDesigns.Design design : designs)
                        {
                            FireCandidate candidate = byNumber.get(design.number());
                            if (candidate == null || candidate.getX() >= lastX)
                            {
                                throw new GameTestAssertException("No. " + design.label() + " isn't to the right of the one before it");
                            }
                            lastX = candidate.getX();
                        }
                        LOGGER.info("[fire test] The Cinder Wastes' mobs stand in one row, each once ({})", designs.size());
                        // their controls: every one of them walks, then the hermits hide (the Pallbearer has no hide)
                        CommandSourceStack source = server.createCommandSourceStack().withLevel(level)
                                .withPosition(Vec3.atBottomCenterOf(column.atY(feetY))).withSuppressedOutput();
                        int walking = FireShowcase.play(source, FireShowcase.Gallery.CINDER_MOBS, "walk");
                        int hiding = FireShowcase.play(source, FireShowcase.Gallery.CINDER_MOBS, "hide");
                        int hermits = (int) designs.stream().filter(d -> d.mob().equals("hermit")).count();
                        if (walking != designs.size() || hiding != hermits)
                        {
                            throw new GameTestAssertException("the controls played walk on " + walking + " (not "
                                    + designs.size() + ") and hide on " + hiding + " (not " + hermits + ")");
                        }
                        for (FireCandidate candidate : found(level, column))
                        {
                            String expected = candidate.getDesign().mob().equals("hermit") ? "hide" : "walk";
                            if (!candidate.animation().equals(expected))
                            {
                                throw new GameTestAssertException("No. " + candidate.getDesign().label() + " plays "
                                        + candidate.animation() + ", not " + expected);
                            }
                        }
                    }
                    finally
                    {
                        found(level, column).forEach(FireCandidate::discard);
                        release(level, held);
                    }
                })
                .thenSucceed();
    }

    /**
     * Every design that moves has its model, its texture and its mob's animations in the mod, and every animation the
     * mod knows of ({@link FireAnims}) is in its file at the length the mod thinks (for a mob whose models each have
     * their own file, the Cinder Hermit, in each of them); every frame of fire a model's flames have ({@link
     * FireAnims#FIRE}) is in the mod; every sound its animations make ({@link FireSounds#CUES}) is one the mod has.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_fire_anims")
    public static void fireAnimationsAreInTheMod(GameTestHelper helper)
    {
        for (FireDesigns.Design design : FireDesigns.ALL)
        {
            if (!design.animated())
            {
                continue;
            }
            if (FireAnims.get(design.mob()) == null)
            {
                throw new GameTestAssertException("No. " + design.label() + " wears the mob " + design.mob()
                        + ", which FireAnims doesn't know");
            }
            for (String file : new String[] {"geo/entity/firemob/" + design.id() + ".geo.json",
                    "textures/entity/firemob/" + design.id() + ".png"})
            {
                if (FireGalleriesGameTest.class.getResource("/assets/dross/" + file) == null)
                {
                    throw new GameTestAssertException("No. " + design.label() + ": no " + file + " in the mod");
                }
            }
        }
        for (FireAnims.Mob mob : FireAnims.BY_ID.values())
        {
            List<String> names = mob.perModel()
                    ? FireDesigns.ALL.stream().filter(d -> d.animated() && d.mob().equals(mob.id()))
                            .map(FireDesigns.Design::id).toList()
                    : List.of(mob.id());
            if (names.isEmpty())
            {
                throw new GameTestAssertException(mob.id() + " has its own animations for each model, but no models");
            }
            for (String name : names)
            {
                checkAnimations("/assets/dross/animations/entity/firemob/" + name + ".animation.json", mob);
            }
        }
        for (Map.Entry<String, Integer> fire : FireAnims.FIRE.entrySet())
        {
            for (int k = 1; k < fire.getValue(); k++)
            {
                String frame = fire.getKey() + "_fire" + k;
                for (String file : new String[] {frame + ".png", frame + "_glow.png"})
                {
                    if (FireGalleriesGameTest.class.getResource("/assets/dross/textures/entity/firemob/" + file) == null)
                    {
                        throw new GameTestAssertException(fire.getKey() + "'s flames: no " + file + " in the mod");
                    }
                }
            }
        }
        for (FireSounds.Cue cue : FireSounds.CUES)
        {
            FireAnims.Mob mob = FireAnims.get(cue.mob());
            if (mob == null || mob.get(cue.anim()) == null
                    || FireSounds.ALL.stream().noneMatch(s -> s.event().equals(cue.event())))
            {
                throw new GameTestAssertException("a sound cue for " + cue.mob() + " " + cue.anim() + " (" + cue.event()
                        + ") points at nothing");
            }
        }
        helper.succeed();
    }

    /** Every animation of the mob is in the file at the length the mod thinks. */
    private static void checkAnimations(String file, FireAnims.Mob mob)
    {
        try (InputStream in = FireGalleriesGameTest.class.getResourceAsStream(file))
        {
            if (in == null)
            {
                throw new GameTestAssertException("no " + file + " in the mod");
            }
            JsonObject anims = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))
                    .getAsJsonObject().getAsJsonObject("animations");
            for (FireAnims.Anim anim : mob.anims())
            {
                JsonObject a = anims.getAsJsonObject("animation." + mob.id() + "." + anim.name());
                if (a == null || Math.abs(a.get("animation_length").getAsFloat() - anim.seconds()) > 0.01F)
                {
                    throw new GameTestAssertException(file + ": " + anim.name() + " missing or not "
                            + anim.seconds() + " s long");
                }
            }
        }
        catch (IOException e)
        {
            throw new GameTestAssertException("couldn't read " + file + ": " + e);
        }
    }

    /** Holds every chunk from ({@code x0}, {@code z0}) to ({@code x1}, {@code z1}) loaded, its entities seen, until
     *  {@link #release}d (the chunks load in the background: wait for {@link #ready}). */
    private static List<ChunkPos> hold(ServerLevel level, int x0, int z0, int x1, int z1)
    {
        List<ChunkPos> held = new ArrayList<>();
        for (int cx = x0 >> 4; cx <= x1 >> 4; cx++)
        {
            for (int cz = z0 >> 4; cz <= z1 >> 4; cz++)
            {
                ChunkPos chunk = new ChunkPos(cx, cz);
                level.getChunkSource().addRegionTicket(TICKET, chunk, HOLD_DISTANCE, chunk);
                held.add(chunk);
            }
        }
        return held;
    }

    /** Waits (throws) until every held chunk is loaded with its entities seen and ticking. */
    private static void ready(ServerLevel level, List<ChunkPos> held)
    {
        int waiting = 0;
        for (ChunkPos chunk : held)
        {
            if (!level.isPositionEntityTicking(chunk.getWorldPosition()))
            {
                waiting++;
            }
        }
        if (waiting > 0)
        {
            throw new GameTestAssertException("waiting for " + waiting + " of " + held.size() + " chunks to load");
        }
    }

    private static void release(ServerLevel level, List<ChunkPos> held)
    {
        for (ChunkPos chunk : held)
        {
            level.getChunkSource().removeRegionTicket(TICKET, chunk, HOLD_DISTANCE, chunk);
        }
    }

    /**
     * A flat plain of grass on dirt, its top at {@link #PLAIN_Y} (over the sea and the land, under the sky), cleared
     * {@link #PLAIN_CLEAR} blocks over it, from ({@code x0}, {@code z0}) to ({@code x1}, {@code z1}).
     */
    private static void plain(ServerLevel level, int x0, int z0, int x1, int z1)
    {
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (int x = x0; x <= x1; x++)
        {
            for (int z = z0; z <= z1; z++)
            {
                for (int y = PLAIN_Y - PLAIN_DEPTH; y <= PLAIN_Y + PLAIN_CLEAR; y++)
                {
                    BlockState want = y < PLAIN_Y ? dirt : y == PLAIN_Y ? grass : air;
                    if (level.getBlockState(pos.set(x, y, z)) != want)
                    {
                        level.setBlock(pos, want, net.minecraft.world.level.block.Block.UPDATE_CLIENTS
                                | net.minecraft.world.level.block.Block.UPDATE_KNOWN_SHAPE);
                    }
                }
            }
        }
    }

    /** Runs a command at {@code column}, feet at {@code feetY}, looking south. */
    private static int run(MinecraftServer server, ServerLevel level, BlockPos column, int feetY, String command)
    {
        var source = server.createCommandSourceStack().withLevel(level).withPermission(2)
                .withPosition(new Vec3(column.getX() + 0.5D, feetY, column.getZ() + 0.5D)).withRotation(new Vec2(0.0F, 0.0F))
                .withSuppressedOutput();
        return server.getCommands().performPrefixedCommand(source, command);
    }

    private static List<FireCandidate> found(ServerLevel level, BlockPos column)
    {
        AABB area = new AABB(column.getX() - SEARCH_RADIUS, level.getMinBuildHeight(), column.getZ() - SEARCH_RADIUS,
                column.getX() + SEARCH_RADIUS, level.getMaxBuildHeight(), column.getZ() + SEARCH_RADIUS);
        return level.getEntities(ModEntities.FIRE_CANDIDATE.get(), area, candidate -> true);
    }

    /** Each design once among {@code candidates}, all on one floor and facing north (back towards the command). */
    private static Map<Integer, FireCandidate> byNumber(List<FireCandidate> candidates, int count, String what)
    {
        Map<Integer, FireCandidate> byNumber = new HashMap<>();
        TreeSet<Integer> numbers = new TreeSet<>();
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
            if (Math.abs(candidate.getY() - floor) > TOLERANCE)
            {
                throw new GameTestAssertException(what + ": No. " + design.label() + " stands at y " + candidate.getY()
                        + ", not on the floor (y " + floor + ")");
            }
            if (Math.abs(Math.IEEEremainder(candidate.getYRot() - 180.0F, 360.0D)) > TOLERANCE)
            {
                throw new GameTestAssertException(what + ": No. " + design.label() + " faces " + candidate.getYRot()
                        + " degrees, not back towards the command (180)");
            }
        }
        if (candidates.size() != count || numbers.size() != count)
        {
            throw new GameTestAssertException(what + ": found " + candidates.size() + " candidates with " + numbers.size()
                    + " different designs, not " + count + " of each");
        }
        return byNumber;
    }

    /** The gallery's models (those in front of the volcano), each once, the columns' rows behind their first. */
    private static void checkGallery(ServerLevel level, BlockPos column, double galleryEnd)
    {
        List<FireCandidate> gallery = new ArrayList<>();
        for (FireCandidate candidate : found(level, column))
        {
            if (candidate.getZ() < galleryEnd)
            {
                gallery.add(candidate);
            }
        }
        Map<Integer, FireCandidate> byNumber = byNumber(gallery, FireDesigns.EMBERDACTYL.size(), "the Emberdactyl's gallery");
        double lastX = Double.POSITIVE_INFINITY;
        Map<Integer, FireCandidate> previous = new HashMap<>();
        for (FireDesigns.Design design : FireDesigns.EMBERDACTYL)
        {
            FireCandidate candidate = byNumber.get(design.number());
            if (design.row() == 0)
            {
                if (candidate.getX() >= lastX)
                {
                    throw new GameTestAssertException("No. " + design.label() + " isn't to the right of the column before it");
                }
                lastX = candidate.getX();
            }
            else
            {
                FireCandidate before = previous.get(design.column());
                if (before == null || Math.abs(before.getX() - candidate.getX()) > TOLERANCE || candidate.getZ() <= before.getZ())
                {
                    throw new GameTestAssertException("No. " + design.label() + " doesn't stand behind the look before it");
                }
            }
            previous.put(design.column(), candidate);
        }
    }

    /** Every lava block planned is a still lava source with lava or solid ground under it and on its four sides. */
    static void checkLava(ServerLevel level, EmberVolcano.Plan plan, String when)
    {
        int[] missing = {0};
        int[] open = {0};
        BlockPos[] firstMissing = {null};
        String[] firstOpen = {null};
        plan.forEachLava(pos -> {
            FluidState fluid = level.getFluidState(pos);
            if (!fluid.is(FluidTags.LAVA) || !fluid.isSource())
            {
                if (missing[0]++ == 0)
                {
                    firstMissing[0] = pos.immutable();
                }
                return;
            }
            for (Direction side : new Direction[] {Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST})
            {
                BlockPos next = pos.relative(side);
                if (!holds(level, next))
                {
                    if (open[0]++ == 0)
                    {
                        firstOpen[0] = pos.toShortString() + " (" + side.getName() + ": " + level.getBlockState(next) + ")";
                    }
                }
            }
        });
        if (missing[0] > 0 || open[0] > 0)
        {
            throw new GameTestAssertException(String.format("the volcano's lava %s: %d of %d planned blocks aren't still lava "
                            + "(first at %s), %d sides open (first at %s)", when, missing[0], plan.lavaBlocks(),
                    firstMissing[0] == null ? "-" : firstMissing[0].toShortString(), open[0], firstOpen[0]));
        }
    }

    /** Lava, or a solid full block: lava next to it can't flow there. */
    private static boolean holds(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        if (state.getFluidState().is(FluidTags.LAVA))
        {
            return true;
        }
        return !state.isAir() && state.getFluidState().isEmpty() && state.isCollisionShapeFullBlock(level, pos);
    }

    /** No lava in the volcano's own columns but what it planned, and none of it flowing. */
    static void checkNoStrayLava(ServerLevel level, EmberVolcano.Plan plan)
    {
        BoundingBox box = plan.box();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int lava = 0;
        int flowing = 0;
        BlockPos first = null;
        for (int x = box.minX(); x <= box.maxX(); x++)
        {
            for (int z = box.minZ(); z <= box.maxZ(); z++)
            {
                if (!plan.manages(x, z))
                {
                    continue;
                }
                for (int y = box.minY(); y <= box.maxY(); y++)
                {
                    FluidState fluid = level.getFluidState(pos.set(x, y, z));
                    if (!fluid.is(FluidTags.LAVA))
                    {
                        continue;
                    }
                    lava++;
                    if (!fluid.isSource() && flowing++ == 0)
                    {
                        first = pos.immutable();
                    }
                }
            }
        }
        if (flowing > 0 || lava != plan.lavaBlocks())
        {
            throw new GameTestAssertException("the volcano has " + lava + " lava blocks (" + plan.lavaBlocks() + " planned), "
                    + flowing + " of them flowing (first at " + (first == null ? "-" : first.toShortString()) + ")");
        }
    }

    /** The rim stands about its planned height. */
    static void checkRim(ServerLevel level, EmberVolcano.Plan plan)
    {
        BlockPos ground = plan.ground();
        int highest = Integer.MIN_VALUE;
        for (int dx = -20; dx <= 20; dx++)
        {
            for (int dz = -20; dz <= 20; dz++)
            {
                highest = Math.max(highest, level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, ground.getX() + dx,
                        ground.getZ() + dz) - 1);
            }
        }
        if (highest < plan.rimY() - RIM_SLACK)
        {
            throw new GameTestAssertException("the volcano's top is at y " + highest + ", its rim should stand at about "
                    + plan.rimY());
        }
    }

    /**
     * The Emberdactyl's perch (the user's second fight: perched on the jagged rim, its body went through the rock): solid
     * ledge under its feet, and nothing solid where its perched body reaches (measured from its model: 6.9 either side,
     * 6 behind and 4 in front of its feet, 15.4 up) or its neck and head held out in front (4.8 either side, 5 to 15 in
     * front, 8 to 14 up).
     */
    static void checkPerch(ServerLevel level, EmberVolcano.Plan plan)
    {
        Vec3 feet = plan.perch();
        double yaw = Math.toRadians(plan.perchYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        for (double along = -2.0D; along <= 2.0D; along += 1.0D)
        {
            for (double across = -6.0D; across <= 6.0D; across += 1.0D)
            {
                BlockPos under = BlockPos.containing(feet.x + fx * along - fz * across, feet.y - 0.5D, feet.z + fz * along + fx * across);
                if (!Showcases.isSolidGround(level, under))
                {
                    throw new GameTestAssertException("the Emberdactyl's perch has no ledge under it at " + under.toShortString()
                            + " (" + level.getBlockState(under) + ")");
                }
            }
        }
        checkClear(level, feet, fx, fz, -6.0D, 4.0D, 6.9D, 0.2D, 15.4D, "body");
        checkClear(level, feet, fx, fz, 5.0D, 15.0D, 4.8D, 8.0D, 14.0D, "head");
    }

    private static void checkClear(ServerLevel level, Vec3 feet, double fx, double fz, double from, double to, double side, double low,
            double high, String what)
    {
        for (double along = from; along <= to; along += 0.5D)
        {
            for (double across = -side; across <= side; across += 0.5D)
            {
                for (double up = low; up <= high; up += 0.5D)
                {
                    BlockPos pos = BlockPos.containing(feet.x + fx * along - fz * across, feet.y + up, feet.z + fz * along + fx * across);
                    if (!level.getBlockState(pos).getCollisionShape(level, pos).isEmpty())
                    {
                        throw new GameTestAssertException("the perched Emberdactyl's " + what + " would be inside "
                                + level.getBlockState(pos) + " at " + pos.toShortString() + " (" + String.format("%.1f", along)
                                + " in front of its feet, " + String.format("%.1f", across) + " to its side, "
                                + String.format("%.1f", up) + " up)");
                    }
                }
            }
        }
    }

    /** The path from its foot to the lookout: ground under every step, room to walk, a block's climb at most. */
    static void checkPath(ServerLevel level, EmberVolcano.Plan plan)
    {
        List<BlockPos> line = plan.pathLine();
        BlockPos before = null;
        for (BlockPos step : line)
        {
            if (!Showcases.isSolidGround(level, step.below()) || !walkable(level, step) || !walkable(level, step.above()))
            {
                throw new GameTestAssertException("the volcano's path is blocked at " + step.toShortString() + " (under: "
                        + level.getBlockState(step.below()) + ", at: " + level.getBlockState(step) + ", over: "
                        + level.getBlockState(step.above()) + ")");
            }
            if (before != null && (Math.abs(step.getY() - before.getY()) > 1 || Math.abs(step.getX() - before.getX()) > 1
                    || Math.abs(step.getZ() - before.getZ()) > 1))
            {
                throw new GameTestAssertException("the volcano's path jumps from " + before.toShortString() + " to "
                        + step.toShortString());
            }
            before = step;
        }
        if (before == null || before.getY() < plan.rimY() - 8)
        {
            throw new GameTestAssertException("the volcano's path ends at " + (before == null ? "nothing" : before.toShortString())
                    + ", below the rim (y " + plan.rimY() + ")");
        }
    }

    private static boolean walkable(ServerLevel level, BlockPos pos)
    {
        BlockState state = level.getBlockState(pos);
        return state.getFluidState().isEmpty() && state.getCollisionShape(level, pos).isEmpty();
    }

    /** The egg in the nest (bones and charred logs round it) and the adult over it, where they were put. */
    private static void checkNest(ServerLevel level, EmberVolcano.Plan plan, BlockPos column, double galleryEnd)
    {
        FireCandidate egg = null;
        FireCandidate adult = null;
        for (FireCandidate candidate : found(level, column))
        {
            if (candidate.getZ() < galleryEnd)
            {
                continue;
            }
            if (candidate.getDesign().label().equals(FireShowcase.NEST_EGG))
            {
                egg = candidate;
            }
            else if (candidate.getDesign().label().equals(FireShowcase.HOVERING_ADULT))
            {
                adult = candidate;
            }
            else
            {
                throw new GameTestAssertException("No. " + candidate.getDesign().label() + " stands at the volcano");
            }
        }
        if (egg == null || egg.position().distanceTo(plan.eggFeet()) > TOLERANCE)
        {
            throw new GameTestAssertException("the egg isn't in the nest at " + plan.eggFeet() + " (" + (egg == null ? "none"
                    : "it's at " + egg.position()) + ")");
        }
        if (adult == null || adult.position().distanceTo(FireShowcase.hoverFeet(plan)) > TOLERANCE)
        {
            throw new GameTestAssertException("the adult isn't hovering at " + FireShowcase.hoverFeet(plan) + " (" + (adult == null ? "none"
                    : "it's at " + adult.position()) + ")");
        }
        BlockPos under = BlockPos.containing(plan.eggFeet()).below();
        if (!Showcases.isSolidGround(level, under))
        {
            throw new GameTestAssertException("the egg doesn't stand on the nest (" + level.getBlockState(under) + ")");
        }
        int reach = (int) Math.ceil(com.reg21meme.dross.boss.emberdactyl.EmberVolcanoTuning.NEST_RING_OUTER) + 1;
        int ring = 0;
        for (int dx = -reach; dx <= reach; dx++)
        {
            for (int dz = -reach; dz <= reach; dz++)
            {
                for (int dy = 1; dy <= 2; dy++)
                {
                    BlockState state = level.getBlockState(under.offset(dx, dy, dz));
                    if (state.is(Blocks.BONE_BLOCK) || state.is(com.reg21meme.dross.registry.ModBlocks.CHARRED_LOG.get()))
                    {
                        ring++;
                    }
                }
            }
        }
        if (ring < NEST_RING_MIN)
        {
            throw new GameTestAssertException("the nest has only " + ring + " bones and charred logs round the egg");
        }
    }
}
