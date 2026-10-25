package com.reg21meme.dross.boss.huntsman;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rage.RageTeam;
import com.reg21meme.dross.boss.rimefall.Rimehorn;
import com.reg21meme.dross.boss.rimefall.Moose;
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
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Headless checks of the Huntsman's fight ({@link HuntFight}, the user's table {@link HuntsmanTuning}): a spawn egg
 * brings him in his Alpha Moose's saddle, calm; a player in survival in sight starts the Wild Hunt with his horn (one in
 * creative doesn't), so does the first hit, and their hits come off one bar while their own health stays full; an arrow
 * at him in the saddle hits him, not the moose; raging one rages both, and a raged enemy is whom they go for; a loop
 * played faster or slower keeps its beat; at 0 the split (he leaps off, neither can be hurt), then two full bars;
 * one dead makes the other angry (its bar, its speed); both dead, "The Hunter Hunted" for the killer and the players near,
 * not one far off; the move tables give the user's shares; they never hurt each other; the bola snares; the moose kicks
 * what's behind it; with nobody near the fight is broken off and both come back, full and calm, him in the saddle; a
 * village pair's chase has its leash, and a foe who gets away is let go (they don't vanish).
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
    /** Ticks for a new pair to settle: the moose seats him (his hitbox over its back) when it next moves him. */
    private static final int RIDER_SETTLE_TICKS = 3;

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

    /** Removes the hostile mobs (but theirs) within the break-off range of {@code near} and a little more. */
    private static void clearFoesNear(ServerLevel level, HuntMob near)
    {
        double r = HuntsmanTuning.BREAK_OFF_RANGE + 16.0D;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, near.getBoundingBox().inflate(r),
                e -> e instanceof Enemy && !(e instanceof HuntMob)))
        {
            mob.discard();
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
            if (!h.crying() && !h.current().name().equals("ride_horn"))      // his war cry, then the horn
            {
                throw new GameTestAssertException("he should cry out and blow his horn when the hunt starts, but he plays "
                        + h.current().name());
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

    /** Calm, a player in creative mode in sight isn't a foe; a player in survival is: the horn, and the Wild Hunt. */
    @GameTest(template = ARENA, batch = "dross_huntsman_spot", timeoutTicks = WAIT + 200)
    public static void aFoeInSightStartsTheHunt(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::aFoeInSightStartsTheHunt))
        {
            return;
        }
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        ServerPlayer watcher = ColossusGameTest.joinPlayer(helper, "hunt-watcher", MIDDLE.offset(6, 0, 0), 90.0F);
        watcher.setGameMode(GameType.CREATIVE);
        int look = HuntsmanTuning.SPOT_INTERVAL * 3;
        helper.runAfterDelay(look, () ->
        {
            if (h.stage() != HuntStage.CALM || m.stage() != HuntStage.CALM)
            {
                ColossusGameTest.leaveAll(List.of(watcher));
                clear(h, m);
                throw new GameTestAssertException("a player in creative mode isn't a foe: they should stay calm, but they're "
                        + m.stage());
            }
            ServerPlayer hunted = ColossusGameTest.joinPlayer(helper, "hunt-spotted", MIDDLE.offset(-6, 0, 5), 0.0F);
            helper.runAfterDelay(look, () ->
            {
                String problem = null;
                if (h.stage() != HuntStage.WILD_HUNT || m.stage() != HuntStage.WILD_HUNT)
                {
                    problem = "a player in sight should start the Wild Hunt, but they're " + m.stage();
                }
                else if (!h.crying() && !h.current().name().equals("ride_horn"))      // his war cry, then the horn
                {
                    problem = "he should cry out and blow his horn as the hunt starts, but he plays " + h.current().name();
                }
                else if (m.getTarget() != hunted || h.getTarget() != hunted)
                {
                    problem = "both should go for the player in survival, not " + m.getTarget() + " and " + h.getTarget();
                }
                ColossusGameTest.leaveAll(List.of(watcher, hunted));
                clear(h, m);
                if (problem != null)
                {
                    throw new GameTestAssertException(problem);
                }
                helper.succeed();
            });
        });
    }

    /** The Rage Wand on one of the pair rages both onto its team; a raged enemy of another team near starts the hunt,
     * and it's whom both of them go for. */
    @GameTest(template = ARENA, batch = "dross_huntsman_rage", timeoutTicks = WAIT + 200)
    public static void ragedTheyFightAsAPair(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::ragedTheyFightAsAPair))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE.offset(-3, 0, 0), 0.0F);
        AlphaMoose m = moose(h);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null)
        {
            clear(h, m);
            throw new GameTestAssertException("couldn't make a zombie");
        }
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(5, 0, 6)));
        zombie.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
        zombie.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 100_000, 4)); // it stays for the check
        level.addFreshEntity(zombie);
        boolean raged = Rage.enrage(h, RageTeam.RED) && Rage.enrage(zombie, RageTeam.BLUE);
        if (!raged || !Rage.isRaged(m) || Rage.teamOf(m) != RageTeam.RED)
        {
            clear(h, m);
            zombie.discard();
            throw new GameTestAssertException("raging the Huntsman should rage his moose onto his team too, but it's "
                    + (Rage.isRaged(m) ? Rage.teamOf(m) : "not raged"));
        }
        helper.runAfterDelay(HuntsmanTuning.SPOT_INTERVAL * 3, () ->
        {
            String problem = null;
            if (!m.stage().fighting())
            {
                problem = "a raged enemy near should start the hunt, but they're " + m.stage();
            }
            else if (m.getTarget() != zombie || h.getTarget() != zombie)
            {
                problem = "both should go for the raged enemy, not " + m.getTarget() + " and " + h.getTarget();
            }
            clear(h, m);
            zombie.discard();
            if (problem != null)
            {
                throw new GameTestAssertException(problem);
            }
            helper.succeed();
        });
    }

    /** In the saddle his hitbox is over the moose's back, a little wider than the moose's: an arrow at his body hits
     * him, not the moose under him. */
    @GameTest(template = ARENA, batch = "dross_huntsman_rider_hit", timeoutTicks = WAIT + 200)
    public static void theRiderCanBeHit(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::theRiderCanBeHit))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE.offset(-3, 0, 0), 0.0F);
        AlphaMoose m = moose(h);
        h.setNoAi(true);
        m.setNoAi(true);
        Arrow[] arrow = new Arrow[1];
        float[] bar = new float[1];
        helper.startSequence()
                // the moose seats him on its next tick
                .thenIdle(RIDER_SETTLE_TICKS)
                .thenExecute(() ->
                {
                    if (Math.abs(h.getBoundingBox().minY - m.getY() - HuntsmanTuning.RIDER_SEAT) > 0.01D
                            || h.getBbWidth() <= m.getBbWidth())
                    {
                        clear(h, m);
                        throw new GameTestAssertException("in the saddle his hitbox should start " + HuntsmanTuning.RIDER_SEAT
                                + " over the moose's feet, wider than the moose's; it's at "
                                + (h.getBoundingBox().minY - m.getY()) + ", " + h.getBbWidth() + " wide (the moose "
                                + m.getBbWidth() + "; mounted " + h.isMounted() + ", his dimensions "
                                + h.getDimensions(h.getPose()) + ")");
                    }
                    // an arrow from across the arena, level with his chest
                    Arrow a = new Arrow(level, h.getX() + 9.0D, h.getY() + 1.5D, h.getZ());
                    a.setBaseDamage(2.0D);
                    a.shoot(-1.0D, 0.0D, 0.0D, 3.0F, 0.0F);
                    level.addFreshEntity(a);
                    arrow[0] = a;
                    bar[0] = m.wildHunt;
                })
                .thenWaitUntil(() ->
                {
                    if (h.hurtTime <= 0 && m.hurtTime <= 0)
                    {
                        throw new GameTestAssertException("waiting for the arrow to hit");
                    }
                })
                .thenExecute(() ->
                {
                    boolean him = h.hurtTime > 0;
                    boolean moose = m.hurtTime > 0;
                    float after = m.wildHunt;
                    clear(h, m);
                    arrow[0].discard();
                    if (!him || moose)
                    {
                        throw new GameTestAssertException("an arrow at his chest should hit him, not the moose (him " + him
                                + ", the moose " + moose + ")");
                    }
                    if (after >= bar[0])
                    {
                        throw new GameTestAssertException("his hit should come off the Wild Hunt's bar");
                    }
                })
                .thenSucceed();
    }

    /** The Alpha Moose's hitboxes past its square middle (the user, October 23, 2026: "increase his hitbox near his
     * legs"): its chest and front legs ahead, its rump and hind legs behind, its head further ahead; a blow on one
     * hurts it, and they're the Huntsman's partner (his shots and throws fly through them). */
    @GameTest(template = ARENA, batch = "dross_huntsman_legs", timeoutTicks = WAIT + 100)
    public static void theMoosesLegsAreHitboxes(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::theMoosesLegsAreHitboxes))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        h.setNoAi(true);
        m.setNoAi(true);
        helper.startSequence()
                .thenIdle(RIDER_SETTLE_TICKS)
                .thenExecute(() ->
                {
                    var parts = m.getBodyParts();
                    double yaw = Math.toRadians(m.yBodyRot);
                    Vec3 ahead = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw));
                    double[] along = new double[parts.length];
                    for (int i = 0; i < parts.length; i++)
                    {
                        along[i] = parts[i].position().subtract(m.position()).dot(ahead);
                    }
                    String problem = null;
                    if (parts.length != 3 || along[0] < 1.5D || along[1] > -1.5D || along[2] < 3.0D)
                    {
                        problem = "its chest, rump and head hitboxes should sit about 1.9 ahead, 1.9 behind and 3.4 ahead, "
                                + "but they're " + java.util.Arrays.toString(along);
                    }
                    else if (parts[1].getBoundingBox().minY > m.getY() + 0.1D)
                    {
                        problem = "its rump's hitbox should reach down its hind legs to the ground";
                    }
                    else if (!HuntFight.isPartner(h, parts[2]))
                    {
                        problem = "its hitboxes should count as his partner (his arrows fly through them)";
                    }
                    if (problem == null)
                    {
                        float before = m.wildHunt;
                        m.invulnerableTime = 0;
                        parts[1].hurt(byZombie(level, m), HIT);
                        m.invulnerableTime = 0;
                        parts[1].hurt(byZombie(level, m), HIT);
                        if (m.stage() != HuntStage.WILD_HUNT || m.wildHunt >= before)
                        {
                            problem = "blows on its rump should start the Wild Hunt and come off its bar (" + m.stage()
                                    + ", " + before + " to " + m.wildHunt + ")";
                        }
                    }
                    clear(h, m);
                    if (problem != null)
                    {
                        throw new GameTestAssertException(problem);
                    }
                })
                .thenSucceed();
    }

    /** A loop played faster or slower goes on from the moment it was at (its beat moves with it); a move plays as
     * made. */
    @GameTest(template = ARENA, batch = "dross_huntsman_clock", timeoutTicks = WAIT + 100)
    public static void theClockKeepsTheBeat(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::theClockKeepsTheBeat))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        m.setShowcase(true);
        h.setShowcase(true);
        m.play("run");
        long started = level.getGameTime();
        if (Math.abs(m.anchor() - (started + 4.0D)) > 1.0E-3D || m.pace() != 1.0F)
        {
            clear(h, m);
            throw new GameTestAssertException("a new animation's beat should fall after its blend, played as made: "
                    + m.anchor() + " at " + m.pace());
        }
        helper.runAfterDelay(30, () ->
        {
            double now = level.getGameTime();
            double length = HuntAnims.named(HuntAnims.ALPHA_MOOSE, "run").length();
            double before = ((now - m.anchor()) * m.pace()) % length;
            m.setPace(0.8F);
            double after = ((now - m.anchor()) * m.pace()) % length;
            clear(h, m);
            if (Math.abs(before - after) > 0.01D && Math.abs(Math.abs(before - after) - length) > 0.01D)
            {
                throw new GameTestAssertException("slowed down, the gallop should go on from where it was (" + before
                        + " ticks into it), but it's at " + after);
            }
            helper.succeed();
        });
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
        for (Object[][] table : new Object[][][] {HuntsmanTuning.WILD_MOOSE_CHANCES, HuntsmanTuning.SADDLE_CHANCES,
                HuntsmanTuning.FOOT_CHANCES, HuntsmanTuning.MOOSE_CHANCES})
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

    /** In the Wild Hunt they fight at once (the user, October 23, 2026: "the hunter needs to be shooting or hitting me
     * with a spear while the moose attacks"): the moose's moves and his own from the saddle on two timers, so some
     * moment has both in the middle of a move. */
    @GameTest(template = ARENA, batch = "dross_huntsman_together", timeoutTicks = WAIT + 600)
    public static void theyAttackAtOnce(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::theyAttackAtOnce))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        // a zombie that can't be killed or move, 9 blocks off: their foe (a hostile mob), and it starts the fight (it keeps
        // its AI: a mob without one is a showcase statue to them, never a foe, so they'd only go for it while its hit is
        // fresh, 5 s)
        Zombie zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null)
        {
            clear(h, m);
            throw new GameTestAssertException("couldn't make a zombie");
        }
        zombie.setInvulnerable(true);
        java.util.Objects.requireNonNull(zombie.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED))
                .setBaseValue(0.0D);
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(9, 0, 0)));
        zombie.moveTo(at.x, at.y, at.z, 90.0F, 0.0F);
        level.addFreshEntity(zombie);
        hit(m, level.damageSources().mobAttack(zombie), 1.0F);
        String[] seen = {null};
        helper.startSequence()
                .thenWaitUntil(() ->
                {
                    clearFoesNearBut(level, m, zombie);
                    if (m.stage() == HuntStage.WILD_HUNT && h.isMounted() && m.busy() && h.busy())
                    {
                        seen[0] = "the moose " + m.current().name() + ", him " + h.current().name();
                        return;
                    }
                    throw new GameTestAssertException("waiting for both of them to be in a move at once (the moose plays "
                            + m.current().name() + ", he plays " + h.current().name() + ", stage " + m.stage() + ")");
                })
                .thenExecute(() ->
                {
                    zombie.discard();
                    clear(h, m);
                    com.mojang.logging.LogUtils.getLogger().info("[huntsman test] both in a move at once: {}", seen[0]);
                })
                .thenSucceed();
    }

    /** Removes the hostile mobs (but theirs and {@code keep}) near {@code near}. */
    private static void clearFoesNearBut(ServerLevel level, HuntMob near, Mob keep)
    {
        double r = HuntsmanTuning.BREAK_OFF_RANGE + 16.0D;
        for (Mob mob : level.getEntitiesOfClass(Mob.class, near.getBoundingBox().inflate(r),
                e -> e instanceof Enemy && !(e instanceof HuntMob) && e != keep))
        {
            mob.discard();
        }
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
                    // (a wild monster wandering in becomes its target in the zombie's place: a creeper did, three runs)
                    for (Mob stray : level.getEntitiesOfClass(Mob.class, m.getBoundingBox().inflate(48.0D),
                            e -> e instanceof Enemy && e != zombie))
                    {
                        stray.discard();
                    }
                    if (m.getTarget() != zombie && m.getTarget() != null && !m.getTarget().isAlive())
                    {
                        m.setTarget(zombie);
                    }
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
        // (he hunts any hostile mob near: one left over from an earlier test would rightly keep the fight going)
        helper.onEachTick(() -> clearFoesNear(level, h));
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
                    problem = "with nobody near, the fight should be broken off: a new Alpha Moose under him (his target "
                            + h.getTarget() + ", stage " + h.stage() + ", lonely for " + h.lonelyTicks + " ticks)";
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

    /** Calm, the moose of a village rides its patrol round, point after point (a small square loop here). In a batch
     * of its own: a foe from another test near it starts a fight, and a fighting pair doesn't patrol. */
    @GameTest(template = ARENA, batch = "dross_huntsman_patrol", timeoutTicks = WAIT + 2400)
    public static void thePatrolGoesRound(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::thePatrolGoesRound))
        {
            return;
        }
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        List<BlockPos> loop = List.of(helper.absolutePos(MIDDLE.offset(-5, 0, -5)), helper.absolutePos(MIDDLE.offset(5, 0, -5)),
                helper.absolutePos(MIDDLE.offset(5, 0, 5)), helper.absolutePos(MIDDLE.offset(-5, 0, 5)));
        m.setPatrol(loop, 0);
        boolean[] reached = new boolean[loop.size()];
        helper.startSequence()
                .thenWaitUntil(() ->
                {
                    int count = 0;
                    for (int i = 0; i < loop.size(); i++)
                    {
                        BlockPos p = loop.get(i);
                        reached[i] |= Math.hypot(m.getX() - (p.getX() + 0.5D), m.getZ() - (p.getZ() + 0.5D)) < 6.5D;
                        count += reached[i] ? 1 : 0;
                    }
                    if (count < loop.size())
                    {
                        throw new GameTestAssertException("waiting for the moose to ride round its patrol (" + count + " of "
                                + loop.size() + " points reached; it's at " + m.blockPosition().toShortString() + ", stage "
                                + m.stage() + ", playing " + m.current().name() + ")");
                    }
                })
                .thenExecute(() -> clear(h, m))
                .thenSucceed();
    }

    // ---------------------------------------------------------------- a village pair's chase

    /** A square loop round a spot {@code out} blocks west of the arena's middle, as a village's patrol. */
    private static List<BlockPos> loopWestOf(GameTestHelper helper, int out)
    {
        BlockPos c = helper.absolutePos(MIDDLE).offset(-out, 0, 0);
        return List.of(c.offset(-4, 0, -4), c.offset(4, 0, -4), c.offset(4, 0, 4), c.offset(-4, 0, 4));
    }

    /** The chase's leash: how far past its loop a spot is (0 inside it); a pair far past its loop with its foe further
     * out is held there and goes no further; its foe back nearer the loop, it isn't held. */
    @GameTest(template = ARENA, timeoutTicks = WAIT + 400)
    public static void theChaseHasALeash(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::theChaseHasALeash))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE.offset(2, 0, 0), -90.0F);
        AlphaMoose m = moose(h);
        List<BlockPos> loop = loopWestOf(helper, 90);
        h.setPatrol(loop, 0);
        m.setPatrol(loop, 0);
        BlockPos c = helper.absolutePos(MIDDLE).offset(-90, 0, 0);
        double inside = HuntFight.pastLoop(m, Vec3.atBottomCenterOf(c));
        double ten = HuntFight.pastLoop(m, Vec3.atBottomCenterOf(c.offset(14, 0, 0)));
        if (inside != 0.0D || Math.abs(ten - 10.0D) > 0.01D)
        {
            clear(h, m);
            throw new GameTestAssertException("the leash's distance: inside the loop " + inside + " (should be 0), 10 past it "
                    + ten);
        }
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, "hunt-leashed", MIDDLE.offset(7, 0, 0), 90.0F);
        player.setGameMode(GameType.SURVIVAL);
        hit(m, level.damageSources().playerAttack(player), 1.0F);       // the hunt is on
        double startX = m.getX();
        double[] furthest = {startX};
        boolean[] everHeld = {false};
        helper.startSequence()
                .thenExecuteFor(100, () ->
                {
                    furthest[0] = Math.max(furthest[0], m.getX());
                    everHeld[0] |= m.held;
                })
                .thenExecute(() ->
                {
                    String problem = null;
                    if (!everHeld[0] || !m.held)
                    {
                        problem = "far past its loop with its foe further out, the moose should be held there";
                    }
                    else if (furthest[0] - startX > 2.0D)
                    {
                        problem = String.format("held at its leash, the moose went %.1f blocks further out", furthest[0] - startX);
                    }
                    if (problem != null)
                    {
                        clear(h, m);
                        ColossusGameTest.leaveAll(List.of(player));
                        throw new GameTestAssertException(problem);
                    }
                    Vec3 back = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(-6, 0, 0)));
                    player.teleportTo(back.x, back.y, back.z);
                })
                .thenIdle(5)
                .thenExecute(() ->
                {
                    boolean held = m.held;
                    clear(h, m);
                    ColossusGameTest.leaveAll(List.of(player));
                    if (held)
                    {
                        throw new GameTestAssertException("its foe nearer the loop than it, the moose shouldn't be held");
                    }
                })
                .thenSucceed();
    }

    /** A village pair whose foe gets away (out of Rimefall and outside the loop here: the test's world isn't the Dross)
     * lets them go: they don't go for that player, and they stay where they are, still fighting and still hurt (until the
     * break-off heals them; the user, October 24, 2026: no vanishing). */
    @GameTest(template = ARENA, timeoutTicks = WAIT + 400)
    public static void aFoeWhoGetsAwayIsLetGo(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::aFoeWhoGetsAwayIsLetGo))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, -90.0F);
        AlphaMoose m = moose(h);
        List<BlockPos> loop = loopWestOf(helper, 90);
        for (HuntMob mob : new HuntMob[] {h, m})
        {
            mob.setPatrol(loop, 0);
            mob.setHome(4_242L, helper.absolutePos(MIDDLE), 1);
        }
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, "hunt-escaper", MIDDLE.offset(6, 0, 0), 90.0F);
        player.setGameMode(GameType.SURVIVAL);
        hit(m, level.damageSources().playerAttack(player), HIT);
        boolean[] targeted = {false};
        helper.startSequence()
                .thenExecuteFor(80, () -> targeted[0] |= m.getTarget() == player || h.getTarget() == player)
                .thenExecute(() ->
                {
                    String problem = null;
                    if (!HuntFight.gotAway(m, player))
                    {
                        problem = "out of Rimefall and outside the loop, the player should count as got away";
                    }
                    else if (targeted[0])
                    {
                        problem = "a player who got away should be let go, not chased";
                    }
                    else if (m.isRemoved() || h.isRemoved() || m.stage() != HuntStage.WILD_HUNT)
                    {
                        problem = "they should stay there, still in the fight (stage " + m.stage() + ", removed "
                                + m.isRemoved() + ")";
                    }
                    else if (m.wildHunt >= HuntsmanTuning.WILD_HUNT_HEALTH)
                    {
                        problem = "they shouldn't heal before the break-off";
                    }
                    clear(h, m);
                    ColossusGameTest.leaveAll(List.of(player));
                    if (problem != null)
                    {
                        throw new GameTestAssertException(problem);
                    }
                })
                .thenSucceed();
    }

    /** He hunts Rimefall's grown animals (and hostile mobs), never their calves or a hunter village's penned ones. */
    @GameTest(template = ARENA, timeoutTicks = WAIT + 100)
    public static void heHuntsGrownAnimals(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::heHuntsGrownAnimals))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Huntsman h = pair(helper, MIDDLE, 0.0F);
        AlphaMoose m = moose(h);
        Rimehorn grown = ModEntities.RIMEHORN.get().create(level);
        Rimehorn calf = ModEntities.RIMEHORN.get().create(level);
        Rimehorn penned = ModEntities.RIMEHORN.get().create(level);
        Moose bull = ModEntities.MOOSE.get().create(level);
        Moose mooseCalf = ModEntities.MOOSE.get().create(level);
        Moose pennedMoose = ModEntities.MOOSE.get().create(level);
        if (grown == null || calf == null || penned == null || bull == null || mooseCalf == null || pennedMoose == null)
        {
            clear(h, m);
            throw new GameTestAssertException("couldn't make the animals");
        }
        calf.setBaby(true);
        penned.setPenned(true);
        mooseCalf.setBaby(true);
        pennedMoose.setPenned(true);
        String problem = null;
        if (!HuntFight.isFoe(h, grown) || !HuntFight.isFoe(h, bull))
        {
            problem = "he should hunt a grown Rimehorn and a grown moose";
        }
        else if (HuntFight.isFoe(h, calf) || HuntFight.isFoe(h, mooseCalf))
        {
            problem = "he should never hunt a calf";
        }
        else if (HuntFight.isFoe(h, penned) || HuntFight.isFoe(h, pennedMoose))
        {
            problem = "he should never hunt a hunter village's penned animals";
        }
        clear(h, m);
        if (problem != null)
        {
            throw new GameTestAssertException(problem);
        }
        helper.succeed();
    }

    /** His arrows from the saddle hit a target 12 blocks off, standing or sprinting across his line, in each of the four
     * directions (each with its own shot: ahead, left, right, behind; HuntProbeGameTest#shots, on a floor in the sky). */
    @GameTest(template = ARENA, timeoutTicks = WAIT + 4000)
    public static void saddleShotsHit(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, HuntsmanFightGameTest::saddleShotsHit))
        {
            return;
        }
        HuntProbeGameTest.shots(helper, new int[] {12}, 1, 0.9D);
    }
}
