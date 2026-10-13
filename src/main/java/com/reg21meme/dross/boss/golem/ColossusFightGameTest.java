package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.enchant.RisenUndead;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA_WAIT_TICKS;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.FLOOR_Y;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.MIDDLE;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.joinPlayer;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.leaveAll;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.waitForArena;

/**
 * Headless tests (GameTests) for the Cinder Colossus's fight moves, after the user's play test (October 2026). Run them
 * with {@code .\gradlew.bat runGameTestServer --console=plain}.
 * <ul>
 *   <li>{@link #firstHitRoarThrowsPlayers}: its first hit makes it roar, throwing a player near it back about 15 blocks
 *       without hurting them (its minion stays put); the second hit doesn't.</li>
 *   <li>{@link #stageChangesRoarSummonAndSpew}: both stage changes roar at their roar moment (throwing the player);
 *       entering Cracking raises 4 stone golems, entering the Lava Core spews 4 lava blobs round the player, never on
 *       them.</li>
 *   <li>{@link #crackingChargeRunsToTheSpot}: a Cracking charge at a player 25 blocks away runs to where they stood,
 *       and its fire is gone after 2 to 2.5 seconds.</li>
 *   <li>{@link #coreChargeFireLastsLonger}: a Lava Core charge's fire lasts 4 to 5 seconds.</li>
 *   <li>{@link #moveChancesMatchTheTable}: the move chooser, sampled many times per stage and distance band, matches
 *       the chance tables (no rock throw in the Lava Core); moves that can't be made share out their chance; three
 *       risen undead attacking move 20 points from the swipe to the slam.</li>
 *   <li>{@link #risenUndeadBringGolems}: with 4 risen undead near, it may summon even in the Shell, a summon raises 2
 *       golems and they go for the risen undead; with 3 of them attacking up close, the slam's share rises.</li>
 * </ul>
 * The charge tests run on a long runway ({@link #RUNWAY}), made in memory like the arena.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class ColossusFightGameTest
{
    /** A long, narrow test floor for the charge (same floor, roof and height as the arena). */
    static final String RUNWAY = "colossus_test_runway";
    private static final ResourceLocation RUNWAY_ID = new ResourceLocation(Dross.MODID, RUNWAY);
    private static final int RUNWAY_LENGTH = 40;
    private static final int RUNWAY_WIDTH = 11;
    /** Where the Colossus stands on the runway (it charges along +x). */
    private static final BlockPos RUNWAY_START = new BlockPos(5, FLOOR_Y + 1, RUNWAY_WIDTH / 2);
    /** How far the player stands from it for each charge test (the far band, and mid). */
    private static final int CRACKING_CHARGE_DISTANCE = 25;
    private static final int CORE_CHARGE_DISTANCE = 20;
    /** A charge ends (its middle) this close to the spot it ran to. */
    private static final double CHARGE_END_TOLERANCE = 1.5D;
    /** Fire is checked this many ticks inside its shortest life (still burning) and past its longest (gone). */
    private static final int FIRE_MARGIN = 5;
    /** Minecraft's yaw for facing east (+X) and west (-X). */
    private static final float YAW_EAST = -90.0F;
    private static final float YAW_WEST = 90.0F;
    private static final float TEST_HIT = 10.0F;
    private static final float HUGE_HIT = 1000.0F;
    /** "About 15 blocks": the roar's throw, worked out with a player's air physics, lands within this range. */
    private static final double THROW_MIN = 13.0D;
    private static final double THROW_MAX = 17.0D;
    private static final double SPEED_TOLERANCE = 0.01D;
    /** The roar has thrown the player (sideways speed above this). */
    private static final double THROWN = 1.0D;
    /** Samples per stage and band in the chance test, and how far a share may be from the table's. */
    private static final int SAMPLES = 20000;
    private static final double SHARE_TOLERANCE = 0.02D;

    private ColossusFightGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The roar
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_colossus_roar", timeoutTicks = ARENA_WAIT_TICKS + 40)
    public static void firstHitRoarThrowsPlayers(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusFightGameTest::firstHitRoarThrowsPlayers))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        CinderColossus golem = CinderColossus.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        if (golem == null)
        {
            helper.fail("the Colossus couldn't be spawned");
            return;
        }
        ServerPlayer near = joinPlayer(helper, "roar-test-near", MIDDLE.offset(5, 0, 0), YAW_WEST);
        StoneGolem minion = GolemMinion.summon(ModEntities.STONE_GOLEM.get(), level, golem,
                helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(-4, 0, 0))), 0.0F);
        List<ServerPlayer> players = List.of(near);
        String problem = null;
        float health = near.getHealth();
        near.setDeltaMovement(Vec3.ZERO);
        golem.hurt(level.damageSources().playerAttack(near), TEST_HIT);
        Vec3 v = near.getDeltaMovement();
        double thrown = throwDistance(v);
        if (Math.abs(v.horizontalDistance() - ColossusTuning.ROAR_KNOCKBACK) > SPEED_TOLERANCE || v.x <= 0.0D
                || Math.abs(v.y - ColossusTuning.ROAR_LIFT) > SPEED_TOLERANCE)
        {
            problem = "its first hit should throw the player away from it (" + ColossusTuning.ROAR_KNOCKBACK + " sideways, "
                    + ColossusTuning.ROAR_LIFT + " up), but their speed is " + v;
        }
        else if (thrown < THROW_MIN || thrown > THROW_MAX)
        {
            problem = "the roar should throw a player about 15 blocks, but it throws them " + thrown;
        }
        else if (near.getHealth() != health)
        {
            problem = "the roar shouldn't hurt anyone, but the player lost " + (health - near.getHealth());
        }
        else if (minion == null || minion.getDeltaMovement().horizontalDistance() > SPEED_TOLERANCE)
        {
            problem = "the roar shouldn't throw its own minion";
        }
        else
        {
            near.setDeltaMovement(Vec3.ZERO);
            golem.invulnerableTime = 0;
            golem.hurt(level.damageSources().playerAttack(near), TEST_HIT);
            if (near.getDeltaMovement().horizontalDistance() > SPEED_TOLERANCE)
            {
                problem = "it should roar only at its first hit, but the second threw the player too";
            }
        }
        leaveAll(players);
        if (minion != null)
        {
            minion.discard();
        }
        golem.discard();
        if (problem != null)
        {
            helper.fail(problem);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_colossus_stage_attacks", timeoutTicks = ARENA_WAIT_TICKS + 600)
    public static void stageChangesRoarSummonAndSpew(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusFightGameTest::stageChangesRoarSummonAndSpew))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        CinderColossus golem = CinderColossus.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        if (golem == null || golem.attackGoal() == null)
        {
            helper.fail("the Colossus couldn't be spawned");
            return;
        }
        ServerPlayer player = joinPlayer(helper, "stage-roar-test", MIDDLE.offset(5, 0, 5), 0.0F);
        List<ServerPlayer> players = List.of(player);
        GolemDesigns.Design design = golem.getDesign();
        int eruptRoar = CinderColossus.ANIMATION_BLEND_TICKS + Math.round(GolemDesigns.ERUPT_BLAST * 20.0F) + 6;
        int shellRoar = CinderColossus.ANIMATION_BLEND_TICKS + design.burstTicks();
        player.setDeltaMovement(Vec3.ZERO);
        golem.hurt(level.damageSources().generic(), HUGE_HIT);
        if (golem.getAction() != GolemAction.ERUPT || player.getDeltaMovement().horizontalDistance() > SPEED_TOLERANCE)
        {
            cleanUp(helper, golem, players);
            helper.fail("a first hit that starts the eruption shouldn't roar at once (the eruption roars a moment later)");
            return;
        }
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(player.getDeltaMovement().horizontalDistance() > THROWN,
                        "the eruption hasn't roared yet"))
                .thenExecute(() -> {
                    String problem = checkRoar(golem, player, GolemAction.ERUPT, eruptRoar, "the eruption");
                    if (problem != null)
                    {
                        cleanUp(helper, golem, players);
                        helper.fail(problem);
                    }
                    player.setDeltaMovement(Vec3.ZERO);
                })
                .thenWaitUntil(() -> helper.assertTrue(golem.getPhase() == GolemPhase.ERUPTED, "still erupting"))
                .thenExecute(() -> {
                    List<GolemMinion> minions = golem.minions();
                    int stone = (int) minions.stream().filter(m -> m instanceof StoneGolem).count();
                    minions.forEach(GolemMinion::discard);
                    if (minions.size() != ColossusTuning.STAGE_CHANGE_MINIONS || stone != minions.size())
                    {
                        cleanUp(helper, golem, players);
                        helper.fail("entering the Cracking stage should raise " + ColossusTuning.STAGE_CHANGE_MINIONS
                                + " stone golems, but it raised " + minions.size() + " (" + stone + " stone)");
                    }
                    golem.invulnerableTime = 0;
                    golem.hurt(level.damageSources().generic(), HUGE_HIT);
                    if (golem.getAction() != GolemAction.BREAK_SHELL)
                    {
                        cleanUp(helper, golem, players);
                        helper.fail("a hit down to 35% should burst the shell, but it's " + golem.getAction());
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(player.getDeltaMovement().horizontalDistance() > THROWN,
                        "the shell bursting hasn't roared yet"))
                .thenExecute(() -> {
                    String problem = checkRoar(golem, player, GolemAction.BREAK_SHELL, shellRoar, "the shell bursting");
                    if (problem != null)
                    {
                        cleanUp(helper, golem, players);
                        helper.fail(problem);
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(golem.getPhase() == GolemPhase.CORE, "the shell is still bursting"))
                .thenExecute(() -> {
                    List<LavaBlob> blobs = level.getEntitiesOfClass(LavaBlob.class, golem.getBoundingBox().inflate(16.0D));
                    List<Vec3> landings = golem.attackGoal().lastLandings();
                    blobs.forEach(LavaBlob::discard); // (they'd only splash on the test's roof)
                    String problem = null;
                    if (blobs.size() != ColossusTuning.STAGE_CHANGE_LOB_BLOBS || landings.size() != blobs.size())
                    {
                        problem = "entering the Lava Core should spew " + ColossusTuning.STAGE_CHANGE_LOB_BLOBS
                                + " lava blobs, but " + blobs.size() + " are flying";
                    }
                    for (Vec3 landing : landings)
                    {
                        double d = Math.hypot(landing.x - player.getX(), landing.z - player.getZ());
                        if (d < ColossusTuning.LOB_LAND_MIN - SPEED_TOLERANCE || d > ColossusTuning.LOB_LAND_MAX + SPEED_TOLERANCE)
                        {
                            problem = "each blob should land " + ColossusTuning.LOB_LAND_MIN + " to " + ColossusTuning.LOB_LAND_MAX
                                    + " blocks from the player (never on them), but one lands " + d + " away";
                        }
                    }
                    cleanUp(helper, golem, players);
                    if (problem != null)
                    {
                        helper.fail(problem);
                    }
                })
                .thenSucceed();
    }

    /** What's wrong with a stage change's roar, or null: it came at the roar moment and threw the player properly. */
    private static String checkRoar(CinderColossus golem, ServerPlayer player, GolemAction action, int expectedTick, String what)
    {
        Vec3 v = player.getDeltaMovement();
        if (golem.getAction() != action || Math.abs(golem.ticksInAction() - expectedTick) > 1)
        {
            return what + " should roar at tick " + expectedTick + " of its animation, but it roared at "
                    + golem.ticksInAction() + " of " + golem.getAction();
        }
        if (Math.abs(v.horizontalDistance() - ColossusTuning.ROAR_KNOCKBACK) > SPEED_TOLERANCE || v.x <= 0.0D || v.z <= 0.0D
                || Math.abs(v.y - ColossusTuning.ROAR_LIFT) > SPEED_TOLERANCE)
        {
            return what + "'s roar should throw the player away from it, but their speed is " + v;
        }
        return null;
    }

    /**
     * How far (blocks, sideways) a player thrown with this speed flies and slides on flat ground with no keys pressed:
     * Minecraft's own steps (move, then gravity 0.08 and air drag 0.98 down, 0.91 sideways; on the ground the
     * slipperiness 0.6 x 0.91).
     */
    static double throwDistance(Vec3 v)
    {
        double side = v.horizontalDistance();
        double up = v.y;
        double x = 0.0D;
        double y = 0.0D;
        for (int tick = 0; tick < 200; tick++)
        {
            x += side;
            y += up;
            up = (up - 0.08D) * 0.98D;
            if (y <= 0.0D)
            {
                break;
            }
            side *= 0.91D;
        }
        side *= 0.6D * 0.91D;
        while (side > 0.003D)
        {
            x += side;
            side *= 0.6D * 0.91D;
        }
        return x;
    }

    // ---------------------------------------------------------------------------------------------
    // The charge
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = RUNWAY, batch = "dross_colossus_charge_cracking", timeoutTicks = ARENA_WAIT_TICKS + 400)
    public static void crackingChargeRunsToTheSpot(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusFightGameTest::crackingChargeRunsToTheSpot, RUNWAY_LENGTH, RUNWAY_WIDTH))
        {
            return;
        }
        charge(helper, GolemPhase.ERUPTED, CRACKING_CHARGE_DISTANCE, ColossusTuning.CRACKING_FIRE_MIN_TICKS,
                ColossusTuning.CRACKING_FIRE_MAX_TICKS, "charge-test-cracking");
    }

    @GameTest(template = RUNWAY, batch = "dross_colossus_charge_core", timeoutTicks = ARENA_WAIT_TICKS + 400)
    public static void coreChargeFireLastsLonger(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusFightGameTest::coreChargeFireLastsLonger, RUNWAY_LENGTH, RUNWAY_WIDTH))
        {
            return;
        }
        charge(helper, GolemPhase.CORE, CORE_CHARGE_DISTANCE, ColossusTuning.FIRE_TRAIL_MIN_TICKS,
                ColossusTuning.FIRE_TRAIL_MAX_TICKS, "charge-test-core");
    }

    /**
     * A charge at a player standing {@code distance} blocks down the runway: it should be a move it can make from
     * there, end with its middle on the spot where the player stood, and leave fire that's still burning a little
     * before {@code fireMin} ticks and gone a little after {@code fireMax}.
     */
    private static void charge(GameTestHelper helper, GolemPhase phase, int distance, int fireMin, int fireMax, String name)
    {
        ServerLevel level = helper.getLevel();
        CinderColossus golem = CinderColossus.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(RUNWAY_START)), YAW_EAST);
        if (golem == null || golem.attackGoal() == null)
        {
            helper.fail("the Colossus couldn't be spawned");
            return;
        }
        golem.setPhase(phase);
        golem.applyStage(golem.stage());
        ServerPlayer player = joinPlayer(helper, name, RUNWAY_START.offset(distance, 0, 0), YAW_WEST);
        List<ServerPlayer> players = List.of(player);
        Vec3 spot = player.position();
        golem.setTarget(player);
        ColossusAttackGoal goal = golem.attackGoal();
        if (!goal.available(ColossusAttackGoal.Move.CHARGE, player))
        {
            cleanUp(helper, golem, players);
            helper.fail("a player " + distance + " blocks away in plain sight should be in its charge's reach");
            return;
        }
        goal.begin(ColossusAttackGoal.Move.CHARGE, player);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(golem.getAction() != GolemAction.CHARGE, "still charging"))
                .thenExecute(() -> {
                    double off = Math.hypot(golem.getX() - spot.x, golem.getZ() - spot.z);
                    int fire = countFire(helper);
                    cleanUp(helper, golem, players);
                    if (off > CHARGE_END_TOLERANCE)
                    {
                        helper.fail("the charge should end where the player stood (" + distance + " blocks away), but it "
                                + "ended " + off + " blocks from there");
                    }
                    if (fire == 0)
                    {
                        helper.fail("a charge in the " + phase + " look should leave a trail of fire");
                    }
                })
                .thenExecuteAfter(fireMin - FIRE_MARGIN, () -> helper.assertTrue(countFire(helper) > 0,
                        "the fire trail should still burn " + (fireMin - FIRE_MARGIN) + " ticks after the charge"))
                .thenExecuteAfter(fireMax - fireMin + 2 * FIRE_MARGIN, () -> helper.assertTrue(countFire(helper) == 0,
                        "the fire trail should be out " + (fireMax + FIRE_MARGIN) + " ticks after the charge"))
                .thenSucceed();
    }

    /** Cinder fire blocks on the runway. */
    private static int countFire(GameTestHelper helper)
    {
        int count = 0;
        for (int x = 0; x < RUNWAY_LENGTH; x++)
        {
            for (int z = 0; z < RUNWAY_WIDTH; z++)
            {
                for (int y = FLOOR_Y + 1; y <= FLOOR_Y + 3; y++)
                {
                    if (helper.getBlockState(new BlockPos(x, y, z)).is(ModBlocks.CINDER_FIRE.get()))
                    {
                        count++;
                    }
                }
            }
        }
        return count;
    }

    // ---------------------------------------------------------------------------------------------
    // Choosing a move
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_colossus_chances", timeoutTicks = 20)
    public static void moveChancesMatchTheTable(GameTestHelper helper)
    {
        RandomSource random = RandomSource.create(20261013L);
        Set<ColossusAttackGoal.Move> all = EnumSet.allOf(ColossusAttackGoal.Move.class);
        for (ColossusStage stage : ColossusStage.values())
        {
            for (ColossusAttackGoal.Band band : ColossusAttackGoal.Band.values())
            {
                ColossusTuning.Chances line = table(stage).get(band.ordinal());
                EnumMap<ColossusAttackGoal.Move, Integer> expected = asMap(line);
                int sum = expected.values().stream().mapToInt(Integer::intValue).sum();
                if (sum != 100)
                {
                    helper.fail(stage + " " + band + ": the chances should add up to 100, but they add up to " + sum);
                    return;
                }
                if (stage == ColossusStage.LAVA_CORE && line.throwRock() != 0)
                {
                    helper.fail("the Lava Core should never throw rocks, but " + band + " gives it " + line.throwRock());
                    return;
                }
                String problem = sample(ColossusAttackGoal.weights(stage, band, false, false), all, expected, random,
                        stage + " " + band);
                if (problem != null)
                {
                    helper.fail(problem);
                    return;
                }
            }
        }
        // Only the moves it can make count: Shell mid with the charge on cooldown is always a throw...
        String problem = sample(ColossusAttackGoal.weights(ColossusStage.SHELL, ColossusAttackGoal.Band.MID, false, false),
                EnumSet.of(ColossusAttackGoal.Move.THROW), percent(0, 100, 0, 0, 0, 0), random, "Shell mid, no charge");
        // ...and Cracking far without the charge shares its 45 out: lob 25, throw 15, summon 15 of 55.
        if (problem == null)
        {
            problem = sample(ColossusAttackGoal.weights(ColossusStage.CRACKING, ColossusAttackGoal.Band.FAR, false, false),
                    EnumSet.of(ColossusAttackGoal.Move.LOB, ColossusAttackGoal.Move.THROW, ColossusAttackGoal.Move.SUMMON),
                    percent(0, 15, 0, 0, 25, 15), random, "Cracking far, no charge");
        }
        // Three risen undead attacking: 20 points from the swipe to the slam in the close band.
        if (problem == null)
        {
            problem = sample(ColossusAttackGoal.weights(ColossusStage.SHELL, ColossusAttackGoal.Band.CLOSE, true, false), all,
                    percent(40, 0, 60, 0, 0, 0), random, "Shell close, swarmed");
        }
        if (problem == null)
        {
            problem = sample(ColossusAttackGoal.weights(ColossusStage.CRACKING, ColossusAttackGoal.Band.CLOSE, true, false), all,
                    percent(20, 0, 45, 0, 0, 35), random, "Cracking close, swarmed");
        }
        if (problem == null)
        {
            problem = sample(ColossusAttackGoal.weights(ColossusStage.LAVA_CORE, ColossusAttackGoal.Band.CLOSE, true, false), all,
                    percent(20, 0, 50, 0, 0, 30), random, "Lava Core close, swarmed");
        }
        // Risen undead near and too few golems: the Shell may summon too.
        if (problem == null && ColossusAttackGoal.weights(ColossusStage.SHELL, ColossusAttackGoal.Band.CLOSE, false, true)
                .get(ColossusAttackGoal.Move.SUMMON) != ColossusTuning.RISEN_SUMMON_CHANCE)
        {
            problem = "with risen undead near, the Shell should be able to summon";
        }
        if (problem != null)
        {
            helper.fail(problem);
            return;
        }
        helper.succeed();
    }

    /**
     * Picks a move {@link #SAMPLES} times and compares how often each comes up with the expected percentages (of the
     * available moves). Null if it matches.
     */
    private static String sample(EnumMap<ColossusAttackGoal.Move, Integer> weights, Set<ColossusAttackGoal.Move> available,
                                 EnumMap<ColossusAttackGoal.Move, Integer> expected, RandomSource random, String what)
    {
        EnumMap<ColossusAttackGoal.Move, Integer> counts = new EnumMap<>(ColossusAttackGoal.Move.class);
        for (int i = 0; i < SAMPLES; i++)
        {
            ColossusAttackGoal.Move move = ColossusAttackGoal.pick(weights, available, random);
            if (move == null || !available.contains(move))
            {
                return what + ": it picked " + move + ", which isn't one it can make";
            }
            counts.merge(move, 1, Integer::sum);
        }
        double total = expected.values().stream().mapToInt(Integer::intValue).sum();
        for (ColossusAttackGoal.Move move : ColossusAttackGoal.Move.values())
        {
            double share = counts.getOrDefault(move, 0) / (double) SAMPLES;
            double wanted = expected.getOrDefault(move, 0) / total;
            if (Math.abs(share - wanted) > SHARE_TOLERANCE)
            {
                return what + ": " + move + " should come up " + Math.round(wanted * 100.0D) + "% of the time, but it came up "
                        + Math.round(share * 1000.0D) / 10.0D + "%";
            }
        }
        return null;
    }

    private static List<ColossusTuning.Chances> table(ColossusStage stage)
    {
        return switch (stage)
        {
            case SHELL -> ColossusTuning.SHELL_CHANCES;
            case CRACKING -> ColossusTuning.CRACKING_CHANCES;
            case LAVA_CORE -> ColossusTuning.CORE_CHANCES;
        };
    }

    private static EnumMap<ColossusAttackGoal.Move, Integer> asMap(ColossusTuning.Chances c)
    {
        return percent(c.swipe(), c.throwRock(), c.slam(), c.charge(), c.lob(), c.summon());
    }

    /**
     * Percentages in the moves' order: swipe, throw, slam, charge, lob, summon ({@link #sample} works each share out of
     * their total, so the moves left when others can't be made may be given by their table numbers).
     */
    private static EnumMap<ColossusAttackGoal.Move, Integer> percent(int swipe, int throwRock, int slam, int charge, int lob,
                                                                    int summon)
    {
        EnumMap<ColossusAttackGoal.Move, Integer> map = new EnumMap<>(ColossusAttackGoal.Move.class);
        map.put(ColossusAttackGoal.Move.SWIPE, swipe);
        map.put(ColossusAttackGoal.Move.THROW, throwRock);
        map.put(ColossusAttackGoal.Move.SLAM, slam);
        map.put(ColossusAttackGoal.Move.CHARGE, charge);
        map.put(ColossusAttackGoal.Move.LOB, lob);
        map.put(ColossusAttackGoal.Move.SUMMON, summon);
        return map;
    }

    // ---------------------------------------------------------------------------------------------
    // Against risen undead
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_colossus_risen", timeoutTicks = ARENA_WAIT_TICKS + 300)
    public static void risenUndeadBringGolems(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusFightGameTest::risenUndeadBringGolems))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        CinderColossus golem = CinderColossus.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        if (golem == null || golem.attackGoal() == null)
        {
            helper.fail("the Colossus couldn't be spawned");
            return;
        }
        // Four of a player's risen undead (stand-ins: frozen zombies with Necromancy's owner mark): three up close at its
        // corners, one further off.
        UUID owner = UUID.randomUUID();
        List<Zombie> risen = new ArrayList<>();
        for (BlockPos at : List.of(MIDDLE.offset(3, 0, 3), MIDDLE.offset(-3, 0, 3), MIDDLE.offset(3, 0, -3), MIDDLE.offset(-6, 0, -6)))
        {
            Zombie zombie = EntityType.ZOMBIE.create(level);
            if (zombie == null)
            {
                golem.discard();
                risen.forEach(Zombie::discard);
                helper.fail("a test zombie couldn't be made");
                return;
            }
            Vec3 feet = helper.absoluteVec(Vec3.atBottomCenterOf(at));
            zombie.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
            zombie.setNoAi(true);
            zombie.setPersistenceRequired();
            zombie.getPersistentData().putUUID(RisenUndead.OWNER_KEY, owner);
            level.addFreshEntity(zombie);
            risen.add(zombie);
        }
        ColossusAttackGoal goal = golem.attackGoal();
        LivingEntity target = risen.get(0);
        golem.setTarget(target);
        String problem = null;
        // Two attacking it up close: the swipe and slam keep their Shell chances (60 / 40)...
        risen.get(0).setTarget(golem);
        risen.get(1).setTarget(golem);
        int two = goal.swarmCount();
        RandomSource random = RandomSource.create(7L);
        Set<ColossusAttackGoal.Move> close = EnumSet.of(ColossusAttackGoal.Move.SWIPE, ColossusAttackGoal.Move.SLAM);
        String calm = sample(ColossusAttackGoal.weights(ColossusStage.SHELL, ColossusAttackGoal.Band.CLOSE,
                two >= ColossusTuning.SWARM_ATTACKERS, false), close, percent(60, 0, 40, 0, 0, 0), random, "two attacking");
        // ...three: the slam's share rises to 60.
        risen.get(2).setTarget(golem);
        int three = goal.swarmCount();
        String swarmed = sample(ColossusAttackGoal.weights(ColossusStage.SHELL, ColossusAttackGoal.Band.CLOSE,
                three >= ColossusTuning.SWARM_ATTACKERS, false), close, percent(40, 0, 60, 0, 0, 0), random, "three attacking");
        if (two != 2 || three != 3)
        {
            problem = "it should count 2, then 3 risen undead attacking it up close, but it counted " + two + ", then " + three;
        }
        else if (calm != null || swarmed != null)
        {
            problem = calm != null ? calm : swarmed;
        }
        else if (!goal.available(ColossusAttackGoal.Move.SUMMON, target))
        {
            problem = "with 4 risen undead near and no golems it should be able to summon, even in the Shell";
        }
        else if (goal.summonCount() != 2)
        {
            problem = "with 4 risen undead near a summon should raise 2 golems, but it would raise " + goal.summonCount();
        }
        if (problem != null)
        {
            golem.discard();
            risen.forEach(Zombie::discard);
            helper.fail(problem);
            return;
        }
        goal.begin(ColossusAttackGoal.Move.SUMMON, target);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(golem.getAction() != GolemAction.SUMMON, "still summoning"))
                .thenExecute(() -> {
                    List<GolemMinion> minions = golem.minions();
                    String wrong = null;
                    if (minions.size() != 2)
                    {
                        wrong = "with 4 risen undead near, the summon should raise 2 golems, but it raised " + minions.size();
                    }
                    for (GolemMinion minion : minions)
                    {
                        if (wrong == null && !(minion.getTarget() instanceof Zombie z && risen.contains(z)))
                        {
                            wrong = "its golems should go for the risen undead first, but one is after " + minion.getTarget();
                        }
                    }
                    minions.forEach(GolemMinion::discard);
                    risen.forEach(Zombie::discard);
                    golem.discard();
                    if (wrong != null)
                    {
                        helper.fail(wrong);
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers and the runway template
    // ---------------------------------------------------------------------------------------------

    /** Logs the stand-in players out and removes the Colossus, its minions and any blobs it spat. */
    private static void cleanUp(GameTestHelper helper, CinderColossus golem, List<ServerPlayer> players)
    {
        leaveAll(players);
        AABB area = golem.getBoundingBox().inflate(24.0D);
        golem.minions().forEach(GolemMinion::discard);
        helper.getLevel().getEntitiesOfClass(LavaBlob.class, area).forEach(LavaBlob::discard);
        golem.discard();
    }

    /** Makes the runway template in memory when a development server starts (like the arena). */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(RUNWAY_ID);
        template.load(event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK),
                ColossusGameTest.floorTemplate(RUNWAY_LENGTH, RUNWAY_WIDTH));
    }
}
