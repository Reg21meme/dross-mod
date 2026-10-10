package com.reg21meme.dross.world;

import com.mojang.authlib.GameProfile;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.command.DrossCommand;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
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
 * Headless test (GameTest) for the portal site's chat message ({@link SiteAnnouncer}) and {@code /dross site}. Run it
 * with {@code .\gradlew.bat runGameTestServer --console=plain}, or in a dev world with {@code /test runall}. It only
 * exists in the development environment: in a normal game nothing here runs.
 *
 * <p>It works in both kinds of world (the GameTest server uses the world named in {@code run/server.properties}):
 * <ul>
 *   <li><b>The site is already placed</b> (for example {@code run/world}, an old world with the small shrine): a player
 *       who joins is told "Dross portal site is at X, Y, Z" once, with the opening's middle, and {@code /dross site}
 *       takes him in front of the frame.</li>
 *   <li><b>A new world</b> (the site is still being placed in the background): nothing is said on joining,
 *       {@code /dross site} fails with "hasn't been placed ... yet" and builds nothing; then, once the site is placed
 *       (about a minute; the test waits at most {@link #PLACEMENT_TIMEOUT_TICKS}), the player has been told where it
 *       is exactly once, and {@code /dross site} works.</li>
 * </ul>
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
@Mod.EventBusSubscriber(modid = Dross.MODID)
public final class PortalSiteGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The test needs no blocks: a 1x1x1 air template, made in memory when the server starts (no file needed). */
    private static final String EMPTY = "portal_site_test_empty";
    private static final ResourceLocation EMPTY_ID = new ResourceLocation(Dross.MODID, EMPTY);
    /** In a new world the site is built in the background, about a minute after it opens: wait at most 5 minutes. */
    private static final int PLACEMENT_TIMEOUT_TICKS = 5 * 60 * 20;
    /** After {@code /dross site}, the player must be within this many blocks (on each of X, Y and Z) of the opening's middle. */
    private static final int NEAR_FRAME = 8;

    @GameTest(template = EMPTY, batch = "dross_portal_site", timeoutTicks = PLACEMENT_TIMEOUT_TICKS)
    public static void siteMessageAndCommand(GameTestHelper helper)
    {
        ServerLevel overworld = helper.getLevel().getServer().overworld();
        boolean placedAtStart = PortalSite.isPlaced(overworld);
        TestPlayer player = TestPlayer.join(helper.getLevel(), "site-test-player");

        if (placedAtStart)
        {
            LOGGER.info("[site test] The site is already placed: checking the join message and /dross site.");
            checked(player, () -> {
                expectSiteMessages(player, 1, overworld, "on joining a world where the site is placed");
                expectSiteCommandWorks(player, overworld);
            });
            player.leave();
            helper.succeed();
            return;
        }

        LOGGER.info("[site test] The site isn't placed yet: checking that joining says nothing and /dross site fails, "
                + "then waiting for the background placement.");
        checked(player, () -> {
            expectSiteMessages(player, 0, overworld, "on joining before the site is placed");
            expectSiteCommandFails(player, overworld);
        });
        helper.startSequence()
                .thenWaitUntil(() -> {
                    if (!PortalSite.isPlaced(overworld))
                    {
                        throw new GameTestAssertException("The site was never placed (waited " + PLACEMENT_TIMEOUT_TICKS / 20 + " s)");
                    }
                })
                .thenExecute(() -> checked(player, () -> {
                    expectSiteMessages(player, 1, overworld, "after the site was placed");
                    expectSiteCommandWorks(player, overworld);
                }))
                .thenExecute(player::leave)
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // Checks
    // ---------------------------------------------------------------------------------------------

    /** The player got exactly {@code count} site messages, each with the opening's middle as X, Y, Z. */
    private static void expectSiteMessages(TestPlayer player, int count, ServerLevel overworld, String when)
    {
        List<TranslatableContents> found = new ArrayList<>();
        for (Component message : player.messages)
        {
            if (message.getContents() instanceof TranslatableContents translatable
                    && SiteAnnouncer.LOCATION_KEY.equals(translatable.getKey()))
            {
                found.add(translatable);
                LOGGER.info("[site test] {}: the player was told \"{}\"", when, message.getString());
            }
        }
        if (found.size() != count)
        {
            throw new GameTestAssertException(when + ": expected " + count + " site message(s), got " + found.size());
        }
        BlockPos center = PortalSite.getOpeningCenter(overworld);
        Object[] expected = {center.getX(), center.getY(), center.getZ()};
        for (TranslatableContents translatable : found)
        {
            if (!Arrays.equals(translatable.getArgs(), expected))
            {
                throw new GameTestAssertException(when + ": the site message says " + Arrays.toString(translatable.getArgs())
                        + " but the opening's middle is " + Arrays.toString(expected));
            }
        }
    }

    /** {@code /dross site} fails with the "hasn't been placed" message, and doesn't build the site. */
    private static void expectSiteCommandFails(TestPlayer player, ServerLevel overworld)
    {
        int before = player.messages.size();
        int result = runSiteCommand(player);
        if (result != 0)
        {
            throw new GameTestAssertException("/dross site should fail before the site is placed, but returned " + result);
        }
        boolean said = player.messages.subList(before, player.messages.size()).stream()
                .anyMatch(message -> message.getString().equals(DrossCommand.SITE_NOT_PLACED));
        if (!said)
        {
            throw new GameTestAssertException("/dross site didn't say \"" + DrossCommand.SITE_NOT_PLACED + "\"");
        }
        if (PortalSite.isPlaced(overworld))
        {
            throw new GameTestAssertException("/dross site built the site (it must only say it isn't placed yet)");
        }
        LOGGER.info("[site test] /dross site before placement failed as it should: \"{}\"", DrossCommand.SITE_NOT_PLACED);
    }

    /** {@code /dross site} works and puts the player near the frame's opening, in the Overworld. */
    private static void expectSiteCommandWorks(TestPlayer player, ServerLevel overworld)
    {
        int result = runSiteCommand(player);
        if (result != 1)
        {
            throw new GameTestAssertException("/dross site should work once the site is placed, but returned " + result);
        }
        BlockPos center = PortalSite.getOpeningCenter(overworld);
        BlockPos at = player.blockPosition();
        if (player.serverLevel() != overworld || Math.abs(at.getX() - center.getX()) > NEAR_FRAME
                || Math.abs(at.getY() - center.getY()) > NEAR_FRAME || Math.abs(at.getZ() - center.getZ()) > NEAR_FRAME)
        {
            throw new GameTestAssertException("/dross site put the player at " + at.toShortString() + " in "
                    + player.serverLevel().dimension().location() + ", not near the opening at " + center.toShortString());
        }
        LOGGER.info("[site test] /dross site put the player at {}, near the opening at {}", at.toShortString(), center.toShortString());
    }

    /** Runs {@code /dross site} as the player, with cheats (permission level 2). Returns the command's result. */
    private static int runSiteCommand(TestPlayer player)
    {
        return player.server.getCommands().performPrefixedCommand(player.createCommandSourceStack().withPermission(2), "dross site");
    }

    /** Runs some checks; if one fails, the stand-in player leaves first, then the test fails. */
    private static void checked(TestPlayer player, Runnable checks)
    {
        try
        {
            checks.run();
        }
        catch (RuntimeException e)
        {
            player.leave();
            throw e;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // The stand-in player
    // ---------------------------------------------------------------------------------------------

    /**
     * A stand-in player on the server's player list (so joining fires the normal login event, and broadcasts reach
     * him) who remembers every chat message sent to him. Like the enchant area's test owner, he gets an in-memory
     * network channel (vanilla's {@code makeMockServerPlayerInLevel} crashes under Forge).
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

    private PortalSiteGameTest() {}
}
