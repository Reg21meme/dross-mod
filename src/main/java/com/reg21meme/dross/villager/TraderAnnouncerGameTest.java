package com.reg21meme.dross.villager;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestSequence;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.IntTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless test (GameTest) for the trader's chat message ({@link TraderAnnouncer}). Run it with
 * {@code .\gradlew.bat runGameTestServer --console=plain} (only it and the chapel tests: set
 * {@link RiftChapelGameTest#NAMESPACE} to {@code "dross_chapel"} and add {@code -PgameTestNamespaces=dross_chapel}). It only
 * exists in the development environment: in a normal game nothing here runs.
 * <ul>
 *   <li>A player who joins is told "Dross trader's hut is at X, Y, Z" once, a moment later (the search runs off the server
 *       thread; the test waits for it by the clock), with the trader's spot in the nearest Rift Chapel to him
 *       ({@link RiftChapels#nearest}); a second player who joins is told once too. If there's no chapel within the
 *       search's reach, nobody is told anything.</li>
 *   <li>Then a spawn-egg trader and a {@code /summon}-style trader are spawned, and neither sends the message.</li>
 * </ul>
 * The wait always ends by itself, before the test's tick limit, so the stand-in players always leave.
 */
@GameTestHolder(RiftChapelGameTest.NAMESPACE)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class TraderAnnouncerGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The test needs no blocks: a 1x1x1 air template, made in memory when the server starts (no file needed). */
    private static final String EMPTY = "trader_announcer_test_empty";
    /** Wait at most this long (real time) for a player's message. */
    private static final long MESSAGE_TIMEOUT_MILLIS = 60_000L;
    /** With no chapel near, how long nobody must be told anything (real time). */
    private static final long QUIET_MILLIS = 3_000L;
    /** The ticks the test may spend waiting, a backstop behind the real-time limits (the server runs ticks very fast). */
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** How long the spawn-egg and {@code /summon}-style traders are watched for a message (ticks). */
    private static final int OTHER_TRADERS_WATCH_TICKS = 40;

    @GameTest(template = EMPTY, batch = "dross_trader_announcer", timeoutTicks = 2 * BACKSTOP_TICKS + OTHER_TRADERS_WATCH_TICKS + 100)
    public static void hutMessage(GameTestHelper helper)
    {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        List<TestPlayer> players = new ArrayList<>();
        GameTestSequence sequence = helper.startSequence();
        Joined first = new Joined();
        Joined second = new Joined();
        sequence.thenExecute(() -> first.join(helper, overworld, players, "trader-test-player"))
                .thenWaitUntil(() -> first.waitForMessage(helper))
                .thenExecute(() -> checked(players, () -> first.expectTold("on joining")))
                .thenExecute(() -> second.join(helper, overworld, players, "trader-test-player-2"))
                .thenWaitUntil(() -> second.waitForMessage(helper))
                .thenExecute(() -> checked(players, () -> second.expectTold("on joining second")))
                // If that failed, the test ends here: in the tick a step fails, the framework still runs the next one.
                .thenIdle(1);

        // Then a spawn-egg trader and a /summon-style trader appear next to the test: nobody is told anything new.
        OtherTraders others = new OtherTraders();
        sequence.thenExecute(() -> checked(players, () -> others.spawn(helper, players)))
                .thenIdle(OTHER_TRADERS_WATCH_TICKS)
                .thenExecute(() -> {
                    others.remove();
                    checked(players, () -> others.expectNothingSaid(players));
                    players.forEach(TestPlayer::leave);
                })
                .thenSucceed();
    }

    /** One stand-in player who joined: when, and the nearest chapel to where he came in. */
    private static final class Joined
    {
        @Nullable
        TestPlayer player;
        Optional<RiftChapels.Chapel> nearest = Optional.empty();
        long joinedAt;

        void join(GameTestHelper helper, ServerLevel overworld, List<TestPlayer> players, String name)
        {
            this.player = TestPlayer.join(helper.getLevel(), name);
            players.add(this.player);
            BlockPos from = RiftChapels.searchOrigin(overworld, this.player.level(), this.player.blockPosition());
            this.nearest = RiftChapels.nearest(overworld, from, RiftChapels.SEARCH_RADIUS);
            this.joinedAt = Util.getMillis();
            LOGGER.info("[trader test] {} joined at {}; the nearest chapel to him: {}", name, from.toShortString(),
                    this.nearest.map(RiftChapels.Chapel::describe).orElse("none within " + RiftChapels.SEARCH_RADIUS + " blocks"));
        }

        /** Waits (by the clock) until he's been told, or (no chapel near) a few quiet seconds have passed. */
        void waitForMessage(GameTestHelper helper)
        {
            long waited = Util.getMillis() - this.joinedAt;
            boolean told = this.player != null && countHutMessages(this.player) > 0;
            boolean over = this.nearest.isPresent() ? told || waited > MESSAGE_TIMEOUT_MILLIS : told || waited > QUIET_MILLIS;
            if (!over && helper.getTick() < BACKSTOP_TICKS)
            {
                throw new GameTestAssertException("Waiting for the hut message");
            }
        }

        void expectTold(String when)
        {
            if (this.player == null)
            {
                throw new GameTestAssertException(when + ": nobody joined");
            }
            expectHutMessages(this.player, this.nearest.isPresent() ? 1 : 0, this.nearest.orElse(null), when);
        }
    }

    /** A spawn-egg trader and a /summon-style trader, and how many hut messages each player had before they appeared. */
    private static final class OtherTraders
    {
        private int[] before = new int[0];
        @Nullable
        private DrossTrader egg;
        @Nullable
        private DrossTrader summoned;

        void spawn(GameTestHelper helper, List<TestPlayer> players)
        {
            before = players.stream().mapToInt(TraderAnnouncerGameTest::countHutMessages).toArray();
            BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0));
            egg = ModEntities.TRADER.get().spawn(helper.getLevel(), at, MobSpawnType.SPAWN_EGG);
            summoned = ModEntities.TRADER.get().spawn(helper.getLevel(), at.above(2), MobSpawnType.COMMAND);
            if (egg == null || summoned == null)
            {
                remove();
                throw new GameTestAssertException("Couldn't spawn the spawn-egg trader or the /summon-style trader");
            }
        }

        void remove()
        {
            if (egg != null)
            {
                egg.discard();
            }
            if (summoned != null)
            {
                summoned.discard();
            }
        }

        void expectNothingSaid(List<TestPlayer> players)
        {
            for (int i = 0; i < players.size(); i++)
            {
                if (countHutMessages(players.get(i)) != before[i])
                {
                    throw new GameTestAssertException("A spawn-egg or /summon-style trader made the hut message appear");
                }
            }
            LOGGER.info("[trader test] A spawn-egg trader and a /summon-style trader sent no hut message, as they should.");
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Checks
    // ---------------------------------------------------------------------------------------------

    private static int countHutMessages(TestPlayer player)
    {
        int count = 0;
        for (Component message : player.messages)
        {
            if (message.getContents() instanceof TranslatableContents translatable
                    && TraderAnnouncer.LOCATION_KEY.equals(translatable.getKey()))
            {
                count++;
            }
        }
        return count;
    }

    /** The player got exactly {@code count} hut messages, each with the trader's spot in this chapel as X, Y, Z. */
    private static void expectHutMessages(TestPlayer player, int count, @Nullable RiftChapels.Chapel chapel, String when)
    {
        List<TranslatableContents> found = new ArrayList<>();
        for (Component message : player.messages)
        {
            if (message.getContents() instanceof TranslatableContents translatable
                    && TraderAnnouncer.LOCATION_KEY.equals(translatable.getKey()))
            {
                found.add(translatable);
                LOGGER.info("[trader test] {}: {} was told \"{}\"", when, player.getGameProfile().getName(), message.getString());
            }
        }
        if (found.size() != count)
        {
            throw new GameTestAssertException(when + ": expected " + count + " hut message(s), got " + found.size());
        }
        if (chapel == null)
        {
            return;
        }
        BlockPos hut = chapel.traderSpot();
        Object[] expected = {hut.getX(), hut.getY(), hut.getZ()};
        for (TranslatableContents translatable : found)
        {
            if (!Arrays.equals(translatable.getArgs(), expected))
            {
                throw new GameTestAssertException(when + ": the hut message says " + Arrays.toString(translatable.getArgs())
                        + " but the nearest chapel's spot is " + Arrays.toString(expected));
            }
        }
    }

    /** Runs some checks; if one fails, the stand-in players leave first, then the test fails. */
    private static void checked(List<TestPlayer> players, Runnable checks)
    {
        try
        {
            checks.run();
        }
        catch (RuntimeException e)
        {
            players.forEach(TestPlayer::leave);
            throw e;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The stand-in player
    // ---------------------------------------------------------------------------------------------

    /**
     * A stand-in player on the server's player list (so joining fires the normal login event, and broadcasts reach
     * him) who remembers every chat message sent to him. He gets an in-memory network channel (vanilla's
     * {@code makeMockServerPlayerInLevel} crashes under Forge).
     */
    private static final class TestPlayer extends ServerPlayer
    {
        final List<Component> messages = new ArrayList<>();

        private TestPlayer(ServerLevel level, String name)
        {
            super(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        }

        static TestPlayer join(ServerLevel level, String name)
        {
            TestPlayer player = new TestPlayer(level, name);
            Connection connection = new Connection(PacketFlow.SERVERBOUND);
            new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
            level.getServer().getPlayerList().placeNewPlayer(connection, player);
            return player;
        }

        @Override
        public void sendSystemMessage(Component message, boolean overlay)
        {
            messages.add(message);
            super.sendSystemMessage(message, overlay);
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

    // ---------------------------------------------------------------------------------------------
    // The empty template
    // ---------------------------------------------------------------------------------------------

    /** GameTests are built on a structure template; this one (a single air block) is made in memory. */
    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event)
    {
        if (!ForgeGameTestHooks.isGametestEnabled())
        {
            return;
        }
        RiftChapelGameTest.registerEmpty(event.getServer(), new ResourceLocation(Dross.MODID, EMPTY));
        if (!RiftChapelGameTest.NAMESPACE.equals(Dross.MODID))
        {
            RiftChapelGameTest.registerEmpty(event.getServer(), new ResourceLocation(RiftChapelGameTest.NAMESPACE, EMPTY));
        }
    }

    private TraderAnnouncerGameTest() {}
}
