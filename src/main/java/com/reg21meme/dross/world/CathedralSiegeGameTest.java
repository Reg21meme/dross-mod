package com.reg21meme.dross.world;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.knight.ElementalKnight;
import com.reg21meme.dross.boss.knight.KnightKind;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.quest.QuestProgressData;
import com.reg21meme.dross.quest.oracle.LandFallMessage;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.entity.EntityTypeTest;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for the Fallen Cathedral's siege ({@link CathedralSiege}, {@link CathedralWard}): run with the
 * others ({@code .\gradlew.bat runGameTestServer --console=plain}). Each in its own batch (the siege is one per world, so
 * no two of them may run at once; the keeper and the ward stand down on the test server, and these drive them).
 * <ul>
 *   <li>{@link #guardsComeFromTheSeed}: for 300 seeds, five guards of five different kinds, never more than two of an
 *       element, the same when worked out again, every kind turning up, their directions spread round the cathedral; for
 *       this world's cathedral, each guard's first spot outside its footprint; the ward's shape (how deep a point is, the
 *       way out); the fall's stretches on the side nearest a player.</li>
 *   <li>{@link #siegeHoldsThenBreaks}: the cathedral's land loaded (a ticket; waited for by the clock) and tidied: a survival
 *       player just inside it is pushed out, one deep inside is sent back outside, a creative one is let be; a Portal Orb
 *       thrown into its frame is refused (the frame unlit, the orb not used, its thrower told why); a player near, its five
 *       knights come, the seed's, each on its own spot outside the footprint, apart; four killed, it still holds and none
 *       comes back; the fifth killed by a player 70 blocks off: it's broken and saved, "Siege Broken" for him (the
 *       credited) and the player near, not one far away, the player near told; the ward lets the survival player in; an
 *       orb now lights the frame. Then the frame and the world's "cathedral lit" flag are put back as they were and the
 *       siege is reset.</li>
 *   <li>{@link #commandsShowResetAndBreak}: {@code /dross siege status|reset|break}.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class CathedralSiegeGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String PLAN_BATCH = "dross_siege_plan";
    private static final String SIEGE_BATCH = "dross_siege";
    private static final String COMMANDS_BATCH = "dross_siege_commands";
    private static final TicketType<ChunkPos> TICKET = TicketType.create("dross_siege_test", Comparator.comparingLong(ChunkPos::toLong));
    /** A wait gives up after this long (real time). */
    private static final long WAIT_MILLIS = 360_000L;
    private static final int TIMEOUT_TICKS = 4_000_000;
    /** How many seeds the plan is tried for. */
    private static final int SEEDS = 300;
    /** The killer stands this far outside the footprint (beyond the credit range: he gets it as the one credited). */
    private static final int KILLER_OUT = CathedralSiege.CREDIT_RANGE + 6;
    /** Two guards' spots at least this far apart. */
    private static final double GUARDS_APART = 20.0D;
    /** A knight that just came is within this of its spot. */
    private static final double AT_HOME = 3.0D;
    /** The flags the test puts the frame back with (no neighbour reacting). */
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private CathedralSiegeGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The plan from the seed, the ward's shape, the fall's stretches (arithmetic)
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = PLAN_BATCH)
    public static void guardsComeFromTheSeed(GameTestHelper helper)
    {
        List<String> problems = new ArrayList<>();
        Set<KnightKind> seen = new HashSet<>();
        int twoOfOne = 0;
        for (int i = 0; i < SEEDS; i++)
        {
            long seed = i * 0x9E3779B97F4A7C15L + 12345L;
            List<CathedralSiege.Guard> plan = CathedralSiege.plan(seed);
            String why = checkPlan(plan);
            if (why != null)
            {
                problems.add("seed " + seed + ": " + why);
                continue;
            }
            if (!plan.equals(CathedralSiege.plan(seed)))
            {
                problems.add("seed " + seed + ": worked out again, its plan differs");
            }
            plan.forEach(g -> seen.add(g.kind()));
            Map<KnightKind.Element, Integer> per = perElement(plan);
            twoOfOne += per.containsValue(2) ? 1 : 0;
        }
        expect(problems, seen.size() == KnightKind.values().length, "over " + SEEDS + " seeds only " + seen.size() + " of the "
                + KnightKind.values().length + " kinds turned up: " + seen);
        expect(problems, twoOfOne > 0, "over " + SEEDS + " seeds no plan had two of an element (it may: up to "
                + CathedralSiege.MAX_PER_ELEMENT + ")");

        // This world's cathedral: each guard's first spot is outside its footprint.
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        SiteSpot spot = SiteFinder.spot(overworld);
        if (spot == null)
        {
            throw new GameTestAssertException("the test world has no portal site");
        }
        BoundingBox foot = spot.footprint();
        List<CathedralSiege.Guard> plan = CathedralSiege.plan(overworld.getSeed());
        List<String> spots = new ArrayList<>();
        for (CathedralSiege.Guard guard : plan)
        {
            BlockPos column = CathedralSiege.column(foot, guard.angle(), CathedralSiege.GUARD_OUT);
            double off = CathedralSiege.fromFootprint(foot, column.getX() + 0.5D, column.getZ() + 0.5D);
            expect(problems, off >= CathedralSiege.GUARD_MIN_GAP, "the " + guard.kind() + "'s first spot " + column.toShortString()
                    + " is only " + (int) off + " blocks outside the footprint");
            spots.add(guard.kind() + " at " + column.getX() + " " + column.getZ());
        }

        // The ward's shape.
        BoundingBox box = CathedralWard.box(spot);
        double midZ = (foot.minZ() + foot.maxZ() + 1) / 2.0D;
        double y = spot.groundY() + 1.0D;
        Vec3 justInWest = new Vec3(foot.minX() + 1.5D, y, midZ);
        expect(problems, Math.abs(CathedralWard.depth(box, justInWest) - 1.5D) < 1.0E-6D, "1.5 blocks in from its west side is "
                + CathedralWard.depth(box, justInWest) + " deep");
        expect(problems, CathedralWard.outward(box, justInWest).x < -0.5D, "the way out from just inside its west side isn't west");
        expect(problems, CathedralWard.depth(box, new Vec3(foot.minX() - 1.0D, y, midZ)) <= 0.0D, "a block west of it counts as inside");
        expect(problems, CathedralWard.depth(box, new Vec3(foot.minX() + 1.5D, box.maxY() + 3.0D, midZ)) <= 0.0D,
                "high over its top counts as inside");
        expect(problems, CathedralWard.depth(box, new Vec3(foot.minX() + 1.5D, foot.minY() - 2.0D, midZ)) > 0.0D,
                "just under its lowest layer doesn't count as inside");
        Vec3 door = CathedralWard.door(overworld, spot);
        expect(problems, CathedralWard.depth(box, door) <= 0.0D, "the great door's spot " + door + " is inside it");

        // The fall: a player south of it sees the south side fall, along X, half a block outside, kept on that side.
        double midX = (foot.minX() + foot.maxX() + 1) / 2.0D;
        List<LandFallMessage> south = CathedralWard.stretches(foot, midX, foot.maxZ() + 20.0D);
        expect(problems, !south.isEmpty() && south.get(0).z() == foot.maxZ() + 1 && south.get(0).alongX() == 1.0F,
                "a player south of it would see " + south + ", not the south side");
        List<LandFallMessage> corner = CathedralWard.stretches(foot, foot.maxX() + 10.0D, foot.maxZ() + 10.0D);
        expect(problems, corner.size() == 2, "a player by its south-east corner would see " + corner.size() + " stretches, not 2");
        for (LandFallMessage m : south)
        {
            expect(problems, m.x() - CathedralWard.FALL_HALF_LENGTH >= foot.minX() - 1 && m.x() + CathedralWard.FALL_HALF_LENGTH <= foot.maxX() + 1,
                    "the south stretch round " + m.x() + " runs off its side");
        }
        fail(problems);
        LOGGER.info("[siege test] {} seeds: five different guards each, at most {} of an element ({} with two of one), all {} kinds "
                + "seen; this world's: {}", SEEDS, CathedralSiege.MAX_PER_ELEMENT, twoOfOne, seen.size(), spots);
        helper.succeed();
    }

    /** Null if the plan is right (five guards of different kinds, at most two of an element, spread round); else why. */
    @Nullable
    private static String checkPlan(List<CathedralSiege.Guard> plan)
    {
        if (plan.size() != CathedralSiege.GUARDS)
        {
            return plan.size() + " guards, not " + CathedralSiege.GUARDS;
        }
        if (plan.stream().map(CathedralSiege.Guard::kind).distinct().count() != CathedralSiege.GUARDS)
        {
            return "two guards of one kind: " + plan;
        }
        for (Map.Entry<KnightKind.Element, Integer> e : perElement(plan).entrySet())
        {
            if (e.getValue() > CathedralSiege.MAX_PER_ELEMENT)
            {
                return e.getValue() + " of " + e.getKey() + ": " + plan;
            }
        }
        // spread: each gap between neighbouring directions within the jitter of an even share
        List<Double> angles = new ArrayList<>();
        plan.forEach(g -> angles.add(((g.angle() % (Math.PI * 2.0D)) + Math.PI * 2.0D) % (Math.PI * 2.0D)));
        angles.sort(Double::compare);
        double even = 360.0D / CathedralSiege.GUARDS;
        double slack = 2.0D * CathedralSiege.ANGLE_JITTER_DEGREES + 0.01D;
        for (int i = 0; i < angles.size(); i++)
        {
            double next = i + 1 < angles.size() ? angles.get(i + 1) : angles.get(0) + Math.PI * 2.0D;
            double gap = Math.toDegrees(next - angles.get(i));
            if (gap < even - slack || gap > even + slack)
            {
                return "two neighbouring guards " + (int) gap + " degrees apart";
            }
        }
        return null;
    }

    private static Map<KnightKind.Element, Integer> perElement(List<CathedralSiege.Guard> plan)
    {
        Map<KnightKind.Element, Integer> per = new EnumMap<>(KnightKind.Element.class);
        plan.forEach(g -> per.merge(g.kind().element, 1, Integer::sum));
        return per;
    }

    // ---------------------------------------------------------------------------------------------
    // The siege at the cathedral: the ward, the frame, the knights, the fall
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = SIEGE_BATCH, timeoutTicks = TIMEOUT_TICKS)
    public static void siegeHoldsThenBreaks(GameTestHelper helper)
    {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        SiteSpot spot = SiteFinder.spot(overworld);
        if (spot == null)
        {
            throw new GameTestAssertException("the test world has no portal site");
        }
        Run run = new Run(helper, overworld, spot);
        Steps steps = new Steps(helper);
        steps.onFail(run::cleanUp);
        steps.watch();
        helper.startSequence()
                .thenExecute(() -> steps.guard(() -> {
                    CathedralSiege.reset(overworld.getServer());
                    run.wasLit = cathedralLitFlag(overworld);
                    run.hold();
                }))
                .thenWaitUntil(() -> steps.waitFor("the cathedral's land and round it", run::loaded, run::loadState))
                .thenExecute(() -> steps.guard(() -> {
                    if (!CathedralSiege.cathedralBuilt(overworld, spot))
                    {
                        throw new GameTestAssertException("there's no dross:portal_site start in the site's chunk " + spot.startChunk()
                                + " (a world whose land was made before the site came with it?)");
                    }
                }))
                .thenWaitUntil(() -> steps.waitFor("the tidy-up", () -> PortalSiteData.get(overworld).isTidied(spot.frame().corner()),
                        () -> "tidy-up finished: " + SiteTidy.isFinished()))
                .thenExecute(() -> steps.guard(() -> {
                    run.players();
                    checkWard(run);
                    run.throwOrb();
                }))
                .thenIdle(6)
                .thenExecute(() -> steps.guard(() -> checkOrbRefused(run)))
                .thenWaitUntil(() -> steps.waitFor("its five knights (a player near)", () -> {
                    CathedralSiege.keep(overworld);
                    return siegeKnights(overworld).size() >= CathedralSiege.GUARDS;
                }, () -> siegeKnights(overworld).size() + " knights; " + CathedralSiege.status(overworld.getServer()).stream()
                        .map(Component::getString).toList()))
                .thenExecute(() -> steps.guard(() -> {
                    checkKnights(run);
                    killAllButTheLast(run);
                    killTheLast(run);
                    checkBroken(run);
                    run.throwOrb();
                }))
                .thenIdle(6)
                .thenExecute(() -> steps.guard(() -> {
                    checkOrbLit(run);
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    /** A survival player just inside is pushed out, deep inside sent back outside; a creative one is let be. */
    private static void checkWard(Run run)
    {
        BoundingBox foot = run.spot.footprint();
        BoundingBox box = CathedralWard.box(run.spot);
        ServerPlayer p = run.near;
        CathedralWard.forget(p.getUUID());
        p.setGameMode(GameType.SURVIVAL);
        try
        {
            Vec3 door = CathedralWard.door(run.overworld, run.spot);
            p.teleportTo(run.overworld, door.x, door.y, door.z, CathedralWard.doorYaw(), 0.0F);
            expect(CathedralWard.check(p) == CathedralWard.Bounce.NONE, "a survival player in front of its great door " + door
                    + " was bounced");
            double midZ = (foot.minZ() + foot.maxZ() + 1) / 2.0D;
            p.teleportTo(run.overworld, foot.minX() + 1.5D, run.spot.groundY() + 1.0D, midZ, 90.0F, 0.0F);
            p.setDeltaMovement(Vec3.ZERO);
            CathedralWard.Bounce pushed = CathedralWard.check(p);
            expect(pushed == CathedralWard.Bounce.PUSHED && p.getDeltaMovement().x < 0.0D, "a survival player 1.5 blocks inside its west side: "
                    + pushed + ", moving " + p.getDeltaMovement() + " (pushed out west expected)");
            double midX = (foot.minX() + foot.maxX() + 1) / 2.0D;
            p.teleportTo(run.overworld, midX, run.spot.groundY() + 1.0D, midZ, 0.0F, 0.0F);
            CathedralWard.Bounce sent = CathedralWard.check(p);
            expect(sent == CathedralWard.Bounce.SENT_BACK && CathedralWard.depth(box, p.position()) <= 0.0D, "a survival player in its middle: "
                    + sent + ", now at " + p.position() + " (sent back outside expected)");
            p.setGameMode(GameType.CREATIVE);
            p.teleportTo(run.overworld, midX, run.spot.groundY() + 1.0D, midZ, 0.0F, 0.0F);
            CathedralWard.Bounce creative = CathedralWard.check(p);
            expect(creative == CathedralWard.Bounce.NONE, "a creative player in its middle was bounced: " + creative);
        }
        finally
        {
            p.setGameMode(GameType.CREATIVE);
            run.standNear();
        }
        LOGGER.info("[siege test] Its ward: a survival player just inside was pushed out, one in its middle sent back outside, a creative "
                + "one let be");
    }

    /** The orb thrown into the frame while it holds: not used, popped out of the opening, the frame unlit, its thrower told. */
    private static void checkOrbRefused(Run run)
    {
        ItemEntity orb = run.orb;
        run.orb = null;
        expect(!lit(run), "the cathedral's frame lit while its siege holds");
        expect(orb != null && !orb.isRemoved() && orb.getItem().is(ModItems.PORTAL_ORB.get()), "the Portal Orb thrown into the frame was used "
                + "up while its siege holds");
        expect(!inOpening(run, orb.blockPosition()), "the refused orb is still in the frame's opening at " + orb.blockPosition().toShortString());
        expect(told(run.killer, CathedralSiege.KEY_FRAME_SHUT), "the orb's thrower wasn't told why (" + CathedralSiege.KEY_FRAME_SHUT + ")");
        orb.discard();
        LOGGER.info("[siege test] An orb thrown into its frame while its siege holds popped back out to {}, unused, its thrower told",
                orb.blockPosition().toShortString());
    }

    /** Its five knights: the seed's kinds, at most two of an element, each on its own spot outside the footprint, apart. */
    private static void checkKnights(Run run)
    {
        List<ElementalKnight> knights = siegeKnights(run.overworld);
        expect(knights.size() == CathedralSiege.GUARDS, knights.size() + " knights came, not " + CathedralSiege.GUARDS);
        CathedralSiegeData data = CathedralSiegeData.get(run.overworld);
        List<CathedralSiege.Guard> plan = CathedralSiege.plan(run.overworld.getSeed());
        BoundingBox foot = run.spot.footprint();
        List<BlockPos> homes = new ArrayList<>();
        for (ElementalKnight k : knights)
        {
            int slot = k.ruinSlot();
            expect(slot >= 0 && slot < CathedralSiege.GUARDS && k.getUUID().equals(data.knight(slot)), "a knight " + k.kind()
                    + " that isn't the siege's (slot " + slot + ")");
            expect(k.kind() == plan.get(slot).kind(), "knight " + (slot + 1) + " is a " + k.kind() + ", not the seed's " + plan.get(slot).kind());
            BlockPos home = data.home(slot);
            expect(home != null && k.position().distanceTo(Vec3.atBottomCenterOf(home)) <= AT_HOME, "knight " + (slot + 1) + " is at "
                    + k.blockPosition().toShortString() + ", not on its spot " + home);
            expect(CathedralSiege.fromFootprint(foot, home.getX() + 0.5D, home.getZ() + 0.5D) >= CathedralSiege.GUARD_MIN_GAP,
                    "knight " + (slot + 1) + "'s spot " + home.toShortString() + " is on or by the cathedral");
            for (BlockPos other : homes)
            {
                expect(Math.sqrt(other.distSqr(home)) >= GUARDS_APART, "two knights' spots " + other.toShortString() + " and "
                        + home.toShortString() + " are less than " + GUARDS_APART + " blocks apart");
            }
            homes.add(home);
        }
        String why = checkPlan(plan);
        expect(why == null, "this world's plan: " + why);
        LOGGER.info("[siege test] Its five knights came: {}", knights.stream().map(k -> k.kind() + " at "
                + k.blockPosition().toShortString()).toList());
    }

    /** Four killed by the player: still holding, the players near told the count, none comes back. */
    private static void killAllButTheLast(Run run)
    {
        List<ElementalKnight> knights = new ArrayList<>(siegeKnights(run.overworld));
        knights.sort(Comparator.comparingInt(ElementalKnight::ruinSlot));
        run.last = knights.remove(knights.size() - 1);
        for (ElementalKnight k : knights)
        {
            kill(k, run.killer);
        }
        CathedralSiegeData data = CathedralSiegeData.get(run.overworld);
        expect(data.fallenCount() == CathedralSiege.GUARDS - 1 && !data.broken() && CathedralSiege.holds(run.overworld.getServer()),
                "four of its knights killed: " + data.fallenCount() + " fallen, broken " + data.broken());
        expect(told(run.near, CathedralSiege.KEY_PROGRESS), "the player near wasn't told a knight fell (" + CathedralSiege.KEY_PROGRESS + ")");
        CathedralSiege.keep(run.overworld);
        expect(siegeKnights(run.overworld).size() == 1, "after four were killed " + siegeKnights(run.overworld).size()
                + " knights stand (a dead one came back?)");
    }

    /** The fifth killed by the player 70 blocks off. */
    private static void killTheLast(Run run)
    {
        kill(run.last, run.killer);
        expect(!run.last.isAlive(), "the last knight didn't die");
    }

    /** Broken and saved; "Siege Broken" for the killer and the player near, not the far one; the ward lets players in. */
    private static void checkBroken(Run run)
    {
        CathedralSiegeData data = CathedralSiegeData.get(run.overworld);
        expect(data.broken() && !CathedralSiege.holds(run.overworld.getServer()), "all five killed: the siege should be broken");
        expect(CathedralSiegeData.load(data.save(new CompoundTag())).broken(), "the broken siege doesn't save");
        expect(data.brokenBy().equals(run.killer.getGameProfile().getName()), "it was broken by " + data.brokenBy() + ", not the killer");
        expect(DrossAdvancements.has(run.killer, DrossAdvancements.SIEGE_BROKEN), "the player credited with the last kill (70 blocks off) "
                + "didn't get Siege Broken");
        expect(DrossAdvancements.has(run.near, DrossAdvancements.SIEGE_BROKEN), "the player near didn't get Siege Broken");
        expect(!DrossAdvancements.has(run.far, DrossAdvancements.SIEGE_BROKEN), "a player far away got Siege Broken");
        expect(told(run.near, CathedralSiege.KEY_BROKEN) && told(run.killer, CathedralSiege.KEY_BROKEN), "the players near and the killer "
                + "weren't told the spell broke (" + CathedralSiege.KEY_BROKEN + ")");
        expect(!told(run.far, CathedralSiege.KEY_BROKEN), "a player far away was told the spell broke");
        ServerPlayer p = run.near;
        p.setGameMode(GameType.SURVIVAL);
        try
        {
            BoundingBox foot = run.spot.footprint();
            p.teleportTo(run.overworld, foot.minX() + 1.5D, run.spot.groundY() + 1.0D, (foot.minZ() + foot.maxZ() + 1) / 2.0D, 90.0F, 0.0F);
            CathedralWard.Bounce bounce = CathedralWard.check(p);
            expect(bounce == CathedralWard.Bounce.NONE, "the siege broken, a survival player inside it was still bounced: " + bounce);
        }
        finally
        {
            p.setGameMode(GameType.CREATIVE);
            run.standNear();
        }
        LOGGER.info("[siege test] All five killed: broken and saved, Siege Broken for the killer and the player near, not the far one");
    }

    /** The siege broken: the orb lights the frame. */
    private static void checkOrbLit(Run run)
    {
        expect(lit(run), "the siege broken, a Portal Orb thrown into the frame didn't light it");
        expect(run.orb == null || run.orb.isRemoved(), "the orb that lit the frame wasn't used up");
        LOGGER.info("[siege test] The siege broken, an orb lit the frame");
    }

    // ---------------------------------------------------------------------------------------------
    // The commands
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = COMMANDS_BATCH)
    public static void commandsShowResetAndBreak(GameTestHelper helper)
    {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        if (!SiteFinder.available(overworld))
        {
            throw new GameTestAssertException("the test world has no portal site");
        }
        TestPlayer player = TestPlayer.join(overworld, "siege-commands");
        try
        {
            BlockPos at = helper.absolutePos(BlockPos.ZERO).above(2);
            player.teleportTo(overworld, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
            int generation = CathedralSiegeData.get(overworld).generation();
            expect(run(player, "dross siege reset") == 1 && CathedralSiege.holds(overworld.getServer())
                    && CathedralSiegeData.get(overworld).generation() == generation + 1, "/dross siege reset didn't start it again");
            player.messages.clear();
            expect(run(player, "dross siege status") == 1 && said(player, "holds"), "/dross siege status didn't say it holds: "
                    + player.messages.stream().map(Component::getString).toList());
            expect(run(player, "dross siege break") == 1 && CathedralSiege.isBroken(overworld.getServer())
                    && CathedralSiegeData.get(overworld).brokenBy().equals("/dross siege break"), "/dross siege break didn't break it");
            expect(!DrossAdvancements.has(player, DrossAdvancements.SIEGE_BROKEN), "/dross siege break gave Siege Broken");
            expect(run(player, "dross siege break") == 0, "/dross siege break on a broken siege didn't fail");
            player.messages.clear();
            expect(run(player, "dross siege status") == 1 && said(player, "broken"), "/dross siege status didn't say it's broken: "
                    + player.messages.stream().map(Component::getString).toList());
            expect(run(player, "dross siege reset") == 1 && CathedralSiege.holds(overworld.getServer()), "/dross siege reset didn't make it hold again");
        }
        finally
        {
            player.leave();
        }
        LOGGER.info("[siege test] /dross siege status, reset and break work");
        helper.succeed();
    }

    private static int run(ServerPlayer player, String command)
    {
        return player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2), command);
    }

    private static boolean said(TestPlayer player, String words)
    {
        return player.messages.stream().anyMatch(m -> m.getString().contains(words));
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    private static List<ElementalKnight> siegeKnights(ServerLevel overworld)
    {
        return new ArrayList<>(overworld.getEntities(EntityTypeTest.forClass(ElementalKnight.class),
                k -> CathedralSiege.isSiegeKnight(k) && k.isAlive()));
    }

    /** A blow from the player no knight survives (so the player is credited). */
    private static void kill(ElementalKnight knight, ServerPlayer by)
    {
        knight.hurt(by.damageSources().playerAttack(by), 1.0E7F);
    }

    /** True if every cell of the cathedral's frame opening is its lit portal. */
    private static boolean lit(Run run)
    {
        for (BlockPos cell : run.openingCells())
        {
            if (!run.overworld.getBlockState(cell).is(ModBlocks.DROSS_PORTAL.get()))
            {
                return false;
            }
        }
        return true;
    }

    private static boolean inOpening(Run run, BlockPos pos)
    {
        return run.openingCells().contains(pos);
    }

    private static boolean told(@Nullable ServerPlayer player, String key)
    {
        if (!(player instanceof TestPlayer test))
        {
            return false;
        }
        for (Component message : test.messages)
        {
            if (message.getContents() instanceof TranslatableContents t && key.equals(t.getKey()))
            {
                return true;
            }
        }
        return false;
    }

    private static boolean cathedralLitFlag(ServerLevel overworld)
    {
        return QuestProgressData.get(overworld).isCathedralLit();
    }

    /**
     * Puts the world's "the cathedral's portal is lit" flag (the quest area's) back as it was before the test lit the frame,
     * so the trader's tests still see an unlit cathedral (test only: the flag has no way back in the game).
     */
    private static void setCathedralLitFlag(ServerLevel overworld, boolean lit)
    {
        QuestProgressData.get(overworld).setCathedralLitForTests(lit);
    }

    private static void expect(boolean ok, String problem)
    {
        if (!ok)
        {
            throw new GameTestAssertException(problem);
        }
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
            throw new GameTestAssertException(String.join("; ", problems));
        }
    }

    /** The siege test's land, players, orb, and what it puts back. */
    private static final class Run
    {
        final GameTestHelper helper;
        final ServerLevel overworld;
        final SiteSpot spot;
        final ChunkPos middle;
        /** The ticket's radius (chunks): the footprint, its knights' spots and the killer's, with their entities ticking. */
        final int radius;
        boolean held;
        @Nullable
        TestPlayer near;
        @Nullable
        TestPlayer killer;
        @Nullable
        TestPlayer far;
        @Nullable
        ItemEntity orb;
        @Nullable
        ElementalKnight last;
        /** The world's "cathedral lit" flag and the opening's blocks before the test (null: not taken yet). */
        @Nullable
        Boolean wasLit;
        @Nullable
        List<BlockState> openingBefore;
        boolean cleaned;

        Run(GameTestHelper helper, ServerLevel overworld, SiteSpot spot)
        {
            this.helper = helper;
            this.overworld = overworld;
            this.spot = spot;
            BoundingBox foot = spot.footprint();
            this.middle = new ChunkPos(((foot.minX() + foot.maxX()) / 2) >> 4, ((foot.minZ() + foot.maxZ()) / 2) >> 4);
            // (the knights' spots are at most 30 blocks past its edge; the ticket's last two rings aren't entity-ticking; the
            // killer stands in its loaded part, and his own tickets load round him)
            int reach = Math.max(foot.getXSpan(), foot.getZSpan()) / 2 + 30 + 16;
            this.radius = reach / 16 + 3;
        }

        void hold()
        {
            this.overworld.getChunkSource().addRegionTicket(TICKET, this.middle, this.radius, this.middle);
            this.held = true;
        }

        boolean loaded()
        {
            return this.loadState() == null;
        }

        /** Null once every chunk within the ticket's entity-ticking part is ticking with its entities. */
        @Nullable
        String loadState()
        {
            int r = this.radius - 2;
            int total = 0;
            int ready = 0;
            for (int dx = -r; dx <= r; dx++)
            {
                for (int dz = -r; dz <= r; dz++)
                {
                    total++;
                    int cx = this.middle.x + dx;
                    int cz = this.middle.z + dz;
                    if (this.overworld.getChunkSource().getChunkNow(cx, cz) != null
                            && this.overworld.isPositionEntityTicking(new BlockPos(cx * 16 + 8, this.spot.groundY(), cz * 16 + 8)))
                    {
                        ready++;
                    }
                }
            }
            return ready == total ? null : ready + " of " + total + " chunks ticking with their entities";
        }

        /** The three stand-in players, all creative (the knights leave them be): one near, the killer 70 blocks off, one far. */
        void players()
        {
            this.near = TestPlayer.join(this.overworld, "siege-near");
            this.standNear();
            BoundingBox foot = this.spot.footprint();
            this.killer = TestPlayer.join(this.overworld, "siege-killer");
            int kx = foot.minX() - KILLER_OUT;
            int kz = (foot.minZ() + foot.maxZ()) / 2;
            int ky = this.overworld.hasChunkAt(new BlockPos(kx, 0, kz))
                    ? this.overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, kx, kz) : this.spot.groundY() + 1;
            this.killer.teleportTo(this.overworld, kx + 0.5D, ky, kz + 0.5D, -90.0F, 0.0F);
            this.far = TestPlayer.join(this.overworld, "siege-far");
            BlockPos at = this.helper.absolutePos(BlockPos.ZERO).above(2);
            this.far.teleportTo(this.overworld, at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
            expect(CathedralSiege.fromFootprint(foot, this.killer.getX(), this.killer.getZ()) > CathedralSiege.CREDIT_RANGE,
                    "the killer isn't beyond the credit range");
            expect(CathedralSiege.fromFootprint(foot, this.far.getX(), this.far.getZ()) > CathedralSiege.CREDIT_RANGE * 3,
                    "the far player isn't far from the cathedral");
            for (TestPlayer p : List.of(this.near, this.killer, this.far))
            {
                DrossAdvancements.revoke(p, DrossAdvancements.SIEGE_BROKEN);
            }
        }

        /** The player near stands in front of the cathedral's great door. */
        void standNear()
        {
            Vec3 door = CathedralWard.door(this.overworld, this.spot);
            this.near.teleportTo(this.overworld, door.x, door.y, door.z, CathedralWard.doorYaw(), 0.0F);
            this.near.setDeltaMovement(Vec3.ZERO);
        }

        List<BlockPos> openingCells()
        {
            SiteFrame frame = this.spot.frame();
            Direction along = SiteFrame.positive(frame.axis());
            List<BlockPos> cells = new ArrayList<>();
            for (int i = 0; i < frame.openingWidth(); i++)
            {
                for (int j = 0; j < frame.openingHeight(); j++)
                {
                    cells.add(frame.openingMin().relative(along, i).above(j));
                }
            }
            return cells;
        }

        /** A Portal Orb dropped into the middle of the frame's opening, thrown by the killer (the opening emptied first). */
        void throwOrb()
        {
            List<BlockPos> cells = this.openingCells();
            if (this.openingBefore == null)
            {
                this.openingBefore = cells.stream().map(this.overworld::getBlockState).toList();
            }
            for (BlockPos cell : cells)
            {
                if (!this.overworld.getBlockState(cell).isAir())
                {
                    this.overworld.setBlock(cell, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState(), FLAGS);
                }
            }
            BlockPos center = this.spot.frame().openingCenter();
            ItemEntity item = new ItemEntity(this.overworld, center.getX() + 0.5D, center.getY() + 0.5D, center.getZ() + 0.5D,
                    new ItemStack(ModItems.PORTAL_ORB.get()));
            item.setDeltaMovement(Vec3.ZERO);
            item.setPickUpDelay(200);
            item.setThrower(this.killer.getUUID());
            this.overworld.addFreshEntity(item);
            this.orb = item;
        }

        /** Puts everything back (safe to call twice): the opening, the lit flag, the siege reset, the players gone, the ticket. */
        void cleanUp()
        {
            if (this.cleaned)
            {
                return;
            }
            this.cleaned = true;
            try
            {
                if (this.orb != null)
                {
                    this.orb.discard();
                }
                if (this.openingBefore != null)
                {
                    List<BlockPos> cells = this.openingCells();
                    for (int i = 0; i < cells.size(); i++)
                    {
                        this.overworld.setBlock(cells.get(i), this.openingBefore.get(i), FLAGS);
                    }
                }
                if (this.wasLit != null)
                {
                    setCathedralLitFlag(this.overworld, this.wasLit);
                }
                CathedralSiege.reset(this.overworld.getServer());
            }
            finally
            {
                for (TestPlayer p : new TestPlayer[] {this.near, this.killer, this.far})
                {
                    if (p != null)
                    {
                        CathedralWard.forget(p.getUUID());
                        p.leave();
                    }
                }
                if (this.held)
                {
                    this.overworld.getChunkSource().removeRegionTicket(TICKET, this.middle, this.radius, this.middle);
                    this.held = false;
                }
            }
        }
    }

    /** A stand-in player who keeps what he's told. */
    private static final class TestPlayer extends ServerPlayer
    {
        final List<Component> messages = new ArrayList<>();

        private TestPlayer(ServerLevel level, String name)
        {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        }

        static TestPlayer join(ServerLevel level, String name)
        {
            TestPlayer player = new TestPlayer(level, name);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection); // (packets sent to him pile up in it)
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            player.setGameMode(GameType.CREATIVE);
            if (player.serverLevel() != level)
            {
                player.teleportTo(level, player.getX(), player.getY(), player.getZ(), 0.0F, 0.0F);
            }
            return player;
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay)
        {
            this.messages.add(message);
            super.sendSystemMessage(message, overlay);
        }

        void leave()
        {
            if (this.server.getPlayerList().getPlayer(this.getUUID()) == this)
            {
                this.server.getPlayerList().remove(this);
            }
        }
    }

    /** The steps of a test: a watchdog fails it at once when a step fails (a wait never fails by itself). */
    private static final class Steps
    {
        final GameTestHelper helper;
        @Nullable
        RuntimeException failure;
        long waitingSince = -1L;
        @Nullable
        Runnable onFail;

        Steps(GameTestHelper helper)
        {
            this.helper = helper;
        }

        void onFail(Runnable cleanUp)
        {
            this.onFail = cleanUp;
        }

        void watch()
        {
            this.helper.startSequence()
                    .thenWaitUntil(() -> {
                        if (this.failure == null)
                        {
                            throw new GameTestAssertException("the test ran out of time");
                        }
                    })
                    .thenFail(() -> this.failure);
        }

        void guard(Runnable step)
        {
            if (this.failure != null)
            {
                throw this.failure;
            }
            try
            {
                step.run();
            }
            catch (Waiting w)
            {
                throw w;
            }
            catch (RuntimeException e)
            {
                LOGGER.error("[siege test] failed: {}", e.getMessage(), e);
                this.failure = e;
                if (this.onFail != null)
                {
                    try
                    {
                        this.onFail.run();
                    }
                    catch (RuntimeException cleanUp)
                    {
                        LOGGER.error("[siege test] cleaning up after the failure failed too", cleanUp);
                    }
                }
                throw e;
            }
        }

        void waitFor(String what, BooleanSupplier done, Supplier<String> state)
        {
            this.guard(() -> {
                if (done.getAsBoolean())
                {
                    this.waitingSince = -1L;
                    return;
                }
                long now = Util.getMillis();
                if (this.waitingSince < 0L)
                {
                    this.waitingSince = now;
                }
                if (now - this.waitingSince > WAIT_MILLIS)
                {
                    throw new GameTestAssertException("gave up waiting for " + what + " after " + WAIT_MILLIS / 1000L + " s (" + state.get() + ")");
                }
                throw new Waiting("waiting for " + what);
            });
        }
    }

    /** "Not yet": a wait's check that isn't done. */
    private static final class Waiting extends GameTestAssertException
    {
        Waiting(String message)
        {
            super(message);
        }
    }
}
