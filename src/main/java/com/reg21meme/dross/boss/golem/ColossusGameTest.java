package com.reg21meme.dross.boss.golem;

import com.mojang.authlib.GameProfile;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.trophy.ColossusTrophies;
import com.reg21meme.dross.boss.trophy.ColossusTrophyBlock;
import com.reg21meme.dross.boss.trophy.ColossusTrophyBlockEntity;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModItems;
import io.netty.channel.embedded.EmbeddedChannel;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
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
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.CombatRules;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Headless tests (GameTests) for the Cinder Colossus fight. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}, or in a dev world with {@code /test runall}. They only exist
 * in the development environment: in a normal game nothing here runs.
 * <ul>
 *   <li>{@link #stagesClampAndAdvance}: a huge hit stops at 60% and starts the eruption (it can't be hurt during it),
 *       then it's in the Cracking stage; the next stops at 35% and bursts the shell; then it can die, and stays as a
 *       statue (vanilla would remove it after a second).</li>
 *   <li>{@link #rangedHitsAreWeakerEachStage}: an arrow's hit takes 50% of its damage in the Shell stage, 35% in
 *       Cracking and 15% in the Lava Core (before its armour); a melee hit of the same damage isn't reduced.</li>
 *   <li>{@link #minionsCrumbleWithTheirColossus}: its minions crumble when it dies.</li>
 *   <li>{@link #lavaPoolStaysSmallAndCoolsAway}: a lava pool reaches at most 3 blocks from its middle, never replaces a
 *       chest or a solid block, and is gone after its time.</li>
 *   <li>{@link #fireTrailGoesOut}: the fire trail goes out by itself.</li>
 *   <li>{@link #thrownRockPlacesNoBlocks}: a thrown rock shatters without placing (or breaking) a block.</li>
 *   <li>{@link #trophyForEveryPlayerInRange}: when it's beaten, each player near it gets one trophy of the drop design
 *       straight into their inventory, and nothing is dropped.</li>
 *   <li>{@link #trophyDropsInFrontWhenInventoryFull}: a player with a full inventory gets it dropped a block in front
 *       of them (at their feet if a block is there), a dead player where they died; only they can pick it up, it never
 *       despawns, and fire, lava, explosions and cactus can't destroy it.</li>
 *   <li>{@link #noTrophyForKillCommand}: removed with {@code /kill}, it gives no trophy.</li>
 *   <li>{@link #rightClickCyclesTrophyDesign}: right-clicking a placed trophy turns it 1, 2 ... 5, then 1 again,
 *       keeping the way it faces (holding a block too, which isn't placed).</li>
 * </ul>
 * The tests that spawn something, look for it or need ticks first wait until their arena has finished loading
 * ({@link #waitForArena}): far from spawn a test can start before its chunks are ready.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class ColossusGameTest
{
    /** The arena: a flat floor under a roof, made in memory when the server starts (no structure file needed). */
    private static final String ARENA = "colossus_test_arena";
    private static final ResourceLocation ARENA_ID = new ResourceLocation(Dross.MODID, ARENA);
    /** Arena width and depth (blocks), and height (floor included): room for the 4 x 5 Colossus. */
    private static final int ARENA_SIZE = 17;
    private static final int ARENA_HEIGHT = 10;
    /**
     * The floor's height: the test's structure block sits at relative y 0, so the template's bottom layer (the floor)
     * is at relative y 1...
     */
    private static final int FLOOR_Y = 1;
    /** ...and the middle of the arena just above it. */
    private static final BlockPos MIDDLE = new BlockPos(8, FLOOR_Y + 1, 8);
    /**
     * A roof of barrier blocks just above the arena (relative height). The test server's world is a normal, random
     * one, and the test framework clears the room for each test deep underground, often right under a cave: gravel or
     * sand left hanging over that room falls in once its chunk starts ticking (it once buried part of the rock test's
     * floor). The roof catches it, and anything else that falls or pours from above.
     */
    private static final int ROOF_Y = ARENA_HEIGHT + 1;
    /** Far more damage than it has health. */
    private static final float HUGE_HIT = 1000.0F;
    /** Ticks to wait after a stage change has finished playing. */
    private static final int AFTER = 4;
    /** How long a dead Colossus is checked to still stand (vanilla removes a dead mob after 20 ticks). */
    private static final int STILL_STANDING = 40;
    private static final float HEALTH_TOLERANCE = 0.01F;
    /** The damage of each hit in the ranged test (small enough to stay above every stage's threshold). */
    private static final float TEST_HIT = 10.0F;
    /** A dropped trophy lies (horizontally) this close to where it should be (blocks). */
    private static final double DROP_SPOT_TOLERANCE = 0.3D;
    /** Minecraft's yaw for facing east (+X) and west (-X). */
    private static final float YAW_EAST = -90.0F;
    private static final float YAW_WEST = 90.0F;
    /** An item's age that never counts up (it never despawns). */
    private static final int NEVER_DESPAWNS_AGE = -32768;
    /** Ticks a trophy is left in lava. */
    private static final int TROPHY_IN_LAVA = 60;
    /**
     * The longest a test waits for its arena to finish loading ({@link #waitForArena}), in ticks, added to the time
     * limit of each test that waits. (It's usually a few ticks, and none near spawn.)
     */
    private static final int ARENA_WAIT_TICKS = 100;

    private ColossusGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_colossus_stages", timeoutTicks = ARENA_WAIT_TICKS + 500)
    public static void stagesClampAndAdvance(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusGameTest::stagesClampAndAdvance))
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
        GolemDesigns.Design design = golem.getDesign();
        float max = golem.getMaxHealth();
        int erupt = CinderColossus.ANIMATION_BLEND_TICKS + design.eruptTicks() + AFTER + 10;
        int burst = CinderColossus.ANIMATION_BLEND_TICKS + design.breakTicks() + AFTER + 10;
        helper.startSequence()
                .thenExecute(() -> {
                    golem.hurt(level.damageSources().generic(), HUGE_HIT);
                    expectHealth(helper, golem, max * ColossusTuning.CRACKING_AT, "after the first huge hit");
                    if (golem.getAction() != GolemAction.ERUPT || golem.stage() != ColossusStage.SHELL)
                    {
                        helper.fail("a hit down to 60% should start the eruption, but it's " + golem.getAction());
                    }
                    golem.invulnerableTime = 0;
                    golem.hurt(level.damageSources().generic(), HUGE_HIT);
                    expectHealth(helper, golem, max * ColossusTuning.CRACKING_AT, "hit during the eruption (it can't be hurt)");
                })
                .thenExecuteAfter(erupt, () -> {
                    if (golem.getPhase() != GolemPhase.ERUPTED || golem.getAction() != GolemAction.IDLE)
                    {
                        helper.fail("after the eruption it should be in the Cracking stage, but it's " + golem.getPhase()
                                + " / " + golem.getAction());
                    }
                    if (Math.abs(golem.getAttributeValue(Attributes.ARMOR) - ColossusStage.CRACKING.armor) > 0.01D)
                    {
                        helper.fail("its armour should be the Cracking stage's");
                    }
                    golem.invulnerableTime = 0;
                    golem.hurt(level.damageSources().generic(), HUGE_HIT);
                    expectHealth(helper, golem, max * ColossusTuning.LAVA_CORE_AT, "after the second huge hit");
                    if (golem.getAction() != GolemAction.BREAK_SHELL)
                    {
                        helper.fail("a hit down to 35% should burst the shell, but it's " + golem.getAction());
                    }
                })
                .thenExecuteAfter(burst, () -> {
                    if (golem.getPhase() != GolemPhase.CORE || golem.stage() != ColossusStage.LAVA_CORE)
                    {
                        helper.fail("after the shell bursts it should be in the Lava Core stage, but it's " + golem.getPhase());
                    }
                    golem.invulnerableTime = 0;
                    golem.hurt(level.damageSources().generic(), HUGE_HIT);
                    if (!golem.isDeadOrDying() || golem.getAction() != GolemAction.DEATH)
                    {
                        helper.fail("in the Lava Core stage a huge hit should kill it (and start the cooling death)");
                    }
                })
                .thenExecuteAfter(STILL_STANDING, () -> {
                    if (golem.isRemoved())
                    {
                        helper.fail("a dead Colossus should stay (cooling into a statue), but it was removed");
                    }
                    golem.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_colossus_ranged", timeoutTicks = 100)
    public static void rangedHitsAreWeakerEachStage(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        CinderColossus golem = CinderColossus.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        if (golem == null)
        {
            helper.fail("the Colossus couldn't be spawned");
            return;
        }
        // The shooter and the arrow are never added to the world: a damage source only needs them to exist.
        Vec3 shooterAt = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(6, 0, 0)));
        Zombie shooter = EntityType.ZOMBIE.create(level);
        Arrow arrow = new Arrow(level, shooterAt.x, shooterAt.y + 1.0D, shooterAt.z);
        if (shooter == null)
        {
            helper.fail("the test zombie couldn't be made");
            return;
        }
        shooter.moveTo(shooterAt.x, shooterAt.y, shooterAt.z, YAW_WEST, 0.0F);
        arrow.setOwner(shooter);
        helper.startSequence()
                .thenExecute(() -> {
                    float[] expectedShare = {0.50F, 0.35F, 0.15F};
                    GolemPhase[] phases = {GolemPhase.DORMANT, GolemPhase.ERUPTED, GolemPhase.CORE};
                    for (int i = 0; i < phases.length; i++)
                    {
                        golem.setPhase(phases[i]);
                        ColossusStage stage = golem.stage();
                        golem.applyStage(stage);
                        if (Math.abs(stage.rangedDamageTaken - expectedShare[i]) > HEALTH_TOLERANCE)
                        {
                            helper.fail(stage + " should take " + expectedShare[i] + " of ranged damage, but its number is "
                                    + stage.rangedDamageTaken);
                        }
                        float armor = golem.getArmorValue();
                        float toughness = (float) golem.getAttributeValue(Attributes.ARMOR_TOUGHNESS);

                        float ranged = healthLost(golem, level.damageSources().arrow(arrow, shooter));
                        float expectedRanged = CombatRules.getDamageAfterAbsorb(TEST_HIT * expectedShare[i], armor, toughness);
                        if (Math.abs(ranged - expectedRanged) > HEALTH_TOLERANCE)
                        {
                            helper.fail(stage + ": an arrow hit of " + TEST_HIT + " should take " + expectedRanged
                                    + " health (" + expectedShare[i] + " of it, then armour), but it took " + ranged);
                        }

                        float melee = healthLost(golem, level.damageSources().mobAttack(shooter));
                        float expectedMelee = CombatRules.getDamageAfterAbsorb(TEST_HIT, armor, toughness);
                        if (Math.abs(melee - expectedMelee) > HEALTH_TOLERANCE)
                        {
                            helper.fail(stage + ": a melee hit of " + TEST_HIT + " should take " + expectedMelee
                                    + " health (all of it, then armour), but it took " + melee);
                        }
                    }
                    golem.discard();
                })
                .thenSucceed();
    }

    /** Hits it once at full health (no invulnerability left from the last hit) and says how much health it lost. */
    private static float healthLost(CinderColossus golem, DamageSource source)
    {
        golem.setHealth(golem.getMaxHealth());
        golem.invulnerableTime = 0;
        golem.hurt(source, TEST_HIT);
        return golem.getMaxHealth() - golem.getHealth();
    }

    @GameTest(template = ARENA, batch = "dross_colossus_minions", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void minionsCrumbleWithTheirColossus(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusGameTest::minionsCrumbleWithTheirColossus))
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
        StoneGolem minion = GolemMinion.summon(ModEntities.STONE_GOLEM.get(), level, golem,
                helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(4, 0, 0))), 0.0F);
        if (minion == null || !minion.belongsTo(golem))
        {
            helper.fail("the stone golem couldn't be summoned");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(5, golem::kill)
                .thenExecuteAfter(2, () -> {
                    if (!minion.isRemoved())
                    {
                        helper.fail("its minion should crumble when the Colossus dies");
                    }
                    golem.discard();
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_colossus_pool", timeoutTicks = ARENA_WAIT_TICKS + 300)
    public static void lavaPoolStaysSmallAndCoolsAway(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusGameTest::lavaPoolStaysSmallAndCoolsAway))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos chest = MIDDLE.offset(2, 0, 0);
        BlockPos stone = MIDDLE.offset(-2, 0, 1);
        helper.setBlock(chest, Blocks.CHEST);
        helper.setBlock(stone, Blocks.STONE);
        int placed = LavaPools.makePool(level, helper.absolutePos(MIDDLE), 1, level.random, true);
        if (placed < 10)
        {
            helper.fail("the pool should cover the floor round it, but only " + placed + " lava blocks were placed");
        }
        helper.assertBlockPresent(Blocks.CHEST, chest);
        helper.assertBlockPresent(Blocks.STONE, stone);
        for (int x = 0; x < ARENA_SIZE; x++)
        {
            for (int z = 0; z < ARENA_SIZE; z++)
            {
                for (int y = FLOOR_Y; y <= ARENA_HEIGHT; y++)
                {
                    BlockPos pos = new BlockPos(x, y, z);
                    if (!helper.getBlockState(pos).is(ModBlocks.CINDER_LAVA.get()))
                    {
                        continue;
                    }
                    double dx = x - MIDDLE.getX();
                    double dz = z - MIDDLE.getZ();
                    // (It follows the ground a block up or down: here, onto the stone block.)
                    if (dx * dx + dz * dz > ColossusTuning.POOL_RADIUS * ColossusTuning.POOL_RADIUS + 1.0E-6D
                            || y > MIDDLE.getY() + 1)
                    {
                        helper.fail("lava at " + pos.toShortString() + " is outside the pool's 3-block circle");
                    }
                }
            }
        }
        // Life 1: two cooling steps, then gone.
        helper.startSequence()
                .thenExecuteAfter(3 * CinderLavaBlock.STEP_TICKS, () -> {
                    for (int x = 0; x < ARENA_SIZE; x++)
                    {
                        for (int z = 0; z < ARENA_SIZE; z++)
                        {
                            for (int y = FLOOR_Y; y <= ARENA_HEIGHT; y++)
                            {
                                helper.assertBlockNotPresent(ModBlocks.CINDER_LAVA.get(), new BlockPos(x, y, z));
                            }
                        }
                    }
                    helper.assertBlockPresent(Blocks.CHEST, chest);
                    helper.assertBlockPresent(Blocks.STONE, stone);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_colossus_fire", timeoutTicks = ARENA_WAIT_TICKS + 200)
    public static void fireTrailGoesOut(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusGameTest::fireTrailGoesOut))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        if (!LavaPools.placeFire(level, helper.absolutePos(MIDDLE), true))
        {
            helper.fail("the fire trail couldn't be placed on the floor");
            return;
        }
        helper.assertBlockPresent(ModBlocks.CINDER_FIRE.get(), MIDDLE);
        helper.startSequence()
                .thenExecuteAfter(ColossusTuning.FIRE_TRAIL_MAX_TICKS + 5,
                        () -> helper.assertBlockNotPresent(ModBlocks.CINDER_FIRE.get(), MIDDLE))
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_colossus_rock", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void thrownRockPlacesNoBlocks(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusGameTest::thrownRockPlacesNoBlocks))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Vec3 from = helper.absoluteVec(new Vec3(MIDDLE.getX() + 0.5D, MIDDLE.getY() + 6.0D, MIDDLE.getZ() + 0.5D));
        ColossusRock rock = ColossusRock.thrown(level, null, from, ColossusTuning.ROCK_DAMAGE, false);
        rock.aim(helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(3, 0, 0))), 12);
        level.addFreshEntity(rock);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(rock.isRemoved(), "the rock is still flying"))
                .thenExecute(() -> {
                    for (int x = 0; x < ARENA_SIZE; x++)
                    {
                        for (int z = 0; z < ARENA_SIZE; z++)
                        {
                            helper.assertBlockPresent(Blocks.SMOOTH_STONE, new BlockPos(x, FLOOR_Y, z));
                            for (int y = FLOOR_Y + 1; y <= ARENA_HEIGHT; y++)
                            {
                                BlockPos pos = new BlockPos(x, y, z);
                                BlockState state = helper.getBlockState(pos);
                                if (!state.isAir())
                                {
                                    helper.fail("the rock left a block behind: " + state + " at " + pos.toShortString());
                                }
                            }
                        }
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_colossus_trophy", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void trophyForEveryPlayerInRange(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusGameTest::trophyForEveryPlayerInRange))
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
        ServerPlayer killer = joinPlayer(helper, "trophy-test-killer", MIDDLE.offset(6, 0, 0), 0.0F);
        ServerPlayer helperPlayer = joinPlayer(helper, "trophy-test-helper", MIDDLE.offset(-6, 0, 5), 0.0F);
        List<ServerPlayer> players = List.of(killer, helperPlayer);
        if (!killDuringLavaCore(helper, golem, killer, players))
        {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    leaveAll(players);
                    golem.discard();
                    List<ItemEntity> dropped = trophiesNear(helper);
                    if (!dropped.isEmpty())
                    {
                        dropped.forEach(ItemEntity::discard);
                        helper.fail("with room in their inventories nothing should drop, but " + dropped.size()
                                + " trophy item(s) lie on the ground");
                    }
                    for (ServerPlayer player : players)
                    {
                        List<ItemStack> mine = trophiesIn(player);
                        if (mine.size() != 1 || mine.get(0).getCount() != 1)
                        {
                            helper.fail(player.getScoreboardName() + " should have exactly one trophy in their inventory, "
                                    + "but has " + mine.size() + " stack(s)");
                        }
                        if (ColossusTrophies.designOf(mine.get(0)) != ColossusTuning.DROP_DESIGN)
                        {
                            helper.fail("the trophy should be design " + ColossusTuning.DROP_DESIGN);
                        }
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_colossus_trophy_full", timeoutTicks = ARENA_WAIT_TICKS + 200)
    public static void trophyDropsInFrontWhenInventoryFull(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusGameTest::trophyDropsInFrontWhenInventoryFull))
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
        // A full inventory, facing east (open floor ahead): dropped a block in front.
        ServerPlayer facingOpen = joinPlayer(helper, "trophy-test-full", MIDDLE.offset(6, 0, 0), YAW_EAST);
        // A full inventory, facing west into a block: dropped at their feet.
        ServerPlayer facingWall = joinPlayer(helper, "trophy-test-wall", MIDDLE.offset(-6, 0, 4), YAW_WEST);
        helper.setBlock(MIDDLE.offset(-7, 0, 4), Blocks.STONE);
        // Dead (waiting to respawn): dropped where they died.
        ServerPlayer dead = joinPlayer(helper, "trophy-test-dead", MIDDLE.offset(0, 0, -6), 0.0F);
        fillInventory(facingOpen);
        fillInventory(facingWall);
        dead.setHealth(0.0F);
        List<ServerPlayer> players = List.of(facingOpen, facingWall, dead);
        Vec3 ahead = facingOpen.position().add(1.0D, 0.0D, 0.0D);
        if (!killDuringLavaCore(helper, golem, facingOpen, players))
        {
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<ItemEntity> trophies = trophiesNear(helper);
                    if (trophies.size() != players.size())
                    {
                        leaveAll(players);
                        helper.fail("each of the " + players.size() + " players should get a dropped trophy, but "
                                + trophies.size() + " dropped");
                    }
                    for (ServerPlayer player : players)
                    {
                        if (!trophiesIn(player).isEmpty())
                        {
                            leaveAll(players);
                            helper.fail(player.getScoreboardName() + " has a full inventory or is dead, so the trophy "
                                    + "shouldn't be in their inventory");
                        }
                        ItemEntity mine = trophyOf(trophies, player);
                        Vec3 expected = player == facingOpen ? ahead : player.position();
                        String problem = mine == null ? "no trophy is kept for " + player.getScoreboardName()
                                : checkTrophy(level, mine, expected, player.getScoreboardName());
                        if (problem != null)
                        {
                            leaveAll(players);
                            helper.fail(problem);
                        }
                    }
                    // Lava right where the one in front lies: it's still there afterwards.
                    ItemEntity inFront = trophyOf(trophies, facingOpen);
                    helper.setBlock(helper.relativePos(inFront.blockPosition()), Blocks.LAVA);
                })
                .thenExecuteAfter(TROPHY_IN_LAVA, () -> {
                    List<ItemEntity> trophies = trophiesNear(helper);
                    leaveAll(players);
                    trophies.forEach(ItemEntity::discard);
                    golem.discard();
                    if (trophies.size() != players.size())
                    {
                        helper.fail("a trophy was destroyed in lava (" + trophies.size() + " left)");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_colossus_trophy_kill", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void noTrophyForKillCommand(GameTestHelper helper)
    {
        if (waitForArena(helper, ColossusGameTest::noTrophyForKillCommand))
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
        ServerPlayer player = joinPlayer(helper, "trophy-test-bystander", MIDDLE.offset(6, 0, 0), 0.0F);
        golem.kill();
        helper.startSequence()
                .thenExecuteAfter(2, () -> {
                    List<ItemEntity> trophies = trophiesNear(helper);
                    int held = trophiesIn(player).size();
                    leaveAll(List.of(player));
                    golem.discard();
                    if (!trophies.isEmpty() || held > 0)
                    {
                        helper.fail("a Colossus removed with /kill shouldn't give a trophy, but " + trophies.size()
                                + " dropped and " + held + " went into the inventory");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_colossus_trophy_cycle", timeoutTicks = 40)
    public static void rightClickCyclesTrophyDesign(GameTestHelper helper)
    {
        Direction facing = Direction.EAST;
        helper.setBlock(MIDDLE, ColossusTrophies.get(1).blockState(facing));
        Player player = helper.makeMockPlayer();
        // Holding a block: the trophy still turns, and the block isn't placed against it.
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.STONE, 4));
        int expected = 1;
        for (int click = 1; click <= ColossusTrophies.count() + 1; click++)
        {
            if (click == 3)
            {
                player.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY); // an empty hand turns it too
            }
            helper.useBlock(MIDDLE, player);
            expected = expected % ColossusTrophies.count() + 1;
            BlockState state = helper.getBlockState(MIDDLE);
            if (!state.is(ModBlocks.COLOSSUS_TROPHY.get()))
            {
                helper.fail("click " + click + ": the trophy is gone (" + state + ")");
                return;
            }
            if (state.getValue(ColossusTrophyBlock.DESIGN) != expected)
            {
                helper.fail("click " + click + ": the trophy should show design " + expected + ", but it shows "
                        + state.getValue(ColossusTrophyBlock.DESIGN));
            }
            if (state.getValue(ColossusTrophyBlock.FACING) != facing)
            {
                helper.fail("click " + click + ": the trophy should still face " + facing + ", but it faces "
                        + state.getValue(ColossusTrophyBlock.FACING));
            }
            if (!(helper.getBlockEntity(MIDDLE) instanceof ColossusTrophyBlockEntity trophy) || trophy.design() != expected)
            {
                helper.fail("click " + click + ": the trophy's block entity should draw design " + expected);
            }
        }
        if (expected != 2)
        {
            helper.fail("after " + (ColossusTrophies.count() + 1) + " clicks it should be back round to design 2");
        }
        for (Direction side : Direction.values())
        {
            helper.assertBlockNotPresent(Blocks.STONE, MIDDLE.relative(side));
        }
        helper.succeed();
    }

    /**
     * What's wrong with a player's dropped trophy, or null if it's right: it should lie (horizontally) at
     * {@code expected}, never despawn and shrug off fire, lava, explosions and cactus.
     */
    @Nullable
    private static String checkTrophy(ServerLevel level, ItemEntity item, Vec3 expected, String name)
    {
        if (ColossusTrophies.designOf(item.getItem()) != ColossusTuning.DROP_DESIGN)
        {
            return "the dropped trophy should be design " + ColossusTuning.DROP_DESIGN;
        }
        double off = Math.hypot(item.getX() - expected.x, item.getZ() - expected.z);
        if (off > DROP_SPOT_TOLERANCE)
        {
            return name + "'s trophy should lie at " + expected + ", but it's " + off + " blocks from there ("
                    + item.position() + ")";
        }
        if (item.getAge() != NEVER_DESPAWNS_AGE)
        {
            return "the trophy should never despawn";
        }
        for (DamageSource source : List.of(level.damageSources().lava(), level.damageSources().inFire(),
                level.damageSources().onFire(), level.damageSources().explosion(null, null), level.damageSources().cactus()))
        {
            if (item.hurt(source, HUGE_HIT) || item.isRemoved())
            {
                return "the trophy shouldn't be hurt by " + source.getMsgId();
            }
        }
        return null;
    }

    /** Takes it straight to the Lava Core stage (no health floor), then one big hit from the killer. False if it lived. */
    private static boolean killDuringLavaCore(GameTestHelper helper, CinderColossus golem, ServerPlayer killer,
                                              List<ServerPlayer> players)
    {
        golem.setPhase(GolemPhase.CORE);
        golem.hurt(helper.getLevel().damageSources().playerAttack(killer), HUGE_HIT);
        if (!golem.isDeadOrDying())
        {
            leaveAll(players);
            golem.discard();
            helper.fail("a huge hit in the Lava Core stage should kill it");
            return false;
        }
        return true;
    }

    /** Fills every slot of a player's inventory with full stacks of dirt (so a trophy can't fit). */
    private static void fillInventory(ServerPlayer player)
    {
        for (int slot = 0; slot < player.getInventory().items.size(); slot++)
        {
            player.getInventory().items.set(slot, new ItemStack(Items.DIRT, Items.DIRT.getMaxStackSize()));
        }
    }

    /** The trophy stacks in a player's inventory. */
    private static List<ItemStack> trophiesIn(ServerPlayer player)
    {
        List<ItemStack> found = new ArrayList<>();
        Inventory inventory = player.getInventory();
        for (int slot = 0; slot < inventory.getContainerSize(); slot++)
        {
            ItemStack stack = inventory.getItem(slot);
            if (stack.is(ModItems.COLOSSUS_TROPHY.get()))
            {
                found.add(stack);
            }
        }
        return found;
    }

    /** The dropped trophy kept for this player, or null. */
    @Nullable
    private static ItemEntity trophyOf(List<ItemEntity> trophies, ServerPlayer player)
    {
        for (ItemEntity item : trophies)
        {
            if (player.getUUID().equals(ownerOf(item)))
            {
                return item;
            }
        }
        return null;
    }

    /** Every trophy item lying in the test's arena. */
    private static List<ItemEntity> trophiesNear(GameTestHelper helper)
    {
        AABB area = new AABB(helper.absolutePos(BlockPos.ZERO), helper.absolutePos(new BlockPos(ARENA_SIZE, ARENA_HEIGHT, ARENA_SIZE)));
        return helper.getLevel().getEntitiesOfClass(ItemEntity.class, area.inflate(1.0D),
                item -> item.isAlive() && item.getItem().is(ModItems.COLOSSUS_TROPHY.get()));
    }

    /** The player a dropped item is kept for (only they can pick it up), or null. */
    @Nullable
    private static UUID ownerOf(ItemEntity item)
    {
        CompoundTag tag = new CompoundTag();
        item.saveWithoutId(tag);
        return tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
    }

    /**
     * A stand-in player on the server's player list, standing at {@code at} in the arena (vanilla's
     * {@code makeMockServerPlayerInLevel} crashes under Forge, so he gets an in-memory network channel, like the
     * enchant area's test owner), facing {@code yaw}. He isn't ticked, so he never picks anything up.
     */
    private static ServerPlayer joinPlayer(GameTestHelper helper, String name, BlockPos at, float yaw)
    {
        ServerLevel level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        player.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        if (player.serverLevel() != level || player.position().distanceToSqr(pos) > 0.01D)
        {
            player.teleportTo(level, pos.x, pos.y, pos.z, yaw, 0.0F);
        }
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        return player;
    }

    /** Logs the stand-in players out. Safe to call twice. */
    private static void leaveAll(List<ServerPlayer> players)
    {
        for (ServerPlayer player : players)
        {
            if (player.server.getPlayerList().getPlayer(player.getUUID()) == player)
            {
                player.server.getPlayerList().remove(player);
            }
        }
    }

    private static void expectHealth(GameTestHelper helper, CinderColossus golem, float expected, String when)
    {
        if (Math.abs(golem.getHealth() - expected) > HEALTH_TOLERANCE)
        {
            helper.fail(when + ": its health should be " + expected + ", but it's " + golem.getHealth());
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Waiting for the arena to load
    // ---------------------------------------------------------------------------------------------

    /**
     * Makes a test wait until its arena has finished loading. GameTests are laid out in a long row, and a test far
     * from spawn starts one tick after the test framework force-loads the chunks under it, before Minecraft has
     * finished loading them (it waits for the chunks round them too, a few ticks). Until then nothing in such a
     * chunk moves, its block ticks wait, and searches like {@code getEntitiesOfClass} skip the entities in it: a
     * trophy dropped there is in the world but can't be found, so a test would count fewer than were dropped (or miss
     * one that shouldn't have been). Near spawn the chunks are always loaded, and nothing waits.
     *
     * @param test the test itself: run again from the start once the arena has loaded
     * @return true if the arena is still loading (the test goes on later, so the caller just returns); false if the
     *         test can go ahead now
     */
    private static boolean waitForArena(GameTestHelper helper, Consumer<GameTestHelper> test)
    {
        if (arenaStillLoading(helper) == null)
        {
            return false;
        }
        helper.startSequence()
                .thenWaitUntil(() -> {
                    String loading = arenaStillLoading(helper);
                    if (loading != null)
                    {
                        helper.fail(loading); // keeps waiting (it only fails the test if it's still loading at the end)
                    }
                })
                .thenExecute(() -> {
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
                        // Run from a sequence, an unexpected error would stop the whole test server (run at the
                        // start, the framework just fails the test): it fails this test instead.
                        GameTestAssertException failure = new GameTestAssertException("the test threw " + e);
                        failure.initCause(e);
                        throw failure;
                    }
                });
        return true;
    }

    /**
     * Null once every chunk under the arena has finished loading: the entities in it tick and can be found, and its
     * block ticks run. Otherwise which chunk is still loading.
     */
    @Nullable
    private static String arenaStillLoading(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        BlockPos corner = helper.absolutePos(new BlockPos(0, FLOOR_Y, 0));
        BlockPos opposite = helper.absolutePos(new BlockPos(ARENA_SIZE - 1, FLOOR_Y, ARENA_SIZE - 1));
        int minX = SectionPos.blockToSectionCoord(Math.min(corner.getX(), opposite.getX()));
        int maxX = SectionPos.blockToSectionCoord(Math.max(corner.getX(), opposite.getX()));
        int minZ = SectionPos.blockToSectionCoord(Math.min(corner.getZ(), opposite.getZ()));
        int maxZ = SectionPos.blockToSectionCoord(Math.max(corner.getZ(), opposite.getZ()));
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
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
    // The arena template
    // ---------------------------------------------------------------------------------------------

    /**
     * GameTests are built on a structure template. Instead of shipping a file, the arena (a smooth stone floor with
     * air above, under a barrier roof: see {@link #ROOF_Y}) is made in memory when a development server starts, before
     * the tests are placed.
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
        // The template's bottom layer (0) is the floor at relative y FLOOR_Y, so the roof is layer ROOF_Y - FLOOR_Y.
        int roofLayer = ROOF_Y - FLOOR_Y;
        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(ARENA_SIZE, roofLayer + 1, ARENA_SIZE));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.SMOOTH_STONE.defaultBlockState()));
        palette.add(NbtUtils.writeBlockState(Blocks.BARRIER.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        for (int x = 0; x < ARENA_SIZE; x++)
        {
            for (int z = 0; z < ARENA_SIZE; z++)
            {
                CompoundTag floor = new CompoundTag();
                floor.put("pos", intList(x, 0, z));
                floor.putInt("state", 0);
                blocks.add(floor);
                CompoundTag roof = new CompoundTag();
                roof.put("pos", intList(x, roofLayer, z));
                roof.putInt("state", 1);
                blocks.add(roof);
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
