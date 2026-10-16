package com.reg21meme.dross.boss.rage;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.boss.golem.GolemMinion;
import com.reg21meme.dross.boss.ratking.Rat;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.registry.ModEffects;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModItems;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA_WAIT_TICKS;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.MIDDLE;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.joinPlayer;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.leaveAll;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.waitForArena;

/**
 * Headless tests (GameTests) for the Rage Wand and its Rage ({@code .\gradlew.bat runGameTestServer}), on the Cinder
 * Colossus tests' flat arena ({@code ColossusGameTest.ARENA}). Each runs in its own batch (raged mobs look for enemies
 * 48 blocks round, so two tests side by side would fight each other), and removes what it spawned when it's done.
 * <ul>
 *   <li>{@link #threeRagedZombiesFightUntilOneIsLeft}: three raged zombies, each for itself, fight until exactly one
 *       is left; that one keeps its Rage for a while, then calms down after {@link RageTuning#RAGE_CALM_TICKS} (no
 *       Rage, no rage goals, no side).</li>
 *   <li>{@link #ragedZombieIgnoresPlayers}: a raged zombie next to a player (who even hits it) never targets the
 *       player while another raged zombie is near, and goes for that one; a zombie that isn't raged does go for the
 *       player.</li>
 *   <li>{@link #wandHitDoesNoDamageAndRagesForever}: the wand's hit does no damage and no knockback and gives Rage for
 *       ever (its swirl hidden); hitting a player does nothing (no damage, and players can't have the Rage); a frozen
 *       (no AI) mob can't be raged.</li>
 *   <li>{@link #milkDoesNotCureRage}: milk takes other effects away, not the Rage.</li>
 *   <li>{@link #ragedPigHeadbuttsRagedCow}: a raged pig and a raged cow (no attack of their own) headbutt each other,
 *       2 damage a time.</li>
 *   <li>{@link #ragedVillagerHeadbutts}: a raged villager (a brain-based mob) headbutts a raged zombie.</li>
 *   <li>{@link #ragedColossusGolemsJoinItsSide}: a raged Colossus's golems (one already alive, one summoned after)
 *       join its side and never target it or each other, and it and they go for the raged enemy.</li>
 *   <li>{@link #ragedRatKingWakesAndHisRatsJoinHim}: raging a sleeping Rat King wakes him; his new rat joins his side
 *       and never targets him, and goes for the raged enemy.</li>
 *   <li>{@link #rageSurvivesSaveAndLoad}: a raged zombie saved and loaded again (NBT) keeps its Rage (for ever) and its
 *       side, its rage goals come back, and it goes for its enemy, never its own side.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RageGameTest
{
    /** The longest the three zombies may take to fight it out (ticks). */
    private static final int FREE_FOR_ALL_TICKS = 4000;
    /** After the last enemy died, the survivor is checked to still be raged this long after (well before it calms). */
    private static final int STILL_RAGED_AFTER = 150;
    /** It calms down this long after the last enemy died, give or take its enemy check's interval and a little. */
    private static final int CALM_SLACK = RageTuning.ENEMY_SCAN_TICKS + 10;
    /** Ticks a test watches who targets whom. */
    private static final int WATCH_TICKS = 100;
    /** The bosses' golems and rats climb out of the ground first; they're watched this long. */
    private static final int BOSS_WATCH_TICKS = 140;
    /** Health given to mobs that should stay alive through a test. */
    private static final float TOUGH = 400.0F;
    /** The longest a headbutt test waits for the first headbutt (ticks). */
    private static final int HEADBUTT_WAIT = 400;
    private static final float HEALTH_TOLERANCE = 0.01F;
    /** The goals of a loaded raged mob must be back within this many ticks. */
    private static final int GOALS_BACK_TICKS = 5;

    private RageGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // Free for all, and calming down
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_rage_free_for_all", timeoutTicks = ARENA_WAIT_TICKS + FREE_FOR_ALL_TICKS + 600)
    public static void threeRagedZombiesFightUntilOneIsLeft(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::threeRagedZombiesFightUntilOneIsLeft))
        {
            return;
        }
        List<Zombie> zombies = List.of(zombie(helper, MIDDLE.offset(-3, 0, -2)), zombie(helper, MIDDLE.offset(3, 0, -2)),
                zombie(helper, MIDDLE.offset(0, 0, 3)));
        for (Zombie zombie : zombies)
        {
            if (!Rage.enrage(zombie))
            {
                helper.fail("the wand's Rage didn't take on a zombie");
            }
        }
        if (zombies.stream().map(Rage::sideOf).distinct().count() != zombies.size())
        {
            helper.fail("each raged zombie should fight for itself (its own side)");
        }
        Zombie[] last = new Zombie[1];
        long[] lastDeath = new long[1];
        long start = helper.getTick();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    long alive = zombies.stream().filter(LivingEntity::isAlive).count();
                    if (alive > 1 && helper.getTick() - start < FREE_FOR_ALL_TICKS)
                    {
                        helper.fail(alive + " raged zombies are still fighting");
                    }
                })
                .thenExecute(() -> {
                    lastDeath[0] = helper.getTick();
                    List<Zombie> alive = zombies.stream().filter(LivingEntity::isAlive).toList();
                    if (alive.size() != 1)
                    {
                        helper.fail("exactly one zombie should be left after the fight, but " + alive.size() + " are");
                    }
                    last[0] = alive.get(0);
                    if (!Rage.isRaged(last[0]))
                    {
                        helper.fail("the last zombie calmed down the moment its last enemy died");
                    }
                })
                .thenIdle(STILL_RAGED_AFTER)
                .thenExecute(() -> {
                    if (!Rage.isRaged(last[0]))
                    {
                        helper.fail("the last zombie calmed down " + STILL_RAGED_AFTER + " ticks after the fight, before "
                                + RageTuning.RAGE_CALM_TICKS);
                    }
                })
                .thenWaitUntil(() -> {
                    if (Rage.isRaged(last[0]) && helper.getTick() - lastDeath[0] <= RageTuning.RAGE_CALM_TICKS + CALM_SLACK)
                    {
                        helper.fail("the last zombie is still raged");
                    }
                })
                .thenExecute(() -> {
                    long calmAfter = helper.getTick() - lastDeath[0];
                    if (Rage.isRaged(last[0]))
                    {
                        helper.fail("the last zombie is still raged " + calmAfter + " ticks after the fight (it should calm down after "
                                + RageTuning.RAGE_CALM_TICKS + ")");
                    }
                    if (calmAfter < RageTuning.RAGE_CALM_TICKS)
                    {
                        helper.fail("the last zombie calmed down after " + calmAfter + " ticks, before " + RageTuning.RAGE_CALM_TICKS);
                    }
                    if (RageAi.hasRageGoals(last[0]) || last[0].getPersistentData().contains(Rage.TAG_SIDE))
                    {
                        helper.fail("the calm zombie still has its rage goals or its side");
                    }
                    discard(zombies);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Players are ignored while there are raged enemies
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_rage_ignores_players", timeoutTicks = ARENA_WAIT_TICKS + WATCH_TICKS + 100)
    public static void ragedZombieIgnoresPlayers(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::ragedZombieIgnoresPlayers))
        {
            return;
        }
        ServerPlayer player = joinPlayer(helper, "rage-test-player", MIDDLE.offset(-6, 0, 0), 0.0F);
        player.setGameMode(GameType.SURVIVAL);
        Zombie near = tough(zombie(helper, MIDDLE.offset(-4, 0, 0)));
        Zombie far = tough(zombie(helper, MIDDLE.offset(5, 0, 0)));
        // Not raged: it shows the player is a fair target (a zombie goes for him).
        Zombie control = tough(zombie(helper, MIDDLE.offset(-6, 0, 5)));
        Rage.enrage(near);
        Rage.enrage(far);
        near.hurt(player.damageSources().playerAttack(player), 1.0F); // the player even hits it
        boolean[] controlWentForPlayer = {false};
        List<Entity> spawned = List.of(near, far, control);
        helper.startSequence()
                .thenExecuteFor(WATCH_TICKS, () -> {
                    if (near.getTarget() instanceof Player || far.getTarget() instanceof Player)
                    {
                        helper.fail("a raged zombie went for the player while another raged zombie was near");
                    }
                    controlWentForPlayer[0] |= control.getTarget() == player;
                })
                .thenExecute(() -> {
                    leaveAll(List.of(player));
                    if (near.getTarget() != far)
                    {
                        helper.fail("the raged zombie should go for the other raged zombie, but its target is " + near.getTarget());
                    }
                    if (!controlWentForPlayer[0])
                    {
                        helper.fail("the zombie that isn't raged never went for the player (so the test proves nothing)");
                    }
                    discard(spawned);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // The wand, and no cure
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_rage_wand", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void wandHitDoesNoDamageAndRagesForever(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::wandHitDoesNoDamageAndRagesForever))
        {
            return;
        }
        Zombie zombie = zombie(helper, MIDDLE);
        Zombie frozen = zombie(helper, MIDDLE.offset(4, 0, 4));
        frozen.setNoAi(true);
        Player holder = helper.makeMockPlayer(); // (a creative mock player: zombies leave him alone)
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(-2, 0, 0)));
        holder.moveTo(at.x, at.y, at.z, -90.0F, 0.0F);
        holder.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(ModItems.RAGE_WAND.get()));
        float health = zombie.getHealth();
        Vec3 motion = zombie.getDeltaMovement();
        holder.attack(zombie);
        if (Math.abs(zombie.getHealth() - health) > HEALTH_TOLERANCE || zombie.hurtTime > 0 || zombie.getLastHurtByMob() != null)
        {
            helper.fail("the wand's hit hurt the zombie (health " + health + " -> " + zombie.getHealth() + ")");
        }
        if (zombie.getDeltaMovement().subtract(motion).lengthSqr() > 1.0E-6D)
        {
            helper.fail("the wand's hit knocked the zombie back");
        }
        MobEffectInstance rage = zombie.getEffect(ModEffects.RAGE.get());
        if (rage == null)
        {
            helper.fail("the wand's hit didn't rage the zombie");
            return;
        }
        if (!rage.isInfiniteDuration() || rage.isVisible())
        {
            helper.fail("the Rage should last for ever with its swirl hidden (duration " + rage.getDuration() + ", visible "
                    + rage.isVisible() + ")");
        }
        // A player hit with it: no damage, and players can't have the Rage.
        ServerPlayer other = joinPlayer(helper, "rage-test-hit-player", MIDDLE.offset(-2, 0, 2), 0.0F);
        other.setGameMode(GameType.SURVIVAL);
        float playerHealth = other.getHealth();
        holder.attack(other);
        other.addEffect(Rage.rageEffect());
        boolean playerHurt = Math.abs(other.getHealth() - playerHealth) > HEALTH_TOLERANCE;
        boolean playerRaged = Rage.isRaged(other);
        leaveAll(List.of(other));
        if (playerHurt || playerRaged)
        {
            helper.fail("the wand hurt a player or a player got the Rage (hurt " + playerHurt + ", raged " + playerRaged + ")");
        }
        // A frozen mob (no AI, like the showcase mobs) can't be raged.
        holder.attack(frozen);
        if (Rage.isRaged(frozen))
        {
            helper.fail("a mob with no AI was raged");
        }
        discard(List.of(zombie, frozen));
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_rage_milk", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void milkDoesNotCureRage(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::milkDoesNotCureRage))
        {
            return;
        }
        Zombie zombie = zombie(helper, MIDDLE);
        Rage.enrage(zombie);
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600));
        zombie.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
        if (zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
        {
            helper.fail("milk didn't take Slowness away (so the test proves nothing)");
        }
        if (!Rage.isRaged(zombie))
        {
            helper.fail("milk cured the Rage");
        }
        discard(List.of(zombie));
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Headbutts
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_rage_headbutt", timeoutTicks = ARENA_WAIT_TICKS + HEADBUTT_WAIT + 100)
    public static void ragedPigHeadbuttsRagedCow(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::ragedPigHeadbuttsRagedCow))
        {
            return;
        }
        Pig pig = spawn(helper, EntityType.PIG, MIDDLE.offset(-2, 0, 0));
        Cow cow = spawn(helper, EntityType.COW, MIDDLE.offset(2, 0, 0));
        if (!RageAi.headbutts(pig) || !RageAi.headbutts(cow))
        {
            helper.fail("a pig and a cow have no attack of their own: they should headbutt");
        }
        Rage.enrage(pig);
        Rage.enrage(cow);
        float max = cow.getMaxHealth();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (cow.getHealth() >= max - HEALTH_TOLERANCE)
                    {
                        helper.fail("the pig hasn't headbutted the cow yet");
                    }
                })
                .thenExecute(() -> {
                    float taken = max - cow.getHealth();
                    if (Math.abs(taken - RageTuning.HEADBUTT_DAMAGE) > HEALTH_TOLERANCE)
                    {
                        helper.fail("a headbutt should take " + RageTuning.HEADBUTT_DAMAGE + ", but it took " + taken);
                    }
                    if (cow.getLastHurtByMob() != pig)
                    {
                        helper.fail("the cow was hurt by " + cow.getLastHurtByMob() + ", not the pig");
                    }
                    discard(List.of(pig, cow));
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_rage_villager", timeoutTicks = ARENA_WAIT_TICKS + HEADBUTT_WAIT + 100)
    public static void ragedVillagerHeadbutts(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::ragedVillagerHeadbutts))
        {
            return;
        }
        Villager villager = tough(spawn(helper, EntityType.VILLAGER, MIDDLE.offset(-3, 0, 0)));
        Zombie zombie = tough(zombie(helper, MIDDLE.offset(3, 0, 0)));
        Rage.enrage(villager);
        Rage.enrage(zombie);
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (zombie.getLastHurtByMob() != villager)
                    {
                        helper.fail("the raged villager hasn't headbutted the raged zombie yet");
                    }
                })
                .thenExecute(() -> discard(List.of(villager, zombie)))
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Bosses: their minions join their side
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_rage_colossus", timeoutTicks = ARENA_WAIT_TICKS + BOSS_WATCH_TICKS + 100)
    public static void ragedColossusGolemsJoinItsSide(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::ragedColossusGolemsJoinItsSide))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        CinderColossus golem = CinderColossus.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        if (golem == null)
        {
            helper.fail("couldn't spawn the Colossus");
            return;
        }
        GolemMinion early = GolemMinion.summon(ModEntities.STONE_GOLEM.get(), level, golem,
                helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(-5, 0, 0))), 0.0F);
        Zombie enemy = tough(zombie(helper, MIDDLE.offset(6, 0, 6)));
        Rage.enrage(enemy);
        if (early == null || Rage.isRaged(early))
        {
            helper.fail("the golem summoned before the Rage should exist and not be raged yet");
            return;
        }
        Rage.enrage(golem);
        GolemMinion fresh = GolemMinion.summon(ModEntities.STONE_GOLEM.get(), level, golem,
                helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(5, 0, 0))), 0.0F);
        if (fresh == null)
        {
            helper.fail("couldn't summon a golem for the raged Colossus");
            return;
        }
        UUID side = Rage.sideOf(golem);
        for (GolemMinion minion : List.of(early, fresh))
        {
            if (!Rage.isRaged(minion) || !side.equals(Rage.sideOf(minion)))
            {
                helper.fail("the Colossus's golem should be raged on its side (raged " + Rage.isRaged(minion) + ")");
            }
        }
        if (Rage.areEnemies(golem, early) || Rage.areEnemies(early, fresh) || !Rage.areEnemies(golem, enemy))
        {
            helper.fail("the Colossus and its golems should be one side, the zombie their enemy");
        }
        List<Entity> spawned = List.of(golem, early, fresh, enemy);
        helper.startSequence()
                .thenExecuteFor(BOSS_WATCH_TICKS, () -> {
                    for (GolemMinion minion : List.of(early, fresh))
                    {
                        if (minion.getTarget() == golem || minion.getTarget() instanceof GolemMinion)
                        {
                            helper.fail("a raged Colossus's golem targeted its master or another golem of its side");
                        }
                        if (golem.getTarget() == minion)
                        {
                            helper.fail("the raged Colossus targeted its own golem");
                        }
                    }
                })
                .thenExecute(() -> {
                    if (golem.getTarget() != enemy || early.getTarget() != enemy || fresh.getTarget() != enemy)
                    {
                        helper.fail("the Colossus and its golems should go for the raged zombie (Colossus " + golem.getTarget()
                                + ", golems " + early.getTarget() + ", " + fresh.getTarget() + ")");
                    }
                    discard(spawned);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_rage_ratking", timeoutTicks = ARENA_WAIT_TICKS + BOSS_WATCH_TICKS + 100)
    public static void ragedRatKingWakesAndHisRatsJoinHim(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::ragedRatKingWakesAndHisRatsJoinHim))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        RatKing king = RatKing.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)), 0.0F);
        if (king == null || !king.isAsleep())
        {
            helper.fail("couldn't spawn a sleeping Rat King");
            return;
        }
        Zombie enemy = tough(zombie(helper, MIDDLE.offset(6, 0, 6)));
        Rage.enrage(enemy);
        Rage.enrage(king);
        if (!king.isAwake())
        {
            helper.fail("raging a sleeping Rat King should wake him");
        }
        Rat rat = Rat.summon(ModEntities.RAT.get(), level, king, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(-4, 0, 0))), 0.0F);
        if (rat == null)
        {
            helper.fail("couldn't summon a rat for the raged Rat King");
            return;
        }
        if (!Rage.isRaged(rat) || !Rage.sideOf(king).equals(Rage.sideOf(rat)))
        {
            helper.fail("the raged Rat King's new rat should be raged on his side");
        }
        List<Entity> spawned = List.of(king, rat, enemy);
        helper.startSequence()
                .thenExecuteFor(BOSS_WATCH_TICKS, () -> {
                    if (rat.getTarget() == king || king.getTarget() == rat)
                    {
                        helper.fail("the raged Rat King and his rat targeted each other");
                    }
                })
                .thenExecute(() -> {
                    if (rat.getTarget() != enemy || king.getTarget() != enemy)
                    {
                        helper.fail("the Rat King and his rat should go for the raged zombie (king " + king.getTarget() + ", rat "
                                + rat.getTarget() + ")");
                    }
                    discard(spawned);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Saving and loading
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_rage_reload", timeoutTicks = ARENA_WAIT_TICKS + WATCH_TICKS + 100)
    public static void rageSurvivesSaveAndLoad(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::rageSurvivesSaveAndLoad))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Zombie leader = tough(zombie(helper, MIDDLE.offset(-4, 0, -3)));
        Zombie member = tough(zombie(helper, MIDDLE.offset(-4, 0, 3)));
        Zombie enemy = tough(zombie(helper, MIDDLE.offset(5, 0, 0)));
        Rage.enrage(leader);
        Rage.join(member, leader);
        Rage.enrage(enemy);
        UUID side = Rage.sideOf(leader);

        // Save the member and load it back as a new zombie (a world saved and loaded again).
        CompoundTag tag = member.saveWithoutId(new CompoundTag());
        tag.putString("id", EntityType.getKey(member.getType()).toString());
        tag.remove("UUID");
        member.discard();
        Entity loaded = EntityType.create(tag, level).orElse(null);
        if (!(loaded instanceof Zombie copy))
        {
            helper.fail("the saved zombie didn't load");
            return;
        }
        level.addFreshEntity(copy);
        MobEffectInstance rage = copy.getEffect(ModEffects.RAGE.get());
        if (rage == null || !rage.isInfiniteDuration())
        {
            helper.fail("the loaded zombie lost its Rage (or it's no longer for ever)");
        }
        if (!side.equals(Rage.sideOf(copy)))
        {
            helper.fail("the loaded zombie lost its side");
        }
        List<Entity> spawned = List.of(leader, copy, enemy);
        helper.startSequence()
                .thenIdle(GOALS_BACK_TICKS)
                .thenExecute(() -> {
                    if (!RageAi.hasRageGoals(copy))
                    {
                        helper.fail("the loaded zombie's rage goals didn't come back");
                    }
                })
                .thenExecuteFor(WATCH_TICKS, () -> {
                    if (copy.getTarget() == leader || leader.getTarget() == copy)
                    {
                        helper.fail("the loaded zombie and its side's leader targeted each other");
                    }
                })
                .thenExecute(() -> {
                    if (copy.getTarget() != enemy)
                    {
                        helper.fail("the loaded raged zombie should go for its enemy, but its target is " + copy.getTarget());
                    }
                    discard(spawned);
                })
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Helpers
    // ---------------------------------------------------------------------------------------------

    /** A mob of this type standing at {@code at} in the arena (no spawn setup: no gear, never a baby), kept from despawning. */
    private static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type, BlockPos at)
    {
        T mob = type.create(helper.getLevel());
        if (mob == null)
        {
            throw new IllegalStateException("couldn't create a " + EntityType.getKey(type));
        }
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        mob.setPersistenceRequired();
        helper.getLevel().addFreshEntity(mob);
        return mob;
    }

    /**
     * A zombie for the tests: fire resistant (the test world may be in daylight) and never calling reinforcements (on
     * Hard a hurt zombie can call one, which would join the fight).
     */
    private static Zombie zombie(GameTestHelper helper, BlockPos at)
    {
        Zombie zombie = spawn(helper, EntityType.ZOMBIE, at);
        zombie.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, MobEffectInstance.INFINITE_DURATION, 0, false, false));
        AttributeInstance reinforcements = zombie.getAttribute(Attributes.SPAWN_REINFORCEMENTS_CHANCE);
        if (reinforcements != null)
        {
            reinforcements.setBaseValue(0.0D);
        }
        return zombie;
    }

    /** Lots of health, so it lives through the test. */
    private static <T extends LivingEntity> T tough(T mob)
    {
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null)
        {
            health.setBaseValue(TOUGH);
            mob.setHealth(TOUGH);
        }
        return mob;
    }

    /** Removes what a test spawned (so a later test's raged mobs never find them). */
    private static void discard(List<? extends Entity> entities)
    {
        for (Entity entity : new ArrayList<>(entities))
        {
            if (entity != null && !entity.isRemoved())
            {
                entity.discard();
            }
        }
    }
}
