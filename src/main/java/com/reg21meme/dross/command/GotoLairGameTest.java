package com.reg21meme.dross.command;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.lair.ColossusLair;
import com.reg21meme.dross.boss.ratking.lair.RatKingLair;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.dimension.sewer.RatCaves;
import com.reg21meme.dross.dimension.sewer.Sewers;
import com.reg21meme.dross.dimension.sites.Site;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.world.PortalSiteGameTest;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
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
 * Headless checks (GameTests) of {@code /dross goto lair colossus} and {@code goto lair ratking} ({@link GotoLair}), of
 * {@code /dross goto sewer} ({@link GotoSewer}), and of {@code /dross goto biome} keeping clear of the lairs. Run them with
 * {@code .\gradlew.bat runGameTestServer --console=plain}; each has its own batch. They wait in real time (the lair may
 * have to be built first); the tick limit is only a backstop.
 * <ol>
 *   <li>{@link #gotoLair}: from the Overworld, the stand-in asks for the nearest lair (to the hub): the command says it's
 *       looking, then (if that lair isn't built yet) that it's building it, then lands him exactly on the entrance of the
 *       lair at the planner's nearest site, on solid ground with two free blocks and no fluid, facing into the mouth, with
 *       the note on its Colossus. Asked again from there, the nearest lair is the same one, at once. The notes are checked
 *       by logic (ready, awake, beaten, Peaceful). A Cinder Wastes biome landing aimed right at the lair's middle lands
 *       outside its reach.</li>
 *   <li>{@link #gotoRatKingLair}: the rat cave nearest the hub, onto its arrival spot facing the nest; again from inside.</li>
 *   <li>{@link #gotoSewer}: the regular sewer nearest the hub, at the foot of its first pipe's steps, on safe ground, facing
 *       the pipe; again from there (the same sewer).</li>
 * </ol>
 * The stand-ins are logged out at the end, also when a check fails.
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class GotoLairGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The nearest lair may have to be built first: wait at most this long for the landing (real time). */
    private static final long PLACEMENT_TIMEOUT_MILLIS = 10 * 60 * 1000L;
    /** The tick limit is only a backstop behind the real-time limit (the test server can run hundreds of ticks a second). */
    private static final int BACKSTOP_TICKS = 2_000_000;
    /** The landing check starts its biome sampling at this height (any height in the world does; the biomes don't change with it). */
    private static final int SAMPLE_Y = 72;

    private GotoLairGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_goto_lair", timeoutTicks = BACKSTOP_TICKS)
    public static void gotoLair(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel().getServer());
        helper.startSequence()
                .thenExecute(() -> run.guard(run::notesAndAsk))
                .thenWaitUntil(() -> run.guard(run::waitForLanding))
                .thenExecute(() -> run.guard(run::checkLanding))
                .thenExecute(() -> run.guard(run::askAgain))
                .thenWaitUntil(() -> run.guard(run::waitForLanding))
                .thenExecute(() -> run.guard(run::checkLanding))
                .thenIdle(2)
                .thenExecute(() -> run.guard(run::biomeTeleportKeepsClear))
                .thenExecute(run::cleanUp)
                .thenSucceed();
    }

    // ---------------------------------------------------------------------------------------------
    // goto lair ratking
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code /dross goto lair ratking} ({@link GotoLair#ratKing}): from the Overworld the stand-in lands on the arrival
     * spot of the rat cave nearest the hub, safe, facing the nest ({@code arrivalYaw}), with the chat line and its note;
     * run again from inside the Dross, it uses the rat cave nearest to him (the one he's in) and lands on its arrival
     * spot again. The notes are checked by logic. The stand-in is logged out at the end and the cave's king (who appears
     * as the stand-in arrives) removed, also when a check fails.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_goto_lair_ratking", timeoutTicks = BACKSTOP_TICKS)
    public static void gotoRatKingLair(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        ServerPlayer[] player = new ServerPlayer[1];
        RatCaves.RatCave[] cave = new RatCaves.RatCave[1];
        helper.startSequence()
                .thenExecute(() -> {
                    try
                    {
                        expectNote(GotoLair.ratKingNote(dross, true, false), GotoLair.RAT_KING_BEATEN, null);
                        expectNote(GotoLair.ratKingNote(dross, true, true), GotoLair.RAT_KING_BEATEN, null);
                        if (dross.getDifficulty() != Difficulty.PEACEFUL)
                        {
                            expectNote(GotoLair.ratKingNote(dross, false, false), GotoLair.RAT_KING_READY, null);
                            expectNote(GotoLair.ratKingNote(dross, false, true), GotoLair.RAT_KING_AWAKE, null);
                        }
                        else
                        {
                            expectNote(GotoLair.ratKingNote(dross, false, false), GotoLair.RAT_KING_PEACEFUL, null);
                        }
                        long started = Util.getMillis();
                        cave[0] = RatCaves.findNearest(dross, DrossHub.CENTER)
                                .orElseThrow(() -> new GameTestAssertException("no rat cave in this world (searched from the hub)"));
                        LOGGER.info("[goto lair test] The rat cave nearest the hub is at {} (found in {} ms).", cave[0].centre().toShortString(),
                                Util.getMillis() - started);
                        player[0] = GotoBiomeGameTest.joinPlayer(server.overworld(), "goto-ratking-test", GotoBiomeGameTest.overworldSpawn(server));
                        toRatCave(server, dross, player[0], cave[0], "from the Overworld");

                        // Again from inside the Dross: the nearest rat cave to him is the one he's in.
                        RatCaves.RatCave here = RatCaves.findNearest(dross, player[0].blockPosition())
                                .orElseThrow(() -> new GameTestAssertException("no rat cave found from inside one"));
                        if (here.id() != cave[0].id())
                        {
                            throw new GameTestAssertException("from inside the rat cave at " + cave[0].centre().toShortString()
                                    + ", the nearest rat cave is the one at " + here.centre().toShortString());
                        }
                        toRatCave(server, dross, player[0], here, "from inside the Dross");
                    }
                    finally
                    {
                        GotoBiomeGameTest.logOut(player[0]);
                        if (cave[0] != null)
                        {
                            RatKingLair.clearForTests(dross, cave[0]);
                        }
                    }
                })
                .thenSucceed();
    }

    /** Runs {@code goto lair ratking} for the stand-in and checks he's on the cave's arrival spot, safe, facing the nest. */
    private static void toRatCave(MinecraftServer server, ServerLevel dross, ServerPlayer player, RatCaves.RatCave cave, String where)
    {
        GotoBiomeGameTest.Chat chat = new GotoBiomeGameTest.Chat();
        long started = Util.getMillis();
        int result = GotoBiomeGameTest.run(chat, player, "dross goto lair ratking");
        long took = Util.getMillis() - started;
        String what = where + ", goto lair ratking";
        if (result != 1 || player.level() != dross)
        {
            throw new GameTestAssertException(what + ": the command gave " + result + " and the player is in "
                    + player.level().dimension().location() + " (" + chat + ")");
        }
        chat.expectLast(GotoLair.RAT_KING_SUCCESS, what);
        Object[] args = chat.lastArgs();
        Component expectedNote = GotoLair.ratKingNote(dross, RatKingLair.isBeaten(server, cave), RatKingLair.isKingAwake(server, cave));
        if (args.length != 4 || !(args[3] instanceof Component note) || !note.getString().equals(expectedNote.getString()))
        {
            throw new GameTestAssertException(what + ": the chat line doesn't end with the note \"" + expectedNote.getString() + "\" (" + chat + ")");
        }
        BlockPos arrival = cave.arrival();
        Vec3 spot = new Vec3(arrival.getX() + 0.5D, arrival.getY(), arrival.getZ() + 0.5D);
        Vec3 at = player.position();
        if (at.distanceToSqr(spot) > 1.0E-4D)
        {
            throw new GameTestAssertException(what + ": the player is at " + at + ", not on the cave's arrival spot " + spot);
        }
        GotoBiomeGameTest.checkSafeGround(dross, player, at, what);
        if (Math.abs(Mth.wrapDegrees(player.getYRot() - cave.arrivalYaw())) > 0.01F)
        {
            throw new GameTestAssertException(what + ": the player faces yaw " + player.getYRot() + ", not the nest (" + cave.arrivalYaw() + ")");
        }
        if (!RatCaves.isInRatCave(dross, player.blockPosition()))
        {
            throw new GameTestAssertException(what + ": the player at " + player.blockPosition().toShortString() + " isn't in the rat cave");
        }
        LOGGER.info("[goto lair test] {}: {} ms, on the arrival spot {} facing the nest {}; \"{}\"", what, took,
                player.blockPosition().toShortString(), cave.nest().toShortString(), chat.lines.get(chat.lines.size() - 1).getString());
    }

    // ---------------------------------------------------------------------------------------------
    // goto sewer
    // ---------------------------------------------------------------------------------------------

    /**
     * {@code /dross goto sewer} ({@link GotoSewer}): from the Overworld the stand-in lands at the foot of the first pipe of
     * the regular sewer nearest the hub (within its fallback radius, at the height of the ground the pipe stands on), on
     * safe ground, facing the pipe ({@code approachYaw}), with the chat line; run again from there, it's the same sewer.
     */
    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_goto_sewer", timeoutTicks = BACKSTOP_TICKS)
    public static void gotoSewer(GameTestHelper helper)
    {
        MinecraftServer server = helper.getLevel().getServer();
        ServerLevel dross = server.getLevel(ModDimensions.DROSS_LEVEL);
        if (dross == null)
        {
            throw new GameTestAssertException("there's no Dross level");
        }
        ServerPlayer[] player = new ServerPlayer[1];
        helper.startSequence()
                .thenExecute(() -> {
                    try
                    {
                        long started = Util.getMillis();
                        Sewers.Network network = Sewers.findNearest(dross, DrossHub.CENTER)
                                .orElseThrow(() -> new GameTestAssertException("no regular sewer in this world (searched from the hub)"));
                        LOGGER.info("[goto sewer test] The regular sewer nearest the hub starts in {} ({} ways in; found in {} ms).",
                                network.startChunk(), network.entrances().size(), Util.getMillis() - started);
                        player[0] = GotoBiomeGameTest.joinPlayer(server.overworld(), "goto-sewer-test", GotoBiomeGameTest.overworldSpawn(server));
                        toSewer(dross, player[0], network, "from the Overworld");
                        Sewers.Network again = Sewers.findNearest(dross, player[0].blockPosition())
                                .orElseThrow(() -> new GameTestAssertException("no sewer found from the foot of one of its pipes"));
                        if (again.id() != network.id())
                        {
                            throw new GameTestAssertException("from the foot of the sewer pipe at " + network.entrances().get(0).opening().toShortString()
                                    + ", the nearest sewer is another one (" + again.startChunk() + ")");
                        }
                        toSewer(dross, player[0], again, "from inside the Dross");
                    }
                    finally
                    {
                        GotoBiomeGameTest.logOut(player[0]);
                    }
                })
                .thenSucceed();
    }

    /** Runs {@code goto sewer} for the stand-in and checks he's at the foot of the network's first pipe, safe, facing it. */
    private static void toSewer(ServerLevel dross, ServerPlayer player, Sewers.Network network, String where)
    {
        GotoBiomeGameTest.Chat chat = new GotoBiomeGameTest.Chat();
        int result = GotoBiomeGameTest.run(chat, player, "dross goto sewer");
        String what = where + ", goto sewer";
        if (result != 1 || player.level() != dross)
        {
            throw new GameTestAssertException(what + ": the command gave " + result + " and the player is in " + player.level().dimension().location()
                    + " (" + chat + ")");
        }
        chat.expectLast(GotoSewer.SUCCESS, what);
        Sewers.Entrance entrance = network.entrances().get(0);
        Vec3 at = player.position();
        BlockPos approach = entrance.approach();
        double sideways = Math.max(Math.abs(at.x - (approach.getX() + 0.5D)), Math.abs(at.z - (approach.getZ() + 0.5D)));
        if (sideways > GotoSewer.APPROACH_FALLBACK_RADIUS + 1.0E-6D || Math.abs(at.y - (entrance.groundY() + 1)) > GotoSewer.APPROACH_MAX_HEIGHT_OFF)
        {
            throw new GameTestAssertException(what + ": the player is at " + at + ", not at the foot of the pipe's steps " + approach.toShortString()
                    + " (ground y " + entrance.groundY() + ")");
        }
        GotoBiomeGameTest.checkSafeGround(dross, player, at, what);
        if (Math.abs(Mth.wrapDegrees(player.getYRot() - entrance.approachYaw())) > 0.01F)
        {
            throw new GameTestAssertException(what + ": the player faces yaw " + player.getYRot() + ", not the pipe (" + entrance.approachYaw() + ")");
        }
        LOGGER.info("[goto sewer test] {}: at {} by the pipe at {}, facing it; \"{}\"", what, player.blockPosition().toShortString(),
                entrance.opening().toShortString(), chat.lines.get(chat.lines.size() - 1).getString());
    }

    /** A note's lang key (and its number of minutes, if any). */
    private static void expectNote(Component note, String key, @Nullable Long minutes)
    {
        if (!(note.getContents() instanceof TranslatableContents translatable) || !translatable.getKey().equals(key))
        {
            throw new GameTestAssertException("the note should be " + key + ", but it's \"" + note.getString() + "\"");
        }
        if (minutes != null && (translatable.getArgs().length != 1 || !minutes.equals(translatable.getArgs()[0])))
        {
            throw new GameTestAssertException("the note " + key + " should say " + minutes + " minutes, but it's \"" + note.getString() + "\"");
        }
    }

    private static final class Run
    {
        final MinecraftServer server;
        ServerLevel dross;
        @Nullable
        ServerPlayer player;
        GotoBiomeGameTest.Chat chat;
        /** The planner's lair site nearest where the command searched from (worked out on its own). */
        @Nullable
        CompletableFuture<Optional<Site>> expected;
        /** The lairs built when the command was run (a lair not among them must have been built for it, with a "building" line). */
        java.util.Set<Long> builtAtAsk = java.util.Set.of();
        long deadline;
        long askedAt;
        String where = "";
        @Nullable
        ColossusLair.Lair landed;
        /** True once a step has failed. */
        boolean failed;

        Run(MinecraftServer server)
        {
            this.server = server;
        }

        /**
         * Runs a step; on a failure, logs the stand-in out first. Anything thrown becomes a test failure. Once a step has
         * failed, the later ones are skipped.
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
            catch (Waiting e)
            {
                throw e;
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

        /** The notes by logic, then the real command from the Overworld (searching from the hub). */
        void notesAndAsk()
        {
            dross = server.getLevel(ModDimensions.DROSS_LEVEL);
            if (dross == null)
            {
                throw new GameTestAssertException("there's no Dross level");
            }
            expectNote(GotoLair.riseNote(dross, ColossusLair.State.BEATEN), GotoLair.BEATEN, null);
            if (dross.getDifficulty() != Difficulty.PEACEFUL)
            {
                expectNote(GotoLair.riseNote(dross, ColossusLair.State.READY), GotoLair.READY, null);
                expectNote(GotoLair.riseNote(dross, ColossusLair.State.AWAKE), GotoLair.AWAKE, null);
            }
            else
            {
                expectNote(GotoLair.riseNote(dross, ColossusLair.State.READY), GotoLair.PEACEFUL, null);
            }
            player = GotoBiomeGameTest.joinPlayer(server.overworld(), "goto-lair-test", GotoBiomeGameTest.overworldSpawn(server));
            ask(DrossHub.CENTER, "from the Overworld");
        }

        /** Runs the command and works out on the side which site it should land at. */
        void ask(BlockPos from, String where)
        {
            this.where = where + ", goto lair colossus";
            expected = ColossusLair.findNearestSite(dross, from);
            builtAtAsk = new java.util.HashSet<>(ColossusLair.all(server).stream().map(ColossusLair.Lair::id).toList());
            chat = new GotoBiomeGameTest.Chat();
            askedAt = Util.getMillis();
            deadline = askedAt + PLACEMENT_TIMEOUT_MILLIS;
            landed = null;
            int result = GotoBiomeGameTest.run(chat, requirePlayer(), "dross goto lair colossus");
            if (result != 1)
            {
                throw new GameTestAssertException(this.where + ": the command gave " + result + " (" + chat + ")");
            }
            // (its first line: if the nearest site is known at once, the rest can follow in the same tick)
            if (chat.lines.isEmpty() || !(chat.lines.get(0).getContents() instanceof TranslatableContents first)
                    || !first.getKey().equals(GotoLair.SEARCHING))
            {
                throw new GameTestAssertException(this.where + ": the command didn't say it's looking (" + chat + ")");
            }
        }

        /** Throws (keeps waiting) until the command's last line is the landing's (or a failure), or the deadline passes. */
        void waitForLanding()
        {
            String key = chat.lastKey();
            boolean answered = GotoLair.SUCCESS.equals(key) || GotoLair.NOT_FOUND.equals(key) || GotoLair.TIMEOUT.equals(key)
                    || GotoLair.STOPPED.equals(key);
            if (!answered && Util.getMillis() < deadline)
            {
                throw new Waiting("waiting for the lair (" + chat.lines.size() + " line(s) so far)");
            }
        }

        /** On the entrance of the expected lair, safe, facing into the mouth, with the right note (and "building" first if it was). */
        void checkLanding()
        {
            ServerPlayer player = requirePlayer();
            chat.expectLast(GotoLair.SUCCESS, where);
            Site site = expected.join().orElseThrow(() -> new GameTestAssertException(where + ": the planner has no lair site"));
            ColossusLair.Lair lair = ColossusLair.get(server, site).orElseThrow(() -> new GameTestAssertException(
                    where + ": landed, but the lair at the nearest site " + site.pos().getX() + " " + site.pos().getZ() + " isn't built"));
            boolean saidBuilding = chat.lines.stream().anyMatch(line -> line.getContents() instanceof TranslatableContents t
                    && t.getKey().equals(GotoLair.BUILDING));
            if (!builtAtAsk.contains(lair.id()) && !saidBuilding)
            {
                throw new GameTestAssertException(where + ": the lair wasn't built yet, but the command never said it was building it (" + chat + ")");
            }
            Object[] args = chat.lastArgs();
            Component expectedNote = GotoLair.riseNote(dross, ColossusLair.state(server, lair));
            if (args.length != 4 || !(args[3] instanceof Component note) || !note.getString().equals(expectedNote.getString()))
            {
                throw new GameTestAssertException(where + ": the chat line doesn't end with the rise note \"" + expectedNote.getString() + "\" (" + chat + ")");
            }
            if (player.level() != dross)
            {
                throw new GameTestAssertException(where + ": the player is in " + player.level().dimension().location());
            }
            Vec3 at = player.position();
            if (at.distanceToSqr(lair.entrance()) > 1.0E-4D)
            {
                throw new GameTestAssertException(where + ": the player is at " + at + ", not on the entrance " + lair.entrance()
                        + " of the nearest lair at " + lair.center().toShortString());
            }
            GotoBiomeGameTest.checkSafeGround(dross, player, at, where);
            Direction facing = Direction.fromYRot(player.getYRot());
            if (Math.abs(Mth.wrapDegrees(player.getYRot() - lair.entranceYaw())) > 0.01F || facing != lair.tunnelDirection().getOpposite())
            {
                throw new GameTestAssertException(where + ": the player faces " + facing + " (yaw " + player.getYRot() + "), not into the mouth ("
                        + lair.tunnelDirection().getOpposite() + ", yaw " + lair.entranceYaw() + ")");
            }
            landed = lair;
            LOGGER.info("[goto lair test] {}: {} s, {}on the entrance {} of the lair of group {} at {} facing {}; \"{}\"", where,
                    String.format("%.1f", (Util.getMillis() - askedAt) / 1000.0D), saidBuilding ? "built it first, then " : "",
                    player.blockPosition().toShortString(), lair.group() + 1, lair.center().toShortString(), facing.getName(),
                    chat.lines.get(chat.lines.size() - 1).getString());
        }

        /** Asked again from the entrance: the nearest lair is the same one, built, so it lands at once. */
        void askAgain()
        {
            ColossusLair.Lair first = landed;
            ask(requirePlayer().blockPosition(), "from the lair's entrance");
            Site site = expected.join().orElseThrow();
            if (first == null || site.patch().id() != first.id())
            {
                throw new GameTestAssertException(where + ": from the entrance of the lair at " + (first == null ? "?" : first.center().toShortString())
                        + ", the nearest lair site is another one (" + site.pos().getX() + " " + site.pos().getZ() + ")");
            }
        }

        /** A biome landing aimed right at the lair's middle keeps clear of it. */
        void biomeTeleportKeepsClear()
        {
            ColossusLair.Lair lair = landed;
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
                    Math.round(lair.distanceTo(at.x, at.z)), took, landing.describe());
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

    /** "Still waiting" in a waiting step: never a failure by itself. */
    private static final class Waiting extends GameTestAssertException
    {
        Waiting(String message)
        {
            super(message);
        }
    }
}
