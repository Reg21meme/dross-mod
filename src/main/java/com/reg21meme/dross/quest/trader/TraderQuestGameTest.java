package com.reg21meme.dross.quest.trader;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.golem.ColossusGameTest;
import com.reg21meme.dross.boss.knight.KnightRuinDesigns;
import com.reg21meme.dross.boss.knight.ruin.KnightRuinClearedEvent;
import com.reg21meme.dross.boss.knight.ruin.RuinSite;
import com.reg21meme.dross.quest.DrossAdvancements;
import com.reg21meme.dross.quest.QuestProgressData;
import com.reg21meme.dross.quest.oracle.OracleMarker;
import com.reg21meme.dross.quest.oracle.OracleSyncMessage;
import com.reg21meme.dross.registry.ModEntities;
import com.reg21meme.dross.registry.ModItems;
import com.reg21meme.dross.villager.DrossTrader;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.Util;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

/**
 * Headless tests (GameTest) for the Dross Trader's quest (PRE_DROSS_PLAN.md, stage T1; {@code quest.trader}), with
 * stand-in players and a trader in the Cinder Colossus tests' arena:
 * <ul>
 *   <li>every line the talk asks for is in the lang file (and no more), the advancements' chain (A Weathered Letter →
 *       Knightfall → The Portal Orb → Enter the Dross), the Portal Orb registered and the Rift Key, the Dross Compass,
 *       Proven Worthy and Keymaster gone;</li>
 *   <li>his offer: the red ? marker, a line a click, then your "Yes, of course." in chat as you, his thanks, 3 golden
 *       apples and the yellow ... marker; his shop never opens;</li>
 *   <li>a ruin cleared for the first time counts ("Knight ruins cleared: n / 3" above the hotbar), a repeat clear doesn't,
 *       nor one before his offer is accepted; the third makes it READY (the green !) with "Knightfall";</li>
 *   <li>the hand-in: a line a click, one Portal Orb ("The Portal Orb"), the cathedral's spot rounded to 100, then no
 *       marker and his reminder of the spot;</li>
 *   <li>a lost orb: his line, a new round of three new ruins (a ruin cleared before doesn't count again), one new orb;</li>
 *   <li>after the Dross ("Enter the Dross"): his three lines in turn; the shop (the Guide Book) opens only once he's made
 *       you an orb;</li>
 *   <li>the nearest ruin you haven't cleared comes in a moment (worked out off the server thread), rounded to 10;</li>
 *   <li>the sync message carries both markers; the progress survives a save and load.</li>
 * </ul>
 */
