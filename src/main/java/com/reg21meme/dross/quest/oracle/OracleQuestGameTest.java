package com.reg21meme.dross.quest.oracle;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.Unpooled;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.BossGrounds;
import com.reg21meme.dross.boss.BossGroundsMessage;
import com.reg21meme.dross.boss.GroundsShape;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.quest.QuestCommands;
import com.reg21meme.dross.quest.QuestProgressData;
import com.reg21meme.dross.registry.ModEntities;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Wolf;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import com.reg21meme.dross.villager.Survivor;
import com.reg21meme.dross.villager.SurvivorRole;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Headless tests (GameTest) for the Blind Oracle's quest line (QUEST_PLAN.md; {@code quest.oracle}), with stand-in
 * players in the Cinder Colossus tests' arena:
 * <ul>
 *   <li>every line the quest list asks for is in the lang file (and no more), every quest's advancement exists and the
 *       chain follows the list, every kill tag holds its mobs;</li>
 *   <li>a fresh player is on quest 0 with the red ? marker; clicking through his story grants "The Oracle's Plea" and
 *       makes quest 1 waiting; a walk-off mid-offer carries on at the next line;</li>
 *   <li>kills count only on their quest, the last one grants its advancement and makes it DONE; a tamed wolf's and a
 *       kills count for their owner; a calf doesn't count where grown beasts are wanted;</li>
 *   <li>a boss quest is DONE at once when its advancement is already held, and when it's earned while it's going;</li>
 *   <li>the last quest grants "Defeat the Dross" and sets off the fireworks;</li>
 *   <li>the lands and bosses follow the quest, creative and spectator are exempt;</li>
 *   <li>progress survives a save and load (and an old save starts at quest 0); {@code reset}, {@code skip}, {@code set}.</li>
 * </ul>
 */
