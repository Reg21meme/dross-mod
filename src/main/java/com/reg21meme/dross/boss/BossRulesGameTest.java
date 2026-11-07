package com.reg21meme.dross.boss;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.boss.golem.ColossusTuning;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.boss.ratking.RatKingTuning;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.List;

/**
 * Headless checks of {@link BossRules} (the user's boss buff, November 6-7, 2026), on the Cinder Colossus and the Rat
 * King: a hit takes at most a fifteenth of a boss's health ({@code /kill} and the Admin Sword aside); extra players
 * raise its health keeping its share and its blows hit harder; nobody near for 3 minutes, it resets (the Rat King asleep,
 * full health, no bonus); and his staff's Poison II.
 * <p>
 * Each test has a batch of its own, so no other test's stand-in players are near (they'd count for the bonus).
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class BossRulesGameTest
{
    private static final String ARENA = ColossusGameTest.ARENA;
    private static final BlockPos MIDDLE = ColossusGameTest.MIDDLE;
    private static final float HUGE_HIT = 1000.0F;
    private static final float TOLERANCE = 0.05F;
    /** Ticks to wait for a boss to count the players near it (it counts every {@link BossRules#COUNT_EVERY}). */
    private static final int COUNT_WAIT = BossRules.COUNT_EVERY * 3;

    private BossRulesGameTest() {}

    @GameTest(template = ARENA, batch = "dross_boss_rules_cap", timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 200)
    public static void aHitIsCapped(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, BossRulesGameTest::aHitIsCapped))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        CinderColossus golem = CinderColossus.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        if (golem == null)
        {
            throw new GameTestAssertException("the Colossus couldn't be spawned");
        }
        ServerPlayer admin = ColossusGameTest.joinPlayer(helper, "rules-admin", MIDDLE.offset(6, 0, 0), 0.0F);
        List<ServerPlayer> players = List.of(admin);
        String problem = null;
        try
        {
            float max = golem.getMaxHealth();
            if (Math.abs(golem.getAttribute(Attributes.MAX_HEALTH).getBaseValue() - ColossusTuning.MAX_HEALTH) > 0.01D)
            {
                problem = "its health should be " + ColossusTuning.MAX_HEALTH + ", but it's " + max;
            }
            golem.hurt(level.damageSources().generic(), HUGE_HIT);
            float lost = max - golem.getHealth();
            if (problem == null && Math.abs(lost - max * BossRules.HIT_CAP_SHARE) > TOLERANCE)
            {
                problem = "a hit of " + HUGE_HIT + " should take a fifteenth of its health (" + max * BossRules.HIT_CAP_SHARE
                        + "), but it took " + lost;
            }
            // The Admin Sword's blow isn't capped: from full health it goes straight down to its first stage's line.
            golem.setHealth(max);
            golem.invulnerableTime = 0;
            admin.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.ADMIN_SWORD.get()));
            golem.hurt(level.damageSources().playerAttack(admin), HUGE_HIT);
            if (problem == null && Math.abs(golem.getHealth() - max * ColossusTuning.CRACKING_AT) > TOLERANCE)
            {
                problem = "the Admin Sword's blow shouldn't be capped: it should stop at the Shell's end ("
                        + max * ColossusTuning.CRACKING_AT + "), but it's at " + golem.getHealth();
            }
            // /kill isn't capped either.
            golem.kill();
            if (problem == null && !golem.isDeadOrDying())
            {
                problem = "/kill should kill it";
            }
        }
        finally
        {
            ColossusGameTest.leaveAll(players);
            golem.discard();
        }
        if (problem != null)
        {
            throw new GameTestAssertException(problem);
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_boss_rules_players", timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 1200)
    public static void morePlayersThenAReset(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, BossRulesGameTest::morePlayersThenAReset))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        RatKing king = RatKing.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        Pig pig = EntityType.PIG.create(level);
        if (king == null || pig == null)
        {
            throw new GameTestAssertException("the Rat King (or the test pig) couldn't be spawned");
        }
        Vec3 p = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(-5, 0, -5)));
        pig.moveTo(p.x, p.y, p.z, 0.0F, 0.0F);
        pig.setNoAi(true);
        level.addFreshEntity(pig);
        float base = (float) RatKingTuning.MAX_HEALTH;
        king.setHealth(base * 0.5F);
        ServerPlayer one = ColossusGameTest.joinPlayer(helper, "rules-one", MIDDLE.offset(5, 0, 0), 0.0F);
        ServerPlayer two = ColossusGameTest.joinPlayer(helper, "rules-two", MIDDLE.offset(-5, 0, 3), 0.0F);
        List<ServerPlayer> players = List.of(one, two);
        helper.startSequence()
                .thenExecuteAfter(COUNT_WAIT, () -> {
                    int extra = king.bossScale().extraPlayers();
                    String problem = null;
                    if (extra < 1)
                    {
                        problem = "with two players near he should have a bonus, but he has none";
                    }
                    else if (Math.abs(king.getMaxHealth() - base * (1.0F + 0.5F * extra)) > TOLERANCE)
                    {
                        problem = "with " + extra + " extra player(s) his health should be " + base * (1.0F + 0.5F * extra)
                                + ", but it's " + king.getMaxHealth();
                    }
                    else if (Math.abs(king.getHealth() / king.getMaxHealth() - 0.5F) > 0.01F)
                    {
                        problem = "his health should keep its share (half), but it's " + king.getHealth() + " of "
                                + king.getMaxHealth();
                    }
                    else
                    {
                        float before = pig.getHealth();
                        pig.hurt(level.damageSources().mobAttack(king), 5.0F);
                        float expected = 5.0F * (1.0F + BossRules.DAMAGE_PER_PLAYER * extra);
                        if (Math.abs(before - pig.getHealth() - expected) > TOLERANCE)
                        {
                            problem = "his blow of 5 should do " + expected + " with " + extra + " extra player(s), but it did "
                                    + (before - pig.getHealth());
                        }
                    }
                    // His staff's poison: Poison II, 4 s.
                    king.staffPoison(pig);
                    MobEffectInstance poison = pig.getEffect(MobEffects.POISON);
                    if (problem == null && (poison == null || poison.getAmplifier() != RatKingTuning.STAFF_POISON_LEVEL
                            || poison.getDuration() != RatKingTuning.STAFF_POISON_TICKS))
                    {
                        problem = "his staff should give Poison II for " + RatKingTuning.STAFF_POISON_TICKS + " ticks, but gave "
                                + poison;
                    }
                    if (problem != null)
                    {
                        ColossusGameTest.leaveAll(players);
                        king.discard();
                        pig.discard();
                        throw new GameTestAssertException(problem);
                    }
                    // Everyone leaves; as if 3 minutes had passed: he resets.
                    ColossusGameTest.leaveAll(players);
                    king.bossScale().pretendAway(BossRules.RESET_TICKS);
                })
                .thenWaitUntil(() -> {
                    if (!(king.isAsleep() && king.bossScale().extraPlayers() == 0
                            && Math.abs(king.getMaxHealth() - base) < TOLERANCE && king.getHealth() >= base - TOLERANCE))
                    {
                        throw new GameTestAssertException("after 3 minutes with nobody near he should be asleep at full health ("
                                + base + ") with no bonus; he's " + king.getAction() + ", " + king.getHealth() + " of "
                                + king.getMaxHealth() + ", bonus " + king.bossScale().extraPlayers());
                    }
                })
                .thenExecute(() -> {
                    king.discard();
                    pig.discard();
                })
                .thenSucceed();
    }
}
