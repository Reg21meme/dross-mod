package com.reg21meme.dross.enchant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.rage.Rage;
import com.reg21meme.dross.boss.rage.RageTeam;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.ai.goal.MoveTowardsTargetGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.targeting.TargetingConditions;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.animal.SnowGolem;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Headless tests (GameTests) for the player's allies and the risen undead ({@link RisenAllies}): iron golems, snow golems
 * and tamed wolves leave them alone. Run them with {@code .\gradlew.bat runGameTestServer --console=plain}, or in a dev
 * world with {@code /test runall}. They only exist in the development environment.
 *
 * <ul>
 *   <li>{@link #ironGolemLeavesRisenAlone}, {@link #snowGolemLeavesRisenAlone}, {@link #tamedWolfLeavesRisenAlone}: the
 *       ally (with its own AI) stands next to risen undead for {@link #ALONE_TICKS} of its ticks and never targets them,
 *       gets angry at them or hurts them. Then a plain (not risen) zombie, or a plain skeleton for the wolf, is added at
 *       the far end of the arena, further from the ally than any risen undead: the ally must go after it (so its AI really
 *       runs, and its search looks past the nearer risen undead), and is watched {@link #AFTER_TARGET_TICKS} more of its
 *       ticks (the iron golem kills the zombie in that time and is free again). The iron golem is watched more than 10
 *       seconds in all. All along, no risen undead may target the ally: a plain zombie the ally hurts calls nearby
 *       zombies for help, and risen zombies must ignore that call.</li>
 *   <li>{@link #ironGolemDropsAnOldGrudge}: an iron golem already fighting a mob that then counts as risen (its target,
 *       its anger and who last hurt it) lets go of it within {@link #DROP_GRACE_TICKS} of its ticks, never lands a blow
 *       on it, never takes it back, and stops attacking and walking at it.</li>
 *   <li>{@link #rageEnemiesMayStillFight}: the Rage Wand's rule wins. Not raged, an iron golem can't target or hurt a risen
 *       zombie; raged on different teams it can (and the allies' check leaves that target alone); raged on one team, never.</li>
 * </ul>
 * They use {@link RisenTargetGameTest}'s closed arena and stand-in owner (in creative mode, so no monster or golem goes
 * for him), wait until the arena has finished loading, count the ally's own ticks ({@link RisenTestWait}), and each test
 * has its own batch, like the other risen undead tests.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RisenAllyGameTest
{
    // ---------------------------------------------------------------------------------------------
    // Test setup (20 ticks = 1 second).
    // ---------------------------------------------------------------------------------------------

    /** Height (relative to the test) the mobs stand at: on the arena's floor. */
    private static final int STAND_Y = RisenTargetGameTest.STAND_Y;
    /** Where the ally starts: 1 block from the owner (at x 7, z 4), so every risen undead (within 3 of him) is within 5 of it. */
    private static final BlockPos ALLY_POS = new BlockPos(7, STAND_Y, 5);
    /** Where the plain mob stands: 7 blocks from where the ally started, further than any risen undead. */
    private static final BlockPos PLAIN_POS = new BlockPos(7, STAND_Y, 12);
    /** Where the old-grudge test's zombie stands: 3 blocks from the iron golem. */
    private static final BlockPos GRUDGE_POS = new BlockPos(7, STAND_Y, 8);

    /** Deathforged I: a leather helmet, so the risen undead don't burn in daylight during the test. */
    private static final int DEATHFORGED_LEVEL = 1;
    /** Damage of the rage test's blows (any amount: only whether it lands matters). */
    private static final float TEST_HIT_DAMAGE = 1.0F;

    // ---- Counted in the ally's own ticks (each is one step of its AI) ----
    /** How long the ally stands with only the risen undead about: 5 seconds (it looks for a target many times). */
    private static final int ALONE_TICKS = 100;
    /** It must go after the plain mob within this many ticks of it appearing (it normally takes a few). */
    private static final int TARGET_GRACE_TICKS = 100;
    /** How long it's watched after it went for the plain mob: 8 seconds. */
    private static final int AFTER_TARGET_TICKS = 160;
    /** The old grudge must be dropped within this many ticks: one ally check ({@link RisenAllies#CHECK_INTERVAL_TICKS}) and a little more. */
    private static final int DROP_GRACE_TICKS = RisenAllies.CHECK_INTERVAL_TICKS + 2;
    /** After dropping it, the golem is watched this long: it must not take it back. */
    private static final int AFTER_DROP_TICKS = 40;

    /** Each test's own tick limit: only a backstop behind its waits (see {@link RisenTestWait}). */
    private static final int TIMEOUT_TICKS = RisenTestWait.TEST_TIMEOUT_TICKS;

    private RisenAllyGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------------------------------------

    /** An iron golem next to a risen zombie and a risen skeleton never targets or hurts them; it does go for a plain zombie. */
    @GameTest(template = RisenTargetGameTest.ARENA, batch = "dross_risen_ally_iron_golem", timeoutTicks = TIMEOUT_TICKS)
    public static void ironGolemLeavesRisenAlone(GameTestHelper helper)
    {
        allyWatch(helper, new AllyCase("iron golem", List.of(RisenType.ZOMBIE, RisenType.SKELETON), EntityType.ZOMBIE,
                scene -> helper.spawn(EntityType.IRON_GOLEM, ALLY_POS)));
    }

    /**
     * A snow golem next to a risen zombie never targets or hurts it (not even with a stray snowball); it does go for a plain
     * zombie. It has Fire Resistance, so it doesn't melt if the test lands in a warm biome.
     */
    @GameTest(template = RisenTargetGameTest.ARENA, batch = "dross_risen_ally_snow_golem", timeoutTicks = TIMEOUT_TICKS)
    public static void snowGolemLeavesRisenAlone(GameTestHelper helper)
    {
        allyWatch(helper, new AllyCase("snow golem", List.of(RisenType.ZOMBIE), EntityType.ZOMBIE, scene -> {
            SnowGolem golem = helper.spawn(EntityType.SNOW_GOLEM, ALLY_POS);
            golem.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
            return golem;
        }));
    }

    /**
     * A wolf tamed by the owner, next to his risen skeleton, never targets or bites it (every wolf hunts skeletons); it does
     * go for a plain skeleton.
     */
    @GameTest(template = RisenTargetGameTest.ARENA, batch = "dross_risen_ally_tamed_wolf", timeoutTicks = TIMEOUT_TICKS)
    public static void tamedWolfLeavesRisenAlone(GameTestHelper helper)
    {
        allyWatch(helper, new AllyCase("tamed wolf", List.of(RisenType.SKELETON), EntityType.SKELETON, scene -> {
            Wolf wolf = helper.spawn(EntityType.WOLF, ALLY_POS);
            wolf.setTame(true);
            wolf.setOwnerUUID(scene.owner.getUUID());
            return wolf;
        }));
    }

    /**
     * An iron golem is fighting a zombie (its target, angry at it, hurt by it) when the zombie comes to count as a risen
     * undead (marked the way Necromancy marks them, so the fight started before). The golem must let go of all of it within
     * {@link #DROP_GRACE_TICKS} of its ticks, never land a blow on it, not take it back for {@link #AFTER_DROP_TICKS}, and
     * end up neither attacking nor walking at it. The zombie has no AI of its own, so it never hits back.
     */
    @GameTest(template = RisenTargetGameTest.ARENA, batch = "dross_risen_ally_old_grudge", timeoutTicks = TIMEOUT_TICKS)
    public static void ironGolemDropsAnOldGrudge(GameTestHelper helper)
    {
        Grudge grudge = new Grudge(helper);
        RisenTestWait drop = new RisenTestWait(helper, RisenTestWait.AI_MILLIS);
        RisenTestWait after = new RisenTestWait(helper, RisenTestWait.AI_MILLIS);
        startWhenArenaLoaded(helper, grudge.guard, grudge::setUp)
                .thenWaitUntil(grudge.guard.poll(drop, grudge::watchDrop))
                .thenExecute(grudge.guard.step(() -> {
                    drop.failIfWrong("");
                    grudge.phaseStart = -1;
                }))
                .thenWaitUntil(grudge.guard.poll(after, grudge::watchAfterDrop))
                .thenExecute(grudge.guard.step(() -> {
                    after.failIfWrong("after it let go: ");
                    grudge.checkCalm();
                    grudge.cleanUp();
                }))
                .thenSucceed();
    }

    /**
     * The Rage Wand's rule wins: an iron golem and a risen zombie raged on different teams may fight. Checked straight
     * away, with no waiting: not raged, the golem can't take the risen zombie as its target and its blow doesn't land; raged
     * on different teams (golem red, zombie blue) it can, the allies' check ({@link RisenAllies#forgetRisen}) leaves that
     * target alone, and its blow lands; with both on red, never.
     */
    @GameTest(template = RisenTargetGameTest.ARENA, batch = "dross_risen_ally_rage", timeoutTicks = TIMEOUT_TICKS)
    public static void rageEnemiesMayStillFight(GameTestHelper helper)
    {
        List<Mob> spawned = new ArrayList<>();
        ServerPlayer[] owner = new ServerPlayer[1];
        Runnable cleanUp = () -> {
            RisenTargetGameTest.removeOwner(owner[0]);
            spawned.forEach(Mob::discard);
        };
        RisenTestWait.Guard guard = new RisenTestWait.Guard(cleanUp);
        startWhenArenaLoaded(helper, guard, () -> {
            owner[0] = RisenTargetGameTest.addOwner(helper);
            Mob risen = DeathforgedGearGameTest.raiseOne(owner[0], RisenType.ZOMBIE, DEATHFORGED_LEVEL);
            if (risen == null)
            {
                throw new GameTestAssertException("the risen zombie couldn't be spawned");
            }
            IronGolem golem = helper.spawn(EntityType.IRON_GOLEM, ALLY_POS);
            spawned.add(golem);
            List<String> problems = new ArrayList<>();

            // Not raged: never.
            golem.setTarget(risen);
            if (golem.getTarget() == risen)
            {
                problems.add("not raged, the iron golem could take the risen zombie as its target");
            }
            if (lands(golem, risen))
            {
                problems.add("not raged, the iron golem's blow landed on the risen zombie");
            }

            // Raged on different teams: the Rage wins, they may fight.
            if (!Rage.enrage(golem, RageTeam.RED) || !Rage.enrage(risen, RageTeam.BLUE))
            {
                throw new GameTestAssertException("the iron golem and the risen zombie couldn't be raged");
            }
            golem.setTarget(risen);
            if (golem.getTarget() != risen)
            {
                problems.add("raged on different teams, the iron golem couldn't take the risen zombie as its target");
            }
            RisenAllies.forgetRisen(golem);
            if (golem.getTarget() != risen)
            {
                problems.add("raged on different teams, the allies' check made the iron golem let go of the risen zombie");
            }
            if (!lands(golem, risen))
            {
                problems.add("raged on different teams, the iron golem's blow didn't land on the risen zombie");
            }

            // Raged on one team: never.
            Rage.enrage(risen, RageTeam.RED);
            golem.setTarget(risen);
            if (golem.getTarget() == risen)
            {
                problems.add("raged on one team, the iron golem could take the risen zombie as its target");
            }
            if (lands(golem, risen))
            {
                problems.add("raged on one team, the iron golem's blow landed on the risen zombie");
            }

            cleanUp.run();
            if (!problems.isEmpty())
            {
                throw new GameTestAssertException(problems.size() + " problem(s): " + String.join("; ", problems));
            }
        })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // The ally watch (iron golem, snow golem, tamed wolf)
    // ---------------------------------------------------------------------------------------------

    /**
     * One ally's test.
     *
     * @param name what it is, for messages ("iron golem")
     * @param risenTypes the risen undead next to it
     * @param plainType the plain mob it must go after (one it hunts: a zombie, or a skeleton for a wolf)
     * @param spawnAlly spawns it at {@link #ALLY_POS} (the owner is already there)
     */
    private record AllyCase(String name, List<RisenType> risenTypes, EntityType<? extends Mob> plainType, Function<Scene, Mob> spawnAlly) {}

    /** Alone with the risen undead, then a plain mob is added, then watched while it goes for that one. */
    private static void allyWatch(GameTestHelper helper, AllyCase test)
    {
        Scene scene = new Scene(helper, test.name());
        RisenTestWait alone = new RisenTestWait(helper, RisenTestWait.AI_MILLIS);
        RisenTestWait spot = new RisenTestWait(helper, RisenTestWait.AI_MILLIS);
        RisenTestWait after = new RisenTestWait(helper, RisenTestWait.AI_MILLIS);
        startWhenArenaLoaded(helper, scene.guard, () -> scene.setUp(test))
                .thenWaitUntil(scene.guard.poll(alone, scene::watchAlone))
                .thenExecute(scene.guard.step(() -> {
                    alone.failIfWrong("with only the risen undead about: ");
                    scene.addPlain(test.plainType());
                    scene.phaseStart = -1;
                }))
                .thenWaitUntil(scene.guard.poll(spot, scene::watchSpot))
                .thenExecute(scene.guard.step(() -> {
                    spot.failIfWrong("once the plain " + scene.plainName() + " was added: ");
                    scene.phaseStart = -1;
                }))
                .thenWaitUntil(scene.guard.poll(after, scene::watchAfter))
                .thenExecute(scene.guard.step(() -> {
                    after.failIfWrong("while it went for the plain " + scene.plainName() + ": ");
                    scene.cleanUp();
                }))
                .thenSucceed();
    }

    /** What an ally test works with (filled in once its arena has loaded). */
    private static final class Scene
    {
        final GameTestHelper helper;
        final String allyName;
        @Nullable
        ServerPlayer owner;
        @Nullable
        Mob ally;
        /** The risen undead next to it, and their health when they rose (it must never drop). */
        final List<Mob> risen = new ArrayList<>();
        final Map<Mob, Float> healthAtStart = new IdentityHashMap<>();
        @Nullable
        Mob plain;
        /** When the plain mob appeared: how far it was from the ally, and the nearest risen undead (for messages). */
        String distances = "";
        /** Other targets the ally had while it should have been going for the plain mob (for messages). */
        final List<String> otherTargets = new ArrayList<>();
        /** The ally's own tick count when the current phase began (-1: at the next look). */
        int phaseStart = -1;
        /** Every step and wait goes through it: the test's first failure is the one it reports, cleaned up. */
        final RisenTestWait.Guard guard = new RisenTestWait.Guard(this::cleanUp);

        Scene(GameTestHelper helper, String allyName)
        {
            this.helper = helper;
            this.allyName = allyName;
        }

        /** The stand-in owner, his risen undead, and the ally (whose searches must skip them). */
        void setUp(AllyCase test)
        {
            this.owner = RisenTargetGameTest.addOwner(this.helper);
            for (RisenType type : test.risenTypes())
            {
                Mob mob = DeathforgedGearGameTest.raiseOne(this.owner, type, DEATHFORGED_LEVEL);
                if (mob == null)
                {
                    throw new GameTestAssertException("the risen undead couldn't be spawned");
                }
                this.risen.add(mob);
                this.healthAtStart.put(mob, mob.getHealth());
            }
            this.ally = test.spawnAlly().apply(this);
            if (!RisenAllies.searchesSkipRisen(this.ally))
            {
                throw new GameTestAssertException("the " + this.allyName + "'s target searches don't skip risen undead");
            }
            for (Mob mob : this.risen)
            {
                if (!RisenAllies.leavesAlone(this.ally, mob))
                {
                    throw new GameTestAssertException("the " + this.allyName + " doesn't count a risen "
                            + RisenTargetGameTest.name(mob) + " as one to leave alone");
                }
            }
        }

        /** The plain mob, with no AI of its own (it stays put) and a helmet (it doesn't burn), at {@link #PLAIN_POS}. */
        void addPlain(EntityType<? extends Mob> type)
        {
            Mob mob = this.helper.spawnWithNoFreeWill(type, PLAIN_POS);
            mob.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            this.plain = mob;
            double nearestRisen = Double.MAX_VALUE;
            for (Mob risenMob : this.risen)
            {
                nearestRisen = Math.min(nearestRisen, this.ally.distanceTo(risenMob));
            }
            this.distances = String.format("it was %.1f blocks from the %s, the nearest risen undead %.1f",
                    this.ally.distanceTo(mob), this.allyName, nearestRisen);
        }

        String plainName()
        {
            return this.plain == null ? "mob" : RisenTargetGameTest.name(this.plain);
        }

        /** The ally's own ticks since the current phase began. */
        int phaseTicks()
        {
            if (this.phaseStart < 0)
            {
                this.phaseStart = this.ally.tickCount;
            }
            return this.ally.tickCount - this.phaseStart;
        }

        // ---- What the waits look at ----

        /** {@link #ALONE_TICKS} of its ticks with only the risen undead about, nothing wrong on any of them. */
        RisenTestWait.Look watchAlone()
        {
            int ticks = this.phaseTicks();
            String problem = this.findProblem();
            if (problem != null)
            {
                return RisenTestWait.Look.failed("after " + ticks + " of its ticks, " + problem);
            }
            return ticks < ALONE_TICKS ? RisenTestWait.Look.waiting("watched for " + ticks + " of its " + ALONE_TICKS + " ticks")
                    : RisenTestWait.Look.DONE;
        }

        /** It goes after the plain mob within {@link #TARGET_GRACE_TICKS}, still never wronging the risen undead. */
        RisenTestWait.Look watchSpot()
        {
            int ticks = this.phaseTicks();
            String problem = this.findProblem();
            if (problem != null)
            {
                return RisenTestWait.Look.failed("after " + ticks + " of its ticks, " + problem);
            }
            if (this.ally.getTarget() == this.plain)
            {
                return RisenTestWait.Look.DONE;
            }
            if (this.ally.getTarget() != null)
            {
                this.otherTargets.add(RisenTargetGameTest.name(this.ally.getTarget()) + " at tick " + ticks);
            }
            if (!this.plain.isAlive())
            {
                return RisenTestWait.Look.failed("the plain " + this.plainName() + " is gone before the " + this.allyName + " went after it");
            }
            boolean mayWait = ticks < TARGET_GRACE_TICKS;
            LivingEntity target = this.ally.getTarget();
            String wrong = "the " + this.allyName + " didn't go after the plain " + this.plainName() + " within "
                    + ticks + " of its ticks (" + this.distances + "; its target: "
                    + (target == null ? "none" : RisenTargetGameTest.name(target)) + ")";
            if (!mayWait)
            {
                wrong += " [" + this.searchReport() + "]";
            }
            return new RisenTestWait.Look(wrong, mayWait);
        }

        /**
         * Why the ally might not pick the plain mob, for the failure message: where both are, whether it can see it, its
         * running target goals, and whether each of its "nearest X" searches would accept it.
         */
        private String searchReport()
        {
            List<String> parts = new ArrayList<>();
            parts.add("ally at " + this.ally.blockPosition().toShortString() + ", plain at " + this.plain.blockPosition().toShortString());
            parts.add("line of sight: " + this.ally.hasLineOfSight(this.plain));
            parts.add("other targets it had meanwhile: " + (this.otherTargets.isEmpty() ? "none"
                    : String.join(", ", this.otherTargets.subList(0, Math.min(5, this.otherTargets.size())))));
            parts.add("running target goals: " + this.ally.targetSelector.getRunningGoals()
                    .map(goal -> goal.getGoal().getClass().getSimpleName()).toList());
            for (WrappedGoal wrapped : this.ally.targetSelector.getAvailableGoals())
            {
                if (wrapped.getGoal() instanceof NearestAttackableTargetGoal<?> search)
                {
                    TargetingConditions conditions = RisenAllies.searchConditions(search);
                    parts.add(search.getClass().getSimpleName() + " (priority " + wrapped.getPriority() + ", running "
                            + wrapped.isRunning() + "): "
                            + (conditions == null ? "conditions unreadable" : "accepts it: " + conditions.test(this.ally, this.plain)));
                }
            }
            return String.join("; ", parts);
        }

        /** {@link #AFTER_TARGET_TICKS} more of its ticks, nothing wrong on any of them. */
        RisenTestWait.Look watchAfter()
        {
            int ticks = this.phaseTicks();
            String problem = this.findProblem();
            if (problem != null)
            {
                return RisenTestWait.Look.failed("after " + ticks + " of its ticks, " + problem);
            }
            return ticks < AFTER_TARGET_TICKS ? RisenTestWait.Look.waiting("watched for " + ticks + " of its " + AFTER_TARGET_TICKS + " ticks")
                    : RisenTestWait.Look.DONE;
        }

        /**
         * What's wrong right now, or null: the ally or a risen undead gone, the ally targeting or angry at a risen undead,
         * a risen undead targeting the ally (nothing here gives them a reason: the plain mob it hurts calls them for help,
         * which they must ignore), or a risen undead hurt (by the ally, or at all: nothing else here can hurt them).
         */
        @Nullable
        private String findProblem()
        {
            if (!this.ally.isAlive())
            {
                return "the " + this.allyName + " is gone";
            }
            for (Mob mob : this.risen)
            {
                String name = RisenTargetGameTest.name(mob);
                if (!mob.isAlive())
                {
                    return "a risen " + name + " is gone (crumbled or died)";
                }
                if (this.ally.getTarget() == mob)
                {
                    return "the " + this.allyName + " targets a risen " + name;
                }
                if (mob.getTarget() == this.ally)
                {
                    return "a risen " + name + " targets the " + this.allyName + " on its own (the owner never hit it, and it never went for him)";
                }
                if (this.ally instanceof NeutralMob neutral && mob.getUUID().equals(neutral.getPersistentAngerTarget()))
                {
                    return "the " + this.allyName + " is angry at a risen " + name;
                }
                if (mob.getLastHurtByMob() == this.ally)
                {
                    return "the " + this.allyName + " hurt a risen " + name;
                }
                float before = this.healthAtStart.get(mob);
                if (mob.getHealth() < before)
                {
                    return "a risen " + name + " lost health (" + before + " -> " + mob.getHealth() + ")";
                }
            }
            return null;
        }

        /** The owner leaves (his risen undead crumble) and the ally and plain mob are removed. Safe to call twice. */
        void cleanUp()
        {
            RisenTargetGameTest.removeOwner(this.owner);
            if (this.ally != null)
            {
                this.ally.discard();
            }
            if (this.plain != null)
            {
                this.plain.discard();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The old grudge
    // ---------------------------------------------------------------------------------------------

    /** What the old-grudge test works with. */
    private static final class Grudge
    {
        final GameTestHelper helper;
        @Nullable
        IronGolem golem;
        @Nullable
        Zombie zombie;
        float healthAtStart;
        /** The golem's own tick count when the current phase began (-1: at the next look). */
        int phaseStart = -1;
        final RisenTestWait.Guard guard = new RisenTestWait.Guard(this::cleanUp);

        Grudge(GameTestHelper helper)
        {
            this.helper = helper;
        }

        /**
         * The golem takes a plain zombie as its target, is angry at it and was hurt by it; then the zombie comes to count as
         * a risen undead (its owner's id in its persistent data, as Necromancy marks them; the owner needn't be here).
         */
        void setUp()
        {
            this.golem = this.helper.spawn(EntityType.IRON_GOLEM, ALLY_POS);
            this.zombie = this.helper.spawnWithNoFreeWill(EntityType.ZOMBIE, GRUDGE_POS);
            this.zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
            this.golem.setTarget(this.zombie);
            if (this.golem.getTarget() != this.zombie)
            {
                throw new GameTestAssertException("the iron golem couldn't take the plain zombie as its target");
            }
            this.golem.setLastHurtByMob(this.zombie);
            this.golem.setPersistentAngerTarget(this.zombie.getUUID());
            this.golem.startPersistentAngerTimer();
            this.zombie.getPersistentData().putUUID(RisenUndead.OWNER_KEY, UUID.randomUUID());
            this.zombie.getPersistentData().putLong(RisenUndead.SPAWN_TIME_KEY, this.helper.getLevel().getGameTime());
            this.healthAtStart = this.zombie.getHealth();
        }

        int phaseTicks()
        {
            if (this.phaseStart < 0)
            {
                this.phaseStart = this.golem.tickCount;
            }
            return this.golem.tickCount - this.phaseStart;
        }

        /** It lets go of all of it within {@link #DROP_GRACE_TICKS} of its ticks, never landing a blow on it. */
        RisenTestWait.Look watchDrop()
        {
            int ticks = this.phaseTicks();
            String harm = this.harm();
            if (harm != null)
            {
                return RisenTestWait.Look.failed("after " + ticks + " of its ticks, " + harm);
            }
            String held = this.held();
            if (held == null)
            {
                return RisenTestWait.Look.DONE;
            }
            return new RisenTestWait.Look("after " + ticks + " of its ticks the iron golem still holds on to the risen zombie ("
                    + held + ")", ticks < DROP_GRACE_TICKS);
        }

        /** For {@link #AFTER_DROP_TICKS} more of its ticks it doesn't take any of it back, and never lands a blow. */
        RisenTestWait.Look watchAfterDrop()
        {
            int ticks = this.phaseTicks();
            String problem = this.harm();
            if (problem == null && this.held() != null)
            {
                problem = "the iron golem took the risen zombie back (" + this.held() + ")";
            }
            if (problem != null)
            {
                return RisenTestWait.Look.failed("after " + ticks + " of its ticks, " + problem);
            }
            return ticks < AFTER_DROP_TICKS ? RisenTestWait.Look.waiting("watched for " + ticks + " of its " + AFTER_DROP_TICKS + " ticks")
                    : RisenTestWait.Look.DONE;
        }

        /** With no target left, it neither attacks nor walks at it any more. */
        void checkCalm()
        {
            if (this.golem.getTarget() != null)
            {
                return; // something else (outside the test) caught its eye: its attack goals are about that one
            }
            boolean stillAttacking = this.golem.goalSelector.getRunningGoals().anyMatch(goal ->
                    goal.getGoal() instanceof MeleeAttackGoal || goal.getGoal() instanceof MoveTowardsTargetGoal);
            if (stillAttacking)
            {
                throw new GameTestAssertException("the iron golem let go of the risen zombie but is still attacking or walking at it");
            }
        }

        /** What it still holds against the zombie (its target, anger, who last hurt it), or null if nothing. */
        @Nullable
        private String held()
        {
            List<String> held = new ArrayList<>();
            if (this.golem.getTarget() == this.zombie)
            {
                held.add("its target");
            }
            if (this.zombie.getUUID().equals(this.golem.getPersistentAngerTarget()))
            {
                held.add("angry at it");
            }
            if (this.golem.getLastHurtByMob() == this.zombie)
            {
                held.add("it last hurt the golem");
            }
            return held.isEmpty() ? null : String.join(", ", held);
        }

        /** The zombie gone or hurt, or null if neither. */
        @Nullable
        private String harm()
        {
            if (!this.golem.isAlive() || !this.zombie.isAlive())
            {
                return "the iron golem or the zombie is gone";
            }
            if (this.zombie.getHealth() < this.healthAtStart || this.zombie.getLastHurtByMob() == this.golem)
            {
                return "the iron golem hurt the risen zombie (health " + this.healthAtStart + " -> " + this.zombie.getHealth() + ")";
            }
            return null;
        }

        /** Both removed. Safe to call twice. */
        void cleanUp()
        {
            if (this.golem != null)
            {
                this.golem.discard();
            }
            if (this.zombie != null)
            {
                this.zombie.discard();
            }
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** True if the attacker's blow lands on the victim (accepted, or its health dropped). Its hurt cooldown is reset first. */
    private static boolean lands(Mob attacker, LivingEntity victim)
    {
        victim.invulnerableTime = 0;
        float before = victim.getHealth();
        boolean landed = victim.hurt(attacker.damageSources().mobAttack(attacker), TEST_HIT_DAMAGE);
        return landed || victim.getHealth() < before;
    }

    /**
     * The start of a test: wait until the arena has finished loading (in real time, {@link RisenTestWait}), then one step
     * that checks it did and runs {@code firstStep}. Nothing is added to the world before that.
     */
    private static GameTestSequence startWhenArenaLoaded(GameTestHelper helper, RisenTestWait.Guard guard, Runnable firstStep)
    {
        RisenTestWait arena = new RisenTestWait(helper, RisenTestWait.ARENA_MILLIS);
        return helper.startSequence()
                .thenWaitUntil(guard.poll(arena, RisenTestWait.arenaLoaded(helper, RisenTargetGameTest.ARENA_SIZE, STAND_Y)))
                .thenExecute(guard.step(() -> {
                    arena.failIfWrong("");
                    firstStep.run();
                }));
    }
}
