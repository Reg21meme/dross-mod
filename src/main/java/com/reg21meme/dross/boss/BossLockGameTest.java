package com.reg21meme.dross.boss;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.CinderColossus;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.boss.lair.LairKeeper;
import com.reg21meme.dross.boss.ratking.RatKing;
import com.reg21meme.dross.boss.ratking.lair.RatKingLairKeeper;
import com.reg21meme.dross.enchant.RisenUndead;
import com.reg21meme.dross.quest.oracle.OracleBoss;
import com.reg21meme.dross.quest.oracle.OracleDialogue;
import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.quest.oracle.OracleRules;
import com.reg21meme.dross.quest.oracle.OracleState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * Headless checks of the locked bosses and the arena wards ({@link BossLocks}, {@link BossWards}; QUEST_PLAN.md,
 * "Locked bosses"). The other bosses' own checks are in {@code lair/LairLockGameTest}, {@code huntsman/HuntLockGameTest},
 * {@code emberdactyl/EmberLockGameTest} and {@code ratking/lair/RatCaveLockGameTest}, under this class's namespace.
 * <ul>
 *   <li>{@link #homeBossesRefuseLockedHits}: a Colossus at home takes nothing from a player it's locked for (their blow,
 *       their arrow, their tamed wolf, their risen zombie), never takes them as a target and doesn't count them for its
 *       bonus or its reset; a player whose quest has reached it ("Set in Stone", its offer heard) hurts it, and so does a
 *       creative one; a summoned Colossus is hurt by the locked player as by anyone.</li>
 *   <li>{@link #homeRatKingSleepsThroughALockedPlayer}: a cave's Rat King sleeps on with a locked player beside him (and
 *       their hit doesn't wake him), wakes for one whose quest has reached him; a summoned king beside the locked player
 *       wakes as always.</li>
 *   <li>{@link #wardsBounceLockedPlayers}: a locked survival player just inside a ward is pushed back out, the way they
 *       came; deeper in, sent back to their last spot outside; an allowed one and a creative one aren't touched.</li>
 * </ul>
 */
@GameTestHolder(BossLockGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class BossLockGameTest
{
    /**
     * The lock tests' GameTest namespace (all five classes): the mod's, so the normal run has them. To run them alone, set
     * it to a namespace of their own (say "dross_locks") and run {@code .\gradlew.bat runGameTestServer
     * -PgameTestNamespaces=dross_locks -PgameTestWorld=<a new folder>}: their templates are then made under it too.
     */
    public static final String NAMESPACE = Dross.MODID;
    /** The flat arena (the Colossus tests'). */
    public static final String ARENA = ColossusGameTest.ARENA;
    /** A single air block (the far-off tests', which build their own floor). */
    public static final String EMPTY = "portal_site_test_empty";
    private static final BlockPos MIDDLE = ColossusGameTest.MIDDLE;
    private static final int TIMEOUT = ColossusGameTest.ARENA_WAIT_TICKS + 600;
    /** A made-up lair or cave id for a boss put "at home" by hand (no real lair has it). */
    private static final long FAKE_HOME = 7_700_770_077L;
    /** A small hit (well under any cap or stage line). */
    private static final float HIT = 6.0F;
    /** Ticks a locked player stands near a sleeping boss to see it stays asleep (several of its looks). */
    private static final int QUIET_TICKS = 60;
    /** Ticks an allowed player gets to wake it. */
    private static final int WAKE_TICKS = 60;

    private BossLockGameTest() {}

    // ------------------------------------------------------------------ Templates under our own namespace

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled() || NAMESPACE.equals(Dross.MODID))
        {
            return;
        }
        MinecraftServer server = event.getServer();
        server.getStructureManager().getOrCreate(new ResourceLocation(NAMESPACE, ARENA))
                .load(server.registryAccess().lookupOrThrow(Registries.BLOCK), ColossusGameTest.floorTemplate(17, 17));
        StructureTemplate empty = server.getStructureManager().getOrCreate(new ResourceLocation(NAMESPACE, EMPTY));
        CompoundTag tag = new CompoundTag();
        tag.put("size", ints(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", ints(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        empty.load(BuiltInRegistries.BLOCK.asLookup(), tag);
    }

    private static ListTag ints(int... values)
    {
        ListTag list = new ListTag();
        for (int v : values)
        {
            list.add(IntTag.valueOf(v));
        }
        return list;
    }

    // ------------------------------------------------------------------ Helpers for every boss's lock test

    /**
     * Puts a stand-in player's Oracle quest on this boss's quest with its offer heard (ACTIVE): the boss may fight them.
     * A fresh stand-in is on the first quest, so every boss at home is locked for them.
     */
    public static void allow(ServerPlayer player, OracleBoss boss)
    {
        OracleProgress.setQuest(player, boss.quest());
        for (int i = 0; i < 40 && OracleProgress.state(player) == OracleState.AVAILABLE; i++)
        {
            OracleDialogue.talk(player);
        }
        if (!OracleRules.mayFightBoss(player, boss))
        {
            throw new GameTestAssertException(player.getGameProfile().getName() + " should be allowed to fight " + boss
                    + " once their quest reaches it");
        }
    }

    /** A survival stand-in at {@code at} (relative), on the first quest: every boss at home is locked for them. */
    public static ServerPlayer locked(GameTestHelper helper, List<ServerPlayer> players, String name, BlockPos at)
    {
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, name, at, 0.0F);
        player.setGameMode(GameType.SURVIVAL);
        players.add(player);
        return player;
    }

    private static void expect(boolean ok, String problem)
    {
        if (!ok)
        {
            throw new GameTestAssertException(problem);
        }
    }

    /** One hit, its invulnerable time cleared first: true if it hurt (its health went down). */
    private static boolean hurts(LivingEntity boss, DamageSource source)
    {
        boss.invulnerableTime = 0;
        float before = boss.getHealth();
        boss.hurt(source, HIT);
        return boss.getHealth() < before;
    }

    // ------------------------------------------------------------------ The tests

    @GameTest(template = ARENA, batch = "dross_locks_hits", timeoutTicks = TIMEOUT)
    public static void homeBossesRefuseLockedHits(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, BossLockGameTest::homeBossesRefuseLockedHits))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<ServerPlayer> players = new ArrayList<>();
        List<LivingEntity> extras = new ArrayList<>();
        Runnable cleanUp = () -> {
            for (LivingEntity e : extras)
            {
                e.discard();
            }
            ColossusGameTest.leaveAll(players);
        };
        CinderColossus home = CinderColossus.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(-3, 0, 0))), 0.0F);
        CinderColossus summoned = CinderColossus.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(4, 0, 0))), 0.0F);
        if (home == null || summoned == null)
        {
            throw new GameTestAssertException("couldn't make the two Colossi");
        }
        extras.add(home);
        extras.add(summoned);
        // (no AI: only what the locks do to their targets counts here, not their own fight)
        home.setNoAi(true);
        summoned.setNoAi(true);
        try
        {
            home.getPersistentData().putLong(LairKeeper.LAIR_TAG, FAKE_HOME);
            expect(home.lockedAs() == OracleBoss.COLOSSUS, "a lair's Colossus should be locked behind its quest");
            expect(summoned.lockedAs() == null, "a summoned Colossus should never be locked");

            ServerPlayer lockedOne = locked(helper, players, "lock-hit-locked", MIDDLE.offset(0, 0, 5));
            expect(!OracleRules.mayFightBoss(lockedOne, OracleBoss.COLOSSUS), "a fresh player shouldn't be allowed to fight it yet");

            // their own blow, their arrow, their tamed wolf, their risen zombie: none hurts it at home
            expect(!hurts(home, level.damageSources().playerAttack(lockedOne)), "a locked player's blow hurt the Colossus at home");
            Arrow arrow = new Arrow(level, lockedOne);
            expect(!hurts(home, level.damageSources().arrow(arrow, lockedOne)), "a locked player's arrow hurt the Colossus at home");
            Wolf wolf = EntityType.WOLF.create(level);
            expect(wolf != null, "couldn't make a wolf");
            wolf.moveTo(lockedOne.getX(), lockedOne.getY(), lockedOne.getZ() - 2.0D, 0.0F, 0.0F);
            wolf.tame(lockedOne);
            wolf.setNoAi(true);
            level.addFreshEntity(wolf);
            extras.add(wolf);
            expect(!hurts(home, level.damageSources().mobAttack(wolf)), "a locked player's tamed wolf hurt the Colossus at home");
            Zombie risen = EntityType.ZOMBIE.create(level);
            expect(risen != null, "couldn't make a zombie");
            risen.moveTo(lockedOne.getX() + 2.0D, lockedOne.getY(), lockedOne.getZ(), 0.0F, 0.0F);
            risen.setNoAi(true);
            risen.getPersistentData().putUUID(RisenUndead.OWNER_KEY, lockedOne.getUUID());
            level.addFreshEntity(risen);
            extras.add(risen);
            expect(!hurts(home, level.damageSources().mobAttack(risen)), "a locked player's risen zombie hurt the Colossus at home");
            expect(home.getHealth() >= home.getMaxHealth(), "the Colossus at home lost health to a locked player");

            // ...but a summoned one takes their blow as anyone's
            expect(hurts(summoned, level.damageSources().playerAttack(lockedOne)), "a summoned Colossus should be hurt by anyone");

            // it never takes them as a target, and they don't count for its bonus or keep it from its reset
            home.setTarget(lockedOne);
            expect(home.getTarget() == null, "the Colossus at home took a locked player as its target");
            expect(!home.canAttack(lockedOne), "the Colossus at home could attack a locked player");
            expect(BossRules.playersNear(home, level, 40.0D) == 0, "a locked player counted for the home Colossus's bonus");
            expect(!BossRules.anyoneNear(home, level, 40.0D), "a locked player kept the home Colossus from its reset");
            summoned.setTarget(lockedOne);
            expect(summoned.getTarget() == lockedOne, "a summoned Colossus should take anyone as its target");

            // a creative player (exempt) hurts it at home
            ServerPlayer creative = locked(helper, players, "lock-hit-creative", MIDDLE.offset(0, 0, -5));
            creative.setGameMode(GameType.CREATIVE);
            expect(hurts(home, level.damageSources().playerAttack(creative)), "a creative player should hurt the Colossus at home");

            // the locked player's quest reaches it ("Set in Stone", his offer heard): now they hurt it and count
            allow(lockedOne, OracleBoss.COLOSSUS);
            expect(hurts(home, level.damageSources().playerAttack(lockedOne)), "a player at \"Set in Stone\" should hurt the Colossus at home");
            home.setTarget(lockedOne);
            expect(home.getTarget() == lockedOne, "the Colossus at home should take a player at \"Set in Stone\" as its target");
            expect(BossRules.playersNear(home, level, 40.0D) == 1, "a player at \"Set in Stone\" should count for its bonus");

            // their quest goes back (reset): it can't attack them any more, and lets go of them within a look or two
            OracleProgress.reset(lockedOne);
            expect(!home.canAttack(lockedOne), "after a reset the Colossus at home could still attack them");
        }
        catch (RuntimeException e)
        {
            cleanUp.run();
            throw e;
        }
        helper.startSequence()
                .thenIdle(BossLocks.TARGET_CHECK_TICKS * 2 + 1)
                .thenExecute(() -> {
                    try
                    {
                        expect(home.getTarget() == null, "the Colossus at home kept a target it's now locked for: "
                                + home.getTarget());
                    }
                    finally
                    {
                        cleanUp.run();
                    }
                })
                .thenSucceed();
    }

    @GameTest(template = ARENA, batch = "dross_locks_rat_wake", timeoutTicks = TIMEOUT)
    public static void homeRatKingSleepsThroughALockedPlayer(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, BossLockGameTest::homeRatKingSleepsThroughALockedPlayer))
        {
            return;
        }
        ServerLevel level = helper.getLevel();
        List<ServerPlayer> players = new ArrayList<>();
        RatKing home = RatKing.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(-5, 0, 0))), 0.0F);
        RatKing summoned = RatKing.spawnBoss(level, helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(6, 0, 0))), 0.0F);
        if (home == null || summoned == null)
        {
            throw new GameTestAssertException("couldn't make the two Rat Kings");
        }
        CompoundTag cave = new CompoundTag();
        cave.putLong("Id", FAKE_HOME);
        home.getPersistentData().put(RatKingLairKeeper.HOME_TAG, cave);
        ServerPlayer player = locked(helper, players, "lock-rat-locked", MIDDLE);
        // (the summoned king fights them once he's up: Resistance V keeps them alive to wake the other one later)
        player.addEffect(new MobEffectInstance(MobEffects.DAMAGE_RESISTANCE, 20 * 60 * 5, 4, false, false));
        Runnable cleanUp = () -> {
            home.discard();
            summoned.discard();
            ColossusGameTest.leaveAll(players);
        };
        helper.startSequence()
                .thenExecute(() -> {
                    try
                    {
                        expect(home.lockedAs() == OracleBoss.RAT_KING && summoned.lockedAs() == null,
                                "the cave's king should be locked, the summoned one not");
                        expect(!home.isAwake() && !summoned.isAwake(), "both should start asleep");
                        home.invulnerableTime = 0;
                        home.hurt(level.damageSources().playerAttack(player), HIT);
                    }
                    catch (RuntimeException e)
                    {
                        cleanUp.run();
                        throw e;
                    }
                })
                .thenIdle(QUIET_TICKS)
                .thenExecute(() -> {
                    try
                    {
                        expect(!home.isAwake(), "the cave's king woke for a player he's locked for");
                        expect(home.getHealth() >= home.getMaxHealth(), "a locked player's hit hurt the cave's king");
                        expect(summoned.isAwake(), "a summoned king should wake for anyone near");
                        summoned.discard(); // (done: he'd only fight them)
                        allow(player, OracleBoss.RAT_KING);
                    }
                    catch (RuntimeException e)
                    {
                        cleanUp.run();
                        throw e;
                    }
                })
                .thenIdle(WAKE_TICKS)
                .thenExecute(() -> {
                    try
                    {
                        expect(home.isAwake(), "the cave's king should wake for a player whose quest has reached him");
                    }
                    finally
                    {
                        cleanUp.run();
                    }
                })
                .thenSucceed();
    }

    /** The test ward's radius: deeper than a push reaches at its middle, inside the arena with room round it. */
    private static final double WARD_RADIUS = 6.0D;

    /** A round test ward round the arena's middle. */
    private record TestWard(OracleBoss boss, Vec3 middle, double radius) implements BossWards.Ward
    {
        @Override
        public double depth(Vec3 feet)
        {
            return this.radius - Math.hypot(feet.x - this.middle.x, feet.z - this.middle.z);
        }

        @Override
        public Vec3 outward(Vec3 feet)
        {
            return BossWards.awayFrom(this.middle.x, this.middle.z, feet);
        }

        @Override
        public Vec3 exit()
        {
            return this.middle.add(this.radius + 2.0D, 0.0D, 0.0D);
        }

        @Override
        public float exitYaw()
        {
            return 90.0F;
        }
    }

    @GameTest(template = ARENA, batch = "dross_locks_wards", timeoutTicks = TIMEOUT)
    public static void wardsBounceLockedPlayers(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, BossLockGameTest::wardsBounceLockedPlayers))
        {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        try
        {
            Vec3 middle = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE));
            TestWard ward = new TestWard(OracleBoss.RAT_KING, middle, WARD_RADIUS);
            List<BossWards.Ward> wards = List.of(ward);

            // a locked survival player, standing outside (remembered), then just inside: pushed back out the way they came
            ServerPlayer lockedOne = locked(helper, players, "ward-locked", MIDDLE.offset((int) WARD_RADIUS + 1, 0, 0));
            BossWards.forget(lockedOne.getUUID());
            expect(BossWards.check(lockedOne, wards) == BossWards.Bounce.NONE, "outside the ward nothing should happen");
            Vec3 outside = lockedOne.position();
            Vec3 edge = middle.add(WARD_RADIUS - 1.0D, 0.0D, 0.0D); // a block inside its edge
            lockedOne.teleportTo(helper.getLevel(), edge.x, edge.y, edge.z, 0.0F, 0.0F);
            lockedOne.setDeltaMovement(Vec3.ZERO);
            expect(BossWards.check(lockedOne, wards) == BossWards.Bounce.PUSHED, "a locked player just inside should be pushed out");
            Vec3 push = lockedOne.getDeltaMovement();
            expect(push.x > 0.5D && Math.abs(push.z) < 0.01D && push.y > 0.0D,
                    "the push should send them back out the way they came (east and up), not " + push);

            // deep inside (an ender pearl, a teleport): back to their last spot outside
            Vec3 deep = middle.add(0.0D, 0.0D, 0.5D);
            lockedOne.teleportTo(helper.getLevel(), deep.x, deep.y, deep.z, 0.0F, 0.0F);
            expect(BossWards.check(lockedOne, wards) == BossWards.Bounce.SENT_BACK, "a locked player deep inside should be sent back");
            expect(lockedOne.position().distanceTo(outside) < 0.01D,
                    "they should be back at their last spot outside " + outside + ", not " + lockedOne.position());
            expect(ward.depth(lockedOne.position()) <= 0.0D, "they should be outside the ward now");

            // ...or, with no spot of theirs near, to the ward's own way out
            BossWards.forget(lockedOne.getUUID());
            lockedOne.teleportTo(helper.getLevel(), deep.x, deep.y, deep.z, 0.0F, 0.0F);
            expect(BossWards.check(lockedOne, wards) == BossWards.Bounce.SENT_BACK, "a locked player deep inside should be sent back");
            expect(lockedOne.position().distanceTo(ward.exit()) < 0.01D, "with no spot outside known, they should go to the ward's way out");

            // one whose quest has reached the boss, and a creative one: left alone
            ServerPlayer allowed = locked(helper, players, "ward-allowed", MIDDLE.offset(1, 0, 0));
            allow(allowed, OracleBoss.RAT_KING);
            Vec3 allowedAt = allowed.position();
            expect(BossWards.check(allowed, wards) == BossWards.Bounce.NONE && allowed.position().distanceTo(allowedAt) < 0.01D,
                    "a player whose quest has reached the boss shouldn't be bounced");
            ServerPlayer creative = locked(helper, players, "ward-creative", MIDDLE.offset(-1, 0, 0));
            creative.setGameMode(GameType.CREATIVE);
            Vec3 creativeAt = creative.position();
            expect(BossWards.check(creative, wards) == BossWards.Bounce.NONE && creative.position().distanceTo(creativeAt) < 0.01D,
                    "a creative player shouldn't be bounced");
        }
        finally
        {
            ColossusGameTest.leaveAll(players);
        }
        helper.succeed();
    }
}
