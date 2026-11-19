package com.reg21meme.dross.boss.giant;

import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.command.SilverwoodGiantShowcase;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.quest.oracle.OracleDialogue;
import com.reg21meme.dross.quest.oracle.OracleProgress;
import com.reg21meme.dross.quest.oracle.OracleQuest;
import com.reg21meme.dross.quest.oracle.OracleQuests;
import com.reg21meme.dross.quest.oracle.OracleState;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.world.PortalSiteGameTest;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Headless tests (GameTest) for the Silverwood Giant ({@link SilverwoodGiant}; QUEST_PLAN.md, "The Silverwood Giant"),
 * on a flat stone field under a barrier roof ({@link #FIELD}), with stand-in players:
 * <ul>
 *   <li>its stats and size, its look (G1 unless told, {@code {Look:"hoplite"}} read and saved);</li>
 *   <li>a survival player walking past stays safe; one standing near is warned (about 4 s), then attacked (about 8 s);
 *       moving off drains its patience;</li>
 *   <li>a mob near it is left alone, but hit by one it fights back, and hit by a player it goes for the player;</li>
 *   <li>past its leash it gives up and walks back; its anger runs out;</li>
 *   <li>no boulder at someone it isn't angry with, a boulder at the one it is;</li>
 *   <li>its spawn rules: Silverwood only, never within 96 blocks of another, never near the hub, only with room;</li>
 *   <li>three kills by a survival player finish the Oracle's "The Forest Giants" (Giant Slayer);</li>
 *   <li>{@code /dross showcase silverwood_giants}: the three looks, frozen, playing what they're told, replaced when run
 *       again.</li>
 * </ul>
 * Run them alone: set {@link #NAMESPACE} to a namespace of their own (say "dross_giant") and run {@code .\gradlew.bat
 * runGameTestServer -PgameTestNamespaces=dross_giant -PgameTestWorld=<a new folder>}: the field is made under that
 * namespace too ({@link #onServerStarted}).
 */
@GameTestHolder(SilverwoodGiantGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class SilverwoodGiantGameTest
{
    static final String NAMESPACE = Dross.MODID;
    /** A flat stone field with air over it, under a barrier roof 9 blocks up: room for a giant and its boulder's arc. */
    static final String FIELD = "silverwood_giant_test_field";
    private static final int FIELD_SIZE = 41;
    private static final int FLOOR_Y = ColossusGameTest.FLOOR_Y;
    private static final BlockPos MIDDLE = new BlockPos(FIELD_SIZE / 2, FLOOR_Y + 1, FIELD_SIZE / 2);
    private static final String BATCH = "dross_giant";
    private static final int TIMEOUT = ColossusGameTest.ARENA_WAIT_TICKS + 1200;
    /** Minecraft's yaw facing south (+z) and north (-z). */
    private static final float YAW_SOUTH = 0.0F;
    private static final float YAW_NORTH = 180.0F;

    private SilverwoodGiantGameTest() {}

    /** The field, made in memory when a test server starts (no structure file), under this class's namespace. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(new ResourceLocation(NAMESPACE, FIELD));
        template.load(event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK),
                ColossusGameTest.floorTemplate(FIELD_SIZE, FIELD_SIZE));
        if (!NAMESPACE.equals(Dross.MODID))
        {
            // Run alone under a namespace of their own, the spawn lists' test (DrossSpawnsGameTest, set to it too) needs
            // the portal site tests' empty template there as well: a single air block, made in memory.
            StructureTemplate empty = event.getServer().getStructureManager()
                    .getOrCreate(new ResourceLocation(NAMESPACE, PortalSiteGameTest.EMPTY));
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
            empty.load(event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK), tag);
        }
    }

    private static ListTag ints(int... values)
    {
        ListTag list = new ListTag();
        for (int value : values)
        {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    // ------------------------------------------------------------------ helpers

    @FunctionalInterface
    private interface Body
    {
        void run(GameTestHelper helper, List<ServerPlayer> players);
    }

    /** Runs a test once its field has loaded, with stand-in players that leave when it's over (also when it fails). */
    private static void run(GameTestHelper helper, Consumer<GameTestHelper> self, Body body)
    {
        if (ColossusGameTest.waitForArena(helper, self, FIELD_SIZE, FIELD_SIZE))
        {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        try
        {
            body.run(helper, players);
        }
        catch (RuntimeException e)
        {
            ColossusGameTest.leaveAll(players);
            throw e;
        }
        // (a test that goes on in a sequence logs its players out at its end: see finish)
    }

    /** A stand-in player at {@code at} in this game mode. */
    private static ServerPlayer join(GameTestHelper helper, List<ServerPlayer> players, String name, BlockPos at, GameType mode)
    {
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, name, at, YAW_NORTH);
        player.setGameMode(mode);
        players.add(player);
        return player;
    }

    /** A giant (G1 unless told) standing at {@code at}, facing {@code yaw}, with or without its AI. */
    private static SilverwoodGiant giant(GameTestHelper helper, BlockPos at, float yaw, boolean ai)
    {
        ServerLevel level = helper.getLevel();
        SilverwoodGiant giant = ModEntities.SILVERWOOD_GIANT.get().create(level);
        if (giant == null)
        {
            throw new GameTestAssertException("couldn't make a Silverwood Giant");
        }
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        giant.moveTo(pos.x, pos.y, pos.z, yaw, 0.0F);
        giant.setYBodyRot(yaw);
        giant.setYHeadRot(yaw);
        giant.setNoAi(!ai);
        giant.setPersistenceRequired();
        level.addFreshEntity(giant);
        return giant;
    }

    private static void expect(boolean condition, String message)
    {
        if (!condition)
        {
            throw new GameTestAssertException(message);
        }
    }

    /** Ends a test that ran in a sequence: its players log out, and it passes. */
    private static void finish(GameTestHelper helper, List<ServerPlayer> players)
    {
        ColossusGameTest.leaveAll(players);
        helper.succeed();
    }

    // ------------------------------------------------------------------ its stats

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void giantStats(GameTestHelper helper)
    {
        run(helper, SilverwoodGiantGameTest::giantStats, (h, players) -> {
            SilverwoodGiant giant = giant(h, MIDDLE, YAW_SOUTH, false);
            expect(giant.getMaxHealth() == (float) GiantTuning.HEALTH, "its health should be " + GiantTuning.HEALTH
                    + ", not " + giant.getMaxHealth());
            expect(giant.getAttributeValue(Attributes.ARMOR) == GiantTuning.ARMOR, "its armour should be " + GiantTuning.ARMOR);
            expect(giant.getAttributeValue(Attributes.KNOCKBACK_RESISTANCE) >= 0.8D, "it should hardly budge when hit");
            expect(giant.getAttributeValue(Attributes.MOVEMENT_SPEED) <= 0.25D, "it should be slow");
            EntityDimensions size = giant.getType().getDimensions();
            expect(size.width == 1.8F && size.height == 4.4F, "its hitbox should be 1.8 x 4.4, not " + size.width + " x " + size.height);
            expect(giant.getType().getCategory() == net.minecraft.world.entity.MobCategory.MONSTER, "it should be a monster");
            expect(giant.look() == GiantMoments.DEFAULT && GiantMoments.DEFAULT == GiantMoments.HURLER,
                    "it should wear G1 (the Boulder-Hurler) unless told, not " + giant.look().id());
            for (GiantMoments.Look look : GiantMoments.LOOKS)
            {
                for (String name : SilverwoodGiant.ANIMATIONS)
                {
                    expect(look.anim(name) != null, look.id() + " has no " + name + " animation");
                }
                expect(look.smashHit() > 0 && look.smashHit() < look.ticks("smash"), look.id() + "'s smash lands outside it");
                expect(look.throwRelease() > 0 && look.throwRelease() < look.ticks("throw"), look.id() + "'s throw lets go outside it");
            }
            // {Look:"brawler"} is read, and its look saved
            CompoundTag tag = new CompoundTag();
            tag.putString(SilverwoodGiant.TAG_LOOK, "brawler");
            giant.readAdditionalSaveData(tag);
            expect(giant.look() == GiantMoments.BRAWLER, "{Look:\"brawler\"} should make it the brawler, not " + giant.look().id());
            CompoundTag saved = giant.saveWithoutId(new CompoundTag());
            expect("brawler".equals(saved.getString(SilverwoodGiant.TAG_LOOK)), "its look should be saved, not '"
                    + saved.getString(SilverwoodGiant.TAG_LOOK) + "'");
            tag.putString(SilverwoodGiant.TAG_LOOK, "nonsense");
            giant.readAdditionalSaveData(tag);
            expect(giant.look() == GiantMoments.DEFAULT, "an unknown look should give G1");
            giant.discard();
            // a new giant (a spawn, an egg) wears one of the three looks at random, each about as often
            java.util.Map<String, Integer> made = new java.util.HashMap<>();
            ServerLevel lvl = h.getLevel();
            int n = 600;
            for (int i = 0; i < n; i++)
            {
                SilverwoodGiant g = ModEntities.SILVERWOOD_GIANT.get().create(lvl);
                g.finalizeSpawn(lvl, lvl.getCurrentDifficultyAt(g.blockPosition()), MobSpawnType.SPAWN_EGG, null, null);
                made.merge(g.look().id(), 1, Integer::sum);
                g.discard();
            }
            for (GiantMoments.Look look : GiantMoments.LOOKS)
            {
                int c = made.getOrDefault(look.id(), 0);
                expect(c > n / 3 - 60 && c < n / 3 + 60, "of " + n + " new giants about a third should be " + look.id()
                        + ", not " + c + " (" + made + ")");
            }
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ patience

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void walkingPastIsSafe(GameTestHelper helper)
    {
        run(helper, SilverwoodGiantGameTest::walkingPastIsSafe, (h, players) -> {
            SilverwoodGiant giant = giant(h, MIDDLE, YAW_SOUTH, false);
            ServerPlayer walker = join(h, players, "giant_passer_by", MIDDLE.offset(-15, 0, 5), GameType.SURVIVAL);
            Set<String> seen = new HashSet<>();
            // walking past 5 blocks from it at a walk (0.2 blocks a tick: about 3 seconds near it), then away
            int[] tick = {0};
            h.startSequence()
                    .thenExecuteFor(150, () -> {
                        double x = -15.0D + 0.2D * tick[0]++;
                        Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(MIDDLE).add(x, 0.0D, 5.0D));
                        walker.teleportTo(at.x, at.y, at.z);
                        if (giant.action() != null)
                        {
                            seen.add(giant.action());
                        }
                    })
                    .thenExecuteFor(100, () -> {
                        if (giant.action() != null)
                        {
                            seen.add(giant.action());
                        }
                    })
                    .thenExecute(() -> {
                        expect(giant.getTarget() == null, "a player walking past shouldn't anger it");
                        expect(!seen.contains("warn"), "a player walking past shouldn't be warned (it played " + seen + ")");
                        expect(giant.patienceOf(walker) == 0, "its patience should have drained once they'd gone, not "
                                + giant.patienceOf(walker));
                        finish(h, players);
                    });
        });
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void lingeringIsWarnedThenAttacked(GameTestHelper helper)
    {
        run(helper, SilverwoodGiantGameTest::lingeringIsWarnedThenAttacked, (h, players) -> {
            // (without its AI until it turns on them, so it doesn't wander off first: its patience needs none)
            SilverwoodGiant giant = giant(h, MIDDLE, YAW_SOUTH, false);
            ServerPlayer player = join(h, players, "giant_lingerer", MIDDLE.offset(0, 0, 5), GameType.SURVIVAL);
            ServerPlayer watcher = join(h, players, "giant_creative_watcher", MIDDLE.offset(3, 0, -4), GameType.CREATIVE);
            int[] warnedAt = {-1};
            int[] angryAt = {-1};
            int[] ticks = {0};
            h.startSequence()
                    .thenWaitUntil(() -> {
                        ticks[0]++;
                        if (warnedAt[0] < 0 && "warn".equals(giant.action()))
                        {
                            warnedAt[0] = ticks[0];
                        }
                        if (angryAt[0] < 0 && giant.getTarget() == player)
                        {
                            angryAt[0] = ticks[0];
                            giant.setNoAi(false);
                        }
                        if (ticks[0] > 400 || player.getHealth() < player.getMaxHealth())
                        {
                            return;
                        }
                        throw new GameTestAssertException("still waiting");
                    })
                    .thenExecute(() -> {
                        expect(warnedAt[0] > 0, "it should have warned the player standing near");
                        expect(warnedAt[0] >= GiantTuning.WARN_TICKS - GiantTuning.PATIENCE_CHECK
                                        && warnedAt[0] <= GiantTuning.WARN_TICKS + 2 * GiantTuning.PATIENCE_CHECK,
                                "it should warn after about 4 seconds, not after " + warnedAt[0] + " ticks");
                        expect(angryAt[0] >= GiantTuning.ANGER_TICKS - GiantTuning.PATIENCE_CHECK
                                        && angryAt[0] <= GiantTuning.ANGER_TICKS + 2 * GiantTuning.PATIENCE_CHECK,
                                "it should turn on them after about 8 seconds, not after " + angryAt[0] + " ticks");
                        expect(player.getHealth() < player.getMaxHealth(), "it should have hit the player it turned on within "
                                + ticks[0] + " ticks (it plays " + giant.action() + ")");
                        expect(giant.getTarget() != watcher && giant.patienceOf(watcher) == 0,
                                "a player in creative mode never tries its patience");
                        finish(h, players);
                    });
        });
    }

    // ------------------------------------------------------------------ fighting back, and only then

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void hitItFightsBack(GameTestHelper helper)
    {
        run(helper, SilverwoodGiantGameTest::hitItFightsBack, (h, players) -> {
            SilverwoodGiant giant = giant(h, MIDDLE, YAW_SOUTH, true);
            Zombie zombie = EntityType.ZOMBIE.create(h.getLevel());
            expect(zombie != null, "couldn't make a zombie");
            Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(0, 0, 6)));
            zombie.moveTo(at.x, at.y, at.z, YAW_NORTH, 0.0F);
            zombie.setNoAi(true);
            zombie.setPersistenceRequired();
            h.getLevel().addFreshEntity(zombie);
            ServerPlayer player = join(h, players, "giant_hitter", MIDDLE.offset(-12, 0, 0), GameType.SURVIVAL);
            h.startSequence()
                    .thenExecuteAfter(60, () -> {
                        expect(giant.getTarget() == null, "it should never pick a fight with a mob near it, but it went for "
                                + giant.getTarget());
                        giant.hurt(giant.damageSources().mobAttack(zombie), 2.0F);
                    })
                    .thenExecuteAfter(12, () -> {
                        expect(giant.getTarget() == zombie && giant.isAngryAt(zombie), "hit by the zombie, it should fight it back, "
                                + "but its target is " + giant.getTarget());
                        zombie.discard();
                        giant.giveUp();
                        giant.hurt(giant.damageSources().playerAttack(player), 2.0F);
                    })
                    .thenExecuteAfter(12, () -> {
                        expect(giant.getTarget() == player && giant.isAngryAt(player), "hit by a player, it should go for them, "
                                + "but its target is " + giant.getTarget());
                        giant.discard();
                        finish(h, players);
                    });
        });
    }

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void itGivesUpPastItsLeash(GameTestHelper helper)
    {
        run(helper, SilverwoodGiantGameTest::itGivesUpPastItsLeash, (h, players) -> {
            SilverwoodGiant giant = giant(h, MIDDLE, YAW_SOUTH, true);
            ServerPlayer player = join(h, players, "giant_runner", MIDDLE.offset(0, 0, 12), GameType.SURVIVAL);
            h.startSequence()
                    // (not on its very first tick: Minecraft's "hurt by" goal takes a hit then for no hit at all)
                    .thenExecuteAfter(3, () -> giant.hurt(giant.damageSources().playerAttack(player), 1.0F))
                    .thenExecuteAfter(5, () -> {
                        expect(giant.getTarget() == player, "hit by a player, it should go for them");
                        // as if it had chased them from 25 blocks off (its leash is 24), still on the field
                        giant.setChaseFromForTest(h.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(17, 0, 18))));
                    })
                    .thenExecuteAfter(3, () -> {
                        expect(giant.getTarget() == null && !giant.isAngryAt(player), "past its leash it should give up, but its "
                                + "target is " + giant.getTarget());
                        expect(giant.getNavigation().isInProgress(), "it should walk back to where the chase began");
                    })
                    .thenExecuteAfter(12, () -> {
                        // angry again (past its moment of not being hurt again), its anger running out
                        giant.hurt(giant.damageSources().playerAttack(player), 1.0F);
                    })
                    .thenExecuteAfter(5, () -> {
                        expect(giant.getTarget() == player, "hit again, it should go for them again");
                        giant.setRemainingPersistentAngerTime(2);
                    })
                    .thenExecuteAfter(6, () -> {
                        expect(giant.getTarget() == null && !giant.isAngry(), "its anger ran out: it should have let them go, "
                                + "but its target is " + giant.getTarget());
                        giant.discard();
                        finish(h, players);
                    });
        });
    }

    // ------------------------------------------------------------------ its boulder, only at the one it's angry with

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void boulderOnlyWhenAngry(GameTestHelper helper)
    {
        run(helper, SilverwoodGiantGameTest::boulderOnlyWhenAngry, (h, players) -> {
            // (calm without its AI, so it can't wander up to one of them and run out of patience)
            SilverwoodGiant giant = giant(h, MIDDLE.offset(0, 0, -6), YAW_SOUTH, false);
            // Only the Boulder-Hurler throws (the user, November 18, 2026): the Brawler and the Tree-Bearer never do.
            expect(giant.throwsBoulders(), "the Boulder-Hurler should hurl boulders");
            for (GiantMoments.Look other : List.of(GiantMoments.BRAWLER, GiantMoments.TREEBEARER))
            {
                SilverwoodGiant g = ModEntities.SILVERWOOD_GIANT.get().create(h.getLevel());
                expect(g != null, "couldn't make a giant");
                g.setLook(other);
                expect(!g.throwsBoulders(), other.id() + " shouldn't hurl boulders");
                g.discard();
            }
            ServerPlayer bystander = join(h, players, "giant_bystander", MIDDLE.offset(-12, 0, 10), GameType.SURVIVAL);
            ServerPlayer foe = join(h, players, "giant_foe", MIDDLE.offset(12, 0, 10), GameType.SURVIVAL);
            AABB field = new AABB(h.absolutePos(new BlockPos(0, 0, 0)), h.absolutePos(new BlockPos(FIELD_SIZE, 12, FIELD_SIZE)));
            List<GiantBoulder> mine = new ArrayList<>();
            int[] waited = {0};
            h.startSequence()
                    .thenExecuteFor(200, () -> {
                        List<GiantBoulder> flying = h.getLevel().getEntitiesOfClass(GiantBoulder.class, field,
                                b -> b.getOwner() == giant);
                        expect(flying.isEmpty(), "it hurled a boulder while it was angry with no one");
                    })
                    .thenExecute(() -> {
                        expect(giant.getTarget() == null, "calm, with two players 16 blocks off, it should have no target");
                        giant.setNoAi(false);
                        giant.hurt(giant.damageSources().playerAttack(foe), 1.0F);
                    })
                    .thenWaitUntil(() -> {
                        waited[0]++;
                        mine.clear();
                        mine.addAll(h.getLevel().getEntitiesOfClass(GiantBoulder.class, field, b -> b.getOwner() == giant));
                        if (mine.isEmpty() && waited[0] < 300)
                        {
                            throw new GameTestAssertException("no boulder yet");
                        }
                    })
                    .thenExecute(() -> {
                        expect(!mine.isEmpty(), "angry with a player " + (int) giant.distanceTo(foe)
                                + " blocks off, it should hurl a boulder within 15 seconds");
                        GiantBoulder boulder = mine.get(0);
                        Vec3 going = boulder.getDeltaMovement().multiply(1.0D, 0.0D, 1.0D).normalize();
                        Vec3 toFoe = foe.position().subtract(boulder.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
                        Vec3 toBystander = bystander.position().subtract(boulder.position()).multiply(1.0D, 0.0D, 1.0D).normalize();
                        expect(going.dot(toFoe) > 0.9D && going.dot(toFoe) > going.dot(toBystander),
                                "its boulder should fly at the one it's angry with, not the bystander");
                        mine.forEach(GiantBoulder::discard);
                        giant.discard();
                        finish(h, players);
                    });
        });
    }

    // ------------------------------------------------------------------ where it spawns

    @GameTest(template = FIELD, batch = "dross_giant_spawns", timeoutTicks = TIMEOUT)
    public static void spawnRules(GameTestHelper helper)
    {
        run(helper, SilverwoodGiantGameTest::spawnRules, (h, players) -> {
            ServerLevel level = h.getLevel();
            BlockPos spot = h.absolutePos(MIDDLE);
            expect("not in the Silverwood Forest".equals(SilverwoodGiants.refusal(level, spot, false, false)),
                    "outside the Silverwood Forest it should never spawn by itself");
            String alone = SilverwoodGiants.refusal(level, spot, true, false);
            expect(alone == null, "in the Silverwood Forest, with room and no giant near, it should spawn, but: " + alone);
            // a roof three blocks up: no room for its 4.4 blocks
            BlockPos roofed = h.absolutePos(MIDDLE.offset(-10, 0, -10));
            level.setBlockAndUpdate(roofed.above(3), Blocks.STONE.defaultBlockState());
            expect("no room for its body".equals(SilverwoodGiants.refusal(level, roofed, true, false)),
                    "under a roof 3 blocks up it shouldn't spawn");
            // the hub
            expect(SilverwoodGiants.nearHub(DrossHub.CENTER.offset(60, 0, 40)), "60, 40 blocks from the hub is too near");
            expect(!SilverwoodGiants.nearHub(DrossHub.CENTER.offset(200, 0, 0)), "200 blocks from the hub is far enough");
            expect("too near the hub".equals(SilverwoodGiants.refusal(level, DrossHub.CENTER.offset(30, 0, 0), true, true)),
                    "in the Dross, next to the hub, it shouldn't spawn");
            // another giant 18 blocks off (within 64)
            SilverwoodGiant other = giant(h, MIDDLE.offset(18, 0, 0), YAW_SOUTH, false);
            String near = SilverwoodGiants.refusal(level, spot, true, false);
            expect(near != null && near.startsWith("another giant"), "with another giant 18 blocks off it shouldn't spawn, but: "
                    + near);
            // spawn eggs and commands skip the rules (but the ground's): any biome
            expect(SilverwoodGiants.checkSpawn(ModEntities.SILVERWOOD_GIANT.get(), level, MobSpawnType.SPAWN_EGG, spot,
                    level.getRandom()), "a spawn egg works anywhere");
            expect(!SilverwoodGiants.checkSpawn(ModEntities.SILVERWOOD_GIANT.get(), level, MobSpawnType.NATURAL, spot,
                    level.getRandom()), "a natural spawn here (not Silverwood, a giant near) should be refused");
            other.discard();
            h.succeed();
        });
    }

    // ------------------------------------------------------------------ the Oracle's quest

    @GameTest(template = FIELD, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void threeKillsFinishGiantSlayer(GameTestHelper helper)
    {
        run(helper, SilverwoodGiantGameTest::threeKillsFinishGiantSlayer, (h, players) -> {
            ServerPlayer player = join(h, players, "giant_slayer", MIDDLE.offset(0, 0, 8), GameType.SURVIVAL);
            OracleProgress.setQuest(player, OracleQuests.GIANT_SLAYER);
            OracleQuest quest = OracleProgress.current(player);
            for (int i = 0; i < 40 && OracleProgress.current(player) == quest && OracleProgress.state(player) == OracleState.AVAILABLE; i++)
            {
                OracleDialogue.talk(player);
            }
            expect(OracleProgress.current(player) == OracleQuests.GIANT_SLAYER && OracleProgress.state(player) == OracleState.ACTIVE,
                    "after hearing its offer the quest should be going, not " + OracleProgress.state(player));
            for (int i = 0; i < 3; i++)
            {
                SilverwoodGiant giant = giant(h, MIDDLE.offset(-6 + 6 * i, 0, 0), YAW_SOUTH, false);
                giant.hurt(player.damageSources().playerAttack(player), 1000.0F);
                expect(!giant.isAlive(), "a giant survived a blow of 1000");
                expect(OracleProgress.kills(player) == i + 1 || i == 2, "kill " + (i + 1) + " should count, but they have "
                        + OracleProgress.kills(player));
            }
            expect(OracleProgress.current(player) == OracleQuests.GIANT_SLAYER && OracleProgress.state(player) == OracleState.DONE,
                    "three giants should finish The Forest Giants, but it's " + OracleProgress.current(player).id() + " "
                            + OracleProgress.state(player));
            expect(DrossAdvancements.has(player, DrossAdvancements.GIANT_SLAYER), "three giants should grant Giant Slayer");
            finish(h, players);
        });
    }

    // ------------------------------------------------------------------ the showcase

    @GameTest(template = FIELD, batch = "dross_giant_showcase", timeoutTicks = TIMEOUT)
    public static void showcaseLinesThemUp(GameTestHelper helper)
    {
        run(helper, SilverwoodGiantGameTest::showcaseLinesThemUp, (h, players) -> {
            ServerLevel level = h.getLevel();
            Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(new BlockPos(FIELD_SIZE / 2, FLOOR_Y + 1, 2)));
            CommandSourceStack source = level.getServer().createCommandSourceStack().withLevel(level).withPosition(at)
                    .withRotation(new Vec2(0.0F, YAW_SOUTH)).withPermission(4).withSuppressedOutput();
            int made = SilverwoodGiantShowcase.spawnGiants(source);
            expect(made == GiantMoments.LOOKS.size(), "the showcase should make " + GiantMoments.LOOKS.size() + " giants, not " + made);
            AABB around = new AABB(at, at).inflate(40.0D, 16.0D, 40.0D);
            List<SilverwoodGiant> giants = level.getEntitiesOfClass(SilverwoodGiant.class, around, SilverwoodGiant::isShowcase);
            expect(giants.size() == GiantMoments.LOOKS.size(), "there should be " + GiantMoments.LOOKS.size()
                    + " showcase giants, not " + giants.size());
            Set<String> looks = new HashSet<>();
            for (SilverwoodGiant giant : giants)
            {
                looks.add(giant.look().id());
                expect(giant.isNoAi() && giant.isSilent() && giant.isPersistenceRequired(), "a showcase giant should be frozen");
                expect(giant.getZ() > at.z, "the giants should stand ahead of the command (south)");
                expect(!giant.hurt(giant.damageSources().generic(), 10.0F), "a showcase giant shouldn't be hurt");
            }
            expect(looks.size() == GiantMoments.LOOKS.size(), "each look should be there once, not " + looks);
            SilverwoodGiantShowcase.play(source, "smash");
            for (SilverwoodGiant giant : giants)
            {
                expect("smash".equals(giant.action()), giant.look().id() + " should play its smash, not " + giant.action());
            }
            h.startSequence()
                    .thenExecuteAfter(60, () -> {
                        for (SilverwoodGiant giant : giants)
                        {
                            expect(giant.action() == null, giant.look().id() + "'s smash should be over, but it plays " + giant.action());
                        }
                        SilverwoodGiantShowcase.play(source, "throw");
                    })
                    .thenExecuteAfter(20, () -> {
                        for (SilverwoodGiant giant : giants)
                        {
                            expect("throw".equals(giant.action()) && giant.isAlive(),
                                    giant.look().id() + " should be throwing (alive), not " + giant.action());
                        }
                        int again = SilverwoodGiantShowcase.spawnGiants(source);
                        List<SilverwoodGiant> now = level.getEntitiesOfClass(SilverwoodGiant.class, around, SilverwoodGiant::isShowcase);
                        expect(again == GiantMoments.LOOKS.size() && now.size() == GiantMoments.LOOKS.size(),
                                "run again, the row should be replaced (" + now.size() + " giants)");
                        now.forEach(SilverwoodGiant::discard);
                        finish(h, players);
                    });
        });
    }
}
