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

import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.ARENA_WAIT_TICKS;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.MIDDLE;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.joinPlayer;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.leaveAll;
import static com.reg21meme.dross.boss.golem.ColossusGameTest.waitForArena;

/**
 * Headless tests (GameTests) for the Rage Wand and its Rage ({@code .\gradlew.bat runGameTestServer}), on the Cinder
 * Colossus tests' flat arena ({@code ColossusGameTest.ARENA}). Each runs in its own batch (raged mobs look for enemies
 * 40 blocks round, so two tests side by side would fight each other), and removes what it spawned when it's done.
 * <ul>
 *   <li>{@link #wandCyclesTeamOnUse}: right-clicking with the wand moves it Blue, Red, Green, Yellow, Purple, Blue
 *       (saved on the item; a new wand is Blue), and its model value follows.</li>
 *   <li>{@link #mobTakesTheWandsTeamAndSwitches}: a hit rages a mob onto the wand's team; hit again with another team,
 *       it switches (red, then purple: purple).</li>
 *   <li>{@link #teamsFightButNeverTheirOwn}: two blue zombies and two red ones: nobody ever targets a teammate, each
 *       goes for one of the other team, and a teammate's blow does no damage.</li>
 *   <li>{@link #lastTeamStandingKeepsItsRage}: the last team standing keeps its Rage, its team and its rage goals well
 *       after the fight (they never calm down).</li>
 *   <li>{@link #ragedZombieIgnoresPlayers}: a raged zombie next to a player (who even hits it) never targets the
 *       player while a raged zombie of another team is near, and goes for that one; a zombie that isn't raged does go
 *       for the player.</li>
 *   <li>{@link #wandHitDoesNoDamageAndRagesForever}: the wand's hit does no damage and no knockback and gives Rage for
 *       ever (its swirl hidden) and the wand's team; hitting a player does nothing (no damage, and players can't have
 *       the Rage); a frozen (no AI) mob can't be raged.</li>
 *   <li>{@link #milkDoesNotCureRage}: milk takes other effects away, not the Rage (nor the team).</li>
 *   <li>{@link #ragedPigHeadbuttsRagedCow}: a raged pig and a raged cow on two teams (no attack of their own) headbutt
 *       each other, 2 damage a time.</li>
 *   <li>{@link #ragedVillagerHeadbutts}: a raged villager (a brain-based mob) headbutts a raged zombie of another
 *       team.</li>
 *   <li>{@link #ragedColossusGolemsJoinItsTeam}: a raged Colossus's golems (one already alive, one summoned after)
 *       join its team and never target it or each other, and it and they go for the raged enemy.</li>
 *   <li>{@link #ragedRatKingWakesAndHisRatsJoinHim}: raging a sleeping Rat King wakes him; his new rat joins his team
 *       and never targets him, and goes for the raged enemy.</li>
 *   <li>{@link #rageSurvivesSaveAndLoad}: a raged zombie saved and loaded again (NBT) keeps its Rage (for ever) and its
 *       team, its rage goals come back, and it goes for its enemy, never its own team.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RageGameTest
{
    /** The longest a team fight may take to finish (ticks). */
    private static final int TEAM_FIGHT_TICKS = 3000;
    /** After the last enemy died, the winners are checked to still be raged on their team this long after (ticks). */
    private static final int STILL_RAGED_AFTER = 300;
    /** Ticks a test watches who targets whom. */
    private static final int WATCH_TICKS = 100;
    /** The bosses' golems and rats climb out of the ground first; they're watched this long. */
    private static final int BOSS_WATCH_TICKS = 140;
    /** Health given to mobs that should stay alive through a test, and to the losing team's (so it loses quickly). */
    private static final float TOUGH = 400.0F;
    private static final float WEAK = 6.0F;
    /** The longest a headbutt test waits for the first headbutt (ticks). */
    private static final int HEADBUTT_WAIT = 400;
    private static final float HEALTH_TOLERANCE = 0.01F;
    /** The goals of a loaded raged mob must be back within this many ticks. */
    private static final int GOALS_BACK_TICKS = 5;

    private RageGameTest() {}

    // ---------------------------------------------------------------------------------------------
    // The wand's teams
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_rage_wand_teams", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void wandCyclesTeamOnUse(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::wandCyclesTeamOnUse))
        {
            return;
        }
        Player holder = helper.makeMockPlayer();
        ItemStack wand = new ItemStack(ModItems.RAGE_WAND.get());
        holder.setItemInHand(InteractionHand.MAIN_HAND, wand);
        if (RageWandItem.teamOf(wand) != RageTeam.BLUE)
        {
            helper.fail("a new wand should be Blue, but it's " + RageWandItem.teamOf(wand));
            return;
        }
        RageTeam[] expected = {RageTeam.RED, RageTeam.GREEN, RageTeam.YELLOW, RageTeam.PURPLE, RageTeam.BLUE};
        for (RageTeam team : expected)
        {
            ItemStack held = holder.getItemInHand(InteractionHand.MAIN_HAND);
            held.getItem().use(helper.getLevel(), holder, InteractionHand.MAIN_HAND);
            RageTeam now = RageWandItem.teamOf(holder.getItemInHand(InteractionHand.MAIN_HAND));
            if (now != team)
            {
                helper.fail("right-clicking should move the wand on to " + team + ", but it's " + now);
                return;
            }
            if (!holder.getItemInHand(InteractionHand.MAIN_HAND).getOrCreateTag().getString(RageWandItem.TAG_TEAM).equals(team.id()))
            {
                helper.fail("the wand's team should be saved on the item as " + team.id());
                return;
            }
        }
        float last = -1.0F;
        for (RageTeam team : RageTeam.values())
        {
            if (team.modelValue() <= last)
            {
                helper.fail("each team's model value should be bigger than the last (" + team + ": " + team.modelValue() + ")");
                return;
            }
            last = team.modelValue();
        }
        helper.succeed();
    }

    @GameTest(template = ARENA, batch = "dross_rage_switch_team", timeoutTicks = ARENA_WAIT_TICKS + 100)
    public static void mobTakesTheWandsTeamAndSwitches(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::mobTakesTheWandsTeamAndSwitches))
        {
            return;
        }
        Zombie zombie = zombie(helper, MIDDLE);
        Player holder = holder(helper, RageTeam.RED);
        holder.attack(zombie);
        String problem = null;
        if (!Rage.isRaged(zombie) || Rage.teamOf(zombie) != RageTeam.RED)
        {
            problem = "a red wand's hit should rage the zombie onto Red, but it's on " + Rage.teamOf(zombie);
        }
        RageWandItem.setTeam(holder.getMainHandItem(), RageTeam.PURPLE);
        holder.attack(zombie);
        if (problem == null && (!Rage.isRaged(zombie) || Rage.teamOf(zombie) != RageTeam.PURPLE))
        {
            problem = "hit again with a purple wand, the zombie should switch to Purple, but it's on " + Rage.teamOf(zombie);
        }
        discard(List.of(zombie));
        if (problem != null)
        {
            helper.fail(problem);
            return;
        }
        helper.succeed();
    }

    // ---------------------------------------------------------------------------------------------
    // Teams fight, never their own; the last team standing stays raged
    // ---------------------------------------------------------------------------------------------

    @GameTest(template = ARENA, batch = "dross_rage_teams", timeoutTicks = ARENA_WAIT_TICKS + WATCH_TICKS + 100)
    public static void teamsFightButNeverTheirOwn(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::teamsFightButNeverTheirOwn))
        {
            return;
        }
        List<Zombie> blue = List.of(tough(zombie(helper, MIDDLE.offset(-4, 0, -2))), tough(zombie(helper, MIDDLE.offset(-4, 0, 2))));
        List<Zombie> red = List.of(tough(zombie(helper, MIDDLE.offset(4, 0, -2))), tough(zombie(helper, MIDDLE.offset(4, 0, 2))));
        blue.forEach(z -> Rage.enrage(z, RageTeam.BLUE));
        red.forEach(z -> Rage.enrage(z, RageTeam.RED));
        List<Zombie> all = new ArrayList<>(blue);
        all.addAll(red);
        // A teammate's blow does no damage (no friendly fire).
        Zombie a = blue.get(0);
        Zombie b = blue.get(1);
        float before = b.getHealth();
        b.invulnerableTime = 0;
        a.doHurtTarget(b);
        if (Math.abs(b.getHealth() - before) > HEALTH_TOLERANCE)
        {
            discard(all);
            helper.fail("a raged zombie's blow hurt a teammate (" + before + " -> " + b.getHealth() + ")");
            return;
        }
        if (!Rage.areEnemies(a, red.get(0)) || Rage.areEnemies(a, b) || !Rage.areTeammates(a, b))
        {
            discard(all);
            helper.fail("two teams should be enemies, one team teammates");
            return;
        }
        helper.startSequence()
                .thenExecuteFor(WATCH_TICKS, () -> {
                    for (Zombie z : all)
                    {
                        if (Rage.areTeammates(z, z.getTarget()))
                        {
                            helper.fail("a raged zombie targeted its own teammate (" + Rage.teamOf(z) + ")");
                        }
                    }
                })
                .thenExecute(() -> {
                    for (Zombie z : all)
                    {
                        if (!Rage.areEnemies(z, z.getTarget()))
                        {
                            helper.fail("each raged zombie should go for one of the other team, but a " + Rage.teamOf(z)
                                    + " one has " + z.getTarget());
                        }
                    }
                    discard(all);
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_rage_last_team", timeoutTicks = ARENA_WAIT_TICKS + TEAM_FIGHT_TICKS + STILL_RAGED_AFTER + 200)
    public static void lastTeamStandingKeepsItsRage(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::lastTeamStandingKeepsItsRage))
        {
            return;
        }
        List<Zombie> winners = List.of(tough(zombie(helper, MIDDLE.offset(-3, 0, -2))), tough(zombie(helper, MIDDLE.offset(-3, 0, 2))));
        Zombie loser = weak(zombie(helper, MIDDLE.offset(4, 0, 0)));
        winners.forEach(z -> Rage.enrage(z, RageTeam.GREEN));
        Rage.enrage(loser, RageTeam.YELLOW);
        List<Zombie> all = List.of(winners.get(0), winners.get(1), loser);
        long start = helper.getTick();
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (loser.isAlive() && helper.getTick() - start < TEAM_FIGHT_TICKS)
                    {
                        helper.fail("the yellow zombie is still standing");
                    }
                })
                .thenExecute(() -> {
                    if (loser.isAlive())
                    {
                        helper.fail("the green team should have beaten the yellow zombie by now");
                    }
                })
                .thenIdle(STILL_RAGED_AFTER)
                .thenExecute(() -> {
                    for (Zombie z : winners)
                    {
                        if (!z.isAlive() || !Rage.isRaged(z) || Rage.teamOf(z) != RageTeam.GREEN || !RageAi.hasRageGoals(z))
                        {
                            helper.fail(STILL_RAGED_AFTER + " ticks after the fight a winner should still be raged on Green "
                                    + "with its rage goals (raged " + Rage.isRaged(z) + ", team " + Rage.teamOf(z) + ")");
                        }
                    }
                    discard(all);
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
        Rage.enrage(near, RageTeam.BLUE);
        Rage.enrage(far, RageTeam.RED);
        near.hurt(player.damageSources().playerAttack(player), 1.0F); // the player even hits it
        boolean[] controlWentForPlayer = {false};
        List<Entity> spawned = List.of(near, far, control);
        helper.startSequence()
                .thenExecuteFor(WATCH_TICKS, () -> {
                    if (near.getTarget() instanceof Player || far.getTarget() instanceof Player)
                    {
                        helper.fail("a raged zombie went for the player while a raged zombie of another team was near");
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
        if (Rage.teamOf(zombie) != RageTeam.DEFAULT)
        {
            helper.fail("a new wand's hit should put the zombie on " + RageTeam.DEFAULT + ", but it's on " + Rage.teamOf(zombie));
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
        Rage.enrage(zombie, RageTeam.RED);
        zombie.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 600));
        zombie.curePotionEffects(new ItemStack(Items.MILK_BUCKET));
        if (zombie.hasEffect(MobEffects.MOVEMENT_SLOWDOWN))
        {
            helper.fail("milk didn't take Slowness away (so the test proves nothing)");
        }
        if (!Rage.isRaged(zombie) || Rage.teamOf(zombie) != RageTeam.RED)
        {
            helper.fail("milk cured the Rage (or took its team)");
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
        Rage.enrage(pig, RageTeam.BLUE);
        Rage.enrage(cow, RageTeam.RED);
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
        Rage.enrage(villager, RageTeam.BLUE);
        Rage.enrage(zombie, RageTeam.RED);
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
    public static void ragedColossusGolemsJoinItsTeam(GameTestHelper helper)
    {
        if (waitForArena(helper, RageGameTest::ragedColossusGolemsJoinItsTeam))
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
        Rage.enrage(enemy, RageTeam.BLUE);
        if (early == null || Rage.isRaged(early))
        {
            helper.fail("the golem summoned before the Rage should exist and not be raged yet");
            return;
        }
        Rage.enrage(golem, RageTeam.RED);
        GolemMinion fresh = GolemMinion.summon(ModEntities.STONE_GOLEM.get(), level, golem,
                helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(5, 0, 0))), 0.0F);
        if (fresh == null)
        {
            helper.fail("couldn't summon a golem for the raged Colossus");
            return;
        }
        for (GolemMinion minion : List.of(early, fresh))
        {
            if (!Rage.isRaged(minion) || Rage.teamOf(minion) != RageTeam.RED)
            {
                helper.fail("the Colossus's golem should be raged on its team, Red (raged " + Rage.isRaged(minion) + ", team "
                        + Rage.teamOf(minion) + ")");
            }
        }
        if (Rage.areEnemies(golem, early) || Rage.areEnemies(early, fresh) || !Rage.areEnemies(golem, enemy))
        {
            helper.fail("the Colossus and its golems should be one team, the zombie their enemy");
        }
        List<Entity> spawned = List.of(golem, early, fresh, enemy);
        helper.startSequence()
                .thenExecuteFor(BOSS_WATCH_TICKS, () -> {
                    for (GolemMinion minion : List.of(early, fresh))
                    {
                        if (minion.getTarget() == golem || minion.getTarget() instanceof GolemMinion)
                        {
                            helper.fail("a raged Colossus's golem targeted its master or another golem of its team");
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
        Rage.enrage(enemy, RageTeam.YELLOW);
        Rage.enrage(king, RageTeam.GREEN);
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
        if (!Rage.isRaged(rat) || Rage.teamOf(rat) != RageTeam.GREEN)
        {
            helper.fail("the raged Rat King's new rat should be raged on his team, Green (it's on " + Rage.teamOf(rat) + ")");
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
        Rage.enrage(leader, RageTeam.PURPLE);
        Rage.join(member, RageTeam.PURPLE);
        Rage.enrage(enemy, RageTeam.YELLOW);

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
        if (Rage.teamOf(copy) != RageTeam.PURPLE)
        {
            helper.fail("the loaded zombie lost its team (it's on " + Rage.teamOf(copy) + ")");
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
                        helper.fail("the loaded zombie and its teammate targeted each other");
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
        return withHealth(mob, TOUGH);
    }

    /** Little health, so it loses quickly. */
    private static <T extends LivingEntity> T weak(T mob)
    {
        return withHealth(mob, WEAK);
    }

    private static <T extends LivingEntity> T withHealth(T mob, float amount)
    {
        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null)
        {
            health.setBaseValue(amount);
            mob.setHealth(amount);
        }
        return mob;
    }

    /** A creative stand-in holding a Rage Wand on this team, next to the arena's middle. */
    private static Player holder(GameTestHelper helper, RageTeam team)
    {
        Player holder = helper.makeMockPlayer(); // (a creative mock player: zombies leave him alone)
        Vec3 at = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(-2, 0, 0)));
        holder.moveTo(at.x, at.y, at.z, -90.0F, 0.0F);
        ItemStack wand = new ItemStack(ModItems.RAGE_WAND.get());
        RageWandItem.setTeam(wand, team);
        holder.setItemInHand(InteractionHand.MAIN_HAND, wand);
        return holder;
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
