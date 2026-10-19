package com.reg21meme.dross.boss.rimefall;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless tests (GameTest) for Rimefall's animals (CLAUDE.md step 14), in the Cinder Colossus tests' arena
 * ({@link ColossusGameTest#ARENA}), with stand-in players ({@link ColossusGameTest#joinPlayer}, in survival):
 * <ul>
 *   <li>a natural pack of 7 Rimehorns has 2 males, the rest females; two males can't mate, a male and a female can; a
 *       bred calf grows up in {@link RimefallMobTuning#RIMEHORN_GROW_TICKS} and remembers who bred it;</li>
 *   <li>hurting a Rimehorn turns its pack on you, but never the one you bred;</li>
 *   <li>the horn flick throws its victim mostly upwards; from afar it rushes and hits;</li>
 *   <li>a mother moose (her calf near) goes for a player who comes close, a moose without a calf doesn't; the moose's
 *       ram throws its victim mostly back;</li>
 *   <li>the Pond Skater is much faster on ice than off it; it has six leg hitboxes, out where its legs are, and hitting
 *       one hurts it.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class RimefallAnimalsGameTest
{
    private static final String BATCH = "dross_rimefall_animals";
    private static final int TIMEOUT = ColossusGameTest.ARENA_WAIT_TICKS + 300;
    private static final BlockPos MIDDLE = ColossusGameTest.MIDDLE;

    private RimefallAnimalsGameTest() {}

    private static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type, BlockPos at, float yaw)
    {
        ServerLevel level = helper.getLevel();
        T mob = type.create(level);
        if (mob == null)
        {
            throw new GameTestAssertException("couldn't make a " + EntityType.getKey(type));
        }
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        mob.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        mob.setYBodyRot(yaw);
        mob.setYHeadRot(yaw);
        level.addFreshEntity(mob);
        return mob;
    }

    private static ServerPlayer survivalPlayer(GameTestHelper helper, String name, BlockPos at)
    {
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, name, at, 0.0F);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void cleanUp(List<Entity> entities, List<ServerPlayer> players)
    {
        for (Entity entity : entities)
        {
            entity.discard();
        }
        ColossusGameTest.leaveAll(players);
    }

    // ---------------------------------------------------------------- the Rimehorn

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void rimehornPacksAndCalves(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::rimehornPacksAndCalves))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        // A natural pack, the way Minecraft's spawning makes one: one group data shared down the pack.
        SpawnGroupData data = null;
        int males = 0;
        for (int i = 0; i < 7; i++)
        {
            Rimehorn member = ModEntities.RIMEHORN.get().create(level);
            if (member == null)
            {
                throw new GameTestAssertException("couldn't make a Rimehorn");
            }
            member.moveTo(helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE)));
            data = ForgeEventFactory.onFinalizeSpawn(member, level, level.getCurrentDifficultyAt(member.blockPosition()),
                    MobSpawnType.NATURAL, data, null);
            if (member.isMale() != (i < RimefallMobTuning.PACK_MALES))
            {
                throw new GameTestAssertException("pack member " + (i + 1) + " should be " + (i < RimefallMobTuning.PACK_MALES
                        ? "a male" : "a female") + " (the first " + RimefallMobTuning.PACK_MALES + " are males)");
            }
            males += member.isMale() ? 1 : 0;
            member.discard();
        }
        // Breeding: a male and a female, never two males; the calf remembers its breeder.
        List<Entity> made = new ArrayList<>();
        List<ServerPlayer> players = new ArrayList<>();
        ServerPlayer breeder = survivalPlayer(helper, "rimehorn_breeder", MIDDLE.offset(-6, 0, -6));
        players.add(breeder);
        Rimehorn male = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(-4, 0, 0), 0.0F);
        Rimehorn male2 = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(0, 0, 4), 0.0F);
        Rimehorn female = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(4, 0, 0), 0.0F);
        made.addAll(List.of(male, male2, female));
        male.setMale(true);
        male2.setMale(true);
        female.setMale(false);
        for (Rimehorn r : List.of(male, male2, female))
        {
            r.setNoAi(true);
            r.setInLove(breeder);
        }
        if (male.canMate(male2))
        {
            cleanUp(made, players);
            throw new GameTestAssertException("two males could mate");
        }
        if (!male.canMate(female))
        {
            cleanUp(made, players);
            throw new GameTestAssertException("a male and a female in love couldn't mate");
        }
        male.spawnChildFromBreeding(level, female);
        List<Rimehorn> calves = level.getEntitiesOfClass(Rimehorn.class, male.getBoundingBox().inflate(12.0D), Rimehorn::isBaby);
        made.addAll(calves);
        if (calves.size() != 1)
        {
            cleanUp(made, players);
            throw new GameTestAssertException("breeding should make one calf, but there are " + calves.size());
        }
        Rimehorn calf = calves.get(0);
        if (calf.getAge() > -RimefallMobTuning.RIMEHORN_GROW_TICKS + 20)
        {
            cleanUp(made, players);
            throw new GameTestAssertException("a calf should take " + RimefallMobTuning.RIMEHORN_GROW_TICKS
                    + " ticks to grow up, but its age is " + calf.getAge());
        }
        if (!breeder.getUUID().equals(calf.getBreeder()))
        {
            cleanUp(made, players);
            throw new GameTestAssertException("the calf should remember who bred it");
        }
        org.slf4j.LoggerFactory.getLogger(RimefallAnimalsGameTest.class).info(
                "Rimehorn: a pack of 7 has {} males; a male and a female made a calf of age {} (its breeder remembered)",
                males, calf.getAge());
        cleanUp(made, players);
        helper.succeed();
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void rimehornPackDefendsButNeverItsBreeder(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::rimehornPackDefendsButNeverItsBreeder))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivalPlayer(helper, "rimehorn_hunter", MIDDLE.offset(0, 0, -7));
        List<ServerPlayer> players = List.of(player);
        Rimehorn wild = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(-4, 0, 2), 0.0F);
        Rimehorn wild2 = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(0, 0, 5), 0.0F);
        Rimehorn bred = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(4, 0, 2), 0.0F);
        bred.setBreeder(player.getUUID());
        List<Entity> made = List.of(wild, wild2, bred);
        // A few ticks in: a mob hit before its first tick doesn't remember who hit it (vanilla's hurt-by timestamp).
        helper.runAfterDelay(5, () -> wild.hurt(level.damageSources().playerAttack(player), 1.0F));
        helper.runAfterDelay(35, () ->
        {
            try
            {
                if (wild.getTarget() != player)
                {
                    throw new GameTestAssertException("the Rimehorn the player hurt should go for them, but its target is " + wild.getTarget());
                }
                if (wild2.getTarget() != player)
                {
                    throw new GameTestAssertException("its pack should go for the player too, but the other's target is " + wild2.getTarget());
                }
                if (bred.getTarget() == player || bred.isAngryAt(player) || bred.canAttack(player))
                {
                    throw new GameTestAssertException("the Rimehorn the player bred turned on them");
                }
                bred.setTarget(player);
                if (bred.getTarget() != null)
                {
                    throw new GameTestAssertException("the Rimehorn the player bred took them as its target");
                }
            }
            finally
            {
                cleanUp(made, players);
            }
            helper.succeed();
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void rimehornFlickThrowsUp(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::rimehornFlickThrowsUp))
        {
            return;
        }
        Rimehorn rimehorn = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(0, 0, -4), 0.0F);
        Pig pig = spawn(helper, EntityType.PIG, MIDDLE, 0.0F);
        rimehorn.setTarget(pig);
        List<Entity> made = List.of(rimehorn, pig);
        helper.succeedWhen(() ->
        {
            if (pig.hurtTime <= 0)
            {
                throw new GameTestAssertException("the Rimehorn hasn't flicked the pig yet");
            }
            Vec3 v = pig.getDeltaMovement();
            cleanUp(made, List.of());
            if (!(v.y > 0.5D && v.y > v.horizontalDistance()))
            {
                helper.fail("the horn flick should throw its victim mostly upwards, but it moves " + v);
            }
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void rimehornRushesFromAfar(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::rimehornRushesFromAfar))
        {
            return;
        }
        Rimehorn rimehorn = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(0, 0, -6), 0.0F);
        Pig pig = spawn(helper, EntityType.PIG, MIDDLE.offset(0, 0, 4), 0.0F);
        pig.setNoAi(true);
        rimehorn.setTarget(pig);
        List<Entity> made = List.of(rimehorn, pig);
        float before = pig.getHealth();
        helper.succeedWhen(() ->
        {
            if (pig.isAlive() && before - pig.getHealth() < RimefallMobTuning.RUSH_DAMAGE - 0.01F)
            {
                throw new GameTestAssertException("the Rimehorn hasn't rushed into the pig yet");
            }
            cleanUp(made, List.of());
        });
    }

    /**
     * A male and a female in love, a few blocks apart, walk up to each other and make a calf on their own (vanilla's
     * breeding goal wanted them within 3 blocks, which two Rimehorns pushing against each other never reach: the user saw
     * them "struggle to make a calf").
     */
    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void rimehornsBreedOnTheirOwn(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::rimehornsBreedOnTheirOwn))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        ServerPlayer breeder = survivalPlayer(helper, "rimehorn_matchmaker", MIDDLE.offset(0, 0, -10));
        Rimehorn male = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(-5, 0, 0), 90.0F);
        Rimehorn female = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(5, 0, 0), -90.0F);
        male.setMale(true);
        female.setMale(false);
        male.setInLove(breeder);
        female.setInLove(breeder);
        List<Entity> made = new ArrayList<>(List.of(male, female));
        AABB around = new AABB(helper.absolutePos(MIDDLE)).inflate(20.0D);
        helper.succeedWhen(() ->
        {
            List<Rimehorn> calves = level.getEntitiesOfClass(Rimehorn.class, around, Rimehorn::isBaby);
            if (calves.isEmpty())
            {
                throw new GameTestAssertException("the male and the female in love haven't made a calf yet (they're "
                        + String.format(java.util.Locale.ROOT, "%.1f", male.distanceTo(female)) + " blocks apart; they mate within "
                        + String.format(java.util.Locale.ROOT, "%.1f", male.mateReach()) + ")");
            }
            made.addAll(calves);
            cleanUp(made, List.of(breeder));
        });
    }

    /**
     * Its face, chest and rump are hitboxes of their own (the user: the hitbox "for the front of his face is off"): the
     * face's sits about 4 blocks in front of it, out of its main hitbox, turned with it, and hitting it hurts the Rimehorn.
     * The moose's head likewise.
     */
    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void rimehornAndMooseHeadsAreHitboxes(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::rimehornAndMooseHeadsAreHitboxes))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        Rimehorn rimehorn = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(-4, 0, -5), 0.0F);
        Moose moose = spawn(helper, ModEntities.MOOSE.get(), MIDDLE.offset(4, 0, -5), 0.0F);
        rimehorn.setNoAi(true);
        moose.setNoAi(true);
        List<Entity> made = List.of(rimehorn, moose);
        helper.runAfterDelay(3, () ->
        {
            try
            {
                checkHead(level, rimehorn, rimehorn.getBodyParts()[0], 3.5D, "the Rimehorn's face");
                checkHead(level, moose, moose.getBodyParts()[0], 2.0D, "the moose's head");
            }
            finally
            {
                cleanUp(made, List.of());
            }
            helper.succeed();
        });
    }

    /** The part sits at least {@code ahead} blocks in front of the animal (it faces +z), out of its main hitbox, and hitting it hurts the animal. */
    private static void checkHead(ServerLevel level, Mob animal, RimefallBodyPart<?> part, double ahead, String what)
    {
        double dz = part.getZ() - animal.getZ();
        double dx = part.getX() - animal.getX();
        if (dz < ahead || Math.abs(dx) > 0.3D || animal.getBoundingBox().contains(part.position().add(0.0D, 0.5D, 0.0D)))
        {
            throw new GameTestAssertException(what + " hitbox should be " + ahead + "+ blocks in front of it, out of its main hitbox, but it's "
                    + String.format(java.util.Locale.ROOT, "%.2f ahead and %.2f to the side", dz, dx));
        }
        float before = animal.getHealth();
        part.hurt(level.damageSources().generic(), 4.0F);
        if (animal.getHealth() >= before)
        {
            throw new GameTestAssertException("hitting " + what + " didn't hurt it");
        }
    }

    /**
     * A Rimehorn doesn't chase you for ever (the user: hit a calf and the herd "keeps chasing me across the biome"): its
     * anger runs out while it chases (here 2 seconds' worth), and it gives up once it's
     * {@link RimefallMobTuning#RIMEHORN_CHASE_LEASH} blocks from where the chase began.
     */
    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void rimehornGivesUpTheChase(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::rimehornGivesUpTheChase))
        {
            return;
        }
        ServerPlayer player = survivalPlayer(helper, "rimehorn_runner", MIDDLE.offset(0, 0, 7));
        player.setInvulnerable(true);
        Rimehorn calm = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(-4, 0, -5), 0.0F);
        Rimehorn far = spawn(helper, ModEntities.RIMEHORN.get(), MIDDLE.offset(4, 0, -5), 0.0F);
        List<Entity> made = List.of(calm, far);
        for (Rimehorn r : List.of(calm, far))
        {
            r.setTarget(player);
            r.setPersistentAngerTarget(player.getUUID());
        }
        calm.setRemainingPersistentAngerTime(40);
        far.setRemainingPersistentAngerTime(20 * 60);
        Vec3 farHome = far.position();
        helper.runAfterDelay(5, () -> far.moveTo(farHome.x + RimefallMobTuning.RIMEHORN_CHASE_LEASH + 2.0D, farHome.y, farHome.z));
        helper.runAfterDelay(8, () ->
        {
            boolean farGaveUp = far.getTarget() == null && !far.isAngryAt(player);
            far.moveTo(farHome.x, farHome.y, farHome.z);
            if (!farGaveUp)
            {
                cleanUp(made, List.of(player));
                helper.fail("a Rimehorn " + Math.round(RimefallMobTuning.RIMEHORN_CHASE_LEASH + 2.0D)
                        + " blocks from where its chase began should give up, but it's still after the player");
            }
        });
        helper.runAfterDelay(80, () ->
        {
            boolean calmed = calm.getTarget() == null && !calm.isAngryAt(player);
            cleanUp(made, List.of(player));
            if (!calmed)
            {
                helper.fail("a Rimehorn's anger should run out while it chases (2 seconds' worth here), but after 4 seconds it's still after the player");
            }
            helper.succeed();
        });
    }

    // ---------------------------------------------------------------- the moose

    /** A bull's ram hurts more than a cow's (the user's request): each rams a pig; the bull's takes more off it. */
    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void mooseBullHitsHarder(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::mooseBullHitsHarder))
        {
            return;
        }
        Moose bull = spawn(helper, ModEntities.MOOSE.get(), MIDDLE.offset(-6, 0, -3), 0.0F);
        bull.setBull(true);
        Moose cow = spawn(helper, ModEntities.MOOSE.get(), MIDDLE.offset(6, 0, -3), 0.0F);
        cow.setBull(false);
        Pig forBull = spawn(helper, EntityType.PIG, MIDDLE.offset(-6, 0, 1), 0.0F);
        Pig forCow = spawn(helper, EntityType.PIG, MIDDLE.offset(6, 0, 1), 0.0F);
        forBull.setNoAi(true);
        forCow.setNoAi(true);
        bull.setTarget(forBull);
        cow.setTarget(forCow);
        float full = forBull.getHealth();
        List<Entity> made = List.of(bull, cow, forBull, forCow);
        helper.succeedWhen(() ->
        {
            if (forBull.hurtTime <= 0 && forBull.isAlive() || forCow.hurtTime <= 0 && forCow.isAlive())
            {
                throw new GameTestAssertException("the bull and the cow haven't both rammed their pig yet");
            }
            float byBull = full - forBull.getHealth();
            float byCow = full - forCow.getHealth();
            cleanUp(made, List.of());
            if (!(byBull > byCow) || Math.abs(byCow - RimefallMobTuning.RAM_DAMAGE_COW) > 0.01F)
            {
                helper.fail("a bull's ram should hurt more than a cow's (" + RimefallMobTuning.RAM_DAMAGE_BULL + " and "
                        + RimefallMobTuning.RAM_DAMAGE_COW + "), but the bull's took " + byBull + " and the cow's " + byCow);
            }
        });
    }

    /**
     * A mother goes for a player near her calf, but gives up once she's {@link RimefallMobTuning#MOTHER_LEASH} blocks
     * from it (the user: she "leaves the calf"), and doesn't go for them again while they keep away.
     */
    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT + 200)
    public static void mooseMotherKeepsToHerCalf(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::mooseMotherKeepsToHerCalf))
        {
            return;
        }
        // (the arena is 17 blocks across: corner to corner)
        Moose mother = spawn(helper, ModEntities.MOOSE.get(), MIDDLE.offset(-5, 0, -7), 0.0F);
        mother.setBull(false);
        Moose calf = spawn(helper, ModEntities.MOOSE.get(), MIDDLE.offset(-7, 0, -7), 0.0F);
        calf.setBaby(true);
        calf.setNoAi(true);
        ServerPlayer player = survivalPlayer(helper, "moose_trespasser", MIDDLE.offset(-2, 0, -7));
        List<Entity> made = List.of(mother, calf);
        // (about 20 from her calf, within her follow range: without the leash she'd chase them all the way)
        Vec3 far = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(7, 0, 7)));
        boolean[] chased = {false};
        int[] calm = {0};
        helper.onEachTick(() ->
        {
            if (!chased[0])
            {
                if (mother.getTarget() == player)
                {
                    chased[0] = true;
                    player.moveTo(far.x, far.y, far.z);       // run off, far from her calf
                }
                return;
            }
            if (mother.getTarget() == null && mother.distanceTo(calf) <= RimefallMobTuning.MOTHER_LEASH + 2.0D)
            {
                calm[0]++;
            }
            else
            {
                calm[0] = 0;
            }
        });
        helper.succeedWhen(() ->
        {
            if (!chased[0])
            {
                throw new GameTestAssertException("the mother hasn't gone for the player beside her calf yet");
            }
            if (mother.distanceTo(calf) > RimefallMobTuning.MOTHER_LEASH + 3.0D)
            {
                cleanUp(made, List.of(player));
                helper.fail("the mother ran " + Math.round(mother.distanceTo(calf)) + " blocks from her calf (she should give up at "
                        + RimefallMobTuning.MOTHER_LEASH + ")");
            }
            if (calm[0] < 40)
            {
                throw new GameTestAssertException("the mother is still after the player (or has just stopped)");
            }
            cleanUp(made, List.of(player));
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void mooseMotherAttacks(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::mooseMotherAttacks))
        {
            return;
        }
        Moose mother = spawn(helper, ModEntities.MOOSE.get(), MIDDLE.offset(-3, 0, 0), 0.0F);
        mother.setBull(false);
        Moose calf = spawn(helper, ModEntities.MOOSE.get(), MIDDLE.offset(-5, 0, 3), 0.0F);
        calf.setBaby(true);
        ServerPlayer player = survivalPlayer(helper, "moose_visitor", MIDDLE.offset(1, 0, 0));   // (4 from her: within MOTHER_NOTICE_RANGE)
        List<Entity> made = List.of(mother, calf);
        helper.succeedWhen(() ->
        {
            if (mother.getTarget() != player)
            {
                throw new GameTestAssertException("the mother hasn't gone for the player near her calf yet");
            }
            cleanUp(made, List.of(player));
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void mooseAloneLeavesYouBe(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::mooseAloneLeavesYouBe))
        {
            return;
        }
        Moose cow = spawn(helper, ModEntities.MOOSE.get(), MIDDLE.offset(-3, 0, 0), 0.0F);
        cow.setBull(false);
        Moose bull = spawn(helper, ModEntities.MOOSE.get(), MIDDLE.offset(-3, 0, 5), 0.0F);
        bull.setBull(true);
        ServerPlayer player = survivalPlayer(helper, "moose_walker", MIDDLE.offset(3, 0, 2));
        List<Entity> made = List.of(cow, bull);
        helper.runAfterDelay(120, () ->
        {
            boolean angry = cow.getTarget() == player || bull.getTarget() == player;
            cleanUp(made, List.of(player));
            if (angry)
            {
                helper.fail("a moose without a calf went for a player who did nothing to it");
            }
            helper.succeed();
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void mooseRamThrowsBack(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::mooseRamThrowsBack))
        {
            return;
        }
        Moose bull = spawn(helper, ModEntities.MOOSE.get(), MIDDLE.offset(0, 0, -3), 0.0F);
        bull.setBull(true);
        Pig pig = spawn(helper, EntityType.PIG, MIDDLE.offset(0, 0, 1), 0.0F);
        bull.setTarget(pig);
        List<Entity> made = List.of(bull, pig);
        helper.succeedWhen(() ->
        {
            if (pig.hurtTime <= 0)
            {
                throw new GameTestAssertException("the moose hasn't rammed the pig yet");
            }
            Vec3 v = pig.getDeltaMovement();
            cleanUp(made, List.of());
            if (!(v.horizontalDistance() > 0.6D && v.horizontalDistance() > v.y))
            {
                helper.fail("the ram should throw its victim back more than up, but it moves " + v);
            }
        });
    }

    // ---------------------------------------------------------------- the Pond Skater

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void pondSkaterSkatesOnIce(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::pondSkaterSkatesOnIce))
        {
            return;
        }
        BlockPos floor = MIDDLE.below();
        helper.setBlock(floor, Blocks.PACKED_ICE);
        PondSkater skater = spawn(helper, ModEntities.POND_SKATER.get(), MIDDLE, 0.0F);
        skater.setNoAi(true);
        // (a monster far from the nearest player despawns: another test's player may be online somewhere far off)
        skater.setPersistenceRequired();
        double base = RimefallMobTuning.SKATER_SPEED;
        helper.runAfterDelay(10, () ->
        {
            double onIce = skater.getAttributeValue(Attributes.MOVEMENT_SPEED);
            if (!skater.isOnIce() || onIce < base * (1.0D + RimefallMobTuning.SKATER_ICE_SPEED_BONUS) - 0.01D)
            {
                skater.discard();
                helper.fail("on packed ice its speed should be " + base * (1.0D + RimefallMobTuning.SKATER_ICE_SPEED_BONUS)
                        + ", but it's " + onIce + (skater.isOnIce() ? "" : " (it doesn't know it's on ice)"));
                return;
            }
            // Off the ice: the floor under it turned to stone, and it put back on that block's middle (on ice the
            // least nudge slides it a long way; with no AI it never steps back).
            helper.setBlock(floor, Blocks.STONE);
            BlockPos at = helper.absolutePos(floor);
            skater.setDeltaMovement(net.minecraft.world.phys.Vec3.ZERO);
            skater.teleportTo(at.getX() + 0.5D, at.getY() + 1.0D, at.getZ() + 0.5D);
            helper.runAfterDelay(10, () ->
            {
                double off = skater.getAttributeValue(Attributes.MOVEMENT_SPEED);
                String under = skater.level().getBlockState(BlockPos.containing(skater.getX(), skater.getY() - 0.2D,
                        skater.getZ())).getBlock().getName().getString();
                skater.discard();
                if (Math.abs(off - base) > 0.01D)
                {
                    helper.fail("off the ice its speed should be a spider's, " + base + ", but it's " + off
                            + " (standing on " + under + ")");
                    return;
                }
                helper.succeed();
            });
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void pondSkaterLegsAreHitboxes(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::pondSkaterLegsAreHitboxes))
        {
            return;
        }
        PondSkater skater = spawn(helper, ModEntities.POND_SKATER.get(), MIDDLE, 0.0F);
        skater.setNoAi(true);
        helper.runAfterDelay(3, () ->
        {
            try
            {
                PondSkaterPart[] legs = skater.getLegs();
                if (legs.length != 6 || skater.getParts().length != 6 || !skater.isMultipartEntity())
                {
                    throw new GameTestAssertException("it should have six leg hitboxes, but has " + legs.length);
                }
                // The middle legs (the oars) are about 3.6 blocks out to each side.
                for (int i = 0; i < 2; i++)
                {
                    double out = Math.hypot(legs[i].getX() - skater.getX(), legs[i].getZ() - skater.getZ());
                    if (Math.abs(out - 3.6D) > 0.3D)
                    {
                        throw new GameTestAssertException("a middle leg's hitbox should be about 3.6 blocks out, but it's " + out);
                    }
                }
                AABB box = legs[0].getBoundingBox();
                if (box.getXsize() > 1.0D || box.getYsize() > 1.2D)
                {
                    throw new GameTestAssertException("a leg's hitbox should be small, but it's " + box);
                }
                float before = skater.getHealth();
                legs[2].hurt(helper.getLevel().damageSources().generic(), 4.0F);
                if (before - skater.getHealth() < 3.99F)
                {
                    throw new GameTestAssertException("hitting a leg should hurt it (4), but its health went from " + before
                            + " to " + skater.getHealth());
                }
            }
            finally
            {
                skater.discard();
            }
            helper.succeed();
        });
    }

    /**
     * Pond Skaters spawn only on ice big enough for them (the user: they saw 10 on one small pond; "one that's 10 blocks
     * shouldn't even get one"): a frozen pond 31 across may get one, a 9 across one none, and a big pond with one on it
     * already none more. (Rimefall's big lakes, 2 each, are checked in RimefallLakeGameTest.) Its own batch: the ponds
     * are built over the arena, a little wider than it.
     */
    @GameTest(template = ColossusGameTest.ARENA, batch = "dross_rimefall_ponds", timeoutTicks = TIMEOUT)
    public static void pondSkatersNeedABigPond(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, RimefallAnimalsGameTest::pondSkatersNeedABigPond))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        BlockPos big = helper.absolutePos(MIDDLE.above(25));
        BlockPos small = helper.absolutePos(MIDDLE.above(45));
        BlockPos patch = helper.absolutePos(MIDDLE.above(65));
        BlockPos littlePatch = helper.absolutePos(MIDDLE.above(85));
        List<BlockPos> built = new ArrayList<>();
        buildPond(level, big, 15, built);
        buildPond(level, small, 4, built);
        buildIcePatch(level, patch, 10, built);          // 21 across, packed ice on stone: no water
        buildIcePatch(level, littlePatch, 7, built);     // 15 across
        PondSkater skater = null;
        try
        {
            boolean onPatch = com.reg21meme.dross.dimension.rimefall.RimefallAreas.skaterMayAppear(level, patch.above(2));
            boolean onLittlePatch = com.reg21meme.dross.dimension.rimefall.RimefallAreas.skaterMayAppear(level, littlePatch.above(2));
            if (!onPatch || onLittlePatch)
            {
                throw new GameTestAssertException("a Pond Skater should spawn on a patch of packed ice 21 across (it may: " + onPatch
                        + "), not on one 15 across (it may: " + onLittlePatch + ")");
            }
            boolean onSmall = com.reg21meme.dross.dimension.rimefall.RimefallAreas.skaterMayAppear(level, small.above(2));
            boolean onBig = com.reg21meme.dross.dimension.rimefall.RimefallAreas.skaterMayAppear(level, big.above(2));
            skater = ModEntities.POND_SKATER.get().create(level);
            if (skater == null)
            {
                throw new GameTestAssertException("couldn't make a Pond Skater");
            }
            skater.setNoAi(true);
            skater.moveTo(big.getX() + 0.5D, big.getY() + 2, big.getZ() + 0.5D, 0.0F, 0.0F);
            level.addFreshEntity(skater);
            boolean onBigAgain = com.reg21meme.dross.dimension.rimefall.RimefallAreas.skaterMayAppear(level, big.offset(6, 2, 6));
            if (onSmall || !onBig || onBigAgain)
            {
                throw new GameTestAssertException("a Pond Skater should spawn on a frozen pond 31 across (it may: " + onBig
                        + "), not on one 9 across (it may: " + onSmall + "), and not on the big one once one is on it (it may: " + onBigAgain + ")");
            }
        }
        finally
        {
            if (skater != null)
            {
                skater.discard();
            }
            for (BlockPos pos : built)
            {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), 2 | 16);
            }
        }
        helper.succeed();
    }

    /** A patch of packed ice, {@code 2 * half + 1} across, on stone, its top one above {@code centre} (like a pond's ice). */
    private static void buildIcePatch(ServerLevel level, BlockPos centre, int half, List<BlockPos> built)
    {
        for (int dx = -half - 1; dx <= half + 1; dx++)
        {
            for (int dz = -half - 1; dz <= half + 1; dz++)
            {
                boolean rim = Math.abs(dx) > half || Math.abs(dz) > half;
                BlockPos column = centre.offset(dx, 0, dz);
                for (int dy = 0; dy <= 3; dy++)
                {
                    BlockPos pos = column.above(dy);
                    // (open air over it: it's built in the rock round the arena)
                    net.minecraft.world.level.block.state.BlockState state = dy >= 2 ? Blocks.AIR.defaultBlockState()
                            : rim || dy == 0 ? Blocks.STONE.defaultBlockState() : Blocks.PACKED_ICE.defaultBlockState();
                    level.setBlock(pos, state, 2 | 16);
                    built.add(pos);
                }
            }
        }
    }

    /** A frozen pond: ice over water, {@code 2 * half + 1} across, in a stone basin round {@code centre} (the water's height). */
    private static void buildPond(ServerLevel level, BlockPos centre, int half, List<BlockPos> built)
    {
        for (int dx = -half - 1; dx <= half + 1; dx++)
        {
            for (int dz = -half - 1; dz <= half + 1; dz++)
            {
                boolean rim = Math.abs(dx) > half || Math.abs(dz) > half;
                BlockPos column = centre.offset(dx, 0, dz);
                for (int dy = -1; dy <= 3; dy++)
                {
                    BlockPos pos = column.above(dy);
                    // (open air over it: it's built in the rock round the arena)
                    net.minecraft.world.level.block.state.BlockState state = dy >= 2 ? Blocks.AIR.defaultBlockState()
                            : rim || dy == -1 ? Blocks.STONE.defaultBlockState()
                            : dy == 0 ? Blocks.WATER.defaultBlockState() : Blocks.ICE.defaultBlockState();
                    level.setBlock(pos, state, 2 | 16);
                    built.add(pos);
                }
            }
        }
    }
}