@GameTestHolder(TraderQuestGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class TraderQuestGameTest
{
    /**
     * The tests' GameTest namespace: the mod's, so the normal run has them. To run them alone, set it to a namespace of
     * their own (say "dross_trader_quest") and run {@code .\gradlew.bat runGameTestServer
     * -PgameTestNamespaces=dross_trader_quest -PgameTestWorld=<a new folder>}: the arena is then made under it too.
     */
    static final String NAMESPACE = Dross.MODID;
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final String BATCH = "dross_trader_quest";
    private static final int TIMEOUT = ColossusGameTest.ARENA_WAIT_TICKS + 400;
    private static final BlockPos MIDDLE = ColossusGameTest.MIDDLE;
    /** How long (real time) the nearest ruin's line is waited for. */
    private static final long RUIN_LINE_MILLIS = 180_000L;
    /** The ticks that wait may take, a backstop behind the clock (a test server runs ticks very fast). */
    private static final int RUIN_LINE_BACKSTOP_TICKS = 200_000;
    /** Made-up ruins' x (far from any real one), each test its own. */
    private static final AtomicInteger FAKE_RUINS = new AtomicInteger();

    private TraderQuestGameTest() {}

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

    /** Runs a test once its arena has loaded, with stand-in players and traders that go afterwards (also when it fails). */
    private static void run(GameTestHelper helper, Consumer<GameTestHelper> self, Body body)
    {
        if (ColossusGameTest.waitForArena(helper, self))
        {
            return;
        }
        Cast cast = new Cast();
        try
        {
            body.run(helper, cast);
        }
        finally
        {
            cast.leave();
        }
        helper.succeed();
    }

    @FunctionalInterface
    private interface Body
    {
        void run(GameTestHelper helper, Cast cast);
    }

    /** The stand-in players and traders of one test, to be cleared away after it. */
    private static final class Cast
    {
        final List<TestPlayer> players = new ArrayList<>();
        final List<DrossTrader> traders = new ArrayList<>();

        TestPlayer player(GameTestHelper helper, String name)
        {
            TestPlayer player = TestPlayer.join(helper, name, MIDDLE);
            players.add(player);
            return player;
        }

        DrossTrader trader(GameTestHelper helper)
        {
            ServerLevel level = helper.getLevel();
            DrossTrader trader = ModEntities.TRADER.get().create(level);
            if (trader == null)
            {
                throw new GameTestAssertException("couldn't make a Dross Trader");
            }
            Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(MIDDLE.offset(2, 0, 0)));
            trader.moveTo(pos.x, pos.y, pos.z, 90.0F, 0.0F);
            trader.setNoAi(true);
            level.addFreshEntity(trader);
            traders.add(trader);
            return trader;
        }

        void leave()
        {
            for (DrossTrader trader : traders)
            {
                trader.setTradingPlayer(null);
                trader.discard();
            }
            players.forEach(TestPlayer::leave);
        }
    }

    /**
     * A stand-in player on the server's player list who remembers every message sent to him, chat and above the hotbar
     * (vanilla's {@code makeMockServerPlayerInLevel} crashes under Forge, so he gets an in-memory network channel).
     */
    private static final class TestPlayer extends ServerPlayer
    {
        final List<Component> chat = new ArrayList<>();
        final List<Component> overlay = new ArrayList<>();

        private TestPlayer(ServerLevel level, String name)
        {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        }

        static TestPlayer join(GameTestHelper helper, String name, BlockPos at)
        {
            ServerLevel level = helper.getLevel();
            TestPlayer player = new TestPlayer(level, name);
            Vec3 pos = helper.absoluteVec(Vec3.atBottomCenterOf(at));
            player.moveTo(pos.x, pos.y, pos.z, 0.0F, 0.0F);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            if (player.serverLevel() != level || player.position().distanceToSqr(pos) > 0.01D)
            {
                player.teleportTo(level, pos.x, pos.y, pos.z, 0.0F, 0.0F);
            }
            return player;
        }

        @Override
        public void sendSystemMessage(Component message, boolean actionBar)
        {
            (actionBar ? overlay : chat).add(message);
            super.sendSystemMessage(message, actionBar);
        }

        /** Logs him out. Safe to call twice. */
        void leave()
        {
            if (server.getPlayerList().getPlayer(getUUID()) == this)
            {
                server.getPlayerList().remove(this);
            }
        }
    }

    private static void expect(boolean condition, String message)
    {
        if (!condition)
        {
            throw new GameTestAssertException(message);
        }
    }

    private static String name(ServerPlayer player)
    {
        return player.getGameProfile().getName();
    }

    /** The trader's line in a chat message ({@code <Dross Trader> line}), or null if it isn't one of his. */
    private static TranslatableContents traderLine(Component message)
    {
        if (message.getContents() instanceof TranslatableContents chat && "chat.type.text".equals(chat.getKey()) && chat.getArgs().length == 2
                && chat.getArgs()[0] instanceof Component speaker && !speaker.getString().isEmpty()
                && chat.getArgs()[1] instanceof Component line && line.getContents() instanceof TranslatableContents said)
        {
            return said;
        }
        return null;
    }

    /** Who says a chat message ({@code <speaker> line}): their name, or "" if it isn't one. */
    private static String speakerOf(Component message)
    {
        if (message.getContents() instanceof TranslatableContents chat && "chat.type.text".equals(chat.getKey()) && chat.getArgs().length == 2
                && chat.getArgs()[0] instanceof Component speaker)
        {
            return speaker.getString();
        }
        return "";
    }

    /**
     * The lang keys of every chat line said to him since {@code from} (his and his own answer), in order. The game's
     * own messages (an advancement's announcement, "... has reached the goal [The Portal Orb]") aren't lines: skipped.
     */
    private static List<String> linesSince(TestPlayer player, int from)
    {
        List<String> keys = new ArrayList<>();
        for (int i = from; i < player.chat.size(); i++)
        {
            TranslatableContents line = traderLine(player.chat.get(i));
            if (line != null)
            {
                keys.add(line.getKey());
            }
        }
        return keys;
    }

    /**
     * The last line said to him (the game's own messages, an advancement's announcement, skipped): its arguments, for a
     * line of his with this key; fails if it isn't.
     */
    private static Object[] lastLineArgs(TestPlayer player, String key)
    {
        TranslatableContents line = null;
        for (int i = player.chat.size() - 1; i >= 0 && line == null; i--)
        {
            line = traderLine(player.chat.get(i));
        }
        expect(line != null, name(player) + " was told no line");
        expect(key.equals(line.getKey()), "the last line to " + name(player) + " should be " + key + ", but it's " + line.getKey());
        return line.getArgs();
    }

    /** Right-clicks the trader once; returns the lang keys of the chat lines that click brought (at once). */
    private static List<String> click(DrossTrader trader, TestPlayer player)
    {
        int before = player.chat.size();
        trader.mobInteract(player, InteractionHand.MAIN_HAND);
        return linesSince(player, before);
    }

    /** How many of this item they have, all their inventory over. */
    private static int count(ServerPlayer player, Item item)
    {
        int count = 0;
        for (int i = 0; i < player.getInventory().getContainerSize(); i++)
        {
            ItemStack stack = player.getInventory().getItem(i);
            if (stack.is(item))
            {
                count += stack.getCount();
            }
        }
        return count;
    }

    private static void expectState(ServerPlayer player, TraderState state, OracleMarker marker, String when)
    {
        expect(TraderProgress.state(player) == state && TraderProgress.marker(player) == marker, when + ": expected " + state + " ("
                + marker + "), but it's " + TraderProgress.state(player) + " (" + TraderProgress.marker(player) + ")");
    }

    /** A made-up ruin far from any real one (only its id matters to the trader's quest). */
    private static RuinSite fakeRuin()
    {
        int n = FAKE_RUINS.incrementAndGet();
        return new RuinSite(25_000_000 - n * 64, 25_000_000, 64, Direction.SOUTH, KnightRuinDesigns.ALL.get(0), false);
    }

    /** The boss area's signal that this player has cleared this ruin. */
    private static void clear(ServerPlayer player, RuinSite ruin, boolean firstTime)
    {
        MinecraftForge.EVENT_BUS.post(new KnightRuinClearedEvent(player, ruin, firstTime, true));
    }

    /** Clicks through his offer (six lines, then your answer): the ruins are going. */
    private static void acceptOffer(DrossTrader trader, TestPlayer player)
    {
        for (int i = 0; i <= TraderProgress.OFFER_LINES; i++)
        {
            click(trader, player);
        }
        expectState(player, TraderState.RUINS, OracleMarker.ACTIVE, "after his offer and your answer");
    }

    /** His offer accepted and three new ruins cleared: READY. */
    private static void toReady(DrossTrader trader, TestPlayer player)
    {
        acceptOffer(trader, player);
        for (int i = 0; i < TraderProgress.RUINS_NEEDED; i++)
        {
            clear(player, fakeRuin(), true);
        }
        expectState(player, TraderState.READY, OracleMarker.DONE, "after three new ruins");
    }

    // ------------------------------------------------------------------ The files

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void everyLineAdvancementAndItemIsThere(GameTestHelper helper)
    {
        run(helper, TraderQuestGameTest::everyLineAdvancementAndItemIsThere, (h, cast) -> {
            JsonObject lang;
            try (InputStream in = TraderQuestGameTest.class.getResourceAsStream("/assets/dross/lang/en_us.json"))
            {
                expect(in != null, "en_us.json isn't on the classpath");
                lang = JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8)).getAsJsonObject();
            }
            catch (java.io.IOException e)
            {
                throw new GameTestAssertException("couldn't read en_us.json: " + e);
            }
            List<String> keys = new ArrayList<>();
            for (int i = 1; i <= TraderProgress.OFFER_LINES; i++)
            {
                keys.add("dross.trader.offer." + i);
            }
            for (int i = 1; i <= TraderProgress.DONE_LINES; i++)
            {
                keys.add("dross.trader.done." + i);
            }
            for (int i = 1; i <= TraderProgress.AFTER_LINES; i++)
            {
                keys.add("dross.trader.after." + i);
            }
            keys.addAll(List.of("dross.trader.answer", "dross.trader.accepted.1", "dross.trader.accepted.2",
                    "dross.trader.accepted.2.no_ruin", "dross.trader.remind", "dross.trader.remind.no_ruin", "dross.trader.remind_new",
                    "dross.trader.remind_new.no_ruin", "dross.trader.done." + TraderProgress.CATHEDRAL_LINE + ".none",
                    "dross.trader.orb_given", "dross.trader.orb_given.none", "dross.trader.orb_lost", "dross.trader.new_orb",
                    "dross.trader.progress", "dross.trader.return", "item.dross.portal_orb",
                    "advancements.dross.knightfall.title", "advancements.dross.knightfall.description",
                    "advancements.dross.portal_orb.title", "advancements.dross.portal_orb.description"));
            List<String> missing = keys.stream().filter(key -> !lang.has(key)).toList();
            expect(missing.isEmpty(), "lang keys missing: " + missing);
            List<String> extra = new ArrayList<>();
            for (String key : List.of("dross.trader.offer." + (TraderProgress.OFFER_LINES + 1), "dross.trader.done." + (TraderProgress.DONE_LINES + 1),
                    "dross.trader.after." + (TraderProgress.AFTER_LINES + 1), "dross.trader.dialogue.intro", "item.dross.rift_key",
                    "item.dross.dross_compass", "advancements.dross.proven_worthy.title", "advancements.dross.keymaster.title",
                    "dross.trader.location", "dross.site.location"))
            {
                if (lang.has(key))
                {
                    extra.add(key);
                }
            }
            expect(extra.isEmpty(), "lang keys that shouldn't be there (lines the talk doesn't count, or the old quest's): " + extra);

            // The chain: A Weathered Letter → Knightfall → The Portal Orb → Enter the Dross; the old two gone.
            var advancements = h.getLevel().getServer().getAdvancements();
            String parent = DrossAdvancements.WEATHERED_LETTER;
            for (String id : List.of(DrossAdvancements.KNIGHTFALL, DrossAdvancements.PORTAL_ORB, DrossAdvancements.ENTERED_THE_DROSS))
            {
                Advancement advancement = advancements.getAdvancement(new ResourceLocation(Dross.MODID, id));
                expect(advancement != null, "the advancement dross:" + id + " doesn't exist");
                expect(advancement.getParent() != null && advancement.getParent().getId().equals(new ResourceLocation(Dross.MODID, parent)),
                        "dross:" + id + "'s parent should be dross:" + parent + ", but it's "
                                + (advancement.getParent() == null ? "none" : advancement.getParent().getId()));
                parent = id;
            }
            for (String gone : List.of("proven_worthy", "keymaster"))
            {
                expect(advancements.getAdvancement(new ResourceLocation(Dross.MODID, gone)) == null, "dross:" + gone + " should be gone");
            }

            // The Portal Orb in, the Rift Key and the Dross Compass out; the orb fireproof, one to a stack, never despawning.
            expect(BuiltInRegistries.ITEM.containsKey(new ResourceLocation(Dross.MODID, "portal_orb")), "dross:portal_orb isn't registered");
            for (String gone : List.of("rift_key", "dross_compass"))
            {
                expect(!BuiltInRegistries.ITEM.containsKey(new ResourceLocation(Dross.MODID, gone)), "dross:" + gone + " should be gone");
            }
            ItemStack orb = new ItemStack(ModItems.PORTAL_ORB.get());
            expect(orb.getMaxStackSize() == 1 && ModItems.PORTAL_ORB.get().isFireResistant()
                    && orb.getEntityLifespan(h.getLevel()) == Integer.MAX_VALUE, "the Portal Orb should stack to 1, be fireproof and never despawn");
            expect(TraderProgress.rounded(7531, TraderProgress.CATHEDRAL_ROUNDING) == 7500
                    && TraderProgress.rounded(1234, TraderProgress.CATHEDRAL_ROUNDING) == 1200
                    && TraderProgress.rounded(-1267, TraderProgress.RUIN_ROUNDING) == -1270, "the rounding is off");
        });
    }

    // ------------------------------------------------------------------ His offer

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void offerGivesApplesAndStartsTheRuins(GameTestHelper helper)
    {
        run(helper, TraderQuestGameTest::offerGivesApplesAndStartsTheRuins, (h, cast) -> {
            TestPlayer player = cast.player(h, "trader_newcomer");
            DrossTrader trader = cast.trader(h);
            expectState(player, TraderState.OFFER, OracleMarker.NEW, "a fresh player");
            for (int i = 1; i <= TraderProgress.OFFER_LINES; i++)
            {
                List<String> lines = click(trader, player);
                expect(lines.equals(List.of("dross.trader.offer." + i)), "click " + i + " should be his offer line " + i + ", but it's " + lines);
                expect(TraderProgress.record(player).line() == i, "after " + i + " lines he should be at line " + i);
                expect(!trader.isTrading(), "his trading screen opened during his offer");
            }
            expectState(player, TraderState.OFFER, OracleMarker.NEW, "after his offer, before your answer");
            expect(count(player, Items.GOLDEN_APPLE) == 0, "the golden apples came before your answer");

            List<String> answer = click(trader, player);
            expect(answer.equals(List.of("dross.trader.answer", "dross.trader.accepted.1")),
                    "the click after his offer should be your answer and his thanks, but it's " + answer);
            String yours = speakerOf(player.chat.get(player.chat.size() - 2));
            String his = speakerOf(player.chat.get(player.chat.size() - 1));
            expect(yours.equals(player.getDisplayName().getString()) && his.equals(trader.getDisplayName().getString()),
                    "\"Yes, of course.\" should be said as the player (it's said by \"" + yours + "\") and his thanks as him (\"" + his + "\")");
            expect(count(player, Items.GOLDEN_APPLE) == TraderProgress.GOLDEN_APPLES, "you should get " + TraderProgress.GOLDEN_APPLES
                    + " golden apples, not " + count(player, Items.GOLDEN_APPLE));
            expectState(player, TraderState.RUINS, OracleMarker.ACTIVE, "after your answer");
            expect(TraderProgress.round(player) == 1 && TraderProgress.ruinsCleared(player) == 0, "a new quest should be round 1, nothing cleared");
            expect(!DrossAdvancements.has(player, DrossAdvancements.KNIGHTFALL), "Knightfall came with the offer");

            List<String> next = click(trader, player);
            expect(next.isEmpty() || next.get(0).equals("dross.trader.accepted.2") || next.get(0).equals("dross.trader.accepted.2.no_ruin"),
                    "the next click should bring his line with the nearest ruin (at once or a moment later), not " + next);
            expect(!trader.isTrading() && !TraderProgress.isShopOpen(player), "his shop opened before the orb");
            expect(count(player, Items.GOLDEN_APPLE) == TraderProgress.GOLDEN_APPLES, "the apples came twice");
        });
    }

    // ------------------------------------------------------------------ The ruins

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void firstClearsCountRepeatsDont(GameTestHelper helper)
    {
        run(helper, TraderQuestGameTest::firstClearsCountRepeatsDont, (h, cast) -> {
            TestPlayer player = cast.player(h, "trader_ruin_clearer");
            DrossTrader trader = cast.trader(h);
            RuinSite early = fakeRuin();
            clear(player, early, true);
            expect(TraderProgress.ruinsCleared(player) == 0 && TraderProgress.state(player) == TraderState.OFFER,
                    "a ruin cleared before his offer is accepted shouldn't count");

            acceptOffer(trader, player);
            clear(player, early, false);
            expect(TraderProgress.ruinsCleared(player) == 0, "clearing a ruin again (its knights came back) shouldn't count");

            RuinSite first = fakeRuin();
            int overlays = player.overlay.size();
            clear(player, first, true);
            expect(TraderProgress.ruinsCleared(player) == 1, "a new ruin should count 1, not " + TraderProgress.ruinsCleared(player));
            expect(player.overlay.size() == overlays + 1 && player.overlay.get(overlays).getContents() instanceof TranslatableContents t
                    && "dross.trader.progress".equals(t.getKey()) && java.util.Arrays.equals(t.getArgs(), new Object[] {1, TraderProgress.RUINS_NEEDED}),
                    "\"Knight ruins cleared: 1 / 3\" should show above the hotbar; it showed " + player.overlay.subList(overlays, player.overlay.size()));
            clear(player, first, false);
            expect(TraderProgress.ruinsCleared(player) == 1, "the same ruin cleared again shouldn't count again");
            expectState(player, TraderState.RUINS, OracleMarker.ACTIVE, "after one ruin");

            clear(player, fakeRuin(), true);
            expect(!DrossAdvancements.has(player, DrossAdvancements.KNIGHTFALL), "Knightfall came after two ruins");
            clear(player, fakeRuin(), true);
            expect(TraderProgress.ruinsCleared(player) == TraderProgress.RUINS_NEEDED, "three new ruins should count 3");
            expectState(player, TraderState.READY, OracleMarker.DONE, "after three new ruins");
            expect(DrossAdvancements.has(player, DrossAdvancements.KNIGHTFALL), "the third ruin should grant Knightfall");
            expect(player.overlay.get(player.overlay.size() - 1).getContents() instanceof TranslatableContents done
                    && "dross.trader.return".equals(done.getKey()), "the third ruin should say to go back to him");

            clear(player, fakeRuin(), true);
            expect(TraderProgress.ruinsCleared(player) == TraderProgress.RUINS_NEEDED && TraderProgress.state(player) == TraderState.READY,
                    "a fourth ruin shouldn't change anything");
        });
    }

    // ------------------------------------------------------------------ The hand-in

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void handInGivesOneOrbAndTheCathedral(GameTestHelper helper)
    {
        run(helper, TraderQuestGameTest::handInGivesOneOrbAndTheCathedral, (h, cast) -> {
            TestPlayer player = cast.player(h, "trader_orb_taker");
            DrossTrader trader = cast.trader(h);
            toReady(trader, player);
            var cathedral = TraderProgress.cathedral(h.getLevel().getServer());
            for (int i = 1; i <= TraderProgress.DONE_LINES; i++)
            {
                expect(count(player, ModItems.PORTAL_ORB.get()) == (i <= TraderProgress.ORB_LINE ? 0 : 1),
                        "before hand-in line " + i + " they have " + count(player, ModItems.PORTAL_ORB.get()) + " orbs");
                List<String> lines = click(trader, player);
                String expected = "dross.trader.done." + i + (i == TraderProgress.CATHEDRAL_LINE && cathedral.isEmpty() ? ".none" : "");
                expect(lines.equals(List.of(expected)), "hand-in click " + i + " should be " + expected + ", but it's " + lines);
                if (i == TraderProgress.ORB_LINE)
                {
                    expect(count(player, ModItems.PORTAL_ORB.get()) == 1, "his line " + i + " should make exactly one Portal Orb, not "
                            + count(player, ModItems.PORTAL_ORB.get()));
                    expect(DrossAdvancements.has(player, DrossAdvancements.PORTAL_ORB), "the orb should grant The Portal Orb");
                }
                if (i == TraderProgress.CATHEDRAL_LINE && cathedral.isPresent())
                {
                    Object[] args = lastLineArgs(player, expected);
                    BlockPos spot = cathedral.get();
                    expect(java.util.Arrays.equals(args, new Object[] {TraderProgress.rounded(spot.getX(), TraderProgress.CATHEDRAL_ROUNDING),
                                    TraderProgress.rounded(spot.getZ(), TraderProgress.CATHEDRAL_ROUNDING)}),
                            "the cathedral line says " + java.util.Arrays.toString(args) + " for the frame at " + spot.toShortString());
                    expect(Math.abs((int) args[0] - spot.getX()) <= 50 && Math.abs((int) args[1] - spot.getZ()) <= 50
                            && (int) args[0] % 100 == 0 && (int) args[1] % 100 == 0, "the cathedral's spot isn't rounded to 100");
                }
                expect(!trader.isTrading(), "his trading screen opened during the hand-in");
            }
            expectState(player, TraderState.ORB_GIVEN, OracleMarker.NONE, "after his hand-in");
            expect(count(player, ModItems.PORTAL_ORB.get()) == 1 && TraderProgress.orbsMade(player) == 1, "the hand-in should give one orb in all");
            expect(TraderProgress.isShopOpen(player), "his shop should be open to them once they have an orb");

            String reminder = "dross.trader.orb_given" + (cathedral.isEmpty() ? ".none" : "");
            List<String> again = click(trader, player);
            expect(again.equals(List.of(reminder)), "a click after the orb should be his reminder " + reminder + ", not " + again);
            expect(!trader.isTrading(), "his shop opened with nothing to sell (no \"Enter the Dross\": no Guide Book)");
            expect(count(player, ModItems.PORTAL_ORB.get()) == 1, "the reminder gave another orb");
        });
    }

    // ------------------------------------------------------------------ A lost orb

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void lostOrbNeedsThreeNewRuins(GameTestHelper helper)
    {
        run(helper, TraderQuestGameTest::lostOrbNeedsThreeNewRuins, (h, cast) -> {
            TestPlayer player = cast.player(h, "trader_orb_loser");
            DrossTrader trader = cast.trader(h);
            acceptOffer(trader, player);
            List<RuinSite> cleared = List.of(fakeRuin(), fakeRuin(), fakeRuin());
            cleared.forEach(ruin -> clear(player, ruin, true));
            for (int i = 0; i < TraderProgress.DONE_LINES; i++)
            {
                click(trader, player);
            }
            expectState(player, TraderState.ORB_GIVEN, OracleMarker.NONE, "after the first orb");

            // The orb's lost (thrown away, say).
            player.getInventory().clearOrCountMatchingItems(stack -> stack.is(ModItems.PORTAL_ORB.get()), -1, player.inventoryMenu.getCraftSlots());
            expect(!TraderProgress.hasOrb(player), "the orb should be gone from their inventory");
            if (TraderProgress.isCathedralLit(h.getLevel().getServer()))
            {
                // (In a world whose cathedral portal is lit, nobody needs a new orb: he only reminds them where it is.)
                List<String> lit = click(trader, player);
                expect(lit.equals(List.of("dross.trader.orb_given")), "with the cathedral lit a click should remind, not " + lit);
                LOGGER.info("[trader quest test] The cathedral's portal is lit in this world: the lost orb's round can't be tested "
                        + "here (he reminded them where it is, as he should).");
                return;
            }
            List<String> lost = click(trader, player);
            expect(lost.equals(List.of("dross.trader.orb_lost")), "with no orb a click should be his lost-orb line, not " + lost);
            expectState(player, TraderState.RUINS, OracleMarker.ACTIVE, "after the lost-orb line");
            expect(TraderProgress.round(player) == 2 && TraderProgress.ruinsCleared(player) == 0, "a lost orb should start round 2 from 0");

            // A ruin cleared before doesn't count again; two new ones do (his souls left over make up the third).
            clear(player, cleared.get(0), false);
            expect(TraderProgress.ruinsCleared(player) == 0, "a ruin cleared for the first orb shouldn't count for the new one");
            expect(TraderProgress.ruinsNeeded(player) == TraderProgress.NEW_ORB_RUINS, "a new orb should want "
                    + TraderProgress.NEW_ORB_RUINS + " ruins, not " + TraderProgress.ruinsNeeded(player));
            clear(player, fakeRuin(), true);
            expectState(player, TraderState.RUINS, OracleMarker.ACTIVE, "after one new ruin for a new orb");
            clear(player, fakeRuin(), true);
            expectState(player, TraderState.READY, OracleMarker.DONE, "after two new ruins for a new orb");

            List<String> again = click(trader, player);
            expect(again.equals(List.of("dross.trader.new_orb")), "the new orb's hand-in should be one line, not " + again);
            expect(count(player, ModItems.PORTAL_ORB.get()) == 1 && TraderProgress.orbsMade(player) == 2, "the new hand-in should give one orb ("
                    + count(player, ModItems.PORTAL_ORB.get()) + " now, " + TraderProgress.orbsMade(player) + " made in all)");
            expectState(player, TraderState.ORB_GIVEN, OracleMarker.NONE, "after the new orb");
            List<String> reminder = click(trader, player);
            expect(!reminder.isEmpty() && reminder.get(0).startsWith("dross.trader.orb_given"), "with the new orb a click should remind, not " + reminder);
        });
    }

    // ------------------------------------------------------------------ After the Dross, and his shop

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void afterTheDrossLinesAndTheShop(GameTestHelper helper)
    {
        run(helper, TraderQuestGameTest::afterTheDrossLinesAndTheShop, (h, cast) -> {
            TestPlayer player = cast.player(h, "trader_dross_veteran");
            DrossTrader trader = cast.trader(h);
            acceptOffer(trader, player);
            // Been to the Dross before he made them an orb (a command, a friend's portal): still no shop.
            DrossAdvancements.grant(player, DrossAdvancements.ENTERED_THE_DROSS);
            click(trader, player);
            click(trader, player);
            expect(!trader.isTrading() && trader.getTradingPlayer() == null, "his shop opened before he made them an orb");

            TraderProgress.complete(player);
            expectState(player, TraderState.ORB_GIVEN, OracleMarker.NONE, "after the trader part of /dross quest complete");
            expect(DrossAdvancements.has(player, DrossAdvancements.KNIGHTFALL) && DrossAdvancements.has(player, DrossAdvancements.PORTAL_ORB),
                    "/dross quest complete should grant Knightfall and The Portal Orb");
            for (int round = 0; round < 2; round++)
            {
                for (int i = 1; i <= TraderProgress.AFTER_LINES; i++)
                {
                    List<String> lines = click(trader, player);
                    expect(lines.equals(List.of("dross.trader.after." + i)), "after the Dross his line " + i + " should come, not " + lines);
                    expect(trader.getTradingPlayer() == player, "his shop should open after his line once he's made them an orb");
                    expect(trader.getOffers().stream().anyMatch(offer -> offer.getResult().is(ModItems.DROSS_GUIDE_BOOK.get())),
                            "his shop should sell the Guide Book to a player who has been to the Dross");
                    player.closeContainer(); // as the game does when the screen is closed: the trade ends
                    expect(trader.getTradingPlayer() == null && trader.getOffers().stream()
                                    .noneMatch(offer -> offer.getResult().is(ModItems.DROSS_GUIDE_BOOK.get())),
                            "the trade should end, and the Guide Book's offer go, when the screen is closed");
                }
            }
            expect(!TraderProgress.hasOrb(player), "(no orb was given here)");
            expect(TraderProgress.state(player) == TraderState.ORB_GIVEN, "with no orb but having been to the Dross, no new round should start");
        });
    }

    // ------------------------------------------------------------------ The markers' message and the save

    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = TIMEOUT)
    public static void markersAreSentAndProgressIsSaved(GameTestHelper helper)
    {
        run(helper, TraderQuestGameTest::markersAreSentAndProgressIsSaved, (h, cast) -> {
            for (OracleMarker oracle : OracleMarker.values())
            {
                for (OracleMarker trader : OracleMarker.values())
                {
                    FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
                    new OracleSyncMessage(oracle, trader, 5, 3).encode(buf);
                    OracleSyncMessage back = OracleSyncMessage.decode(buf);
                    expect(back.marker() == oracle && back.traderMarker() == trader && back.openLands() == 5 && back.fallenLands() == 3,
                            "the sync message came back as " + back + " for " + oracle + ", " + trader);
                }
            }
            expect(TraderState.OFFER.marker() == OracleMarker.NEW && TraderState.RUINS.marker() == OracleMarker.ACTIVE
                            && TraderState.READY.marker() == OracleMarker.DONE && TraderState.ORB_GIVEN.marker() == OracleMarker.NONE,
                    "each step's marker: ? while his offer waits, ... while clearing ruins, ! when done, none after");

            TestPlayer player = cast.player(h, "trader_saver");
            DrossTrader trader = cast.trader(h);
            acceptOffer(trader, player);
            clear(player, fakeRuin(), true);
            ServerLevel overworld = h.getLevel().getServer().overworld();
            CompoundTag saved = QuestProgressData.get(overworld).save(new CompoundTag());
            TraderRecord loaded = QuestProgressData.load(saved).trader(player.getUUID());
            expect(loaded.state() == TraderState.RUINS && loaded.ruins() == 1 && loaded.round() == 1 && loaded.orbs() == 0,
                    "after a save and load the trader quest is " + loaded.state() + ", " + loaded.ruins() + " ruins, round " + loaded.round());
            expect(QuestProgressData.load(new CompoundTag()).trader(player.getUUID()).state() == TraderState.OFFER,
                    "a save without the trader's quest should start at his offer");
        });
    }

    // ------------------------------------------------------------------ The nearest ruin, worked out off the server thread

    /**
     * After you agree, his second click names the nearest ruin you haven't cleared a moment later (or says he can't feel
     * one): the line comes by itself (waited for by the clock), its X and Z rounded to 10, a ruin you haven't cleared.
     */
    @GameTest(template = ColossusGameTest.ARENA, batch = BATCH, timeoutTicks = ColossusGameTest.ARENA_WAIT_TICKS + 2 * RUIN_LINE_BACKSTOP_TICKS)
    public static void nearestRuinLineComes(GameTestHelper helper)
    {
        if (ColossusGameTest.waitForArena(helper, TraderQuestGameTest::nearestRuinLineComes))
        {
            return;
        }
        Cast cast = new Cast();
        TestPlayer player;
        int from;
        try
        {
            player = cast.player(helper, "trader_ruin_seeker");
            DrossTrader trader = cast.trader(helper);
            acceptOffer(trader, player);
            from = player.chat.size();
            List<String> lines = click(trader, player);
            expect(lines.isEmpty() || lines.equals(List.of("dross.trader.accepted.2")) || lines.equals(List.of("dross.trader.accepted.2.no_ruin")),
                    "the click after your answer should bring his line with the nearest ruin (at once or a moment later), not " + lines);
        }
        catch (RuntimeException e)
        {
            cast.leave();
            throw e;
        }
        long deadline = Util.getMillis() + RUIN_LINE_MILLIS;
        String[] failure = {null};
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (failure[0] != null || ruinLineSince(player, from) != null)
                    {
                        return;
                    }
                    if (Util.getMillis() > deadline)
                    {
                        failure[0] = "no line naming the nearest ruin came within " + RUIN_LINE_MILLIS / 1000 + " s (told: "
                                + linesSince(player, from) + ")";
                        return;
                    }
                    throw new GameTestAssertException("waiting for the nearest ruin's line");
                })
                .thenExecute(() -> {
                    try
                    {
                        expect(failure[0] == null, failure[0] == null ? "" : failure[0]);
                        TranslatableContents line = ruinLineSince(player, from);
                        if ("dross.trader.accepted.2".equals(line.getKey()))
                        {
                            Object[] args = line.getArgs();
                            expect(args.length == 2 && (int) args[0] % TraderProgress.RUIN_ROUNDING == 0
                                    && (int) args[1] % TraderProgress.RUIN_ROUNDING == 0, "the ruin's spot isn't rounded to 10: "
                                    + java.util.Arrays.toString(args));
                            LOGGER.info("[trader quest test] The nearest ruin he named: near X {}, Z {}", args[0], args[1]);
                        }
                        else
                        {
                            LOGGER.info("[trader quest test] He could feel no ruin near the test (\"{}\"): every one within {} blocks "
                                    + "cleared, or none in this world", line.getKey(), TraderRuins.SEARCH_RADII[TraderRuins.SEARCH_RADII.length - 1]);
                        }
                    }
                    finally
                    {
                        cast.leave();
                    }
                })
                .thenSucceed();
    }

    /** His line naming the nearest ruin (or saying he can't feel one) said since {@code from}, or null. */
    private static TranslatableContents ruinLineSince(TestPlayer player, int from)
    {
        for (int i = from; i < player.chat.size(); i++)
        {
            TranslatableContents line = traderLine(player.chat.get(i));
            if (line != null && (line.getKey().equals("dross.trader.accepted.2") || line.getKey().equals("dross.trader.accepted.2.no_ruin")))
            {
                return line;
            }
        }
        return null;
    }
}
