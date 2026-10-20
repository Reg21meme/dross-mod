package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.registry.ModEffects;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Headless checks of the Huntsman's fight ({@link HuntFight}, the user's table {@link HuntsmanTuning}): a spawn egg
 * brings him in his Alpha Moose's saddle, calm; the first hit starts the Wild Hunt with his horn, and their hits come off
 * one bar while their own health stays full; at 0 the split (he leaps off, neither can be hurt), then two full bars;
 * one dead makes the other angry (its bar, its speed); both dead, "The Hunter Hunted" for the killer and the players near,
 * not one far off; the move tables give the user's shares; they never hurt each other; the bola snares; the moose kicks
 * what's behind it; with nobody near the fight is broken off and both come back, full and calm, him in the saddle.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class HuntsmanFightGameTest
{
    private static final String ARENA = ColossusGameTest.ARENA;
    private static final BlockPos MIDDLE = ColossusGameTest.MIDDLE;
    private static final int WAIT = ColossusGameTest.ARENA_WAIT_TICKS;
    private static final float HIT = 10.0F;
    private static final float HUGE = 1000.0F;
    /** A share of the move table may be off by this many of chance's standard deviations (and at least by
     * {@link #SHARE_TOLERANCE_MIN}): the rolls come from a fixed seed, so a tighter limit could fail every time. */
    private static final double SHARE_SIGMAS = 4.0D;
    private static final double SHARE_TOLERANCE_MIN = 0.01D;
    private static final int ROLLS = 20_000;

    private HuntsmanFightGameTest() {}

    private static Huntsman pair(GameTestHelper helper, BlockPos at, float yaw)
    {
        Huntsman h = HuntsmanPair.spawn(helper.getLevel(), helper.absoluteVec(Vec3.atBottomCenterOf(at)), yaw);
        if (h == null || !(h.getVehicle() instanceof AlphaMoose))
        {
            throw new GameTestAssertException("couldn't make the Huntsman in his Alpha Moose's saddle");
        }
        return h;
    }

    private static AlphaMoose moose(Huntsman h)
    {
        AlphaMoose m = HuntFight.mooseOf(h);
        if (m == null)
        {
            throw new GameTestAssertException("the Huntsman has no Alpha Moose");
        }
        return m;
    }

    /** A blow from a zombie standing where {@code at} stands (it isn't put in the world, so nothing can hit it back;
     * standing there, it's no target to chase across the world). */
    private static DamageSource byZombie(ServerLevel level, HuntMob at)
    {
        Zombie z = EntityType.ZOMBIE.create(level);
        if (z == null)
        {
            throw new GameTestAssertException("couldn't make a zombie");
        }
        z.moveTo(at.getX(), at.getY(), at.getZ(), 0.0F, 0.0F);
        return level.damageSources().mobAttack(z);
    }

    /** Hits {@code mob} (its last hit's cool-down cleared so every hit counts). */
    private static boolean hit(HuntMob mob, DamageSource source, float amount)
    {
        mob.invulnerableTime = 0;
        return mob.hurt(source, amount);
    }

    private static void clear(HuntMob... mobs)
    {
        for (HuntMob m : mobs)
        {
            if (m != null)
            {
                m.discard();
            }
        }
    }

    private static String barKey(HuntMob mob)
    {
        ServerBossEvent bar = HuntFight.bar(mob);
        if (bar == null || !(bar.getName().getContents() instanceof TranslatableContents t))
        {
            return null;
        }
        return t.getKey();
    }

    // ---------------------------------------------------------------- calm, the first hit, the shared bar

    @GameTest(template = ARENA, batch = "dross_huntsman_egg", timeoutTicks = WAIT + 100)
    public static void eggBringsHimInTheSaddleCalm(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::eggBringsHimInTheSaddleCalm))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = ModEntities.HUNTSMAN.get().spawn(level, helper.absolutePos(MIDDLE), MobSpawnType.SPAWN_EGG);
        if (h == null || !(h.getVehicle() instanceof AlphaMoose m))
        {
            throw new GameTestAssertException("a spawn egg should bring the Huntsman in his Alpha Moose's saddle");
        }
        if (!HuntFight.isPartner(h, m) || h.stage() != HuntStage.CALM || m.stage() != HuntStage.CALM)
        {
            clear(h, m);
            throw new GameTestAssertException("the pair should be partners and calm, but they're " + h.stage() + " and " + m.stage());
        }
        helper.runAfterDelay(40, () ->
        {
            boolean calm = h.stage() == HuntStage.CALM && m.stage() == HuntStage.CALM && h.getVehicle() == m;
            clear(h, m);
            if (!calm)
            {
                throw new GameTestAssertException("left alone they should stay calm, him in the saddle");
            }
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "dross_huntsman_first_hit", timeoutTicks = WAIT + 100)
    public static void firstHitStartsTheWildHunt(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::firstHitStartsTheWildHunt))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        try
        {
            hit(m, byZombie(level, m), HIT);
            if (h.stage() != HuntStage.WILD_HUNT || m.stage() != HuntStage.WILD_HUNT)
            {
                throw new GameTestAssertException("the first hit should start the Wild Hunt, but they're " + h.stage());
            }
            if (!h.current().name().equals("ride_horn"))
            {
                throw new GameTestAssertException("he should blow his horn when the hunt starts, but he plays " + h.current().name());
            }
            float afterMoose = m.wildHunt;
            hit(h, byZombie(level, h), HIT);
            float afterHim = m.wildHunt;
            if (Math.abs(afterMoose - (HuntsmanTuning.WILD_HUNT_HEALTH - HIT)) > 0.01F
                    || Math.abs(afterHim - (afterMoose - HIT)) > 0.01F)
            {
                throw new GameTestAssertException("their hits should come off the Wild Hunt's bar: " + afterMoose + " then "
                        + afterHim);
            }
            if (m.getHealth() < m.getMaxHealth() || h.getHealth() < h.getMaxHealth())
            {
                throw new GameTestAssertException("in stage 1 their own health should stay full, but it's " + m.getHealth()
                        + " and " + h.getHealth());
            }
        }
        finally
        {
            clear(h, m);
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------- the split and stage 2

    @GameTest(template = ARENA, batch = "dross_huntsman_split", timeoutTicks = WAIT + 200)
    public static void theSplitThenTwoBars(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::theSplitThenTwoBars))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        hit(m, byZombie(level, m), HIT);
        m.wildHunt = 5.0F;
        hit(m, byZombie(level, m), HIT);
        if (m.stage() != HuntStage.SPLIT || h.stage() != HuntStage.SPLIT)
        {
            clear(h, m);
            throw new GameTestAssertException("the bar at 0 should start the split, but they're " + m.stage());
        }
        if (hit(m, byZombie(level, m), HIT) || hit(h, byZombie(level, h), HIT))
        {
            clear(h, m);
            throw new GameTestAssertException("neither should be hurt during the split");
        }
        helper.runAfterDelay(HuntsmanTuning.SPLIT_INVULNERABLE_TICKS + 10, () ->
        {
            String problem = null;
            if (h.isMounted())
            {
                problem = "he should have leapt off the moose";
            }
            else if (h.stage() != HuntStage.DUEL || m.stage() != HuntStage.DUEL)
            {
                problem = "after the split they should fight on their own, but they're " + h.stage() + " and " + m.stage();
            }
            else if (h.getHealth() < h.getMaxHealth() || m.getHealth() < m.getMaxHealth()
                    || h.getMaxHealth() != HuntsmanTuning.HUNTSMAN_HEALTH || m.getMaxHealth() != HuntsmanTuning.MOOSE_HEALTH)
            {
                problem = "each should start stage 2 on a full bar of 150";
            }
            else if (!(HuntsmanBarShowcase.KEY_PREFIX + "huntsman").equals(barKey(h))
                    || !(HuntsmanBarShowcase.KEY_PREFIX + "alpha_moose").equals(barKey(m)))
            {
                problem = "stage 2 should show The Huntsman's and the Alpha Moose's bars, but they're " + barKey(h)
                        + " and " + barKey(m);
            }
            clear(h, m);
            if (problem != null)
            {
                throw new GameTestAssertException(problem);
            }
            helper.succeed();
        });
    }

    @GameTest(template = ARENA, batch = "dross_huntsman_end", timeoutTicks = WAIT + 200)
    public static void oneDownTheOtherAngryThenTheEnd(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::oneDownTheOtherAngryThenTheEnd))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        hit(m, byZombie(level, m), HIT);
        m.wildHunt = 1.0F;
        hit(m, byZombie(level, m), HIT);
        helper.runAfterDelay(HuntsmanTuning.SPLIT_INVULNERABLE_TICKS + 10, () ->
        {
            ServerPlayer killer = ColossusGameTest.joinPlayer(helper, "hunt-killer", MIDDLE.offset(4, 0, 0), 0.0F);
            ServerPlayer near = ColossusGameTest.joinPlayer(helper, "hunt-near", MIDDLE.offset(-4, 0, 3), 0.0F);
            ServerPlayer far = ColossusGameTest.joinPlayer(helper, "hunt-far", MIDDLE.offset(60, 0, 0), 0.0F);
            List<ServerPlayer> players = List.of(killer, near, far);
            String problem = null;
            double speed = h.getAttributeValue(Attributes.MOVEMENT_SPEED);
            hit(m, level.damageSources().playerAttack(killer), HUGE);
            if (!m.isDeadOrDying())
            {
                problem = "a huge hit should kill the moose";
            }
            else if (!h.isAngry() || !(HuntsmanBarShowcase.KEY_PREFIX + "huntsman_angry").equals(barKeyNext(h)))
            {
                problem = "with the moose dead the Huntsman should be angry, his bar blood red";
            }
            else if (h.getAttributeValue(Attributes.MOVEMENT_SPEED) < speed * (HuntsmanTuning.ANGRY_SPEED - 0.01D))
            {
                problem = "angry, he should be faster";
            }
            else
            {
                hit(h, level.damageSources().playerAttack(killer), HUGE);
                if (!h.isDeadOrDying())
                {
                    problem = "a huge hit should kill him";
                }
                else if (!DrossAdvancements.has(killer, DrossAdvancements.THE_HUNTER_HUNTED)
                        || !DrossAdvancements.has(near, DrossAdvancements.THE_HUNTER_HUNTED))
                {
                    problem = "the player who beat them and a player near should get The Hunter Hunted";
                }
                else if (DrossAdvancements.has(far, DrossAdvancements.THE_HUNTER_HUNTED))
                {
                    problem = "a player 60 blocks away shouldn't get The Hunter Hunted";
                }
            }
            ColossusGameTest.leaveAll(players);
            clear(h, m);
            if (problem != null)
            {
                throw new GameTestAssertException(problem);
            }
            helper.succeed();
        });
    }

    /** Its bar's key once the next tick has given it its look (the anger shows on the next update). */
    private static String barKeyNext(HuntMob mob)
    {
        HuntFight.tick(mob);
        return barKey(mob);
    }

    // ---------------------------------------------------------------- the tables, friends, the bola, the kick

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_huntsman_tables", timeoutTicks = 20)
    public static void movesFollowTheUsersShares(GameTestHelper helper)
    {
        RandomSource random = RandomSource.create(20261020L);
        for (Object[][] table : new Object[][][] {HuntsmanTuning.WILD_CHANCES, HuntsmanTuning.FOOT_CHANCES,
                HuntsmanTuning.MOOSE_CHANCES})
        {
            for (Object[] row : table)
            {
                int total = 0;
                for (int i = 1; i < row.length; i += 2)
                {
                    total += (Integer) row[i];
                }
                Map<String, Integer> got = new HashMap<>();
                for (int k = 0; k < ROLLS; k++)
                {
                    got.merge(HuntFight.pick(row, n -> true, random), 1, Integer::sum);
                }
                for (int i = 0; i < row.length; i += 2)
                {
                    double want = (Integer) row[i + 1] / (double) total;
                    double share = got.getOrDefault((String) row[i], 0) / (double) ROLLS;
                    double tolerance = Math.max(SHARE_TOLERANCE_MIN,
                            SHARE_SIGMAS * Math.sqrt(want * (1.0D - want) / ROLLS));
                    if (Math.abs(share - want) > tolerance)
                    {
                        throw new GameTestAssertException(row[i] + " should come up " + want + " of the time, but came up "
                                + share);
                    }
                }
                // a move that can't be made drops out: the others share its weight
                String gone = (String) row[0];
                for (int k = 0; k < 200; k++)
                {
                    if (gone.equals(HuntFight.pick(row, n -> !n.equals(gone), random)))
                    {
                        throw new GameTestAssertException(gone + " came up though it couldn't be made");
                    }
                }
            }
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_huntsman_friends", timeoutTicks = WAIT + 60)
    public static void theyNeverHurtEachOther(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::theyNeverHurtEachOther))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        try
        {
            Arrow arrow = new Arrow(level, h.getX(), h.getY() + 3.0D, h.getZ());
            arrow.setOwner(h);
            if (hit(m, level.damageSources().mobAttack(h), HIT) || hit(m, level.damageSources().arrow(arrow, h), HIT)
                    || hit(h, level.damageSources().mobAttack(m), HIT))
            {
                throw new GameTestAssertException("his blows and arrows shouldn't hurt his moose, nor its his");
            }
            if (m.stage() != HuntStage.CALM || m.wildHunt < HuntsmanTuning.WILD_HUNT_HEALTH)
            {
                throw new GameTestAssertException("a hit from his own partner shouldn't start the fight or touch the bar");
            }
            if (!HuntFight.within(m, m.position(), 0.0F, 6.0D, 180.0D).isEmpty())
            {
                throw new GameTestAssertException("the moose's blows shouldn't count him (or itself) as something to hit");
            }
        }
        finally
        {
            clear(h, m);
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_huntsman_bola", timeoutTicks = WAIT + 80)
    public static void theBolaSnares(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::theBolaSnares))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE.offset(-5, 0, 0), 0.0F);
        AlphaMoose m = moose(h);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null)
        {
            clear(h, m);
            throw new GameTestAssertException("couldn't make a zombie");
        }
        zombie.setNoAi(true);
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(4, 0, 0)));
        zombie.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        level.addFreshEntity(zombie);
        Bola.throwAt(h, HuntGoal.hand(h, HuntsmanTuning.RIDER_HAND_UP - 3.0D), zombie);
        helper.startSequence()
                .thenWaitUntil(() ->
                {
                    if (!zombie.hasEffect(ModEffects.SNARED.get()))
                    {
                        throw new GameTestAssertException("waiting for the bola to land");
                    }
                })
                .thenExecute(() ->
                {
                    boolean slowed = zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN);
                    clear(h, m);
                    zombie.discard();
                    if (!slowed)
                    {
                        throw new GameTestAssertException("the bola should slow what it snares, after holding it still");
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_huntsman_kick", timeoutTicks = WAIT + 80)
    public static void theMooseKicksWhatsBehindIt(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::theMooseKicksWhatsBehindIt))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        // the moose on its own, facing south; a frozen zombie close behind it (to its north) hits it, which starts the
        // fight (stage 2, no rider) and makes the zombie its target. (A stand-in player can't be hurt: he's never
        // ticked, so his few seconds of safety on joining never run out.)
        h.stopRiding();
        h.discard();
        m.partnerId = null;
        m.setYRot(0.0F);
        m.yBodyRot = 0.0F;
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null)
        {
            m.discard();
            throw new GameTestAssertException("couldn't make a zombie");
        }
        zombie.setNoAi(true);
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 100_000, 0)); // only the kick may hurt it
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(0, 0, -4)));
        zombie.moveTo(at.x, at.y, at.z, 180.0F, 0.0F);
        level.addFreshEntity(zombie);
        hit(m, level.damageSources().mobAttack(zombie), 1.0F);
        m.setYRot(0.0F);
        m.yBodyRot = 0.0F;
        float before = zombie.getHealth();
        boolean[] kicked = {false};
        helper.startSequence()
                .thenWaitUntil(() ->
                {
                    kicked[0] |= m.current().name().equals("back_kick");
                    if (!kicked[0] || zombie.getHealth() >= before)
                    {
                        throw new GameTestAssertException("waiting for the back kick (the moose plays "
                                + m.current().name() + ", its target " + m.getTarget() + ")");
                    }
                })
                .thenExecute(() ->
                {
                    zombie.discard();
                    m.discard();
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------- breaking off

    @GameTest(template = ARENA, batch = "dross_huntsman_break_off",
            timeoutTicks = WAIT + HuntsmanTuning.BREAK_OFF_TICKS + 200)
    public static void brokenOffTheyComeBackFull(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::brokenOffTheyComeBackFull))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        hit(m, byZombie(level, m), HIT);
        m.wildHunt = 1.0F;
        hit(m, byZombie(level, m), HIT);
        helper.runAfterDelay(HuntsmanTuning.SPLIT_INVULNERABLE_TICKS + 10, () ->
        {
            hit(m, byZombie(level, m), HUGE);
            hit(h, byZombie(level, h), HIT * 3.0F);
            if (!m.isDeadOrDying() || h.getHealth() >= h.getMaxHealth())
            {
                clear(h, m);
                throw new GameTestAssertException("the moose should be dead and him hurt before the break-off");
            }
            helper.runAfterDelay(HuntsmanTuning.BREAK_OFF_TICKS + 40, () ->
            {
                String problem = null;
                AlphaMoose back = HuntFight.mooseOf(h);
                if (back == null || back == m || !back.isAlive() || h.getVehicle() != back)
                {
                    problem = "with nobody near, the fight should be broken off: a new Alpha Moose under him";
                }
                else if (h.stage() != HuntStage.CALM || back.stage() != HuntStage.CALM || h.isAngry())
                {
                    problem = "broken off, they should be calm again, not angry";
                }
                else if (h.getHealth() < h.getMaxHealth() || back.wildHunt < HuntsmanTuning.WILD_HUNT_HEALTH)
                {
                    problem = "broken off, they should be back at full health";
                }
                clear(h, back, m);
                if (problem != null)
                {
                    throw new GameTestAssertException(problem);
                }
                helper.succeed();
            });
        });
    }
}
