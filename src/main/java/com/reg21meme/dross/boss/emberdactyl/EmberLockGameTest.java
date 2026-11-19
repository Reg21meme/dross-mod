package com.reg21meme.dross.boss.emberdactyl;

import com.mojang.logging.LogUtils;
import com.reg21meme.dross.boss.BossLockGameTest;
import com.reg21meme.dross.boss.BossWards;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless checks of the Emberdactyl's lock and its crater's ward ({@code boss.BossLocks}, {@link CraterWard}):
 * <ul>
 *   <li>{@link #homeEmberdactylSleepsThroughALockedPlayer}: one of a world volcano rests on with a survival player near
 *       whose Oracle quest hasn't reached it, and their blow doesn't hurt or wake it; once their quest reaches it ("Wings
 *       Clipped", its offer heard), it wakes for them.</li>
 *   <li>{@link #craterWardShape}: the lobes worked out from the seed alone match the volcano's own plan; the ward (since
 *       November 9, 2026 the whole volcano from its foot) holds the nest, the lookout and the Emberdactyl's ledge deep
 *       inside, a spot on the path just past the foot a push from its edge, not the path's foot; nothing far over the rim
 *       or deep under the ground; its blocks protected (the nest, the path's foot), not the land well past it.</li>
 * </ul>
 */
@GameTestHolder(BossLockGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
public final class EmberLockGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();
    /** A made-up volcano id: not 0, so it counts as a world volcano's (none is built with it). */
    private static final long FAKE_VOLCANO = 8_400_680_000_777L;
    /** Ticks a locked player stands near it to see it rests on (many of its looks, every 10 ticks). */
    private static final int QUIET_TICKS = 80;
    /** Seeds whose volcanoes' crests are checked. */
    private static final long[] SEEDS = {12_345L, -987_654_321_123L, 0x5EED_0F_A5L};
    /** Angles round each crest checked. */
    private static final int ANGLES = 24;

    private EmberLockGameTest() {}

    @GameTest(template = BossLockGameTest.EMPTY, batch = "dross_locks_ember", timeoutTicks = 2_000_000)
    public static void homeEmberdactylSleepsThroughALockedPlayer(GameTestHelper helper)
    {
        EmberFightGameTest.Run run = new EmberFightGameTest.Run(helper.getLevel(), 8400, -6800);
        run.watch(helper);
        Emberdactyl[] adult = new Emberdactyl[1];
        helper.startSequence()
                .thenWaitUntil(() -> run.guard(run::ready))
                .thenExecute(() -> run.guard(() -> {
                    run.floor();
                    adult[0] = run.spawn(ModEntities.EMBERDACTYL.get(), run.centre.add(0.0D, 8.0D, 0.0D));
                    adult[0].setHome(run.centre, null, null, 0.0F, FAKE_VOLCANO);
                    if (adult[0].lockedAs() != OracleBoss.EMBERDACTYL)
                    {
                        throw new GameTestAssertException("one of a world volcano should be locked behind its quest");
                    }
                    run.join("ember-lock-test", run.centre.add(30.0D, 0.0D, 0.0D));
                }))
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> run.guard(() -> {
                    if (adult[0].isFighting())
                    {
                        throw new GameTestAssertException("it woke for a player it's locked for");
                    }
                    adult[0].invulnerableTime = 0;
                    adult[0].hurt(run.player.damageSources().playerAttack(run.player), 20.0F);
                    if (adult[0].getHealth() < adult[0].getMaxHealth() || adult[0].isFighting())
                    {
                        throw new GameTestAssertException("a locked player's blow hurt or woke it (health " + adult[0].getHealth()
                                + ", fighting " + adult[0].isFighting() + ")");
                    }
                    BossLockGameTest.allow(run.player, OracleBoss.EMBERDACTYL);
                }))
                .thenWaitUntil(() -> run.waitFor("it to wake for a player whose quest has reached it", () -> adult[0].isFighting(),
                        () -> "fighting " + adult[0].isFighting()))
                .thenExecute(() -> run.guard(() -> {
                    LOGGER.info("[ember lock test] it rested on beside a locked player and woke for an allowed one");
                    run.cleanUp();
                }))
                .thenSucceed();
    }

    @GameTest(template = BossLockGameTest.EMPTY, batch = "dross_locks_crater", timeoutTicks = 400)
    public static void craterWardShape(GameTestHelper helper)
    {
        for (long seed : SEEDS)
        {
            EmberVolcano.Plan plan = EmberVolcano.planOnLand(1000, -2000, (x, z) -> 64, seed);
            double[] phases = EmberVolcano.lobePhases(seed);
            for (int i = 0; i < ANGLES; i++)
            {
                double angle = -Math.PI + 2.0D * Math.PI * i / ANGLES;
                double own = plan.lobesAt(angle);
                double fromSeed = EmberVolcano.lobes(phases, angle);
                if (Math.abs(own - fromSeed) > 1.0E-9D)
                {
                    throw new GameTestAssertException("seed " + seed + ": the crest's lobes from the seed (" + fromSeed
                            + ") aren't its plan's (" + own + ") at angle " + angle);
                }
            }
            EmberVolcanoData.Volcano volcano = new EmberVolcanoData.Volcano(seed, 0, plan.ground(), plan.rimY(), plan.lakeY(),
                    plan.eggFeet(), plan.pathFoot(), plan.pathFootYaw(), plan.lookout(), plan.lookoutYaw(), plan.perch(),
                    plan.perchYaw(), 0L);
            CraterWard ward = CraterWard.of(volcano, seed);
            double egg = ward.depth(volcano.egg());
            double lookout = ward.depth(volcano.lookout());
            double foot = ward.depth(volcano.pathFoot());
            String where = "seed " + seed + ": ";
            if (egg <= BossWards.PUSH_DEPTH)
            {
                throw new GameTestAssertException(where + "the nest should be deep in the volcano's ward (" + egg + ")");
            }
            if (lookout <= BossWards.PUSH_DEPTH)
            {
                throw new GameTestAssertException(where + "the lookout should be deep in the volcano's ward (" + lookout + ")");
            }
            if (foot > 0.0D)
            {
                throw new GameTestAssertException(where + "the path's foot should be outside the ward (" + foot + ")");
            }
            if (volcano.perch() != null && ward.depth(volcano.perch()) <= BossWards.PUSH_DEPTH)
            {
                throw new GameTestAssertException(where + "the Emberdactyl's ledge should be deep in the ward (" + ward.depth(volcano.perch()) + ")");
            }
            // a spot on the path just up from its foot: a push from the edge, away from the middle
            double mx = volcano.ground().getX() + 0.5D;
            double mz = volcano.ground().getZ() + 0.5D;
            double share = (EmberVolcanoTuning.BASE_RADIUS - 2.0D) / (EmberVolcanoTuning.BASE_RADIUS + EmberVolcanoTuning.PATH_START_OUT);
            Vec3 onPath = new Vec3(mx + (volcano.pathFoot().x - mx) * share, volcano.pathFoot().y + 1.0D, mz + (volcano.pathFoot().z - mz) * share);
            double justIn = ward.depth(onPath);
            if (justIn <= 0.0D || justIn > BossWards.PUSH_DEPTH)
            {
                throw new GameTestAssertException(where + "a spot on the path just past the foot should be a push from the ward's edge (" + justIn + ")");
            }
            Vec3 out = ward.outward(onPath);
            Vec3 toMiddle = new Vec3(mx - onPath.x, 0.0D, mz - onPath.z);
            if (out.dot(toMiddle) >= 0.0D)
            {
                throw new GameTestAssertException(where + "the push on the path should be away from the volcano's middle");
            }
            Vec3 high = new Vec3(volcano.egg().x, volcano.rimY() + CraterWard.ABOVE_RIM + 5.0D, volcano.egg().z);
            if (ward.depth(high) > 0.0D)
            {
                throw new GameTestAssertException(where + "the ward reaches too high over the volcano");
            }
            Vec3 under = new Vec3(onPath.x, volcano.ground().getY() - CraterWard.BELOW_GROUND - 5.0D, onPath.z);
            if (ward.depth(under) > 0.0D)
            {
                throw new GameTestAssertException(where + "the ward reaches too deep under the volcano");
            }
            if (!ward.covers(BlockPos.containing(volcano.egg())) || !ward.covers(BlockPos.containing(volcano.pathFoot()).below()))
            {
                throw new GameTestAssertException(where + "the nest and the ground at the path's foot should be protected");
            }
            Vec3 farOut = new Vec3(mx + (volcano.pathFoot().x - mx) * 2.0D, volcano.pathFoot().y, mz + (volcano.pathFoot().z - mz) * 2.0D);
            if (ward.covers(BlockPos.containing(farOut)))
            {
                throw new GameTestAssertException(where + "the land well past the volcano's foot shouldn't be protected");
            }
            LOGGER.info("[ember lock test] seed {}: the nest {} deep in the ward, the lookout {}, the path's foot {}, just up the path {}",
                    seed, String.format("%.1f", egg), String.format("%.1f", lookout), String.format("%.1f", foot),
                    String.format("%.1f", justIn));
        }
        helper.succeed();
    }
}
