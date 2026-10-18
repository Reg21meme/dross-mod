package com.reg21meme.dross.boss.ratking.lair;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.ratking.Rat;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.boss.ratking.RatKingEvents;
import com.reg21meme.dross.boss.ratking.RatKingTuning;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.RatCaves;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.registry.ModBlocks;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LightBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless checks (GameTests) of the Rat King's home in the rat caves under Blightwood ({@link RatKingLairKeeper}), the
 * rat caves' stronger Plague Ravagers and the rats' spawn rules. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}; each has its own batch. They use the rat cave nearest to the
 * Blightwood nearest the hub ({@link RatCaves#findNearest}), generate its chunks and keep them loaded while they run; the
 * waits are in real time (the test server ticks as fast as it can), the tick limits only a backstop.
 * <ol>
 *   <li>{@link #kingAppearsOnceAndNeverReturns}: a stand-in player arriving in the cave finds the Rat King asleep on its
 *       nest (feet on the top carpet, facing the arrival tunnel), and only one, still one a while later; killed, none ever
 *       comes back (no respawn): not for the player who stayed (over two waits), not for a newcomer.</li>
 *   <li>{@link #caveRavagersAreStronger}: a Plague Ravager spawned naturally in the cave has 1.5 times the health (full)
 *       and bite damage, still after a save and load; one spawned naturally outside the cave (above it, or in the
 *       Overworld), a summoned one or a spawn egg's in the cave, and a Plague Rat in the cave are normal.</li>
 *   <li>{@link #ratSpawnRules}: both rats spawn on the ground ({@code ON_GROUND}); in the dark on the cave's floor
 *       (bricks and Sewer Sludge) they may spawn, in mid-air they may not; in the sewers' dim light (block light 7: glow
 *       lichen's) they may, in brighter light (8) not; outside the sewers (over the cave, in the open) not even at 7.</li>
 *   <li>{@link #snoresAtTheGrandEntrance}: while the cave's king hasn't appeared (and hasn't been beaten), a stand-in at
 *       the sewer's grand entrance hears his snores now and then, one 40 blocks off hears none; once he's beaten, the
 *       pipe goes quiet.</li>
 * </ol>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RatKingLairGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The tick limits are only a backstop behind the real-time limits. */
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** Finding, generating and loading the cave gets at most this long (real time). */
    private static final long LOAD_TIMEOUT_MILLIS = 3 * 60 * 1000L;
    /** The king must appear within this long of a stand-in arriving (real time). */
    private static final long APPEAR_TIMEOUT_MILLIS = 60 * 1000L;
    /** His body must vanish within this long of his death (real time; it lies there about 15 seconds of game time). */
    private static final long VANISH_TIMEOUT_MILLIS = 60 * 1000L;
    /** A light block's light must reach the spot below it within this long (real time). */
    private static final long LIGHT_TIMEOUT_MILLIS = 30 * 1000L;
    /** Ticks waited to see that nothing appears (several of the keeper's looks). */
    private static final int QUIET_TICKS = 6 * RatKingTuning.RAT_CAVE_CHECK_INTERVAL_TICKS;
    /** The light block hangs {@link #LIGHT_ABOVE} blocks over the spot at this level, so the spot gets 7 (the sewers' limit)... */
    private static final int DIM_LIGHT_LEVEL = RatKingTuning.RAT_SEWER_SPAWN_MAX_BLOCK_LIGHT + 2;
    /** ...then at this level, so it gets 8 (too bright). */
    private static final int BRIGHT_LIGHT_LEVEL = RatKingTuning.RAT_SEWER_SPAWN_MAX_BLOCK_LIGHT + 3;
    /** The "outside the sewers" spot is this many blocks over the ground above the cave (in the open, in Blightwood). */
    private static final int OUTSIDE_ABOVE_GROUND = 30;
    /** The snores must reach the stand-in at the grand entrance within this long (real time). */
    private static final long SNORE_TIMEOUT_MILLIS = 60 * 1000L;
    /** The stand-in who mustn't hear them stands this far from the grand entrance. */
    private static final int FAR_LISTENER = 40;
    /** Ticks waited to see the pipe has gone quiet (more than the longest gap between snores). */
    private static final int QUIET_SNORE_TICKS = 2 * RatKingTuning.RAT_CAVE_SNORE_MAX_TICKS;
    /** He must lie this close to the middle of the nest's top carpet (blocks, sideways and up or down). */
    private static final double NEST_TOLERANCE = 0.3D;
    private static final double NEST_HEIGHT_TOLERANCE = 0.1D;
    /** ...facing the arrival tunnel to within this many degrees. */
    private static final float YAW_TOLERANCE = 1.0F;
    /** The biome search for the Blightwood nearest the hub starts at this height. */
    private static final int SAMPLE_Y = 72;
    /** Spawn-rule spots are looked for on the cave's floor at least this far from its middle (the nest and hoard)... */
    private static final int SPOT_MIN_DISTANCE = 9;
    /** ...and at most this far. */
    private static final int SPOT_MAX_DISTANCE = 18;
    /** The light block hangs this many blocks above a dark spot (the cave is 6 to 14 blocks tall there). */
    private static final int LIGHT_ABOVE = 2;
    /** The mid-air spot is this many blocks above the floor. */
    private static final int MID_AIR = 3;
    /** The "outside the cave" Ravager spawns this many blocks above the top of the cave's box (in the rock over it). */
    private static final int ABOVE_THE_CAVE = 4;
    /** Natural spawn rules use randomness: each is asked this many times. */
    private static final int RULE_TRIES = 8;
    /** Tolerance for attribute values. */
    private static final double EPSILON = 1.0E-6D;

    /** The rat cave the tests use (found once). */
    @Nullable
    private static RatCaves.RatCave testCave;

    private RatKingLairGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The king appears asleep on his nest, once, and never comes back after he's beaten
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ratking_lair", timeoutTicks = BACKSTOP_TICKS)
    public static void kingAppearsOnceAndNeverReturns(GameTestHelper helper)
    {
        KingRun run = new KingRun(helper.getLevel().getServer());
        helper.startSequence()
                .thenExecute(() -> run.guard(run::load))
                .thenWaitUntil(() -> run.guard(() -> run.cave.waitForEntities()))
                .thenExecute(() -> run.guard(run::arrive))
                .thenWaitUntil(() -> run.guard(() -> run.waitFor(() -> run.livingKing() != null, "the Rat King to appear")))
                .thenExecute(() -> run.guard(run::checkAsleepOnNest))
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> run.guard(run::checkStillOneThenKill))
                .thenWaitUntil(() -> run.guard(() -> run.waitFor(run::bodyGone, "his body to vanish")))
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> run.guard(run::checkNoneAfterHisDeath))
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> run.guard(run::checkNoneThenNewcomer))
                .thenIdle(2 * QUIET_TICKS)
                .thenExecute(() -> run.guard(run::checkNeverBack))
                .thenExecute(run::cleanUp)
                .thenSucceed();
    }

    /** The king test's state. */
    private static final class KingRun
    {
        final MinecraftServer server;
        TestCave cave;
        @Nullable
        ServerPlayer player;
        @Nullable
        UUID first;
        long deadline;
        int arrivals;
        boolean failed;

        KingRun(MinecraftServer server)
        {
            this.server = server;
        }

        /** Runs a step; on a failure (not a "still waiting"), cleans up first. */
        void guard(Runnable step)
        {
            if (failed)
            {
                return;
            }
            try
            {
                step.run();
            }
            catch (Waiting e)
            {
                throw e;
            }
            catch (RuntimeException e)
            {
                failed = true;
                LOGGER.error("[rat king lair test] {}", e.getMessage());
                cleanUp();
                throw e instanceof GameTestAssertException assertion ? assertion : wrap(e);
            }
        }

        void load()
        {
            cave = TestCave.load(server);
            deadline = Util.getMillis() + LOAD_TIMEOUT_MILLIS;
        }

        RatKingLairData data()
        {
            return RatKingLairData.get(server);
        }

        /** A new stand-in player arrives on the cave's arrival spot (the one before him, if any, has left). */
        void arrive()
        {
            if (arrivals == 0)
            {
                cave.clear(); // whatever an earlier run left there (now that its entities are loaded)
                if (countKings(false) != 0)
                {
                    throw new GameTestAssertException("the cave still has a Rat King after clearing it");
                }
            }
            logOut(player);
            arrivals++;
            BlockPos arrival = cave.cave.arrival();
            player = joinPlayer(cave.dross, "rat-cave-test-" + arrivals, new Vec3(arrival.getX() + 0.5D, arrival.getY(), arrival.getZ() + 0.5D));
            deadline = Util.getMillis() + APPEAR_TIMEOUT_MILLIS;
        }

        /** Throws (keeps waiting) until the condition holds or the deadline passes. */
        void waitFor(BooleanSupplier condition, String what)
        {
            if (!condition.getAsBoolean() && Util.getMillis() < deadline)
            {
                throw new Waiting("waiting for " + what);
            }
        }

        /** The cave's living king (by its saved UUID), or null. */
        @Nullable
        RatKing livingKing()
        {
            UUID id = data().king(cave.cave.id());
            return id != null && cave.dross.getEntity(id) instanceof RatKing king && king.isAlive() ? king : null;
        }

        /** Rat Kings in the cave (living only, or lying dead too). */
        int countKings(boolean livingOnly)
        {
            AABB box = AABB.of(cave.cave.bounds()).inflate(RatKingTuning.RAT_CAVE_HOME_MARGIN);
            return cave.dross.getEntitiesOfClass(RatKing.class, box, k -> !k.isShowcase() && (!livingOnly || k.isAlive())).size();
        }

        void checkAsleepOnNest()
        {
            RatKing king = requireKing("no Rat King appeared within " + APPEAR_TIMEOUT_MILLIS / 1000 + " s of a player arriving in the cave");
            checkOnNest(king, "the first king");
            if (countKings(false) != 1)
            {
                throw new GameTestAssertException(countKings(false) + " Rat Kings in the cave, not 1");
            }
            first = king.getUUID();
            LOGGER.info("[rat king lair test] The Rat King appeared asleep on the nest at {} (cave {}), facing the arrival tunnel.",
                    king.blockPosition().toShortString(), cave.cave.centre().toShortString());
        }

        void checkOnNest(RatKing king, String which)
        {
            if (!king.isAsleep())
            {
                throw new GameTestAssertException(which + " isn't asleep (" + king.getAction() + ")");
            }
            Vec3 feet = RatKingLair.nestFeet(cave.cave);
            double off = Math.hypot(king.getX() - feet.x, king.getZ() - feet.z);
            if (off > NEST_TOLERANCE || Math.abs(king.getY() - feet.y) > NEST_HEIGHT_TOLERANCE)
            {
                throw new GameTestAssertException(which + " is at " + king.position() + ", not on the nest's top carpet " + feet);
            }
            float yaw = RatKingLairKeeper.kingYaw(cave.cave);
            if (Math.abs(Mth.wrapDegrees(king.getYRot() - yaw)) > YAW_TOLERANCE)
            {
                throw new GameTestAssertException(which + " faces yaw " + king.getYRot() + ", not the arrival tunnel (" + yaw + ")");
            }
            if (!RatKingLairKeeper.isCaveKing(king))
            {
                throw new GameTestAssertException(which + " doesn't know his cave (no " + RatKingLairKeeper.HOME_TAG + " in his saved data)");
            }
        }

        void checkStillOneThenKill()
        {
            RatKing king = requireKing("the Rat King is gone a moment after he appeared");
            if (!king.getUUID().equals(first) || countKings(false) != 1)
            {
                throw new GameTestAssertException("a moment later the cave has " + countKings(false) + " Rat Kings (the first " + first
                        + ", the cave's " + king.getUUID() + "), not the same one");
            }
            checkOnNest(king, "the king a moment later");
            king.kill(); // like /kill: it counts as beaten (no trophy), like the Colossus's lair
            if (!data().beaten(cave.cave.id()) || !RatKingLair.isBeaten(server, cave.cave))
            {
                throw new GameTestAssertException("his death wasn't recorded (beaten " + data().beaten(cave.cave.id()) + ")");
            }
            deadline = Util.getMillis() + VANISH_TIMEOUT_MILLIS;
            LOGGER.info("[rat king lair test] Still one king, still asleep; killed him. Waiting for his body to vanish.");
        }

        boolean bodyGone()
        {
            return countKings(false) == 0;
        }

        void checkNoneAfterHisDeath()
        {
            if (!bodyGone())
            {
                throw new GameTestAssertException("his body didn't vanish within " + VANISH_TIMEOUT_MILLIS / 1000 + " s");
            }
            if (livingKing() != null || countKings(true) > 0)
            {
                throw new GameTestAssertException("a Rat King came back straight after the last one died");
            }
        }

        void checkNoneThenNewcomer()
        {
            if (countKings(true) > 0)
            {
                throw new GameTestAssertException("a Rat King came back a while later, for the player who stayed in the cave");
            }
            arrive(); // someone new comes in
        }

        void checkNeverBack()
        {
            if (countKings(true) > 0 || livingKing() != null)
            {
                throw new GameTestAssertException("a Rat King came back for a newcomer after the cave's king was beaten");
            }
            if (!RatKingLair.isBeaten(server, cave.cave))
            {
                throw new GameTestAssertException("the cave doesn't count as beaten any more");
            }
            LOGGER.info("[rat king lair test] Beaten, the cave stayed empty for the player who stayed and for a newcomer (no respawn).");
        }

        RatKing requireKing(String otherwise)
        {
            RatKing king = livingKing();
            if (king == null)
            {
                throw new GameTestAssertException(otherwise);
            }
            return king;
        }

        void cleanUp()
        {
            logOut(player);
            player = null;
            if (cave != null)
            {
                cave.clear();
                cave.release();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The rat caves' own Plague Ravagers are stronger
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ratking_cave_rats", timeoutTicks = BACKSTOP_TICKS)
    public static void caveRavagersAreStronger(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        TestCave[] cave = new TestCave[1];
        helper.startSequence()
                .thenExecute(() -> cave[0] = TestCave.load(server))
                .thenExecute(() -> {
                    try
                    {
                        checkCaveRats(helper, cave[0]);
                    }
                    finally
                    {
                        cave[0].release();
                    }
                })
                .thenSucceed();
    }

    private static void checkCaveRats(GameTestHelper helper, TestCave test)
    {
        ServerLevel dross = test.dross;
        BlockPos inside = test.cave.arrival();
        BlockPos above = inside.above(test.cave.bounds().getYSpan() + ABOVE_THE_CAVE);
        BlockPos overworld = helper.absolutePos(new BlockPos(1, 2, 1));
        if (!RatCaves.isInRatCave(dross, inside) || RatCaves.isInRatCave(dross, above))
        {
            throw new GameTestAssertException("the test spots are wrong: " + inside.toShortString() + " should be in the rat cave, "
                    + above.toShortString() + " not");
        }
        EntityType<Rat> ravager = ModEntities.ENRAGED_RAT.get();
        double health = RatKingTuning.ENRAGED_RAT_HEALTH;
        double damage = RatKingTuning.ENRAGED_RAT_DAMAGE;
        double strongHealth = health * RatKingTuning.RAT_CAVE_ENRAGED_HEALTH_MULTIPLIER;
        double strongDamage = damage * RatKingTuning.RAT_CAVE_ENRAGED_DAMAGE_MULTIPLIER;

        Rat strong = spawn(ravager, dross, inside, MobSpawnType.NATURAL);
        expectStats(strong, true, strongHealth, strongDamage, "a Plague Ravager spawned naturally in the cave");
        Rat reloaded = ravager.create(dross);
        if (reloaded == null)
        {
            throw new GameTestAssertException("couldn't make a Plague Ravager");
        }
        reloaded.load(strong.saveWithoutId(new CompoundTag()));
        expectStats(reloaded, true, strongHealth, strongDamage, "the cave's Plague Ravager after a save and load");

        expectStats(spawn(ravager, dross, above, MobSpawnType.NATURAL), false, health, damage,
                "a Plague Ravager spawned naturally just above the cave");
        expectStats(spawn(ravager, helper.getLevel(), overworld, MobSpawnType.NATURAL), false, health, damage,
                "a Plague Ravager spawned naturally in the Overworld");
        expectStats(spawn(ravager, dross, inside, MobSpawnType.MOB_SUMMONED), false, health, damage,
                "a summoned Plague Ravager in the cave (like the Rat King's)");
        expectStats(spawn(ravager, dross, inside, MobSpawnType.SPAWN_EGG), false, health, damage,
                "a spawn egg's Plague Ravager in the cave");
        expectStats(spawn(ModEntities.RAT.get(), dross, inside, MobSpawnType.NATURAL), false, RatKingTuning.RAT_HEALTH,
                RatKingTuning.RAT_DAMAGE, "a Plague Rat spawned naturally in the cave");
        LOGGER.info("[rat king lair test] The cave's own Plague Ravager has {} health and bites for {}; every other rat is normal.",
                strongHealth, strongDamage);
    }

    /** A rat made as Minecraft makes one for this reason (its spawn setup), at {@code feet}; not added to the world. */
    private static Rat spawn(EntityType<Rat> type, ServerLevel level, BlockPos feet, MobSpawnType reason)
    {
        Rat rat = type.create(level);
        if (rat == null)
        {
            throw new GameTestAssertException("couldn't make a " + EntityType.getKey(type));
        }
        rat.moveTo(feet.getX() + 0.5D, feet.getY(), feet.getZ() + 0.5D, 0.0F, 0.0F);
        ForgeEventFactory.onFinalizeSpawn(rat, level, level.getCurrentDifficultyAt(feet), reason, null, null);
        return rat;
    }

    private static void expectStats(Rat rat, boolean caveRavager, double health, double damage, String what)
    {
        double max = rat.getMaxHealth();
        double bite = rat.getAttributeValue(Attributes.ATTACK_DAMAGE);
        if (rat.isCaveRavager() != caveRavager || Math.abs(max - health) > EPSILON || Math.abs(rat.getHealth() - health) > EPSILON
                || Math.abs(bite - damage) > EPSILON)
        {
            throw new GameTestAssertException(String.format(java.util.Locale.ROOT,
                    "%s: %s, health %.2f of %.2f, bite %.2f (expected %s, %.2f of %.2f, bite %.2f)", what,
                    rat.isCaveRavager() ? "stronger" : "normal", rat.getHealth(), max, bite, caveRavager ? "stronger" : "normal",
                    health, health, damage));
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The rats' spawn rules
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ratking_spawn_rules", timeoutTicks = BACKSTOP_TICKS)
    public static void ratSpawnRules(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        SpawnRun run = new SpawnRun(server);
        helper.startSequence()
                .thenExecute(() -> run.guard(run::darkSpots))
                .thenWaitUntil(() -> run.guard(run::waitForLight))
                .thenExecute(() -> run.guard(run::dimSpot))
                .thenWaitUntil(() -> run.guard(run::waitForLight))
                .thenExecute(() -> run.guard(run::brightSpot))
                .thenWaitUntil(() -> run.guard(run::waitForOutsideLight))
                .thenExecute(() -> run.guard(run::dimOutside))
                .thenExecute(run::cleanUp)
                .thenSucceed();
    }

    /** The spawn-rule test's state. */
    private static final class SpawnRun
    {
        final MinecraftServer server;
        TestCave cave;
        BlockPos brick;
        BlockPos sludge;
        @Nullable
        BlockPos light;
        /** The spot outside the sewers (in the open over the cave), its floor block and its light block. */
        @Nullable
        BlockPos outside;
        @Nullable
        BlockPos outsideFloor;
        @Nullable
        BlockPos outsideLight;
        /** The block light the spot under the light block must reach next. */
        int wanted;
        long deadline;
        boolean failed;

        SpawnRun(MinecraftServer server)
        {
            this.server = server;
        }

        void guard(Runnable step)
        {
            if (failed)
            {
                return;
            }
            try
            {
                step.run();
            }
            catch (Waiting e)
            {
                throw e;
            }
            catch (RuntimeException e)
            {
                failed = true;
                LOGGER.error("[rat spawn rule test] {}", e.getMessage());
                cleanUp();
                throw e instanceof GameTestAssertException assertion ? assertion : wrap(e);
            }
        }

        void darkSpots()
        {
            for (EntityType<Rat> type : List.of(ModEntities.RAT.get(), ModEntities.ENRAGED_RAT.get()))
            {
                if (SpawnPlacements.getPlacementType(type) != SpawnPlacements.Type.ON_GROUND
                        || SpawnPlacements.getHeightmapType(type) != Heightmap.Types.MOTION_BLOCKING_NO_LEAVES)
                {
                    throw new GameTestAssertException(EntityType.getKey(type) + " spawns " + SpawnPlacements.getPlacementType(type) + " / "
                            + SpawnPlacements.getHeightmapType(type) + ", not on the ground");
                }
            }
            cave = TestCave.load(server);
            ServerLevel dross = cave.dross;
            if (dross.getDifficulty() == Difficulty.PEACEFUL)
            {
                throw new GameTestAssertException("the test world is on Peaceful, where no rat ever spawns");
            }
            brick = findDarkSpot(false);
            sludge = findDarkSpot(true);
            for (BlockPos spot : List.of(brick, sludge))
            {
                String ground = dross.getBlockState(spot.below()).getBlock().getName().getString();
                expectRule(spot, true, "in the dark on " + ground);
                expectRule(spot.above(MID_AIR), false, "in mid-air " + MID_AIR + " blocks over the floor");
            }
            light = brick.above(LIGHT_ABOVE);
            setLight(light, DIM_LIGHT_LEVEL);
        }

        /** A light block of this level at {@code at}; the spot below it should then get that level less LIGHT_ABOVE. */
        void setLight(BlockPos at, int level)
        {
            cave.dross.setBlock(at, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, level), 3);
            wanted = level - LIGHT_ABOVE;
            deadline = Util.getMillis() + LIGHT_TIMEOUT_MILLIS;
        }

        void waitForLight()
        {
            if (cave.dross.getBrightness(LightLayer.BLOCK, brick) != wanted && Util.getMillis() < deadline)
            {
                throw new Waiting("waiting for the light block's light");
            }
        }

        /** Block light 7 in the sewers (the rat cave): rats may spawn. Then 8. */
        void dimSpot()
        {
            int lit = requireLight(brick);
            expectRule(brick, true, "in the sewers' dim light (block light " + lit + ")");
            setLight(light, BRIGHT_LIGHT_LEVEL);
        }

        /** Block light 8 in the sewers: too bright. Then the spot outside the sewers, at 7. */
        void brightSpot()
        {
            int lit = requireLight(brick);
            expectRule(brick, false, "in the sewers in brighter light (block light " + lit + ")");
            ServerLevel dross = cave.dross;
            int x = brick.getX();
            int z = brick.getZ();
            int ground = dross.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            outsideFloor = new BlockPos(x, ground + OUTSIDE_ABOVE_GROUND, z);
            outside = outsideFloor.above();
            outsideLight = outside.above(LIGHT_ABOVE);
            for (int k = 1; k <= LIGHT_ABOVE + 1; k++)
            {
                if (!dross.getBlockState(outsideFloor.above(k)).isAir())
                {
                    throw new GameTestAssertException("the open spot over the cave at " + outside.toShortString() + " isn't open");
                }
            }
            dross.setBlock(outsideFloor, Blocks.STONE.defaultBlockState(), 3);
            dross.setBlock(outsideLight, Blocks.LIGHT.defaultBlockState().setValue(LightBlock.LEVEL, DIM_LIGHT_LEVEL), 3);
            wanted = DIM_LIGHT_LEVEL - LIGHT_ABOVE;
            deadline = Util.getMillis() + LIGHT_TIMEOUT_MILLIS;
        }

        void waitForOutsideLight()
        {
            if (cave.dross.getBrightness(LightLayer.BLOCK, outside) != wanted && Util.getMillis() < deadline)
            {
                throw new Waiting("waiting for the light block's light outside");
            }
        }

        /** Block light 7 outside the sewers (in the open over the cave): no rats (the Dross's darkness-only rule). */
        void dimOutside()
        {
            int lit = requireLight(outside);
            if (RatKingEvents.isSewerSpot(cave.dross, outside))
            {
                throw new GameTestAssertException("the open spot " + outside.toShortString() + " over the cave counts as the sewers");
            }
            expectRule(outside, false, "outside the sewers in dim light (block light " + lit + ")");
            LOGGER.info("[rat spawn rule test] Rats may spawn in the dark on the cave's bricks ({}) and Sewer Sludge ({}) and in the sewers' dim "
                    + "light (7), not in brighter light (8), mid-air, or in dim light outside the sewers ({}).", brick.toShortString(),
                    sludge.toShortString(), outside.toShortString());
        }

        int requireLight(BlockPos at)
        {
            int lit = cave.dross.getBrightness(LightLayer.BLOCK, at);
            if (lit != wanted)
            {
                throw new GameTestAssertException("the light block over " + at.toShortString() + " gave block light " + lit + ", not " + wanted
                        + ", within " + LIGHT_TIMEOUT_MILLIS / 1000 + " s");
            }
            return lit;
        }

        /** A floor spot in the cave (away from the nest and hoard) in full darkness where a rat fits, on sludge or not. */
        BlockPos findDarkSpot(boolean onSludge)
        {
            ServerLevel dross = cave.dross;
            BlockPos centre = cave.cave.centre();
            EntityType<Rat> type = ModEntities.ENRAGED_RAT.get(); // the bigger of the two
            for (int r = SPOT_MIN_DISTANCE; r <= SPOT_MAX_DISTANCE; r++)
            {
                for (int dx = -r; dx <= r; dx++)
                {
                    for (int dz = -r; dz <= r; dz++)
                    {
                        if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                        {
                            continue;
                        }
                        BlockPos feet = new BlockPos(centre.getX() + dx, cave.cave.floorY() + 1, centre.getZ() + dz);
                        boolean sludge = dross.getBlockState(feet.below()).is(ModBlocks.SEWER_SLUDGE.get());
                        if (sludge == onSludge && cave.cave.contains(feet) && dross.getBrightness(LightLayer.BLOCK, feet) == 0
                                && dross.getBrightness(LightLayer.SKY, feet) == 0 && openAbove(feet)
                                && NaturalSpawner.isSpawnPositionOk(SpawnPlacements.Type.ON_GROUND, dross, feet, type))
                        {
                            return feet;
                        }
                    }
                }
            }
            throw new GameTestAssertException("found no dark floor spot " + (onSludge ? "on Sewer Sludge " : "") + "in the rat cave at "
                    + centre.toShortString());
        }

        /** Air over the spot up to the mid-air spot's head (no stalactite in the way of the light block or the mid-air check). */
        boolean openAbove(BlockPos feet)
        {
            for (int k = 1; k <= MID_AIR + 1; k++)
            {
                if (!cave.dross.getBlockState(feet.above(k)).isAir())
                {
                    return false;
                }
            }
            return true;
        }

        /** Both rats' spawn rules at this spot, as for a natural spawn (asked a few times: they use randomness). */
        void expectRule(BlockPos pos, boolean expected, String where)
        {
            ServerLevel dross = cave.dross;
            for (EntityType<Rat> type : List.of(ModEntities.RAT.get(), ModEntities.ENRAGED_RAT.get()))
            {
                for (int i = 0; i < RULE_TRIES; i++)
                {
                    boolean ok = NaturalSpawner.isSpawnPositionOk(SpawnPlacements.getPlacementType(type), dross, pos, type)
                            && SpawnPlacements.checkSpawnRules(type, dross, MobSpawnType.NATURAL, pos, dross.random);
                    if (ok != expected)
                    {
                        throw new GameTestAssertException(EntityType.getKey(type) + " " + (expected ? "can't" : "can") + " spawn " + where
                                + " at " + pos.toShortString());
                    }
                }
            }
        }

        void cleanUp()
        {
            if (cave != null)
            {
                if (light != null && cave.dross.getBlockState(light).is(Blocks.LIGHT))
                {
                    cave.dross.setBlock(light, Blocks.AIR.defaultBlockState(), 3);
                }
                if (outsideLight != null && cave.dross.getBlockState(outsideLight).is(Blocks.LIGHT))
                {
                    cave.dross.setBlock(outsideLight, Blocks.AIR.defaultBlockState(), 3);
                }
                if (outsideFloor != null && cave.dross.getBlockState(outsideFloor).is(Blocks.STONE))
                {
                    cave.dross.setBlock(outsideFloor, Blocks.AIR.defaultBlockState(), 3);
                }
                cave.release();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // His snores at the grand entrance
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_ratking_snores", timeoutTicks = BACKSTOP_TICKS)
    public static void snoresAtTheGrandEntrance(GameTestHelper helper)
    {
        SnoreRun run = new SnoreRun(helper.getLevel().getServer());
        helper.startSequence()
                .thenExecute(() -> run.guard(run::load))
                .thenWaitUntil(() -> run.guard(() -> run.cave.waitForEntities()))
                .thenExecute(() -> run.guard(run::listen))
                .thenWaitUntil(() -> run.guard(run::waitForSnore))
                .thenExecute(() -> run.guard(run::checkHeardThenBeat))
                .thenIdle(QUIET_SNORE_TICKS)
                .thenExecute(() -> run.guard(run::checkQuiet))
                .thenExecute(run::cleanUp)
                .thenSucceed();
    }

    /** The snore test's state: the cave, a stand-in at its grand entrance and one further off. */
    private static final class SnoreRun
    {
        final MinecraftServer server;
        TestCave cave;
        BlockPos entrance;
        @Nullable
        ServerPlayer near;
        @Nullable
        ServerPlayer far;
        int heardWhenBeaten;
        long deadline;
        boolean failed;

        SnoreRun(MinecraftServer server)
        {
            this.server = server;
        }

        void guard(Runnable step)
        {
            if (failed)
            {
                return;
            }
            try
            {
                step.run();
            }
            catch (Waiting e)
            {
                throw e;
            }
            catch (RuntimeException e)
            {
                failed = true;
                LOGGER.error("[rat king snore test] {}", e.getMessage());
                cleanUp();
                throw e instanceof GameTestAssertException assertion ? assertion : wrap(e);
            }
        }

        void load()
        {
            cave = TestCave.load(server);
            if (cave.cave.entrance() == null)
            {
                throw new GameTestAssertException("the rat cave at " + cave.cave.centre().toShortString() + " has no grand entrance");
            }
            entrance = cave.cave.entrance();
        }

        /** No king yet and not beaten; a stand-in at the grand entrance, another {@value #FAR_LISTENER} blocks off. */
        void listen()
        {
            cave.clear();
            near = joinPlayer(cave.dross, "snore-near", new Vec3(entrance.getX() + 0.5D, entrance.getY(), entrance.getZ() + 0.5D));
            far = joinPlayer(cave.dross, "snore-far", new Vec3(entrance.getX() + FAR_LISTENER + 0.5D, entrance.getY(), entrance.getZ() + 0.5D));
            deadline = Util.getMillis() + SNORE_TIMEOUT_MILLIS;
        }

        void waitForSnore()
        {
            if (RatKingSnores.snoresHeardBy(near) == 0 && Util.getMillis() < deadline)
            {
                throw new Waiting("waiting for a snore at the grand entrance");
            }
        }

        void checkHeardThenBeat()
        {
            int heard = RatKingSnores.snoresHeardBy(near);
            if (heard == 0)
            {
                throw new GameTestAssertException("no snore came up the grand entrance at " + entrance.toShortString() + " within "
                        + SNORE_TIMEOUT_MILLIS / 1000 + " s (his king asleep or not yet there, not beaten)");
            }
            if (RatKingSnores.snoresHeardBy(far) > 0)
            {
                throw new GameTestAssertException("a player " + FAR_LISTENER + " blocks from the grand entrance heard the snores");
            }
            // He's beaten: the pipe goes quiet.
            RatKingLairData.get(server).recordDefeat(cave.cave.id());
            heardWhenBeaten = RatKingSnores.snoresHeardBy(near);
            LOGGER.info("[rat king snore test] {} snore(s) came up the grand entrance at {}; nobody {} blocks off heard any. He's beaten now.",
                    heard, entrance.toShortString(), FAR_LISTENER);
        }

        void checkQuiet()
        {
            int more = RatKingSnores.snoresHeardBy(near) - heardWhenBeaten;
            if (more > 0)
            {
                throw new GameTestAssertException(more + " snore(s) came up the grand entrance after the Rat King was beaten");
            }
            LOGGER.info("[rat king snore test] Once he was beaten, the pipe went quiet.");
        }

        void cleanUp()
        {
            logOut(near);
            logOut(far);
            near = null;
            far = null;
            if (cave != null)
            {
                cave.clear();
                cave.release();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** "Still waiting" in a waiting step: never a failure by itself. */
    private static final class Waiting extends GameTestAssertException
    {
        Waiting(String message)
        {
            super(message);
        }
    }

    private static GameTestAssertException wrap(RuntimeException e)
    {
        GameTestAssertException failure = new GameTestAssertException("the test threw " + e);
        failure.initCause(e);
        return failure;
    }

    /** The test's rat cave, its chunks generated and kept loaded (forced) until {@link #release}. */
    private static final class TestCave
    {
        final ServerLevel dross;
        final RatCaves.RatCave cave;
        final List<ChunkPos> forced = new ArrayList<>();
        long deadline;

        private TestCave(ServerLevel dross, RatCaves.RatCave cave)
        {
            this.dross = dross;
            this.cave = cave;
        }

        /** Finds the cave (once per test run), generates its chunks and keeps them loaded. */
        static TestCave load(MinecraftServer server)
        {
            ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
            if (dross == null)
            {
                throw new GameTestAssertException("there's no Dross level");
            }
            if (testCave == null)
            {
                long started = Util.getMillis();
                BlockPos blightwood = DrossBiomes.findNearest(dross, DrossHub.CENTER.atY(SAMPLE_Y), DrossBiomes.BLIGHTWOOD)
                        .orElseThrow(() -> new GameTestAssertException("no Blightwood in this world"));
                testCave = RatCaves.findNearest(dross, blightwood)
                        .orElseThrow(() -> new GameTestAssertException("no rat cave near the Blightwood at " + blightwood.toShortString()));
                LOGGER.info("[rat king lair test] Using the rat cave at {} (nest {}, arrival {}), found in {} ms.", testCave.centre().toShortString(),
                        testCave.nest().toShortString(), testCave.arrival().toShortString(), Util.getMillis() - started);
            }
            TestCave test = new TestCave(dross, testCave);
            long started = Util.getMillis();
            RatKingLair.prepare(dross, testCave);
            for (ChunkPos chunk : RatKingLairKeeper.chunks(testCave))
            {
                if (!dross.getForcedChunks().contains(chunk.toLong()))
                {
                    dross.setChunkForced(chunk.x, chunk.z, true);
                    test.forced.add(chunk);
                }
            }
            LOGGER.info("[rat king lair test] Generated and loaded the cave's {} chunks in {} ms.", RatKingLairKeeper.chunks(testCave).size(),
                    Util.getMillis() - started);
            test.deadline = Util.getMillis() + LOAD_TIMEOUT_MILLIS;
            return test;
        }

        /** Throws (keeps waiting) until the cave's entities are loaded. */
        void waitForEntities()
        {
            if (!RatKingLairKeeper.entitiesLoaded(dross, cave))
            {
                if (Util.getMillis() < deadline)
                {
                    throw new Waiting("waiting for the cave's entities to load");
                }
                throw new GameTestAssertException("the cave's entities didn't load within " + LOAD_TIMEOUT_MILLIS / 1000 + " s");
            }
        }

        /** No Rat King in the cave, its king forgotten (as if nobody had been near). */
        void clear()
        {
            RatKingLair.clearForTests(dross, cave);
        }

        /** Lets go of the chunks this test forced. */
        void release()
        {
            for (ChunkPos chunk : forced)
            {
                dross.setChunkForced(chunk.x, chunk.z, false);
            }
            forced.clear();
        }
    }

    /**
     * A stand-in player on the server's player list, in creative mode (rats leave him alone; the Rat King still wakes for
     * him, but he stays outside the wake range), at {@code feet} in the Dross. He logs in at the Overworld's spawn (so no
     * far-off Overworld land is generated for him) and is teleported in at once. He gets an in-memory network channel
     * like the other areas' stand-ins; he isn't ticked.
     */
    private static ServerPlayer joinPlayer(ServerLevel dross, String name, Vec3 feet)
    {
        MinecraftServer server = dross.getServer();
        BlockPos spawn = server.overworld().getSharedSpawnPos();
        ServerPlayer player = new ServerPlayer(server, server.overworld(), new GameProfile(UUID.randomUUID(), name));
        player.moveTo(spawn.getX() + 0.5D, spawn.getY(), spawn.getZ() + 0.5D, 0.0F, 0.0F);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
        server.getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.CREATIVE);
        player.teleportTo(dross, feet.x, feet.y, feet.z, 0.0F, 0.0F);
        if (player.serverLevel() != dross || player.position().distanceToSqr(feet) > 0.01D)
        {
            throw new GameTestAssertException("the stand-in " + name + " couldn't be put at " + feet + " in the Dross");
        }
        return player;
    }

    private static void logOut(@Nullable ServerPlayer player)
    {
        if (player != null && player.server.getPlayerList().getPlayer(player.getUUID()) == player)
        {
            player.server.getPlayerList().remove(player);
        }
    }
}
