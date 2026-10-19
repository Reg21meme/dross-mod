package com.reg21meme.dross.command;

import com.mojang.authlib.GameProfile;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import com.reg21meme.dross.Dross;
import com.reg21meme.dross.boss.lair.ColossusLair;
import com.reg21meme.dross.dimension.DrossBiomes;
import com.reg21meme.dross.dimension.ModDimensions;
import com.reg21meme.dross.portal.DrossHub;
import com.reg21meme.dross.world.PortalSiteGameTest;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestAssertException;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.BoundingBox;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.slf4j.Logger;

/**
 * Headless check (GameTest) of {@code /dross goto biome <name>} ({@link GotoBiome}), with a stand-in player who really
 * runs the command. Run it with {@code .\gradlew.bat runGameTestServer --console=plain}; it has its own batch, so no
 * other test's stand-in players are about. It only exists in the development environment.
 * <ol>
 *   <li><b>From the Overworld</b>, for every Dross biome (the Gravecrags first, then the Silverwood Forest, then the
 *       rest): the player ends up in the Dross, standing in that biome (what F3 would show), on safe ground (something
 *       solid under the feet, two blocks of free space with no fluid), outside the hub's footprint and its
 *       {@value GotoBiome#HUB_CLEARANCE}-block margin (for the Silverwood Forest, whose nearest place seen from the
 *       Overworld is the hub itself, that means the forest beside the spire), and outside the Colossus lair (once it's
 *       built; {@link GotoLairGameTest} checks that part directly). The chat line is the "from the hub" one.</li>
 *   <li><b>Already there:</b> standing on the hub's arrival step (in the Silverwood Forest),
 *       {@code goto biome silverwood_forest} says so and doesn't move the player.</li>
 *   <li><b>From inside the Dross</b> (the hub's arrival step), {@code goto biome cinder_wastes} lands in the Cinder
 *       Wastes, safe, not much further from where the player stood than the place the biome search
 *       ({@link DrossBiomes#findNearest}) gives (at most {@value #NOT_MUCH_FURTHER} blocks more), and outside the lair.
 *       The chat line is the "from where you were" one.</li>
 * </ol>
 * Every landing must also be in the biome asked for, where the player stands (what F3 shows).
 * <p>
 * The stand-in is logged out at the end, also when a check fails. How long each teleport took is logged
 * ("[goto biome test]" lines).
 */
@GameTestHolder(Dross.MODID)
@PrefixGameTestTemplate(false)
public final class GotoBiomeGameTest
{
    private static final Logger LOGGER = LogUtils.getLogger();

    /** The landing may be at most this many blocks further from the start than the place the biome search found. */
    private static final int NOT_MUCH_FURTHER = 64;
    /** "Two free blocks" above the feet. */
    private static final double FREE_HEIGHT = 2.0D;
    /** A standing player's width. */
    private static final double PLAYER_WIDTH = 0.6D;
    /** Ticks between the steps (each teleport runs in one go; the pauses just let the server tick in between). */
    private static final int PAUSE_TICKS = 2;
    /** The order the biomes are tried from the Overworld: the far mountains and the hub's own forest first. */
    private static final List<ResourceKey<Biome>> FROM_OVERWORLD = List.of(DrossBiomes.GRAVECRAGS, DrossBiomes.SILVERWOOD_FOREST,
            DrossBiomes.CINDER_WASTES, DrossBiomes.BLIGHTWOOD, DrossBiomes.RIMEFALL);

    private GotoBiomeGameTest() {}

