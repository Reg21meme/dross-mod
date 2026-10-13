package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.lair.ColossusLair;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.Optional;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.Difficulty;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless check (GameTest) of {@code /dross goto lair colossus} ({@link GotoLair}), and of {@code /dross goto biome}
 * keeping clear of the lair. Run it with {@code .\gradlew.bat runGameTestServer --console=plain}; it has its own batch.
 * It waits in real time (at most {@value #PLACEMENT_TIMEOUT_MILLIS} ms) for the lair, which is built in the background a
 * little while after the world starts; the tick limit is only a backstop.
 * <ol>
 *   <li><b>Before the lair is built</b> the command builds nothing and fails with "...hasn't been placed in this world
 *       yet.": checked with the real command if the test happens to start before the lair is built (a fresh world, when
 *       this batch runs early), and always by logic (the command's work given no lair).</li>
 *   <li><b>Once it's built:</b> from the Overworld, the stand-in lands exactly on the lair's entrance in the Dross, on
 *       solid ground with two free blocks and no fluid, facing into the tunnel (the entrance's yaw, the opposite of the way
 *       the tunnel runs out of the cavern), and the chat line says whether the Colossus can rise. The "N minutes" wording
 *       is checked by logic too.</li>
 *   <li><b>{@code goto biome cinder_wastes} keeps clear of it:</b> a landing search aimed right at the lair's middle
 *       ({@link GotoBiome.Landing}, as the command does it) lands in the Cinder Wastes, on safe ground, outside the lair's
 *       reach (further from its middle than its entrance is).</li>
 * </ol>
 * The stand-in is logged out at the end, also when a check fails, and nobody is logged in while it waits for the lair
 * (a new stand-in joins once it's built), so giving up the wait leaves nobody behind.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class GotoLairGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The lair is built in the background a little after the world starts: wait at most this long (real time). */
    private static final long PLACEMENT_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    /** The tick limit is only a backstop behind the real-time limit (the test server can run hundreds of ticks a second). */
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** The landing check starts its biome sampling at this height (any height in the world does; the biomes don't change with it). */
    private static final int SAMPLE_Y = 72;
    /** Ticks in a minute. */
    private static final long MINUTE = 1200L;

    private GotoLairGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_goto_lair", timeoutTicks = BACKSTOP_TICKS)
    public static void gotoLair(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel().getServer());
        long deadline = Util.getMillis() + PLACEMENT_TIMEOUT_MILLIS;
        helper.startSequence()
                .thenExecute(() -> run.guard(run::beforePlacement))
                .thenWaitUntil(() -> {
                    if (!ColossusLair.isPlaced(run.server) && Util.getMillis() < deadline)
                    {
                        throw new GameTestAssertException("waiting for the Colossus lair to be built");
                    }
                })
                .thenExecute(() -> run.guard(run::toTheEntrance))
                .thenIdle(2)
                .thenExecute(() -> run.guard(run::biomeTeleportKeepsClear))
                .thenExecute(run::cleanUp)
                .thenSucceed();
    }

    private static final class Run
    {
        final MinecraftServer server;
        ServerLevel dross;
        @Nullable
        ServerPlayer player;
        final long startMillis = Util.getMillis();
        /** True once a step has failed. */
        boolean failed;

        Run(MinecraftServer server)
        {
            this.server = server;
        }

        /**
         * Runs a step; on a failure, logs the stand-in out first. Anything thrown becomes a test failure. Once a step has
         * failed, the later ones are skipped (they'd run in the same tick and their own failure would hide the first one).
         */
        void guard(Runnable step)
        {
            if (failed)
            {
                return;
            }
            try
            {
                step.run();
            }
            catch (GameTestAssertException e)
            {
                failed = true;
                LOGGER.error("[goto lair test] {}", e.getMessage());
                cleanUp();
                throw e;
            }
            catch (RuntimeException e)
            {
                failed = true;
                LOGGER.error("[goto lair test] the test threw", e);
                cleanUp();
                GameTestAssertException failure = new GameTestAssertException("the test threw " + e);
                failure.initCause(e);
                throw failure;
            }
        }

        /** The "not built yet" answer: with the real command if the lair isn't built yet, and always by logic. */
        void beforePlacement()
        {
            dross = server.getLevel(ModDimensions.DROSS_LEVEL);
            if (dross == null)
            {
                throw new GameTestAssertException("there's no Dross level");
            }
            player = GotoBiomeGameTest.joinPlayer(server.overworld(), "goto-lair-test", GotoBiomeGameTest.overworldSpawn(server));
            Vec3 before = player.position();

            if (!ColossusLair.isPlaced(server))
            {
                GotoBiomeGameTest.Chat chat = new GotoBiomeGameTest.Chat();
                int result = GotoBiomeGameTest.run(chat, player, "dross goto lair colossus");
                if (!ColossusLair.isPlaced(server)) // (it could have been finished in between: then this check doesn't apply)
                {
                    expectNotPlaced(result, chat, before, "the real command, before the lair was built");
                    LOGGER.info("[goto lair test] Before the lair was built, the real command said it hasn't been placed yet and moved nobody.");
                }
            }
            else
            {
                LOGGER.info("[goto lair test] The lair was already built when the test started, so the \"not placed yet\" answer is "
                        + "only checked by logic.");
            }

            GotoBiomeGameTest.Chat chat = new GotoBiomeGameTest.Chat();
            CommandSourceStack source = player.createCommandSourceStack().withPermission(2).withSource(chat);
            int result;
            try
            {
                result = GotoLair.teleport(source, dross, Optional.empty(), 0L);
            }
            catch (CommandSyntaxException e)
            {
                throw new GameTestAssertException("with no lair, the command threw " + e.getMessage());
            }
            expectNotPlaced(result, chat, before, "by logic (no lair)");

            if (dross.getDifficulty() != Difficulty.PEACEFUL)
            {
                expectNote(GotoLair.riseNote(dross, 0L), GotoLair.READY, null);
                expectNote(GotoLair.riseNote(dross, 1L), GotoLair.WAITING_ONE, null);
                expectNote(GotoLair.riseNote(dross, MINUTE), GotoLair.WAITING_ONE, null);
                expectNote(GotoLair.riseNote(dross, MINUTE + 1L), GotoLair.WAITING, 2L);
                expectNote(GotoLair.riseNote(dross, 10L * MINUTE), GotoLair.WAITING, 10L);
            }
            else
            {
                expectNote(GotoLair.riseNote(dross, 0L), GotoLair.PEACEFUL, null);
            }
            // Nobody stays logged in during the wait for the lair, so a test that gives up waiting leaves no stand-in behind.
            cleanUp();
        }

        void expectNotPlaced(int result, GotoBiomeGameTest.Chat chat, Vec3 before, String how)
        {
            ServerPlayer player = requirePlayer();
            if (result != 0 || player.level() != server.overworld() || player.position().distanceToSqr(before) > 1.0E-6D)
            {
                throw new GameTestAssertException(how + ": with no lair the command gave " + result + " and the player is at " + player.position()
                        + " in " + player.level().dimension().location() + " (" + chat + ")");
            }
            chat.expectLast(GotoLair.NOT_PLACED, how);
        }

        void expectNote(Component note, String key, @Nullable Long minutes)
        {
            if (!(note.getContents() instanceof TranslatableContents translatable) || !translatable.getKey().equals(key))
            {
                throw new GameTestAssertException("the Colossus's rise note should be " + key + ", but it's \"" + note.getString() + "\"");
            }
            if (minutes != null && (translatable.getArgs().length != 1 || !minutes.equals(translatable.getArgs()[0])))
            {
                throw new GameTestAssertException("the rise note " + key + " should say " + minutes + " minutes, but it's \"" + note.getString() + "\"");
            }
        }

        /** From the Overworld, {@code goto lair colossus}: on the entrance, safe, facing the tunnel. */
        void toTheEntrance()
        {
            ColossusLair.Lair lair = ColossusLair.get(server).orElseThrow(() -> new GameTestAssertException(
                    "the Colossus lair wasn't built within " + PLACEMENT_TIMEOUT_MILLIS / 1000 + " s"));
            LOGGER.info("[goto lair test] The lair is built (waited {} s): middle {}, entrance {}, tunnel runs {}.",
                    String.format("%.1f", (Util.getMillis() - startMillis) / 1000.0D), lair.center().toShortString(),
                    BlockPos.containing(lair.entrance()).toShortString(), lair.tunnelDirection().getName());
            player = GotoBiomeGameTest.joinPlayer(server.overworld(), "goto-lair-test", GotoBiomeGameTest.overworldSpawn(server));
            ServerPlayer player = requirePlayer();

            GotoBiomeGameTest.Chat chat = new GotoBiomeGameTest.Chat();
            long started = Util.getMillis();
            int result = GotoBiomeGameTest.run(chat, player, "dross goto lair colossus");
            long took = Util.getMillis() - started;
            String where = "from the Overworld, goto lair colossus";
            if (result != 1 || player.level() != dross)
            {
                throw new GameTestAssertException(where + ": the command gave " + result + " and the player is in "
                        + player.level().dimension().location() + " (" + chat + ")");
            }
            chat.expectLast(GotoLair.SUCCESS, where);
            Object[] args = chat.lastArgs();
            Component expectedNote = GotoLair.riseNote(dross, ColossusLair.ticksUntilRespawn(server));
            if (args.length != 4 || !(args[3] instanceof Component note) || !note.getString().equals(expectedNote.getString()))
            {
                throw new GameTestAssertException(where + ": the chat line doesn't end with the rise note \"" + expectedNote.getString() + "\" ("
                        + chat + ")");
            }

            Vec3 at = player.position();
            if (at.distanceToSqr(lair.entrance()) > 1.0E-4D)
            {
                throw new GameTestAssertException(where + ": the player is at " + at + ", not on the entrance " + lair.entrance());
            }
            GotoBiomeGameTest.checkSafeGround(dross, player, at, where);
            Direction facing = Direction.fromYRot(player.getYRot());
            if (Math.abs(Mth.wrapDegrees(player.getYRot() - lair.entranceYaw())) > 0.01F || facing != lair.tunnelDirection().getOpposite())
            {
                throw new GameTestAssertException(where + ": the player faces " + facing + " (yaw " + player.getYRot() + "), not into the tunnel ("
                        + lair.tunnelDirection().getOpposite() + ", yaw " + lair.entranceYaw() + ")");
            }
            LOGGER.info("[goto lair test] {}: {} ms, on the entrance {} facing {} into the tunnel; \"{}\"", where, took,
                    player.blockPosition().toShortString(), facing.getName(), chat.lines.get(chat.lines.size() - 1).getString());
        }

        /** A biome landing aimed right at the lair's middle keeps clear of it. */
        void biomeTeleportKeepsClear()
        {
            ColossusLair.Lair lair = ColossusLair.get(server).orElseThrow();
            BlockPos origin = DrossHub.CENTER.atY(SAMPLE_Y);
            BlockPos aim = lair.center().atY(SAMPLE_Y);
            long started = Util.getMillis();
            GotoBiome.Landing landing = new GotoBiome.Landing(dross, requirePlayer(), DrossBiomes.CINDER_WASTES, SAMPLE_Y);
            Optional<Vec3> spot = landing.find(origin, aim);
            long took = Util.getMillis() - started;
            String where = "a Cinder Wastes landing aimed at the lair's middle";
            if (spot.isEmpty())
            {
                throw new GameTestAssertException(where + ": found no landing spot (" + landing.describe() + ")");
            }
            Vec3 at = spot.get();
            BlockPos feet = BlockPos.containing(at);
            if (!dross.getBiome(feet).is(DrossBiomes.CINDER_WASTES))
            {
                throw new GameTestAssertException(where + ": landed at " + feet.toShortString() + ", not in the Cinder Wastes");
            }
            GotoBiomeGameTest.checkClearOfLair(server, at, where);
            ServerPlayer player = requirePlayer();
            player.teleportTo(dross, at.x, at.y, at.z, 0.0F, 0.0F);
            GotoBiomeGameTest.checkSafeGround(dross, player, player.position(), where);
            LOGGER.info("[goto lair test] {}: landed at {}, {} blocks from its middle ({} ms; {})", where, feet.toShortString(),
                    Math.round(Math.hypot(at.x - (lair.center().getX() + 0.5D), at.z - (lair.center().getZ() + 0.5D))), took, landing.describe());
        }

        ServerPlayer requirePlayer()
        {
            if (player == null)
            {
                throw new GameTestAssertException("the stand-in player is gone");
            }
            return player;
        }

        /** Logs the stand-in out (also after a failure). */
        void cleanUp()
        {
            GotoBiomeGameTest.logOut(player);
            player = null;
        }
    }
}