@GameTestHolder(OracleQuestGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class OracleQuestGameTest
{
    /**
     * The tests' GameTest namespace: the mod's, so the normal run has them. To run them alone, set it to a namespace of
     * their own (say "dross_oracle") and run {@code .\gradlew.bat runGameTestServer -PgameTestNamespaces=dross_oracle
     * -PgameTestWorld=<a new folder>}: the arena is then made under that namespace too ({@link #onServerStarted}).
     */
    static final String NAMESPACE = Dross.MODID;
    private static final String BATCH = "dross_oracle";
    private static final int TIMEOUT = ColossusGameTest.ARENA_WAIT_TICKS + 400;
    private static final BlockPos MIDDLE = ColossusGameTest.MIDDLE;
    /** Far more clicks than any quest has lines (a loop that never ends would be a bug). */
    private static final int MAX_CLICKS = 40;

    private OracleQuestGameTest() {}

    /** The Colossus tests' arena, made under this class's own namespace when it isn't the mod's. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled() || NAMESPACE.equals(Dross.MODID))
        {
            return;
        }
        StructureTemplate template = event.getServer().getStructureManager()
                .getOrCreate(new ResourceLocation(NAMESPACE, ColossusGameTest.ARENA));
        template.load(event.getServer().registryAccess().lookupOrThrow(Registries.BLOCK),
                ColossusGameTest.floorTemplate(17, 17));
    }

    // ------------------------------------------------------------------ Helpers

    /** Runs a test once its arena has loaded, with stand-in players that leave afterwards (also when it fails). */
    private static void run(GameTestHelper helper, Consumer<GameTestHelper> self, Body body)
    {
        if (ColossusGameTest.waitForArena(helper, self))
        {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        try
        {
            body.run(helper, players);
        }
        finally
        {
            ColossusGameTest.leaveAll(players);
        }
        helper.succeed();
    }

    @FunctionalInterface
    private interface Body
    {
        void run(GameTestHelper helper, List<ServerPlayer> players);
    }

    private static ServerPlayer join(GameTestHelper helper, List<ServerPlayer> players, String name, GameType mode)
    {
        ServerPlayer player = ColossusGameTest.joinPlayer(helper, name, MIDDLE, 0.0F);
        player.setGameMode(mode);
        players.add(player);
        return player;
    }

    private static String keyOf(Component line)
    {
        return line.getContents() instanceof TranslatableContents translatable ? translatable.getKey() : line.getString();
    }

    /** Right-clicks the Oracle once and returns the line's lang key. */
    private static String click(ServerPlayer player)
    {
        Optional<Component> line = OracleDialogue.talk(player);
        if (line.isEmpty())
        {
            throw new GameTestAssertException("the Oracle said nothing to " + player.getGameProfile().getName()
                    + " on " + OracleProgress.current(player) + " " + OracleProgress.state(player));
        }
        return keyOf(line.get());
    }

    /** Clicks until the current quest has left AVAILABLE (its offer heard). */
    private static void hearOffer(ServerPlayer player)
    {
        OracleQuest quest = OracleProgress.current(player);
        for (int i = 0; i < MAX_CLICKS && OracleProgress.current(player) == quest
                && OracleProgress.state(player) == OracleState.AVAILABLE; i++)
        {
            click(player);
        }
        if (OracleProgress.current(player) == quest && OracleProgress.state(player) == OracleState.AVAILABLE)
        {
            throw new GameTestAssertException(quest.id() + "'s offer never ended");
        }
    }

    private static void expect(boolean condition, String message)
    {
        if (!condition)
        {
            throw new GameTestAssertException(message);
        }
    }

    private static void expectAt(ServerPlayer player, OracleQuest quest, OracleState state, String when)
    {
        OracleQuest at = OracleProgress.current(player);
        OracleState now = OracleProgress.state(player);
        expect(at == quest && now == state, when + ": expected " + quest.id() + " " + state + ", but it's " + at.id() + " " + now);
    }

    private static <T extends Mob> T spawn(GameTestHelper helper, EntityType<T> type, BlockPos at)
    {
        ServerLevel level = helper.getLevel();
        T mob = type.create(level);
        if (mob == null)
        {
            throw new GameTestAssertException("couldn't make a " + BuiltInRegistries.ENTITY_TYPE.getKey(type));
        }
        Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
        mob.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
        mob.setNoAi(true);
        level.addFreshEntity(mob);
        return mob;
    }

    /** Kills it with a player's blow. */
    private static void killBy(LivingEntity victim, ServerPlayer player)
    {
        victim.hurt(player.damageSources().playerAttack(player), 1000.0F);
        expect(!victim.isAlive(), "a " + victim.getType().getDescriptionId() + " survived a blow of 1000");
    }

    // ------------------------------------------------------------------ The list and its files

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void everyLineAdvancementAndTagIsThere(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::everyLineAdvancementAndTagIsThere, (h, players) -> {
            JsonObject lang;
            try (InputStream in = OracleQuestGameTest.class.getResourceAsStream("/assets/dross/lang/en_us.json"))
            {
                expect(in != null, "en_us.json isn't on the classpath");
                lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            catch (java.io.IOException e)
            {
                throw new GameTestAssertException("couldn't read en_us.json: " + e);
            }
            List<String> missing = new ArrayList<>();
            List<String> extra = new ArrayList<>();
            for (OracleQuest quest : OracleQuests.ALL)
            {
                List<String> keys = new ArrayList<>();
                keys.add(quest.key("name"));
                for (int i = 1; i <= quest.offerLines(); i++)
                {
                    keys.add(quest.key("offer." + i));
                }
                if (quest.kind() == OracleQuest.Kind.KILL || quest.kind() == OracleQuest.Kind.BOSS)
                {
                    keys.add(quest.key("remind"));
                }
                for (int i = 1; i <= quest.doneLines(); i++)
                {
                    keys.add(quest.key("done." + i));
                }
                for (String key : keys)
                {
                    if (!lang.has(key))
                    {
                        missing.add(key);
                    }
                }
                if (lang.has(quest.key("offer." + (quest.offerLines() + 1))))
                {
                    extra.add(quest.key("offer." + (quest.offerLines() + 1)));
                }
                if (lang.has(quest.key("done." + (quest.doneLines() + 1))))
                {
                    extra.add(quest.key("done." + (quest.doneLines() + 1)));
                }
                if (quest.grantsOwnAdvancement())
                {
                    for (String part : List.of("title", "description"))
                    {
                        String key = "advancements.dross." + quest.advancement() + "." + part;
                        if (!lang.has(key))
                        {
                            missing.add(key);
                        }
                    }
                }
            }
            for (String key : List.of("dross.oracle.progress", "dross.oracle.return"))
            {
                if (!lang.has(key))
                {
                    missing.add(key);
                }
            }
            expect(missing.isEmpty(), "lang keys missing: " + missing);
            expect(extra.isEmpty(), "lines the quest list doesn't count (raise its line count?): " + extra);

            // Every quest's advancement, its parent the one before it in the list.
            var advancements = h.getLevel().getServer().getAdvancements();
            String parent = DrossAdvancements.ENTERED_THE_DROSS;
            for (OracleQuest quest : OracleQuests.ALL)
            {
                Advancement advancement = advancements.getAdvancement(new ResourceLocation(Dross.MODID, quest.advancement()));
                expect(advancement != null, "the advancement dross:" + quest.advancement() + " doesn't exist");
                expect(advancement.getParent() != null && advancement.getParent().getId().equals(new ResourceLocation(Dross.MODID, parent)),
                        "dross:" + quest.advancement() + "'s parent should be dross:" + parent + ", but it's "
                                + (advancement.getParent() == null ? "none" : advancement.getParent().getId()));
                parent = quest.advancement();
            }

            // Every kill tag holds the mobs it should (the Silverwood Giant's entry is optional until the boss area adds it).
            expectTag(OracleQuests.INTO_THE_ASHES, ModEntities.STONE_GOLEM.get(), ModEntities.LAVA_GOLEM.get(),
                    ModEntities.CINDER_HERMIT.get(), ModEntities.PALLBEARER.get());
            expectTag(OracleQuests.BENEATH_THE_ROT, ModEntities.RAT.get(), ModEntities.ENRAGED_RAT.get(), EntityType.WITCH);
            expectTag(OracleQuests.FIRST_FROST, ModEntities.POND_SKATER.get(), ModEntities.MOOSE.get(), ModEntities.RIMEHORN.get());
            expect(!ModEntities.ALPHA_MOOSE.get().is(OracleQuests.FIRST_FROST.targets()), "the Alpha Moose isn't a Rimefall beast");
            expect(OracleQuests.lockableLands().containsAll(List.of(DrossBiomes.CINDER_WASTES, DrossBiomes.GRAVECRAGS,
                    DrossBiomes.BLIGHTWOOD, DrossBiomes.SEWERS, DrossBiomes.RIMEFALL)), "the lockable lands are " + OracleQuests.lockableLands());
            expect(OracleQuests.BENEATH_THE_ROT.count() == 3 && OracleQuests.FIRST_FROST.count() == 3,
                    "Beneath the Rot and First Frost should want three each (the user, November 18, 2026)");

            // The spell ends only the giants' hand-in; every other goes off on a line of its quest's offer (the user,
            // November 25, 2026: he tells of the boss or the land, breaks its seal, then sends you off).
            List<OracleQuest> casting = OracleQuests.ALL.stream().filter(OracleQuests::castsSpell).toList();
            expect(casting.equals(List.of(OracleQuests.GIANT_SLAYER)), "the quests whose hand-in casts the spell are " + casting);
            java.util.Map<OracleQuest, Integer> spells = java.util.Map.of(OracleQuests.SET_IN_STONE, 4, OracleQuests.BENEATH_THE_ROT, 2,
                    OracleQuests.DOWN_THE_DRAIN, 3, OracleQuests.FIRST_FROST, 3, OracleQuests.THE_HUNTER_HUNTED, 3,
                    OracleQuests.WINGS_CLIPPED, 3);
            for (OracleQuest quest : OracleQuests.ALL)
            {
                int expected = spells.getOrDefault(quest, 0);
                expect(quest.spellLine() == expected, quest.id() + "'s spell should be on offer line " + expected + ", not "
                        + quest.spellLine());
            }
        });
    }

    private static void expectTag(OracleQuest quest, EntityType<?>... types)
    {
        for (EntityType<?> type : types)
        {
            expect(type.is(quest.targets()), BuiltInRegistries.ENTITY_TYPE.getKey(type) + " should be in "
                    + quest.targets().location());
        }
    }

    // ------------------------------------------------------------------ Talking

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void storyGrantsThePleaAndMovesOn(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::storyGrantsThePleaAndMovesOn, (h, players) -> {
            ServerPlayer player = join(h, players, "oracle_listener", GameType.CREATIVE);
            expectAt(player, OracleQuests.ORACLE_PLEA, OracleState.AVAILABLE, "a fresh player");
            expect(OracleProgress.marker(player) == OracleMarker.NEW, "a fresh player should see the red ? (NEW), not "
                    + OracleProgress.marker(player));
            OracleQuest plea = OracleQuests.ORACLE_PLEA;
            for (int i = 1; i <= plea.offerLines(); i++)
            {
                expect(!DrossAdvancements.has(player, plea.advancement()), "the Plea came before the story's end (line " + i + ")");
                String key = click(player);
                expect(key.equals(plea.key("offer." + i)), "line " + i + " of his story should be " + plea.key("offer." + i)
                        + ", but it's " + key);
                if (i == 2)
                {
                    // Walking off and coming back: the record carries the line on (nothing else to it).
                    expect(OracleProgress.record(player).line() == 2, "after two lines he should be at line 2");
                }
            }
            expect(DrossAdvancements.has(player, plea.advancement()), "the story's end should grant dross:" + plea.advancement());
            expectAt(player, OracleQuests.GIANT_SLAYER, OracleState.AVAILABLE, "after his story");
            expect(OracleProgress.marker(player) == OracleMarker.NEW, "quest 1 waiting should show the red ?");
            String key = click(player);
            expect(key.equals(OracleQuests.GIANT_SLAYER.key("offer.1")), "the next click should start quest 1's offer, not " + key);
        });
    }

    // ------------------------------------------------------------------ Kills

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void killsCountOnlyOnTheirQuest(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::killsCountOnlyOnTheirQuest, (h, players) -> {
            ServerPlayer player = join(h, players, "oracle_ash_walker", GameType.CREATIVE);
            OracleProgress.setQuest(player, OracleQuests.INTO_THE_ASHES);
            killBy(spawn(h, ModEntities.STONE_GOLEM.get(), MIDDLE.offset(3, 0, 0)), player);
            expect(OracleProgress.kills(player) == 0, "a kill before the quest is given shouldn't count");

            OracleProgress.setQuest(player, OracleQuests.BENEATH_THE_ROT);
            hearOffer(player);
            killBy(spawn(h, ModEntities.STONE_GOLEM.get(), MIDDLE.offset(3, 0, 1)), player);
            expect(OracleProgress.kills(player) == 0, "a stone golem shouldn't count for the rats");

            OracleProgress.setQuest(player, OracleQuests.INTO_THE_ASHES);
            hearOffer(player);
            expectAt(player, OracleQuests.INTO_THE_ASHES, OracleState.ACTIVE, "after its offer");
            expect(OracleProgress.marker(player) == OracleMarker.ACTIVE, "a going quest shows the yellow marker");
            killBy(spawn(h, ModEntities.STONE_GOLEM.get(), MIDDLE.offset(-3, 0, 0)), player);
            killBy(spawn(h, ModEntities.STONE_GOLEM.get(), MIDDLE.offset(-3, 0, 2)), player);
            expect(OracleProgress.kills(player) == 2, "two golems should count 2, not " + OracleProgress.kills(player));
            expectAt(player, OracleQuests.INTO_THE_ASHES, OracleState.ACTIVE, "after two of three");
            expect(!DrossAdvancements.has(player, DrossAdvancements.INTO_THE_ASHES), "Into the Ashes came after two kills");
            String remind = click(player);
            expect(remind.equals(OracleQuests.INTO_THE_ASHES.key("remind")), "a going quest's click should remind, not " + remind);
            killBy(spawn(h, ModEntities.STONE_GOLEM.get(), MIDDLE.offset(-3, 0, -2)), player);
            expectAt(player, OracleQuests.INTO_THE_ASHES, OracleState.DONE, "after the third kill");
            expect(DrossAdvancements.has(player, DrossAdvancements.INTO_THE_ASHES), "the third kill should grant Into the Ashes");
            expect(OracleProgress.marker(player) == OracleMarker.DONE, "a done quest shows the green !");

            // His thanks, then the Colossus's quest.
            for (int i = 1; i <= OracleQuests.INTO_THE_ASHES.doneLines(); i++)
            {
                String key = click(player);
                expect(key.equals(OracleQuests.INTO_THE_ASHES.key("done." + i)), "thanks line " + i + " was " + key);
            }
            expectAt(player, OracleQuests.SET_IN_STONE, OracleState.AVAILABLE, "after his thanks");
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void petsKillsCount(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::petsKillsCount, (h, players) -> {
            ServerPlayer player = join(h, players, "oracle_wolf_owner", GameType.CREATIVE);
            OracleProgress.setQuest(player, OracleQuests.BENEATH_THE_ROT);
            hearOffer(player);

            Wolf wolf = spawn(h, EntityType.WOLF, MIDDLE.offset(2, 0, 2));
            wolf.tame(player);
            LivingEntity rat = spawn(h, ModEntities.RAT.get(), MIDDLE.offset(3, 0, 2));
            rat.hurt(wolf.damageSources().mobAttack(wolf), 1000.0F);
            expect(!rat.isAlive(), "the wolf's bite should kill the rat");
            expect(OracleProgress.kills(player) == 1, "a rat killed by the player's tamed wolf should count, kills: "
                    + OracleProgress.kills(player));

            Wolf stray = spawn(h, EntityType.WOLF, MIDDLE.offset(2, 0, -2));
            LivingEntity other = spawn(h, ModEntities.RAT.get(), MIDDLE.offset(3, 0, -2));
            other.hurt(stray.damageSources().mobAttack(stray), 1000.0F);
            expect(OracleProgress.kills(player) == 1, "a wild wolf's kill shouldn't count for anyone");
            wolf.discard();
            stray.discard();
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void calvesDontCountAsBeasts(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::calvesDontCountAsBeasts, (h, players) -> {
            ServerPlayer player = join(h, players, "oracle_frost_hunter", GameType.CREATIVE);
            OracleProgress.setQuest(player, OracleQuests.FIRST_FROST);
            hearOffer(player);
            var calf = spawn(h, ModEntities.MOOSE.get(), MIDDLE.offset(4, 0, 4));
            calf.setBaby(true);
            killBy(calf, player);
            expect(OracleProgress.kills(player) == 0, "a moose calf shouldn't count as one of Rimefall's grown beasts");
            killBy(spawn(h, ModEntities.MOOSE.get(), MIDDLE.offset(-4, 0, 4)), player);
            expect(OracleProgress.kills(player) == 1, "a grown moose should count");
            killBy(spawn(h, ModEntities.POND_SKATER.get(), MIDDLE.offset(0, 0, -4)), player);
            expectAt(player, OracleQuests.FIRST_FROST, OracleState.ACTIVE, "after a moose and a Pond Skater (two of three)");
            killBy(spawn(h, ModEntities.RIMEHORN.get(), MIDDLE.offset(0, 0, 5)), player);
            expectAt(player, OracleQuests.FIRST_FROST, OracleState.DONE, "after a moose, a Pond Skater and a Rimehorn");
            expect(DrossAdvancements.has(player, DrossAdvancements.FIRST_FROST), "First Frost should be granted");
        });
    }

    // ------------------------------------------------------------------ Bosses and the end

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void bossQuestsFollowTheirAdvancement(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::bossQuestsFollowTheirAdvancement, (h, players) -> {
            ServerPlayer veteran = join(h, players, "oracle_veteran", GameType.CREATIVE);
            DrossAdvancements.grant(veteran, DrossAdvancements.SET_IN_STONE);
            OracleProgress.setQuest(veteran, OracleQuests.SET_IN_STONE);
            hearOffer(veteran);
            expectAt(veteran, OracleQuests.SET_IN_STONE, OracleState.DONE, "with Set in Stone already held, after its offer");
            for (int i = 0; i < OracleQuests.SET_IN_STONE.doneLines(); i++)
            {
                click(veteran);
            }
            expectAt(veteran, OracleQuests.BENEATH_THE_ROT, OracleState.AVAILABLE, "after his thanks");

            ServerPlayer fighter = join(h, players, "oracle_rat_fighter", GameType.CREATIVE);
            OracleProgress.setQuest(fighter, OracleQuests.DOWN_THE_DRAIN);
            hearOffer(fighter);
            expectAt(fighter, OracleQuests.DOWN_THE_DRAIN, OracleState.ACTIVE, "without Down the Drain, after its offer");
            String remind = click(fighter);
            expect(remind.equals(OracleQuests.DOWN_THE_DRAIN.key("remind")), "a going boss quest should remind, not " + remind);
            DrossAdvancements.grant(fighter, DrossAdvancements.DOWN_THE_DRAIN);
            expectAt(fighter, OracleQuests.DOWN_THE_DRAIN, OracleState.DONE, "once Down the Drain is earned");

            // A boss advancement that isn't this quest's changes nothing.
            ServerPlayer other = join(h, players, "oracle_wrong_boss", GameType.CREATIVE);
            OracleProgress.setQuest(other, OracleQuests.THE_HUNTER_HUNTED);
            hearOffer(other);
            DrossAdvancements.grant(other, DrossAdvancements.WINGS_CLIPPED);
            expectAt(other, OracleQuests.THE_HUNTER_HUNTED, OracleState.ACTIVE, "after another boss's advancement");
        });
    }

    /**
     * A boss's home opens for a player the moment they hold its advancement, whatever their Oracle quest (the user,
     * November 22, 2026: "once you defeat the boss you should be able to break the boss lair, not when you go back to the
     * villager"); and the protected shapes a player's game is sent come out of the message as they went in.
     */
    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void bossHomesOpenWithTheirAdvancement(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::bossHomesOpenWithTheirAdvancement, (h, players) -> {
            ServerPlayer digger = join(h, players, "grounds_digger", GameType.SURVIVAL);
            for (OracleBoss boss : OracleBoss.values())
            {
                expect(!BossGrounds.hasBeaten(digger, boss), "a fresh player hasn't beaten " + boss);
            }
            // As after "/dross quest set down_the_drain": the quest waiting to be given, then the Rat King killed.
            OracleProgress.setQuest(digger, OracleQuests.DOWN_THE_DRAIN);
            DrossAdvancements.grant(digger, DrossAdvancements.DOWN_THE_DRAIN);
            expectAt(digger, OracleQuests.DOWN_THE_DRAIN, OracleState.AVAILABLE, "with Down the Drain earned before his offer");
            expect(BossGrounds.hasBeaten(digger, OracleBoss.RAT_KING),
                    "with Down the Drain held, the Rat King's home should be open, his quest only waiting to be given");
            expect(!BossGrounds.hasBeaten(digger, OracleBoss.HUNTSMAN), "Down the Drain doesn't open the Huntsman's village");

            ServerPlayer early = join(h, players, "grounds_early", GameType.SURVIVAL);
            DrossAdvancements.grant(early, DrossAdvancements.WINGS_CLIPPED);
            expect(BossGrounds.hasBeaten(early, OracleBoss.EMBERDACTYL), "Wings Clipped opens the Emberdactyl's volcano on quest 0");
            expect(!BossGrounds.hasBeaten(early, OracleBoss.COLOSSUS), "Wings Clipped doesn't open the Colossus's lair");

            // The shapes' trip to a player's game: Box, a round lair and a lobed volcano, tested on the same blocks.
            List<GroundsShape> sent = List.of(new GroundsShape.Box(-5, 40, 3, 7, 90, 18),
                    new GroundsShape.Round(100.5D, -40.5D, 60.0D, 20, 130, GroundsShape.Round.ROUND),
                    new GroundsShape.Round(-300.5D, 250.5D, 74.0D, 30, 160, new double[] {0.4D, 2.1D, 5.0D}));
            FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
            new BossGroundsMessage(sent).encode(buf);
            List<GroundsShape> got = BossGroundsMessage.decode(buf).shapes();
            buf.release();
            expect(got.size() == sent.size(), "the message brought " + got.size() + " shapes, " + sent.size() + " sent");
            java.util.Random random = new java.util.Random(22L);
            int inside = 0;
            for (int i = 0; i < 20000; i++)
            {
                BlockPos at = new BlockPos(random.nextInt(800) - 420, random.nextInt(200), random.nextInt(700) - 400);
                for (int k = 0; k < sent.size(); k++)
                {
                    boolean before = sent.get(k).contains(at);
                    expect(before == got.get(k).contains(at), "shape " + k + " changed on its way at " + at.toShortString());
                    inside += before ? 1 : 0;
                }
            }
            expect(inside > 300, "only " + inside + " of the sampled blocks were inside a shape");
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void finalQuestGrantsDefeatTheDross(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, OracleQuestGameTest::finalQuestGrantsDefeatTheDross))
        {
            return;
        }
        List<ServerPlayer> players = new ArrayList<>();
        ServerPlayer player;
        try
        {
            player = join(helper, players, "oracle_champion", GameType.CREATIVE);
            OracleProgress.setQuest(player, OracleQuests.DEFEAT_THE_DROSS);
            OracleQuest last = OracleQuests.DEFEAT_THE_DROSS;
            for (int i = 1; i <= last.offerLines(); i++)
            {
                expect(!DrossAdvancements.has(player, last.advancement()), "Defeat the Dross came before his last words");
                String key = click(player);
                expect(key.equals(last.key("offer." + i)), "his last words' line " + i + " was " + key);
            }
            expect(DrossAdvancements.has(player, last.advancement()), "his last words should grant Defeat the Dross");
            expect(OracleProgress.state(player) == OracleState.FINISHED, "after Defeat the Dross every quest is finished");
            expect(OracleProgress.marker(player) == OracleMarker.NONE, "a finished player sees no marker");
            String after = OracleDialogue.talk(player).map(c -> c.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents t
                    ? t.getKey() : "").orElse("nothing");
            expect(after.equals("dross.oracle.after.1"), "after Defeat the Dross he should talk to them as an old friend "
                    + "(dross.oracle.after.1), not " + after);
        }
        catch (RuntimeException e)
        {
            ColossusGameTest.leaveAll(players);
            throw e;
        }
        ServerPlayer champion = player;
        Set<UUID> seen = new HashSet<>();
        helper.succeedWhen(() -> {
            // (The arena is underground under a barrier roof: the rockets start at the top of the land over it.)
            ServerLevel level = helper.getLevel();
            AABB around = new AABB(champion.getX() - 48.0D, level.getMinBuildHeight(), champion.getZ() - 48.0D,
                    champion.getX() + 48.0D, level.getMaxBuildHeight(), champion.getZ() + 48.0D);
            for (FireworkRocketEntity rocket : level.getEntitiesOfClass(FireworkRocketEntity.class, around, OracleFireworks::isOurs))
            {
                seen.add(rocket.getUUID());
            }
            if (seen.size() < OracleFireworks.ROCKETS)
            {
                throw new GameTestAssertException(seen.size() + " of the " + OracleFireworks.ROCKETS + " rockets so far");
            }
            ColossusGameTest.leaveAll(players);
        });
    }

    // ------------------------------------------------------------------ Rules

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void landsAndBossesFollowTheQuest(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::landsAndBossesFollowTheQuest, (h, players) -> {
            ServerPlayer player = join(h, players, "oracle_wanderer", GameType.SURVIVAL);
            ResourceKey<?> forest = DrossBiomes.SILVERWOOD_FOREST;
            expectLands(player, "a fresh player", forest);
            expectBosses(player, "a fresh player");

            // The giants: the forest's barrier holds until they're handed in.
            OracleProgress.setQuest(player, OracleQuests.GIANT_SLAYER);
            hearOffer(player);
            expectLands(player, "the giants going", forest);
            OracleProgress.record(player).enter(OracleState.DONE);
            expectLands(player, "the giants done, not yet handed in", forest);

            // Handed in: the Cinder Wastes and the Gravecrags open.
            OracleProgress.setQuest(player, OracleQuests.INTO_THE_ASHES);
            expectLands(player, "Into the Ashes waiting", forest, DrossBiomes.GRAVECRAGS, DrossBiomes.CINDER_WASTES);
            hearOffer(player);
            expectBosses(player, "Into the Ashes going");

            // The Colossus's quest waiting: its volcanoes stay warded until his offer breaks the ward (its spell line).
            OracleProgress.setQuest(player, OracleQuests.SET_IN_STONE);
            expectBosses(player, "the Colossus's quest waiting");
            expectLands(player, "the Colossus's quest waiting", forest, DrossBiomes.GRAVECRAGS, DrossBiomes.CINDER_WASTES);
            for (int i = 1; i < OracleQuests.SET_IN_STONE.spellLine(); i++)
            {
                click(player);
            }
            expectBosses(player, "the Colossus's offer before its spell");
            click(player);
            expectBosses(player, "the Colossus's spell line heard", OracleBoss.COLOSSUS);
            hearOffer(player);
            expectBosses(player, "the Colossus's offer heard", OracleBoss.COLOSSUS);

            // Beneath the Rot: Blightwood and its sewers open on its offer's spell line.
            OracleProgress.setQuest(player, OracleQuests.BENEATH_THE_ROT);
            expectLands(player, "Beneath the Rot waiting", forest, DrossBiomes.GRAVECRAGS, DrossBiomes.CINDER_WASTES);
            expectBosses(player, "Beneath the Rot waiting", OracleBoss.COLOSSUS);
            for (int i = 1; i <= OracleQuests.BENEATH_THE_ROT.spellLine(); i++)
            {
                click(player);
            }
            expectAt(player, OracleQuests.BENEATH_THE_ROT, OracleState.AVAILABLE, "Beneath the Rot's spell line heard, its offer not over");
            expectLands(player, "Beneath the Rot's spell line heard", forest, DrossBiomes.GRAVECRAGS, DrossBiomes.CINDER_WASTES,
                    DrossBiomes.BLIGHTWOOD, DrossBiomes.SEWERS);

            OracleProgress.setQuest(player, OracleQuests.DOWN_THE_DRAIN);
            expectBosses(player, "the Rat King's quest waiting", OracleBoss.COLOSSUS);
            hearOffer(player);
            expectBosses(player, "the Rat King's offer heard", OracleBoss.COLOSSUS, OracleBoss.RAT_KING);

            // First Frost: Rimefall opens on its offer's spell line.
            OracleProgress.setQuest(player, OracleQuests.FIRST_FROST);
            expectLands(player, "First Frost waiting", forest, DrossBiomes.GRAVECRAGS, DrossBiomes.CINDER_WASTES,
                    DrossBiomes.BLIGHTWOOD, DrossBiomes.SEWERS);
            hearOffer(player);
            expectLands(player, "First Frost's offer heard", forest, DrossBiomes.GRAVECRAGS, DrossBiomes.CINDER_WASTES,
                    DrossBiomes.BLIGHTWOOD, DrossBiomes.SEWERS, DrossBiomes.RIMEFALL);
            expectBosses(player, "First Frost going", OracleBoss.COLOSSUS, OracleBoss.RAT_KING);

            OracleProgress.setQuest(player, OracleQuests.THE_HUNTER_HUNTED);
            expectBosses(player, "the Huntsman's quest waiting", OracleBoss.COLOSSUS, OracleBoss.RAT_KING);
            hearOffer(player);
            expectBosses(player, "the Huntsman's offer heard", OracleBoss.COLOSSUS, OracleBoss.RAT_KING, OracleBoss.HUNTSMAN);

            OracleProgress.setQuest(player, OracleQuests.WINGS_CLIPPED);
            expectBosses(player, "the Emberdactyl's quest waiting", OracleBoss.COLOSSUS, OracleBoss.RAT_KING, OracleBoss.HUNTSMAN);
            hearOffer(player);
            expectBosses(player, "the Emberdactyl's offer heard", OracleBoss.values());

            OracleProgress.setQuest(player, OracleQuests.DEFEAT_THE_DROSS);
            OracleProgress.skip(player);
            expect(OracleProgress.isFinished(player), "skipping the last quest should finish them");
            expectBosses(player, "finished", OracleBoss.values());

            // Creative and spectator: everything open, at any point.
            ServerPlayer builder = join(h, players, "oracle_builder", GameType.CREATIVE);
            ServerPlayer ghost = join(h, players, "oracle_ghost", GameType.SPECTATOR);
            for (ServerPlayer exempt : List.of(builder, ghost))
            {
                expectAt(exempt, OracleQuests.ORACLE_PLEA, OracleState.AVAILABLE, "an exempt player");
                for (ResourceKey<Biome> land : OracleQuests.lockableLands())
                {
                    expect(OracleRules.isLandOpen(exempt, land), land.location() + " should be open in "
                            + exempt.gameMode.getGameModeForPlayer());
                }
                for (OracleBoss boss : OracleBoss.values())
                {
                    expect(OracleRules.mayFightBoss(exempt, boss), boss + " should be allowed in "
                            + exempt.gameMode.getGameModeForPlayer());
                }
            }
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void spellsGoOffOnTheirLines(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::spellsGoOffOnTheirLines, (h, players) -> {
            ServerPlayer player = join(h, players, "oracle_spell_watcher", GameType.SURVIVAL);
            Survivor oracle = ModEntities.SURVIVOR.get().create(h.getLevel());
            expect(oracle != null, "couldn't make the Blind Oracle");
            oracle.setRole(SurvivorRole.MENTOR);
            Vec3 at = h.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(2, 0, 0)));
            oracle.moveTo(at.x, at.y, at.z, 0.0F, 0.0F);
            h.getLevel().addFreshEntity(oracle);
            try
            {
                // The giants' hand-in: the spell on its last thanks line, not before.
                OracleQuest giants = OracleQuests.GIANT_SLAYER;
                OracleProgress.setQuest(player, giants);
                hearOffer(player);
                OracleProgress.record(player).enter(OracleState.DONE);
                OracleSpell.Cast before = OracleSpell.lastCast();
                for (int i = 1; i <= giants.doneLines(); i++)
                {
                    expect(OracleDialogue.talk(player, oracle).isPresent(), "the giants' thanks line " + i + " was nothing");
                    boolean cast = OracleSpell.lastCast() != null && OracleSpell.lastCast() != before;
                    expect(cast == (i == giants.doneLines()), "the giants' spell should go off on their last thanks line only (line " + i
                            + (cast ? " cast it)" : " didn't)"));
                }
                expect(OracleSpell.lastCast().quest().equals(giants.id()), "the giants' hand-in should cast their spell");

                // Every other spell: on its quest's offer line, not before or after, and none at its hand-in.
                for (OracleQuest quest : List.of(OracleQuests.SET_IN_STONE, OracleQuests.BENEATH_THE_ROT, OracleQuests.WINGS_CLIPPED))
                {
                    OracleProgress.setQuest(player, quest);
                    before = OracleSpell.lastCast();
                    for (int i = 1; i <= quest.offerLines(); i++)
                    {
                        OracleSpell.Cast was = OracleSpell.lastCast();
                        expect(OracleDialogue.talk(player, oracle).isPresent(), quest.id() + "'s offer line " + i + " was nothing");
                        boolean cast = OracleSpell.lastCast() != null && OracleSpell.lastCast() != was;
                        expect(cast == (i == quest.spellLine()), quest.id() + ": the spell should go off on offer line " + quest.spellLine()
                                + " only (line " + i + (cast ? " cast it)" : " didn't)"));
                    }
                    expect(OracleSpell.lastCast() != before && OracleSpell.lastCast().quest().equals(quest.id()), quest.id()
                            + "'s offer should have cast its spell");
                    OracleProgress.record(player).enter(OracleState.DONE);
                    OracleSpell.Cast handIn = OracleSpell.lastCast();
                    for (int i = 0; i < quest.doneLines(); i++)
                    {
                        OracleDialogue.talk(player, oracle);
                    }
                    expect(OracleSpell.lastCast() == handIn, quest.id() + "'s hand-in shouldn't cast a spell");
                    expect(OracleProgress.current(player) == OracleQuests.next(quest), "after " + quest.id() + "'s thanks the next "
                            + "quest should be waiting, not " + OracleProgress.current(player).id());
                }
            }
            finally
            {
                oracle.discard();
            }
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void barriersFallOnceAndAreSaved(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::barriersFallOnceAndAreSaved, (h, players) -> {
            ServerPlayer player = join(h, players, "oracle_barrier_walker", GameType.SURVIVAL);
            OracleProgress.setQuest(player, OracleQuests.FIRST_FROST);
            for (ResourceKey<Biome> land : OracleQuests.lockableLands())
            {
                expect(!OracleProgress.hasSeenFall(player, land), "a fresh player hasn't seen " + land.location() + " fall");
            }
            OracleProgress.barrierFalls(player, DrossBiomes.CINDER_WASTES, 10, 20);
            OracleProgress.barrierFalls(player, DrossBiomes.BLIGHTWOOD, 30, 40);
            expect(OracleProgress.hasSeenFall(player, DrossBiomes.CINDER_WASTES), "the Cinder Wastes' barrier has fallen for them");
            expect(OracleProgress.hasSeenFall(player, DrossBiomes.SEWERS), "the sewers fall with Blightwood");
            expect(!OracleProgress.hasSeenFall(player, DrossBiomes.RIMEFALL), "Rimefall's hasn't");
            expect(OracleProgress.hasSeenFall(player, DrossBiomes.SILVERWOOD_FOREST), "a land no quest locks has no barrier to fall");

            ServerLevel overworld = h.getLevel().getServer().overworld();
            CompoundTag saved = QuestProgressData.get(overworld).save(new CompoundTag());
            OracleRecord loaded = QuestProgressData.load(saved).oracle(player.getUUID());
            expect(loaded.fallen() == OracleProgress.fallenLandsMask(player), "the fallen barriers should survive a save and load: "
                    + loaded.fallen() + " loaded, " + OracleProgress.fallenLandsMask(player) + " saved");

            OracleProgress.reset(player);
            expect(OracleProgress.fallenLandsMask(player) == 0, "a reset puts every barrier back");
        });
    }

    private static void expectLands(ServerPlayer player, String when, ResourceKey<?>... open)
    {
        List<ResourceKey<?>> openList = List.of(open);
        List<ResourceKey<Biome>> all = new ArrayList<>(DrossBiomes.ALL);
        all.add(DrossBiomes.SEWERS);
        int mask = OracleProgress.openLandsMask(player);
        for (ResourceKey<Biome> land : all)
        {
            boolean expected = openList.contains(land);
            expect(OracleRules.isLandOpen(player, land) == expected, when + ": " + land.location() + " should be "
                    + (expected ? "open" : "locked"));
            int bit = OracleQuests.lockableLands().indexOf(land);
            if (bit >= 0)
            {
                expect(((mask & (1 << bit)) != 0) == expected, when + ": the sync message's bit for " + land.location()
                        + " should say " + (expected ? "open" : "locked"));
            }
        }
    }

    private static void expectBosses(ServerPlayer player, String when, OracleBoss... allowed)
    {
        List<OracleBoss> allowedList = List.of(allowed);
        for (OracleBoss boss : OracleBoss.values())
        {
            boolean expected = allowedList.contains(boss);
            expect(OracleRules.mayFightBoss(player, boss) == expected, when + ": " + boss + " should "
                    + (expected ? "" : "not ") + "fight them");
        }
    }

    // ------------------------------------------------------------------ Saving and the commands

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void progressSurvivesASaveAndLoad(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::progressSurvivesASaveAndLoad, (h, players) -> {
            ServerPlayer hunter = join(h, players, "oracle_saver", GameType.CREATIVE);
            OracleProgress.setQuest(hunter, OracleQuests.FIRST_FROST);
            hearOffer(hunter);
            killBy(spawn(h, ModEntities.RIMEHORN.get(), MIDDLE.offset(4, 0, -4)), hunter);
            ServerPlayer talker = join(h, players, "oracle_half_listener", GameType.CREATIVE);
            OracleProgress.setQuest(talker, OracleQuests.BENEATH_THE_ROT);
            click(talker);

            ServerLevel overworld = h.getLevel().getServer().overworld();
            CompoundTag saved = QuestProgressData.get(overworld).save(new CompoundTag());
            QuestProgressData loaded = QuestProgressData.load(saved);
            OracleRecord hunterAgain = loaded.oracle(hunter.getUUID());
            expect(hunterAgain.quest() == OracleQuests.FIRST_FROST && hunterAgain.state() == OracleState.ACTIVE
                    && hunterAgain.kills() == 1, "loaded: " + hunterAgain.quest().id() + " " + hunterAgain.state() + " kills "
                    + hunterAgain.kills() + " (First Frost, ACTIVE, 1 expected)");
            OracleRecord talkerAgain = loaded.oracle(talker.getUUID());
            expect(talkerAgain.quest() == OracleQuests.BENEATH_THE_ROT && talkerAgain.state() == OracleState.AVAILABLE
                    && talkerAgain.line() == 1, "loaded mid-offer: " + talkerAgain.quest().id() + " " + talkerAgain.state()
                    + " line " + talkerAgain.line() + " (Beneath the Rot, AVAILABLE, line 1 expected)");

            // An old save from before the Oracle: the first quest, waiting.
            UUID oldId = UUID.randomUUID();
            CompoundTag entry = new CompoundTag();
            entry.putUUID("Id", oldId);
            entry.putBoolean("KeyGiven", true);
            ListTag list = new ListTag();
            list.add(entry);
            CompoundTag old = new CompoundTag();
            old.put("Players", list);
            OracleRecord fresh = QuestProgressData.load(old).oracle(oldId);
            expect(fresh.quest() == OracleQuests.ORACLE_PLEA && fresh.state() == OracleState.AVAILABLE
                    && fresh.kills() == 0 && fresh.line() == 0, "an old save should start at the first quest, waiting");
        });
    }

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void resetSkipAndSet(GameTestHelper helper)
    {
        run(helper, OracleQuestGameTest::resetSkipAndSet, (h, players) -> {
            ServerPlayer player = join(h, players, "oracle_tester", GameType.CREATIVE);
            var source = player.createCommandSourceStack();

            expect(QuestCommands.set(source, player, "nonsense") == 0, "set should refuse an unknown quest");
            expect(QuestCommands.set(source, player, OracleQuests.INTO_THE_ASHES.id()) == 1, "set into_the_ashes failed");
            expectAt(player, OracleQuests.INTO_THE_ASHES, OracleState.AVAILABLE, "after set");
            expect(QuestCommands.skip(source, player) == 1, "skip failed");
            expect(DrossAdvancements.has(player, DrossAdvancements.INTO_THE_ASHES), "skipping a kill quest grants its advancement");
            expectAt(player, OracleQuests.SET_IN_STONE, OracleState.AVAILABLE, "after skipping Into the Ashes");
            QuestCommands.skip(source, player);
            expect(!DrossAdvancements.has(player, DrossAdvancements.SET_IN_STONE), "skipping a boss quest mustn't grant the boss's advancement");
            expectAt(player, OracleQuests.BENEATH_THE_ROT, OracleState.AVAILABLE, "after skipping the Colossus");

            // A DONE quest skips to the next.
            hearOffer(player);
            for (int i = 0; i < OracleQuests.BENEATH_THE_ROT.count(); i++)
            {
                killBy(spawn(h, ModEntities.RAT.get(), MIDDLE.offset(i - 2, 0, 5)), player);
            }
            expectAt(player, OracleQuests.BENEATH_THE_ROT, OracleState.DONE, "after three rats");
            QuestCommands.skip(source, player);
            expectAt(player, OracleQuests.DOWN_THE_DRAIN, OracleState.AVAILABLE, "after skipping the thanks");

            QuestCommands.set(source, player, OracleQuests.DEFEAT_THE_DROSS.id());
            QuestCommands.skip(source, player);
            expect(OracleProgress.isFinished(player) && DrossAdvancements.has(player, DrossAdvancements.DEFEAT_THE_DROSS),
                    "skipping the last quest grants Defeat the Dross and finishes them");
            expect(QuestCommands.skip(source, player) == 0, "nothing to skip once finished");
            QuestCommands.status(source, player);

            DrossAdvancements.grant(player, DrossAdvancements.SET_IN_STONE);
            QuestCommands.reset(source, player);
            expectAt(player, OracleQuests.ORACLE_PLEA, OracleState.AVAILABLE, "after reset");
            for (String id : OracleQuests.ownAdvancements())
            {
                expect(!DrossAdvancements.has(player, id), "reset should take away dross:" + id);
            }
            expect(DrossAdvancements.has(player, DrossAdvancements.SET_IN_STONE), "reset leaves the bosses' advancements alone");
            expect(OracleProgress.kills(player) == 0, "reset clears the kills");
        });
    }
}
