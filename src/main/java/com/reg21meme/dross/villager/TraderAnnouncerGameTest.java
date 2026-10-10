package com.reg21meme.dross.villager;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.registry.ModEntities;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
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
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.gametest.ForgeGameTestHooks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless test (GameTest) for the trader's chat message ({@link TraderAnnouncer}). Run it with
 * {@code .\gradlew.bat runGameTestServer --console=plain}, or in a dev world with {@code /test runall}. It only exists
 * in the development environment: in a normal game nothing here runs.
 *
 * <p>It works in both kinds of world (the GameTest server uses the world named in {@code run/server.properties}):
 * <ul>
 *   <li><b>He has already spawned:</b> a player who joins is told "Dross trader's hut is at X, Y, Z" once, with his
 *       saved spot in the hut.</li>
 *   <li><b>He hasn't spawned yet</b> (his village search is still running in the background): nothing is said on
 *       joining; once he spawns (usually under a minute; the test waits at most {@link #SPAWN_TIMEOUT_TICKS}) that
 *       player has been told exactly once, and a second player who joins then is told once too.</li>
 * </ul>
 * In both cases a spawn-egg trader and a showcase trader are then spawned, and neither sends the message.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class TraderAnnouncerGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The test needs no blocks: a 1x1x1 air template, made in memory when the server starts (no file needed). */
    private static final String EMPTY = "trader_announcer_test_empty";
    private static final ResourceLocation EMPTY_ID = new ResourceLocation(Dross.MODID, EMPTY);
    /** In a new world he spawns in the background once a village is ready (usually under a minute): wait at most 10 minutes. */
    private static final int SPAWN_TIMEOUT_TICKS = 10 * 60 * 20;
    /** How long the spawn-egg and showcase traders are watched for a message (ticks). */
    private static final int OTHER_TRADERS_WATCH_TICKS = 40;

    @GameTest(template = EMPTY, batch = "dross_trader_announcer", timeoutTicks = SPAWN_TIMEOUT_TICKS)
    public static void hutMessage(GameTestHelper helper)
    {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean spawnedAtStart = TraderSpawnData.get(overworld).hasSpawned();
        TestPlayer first = TestPlayer.join(helper.getLevel(), "trader-test-player");
        List<TestPlayer> players = new ArrayList<>(List.of(first));
        // One sequence for the whole test (a sequence must not be started from inside another one).
        GameTestSequence sequence = helper.startSequence();

        if (spawnedAtStart)
        {
            LOGGER.info("[trader test] The trader has already spawned: checking the join message.");
            checked(players, () -> expectHutMessages(first, 1, overworld, "on joining a world where he has spawned"));
        }
        else
        {
            LOGGER.info("[trader test] The trader hasn't spawned yet: checking that joining says nothing, then waiting for him.");
            checked(players, () -> expectHutMessages(first, 0, overworld, "on joining before he has spawned"));
            sequence.thenWaitUntil(() -> {
                        if (!TraderSpawnData.get(overworld).hasSpawned())
                        {
                            throw new GameTestAssertException("The trader never spawned (waited " + SPAWN_TIMEOUT_TICKS / 20 + " s)");
                        }
                    })
                    .thenExecute(() -> checked(players, () -> {
                        expectHutMessages(first, 1, overworld, "after he spawned (the broadcast)");
                        TestPlayer second = TestPlayer.join(helper.getLevel(), "trader-test-player-2");
                        players.add(second);
                        expectHutMessages(second, 1, overworld, "on joining after he spawned");
                    }));
        }

        // Then a spawn-egg trader and a showcase trader appear next to the test: nobody is told anything new.
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

    /** A spawn-egg trader and a showcase trader, and how many hut messages each player had before they appeared. */
    private static final class OtherTraders
    {
        private int[] before = new int[0];
        @Nullable
        private DrossTrader egg;
        @Nullable
        private DrossTrader showcase;

        void spawn(GameTestHelper helper, List<TestPlayer> players)
        {
            before = players.stream().mapToInt(TraderAnnouncerGameTest::countHutMessages).toArray();
            BlockPos at = helper.absolutePos(new BlockPos(0, 1, 0));
            egg = ModEntities.TRADER.get().spawn(helper.getLevel(), at, MobSpawnType.SPAWN_EGG);
            showcase = DrossTrader.spawnShowcase(helper.getLevel(), Vec3.atBottomCenterOf(at.above(2)), 0.0F,
                    TraderSkins.ALL.get(0));
            if (egg == null || showcase == null)
            {
                remove();
                throw new GameTestAssertException("Couldn't spawn the spawn-egg trader or the showcase trader");
            }
        }

        void remove()
        {
            if (egg != null)
            {
                egg.discard();
            }
            if (showcase != null)
            {
                showcase.discard();
            }
        }

        void expectNothingSaid(List<TestPlayer> players)
        {
            for (int i = 0; i < players.size(); i++)
            {
                if (countHutMessages(players.get(i)) != before[i])
                {
                    throw new GameTestAssertException("A spawn-egg or showcase trader made the hut message appear");
                }
            }
            LOGGER.info("[trader test] A spawn-egg trader and a showcase trader sent no hut message, as they should.");
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

    /** The player got exactly {@code count} hut messages, each with his saved spot in the hut as X, Y, Z. */
    private static void expectHutMessages(TestPlayer player, int count, ServerLevel overworld, String when)
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
        BlockPos hut = TraderSpawnData.get(overworld).getPos();
        Object[] expected = {hut.getX(), hut.getY(), hut.getZ()};
        for (TranslatableContents translatable : found)
        {
            if (!Arrays.equals(translatable.getArgs(), expected))
            {
                throw new GameTestAssertException(when + ": the hut message says " + Arrays.toString(translatable.getArgs())
                        + " but his saved spot is " + Arrays.toString(expected));
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
        StructureTemplate template = event.getServer().getStructureManager().getOrCreate(EMPTY_ID);
        template.load(BuiltInRegistries.BLOCK.asLookup(), emptyTag());
    }

    private static CompoundTag emptyTag()
    {
        CompoundTag tag = new CompoundTag();
        tag.put("size", intList(1, 1, 1));
        ListTag palette = new ListTag();
        palette.add(NbtUtils.writeBlockState(Blocks.AIR.defaultBlockState()));
        tag.put("palette", palette);
        ListTag blocks = new ListTag();
        CompoundTag block = new CompoundTag();
        block.put("pos", intList(0, 0, 0));
        block.putInt("state", 0);
        blocks.add(block);
        tag.put("blocks", blocks);
        tag.put("entities", new ListTag());
        return tag;
    }

    private static ListTag intList(int... values)
    {
        ListTag list = new ListTag();
        for (int value : values)
        {
            list.add(IntTag.valueOf(value));
        }
        return list;
    }

    private TraderAnnouncerGameTest() {}
}
