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
import net.minecraft.gametest.framework.GameTestSequence;
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
 *   <li>{@link #rageStopsAtHalfHealth}: a huge hit stops at 50% and starts the rage, during which he can't be hurt;
 *       then he's red-eyed and faster.</li>
 *   <li>{@link #burrowsWithMoreRatsEachTime}: every 25 health lost (a huge hit stops there) he burrows; under the ground
 *       he can't be hurt or hit, stays 80 ticks, calls 10 regular rats round his target and pops up 4 blocks from it;
 *       then 12, then 14; there's no limit, so three burrows leave 36 of his rats alive.</li>
 *   <li>{@link #rageBurrowsWithMoreEnragedRats}: in the Rage every 25 health, 50 ticks under, 8 enraged rats, then 9,
 *       then 10 (all 27 alive: every one is out before he pops up), and no burrow after the third.</li>
 *   <li>{@link #castsOneBallThenThreeInRage}: a cast throws one poison ball, three in the Rage; a ball bursts into a
 *       poison cloud.</li>
 *   <li>{@link #runsAtTheUsersSpeeds}: he runs at about 4.8 blocks a second, 6.4 in the Rage (measured).</li>
 *   <li>{@link #movesFollowTheUsersTable}: thousands of rolls in each distance band give the user's shares (within a
 *       few points), in both phases; a move on its cooldown drops out and the others share its chance; between his
 *       sweep's reach and the mid band he doesn't roll.</li>
 *   <li>{@link #chargeLocksTheSpotAndRunsToIt}: the spear charge locks his target's spot at the end of the wind-up (not
 *       before), dashes straight to it at 9 blocks a second (11 in the Rage) and stops on it; missed, he recovers for
 *       0.5 s, then the charge's cooldown runs (5 s, 3.5 s in the Rage).</li>
 *   <li>{@link #chargeHitsThenSweeps}: the lance hits the first thing in his way for 6 (8 in the Rage) and he goes
 *       straight into a sweep (9, 11 in the Rage); then the sweep's cooldown runs (22, 16 in the Rage).</li>
 *   <li>{@link #wallStopsTheCharge}: no charge with a wall in the way; made anyway, the wall stops him (and he
 *       recovers).</li>
 *   <li>{@link #cloudPoisonsBrieflyAndSkipsRats}: the cloud's poison lasts at most 50 ticks, never reaches a rat or
 *       anything outside it, and the cloud is gone after its time.</li>
 *   <li>{@link #cloudPoisonIsSteady}: standing in a cloud for its whole life is a steady Poison I (one point of damage
 *       every 25 ticks: at most 3 in its 60 ticks), not a hit every time the cloud tops the poison up.</li>
 *   <li>{@link #facesHisTargetMostOfTheTime}: fighting a stand-in player who walks round him (with a burrow, his rage
 *       and a burrow in the Rage), his body faces them (within 30 degrees) at least 90% of the time, his time under the
 *       ground and his charges' dashes (he runs where he aimed) not counted (the user's wish after a play test).</li>
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
    /** The speed test: he runs at a target far off for this long; his speed is measured between these two ticks. */
    private static final int SPEED_FROM_TICK = 12;
    private static final int SPEED_TO_TICK = 36;
    /** The user's speeds (blocks a second), and how close the measured ones must be. */
    private static final double USER_RUN_SPEED = 4.8D;
    private static final double USER_RAGE_RUN_SPEED = 6.4D;
    private static final double SPEED_TOLERANCE = 0.4D;
    /** Rolls of his move choice per band and phase, and how close (share) each one's share must be to the table's. */
    private static final int ROLLS = 4000;
    private static final double SHARE_TOLERANCE = 0.03D;
    /** The charge tests: where he stands (west side of the wide arena, facing east) and how far his target is. */
    private static final int CHARGE_FROM_X = 2;
    private static final int CHARGE_TARGET_DISTANCE = 12;
    /** The wind-up's target steps aside this far (blocks) a few ticks in, before the spot locks. */
    private static final int CHARGE_STEP_ASIDE = 3;
    private static final int CHARGE_STEP_ASIDE_AFTER = 3;
    /** The dash stops this close (blocks) to the locked spot, and lasts its run at its speed within this many ticks. */
    private static final double CHARGE_SPOT_TOLERANCE = 0.3D;
    private static final int CHARGE_TICK_TOLERANCE = 1;
    /** The hit test: the target's health (it must take every blow). */
    private static final float SWEEP_TEST_HEALTH = 100.0F;
    /** The wall test: the wall's distance in front of him, its half width and its height (blocks). */
    private static final int WALL_AT = 7;
    private static final int WALL_HALF_WIDTH = 2;
    private static final int WALL_HEIGHT = 4;

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
    public static void rageStopsAtHalfHealth(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::rageStopsAtHalfHealth))
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
                    expectHealth(helper, king, rageAt, "a huge hit above 50% (it stops there)");
                    if (king.getAction() != RatKingAction.RAGE || !king.isEnraged())
                    {
                        helper.fail("reaching 50% should start his rage, but he's " + king.getAction());
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
    public static void burrowsWithMoreRatsEachTime(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::burrowsWithMoreRatsEachTime))
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
        List<Integer> rats = RatKingTuning.RATS_PER_BURROW;
        long[] under = new long[1];
        king.startFightNow();
        king.setTarget(target);
        helper.startSequence()
                // The first burrow, at 175.
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
                    checkRats(helper, king, rats.get(0), false);
                    checkPopup(helper, king, target);
                })
                // The second, at 150.
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.IDLE))
                .thenExecute(() -> hitIntoBurrow(helper, king, max - 2 * RatKingTuning.BURROW_EVERY))
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.POPUP))
                .thenExecute(() -> checkRats(helper, king, rats.get(0) + rats.get(1), false))
                // The third, at 125: there's no limit, so he calls a full (bigger) set again.
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.IDLE))
                .thenExecute(() -> hitIntoBurrow(helper, king, max - 3 * RatKingTuning.BURROW_EVERY))
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.POPUP))
                .thenExecute(() -> {
                    checkRats(helper, king, rats.get(0) + rats.get(1) + rats.get(2), false);
                    LOGGER.info("[rat king test] three burrows at 175, 150 and 125 health, {} rats (no limit)", rats);
                    cleanUp(helper, king, target);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_ratking_rage_burrow", timeoutTicks = ARENA_WAIT_TICKS + 1400)
    public static void rageBurrowsWithMoreEnragedRats(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::rageBurrowsWithMoreEnragedRats))
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
        List<Integer> rats = RatKingTuning.RAGE_RATS_PER_BURROW;
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
                    checkRats(helper, king, rats.get(0), true);
                    checkPopup(helper, king, target);
                })
                // The second, at 50, and the third and last, at 25: more enraged rats each time (the last burrow's 10
                // come out a little faster, to all be out in its 50 ticks under the ground).
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.IDLE))
                .thenExecute(() -> hitIntoBurrow(helper, king, rageAt - 2 * RatKingTuning.RAGE_BURROW_EVERY))
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.POPUP))
                .thenExecute(() -> checkRats(helper, king, rats.get(0) + rats.get(1), true))
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.IDLE))
                .thenExecute(() -> hitIntoBurrow(helper, king, rageAt - 3 * RatKingTuning.RAGE_BURROW_EVERY))
                .thenWaitUntil(() -> waitFor(helper, king, RatKingAction.POPUP))
                .thenExecute(() -> {
                    checkRats(helper, king, rats.get(0) + rats.get(1) + rats.get(2), true);
                    if (king.checkpointBelow(king.getHealth()) != 0.0F)
                    {
                        helper.fail("after the Rage's third burrow (at 25) there should be no more, but the next is at "
                                + king.checkpointBelow(king.getHealth()));
                    }
                    LOGGER.info("[rat king test] three Rage burrows at 75, 50 and 25 health, {} enraged rats", rats);
                })
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

    // ---------------------------------------------------------------------------------------------
    // His speed, and which move he makes
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = RAT_ARENA, batch = "dross_ratking_speed", timeoutTicks = ARENA_WAIT_TICKS + 2 * SPEED_TO_TICK + 100)
    public static void runsAtTheUsersSpeeds(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::runsAtTheUsersSpeeds, RAT_ARENA_SIZE, RAT_ARENA_SIZE))
        {
            return;
        }
        int z = RAT_ARENA_SIZE / 2;
        BlockPos from = new BlockPos(1, FLOOR_Y + 1, z);
        RatKing king = spawnKing(helper, from);
        // Far beyond his moves' reach (more than 24 blocks): he only runs at it.
        Pig target = dummyTarget(helper, new BlockPos(RAT_ARENA_SIZE - 2, FLOOR_Y + 1, z));
        if (king == null || target == null)
        {
            return;
        }
        if (Math.abs(king.getAttributeBaseValue(Attributes.MOVEMENT_SPEED) - RatKingTuning.SPEED) > 1.0E-6D)
        {
            helper.fail("his speed should be " + RatKingTuning.SPEED + ", but it's " + king.getAttributeBaseValue(Attributes.MOVEMENT_SPEED));
            return;
        }
        king.startFightNow();
        king.setTarget(target);
        // Only running: his moves on their cooldowns.
        holdMoves(king);
        double[] measured = new double[2];
        Vec3[] mark = new Vec3[1];
        Vec3 start = helper.absoluteVec(Vec3.atBottomCenterOf(from));
        helper.startSequence()
                .thenExecuteAfter(SPEED_FROM_TICK, () -> mark[0] = king.position())
                .thenExecuteAfter(SPEED_TO_TICK - SPEED_FROM_TICK, () -> {
                    measured[0] = RatKingAttackGoal.flatDistance(mark[0], king.position()) * 20.0D / (SPEED_TO_TICK - SPEED_FROM_TICK);
                    // Again in the Rage, from the start.
                    king.skipToRage();
                    king.moveTo(start.x, start.y, start.z, YAW_EAST, 0.0F);
                    king.setDeltaMovement(Vec3.ZERO);
                    king.setTarget(target);
                    holdMoves(king);
                })
                .thenExecuteAfter(SPEED_FROM_TICK, () -> mark[0] = king.position())
                .thenExecuteAfter(SPEED_TO_TICK - SPEED_FROM_TICK, () -> {
                    measured[1] = RatKingAttackGoal.flatDistance(mark[0], king.position()) * 20.0D / (SPEED_TO_TICK - SPEED_FROM_TICK);
                    LOGGER.info("[rat king test] he runs at {} blocks a second, {} in the Rage (the user's {} and {})",
                            String.format("%.2f", measured[0]), String.format("%.2f", measured[1]), USER_RUN_SPEED, USER_RAGE_RUN_SPEED);
                    cleanUp(helper, king, target);
                    if (Math.abs(measured[0] - USER_RUN_SPEED) > SPEED_TOLERANCE)
                    {
                        helper.fail("he should run at about " + USER_RUN_SPEED + " blocks a second, but ran at " + measured[0]);
                    }
                    else if (Math.abs(measured[1] - USER_RAGE_RUN_SPEED) > SPEED_TOLERANCE)
                    {
                        helper.fail("in the Rage he should run at about " + USER_RAGE_RUN_SPEED + " blocks a second, but ran at "
                                + measured[1]);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = RAT_ARENA, batch = "dross_ratking_move_table", timeoutTicks = ARENA_WAIT_TICKS + 40)
    public static void movesFollowTheUsersTable(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::movesFollowTheUsersTable, RAT_ARENA_SIZE, RAT_ARENA_SIZE))
        {
            return;
        }
        int z = RAT_ARENA_SIZE / 2;
        RatKing king = spawnKing(helper, new BlockPos(1, FLOOR_Y + 1, z));
        // Targets in each band (blocks from his middle): close 3, between 4.5, mid 10, far 20, beyond 27.
        Pig close = dummyTarget(helper, new BlockPos(4, FLOOR_Y + 1, z));
        Pig between = dummyTarget(helper, new BlockPos(5, FLOOR_Y + 1, z + 2));
        Pig mid = dummyTarget(helper, new BlockPos(11, FLOOR_Y + 1, z));
        Pig far = dummyTarget(helper, new BlockPos(21, FLOOR_Y + 1, z));
        Pig beyond = dummyTarget(helper, new BlockPos(28, FLOOR_Y + 1, z));
        if (king == null || close == null || between == null || mid == null || far == null || beyond == null)
        {
            return;
        }
        RatKingAttackGoal goal = king.attackGoal();
        // 4.3 blocks ahead and 2 aside: out of his sweep's reach (more than 4 blocks to its edge), nearer than the mid band.
        between.moveTo(king.getX() + 4.3D, king.getY(), king.getZ() + 2.0D);
        List<LivingEntity> spawned = List.of(close, between, mid, far, beyond);
        String problem = null;
        Map<RatKingAttackGoal.Band, Pig> targets = Map.of(RatKingAttackGoal.Band.CLOSE, close, RatKingAttackGoal.Band.MID, mid,
                RatKingAttackGoal.Band.FAR, far, RatKingAttackGoal.Band.BEYOND, beyond);
        for (boolean rage : new boolean[] {false, true})
        {
            if (rage)
            {
                king.skipToRage();
            }
            for (RatKingAttackGoal.Band band : RatKingAttackGoal.Band.values())
            {
                Pig target = targets.get(band);
                if (goal.band(target) != band)
                {
                    problem = "a target " + RatKingAttackGoal.flatDistance(king.position(), target.position()) + " blocks away should be in the "
                            + band + " band, but it's in " + goal.band(target);
                    break;
                }
                RatKingTuning.Chances line = RatKingAttackGoal.chances(rage, band);
                problem = checkShares(goal, target, Map.of(RatKingAttackGoal.Choice.SWEEP, line.sweep(),
                        RatKingAttackGoal.Choice.CHARGE, line.charge(), RatKingAttackGoal.Choice.POISON, line.poison(),
                        RatKingAttackGoal.Choice.RUN_IN, line.runIn()), (rage ? "Rage " : "first phase ") + band);
                if (problem != null)
                {
                    break;
                }
            }
            if (problem == null)
            {
                // The charge on its cooldown: the poison shot and running in share its chance.
                RatKingTuning.Chances line = RatKingAttackGoal.chances(rage, RatKingAttackGoal.Band.MID);
                goal.setCooldown(RatKingAttackGoal.Move.CHARGE, 1000);
                problem = checkShares(goal, mid, Map.of(RatKingAttackGoal.Choice.SWEEP, 0, RatKingAttackGoal.Choice.CHARGE, 0,
                        RatKingAttackGoal.Choice.POISON, line.poison(), RatKingAttackGoal.Choice.RUN_IN, line.runIn()),
                        (rage ? "Rage" : "first phase") + " mid, the charge on its cooldown");
                goal.setCooldown(RatKingAttackGoal.Move.CHARGE, 0);
            }
            if (problem == null && (goal.band(between) != null || goal.choose(between) != null))
            {
                problem = "between his sweep's reach and the mid band he should just run in (no roll), but he picked "
                        + goal.choose(between);
            }
            if (problem == null)
            {
                goal.setCooldown(RatKingAttackGoal.Move.SWEEP, 1000);
                if (goal.choose(close) != null)
                {
                    problem = "up close with his sweep on its cooldown he should pick nothing, but he picked " + goal.choose(close);
                }
                goal.setCooldown(RatKingAttackGoal.Move.SWEEP, 0);
            }
            if (problem != null)
            {
                break;
            }
        }
        spawned.forEach(LivingEntity::discard);
        king.discard();
        if (problem != null)
        {
            helper.fail(problem);
            return;
        }
        helper.succeed();
    }

    /** Puts his moves on long cooldowns (so he only runs). */
    private static void holdMoves(RatKing king)
    {
        for (RatKingAttackGoal.Move move : RatKingAttackGoal.Move.values())
        {
            king.attackGoal().setCooldown(move, 1000);
        }
    }

    /** Rolls his choice at a target {@link #ROLLS} times: null if each choice's share is its weight's (rescaled), else what's wrong. */
    @Nullable
    private static String checkShares(RatKingAttackGoal goal, LivingEntity target, Map<RatKingAttackGoal.Choice, Integer> weights,
                                      String what)
    {
        Map<RatKingAttackGoal.Choice, Integer> counts = new TreeMap<>();
        for (int i = 0; i < ROLLS; i++)
        {
            RatKingAttackGoal.Choice choice = goal.choose(target);
            if (choice == null)
            {
                return what + ": he picked nothing";
            }
            counts.merge(choice, 1, Integer::sum);
        }
        int total = weights.values().stream().mapToInt(Integer::intValue).sum();
        StringBuilder shares = new StringBuilder();
        for (RatKingAttackGoal.Choice choice : RatKingAttackGoal.Choice.values())
        {
            double want = weights.getOrDefault(choice, 0) / (double) total;
            double got = counts.getOrDefault(choice, 0) / (double) ROLLS;
            shares.append(String.format(" %s %.1f%% (table %.1f%%)", choice, 100.0D * got, 100.0D * want));
            if (Math.abs(got - want) > SHARE_TOLERANCE)
            {
                return what + ": " + choice + " should be picked " + Math.round(100.0D * want) + "% of the time, but was "
                        + String.format("%.1f", 100.0D * got) + "%";
            }
        }
        LOGGER.info("[rat king test] {}:{}", what, shares);
        return null;
    }

    // ---------------------------------------------------------------------------------------------
    // The spear charge
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = RAT_ARENA, batch = "dross_ratking_charge_miss", timeoutTicks = ARENA_WAIT_TICKS + 400)
    public static void chargeLocksTheSpotAndRunsToIt(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::chargeLocksTheSpotAndRunsToIt, RAT_ARENA_SIZE, RAT_ARENA_SIZE))
        {
            return;
        }
        int z = RAT_ARENA_SIZE / 2;
        BlockPos from = new BlockPos(CHARGE_FROM_X, FLOOR_Y + 1, z);
        RatKing king = spawnKing(helper, from);
        BlockPos firstSpot = from.offset(CHARGE_TARGET_DISTANCE, 0, 0);
        Pig target = dummyTarget(helper, firstSpot);
        if (king == null || target == null)
        {
            return;
        }
        king.startFightNow();
        king.setTarget(target);
        GameTestSequence sequence = helper.startSequence();
        chargeMiss(helper, sequence, king, target, from, firstSpot, false);
        sequence.thenExecute(() -> king.skipToRage());
        chargeMiss(helper, sequence, king, target, from, firstSpot, true);
        sequence.thenExecute(() -> cleanUp(helper, king, target)).thenSucceed();
    }

    /**
     * One missed charge: he starts it at {@code target} (on {@code spot}); a few ticks in, the target steps aside; when
     * the spot locks (at the end of the wind-up) it must be where the target stepped to; then the target steps right out
     * of the way, and he must dash to the locked spot at his dash speed, stop on it, recover for
     * {@link RatKingTuning#CHARGE_RECOVER_TICKS} and put the charge on its cooldown.
     */
    private static void chargeMiss(GameTestHelper helper, GameTestSequence sequence, RatKing king, Pig target, BlockPos from,
                                   BlockPos spot, boolean rage)
    {
        String phase = rage ? "in the Rage" : "in the first phase";
        Vec3 start = helper.absoluteVec(Vec3.atBottomCenterOf(from));
        Vec3 aside = helper.absoluteVec(Vec3.atBottomCenterOf(spot.offset(0, 0, CHARGE_STEP_ASIDE)));
        Vec3 away = helper.absoluteVec(Vec3.atBottomCenterOf(spot.offset(0, 0, -RAT_ARENA_SIZE / 2 + 1)));
        long[] skidAt = new long[1];
        boolean[] actedWhileRecovering = new boolean[1];
        sequence.thenExecute(() -> {
                    king.moveTo(start.x, start.y, start.z, YAW_EAST, 0.0F);
                    king.face(YAW_EAST);
                    king.setDeltaMovement(Vec3.ZERO);
                    Vec3 p = helper.absoluteVec(Vec3.atBottomCenterOf(spot));
                    target.moveTo(p.x, p.y, p.z);
                    king.setTarget(target);
                    begin(helper, king, RatKingAttackGoal.Move.CHARGE, target);
                })
                .thenExecuteAfter(CHARGE_STEP_ASIDE_AFTER, () -> {
                    if (king.attackGoal().chargeSpot() != null)
                    {
                        helper.fail("the charge's spot locked before the end of the wind-up (" + phase + ")");
                    }
                    target.moveTo(aside.x, aside.y, aside.z);
                })
                .thenWaitUntil(() -> helper.assertTrue(king.attackGoal().chargeSpot() != null, "waiting for the spot to lock"))
                .thenExecute(() -> {
                    Vec3 locked = king.attackGoal().chargeSpot();
                    if (RatKingAttackGoal.flatDistance(locked, aside) > 1.0E-3D)
                    {
                        helper.fail("the charge should lock where his target stood at the end of the wind-up " + aside
                                + ", but locked " + locked + " (" + phase + ")");
                    }
                    target.moveTo(away.x, away.y, away.z); // it dodges right out of the way: a miss
                })
                .thenWaitUntil(() -> helper.assertTrue(king.getAction() == RatKingAction.CHARGE_SKID, "waiting for the dash to end"))
                .thenExecute(() -> {
                    skidAt[0] = helper.getLevel().getGameTime();
                    RatKingAttackGoal.Dash dash = king.attackGoal().lastDash();
                    double speed = rage ? RatKingTuning.RAGE_CHARGE_SPEED : RatKingTuning.CHARGE_SPEED;
                    double run = RatKingAttackGoal.flatDistance(dash.from(), dash.spot());
                    int expected = (int) Math.ceil(run / speed);
                    LOGGER.info("[rat king test] charge {}: locked at tick {} of the wind-up, ran {} blocks in {} ticks ({} blocks a "
                                    + "second), stopped {} from the spot", phase, dash.lockedAt(), String.format("%.2f", run), dash.ticks(),
                            String.format("%.1f", run * 20.0D / Math.max(1, dash.ticks())),
                            String.format("%.3f", RatKingAttackGoal.flatDistance(dash.end(), dash.spot())));
                    if (dash.lockedAt() != RatKingTuning.CHARGE_WINDUP_TICKS)
                    {
                        helper.fail("the spot should lock at tick " + RatKingTuning.CHARGE_WINDUP_TICKS + " of the wind-up, but locked at "
                                + dash.lockedAt() + " (" + phase + ")");
                    }
                    if (dash.hit() != null || dash.wall())
                    {
                        helper.fail("the dash should have missed (nothing in its way), but hit " + dash.hit() + " (wall " + dash.wall() + ")");
                    }
                    if (RatKingAttackGoal.flatDistance(king.position(), dash.spot()) > CHARGE_SPOT_TOLERANCE)
                    {
                        helper.fail("he should stop on the locked spot, but stopped "
                                + RatKingAttackGoal.flatDistance(king.position(), dash.spot()) + " blocks from it (" + phase + ")");
                    }
                    if (Math.abs(dash.ticks() - expected) > CHARGE_TICK_TOLERANCE)
                    {
                        helper.fail("at " + speed * 20.0D + " blocks a second the dash should take " + expected + " ticks, but took "
                                + dash.ticks() + " (" + phase + ")");
                    }
                })
                .thenWaitUntil(() -> {
                    if (king.attackGoal().current() == RatKingAttackGoal.Move.CHARGE && king.getAction() != RatKingAction.CHARGE_SKID)
                    {
                        actedWhileRecovering[0] = true;
                    }
                    helper.assertTrue(king.attackGoal().current() != RatKingAttackGoal.Move.CHARGE, "waiting for him to recover");
                })
                .thenExecute(() -> {
                    if (actedWhileRecovering[0])
                    {
                        helper.fail("while he recovers he should do nothing else (" + phase + ")");
                    }
                    long recovered = helper.getLevel().getGameTime() - skidAt[0];
                    int cooldown = rage ? RatKingTuning.RAGE_CHARGE_COOLDOWN : RatKingTuning.CHARGE_COOLDOWN;
                    int left = king.attackGoal().cooldownLeft(RatKingAttackGoal.Move.CHARGE);
                    if (Math.abs(recovered - RatKingTuning.CHARGE_RECOVER_TICKS) > CHARGE_TICK_TOLERANCE)
                    {
                        helper.fail("he should recover for " + RatKingTuning.CHARGE_RECOVER_TICKS + " ticks, but took " + recovered);
                    }
                    if (left < cooldown - 2 || left > cooldown)
                    {
                        helper.fail("after a charge its cooldown should be " + cooldown + " ticks, but it's " + left + " (" + phase + ")");
                    }
                    if (king.attackGoal().fits(RatKingAttackGoal.Move.CHARGE, target))
                    {
                        helper.fail("on its cooldown he shouldn't be able to charge again");
                    }
                });
    }

    @GameTest(template = RAT_ARENA, batch = "dross_ratking_charge_hit", timeoutTicks = ARENA_WAIT_TICKS + 400)
    public static void chargeHitsThenSweeps(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::chargeHitsThenSweeps, RAT_ARENA_SIZE, RAT_ARENA_SIZE))
        {
            return;
        }
        int z = RAT_ARENA_SIZE / 2;
        BlockPos from = new BlockPos(CHARGE_FROM_X, FLOOR_Y + 1, z);
        RatKing king = spawnKing(helper, from);
        Pig target = dummyTarget(helper, from.offset(CHARGE_TARGET_DISTANCE, 0, 0));
        if (king == null || target == null)
        {
            return;
        }
        target.setInvulnerable(false);
        target.getAttribute(Attributes.MAX_HEALTH).setBaseValue(SWEEP_TEST_HEALTH);
        king.startFightNow();
        king.setTarget(target);
        GameTestSequence sequence = helper.startSequence();
        chargeHit(helper, sequence, king, target, from, false);
        sequence.thenExecute(() -> king.skipToRage());
        chargeHit(helper, sequence, king, target, from, true);
        sequence.thenExecute(() -> cleanUp(helper, king, target)).thenSucceed();
    }

    /** One charge that hits: the jab's damage, straight into a sweep and its damage, then the sweep's cooldown. */
    private static void chargeHit(GameTestHelper helper, GameTestSequence sequence, RatKing king, Pig target, BlockPos from,
                                  boolean rage)
    {
        String phase = rage ? "in the Rage" : "in the first phase";
        float jab = rage ? RatKingTuning.RAGE_CHARGE_DAMAGE : RatKingTuning.CHARGE_DAMAGE;
        float sweep = rage ? RatKingTuning.RAGE_SWEEP_DAMAGE : RatKingTuning.SWEEP_DAMAGE;
        int sweepCooldown = rage ? RatKingTuning.RAGE_SWEEP_COOLDOWN : RatKingTuning.SWEEP_COOLDOWN;
        Vec3 start = helper.absoluteVec(Vec3.atBottomCenterOf(from));
        Vec3 spot = helper.absoluteVec(Vec3.atBottomCenterOf(from.offset(CHARGE_TARGET_DISTANCE, 0, 0)));
        sequence.thenExecute(() -> {
                    king.moveTo(start.x, start.y, start.z, YAW_EAST, 0.0F);
                    king.face(YAW_EAST);
                    king.setDeltaMovement(Vec3.ZERO);
                    target.moveTo(spot.x, spot.y, spot.z);
                    target.setHealth(SWEEP_TEST_HEALTH);
                    target.invulnerableTime = 0;
                    king.setTarget(target);
                    begin(helper, king, RatKingAttackGoal.Move.CHARGE, target);
                })
                .thenWaitUntil(() -> {
                    RatKingAttackGoal.Dash dash = king.attackGoal().lastDash();
                    helper.assertTrue(dash != null && dash.hit() == target && king.getAction() != RatKingAction.CHARGE,
                            "waiting for the lance to hit");
                })
                .thenExecute(() -> {
                    float taken = SWEEP_TEST_HEALTH - target.getHealth();
                    if (Math.abs(taken - jab) > HEALTH_TOLERANCE)
                    {
                        helper.fail("the charge's lance should hit for " + jab + " (" + phase + "), but took " + taken);
                    }
                    if (king.getAction() != RatKingAction.SWEEP || king.attackGoal().current() != RatKingAttackGoal.Move.SWEEP)
                    {
                        helper.fail("after the lance's hit (his target in reach) he should go straight into a sweep, but he's "
                                + king.getAction());
                    }
                })
                .thenWaitUntil(() -> helper.assertTrue(king.attackGoal().current() == null, "waiting for the sweep to end"))
                .thenExecute(() -> {
                    float taken = SWEEP_TEST_HEALTH - target.getHealth();
                    LOGGER.info("[rat king test] charge {}: the lance and the sweep took {} (jab {}, sweep {})", phase, taken, jab, sweep);
                    if (Math.abs(taken - jab - sweep) > HEALTH_TOLERANCE)
                    {
                        helper.fail("the lance and the sweep after it should take " + (jab + sweep) + " (" + phase + "), but took " + taken);
                    }
                    int left = king.attackGoal().cooldownLeft(RatKingAttackGoal.Move.SWEEP);
                    if (left < sweepCooldown - 2 || left > sweepCooldown)
                    {
                        helper.fail("after a sweep its cooldown should be " + sweepCooldown + " ticks, but it's " + left + " (" + phase + ")");
                    }
                    if (king.attackGoal().cooldownLeft(RatKingAttackGoal.Move.CHARGE) <= 0)
                    {
                        helper.fail("the charge should be on its cooldown after it hit");
                    }
                });
    }

    @GameTest(template = RAT_ARENA, batch = "dross_ratking_charge_wall", timeoutTicks = ARENA_WAIT_TICKS + 200)
    public static void wallStopsTheCharge(GameTestHelper helper)
    {
        if (waitForArena(helper, RatKingFightGameTest::wallStopsTheCharge, RAT_ARENA_SIZE, RAT_ARENA_SIZE))
        {
            return;
        }
        int z = RAT_ARENA_SIZE / 2;
        BlockPos from = new BlockPos(CHARGE_FROM_X, FLOOR_Y + 1, z);
        RatKing king = spawnKing(helper, from);
        Pig target = dummyTarget(helper, from.offset(2 * WALL_AT, 0, 0));
        if (king == null || target == null)
        {
            return;
        }
        List<BlockPos> wall = new java.util.ArrayList<>();
        for (int dz = -WALL_HALF_WIDTH; dz <= WALL_HALF_WIDTH; dz++)
        {
            for (int dy = 0; dy < WALL_HEIGHT; dy++)
            {
                BlockPos at = from.offset(WALL_AT, dy, dz);
                helper.setBlock(at, Blocks.STONE);
                wall.add(at);
            }
        }
        king.startFightNow();
        king.setTarget(target);
        double wallX = helper.absolutePos(from.offset(WALL_AT, 0, 0)).getX();
        helper.startSequence()
                .thenExecute(() -> {
                    if (king.attackGoal().fits(RatKingAttackGoal.Move.CHARGE, target))
                    {
                        helper.fail("with a wall in the way he shouldn't charge");
                    }
                    begin(helper, king, RatKingAttackGoal.Move.CHARGE, target); // made anyway
                })
                .thenWaitUntil(() -> helper.assertTrue(king.getAction() == RatKingAction.CHARGE_SKID, "waiting for the wall to stop him"))
                .thenExecute(() -> {
                    RatKingAttackGoal.Dash dash = king.attackGoal().lastDash();
                    if (dash == null || !dash.wall() || dash.hit() != null)
                    {
                        helper.fail("the wall should have stopped the dash (wall " + (dash != null && dash.wall()) + ")");
                    }
                    if (king.getBoundingBox().maxX > wallX + 1.0E-3D)
                    {
                        helper.fail("he should stop in front of the wall, but he's at " + king.getX());
                    }
                    LOGGER.info("[rat king test] the wall stopped his charge after {} ticks, {} blocks from where he set off",
                            dash == null ? -1 : dash.ticks(), dash == null ? 0 : String.format("%.2f", RatKingAttackGoal.flatDistance(dash.from(), dash.end())));
                    wall.forEach(at -> helper.setBlock(at, Blocks.AIR));
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
                    // Under the ground (diving, tunnelling, until he bursts out) doesn't count, nor his charges' dashes (he
                    // runs where he aimed), nor a target inside him.
                    if (!king.isAlive() || !king.isFightActive() || king.isUnderground() || king.attackGoal().isDashing()
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