    @GameTest(template = PortalSiteGameTest.EMPTY, batch = "dross_goto_biome", timeoutTicks = 600)
    public static void gotoBiome(GameTestHelper helper)
    {
        Run run = new Run(helper.getLevel().getServer());
        var sequence = helper.startSequence().thenExecute(() -> run.guard(run::begin));
        for (ResourceKey<Biome> biome : FROM_OVERWORLD)
        {
            sequence = sequence.thenIdle(PAUSE_TICKS).thenExecute(() -> run.guard(() -> run.fromOverworld(biome)));
        }
        sequence.thenIdle(PAUSE_TICKS)
                .thenExecute(() -> run.guard(run::atTheHub))
                .thenIdle(PAUSE_TICKS)
                .thenExecute(() -> run.guard(run::fromInsideTheDross))
                .thenExecute(run::cleanUp)
                .thenSucceed();
    }

    /** The test's state: the stand-in player, the Dross and the hub's footprint. */
    private static final class Run
    {
        final MinecraftServer server;
        ServerLevel dross;
        BoundingBox hubZone;
        @Nullable
        ServerPlayer player;
        final List<String> timings = new ArrayList<>();
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
                LOGGER.error("[goto biome test] {}", e.getMessage());
                cleanUp();
                throw e;
            }
            catch (RuntimeException e)
            {
                failed = true;
                LOGGER.error("[goto biome test] the test threw", e);
                cleanUp();
                GameTestAssertException failure = new GameTestAssertException("the test threw " + e);
                failure.initCause(e);
                throw failure;
            }
        }

        void begin()
        {
            dross = server.getLevel(ModDimensions.DROSS_LEVEL);
            if (dross == null)
            {
                throw new GameTestAssertException("there's no Dross level");
            }
            DrossHub.Layout layout = DrossHub.layout(server).orElseThrow(() -> new GameTestAssertException("the hub has no layout"));
            hubZone = layout.footprint().inflatedBy(GotoBiome.HUB_CLEARANCE);
            player = joinPlayer(server.overworld(), "goto-biome-test", overworldSpawn(server));
        }

        /** {@code goto biome <name>} from the Overworld. */
        void fromOverworld(ResourceKey<Biome> biome)
        {
            ServerPlayer player = requirePlayer();
            Vec3 spawn = overworldSpawn(server);
            player.teleportTo(server.overworld(), spawn.x, spawn.y, spawn.z, 0.0F, 0.0F);
            if (player.level() != server.overworld())
            {
                throw new GameTestAssertException("couldn't put the stand-in back in the Overworld");
            }
            String name = DrossBiomes.name(biome);
            Chat chat = new Chat();
            long started = Util.getMillis();
            int result = run(chat, player, "dross goto biome " + name);
            long took = Util.getMillis() - started;
            String where = "from the Overworld, goto biome " + name;
            if (result != 1)
            {
                throw new GameTestAssertException(where + ": the command gave " + result + " (" + chat + ")");
            }
            chat.expectLast(GotoBiome.SUCCESS_FROM_HUB, where);
            checkLanding(player, biome, where);
            note(where, took, player);
        }

        /**
         * On the hub's arrival step, which stands in the Silverwood Forest: {@code goto biome silverwood_forest} says so and
         * doesn't move them.
         */
        void atTheHub()
        {
            ServerPlayer player = requirePlayer();
            if (!DrossHub.teleportToHub(player) || player.level() != dross)
            {
                throw new GameTestAssertException("couldn't put the stand-in on the hub's arrival step");
            }
            Holder<Biome> step = dross.getBiome(player.blockPosition());
            if (!step.is(DrossBiomes.SILVERWOOD_FOREST))
            {
                throw new GameTestAssertException("the hub's arrival step at " + player.blockPosition().toShortString() + " is in "
                        + biomeId(step) + ", not the Silverwood Forest");
            }
            expectAlreadyThere(player, "already in the Silverwood Forest (the hub's arrival step)");
        }

        /** {@code goto biome silverwood_forest} where the player stands (in the Silverwood Forest) doesn't move them. */
        void expectAlreadyThere(ServerPlayer player, String where)
        {
            Vec3 before = player.position();
            Chat chat = new Chat();
            int result = run(chat, player, "dross goto biome silverwood_forest");
            if (result != 0 || player.level() != dross || player.position().distanceToSqr(before) > 1.0E-6D)
            {
                throw new GameTestAssertException(where + ": the command gave " + result + " and moved the player from " + before + " to "
                        + player.position() + " (" + chat + ")");
            }
            chat.expectLast(GotoBiome.ALREADY_THERE, where);
            LOGGER.info("[goto biome test] {}: told so, not moved.", where);
        }

        /** From the hub's arrival step, {@code goto biome cinder_wastes}: near where the biome search points. */
        void fromInsideTheDross()
        {
            ServerPlayer player = requirePlayer();
            if (!DrossHub.teleportToHub(player) || player.level() != dross)
            {
                throw new GameTestAssertException("couldn't put the stand-in on the hub's arrival step");
            }
            BlockPos origin = GotoBiome.searchOrigin(dross, player.blockPosition());
            BlockPos found = DrossBiomes.findNearest(dross, origin, DrossBiomes.CINDER_WASTES)
                    .orElseThrow(() -> new GameTestAssertException("the biome search finds no Cinder Wastes from the hub"));
            double foundDistance = GotoBiome.horizontalDistance(origin, Vec3.atBottomCenterOf(found));
            Chat chat = new Chat();
            long started = Util.getMillis();
            int result = run(chat, player, "dross goto biome cinder_wastes");
            long took = Util.getMillis() - started;
            String where = "from the hub's arrival step in the Dross, goto biome cinder_wastes";
            if (result != 1)
            {
                throw new GameTestAssertException(where + ": the command gave " + result + " (" + chat + ")");
            }
            chat.expectLast(GotoBiome.SUCCESS_FROM_YOU, where);
            checkLanding(player, DrossBiomes.CINDER_WASTES, where);
            double distance = GotoBiome.horizontalDistance(origin, player.position());
            // Where the nearest Cinder Wastes is inside a lair's circle (a planned lair can sit right at the edge of the
            // biome nearest the hub, on some seeds), the landing must go round it: allow the circle's width more.
            double allowed = foundDistance + NOT_MUCH_FURTHER;
            for (GotoBiome.LairZone zone : GotoBiome.lairZones(dross, found.getX(), found.getZ()))
            {
                if (zone.contains(found.getX(), found.getZ()))
                {
                    allowed = Math.max(allowed, foundDistance + 2.0D * zone.radius() + NOT_MUCH_FURTHER);
                }
            }
            if (distance > allowed)
            {
                throw new GameTestAssertException(where + ": landed " + Math.round(distance) + " blocks away, but the biome search found it "
                        + Math.round(foundDistance) + " blocks away (at " + found.getX() + ", " + found.getZ() + ")");
            }
            LOGGER.info("[goto biome test] {}: the biome search found it {} blocks away, the player landed {} blocks away.", where,
                    Math.round(foundDistance), Math.round(distance));
            note(where, took, player);
        }

        /** In the Dross, on safe ground, in the biome, clear of the hub and the lair. */
        void checkLanding(ServerPlayer player, ResourceKey<Biome> biome, String where)
        {
            if (player.level() != dross)
            {
                throw new GameTestAssertException(where + ": the player is in " + player.level().dimension().location() + ", not the Dross");
            }
            Vec3 at = player.position();
            BlockPos feet = player.blockPosition();
            checkSafeGround(dross, player, at, where);
            checkInBiome(dross, feet, biome, where);
            checkClearOfHub(hubZone, feet, where);
            checkClearOfLair(server, at, where);
        }

        void note(String where, long took, ServerPlayer player)
        {
            String line = where + ": " + took + " ms, landed at " + player.blockPosition().toShortString();
            timings.add(line);
            LOGGER.info("[goto biome test] {}", line);
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
            logOut(player);
            player = null;
        }
    }

    // ---------------------------------------------------------------------------------------------
    // Shared with GotoLairGameTest
    // ---------------------------------------------------------------------------------------------

    /** The biome where the player stands (what F3 shows) is {@code biome}. */
    static void checkInBiome(ServerLevel dross, BlockPos feet, ResourceKey<Biome> biome, String where)
    {
        Holder<Biome> here = dross.getBiome(feet);
        if (!here.is(biome))
        {
            throw new GameTestAssertException(where + ": landed at " + feet.toShortString() + " in " + biomeId(here) + ", not " + DrossBiomes.name(biome));
        }
    }

    /** Not on the hub's footprint or within {@value GotoBiome#HUB_CLEARANCE} blocks of it ({@code hubZone}: the footprint and that margin). */
    static void checkClearOfHub(BoundingBox hubZone, BlockPos feet, String where)
    {
        if (feet.getX() >= hubZone.minX() && feet.getX() <= hubZone.maxX() && feet.getZ() >= hubZone.minZ() && feet.getZ() <= hubZone.maxZ())
        {
            throw new GameTestAssertException(where + ": landed at " + feet.toShortString() + ", on the hub's footprint or within "
                    + GotoBiome.HUB_CLEARANCE + " blocks of it (" + hubZone + ")");
        }
    }

    /** A biome's id, for messages. */
    static String biomeId(Holder<Biome> biome)
    {
        return biome.unwrapKey().map(key -> key.location().toString()).orElse("?");
    }

    /**
     * Something solid right under the feet, and two blocks of room (a player's width) with no block or fluid in it.
     * {@code entity}: who stands there (null: just the blocks in the room).
     */
    static void checkSafeGround(ServerLevel level, @Nullable Entity entity, Vec3 at, String where)
    {
        AABB room = new AABB(at.x - PLAYER_WIDTH / 2.0D, at.y, at.z - PLAYER_WIDTH / 2.0D, at.x + PLAYER_WIDTH / 2.0D, at.y + FREE_HEIGHT,
                at.z + PLAYER_WIDTH / 2.0D);
        if (!level.noCollision(entity, room))
        {
            throw new GameTestAssertException(where + ": no room to stand at " + at + " (a block is in the two blocks above the feet)");
        }
        if (level.containsAnyLiquid(room))
        {
            throw new GameTestAssertException(where + ": there's a fluid where the player stands at " + at);
        }
        BlockPos under = BlockPos.containing(at.x, at.y - 0.01D, at.z);
        BlockState ground = level.getBlockState(under);
        if (ground.getCollisionShape(level, under).isEmpty() || !ground.getFluidState().isEmpty())
        {
            throw new GameTestAssertException(where + ": nothing solid under the feet at " + at + " (" + ground + ")");
        }
    }

    /** Outside every built Colossus lair: further from its middle than its entrance is (its cavern, dome, tunnel and mouth). */
    static void checkClearOfLair(MinecraftServer server, Vec3 at, String where)
    {
        List<ColossusLair.Lair> lairs = ColossusLair.all(server);
        if (lairs.isEmpty())
        {
            LOGGER.info("[goto biome test] {}: no Colossus lair is built yet, so there's no lair to keep clear of (GotoLairGameTest "
                    + "checks that part once one is).", where);
            return;
        }
        for (ColossusLair.Lair lair : lairs)
        {
            double reach = lair.reach();
            double distance = lair.distanceTo(at.x, at.z);
            if (distance <= reach)
            {
                throw new GameTestAssertException(where + ": landed at " + BlockPos.containing(at).toShortString() + ", " + Math.round(distance)
                        + " blocks from the middle of the Colossus lair at " + lair.center().toShortString() + ", inside its reach ("
                        + Math.round(reach) + " blocks, to its entrance)");
            }
        }
    }

    /** Runs a command as the player, with cheats, its chat lines going to {@code chat}. */
    static int run(Chat chat, ServerPlayer player, String command)
    {
        CommandSourceStack source = player.createCommandSourceStack().withPermission(2).withSource(chat);
        try
        {
            return player.server.getCommands().getDispatcher().execute(command, source);
        }
        catch (CommandSyntaxException e)
        {
            throw new GameTestAssertException("/" + command + " didn't run: " + e.getMessage());
        }
    }

    /** Feet on the ground at the Overworld's spawn (its chunk loaded first). */
    static Vec3 overworldSpawn(MinecraftServer server)
    {
        ServerLevel overworld = server.overworld();
        BlockPos spawn = overworld.getSharedSpawnPos();
        overworld.getChunkAt(spawn);
        int y = overworld.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, spawn.getX(), spawn.getZ());
        return new Vec3(spawn.getX() + 0.5D, y, spawn.getZ() + 0.5D);
    }

    /**
     * A stand-in player on the server's player list (in creative, so mobs leave him alone), at {@code feet} in
     * {@code level}. He gets an in-memory network channel like the other areas' stand-ins; he isn't ticked.
     */
    static ServerPlayer joinPlayer(ServerLevel level, String name, Vec3 feet)
    {
        ServerPlayer player = new ServerPlayer(level.getServer(), level, new GameProfile(UUID.randomUUID(), name));
        player.moveTo(feet.x, feet.y, feet.z, 0.0F, 0.0F);
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection); // opens the in-memory channel; packets sent to him just pile up in it
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        player.setGameMode(GameType.CREATIVE);
        if (player.serverLevel() != level || player.position().distanceToSqr(feet) > 0.01D)
        {
            player.teleportTo(level, feet.x, feet.y, feet.z, 0.0F, 0.0F);
        }
        return player;
    }

    /** Logs a stand-in out, if he's still on the player list. */
    static void logOut(@Nullable ServerPlayer player)
    {
        if (player != null && player.server.getPlayerList().getPlayer(player.getUUID()) == player)
        {
            player.server.getPlayerList().remove(player);
        }
    }

    /** Collects the chat lines a command sends (instead of sending them to the stand-in). */
    static final class Chat implements CommandSource
    {
        final List<Component> lines = new ArrayList<>();

        @Override
        public void sendSystemMessage(Component message)
        {
            lines.add(message);
        }

        @Override
        public boolean acceptsSuccess()
        {
            return true;
        }

        @Override
        public boolean acceptsFailure()
        {
            return true;
        }

        @Override
        public boolean shouldInformAdmins()
        {
            return false;
        }

        /** The last line's lang key, or null if it has none (or there's no line). */
        @Nullable
        String lastKey()
        {
            TranslatableContents translatable = lines.isEmpty() ? null : translatableIn(lines.get(lines.size() - 1));
            return translatable == null ? null : translatable.getKey();
        }

        /** The last line's arguments (a translatable line's), or none. */
        Object[] lastArgs()
        {
            TranslatableContents translatable = lines.isEmpty() ? null : translatableIn(lines.get(lines.size() - 1));
            return translatable == null ? new Object[0] : translatable.getArgs();
        }

        /**
         * The line's lang-key part: the line itself, or (for a failure, which Minecraft wraps in an empty red line) the
         * first part of it that has one.
         */
        @Nullable
        static TranslatableContents translatableIn(Component line)
        {
            if (line.getContents() instanceof TranslatableContents translatable)
            {
                return translatable;
            }
            for (Component part : line.getSiblings())
            {
                TranslatableContents found = translatableIn(part);
                if (found != null)
                {
                    return found;
                }
            }
            return null;
        }

        void expectLast(String key, String where)
        {
            if (!key.equals(lastKey()))
            {
                throw new GameTestAssertException(where + ": the chat line should be " + key + ", but it was " + this);
            }
        }

        @Override
        public String toString()
        {
            List<String> shown = new ArrayList<>();
            for (Component line : lines)
            {
                TranslatableContents translatable = translatableIn(line);
                shown.add(translatable != null ? translatable.getKey() + " \"" + line.getString() + "\"" : "\"" + line.getString() + "\"");
            }
            return shown.isEmpty() ? "no chat lines" : "chat: " + String.join(" / ", shown);
        }
    }
}
