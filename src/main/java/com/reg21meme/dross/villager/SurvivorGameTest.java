package com.reg21meme.dross.villager;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.world.PortalSiteGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.monster.Skeleton;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless tests (GameTests) for the hub's two survivors ({@link Survivor}). Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}. They only exist in the development environment.
 * <ul>
 *   <li>{@link #realPairAtTheHub}: the real pair spawned at the hub ({@link SurvivorSpawnData}) is there: exactly two
 *       survivors near the hub, the Blind Oracle (mentor, skin 8) and the Ragged Seeker (student, skin 3), each the
 *       other's partner, with the hub's two rest spots as their homes, inside their area (the footprint and 20 blocks
 *       round it), within 6 blocks of each other, and neither on the arrival step or in the portal. (In a world that's
 *       been played in, they could be walking apart at that moment; the check is meant for fresh and copied worlds.)
 *       It keeps the hub's chunks loaded with a ticket while it looks, and always removes it at the end.</li>
 *   <li>{@link #cannotDie}: a 1000-damage hit, {@code kill()} and the void leave it alive; at half health or less it
 *       heals fully and is back on its rest spot.</li>
 *   <li>{@link #staysInItsArea}: moved out of its area it pops back within a couple of ticks (its partner too); falling
 *       more than 4 blocks pops it back before it lands (no fall damage).</li>
 *   <li>{@link #walksTogether}: started 12 blocks apart, they walk to within 3 blocks of each other within 200 ticks,
 *       then stay within 6 for another 200, without popping back.</li>
 *   <li>{@link #hostileMobsIgnoreThem}: a zombie or skeleton told to target one doesn't; one "hurt" by it doesn't go for
 *       it either, and an angry piglin (the brain's way of picking a target) never gets it as its attack target.</li>
 *   <li>{@link #rightClickSaysALine}: right-clicking says the next line in chat with its name as the speaker, in order;
 *       the off hand and sneaking do nothing; no menu opens.</li>
 * </ul>
 * The test-made survivors live in a closed test arena (floor, barrier walls and roof) and never touch the saved data;
 * they're removed with {@code discard()} at the end (they can't be killed).
 *
 * <p>Waiting for chunks to load (the hub's, or a test's arena) is measured in real time, at most
 * {@link #LOAD_TIMEOUT_MILLIS}: loading is background work, while the test server runs its ticks as fast as it can (it
 * doesn't wait 50 ms between them), so on a busy computer hundreds of ticks can pass first. Every wait ends by itself
 * (once it has what it waits for, at that deadline, or after a set number of ticks), well before the test's own tick
 * limit, which would end the test on the spot. So the step after each wait always runs, and it cleans up whether the
 * test passed or failed: the hub's chunk ticket, the test's mobs and its stand-in player.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class SurvivorGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "dross_survivor";

    // ---- The arena: a smooth stone floor inside barrier walls under a barrier roof, made in memory ----
    private static final String ARENA = "survivor_test_arena";
    private static final ResourceLocation ARENA_ID = new ResourceLocation(Dross.MODID, ARENA);
    /** Width and depth, walls included (the floor inside is 21 x 21). */
    private static final int ARENA_SIZE = 23;
    /** Layers: the floor (0), air with the walls round it, the roof (the last). */
    private static final int ARENA_LAYERS = 14;
    /** The test's structure block is at relative y 0, so the template's floor (layer 0) is at relative y 1... */
    private static final int FLOOR_Y = 1;
    /** ...they stand at relative y 2... */
    private static final int STAND_Y = FLOOR_Y + 1;
    /** ...and the roof is at this relative y. */
    private static final int ROOF_Y = FLOOR_Y + ARENA_LAYERS - 1;
    /** The inside of the walls, relative X and Z. */
    private static final int INSIDE_MIN = 1;
    private static final int INSIDE_MAX = ARENA_SIZE - 2;

    // ---- Waiting for chunks to load (a test's arena, or the hub's chunks and the pair) ----
    /**
     * The longest a test waits for chunks to load, in real time. Loading is background work (making or reading the
     * chunks, then their entities), while the test server runs its ticks as fast as it can, so on a busy computer
     * hundreds of ticks can pass before it's done. It usually takes a second or two.
     */
    private static final long LOAD_TIMEOUT_MILLIS = 3 * 60 * 1000L;
    /**
     * The ticks a test may spend waiting for chunks to load, added to its own time limit: only a backstop behind
     * {@link #LOAD_TIMEOUT_MILLIS} (the test server can run several hundred ticks a second while it waits). A wait gives
     * up by itself when it gets here, so its test still cleans up.
     */
    private static final int LOAD_BACKSTOP_TICKS = 1_000_000;

    // ---- The real pair ----
    /**
     * Keeps the hub's chunks loaded while the test looks. The test renews it on every tick it waits and always removes it
     * when it's done, passed or failed; should anything ever skip that, it runs out by itself this many ticks after it
     * was last renewed.
     */
    private static final int HUB_TICKET_TICKS = 20 * 30;
    private static final TicketType<ChunkPos> HUB_TICKET = TicketType.create("dross_survivor_test", Comparator.comparingLong(ChunkPos::toLong),
            HUB_TICKET_TICKS);
    /** The pair stands this close together (blocks). */
    private static final double TOGETHER = 6.0D;

    // ---- The other tests ----
    private static final float HUGE_HIT = 1000.0F;
    /** Walking together: they start this far apart (blocks)... */
    private static final int START_APART = 12;
    /** ...are within this distance after at most WALK_TOGETHER_TICKS... */
    private static final double CLOSE = 3.0D;
    private static final int WALK_TOGETHER_TICKS = 200;
    /** ...and then stay within TOGETHER for this long. */
    private static final int STAY_TOGETHER_TICKS = 200;
    /** Moved out of its area, it's back within this many ticks. */
    private static final int POP_BACK_TICKS = 3;
    /** It's lifted this high above the floor for the fall check (more than Survivor.MAX_FALL). */
    private static final int DROP_HEIGHT = 8;
    /** Dropped, it's back within this many ticks (it has fallen more than Survivor.MAX_FALL after about 10). */
    private static final int FALL_POP_BACK_TICKS = 60;
    /** How long the hostile mobs are watched (ticks). */
    private static final int HOSTILE_WATCH_TICKS = 100;

    private SurvivorGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The real pair at the hub
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = BATCH, timeoutTicks = LOAD_BACKSTOP_TICKS + 100)
    public static void realPairAtTheHub(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        SurvivorSpawnData data = SurvivorSpawnData.get(server.overworld());
        UUID mentorId = data.getMentorId();
        UUID studentId = data.getStudentId();
        if (!data.hasSpawned() || mentorId == null || studentId == null)
        {
            throw new GameTestAssertException("the hub's survivors were never spawned (see the log: did the hub have a layout?)");
        }
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("the Dross dimension isn't loaded");
        }
        DrossHub.Layout layout = DrossHub.layout(server).orElseThrow(() -> new GameTestAssertException("the hub has no layout"));
        BoundingBox area = layout.footprint().inflatedBy(SurvivorGrounds.AREA_MARGIN);
        // From here on the ticket keeps the hub's chunks loaded; the step after the wait always removes it.
        HubTicket ticket = HubTicket.add(dross, area);
        LoadWait wait = new LoadWait(helper);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    ticket.renew(); // so it never runs out while the test waits, however many ticks that takes
                    wait.check(pairStillLoading(dross, area, mentorId, studentId));
                })
                .thenExecute(() -> {
                    try
                    {
                        LOGGER.info("[survivor test] Waited {} for the hub's chunks and the pair to load", wait.took());
                        wait.failIfStillMissing();
                        checkRealPair(dross, layout, area, (Survivor) dross.getEntity(mentorId), (Survivor) dross.getEntity(studentId));
                    }
                    finally
                    {
                        ticket.remove();
                    }
                })
                .thenSucceed();
    }

    private static void checkRealPair(ServerLevel dross, DrossHub.Layout layout, BoundingBox area, Survivor mentor, Survivor student)
    {
        List<String> problems = new ArrayList<>();
        // Roles, skins and partners.
        expect(problems, mentor.getRole() == SurvivorRole.MENTOR, "the saved mentor is a " + mentor.getRole());
        expect(problems, student.getRole() == SurvivorRole.STUDENT, "the saved student is a " + student.getRole());
        expect(problems, mentor.getRole().skinNumber() == TraderSkins.BLIND_ORACLE
                && mentor.getRole().skin().texture().equals(TraderSkins.byNumber(8).texture()), "the Blind Oracle doesn't wear skin 8");
        expect(problems, student.getRole().skinNumber() == TraderSkins.RAGGED_SEEKER
                && student.getRole().skin().texture().equals(TraderSkins.byNumber(3).texture()), "the Ragged Seeker doesn't wear skin 3");
        expect(problems, student.getUUID().equals(mentor.getPartnerId()) && mentor.getUUID().equals(student.getPartnerId()),
                "they aren't each other's partners");
        // Their home: the layout's rest spots and area.
        List<BlockPos> rest = layout.restSpots();
        expect(problems, rest.size() == 2 && rest.get(0).equals(mentor.getRestSpot()) && rest.get(1).equals(student.getRestSpot()),
                "their rest spots are " + mentor.getRestSpot() + " and " + student.getRestSpot() + ", not the hub's " + rest);
        for (Survivor survivor : List.of(mentor, student))
        {
            String who = survivor.getRole().id();
            BlockPos at = survivor.blockPosition();
            BoundingBox own = survivor.getArea();
            expect(problems, own != null && own.equals(area), "the " + who + "'s area is " + own + ", not the footprint and "
                    + SurvivorGrounds.AREA_MARGIN + " blocks round it " + area);
            expect(problems, area.isInside(at), "the " + who + " is at " + at.toShortString() + ", outside its area");
            SurvivorGrounds.Clearance clearance = survivor.getClearance();
            expect(problems, clearance != null && !clearance.keepsWalkingOff(at.getX(), at.getY(), at.getZ())
                    && !BlockPos.containing(layout.arrival()).equals(at), "the " + who + " is at " + at.toShortString()
                    + ", on the arrival step, in the portal or by the arrival spot");
            expect(problems, survivor.isAlive() && survivor.getHealth() > 0.0F, "the " + who + " isn't alive");
        }
        double apart = mentor.distanceTo(student);
        expect(problems, apart <= TOGETHER, "they're " + String.format("%.1f", apart) + " blocks apart, more than " + TOGETHER);
        // Exactly two survivors at the hub (they were never spawned twice).
        List<Survivor> all = dross.getEntitiesOfClass(Survivor.class, AABB.of(area));
        expect(problems, all.size() == 2, "there are " + all.size() + " survivors at the hub, not 2");
        fail(problems);
        LOGGER.info("[survivor test] At the hub ({}): the Blind Oracle at {} (rest spot {}) and the Ragged Seeker at {} (rest spot {}), "
                        + "{} blocks apart; area {}; {} places to walk to",
                layout.kind(), mentor.blockPosition().toShortString(), mentor.getRestSpot().toShortString(),
                student.blockPosition().toShortString(), student.getRestSpot().toShortString(), String.format("%.1f", apart), area,
                mentor.getWaypoints().size());
    }

    /**
     * The test's ticket on the hub's chunks: a square round the hub's center, big enough to load the whole area fully.
     * Adding a ticket that's already there just resets its age, which is how {@link #renew} keeps it from running out.
     */
    private record HubTicket(ServerLevel level, ChunkPos center, int radius)
    {
        static HubTicket add(ServerLevel level, BoundingBox area)
        {
            ChunkPos center = new ChunkPos(DrossHub.CENTER);
            HubTicket ticket = new HubTicket(level, center, ticketRadius(area, center));
            ticket.renew();
            return ticket;
        }

        void renew()
        {
            this.level.getChunkSource().addRegionTicket(HUB_TICKET, this.center, this.radius, this.center);
        }

        /** Safe to call twice. */
        void remove()
        {
            this.level.getChunkSource().removeRegionTicket(HUB_TICKET, this.center, this.radius, this.center);
        }
    }

    /** Null once every chunk of the area has loaded its entities and both survivors are there; otherwise what's missing. */
    @Nullable
    private static String pairStillLoading(ServerLevel dross, BoundingBox area, UUID mentorId, UUID studentId)
    {
        String chunk = stillLoading(dross, area);
        if (chunk != null)
        {
            return chunk;
        }
        if (!(dross.getEntity(mentorId) instanceof Survivor) || !(dross.getEntity(studentId) instanceof Survivor))
        {
            return "the hub's survivors haven't loaded yet";
        }
        return null;
    }

    /** The ticket radius (chunks) that makes every chunk of the area fully loaded round {@code center}. */
    private static int ticketRadius(BoundingBox area, ChunkPos center)
    {
        int far = 2;
        for (int x : new int[] {SectionPos.blockToSectionCoord(area.minX()), SectionPos.blockToSectionCoord(area.maxX())})
        {
            for (int z : new int[] {SectionPos.blockToSectionCoord(area.minZ()), SectionPos.blockToSectionCoord(area.maxZ())})
            {
                far = Math.max(far, Math.max(Math.abs(x - center.x), Math.abs(z - center.z)));
            }
        }
        return Math.min(far, 10);
    }

    /** Null once every chunk of the area has loaded its entities; otherwise which chunk hasn't. */
    @Nullable
    private static String stillLoading(ServerLevel level, BoundingBox area)
    {
        for (int x = SectionPos.blockToSectionCoord(area.minX()); x <= SectionPos.blockToSectionCoord(area.maxX()); x++)
        {
            for (int z = SectionPos.blockToSectionCoord(area.minZ()); z <= SectionPos.blockToSectionCoord(area.maxZ()); z++)
            {
                if (!level.areEntitiesLoaded(ChunkPos.asLong(x, z)))
                {
                    return "the hub's chunk " + x + ", " + z + " hasn't loaded its entities yet";
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // Can't die
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = LOAD_BACKSTOP_TICKS + 40)
    public static void cannotDie(GameTestHelper helper)
    {
        if (waitForArena(helper, SurvivorGameTest::cannotDie))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Survivor survivor = spawn(helper, SurvivorRole.MENTOR, new BlockPos(5, STAND_Y, 5), new BlockPos(9, STAND_Y, 9), insideArea(helper));
        BlockPos away = helper.absolutePos(new BlockPos(9, STAND_Y, 9));
        helper.startSequence()
                .thenExecute(() -> {
                    try
                    {
                        List<String> problems = new ArrayList<>();
                        float max = survivor.getMaxHealth();

                        survivor.hurt(level.damageSources().generic(), HUGE_HIT);
                        expect(problems, survivor.isAlive() && !survivor.isRemoved(), "a " + HUGE_HIT + "-damage hit killed it");
                        expect(problems, survivor.getHealth() == max, "after the huge hit its health is " + survivor.getHealth() + ", not " + max);
                        expectAtRest(problems, survivor, "after the huge hit");

                        survivor.invulnerableTime = 0;
                        survivor.kill();
                        expect(problems, survivor.isAlive() && !survivor.isRemoved(), "kill() killed it");

                        survivor.invulnerableTime = 0;
                        survivor.teleportTo(away.getX() + 0.5D, away.getY(), away.getZ() + 0.5D);
                        survivor.setHealth(max / 2.0F + 2.0F);
                        survivor.hurt(level.damageSources().generic(), 3.0F);
                        expect(problems, survivor.getHealth() == max, "hit down to half health it has " + survivor.getHealth() + ", not " + max);
                        expectAtRest(problems, survivor, "after a hit down to half health");

                        survivor.invulnerableTime = 0;
                        survivor.hurt(level.damageSources().fellOutOfWorld(), HUGE_HIT);
                        expect(problems, survivor.isAlive() && !survivor.isRemoved(), "the void's damage killed it");
                        fail(problems);
                        LOGGER.info("[survivor test] A survivor took a {}-damage hit, kill(), a hit to half health and the void's damage: alive, "
                                + "healed and back on its rest spot ({} pops)", HUGE_HIT, survivor.timesPoppedBack());
                    }
                    finally
                    {
                        survivor.discard();
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Stays in its area
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = LOAD_BACKSTOP_TICKS + POP_BACK_TICKS + FALL_POP_BACK_TICKS + 40)
    public static void staysInItsArea(GameTestHelper helper)
    {
        if (waitForArena(helper, SurvivorGameTest::staysInItsArea))
        {
            return;
        }
        // The area is the arena's west part (X 1..15), so the east part (X 16..21) is outside it but still in the arena.
        BoundingBox area = BoundingBox.fromCorners(helper.absolutePos(new BlockPos(INSIDE_MIN, STAND_Y, INSIDE_MIN)),
                helper.absolutePos(new BlockPos(15, ROOF_Y - 1, INSIDE_MAX)));
        Survivor mentor = spawn(helper, SurvivorRole.MENTOR, new BlockPos(3, STAND_Y, 3), new BlockPos(3, STAND_Y, 3), area);
        Survivor student = spawn(helper, SurvivorRole.STUDENT, new BlockPos(4, STAND_Y, 3), new BlockPos(4, STAND_Y, 3), area);
        pair(mentor, student);
        BlockPos outside = helper.absolutePos(new BlockPos(18, STAND_Y, 10));
        BlockPos high = helper.absolutePos(new BlockPos(8, STAND_Y + DROP_HEIGHT, 8));
        int[] before = new int[2];
        long[] droppedAt = {-1L}; // the tick it's dropped at (-1 until then)
        helper.startSequence()
                .thenExecute(() -> checked(List.of(mentor, student), () -> {
                    before[0] = mentor.timesPoppedBack();
                    before[1] = student.timesPoppedBack();
                    mentor.teleportTo(outside.getX() + 0.5D, outside.getY(), outside.getZ() + 0.5D);
                }))
                .thenExecuteAfter(POP_BACK_TICKS, () -> checked(List.of(mentor, student), () -> {
                    List<String> problems = new ArrayList<>();
                    expect(problems, mentor.timesPoppedBack() > before[0], "moved out of its area, it didn't pop back within " + POP_BACK_TICKS + " ticks");
                    expectAtRest(problems, mentor, "moved out of its area");
                    expect(problems, student.timesPoppedBack() > before[1], "its partner didn't pop back with it");
                    expectAtRest(problems, student, "its partner, after it was moved out of its area");
                    fail(problems);
                    before[0] = mentor.timesPoppedBack();
                    before[1] = student.timesPoppedBack();
                    student.teleportTo(high.getX() + 0.5D, high.getY(), high.getZ() + 0.5D);
                    student.fallDistance = 0.0F;
                    droppedAt[0] = helper.getTick();
                }))
                // If that failed, the test ends here: in the tick a step fails, the framework still runs the next ones,
                // and if its partner had popped back the wait below would end at once and the last step would run too.
                .thenIdle(1)
                .thenWaitUntil(() -> {
                    // Ends once it has popped back, or FALL_POP_BACK_TICKS after the drop (then the next step fails and cleans up).
                    boolean waitedEnough = droppedAt[0] >= 0 && helper.getTick() - droppedAt[0] >= FALL_POP_BACK_TICKS;
                    if (student.timesPoppedBack() <= before[1] && !waitedEnough)
                    {
                        helper.fail("dropped " + DROP_HEIGHT + " blocks, it hasn't popped back yet");
                    }
                })
                .thenExecute(() -> checked(List.of(mentor, student), () -> {
                    if (student.timesPoppedBack() <= before[1])
                    {
                        helper.fail("dropped " + DROP_HEIGHT + " blocks, it didn't pop back within " + FALL_POP_BACK_TICKS + " ticks");
                    }
                    List<String> problems = new ArrayList<>();
                    expectAtRest(problems, student, "after falling");
                    expect(problems, student.getHealth() == student.getMaxHealth(), "it took fall damage (health " + student.getHealth() + ")");
                    expect(problems, student.fallDistance == 0.0F, "its fall distance wasn't reset");
                    expect(problems, mentor.timesPoppedBack() > before[0], "its partner didn't pop back with it after its fall");
                    fail(problems);
                    LOGGER.info("[survivor test] Moved out of its area, a survivor popped back at once (its partner too); dropped {} blocks, it "
                            + "popped back before landing, unhurt", DROP_HEIGHT);
                    mentor.discard();
                    student.discard();
                }))
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Walks together
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = LOAD_BACKSTOP_TICKS + WALK_TOGETHER_TICKS + STAY_TOGETHER_TICKS + 100)
    public static void walksTogether(GameTestHelper helper)
    {
        if (waitForArena(helper, SurvivorGameTest::walksTogether))
        {
            return;
        }
        BoundingBox area = insideArea(helper);
        int z = 11;
        int mentorX = 5;
        Survivor mentor = spawn(helper, SurvivorRole.MENTOR, new BlockPos(2, STAND_Y, 2), new BlockPos(mentorX, STAND_Y, z), area);
        Survivor student = spawn(helper, SurvivorRole.STUDENT, new BlockPos(3, STAND_Y, 2), new BlockPos(mentorX + START_APART, STAND_Y, z), area);
        pair(mentor, student);
        long[] ticks = new long[2];
        double[] furthest = new double[1];
        helper.startSequence()
                .thenExecute(() -> ticks[0] = helper.getTick())
                .thenWaitUntil(() -> {
                    // Ends once they're close, or after WALK_TOGETHER_TICKS (then the next step fails and cleans up).
                    if (mentor.distanceTo(student) > CLOSE && helper.getTick() - ticks[0] < WALK_TOGETHER_TICKS)
                    {
                        helper.fail("they're still " + String.format("%.1f", mentor.distanceTo(student)) + " blocks apart");
                    }
                })
                .thenExecute(() -> checked(List.of(mentor, student), () -> {
                    ticks[1] = helper.getTick();
                    double apart = mentor.distanceTo(student);
                    if (apart > CLOSE || ticks[1] - ticks[0] > WALK_TOGETHER_TICKS)
                    {
                        helper.fail("they didn't get within " + CLOSE + " blocks of each other within " + WALK_TOGETHER_TICKS + " ticks (after "
                                + (ticks[1] - ticks[0]) + " ticks they're " + String.format("%.1f", apart) + " blocks apart)");
                    }
                }))
                // If that failed, the test ends here: in the tick a step fails, the framework still runs the next one,
                // whose message would replace it.
                .thenIdle(1)
                .thenExecuteFor(STAY_TOGETHER_TICKS, () -> checked(List.of(mentor, student), () -> {
                    double apart = mentor.distanceTo(student);
                    furthest[0] = Math.max(furthest[0], apart);
                    if (apart > TOGETHER)
                    {
                        helper.fail("they drifted " + String.format("%.1f", apart) + " blocks apart, more than " + TOGETHER);
                    }
                }))
                .thenExecute(() -> checked(List.of(mentor, student), () -> {
                    if (mentor.timesPoppedBack() != 0 || student.timesPoppedBack() != 0)
                    {
                        helper.fail("they popped back (" + mentor.timesPoppedBack() + ", " + student.timesPoppedBack() + " times) instead of walking");
                    }
                    LOGGER.info("[survivor test] Started {} blocks apart, the survivors were within {} blocks of each other after {} ticks, then "
                            + "stayed within {} for {} ticks (at most {} apart)", START_APART, CLOSE, ticks[1] - ticks[0], TOGETHER,
                            STAY_TOGETHER_TICKS, String.format("%.1f", furthest[0]));
                    mentor.discard();
                    student.discard();
                }))
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Hostile mobs ignore them
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = LOAD_BACKSTOP_TICKS + HOSTILE_WATCH_TICKS + 40)
    public static void hostileMobsIgnoreThem(GameTestHelper helper)
    {
        if (waitForArena(helper, SurvivorGameTest::hostileMobsIgnoreThem))
        {
            return;
        }
        Survivor survivor = spawn(helper, SurvivorRole.MENTOR, new BlockPos(6, STAND_Y, 11), new BlockPos(6, STAND_Y, 11), insideArea(helper));
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(14, STAND_Y, 11));
        Skeleton skeleton = helper.spawn(EntityType.SKELETON, new BlockPos(14, STAND_Y, 6));
        Piglin piglin = helper.spawn(EntityType.PIGLIN, new BlockPos(14, STAND_Y, 16));
        piglin.setImmuneToZombification(true);
        List<Mob> mobs = List.of(zombie, skeleton, piglin);
        List<Mob> everyone = List.of(survivor, zombie, skeleton, piglin);
        helper.startSequence()
                .thenExecute(() -> checked(everyone, () -> {
                    List<String> problems = new ArrayList<>();
                    zombie.setTarget(survivor);
                    expect(problems, zombie.getTarget() == null, "a zombie told to target a survivor did (" + zombie.getTarget() + ")");
                    skeleton.setTarget(survivor);
                    expect(problems, skeleton.getTarget() == null, "a skeleton told to target a survivor did");
                    fail(problems);
                    // Make them want it: "hurt" by it, and an angry piglin.
                    zombie.setLastHurtByMob(survivor);
                    skeleton.setLastHurtByMob(survivor);
                    piglin.getBrain().setMemoryWithExpiry(MemoryModuleType.ANGRY_AT, survivor.getUUID(), 600L);
                }))
                .thenExecuteFor(HOSTILE_WATCH_TICKS, () -> checked(everyone, () -> {
                    for (Mob mob : mobs)
                    {
                        if (mob.getTarget() == survivor)
                        {
                            helper.fail("a " + mob.getType().getDescription().getString() + " targets the survivor");
                        }
                        if (mob.getBrain().hasMemoryValue(MemoryModuleType.ATTACK_TARGET)
                                && mob.getBrain().getMemory(MemoryModuleType.ATTACK_TARGET).orElse(null) == survivor)
                        {
                            helper.fail("a " + mob.getType().getDescription().getString() + " has the survivor as its attack target");
                        }
                    }
                }))
                .thenExecute(() -> {
                    LOGGER.info("[survivor test] Told to target a survivor, a zombie and a skeleton didn't; after {} ticks of being \"hurt\" by it (and "
                            + "an angry piglin), none of them targeted it", HOSTILE_WATCH_TICKS);
                    everyone.forEach(Mob::discard);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Right-click
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = BATCH, timeoutTicks = LOAD_BACKSTOP_TICKS + 40)
    public static void rightClickSaysALine(GameTestHelper helper)
    {
        if (waitForArena(helper, SurvivorGameTest::rightClickSaysALine))
        {
            return;
        }
        BoundingBox area = insideArea(helper);
        Survivor mentor = spawn(helper, SurvivorRole.MENTOR, new BlockPos(6, STAND_Y, 6), new BlockPos(6, STAND_Y, 6), area);
        Survivor student = spawn(helper, SurvivorRole.STUDENT, new BlockPos(7, STAND_Y, 6), new BlockPos(7, STAND_Y, 6), area);
        pair(mentor, student);
        TestPlayer player = TestPlayer.join(helper.getLevel(), "survivor-test-player");
        helper.startSequence()
                .thenExecute(() -> {
                    try
                    {
                        List<String> problems = new ArrayList<>();
                        expectLine(problems, player, mentor, InteractionHand.MAIN_HAND, "dross.survivor.mentor.line.1", "entity.dross.survivor.blind_oracle");
                        expectLine(problems, player, mentor, InteractionHand.MAIN_HAND, "dross.survivor.mentor.line.2", "entity.dross.survivor.blind_oracle");
                        expectLine(problems, player, student, InteractionHand.MAIN_HAND, "dross.survivor.student.line.1",
                                "entity.dross.survivor.ragged_seeker");
                        expectLine(problems, player, mentor, InteractionHand.OFF_HAND, null, null);
                        player.setShiftKeyDown(true);
                        expectLine(problems, player, mentor, InteractionHand.MAIN_HAND, null, null);
                        player.setShiftKeyDown(false);
                        expectLine(problems, player, mentor, InteractionHand.MAIN_HAND, "dross.survivor.mentor.line.3", "entity.dross.survivor.blind_oracle");
                        expect(problems, player.containerMenu == player.inventoryMenu, "a menu opened: " + player.containerMenu);
                        fail(problems);
                        LOGGER.info("[survivor test] Right-clicks: the Blind Oracle said lines 1, 2 and 3 and the Ragged Seeker line 1, in chat with "
                                + "their names; the off hand and sneaking said nothing; no menu opened");
                    }
                    finally
                    {
                        player.leave();
                        mentor.discard();
                        student.discard();
                    }
                })
                .thenSucceed();
    }

    /**
     * Right-clicks with that hand. With a key, it must answer (the action is used up) with exactly one new chat line,
     * "<name> line", whose name has {@code nameKey} and whose line has {@code lineKey}; without one, nothing must happen.
     */
    private static void expectLine(List<String> problems, TestPlayer player, Survivor survivor, InteractionHand hand, @Nullable String lineKey,
                                   @Nullable String nameKey)
    {
        int before = player.messages.size();
        InteractionResult result = survivor.interact(player, hand);
        List<Component> said = player.messages.subList(before, player.messages.size());
        String what = survivor.getRole().id() + " (" + hand + (player.isShiftKeyDown() ? ", sneaking" : "") + ")";
        if (lineKey == null)
        {
            expect(problems, !result.consumesAction() && said.isEmpty(), "right-clicking the " + what + " should do nothing, but it gave "
                    + result + " and said " + said.size() + " line(s)");
            return;
        }
        expect(problems, result.consumesAction(), "right-clicking the " + what + " gave " + result);
        if (said.size() != 1)
        {
            problems.add("right-clicking the " + what + " said " + said.size() + " lines, not 1");
            return;
        }
        Component message = said.get(0);
        if (!(message.getContents() instanceof TranslatableContents chat) || !"chat.type.text".equals(chat.getKey()) || chat.getArgs().length != 2)
        {
            problems.add("the " + what + "'s line isn't a chat line with a speaker: " + message);
            return;
        }
        Object name = chat.getArgs()[0];
        Object line = chat.getArgs()[1];
        expect(problems, name instanceof Component speaker && speaker.getContents() instanceof TranslatableContents key && nameKey.equals(key.getKey()),
                "the " + what + "'s speaker is " + name + ", not " + nameKey);
        expect(problems, line instanceof Component text && text.getContents() instanceof TranslatableContents key && lineKey.equals(key.getKey()),
                "the " + what + " said " + line + ", not " + lineKey);
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** A test survivor in the arena: its rest spot and start (relative), its area (absolute). */
    private static Survivor spawn(GameTestHelper helper, SurvivorRole role, BlockPos rest, BlockPos start, BoundingBox area)
    {
        ServerLevel level = helper.getLevel();
        Survivor survivor = Survivor.create(level, role);
        if (survivor == null)
        {
            throw new GameTestAssertException("couldn't create a survivor");
        }
        survivor.setHome(helper.absolutePos(rest), 0.0F, area, List.of(), null);
        BlockPos at = helper.absolutePos(start);
        survivor.moveTo(at.getX() + 0.5D, at.getY(), at.getZ() + 0.5D, 0.0F, 0.0F);
        if (!level.addFreshEntity(survivor))
        {
            throw new GameTestAssertException("couldn't add a survivor to the level");
        }
        return survivor;
    }

    private static void pair(Survivor mentor, Survivor student)
    {
        mentor.setPartner(student.getUUID());
        student.setPartner(mentor.getUUID());
    }

    /** The whole inside of the arena, from the floor to under the roof (absolute). */
    private static BoundingBox insideArea(GameTestHelper helper)
    {
        return BoundingBox.fromCorners(helper.absolutePos(new BlockPos(INSIDE_MIN, STAND_Y, INSIDE_MIN)),
                helper.absolutePos(new BlockPos(INSIDE_MAX, ROOF_Y - 1, INSIDE_MAX)));
    }

    private static void expectAtRest(List<String> problems, Survivor survivor, String when)
    {
        BlockPos rest = survivor.getRestSpot();
        expect(problems, rest != null && survivor.blockPosition().equals(rest), when + ", it's at " + survivor.blockPosition().toShortString()
                + ", not its rest spot " + (rest == null ? "(none)" : rest.toShortString()));
    }

    /** Runs some checks; if one fails, the test's mobs are removed first, then the test fails. */
    private static void checked(List<? extends Mob> mobs, Runnable checks)
    {
        try
        {
            checks.run();
        }
        catch (RuntimeException e)
        {
            mobs.forEach(Mob::discard);
            throw e;
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
            throw new GameTestAssertException(problems.size() + " problem(s): " + String.join("; ", problems));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Waiting for chunks to load
    // ---------------------------------------------------------------------------------------------

    /**
     * A wait for chunks to load, measured in real time: it goes on while something is still missing, for at most
     * {@link #LOAD_TIMEOUT_MILLIS}, and never past tick {@link #LOAD_BACKSTOP_TICKS}. So it always ends by itself before
     * its test's tick limit (which would end the test on the spot, skipping its clean-up), and the step after it runs.
     */
    private static final class LoadWait
    {
        private final GameTestHelper helper;
        private final long startMillis = Util.getMillis();
        private final long startTick;
        @Nullable
        private String missing;

        LoadWait(GameTestHelper helper)
        {
            this.helper = helper;
            this.startTick = helper.getTick();
        }

        /** For the sequence's wait: notes what's still missing (null: nothing) and keeps the sequence waiting while it may. */
        void check(@Nullable String stillMissing)
        {
            this.missing = stillMissing;
            if (stillMissing != null && Util.getMillis() - this.startMillis < LOAD_TIMEOUT_MILLIS && this.helper.getTick() < LOAD_BACKSTOP_TICKS)
            {
                this.helper.fail(stillMissing); // keeps waiting
            }
        }

        /** After the wait: fails the test if something's still missing (the wait gave up). */
        void failIfStillMissing()
        {
            if (this.missing != null)
            {
                this.helper.fail(this.missing + " (gave up after " + this.took() + ")");
            }
        }

        /** How long it has waited, in ticks and seconds. */
        String took()
        {
            return (this.helper.getTick() - this.startTick) + " ticks (" + String.format("%.1f", (Util.getMillis() - this.startMillis) / 1000.0D) + " s)";
        }
    }

    /**
     * Makes a test wait until its arena has finished loading (like the Colossus tests: a test can start before its
     * chunks are ready): until then nothing in it moves and searches skip it. Nothing of the test exists yet while it
     * waits, so if it gives up there's nothing to clean up.
     *
     * @return true if it's still loading (the test is run again from the start later, so the caller just returns)
     */
    private static boolean waitForArena(GameTestHelper helper, Consumer<GameTestHelper> test)
    {
        if (arenaStillLoading(helper) == null)
        {
            return false;
        }
        LoadWait wait = new LoadWait(helper);
        helper.startSequence()
                .thenWaitUntil(() -> wait.check(arenaStillLoading(helper)))
                .thenExecute(() -> {
                    wait.failIfStillMissing();
                    try
                    {
                        test.accept(helper);
                    }
                    catch (GameTestAssertException failure)
                    {
                        throw failure;
                    }
                    catch (RuntimeException e)
                    {
                        GameTestAssertException failure = new GameTestAssertException("the test threw " + e);
                        failure.initCause(e);
                        throw failure;
                    }
                });
        return true;
    }

    @Nullable
    private static String arenaStillLoading(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(0, FLOOR_Y, 0));
        BlockPos opposite = helper.absolutePos(new BlockPos(ARENA_SIZE - 1, FLOOR_Y, ARENA_SIZE - 1));
        for (int x = SectionPos.blockToSectionCoord(Math.min(corner.getX(), opposite.getX())); x <= SectionPos.blockToSectionCoord(Math.max(corner.getX(), opposite.getX())); x++)
        {
            for (int z = SectionPos.blockToSectionCoord(Math.min(corner.getZ(), opposite.getZ())); z <= SectionPos.blockToSectionCoord(Math.max(corner.getZ(), opposite.getZ())); z++)
            {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.isPositionEntityTicking(chunk.getWorldPosition()) || !level.areEntitiesLoaded(chunk.toLong())
                        || !level.getChunkSource().isPositionTicking(chunk.toLong()))
                {
                    return "the arena's chunk " + x + ", " + z + " hasn't finished loading";
                }
            }
        }
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // The stand-in player (an in-memory network channel: vanilla's makeMockServerPlayerInLevel crashes under Forge)
    // ---------------------------------------------------------------------------------------------

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
            new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            return player;
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay)
        {
            messages.add(message);
            super.sendSystemMessage(message, overlay);
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

    // ---------------------------------------------------------------------------------------------
    // The arena template
    // ---------------------------------------------------------------------------------------------

    /**
     * GameTests are built on a structure template. The arena (a smooth stone floor inside barrier walls, under a barrier
     * roof: the test world is a random one and the framework clears the room deep underground, often under a cave) is
     * made in memory when a development server starts, before the tests are placed.
     */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(ARENA_ID);
        template.load(event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK), arenaTag());
    }

    private static CompoundTag arenaTag()
    {
        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(ARENA_SIZE, ARENA_LAYERS, ARENA_SIZE));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.SMOOTH_STONE.defaultBlockState()));
        palette.add(NbtUtils.writeBlockState(Blocks.BARRIER.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int x = 0; x < ARENA_SIZE; x++)
        {
            for (int z = 0; z < ARENA_SIZE; z++)
            {
                boolean wall = x == 0 || z == 0 || x == ARENA_SIZE - 1 || z == ARENA_SIZE - 1;
                for (int y = 0; y < ARENA_LAYERS; y++)
                {
                    int state;
                    if (y == 0)
                    {
                        state = 0; // the floor
                    }
                    else if (y == ARENA_LAYERS - 1 || wall)
                    {
                        state = 1; // the roof and the walls
                    }
                    else
                    {
                        continue; // air
                    }
                    CompoundTag block = new CompoundTag();
                    block.put("pos", intList(x, y, z));
                    block.putInt("state", state);
                    blocks.add(block);
                }
            }
        }
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static ListTag intList(int... values)
    {
        ListTag list = new ListTag();
        for (int value : values)
        {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }
}
