package com.reg21meme.dross.boss.ratking;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.RatKingArena;
import com.reg21meme.dross.boss.golem.ColossusFightGameTest;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA_WAIT_TICKS;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.FLOOR_Y;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.MIDDLE;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.joinPlayer;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.leaveAll;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.waitForArena;

/**
 * Headless tests (GameTests) for the Rat King's fight ({@code .\gradlew.bat runGameTestServer}), in the Cinder Colossus
 * tests' arena ({@link ColossusGameTest#ARENA}, 17 x 17 blocks). Each runs in its own batch, so one test's players never
 * wake (or see the boss bar of) another test's king.
 * <ul>
 *   <li>{@link #wakesWhenAPlayerComesNear}: asleep with a player 13 blocks away, and with a sneaking one 9 blocks away;
 *       awake once that player stops sneaking.</li>
 *   <li>{@link #wakesWhenHurtWithoutDamage}: a hit only wakes him (no damage); he can't be hurt while he wakes up; then
 *       he can.</li>
 *   <li>{@link #rageStopsAtFortyPercent}: a huge hit stops at 40% and starts the rage, during which he can't be hurt;
 *       then he's red-eyed and faster.</li>
 *   <li>{@link #burrowsEveryThirtyWithRats}: every 30 health lost (a huge hit stops there) he burrows; under the ground
 *       he can't be hurt or hit, stays 80 ticks, calls 10 regular rats round his target and pops up 4 blocks from it;
 *       there's no limit, so three burrows leave 30 of his rats alive.</li>
 *   <li>{@link #rageBurrowsEveryTwentyWithEnragedRats}: in the Rage every 20 health, 50 ticks under, 7 enraged rats.</li>
 *   <li>{@link #castsOneBallThenThreeInRage}: a cast throws one poison ball, three in the Rage; a ball bursts into a
 *       poison cloud.</li>
 *   <li>{@link #cloudPoisonsBrieflyAndSkipsRats}: the cloud's poison lasts at most 50 ticks, never reaches a rat or
 *       anything outside it, and the cloud is gone after its time.</li>
 *   <li>{@link #cloudPoisonIsSteady}: standing in a cloud for its whole life is a steady Poison I (one point of damage
 *       every 25 ticks: at most 3 in its 60 ticks), not a hit every time the cloud tops the poison up.</li>
 *   <li>{@link #facesHisTargetMostOfTheTime}: fighting a stand-in player who walks round him (with a burrow, his rage
 *       and a burrow in the Rage), his body faces them (within 30 degrees) at least 90% of the time, his time under the
 *       ground not counted (the user's wish after a play test).</li>
 *   <li>{@link #enragedBitesPoison}: an enraged rat's bite poisons for 40 ticks; a regular rat's doesn't.</li>
 *   <li>{@link #wildRatsTargetOnlyIfEnraged}: a wild enraged rat goes for a player, a wild regular rat never does.</li>
 *   <li>{@link #summonedRatsCrumbleWithTheirKing}: his rats crumble when he dies (a wild rat stays).</li>
 *   <li>{@link #downTheDrainForPlayersNear}: beaten, he gives "Down the Drain" to the killer and players within 40
 *       blocks (not to one further away) and lies there; removed with {@code /kill}, he gives nothing.</li>
 *   <li>{@link #bossBarOnlyAwakeAndNear}: no boss bar while he's asleep or waking up; then only players within 40
 *       blocks see it; it follows his health and goes when he dies.</li>
 *   <li>{@link #arenaIsDirtAndCoarseDirt}: {@code /dross boss arena ratking}'s floor is only dirt and coarse dirt
 *       (about a third coarse) in its circle, with clear air above.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class RatKingFightGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A wide test floor for the arena test (the arena is 31 across), made in memory like the Colossus's. */
    static final String RAT_ARENA = "rat_king_test_arena";
    private static final ResourceLocation RAT_ARENA_ID = new ResourceLocation(Dross.MODID, RAT_ARENA);
    private static final int RAT_ARENA_SIZE = 2 * RatKingArena.RADIUS + 3;
    /** Where the king usually stands (west side of the arena), facing east, and where his target stands. */
    private static final BlockPos KING_AT = new BlockPos(2, FLOOR_Y + 1, 8);
    private static final BlockPos TARGET_AT = MIDDLE;
    private static final float YAW_EAST = -90.0F;
    private static final float HUGE_HIT = 1000.0F;
    private static final float SMALL_HIT = 10.0F;
    private static final float HEALTH_TOLERANCE = 0.01F;
    /** Waits after something should have happened. */
    private static final int AFTER = 4;
    private static final int SETTLE = 10;
    /** A pop-up lands this close to POPUP_DISTANCE from his target (blocks). */
    private static final double POPUP_TOLERANCE = 0.75D;
    /** The time he spends under the ground is measured within this many ticks. */
    private static final int UNDER_TOLERANCE = 2;
    /** After a wild enraged rat has gone for the player, the regular one is watched this many ticks more. */
    private static final int WILD_WATCH = 20;
    /** Far beyond the boss bar's (and the reward's) 40 blocks. */
    private static final int FAR = 45;
    /** Minecraft's Poison I hurts once every this many ticks. */
    private static final int POISON_I_PERIOD = 25;
    /**
     * The facing test: he fights a stand-in player walking round the floor (ColossusFightGameTest.walkRound) for this
     * many ticks; at this tick a hit forces his first burrow, from this one his rage (once he's free for it), and from
     * this one a burrow in the Rage.
     */
    private static final int FACING_TICKS = 800;
    private static final int FACING_BURROW_AT = 100;
    private static final int FACING_RAGE_AT = 360;
    private static final int FACING_RAGE_BURROW_AT = 560;

    private RatKingFightGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Waking up
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_ratking_wake_near", timeoutTicks = ARENA_WAIT_TICKS + 200)
    public static void wakesWhenAPlayerComesNear(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::wakesWhenAPlayerComesNear))
        {
            return;
        }
        RatKing king = spawnKing(helper, KING_AT);
        if (king == null)
        {
            return;
        }
        ServerPlayer player = joinPlayer(helper, "rat-wake-test", KING_AT.offset(13, 0, 0), 0.0F);
        List<ServerPlayer> players = List.of(player);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    if (!king.isAsleep())
                    {
                        fail(helper, players, "a player 13 blocks away shouldn't wake him, but he's " + king.getAction());
                    }
                    // Sneaking, 9 blocks away (both at once: he never sees him close and standing).
                    player.setShiftKeyDown(true);
                    player.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(KING_AT.offset(9, 0, 0))));
                })
                .thenExecuteAfter(SETTLE, () -> {
                    if (!king.isAsleep())
                    {
                        fail(helper, players, "a sneaking player 9 blocks away shouldn't wake him, but he's " + king.getAction());
                    }
                    player.setShiftKeyDown(false);
                })
                .thenExecuteAfter(2, () -> {
                    if (king.getAction() != RatKingAction.WAKE || !king.isAwake())
                    {
                        fail(helper, players, "a player 9 blocks away (not sneaking) should wake him, but he's " + king.getAction());
                    }
                    leaveAll(players);
                    king.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_ratking_wake_hit", timeoutTicks = ARENA_WAIT_TICKS + 300)
    public static void wakesWhenHurtWithoutDamage(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::wakesWhenHurtWithoutDamage))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        RatKing king = spawnKing(helper, KING_AT);
        if (king == null)
        {
            return;
        }
        float max = king.getMaxHealth();
        int wakeEnd = RatKing.ANIMATION_BLEND_TICKS + king.getDesign().wakeTicks() + AFTER;
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    if (!king.isAsleep())
                    {
                        helper.fail("he should start asleep, but he's " + king.getAction());
                    }
                    king.hurt(level.damageSources().generic(), SMALL_HIT);
                    expectHealth(helper, king, max, "the waking hit (no damage)");
                    if (king.getAction() != RatKingAction.WAKE)
                    {
                        helper.fail("a hit should wake him, but he's " + king.getAction());
                    }
                    king.invulnerableTime = 0;
                    king.hurt(level.damageSources().generic(), HUGE_HIT);
                    expectHealth(helper, king, max, "a hit while he wakes up (he can't be hurt)");
                })
                .thenExecuteAfter(wakeEnd, () -> {
                    if (king.getAction() != RatKingAction.IDLE || !king.isFightActive())
                    {
                        helper.fail("after waking up he should be standing and fighting, but he's " + king.getAction());
                    }
                    king.invulnerableTime = 0;
                    king.hurt(level.damageSources().generic(), SMALL_HIT);
                    if (king.getHealth() >= max - HEALTH_TOLERANCE)
                    {
                        helper.fail("awake, a hit should hurt him");
                    }
                    king.discard();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // The rage
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_ratking_rage", timeoutTicks = ARENA_WAIT_TICKS + 300)
    public static void rageStopsAtFortyPercent(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::rageStopsAtFortyPercent))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        RatKing king = spawnKing(helper, KING_AT);
        if (king == null)
        {
            return;
        }
        float rageAt = RatKingTuning.RAGE_AT * king.getMaxHealth();
        int rageEnd = RatKing.ANIMATION_BLEND_TICKS + king.getDesign().rageTicks() + AFTER;
        king.startFightNow();
        king.setHealth(rageAt + 5.0F);
        helper.startSequence()
                .thenExecute(() -> {
                    king.hurt(level.damageSources().generic(), HUGE_HIT);
                    expectHealth(helper, king, rageAt, "a huge hit above 40% (it stops there)");
                    if (king.getAction() != RatKingAction.RAGE || !king.isEnraged())
                    {
                        helper.fail("reaching 40% should start his rage, but he's " + king.getAction());
                    }
                    king.invulnerableTime = 0;
                    king.hurt(level.damageSources().generic(), HUGE_HIT);
                    expectHealth(helper, king, rageAt, "a hit during the rage (he can't be hurt)");
                })
                .thenExecuteAfter(rageEnd, () -> {
                    if (king.getAction() != RatKingAction.IDLE || !king.isEnraged())
                    {
                        helper.fail("after his rage he should be standing, red-eyed, but he's " + king.getAction());
                    }
                    if (Math.abs(king.getAttributeValue(Attributes.MOVEMENT_SPEED) - RatKingTuning.RAGE_SPEED) > 1.0E-4D)
                    {
                        helper.fail("in the Rage his speed should be " + RatKingTuning.RAGE_SPEED + ", but it's "
                                + king.getAttributeValue(Attributes.MOVEMENT_SPEED));
                    }
                    king.discard();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Burrowing and his rats
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_ratking_burrow", timeoutTicks = ARENA_WAIT_TICKS + 1600)
    public static void burrowsEveryThirtyWithRats(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::burrowsEveryThirtyWithRats))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        RatKing king = spawnKing(helper, KING_AT);
        Pig target = dummyTarget(helper, TARGET_AT);
        if (king == null || target == null)
        {
            return;
        }
        float max = king.getMaxHealth();
        long[] under = new long[1];
        king.startFightNow();
        king.setTarget(target);
        helper.startSequence()
                // The first burrow, at 170.
                .thenExecute(() -> hitIntoBurrow(helper, king, max - RatKingTuning.BURROW_EVERY))
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.BURROWED))
                .thenExecute(() -> {
                    under[0] = level.getGameTime();
                    checkUnderground(helper, king, max - RatKingTuning.BURROW_EVERY);
                })
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.POPUP))
                .thenExecute(() -> {
                    long ticks = level.getGameTime() - under[0];
                    if (Math.abs(ticks - RatKingTuning.UNDERGROUND_TICKS) > UNDER_TOLERANCE)
                    {
                        helper.fail("he should stay under the ground " + RatKingTuning.UNDERGROUND_TICKS + " ticks, but stayed " + ticks);
                    }
                    checkRats(helper, king, RatKingTuning.RATS_PER_BURROW, false);
                    checkPopup(helper, king, target);
                })
                // The second, at 140.
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.IDLE))
                .thenExecute(() -> hitIntoBurrow(helper, king, max - 2 * RatKingTuning.BURROW_EVERY))
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.POPUP))
                .thenExecute(() -> checkRats(helper, king, 2 * RatKingTuning.RATS_PER_BURROW, false))
                // The third, at 110: there's no limit, so he calls a full set again.
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.IDLE))
                .thenExecute(() -> hitIntoBurrow(helper, king, max - 3 * RatKingTuning.BURROW_EVERY))
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.POPUP))
                .thenExecute(() -> {
                    checkRats(helper, king, 3 * RatKingTuning.RATS_PER_BURROW, false);
                    LOGGER.info("[rat king test] three burrows at 170, 140 and 110 health, {} rats each (no limit)",
                            RatKingTuning.RATS_PER_BURROW);
                    cleanUp(helper, king, target);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_ratking_rage_burrow", timeoutTicks = ARENA_WAIT_TICKS + 800)
    public static void rageBurrowsEveryTwentyWithEnragedRats(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::rageBurrowsEveryTwentyWithEnragedRats))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        RatKing king = spawnKing(helper, KING_AT);
        Pig target = dummyTarget(helper, TARGET_AT);
        if (king == null || target == null)
        {
            return;
        }
        float rageAt = RatKingTuning.RAGE_AT * king.getMaxHealth();
        long[] under = new long[1];
        king.startFightNow();
        king.skipToRage();
        king.setTarget(target);
        helper.startSequence()
                .thenExecute(() -> hitIntoBurrow(helper, king, rageAt - RatKingTuning.RAGE_BURROW_EVERY))
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.BURROWED))
                .thenExecute(() -> {
                    under[0] = level.getGameTime();
                    checkUnderground(helper, king, rageAt - RatKingTuning.RAGE_BURROW_EVERY);
                })
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.POPUP))
                .thenExecute(() -> {
                    long ticks = level.getGameTime() - under[0];
                    if (Math.abs(ticks - RatKingTuning.RAGE_UNDERGROUND_TICKS) > UNDER_TOLERANCE)
                    {
                        helper.fail("in the Rage he should stay under " + RatKingTuning.RAGE_UNDERGROUND_TICKS
                                + " ticks, but stayed " + ticks);
                    }
                    checkRats(helper, king, RatKingTuning.RAGE_RATS_PER_BURROW, true);
                    checkPopup(helper, king, target);
                })
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.IDLE))
                .thenExecute(() -> hitIntoBurrow(helper, king, rageAt - 2 * RatKingTuning.RAGE_BURROW_EVERY))
                .thenExecute(() -> cleanUp(helper, king, target))
                .thenSucceed();
    }

    /** A huge hit: it should stop at {@code checkpoint} and start a burrow. */
    private static void hitIntoBurrow(GameTestHelper helper, RatKing king, float checkpoint)
    {
        king.invulnerableTime = 0;
        king.hurt(helper.getLevel().damageSources().generic(), HUGE_HIT);
        expectHealth(helper, king, checkpoint, "a huge hit before a burrow (it stops at the burrow)");
        if (king.getAction() != RatKingAction.BURROW)
        {
            helper.fail("reaching " + checkpoint + " health should start a burrow, but he's " + king.getAction());
        }
    }

    /** Under the ground: no hit hurts him, nothing can pick him out, and his health stays at the checkpoint. */
    private static void checkUnderground(GameTestHelper helper, RatKing king, float checkpoint)
    {
        king.invulnerableTime = 0;
        king.hurt(helper.getLevel().damageSources().generic(), HUGE_HIT);
        expectHealth(helper, king, checkpoint, "a hit while he's under the ground (he can't be hurt)");
        if (king.isPickable() || king.canBeSeenAsEnemy())
        {
            helper.fail("under the ground nothing should be able to hit or target him");
        }
    }

    /** He has called exactly {@code count} rats of his, all of the right kind. */
    private static void checkRats(GameTestHelper helper, RatKing king, int count, boolean enraged)
    {
        List<Rat> rats = king.summonedRats();
        if (rats.size() != count)
        {
            helper.fail("he should have " + count + " rats alive, but has " + rats.size());
        }
        int rightKind = 0;
        for (Rat rat : rats)
        {
            if (rat.isEnraged() == enraged && rat.isSummoned() && rat.isHostile())
            {
                rightKind++;
            }
        }
        // (In the Rage test all his rats came from Rage burrows; in the first phase, all regular.)
        if (rightKind != rats.size())
        {
            helper.fail("his rats should all be " + (enraged ? "enraged" : "regular") + " (and his), but only " + rightKind
                    + " of " + rats.size() + " are");
        }
    }

    /** He has popped up {@link RatKingTuning#POPUP_DISTANCE} from his target, facing it. */
    private static void checkPopup(GameTestHelper helper, RatKing king, LivingEntity target)
    {
        double d = RatKingAttackGoal.flatDistance(king.position(), target.position());
        if (Math.abs(d - RatKingTuning.POPUP_DISTANCE) > POPUP_TOLERANCE)
        {
            helper.fail("he should pop up " + RatKingTuning.POPUP_DISTANCE + " blocks from his target, but it's " + d);
        }
        Vec3 toTarget = target.position().subtract(king.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
        if (king.forward().dot(toTarget) < 0.9D)
        {
            helper.fail("he should pop up facing his target");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The poison shot and the cloud
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_ratking_cast", timeoutTicks = ARENA_WAIT_TICKS + 300)
    public static void castsOneBallThenThreeInRage(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::castsOneBallThenThreeInRage))
        {
            return;
        }
        RatKing king = spawnKing(helper, KING_AT);
        Pig target = dummyTarget(helper, KING_AT.offset(10, 0, 0));
        if (king == null || target == null)
        {
            return;
        }
        RatKingDesigns.Design d = king.getDesign();
        int release = RatKing.ANIMATION_BLEND_TICKS + d.castRelease() + 1;
        king.startFightNow();
        king.setTarget(target);
        helper.startSequence()
                .thenExecute(() -> begin(helper, king, RatKingAttackGoal.Move.CAST, target))
                .thenExecuteAfter(release, () -> {
                    int balls = ballsOf(helper, king);
                    if (balls != RatKingTuning.CAST_BALLS || king.attackGoal().lastBalls() != RatKingTuning.CAST_BALLS)
                    {
                        helper.fail("in the first phase a cast should throw " + RatKingTuning.CAST_BALLS + " ball, but "
                                + balls + " are flying");
                    }
                })
                .thenExecuteAfter(RatKingTuning.BALL_MAX_TICKS / 2, () -> {
                    AABB around = target.getBoundingBox().inflate(RatKingTuning.CLOUD_RADIUS + 1.0D);
                    if (helper.getLevel().getEntitiesOfClass(PoisonCloud.class, around).isEmpty())
                    {
                        helper.fail("the ball should have burst into a poison cloud by his target");
                    }
                    king.skipToRage();
                    begin(helper, king, RatKingAttackGoal.Move.CAST, target);
                })
                .thenExecuteAfter(release, () -> {
                    int balls = ballsOf(helper, king);
                    if (balls != RatKingTuning.RAGE_CAST_BALLS || king.attackGoal().lastBalls() != RatKingTuning.RAGE_CAST_BALLS)
                    {
                        helper.fail("in the Rage a cast should throw " + RatKingTuning.RAGE_CAST_BALLS + " balls, but "
                                + balls + " are flying");
                    }
                    cleanUp(helper, king, target);
                })
                .thenSucceed();
    }

    private static void begin(GameTestHelper helper, RatKing king, RatKingAttackGoal.Move move, LivingEntity target)
    {
        RatKingAttackGoal goal = king.attackGoal();
        if (goal == null)
        {
            helper.fail("he has no fighting AI");
            return;
        }
        goal.begin(move, target);
    }

    private static int ballsOf(GameTestHelper helper, RatKing king)
    {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(17, 11, 17))).inflate(8.0D);
        return helper.getLevel().getEntitiesOfClass(PlagueBall.class, area, ball -> ball.getOwner() == king).size();
    }

    @GameTest(template = ARENA, batch = "dross_ratking_cloud", timeoutTicks = ARENA_WAIT_TICKS + 300)
    public static void cloudPoisonsBrieflyAndSkipsRats(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::cloudPoisonsBrieflyAndSkipsRats))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Pig inside = dummyTarget(helper, MIDDLE.offset(1, 0, 0));
        Pig outside = dummyTarget(helper, MIDDLE.offset(5, 0, 0));
        Rat rat = wildRat(helper, ModEntities.RAT.get(), MIDDLE.offset(-1, 0, 0), true);
        if (inside == null || outside == null || rat == null)
        {
            return;
        }
        PoisonCloud cloud = PoisonCloud.at(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), null);
        level.addFreshEntity(cloud);
        int[] longest = new int[1];
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    MobEffectInstance poison = inside.getEffect(MobEffects.POISON);
                    if (poison == null || poison.getAmplifier() != 0)
                    {
                        helper.fail("something inside the cloud should get Poison I");
                        return;
                    }
                    longest[0] = poison.getDuration();
                    if (poison.getDuration() > RatKingTuning.POISON_TICKS)
                    {
                        helper.fail("the cloud's poison should last at most " + RatKingTuning.POISON_TICKS + " ticks, but lasts "
                                + poison.getDuration());
                    }
                    if (rat.hasEffect(MobEffects.POISON))
                    {
                        helper.fail("the cloud should never poison a rat");
                    }
                    if (outside.hasEffect(MobEffects.POISON))
                    {
                        helper.fail("the cloud shouldn't reach 5 blocks away");
                    }
                })
                .thenExecuteAfter(RatKingTuning.CLOUD_APPLY_EVERY * 3, () -> {
                    MobEffectInstance poison = inside.getEffect(MobEffects.POISON);
                    if (poison != null && poison.getDuration() > RatKingTuning.POISON_TICKS)
                    {
                        helper.fail("topped up by the cloud, the poison should still last at most " + RatKingTuning.POISON_TICKS);
                    }
                    if (rat.hasEffect(MobEffects.POISON))
                    {
                        helper.fail("the cloud should never poison a rat");
                    }
                })
                .thenExecuteAfter(RatKingTuning.CLOUD_TICKS + RatKingTuning.POISON_TICKS, () -> {
                    if (!cloud.isRemoved())
                    {
                        helper.fail("the cloud should be gone after " + RatKingTuning.CLOUD_TICKS + " ticks");
                    }
                    if (inside.hasEffect(MobEffects.POISON))
                    {
                        helper.fail("the poison should have worn off soon after the cloud went");
                    }
                    LOGGER.info("[rat king test] the poison cloud poisons for {} ticks, skips rats, and goes", longest[0]);
                    inside.discard();
                    outside.discard();
                    rat.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_ratking_cloud_steady", timeoutTicks = ARENA_WAIT_TICKS + 200)
    public static void cloudPoisonIsSteady(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::cloudPoisonIsSteady))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        // A pig that can be hurt (the poison can't kill it: Minecraft's poison stops at half a heart).
        Pig pig = EntityType.PIG.create(level);
        if (pig == null)
        {
            helper.fail("the test pig couldn't be made");
            return;
        }
        Vec3 p = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE));
        pig.moveTo(p.x, p.y, p.z, 0.0F, 0.0F);
        pig.setNoAi(true);
        pig.setPersistenceRequired();
        level.addFreshEntity(pig);
        float health = pig.getHealth();
        PoisonCloud cloud = PoisonCloud.at(level, p, null);
        level.addFreshEntity(cloud);
        // Standing in it for its whole life: a steady Poison I, one point of damage every 25 ticks (the first at once).
        int steady = (RatKingTuning.CLOUD_TICKS + POISON_I_PERIOD - 1) / POISON_I_PERIOD;
        helper.startSequence()
                .thenExecuteAfter(RatKingTuning.CLOUD_TICKS + AFTER, () -> {
                    float lost = health - pig.getHealth();
                    LOGGER.info("[rat king test] standing in the poison cloud for its whole life ({} ticks) took {} health "
                            + "(steady Poison I: at most {})", RatKingTuning.CLOUD_TICKS, lost, steady);
                    if (!cloud.isRemoved())
                    {
                        helper.fail("the cloud should be gone after " + RatKingTuning.CLOUD_TICKS + " ticks");
                    }
                    else if (lost < 1.0F - HEALTH_TOLERANCE)
                    {
                        helper.fail("the cloud should poison what stands in it, but the pig lost no health");
                    }
                    else if (lost > steady + HEALTH_TOLERANCE)
                    {
                        helper.fail("standing in the cloud for its whole life should be a steady Poison I (at most " + steady
                                + " health in " + RatKingTuning.CLOUD_TICKS + " ticks), but the pig lost " + lost);
                    }
                    pig.discard();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Facing his target
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = RAT_ARENA, batch = "dross_ratking_facing", timeoutTicks = ARENA_WAIT_TICKS + FACING_TICKS + 100)
    public static void facesHisTargetMostOfTheTime(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::facesHisTargetMostOfTheTime, RAT_ARENA_SIZE, RAT_ARENA_SIZE))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos middle = new BlockPos(RAT_ARENA_SIZE / 2, FLOOR_Y + 1, RAT_ARENA_SIZE / 2);
        RatKing king = spawnKing(helper, middle);
        if (king == null)
        {
            return;
        }
        ServerPlayer player = joinPlayer(helper, "rat-facing-test",
                middle.offset((int) ColossusFightGameTest.FACING_RADIUS_MID, 0, 0), 0.0F);
        // He can't hurt this stand-in (it would die and the fight would end), so it's always the one he fights: an
        // invulnerable player is never picked as a target by himself, so nothing takes it away.
        player.getAbilities().invulnerable = true;
        List<ServerPlayer> players = List.of(player);
        king.startFightNow();
        king.setTarget(player);
        Vec3 centre = helper.absoluteVec(Vec3.atBottomCenterOf(middle));
        int[] tick = {0};
        double[] walked = {0.0D};
        int[] counted = {0};
        int[] facing = {0};
        boolean[] raged = {false};
        boolean[] rageBurrowed = {false};
        Map<String, int[]> breakdown = new TreeMap<>();
        helper.startSequence()
                .thenExecuteFor(FACING_TICKS, () -> {
                    int k = tick[0]++;
                    // The fight's script: a burrow, his rage, a burrow in the Rage (each once he's free for it).
                    if (k == FACING_BURROW_AT)
                    {
                        king.invulnerableTime = 0;
                        king.hurt(level.damageSources().generic(), HUGE_HIT);
                    }
                    if (k >= FACING_RAGE_AT && !raged[0] && !king.isBusy())
                    {
                        raged[0] = true;
                        king.setHealth(king.rageHealth() + SMALL_HIT);
                        king.invulnerableTime = 0;
                        king.hurt(level.damageSources().generic(), HUGE_HIT);
                    }
                    if (k >= FACING_RAGE_BURROW_AT && raged[0] && !rageBurrowed[0] && !king.isBusy())
                    {
                        rageBurrowed[0] = true;
                        king.invulnerableTime = 0;
                        king.hurt(level.damageSources().generic(), HUGE_HIT);
                    }
                    walked[0] = ColossusFightGameTest.walkRound(player, centre, k, walked[0]);
                    if (king.getTarget() != player)
                    {
                        king.setTarget(player);
                    }
                    // Under the ground (diving, tunnelling, until he bursts out) doesn't count, nor a target inside him.
                    if (!king.isAlive() || !king.isFightActive() || king.isUnderground()
                            || ColossusFightGameTest.flatDistance(king.position(), player.position())
                            <= king.getBbWidth() / 2.0D + player.getBbWidth() / 2.0D)
                    {
                        return;
                    }
                    counted[0]++;
                    boolean faced = ColossusFightGameTest.faces(king, player.position());
                    if (faced)
                    {
                        facing[0]++;
                    }
                    // What he was doing (and whether he was running), for the log.
                    String label = king.getAction() + (ColossusFightGameTest.isWalking(king) ? " running" : "");
                    int[] row = breakdown.computeIfAbsent(label, x -> new int[2]);
                    row[0]++;
                    row[1] += faced ? 1 : 0;
                })
                .thenExecute(() -> {
                    double share = (double) facing[0] / Math.max(1, counted[0]);
                    breakdown.forEach((key, row) -> LOGGER.info("[rat king test] facing while {}: {} of {} ticks", key, row[1], row[0]));
                    LOGGER.info("[rat king test] he faced his target (within {} degrees) {}% of {} ticks (raged: {}, burrowed "
                                    + "in the Rage: {})", ColossusFightGameTest.FACING_TOLERANCE,
                            String.format("%.1f", 100.0D * share), counted[0], raged[0], rageBurrowed[0]);
                    leaveAll(players);
                    AABB area = new AABB(helper.absolutePos(BlockPos.ZERO),
                            helper.absolutePos(new BlockPos(RAT_ARENA_SIZE, 11, RAT_ARENA_SIZE))).inflate(16.0D);
                    level.getEntitiesOfClass(Rat.class, area).forEach(Rat::discard);
                    level.getEntitiesOfClass(PlagueBall.class, area).forEach(PlagueBall::discard);
                    level.getEntitiesOfClass(PoisonCloud.class, area).forEach(PoisonCloud::discard);
                    king.discard();
                    if (!raged[0] || !rageBurrowed[0])
                    {
                        helper.fail("the script should have made him rage and burrow in the Rage (raged: " + raged[0]
                                + ", burrowed in the Rage: " + rageBurrowed[0] + ")");
                    }
                    else if (counted[0] < ColossusFightGameTest.FACING_MIN_SHARE_COUNTED * FACING_TICKS)
                    {
                        helper.fail("too few ticks counted to measure his facing: " + counted[0]);
                    }
                    else if (share < ColossusFightGameTest.FACING_SHARE)
                    {
                        helper.fail("he should face his target at least " + Math.round(ColossusFightGameTest.FACING_SHARE * 100.0D)
                                + "% of the time, but faced it " + String.format("%.1f", 100.0D * share) + "%");
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // The rats
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_ratking_bite", timeoutTicks = ARENA_WAIT_TICKS + 40)
    public static void enragedBitesPoison(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::enragedBitesPoison))
        {
            return;
        }
        Rat enraged = wildRat(helper, ModEntities.ENRAGED_RAT.get(), MIDDLE.offset(-1, 0, 0), true);
        Rat regular = wildRat(helper, ModEntities.RAT.get(), MIDDLE.offset(-1, 0, 3), true);
        Pig bitten = dummyTarget(helper, MIDDLE);
        Pig other = dummyTarget(helper, MIDDLE.offset(0, 0, 3));
        if (enraged == null || regular == null || bitten == null || other == null)
        {
            return;
        }
        bitten.setInvulnerable(false);
        other.setInvulnerable(false);
        enraged.doHurtTarget(bitten);
        regular.doHurtTarget(other);
        MobEffectInstance poison = bitten.getEffect(MobEffects.POISON);
        String problem = null;
        if (poison == null || poison.getDuration() != RatKingTuning.ENRAGED_BITE_POISON_TICKS || poison.getAmplifier() != 0)
        {
            problem = "an enraged rat's bite should poison for " + RatKingTuning.ENRAGED_BITE_POISON_TICKS + " ticks, but it's "
                    + (poison == null ? "none" : poison.getDuration() + " ticks");
        }
        else if (other.hasEffect(MobEffects.POISON))
        {
            problem = "a regular rat's bite shouldn't poison";
        }
        enraged.discard();
        regular.discard();
        bitten.discard();
        other.discard();
        if (problem != null)
        {
            helper.fail(problem);
            return;
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_ratking_wild_rats", timeoutTicks = ARENA_WAIT_TICKS + 400)
    public static void wildRatsTargetOnlyIfEnraged(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::wildRatsTargetOnlyIfEnraged))
        {
            return;
        }
        ServerPlayer player = joinPlayer(helper, "rat-wild-test", MIDDLE, 0.0F);
        Rat regular = wildRat(helper, ModEntities.RAT.get(), MIDDLE.offset(4, 0, 0), false);
        Rat enraged = wildRat(helper, ModEntities.ENRAGED_RAT.get(), MIDDLE.offset(-4, 0, 0), false);
        List<ServerPlayer> players = List.of(player);
        if (regular == null || enraged == null)
        {
            leaveAll(players);
            return;
        }
        boolean[] regularWent = new boolean[1];
        helper.startSequence()
                .thenWaitUntil(() -> {
                    regularWent[0] |= regular.getTarget() != null;
                    if (enraged.getTarget() != player)
                    {
                        helper.fail("a wild enraged rat should go for the player near it");
                    }
                })
                .thenExecuteAfter(WILD_WATCH, () -> {
                    String problem = regularWent[0] || regular.getTarget() != null || regular.isAggressive()
                            ? "a wild regular rat should never go for a player" : null;
                    leaveAll(players);
                    regular.discard();
                    enraged.discard();
                    if (problem != null)
                    {
                        helper.fail(problem);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_ratking_rats_crumble", timeoutTicks = ARENA_WAIT_TICKS + 60)
    public static void summonedRatsCrumbleWithTheirKing(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::summonedRatsCrumbleWithTheirKing))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        RatKing king = spawnKing(helper, KING_AT);
        if (king == null)
        {
            return;
        }
        king.startFightNow();
        Rat a = Rat.summon(ModEntities.RAT.get(), level, king, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        Rat b = Rat.summon(ModEntities.ENRAGED_RAT.get(), level, king,
                helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(2, 0, 2))), 0.0F);
        Rat wild = wildRat(helper, ModEntities.RAT.get(), MIDDLE.offset(4, 0, -3), true);
        if (a == null || b == null || wild == null || !a.belongsTo(king) || !b.belongsTo(king))
        {
            helper.fail("his rats couldn't be summoned");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(5, () -> {
                    if (a.isRemoved() || b.isRemoved())
                    {
                        helper.fail("his rats shouldn't crumble while he lives");
                    }
                    king.kill();
                })
                .thenExecuteAfter(2, () -> {
                    if (!a.isRemoved() || !b.isRemoved())
                    {
                        helper.fail("his rats should crumble when he dies");
                    }
                    if (wild.isRemoved())
                    {
                        helper.fail("a wild rat shouldn't crumble with him");
                    }
                    wild.discard();
                    king.discard();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // The end and the boss bar
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_ratking_reward", timeoutTicks = ARENA_WAIT_TICKS + 200)
    public static void downTheDrainForPlayersNear(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::downTheDrainForPlayersNear))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        RatKing beaten = spawnKing(helper, MIDDLE);
        RatKing killed = spawnKing(helper, KING_AT);
        if (beaten == null || killed == null)
        {
            return;
        }
        ServerPlayer killer = joinPlayer(helper, "rat-reward-killer", MIDDLE.offset(3, 0, 0), 0.0F);
        ServerPlayer near = joinPlayer(helper, "rat-reward-near", MIDDLE.offset(-5, 0, 4), 0.0F);
        ServerPlayer far = joinPlayer(helper, "rat-reward-far", MIDDLE.offset(FAR, 0, 0), 0.0F);
        List<ServerPlayer> players = List.of(killer, near, far);
        beaten.startFightNow();
        beaten.skipToRage();
        beaten.setHealth(SMALL_HIT);
        beaten.hurt(level.damageSources().playerAttack(killer), HUGE_HIT);
        String problem = null;
        if (!beaten.isDeadOrDying() || beaten.getAction() != RatKingAction.DEATH)
        {
            problem = "at the end of the Rage a huge hit should kill him (his silly death), but he's " + beaten.getAction();
        }
        else if (!DrossAdvancements.has(killer, DrossAdvancements.DOWN_THE_DRAIN))
        {
            problem = "the player who beat him should get Down the Drain";
        }
        else if (!DrossAdvancements.has(near, DrossAdvancements.DOWN_THE_DRAIN))
        {
            problem = "a player within 40 blocks should get Down the Drain";
        }
        else if (DrossAdvancements.has(far, DrossAdvancements.DOWN_THE_DRAIN))
        {
            problem = "a player " + FAR + " blocks away shouldn't get Down the Drain";
        }
        if (problem != null)
        {
            fail(helper, players, problem);
            return;
        }
        // Removed with /kill: nobody gets it (a player who hasn't got it yet stands right by him).
        leaveAll(List.of(near));
        ServerPlayer bystander = joinPlayer(helper, "rat-reward-bystander", KING_AT.offset(2, 0, 0), 0.0F);
        List<ServerPlayer> all = List.of(killer, far, bystander);
        killed.startFightNow();
        killed.kill();
        helper.startSequence()
                .thenExecuteAfter(40, () -> {
                    if (DrossAdvancements.has(bystander, DrossAdvancements.DOWN_THE_DRAIN))
                    {
                        fail(helper, all, "a Rat King removed with /kill shouldn't give Down the Drain");
                    }
                    if (beaten.isRemoved())
                    {
                        fail(helper, all, "a beaten Rat King should lie there for a while, but he was removed");
                    }
                    leaveAll(all);
                    beaten.discard();
                    killed.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_ratking_bar", timeoutTicks = ARENA_WAIT_TICKS + 300)
    public static void bossBarOnlyAwakeAndNear(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::bossBarOnlyAwakeAndNear))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        RatKing king = spawnKing(helper, KING_AT);
        if (king == null)
        {
            return;
        }
        // Near (13 blocks: it doesn't wake him), and far beyond the bar's 40 blocks.
        ServerPlayer near = joinPlayer(helper, "rat-bar-near", KING_AT.offset(13, 0, 0), 0.0F);
        ServerPlayer far = joinPlayer(helper, "rat-bar-far", KING_AT.offset(FAR, 0, 0), 0.0F);
        List<ServerPlayer> players = List.of(near, far);
        helper.startSequence()
                .thenExecuteAfter(SETTLE, () -> {
                    if (!king.isAsleep() || shows(king, near))
                    {
                        fail(helper, players, "asleep, he should have no boss bar");
                    }
                    king.wakeUp();
                })
                .thenExecuteAfter(SETTLE, () -> {
                    if (king.getAction() != RatKingAction.WAKE || shows(king, near))
                    {
                        fail(helper, players, "while he wakes up there should be no boss bar yet");
                    }
                })
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.IDLE))
                .thenExecuteAfter(2, () -> {
                    if (!shows(king, near))
                    {
                        fail(helper, players, "awake, his boss bar should show to a player 13 blocks away");
                    }
                    if (shows(king, far))
                    {
                        fail(helper, players, "his boss bar shouldn't show to a player " + FAR + " blocks away");
                    }
                    king.invulnerableTime = 0;
                    king.hurt(level.damageSources().generic(), SMALL_HIT);
                })
                .thenExecuteAfter(2, () -> {
                    ServerBossEvent bar = king.bossBar();
                    float expected = king.getHealth() / king.getMaxHealth();
                    if (bar == null || Math.abs(bar.getProgress() - expected) > HEALTH_TOLERANCE)
                    {
                        fail(helper, players, "his boss bar should follow his health");
                    }
                    king.kill();
                })
                .thenExecuteAfter(2, () -> {
                    if (shows(king, near))
                    {
                        fail(helper, players, "his boss bar should go when he dies");
                    }
                    leaveAll(players);
                    king.discard();
                })
                .thenSucceed();
    }

    private static boolean shows(RatKing king, ServerPlayer player)
    {
        ServerBossEvent bar = king.bossBar();
        return bar != null && bar.getPlayers().contains(player);
    }

    // ---------------------------------------------------------------------------------------------
    // The arena
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = RAT_ARENA, batch = "dross_ratking_arena", timeoutTicks = ARENA_WAIT_TICKS + 40)
    public static void arenaIsDirtAndCoarseDirt(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::arenaIsDirtAndCoarseDirt, RAT_ARENA_SIZE, RAT_ARENA_SIZE))
        {
            return;
        }
        int middle = RAT_ARENA_SIZE / 2;
        BlockPos floor = new BlockPos(middle, FLOOR_Y, middle);
        RatKingArena.build(helper.getLevel(), helper.absolutePos(floor));
        int coarse = 0;
        int columns = 0;
        for (int dx = -RatKingArena.RADIUS; dx <= RatKingArena.RADIUS; dx++)
        {
            for (int dz = -RatKingArena.RADIUS; dz <= RatKingArena.RADIUS; dz++)
            {
                if (!RatKingArena.inFloor(dx, dz))
                {
                    continue;
                }
                BlockPos at = floor.offset(dx, 0, dz);
                BlockState state = helper.getBlockState(at);
                if (state.is(Blocks.COARSE_DIRT))
                {
                    coarse++;
                }
                else if (!state.is(Blocks.DIRT))
                {
                    helper.fail("the arena's floor should be only dirt and coarse dirt, but " + at.toShortString() + " is "
                            + state.getBlock());
                }
                for (int dy = 1; dy <= RatKingArena.CLEAR_HEIGHT; dy++)
                {
                    if (!helper.getBlockState(at.above(dy)).isAir())
                    {
                        helper.fail("the air over the arena should be clear, but " + at.above(dy).toShortString() + " isn't");
                    }
                }
                columns++;
            }
        }
        double share = coarse / (double) columns;
        if (share < RatKingArena.COARSE_SHARE - 0.05D || share > RatKingArena.COARSE_SHARE + 0.05D)
        {
            helper.fail("about a third of the floor should be coarse dirt, but it's " + share);
        }
        if (!helper.getBlockState(new BlockPos(0, FLOOR_Y, 0)).is(Blocks.SMOOTH_STONE))
        {
            helper.fail("the arena shouldn't reach past its circle");
        }
        LOGGER.info("[rat king test] the arena's floor: {} columns, {} coarse dirt ({}%)", columns, coarse,
                Math.round(share * 100.0D));
        helper.succeed();
    }

    /** The wide floor for the arena test, made in memory when a development server starts. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(RAT_ARENA_ID);
        template.load(event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK),
                ColossusGameTest.floorTemplate(RAT_ARENA_SIZE, RAT_ARENA_SIZE));
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** A fighting Rat King (asleep) at {@code at}, facing east. */
    @Nullable
    private static RatKing spawnKing(GameTestHelper helper, BlockPos at)
    {
        RatKing king = RatKing.spawnBoss(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(at)), YAW_EAST);
        if (king == null)
        {
            helper.fail("the Rat King couldn't be spawned");
        }
        return king;
    }

    /** A pig that stands still and can't be hurt: something for him (and his rats) to go for. */
    @Nullable
    private static Pig dummyTarget(GameTestHelper helper, BlockPos at)
    {
        Pig pig = EntityType.PIG.create(helper.getLevel());
        if (pig == null)
        {
            helper.fail("the test pig couldn't be made");
            return null;
        }
        Vec3 p = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        pig.moveTo(p.x, p.y, p.z, 0.0F, 0.0F);
        pig.setNoAi(true);
        pig.setInvulnerable(true);
        pig.setPersistenceRequired();
        helper.getLevel().addFreshEntity(pig);
        return pig;
    }

    /** A wild rat (as if it spawned by itself), frozen if {@code noAi}. */
    @Nullable
    private static Rat wildRat(GameTestHelper helper, EntityType<Rat> type, BlockPos at, boolean noAi)
    {
        ServerLevel level = helper.getLevel();
        Rat rat = type.create(level);
        if (rat == null)
        {
            helper.fail("a rat couldn't be made");
            return null;
        }
        Vec3 p = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        rat.moveTo(p.x, p.y, p.z, 0.0F, 0.0F);
        rat.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(p)), MobSpawnType.NATURAL, null, null);
        rat.setNoAi(noAi);
        rat.setPersistenceRequired();
        level.addFreshEntity(rat);
        return rat;
    }

    /** For thenWaitUntil: fails (so it waits on) until he's doing {@code action}. */
    private static void waitFor(GameTestHelper helper, RatKing king, RatKingAction action)
    {
        if (king.getAction() != action)
        {
            helper.fail("waiting for him to " + action + " (he's " + king.getAction() + ")");
        }
    }

    /** Removes him, his rats, his balls and clouds, and the target. */
    private static void cleanUp(GameTestHelper helper, RatKing king, LivingEntity target)
    {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(17, 11, 17))).inflate(16.0D);
        ServerLevel level = helper.getLevel();
        level.getEntitiesOfClass(Rat.class, area).forEach(Rat::discard);
        level.getEntitiesOfClass(PlagueBall.class, area).forEach(PlagueBall::discard);
        level.getEntitiesOfClass(PoisonCloud.class, area).forEach(PoisonCloud::discard);
        king.discard();
        target.discard();
    }

    private static void expectHealth(GameTestHelper helper, RatKing king, float expected, String when)
    {
        if (Math.abs(king.getHealth() - expected) > HEALTH_TOLERANCE)
        {
            helper.fail(when + ": his health should be " + expected + ", but it's " + king.getHealth());
        }
    }

    /** Logs the stand-in players out, then fails the test. */
    private static void fail(GameTestHelper helper, List<ServerPlayer> players, String problem)
    {
        leaveAll(players);
        helper.fail(problem);
    }
}
