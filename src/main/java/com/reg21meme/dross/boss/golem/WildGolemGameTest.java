package com.reg21meme.dross.boss.golem;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnPlacements;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests (GameTests) for the wild stone and lava golems of the Cinder Wastes, on the Colossus tests' arena
 * ({@link ColossusGameTest}: a smooth stone floor under a barrier roof). Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}. Each has its own batch, so no other test's stand-in players
 * or golems are about while it runs.
 * <ul>
 *   <li>{@link #stoneGolemSpawnsInLightNotInWaterOrAir}: by the natural-spawning rules, a stone golem may spawn on
 *       basalt in full light (where a zombie may not), but not in water, in mid-air or on Peaceful.</li>
 *   <li>{@link #lavaGolemOnlySpawnsNearLava}: a lava golem may not spawn with no lava near, nor with lava 7 blocks
 *       away; it may 3 blocks from lava (a stone golem may anywhere).</li>
 *   <li>{@link #wildLavaGolemWaitsForAPlayer}: a wild lava golem with no player near doesn't blow up in 10 seconds
 *       (a summoned one would after 7), and never crumbles.</li>
 *   <li>{@link #wildLavaGolemBlowsUpNextToAPlayer}: with a player 2 blocks away its fuse is lit, and it blows up
 *       without breaking a block.</li>
 *   <li>{@link #wildGolemsDespawnSummonedOnesStay}: wild golems (natural or from a spawn egg) despawn when the only
 *       player is far away; a Colossus's summoned golem doesn't.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class WildGolemGameTest
{
    private static final BlockPos MIDDLE = ColossusGameTest.MIDDLE;
    /** Block light at least this strong counts as bright (a zombie needs 0 in the Overworld). */
    private static final int BRIGHT = 12;
    /** The longest the light test waits for the lantern's light to spread (ticks). */
    private static final int LIGHT_WAIT_TICKS = 60;
    /** Lava this many blocks from the spot is near enough for a lava golem. */
    private static final int NEAR_LAVA = 3;
    /** How long a wild lava golem is left alone (ticks: 10 seconds, longer than a summoned one's 7-second fuse). */
    private static final int LONELY_TICKS = 200;
    /** How long the blast test waits: the fuse is lit at once, and it blows up 1.5 seconds later (with room to spare). */
    private static final int BLAST_WAIT_TICKS = ColossusTuning.LAVA_GOLEM_SWELL_TICKS + 30;
    /** The far player stands this many blocks above the arena: further than a monster's 128-block despawn distance. */
    private static final int FAR_ABOVE = 140;
    /** Ticks the despawn test waits (despawning is checked every tick). */
    private static final int DESPAWN_WAIT_TICKS = 5;
    /** A distance far beyond the despawn distance, to ask {@code removeWhenFarAway} with. */
    private static final double FAR_AWAY = 200.0D;
    private static final float YAW_WEST = 90.0F;

    private WildGolemGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Spawn rules
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_wild_golem_spawn",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + LIGHT_WAIT_TICKS)
    public static void stoneGolemSpawnsInLightNotInWaterOrAir(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, WildGolemGameTest::stoneGolemSpawnsInLightNotInWaterOrAir))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        EntityType<StoneGolem> stone = ModEntities.STONE_GOLEM.get();
        BlockPos lit = MIDDLE;
        BlockPos lantern = lit.east();
        BlockPos wet = MIDDLE.offset(-4, 0, -4);
        BlockPos floating = MIDDLE.above(3);
        helper.setBlock(lit.below(), Blocks.BASALT);
        helper.setBlock(lantern, Blocks.SEA_LANTERN);
        helper.setBlock(wet.below(), Blocks.BASALT);
        basin(helper, wet, Blocks.WATER);
        helper.startSequence()
                .thenWaitUntil(() -> helper.assertTrue(level.getBrightness(LightLayer.BLOCK, helper.absolutePos(lit)) >= BRIGHT,
                        "the sea lantern's light hasn't reached the spot yet"))
                .thenExecute(() -> {
                    BlockPos at = helper.absolutePos(lit);
                    try
                    {
                        if (Monster.checkMonsterSpawnRules(EntityType.ZOMBIE, level, MobSpawnType.NATURAL, at, level.random))
                        {
                            helper.fail("the spot should be too bright for a zombie (the test needs full light)");
                        }
                        if (!wouldSpawnNaturally(level, stone, at))
                        {
                            helper.fail("a stone golem should spawn on basalt in full light");
                        }
                        if (wouldSpawnNaturally(level, stone, helper.absolutePos(wet)))
                        {
                            helper.fail("a stone golem shouldn't spawn in water");
                        }
                        if (wouldSpawnNaturally(level, stone, helper.absolutePos(floating)))
                        {
                            helper.fail("a stone golem shouldn't spawn in mid-air (nothing under it)");
                        }
                        if (spawnsOnPeaceful(level, stone, at))
                        {
                            helper.fail("a stone golem shouldn't spawn on Peaceful");
                        }
                    }
                    finally
                    {
                        clear(helper, wet, lantern);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_wild_golem_lava",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 20)
    public static void lavaGolemOnlySpawnsNearLava(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, WildGolemGameTest::lavaGolemOnlySpawnsNearLava))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        EntityType<LavaGolem> lava = ModEntities.LAVA_GOLEM.get();
        EntityType<StoneGolem> stone = ModEntities.STONE_GOLEM.get();
        BlockPos spot = MIDDLE;
        BlockPos farLava = MIDDLE.offset(ColossusTuning.WILD_LAVA_SEARCH_SIDEWAYS + 1, 0, 0);
        BlockPos nearLava = MIDDLE.offset(NEAR_LAVA, 0, 0);
        helper.setBlock(spot.below(), Blocks.BASALT);
        BlockPos at = helper.absolutePos(spot);
        helper.startSequence()
                .thenExecute(() -> {
                    try
                    {
                        if (wouldSpawnNaturally(level, lava, at))
                        {
                            helper.fail("a lava golem shouldn't spawn with no lava near");
                        }
                        if (!wouldSpawnNaturally(level, stone, at))
                        {
                            helper.fail("a stone golem should spawn there (it doesn't need lava)");
                        }
                        basin(helper, farLava, Blocks.LAVA);
                        if (wouldSpawnNaturally(level, lava, at))
                        {
                            helper.fail("lava " + (ColossusTuning.WILD_LAVA_SEARCH_SIDEWAYS + 1)
                                    + " blocks away is too far for a lava golem");
                        }
                        basin(helper, nearLava, Blocks.LAVA);
                        if (!wouldSpawnNaturally(level, lava, at))
                        {
                            helper.fail("a lava golem should spawn " + NEAR_LAVA + " blocks from lava");
                        }
                        if (!wouldSpawnNaturally(level, stone, at))
                        {
                            helper.fail("a stone golem should spawn near lava too");
                        }
                    }
                    finally
                    {
                        clear(helper, farLava, nearLava);
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // A wild lava golem's fuse
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_wild_golem_waits",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + LONELY_TICKS + 20)
    public static void wildLavaGolemWaitsForAPlayer(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, WildGolemGameTest::wildLavaGolemWaitsForAPlayer))
        {
            return;
        }
        LavaGolem golem = ModEntities.LAVA_GOLEM.get().spawn(helper.getLevel(), helper.absolutePos(MIDDLE), MobSpawnType.NATURAL);
        if (golem == null)
        {
            helper.fail("the lava golem couldn't be spawned");
            return;
        }
        helper.startSequence()
                .thenExecuteAfter(LONELY_TICKS, () -> {
                    boolean gone = golem.isRemoved();
                    boolean lit = golem.isFuseLit();
                    int fuse = golem.fuse();
                    int ticked = golem.tickCount;
                    golem.discard();
                    if (gone)
                    {
                        helper.fail("a wild lava golem with no player near shouldn't blow up, crumble or vanish, but it's gone"
                                + " (" + golem.getRemovalReason() + ")");
                    }
                    if (ticked < LONELY_TICKS / 2)
                    {
                        helper.fail("the lava golem only ticked " + ticked + " times, so the test proves nothing");
                    }
                    if (lit || fuse != ColossusTuning.LAVA_GOLEM_FUSE_TICKS)
                    {
                        helper.fail("its fuse should wait for a player, but it's " + (lit ? "lit" : "not lit") + " at " + fuse);
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_wild_golem_blast",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + BLAST_WAIT_TICKS + 20)
    public static void wildLavaGolemBlowsUpNextToAPlayer(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, WildGolemGameTest::wildLavaGolemBlowsUpNextToAPlayer))
        {
            return;
        }
        LavaGolem golem = ModEntities.LAVA_GOLEM.get().spawn(helper.getLevel(), helper.absolutePos(MIDDLE), MobSpawnType.NATURAL);
        if (golem == null)
        {
            helper.fail("the lava golem couldn't be spawned");
            return;
        }
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, "wild-golem-test-player", MIDDLE.offset(2, 0, 0), YAW_WEST);
        player.setGameMode(GameType.SURVIVAL); // (the test server's players start in creative, which monsters ignore)
        golem.setTarget(player);
        List<ServerPlayer> players = List.of(player);
        helper.startSequence()
                .thenExecuteAfter(BLAST_WAIT_TICKS, () -> {
                    ColossusGameTest.leaveAll(players);
                    boolean gone = golem.isRemoved();
                    Entity.RemovalReason reason = golem.getRemovalReason();
                    golem.discard();
                    if (!gone)
                    {
                        helper.fail("with a player 2 blocks away it should have blown up within "
                                + BLAST_WAIT_TICKS + " ticks (its fuse is " + (golem.isFuseLit() ? "lit" : "not lit") + ")");
                    }
                    if (reason != Entity.RemovalReason.DISCARDED)
                    {
                        helper.fail("it should have blown up, but it was removed as " + reason);
                    }
                    String broken = brokenBlock(helper);
                    if (broken != null)
                    {
                        helper.fail("its blast shouldn't change any block, but " + broken);
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Despawning
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_wild_golem_despawn",
            timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + DESPAWN_WAIT_TICKS + 20)
    public static void wildGolemsDespawnSummonedOnesStay(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, WildGolemGameTest::wildGolemsDespawnSummonedOnesStay))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<Entity> made = new ArrayList<>();
        CinderColossus colossus = CinderColossus.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        made.add(colossus);
        StoneGolem summoned = colossus == null ? null : GolemMinion.summon(ModEntities.STONE_GOLEM.get(), level, colossus,
                helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(5, 0, 0))), 0.0F);
        made.add(summoned);
        StoneGolem wild = ModEntities.STONE_GOLEM.get().spawn(level, helper.absolutePos(MIDDLE.offset(-5, 0, -5)), MobSpawnType.NATURAL);
        made.add(wild);
        LavaGolem egg = ModEntities.LAVA_GOLEM.get().spawn(level, helper.absolutePos(MIDDLE.offset(-5, 0, 5)), MobSpawnType.SPAWN_EGG);
        made.add(egg);
        if (made.contains(null))
        {
            discardAll(made);
            helper.fail("a Colossus or a golem couldn't be spawned");
            return;
        }
        String problem = null;
        if (!wild.isWild() || wild.isPersistenceRequired() || !wild.removeWhenFarAway(FAR_AWAY))
        {
            problem = "a naturally spawned stone golem should be wild and allowed to despawn";
        }
        else if (!egg.isWild() || egg.isPersistenceRequired() || !egg.removeWhenFarAway(FAR_AWAY) || egg.isFuseLit())
        {
            problem = "a lava golem from a spawn egg should be wild, allowed to despawn, with its fuse waiting for a player";
        }
        else if (summoned.isWild() || !summoned.isPersistenceRequired() || summoned.removeWhenFarAway(FAR_AWAY)
                || !summoned.belongsTo(colossus))
        {
            problem = "a summoned stone golem should belong to its Colossus and never despawn";
        }
        if (problem != null)
        {
            discardAll(made);
            helper.fail(problem);
            return;
        }
        // The only player, far above: wild golems despawn (Minecraft checks every tick), summoned ones stay.
        ServerPlayer far = ColossusGameTest.joinPlayer(helper, "wild-golem-far-player", MIDDLE.above(FAR_ABOVE), 0.0F);
        helper.startSequence()
                .thenExecuteAfter(DESPAWN_WAIT_TICKS, () -> {
                    ColossusGameTest.leaveAll(List.of(far));
                    boolean wildGone = wild.isRemoved();
                    boolean eggGone = egg.isRemoved();
                    boolean summonedGone = summoned.isRemoved();
                    discardAll(made);
                    if (!wildGone || !eggGone)
                    {
                        helper.fail("with the only player " + FAR_ABOVE + " blocks away, wild golems should despawn (natural: "
                                + (wildGone ? "gone" : "still there") + ", spawn egg: " + (eggGone ? "gone" : "still there") + ")");
                    }
                    if (summonedGone)
                    {
                        helper.fail("a Colossus's summoned golem should never despawn");
                    }
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * True if Minecraft's natural spawning would put a golem of this type at {@code pos} (its feet), with the same
     * checks in the same order: the placement (on the ground, room, no fluid), the type's spawn rules (Peaceful, lava),
     * room for its body, then the mob's own check (light, for a monster) and nothing in the way.
     */
    private static boolean wouldSpawnNaturally(ServerLevel level, EntityType<? extends GolemMinion> type, BlockPos pos)
    {
        if (!NaturalSpawner.isSpawnPositionOk(SpawnPlacements.getPlacementType(type), level, pos, type)
                || !SpawnPlacements.checkSpawnRules(type, level, MobSpawnType.NATURAL, pos, level.random)
                || !level.noCollision(type.getAABB(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D)))
        {
            return false;
        }
        GolemMinion mob = type.create(level); // never added to the world
        if (mob == null)
        {
            return false;
        }
        mob.moveTo(pos.getX() + 0.5D, pos.getY(), pos.getZ() + 0.5D, 0.0F, 0.0F);
        return ForgeEventFactory.checkSpawnPosition(mob, level, MobSpawnType.NATURAL);
    }

    /** True if it would spawn there on Peaceful (the difficulty is put back at once, within the same tick). */
    private static boolean spawnsOnPeaceful(ServerLevel level, EntityType<? extends GolemMinion> type, BlockPos pos)
    {
        MinecraftServer server = level.getServer();
        Difficulty before = server.getWorldData().getDifficulty();
        server.setDifficulty(Difficulty.PEACEFUL, true);
        try
        {
            return wouldSpawnNaturally(level, type, pos);
        }
        finally
        {
            server.setDifficulty(before, true);
        }
    }

    /** A one-block pool of {@code fluid} on the floor, walled in by stone on all four sides (so it never flows). */
    private static void basin(GameTestHelper helper, BlockPos at, Block fluid)
    {
        for (Direction side : Direction.Plane.HORIZONTAL)
        {
            helper.setBlock(at.relative(side), Blocks.STONE);
        }
        helper.setBlock(at, fluid);
    }

    /** Removes the test's pools (fluid first, then their walls) and other blocks it placed on the floor. */
    private static void clear(GameTestHelper helper, BlockPos... spots)
    {
        for (BlockPos spot : spots)
        {
            helper.setBlock(spot, Blocks.AIR);
        }
        for (BlockPos spot : spots)
        {
            for (Direction side : Direction.Plane.HORIZONTAL)
            {
                if (helper.getBlockState(spot.relative(side)).is(Blocks.STONE))
                {
                    helper.setBlock(spot.relative(side), Blocks.AIR);
                }
            }
        }
    }

    /** What changed in the arena (its floor should be smooth stone, with air above), or null if nothing did. */
    @Nullable
    private static String brokenBlock(GameTestHelper helper)
    {
        for (int x = 0; x < ColossusGameTest.ARENA_SIZE; x++)
        {
            for (int z = 0; z < ColossusGameTest.ARENA_SIZE; z++)
            {
                BlockPos floor = new BlockPos(x, ColossusGameTest.FLOOR_Y, z);
                if (!helper.getBlockState(floor).is(Blocks.SMOOTH_STONE))
                {
                    return "the floor at " + floor.toShortString() + " is now " + helper.getBlockState(floor);
                }
                for (int y = ColossusGameTest.FLOOR_Y + 1; y <= ColossusGameTest.ARENA_HEIGHT; y++)
                {
                    BlockState state = helper.getBlockState(new BlockPos(x, y, z));
                    if (!state.isAir())
                    {
                        return "there's " + state + " at " + new BlockPos(x, y, z).toShortString();
                    }
                }
            }
        }
        return null;
    }

    private static void discardAll(List<Entity> entities)
    {
        for (Entity entity : entities)
        {
            if (entity != null)
            {
                entity.discard();
            }
        }
    }
}
