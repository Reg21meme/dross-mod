package com.reg21meme.dross.boss.ratking.lair;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.BossLockGameTest;
import com.reg21meme.dross.boss.BossWards;
import com.reg21meme.dross.dimension.sewer.RatCaves;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless check of the Rat King's cave ward ({@link RatCaveWard}, {@code boss.BossWards}) in a real rat cave (the lair
 * tests' own, {@link RatKingLairGameTest.TestCave}): three stand-ins put deep in the cave, about
 * {@value #SPOT_FROM_MIDDLE} blocks from its middle; a few of the wards' looks later the survival one whose Oracle quest
 * hasn't reached the Rat King is out of the cave, in the tunnel behind its arrival spot, while the one whose quest has
 * ("Down the Drain", its offer heard) and the creative one are still where they were.
 */
@GameTestHolder(BossLockGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class RatCaveLockGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** The stand-ins stand this far from the cave's middle (well past a push from its edge, out of the sleeping king's wake range). */
    private static final int SPOT_FROM_MIDDLE = 14;
    /** Ticks for the wards to have looked a few times. */
    private static final int LOOKS = BossWards.CHECK_EVERY_TICKS * 4;
    /** The bounced one must end up within this many blocks of the arrival spot (the tunnel behind it). */
    private static final double NEAR_ARRIVAL = RatCaveWard.EXIT_SEARCH + 3.0D;

    private RatCaveLockGameTest() {}

    /** A spot in the cave's air {@code SPOT_FROM_MIDDLE} blocks from its middle, two blocks of air tall. */
    private static Vec3 deepSpot(RatKingLairGameTest.TestCave test)
    {
        RatCaves.RatCave cave = test.cave;
        BlockPos centre = cave.centre();
        for (int i = 0; i < 16; i++)
        {
            double a = 2.0D * Math.PI * i / 16;
            for (int up = 1; up <= 4; up++)
            {
                BlockPos feet = new BlockPos((int) Math.floor(centre.getX() + 0.5D + Math.cos(a) * SPOT_FROM_MIDDLE), cave.floorY() + up,
                        (int) Math.floor(centre.getZ() + 0.5D + Math.sin(a) * SPOT_FROM_MIDDLE));
                if (cave.contains(feet) && test.dross.getBlockState(feet).isAir() && test.dross.getBlockState(feet.above()).isAir())
                {
                    return Vec3.atBottomCenterOf(feet);
                }
            }
        }
        throw new GameTestAssertException("no open spot " + SPOT_FROM_MIDDLE + " blocks from the rat cave's middle at " + centre.toShortString());
    }

    @GameTest(template = BossLockGameTest.EMPTY, batch = "dross_locks_rat_cave", timeoutTicks = 2_000_000)
    public static void caveIsWarded(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        RatKingLairGameTest.TestCave[] test = new RatKingLairGameTest.TestCave[1];
        List<ServerPlayer> players = new ArrayList<>();
        Vec3[] spot = new Vec3[1];
        Runnable cleanUp = () -> {
            for (ServerPlayer p : players)
            {
                RatKingLairGameTest.logOut(p);
            }
            if (test[0] != null)
            {
                test[0].clear();
                test[0].release();
            }
        };
        helper.startSequence()
                .thenExecute(() -> test[0] = RatKingLairGameTest.TestCave.load(server))
                .thenWaitUntil(() -> test[0].waitForEntities())
                .thenExecute(() -> {
                    try
                    {
                        test[0].clear();
                        spot[0] = deepSpot(test[0]);
                        ServerPlayer locked = RatKingLairGameTest.joinPlayer(test[0].dross, "rat-ward-locked", spot[0]);
                        locked.setGameMode(GameType.SURVIVAL);
                        players.add(locked);
                        ServerPlayer allowed = RatKingLairGameTest.joinPlayer(test[0].dross, "rat-ward-allowed", spot[0]);
                        allowed.setGameMode(GameType.SURVIVAL);
                        BossLockGameTest.allow(allowed, OracleBoss.RAT_KING);
                        players.add(allowed);
                        ServerPlayer creative = RatKingLairGameTest.joinPlayer(test[0].dross, "rat-ward-creative", spot[0]);
                        players.add(creative); // (creative already)
                        if (RatCaveWard.of(test[0].dross, test[0].cave).depth(spot[0]) <= BossWards.PUSH_DEPTH)
                        {
                            throw new GameTestAssertException("the test's spot " + spot[0] + " should be deep in the cave's ward");
                        }
                    }
                    catch (RuntimeException e)
                    {
                        cleanUp.run();
                        throw e;
                    }
                })
                .thenIdle(LOOKS)
                .thenExecute(() -> {
                    try
                    {
                        ServerPlayer locked = players.get(0);
                        BlockPos now = locked.blockPosition();
                        if (test[0].cave.contains(now) || RatCaves.isInRatCave(test[0].dross, now))
                        {
                            throw new GameTestAssertException("the locked player is still in the rat cave, at " + now.toShortString());
                        }
                        double fromArrival = locked.position().distanceTo(Vec3.atBottomCenterOf(test[0].cave.arrival()));
                        if (fromArrival > NEAR_ARRIVAL)
                        {
                            throw new GameTestAssertException("the locked player was sent " + Math.round(fromArrival)
                                    + " blocks from the cave's arrival spot (to " + now.toShortString() + "), not into the tunnel behind it");
                        }
                        for (ServerPlayer stays : List.of(players.get(1), players.get(2)))
                        {
                            if (stays.position().distanceTo(spot[0]) > 0.01D)
                            {
                                throw new GameTestAssertException(stays.getGameProfile().getName() + " was moved from " + spot[0]
                                        + " to " + stays.position());
                            }
                        }
                        LOGGER.info("[rat cave lock test] the locked player was bounced to {} ({} blocks from the arrival spot); the others stayed",
                                now.toShortString(), Math.round(fromArrival));
                    }
                    finally
                    {
                        cleanUp.run();
                    }
                })
                .thenSucceed();
    }
}
