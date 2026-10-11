package com.reg21meme.dross.enchant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEnchantments;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.villager.Survivor;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
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
import net.minecraft.world.level.ChunkPos;
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
 * <p>Both use {@link RisenTargetGameTest}'s arena and stand-in owner. They wait until the arena has finished loading
 * (far from spawn a test can start before its chunks are ready, and nothing in them moves until then), and each has
 * its own batch, like the other risen undead tests.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RisenSurvivorGameTest
{
    // ---------------------------------------------------------------------------------------------
    // Test setup (20 ticks = 1 second).
    // ---------------------------------------------------------------------------------------------

    /**
     * Height (relative to the test) the mobs stand at. The test's structure block is at relative y 0 and the arena's
     * floor (the template's bottom layer) at relative y 1, so y 2 is standing on the floor.
     */
    private static final int STAND_Y = 2;
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
     * How long the pack is watched after the owner hits the survivor: 2 seconds. That's about 20 target decisions (one
     * every other tick) and 4 pack searches (every {@link RisenTargetGoal#SEARCH_INTERVAL_TICKS}), all inside the
     * {@link RisenTargetGoal#OWNER_TARGET_MEMORY_TICKS} the owner's hit counts as "the owner's target".
     */
    private static final int WATCH_TICKS = 40;
    /** After the watch, each risen undead must have ticked at least this often, or its AI never really ran. */
    private static final int MIN_TICKS_LIVED = WATCH_TICKS / 2;
    /** How long the arena may take to finish loading. */
    private static final int ARENA_WAIT_TICKS = 200;
    private static final int TIMEOUT_TICKS = ARENA_WAIT_TICKS + WATCH_TICKS + 60;

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
        helper.startSequence()
                .thenWaitUntil(() -> waitForArena(helper))
                .thenExecute(step(scene, () -> {
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
                }))
                .thenSucceed();
    }

    /**
     * A risen zombie and a risen skeleton next to a survivor. At once: the survivor must not be a valid target for
     * either, and their blow, the skeleton's arrow and an arrow whose skeleton has crumbled must do it no harm. Then the
     * owner hits the survivor (so it's "the owner's target") and the zombie is told the survivor hurt it ("fight
     * back"). For {@link #WATCH_TICKS} ticks neither may target the survivor, neither's pack AI may be busy (there's
     * nothing else here to fight), and the survivor may not lose health. Last, both must really have ticked, so the
     * watch meant something.
     */
    @GameTest(template = RisenTargetGameTest.ARENA, batch = "dross_risen_survivor_safe", timeoutTicks = TIMEOUT_TICKS)
    public static void risenNeverTargetOrHurtASurvivor(GameTestHelper helper)
    {
        Scene scene = new Scene();
        helper.startSequence()
                .thenWaitUntil(() -> waitForArena(helper))
                .thenExecute(step(scene, () -> {
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
                }))
                .thenExecuteFor(WATCH_TICKS, step(scene, scene::watch))
                .thenExecute(step(scene, () -> {
                    for (Mob mob : scene.risen)
                    {
                        if (mob.tickCount < MIN_TICKS_LIVED)
                        {
                            scene.problems.add("a risen " + RisenTargetGameTest.name(mob) + " only ticked " + mob.tickCount
                                    + " times, so its AI never really ran and the watch proved nothing");
                        }
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

    /** An iron sword with Necromancy V and Deathforged I, in the five-level numbering (so it's never moved up a level). */
    private static ItemStack necromancySword()
    {
        ItemStack sword = new ItemStack(Items.IRON_SWORD);
        sword.enchant(ModEnchantments.NECROMANCY.get(), NECROMANCY_LEVEL);
        sword.enchant(ModEnchantments.DEATHFORGED.get(), DEATHFORGED_LEVEL);
        NecromancyScheme.mark(sword);
        return sword;
    }

    /**
     * Throws (so the sequence keeps waiting) until every chunk under the arena has loaded its mobs and ticks them. Until
     * then nothing has been added to the test, so a time-out leaves nothing behind.
     */
    private static void waitForArena(GameTestHelper helper)
    {
        ServerLevel level = helper.getLevel();
        int far = RisenTargetGameTest.ARENA_SIZE - 1;
        BlockPos corner = helper.absolutePos(new BlockPos(0, STAND_Y, 0));
        BlockPos opposite = helper.absolutePos(new BlockPos(far, STAND_Y, far));
        int minX = SectionPos.blockToSectionCoord(Math.min(corner.getX(), opposite.getX()));
        int maxX = SectionPos.blockToSectionCoord(Math.max(corner.getX(), opposite.getX()));
        int minZ = SectionPos.blockToSectionCoord(Math.min(corner.getZ(), opposite.getZ()));
        int maxZ = SectionPos.blockToSectionCoord(Math.max(corner.getZ(), opposite.getZ()));
        for (int x = minX; x <= maxX; x++)
        {
            for (int z = minZ; z <= maxZ; z++)
            {
                ChunkPos chunk = new ChunkPos(x, z);
                if (!level.areEntitiesLoaded(chunk.toLong()) || !level.isPositionEntityTicking(chunk.getWorldPosition()))
                {
                    throw new GameTestAssertException("the arena's chunk " + x + ", " + z + " hasn't finished loading");
                }
            }
        }
    }

    /**
     * One step of a test. A failed check fails the test; anything else that goes wrong becomes a failure too, instead
     * of stopping the test server. Either way the scene is cleaned up first, so no stand-in player is left behind.
     */
    private static Runnable step(Scene scene, Runnable body)
    {
        return () -> {
            try
            {
                body.run();
            }
            catch (GameTestAssertException failure)
            {
                scene.cleanUp();
                throw failure;
            }
            catch (RuntimeException e)
            {
                scene.cleanUp();
                GameTestAssertException failure = new GameTestAssertException("the test threw " + e);
                failure.initCause(e);
                throw failure;
            }
        };
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
        /** Ticks watched so far, and the first problem the watch found (only the first is reported). */
        int watchedTicks;
        @Nullable
        String watchProblem;

        /** A survivor with no AI of its own (so it stays put), standing on the arena's floor. */
        Survivor spawnSurvivor(GameTestHelper helper)
        {
            Survivor spawned = helper.spawnWithNoFreeWill(ModEntities.SURVIVOR.get(), SURVIVOR_POS);
            this.survivor = spawned;
            return spawned;
        }

        /**
         * A fresh stand-in owner ({@link RisenTargetGameTest#addOwner}), moved onto the arena's floor (that helper puts
         * him at relative y 1, inside the floor's own layer). Any earlier owner leaves first; his risen undead crumble.
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

        /** One tick of the watch: notes the first thing that goes wrong (and when). */
        void watch()
        {
            this.watchedTicks++;
            if (this.watchProblem != null)
            {
                return;
            }
            String problem = this.findProblem();
            if (problem != null)
            {
                this.watchProblem = problem;
                this.problems.add("after " + this.watchedTicks + " tick(s) of the watch, " + problem);
            }
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
