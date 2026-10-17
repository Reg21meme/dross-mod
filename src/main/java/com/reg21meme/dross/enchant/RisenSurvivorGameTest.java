package com.reg21meme.dross.enchant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEnchantments;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.villager.Survivor;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Headless tests (GameTests) for Necromancy and the hub's two survivors in the Dross (the Blind Oracle and the Ragged
 * Seeker, {@link Survivor}). Run them with {@code .\gradlew.bat runGameTestServer --console=plain}, or in a dev world
 * with {@code /test runall}. They only exist in the development environment.
 *
 * <ul>
 *   <li>{@link #necromancyHitOnASurvivorRaisesNothing}: with Necromancy V (every hit raises), hitting a survivor raises
 *       nothing, while the same sword on a pig does (so the test can tell the two apart).</li>
 *   <li>{@link #risenNeverTargetOrHurtASurvivor}: a survivor is never a valid target for a risen undead; a risen
 *       zombie's blow, a risen skeleton's arrow and an arrow whose skeleton has crumbled do it no harm; and after the
 *       owner hits it (and after it "hurt" one of them), the pack still never goes after it, watched for
 *       {@link #WATCH_TICKS} ticks.</li>
 * </ul>
 * Hostile mobs already can't set a survivor as their target (the villager area's {@code SurvivorEvents} cancels it),
 * but without the survivor rule in {@link RisenUndead} the pack AI would still pick one, then stay stuck on a target
 * it can never set; the watch catches that. Each test collects every problem it finds and reports them all at the end.
 *
 * <p>Both use {@link RisenTargetGameTest}'s closed arena and stand-in owner (in creative mode, so the monsters of the
 * test server's survival world leave him alone). They wait until the arena has finished loading, in real time (far
 * from spawn a test can start before its chunks are ready, and nothing in them moves until then), the watch counts the
 * risen undead's own ticks ({@link RisenTestWait}), and each test has its own batch, like the other risen undead tests.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RisenSurvivorGameTest
{
    // ---------------------------------------------------------------------------------------------
    // Test setup (20 ticks = 1 second).
    // ---------------------------------------------------------------------------------------------

    /** Height (relative to the test) the mobs stand at: on the arena's floor. */
    private static final int STAND_Y = RisenTargetGameTest.STAND_Y;
    /** Where the owner stands; the risen undead rise up to 3 blocks from him. */
    private static final BlockPos OWNER_POS = new BlockPos(7, STAND_Y, 4);
    /** Where the survivor stands: 6 blocks from the owner. */
    private static final BlockPos SURVIVOR_POS = new BlockPos(7, STAND_Y, 10);
    /** Where the first test's pig stands, away from the survivor. */
    private static final BlockPos PIG_POS = new BlockPos(12, STAND_Y, 10);

    /** Necromancy V: a 100% raise chance and infinite souls, so any hit that may raise undead does. */
    private static final int NECROMANCY_LEVEL = NecromancyEnchantment.MAX_LEVEL;
    /** Deathforged I: a leather helmet, so the risen undead don't burn in daylight during the test. */
    private static final int DEATHFORGED_LEVEL = 1;
    /** Damage of the test's blows and arrows at the survivor (any amount: none of them may land). */
    private static final float TEST_HIT_DAMAGE = 4.0F;

    /**
     * How long the pack is watched after the owner hits the survivor, in the risen undead's own ticks: 2 seconds of
     * their life. That's about 20 target decisions (one every other tick) and 4 pack searches (every
     * {@link RisenTargetGoal#SEARCH_INTERVAL_TICKS}), all inside the {@link RisenTargetGoal#OWNER_TARGET_MEMORY_TICKS}
     * the owner's hit counts as "the owner's target". Counting their own ticks means the watch can't end before their
     * AI has really run.
     */
    private static final int WATCH_TICKS = 40;
    /** Each test's own tick limit: only a backstop behind its waits (see {@link RisenTestWait}). */
    private static final int TIMEOUT_TICKS = RisenTestWait.TEST_TIMEOUT_TICKS;

    private RisenSurvivorGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------

    /**
     * An owner holding a Necromancy V sword (a sure raise) hits a survivor: nothing may rise. Then a second owner, with
     * the same sword and none of his own alive, hits a pig: they must rise, otherwise the first check proved nothing
     * (Peaceful, a broken sword...). Two owners, so the survivor's hit always has room to raise (Necromancy V raises
     * its full 10 when none are alive, and then has no room left).
     */
    @GameTest(template = RisenTargetGameTest.ARENA, batch = "dross_risen_survivor_hit", timeoutTicks = TIMEOUT_TICKS)
    public static void necromancyHitOnASurvivorRaisesNothing(GameTestHelper helper)
    {
        Scene scene = new Scene();
        startWhenArenaLoaded(helper, scene, () -> {
            Survivor survivor = scene.spawnSurvivor(helper);
            Pig pig = helper.spawnWithNoFreeWill(EntityType.PIG, PIG_POS);
            scene.others.add(pig);

            // A survivor: nothing may rise.
            ServerPlayer owner = scene.newOwner(helper);
            owner.setItemInHand(InteractionHand.MAIN_HAND, necromancySword());
            hit(owner, survivor);
            int fromSurvivor = RisenUndead.countAlive(owner.getUUID());
            if (fromSurvivor != 0)
            {
                scene.problems.add("hitting a survivor with Necromancy " + NECROMANCY_LEVEL + " raised " + fromSurvivor + " undead");
            }

            // A pig: they must rise.
            owner = scene.newOwner(helper);
            owner.setItemInHand(InteractionHand.MAIN_HAND, necromancySword());
            hit(owner, pig);
            if (RisenUndead.countAlive(owner.getUUID()) == 0)
            {
                scene.problems.add("the same sword raised nothing from a pig either, so this test can't tell (is the test world on Peaceful?)");
            }

            scene.cleanUp();
            scene.failIfProblems();
        })
                .thenSucceed();
    }

    /**
     * A risen zombie and a risen skeleton next to a survivor. At once: the survivor must not be a valid target for
     * either, and their blow, the skeleton's arrow and an arrow whose skeleton has crumbled must do it no harm. Then the
     * owner hits the survivor (so it's "the owner's target") and the zombie is told the survivor hurt it ("fight
     * back"). For {@link #WATCH_TICKS} of their own ticks neither may target the survivor, neither's pack AI may be busy
     * (there's nothing else here to fight), and the survivor may not lose health.
     */
    @GameTest(template = RisenTargetGameTest.ARENA, batch = "dross_risen_survivor_safe", timeoutTicks = TIMEOUT_TICKS)
    public static void risenNeverTargetOrHurtASurvivor(GameTestHelper helper)
    {
        Scene scene = new Scene();
        RisenTestWait watch = new RisenTestWait(helper, RisenTestWait.AI_MILLIS);
        startWhenArenaLoaded(helper, scene, () -> {
            Survivor survivor = scene.spawnSurvivor(helper);
            ServerPlayer owner = scene.newOwner(helper);
            Mob zombie = DeathforgedGearGameTest.raiseOne(owner, RisenType.ZOMBIE, DEATHFORGED_LEVEL);
            Mob skeleton = DeathforgedGearGameTest.raiseOne(owner, RisenType.SKELETON, DEATHFORGED_LEVEL);
            if (zombie == null || skeleton == null)
            {
                throw new GameTestAssertException("the risen undead couldn't be spawned");
            }
            scene.risen.add(zombie);
            scene.risen.add(skeleton);

            // Never a valid target.
            for (Mob mob : scene.risen)
            {
                if (RisenUndead.isValidTarget(mob, survivor))
                {
                    scene.problems.add("a survivor is a valid target for a risen " + RisenTargetGameTest.name(mob));
                }
            }

            // Their blows and arrows do it no harm. An arrow outlives the skeleton that shot it, so it carries
            // the owner's id itself (set when it's shot; set by hand here, since this one is never shot).
            ServerLevel level = helper.getLevel();
            Arrow shot = new Arrow(level, skeleton);
            Arrow leftOver = new Arrow(EntityType.ARROW, level);
            leftOver.getPersistentData().putUUID(RisenUndead.PROJECTILE_OWNER_KEY, owner.getUUID());
            scene.expectNoHarm(survivor.damageSources().mobAttack(zombie), "a risen zombie's blow");
            scene.expectNoHarm(survivor.damageSources().arrow(shot, skeleton), "a risen skeleton's arrow");
            scene.expectNoHarm(survivor.damageSources().arrow(leftOver, null), "an arrow whose risen skeleton has crumbled");

            // The owner hits it, and it "hurt" the zombie: watched below.
            hit(owner, survivor);
            zombie.setLastHurtByMob(survivor);
            scene.healthBeforeWatch = survivor.getHealth();
        })
                .thenWaitUntil(scene.guard.poll(watch, scene::watch))
                .thenExecute(scene.guard.step(() -> {
                    if (watch.problem() != null)
                    {
                        scene.problems.add(watch.problem());
                    }
                    scene.cleanUp();
                    scene.failIfProblems();
                }))
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /**
     * The owner's swing at {@code target}, as Necromancy and the pack AI see it: the attack event (Necromancy acts there,
     * before any damage), then the target becomes the mob he last hit.
     */
    private static void hit(ServerPlayer owner, LivingEntity target)
    {
        MinecraftForge.EVENT_BUS.post(new AttackEntityEvent(owner, target));
        owner.setLastHurtMob(target);
    }

    /** An iron sword with Necromancy V and Deathforged I. */
    private static ItemStack necromancySword()
    {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(ModEnchantments.NECROMANCY.get(), NECROMANCY_LEVEL);
        sword.enchant(ModEnchantments.DEATHFORGED.get(), DEATHFORGED_LEVEL);
        return sword;
    }

    /**
     * The start of a test: wait until the arena has finished loading (in real time, {@link RisenTestWait}), then one
     * step that checks it did and runs {@code firstStep}. Nothing is added to the world before that. Every step and
     * wait goes through the scene's guard, so the test reports its first failure, cleaned up.
     */
    private static GameTestSequence startWhenArenaLoaded(GameTestHelper helper, Scene scene, Runnable firstStep)
    {
        RisenTestWait arena = new RisenTestWait(helper, RisenTestWait.ARENA_MILLIS);
        return helper.startSequence()
                .thenWaitUntil(scene.guard.poll(arena, RisenTestWait.arenaLoaded(helper, RisenTargetGameTest.ARENA_SIZE, STAND_Y)))
                .thenExecute(scene.guard.step(() -> {
                    arena.failIfWrong("");
                    firstStep.run();
                }));
    }

    /** What a test works with (filled in once its arena has loaded), and the problems it found. */
    private static final class Scene
    {
        @Nullable
        ServerPlayer owner;
        @Nullable
        Survivor survivor;
        /** The risen undead being watched. */
        final List<Mob> risen = new ArrayList<>();
        /** Other test mobs, removed at the end. */
        final List<Mob> others = new ArrayList<>();
        final List<String> problems = new ArrayList<>();
        /** The survivor's health when the watch starts (it must not drop during the watch). */
        float healthBeforeWatch;
        /** Each risen undead's own tick count when the watch began (null until it begins). */
        @Nullable
        Map<Mob, Integer> watchStart;
        /** Every step and wait goes through it: the test's first failure is the one it reports, cleaned up. */
        final RisenTestWait.Guard guard = new RisenTestWait.Guard(this::cleanUp);

        /** A survivor with no AI of its own (so it stays put), standing on the arena's floor. */
        Survivor spawnSurvivor(GameTestHelper helper)
        {
            Survivor spawned = helper.spawnWithNoFreeWill(ModEntities.SURVIVOR.get(), SURVIVOR_POS);
            this.survivor = spawned;
            return spawned;
        }

        /**
         * A fresh stand-in owner ({@link RisenTargetGameTest#addOwner}, in creative mode), standing on the arena's floor
         * at this test's own spot. Any earlier owner leaves first; his risen undead crumble.
         */
        ServerPlayer newOwner(GameTestHelper helper)
        {
            RisenTargetGameTest.removeOwner(this.owner);
            ServerPlayer player = RisenTargetGameTest.addOwner(helper);
            this.owner = player;
            Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(OWNER_POS));
            player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            return player;
        }

        /**
         * A hit from this source must do the survivor no harm: refused, and its health unchanged. Its hurt cooldown is
         * reset first, so an earlier hit that wrongly landed can't hide this one.
         */
        void expectNoHarm(DamageSource source, String what)
        {
            Survivor victim = this.survivor;
            victim.invulnerableTime = 0;
            float before = victim.getHealth();
            boolean landed = victim.hurt(source, TEST_HIT_DAMAGE);
            if (landed || victim.getHealth() < before)
            {
                this.problems.add(what + " hurt a survivor (health " + before + " -> " + victim.getHealth() + ")");
            }
        }

        /**
         * One tick of the watch, for its {@link RisenTestWait}: it ends at the first thing that goes wrong (and says
         * when), or once both risen undead have lived {@link #WATCH_TICKS} of their own ticks since it began.
         */
        RisenTestWait.Look watch()
        {
            if (this.watchStart == null)
            {
                this.watchStart = RisenTestWait.mark(this.risen);
            }
            int watched = RisenTestWait.fewestTicksSince(this.watchStart);
            String problem = this.findProblem();
            if (problem != null)
            {
                return RisenTestWait.Look.failed("after " + watched + " of their ticks of the watch, " + problem);
            }
            return watched < WATCH_TICKS
                    ? RisenTestWait.Look.waiting("the risen undead have been watched for " + watched + " of their " + WATCH_TICKS + " ticks")
                    : RisenTestWait.Look.DONE;
        }

        /** What's wrong right now, or null. */
        @Nullable
        private String findProblem()
        {
            Survivor victim = this.survivor;
            if (victim.getHealth() < this.healthBeforeWatch)
            {
                return "the survivor was hurt (health " + this.healthBeforeWatch + " -> " + victim.getHealth() + ")";
            }
            for (Mob mob : this.risen)
            {
                String name = RisenTargetGameTest.name(mob);
                if (!mob.isAlive())
                {
                    return "a risen " + name + " is gone, so it can't be watched";
                }
                if (mob.getTarget() == victim)
                {
                    return "a risen " + name + " targets the survivor";
                }
                if (mob.targetSelector.getRunningGoals().anyMatch(goal -> goal.getGoal() instanceof RisenTargetGoal))
                {
                    LivingEntity target = mob.getTarget();
                    return "a risen " + name + "'s pack AI is busy with a fight (its target: "
                            + (target == null ? "none" : RisenTargetGameTest.name(target)) + "), but the only other mob here is the survivor";
                }
            }
            return null;
        }

        /** The owner leaves (his risen undead crumble) and the test's other mobs are removed. Safe to call twice. */
        void cleanUp()
        {
            RisenTargetGameTest.removeOwner(this.owner);
            if (this.survivor != null)
            {
                this.survivor.discard();
            }
            this.others.forEach(Mob::discard);
        }

        void failIfProblems()
        {
            if (!this.problems.isEmpty())
            {
                throw new GameTestAssertException(this.problems.size() + " problem(s): " + String.join("; ", this.problems));
            }
        }
    }
}
