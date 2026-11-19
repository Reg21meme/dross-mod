package com.reg21meme.dross.boss.lair;

import com.reg21meme.dross.boss.BossLockGameTest;
import com.reg21meme.dross.boss.BossWards;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks of the Cinder Colossus's lock and its cavern's ward ({@code boss.BossLocks}, {@link LairWard}), on a
 * lair laid out round the test arena by hand (no volcano is built: only where its cavern and passage would be counts):
 * <ul>
 *   <li>{@link #lockedPlayerDoesntRaiseTheColossus}: a survival player whose Oracle quest hasn't reached it, standing in
 *       the cavern, doesn't make it rise; one at "Set in Stone" (its offer heard) does, and so does a creative one.</li>
 *   <li>{@link #cavernWardShape}: the cavern and the crater's throat are deep in the ward (sent back), the passage just
 *       inside the mouth a push back out along it, the gully in front of the mouth and the volcano over the cavern
 *       outside it; with no saved way in, the ward's way out is in front of the mouth, outside it.</li>
 * </ul>
 */
@GameTestHolder(BossLockGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class LairLockGameTest
{
    private static final BlockPos MIDDLE = ColossusGameTest.MIDDLE;
    /** A made-up lair id (none is built with it). */
    private static final long FAKE_LAIR = 9_900_990_099L;
    /** The made-up lair's way in runs east, its mouth this many blocks out, its rim this high over its floor. */
    private static final Direction TUNNEL = Direction.EAST;
    private static final int MOUTH = LairShape.straightEnd() + 2;
    private static final int RIM_ABOVE = 40;

    private LairLockGameTest() {}

    private static void expect(boolean ok, String problem)
    {
        if (!ok)
        {
            throw new GameTestAssertException(problem);
        }
    }

    /** A lair whose floor's middle is the arena's middle (its floor the arena's). */
    private static LairKeeper.Volume lairAt(GameTestHelper helper)
    {
        BlockPos floor = helper.absolutePos(MIDDLE.below());
        return new LairKeeper.Volume(FAKE_LAIR, floor.getX(), floor.getZ(), floor.getY(), TUNNEL, MOUTH, floor.getY() + RIM_ABOVE);
    }

    @GameTest(template = BossLockGameTest.ARENA, batch = "dross_locks_lair_rise", timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 200)
    public static void lockedPlayerDoesntRaiseTheColossus(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, LairLockGameTest::lockedPlayerDoesntRaiseTheColossus))
        {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        try
        {
            LairKeeper.Volume lair = lairAt(helper);
            ServerPlayer player = BossLockGameTest.locked(helper, players, "lair-lock-locked", MIDDLE);
            expect(lair.inCavern(player.position()), "the stand-in should be standing in the made-up cavern");
            expect(LairKeeper.walkerIn(helper.getLevel(), lair) == null, "the Colossus rose for a player it's locked for");
            BossLockGameTest.allow(player, OracleBoss.COLOSSUS);
            expect(LairKeeper.walkerIn(helper.getLevel(), lair) == player, "the Colossus should rise for a player at \"Set in Stone\"");
            com.reg21meme.dross.quest.oracle.OracleProgress.reset(player);
            ServerPlayer creative = BossLockGameTest.locked(helper, players, "lair-lock-creative", MIDDLE.offset(2, 0, 0));
            creative.setGameMode(GameType.CREATIVE);
            expect(LairKeeper.walkerIn(helper.getLevel(), lair) == creative, "the Colossus should rise for a creative player");
        }
        finally
        {
            ColossusGameTest.leaveAll(players);
        }
        helper.succeed();
    }

    @GameTest(template = BossLockGameTest.EMPTY, batch = "dross_locks_lair_ward", timeoutTicks = 200)
    public static void cavernWardShape(GameTestHelper helper)
    {
        BlockPos at = helper.absolutePos(BlockPos.ZERO);
        LairKeeper.Volume lair = new LairKeeper.Volume(FAKE_LAIR, at.getX(), at.getZ(), at.getY(), TUNNEL, MOUTH, at.getY() + RIM_ABOVE);
        LairWard ward = new LairWard(lair, null, 0.0F);
        double cx = at.getX() + 0.5D;
        double cz = at.getZ() + 0.5D;
        double feetY = at.getY() + 1.0D;
        // in the cavern, and dropping down the crater's throat into it: deep (sent back)
        expect(ward.depth(new Vec3(cx, feetY, cz)) > BossWards.PUSH_DEPTH, "the cavern's middle should be deep in the ward");
        expect(ward.depth(new Vec3(cx - 15.0D, feetY, cz + 6.0D)) > BossWards.PUSH_DEPTH, "the cavern by its wall should be deep in the ward");
        expect(ward.depth(new Vec3(cx + 2.0D, feetY + LairTuning.DOME_HEIGHT + 5.0D, cz)) > BossWards.PUSH_DEPTH,
                "the crater's throat should be deep in the ward");
        // the passage just inside the mouth: a push, back out along it (east)
        double inMouth = ward.depth(new Vec3(cx + MOUTH - 1.0D, feetY, cz));
        expect(inMouth > 0.0D && inMouth <= BossWards.PUSH_DEPTH, "just inside the mouth should be a push from the ward's edge (" + inMouth + ")");
        Vec3 out = ward.outward(new Vec3(cx + MOUTH - 1.0D, feetY, cz));
        expect(out.x > 0.99D, "the push should be back out along the passage (east), not " + out);
        // the gully in front of the mouth and the volcano over the cavern: outside
        expect(ward.depth(new Vec3(cx + MOUTH + 2.0D, feetY, cz)) <= 0.0D, "the gully in front of the mouth should be outside the ward");
        expect(ward.depth(new Vec3(cx + 12.0D, feetY + LairTuning.DOME_HEIGHT + 8.0D, cz)) <= 0.0D,
                "the volcano over the cavern should be outside the ward");
        expect(ward.depth(new Vec3(cx + MOUTH - 2.0D, feetY + LairTuning.MOUTH_HEIGHT + 2.0D, cz)) <= 0.0D,
                "the rock over the passage should be outside the ward");
        // its way out (no saved one here) lies outside it, in front of the mouth
        expect(ward.depth(ward.exit()) <= 0.0D && ward.exit().x > cx + MOUTH, "its way out should be in front of the mouth, outside");
        helper.succeed();
    }
}
