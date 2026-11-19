package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.boss.BossLockGameTest;
import com.reg21meme.dross.boss.BossRules;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.boss.huntsman.village.HuntsmanVillages;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.GameType;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless check of the Huntsman's lock ({@code boss.BossLocks}): a village's pair (at home) doesn't come home for, spot,
 * pick or count a survival player whose Oracle quest hasn't reached it, and their blow doesn't start the Wild Hunt or take
 * anything off its bar; once their quest reaches it ("The Hunter Hunted", its offer heard) they're a foe, and their blow
 * starts the hunt. A creative player still brings him home (testers see him).
 */
@GameTestHolder(BossLockGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class HuntLockGameTest
{
    private static final BlockPos MIDDLE = ColossusGameTest.MIDDLE;
    /** A made-up village id (not 0: the pair counts as at home). */
    private static final long FAKE_VILLAGE = 4_343_434L;
    private static final float HIT = 10.0F;
    /** Ticks for the pair to look about after a blow. */
    private static final int SETTLE = 20;

    private HuntLockGameTest() {}

    private static void expect(boolean ok, String problem)
    {
        if (!ok)
        {
            throw new GameTestAssertException(problem);
        }
    }

    @GameTest(template = BossLockGameTest.ARENA, batch = "dross_locks_hunt", timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 400)
    public static void homeHuntIgnoresALockedPlayer(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntLockGameTest::homeHuntIgnoresALockedPlayer))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<ServerPlayer> players = new ArrayList<>();
        Huntsman h = HuntsmanFightGameTest.pair(helper, MIDDLE, -90.0F);
        AlphaMoose m = HuntsmanFightGameTest.moose(h);
        Runnable cleanUp = () -> {
            HuntsmanFightGameTest.clear(h, m);
            ColossusGameTest.leaveAll(players);
        };
        ServerPlayer player;
        float[] pool = new float[1];
        try
        {
            for (HuntMob mob : new HuntMob[] {h, m})
            {
                mob.setHome(FAKE_VILLAGE, helper.absolutePos(MIDDLE), 1);
            }
            expect(h.lockedAs() == OracleBoss.HUNTSMAN && m.lockedAs() == OracleBoss.HUNTSMAN, "a village's pair should be locked behind its quest");
            player = BossLockGameTest.locked(helper, players, "hunt-lock-locked", MIDDLE.offset(6, 0, 0));
            player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60, 4, false, false));
            expect(!HuntsmanVillages.bringsHim(player), "a locked player shouldn't bring him home");
            expect(!HuntFight.isFoe(h, player) && !HuntFight.isFoe(m, player), "a locked player shouldn't be their foe");
            expect(BossRules.playersNear(m, level, 40.0D) == 0, "a locked player counted for their bonus");
            float before = m.wildHunt;
            HuntsmanFightGameTest.hit(m, level.damageSources().playerAttack(player), HIT);
            HuntsmanFightGameTest.hit(h, level.damageSources().playerAttack(player), HIT);
            expect(m.stage() == HuntStage.CALM && h.stage() == HuntStage.CALM, "a locked player's blow started the Wild Hunt");
            expect(m.wildHunt >= before, "a locked player's blow took something off the Wild Hunt's bar");
            ServerPlayer creative = BossLockGameTest.locked(helper, players, "hunt-lock-creative", MIDDLE.offset(-6, 0, 0));
            creative.setGameMode(GameType.CREATIVE);
            expect(HuntsmanVillages.bringsHim(creative), "a creative player should still bring him home");
            expect(!HuntsmanVillages.wakesHim(player) && !HuntsmanVillages.wakesHim(creative),
                    "until their quest reaches him, he sleeps (creative alone doesn't wake him)");
            BossLockGameTest.allow(player, OracleBoss.HUNTSMAN);
            expect(HuntsmanVillages.bringsHim(player), "a player whose quest has reached him should bring him home");
            expect(HuntsmanVillages.wakesHim(player), "a player whose quest has reached him should wake him onto his patrol");
            expect(HuntFight.isFoe(h, player) && HuntFight.isFoe(m, player), "a player whose quest has reached him should be their foe");
            pool[0] = m.wildHunt;
            HuntsmanFightGameTest.hit(m, level.damageSources().playerAttack(player), HIT);
        }
        catch (RuntimeException e)
        {
            cleanUp.run();
            throw e;
        }
        helper.startSequence()
                .thenIdle(SETTLE)
                .thenExecute(() -> {
                    try
                    {
                        expect(m.stage() == HuntStage.WILD_HUNT, "an allowed player's blow should start the Wild Hunt (stage " + m.stage() + ")");
                        expect(m.wildHunt < pool[0], "an allowed player's blow should come off the Wild Hunt's bar");
                    }
                    finally
                    {
                        cleanUp.run();
                    }
                })
                .thenSucceed();
    }
}
